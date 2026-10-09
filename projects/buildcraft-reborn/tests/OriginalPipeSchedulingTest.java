import buildcraft.api.v2.pipe.ItemTransportProfile;
import buildcraft.lib.misc.data.DelayedList;
import java.util.List;

/**
 * Runs the actual unmodified BCCE source used by PipeFlowItems, not a custom
 * simulation. This proves its scheduler semantics but NOT Minecraft cargo
 * movement, native pipe ownership, MJ or rendering.
 */
public final class OriginalPipeSchedulingTest {
    private static int checks;
    private static void require(boolean yes, String reason) {
        checks++;
        if (!yes) throw new AssertionError(reason);
    }
    private static void invalid(Runnable attempt, String reason) {
        try {
            attempt.run();
            throw new AssertionError("Unexpectedly accepted " + reason);
        } catch (IllegalArgumentException expected) { checks++; }
    }
    public static void main(String[] args) {
        DelayedList<String> original = new DelayedList<>();
        require(original.getMaxDelay() == 0, "Original queue begins empty");
        require(original.advance().isEmpty(), "Empty tick advances safely");
        original.add(2, "iron");
        original.add(0, "gold");
        original.add(1, "copper");
        require(original.getMaxDelay() == 3, "Original delay wheel has three ticks");
        require(original.getAllElements().stream().mapToInt(List::size).sum() == 3,
                "All scheduled original item identities retained");
        require(original.advance().equals(List.of("gold")), "Immediate arrival at first tick");
        require(original.advance().equals(List.of("copper")), "One-delay arrival at next tick");
        require(original.advance().equals(List.of("iron")), "Two-delay arrival at third tick");
        require(original.advance().isEmpty(), "No duplicate delivery after arrivals");
        require(original.getMaxDelay() == 0, "Original queue drained");

        original.add(-7, "negative-defaults-zero");
        original.add(0, "same-tick-second");
        require(original.advance().equals(List.of("negative-defaults-zero", "same-tick-second")),
                "Negative delay clamps to zero and preserves insertion order");
        original.add(10, "cancelled");
        original.clear();
        require(original.getMaxDelay() == 0 && original.advance().isEmpty(),
                "Clearing original pipe scheduler removes scheduled arrivals");

        DelayedList<Integer> concurrent = DelayedList.createConcurrent();
        for (int i = 0; i < 64; i++) concurrent.add(i % 4, i);
        int delivered = 0;
        for (int i = 0; i < 4; i++) delivered += concurrent.advance().size();
        require(delivered == 64, "All entries survive the original concurrent queue");

        ItemTransportProfile profile = new ItemTransportProfile(8, 2);
        require(profile.maxItemsPerCycle() == 8 && profile.routingWeight() == 2,
                "Original API2 item transport parameters preserved");
        invalid(() -> new ItemTransportProfile(0, 2), "zero max items");
        invalid(() -> new ItemTransportProfile(-1, 2), "negative max items");
        invalid(() -> new ItemTransportProfile(1, -1), "negative routing weight");
        System.out.println("PASS: " + checks
                + " assertions on original BCCE DelayedList + ItemTransportProfile");
        System.out.println("SOURCE-ONLY: no Minecraft pipe gameplay has been claimed.");
    }
}
