package dev.howlingwhispers.frontier;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Frontier's chat style. Minecraft renders legacy formatting codes in literal chat text,
 * so no loader change is needed. Colours carry meaning, used the same way everywhere:
 *
 * <ul>
 *   <li>gold: headings and the Frontier name</li>
 *   <li>gray: labels; white: values; dark gray: separators</li>
 *   <li>green: good or done; yellow: waiting or needs attention; red: stopped, failed, refused</li>
 *   <li>dark aqua: settler IDs you type back into commands</li>
 * </ul>
 *
 * Meaning never depends on colour alone: the words still say it.
 */
final class Chat {
    private static final String S = "§";
    static final String RESET = S + "r", BOLD = S + "l";
    static final String GOLD = S + "6", GRAY = S + "7", DARK_GRAY = S + "8", WHITE = S + "f";
    static final String GREEN = S + "a", YELLOW = S + "e", RED = S + "c", DARK_AQUA = S + "3";
    static final String SEP = " " + DARK_GRAY + "|" + RESET + " ";
    private static final Pattern CODES = Pattern.compile(S + "[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);

    private Chat() {}

    static String heading(String text) { return GOLD + BOLD + text + RESET; }
    static String label(String text) { return GRAY + text + RESET; }
    static String value(Object text) { return WHITE + text + RESET; }
    static String good(Object text) { return GREEN + text + RESET; }
    static String warn(Object text) { return YELLOW + text + RESET; }
    static String bad(Object text) { return RED + text + RESET; }
    static String id(String text) { return DARK_AQUA + text + RESET; }
    static String muted(Object text) { return DARK_GRAY + text + RESET; }

    /** "Label: value" with the value in its own colour. */
    static String field(String label, String coloredValue) { return label(label + ": ") + coloredValue; }

    /** Command refusals and errors. */
    static String refused(String message) { return RED + "Frontier: " + RESET + WHITE + message + RESET; }
    /** Confirmations of something the player just did. */
    static String done(String message) { return GREEN + message + RESET; }

    /** A need on a 0-100 scale: green when comfortable, yellow when low, red when critical. */
    static String need(double value) {
        int v = (int) value;
        return v >= 60 ? good(v) : v >= 25 ? warn(v) : bad(v);
    }

    /** Arrival explanations are written as plain sentences; their opening word sets the tone. */
    static String arrival(String status) {
        String s = status.toLowerCase(Locale.ROOT);
        if (s.startsWith("stopped") || s.contains("cannot appear")) return bad(status);
        if (s.contains("on the way")) return good(status);
        return warn(status);
    }

    /** Text without formatting codes, for logs and tests. */
    static String plain(String text) { return CODES.matcher(text).replaceAll(""); }

    /** True when every formatting code is complete and valid (no stray section signs). */
    static boolean wellFormed(String text) {
        return !CODES.matcher(text).replaceAll("").contains(S);
    }
}
