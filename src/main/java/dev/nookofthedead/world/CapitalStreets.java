package dev.nookofthedead.world;

import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The capital's working life, left where it stopped ({@link Capital}): the market's stalls round the square by the council's
 * hall - counters, their awnings in the guilds' colours, what was left to sell; the cranes along the quays, their jibs out
 * over the water and a load still on the chain; boats tied up below them, and the posters on the walls. Each from the
 * plan alone, so a stall or a crane across a chunk's edge is whole from both sides.
 */
final class CapitalStreets {
    /** How far from the market's middle the stalls stand. */
    static final double MARKET = 17;
    /** The stalls' grid. */
    static final int STALL = 7;

    /**
     * A market stall's column at ({@code x}, {@code z}) on the square, {@code y} its paving, if one stands there; whether it
     * did. A stall is four by two in a seven-square cell: posts at its corners, a counter of two desks at the front, a barrel
     * and a crate at the back, a wool awning over it.
     */
    static boolean stall(WorldGenLevel level, Capital.Site s, int x, int y, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        double[] m = CapitalLandmarks.marketPoint(s);
        double d = Math.hypot(x + 0.5 - m[0], z + 0.5 - m[1]);
        if (d > MARKET || d < 5) {
            return false;                                                   // its middle kept clear for the well and the notices
        }
        int ox = (int) Math.floor(m[0]);
        int oz = (int) Math.floor(m[1]);
        int ci = Math.floorDiv(x - ox, STALL);
        int cj = Math.floorDiv(z - oz, STALL);
        long h = Town.hash(s.seed(), ci, cj, 430);
        if (Math.floorMod(h, 100L) >= 75) {
            return false;
        }
        int u = Math.floorMod(x - ox, STALL) - 1;                           // 0..3 along the stall
        int v = Math.floorMod(z - oz, STALL) - 2;                           // 0 the front, 1 the back
        if (u < 0 || u > 3 || v < 0 || v > 1) {
            return false;
        }
        Direction front = Direction.NORTH;                                  // its counter on the cell's north side
        boolean post = u == 0 || u == 3;
        boolean fallen = Math.floorMod(h >> 8, 100L) < 25;                  // its awning come down
        Block wool = switch ((int) Math.floorMod(h >> 12, 5L)) {
            case 0 -> Blocks.WOOL.red();
            case 1 -> Blocks.WOOL.yellow();
            case 2 -> Blocks.WOOL.green();
            case 3 -> Blocks.WOOL.blue();
            default -> Blocks.WOOL.white();
        };
        if (post) {
            set(level, box, at.set(x, y + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());
            set(level, box, at.set(x, y + 2, z), Blocks.SPRUCE_FENCE.defaultBlockState());
        } else if (v == 0) {
            set(level, box, at.set(x, y + 1, z), ModWoods.DESKS.get("spruce").get().defaultBlockState()
                    .setValue(DeskBlock.joined(Direction.EAST), u == 1).setValue(DeskBlock.joined(Direction.WEST), u == 2));
            RandomSource random = RandomSource.create(h * 31 + u);
            long k = Math.floorMod(h >> 16, 4L);
            if (k == 0) {
                CapitalRooms.tray(level, box, at.set(x, y + 2, z).immutable(), random);
            } else if (k == 1) {
                CapitalRooms.bottles(level, box, at.set(x, y + 2, z).immutable(), 1 + random.nextInt(4), random);
            } else if (k == 2 && u == 1) {
                set(level, box, at.set(x, y + 2, z), Blocks.DECORATED_POT.defaultBlockState());
            }
        } else {
            boolean barrel = u == 1;
            if (Math.floorMod(h >> 20, 3L) == 0) {
                CapitalDraw.container(level, box, at.set(x, y + 1, z), barrel ? Blocks.BARREL : ModBlocks.CRATE.get(),
                        Math.floorMod(h >> 24, 2L) == 0 ? "food" : "general", Town.hash(s.seed(), x, y, z));
            } else {
                set(level, box, at.set(x, y + 1, z), (barrel ? Blocks.BARREL : ModBlocks.CRATE.get()).defaultBlockState());
            }
        }
        if (fallen) {
            if (!post && v == 1) {
                set(level, box, at.set(x, y + 2, z), carpet(wool).defaultBlockState());   // the cloth fallen on the goods
            }
        } else {
            set(level, box, at.set(x, y + 3, z), wool.defaultBlockState());
        }
        return true;
    }

    private static Block carpet(Block wool) {
        return wool == Blocks.WOOL.red() ? Blocks.CARPET.red() : wool == Blocks.WOOL.yellow() ? Blocks.CARPET.yellow()
                : wool == Blocks.WOOL.green() ? Blocks.CARPET.green() : wool == Blocks.WOOL.blue() ? Blocks.CARPET.blue() : Blocks.CARPET.white();
    }

    // --- the quays ----------------------------------------------------------------------------------------------------

    /** Where along the river (its {@code u}) a crane stands, and on which bank: {u0, side}, or null for none in this slot. */
    private static double[] crane(Capital.Site s, double u) {
        int i = (int) Math.floor(u / 47.0);
        long h = Town.hash(s.seed(), i, 0, 431);
        if (Math.floorMod(h, 100L) >= 60) {
            return null;
        }
        double u0 = i * 47 + 12 + Math.floorMod(h >> 8, 23L);
        if (Capital.crossing(s, u0) < 6 || Math.abs(u0) < 20) {
            return null;                                                    // not by a bridge
        }
        return new double[] {u0, (h >> 16 & 1) == 0 ? 1 : -1};
    }

    private static double u(Capital.Site s, int x, int z) {
        return (x + 0.5 - s.x()) * s.ax() + (z + 0.5 - s.z()) * s.az();
    }

    /** The crane's jib height: over the quay, high enough to swing a load onto a boat. */
    private static int jib() {
        return Capital.QUAY + 6;
    }

    /** At a quay's edge, a crane's post if one stands there; whether it did. */
    static boolean cranePost(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        double[] cr = crane(s, u(s, x, z));
        if (cr == null || Math.abs(u(s, x, z) - cr[0]) >= 0.5 || Math.signum(c.river()) != cr[1]) {
            return false;
        }
        for (int y = c.ground() + 1; y <= jib(); y++) {
            set(level, box, at.set(x, y, z), Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        set(level, box, at.set(x, jib() + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
        // its windlass at the foot
        set(level, box, at.set(x, c.ground() + 1, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
        return true;
    }

    /** Over and on the water by the quays: a crane's jib with its load, boats tied up below the quay. */
    static void onWater(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, long r, BoundingBox box, BlockPos.MutableBlockPos at) {
        double u = u(s, x, z);
        double fromQuay = Capital.RIVER_HALF - Math.abs(c.river());       // 0 at the quay's edge
        double[] cr = crane(s, u);
        if (cr != null && Math.abs(u - cr[0]) < 0.5 && Math.signum(c.river()) == cr[1] && fromQuay < 3) {
            Direction.Axis across = Math.abs(s.az()) > Math.abs(s.ax()) ? Direction.Axis.X : Direction.Axis.Z;
            set(level, box, at.set(x, jib(), z), Blocks.DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, across));
            if (fromQuay >= 2) {
                // the load still on its chain
                set(level, box, at.set(x, jib() - 1, z), Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                set(level, box, at.set(x, jib() - 2, z), Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                set(level, box, at.set(x, jib() - 3, z), (Math.floorMod(Town.hash(s.seed(), x, z, 432), 2L) == 0 ? Blocks.BARREL
                        : ModBlocks.CRATE.get()).defaultBlockState());
            }
            return;
        }
        // a boat tied up along the quay, now and then
        if (fromQuay >= 1 && fromQuay < 2 && r < 5 && box.isInside(at.set(x, Capital.WATER, z))) {
            var boat = (r % 2 == 0 ? net.minecraft.world.entity.EntityTypes.SPRUCE_BOAT : net.minecraft.world.entity.EntityTypes.OAK_BOAT)
                    .create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (boat != null) {
                float yaw = Math.abs(s.az()) > Math.abs(s.ax()) ? 0 : 90;
                boat.snapTo(x + 0.5, Capital.WATER + 0.9, z + 0.5, yaw, 0.0F);
                level.addFreshEntity(boat);
            }
        }
    }

    // --- the posters ---------------------------------------------------------------------------------------------------

    /**
     * On the wall's inner face either side of each gate, the posters they put up when the gates were shut: the king's
     * portrait and the order to keep out; by the north gate, where the fever came in, the warning of it; by the south
     * gate, the last way out, the king's face again with the word someone wrote over it.
     */
    static void gatePoster(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int y, int z, BoundingBox box) {
        BlockPos pos = new BlockPos(x, y + 3, z);
        if (!box.isInside(pos)) {
            return;
        }
        for (int k = 0; k < 4; k++) {
            for (int side = -1; side <= 1; side += 2) {
                double anchor = s.gate(k) + side * 0.07;
                double ax = s.x() + Math.cos(anchor) * (Capital.WALL - 0.5);
                double az = s.z() + Math.sin(anchor) * (Capital.WALL - 0.5);
                if ((int) Math.floor(ax) != x || (int) Math.floor(az) != z) {
                    continue;
                }
                String design = k == 0 ? (side > 0 ? "infected_area" : "do_not_enter") : k == 2 ? (side > 0 ? "the_king" : "tyrant")
                        : side > 0 ? "the_king" : "do_not_enter";
                Direction in = Direction.getApproximateNearest(-Math.cos(anchor), 0, -Math.sin(anchor));
                var poster = new dev.nookworks.poster.PosterEntity(level.getLevel(), pos, in, dev.nookworks.Nookworks.id(design));
                level.addFreshEntity(poster);
                return;
            }
        }
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalStreets() {}
}
