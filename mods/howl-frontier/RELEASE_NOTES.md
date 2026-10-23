Frontier 0.3.2: clearing in the right order, and a builder crash fixed.

Fixed: "Update failed: Invalid transfer size". A builder fetching more than 64 of one item from the stockpile (the 78 logs for a log cabin) asked for too many at once, so the builder stopped working. It now takes up to 64 at a time and comes back for the rest.

From the 0.3.1 play test: the builder broke leaves by hand first and never fetched an axe, because the tree stood just outside the plot with only its leaves hanging over it.

Clearing now always follows the same order, and /frontier settlers shows the step:

1. (1/3) Trees. The builder fetches an axe from the stockpile, then fells every natural tree that stands on the plot or hangs its leaves over it, nearest first, whole and from the top. The logs go to the stockpile and count towards the log cabin.
2. (2/3) Leaves and plants still on the site, top down, by hand.
3. (3/3) The ground inside the walls, by hand.

Without an axe in the stockpile the builder waits at step 1 and says so. Trees outside your land and anything you built are never touched.

Requires H.O.W.L. 0.0.46+ and CodaLauncher Nightly. Choose Frontier, then Nightly Play / Install-Repair.
