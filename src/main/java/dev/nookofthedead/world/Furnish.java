package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * What life was left in the town's buildings, over what their builders put in (PLAN.md's later list, 2026-10-14: the
 * interiors thicker with Nookworks' things; the user, 2026-10-25: the user's own buildings too). Read from each building's
 * own template - its floors under its roof, the walls beside them, its desks and tables - so a building across a chunk's
 * edge is dressed the same from both sides, and set down only where the building left the place empty:
 * <ul>
 * <li>by a wall, now and then a shelf on it with what was left on the shelf - books in a row, bottles, a pot, a lantern;</li>
 * <li>at a desk or a table, its chairs (a stool where it is a workbench), one pushed back askew;</li>
 * <li>from the ceiling, now and then a lantern long out;</li>
 * <li>on the floor, a rug's worth of carpet in a living room's middle, now and then;</li>
 * </ul>
 * never before a door, nor in a passage two wide or less, and no more than a handful to a building.
 */
public final class Furnish {
    /** At most this many things a building. */
    static final int MOST = 22;

    /** A template's blocks in a dense grid by their place in it (its first palette), null where it sets nothing; cached. */
    record Grid(BlockState[] at, int sx, int sy, int sz) {
        BlockState get(BlockPos p) {
            if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0 || p.getX() >= this.sx || p.getY() >= this.sy || p.getZ() >= this.sz) {
                return null;
            }
            return this.at[(p.getY() * this.sz + p.getZ()) * this.sx + p.getX()];
        }

