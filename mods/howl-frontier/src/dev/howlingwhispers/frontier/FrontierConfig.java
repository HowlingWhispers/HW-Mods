package dev.howlingwhispers.frontier;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Player settings in {@code <profile>/config/howl-frontier.properties}. Created with defaults and
 * comments when missing; never overwritten. Unreadable or out-of-range values fall back to the
 * default and are reported once in the log.
 */
record FrontierConfig(double placePerSecond, double breakPerSecond) {
    static final String FILE = "howl-frontier.properties";
    static final double MIN = 0.25, MAX = 8;
    static final FrontierConfig DEFAULTS = new FrontierConfig(1, 1);

    static final String TEMPLATE = """
            # H.O.W.L. Frontier settings. Changes apply the next time a world is opened.
            #
            # How many blocks a settler may place per second while building (0.25 - 8).
            # 1 is the normal pace; 0.5 is one block every two seconds; 4 is four per second.
            place_per_second=1
            #
            # How many blocks a settler may break per second while clearing a site,
            # taking down a hut, mining or felling trees (0.25 - 8).
            break_per_second=1
            """;

    static FrontierConfig load(Path configDirectory) {
        if (configDirectory == null) return DEFAULTS;
        Path file = configDirectory.resolve(FILE);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDirectory);
                Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
                return DEFAULTS;
            }
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > 16 * 1024) {
                System.out.println("[Frontier] " + FILE + " is not a small regular file; using default settings");
                return DEFAULTS;
            }
            Properties p = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(in); }
            return new FrontierConfig(rate(p, "place_per_second"), rate(p, "break_per_second"));
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("[Frontier] Could not read " + FILE + " (" + e.getMessage() + "); using default settings");
            return DEFAULTS;
        }
    }

    private static double rate(Properties p, String key) {
        String text = p.getProperty(key);
        if (text == null) return 1;
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isFinite(v) && v >= MIN && v <= MAX) return v;
        } catch (NumberFormatException ignored) {
        }
        System.out.println("[Frontier] " + FILE + ": " + key + "=" + text.trim() + " is not a number from " + MIN + " to " + MAX + "; using 1");
        return 1;
    }

    String describe() {
        return String.format(Locale.ROOT, "place %s/s, break %s/s", trim(placePerSecond), trim(breakPerSecond));
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
