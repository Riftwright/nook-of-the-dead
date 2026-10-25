package dev.nookofthedead.world;

/**
 * The capital (the user, 2026-10-24: "城下町みたいな中央にでかい城があって、首都というか帝都というか…路は直線ではなく、曲がり
 * くねって複雑。立体的な構造があり、歴史を感じさせる複雑な構造をもった大都会…道以外はほぼ隙間なく"): one to an If, some
 * {@link #DISTANCE} blocks from where one comes in, a walled city {@link #WALL} blocks round its middle - the old seat of
 * those who ruled the land the pioneers built, a castle on a crag in its middle, a river in a gorge across it.
 * <p>
 * Everything of it is a function of the seed and the column, so each chunk draws its own part alike, and no chunk's edge
 * shows: the ground, the streets, the parcels, the buildings' heights and dress are all asked of the same fields from
 * every side.
 * <ul>
 * <li><b>Its ground</b> rises from the walls to the crag ({@link #ground}): the castle stands on a rock {@link #TOP} high,
 * the city on rolling ground round it, the river cut down through it to {@link #WATER}.</li>
 * <li><b>Its streets</b> wind ({@link #column}): a ring inside the wall and round the crag's foot, four avenues from the
 * gates to the crag, and between them the lanes and alleys of a Voronoi of city blocks, warped so none runs straight;
 * here and there a block left open as a square.</li>
 * <li><b>Its buildings</b> fill every block but its streets: each block split again into parcels, a house to each, wall to
 * wall, three to seven storeys, their upper floors jutting over the narrow lanes and bridging some of the alleys
 * ({@link CapitalHouses}).</li>
 * <li><b>Outside the wall</b>: a berm, the moat, a ring road, and what the people shut out of it left; the ground there
 * eases into the land round it, so the capital's edge is the wall and the moat, never a step in the ground.</li>
 * </ul>
 */
public final class Capital {
    /** How far from the world's middle (where one comes in) the capital's middle is. */
    public static final int DISTANCE = 1150;
    /** The inner face of the city wall, from the middle; the wall {@link #WALL_T} thick outward of it. */
    public static final int WALL = 280;
    public static final int WALL_T = 4;
    /** The moat: from and to these radii, its water at {@link #WATER}. */
    static final int MOAT_IN = WALL + 9;
    static final int MOAT_OUT = WALL + 19;
    /** The road round the walls, outside the moat. */
    static final int RING_IN = WALL + 23;
    static final int RING_OUT = WALL + 28;
    /** As far as the capital reaches: past it, the land as it would be; between the moat and this, it eases into it. */
    public static final int ZONE = WALL + 72;
    /** The river's and the moat's water, and the quays' tops. */
    static final int WATER = 60;
    static final int QUAY = 63;
    /** The city's ground: {@link #BASE} at the walls, {@link #RISE} more at the crag's foot, give or take {@link #HILLS}. */
    static final int BASE = 65;
    static final int RISE = 14;
    static final int HILLS = 5;
    /** The crag: its top {@link #TOP}, flat within {@link #R_TOP} of the middle; its foot about {@link #R_CRAG} out. */
    static final int TOP = 112;
    static final int R_TOP = 42;
    static final int R_CRAG = 66;
    /** The river: its middle about {@link #RIVER_OFF} out of the middle; water {@link #RIVER_HALF} each side, quays to {@link #QUAY_HALF}. */
    static final double RIVER_OFF = 128;
    static final double RIVER_HALF = 6.5;
    static final double QUAY_HALF = 10.5;
    /**
     * The city's blocks (a Voronoi of seeds this far apart) and the parcels in them: the old town's, inside the line of its
     * old wall ({@link #oldRing}), small and close; the new town's outside it, larger.
     */
    record Grid(int size, long salt, int flag) {}

