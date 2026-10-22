package dev.howlingwhispers.frontier;

import java.util.*;

/**
 * Everything Frontier stores for one world. The format holds several settlements;
 * this release creates at most one per world.
 */
final class FrontierState {
    final List<Settlement> settlements = new ArrayList<>();
    /**
     * Settler entities that must not stay in the world: retired, or dismissed by a reset.
     * Removed when next seen loaded, so an absent settler can never come back as a duplicate.
     */
    final LinkedHashSet<UUID> dismissed = new LinkedHashSet<>();

    Settlement primary() { return settlements.isEmpty() ? null : settlements.getFirst(); }
}
