H.O.W.L. BUILDCRAFT 0.0.1-dev.1 - DEVELOPER BUILD
=================================================
This file supersedes the obsolete chest/glass /buildcraft pulse instructions.
The old command and instant inventory teleport are NOT in the current mod.

Original source reference: BCCE 8.0.23 (NOT the H.O.W.L. mod version).
This development build registers real wooden and cobblestone pipes, a native
redstone engine, a wrench item and the BuildCraft Creative tab. Engine MJ,
heat and piston timing run on the server and persist under the active save.
Pipe-route survey runs every 100 server ticks. To inspect a route, stand
within 16 blocks of a placed redstone engine and type /buildcraft status.

Arrange an engine next to a wooden extraction pipe, an ordinary chest/barrel
on another side of that wooden pipe, and cobblestone pipes leading to a
second inventory. The in-game status will confirm whether real placed pipes
form a loaded route. IT WILL NOT MOVE ITEMS YET.

There are no original moving item packets or pipe block entities, so this is
not yet a playable BuildCraft transport system. Do not use valuable worlds.
Do not promote to Stable or claim one-to-one feature parity.
Use the CI development artifacts, not any older published Nightly marked
as a player transport test. Test with a disposable single-player world.

IMPORTANT: the JAR package filename still says 0.1.0-dev for compatibility
with currently deployed CodaLauncher Nightly installers. coda.mod.json
contains the authoritative H.O.W.L. BuildCraft version 0.0.1-dev.1.
