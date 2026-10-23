package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierSimulation.add;

/**
 * Plans the settlement on a grid of 8x8-block cells: surveys the real terrain, gives each
 * cell a role, routes roads from the square and picks building plots. It only reads blocks,
 * so it needs no loader changes; builders (0.3) will only ever build what it approves.
 * Survey results are runtime only and are redone after the world is reopened.
 */
final class FrontierPlanner {
    static final int CELL = 8, MARGIN = 3, SEARCH = 24;
    static final int MAX_FLAT = 2, MAX_ROAD_RISE = 4, MAX_DROP = 4, HOLLOW_DEPTH = 3, FOREST_TREES = 3;
    /** Seconds between cell surveys: a full 3x3-chunk survey takes about a minute. */
    static final int SURVEY_EVERY = 2;

    enum Role {
        SQUARE("Square", Chat.GRAY), OPEN("Open", Chat.GREEN), FOREST("Forest", Chat.S + "2"), FARM("Farm", Chat.GOLD),
        YOURS("Your builds", Chat.WHITE), WATER("Water", Chat.S + "9"), HAZARD("Hazard", Chat.RED), STEEP("Steep", Chat.S + "5"),
        UNKNOWN("Not surveyed yet", Chat.DARK_GRAY);

        final String label, colour;
        Role(String label, String colour) { this.label = label; this.colour = colour; }
    }

    record CellPos(int x, int z) {
        static CellPos of(CodaBlockPos p) { return new CellPos(Math.floorDiv(p.x(), CELL), Math.floorDiv(p.z(), CELL)); }
        int minX() { return x * CELL; }
        int minZ() { return z * CELL; }
        CellPos step(int dx, int dz) { return new CellPos(x + dx, z + dz); }
    }

    /** What the survey found in one cell. {@code level} is the typical floor height. */
    record Survey(CellPos cell, Role role, String reason, int minY, int maxY, int level, int trees,
                  Map<String, Integer> treeLogs, Map<String, Integer> surface) {}

    /** The next building: its cell, the cells of road to lay first, and which side its door faces. */
    record Plot(CellPos cell, List<CellPos> road, String doorFacing, int level) {}

    private final Map<CellPos, Survey> surveys = new HashMap<>();
    private int cursor;

    void clear() {
        surveys.clear();
        cursor = 0;
    }

    Optional<Survey> survey(CellPos cell) { return Optional.ofNullable(surveys.get(cell)); }

    // ---- The grid ----------------------------------------------------------------------------

    static List<CellPos> cells(Territory t) {
        List<CellPos> all = new ArrayList<>();
        for (int z = Math.floorDiv(t.minBlockZ(), CELL); z <= Math.floorDiv(t.maxBlockZ(), CELL); z++)
            for (int x = Math.floorDiv(t.minBlockX(), CELL); x <= Math.floorDiv(t.maxBlockX(), CELL); x++) all.add(new CellPos(x, z));
        return all;
    }

    static boolean square(Settlement c, CellPos cell) {
        return ChunkPos.of(new CodaBlockPos(cell.minX(), 0, cell.minZ())).equals(ChunkPos.of(c.campfire));
    }

    /** Survey the next cell in turn. Called once a second; works every {@link #SURVEY_EVERY} seconds. */
    void tick(CodaLivingWorld world, Settlement c) throws Exception {
        if ((c.elapsed / 20) % SURVEY_EVERY != 0) return;
        var all = cells(c.territory());
        surveys.keySet().retainAll(new HashSet<>(all));
        CellPos next = all.get(Math.floorMod(cursor++, all.size()));
        surveys.put(next, surveyCell(world, c, next));
    }

    /** Survey every cell now. For tests and for /frontier plan's first use. */
    void surveyAll(CodaLivingWorld world, Settlement c) throws Exception {
        for (var cell : cells(c.territory())) surveys.put(cell, surveyCell(world, c, cell));
    }

    // ---- Surveying one cell ---------------------------------------------------------------------

    /** Ground height, the block on top, and whether the column starts in water or lava. */
    private record Column(int y, String top, boolean water, boolean lava, boolean known) {}

    private static boolean plant(CodaLivingWorld.Block b) {
        if (b.solid() || b.fluid()) return false;
        return !b.air();
    }

    /** Leaves and tree trunks are not ground: the ground is under the tree. */
    private static boolean overGround(CodaLivingWorld.Block b) {
        return b.air() || plant(b) || b.id().endsWith("_leaves") || FrontierJobs.isLog(b.id());
    }

