package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.buildcraft.PipeNetwork.Direction;
import dev.howlingwhispers.buildcraft.PipeNetwork.NodeState;
import dev.howlingwhispers.buildcraft.PipeNetwork.PacketState;
import dev.howlingwhispers.buildcraft.PipeNetwork.Pos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Versioned, checksum-protected, atomic state for a single transport network.
 * Never load from or save to the client/render thread. The future Minecraft
 * adapter must call this on the integrated server thread at lifecycle boundaries.
 */
public final class PipeNetworkStore {
    private static final int MAGIC = 0x42434d4c; // BCML
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_SAVE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_NODES = 100_000;
    private static final int MAX_STACKS_PER_NODE = 1_000_000;

    private PipeNetworkStore() {}

    public static void save(Path destination, PipeNetwork network) throws IOException {
        byte[] bytes = encode(network);
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Transport save must have a parent directory");
        if (Files.isSymbolicLink(absolute)) throw new IOException("Refusing to replace a symlink");
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".buildcraft-", ".tmp");
        try {
            Files.write(temp, bytes);
            // Refuse unsafe non-atomic replacement. Failure keeps prior save intact.
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static PipeNetwork load(Path source) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IOException("Refusing symlink transport save");
        if (Files.size(source) > MAX_SAVE_BYTES) throw new IOException("Transport save is too large");
        return decode(Files.readAllBytes(source));
    }

    public static byte[] encode(PipeNetwork network) throws IOException {
        List<NodeState> states = network.snapshot();
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(payload)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(states.size());
            for (NodeState node : states) {
                out.writeInt(node.pos().x());
                out.writeInt(node.pos().y());
                out.writeInt(node.pos().z());
                out.writeBoolean(node.inventory());
                out.writeInt(node.capacity());
                out.writeInt(node.cursor());
                out.writeInt(node.packets().size());
                for (PacketState packet : node.packets()) {
                    out.writeUTF(packet.itemId());
                    out.writeByte(packet.amount());
                    out.writeByte(packet.enteredBy() == null ? -1 : packet.enteredBy().ordinal());
                }
                if (payload.size() > MAX_SAVE_BYTES - DIGEST_BYTES)
                    throw new IOException("Transport save exceeds the size limit");
            }
        }
        byte[] body = payload.toByteArray();
        byte[] digest = sha256(body);
        if (body.length + digest.length > MAX_SAVE_BYTES)
            throw new IOException("Transport save exceeds the size limit");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(body);
        result.write(digest);
        return result.toByteArray();
    }

    public static PipeNetwork decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 12 + DIGEST_BYTES || bytes.length > MAX_SAVE_BYTES)
            throw new IOException("Invalid transport save size");
        int length = bytes.length - DIGEST_BYTES;
        byte[] body = java.util.Arrays.copyOf(bytes, length);
        byte[] digest = java.util.Arrays.copyOfRange(bytes, length, bytes.length);
        if (!MessageDigest.isEqual(sha256(body), digest))
            throw new IOException("Transport save checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid transport save header");
            if (in.readInt() != VERSION) throw new IOException("Unsupported transport save version");
            int count = in.readInt();
            if (count < 0 || count > MAX_NODES) throw new IOException("Invalid node count");
            List<NodeState> states = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Pos pos = new Pos(in.readInt(), in.readInt(), in.readInt());
                boolean inventory = in.readBoolean();
                int capacity = in.readInt();
                int cursor = in.readInt();
                int stacks = in.readInt();
                if (stacks < 0 || stacks > MAX_STACKS_PER_NODE)
                    throw new IOException("Invalid stack count");
                List<PacketState> packets = new ArrayList<>();
                for (int j = 0; j < stacks; j++) {
                    String item = in.readUTF();
                    int amount = in.readUnsignedByte();
                    int ordinal = in.readByte();
                    if (ordinal < -1 || ordinal >= Direction.values().length)
                        throw new IOException("Invalid packet direction");
                    packets.add(new PacketState(item, amount,
                            ordinal == -1 ? null : Direction.values()[ordinal]));
                }
                states.add(new NodeState(pos, inventory, capacity, cursor, packets));
            }
            if (in.available() != 0) throw new IOException("Trailing bytes in transport save");
            try {
                return PipeNetwork.restore(states);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IOException("Invalid transport network: " + ex.getMessage(), ex);
            }
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
