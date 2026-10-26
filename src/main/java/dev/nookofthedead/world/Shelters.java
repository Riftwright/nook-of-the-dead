package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The shelters the townspeople dug under their town (the user, 2026-10-23, of a vault with its way in hidden in a
 * park; diary 10, the caves where people hid): one block of the town in a few, in its streets' quarters, a well that is
 * only a well to look at - dry, a ladder down its shaft - and at its foot a hall dug out of the rock, low passages off it
 * to rooms: where they slept, what they kept, where the sick lay, where they prayed, where they drew water, and one
 * passage that came down on them. The plague got in all the same: the dead standing about it, and the ones who never
 * left. Each chunk draws its own part, fixed by the seed and the place.
 */
public final class Shelters {
    /** Of a quarter's blocks, how many in a hundred have one. */
    public static final int SHARE = 22;
    /** How deep its floor is under the ground at the well. */
    static final int DEEP = 14;
    /** Its rooms' air: from the floor up this many. */
    static final int HIGH = 4;
    /** From the hall's middle to a room's middle. */
    static final int REACH = 10;
    /** The lowest its floor may be: well over the galleries ({@link Tunnels}). */
    static final int LOWEST = Tunnels.FLOOR + Tunnels.HIGH + 3;

    /** What a room off the hall is. */
    public enum Room { BEDS, STORES, SICK, PRAYER, WATER, FALLEN }

    /** A shelter: its well's middle (its floor's level as y), and its rooms by the way they lie from the hall. */
    public record Shelter(BlockPos well, int top, Map<Direction, Room> rooms, long seed) {}

    /**
     * What is kept, by every input whole: the seed, the buildings' generation and the block (the review, 2026-10-25: one
     * long made of them all let another If's "none here" stand for a shelter of this one).
     */
    private record Key(long seed, long generation, int bx, int bz) {}
    private static final Map<Key, Optional<Shelter>> CACHE = new ConcurrentHashMap<>();
    /** How many blocks' answers are kept at most: past it, forgotten and worked out again (a long journey's worth). */
    private static final int KEPT = 20_000;

    /** Forgets every shelter worked out so far (for the tests: each asks from nothing). */
    public static void forget() {
        CACHE.clear();
    }

    /** The shelter under block ({@code bx}, {@code bz}), if it has one. */
    public static Optional<Shelter> at(long seed, int bx, int bz) {
        if (CACHE.size() > KEPT) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(new Key(seed, District.generation(), bx, bz), k -> find(seed, bx, bz));
    }

