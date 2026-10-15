# H.O.W.L. Codex 0.1.0-dev.3 | Nightly

**All player testing is through CodaLauncher Nightly. There are no manual
downloads, custom launchers, JAR copying or separate test ZIPs.**

Minecraft Java 26.4 Snapshot 3. Requires H.O.W.L. Nightly v0.0.42+
with `CodaInventoryPanels` from [loader PR #16](https://github.com/HowlingWhispers/HW-CodaLoader/pull/16).

## Player flow

1. Select **Nightly** in CodaLauncher. Nightly Play / Install-Repair
   automatically checks compatible first-party experimental mods, installs or
   updates them and verifies checksums in the Nightly profile.
2. Open a disposable Minecraft test world and press **E**.
3. Select **Codex** on the vanilla player inventory screen. Codex closes the vanilla recipe book when needed and places an NEI/TMI-style compact clickable item grid in the free margin beside the inventory, never over its slots.
4. Search by name, `@minecraft`, `@howl_sorting_pipes` or `#tag`.
5. Hover over each item icon for its full name and registry ID. Click an icon to inspect its ID and mod owner. Select **R Known recipes**
   for recipes producing it, or **U Known uses** for recipes consuming it.
6. Browse synchronized known recipes, outputs, ingredient alternatives and
   registered Minecraft item sprites. Scroll through result pages with Previous / Next.

## Automated release contract

CI checks Java compilation, native UI interaction, item-icon rendering, recipe
and reverse-usage navigation, mod ID/version, Minecraft target, entrypoint and
direct JAR SHA-256. Then the `main` publish job requires a published v0.0.42
loader JAR containing the panel API and releases an immutable
`howl-codex-0.1.0-dev.3.jar` plus `.jar.sha256` asset. CodaLauncher retrieves
it **automatically** via the Nightly-only required-mod manifest and never puts
it in Stable.

No CI workflow artifacts are distributed to players. A failed release/build
does not authorize manual installation; the previous checksum-verified Nightly
build remains in use.

## Limitations and verification

- Only the vanilla player inventory is supported, not chest/creative screens.
- Client-visible recipes are limited to known/unlocked, synced displays.
- Missing ingredient data is never guessed.
- All actions are read-only; there is no item duplication, item giving or
  inventory mutation.
- Tests have passed in simulation and official Mojang Snapshot 3 API probes.
  Actual rendered in-game interaction is to be verified **in Nightly** before
  approving the mod for Stable.

Track [HW-Mods #10](https://github.com/HowlingWhispers/HW-Mods/issues/10)
and [Nightly integration #10](https://github.com/HowlingWhispers/HW-CodaLauncher/issues/10).

## Developer-only CI build

```bash
HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java bash scripts/build-howl-codex.sh
```

The command above is for CI and developers only, never for player setup.
