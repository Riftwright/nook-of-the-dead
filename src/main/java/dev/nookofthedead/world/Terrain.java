package dev.nookofthedead.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;

/**
 * The ground of the regions away from the town ({@link Region}): the wilds' hills and rocks, a cave in some of them where
 * people hid, a small lake now and then in the wilds and the farmland - all small (the user, 2026-10-15: "大きすぎる海とか
 * 山とかあると興ざめなので、小規模なもの"). Everything here is fixed by the seed and the place, so each chunk draws its own
 * part alike.
 */
public final class Terrain {
    /** How high the wilds' hills rise at most, and the farmland's swells. */
    public static final double WILD_HILLS = 12.0;
    public static final double FARM_SWELLS = 3.0;
    /** How far into its region a hill takes to rise to its full height. */
    private static final double RISE = 60.0;
    /** The tallest a rock stands above the ground about it. */
    public static final int ROCK = 9;
    /** Of the wilds' blocks, how many in a thousand have a cave; of the wilds' and farmland's, how many a lake. */
    public static final int CAVES = 300;
    public static final int LAKES = 110;

    /** A small lake: its middle, how far it reaches, its water's top. */
    public record Lake(int x, int z, int radius, int water, long seed) {
        /**
         * How far from its shore ({@code x}, {@code z}) is, out in it (above 0) or on land (below): its shore pushed in
         * and out round it, so it is no circle.
         */
        public double in(int px, int pz) {
            double angle = Math.atan2(pz - this.z, px - this.x);
            double wander = Town.noise(this.seed, (int) Math.round(Math.cos(angle) * 30) + this.x, (int) Math.round(Math.sin(angle) * 30) + this.z,
                    14.0, 116);
            return this.radius * (0.7 + 0.5 * wander) - Math.hypot(px - this.x, pz - this.z);
        }
    }

    /**
     * A cave in a rock: a hill of rock {@link #HILL_RADIUS} round its middle, a hollow in it {@link #radius} across at the
     * ground's level ({@link #floor}), {@link #HEIGHT} high at its middle, and a way in from {@link #mouth}'s side.
     */
    public record Cave(int x, int z, int floor, int radius, Direction mouth) {
        public static final int HEIGHT = 5;
        public static final int HILL_RADIUS = 22;
        public static final int HILL = 13;

        /** How high the hollow is at ({@code px}, {@code pz}) above its floor: 0 where it is rock. */
        public int hollow(int px, int pz) {
            double d = Math.hypot(px - this.x, pz - this.z) / this.radius;
            if (d < 1) {
                return (int) Math.round(HEIGHT * Math.sqrt(1 - d * d));
            }
            // the way in: three wide, three high, out through the hill's side
            int along = (px - this.x) * this.mouth.getStepX() + (pz - this.z) * this.mouth.getStepZ();
            int across = Math.abs((px - this.x) * this.mouth.getStepZ() - (pz - this.z) * this.mouth.getStepX());
            return along > 0 && along <= HILL_RADIUS + 2 && across <= 1 ? 3 : 0;
        }

        /** Whether ({@code px}, {@code pz}) is in the hollow itself (not the way in). */
        public boolean inChamber(int px, int pz) {
            return Math.hypot(px - this.x, pz - this.z) < this.radius;
        }

        /**
         * The hill of rock over it at ({@code px}, {@code pz}): how high above the floor - a broad rounded dome, its edge
         * pushed in and out round it and its face roughened, so it reads as a hill of rock, not a cone.
         */
        public double hill(long seed, int px, int pz) {
            double angle = Math.atan2(pz - this.z, px - this.x);
            double reach = HILL_RADIUS * (0.8 + 0.35 * Town.noise(seed, (int) Math.round(Math.cos(angle) * 40) + this.x,
                    (int) Math.round(Math.sin(angle) * 40) + this.z, 18.0, 114));
            double d = Math.hypot(px - this.x, pz - this.z) / reach;
            if (d >= 1) {
                return 0;
            }
            double dome = HILL * Math.sqrt(1 - d * d);
            return Math.max(0, dome + (Town.noise(seed, px, pz, 5.0, 115) - 0.5) * 4 - (d > 0.85 ? (d - 0.85) * 20 : 0));
        }
    }

    private static final Map<Key, Object> CACHE = new ConcurrentHashMap<>();

