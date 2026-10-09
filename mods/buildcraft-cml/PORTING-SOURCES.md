# BuildCraft Community Edition for H.O.W.L.

The user-selected upstream is [BCCE-team/BuildCraft](https://github.com/BCCE-team/BuildCraft),
version **8.0.23**, pinned to commit
`b1b166d29da797abf6df3e0618a3bd62f14bc41e` (2026-10-08).
The previous original BuildCraft 8.0.0 baseline has been superseded.

Use the modern **1.21.11** source family and shared API v2 as the reference.
Forge/NeoForge registration, inventories, networking and rendering must be
adapted to **H.O.W.L. / Minecraft 26.4 Snapshot 3**. The BCCE JAR cannot run
unchanged. Do not add a second mod loader or downgrade Minecraft.

The importer checks the exact Git commit and clean cached checkout, verifies
upstream version metadata, copies resource layers in BCCE's precedence order,
and bundles its MPL-2.0 license and pinned source URL in the mod JAR.
Imported PNG files are unchanged. Pipes, wrench and engine images match the
original BuildCraft 8.0.0 assets byte for byte. Snapshot 3 models must use unique
atlas assignments and the original dimensions, face textures and UVs.

The current H.O.W.L. transport adapter remains an early prototype. BCCE's
modern engine, wooden-pipe extraction, renderer and persistence code are the
reference for the next gameplay ports; cached source does not mean those
classes are already running under H.O.W.L.

First in-game checkpoint: place wooden/cobblestone pipes and a powered
redstone engine, open the BuildCraft creative tab, and transfer real items
between chests without duplication or loss. Then port connected rendering,
engine MJ/temperature and animations, wrench actions and save/reload behavior.
Recipes, the remaining pipes, fluids, quarry, machinery, builders and robotics
still require native ports and gameplay verification.

Retain BCCE and original BuildCraft per-file copyright notices, preserve
MPL-2.0 obligations and provide corresponding modified source. Neither
upstream project endorses this unofficial H.O.W.L. port.
