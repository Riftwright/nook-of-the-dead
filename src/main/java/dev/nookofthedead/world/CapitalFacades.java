package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What hangs off the capital's houses into the open before them ({@link CapitalHouses}): window boxes and shutters on the
 * upper floors, now and then a balcony with its rail; chains strung across the narrow lanes from house to house, a
 * lantern under some; ivy down the walls the years have had longest. Drawn from the open column's side - the street, the
 * square, the quay - by looking at the walls round it, so whichever chunk the wall is in, what hangs off it is the same.
 */
final class CapitalFacades {
    static void draw(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int z, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        int street = c.ground();
        // across a narrow lane, a chain from wall to wall, a lantern under some
        for (Direction d : new Direction[] {Direction.EAST, Direction.SOUTH}) {
            Capital.Column one = w[a + d.getStepX()][b + d.getStepZ()];
            Capital.Column other = w[a - d.getStepX()][b - d.getStepZ()];
            boolean wallOne = one != null && one.kind() == Capital.Kind.BUILDING;
            boolean wallOther = other != null && other.kind() == Capital.Kind.BUILDING;
            // a lane one or two wide: walls both sides, or a wall and the lane's other column with the wall past it
            int fa = a - 2 * d.getStepX();
            int fb = b - 2 * d.getStepZ();
            boolean twoWide = other != null && other.kind() == Capital.Kind.STREET && fa >= 0 && fb >= 0 && fa < w.length && fb < w.length
                    && w[fa][fb] != null && w[fa][fb].kind() == Capital.Kind.BUILDING;
            if (wallOne && (wallOther || twoWide)) {
                long h = Town.hash(s.seed(), Math.floorDiv(x, 5), Math.floorDiv(z, 5), d == Direction.EAST ? 400 : 401);
                if (Math.floorMod(h, 100L) < 22 && c.upper() <= 0) {
                    int y = street + 7 + (int) Math.floorMod(h >> 8, 4L);
                    BlockState chain = Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, d.getAxis());
                    placeIfAir(level, box, at.set(x, y, z), chain);
                    if (Math.floorMod(h >> 12, 3L) == 0 && (d.getAxis() == Direction.Axis.X ? x : z) % 2 == 0) {
                        placeIfAir(level, box, at.set(x, y - 1, z), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
                    }
                }
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int na = a + d.getStepX();
            int nb = b + d.getStepZ();
            Capital.Column n = w[na][nb];
            if (n == null || n.kind() != Capital.Kind.BUILDING) {
                continue;
            }
            CapitalHouses.House house = CapitalHouses.house(s, n);
            int nx = x + d.getStepX();
            int nz = z + d.getStepZ();
            Direction out = d.getOpposite();                                   // the wall's face, toward this column
            int along = out.getAxis() == Direction.Axis.X ? nz : nx;
            boolean ivy = house.ruin() > 35 && Town.noise(s.seed(), nx, nz, 7.0, 402) > 0.55;
            int standing = CapitalHouses.standing(s, n, house, nx, nz);
            for (int y = house.level() + 1; y < standing; y++) {
                double e = CapitalHouses.edgeAt(n, house, y);
                if (e <= 0 || e >= 1 && n.party() >= 1) {
                    continue;                                                  // no wall of it here, at this height
                }
                // this column is open at y (not the house's own upper floor over a lane)
                if (c.upper() > 0 && y >= CapitalHouses.house(s, c).level() + CapitalHouses.STOREY) {
                    continue;
                }
                int storey = (y - house.level()) / CapitalHouses.STOREY;
                int inStorey = (y - house.level()) % CapitalHouses.STOREY;
                boolean windowCol = Math.floorMod(along + storey, 2) == 0;
                long h = Math.floorMod(Town.hash(s.seed(), nx * 31L + y, nz, 403), 1000L);
                if (storey >= 1) {
                    if (inStorey == 0 && windowCol && h < 200) {
                        // a window box under the window: a slab with a pot on it
                        placeIfAir(level, box, at.set(x, y, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                        placeIfAir(level, box, at.set(x, y + 1, z), pot(h));
                        continue;
                    }
                    if (inStorey == 0 && windowCol && h > 960 && storey >= 2) {
                        // a balcony: its floor and rail
                        placeIfAir(level, box, at.set(x, y, z), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                        placeIfAir(level, box, at.set(x, y + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
                        continue;
                    }
                    if ((inStorey == 1 || inStorey == 2) && !windowCol && h < 330) {
                        // a shutter folded back flat against the wall beside the window
                        BlockState shutter = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, out)
                                .setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, inStorey == 1 ? Half.BOTTOM : Half.TOP);
                        placeIfAir(level, box, at.set(x, y, z), shutter);
                        continue;
                    }
                }
                if (ivy && (!windowCol || inStorey == 0 || inStorey == 3) && Town.noise(s.seed(), nx + y * 3, nz, 4.0, 404) > 0.35) {
                    placeIfAir(level, box, at.set(x, y, z), Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d), true));
                }
            }
        }
    }

    /** A pot on a window box: geraniums, a fern, a dead stalk. */
    private static BlockState pot(long h) {
        Block p = switch ((int) (h % 6)) {
            case 0 -> Blocks.POTTED_RED_TULIP;
            case 1 -> Blocks.POTTED_FERN;
            case 2 -> Blocks.POTTED_DEAD_BUSH;
            case 3 -> Blocks.POTTED_POPPY;
            case 4 -> Blocks.POTTED_AZURE_BLUET;
            default -> Blocks.FLOWER_POT;
        };
        return p.defaultBlockState();
    }

    /**
     * On a quay under the gorge's wall, now and then a ladder up it to the street or the house over it (the quays were
     * worked from above).
     */
    static void quayLadder(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int z, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = w[a + d.getStepX()][b + d.getStepZ()];
            // up to a street or a square only (a ladder up a house's wall led nowhere), every twenty-odd blocks along the quay
            if (n == null || n.kind() != Capital.Kind.STREET && n.kind() != Capital.Kind.SQUARE) {
                continue;
            }
            int up = n.ground();
            if (up < c.ground() + 2) {
                continue;
            }
            int along = Math.abs(s.ax()) > 0.5 ? x : z;                        // along the river
            if (Math.floorMod(along, 23) != 0) {
                continue;
            }
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, d.getOpposite());
            for (int y = c.ground() + 1; y <= up; y++) {
                placeIfAir(level, box, at.set(x, y, z), ladder);
            }
            return;
        }
    }

    static void placeIfAir(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at) && level.getBlockState(at).isAir()) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalFacades() {}
}
