H.O.W.L. BuildCraft 0.0.1-dev.1
HUMAN SMOKE TEST: Native pipes, Creative inventory and world reload
==============================================================

This Nightly is for ONE real-world compatibility test on Minecraft Java
26.4 Snapshot 3. It is not a functioning item transport release.

SOURCE: Original BCCE 8.0.23, pinned commit
23c6af379676ce5262c5c0cb6f1f331edc9b12c6.
RUNTIME: H.O.W.L. 0.0.33 prerelease (not Stable).

1. In CodaLauncher select NIGHTLY, not Stable. Update the Nightly
   H.O.W.L. runtime to v0.0.33; then install/update the optional BuildCraft
   mod in Add-ons. Confirm both versions before entering a world.
2. Create a brand-new disposable Creative single-player world.
3. Open Creative inventory. Confirm the BuildCraft tab and find wooden item
   pipe, cobblestone item pipe, redstone engine and wrench. Note any crashes.
4. Place a chest, wooden pipe against it, two cobblestone pipes, and another
   chest at the far end. Place a redstone engine adjacent to the wooden pipe
   and power the engine with a lever.
5. Type /buildcraft status. It should report a real route if the blocks are
   connected and the chunks loaded. It should also show 'Native pipe-holder
   ticks this session: N' where N increases during gameplay. If N stays zero,
   the Minecraft block entity ticker is NOT working and we need the log.
6. Exit to title, re-enter the SAME test world. Verify the native pipe blocks
   remain placed and continue receiving ticks (new session count may reset
   when Minecraft is restarted). Try breaking and re-placing one pipe.
7. Provide the entire CURRENT Minecraft log and a screenshot of BuildCraft
   Creative tab and the /buildcraft status messages.

IMPORTANT: Real BCCE TilePipeHolder state, PipeFlowItems, travelling stacks,
moving item animations, MJ hookups and original wrench actions are NOT yet
ported. DO NOT EXPECT CHEST-TO-CHEST ITEMS TO MOVE. Items must remain in
their starting chest: no teleportation or placeholder transfer was added.

Never load valuable saves into this Nightly. Stable is separate.
If a class/method mismatch or save failure occurs, STOP using that
test world and send the log. Do not carry the world into Stable.
