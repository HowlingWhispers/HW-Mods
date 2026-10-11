package dev.howlingwhispers.waypoints;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;

/** Plain Java fixtures. Live Snapshot 3 command/teleport behavior remains a separate test. */
public final class WaypointsTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    @FunctionalInterface private interface Trial { void run() throws Exception; }
    private static void refuses(Trial trial) throws Exception {
        try { trial.run(); throw new AssertionError("Expected a refusal"); }
        catch (IllegalArgumentException | java.io.IOException expected) { checks++; }
    }
    private static final class Player implements CodaCommandContext {
        final Path world; final UUID player; CodaPosition position; int teleports;
        boolean refuse; final List<String> replies = new ArrayList<>();
        Player(Path world, UUID player, CodaPosition at) { this.world = world; this.player = player; position = at; }
        public Path worldDirectory() { return world; }
        public UUID playerId() { return player; }
        public CodaPosition position() { return position; }
        public void reply(String message) { replies.add(message); }
        public void teleport(CodaPosition to) {
            if (refuse) throw new IllegalArgumentException("Unsafe landing");
            position = to; teleports++;
        }
        String last() { return replies.getLast(); }
    }
    private static CodaCommand wp() {
        return CodaCommands.registrations().stream().filter(e -> e.name().equals("wp"))
                .findFirst().orElseThrow().command();
    }
    private static void command(Player p, String... args) throws Exception {
        wp().execute(p, List.of(args));
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hw-waypoints-");
        Path world = root.resolve("first"), other = root.resolve("second");
        CodaPosition origin = new CodaPosition("minecraft:overworld", 0, 64, 0, 45, -10);
        CodaPosition farm = new CodaPosition("minecraft:overworld", 30, 69, -40, 60, 25);
        CodaPosition nether = new CodaPosition("minecraft:the_nether", 5, 70, 5, 0, 0);
        WaypointStore store = new WaypointStore();
        check(WaypointStore.name("FaRM").equals("farm"), "lowercase keys");
        refuses(() -> WaypointStore.name("../escape"));
        refuses(() -> WaypointStore.name("a".repeat(25)));
        refuses(() -> WaypointStore.note("a".repeat(81)));
        store.set(world, "Farm", farm, "wheat + carrots", false);
        check(store.all(world).get("farm").position().equals(farm), "position and facing persist");
        check(new WaypointStore().all(world).get("farm").note().equals("wheat + carrots"), "notes survive restart");
        check(store.all(other).isEmpty(), "worlds isolated");
        refuses(() -> store.set(world, "FARM", origin, "", false));
        store.set(world, "Farm", origin, "replaced", true);
        check(store.all(world).get("farm").position().equals(origin), "explicit replace");
        Path data = world.resolve("hw/hw_waypoints/data.json");
        Path backup = world.resolve("hw/hw_waypoints/data.json.bak");
        check(Files.exists(data) && Files.exists(backup), "atomic save and backup files");
        check(Files.readString(backup).contains("wheat + carrots"), "backup retains older valid snapshot");
        Files.writeString(data, "{ broken");
        check(store.all(world).get("farm").position().equals(farm), "corrupt primary falls back to backup");
        try (var entries = Files.list(data.getParent())) {
            check(entries.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")), "corrupt file preserved");
        }
        store.set(world, "farm", farm, "restored", true);
        check(Files.exists(data), "data recovered after corruption");
        store.set(world, "nether", nether, "other dimension", false);
        check(store.delete(world, "NETHER"), "case-insensitive deletion");
        check(!store.delete(world, "not_here"), "missing delete doesn't touch storage");
        String[] dir = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[][] coords = {{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};
        for (int i = 0; i < 8; i++) {
            CodaPosition dest = new CodaPosition(origin.dimension(), coords[i][0], 64, coords[i][1], 0, 0);
            check(HwWaypointsMod.heading(origin, dest).equals(dir[i]), "heading " + dir[i]);
        }
        check(HwWaypointsMod.heading(origin, origin).equals("HERE"), "zero distance heading");
        check(HwWaypointsMod.horizontal(origin, farm) == 50.0, "horizontal distance");

        new HwWaypointsMod().onInitialize(new CodaContext("0.0.27", "26.4-snapshot-3", root,
                root, "hw_waypoints", List.of("hw_waypoints")));
        check(CodaCommands.registrations().stream().anyMatch(r -> r.name().equals("wp")), "command registered");
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        Player p = new Player(other, alice, origin);
        command(p, "set", "Farm", "crops", "and", "seeds");
        check(p.last().contains("farm"), "set reply");
        refuses(() -> command(p, "set", "fArM"));
        command(p, "set", "Farm", "--replace", "updated");
        check(new WaypointStore().all(other).get("farm").note().equals("updated"), "replace note");
        command(p, "where", "farm");
        check(p.last().contains("0 blocks HERE"), "where contains direction and distance");
        refuses(() -> command(p, "back"));
        p.position = farm;
        command(p, "go", "farm");
        check(p.teleports == 1 && p.position.equals(origin), "go sends saved destination");
        command(p, "back");
        check(p.position.equals(farm), "back remembers departure");
        command(p, "back");
        check(p.position.equals(origin), "back swaps on repeat");
        p.refuse = true;
        refuses(() -> command(p, "go", "farm"));
        refuses(() -> command(p, "back"));
        check(p.teleports == 3, "rejected teleports don't move");
        p.refuse = false;
        command(p, "back");
        check(p.position.equals(farm), "rejected back doesn't change stored point");
        refuses(() -> command(new Player(other, bob, origin), "back"));
        refuses(() -> command(new Player(world, alice, origin), "back"));
        p.position = nether;
        refuses(() -> command(p, "go", "farm"));
        refuses(() -> command(p, "back"));
        check(p.teleports == 4, "other dimensions refused");
        p.position = origin;
        command(p, "set", "near", "--replace");
        command(p, "set", "far");
        command(p, "list");
        check(p.last().indexOf("farm [") < p.last().indexOf("far [") || p.last().contains("farm ["), "list contains distances");
        command(p, "del", "farm");
        refuses(() -> command(p, "where", "farm"));
        refuses(() -> command(p, "list", "0"));
        refuses(() -> command(p, "set"));
        refuses(() -> command(p, "go"));
        System.out.println("HW Waypoints tests passed: " + checks + " checks.");
    }
}
