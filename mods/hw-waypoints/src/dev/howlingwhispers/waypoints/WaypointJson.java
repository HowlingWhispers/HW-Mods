package dev.howlingwhispers.waypoints;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small dependency-free JSON codec. Strictly parses the subset used by waypoint data. */
final class WaypointJson {
    private WaypointJson() {}

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    static Map<String, Object> parseObject(String source) throws IOException {
        Parser parser = new Parser(source);
        Map<String, Object> object = parser.object();
        parser.space();
        if (parser.index != source.length()) throw new IOException("Trailing JSON data");
        return object;
    }

    private static final class Parser {
        final String source;
        int index;
        Parser(String source) { this.source = source; }
        void space() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }
        boolean take(char expected) {
            space();
            if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }
        void require(char expected) throws IOException {
            if (!take(expected)) throw new IOException("Expected '" + expected + "' at " + index);
        }
        Map<String, Object> object() throws IOException {
            require('{');
            Map<String, Object> result = new LinkedHashMap<>();
            if (take('}')) return result;
            do {
                String key = string();
                require(':');
                Object value = value();
                if (result.putIfAbsent(key, value) != null) throw new IOException("Duplicate JSON field");
                if (take('}')) return result;
                require(',');
            } while (true);
        }
        Object value() throws IOException {
            space();
            if (index >= source.length()) throw new IOException("Unexpected end of JSON");
            char c = source.charAt(index);
            if (c == '{') return object();
            if (c == '"') return string();
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw new IOException("Unsupported JSON value at " + index);
        }
        Number number() throws IOException {
            space();
            int start = index;
            if (source.charAt(index) == '-') index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            try {
                double value = Double.parseDouble(source.substring(start, index));
                if (!Double.isFinite(value)) throw new NumberFormatException();
                return value;
            } catch (NumberFormatException e) {
                throw new IOException("Invalid JSON number", e);
            }
        }
        String string() throws IOException {
            require('"');
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw new IOException("Control character in JSON string");
                if (c != '\\') { out.append(c); continue; }
                if (index >= source.length()) throw new IOException("Broken JSON escape");
                switch (source.charAt(index++)) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw new IOException("Incomplete Unicode escape");
                        try { out.append((char) Integer.parseInt(source.substring(index, index + 4), 16)); }
                        catch (NumberFormatException ex) { throw new IOException("Invalid Unicode escape", ex); }
                        index += 4;
                    }
                    default -> throw new IOException("Invalid JSON escape");
                }
            }
            throw new IOException("Unclosed JSON string");
        }
    }
}
