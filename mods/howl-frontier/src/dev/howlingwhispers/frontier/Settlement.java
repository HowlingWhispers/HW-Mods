package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.util.*;

/** One player's settlement: pure game state, no Minecraft objects. */
final class Settlement {
    static final int MAX_POPULATION = 64, MAX_BEDS = 256, MAX_RECORDS = 4096;
    static final int ARRIVAL_INTERVAL = 6000; // five minutes of loaded camp time
    private static final List<String> NAMES = List.of("Asta", "Bryn", "Edda", "Finn", "Ivar", "Liv", "Mika", "Nils",
            "Runa", "Siv", "Tora", "Vidar", "Alva", "Bjorn", "Dagny", "Eir", "Frode", "Gunn", "Halla", "Ingrid",
            "Jarl", "Ketil", "Lone", "Magne", "Oda", "Ragna", "Solveig", "Trond", "Ulf", "Ylva");

    final UUID id;
    /** Null until a player claims it (until CodaLoader reports who placed the campfire). */
    UUID owner;
    final String dimension = "minecraft:overworld";
    final CodaBlockPos campfire;
    /** Highest number of residents ever present at once. Drives the territory; never decreases. */
    private int peakResidents;
    CodaBlockPos stockpile;
    long elapsed;
    int arrivalTimer;
    boolean pioneerCommitted;
    int namesIssued;
    final LinkedHashMap<UUID, Settler> settlers = new LinkedHashMap<>();
    final LinkedHashSet<CodaBlockPos> beds = new LinkedHashSet<>();
    /** Where miners dig: the first feet-level block of a two-high tunnel, its direction, and blocks cleared. */
    CodaBlockPos mineEntrance;
    String mineDirection = "north";
    int mineProgress;
    /** Grid cells that already carry a road the settlers laid. Roads stay roads. */
    final LinkedHashSet<FrontierPlanner.CellPos> roads = new LinkedHashSet<>();
    /** The building under construction, or null. Fixed when it starts, so a changing plan never moves it. */
    Project project;
    int homesBuilt;

    /** A building in progress: where, which way it faces, in which materials, and how far it has got. */
    static final class Project {
        /** TEARDOWN takes down a house started with the old raised layout before it starts again. */
        enum Phase { ROAD, CLEAR, BUILD, TEARDOWN }
        /** 0.3.x: floor laid on top of the ground. Only read from old saves. */
        static final int RAISED = 1;
        /** 0.4.0+: the top layer of ground is dug out and a foundation is laid in it. */
        static final int SUNK = 2;
        final String blueprint;
        final FrontierPlanner.CellPos cell;
        final String facing;
        final int level;
        final Palette palette;
        final List<FrontierPlanner.CellPos> road;
        Phase phase = Phase.ROAD;
        int step;
        int layout = SUNK;

        Project(String blueprint, FrontierPlanner.CellPos cell, String facing, int level, Palette palette, List<FrontierPlanner.CellPos> road) {
            this.blueprint = blueprint;
            this.cell = cell;
            this.facing = facing;
            this.level = level;
            this.palette = palette;
            this.road = List.copyOf(road);
        }
    }

    /** Bed heads the player keeps for themselves. Settlers never claim these. */
    final LinkedHashSet<CodaBlockPos> keptBeds = new LinkedHashSet<>();

    Settlement(UUID id, UUID owner, CodaBlockPos campfire) {
        this.id = Objects.requireNonNull(id);
        this.owner = owner;
        this.campfire = Objects.requireNonNull(campfire);
    }

    int peakResidents() { return peakResidents; }

    /** Loading only: restores a stored peak. Never lowers it. */
    void restorePeak(int stored) {
        if (stored < 0) throw new IllegalArgumentException("Negative peak");
        peakResidents = Math.max(peakResidents, stored);
    }

    Territory territory() { return Territory.around(campfire, peakResidents); }

    long residents() { return settlers.values().stream().filter(Settler::resident).count(); }
    long population() { return settlers.values().stream().filter(Settler::present).count(); }
    long count(Settler.State state) { return settlers.values().stream().filter(s -> s.state == state).count(); }

    /**
     * Record the current resident count. Returns true when this unlocked a new ring.
     * Duplicates are impossible: settlers are keyed by UUID.
     */
    boolean recordResidents() {
        int before = territory().milestone();
        peakResidents = Math.max(peakResidents, (int) Math.min(Integer.MAX_VALUE, residents()));
        return territory().milestone() > before;
    }

    Set<CodaBlockPos> claimedBeds() {
        Set<CodaBlockPos> claimed = new HashSet<>();
        for (var s : settlers.values()) if (s.present() && s.bed != null) claimed.add(s.bed);
        return claimed;
    }

    List<CodaBlockPos> freeBeds() {
        var claimed = claimedBeds();
        return beds.stream().filter(b -> !keptBeds.contains(b) && !claimed.contains(b) && territory().contains(b)).toList();
    }

    Optional<Settler> settler(UUID id) { return Optional.ofNullable(settlers.get(id)); }

    /** Settlers matching an ID prefix of at least four characters, excluding terminal ones unless asked. */
    List<Settler> matching(String prefix, boolean includeTerminal) {
        if (prefix.length() < 4) return List.of();
        return settlers.values().stream()
                .filter(s -> (includeTerminal || !s.terminal()) && s.id.toString().startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** A name no other settler of this camp has ever used. */
    String nextName() {
        Set<String> used = new HashSet<>();
        for (var s : settlers.values()) used.add(s.name.toLowerCase(Locale.ROOT));
        for (int attempt = 0; attempt < 100_000; attempt++) {
            int n = namesIssued++;
            String base = NAMES.get(n % NAMES.size());
            int round = n / NAMES.size();
            String candidate = round == 0 ? base : base + " " + (round + 1);
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
        throw new IllegalStateException("No settler names left");
    }
}