    /**
     * Find the ground in one column, starting near {@code hint} (the neighbouring column's
     * ground) to keep block reads low: usually about 6 reads instead of 48.
     */
    private static Column column(CodaLivingWorld world, int x, int z, int centreY, int hint) throws Exception {
        int lo = centreY - SEARCH, hi = centreY + SEARCH;
        int start = Math.max(lo, Math.min(hi, hint + 3));
        var first = world.block(new CodaBlockPos(x, start, z));
        if (first.isEmpty()) return new Column(0, "", false, false, false);
        if (overGround(first.get())) {
            for (int y = start - 1; y >= lo; y--) {
                var b = world.block(new CodaBlockPos(x, y, z));
                if (b.isEmpty()) return new Column(0, "", false, false, false);
                if (!overGround(b.get())) return ground(y, b.get());
            }
            return new Column(lo - 1, "", false, false, true); // a deep drop
        }
        var top = first.get();
        int topY = start;
        for (int y = start + 1; y <= hi; y++) {
            var b = world.block(new CodaBlockPos(x, y, z));
            if (b.isEmpty()) return new Column(0, "", false, false, false);
            if (overGround(b.get())) break;
            top = b.get();
            topY = y;
        }
        return ground(topY, top);
    }

    private static Column ground(int y, CodaLivingWorld.Block b) {
        if (b.fluid()) return new Column(y, b.id(), !b.id().contains("lava"), b.id().contains("lava"), true);
        return new Column(y, b.id(), false, false, true);
    }

    /** Blocks the world does not generate on its own: if any are present, the cell is someone's build. */
    static boolean made(String id) {
        if (id.startsWith("minecraft:stripped_")) return true;
        for (String part : List.of("_planks", "brick", "cobblestone", "_slab", "_stairs", "_wall", "_fence", "glass", "_door", "_trapdoor",
                "chest", "barrel", "crafting_table", "furnace", "torch", "lantern", "_bed", "wool", "carpet", "campfire", "ladder", "concrete",
                "bookshelf", "_pressure_plate", "_button", "lever", "rail", "smooth_", "polished_", "chiseled_", "cut_", "_sign", "hay_block",
                "dirt_path", "glazed_terracotta", "bell", "anvil", "composter", "lectern", "loom", "smoker", "stonecutter", "scaffolding"))
            if (id.contains(part)) return true;
        return false;
    }

