package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.*;
import java.util.*;

/**
 * Things the owner asked one settler to do (0.4.5): craft something with the game's own recipes,
 * or come over. A settler works on their first request before their own job, during the day, and
 * keeps the rest of the queue in order. Requests are saved with the settler, so a reload resumes them.
 * Nothing here bypasses the normal rules: crafting uses real items from the stockpile and, where a
 * recipe needs one, a real crafting table within 3 blocks of it.
 */
final class FrontierRequests {
    static final int MAX_QUEUE = 5, MAX_COUNT = 64;
    /** How long a settler who came over waits for you, and how long they try to find a way before giving up. */
    static final int COME_WAIT_SECONDS = 60, COME_GIVE_UP_SECONDS = 30;
    static final java.util.regex.Pattern ITEM = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    enum Kind { CRAFT, COME }

    /** One request. {@code progress} is items made so far (CRAFT) or seconds waited after arriving (COME). */
    static final class Request {
        final Kind kind;
        final String item;
        final int count;
        final CodaBlockPos to;
        int progress;
        /** Runtime only: seconds without a path while coming over. */
        int blocked;

        private Request(Kind kind, String item, int count, CodaBlockPos to, int progress) {
            this.kind = kind;
            this.item = item;
            this.count = count;
            this.to = to;
            this.progress = progress;
        }

        static Request craft(String item, int count) {
            if (!ITEM.matcher(item).matches()) throw new IllegalArgumentException("Not an item ID: " + item);
            if (count < 1 || count > MAX_COUNT) throw new IllegalArgumentException("Ask for 1 to " + MAX_COUNT + " items at a time");
            return new Request(Kind.CRAFT, item, count, null, 0);
        }

        static Request come(CodaBlockPos to) { return new Request(Kind.COME, null, 0, Objects.requireNonNull(to), 0); }

        String describe() {
            return kind == Kind.CRAFT ? "craft " + count + " " + name(item) + (progress > 0 ? " (" + Math.min(progress, count) + " made)" : "")
                    : "come to " + FrontierSimulation.text(to);
        }

        /** One line in the world file: "craft minecraft:oak_door 3 0" or "come 10,64,-3 0". */
        String save() {
            return kind == Kind.CRAFT ? "craft " + item + " " + count + " " + progress
                    : "come " + to.x() + "," + to.y() + "," + to.z() + " " + progress;
        }

        static Request parse(String line) {
            String[] v = line.split(" ", -1);
            if (v.length == 4 && v[0].equals("craft")) {
                var r = craft(v[1], Integer.parseInt(v[2]));
                r.progress = Math.max(0, Integer.parseInt(v[3]));
                return r;
            }
            if (v.length == 3 && v[0].equals("come")) {
                var to = FrontierStore.position(v[1]);
                if (to == null) throw new IllegalArgumentException("request without a place");
                var r = come(to);
                r.progress = Math.max(0, Math.min(COME_WAIT_SECONDS, Integer.parseInt(v[2])));
                return r;
            }
            throw new IllegalArgumentException("invalid request " + line);
        }
    }

    static String name(String item) {
        return item.replace("minecraft:", "").replace('_', ' ');
    }

    private final FrontierSimulation sim;

    FrontierRequests(FrontierSimulation sim) { this.sim = sim; }

    /** Work on the settler's first request. Returns true when this second went to a request. */
    boolean work(CodaLivingWorld world, CodaWorldView view, Settlement c, Settler s, CodaLivingWorld.Actor actor) throws Exception {
        var r = s.requests.peekFirst();
        if (r == null) return false;
        if (r.kind == Kind.COME) come(world, s, actor, r);
        else craft(world, c, s, actor, r);
        return true;
    }

