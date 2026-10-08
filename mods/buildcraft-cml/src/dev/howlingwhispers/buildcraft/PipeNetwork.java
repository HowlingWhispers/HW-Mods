package dev.howlingwhispers.buildcraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minecraft-independent, server-authoritative transport prototype.
 *
 * One tick moves each item packet at most one adjacent node. Packets never enter
 * an unregistered position, never split or duplicate, and back up when
 * their next destination is full. Minecraft adapters must call tick on the
 * integrated/dedicated server thread, not the render thread.
 *
 * This is original CML code, not a copy of BuildCraft's Forge implementation.
 */
public final class PipeNetwork {
    public record Pos(int x, int y, int z) {
        public Pos offset(Direction d) {
            return new Pos(x + d.dx, y + d.dy, z + d.dz);
        }
    }

    public enum Direction {
        NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0),
        WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);
        final int dx, dy, dz;
        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
        public Direction opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }
    }

    private enum Kind { PIPE, INVENTORY }

    private record Packet(String itemId, int amount, Direction enteredBy) {
        Packet {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isBlank() || amount < 1 || amount > 64) {
                throw new IllegalArgumentException("Item ID must be nonblank and stack size 1..64");
            }
        }
    }

    private static final int PIPE_PACKET_CAPACITY = 8;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Comparator<Pos> POSITION_ORDER = Comparator
            .comparingInt(Pos::x).thenComparingInt(Pos::y).thenComparingInt(Pos::z);

    private static final class Node {
        final Kind kind;
        final int capacity;
        final Deque<Packet> packets = new ArrayDeque<>();
        int cursor;
        Node(Kind kind, int capacity) {
            this.kind = kind;
            this.capacity = capacity;
        }
    }

    private record Move(Pos source, Pos destination, Packet packet, Direction direction) {}
    private final Map<Pos, Node> nodes = new HashMap<>();

    public void addPipe(Pos pos) {
        add(pos, new Node(Kind.PIPE, PIPE_PACKET_CAPACITY));
    }

    public void addInventory(Pos pos, int itemCapacity) {
        if (itemCapacity < 1) throw new IllegalArgumentException("Inventory capacity must be positive");
        add(pos, new Node(Kind.INVENTORY, itemCapacity));
    }

    private void add(Pos pos, Node node) {
        Objects.requireNonNull(pos, "pos");
        if (nodes.putIfAbsent(pos, node) != null) {
            throw new IllegalArgumentException("Position already registered: " + pos);
        }
    }

    /** Insert a virtual stack into a pipe for testing or from a game adapter. */
    public void insert(Pos pipe, String itemId, int amount) {
        Packet packet = new Packet(itemId, amount, null);
        Node node = require(pipe, Kind.PIPE);
        if (node.packets.size() >= node.capacity) {
            throw new IllegalStateException("Pipe is full: " + pipe);
        }
        node.packets.addLast(packet);
    }

    /** Remove only an EMPTY pipe/inventory, so breaking a block cannot void items. */
    public void removeEmpty(Pos pos) {
        Node node = Objects.requireNonNull(nodes.get(pos), "Unknown position");
        if (!node.packets.isEmpty()) throw new IllegalStateException("Drain or drop stored items first");
        nodes.remove(pos);
    }

    public int itemsAt(Pos pos) {
        Node node = Objects.requireNonNull(nodes.get(pos), "Unknown position");
        return node.packets.stream().mapToInt(Packet::amount).sum();
    }

    public int totalItems() {
        return nodes.values().stream().flatMap(n -> n.packets.stream()).mapToInt(Packet::amount).sum();
    }

    /** Simulate a single game tick; returns the number of packets advanced. */
    public int tick() {
        List<Pos> positions = nodes.keySet().stream().sorted(POSITION_ORDER).toList();
        List<Move> moves = new ArrayList<>();
        Map<Pos, Integer> reservedPackets = new HashMap<>();
        Map<Pos, Integer> reservedItems = new HashMap<>();
        for (Pos pos : positions) {
            Node node = nodes.get(pos);
            if (node.kind != Kind.PIPE) continue;
            for (Packet packet : node.packets) {
                Move move = findDestination(pos, node, packet, reservedPackets, reservedItems);
                if (move == null) continue;
                moves.add(move);
                Node dest = nodes.get(move.destination);
                if (dest.kind == Kind.PIPE) {
                    reservedPackets.merge(move.destination, 1, Integer::sum);
                } else {
                    reservedItems.merge(move.destination, packet.amount, Integer::sum);
                }
            }
        }
        // Commit after planning all routes so no stack moves twice in one tick.
        for (Move move : moves) {
            Node source = nodes.get(move.source);
            if (!source.packets.removeFirstOccurrence(move.packet)) {
                throw new IllegalStateException("Transport source changed during tick");
            }
            nodes.get(move.destination).packets.addLast(
                    new Packet(move.packet.itemId, move.packet.amount, move.direction));
        }
        return moves.size();
    }

    private Move findDestination(Pos pos, Node source, Packet packet,
            Map<Pos, Integer> reservedPackets, Map<Pos, Integer> reservedItems) {
        // Deliver to inventories first; when none can accept, route along pipes.
        for (Kind sought : new Kind[] {Kind.INVENTORY, Kind.PIPE}) {
            for (int step = 0; step < DIRECTIONS.length; step++) {
                int idx = (source.cursor + step) % DIRECTIONS.length;
                Direction d = DIRECTIONS[idx];
                if (packet.enteredBy != null && d == packet.enteredBy.opposite()) continue;
                Pos neighborPos = pos.offset(d);
                Node dest = nodes.get(neighborPos);
                if (dest == null || dest.kind != sought) continue;
                boolean hasRoom = dest.kind == Kind.PIPE
                        ? dest.packets.size() + reservedPackets.getOrDefault(neighborPos, 0) < dest.capacity
                        : itemsAt(neighborPos) + reservedItems.getOrDefault(neighborPos, 0)
                                + packet.amount <= dest.capacity;
                if (hasRoom) {
                    source.cursor = (idx + 1) % DIRECTIONS.length;
                    return new Move(pos, neighborPos, packet, d);
                }
            }
        }
        return null;
    }


    /** Immutable, ordered state for lossless chunk-save and restart handoff. */
    public record PacketState(String itemId, int amount, Direction enteredBy) {}

    public record NodeState(Pos pos, boolean inventory, int capacity, int cursor,
                            List<PacketState> packets) {
        public NodeState {
            Objects.requireNonNull(pos, "pos");
            packets = List.copyOf(packets);
        }
    }

    /**
     * Capture all in-flight stacks, sink contents and junction cursors.
     * Call on the authoritative server thread between ticks.
     */
    public List<NodeState> snapshot() {
        List<NodeState> result = new ArrayList<>();
        for (Pos pos : nodes.keySet().stream().sorted(POSITION_ORDER).toList()) {
            Node node = nodes.get(pos);
            List<PacketState> packets = node.packets.stream()
                    .map(p -> new PacketState(p.itemId, p.amount, p.enteredBy)).toList();
            result.add(new NodeState(pos, node.kind == Kind.INVENTORY,
                    node.capacity, node.cursor, packets));
        }
        return List.copyOf(result);
    }

    /**
     * Validate the entire snapshot before exposing a restored network.
     * A corrupt save cannot partially create a live transport network.
     */
    public static PipeNetwork restore(List<NodeState> saved) {
        Objects.requireNonNull(saved, "saved");
        if (saved.size() > 100_000)
            throw new IllegalArgumentException("Too many transport nodes");
        PipeNetwork result = new PipeNetwork();
        for (NodeState state : saved) {
            Objects.requireNonNull(state, "node");
            if (state.capacity() < 1 || state.capacity() > 1_000_000
                    || (!state.inventory() && state.capacity() != PIPE_PACKET_CAPACITY))
                throw new IllegalArgumentException("Invalid node capacity at " + state.pos());
            if (state.cursor() < 0 || state.cursor() >= DIRECTIONS.length)
                throw new IllegalArgumentException("Invalid junction cursor at " + state.pos());
            if (state.packets().size() > (state.inventory() ? 1_000_000 : PIPE_PACKET_CAPACITY))
                throw new IllegalArgumentException("Too many stacks at " + state.pos());
            if (state.inventory()) result.addInventory(state.pos(), state.capacity());
            else result.addPipe(state.pos());
            Node node = result.nodes.get(state.pos());
            node.cursor = state.cursor();
            long total = 0;
            for (PacketState packet : state.packets()) {
                Objects.requireNonNull(packet, "packet");
                Packet validated = new Packet(packet.itemId(), packet.amount(), packet.enteredBy());
                if (validated.itemId.length() > 128)
                    throw new IllegalArgumentException("Item identifier too long at " + state.pos());
                node.packets.addLast(validated);
                total += validated.amount();
                if (state.inventory() && total > state.capacity())
                    throw new IllegalArgumentException("Inventory over capacity at " + state.pos());
            }
        }
        return result;
    }


    private Node require(Pos pos, Kind kind) {
        Node node = Objects.requireNonNull(nodes.get(pos), "Unknown position");
        if (node.kind != kind) throw new IllegalArgumentException("Not a " + kind + ": " + pos);
        return node;
    }
}
