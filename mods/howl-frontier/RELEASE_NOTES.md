Frontier 0.4.3: place and break speed settings, and beds that do not count are explained.

From the play test:

- "I placed two beds and there is only one settler, but status says all beds are claimed." A settler's bed needs air right above the pillow and a solid roof 2-6 blocks above it; the second bed had none, so it was silently ignored. Now placing such a bed tells you at once, /frontier status lists it with the reason ("Bed at x y z: it has no roof 2-6 blocks above the pillow"), and Arrivals points at it. Add a roof and it counts.
- "There is no Frontier config to set place and break speed." There is now: config/howl_frontier/howl-frontier.properties in the Frontier profile (%APPDATA%\.howlingshispers\nightly\profiles\frontier\minecraft), created on first start.
  - place_per_second (default 1, 0.25 to 8): blocks a settler places per second while building.
  - break_per_second (default 1, 0.25 to 8): blocks broken per second while clearing, taking down, mining and felling.
  Changes apply the next time a world is opened. The startup log shows the pace in use.

Also includes 0.4.2: miners keep digging on their own instead of standing still until nudged.

Requires H.O.W.L. 0.0.46+ and CodaLauncher Nightly. Choose Frontier, then Nightly Play / Install-Repair.
