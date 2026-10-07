package dev.howlingwhispers.essentials;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;

public final class EssentialsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void fails(RunnableWithException task) throws Exception {
        try { task.run(); throw new AssertionError("Expected refusal"); }
        catch (IllegalArgumentException | IllegalStateException | java.io.IOException expected) { checks++; }
    }
    @FunctionalInterface interface RunnableWithException { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hw-essentials-tests-");
        Path worldA = root.resolve("world-a"), worldB = root.resolve("world-b");
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        CodaPosition home = new CodaPosition("overworld", 10.5, 70, -42.25, 90, 15);
        CodaPosition moved = new CodaPosition("overworld", 20, 64, 40, 0, 0);
        HomeStore store = new HomeStore(2);
        store.set(worldA, alice, "Cabin", home);
        check(store.homes(worldA, alice).get("cabin").equals(home), "coordinates and facing persist");
        check(new HomeStore(2).homes(worldA, alice).equals(store.homes(worldA, alice)), "persist across restart");
        check(store.homes(worldB, alice).isEmpty(), "world isolation");
        check(store.homes(worldA, bob).isEmpty(), "player isolation");
        store.set(worldA, alice, "mine", moved);
        fails(() -> store.set(worldA, alice, "third", home));
        store.set(worldA, alice, "cabin", moved);
        check(store.homes(worldA, alice).get("cabin").equals(moved), "overwrite allowed at limit");
        fails(() -> store.set(worldA, alice, "../../escape", home));
        fails(() -> new CodaPosition("overworld", Double.NaN, 0, 0, 0, 0));
        check(store.delete(worldA, alice, "MINE"), "delete is case insensitive");
        check(!store.delete(worldA, alice, "missing"), "missing delete is harmless");
        Path saved = worldA.resolve("cml/hw-essentials/homes/" + alice + ".properties");
        byte[] original = Files.readAllBytes(saved);
        Files.writeString(saved, "schema=1\nhome.broken.x=12\n");
        byte[] damaged = Files.readAllBytes(saved);
        fails(() -> store.set(worldA, alice, "new", home));
        check(Arrays.equals(damaged, Files.readAllBytes(saved)), "corrupt file preserved");
        Files.write(saved, original);

        Path config = root.resolve("config"); Files.createDirectories(config);
        new HwEssentialsMod().onInitialize(new CodaContext("0.0.18-essentials", "26.4-snapshot-3", root, config, "hw_essentials", List.of("hw_essentials")));
        check(CodaCommands.registrations().size() == 5, "command registration count");
        FakeContext context = new FakeContext(worldB, bob, home);
        command("sethome").execute(context, List.of());
        check(context.replies.getLast().contains("filed"), "sethome confirms save");
        context.position = moved;
        command("home").execute(context, List.of());
        check(context.position.equals(home), "home sends saved destination");
        check(context.teleports == 1, "single teleport");
        context.position = new CodaPosition("nether", 0, 64, 0, 0, 0);
        fails(() -> command("home").execute(context, List.of()));
        check(context.teleports == 1, "cross dimension refuses movement");
        fails(() -> command("sethome").execute(context, List.of("a", "b")));
        command("homes").execute(context, List.of());
        check(context.replies.getLast().contains("home"), "homes lists current player");
        command("delhome").execute(context, List.of("home"));
        fails(() -> command("home").execute(context, List.of()));
        fails(() -> CodaCommands.register("another_mod", "home", "collision", (c, a) -> {}));
        System.out.println("HW Essentials tests passed: " + checks + " checks.");
    }

    private static CodaCommand command(String name) {
        return CodaCommands.registrations().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().command();
    }
    private static final class FakeContext implements CodaCommandContext {
        final Path world; final UUID player; CodaPosition position; int teleports;
        final List<String> replies = new ArrayList<>();
        FakeContext(Path world, UUID player, CodaPosition position) { this.world = world; this.player = player; this.position = position; }
        public Path worldDirectory() { return world; }
        public UUID playerId() { return player; }
        public CodaPosition position() { return position; }
        public void reply(String text) { replies.add(text); }
        public void teleport(CodaPosition position) { this.position = position; teleports++; }
    }
}
