package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaSingleplayerWorld;
import java.util.List;

/**
 * PLAYABLE-FIRST, developer-only single-player proof of gameplay.
 *
 * Glass stands in for pipes until native H.O.W.L. block registration exists.
 * /buildcraft pulse x1 y1 z1 x2 y2 z2
 * Transfer is a real native ItemStack mutation, not the virtual pipe model.
 */
public final class BuildCraftGlassPipeDemo {
    private static final int MAX_GLASS_PIPES = 16;
    private static final int ITEMS_PER_PULSE = 16;

    private BuildCraftGlassPipeDemo() {}

    public static void execute(CodaCommandContext player, List<String> args) throws Exception {
        if (args.isEmpty() || (args.size() == 1 && args.get(0).equalsIgnoreCase("help"))) {
            player.reply("BuildCraft developer test: Put single chests/barrels at each end of 1-16 straight "
                    + "GLASS blocks (temporary pipe markers). Put items in the first chest, then run "
                    + "/buildcraft pulse x1 y1 z1 x2 y2 z2 (coordinates of the chests). "
                    + "One command pulse moves up to 16 items. Test in a new throwaway world.");
            return;
        }
        if (args.size() != 7 || !args.get(0).equalsIgnoreCase("pulse"))
            throw new IllegalArgumentException(
                    "Usage: /buildcraft pulse <fromX> <fromY> <fromZ> <toX> <toY> <toZ>");

        int[] coords = new int[6];
        for (int i = 0; i < coords.length; i++) {
            try {
                coords[i] = Integer.parseInt(args.get(i + 1));
            } catch (NumberFormatException badPosition) {
                throw new IllegalArgumentException("Coordinates must be whole block numbers");
            }
        }
        CodaBlockPos from = new CodaBlockPos(coords[0], coords[1], coords[2]);
        CodaBlockPos to = new CodaBlockPos(coords[3], coords[4], coords[5]);

        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        int differingAxes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        long distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (differingAxes != 1 || distance < 2 || distance > MAX_GLASS_PIPES + 1)
            throw new IllegalArgumentException("Use a straight row of 1-16 glass blocks between two chests");

        int stepX = Long.signum(dx);
        int stepY = Long.signum(dy);
        int stepZ = Long.signum(dz);

        CodaSingleplayerWorld world = player.singleplayerWorld();
        if (!world.isLoaded(from) || !world.isLoaded(to))
            throw new IllegalArgumentException("Both chests must be in loaded chunks");
        for (int index = 1; index < distance; index++) {
            CodaBlockPos pipe = new CodaBlockPos(from.x() + index * stepX,
                    from.y() + index * stepY, from.z() + index * stepZ);
            if (!world.isLoaded(pipe) || !world.isBlock(pipe, "minecraft:glass"))
                throw new IllegalArgumentException(
                        "Missing GLASS test pipe at " + pipe.x() + " " + pipe.y() + " " + pipe.z());
        }

        // Only transfer after validating every pipe marker and endpoint.
        int moved = world.transfer(from, to, ITEMS_PER_PULSE);
        if (moved == 0) player.reply("BuildCraft test: No matching item or free destination slot. Nothing moved.");
        else player.reply("BuildCraft test: " + moved + " real items moved chest-to-chest through "
                + (distance - 1) + " temporary glass pipe marker(s).");
    }
}
