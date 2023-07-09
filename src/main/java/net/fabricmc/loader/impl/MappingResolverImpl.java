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

import cpw.mods.modlauncher.api.INameMappingService;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.INamedMappingFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class MappingResolverImpl implements MappingResolver {
    /**
     * We ship this file in our jar, by default it contains OFFICIAL -> SRG -> INTERMEDIARY mappings
     */
    private static final String MAPPINGS_RESOURCE = "/mappings.tsrg";
    private static final String FML_NAMESPACE = FMLEnvironment.naming;
    private static final String OBF_NAMESPACE = "srg";

    private final INamedMappingFile mappings;

    public MappingResolverImpl() {
        URL path = getClass().getResource(MAPPINGS_RESOURCE);
        if (path == null && !FMLEnvironment.production)
            throw new RuntimeException("Missing mappings file");

        try (InputStream is = path.openStream()) {
            INamedMappingFile map = INamedMappingFile.load(is);

            List<String> names = map.getNames();
            // If we're in a deobfuscated environment, append deobf named to the mappings
            // This is done at runtime to comply with mojmap licensing
            if (!FML_NAMESPACE.equals(OBF_NAMESPACE) && names.contains(OBF_NAMESPACE)) {
                IMappingBuilder builder = IMappingBuilder.create(Stream.concat(map.getNames().stream(), Stream.of(FML_NAMESPACE)).toArray(String[]::new));
                // Grab modlauncher service for remapping SRG -> MOJ
                BiFunction<INameMappingService.Domain, String, String> mapper = FMLLoader.getNameFunction(OBF_NAMESPACE).orElseThrow();

                // Get all names but SRG
                List<String> filtered = new ArrayList<>(names);
                filtered.remove(OBF_NAMESPACE);
                // Mapping of the first namespace to SRG
                IMappingFile primary = map.getMap(filtered.get(0), OBF_NAMESPACE);
                // Copy all members from the primary mapping to the builder, while adding the remaining namespace mappings
                // We assume all mapping files created by the named mapping file have the same amount of classes based on the implementation
                primary.getClasses().forEach(cls -> {
                    // Create new class
                    IMappingBuilder.IClass newCls = builder.addClass(getNames(map, filtered, cls, IMappingFile::getClass, cls.getMapped()));
                    // Add all methods
                    cls.getMethods().forEach(mtd -> newCls.method(mtd.getDescriptor(), getNames(map, filtered, mtd,
                            (m, name) -> m.getClass(cls.getOriginal()).getMethod(name, mtd.getDescriptor()),
                            mapMethodNameIncludingRecords(mapper, mtd.getMapped()))));
                    // Add all fields
                    cls.getFields().forEach(fd -> newCls.field(getNames(map, filtered, fd,
                            (m, name) -> m.getClass(cls.getOriginal()).getField(name),
                            mapper.apply(INameMappingService.Domain.FIELD, fd.getMapped()))));
                });
                // Add all packages
                primary.getPackages().forEach(pkg -> builder.addPackage(getNames(map, filtered, pkg, IMappingFile::getPackage, pkg.getOriginal())));
                this.mappings = builder.build();
            } else {
                this.mappings = map;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // See https://github.com/MinecraftForge/ForgeGradle/issues/922
    private static String mapMethodNameIncludingRecords(BiFunction<INameMappingService.Domain, String, String> mapper, String name) {
        String mapped = mapper.apply(INameMappingService.Domain.METHOD, name);
        if (mapped.equals(name)) {
            mapped = mapper.apply(INameMappingService.Domain.FIELD, name);
        }
        return mapped;
    }

    /**
     * Get all names for a node in named mapping file.
     * @param map the named mapping file
     * @param namespaces all mapping files namespaces, excluding the target namespace of node
     * @param node the node to get all names for
     * @param resolver returns nodes for remaining mapping pairs in the named mapping files
     * @param additional additional mapped names to include, useful when appending new namespaces
     * @return an array of all available mapped names for the given node
     */
    private static String[] getNames(INamedMappingFile map, List<String> namespaces, IMappingFile.INode node, BiFunction<IMappingFile, String, IMappingFile.INode> resolver, String... additional) {
        String[] arr = new String[map.getNames().size() + additional.length];
        arr[0] = node.getOriginal();
        arr[1] = node.getMapped();
        for (int i = 2; i <= namespaces.size(); i++) {
            IMappingFile mappingFile = map.getMap(namespaces.get(0), namespaces.get(i - 1));
            arr[i] = resolver.apply(mappingFile, node.getOriginal()).getMapped();
        }
        System.arraycopy(additional, 0, arr, arr.length - additional.length, additional.length);
        return arr;
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
