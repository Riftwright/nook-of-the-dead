package dev.nookofthedead.world;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The sky base made a dungeon with a way through it (the user, 2026-10-23: "天空ダンジョンは、エンドシティや黄昏の森のリッチ
 * タワーみたいに、分岐や行き止まりみたいな部屋があってもいいかも"). The laboratory is shut from the hub - its corridor caved in
 * before a blast door sealed from inside, the deck about it fallen away so it stands out over the drop - and the way to it
 * is round and up:
 * <ol>
 * <li>through the quarters (east) and up the stair in their north corner, to the landing built on their roof;</li>
 * <li>out along the catwalk north, in the wind; at its end the way splits - east, the water tank (a dead end, its stash),
 *     west, a bridge over the drop and steps up to the laboratory's roof;</li>
 * <li>down the hatch to the gallery inside the laboratory, and down its ladder to the floor.</li>
 * </ol>
 * Off the landing: the director's room (one of the key-holders, {@link SkyBase#holder}) and, up a ladder, the radio mast
 * on its roof (a dead end with the view, and the signal lamp of the diaries). Off the wings below: the infirmary, the
 * washroom, the cold store - side rooms, the dead in them. Drawn over the base by {@link SkyBase#draw}, each chunk its part.
 */
final class SkyBaseRooms {
    private final WorldGenLevel level;
    private final BoundingBox box;
    private final BlockPos c;
    private final int b;
    private final long h;
    private final RandomSource random;

    private SkyBaseRooms(WorldGenLevel level, BoundingBox box, BlockPos c, int b, long h) {
        this.level = level;
        this.box = box;
        this.c = c;
        this.b = b;
        this.h = h;
        this.random = RandomSource.create(h ^ 0x5EB0L);
    }

    /** The upper floor, the way to the laboratory, the side rooms: over {@code c}'s base, its deck at {@code b}. */
    static void draw(WorldGenLevel level, BoundingBox box, BlockPos c, int b, long h) {
        SkyBaseRooms rooms = new SkyBaseRooms(level, box, c, b, h);
        rooms.seal();
        rooms.stair();
        rooms.landing();
        rooms.director();
        rooms.mast();
        rooms.catwalk();
        rooms.tank();
        rooms.gallery();
        rooms.infirmary();
        rooms.washroom();
        rooms.coldStore();
    }

    // --- where things go (dx, dz from the base's middle; y the world's) -------------------------------------------------------

    void set(int dx, int y, int dz, BlockState state) {
        Tower.put(this.level, this.box, this.c.getX() + dx, y, this.c.getZ() + dz, state);
    }

    void set(int dx, int y, int dz, Block block) {
        this.set(dx, y, dz, block.defaultBlockState());
    }

    void fill(int dx0, int y0, int dz0, int dx1, int y1, int dz1, BlockState state) {
        for (int dx = dx0; dx <= dx1; dx++) {
            for (int y = y0; y <= y1; y++) {
                for (int dz = dz0; dz <= dz1; dz++) {
                    this.set(dx, y, dz, state);
                }
            }
        }
    }

    void fill(int dx0, int y0, int dz0, int dx1, int y1, int dz1, Block block) {
        this.fill(dx0, y0, dz0, dx1, y1, dz1, block.defaultBlockState());
    }

    BlockPos at(int dx, int y, int dz) {
        return new BlockPos(this.c.getX() + dx, y, this.c.getZ() + dz);
    }

    void container(int dx, int y, int dz, BlockState state, String loot) {
        Tower.container(this.level, this.box, this.c.getX() + dx, y, this.c.getZ() + dz, state, loot,
                this.h ^ (dx * 31L + dz) * 0x9E37L ^ y);
    }

    void marker(int dx, int y, int dz, int count) {
        this.set(dx, y, dz, ZombieMarkerBlock.of(count, false));
    }

    void lantern(int dx, int y, int dz) {
        this.set(dx, y, dz, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    void pile(int dx, int y, int dz, int n, Supplier<ItemStack> books) {
        Props.pile(this.level, this.box, this.at(dx, y, dz), n, this.random.nextFloat() * 360, this.random, books);
    }

    void ladder(int dx, int y0, int y1, int dz, Direction facing) {
        for (int y = y0; y <= y1; y++) {
            this.set(dx, y, dz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        }
    }

    /** Iron bars along {@code cells} (dx, y, dz), each joined to its neighbours among them. */
    void rail(Set<BlockPos> cells) {
        for (BlockPos p : cells) {
            this.set(p.getX(), p.getY(), p.getZ(), Blocks.IRON_BARS.defaultBlockState()
                    .setValue(IronBarsBlock.NORTH, cells.contains(p.north())).setValue(IronBarsBlock.SOUTH, cells.contains(p.south()))
                    .setValue(IronBarsBlock.EAST, cells.contains(p.east())).setValue(IronBarsBlock.WEST, cells.contains(p.west())));
        }
    }

    /**
     * A room of the upper floor: walls from {@code y0} up {@code high}, windows in them, a roof over; the floor it stands on
     * already there (a roof below). Its inside cleared.
     */
    void room(int dx0, int dz0, int dx1, int dz1, int y0, int high) {
        for (int dx = dx0; dx <= dx1; dx++) {
            for (int dz = dz0; dz <= dz1; dz++) {
                boolean wall = dx == dx0 || dx == dx1 || dz == dz0 || dz == dz1;
                boolean corner = (dx == dx0 || dx == dx1) && (dz == dz0 || dz == dz1);
                for (int y = y0; y < y0 + high; y++) {
                    boolean window = wall && !corner && y > y0 && y < y0 + high - 1 && Math.floorMod(dx + dz, 3) == 0;
                    this.set(dx, y, dz, !wall ? Blocks.AIR.defaultBlockState() : window ? Blocks.GLASS.defaultBlockState()
                            : (y == y0 ? Blocks.SMOOTH_STONE : Blocks.CONCRETE.white()).defaultBlockState());
                }
                this.set(dx, y0 + high, dz, Blocks.CONCRETE.lightGray().defaultBlockState());
                this.set(dx, y0 + high + 1, dz, Blocks.AIR.defaultBlockState());
            }
        }
    }

    void doorway(int dx, int y, int dz) {
        this.fill(dx, y, dz, dx, y + 1, dz, Blocks.AIR);
    }

    // --- the laboratory shut ---------------------------------------------------------------------------------------------

    /** The corridor from the hub to the laboratory caved in, and past the rubble the blast door, shut from inside. */
    void seal() {
        int f = this.b + 1;
        BlockState bulkhead = dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get().defaultBlockState();
        this.fill(-1, f, -12, 1, f + SkyBase.HIGH - 1, -12, bulkhead);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -11; dz <= -10; dz++) {
                long r = Math.floorMod(Town.hash(this.h, dx, dz, 220), 100);
                int high = dz == -11 ? 2 + (int) (r % 2) : (int) (r % 2);
                for (int y = f; y < f + high; y++) {
                    this.set(dx, y, dz, (r < 40 ? Blocks.COBBLED_DEEPSLATE : r < 75 ? Blocks.CRACKED_STONE_BRICKS : Blocks.CONCRETE.gray()).defaultBlockState());
                }
                if (high < 3 && Math.floorMod(r, 3) == 0) {
                    this.set(dx, f + high, dz, Props.sheet(r, Direction.UP, Direction.Axis.X, false));
                }
            }
            this.set(dx, f + SkyBase.HIGH, -11, Blocks.AIR.defaultBlockState());            // where the roof came down
        }
        this.set(0, f + SkyBase.HIGH + 1, -11, Blocks.AIR.defaultBlockState());
    }

    // --- up: the quarters' stair, the landing, the director's room, the mast ---------------------------------------------

    /** The stair up the quarters' north wall, through their roof to the landing. */
    void stair() {
        int f = this.b + 1;
        for (int k = 0; k <= 5; k++) {
            this.set(14 + k, f + k, -7, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
            this.fill(14 + k, f + k + 1, -7, 14 + k, f + k + 3, -7, Blocks.AIR);
        }
    }

    /** The landing over the quarters: where the stair comes up, the doors out of it, the dead about it. */
    void landing() {
        int y = this.b + SkyBase.HIGH + 2;                                  // its floor's top: the quarters' roof is under it
        this.room(14, -8, 21, 0, y, 4);
        this.fill(16, y - 1, -7, 18, y - 1, -7, Blocks.AIR);                // the stairwell, railed
        Set<BlockPos> rail = new HashSet<>();
        for (int dx = 15; dx <= 18; dx++) {
            rail.add(new BlockPos(dx, y, -6));
        }
        this.rail(rail);
        this.doorway(17, y, -8);
        this.doorway(17, y, 0);
        this.ladder(15, y, y + 4, -2, Direction.EAST);
        this.set(20, y, -2, dev.nookworks.registry.ModBlocks.CRATE.get());
        this.set(20, y + 1, -2, dev.nookworks.registry.ModBlocks.CRATE.get());
        this.container(20, y, -1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.WEST), "supplies");
        this.lantern(18, y + 3, -4);
        this.marker(19, y, -4, 2);
    }

    /** The director's room, off the landing: a bed, a desk, the director - a key in hand - and their papers. */
    void director() {
        int y = this.b + SkyBase.HIGH + 2;
        this.room(14, 0, 21, 8, y, 4);
        this.doorway(17, y, 0);
        BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        this.set(20, y, 6, bed.setValue(BedBlock.PART, BedPart.FOOT));
        this.set(20, y, 7, bed.setValue(BedBlock.PART, BedPart.HEAD));
        for (int dx = 15; dx <= 17; dx++) {
            this.set(dx, y, 7, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        this.set(16, y, 6, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        this.pile(15, y + 1, 7, 2, () -> Notes.book(Notes.BASE.get(this.random.nextInt(Notes.BASE.size()))));
        this.set(17, y + 1, 7, Blocks.POTTED_DEAD_BUSH);
        this.container(15, y, 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "stash");
        this.lantern(17, y + 3, 4);
        SkyBase.holder(this.level, this.box, this.at(18, y, 4), this.h ^ 0x4E2);
    }

    /** The radio mast on the landing's roof, up the ladder: the signal lamp they kept, a diary, the drop all round. */
    void mast() {
        int y = this.b + SkyBase.HIGH + 7;                                  // the landing's roof's top
        this.set(15, y - 1, -2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        Set<BlockPos> rail = new HashSet<>();
        for (int dx = 14; dx <= 21; dx++) {
            rail.add(new BlockPos(dx, y, -8));
            rail.add(new BlockPos(dx, y, 8));
        }
        for (int dz = -8; dz <= 8; dz++) {
            rail.add(new BlockPos(14, y, dz));
            rail.add(new BlockPos(21, y, dz));
        }
        this.rail(rail);
        for (int k = 0; k <= 6; k++) {
            this.set(20, y + k, -6, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, k % 3 == 0)
                    .setValue(IronBarsBlock.SOUTH, k % 3 == 0));
        }
        this.set(20, y + 7, -6, Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState());
        this.set(16, y, -6, Blocks.REDSTONE_LAMP);
        this.set(16, y + 1, -6, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        BlockPos lectern = this.at(18, y, -4);
        if (this.box.isInside(lectern)) {
            this.level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH)
                    .setValue(LecternBlock.HAS_BOOK, true), 2);
            if (this.level.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity stand) {
                stand.setBook(Diaries.book(RandomSource.create(this.h ^ 0x3A57L)));
            }
        }
    }

    // --- out: the catwalk, the tank, the bridge, the gallery -------------------------------------------------------------

    /**
     * The catwalk north from the landing, two wide on legs over the deck; at its end east to the tank, and west a bridge
     * over the drop and steps up to the laboratory's roof.
     */
    void catwalk() {
        int y = this.b + SkyBase.HIGH + 1;                                  // its floor: the roofs' level
        BlockState grate = Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState();
        this.fill(16, y, -12, 17, y, -9, grate);
        this.fill(9, y, -12, 18, y, -11, grate);
        Set<BlockPos> rail = new HashSet<>();
        for (int dz = -12; dz <= -9; dz++) {
            rail.add(new BlockPos(15, y + 1, dz));
            rail.add(new BlockPos(18, y + 1, dz));
        }
        for (int dx = 13; dx <= 18; dx++) {
            rail.add(new BlockPos(dx, y + 1, -13));
        }
        for (int dx = 13; dx <= 15; dx++) {
            rail.add(new BlockPos(dx, y + 1, -10));
        }
        rail.remove(new BlockPos(15, y + 1, -11));
        rail.remove(new BlockPos(15, y + 1, -12));
        rail.remove(new BlockPos(18, y + 1, -11));
        rail.remove(new BlockPos(18, y + 1, -12));
        this.rail(rail);
        for (int k = 1; k <= 3; k++) {                                     // the steps up to the laboratory's roof
            int dx = 13 - k;
            this.fill(dx, y, -12, dx, y + k - 1, -11, grate);
            this.fill(dx, y + k, -12, dx, y + k, -11, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            this.fill(dx, y + k + 1, -12, dx, y + k + 3, -11, Blocks.AIR);
        }
        this.fill(9, y + 3, -13, 9, y + 3, -11, grate);                    // the landing at the top, onto the roof's corner
        this.fill(9, y + 4, -13, 9, y + 6, -11, Blocks.AIR);
        for (int[] leg : new int[][] {{16, -12}, {17, -9}, {13, -12}}) {   // its legs, down to the deck
            this.fill(leg[0], this.b + 1, leg[1], leg[0], y - 1, leg[1], Blocks.IRON_BARS);
        }
        this.marker(16, y + 1, -11, 2);
    }

    /** The water tank at the catwalk's east end: a dead end, what someone hid in it, and who came for it. */
    void tank() {
        int y = this.b + SkyBase.HIGH + 1;
        BlockState copper = Blocks.CUT_COPPER.waxed().weathered().defaultBlockState();
        for (int dx = 19; dx <= 21; dx++) {
            for (int dz = -14; dz <= -10; dz++) {
                boolean wall = dx != 20 || dz == -14 || dz == -10;
                this.set(dx, y, dz, copper);
                for (int k = 1; k <= 4; k++) {
                    this.set(dx, y + k, dz, wall ? copper : Blocks.AIR.defaultBlockState());
                }
                this.set(dx, y + 5, dz, Blocks.CUT_COPPER_SLAB.waxed().weathered().defaultBlockState());
            }
        }
        for (int[] leg : new int[][] {{19, -14}, {21, -14}, {19, -10}, {21, -10}}) {
            this.fill(leg[0], this.b + 1, leg[1], leg[0], y - 1, leg[1], Blocks.IRON_BARS);
        }
        this.doorway(19, y + 1, -12);
        this.container(20, y + 1, -13, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), "stash");
        this.container(20, y + 1, -11, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), "supplies");
        this.set(20, y + 4, -12, Blocks.COBWEB);
        this.marker(20, y + 1, -12, 1);
    }

    /**
     * The way into the laboratory from its roof: a hatch at its east side, a ladder down to the gallery round its east wall,
     * and from the gallery's far end a ladder to the floor. A Sentry Golem keeps the roof.
     */
    void gallery() {
        int f = this.b + 1;
        int floor = this.b + 5;                                             // the gallery's floor
        int roof = this.b + SkyBase.LAB_HIGH + 1;
        this.fill(5, floor, -21, 7, floor, -14, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
        Set<BlockPos> rail = new HashSet<>();
        for (int dz = -21; dz <= -14; dz++) {
            rail.add(new BlockPos(4, floor + 1, dz));
        }
        this.rail(rail);
        this.set(7, roof + 1, -17, Blocks.AIR.defaultBlockState());          // no panel over the hatch
        this.set(0, roof + 1, -18, Blocks.AIR.defaultBlockState());
        this.ladder(7, floor + 1, roof, -17, Direction.WEST);
        this.ladder(7, f, floor, -21, Direction.WEST);
        this.marker(6, floor + 1, -19, 1);
        SkyBase.sentry(this.level, this.box, this.c.getX(), roof + 1, this.c.getZ() - 18);
    }

    // --- off the wings: the side rooms -----------------------------------------------------------------------------------

    /** A side room on the deck between two wings: its own floor over the drop, walls, a roof; its door cut through the wing's wall. */
    void annex(int dx0, int dz0, int dx1, int dz1) {
        int f = this.b + 1;
        this.fill(dx0, this.b - 1, dz0, dx1, this.b - 1, dz1, Blocks.CONCRETE.gray());
        this.fill(dx0, this.b, dz0, dx1, this.b, dz1, Blocks.SMOOTH_STONE);
        this.room(dx0, dz0, dx1, dz1, f, SkyBase.HIGH);
    }

    /** The infirmary, off the quarters to the south: beds behind bars, what they treated it with, one who stayed on a bed. */
    void infirmary() {
        int f = this.b + 1;
        this.annex(10, 9, 16, 15);
        this.doorway(14, f, 8);
        this.doorway(14, f, 9);
        for (int dx : new int[] {11, 13}) {
            BlockState bed = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
            this.set(dx, f, 13, bed.setValue(BedBlock.PART, BedPart.FOOT));
            this.set(dx, f, 14, bed.setValue(BedBlock.PART, BedPart.HEAD));
        }
        this.fill(12, f, 13, 12, f + 1, 14, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true));
        Dress.brewingStand(this.level, this.box, this.at(15, f, 12), this.h ^ 0x1F1);
        this.set(15, f, 11, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 1));
        this.container(15, f, 14, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST), "medical");
        this.pile(11, f, 10, 1, () -> Notes.book(Notes.BASE.get(this.random.nextInt(Notes.BASE.size()))));
        BlockPos body = this.at(13, f, 11);
        if (this.box.isInside(body)) {
            Markers.remains(this.level, body, Direction.SOUTH, RandomSource.create(this.h ^ 0x1F2));
        }
        this.lantern(13, f + SkyBase.HIGH - 1, 12);
        this.marker(12, f, 11, 2);
    }

    /** The washroom, off the stores to the south: showers on a grate, cauldrons, the towels. */
    void washroom() {
        int f = this.b + 1;
        this.annex(-16, 9, -10, 15);
        this.doorway(-14, f, 8);
        this.doorway(-14, f, 9);
        for (int dx = -15; dx <= -12; dx++) {
            this.set(dx, this.b, 14, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
            this.set(dx, f + SkyBase.HIGH - 1, 14, Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.DOWN));
        }
        for (int dz = 10; dz <= 12; dz++) {
            this.set(-11, f, dz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 1 + Math.floorMod(dz, 2)));
        }
        this.set(-15, f, 10, Blocks.WOOL.white());
        this.set(-15, f, 11, Blocks.CARPET.white());
        this.container(-15, f, 12, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST), "supplies");
        this.lantern(-13, f + SkyBase.HIGH - 1, 12);
        this.marker(-13, f, 13, 2);
    }

    /** The cold store, a room walled off in the stores' corner: ice, the last of the food, and the cold one in it. */
    void coldStore() {
        int f = this.b + 1;
        for (int y = f; y < f + SkyBase.HIGH; y++) {
            for (int dz = 4; dz <= 7; dz++) {
                this.set(-18, y, dz, Blocks.CONCRETE.white());
            }
            for (int dx = -21; dx <= -18; dx++) {
                this.set(dx, y, 4, Blocks.CONCRETE.white());
            }
            this.fill(-21, y, 5, -19, y, 7, Blocks.AIR);
        }
        this.doorway(-20, f, 4);
        this.fill(-21, this.b, 5, -19, this.b, 7, Blocks.PACKED_ICE);
        this.set(-21, f, 5, Blocks.BLUE_ICE);
        this.set(-21, f, 7, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        this.container(-21, f, 6, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), "food");
        this.container(-19, f, 7, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH), "food");
        this.set(-20, f, 7, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 2));
        this.marker(-20, f, 6, 1);
    }
}