    private Survey surveyCell(CodaLivingWorld world, Settlement c, CellPos cell) throws Exception {
        int centreY = c.campfire.y();
        Map<String, Integer> surface = new HashMap<>(), treeLogs = new HashMap<>();
        if (square(c, cell)) return new Survey(cell, Role.SQUARE, "the campfire's chunk is the town square", 0, 0, centreY, 0, treeLogs, surface);
        int x0 = cell.minX(), z0 = cell.minZ();
        Column[][] cols = new Column[CELL + 2 * MARGIN][CELL + 2 * MARGIN];
        int hint = centreY;
        for (int dx = 0; dx < cols.length; dx++)
            for (int dz = 0; dz < cols.length; dz++) {
                var col = column(world, x0 - MARGIN + dx, z0 - MARGIN + dz, centreY, hint);
                if (!col.known()) return unknown(cell, centreY, "part of it is in an unloaded area");
                cols[dx][dz] = col;
                hint = Math.max(centreY - SEARCH, col.y());
            }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, water = 0, lava = 0, loose = 0, hollow = 0, farmland = 0, made = 0, trees = 0;
        long sum = 0;
        for (int dx = MARGIN; dx < MARGIN + CELL; dx++)
            for (int dz = MARGIN; dz < MARGIN + CELL; dz++) {
                var col = cols[dx][dz];
                int x = x0 - MARGIN + dx, z = z0 - MARGIN + dz;
                min = Math.min(min, col.y());
                max = Math.max(max, col.y());
                sum += col.y();
                surface.merge(col.top(), 1, Integer::sum);
                if (col.water()) water++;
                if (col.lava()) lava++;
                if (FrontierJobs.FALLING.contains(col.top())) loose++;
                if (col.top().equals("minecraft:farmland")) farmland++;
                if (made(col.top())) made++;
                // A hollow (cave, ravine roof) within a few blocks under the ground.
                for (int d = 1; d <= HOLLOW_DEPTH && !col.water() && !col.lava(); d++) {
                    var below = world.block(new CodaBlockPos(x, col.y() - d, z));
                    if (below.isPresent() && (below.get().air() || below.get().fluid())) { hollow++; break; }
                }
                // Anything built above the ground, and tree trunks standing on it.
                boolean naturalTrunk = false;
                for (int y = col.y() + 1; y <= col.y() + 6; y++) {
                    var above = world.block(new CodaBlockPos(x, y, z));
                    if (above.isEmpty()) break;
                    var block = above.get();
                    String id = block.id();
                    if (made(id) || (id.endsWith("_leaves") && "true".equals(block.properties().get("persistent")))) { made++; continue; }
                    if (!FrontierJobs.isLog(id)) continue;
                    var at = new CodaBlockPos(x, y, z);
                    if (y == col.y() + 1 && FrontierJobs.naturalCanopy(world, at, FrontierJobs.trunk(world, at))) {
                        naturalTrunk = true;
                        trees++;
                        treeLogs.merge(id, 1, Integer::sum);
                    } else if (!naturalTrunk && !(y == col.y() + 1 && !"y".equals(block.properties().get("axis")))) {
                        made++; // a log that is not part of a natural tree (lying logs on the ground are fallen trees)
                    }
                }
            }
        int level = (int) Math.round(sum / (double) (CELL * CELL));
        // Cliffs: the ground just outside the cell falling well below its lowest point.
        int lowestAround = Integer.MAX_VALUE;
        boolean lavaAround = false;
        for (int dx = 0; dx < cols.length; dx++)
            for (int dz = 0; dz < cols.length; dz++) {
                boolean inside = dx >= MARGIN && dx < MARGIN + CELL && dz >= MARGIN && dz < MARGIN + CELL;
                if (inside) continue;
                lowestAround = Math.min(lowestAround, cols[dx][dz].y());
                lavaAround |= cols[dx][dz].lava();
            }
        String cellName = "this cell";
        if (made > 0) return new Survey(cell, Role.YOURS, "there are built blocks here (" + made + "), so settlers leave it alone", min, max, level, trees, treeLogs, surface);
        if (lava > 0 || lavaAround) return new Survey(cell, Role.HAZARD, "lava in or next to " + cellName, min, max, level, trees, treeLogs, surface);
        if (water > 0) return new Survey(cell, Role.WATER, "water covers " + water + " of its 64 blocks", min, max, level, trees, treeLogs, surface);
        if (min - lowestAround > MAX_DROP)
            return new Survey(cell, Role.HAZARD, "a drop of " + (min - lowestAround) + " blocks right next to it (cliff or ravine)", min, max, level, trees, treeLogs, surface);
        if (hollow > 0) return new Survey(cell, Role.HAZARD, "hollow ground: a cave or gap within " + HOLLOW_DEPTH + " blocks under " + hollow + " spots", min, max, level, trees, treeLogs, surface);
        if (loose > 0) return new Survey(cell, Role.HAZARD, "loose ground (sand or gravel) under " + loose + " spots", min, max, level, trees, treeLogs, surface);
        if (farmland >= 4) return new Survey(cell, Role.FARM, "farmland is already here", min, max, level, trees, treeLogs, surface);
        if (trees >= FOREST_TREES) return new Survey(cell, Role.FOREST, trees + " natural trees, kept for the lumberjacks", min, max, level, trees, treeLogs, surface);
        if (max - min > MAX_FLAT) return new Survey(cell, Role.STEEP, "the ground rises " + (max - min) + " blocks across it (" + MAX_FLAT + " at most for a building)", min, max, level, trees, treeLogs, surface);
        return new Survey(cell, Role.OPEN, "flat, safe and empty", min, max, level, trees, treeLogs, surface);
    }

    private static Survey unknown(CellPos cell, int y, String why) {
        return new Survey(cell, Role.UNKNOWN, why, 0, 0, y, 0, Map.of(), Map.of());
    }

    // ---- Materials --------------------------------------------------------------------------------

    Palette palette() {
        Map<String, Integer> logs = new HashMap<>(), surface = new HashMap<>();
        for (var s : surveys.values()) {
            s.treeLogs().forEach((k, v) -> logs.merge(k, v, Integer::sum));
            s.surface().forEach((k, v) -> surface.merge(k, v, Integer::sum));
        }
        return Palette.choose(logs, surface);
    }

    // ---- Roads and plots --------------------------------------------------------------------------

