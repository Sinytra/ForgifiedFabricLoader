# Forgified Fabric Loader

[![Build](https://github.com/Sinytra/ForgifiedFabricLoader/actions/workflows/build.yml/badge.svg)](https://github.com/Sinytra/ForgifiedFabricLoader/actions/workflows/build.yml)
[![Latest Release](https://maven.su5ed.dev/api/badge/latest/releases/dev/su5ed/sinytra/fabric-loader?color=2280e0&name=Latest%20Release)](https://maven.su5ed.dev/#/releases/dev/su5ed/sinytra/fabric-loader)
[![License](https://img.shields.io/github/license/sinytra/ForgifiedFabricLoader?color=orange)](https://github.com/Sinytra/ForgifiedFabricLoader/blob/1.20.1/LICENSE)

An implementation of Fabric Loader api on top of Forge Mod Loader, allowing Forge mods to access game information as
well as other mod containers. Useful for cross-platform mod development.

### Supported Features

- Accessing FML mod containers/metadata
- Mapping resolver
- Environmental and game information

### Unsupported Features

- Mod initializers
- Loading Fabric mods

## Installation

### Users

The Forgified Fabric Loader is bundled with the
main [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI) jar. If you already have that installed, no
additional steps are required.

### Developers

In development environments, the Forgified Fabric Loader is downloaded as a dependency of the Forgified Fabric API.
This is going to be the case for most developers. See
the [Forgified Fabric API](https://github.com/Sinytra/ForgifiedFabricAPI) repository for installation instructions.

If you wish to depend on the Forgified Fabric Loader alone, it is published at `https://maven.su5ed.dev/releases`
under the `dev.su5ed.sinytra:fabric-loader` identifier. The versioning scheme follows
a `{impl_version}+{upstream_version}+{mc_version}` pattern.

#### NESTED JARS NOTE

In case your mods bundles Fabric API modules rather than depending on the entire mod, keep in mind certain parts of the
API might depend on the Forgified Fabric Loader, in which case you'll have to bundle it as well.
More information can be found on FFAPI's repository.

## License

Licensed under the Apache License 2.0.
