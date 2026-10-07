package dev.howlingwhispers.essentials;

import dev.howlingwhispers.codaloader.api.CodaPosition;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/** World-local, player-local homes. Reads fail closed; failed saves never replace the original. */
public final class HomeStore {
    private final int limit;
    public HomeStore(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("max-homes must be between 1 and 100");
        this.limit = limit;
    }

    public static String name(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]{1,32}"))
            throw new IllegalArgumentException("Home names need 1–32 letters, numbers, underscores or hyphens.");
        return value;
    }

    private Path file(Path world, UUID player) {
        return world.toAbsolutePath().normalize().resolve("cml/hw-essentials/homes").resolve(player + ".properties");
    }

    public synchronized Map<String, CodaPosition> homes(Path world, UUID player) throws IOException {
        Path path = file(world, player);
        if (!Files.exists(path)) return new TreeMap<>();
        if (Files.size(path) > 262144) throw new IOException("Home file is too large; saved homes were left untouched.");
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(path)) { data.load(input); }
        try {
            if (!"1".equals(data.getProperty("schema"))) throw new IllegalArgumentException("Unknown home schema");
            Map<String, CodaPosition> result = new TreeMap<>();
            for (String key : data.stringPropertyNames()) {
                if (!key.startsWith("home.") || !key.endsWith(".dimension")) continue;
                String home = name(key.substring(5, key.length() - 10));
                String prefix = "home." + home + ".";
                result.put(home, new CodaPosition(data.getProperty(key),
                        Double.parseDouble(data.getProperty(prefix + "x")), Double.parseDouble(data.getProperty(prefix + "y")),
                        Double.parseDouble(data.getProperty(prefix + "z")), Float.parseFloat(data.getProperty(prefix + "yaw")),
                        Float.parseFloat(data.getProperty(prefix + "pitch"))));
            }
            // Every position must be complete; do not silently drop a damaged home on the next write.
            for (String key : data.stringPropertyNames()) {
                if (key.equals("schema")) continue;
                if (!key.matches("home\\.[a-z0-9_-]{1,32}\\.(dimension|x|y|z|yaw|pitch)"))
                    throw new IllegalArgumentException("Invalid home property");
                String home = key.substring(5, key.lastIndexOf('.'));
                if (!result.containsKey(home)) throw new IllegalArgumentException("Incomplete home");
            }
            return result;
        } catch (RuntimeException ex) {
            throw new IOException("Coda couldn't read your saved homes. The file was left untouched.", ex);
        }
    }

    public synchronized void set(Path world, UUID player, String raw, CodaPosition position) throws IOException {
        String name = name(raw);
        Map<String, CodaPosition> homes = homes(world, player);
        if (!homes.containsKey(name) && homes.size() >= limit)
            throw new IllegalArgumentException("Your " + limit + " home slots are full. Use /delhome <name> first.");
        homes.put(name, position);
        save(world, player, homes);
    }

    public synchronized boolean delete(Path world, UUID player, String raw) throws IOException {
        Map<String, CodaPosition> homes = homes(world, player);
        if (homes.remove(name(raw)) == null) return false;
        save(world, player, homes);
        return true;
    }

    private void save(Path world, UUID player, Map<String, CodaPosition> homes) throws IOException {
        Properties data = new Properties();
        data.setProperty("schema", "1");
        homes.forEach((name, p) -> {
            String prefix = "home." + name + ".";
            data.setProperty(prefix + "dimension", p.dimension());
            data.setProperty(prefix + "x", Double.toString(p.x()));
            data.setProperty(prefix + "y", Double.toString(p.y()));
            data.setProperty(prefix + "z", Double.toString(p.z()));
            data.setProperty(prefix + "yaw", Float.toString(p.yaw()));
            data.setProperty(prefix + "pitch", Float.toString(p.pitch()));
        });
        Path destination = file(world, player);
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), "homes-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { data.store(output, "HW Essentials homes v1"); }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("This disk cannot safely replace the home file; saved homes were left untouched.", ex);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
