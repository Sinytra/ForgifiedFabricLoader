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

package dev.su5ed.sinytra.loader;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;

import java.util.List;
import java.util.Set;

public class FabricLoaderServiceProvider implements ITransformationService {
    private static final String NAME = "fabric-loader";

    private boolean initializedMods;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public List<ITransformer> transformers() {
        if (!this.initializedMods) {
            // Load FML mods into Fabric Loader
            List<ModInfo> mods = LoadingModList.get().getMods();
            FabricLoaderImpl.INSTANCE.addFmlMods(mods);
            initializedMods = true;
        }
        return List.of();
    }
}
