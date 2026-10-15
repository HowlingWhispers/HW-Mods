# H.O.W.L. Codex (development foundation)

Original item browser and recipe/usage index for H.O.W.L., inspired by the
*functions* of NEI/TMI rather than any copied code or assets.

**Minecraft target:** Java 26.4 Snapshot 3  
**Mod ID:** `howl_codex`  
**Status:** 0.1.0-dev.1, development-only. **No GUI or native registry adapter yet.**

The new `ItemIndex` searches vanilla and modded item descriptors supplied by
an eventual Minecraft registry bridge. Search is case-insensitive and supports
ordinary text, `@modnamespace` and `#tag` tokens. Results are immutable,
stable, ranked and paginated. `RecipeIndex` supports multiple output recipes
and reverse ingredient usages, including alternate ingredient choices.

These models intentionally **do not** pull recipes directly from datapack JSON:
the future GUI adapter must read actual *synced native recipe data* after
recipe/resource reload and refresh these indices. This will include Sorting
Pipes without a special hardcoded integration.

## Build and test

From HW-Mods repository root:

```bash
# Supply the matching H.O.W.L. API source folder from a sibling checkout.
HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java bash scripts/build-howl-codex.sh
```

The script compiles against the API, runs pure-Java regression tests and
creates `mods/howl-codex/dist/howl-codex-0.1.0-dev.1.jar`.

A `/howlcodex` command reports that the foundation is loaded. **This is not
yet a playable NEI-style browser**; do not distribute or install it as required
first-party content. No item spawning, recipes, vanilla UI or save data are
modified.

## Next implementation milestones

1. Add loader client inventory lifecycle/render/input API:
   https://github.com/HowlingWhispers/HW-CodaLoader/issues/15
2. Native item registry + recipe manager adapters, with resource reload
   invalidation and snapshot mapping smoke tests.
3. Inventory side panel, native sprite rendering, keyboard search, R/U recipe
   navigation, filtering, and clear readable spacing.
4. Real Snapshot 3 in-game testing, permissions for optional creative tools,
   launcher catalog integration only after verification.

Main tracking issue: https://github.com/HowlingWhispers/HW-Mods/issues/10
