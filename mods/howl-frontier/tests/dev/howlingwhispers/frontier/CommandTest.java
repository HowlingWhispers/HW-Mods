package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** /frontier through the registered command, as a player would use it. */
public final class CommandTest {
    static CodaCommand command;
    static FrontierMod mod;

    public static void main(String[] args) throws Exception {
        Path profile = Files.createTempDirectory("frontier-profile-");
        Files.createDirectories(profile.resolve("config"));
        Files.writeString(profile.resolve("config/howl-play-mode.txt"), "frontier\n");
        mod = new FrontierMod();
        mod.onInitialize(new CodaContext("0.0.45", "26.4-snapshot-3", profile, profile.resolve("config"), "howl_frontier", List.of("howl_frontier")));
        command = CodaCommands.registrations().stream().filter(r -> r.name().equals("frontier")).findFirst().orElseThrow().command();
        version();
        foundingAndOwnership();
        stockpileAndBeds();
        statusAndSettlers();
        renameAndRetire();
        reset();
        jobsAndMine();
        finish("command checks");
    }

    /** Each scenario gets its own world; the command reloads state when the world directory changes. */
    static Camp world() throws Exception {
        var t = new Camp();
        CodaServerTicks.dispatch(new CodaServerTickContext("cmd-" + t.root.getFileName(), 20, Optional.of(t.view)));
        return t;
    }

    /** The settlement as the registered command currently holds it. */
    static Settlement live() { return mod.simulation().camp(); }

    static void run(Player p, String... args) throws Exception { command.execute(p, List.of(args)); }

    static Settlement stored(Camp t) throws Exception { return new FrontierStore(t.root).load().primary(); }

    static void version() throws Exception {
        String manifest = Files.readString(Path.of("mods/howl-frontier/resources/coda.mod.json"));
        var m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(manifest);
        check(m.find() && FrontierMod.version().equals(m.group(1)), "The startup log reports the manifest version");
    }

    static void foundingAndOwnership() throws Exception {
        var t = world();
        var me = new Player(t.view);
        run(me, "status");
        check(me.last().contains("No settlement yet"), "Status before founding explains what to do");
        me.look(new CodaBlockPos(9, 64, 9));
        run(me, "camp");
        check(me.last().contains("lit campfire"), "Looking at something else is refused");
        me.look(CAMP);
        run(me, "camp");
        check(me.last().contains("owned by you") && me.id.equals(stored(t).owner), "/frontier camp founds a settlement owned by the player");
        run(me, "camp");
        check(me.last().contains("already has"), "A second settlement is refused");

        // A settlement founded by placing a campfire has no owner until someone inside its land claims it.
        var placed = world();
        placed.found();
        // Founded by another simulation instance, so reopen the world for the command's simulation to read it.
        CodaServerTicks.dispatch(new CodaServerTickContext("reopened-" + placed.root.getFileName(), 20, Optional.of(placed.view)));
        var visitor = new Player(placed.view);
        visitor.position = new CodaPosition("minecraft:overworld", 500, 64, 500, 0, 0);
        run(visitor, "status");
        check(!visitor.said("You now own") && stored(placed).owner == null, "A player outside the land does not claim it");
        var claimer = new Player(placed.view);
        run(claimer, "status");
        check(claimer.said("You now own") && claimer.id.equals(stored(placed).owner), "The first player inside the land claims an unowned settlement");
        var stranger = new Player(placed.view);
        stranger.id = new UUID(0, 2);
        var chest = new CodaBlockPos(1, 64, 2);
        placed.world.blocks.put(chest, block("minecraft:chest", Map.of("type", "single"), true));
        stranger.look(chest);
        run(stranger, "stockpile");
        check(stranger.last().contains("Only the settlement's owner"), "Another player cannot change someone else's settlement");
    }

    static void stockpileAndBeds() throws Exception {
        var t = world();
        var me = new Player(t.view);
        me.look(CAMP);
        run(me, "camp");
        var chest = new CodaBlockPos(1, 64, 2);
        t.world.blocks.put(chest, block("minecraft:chest", Map.of("type", "single"), true));
        run(me, "stockpile");
        check(me.last().contains("Look at"), "Stockpile without looking at anything explains how");
        me.look(chest);
        run(me, "stockpile");
        check(me.last().contains("Stockpile set") && chest.equals(stored(t).stockpile), "Looking at a chest sets the stockpile, no coordinates needed");
        var far = new CodaBlockPos(70, 64, 0);
        t.world.blocks.put(far, block("minecraft:barrel", Map.of(), true));
        me.look(far);
        run(me, "stockpile");
        check(me.last().contains("inside your land") && chest.equals(stored(t).stockpile), "A stockpile outside the land is refused and the old one kept");
        var twin = new CodaBlockPos(2, 64, 2);
        t.world.blocks.put(twin, block("minecraft:chest", Map.of("type", "left"), true));
        me.look(twin);
        run(me, "stockpile");
        check(me.last().contains("double chest"), "Double chests are refused");
        var head = t.world.bed(new CodaBlockPos(5, 64, 5));
        var c = live();
        c.beds.add(head);
        var sleeper = new Settler(UUID.randomUUID(), "Sleeper");
        sleeper.state = RESIDENT;
        sleeper.bed = head;
        sleeper.arrivalPoint = new CodaBlockPos(20, 64, 0);
        c.settlers.put(sleeper.id, sleeper);
        c.recordResidents();
        me.look(new CodaBlockPos(4, 64, 5)); // the foot of the bed
        run(me, "keepbed");
        check(me.last().contains("kept for you") && !c.beds.contains(head) && sleeper.bed == null, "keepbed reserves the bed and the settler finds another");
        run(me, "keepbed");
        check(me.last().contains("again") && c.keptBeds.isEmpty(), "keepbed again shares the bed");
    }

