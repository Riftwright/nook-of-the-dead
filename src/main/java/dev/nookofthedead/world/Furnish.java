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
final class Furnish {
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

    static void draw(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed) {
        Grid b = blocks(template);
        if (b.isEmpty()) {
            return;
        }
        List<BlockPos> floors = new ArrayList<>(Markers.floors(template));
        int placed = 0;
        RandomSource random = RandomSource.create(seed ^ 0xF0E21L);
        for (BlockPos p : floors) {
            if (placed >= MOST) {
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

    private static void shelfTop(WorldGenLevel level, BoundingBox box, BlockPos origin, StructurePlaceSettings settings, BlockPos local,
            Direction out, long h, RandomSource random) {
        BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
        if (!box.isInside(at) || !level.getBlockState(at).isAir()) {
            return;
        }
        Direction facing = settings.getMirror().mirror(settings.getRotation().rotate(out));
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
        if (!box.isInside(at) || !level.getBlockState(at).isAir()) {
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
