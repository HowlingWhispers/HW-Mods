import dev.howlingwhispers.buildcraft.PipeNetwork;
import dev.howlingwhispers.buildcraft.PipeNetwork.Direction;
import dev.howlingwhispers.buildcraft.PipeNetwork.PipeSettings;
import dev.howlingwhispers.buildcraft.PipeNetwork.Pos;
import dev.howlingwhispers.buildcraft.PipeNetworkStore;

/** A classic wooden-pipe EXPLICIT pulse, tested only with virtual inventories. */
public final class WoodenExtractionTest {
    private static int checks;

    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    private static void invalid(Runnable run, String reason) {
        try { run.run(); }
        catch (IllegalStateException | IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        Pos input = new Pos(0, 60, 0), wooden = new Pos(1, 60, 0),
                transport = new Pos(2, 60, 0), output = new Pos(3, 60, 0);
        PipeNetwork network = new PipeNetwork();
        network.addInventory(input, 128);
        network.addPipe(wooden);
        network.addPipe(transport);
        network.addInventory(output, 128);
        network.stockVirtualInventory(input, "minecraft:iron_ingot", 48);
        network.stockVirtualInventory(input, "minecraft:coal", 5);
        check(network.totalItems() == 53, "Fixture starts with conserved inventory stock");
        check(network.extractVirtualOnPulse(input, wooden, 16, pos -> false) == 0,
                "An unloaded source or pipe refuses extraction");
        check(network.totalItems() == 53 && network.itemsAt(wooden) == 0,
                "Unloaded extraction leaves all items untouched");

        check(network.extractVirtualOnPulse(input, wooden, 16, pos -> true) == 16,
                "Power pulse extracts at most configured stack size");
        check(network.itemsAt(input) == 37 && network.itemsAt(wooden) == 16,
                "Partial extraction splits without duplication");
        check(network.totalItems() == 53, "Pulse preserves total item count");

        // Source exclusion: items exiting a wooden pipe do not bounce back
        // to the same inventory they were pulled from.
        check(network.tick() == 1, "Extracted packet advances one hop");
        check(network.itemsAt(input) == 37 && network.itemsAt(transport) == 16,
                "First hop did not bounce into the extraction chest");
        check(network.tick() == 1, "Transport pipe delivers packet to output chest");
        check(network.itemsAt(output) == 16 && network.totalItems() == 53,
                "Transport and extraction cargo conserved together");

        // Filters apply at the wooden input, selecting a matching inventory slot.
        network.configure(wooden, new PipeSettings(Direction.EAST, "minecraft:coal"));
        check(network.extractVirtualOnPulse(input, wooden, 64, pos -> true) == 5,
                "Whitelisted item can be pulled from later virtual inventory slot");
        check(network.itemsAt(input) == 32, "Nonmatching inventory items remain");
        check(network.extractVirtualOnPulse(input, wooden, 64, pos -> true) == 0,
                "No matching items means no extraction");
        check(network.totalItems() == 53, "Filtering preserves all item counts");

        // A mid-flight save must keep extracted items, source stock and config.
        byte[] saved = PipeNetworkStore.encode(network);
        PipeNetwork resumed = PipeNetworkStore.decode(saved);
        check(resumed.snapshot().equals(network.snapshot()), "Pulse state survives save/restart");
        check(resumed.tick() == 1, "Saved extracted items resume transport");
        check(resumed.tick() == 1, "Saved cargo is delivered");
        check(resumed.itemsAt(output) == 21 && resumed.totalItems() == 53,
                "Save and resume neither lose nor duplicate stock");

        // Full transport pipe is a lossless hard stop.
        PipeNetwork congested = new PipeNetwork();
        congested.addInventory(input, 64);
        congested.addPipe(wooden);
        congested.stockVirtualInventory(input, "minecraft:stone", 20);
        for (int i = 0; i < 8; i++) congested.insert(wooden, "minecraft:diamond", 1);
        check(congested.extractVirtualOnPulse(input, wooden, 8, pos -> true) == 0,
                "Full wooden pipe refuses new extraction");
        check(congested.itemsAt(input) == 20 && congested.totalItems() == 28,
                "Congestion does not void inventory contents");

        // Reject invalid or hazardous extraction requests.
        invalid(() -> resumed.extractVirtualOnPulse(input, output, 1, pos -> true),
                "Distant inventory and pipe must not connect");
        invalid(() -> resumed.extractVirtualOnPulse(input, wooden, 0, pos -> true),
                "Zero-sized pulse must be refused");
        invalid(() -> resumed.extractVirtualOnPulse(input, wooden, 65, pos -> true),
                "Oversized pulse must be refused");
        invalid(() -> resumed.stockVirtualInventory(input, "minecraft:stone", 64),
                "Inventory capacity must be enforced");
        invalid(() -> resumed.stockVirtualInventory(wooden, "minecraft:stone", 1),
                "Pipe must not masquerade as a virtual inventory");

        System.out.println("PASS: " + checks + " BuildCraft virtual wooden extraction and conservation checks");
    }
}
