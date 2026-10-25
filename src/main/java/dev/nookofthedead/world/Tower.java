package dev.nookofthedead.world;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Tower (PLAN_0.3.md; the user, 2026-10-19: "何度か中継点を得て、限界高度ギリギリの天空基地"): a shaft of concrete
 * rising from the lift room at the back of the second institute ({@link Ascent}) to the sky base ({@link SkyBase}), and on
 * it three relay stations, each a deck round the shaft with its rooms.
 * <ul>
 * <li>Up to the first, {@link #R1}: the lift works - the Elevator in the lift room's floor, one on a landing half way,
 *     one in the first relay's floor.</li>
 * <li>On to the second, {@link #R2}: the lift is broken; the way is outside, a stair wound round the shaft, two wide,
 *     bars at its edge, landings at its corners, the wind.</li>
 * <li>On to the third, {@link #R3}: inside the shaft, a ladder up its north wall, a ledge to rest on every twenty
 *     blocks - the dead on them.</li>
 * <li>From the third, the lift works again: the last ride, up into the base.</li>
 * </ul>
 * Each relay has a lodestone (a Nookworks Lode Pearl bound to it brings one back after a death - dying in the If sends one
 * home), a chest with Lode Pearls bound to it, its stores, beds, the dead. Built by code, each chunk its part.
 */
public final class Tower {
    /** The relays' decks (their floor's level). */
    public static final int R1 = 160;
    public static final int R2 = R1 + 99;
    public static final int R3 = R2 + 100;
    /** The base's deck: the last lift from {@link #R3} (within an Elevator's reach). */
    public static final int BASE = R3 + 45;
    /** The shaft's outer half width (its walls), and the decks'. */
    static final int CORE = 4;
    static final int DECK = 13;
    /** The stair round the shaft: its two lanes, and how much it climbs a side. */
    static final int LANE_IN = 5;
    static final int LANE_OUT = 6;
    static final int SIDE = 9;
    /** The ledges in the ladder shaft, this far apart. */
    static final int LEDGE = 20;
    /** Lode Pearls left at each relay, bound to its lodestone. */
    public static final int PEARLS = 2;

    /** The shaft's middle: over the lift room. */
    public static BlockPos centre(Ascent.Site site) {
        return new BlockPos(site.x(), 0, site.z() - 8);
    }

    /** The landing half way up to the first relay. */
    public static int landing(Ascent.Site site) {
        return site.ground() + (R1 - site.ground()) / 2;
    }

    /** A relay's room: in it, before its lodestone. */
    public static BlockPos relay(Ascent.Site site, int deck) {
        BlockPos c = centre(site);
        return new BlockPos(c.getX() + 9, deck + 1, c.getZ());
    }

    /** A relay's lodestone, at the back of its room: its pearls are bound to it. */
    public static BlockPos lodestone(Ascent.Site site, int deck) {
        BlockPos c = centre(site);
        return new BlockPos(c.getX() + 11, deck + 1, c.getZ());
    }

    static void draw(WorldGenLevel level, long seed, Ascent.Site site, BoundingBox box) {
        BlockPos c = centre(site);
        if (box.maxX() < c.getX() - DECK || box.minX() > c.getX() + DECK || box.maxZ() < c.getZ() - DECK || box.minZ() > c.getZ() + DECK) {
            return;
        }
        shaft(level, seed, site, box, c);
        silhouette(level, site, box, c);
        stair(level, seed, box, c);
        ladder(level, box, c);
        for (int deck : new int[] {R1, R2, R3}) {
            deck(level, seed, site, box, c, deck);
        }
    }

    /** The shaft: its walls from the institute's roof to the base; inside, what each stretch of the way is. */
    private static void shaft(WorldGenLevel level, long seed, Ascent.Site site, BoundingBox box, BlockPos c) {
        int g = site.ground();
        int roof = g + Ascent.STOREY;
        int e1 = landing(site);
        for (int dx = -CORE; dx <= CORE; dx++) {
            for (int dz = -CORE; dz <= CORE; dz++) {
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                if (!box.isInside(x, g, z)) {
                    continue;
                }
                boolean wall = Math.abs(dx) == CORE || Math.abs(dz) == CORE;
                for (int y = roof; y < BASE; y++) {
                    BlockState state;
                    if (wall) {
                        state = wall(seed, x, y, z, dx, dz, g);
                    } else {
                        state = inside(seed, x, y, z, dx, dz, e1, g);
                        boolean filled = y > R1 + 5 && y < R2 || y > R3 + 4;
                        boolean behindWindow = (dx == 0 || dz == 0) && Math.max(Math.abs(dx), Math.abs(dz)) == CORE - 1
                                && Math.floorMod(y - g, 20) == 10;
                        if (filled && behindWindow) {
                            state = Blocks.OCHRE_FROGLIGHT.defaultBlockState();     // a light behind each window where it is solid
                        } else if (filled && Math.max(Math.abs(dx), Math.abs(dz)) == CORE - 1) {
                            state = Blocks.CONCRETE.gray().defaultBlockState();     // the dark core behind the lattice
                        }
                    }
                    put(level, box, x, y, z, state);
                }
            }
        }
        // the doors out onto the decks: two high, in the middle of the east wall
        for (int deck : new int[] {R1, R2, R3}) {
            for (int y = deck + 1; y <= deck + 2; y++) {
                put(level, box, c.getX() + CORE, y, c.getZ(), Blocks.AIR.defaultBlockState());
                put(level, box, c.getX() + CORE, y, c.getZ() + 1, Blocks.AIR.defaultBlockState());
            }
        }
        // a lamp still burning in each closed space of the shaft - the landing, the rooms over the first and third relays'
        // floors, the second's, each ledge: lit, nothing is born there (the review, 2026-10-19: the landing filled with the
        // dead, and the lift set one down among them)
        for (int y : java.util.List.of(e1 + 1, R1 + 1, R2 + 1, R3 + 1)) {
            put(level, box, c.getX() + 2, y, c.getZ() + 2, Blocks.LANTERN.defaultBlockState());
            put(level, box, c.getX() - 2, y, c.getZ() + 2, Blocks.LANTERN.defaultBlockState());
        }
        for (int y = R2 + LEDGE; y < R3; y += LEDGE) {
            put(level, box, c.getX() + 2, y + 1, c.getZ() + 2, Blocks.LANTERN.defaultBlockState());
            put(level, box, c.getX() - 2, y + 1, c.getZ() - 2, Blocks.LANTERN.defaultBlockState());
        }
        // the Elevators: the landing half way, the first relay's floor, the third's (the base's is its own)
        for (int y : new int[] {e1, R1, R3}) {
            put(level, box, c.getX(), y, c.getZ(), dev.nookworks.registry.ModBlocks.ELEVATOR.get().defaultBlockState());
        }
    }

    /**
     * The shaft's wall at ({@code dx}, {@code dz}), {@code y} up: concrete, a band every twenty with lamps set in it like a
     * mast's; where the shaft is solid inside, a lattice of andesite and bars over its dark core, so from afar it reads as
     * the frame it is (the visual review, 2026-10-19: a plain grey column, black in the base's shadow).
     */
    private static BlockState wall(long seed, int x, int y, int z, int dx, int dz, int g) {
        boolean band = Math.floorMod(y - g, 20) == 0;
        boolean window = (dx == 0 || dz == 0) && Math.floorMod(y - g, 20) == 10;
        if (window) {
            return Blocks.GLASS.defaultBlockState();
        }
        if (band) {
            return (Math.floorMod(dx + dz, 2) == 0 ? Blocks.SEA_LANTERN : Blocks.IRON_BLOCK).defaultBlockState();
        }
        boolean filled = y > R1 + 5 && y < R2 || y > R3 + 4;
        if (filled) {
            int along = Math.abs(dx) == CORE ? dz : dx;
            if (Math.abs(along) == CORE || along == 0 || Math.floorMod(y - g, 5) == 0) {
                return Blocks.POLISHED_ANDESITE.defaultBlockState();
            }
            return bars(Math.abs(dx) == CORE ? Direction.Axis.Z : Direction.Axis.X);
        }
        long r = Math.floorMod(Town.hash(seed, x, y * 7 + z, 180), 100);
        return (r < 6 ? Blocks.CRACKED_STONE_BRICKS : r < 9 ? Blocks.CONCRETE.gray() : Blocks.CONCRETE.lightGray()).defaultBlockState();
    }

    /**
     * What gives the Tower its shape: a foot of stone round it on the institute's roof, buttressed down to the yard at its
     * back; ribs up its corners; two aerial platforms on the west between the second relay and the third, a light on
     * each (the visual review, 2026-10-19). None of it touches the stair, the ladder, the lifts or the doors.
     */
    private static void silhouette(WorldGenLevel level, Ascent.Site site, BoundingBox box, BlockPos c) {
        int g = site.ground();
        int roof = g + Ascent.STOREY;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < 5) {
                    continue;
                }
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                boolean outside = z < site.z() - Ascent.HALF_Z;                 // past the building's back wall: down to the yard
                int from = outside ? g + 1 : roof;
                int to = ring == 5 ? roof + 4 : roof + 1;
                for (int y = from; y <= to; y++) {
                    put(level, box, x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
                Direction in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                put(level, box, x, to + 1, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, in.getOpposite()));
            }
        }
        for (int sx : new int[] {-5, 5}) {                                     // ribs at the corners
            for (int sz : new int[] {-5, 5}) {
                for (int y = roof + 6; y < BASE - 2; y++) {
                    boolean nearDeck = Math.abs(y - R1) <= 5 || Math.abs(y - R2) <= 5 || Math.abs(y - R3) <= 5 || y - 1 == R3 + 5;
                    if (nearDeck || y > R1 && y < R2) {
                        continue;                                              // clear of the decks, and of the stair's corners
                    }
                    put(level, box, c.getX() + sx, y, c.getZ() + sz, Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        for (int y : new int[] {R2 + 30, R2 + 70}) {                           // the aerial platforms
            for (int dx = -7; dx <= -5; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    put(level, box, c.getX() + dx, y, c.getZ() + dz, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                }
            }
            for (int dz = -3; dz <= 3; dz++) {
                put(level, box, c.getX() - 8, y + 1, c.getZ() + dz, bars(Direction.Axis.Z));
            }
            for (int dz : new int[] {-2, 2}) {
                for (int k = 1; k <= 4; k++) {
                    put(level, box, c.getX() - 7, y + k, c.getZ() + dz, Blocks.IRON_BARS.defaultBlockState());
                }
                put(level, box, c.getX() - 7, y + 5, c.getZ() + dz, Blocks.END_ROD.defaultBlockState());
            }
        }
    }

    /** What is inside the shaft at ({@code dx}, {@code dz}) from its middle, {@code y} up. */
    private static BlockState inside(long seed, int x, int y, int z, int dx, int dz, int e1, int g) {
        if (y == e1 || y == R1 || y == R3) {                               // a floor with its Elevator (set after)
            return (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? Blocks.IRON_BLOCK : Blocks.SMOOTH_STONE).defaultBlockState();
        }
        if (y > R1 && y <= R1 + 4 || y > R3 && y <= R3 + 4) {               // a relay's room in the shaft
            return Blocks.AIR.defaultBlockState();
        }
        if (y == R1 + 5) {                                                  // where the lift broke: what came down, stopped
            long r = Math.floorMod(Town.hash(seed, x, y * 5 + z, 181), 100);
            return (r < 40 ? Blocks.COBBLESTONE : r < 70 ? Blocks.COARSE_DIRT : r < 85 ? Blocks.IRON_BLOCK : Blocks.ANDESITE).defaultBlockState();
        }
        if (y > R1 + 5 && y < R2 || y > R3 + 4) {                           // the broken stretch, and up to the base: filled
            return Blocks.CONCRETE.lightGray().defaultBlockState();
        }
        if (y == R2) {
            return dx == 0 && dz == -3 ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)
                    : Blocks.SMOOTH_STONE.defaultBlockState();
        }
        if (y > R2 && y < R3 && Math.floorMod(y - R2, LEDGE) == 0) {        // a ledge to rest on, the ladder through it
            return dx == 0 && dz == -3 ? Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH)
                    : Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** The ladder up the shaft's north wall, from the second relay's room to the third's. */
    private static void ladder(WorldGenLevel level, BoundingBox box, BlockPos c) {
        for (int y = R2 + 1; y <= R3; y++) {
            put(level, box, c.getX(), y, c.getZ() - 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
    }

    /**
     * The stair from the first relay to the second, round the shaft: up the east side going north, the north going west,
     * the west going south, the south going east, {@link #SIDE} a side, a landing at each corner; bars along its edge.
     */
    private static void stair(WorldGenLevel level, long seed, BoundingBox box, BlockPos c) {
        Direction[] going = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
        int base = R1;
        for (int side = 0; base < R2; side++) {
            Direction way = going[side % 4];
            Direction out = way.getClockWise();                             // from the shaft outward
            // the side's first step is just past the corner it starts from
            for (int i = 0; i < SIDE; i++) {
                int along = i - 4;                                          // -4..4 along the side
                for (int lane = LANE_IN; lane <= LANE_OUT; lane++) {
                    BlockPos at = sidePos(c, out, way, lane, along).atY(base + i + 1);
                    put(level, box, at.getX(), at.getY(), at.getZ(), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, way).setValue(StairBlock.HALF, Half.BOTTOM));
                    put(level, box, at.getX(), at.getY() - 1, at.getZ(), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, way.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
                }
                BlockPos rail = sidePos(c, out, way, LANE_OUT + 1, along).atY(base + i + 2);
                if (Math.floorMod(Town.hash(seed, rail.getX(), rail.getY() * 3 + rail.getZ(), 182), 9) != 0) {   // a bar gone here and there
                    put(level, box, rail.getX(), rail.getY(), rail.getZ(), bars(way.getAxis()));
                }
            }
            int top = base + SIDE;
            // the corner the side ends at: a landing, two by two, and its bars
            BlockPos corner = sidePos(c, out, way, LANE_IN, 5);
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 2; b++) {
                    BlockPos at = corner.relative(out, a).relative(way, b);
                    put(level, box, at.getX(), top, at.getZ(), Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
            // bars round its outer sides: past its far end, and along its outside (the play check, 2026-10-19: walking on
            // up a side carried one straight off the landing)
            for (int a = 0; a <= 2; a++) {
                BlockPos end = corner.relative(out, a).relative(way, 3);
                put(level, box, end.getX(), top + 1, end.getZ(), bars(out.getAxis()));
            }
            for (int b = 0; b <= 3; b++) {
                BlockPos edge = corner.relative(out, 2).relative(way, b);
                put(level, box, edge.getX(), top + 1, edge.getZ(), bars(way.getAxis()));
            }
            if (Math.floorMod(Town.hash(seed, c.getX(), top, 183), 3) == 0) {
                BlockPos d = corner.relative(out).relative(way);
                put(level, box, d.getX(), top + 1, d.getZ(), ZombieMarkerBlock.of(1 + (int) Math.floorMod(Town.hash(seed, top, 0, 184), 2L), false));
            }
            base = top;
        }
    }

    /** {@code lane} out from the shaft's middle toward {@code out}, {@code along} along the side going {@code way}. */
    private static BlockPos sidePos(BlockPos c, Direction out, Direction way, int lane, int along) {
        return c.relative(out, lane).relative(way, along);
    }

    private static BlockState bars(Direction.Axis along) {
        boolean x = along == Direction.Axis.X;
        return Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, x).setValue(IronBarsBlock.WEST, x)
                .setValue(IronBarsBlock.NORTH, !x).setValue(IronBarsBlock.SOUTH, !x);
    }

    /**
     * A relay: a deck round the shaft {@link #DECK} out, braced under, bars round its edge; on it a room on the east -
     * its lodestone, the chest of Lode Pearls bound to it, beds, stores - and the dead about it.
     */
    private static void deck(WorldGenLevel level, long seed, Ascent.Site site, BoundingBox box, BlockPos c, int deck) {
        long h = Town.hash(seed, c.getX(), deck, 185);
        for (int dx = -DECK; dx <= DECK; dx++) {
            for (int dz = -DECK; dz <= DECK; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring <= CORE) {
                    continue;
                }
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                if (!box.isInside(x, deck, z)) {
                    continue;
                }
                if (deck == R2 && (dx == -LANE_IN || dx == -LANE_OUT) && dz >= 1 && dz <= 3) {
                    continue;   // the stair comes up through here (the review, 2026-10-19: it met the deck's underside)
                }
                long r = Math.floorMod(Town.hash(seed, x, deck * 3 + z, 186), 100);
                boolean underHut = dx >= 7 && dx <= 12 && Math.abs(dz) <= 6;
                if (ring >= 9 && ring < DECK && !underHut) {                 // a grating, the light through it to the deck below
                    put(level, box, x, deck, z, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
                } else {
                    put(level, box, x, deck, z, (r < 20 ? Blocks.CRACKED_STONE_BRICKS : Blocks.SMOOTH_STONE).defaultBlockState());
                }
                if (ring == DECK) {                                         // its edge: bars, a gap here and there, lamps on posts
                    if (r >= 8) {
                        put(level, box, x, deck + 1, z, bars(Math.abs(dz) == DECK ? Direction.Axis.X : Direction.Axis.Z));
                        int along = Math.abs(dz) == DECK ? dx : dz;
                        if (Math.abs(dx) == Math.abs(dz) || Math.floorMod(along, 6) == 0) {
                            put(level, box, x, deck + 2, z, Blocks.LANTERN.defaultBlockState());
                        }
                    }
                } else if (ring >= LANE_OUT + 1 && (Math.abs(dx) == Math.abs(dz) || dx == 0 || dz == 0) && ring % 3 == 0) {
                    put(level, box, x, deck - 1, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState()       // braces under it
                            .setValue(StairBlock.FACING, Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST)
                                    : (dz > 0 ? Direction.NORTH : Direction.SOUTH)).setValue(StairBlock.HALF, Half.TOP));
                }
            }
        }
        // the room: east of the shaft, 7 to 12 out, its door to the south (the stair passes its west side); inside its
        // lodestone, the pearls, beds, stores
        int x0 = c.getX() + 7;
        int x1 = c.getX() + 12;
        int z0 = c.getZ() - 6;
        int z1 = c.getZ() + 6;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                boolean post = (x == x0 || x == x1) && (z == z0 || z == z1);
                for (int y = deck + 1; y <= deck + 4; y++) {
                    boolean door = z == z1 && (x == c.getX() + 9 || x == c.getX() + 10) && y <= deck + 2;
                    boolean window = wall && !door && !post && y == deck + 2 && Math.floorMod(x + z, 3) == 0 && x != x0;
                    BlockState state;
                    if (!wall || door) {
                        state = Blocks.AIR.defaultBlockState();
                    } else if (post) {
                        state = Blocks.POLISHED_ANDESITE.defaultBlockState();
                    } else if (window) {
                        boolean alongX = z == z0 || z == z1;
                        state = Blocks.GLASS_PANE.defaultBlockState().setValue(net.minecraft.world.level.block.IronBarsBlock.EAST, alongX)
                                .setValue(net.minecraft.world.level.block.IronBarsBlock.WEST, alongX)
                                .setValue(net.minecraft.world.level.block.IronBarsBlock.NORTH, !alongX)
                                .setValue(net.minecraft.world.level.block.IronBarsBlock.SOUTH, !alongX);
                    } else {
                        state = Blocks.CUT_COPPER.waxed().weathered().defaultBlockState();   // weathered metal: the Tower's own
                    }
                    put(level, box, x, y, z, state);
                }
                put(level, box, x, deck + 5, z, Blocks.CUT_COPPER_SLAB.waxed().oxidized().defaultBlockState());
            }
        }
        BlockPos stone = lodestone(site, deck);
        put(level, box, stone.getX(), stone.getY(), stone.getZ(), Blocks.LODESTONE.defaultBlockState());
        BlockPos chest = new BlockPos(x1 - 1, deck + 1, c.getZ() + 1);
        if (box.isInside(chest)) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), 2);
            if (level.getBlockEntity(chest) instanceof ChestBlockEntity pearls) {
                // Nookworks' own Lode Pearls (the user, 2026-10-21: "ロードパール入れとけばいいんじゃない？")
                pearls.setItem(13, dev.nookworks.pearl.LodePearlItem.bound(GlobalPos.of(dev.nookworks.ifworld.IfWorld.KEY, stone)).copyWithCount(PEARLS));
            }
        }
        container(level, box, x1 - 1, deck + 1, z0 + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), "food", h);
        container(level, box, x1 - 1, deck + 1, z1 - 1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST),
                deck == R3 ? "medical" : "supplies", h >> 4);
        put(level, box, x0 + 2, deck + 1, z0 + 1, Blocks.ENDER_CHEST.defaultBlockState());
        put(level, box, x0 + 1, deck + 1, z0 + 1, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());   // they mapped the way up
        put(level, box, x1 - 1, deck + 2, z0 + 1, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3));
        for (int dz : new int[] {-3, 3}) {                                  // its lamps: lit where the pearls set one down
            Dress.hangingLantern(level, box, new BlockPos(c.getX() + 9, deck + 4, c.getZ() + dz));
        }
        for (int k = 0; k < 2; k++) {
            int z = z1 - 3 - k * 2;
            BlockState bed = Blocks.BED.gray().defaultBlockState().setValue(BedBlock.FACING, Direction.WEST);
            put(level, box, x0 + 1, deck + 1, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
            put(level, box, x0 + 2, deck + 1, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
        }
        // the dead on the deck, and one in the room
        int[][] dead = {{-9, -9, 2}, {9, -10, 1}, {-10, 8, 2}, {2, 10, 1}, {10, 4, 1}};
        for (int[] d : dead) {
            put(level, box, c.getX() + d[0], deck + 1, c.getZ() + d[1], ZombieMarkerBlock.of(d[2] + (deck == R3 ? 1 : 0), false));
        }
        if (deck == R3) {                                                   // the base's guard, come down this far
            SkyBase.sentry(level, box, c.getX() - 9, deck + 1, c.getZ() - 2);
        }
        if (((h >> 9) & 1) == 0) {
            BlockPos body = new BlockPos(x1 - 1, deck + 1, z0 + 3);
            if (box.isInside(body)) {
                Markers.remains(level, body, Direction.WEST, RandomSource.create(h ^ 0xB0D1E5L));
            }
        }
    }

    static void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        if (box.isInside(x, y, z)) {
            level.setBlock(new BlockPos(x, y, z), state, 2);
        }
    }

    static void container(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state, String loot, long seed) {
        if (!box.isInside(x, y, z)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, state, 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, NookOfTheDead.id("chests/" + loot)), seed);
        }
    }

    private Tower() {}
}
