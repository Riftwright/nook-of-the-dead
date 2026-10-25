package dev.nookofthedead.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The capital's houses ({@link Capital}): one to a parcel, filling it wall to wall - a stone ground floor, timber-framed or
 * brick or stone storeys over it, the upper ones jutting a block over the lanes and some meeting over the alleys, roofs
 * hipped from every edge (whatever the parcel's shape, the roof comes down to every wall), chimneys on the party walls.
 * Inside: a floor to each storey, a ladder up beside a post, what was left in the rooms. Twenty-odd years on: windows
 * gone, roofs holed, moss and vines, the rooms as they were left.
 * <p>
 * Drawn a column at a time from what {@link Capital#column} says of it and its neighbours, so a house across a chunk's
 * edge is the same house from both sides.
 */
final class CapitalHouses {
    /** A storey's height: its floor and three of room. */
    static final int STOREY = 4;

    /** How a house is made: its walls' stuff, its frame's, its roof's. */
    record Style(Block ground, Block infill, Block frame, Block roof, Block roofFull, Block floor, boolean timber) {}

    static final Style[] STYLES = {
            new Style(Blocks.STONE_BRICKS, Blocks.CALCITE, Blocks.DARK_OAK_LOG, Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.SPRUCE_PLANKS, true),
            new Style(Blocks.COBBLESTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SPRUCE_LOG, Blocks.GRANITE_STAIRS, Blocks.GRANITE, Blocks.OAK_PLANKS, true),
            new Style(Blocks.STONE_BRICKS, Blocks.BRICKS, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.DARK_OAK_PLANKS, false),
            new Style(Blocks.ANDESITE, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_PLANKS, false),
            new Style(Blocks.COBBLESTONE, Blocks.DYED_TERRACOTTA.white(), Blocks.DARK_OAK_LOG, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, Blocks.SPRUCE_PLANKS, true),
            new Style(Blocks.COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.OAK_LOG, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICKS, Blocks.SPRUCE_PLANKS, true),
            new Style(Blocks.STONE_BRICKS, Blocks.MUD_BRICKS, Blocks.DARK_OAK_LOG, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICKS,
                    Blocks.OAK_PLANKS, true),
            new Style(Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES,
                    Blocks.DARK_OAK_PLANKS, false),
    };

    /** A house as a whole: where it stands, how high, how made, how far gone. */
    record House(long key, int level, int storeys, Style style, int ruin, long hash) {
        int top() {
            return this.level + STOREY * this.storeys;
        }
    }

    /** The house a column of a building (or a lane under one's upper floors) belongs to. */
    static House house(Capital.Site s, Capital.Column c) {
        long key = c.block() * 0x9E3779B97F4A7C15L ^ c.parcel();
        long h = Town.hash(s.seed(), c.block(), c.parcel(), 340);
        int level = Capital.parcelLevel(s, c.parcel());
        // how high and how made, from where the house's middle is - the same for all its columns (the review: a house over
        // a quarter's line came out two heights and two styles, open where they met)
        double[] place = Capital.parcelPlace(s, c.parcel());
        double r = place[0];
        double river = place[1];
        int base = r < 120 ? 4 : r < 195 ? 3 : 2;
        int storeys = base + (int) Math.floorMod(h >> 3, 3L);
        if (Math.floorMod(h >> 7, 100L) < 6) {
            storeys += 2;                                                   // now and then a tall narrow one over the rest
        }
        int style;
        long pick = Math.floorMod(h >> 11, 100L);
        if (r < 120) {
            style = pick < 35 ? 7 : pick < 60 ? 3 : pick < 80 ? 2 : 0;           // under the castle: stone, the well-to-do's
        } else if (Math.abs(river) < 60) {
            style = pick < 35 ? 5 : pick < 60 ? 1 : pick < 80 ? 4 : 6;           // by the river: the poorer, wood and plaster
        } else {
            style = pick < 30 ? 0 : pick < 50 ? 4 : pick < 65 ? 6 : pick < 80 ? 2 : pick < 90 ? 1 : 5;
        }
        int ruin = (int) Math.floorMod(h >> 17, 100L);
        return new House(key, level, storeys, STYLES[style], ruin, h);
    }

    /**
     * The footprint's edge at height {@code y}: how far in from the house's outside the column is there (the ground floor's,
     * or the upper floors' which reach further); negative where the house is not at that height.
     */
    static double edgeAt(Capital.Column c, House house, int y) {
        if (c.kind() == Capital.Kind.STREET) {
            return y >= house.level() + STOREY ? c.upper() : -1;
        }
        return y >= house.level() + STOREY ? c.upper() : c.edge();
    }

    /** How high column ({@code x}, {@code z}) of a house still stands: where it burnt, the upper storeys fell in, ragged. */
    static int standing(Capital.Site s, Capital.Column c, House house, int x, int z) {
        if (!CapitalDraw.burnt(s, c)) {
            return house.top();
        }
        // a house at a time: so many storeys gone, the same all round, the wall's top ragged by a block here and there
        // (in game, from above: a block-by-block jumble of stumps)
        int storeys = (int) Math.floorMod(house.hash() >> 41, (long) Math.max(1, house.storeys()));
        int rag = Town.noise(s.seed(), x, z, 3.0, 352) < 0.3 ? 1 : 0;
        return Math.max(house.level() + 2, house.top() - storeys * STOREY - rag);
    }

    /** How high a hipped roof stands over the walls' top at column {@code c} (whose roof edge is {@code e}). */
    static int roofRise(double e, double party) {
        return (int) Math.floor(Math.min(e, party) * 1.0);
    }

    /** What one chunk's drawing looks up more than once: the houses' ladders and ridges, and the plan past its window. */
    static final class Memo {
        final Map<Long, int[]> ladders = new HashMap<>();
        final Map<Long, Direction.Axis> ridges = new HashMap<>();
        final Map<Long, Capital.Column> columns = new HashMap<>();
    }

    /** How many houses in a hundred are gabled (the rest hipped). */
    static final int GABLED = 60;

    /** How far a gable's slope is looked along: past it, the roof stops rising (a house is never that wide). */
    private static final int REACH = 12;

    /** Whether column {@code n} is under {@code house}'s roof: the house's own, or a lane's under its jutting floors. */
    private static boolean roofed(Capital.Column n, House house) {
        return n != null && (n.kind() == Capital.Kind.BUILDING || n.kind() == Capital.Kind.STREET) && n.upper() > 0
                && (n.block() * 0x9E3779B97F4A7C15L ^ n.parcel()) == house.key();
    }

    /** The column at ({@code x}, {@code z}), window index ({@code a}, {@code b}): from the window, or the plan past it (kept). */
    private static Capital.Column column(Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z, Memo memo) {
        if (a >= 0 && b >= 0 && a < w.length && b < w.length) {
            return w[a][b];
        }
        long key = BlockPos.asLong(x, 0, z);
        Capital.Column c = memo.columns.get(key);
        if (c == null && !memo.columns.containsKey(key)) {
            c = Capital.column(s.seed(), x, z);
            memo.columns.put(key, c);
        }
        return c;
    }

    /**
     * The axis a gabled house's ridge runs along, or null for a hipped roof: the longer way through its parcel's seed, so a
     * narrow house deep from the lane shows its gable to it (PLAN.md's old town: houses with their gables to the street).
     */
    static Direction.Axis ridge(Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z, House house, Capital.Column c, Memo memo) {
        return memo.ridges.computeIfAbsent(house.key() ^ 0x5DEECE66DL, k -> {
            if (Math.floorMod(house.hash() >> 23, 100L) >= GABLED) {
                return null;
            }
            int[] p = Capital.parcelSeed(s, c.parcel());
            int pa = a + p[0] - x;
            int pb = b + p[1] - z;
            if (!roofed(column(s, w, pa, pb, p[0], p[1], memo), house)) {
                return null;                                                 // a parcel whose seed fell outside its house: hipped
            }
            int alongX = run(s, w, pa, pb, p[0], p[1], house, Direction.EAST, memo) + run(s, w, pa, pb, p[0], p[1], house, Direction.WEST, memo);
            int alongZ = run(s, w, pa, pb, p[0], p[1], house, Direction.SOUTH, memo) + run(s, w, pa, pb, p[0], p[1], house, Direction.NORTH, memo);
            if (alongX == alongZ) {
                return (house.hash() >> 29 & 1L) == 0 ? Direction.Axis.X : Direction.Axis.Z;
            }
            return alongX > alongZ ? Direction.Axis.X : Direction.Axis.Z;
        });
    }

    /** How many steps from ({@code x}, {@code z}) toward {@code d} stay under the house's roof (up to {@link #REACH}). */
    private static int run(Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z, House house, Direction d, Memo memo) {
        int k = 0;
        while (k < REACH && roofed(column(s, w, a + d.getStepX() * (k + 1), b + d.getStepZ() * (k + 1), x + d.getStepX() * (k + 1),
                z + d.getStepZ() * (k + 1), memo), house)) {
            k++;
        }
        return k;
    }

    /**
     * How high the roof stands over the walls' top at column ({@code a}, {@code b}): a hipped roof's from every edge, a gabled
     * one's from its eaves only - across the ridge, the nearer of the two ways out.
     */
    static int rise(Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z, House house, Capital.Column c, Memo memo) {
        Direction.Axis ridge = ridge(s, w, a, b, x, z, house, c, memo);
        if (ridge == null) {
            return roofRise(c.upper(), c.party());
        }
        Direction across = ridge == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        return Math.min(run(s, w, a, b, x, z, house, across, memo), run(s, w, a, b, x, z, house, across.getOpposite(), memo));
    }

    /**
     * Draws column ({@code a}, {@code b}) of the window {@code w} (the chunk's columns and one round them) where a house
     * stands - a building's, or a lane's under upper floors.
     */
    static void draw(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z, BoundingBox box,
            Memo memo, BlockPos.MutableBlockPos at) {
        Capital.Column c = w[a][b];
        House house = house(s, c);
        int top = house.top();
        Style st = house.style();
        boolean lane = c.kind() == Capital.Kind.STREET;
        int from = lane ? house.level() + STOREY : house.level();
        int[] found = memo.ladders.computeIfAbsent(house.key(), k -> {
            int[] l = ladder(s, c, house);
            return l == null ? new int[0] : l;                             // kept for a house without one too: not looked for again
        });
        int[] ladder = found.length == 0 ? null : found;
        boolean isLadder = ladder != null && ladder[0] == x && ladder[1] == z;
        boolean isPost = ladder != null && ladder[0] + 1 == x && ladder[1] == z;
        RandomSource random = RandomSource.create(Town.hash(s.seed(), x, z, 341));
        boolean burnt = CapitalDraw.burnt(s, c);
        int standing = standing(s, c, house, x, z);
        int lost = top - standing;
        // open beside: a neighbour not this house's at the ground floor, or above it - the wall goes there too (the review:
        // where the warp stretches a block, the edge skips past 1 and a slit opened in the wall)
        boolean openLow = false;
        boolean openHigh = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = w[a + d.getStepX()][b + d.getStepZ()];
            boolean same = n != null && (n.kind() == Capital.Kind.BUILDING || n.kind() == Capital.Kind.STREET && n.upper() > 0)
                    && house(s, n).key() == house.key();
            openLow |= !(same && n.kind() == Capital.Kind.BUILDING && n.edge() > 0);
            openHigh |= !(same && n.upper() > 0);
        }
        for (int y = from; y <= top; y++) {
            double e = edgeAt(c, house, y);
            if (e <= 0) {
                continue;
            }
            if (y > standing) {
                set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
                continue;
            }
            boolean floorLine = (y - house.level()) % STOREY == 0;
            boolean wall = e < 1 || c.party() < 1 || (y < house.level() + STOREY ? openLow : openHigh);
            BlockState state;
            if (wall) {
                state = wallBlock(s, w, a, b, c, house, x, y, z, e, floorLine, burnt);
            } else if (floorLine) {
                state = isLadder && y > house.level() && y < top ? ladderState(ladder) : isPost ? st.frame().defaultBlockState()
                        : (y == house.level() ? groundFloor(s, x, z, st) : floorBlock(st, burnt, s, x, y, z));
            } else if (isLadder && y < top) {
                state = ladderState(ladder);
            } else if (isPost) {
                state = (burnt ? Blocks.STRIPPED_DARK_OAK_LOG : st.frame()).defaultBlockState();
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
            if (!wall && !floorLine && !isLadder && !isPost && y > house.level() && c.kind() == Capital.Kind.BUILDING
                    && !doorway(s, w, a, b, house, x, z)) {
                CapitalRooms.furnish(level, s, w, a, b, c, house, ladder, x, y, z, burnt, box, at);
            }
        }
        if (lost == 0 && !burnt) {
            roof(level, s, w, a, b, c, house, x, z, box, at, burnt, memo);
        } else if (burnt && lost == 0 && Town.noise(s.seed(), x, z, 5.0, 347) < 0.2) {
            set(level, box, at.set(x, top + 1, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, (x + z) % 2 == 0 ? Direction.Axis.X : Direction.Axis.Z));   // a roof beam left
        } else if (Town.noise(s.seed(), x, z, 2.0, 351) < 0.3 && standing > house.level()) {
            // what came down lies on the floor it fell to
            int floor = house.level() + (standing - house.level()) / STOREY * STOREY;
            set(level, box, at.set(x, floor + 1, z), (Math.floorMod(x + z, 3) == 0 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.BLACKSTONE)
                    .defaultBlockState());
        }
    }

    /** What the wall is at ({@code x}, {@code y}, {@code z}): framed, filled, a window, a door. */
    private static BlockState wallBlock(Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, House house, int x, int y,
            int z, double e, boolean floorLine, boolean burnt) {
        Style st = house.style();
        int storey = (y - house.level()) / STOREY;
        int inStorey = (y - house.level()) % STOREY;
        Direction out = outside(s, w, a, b, house, y);
        boolean corner = corner(s, w, a, b, house, y);
        boolean groundFloor = storey == 0;
        long r = Math.floorMod(Town.hash(s.seed(), x * 31L + y, z, 342), 1000L);
        Block block;
        if (groundFloor) {
            block = st.ground();
        } else if (st.timber() && (corner || floorLine)) {
            block = st.frame();
        } else if (st.timber() && inStorey == 1 && (x + z) % 4 == 0) {
            block = st.frame();                                            // the posts of the frame, up the face
        } else if (!st.timber() && corner) {
            block = st.frame();
        } else {
            block = st.infill();
        }
        if (out != null && !corner && !floorLine) {
            boolean shop = groundFloor && CapitalDraw.front(s, w, a, b, out);
            int sill = groundFloor ? doorSill(s, w, a, b, house, x, z, out) : NO_DOOR;
            if (sill != NO_DOOR && (y == sill + 1 || y == sill + 2)) {
                // the door, both halves or neither: the column decides, not the height (the user, in game: half doors)
                boolean gone = Math.floorMod(Town.hash(s.seed(), x, z, 343), 100L) < 45;
                return gone ? Blocks.AIR.defaultBlockState() : doorState(st, out, y == sill + 1);
            }
            if (sill != NO_DOOR) {
                return weather(s, block, x, y, z, burnt).defaultBlockState();   // its sill and lintel
            }
            // windows two high, every other block along the face; the ground floor's fewer, a shop's wide
            int along = out.getAxis() == Direction.Axis.X ? z : x;
            boolean window = shop ? inStorey >= 1 && inStorey <= 2 && Math.floorMod(along, 3) != 0
                    : !groundFloor && inStorey >= 1 && inStorey <= 2 && Math.floorMod(along + storey, 2) == 0
                    || groundFloor && inStorey == 2 && Math.floorMod(along, 3) == 0;
            if (window) {
                if (burnt || r < 380) {
                    return Blocks.AIR.defaultBlockState();                   // its glass long gone
                }
                if (r < 470) {
                    return Blocks.SPRUCE_PLANKS.defaultBlockState();          // boarded up
                }
                return CapitalDraw.along(Blocks.STAINED_GLASS_PANE.gray().defaultBlockState(), out.getClockWise().getAxis());
            }
        }
        return weather(s, block, x, y, z, burnt).defaultBlockState();
    }

    /** The way out of a wall column at height {@code y}: toward open air (a street, a square, water, a lower house's roof). */
    static Direction outside(Capital.Site s, Capital.Column[][] w, int a, int b, House house, int y) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int na = a + d.getStepX();
            int nb = b + d.getStepZ();
            if (na < 0 || nb < 0 || na >= w.length || nb >= w.length) {
                continue;
            }
            Capital.Column n = w[na][nb];
            if (n == null || !(n.kind() == Capital.Kind.BUILDING || n.kind() == Capital.Kind.STREET && n.upper() > 0)) {
                if (n == null || n.kind() != Capital.Kind.CATHEDRAL && n.kind() != Capital.Kind.HALL && n.kind() != Capital.Kind.CASTLE) {
                    return d;
                }
                continue;
            }
            House other = house(s, n);
            if (other.key() == house.key()) {
                if (edgeAt(n, other, y) <= 0) {
                    return d;                                               // over the lane, below the floors that jut over it
                }
                continue;
            }
            if (other.top() < y || edgeAt(n, other, y) <= 0) {
                return d;                                                   // above the next house, or where it does not reach
            }
        }
        return null;
    }

    /** Whether the wall column is a corner of the house (open to two sides, or turning). */
    private static boolean corner(Capital.Site s, Capital.Column[][] w, int a, int b, House house, int y) {
        int open = 0;
        boolean xs = false;
        boolean zs = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int na = a + d.getStepX();
            int nb = b + d.getStepZ();
            if (na < 0 || nb < 0 || na >= w.length || nb >= w.length) {
                continue;
            }
            Capital.Column n = w[na][nb];
            boolean same = n != null && (n.kind() == Capital.Kind.BUILDING || n.kind() == Capital.Kind.STREET && n.upper() > 0)
                    && house(s, n).key() == house.key() && edgeAt(n, house, y) > 0;
            if (!same) {
                open++;
                if (d.getAxis() == Direction.Axis.X) {
                    xs = true;
                } else {
                    zs = true;
                }
            }
        }
        return xs && zs || open >= 3;
    }

    /** Whether a door is in the wall at ({@code x}, {@code z}), facing {@code out}: one a few blocks along each face. */
    private static boolean door(Capital.Site s, int x, int z, Direction out) {
        int along = out.getAxis() == Direction.Axis.X ? z : x;
        return Math.floorMod(along + Town.hash(s.seed(), out.getAxis() == Direction.Axis.X ? x : z, 0, 343), 7L) == 0;
    }

    static final int NO_DOOR = Integer.MIN_VALUE;

    /**
     * The sill of the door in ground-floor wall column ({@code a}, {@code b}) facing {@code out}, or {@link #NO_DOOR}: only
     * onto a street or a square whose paving is within a step of the floor (in game: doors raised over the street, doors
     * onto nothing). Where the street stands a block higher than the floor, the door is set up to it, a step down inside.
     */
    static int doorSill(Capital.Site s, Capital.Column[][] w, int a, int b, House house, int x, int z, Direction out) {
        if (!door(s, x, z, out)) {
            return NO_DOOR;
        }
        int na = a + out.getStepX();
        int nb = b + out.getStepZ();
        if (na < 0 || nb < 0 || na >= w.length || nb >= w.length || w[na][nb] == null) {
            return NO_DOOR;
        }
        Capital.Column n = w[na][nb];
        if (n.kind() != Capital.Kind.STREET && n.kind() != Capital.Kind.SQUARE) {
            return NO_DOOR;
        }
        int g = n.ground();
        if (g < house.level() - 1 || g > house.level() + 1) {
            return NO_DOOR;
        }
        return Math.max(house.level(), g);
    }

    /**
     * Whether column ({@code a}, {@code b}) inside a house may be just behind one of its doors, to be kept clear: a wall of
     * the house beside it with a street or square past it and a door's place in it (asked of the plan alone, so both
     * chunks of a wall on a chunk's edge agree).
     */
    static boolean doorway(Capital.Site s, Capital.Column[][] w, int a, int b, House house, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = at(s, w, a + d.getStepX(), b + d.getStepZ(), x + d.getStepX(), z + d.getStepZ());
            if (n == null || n.kind() != Capital.Kind.BUILDING || house(s, n).key() != house.key() || !(n.edge() < 1 || n.party() < 1)) {
                continue;
            }
            Capital.Column past = at(s, w, a + 2 * d.getStepX(), b + 2 * d.getStepZ(), x + 2 * d.getStepX(), z + 2 * d.getStepZ());
            if (past != null && (past.kind() == Capital.Kind.STREET || past.kind() == Capital.Kind.SQUARE)
                    && door(s, x + d.getStepX(), z + d.getStepZ(), d)) {
                return true;
            }
        }
        return false;
    }

    /** The column at window index ({@code a}, {@code b}), world ({@code x}, {@code z}): from the window, or the plan past it. */
    private static Capital.Column at(Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z) {
        if (a >= 0 && b >= 0 && a < w.length && b < w.length && w[a][b] != null) {
            return w[a][b];
        }
        return Capital.column(s.seed(), x, z);
    }

    private static BlockState doorState(Style st, Direction out, boolean lower) {
        Block door = st.ground() == Blocks.POLISHED_DEEPSLATE ? Blocks.DARK_OAK_DOOR : st.frame() == Blocks.SPRUCE_LOG ? Blocks.SPRUCE_DOOR
                : Blocks.OAK_DOOR;
        return door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out.getOpposite())
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, lower ? net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                        : net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                .setValue(BlockStateProperties.OPEN, true);
    }

    /** Twenty years on: mossy, cracked, blackened where it burnt. */
    static Block weather(Capital.Site s, Block block, int x, int y, int z, boolean burnt) {
        double v = Town.noise(s.seed(), x + y * 7, z - y * 5, 3.0, 344);
        if (burnt) {
            if (block == Blocks.CALCITE || block == Blocks.SMOOTH_SANDSTONE || block == Blocks.DYED_TERRACOTTA.white() || block == Blocks.MUD_BRICKS
                    || block == Blocks.SMOOTH_STONE) {
                return v < 0.4 ? Blocks.BLACKSTONE : v < 0.7 ? Blocks.DYED_TERRACOTTA.gray() : Blocks.TUFF;
            }
            if (block == Blocks.DARK_OAK_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.OAK_LOG || block == Blocks.STRIPPED_DARK_OAK_LOG) {
                return v < 0.55 ? Blocks.STRIPPED_DARK_OAK_LOG : v < 0.8 ? Blocks.POLISHED_BASALT : Blocks.BASALT;   // charred
            }
            if (block == Blocks.BRICKS || block == Blocks.MUD_BRICKS) {
                return v < 0.35 ? Blocks.POLISHED_BLACKSTONE_BRICKS : v < 0.5 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : block;
            }
            if (block == Blocks.SPRUCE_PLANKS || block == Blocks.OAK_PLANKS) {
                return Blocks.DARK_OAK_PLANKS;                              // charred through, the same dark all over (from above, no speckle)
            }
            if (block == Blocks.STONE_BRICKS || block == Blocks.COBBLESTONE) {
                return v < 0.45 ? Blocks.CRACKED_STONE_BRICKS : v < 0.75 ? Blocks.COBBLED_DEEPSLATE : Blocks.TUFF;
            }
        }
        if (block == Blocks.STONE_BRICKS) {
            return v < 0.25 ? Blocks.MOSSY_STONE_BRICKS : v > 0.85 ? Blocks.CRACKED_STONE_BRICKS : block;
        }
        if (block == Blocks.COBBLESTONE) {
            return v < 0.3 ? Blocks.MOSSY_COBBLESTONE : block;
        }
        if (block == Blocks.DEEPSLATE_BRICKS) {
            return v > 0.8 ? Blocks.CRACKED_DEEPSLATE_BRICKS : block;
        }
        if (block == Blocks.BRICKS) {
            return v > 0.9 ? Blocks.MUD_BRICKS : block;
        }
        return block;
    }

    private static BlockState groundFloor(Capital.Site s, int x, int z, Style st) {
        double v = Town.noise(s.seed(), x, z, 4.0, 345);
        return (v < 0.6 ? st.floor() : v < 0.8 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState();
    }

    private static BlockState floorBlock(Style st, boolean burnt, Capital.Site s, int x, int y, int z) {
        if (burnt) {
            double v = Town.noise(s.seed(), x + y * 3, z, 6.0, 346);
            return v < 0.35 ? Blocks.AIR.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState();   // burnt through, in holes
        }
        return st.floor().defaultBlockState();
    }

    private static BlockState ladderState(int[] ladder) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
    }

    /**
     * The house's ladder: the inside column nearest its parcel's seed with an inside column east of it for its post;
     * {x, z}, or null for a house too small to have one (its upper floors reached only through its neighbours').
     */
    static int[] ladder(Capital.Site s, Capital.Column c, House house) {
        int[] p = Capital.parcelSeed(s, c.parcel());
        for (int ring = 0; ring <= 3; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = p[0] + dx;
                    int z = p[1] + dz;
                    Capital.Column here = Capital.column(s.seed(), x, z);
                    Capital.Column post = Capital.column(s.seed(), x + 1, z);
                    if (inside(here, house) && inside(post, house)) {
                        return new int[] {x, z};
                    }
                }
            }
        }
        return null;
    }

    private static boolean inside(Capital.Column c, House house) {
        return c != null && c.kind() == Capital.Kind.BUILDING && (c.block() * 0x9E3779B97F4A7C15L ^ c.parcel()) == house.key()
                && c.edge() >= 1.5 && c.party() >= 1.5;
    }

    /**
     * The roof over column ({@code a}, {@code b}): hipped from every edge of the house, or gabled from two (its height over
     * the walls the column's distance in from the nearest eave - see {@link #rise}), a stair turned up the slope, the ridge
     * full blocks, a gable's end walled up to it; a hole here and there.
     */
    private static void roof(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, House house, int x,
            int z, BoundingBox box, BlockPos.MutableBlockPos at, boolean burnt, Memo memo) {
        int top = house.top();
        double e = c.upper();
        if (e <= 0) {
            return;
        }
        int rise = rise(s, w, a, b, x, z, house, c, memo);
        int y = top + 1 + rise;
        Style st = house.style();
        double hole = Town.noise(s.seed(), x, z, 5.0, 347);
        boolean holed = burnt ? hole < 0.75 : hole < (house.ruin() < 30 ? 0.25 : 0.06);
        // the attic: air between the walls' top and the roof - or, at a gable's end (an edge of the house the roof does not
        // come down to), the wall carried up under it, a window in its middle
        boolean gableEnd = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            gableEnd |= rise > 0 && !roofed(column(s, w, a + d.getStepX(), b + d.getStepZ(), x + d.getStepX(), z + d.getStepZ(), memo), house);
        }
        for (int k = top + 1; k < y; k++) {
            BlockState fill = Blocks.AIR.defaultBlockState();
            if (gableEnd) {
                boolean window = k == top + 2 && rise >= 3 && Math.floorMod(Town.hash(s.seed(), x, z, 353), 3L) == 0;
                fill = window ? Blocks.AIR.defaultBlockState()
                        : weather(s, st.timber() && k == top + 1 ? st.frame() : st.infill(), x, k, z, burnt).defaultBlockState();
            }
            set(level, box, at.set(x, k, z), fill);
        }
        if (holed) {
            if (burnt && hole < 0.3) {
                set(level, box, at.set(x, top + 1, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, (x + z) % 2 == 0 ? Direction.Axis.X : Direction.Axis.Z));
            }
            return;
        }
        // up the slope: the neighbour of the same house whose roof stands highest
        Direction up = null;
        int best = rise;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int na = a + d.getStepX();
            int nb = b + d.getStepZ();
            if (na < 0 || nb < 0 || na >= w.length || nb >= w.length) {
                continue;
            }
            Capital.Column n = w[na][nb];
            if (!roofed(n, house)) {
                continue;
            }
            int nr = rise(s, w, na, nb, x + d.getStepX(), z + d.getStepZ(), house, n, memo);
            if (nr > best) {
                best = nr;
                up = d;
            }
        }
        BlockState state;
        if (up == null) {
            state = st.roofFull().defaultBlockState();                     // the ridge
        } else {
            state = st.roof().defaultBlockState().setValue(StairBlock.FACING, up).setValue(StairBlock.HALF, Half.BOTTOM);
        }
        set(level, box, at.set(x, y, z), state);
        // down to the lowest roof of the same house beside it: where the warp steps the roof two, no gap between
        int low = y;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int na = a + d.getStepX();
            int nb = b + d.getStepZ();
            Capital.Column n = na < 0 || nb < 0 || na >= w.length || nb >= w.length ? null : w[na][nb];
            if (roofed(n, house)) {
                low = Math.min(low, top + 1 + rise(s, w, na, nb, x + d.getStepX(), z + d.getStepZ(), house, n, memo));
            }
        }
        for (int k = low + 1; k < y; k++) {
            set(level, box, at.set(x, k, z), st.roofFull().defaultBlockState());
        }
        // a chimney on a party wall, now and then
        if (c.party() < 1 && Math.floorMod(Town.hash(s.seed(), x, z, 348), 41L) == 0 && house.ruin() > 15) {
            for (int k = y; k <= y + 2; k++) {
                set(level, box, at.set(x, k, z), (k == y + 2 ? Blocks.BRICK_WALL : Blocks.BRICKS).defaultBlockState());
            }
        }
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalHouses() {}
}
