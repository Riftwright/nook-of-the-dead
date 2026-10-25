package dev.nookofthedead.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The way to the end of the If (PLAN_0.3.md, the user, 2026-10-19: "そのパールを使うと、7000ブロックほど離れた別の研究所に
 * 飛ぶ。そこには、nookworksのエレベーターが置いてある"): one in each world, about {@link #DISTANCE} blocks out from the middle
 * in a direction the seed chooses. Here, the second institute: a compound walled in on ground levelled for it, and in it a
 * smaller institute - the hall with the lodestone the refuge's Lode Pearls are bound to; a laboratory; the rooms they lived in;
 * at the back, the lift room, a Nookworks Elevator in its floor, the way up.
 * <p>
 * Built by code, each chunk drawing its part from the seed alone, over whatever the town had there (no lot of the town's
 * is laid in it: {@link #near}). Its dead and its remains are set down as the town's are.
 */
public final class Ascent {
    /** How far out from the world's middle. */
    public static final int DISTANCE = 7000;
    /** The levelled ground's half width; the compound's wall two inside it. */
    public static final int REACH = 40;
    /** The building's half width (x) and half depth (z), and its storey. */
    static final int HALF_X = 16;
    static final int HALF_Z = 12;
    static final int STOREY = 6;

    /** The second institute of a world: its middle, and its floor's level. */
    public record Site(int x, int z, int ground) {
        /** The lodestone in its hall, that the refuge's Lode Pearls are bound to. */
        public BlockPos lodestone() {
            return new BlockPos(this.x, this.ground, this.z + 5);
        }

        /** Where one stands on arriving: on the lodestone. */
        public BlockPos arrival() {
            return this.lodestone().above();
        }

        /** The Elevator in the lift room's floor. */
        public BlockPos elevator() {
            return new BlockPos(this.x, this.ground, this.z - 8);
        }

        public boolean near(int px, int pz, int margin) {
            return Math.abs(px - this.x) <= REACH + margin && Math.abs(pz - this.z) <= REACH + margin;
        }
    }

    private static final Map<Long, Site> SITES = new ConcurrentHashMap<>();

    /** The second institute of the world of {@code seed}. */
    public static Site site(long seed) {
        return SITES.computeIfAbsent(seed, s -> {
            int[] at = where(s);
            int ground = (int) Math.round(Math.max(Town.SURFACE + 1, Math.min(Town.SURFACE + 40, Town.height(s, at[0], at[1]))));
            return new Site(at[0], at[1], ground);
        });
    }

    /** Where the site's middle is, {x, z}: from the seed alone (the seas and mountains keep off it, asked before the ground is). */
    public static int[] where(long seed) {
        double angle = Math.floorMod(Town.hash(seed, 0, 0, 170), 3600L) / 3600.0 * Math.PI * 2;
        return new int[] {(int) Math.round(Math.cos(angle) * DISTANCE), (int) Math.round(Math.sin(angle) * DISTANCE)};
    }

    /** Whether a lot of the town at ({@code x0}, {@code z0}), {@code size} across, would stand on the site: it is left out. */
    public static boolean near(long seed, int x0, int z0, int size) {
        Site site = site(seed);
        return x0 + size >= site.x - REACH - 4 && x0 <= site.x + REACH + 4 && z0 + size >= site.z - REACH - 4 && z0 <= site.z + REACH + 4;
    }

    /** This chunk's part of the site, if it has one: drawn last, over all else. */
    public static void draw(WorldGenLevel level, long seed, BoundingBox box) {
        Site site = site(seed);
        if (box.maxX() < site.x - REACH || box.minX() > site.x + REACH || box.maxZ() < site.z - REACH || box.minZ() > site.z + REACH) {
            return;
        }
        ground(level, seed, site, box);
        yard(level, seed, site, box);
        building(level, seed, site, box);
        porch(level, seed, site, box);
        furnish(level, seed, site, box);
        Tower.draw(level, seed, site, box);
        SkyBase.draw(level, seed, site, box);
    }

    /** The ground levelled for it: earth and stone under, grass gone to dirt and gravel over, the air above cleared; the wall. */
    private static void ground(WorldGenLevel level, long seed, Site site, BoundingBox box) {
        int g = site.ground;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(box.minX(), site.x - REACH); x <= Math.min(box.maxX(), site.x + REACH); x++) {
            for (int z = Math.max(box.minZ(), site.z - REACH); z <= Math.min(box.maxZ(), site.z + REACH); z++) {
                int dx = x - site.x;
                int dz = z - site.z;
                for (int y = g - 14; y < g - 3; y++) {
                    if (level.getBlockState(at.set(x, y, z)).isAir() || !level.getFluidState(at).isEmpty()) {
                        level.setBlock(at, Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                for (int y = g - 3; y < g; y++) {
                    level.setBlock(at.set(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
                }
                double n = Town.noise(seed, x, z, 9.0, 171);
                double m = Town.noise(seed, x, z, 2.5, 179);
                Block top = n < 0.3 && m > 0.4 ? Blocks.COARSE_DIRT : n > 0.72 && m > 0.5 ? Blocks.GRAVEL : Blocks.GRASS_BLOCK;
                if (Math.abs(dx) <= 3 && dz > HALF_Z) {
                    top = Math.floorMod(Town.hash(seed, x, z, 172), 6) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;   // the drive
                }
                level.setBlock(at.set(x, g, z), top.defaultBlockState(), 2);
                for (int y = g + 1; y <= g + 40; y++) {
                    level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                // the wall round it, its gate to the south, broken here and there
                int edge = REACH - 2;
                boolean ring = Math.abs(dx) == edge && Math.abs(dz) <= edge || Math.abs(dz) == edge && Math.abs(dx) <= edge;
                if (ring && !(dz == edge && Math.abs(dx) <= 3)) {
                    long r = Math.floorMod(Town.hash(seed, x, z, 173), 100);
                    int high = r < 8 ? 1 : r < 20 ? 3 : 4;
                    for (int y = g + 1; y <= g + high; y++) {
                        level.setBlock(at.set(x, y, z), (y == g + high && high == 4 ? Blocks.STONE_BRICK_WALL : r < 35 ? Blocks.MOSSY_STONE_BRICKS
                                : r < 50 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * What they set up in the yard while they held it: tents for the sick along the east, a line of sandbags across the
     * gate, a landing pad on the west with its mark, posts with dead lamps along the drive.
     */
    private static void yard(WorldGenLevel level, long seed, Site site, BoundingBox box) {
        int g = site.ground;
        int cx = site.x;
        int cz = site.z;
        // tents of canvas over a ridge pole (stairs for its slopes: wool steps read as a rib cage from above - the visual
        // review), a cot and a barrel in each, one fallen in
        for (int t = 0; t < 3; t++) {
            int tx = cx + 22;
            int tz = cz + 16 + t * 7;
            boolean fallen = Math.floorMod(Town.hash(seed, tx, tz, 194), 3) == 0;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int y = 2 - Math.abs(dx);
                    if (fallen && y > 0) {
                        continue;
                    }
                    BlockState canvas = dx == 0 ? Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState()
                            : Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, dx < 0 ? Direction.EAST : Direction.WEST);
                    put(level, box, tx + dx, g + 1 + y, tz + dz, canvas);
                    if (dz == -2 && !fallen && y < 2) {                     // its back closed
                        for (int k = 0; k < y; k++) {
                            put(level, box, tx + dx, g + 1 + k, tz + dz, Blocks.WOOL.white().defaultBlockState());
                        }
                    }
                }
            }
            if (!fallen) {
                for (int pz : new int[] {-3, 3}) {                          // the poles, poking past its ends
                    for (int y = 1; y <= 3; y++) {
                        put(level, box, tx, g + y, tz + pz, Blocks.SPRUCE_FENCE.defaultBlockState());
                    }
                }
                put(level, box, tx - 1, g + 1, tz - 1, Blocks.WOOL.white().defaultBlockState());
                put(level, box, tx + 1, g + 1, tz - 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            }
        }
        // sandbags across the gate, the way left between them
        for (int dx = -9; dx <= 9; dx++) {
            if (Math.abs(dx) <= 2) {
                continue;
            }
            int z = cz + REACH - 6 + (Math.abs(dx) > 6 ? -1 : 0);
            put(level, box, cx + dx, g + 1, z, Blocks.PACKED_MUD.defaultBlockState());
            if (Math.floorMod(Town.hash(seed, cx + dx, z, 195), 3) != 0) {
                put(level, box, cx + dx, g + 2, z, Blocks.MUD_BRICK_SLAB.defaultBlockState());
            }
        }
        // the landing pad: a ring, and its mark
        int px = cx - 24;
        int pz = cz + 20;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > 6.3) {
                    continue;
                }
                boolean mark = (Math.abs(dx) == 2 && Math.abs(dz) <= 3) || (dz == 0 && Math.abs(dx) <= 2);
                boolean ring = r > 5.2;
                boolean faded = Math.floorMod(Town.hash(seed, dx, dz, 207), 5) == 0;
                put(level, box, px + dx, g, pz + dz, ((mark || ring) && !faded ? Blocks.DYED_TERRACOTTA.yellow()
                        : Math.floorMod(Town.hash(seed, dx, dz, 196), 7) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.SMOOTH_STONE).defaultBlockState());
            }
        }
        // posts along the drive, their lamps long dark
        for (int dz = HALF_Z + 4; dz < REACH - 4; dz += 7) {
            for (int side : new int[] {-4, 4}) {
                if (Math.floorMod(Town.hash(seed, side, dz, 208), 3) == 0) {   // one in three fallen, its lamp in the grass
                    for (int k = 0; k < 3; k++) {
                        put(level, box, cx + side + Integer.signum(side) * k, g + 1, cz + dz, Blocks.IRON_CHAIN.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X));
                    }
                    put(level, box, cx + side + Integer.signum(side) * 3, g + 1, cz + dz, Blocks.REDSTONE_LAMP.defaultBlockState());
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    put(level, box, cx + side, g + y, cz + dz, Blocks.IRON_BARS.defaultBlockState());
                }
                put(level, box, cx + side, g + 4, cz + dz, Blocks.REDSTONE_LAMP.defaultBlockState());
            }
        }
    }

    /** A porch over the front door, posts of bars; the front's windows boarded from outside while they held the yard. */
    private static void porch(WorldGenLevel level, long seed, Site site, BoundingBox box) {
        int g = site.ground;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = HALF_Z + 1; dz <= HALF_Z + 2; dz++) {
                put(level, box, site.x + dx, g + 4, site.z + dz, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
        }
        for (int dx : new int[] {-2, 2}) {
            for (int y = g + 1; y <= g + 3; y++) {
                put(level, box, site.x + dx, y, site.z + HALF_Z + 2, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        for (int dx = -HALF_X + 1; dx < HALF_X; dx++) {
            if (Math.floorMod(dx, 6) == 2 || Math.floorMod(dx, 6) == 4) {
                if (Math.floorMod(Town.hash(seed, dx, 0, 203), 3) == 0) {
                    for (int y = g + 2; y <= g + 3; y++) {
                        put(level, box, site.x + dx, y, site.z + HALF_Z + 1, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
                                .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.SOUTH));
                    }
                }
            }
        }
    }

    /** The building: its walls, windows, floors and roof, the inner walls between its rooms. */
    private static void building(WorldGenLevel level, long seed, Site site, BoundingBox box) {
        int g = site.ground;
        for (int dx = -HALF_X; dx <= HALF_X; dx++) {
            for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
                int x = site.x + dx;
                int z = site.z + dz;
                if (!box.isInside(x, g, z)) {
                    continue;
                }
                boolean outer = Math.abs(dx) == HALF_X || Math.abs(dz) == HALF_Z;
                boolean inner = (Math.abs(dx) == 7 && Math.abs(dz) < HALF_Z) || (dz == -4 && Math.abs(dx) < 7);
                long r = Math.floorMod(Town.hash(seed, x, z, 174), 100);
                put(level, box, x, g, z, (r < 10 ? Blocks.CRACKED_STONE_BRICKS : Blocks.POLISHED_ANDESITE).defaultBlockState());
                for (int y = g + 1; y < g + STOREY; y++) {
                    BlockState wall = null;
                    if (outer) {
                        int along = Math.abs(dx) == HALF_X ? dz : dx;
                        boolean corner = Math.abs(dx) == HALF_X && Math.abs(dz) == HALF_Z;
                        // pilasters every six, windows between them, lintels over (the visual review: one strip of windows read
                        // as generated)
                        boolean pilaster = Math.floorMod(along, 6) == 0;
                        boolean window = !corner && !pilaster && (Math.floorMod(along, 6) == 2 || Math.floorMod(along, 6) == 4)
                                && (y == g + 2 || y == g + 3);
                        boolean door = dz == HALF_Z && Math.abs(dx) <= 1 && y <= g + 3;
                        if (door) {
                            wall = Blocks.AIR.defaultBlockState();
                        } else if (window) {
                            boolean alongX = Math.abs(dz) == HALF_Z;
                            wall = Math.floorMod(Town.hash(seed, x, y * 7 + z, 175), 4) == 0 ? Blocks.AIR.defaultBlockState()
                                    : Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, alongX).setValue(IronBarsBlock.WEST, alongX)
                                            .setValue(IronBarsBlock.NORTH, !alongX).setValue(IronBarsBlock.SOUTH, !alongX);
                        } else if (pilaster || corner) {
                            wall = (Math.floorMod(Town.hash(seed, x, y * 11 + z, 176), 7) == 0 ? Blocks.CRACKED_STONE_BRICKS
                                    : Blocks.STONE_BRICKS).defaultBlockState();
                        } else {
                            boolean lintel = y == g + 4 && (Math.floorMod(along, 6) == 2 || Math.floorMod(along, 6) == 4);
                            wall = (y == g + 1 ? Blocks.SMOOTH_STONE : y == g + STOREY - 1 ? Blocks.SMOOTH_STONE
                                    : lintel ? Blocks.POLISHED_ANDESITE
                                    : Math.floorMod(Town.hash(seed, x, y * 11 + z, 176), 9) == 0 ? Blocks.CRACKED_STONE_BRICKS
                                    : Blocks.CONCRETE.lightGray()).defaultBlockState();
                        }
                    } else if (inner) {
                        boolean doorway = y <= g + 2 && (Math.abs(dx) == 7 ? dz == 0 || dz == -8 || dz == 8 : dx == 0);
                        wall = doorway ? Blocks.AIR.defaultBlockState() : (y == g + 1 ? Blocks.SMOOTH_STONE : Blocks.CONCRETE.white()).defaultBlockState();
                    } else {
                        wall = Blocks.AIR.defaultBlockState();
                    }
                    put(level, box, x, y, z, wall);
                }
                // the roof: flat, a parapet round it, fallen in here and there over the laboratory
                boolean hole = dx < -8 && Town.noise(seed, x, z, 3.0, 177) > 0.78;
                put(level, box, x, g + STOREY, z, (hole ? Blocks.AIR : outer ? Blocks.CONCRETE.white() : Blocks.SMOOTH_STONE).defaultBlockState());
                if (outer && !hole) {
                    put(level, box, x, g + STOREY + 1, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                }
                if (hole && Town.noise(seed, x, z, 3.0, 177) > 0.84) {                         // what came down
                    long q = Math.floorMod(Town.hash(seed, x, z, 179), 5);
                    put(level, box, x, g + 1, z, (q == 0 ? Blocks.MOSSY_COBBLESTONE : q == 1 ? Blocks.GRAVEL : q == 2 ? Blocks.CRACKED_STONE_BRICKS
                            : Blocks.COBBLESTONE).defaultBlockState());
                    if (q > 2) {
                        put(level, box, x, g + 2, z, Blocks.COBBLESTONE_SLAB.defaultBlockState());
                    }
                } else if (hole) {
                    put(level, box, x, g + STOREY - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());   // from the hole's rim
                }
                // vines down its outer walls, moss and grass at its foot
                if (outer) {
                    int ox = Math.abs(dx) == HALF_X ? Integer.signum(dx) : 0;
                    int oz = Math.abs(dz) == HALF_Z && ox == 0 ? Integer.signum(dz) : 0;
                    double n = Town.noise(seed, x, z, 4.0, 198);
                    if (n > 0.7) {
                        net.minecraft.world.level.block.state.properties.BooleanProperty face = ox > 0 ? net.minecraft.world.level.block.VineBlock.WEST
                                : ox < 0 ? net.minecraft.world.level.block.VineBlock.EAST : oz > 0 ? net.minecraft.world.level.block.VineBlock.NORTH
                                : net.minecraft.world.level.block.VineBlock.SOUTH;
                        int len = 2 + (int) Math.floorMod(Town.hash(seed, x, z, 202), 4L);
                        for (int k = 0; k < len; k++) {
                            put(level, box, x + ox, g + STOREY - 1 - k, z + oz, Blocks.VINE.defaultBlockState().setValue(face, true));
                        }
                    } else if (n > 0.55) {
                        put(level, box, x + ox, g + 1, z + oz, (n > 0.62 ? Blocks.MOSS_CARPET : Blocks.SHORT_GRASS).defaultBlockState());
                    }
                }
            }
        }
    }

    /** What is in it: the hall and its lodestone, the laboratory, the rooms they lived in, the lift room; its dead. */
    private static void furnish(WorldGenLevel level, long seed, Site site, BoundingBox box) {
        int g = site.ground;
        int f = g + 1;
        int cx = site.x;
        int cz = site.z;
        long h = Town.hash(seed, cx, cz, 178);
        // the hall: where the pearls set one down - its lodestone in a ring of chiseled stone; benches along the walls
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 4; dz <= 6; dz++) {
                put(level, box, cx + dx, g, cz + dz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            }
        }
        put(level, box, site.lodestone().getX(), site.lodestone().getY(), site.lodestone().getZ(), Blocks.LODESTONE.defaultBlockState());
        // its lamps still lit, hung from the ceiling: in the hall, over the desk, in the lift room where the climb begins
        for (int[] l : new int[][] {{-5, 2}, {5, 2}, {-5, 10}, {5, 10}, {-2, 1}, {2, 1}, {-4, -8}, {4, -8}}) {
            Dress.hangingLantern(level, box, new BlockPos(cx + l[0], g + STOREY - 1, cz + l[1]));
        }
        // papers on the hall's floor round the desk; stains, and a trail from the lab's door to where one lies
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -2; dz <= 3; dz++) {
                if (Math.floorMod(Town.hash(seed, dx, dz, 204), 9) == 0) {
                    put(level, box, cx + dx, f, cz + dz, Blocks.CARPET.white().defaultBlockState());
                }
            }
        }
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -11; dz <= 11; dz++) {
                Block stain = Dress.stain(seed, cx + dx, cz + dz, 205);
                if (stain != null && !(Math.abs(dx) <= 1 && dz >= 4 && dz <= 6)) {
                    put(level, box, cx + dx, g, cz + dz, stain.defaultBlockState());
                }
            }
        }
        for (int k = 0; k <= 6; k++) {
            if (k % 3 != 2) {
                put(level, box, cx - 7 - k, g, cz - (k * 5) / 6, Blocks.DYED_TERRACOTTA.brown().defaultBlockState());
            }
        }
        for (int dz = 1; dz <= 9; dz += 2) {
            put(level, box, cx - 6, f, cz + dz, Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            put(level, box, cx + 6, f, cz + dz, Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
        for (int dx = -3; dx <= 3; dx++) {                                   // the desk across the back of the hall
            put(level, box, cx + dx, f, cz - 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        put(level, box, cx - 3, f + 1, cz - 1, Blocks.CANDLE.defaultBlockState());
        // two of the four launch keys are on the two who stayed (their remains, below); the other two went up with the
        // key-holders (SkyBase) - the story's review, 2026-10-23
        container(level, box, cx + 2, f, cz - 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), "supplies", h);
        put(level, box, cx + 5, f, cz + 10, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        put(level, box, cx - 5, f, cz + 10, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        // the laboratory, west: benches with their stands and cauldrons, cages along the inner wall, its stores
        for (int dz = -10; dz <= 10; dz++) {
            put(level, box, cx - 15, f, cz + dz, (dz % 4 == 0 ? Blocks.BARREL : Blocks.SMOOTH_STONE).defaultBlockState());
            if (dz % 4 != 0 && Math.floorMod(dz, 5) == 0) {
                Dress.brewingStand(level, box, new BlockPos(cx - 15, f + 1, cz + dz), h ^ dz);
            }
            BlockState on = switch (Math.floorMod(dz, 5)) {
                case 2 -> (Math.floorMod(dz, 2) == 0 ? Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 1) : Blocks.CAULDRON.defaultBlockState());
                case 3 -> Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 2);
                default -> null;
            };
            if (on != null && dz % 4 != 0) {
                put(level, box, cx - 15, f + 1, cz + dz, on);
            }
        }
        for (int dz = -10; dz <= -2; dz += 4) {                              // cages against the inner wall, bones in them
            for (int y = f; y <= f + 1; y++) {
                for (int k = 0; k <= 2; k++) {
                    put(level, box, cx - 9 - k, y, cz + dz + 2, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true)
                            .setValue(IronBarsBlock.WEST, true));
                }
                put(level, box, cx - 11, y, cz + dz, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)
                        .setValue(IronBarsBlock.SOUTH, true));
                put(level, box, cx - 11, y, cz + dz + 1, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true)
                        .setValue(IronBarsBlock.SOUTH, true));
            }
            Dress.bones(level, box, new BlockPos(cx - 9, f, cz + dz), Town.hash(seed, dz, 0, 206), true);
            put(level, box, cx - 10, f, cz + dz + 1, Blocks.CARPET.brown().defaultBlockState());
        }
        container(level, box, cx - 12, f, cz + 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "medical", h >> 4);
        container(level, box, cx - 12, f, cz + 8, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "potion", h >> 8);
        put(level, box, cx - 9, f, cz + 10, Blocks.CRAFTING_TABLE.defaultBlockState());
        // their rooms, east: beds, desks, shelves, what they kept
        for (int dz = -10; dz <= 10; dz += 4) {
            Block colour = Math.floorMod(dz, 12) == 2 ? Blocks.BED.lightGray() : Math.floorMod(dz, 12) == 6 ? Blocks.BED.brown() : Blocks.BED.white();
            BlockState bed = colour.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
            put(level, box, cx + 15, f, cz + dz, bed.setValue(BedBlock.PART, BedPart.HEAD));
            put(level, box, cx + 14, f, cz + dz, bed.setValue(BedBlock.PART, BedPart.FOOT));
            put(level, box, cx + 15, f, cz + dz + 2, Blocks.BOOKSHELF.defaultBlockState());
        }
        for (int dz = -9; dz <= 9; dz += 6) {
            put(level, box, cx + 9, f, cz + dz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            put(level, box, cx + 10, f, cz + dz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        }
        container(level, box, cx + 8, f, cz + 11, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), "food", h >> 12);
        container(level, box, cx + 12, f, cz - 11, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), "stash", h >> 16);
        if (box.isInside(cx + 9, f, cz + 3)) {                               // what one of them wrote, left open
            BlockPos at = new BlockPos(cx + 9, f, cz + 3);
            level.setBlock(at, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.EAST).setValue(LecternBlock.HAS_BOOK, true), 2);
            if (level.getBlockEntity(at) instanceof LecternBlockEntity lectern) {
                lectern.setBook(Notes.book(Notes.STAYING));            // the two who stayed, and the keys they kept
            }
        }
        // the lift room at the back: the Elevator in the floor, a cage of bars round it open to the door
        BlockPos lift = site.elevator();
        put(level, box, lift.getX(), lift.getY(), lift.getZ(), dev.nookworks.registry.ModBlocks.ELEVATOR.get().defaultBlockState());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && !(dz == 2 && Math.abs(dx) <= 0)) {
                    for (int y = f; y <= f + 2; y++) {
                        put(level, box, lift.getX() + dx, y, lift.getZ() + dz, Blocks.IRON_BARS.defaultBlockState()
                                .setValue(IronBarsBlock.NORTH, Math.abs(dx) == 2).setValue(IronBarsBlock.SOUTH, Math.abs(dx) == 2)
                                .setValue(IronBarsBlock.EAST, Math.abs(dz) == 2).setValue(IronBarsBlock.WEST, Math.abs(dz) == 2));
                    }
                }
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && !(dx == 0 && dz == 0)) {
                    put(level, box, lift.getX() + dx, g, lift.getZ() + dz, Blocks.IRON_BLOCK.defaultBlockState());
                }
            }
        }
        // what they left of their papers (0.3, Nookworks 1.1's books): on the desk across the hall, two piles, the lift's
        // timetable and their packing list among them; rows of books on the shelves in their rooms; more fallen in corners
        RandomSource rolls = random(h);
        java.util.function.Supplier<net.minecraft.world.item.ItemStack> books = () -> Props.studied(level, Notes.ASCENT, rolls);
        Props.pile(level, box, new BlockPos(cx - 2, f + 1, cz - 1), 3, 200, random(h ^ 1), () -> Notes.book(Notes.ASCENT.get(0)));
        Props.pile(level, box, new BlockPos(cx + 1, f + 1, cz - 1), 2, 160, random(h ^ 2), () -> Notes.book(Notes.ASCENT.get(1)));
        for (int dz = -10; dz <= 10; dz += 4) {
            Props.row(level, box, new BlockPos(cx + 15, f + 1, cz + dz + 2), Direction.WEST,
                    net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT, 2 + Math.floorMod(dz + 11, 4), books);
        }
        Props.scatter(level, box, new BoundingBox(cx - 16, f, cz - 12, cx + 16, f + 2, cz + 12), seed ^ 0xA5CE, 90, books, random(h ^ 3));
        // the dead: in the laboratory, the rooms, the lift room, the yard - none in the hall, where a pearl sets one down (in
        // game, 2026-10-19: three beside the lodestone had the one who came in pushed off it before the screen had cleared);
        // and two who stayed
        int[][] dead = {{-12, -4, 2}, {-13, 4, 1}, {12, 0, 2}, {11, -7, 1}, {-4, -9, 2}, {4, -10, 1},
                {-20, 18, 3}, {22, 20, 2}, {-25, -20, 2}, {18, -24, 3}, {0, 26, 2}};
        for (int[] d : dead) {
            put(level, box, cx + d[0], f, cz + d[1], ZombieMarkerBlock.of(d[2], false));
        }
        for (int[] body : new int[][] {{-14, -6}, {13, 6}}) {
            BlockPos at = new BlockPos(cx + body[0], f, cz + body[1]);
            if (box.isInside(at)) {
                Markers.remains(level, at, body[0] < 0 ? Direction.EAST : Direction.WEST, RandomSource.create(h ^ body[0] ^ (long) body[1] << 8),
                        new net.minecraft.world.item.ItemStack(dev.nookofthedead.registry.ZwItems.LAUNCH_KEY.get()));   // their keys still on them
            }
        }
    }

    /** A random source of its own for {@code h}: the same books on the same place every time. */
    private static RandomSource random(long h) {
        return RandomSource.create(h ^ 0x0B00C5L);
    }

    private static void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        if (box.isInside(x, y, z)) {
            level.setBlock(new BlockPos(x, y, z), state, 2);
        }
    }

    private static void container(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state, String loot, long seed) {
        if (!box.isInside(x, y, z)) {
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, state, 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, NookOfTheDead.id("chests/" + loot)), seed);
        }
    }

    private Ascent() {}
}
