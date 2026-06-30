/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;
import net.fabricmc.loader.impl.util.ExceptionUtil;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public final class FabricLoaderImpl extends net.fabricmc.loader.FabricLoader {
    public static final FabricLoaderImpl INSTANCE = InitHelper.get();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, ModContainerImpl> modMap = new HashMap<>();
    private final List<ModContainerImpl> mods = new ArrayList<>();
    private final Multimap<String, String> modAliases = HashMultimap.create();
    private final Set<String> ignoredMods = new HashSet<>();

    private final Map<String, Supplier<LanguageAdapter>> adapterMap = new HashMap<>();
    private final EntrypointStorage entrypointStorage = new EntrypointStorage();

    private final ObjectShare objectShare = new ObjectShareImpl();
    private volatile MappingResolverImpl mappingResolver;

    private String[] launchArgs;
    private boolean loadedFMLMods;
    private boolean setupDone;

    private FabricLoaderImpl() {
    }

    @Override
	public String getRawGameVersion() {
		return FMLLoader.getCurrent().getVersionInfo().mcVersion();
	}

	@Override
    public Object getGameInstance() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.getDist() == Dist.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    /**
     * @return The game instance's root directory.
     */
    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    }

    @Override
    @Deprecated
    public File getGameDirectory() {
        return getGameDir().toFile();
    }

    /**
     * @return The game instance's configuration directory.
     */
    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    @Deprecated
    public File getConfigDirectory() {
        return getConfigDir().toFile();
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        return entrypointStorage.getEntrypoints(key, type);
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        return entrypointStorage.getEntrypointContainers(key, type);
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        if (!hasEntrypoints(key)) {
            Log.debug(LogCategory.ENTRYPOINT, "No subscribers for entrypoint '%s'", key);
            return;
        }

        RuntimeException exception = null;
        Collection<EntrypointContainer<T>> entrypoints = FabricLoaderImpl.INSTANCE.getEntrypointContainers(key, type);

        Log.debug(LogCategory.ENTRYPOINT, "Iterating over entrypoint '%s'", key);

        for (EntrypointContainer<T> container : entrypoints) {
            try {
                invoker.accept(container.getEntrypoint());
            } catch (Throwable t) {
                exception = ExceptionUtil.gatherExceptions(t,
                    exception,
                    exc -> new RuntimeException(String.format("Could not execute entrypoint stage '%s' due to errors, provided by '%s' at '%s'!",
                        key, container.getProvider().getMetadata().getId(), container.getDefinition()),
                        exc));
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

	@Override
    public MappingResolverImpl getMappingResolver() {
        if (mappingResolver == null) {
            synchronized (this) {
                if (mappingResolver == null) {
                    mappingResolver = new MappingResolverImpl();
                }
            }
        }
        return mappingResolver;
    }

    @Override
    public ObjectShare getObjectShare() {
        return objectShare;
    }

    @Override
    public Optional<ModContainer> getModContainer(String id) {
        return Optional.ofNullable(modMap.get(id));
    }

    @Override
    public Collection<ModContainer> getAllMods() {
        return Collections.unmodifiableList(mods);
    }

    @Override
    public boolean isModLoaded(String id) {
        return modMap.containsKey(id);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        if (launchArgs == null) {
            launchArgs = FMLLoader.getCurrent().getProgramArgs().getArguments();
        }
        return launchArgs;
    }

    public boolean hasEntrypoints(String key) {
        return entrypointStorage.hasEntrypoints(key);
    }

    public Collection<Object> getModInstances(String modid) {
        return entrypointStorage.getModInstances().get(modid);
    }

    public void addFmlMods(List<? extends IModInfo> fmlMods) {
        if (!loadedFMLMods) {
            LOGGER.debug("Adding {} NeoForge mods to Fabric context", mods.size());

            adapterMap.put("default", () -> DefaultLanguageAdapter.INSTANCE);

            for (IModInfo mod : fmlMods) {
                if (ignoredMods.contains(mod.getModId())) {
                    continue;
                }

                ModContainerImpl container = new ModContainerImpl(mod);
                if (modMap.put(mod.getModId(), container) != null) {
                    throw new IllegalStateException("Duplicate fml mod with metadata: " + mod.getModId());
                }
                mods.add(container);
                for (String provides : container.getMetadata().getProvides()) {
                    modMap.putIfAbsent(provides, container);
                }
            }

            loadedFMLMods = true;
        }
    }

    public void aliasMods(Multimap<String, String> aliases) {
        modAliases.putAll(aliases);
    }

    public Collection<String> getModAliases(String modid) {
        return modAliases.get(modid);
    }
    
    public void ignoreMods(Collection<String> modIds) {
        ignoredMods.addAll(modIds);
    }

    public void setup() {
        if (setupDone) {
            return;
        }

        addFmlMods(FMLLoader.getCurrent().getLoadingModList().getMods());
        setupLanguageAdapters();
        setupMods();

        // Register the upstream Fabric Loader version as a ForgeFeature
        var loaderVersion = getUpstreamLoaderVersion();
        if (loaderVersion != null) {
            ForgeFeature.registerFeature("fabricLoader", ForgeFeature.VersionFeatureTest.forVersionString(IModInfo.DependencySide.BOTH, loaderVersion));
        }

        setupDone = true;
    }

    @Nullable
    private String getUpstreamLoaderVersion() {
        var loaderVersion = FabricLoaderImpl.class.getResourceAsStream("/net/fabricmc/loader/fabric_loader_version");
        if (loaderVersion != null) {
            try (var stream = loaderVersion) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException exception) {
                LOGGER.error("Failed to read Fabric Loader version: ", exception);
            }
        }
        return null;
    }

    private void setupLanguageAdapters() {
        adapterMap.put("default", () -> DefaultLanguageAdapter.INSTANCE);

        for (ModContainerImpl mod : mods) {
            // add language adapters
            for (Map.Entry<String, String> laEntry : mod.getInfo().getLanguageAdapterDefinitions().entrySet()) {
                if (adapterMap.containsKey(laEntry.getKey())) {
                    throw new RuntimeException("Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " + adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
                }

                Supplier<LanguageAdapter> supplier = Suppliers.memoize(() -> {
                    try {
                        return (LanguageAdapter) Class.forName(laEntry.getValue(), true, Thread.currentThread().getContextClassLoader()).getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
                    }
                });
                adapterMap.put(laEntry.getKey(), supplier);
            }
        }
    }

    private void setupMods() {
        for (ModContainerImpl mod : mods) {
            try {
                for (String in : mod.getInfo().getOldInitializers()) {
                    String adapter = mod.getInfo().getOldStyleLanguageAdapter();
                    entrypointStorage.addDeprecated(mod, adapter, in);
                }

                for (String key : mod.getInfo().getEntrypointKeys()) {
                    for (EntrypointMetadata in : mod.getInfo().getEntrypoints(key)) {
                        entrypointStorage.add(mod, key, in, adapterMap);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(String.format("Failed to setup mod %s (%s)", mod.getInfo().getName(), mod.getOrigin()), e);
            }
        }
    }

    /**
     * Provides singleton for static init assignment regardless of load order.
     */
    public static class InitHelper {
        private static FabricLoaderImpl instance;

        public static FabricLoaderImpl get() {
            if (instance == null) instance = new FabricLoaderImpl();

            return instance;
        }
    }
}