    /** What is kept: a block's lake or cave, by seed and block. */
    private record Key(long seed, int bx, int bz, boolean cave) {}
    private static final Object NONE = new Object();

    /**
     * How much the ground at ({@code x}, {@code z}) rises over the town's ({@link Town#height}): the wilds' hills, the farmland's
     * swells, and a mountain's ({@link Peak}).
     */
    public static double lift(long seed, int x, int z) {
        double hills = hills(seed, x, z);
        Peak peak = peak(seed, x, z);
        return peak == null ? hills : hills + peak.rise(x, z);
    }

    /** The wilds' hills and the farmland's swells alone: what a sea is set into ({@link #feature}). */
    static double hills(long seed, int x, int z) {
        // the block's own region, under the capital too: the ground the capital eases into is the land's as it would be
        Region region = Region.of(seed, Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK));
        if (region != Region.WILDS && region != Region.FARMLAND) {
            return 0;
        }
        double inside = Math.min(1.0, Region.inside(seed, x, z) / RISE);
        double rise = inside * inside * (3 - 2 * inside);
        if (region == Region.WILDS) {
            double hills = Math.max(0, Town.noise(seed, x, z, 150.0, 92) - 0.3) / 0.7;
            return rise * WILD_HILLS * hills * hills + rise * 2 * (Town.noise(seed, x, z, 47.0, 94) - 0.5);
        }
        return rise * FARM_SWELLS * (Town.noise(seed, x, z, 190.0, 93) - 0.5) * 2;
    }

    /**
     * How high a rock stands above the ground at ({@code x}, {@code z}) in the wilds: here and there an outcrop where the
     * noise peaks, and the hill over a cave; 0 for none.
     */
    public static double rock(long seed, int x, int z) {
        if (Region.at(seed, x, z) != Region.WILDS) {
            return 0;
        }
        Cave cave = cave(seed, Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK));
        double hill = cave == null ? 0 : cave.hill(seed, x, z);
        double edge = Math.min(1.0, Region.inside(seed, x, z) / 30.0);
        // a crag where the broad noise peaks: rounded at its shoulders (the square root of how far in), rough on it
        double n = Town.noise(seed, x, z, 34.0, 95);
        double outcrop = 0;
        if (n > CRAG) {
            double in = (n - CRAG) / (1 - CRAG);
            outcrop = (Math.sqrt(in) * ROCK + (Town.noise(seed, x, z, 4.0, 96) - 0.5) * 3) * edge;
        }
        double high = Math.max(hill, outcrop);
        // gone toward a mountain's top, which is its own bare rock and snow (in game: a crag sat on the summit, and the
        // snow showed only on one shoulder beside it)
        Peak peak = high <= 0 ? null : peak(seed, x, z);
        double fade = peak == null ? 1 : Math.max(0, Math.min(1, (0.72 - peak.up(x, z)) / 0.12));
        return high <= 0 ? 0 : high * room(seed, x, z) * fade;
    }

    /** How far from a track a rock comes down to it, the same all round. */
    static final int TAPER = 6;

    /**
     * How much of a rock stands this near the wilds' tracks (the town's street grid, Town#ground) and the ruins standing on
     * their lots: 1 from {@link #TAPER} away, down to nothing beside one, eased - so a crag comes down to a track or a ruin on
     * a slope, not cut off sheer at it (the user, 2026-10-24: "チャンクの境目に直線的で不自然な崖"; the rocks were drawn on the
     * empty lots only, a wall left at a track's edge and a pit round a ruin).
     */
    static double room(long seed, int x, int z) {
        if (Town.ground(seed, x, z) != Town.Ground.LOT) {
            return 0;
        }
        int near = TAPER;
        for (int k = 1; k < near; k++) {
            if (Town.ground(seed, x + k, z) != Town.Ground.LOT || Town.ground(seed, x - k, z) != Town.Ground.LOT
                    || Town.ground(seed, x, z + k) != Town.Ground.LOT || Town.ground(seed, x, z - k) != Town.Ground.LOT) {
                near = k;
                break;
            }
        }
        int bx0 = Math.floorDiv(x, Town.BLOCK);
        int bz0 = Math.floorDiv(z, Town.BLOCK);
        for (int bx = bx0 - 1; bx <= bx0 + 1; bx++) {
            for (int bz = bz0 - 1; bz <= bz0 + 1; bz++) {
                for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                    if (lot.use() != Town.Use.BUILDING || lot.building().isEmpty()) {
                        continue;
                    }
                    int lx = lot.corner().getX();
                    int lz = lot.corner().getZ();
                    int d = Math.max(Math.max(lx - x, x - (lx + lot.size() - 1)), Math.max(lz - z, z - (lz + lot.size() - 1)));
                    if (d <= 0) {
                        return 0;
                    }
                    near = Math.min(near, d);
                }
            }
        }
        double t = (near - 1) / (double) (TAPER - 1);
        return t * t * (3 - 2 * t);
    }

    /** Where the wilds' broad noise is over this, a crag. */
    private static final double CRAG = 0.7;

    // --- seas and mountains ------------------------------------------------------------------------------------------------

    /**
     * A small sea (the user, 2026-10-13: "普通に小規模な海とか山とかもたまにあったほうが"; 2026-10-15: never so big it
     * spoils the place): open water a couple of hundred blocks across in the wilds or the farmland, its shore wandering, a
     * beach round it, the ground eased down to it; the tracks that crossed the land run on into it, under the water.
     */
    public record Sea(int x, int z, int radius, int water, long seed) {
        /** How far out in it ({@code px}, {@code pz}) is (above 0), or back from its shore (below). */
        public double in(int px, int pz) {
            double angle = Math.atan2(pz - this.z, px - this.x);
            double wander = Town.noise(this.seed, (int) Math.round(Math.cos(angle) * 70) + this.x, (int) Math.round(Math.sin(angle) * 70) + this.z,
                    30.0, 118);
            return this.radius * (0.7 + 0.55 * wander) - Math.hypot(px - this.x, pz - this.z);
        }

        /** The ground at ({@code px}, {@code pz}) from what it would be: its bed out in the water, eased down to the shore. */
        double shape(int px, int pz, double ground) {
            double in = this.in(px, pz);
            if (in > 0) {
                return Math.min(ground, Math.max(SEA_FLOOR, this.water - 1 - Math.min(SEA_DEPTH, in / 4.0)));
            }
            if (in > -SHORE) {
                double t = 1 + in / SHORE;                                  // 1 at the water, 0 where the land is its own
                return ground + (this.water - ground) * t * t * (3 - 2 * t);
            }
            return ground;
        }

        /** How far it reaches at most, its shore's ground with it. */
        int reach() {
            return (int) Math.ceil(this.radius * 1.25) + SHORE;
        }
    }

    /** A mountain in the wilds: a broad rise up to a summit, bare rock above the trees, snow at its top. */
    public record Peak(int x, int z, int radius, double height, long seed) {
        /** How high it lifts the ground at ({@code px}, {@code pz}): its shoulders and gullies rough at a hillside's scale. */
        public double rise(int px, int pz) {
            return this.height * 0.95 * this.up(px, pz) * (0.86 + 0.28 * Town.noise(this.seed, px, pz, 44.0, 120));
        }

        /**
         * How far up it ({@code px}, {@code pz}) is, 0 at its foot to 1 at its summit, its roughness left out: the trees
         * stop, the rock begins and the snow lies by this, so they ring it evenly.
         */
        public double up(int px, int pz) {
            double angle = Math.atan2(pz - this.z, px - this.x);
            double reach = this.radius * (0.8 + 0.4 * Town.noise(this.seed, (int) Math.round(Math.cos(angle) * 60) + this.x,
                    (int) Math.round(Math.sin(angle) * 60) + this.z, 26.0, 119));
            double d = Math.hypot(px - this.x, pz - this.z) / reach;
            if (d >= 1) {
                return 0;
            }
            // steeper the higher it goes, as a mountain does (a dome reads as a hill): gentle at its foot, rock near its top,
            // the summit rounded off a little
            double t = 1 - d;
            double cone = Math.pow(t, 1.7);
            double foot = Math.min(1, t * 6);                                  // no crease where it leaves the land
            double summit = cone > 0.9 ? 0.9 + (cone - 0.9) * 0.5 : cone;
            return summit * foot * foot / 0.95;
        }

        int reach() {
            return (int) Math.ceil(this.radius * 1.2);
        }
    }

    /** Seas and mountains are looked for one to a square of this many grid blocks, and stay this far inside it. */
    public static final int FEATURE_CELL = 7;
    private static final int FEATURE_MARGIN = 200;
    /** Of the squares, how many in a hundred try for a sea and how many for a mountain - where the ground lets them. */
    public static final int SEAS = 30;
    public static final int PEAKS = 30;
    /** A sea's shore: how far back the land eases down to it; how deep it gets; never deeper than this (the galleries). */
    static final int SHORE = 26;
    static final int SEA_DEPTH = 9;
    static final int SEA_FLOOR = Tunnels.FLOOR + Tunnels.HIGH + 8;

    private record Square(long seed, int qx, int qz) {}
    private static final Map<Square, Object> FEATURES = new ConcurrentHashMap<>();

    /** The sea whose water or shore ({@code x}, {@code z}) is in, if any. */
    public static @Nullable Sea sea(long seed, int x, int z) {
        return feature(seed, x, z) instanceof Sea sea && Math.hypot(x - sea.x(), z - sea.z()) <= sea.reach() ? sea : null;
    }

    /** The mountain ({@code x}, {@code z}) is on, if any. */
    public static @Nullable Peak peak(long seed, int x, int z) {
        return feature(seed, x, z) instanceof Peak peak && Math.hypot(x - peak.x(), z - peak.z()) <= peak.reach() ? peak : null;
    }

    /** The sea or mountain of the square ({@code x}, {@code z}) is in, if it has one. */
    static @Nullable Object feature(long seed, int x, int z) {
        int side = FEATURE_CELL * Town.BLOCK;
        return square(seed, Math.floorDiv(x, side), Math.floorDiv(z, side));
    }

    /** The sea or mountain of square ({@code qx}, {@code qz}) ({@link #FEATURE_CELL} grid blocks a side), if it has one. */
    public static @Nullable Object square(long seed, int qx, int qz) {
        Square key = new Square(seed, qx, qz);
        Object found = FEATURES.get(key);
        if (found == null) {
            found = rollFeature(seed, key.qx(), key.qz());                  // not in computeIfAbsent: a site asks the ground's height
            FEATURES.putIfAbsent(key, found);
            if (FEATURES.size() > 4096) {
                FEATURES.clear();
            }
        }
        return found == NONE ? null : found;
    }

    /**
     * What square ({@code qx}, {@code qz}) holds: a sea or a mountain where its roll says and the ground lets it - all the
     * land it reaches the wilds (or the farmland, for a sea), not near where one comes in, the capital, or the second
     * institute; else nothing.
     */
    private static Object rollFeature(long seed, int qx, int qz) {
        long h = Town.hash(seed, qx, qz, 121);
        int roll = (int) Math.floorMod(h, 100L);
        boolean sea = roll < SEAS;
        if (!sea && roll >= SEAS + PEAKS) {
            return NONE;
        }
        int side = FEATURE_CELL * Town.BLOCK;
        int span = side - 2 * FEATURE_MARGIN;
        int x = qx * side + FEATURE_MARGIN + (int) Math.floorMod(h >> 8, (long) span);
        int z = qz * side + FEATURE_MARGIN + (int) Math.floorMod(h >> 24, (long) span);
        Object made = sea ? new Sea(x, z, 85 + (int) Math.floorMod(h >> 40, 36L), 0, seed)
                : new Peak(x, z, 120 + (int) Math.floorMod(h >> 40, 40L), 50 + Math.floorMod(h >> 48, 21L), seed);
        int reach = made instanceof Sea s ? s.reach() : ((Peak) made).reach();
        if (Math.hypot(x, z) < (Region.HOME + 3) * Town.BLOCK + reach || Capital.near(seed, x - reach, z - reach, 2 * reach, 120)) {
            return NONE;
        }
        int[] ascent = Ascent.where(seed);
        if (Math.hypot(x - ascent[0], z - ascent[1]) < reach + 160) {
            return NONE;
        }
        for (int bx = Math.floorDiv(x - reach, Town.BLOCK); bx <= Math.floorDiv(x + reach, Town.BLOCK); bx++) {
            for (int bz = Math.floorDiv(z - reach, Town.BLOCK); bz <= Math.floorDiv(z + reach, Town.BLOCK); bz++) {
                Region region = Region.of(seed, bx, bz);
                if (region != Region.WILDS && !(sea && region == Region.FARMLAND)) {
                    return NONE;
                }
            }
        }
        if (made instanceof Sea s) {
            // its water a block under the lowest of the land round it, so its shore holds it all round
            double low = Double.MAX_VALUE;
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI / 16;
                int px = (int) Math.round(x + Math.cos(a) * s.radius() * 1.3);
                int pz = (int) Math.round(z + Math.sin(a) * s.radius() * 1.3);
                low = Math.min(low, Town.groundBefore(seed, px, pz));
            }
            return new Sea(x, z, s.radius(), Math.max(SEA_FLOOR + 4, (int) Math.floor(low) - 1), seed);
        }
        return made;
    }

    /**
     * Whether a lot at ({@code x0}, {@code z0}), {@code size} across, stands clear of the seas and mountains: off a sea's
     * water and beach, and low on a mountain's foot at most - nothing is built in the one or up the other.
     */
    public static boolean free(long seed, int x0, int z0, int size) {
        for (int[] p : new int[][] {{x0, z0}, {x0 + size - 1, z0}, {x0, z0 + size - 1}, {x0 + size - 1, z0 + size - 1}, {x0 + size / 2, z0 + size / 2}}) {
            Sea sea = sea(seed, p[0], p[1]);
            if (sea != null && sea.in(p[0], p[1]) > -10) {
                return false;
            }
            Peak peak = peak(seed, p[0], p[1]);
            if (peak != null && peak.rise(p[0], p[1]) > 5) {
                return false;
            }
        }
        return true;
    }

    /** The lake of block ({@code bx}, {@code bz}), if it has one: in the wilds and farmland, never a cave's block. */
    public static @Nullable Lake lake(long seed, int bx, int bz) {
        Object found = CACHE.computeIfAbsent(new Key(seed, bx, bz, false), k -> {
            Region region = Region.of(seed, bx, bz);
            if (region != Region.WILDS && region != Region.FARMLAND) {
                return NONE;
            }
            if (Capital.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK, 24)) {
                return NONE;                                                // none at the capital's edge (a dry crater cut by its circle)
            }
            if (feature(seed, bx * Town.BLOCK + Town.BLOCK / 2, bz * Town.BLOCK + Town.BLOCK / 2) != null) {
                return NONE;                                                // none in a sea's square, nor up a mountain
            }
            long h = Town.hash(seed, bx, bz, 98);
            if (Math.floorMod(h, 1000) >= LAKES) {
                return NONE;
            }
            int radius = 12 + (int) Math.floorMod(h >> 10, 8L);
            int x = bx * Town.BLOCK + Town.BLOCK / 2 + (int) Math.floorMod(h >> 20, 11L) - 5;
            int z = bz * Town.BLOCK + Town.BLOCK / 2 + (int) Math.floorMod(h >> 30, 11L) - 5;
            int water = (int) Math.floor(Town.height(seed, x, z)) - 1;
            return new Lake(x, z, radius, water, seed);
        });
        trim();
        return found instanceof Lake lake ? lake : null;
    }

    /** The cave of block ({@code bx}, {@code bz}), if it has one: in the wilds only, never with a lake. */
    public static @Nullable Cave cave(long seed, int bx, int bz) {
        Object found = CACHE.computeIfAbsent(new Key(seed, bx, bz, true), k -> {
            if (Region.of(seed, bx, bz) != Region.WILDS || Math.floorMod(Town.hash(seed, bx, bz, 98), 1000) < LAKES
                    || Capital.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK, 4)
                    || sea(seed, bx * Town.BLOCK + Town.BLOCK / 2, bz * Town.BLOCK + Town.BLOCK / 2) != null) {
                return NONE;
            }
            long h = Town.hash(seed, bx, bz, 100);
            if (Math.floorMod(h, 1000) >= CAVES) {
                return NONE;
            }
            int x = bx * Town.BLOCK + Town.BLOCK / 2 + (int) Math.floorMod(h >> 20, 15L) - 7;
            int z = bz * Town.BLOCK + Town.BLOCK / 2 + (int) Math.floorMod(h >> 30, 15L) - 7;
            Direction mouth = Direction.from2DDataValue((int) Math.floorMod(h >> 40, 4L));
            int floor = (int) Math.round(Town.height(seed, x, z));
            return new Cave(x, z, floor, 7 + (int) Math.floorMod(h >> 44, 3L), mouth);
        });
        trim();
        return found instanceof Cave cave ? cave : null;
    }

    private static void trim() {
        if (CACHE.size() > 50_000) {
            CACHE.clear();
        }
    }

    private Terrain() {}
}