    static final Grid OLD_BLOCKS = new Grid(19, 322, 1);
    static final Grid NEW_BLOCKS = new Grid(29, 320, 0);
    static final Grid OLD_PARCELS = new Grid(7, 332, 1);
    static final Grid NEW_PARCELS = new Grid(9, 330, 0);
    static final int CELL = 29;
    /** The old town: inside about this far out, and this much higher than the new (its old wall a terrace's edge). */
    static final int OLD = 150;
    static final int OLD_RISE = 5;
    /** Where three blocks meet, the lanes widen into a little open place: this far from the meeting, by this much. */
    static final double JUNCTION = 4.2;
    /** The avenues' and the inner ring's half widths; the round-the-crag road's. */
    static final double AVENUE = 3.2;
    /** How deep the shops on the main bridge are, each side of its way. */
    static final double SHOPS = 4.4;
    static final double RING = 3.0;

    /** Where an If's capital is, and which way its river runs. */
    record Site(long seed, int x, int z, double river, double[] phases) {
        /** The river's unit along ({@code ax}, {@code az}) and across, toward its side of the middle ({@code nx}, {@code nz}). */
        double ax() {
            return Math.cos(this.river);
        }

        double az() {
            return Math.sin(this.river);
        }

        /** The angle of gate {@code k} (0, the main gate, beyond the river; then round a quarter at a time). */
        double gate(int k) {
            return this.river + Math.PI / 2 + k * Math.PI / 2;
        }
    }

    private static volatile Site last;

    static Site site(long seed) {
        Site s = last;
        if (s != null && s.seed == seed) {
            return s;
        }
        long h = Town.hash(seed, 300, 300, 300);
        double angle = Math.floorMod(h, 100000L) / 100000.0 * Math.PI * 2;
        int x = (int) Math.round(Math.cos(angle) * DISTANCE);
        int z = (int) Math.round(Math.sin(angle) * DISTANCE);
        // the river along one of the world's axes, so the gates, the castle and the cathedral square to the blocks (a
        // building turned at an odd angle is all jagged edges in blocks); always the same one, so the diaries' north gate
        // (where the fever came from), east gate and south gate are where they say: the main gate north, over the river
        double river = Math.PI;
        double[] phases = new double[8];
        for (int i = 0; i < phases.length; i++) {
            phases[i] = Math.floorMod(Town.hash(seed, i, 301, 301), 100000L) / 100000.0 * Math.PI * 2;
        }
        s = new Site(seed, x, z, river, phases);
        last = s;
        return s;
    }

    /** For commands: the capital's middle. */
    /**
     * What stops the road up the crag being walked, or null: from the main avenue at the crag's foot, along the road's
     * middle, to the castle's rim before its gate - every step at most a block, every column the road's (in game: it ran
     * in a trench walled at both ends).
     */
    public static String rampProblem(long seed) {
        Site s = site(seed);
        double av = avenue(s, 0, R_CRAG);
        double foot = cragFoot(s, av);
        Column street = column(seed, (int) Math.floor(s.x + Math.cos(av) * (foot + 1.5)), (int) Math.floor(s.z + Math.sin(av) * (foot + 1.5)));
        int last = street.ground();
        if (street.kind() != Kind.STREET && street.kind() != Kind.SQUARE) {
            return "no street at the road's foot: " + street.kind();
        }
        double a0 = wrap(av - s.gate(0)) - 0.08;
        for (int i = 0; i <= 2000; i++) {
            double u = i / 2000.0;
            double angle = s.gate(0) + a0 + u * (Math.PI - a0);
            double r = CapitalCastle.rampRadius(u, cragFoot(s, angle));
            int x = (int) Math.floor(s.x + Math.cos(angle) * r);
            int z = (int) Math.floor(s.z + Math.sin(angle) * r);
            Column c = column(seed, x, z);
            if (c.kind() != Kind.RAMP && c.kind() != Kind.CASTLE && c.kind() != Kind.STREET && c.kind() != Kind.SQUARE) {
                return "off the road at " + x + "," + z + " (u " + u + "): " + c.kind();
            }
            if (Math.abs(c.ground() - last) > 1) {
                return "a step of " + (c.ground() - last) + " at " + x + "," + z + " (u " + u + ")";
            }
            last = c.ground();
        }
        return last >= TOP - 1 ? null : "the road ends at " + last + ", not the castle's " + TOP;
    }

    public static int[] centre(long seed) {
        Site s = site(seed);
        return new int[] {s.x, s.z};
    }

