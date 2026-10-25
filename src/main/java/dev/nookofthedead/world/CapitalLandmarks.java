package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The capital's two great buildings in the city ({@link Capital}), drawn whole: the cathedral, its two towers over its
 * square and a spire over the crossing, where the people went for sanctuary and barred its doors behind them; and the
 * council's hall on the market square, its clock tower, its arcade where the rations were handed out, the chamber
 * upstairs where it was decided what to tell them. Both square to the world's axes, turned to face their squares.
 */
final class CapitalLandmarks {
    /** The cathedral: {@link #NAVE} long from its front, {@link #NAVE_W} each side of its middle; the transept across it. */
    static final int NAVE = 50;
    static final int NAVE_W = 9;
    static final int TRANSEPT_FROM = 30;
    static final int TRANSEPT_TO = 40;
    static final int TRANSEPT_W = 18;
    /** The council's hall: {@link #HALL_W} each side of its middle, {@link #HALL_D} deep from its front. */
    static final int HALL_W = 14;
    static final int HALL_D = 17;

    /** A great building's frame: where its front's middle is, which way its front faces (out), and across. */
    record Frame(int x, int z, Direction front, int level) {
        /** How far in from the front ({@code a}) and across from the middle ({@code b}) column ({@code wx}, {@code wz}) is. */
        double[] local(double wx, double wz) {
            double dx = wx + 0.5 - (this.x + 0.5);
            double dz = wz + 0.5 - (this.z + 0.5);
            Direction in = this.front.getOpposite();
            double a = dx * in.getStepX() + dz * in.getStepZ();
            Direction side = this.front.getClockWise();
            double b = dx * side.getStepX() + dz * side.getStepZ();
            return new double[] {a, b};
        }
    }

    private static volatile long knownSeed = Long.MIN_VALUE;
    private static volatile Frame cathedral;
    private static volatile Frame hall;

    private static void work(Capital.Site s) {
        if (knownSeed == s.seed()) {
            return;
        }
        double ca = s.river() - Math.PI / 2 - 0.9;
        int cx = (int) Math.round(s.x() + Math.cos(ca) * 168);
        int cz = (int) Math.round(s.z() + Math.sin(ca) * 168);
        Direction toCastle = Direction.getApproximateNearest(s.x() - cx, 0, s.z() - cz);
        Direction out = toCastle.getOpposite();
        // its front: the end away from the castle, half its length out from where it stands
        int fx = cx + out.getStepX() * NAVE / 2;
        int fz = cz + out.getStepZ() * NAVE / 2;
        // its floor at its square's height, before its great door (the review: taken at its middle, the door missed the square)
        Frame c = new Frame(fx, fz, out, (int) Math.round(Capital.city(s, fx + out.getStepX() * 3, fz + out.getStepZ() * 3)));
        double[] m = marketPoint(s);
        Direction hallOut = Direction.getApproximateNearest(m[0] - s.x(), 0, m[1] - s.z());
        // the hall stands on the square's castle side, facing out over it
        int hx = (int) Math.round(m[0]) - hallOut.getStepX() * 15;
        int hz = (int) Math.round(m[1]) - hallOut.getStepZ() * 15;
        Frame h = new Frame(hx, hz, hallOut, (int) Math.round(Capital.city(s, hx + hallOut.getStepX() * 3, hz + hallOut.getStepZ() * 3)));
        cathedral = c;
        hall = h;
        knownSeed = s.seed();
    }

    static Frame cathedral(Capital.Site s) {
        work(s);
        return cathedral;
    }

    static Frame hall(Capital.Site s) {
        work(s);
        return hall;
    }

    /** The market square's middle. */
    static double[] marketPoint(Capital.Site s) {
        double m = s.river() - Math.PI / 2 + 0.55;
        return new double[] {s.x() + Math.cos(m) * 112, s.z() + Math.sin(m) * 112};
    }

    /** The middle of the square before the cathedral's front. */
    static double[] cathedralFront(Capital.Site s) {
        Frame c = cathedral(s);
        return new double[] {c.x() + c.front().getStepX() * 16, c.z() + c.front().getStepZ() * 16};
    }

