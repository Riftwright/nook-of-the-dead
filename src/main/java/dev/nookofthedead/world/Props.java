package dev.nookofthedead.world;

import java.util.List;
import java.util.function.Supplier;

import dev.nookworks.decor.BookendBlock;
import dev.nookworks.decor.BookendBlockEntity;
import dev.nookworks.decor.PlacedBooksBlock;
import dev.nookworks.decor.PlacedBooksBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModRecycling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Nookworks 1.1's things set down in the If's ruins (0.3, the user, 2026-10-20: "NW1.1のブロックを使って、新しい建築や
 * コレまでの建築の修整、小物の追加"): books lying in piles, leant on a wall, standing in a row against a Bookend - plain
 * ones mostly, a diary now and then, now and then an enchanted one (the user: "たまにエンチャント本も置いていいよ"); and
 * scrap to build with - Corrugated Sheets (bare, left to rust: "波板は、ワックスしなくていいよ"), Scrap Bales, Patched
 * Sheet Metal. Each draws only inside {@code box}, from the seed and the place alone.
 */
public final class Props {
    /** What a book set down is: a plain one, a diary (which one, its first reader decides), an enchanted one, an empty one to write in. */
    public enum Kind { PLAIN, DIARY, ENCHANTED, WRITABLE }

    /** The enchantments a book left lying about may hold, at the first level or two: nothing a ruin should hand out early (no Mending). */
    private static final List<ResourceKey<Enchantment>> LYING_ABOUT = List.of(Enchantments.UNBREAKING, Enchantments.EFFICIENCY,
            Enchantments.PROTECTION, Enchantments.SHARPNESS, Enchantments.POWER, Enchantments.FEATHER_FALLING, Enchantments.RESPIRATION,
            Enchantments.FIRE_PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
            Enchantments.LUCK_OF_THE_SEA, Enchantments.LURE, Enchantments.AQUA_AFFINITY, Enchantments.SILK_TOUCH, Enchantments.FORTUNE);

    /** A book of {@code kind}. */
    public static ItemStack book(WorldGenLevel level, Kind kind, RandomSource random) {
        return switch (kind) {
            case DIARY -> Diaries.book(random);
            case WRITABLE -> new ItemStack(Items.WRITABLE_BOOK);
            case ENCHANTED -> {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(LYING_ABOUT.get(random.nextInt(LYING_ABOUT.size())));
                ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                stored.set(holder, Math.min(holder.value().getMaxLevel(), 1 + random.nextInt(2)));
                book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
                yield book;
            }
            default -> new ItemStack(Items.BOOK);
        };
    }

    /**
     * A book as one finds them lying about a house: plain mostly; a diary one in ten, an enchanted one in twenty, one to
     * write in one in twenty.
     */
    public static ItemStack found(WorldGenLevel level, RandomSource random) {
        int roll = random.nextInt(20);
        return book(level, roll < 2 ? Kind.DIARY : roll == 2 ? Kind.ENCHANTED : roll == 3 ? Kind.WRITABLE : Kind.PLAIN, random);
    }

    /** Whether books may lie at {@code at}: empty, on a full top face. */
    static boolean floorFor(WorldGenLevel level, BlockPos at) {
        BlockState here = level.getBlockState(at);
        return (here.isAir() || here.canBeReplaced()) && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP);
    }

    /** A pile of {@code n} books (1 to 3) lying at {@code at}, the first its way at {@code yaw}; false where they cannot lie. */
    public static boolean pile(WorldGenLevel level, BoundingBox box, BlockPos at, int n, float yaw, RandomSource random, Supplier<ItemStack> books) {
        if (!box.isInside(at) || !floorFor(level, at)) {
            return false;
        }
        n = Math.max(1, Math.min(PlacedBooksBlock.MAX, n));
        level.setBlock(at, ModBlocks.PLACED_BOOKS.get().defaultBlockState().setValue(PlacedBooksBlock.BOOKS, n)
                .setValue(PlacedBooksBlock.STANDING, false), 2);
        if (level.getBlockEntity(at) instanceof PlacedBooksBlockEntity pile) {
            for (int i = 0; i < n; i++) {
                pile.add(books.get(), yaw, false, random);
            }
        }
        return true;
    }

    /** {@code n} books leant at {@code at} against the wall behind them, covers facing {@code out}; false where there is no wall. */
    public static boolean leant(WorldGenLevel level, BoundingBox box, BlockPos at, Direction out, int n, RandomSource random, Supplier<ItemStack> books) {
        BlockPos wall = at.relative(out.getOpposite());
        if (!box.isInside(at) || !floorFor(level, at) || !level.getBlockState(wall).isFaceSturdy(level, wall, out)) {
            return false;
        }
        n = Math.max(1, Math.min(PlacedBooksBlock.MAX, n));
        level.setBlock(at, ModBlocks.PLACED_BOOKS.get().defaultBlockState().setValue(PlacedBooksBlock.BOOKS, n)
                .setValue(PlacedBooksBlock.STANDING, true).setValue(PlacedBooksBlock.FACING, out), 2);
        if (level.getBlockEntity(at) instanceof PlacedBooksBlockEntity pile) {
            for (int i = 0; i < n; i++) {
                pile.add(books.get(), out.toYRot(), true, random);
            }
        }
        return true;
    }

    /**
     * A Bookend at {@code at} - spines facing {@code spines}, its upright at {@code hinge} - and {@code count} books
     * standing in a row from it, five a block, on as far as there is floor; how many stood.
     */
    public static int row(WorldGenLevel level, BoundingBox box, BlockPos at, Direction spines, DoorHingeSide hinge, int count,
            Supplier<ItemStack> books) {
        if (!box.isInside(at) || !floorFor(level, at)) {
            return 0;
        }
        BlockState end = ModBlocks.BOOKEND.get().defaultBlockState().setValue(BookendBlock.FACING, spines).setValue(BookendBlock.HINGE, hinge);
        Direction along = BookendBlock.along(end);
        int stood = 0;
        BlockPos pos = at;
        boolean first = true;
        while (stood < count) {
            if (!first && (!box.isInside(pos) || !floorFor(level, pos))) {
                break;
            }
            int here = Math.min(BookendBlock.MAX, count - stood);
            BlockState state = (first ? end : ModBlocks.BOOK_ROW.get().defaultBlockState().setValue(BookendBlock.FACING, spines)
                    .setValue(BookendBlock.HINGE, hinge)).setValue(BookendBlock.BOOKS, here);
            level.setBlock(pos, state, 2);
            if (level.getBlockEntity(pos) instanceof BookendBlockEntity row) {
                for (int i = 0; i < here; i++) {
                    row.add(books.get());
                }
            }
            stood += here;
            first = false;
            if (here < BookendBlock.MAX) {
                break;
            }
            pos = pos.relative(along);
        }
        return stood;
    }

    // --- scrap ----------------------------------------------------------------------------------------------------------

    private static final String[] AGES = {"", "exposed_", "weathered_", "oxidized_"};

    /** How far a bare sheet or bale has rusted, from {@code h}: mostly well on, out in the weather all this while. */
    private static String age(long h) {
        int roll = (int) Math.floorMod(h, 10L);
        return AGES[roll < 1 ? 0 : roll < 3 ? 1 : roll < 7 ? 2 : 3];
    }

    /** A bare Corrugated Sheet, rusted as {@code h} has it, its face looking {@code facing}; in the middle of its block if {@code centered}. */
    public static BlockState sheet(long h, Direction facing, Direction.Axis flatAxis, boolean centered) {
        Block block = ModRecycling.CORRUGATED_SHEETS.get(age(h) + "corrugated_sheet").get();
        Direction.Axis axis = facing.getAxis().isHorizontal() ? facing.getClockWise().getAxis() : flatAxis;
        return block.defaultBlockState().setValue(dev.nookworks.recycling.CorrugatedSheetBlock.FACING, facing)
                .setValue(dev.nookworks.recycling.CorrugatedSheetBlock.AXIS, axis)
                .setValue(dev.nookworks.recycling.CorrugatedSheetBlock.CENTERED, centered);
    }

    /** A painted sheet (red, green, blue or white), which never rusts: a shop's, a depot's. */
    public static BlockState painted(long h, Direction facing, boolean centered) {
        var paints = ModRecycling.PAINTS;
        Block block = ModRecycling.PAINTED_SHEETS.get(paints.get((int) Math.floorMod(h, (long) paints.size()))).get();
        return block.defaultBlockState().setValue(dev.nookworks.recycling.CorrugatedSheetBlock.FACING, facing)
                .setValue(dev.nookworks.recycling.CorrugatedSheetBlock.AXIS, facing.getAxis().isHorizontal() ? facing.getClockWise().getAxis()
                        : Direction.Axis.X)
                .setValue(dev.nookworks.recycling.CorrugatedSheetBlock.CENTERED, centered);
    }

    /** A bare Scrap Bale, rusted as {@code h} has it. */
    public static BlockState bale(long h) {
        return ModRecycling.SCRAP_BALES.get(age(h) + "scrap_bale").get().defaultBlockState();
    }

    public static BlockState patched() {
        return ModRecycling.PATCHED_SHEET_METAL.get().defaultBlockState();
    }

    // --- windows sheeted over ------------------------------------------------------------------------------------------

    /** The buildings people held out in to the last (the shelters, the refuge house, the dig camp, the caravanserai). */
    public static final java.util.Set<String> SHELTERS = java.util.Set.of("town/m7", "town/m8", "town/m9", "town/s26", "town/s27",
            "town/s28", "town/s30");

    /** The town's library gone to ruin (S29, 0.3). */
    public static final String LIBRARY = "town/s29";

    /** Whether the building on a lot of {@code seed} was sheeted over by whoever held out in it: a shelter always, one house in twelve. */
    static boolean boarded(net.minecraft.resources.Identifier building, long seed) {
        return SHELTERS.contains(building.getPath()) || Math.floorMod(seed >> 9, 12L) == 0;
    }

    /**
     * Its windows sheeted over from inside (0.3, Nookworks 1.1): {@code percent} in a hundred of its glass panes a
     * Corrugated Sheet in their place, in the middle of the block as the pane stood, rusted as it chanced.
     */
    public static net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor boarded(int percent) {
        return new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor() {
            @Override
            public StructureTemplate.@org.jspecify.annotations.Nullable StructureBlockInfo process(net.minecraft.world.level.LevelReader level,
                    BlockPos target, BlockPos reference, StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed,
                    StructurePlaceSettings settings, @org.jspecify.annotations.Nullable StructureTemplate template) {
                if (processed == null || !(processed.state().getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock)
                        || !BuiltInRegistries.BLOCK.getKey(processed.state().getBlock()).getPath().endsWith("glass_pane")) {
                    return processed;
                }
                BlockPos at = processed.pos();
                long h = Town.hash(0x5EE7L, at.getX(), at.getY(), at.getZ());
                if (Math.floorMod(h, 100L) >= percent) {
                    return processed;
                }
                BlockState pane = processed.state();
                boolean alongX = pane.getValue(net.minecraft.world.level.block.IronBarsBlock.EAST) || pane.getValue(net.minecraft.world.level.block.IronBarsBlock.WEST);
                Direction facing = alongX ? Direction.SOUTH : Direction.EAST;
                return new StructureTemplate.StructureBlockInfo(at, sheet(h >> 8, facing, facing.getAxis(), true), null);
            }

            @Override
            public com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor> codec() {
                return com.mojang.serialization.MapCodec.unit(this);          // made in place, never written out
            }
        };
    }

    // --- a building's books -------------------------------------------------------------------------------------------

    /** What books are put on: desks, tables, a workbench; bookshelves have rows stood on top. */
    private static boolean desk(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return block == Blocks.CRAFTING_TABLE || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE
                || BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("nookworks") && path.endsWith("_desk");
    }

    /** The desks and bookshelves a template holds, where they are put. */
    private static List<StructureTemplate.StructureBlockInfo> surfaces(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> all = new java.util.ArrayList<>();
        java.util.Set<Block> seen = new java.util.HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if ((desk(block) || block == Blocks.BOOKSHELF) && seen.add(block)) {
                all.addAll(template.filterBlocks(origin, settings, block));
            }
        }
        return all;
    }

    /**
     * Books left about a building as it was left (0.3): on its desks and tables now and then a pile, on its bookshelves now
     * and then a row against a Bookend, before them now and then some fallen to the floor - none where it burned, nor on
     * what fell or was carried off since. At most six things a building.
     */
    static void dress(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed, boolean burned) {
        if (burned) {
            return;
        }
        RandomSource random = RandomSource.create(seed ^ 0xB00C5L);
        books(level, template, origin, settings, box, seed, 30, 14, 8, 6, () -> found(level, random), random);
    }

    /**
     * A research institute's books (the user, 2026-10-20: "研究所の隅とかに乱雑に置かれた本を拾って読む"): its desks and shelves
     * thick with them, more lying in the corners of its rooms, and among them now and then one of the notes its people
     * left ({@link Notes#INSTITUTE}).
     */
    static void research(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x5E5EA5C4L);
        Supplier<ItemStack> books = () -> studied(level, Notes.INSTITUTE, random);
        books(level, template, origin, settings, box, seed, 60, 40, 15, 64, books, random);
        scatter(level, box, template.getBoundingBox(settings, origin), seed, 70, books, random);
    }

    /**
     * Where people held out to the last (the shelters): their books on every desk and in the corners, their notes among
     * them ({@link Notes#CAMP}); the scrap they were saving, baled, against a wall.
     */
    static void held(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed) {
        RandomSource random = RandomSource.create(seed ^ 0xC4A9FL);
        Supplier<ItemStack> books = () -> studied(level, Notes.CAMP, random);
        books(level, template, origin, settings, box, seed, 50, 30, 15, 24, books, random);
        BoundingBox region = template.getBoundingBox(settings, origin);
        scatter(level, box, region, seed, 25, books, random);
        // scrap baled and waiting along a wall: a few stacks where a wall meets the floor
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int stacks = 0;
        for (int x = Math.max(box.minX(), region.minX()); x <= Math.min(box.maxX(), region.maxX()) && stacks < 3; x++) {
            for (int z = Math.max(box.minZ(), region.minZ()); z <= Math.min(box.maxZ(), region.maxZ()) && stacks < 3; z++) {
                for (int y = Math.max(box.minY(), region.minY()); y <= Math.min(box.maxY(), region.maxY()) && stacks < 3; y++) {
                    long h = Town.hash(seed ^ 0xBA1EL, x, y, z);
                    if (Math.floorMod(h, 1000L) >= 6) {
                        continue;
                    }
                    at.set(x, y, z);
                    if (!floorFor(level, at) || !level.getBlockState(at.above()).isAir() || !level.getBlockState(at.above(2)).isAir()) {
                        continue;
                    }
                    boolean wall = false;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        wall |= level.getBlockState(at.relative(d)).isFaceSturdy(level, at.relative(d), d.getOpposite());
                    }
                    if (wall) {
                        level.setBlock(at, bale(h >> 10), 2);
                        if ((h >> 20 & 1) == 0) {
                            level.setBlock(at.above(), bale(h >> 22), 2);
                        }
                        stacks++;
                    }
                }
            }
        }
    }

    /**
     * The library (S29): every desk and shelf with books on it, rows along the shelves, more fallen to the floor and in
     * every corner; its notes among them ({@link Notes#LIBRARY}).
     */
    static void library(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed) {
        RandomSource random = RandomSource.create(seed ^ 0x11B4A4L);
        Supplier<ItemStack> books = () -> studied(level, Notes.LIBRARY, random);
        books(level, template, origin, settings, box, seed, 80, 55, 25, 96, books, random);
        scatter(level, box, template.getBoundingBox(settings, origin), seed, 120, books, random);
    }

    /** A book as one finds them where people studied: a note of {@code notes} one in four, enchanted one in ten, a diary one in twenty. */
    static ItemStack studied(WorldGenLevel level, List<Notes.Note> notes, RandomSource random) {
        int roll = random.nextInt(20);
        return roll < 5 ? Notes.any(notes, random) : book(level, roll < 7 ? Kind.ENCHANTED : roll == 7 ? Kind.DIARY : roll == 8 ? Kind.WRITABLE
                : Kind.PLAIN, random);
    }

    /**
     * Books lying in the corners of rooms within {@code region}: where two walls meet over a floor, {@code perMille} in a
     * thousand of them - a pile on the floor, or leant on one of the walls.
     */
    static void scatter(WorldGenLevel level, BoundingBox box, BoundingBox region, long seed, int perMille, Supplier<ItemStack> books,
            RandomSource random) {
        int x0 = Math.max(box.minX(), region.minX()), x1 = Math.min(box.maxX(), region.maxX());
        int y0 = Math.max(box.minY(), region.minY()), y1 = Math.min(box.maxY(), region.maxY());
        int z0 = Math.max(box.minZ(), region.minZ()), z1 = Math.min(box.maxZ(), region.maxZ());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    long h = Town.hash(seed ^ 0xC0E7L, x, y, z);
                    if (Math.floorMod(h, 1000L) >= perMille) {
                        continue;
                    }
                    at.set(x, y, z);
                    if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !floorFor(level, at)) {
                        continue;
                    }
                    Direction wall = null;
                    int walls = 0;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos side = at.relative(d);
                        if (level.getBlockState(side).isFaceSturdy(level, side, d.getOpposite())) {
                            walls++;
                            wall = d;
                        }
                    }
                    if (walls < 2 || walls > 3) {
                        continue;                                        // a corner, not the middle of a way through, nor a closet
                    }
                    BlockPos here = at.immutable();
                    if ((h >> 12 & 1) == 0) {
                        leant(level, box, here, wall.getOpposite(), 1 + (int) Math.floorMod(h >> 14, 3L), random, books);
                    } else {
                        pile(level, box, here, 1 + (int) Math.floorMod(h >> 14, 3L), random.nextFloat() * 360, random, books);
                    }
                }
            }
        }
    }

    /**
     * Books on a template's desks ({@code deskChance} in a hundred), in rows on its bookshelves ({@code rowChance}),
     * fallen before them ({@code fallChance}); at most {@code most}.
     */
    static void books(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed, int deskChance, int rowChance, int fallChance, int most, Supplier<ItemStack> books, RandomSource random) {
        int placed = 0;
        for (StructureTemplate.StructureBlockInfo info : surfaces(template, origin, settings)) {
            BlockPos at = info.pos();
            if (placed >= most || !box.isInside(at.above()) || level.getBlockState(at).getBlock() != info.state().getBlock()) {
                continue;
            }
            long h = Town.hash(seed, at.getX(), at.getY(), at.getZ());
            int roll = (int) Math.floorMod(h >> 7, 100L);
            if (info.state().is(Blocks.BOOKSHELF)) {
                if (roll < rowChance) {                                         // a row on top, its spines to a side open to the room
                    for (Direction out : Direction.Plane.HORIZONTAL) {
                        if (level.getBlockState(at.relative(out)).isAir()) {
                            DoorHingeSide hinge = (h & 1) == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
                            if (row(level, box, at.above(), out, hinge, 2 + (int) Math.floorMod(h >> 3, 6L), books) > 0) {
                                placed++;
                            }
                            break;
                        }
                    }
                } else if (roll < rowChance + fallChance) {                                  // fallen off it to the floor before it
                    for (Direction out : Direction.Plane.HORIZONTAL) {
                        if (pile(level, box, at.relative(out), 1 + (int) Math.floorMod(h >> 5, 3L), random.nextFloat() * 360, random, books)) {
                            placed++;
                            break;
                        }
                    }
                }
            } else if (roll < deskChance && pile(level, box, at.above(), 1 + (int) Math.floorMod(h >> 5, 3L), random.nextFloat() * 360, random, books)) {
                placed++;
            }
        }
    }

    private Props() {}
}
