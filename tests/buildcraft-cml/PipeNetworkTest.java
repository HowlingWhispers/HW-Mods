import dev.howlingwhispers.buildcraft.PipeNetwork;
import dev.howlingwhispers.buildcraft.PipeNetwork.Pos;

public final class PipeNetworkTest {
    private static void equal(int actual, int expected, String message) {
        if (actual != expected) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
    private static void fails(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) { return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int tests = 0;
        PipeNetwork p = new PipeNetwork();
        Pos a = new Pos(0, 60, 0), b = new Pos(1, 60, 0), chest = new Pos(2, 60, 0);
        p.addPipe(a); p.addPipe(b); p.addInventory(chest, 128);
        p.insert(a, "minecraft:iron_ingot", 32);
        equal(p.tick(), 1, "First hop");
        equal(p.itemsAt(b), 32, "Second pipe holds stack");
        equal(p.itemsAt(chest), 0, "No two-hop movement in one tick");
        equal(p.tick(), 1, "Second hop");
        equal(p.itemsAt(chest), 32, "Delivered to inventory");
        equal(p.totalItems(), 32, "No lost or duplicated items");
        tests++;

        PipeNetwork full = new PipeNetwork();
        Pos start = new Pos(0, 0, 0), target = new Pos(0, 0, 1);
        full.addPipe(start); full.addInventory(target, 20);
        full.insert(start, "minecraft:stone", 21);
        equal(full.tick(), 0, "Full destination applies backpressure");
        equal(full.itemsAt(start), 21, "Items stay in pipe");
        equal(full.totalItems(), 21, "Backpressure conserves items");
        tests++;

        PipeNetwork split = new PipeNetwork();
        Pos fork = new Pos(0, 0, 0), north = new Pos(0, 0, -1), south = new Pos(0, 0, 1);
        split.addPipe(fork); split.addInventory(north, 64); split.addInventory(south, 64);
        for (int i = 0; i < 4; i++) split.insert(fork, "minecraft:coal", 1);
        equal(split.tick(), 4, "Four packets split in one tick");
        equal(split.itemsAt(north), 2, "Round-robin first inventory");
        equal(split.itemsAt(south), 2, "Round-robin second inventory");
        equal(split.totalItems(), 4, "Fork conservation");
        tests++;

        fails(() -> split.removeEmpty(north), "Cannot delete items when removing nodes");
        fails(() -> split.addPipe(fork), "Duplicate position should fail");
        fails(() -> split.insert(fork, "minecraft:diamond", 65), "Oversized stack should fail");
        tests++;

        PipeNetwork preferSink = new PipeNetwork();
        Pos center = new Pos(0, 0, 0);
        Pos candidatePipe = new Pos(0, 0, -1);
        Pos candidateChest = new Pos(0, 0, 1);
        preferSink.addPipe(center); preferSink.addPipe(candidatePipe);
        preferSink.addInventory(candidateChest, 64);
        preferSink.insert(center, "minecraft:gold_ingot", 4);
        equal(preferSink.tick(), 1, "Packet leaves junction");
        equal(preferSink.itemsAt(candidateChest), 4, "Inventory sink preferred over forwarding pipe");
        equal(preferSink.itemsAt(candidatePipe), 0, "Packet did not bypass chest");
        tests++;

        PipeNetwork deadEnd = new PipeNetwork();
        Pos one = new Pos(0, 0, 0), two = new Pos(1, 0, 0);
        deadEnd.addPipe(one); deadEnd.addPipe(two);
        deadEnd.insert(one, "minecraft:gold_ingot", 5);
        equal(deadEnd.tick(), 1, "Moves into dead end");
        equal(deadEnd.tick(), 0, "Does not immediately bounce backward");
        equal(deadEnd.itemsAt(two), 5, "Retains stalled packets");
        tests++;

        System.out.println("PASS: " + tests + " BuildCraft CML transport tests");
    }
}