    /** Which great building ({@code px}, {@code pz}) is under, if any. */
    static Capital.Kind at(Capital.Site s, double px, double pz) {
        Frame c = cathedral(s);
        double[] ab = c.local(Math.floor(px), Math.floor(pz));
        if (inCathedral(ab[0], ab[1])) {
            return Capital.Kind.CATHEDRAL;
        }
        Frame h = hall(s);
        ab = h.local(Math.floor(px), Math.floor(pz));
        if (ab[0] >= 0 && ab[0] < HALL_D && Math.abs(ab[1]) <= HALL_W) {
            return Capital.Kind.HALL;
        }
        return null;
    }

    static boolean inCathedral(double a, double b) {
        return inCathedral(a, b, 0);
    }

    /** Whether ({@code a}, {@code b}) is the cathedral's, or within {@code m} of it. */
    static boolean inCathedral(double a, double b, double m) {
        if (a < -m || a > NAVE + NAVE_W + m) {
            return false;
        }
        if (a > NAVE) {
            return Math.hypot(a - NAVE, b) <= NAVE_W + m;                         // the apse, round at the far end (all of it)
        }
        if (a >= TRANSEPT_FROM - m && a <= TRANSEPT_TO + m && Math.abs(b) <= TRANSEPT_W + m) {
            return true;
        }
        return Math.abs(b) <= NAVE_W + m || a < 8 + m && Math.abs(b) <= NAVE_W + 3 + m;   // the towers stand out past the nave a little
    }

    /**
     * Whether ({@code px}, {@code pz}) is close round a great building but not in it: kept clear as a lane, so no house
     * leans on it (the review: houses against them stood open on that side, their windows looking into the houses).
     */
    static boolean round(Capital.Site s, double px, double pz) {
        double[] ab = cathedral(s).local(Math.floor(px), Math.floor(pz));
        if (inCathedral(ab[0], ab[1], 2.5)) {
            return true;
        }
        ab = hall(s).local(Math.floor(px), Math.floor(pz));
        return ab[0] >= -2.5 && ab[0] < HALL_D + 2.5 && Math.abs(ab[1]) <= HALL_W + 2.5;
    }

    static double level(Capital.Site s, Capital.Kind kind) {
        return kind == Capital.Kind.CATHEDRAL ? cathedral(s).level() : hall(s).level();
    }

    // --- the cathedral ------------------------------------------------------------------------------------------------

    static void draw(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        if (c.kind() == Capital.Kind.CATHEDRAL) {
            cathedralColumn(level, s, x, z, box, at);
        } else {
            hallColumn(level, s, x, z, box, at);
        }
    }

    private static BlockState stone(Capital.Site s, int x, int y, int z) {
        return CapitalHouses.weather(s, Blocks.STONE_BRICKS, x, y, z, false).defaultBlockState();
    }

