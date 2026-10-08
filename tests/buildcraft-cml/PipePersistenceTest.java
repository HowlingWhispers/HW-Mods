import dev.howlingwhispers.buildcraft.PipeNetwork;
import dev.howlingwhispers.buildcraft.PipeNetwork.Pos;
import dev.howlingwhispers.buildcraft.PipeNetwork.NodeState;
import dev.howlingwhispers.buildcraft.PipeNetwork.PacketState;
import dev.howlingwhispers.buildcraft.PipeNetworkStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class PipePersistenceTest {
    private static int tests;
    private static void equal(Object actual, Object expected, String reason) {
        if (!actual.equals(expected)) throw new AssertionError(reason + ": " + actual + " != " + expected);
    }
    private static void rejected(Runnable f, String reason) {
        try { f.run(); } catch (IllegalArgumentException expected) { tests++; return; }
        throw new AssertionError(reason);
    }
    private static void invalid(byte[] data, String reason) {
        try { PipeNetworkStore.decode(data); } catch (IOException expected) { tests++; return; }
        throw new AssertionError(reason);
    }
    public static void main(String[] args) throws Exception {
        Pos source = new Pos(0, 64, 0);
        Pos fork = new Pos(1, 64, 0);
        Pos chest = new Pos(2, 64, 0);
        PipeNetwork network = new PipeNetwork();
        network.addPipe(source);
        network.addPipe(fork);
        network.addInventory(chest, 128);
        network.insert(source, "minecraft:iron_ingot", 30);
        network.insert(source, "minecraft:gold_ingot", 20);
        equal(network.tick(), 2, "Move packets to junction");
        tests++;
        byte[] saved = PipeNetworkStore.encode(network);
        PipeNetwork resumed = PipeNetworkStore.decode(saved);
        equal(resumed.snapshot(), network.snapshot(), "All packets, routing state, node kinds survived serialization");
        equal(resumed.totalItems(), 50, "No items lost during save/reload");
        tests += 2;
        equal(resumed.tick(), 2, "Pipes continue routing after reload");
        equal(resumed.itemsAt(chest), 50, "Both stacks reached inventory");
        equal(resumed.totalItems(), 50, "Total conserved after resume");
        tests += 3;

        // Checksum prevents partial/corrupt game data being accepted.
        byte[] corrupted = saved.clone();
        corrupted[14] ^= 4;
        invalid(corrupted, "Modified save passed checksum");
        invalid(Arrays.copyOf(saved, saved.length - 1), "Truncated save was accepted");
        invalid(new byte[12], "Too short save was accepted");

        // Invalid routing, duplicate nodes and over-capacity stacks must fail closed.
        rejected(() -> PipeNetwork.restore(List.of(
                new NodeState(source, false, 8, 6, List.of()))), "Invalid cursor accepted");
        rejected(() -> PipeNetwork.restore(List.of(
                new NodeState(source, false, 8, 0, List.of()),
                new NodeState(source, true, 20, 0, List.of()))), "Duplicate node accepted");
        rejected(() -> PipeNetwork.restore(List.of(
                new NodeState(chest, true, 2, 0,
                        List.of(new PacketState("minecraft:iron_ingot", 3, null))))),
                "Overflow accepted");

        Path dir = Files.createTempDirectory("buildcraft-pipes-");
        try {
            Path file = dir.resolve("network.bcml");
            PipeNetworkStore.save(file, network);
            equal(PipeNetworkStore.load(file).snapshot(), network.snapshot(), "Atomic file roundtrip");
            tests++;
            byte[] original = Files.readAllBytes(file);
            Files.write(file, Arrays.copyOf(original, original.length - 2));
            try { PipeNetworkStore.load(file); throw new AssertionError("Truncated file load succeeded"); }
            catch (IOException expected) { tests++; }
            // Invalid load does not erase or replace the player's saved bytes.
            equal(Files.readAllBytes(file).length, original.length - 2, "Corrupt file was rewritten");
            tests++;
            PipeNetworkStore.save(file, network);
            equal(PipeNetworkStore.load(file).totalItems(), 50, "Recovered with good save");
            tests++;
        } finally {
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(dir);
        }
        System.out.println("PASS: " + tests + " BuildCraft persistence/recovery checks");
    }
}
