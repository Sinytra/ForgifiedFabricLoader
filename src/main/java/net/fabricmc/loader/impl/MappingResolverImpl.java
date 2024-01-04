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

import net.fabricmc.loader.api.MappingResolver;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.INamedMappingFile;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;

public class MappingResolverImpl implements MappingResolver {
    /**
     * We ship this file in our jar, by default it contains OFFICIAL -> SRG -> INTERMEDIARY mappings
     */
    private static final String MAPPINGS_RESOURCE = "/mappings.tsrg";
    private static final String FML_NAMESPACE = FMLEnvironment.naming;

    private final INamedMappingFile mappings;

    public MappingResolverImpl() {
        URL path = getClass().getResource(MAPPINGS_RESOURCE);
        if (path == null && !FMLEnvironment.production)
            throw new RuntimeException("Missing mappings file");

        try (InputStream is = path.openStream()) {
            this.mappings = INamedMappingFile.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public IMappingFile getCurrentMap(String from) {
        return getMap(from, FML_NAMESPACE);
    }

    public IMappingFile getMap(String from, String to) {
        return this.mappings.getMap(from, to);
    }

    public String mapDescriptor(String namespace, String descriptor) {
        return getCurrentMap(namespace).remapDescriptor(descriptor);
    }

    @Override
    public Collection<String> getNamespaces() {
        return this.mappings.getNames();
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return FML_NAMESPACE;
    }

    @Override
    public String mapClassName(String namespace, String className) {
        return toBinaryName(getCurrentMap(namespace).remapClass(toInternalName(className)));
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        return toBinaryName(getMap(FML_NAMESPACE, targetNamespace).remapClass(toInternalName(className)));
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        return Optional.ofNullable(getCurrentMap(namespace).getClass(toInternalName(owner)))
                .map(cls -> cls.remapField(name))
                .orElse(name);
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        return Optional.ofNullable(getCurrentMap(namespace).getClass(toInternalName(owner)))
                .map(cls -> cls.remapMethod(name, descriptor))
                .orElse(name);
    }

    private static String toBinaryName(String className) {
        return className.replace('/', '.');
    }

    private static String toInternalName(String className) {
        return className.replace('.', '/');
    }
}
