# HW Essentials 0.2.0

A CML-native utility mod for **Minecraft Java 26.4 Snapshot 3**. Ships as `hw-essentials-0.2.0.jar` with its own `coda.mod.json`; uses CodaLoader's command API, without Fabric or Forge.

This mod is now part of the **HW-Mods** repository and is published independently to GitHub Releases. CodaLoader 0.0.20+ bundles this version automatically and installs it into the active game profile (including CodaLauncher-managed profiles).

| Command | Result |
| --- | --- |
| `/sethome [name]` | Save your current position and facing. Defaults to `home`. |
| `/home [name]` | Return to that home, after checking the landing spot. |
| `/back` | Return to the departure point of the last successful `/home` or `/back`. |
| `/homes` | List your homes in this world. |
| `/delhome <name>` | Delete a home. |
| `/hwessentials` | Version and command help. |

`/back` remembers one return point per world and player in memory. Successful return trips replace that point, so repeated `/back` swaps between the two positions. Refused teleports preserve the point. Death, ordinary movement and other mods' teleports do not create return points. Restarting Minecraft clears them; saved homes remain intact.

Ten home slots by default. Names allow 1–32 letters, numbers, underscores and hyphens; case is normalized. Set `max-homes=1..100` in `config/hw_essentials/essentials.properties` and restart Minecraft to change the limit.

Homes are stored inside the active world at `cml/hw-essentials/homes/<player UUID>.properties`. Each world and player has separate homes. Writes use an atomic replacement; corrupt files are left intact and reported rather than reset.

Version 0.2 supports singleplayer's integrated server and homes in the player's current dimension. A home in another dimension is retained, but the player must return to that dimension first. Ordinary remote vanilla servers have no CML server bridge. Commands require a player and do not require enabling cheats in a personal singleplayer world.

The server-thread adapter refuses positions outside the world border, collisions, fluid at the destination or missing support underfoot. It loads the destination chunk before checking. This is a conservative landing check, not a complete environmental hazard detector.

CodaLoader installs the bundled mod into the active game profile automatically, including profiles managed by CodaLauncher. It updates only its known bundled JAR; a manually modified conflicting `hw-essentials.jar` is preserved and reported.

The bridge is pinned to Snapshot 3's named APIs. CI checks compilation, loading, storage, command behavior and the reflection adapter against fixtures. Actual registration and teleportation in a live Snapshot 3 world still require in-game verification.

## Building

```bash
# Requires HW-CodaLoader checked out alongside HW-Mods (for API sources)
export HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java
./scripts/build-hw-essentials.sh
```

Output: `dist/hw-essentials-0.2.0.jar` + `.sha256`

## Testing

```bash
./scripts/test-hw-essentials.sh
```

## Distribution

Mod JARs are published to GitHub Releases at `https://github.com/HowlingWhispers/HW-Mods/releases`. CodaLoader fetches a pinned release and verifies SHA-256 before embedding and installing.

The shared command API (`CodaMod`, `CodaContext`, `CodaCommand`, `CodaCommandContext`, `CodaPosition`, `CodaCommands`) and Minecraft hooks remain in **HW-CodaLoader**.

Target: **Minecraft Java 26.4 Snapshot 3**.