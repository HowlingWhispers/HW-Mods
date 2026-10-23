package dev.howlingwhispers.frontier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A building as text: one grid per layer, letters mapped to roles. See
 * resources/frontier/blueprints/starter_hut.blueprint for the format.
 * The front (rows' last line, south) is where the door is and faces the road.
 */
final class Blueprint {
    /** One position: its role ("air", "keep", or a palette role) and block properties. */
    record Cell(int x, int y, int z, String role, String properties) {
        boolean solid() { return !role.equals("air") && !role.equals("keep"); }
        /** The upper half of a door and the head of a bed come from the same item as their other half. */
        boolean secondHalf() {
            return (role.equals("door") && properties.contains("half=upper")) || (role.equals("bed") && properties.contains("part=head"));
        }
    }

    static final Set<String> ROLES = Set.of("air", "keep", "frame", "wall", "floor", "roof", "door", "bed");

    final String name;
    final int width, height, depth;
    final int anchorX, anchorY, anchorZ;
    final List<Cell> cells;

    private Blueprint(String name, int[] size, int[] anchor, List<Cell> cells) {
        this.name = name;
        width = size[0];
        height = size[1];
        depth = size[2];
        anchorX = anchor[0];
        anchorY = anchor[1];
        anchorZ = anchor[2];
        this.cells = List.copyOf(cells);
    }

    static Blueprint resource(String id) throws IOException {
        if (!id.matches("[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Invalid blueprint id " + id);
        try (InputStream in = Blueprint.class.getResourceAsStream("/frontier/blueprints/" + id + ".blueprint")) {
            if (in == null) throw new FileNotFoundException("No blueprint " + id);
            return parse(new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8));
        }
    }

    static Blueprint parse(String text) {
        String name = null;
        int[] size = null, anchor = null;
        Map<Character, String[]> legend = new HashMap<>();
        List<List<String>> layers = new ArrayList<>();
        String section = "";
        int lineNumber = 0;
        for (String raw : text.split("\n", -1)) {
            lineNumber++;
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.stripTrailing();
            if (line.isBlank()) continue;
            String trimmed = line.strip();
            try {
                if (trimmed.startsWith("name:")) name = trimmed.substring(5).strip();
                else if (trimmed.startsWith("size:")) size = numbers(trimmed.substring(5), 3);
                else if (trimmed.startsWith("anchor:")) anchor = numbers(trimmed.substring(7), 3);
                else if (trimmed.equals("legend:")) section = "legend";
                else if (trimmed.startsWith("layer")) { section = "layer"; layers.add(new ArrayList<>()); }
                else if (section.equals("legend")) {
                    String[] parts = trimmed.split("\\s+", 2);
                    if (parts[0].length() != 1 || parts.length != 2) throw new IllegalArgumentException("legend lines are: <letter> <role>[properties]");
                    String value = parts[1].strip(), props = "";
                    int bracket = value.indexOf('[');
                    if (bracket >= 0) {
                        if (!value.endsWith("]")) throw new IllegalArgumentException("unclosed [ in " + value);
                        props = value.substring(bracket + 1, value.length() - 1);
                        value = value.substring(0, bracket);
                    }
                    if (!ROLES.contains(value)) throw new IllegalArgumentException("unknown role " + value);
                    if (!props.isEmpty() && !props.matches("[a-z_]+=[a-z0-9_]+(,[a-z_]+=[a-z0-9_]+)*")) throw new IllegalArgumentException("bad properties " + props);
                    if (legend.put(parts[0].charAt(0), new String[]{value, props}) != null) throw new IllegalArgumentException("letter " + parts[0] + " defined twice");
                } else if (section.equals("layer")) layers.getLast().add(trimmed);
                else throw new IllegalArgumentException("unexpected line");
            } catch (RuntimeException bad) {
                throw new IllegalArgumentException("Blueprint line " + lineNumber + ": " + bad.getMessage(), bad);
            }
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Blueprint has no name");
        if (size == null || anchor == null) throw new IllegalArgumentException("Blueprint needs size and anchor");
        int w = size[0], h = size[1], d = size[2];
        if (w < 1 || h < 1 || d < 1 || w > 16 || h > 16 || d > 16) throw new IllegalArgumentException("Blueprint size must be 1-16 on each side");
        if (layers.size() != h) throw new IllegalArgumentException("Expected " + h + " layers, found " + layers.size());
        if (anchor[0] < 0 || anchor[0] >= w || anchor[1] < 0 || anchor[1] >= h || anchor[2] < 0 || anchor[2] >= d)
            throw new IllegalArgumentException("Anchor is outside the blueprint");
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            var rows = layers.get(y);
            if (rows.size() != d) throw new IllegalArgumentException("Layer " + y + " has " + rows.size() + " rows, expected " + d);
            for (int z = 0; z < d; z++) {
                String row = rows.get(z);
                if (row.length() != w) throw new IllegalArgumentException("Layer " + y + " row " + z + " is " + row.length() + " wide, expected " + w);
                for (int x = 0; x < w; x++) {
                    var entry = legend.get(row.charAt(x));
                    if (entry == null) throw new IllegalArgumentException("Layer " + y + ": '" + row.charAt(x) + "' is not in the legend");
                    cells.add(new Cell(x, y, z, entry[0], entry[1]));
                }
            }
        }
        return new Blueprint(name, size, anchor, cells);
    }

    private static int[] numbers(String text, int count) {
        String[] parts = text.strip().split("\\s+");
        if (parts.length != count) throw new IllegalArgumentException("expected " + count + " numbers");
        int[] n = new int[count];
        for (int i = 0; i < count; i++) n[i] = Integer.parseInt(parts[i]);
        return n;
    }

    /** Items needed to build this in a palette, sorted by item id. */
    SortedMap<String, Integer> materials(Palette palette) {
        SortedMap<String, Integer> items = new TreeMap<>();
        for (var c : cells) {
            if (!c.solid() || c.secondHalf()) continue;
            String state = palette.resolve(c.role(), c.properties());
            int bracket = state.indexOf('[');
            items.merge(bracket < 0 ? state : state.substring(0, bracket), 1, Integer::sum);
        }
        return items;
    }

}
