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

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;

public class FabricLoaderBootstrap implements ILaunchPluginService {
    private static final String NAME = "fabric_loader_bootstrap";
    private static final EnumSet<Phase> NOPE = EnumSet.noneOf(Phase.class);
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return NOPE;
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
        // Load FML mods into Fabric Loader
        LOGGER.info("Propagating FML mod list to Fabric Loader");
        List<ModInfo> mods = LoadingModList.get().getMods();
        FabricLoaderImpl.INSTANCE.addFmlMods(mods);
    }
}
