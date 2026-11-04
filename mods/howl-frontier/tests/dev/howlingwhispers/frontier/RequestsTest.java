package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;
import static dev.howlingwhispers.frontier.Fixtures.*;
import static dev.howlingwhispers.frontier.Settler.State.*;

/** 0.4.5: /frontier ask gives one settler requests (craft, come, cancel) that come before their own job. */
public final class RequestsTest {
    static final CodaBlockPos STOCK = new CodaBlockPos(4, 64, 2);

    public static void main(String[] args) throws Exception {
        craftsWhatYouAskFor();
        biggerRecipesNeedATable();
        unknownRecipeIsExplained();
        queueKeepsOrderAndSurvivesReopening();
        comesOverAndGoesBackToWork();
        waitsForTheDayAndCanBeCancelled();
        commandChecks();
        finish("request checks");
    }

    /** A settlement with a stockpile and one resident standing next to it, in daylight. */
    static Camp camp() throws Exception {
        var t = new Camp();
        t.autoBuild = false;
        t.sim.builder.autoStart = false;
        t.found();
        t.world.blocks.put(STOCK, block("minecraft:chest", Map.of("type", "single"), true));
        t.c().stockpile = STOCK;
        t.world.food = 60;
        return t;
    }

    static Settler asta(Camp t) { var s = t.add("Asta", RESIDENT, null, true); t.c().recordResidents(); return s; }

    static void ask(Camp t, Player me, String... args) throws Exception {
        new FrontierCommands(t.sim).run(me, t.view, t.world, List.of(args));
    }

    static void craftsWhatYouAskFor() throws Exception {
        var t = camp();
        var s = asta(t);
        t.world.stock(STOCK, "minecraft:oak_planks", 5);
        var me = new Player(t.view);
        ask(t, me, "ask", "asta", "craft", "stick", "8");
        check(me.last().contains("Asta will craft 8 stick next"), "The settler takes the request: " + me.last());
        check(s.requests.size() == 1, "It is queued");
        t.seconds(5);
        check(t.world.stocked(STOCK, "minecraft:stick") == 8 && t.world.stocked(STOCK, "minecraft:oak_planks") == 1,
                "Two real crafts made 8 sticks from 4 planks, into the stockpile (" + t.world.stocked(STOCK, "minecraft:stick") + " sticks)");
        check(s.requests.isEmpty(), "The request is done and gone");
        check(t.world.announcements.stream().anyMatch(a -> a.contains("Asta made 8 stick for you")), "Players are told it is done: " + t.world.announcements);
        check(t.world.crafted.equals(List.of("minecraft:stick", "minecraft:stick")), "Exactly two crafts, nothing extra");
    }

    static void biggerRecipesNeedATable() throws Exception {
        var t = camp();
        var s = asta(t);
        t.world.stock(STOCK, "minecraft:oak_planks", 8);
        var me = new Player(t.view);
        ask(t, me, "ask", "Asta", "craft", "minecraft:chest");
        t.seconds(3);
        check(s.requests.isEmpty() && t.world.announcements.stream().anyMatch(a -> a.contains("could not craft chest") && a.contains("crafting table")),
                "Without a table, a chest cannot be made and the reason names the table: " + t.world.announcements);
        check(t.world.stocked(STOCK, "minecraft:oak_planks") == 8, "Nothing was used");
        t.world.blocks.put(new CodaBlockPos(STOCK.x(), STOCK.y(), STOCK.z() + 2), block("minecraft:crafting_table", Map.of(), true));
        ask(t, me, "ask", "Asta", "craft", "chest");
        t.seconds(5);
        check(t.world.stocked(STOCK, "minecraft:chest") == 1 && t.world.stocked(STOCK, "minecraft:oak_planks") == 0,
                "With a table by the stockpile, the chest is made from 8 planks: " + s.activity);
    }

    static void unknownRecipeIsExplained() throws Exception {
        var t = camp();
        var s = asta(t);
        var me = new Player(t.view);
        ask(t, me, "ask", "Asta", "craft", "diamond_banana", "2");
        t.seconds(3);
        check(s.requests.isEmpty() && t.world.announcements.stream().anyMatch(a -> a.contains("no crafting recipe called minecraft:diamond_banana")),
                "An unknown recipe ends the request with a clear reason: " + t.world.announcements);
        t.world.stock(STOCK, "minecraft:oak_planks", 2);
        ask(t, me, "ask", "Asta", "craft", "stick", "64");
        t.seconds(4);
        check(s.requests.isEmpty() && t.world.announcements.stream().anyMatch(a -> a.contains("ran out of ingredients after 4")),
                "Running out part-way stops with what was made: " + t.world.announcements);
    }

