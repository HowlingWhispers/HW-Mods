package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** A small mocked world for behaviour tests. Not a Minecraft compatibility claim: CI's native GameTest covers that. */
final class Fixtures {
    static int checks;

    static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    static void finish(String suite) { System.out.println("PASS: " + checks + " " + suite); }

    static final CodaBlockPos CAMP = new CodaBlockPos(0, 64, 0);

    static CodaLivingWorld.Block block(String id, Map<String, String> properties, boolean solid) {
        return new CodaLivingWorld.Block(id, properties, solid, false);
    }

    static final class World implements CodaLivingWorld {
        final Map<CodaBlockPos, Block> blocks = new HashMap<>();
        final Map<UUID, Actor> actors = new HashMap<>();
        final Map<UUID, String> names = new HashMap<>();
        final List<String> announcements = new ArrayList<>();
        int spawned, food = 5, damage;
        boolean loaded = true, spawnAllowed = true, paths = true;
        long clock = 6000;
        UUID broken;
        Predicate<CodaBlockPos> flooded = p -> false;
        /** Terrain: the solid surface block's height per column (default flat at 63). */
        java.util.function.IntBinaryOperator ground = (x, z) -> 63;
        /** Columns with tall grass standing on the surface. */
        java.util.function.BiPredicate<Integer, Integer> grass = (x, z) -> false;
        /** Spots where the real game would refuse a spawn for collision reasons the rule cannot see. */
        Predicate<CodaBlockPos> collides = p -> false;
        final List<CodaBlockPos> refusedSpawns = new ArrayList<>();

        World() { blocks.put(CAMP, Fixtures.block("minecraft:campfire", Map.of("lit", "true"), false)); }

        /** A sheltered bed: head at {@code head}, foot to the west, a plank roof three blocks up. */
        CodaBlockPos bed(CodaBlockPos head) {
            blocks.put(head, Fixtures.block("minecraft:red_bed", Map.of("part", "head", "occupied", "false"), false));
            blocks.put(new CodaBlockPos(head.x() - 1, head.y(), head.z()), Fixtures.block("minecraft:red_bed", Map.of("part", "foot", "occupied", "false"), false));
            blocks.put(new CodaBlockPos(head.x(), head.y() + 3, head.z()), Fixtures.block("minecraft:oak_planks", Map.of(), true));
            return head;
        }

        void put(UUID id, CodaBlockPos at, float health) { actors.put(id, new Actor(id, at.x() + .5, at.y(), at.z() + .5, health, false)); }

