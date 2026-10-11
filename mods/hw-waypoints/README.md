# HW Waypoints 0.1.0-dev.1

**Development-only** H.O.W.L. addon for Minecraft Java 26.4 Snapshot 3. Implements the command-based portion of the HW Waypoints concept. It has not yet been verified in a live game.

| Command | Action |
| --- | --- |
| `/wp set <name> [note] [--replace]` | Save position and facing; overwrite only with `--replace`. |
| `/wp go <name>` | Teleport after H.O.W.L.'s server-side landing safety checks. |
| `/wp list [page]` | List eight waypoints per page, nearest first and other dimensions last. |
| `/wp where <name>` | Show horizontal distance, compass heading, height difference, and note. |
| `/wp back` | Return to departure point; repeating swaps between both positions. |
| `/wp del <name>` | Delete a waypoint. |

Waypoints are shared per world, not per player. Names are case-insensitive and permit only `a-z`, `0-9`, `_`, `-` (1-24 characters). Limit: 200 waypoints per world, 80 characters per note. Cross-dimension travel is refused. Return points are in memory per world/player and clear on restart. Refused teleports do not change them.

Data: `<world>/hw/hw_waypoints/data.json`, saved with fsync and atomic replacement. `data.json.bak` holds the previous good snapshot. Corrupt files are renamed to `.corrupt-<timestamp>` and preserved; a valid backup is loaded if possible. Atomic moves must be supported on the save filesystem.

No radar or graphical screen is included. Those require the unverified live Snapshot 3 screen hook. HW Essentials and its personal homes are unchanged.

## Build and test

Use Java 21+ with a sibling HW-CodaLoader checkout:

```bash
export HW_CODALOADER_API_DIR=../HW-CodaLoader/src/main/java
bash scripts/build-hw-waypoints.sh
bash scripts/test-hw-waypoints.sh
```

Alternatively set `HW_CODALOADER_API_JAR` to the SDK API JAR. Output: `dist/hw-waypoints-0.1.0-dev.1.jar` and SHA-256. Loader API classes are compile-only and never bundled.

## Live acceptance in a disposable single-player world

1. Load H.O.W.L. 0.0.27 + Minecraft 26.4 Snapshot 3 with this development JAR; verify the `/wp` registration log.
2. Set `/wp set farm wheat crops`, move away, inspect `/wp where farm` and `/wp list`.
3. Test safe `/wp go farm`, `/wp back`, and repeated `/wp back`; facing and departure should be preserved.
4. Test deliberately unsafe locations and other dimensions: teleports must be refused without movement or return-point changes.
5. Reopen the world to confirm waypoint persistence and a cleared `/wp back`.
6. Check world isolation, duplicate names, case folding, `--replace`, notes, pagination, and atomic storage.
7. Inspect `data.json.bak`. Corruption recovery must be tested only on a throwaway save.

**CI and fixture success do not prove live game compatibility.** Do not publish this to the mandatory first-party player release/install pipeline until live tests pass.