    private static void cathedralColumn(WorldGenLevel level, Capital.Site s, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        Frame f = cathedral(s);
        double[] ab = f.local(x, z);
        double a = ab[0];
        double b = ab[1];
        int base = f.level();
        RandomSource random = RandomSource.create(Town.hash(s.seed(), x, z, 360));
        CapitalDraw.foundation(level, s, x, z, base, box, at);
        boolean tower = a < 8 && Math.abs(b) >= NAVE_W - 4;
        boolean apse = a > NAVE;
        boolean transept = a >= TRANSEPT_FROM && a <= TRANSEPT_TO && Math.abs(b) > NAVE_W;
        int wallTop = base + (tower ? 40 : transept ? 18 : 22);
        double in = cathedralIn(a, b);
        boolean wall = in < 1.2;
        boolean frontWall = a < 1.2 && !tower;
        for (int y = base; y <= wallTop; y++) {
            BlockState state;
            if (wall) {
                state = cathedralWall(s, x, y, z, a, b, base, tower, frontWall, transept, apse, random);
                if (state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) {
                    // a window's glass, joined along its wall: the wall's run is across the way the inside grows fastest
                    boolean normalAlongA = Math.abs(cathedralIn(a + 1, b) - cathedralIn(a - 1, b)) > Math.abs(cathedralIn(a, b + 1) - cathedralIn(a, b - 1));
                    state = CapitalDraw.along(state, normalAlongA ? f.front().getClockWise().getAxis() : f.front().getAxis());
                }
            } else if (y == base) {
                state = (Math.floorMod(x + z, 2) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.CHISELED_STONE_BRICKS).defaultBlockState();
            } else if (!tower && (state = nave(f, (int) Math.round(a), (int) Math.round(b), y - base)) != null) {
                // the nave's stone: its piers and arcades, the crossing's great piers, the vault's ribs
            } else if (tower && Math.round(a) == 6 && Math.abs(Math.round(b)) == NAVE_W - 3 && y > base && y <= base + 33) {
                state = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front());   // up to the bells
            } else if (tower && (y - base) % 8 == 0) {
                state = Blocks.SPRUCE_PLANKS.defaultBlockState();
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (!wall) {
            CapitalStory.cathedralFloor(level, s, x, z, a, b, base, box, at, random, tower);
        }
        // the roofs: the nave's and the transept's steep gables, the towers' spires, the crossing's spire
        if (tower) {
            double tb = Math.abs(b) - (NAVE_W - 4);
            double inT = Math.min(Math.min(a + 0.5, 7.5 - a), Math.min(tb + 0.5, 7.5 - tb));
            int rise = (int) Math.floor(Math.max(0, inT) * 3.2);
            int outer = (int) Math.floor(Math.max(0, inT - 1) * 3.2);              // the next ring out's: filled down to it, no gaps
            for (int y = wallTop + 1; y < wallTop + 1 + rise; y++) {
                set(level, box, at.set(x, y, z), (y > wallTop + 1 + outer ? Blocks.DEEPSLATE_TILES : Blocks.AIR).defaultBlockState());
            }
            set(level, box, at.set(x, wallTop + 1 + rise, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
            if (inT > 3.2) {
                set(level, box, at.set(x, wallTop + 2 + rise, z), Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState());
            }
            if (inT > 2 && inT < 3 && Math.floorMod(x + z, 2) == 0) {
                set(level, box, at.set(x, base + 33, z), Blocks.BELL.defaultBlockState());    // on the belfry's floor
            }
            return;
        }
        double across = transept ? Math.min(a - TRANSEPT_FROM, TRANSEPT_TO - a) : NAVE_W + 0.5 - Math.abs(b);
        if (apse) {
            across = in;
        }
        int rise = (int) Math.floor(Math.max(0, across) * 1.3);
        int outer = (int) Math.floor(Math.max(0, across - 1) * 1.3);
        boolean crossing = a >= TRANSEPT_FROM && a <= TRANSEPT_TO && Math.abs(b) <= NAVE_W;
        if (crossing) {
            double inC = Math.min(Math.min(a - TRANSEPT_FROM, TRANSEPT_TO - a), NAVE_W - Math.abs(b));
            rise = Math.max(rise, (int) Math.floor(Math.max(0, inC) * 7.5));
            outer = Math.max(outer, (int) Math.floor(Math.max(0, inC - 1) * 7.5));
        }
        for (int y = wallTop + 1; y < wallTop + 1 + rise; y++) {
            set(level, box, at.set(x, y, z), wall && !apse && !transept && a < 1.2 ? stone(s, x, y, z)
                    : y > wallTop + 1 + outer ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        set(level, box, at.set(x, wallTop + 1 + rise, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
        if (crossing && Math.abs(a - (TRANSEPT_FROM + TRANSEPT_TO) / 2.0) < 0.6 && Math.abs(b) < 0.6) {
            set(level, box, at.set(x, wallTop + 2 + rise, z), Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState());
        }
    }

    /** How far in from the cathedral's outside ({@code a}, {@code b}) is, for its walls. */
    static double cathedralIn(double a, double b) {
        boolean tower = a < 8 && Math.abs(b) >= NAVE_W - 4;
        if (a > NAVE) {
            return NAVE_W - Math.hypot(a - NAVE, b);
        }
        if (tower) {
            double tb = Math.abs(b) - (NAVE_W - 4);
            return Math.min(Math.min(a + 0.5, 7.5 - a), Math.min(tb + 0.5, 7.5 - tb));
        }
        if (a >= TRANSEPT_FROM && a <= TRANSEPT_TO) {
            if (Math.abs(b) > NAVE_W) {
                return Math.min(Math.min(a - TRANSEPT_FROM + 0.5, TRANSEPT_TO + 0.5 - a), TRANSEPT_W + 0.5 - Math.abs(b));
            }
            return a + 0.5;                                                       // the crossing: open into both arms
        }
        return Math.min(NAVE_W + 0.5 - Math.abs(b), a + 0.5);
    }

    /**
     * The nave's stone at ({@code a}, {@code b}), {@code h} over the floor, or null for open: piers down both sides every
     * six, carved at foot and head, the arcade's arches between them and the course over; the crossing's four great piers
     * to the vault; across the nave under the roof, a rib at every pier.
     */
    static BlockState nave(Frame f, int a, int b, int h) {
        int ab = Math.abs(b);
        boolean crossing = a >= TRANSEPT_FROM - 1 && a <= TRANSEPT_TO + 1;
        if (crossing && (a == TRANSEPT_FROM || a == TRANSEPT_FROM + 1 || a == TRANSEPT_TO || a == TRANSEPT_TO - 1) && (ab == NAVE_W - 1 || ab == NAVE_W - 2)) {
            return (h % 6 == 1 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
        }
        if (a <= 8 || a >= NAVE - 2 || crossing) {
            return null;
        }
        int bay = Math.floorMod(a, 6);
        if (ab == 5 && h <= 13) {
            if (bay == 3) {
                return (h == 1 || h == 12 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
            }
            if (h == 13) {
                return Blocks.STONE_BRICKS.defaultBlockState();
            }
            if (h == 12 && (bay == 2 || bay == 4)) {
                return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.FACING, bay == 2 ? f.front().getOpposite() : f.front());
            }
            return null;
        }
        if (bay == 3 && ab < 5) {
            if (h == 21) {
                return Blocks.STONE_BRICKS.defaultBlockState();
            }
            if (h == 20 && ab == 4) {
                return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.FACING, b > 0 ? f.front().getClockWise() : f.front().getCounterClockWise());
            }
        }
        return null;
    }

    private static BlockState cathedralWall(Capital.Site s, int x, int y, int z, double a, double b, int base, boolean tower,
            boolean frontWall, boolean transept, boolean apse, RandomSource random) {
        int h = y - base;
        // a door from the nave into each tower
        if (tower && Math.round(a) == 4 && Math.abs(Math.round(b)) == NAVE_W - 4 && h >= 1 && h <= 3) {
            return Blocks.AIR.defaultBlockState();
        }
        // the doors: three in the front, barred from within; the great one in the middle
        if (frontWall && h >= 1 && h <= 6 && Math.abs(b) < 2.6) {
            return h == 6 ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP) : Blocks.AIR.defaultBlockState();
        }
        // the rose window over the great door
        if (frontWall && Math.hypot(h - 13, b) < 4.2) {
            return Math.hypot(h - 13, b) < 3.4 ? stainedGlass(s, x, y, z) : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        }
        // tall windows down the sides, a buttress between each
        boolean side = !frontWall && !tower;
        int along = (int) Math.round(apse ? Math.atan2(b, a - NAVE) * 8 : transept ? b : a);
        if (side && h >= 5 && h <= (transept ? 14 : 17) && Math.floorMod(along, 4) != 0) {
            return h >= (transept ? 13 : 16) ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                    : stainedGlass(s, x, y, z);
        }
        if (tower && h > 8 && (h - 8) % 8 >= 2 && (h - 8) % 8 <= 5 && Math.floorMod(x + z, 3) == 0) {
            return Blocks.AIR.defaultBlockState();                          // the towers' louvres
        }
        return stone(s, x, y, z);
    }

    static BlockState stainedGlass(Capital.Site s, int x, int y, int z) {
        long r = Math.floorMod(Town.hash(s.seed(), x * 7L + y, z, 361), 100L);
        if (r < 35) {
            return Blocks.AIR.defaultBlockState();
        }
        Block pane = r < 50 ? Blocks.STAINED_GLASS_PANE.blue() : r < 62 ? Blocks.STAINED_GLASS_PANE.red() : r < 72 ? Blocks.STAINED_GLASS_PANE.yellow()
                : r < 82 ? Blocks.STAINED_GLASS_PANE.purple() : r < 90 ? Blocks.STAINED_GLASS_PANE.green() : Blocks.STAINED_GLASS_PANE.lightBlue();
        return pane.defaultBlockState();
    }

    // --- the council's hall ---------------------------------------------------------------------------------------

    private static void hallColumn(WorldGenLevel level, Capital.Site s, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        Frame f = hall(s);
        double[] ab = f.local(x, z);
        double a = ab[0];
        double b = ab[1];
        int base = f.level();
        RandomSource random = RandomSource.create(Town.hash(s.seed(), x, z, 362));
        CapitalDraw.foundation(level, s, x, z, base, box, at);
        boolean clock = a < 7 && Math.abs(b) < 3.6;
        double in = Math.min(Math.min(a + 0.5, HALL_D - 0.5 - a), HALL_W + 0.5 - Math.abs(b));
        boolean wall = in < 1.2;
        int storey = 5;
        int wallTop = base + (clock ? 34 : storey * 3);
        boolean arcade = a < 1.2 && !clock;                                     // the open arches onto the square
        for (int y = base; y <= wallTop; y++) {
            int h = y - base;
            BlockState state;
            if (clock) {
                double inC = Math.min(Math.min(a + 0.5, 6.5 - a), 3.5 - Math.abs(b));
                boolean shell = inC < 1.2;
                if (shell) {
                    boolean face = a < 1.2 && h >= 26 && h <= 30 && Math.abs(b) < 2.6;
                    boolean door = a < 1.2 && h >= 1 && h <= 3 && Math.abs(b) < 1.1;
                    state = face ? (Math.abs(b) < 0.6 && h == 28 ? Blocks.GOLD_BLOCK.defaultBlockState()
                            : Blocks.QUARTZ_BLOCK.defaultBlockState()) : door ? Blocks.AIR.defaultBlockState() : stone(s, x, y, z);
                } else if (a == 5 && b == 0 && h >= 1 && h <= 30) {
                    // a ladder up the tower's back to the clock's floor
                    state = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front());
                } else {
                    state = h % 6 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : h == 31 && a == 3 && b == 0 ? Blocks.BELL.defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                }
            } else if (wall) {
                if (arcade && h >= 1 && h <= 3 && Math.floorMod((int) Math.round(b), 4) != 0) {
                    state = h == 3 ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP) : Blocks.AIR.defaultBlockState();
                } else if (h >= storey + 1 && h % storey >= 2 && h % storey <= 3 && Math.floorMod((int) Math.round(a + b), 3) != 0) {
                    boolean endWall = Math.min(a + 0.5, HALL_D - 0.5 - a) < HALL_W + 0.5 - Math.abs(b);
                    state = Math.floorMod(Town.hash(s.seed(), x, y, z), 3L) == 0
                            ? CapitalDraw.along(Blocks.STAINED_GLASS_PANE.gray().defaultBlockState(), endWall ? f.front().getClockWise().getAxis() : f.front().getAxis())
                            : Blocks.AIR.defaultBlockState();
                } else {
                    state = h % storey == 0 && h > 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : stone(s, x, y, z);
                }
            } else if (hallLadder(a, b) && h >= 1 && h <= storey * 2) {
                // the way up to the chamber and the archive: a ladder in the back corner
                state = Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front().getClockWise());
            } else if (h % storey == 0) {
                state = (h == 0 ? Blocks.STONE_BRICKS : Blocks.DARK_OAK_PLANKS).defaultBlockState();
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (!wall && !clock && !hallLadder(a, b)) {
            CapitalStory.hallRooms(level, s, x, z, a, b, base, storey, box, at, random);
        }
        // the roof: hipped over the hall, a pyramid over the clock
        double inR = clock ? Math.min(Math.min(a + 0.5, 6.5 - a), 3.5 - Math.abs(b)) : in;
        int rise = (int) Math.floor(Math.max(0, inR) * (clock ? 2.4 : 1.0));
        int outer = (int) Math.floor(Math.max(0, inR - 1) * (clock ? 2.4 : 1.0));
        for (int y = wallTop + 1; y < wallTop + 1 + rise; y++) {
            set(level, box, at.set(x, y, z), (y > wallTop + 1 + outer ? (clock ? Blocks.DEEPSLATE_TILES : Blocks.BRICKS) : Blocks.AIR).defaultBlockState());
        }
        set(level, box, at.set(x, wallTop + 1 + rise, z), (clock ? Blocks.DEEPSLATE_TILES : Blocks.BRICKS).defaultBlockState());
    }

    /** The hall's ladder's column: the back corner on the -across side, against the side wall. */
    static boolean hallLadder(double a, double b) {
        return a == HALL_D - 2 && b == -(HALL_W - 1);
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalLandmarks() {}
}
