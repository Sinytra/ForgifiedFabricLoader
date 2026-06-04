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

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import org.slf4j.Logger;

import java.util.List;

public class LoaderBootstrap implements ClassProcessorProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void createProcessors(Context context, Collector collector) {
        List<? extends IModInfo> mods = FMLLoader.getCurrent().getLoadingModList().getMods();

        LOGGER.debug("Adding {} NeoForge mods to Fabric context", mods.size());
        FabricLoaderImpl.INSTANCE.addFmlMods(mods);
    }
}
