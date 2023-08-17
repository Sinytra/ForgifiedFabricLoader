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

import cpw.mods.modlauncher.ArgumentHandler;
import cpw.mods.modlauncher.Launcher;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.util.DefaultLanguageAdapter;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings("deprecation")
public final class FabricLoaderImpl extends net.fabricmc.loader.FabricLoader {
    public static final FabricLoaderImpl INSTANCE = InitHelper.get();

    private final Map<String, ModContainerImpl> modMap = new HashMap<>();
    private final List<ModContainerImpl> mods = new ArrayList<>();

    private final Map<String, LanguageAdapter> adapterMap = new HashMap<>();
    private final EntrypointStorage entrypointStorage = new EntrypointStorage();

    private final ObjectShare objectShare = new ObjectShareImpl();
    private volatile MappingResolverImpl mappingResolver;

    private String[] launchArgs;
    private boolean loadedFMLMods;

    private FabricLoaderImpl() {
    }

    @Override
    public Object getGameInstance() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.dist == Dist.CLIENT ? EnvType.CLIENT : EnvType.SERVER;
    }

    /**
     * @return The game instance's root directory.
     */
    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
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
        return !FMLEnvironment.production;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        if (launchArgs == null) {
            try {
                Field argumentHandlerField = Launcher.class.getDeclaredField("argumentHandler");
                argumentHandlerField.setAccessible(true);
                ArgumentHandler handler = (ArgumentHandler) argumentHandlerField.get(Launcher.INSTANCE);
                launchArgs = handler.buildArgumentList();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
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
            for (IModInfo mod : fmlMods) {
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

    public void addMods(List<ModContainerImpl> fabricMods) {
        for (ModContainerImpl mod : fabricMods) {
            if (modMap.put(mod.getMetadata().getId(), mod) != null) {
                throw new IllegalStateException("Duplicate fabric mod: " + mod.getMetadata().getId());
            }
            mods.add(mod);
            for (String provides : mod.getMetadata().getProvides()) {
                modMap.put(provides, mod);
            }
        }
    }

    public void setup() {
        setupLanguageAdapters();
        setupMods();
    }

    private void setupLanguageAdapters() {
        adapterMap.put("default", DefaultLanguageAdapter.INSTANCE);

        for (ModContainerImpl mod : mods) {
            // add language adapters
            for (Map.Entry<String, String> laEntry : mod.getInfo().getLanguageAdapterDefinitions().entrySet()) {
                if (adapterMap.containsKey(laEntry.getKey())) {
                    throw new RuntimeException("Duplicate language adapter key: " + laEntry.getKey() + "! (" + laEntry.getValue() + ", " + adapterMap.get(laEntry.getKey()).getClass().getName() + ")");
                }

                try {
                    adapterMap.put(laEntry.getKey(), (LanguageAdapter) Class.forName(laEntry.getValue(), true, Thread.currentThread().getContextClassLoader()).getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate language adapter: " + laEntry.getKey(), e);
                }
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