    private void come(CodaLivingWorld world, Settler s, CodaLivingWorld.Actor actor, Request r) throws Exception {
        if (actor.distanceSquared(r.to) > 9) {
            if (sim.navigate(world, s, r.to, .7)) {
                r.blocked = 0;
                s.activity = "Coming to you at " + FrontierSimulation.text(r.to);
            } else if (++r.blocked >= COME_GIVE_UP_SECONDS) {
                finish(world, s, r, false, "cannot find a way to " + FrontierSimulation.text(r.to));
            } else {
                s.activity = "Coming to you: looking for a way to " + FrontierSimulation.text(r.to);
            }
            return;
        }
        r.progress++;
        s.activity = "Here for you (" + (COME_WAIT_SECONDS - r.progress) + " s, then back to work)";
        if (r.progress >= COME_WAIT_SECONDS) finish(world, s, r, true, null);
    }

    /**
     * One craft per second at the stockpile, with a crafting table when one stands within 3 blocks
     * of it. Everything made goes into the stockpile. Missing ingredients, an unknown recipe or a
     * recipe that needs a table end the request with the reason.
     */
    private void craft(CodaLivingWorld world, Settlement c, Settler s, CodaLivingWorld.Actor actor, Request r) throws Exception {
        if (c.stockpile == null) { finish(world, s, r, false, "there is no stockpile: look at a chest and use /frontier stockpile"); return; }
        var table = FrontierBuilder.craftingTable(world, c);
        var spot = table == null ? null : FrontierBuilder.craftingSpot(world, c, table, actor);
        if (spot == null) table = null; // no room by both: craft what fits the 2x2 grid
        int reach = FrontierBuilder.TABLE_REACH * FrontierBuilder.TABLE_REACH;
        if (actor.distanceSquared(c.stockpile) > reach || (table != null && actor.distanceSquared(table) > reach)) {
            s.activity = "Walking to the stockpile to craft " + name(r.item) + " for you";
            if (table == null ? !sim.moveNear(world, s, c.stockpile) : !sim.navigate(world, s, spot, .65))
                s.activity = "Path blocked: cannot reach the stockpile to craft " + name(r.item);
            return;
        }
        if (!sim.builder.emptyBag(world, c, s)) { s.activity = "The stockpile is full: make room in it (crafting " + name(r.item) + " for you)"; return; }
        int crafted;
        try {
            crafted = world.craft(s.id, table, c.stockpile, r.item, 1);
        } catch (UnsupportedOperationException oldLoader) {
            finish(world, s, r, false, "settlers need H.O.W.L. 0.0.47 or newer to craft");
            return;
        } catch (IllegalArgumentException unknown) {
            finish(world, s, r, false, "there is no crafting recipe called " + r.item + " (smelting and special recipes are not crafted)");
            return;
        }
        int made = world.carried(s.id);
        boolean stored = sim.builder.emptyBag(world, c, s);
        if (crafted == 0) {
            finish(world, s, r, false, r.progress > 0 ? "ran out of ingredients after " + r.progress
                    : table == null ? "the stockpile lacks the ingredients, or it needs a crafting table within 3 blocks of the stockpile"
                    : "the stockpile lacks the ingredients");
            return;
        }
        r.progress += Math.max(1, made);
        sim.save();
        if (r.progress >= r.count) { finish(world, s, r, true, null); return; }
        s.activity = "Crafting " + name(r.item) + " for you (" + r.progress + "/" + r.count + ")" + (stored ? "" : ": the stockpile is full, make room in it");
    }

    private void finish(CodaLivingWorld world, Settler s, Request r, boolean ok, String why) throws Exception {
        s.requests.remove(r);
        sim.save();
        if (r.kind == Kind.COME) {
            if (ok) s.activity = "Back to work";
            else world.announce(Chat.warn(s.name + " could not come to you") + Chat.label(": " + why + "."));
            return;
        }
        if (ok) world.announce(Chat.good(s.name + " made " + r.progress + " " + name(r.item) + " for you.") + Chat.label(" They are in the stockpile."));
        else world.announce(Chat.warn(s.name + " could not craft " + name(r.item)) + Chat.label(": " + why + "."));
        s.activity = ok ? "Finished crafting " + name(r.item) : "Could not craft " + name(r.item);
    }
}
