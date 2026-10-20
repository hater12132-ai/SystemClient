# SystemMods.levipack (bundled)

- Lives in `app/src/main/assets/system_mods/SystemMods.levipack`
- On launch: copied into the version mods folder as mod id `SystemMods` and enabled
- Levi loads it like a normal native mod (no manual import)
- SystemDLC menu lists the main modules and writes their `masterEnabled` into config

Note: the binary still contains all BedrockTools modules; SystemDLC only surfaces the ones we care about. A trimmed rebuild can come later.
