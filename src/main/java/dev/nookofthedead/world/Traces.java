package dev.nookofthedead.world;

import org.jspecify.annotations.Nullable;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the people did when it came, and how it ended, left lying about the town (the user, 2026-10-17, picked from a survey
 * of the films, games and photographs): never told, only there to be seen.
 * <ul>
 * <li>{@link #flight}: the carts of those who tried to get out, left on the main streets - more of them the further out.</li>
 * <li>{@link #barricade}: lines across the streets - fences, barrels, hay, sandbags of mud - pushed through somewhere.</li>
 * <li>{@link #quarantined}: now and then a whole block walled in planks, more of the dead and more left inside.</li>
 * <li>{@link #graves}: a yard made a burial ground, the first rows marked, the later ones less, the last left open.</li>
 * <li>{@link #canal}: a waterway where no street runs, boats left in it, its footbridge broken.</li>
 * <li>{@link #ruin}: a building fallen in at one corner, or a tree grown up through its roof.</li>
 * <li>{@link #burning}: where fire went through, a band of burnt houses and scorched ground, not one house here and there.</li>
 * </ul>
 * All from the seed and the place, the same from every chunk.
 */
public final class Traces {
    private Traces() {}

    // --- where a street column is -----------------------------------------------------------------------------------------

    /**
     * The street a column lies on, if one and only one: {northSouth (1 or 0), its line, along it, across it (0 at its
     * first column), its width}; null at a crossing or off every street.
     */
    static int @Nullable [] lane(long seed, int x, int z) {
        int i = Math.floorDiv(x + Town.WIDE_HALF + Town.JOG, Town.BLOCK);
        int[] v = Town.band(seed, true, i, z);
        boolean onV = x >= v[0] && x < v[1];
        int k = Math.floorDiv(z + Town.WIDE_HALF + Town.JOG, Town.BLOCK);
        int[] h = Town.band(seed, false, k, x);
        boolean onH = z >= h[0] && z < h[1];
        if (onV == onH) {
            return null;
        }
        return onV ? new int[] {1, i, z, x - v[0], v[1] - v[0]} : new int[] {0, k, x, z - h[0], h[1] - h[0]};
    }

    private static District district(long seed, boolean northSouth, int line, int along) {
        int at = Math.floorDiv(along, Town.BLOCK);
        return northSouth ? District.of(seed, line, at) : District.of(seed, at, line);
    }

    // --- the carts of those who left -------------------------------------------------------------------------------------

    /** How many of a quarter's cart places hold a cart: none in the centre, most on the farm lanes. */
    private static double flight(District district) {
        return switch (district) {
            case CENTRE -> 0.03;
            case OLD_TOWN -> 0.06;
            case RESIDENTIAL, SETTLEMENT -> 0.12;
            case OUTSKIRTS, WASTE -> 0.22;
            case FARM -> 0.28;
            case WILD -> 0.0;
            case DESERT -> 0.2;                                         // stranded, the sand up their wheels
        };
    }

    private static final int CART = 3;

    /**
     * On a main street (one that goes the whole way), what of a left cart stands at ({@code x}, {@code z}) on ground {@code y}:
     * carts three long in the lanes either side of the middle, bunched where the jam was (the user, 2026-10-17: "逃げ遅れ
     * の列"), each standing on its wheels (in the columns either side of its back). Draws it; whether it did.
     */
    static boolean flight(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y) {
        int[] lane = lane(seed, x, z);
        if (lane == null || lane[4] < 6 || !Town.through(seed, lane[0] == 1, lane[1])) {
            return false;
        }
        int across = lane[3];
        if (across == 1 || across == lane[4] - 2) {                  // two lanes, one in from each edge
            long[] cart = cartAt(seed, lane, across, lane[2]);
            if (cart == null) {
                return false;
            }
            cart(level, at, x, y, z, (int) cart[0], cart[1]);
            return true;
        }
        for (int side = -1; side <= 1; side += 2) {                    // beside one: a wheel at its back
            int next = across - side;
            if (next != 1 && next != lane[4] - 2) {
                continue;
            }
            long[] cart = cartAt(seed, lane, next, lane[2]);
            if (cart != null && cart[0] == CART - 1 && upright(cart[1])) {
                Direction away = lane[0] == 1 ? (side > 0 ? Direction.EAST : Direction.WEST) : (side > 0 ? Direction.SOUTH : Direction.NORTH);
                level.setBlock(at.set(x, y + 1, z), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                        .setValue(TrapDoorBlock.FACING, away), 2);
                return true;
            }
        }
        return false;
    }

    /**
     * The cart in lane {@code across} of the street {@code lane} whose part stands at {@code along}, if one does: {its part (0
     * the shafts .. CART - 1 its back), its hash}. Only where all of it stands level - its columns and its wheels' on the
     * street, none of them a half step, all at one height - so it is the whole cart or none, from whichever chunk (the user,
     * 2026-10-18: a chest and a slab on their own in the air, the rest of the cart left off at a step).
     */
    private static long @Nullable [] cartAt(long seed, int[] lane, int across, int along) {
        boolean ns = lane[0] == 1;
        for (int k = 0; k < CART; k++) {
            int start = along - k;
            if (Math.floorMod(start, CART + 1) != 0) {
                continue;
            }
            District district = district(seed, ns, lane[1], start);
            double share = flight(district) * (0.4 + 1.6 * Town.noise(seed, lane[1] * 31, start, 40.0, 181));
            long h = Town.hash(seed, lane[1] * 4L + across, start, ns ? 182 : 183);
            if (Math.floorMod(h, 1000) >= share * 1000) {
                return null;
            }
            Double level = null;
            for (int part = 0; part < CART + 2; part++) {               // its three columns, then its wheels'
                int a = part < CART ? across : across + (part == CART ? -1 : 1);
                int s = part < CART ? start + part : start + CART - 1;
                int first = Town.band(seed, ns, lane[1], s)[0];         // the street's first column on that row
                int x = ns ? first + a : s;
                int z = ns ? s : first + a;
                int[] there = lane(seed, x, z);
                if (there == null || there[0] != lane[0] || there[1] != lane[1]) {
                    return null;                                         // off the street, or into a crossing
                }
                double height = Town.streetHeight(seed, x, z);
                if (height != Math.floor(height) || level != null && height != level) {
                    return null;                                         // a half step, or a step between
                }
                level = height;
            }
            return new long[] {k, h};
        }
        return null;
    }

    /**
     * For looking the town over (/nzw find cart): the back of the nearest standing cart to ({@code x}, {@code z}) within
     * {@code reach}, and the way its street runs: {x, z, 1 if north-south}; null if none.
     */
    public static int @Nullable [] nearestCart(long seed, int x, int z, int reach) {
        for (int r = 0; r <= reach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int[] lane = lane(seed, x + dx, z + dz);
                    if (lane == null || lane[4] < 6 || !Town.through(seed, lane[0] == 1, lane[1])
                            || lane[3] != 1 && lane[3] != lane[4] - 2) {
                        continue;
                    }
                    long[] cart = cartAt(seed, lane, lane[3], lane[2]);
                    if (cart != null && cart[0] == CART - 1 && upright(cart[1])) {
                        return new int[] {x + dx, z + dz, lane[0]};
                    }
                }
            }
        }
        return null;
    }

    /** Whether the cart of hash {@code h} still stands (three in four), not lying over with its load in the street. */
    private static boolean upright(long h) {
        return Math.floorMod(h >> 12, 4L) != 0;
    }

    /** Part {@code k} of a cart: the shafts, then its bed on its wheels with what was loaded on it, some of it fallen. */
    private static void cart(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int y, int z, int k, long h) {
        int tipped = (int) Math.floorMod(h >> 12, 4L);                 // one in four lies over, its load in the street
        if (k == 0) {
            if (tipped != 0) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            }
            return;
        }
        if (tipped == 0) {
            level.setBlock(at.set(x, y + 1, z), (k == 1 ? Blocks.SPRUCE_TRAPDOOR : Blocks.BARREL).defaultBlockState(), 2);
            return;
        }
        level.setBlock(at.set(x, y + 1, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        int load = (int) Math.floorMod(h >> (16 + 4 * k), 8L);
        BlockState on = switch (load) {
            case 0, 1 -> Blocks.BARREL.defaultBlockState();
            case 2 -> Blocks.HAY_BLOCK.defaultBlockState();
            case 3 -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue((int) (h >>> 40) & 3));
            case 4 -> Blocks.WOOL.white().defaultBlockState();
            case 5 -> Blocks.CARPET.brown().defaultBlockState();
            default -> null;
        };
        if (on == null) {
            return;
        }
        level.setBlock(at.set(x, y + 2, z), on, 2);
        if (on.is(Blocks.CHEST) || on.is(Blocks.BARREL)) {
            if (level.getBlockEntity(at) instanceof RandomizableContainer box && (h >>> 50) % 3 == 0) {
                box.setLootTable(Markers.table((h >>> 52) % 2 == 0 ? "supplies" : "food"), h);
            }
        }
    }

    // --- barricades across the streets -----------------------------------------------------------------------------------

    /** How often a stretch of a quarter's streets is barricaded across. */
    private static double barricades(District district) {
        return switch (district) {
            case CENTRE -> 0.06;
            case OLD_TOWN, RESIDENTIAL -> 0.1;
            case OUTSKIRTS, SETTLEMENT -> 0.16;
            case WASTE -> 0.12;
            default -> 0.0;
        };
    }

    /** Where along it a stretch's barricade stands (the street's line at {@code line}, the stretch after crossing {@code stretch}), or -1. */
    public static int barricadeAt(long seed, boolean northSouth, int line, int stretch) {
        long h = Town.hash(seed, line, stretch, northSouth ? 184 : 185);
        if (Town.line(seed, northSouth, line, stretch) != Town.Ground.STREET
                || Math.floorMod(h, 1000) >= barricades(district(seed, northSouth, line, stretch * Town.BLOCK)) * 1000) {
            return -1;
        }
        return stretch * Town.BLOCK + 22 + (int) Math.floorMod(h >> 10, 36L);
    }

    /**
     * A barricade across the street at ({@code x}, {@code z}), on a stretch picked for one: a line across it somewhere
     * between its crossings - fences, barrels, hay, mud bricks for sandbags, carts on their sides - and a gap pushed
     * through it (the user, 2026-10-17: "封鎖線と検問所"). Draws it; whether it did.
     */
    static boolean barricade(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y) {
        int[] lane = lane(seed, x, z);
        if (lane == null) {
            return false;
        }
        boolean ns = lane[0] == 1;
        int stretch = Math.floorDiv(lane[2], Town.BLOCK);
        long h = Town.hash(seed, lane[1], stretch, ns ? 184 : 185);
        if (Math.floorMod(h, 1000) >= barricades(district(seed, ns, lane[1], lane[2])) * 1000) {
            return false;
        }
        int rel = lane[2] - stretch * Town.BLOCK;
        int line = 22 + (int) Math.floorMod(h >> 10, 36L);
        if (rel != line && !(rel == line + 1 && Math.floorMod(Town.hash(seed, x, z, 186), 3L) == 0)) {
            return false;
        }
        int gap = (int) Math.floorMod(h >> 20, (long) Math.max(1, lane[4] - 2));
        if (lane[3] >= gap && lane[3] < gap + 2 + (int) ((h >> 30) & 1)) {
            if (Math.floorMod(Town.hash(seed, x, z, 187), 2L) == 0) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);   // what was pushed aside
            }
            return true;
        }
        // what they had to hand: fences, barrels, hay, mud for sandbags; and scrap (0.3, Nookworks 1.1) - bales of it,
        // patched sheet, corrugated sheets stood across the street two high and left to rust
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 188), 12L);
        long rust = Town.hash(seed, x, z, 193);
        net.minecraft.core.Direction across = ns ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.EAST;
        if (r == 9 || r == 10) {
            for (int up = 1; up <= 2; up++) {
                level.setBlock(at.set(x, y + up, z), Props.sheet(rust >> up, across, across.getAxis(), true), 2);
            }
            return true;
        }
        BlockState low = switch (r) {
            case 0, 1 -> Blocks.OAK_FENCE.defaultBlockState();
            case 2 -> Props.bale(rust);
            case 3 -> Blocks.BARREL.defaultBlockState();
            case 4 -> Blocks.HAY_BLOCK.defaultBlockState();
            case 5, 6 -> Blocks.PACKED_MUD.defaultBlockState();
            case 7 -> Blocks.MUD_BRICKS.defaultBlockState();
            case 8 -> Props.patched();
            default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
        };
        level.setBlock(at.set(x, y + 1, z), low, 2);
        if (r <= 1 || (r == 5 || r == 6 || r == 7) && Math.floorMod(Town.hash(seed, x, z, 189), 2L) == 0) {
            level.setBlock(at.set(x, y + 2, z), (r <= 1 ? Blocks.OAK_FENCE : Blocks.MUD_BRICK_SLAB).defaultBlockState(), 2);
        } else if ((r == 2 || r == 8) && Math.floorMod(Town.hash(seed, x, z, 189), 2L) == 0) {
            level.setBlock(at.set(x, y + 2, z), Props.sheet(rust >> 3, across, across.getAxis(), true), 2);
        }
        return true;
    }

    // --- a block walled off ----------------------------------------------------------------------------------------------

    /** Whether block ({@code bx}, {@code bz}) was walled in: one in thirty of the town's houses, never the centre's. */
    public static boolean quarantined(long seed, int bx, int bz) {
        District district = District.of(seed, bx, bz);
        if (district == District.CENTRE || district == District.FARM || district == District.WILD || Institute.at(seed, bx, bz)) {
            return false;
        }
        return Math.floorMod(Town.hash(seed, bx, bz, 190), 1000) < 33;
    }

    /**
     * The wall round a block walled in, on the street's edge beside it: planks two high with fence over, a gate here and
     * there and gaps where it was broken. Draws it; whether it did.
     */
    static boolean wall(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y) {
        int[] lane = lane(seed, x, z);
        if (lane == null || lane[3] != 0 && lane[3] != lane[4] - 1) {
            return false;
        }
        boolean ns = lane[0] == 1;
        int at0 = Math.floorDiv(lane[2], Town.BLOCK);
        int side = lane[3] == 0 ? lane[1] - 1 : lane[1];              // the block the edge is beside
        boolean in = ns ? quarantined(seed, side, at0) : quarantined(seed, at0, side);
        if (!in) {
            return false;
        }
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 191), 100L);
        double broken = Town.noise(seed, x, z, 6.0, 192);
        if (broken > 0.78) {
            if (r < 40) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
            }
            return true;
        }
        if (r >= 35 && r < 60) {                                       // a run of corrugated sheet (0.3, Nookworks 1.1), rusting
            net.minecraft.core.Direction out = ns ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.SOUTH;
            long rust = Town.hash(seed, x, z, 194);
            for (int up = 1; up <= (broken > 0.68 ? 2 : 3); up++) {
                level.setBlock(at.set(x, y + up, z), Props.sheet(rust >> up, out, out.getAxis(), true), 2);
            }
            return true;
        }
        Block plank = r < 35 ? Blocks.SPRUCE_PLANKS : r < 85 ? Blocks.OAK_PLANKS : Blocks.STRIPPED_SPRUCE_LOG;
        level.setBlock(at.set(x, y + 1, z), plank.defaultBlockState(), 2);
        level.setBlock(at.set(x, y + 2, z), (broken > 0.68 ? Blocks.OAK_FENCE : plank).defaultBlockState(), 2);
        if (broken <= 0.68) {
            level.setBlock(at.set(x, y + 3, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
        }
        return true;
    }

    /** Inside a block walled in, on its open ground: more of the dead standing, and more left behind. */
    static void inside(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y) {
        if (!quarantined(seed, Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK))) {
            return;
        }
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 193), 1000L);
        if (r < 5) {
            level.setBlock(at.set(x, y + 1, z), ZombieMarkerBlock.of(1 + r % 3, false), 2);
        } else if (r < 8) {
            level.setBlock(at.set(x, y + 1, z), Blocks.BARREL.defaultBlockState(), 2);
            if (level.getBlockEntity(at) instanceof RandomizableContainer box) {
                box.setLootTable(Markers.table(r == 5 ? "medical" : r == 6 ? "food" : "supplies"), Town.hash(seed, x, z, 194));
            }
        }
    }

    // --- a yard made a burial ground -------------------------------------------------------------------------------------

    /** Whether a yard is a burial ground: one in three in the town's houses and the settlements. */
    public static boolean graveyard(Town.Lot lot) {
        return Math.floorMod(Town.hash(lot.seed(), 11, 0, 195), 3L) == 0;
    }

    /**
     * A grave's part at ({@code x}, {@code z}) of a burial ground: rows of mounds two long, the first rows with stones at
     * their heads, the next with posts, then none, the last dug and left open (the user, 2026-10-17: "急ごしらえの墓地").
     * Weeds between. Draws it; whether it was a grave.
     */
    static boolean graves(WorldGenLevel level, Town.Lot lot, BlockPos.MutableBlockPos at, int x, int z, int y) {
        int dx = x - lot.corner().getX() - 2;
        int dz = z - lot.corner().getZ() - 2;
        int span = lot.size() - 4;
        if (dx < 0 || dz < 0 || dx >= span || dz >= span) {
            return false;
        }
        int row = dz / 3;
        int col = dx / 2;
        int rows = span / 3;
        if (dx % 2 == 1 || dz % 3 == 2) {
            return false;
        }
        long h = Town.hash(lot.seed(), col, row, 196);
        int filled = rows - 1 - (int) Math.floorMod(lot.seed() >> 8, 2L);   // how far the rows got
        if (row > filled) {
            return false;
        }
        boolean head = dz % 3 == 0;
        if (row == filled && Math.floorMod(h, 3L) != 0) {                // the last row: dug, and left open
            level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(at.set(x, y - 1, z), Blocks.COARSE_DIRT.defaultBlockState(), 2);
            return true;
        }
        if (Math.floorMod(h, 7L) == 0) {
            return false;                                                // a gap in the row
        }
        level.setBlock(at.set(x, y, z), (Math.floorMod(h >> 8, 3L) == 0 ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT).defaultBlockState(), 2);
        if (head) {
            double neat = 1.0 - (double) row / Math.max(1, filled);
            int r = (int) Math.floorMod(h >> 16, 100L);
            if (neat > 0.6 && r < 85) {
                level.setBlock(at.set(x, y + 1, z), (r < 40 ? Blocks.COBBLESTONE_WALL : Blocks.MOSSY_COBBLESTONE_WALL).defaultBlockState(), 2);
            } else if (neat > 0.25 && r < 70) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
            }
        }
        return true;
    }

    // --- canals ----------------------------------------------------------------------------------------------------------

    /**
     * Where no street runs ({@link Town#gone}) in the town, one stretch in three is a canal: its water's level - a block under
     * the lowest of the ground along it - or 0 if ({@code x}, {@code z}) is not in one. Four wide down the middle.
     */
    public static int canal(long seed, int x, int z) {
        if (Capital.owns(seed, x, z)) {
            return 0;
        }
        int i = Math.floorDiv(x + Town.WIDE_HALF + Town.JOG, Town.BLOCK);
        int[] v = Town.band(seed, true, i, z);
        if (x >= v[0] && x < v[1] && Math.abs(2 * x + 1 - v[0] - v[1]) <= 4) {
            int level = canalLevel(seed, true, i, Math.floorDiv(z, Town.BLOCK));
            if (level > 0) {
                return level;
            }
        }
        int k = Math.floorDiv(z + Town.WIDE_HALF + Town.JOG, Town.BLOCK);
        int[] h = Town.band(seed, false, k, x);
        if (z >= h[0] && z < h[1] && Math.abs(2 * z + 1 - h[0] - h[1]) <= 4) {
            return canalLevel(seed, false, k, Math.floorDiv(x, Town.BLOCK));
        }
        return 0;
    }

    /** The water's level of the canal along a stretch, or 0 if it has none. */
    private static int canalLevel(long seed, boolean northSouth, int line, int at) {
        if (Town.line(seed, northSouth, line, at) != Town.Ground.LOT || Math.floorMod(Town.hash(seed, line, at, northSouth ? 197 : 198), 3L) != 0) {
            return 0;
        }
        Region a = northSouth ? Region.of(seed, line - 1, at) : Region.of(seed, at, line - 1);
        Region b = northSouth ? Region.of(seed, line, at) : Region.of(seed, at, line);
        if (a != Region.CITY || b != Region.CITY) {
            return 0;
        }
        double low = Double.MAX_VALUE;
        for (int along = at * Town.BLOCK - Town.BLOCK / 4; along <= (at + 1) * Town.BLOCK + Town.BLOCK / 4; along += 4) {
            int across = line * Town.BLOCK;
            low = Math.min(low, Town.height(seed, northSouth ? across : along, northSouth ? along : across));
        }
        return (int) Math.floor(low) - 1;
    }

    /** Whether a boat was left at ({@code x}, {@code z}) of a canal, and the footbridge's part there if any: drawn. */
    static void onCanal(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int water) {
        int[] lane = lane(seed, x, z);
        if (lane == null) {
            return;
        }
        int rel = Math.floorMod(lane[2], Town.BLOCK);
        if (rel == Town.BLOCK / 2 || rel == Town.BLOCK / 2 + 1) {         // the footbridge, its middle fallen in
            int middle = Math.abs(2 * lane[3] + 1 - lane[4]);
            if (middle > 1) {
                level.setBlock(at.set(x, water + 1, z), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
            }
            return;
        }
        long h = Town.hash(seed, x, z, 199);
        if (Math.floorMod(h, 1000L) < 8 && lane[3] == lane[4] / 2) {
            var boat = net.minecraft.world.entity.EntityTypes.OAK_BOAT.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (boat != null) {
                boat.snapTo(x + 0.5, water + 0.1, z + 0.5, (float) Math.floorMod(h >> 12, 360L), 0.0F);
                level.addFreshEntity(boat);
            }
        }
    }

    // --- buildings fallen in, grown through ------------------------------------------------------------------------------

    /** How a building has gone beyond its {@link Repaint.Wear}: as it is, fallen in at a corner, or a tree up through it. */
    public enum Ruin { STANDING, FALLEN, GROWN }

    /** Which, for {@code lot} in {@code district}: more of both the further out (the photographs' stages, Pripyat's). */
    public static Ruin ruin(Town.Lot lot, District district) {
        if (lot.building().map(id -> Repaint.OWN.contains(id.getPath()) || Vanilla.is(id) || id.equals(Town.INSTITUTE)).orElse(true)) {
            return Ruin.STANDING;
        }
        if (district == District.DESERT) {
            return Ruin.STANDING;                                      // worn to its footings already (Desert.erode)
        }
        int fallen = switch (district) {
            case WASTE -> 14;
            case OUTSKIRTS -> 9;
            case CENTRE -> 2;
            default -> 4;
        };
        int grown = switch (district) {
            case WASTE -> 10;
            case OUTSKIRTS, FARM, SETTLEMENT -> 6;
            case CENTRE -> 1;
            default -> 3;
        };
        int r = (int) Math.floorMod(Town.hash(lot.seed(), 12, 0, 200), 100L);
        return r < fallen ? Ruin.FALLEN : r < fallen + grown ? Ruin.GROWN : Ruin.STANDING;
    }

    /**
     * A building fallen in at one corner ({@link Ruin#FALLEN}): within a ragged reach of the corner, all above the second
     * floor gone - lower where it fell worst. Worked out from the lot alone, so every chunk of it agrees (the review,
     * 2026-10-18: the collapse once read the world, and the signs were set up again where the floor had gone).
     */
    public record Fall(BoundingBox foot, int cx, int cz, double reach, int floor, long seed) {
        double distance(int x, int z) {
            return Math.hypot(x - this.cx, z - this.cz) + (Town.noise(this.seed, x, z, 3.0, 202) - 0.5) * 6.0;
        }

        /** Whether what stood at ({@code x}, {@code y}, {@code z}) came down. */
        public boolean gone(int x, int y, int z) {
            if (x < this.foot.minX() || x > this.foot.maxX() || z < this.foot.minZ() || z > this.foot.maxZ()) {
                return false;
            }
            double d = this.distance(x, z);
            return d <= this.reach && y >= this.floor + 3 + (int) ((d / this.reach) * 4);
        }

        public boolean gone(BlockPos at) {
            return this.gone(at.getX(), at.getY(), at.getZ());
        }
    }

    /** How {@code lot}'s building fell in, if it did ({@link #ruin}). */
    public static @Nullable Fall fall(Town.Lot lot, District district) {
        if (ruin(lot, district) != Ruin.FALLEN) {
            return null;
        }
        BoundingBox foot = lot.footprint();
        long h = Town.hash(lot.seed(), 13, 0, 201);
        return new Fall(foot, (h & 1) == 0 ? foot.minX() : foot.maxX(), (h & 2) == 0 ? foot.minZ() : foot.maxZ(),
                lot.size() * (0.4 + 0.15 * ((h >> 8) & 3) / 3.0), lot.corner().getY(), lot.seed());
    }

    /**
     * What takes away the part of a building that fell, as the template is set down: its blocks there left as air, its
     * things there (frames, stands) not put up at all.
     */
    static StructureProcessor collapse(Fall fall) {
        return new StructureProcessor() {
            @Override
            public StructureTemplate.@Nullable StructureBlockInfo process(net.minecraft.world.level.LevelReader level, BlockPos target, BlockPos reference,
                    StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings,
                    @Nullable StructureTemplate template) {
                return processed != null && fall.gone(processed.pos())
                        ? new StructureTemplate.StructureBlockInfo(processed.pos(), Blocks.AIR.defaultBlockState(), null) : processed;
            }

            @Override
            public StructureTemplate.StructureEntityInfo processEntity(net.minecraft.world.level.LevelReader level, BlockPos seed,
                    StructureTemplate.StructureEntityInfo raw, StructureTemplate.StructureEntityInfo info, StructurePlaceSettings settings,
                    StructureTemplate template) {
                return info == null || fall.gone(info.blockPos) ? null : info;
            }

            @Override
            public MapCodec<? extends StructureProcessor> codec() {
                return MapCodec.unit(this);                               // made in place, never written out
            }
        };
    }

    /**
     * {@code lot}'s building, once placed, gone further as {@link #ruin} has it: its part in {@code box}. (What fell was
     * already left out as it was placed, {@link #collapse}; here, the heap of it on the ground, or the tree.)
     */
    static void ruin(WorldGenLevel level, Town.Lot lot, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            District district) {
        Ruin ruin = ruin(lot, district);
        if (ruin == Ruin.FALLEN) {
            heap(level, lot, Objects.requireNonNull(fall(lot, district)), box);
        } else if (ruin == Ruin.GROWN) {
            growThrough(level, lot, template, origin, settings, box);
        }
    }

    /** The heap of what came down, on the ground where it fell: higher where it fell worst. */
    private static void heap(WorldGenLevel level, Town.Lot lot, Fall fall, BoundingBox box) {
        BoundingBox foot = lot.footprint();
        int floor = lot.corner().getY();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(foot.minX(), box.minX()); x <= Math.min(foot.maxX(), box.maxX()); x++) {
            for (int z = Math.max(foot.minZ(), box.minZ()); z <= Math.min(foot.maxZ(), box.maxZ()); z++) {
                double d = fall.distance(x, z);
                if (d > fall.reach()) {
                    continue;
                }
                double heap = 1.0 - d / fall.reach();
                int r = (int) Math.floorMod(Town.hash(lot.seed(), x, z, 203), 100L);
                if (heap > 0.25 && level.getBlockState(at.set(x, floor + 1, z)).isAir()) {
                    level.setBlock(at, rubble(r).defaultBlockState(), 2);
                    if (heap > 0.55 && level.getBlockState(at.set(x, floor + 2, z)).isAir()) {
                        level.setBlock(at, (r < 50 ? Blocks.COBBLESTONE_SLAB : Blocks.SPRUCE_SLAB).defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static Block rubble(int r) {
        return r < 25 ? Blocks.COBBLESTONE : r < 45 ? Blocks.MOSSY_COBBLESTONE : r < 60 ? Blocks.BRICKS : r < 75 ? Blocks.SPRUCE_PLANKS
                : r < 88 ? Blocks.GRAVEL : Blocks.CRACKED_STONE_BRICKS;
    }

    /**
     * A tree grown up through it: a trunk from the floor up past the roof, its crown spread over the top. How high the roof
     * is there is the template's, not the world's (the review, 2026-10-18: read from the world, a chunk saw its neighbour
     * half made, and the crown came out at two heights).
     */
    private static void growThrough(WorldGenLevel level, Town.Lot lot, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox box) {
        BoundingBox foot = lot.footprint();
        int trees = lot.size() >= Town.M_LOT ? 2 : 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int floor = lot.corner().getY();
        for (int t = 0; t < trees; t++) {
            long h = Town.hash(lot.seed(), t, 0, 204);
            int tx = foot.minX() + 5 + (int) Math.floorMod(h, (long) lot.size() - 10);
            int tz = foot.minZ() + 5 + (int) Math.floorMod(h >> 16, (long) lot.size() - 10);
            int top = Math.max(floor + 1, Town.roof(template, origin, settings, tx, tz));
            int crown = Math.max(top + 3, floor + 8);
            Block log = (h >> 32 & 1) == 0 ? Blocks.OAK_LOG : Blocks.DARK_OAK_LOG;
            if (box.isInside(at.set(tx, floor, tz))) {
                for (int y = floor + 1; y <= crown; y++) {
                    level.setBlock(at.set(tx, y, tz), log.defaultBlockState(), 2);
                }
            }
            BlockState leaves = ((h >> 33 & 1) == 0 ? Blocks.OAK_LEAVES : Blocks.DARK_OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        if (dx * dx + dz * dz + dy * dy * 2 > 11 || !box.isInside(at.set(tx + dx, crown + dy, tz + dz))) {
                            continue;
                        }
                        if (level.getBlockState(at).isAir() && Math.floorMod(Town.hash(lot.seed(), tx + dx, tz + dz * 7 + dy, 205), 5L) != 0) {
                            level.setBlock(at, leaves, 2);
                        }
                    }
                }
            }
        }
    }

    // --- where fire went through ----------------------------------------------------------------------------------------

    /** Whether ({@code x}, {@code z}) lies where a fire went through: long bands of it, a few blocks of houses across. */
    public static boolean burning(long seed, int x, int z) {
        Region region = Region.at(seed, x, z);
        if (region != Region.CITY && region != Region.SETTLEMENT) {
            return false;
        }
        double a = Town.noise(seed, x, z, 150.0, 206);
        return a > 0.8 && Town.noise(seed, x, z, 23.0, 207) > 0.25;
    }

    /** The ground where it burnt: scorched earth and ash, a dead bush, nothing green. Draws it; whether it did. */
    static boolean scorched(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int y) {
        if (!burning(seed, x, z)) {
            return false;
        }
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 208), 100L);
        // charred, in patches as a fire leaves it - dark earth, ash and cinders, not the orange of bare earth (the survey,
        // 2026-10-18: it read as a dug field)
        double n = Town.noise(seed, x, z, 3.5, 209) + (r - 50) / 400.0;
        Block top = n < 0.36 ? Blocks.SOUL_SOIL : n < 0.52 ? Blocks.COARSE_DIRT : n < 0.66 ? Blocks.TUFF : n < 0.8 ? Blocks.GRAVEL
                : Blocks.ROOTED_DIRT;
        level.setBlock(at.set(x, y, z), top.defaultBlockState(), 2);
        if (r > 92 && (top == Blocks.COARSE_DIRT || top == Blocks.ROOTED_DIRT)) {   // only where one can stand (soul soil,
            level.setBlock(at.set(x, y + 1, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);   // tuff, gravel let it drop: the review)
        }
        return true;
    }
}
