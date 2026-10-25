package dev.nookofthedead.world;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The fallen world's underground (the user, 2026-10-16: "ときどき地下への入り口…廃坑を改造したような、生活跡のある地下道…まれに
 * バリケード…トラップ…中のゾンビを始末したら、一時的に身を寄せる場所"): old mine galleries people made their own when the
 * surface was lost. Under some patches of the world ({@link #MINES} in a hundred {@link #CELL}-block squares, never under an
 * institute) a network of galleries runs at {@link #FLOOR}: along a grid of {@link #GRID}, each stretch there or not by the
 * seed, so they branch and end. A gallery is three wide and three high, propped with timber every few blocks, a rail here
 * and there. At some crossings a room where people lived - bedrolls, a cold fire, their stores, sometimes one of them
 * who never left ({@code remains}), and the dead standing about; some stretches are barricaded, some trapped (arrows from
 * a tripwire, a pit of points under a carpet - set for the dead, as deadly to the living). Here and there a shaft with a
 * ladder comes up to the open ground: the way in. The dead cannot climb ladders - clear it, light it, and it holds.
 * <p>
 * All of it fixed by the seed and the place, each chunk drawing its own part; it is a biome of its own below
 * {@link #DEPTHS} ({@link RegionBiomes}).
 */
public final class Tunnels {
    /** The galleries' floor; their ceiling three above it. */
    public static final int FLOOR = 36;
    public static final int HIGH = 3;
    /** Below this the world is the depths' biome. */
    public static final int DEPTHS = 52;
    /** The grid the galleries follow; a mine is a square of {@link #CELL} with them. */
    public static final int GRID = 20;
    public static final int CELL = 160;
    public static final int MINES = 38;
    /** Of the stretches in a mine, how many in a hundred are dug; of the crossings, how many a room, how many a way up. */
    public static final int DUG = 72;
    public static final int ROOMS = 22;
    public static final int SHAFTS = 12;
    /** Of the stretches, how many in a hundred barricaded; trapped. */
    public static final int BARRICADED = 7;
    public static final int TRAPPED = 6;
    /** Of the stretches, how many in a hundred have a door in the wall, a hideout behind it (0.3). */
    public static final int HIDEOUTS = 12;
    /**
     * Where the bloom took hold ({@link #bloomAt}): at most one crossing in each square of {@link #BLOOM_CELL} crossings a
     * side, and in that many in a hundred of those squares (the user, 2026-10-24: one every few blocks was too many - nine in
     * a hundred of the sewers' crossings, with nothing to keep them apart).
     */
    public static final int BLOOMS = 60;
    public static final int BLOOM_CELL = 4;
    /** How far along its stretch the hideout's door is: east-running ones and south-running ones apart, so no two meet. */
    static final int DOOR_EAST = 13;
    static final int DOOR_SOUTH = 7;

    /** Whether the square of {@link #CELL} at cell ({@code cx}, {@code cz}) has a mine under it. */
    public static boolean mine(long seed, int cx, int cz) {
        if (Math.floorMod(Town.hash(seed, cx, cz, 130), 100) >= MINES) {
            return false;
        }
        // never under an institute's grounds, whose deep part reaches down there
        for (int bx = Math.floorDiv(cx * CELL, Town.BLOCK); bx <= Math.floorDiv(cx * CELL + CELL - 1, Town.BLOCK); bx++) {
            for (int bz = Math.floorDiv(cz * CELL, Town.BLOCK); bz <= Math.floorDiv(cz * CELL + CELL - 1, Town.BLOCK); bz++) {
                if (Institute.at(seed, bx, bz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether grid point ({@code gx}, {@code gz}) is in a mine. */
    static boolean inMine(long seed, int gx, int gz) {
        if (Capital.near(seed, gx * GRID - 8, gz * GRID - 8, 17, 12)) {
            return false;                                                   // the capital has no galleries under it (2026-10-24)
        }
        return mine(seed, Math.floorDiv(gx * GRID, CELL), Math.floorDiv(gz * GRID, CELL));
    }

    /** Whether the stretch from grid point ({@code gx}, {@code gz}) to the next east ({@code east}) or south is dug. */
    public static boolean dug(long seed, int gx, int gz, boolean east) {
        int ox = east ? gx + 1 : gx;
        int oz = east ? gz : gz + 1;
        if (!inMine(seed, gx, gz) || !inMine(seed, ox, oz)) {
            return false;
        }
        return Math.floorMod(Town.hash(seed, gx, gz, east ? 131 : 132), 100) < DUG;
    }

    /** Whether grid point ({@code gx}, {@code gz}) has any stretch dug from it. */
    static boolean reached(long seed, int gx, int gz) {
        return dug(seed, gx, gz, true) || dug(seed, gx, gz, false) || dug(seed, gx - 1, gz, true) || dug(seed, gx, gz - 1, false);
    }

    /** For commands and tests: {@link #reached}, {@link #crossing}. */
    public static boolean reachedAt(long seed, int gx, int gz) {
        return reached(seed, gx, gz);
    }

    public static int crossingAt(long seed, int gx, int gz) {
        return crossing(seed, gx, gz);
    }

    /** What stands at a reached crossing: 0 nothing, 1 a room, 2 a way up. */
    static int crossing(long seed, int gx, int gz) {
        long h = Math.floorMod(Town.hash(seed, gx, gz, 133), 100);
        return h < ROOMS ? 1 : h < ROOMS + SHAFTS ? 2 : 0;
    }

    /** This chunk's part of the galleries. */
    static void draw(WorldGenLevel level, long seed, Town.Plan plan, BoundingBox box) {
        int gx0 = Math.floorDiv(box.minX() - 6, GRID);
        int gx1 = Math.floorDiv(box.maxX() + 6, GRID);
        int gz0 = Math.floorDiv(box.minZ() - 6, GRID);
        int gz1 = Math.floorDiv(box.maxZ() + 6, GRID);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                if (dug(seed, gx, gz, true)) {
                    stretch(level, seed, box, at, gx, gz, true);
                }
                if (dug(seed, gx, gz, false)) {
                    stretch(level, seed, box, at, gx, gz, false);
                }
            }
        }
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                if (!reached(seed, gx, gz)) {
                    continue;
                }
                if (bloomAt(seed, gx, gz)) {
                    bloomChamber(level, seed, box, gx, gz);
                    continue;
                }
                int kind = crossing(seed, gx, gz);
                if (kind == 1) {
                    room(level, seed, box, at, gx, gz);
                } else if (sinkholeAt(seed, gx, gz)) {
                    hollow(level, box, at, gx * GRID - 1, gx * GRID + 1, gz * GRID - 1, gz * GRID + 1, FLOOR, FLOOR + HIGH - 1, seed);
                    sinkhole(level, seed, box, gx, gz);
                } else {
                    hollow(level, box, at, gx * GRID - 1, gx * GRID + 1, gz * GRID - 1, gz * GRID + 1, FLOOR, FLOOR + HIGH - 1, seed);
                    if (kind == 2) {
                        shaft(level, seed, plan, box, at, gx, gz);
                    }
                }
            }
        }
    }

    /** A gallery from grid point ({@code gx}, {@code gz}) east or south to the next, with its props, rails, and now and then a barricade or a trap. */
    private static void stretch(WorldGenLevel level, long seed, BoundingBox box, BlockPos.MutableBlockPos at, int gx, int gz, boolean east) {
        int x0 = gx * GRID;
        int z0 = gz * GRID;
        int x1 = east ? x0 + GRID : x0;
        int z1 = east ? z0 : z0 + GRID;
        if (east) {
            hollow(level, box, at, x0, x1, z0 - 1, z0 + 1, FLOOR, FLOOR + HIGH - 1, seed);
        } else {
            hollow(level, box, at, x0 - 1, x0 + 1, z0, z1, FLOOR, FLOOR + HIGH - 1, seed);
        }
        long h = Town.hash(seed, gx, gz, east ? 134 : 135);
        boolean rails = (h & 3) == 0;
        Direction along = east ? Direction.EAST : Direction.SOUTH;
        Direction across = along.getClockWise();
        if (sewer(seed, gx, gz)) {
            sewer(level, seed, box, x0, z0, along, across);
        }
        for (int d = 2; d < GRID - 1 && !sewer(seed, gx, gz); d++) {
            int cx = x0 + along.getStepX() * d;
            int cz = z0 + along.getStepZ() * d;
            if (d % 4 == 0) {                                              // a timber prop: two posts, a beam
                for (int s : new int[] {-1, 1}) {
                    BlockPos post = new BlockPos(cx + across.getStepX() * s, FLOOR, cz + across.getStepZ() * s);
                    for (int y = 0; y < HIGH - 1; y++) {
                        put(level, box, post.above(y), Blocks.OAK_FENCE.defaultBlockState());
                    }
                }
                for (int s = -1; s <= 1; s++) {
                    put(level, box, new BlockPos(cx + across.getStepX() * s, FLOOR + HIGH - 1, cz + across.getStepZ() * s),
                            (Math.floorMod(Town.hash(seed, cx, cz, 136 + s), 9) == 0 ? Blocks.AIR : Blocks.SPRUCE_PLANKS).defaultBlockState());
                }
            } else if (rails && Math.floorMod(Town.hash(seed, cx, cz, 137), 5) != 0) {
                put(level, box, new BlockPos(cx, FLOOR, cz), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,
                        east ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH));
            }
        }
        if (!sewer(seed, gx, gz)) {
            dress(level, seed, box, x0, z0, along, across, hideoutAt(seed, gx, gz, east) ? (east ? DOOR_EAST : DOOR_SOUTH) : -GRID,
                    east ? 1 : -1);
        }
        if (hideoutAt(seed, gx, gz, east)) {
            hideout(level, seed, box, x0, z0, along, east ? DOOR_EAST : DOOR_SOUTH, east ? Direction.SOUTH : Direction.EAST);
        }
        int r = (int) Math.floorMod(h >> 8, 100);
        int mid = GRID / 2;
        int mx = x0 + along.getStepX() * mid;
        int mz = z0 + along.getStepZ() * mid;
        if (r < BARRICADED) {
            barricade(level, seed, box, mx, mz, across);
        } else if (r < BARRICADED + TRAPPED) {
            if (((h >> 16) & 1) == 0) {
                arrows(level, box, mx, mz, along, across);
            } else {
                pit(level, box, mx, mz, along, across, seed);
            }
        }
    }

    /**
     * What people left along a mine's gallery (the user, 2026-10-19: "ちょっと単調なので、もう少し飾りとか構造を足したい"):
     * cobwebs in the corners by the props, and between them now and then a barrel against the wall, a bedroll in a niche cut
     * into the rock, a fall of rubble, a plank table with a candle on it, stores stacked up. Nothing on the wall of a
     * hideout ({@code door} along, on {@code doorSide}: -1 or 1 across).
     */
    private static void dress(WorldGenLevel level, long seed, BoundingBox box, int x0, int z0, Direction along, Direction across,
            int door, int doorSide) {
        for (int d = 1; d < GRID; d++) {
            int cx = x0 + along.getStepX() * d;
            int cz = z0 + along.getStepZ() * d;
            long h = Town.hash(seed, cx, cz, 150);
            int side = (h & 1) == 0 ? -1 : 1;
            if (d % 4 == 1 || d % 4 == 3) {                                   // webs by a prop, up where the beam meets the wall
                if (Math.floorMod(h >> 1, 6) == 0) {
                    put(level, box, new BlockPos(cx + across.getStepX() * side, FLOOR + HIGH - 1, cz + across.getStepZ() * side),
                            Blocks.COBWEB.defaultBlockState());
                }
                continue;
            }
            if (d % 4 != 2 || d == GRID / 2) {                                // the props' places, and the middle (a barricade's, a trap's)
                continue;
            }
            if (side == doorSide && Math.abs(d - door) <= 3) {
                continue;                                                     // the hideout's wall: kept whole, its door clear
            }
            BlockPos wall = new BlockPos(cx + across.getStepX() * side, FLOOR, cz + across.getStepZ() * side);
            Direction out = side < 0 ? across : across.getOpposite();         // from the wall into the gallery
            int r = (int) Math.floorMod(h >> 4, 100);
            if (r < 10) {
                container(level, box, wall, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, out),
                        r < 5 ? "supplies" : "food", h >> 8);
            } else if (r < 17) {                                              // a bedroll in a niche cut into the wall
                BlockPos niche = wall.relative(out.getOpposite());
                for (BlockPos at : new BlockPos[] {niche, niche.relative(along)}) {
                    put(level, box, at.below(), Blocks.COARSE_DIRT.defaultBlockState());
                    put(level, box, at, Blocks.CAVE_AIR.defaultBlockState());
                    put(level, box, at.above(), Blocks.CAVE_AIR.defaultBlockState());
                }
                put(level, box, niche, Blocks.HAY_BLOCK.defaultBlockState());
                put(level, box, niche.above(), Blocks.CARPET.brown().defaultBlockState());
            } else if (r < 24) {                                              // fallen rubble
                put(level, box, wall, Blocks.GRAVEL.defaultBlockState());
                put(level, box, wall.above(), Blocks.COBBLESTONE_SLAB.defaultBlockState());
                put(level, box, wall.relative(along), Blocks.COBBLESTONE_SLAB.defaultBlockState());
            } else if (r < 30) {                                              // a table of planks, a candle long out
                put(level, box, wall, Blocks.SPRUCE_FENCE.defaultBlockState());
                put(level, box, wall.above(), Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
                put(level, box, wall.relative(along), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                put(level, box, wall.relative(along).above(), Blocks.CANDLE.defaultBlockState());
            } else if (r < 35) {                                              // stores stacked
                put(level, box, wall, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                put(level, box, wall.above(), Blocks.HAY_BLOCK.defaultBlockState());
                put(level, box, wall.relative(along), Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
    }

    /** Whether the stretch from grid point ({@code gx}, {@code gz}) east (or south) has a door in its wall, a hideout behind. */
    public static boolean hideoutAt(long seed, int gx, int gz, boolean east) {
        return dug(seed, gx, gz, east) && Math.floorMod(Town.hash(seed, gx, gz, east ? 151 : 152), 100) < HIDEOUTS;
    }

    /**
     * A door in the gallery's wall, {@code d} along it, and behind it a hideout someone made and left (the user: "カベにドア
     * とか、そしてその先に隠れ家（ただし人はいない）"): five by five, lined with planks, a rug over the floor (nothing is
     * born on it), a bed, a chest of what they hid, a barrel, a table; now and then a diary on a lectern. Nobody in it, alive
     * or dead. East-running stretches have it on their south side at {@link #DOOR_EAST}, south-running ones on their east
     * at {@link #DOOR_SOUTH}: clear of each other, of the crossings' rooms, of the props and the middle.
     */
    private static void hideout(WorldGenLevel level, long seed, BoundingBox box, int x0, int z0, Direction along, int d, Direction side) {
        long h = Town.hash(seed, x0, z0, 153 + d);
        int cx = x0 + along.getStepX() * d;
        int cz = z0 + along.getStepZ() * d;
        // the room: across 3..7 from the gallery's middle, along d-2..d+2; its shell one further out each way (the gallery's
        // own wall, across 2, stays rock but for the door)
        for (int a = -3; a <= 3; a++) {
            for (int s = 3; s <= 8; s++) {
                for (int y = FLOOR - 1; y <= FLOOR + HIGH; y++) {
                    BlockPos at = new BlockPos(cx + along.getStepX() * a + side.getStepX() * s, y, cz + along.getStepZ() * a + side.getStepZ() * s);
                    boolean shell = Math.abs(a) == 3 || s == 8 || y == FLOOR - 1 || y == FLOOR + HIGH;
                    if (!shell) {
                        put(level, box, at, Blocks.CAVE_AIR.defaultBlockState());
                    } else if (Math.abs(a) == 3 && (s == 3 || s == 8) && y >= FLOOR && y < FLOOR + HIGH) {
                        put(level, box, at, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());       // the corner posts
                    } else {
                        boolean beam = y == FLOOR + HIGH && (s == 4 || s == 6);
                        long r = Town.hash(seed, at.getX(), at.getY() * 13 + at.getZ(), 154);
                        // planks, and where they ran short what scrap they had (0.3, Nookworks 1.1): patched sheet, a bale
                        BlockState wall = Math.floorMod(r, 7) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                                : Math.floorMod(r, 11) == 1 && y > FLOOR - 1 && y < FLOOR + HIGH ? Props.patched()
                                : Math.floorMod(r, 17) == 2 && y > FLOOR - 1 && y < FLOOR + HIGH ? Props.bale(r >> 5)
                                : Blocks.OAK_PLANKS.defaultBlockState();
                        put(level, box, at, beam ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                                along.getAxis()) : wall);
                    }
                }
            }
        }
        // the door, where the gallery's wall is
        BlockPos door = new BlockPos(cx + side.getStepX() * 2, FLOOR, cz + side.getStepZ() * 2);
        BlockState shut = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, side.getOpposite());
        put(level, box, door.below(), Blocks.SPRUCE_PLANKS.defaultBlockState());
        put(level, box, door, shut.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(level, box, door.above(), shut.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        put(level, box, door.above(2), Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int a : new int[] {-1, 1}) {                                    // its frame on the gallery's side, a mat before it
            BlockPos post = door.relative(along, a);
            put(level, box, post, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            put(level, box, post.above(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            put(level, box, post.above(2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    along.getAxis()));
        }
        put(level, box, door.above(2), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                along.getAxis()));
        put(level, box, door.relative(side.getOpposite()), Blocks.CARPET.brown().defaultBlockState());
        // the rug, over every floor block the furniture leaves free
        Block[][] rugs = {{Blocks.CARPET.brown(), Blocks.CARPET.red()}, {Blocks.CARPET.gray(), Blocks.CARPET.green()},
                {Blocks.CARPET.brown(), Blocks.CARPET.green()}, {Blocks.CARPET.red(), Blocks.CARPET.brown()}};
        Block[] rug = rugs[(int) Math.floorMod(h, 4L)];
        for (int a = -2; a <= 2; a++) {
            for (int s = 3; s <= 7; s++) {
                boolean border = Math.abs(a) == 2 || s == 3 || s == 7;
                put(level, box, inside(cx, cz, along, side, a, s), (border ? rug[0] : rug[1]).defaultBlockState());
            }
        }
        // the bed along the far wall, its head to it
        BlockState bed = Blocks.BED.brown().defaultBlockState().setValue(BedBlock.FACING, side);
        int bedAt = (h & 16) == 0 ? -2 : 2;
        put(level, box, inside(cx, cz, along, side, bedAt, 7), bed.setValue(BedBlock.PART, BedPart.HEAD));
        put(level, box, inside(cx, cz, along, side, bedAt, 6), bed.setValue(BedBlock.PART, BedPart.FOOT));
        // their stores along the other wall
        int other = -bedAt;
        Direction toMiddle = other < 0 ? along : along.getOpposite();
        container(level, box, inside(cx, cz, along, side, other, 7), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, toMiddle),
                "stash", h >> 8);
        container(level, box, inside(cx, cz, along, side, other, 6), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP),
                "food", h >> 12);
        put(level, box, inside(cx, cz, along, side, other, 6).above(), Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 2 + (int) Math.floorMod(h >> 3, 2L)));
        put(level, box, inside(cx, cz, along, side, 0, 8).above(), Blocks.BOOKSHELF.defaultBlockState());    // cubbies in the walls
        put(level, box, inside(cx, cz, along, side, other, 5).relative(along, other < 0 ? -1 : 1).above(),
                Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, other < 0 ? along : along.getOpposite()));
        put(level, box, inside(cx, cz, along, side, 0, 7), Blocks.CRAFTING_TABLE.defaultBlockState());
        put(level, box, inside(cx, cz, along, side, 0, 7).above(), Blocks.CANDLE.defaultBlockState());   // nothing born on it
        BlockPos desk = inside(cx, cz, along, side, other, 4);
        if (((h >> 20) & 1) == 0) {                                           // what they wrote, left open
            if (box.isInside(desk)) {
                level.setBlock(desk, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, toMiddle)
                        .setValue(LecternBlock.HAS_BOOK, true), 2);
                if (level.getBlockEntity(desk) instanceof LecternBlockEntity lectern) {
                    lectern.setBook(Diaries.book(RandomSource.create(h)));
                }
            }
        } else {
            put(level, box, desk, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toMiddle.getOpposite()));   // a chair
        }
        put(level, box, inside(cx, cz, along, side, bedAt, 4), Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        // what they read and wrote, on the barrel by the stores (0.3, Nookworks 1.1's books): a diary, one of their notes
        RandomSource random = RandomSource.create(h ^ 0xB00CL);
        Props.pile(level, box, inside(cx, cz, along, side, other, 5).relative(along, other < 0 ? -1 : 1).above(2), 1 + (int) Math.floorMod(h >> 24, 3L),
                random.nextFloat() * 360, random, () -> switch (random.nextInt(5)) {
                    case 0, 1 -> Notes.any(Notes.CAMP, random);
                    case 2 -> Diaries.book(random);
                    default -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);
                });
    }

    /** The floor block {@code a} along and {@code s} across (to {@code side}) from ({@code cx}, {@code cz}), at the galleries' floor. */
    private static BlockPos inside(int cx, int cz, Direction along, Direction side, int a, int s) {
        return new BlockPos(cx + along.getStepX() * a + side.getStepX() * s, FLOOR, cz + along.getStepZ() * a + side.getStepZ() * s);
    }

    /** Whether the sewer crossing at grid point ({@code gx}, {@code gz}) is where a {@link dev.nookofthedead.entity.SewerBloom} grew. */
    public static boolean bloomAt(long seed, int gx, int gz) {
        int cx = Math.floorDiv(gx, BLOOM_CELL);
        int cz = Math.floorDiv(gz, BLOOM_CELL);
        long h = Town.hash(seed, cx, cz, 160);
        return Math.floorMod(h, 100) < BLOOMS && gx == cx * BLOOM_CELL + (int) Math.floorMod(h >> 8, (long) BLOOM_CELL)
                && gz == cz * BLOOM_CELL + (int) Math.floorMod(h >> 16, (long) BLOOM_CELL) && reached(seed, gx, gz) && sewer(seed, gx, gz);
    }

    /**
     * Where the sewer bloom took hold: the crossing grown into a vault nine across and six high, its old brick furred with
     * moss, the floor gone to mud and moss and standing water, roots hanging from the vault, a glow of lichen on the walls
     * (enough to see it by, and nothing else is born in it), a spore blossom shedding; the bones of what it caught; and in
     * the middle, rooted, the bloom ({@link dev.nookofthedead.entity.SewerBloom}). The galleries come in at the middle of
     * the sides they come from.
     */
    private static void bloomChamber(WorldGenLevel level, long seed, BoundingBox box, int gx, int gz) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        long h = Town.hash(seed, gx, gz, 161);
        boolean[] ways = {dug(seed, gx, gz - 1, false), dug(seed, gx, gz, false), dug(seed, gx - 1, gz, true), dug(seed, gx, gz, true)};
        int top = FLOOR + 5;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                boolean ring = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                boolean way = ring && Math.abs(dz) == 5 && Math.abs(dx) <= 1 && ways[dz < 0 ? 0 : 1]
                        || ring && Math.abs(dx) == 5 && Math.abs(dz) <= 1 && ways[dx < 0 ? 2 : 3];
                for (int y = FLOOR - 1; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                    long r = Math.floorMod(Town.hash(seed, at.getX(), at.getY() * 29 + at.getZ(), 162), 100);
                    if (y == FLOOR - 1 && !ring) {                               // the floor: mud, moss, a pool
                        double wet = Town.noise(seed, at.getX(), at.getZ(), 2.5, 163);
                        boolean middle = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                        boolean root = dx == 0 && dz == 0;
                        put(level, box, at, (root ? Blocks.ROOTED_DIRT : middle ? Blocks.MUD : wet > 0.68 ? Blocks.WATER : wet > 0.45 ? Blocks.MUD
                                : r < 25 ? Blocks.MOSS_BLOCK : r < 50 ? Blocks.PALE_MOSS_BLOCK : Blocks.MOSSY_STONE_BRICKS).defaultBlockState());
                    } else if (y == top + 1 || ring && !(way && y < FLOOR + HIGH)) {  // the walls and the vault, gone to moss
                        put(level, box, at, (r < 15 ? Blocks.MOSS_BLOCK : r < 30 ? Blocks.PALE_MOSS_BLOCK : r < 60 ? Blocks.MOSSY_STONE_BRICKS : r < 72
                                ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState());
                    } else if (!ring || way) {
                        put(level, box, at, (y == FLOOR - 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CAVE_AIR).defaultBlockState());   // no trench at a way in
                    }
                }
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos floor = new BlockPos(cx + dx, FLOOR, cz + dz);
                BlockPos vault = new BlockPos(cx + dx, top, cz + dz);
                long r = Math.floorMod(Town.hash(h, dx, dz, 164), 100);
                boolean clear = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;          // where it stands and lashes
                boolean wet = Town.noise(seed, floor.getX(), floor.getZ(), 2.5, 163) > 0.68 && !(Math.abs(dx) <= 1 && Math.abs(dz) <= 1);
                if (!wet && !clear && r < 30) {
                    put(level, box, floor, (r < 15 ? Blocks.MOSS_CARPET : Blocks.PALE_MOSS_CARPET).defaultBlockState());
                } else if (!wet && !clear && r < 34) {
                    Dress.bones(level, box, floor, Town.hash(h, dx, dz, 167), true);   // what it caught, lying
                } else if (!wet && !clear && r < 37) {
                    put(level, box, floor, Blocks.BIG_DRIPLEAF.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.BigDripleafBlock.FACING, Direction.from2DDataValue((int) (r & 3))));
                }
                long v = Math.floorMod(Town.hash(h, dx, dz, 165), 100);
                boolean over = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (over ? v < 60 : v < 12) {                                    // roots hanging from the vault, thickest over it
                    put(level, box, vault, Blocks.HANGING_ROOTS.defaultBlockState());
                } else if (v < 22) {
                    put(level, box, vault, Blocks.PALE_HANGING_MOSS.defaultBlockState());
                } else if (v % 6 == 0) {                                         // lichen on the vault's underside: its light
                    put(level, box, vault.above(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    put(level, box, vault, Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.UP), true));
                } else if (v < 23 && !clear) {                                   // glow berries, two long
                    put(level, box, vault, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(net.minecraft.world.level.block.CaveVines.BERRIES, v < 24));
                    put(level, box, vault.below(), Blocks.CAVE_VINES.defaultBlockState().setValue(net.minecraft.world.level.block.CaveVines.BERRIES, false));
                }
            }
        }
        put(level, box, new BlockPos(cx, top, cz), Blocks.SPORE_BLOSSOM.defaultBlockState());   // one, over it: its spores fall on it
        // lichen on the walls' faces, a little light to see it by
        for (int k = -3; k <= 3; k += 2) {
            for (Direction face : Direction.Plane.HORIZONTAL) {
                if (Math.floorMod(Town.hash(h, k, face.get2DDataValue(), 166), 3) == 0) {
                    continue;
                }
                int ax = face.getStepX() != 0 ? face.getStepX() * 4 : k;
                int az = face.getStepZ() != 0 ? face.getStepZ() * 4 : k;
                BlockPos at = new BlockPos(cx + ax, FLOOR + 2 + (int) Math.floorMod(h >> (k + 4), 2L), cz + az);
                put(level, box, at, Blocks.GLOW_LICHEN.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(face), true));
            }
        }
        BlockPos middle = new BlockPos(cx, FLOOR, cz);
        if (box.isInside(middle)) {                                              // the bloom, made by the chunk with its middle
            var bloom = dev.nookofthedead.registry.ZwEntities.SEWER_BLOOM.get().create(level.getLevel(),
                    net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (bloom != null) {
                bloom.snapTo(cx + 0.5, FLOOR, cz + 0.5, Math.floorMod(h, 360L), 0);
                level.addFreshEntity(bloom);
            }
            if (((h >> 12) & 1) == 0) {
                level.setBlock(new BlockPos(cx + 3, FLOOR, cz + 3), Blocks.CAVE_AIR.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + 3, FLOOR - 1, cz + 3), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                Markers.remains(level, new BlockPos(cx + 3, FLOOR, cz + 3), Direction.NORTH, RandomSource.create(h ^ 0xB0D1E5L));
            }
        }
    }

    /** Whether the galleries round grid point ({@code gx}, {@code gz}) are the town's old sewers rather than a mine's. */
    public static boolean sewer(long seed, int gx, int gz) {
        return Region.at(seed, gx * GRID, gz * GRID) == Region.CITY;
    }

    /**
     * A stretch of the town's sewer: its walls and vault of old brick (mossed and cracked here and there), and down the
     * middle of the floor a channel of standing water, a grate now and then over a side drain, a chain hanging.
     */
    private static void sewer(WorldGenLevel level, long seed, BoundingBox box, int x0, int z0, Direction along, Direction across) {
        for (int d = 1; d < GRID; d++) {
            int cx = x0 + along.getStepX() * d;
            int cz = z0 + along.getStepZ() * d;
            for (int s = -2; s <= 2; s++) {
                for (int y = FLOOR - 1; y <= FLOOR + HIGH; y++) {
                    boolean wall = Math.abs(s) == 2 || y == FLOOR + HIGH;
                    boolean floor = y == FLOOR - 1 && Math.abs(s) <= 1;
                    if (!wall && !floor) {
                        continue;
                    }
                    BlockPos at = new BlockPos(cx + across.getStepX() * s, y, cz + across.getStepZ() * s);
                    if (floor && s == 0 && d >= 2 && d <= GRID - 2) {
                        put(level, box, at, Blocks.WATER.defaultBlockState());     // the channel, still
                        continue;
                    }
                    long r = Math.floorMod(Town.hash(seed, at.getX(), at.getY() * 31 + at.getZ(), 143), 100);
                    Block brick = r < 18 ? Blocks.MOSSY_STONE_BRICKS : r < 30 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
                    if (wall && Math.abs(s) == 2 && y == FLOOR && d % 7 == 3 && r < 50) {
                        brick = Blocks.IRON_BARS;                                  // a grate over a side drain
                    }
                    put(level, box, at, brick.defaultBlockState());
                }
            }
            if (d % 9 == 4) {                                                       // a chain hanging from the vault
                put(level, box, new BlockPos(cx + across.getStepX(), FLOOR + HIGH - 1, cz + across.getStepZ()), Blocks.IRON_CHAIN.defaultBlockState());
            }
        }
    }

    /** Air from ({@code x0}, {@code y0}, {@code z0}) to ({@code x1}, {@code y1}, {@code z1}) in this chunk; a floor of trodden earth and gravel under it. */
    private static void hollow(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos at, int x0, int x1, int z0, int z1, int y0, int y1,
            long seed) {
        for (int x = Math.max(x0, box.minX()); x <= Math.min(x1, box.maxX()); x++) {
            for (int z = Math.max(z0, box.minZ()); z <= Math.min(z1, box.maxZ()); z++) {
                for (int y = y0; y <= y1; y++) {
                    level.setBlock(at.set(x, y, z), Blocks.CAVE_AIR.defaultBlockState(), 2);
                }
                double n = Town.noise(seed, x, z, 3.0, 138);
                Block floor = n < 0.45 ? Blocks.COARSE_DIRT : n < 0.7 ? Blocks.GRAVEL : n < 0.85 ? Blocks.PACKED_MUD : Blocks.COBBLESTONE;
                level.setBlock(at.set(x, y0 - 1, z), floor.defaultBlockState(), 2);
            }
        }
    }

    /** A barricade across a gallery: planks, fences, cobbles and a cart's worth of junk, with a gap they broke or none at all. */
    private static void barricade(WorldGenLevel level, long seed, BoundingBox box, int x, int z, Direction across) {
        for (int s = -1; s <= 1; s++) {
            for (int y = 0; y < HIGH; y++) {
                long h = Town.hash(seed, x + s * 7, z + y, 139);
                if (Math.floorMod(h, 100) < 35) {
                    continue;
                }
                Direction along = across.getClockWise();
                BlockState b = switch ((int) Math.floorMod(h >> 8, 7L)) {
                    case 0 -> Blocks.OAK_PLANKS.defaultBlockState();
                    case 1 -> Blocks.COBBLESTONE.defaultBlockState();
                    case 2 -> Blocks.OAK_FENCE.defaultBlockState();
                    case 3 -> Blocks.BARREL.defaultBlockState();
                    case 4 -> Props.bale(h >> 12);                                    // scrap (0.3, Nookworks 1.1)
                    case 5 -> Props.sheet(h >> 14, along, along.getAxis(), true);
                    default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
                };
                put(level, box, new BlockPos(x + across.getStepX() * s, FLOOR + y, z + across.getStepZ() * s), b);
            }
        }
    }

    /** A tripwire across the gallery and a dispenser of arrows in the wall beside it, set for the dead. */
    private static void arrows(WorldGenLevel level, BoundingBox box, int x, int z, Direction along, Direction across) {
        BlockPos left = new BlockPos(x + across.getStepX() * -2, FLOOR, z + across.getStepZ() * -2);
        BlockPos right = new BlockPos(x + across.getStepX() * 2, FLOOR, z + across.getStepZ() * 2);
        put(level, box, left, Blocks.STONE.defaultBlockState());
        put(level, box, right, Blocks.STONE.defaultBlockState());
        put(level, box, left.relative(across), Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, across)
                .setValue(TripWireHookBlock.ATTACHED, true));
        put(level, box, right.relative(across.getOpposite()), Blocks.TRIPWIRE_HOOK.defaultBlockState()
                .setValue(TripWireHookBlock.FACING, across.getOpposite()).setValue(TripWireHookBlock.ATTACHED, true));
        put(level, box, new BlockPos(x, FLOOR, z), Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, true)
                .setValue(across.getAxis() == Direction.Axis.X ? TripWireBlock.EAST : TripWireBlock.NORTH, true)
                .setValue(across.getAxis() == Direction.Axis.X ? TripWireBlock.WEST : TripWireBlock.SOUTH, true));
        // the dispenser in the wall one on, beside the stone the hook powers when the wire is crossed, shooting across the way
        BlockPos gun = right.relative(along);
        if (box.isInside(gun)) {
            level.setBlock(gun, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, across.getOpposite()), 2);
            if (level.getBlockEntity(gun) instanceof DispenserBlockEntity dispenser) {
                dispenser.setItem(0, new ItemStack(Items.ARROW, 9));
            }
        }
    }

    /** A pit of dripstone points across the gallery, two deep (a cover over it would hold: the dark has to do). */
    private static void pit(WorldGenLevel level, BoundingBox box, int x, int z, Direction along, Direction across, long seed) {
        for (int a = -1; a <= 1; a++) {
            for (int s = -1; s <= 1; s++) {
                BlockPos top = new BlockPos(x + along.getStepX() * a + across.getStepX() * s, FLOOR - 1, z + along.getStepZ() * a + across.getStepZ() * s);
                put(level, box, top, Blocks.CAVE_AIR.defaultBlockState());
                put(level, box, top.below(), Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP));
                put(level, box, top.below(2), Blocks.DRIPSTONE_BLOCK.defaultBlockState());
            }
        }
    }

    /**
     * A room at a crossing where people lived: seven across, four high, its corners propped; bedrolls along the walls,
     * a cold fire, a crafting table, barrels and a chest of what they kept, candles long out; one of them sometimes still
     * there; the dead standing about.
     */
    private static void room(WorldGenLevel level, long seed, BoundingBox box, BlockPos.MutableBlockPos at, int gx, int gz) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        hollow(level, box, at, cx - 3, cx + 3, cz - 3, cz + 3, FLOOR, FLOOR + HIGH, seed);
        int kind = roomKind(seed, gx, gz);
        if (kind == 1) {
            bunker(level, seed, box, gx, gz);
            return;
        }
        if (kind == 2) {
            nest(level, seed, box, gx, gz);
            return;
        }
        long h = Town.hash(seed, gx, gz, 140);
        RandomSource random = RandomSource.create(h);
        for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int y = 0; y <= HIGH; y++) {
                put(level, box, new BlockPos(cx + c[0], FLOOR + y, cz + c[1]), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            }
        }
        put(level, box, new BlockPos(cx, FLOOR, cz), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        for (int k = -2; k <= 2; k += 2) {                                 // bedrolls along the west and east walls
            for (int side : new int[] {-3, 3}) {
                if (Math.floorMod(h >> (k + 4 + side + 3), 3) == 0) {
                    continue;
                }
                put(level, box, new BlockPos(cx + side, FLOOR, cz + k), Blocks.HAY_BLOCK.defaultBlockState());
                put(level, box, new BlockPos(cx + side, FLOOR + 1, cz + k), (k == 0 ? Blocks.CARPET.lightGray() : Blocks.CARPET.brown()).defaultBlockState());
            }
        }
        put(level, box, new BlockPos(cx - 2, FLOOR, cz - 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        container(level, box, new BlockPos(cx - 1, FLOOR, cz - 3), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH),
                "supplies", h);
        container(level, box, new BlockPos(cx + 1, FLOOR, cz - 3), Blocks.CHEST.defaultBlockState(), (h & 1) == 0 ? "food" : "medical", h >> 4);
        put(level, box, new BlockPos(cx - 1, FLOOR + 1, cz - 3), Blocks.CANDLE.defaultBlockState());
        put(level, box, new BlockPos(cx + 2, FLOOR, cz + 3), Blocks.WATER_CAULDRON.defaultBlockState());
        // what the room holds is rolled the same from every chunk; the remains draw on a roll of their own, made only by
        // the chunk they fall in
        int dead = 1 + random.nextInt(3);
        int[] kinds = {1 + random.nextInt(2), 1 + random.nextInt(2), 1 + random.nextInt(2)};
        if (random.nextInt(3) == 0) {                                      // one who stayed
            BlockPos body = new BlockPos(cx + 2, FLOOR, cz - 2);
            if (box.isInside(body)) {
                Markers.remains(level, body, Direction.SOUTH, RandomSource.create(h ^ 0xB0D1E5L));
            }
        }
        int[][] spots = {{1, 2}, {-2, 1}, {0, -2}};
        for (int i = 0; i < dead; i++) {
            put(level, box, new BlockPos(cx + spots[i][0], FLOOR, cz + spots[i][1]), ZombieMarkerBlock.of(kinds[i], false));
        }
    }

    /** What a room is: 0 where people lived, 1 a bunker (under the town or a settlement only), 2 a rats' nest. */
    public static int roomKind(long seed, int gx, int gz) {
        int kind = (int) Math.floorMod(Town.hash(seed, gx, gz, 144), 10L);
        Region region = Region.at(seed, gx * GRID, gz * GRID);
        return kind < 2 && (region == Region.CITY || region == Region.SETTLEMENT) ? 1 : kind >= 2 && kind < 4 ? 2 : 0;
    }

    /**
     * A shelter dug under the town or a settlement: lined with deepslate and lit by nothing now, bunks along two walls,
     * the stores of people who meant to wait it out - more than a room's, and weapons - and the dead who did wait.
     */
    private static void bunker(WorldGenLevel level, long seed, BoundingBox box, int gx, int gz) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        long h = Town.hash(seed, gx, gz, 145);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean ring = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                boolean way = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1);     // the galleries come in at the middle of each side
                for (int y = FLOOR - 1; y <= FLOOR + HIGH + 1; y++) {
                    BlockPos at = new BlockPos(cx + dx, y, cz + dz);
                    if (y == FLOOR - 1 && !ring) {
                        put(level, box, at, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    } else if (y == FLOOR + HIGH + 1 || ring && !(way && y < FLOOR + HIGH)) {
                        put(level, box, at, (Math.floorMod(Town.hash(seed, at.getX(), at.getY() * 17 + at.getZ(), 146), 5) == 0
                                ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES).defaultBlockState());
                    }
                }
            }
        }
        for (int dz = -2; dz <= 2; dz += 4) {                                   // bunks along the east and west walls, clear of the ways in
            for (int side : new int[] {-3, 3}) {
                Direction head = side < 0 ? Direction.WEST : Direction.EAST;     // its head to the wall
                BlockState bed = Blocks.BED.gray().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, head);
                put(level, box, new BlockPos(cx + side, FLOOR, cz + dz),
                        bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                put(level, box, new BlockPos(cx + side - head.getStepX(), FLOOR, cz + dz),
                        bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
            }
        }
        container(level, box, new BlockPos(cx - 2, FLOOR, cz - 3), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH),
                "supplies", h);
        container(level, box, new BlockPos(cx - 1, FLOOR, cz - 3), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.SOUTH),
                "food", h >> 3);
        container(level, box, new BlockPos(cx + 1, FLOOR, cz - 3), Blocks.CHEST.defaultBlockState(), "weapon", h >> 6);
        container(level, box, new BlockPos(cx + 2, FLOOR, cz - 3), Blocks.CHEST.defaultBlockState(), "medical", h >> 9);
        put(level, box, new BlockPos(cx + 2, FLOOR, cz + 3), Blocks.CRAFTING_TABLE.defaultBlockState());
        put(level, box, new BlockPos(cx - 2, FLOOR + 1, cz - 3), Blocks.CANDLE.defaultBlockState());   // long out, on a barrel
        RandomSource books = RandomSource.create(h ^ 0xB00CL);                  // on the workbench, what they kept (Nookworks 1.1's books)
        Props.pile(level, box, new BlockPos(cx + 2, FLOOR + 1, cz + 3), 2, books.nextFloat() * 360, books,
                () -> books.nextInt(3) == 0 ? Notes.any(Notes.CAMP, books) : new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK));
        RandomSource random = RandomSource.create(h);
        int dead = 2 + random.nextInt(2);
        int[][] spots = {{1, 1}, {-1, -1}, {2, 0}};
        for (int i = 0; i < dead; i++) {
            put(level, box, new BlockPos(cx + spots[i][0], FLOOR, cz + spots[i][1]), ZombieMarkerBlock.of(1, false));
        }
        if (random.nextInt(2) == 0) {
            BlockPos body = new BlockPos(cx - 2, FLOOR, cz + 2);
            if (box.isInside(body)) {
                Markers.remains(level, body, Direction.EAST, RandomSource.create(h ^ 0xB0D1E5L));
            }
        }
    }

    /**
     * A rats' nest: the room's floor heaped with straw, bones, gnawed things and webs, and the rats of the plague in it -
     * a pack, waiting in the dark.
     */
    private static void nest(WorldGenLevel level, long seed, BoundingBox box, int gx, int gz) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        long h = Town.hash(seed, gx, gz, 147);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos at = new BlockPos(cx + dx, FLOOR, cz + dz);
                long r = Math.floorMod(Town.hash(h, dx, dz, 148), 100);
                if (r < 22) {
                    put(level, box, at, Blocks.HAY_BLOCK.defaultBlockState());
                } else if (r < 30) {
                    put(level, box, at, Blocks.BONE_BLOCK.defaultBlockState());
                } else if (r < 38) {
                    put(level, box, at, Blocks.COBWEB.defaultBlockState());
                } else if (r < 44) {
                    put(level, box, at, Blocks.CARPET.brown().defaultBlockState());
                }
                if (r < 12 && (Math.abs(dx) == 3 || Math.abs(dz) == 3)) {
                    put(level, box, at.above(), Blocks.COBWEB.defaultBlockState());
                }
            }
        }
        container(level, box, new BlockPos(cx + 3, FLOOR, cz + 3), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP),
                "food", h);
        BlockPos middle = new BlockPos(cx, FLOOR, cz);
        if (box.isInside(middle)) {                                             // the pack, made by the chunk with its middle
            RandomSource random = RandomSource.create(h);
            int rats = 4 + random.nextInt(3);
            for (int i = 0; i < rats; i++) {
                var rat = dev.nookofthedead.registry.ZwEntities.INFECTED_RAT.get().create(level.getLevel(),
                        net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
                if (rat != null) {
                    rat.snapTo(cx + 0.5 + random.nextInt(3) - 1, FLOOR + 1, cz + 0.5 + random.nextInt(3) - 1, random.nextFloat() * 360, 0);
                    level.addFreshEntity(rat);
                }
            }
        }
    }

    /**
     * A way up from a crossing to the open ground: a shaft with a ladder on its north wall, a rim of old stones and two posts
     * with a beam over the hole at the top; only where the ground there is open (no street, no building, no water), else it
     * stays a crossing.
     */
    private static void shaft(WorldGenLevel level, long seed, Town.Plan plan, BoundingBox box, BlockPos.MutableBlockPos at, int gx, int gz) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!open(seed, cx + dx, cz + dz)) {
                    return;
                }
            }
        }
        // the ground's own level there, as the chunk round the shaft's middle makes it: the same from every chunk
        int top = Town.plan(seed, cx - 8, cz - 8).top()[8][8];
        for (int x = cx - 1; x <= cx; x++) {
            for (int z = cz; z <= cz + 1; z++) {
                for (int y = FLOOR; y <= top + 3; y++) {
                    put(level, box, new BlockPos(x, y, z), Blocks.CAVE_AIR.defaultBlockState());
                }
            }
        }
        for (int y = FLOOR; y <= top; y++) {                               // the ladder up the north side
            for (int x = cx - 1; x <= cx; x++) {
                put(level, box, new BlockPos(x, y, cz - 1), Blocks.COBBLESTONE.defaultBlockState());
                put(level, box, new BlockPos(x, y, cz), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            }
        }
        for (int dx = -2; dx <= 1; dx++) {                                 // the rim
            for (int dz = -1; dz <= 2; dz++) {
                boolean hole = dx >= -1 && dx <= 0 && dz >= 0 && dz <= 1;
                if (!hole) {
                    long r = Town.hash(seed, cx + dx, cz + dz, 141);
                    put(level, box, new BlockPos(cx + dx, top, cz + dz), (Math.floorMod(r, 3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE)
                            .defaultBlockState());
                    if (Math.floorMod(r >> 4, 4) == 0) {
                        put(level, box, new BlockPos(cx + dx, top + 1, cz + dz), Blocks.COBBLESTONE_WALL.defaultBlockState());
                    }
                }
            }
        }
        for (int dx : new int[] {-2, 1}) {                                 // two posts and a beam
            put(level, box, new BlockPos(cx + dx, top + 1, cz + 2), Blocks.SPRUCE_FENCE.defaultBlockState());
            put(level, box, new BlockPos(cx + dx, top + 2, cz + 2), Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        for (int dx = -2; dx <= 1; dx++) {
            put(level, box, new BlockPos(cx + dx, top + 3, cz + 2), Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        put(level, box, new BlockPos(cx - 1, top + 1, cz + 1), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.FACING, Direction.NORTH));
    }

    /** Whether the gallery from grid point ({@code gx}, {@code gz}) east (or south) has the arrow trap in its middle. */
    public static boolean arrowsAt(long seed, int gx, int gz, boolean east) {
        long h = Town.hash(seed, gx, gz, east ? 134 : 135);
        int r = (int) Math.floorMod(h >> 8, 100);
        return dug(seed, gx, gz, east) && r >= BARRICADED && r < BARRICADED + TRAPPED && ((h >> 16) & 1) == 0;
    }

    /** Of the crossings with nothing at them, how many in a hundred the ground over them fell into (2026-10-23). */
    public static final int SINKHOLES = 16;
    /** How far out from a sinkhole's middle the ground over it must be clear (no building, no water). */
    static final int SINK_REACH = 4;

    /**
     * Whether the ground fell in over crossing ({@code gx}, {@code gz}) (the user, 2026-10-23, of holes in the
     * roads: a way down found, not made): one with nothing at it, under a street or open ground clear of buildings, water
     * and the second institute's grounds.
     */
    public static boolean sinkholeAt(long seed, int gx, int gz) {
        if (!reached(seed, gx, gz) || crossing(seed, gx, gz) != 0 || bloomAt(seed, gx, gz)
                || Math.floorMod(Town.hash(seed, gx, gz, 144), 100) >= SINKHOLES) {
            return false;
        }
        for (int dx = -SINK_REACH; dx <= SINK_REACH; dx++) {
            for (int dz = -SINK_REACH; dz <= SINK_REACH; dz++) {
                int x = gx * GRID + dx;
                int z = gz * GRID + dz;
                if (!(Town.ground(seed, x, z) == Town.Ground.LOT ? open(seed, x, z) : clear(seed, x, z)) || Traces.canal(seed, x, z) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the sinkhole over crossing ({@code gx}, {@code gz}) takes ({@code x}, {@code y}, {@code z}): a funnel, ragged. */
    static boolean sunk(long seed, int gx, int gz, int top, int x, int y, int z) {
        int dx = x - gx * GRID;
        int dz = z - gz * GRID;
        double r = y >= top - 2 ? 3.3 : y >= top - 8 ? 2.8 : 2.4;
        double rag = Math.floorMod(Town.hash(seed, x, z, 145 + (y >> 2)), 7) / 6.0 - 0.5;
        return y >= FLOOR + HIGH && dx * dx + dz * dz <= (r + rag) * (r + rag);
    }

    /**
     * The ground fallen into the galleries over crossing ({@code gx}, {@code gz}): a ragged funnel from the street down
     * through the rock, the road's edge broken round it and a rail or two of a fence someone put up, the heap of what came
     * down in the gallery below, and vines grown down one side - the way down, and back up.
     */
    private static void sinkhole(WorldGenLevel level, long seed, BoundingBox box, int gx, int gz) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        int top = Town.plan(seed, cx - 8, cz - 8).top()[8][8];
        for (int dx = -SINK_REACH; dx <= SINK_REACH; dx++) {
            for (int dz = -SINK_REACH; dz <= SINK_REACH; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                boolean any = false;
                for (int y = FLOOR + HIGH; y <= top + 4; y++) {
                    if (y > top ? sunk(seed, gx, gz, top, x, top, z) : sunk(seed, gx, gz, top, x, y, z)) {
                        put(level, box, new BlockPos(x, y, z), (y > top ? Blocks.AIR : Blocks.CAVE_AIR).defaultBlockState());
                        any = true;
                    }
                }
                long r = Town.hash(seed, x, z, 146);
                if (!any && dx * dx + dz * dz <= 20) {                     // the road's broken edge, a fence rail or two
                    Block edge = switch ((int) Math.floorMod(r, 4L)) {
                        case 0 -> Blocks.GRAVEL;
                        case 1 -> Blocks.COARSE_DIRT;
                        case 2 -> Blocks.CRACKED_STONE_BRICKS;
                        default -> Blocks.COBBLESTONE;
                    };
                    put(level, box, new BlockPos(x, top, z), edge.defaultBlockState());
                    if (Math.floorMod(r >> 5, 5) == 0) {
                        put(level, box, new BlockPos(x, top + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());
                    }
                }
            }
        }
        // the heap in the gallery: highest in the middle, a step down to either side
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int high = Math.abs(dx) + Math.abs(dz) <= 1 ? 2 : Math.abs(dx) + Math.abs(dz) <= 2 ? 1 : 0;
                for (int y = FLOOR; y < FLOOR + high; y++) {
                    long r = Town.hash(seed, cx + dx, cz + dz, 147 + y);
                    put(level, box, new BlockPos(cx + dx, y, cz + dz), (Math.floorMod(r, 3) == 0 ? Blocks.COBBLED_DEEPSLATE
                            : Math.floorMod(r, 3) == 1 ? Blocks.COARSE_DIRT : Blocks.COBBLESTONE).defaultBlockState());
                }
            }
        }
        // vines down the north side, from just under the street to the hole's foot
        for (int y = FLOOR + HIGH; y < top; y++) {
            int k = 0;
            while (k < SINK_REACH && sunk(seed, gx, gz, top, cx, y, cz - k - 1)) {
                k++;
            }
            if (sunk(seed, gx, gz, top, cx, y, cz - k)) {
                put(level, box, new BlockPos(cx, y, cz - k), Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.NORTH, true));
            }
        }
    }

    /** Whether column ({@code x}, {@code z}) is open ground: no street or alley, no building's lot, no lake. */
    public static boolean open(long seed, int x, int z) {
        if (Town.ground(seed, x, z) != Town.Ground.LOT) {
            return false;
        }
        return clear(seed, x, z);
    }

    /** Whether nothing stands at column ({@code x}, {@code z}): no building's lot, no lake, no crag, no cave's hill. */
    static boolean clear(long seed, int x, int z) {
        if (Ascent.near(seed, x, z, 1)) {
            return false;
        }
        int bx = Math.floorDiv(x, Town.BLOCK);
        int bz = Math.floorDiv(z, Town.BLOCK);
        Terrain.Lake lake = Terrain.lake(seed, bx, bz);
        if (lake != null && lake.in(x, z) > -8) {
            return false;
        }
        Terrain.Sea sea = Terrain.sea(seed, x, z);
        Terrain.Peak peak = Terrain.peak(seed, x, z);
        if (sea != null && sea.in(x, z) > -12 || peak != null && peak.rise(x, z) > 4) {
            return false;                                                // a sea's water or beach, a mountain's side
        }
        if (Terrain.rock(seed, x, z) > 0) {
            return false;                                                // a crag stands there
        }
        Terrain.Cave cave = Terrain.cave(seed, bx, bz);
        if (cave != null && cave.hill(seed, x, z) > 0) {
            return false;
        }
        for (Town.Lot lot : Town.lots(seed, bx, bz)) {
            if (lot.use() == Town.Use.BUILDING && lot.covers(x, z)) {
                return false;
            }
        }
        return true;
    }

    private static void put(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state) {
        if (box.isInside(pos)) {
            level.setBlock(pos, state, 2);
        }
    }

    private static void container(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state, String loot, long seed) {
        if (!box.isInside(pos)) {
            return;
        }
        level.setBlock(pos, state, 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, NookOfTheDead.id("chests/" + loot)), seed);
        }
    }

    private Tunnels() {}
}
