package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The covered market (the user, 2026-10-23, of a mall whose shops are drawn anew every time: "全部やっていい"):
 * a timber hall on an S lot, an aisle down its middle and eight booths along it, four a side, the same every time - and
 * what each booth sold drawn anew for each market from the town's trades ({@link Trade}): the baker's, the butcher's, the
 * greengrocer's, the apothecary's, the smith's, the tailor's, the chandler's, the bookseller's, the potter's, the cooper's.
 * Each its awning's colour over its counter, its goods on the counter and along its back, a barrel of what was left. The
 * roof fallen through in places, a counter knocked over here and there, the dead in the aisle. Drawn by code, not a
 * template ({@link Town#fill}), turned as its lot says.
 */
public final class Market {
    /** Its building id in the districts' lists: no template behind it. */
    public static final Identifier ID = NookOfTheDead.id("town/market");
    static final int SIZE = 32;
    /** The hall's walls (the lot's own x, z), its height, the aisle's sides. */
    static final int X0 = 2;
    static final int X1 = 29;
    static final int Z0 = 3;
    static final int Z1 = 29;
    static final int HIGH = 7;
    static final int AISLE0 = 13;
    static final int AISLE1 = 18;
    /** The booths along the aisle, by their first z (each four deep, a partition after). */
    static final int[] BOOTHS = {4, 10, 16, 22};

    /** What a booth sold. */
    enum Trade {
        BAKER(Blocks.WOOL.yellow()), BUTCHER(Blocks.WOOL.red()), GREENGROCER(Blocks.WOOL.lime()), APOTHECARY(Blocks.WOOL.green()),
        SMITH(Blocks.WOOL.gray()), TAILOR(Blocks.WOOL.magenta()), CHANDLER(Blocks.WOOL.orange()), BOOKSELLER(Blocks.WOOL.blue()),
        POTTER(Blocks.WOOL.brown()), COOPER(Blocks.WOOL.lightGray());

        final Block awning;

        Trade(Block awning) {
            this.awning = awning;
        }
    }

    /** The trades of {@code lot}'s eight booths, in order (west side first, from the front): eight of the ten, no two alike. */
    public static List<Trade> trades(Town.Lot lot) {
        List<Trade> all = new ArrayList<>(Arrays.asList(Trade.values()));
        java.util.Collections.shuffle(all, new java.util.Random(Town.hash(lot.seed(), 4, 4, 190)));
        return all.subList(0, 8);
    }

    private final WorldGenLevel level;
    private final Town.Lot lot;
    private final BoundingBox box;
    private final int y0;
    /** How the hall is turned: its front (north, as drawn) to the lot's front. Drawn unmirrored, so not the lot's rotation,
     * which is for a mirrored building on half the lots. */
    private final Rotation turn;

    private Market(WorldGenLevel level, Town.Lot lot, BoundingBox box) {
        this.level = level;
        this.lot = lot;
        this.box = box;
        this.y0 = lot.corner().getY() - 1;                                  // the floor: the ground's own level, as a template's
        this.turn = Arrays.stream(Rotation.values()).filter(r -> r.rotate(Direction.NORTH) == lot.front()).findFirst()
                .orElse(lot.rotation());
    }

    /** The market on {@code lot}, inside {@code box}. */
    static void draw(WorldGenLevel level, Town.Lot lot, BoundingBox box) {
        Market m = new Market(level, lot, box);
        m.hall();
        List<Trade> trades = trades(lot);
        for (int i = 0; i < 8; i++) {
            m.booth(i < 4, BOOTHS[i % 4], trades.get(i));
        }
        m.aisle();
    }

    // --- where things go: the lot's own x (to the street's right), z (away from the street), up from the floor ----------------

    BlockPos at(int x, int up, int z) {
        BlockPos c = this.lot.corner();
        int n = SIZE - 1;
        int[] xz = switch (this.turn) {
            case NONE -> new int[] {x, z};
            case CLOCKWISE_90 -> new int[] {n - z, x};
            case CLOCKWISE_180 -> new int[] {n - x, n - z};
            case COUNTERCLOCKWISE_90 -> new int[] {z, n - x};
        };
        return new BlockPos(c.getX() + xz[0], this.y0 + up, c.getZ() + xz[1]);
    }

    void set(int x, int up, int z, BlockState state) {
        BlockPos at = this.at(x, up, z);
        if (this.box.isInside(at)) {
            this.level.setBlock(at, state.rotate(this.turn), 2);
        }
    }

    void set(int x, int up, int z, Block block) {
        this.set(x, up, z, block.defaultBlockState());
    }

    long hash(int x, int up, int z) {
        return Town.hash(this.lot.seed(), x * 31L + up, z, 191);
    }

    void barrel(int x, int up, int z, String loot) {
        BlockPos at = this.at(x, up, z);
        if (this.box.isInside(at)) {
            Tower.container(this.level, this.box, at.getX(), at.getY(), at.getZ(),
                    Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), loot, this.hash(x, up, z));
        }
    }

    // --- the hall ---------------------------------------------------------------------------------------------------------

    /** Its floor, its walls of timber and plaster, its roof - fallen through in places - and its two ways in. */
    void hall() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                boolean inside = x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
                this.set(x, -2, z, Blocks.DIRT);
                this.set(x, -1, z, Blocks.DIRT);
                long r = Math.floorMod(this.hash(x, 0, z), 100);
                boolean aisle = x >= AISLE0 && x <= AISLE1 || z == Z1 - 1;
                BlockState floor = !inside ? (z < Z0 && x >= AISLE0 - 2 && x <= AISLE1 + 2
                        ? (r < 20 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState())
                        : aisle ? (r < 15 ? Blocks.CRACKED_STONE_BRICKS : r < 30 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState()
                        : Blocks.SPRUCE_PLANKS.defaultBlockState();
                this.set(x, 0, z, floor);
                for (int up = 1; up <= HIGH + 2; up++) {
                    this.set(x, up, z, Blocks.AIR);
                }
                if (!inside) {
                    continue;
                }
                boolean wall = x == X0 || x == X1 || z == Z0 || z == Z1;
                if (wall) {
                    boolean post = (x - X0) % 4 == 0 && (z == Z0 || z == Z1) || (z - Z0) % 4 == 0 && (x == X0 || x == X1);
                    boolean door = (z == Z0 || z == Z1) && x >= AISLE0 + 1 && x <= AISLE1 - 1;
                    for (int up = 1; up <= HIGH; up++) {
                        if (door && up <= 4) {
                            continue;
                        }
                        boolean window = !post && up >= 3 && up <= 5 && (x == X0 || x == X1) && (z - Z0) % 4 == 2;
                        this.set(x, up, z, post ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                                : window ? (Math.floorMod(this.hash(x, up, z), 3) == 0 ? Blocks.AIR : Blocks.SPRUCE_TRAPDOOR).defaultBlockState()
                                : up == 1 ? Blocks.COBBLESTONE.defaultBlockState()
                                : up == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.DYED_TERRACOTTA.white().defaultBlockState());
                    }
                }
                // the roof: planks on beams, holes where it fell in (the rafters left), a skylight over the aisle
                boolean fell = Town.noise(this.lot.seed(), this.at(x, 0, z).getX(), this.at(x, 0, z).getZ(), 5.0, 192) > 0.62;
                boolean beam = (z - Z0) % 6 == 0;
                if (!fell || beam || wall) {
                    this.set(x, HIGH + 1, z, (beam || wall ? Blocks.SPRUCE_PLANKS : Blocks.SPRUCE_SLAB).defaultBlockState());
                } else if (r < 50) {                                        // what fell lies under the hole
                    this.set(x, 1, z, r < 25 ? Blocks.SPRUCE_SLAB.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
    }

    /** One booth: on the west side ({@code west}) or the east, from {@code z0} four deep, what it sold. */
    void booth(boolean west, int z0, Trade trade) {
        int back = west ? X0 + 1 : X1 - 1;                                  // against the wall
        int front = west ? AISLE0 - 1 : AISLE1 + 1;                         // the counter, on the aisle
        int in = west ? 1 : -1;                                             // from the wall toward the aisle
        long h = this.hash(back, 0, z0);
        boolean knocked = Math.floorMod(h, 5) == 0;
        for (int z = z0; z < z0 + 4; z++) {                                 // the counter, its gap to come in by
            if (z == z0 + 1 && !knocked) {
                continue;
            }
            if (knocked && z == z0 + 2) {
                this.set(front, 1, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, west ? Direction.EAST : Direction.WEST)
                        .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
                continue;
            }
            this.set(front, 1, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        for (int z = z0; z < z0 + 4; z++) {                                 // the awning: its colour along the beam over the counter, torn here and there
            this.set(front, 4, z, (Math.floorMod(this.hash(front, 4, z), 5) == 0 ? Blocks.SPRUCE_PLANKS : trade.awning).defaultBlockState());
        }
        if (z0 + 4 <= Z1 - 2) {                                             // the partition after it
            for (int x = Math.min(back, front); x <= Math.max(back, front); x++) {
                for (int up = 1; up <= 3; up++) {
                    this.set(x, up, z0 + 4, (up == 3 ? Blocks.SPRUCE_FENCE : Blocks.SPRUCE_PLANKS).defaultBlockState());
                }
            }
        }
        this.goods(trade, back, in, front, z0, h);
    }

    /** What {@code trade} left: along the back wall, on the counter, a barrel of it. */
    void goods(Trade trade, int back, int in, int front, int z0, long h) {
        RandomSource random = RandomSource.create(h);
        int z1 = z0 + 3;
        switch (trade) {
            case BAKER -> {
                this.set(back, 1, z0, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
                this.set(back, 1, z0 + 1, Blocks.HAY_BLOCK);
                this.set(back, 2, z0 + 1, Blocks.HAY_BLOCK);
                this.barrel(back, 1, z1, "food");
                this.set(front, 2, z0 + 2, Blocks.DECORATED_POT);
            }
            case BUTCHER -> {
                this.set(back, 1, z0, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
                for (int z = z0 + 1; z <= z1 - 1; z++) {
                    this.set(back, 3, z, Blocks.IRON_CHAIN);
                }
                this.set(back + in, 1, z0 + 2, Blocks.BONE_BLOCK);
                this.barrel(back, 1, z1, "food");
            }
            case GREENGROCER -> {
                this.set(back, 1, z0, Blocks.PUMPKIN);
                this.set(back, 1, z0 + 1, Blocks.MELON);
                this.set(back, 1, z0 + 2, Blocks.COMPOSTER);
                this.set(front, 2, z0 + 2, Blocks.POTTED_FERN);
                this.set(front, 2, z0 + 3, Blocks.CARVED_PUMPKIN);
                this.barrel(back, 1, z1, "food");
            }
            case APOTHECARY -> {
                Dress.brewingStand(this.level, this.box, this.at(back, 1, z0), h ^ 0xA9L);
                this.set(back, 1, z0 + 1, Blocks.FLOWER_POT);
                this.set(back, 1, z0 + 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 1));
                this.set(front, 2, z0 + 2, dev.nookworks.registry.ModBlocks.PLACED_BOTTLES.get().defaultBlockState()
                        .setValue(dev.nookworks.decor.PlacedBottlesBlock.BOTTLES, 1 + random.nextInt(4)));
                this.barrel(back, 1, z1, "medical");
            }
            case SMITH -> {
                this.set(back, 1, z0, Blocks.DAMAGED_ANVIL);
                this.set(back, 1, z0 + 1, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,
                        net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
                this.set(back, 1, z0 + 2, Blocks.SMITHING_TABLE);
                this.set(back, 2, z0 + 2, Blocks.IRON_BARS);
                this.barrel(back, 1, z1, "tools");
            }
            case TAILOR -> {
                this.set(back, 1, z0, Blocks.LOOM.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
                this.set(back, 1, z0 + 1, Blocks.WOOL.white());
                this.set(back, 2, z0 + 1, Blocks.WOOL.lightGray());
                this.set(back, 1, z0 + 2, Blocks.WOOL.brown());
                this.set(front, 2, z0 + 2, Blocks.CARPET.red());
                this.barrel(back, 1, z1, "general");
            }
            case CHANDLER -> {
                this.set(back, 1, z0, Blocks.HONEYCOMB_BLOCK);
                for (int z = z0 + 2; z <= z0 + 3; z++) {
                    this.set(front, 2, z, Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + random.nextInt(4)));
                }
                this.set(back, 1, z0 + 1, Blocks.DYED_CANDLE.yellow().defaultBlockState().setValue(BlockStateProperties.CANDLES, 3));
                this.barrel(back, 1, z1, "supplies");
            }
            case BOOKSELLER -> {
                for (int z = z0; z <= z0 + 2; z++) {
                    this.set(back, 1, z, Blocks.BOOKSHELF);
                    this.set(back, 2, z, z == z0 + 1 ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF);
                }
                BlockPos lectern = this.at(back + in, 1, z1);
                if (this.box.isInside(lectern)) {
                    this.level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING,
                            this.turn.rotate(in > 0 ? Direction.WEST : Direction.EAST)).setValue(LecternBlock.HAS_BOOK, true), 2);
                    if (this.level.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity stand) {
                        stand.setBook(Diaries.book(RandomSource.create(h ^ 0xB00CL)));
                    }
                }
                this.set(front, 2, z0 + 2, dev.nookworks.registry.ModBlocks.PLACED_BOOKS.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
            }
            case POTTER -> {
                for (int z = z0; z <= z0 + 2; z++) {
                    this.set(back, 1, z, Blocks.DECORATED_POT);
                }
                this.set(back, 2, z0, Blocks.FLOWER_POT);
                this.set(back + in, 1, z0 + 1, Blocks.CLAY);
                this.set(front, 2, z0 + 3, Blocks.DECORATED_POT);
                this.barrel(back, 1, z1, "general");
            }
            case COOPER -> {
                for (int z = z0; z <= z1; z++) {
                    this.set(back, 1, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                    if (z != z1) {
                        this.set(back, 2, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST));
                    }
                }
                this.set(back + in, 1, z0 + 1, Blocks.STRIPPED_OAK_LOG);
                this.barrel(back + in, 1, z1, "supplies");
            }
            default -> {
            }
        }
    }

    /** The aisle: the dead about it, one of the living who did not get out, a handcart overturned. */
    void aisle() {
        long h = this.hash(AISLE0, 0, Z0);
        for (int z : new int[] {8, 17, 25}) {
            int x = AISLE0 + 1 + (int) Math.floorMod(this.hash(z, 1, z), 4L);
            this.set(x, 1, z, ZombieMarkerBlock.of(1 + (int) Math.floorMod(this.hash(x, 2, z), 2L), false));
        }
        BlockPos body = this.at(AISLE0 + 1, 1, 13 + (int) Math.floorMod(h, 6L));
        if (this.box.isInside(body) && this.level.getBlockState(body).isAir()) {
            Markers.remains(this.level, body, this.turn.rotate(Direction.EAST), RandomSource.create(h));
        }
        this.set(AISLE1 - 1, 1, 20, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        this.set(AISLE1 - 1, 1, 21, Blocks.HAY_BLOCK);
        this.set(AISLE1, 1, 21, Blocks.SPRUCE_SLAB);
    }
}
