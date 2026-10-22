package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.FrontierSimulation.text;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** /frontier. Players never need coordinates or full UUIDs: they look at blocks and use short IDs. */
final class FrontierCommands {
    static final int RESET_WINDOW_TICKS = 600; // reset confirm must follow within 30 seconds

    private final FrontierSimulation sim;
    private UUID resetRequestedBy;
    private long resetRequestedAt;

    FrontierCommands(FrontierSimulation sim) { this.sim = sim; }

    void run(CodaCommandContext player, CodaWorldView view, CodaLivingWorld world, List<String> args) throws Exception {
        String op = args.isEmpty() ? "help" : args.getFirst().toLowerCase(Locale.ROOT);
        if (op.equals("help")) { help(player); return; }
        if (op.equals("camp")) { camp(player, world); return; }
        Settlement c = sim.camp();
        if (c == null) throw new IllegalArgumentException("No settlement yet. Place and light a campfire, or look at a lit campfire and use /frontier camp");
        claimIfUnowned(player, c);
        switch (op) {
            case "status" -> status(player, view, world, c);
            case "settlers" -> settlers(player, c, args);
            case "stockpile" -> stockpile(player, world, c);
            case "keepbed" -> keepBed(player, world, c);
            case "name" -> name(player, world, c, args);
            case "retire" -> retire(player, world, c, args);
            case "reset" -> reset(player, c, args);
            default -> throw new IllegalArgumentException("Unknown command. Use /frontier help");
        }
    }

    private void help(CodaCommandContext player) throws Exception {
        player.reply("FRONTIER: place and light a campfire to found your settlement. Your land starts at 3x3 chunks around it and grows by one ring for every 5 residents.");
        player.reply("A pioneer arrives without needing a bed. Later settlers each need a sheltered bed (solid roof 2-6 blocks above) inside your land.");
        player.reply("/frontier status | settlers [all] | stockpile (look at a chest or barrel) | keepbed (look at your own bed)");
        player.reply("/frontier name <id> <MinecraftUsername> | retire <id> | reset | camp (look at a lit campfire)");
    }

    private void camp(CodaCommandContext player, CodaLivingWorld world) throws Exception {
        var target = player.targetBlock().orElseThrow(() -> new IllegalArgumentException("Look at a lit campfire within reach, then use /frontier camp"));
        if (!target.dimension().equals("minecraft:overworld")) throw new IllegalArgumentException("Settlements belong in the Overworld");
        sim.establish(world, target.position(), player.playerId());
        player.reply("Settlement founded and owned by you.");
    }

    /** Until CodaLoader reports who placed the campfire, the first player to use a command inside the land claims it. */
    private void claimIfUnowned(CodaCommandContext player, Settlement c) throws Exception {
        if (c.owner != null || !c.territory().contains(feet(player))) return;
        c.owner = player.playerId();
        try {
            sim.save();
        } catch (Exception failure) {
            c.owner = null;
            throw failure;
        }
        player.reply("You now own this settlement.");
    }

    private void requireOwner(CodaCommandContext player, Settlement c) throws Exception {
        if (c.owner == null) throw new IllegalArgumentException("Nobody owns this settlement yet. Stand inside its land and use any /frontier command to claim it");
        if (!c.owner.equals(player.playerId())) throw new IllegalArgumentException("Only the settlement's owner can do that");
    }

    private void status(CodaCommandContext player, CodaWorldView view, CodaLivingWorld world, Settlement c) throws Exception {
        Territory t = c.territory();
        String owner = c.owner == null ? "nobody yet" : c.owner.equals(player.playerId()) ? "you" : "another player";
        player.reply("Settlement at " + text(c.campfire) + ", owned by " + owner + " | land " + t.describe()
                + " | peak " + c.peakResidents() + " residents, next ring at " + Territory.nextRingAt(c.peakResidents()));
        player.reply("Settlers " + c.population() + "/" + Settlement.MAX_POPULATION + ": " + c.count(RESIDENT) + " resident, "
                + (c.count(COMMITTED) + c.count(ARRIVING)) + " arriving, " + c.count(LEAVING) + " leaving"
                + " | sheltered beds " + c.beds.size() + ", free " + c.freeBeds().size() + (c.keptBeds.isEmpty() ? "" : ", kept for you " + c.keptBeds.size()));
        player.reply("Arrivals: " + sim.arrivalStatus);
        String problem = FrontierSimulation.stockpileProblem(world, c);
        player.reply("Stockpile: " + (problem != null ? problem : text(c.stockpile) + ", " + (sim.hasFood(view, c) ? "has food" : "no food in it")));
        long past = c.count(DEAD) + c.count(DEPARTED) + c.count(RETIRED);
        if (past > 0) player.reply("Past settlers: " + c.count(DEAD) + " dead, " + c.count(DEPARTED) + " departed, " + c.count(RETIRED) + " retired. /frontier settlers all lists them");
        if (sim.lastError != null)
            player.reply("Problem (last seen about " + Math.max(0, (sim.now - sim.lastErrorTick) / 20) + "s ago): " + sim.lastError
                    + (sim.errorCount > 1 ? " (" + sim.errorCount + " failures this session)" : "") + ". The rest of the settlement keeps running");
    }

