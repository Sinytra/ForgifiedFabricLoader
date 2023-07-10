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

import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;

public final class MappingConfiguration {
	private final String gameId = "minecraft";
	private final String gameVersion = FMLLoader.versionInfo().mcVersion();
//	private TinyTree mappings;

	public String getGameId() {
		return gameId;
	}

	public String getGameVersion() {
		return gameVersion;
	}

	public boolean matches(String gameId, String gameVersion) {
		return (this.gameId == null || gameId == null || gameId.equals(this.gameId))
				&& (this.gameVersion == null || gameVersion == null || gameVersion.equals(this.gameVersion));
	}

//	public TinyTree getMappings() {
//		initialize();
//
//		return mappings;
//	}

	public String getTargetNamespace() {
		return FMLEnvironment.naming;
	}

	public boolean requiresPackageAccessHack() {
		return false;
	}
}