        boolean isEmpty() {
            return this.at.length == 0;
        }
    }

    private static final Map<StructureTemplate, Grid> BLOCKS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Map<Block, Boolean> TABLES = new java.util.concurrent.ConcurrentHashMap<>();

    private static Grid blocks(StructureTemplate template) {
        Grid found = BLOCKS.get(template);
        if (found != null) {
            return found;
        }
        net.minecraft.core.Vec3i size = template.getSize();
        BlockState[] at = new BlockState[size.getX() * size.getY() * size.getZ()];
        var palettes = ((dev.nookofthedead.mixin.StructureTemplateAccessor) template).nookofthedead$palettes();
        if (!palettes.isEmpty()) {
            for (StructureTemplate.StructureBlockInfo info : palettes.get(0).blocks()) {
                BlockPos p = info.pos();
                if (p.getX() >= 0 && p.getY() >= 0 && p.getZ() >= 0 && p.getX() < size.getX() && p.getY() < size.getY() && p.getZ() < size.getZ()) {
                    at[(p.getY() * size.getZ() + p.getZ()) * size.getX() + p.getX()] = info.state();
                }
            }
        }
        Grid grid = new Grid(at, size.getX(), size.getY(), size.getZ());
        BLOCKS.put(template, grid);
        return grid;
    }

    private static boolean air(Grid b, BlockPos p) {
        BlockState s = b.get(p);
        return s != null && s.isAir();
    }

    /** A solid block of the building's (a wall, a floor): not air, not open, not a thing set on the floor. */
    private static boolean solid(Grid b, BlockPos p) {
        BlockState s = b.get(p);
        return s != null && !s.isAir() && s.canOcclude();
    }

    private static boolean doorish(BlockState s) {
        return s != null && (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock || s.is(Blocks.LADDER));
    }

    private static boolean table(BlockState s) {
        if (s == null) {
            return false;
        }
        return TABLES.computeIfAbsent(s.getBlock(), block -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().endsWith("_desk")
                || block == Blocks.CRAFTING_TABLE || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE);
    }

    /** A floor of the building under its roof at {@code p}: empty two high over a solid floor, roofed within five. */
    private static boolean floor(Grid b, BlockPos p) {
        if (!air(b, p) || !air(b, p.above()) || !solid(b, p.below())) {
            return false;
        }
        for (int up = 2; up <= 5; up++) {
            if (solid(b, p.above(up))) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code p} is in a way through: a door beside it or under it, or open on two opposite sides between walls. */
    private static boolean passage(Grid b, BlockPos p) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (doorish(b.get(p.relative(d))) || doorish(b.get(p.relative(d).above())) || doorish(b.get(p.relative(d, 2)))) {
                return true;
            }
        }
        boolean xWalls = solid(b, p.east()) && solid(b, p.west());
        boolean zWalls = solid(b, p.north()) && solid(b, p.south());
        return xWalls || zWalls;
    }

    public static void draw(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed) {
        draw(level, template, origin, settings, box, seed, java.util.Optional.empty());
    }

    /** As {@link #draw}, for a building of {@code kind} ({@link Stashes}): a house's or an inn's desks and tables laid too. */
    public static void draw(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed, java.util.Optional<String> kind) {
        Grid b = blocks(template);
        if (b.isEmpty()) {
            return;
        }
        if (kind.map(k -> k.equals("home") || k.equals("inn")).orElse(false)) {
            laid(level, b, origin, settings, box, seed);
        }
        List<BlockPos> floors = new ArrayList<>(Markers.floors(template));
        int placed = 0;
        int most = Math.max(MOST, MOST + floors.size() / 60);              // a hall has more in it than a cottage
        debris(level, b, floors, origin, settings, box, seed);
        eaves(level, b, floors, origin, settings, box, seed, kind);
        RandomSource random = RandomSource.create(seed ^ 0xF0E21L);
        for (BlockPos p : floors) {
            if (placed >= most) {
                return;
            }
            if (!floor(b, p) || passage(b, p)) {
                continue;
            }
            long h = Math.floorMod(Town.hash(seed ^ 0xF0E21L, p.getX(), p.getY() * 131 + p.getZ(), 410), 1000L);
            Direction wall = null;
            int open = 0;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (solid(b, p.relative(d)) && solid(b, p.relative(d).above())) {
                    wall = d;
                } else if (air(b, p.relative(d))) {
                    open++;
                }
            }
            Direction table = null;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (table(b.get(p.relative(d)))) {
                    table = d;
                }
            }
            if (table != null && h < 450) {
                // a chair at the desk or the table, facing it; now and then pushed back askew
                BlockState at = b.get(p.relative(table));
                boolean bench = at.is(Blocks.CRAFTING_TABLE) || at.is(Blocks.FLETCHING_TABLE);
                Block seat = (bench ? ModWoods.STOOLS : ModWoods.CHAIRS).get(wood(seed)).get();
                Direction facing = h < 60 ? table.getClockWise() : table;
                put(level, box, origin, settings, p, seat.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
                placed++;
                continue;
            }
            if (wall != null && open >= 2 && h < 32 && solid(b, p.relative(wall).above(2)) && air(b, p.above(2))) {
                // a shelf on the wall at a man's chest, and what was left on it
                Direction out = wall.getOpposite();
                BlockState shelf = ModWoods.WALL_SHELVES.get(wood(seed)).get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out);
                if (put(level, box, origin, settings, p.above(), shelf) == 1) {
                    shelfTop(level, box, origin, settings, p.above(2), out, h, random);
                }
                placed++;                                                // counted whichever chunk sets it: the same handful from all
                continue;
            }
            if (open == 4 && h < 18) {
                // a lantern hung from the ceiling over the room's middle, out
                for (int up = 2; up <= 5; up++) {
                    if (solid(b, p.above(up))) {
                        put(level, box, origin, settings, p.above(up - 1), ModBlocks.UNLIT_LANTERN.get().defaultBlockState()
                                .setValue(BlockStateProperties.HANGING, true));
                        placed++;
                        break;
                    }
                }
                continue;
            }
            if (open == 4 && h >= 980) {
                // a rug, three by three, where the room is open round it
                boolean room = true;
                for (int dx = -1; dx <= 1 && room; dx++) {
                    for (int dz = -1; dz <= 1 && room; dz++) {
                        room = floor(b, p.offset(dx, 0, dz)) && !passage(b, p.offset(dx, 0, dz));
                    }
                }
                if (room) {
                    Block rug = h % 3 == 0 ? Blocks.CARPET.red() : h % 3 == 1 ? Blocks.CARPET.brown() : Blocks.CARPET.gray();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.floorMod(Town.hash(seed, p.getX() + dx, p.getZ() + dz, 411), 7L) != 0) {   // worn through here and there
                                put(level, box, origin, settings, p.offset(dx, 0, dz), rug.defaultBlockState());
                            }
                        }
                    }
                    placed++;
                }
            }
        }
    }

    /**
     * Time in the rooms (1.2, the user: 「映画のセットかと思うくらい、細部までこだわって」): over the building's floors, now and
     * then plaster down from the ceiling against a wall, leaves blown in by a door or a broken window, a web up in a corner,
     * a book or a bottle dropped on the floor - a few to a room, never in a way through, all from the template, the same
     * from every chunk.
     */
    private static void debris(WorldGenLevel level, Grid b, List<BlockPos> floors, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox box, long seed) {
        for (BlockPos p : floors) {
            if (!floor(b, p)) {
                continue;
            }
            long h = Math.floorMod(Town.hash(seed ^ 0xD3B215L, p.getX(), p.getY() * 131 + p.getZ(), 414), 1000L);
            if (h >= 70) {
                continue;
            }
            int walls = 0;
            boolean opening = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState side = b.get(p.relative(d));
                walls += solid(b, p.relative(d)) ? 1 : 0;
                opening |= doorish(side) || side != null && side.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
                        || doorish(b.get(p.relative(d, 2)));
            }
            boolean way = passage(b, p);
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, p).offset(origin);
            if (!box.isInside(at) || !level.getBlockState(at).isAir() || Markers.taken(at)
                    || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                continue;
            }
            if (opening && h < 30) {
                // leaves blown in at the door, at the broken window
                level.setBlock(at, Blocks.LEAF_LITTER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                        Direction.from2DDataValue((int) (h % 4))).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SEGMENT_AMOUNT, 1 + (int) (h % 4)), 2);
            } else if (walls >= 1 && !way && h < 22) {
                level.setBlock(at, ModBlocks.PEBBLES.get().defaultBlockState(), 2);       // plaster down from the ceiling
            } else if (walls >= 2 && !way && h < 40) {
                // a web up in the corner: under the ceiling, where two walls meet
                for (int up = 2; up <= 5; up++) {
                    if (solid(b, p.above(up))) {
                        put(level, box, origin, settings, p.above(up - 1), Blocks.COBWEB.defaultBlockState());
                        break;
                    }
                }
            } else if (!way && h < 48) {
                RandomSource random = RandomSource.create(seed ^ at.asLong());
                Props.pile(level, box, at, 1, random.nextFloat() * 360, random, () -> Props.found(level, random));   // a book dropped
            } else if (!way && walls >= 1 && h < 54) {
                CapitalRooms.bottles(level, box, at, 1, RandomSource.create(seed ^ at.asLong()));   // a bottle left on the floor
            }
        }
    }

    /**
     * What was put away under the roof (1.2: an attic stood empty): where the ceiling comes down low over the floor - the
     * slope of the roof two blocks up - against a wall, now and then a crate, a barrel or an old chest with what was put
     * away in it (the building's own stash), a stool on its side, a bundle of hay.
     */
    private static void eaves(WorldGenLevel level, Grid b, List<BlockPos> floors, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox box, long seed, java.util.Optional<String> kind) {
        for (BlockPos p : floors) {
            BlockState over = b.get(p.above(2));
            if (!floor(b, p) || over == null || !(over.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                    || over.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) || passage(b, p)) {
                continue;
            }
            boolean wall = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                wall |= solid(b, p.relative(d));
            }
            long h = Math.floorMod(Town.hash(seed ^ 0xEA7E5L, p.getX(), p.getY() * 131 + p.getZ(), 415), 1000L);
            if (!wall || h >= 220) {
                continue;
            }
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, p).offset(origin);
            if (!box.isInside(at) || !level.getBlockState(at).isAir() || Markers.taken(at)
                    || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                continue;
            }
            if (h < 70) {
                level.setBlock(at, ModBlocks.CRATE.get().defaultBlockState(), 2);
            } else if (h < 120) {
                level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), 2);
            } else if (h < 165) {
                Direction facing = settings.getRotation().rotate(Direction.from2DDataValue((int) (h % 4)));
                level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
                if (level.getBlockEntity(at) instanceof net.minecraft.world.RandomizableContainer chest) {
                    chest.setLootTable(Markers.table(kind.map(k -> "stash_" + k).orElse("stash")), seed ^ at.asLong());
                }
            } else if (h < 195) {
                level.setBlock(at, Blocks.HAY_BLOCK.defaultBlockState(), 2);
            } else {
                level.setBlock(at, ModWoods.STOOLS.get(wood(seed)).get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((int) (h % 4))), 2);
            }
        }
    }

    /** At most this many places laid in a building. */
    static final int LAID = 6;
    /** What was set out and never eaten (Nookworks 1.7 sets each out as itself on a plate). */
    static final java.util.List<String> DISHES = java.util.List.of("minecraft:bread", "minecraft:baked_potato", "minecraft:cooked_cod",
            "minecraft:apple", "minecraft:pumpkin_pie", "minecraft:cookie", "nookworks:tamagoyaki", "nookworks:karaage", "nookworks:fried_fish",
            "nookworks:donut", "nookworks:pancake", "nookworks:salt_onigiri", "nookworks:salmon_onigiri", "nookworks:tonkatsu",
            "nookworks:fried_egg", "nookworks:boiled_egg", "nookworks:grilled_corn", "nookworks:french_fries", "minecraft:rotten_flesh");

    /**
     * The table laid for a meal no one sat down to (1.2, Nookworks 1.7's dishes set out as themselves): on now and then a
     * desk or table of a house or an inn, a plate with what was on it, or a mug, a glass, a tray, a candle - from the
     * template, the same from every chunk.
     */
    private static void laid(WorldGenLevel level, Grid b, BlockPos origin, StructurePlaceSettings settings, BoundingBox box, long seed) {
        int laid = 0;
        for (int y = 1; y < b.sy() && laid < LAID; y++) {
            for (int z = 0; z < b.sz() && laid < LAID; z++) {
                for (int x = 0; x < b.sx() && laid < LAID; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!table(b.get(p.below())) || !air(b, p) || b.get(p.below()).is(Blocks.CRAFTING_TABLE)) {
                        continue;
                    }
                    long h = Math.floorMod(Town.hash(seed ^ 0x1A1DL, x, y * 131 + z, 412), 1000L);
                    if (h >= 300) {
                        continue;
                    }
                    laid++;                                              // counted whichever chunk sets it: the same from all
                    BlockPos at = StructureTemplate.calculateRelativePosition(settings, p).offset(origin);
                    if (!box.isInside(at) || !level.getBlockState(at).isAir() || Markers.taken(at)
                            || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                        continue;                                        // the table gone with the wear or the fall: nothing laid on air
                    }
                    Direction facing = settings.getRotation().rotate(Direction.from2DDataValue((int) (h % 4)));
                    if (h < 170) {
                        plate(level, at, facing, h);
                    } else if (h < 230) {
                        level.setBlock(at, (h % 2 == 0 ? ModBlocks.MUG : ModBlocks.WINE_GLASS).get().defaultBlockState(), 2);
                    } else if (h < 270) {
                        level.setBlock(at, dev.nookworks.registry.ModKitchen.WOODEN_TRAY.get().defaultBlockState(), 2);   // a tray, cleared
                    } else {
                        level.setBlock(at, Blocks.CANDLE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** A plate set at {@code at}, facing {@code facing}, with one of {@link #DISHES} on it from {@code h} - licked clean one time in five. */
    public static void plate(WorldGenLevel level, BlockPos at, Direction facing, long h) {
        level.setBlock(at, dev.nookworks.registry.ModKitchen.PLATE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
        net.minecraft.world.item.Item food = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getValue(net.minecraft.resources.Identifier.parse(DISHES.get((int) (Math.floorMod(h >> 2, (long) DISHES.size())))));
        if (Math.floorMod(h, 5L) != 0 && level.getBlockEntity(at) instanceof dev.nookworks.cooking.PlateBlockEntity on) {
            on.place(new net.minecraft.world.item.ItemStack(food), null);
        }
    }

    /** The small pictures, a block square, a room's wall takes: hung where a shelf might be. */
    public static final java.util.List<String> SMALL_PICTURES = java.util.List.of("portrait_in_red", "shallows", "embers", "patchwork",
            "carrot", "potato", "radish");

    /** A small picture hung at {@code at} on the wall behind it, its face {@code out}; framed half the time, from {@code h}. */
    public static void picture(WorldGenLevel level, BoundingBox box, BlockPos at, Direction out, long h) {
        if (!box.isInside(at) || !level.getBlockState(at).isAir()) {
            return;
        }
        var poster = new dev.nookworks.poster.PosterEntity(level.getLevel(), at, out,
                dev.nookworks.Nookworks.id(SMALL_PICTURES.get((int) Math.floorMod(h, (long) SMALL_PICTURES.size()))));
        long k = Math.floorMod(h >> 5, 10L);
        if (k < 5) {
            poster.setFrame(new net.minecraft.world.item.ItemStack(k < 2 ? net.minecraft.world.item.Items.ITEM_FRAME
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.nookworks.Nookworks.id(k < 4 ? "dark_oak_item_frame" : "brass_item_frame"))));
        }
        BlockPos behind = at.relative(out.getOpposite());
        if (!box.isInside(behind) || !level.getBlockState(behind).isFaceSturdy(level, behind, out)) {
            return;                                                      // no wall there to hang it on (the audit, 2026-11-15)
        }
        level.addFreshEntity(poster);
    }

    /** Posters nothing frames: a stain, a sign, a warning, a notice tacked up. */
    static final java.util.Set<String> UNFRAMED = java.util.Set.of("bloodstain", "do_not_enter", "hazard_stripes", "infected_area",
            "safety_cross", "safety_first", "wanted", "the_infected", "zombie", "workshop");

    /**
     * Its pictures framed (1.2, Nookworks 1.7's frames round a poster): of the posters its builder hung, a house's, a
     * shop's, an inn's, a church's or an office's now and then in a frame - plain, dark oak or brass as the place was -
     * as it is placed. A notice, a warning, a stain is never framed.
     */
    public static net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor frames(long seed, java.util.Optional<String> kind) {
        int percent = kind.map(k -> switch (k) {
            case "home", "inn", "shop", "office", "church" -> 45;
            default -> 10;
        }).orElse(15);
        boolean fine = kind.map(k -> k.equals("shop") || k.equals("office") || k.equals("inn") || k.equals("church")).orElse(false);
        return new net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor() {
            @Override
            public StructureTemplate.@org.jspecify.annotations.Nullable StructureBlockInfo process(net.minecraft.world.level.LevelReader level,
                    BlockPos target, BlockPos reference, StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed,
                    StructurePlaceSettings settings, @org.jspecify.annotations.Nullable StructureTemplate template) {
                return processed;
            }

            @Override
            public StructureTemplate.StructureEntityInfo processEntity(net.minecraft.world.level.LevelReader level, BlockPos seedPos,
                    StructureTemplate.StructureEntityInfo raw, StructureTemplate.StructureEntityInfo info, StructurePlaceSettings settings,
                    StructureTemplate template) {
                if (info == null || !info.nbt.getStringOr("id", "").equals("nookworks:poster") || info.nbt.contains("frame")) {
                    return info;
                }
                String variant = info.nbt.getStringOr("variant", "");
                if (UNFRAMED.contains(variant.substring(variant.indexOf(':') + 1))) {
                    return info;
                }
                long h = Math.floorMod(Town.hash(seed ^ 0xF4A3EL, raw.blockPos.getX(), raw.blockPos.getY() * 131L + raw.blockPos.getZ(), 413), 1000L);
                if (h >= percent * 10L) {
                    return info;
                }
                long k = h % 20;
                String frame = fine ? (k < 6 ? "minecraft:item_frame" : k < 13 ? "nookworks:dark_oak_item_frame" : "nookworks:brass_item_frame")
                        : (k < 12 ? "minecraft:item_frame" : k < 18 ? "nookworks:dark_oak_item_frame" : "nookworks:brass_item_frame");
                net.minecraft.nbt.CompoundTag nbt = info.nbt.copy();
                net.minecraft.nbt.CompoundTag stack = new net.minecraft.nbt.CompoundTag();
                stack.putString("id", frame);
                stack.putInt("count", 1);
                nbt.put("frame", stack);
                return new StructureTemplate.StructureEntityInfo(info.pos, info.blockPos, nbt);
            }

            @Override
            public com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor> codec() {
                return com.mojang.serialization.MapCodec.unit(this);          // made in place, never written out
            }
        };
    }

    private static void shelfTop(WorldGenLevel level, BoundingBox box, BlockPos origin, StructurePlaceSettings settings, BlockPos local,
            Direction out, long h, RandomSource random) {
        BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
        if (!box.isInside(at) || !level.getBlockState(at).isAir() || Markers.taken(at)) {
            return;
        }
        // mirrored, then turned, as put() turns the shelf itself (the review, 2026-10-25: the other way round, a mirrored
        // building turned a quarter had the books on its shelves facing the wall)
        Direction facing = settings.getRotation().rotate(settings.getMirror().mirror(out));
        long k = (h >> 1) % 5;
        if (k <= 1) {
            Props.row(level, box, at, facing, (h & 1) == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT, 2 + (int) (h % 4),
                    () -> Props.found(level, random));
        } else if (k == 2) {
            CapitalRooms.bottles(level, box, at, 1 + (int) (h % 3), random);
        } else if (k == 3) {
            level.setBlock(at, dev.nookworks.registry.GlazedPots.EMPTY.get().defaultBlockState(), 2);
        } else {
            level.setBlock(at, ModBlocks.UNLIT_LANTERN.get().defaultBlockState(), 2);
        }
    }

    /** Sets {@code state} at the building's {@code local} place, turned as the building is, if it is empty there now; 1 if set. */
    private static int put(WorldGenLevel level, BoundingBox box, BlockPos origin, StructurePlaceSettings settings, BlockPos local, BlockState state) {
        BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
        if (!box.isInside(at) || !level.getBlockState(at).isAir() || Markers.taken(at)) {
            return 0;
        }
        level.setBlock(at, state.mirror(settings.getMirror()).rotate(settings.getRotation()), 2);
        return 1;
    }

    private static String wood(long seed) {
        return switch ((int) Math.floorMod(seed >> 13, 4L)) {
            case 0 -> "spruce";
            case 1 -> "dark_oak";
            case 2 -> "oak";
            default -> "birch";
        };
    }

    private Furnish() {}
}
