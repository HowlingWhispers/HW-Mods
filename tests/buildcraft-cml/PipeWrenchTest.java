import dev.howlingwhispers.buildcraft.PipeNetwork;
import dev.howlingwhispers.buildcraft.PipeNetwork.Direction;
import dev.howlingwhispers.buildcraft.PipeNetwork.NodeState;
import dev.howlingwhispers.buildcraft.PipeNetwork.PacketState;
import dev.howlingwhispers.buildcraft.PipeNetwork.PipeSettings;
import dev.howlingwhispers.buildcraft.PipeNetwork.Pos;
import dev.howlingwhispers.buildcraft.PipeNetworkStore;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

/** Original CML fixtures: no live Minecraft gameplay is claimed here. */
public final class PipeWrenchTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void rejected(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }

    /** Build a true v1 byte stream as it was written before pipe settings existed. */
    private static byte[] legacySave(PipeNetwork network) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x42434d4c);
            output.writeInt(1);
            List<NodeState> nodes = network.snapshot();
            output.writeInt(nodes.size());
            for (NodeState node : nodes) {
                output.writeInt(node.pos().x());
                output.writeInt(node.pos().y());
                output.writeInt(node.pos().z());
                output.writeBoolean(node.inventory());
                output.writeInt(node.capacity());
                output.writeInt(node.cursor());
                output.writeInt(node.packets().size());
                for (PacketState packet : node.packets()) {
                    output.writeUTF(packet.itemId());
                    output.writeByte(packet.amount());
                    output.writeByte(packet.enteredBy() == null ? -1 : packet.enteredBy().ordinal());
                }
            }
        }
        byte[] body = bytes.toByteArray();
        bytes.write(MessageDigest.getInstance("SHA-256").digest(body));
        return bytes.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Pos junction = new Pos(0, 64, 0);
        Pos north = new Pos(0, 64, -1);
        Pos south = new Pos(0, 64, 1);

        PipeNetwork directed = new PipeNetwork();
        directed.addPipe(junction);
        directed.addInventory(north, 64);
        directed.addInventory(south, 64);
        directed.configure(junction, new PipeSettings(Direction.SOUTH, null));
        directed.insert(junction, "minecraft:iron_ingot", 12);
        check(directed.tick() == 1, "Configured output delivers a packet");
        check(directed.itemsAt(south) == 12, "Directional pipe chooses SOUTH");
        check(directed.itemsAt(north) == 0, "No packet leaks to other route");

        PipeNetwork blocked = new PipeNetwork();
        blocked.addPipe(junction);
        blocked.addInventory(north, 64);
        blocked.addInventory(south, 10);
        blocked.configure(junction, new PipeSettings(Direction.SOUTH, null));
        blocked.insert(junction, "minecraft:gold_ingot", 11);
        check(blocked.tick() == 0, "Configured output applies backpressure rather than rerouting");
        check(blocked.itemsAt(north) == 0 && blocked.itemsAt(junction) == 11,
                "Backpressure never diverts or deletes cargo");

        PipeNetwork filtered = new PipeNetwork();
        filtered.addPipe(junction);
        filtered.addInventory(south, 64);
        filtered.configure(junction, new PipeSettings(Direction.SOUTH, "minecraft:iron_ingot"));
        filtered.insert(junction, "minecraft:copper_ingot", 4);
        filtered.insert(junction, "minecraft:iron_ingot", 6);
        check(filtered.tick() == 1, "Only whitelisted packets advance");
        check(filtered.itemsAt(junction) == 4, "Unmatched item remains in pipe");
        check(filtered.itemsAt(south) == 6, "Matched item is delivered");
        check(filtered.totalItems() == 10, "Filtering conserves all cargo");

        byte[] saved = PipeNetworkStore.encode(filtered);
        PipeNetwork loaded = PipeNetworkStore.decode(saved);
        check(loaded.snapshot().equals(filtered.snapshot()), "V2 save preserves wrench and filter settings");
        check(loaded.tick() == 0, "Blocked cargo stays blocked after a reload");
        loaded.configure(junction, new PipeSettings(Direction.SOUTH, null));
        check(loaded.tick() == 1, "Clearing filter releases stored cargo");
        check(loaded.itemsAt(south) == 10, "Saved and resumed cargo is conserved");

        PipeNetwork rotated = new PipeNetwork();
        rotated.addPipe(junction);
        rotated.configure(junction, new PipeSettings(null, "minecraft:coal"));
        for (Direction d : Direction.values()) {
            check(rotated.wrenchRotate(junction) == d, "Wrench cycles " + d);
            check(rotated.settings(junction).itemFilter().equals("minecraft:coal"),
                    "Wrench preserves configured filter");
        }
        check(rotated.wrenchRotate(junction) == null, "Seventh wrench action restores auto direction");
        check(rotated.settings(junction).equals(new PipeSettings(null, "minecraft:coal")),
                "Auto mode keeps filter");

        // A v1 world migrates with an automatic, unrestricted routing default.
        PipeNetwork classic = new PipeNetwork();
        classic.addPipe(junction);
        classic.addInventory(south, 64);
        classic.insert(junction, "minecraft:stone", 7);
        PipeNetwork classicLoaded = PipeNetworkStore.decode(legacySave(classic));
        check(classicLoaded.snapshot().equals(classic.snapshot()), "v1 saved cargo migrates unchanged");
        check(classicLoaded.settings(junction).equals(PipeSettings.DEFAULT),
                "Legacy pipe defaults to unrestricted output");
        check(classicLoaded.tick() == 1, "Legacy cargo can still move after migration");
        check(classicLoaded.totalItems() == 7, "Legacy movement conserves items");

        rejected(() -> new PipeSettings(null, ""), "Empty item identifier cannot become filter");
        rejected(() -> new PipeSettings(null, "NOT_A_NAMESPACED_ID"),
                "Unnamespaced filter must fail closed");
        rejected(() -> directed.configure(south, PipeSettings.DEFAULT),
                "Wrench must not configure an inventory");
        rejected(() -> PipeNetwork.restore(List.of(
                new NodeState(south, true, 64, 0, List.of(), new PipeSettings(Direction.SOUTH, null)))),
                "Inventory save must not contain pipe controls");

        System.out.println("PASS: " + checks + " wrench routing, filtering and legacy-save checks");
    }
}
