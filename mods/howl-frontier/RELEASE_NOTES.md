Frontier 0.2.1: the pioneer and settlers now actually appear.

Fix: in 0.2.0 the pioneer could stay invisible forever on grassy ground. Frontier chose an arrival spot with tall grass in it, the game refused to spawn there, and Frontier retried that same spot every 10 seconds.

- Arrival spots now follow the game's spawn rule exactly: clear air at feet and head, solid dry ground below.
- Several spots along every edge of your land are tried, up to 8 blocks up or down, so grass, trees and hills no longer block arrivals.
- If the game refuses a spot anyway, a different one is tried next instead of the same one forever.
- /frontier status shows a settler who is still on the way and why they have not appeared yet.

Existing 0.2.0 worlds recover by themselves: a pioneer stuck from 0.2.0 appears within about 20 seconds of play after updating.

Requires H.O.W.L. 0.0.45+ and CodaLauncher Nightly. Choose Frontier, then Nightly Play / Install-Repair.
