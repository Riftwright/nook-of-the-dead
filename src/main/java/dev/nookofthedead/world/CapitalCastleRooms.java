package dev.nookofthedead.world;

import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Inside the castle on the crag ({@link CapitalCastle}): what made it a king's house, twenty years gone dark. The great
 * hall - a checkered floor, the red runner down its middle to the dais and the throne under the realm's banners, two long
 * banquet tables with their benches, the wall's piers carrying a blind arcade and the timber trusses of its roof,
 * chandeliers of chain and lanterns over the tables, sconces on the piers, all of them out. The chapel - benches either side
 * of its aisle, the altar and its candles under the glass at the east end. And in the keep, what every storey shares: oak
 * panelling round the walls, beams under each floor, sconces between the windows, banners in the rooms of state.
 * <p>
 * All in the castle's own axes ({@code f} forward to the gate, {@code l} across), every column from its own coordinates.
 */
final class CapitalCastleRooms {
    // --- the great hall -------------------------------------------------------------------------------------------

    /** The great hall's extent in castle axes: forward {@code F0}..{@code F1}, across {@code L0}..{@code L1}. */
    static final int F0 = -4;
    static final int F1 = 20;
    static final int L0 = -30;
    static final int L1 = -16;
    /** How high its walls stand over the castle's top. */
    static final int HIGH = 14;

