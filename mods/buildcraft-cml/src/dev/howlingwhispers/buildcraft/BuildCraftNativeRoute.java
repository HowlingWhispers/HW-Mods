package dev.howlingwhispers.buildcraft;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;
import dev.howlingwhispers.codaloader.api.CodaWorldView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only survey of ACTUAL placed Minecraft blocks on the server thread.
 *
 * This checks whether a real redstone engine faces a wooden extraction pipe,
 * a real inventory is adjacent to that wooden pipe and cobblestone pipes
 * reach a second inventory. No chests, packets or block entities are mutated.
 * Only already-loaded chunks are inspected. A route is NOT item transport.
 */
public final class BuildCraftNativeRoute {
    private static final String ENGINE = "buildcraftcore:engine_redstone";
    private static final String WOOD = "buildcrafttransport:wood_item";
    private static final String COBBLE = "buildcrafttransport:cobblestone_item";
    private static final int MAX_PIPES = 64;
    private static final int[][] FACES = {
        { 1, 0, 0 }, {-1, 0, 0}, {0, 0, 1},
        { 0, 0,-1 }, { 0, 1, 0}, {0,-1, 0}
    };

    public record Route(CodaBlockPos engine, CodaBlockPos source,
                        List<CodaBlockPos> pipes, CodaBlockPos destination,
                        boolean powered) {
        public Route {
            Objects.requireNonNull(engine);
            Objects.requireNonNull(source);
            Objects.requireNonNull(destination);
            pipes = List.copyOf(pipes);
            if (pipes.size() < 2 || pipes.size() > MAX_PIPES)
                throw new IllegalArgumentException("Route requires wooden and cobble pipes");
        }
    }

    private record Step(CodaBlockPos pos, List<CodaBlockPos> path) {}

    private BuildCraftNativeRoute() {}

    private static CodaBlockPos offset(CodaBlockPos p, int[] face) {
        try {
            CodaBlockPos next = new CodaBlockPos(
                Math.addExact(p.x(), face[0]),
                Math.addExact(p.y(), face[1]),
                Math.addExact(p.z(), face[2]));
            if (Math.abs((long)next.x()) > 30_000_000
                    || Math.abs((long)next.z()) > 30_000_000
                    || Math.abs((long)next.y()) > 4_096) return null;
            return next;
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static boolean loaded(CodaWorldView world, String dimension, CodaBlockPos pos)
            throws Exception {
        return pos != null && world.isChunkLoaded(dimension, pos);
    }

    private static boolean inventory(CodaWorldView world, String dimension, CodaBlockPos pos)
            throws Exception {
        return loaded(world, dimension, pos) && world.inventory(dimension, pos).isPresent();
    }

    public static Optional<Route> inspect(CodaWorldView world, String dimension, CodaBlockPos engine)
            throws Exception {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(engine, "engine");
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid dimension");
        if (!loaded(world, dimension, engine) || !world.isBlock(dimension, engine, ENGINE))
            return Optional.empty();
        boolean powered = world.hasNeighborSignal(dimension, engine);
        for (int[] direction : FACES) {
            CodaBlockPos wood = offset(engine, direction);
            if (!loaded(world, dimension, wood) || !world.isBlock(dimension, wood, WOOD)) continue;
            for (int[] face : FACES) {
                CodaBlockPos source = offset(wood, face);
                if (source == null || source.equals(engine)
                        || !inventory(world, dimension, source)) continue;
                Optional<Route> result = search(world, dimension, engine, source, wood, powered);
                if (result.isPresent()) return result;
            }
        }
        return Optional.empty();
    }

    private static Optional<Route> search(CodaWorldView world, String dimension,
                                           CodaBlockPos engine, CodaBlockPos source,
                                           CodaBlockPos wood, boolean powered) throws Exception {
        ArrayDeque<Step> frontier = new ArrayDeque<>();
        Set<CodaBlockPos> visited = new HashSet<>();
        visited.add(wood);
        frontier.add(new Step(wood, List.of(wood)));
        while (!frontier.isEmpty()) {
            Step step = frontier.removeFirst();
            for (int[] face : FACES) {
                CodaBlockPos next = offset(step.pos(), face);
                if (next == null || next.equals(engine) || next.equals(source)
                        || !loaded(world, dimension, next)) continue;
                // Only cobblestone pipe may follow the extraction pipe.
                if (world.isBlock(dimension, next, COBBLE)) {
                    if (visited.size() >= MAX_PIPES) continue;
                    if (visited.add(next)) {
                        List<CodaBlockPos> path = new ArrayList<>(step.path());
                        path.add(next);
                        frontier.addLast(new Step(next, List.copyOf(path)));
                    }
                } else if (step.path().size() >= 2
                        && inventory(world, dimension, next)) {
                    return Optional.of(new Route(engine, source, step.path(), next, powered));
                }
            }
        }
        return Optional.empty();
    }
}
