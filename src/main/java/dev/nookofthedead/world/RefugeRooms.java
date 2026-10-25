package dev.nookofthedead.world;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The refuge as the last of them lived in it (the user, 2026-10-23: "避難室内の内装ももっと凝っていいし、広くていい。機械も
 * もっとあってもいいかも。一流の研究者たちが引きこもって（というか出れなくなっただけだが）研究していた空間のはずなので、それなりの
 * 生活感と閉塞感があるといいな"; and its way in "分かりにくい"). Drawn by code over the deep part as shipped
 * ({@link Town#INSTITUTE_DEEP}), in its own blocks, after {@link Refuge#leftBehind} and {@link Refuge#workshop}:
 * <ul>
 * <li>The door from the Vandal's hall made one: a frame of iron in hazard stripes, two lamps over it, unlit, and the trail
 *     of those who came this way on the hall's floor. When the Vandal dies the lamps come on, a buzzer sounds and the plug
 *     of iron is drawn back - they had set it to open when "he" was asleep ({@link #onVandalDeath}).</li>
 * <li>Upstairs, the rooms lived in: a decontamination room before the corridor (lockers, suits on stands, showers, the
 *     barricade they took down stacked aside), the corridor narrowed by what they kept in it and pipes under its roof,
 *     the store made a kitchen with a table, the dormitory bunks in cubicles each with its own few things.</li>
 * <li>A lower floor, by a stair from the machine hall through an opening in its floor: the machine hall two storeys high
 *     with more of Nookworks' machines; a low corridor, and off it the records room, the chief's room, the isolation room
 *     (one bitten lived on there - the plague stands still in here - and never left), a cold room for the specimens, the
 *     washroom.</li>
 * </ul>
 * Its lower floor is sealed all round in bulkheads as the rest is ({@link Refuge#hull}), and counted in the refuge
 * ({@link Refuge#LOCAL}). A refuge made before it is given it the first time a player comes near who has never stood in
 * that one ({@link #retrofit}).
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class RefugeRooms {
    /** The lower floor: its floor, the lowest air over it, its ceiling (the template's own y; the upper floor is at 1). */
    public static final int FLOOR = -7;
    static final int LOW = -6;
    static final int CEILING = -1;
    /** The lower floor's shell: its outermost cells bulkheads, the rest deepslate tiles the rooms are cut from. */
    static final BoundingBox SHELL = new BoundingBox(6, -8, 38, 58, CEILING, 64);
    /** The opening in the machine hall's floor, down to the lower floor. */
    static final BoundingBox OPENING = new BoundingBox(34, CEILING, 51, 51, 1, 55);
    /** The lamps over the door from the hall: unlit till the Vandal dies. */
    static final List<BlockPos> LAMPS = List.of(new BlockPos(37, 6, 35), new BlockPos(38, 6, 35));
    /** The plug of iron in the passage from the hall. */
    static final BoundingBox PLUG = new BoundingBox(37, 2, 36, 38, 4, 36);

    private final WorldGenLevel level;
    private final BlockPos under;
    private final StructurePlaceSettings settings;
    private final BoundingBox box;
    private final long seed;
    /** Whether the door's plug is made bulkhead ({@link #door}). */
    private boolean seal = true;

    private RefugeRooms(WorldGenLevel level, BlockPos under, StructurePlaceSettings settings, BoundingBox box) {
        this.level = level;
        this.under = under;
        this.settings = settings;
        this.box = box;
        this.seed = level.getSeed() ^ under.asLong();
    }

    /** The refuge's rooms drawn, inside {@code box}, over the deep part placed at {@code under} as {@code settings} says. */
    public static void draw(WorldGenLevel level, BlockPos under, StructurePlaceSettings settings, BoundingBox box) {
        draw(level, under, settings, box, true);
    }

    /** As {@link #draw(WorldGenLevel, BlockPos, StructurePlaceSettings, BoundingBox)}, the plug made bulkhead only if {@code seal}. */
    static void draw(WorldGenLevel level, BlockPos under, StructurePlaceSettings settings, BoundingBox box, boolean seal) {
        RefugeRooms rooms = new RefugeRooms(level, under, settings, box);
        rooms.seal = seal;
        Diaries.at(Diaries.Place.REFUGE);
        try {
            rooms.door(false);
            rooms.lowerFloor();
            rooms.stair();
            rooms.upstairs();
            rooms.machines();
            rooms.records();
            rooms.chief();
            rooms.isolation();
            rooms.specimens();
            rooms.washroom();
        } finally {
            Diaries.at(Diaries.Place.TOWN);
        }
    }

    // --- where things go -------------------------------------------------------------------------------------------------

    BlockPos at(int x, int y, int z) {
        return StructureTemplate.calculateRelativePosition(this.settings, new BlockPos(x, y, z)).offset(this.under);
    }

    /** A state as drawn in the template's own frame, turned and mirrored as the deep part is. */
    BlockState turned(BlockState state) {
        return state.mirror(this.settings.getMirror()).rotate(this.settings.getRotation());
    }

    Direction turned(Direction d) {
        return this.settings.getRotation().rotate(this.settings.getMirror().mirror(d));
    }

    void set(int x, int y, int z, BlockState state) {
        BlockPos at = this.at(x, y, z);
        if (this.box.isInside(at)) {
            this.level.setBlock(at, this.turned(state), 2);
        }
    }

    void set(int x, int y, int z, Block block) {
        this.set(x, y, z, block.defaultBlockState());
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    this.set(x, y, z, state);
                }
            }
        }
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        this.fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
    }

    boolean air(int x, int y, int z) {
        BlockPos at = this.at(x, y, z);
        return this.box.isInside(at) && this.level.getBlockState(at).isAir();
    }

    long hash(int x, int y, int z) {
        return Town.hash(this.seed, x * 31L + y, z, 0x2EF6);
    }

    /** A container at ({@code x}, {@code y}, {@code z}) with {@code items} in it. */
    void container(int x, int y, int z, BlockState state, ItemStack... items) {
        BlockPos at = this.at(x, y, z);
        if (!this.box.isInside(at)) {
            return;
        }
        this.level.setBlock(at, this.turned(state), 2);
        if (this.level.getBlockEntity(at) instanceof BaseContainerBlockEntity container) {
            RandomSource random = RandomSource.create(this.hash(x, y, z));
            for (ItemStack item : items) {
                int slot = random.nextInt(container.getContainerSize());
                for (int i = 0; i < container.getContainerSize() && !container.getItem(slot).isEmpty(); i++) {
                    slot = (slot + 1) % container.getContainerSize();
                }
                container.setItem(slot, item);
            }
            container.setChanged();
        }
    }

    BlockState barrel(Direction facing) {
        return Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing);
    }

    BlockState stair(Block block, Direction facing, boolean top) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    BlockState slab(Block block, boolean top) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    void bed(int x, int y, int z, Direction head, Block bed) {
        BlockState state = bed.defaultBlockState().setValue(BedBlock.FACING, head);
        this.set(x, y, z, state.setValue(BedBlock.PART, BedPart.FOOT));
        this.set(x + head.getStepX(), y, z + head.getStepZ(), state.setValue(BedBlock.PART, BedPart.HEAD));
    }

    void lantern(int x, int y, int z) {
        this.set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
    }

    void bottles(int x, int y, int z) {
        long h = this.hash(x, y, z);
        BlockState state = dev.nookworks.registry.ModBlocks.PLACED_BOTTLES.get().defaultBlockState()
                .setValue(dev.nookworks.decor.PlacedBottlesBlock.BOTTLES, 1 + (int) Math.floorMod(h, 4L));
        this.set(x, y, z, state);
    }

    void lectern(int x, int y, int z, Direction facing, ItemStack book) {
        BlockPos at = this.at(x, y, z);
        if (!this.box.isInside(at)) {
            return;
        }
        this.level.setBlock(at, this.turned(Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing)
                .setValue(LecternBlock.HAS_BOOK, true)), 2);
        if (this.level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
            lectern.setBook(book);
        }
    }

    /** Iron bars along {@code cells}, each joined to its neighbours among them. */
    void rail(Set<BlockPos> cells) {
        for (BlockPos c : cells) {
            this.set(c.getX(), c.getY(), c.getZ(), Blocks.IRON_BARS.defaultBlockState()
                    .setValue(IronBarsBlock.NORTH, cells.contains(c.north())).setValue(IronBarsBlock.SOUTH, cells.contains(c.south()))
                    .setValue(IronBarsBlock.EAST, cells.contains(c.east())).setValue(IronBarsBlock.WEST, cells.contains(c.west())));
        }
    }

    /** A room cut out of the lower floor's shell: air from {@link #LOW} to {@code top}, its floor polished. */
    void room(int x0, int z0, int x1, int z1, int top) {
        this.fill(x0, LOW, z0, x1, top, z1, Blocks.AIR);
        this.fill(x0, FLOOR, z0, x1, FLOOR, z1, Blocks.POLISHED_DEEPSLATE);
    }

    /** A doorway one wide and two high in a wall of the lower floor. */
    void doorway(int x, int z) {
        this.fill(x, LOW, z, x, LOW + 1, z, Blocks.AIR);
        this.set(x, FLOOR, z, Blocks.POLISHED_DEEPSLATE);
    }

    // --- the way in ------------------------------------------------------------------------------------------------------

    /**
     * The door from the hall: in the hall's south wall about the passage (z 35), hazard stripes outside the bulkheads of its
     * frame, two copper lamps over it; on the floor before it the trail of those who came; {@code open}, the lamps lit.
     */
    void door(boolean open) {
        for (int y = 1; y <= 7; y++) {
            for (int x = 34; x <= 41; x++) {
                boolean side = x <= 35 || x >= 40;
                boolean lintel = y >= 6;
                if (y == 1 || !(side || lintel) || y == 6 && (x == 37 || x == 38)) {
                    continue;
                }
                this.set(x, y, 35, (Math.floorMod(x + y, 4) < 2 ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.black()).defaultBlockState());
            }
        }
        for (BlockPos lamp : LAMPS) {
            this.set(lamp.getX(), lamp.getY(), lamp.getZ(), Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState()
                    .setValue(BlockStateProperties.LIT, open));
        }
        if (open) {
            return;
        }
        // the plug made of bulkhead: nothing breaks it, and it is drawn back only when the Vandal dies (the user,
        // 2026-10-23: "壊せないようにしてほしい" - iron, a pickaxe got past the Vandal without it); one dug already stays dug
        for (int x = PLUG.minX(); x <= PLUG.maxX(); x++) {
            for (int y = PLUG.minY(); y <= PLUG.maxY(); y++) {
                BlockPos at = this.at(x, y, PLUG.minZ());
                if (this.seal && this.box.isInside(at) && this.level.getBlockState(at).is(Blocks.IRON_BLOCK)) {
                    this.level.setBlock(at, dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get().defaultBlockState(), 2);
                }
            }
        }
        // the threshold, worn; and the trail to it across the hall, from where they came in
        for (int x = 36; x <= 39; x++) {
            this.set(x, 1, 34, Blocks.POLISHED_BLACKSTONE);
        }
        for (int z = 14; z <= 33; z++) {
            for (int x = 36; x <= 39; x++) {
                long h = this.hash(x, 1, z);
                if (Math.floorMod(h, 100) < (x == 37 || x == 38 ? 45 : 12)) {
                    BlockPos floor = this.at(x, 1, z);
                    if (this.box.isInside(floor) && this.level.getBlockState(floor.above()).isAir()
                            && !this.level.getBlockState(floor).isAir()) {
                        this.set(x, 1, z, Math.floorMod(h >> 8, 3) == 0 ? Blocks.DYED_TERRACOTTA.black() : Blocks.DYED_TERRACOTTA.brown());
                    }
                }
            }
        }
    }

    // --- the lower floor -------------------------------------------------------------------------------------------------

    /** The lower floor's shell, sealed, and its rooms cut out of it. */
    void lowerFloor() {
        BlockState bulkhead = dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get().defaultBlockState();
        for (int x = SHELL.minX(); x <= SHELL.maxX(); x++) {
            for (int y = SHELL.minY(); y <= SHELL.maxY(); y++) {
                for (int z = SHELL.minZ(); z <= SHELL.maxZ(); z++) {
                    boolean outer = x == SHELL.minX() || x == SHELL.maxX() || z == SHELL.minZ() || z == SHELL.maxZ() || y == SHELL.minY();
                    this.set(x, y, z, outer ? bulkhead : Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        // between the floors, inside the shell, tiles: the bulkheads under the rooms upstairs are kept only at the rim
        this.fill(8, 0, 40, 56, 0, 62, Blocks.DEEPSLATE_TILES.defaultBlockState());
        this.room(30, 48, 55, 61, -2);                                  // the machine hall's lower storey
        this.room(9, 52, 29, 53, -4);                                   // the corridor, low
        this.room(8, 41, 18, 50, -2);                                   // the records room
        this.room(20, 43, 28, 50, -2);                                  // the chief's room
        this.room(8, 55, 13, 61, -3);                                   // the isolation room
        this.room(15, 55, 22, 61, -3);                                  // the cold room
        this.room(24, 55, 28, 61, -3);                                  // the washroom
        this.doorway(13, 51);
        this.doorway(24, 51);
        this.doorway(18, 54);
        this.doorway(26, 54);
        // the corridor's pipes and its lamps, under its low roof
        for (int x = 9; x <= 29; x++) {
            this.set(x, -4, 53, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        }
        for (int x = 11; x <= 29; x += 6) {
            this.lantern(x, -4, 52);
        }
    }

    /** The opening in the machine hall's floor, railed round, and the stair down it along its north edge. */
    void stair() {
        this.fill(OPENING.minX(), OPENING.minY(), OPENING.minZ(), OPENING.maxX(), OPENING.maxY(), OPENING.maxZ(), Blocks.AIR);
        for (int k = 0; k <= 6; k++) {
            int x = 34 + k;
            int y = -k;
            this.set(x, y, 51, this.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.WEST, false));
            this.fill(x, FLOOR + 1, 51, x, y - 1, 51, Blocks.DEEPSLATE_TILES);
        }
        Set<BlockPos> rail = new HashSet<>();
        for (int x = 34; x <= 52; x++) {
            rail.add(new BlockPos(x, 2, 50));
            rail.add(new BlockPos(x, 2, 56));
        }
        for (int z = 51; z <= 56; z++) {
            rail.add(new BlockPos(52, 2, z));
            if (z >= 52) {
                rail.add(new BlockPos(33, 2, z));
            }
        }
        rail.add(new BlockPos(33, 2, 56));
        this.rail(rail);
        // the stair's open side, railed below the floor too
        Set<BlockPos> low = new HashSet<>();
        for (int k = 1; k <= 6; k++) {
            low.add(new BlockPos(34 + k, 1 - k, 52));
        }
        this.rail(low);
    }

    // --- upstairs --------------------------------------------------------------------------------------------------------

    void upstairs() {
        // the decontamination room (the vestibule): lockers, suits on stands, two showers on a grate, a bench; the barricade
        // they took down from the passage stacked either side of it
        for (int z = 41; z <= 43; z++) {
            this.container(42, 2, z, this.barrel(Direction.WEST), new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.STRING, 3));
            this.set(42, 3, z, this.barrel(Direction.WEST));
        }
        for (int x = 39; x <= 40; x++) {
            for (int z = 41; z <= 42; z++) {
                this.set(x, 1, z, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
            }
            this.set(x, 6, 41, Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        }
        this.set(34, 2, 43, this.slab(Blocks.SPRUCE_SLAB, false));
        this.set(35, 2, 43, this.slab(Blocks.SPRUCE_SLAB, false));
        for (int[] p : new int[][] {{33, 2, 40}, {33, 3, 40}, {34, 2, 40}, {35, 2, 40}, {40, 2, 40}, {41, 2, 40}, {41, 3, 40}, {42, 2, 40}}) {
            this.set(p[0], p[1], p[2], Blocks.SPRUCE_PLANKS);
        }
        this.suit(33, 41, Direction.EAST, 0xD9B43A);
        this.suit(33, 42, Direction.EAST, 0xE0E0D8);
        // the corridor: pipes under its roof along one side, and what they kept in it along the other, the doors kept clear
        for (int x = 11; x <= 54; x++) {
            this.set(x, 5, 46, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        }
        for (int x : new int[] {11, 13, 23, 25, 27, 34, 41, 43, 46, 54}) {
            long h = this.hash(x, 2, 46);
            switch ((int) Math.floorMod(h, 4L)) {
                case 0 -> {
                    this.set(x, 2, 46, Props.bale(h));
                    this.set(x, 3, 46, Props.bale(h >> 4));
                }
                case 1 -> this.container(x, 2, 46, this.barrel(Direction.UP), new ItemStack(Items.GLASS_BOTTLE, 5));
                case 2 -> {
                    this.set(x, 2, 46, dev.nookworks.registry.ModBlocks.CRATE.get());
                    this.bottles(x, 3, 46);
                }
                default -> this.set(x, 2, 46, Blocks.DECORATED_POT);
            }
        }
        // the brewing room: the bottles they emptied, on the bench and about the floor
        for (int x : new int[] {13, 16, 19}) {
            this.bottles(x, 2, 42);
        }
        this.bottles(29, 2, 43);
        this.bottles(30, 2, 40);
        // the store made a kitchen: a table down its middle, stools at it, what was on it
        for (int x = 46; x <= 50; x++) {
            this.set(x, 2, 42, this.slab(Blocks.SPRUCE_SLAB, true));
        }
        for (int x : new int[] {46, 48, 50}) {
            this.set(x, 2, 43, this.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        }
        this.set(49, 2, 41, this.stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        this.bottles(47, 3, 42);
        this.set(49, 3, 42, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 2));
        this.set(50, 3, 42, Blocks.FLOWER_POT);
        this.container(52, 2, 43, this.barrel(Direction.UP), new ItemStack(Items.BOWL, 7), new ItemStack(Items.DRIED_KELP, 9));
        // the dormitory made cubicles: a partition between each two beds, a curtain over it, each one's few things
        for (int x : new int[] {12, 15, 22}) {
            for (int z : new int[] {48, 49, 51, 52}) {
                this.set(x, 2, z, Blocks.SPRUCE_PLANKS);
                this.set(x, 3, z, Blocks.WOOL.lightGray());
            }
        }
        for (int[] c : new int[][] {{13, 48}, {13, 51}, {10, 50}, {16, 49}, {20, 48}, {24, 52}}) {
            long h = this.hash(c[0], 2, c[1]);
            switch ((int) Math.floorMod(h, 4L)) {
                case 0 -> this.set(c[0], 2, c[1], Blocks.POTTED_DEAD_BUSH);
                case 1 -> this.set(c[0], 2, c[1], Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) (h >> 4 & 1)));
                case 2 -> Props.pile(this.level, this.box, this.at(c[0], 2, c[1]), 1, 90, RandomSource.create(h), () -> Diaries.book(RandomSource.create(h)));
                default -> this.container(c[0], 2, c[1], this.barrel(Direction.UP), new ItemStack(Items.PAPER, 6), new ItemStack(Items.CLOCK));
            }
        }
        // the way out: what they did not take, left by the door they did not have
        this.container(11, 2, 56, this.barrel(Direction.UP), new ItemStack(Items.LEATHER_CHESTPLATE), new ItemStack(Items.BREAD, 2));
        this.set(11, 3, 56, this.barrel(Direction.UP));
        this.set(12, 2, 56, dev.nookworks.registry.ModBlocks.CRATE.get());
    }

    /** One of their suits on a stand at ({@code x}, 2, {@code z}), facing {@code facing}: dyed leather, a helmet's glass. */
    void suit(int x, int z, Direction facing, int colour) {
        BlockPos at = this.at(x, 2, z);
        if (!this.box.isInside(at)) {
            return;
        }
        var stand = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (stand == null) {
            return;
        }
        stand.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, this.turned(facing).toYRot(), 0.0F);
        for (var piece : new net.minecraft.world.item.Item[] {Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS}) {
            ItemStack worn = new ItemStack(piece);
            worn.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(colour));
            stand.setItemSlot(piece == Items.LEATHER_CHESTPLATE ? EquipmentSlot.CHEST : piece == Items.LEATHER_LEGGINGS ? EquipmentSlot.LEGS
                    : EquipmentSlot.FEET, worn);
        }
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GLASS));
        this.level.addFreshEntity(stand);
    }

    // --- downstairs ------------------------------------------------------------------------------------------------------

    /** The machine hall's lower storey: two more rows of Nookworks' machines on their own lines, scrap, spares. */
    void machines() {
        Block[] north = {
                dev.nookworks.registry.ModBlocks.DIESEL_GENERATOR.get(), dev.nookworks.registry.ModBlocks.GENERATOR.get(),
                dev.nookworks.registry.ModBlocks.ELECTROLYTIC_CELL.get(), dev.nookworks.registry.ModBlocks.ELECTROLYTIC_CELL.get(),
                dev.nookworks.registry.ModBlocks.STIRRING_TANK.get(), dev.nookworks.registry.ModBlocks.STIRRING_TANK.get(),
                dev.nookworks.registry.ModBlocks.DISTILLATION_COLUMN.get(), dev.nookworks.registry.ModBlocks.CONDENSER.get(),
                dev.nookworks.registry.ModBlocks.SIFTER.get(), dev.nookworks.registry.ModBlocks.STONE_MAKER.get(),
                dev.nookworks.registry.ModBlocks.ELECTRIC_FURNACE.get(), dev.nookworks.registry.ModBlocks.CRUSHER.get(),
                dev.nookworks.registry.ModBlocks.GENERATOR.get()};
        Block[] south = {
                dev.nookworks.registry.ModBlocks.GENERATOR.get(), dev.nookworks.registry.ModBlocks.IRON_STORAGE.get(),
                dev.nookworks.registry.ModBlocks.IRON_STORAGE.get(), dev.nookworks.registry.ModBlocks.ITEM_SORTER.get(),
                dev.nookworks.registry.ModBlocks.COPPER_TANK.get(), dev.nookworks.registry.ModBlocks.SIGHT_GLASS.get(),
                dev.nookworks.registry.ModBlocks.COPPER_TANK.get(), dev.nookworks.registry.ModBlocks.WATER_PUMP.get(),
                dev.nookworks.registry.ModBlocks.REPAIR_BOX.get(), dev.nookworks.registry.ModBlocks.PRINTING_PRESS.get(),
                dev.nookworks.registry.ModBlocks.ELECTRIC_FURNACE.get(), dev.nookworks.registry.ModBlocks.DIESEL_GENERATOR.get()};
        this.row(north, 36, 48, Direction.SOUTH, 49);
        this.row(south, 37, 61, Direction.NORTH, 60);
        // spares: chassis stacked, sheets of patched metal, crates of parts; the stair's foot kept clear
        for (int[] p : new int[][] {{31, LOW, 49}, {31, LOW + 1, 49}, {31, LOW, 50}, {32, LOW, 49}}) {
            this.set(p[0], p[1], p[2], dev.nookworks.registry.ModBlocks.MACHINE_CHASSIS.get());
        }
        for (int[] p : new int[][] {{54, LOW, 52}, {54, LOW + 1, 52}, {54, LOW, 53}, {54, LOW, 56}, {54, LOW + 1, 56}}) {
            this.set(p[0], p[1], p[2], dev.nookworks.registry.ModRecycling.PATCHED_SHEET_METAL.get());
        }
        this.container(31, LOW, 58, this.barrel(Direction.UP), new ItemStack(Items.COAL, 32), new ItemStack(Items.REDSTONE, 12));
        this.container(31, LOW, 59, this.barrel(Direction.UP), new ItemStack(Items.COPPER_INGOT, 9), new ItemStack(Items.IRON_NUGGET, 20));
        this.set(31, LOW + 1, 58, dev.nookworks.registry.ModBlocks.CRATE.get());
        // pillars holding up the floor above; the pipes along the north wall; a bench under the opening, what was on it
        for (int[] p : new int[][] {{33, 57}, {39, 57}, {45, 57}, {51, 57}, {53, 50}, {53, 54}}) {
            this.fill(p[0], LOW, p[1], p[0], -2, p[1], Blocks.POLISHED_DEEPSLATE);
        }
        for (int x = 31; x <= 54; x++) {
            this.set(x, -3, 48, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            this.set(x, -2, 61, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        }
        for (int x = 43; x <= 48; x++) {
            for (int z = 53; z <= 54; z++) {
                this.set(x, LOW, z, this.slab(Blocks.SMOOTH_STONE_SLAB, true));
            }
        }
        this.bottles(43, LOW + 1, 53);
        this.bottles(46, LOW + 1, 54);
        this.set(45, LOW + 1, 53, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 4));
        this.set(48, LOW + 1, 54, dev.nookworks.registry.ModBlocks.PLACED_BOOKS.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        Dress.brewingStand(this.level, this.box, this.at(47, LOW + 1, 53), this.hash(47, 0, 53));
        for (int z = 50; z <= 52; z++) {
            this.set(55, LOW, z, dev.nookworks.registry.ModBlocks.CRATE.get());
            if (z != 51) {
                this.set(55, LOW + 1, z, dev.nookworks.registry.ModBlocks.CRATE.get());
            }
        }
        this.container(55, LOW, 55, this.barrel(Direction.WEST), new ItemStack(Items.GLASS_BOTTLE, 12), new ItemStack(Items.NETHER_WART, 4));
        this.set(55, LOW + 1, 55, this.barrel(Direction.UP));
        for (int x = 32; x <= 54; x += 6) {
            for (int z : new int[] {49, 59}) {
                this.lantern(x, -2, z);
            }
        }
        for (int z : new int[] {50, 57}) {
            this.lantern(44, -2, z);
        }
    }

    /** A row of machines along {@code z} from {@code x0}, facing {@code facing}, a line of Energy Cable before them along {@code cableZ}. */
    void row(Block[] machines, int x0, int z, Direction facing, int cableZ) {
        List<BlockPos> cables = new java.util.ArrayList<>();
        for (int i = 0; i < machines.length; i++) {
            BlockState state = machines[i].defaultBlockState();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            }
            this.set(x0 + i, LOW, z, state);
            BlockPos cable = this.at(x0 + i, LOW, cableZ);
            if (this.box.isInside(cable)) {
                this.level.setBlock(cable, dev.nookworks.registry.ModBlocks.ENERGY_CABLE.get().defaultBlockState(), 2);
                cables.add(cable);
            }
        }
        java.util.List<Block> kinds = java.util.Arrays.asList(machines);
        for (BlockPos cable : cables) {                                     // joined to each other and to the machines, as set by hand
            BlockState state = this.level.getBlockState(cable);
            for (Direction d : Direction.values()) {
                Block next = this.level.getBlockState(cable.relative(d)).getBlock();
                boolean join = next instanceof dev.nookworks.block.EnergyCableBlock || kinds.contains(next);
                state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d), join);
            }
            this.level.setBlock(cable, state, 2);
        }
    }

    /** The records room: shelves of their work to the ceiling, the filing, a desk, the diaries of those who kept them. */
    void records() {
        for (int x = 8; x <= 18; x++) {
            for (int y = LOW; y <= -3; y++) {
                this.set(x, y, 41, Math.floorMod(this.hash(x, y, 41), 5) == 0 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF);
            }
        }
        for (int z = 43; z <= 49; z += 2) {
            for (int y = LOW; y <= LOW + 1; y++) {
                this.container(8, y, z, this.barrel(Direction.EAST), new ItemStack(Items.PAPER, 4 + (int) Math.floorMod(this.hash(8, y, z), 12L)));
            }
        }
        for (int x = 11; x <= 15; x++) {
            this.set(x, LOW, 45, this.slab(Blocks.DARK_OAK_SLAB, true));
            this.set(x, LOW, 47, this.slab(Blocks.DARK_OAK_SLAB, true));
        }
        for (int x : new int[] {12, 14}) {
            this.set(x, LOW, 44, this.stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
            this.set(x, LOW, 48, this.stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        }
        this.set(11, LOW + 1, 45, dev.nookworks.registry.ModBlocks.PLACED_BOOKS.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        this.set(15, LOW + 1, 47, dev.nookworks.registry.ModBlocks.PLACED_BOOKS.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        this.set(13, LOW + 1, 45, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3));
        this.bottles(14, LOW + 1, 47);
        this.lectern(17, LOW, 45, Direction.WEST, Diaries.book(RandomSource.create(this.hash(17, LOW, 45))));
        this.lectern(17, LOW, 48, Direction.WEST, Diaries.book(RandomSource.create(this.hash(17, LOW, 48))));
        this.set(17, LOW, 43, Blocks.CARTOGRAPHY_TABLE);
        for (int x : new int[] {10, 16}) {
            for (int z : new int[] {44, 48}) {
                this.lantern(x, -2, z);
            }
        }
    }

    /** The chief's room: a bed, a desk under the lamp, the one who held them together's own things. */
    void chief() {
        this.bed(21, LOW, 49, Direction.NORTH, Blocks.BED.red());
        this.set(21, LOW, 47, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        this.set(21, LOW + 1, 47, Blocks.POTTED_DEAD_BUSH);
        for (int x = 24; x <= 27; x++) {
            this.set(x, LOW, 44, this.slab(Blocks.DARK_OAK_SLAB, true));
        }
        this.set(25, LOW, 45, this.stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        this.lectern(28, LOW, 47, Direction.WEST, Diaries.book(RandomSource.create(this.hash(28, LOW, 47))));
        this.set(24, LOW + 1, 44, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1).setValue(BlockStateProperties.LIT, true));
        this.bottles(27, LOW + 1, 44);
        Props.pile(this.level, this.box, this.at(26, LOW + 1, 44), 2, 0, RandomSource.create(this.hash(26, 0, 44)),
                () -> Diaries.book(RandomSource.create(this.hash(26, 1, 44))));
        this.container(28, LOW, 49, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST),
                new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.COMPASS), new ItemStack(Items.GOLD_NUGGET, 6));
        this.lantern(24, -2, 46);
    }

    /**
     * The isolation room: one of them bitten, who lived on in here where the plague stands still and never could leave it
     * (out of the refuge it would run on: the user's refuge, 2026-10-19). Shut from outside, a lever by its door; inside
     * a bed, the days counted on the wall, what is left of them.
     */
    void isolation() {
        this.set(9, LOW + 1, 54, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true));
        this.set(10, LOW + 1, 54, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.WEST, true));
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        this.set(11, LOW, 54, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        this.set(11, LOW + 1, 54, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        this.set(11, FLOOR, 54, Blocks.POLISHED_DEEPSLATE);
        this.set(12, LOW + 1, 53, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.NORTH));
        this.bed(9, LOW, 61, Direction.NORTH, Blocks.BED.white());
        for (int z = 56; z <= 61; z++) {                                    // the days, counted on the east wall
            for (int y = LOW + 1; y <= LOW + 2; y++) {
                if (Math.floorMod(z, 3) != 2) {
                    this.set(14, y, z, Blocks.CRACKED_DEEPSLATE_TILES);
                }
            }
        }
        this.bottles(12, LOW, 61);
        this.set(13, LOW, 61, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        this.lantern(11, -3, 58);
        BlockPos body = this.at(12, LOW, 57);
        if (this.box.isInside(body)) {
            Markers.remains(this.level, body, this.turned(Direction.WEST), RandomSource.create(this.hash(12, LOW, 57)));
        }
    }

    /** The cold room: the specimens kept on ice - jars on shelves, cauldrons of powder snow, the drain frozen. */
    void specimens() {
        this.fill(15, FLOOR, 55, 22, FLOOR, 61, Blocks.PACKED_ICE);
        this.set(18, FLOOR, 54, Blocks.POLISHED_DEEPSLATE);
        for (int z = 56; z <= 61; z++) {
            this.set(15, LOW, z, Blocks.BLUE_ICE);
            this.set(22, LOW, z, Blocks.BLUE_ICE);
            // shelves: upper slabs, so what stands on them stands on them (the user, 2026-10-24: the jars floated)
            this.set(15, LOW + 1, z, this.slab(Blocks.SMOOTH_STONE_SLAB, true));
            this.set(22, LOW + 1, z, this.slab(Blocks.SMOOTH_STONE_SLAB, true));
            if (Math.floorMod(z, 2) == 0) {
                this.set(15, LOW + 2, z, Blocks.DECORATED_POT);
                this.bottles(22, LOW + 2, z);
            }
        }
        this.set(17, LOW, 61, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        this.set(20, LOW, 61, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
        this.container(18, LOW, 61, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH),
                new ItemStack(Items.ROTTEN_FLESH, 16), new ItemStack(Items.FERMENTED_SPIDER_EYE, 2), new ItemStack(Items.GLASS_BOTTLE, 6));
        this.set(19, LOW, 61, Blocks.BREWING_STAND);
        this.lantern(18, -3, 58);
        this.set(16, LOW, 58, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 1));
        this.set(21, LOW, 57, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 2));
    }

    /** The washroom: cauldrons, showers on a grate behind iron, a towel's worth of wool. */
    void washroom() {
        for (int z = 56; z <= 60; z += 2) {
            this.set(24, LOW, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 1 + Math.floorMod(z, 3)));
        }
        for (int x = 26; x <= 28; x++) {
            this.set(x, FLOOR, 59, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
            this.set(x, FLOOR, 60, Blocks.COPPER_GRATE.waxed().weathered().defaultBlockState());
            this.set(x, -3, 60, Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN));
        }
        this.set(27, LOW, 57, Blocks.WOOL.white());
        this.lantern(26, -3, 57);
    }

    // --- the door opened -------------------------------------------------------------------------------------------------

    /**
     * The door of the refuge of the institute at {@code near}'s block opened, its Vandal dead ({@link VandalKeeper}): the
     * lamps come on over it, a buzzer goes, and the plug is drawn back - they had it set to open once "he" was asleep, for
     * whoever came after (their note: "広間の彼を眠らせてくれたのだろう"). Whether there was one.
     */
    public static boolean open(ServerLevel level, BlockPos near) {
        int bx = Math.floorDiv(near.getX(), Town.BLOCK);
        int bz = Math.floorDiv(near.getZ(), Town.BLOCK);
        Town.Lot lot = Town.lots(level.getSeed(), bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
        if (lot == null || deep == null) {
            return false;
        }
        RefugeRooms rooms = new RefugeRooms(level, Town.deepOrigin(lot, deep),
                new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror()), BoundingBox.infinite());
        rooms.door(true);
        for (int x = PLUG.minX(); x <= PLUG.maxX(); x++) {
            for (int y = PLUG.minY(); y <= PLUG.maxY(); y++) {
                BlockPos at = rooms.at(x, y, PLUG.minZ());
                if (level.getBlockState(at).is(Blocks.IRON_BLOCK) || level.getBlockState(at).is(dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get())) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        BlockPos door = rooms.at(37, 3, 35);
        // the camera to it for those near, as it opens (Cuts, 2026-10-24): from out in the hall, a little up
        Vec3 middle = Vec3.atCenterOf(door).add(Vec3.atCenterOf(rooms.at(38, 3, 35)).subtract(Vec3.atCenterOf(door)).scale(0.5));
        Vec3 view = Vec3.atCenterOf(rooms.at(37, 5, 26)).add(middle.subtract(Vec3.atCenterOf(door)));
        dev.nookofthedead.ending.Cuts.vandal(level, Vec3.atCenterOf(door), middle, view);
        level.playSound(null, door, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 3.0F, 0.5F);
        level.playSound(null, door, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 2.0F, 0.6F);
        level.playSound(null, door, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2.0F, 0.7F);
        NookOfTheDead.LOGGER.info("The refuge's door at {} opened (its Vandal dead)", door);
        return true;
    }

    /** How near the door a player is looked at for {@link #watch}. */
    static final int WATCH = 32;
    /** How many looks in a row with no Vandal about before one is put back (five seconds apart). */
    static final int EMPTY_LOOKS = 3;
    /** The looks in a row each shut door has had with no Vandal about (the server's thread only). */
    private static final java.util.Map<Long, Integer> EMPTY = new java.util.HashMap<>();

    /**
     * Never shut for good, and never open for less than the Vandal's death (the user, 2026-10-23): a door still shut and a
     * player near it - its Vandal written down dead, it opens (one that died while the door was out of reach); none of its
     * Vandals about, {@link #EMPTY_LOOKS} looks in a row with the deep part's entities loaded (so one not yet loaded is not
     * taken for none), and a Vandal is put back in the hall ({@link VandalKeeper#putBack}).
     */
    static void watch(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            return;
        }
        int bx = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        if (Refuge.box(level, bx, bz) == null) {
            return;
        }
        Town.Lot lot = Town.lots(level.getSeed(), bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
        if (lot == null || deep == null) {
            return;
        }
        BlockPos under = Town.deepOrigin(lot, deep);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        RefugeRooms rooms = new RefugeRooms(level, under, settings, BoundingBox.infinite());
        BlockPos door = rooms.at(37, 3, 35);
        BlockPos plug = rooms.at(PLUG.minX(), PLUG.minY(), PLUG.minZ());
        if (player.blockPosition().distSqr(door) > WATCH * WATCH || !level.isLoaded(plug)
                || !level.getBlockState(plug).is(dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get())) {
            return;
        }
        String key = VandalKeeper.key(level, bx, bz);
        if (VandalKeeper.dead(level, key)) {
            open(level, door);
            return;
        }
        BoundingBox whole = deep.getBoundingBox(settings, under).inflatedBy(32);
        for (int cx = whole.minX() >> 4; cx <= whole.maxX() >> 4; cx++) {
            for (int cz = whole.minZ() >> 4; cz <= whole.maxZ() >> 4; cz++) {
                if (level.hasChunk(cx, cz) && !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(cx, cz))) {
                    return;                                             // not all there yet: look again later
                }
            }
        }
        long look = door.asLong();
        if (VandalKeeper.about(level, key)) {
            EMPTY.remove(look);
            return;
        }
        int looks = EMPTY.merge(look, 1, Integer::sum);
        if (looks >= EMPTY_LOOKS) {
            EMPTY.remove(look);
            VandalKeeper.putBack(level, key, deep, under, settings);
        }
    }

    /** Whether the refuge at {@code under} has its rooms (its door's lamps up). */
    static boolean drawn(ServerLevel level, BlockPos under, StructurePlaceSettings settings) {
        BlockPos lamp = StructureTemplate.calculateRelativePosition(settings, LAMPS.get(0)).offset(under);
        return level.getBlockState(lamp).is(Blocks.COPPER_BULB.waxed().unaffected());
    }

    // --- refuges made before ---------------------------------------------------------------------------------------------

    /** How near a player comes before a refuge made before its rooms is given them. */
    static final int FROM = 48;

    /**
     * The cold room's shelves in a refuge drawn before they were upper slabs (0.4 before 2026-10-24): made so, once, the
     * jars and bottles on them no longer floating half a block over them. Only lower smooth stone slabs are turned.
     */
    private static void shelves(ServerLevel level, Refuge.Retrofitted done, String number, int bx, int bz) {
        String key = "shelves:" + number;
        if (done.refuges.contains(key)) {
            return;
        }
        done.refuges.add(key);
        done.setDirty();
        Town.Lot lot = Town.lots(level.getSeed(), bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
        if (lot == null || deep == null) {
            return;
        }
        RefugeRooms rooms = new RefugeRooms(level, Town.deepOrigin(lot, deep),
                new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror()), BoundingBox.infinite());
        int turned = 0;
        for (int z = 56; z <= 61; z++) {
            for (int x : new int[] {15, 22}) {
                BlockPos at = rooms.at(x, LOW + 1, z);
                BlockState was = level.getBlockState(at);
                if (was.is(Blocks.SMOOTH_STONE_SLAB) && was.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                    level.setBlock(at, was.setValue(SlabBlock.TYPE, SlabType.TOP), 2);
                    turned++;
                }
            }
        }
        if (turned > 0) {
            NookOfTheDead.LOGGER.info("The refuge's cold room shelves at {} made upper slabs ({})", rooms.at(18, LOW, 58), turned);
        }
    }

    /**
     * A refuge made before these rooms (0.3) given them the first time a player comes within {@link #FROM} of it, its
     * chunks loaded - only one nobody has stood in yet ({@link Refuge.Retrofitted}: what a player found there is left as
     * they found it), once only. One dug into already has its door opened; its plug is made bulkhead only if its Vandal is
     * about (one killed before 0.3 left no word of its death).
     */
    static void retrofit(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            return;
        }
        int bx = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        BoundingBox refuge = Refuge.box(level, bx, bz);
        if (refuge == null || !refuge.inflatedBy(FROM).isInside(player.blockPosition())) {
            return;
        }
        Refuge.Retrofitted done = level.getServer().getDataStorage().computeIfAbsent(Refuge.Retrofitted.TYPE);
        String number = dev.nookworks.ifworld.IfWorld.get(level.getServer()).number() + ":" + bx + "," + bz;
        String key = "rooms:" + number;
        BoundingBox around = refuge.inflatedBy(12);
        if (!level.hasChunksAt(around.minX(), around.minY() - 10, around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
            return;
        }
        shelves(level, done, number, bx, bz);
        if (done.refuges.contains(key)) {
            return;
        }
        done.refuges.add(key);
        done.setDirty();
        Town.Lot lot = Town.lots(level.getSeed(), bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
        if (lot == null || deep == null || done.refuges.contains(number)) {
            return;                                                     // stood in already: left as it was found
        }
        BlockPos under = Town.deepOrigin(lot, deep);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        RefugeRooms probe = new RefugeRooms(level, under, settings, BoundingBox.infinite());
        if (level.getBlockState(probe.at(LAMPS.get(0).getX(), LAMPS.get(0).getY(), LAMPS.get(0).getZ())).is(Blocks.COPPER_BULB.waxed().unaffected())) {
            return;                                                     // made with them
        }
        boolean opened = level.getBlockState(probe.at(PLUG.minX(), PLUG.minY(), PLUG.minZ())).isAir();   // dug through already
        // the plug made bulkhead only where its Vandal is there to open it; one killed before 0.3 left no word of it, and
        // its plug stays iron, to dig as before (the user, 2026-10-23)
        BoundingBox whole = deep.getBoundingBox(settings, under).inflatedBy(32);
        for (int cx = whole.minX() >> 4; cx <= whole.maxX() >> 4; cx++) {
            for (int cz = whole.minZ() >> 4; cz <= whole.maxZ() >> 4; cz++) {
                if (level.hasChunk(cx, cz) && !level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(cx, cz))) {
                    done.refuges.remove(key);                           // not all there yet: again next time
                    done.setDirty();
                    return;
                }
            }
        }
        boolean vandal = VandalKeeper.about(level, VandalKeeper.key(level, bx, bz));
        draw(level, under, settings, BoundingBox.infinite(), vandal);
        if (opened) {
            probe.door(true);
        }
        NookOfTheDead.LOGGER.info("The refuge at {} given its rooms (made before them)", refuge.getCenter());
    }
}