    private void settlers(CodaCommandContext player, Settlement c, List<String> args) throws Exception {
        boolean all = args.size() == 2 && args.get(1).equalsIgnoreCase("all");
        if (args.size() > 2 || (args.size() == 2 && !all)) throw new IllegalArgumentException("Use /frontier settlers or /frontier settlers all");
        if (c.settlers.isEmpty()) {
            player.reply("No settlers yet. " + sim.arrivalStatus);
            return;
        }
        for (var s : c.settlers.values()) {
            if (!all && s.terminal()) continue;
            player.reply(s.shortId() + " | " + s.name + (s.pioneer ? " (pioneer)" : "") + " | " + s.state + " | " + s.profession
                    + " | food " + (int) s.hunger + " | loyalty " + (int) s.loyalty + " | " + s.activity);
        }
        long past = c.count(DEAD) + c.count(DEPARTED) + c.count(RETIRED);
        if (!all && past > 0)
            player.reply("Not listed: " + c.count(DEAD) + " dead" + names(c, DEAD) + ", " + c.count(DEPARTED) + " departed" + names(c, DEPARTED)
                    + ", " + c.count(RETIRED) + " retired" + names(c, RETIRED) + ". Use /frontier settlers all");
    }

    private void stockpile(CodaCommandContext player, CodaLivingWorld world, Settlement c) throws Exception {
        requireOwner(player, c);
        var p = lookedAt(player, "a chest or barrel");
        if (!c.territory().contains(p)) throw new IllegalArgumentException("The stockpile must be inside your land (" + c.territory().describe() + ")");
        var block = world.block(p).orElseThrow(() -> new IllegalArgumentException("Look at a loaded chest or barrel"));
        if (!FrontierSimulation.CONTAINERS.contains(block.id())) throw new IllegalArgumentException("Look at a single chest or a barrel");
        if (block.id().equals("minecraft:chest") && !block.properties().getOrDefault("type", "single").equals("single"))
            throw new IllegalArgumentException("Use a single chest or a barrel, not a double chest");
        CodaBlockPos before = c.stockpile;
        c.stockpile = p;
        try {
            sim.save();
        } catch (Exception failure) {
            c.stockpile = before;
            throw failure;
        }
        player.reply("Stockpile set. Put food and worker tools in it.");
    }

    private void keepBed(CodaCommandContext player, CodaLivingWorld world, Settlement c) throws Exception {
        requireOwner(player, c);
        var head = FrontierSimulation.bedHead(world, lookedAt(player, "your bed"));
        if (head == null) throw new IllegalArgumentException("Look at a bed, then use /frontier keepbed");
        if (!c.territory().contains(head)) throw new IllegalArgumentException("That bed is outside your land, so settlers will not use it anyway");
        if (c.keptBeds.remove(head)) {
            sim.save();
            player.reply("Settlers may use the bed at " + text(head) + " again.");
            return;
        }
        c.keptBeds.add(head);
        c.beds.remove(head);
        for (var s : c.settlers.values()) if (head.equals(s.bed)) s.bed = null; // they find another bed
        sim.save();
        player.reply("The bed at " + text(head) + " is kept for you. Use /frontier keepbed on it again to share it.");
    }

