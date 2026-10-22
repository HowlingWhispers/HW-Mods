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
            return Optional.of(blocks.getOrDefault(p, p.y() == 63 ? Fixtures.block("minecraft:grass_block", Map.of(), true) : Fixtures.block("minecraft:air", Map.of(), false)));
        }
        public Optional<Actor> actor(UUID id) {
            if (id.equals(broken)) throw new IllegalStateException("simulated broken entity");
            return loaded ? Optional.ofNullable(actors.get(id)) : Optional.empty();
        }
        public boolean spawn(UUID id, String name, CodaBlockPos p) {
            if (!spawnAllowed || actors.containsKey(id)) return false;
            spawned++;
            put(id, p, 20);
            names.put(id, name);
            return true;
        }
        public boolean walk(UUID id, CodaBlockPos p, double speed) {
            if (!paths) return false;
            var a = actors.get(id);
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
        public boolean eat(UUID id, CodaBlockPos p, Set<String> allowed) {
            if (food == 0) return false;
            food--;
            return true;
        }
        public int deliver(UUID id, CodaBlockPos p) { return 0; }
        public int carried(UUID id) { return 0; }
        public int collect(UUID id, CodaBlockPos p, CodaItemFilter f, int maximum) { return 0; }
        public boolean equip(UUID id, CodaBlockPos p, Set<String> tools) { return true; }
        public boolean harvest(UUID id, CodaBlockPos p, String expected, CodaBlockPos supplies, Set<String> tools) { return false; }
        public boolean plant(UUID id, CodaBlockPos p, String item, CodaBlockPos supplies) { return false; }
        public void announce(String text) { announcements.add(text); }
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
            return Optional.of(new CodaItemInventoryView(p, List.of(new CodaItemInventoryView.Slot(0, "minecraft:bread", world.food, 64))));
        }
        @Override public Optional<CodaLivingWorld> living(String d) { return Optional.of(world); }
    }

    static final class Player implements CodaCommandContext {
        final View view;
        final List<String> replies = new ArrayList<>();
        UUID id = new UUID(0, 1);
        CodaPosition position = new CodaPosition("minecraft:overworld", 2.5, 64, 2.5, 0, 0);
        Optional<CodaBlockTarget> target = Optional.empty();
        Player(View view) { this.view = view; }
        @Override public void withWorld(WorldAction action) throws Exception { action.run(view); }
        public UUID playerId() { return id; }
        public Path worldDirectory() { return view.root; }
        public CodaPosition position() { return position; }
        @Override public Optional<CodaBlockTarget> targetBlock() { return target; }
        public void reply(String s) { replies.add(s); }
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
