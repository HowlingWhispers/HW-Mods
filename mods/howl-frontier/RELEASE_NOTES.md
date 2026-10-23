Frontier 0.4.2: miners keep digging on their own.

From the 0.4.1 play test: Asta dug a block, then stood still in the mine until nudged, dug one more, and stopped again.

Why: after each block she was sent exactly one step forward. The game's pathfinder counts anything within one block as already reached, so it planned no movement at all, and she kept waiting to arrive.

Now:
- A miner digs as soon as the face is within reach, wherever she stands in the mine, instead of insisting on the exact step behind it.
- When she does need to move, she walks three steps ahead (or two), never one, so the pathfinder always moves her.

Requires H.O.W.L. 0.0.46+ and CodaLauncher Nightly. Choose Frontier, then Nightly Play / Install-Repair.
