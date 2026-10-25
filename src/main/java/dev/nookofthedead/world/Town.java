package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * The town that fills the fallen world (PLAN.md, the user 2026-10-12: "基本的には全部町…建造物で埋め尽くされている"; BUILDING.md:
 * lots of 16, buildings in multiples of them, streets made by the mod), laid out on a grid the seed fixes, so each chunk can
 * draw its own part of it without the rest.
 * <p>
 * Blocks of about {@link #BLOCK}, a street {@link #STREET} wide between each two. The streets are not ruled straight (the
 * user, 2026-10-13: "道路も直線ばかりじゃなくて、こんなふうにジグザクしてたり、多少起伏があったり"): a street stands up to
 * {@link #JOG} off its line, somewhere else at each crossing and in each third of the way between, stepping across from
 * one to the next, so each street runs on in short dog-legs; the houses keep to it, each built up to the street where its own stretch is, so the fronts
 * step in and out with it, and what is left between a front and the street is paved as the street is. Not every street
 * goes through ("ちょっと碁盤の目すぎるよね"): every {@link #THROUGH}th line runs the whole way, but on the others a stretch
 * may be only an alley - a footpath in the weeds - so streets end at T junctions ({@link District#alleys}).
 * <p>
 * The ground rises and falls slowly, up to {@link #HILL} about {@link #SURFACE} over several hundred blocks ({@link #height};
 * the user, 2026-10-13: "もっと広い範囲でゆったりと高さが変わるイメージ"). The streets follow it in half steps (slabs); each
 * building's lot is levelled to the street before it, so its door is at the street's height; the open ground between lots
 * and under yards meets each lot at its level and steps where it wanders, not along a lot's edge ({@link #between});
 * where a lot stands two or more above what is beside it, a wall of old stone holds it up.
 * <p>
 * Inside its streets a block is built right up to them, the buildings back to back (the user: "背中合わせで詰めてほしいし、
 * もうちょっと乱れた配置の区画も作って欲しい"), in one of three ways, by its quarter:
 * <ul>
 * <li>two rows back to back, each facing out to the street on its side: S buildings (32) and XS ones (16) in whatever
 * order the seed picks, so the two rows' lots do not line up; behind an XS is a yard, or at a row's end another XS
 * facing the side street, and a building at a row's end may face the side street itself; where a block is too shallow
 * for two S rows, the far row's houses are little ones;</li>
 * <li>a ring of XS round a court ({@link #COURT}): a fountain, a little park, or weeds;</li>
 * <li>one M building (64), facing a street.</li>
 * </ul>
 * A lot with no building is rubble, a dead field or a yard gone to weeds. Each building is turned to face a street (its
 * north side, as every one is built), mirrored or not by the seed (the user: "毎回、向き・反転…を変えて、同じ建物でも同じに
 * 見えにくくする"), a few panes broken and stones cracked as it goes in ({@link #WEAR}); its marker signs become what they
 * mark ({@link Markers}). The buildings are taken out of the build world with {@code /nzw export} from y -63 (two layers
 * under its grass, so wells and ponds keep their depth): the user's own (S1, S2) and the AI's drafts (S3-S11, M1-M8,
 * XS1-XS5) until the user's own replace them.
 */
public final class Town {
    /** The grass's height on level ground: the town's ground rises and falls about it ({@link #height}). */
    public static final int SURFACE = 63;
    /**
     * A block with half the street round it: the streets' lines are at its multiples, the streets about them. Room for two
     * S rows back to back, or a ring or an M building, even where the streets step in on it.
     */
    public static final int BLOCK = 80;
    public static final int STREET = 6;
    /** Of the street's six, how many fall on each side of where it runs. */
    private static final int HALF = STREET / 2;
    /**
     * A main street of the town's own region (one of the lines that go the whole way): ten wide (the user, 2026-10-15: "中央の
     * 道は広めで"), five each side.
     */
    public static final int WIDE_HALF = 5;
    /** How far a street may stand off its line, either way. */
    public static final int JOG = 5;
    /** A stretch between two crossings bends through this many places, each off its line by its own amount ({@link #course}). */
    private static final int PIECES = 3;
    /** How far the ground may rise or fall from {@link #SURFACE}. */
    public static final int HILL = 7;
    /** How far a lot's level reaches into the open ground round it ({@link #between}); past it, the ground's own height. */
    private static final int REACH = 12;
    /** How far a street's height reaches into the open ground beside it. */
    private static final int VERGE = 6;
    /** How far round a column of open ground its height is evened out ({@link #smoothed}). */
    private static final int SMOOTH = 2;
    /** An S lot's size, an M lot's, an XS lot's: the build world's plots, built up to the street. */
    public static final int S_LOT = 32;
    public static final int M_LOT = 64;
    public static final int XS_LOT = 16;
    /** A ring of XS lots round a court, across: two S rows' depth; and the court in it. */
    public static final int RING = 2 * S_LOT + 2;
    public static final int COURT = RING - 2 * XS_LOT;
    /** Every this many street lines, one goes the whole way through, never an alley. */
    public static final int THROUGH = 4;
    /** The research institute's building, and the deep part set under it ({@link Institute}). */
    public static final Identifier INSTITUTE = dev.nookofthedead.NookOfTheDead.id("town/institute");
    public static final Identifier INSTITUTE_DEEP = dev.nookofthedead.NookOfTheDead.id("town/institute_deep");

    /** How far under its own grass a template starts (taken out from y -63, the build world's grass at -61). */
    public static final int BELOW = 2;

    /** A ruined portal's lava, banked down to magma: a town of timber would burn to the ground. */
    private static final RuleProcessor BANKED = new RuleProcessor(List.of(new ProcessorRule(
            new net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest(Blocks.LAVA), AlwaysTrueTest.INSTANCE,
            Blocks.MAGMA_BLOCK.defaultBlockState())));

    /** Ruin's touch on every building: some glass gone, some stone cracked or mossed - a little different each time. */
    static final RuleProcessor WEAR = new RuleProcessor(List.of(
            new ProcessorRule(new RandomBlockMatchTest(Blocks.GLASS_PANE, 0.35F), AlwaysTrueTest.INSTANCE, Blocks.AIR.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.GLASS, 0.3F), AlwaysTrueTest.INSTANCE, Blocks.AIR.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.STONE_BRICKS, 0.15F), AlwaysTrueTest.INSTANCE, Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),
            new ProcessorRule(new RandomBlockMatchTest(Blocks.COBBLESTONE, 0.12F), AlwaysTrueTest.INSTANCE, Blocks.MOSSY_COBBLESTONE.defaultBlockState())));

    /** What a lot holds. */
    public enum Use { BUILDING, RUBBLE, FIELD, YARD }

    /** What a column of the town is: street, an alley where a street does not go through, or a block's lots. */
    public enum Ground { STREET, ALLEY, LOT }

    /**
     * One lot: where its north-west corner is (its ground's height as y: levelled to the street before it), how big, what
     * is on it, and how a building on it is turned.
     */
    public record Lot(BlockPos corner, int size, Use use, Optional<Identifier> building, Rotation rotation, Mirror mirror, long seed) {
        static Lot of(BlockPos corner, int size, Use use, Optional<Identifier> building, long h) {
            return new Lot(corner, size, use, building, Rotation.values()[(int) Math.floorMod(h >> 16, 4L)],
                    ((h >> 20) & 1) == 0 ? Mirror.NONE : Mirror.LEFT_RIGHT, h);
        }

        /**
         * A lot whose building's front - the north side, as every draft is built; for the game's village houses where their
         * entrance jigsaw is ({@link Vanilla#entrance}) - ends up facing {@code front}, mirrored or not by chance.
         */
        static Lot facing(BlockPos corner, int size, Use use, Optional<Identifier> building, long h, Direction front) {
            Mirror mirror = ((h >> 20) & 1) == 0 ? Mirror.NONE : Mirror.LEFT_RIGHT;
            Direction mirrored = mirror.mirror(built(building));
            for (Rotation rotation : Rotation.values()) {
                if (rotation.rotate(mirrored) == front) {
                    return new Lot(corner, size, use, building, rotation, mirror, h);
                }
            }
            return of(corner, size, use, building, h);
        }

        /** Which way the building's front (its north side as built; the game's houses, their west) faces once placed. */
        public Direction front() {
            return this.rotation.rotate(this.mirror.mirror(built(this.building)));
        }

        private static Direction built(Optional<Identifier> building) {
            return building.filter(Vanilla::village).map(Vanilla::entrance).orElse(Direction.NORTH);
        }

        public BoundingBox footprint() {
            return new BoundingBox(this.corner.getX(), this.corner.getY() - 8, this.corner.getZ(), this.corner.getX() + this.size - 1,
                    this.corner.getY() + 96, this.corner.getZ() + this.size - 1);
        }

        public boolean covers(int x, int z) {
            return x >= this.corner.getX() && x < this.corner.getX() + this.size && z >= this.corner.getZ() && z < this.corner.getZ() + this.size;
        }
    }

    // --- the ground's height ---------------------------------------------------------------------------------------------

    /**
     * The town's ground at ({@code x}, {@code z}) before it is levelled: broad rises and falls some five hundred blocks
     * across with gentler swells of two hundred on them, at most {@link #HILL} from {@link #SURFACE} - never more than a
     * block in sixteen, mostly far less; nothing smaller (the user, 2026-10-13: the little ups and downs read as the
     * generator's, a whole block's worth along a lot's straight edge).
     */
    public static double height(long seed, int x, int z) {
        double ground = rolling(seed, x, z) + Terrain.lift(seed, x, z);
        Terrain.Sea sea = Terrain.sea(seed, x, z);
        return sea == null ? ground : sea.shape(x, z, ground);             // a sea's bed, its shore eased down to it
    }

    /** The ground as it would be with no sea or mountain there: what a sea's water is set by ({@link Terrain.Sea}). */
    static double groundBefore(long seed, int x, int z) {
        return rolling(seed, x, z) + Terrain.hills(seed, x, z);
    }

    private static double rolling(long seed, int x, int z) {
        double broad = noise(seed, x, z, 520.0, 70) - 0.5;
        double swell = noise(seed, x, z, 210.0, 71) - 0.5;
        return SURFACE + Math.max(-HILL, Math.min(HILL, HILL * (broad * 1.7 + swell * 0.5)));
    }

    /** A street's height at a column: the ground's, to the half block (the halves are slabs). */
    public static double streetHeight(long seed, int x, int z) {
        return Math.round(height(seed, x, z) * 2) / 2.0;
    }

    /** The level a lot of {@code size} at {@code x}, {@code z} is set at: the street's just before its front, to the block. */
    static int level(long seed, int x, int z, int size, Direction front) {
        int fx = x + size / 2 + front.getStepX() * (size / 2 + 1);
        int fz = z + size / 2 + front.getStepZ() * (size / 2 + 1);
        return (int) Math.round(height(seed, fx, fz));
    }

    // --- the streets -----------------------------------------------------------------------------------------------------

    /**
     * Whether the street on a line of the grid is open beside one block, or only an alley there. {@code northSouth}: the
     * line x = {@code line} * {@link #BLOCK}, beside the blocks of row {@code along}; else z = {@code line} * BLOCK, beside
     * column {@code along}.
     */
    public static boolean open(long seed, boolean northSouth, int line, int along) {
        if (through(seed, northSouth, line)) {
            return true;                                                // a street that goes the whole way
        }
        District district = northSouth ? District.of(seed, line, along) : District.of(seed, along, line);
        return Math.floorMod(hash(seed, line, along, northSouth ? 30 : 31), 1000) >= district.alleys * 1000;
    }

    /** Whether the street on a grid line goes the whole way (every {@link #THROUGH}th). */
    public static boolean through(long seed, boolean northSouth, int line) {
        int offset = (int) Math.floorMod(hash(seed, northSouth ? 1 : 2, 0, 33), (long) THROUGH);
        return Math.floorMod(line + offset, THROUGH) == 0;
    }

    /**
     * How many of a street's width fall each side of where it runs, beside block {@code along}: a main street of the town
     * ({@link #through}, the town's region both sides) {@link #WIDE_HALF}, any other {@link #HALF}.
     */
    public static int half(long seed, boolean northSouth, int line, int along) {
        if (!through(seed, northSouth, line)) {
            return HALF;
        }
        Region a = northSouth ? Region.of(seed, line - 1, along) : Region.of(seed, along, line - 1);
        Region b = northSouth ? Region.of(seed, line, along) : Region.of(seed, along, line);
        return a == Region.CITY && b == Region.CITY ? WIDE_HALF : HALF;
    }

    /**
     * What a grid line is beside block {@code along}: in the open country (the wilds, the farmland, both sides) only the
     * lines that go the whole way are anything - a lane across the farmland, a trail through the wilds - and the rest are
     * not there at all; elsewhere a street, or an alley where it does not go through.
     */
    public static Ground line(long seed, boolean northSouth, int line, int along) {
        Region a = northSouth ? Region.of(seed, line - 1, along) : Region.of(seed, along, line - 1);
        Region b = northSouth ? Region.of(seed, line, along) : Region.of(seed, along, line);
        if (rural(a) && rural(b)) {
            if (!through(seed, northSouth, line)) {
                return Ground.LOT;
            }
            return a == Region.WILDS || b == Region.WILDS ? Ground.ALLEY : Ground.STREET;
        }
        if (open(seed, northSouth, line, along)) {
            return Ground.STREET;
        }
        return gone(seed, northSouth, line, along) ? Ground.LOT : Ground.ALLEY;
    }

    /**
     * Whether ({@code x}, {@code z}) is on the trodden way down the middle of a stretch where no street runs ({@link #gone}):
     * a footpath where the alley was, two wide, wandering with the line it follows.
     */
    public static boolean track(long seed, int x, int z) {
        int i = Math.floorDiv(x + WIDE_HALF + JOG, BLOCK);
        int[] v = band(seed, true, i, z);
        int bz = Math.floorDiv(z, BLOCK);
        if (x >= v[0] && x < v[1] && Math.abs(2 * x + 1 - v[0] - v[1]) <= 2 && line(seed, true, i, bz) == Ground.LOT) {
            return !rural(Region.of(seed, i - 1, bz)) || !rural(Region.of(seed, i, bz));
        }
        int k = Math.floorDiv(z + WIDE_HALF + JOG, BLOCK);
        int[] h = band(seed, false, k, x);
        int bx = Math.floorDiv(x, BLOCK);
        return z >= h[0] && z < h[1] && Math.abs(2 * z + 1 - h[0] - h[1]) <= 2 && line(seed, false, k, bx) == Ground.LOT
                && (!rural(Region.of(seed, bx, k - 1)) || !rural(Region.of(seed, bx, k)));
    }

    /**
     * Whether a stretch that is no street is not even an alley: nothing runs there, and the blocks either side of it are
     * one, their backs to the open ground between (the user, 2026-10-17: the blocks read too plainly as a grid - "区画が碁盤
     * の目のようにはっきり分かれすぎている"). Half the alleys of a quarter, the old town's and the centre's fewer: kept close.
     */
    public static boolean gone(long seed, boolean northSouth, int line, int along) {
        District district = northSouth ? District.of(seed, line, along) : District.of(seed, along, line);
        long share = district == District.OLD_TOWN || district == District.CENTRE ? 300 : 550;
        return Math.floorMod(hash(seed, line, along, northSouth ? 34 : 35), 1000) < share;
    }

    // --- the squares -----------------------------------------------------------------------------------------------------

    /**
     * Where the streets meet at crossing ({@code i}, {@code k}) - line {@code i} north-south, {@code k} west-east - opened
     * out now and then into a square: a rough round of paving a dozen blocks or so across, its edge wandering, the houses
     * that stood there gone. Its middle and how far it reaches, or null for a plain crossing. Never in the open country,
     * nor where the institute's grounds come near.
     */
    public static double[] square(long seed, int i, int k) {
        double[] sq = SQUARES.get(seed, i, k, () -> squareAt(seed, i, k));
        return sq.length == 0 ? null : sq;
    }

    /** The squares worked out so far, by crossing ({@link #NO_SQUARE} for a plain one): ground() asks at every column. */
    private static final BlockCache.Of<double[]> SQUARES = new BlockCache.Of<>(12);
    private static final double[] NO_SQUARE = new double[0];

    private static double[] squareAt(long seed, int i, int k) {
        long h = hash(seed, i, k, 140);
        if (Math.floorMod(h, 1000) >= 300) {
            return NO_SQUARE;                                           // most crossings, before asking anything more
        }
        District district = District.of(seed, i, k);
        double chance = switch (district) {
            case CENTRE -> 0.2;
            case RESIDENTIAL, SETTLEMENT -> 0.15;
            case OLD_TOWN -> 0.12;
            case OUTSKIRTS -> 0.06;
            case WASTE -> 0.04;
            default -> 0.0;
        };
        if (Math.floorMod(h, 1000) >= chance * 1000) {
            return NO_SQUARE;
        }
        for (int bx = i - 1; bx <= i; bx++) {
            for (int bz = k - 1; bz <= k; bz++) {
                if (rural(Region.of(seed, bx, bz)) || Institute.at(seed, bx, bz)) {
                    return NO_SQUARE;
                }
            }
        }
        double cx = i * BLOCK + offset(seed, true, i, k) + 0.5;
        double cz = k * BLOCK + offset(seed, false, k, i) + 0.5;
        double[] sq = {cx, cz, 8 + Math.floorMod(h >> 12, 5)};
        // a big building at the corner stays, and the crossing stays plain: no square takes an M building's lot
        for (int bx = i - 1; bx <= i; bx++) {
            for (int bz = k - 1; bz <= k; bz++) {
                for (Lot lot : layOut(seed, bx, bz)) {
                    if (lot.size() == M_LOT && reaches(sq, lot)) {
                        return NO_SQUARE;
                    }
                }
            }
        }
        return sq;
    }

    /** Whether {@code lot} reaches into square {@code sq} (its middle and reach), with a margin for its wandering edge. */
    private static boolean reaches(double[] sq, Lot lot) {
        double dx = Math.max(0, Math.max(lot.corner().getX() - sq[0], sq[0] - lot.corner().getX() - lot.size()));
        double dz = Math.max(0, Math.max(lot.corner().getZ() - sq[1], sq[1] - lot.corner().getZ() - lot.size()));
        return Math.hypot(dx, dz) < sq[2] + 2.0;
    }

    /** How far inside the square at the crossing nearest ({@code x}, {@code z}) the column is: below 0, outside, or none there. */
    static double inSquare(long seed, int x, int z) {
        int i = Math.round((float) x / BLOCK);
        int k = Math.round((float) z / BLOCK);
        double[] sq = square(seed, i, k);
        if (sq == null) {
            return -1;
        }
        double d = Math.hypot(x + 0.5 - sq[0], z + 0.5 - sq[1]);
        double edge = sq[2] + (noise(seed, x, z, 5.0, 141) - 0.5) * 5.0;
        return edge - d;
    }

    /** The open country: no town grid of streets. */
    public static boolean rural(Region region) {
        return region == Region.WILDS || region == Region.FARMLAND;
    }

    /** How far the street on a grid line stands off it where it crosses line {@code at} of the other way, either way. */
    public static int offset(long seed, boolean northSouth, int line, int at) {
        return (int) Math.floorMod(hash(seed, line, at, northSouth ? 60 : 61), (long) (2 * JOG + 1)) - JOG;
    }

    /**
     * Where the street of a grid line is where it passes {@code along} (z for a north-south line): from its first column to
     * just past its last, across, as it bends along its {@link #course}.
     */
    public static int[] band(long seed, boolean northSouth, int line, int along) {
        int at = Math.floorDiv(along, BLOCK);
        int rel = along - at * BLOCK;
        int w = half(seed, northSouth, line, at);
        int o = (int) Math.round(course(seed, northSouth, line, at, rel));
        return new int[] {line * BLOCK + o - w, line * BLOCK + o + w};
    }

    /**
     * Where a street runs off its line, {@code rel} blocks past crossing {@code at}: it bends from where it stands at one
     * crossing to where it stands halfway and on to the next, and wanders a little as it goes (the user, 2026-10-17: the
     * blocks read as a grid, the streets too plain - "うねうねしていたり"). Level at each crossing, so the streets meet
     * there as they always have; never past {@link #JOG} either way.
     */
    private static double course(long seed, boolean northSouth, int line, int at, int rel) {
        double half = BLOCK / 2.0;
        int piece = rel < half ? 0 : 1;
        double from = standing(seed, northSouth, line, at, piece == 0 ? 0 : 1);
        double to = standing(seed, northSouth, line, at, piece == 0 ? 1 : PIECES - 1);
        double t = smooth((rel - piece * half) / half);
        // a wander along it, nothing at the crossings
        double wander = (noise(seed, line * 7919 + (northSouth ? 0 : 3571), rel + at * BLOCK, 23.0, northSouth ? 64 : 65) - 0.5)
                * 3.0 * Math.sin(Math.PI * rel / BLOCK);
        return Math.max(-JOG, Math.min(JOG, from + (to - from) * t + wander));
    }

    /** How far piece {@code piece} of the stretch after crossing {@code at} stands off its line: the crossings' own at the ends. */
    private static int standing(long seed, boolean northSouth, int line, int at, int piece) {
        if (piece == 0) {
            return offset(seed, northSouth, line, at);
        }
        if (piece == PIECES - 1) {
            return offset(seed, northSouth, line, at + 1);
        }
        return (int) Math.floorMod(hash(seed, line, at * 8L + piece, northSouth ? 62 : 63), (long) (2 * JOG + 1)) - JOG;
    }

    /** What column ({@code x}, {@code z}) is: a crossing is street if any of the four ways into it is. */
    public static Ground ground(long seed, int x, int z) {
        if (inSquare(seed, x, z) >= 0) {
            return Ground.STREET;
        }
        int i = Math.floorDiv(x + WIDE_HALF + JOG, BLOCK);
        int[] v = band(seed, true, i, z);
        boolean onV = x >= v[0] && x < v[1];
        int k = Math.floorDiv(z + WIDE_HALF + JOG, BLOCK);
        int[] h = band(seed, false, k, x);
        boolean onH = z >= h[0] && z < h[1];
        if (!onV && !onH) {
            return Ground.LOT;
        }
        if (onV && onH) {                                               // a crossing: the most of what meets there
            Ground most = Ground.LOT;
            for (Ground g : new Ground[] {line(seed, true, i, k - 1), line(seed, true, i, k), line(seed, false, k, i - 1), line(seed, false, k, i)}) {
                if (g == Ground.STREET || g == Ground.ALLEY && most == Ground.LOT) {
                    most = g;
                }
            }
            return most;
        }
        return onV ? line(seed, true, i, Math.floorDiv(z, BLOCK)) : line(seed, false, k, Math.floorDiv(x, BLOCK));
    }

    public static boolean street(long seed, int x, int z) {
        return ground(seed, x, z) == Ground.STREET;
    }

    /**
     * How far into its street column ({@code x}, {@code z}) is: 0 at its edge, more toward the middle (at a crossing, the
     * deeper of the two ways); and the street's half width, after it. Off every street, {1, 3}: a forecourt is paved as a
     * street's near edge.
     */
    static int[] margin(long seed, int x, int z) {
        int i = Math.floorDiv(x + WIDE_HALF + JOG, BLOCK);
        int[] v = band(seed, true, i, z);
        int k = Math.floorDiv(z + WIDE_HALF + JOG, BLOCK);
        int[] h = band(seed, false, k, x);
        int m = -1;
        int w = HALF;
        if (x >= v[0] && x < v[1]) {
            m = Math.min(x - v[0], v[1] - 1 - x);
            w = (v[1] - v[0]) / 2;
        }
        if (z >= h[0] && z < h[1]) {
            int mh = Math.min(z - h[0], h[1] - 1 - z);
            if (mh > m) {
                m = mh;
                w = (h[1] - h[0]) / 2;
            }
        }
        double sq = inSquare(seed, x, z);
        if (sq >= 0 && (int) sq > m) {
            m = (int) sq;
            w = Math.max(w, m + 1);
        }
        return m < 0 ? new int[] {1, HALF} : new int[] {m, w};
    }

    // --- the lots --------------------------------------------------------------------------------------------------------

    /**
     * A block as its layout sees it: {@code u} along its rows, {@code v} across them (world coordinates, x and z or z and
     * x), which way each side faces in the world - the rows' fronts ({@code near} toward lower v, {@code far}) and their
     * ends ({@code start} toward lower u, {@code end}) - and where its streets end on each side.
     */
    private record Frame(long seed, int bx, int bz, boolean alongX, Direction near, Direction far, Direction start, Direction end) {
        int x(int u, int v) {
            return this.alongX ? u : v;
        }

        int z(int u, int v) {
            return this.alongX ? v : u;
        }

        /** The block's lines on the u axis and the v axis (where its streets run on the lower side). */
        int u0() {
            return (this.alongX ? this.bx : this.bz) * BLOCK;
        }

        int v0() {
            return (this.alongX ? this.bz : this.bx) * BLOCK;
        }

        /** The first v past the near street at {@code u}; the far street's first v; the start street's first u past it; the end's. */
        int near(int u) {
            return this.alongX ? band(this.seed, false, this.bz, u)[1] : band(this.seed, true, this.bx, u)[1];
        }

        int far(int u) {
            return this.alongX ? band(this.seed, false, this.bz + 1, u)[0] : band(this.seed, true, this.bx + 1, u)[0];
        }

        int start(int v) {
            return this.alongX ? band(this.seed, true, this.bx, v)[1] : band(this.seed, false, this.bz, v)[1];
        }

        int end(int v) {
            return this.alongX ? band(this.seed, true, this.bx + 1, v)[0] : band(this.seed, false, this.bz + 1, v)[0];
        }

        int maxNear(int u0, int u1) {
            int m = Integer.MIN_VALUE;
            for (int u = u0; u < u1; u++) {
                m = Math.max(m, near(u));
            }
            return m;
        }

        int minFar(int u0, int u1) {
            int m = Integer.MAX_VALUE;
            for (int u = u0; u < u1; u++) {
                m = Math.min(m, far(u));
            }
            return m;
        }

        int maxStart(int v0, int v1) {
            int m = Integer.MIN_VALUE;
            for (int v = v0; v < v1; v++) {
                m = Math.max(m, start(v));
            }
            return m;
        }

        int minEnd(int v0, int v1) {
            int m = Integer.MAX_VALUE;
            for (int v = v0; v < v1; v++) {
                m = Math.min(m, end(v));
            }
            return m;
        }
    }

    /** The lots of block ({@code bx}, {@code bz}) in a world of {@code seed}, laid out as its quarter has it. */
    public static List<Lot> lots(long seed, int bx, int bz) {
        // keyed by the buildings' generation too: a layout begun before a reload is never taken for one after it
        return LOTS.get(seed + District.generation() * 0x9E3779B97F4A7C15L, bx, bz,
                () -> List.copyOf(varied(seed, bx, bz, unsquared(seed, bx, bz, layOut(seed, bx, bz))).stream()
                        .filter(lot -> !Ascent.near(seed, lot.corner().getX(), lot.corner().getZ(), lot.size()))
                        .filter(lot -> Terrain.free(seed, lot.corner().getX(), lot.corner().getZ(), lot.size()))
                        .filter(lot -> !Capital.near(seed, lot.corner().getX(), lot.corner().getZ(), lot.size(), 2)).toList()));
    }

    /** What {@link #lots} has laid out: each block's are asked for by every chunk round it. */
    private static final BlockCache.Of<List<Lot>> LOTS = new BlockCache.Of<>(12);

    /** Forgets the lots laid out so far: the buildings to choose from have changed ({@link Buildings}). */
    public static void forget() {
        LOTS.clear();
    }

    /**
     * {@code lots} with no building twice in one block where the quarter has others to give (the user, 2026-10-16: "同じ
     * 建物ばかり繰り返し出てくる地域…"): a second of the same is chosen again, a few times, from the same list for its size.
     */
    private static List<Lot> varied(long seed, int bx, int bz, List<Lot> lots) {
        if (lots.isEmpty()) {
            return lots;
        }
        District district = District.of(seed, bx, bz);
        java.util.Set<Identifier> used = new java.util.HashSet<>();
        List<Lot> out = new ArrayList<>(lots.size());
        for (Lot lot : lots) {
            Identifier id = lot.building().orElse(null);
            if (id == null || lot.use() != Use.BUILDING || Vanilla.is(id) || id.equals(INSTITUTE) || used.add(id)) {
                out.add(lot);
                continue;
            }
            List<District.Weighted> list = lot.size() == XS_LOT ? district.xsBuildings : lot.size() == S_LOT ? district.sBuildings
                    : lot.size() == M_LOT ? district.mBuildings : lot.size() == COURT ? district.courts : List.of();
            Identifier other = id;
            for (int k = 1; k <= 6 && !list.isEmpty(); k++) {
                Identifier pick = District.pick(list, hash(lot.seed(), k, 0, 170));
                if (!used.contains(pick)) {
                    other = pick;
                    break;
                }
            }
            used.add(other);
            // turned anew to face the same way: a house of the game's is built facing another way than ours (the desert's
            // lists have both)
            out.add(Lot.facing(lot.corner(), lot.size(), lot.use(), Optional.of(other), lot.seed(), lot.front()));
        }
        return out;
    }

    private static List<Lot> layOut(long seed, int bx, int bz) {
        if (Terrain.lake(seed, bx, bz) != null || Terrain.cave(seed, bx, bz) != null) {
            return List.of();                                           // the lake's or the cave's block: nothing built
        }
        if (Vanilla.villageAt(seed, bx, bz)) {                          // a zombie village of the game's own houses
            return Vanilla.village(seed, bx, bz, (x, z) -> (int) Math.round(height(seed, x, z)));
        }
        District district = District.of(seed, bx, bz);
        long h = hash(seed, bx, bz, 1);
        if (district == District.WILD) {
            return lone(seed, bx, bz, district, h);
        }
        if (Institute.at(seed, bx, bz)) {                                // the research institute: the whole block its grounds
            Set<Direction> ways = EnumSet.noneOf(Direction.class);
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                boolean ns = d.getAxis() == Direction.Axis.X;
                int line = d == Direction.NORTH ? bz : d == Direction.SOUTH ? bz + 1 : d == Direction.WEST ? bx : bx + 1;
                if (open(seed, ns, line, ns ? bz : bx)) {
                    ways.add(d);
                }
            }
            Direction front = either(ways, h >> 28, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
            int x = bx * BLOCK + (BLOCK - M_LOT) / 2;
            int z = bz * BLOCK + (BLOCK - M_LOT) / 2;
            return List.of(Lot.facing(corner(seed, x, z, M_LOT, front), M_LOT, Use.BUILDING, Optional.of(INSTITUTE), h, front));
        }
        Set<Direction> open = EnumSet.noneOf(Direction.class);
        if (open(seed, false, bz, bx)) {
            open.add(Direction.NORTH);
        }
        if (open(seed, false, bz + 1, bx)) {
            open.add(Direction.SOUTH);
        }
        if (open(seed, true, bx, bz)) {
            open.add(Direction.WEST);
        }
        if (open(seed, true, bx + 1, bz)) {
            open.add(Direction.EAST);
        }
        // the rows face the way more streets are open, either if as many
        int northSouth = (open.contains(Direction.NORTH) ? 1 : 0) + (open.contains(Direction.SOUTH) ? 1 : 0);
        int westEast = (open.contains(Direction.WEST) ? 1 : 0) + (open.contains(Direction.EAST) ? 1 : 0);
        boolean alongX = northSouth != westEast ? northSouth > westEast : ((h >> 40) & 1) == 0;
        // the rows' fronts not onto a stretch where nothing runs, if the other way round has none
        boolean goneNS = gone(seed, false, bz, bx) && !open.contains(Direction.NORTH) || gone(seed, false, bz + 1, bx) && !open.contains(Direction.SOUTH);
        boolean goneWE = gone(seed, true, bx, bz) && !open.contains(Direction.WEST) || gone(seed, true, bx + 1, bz) && !open.contains(Direction.EAST);
        if (goneNS != goneWE) {
            alongX = goneWE;
        }
        Frame f = alongX ? new Frame(seed, bx, bz, true, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
                : new Frame(seed, bx, bz, false, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH);
        // the block clear of all four streets wherever they step: for the layouts that need a square
        int uLo = f.maxStart(f.v0(), f.v0() + BLOCK);
        int uHi = f.minEnd(f.v0(), f.v0() + BLOCK);
        int vLo = f.maxNear(f.u0(), f.u0() + BLOCK);
        int vHi = f.minFar(f.u0(), f.u0() + BLOCK);
        boolean square = uHi - uLo >= RING && vHi - vLo >= RING;
        boolean roomy = uHi - uLo >= M_LOT && vHi - vLo >= M_LOT;
        List<Lot> lots = new ArrayList<>();
        double roll = Math.floorMod(h, 1000) / 1000.0;
        if (roomy && roll < district.medium) {
            int u = uLo + (uHi - uLo - M_LOT) / 2;
            int v = vLo + (vHi - vLo - M_LOT) / 2;
            Direction front = either(open, h >> 28, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
            List<District.Weighted> list = Math.floorMod(h >> 44, 1000) < District.RARE_M * 1000 ? district.rareM : district.mBuildings;
            lots.add(Lot.facing(corner(seed, f.x(u, v), f.z(u, v), M_LOT, front), M_LOT, Use.BUILDING,
                    Optional.of(District.pick(list, h >> 8)), h, front));
        } else if (square && roll < district.medium + district.ring) {
            ring(lots, f, district, open, h, uLo + (uHi - uLo - RING) / 2, vLo + (vHi - vLo - RING) / 2);
        } else {
            List<int[]> taken = new ArrayList<>();
            row(lots, taken, f, district, open, hash(h, 1, 0, 5), true, uLo, uHi);
            row(lots, taken, f, district, open, hash(h, 2, 0, 5), false, uLo, uHi);
        }
        return lots;
    }

    /** {@code lots} of block ({@code bx}, {@code bz}) less what stood where a square opened out at one of its corners. */
    private static List<Lot> unsquared(long seed, int bx, int bz, List<Lot> lots) {
        List<Lot> out = new ArrayList<>(lots);
        for (int i = bx; i <= bx + 1; i++) {
            for (int k = bz; k <= bz + 1; k++) {
                double[] sq = square(seed, i, k);
                if (sq != null) {
                    out.removeIf(lot -> lot.size() != COURT && reaches(sq, lot));
                }
            }
        }
        return out;
    }

    /**
     * A block of the wilds: now and then one small thing standing alone in the middle of it - a camp, a cabin - and
     * otherwise nothing built ({@link District#WILD}'s {@code yard} of the time).
     */
    private static List<Lot> lone(long seed, int bx, int bz, District district, long h) {
        if (Math.floorMod(h >> 8, 1000) < district.yard * 1000) {
            return List.of();
        }
        boolean small = Math.floorMod(h >> 18, 1000) < district.small * 1000;
        int size = small ? XS_LOT : S_LOT;
        List<District.Weighted> list = small ? district.xsBuildings : district.sBuildings;
        int x = bx * BLOCK + (BLOCK - size) / 2;
        int z = bz * BLOCK + (BLOCK - size) / 2;
        Direction front = Direction.from2DDataValue((int) Math.floorMod(h >> 28, 4L));
        return List.of(Lot.facing(corner(seed, x, z, size, front), size, Use.BUILDING, Optional.of(District.pick(list, h >> 32)), h, front));
    }

    /** Where a lot of {@code size} at {@code x}, {@code z} facing {@code front} has its corner: at the street's height there. */
    private static BlockPos corner(long seed, int x, int z, int size, Direction front) {
        return new BlockPos(x, level(seed, x, z, size, front), z);
    }

    /** One of {@code sides} that is an open street, by {@code roll}; any of them if none is. */
    private static Direction either(Set<Direction> open, long roll, Direction... sides) {
        List<Direction> ways = Arrays.stream(sides).filter(open::contains).toList();
        List<Direction> from = ways.isEmpty() ? List.of(sides) : ways;
        return from.get((int) Math.floorMod(roll, (long) from.size()));
    }

    /** A building of {@code list} facing {@code front}, or now and then rubble or a dead field, as the quarter has it. */
    private static Lot lot(long seed, int x, int z, int size, District district, List<District.Weighted> list, long q, Direction front) {
        BlockPos corner = corner(seed, x, z, size, front);
        double r = Math.floorMod(q, 1000) / 1000.0;
        if (r < district.rubble) {
            return Lot.of(corner, size, Use.RUBBLE, Optional.empty(), q);
        }
        if (r < district.rubble + district.field) {
            return Lot.of(corner, size, Use.FIELD, Optional.empty(), q);
        }
        if (r < district.rubble + district.field + district.yard) {
            return Lot.of(corner, size, Use.YARD, Optional.empty(), q);
        }
        Optional<Identifier> portal = Vanilla.portal(q, size);
        if (portal.isPresent()) {                                        // now and then a ruined portal, the game's own
            return Lot.facing(corner, size, Use.BUILDING, portal, q, front);
        }
        boolean rare = list == district.sBuildings && Math.floorMod(q >> 44, 1000) < District.RARE_S * 1000;
        return Lot.facing(corner, size, Use.BUILDING, Optional.of(District.pick(rare ? district.rareS : list, q >> 8)), q, front);
    }

    /** Whether the square [u0, u0+size) x [v0, v0+size) is clear of every lot already laid ({u0, u1, v0, v1}). */
    private static boolean clear(List<int[]> taken, int u0, int v0, int size) {
        for (int[] t : taken) {
            if (u0 < t[1] && u0 + size > t[0] && v0 < t[3] && v0 + size > t[2]) {
                return false;
            }
        }
        return true;
    }

    /**
     * One of a block's two rows: S lots and XS lots in an order the seed picks, side by side, what is left over at the
     * row's two ends. The near row is built up to its street, each lot where that lot's own stretch of it is, so the fronts
     * step with the street; the far row stands against the near row's backs where its street is near enough (the user,
     * 2026-10-13: "建物同士はもっと密集させたい"), at most {@link #FAR_BACK} back from it, the ground before it paved as the
     * street, a garden behind it where the near row is shallow. A lot at a row's end faces the side street now and then (always, if its own is an alley), and behind an
     * XS there at the near row's end may stand another facing it. Where the far row has no room for an S, two XS instead.
     */
    private static void row(List<Lot> lots, List<int[]> taken, Frame f, District district, Set<Direction> open, long h, boolean near,
            int uLo, int uHi) {
        long seed = f.seed();
        List<Integer> units = new ArrayList<>();
        int left = uHi - uLo;
        for (int k = 0; left >= XS_LOT; k++) {
            int size = left >= S_LOT && Math.floorMod(hash(h, k, 0, 6), 1000) >= district.small * 1000 ? S_LOT : XS_LOT;
            units.add(size);
            left -= size;
        }
        // the houses side by side; what is left over at the row's ends - but not between a corner lot and a side street it
        // may face: then at the other end, or between two lots if both may
        Direction front = near ? f.near() : f.far();
        boolean startOpen = open.contains(f.start());
        boolean endOpen = open.contains(f.end());
        int startSpare = startOpen && endOpen ? 0 : startOpen ? 0 : endOpen ? left : (int) Math.floorMod(hash(h, 7, 7, 9), (long) (left + 1));
        int middleAt = startOpen && endOpen && units.size() > 1 ? 1 + (int) Math.floorMod(hash(h, 7, 8, 9), (long) (units.size() - 1)) : -1;
        int u = uLo + startSpare;
        for (int k = 0; k < units.size(); k++) {
            if (k == middleAt) {
                u += left;
            }
            int size = units.get(k);
            long q = hash(h, k, 1, 7);
            Direction side = k == 0 ? f.start() : k == units.size() - 1 ? f.end() : null;
            if (size == S_LOT) {
                Direction facing = side != null && open.contains(side) && (!open.contains(front) || Math.floorMod(q >> 30, 3L) == 0) ? side : front;
                int v = rowV(taken, f, near, u, S_LOT);
                if (v != Integer.MIN_VALUE) {
                    add(lots, taken, f, lot(seed, f.x(u, v), f.z(u, v), S_LOT, district, district.sBuildings, q, facing), u, v);
                    u += size;
                    continue;
                }
                // no room here for an S behind the near row: two little ones
                for (int half = 0; half < 2; half++) {
                    little(lots, taken, f, district, open, hash(q, half, 0, 11), near, u + half * XS_LOT, front, null);
                }
                u += size;
                continue;
            }
            little(lots, taken, f, district, open, q, near, u, front, side);
            u += size;
        }
    }

    /** How far back from its street a far row's lot may stand, to keep against the near row's backs. */
    private static final int FAR_BACK = 4;
    /** How far back a near row's lot stands from its street, one picked for each: most up to it. */
    private static final int[] SETBACK = {0, 0, 0, 0, 1, 2, 3, 5};

    /**
     * Where along v a row's lot of {@code size} at {@code u} goes, or MIN_VALUE if it does not fit: the near row's up to
     * its street; the far row's right behind whatever the near row has there (against its own street if nothing), not
     * reaching into its street.
     */
    private static int rowV(List<int[]> taken, Frame f, boolean near, int u, int size) {
        if (near) {
            // not all to one line: a house now and then stands back a little from its street, a yard before it (the user,
            // 2026-10-17: the fronts in a row read as the grid's)
            int v = f.maxNear(u, u + size) + SETBACK[(int) Math.floorMod(hash(f.seed(), f.x(u, 0), f.z(0, u), 142), (long) SETBACK.length)];
            return v + size <= f.minFar(u, u + size) && clear(taken, u, v, size) ? v : Integer.MIN_VALUE;
        }
        int far = f.minFar(u, u + size);
        int behind = Integer.MIN_VALUE;
        for (int[] t : taken) {
            if (t[0] < u + size && t[1] > u) {
                behind = Math.max(behind, t[3]);
            }
        }
        // against the near row's backs, but never more than a few blocks back from its own street: where the near row is
        // shallow, a garden between them rather than a square before it
        int v = behind == Integer.MIN_VALUE ? far - size : Math.max(behind, far - size - FAR_BACK);
        return v + size <= far && clear(taken, u, v, size) ? v : Integer.MIN_VALUE;
    }

    /** An XS lot of a row at {@code u}; at the near row's end, maybe another behind it facing the side street. */
    private static void little(List<Lot> lots, List<int[]> taken, Frame f, District district, Set<Direction> open, long q, boolean near,
            int u, Direction front, Direction side) {
        long seed = f.seed();
        Direction facing = side != null && open.contains(side) && (!open.contains(front) || Math.floorMod(q >> 30, 3L) == 0) ? side : front;
        int v = rowV(taken, f, near, u, XS_LOT);
        if (v == Integer.MIN_VALUE) {
            return;
        }
        add(lots, taken, f, lot(seed, f.x(u, v), f.z(u, v), XS_LOT, district, district.xsBuildings, q, facing), u, v);
        long b = hash(q, 2, 0, 8);
        int back = v + XS_LOT;
        if (near && side != null && open.contains(side) && Math.floorMod(b >> 30, 2L) == 0 && clear(taken, u, back, XS_LOT)) {
            add(lots, taken, f, lot(seed, f.x(u, back), f.z(u, back), XS_LOT, district, district.xsBuildings, b, side), u, back);
        }
    }

    private static void add(List<Lot> lots, List<int[]> taken, Frame f, Lot lot, int u, int v) {
        lots.add(lot);
        taken.add(new int[] {u, u + lot.size(), v, v + lot.size()});
    }

    /** A ring of XS lots facing out, round a court: a building of the quarter's courts most often, else weeds or rubble. */
    private static void ring(List<Lot> lots, Frame f, District district, Set<Direction> open, long h, int u0, int v0) {
        long seed = f.seed();
        int[] across = {0, XS_LOT, RING - 2 * XS_LOT, RING - XS_LOT};
        for (int k = 0; k < 4; k++) {
            Direction side = k == 0 ? f.start() : k == 3 ? f.end() : null;
            for (boolean near : new boolean[] {true, false}) {
                long q = hash(h, k, near ? 1 : 2, 9);
                Direction front = near ? f.near() : f.far();
                Direction facing = side != null && open.contains(side) && (!open.contains(front) || Math.floorMod(q >> 30, 2L) == 0) ? side : front;
                int u = u0 + across[k];
                int v = v0 + (near ? 0 : RING - XS_LOT);
                lots.add(lot(seed, f.x(u, v), f.z(u, v), XS_LOT, district, district.xsBuildings, q, facing));
            }
        }
        for (int k = 1; k <= 2; k++) {                                   // the two sides between, facing their ends
            int v = v0 + across[k];
            lots.add(lot(seed, f.x(u0, v), f.z(u0, v), XS_LOT, district, district.xsBuildings, hash(h, k, 3, 9), f.start()));
            int ue = u0 + RING - XS_LOT;
            lots.add(lot(seed, f.x(ue, v), f.z(ue, v), XS_LOT, district, district.xsBuildings, hash(h, k, 4, 9), f.end()));
        }
        long c = hash(h, 0, 5, 9);
        double r = Math.floorMod(c, 1000) / 1000.0;
        int cu = u0 + XS_LOT;
        int cv = v0 + XS_LOT;
        BlockPos court = new BlockPos(f.x(cu, cv), (int) Math.round(height(seed, f.x(cu, cv) + COURT / 2, f.z(cu, cv) + COURT / 2)), f.z(cu, cv));
        lots.add(r < 0.6 ? Lot.of(court, COURT, Use.BUILDING, Optional.of(District.pick(district.courts, c >> 8)), c)
                : Lot.of(court, COURT, r < 0.85 ? Use.YARD : Use.RUBBLE, Optional.empty(), c));
    }

    // --- a chunk of it ---------------------------------------------------------------------------------------------------

    /**
     * What the 18 x 18 columns from ({@code x0}, {@code z0}) are to be: of what ground, how high, paved or not, under a
     * lot or not - the same from every chunk. Public for the {@code /nzw ground} check.
     */
    public record Plan(long seed, int x0, int z0, List<Lot> lots, int[][] top, boolean[][] half, boolean[][] paved, Ground[][] kind,
            boolean[][] built, int[][] water) {}

    public static Plan plan(long seed, int x0, int z0) {
        List<Lot> lots = new ArrayList<>();
        for (int bx = Math.floorDiv(x0, BLOCK) - 1; bx <= Math.floorDiv(x0 + 17, BLOCK) + 1; bx++) {
            for (int bz = Math.floorDiv(z0, BLOCK) - 1; bz <= Math.floorDiv(z0 + 17, BLOCK) + 1; bz++) {
                lots.addAll(lots(seed, bx, bz));
            }
        }
        int[][] top = new int[18][18];
        boolean[][] half = new boolean[18][18];
        boolean[][] paved = new boolean[18][18];
        Ground[][] kind = new Ground[18][18];
        boolean[][] built = new boolean[18][18];
        // first a margin wider, so the open ground can be smoothed the same from every chunk; and the ground of a margin
        // wider again, as far out as the open ground looks for its streets ({@link #between}), worked out once
        int w = 18 + 2 * SMOOTH;
        int m = SMOOTH + VERGE;
        Ground[][] wide = new Ground[18 + 2 * m][18 + 2 * m];
        for (int i = 0; i < wide.length; i++) {
            for (int j = 0; j < wide.length; j++) {
                wide[i][j] = ground(seed, x0 - m + i, z0 - m + j);
            }
        }
        Grounds grounds = (gx, gz) -> {
            int i = gx - x0 + m;
            int j = gz - z0 + m;
            return i >= 0 && j >= 0 && i < wide.length && j < wide.length ? wide[i][j] : ground(seed, gx, gz);
        };
        int[][] raw = new int[w][w];
        Ground[][] rawKind = new Ground[w][w];
        boolean[][] open = new boolean[w][w];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < w; j++) {
                int x = x0 - SMOOTH + i;
                int z = z0 - SMOOTH + j;
                rawKind[i][j] = wide[i + VERGE][j + VERGE];
                if (rawKind[i][j] == Ground.STREET) {
                    raw[i][j] = (int) Math.floor(streetHeight(seed, x, z));
                    continue;
                }
                Lot under = null;
                for (Lot lot : lots) {
                    if (!soft(lot) && lot.covers(x, z)) {
                        under = lot;
                        break;
                    }
                }
                if (under != null) {                                      // a building's lot, or a court: its level
                    raw[i][j] = under.corner().getY();
                } else {                                                  // the open ground and the alleys: up to what is round them
                    raw[i][j] = (int) Math.round(between(seed, lots, x, z, grounds));
                    open[i][j] = true;
                }
            }
        }
        for (int a = 0; a < 18; a++) {
            for (int b = 0; b < 18; b++) {
                int i = a + SMOOTH;
                int j = b + SMOOTH;
                kind[a][b] = rawKind[i][j];
                top[a][b] = open[i][j] ? smoothed(raw, rawKind, i, j) : raw[i][j];
                if (kind[a][b] == Ground.STREET) {
                    half[a][b] = streetHeight(seed, x0 + a, z0 + b) - top[a][b] > 0.25;
                }
                int x = x0 + a;
                int z = z0 + b;
                built[a][b] = lots.stream().anyMatch(l -> l.covers(x, z));
            }
        }
        for (Lot lot : lots) {                                            // before each building, paving up to its street
            if (lot.size() != COURT && !soft(lot) && reaches(lot, x0, z0)) {
                forecourt(seed, lots, lot, x0, z0, top, paved);
            }
        }
        int[][] water = new int[18][18];
        for (int a = 0; a < 18; a++) {                                    // a lake: its bed dug out, its shore down to it
            for (int b = 0; b < 18; b++) {
                int x = x0 + a;
                int z = z0 + b;
                Terrain.Sea sea = built[a][b] ? null : Terrain.sea(seed, x, z);
                if (sea != null && sea.in(x, z) > 0) {                    // a sea: over the ground and the tracks run into it alike
                    top[a][b] = Math.min(top[a][b], (int) Math.floor(sea.shape(x, z, top[a][b])));
                    water[a][b] = sea.water();
                    continue;
                }
                if (kind[a][b] != Ground.LOT || built[a][b]) {
                    continue;
                }
                int canal = Traces.canal(seed, x, z);                     // a canal where no street runs: its bed and water
                if (canal > 0) {
                    top[a][b] = canal - 2;
                    water[a][b] = canal;
                    continue;
                }
                Terrain.Lake lake = Terrain.lake(seed, Math.floorDiv(x, BLOCK), Math.floorDiv(z, BLOCK));
                if (lake == null) {
                    continue;
                }
                double in = lake.in(x, z);
                if (in > 0) {                                             // shallow at the edge, a few deep in the middle
                    top[a][b] = lake.water() - 1 - (int) Math.min(3, Math.floor(in / 4.0));
                    water[a][b] = lake.water();
                } else if (in > -8) {                                     // the shore comes down to it gently
                    top[a][b] = Math.max(lake.water(), Math.min(top[a][b], lake.water() + (int) Math.floor(-in / 3.0)));
                }
            }
        }
        return new Plan(seed, x0, z0, lots, top, half, paved, kind, built, water);
    }

    /**
     * This chunk's part of the town: the ground raised or lowered to each column's height - the lots levelled, the streets
     * in half steps, a wall of old stone wherever a column stands two or more above the one beside it - the streets and
     * alleys paved, the ground before each building's front paved up to the street, and of each lot reaching into it, what
     * falls inside it.
     */
    public static void decorate(WorldGenLevel level, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        long seed = level.getSeed();
        BoundingBox box = new BoundingBox(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(), pos.getMaxBlockX(), level.getMaxY(),
                pos.getMaxBlockZ());
        int x0 = box.minX() - 1;                                          // a column round the chunk, for the walls
        int z0 = box.minZ() - 1;
        Plan plan = plan(seed, x0, z0);
        List<Lot> lots = plan.lots();
        int[][] top = plan.top();
        boolean[][] half = plan.half();
        boolean[][] paved = plan.paved();
        Ground[][] kind = plan.kind();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        // the capital's columns are its own (Capital, 2026-10-24): the town leaves them, and the capital draws them last
        boolean capital = Capital.near(seed, box.minX(), box.minZ(), 16, 0);
        boolean[][] mine = new boolean[18][18];
        for (int a = 0; a < 18; a++) {
            for (int b = 0; b < 18; b++) {
                mine[a][b] = capital && Capital.owns(seed, x0 + a, z0 + b);
            }
        }
        for (int a = 1; a <= 16; a++) {
            for (int b = 1; b <= 16; b++) {
                int x = x0 + a;
                int z = z0 + b;
                int t = top[a][b];
                if (mine[a][b]) {
                    continue;
                }
                shape(level, seed, at, x, z, t);
                District district = District.of(seed, Math.floorDiv(x, BLOCK), Math.floorDiv(z, BLOCK));
                if (kind[a][b] == Ground.STREET || paved[a][b]) {
                    paveStreet(level, seed, at, x, z, t, half[a][b], district);
                    if (kind[a][b] == Ground.STREET && !half[a][b]) {     // what was left on it (Traces)
                        boolean drawn = Traces.wall(level, seed, at, x, z, t) || Traces.barricade(level, seed, at, x, z, t)
                                || Traces.flight(level, seed, at, x, z, t);
                    }
                } else if (kind[a][b] == Ground.ALLEY) {
                    alley(level, seed, at, x, z, t);
                    Traces.wall(level, seed, at, x, z, t);
                } else if (!plan.built()[a][b] && plan.water()[a][b] == 0 && !Town.rural(Region.at(seed, x, z))) {
                    openGround(level, seed, at, x, z, t, district);
                }
            }
        }
        for (Lot lot : lots) {
            if (lot.footprint().intersects(box)) {
                fill(level, lot, box, plan);
            }
        }
        Diaries.at(Diaries.Place.UNDER);                                  // the caves where people hid, the galleries: below
        try {
            Country.draw(level, seed, plan, box, mine);
        } finally {
            Diaries.at(Diaries.Place.TOWN);
        }
        for (int a = 1; a <= 16; a++) {                                    // boats left in the canals, their bridges broken
            for (int b = 1; b <= 16; b++) {
                if (plan.water()[a][b] > 0 && kind[a][b] == Ground.LOT) {
                    int c = Traces.canal(seed, x0 + a, z0 + b);
                    if (c > 0 && !Ascent.near(seed, x0 + a, z0 + b, 1)) {
                        Traces.onCanal(level, seed, at, x0 + a, z0 + b, c);
                    }
                }
            }
        }
        Diaries.at(Diaries.Place.UNDER);
        try {
            Tunnels.draw(level, seed, plan, box);
        } finally {
            Diaries.at(Diaries.Place.TOWN);
        }
        Shelters.draw(level, seed, box);                                  // the shelters dug under the town (2026-10-23)
        // last, over a building's own ground: where a column stands two or more above the one beside it, the face below
        // its top is a wall of old stone (a single step stays ground: the open ground's steps wander, and a building's own
        // edge is as its builder made it)
        for (int a = 1; a <= 16; a++) {
            for (int b = 1; b <= 16; b++) {
                if (mine[a][b]) {
                    continue;
                }
                int t = top[a][b];
                int low = t;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int[][] w = plan.water();
                    low = Math.min(low, Math.max(top[a + d[0]][b + d[1]], w[a + d[0]][b + d[1]]));
                }
                if (plan.water()[a][b] > 0) {
                    continue;                                           // under water: the lake's bed, no wall
                }
                for (int y = low + 1; y < t; y++) {
                    level.setBlock(at.set(x0 + a, y, z0 + b), wall(seed, x0 + a, y, z0 + b), 2);
                }
            }
        }
        Desert.ground(level, plan, x0, z0);                            // and where the sand took the town, its sand over it all
        Ascent.draw(level, seed, box);                                 // the second institute, over all of it (0.3)
        if (capital) {
            CapitalDraw.decorate(level, box);                          // the capital, over its own columns
        }
    }

    /**
     * A lot whose ground is not levelled: a yard gone to weeds, a heap of rubble or a dead field lies on the ground as it
     * is ({@link #between}); a building's lot is levelled to its street, and a court's.
     */
    public static boolean soft(Lot lot) {
        return lot.size() != COURT && lot.use() != Use.BUILDING;
    }

    /**
     * Of the open ground at ({@code i}, {@code j}) of {@code raw}, the middle height of the 5 x 5 round it (streets left
     * out): a hummock or a hollow a block or two across, or a strip one wide, goes (the user, 2026-10-13: ground "一段
     * 埋まってたり、盛り上がってたり" looks made); a step stays where the ground as a whole changes.
     */
    private static int smoothed(int[][] raw, Ground[][] kind, int i, int j) {
        int[] values = new int[(2 * SMOOTH + 1) * (2 * SMOOTH + 1)];
        int n = 0;
        for (int di = -SMOOTH; di <= SMOOTH; di++) {
            for (int dj = -SMOOTH; dj <= SMOOTH; dj++) {
                int a = i + di;
                int b = j + dj;
                if (a >= 0 && b >= 0 && a < raw.length && b < raw.length && kind[a][b] != Ground.STREET) {
                    values[n++] = raw[a][b];
                }
            }
        }
        if (n < 9) {
            return raw[i][j];
        }
        Arrays.sort(values, 0, n);
        return values[n / 2];
    }

    /**
     * The open ground at ({@code x}, {@code z}) - between lots, under a yard, a heap of rubble or a dead field, along an
     * alley: the levels of the levelled lots and the streets round it, each counting the more the nearer it is (the one
     * right beside it all but alone), the ground's own height beneath them where none is near, and an unevenness that
     * grows away from them; so the ground meets each lot and street at its level, and where two levels meet, the step
     * wanders over the ground between them rather than following a lot's straight edge (the user, 2026-10-13: "直線的
     * だったり、長方形で一段高くなったりしていると、いかにも生成の都合にみえる"). The same from every chunk: it asks only
     * the lots and the seed.
     */
    /** What the ground is at a column ({@link #ground}), as worked out already for the chunk being made. */
    private interface Grounds {
        Ground at(int x, int z);
    }

    private static double between(long seed, List<Lot> lots, int x, int z, Grounds grounds) {
        double sum = 0;
        double weight = 0;
        double nearest = REACH;
        double lo = Double.MAX_VALUE;                                     // the lowest and highest of what is round it
        double hi = -Double.MAX_VALUE;
        for (Lot lot : lots) {
            if (soft(lot)) {
                continue;
            }
            int dx = Math.max(0, Math.max(lot.corner().getX() - x, x - lot.corner().getX() - lot.size() + 1));
            int dz = Math.max(0, Math.max(lot.corner().getZ() - z, z - lot.corner().getZ() - lot.size() + 1));
            int d2 = dx * dx + dz * dz;
            if (d2 == 0 || d2 > REACH * REACH) {
                continue;
            }
            double d = Math.sqrt(d2);
            // falling off as the cube of the distance, to nothing at the edge of its reach (no step where it ends)
            double w = 1.0 / (d2 * d) - 1.0 / ((double) REACH * REACH * REACH);
            sum += w * lot.corner().getY();
            weight += w;
            nearest = Math.min(nearest, d);
            lo = Math.min(lo, lot.corner().getY());
            hi = Math.max(hi, lot.corner().getY());
        }
        for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {  // the nearest street each way, a few blocks out
            for (int d = 1; d <= VERGE; d++) {
                int sx = x + dir[0] * d;
                int sz = z + dir[1] * d;
                if (grounds.at(sx, sz) == Ground.STREET) {
                    double w = 1.0 / ((double) d * d * d) - 1.0 / ((double) (VERGE + 1) * (VERGE + 1) * (VERGE + 1));
                    double street = streetHeight(seed, sx, sz);
                    sum += w * street;
                    weight += w;
                    nearest = Math.min(nearest, d);
                    lo = Math.min(lo, Math.floor(street));
                    hi = Math.max(hi, Math.floor(street));
                    break;
                }
            }
        }
        double ground = height(seed, x, z);
        double w = 1.0 / ((double) REACH * REACH * REACH);
        sum += w * ground;
        weight += w;
        if (lo > hi) {
            lo = hi = Math.round(ground);
        } else if (nearest >= REACH / 2.0) {
            // far out from what is round it, its own ground counts in the bounds too: a lot only just in reach no longer
            // pulls a column all the way to its level, a step beside the one just out of reach (a farm's lot past a lane)
            lo = Math.min(lo, Math.round(ground));
            hi = Math.max(hi, Math.round(ground));
        }
        // an unevenness, little at a lot's edge (less than half a block: it stays level with it), more further out, that
        // moves where a step between two levels goes - but never past the levels round it: no hummock or hollow of its own
        double out = Math.min(1.0, (nearest - 1) / 4.0);
        double wobble = (noise(seed, x, z, 9.0, 16) - 0.5) * (0.6 + 0.6 * out) + (noise(seed, x, z, 19.0, 17) - 0.5) * 1.0 * out;
        return Math.max(lo - 0.45, Math.min(hi + 0.45, sum / weight + wobble));
    }

    /** The column ({@code x}, {@code z}) brought to {@code t}: earth put under, or taken away down to it; grass on top. */
    private static void shape(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int t) {
        for (int y = SURFACE + 1; y <= t; y++) {
            level.setBlock(at.set(x, y, z), (y > t - 4 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2);
        }
        for (int y = t + 1; y <= SURFACE; y++) {
            level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(at.set(x, t, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
    }

    /** Whether the ground before {@code lot}'s front may reach the 18 x 18 from ({@code x0}, {@code z0}). */
    private static boolean reaches(Lot lot, int x0, int z0) {
        int pad = BLOCK / 2 + 1;
        return lot.corner().getX() - pad <= x0 + 17 && lot.corner().getX() + lot.size() + pad >= x0
                && lot.corner().getZ() - pad <= z0 + 17 && lot.corner().getZ() + lot.size() + pad >= z0;
    }

    /** What a wall holding up higher ground is made of: old stone, in patches. */
    private static BlockState wall(long seed, int x, int y, int z) {
        if (Desert.at(seed, x, z)) {
            return Desert.wall(seed, x, y, z);
        }
        double v = noise(seed, x + y * 7, z - y * 5, 3.0, 15);
        if (Terrain.peak(seed, x, z) != null) {                           // up a mountain: its own rock, not a made wall
            return (v < 0.5 ? Blocks.STONE : v < 0.75 ? Blocks.ANDESITE : v < 0.9 ? Blocks.TUFF : Blocks.COBBLESTONE).defaultBlockState();
        }
        Block block = v < 0.35 ? Blocks.STONE_BRICKS : v < 0.55 ? Blocks.MOSSY_STONE_BRICKS : v < 0.75 ? Blocks.COBBLESTONE
                : v < 0.9 ? Blocks.MOSSY_COBBLESTONE : Blocks.CRACKED_STONE_BRICKS;
        return block.defaultBlockState();
    }

    /**
     * Before {@code lot}'s front, the ground between it and the street (where the lot's own stretch of street stands back
     * from where the lot was built up to) paved as the street, at the lot's level.
     */
    private static void forecourt(long seed, List<Lot> lots, Lot lot, int x0, int z0, int[][] top, boolean[][] paved) {
        Direction front = lot.front();
        int cx = lot.corner().getX();
        int cz = lot.corner().getZ();
        int n = lot.size();
        for (int s = 0; s < n; s++) {
            int x = front.getAxis() == Direction.Axis.X ? (front.getStepX() > 0 ? cx + n - 1 : cx) : cx + s;
            int z = front.getAxis() == Direction.Axis.Z ? (front.getStepZ() > 0 ? cz + n - 1 : cz) : cz + s;
            if (front.getAxis() == Direction.Axis.Z ? x < x0 || x > x0 + 17 : z < z0 || z > z0 + 17) {
                continue;                                               // its line out never crosses the window
            }
            // out to the street, asking the lots and the ground themselves (not this chunk's window, which may not reach
            // back to the lot), so every chunk paves the same
            // - only if it reaches a street or an alley: a cabin alone in the wilds, a village house, have none to pave to
            int reach = 0;
            for (int d = 1; d <= BLOCK / 2 && reach == 0; d++) {
                int px = x + front.getStepX() * d;
                int pz = z + front.getStepZ() * d;
                if (lots.stream().anyMatch(l -> l.covers(px, pz))) {
                    break;
                }
                if (ground(seed, px, pz) != Ground.LOT) {
                    reach = d;
                }
            }
            for (int d = 1; d < reach; d++) {
                int px = x + front.getStepX() * d;
                int pz = z + front.getStepZ() * d;
                int a = px - x0;
                int b = pz - z0;
                if (a >= 0 && b >= 0 && a < 18 && b < 18) {
                    paved[a][b] = true;
                    top[a][b] = lot.corner().getY();
                }
            }
        }
    }

    /**
     * How far a quarter's streets have gone back to the ground, 0 to 1: the centre's hardly, the waste's most (the user,
     * 2026-10-17: streets "草でおおわれていたり、途中で途切れていたり").
     */
    private static double neglect(District district) {
        return switch (district) {
            case CENTRE -> 0.15;
            case OLD_TOWN, SETTLEMENT -> 0.3;
            case RESIDENTIAL -> 0.4;
            case OUTSKIRTS -> 0.6;
            case FARM -> 0.65;
            case WASTE, WILD -> 0.8;
            case DESERT -> 0.95;
        };
    }

    /**
     * A street's top in patches a few blocks across (not block by block, which reads as noise): the quarter's paving -
     * gravel and cobbles, andesite and mossy cobbles in places, bare earth here and there; on a half step, its slab. Gone
     * to seed as the quarter has let it: its edges ragged, the grass in over them; moss and weeds in the cracks; stretches
     * grown over altogether, a worn track down the middle of them; a stone, a slab or a heap of rubble lying on it.
     */
    private static void paveStreet(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y, boolean half,
            District district) {
        double neglect = neglect(district);
        int r = (int) Math.floorMod(hash(seed, x, z, 4), 1000);
        if (!half) {
            int[] margin = margin(seed, x, z);
            boolean middle = margin[0] >= margin[1] - 1;
            double edge = noise(seed, x, z, 3.0, 91);
            boolean overgrown = noise(seed, x, z, 38.0, 90) > 1.0 - neglect * 0.75;
            // the grass in over the edges, raggedly, and in patches across
            boolean verge = margin[0] == 0 && edge > 0.45 - neglect * 0.3 || margin[0] == 1 && edge > 0.7 - neglect * 0.3
                    || margin[0] == 2 && edge > 0.9 - neglect * 0.2 || noise(seed, x, z, 5.0, 105) > 0.84 - neglect * 0.2;
            if (overgrown && middle && noise(seed, x, z, 6.0, 92) < 0.8) {
                level.setBlock(at.set(x, y, z), (edge < 0.45 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState(), 2);
                return;                                                 // the track trodden through it
            }
            if (overgrown || verge) {
                level.setBlock(at.set(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (r < (overgrown ? 520 : 300)) {
                    tuft(level, at, x, z, y, r % 300);
                }
                return;
            }
        }
        double v = noise(seed, x, z, 6.0, 3);
        Block top = district.streets[Math.min(district.streets.length - 1, (int) (v * district.streets.length))];
        if (!half && r < 25 * neglect) {                                // a crack the weeds came up through
            top = r % 2 == 0 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK;
        }
        level.setBlock(at.set(x, y, z), top.defaultBlockState(), 2);
        Block slab = half ? slab(top) : null;
        if (half && slab == null) {
            // earth has no slab of its own: a stepping stone of the quarter's (a hole a whole step down read as dug out)
            slab = district == District.OUTSKIRTS || district == District.WASTE ? Blocks.MUD_BRICK_SLAB : Blocks.COBBLESTONE_SLAB;
        }
        if (slab != null) {
            level.setBlock(at.set(x, y + 1, z), slab.defaultBlockState(), 2);
            return;
        }
        if (top == Blocks.GRASS_BLOCK || top == Blocks.MOSS_BLOCK) {
            level.setBlock(at.set(x, y + 1, z), (r % 4 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState(), 2);
            return;
        }
        if (top != Blocks.DIRT_PATH && noise(seed, x, z, 5.0, 94) > 0.86 - neglect * 0.12 && r < 700) {
            level.setBlock(at.set(x, y + 1, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
            return;                                                     // moss creeping over the stones
        }
        if (noise(seed, x, z, 7.0, 93) > 0.9 - neglect * 0.08 && r < 650) {
            // a heap of what came down: stones and broken slabs, higher in its middle
            BlockState stone = r < 260 ? Blocks.COBBLESTONE.defaultBlockState() : r < 420 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : r < 540 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.STONE_BRICK_SLAB.defaultBlockState();
            level.setBlock(at.set(x, y + 1, z), stone, 2);
            return;
        }
        if (r < 10 && top != Blocks.DIRT_PATH) {
            BlockState on = r < 5 ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
                    : r < 8 ? Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            level.setBlock(at.set(x, y + 1, z), on, 2);
        }
    }

    /**
     * The town's open ground between the lots: no longer a kept lawn but grass gone to seed, the odd bush - and the paths
     * people wore across it, winding from street to street and round the houses, fading out and on again (the user,
     * 2026-10-17: "通路も、建物と連続していたり、途中で途切れていたり…うねうねしていたり").
     */
    private static void openGround(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y, District district) {
        if (Traces.scorched(level, seed, at, x, z, y)) {
            return;
        }
        Traces.inside(level, seed, at, x, z, y);
        int r = (int) Math.floorMod(hash(seed, x, z, 96), 1000);
        if (path(seed, x, z)) {
            double n = noise(seed, x, z, 3.0, 97);
            if (n < 0.78) {
                level.setBlock(at.set(x, y, z), (n < 0.6 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState(), 2);
            } else if (r < 400) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            return;
        }
        if (district != District.DESERT) {                               // the desert's ground, bare (the review, 2026-10-18)
            weeds(level, seed, at, x, z, y, r, 0.55 - neglect(district) * 0.25, 98);
        }
    }

    /**
     * Whether a worn path crosses ({@code x}, {@code z}): where a broad noise passes its middle - lines that wind on and
     * meet each other, a block or two wide - and not in the stretches where nobody has gone for a long time.
     */
    static boolean path(long seed, int x, int z) {
        if (track(seed, x, z) && noise(seed, x, z, 9.0, 143) < 0.82) {
            return true;
        }
        double a = noise(seed, x, z, 27.0, 95);
        double b = noise(seed, x + 1000, z - 1000, 41.0, 99);
        boolean on = Math.abs(a - 0.5) < 0.037 || Math.abs(b - 0.5) < 0.026;
        return on && noise(seed, x, z, 15.0, 100) < 0.72;
    }

    /** The slab of a street's paving, for a half step; null for earth, which has none. */
    private static Block slab(Block paving) {
        if (paving == Blocks.STONE_BRICKS || paving == Blocks.CRACKED_STONE_BRICKS) {
            return Blocks.STONE_BRICK_SLAB;
        }
        if (paving == Blocks.MOSSY_STONE_BRICKS) {
            return Blocks.MOSSY_STONE_BRICK_SLAB;
        }
        if (paving == Blocks.POLISHED_ANDESITE) {
            return Blocks.POLISHED_ANDESITE_SLAB;
        }
        if (paving == Blocks.ANDESITE) {
            return Blocks.ANDESITE_SLAB;
        }
        if (paving == Blocks.COBBLESTONE || paving == Blocks.GRAVEL) {
            return Blocks.COBBLESTONE_SLAB;
        }
        if (paving == Blocks.MOSSY_COBBLESTONE) {
            return Blocks.MOSSY_COBBLESTONE_SLAB;
        }
        if (paving == Blocks.PACKED_MUD) {
            return Blocks.MUD_BRICK_SLAB;
        }
        if (paving == Blocks.SAND || paving == Blocks.SANDSTONE) {         // the desert's (Desert)
            return Blocks.SANDSTONE_SLAB;
        }
        if (paving == Blocks.SMOOTH_SANDSTONE) {
            return Blocks.SMOOTH_SANDSTONE_SLAB;
        }
        if (paving == Blocks.CUT_SANDSTONE) {
            return Blocks.CUT_SANDSTONE_SLAB;
        }
        return null;
    }

    /**
     * An alley, where a street does not go through: a trodden path two wide down its middle, earth and grass, and either
     * side of it weeds and bushes grown up between the backs of the houses.
     */
    private static void alley(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y) {
        int i = Math.floorDiv(x + WIDE_HALF + JOG, BLOCK);
        int[] v = band(seed, true, i, z);
        int k = Math.floorDiv(z + WIDE_HALF + JOG, BLOCK);
        int[] h = band(seed, false, k, x);
        boolean path = x >= v[0] && x < v[1] && Math.abs(2 * x + 1 - v[0] - v[1]) <= 2
                || z >= h[0] && z < h[1] && Math.abs(2 * z + 1 - h[0] - h[1]) <= 2;
        int r = (int) Math.floorMod(hash(seed, x, z, 12), 1000);
        if (path) {
            double n = noise(seed, x, z, 3.0, 13);
            if (n < 0.7) {
                level.setBlock(at.set(x, y, z), (n < 0.4 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH).defaultBlockState(), 2);
            } else if (r < 300) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            }
            return;
        }
        if (!Desert.at(seed, x, z)) {
            weeds(level, seed, at, x, z, y, r, 0.4, 14);
        }
    }

    /** Grass gone to seed and the odd bush, where nothing has been kept for a long time. */
    private static void weeds(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y, int r, double thick, long salt) {
        if (noise(seed, x, z, 5.0, salt + 1) > 0.72 && r < 450) {
            BlockState bush = r < 200 ? Blocks.BUSH.defaultBlockState()
                    : Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            level.setBlock(at.set(x, y + 1, z), bush, 2);
            if (r >= 200 && r < 260) {
                level.setBlock(at.set(x, y + 2, z), bush, 2);
            }
        } else if (noise(seed, x, z, 4.0, salt) > thick && r < 650) {
            tuft(level, at, x, z, y, r);
        }
    }

    private static void tuft(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int z, int y, int r) {
        level.setBlock(at.set(x, y + 1, z), (r < 60 ? Blocks.TALL_GRASS : r < 90 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState(), 2);
        if (r < 60) {
            level.setBlock(at.set(x, y + 2, z), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
        }
    }

    /** Smooth noise, 0 to 1, in blobs about {@code scale} blocks across: the seed's values at a grid, blended between. */
    static double noise(long seed, int x, int z, double scale, long salt) {
        double fx = x / scale;
        double fz = z / scale;
        int gx = (int) Math.floor(fx);
        int gz = (int) Math.floor(fz);
        double tx = smooth(fx - gx);
        double tz = smooth(fz - gz);
        double a = unit(seed, gx, gz, salt);
        double b = unit(seed, gx + 1, gz, salt);
        double c = unit(seed, gx, gz + 1, salt);
        double d = unit(seed, gx + 1, gz + 1, salt);
        return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double unit(long seed, long a, long b, long salt) {
        return (hash(seed, a, b, salt) % 1_000_000) / 1_000_000.0;
    }

    /** Of {@code lot}, what falls inside {@code box} (a chunk; {@code plan} its ground). */
    static void fill(WorldGenLevel level, Lot lot, BoundingBox box, Plan plan) {
        if (lot.use() == Use.BUILDING && lot.building().map(Market.ID::equals).orElse(false)) {
            Market.draw(level, lot, box);                               // the covered market, drawn by code (2026-10-23)
            return;
        }
        if (lot.use() == Use.BUILDING && lot.building().map(Desert.PYRAMID::equals).orElse(false)) {
            Desert.pyramid(level, lot, box);                            // the game's own piece, not a template
            return;
        }
        if (lot.use() == Use.BUILDING) {
            Optional<StructureTemplate> template = lot.building().flatMap(id -> level.getLevel().getServer().getStructureTemplateManager().get(id));
            if (template.isPresent()) {
                place(level, lot, template.get(), box);
                return;
            }
        }
        boolean desert = Desert.on(level.getSeed(), lot);
        if (!desert || lot.use() != Use.FIELD) {                         // no field the desert's: no crops, no orchard
            scatter(level, lot, box, plan);
        }
        if (desert) {
            Desert.traces(level, lot, box, (x, z) -> x - plan.x0() >= 0 && x - plan.x0() < plan.top().length && z - plan.z0() >= 0
                    && z - plan.z0() < plan.top()[0].length ? plan.top()[x - plan.x0()][z - plan.z0()] : lot.corner().getY());  // what players left there
        }
    }

    /**
     * A village house's earth grassed over where it lies open: the game's templates carry the dirt under their floor and
     * yard at the ground's own level, a dark band round the house that read as a pit dug for it (the user, 2026-10-17).
     */
    private static void grassOver(WorldGenLevel level, Lot lot, BoundingBox box) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BoundingBox foot = lot.footprint();
        for (int x = Math.max(foot.minX(), box.minX()); x <= Math.min(foot.maxX(), box.maxX()); x++) {
            for (int z = Math.max(foot.minZ(), box.minZ()); z <= Math.min(foot.maxZ(), box.maxZ()); z++) {
                for (int y = lot.corner().getY() - 1; y <= lot.corner().getY() + 1; y++) {
                    if (level.getBlockState(at.set(x, y, z)).is(Blocks.DIRT) && level.getBlockState(at.set(x, y + 1, z)).isAir()) {
                        level.setBlock(at.set(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * A zombie village's own: on one lot in two, one or two of the mutated dead standing in the middle of it (a marker,
     * released when someone comes), if that spot is in this chunk and has room.
     */
    private static void villagers(WorldGenLevel level, Lot lot, BoundingBox box) {
        if ((lot.seed() >> 3 & 1) == 0) {
            return;
        }
        int x = lot.corner().getX() + lot.size() / 2;
        int z = lot.corner().getZ() + lot.size() / 2;
        for (int y = lot.corner().getY() + 1; y <= lot.corner().getY() + 6; y++) {
            BlockPos at = new BlockPos(x, y, z);
            if (box.isInside(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                    && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                // the dead of the fallen world, as everywhere (the user, 2026-10-17: "ゾンビ村にスポーンするゾンビも普通の
                // 変異ゾンビとかでいい"), standing in the house till someone comes
                Markers.make(level, at, lot.front(), "[zombie " + (1 + (lot.seed() >> 4 & 1)) + "]", RandomSource.create(lot.seed()));
                cat(level, lot, box, at);
                return;
            }
        }
    }

    /**
     * The house's cat, the plague's now, one house in two of those with their dead (2026-10-23): somewhere else on its
     * floor, kept there till someone comes.
     */
    private static void cat(WorldGenLevel level, Lot lot, BoundingBox box, BlockPos dead) {
        if ((lot.seed() >> 6 & 1) == 0) {
            return;
        }
        for (int[] d : new int[][] {{2, 1}, {-2, -1}, {1, -2}, {-1, 2}, {3, 0}, {0, 3}, {-3, 0}, {0, -3}}) {
            BlockPos at = dead.offset(d[0], 0, d[1]);
            if (box.isInside(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                    && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                var cat = dev.nookofthedead.registry.ZwEntities.INFECTED_CAT.get().create(level.getLevel(),
                        net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
                if (cat != null) {
                    cat.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RandomSource.create(lot.seed()).nextFloat() * 360, 0);
                    cat.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
                    cat.setPersistenceRequired();
                    level.addFreshEntity(cat);
                }
                return;
            }
        }
    }

    /**
     * Where the institute's deep part ({@link #INSTITUTE_DEEP}) has its zero on {@code lot}: straight under the building,
     * its top just under the grass's level, turned as the building is.
     */
    public static BlockPos deepOrigin(Lot lot, StructureTemplate deep) {
        return origin(lot, deep).above(BELOW).below(deep.getSize().getY());
    }

    /** For commands: where {@code template}'s zero goes on {@code lot} ({@link #origin}). */
    public static BlockPos placedAt(Lot lot, StructureTemplate template) {
        return origin(lot, template);
    }

    /** Where {@code template}'s zero goes on {@code lot}: centred on it, turned and mirrored as the lot says. */
    private static BlockPos origin(Lot lot, StructureTemplate template) {
        int size = Math.max(template.getSize().getX(), template.getSize().getZ());
        int inset = Math.max(0, (lot.size() - size) / 2);
        // ours are taken out with two layers of earth under them; the game's own have their bottom layer at the ground's
        // own level (a village house's floor, or its yard's grass and paths), as the game sets them on its streets
        int below = lot.building().map(Vanilla::is).orElse(false) ? 0 : BELOW;
        BlockPos corner = lot.corner().offset(inset, -below, inset);
        return template.getZeroPositionWithTransform(corner, lot.mirror(), lot.rotation());
    }

    /** Where {@code template}'s marker signs stand once placed on {@code lot}, each with what it says. For tests. */
    public static List<java.util.Map.Entry<BlockPos, String>> markersOn(Lot lot, StructureTemplate template) {
        BlockPos origin = origin(lot, template);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        return Markers.of(template).stream()
                .map(m -> java.util.Map.entry(StructureTemplate.calculateRelativePosition(settings, m.local()).offset(origin), m.text()))
                .toList();
    }

    /** Each {@code [loot]} marker of {@code template} placed on {@code lot} with the container beside it: {marker, container}. For tests. */
    public static List<BlockPos[]> lootOn(Lot lot, StructureTemplate template) {
        BlockPos origin = origin(lot, template);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        return Markers.of(template).stream().filter(m -> m.container() != null && m.text().toLowerCase(java.util.Locale.ROOT).startsWith("[loot"))
                .map(m -> new BlockPos[] {StructureTemplate.calculateRelativePosition(settings, m.local()).offset(origin),
                        StructureTemplate.calculateRelativePosition(settings, m.container()).offset(origin)})
                .toList();
    }

    private static void place(WorldGenLevel level, Lot lot, StructureTemplate template, BoundingBox box) {
        District district = District.of(level.getSeed(), Math.floorDiv(lot.corner().getX() + lot.size() / 2, BLOCK),
                Math.floorDiv(lot.corner().getZ() + lot.size() / 2, BLOCK));
        Diaries.at(diaryPlace(lot, district));
        try {
            placeBuilding(level, lot, template, box, district);
        } finally {
            Diaries.at(Diaries.Place.TOWN);
        }
    }

    /**
     * Where a building's diaries are (the story's review, 2026-10-23): the inn, the clinics, the station and the rest
     * have their own; the farms and settlements, and the sand's town, theirs; anywhere else, the town's.
     */
    static Diaries.Place diaryPlace(Lot lot, District district) {
        return switch (lot.building().map(Identifier::getPath).orElse("")) {
            case "town/s22", "town/xs13" -> Diaries.Place.INN;
            case "town/m11", "town/s17", "town/m13" -> Diaries.Place.CLINIC;
            case "town/m12" -> Diaries.Place.STATION;
            case "town/xs21" -> Diaries.Place.CHECKPOINT;
            case "town/s13", "town/institute" -> Diaries.Place.LAB;
            case "town/s23", "town/s12", "town/xs18" -> Diaries.Place.BELL;
            case "town/m10", "town/s16" -> Diaries.Place.WORKS;
            case "town/m4", "town/m7" -> Diaries.Place.SQUARE;
            default -> district == District.DESERT ? Diaries.Place.DESERT
                    : district == District.FARM || district == District.SETTLEMENT ? Diaries.Place.FARM : Diaries.Place.TOWN;
        };
    }

    /** For looking inside ({@code /nzw inside}): a floor of {@code lot}'s building, the {@code k}th of its floors round, in the world. */
    public static @Nullable BlockPos floorIn(net.minecraft.server.level.ServerLevel level, Lot lot, int k) {
        Optional<StructureTemplate> template = lot.building().flatMap(id -> level.getServer().getStructureTemplateManager().get(id));
        if (template.isEmpty()) {
            return null;
        }
        java.util.List<BlockPos> floors = Markers.floors(template.get());
        if (floors.isEmpty()) {
            return null;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        BlockPos local = floors.get(Math.floorMod(k * 7919, floors.size()));
        return StructureTemplate.calculateRelativePosition(settings, local).offset(origin(lot, template.get()));
    }

    /** The user's s2 with the camp on its roof (the holdout's) - and without, as it stands most times (tools/s2_bare.py). */
    static final Identifier S2 = dev.nookofthedead.NookOfTheDead.id("town/s2");
    static final Identifier S2_BARE = dev.nookofthedead.NookOfTheDead.id("town/s2_bare");

    /** Whether s2 on {@code lot} has its roof's camp: one time in five (the user, 2026-10-25: now and then, not every time). */
    static boolean roofCamp(Lot lot) {
        return Math.floorMod(lot.seed() >> 23, 5L) == 0;
    }

    private static void placeBuilding(WorldGenLevel level, Lot lot, StructureTemplate template, BoundingBox box, District district) {
        if (lot.building().map(S2::equals).orElse(false) && !roofCamp(lot)) {
            template = level.getLevel().getServer().getStructureTemplateManager().get(S2_BARE).orElse(template);
        }
        BlockPos origin = origin(lot, template);
        Traces.Fall fall = Traces.fall(lot, district);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror())
                .setBoundingBox(box).setRandom(RandomSource.create(lot.seed())).addProcessor(WEAR);
        boolean vanilla = lot.building().map(Vanilla::is).orElse(false);
        if (!vanilla) {                                                  // its own timber, colours and state on this lot
            boolean fire = Traces.burning(level.getSeed(), lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2);
            if (!Desert.landmark(lot)) {                                 // the landmarks as their builders made them
                lot.building().ifPresent(id -> settings.addProcessor(Repaint.of(id, lot.seed(), fire)));
            }
            settings.addProcessor(UNLIT);
            settings.addProcessor(KITCHEN);
            if (lot.building().map(id -> Props.boarded(id, lot.seed())).orElse(false)) {   // sheeted over by whoever held out in it
                settings.addProcessor(Props.boarded(lot.building().map(id -> Props.SHELTERS.contains(id.getPath())).orElse(false) ? 60 : 40));
            }
        }
        if (fall != null) {                                              // what fell in, never put up (Traces.collapse)
            settings.addProcessor(Traces.collapse(fall));
        }
        // where the sand took the town, all but its footings gone (Desert.erode); not the institute
        boolean desert = Desert.on(level.getSeed(), lot) && !lot.building().map(INSTITUTE::equals).orElse(false);
        int sheltered = desert ? Desert.sheltered(level.getSeed(), lot) + Desert.built(lot) : 0;
        if (desert) {
            settings.addProcessor(Desert.erode(lot, vanilla, sheltered));
        }
        if (vanilla) {
            // the air round a village house's floor (its template's bottom layer) would cut a trench in the ground about it
            // (the user, 2026-10-17: the village's houses stood in pits): the game's own air is left out, ours is not
            settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE);
            settings.addProcessor(BANKED);
            settings.addProcessor(UNLIT);      // their torches and lanterns out as well (the play test, 2026-10-19: still burning)
        }
        template.placeInWorld(level, origin, origin, settings, RandomSource.create(lot.seed() ^ box.minX() * 31L ^ box.minZ()), 2);
        if (lot.building().map(Vanilla::village).orElse(false)) {
            if (!desert) {
                grassOver(level, lot, box);
            }
            villagers(level, lot, box);
        }
        var manager = level.getLevel().getServer().getStructureTemplateManager();
        if (lot.building().map(INSTITUTE::equals).orElse(false)) {
            // its deep part straight under it, the shaft lined up: its top just under the grass's level, over the two layers of
            // earth the building was taken out with
            manager.get(INSTITUTE_DEEP).ifPresent(deep -> {
                BlockPos under = deepOrigin(lot, deep);
                // its blocks set as saved (knownShape): reshaped against their neighbours during generation, the refuge's
                // energy cables lost every join to a machine - nothing can be asked for power there - and the line stood
                // dead (the Nookworks session, 2026-10-19). A box of rock, it has no edge to the ground to mend.
                StructurePlaceSettings deeper = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror())
                        .setBoundingBox(box).setRandom(RandomSource.create(lot.seed() ^ 0xDEE9)).setKnownShape(true);
                deep.placeInWorld(level, under, under, deeper, RandomSource.create(lot.seed() ^ 0xDEE9 ^ box.minX() * 31L ^ box.minZ()), 2);
                Markers.apply(level, deep, under, deeper, box, lot.seed() ^ 0xDEE9);
                Refuge.leftBehind(level, under, deeper, box);     // the note and the pearls at the back of the refuge (0.3)
                Refuge.hull(level, under, deeper, box);           // and sealed all round in bulkheads but the way from the hall
                Refuge.workshop(level, under, deeper, box);       // the hall's empty half a workshop of Nookworks 1.1's machines
                RefugeRooms.draw(level, under, deeper, box);      // its rooms lived in, its lower floor, its door (2026-10-23)
                Props.research(level, deep, under, deeper, box, lot.seed() ^ 0xDEE9);   // the labs' books, the notes among them
            });
        }
        // one time in a few, a variant over it (Variants): the building's markers where it changed things give way to its own
        Optional<StructureTemplate> variant = lot.building().flatMap(id -> Variants.pick(manager, id, lot.seed()));
        variant.ifPresent(v -> v.placeInWorld(level, origin, origin, settings, RandomSource.create(lot.seed() ^ 0x5EED ^ box.minX() * 31L ^ box.minZ()), 2));
        Desert.finds(level, template, origin, settings, box, lot.seed());
        Traces.ruin(level, lot, template, origin, settings, box, district);
        Rooftops.draw(level, lot, origin, settings, box, fall == null && !desert);   // a last holdout on its roof (2026-10-23)
        Salvation.draw(level, lot, template, origin, settings, box, fall == null && !desert);   // the church of "salvation"
        if (desert) {
            // the sand drifted in over what is left - before the bodies and the dead are set down, not over them (the
            // review, 2026-10-19: bodies lay half in the sand)
            Desert.drift(level, lot, box, vanilla, sheltered);
        }
        Markers.stray(level, template, origin, settings, box, lot.seed());
        // no marker's thing where the floor fell in (a diary, the remains, set up again in mid-air)
        java.util.function.Predicate<BlockPos> fell = local -> {
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
            return fall != null && fall.gone(world) || desert && Desert.worn(lot, world, vanilla, sheltered);
        };
        if (variant.isEmpty()) {
            Markers.apply(level, template, origin, settings, box, lot.seed(), fell);
        } else {
            java.util.Set<BlockPos> changed = Variants.changed(variant.get());
            Markers.apply(level, template, origin, settings, box, lot.seed(), fell.or(changed::contains));
            Markers.apply(level, variant.get(), origin, settings, box, lot.seed() ^ 0x5EED, fell);
        }
        if (lot.building().map(INSTITUTE::equals).orElse(false)) {       // its books, left as they were left (0.3, Nookworks 1.1)
            Props.research(level, template, origin, settings, box, lot.seed());
        } else if (lot.building().map(id -> id.getPath().equals(Props.LIBRARY)).orElse(false)) {
            Props.library(level, template, origin, settings, box, lot.seed());
        } else if (lot.building().map(id -> Props.SHELTERS.contains(id.getPath())).orElse(false)) {
            Props.held(level, template, origin, settings, box, lot.seed());
        } else if (!vanilla) {
            Props.dress(level, template, origin, settings, box, lot.seed(),
                    Traces.burning(level.getSeed(), lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2));
        }
        // what life was left in it, over what its builder put in (Furnish): not where it burned, fell in, or the sand took it
        boolean burned = Traces.burning(level.getSeed(), lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2);
        if (!vanilla && !burned && fall == null && !desert && !lot.building().map(INSTITUTE::equals).orElse(false)) {
            Furnish.draw(level, template, origin, settings, box, lot.seed());
        }
    }

    /**
     * Where the lights left in a building are put out: a lantern (iron or copper) as Nookworks' unlit one, as the town's
     * other buildings have them (the review, 2026-10-18: S1 and S2 still had theirs burning - the look kept, only the light).
     */
    static final StructureProcessor UNLIT = new StructureProcessor() {
        private final java.util.Map<net.minecraft.world.level.block.Block, Optional<net.minecraft.world.level.block.Block>> out = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public StructureTemplate.@Nullable StructureBlockInfo process(net.minecraft.world.level.LevelReader level, BlockPos target, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings,
                @Nullable StructureTemplate template) {
            if (processed == null) {
                return null;
            }
            BlockState state = processed.state();
            Optional<net.minecraft.world.level.block.Block> unlit = this.out.computeIfAbsent(state.getBlock(), block -> {
                net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                if (id.getNamespace().equals("minecraft") && id.getPath().endsWith("torch") && !id.getPath().contains("redstone")) {
                    return Optional.of(Blocks.AIR);       // a torch burnt out, gone (the review, 2026-10-19: s2's still burned)
                }
                if (!id.getNamespace().equals("minecraft") || !id.getPath().endsWith("lantern") || id.getPath().contains("sea_")
                        || id.getPath().contains("jack_o")) {
                    return Optional.empty();
                }
                return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "unlit_" + id.getPath()));
            });
            if (unlit.isEmpty() && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                // a candle, a campfire, an oil lamp left burning: out, as the rest (the handed-over desert landmarks had them lit)
                return new StructureTemplate.StructureBlockInfo(processed.pos(),
                        state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false), processed.nbt());
            }
            return unlit.map(b -> new StructureTemplate.StructureBlockInfo(processed.pos(), b.withPropertiesOf(state), processed.nbt())).orElse(processed);
        }

        @Override
        public com.mojang.serialization.MapCodec<? extends StructureProcessor> codec() {
            return com.mojang.serialization.MapCodec.unit(this);          // made in place, never written out
        }
    };

    /**
     * Nookworks' pots and grills over a fire as Nookworks has them (the user, 2026-10-18: "鍋は…焼き網なしで焚火の上に置かれて
     * ることが多い。焼き網は何故か隣に置いてある"). The builders set Iron Pots straight on campfires and heaped Grills two
     * high; over a campfire the pot sits on its grill as one block (on_grill), a grill alone stands astride the fire
     * (over_fire), and whatever more was heaped over it comes down into that one block. Done over the whole column, which a
     * chunk always holds whole.
     */
    public static final StructureProcessor KITCHEN = new StructureProcessor() {
        private final net.minecraft.resources.Identifier pot = net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "iron_pot");
        private final net.minecraft.resources.Identifier grill = net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "grill");

        private boolean is(BlockState state, net.minecraft.resources.Identifier id) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
        }

        private BlockState set(BlockState state, String name) {
            return state.getBlock().getStateDefinition().getProperty(name) instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag
                    ? state.setValue(flag, true) : state;
        }

        @Override
        public StructureTemplate.@Nullable StructureBlockInfo process(net.minecraft.world.level.LevelReader level, BlockPos target, BlockPos reference,
                StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings,
                @Nullable StructureTemplate template) {
            return processed;
        }

        @Override
        public java.util.List<StructureTemplate.StructureBlockInfo> finalizeProcessing(net.minecraft.world.level.ServerLevelAccessor level,
                BlockPos position, BlockPos reference, java.util.List<StructureTemplate.StructureBlockInfo> original,
                java.util.List<StructureTemplate.StructureBlockInfo> processed, StructurePlaceSettings settings) {
            java.util.Map<BlockPos, Integer> at = new java.util.HashMap<>();
            for (int i = 0; i < processed.size(); i++) {
                at.put(processed.get(i).pos(), i);
            }
            java.util.List<StructureTemplate.StructureBlockInfo> out = new java.util.ArrayList<>(processed);
            for (StructureTemplate.StructureBlockInfo fire : processed) {
                if (!fire.state().is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
                    continue;
                }
                java.util.List<Integer> heap = new java.util.ArrayList<>();      // grills, and a pot on top, as they were piled
                StructureTemplate.StructureBlockInfo potOn = null;
                for (Integer i = at.get(fire.pos().above()); i != null; i = at.get(processed.get(i).pos().above())) {
                    BlockState state = processed.get(i).state();
                    if (!this.is(state, this.grill) && !this.is(state, this.pot)) {
                        break;
                    }
                    heap.add(i);
                    if (this.is(state, this.pot)) {
                        potOn = processed.get(i);
                        break;
                    }
                }
                if (heap.isEmpty()) {
                    continue;
                }
                BlockPos over = fire.pos().above();
                out.set(heap.getFirst(), potOn != null
                        ? new StructureTemplate.StructureBlockInfo(over, this.set(potOn.state(), "on_grill"), potOn.nbt())
                        : new StructureTemplate.StructureBlockInfo(over, this.set(processed.get(heap.getFirst()).state(), "over_fire"),
                                processed.get(heap.getFirst()).nbt()));
                for (int k = 1; k < heap.size(); k++) {
                    out.set(heap.get(k), new StructureTemplate.StructureBlockInfo(processed.get(heap.get(k)).pos(), Blocks.AIR.defaultBlockState(), null));
                }
            }
            return out;
        }

        @Override
        public com.mojang.serialization.MapCodec<? extends StructureProcessor> codec() {
            return com.mojang.serialization.MapCodec.unit(this);          // made in place, never written out
        }
    };

    /** Each template's highest block in each of its columns (x, z in the template): its roof there. */
    private static final java.util.Map<StructureTemplate, java.util.Map<Long, Integer>> ROOFS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * How high {@code template}, placed at {@code origin} as {@code settings} turns it, stands at the world column
     * ({@code x}, {@code z}): the y of its highest block there, or the lowest y it could be if it has none. From the
     * template alone - the same in every chunk, whatever has been made round it yet.
     */
    static int roof(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, int x, int z) {
        java.util.Map<Long, Integer> tops = ROOFS.computeIfAbsent(template, t -> {
            java.util.Map<Long, Integer> found = new java.util.HashMap<>();
            for (var palette : ((dev.nookofthedead.mixin.StructureTemplateAccessor) t).nookofthedead$palettes()) {
                for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                    if (!info.state().isAir() && !info.state().is(Blocks.STRUCTURE_VOID)) {
                        found.merge(BlockPos.asLong(info.pos().getX(), 0, info.pos().getZ()), info.pos().getY(), Math::max);
                    }
                }
                break;
            }
            return found;
        });
        int best = Integer.MIN_VALUE;
        for (var column : tops.entrySet()) {
            BlockPos local = BlockPos.of(column.getKey());
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
            if (at.getX() == x && at.getZ() == z) {
                best = Math.max(best, origin.getY() + column.getValue());
            }
        }
        return best == Integer.MIN_VALUE ? origin.getY() : best;
    }

    /**
     * A lot with no building on it. Rubble: a few heaps where something stood - stones and broken slabs piled higher at the
     * middle - in long grass and weeds. A field: furrows of dry earth gone to dead bushes. A yard: weeds and bushes.
     */
    private static void scatter(WorldGenLevel level, Lot lot, BoundingBox box, Plan plan) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BoundingBox foot = lot.footprint();
        int heaps = lot.use() == Use.YARD ? 0 : 2 + (int) Math.floorMod(lot.seed() >> 24, 4L);
        int[][] centres = new int[heaps][3];
        for (int i = 0; i < heaps; i++) {
            long h = hash(lot.seed(), i, 7, 6);
            centres[i][0] = foot.minX() + 4 + (int) Math.floorMod(h, (long) lot.size() - 8);
            centres[i][1] = foot.minZ() + 4 + (int) Math.floorMod(h >> 16, (long) lot.size() - 8);
            centres[i][2] = 2 + (int) Math.floorMod(h >> 32, 3L);             // its radius
        }
        for (int x = Math.max(foot.minX(), box.minX()); x <= Math.min(foot.maxX(), box.maxX()); x++) {
            for (int z = Math.max(foot.minZ(), box.minZ()); z <= Math.min(foot.maxZ(), box.maxZ()); z++) {
                int r = (int) Math.floorMod(hash(lot.seed(), x, z, 5), 1000);
                int y = plan.top()[x - plan.x0()][z - plan.z0()];          // a yard or rubble lies as the ground does
                if (lot.use() == Use.FIELD && Region.at(plan.seed(), x, z) == Region.FARMLAND) {
                    Country.field(level, lot, x, z, y, r, at);
                    continue;
                }
                if (lot.use() == Use.YARD && Desert.at(plan.seed(), x, z)) {
                    if (Traces.graveyard(lot)) {                             // no trees, flowers or bushes in the sand
                        Traces.graves(level, lot, at, x, z, y);
                    }
                    continue;
                }
                if (lot.use() == Use.YARD && Region.at(plan.seed(), x, z) != Region.CITY && Region.at(plan.seed(), x, z) != Region.SETTLEMENT) {
                    Country.wild(level, plan.seed(), x, z, y, box, at);
                    continue;
                }
                if (lot.use() == Use.FIELD) {
                    boolean furrow = Math.floorMod(z - foot.minZ(), 3) != 2;
                    level.setBlock(at.set(x, y, z), (furrow ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), 2);
                    if (furrow && r < 120) {
                        level.setBlock(at.set(x, y + 1, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);
                    }
                    continue;
                }
                if (lot.use() == Use.YARD) {
                    if (Traces.graveyard(lot) && Traces.graves(level, lot, at, x, z, y)) {
                        continue;
                    }
                    weeds(level, lot.seed(), at, x, z, y, r, 0.3, 10);
                    continue;
                }
                double heap = 0;                                            // how deep in a heap: 1 at its middle
                for (int[] c : centres) {
                    heap = Math.max(heap, 1 - Math.hypot(x - c[0], z - c[1]) / c[2]);
                }
                heap += (noise(lot.seed(), x, z, 2.0, 8) - 0.5) * 0.5;
                if (heap > 0.05) {
                    double kind = noise(lot.seed(), x, z, 3.0, 9);
                    Block stone = kind < 0.4 ? Blocks.COBBLESTONE : kind < 0.65 ? Blocks.MOSSY_COBBLESTONE : kind < 0.85 ? Blocks.STONE_BRICKS
                            : Blocks.CRACKED_STONE_BRICKS;
                    level.setBlock(at.set(x, y, z), Blocks.GRAVEL.defaultBlockState(), 2);
                    level.setBlock(at.set(x, y + 1, z), (heap > 0.3 ? stone : Blocks.COBBLESTONE_SLAB).defaultBlockState(), 2);
                    if (heap > 0.6) {
                        level.setBlock(at.set(x, y + 2, z), (r < 500 ? Blocks.COBBLESTONE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState(), 2);
                    }
                } else if (noise(lot.seed(), x, z, 4.0, 10) > 0.45 && r < 600) {
                    tuft(level, at, x, z, y, r);
                }
            }
        }
    }

    /** A number fixed by the seed and the place (and a salt per use), well mixed. */
    public static long hash(long seed, long a, long b, long salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }

    private Town() {}
}