    /** Draws column ({@code x}, {@code z}) of the great hall, at ({@code f}, {@code l}). */
    static void greatHall(WorldGenLevel level, Capital.Site s, int x, int z, double f, double l, int top, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        double across = Math.min(l - L0, L1 - l);                         // in from the long sides
        double along = Math.min(f - F0, F1 - f);                          // in from the ends
        double c = l - (L0 + L1) / 2.0;                                   // from the middle line: half-blocks
        boolean wall = across < 1 || along < 1;
        boolean pierLine = Math.floorMod((int) Math.floor(f), 4) == 0;      // the piers, every fourth along
        int height = top + HIGH;
        Direction fwd = CapitalCastle.forward(s);                          // toward the gate: the door's end is back of it
        Direction toThrone = fwd;
        Direction intoRoom = c > 0 ? CapitalCastle.sideways(s, -1) : CapitalCastle.sideways(s, 1);
        for (int y = top; y <= height; y++) {
            int h = y - top;
            BlockState state;
            if (wall) {
                state = hallWall(s, x, y, z, f, c, h, across, along, pierLine);
            } else if (h == 0) {
                // the floor: polished stone in a checker, darker under the dais
                boolean dark = Math.floorMod((int) Math.floor(f) + (int) Math.floor(c), 2) == 0;
                state = (f > 15 ? Blocks.POLISHED_DEEPSLATE : dark ? Blocks.POLISHED_DEEPSLATE : Blocks.POLISHED_ANDESITE).defaultBlockState();
            } else if (Math.abs(c) > 5 && pierLine && h <= 11) {
                // a pier inside the wall, its base and capital carved
                state = (h == 1 || h == 11 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
            } else if (Math.abs(c) > 5 && h >= 10 && h <= 11) {
                // the blind arcade between the piers: the arches' springing, the wall over them
                int fromPier = Math.min(Math.floorMod((int) Math.floor(f), 4), 4 - Math.floorMod((int) Math.floor(f), 4));
                state = h == 11 ? Blocks.STONE_BRICKS.defaultBlockState() : fromPier == 1
                        ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                                .setValue(StairBlock.FACING, Math.floorMod((int) Math.floor(f), 4) == 1 ? fwd.getOpposite() : fwd)
                        : Blocks.AIR.defaultBlockState();
            } else if (h == HIGH - 1 && pierLine) {
                // the roof's trusses: a tie beam across from pier to pier
                state = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, CapitalCastle.sideways(s, 1).getAxis());
            } else if (h == HIGH - 2 && pierLine && Math.abs(c) > 4) {
                state = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.FACING, intoRoom.getOpposite());                  // its brace against the wall
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (!wall) {
            hallFloor(level, s, x, z, f, c, top, fwd, toThrone, intoRoom, box, at, random);
            chandelier(level, s, x, z, f, c, top, across, box, at);
        }
        // the gable roof: rising from the long sides to a ridge down the middle, no gaps where it steps
        int rise = (int) Math.floor(Math.max(0, across));
        int outer = (int) Math.floor(Math.max(0, across - 1));
        int y = height + 1 + rise;
        for (int k = height + 1; k < y; k++) {
            set(level, box, at.set(x, k, z), along < 1 ? stone(s, x, k, z) : k > height + 1 + outer ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                    : Blocks.AIR.defaultBlockState());
        }
        Direction up = across >= 6.5 ? null : (c < 0 ? CapitalCastle.sideways(s, 1) : CapitalCastle.sideways(s, -1));
        set(level, box, at.set(x, y, z), up == null ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                : Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
    }

    private static BlockState hallWall(Capital.Site s, int x, int y, int z, double f, double c, int h, double across, double along,
            boolean pierLine) {
        boolean longSide = across < 1;
        // the great door, at the end away from the throne: four wide, an arch over it
        if (!longSide && f < F0 + 1 && Math.abs(c) < 2) {
            if (h >= 1 && h <= 5) {
                return Blocks.AIR.defaultBlockState();
            }
            if (h == 6) {
                return Math.abs(c) > 1 ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.FACING, CapitalCastle.sideways(s, c > 0 ? 1 : -1)) : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            }
        }
        // the round window over the throne
        if (!longSide && f > F1 - 1 && Math.hypot(h - 10, c) < 2.7) {
            return Math.hypot(h - 10, c) < 2 ? CapitalDraw.along(CapitalCastle.glass(s, x, y, z), CapitalCastle.sideways(s, 1).getAxis())
                    : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        }
        // the throne's high back, gold, set into the wall behind it
        if (!longSide && f > F1 - 1 && Math.abs(c) < 1 && h >= 2 && h <= 4) {
            return (h == 4 ? Blocks.CHISELED_STONE_BRICKS : Blocks.GOLD_BLOCK).defaultBlockState();
        }
        // tall windows down the long sides between the piers, an arch at their heads
        if (longSide && !pierLine && h >= 4 && h <= 9) {
            int inBay = Math.floorMod((int) Math.floor(f), 4);
            if (inBay == 2 && h <= 8 || h >= 5 && h <= 8) {
                return CapitalCastle.glass(s, x, y, z);                   // three wide, the middle a block taller
            }
        }
        if (h == 0) {
            return Blocks.POLISHED_ANDESITE.defaultBlockState();
        }
        return stone(s, x, y, z);
    }

    /** What stands on the great hall's floor at ({@code f}, {@code c}). */
    private static void hallFloor(WorldGenLevel level, Capital.Site s, int x, int z, double f, double c, int top, Direction fwd,
            Direction toThrone, Direction intoRoom, BoundingBox box, BlockPos.MutableBlockPos at, RandomSource random) {
        int y = top + 1;
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 394), 1000L);
        double ac = Math.abs(c);
        // the dais: two steps up across the hall's end, the throne on it
        if (f > 15 && f < 16) {
            set(level, box, at.set(x, y, z), Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toThrone));
            return;
        }
        if (f > 16) {
            set(level, box, at.set(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            if (f > 18 && ac < 1) {
                // the throne itself: its seat, the gold arms either side
                set(level, box, at.set(x, y + 1, z), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toThrone));
            } else if (f > 18 && ac < 2) {
                set(level, box, at.set(x, y + 1, z), Blocks.GOLD_BLOCK.defaultBlockState());
                set(level, box, at.set(x, y + 2, z), Blocks.CANDLE.defaultBlockState());
            } else if (f > 18 && ac > 2 && ac < 3) {
                // the realm's banners either side of it, hanging from the end wall
                set(level, box, at.set(x, y + 4, z), Blocks.WALL_BANNER.red().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, toThrone.getOpposite()));
            } else if (ac < 2) {
                set(level, box, at.set(x, y + 1, z), (ac > 1 ? Blocks.CARPET.yellow() : Blocks.CARPET.red()).defaultBlockState());
            }
            return;
        }
        // the runner down the middle, from the door to the dais: red, its edges gold
        if (ac < 2) {
            set(level, box, at.set(x, y, z), (ac > 1 ? Blocks.CARPET.yellow() : Blocks.CARPET.red()).defaultBlockState());
            if (r > 994) {
                Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
            }
            return;
        }
        // the two banquet tables, their benches either side
        boolean byTable = f > 0 && f < 14;
        if (byTable && ac > 3 && ac < 4) {
            boolean first = f < 1;
            boolean last = f > 13;
            set(level, box, at.set(x, y, z), ModWoods.DESKS.get("dark_oak").get().defaultBlockState()
                    .setValue(DeskBlock.joined(fwd), !last).setValue(DeskBlock.joined(fwd.getOpposite()), !first));
            if (r < 160) {
                CapitalRooms.bottles(level, box, at.set(x, y + 1, z).immutable(), 1 + (int) (r % 3), random);
            } else if (r < 260) {
                set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState()
                        .setValue(BlockStateProperties.CANDLES, 1 + (int) (r % 4)));
            } else if (r < 330) {
                CapitalRooms.tray(level, box, at.set(x, y + 1, z).immutable(), random);
            } else if (r < 360) {
                set(level, box, at.set(x, y + 1, z), Blocks.DECORATED_POT.defaultBlockState());
            }
            return;
        }
        if (byTable && (ac > 2 && ac < 3 || ac > 4 && ac < 5)) {
            // the benches either side of the table: long boards on trestles, one dragged out of line here and there
            if (r < 70) {
                set(level, box, at.set(x, y, z), ModWoods.STOOLS.get("dark_oak").get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)));
                return;
            }
            set(level, box, at.set(x, y, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
            return;
        }
        // on the piers, a sconce: out long since
        if (ac > 4 && ac < 5 && Math.floorMod((int) Math.floor(f), 4) == 0) {
            set(level, box, at.set(x, y + 3, z), ModBlocks.WALL_SCONCE.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, intoRoom));
            set(level, box, at.set(x, y + 6, z), Blocks.WALL_BANNER.red().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, intoRoom));
            return;
        }
        if (r > 990) {
            Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
        } else if (r > 978) {
            set(level, box, at.set(x, y, z), ZombieMarkerBlock.of(1, true));    // the household's guard, in their armour still
        }
    }

    /**
     * Over each banquet table, three chandeliers: a chain down from the roof, an iron ring of four arms, a lantern under
     * each and one in the middle - all out.
     */
    private static void chandelier(WorldGenLevel level, Capital.Site s, int x, int z, double f, double c, int top, double across,
            BoundingBox box, BlockPos.MutableBlockPos at) {
        double ac = Math.abs(c);
        int fi = (int) Math.floor(f);
        int ring = top + 9;
        boolean overTable = ac > 3 && ac < 4;
        boolean atLight = fi == 2 || fi == 7 || fi == 12;
        int roof = top + HIGH + (int) Math.floor(Math.max(0, across));
        if (overTable && atLight) {
            BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();
            for (int y = ring + 1; y < roof; y++) {
                if (y == top + HIGH - 1) {
                    continue;                                                  // where it passes a tie beam (none on these lines)
                }
                set(level, box, at.set(x, y, z), chain);
            }
            set(level, box, at.set(x, ring, z), Blocks.IRON_BARS.defaultBlockState());
            set(level, box, at.set(x, ring - 1, z), ModBlocks.UNLIT_LANTERN.get().defaultBlockState().setValue(BlockStateProperties.HANGING, true));
            return;
        }
        // the ring's arms: one along each way, a lantern hanging from each
        boolean armAlong = overTable && (fi == 1 || fi == 3 || fi == 6 || fi == 8 || fi == 11 || fi == 13);
        boolean armAcross = (ac > 2 && ac < 3 || ac > 4 && ac < 5) && atLight;
        if (armAlong || armAcross) {
            Direction.Axis axis = armAlong ? CapitalCastle.forward(s).getAxis() : CapitalCastle.sideways(s, 1).getAxis();
            set(level, box, at.set(x, ring, z), Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, axis));
            set(level, box, at.set(x, ring - 1, z), ModBlocks.UNLIT_LANTERN.get().defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        }
    }

    // --- the castle's chapel ------------------------------------------------------------------------------------------

    /** What stands in the chapel at ({@code f}, {@code c}) ({@code c} across from its middle): the altar end at low {@code f}. */
    static void chapelFloor(WorldGenLevel level, Capital.Site s, int x, int z, double f, double c, int top, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        int y = top + 1;
        double ac = Math.abs(c);
        Direction fwd = CapitalCastle.forward(s);
        Direction toAltar = fwd.getOpposite();
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 396), 1000L);
        if (f < 4) {
            // the altar: a carved table of stone across the end, candles on it, the cross of the chapel's banners behind
            if (ac < 2) {
                set(level, box, at.set(x, y, z), (ac < 1 ? Blocks.CHISELED_STONE_BRICKS : Blocks.POLISHED_ANDESITE).defaultBlockState());
                set(level, box, at.set(x, y + 1, z), ac < 1 ? Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3)
                        : Blocks.CANDLE.defaultBlockState());
            } else if (ac > 2 && ac < 3) {
                set(level, box, at.set(x, y + 2, z), Blocks.WALL_BANNER.white().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, fwd));
            }
            return;
        }
        if (ac < 1) {
            set(level, box, at.set(x, y, z), Blocks.CARPET.red().defaultBlockState());     // the aisle
            return;
        }
        // the pews, every other row, their backs to the door
        if (f > 5 && f < 14 && Math.floorMod((int) Math.floor(f), 2) == 0 && ac < 4) {
            set(level, box, at.set(x, y, z), Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toAltar.getOpposite()));
            if (r < 60) {
                Markers.remains(level, at.set(x, y, z).immutable(), toAltar, random);   // the last at prayer
            }
            return;
        }
        if (r > 992) {
            Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
        }
    }

    // --- the lean-tos along the curtain -------------------------------------------------------------------------------

    /** The lean-tos' three: the kitchen nearest the gate, the barracks, the stables at the back. */
    enum Shed { KITCHEN, BARRACKS, STABLES }

    static Shed shed(double off) {
        return off < 1.95 ? Shed.KITCHEN : off < 2.25 ? Shed.BARRACKS : Shed.STABLES;
    }

    private static double off(Capital.Site s, double x, double z) {
        return Math.abs(Capital.wrap(Math.atan2(z - s.z(), x - s.x()) - (s.gate(0) + Math.PI)));
    }

    private static double in(Capital.Site s, double x, double z) {
        return Math.hypot(x - s.x(), z - s.z()) - (CapitalCastle.CURTAIN - 7);
    }

    private static Direction out(Capital.Site s, double x, double z) {
        return Direction.getApproximateNearest(x - s.x(), 0, z - s.z());
    }

    /** Whether column ({@code x}, {@code z}) is a barracks bed's head: by the wall, every other along, its foot the same way out. */
    private static boolean bedHead(Capital.Site s, int x, int z) {
        double cx = x + 0.5;
        double cz = z + 0.5;
        double in = in(s, cx, cz);
        if (in < 5.6 || in >= 7 || shed(off(s, cx, cz)) != Shed.BARRACKS) {
            return false;
        }
        Direction o = out(s, cx, cz);
        double footX = cx - o.getStepX();
        double footZ = cz - o.getStepZ();
        int along = (int) Math.floor(Math.atan2(cz - s.z(), cx - s.x()) * CapitalCastle.CURTAIN / 2);
        return Math.floorMod(along, 2) == 0 && out(s, footX, footZ) == o && in(s, footX, footZ) >= 4.5 && shed(off(s, footX, footZ)) == Shed.BARRACKS;
    }

    /**
     * Inside a lean-to at ({@code x}, {@code z}), {@code in} from its front: the kitchen with its ovens along the wall and
     * its tables, the barracks with beds in a row and chests at their feet, the stables with stalls and hay; plank
     * partitions between the three, a doorway through each.
     */
    static void leanToInside(WorldGenLevel level, Capital.Site s, int x, int z, double in, int top, BoundingBox box, BlockPos.MutableBlockPos at,
            RandomSource random) {
        double cx = x + 0.5;
        double cz = z + 0.5;
        double off = off(s, cx, cz);
        double r = Math.hypot(cx - s.x(), cz - s.z());
        int y = top + 1;
        int height = top + 4 + (int) Math.floor(in * 0.6);
        // the partitions
        for (double edge : new double[] {1.95, 2.25}) {
            if (Math.abs(off - edge) * r < 0.5) {
                for (int k = y; k < height; k++) {
                    boolean doorway = in >= 2 && in < 4 && k <= y + 1;
                    set(level, box, at.set(x, k, z), doorway ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
                return;
            }
        }
        Direction o = out(s, cx, cz);
        Direction inward = o.getOpposite();
        int along = (int) Math.floor(Math.atan2(cz - s.z(), cx - s.x()) * CapitalCastle.CURTAIN / 2);
        long h = Math.floorMod(Town.hash(s.seed(), x, z, 357), 1000L);
        switch (shed(off)) {
            case KITCHEN -> {
                if (in >= 6) {
                    BlockState oven = (Math.floorMod(along, 3) == 0 ? Blocks.SMOKER : Math.floorMod(along, 3) == 1 ? Blocks.FURNACE : Blocks.BARREL)
                            .defaultBlockState();
                    if (oven.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                        oven = oven.setValue(BlockStateProperties.HORIZONTAL_FACING, inward);
                    }
                    set(level, box, at.set(x, y, z), oven);
                    if (Math.floorMod(along, 3) == 1) {
                        set(level, box, at.set(x, y + 1, z), dev.nookworks.registry.ModCooking.IRON_POT.get().defaultBlockState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, inward));
                    }
                } else if (in >= 3 && in < 4) {
                    set(level, box, at.set(x, y, z), ModWoods.DESKS.get("spruce").get().defaultBlockState()
                            .setValue(DeskBlock.joined(o.getClockWise()), true).setValue(DeskBlock.joined(o.getCounterClockWise()), true));
                    if (h < 250) {
                        CapitalRooms.tray(level, box, at.set(x, y + 1, z).immutable(), random);
                    } else if (h < 400) {
                        CapitalRooms.bottles(level, box, at.set(x, y + 1, z).immutable(), 1 + (int) (h % 3), random);
                    }
                } else if (in >= 4.5 && in < 5.5 && h < 120) {
                    set(level, box, at.set(x, y, z), (h < 60 ? Blocks.CAULDRON : dev.nookworks.registry.ModBlocks.CRATE.get()).defaultBlockState());
                }
            }
            case BARRACKS -> {
                if (bedHead(s, x, z)) {
                    set(level, box, at.set(x, y, z), Blocks.BED.gray().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, o)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                } else if (bedHead(s, x + o.getStepX(), z + o.getStepZ()) && out(s, cx + o.getStepX(), cz + o.getStepZ()) == o) {
                    set(level, box, at.set(x, y, z), Blocks.BED.gray().defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, o)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                    if (h < 120) {
                        Markers.remains(level, at.set(x, y, z).immutable(), o, random);      // died in his bed
                    }
                } else if (in >= 3.5 && in < 4.5 && Math.floorMod(along, 2) == 0 && h < 600) {
                    CapitalDraw.container(level, box, at.set(x, y, z), Blocks.CHEST, h < 200 ? "weapon" : "general", Town.hash(s.seed(), x, y, z));
                } else if (in >= 6 && h < 500) {
                    set(level, box, at.set(x, y + 1, z), ModBlocks.WALL_SCONCE.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, inward));
                }
            }
            case STABLES -> {
                if (in >= 3 && Math.floorMod(along, 3) == 0) {
                    set(level, box, at.set(x, y, z), Blocks.SPRUCE_FENCE.defaultBlockState());   // the stalls' rails
                } else if (in >= 6) {
                    set(level, box, at.set(x, y, z), (h < 300 ? Blocks.COMPOSTER : Blocks.HAY_BLOCK).defaultBlockState());
                } else if (in >= 3 && h < 220) {
                    set(level, box, at.set(x, y, z), h < 40 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.CARPET.yellow().defaultBlockState());   // straw
                } else if (in < 3 && h < 30) {
                    set(level, box, at.set(x, y, z), Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 1));
                }
            }
        }
    }

    // --- the keep -------------------------------------------------------------------------------------------------

    /**
     * What every storey of the keep shares, at its column ({@code kf}, {@code kl}) from the keep's middle: panelling in oak
     * a block high round the walls, a beam under each floor, a sconce between the windows; the rooms of state hung with
     * banners. {@code edge} how far in from the keep's outside the column is.
     */
    static void keepDress(WorldGenLevel level, Capital.Site s, int x, int z, double kf, double kl, double edge, int top, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        Direction fwd = CapitalCastle.forward(s);
        for (int storey = 0; storey < CapitalCastle.KEEP_STOREYS; storey++) {
            int floor = top + storey * CapitalCastle.KEEP_STOREY;
            // the beams under the floor above, across the keep
            int fk = (int) Math.floor(kf);
            if (fk == -5 || fk == 4) {
                CapitalFacades.placeIfAir(level, box, at.set(x, floor + CapitalCastle.KEEP_STOREY - 1, z).immutable(),
                        Blocks.DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, CapitalCastle.sideways(s, 1).getAxis()));
            }
            if (edge >= 3) {
                continue;
            }
            // by the wall: which wall
            Direction wall;
            if (Math.abs(Math.abs(kf) - Math.abs(kl)) < 1) {
                continue;                                                      // a corner
            } else if (Math.abs(kf) > Math.abs(kl)) {
                wall = kf > 0 ? fwd : fwd.getOpposite();
            } else {
                wall = kl > 0 ? CapitalCastle.sideways(s, 1) : CapitalCastle.sideways(s, -1);
            }
            Direction into = wall.getOpposite();
            int along = (int) Math.floor(Math.abs(kf) > Math.abs(kl) ? kl : kf);
            // a sconce between the windows (they are every fourth along, at 1)
            if (Math.floorMod(along, 4) == 3) {
                CapitalFacades.placeIfAir(level, box, at.set(x, floor + 3, z).immutable(), ModBlocks.WALL_SCONCE.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, into));
            }
            // the rooms of state - the chancery, the court's, the chapel, the king's - hung with banners between
            if (storey >= 3 && Math.floorMod(along, 8) == 5) {
                Block banner = storey == 5 ? Blocks.WALL_BANNER.white() : storey == 6 ? Blocks.WALL_BANNER.purple() : Blocks.WALL_BANNER.red();
                CapitalFacades.placeIfAir(level, box, at.set(x, floor + 4, z).immutable(), banner.defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, into));
            }
        }
    }

    /** The way from the keep's wall into the room at ({@code kf}, {@code kl}), by the wall; null in a corner. */
    static Direction into(Capital.Site s, double kf, double kl) {
        Direction fwd = CapitalCastle.forward(s);
        if (Math.abs(Math.abs(kf) - Math.abs(kl)) < 1) {
            return null;
        }
        if (Math.abs(kf) > Math.abs(kl)) {
            return kf > 0 ? fwd.getOpposite() : fwd;
        }
        return kl > 0 ? CapitalCastle.sideways(s, -1) : CapitalCastle.sideways(s, 1);
    }

    /** The keep's panelling: what its wall's inner face is at {@code inStorey} of a storey, or null for the stone. */
    static BlockState panel(int inStorey) {
        return inStorey == 1 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : null;
    }

    private static BlockState stone(Capital.Site s, int x, int y, int z) {
        return CapitalHouses.weather(s, Blocks.STONE_BRICKS, x, y, z, false).defaultBlockState();
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalCastleRooms() {}
}
