package dev.howlingwhispers.waypoints;

import dev.howlingwhispers.codaloader.api.CodaPosition;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Per-world, schema-versioned waypoints. Never stores data in a global profile. */
public final class WaypointStore {
    public static final int CAP = 200;
    public record Waypoint(CodaPosition position, String note, Instant created) {}

    public static String name(String input) {
        if (input == null) throw new IllegalArgumentException("Waypoint name is required. Use /wp set <name>.");
        String normalized = input.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,24}"))
            throw new IllegalArgumentException("Names must be 1-24 characters: a-z, 0-9, _ or -.");
        return normalized;
    }
    public static String note(String input) {
        String normalized = input == null ? "" : input.strip();
        if (normalized.codePointCount(0, normalized.length()) > 80)
            throw new IllegalArgumentException("Waypoint notes cannot exceed 80 characters.");
        return normalized;
    }

    private Path file(Path world) {
        if (world == null) throw new IllegalArgumentException("World directory unavailable");
        return world.toAbsolutePath().normalize().resolve("hw/hw_waypoints/data.json");
    }
    private static Map<String, Object> obj(Object value) throws IOException {
        if (!(value instanceof Map<?,?> source)) throw new IOException("Expected JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var item : source.entrySet()) {
            if (!(item.getKey() instanceof String key)) throw new IOException("Invalid JSON key");
            result.put(key, item.getValue());
        }
        return result;
    }
    private static String str(Object value) throws IOException {
        if (value instanceof String s) return s;
        throw new IOException("Missing or invalid waypoint text field");
    }
    private static double finite(Object value) throws IOException {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IOException("Missing or invalid position value");
        return number.doubleValue();
    }
    private static float finiteFloat(Object value) throws IOException {
        double number = finite(value);
        if (number < -Float.MAX_VALUE || number > Float.MAX_VALUE) throw new IOException("Invalid facing value");
        return (float) number;
    }
    private static Map<String, Waypoint> decode(String json) throws IOException {
        Map<String, Object> root = WaypointJson.parseObject(json);
        if (finite(root.get("schema")) != 1) throw new IOException("Unsupported waypoint schema");
        Map<String, Object> raw = obj(root.get("waypoints"));
        if (raw.size() > CAP) throw new IOException("Too many saved waypoints");
        Map<String, Waypoint> result = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            String key;
            try { key = name(entry.getKey()); }
            catch (IllegalArgumentException ex) { throw new IOException("Invalid waypoint key", ex); }
            if (!key.equals(entry.getKey()) || result.containsKey(key)) throw new IOException("Duplicate waypoint name");
            Map<String, Object> row = obj(entry.getValue());
            CodaPosition pos;
            Instant created;
            String note;
            try {
                pos = new CodaPosition(str(row.get("dimension")), finite(row.get("x")), finite(row.get("y")),
                        finite(row.get("z")), finiteFloat(row.get("yaw")), finiteFloat(row.get("pitch")));
                created = Instant.parse(str(row.get("created")));
                note = note(str(row.get("note")));
            } catch (IllegalArgumentException | DateTimeParseException ex) {
                throw new IOException("Invalid stored waypoint", ex);
            }
            result.put(key, new Waypoint(pos, note, created));
        }
        return result;
    }
    private static String encode(Map<String, Waypoint> points) {
        StringBuilder out = new StringBuilder("{\n  \"schema\": 1,\n  \"waypoints\": {");
        boolean first = true;
        for (var entry : points.entrySet()) {
            if (!first) out.append(',');
            first = false;
            CodaPosition p = entry.getValue().position();
            out.append("\n    ").append(WaypointJson.quote(entry.getKey())).append(": {")
                    .append("\"dimension\": ").append(WaypointJson.quote(p.dimension()))
                    .append(", \"x\": ").append(p.x()).append(", \"y\": ").append(p.y())
                    .append(", \"z\": ").append(p.z()).append(", \"yaw\": ").append(p.yaw())
                    .append(", \"pitch\": ").append(p.pitch())
                    .append(", \"note\": ").append(WaypointJson.quote(entry.getValue().note()))
                    .append(", \"created\": ").append(WaypointJson.quote(entry.getValue().created().toString()))
                    .append('}');
        }
        return out.append("\n  }\n}\n").toString();
    }

    private static Map<String, Waypoint> readFile(Path file) throws IOException {
        if (Files.size(file) > 1_000_000) throw new IOException("Waypoint data exceeds 1 MB");
        return decode(Files.readString(file, StandardCharsets.UTF_8));
    }
    private static void quarantine(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Path target = path.resolveSibling(path.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        Files.move(path, target);
    }
    private Map<String, Waypoint> read(Path path) throws IOException {
        Path backup = path.resolveSibling("data.json.bak");
        if (!Files.exists(path)) {
            if (Files.exists(backup)) return readFile(backup);
            return new LinkedHashMap<>();
        }
        try { return readFile(path); }
        catch (IOException ex) {
            quarantine(path); // Never delete damaged player data.
            if (Files.exists(backup)) {
                try { return readFile(backup); }
                catch (IOException backupError) {
                    quarantine(backup);
                }
            }
            return new LinkedHashMap<>();
        }
    }
    private static void durableWrite(Path path, String contents) throws IOException {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(contents);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
    private static void save(Path path, Map<String, Waypoint> points) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling("data.json.tmp");
        Path backup = path.resolveSibling("data.json.bak");
        durableWrite(temp, encode(points));
        try {
            // Do not preserve a corrupt old primary as the new backup.
            if (Files.exists(path)) {
                Map<String, Waypoint> prior = readFile(path);
                durableWrite(backup.resolveSibling("data.json.bak.tmp"), encode(prior));
                Files.move(backup.resolveSibling("data.json.bak.tmp"), backup,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            throw new IOException("Atomic world saving not supported on this drive", ex);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
    public synchronized Map<String, Waypoint> all(Path world) throws IOException {
        return Map.copyOf(read(file(world)));
    }
    public synchronized void set(Path world, String requestedName, CodaPosition position,
            String requestedNote, boolean replace) throws IOException {
        String key = name(requestedName);
        String savedNote = note(requestedNote);
        Path path = file(world);
        Map<String, Waypoint> points = read(path);
        Waypoint old = points.get(key);
        if (old != null && !replace) throw new IllegalArgumentException("'" + key + "' exists. Use /wp set " + key + " --replace.");
        if (old == null && points.size() >= CAP) throw new IllegalArgumentException("World waypoint limit reached (200).");
        points.put(key, new Waypoint(position, savedNote, old == null ? Instant.now() : old.created()));
        save(path, points);
    }
    public synchronized boolean delete(Path world, String requestedName) throws IOException {
        String key = name(requestedName);
        Path path = file(world);
        Map<String, Waypoint> points = read(path);
        if (points.remove(key) == null) return false;
        save(path, points);
        return true;
    }
}
