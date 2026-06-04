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
import net.fabricmc.loader.impl.util.LoaderUtil;

import java.util.Collection;
import java.util.Set;

public class MappingResolverImpl implements MappingResolver {
    private static final String FML_NAMESPACE = LoaderUtil.RUNTIME_MAPPING;

    @Override
    public Collection<String> getNamespaces() {
        return Set.of(FML_NAMESPACE);
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return FML_NAMESPACE;
    }

    @Override
    public String mapClassName(String namespace, String className) {
        return className;
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        return className;
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        return name;
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        return name;
    }
}
