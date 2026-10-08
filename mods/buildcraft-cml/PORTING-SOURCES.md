# BuildCraft for H.O.W.L.: one-to-one port baseline

## Canonical source: latest official BuildCraft stable release **8.0.0**

**Use the ORIGINAL BuildCraft project's RELEASE ARTIFACTS**, not the repository's
moving `8.0.x-1.12.2` development branch, and not an independent reimplementation.

- Official download/release: https://mod-buildcraft.com/pages/download.html
- Original BuildCraft 8.0.0 (April 2, 2025): https://mod-buildcraft.com/releases/BuildCraft/8.0.0/
- **Source-of-truth archive**: https://mod-buildcraft.com/releases/BuildCraft/8.0.0/buildcraft-all-8.0.0-sources.jar
- Original complete game JAR / resource assets: https://mod-buildcraft.com/releases/BuildCraft/8.0.0/buildcraft-all-8.0.0.jar
- Original upstream repository: https://github.com/BuildCraft/BuildCraft
- Changelog for the selected stable release: https://mod-buildcraft.com/pages/buildinfo/BuildCraft/changelog/8.0.0.html

Why this matters: GitHub's "latest release" pane shows `7.99.20` from 2018,
which **is not the latest BuildCraft release**. The project's own download
page labels `8.0.0` the latest stable BuildCraft for Minecraft 1.12.2.
`8.0.x-1.12.2` is a moving development branch with later unpublished changes.

The resource-import script downloads the `8.0.0` original source and binary,
verifies each against BuildCraft's *published Maven SHA1* for that named
version, extracts the actual 8.0.0 `assets/**` into our Nightly JAR, and
records source locations and SHA256 digests under
`META-INF/buildcraft-upstream/SOURCE.txt`. The original source JAR is kept in
the CI build cache to port source methods individually.

**Original gameplay, items, textures, model shapes, recipes, and balance are
canonical.** Do not invent replacements for existing BuildCraft systems.

### One-to-one behavior details in 8.0.0

The exact 8.0.0 changelog is binding where older branches disagree:
- Engines produce power every tick for kinesis pipes, **except that wooden
  extraction pipes retain an engine pulse**. A manually invoked
  `/buildcraft pulse` is NOT equivalent to the final engine/pipes.
- Kinesis transfer limits are enforced: cobblestone 4 MJ/t, stone 8 MJ/t,
  wooden and sandstone 16 MJ/t, quartz 32 MJ/t, gold 128 MJ/t.
- Diamond and iron kinesis pipes include their configurable flow limits.
- MJ and (optionally) RF pipes have the original 8.0.0 textures and rules.

Any adapters to snapshot Minecraft/H.O.W.L. should alter host interfaces,
**not** original BuildCraft behavior or crafting.

## Licensing

BuildCraft's history includes older MMPL 1.0.1 and newer MPL 2.0 covered
files. The port must retain applicable copyright/license notices, disclose
modifications, and offer the corresponding modified source. The official
BuildCraft artifact and its matching source JAR must stay accessible.
`LICENSE`, `LICENSE-NEW`, `LICENSE.BUILDCRAFT` accompany the imported
original assets. Check per-file notices when porting actual source methods.
Neither H.O.W.L. nor BuildCraft's original authors endorse each other.

## H.O.W.L. compatibility boundary

The original release is for **Minecraft 1.12.2 + Forge**. It cannot load
unchanged into **Minecraft Java 26.4 Snapshot 3** with H.O.W.L., which
intentionally does not run Forge or Fabric. The port must adapt block/item
registration and Creative tabs, block entities, inventories, networking,
rendering and registry lifecycle. This is *host-specific compatibility work*,
not new design or a creative rewriting of BuildCraft.

### First **real in-game** checkpoint

- Port original **wooden item pipe**, **cobblestone item pipe**, **wrench**,
  **redstone engine**, original textures, models and recipes.
- Register their actual blocks/items and BuildCraft Creative inventory group.
- Connect original extraction and pipe movement semantics to real chest
  inventories, with save/reload and no duplication.
- Verify in a **throwaway Nightly Minecraft world**, with real placement,
  connected textures, engine animation and moving stacks.

Until that passes in actual Minecraft, the Nightly mod is development-only.
The existing temporary vanilla-glass `/buildcraft pulse` experiment is not
a one-to-one BuildCraft gameplay test and must not be marketed as such.

## Secondary references (never supersede release 8.0.0)

- Original moving development branch: https://github.com/BuildCraft/BuildCraft/tree/8.0.x-1.12.2
- Independent BuildCraft Refabricated: https://github.com/fromdisposition/BuildCraftRefabricated

Neither is the selected release nor an automatic dependency. Retain native
H.O.W.L. loading rather than adding Forge, Fabric or a second mod loader.
