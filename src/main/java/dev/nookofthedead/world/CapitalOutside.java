package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Outside the capital's wall ({@link Capital}): the berm at its foot, the moat, the road round it - and beyond the road,
 * where the people of the country came when the fever reached them and found the gates shut, their camp: tents, cold fires,
 * carts, the graves they dug, and before the shut gates the most of them, still there. Out past the camp the ground eases
 * into the land round the capital (the town's own ground at the capital's edge), so the edge is the moat and the camp,
 * never a step; the town's streets that run up to it go on as tracks to the ring road.
 */
final class CapitalOutside {
    /** How far out of the ring road the camp goes, at most (more before the gates). */
    static final int CAMP = 34;

    static void draw(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, int planTop, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        double r = c.r();
        int inner = c.ground();
        double t = Capital.smoothstep(Capital.RING_OUT + 6, Capital.ZONE - 1, r);
        int ground = (int) Math.round(inner * (1 - t) + planTop * t);
        Region under = Region.of(s.seed(), Math.floorDiv(x, Town.BLOCK), Math.floorDiv(z, Town.BLOCK));
        boolean sand = under == Region.DESERT && t > 0.5;
        CapitalDraw.earth(level, x, z, ground, box, at);
        long h = Math.floorMod(Town.hash(s.seed(), x, z, 380), 1000L);
        if (c.kind() == Capital.Kind.ROAD) {
            double n = Town.noise(s.seed(), x, z, 4.0, 381);
            set(level, box, at.set(x, ground, z), (n < 0.4 ? Blocks.GRAVEL : n < 0.75 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT).defaultBlockState());
            if (h < 6) {
                Markers.remains(level, at.set(x, ground + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(h)),
                        RandomSource.create(h * 31));
            }
            return;
        }
        if (c.kind() == Capital.Kind.BERM) {
            set(level, box, at.set(x, ground, z), Blocks.GRASS_BLOCK.defaultBlockState());
            if (h < 120) {
                set(level, box, at.set(x, ground + 1, z), (h < 40 ? Blocks.TALL_GRASS : Blocks.SHORT_GRASS).defaultBlockState());
            } else if (h < 140) {
                set(level, box, at.set(x, ground + 1, z), Blocks.COBBLESTONE.defaultBlockState());      // fallen from the wall
            }
            return;
        }
        // the town's streets, run on as tracks to the ring road
        if (Town.ground(s.seed(), x, z) == Town.Ground.STREET && t < 0.98) {
            double n = Town.noise(s.seed(), x, z, 3.0, 382);
            set(level, box, at.set(x, ground, z), (sand ? Blocks.SAND : n < 0.5 ? Blocks.COARSE_DIRT : n < 0.8 ? Blocks.DIRT_PATH : Blocks.GRAVEL)
                    .defaultBlockState());
            return;
        }
        set(level, box, at.set(x, ground, z), (sand ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState());
        double gateNear = 0;
        for (int k = 0; k < 4; k++) {
            double arc = r * Math.abs(Capital.wrap(c.angle() - s.gate(k)));
            gateNear = Math.max(gateNear, 1 - Math.min(1, arc / 90.0));
        }
        double out = r - Capital.RING_OUT;
        double reach = CAMP * (0.45 + 0.55 * gateNear);
        if (out < reach && !sand) {
            camp(level, s, c, x, z, ground, gateNear, box, at, h);
            return;
        }
        if (h < 220 && !sand) {
            set(level, box, at.set(x, ground + 1, z), (h < 50 ? Blocks.TALL_GRASS : h < 70 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
            if (h < 50) {
                set(level, box, at.set(x, ground + 2, z), Blocks.TALL_GRASS.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            }
        } else if (sand && h < 12) {
            set(level, box, at.set(x, ground + 1, z), Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    /** The camp of those shut out: tents in rows, cold fires, carts and barrels, graves; the dead before the shut gates. */
    private static void camp(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, int ground, double gateNear, BoundingBox box,
            BlockPos.MutableBlockPos at, long h) {
        double n = Town.noise(s.seed(), x, z, 5.0, 383);
        set(level, box, at.set(x, ground, z), (n < 0.35 ? Blocks.COARSE_DIRT : n < 0.5 ? Blocks.DIRT_PATH : n < 0.6 ? Blocks.MUD
                : Blocks.GRASS_BLOCK).defaultBlockState());
        // the tents: one to a cell of seven, most cells, turned along or across
        Capital.Cell cell = Capital.cell(s.seed(), x + 0.5, z + 0.5, 7, 384, null);
        long th = Town.hash(s.seed(), cell.i(), cell.j(), 385);
        boolean tent = Math.floorMod(th, 100L) < 62;
        boolean along = (th >> 8 & 1) == 0;
        double du = along ? x + 0.5 - cell.sx() : z + 0.5 - cell.sz();
        double dv = along ? z + 0.5 - cell.sz() : x + 0.5 - cell.sx();
        if (tent && Math.abs(du) <= 2.2 && Math.abs(dv) <= 1.6) {
            Block cloth = switch ((int) Math.floorMod(th >> 10, 5L)) {
                case 0 -> Blocks.WOOL.white();
                case 1 -> Blocks.WOOL.lightGray();
                case 2 -> Blocks.WOOL.brown();
                case 3 -> Blocks.WOOL.gray();
                default -> Blocks.WOOL.green();
            };
            // a ridge tent: its sides two high, the ridge over the middle, room inside to crawl in at the open end and lie
            // down on the bedroll (in game: the tents were solid wool, the dead sunk in them)
            boolean end = du < -1.6;                                           // its open end, the flap thrown back
            boolean back = du > 1.6;
            boolean collapsed = Math.floorMod(th >> 14, 100L) < 25;
            Block bedroll = (th >> 24 & 1) == 0 ? Blocks.CARPET.brown() : Blocks.CARPET.gray();
            if (collapsed) {
                // fallen in: the cloth lying over what was inside
                set(level, box, at.set(x, ground + 1, z), (Math.abs(dv) < 0.6 ? cloth : carpetOf(cloth)).defaultBlockState());
                return;
            }
            if (Math.abs(dv) < 0.6) {
                set(level, box, at.set(x, ground + 1, z), back ? cloth.defaultBlockState() : end ? Blocks.AIR.defaultBlockState()
                        : bedroll.defaultBlockState());
                set(level, box, at.set(x, ground + 2, z), back ? cloth.defaultBlockState() : Blocks.AIR.defaultBlockState());
                set(level, box, at.set(x, ground + 3, z), cloth.defaultBlockState());
                if (!end && !back && du > 0.4 && gateNear > 0.6 && Math.floorMod(th >> 32, 100L) < 4) {
                    // now and then, by someone's bedroll, what they wrote while they waited at the gate
                    set(level, box, at.set(x, ground + 1, z), Blocks.AIR.defaultBlockState());
                    RandomSource book = RandomSource.create(th * 13);
                    Props.pile(level, box, at.set(x, ground + 1, z).immutable(), 1, (float) Math.floorMod(th, 360L), book,
                            () -> Diaries.book(book, Diaries.Place.CAMP));
                } else if (!end && !back && Math.abs(du) < 0.6 && Math.floorMod(th >> 20, 100L) < 30) {
                    Markers.remains(level, at.set(x, ground + 1, z).immutable(), along ? Direction.EAST : Direction.SOUTH, RandomSource.create(th));
                } else if (!end && !back && du > 0.4 && Math.floorMod(th >> 28, 100L) < 25) {
                    CapitalDraw.container(level, box, at.set(x, ground + 1, z), dev.nookworks.registry.ModBlocks.CRATE.get(), "food",
                            Town.hash(s.seed(), x, ground, z));
                }
            } else if (Math.abs(dv) < 1.6) {
                set(level, box, at.set(x, ground + 1, z), cloth.defaultBlockState());
                set(level, box, at.set(x, ground + 2, z), end ? Blocks.AIR.defaultBlockState() : cloth.defaultBlockState());
                set(level, box, at.set(x, ground + 3, z), carpetOf(cloth).defaultBlockState());
            }
            return;
        }
        // beside the tent, its fire and its things
        if (tent && Math.abs(du) <= 2.2 && Math.abs(dv) > 1.6 && Math.abs(dv) < 2.6 && Math.abs(du) < 0.6) {
            set(level, box, at.set(x, ground + 1, z), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
            return;
        }
        if (h < 14) {
            set(level, box, at.set(x, ground + 1, z), (h < 6 ? Blocks.BARREL : h < 10 ? dev.nookworks.registry.ModBlocks.CRATE.get() : Blocks.HAY_BLOCK)
                    .defaultBlockState());
            return;
        }
        if (h < 22) {
            CapitalDraw.container(level, box, at.set(x, ground + 1, z), Blocks.BARREL, "food", Town.hash(s.seed(), x, ground, z));
            return;
        }
        // the dead before the gates, the more the nearer
        if (h < 22 + 40 * gateNear * gateNear) {
            Markers.remains(level, at.set(x, ground + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(h)),
                    RandomSource.create(h * 7 + x));
            return;
        }

        if (h > 960 && gateNear < 0.5) {
            set(level, box, at.set(x, ground + 1, z), ZombieMarkerBlock.of(2, false));
        }
    }

    /** The carpet of a tent's cloth: its eaves, and what is left lying of a fallen one. */
    private static Block carpetOf(Block cloth) {
        return cloth == Blocks.WOOL.white() ? Blocks.CARPET.white() : cloth == Blocks.WOOL.lightGray() ? Blocks.CARPET.lightGray()
                : cloth == Blocks.WOOL.brown() ? Blocks.CARPET.brown() : cloth == Blocks.WOOL.gray() ? Blocks.CARPET.gray() : Blocks.CARPET.green();
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalOutside() {}
}