    /** Can a road run through this cell? */
    private static boolean roadable(Survey s) {
        return s != null && switch (s.role()) {
            case SQUARE, OPEN, FOREST -> true;
            case STEEP -> s.maxY() - s.minY() <= MAX_ROAD_RISE;
            default -> false;
        };
    }

    private static int cost(Survey s) {
        return switch (s.role()) {
            case FOREST -> 4;
            case STEEP -> 2;
            default -> 1;
        };
    }

    /**
     * The next building plot: the open cell with the lowest score (road length, unevenness),
     * and the road from the square to it. Null when there is none yet.
     */
    Plot nextPlot(Settlement c) {
        Territory t = c.territory();
        Map<CellPos, Integer> dist = new HashMap<>();
        Map<CellPos, CellPos> from = new HashMap<>();
        PriorityQueue<CellPos> queue = new PriorityQueue<>(Comparator.comparingInt(dist::get));
        for (var s : surveys.values())
            if (s.role() == Role.SQUARE) { dist.put(s.cell(), 0); queue.add(s.cell()); }
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            var at = queue.poll();
            var here = surveys.get(at);
            for (int[] d : dirs) {
                var next = at.step(d[0], d[1]);
                var s = surveys.get(next);
                if (!roadable(s) || !t.contains(new CodaBlockPos(next.minX(), 0, next.minZ()))) continue;
                if (here.role() != Role.SQUARE && Math.abs(s.level() - here.level()) > MAX_ROAD_RISE) continue;
                int nd = dist.get(at) + cost(s);
                if (nd < dist.getOrDefault(next, Integer.MAX_VALUE)) {
                    dist.put(next, nd);
                    from.put(next, at);
                    queue.remove(next);
                    queue.add(next);
                }
            }
        }
        Plot best = null;
        int bestScore = Integer.MAX_VALUE;
        for (var s : surveys.values()) {
            if (s.role() != Role.OPEN) continue;
            for (int[] d : dirs) {
                var neighbour = s.cell().step(d[0], d[1]);
                var ns = surveys.get(neighbour);
                if (!dist.containsKey(neighbour) || ns == null) continue;
                if (ns.role() != Role.SQUARE && Math.abs(ns.level() - s.level()) > MAX_ROAD_RISE) continue;
                List<CellPos> road = new ArrayList<>();
                for (var at = neighbour; surveys.get(at).role() != Role.SQUARE; at = from.get(at)) road.addFirst(at);
                if (road.contains(s.cell())) continue;
                int score = dist.get(neighbour) * 10 + (s.maxY() - s.minY()) * 3 + Math.abs(s.level() - c.campfire.y());
                if (score < bestScore || (score == bestScore && compare(s.cell(), best.cell()) < 0)) {
                    bestScore = score;
                    String facing = d[1] < 0 ? "north" : d[0] > 0 ? "east" : d[1] > 0 ? "south" : "west";
                    best = new Plot(s.cell(), road, facing, s.level());
                }
            }
        }
        return best;
    }

    private static int compare(CellPos a, CellPos b) {
        return a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x());
    }

    long surveyed(Settlement c) {
        return cells(c.territory()).stream().filter(cell -> surveys.containsKey(cell) && surveys.get(cell).role() != Role.UNKNOWN).count();
    }

    // ---- Showing the plan in chat ------------------------------------------------------------------

    static final String GLYPH = "■";

    /** The grid as rows of coloured squares, north at the top. */
    List<String> map(Settlement c, Plot plot, CellPos player) {
        var all = cells(c.territory());
        int minX = all.stream().mapToInt(CellPos::x).min().orElse(0), maxX = all.stream().mapToInt(CellPos::x).max().orElse(0);
        int minZ = all.stream().mapToInt(CellPos::z).min().orElse(0), maxZ = all.stream().mapToInt(CellPos::z).max().orElse(0);
        Set<CellPos> road = plot == null ? Set.of() : new HashSet<>(plot.road());
        List<String> rows = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = minX; x <= maxX; x++) {
                var cell = new CellPos(x, z);
                String colour;
                if (cell.equals(player)) colour = Chat.S + "b";
                else if (plot != null && cell.equals(plot.cell())) colour = Chat.S + "d";
                else if (road.contains(cell)) colour = Chat.YELLOW;
                else colour = survey(cell).map(s -> s.role().colour).orElse(Role.UNKNOWN.colour);
                row.append(colour).append(GLYPH);
            }
            rows.add(row.append(Chat.RESET).toString());
        }
        return rows;
    }
}
