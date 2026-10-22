package dev.howlingwhispers.frontier;

import dev.howlingwhispers.codaloader.api.CodaBlockPos;

/** A chunk column. Floor division, so block x = -1 lies in chunk -1, not chunk 0. */
record ChunkPos(int x, int z) {
    static ChunkPos of(CodaBlockPos p) {
        return new ChunkPos(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16));
    }

    int minBlockX() { return x * 16; }
    int minBlockZ() { return z * 16; }
}