    static void queueKeepsOrderAndSurvivesReopening() throws Exception {
        var t = camp();
        var s = asta(t);
        var me = new Player(t.view);
        t.world.stock(STOCK, "minecraft:oak_log", 1);
        ask(t, me, "ask", "Asta", "craft", "stick", "4");
        ask(t, me, "ask", "Asta", "craft", "oak_planks", "4");
        check(me.last().contains("after 1 earlier request"), "The second request waits its turn: " + me.last());
        var stored = new FrontierStore(t.root).load().primary().settlers.get(s.id);
        check(stored.requests.size() == 2 && stored.requests.getFirst().item.equals("minecraft:stick"), "Requests are saved in order");
        t.world.clock = 15000; // reopen at night, so nothing is worked on before the check
        t.reopen();
        var again = t.c().settlers.get(s.id);
        check(again.requests.size() == 2, "They survive reopening the world");
        t.world.clock = 6000;
        t.seconds(3);
        // Sticks first: no planks yet, so that request fails; then the log becomes planks.
        check(t.world.crafted.equals(List.of("minecraft:oak_planks")) && t.world.stocked(STOCK, "minecraft:oak_planks") == 4,
                "First in line goes first: the stick request failed for want of planks before the planks were made (" + t.world.crafted + ")");
        check(again.requests.isEmpty(), "Both are done");
        for (int i = 0; i < 5; i++) ask(t, me, "ask", "Asta", "come");
        try { ask(t, me, "ask", "Asta", "come"); throw new AssertionError("A sixth request was accepted"); }
        catch (IllegalArgumentException full) { check(full.getMessage().contains("already has 5 requests"), "At most 5 requests per settler"); }
    }

    static void comesOverAndGoesBackToWork() throws Exception {
        var t = camp();
        var s = asta(t);
        var me = new Player(t.view);
        me.position = new CodaPosition("minecraft:overworld", 14.5, 64, 9.5, 0, 0);
        ask(t, me, "ask", "Asta", "come");
        t.seconds(3);
        var a = t.world.actor(s.id).orElseThrow();
        check(a.distanceSquared(new CodaBlockPos(14, 64, 9)) <= 9, "Asta walks over to where you stood");
        check(s.activity.startsWith("Here for you"), "and waits for you: " + s.activity);
        t.seconds(FrontierRequests.COME_WAIT_SECONDS);
        check(s.requests.isEmpty() && !s.activity.startsWith("Here for you"), "After a minute she goes back to work: " + s.activity);
        // No way there: she gives up and says so.
        t.world.paths = false;
        ask(t, me, "ask", "Asta", "come");
        me.position = new CodaPosition("minecraft:overworld", 30.5, 64, 30.5, 0, 0);
        ask(t, me, "ask", "Asta", "come");
        t.seconds(FrontierRequests.COME_WAIT_SECONDS + FrontierRequests.COME_GIVE_UP_SECONDS + 2);
        check(t.world.announcements.stream().anyMatch(a2 -> a2.contains("Asta could not come to you")), "With no path, she gives up and tells you: " + t.world.announcements);
    }

    static void waitsForTheDayAndCanBeCancelled() throws Exception {
        var t = camp();
        var s = asta(t);
        t.world.stock(STOCK, "minecraft:oak_planks", 4);
        t.world.clock = 15000; // night
        var me = new Player(t.view);
        ask(t, me, "ask", "Asta", "craft", "stick", "4");
        t.seconds(3);
        check(t.world.crafted.isEmpty() && s.requests.size() == 1, "At night the request waits");
        ask(t, me, "ask", "Asta");
        check(me.said("1. craft 4 stick"), "ask <name> lists the queue: " + me.replies);
        ask(t, me, "ask", "Asta", "cancel");
        check(s.requests.isEmpty() && me.last().contains("dropped 1 request"), "cancel clears the queue: " + me.last());
        t.world.clock = 6000;
        t.seconds(3);
        check(t.world.crafted.isEmpty(), "A cancelled request is never done");
    }

    static void commandChecks() throws Exception {
        var t = camp();
        asta(t);
        var me = new Player(t.view);
        for (var bad : List.of(List.of("ask"), List.of("ask", "Nobody", "come"), List.of("ask", "Asta", "dance"),
                List.of("ask", "Asta", "craft"), List.of("ask", "Asta", "craft", "stick", "lots"), List.of("ask", "Asta", "craft", "stick", "65"))) {
            try { ask(t, me, bad.toArray(String[]::new)); throw new AssertionError("Accepted " + bad); }
            catch (IllegalArgumentException refused) { checks++; }
        }
        var arriving = t.add("Bryn", ARRIVING, null, true);
        try { ask(t, me, "ask", "Bryn", "come"); throw new AssertionError("A settler still arriving took a request"); }
        catch (IllegalArgumentException refused) { check(refused.getMessage().contains("not a resident"), "Only residents take requests"); }
        ask(t, me, "ask", arriving.shortId(), "cancel");
        check(me.last().contains("Bryn had no requests"), "Settlers can be named by ID too");
        var other = new Player(t.view);
        other.id = new UUID(0, 2);
        try { ask(t, other, "ask", "Asta", "come"); throw new AssertionError("Someone else's settler took a request"); }
        catch (IllegalArgumentException refused) { check(refused.getMessage().contains("owner"), "Only the owner gives requests"); }
    }
}
