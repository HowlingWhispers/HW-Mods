package dev.howlingwhispers.waypoints;

import dev.howlingwhispers.codaloader.api.CodaCommandContext;
import dev.howlingwhispers.codaloader.api.CodaContext;
import dev.howlingwhispers.codaloader.api.CodaMod;
import dev.howlingwhispers.codaloader.api.CodaPosition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Shared world landmarks using only H.O.W.L.'s existing command/teleport API. */
public final class HwWaypointsMod implements CodaMod {
    private final WaypointStore store = new WaypointStore();
    private record PlayerWorld(Path world, UUID player) {}
    private final Map<PlayerWorld, CodaPosition> returnPoints = new HashMap<>();

    @Override public void onInitialize(CodaContext context) {
        context.registerCommand("wp", "Shared world waypoints: set, go, list, where, back, del", this::execute);
        System.out.println("[HW Waypoints] 0.1.0: /wp registered (commands only, no screen hook).");
    }

    private static String usage() {
        return "Usage: /wp set <name> [note] [--replace], go <name>, list [page], where <name>, back, del <name>";
    }
    private static void require(List<String> args, int count, String help) {
        if (args.size() != count) throw new IllegalArgumentException(help);
    }
    private static PlayerWorld key(CodaCommandContext source) throws Exception {
        return new PlayerWorld(source.worldDirectory().toAbsolutePath().normalize(), source.playerId());
    }
    static double horizontal(CodaPosition from, CodaPosition to) {
        return Math.hypot(to.x() - from.x(), to.z() - from.z());
    }
    static String heading(CodaPosition from, CodaPosition to) {
        double dx = to.x() - from.x(), dz = to.z() - from.z();
        if (dx == 0 && dz == 0) return "HERE";
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int sector = (int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4));
        return names[Math.floorMod(sector, 8)];
    }
    private static String location(CodaPosition current, CodaPosition target) {
        if (!current.dimension().equals(target.dimension())) return "other dimension (" + target.dimension() + ")";
        double y = target.y() - current.y();
        return Math.round(horizontal(current, target)) + " blocks " + heading(current, target)
                + ", height " + (y >= 0 ? "+" : "") + Math.round(y);
    }
    private void execute(CodaCommandContext source, List<String> args) throws Exception {
        if (args.isEmpty()) { source.reply(usage()); return; }
        String sub = args.getFirst().toLowerCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        switch (sub) {
            case "set" -> set(source, rest);
            case "go" -> go(source, rest);
            case "list" -> list(source, rest);
            case "where" -> where(source, rest);
            case "back" -> back(source, rest);
            case "del" -> del(source, rest);
            default -> source.reply(usage());
        }
    }
    private void set(CodaCommandContext source, List<String> args) throws Exception {
        if (args.isEmpty()) throw new IllegalArgumentException("Usage: /wp set <name> [note] [--replace]");
        String name = WaypointStore.name(args.getFirst());
        boolean replace = false;
        List<String> note = new ArrayList<>();
        for (String arg : args.subList(1, args.size())) {
            if (arg.equals("--replace")) replace = true;
            else note.add(arg);
        }
        store.set(source.worldDirectory(), name, source.position(), String.join(" ", note), replace);
        source.reply("Waypoint '" + name + "' saved. Use /wp where " + name + " or /wp go " + name + ".");
    }
    private void go(CodaCommandContext source, List<String> args) throws Exception {
        require(args, 1, "Usage: /wp go <name>");
        String name = WaypointStore.name(args.getFirst());
        WaypointStore.Waypoint found = store.all(source.worldDirectory()).get(name);
        if (found == null) throw new IllegalArgumentException("No waypoint '" + name + "'. Use /wp list.");
        CodaPosition departure = source.position();
        if (!departure.dimension().equals(found.position().dimension()))
            throw new IllegalArgumentException("Waypoint '" + name + "' is in another dimension. Travel there first.");
        source.teleport(found.position()); // The loader performs the authoritative safety check.
        returnPoints.put(key(source), departure); // Do not change on refused teleports.
        source.reply("Arrived at '" + name + "'. Use /wp back to return.");
    }
    private void back(CodaCommandContext source, List<String> args) throws Exception {
        require(args, 0, "Usage: /wp back");
        PlayerWorld id = key(source);
        CodaPosition destination = returnPoints.get(id);
        if (destination == null) throw new IllegalArgumentException("No return point yet. Use /wp go <name> first.");
        CodaPosition departure = source.position();
        if (!departure.dimension().equals(destination.dimension()))
            throw new IllegalArgumentException("Return point is in another dimension. Travel there first.");
        source.teleport(destination);
        returnPoints.put(id, departure); // Repeated /wp back swaps safely.
        source.reply("Returned to your previous position. /wp back can swap again.");
    }
    private void where(CodaCommandContext source, List<String> args) throws Exception {
        require(args, 1, "Usage: /wp where <name>");
        String name = WaypointStore.name(args.getFirst());
        WaypointStore.Waypoint found = store.all(source.worldDirectory()).get(name);
        if (found == null) throw new IllegalArgumentException("No waypoint '" + name + "'. Use /wp list.");
        source.reply("'" + name + "': " + location(source.position(), found.position())
                + (found.note().isEmpty() ? "" : " | " + found.note()) + ". Use /wp go " + name + " to travel.");
    }
    private void list(CodaCommandContext source, List<String> args) throws Exception {
        if (args.size() > 1) throw new IllegalArgumentException("Usage: /wp list [page]");
        int page = 1;
        if (!args.isEmpty()) {
            try { page = Integer.parseInt(args.getFirst()); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Page must be a positive number."); }
            if (page < 1) throw new IllegalArgumentException("Page must be a positive number.");
        }
        var points = store.all(source.worldDirectory());
        if (points.isEmpty()) { source.reply("No world waypoints yet. Stand at a landmark and use /wp set <name>."); return; }
        CodaPosition current = source.position();
        var ordered = new ArrayList<>(points.entrySet());
        ordered.sort(Comparator.<Map.Entry<String, WaypointStore.Waypoint>>comparingInt(e ->
                e.getValue().position().dimension().equals(current.dimension()) ? 0 : 1)
                .thenComparingDouble(e -> e.getValue().position().dimension().equals(current.dimension())
                        ? horizontal(current, e.getValue().position()) : Double.POSITIVE_INFINITY)
                .thenComparing(Map.Entry::getKey));
        int pages = (ordered.size() + 7) / 8;
        if (page > pages) throw new IllegalArgumentException("Only " + pages + " page(s). Use /wp list " + pages + ".");
        StringBuilder reply = new StringBuilder("World waypoints (").append(ordered.size()).append(", page ")
                .append(page).append('/').append(pages).append("): ");
        for (int i = (page - 1) * 8; i < Math.min(page * 8, ordered.size()); i++) {
            var p = ordered.get(i);
            if (i > (page - 1) * 8) reply.append(" | ");
            reply.append(p.getKey()).append(" [").append(location(current, p.getValue().position())).append(']');
        }
        if (page < pages) reply.append(" | Next: /wp list ").append(page + 1);
        source.reply(reply.toString());
    }
    private void del(CodaCommandContext source, List<String> args) throws Exception {
        require(args, 1, "Usage: /wp del <name>");
        String name = WaypointStore.name(args.getFirst());
        if (!store.delete(source.worldDirectory(), name)) throw new IllegalArgumentException("No waypoint '" + name + "'.");
        source.reply("Waypoint '" + name + "' deleted. Use /wp list to see the rest.");
    }
}
