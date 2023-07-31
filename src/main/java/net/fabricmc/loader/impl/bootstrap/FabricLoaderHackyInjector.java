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

package net.fabricmc.loader.impl.bootstrap;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.forgespi.language.ILifecycleEvent;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.ModFileScanData;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Terribly ugly hack to bootstrap fml mods into fabric loader before modloading starts, in a way that can be loaded from JarJar.
 */
public class FabricLoaderHackyInjector implements IModLanguageProvider {
    public static final String NAME = "__fabric_loader_bootstrap";

    public FabricLoaderHackyInjector() {
        try {
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            Map<String, ILaunchPluginService> plugins = (Map<String, ILaunchPluginService>) pluginsField.get(launchPluginHandler);
            // Ew hacks
            ILaunchPluginService bootstrap = new FabricLoaderBootstrap();
            plugins.put(bootstrap.name(), bootstrap);
        } catch (Exception e) {
            throw new RuntimeException("Error injecting fabric loader bootstrap", e);
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor() {
        return data -> {};
    }

    @Override
    public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(Supplier<R> consumeEvent) {
    }
}
