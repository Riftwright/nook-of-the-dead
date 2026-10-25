package dev.nookofthedead.world;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Where the research institutes stand (the Vandal's: {@link dev.nookofthedead.entity.Vandal}): rare - in some of the
 * region cells ({@link Region#SIZE} blocks of the grid square), one block of the town or of a settlement holds one, the
 * whole block its grounds. Found by the map the scraps make ({@link dev.nookofthedead.item.ScrawledMapItem}).
 */
public final class Institute {
    /** Of the region cells, how many in a hundred have one. */
    public static final int CHANCE = 45;

    /** Whether block ({@code bx}, {@code bz}) of the grid is an institute's. */
    public static boolean at(long seed, int bx, int bz) {
        long[] site = site(seed, Math.floorDiv(bx, Region.SIZE), Math.floorDiv(bz, Region.SIZE));
        // none where the second institute stands: its lot is never laid there (the review, 2026-10-19)
        return site != null && site[0] == bx && site[1] == bz && !Ascent.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK)
                && !Capital.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK, 8);
    }

    /** The block of cell ({@code qx}, {@code qz}) that has the cell's institute, if it has one: {bx, bz}. */
    public static long @Nullable [] site(long seed, int qx, int qz) {
        long h = Town.hash(seed, qx, qz, 120);
        if (Math.floorMod(h, 100) >= CHANCE) {
            return null;
        }
        int bx = qx * Region.SIZE + 1 + (int) Math.floorMod(h >> 8, (long) Region.SIZE - 2);
        int bz = qz * Region.SIZE + 1 + (int) Math.floorMod(h >> 20, (long) Region.SIZE - 2);
        if (Math.abs(bx) <= Region.HOME + 1 && Math.abs(bz) <= Region.HOME + 1) {
            return null;                                                  // never where one comes in
        }
        Region region = Region.of(seed, bx, bz);
        if (region != Region.CITY && region != Region.SETTLEMENT) {
            return null;
        }
        return new long[] {bx, bz};
    }

    /** The middle of the nearest institute's block to ({@code x}, {@code z}), within {@code cells} region cells; null if none. */
    public static @Nullable BlockPos nearest(long seed, int x, int z, int cells) {
        int qx0 = Math.floorDiv(Math.floorDiv(x, Town.BLOCK), Region.SIZE);
        int qz0 = Math.floorDiv(Math.floorDiv(z, Town.BLOCK), Region.SIZE);
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (int r = 0; r <= cells; r++) {
            for (int qx = qx0 - r; qx <= qx0 + r; qx++) {
                for (int qz = qz0 - r; qz <= qz0 + r; qz++) {
                    if (Math.max(Math.abs(qx - qx0), Math.abs(qz - qz0)) != r) {
                        continue;
                    }
                    long[] site = site(seed, qx, qz);
                    if (site == null) {
                        continue;
                    }
                    int cx = (int) site[0] * Town.BLOCK + Town.BLOCK / 2;
                    int cz = (int) site[1] * Town.BLOCK + Town.BLOCK / 2;
                    double d = (double) (cx - x) * (cx - x) + (double) (cz - z) * (cz - z);
                    if (d < nearest) {
                        nearest = d;
                        best = new BlockPos(cx, Town.SURFACE, cz);
                    }
                }
            }
            if (best != null && r >= 1) {
                return best;                                              // a ring further out is never nearer than this by much
            }
        }
        return best;
    }

    private Institute() {}
}