        public long clock() { return clock; }
        public Optional<Block> block(CodaBlockPos p) {
            if (!loaded) return Optional.empty();
            if (flooded.test(p)) return Optional.of(new Block("minecraft:water", Map.of(), false, true));
            var placed = blocks.get(p);
            if (placed != null) return Optional.of(placed);
            int surface = ground.applyAsInt(p.x(), p.z());
            if (p.y() <= surface) return Optional.of(Fixtures.block("minecraft:grass_block", Map.of(), true));
            if (p.y() == surface + 1 && grass.test(p.x(), p.z())) return Optional.of(Fixtures.block("minecraft:short_grass", Map.of(), false));
            return Optional.of(Fixtures.block("minecraft:air", Map.of(), false));
        }
        public Optional<Actor> actor(UUID id) {
            if (id.equals(broken)) throw new IllegalStateException("simulated broken entity");
            return loaded ? Optional.ofNullable(actors.get(id)) : Optional.empty();
        }
        /** Mirrors CodaLoader 0.0.45 MinecraftLivingWorld.standable(): the real game refuses anything else. */
        boolean loaderStandable(CodaBlockPos p) {
            var feet = block(p);
            var head = block(new CodaBlockPos(p.x(), p.y() + 1, p.z()));
            var floor = block(new CodaBlockPos(p.x(), p.y() - 1, p.z()));
            return feet.isPresent() && head.isPresent() && floor.isPresent() && feet.get().air() && head.get().air()
                    && floor.get().solid() && !floor.get().fluid()
                    && !Set.of("minecraft:magma_block", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:cactus").contains(floor.get().id());
        }
        public boolean spawn(UUID id, String name, CodaBlockPos p) {
            if (!spawnAllowed || actors.containsKey(id)) return false;
            if (!loaderStandable(p) || collides.test(p)) {
                refusedSpawns.add(p);
                return false;
            }
            spawned++;
            put(id, p, 20);
            names.put(id, name);
            return true;
        }
        public boolean walk(UUID id, CodaBlockPos p, double speed) {
            var a = actors.get(id);
            // Loader: the entity must exist, the target be within 32 blocks and loaded, and a path must reach it.
            if (!paths || a == null || a.distanceSquared(p) > 32 * 32 || block(p).isEmpty()) return false;
            actors.put(id, new Actor(id, p.x() + .5, p.y(), p.z() + .5, a.health(), false));
            return true;
        }
        public boolean sleep(UUID id, CodaBlockPos p) { return true; }
        public void wake(UUID id) {}
        public void damage(UUID id, float amount) {
            damage++;
            var a = actors.get(id);
            actors.put(id, new Actor(id, a.x(), a.y(), a.z(), Math.max(0, a.health() - amount), false));
        }
        public void discard(UUID id) { actors.remove(id); }
        // ---- Items, mirroring CodaLoader 0.0.45 MinecraftLivingWorld ---------------------------------
        /** Each settler's 8-slot bag (item -> count), and what each holds in the main hand. */
        final Map<UUID, LinkedHashMap<String, Integer>> bags = new HashMap<>();
        final Map<UUID, String> hands = new HashMap<>();
        /** Chest and barrel contents by position. Bread is counted by {@link #food} so older tests keep working. */
        final Map<CodaBlockPos, LinkedHashMap<String, Integer>> chests = new HashMap<>();
        boolean chestFull;
        final List<String> harvested = new ArrayList<>();

        LinkedHashMap<String, Integer> bag(UUID id) { return bags.computeIfAbsent(id, k -> new LinkedHashMap<>()); }
        LinkedHashMap<String, Integer> chest(CodaBlockPos p) {
            var b = blocks.get(p);
            if (!loaded || b == null || !FrontierSimulation.CONTAINERS.contains(b.id())) return null;
            return chests.computeIfAbsent(p, k -> new LinkedHashMap<>());
        }
        void stock(CodaBlockPos chest, String item, int count) { chest(chest).merge(item, count, Integer::sum); }
        int stocked(CodaBlockPos chest, String item) { var c = chest(chest); return c == null ? 0 : c.getOrDefault(item, 0); }
        private boolean near(UUID id, CodaBlockPos p, double d) { var a = actors.get(id); return a != null && a.distanceSquared(p) <= d * d; }
        private static int slots(Map<String, Integer> items) { int n = 0; for (int c : items.values()) n += (c + 63) / 64; return n; }
        private static void take(Map<String, Integer> items, String id, int n) { int left = items.get(id) - n; if (left <= 0) items.remove(id); else items.put(id, left); }

        public boolean eat(UUID id, CodaBlockPos p, Set<String> allowed) {
            if (!near(id, p, 3) || chest(p) == null || food == 0) return false;
            food--;
            return true;
        }
        public int carried(UUID id) { int n = 0; for (int c : bag(id).values()) n += c; return n; }
        public int deliver(UUID id, CodaBlockPos p) {
            var chest = chest(p);
            if (!near(id, p, 3) || chest == null || chestFull) return 0;
            int moved = carried(id);
            bag(id).forEach((item, n) -> chest.merge(item, n, Integer::sum));
            bag(id).clear();
            return moved;
        }
        public int collect(UUID id, CodaBlockPos p, CodaItemFilter f, int maximum) {
            var chest = chest(p);
            if (!near(id, p, 3) || chest == null) return 0;
            int moved = 0;
            for (var item : new ArrayList<>(chest.keySet())) {
                if (!f.allows(item) || moved >= maximum) continue;
                int n = Math.min(maximum - moved, chest.get(item));
                take(chest, item, n);
                bag(id).merge(item, n, Integer::sum);
                moved += n;
            }
            return moved;
        }
        public boolean equip(UUID id, CodaBlockPos supplies, Set<String> tools) {
            if (!actors.containsKey(id)) return false;
            String held = hands.get(id);
            if (held != null && !tools.contains(held)) {
                var chest = chest(supplies);
                if (!near(id, supplies, 3) || chest == null) return false; // a wrong tool goes back first
                chest.merge(held, 1, Integer::sum);
                hands.remove(id);
                held = null;
            }
            if (tools.isEmpty() || held != null) return true;
            for (var item : new ArrayList<>(bag(id).keySet())) if (tools.contains(item)) { take(bag(id), item, 1); hands.put(id, item); return true; }
            var chest = near(id, supplies, 3) ? chest(supplies) : null;
            if (chest == null) return false;
            for (var item : new ArrayList<>(chest.keySet())) if (tools.contains(item)) { take(chest, item, 1); hands.put(id, item); return true; }
            return false;
        }
        /** What breaking a block drops, simplified from vanilla loot. */
        static Map<String, Integer> drops(Block b) {
            String id = b.id();
            if (id.endsWith("_log")) return Map.of(id, 1);
            boolean ripe = FrontierJobs.CROPS.containsKey(id) && FrontierJobs.CROPS.get(id).ripeAge().equals(b.properties().get("age"));
            return switch (id) {
                case "minecraft:wheat" -> ripe ? Map.of("minecraft:wheat", 1, "minecraft:wheat_seeds", 2) : Map.of("minecraft:wheat_seeds", 1);
                case "minecraft:carrots" -> Map.of("minecraft:carrot", ripe ? 3 : 1);
                case "minecraft:potatoes" -> Map.of("minecraft:potato", ripe ? 3 : 1);
                case "minecraft:beetroots" -> ripe ? Map.of("minecraft:beetroot", 1, "minecraft:beetroot_seeds", 2) : Map.of("minecraft:beetroot_seeds", 1);
                case "minecraft:stone" -> Map.of("minecraft:cobblestone", 1);
                case "minecraft:coal_ore" -> Map.of("minecraft:coal", 1);
                default -> Map.of(id, 1);
            };
        }
        public boolean harvest(UUID id, CodaBlockPos p, String expected, CodaBlockPos supplies, Set<String> tools) {
            String held = hands.get(id);
            if ((!tools.isEmpty() && (held == null || !tools.contains(held))) || !near(id, p, 6)) return false;
            var b = block(p);
            if (b.isEmpty() || !b.get().id().equals(expected) || b.get().fluid() || FrontierSimulation.CONTAINERS.contains(expected)) return false;
            var after = new LinkedHashMap<>(bag(id));
            drops(b.get()).forEach((item, n) -> after.merge(item, n, Integer::sum));
            if (slots(after) > 8) return false; // the 8-slot bag is full
            blocks.put(p, Fixtures.block("minecraft:air", Map.of(), false));
            bags.put(id, after);
            harvested.add(expected);
            return true;
        }
        public boolean plant(UUID id, CodaBlockPos p, String item, CodaBlockPos supplies) {
            if (!near(id, p, 3)) return false;
            var here = block(p);
            var floor = block(new CodaBlockPos(p.x(), p.y() - 1, p.z()));
            if (here.isEmpty() || !here.get().air() || floor.isEmpty()) return false;
            String crop = switch (item) {
                case "minecraft:wheat_seeds" -> "minecraft:wheat";
                case "minecraft:carrot" -> "minecraft:carrots";
                case "minecraft:potato" -> "minecraft:potatoes";
                case "minecraft:beetroot_seeds" -> "minecraft:beetroots";
                default -> null;
            };
            boolean sapling = item.endsWith("_sapling") || item.endsWith("_propagule");
            if (crop == null && !sapling) return false;
            if (crop != null && !floor.get().id().equals("minecraft:farmland")) return false;
            if (sapling && !FrontierJobs.GROUND_FOR_TREES.contains(floor.get().id())) return false;
            Map<String, Integer> from = bag(id).containsKey(item) ? bag(id) : near(id, supplies, 3) ? chest(supplies) : null;
            if (from == null || !from.containsKey(item)) return false;
            take(from, item, 1);
            blocks.put(p, crop != null ? Fixtures.block(crop, Map.of("age", "0"), false) : Fixtures.block(item, Map.of(), false));
            return true;
        }

        // ---- Building helpers for tests -------------------------------------------------------------
        /** A tree: logs from {@code base} upward, a 5x5 canopy around the top two logs and one above. */
        void tree(CodaBlockPos base, int height, String wood, boolean naturalLeaves) {
            for (int y = 0; y < height; y++) blocks.put(new CodaBlockPos(base.x(), base.y() + y, base.z()), Fixtures.block(wood + "_log", Map.of("axis", "y"), true));
            for (int y = height - 2; y <= height; y++)
                for (int x = -2; x <= 2; x++)
                    for (int z = -2; z <= 2; z++) {
                        var p = new CodaBlockPos(base.x() + x, base.y() + y, base.z() + z);
                        if (!blocks.containsKey(p)) blocks.put(p, Fixtures.block(wood + "_leaves", Map.of("persistent", naturalLeaves ? "false" : "true"), true));
                    }
        }
        void crop(CodaBlockPos p, String crop, String age) {
            blocks.put(new CodaBlockPos(p.x(), p.y() - 1, p.z()), Fixtures.block("minecraft:farmland", Map.of(), true));
            blocks.put(p, Fixtures.block(crop, Map.of("age", age), false));
        }

        final List<String> rawAnnouncements = new ArrayList<>();
        public void announce(String text) {
            check(Chat.wellFormed(text), "Announcement has broken formatting: " + text);
            rawAnnouncements.add(text);
            announcements.add(Chat.plain(text));
        }
        @Override public boolean rename(UUID id, String name) {
            if (!actors.containsKey(id)) return false;
            names.put(id, name);
            return true;
        }
    }

