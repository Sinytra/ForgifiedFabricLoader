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
