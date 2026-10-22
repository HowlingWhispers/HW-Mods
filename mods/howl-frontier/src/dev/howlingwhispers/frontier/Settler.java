package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import java.util.*;

/** One settler's persistent record. The native entity owns position, health and items. */
final class Settler {
    enum State {
        /** Identity and bed saved; the entity has not been spawned yet. */
        COMMITTED,
        ARRIVING,
        RESIDENT,
        /** Walking out after a food shortage. */
        LEAVING,
        DEAD,
        DEPARTED,
        /** Removed by the player with retire or reset. */
        RETIRED;

        boolean terminal() { return this == DEAD || this == DEPARTED || this == RETIRED; }
        /** States that keep a bed reserved and count toward the camp's population. */
        boolean present() { return this == COMMITTED || this == ARRIVING || this == RESIDENT; }
    }

    enum Profession { LABOURER, LUMBERJACK, MINER, FARMER }

    private static final Map<State, Set<State>> ALLOWED = Map.of(
            State.COMMITTED, EnumSet.of(State.ARRIVING, State.RETIRED),
            State.ARRIVING, EnumSet.of(State.RESIDENT, State.DEAD, State.RETIRED),
            State.RESIDENT, EnumSet.of(State.LEAVING, State.DEAD, State.RETIRED),
            State.LEAVING, EnumSet.of(State.DEPARTED, State.DEAD, State.RETIRED),
            State.DEAD, EnumSet.noneOf(State.class),
            State.DEPARTED, EnumSet.noneOf(State.class),
            State.RETIRED, EnumSet.noneOf(State.class));

    static final int HUNGER_STEP_TICKS = 200; // one point every ten seconds of loaded time
    static final double MEAL = 40, HUNGRY = 60, TOO_HUNGRY_TO_WORK = 20;

    final UUID id;
    String name;
    State state = State.COMMITTED;
    Profession profession = Profession.LABOURER;
    boolean pioneer;
    CodaBlockPos bed;
    /** Where the settler entered the territory, and where they leave from. */
    CodaBlockPos arrivalPoint;
    double hunger = 80, loyalty = 55;
    int hungerTicks;

    // Runtime only: rebuilt each session, never saved.
    String activity = "Arriving";
    int retryTicks;

    Settler(UUID id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    static boolean allowed(State from, State to) { return ALLOWED.get(from).contains(to); }

    /** The only way to change state. Leaving the present states always releases the bed. */
    void moveTo(State next) {
        if (!allowed(state, next)) throw new IllegalStateException(name + " cannot go from " + state + " to " + next);
        state = next;
        if (!next.present()) bed = null;
    }

    boolean terminal() { return state.terminal(); }
    boolean present() { return state.present(); }
    boolean resident() { return state == State.RESIDENT; }
    String shortId() { return id.toString().substring(0, 8); }

    /**
     * Advance hunger by one step of loaded time. A resident whose loyalty is too low
     * for the shortage starts leaving. Loyal residents stay, and can starve.
     * Returns true when this step made the settler leave.
     */
    boolean hungerStep() {
        hunger = Math.max(0, hunger - 1);
        if (hunger < 30 && loyalty < 80) loyalty = Math.max(0, loyalty - .05);
        if (state == State.RESIDENT && ((loyalty < 40 && hunger <= 30) || (loyalty < 80 && hunger <= 10))) {
            moveTo(State.LEAVING);
            activity = "Leaving: not enough food";
            return true;
        }
        return false;
    }

    void meal() {
        hunger = Math.min(100, hunger + MEAL);
        loyalty = Math.min(100, loyalty + .5);
    }
}
