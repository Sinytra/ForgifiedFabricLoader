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

package net.fabricmc.loader.impl.launch;

import net.fabricmc.api.EnvType;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;

public class FabricLauncherFML extends FabricLauncherBase {
    @Override
    public void addToClassPath(Path path, String... allowedPrefixes) {}

    @Override
    public void setAllowedPrefixes(Path path, String... prefixes) {}

    @Override
    public void setValidParentClassPath(Collection<Path> paths) {}

    @Override
    public EnvType getEnvironmentType() {
        return FMLEnvironment.dist.isClient() ? EnvType.CLIENT : EnvType.SERVER;
    }

    @Override
    public boolean isClassLoaded(String name) {
        return false;
    }

    @Override
    public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
        return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Override
    public ClassLoader getTargetClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
        return null;
    }

    @Override
    public Manifest getManifest(Path originPath) {
        return new Manifest();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    @Override
    public String getEntrypoint() {
        return null;
    }

    @Override
    public String getTargetNamespace() {
        return FMLEnvironment.naming;
    }

    @Override
    public List<Path> getClassPath() {
        return List.of();
    }
}
