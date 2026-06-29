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

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.*;
import net.fabricmc.loader.impl.metadata.CustomValueImpl;
import net.fabricmc.loader.impl.metadata.SimplePerson;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FMLModMetadata implements ModMetadata {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final IModInfo modInfo;
    private final Version version;
    private final Collection<Person> authors;
    private final Map<String, CustomValue> customValues;

    public FMLModMetadata(IModInfo modInfo) {
        this.modInfo = modInfo;
        this.version = uncheck(() -> Version.parse(this.modInfo.getVersion().toString()));
        this.authors = modInfo.getConfig().getConfigElement("authors").stream()
            .flatMap(obj -> obj instanceof List list ? ((List<String>) list).stream() : Stream.of(obj.toString().split(",")))
            .<Person>map(SimplePerson::new)
            .toList();
        this.customValues = this.modInfo.getModProperties().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> convertModProperty(e.getValue())));
    }

    @Override
    public String getType() {
        return "fabric";
    }

    @Override
    public String getId() {
        return this.modInfo.getModId();
    }

    @Override
    public Collection<String> getProvides() {
        List<String> modProvides = new ArrayList<>(Optional.ofNullable(this.modInfo.getModProperties().get(Constants.PROVIDES))
            .filter(List.class::isInstance)
            .map(o -> (List<String>) o)
            .orElseGet(List::of));

        // Make a guess and convert the modid into a fabric-styled one to increase dependency resolution success rate
        // Certain cross-platform mods such as Cloth Config use an underscored modid on Forge, while using a hyphenated one on Fabric
        if (modProvides.isEmpty() && getId().contains("_")) {
            String denormalized = getId().replace('_', '-');
            modProvides.add(denormalized);
        }

        // Add user-configured mod aliases 
        modProvides.addAll(FabricLoaderImpl.INSTANCE.getModAliases(getId()));

        return modProvides;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    @Override
    public ModEnvironment getEnvironment() {
        return ModEnvironment.UNIVERSAL;
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return Set.of();
    }

    @Override
    public String getName() {
        return this.modInfo.getDisplayName();
    }

    @Override
    public String getDescription() {
        return this.modInfo.getDescription();
    }

    @Override
    public Collection<Person> getAuthors() {
        return this.authors;
    }

    @Override
    public Collection<Person> getContributors() {
        return Set.of();
    }

    @Override
    public ContactInformation getContact() {
        return ContactInformation.EMPTY;
    }

    @Override
    public Collection<String> getLicense() {
        return List.of(this.modInfo.getOwningFile().getLicense());
    }

    @Override
    public Optional<String> getIconPath(int size) {
        return this.modInfo.getLogoFile();
    }

    @Override
    public boolean containsCustomValue(String key) {
        return this.modInfo.getModProperties().containsKey(key);
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return this.customValues.get(key);
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return this.customValues;
    }

    @Override
    public boolean containsCustomElement(String key) {
        return containsCustomValue(key);
    }

    @Nullable
    private static CustomValue convertModProperty(@Nullable Object value) {
        switch (value) {
            case null -> {
                return CustomValueImpl.NULL;
            }
            case UnmodifiableConfig config -> {
                Map<String, CustomValue> entries = config.entrySet().stream()
                        .collect(Collectors.toMap(UnmodifiableConfig.Entry::getKey, e -> convertModProperty(e.getValue())));
                return new CustomValueImpl.ObjectImpl(entries);
            }
            case ArrayList<?> list -> {
                List<CustomValue> contents = list.stream().map(FMLModMetadata::convertModProperty).toList();
                return new CustomValueImpl.ArrayImpl(contents);
            }
            case Boolean b -> {
                return b ? CustomValueImpl.BOOLEAN_TRUE : CustomValueImpl.BOOLEAN_FALSE;
            }
            case String str -> {
                return new CustomValueImpl.StringImpl(str);
            }
            case Number num -> {
                return new CustomValueImpl.NumberImpl(num);
            }
            default -> {}
        }
        LOGGER.warn("Ignoring custom mod property value '{}' of unsupported type '{}'", value, value.getClass().getName());
        return null;
    }
    
    private static <T> T uncheck(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
