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
        this.authors = modInfo.getConfig().getConfigElement("authors")
                .map(str -> Stream.of(((String) str).split(",")).<Person>map(SimplePerson::new).toList())
                .orElseGet(List::of);
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
        return ((ModInfo) this.modInfo).<List<String>>getConfigElement("provides").orElseGet(List::of);
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
