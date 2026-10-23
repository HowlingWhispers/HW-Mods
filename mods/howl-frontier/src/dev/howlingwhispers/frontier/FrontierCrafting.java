package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierSimulation.add;
import static dev.howlingwhispers.frontier.FrontierSimulation.text;

/**
 * Settlers craft what they need with H.O.W.L. 0.0.47's crafting hook (L8): the game's own recipes,
 * real items and a real crafting table next to the stockpile. Frontier only knows which ingredients
 * to gather; the loader checks and makes everything. On older loaders this stays off and settlers
 * build log cabins and ask for doors and beds, as before.
 */
final class FrontierCrafting {
    static final List<String> COLOURS = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

    /** What Frontier needs to know about a recipe: what to gather, how many it makes, whether it needs a table. */
    record Recipe(String id, String result, int yield, Map<String, Integer> ingredients, boolean table) {}

    private final FrontierSimulation sim;
    /** True when the running loader has craft() (H.O.W.L. 0.0.47+). Tests set it directly. */
    boolean enabled = detect();

    FrontierCrafting(FrontierSimulation sim) { this.sim = sim; }

    static boolean detect() {
        try {
            CodaLivingWorld.class.getMethod("craft", UUID.class, CodaBlockPos.class, CodaBlockPos.class, String.class, int.class);
            return true;
        } catch (NoSuchMethodException | LinkageError e) {
            return false;
        }
    }

