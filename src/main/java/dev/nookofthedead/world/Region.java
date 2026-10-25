package dev.nookofthedead.world;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * The fallen world's biomes (the user, 2026-10-15, as proposed in PROPOSAL_1.0.md): regions some five hundred blocks
 * across, each of whole blocks of the town's grid, so a region ends along a street. Each is a biome of its own - its name on
 * the debug screen, its own grass, sky and water - and has its own ground, buildings and number of the dead; the dead are
 * everywhere all the same (the user: "どこにいっても大量のゾンビがいて、ずっと追いかけられている、安息地を探すのがすごく大変").
 * <ul>
 * <li>{@link #CITY}: the town, fallen not long ago - packed close, the main streets wide and winding, dead ends and lanes;
 * its own quarters inside it ({@link District}). The most of the dead.</li>
 * <li>{@link #WILDS}: away from the town - woods and grass, rocks standing up out of them; in some a cave where people
 * hid and the infection got in, full of the dead. Few outside.</li>
 * <li>{@link #SETTLEMENT}: where players lived - machines, store rooms and buildings made with care, close together.</li>
 * <li>{@link #FARMLAND}: wide fields gone to tall grass, the odd big farmhouse standing alone; the dead hard to see in it.</li>
 * <li>{@link #DESERT} (the user, 2026-10-18: "砂漠バイオーム追加しない？…映画のバイオ3みたいなイメージ"): a town the sand took -
 * its streets under the dunes, its buildings worn down to their footings, sand drifted into them; desert houses of the
 * game's own, pyramids still standing or already dug out, what players left; the mutated husks in numbers, the infected
 * camels and scorpions ({@link Desert}). Some of what would be the wilds or the farmland: the rest of the world stays laid
 * out as it was.</li>
 * </ul>
 * Small lakes and rocky hills come now and then in the wilds and the farmland, never big ({@link Terrain}).
 */
public enum Region {
    CITY("fallen_town", 1.0, 45),
    WILDS("fallen_wilds", 0.4, 20),
    SETTLEMENT("fallen_settlement", 0.9, 13),
    FARMLAND("fallen_farmland", 0.7, 22),
    /** Never rolled for itself: a share of the wilds' and the farmland's cells ({@link #DESERTS}), so nothing else moves. */
    DESERT("fallen_desert", 0.8, 0),
    /**
     * Never rolled: the one capital ({@link Capital}), wherever it stands over whatever region was there - its walled
     * city, the moat and the ground outside (2026-10-24). Asked of a column ({@link #at}), never of a block ({@link #of}).
     */
    CAPITAL("fallen_capital", 1.25, 0);

    /** Of the cells rolled wilds or farmland, how many in a hundred are desert instead. */
    public static final int DESERTS = 40;

    public final ResourceKey<Biome> biome;
    /** How many of the dead are about here, of the town's number ({@link dev.nookofthedead.entity.Horde}). */
    public final double dead;
    final int weight;

    Region(String biome, double dead, int weight) {
        this.biome = ResourceKey.create(Registries.BIOME, NookOfTheDead.id(biome));
        this.dead = dead;
        this.weight = weight;
    }

    /** Region centres sit one to a square of this many grid blocks, somewhere in it. */
    public static final int SIZE = 8;
    /** Within this many grid blocks of the world's middle (where one comes in), always the town. */
    public static final int HOME = 2;

    /** The region block ({@code bx}, {@code bz}) of the town's grid is in, for a world of {@code seed}. */
    public static Region of(long seed, int bx, int bz) {
        if (Math.abs(bx) <= HOME && Math.abs(bz) <= HOME) {
            return CITY;
        }
        return KINDS[KNOWN.get(seed, bx, bz, () -> {
            long[] nearest = nearest(seed, bx + 0.5, bz + 0.5);
            return kind(seed, nearest[0], nearest[1]).ordinal();
        })];
    }

    private static final Region[] KINDS = values();
    /** What {@link #of} has worked out: it is asked for the same few blocks over and over. */
    private static final BlockCache KNOWN = new BlockCache(14);
    /** Which of a block's neighbours within {@link #DEEP} are of another region ({@link #inside}), a bit each. */
    private static final BlockCache BORDERS = new BlockCache(13);

    /**
     * How far ({@code x}, {@code z}) (blocks of the world) is inside its region, in blocks, up to {@link #DEEP}: 0 at its
     * edge, growing inward - the hills of the wilds come up from nothing at its edge ({@link Terrain}), so a region's
     * border is never a cliff.
     */
    public static double inside(long seed, int x, int z) {
        int bx = Math.floorDiv(x, Town.BLOCK);
        int bz = Math.floorDiv(z, Town.BLOCK);
        int borders = BORDERS.get(seed, bx, bz, () -> {
            Region mine = of(seed, bx, bz);
            int bits = 0;
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    if (of(seed, bx + dx, bz + dz) != mine) {
                        bits |= 1 << bit(dx, dz);
                    }
                }
            }
            return bits;
        });
        if (borders == 0) {
            return DEEP;                                                 // the most of the land: nothing else near
        }
        double best = DEEP;
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                if ((borders >> bit(dx, dz) & 1) != 0) {
                    int x0 = (bx + dx) * Town.BLOCK;
                    int z0 = (bz + dz) * Town.BLOCK;
                    double ox = Math.max(0, Math.max(x0 - x, x - (x0 + Town.BLOCK - 1)));
                    double oz = Math.max(0, Math.max(z0 - z, z - (z0 + Town.BLOCK - 1)));
                    best = Math.min(best, Math.hypot(ox, oz));
                }
            }
        }
        return best;
    }

    /** How far in {@link #inside} looks: past it, as deep as it gets. */
    public static final double DEEP = 120.0;
    private static final int REACH = (int) Math.ceil(DEEP / Town.BLOCK);

    private static int bit(int dx, int dz) {
        return (dx + REACH) * (2 * REACH + 1) + dz + REACH;
    }

    /** The nearest region centre's cell to ({@code gx}, {@code gz}) in grid blocks: {qx, qz}. */
    private static long[] nearest(long seed, double gx, double gz) {
        int qx = Math.floorDiv((int) Math.floor(gx), SIZE);
        int qz = Math.floorDiv((int) Math.floor(gz), SIZE);
        double best = Double.MAX_VALUE;
        long[] found = {qx, qz};
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                double[] c = centre(seed, qx + dx, qz + dz);
                double d = (gx - c[0]) * (gx - c[0]) + (gz - c[1]) * (gz - c[1]);
                if (d < best) {
                    best = d;
                    found = new long[] {qx + dx, qz + dz};
                }
            }
        }
        return found;
    }

    /** Where in its square cell ({@code qx}, {@code qz}) a region's centre is, in grid blocks. */
    private static double[] centre(long seed, long qx, long qz) {
        long h = Town.hash(seed, qx, qz, 80);
        return new double[] {qx * SIZE + Math.floorMod(h, 1000L) / 1000.0 * SIZE, qz * SIZE + Math.floorMod(h >> 12, 1000L) / 1000.0 * SIZE};
    }

    /** What kind the region of cell ({@code qx}, {@code qz}) is: rolled by weight, a share of the country then desert. */
    private static Region kind(long seed, long qx, long qz) {
        Region rolled = rolled(seed, qx, qz);
        if ((rolled == WILDS || rolled == FARMLAND) && Math.floorMod(Town.hash(seed, qx, qz, 82) >> 8, 100L) < DESERTS) {
            return DESERT;
        }
        return rolled;
    }

    private static Region rolled(long seed, long qx, long qz) {
        int total = 0;
        for (Region r : values()) {
            total += r.weight;
        }
        int roll = (int) Math.floorMod(Town.hash(seed, qx, qz, 81) >> 8, (long) total);
        for (Region r : values()) {
            roll -= r.weight;
            if (roll < 0) {
                return r;
            }
        }
        return CITY;
    }

    /**
     * How far the regions' look - their biome's colours, the desert's sand, the country's growth - wanders off the grid's
     * straight lines (the user, 2026-10-25, of the desert's edge: "まっすぐな砂の線"). What is built where, and how many of
     * the dead, keep to the blocks ({@link #at}).
     */
    public static final double WANDER = 22.0;

    /** ({@code x}, {@code z}) moved by the wander: {x, z} of the point whose region it looks like. */
    public static int[] wandered(long seed, int x, int z) {
        return new int[] {x + (int) Math.round((Town.noise(seed, x, z, 70.0, 330) - 0.5) * 2 * WANDER),
                z + (int) Math.round((Town.noise(seed, x, z, 70.0, 331) - 0.5) * 2 * WANDER)};
    }

    /** The region ({@code x}, {@code z}) looks like: the capital's where it reaches, else the wandered point's block's. */
    public static Region looks(long seed, int x, int z) {
        if (Capital.owns(seed, x, z)) {
            return CAPITAL;
        }
        int[] p = wandered(seed, x, z);
        return of(seed, Math.floorDiv(p[0], Town.BLOCK), Math.floorDiv(p[1], Town.BLOCK));
    }

    /** How far inside the region it looks like ({@code x}, {@code z}) is ({@link #inside}, at the wandered point). */
    public static double looksInside(long seed, int x, int z) {
        int[] p = wandered(seed, x, z);
        return inside(seed, p[0], p[1]);
    }

    /** The region at ({@code x}, {@code z}), blocks of the world: the capital's where it reaches, else its block's. */
    public static Region at(long seed, int x, int z) {
        if (Capital.owns(seed, x, z)) {
            return CAPITAL;
        }
        return of(seed, Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK));
    }
}
