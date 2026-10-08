import dev.howlingwhispers.buildcraft.BuildCraftCmlMod;
import dev.howlingwhispers.buildcraft.BuildCraftTransportRuntime;
import dev.howlingwhispers.buildcraft.PipeNetwork;
import dev.howlingwhispers.buildcraft.PipeNetwork.Pos;
import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaServerTickContext;
import dev.howlingwhispers.codaloader.api.CodaServerTicks;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BuildCraftRuntimeTest {
    private static int tests;
    private static void check(boolean result, String why) {
        tests++;
        if (!result) throw new AssertionError(why);
    }
    private static void rejected(Runnable action, String why) {
        try { action.run(); } catch (IllegalStateException expected) { tests++; return; }
        throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        var mod = new BuildCraftCmlMod();
        mod.onInitialize(new CodaContext("0.0.26", "26.4-snapshot-3",
                Path.of("run"), Path.of("run/config/buildcraft_cml"), "buildcraft_cml",
                List.of("buildcraft_cml")));
        var controller = BuildCraftCmlMod.transportRuntime();
        Pos a = new Pos(0, 65, 0), b = new Pos(1, 65, 0), chest = new Pos(2, 65, 0);
        var north = new PipeNetwork();
        north.addPipe(a); north.addPipe(b); north.addInventory(chest, 128);
        north.insert(a, "minecraft:iron_ingot", 17);
        var south = new PipeNetwork();
        south.addPipe(a); south.addPipe(b); south.addInventory(chest, 128);
        south.insert(a, "minecraft:gold_ingot", 12);
        AtomicBoolean loaded = new AtomicBoolean(false);
        controller.bind("north-world", north, pos -> pos.x() == 0 || loaded.get());
        controller.bind("south-world", south, pos -> true);
        check(controller.activeSessions() == 2, "Two worlds bound separately");
        rejected(() -> controller.bind("north-world", north, pos -> true), "Duplicate world bind accepted");

        CodaServerTicks.dispatch(new CodaServerTickContext("north-world", 1));
        check(north.itemsAt(a) == 17, "Pipe is blocked by unloaded chunk");
        check(south.itemsAt(a) == 12, "Unrelated world not ticked");

        loaded.set(true);
        CodaServerTicks.dispatch(new CodaServerTickContext("north-world", 2));
        check(north.itemsAt(b) == 17, "Loader tick callback routes first hop");
        check(north.itemsAt(chest) == 0, "No double hop in same tick");
        CodaServerTicks.dispatch(new CodaServerTickContext("north-world", 3));
        check(north.itemsAt(chest) == 17, "Loader tick callback routes second hop");
        check(north.totalItems() == 17, "No missing or duplicated cargo");
        CodaServerTicks.dispatch(new CodaServerTickContext("south-world", 1));
        check(south.itemsAt(b) == 12, "Second world routes independently");

        var threadFailure = new AtomicReference<Throwable>();
        Thread offThread = new Thread(() -> {
            try { controller.onServerTick(new CodaServerTickContext("north-world", 4)); }
            catch (Throwable ex) { threadFailure.set(ex); }
        }, "fake-client-render-thread");
        offThread.start(); offThread.join();
        check(threadFailure.get() instanceof IllegalStateException,
                "World network cannot tick on client/render thread");
        check(north.totalItems() == 17, "Off-thread attempt did not mutate inventory");

        rejected(() -> controller.onServerTick(new CodaServerTickContext("north-world", 3)),
                "Replay server tick accepted");
        check(controller.unbind("north-world").stream()
                .mapToInt(n -> n.packets().stream().mapToInt(p -> p.amount()).sum()).sum() == 17,
                "Unbind returns all persisted in-flight and delivered cargo");
        check(controller.activeSessions() == 1, "Unbind clears completed world session");
        check(south.totalItems() == 12, "Other world remains intact after unload");
        check(controller.unbind("south-world").size() == 3, "World snapshot has all nodes");
        check(controller.activeSessions() == 0, "All sessions detached");

        System.out.println("PASS: " + tests + " BuildCraft H.O.W.L. server-tick bridge checks");
    }
}