    /** Whether ({@code x}, {@code z}) is the capital's to draw (its biome, its ground). */
    public static boolean owns(long seed, int x, int z) {
        Site s = site(seed);
        double dx = x + 0.5 - s.x;
        double dz = z + 0.5 - s.z;
        return dx * dx + dz * dz < (double) ZONE * ZONE;
    }

    /** Whether the square from ({@code x0}, {@code z0}), {@code size} a side, comes within {@code pad} of the capital's reach. */
    public static boolean near(long seed, int x0, int z0, int size, int pad) {
        Site s = site(seed);
        double nx = Math.max(x0, Math.min(s.x, x0 + size - 1));
        double nz = Math.max(z0, Math.min(s.z, z0 + size - 1));
        double dx = nx - s.x;
        double dz = nz - s.z;
        return dx * dx + dz * dz < (double) (ZONE + pad) * (ZONE + pad);
    }

    // --- the fields -------------------------------------------------------------------------------------------------

    /** The river's middle, out from the city's middle across it, at {@code u} along it. */
    static double riverMiddle(Site s, double u) {
        return RIVER_OFF + 20 * Math.sin(u / 57.0 + s.phases[0]) + 8 * Math.sin(u / 23.0 + s.phases[1]);
    }

    /** How far the crag's foot is out of the middle, toward {@code angle}. */
    static double cragFoot(Site s, double angle) {
        double n = Town.noise(s.seed, (int) Math.round(s.x + Math.cos(angle) * 60), (int) Math.round(s.z + Math.sin(angle) * 60), 28.0, 305);
        return R_CRAG + (n - 0.5) * 14;
    }

    /**
     * The city's ground at ({@code x}, {@code z}), as if the river and the crag were not there: rising from the walls to the
     * crag, rolling.
     */
    static double city(Site s, double x, double z) {
        double r = Math.hypot(x - s.x, z - s.z);
        double t = smoothstep(R_CRAG, WALL - 10, r);
        double hills = (Town.noise(s.seed, (int) x, (int) z, 95.0, 303) - 0.5) * 2 * HILLS;
        double near = (Town.noise(s.seed, (int) x, (int) z, 31.0, 304) - 0.5) * 2;
        double angle = Math.atan2(z - s.z, x - s.x);
        double old = OLD_RISE * (1 - smoothstep(oldRing(s, angle) - 6, oldRing(s, angle) + 3, r));
        return BASE + RISE * (1 - t) + hills + near + old;
    }

    /** The line of the old town's wall, out from the middle toward {@code angle}: a ring, wavering. */
    static double oldRing(Site s, double angle) {
        return OLD + 9 * Math.sin(3 * angle + s.phases[6]) + 4 * Math.sin(5 * angle + s.phases[7]);
    }

