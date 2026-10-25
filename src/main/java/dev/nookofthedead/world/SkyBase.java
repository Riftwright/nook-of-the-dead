package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The sky base at the top of the Tower (the user, 2026-10-19: "限界高度ギリギリの天空基地…人類のための新たな拠点を…しかし、
 * そこでも感染者が出てしまったのか、最後の楽園は、ゾンビの巣窟と化していた"): a deck in the thin air, {@link Tower#BASE}
 * high, round the top of the shaft. In the middle the hub, where the last lift comes up; corridors out to its four wings -
 * to the south the greenhouse they meant to live on, to the east their quarters, to the west their stores, and to the
 * north, the deepest part, the laboratory where the vaccine was finished ({@link #lab}). Over the hub, a lookout; over
 * the shaft, the mast. All of it gone to the dead: webs, bones, the stains of it, the glass broken, the dead everywhere.
 */
public final class SkyBase {
    /** The deck's half width. */
    static final int HALF = 24;
    /** The hub's half width (its walls). */
    static final int HUB = 9;
    /** The wings: from this far out along each axis to the deck's edge, this wide either side. */
    static final int WING = 13;
    static final int WING_HALF = 8;
    static final int HIGH = 5;
    static final int LAB_HIGH = 8;

    /**
     * Where in the laboratory the vaccine's console is: its far wall's middle, a step out from the glass, the vessel
     * between (0.3, part I; the vessel 2026-10-20 - till then the console stood against the glass, {@link #refit}).
     */
    public static BlockPos vaccine(Ascent.Site site) {
        BlockPos c = Tower.centre(site);
        return new BlockPos(c.getX(), Tower.BASE + 2, c.getZ() - HALF + 4);
    }

    static void draw(WorldGenLevel level, long seed, Ascent.Site site, BoundingBox box) {
        BlockPos c = Tower.centre(site);
        if (box.maxX() < c.getX() - HALF || box.minX() > c.getX() + HALF || box.maxZ() < c.getZ() - HALF || box.minZ() > c.getZ() + HALF) {
            return;
        }
        int b = Tower.BASE;
        // trusses under the deck, sloping down into the shaft: it sits on something (the visual review, 2026-10-19)
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            for (int ring = 6; ring <= (d[0] != 0 && d[1] != 0 ? 16 : 20); ring++) {
                int x = c.getX() + d[0] * ring;
                int z = c.getZ() + d[1] * ring;
                int y = b - 2 - (20 - ring) / 3;
                put(level, box, x, y, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                if (ring % 2 == 0) {
                    for (int k = y + 1; k <= b - 2; k++) {
                        put(level, box, x, k, z, Blocks.IRON_BARS.defaultBlockState());
                    }
                }
            }
        }
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                if (!box.isInside(x, b, z) || Math.abs(dx) + Math.abs(dz) > HALF + HALF / 2) {
                    continue;
                }
                column(level, seed, box, x, z, dx, dz, b);
            }
        }
        Diaries.at(Diaries.Place.SKY);                                  // its diaries are of up here
        try {
            furnish(level, seed, site, box, c, b);
        } finally {
            Diaries.at(Diaries.Place.TOWN);
        }
    }

    /** Whether ({@code dx}, {@code dz}) is on the deck's rim. */
    private static boolean edgeAt(int dx, int dz) {
        int sum = Math.abs(dx) + Math.abs(dz);
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        return sum <= HALF + HALF / 2 && ring <= HALF && (sum == HALF + HALF / 2 || ring == HALF);
    }

    /**
     * One column of the base: the deck, and what stands on it there - walls, roof, windows, the mast. The open deck is
     * a grating on beams, so the light goes through to the Tower below (the visual review, 2026-10-19: under a solid
     * deck 49 across, the shaft and every relay stood black by day); the rooms keep solid floors, stained where the
     * dead went.
     */
    private static void column(WorldGenLevel level, long seed, BoundingBox box, int x, int z, int dx, int dz, int b) {
        long r = Math.floorMod(Town.hash(seed, x, z, 190), 100);
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        boolean edge = edgeAt(dx, dz);
        int along = Math.abs(dx) >= Math.abs(dz) ? Math.abs(dx) : Math.abs(dz);
        int across = Math.abs(dx) >= Math.abs(dz) ? Math.abs(dz) : Math.abs(dx);
        boolean north = dz < 0 && Math.abs(dz) >= Math.abs(dx);
        boolean hub = ring <= HUB;
        boolean corridor = !hub && along < WING && across <= 2;
        boolean wing = along >= WING && along <= HALF - 2 && across <= WING_HALF;
        boolean open = !edge && ring > Tower.CORE && !hub && !corridor && !wing;
        if (north && (open || edge)) {
            // the deck before and about the laboratory fallen away: it stands out over the drop, and the way in is from
            // above (SkyBaseRooms; the user, 2026-10-23)
            put(level, box, x, b - 1, z, Blocks.AIR.defaultBlockState());
            put(level, box, x, b, z, Blocks.AIR.defaultBlockState());
            return;
        }
        boolean beam = Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0;
        if (open && !beam) {                                               // the grating, light through it
            put(level, box, x, b - 1, z, Blocks.AIR.defaultBlockState());
            put(level, box, x, b, z, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
        } else {
            put(level, box, x, b - 1, z, Blocks.CONCRETE.gray().defaultBlockState());
            Block stain = !open && !edge ? Dress.stain(seed, x, z, 197) : null;
            put(level, box, x, b, z, (stain != null ? stain : beam ? Blocks.IRON_BLOCK
                    : r < 8 ? Blocks.CRACKED_STONE_BRICKS : Blocks.SMOOTH_STONE).defaultBlockState());
        }
        if (edge) {
            if (r >= 10) {                                                  // the rail, joined to its neighbours only
                put(level, box, x, b + 1, z, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, edgeAt(dx, dz - 1))
                        .setValue(IronBarsBlock.SOUTH, edgeAt(dx, dz + 1)).setValue(IronBarsBlock.EAST, edgeAt(dx + 1, dz))
                        .setValue(IronBarsBlock.WEST, edgeAt(dx - 1, dz)));
            }
            if (ring % 6 == 0) {
                put(level, box, x, b - 2, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.FACING, Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST)
                                : (dz > 0 ? Direction.NORTH : Direction.SOUTH)));
            }
            if (north && Town.noise(seed, x, z, 3.0, 199) > 0.55) {        // snow drifted against the north rail: the cold up here
                put(level, box, x, b + 1, z + 1, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,
                        1 + (int) Math.floorMod(r, 2L)));
            }
            return;
        }
        if (ring <= Tower.CORE) {
            core(level, seed, box, x, z, dx, dz, b, ring);
            return;
        }
        if (open) {
            return;
        }
        int high = north && along >= WING ? LAB_HIGH : HIGH;
        boolean wall = hub ? ring == HUB && !(across <= 1 && along == HUB)
                : corridor ? across == 2
                : along == HALF - 2 || across == WING_HALF || along == WING && across > 1;
        boolean glassHouse = !north && dz > 0 && Math.abs(dz) >= Math.abs(dx) && wing;     // the greenhouse
        boolean backlight = north && along == HALF - 2 && Math.abs(dx) <= 2;                // glass behind the vaccine
        for (int y = b + 1; y <= b + high; y++) {
            BlockState state;
            if (wall) {
                boolean window = y >= b + 2 && y <= b + high - 1 && (along + across) % 3 == 0;
                if (backlight && y <= b + 6) {
                    state = Blocks.GLASS.defaultBlockState();
                } else if (glassHouse) {                                    // panes in an iron frame, broken here and there
                    boolean mullion = (along + across) % 3 == 0 || y == b + 1;
                    state = mullion ? Blocks.IRON_BLOCK.defaultBlockState() : r < 18 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState();
                } else if (window && north && wing) {                       // the laboratory's glass whole: kept sealed
                    state = Blocks.GLASS.defaultBlockState();
                } else if (window) {
                    // boarded from inside on the wings, one in four: someone's last stand
                    state = wing && Math.floorMod(Town.hash(seed, x, y, 196), 4) == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                            : r < 18 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState();
                } else {
                    state = (y == b + 1 ? Blocks.SMOOTH_STONE : r < 7 ? Blocks.CRACKED_STONE_BRICKS : Blocks.CONCRETE.white()).defaultBlockState();
                }
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            put(level, box, x, y, z, state);
        }
        // the roof: glass over the greenhouse, else concrete, the panels on it in rows, some blown off
        put(level, box, x, b + high + 1, z, (glassHouse ? (r < 10 ? Blocks.AIR : Blocks.GLASS) : Blocks.CONCRETE.lightGray()).defaultBlockState());
        if (!glassHouse && !wall && Math.floorMod(dz, 2) == 0 && Town.noise(seed, x, z, 3.0, 198) <= 0.7) {
            put(level, box, x, b + high + 2, z, Blocks.DAYLIGHT_DETECTOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DaylightDetectorBlock.INVERTED, true));
        }
    }

    /** The shaft through the base: the last lift's room, doors on its four sides; over it the mast. */
    private static void core(WorldGenLevel level, long seed, BoundingBox box, int x, int z, int dx, int dz, int b, int ring) {
        boolean wall = ring == Tower.CORE;
        boolean door = wall && (dx == 0 || dz == 0);
        for (int y = b + 1; y <= b + 36; y++) {
            BlockState state;
            if (y <= b + 4) {
                state = wall && !(door && y <= b + 2) ? Blocks.CONCRETE.lightGray().defaultBlockState() : Blocks.AIR.defaultBlockState();
            } else if (y <= b + 6) {
                state = Blocks.CONCRETE.lightGray().defaultBlockState();
            } else if (ring <= 1 && y <= b + 30) {                          // the mast, narrowing
                state = ring == 0 ? Blocks.IRON_BLOCK.defaultBlockState() : y % 4 == 0 ? Blocks.IRON_BARS.defaultBlockState()
                        .setValue(IronBarsBlock.NORTH, dx == 0).setValue(IronBarsBlock.SOUTH, dx == 0)
                        .setValue(IronBarsBlock.EAST, dz == 0).setValue(IronBarsBlock.WEST, dz == 0) : Blocks.AIR.defaultBlockState();
            } else if (ring == 0 && y <= b + 35) {
                state = Blocks.IRON_BARS.defaultBlockState();
            } else if (ring == 0 && y == b + 36) {
                state = Blocks.END_ROD.defaultBlockState();
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            put(level, box, x, y, z, state);
        }
        if (dx == 0 && dz == 0) {
            put(level, box, x, b, z, dev.nookworks.registry.ModBlocks.ELEVATOR.get().defaultBlockState());
        }
    }

    /** The rooms' things: the lab, the quarters, the stores, the greenhouse, the lookout's ladder; the dead in all of it. */
    private static void furnish(WorldGenLevel level, long seed, Ascent.Site site, BoundingBox box, BlockPos c, int b) {
        int f = b + 1;
        long h = Town.hash(seed, c.getX(), b, 191);
        lab(level, seed, site, box, c, f, h);
        nest(level, seed, box, c, f, h);
        // the quarters, east: bunks in rows, lockers, a table
        for (int dz = -6; dz <= 6; dz += 3) {
            for (int dx : new int[] {15, 19}) {
                BlockState bed = Blocks.BED.lightGray().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
                put(level, box, c.getX() + dx + 1, f, c.getZ() + dz, bed.setValue(BedBlock.PART, BedPart.HEAD));
                put(level, box, c.getX() + dx, f, c.getZ() + dz, bed.setValue(BedBlock.PART, BedPart.FOOT));
            }
        }
        for (int dz = -7; dz <= 7; dz += 2) {
            Tower.container(level, box, c.getX() + 21, f, c.getZ() + dz, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.WEST),
                    Math.floorMod(dz, 4) == 1 ? "supplies" : "food", h ^ dz);
        }
        // the stores, west: crates stacked, chests, an anvil
        for (int dz = -7; dz <= 7; dz++) {
            long r = Math.floorMod(Town.hash(seed, c.getX() - 20, c.getZ() + dz, 192), 100);
            if (r < 55) {
                put(level, box, c.getX() - 21, f, c.getZ() + dz, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                if (r < 30) {
                    put(level, box, c.getX() - 21, f + 1, c.getZ() + dz, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                }
            }
        }
        Tower.container(level, box, c.getX() - 17, f, c.getZ() - 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "weapon", h >> 3);
        Tower.container(level, box, c.getX() - 17, f, c.getZ() + 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "tools", h >> 5);
        Tower.container(level, box, c.getX() - 15, f, c.getZ() - 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "stash", h >> 7);
        put(level, box, c.getX() - 15, f, c.getZ() + 4, Blocks.DAMAGED_ANVIL.defaultBlockState());
        // the greenhouse, south: beds of earth gone dry, dead stalks in them, the channels empty
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = 15; dz <= 20; dz++) {
                boolean channel = dx == 0 || dz == 17;
                put(level, box, c.getX() + dx, b, c.getZ() + dz, (channel ? Blocks.MUD : Blocks.COARSE_DIRT).defaultBlockState());
                if (!channel && Math.floorMod(Town.hash(seed, dx, dz, 193), 3) == 0) {
                    put(level, box, c.getX() + dx, f, c.getZ() + dz, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
        put(level, box, c.getX() - 7, f, c.getZ() + 14, Blocks.COMPOSTER.defaultBlockState());
        put(level, box, c.getX() + 7, f, c.getZ() + 14, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        // the lookout over the hub: a ladder up the shaft's south-east from the hub, a glass room on the hub's roof
        for (int y = f; y <= b + HIGH + 1; y++) {
            put(level, box, c.getX() + 5, y, c.getZ() + 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
            put(level, box, c.getX() + 4, y, c.getZ() + 5, Blocks.CONCRETE.lightGray().defaultBlockState());
        }
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring <= Tower.CORE || ring > 7) {
                    continue;
                }
                for (int y = b + HIGH + 2; y <= b + HIGH + 4; y++) {
                    put(level, box, c.getX() + dx, y, c.getZ() + dz, (ring == 7 ? Blocks.GLASS : Blocks.AIR).defaultBlockState());
                }
                put(level, box, c.getX() + dx, b + HIGH + 5, c.getZ() + dz, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        }
        put(level, box, c.getX() + 5, b + HIGH + 2, c.getZ() + 5, Blocks.AIR.defaultBlockState());
        papers(level, seed, box, c, f, h);
        workshop(level, box, c, f);
        // the dead: everywhere (the user: "ゾンビの巣窟"); and some who did not get out
        int[][] dead = {{6, 6, 2}, {-6, 6, 2}, {-6, -6, 3}, {7, -2, 2}, {0, 11, 2}, {11, 0, 2}, {-11, 0, 2},
                {16, -4, 3}, {18, 3, 2}, {17, 7, 2}, {-17, -3, 3}, {-19, 5, 2}, {-3, 16, 2}, {4, 19, 3}, {-5, -16, 3},
                {5, -17, 3}, {-3, -20, 2}, {3, -20, 2}, {20, 12, 2}, {-20, -12, 2}, {12, 20, 2}, {-12, -20, 2}};
        for (int[] d : dead) {
            put(level, box, c.getX() + d[0], f, c.getZ() + d[1], ZombieMarkerBlock.of(d[2], false));
        }
        // its guards: at the doors to the laboratory, in the corridors, on the deck
        for (int[] g : new int[][] {{-5, -18}, {5, -18}, {12, 0}, {-12, 0}, {0, 11}}) {
            sentry(level, box, c.getX() + g[0], f, c.getZ() + g[1]);
        }
        for (int[] body : new int[][] {{14, -6}, {-14, 6}, {-6, 19}}) {
            BlockPos at = new BlockPos(c.getX() + body[0], f, c.getZ() + body[1]);
            if (box.isInside(at)) {
                Markers.remains(level, at, Direction.NORTH, RandomSource.create(h ^ body[0] * 31L ^ body[1]));
            }
        }
        SkyBaseRooms.draw(level, box, c, b, h);                             // the way round and up to the laboratory (2026-10-23)
    }

    /**
     * The deepest part: the laboratory, eight high, its far wall the vaccine's - a dais, tanks of it standing in glass at
     * either side, the benches where it was made, cold. What is done there comes with part I.
     */
    private static void lab(WorldGenLevel level, long seed, Ascent.Site site, BoundingBox box, BlockPos c, int f, long h) {
        BlockPos v = vaccine(site);
        int w = v.getZ() - 1;                                               // the row along the glass
        // the dais, raised a step, the vaccine on it before the glass and the sky (the visual review, 2026-10-19: the end of
        // the way, and it stood flush with the floor)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = 0; dz <= 2; dz++) {
                put(level, box, v.getX() + dx, f, w + dz, Blocks.QUARTZ_BRICKS.defaultBlockState());
            }
            put(level, box, v.getX() + dx, f, w + 3, Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        put(level, box, v.getX(), v.getY(), v.getZ(), dev.nookofthedead.registry.ZwBlocks.VACCINE.get().defaultBlockState());
        apparatus(level, box, v, f, true);
        for (int dz = w + 4; dz <= c.getZ() - 14; dz++) {                   // the way to it: the one clean floor in the base
            for (int dx = -1; dx <= 1; dx++) {
                put(level, box, v.getX() + dx, f - 1, dz, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
        }
        for (int dx = -6; dx <= 6; dx += 4) {                               // its lamps, still lit: the hum they hear
            for (int dz = w + 2; dz <= c.getZ() - 15; dz += 3) {
                put(level, box, v.getX() + dx, f + LAB_HIGH - 1, dz, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        for (int dx = -5; dx <= 5; dx += 2) {                               // the benches down the hall, what was brewed on them
            for (int dz = 5; dz <= 8; dz++) {
                put(level, box, v.getX() + dx, f, w + dz + 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            }
            Dress.brewingStand(level, box, new BlockPos(v.getX() + dx, f + 1, w + 7), h ^ dx);
        }
        Tower.container(level, box, v.getX() - 7, f, w + 4, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "potion", h >> 11);
        Tower.container(level, box, v.getX() + 7, f, w + 4, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), "medical", h >> 13);
        BlockPos last = new BlockPos(v.getX() + 1, f, w + 4);         // the last of them, at the foot of the step
        if (box.isInside(last)) {
            Markers.remains(level, last, Direction.NORTH, RandomSource.create(h ^ 0x1A57L));
        }
    }

    /**
     * What they left of their papers (0.3, Nookworks 1.1's books): piles on the lab's benches, its notes among them;
     * books on the quarters' lockers and in their corners; more fallen anywhere a corner is.
     */
    private static void papers(WorldGenLevel level, long seed, BoundingBox box, BlockPos c, int f, long h) {
        RandomSource random = RandomSource.create(h ^ 0x9A9E5L);
        java.util.function.Supplier<net.minecraft.world.item.ItemStack> books = () -> Props.studied(level, Notes.BASE, random);
        int w = c.getZ() - HALF + 3;                                        // the lab's row along the glass, as lab() has it
        for (int dx = -5; dx <= 5; dx += 2) {                               // its benches, w + 6 .. w + 9 (lab())
            for (int dz = 6; dz <= 9; dz++) {
                if (Math.floorMod(Town.hash(seed, dx, dz, 207), 4) == 0) {
                    Props.pile(level, box, new BlockPos(c.getX() + dx, f + 1, w + dz), 1 + Math.floorMod(dx + dz, 3), random.nextFloat() * 360, random, books);
                }
            }
        }
        Props.pile(level, box, new BlockPos(c.getX() + 3, f + 1, w + 9), 2, 90, random, () -> Notes.book(Notes.BASE.get(0)));
        // where the keys were (the story's review, 2026-10-23), on the bench by the console
        Props.pile(level, box, new BlockPos(c.getX() - 3, f + 1, w + 9), 1, 270, random, () -> Notes.book(Notes.KEYS));
        Props.pile(level, box, new BlockPos(c.getX() + 21, f + 1, c.getZ() - 5), 1, 270, random, () -> Diaries.book(random));
        // the key-holders: the chief in the laboratory, shut in it; the director in their room upstairs (SkyBaseRooms)
        holder(level, box, new BlockPos(c.getX(), f, w + 6), h ^ 0x4E1);
        for (int dz = -7; dz <= 7; dz += 4) {                               // on the quarters' lockers
            Props.pile(level, box, new BlockPos(c.getX() + 21, f + 1, c.getZ() + dz), 1 + Math.floorMod(dz + 8, 3), 270, random,
                    dz == 1 ? () -> Notes.book(Notes.BASE.get(2)) : books);
        }
        Props.pile(level, box, new BlockPos(c.getX() - 15, f, c.getZ() - 5), 1, 0, random, () -> Notes.book(Notes.BASE.get(1)));
        Props.scatter(level, box, new BoundingBox(c.getX() - HALF, f, c.getZ() - HALF, c.getX() + HALF, f + 2, c.getZ() + HALF), seed ^ 0xBA5E,
                35, books, random);
    }

    /**
     * A key-holder of the base (the story's review, 2026-10-23): a thin-air zombie that was one of the four, its launch
     * key still in its hand, kept for good and always letting the key fall when it goes
     * ({@link dev.nookofthedead.item.LaunchKeyItem}), whatever puts it down.
     */
    static void holder(WorldGenLevel level, BoundingBox box, BlockPos at, long seed) {
        if (!box.isInside(at)) {
            return;
        }
        var dead = dev.nookofthedead.registry.ZwEntities.THIN_AIR_ZOMBIE.get().create(level.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (dead == null) {
            return;
        }
        dead.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, RandomSource.create(seed).nextFloat() * 360, 0);
        dead.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
        dead.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(dev.nookofthedead.registry.ZwItems.LAUNCH_KEY.get()));
        dead.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 2.0F);
        dead.setCanPickUpLoot(false);
        dead.setPersistenceRequired();
        dead.addTag(dev.nookofthedead.item.LaunchKeyItem.HOLDER);
        level.addFreshEntity(dead);
    }

    /**
     * The stores made a workshop (the user, 2026-10-20: in the base "好きな機械を置きまくっていい"): two rows of Nookworks'
     * machines in the middle of the west wing - Generators, Electric Furnaces, Crushers, Compressors, a Printing Press, a
     * Repair Box - an Energy Cable along the top of each row and one between them, joined as set down by hand; a barrel
     * of coal for the Generators.
     */
    private static void workshop(WorldGenLevel level, BoundingBox box, BlockPos c, int f) {
        net.minecraft.world.level.block.Block[][] rows = {
                {dev.nookworks.registry.ModBlocks.GENERATOR.get(), dev.nookworks.registry.ModBlocks.GENERATOR.get(),
                        dev.nookworks.registry.ModBlocks.ELECTRIC_FURNACE.get(), dev.nookworks.registry.ModBlocks.ELECTRIC_FURNACE.get(),
                        dev.nookworks.registry.ModBlocks.CRUSHER.get()},
                {dev.nookworks.registry.ModRecycling.COMPRESSOR.get(), dev.nookworks.registry.ModBlocks.PRINTING_PRESS.get(),
                        dev.nookworks.registry.ModBlocks.REPAIR_BOX.get(), dev.nookworks.registry.ModBlocks.CRUSHER.get(),
                        dev.nookworks.registry.ModRecycling.COMPRESSOR.get()}};
        int[] zs = {0, -3};
        Direction[] faces = {Direction.SOUTH, Direction.NORTH};
        java.util.List<BlockPos> cables = new java.util.ArrayList<>();
        java.util.List<net.minecraft.world.level.block.Block> machines = new java.util.ArrayList<>();
        for (int r = 0; r < 2; r++) {
            for (int i = 0; i < 5; i++) {
                BlockPos at = new BlockPos(c.getX() - 20 + i, f, c.getZ() + zs[r]);
                BlockState state = rows[r][i].defaultBlockState();
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                    state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, faces[r]);
                }
                put(level, box, at.getX(), at.getY(), at.getZ(), state);
                machines.add(rows[r][i]);
                cables.add(at.above());
            }
        }
        cables.add(new BlockPos(c.getX() - 20, f + 1, c.getZ() - 1));
        cables.add(new BlockPos(c.getX() - 20, f + 1, c.getZ() - 2));
        for (BlockPos at : cables) {
            put(level, box, at.getX(), at.getY(), at.getZ(), dev.nookworks.registry.ModBlocks.ENERGY_CABLE.get().defaultBlockState());
        }
        for (BlockPos at : cables) {
            if (!box.isInside(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            for (Direction d : Direction.values()) {
                BlockState next = level.getBlockState(at.relative(d));
                boolean join = next.getBlock() instanceof dev.nookworks.block.EnergyCableBlock || machines.contains(next.getBlock());
                state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d), join);
            }
            level.setBlock(at, state, 2);
        }
        BlockPos coal = new BlockPos(c.getX() - 21, f, c.getZ() - 1);
        if (box.isInside(coal)) {
            level.setBlock(coal, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST), 2);
            if (level.getBlockEntity(coal) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
                for (int slot = 0; slot < 3; slot++) {
                    barrel.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 64));
                }
            }
        }
    }

    /**
     * The base as the dead's nest (the user: "最後の楽園は、ゾンビの巣窟と化していた"; the visual review, 2026-10-19): the
     * barricades they put across the wings' doors, broken through from outside; the lab's doors forced; bedding dragged
     * into heaps with what is left of the ones it was taken from; the quarters' partitions torn down; red emergency
     * lamps; the ones who did not get out.
     */
    private static void nest(WorldGenLevel level, long seed, BoundingBox box, BlockPos c, int f, long h) {
        for (Direction out : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH}) {
            Direction across = out.getClockWise();
            BlockPos door = c.relative(out, WING - 1).atY(f);
            // the barricades: what they had - scrap baled, patched sheet, sheets off the greenhouse's frames (Nookworks 1.1)
            for (int k : new int[] {-1, 1}) {
                BlockPos at = door.relative(across, k);
                long r = Town.hash(seed, at.getX(), f, at.getZ());
                put(level, box, at.getX(), f, at.getZ(), k < 0 ? Props.bale(r) : Props.patched());
                put(level, box, at.getX(), f + 1, at.getZ(), Props.sheet(r >> 4, out.getOpposite(), out.getAxis(), true));
            }
            BlockPos strewn = c.relative(out, WING - 2).atY(f);               // pushed in toward the hub
            put(level, box, strewn.getX(), f, strewn.getZ(), Props.bale(Town.hash(seed, strewn.getX(), f, strewn.getZ())));
            BlockPos slab = c.relative(out, WING - 3).relative(across, 1).atY(f);
            put(level, box, slab.getX(), f, slab.getZ(), Props.sheet(Town.hash(seed, slab.getX(), 1, slab.getZ()), Direction.UP, across.getAxis(), false));
            BlockPos lid = c.relative(out, WING - 3).relative(across, -1).atY(f);
            put(level, box, lid.getX(), f, lid.getZ(), Props.sheet(Town.hash(seed, lid.getX(), 2, lid.getZ()), Direction.UP, out.getAxis(), false));
        }
        for (int dx : new int[] {-1, 1}) {                                   // the lab's doors, forced
            BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true)
                    .setValue(net.minecraft.world.level.block.DoorBlock.HINGE, dx < 0 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                            : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
            put(level, box, c.getX() + dx, f, c.getZ() - WING, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            put(level, box, c.getX() + dx, f + 1, c.getZ() - WING, door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        // bedding dragged into heaps: in the quarters and in the hub
        for (int[] heap : new int[][] {{19, -7}, {15, 7}, {-7, -7}, {7, 7}}) {
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 1; b++) {
                    put(level, box, c.getX() + heap[0] + a, f, c.getZ() + heap[1] + b, ((a + b) % 2 == 0 ? Blocks.WOOL.white() : Blocks.WOOL.lightGray())
                            .defaultBlockState());
                }
            }
            for (int[] o : new int[][] {{-1, 0}, {2, 1}, {0, -1}, {1, 2}}) {
                put(level, box, c.getX() + heap[0] + o[0], f, c.getZ() + heap[1] + o[1], (o[0] < 0 ? Blocks.CARPET.brown() : Blocks.CARPET.lightGray())
                        .defaultBlockState());
            }
            Dress.bones(level, box, new BlockPos(c.getX() + heap[0], f + 1, c.getZ() + heap[1]), Town.hash(seed, heap[0], heap[1], 200), false);
        }
        put(level, box, c.getX() + 19, f, c.getZ() - 6, Blocks.AIR.defaultBlockState());   // the bunks the bedding came from
        put(level, box, c.getX() + 20, f, c.getZ() - 6, Blocks.AIR.defaultBlockState());
        // the quarters' partitions, torn down
        for (int dz : new int[] {-5, -2, 1, 4}) {
            for (int dx = 14; dx <= 20; dx++) {
                long r = Math.floorMod(Town.hash(seed, dx, dz, 201), 100);
                if (r < 40) {
                    put(level, box, c.getX() + dx, f, c.getZ() + dz, Blocks.COBBLESTONE_SLAB.defaultBlockState());
                } else if (!(dx == 15 || dx == 16 || dx == 19 || dx == 20)) {        // clear of the bunks' rows
                    for (int y = f; y <= f + 1; y++) {
                        put(level, box, c.getX() + dx, y, c.getZ() + dz, Blocks.CONCRETE.lightGray().defaultBlockState());
                    }
                }
            }
        }
        // red emergency lamps in the corridors and at the hub's corners: a glint, not enough to keep anything away
        for (Direction out : Direction.Plane.HORIZONTAL) {
            BlockPos lamp = c.relative(out, 10).relative(out.getClockWise(), 2).atY(f + 2);
            put(level, box, lamp.getX(), lamp.getY(), lamp.getZ(), Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.RedstoneWallTorchBlock.FACING, out.getCounterClockWise()));
        }
        for (Direction out : new Direction[] {Direction.EAST, Direction.WEST}) {   // the ones who did not get out, at the breaches
            BlockPos at = c.relative(out, 11).relative(out.getClockWise(), out == Direction.EAST ? 1 : -1).atY(f);
            if (box.isInside(at)) {
                Markers.remains(level, at, out.getOpposite(), RandomSource.create(h ^ out.ordinal()));
            }
        }
        // webs in the wings' far corners
        for (int[] w : new int[][] {{21, -7}, {21, 7}, {-21, -7}, {-21, 7}, {-7, 21}, {7, 21}}) {
            put(level, box, c.getX() + w[0], f + HIGH - 1, c.getZ() + w[1], Blocks.COBWEB.defaultBlockState());
        }
    }

    /**
     * The vaccine's vessel and its tanks, along the glass behind the console {@code v} (the user, 2026-10-20, of the trailer:
     * the machine, one cube on the dais, looked poor beside the rest - "他がよい分、余計にしょぼく見える"): the vessel three wide
     * and four high on a copper foot, slim copper posts either side (in game: full blocks of it were a heavy orange
     * doorway), capped, standing on the dais with the sky through it; from its cap a pipe up into the ceiling,
     * the way out to the roof; two tanks either side, piped to it. {@code full} unless the If is cured ({@link #drain}).
     */
    static void apparatus(WorldGenLevel level, BoundingBox box, BlockPos v, int f, boolean full) {
        int x = v.getX();
        int w = v.getZ() - 1;
        BlockState tank = dev.nookofthedead.registry.ZwBlocks.VACCINE_TANK.get().defaultBlockState()
                .setValue(dev.nookofthedead.ending.VaccineTankBlock.FULL, full);
        BlockState cut = Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState();
        BlockState chiselled = Blocks.CHISELED_COPPER.waxed().unaffected().defaultBlockState();
        BlockState bars = Blocks.COPPER_BARS.waxed().unaffected().defaultBlockState();
        BlockState slab = Blocks.CUT_COPPER_SLAB.waxed().unaffected().defaultBlockState();
        BlockState rod = Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            boolean post = Math.abs(dx) == 2;
            for (int y = f + 1; y <= f + 6; y++) {
                BlockState s = y == f + 1 ? (post ? chiselled : cut)
                        : y == f + 6 ? (post ? slab : cut)
                        : post ? bars : tank;
                put(level, box, x + dx, y, w, s);
            }
        }
        put(level, box, x, f + 7, w, rod);                                  // up, out to the roof
        for (int side : new int[] {-6, -4, 4, 6}) {
            for (int y = f; y <= f + 5; y++) {
                put(level, box, x + side, y, w, y == f ? chiselled : y == f + 5 ? slab : tank);
            }
        }
        for (int dx : new int[] {3, 5}) {                                   // and piped to it, cap to cap
            put(level, box, x + dx, f + 5, w, rod.setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.EAST));
            put(level, box, x - dx, f + 5, w, rod.setValue(net.minecraft.world.level.block.LightningRodBlock.FACING, Direction.WEST));
        }
    }

    /**
     * The vaccine let go ({@link dev.nookofthedead.ending.Ending}): its vessel and tanks run down, {@code step} rows from
     * the top (the vessel's four, the tanks' four), each with the sound of it; any step past the last empties them all.
     */
    public static void drain(net.minecraft.server.level.ServerLevel level, int step) {
        BlockPos v = vaccine(Ascent.site(level.getSeed()));
        int top = v.getY() + 4;
        boolean any = false;
        for (BlockPos p : BlockPos.betweenClosed(v.offset(-7, -2, -2), v.offset(7, 6, 0))) {
            BlockState s = level.getBlockState(p);
            if (s.is(dev.nookofthedead.registry.ZwBlocks.VACCINE_TANK.get()) && s.getValue(dev.nookofthedead.ending.VaccineTankBlock.FULL)
                    && p.getY() + (Math.abs(p.getX() - v.getX()) > 2 ? 1 : 0) >= top - step) {
                level.setBlock(p, s.setValue(dev.nookofthedead.ending.VaccineTankBlock.FULL, false), Block.UPDATE_ALL);
                any = true;
            }
        }
        if (any) {
            level.playSound(null, v.relative(Direction.NORTH), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.5F, 0.6F + 0.1F * Math.min(step, 4));
        }
    }

    /** How near a player comes before a laboratory made before the vessel is given it. */
    static final int REFIT_FROM = 40;

    /**
     * A laboratory made before the vessel (2026-10-20) - the console against the glass, a chain hung over it, glass tanks
     * on sea lanterns - is given the vessel the first time a player comes within {@link #REFIT_FROM} of it: the old tanks
     * and chains taken out, the console moved a step out, the apparatus put up (empty if the If is cured). Once only (kept
     * with the world, {@link Refuge.Retrofitted}).
     */
    static void refit(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.level();
        if (!dev.nookofthedead.NookOfTheDead.fallen(level)) {
            return;
        }
        BlockPos v = vaccine(Ascent.site(level.getSeed()));
        if (player.blockPosition().distSqr(v) > REFIT_FROM * REFIT_FROM || !level.hasChunksAt(v.offset(-8, -2, -3), v.offset(8, 8, 3))) {
            return;
        }
        Refuge.Retrofitted done = level.getServer().getDataStorage().computeIfAbsent(Refuge.Retrofitted.TYPE);
        String key = "vessel:" + dev.nookworks.ifworld.IfWorld.get(level.getServer()).number();
        if (done.refuges.contains(key)) {
            return;
        }
        BlockPos old = v.relative(Direction.NORTH);
        if (level.getBlockState(old).is(dev.nookofthedead.registry.ZwBlocks.VACCINE.get())) {
            int f = Tower.BASE + 1;
            for (BlockPos p : BlockPos.betweenClosed(v.offset(-6, -1, 0), v.offset(6, 4, 0))) {
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.IRON_CHAIN) || s.is(Blocks.SEA_LANTERN) || s.is(Blocks.STAINED_GLASS.lightBlue()) || s.is(Blocks.IRON_BLOCK)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.setBlock(v, dev.nookofthedead.registry.ZwBlocks.VACCINE.get().defaultBlockState(), Block.UPDATE_ALL);
            apparatus(level, BoundingBox.infinite(), v, f, !dev.nookofthedead.ending.Cure.released(level.getServer()));
            dev.nookofthedead.NookOfTheDead.LOGGER.info("The laboratory at {} given its vessel (made before it)", v);
        }
        done.refuges.add(key);
        done.setDirty();
    }

    private static void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        Tower.put(level, box, x, y, z, state);
    }

    /** A Sentry Golem standing at ({@code x}, {@code y}, {@code z}), made by the chunk it stands in. */
    static void sentry(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
        if (!box.isInside(x, y, z)) {
            return;
        }
        var golem = dev.nookofthedead.registry.ZwEntities.SENTRY_GOLEM.get().create(level.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (golem != null) {
            golem.snapTo(x + 0.5, y, z + 0.5, Math.floorMod(x * 31 + z, 360), 0);
            level.addFreshEntity(golem);
        }
    }

    private SkyBase() {}
}
