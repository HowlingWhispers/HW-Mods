# BuildCraft CML port: source references

## Modern reference (prefer for Minecraft-facing behavior)

- **BuildCraft Refabricated**: https://github.com/fromdisposition/BuildCraftRefabricated
- Mainline: **Minecraft Java 26.3**, Java 25, Fabric Loader 0.19.5+, Fabric API 0.160.5+26.3.
- Includes modern implementations of transport, MJ energy, engines, quarry, oil, pumps, gates, builders, silicon machinery and robotics.
- Repository declares **MPL-2.0**. Verify every file and third-party dependency before copying source or assets.
- This is an *architecture/implementation reference*, **not** a binary dependency. Do not import Fabric API, Fabric Transfer API, or Team Reborn Energy directly into CodaLoader.

## Historical reference (behavioral authority)

- Original BuildCraft: https://github.com/BuildCraft/BuildCraft
- Use `8.0.x-1.12.2` as the reference for historic behavior.
- Upstream code distinguishes MPL-2.0 (`LICENSE-NEW`) and older MMPL (`LICENSE`). Avoid mixing code with incompatible redistribution requirements. Preserve contributor notices.

## Proposed adaptation

| BuildCraft Refabricated responsibility | CodaLoader-owned generic hook | HW-Mods gameplay |
| --- | --- | --- |
| Fabric block/item registration | Version-pinned registry lifecycle | Pipe, wrench, engine, quarry definitions |
| Fabric block entities and world ticks | Server-thread block-entity adapter and tick callbacks | Pipe buffers, engines, machines |
| Fabric Transfer API inventory/fluid | Vanilla inventory/fluid adapter SPI | Pipe extraction and fluid movement |
| Team Reborn Energy | Optional energy interoperability adapters (later) | Native MJ network (first) |
| Minecraft models/rendering | Asset/model registration and client packet hooks | Pipe contents, quarry visuals, animations |
| Fabric networking | Versioned CML server-to-client packets | Visible transport, GUI state |

## First in-game milestone

One pipe between two vanilla chests, plus a wrench, in a local 26.4 Snapshot 3 world. Moving stacks must survive save/reload and unloaded chunks without duplication. No automatic install or public release until that works. Tracked under https://github.com/HowlingWhispers/HW-CodaLoader/issues/1.

## Current state

`PipeNetwork.java` is a new, Minecraft-independent prototype with six passing isolated JVM tests. No original BuildCraft/Refabricated code or assets are currently included in HW-Mods.
