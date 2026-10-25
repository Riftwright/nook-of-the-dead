package dev.nookofthedead.world;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The open country's own ground, drawn over the town's ({@link Town#decorate}; the user, 2026-10-15, biomes b and d):
 * <ul>
 * <li>a lake's water ({@link Terrain.Lake}), on sand and clay;</li>
 * <li>in the wilds, rocks standing up out of the grass ({@link Terrain#rock}), a cave hollowed in some where people hid
 * and the infection got in among them - their beds and barrels left, a fire gone out, and the dead standing about inside
 * ({@link Terrain.Cave}, zombie markers: they come out as one comes near, and stand in the dark); woods of oak, birch and
 * spruce, ferns and tall grass;</li>
 * <li>in the farmland, grass grown tall over everything left open, and the fields gone wild ({@link #field}): crops gone
 * to seed in the tall grass, broken fences, the odd bale - the dead hard to see in it.</li>
 * </ul>
 */
public final class Country {
    /** Of the wilds' open columns, how many in a thousand a tree stands on: in the woods, at their edge, in the open. */
    public static final int WOODS = 70;
    public static final int EDGE = 22;
    public static final int OPEN = 5;

    static void draw(WorldGenLevel level, long seed, Town.Plan plan, BoundingBox box, boolean[][] capital) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int a = 1; a <= 16; a++) {
            for (int b = 1; b <= 16; b++) {
                int x = plan.x0() + a;
                int z = plan.z0() + b;
                if (capital[a][b]) {
                    continue;                                           // the capital's (Capital)
                }
                int t = plan.top()[a][b];
                int water = plan.water()[a][b];
                if (water > 0) {
                    if (Terrain.sea(seed, x, z) != null) {
                        sea(level, seed, at, x, z, t, water);
                    } else {
                        lake(level, seed, at, x, z, t, water);
                    }
                    continue;
                }
                if (plan.kind()[a][b] != Town.Ground.LOT || plan.built()[a][b]) {
                    continue;
                }
                Region region = Region.looks(seed, x, z);                 // what grows: by the region it looks, its edge wandering
                Terrain.Sea sea = Terrain.sea(seed, x, z);
                if (sea != null && sea.in(x, z) > -BEACH && t <= sea.water() + 2) {
                    beach(level, seed, at, x, z, t, sea.in(x, z));
                    continue;
                }
                if (region == Region.WILDS) {
                    int rock = (int) Math.round(Terrain.rock(seed, x, z));
                    Terrain.Peak peak = Terrain.peak(seed, x, z);
                    double up = peak == null ? 0 : peak.up(x, z);
                    if (rock > 0) {
                        rock(level, seed, at, x, z, t, rock);
                    } else if (up > TREELINE) {
                        mountain(level, seed, at, x, z, t, up);
                    } else {
                        wild(level, seed, x, z, t, box, at);
                    }
                } else if (region == Region.FARMLAND) {
                    meadow(level, seed, at, x, z, t);
                }
            }
        }
        Terrain.Cave cave = Terrain.cave(seed, Math.floorDiv(box.minX(), Town.BLOCK), Math.floorDiv(box.minZ(), Town.BLOCK));
        if (cave != null) {
            hollow(level, seed, cave, box, at);
        }
    }

    private static void lake(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int bed, int water) {
        double n = Town.noise(seed, x, z, 5.0, 101);
        level.setBlock(at.set(x, bed, z), (n < 0.45 ? Blocks.SAND : n < 0.75 ? Blocks.GRAVEL : Blocks.CLAY).defaultBlockState(), 2);
        for (int y = bed + 1; y <= water; y++) {
            level.setBlock(at.set(x, y, z), Blocks.WATER.defaultBlockState(), 2);
        }
        for (int y = water + 1; y <= water + 2; y++) {                    // nothing standing in the water from the ground before
            level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 102), 100);
        if (bed == water - 1 && r < 8) {
            level.setBlock(at.set(x, water + 1, z), Blocks.LILY_PAD.defaultBlockState(), 2);
        } else if (bed <= water - 2 && r < 30) {
            level.setBlock(at.set(x, bed + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
        }
    }

    /** How far back from a sea's water its beach reaches; how far up a mountain (of its height) the trees go, and grass. */
    static final int BEACH = 6;
    static final double TREELINE = 0.5;
    static final double BARE = 0.64;
    static final double SNOW = 0.85;

    /** A sea's beach ({@code in} its shore's distance, below 0): sand, gravel in patches, nothing growing; a boat left on it. */
    private static void beach(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int t, double in) {
        double n = Town.noise(seed, x, z, 6.0, 122);
        Block sand = n < 0.72 ? Blocks.SAND : Blocks.GRAVEL;
        for (int y = t - 2; y <= t; y++) {
            level.setBlock(at.set(x, y, z), sand.defaultBlockState(), 2);
        }
        for (int y = t + 1; y <= t + 2; y++) {
            level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        long h = Town.hash(seed, x, z, 130);
        if (in > -3 && Math.floorMod(h, 1000L) < 3) {                      // pulled up out of the water, and never taken out again
            var boat = (Math.floorMod(h >> 10, 2L) == 0 ? net.minecraft.world.entity.EntityTypes.OAK_BOAT
                    : net.minecraft.world.entity.EntityTypes.SPRUCE_BOAT).create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (boat != null) {
                boat.snapTo(x + 0.5, t + 1, z + 0.5, (float) Math.floorMod(h >> 12, 360L), 0.0F);
                level.addFreshEntity(boat);
            }
        }
    }

    /**
     * High on a mountain ({@code up} of its height): above the trees, grass going to bare rock, gravel in its hollows; snow
     * at its top.
     */
    private static void mountain(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int t, double up) {
        double n = Town.noise(seed, x, z, 9.0, 123);
        boolean bare = up > BARE || n < (up - TREELINE) / (BARE - TREELINE) * 0.8;
        if (bare) {
            for (int y = t - 3; y <= t; y++) {
                double v = Town.noise(seed, x + y * 5, z - y * 3, 5.0, 124);
                Block stone = v < 0.45 ? Blocks.STONE : v < 0.65 ? Blocks.ANDESITE : v < 0.8 ? Blocks.TUFF : v < 0.92 ? Blocks.COBBLESTONE : Blocks.GRAVEL;
                level.setBlock(at.set(x, y, z), stone.defaultBlockState(), 2);
            }
        }
        if (up > SNOW + (n - 0.5) * 0.08) {
            level.setBlock(at.set(x, t, z), Blocks.SNOW_BLOCK.defaultBlockState(), 2);
            if (up > SNOW + 0.05 && level.getBlockState(at.set(x, t + 1, z)).isAir()) {
                level.setBlock(at, Blocks.SNOW.defaultBlockState(), 2);
            }
            return;
        }
        if (!bare && level.getBlockState(at.set(x, t + 1, z)).isAir() && Math.floorMod(Town.hash(seed, x, z, 125), 100L) < 18) {
            level.setBlock(at, Blocks.SHORT_GRASS.defaultBlockState(), 2);
        }
    }

    /** A sea's water over its bed: sand and gravel, seagrass here and there, kelp standing up in the deep. */
    private static void sea(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int bed, int water) {
        double n = Town.noise(seed, x, z, 7.0, 126);
        level.setBlock(at.set(x, bed, z), (n < 0.7 ? Blocks.SAND : Blocks.GRAVEL).defaultBlockState(), 2);
        for (int y = bed + 1; y <= water; y++) {
            level.setBlock(at.set(x, y, z), Blocks.WATER.defaultBlockState(), 2);
        }
        for (int y = water + 1; y <= water + 2; y++) {                    // nothing standing in the water from the ground before
            level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 127), 100);
        int depth = water - bed;
        if (depth >= 5 && r < 4 && Town.noise(seed, x, z, 20.0, 128) > 0.55) {
            int high = bed + 1 + (int) Math.floorMod(Town.hash(seed, x, z, 129), (long) (depth - 2));
            for (int y = bed + 1; y < high; y++) {
                level.setBlock(at.set(x, y, z), Blocks.KELP_PLANT.defaultBlockState(), 2);
            }
            level.setBlock(at.set(x, high, z), Blocks.KELP.defaultBlockState(), 2);
        } else if (depth >= 2 && r < 7) {
            level.setBlock(at.set(x, bed + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
        }
    }

    private static void rock(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int t, int height) {
        for (int y = t; y <= t + height; y++) {
            double v = Town.noise(seed, x + y * 5, z - y * 3, 4.0, 103);
            Block stone = v < 0.4 ? Blocks.STONE : v < 0.6 ? Blocks.ANDESITE : v < 0.75 ? Blocks.COBBLESTONE : v < 0.88 ? Blocks.TUFF : Blocks.MOSSY_COBBLESTONE;
            level.setBlock(at.set(x, y, z), stone.defaultBlockState(), 2);
        }
        double cover = Town.noise(seed, x, z, 6.0, 104);
        if (cover < 0.45) {
            level.setBlock(at.set(x, t + height, z), (cover < 0.3 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
            if (cover < 0.2 && Math.floorMod(Town.hash(seed, x, z, 105), 4) == 0) {
                level.setBlock(at.set(x, t + height + 1, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
            }
        }
    }

    /** The wilds' open ground: now and then a tree, else ferns, tall grass, a flower, a bush. */
    static void wild(WorldGenLevel level, long seed, int x, int z, int t, BoundingBox box, BlockPos.MutableBlockPos at) {
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 106), 1000);
        double woods = Town.noise(seed, x, z, 55.0, 107) * 0.8 + Town.noise(seed, x, z, 13.0, 117) * 0.2;
        int trees = woods > 0.55 ? WOODS : woods > 0.42 ? EDGE : OPEN;
        boolean inside = x - box.minX() >= 2 && box.maxX() - x >= 2 && z - box.minZ() >= 2 && box.maxZ() - z >= 2;
        if (r >= 994 && inside && woods > 0.42) {                          // a tree fallen long ago, mossy
            fallen(level, seed, x, z, t, at);
            return;
        }
        if (r < trees && inside) {
            double kind = Town.noise(seed, x, z, 60.0, 108);
            ResourceKey<Feature> tree = kind < 0.35 ? TreeFeatures.OAK : kind < 0.55 ? TreeFeatures.BIRCH : kind < 0.8 ? TreeFeatures.SPRUCE
                    : TreeFeatures.FANCY_OAK;
            Holder<Feature> feature = level.registryAccess().lookupOrThrow(Registries.FEATURE).get(tree).orElse(null);
            if (feature != null && level.getBlockState(at.set(x, t, z)).is(Blocks.GRASS_BLOCK)) {
                feature.value().place(level, level.getLevel().getChunkSource().getGenerator(), RandomSource.create(Town.hash(seed, x, z, 109)),
                        new BlockPos(x, t + 1, z));
                return;
            }
        }
        if (!level.getBlockState(at.set(x, t + 1, z)).isAir()) {
            return;
        }
        // the tall growth in thickets, not everywhere (the user, 2026-10-16: some cover for the dead to come out of
        // suddenly, but not grass all over - it hid everything, and weighed): deep in a thicket, open ground between
        int high = thicket(seed, x, z) ? 620 : 70;
        if (r < high * 3 / 4) {
            tall(level, at, x, t, z, Blocks.TALL_GRASS);
        } else if (r < high) {
            tall(level, at, x, t, z, Blocks.LARGE_FERN);
        } else if (r < 620) {
            level.setBlock(at.set(x, t + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        } else if (r < 700) {
            level.setBlock(at.set(x, t + 1, z), Blocks.FERN.defaultBlockState(), 2);
        } else if (r < 725) {
            Block flower = r < 710 ? Blocks.POPPY : r < 718 ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY;
            level.setBlock(at.set(x, t + 1, z), flower.defaultBlockState(), 2);
        } else if (r < 745) {
            level.setBlock(at.set(x, t + 1, z), Blocks.BUSH.defaultBlockState(), 2);
        } else if (r < 752 && woods > 0.42) {
            level.setBlock(at.set(x, t + 1, z), Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 2);
        }
    }

    /**
     * Whether ({@code x}, {@code z}) is in a thicket of tall growth: patches some ten to twenty across, about a third of
     * the open ground, with the way between them clear enough to see down.
     */
    static boolean thicket(long seed, int x, int z) {
        return Town.noise(seed, x, z, 14.0, 119) * 0.75 + Town.noise(seed, x, z, 5.0, 120) * 0.25 > 0.56;
    }

    /** A trunk lying along the ground, three or four long, moss on it here and there. */
    private static void fallen(WorldGenLevel level, long seed, int x, int z, int t, BlockPos.MutableBlockPos at) {
        long h = Town.hash(seed, x, z, 118);
        boolean alongX = (h & 1) == 0;
        int length = 3 + (int) ((h >> 1) & 1);
        Block log = ((h >> 2) & 1) == 0 ? Blocks.OAK_LOG : Blocks.SPRUCE_LOG;
        for (int i = 0; i < length; i++) {
            int lx = x + (alongX ? i : 0);
            int lz = z + (alongX ? 0 : i);
            if (!level.getBlockState(at.set(lx, t + 1, lz)).canBeReplaced()) {
                return;
            }
        }
        for (int i = 0; i < length; i++) {
            int lx = x + (alongX ? i : 0);
            int lz = z + (alongX ? 0 : i);
            level.setBlock(at.set(lx, t + 1, lz), log.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    alongX ? Direction.Axis.X : Direction.Axis.Z), 2);
            if (((h >> (3 + i)) & 1) == 0) {
                level.setBlock(at.set(lx, t + 2, lz), Blocks.MOSS_CARPET.defaultBlockState(), 2);
            }
        }
    }

    /** The farmland's open ground: grass grown up tall, hiding what walks in it. */
    private static void meadow(WorldGenLevel level, long seed, BlockPos.MutableBlockPos at, int x, int z, int t) {
        if (!level.getBlockState(at.set(x, t + 1, z)).isAir()) {
            return;
        }
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 110), 1000);
        if (r < (thicket(seed, x, z) ? 700 : 90)) {
            tall(level, at, x, t, z, Blocks.TALL_GRASS);
        } else if (r < 820) {
            level.setBlock(at.set(x, t + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        } else if (r < 840) {
            level.setBlock(at.set(x, t + 1, z), Blocks.DANDELION.defaultBlockState(), 2);
        }
    }

    /**
     * A field of the farmland gone wild - each lot its own (the user, 2026-10-16: the farmland read as one plain of grass
     * from afar): mostly what was sown, still there in rows - wheat, carrots, potatoes, beetroot - with tall grass come
     * up through it; else an orchard of small trees in rows, a pasture with its trough, a fallow strip of bare earth and
     * stones, a patch of pumpkins or sunflowers, or a hay meadow with its bales. Round it a fence, a hedge, a low stone
     * wall or nothing, broken in places.
     */
    static void field(WorldGenLevel level, Town.Lot lot, int x, int z, int y, int r, BlockPos.MutableBlockPos at) {
        BoundingBox foot = lot.footprint();
        boolean edge = x == foot.minX() || x == foot.maxX() || z == foot.minZ() || z == foot.maxZ();
        int kind = (int) Math.floorMod(lot.seed() >> 40, 10L);
        if (edge) {
            boundary(level, lot, x, z, y, r, at);
            return;
        }
        switch (kind) {
            case 4 -> orchard(level, lot, x, z, y, r, at);
            case 5 -> {                                                  // a pasture: grass, a trough, a few bones
                if (x == foot.minX() + 4 && z >= foot.minZ() + 4 && z <= foot.minZ() + 6) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.CAULDRON.defaultBlockState(), 2);
                } else if (r < 300) {
                    tall(level, at, x, y, z, Blocks.TALL_GRASS);
                } else if (r < 760) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                } else if (r < 765) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.BONE_BLOCK.defaultBlockState(), 2);
                }
            }
            case 6 -> {                                                  // fallow: bare earth, stones turned up, dead bushes
                level.setBlock(at.set(x, y, z), (r < 450 ? Blocks.COARSE_DIRT : r < 600 ? Blocks.ROOTED_DIRT : Blocks.DIRT).defaultBlockState(), 2);
                if (r < 40) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);
                } else if (r < 52) {
                    level.setBlock(at.set(x, y + 1, z), (r < 46 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE).defaultBlockState(), 2);
                } else if (r < 140) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
            case 7 -> {                                                  // a patch gone to pumpkins, or to sunflowers
                boolean pumpkins = (lot.seed() >> 44 & 1) == 0;
                if (pumpkins && r < 70) {
                    level.setBlock(at.set(x, y + 1, z), (r < 6 ? Blocks.CARVED_PUMPKIN : Blocks.PUMPKIN).defaultBlockState(), 2);
                } else if (!pumpkins && r < 330) {
                    tall(level, at, x, y, z, Blocks.SUNFLOWER);
                } else if (r < 600) {
                    tall(level, at, x, y, z, Blocks.TALL_GRASS);
                } else {
                    level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
            case 8 -> {                                                  // a hay meadow: long grass and its bales, stacked
                if (r < 14) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
                    if (r < 5) {
                        level.setBlock(at.set(x, y + 2, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
                    }
                } else if (r < 700) {
                    tall(level, at, x, y, z, Blocks.TALL_GRASS);
                } else {
                    level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
            }
            default -> crops(level, lot, x, z, y, r, at);
        }
    }

    /** What was sown, in rows, with tall grass come up through most of it; a bale lying here and there. */
    private static void crops(WorldGenLevel level, Town.Lot lot, int x, int z, int y, int r, BlockPos.MutableBlockPos at) {
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        Block crop = crops[(int) Math.floorMod(lot.seed() >> 12, 4L)];
        boolean row = Math.floorMod(lot.rotation().ordinal() % 2 == 0 ? z : x, 2) == 0;
        if (r < 8) {
            level.setBlock(at.set(x, y + 1, z), Blocks.HAY_BLOCK.defaultBlockState(), 2);
        } else if (r < 470) {
            tall(level, at, x, y, z, Blocks.TALL_GRASS);
        } else if (row && r < 800) {
            level.setBlock(at.set(x, y, z), Blocks.FARMLAND.defaultBlockState(), 2);
            BlockState plant = crop.defaultBlockState();
            if (crop instanceof CropBlock cropBlock) {
                plant = cropBlock.getStateForAge(Math.min(cropBlock.getMaxAge(), 3 + r % (cropBlock.getMaxAge() - 2)));
            }
            level.setBlock(at.set(x, y + 1, z), plant, 2);
        } else if (r < 900) {
            level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        } else {
            level.setBlock(at.set(x, y, z), Blocks.COARSE_DIRT.defaultBlockState(), 2);
        }
    }

    /**
     * An orchard: small trees in rows five apart, a trunk two or three high and a round head of leaves - each column works
     * out for itself which trees reach it, so a tree on a chunk's edge is whole - and grass between, windfalls rotting.
     */
    private static void orchard(WorldGenLevel level, Town.Lot lot, int x, int z, int y, int r, BlockPos.MutableBlockPos at) {
        BoundingBox foot = lot.footprint();
        boolean birch = (lot.seed() >> 46 & 1) == 0;
        BlockState log = (birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).defaultBlockState();
        BlockState leaves = (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int lx = x - foot.minX() - 3;
        int lz = z - foot.minZ() - 3;
        boolean any = false;
        for (int tx = Math.floorDiv(lx + 2, 5) * 5 - 5; tx <= lx + 2; tx += 5) {
            for (int tz = Math.floorDiv(lz + 2, 5) * 5 - 5; tz <= lz + 2; tz += 5) {
                if (tx < 0 || tz < 0 || tx > lot.size() - 7 || tz > lot.size() - 7) {
                    continue;
                }
                long h = Town.hash(lot.seed(), tx, tz, 172);
                if (Math.floorMod(h, 10) < 2) {
                    continue;                                            // a gap where one died
                }
                int trunk = 2 + (int) Math.floorMod(h >> 8, 2L);
                int dx = lx - tx;
                int dz = lz - tz;
                if (dx == 0 && dz == 0) {
                    for (int k = 1; k <= trunk; k++) {
                        level.setBlock(at.set(x, y + k, z), log, 2);
                    }
                    level.setBlock(at.set(x, y + trunk + 1, z), leaves, 2);
                    any = true;
                } else if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && dx * dx + dz * dz <= 5) {
                    int from = Math.abs(dx) + Math.abs(dz) <= 1 ? trunk : trunk + 1;
                    for (int k = from; k <= trunk + (dx * dx + dz * dz <= 2 ? 2 : 1); k++) {
                        if (Math.floorMod(Town.hash(h, dx, dz * 7 + k, 173), 9) > 0) {
                            level.setBlock(at.set(x, y + k, z), leaves, 2);
                        }
                    }
                    any = true;
                }
            }
        }
        if (!any) {
            if (r < 260) {
                level.setBlock(at.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            } else if (r < 268) {
                level.setBlock(at.set(x, y + 1, z), Blocks.BROWN_MUSHROOM.defaultBlockState(), 2);
            }
        }
    }

    /** The field's edge: a fence, a hedge, a low wall of field stones, or nothing - broken in places. */
    private static void boundary(WorldGenLevel level, Town.Lot lot, int x, int z, int y, int r, BlockPos.MutableBlockPos at) {
        int edge = (int) Math.floorMod(lot.seed() >> 48, 4L);
        switch (edge) {
            case 0 -> {
                if (r < 620) {
                    level.setBlock(at.set(x, y + 1, z), Blocks.OAK_FENCE.defaultBlockState(), 2);
                }
            }
            case 1 -> {                                                  // a hedge, one or two high, thin in places
                if (r < 860) {
                    BlockState hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    level.setBlock(at.set(x, y + 1, z), hedge, 2);
                    if (r < 430) {
                        level.setBlock(at.set(x, y + 2, z), hedge, 2);
                    }
                }
            }
            case 2 -> {                                                  // a low wall of the stones picked off the field
                if (r < 700) {
                    level.setBlock(at.set(x, y + 1, z), (r < 300 ? Blocks.MOSSY_COBBLESTONE_WALL : Blocks.COBBLESTONE_WALL).defaultBlockState(), 2);
                }
            }
            default -> {
            }
        }
    }

    private static void tall(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int y, int z, Block plant) {
        level.setBlock(at.set(x, y + 1, z), plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(at.set(x, y + 2, z), plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
    }

    /**
     * The cave hollowed out of its rock, in this chunk: the hollow and the way in, its floor of earth and gravel; and what
     * was left in it - bedrolls along the wall, barrels and a chest (their loot), a fire gone out in the middle, cobwebs in
     * the dark - and the dead, three markers' worth, standing there.
     */
    private static void hollow(WorldGenLevel level, long seed, Terrain.Cave cave, BoundingBox box, BlockPos.MutableBlockPos at) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int high = cave.hollow(x, z);
                // the way in only through the rock: out past the hill's face it is open ground, not a trench
                if (high <= 0 || !cave.inChamber(x, z) && Terrain.rock(seed, x, z) < 3) {
                    continue;
                }
                double n = Town.noise(seed, x, z, 3.0, 111);
                level.setBlock(at.set(x, cave.floor(), z), (n < 0.5 ? Blocks.COARSE_DIRT : n < 0.8 ? Blocks.GRAVEL : Blocks.PACKED_MUD).defaultBlockState(), 2);
                for (int y = cave.floor() + 1; y <= cave.floor() + high; y++) {
                    level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        long h = Town.hash(seed, cave.x(), cave.z(), 112);
        int f = cave.floor() + 1;
        put(level, box, at.set(cave.x(), f, cave.z()), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        int far = cave.radius() - 2;
        Direction back = cave.mouth().getOpposite();
        Direction side = back.getClockWise();
        // the far wall: barrels and a chest; the sides: bedrolls (wool on straw)
        BlockPos wall = new BlockPos(cave.x() + back.getStepX() * far, f, cave.z() + back.getStepZ() * far);
        container(level, box, wall.relative(side, -1), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, cave.mouth()), "supplies", h);
        container(level, box, wall, Blocks.CHEST.defaultBlockState(), "food", h >> 4);
        container(level, box, wall.relative(side, 1), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, cave.mouth()), "medical", h >> 8);
        for (int k = -2; k <= 2; k += 2) {
            for (int s : new int[] {-1, 1}) {
                BlockPos bed = new BlockPos(cave.x() + side.getStepX() * s * far + back.getStepX() * k, f, cave.z() + side.getStepZ() * s * far
                        + back.getStepZ() * k);
                put(level, box, bed.below(), Blocks.HAY_BLOCK.defaultBlockState());
                put(level, box, bed, ((Math.floorMod(h >> (k + 4 + s), 3) == 0) ? Blocks.CARPET.lightGray() : Blocks.CARPET.brown()).defaultBlockState());
            }
        }
        for (int k = 0; k < 6; k++) {                                      // cobwebs up in the dark
            long c = Town.hash(seed, cave.x(), k, 113);
            int dx = (int) Math.floorMod(c, (long) (2 * far + 1)) - far;
            int dz = (int) Math.floorMod(c >> 8, (long) (2 * far + 1)) - far;
            int top = cave.hollow(cave.x() + dx, cave.z() + dz);
            if (top > 1) {
                put(level, box, at.set(cave.x() + dx, cave.floor() + top, cave.z() + dz), Blocks.COBWEB.defaultBlockState());
            }
        }
        // props of timber holding the roof, where they had made it a shelter
        int prop = (int) Math.round((cave.radius() - 1.5) / Math.sqrt(2));
        for (int[] c : new int[][] {{prop, prop}, {-prop, prop}, {prop, -prop}, {-prop, -prop}}) {
            int px = cave.x() + c[0];
            int pz = cave.z() + c[1];
            int high = cave.hollow(px, pz);
            for (int y = f; y <= cave.floor() + high; y++) {                 // up to the roof (it stopped one short: the review)
                put(level, box, at.set(px, y, pz), Blocks.SPRUCE_LOG.defaultBlockState());
            }
        }
        // what they kept by them: a table, a cauldron, candles long out, a book left open on a lectern
        BlockPos keep = wall.relative(side, -3);
        put(level, box, keep, Blocks.CRAFTING_TABLE.defaultBlockState());
        put(level, box, wall.relative(side, 3), Blocks.CAULDRON.defaultBlockState());
        put(level, box, wall.relative(side, -1).above(), Blocks.CANDLE.defaultBlockState());
        put(level, box, wall.relative(side, 1).above(), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3));
        BlockPos lectern = new BlockPos(cave.x() + side.getStepX() * 2 + back.getStepX() * 2, f, cave.z() + side.getStepZ() * 2 + back.getStepZ() * 2);
        if (box.isInside(lectern)) {
            level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, cave.mouth())
                    .setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true), 2);
            if (level.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity stand) {
                stand.setBook(Diaries.book(RandomSource.create(h)));
            }
        }
        // the barricade they put across the way in: planks and fences, broken through
        for (int k = 1; k <= 2; k++) {
            int along = cave.radius() + k;
            for (int across = -1; across <= 1; across++) {
                for (int y = f; y <= f + 2; y++) {
                    long b = Town.hash(seed, along * 7 + across, y, 119);
                    if (Math.floorMod(b, 100) < 45) {
                        continue;                                           // where they broke through
                    }
                    BlockPos piece = new BlockPos(cave.x() + cave.mouth().getStepX() * along + side.getStepX() * across, y,
                            cave.z() + cave.mouth().getStepZ() * along + side.getStepZ() * across);
                    put(level, box, piece, (k == 1 ? Blocks.OAK_PLANKS : Blocks.OAK_FENCE).defaultBlockState());
                }
            }
        }
        int[][] dead = {{2, 1}, {-2, -1}, {1, -3}};
        for (int[] d : dead) {
            BlockPos spot = new BlockPos(cave.x() + d[0], f, cave.z() + d[1]);
            put(level, box, spot, ZombieMarkerBlock.of(3, false));
        }
    }

    private static void put(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state) {
        if (box.isInside(pos)) {
            level.setBlock(pos, state, 2);
        }
    }

    private static void container(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state, String loot, long seed) {
        if (!box.isInside(pos)) {
            return;
        }
        level.setBlock(pos, state, 2);
        if (level.getBlockEntity(pos) instanceof RandomizableContainer container) {
            container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, NookOfTheDead.id("chests/" + loot)), seed);
        }
    }

    private Country() {}
}