    static double smoothstep(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** The warp that bends the city's grid of blocks, so no lane runs straight: how far ({@code x}, {@code z}) is moved. */
    static double warpX(long seed, int x, int z) {
        return 7.5 * (Town.noise(seed, x, z, 43.0, 306) * 2 - 1) + 3.0 * (Town.noise(seed, x, z, 17.0, 308) * 2 - 1);
    }

    static double warpZ(long seed, int x, int z) {
        return 7.5 * (Town.noise(seed, x, z, 43.0, 307) * 2 - 1) + 3.0 * (Town.noise(seed, x, z, 17.0, 309) * 2 - 1);
    }

    /** One Voronoi lookup: the nearest seed's cell, where it is, and how far inside its cell the point is (by the bisectors). */
    record Cell(int i, int j, double sx, double sz, double edge, int ni, int nj, double nearEdge, double second) {}

    /** A seed of the grid of {@code size} at cell ({@code i}, {@code j}), {@code salt} telling the grids apart. */
    static double[] seedOf(long seed, int i, int j, int size, long salt) {
        long h = Town.hash(seed, i, j, salt);
        double fx = 0.18 + 0.64 * (Math.floorMod(h, 1000L) / 1000.0);
        double fz = 0.18 + 0.64 * (Math.floorMod(h >> 12, 1000L) / 1000.0);
        return new double[] {(i + fx) * size, (j + fz) * size};
    }

    /**
     * The cell of the grid of {@code size} that ({@code px}, {@code pz}) is in, and how far it is from the cell's edge: the
     * bisector with each neighbour, less {@code half} of what lies along it ({@code half} null: nothing, the bare edge).
     */
    static Cell cell(long seed, double px, double pz, int size, long salt, java.util.function.ToDoubleBiFunction<long[], long[]> half) {
        int ci = (int) Math.floor(px / size);
        int cj = (int) Math.floor(pz / size);
        double best = Double.MAX_VALUE;
        int bi = ci;
        int bj = cj;
        double bx = 0;
        double bz = 0;
        double[][] seeds = new double[25][];
        int[][] ids = new int[25][];
        int n = 0;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                double[] sp = seedOf(seed, ci + di, cj + dj, size, salt);
                seeds[n] = sp;
                ids[n] = new int[] {ci + di, cj + dj};
                n++;
                double d = (px - sp[0]) * (px - sp[0]) + (pz - sp[1]) * (pz - sp[1]);
                if (d < best) {
                    best = d;
                    bi = ci + di;
                    bj = cj + dj;
                    bx = sp[0];
                    bz = sp[1];
                }
            }
        }
        double edge = Double.MAX_VALUE;
        double bare = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        int ni = bi;
        int nj = bj;
        for (int k = 0; k < n; k++) {
            if (ids[k][0] == bi && ids[k][1] == bj) {
                continue;
            }
            double ox = seeds[k][0] - bx;
            double oz = seeds[k][1] - bz;
            double len = Math.sqrt(ox * ox + oz * oz);
            double d2b = (px - bx) * (px - bx) + (pz - bz) * (pz - bz);
            double d2o = (px - seeds[k][0]) * (px - seeds[k][0]) + (pz - seeds[k][1]) * (pz - seeds[k][1]);
            double bis = (d2o - d2b) / (2 * len);
            if (bis < bare) {
                second = bare;
                bare = bis;
                ni = ids[k][0];
                nj = ids[k][1];
            } else if (bis < second) {
                second = bis;
            }
            double less = half == null ? 0 : half.applyAsDouble(new long[] {bi, bj}, new long[] {ids[k][0], ids[k][1]});
            edge = Math.min(edge, bis - less);
        }
        return new Cell(bi, bj, bx, bz, edge, ni, nj, bare, second);
    }

    /** The half width of the street between city blocks {@code a} and {@code b}: an alley, a lane, now and then a street. */
    static double lane(long seed, long[] a, long[] b) {
        long lo0 = Math.min(a[0] * 100003L + a[1], b[0] * 100003L + b[1]);
        long hi0 = Math.max(a[0] * 100003L + a[1], b[0] * 100003L + b[1]);
        long h = Math.floorMod(Town.hash(seed, lo0, hi0, 310), 100L);
        return h < 45 ? 1.0 : h < 85 ? 1.6 : 2.3;
    }

    /** Whether the street between city blocks {@code a} and {@code b} is roofed over by the houses either side, up high. */
    static boolean covered(long seed, long[] a, long[] b) {
        long lo0 = Math.min(a[0] * 100003L + a[1], b[0] * 100003L + b[1]);
        long hi0 = Math.max(a[0] * 100003L + a[1], b[0] * 100003L + b[1]);
        return lane(seed, a, b) < 1.2 && Math.floorMod(Town.hash(seed, lo0, hi0, 311), 100L) < 30;
    }

    /** Whether a street across the river between city blocks {@code a} and {@code b} has its bridge. */
    static boolean bridged(long seed, long[] a, long[] b) {
        long lo0 = Math.min(a[0] * 100003L + a[1], b[0] * 100003L + b[1]);
        long hi0 = Math.max(a[0] * 100003L + a[1], b[0] * 100003L + b[1]);
        return lane(seed, a, b) > 1.5 && Math.floorMod(Town.hash(seed, lo0, hi0, 312), 100L) < 55;
    }

    static double wrap(double angle) {
        double a = angle % (Math.PI * 2);
        if (a > Math.PI) {
            a -= Math.PI * 2;
        } else if (a < -Math.PI) {
            a += Math.PI * 2;
        }
        return a;
    }

    /** The middle of avenue {@code k} at {@code r} out: its angle, winding a little as it goes. */
    static double avenue(Site s, int k, double r) {
        // straight at both ends, so it meets its gate and the road up the crag square on (the review: up to 31 blocks off)
        double wind = smoothstep(R_CRAG, R_CRAG + 40, r) * (1 - smoothstep(WALL - 70, WALL - 8, r));
        return s.gate(k) + wind * (0.085 * Math.sin(r / 43.0 + s.phases[2 + k]) + 0.035 * Math.sin(r / 19.0 + s.phases[k]));
    }

    // --- what each column is --------------------------------------------------------------------------------------

    /** What a column of the capital is. */
    public enum Kind {
        /** Outside the wall: the ground easing into the land round it, the camps on it. */
        APRON, ROAD, BERM, MOAT, CAUSEWAY,
        /** The wall, a gate in it, the river's way through it. */
        WALL, GATE, WATERGATE,
        STREET, SQUARE, BRIDGE, RIVER, QUAY,
        BUILDING,
        /** The crag, its road up, its top (the castle's). */
        CRAG, RAMP, CASTLE,
        /** What stands of the old town's wall, along the inside of the street that follows its line. */
        OLDWALL,
        /** The great buildings drawn whole ({@link CapitalLandmarks}). */
        CATHEDRAL, HALL
    }

    /**
     * One column: what it is, the height of its ground (a building's floor; a street's or a square's top; the river's
     * bed; the crag's rock), and for a building, which it is and how it stands.
     *
     * @param block    the city block's cell (i, j), packed
     * @param parcel   the parcel's cell, packed
     * @param edge     how far in from the building's outside (its street, the river, a square) the column is
     * @param party    how far in from the next parcel's wall
     * @param upper    {@link #edge} for the floors above the ground floor (they jut over a lane, roof an alley)
     */
    public record Column(Kind kind, int ground, long block, long parcel, double edge, double party, double upper, double r,
            double angle, double river) {
        boolean built() {
            return this.kind == Kind.BUILDING;
        }
    }

    static long pack(int i, int j) {
        return (long) i << 32 ^ (j & 0xffffffffL);
    }

    /** A block's or a parcel's key: its cell, and which grid (old town or new). */
    static long key(Grid g, int i, int j) {
        return pack(i, j) << 1 | g.flag();
    }

    static Grid blocksOf(long key) {
        return (key & 1) == 1 ? OLD_BLOCKS : NEW_BLOCKS;
    }

    static Grid parcelsOf(long key) {
        return (key & 1) == 1 ? OLD_PARCELS : NEW_PARCELS;
    }

    static int keyI(long key) {
        return (int) ((key >> 1) >> 32);
    }

    static int keyJ(long key) {
        return (int) (key >> 1);
    }

    /** Whether city block {@code key} is left open as a square. */
    static boolean square(Site s, long key) {
        long[] placed = placedSquares(s);
        for (long p : placed) {
            if (p == key) {
                return true;
            }
        }
        Grid g = blocksOf(key);
        double[] p = seedOf(s.seed, keyI(key), keyJ(key), g.size(), g.salt());
        double r = Math.hypot(p[0] - s.x, p[1] - s.z);
        if (r < R_CRAG + 20 || r > WALL - 20) {
            return false;
        }
        return Math.floorMod(Town.hash(s.seed, keyI(key), keyJ(key), 321 + g.flag()), 100L) < (g.flag() == 1 ? 4 : 3);
    }

    private static volatile long placedSeed = Long.MIN_VALUE;
    private static volatile long[] placed;

    /** The blocks of the squares placed on purpose ({@link #squares}), worked out once for the seed. */
    static long[] placedSquares(Site s) {
        long[] p = placed;
        if (p != null && placedSeed == s.seed) {
            return p;
        }
        double[][] at = squares(s);
        p = new long[at.length];
        for (int k = 0; k < at.length; k++) {
            p[k] = nearestBlock(s, at[k][0], at[k][1]);
        }
        placed = p;
        placedSeed = s.seed;
        return p;
    }

    /** The squares placed on purpose: the market's, the cathedral's, and one inside each gate. */
    static double[][] squares(Site s) {
        double[][] out = new double[6][];
        double[] m = CapitalLandmarks.marketPoint(s);                         // the market: the near side of the river, off the main way
        out[0] = m;
        double[] cath = CapitalLandmarks.cathedralFront(s);
        out[1] = cath;
        for (int k = 0; k < 4; k++) {
            double g = s.gate(k);
            out[2 + k] = new double[] {s.x + Math.cos(g) * (WALL - 24), s.z + Math.sin(g) * (WALL - 24)};
        }
        return out;
    }

    /** The city block the point ({@code x}, {@code z}) of the world is in (warped as the city's are). */
    static long nearestBlock(Site s, double x, double z) {
        double wx = x + warpX(s.seed, (int) x, (int) z);
        double wz = z + warpZ(s.seed, (int) x, (int) z);
        Grid g = gridAt(s, x, z);
        Cell c = cell(s.seed, wx, wz, g.size(), g.salt(), null);
        return key(g, c.i(), c.j());
    }

    /** The blocks' grid at ({@code x}, {@code z}): the old town's inside its old wall's line, the new town's outside. */
    static Grid gridAt(Site s, double x, double z) {
        double r = Math.hypot(x - s.x, z - s.z);
        return r < oldRing(s, Math.atan2(z - s.z, x - s.x)) ? OLD_BLOCKS : NEW_BLOCKS;
    }

    /** What column ({@code x}, {@code z}) of the capital is; null outside it. */
    public static Column column(long seed, int x, int z) {
        Site s = site(seed);
        double px = x + 0.5;
        double pz = z + 0.5;
        double dx = px - s.x;
        double dz = pz - s.z;
        double r = Math.hypot(dx, dz);
        if (r >= ZONE) {
            return null;
        }
        double angle = Math.atan2(dz, dx);
        double u = dx * s.ax() + dz * s.az();
        double v = -dx * s.az() + dz * s.ax();
        double river = v - riverMiddle(s, u);                                // signed: + beyond the river's middle
        double city = city(s, px, pz);
        int street = (int) Math.round(city);
        // outside the wall
        if (r >= WALL) {
            for (int k = 0; k < 4; k++) {
                double arc = r * Math.abs(wrap(angle - s.gate(k)));
                if (arc < 3.6 && r < RING_IN) {
                    return new Column(r < WALL + WALL_T ? Kind.GATE : Kind.CAUSEWAY, street, 0, 0, 0, 0, 0, r, angle, river);
                }
            }
            if (Math.abs(river) < RIVER_HALF && r < MOAT_IN + 1) {
                return new Column(r < WALL + WALL_T ? Kind.WATERGATE : Kind.RIVER, WATER - 3, 0, 0, 0, 0, 0, r, angle, river);
            }
            Kind kind = r < WALL + WALL_T ? Kind.WALL : r < MOAT_IN ? Kind.BERM : r < MOAT_OUT ? Kind.MOAT : r >= RING_IN && r < RING_OUT
                    ? Kind.ROAD : Kind.APRON;
            return new Column(kind, street, 0, 0, 0, 0, 0, r, angle, river);
        }
        // the crag
        double foot = cragFoot(s, angle);
        if (r < foot) {
            return CapitalCastle.column(s, x, z, r, angle, foot, river);
        }
        // the river and its quays
        if (Math.abs(river) < QUAY_HALF) {
            // the main avenue's bridge: wide, a row of shops down each side of it (edge: how far into the shops' row)
            double mainArc = r * Math.abs(wrap(angle - avenue(s, 0, r)));
            // a bridge's deck is level across the river: the city's ground over the river's middle there
            int deck = (int) Math.round(city(s, s.x + u * s.ax() - riverMiddle(s, u) * s.az(), s.z + u * s.az() + riverMiddle(s, u) * s.ax()));
            if (mainArc < AVENUE + SHOPS) {
                return new Column(Kind.BRIDGE, deck, 0, 0, mainArc - AVENUE, 0, 0, r, angle, river);
            }
            Kind over = bridgeOver(s, px, pz, r, angle, u, foot);
            if (over != null) {
                return new Column(Kind.BRIDGE, deck, 0, 0, -1, 0, 0, r, angle, river);
            }
            return new Column(Math.abs(river) < RIVER_HALF ? Kind.RIVER : Kind.QUAY, Math.abs(river) < RIVER_HALF ? WATER - 3
                    : Math.min(QUAY, street - 1), 0, 0, 0, 0, 0, r, angle, river);
        }
        // the streets up to the crossings, from both banks
        if (Math.abs(river) < QUAY_HALF + 14 && crossing(s, u) < CROSSING) {
            return new Column(Kind.STREET, street, 0, 0, 0, 0, 0, r, angle, river);
        }
        // the great buildings
        Kind great = CapitalLandmarks.at(s, px, pz);
        if (great != null) {
            return new Column(great, (int) Math.round(CapitalLandmarks.level(s, great)), 0, 0, 0, 0, 0, r, angle, river);
        }
        if (CapitalLandmarks.round(s, px, pz)) {
            return new Column(Kind.STREET, street, 0, 0, 0, 0, 0, r, angle, river);
        }
        // the streets: the ring inside the wall, the road round the crag's foot, the avenues; the old wall's line, its
        // ruin along the old town's side of it
        double ringEdge = Math.abs(r - (WALL - 5)) - RING;
        // the road round the crag's foot runs right up to the rock (in game: a ring of one-block house fronts against it)
        double cragEdge = Math.abs(r - (foot + 3.5)) - 3.5;
        double avenueEdge = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            avenueEdge = Math.min(avenueEdge, r * Math.abs(wrap(angle - avenue(s, k, r))) - AVENUE);
        }
        double old = oldRing(s, angle);
        double oldEdge = Math.abs(r - old) - 2.7;
        if (ringEdge < 0 || cragEdge < 0 || avenueEdge < 0 || r >= WALL - 2) {
            return new Column(Kind.STREET, street, 0, 0, 0, 0, 0, r, angle, river);
        }
        if (oldEdge < 0) {
            boolean ruin = r < old - 1.4;
            return new Column(ruin ? Kind.OLDWALL : Kind.STREET, street, 0, 0, 0, 0, 0, r, angle, river);
        }
        // the lanes between the city's blocks
        Grid blocks = r < old ? OLD_BLOCKS : NEW_BLOCKS;
        Grid parcels = r < old ? OLD_PARCELS : NEW_PARCELS;
        double wx = px + warpX(seed, x, z);
        double wz = pz + warpZ(seed, x, z);
        Cell block = cell(seed, wx, wz, blocks.size(), blocks.salt(), (a, b) -> lane(seed, a, b) - (blocks.flag() == 1 ? 0.25 : 0));
        long bkey = key(blocks, block.i(), block.j());
        double widen = Math.max(0, JUNCTION - block.second()) * 0.85;          // the little open places where three meet
        double laneEdge = block.edge() - widen;
        if (square(s, bkey)) {
            return new Column(laneEdge < 0 ? Kind.STREET : Kind.SQUARE, street, bkey, 0, laneEdge, 0, 0, r, angle, river);
        }
        double others = Math.min(Math.min(Math.abs(river) - QUAY_HALF, Math.min(ringEdge, cragEdge)), Math.min(Math.min(avenueEdge, oldEdge),
                WALL - 2 - r));
        double edge = Math.min(laneEdge, others);
        long[] a = {block.i(), block.j()};
        long[] b = {block.ni(), block.nj()};
        double reach = covered(seed, a, b) ? 2.6 : lane(seed, a, b) > 1.3 && widen == 0 ? 1 : 0;
        Cell parcel = cell(seed, wx, wz, parcels.size(), parcels.salt(), null);
        long pkey = key(parcels, parcel.i(), parcel.j());
        int level = parcelLevel(s, pkey);
        if (laneEdge < 0) {
            // the lane itself; its upper floors' reach over it is the houses' (covered, or a jetty)
            double upper = Math.min(laneEdge + reach, others);
            if (upper > 0 && level + 4 >= street + 3) {
                // the two sides' upper floors meet over the lane's middle: a wall there between them
                return new Column(Kind.STREET, street, bkey, pkey, laneEdge, Math.min(parcel.edge(), block.nearEdge()), upper, r, angle, river);
            }
            return new Column(Kind.STREET, street, bkey, 0, laneEdge, 0, 0, r, angle, river);
        }
        double upper = Math.min(laneEdge + reach, others);
        return new Column(Kind.BUILDING, level, bkey, pkey, edge, parcel.edge(), upper, r, angle, river);
    }

    /**
     * Whether the river at ({@code px}, {@code pz}) is bridged: the avenues', the ring road's and the old wall's road's, and
     * the crossings between ({@link #crossing}) - each a whole bridge from bank to bank with a street up to it on both (the
     * review: the lanes' bridges, a lane's length each, mostly stopped in the water).
     */
    private static Kind bridgeOver(Site s, double px, double pz, double r, double angle, double u, double foot) {
        for (int k = 0; k < 4; k++) {
            if (r * Math.abs(wrap(angle - avenue(s, k, r))) < AVENUE) {
                return Kind.BRIDGE;
            }
        }
        if (Math.abs(r - (WALL - 5)) < RING || Math.abs(r - oldRing(s, angle)) < 2.7 || crossing(s, u) < CROSSING) {
            return Kind.BRIDGE;
        }
        return null;
    }

    /** Half a crossing's width. */
    static final double CROSSING = 1.6;

    /** How far along the river ({@code u}) the nearest of its crossings is: one in a slot of forty, most slots, not by the avenue. */
    static double crossing(Site s, double u) {
        int i = (int) Math.floor(u / 40.0);
        double best = Double.MAX_VALUE;
        for (int k = i - 1; k <= i + 1; k++) {
            long h = Town.hash(s.seed, k, 0, 334);
            double u0 = k * 40 + 20 + (Math.floorMod(h >> 8, 17L) - 8);
            if (Math.floorMod(h, 100L) < 70 && Math.abs(u0) > 28) {
                best = Math.min(best, Math.abs(u - u0));
            }
        }
        return best;
    }

    /** The ground level of city block {@code block} (packed): the city's, at its seed. */
    static int blockLevel(Site s, long block) {
        Grid g = blocksOf(block);
        double[] p = seedOf(s.seed, keyI(block), keyJ(block), g.size(), g.salt());
        return (int) Math.round(city(s, p[0], p[1]));
    }

    /**
     * The ground floor's level of the house on parcel {@code parcel}: the city's ground at the parcel's own middle (in game,
     * and the review: one level to a whole block of houses left doors a block or more over or under the street).
     */
    static int parcelLevel(Site s, long parcel) {
        int[] p = parcelSeed(s, parcel);
        return (int) Math.round(city(s, p[0] + 0.5, p[1] + 0.5));
    }

    /** How far out of the middle parcel {@code parcel}'s middle is, and across the river from it: {r, river}. */
    static double[] parcelPlace(Site s, long parcel) {
        int[] p = parcelSeed(s, parcel);
        double dx = p[0] + 0.5 - s.x;
        double dz = p[1] + 0.5 - s.z;
        double u = dx * s.ax() + dz * s.az();
        double v = -dx * s.az() + dz * s.ax();
        return new double[] {Math.hypot(dx, dz), v - riverMiddle(s, u)};
    }

    /** Where parcel {@code parcel}'s seed is, near enough, in the world's blocks. */
    static int[] parcelSeed(Site s, long parcel) {
        Grid g = parcelsOf(parcel);
        double[] p = seedOf(s.seed, keyI(parcel), keyJ(parcel), g.size(), g.salt());
        // the seeds are where the warped grid puts them: back by the warp there, near enough
        int x = (int) Math.floor(p[0]);
        int z = (int) Math.floor(p[1]);
        return new int[] {(int) Math.floor(p[0] - warpX(s.seed, x, z)), (int) Math.floor(p[1] - warpZ(s.seed, x, z))};
    }

    private Capital() {}
}