    /**
     * Calls craft() by reflection, so Frontier still compiles and runs against the H.O.W.L. 0.0.46 API,
     * where the method does not exist; crafting simply stays off there.
     */
    static int craft(CodaLivingWorld world, UUID id, CodaBlockPos workbench, CodaBlockPos supplies, String recipe, int times) throws Exception {
        java.lang.reflect.Method m;
        try {
            m = CodaLivingWorld.class.getMethod("craft", UUID.class, CodaBlockPos.class, CodaBlockPos.class, String.class, int.class);
        } catch (NoSuchMethodException older) {
            m = world.getClass().getMethod("craft", UUID.class, CodaBlockPos.class, CodaBlockPos.class, String.class, int.class);
        }
        try {
            return (Integer) m.invoke(world, id, workbench, supplies, recipe, times);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception x) throw x;
            throw e;
        }
    }

    // ---- Recipes ------------------------------------------------------------------------------------

    static boolean isPlanks(String id) { return id.endsWith("_planks"); }

    /** "#planks" in an ingredient list means any planks, as the game's planks tag does. */
    static final String ANY_PLANKS = "#planks";

    /** The recipe Frontier uses for an item, or null. For beds the colour must be decided first. */
    static Recipe recipe(String item) {
        String name = item.replace("minecraft:", "");
        if (name.endsWith("_planks")) {
            String wood = name.substring(0, name.length() - "_planks".length());
            if (!Palette.WOODS.contains(wood)) return null;
            return new Recipe(item, item, 4, Map.of("minecraft:" + wood + "_log", 1), false);
        }
        if (name.endsWith("_door") && !name.equals("iron_door")) {
            String wood = name.substring(0, name.length() - "_door".length());
            if (!Palette.WOODS.contains(wood)) return null;
            return new Recipe(item, item, 3, Map.of("minecraft:" + wood + "_planks", 6), true);
        }
        if (name.endsWith("_bed")) {
            String colour = name.substring(0, name.length() - "_bed".length());
            if (!COLOURS.contains(colour)) return null;
            return new Recipe(item, item, 1, Map.of("minecraft:" + colour + "_wool", 3, ANY_PLANKS, 3), true);
        }
        return switch (name) {
            case "stick" -> new Recipe(item, item, 4, Map.of(ANY_PLANKS, 2), false);
            case "crafting_table" -> new Recipe(item, item, 1, Map.of(ANY_PLANKS, 4), false);
            case "ladder" -> new Recipe(item, item, 3, Map.of("minecraft:stick", 7), true);
            default -> null;
        };
    }

    /** A bed colour the stockpile has 3 wool for, or null. */
    static String bedFromWool(Map<String, Integer> stock) {
        for (String colour : COLOURS) if (stock.getOrDefault("minecraft:" + colour + "_wool", 0) >= 3) return "minecraft:" + colour + "_bed";
        return null;
    }

    private static int have(Map<String, Integer> stock, String ingredient) {
        if (!ingredient.equals(ANY_PLANKS)) return stock.getOrDefault(ingredient, 0);
        int n = 0;
        for (var e : stock.entrySet()) if (isPlanks(e.getKey())) n += e.getValue();
        return n;
    }

    /** Which planks to make when a recipe takes any planks: the wood the stockpile has most logs of. */
    private static String planksToMake(Map<String, Integer> stock, String preferred) {
        if (preferred != null && stock.getOrDefault(preferred.replace("_planks", "_log"), 0) > 0) return preferred;
        String best = null;
        int most = 0;
        for (var e : stock.entrySet())
            if (FrontierJobs.isLog(e.getKey()) && e.getValue() > most && Palette.WOODS.contains(e.getKey().replace("minecraft:", "").replace("_log", ""))) {
                most = e.getValue();
                best = e.getKey().replace("_log", "_planks");
            }
        return best;
    }

    // ---- The workbench ------------------------------------------------------------------------------

    /** A crafting table within 3 blocks of the stockpile, or null. */
    static CodaBlockPos workbench(CodaLivingWorld world, Settlement c) throws Exception {
        if (c.stockpile == null) return null;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    var p = add(c.stockpile, dx, dy, dz);
                    if (dx * dx + dy * dy + dz * dz > 9) continue;
                    var b = world.block(p);
                    if (b.isPresent() && b.get().id().equals("minecraft:crafting_table")) return p;
                }
        return null;
    }

    /** A free spot beside the stockpile for a new crafting table: air, solid below, not where people stand in the way. */
    private static CodaBlockPos tableSpot(CodaLivingWorld world, Settlement c, CodaLivingWorld.Actor actor) throws Exception {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
            var p = add(c.stockpile, d[0], 0, d[1]);
            // Not where someone stands: the game refuses to place a block into an entity.
            boolean occupied = false;
            for (var other : c.settlers.values()) {
                var a = other == null ? null : world.actor(other.id).orElse(null);
                if (a != null && Math.abs(a.x() - p.x() - .5) < 1 && Math.abs(a.z() - p.z() - .5) < 1 && Math.abs(a.y() - p.y()) < 2) occupied = true;
            }
            if (occupied) continue;
            var here = world.block(p);
            var below = world.block(add(p, 0, -1, 0));
            if (here.isPresent() && here.get().air() && below.isPresent() && below.get().solid() && c.territory().contains(p)) return p;
        }
        return null;
    }

    // ---- Doing it -------------------------------------------------------------------------------------

    /**
     * Work towards having {@code wanted} of {@code item} in the stockpile, one step per call: gather
     * ingredients by crafting them first (logs into planks, planks into sticks), make and place a
     * crafting table if a recipe needs one, then craft at the stockpile and deliver the result.
     * Returns true while it is working on it; false when it cannot (then {@code s.activity} says why,
     * or names the raw material that is missing, so the caller can gather it).
     */
    boolean craftFor(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, String item, int wanted) throws Exception {
        if (!enabled || c.stockpile == null) return false;
        var stock = FrontierBuilder.stockpileItems(view, c);
        var r = recipe(item);
        if (r == null) return false;
        // Each ingredient first: craft it if it can be crafted, otherwise report what is missing.
        int batches = Math.max(1, Math.min(16, (wanted + r.yield() - 1) / r.yield()));
        for (var e : r.ingredients().entrySet()) {
            int need = e.getValue() * batches, got = have(stock, e.getKey());
            if (got >= e.getValue()) continue; // at least one batch can be made; more come later
            String sub = e.getKey().equals(ANY_PLANKS) ? planksToMake(stock, null) : e.getKey();
            if (sub == null || recipe(sub) == null) {
                s.activity = "Crafting " + nice(item) + ": needs " + (e.getKey().equals(ANY_PLANKS) ? "logs for planks" : nice(e.getKey())) + " in the stockpile";
                return false;
            }
            if (craftFor(world, view, c, s, actor, sub, need - got)) return true;
            return false;
        }
        CodaBlockPos table = null;
        if (r.table()) {
            table = workbench(world, c);
            if (table == null) return placeTable(world, view, c, s, actor, stock);
        }
        // At the stockpile (and so the table) with an empty bag, so only the stockpile's items are used.
        if (actor.distanceSquared(c.stockpile) > 8 || (table != null && actor.distanceSquared(table) > 8)) {
            s.activity = "Going to the " + (table != null ? "workbench" : "stockpile") + " to make " + nice(item);
            var spot = standBetween(world, c.stockpile, table);
            if (spot == null || !sim.navigate(world, s, spot, .65)) s.activity = "Cannot reach the " + (table != null ? "workbench" : "stockpile") + " to make " + nice(item);
            return true;
        }
        if (world.carried(s.id) > 0) {
            if (world.deliver(s.id, c.stockpile) == 0) { s.activity = "The stockpile is full: make room in it"; return true; }
            return true;
        }
        int possible = batches;
        for (var e : r.ingredients().entrySet()) possible = Math.min(possible, have(stock, e.getKey()) / e.getValue());
        if (possible < 1) return false;
        int made;
        try {
            made = craft(world, s.id, table, c.stockpile, r.id(), possible);
        } catch (UnsupportedOperationException | NoSuchMethodException | LinkageError old) {
            enabled = false; // an older loader after all: back to asking the player
            return false;
        }
        if (made < 1) {
            s.activity = "Crafting paused: could not make " + nice(item) + " at " + (table != null ? "the workbench at " + text(table) : "the stockpile");
            return true;
        }
        s.delivering = true; // the results go straight into the stockpile
        s.activity = "Crafted " + made * r.yield() + " " + nice(item) + (table != null ? " at the workbench" : "");
        return true;
    }

    /** A place to stand within reach of the stockpile and, if there is one, the workbench (the loader allows 3 blocks). */
    private static CodaBlockPos standBetween(CodaLivingWorld world, CodaBlockPos stockpile, CodaBlockPos table) throws Exception {
        CodaBlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                var spot = FrontierSimulation.standing(world, add(stockpile, dx, 0, dz), 0);
                if (spot == null) continue;
                double a = FrontierJobs.distanceSquared(spot, stockpile), b = table == null ? 0 : FrontierJobs.distanceSquared(spot, table);
                if (a > 6.5 || b > 6.5) continue;
                if (a + b < bestScore) { bestScore = a + b; best = spot; }
            }
        return best;
    }

    /** No crafting table yet: make one from 4 planks (crafting the planks first) and place it by the stockpile. */
    private boolean placeTable(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor, Map<String, Integer> stock) throws Exception {
        // A crafted table goes to the stockpile first (craftFor delivers results), then it is placed from there.
        if (stock.getOrDefault("minecraft:crafting_table", 0) == 0)
            return craftFor(world, view, c, s, actor, "minecraft:crafting_table", 1);
        var spot = tableSpot(world, c, actor);
        if (spot == null) { s.activity = "Needs a workbench: no free spot beside the stockpile. Place a crafting table within 3 blocks of it"; return false; }
        if (actor.distanceSquared(c.stockpile) > 4) {
            s.activity = "Bringing a crafting table to the stockpile";
            sim.moveNear(world, s, c.stockpile);
            return true;
        }
        if (!sim.pace.take(s.id, true)) { s.activity = "Setting up a workbench"; return true; }
        if (world.place(s.id, spot, "minecraft:crafting_table", c.stockpile)) {
            s.activity = "Set up a workbench beside the stockpile";
            world.announce(Chat.good(s.name + " set up a workbench") + Chat.label(" beside the stockpile at ") + Chat.value(text(spot)) + Chat.label(". Settlers craft there now."));
        } else {
            s.activity = "Could not set up a workbench at " + text(spot);
        }
        return true;
    }

    static String nice(String id) { return id.replace("minecraft:", "").replace('_', ' '); }
}
