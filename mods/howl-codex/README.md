# H.O.W.L. Codex 0.1.0-dev.2: Item browser with recipe/use lookup

Development-only, original-source NEI/TMI-style item browser for **Minecraft Java
26.4 Snapshot 3** using the H.O.W.L. inventory-panel API from
[HW-CodaLoader #16](https://github.com/HowlingWhispers/HW-CodaLoader/pull/16).

## How to use

1. Use the matching development loader from `feat/codex-inventory-panel-api`.
2. Install the Codex JAR only in an **isolated testing profile**.
3. Start a test world, open the vanilla player inventory with **E**, and press **Codex**.
4. Type an item name, `@minecraft`, `@howl_sorting_pipes`, or `#minecraft:ingots`.
5. Select an item. Press **R Known recipes** or **U Known uses** to view
   server-synchronized recipe displays available to that player.
6. Choose a recipe/usage for the output item, quantity, type and resolved
   ingredient alternatives. Use **Back** to return.

The search-results list and known recipe results now use **native
`GuiGraphicsExtractor.item(ItemStack, ...)` sprites**. All item stack
objects come from Minecraft's actual item registry. No custom emoji icons.
The mod only adds GUI widgets; it does **not** change inventory contents,
vanilla crafting, advancements, save files or permissions.

**Limitations:** Client-visible recipe displays cover the player's known
recipes, not necessarily every datapack recipe on the server. Unavailable
tag ingredients and unsupported displays are not guessed. Item registry
snapshots are built when Codex is first opened; dynamic registry changes
require reopening the client. The full in-game rendered UI still needs an
interactive Snapshot 3 playtest before Stable or launcher auto-install.
No item-give/cheat feature yet. Only the player inventory is supported,
not chests or creative inventory.

## Build

```bash
HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java bash scripts/build-howl-codex.sh
```

This produces `mods/howl-codex/dist/howl-codex-0.1.0-dev.2.jar` and a
SHA-256. The CI workflow runs pure Java registry/recipe tests, simulated
GUI interaction tests (including R/U views and icon rendering), and a
separate descriptor probe against the checksum-verified official Mojang
26.4 Snapshot 3 client.

Track the mod in [HW-Mods #10](https://github.com/HowlingWhispers/HW-Mods/issues/10)
and the required loader in
[HW-CodaLoader #15](https://github.com/HowlingWhispers/HW-CodaLoader/issues/15).
