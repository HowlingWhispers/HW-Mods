Frontier 0.4.5: ask your settlers for things.

New: `/frontier ask <name>` gives one settler a request. Requests come before their own job, in the order you gave them, during the day. Each settler takes up to 5, and they are saved with the world.

- `/frontier ask Asta craft oak_door 3`: Asta crafts it with the game's own recipe, from what is in the stockpile, and puts what she made back in the stockpile. Recipes bigger than 2x2 (doors, chests...) need a crafting table within 3 blocks of the stockpile. If something is missing, chat says what: the ingredients, the table, or that there is no such recipe. Smelting is not crafted.
- `/frontier ask Asta come`: she comes to where you stand, waits a minute, then goes back to work.
- `/frontier ask Asta` lists her requests; `/frontier ask Asta cancel` drops them.
- `/frontier settlers` shows how many requests each settler has.

Settlers can be named by name or by their short ID.

Requires H.O.W.L. 0.0.47+ and CodaLauncher Nightly. Choose Frontier, then Nightly Play / Install-Repair.