    static void statusAndSettlers() throws Exception {
        var t = world();
        var me = new Player(t.view);
        me.look(CAMP);
        run(me, "camp");
        var c = live();
        var chest = new CodaBlockPos(1, 64, 2);
        t.world.blocks.put(chest, block("minecraft:chest", Map.of("type", "single"), true));
        c.stockpile = chest;
        var alive = new Settler(UUID.randomUUID(), "Asta");
        alive.state = RESIDENT;
        alive.pioneer = true;
        alive.arrivalPoint = new CodaBlockPos(20, 64, 0);
        var dead = new Settler(UUID.randomUUID(), "Bryn");
        dead.state = DEAD;
        dead.arrivalPoint = alive.arrivalPoint;
        c.settlers.put(alive.id, alive);
        c.settlers.put(dead.id, dead);
        c.recordResidents();
        me.replies.clear(); me.raw.clear();
        run(me, "status");
        check(me.said("Land: 3x3 chunks (48x48 blocks)") && me.said("next ring at 5"), "Status shows land size and the next ring");
        check(me.said("1 resident"), "Status counts residents");
        check(me.replies.stream().anyMatch(r -> r.startsWith("Arrivals: ")), "Status explains arrivals");
        check(me.said("Stockpile: 1 64 2, has food"), "Status reports the stockpile and food");
        check(me.said("1 dead"), "Status mentions past settlers");
        check(me.raw.stream().allMatch(r -> !r.equals(Chat.plain(r))), "Every status line is styled");
        check(me.raw.stream().anyMatch(r -> r.contains(Chat.GREEN + "1 64 2, has food")), "A stocked stockpile is shown in green");
        check(me.raw.getFirst().startsWith(Chat.GOLD), "Status opens with a gold heading");
        me.replies.clear(); me.raw.clear();
        run(me, "settlers");
        check(me.said("Asta (pioneer)") && !me.said("| Bryn |") && me.last().contains("1 dead (Bryn)"), "settlers lists the living and names the dead");
        me.replies.clear(); me.raw.clear();
        run(me, "settlers", "all");
        check(me.replies.size() == 2 && me.said("DEAD"), "settlers all lists every record");
        check(me.raw.stream().anyMatch(r -> r.contains(Chat.RED + "DEAD")), "Dead settlers are shown in red");
        check(me.raw.stream().anyMatch(r -> r.contains(Chat.DARK_AQUA + alive.shortId())), "Settler IDs are highlighted for typing back");
        run(me, "settlers", "nonsense");
        check(me.last().contains("settlers all"), "A bad argument shows usage");
        run(me, "nonsense");
        check(me.last().contains("/frontier help"), "Unknown commands point to help");
        check(me.raw.getLast().startsWith(Chat.RED + "Frontier: "), "Refusals are red");
    }

