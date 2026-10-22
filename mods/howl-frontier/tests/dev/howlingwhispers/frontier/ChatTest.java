package dev.howlingwhispers.frontier;

import static dev.howlingwhispers.frontier.Fixtures.*;

/** The chat style vocabulary: valid codes, readable fallbacks, consistent meaning. */
public final class ChatTest {
    public static void main(String[] args) {
        check(Chat.plain(Chat.heading("Land") + Chat.SEP + Chat.good("ok")).equals("Land | ok"), "Plain text keeps the words and separators");
        check(Chat.wellFormed(Chat.bad("x") + Chat.need(10) + Chat.id("abcd")), "Helpers only produce valid codes");
        check(!Chat.wellFormed("broken § sign"), "A stray section sign is detected");
        check(!Chat.wellFormed("bad §z code"), "An invalid code is detected");
        check(Chat.need(80).startsWith(Chat.GREEN) && Chat.need(40).startsWith(Chat.YELLOW) && Chat.need(10).startsWith(Chat.RED), "Needs go green, yellow, red");
        check(Chat.arrival("Stopped: the campfire is not lit").startsWith(Chat.RED), "Stopped arrivals are red");
        check(Chat.arrival("Pioneer Asta is on the way. Walking").startsWith(Chat.GREEN), "Arrivals on the way are green");
        check(Chat.arrival("Waiting for a bed: build one").startsWith(Chat.YELLOW), "Waiting arrivals are yellow");
        check(Chat.arrival("Pioneer Asta is on the way. Cannot appear: no safe spot").startsWith(Chat.RED), "A settler who cannot appear is red, even if on the way");
        check(Chat.refused("No").equals(Chat.RED + "Frontier: " + Chat.RESET + Chat.WHITE + "No" + Chat.RESET), "Refusals have a red Frontier prefix");
        finish("chat style checks");
    }
}