    static final class View implements CodaWorldView {
        final Path root;
        final World world;
        View(Path root, World world) { this.root = root; this.world = world; }
        @Override public Optional<Path> worldDirectory() { return Optional.of(root); }
        public List<String> dimensions() { return List.of("minecraft:overworld"); }
        public boolean isChunkLoaded(String d, CodaBlockPos p) { return world.loaded; }
        public Optional<CodaInventoryView> inventory(String d, CodaBlockPos p) { return Optional.empty(); }
        @Override public Optional<CodaItemInventoryView> itemInventory(String d, CodaBlockPos p) {
            var b = world.blocks.get(p);
            if (!world.loaded || b == null || !FrontierSimulation.CONTAINERS.contains(b.id())) return Optional.empty();
            // Like the loader: one entry per real item in the chest, plus the bread counted by world.food.
            List<CodaItemInventoryView.Slot> slots = new ArrayList<>();
            slots.add(new CodaItemInventoryView.Slot(0, "minecraft:bread", world.food, 64));
            var chest = world.chest(p);
            if (chest != null) for (var e : chest.entrySet()) slots.add(new CodaItemInventoryView.Slot(slots.size(), e.getKey(), e.getValue(), 64));
            return Optional.of(new CodaItemInventoryView(p, slots));
        }
        @Override public Optional<CodaLivingWorld> living(String d) { return Optional.of(world); }
    }

