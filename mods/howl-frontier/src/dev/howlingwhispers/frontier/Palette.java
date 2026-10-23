package dev.howlingwhispers.frontier;

import java.util.*;

/**
 * The materials a settlement builds with, chosen from what its land offers. Blueprints name
 * roles; a palette turns each role into a real block that the jobs can gather.
 */
record Palette(String name, String frame, String wall, String floor, String roof, String door, String reason) {
    static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak");
    static final int TREES_FOR_WOOD = 4;

    /**
     * A log cabin: every part is logs, which lumberjacks gather directly. Settlers cannot craft
     * planks yet; plank houses come with a crafting hook.
     */
    static Palette wood(String species, String reason) {
        String log = "minecraft:" + species + "_log";
        return new Palette(species.replace('_', ' ') + " log cabin", log, log, log, log, "minecraft:" + species + "_door", reason);
    }

    static Palette sandstone(String reason) {
        return new Palette("sandstone", "minecraft:sandstone", "minecraft:sandstone", "minecraft:sandstone", "minecraft:sandstone", "minecraft:oak_door", reason);
    }

    static Palette terracotta(String wallColour, String reason) {
        return new Palette("terracotta", "minecraft:terracotta", wallColour, "minecraft:terracotta", "minecraft:terracotta", "minecraft:oak_door", reason);
    }

    static Palette stone(String reason) {
        return new Palette("cobblestone", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:cobblestone", "minecraft:oak_door", reason);
    }

    /**
     * Choose from the land. {@code treeLogs} counts natural tree trunks by log id;
     * {@code surface} counts the blocks found at ground level.
     */
    static Palette choose(Map<String, Integer> treeLogs, Map<String, Integer> surface) {
        int trees = treeLogs.values().stream().mapToInt(Integer::intValue).sum();
        if (trees >= TREES_FOR_WOOD) {
            String log = treeLogs.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                    .orElseThrow().getKey();
            String species = log.replace("minecraft:", "").replace("_log", "");
            if (WOODS.contains(species)) return wood(species, trees + " trees in your land");
        }
        int sand = 0, terracotta = 0, total = 0;
        String colour = null;
        int colourCount = 0;
        for (var e : surface.entrySet()) {
            String id = e.getKey();
            int n = e.getValue();
            total += n;
            if (id.equals("minecraft:sand") || id.equals("minecraft:sandstone")) sand += n;
            if (id.equals("minecraft:red_sand") || id.endsWith("terracotta")) terracotta += n;
            if (id.endsWith("_terracotta") && !id.contains("glazed") && n > colourCount) { colour = id; colourCount = n; }
        }
        String few = trees == 0 ? "no trees" : "only " + trees + (trees == 1 ? " tree" : " trees");
        if (total > 0 && terracotta * 3 >= total) return terracotta(colour != null ? colour : "minecraft:terracotta", few + ", terracotta ground");
        if (total > 0 && sand * 3 >= total) return sandstone(few + ", sandy ground");
        return stone(few + " in your land");
    }

    /** The real block state for a blueprint role. Properties only apply to blocks that have them. */
    String resolve(String role, String properties) {
        String id = switch (role) {
            case "frame" -> frame;
            case "wall" -> wall;
            case "floor" -> floor;
            case "roof" -> roof;
            case "door" -> door;
            case "bed" -> "minecraft:red_bed";
            default -> throw new IllegalArgumentException("Unknown role " + role);
        };
        boolean keepProperties = !properties.isEmpty()
                && (role.equals("door") || role.equals("bed") || (role.equals("frame") && id.endsWith("_log")));
        return keepProperties ? id + "[" + properties + "]" : id;
    }

}
