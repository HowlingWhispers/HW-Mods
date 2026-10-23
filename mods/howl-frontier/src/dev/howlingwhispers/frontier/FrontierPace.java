package dev.howlingwhispers.frontier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How many blocks each settler may place and break this second, from {@link FrontierConfig}.
 * Each second adds the configured rate, up to one second's worth (at least one action), so a
 * pause never turns into a burst. At the default rate of 1 this is exactly one action a second.
 */
final class FrontierPace {
    private FrontierConfig config = FrontierConfig.DEFAULTS;
    private final Map<UUID, double[]> credit = new HashMap<>();

    void configure(FrontierConfig config) {
        this.config = config;
        credit.clear();
    }

    FrontierConfig config() { return config; }

    /** Called once a second for each working settler, before they act. */
    void second(UUID id) {
        double[] c = credit.computeIfAbsent(id, k -> new double[2]);
        c[0] = Math.min(c[0] + config.placePerSecond(), Math.max(1, config.placePerSecond()));
        c[1] = Math.min(c[1] + config.breakPerSecond(), Math.max(1, config.breakPerSecond()));
    }

    /** True if a block may be placed (place) or broken now; uses up one action. */
    boolean take(UUID id, boolean place) {
        double[] c = credit.get(id);
        if (c == null) return true; // not paced (for example tests that drive a job directly)
        int i = place ? 0 : 1;
        if (c[i] < 1 - 1e-9) return false;
        c[i] -= 1;
        return true;
    }

    /** True if another action of that kind is allowed this second. */
    boolean more(UUID id, boolean place) {
        double[] c = credit.get(id);
        return c != null && c[place ? 0 : 1] >= 1 - 1e-9;
    }

    void clear() { credit.clear(); }
}