    private static Optional<Shelter> find(long seed, int bx, int bz) {
        if (Capital.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK, 8)) {
            return Optional.empty();                                        // not under the capital (2026-10-24)
        }
        District district = District.of(seed, bx, bz);
        if (!EnumSet.of(District.CENTRE, District.RESIDENTIAL, District.OLD_TOWN, District.OUTSKIRTS).contains(district)
                || Institute.at(seed, bx, bz) || Math.floorMod(Town.hash(seed, bx, bz, 180), 100) >= SHARE) {
            return Optional.empty();
        }
        for (int i = 0; i < 24; i++) {                                      // a spot of open ground for its well
            long h = Town.hash(seed, bx * 64L + i, bz, 181);
            int x = bx * Town.BLOCK + 16 + (int) Math.floorMod(h, (long) Town.BLOCK - 32);
            int z = bz * Town.BLOCK + 16 + (int) Math.floorMod(h >> 16, (long) Town.BLOCK - 32);
            boolean clear = true;
            for (int dx = -2; dx <= 2 && clear; dx++) {
                for (int dz = -2; dz <= 2 && clear; dz++) {
                    clear = Tunnels.open(seed, x + dx, z + dz);
                }
            }
            if (!clear) {
                continue;
            }
            int top = Town.plan(seed, x - 8, z - 8).top()[8][8];
            if (top - DEEP < LOWEST) {
                continue;
            }
            List<Room> kinds = new ArrayList<>(List.of(Room.values()));
            RandomSource random = RandomSource.create(h);
            java.util.Collections.shuffle(kinds, new java.util.Random(h));
            Map<Direction, Room> rooms = new java.util.EnumMap<>(Direction.class);
            int k = 0;
            for (Direction way : Direction.Plane.HORIZONTAL) {
                if (random.nextInt(5) != 0) {                               // three or four of the four ways
                    rooms.put(way, kinds.get(k++));
                }
            }
            return Optional.of(new Shelter(new BlockPos(x, top - DEEP, z), top, rooms, h));
        }
        return Optional.empty();
    }

    /** This chunk's part of the shelters under it and round it. */
    static void draw(WorldGenLevel level, long seed, BoundingBox box) {
        int bx0 = Math.floorDiv(box.minX() - 24, Town.BLOCK);
        int bx1 = Math.floorDiv(box.maxX() + 24, Town.BLOCK);
        int bz0 = Math.floorDiv(box.minZ() - 24, Town.BLOCK);
        int bz1 = Math.floorDiv(box.maxZ() + 24, Town.BLOCK);
        for (int bx = bx0; bx <= bx1; bx++) {
            for (int bz = bz0; bz <= bz1; bz++) {
                at(seed, bx, bz).ifPresent(shelter -> {
                    BlockPos w = shelter.well();
                    if (w.getX() + REACH + 6 >= box.minX() && w.getX() - REACH - 6 <= box.maxX()
                            && w.getZ() + REACH + 6 >= box.minZ() && w.getZ() - REACH - 6 <= box.maxZ()) {
                        Diaries.at(Diaries.Place.SHELTER);
                        try {
                            new Digger(level, box, shelter).dig();
                        } finally {
                            Diaries.at(Diaries.Place.TOWN);
                        }
                    }
                });
            }
        }
    }

    /** One shelter dug, inside a chunk's box. */
    private record Digger(WorldGenLevel level, BoundingBox box, Shelter s) {
        void put(int x, int y, int z, BlockState state) {
            Tower.put(this.level, this.box, x, y, z, state);
        }

        void put(int x, int y, int z, net.minecraft.world.level.block.Block block) {
            this.put(x, y, z, block.defaultBlockState());
        }

        /** Air from the floor up {@code high} over the box ({@code x0}..{@code x1}, {@code z0}..{@code z1}), a floor of packed earth under it. */
        void hollow(int x0, int z0, int x1, int z1, int high) {
            int f = this.s.well().getY();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    this.put(x, f, z, (Math.floorMod(Town.hash(this.s.seed(), x, z, 182), 4) == 0 ? Blocks.COARSE_DIRT : Blocks.PACKED_MUD));
                    for (int y = f + 1; y <= f + high; y++) {
                        this.put(x, y, z, Blocks.CAVE_AIR);
                    }
                }
            }
        }

        void container(int x, int y, int z, BlockState state, String loot) {
            Tower.container(this.level, this.box, x, y, z, state, loot, this.s.seed() ^ (x * 31L + z) * 0x9E37L ^ y);
        }

        /**
         * "Treasure number one" (the boy's book, diary 45: a stone that glows blue, found at the bottom of the well, given
         * to Luca): in a box by the beds, alone.
         */
        void treasure(int x, int y, int z) {
            BlockPos at = new BlockPos(x, y, z);
            if (this.box.isInside(at) && this.level.getBlockState(at).isAir()) {
                this.level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 2);
                if (this.level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity box) {
                    box.setItem(13, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI));
                }
            }
        }

        void marker(int x, int y, int z, int count) {
            this.put(x, y, z, ZombieMarkerBlock.of(count, false));
        }

        void lamp(int x, int y, int z) {
            this.put(x, y, z, dev.nookworks.registry.ModBlocks.UNLIT_LANTERN.get().defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }

        void remains(int x, int y, int z, Direction facing) {
            BlockPos at = new BlockPos(x, y, z);
            if (this.box.isInside(at)) {
                Markers.remains(this.level, at, facing, RandomSource.create(this.s.seed() ^ at.asLong()));
            }
        }

        void dig() {
            BlockPos w = this.s.well();
            int cx = w.getX();
            int cz = w.getZ();
            int f = w.getY();
            int top = this.s.top();
            this.well(cx, cz, top);
            // the hall under the well, its shaft coming down into it
            this.hollow(cx - 3, cz - 3, cx + 3, cz + 3, HIGH);
            for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {   // props at its corners
                for (int y = f + 1; y <= f + HIGH; y++) {
                    this.put(cx + p[0], y, cz + p[1], Blocks.SPRUCE_LOG);
                }
            }
            this.put(cx + 2, f + 1, cz + 2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
            this.put(cx - 2, f + 1, cz + 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            this.put(cx + 2, f + 1, cz - 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            this.lamp(cx, f + HIGH, cz + 2);
            BlockPos pile = new BlockPos(cx - 2, f + 1, cz - 2);
            if (this.box.isInside(pile)) {
                RandomSource random = RandomSource.create(this.s.seed() ^ 0x5EE1L);
                Props.pile(this.level, this.box, pile, 2, random.nextFloat() * 360, random, () -> Diaries.book(random));
            }
            this.marker(cx + 1, f + 1, cz - 1, 2);
            for (Map.Entry<Direction, Room> room : this.s.rooms().entrySet()) {
                this.passage(room.getKey());
                this.room(room.getKey(), room.getValue());
            }
        }

        /** The well over the shaft: a rim of old stone, a roof on two posts, its rope cut; dry, and a ladder down its side. */
        void well(int cx, int cz, int top) {
            int f = this.s.well().getY();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean shaft = dx == 0 && dz == 0;
                    for (int y = f + HIGH + 1; y <= top; y++) {
                        this.put(cx + dx, y, cz + dz, shaft ? Blocks.CAVE_AIR.defaultBlockState()
                                : Blocks.COBBLESTONE.defaultBlockState());
                    }
                    if (!shaft) {
                        long r = Town.hash(this.s.seed(), dx, dz, 183);
                        this.put(cx + dx, top + 1, cz + dz, (Math.floorMod(r, 3) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState());
                    }
                }
            }
            this.put(cx, top + 1, cz, Blocks.CAVE_AIR);
            for (int y = f + 1; y <= top + 1; y++) {                       // the ladder on the shaft's north side, on a post in the hall
                this.put(cx, y, cz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
                if (y <= f + HIGH) {
                    this.put(cx, y, cz - 1, Blocks.COBBLESTONE);
                }
            }
            for (int dx : new int[] {-1, 1}) {                              // the posts and the little roof
                this.put(cx + dx, top + 2, cz, Blocks.SPRUCE_FENCE);
                this.put(cx + dx, top + 3, cz, Blocks.SPRUCE_FENCE);
            }
            for (int dx = -2; dx <= 2; dx++) {
                this.put(cx + dx, top + 4, cz - 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                this.put(cx + dx, top + 4, cz, Blocks.SPRUCE_SLAB);
                this.put(cx + dx, top + 4, cz + 1, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            }
            this.put(cx, top + 3, cz, Blocks.IRON_CHAIN);
        }

        /** The low passage from the hall's side {@code way} to its room: two wide, three high, propped. */
        void passage(Direction way) {
            BlockPos w = this.s.well();
            int f = w.getY();
            Direction across = way.getClockWise();
            for (int d = 4; d <= REACH - 4; d++) {
                for (int s = 0; s <= 1; s++) {
                    int x = w.getX() + way.getStepX() * d + across.getStepX() * s;
                    int z = w.getZ() + way.getStepZ() * d + across.getStepZ() * s;
                    this.put(x, f, z, Blocks.PACKED_MUD);
                    for (int y = f + 1; y <= f + 3; y++) {
                        this.put(x, y, z, Blocks.CAVE_AIR);
                    }
                    this.put(x, f + 3, z, d == REACH - 5 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.CAVE_AIR.defaultBlockState());
                }
            }
        }

        /** The room at side {@code way}: seven across, what it was for in it. */
        void room(Direction way, Room kind) {
            BlockPos w = this.s.well();
            int f = w.getY();
            int rx = w.getX() + way.getStepX() * REACH;
            int rz = w.getZ() + way.getStepZ() * REACH;
            if (kind == Room.FALLEN) {                                      // the passage came down: rubble to its end, one under it
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        long r = Town.hash(this.s.seed(), rx + dx, rz + dz, 184);
                        this.put(rx + dx, f, rz + dz, Blocks.PACKED_MUD);
                        int high = Math.floorMod(r, 3) + 1;
                        for (int y = f + 1; y <= f + high; y++) {
                            this.put(rx + dx, y, rz + dz, (Math.floorMod(r >> y, 3) == 0 ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState());
                        }
                    }
                }
                int ex = w.getX() + way.getStepX() * (REACH - 4);
                int ez = w.getZ() + way.getStepZ() * (REACH - 4);
                this.put(ex, f + 1, ez, Blocks.GRAVEL);
                this.remains(w.getX() + way.getStepX() * (REACH - 5), f + 1, w.getZ() + way.getStepZ() * (REACH - 5), way.getOpposite());
                return;
            }
            this.hollow(rx - 3, rz - 3, rx + 3, rz + 3, HIGH);
            this.lamp(rx, f + HIGH, rz);
            Direction back = way;                                           // its far wall, away from the hall
            Direction across = way.getClockWise();
            int bx = rx + back.getStepX() * 3;
            int bz = rz + back.getStepZ() * 3;
            switch (kind) {
                case BEDS -> {
                    for (int s : new int[] {-3, -1, 1, 3}) {
                        int x = rx + across.getStepX() * s + back.getStepX() * 2;
                        int z = rz + across.getStepZ() * s + back.getStepZ() * 2;
                        BlockState bed = (s % 2 == 0 ? Blocks.BED.brown() : Blocks.BED.lightGray()).defaultBlockState().setValue(BedBlock.FACING, back);
                        this.put(x, f + 1, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
                        this.put(x - back.getStepX(), f + 1, z - back.getStepZ(), bed.setValue(BedBlock.PART, BedPart.FOOT));
                    }
                    this.container(rx + across.getStepX() * 3, f + 1, rz + across.getStepZ() * 3, Blocks.BARREL.defaultBlockState()
                            .setValue(BarrelBlock.FACING, Direction.UP), "supplies");
                    this.treasure(rx + across.getStepX() * -3, f + 1, rz + across.getStepZ() * -3);
                    this.remains(rx + across.getStepX() * -1, f + 1, rz + across.getStepZ() * -1, back.getOpposite());
                    this.marker(rx + across.getStepX() * 2, f + 1, rz + across.getStepZ() * 2, 2);
                }
                case STORES -> {
                    for (int s = -3; s <= 3; s++) {
                        int x = bx + across.getStepX() * s;
                        int z = bz + across.getStepZ() * s;
                        long r = Math.floorMod(Town.hash(this.s.seed(), x, z, 185), 4);
                        if (r == 0) {
                            this.put(x, f + 1, z, dev.nookworks.registry.ModBlocks.CRATE.get());
                        } else {
                            this.container(x, f + 1, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), r == 1 ? "supplies" : "food");
                        }
                        if (r < 2) {
                            this.put(x, f + 2, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                        }
                    }
                    this.marker(rx, f + 1, rz, 1);
                }
                case SICK -> {
                    for (int s : new int[] {-2, 2}) {
                        int x = rx + across.getStepX() * s + back.getStepX() * 2;
                        int z = rz + across.getStepZ() * s + back.getStepZ() * 2;
                        BlockState bed = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, back);
                        this.put(x, f + 1, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
                        this.put(x - back.getStepX(), f + 1, z - back.getStepZ(), bed.setValue(BedBlock.PART, BedPart.FOOT));
                    }
                    Dress.brewingStand(this.level, this.box, new BlockPos(bx, f + 1, bz), this.s.seed() ^ 0x51CL);
                    this.put(bx + across.getStepX(), f + 1, bz + across.getStepZ(), Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(BlockStateProperties.LEVEL_CAULDRON, 1));
                    this.container(bx - across.getStepX(), f + 1, bz - across.getStepZ(), Blocks.CHEST.defaultBlockState()
                            .setValue(ChestBlock.FACING, back.getOpposite()), "medical");
                    this.remains(rx + across.getStepX() * 2 + back.getStepX() * 2, f + 2, rz + across.getStepZ() * 2 + back.getStepZ() * 2, back.getOpposite());
                    this.marker(rx - across.getStepX(), f + 1, rz - across.getStepZ(), 1);
                }
                case PRAYER -> {
                    this.put(bx, f + 1, bz, Blocks.CHISELED_STONE_BRICKS);
                    this.put(bx, f + 2, bz, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3));
                    for (int s : new int[] {-2, 2}) {
                        this.put(bx + across.getStepX() * s, f + 1, bz + across.getStepZ() * s, Blocks.CANDLE.defaultBlockState()
                                .setValue(BlockStateProperties.CANDLES, 2));
                    }
                    for (int d = -1; d <= 1; d++) {                         // benches facing the stone
                        for (int s : new int[] {-2, -1, 1, 2}) {
                            this.put(rx + across.getStepX() * s - back.getStepX() * d, f + 1, rz + across.getStepZ() * s - back.getStepZ() * d,
                                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, back.getOpposite()));
                        }
                    }
                    BlockPos lectern = new BlockPos(bx - back.getStepX(), f + 1, bz - back.getStepZ());
                    if (this.box.isInside(lectern)) {
                        this.level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, back.getOpposite())
                                .setValue(LecternBlock.HAS_BOOK, true), 2);
                        if (this.level.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity stand) {
                            stand.setBook(Diaries.book(RandomSource.create(this.s.seed() ^ 0x9A7L)));
                        }
                    }
                    this.remains(rx, f + 1, rz - back.getStepZ() * 2 - back.getStepX() * 0, back);
                }
                case WATER -> {
                    for (int dx = -1; dx <= 1; dx++) {                       // the pool they dug down to the water
                        for (int dz = -1; dz <= 1; dz++) {
                            this.put(rx + dx, f, rz + dz, Blocks.WATER);
                            this.put(rx + dx, f - 1, rz + dz, Blocks.CLAY);
                        }
                    }
                    for (int s : new int[] {-3, 3}) {
                        this.put(rx + across.getStepX() * s, f + 1, rz + across.getStepZ() * s, Blocks.WATER_CAULDRON.defaultBlockState()
                                .setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
                    }
                    this.container(bx, f + 1, bz, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), "food");
                    this.marker(rx + across.getStepX() * 2, f + 1, rz + across.getStepZ() * 2, 1);
                }
                default -> {
                }
            }
        }
    }

    private Shelters() {}
}
