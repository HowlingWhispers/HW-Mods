H.O.W.L. / BUILDCRAFT EXPERIMENTAL SINGLE-PLAYER TEST
===================================================
This ZIP is NOT a released BuildCraft port. It proves a real Minecraft chest
transaction using temporary glass blocks to stand in for pipes.

ONE ZIP, separate game folder, no edits to your installed CodaLauncher:
1. Extract the whole ZIP into its own empty folder on Windows.
2. Have Java 25 installed and available as "java" in Command Prompt.
3. Double-click Start-BuildCraft-Playtest.bat (initial download needs internet).
   It starts Minecraft Java 26.4 Snapshot 3 in LOCAL-ONLY singleplayer mode.
4. Create a NEW THROWAWAY creative-mode singleplayer world. Do not load your
   cherished saves. If the game does not start, send the complete console log.
5. Use F3 to find exact block coordinates. Place:
   chest A at 0 64 0       (put at least 16 iron ingots in this one)
   glass at 1 64 0
   glass at 2 64 0
   glass at 3 64 0
   chest B at 4 64 0       (leave this chest empty)
   The straight glass row is only a temporary visual pipe marker.
6. Run in chat:
   /buildcraft pulse 0 64 0 4 64 0
   Expected: up to 16 real items removed from chest A and inserted into B.
   Run it again to move more. /buildcraft displays help.
7. Try removing one glass block or filling chest B. The pulse should refuse
   unsafe movement without losing your inventory.

This prototype has no item pipe textures, no custom wrench item, no engine and
no animation. A command is the stand-in for the engine pulse. It is not
"BuildCraft complete" and is NOT installed by the normal CodaLauncher update.
This test uses Minecraft's real loaded chest/barrel item stacks, preserving
components when the native methods match; mapping mismatches fail closed.

The code has Java CI fixture tests but has NOT yet passed a live 26.4
Snapshot 3 playtest. A failed first playtest is useful! Please give the exact
message or full console log. Test world files stay in this ZIP's run folder,
never your regular launcher profile.

SAFETY: do not play existing worlds. Mod-created block entities, double-chest
unions, hoppers and other special inventory types are excluded. Multiplayer is
not supported. No Microsoft sign-in is granted by this local-only test.
