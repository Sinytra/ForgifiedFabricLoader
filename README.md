# Forgified Fabric Loader

An implementation of Fabric Loader api on top of Forge Mod Loader, allowing Fabric mods to access game information as
well as other mod containers. Currently, it does not do anything by itself and must be bootstrapped
by [Connector](https://github.com/Sinytra/Connector), which uses it to invoke fabric mod initializers and provide
environment information to them.

## License

Licensed under the Apache License 2.0.
