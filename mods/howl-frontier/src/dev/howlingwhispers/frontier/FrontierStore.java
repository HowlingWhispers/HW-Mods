package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/**
 * Per-world state in {@code <world>/howl_frontier/frontier.properties}, schema 2.
 * Writes are atomic and keep the previous version. An unreadable file is reported,
 * never replaced. The 0.1.x file (settlement.properties) is ignored and left alone.
 */
final class FrontierStore {
    static final String FILE = "frontier.properties", PREVIOUS = "frontier.previous.properties";
    private static final int SCHEMA = 2, MAX_BYTES = 4 * 1024 * 1024;
    private final Path folder, file;

    FrontierStore(Path world) throws IOException {
        Path root = world.toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) throw new IOException("Invalid world directory");
        folder = root.resolve("howl_frontier");
        file = folder.resolve(FILE);
        refuseSymlinks();
    }

    Path file() { return file; }

    private void refuseSymlinks() throws IOException {
        for (Path p : List.of(folder, file, folder.resolve(PREVIOUS)))
            if (Files.isSymbolicLink(p)) throw new IOException("Refusing symlinked Frontier state: " + p.getFileName());
    }

    FrontierState load() throws IOException {
        refuseSymlinks();
        if (!Files.exists(file)) return new FrontierState();
        if (Files.size(file) > MAX_BYTES) throw new IOException("Frontier state is larger than " + MAX_BYTES + " bytes");
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) { p.load(in); }
        try {
            return decode(p);
        } catch (RuntimeException invalid) {
            throw new IOException("Frontier state is invalid (" + invalid.getMessage() + "); the file was left unchanged", invalid);
        }
    }

    void save(FrontierState state) throws IOException {
        refuseSymlinks();
        Files.createDirectories(folder);
        Properties p = encode(state);
        decode(p); // never write what could not be read back
        Path tmp = Files.createTempFile(folder, "frontier-", ".tmp");
        try {
            try (var out = Files.newOutputStream(tmp)) { p.store(out, "H.O.W.L. Frontier world state, schema " + SCHEMA); }
            try (var channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.exists(file)) Files.copy(file, folder.resolve(PREVIOUS), StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static Properties encode(FrontierState state) {
        Properties p = new Properties();
        p.setProperty("schema", "" + SCHEMA);
        p.setProperty("dismissed.count", "" + state.dismissed.size());
        int d = 0;
        for (UUID id : state.dismissed) p.setProperty("dismissed." + d++, id.toString());
        p.setProperty("settlements", "" + state.settlements.size());
        for (int i = 0; i < state.settlements.size(); i++) {
            var c = state.settlements.get(i);
            String k = "c" + i + ".";
            p.setProperty(k + "id", c.id.toString());
            p.setProperty(k + "owner", c.owner == null ? "" : c.owner.toString());
            p.setProperty(k + "dimension", c.dimension);
            put(p, k + "campfire", c.campfire);
            p.setProperty(k + "peakResidents", "" + c.peakResidents());
            put(p, k + "stockpile", c.stockpile);
            p.setProperty(k + "elapsed", "" + c.elapsed);
            p.setProperty(k + "arrivalTimer", "" + c.arrivalTimer);
            p.setProperty(k + "pioneerCommitted", "" + c.pioneerCommitted);
            p.setProperty(k + "namesIssued", "" + c.namesIssued);
            positions(p, k + "beds", c.beds);
            positions(p, k + "keptBeds", c.keptBeds);
            p.setProperty(k + "settlers", "" + c.settlers.size());
            int n = 0;
            for (var s : c.settlers.values()) {
                String sk = k + "s" + n++ + ".";
                p.setProperty(sk + "id", s.id.toString());
                p.setProperty(sk + "name", s.name);
                p.setProperty(sk + "state", s.state.name());
                p.setProperty(sk + "profession", s.profession.name());
                p.setProperty(sk + "pioneer", "" + s.pioneer);
                put(p, sk + "bed", s.bed);
                put(p, sk + "arrivalPoint", s.arrivalPoint);
                p.setProperty(sk + "hunger", "" + s.hunger);
                p.setProperty(sk + "loyalty", "" + s.loyalty);
                p.setProperty(sk + "hungerTicks", "" + s.hungerTicks);
            }
        }
        return p;
    }

    static FrontierState decode(Properties p) {
        if (!("" + SCHEMA).equals(p.getProperty("schema"))) throw new IllegalArgumentException("unknown schema " + p.getProperty("schema"));
        var state = new FrontierState();
        int dismissed = integer(p, "dismissed.count", 0, Settlement.MAX_RECORDS * 4);
        for (int i = 0; i < dismissed; i++) state.dismissed.add(uuid(p, "dismissed." + i));
        int settlements = integer(p, "settlements", 0, 1);
        Set<UUID> allSettlers = new HashSet<>();
        for (int i = 0; i < settlements; i++) {
            String k = "c" + i + ".";
            String owner = required(p, k + "owner");
            if (!"minecraft:overworld".equals(p.getProperty(k + "dimension"))) throw new IllegalArgumentException("unsupported dimension");
            CodaBlockPos campfire = position(required(p, k + "campfire"));
            if (campfire == null) throw new IllegalArgumentException("settlement without a campfire");
            var c = new Settlement(uuid(p, k + "id"), owner.isEmpty() ? null : UUID.fromString(owner), campfire);
            c.restorePeak(integer(p, k + "peakResidents", 0, Settlement.MAX_POPULATION));
            Territory t = c.territory();
            c.stockpile = inside(t, position(required(p, k + "stockpile")), "stockpile");
            c.elapsed = Long.parseLong(required(p, k + "elapsed"));
            if (c.elapsed < 0) throw new IllegalArgumentException("negative camp time");
            c.arrivalTimer = integer(p, k + "arrivalTimer", 0, Settlement.ARRIVAL_INTERVAL);
            c.pioneerCommitted = bool(p, k + "pioneerCommitted");
            c.namesIssued = integer(p, k + "namesIssued", 0, 1_000_000);
            readPositions(p, k + "beds", t, c.beds);
            readPositions(p, k + "keptBeds", t, c.keptBeds);
            int count = integer(p, k + "settlers", 0, Settlement.MAX_RECORDS);
            Set<CodaBlockPos> claims = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (int n = 0; n < count; n++) {
                String sk = k + "s" + n + ".";
                UUID id = uuid(p, sk + "id");
                if (!allSettlers.add(id)) throw new IllegalArgumentException("duplicate settler " + id);
                String name = required(p, sk + "name");
                if (name.isBlank() || name.length() > 32) throw new IllegalArgumentException("invalid settler name");
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("duplicate settler name " + name);
                var s = new Settler(id, name);
                s.state = Settler.State.valueOf(required(p, sk + "state"));
                s.profession = Settler.Profession.valueOf(required(p, sk + "profession"));
                s.pioneer = bool(p, sk + "pioneer");
                s.bed = inside(t, position(required(p, sk + "bed")), "settler bed");
                s.arrivalPoint = inside(t, position(required(p, sk + "arrivalPoint")), "arrival point");
                if (s.arrivalPoint == null) throw new IllegalArgumentException("settler without an arrival point");
                if (s.bed != null && !s.present()) throw new IllegalArgumentException("a " + s.state + " settler holds a bed");
                if (s.bed != null && !claims.add(s.bed)) throw new IllegalArgumentException("two settlers claim one bed");
                s.hunger = percent(p, sk + "hunger");
                s.loyalty = percent(p, sk + "loyalty");
                s.hungerTicks = integer(p, sk + "hungerTicks", 0, Settler.HUNGER_STEP_TICKS - 1);
                c.settlers.put(id, s);
            }
            if (c.population() > Settlement.MAX_POPULATION) throw new IllegalArgumentException("population above limit");
            if (c.residents() > c.peakResidents()) throw new IllegalArgumentException("more residents than the stored peak");
            state.settlements.add(c);
        }
        return state;
    }

    private static CodaBlockPos inside(Territory t, CodaBlockPos p, String what) {
        if (p != null && !t.contains(p)) throw new IllegalArgumentException(what + " outside the territory");
        return p;
    }
    private static void positions(Properties p, String key, Collection<CodaBlockPos> values) {
        p.setProperty(key + ".count", "" + values.size());
        int i = 0;
        for (var v : values) put(p, key + "." + i++, v);
    }
    private static void readPositions(Properties p, String key, Territory t, Collection<CodaBlockPos> into) {
        int count = integer(p, key + ".count", 0, Settlement.MAX_BEDS);
        for (int i = 0; i < count; i++) {
            var b = inside(t, position(required(p, key + "." + i)), "bed");
            if (b == null) throw new IllegalArgumentException("empty bed entry");
            into.add(b);
        }
    }
    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null) throw new IllegalArgumentException("missing " + key);
        return v;
    }
    private static UUID uuid(Properties p, String key) { return UUID.fromString(required(p, key)); }
    private static boolean bool(Properties p, String key) {
        String v = required(p, key);
        if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException("invalid " + key);
        return v.equals("true");
    }
    private static int integer(Properties p, String key, int min, int max) {
        int n = Integer.parseInt(required(p, key));
        if (n < min || n > max) throw new IllegalArgumentException("invalid " + key);
        return n;
    }
    private static double percent(Properties p, String key) {
        double d = Double.parseDouble(required(p, key));
        if (!Double.isFinite(d) || d < 0 || d > 100) throw new IllegalArgumentException("invalid " + key);
        return d;
    }
    private static void put(Properties p, String key, CodaBlockPos b) {
        p.setProperty(key, b == null ? "" : b.x() + "," + b.y() + "," + b.z());
    }
    static CodaBlockPos position(String value) {
        if (value.isEmpty()) return null;
        String[] v = value.split(",", -1);
        if (v.length != 3) throw new IllegalArgumentException("invalid position " + value);
        var b = new CodaBlockPos(Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]));
        if (Math.abs((long) b.x()) > 29_999_984 || Math.abs((long) b.z()) > 29_999_984 || b.y() < -64 || b.y() > 319)
            throw new IllegalArgumentException("position outside the Overworld: " + value);
        return b;
    }
}