    private void name(CodaCommandContext player, CodaLivingWorld world, Settlement c, List<String> args) throws Exception {
        requireOwner(player, c);
        if (args.size() != 3) throw new IllegalArgumentException("Use /frontier name <id> <MinecraftUsername>");
        Settler s = one(c, args.get(1), false);
        String next = args.get(2);
        if (!next.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Use a Minecraft username: 3-16 letters, numbers or underscores");
        for (var other : c.settlers.values())
            if (other != s && other.name.equalsIgnoreCase(next)) throw new IllegalArgumentException("Another settler already has that name");
        if (!world.rename(s.id, next)) throw new IllegalArgumentException(s.name + " must be alive and loaded nearby to be renamed");
        String before = s.name;
        s.name = next;
        try {
            sim.save();
        } catch (Exception failure) {
            s.name = before;
            try { world.rename(s.id, before); } catch (Exception undo) { failure.addSuppressed(undo); }
            throw failure;
        }
        player.reply("Renamed to " + next + ". Their skin follows that Minecraft account; unknown accounts use the default skin.");
    }

    /** For settlers who are gone. Refused while they are alive and loaded, so it can never erase a living settler. */
    private void retire(CodaCommandContext player, CodaLivingWorld world, Settlement c, List<String> args) throws Exception {
        requireOwner(player, c);
        if (args.size() != 2) throw new IllegalArgumentException("Use /frontier retire <id>");
        Settler s = one(c, args.get(1), false);
        var actor = world.actor(s.id);
        if (actor.isPresent() && actor.get().health() > 0)
            throw new IllegalArgumentException(s.name + " is alive and loaded nearby, so they are not lost. Retire is only for settlers who are gone");
        var before = s.state;
        var bed = s.bed;
        s.moveTo(RETIRED);
        s.activity = "Retired by the owner";
        sim.state.dismissed.add(s.id);
        try {
            sim.save();
        } catch (Exception failure) {
            s.state = before;
            s.bed = bed;
            sim.state.dismissed.remove(s.id);
            throw failure;
        }
        player.reply(s.name + " is retired. Their bed is free, and if they ever turn up again they will be removed rather than duplicated.");
    }

    private void reset(CodaCommandContext player, Settlement c, List<String> args) throws Exception {
        requireOwner(player, c);
        boolean confirm = args.size() == 2 && args.get(1).equalsIgnoreCase("confirm");
        if (args.size() > 2 || (args.size() == 2 && !confirm)) throw new IllegalArgumentException("Use /frontier reset, then /frontier reset confirm");
        if (!confirm) {
            resetRequestedBy = player.playerId();
            resetRequestedAt = sim.now;
            player.reply("This forgets the settlement and dismisses all " + c.population() + " settlers. Settlers leave with what they carry."
                    + " Your buildings, chests and inventory are not touched. Type /frontier reset confirm within 30 seconds.");
            return;
        }
        if (!player.playerId().equals(resetRequestedBy) || sim.now - resetRequestedAt > RESET_WINDOW_TICKS)
            throw new IllegalArgumentException("Use /frontier reset first, then /frontier reset confirm within 30 seconds");
        resetRequestedBy = null;
        Set<UUID> added = new LinkedHashSet<>();
        for (var s : c.settlers.values()) if (!s.terminal() && sim.state.dismissed.add(s.id)) added.add(s.id);
        sim.state.settlements.remove(c);
        try {
            sim.save();
        } catch (Exception failure) {
            sim.state.settlements.addFirst(c);
            sim.state.dismissed.removeAll(added);
            throw failure;
        }
        sim.arrivalStatus = "No settlement yet: place and light a campfire";
        player.reply("Settlement reset. Place and light a campfire, or look at a lit one and use /frontier camp, to start again.");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static Settler one(Settlement c, String prefix, boolean includeTerminal) {
        var matches = c.matching(prefix, includeTerminal);
        if (matches.size() != 1) throw new IllegalArgumentException("Use a unique ID of at least 4 characters from /frontier settlers");
        return matches.getFirst();
    }

    private static String names(Settlement c, Settler.State state) {
        var all = c.settlers.values().stream().filter(s -> s.state == state).map(s -> s.name).toList();
        if (all.isEmpty()) return "";
        return " (" + String.join(", ", all.subList(0, Math.min(8, all.size()))) + (all.size() > 8 ? " and " + (all.size() - 8) + " more" : "") + ")";
    }

    private static CodaBlockPos lookedAt(CodaCommandContext player, String what) throws Exception {
        var target = player.targetBlock().orElseThrow(() -> new IllegalArgumentException("Look at " + what + " within reach and try again"));
        if (!target.dimension().equals(player.position().dimension())) throw new IllegalArgumentException("Look at " + what + " in this world");
        return target.position();
    }

    private static CodaBlockPos feet(CodaCommandContext player) throws Exception {
        var at = player.position();
        return new CodaBlockPos((int) Math.floor(at.x()), (int) Math.floor(at.y()), (int) Math.floor(at.z()));
    }
}
