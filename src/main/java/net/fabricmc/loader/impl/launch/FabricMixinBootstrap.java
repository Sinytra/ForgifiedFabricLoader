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

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.Collections;
import java.util.List;

// Used in Launchpad, do not remove
@SuppressWarnings("unused")
public final class FabricMixinBootstrap {
    private FabricMixinBootstrap() {
    }

    public static int getMixinCompat(ModMetadata metadata) {
        // infer from loader dependency by determining the least relevant loader version the mod accepts
        // AND any loader deps

        List<VersionInterval> reqIntervals = Collections.singletonList(VersionInterval.INFINITE);

        for (ModDependency dep : metadata.getDependencies()) {
            if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
                if (dep.getKind() == Kind.DEPENDS) {
                    reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
                } else if (dep.getKind() == Kind.BREAKS) {
                    reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
                }
            }
        }

        if (reqIntervals.isEmpty()) {
            throw new IllegalStateException("mod " + metadata.getId() + " is incompatible with every loader version?"); // shouldn't get there
        }

        Version minLoaderVersion = reqIntervals.getFirst().getMin(); // it is sorted, to 0 has the absolute lower bound

        if (minLoaderVersion != null) { // has a lower bound
            for (FabricMixinVersions.LoaderMixinVersionEntry version : FabricMixinVersions.getVersions()) {
                if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
                    return version.mixinVersion;
                }
            }
        }

        return FabricUtil.COMPATIBILITY_0_9_2;
    }
}