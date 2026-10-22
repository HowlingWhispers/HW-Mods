package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;

/**
 * A square of chunks centred on the campfire's chunk. The single spatial rule of
 * Frontier: everything a settlement does must lie inside it.
 *
 * milestone = floor(peakResidents / 5); side = 3 + 2 * milestone chunks.
 */
record Territory(ChunkPos centre, int milestone) {
    static final int RESIDENTS_PER_RING = 5;
    /** 65x65 chunks. Far beyond any planned population; guards against corrupt numbers. */
    static final int MAX_MILESTONE = 31;

    Territory {
        if (centre == null) throw new IllegalArgumentException("Territory needs a centre chunk");
        if (milestone < 0 || milestone > MAX_MILESTONE) throw new IllegalArgumentException("Invalid territory milestone " + milestone);
    }

    static Territory around(CodaBlockPos campfire, int peakResidents) {
        return new Territory(ChunkPos.of(campfire), milestoneFor(peakResidents));
    }

    static int milestoneFor(int peakResidents) {
        if (peakResidents < 0) throw new IllegalArgumentException("Negative resident count");
        return Math.min(MAX_MILESTONE, peakResidents / RESIDENTS_PER_RING);
    }

    /** Chunks from the centre chunk to the edge, inclusive. */
    int reach() { return 1 + milestone; }
    int side() { return 3 + 2 * milestone; }
    int chunkCount() { return side() * side(); }
    int sideBlocks() { return side() * 16; }

    boolean contains(ChunkPos c) {
        return Math.abs((long) c.x() - centre.x()) <= reach() && Math.abs((long) c.z() - centre.z()) <= reach();
    }

    boolean contains(CodaBlockPos p) { return contains(ChunkPos.of(p)); }

    int minBlockX() { return (centre.x() - reach()) * 16; }
    int maxBlockX() { return (centre.x() + reach()) * 16 + 15; }
    int minBlockZ() { return (centre.z() - reach()) * 16; }
    int maxBlockZ() { return (centre.z() + reach()) * 16 + 15; }

    /** Territory only ever grows. A lower milestone returns this territory unchanged. */
    Territory atLeast(int otherMilestone) {
        return otherMilestone <= milestone ? this : new Territory(centre, Math.min(MAX_MILESTONE, otherMilestone));
    }

    /** Residents needed for the next ring, from the stored peak. */
    static int nextRingAt(int peakResidents) {
        return (milestoneFor(peakResidents) + 1) * RESIDENTS_PER_RING;
    }

    String describe() {
        return side() + "x" + side() + " chunks (" + sideBlocks() + "x" + sideBlocks() + " blocks)";
    }
}
