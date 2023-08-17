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

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.*;
import net.fabricmc.loader.impl.metadata.SimplePerson;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.*;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class FMLModMetadata implements ModMetadata {
    private final IModInfo modInfo;
    private final Version version;
    private final Collection<Person> authors;

    public FMLModMetadata(IModInfo modInfo) {
        this.modInfo = modInfo;
        this.version = uncheck(() -> Version.parse(this.modInfo.getVersion().toString()));
        this.authors = modInfo.getConfig().getConfigElement("authors").stream()
            .flatMap(obj -> obj instanceof List list ? ((List<String>) list).stream() : Stream.of(obj.toString().split(",")))
            .<Person>map(SimplePerson::new)
            .toList();
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
        List<String> modProvides = ((ModInfo) this.modInfo).<List<String>>getConfigElement("provides").orElseGet(List::of);
        // Make a guess and convert the modid into a fabric-styled one to increase dependency resolution success rate
        // Certain cross-platform mods such as Cloth Config use an underscored modid on Forge, while using a hyphenated one on Fabric
        if (modProvides.isEmpty() && getId().contains("_")) {
            String normalized = getId().replace('_', '-');
            return List.of(normalized);
        }
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
        return false;
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return null;
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return Map.of();
    }

    @Override
    public boolean containsCustomElement(String key) {
        return false;
    }
}
