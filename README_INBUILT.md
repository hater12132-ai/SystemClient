# SystemClient inbuilt mods

Ships these packs inside the APK (assets/system_mods/) and installs them into the
current version's modsDir on every launch via SystemBundledMods.ensureInstalled:

- 4DSkin.levipack
- BedrockTools.levipack
- FreeLook.levipack
- FrameGen.levipack
- libAirSwing.so

They use Levi's normal preload-native path (ModManager + ModNativeLoader), so they
show as real enabled mods without the plugin importer. User can still toggle them
in the Mods list / in-game menu that each SO registers.

No external import required.