    static final class Player implements CodaCommandContext {
        final View view;
        /** Replies as the player reads them (formatting removed); {@link #raw} keeps the codes. */
        final List<String> replies = new ArrayList<>();
        final List<String> raw = new ArrayList<>();
        UUID id = new UUID(0, 1);
        CodaPosition position = new CodaPosition("minecraft:overworld", 2.5, 64, 2.5, 0, 0);
        Optional<CodaBlockTarget> target = Optional.empty();
        Player(View view) { this.view = view; }
        @Override public void withWorld(WorldAction action) throws Exception { action.run(view); }
        public UUID playerId() { return id; }
        public Path worldDirectory() { return view.root; }
        public CodaPosition position() { return position; }
        @Override public Optional<CodaBlockTarget> targetBlock() { return target; }
        public void reply(String s) {
            check(Chat.wellFormed(s), "Reply has broken formatting: " + s);
            raw.add(s);
            replies.add(Chat.plain(s));
        }
        public void teleport(CodaPosition p) { throw new AssertionError("Frontier never teleports players"); }
        void look(CodaBlockPos p) { target = Optional.of(new CodaBlockTarget("minecraft:overworld", p)); }
        String last() { return replies.isEmpty() ? "" : replies.getLast(); }
        boolean said(String text) { return replies.stream().anyMatch(r -> r.contains(text)); }
    }

    /** A world directory, mocked world and simulation with its own monotonic tick clock. */
    static final class Camp {
        final Path root;
        final World world;
        final View view;
        FrontierSimulation sim = new FrontierSimulation();
        long tick;
        String session = "session-1";

        Camp() throws Exception { this(Files.createTempDirectory("frontier-test-"), new World()); }
        Camp(Path root, World world) { this.root = root; this.world = world; this.view = new View(root, world); }

        void seconds(int n) {
            for (int i = 0; i < n; i++) {
                tick += 20;
                sim.tick(new CodaServerTickContext(session, tick, Optional.of(view)));
            }
        }

        /** As if the world were closed and opened again: a new simulation and session read the saved file. */
        void reopen() {
            sim = new FrontierSimulation();
            session = session + "-reopened";
            seconds(1);
        }

        Settlement found() throws Exception {
            sim.open(view, session);
            return sim.establish(world, CAMP, null);
        }

        Settlement c() { return sim.camp(); }

        Settler add(String name, Settler.State state, CodaBlockPos bed, boolean withActor) {
            var s = new Settler(UUID.randomUUID(), name);
            s.state = state;
            s.bed = bed;
            s.arrivalPoint = new CodaBlockPos(CAMP.x() + 20, 64, CAMP.z());
            c().settlers.put(s.id, s);
            if (withActor) world.put(s.id, bed != null ? bed : new CodaBlockPos(2, 64, 2), 20);
            return s;
        }
    }
}
