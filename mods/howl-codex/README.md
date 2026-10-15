# H.O.W.L. Codex 0.1.0-dev.1: playable inventory browser prototype

**Development-only.** Minecraft Java 26.4 Snapshot 3; H.O.W.L. loader **with**
inventory panel API from [HW-CodaLoader#15](https://github.com/HowlingWhispers/HW-CodaLoader/issues/15).

## Use

1. Build the *matching loader development branch* `feat/codex-inventory-panel-api`.
2. Build this mod with `HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java bash scripts/build-howl-codex.sh`.
3. Put the resulting `mods/howl-codex/dist/howl-codex-0.1.0-dev.1.jar` in the H.O.W.L. mods directory and start the **exact** Snapshot 3 profile.
4. Open the **player inventory** (E); click **Codex** at the top right.
5. Type an item name, `@mod_id` (e.g. `@minecraft` or `@howl_sorting_pipes`), or `#namespaced_tag`. Click an item for name, registry ID and mod owner. Use Previous/Next to browse.
6. Select **R Show known recipes** to populate Minecraft's *ordinary, unlocked recipe book* search with the item's name. Recipe search respects Minecraft's unlock restrictions.

No item giving, duplication, world edits, or GUI replacement. Only the vanilla player inventory is supported for now; container screens and creative-mode inventory remain untouched. **U / reverse-usage** isn't wired to a real recipe adapter yet. Real rendered game testing is required before player distribution.

If the GUI does not appear, inspect logs for `[H.O.W.L. Codex]`, verify the loader branch and test GUI scale/window width. Source does not bundle a private copy of loader APIs. Does not copy NEI/TMI source/assets.

## Build and tests

```bash
HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java bash scripts/build-howl-codex.sh
```

Runs item and recipe index tests. CI generates a development JAR and SHA256.
Native widget code uses reflection and must be tested on a real Mojang game UI,
not merely compiled. The panel renders actual Minecraft-native text controls;
native item sprite tiles, full recipe/usage view, keyboard shortcuts, and
recipe data adapters are subsequent development tasks.

Tracking: https://github.com/HowlingWhispers/HW-Mods/issues/10