    static void renameAndRetire() throws Exception {
        var t = world();
        var me = new Player(t.view);
        me.look(CAMP);
        run(me, "camp");
        var c = live();
        var bed = t.world.bed(new CodaBlockPos(5, 64, 5));
        c.beds.add(bed);
        var a = new Settler(UUID.randomUUID(), "Asta");
        a.state = RESIDENT;
        a.bed = bed;
        a.arrivalPoint = new CodaBlockPos(20, 64, 0);
        var b = new Settler(UUID.randomUUID(), "Bryn");
        b.state = RESIDENT;
        b.arrivalPoint = a.arrivalPoint;
        c.settlers.put(a.id, a);
        c.settlers.put(b.id, b);
        c.recordResidents();
        t.world.put(a.id, bed, 20);
        run(me, "name", a.shortId(), "jeb_");
        check(a.name.equals("jeb_") && t.world.names.get(a.id).equals("jeb_") && stored(t).settler(a.id).orElseThrow().name.equals("jeb_"), "Renaming changes the skin account and is saved");
        run(me, "name", a.shortId(), "bad-name");
        check(me.last().contains("Minecraft username"), "Invalid usernames are refused");
        run(me, "name", a.shortId(), "Bryn");
        check(me.last().contains("already has that name"), "Duplicate names are refused");
        run(me, "name", "ab", "Alex");
        check(me.last().contains("unique ID"), "Too-short IDs are refused");
        run(me, "retire", a.shortId());
        check(me.last().contains("alive and loaded") && a.state == RESIDENT, "Retire refuses a settler who is alive and loaded");
        t.world.actors.remove(a.id); // gone from the world, for whatever reason
        run(me, "retire", a.shortId());
        check(a.state == RETIRED && a.bed == null && c.freeBeds().contains(bed), "Retiring a lost settler frees their bed");
        check(stored(t).settler(a.id).orElseThrow().state == RETIRED, "Retirement is saved");
        check(live() == c && new FrontierStore(t.root).load().dismissed.contains(a.id), "A retired settler is removed if they ever reappear");
    }

    static void reset() throws Exception {
        var t = world();
        var me = new Player(t.view);
        me.look(CAMP);
        run(me, "camp");
        var c = live();
        var s = new Settler(UUID.randomUUID(), "Asta");
        s.state = RESIDENT;
        s.arrivalPoint = new CodaBlockPos(20, 64, 0);
        c.settlers.put(s.id, s);
        c.recordResidents();
        var house = new CodaBlockPos(6, 64, 6);
        t.world.blocks.put(house, block("minecraft:oak_planks", Map.of(), true));
        var blocksBefore = new HashMap<>(t.world.blocks);
        run(me, "reset", "confirm");
        check(me.last().contains("reset first") && stored(t) != null, "Confirm alone does nothing");
        run(me, "reset");
        check(me.last().contains("reset confirm") && stored(t) != null, "Reset asks for confirmation first");
        var stranger = new Player(t.view);
        stranger.id = new UUID(0, 9);
        run(stranger, "reset", "confirm");
        check(stranger.last().contains("Only the settlement's owner") && stored(t) != null, "Nobody else can confirm the owner's reset");
        run(me, "reset", "confirm");
        var after = new FrontierStore(t.root).load();
        check(after.settlements.isEmpty() && after.dismissed.contains(s.id), "Reset forgets the settlement and dismisses its settlers");
        check(t.world.blocks.equals(blocksBefore), "Reset never touches world blocks");
        me.look(CAMP);
        run(me, "camp");
        check(me.last().contains("owned by you") && stored(t) != null && stored(t).settlers.isEmpty(), "A fresh settlement can start straight after a reset");
    }

    static void jobsAndMine() throws Exception {
        var t = world();
        var me = new Player(t.view);
        me.look(CAMP);
        run(me, "camp");
        var c = live();
        var s = new Settler(UUID.randomUUID(), "Asta");
        s.state = RESIDENT;
        s.arrivalPoint = new CodaBlockPos(20, 64, 0);
        c.settlers.put(s.id, s);
        c.recordResidents();
        run(me, "job", s.shortId(), "farmer");
        check(s.profession == Settler.Profession.FARMER && s.jobChosen && stored(t).settler(s.id).orElseThrow().jobChosen, "/frontier job sets and saves a chosen job");
        run(me, "job", s.shortId(), "labourer");
        check(s.profession == Settler.Profession.LABOURER && !s.jobChosen, "labourer lets the settler choose again");
        run(me, "job", s.shortId(), "wizard");
        check(me.last().contains("lumberjack, farmer, miner"), "Unknown jobs are refused with the list");
        me.position = new CodaPosition("minecraft:overworld", 2.5, 64, 2.5, 270, 0); // facing east
        me.look(new CodaBlockPos(6, 64, 0));
        run(me, "mine");
        check(new CodaBlockPos(6, 64, 0).equals(stored(t).mineEntrance) && stored(t).mineDirection.equals("east"), "/frontier mine takes the looked-at block and the facing direction");
        me.look(new CodaBlockPos(70, 64, 0));
        run(me, "mine");
        check(me.last().contains("inside your land") && stored(t).mineEntrance.equals(new CodaBlockPos(6, 64, 0)), "A mine outside the land is refused");
        me.replies.clear(); me.raw.clear();
        run(me, "status");
        check(me.said("Work: 1 labourer") && me.said("mine 6 64 0, tunnel east, 0 blocks dug"), "Status shows jobs and the mine");
        run(me, "mine", "auto");
        check(stored(t).mineEntrance == null && me.last().contains("choose the settlement's mine themselves"), "/frontier mine auto hands the choice back to the miners");
        me.replies.clear(); me.raw.clear();
        run(me, "status");
        check(me.said("miners choose one when they start"), "Status says miners will choose: " + me.replies);
    }
}
