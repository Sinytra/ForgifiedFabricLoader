package net.fabricmc.loader.impl.util;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.ModuleLayerHandler;
import cpw.mods.modlauncher.api.IModuleLayerManager;

import java.lang.reflect.Method;

public class ModLauncherUtils { // https://github.com/dima-dencep/ModLauncherUtils/blob/main/v10/src/main/java/com/github/dima_dencep/mods/modlauncherutils/v10/hacks/HacksImpl.java
    private static Method addToLayerMethod;

    public static void addJarToLayer(IModuleLayerManager.Layer layer, SecureJar jar) throws Exception {
        if (addToLayerMethod == null) {
            addToLayerMethod = ModuleLayerHandler.class.getDeclaredMethod("addToLayer", IModuleLayerManager.Layer.class, SecureJar.class);
            addToLayerMethod.setAccessible(true);
        }

        addToLayerMethod.invoke(Launcher.INSTANCE.findLayerManager().orElseThrow(), layer, jar);
    }
}
