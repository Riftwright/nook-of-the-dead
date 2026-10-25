package dev.nookofthedead.world;

import com.mojang.serialization.MapCodec;
import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * The town the sand took ({@link Region#DESERT}; the user, 2026-10-18: "砂漠バイオーム追加しない？…砂漠の建物は石造りの家
 * よりも風化が早そうだから、上の方はごっそりなくなって土台だけのこってる…プレイヤーの開発跡ものこっていて、綺麗にのこってる
 * ピラミッドとすでに荒らされたピラミッドがランダムで"):
 * <ul>
 * <li>its ground sand over sandstone, dunes heaped over the streets and the open ground, a dead bush, a cactus now and then
 * ({@link #ground});</li>
 * <li>its buildings worn down to their footings - the walls standing a block or two here, five at a corner there, nothing
 * above - and the sand drifted in over their floors ({@link #erode}, {@link #drift}); the desert village's houses among
 * them, worn less;</li>
 * <li>a pyramid of the game's own on a lot now and then: one in two as it was left, its trap set and its chests full (a
 * suppressant among the gold, now and then); the other dug out from the top before anyone here, its trap taken up, its
 * chests empty, a ladder and the torches of whoever did it still there ({@link #pyramid});</li>
 * <li>on the lots with nothing built, what players left: a camp walled with sandstone, a field gone dry, a shaft sunk
 * with a cart track to it ({@link #traces}).</li>
 * </ul>
 * All of it fixed by the seed and the place, so every chunk draws its own part alike.
 */
public final class Desert {
    /** A pyramid's lot: no template, the game's own piece set down on it ({@link #pyramid}). */
    public static final Identifier PYRAMID = NookOfTheDead.id("desert/pyramid");
    /** What an untouched pyramid's chests hold: the game's, and a suppressant now and then. */
    public static final ResourceKey<LootTable> PYRAMID_LOOT = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
            NookOfTheDead.id("chests/pyramid"));
    /** How high a dune heaps at most over the ground. */
    public static final int DUNE = 4;

    /** Whether ({@code x}, {@code z}) is in the desert. */
    public static boolean at(long seed, int x, int z) {
        return Region.at(seed, x, z) == Region.DESERT;
    }

    /** Whether {@code lot} is in the desert (by its middle). */
    public static boolean on(long seed, Town.Lot lot) {
        return at(seed, lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2);
    }

    /**
     * How high the sand is heaped over the ground at ({@code x}, {@code z}): broad dunes, long ridges across the wind on
     * them, 0 to {@link #DUNE} - wide and gentle, a step every few blocks (not a heap of single blocks: the first look,
     * 2026-10-18, read as rubble).
     */
    static int dune(long seed, int x, int z) {
        double swell = Town.noise(seed, x, z, 64.0, 302);
        double ridge = Town.noise(seed, x + z / 2, z - x / 3, 34.0, 301);              // long ridges, across the wind
        double h = (swell * 0.75 + ridge * 0.6 - 0.62) * 7.0;
        // coming up from nothing past the ragged edge, never a wall of sand beside the edge's bare ground (the survey,
        // 2026-10-18: dunes three high stood straight up from the ground at the edge)
        double edge = Math.max(0.0, Math.min(1.0, (Region.looksInside(seed, x, z) - EDGE) / 30.0));
        return (int) Math.max(0, Math.min(DUNE, Math.floor(h * edge)));
    }

    /** Whether the sand has the column ({@code x}, {@code z}): all of it inside, less and less of it out to the edge. */
    static boolean sandy(long seed, int x, int z) {
        double in = Region.looksInside(seed, x, z);
        return in >= EDGE || Town.noise(seed, x, z, 9.0, 306) < in / EDGE * 0.9 + 0.1;
    }

    /** How far in from its edge the desert's sand thins out to the ground the next region has. */
    public static final double EDGE = 36.0;

    // --- the ground ------------------------------------------------------------------------------------------------------

    /** What the desert takes for sand: the earth, the grass and the paths, the town's gravel. */
    private static boolean earth(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MUD) || state.is(Blocks.PACKED_MUD)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.MOSS_BLOCK);
    }

    /** Whether {@code state} is a plant (grass, a flower, a fern) the desert leaves nothing of. */
    private static boolean green(BlockState state) {
        return !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(Blocks.DEAD_BUSH);
    }

    /**
     * The chunk's columns not under a building, made desert - last, over everything else drawn there: the earth sand (and
     * sandstone under it), the plants gone, the dunes heaped on the streets, the alleys and the open ground (not on a
     * lot: a camp or a field there stays clear), a dead bush, a cactus now and then.
     */
    static void ground(WorldGenLevel level, Town.Plan plan, int x0, int z0) {
        long seed = plan.seed();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        java.util.List<int[]> cacti = new java.util.ArrayList<>();
        for (int a = 1; a <= 16; a++) {
            for (int b = 1; b <= 16; b++) {
                int x = x0 + a;
                int z = z0 + b;
                if (Region.looks(seed, x, z) != Region.DESERT || plan.water()[a][b] > 0) {
                    continue;                                            // its edge as it looks, wandering (Region.WANDER)
                }
                if (!sandy(seed, x, z)) {
                    scrub(level, plan, at, a, b, x, z);
                    continue;
                }
                boolean building = false;
                boolean onLot = false;
                for (Town.Lot lot : plan.lots()) {
                    if (lot.covers(x, z)) {
                        onLot = true;
                        building |= lot.use() == Town.Use.BUILDING;
                    }
                }
                if (building) {
                    continue;                                            // its own ground: drift
                }
                int t = plan.top()[a][b];
                BlockState ground = level.getBlockState(at.set(x, t, z));
                if (ground.isAir() || ground.is(Blocks.LADDER)) {
                    continue;                                            // a way up from the galleries: left open (the review, 2026-10-19)
                }
                for (int y = t - 3; y <= t; y++) {
                    BlockState state = level.getBlockState(at.set(x, y, z));
                    if (earth(state)) {
                        level.setBlock(at, (y >= t - 1 ? Blocks.SAND : Blocks.SANDSTONE).defaultBlockState(), 2);
                    }
                }
                for (int y = t + 1; y <= t + 3; y++) {
                    BlockState state = level.getBlockState(at.set(x, y, z));
                    if (green(state)) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                    } else {
                        BlockState sandy = sandstone(state);              // the rubble lying about, the desert's stone
                        if (sandy != state) {
                            level.setBlock(at, sandy, 2);
                        }
                    }
                }
                BlockState over = level.getBlockState(at.set(x, t + 1, z));
                if (over.getBlock() instanceof SlabBlock || !over.isAir()) {
                    continue;                                            // a street's half step, or what lies on it
                }
                int heap = Math.min(dune(seed, x, z), slope(plan.lots(), x, z));
                if (plan.kind()[a][b] == Town.Ground.STREET) {
                    heap = Math.max(0, heap - 1);                       // the streets a little clearer: the wind runs down them
                }
                for (int y = t + 1; y <= t + heap; y++) {
                    level.setBlock(at.set(x, y, z), Blocks.SAND.defaultBlockState(), 2);
                }
                int r = (int) Math.floorMod(Town.hash(seed, x, z, 304), 1000L);
                int surface = t + heap;
                if (!onLot && r < 14 && level.getBlockState(at.set(x, surface, z)).is(Blocks.SAND)) {
                    level.setBlock(at.set(x, surface + 1, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);
                } else if (!onLot && r < 17 && a > 1 && a < 16 && b > 1 && b < 16) {
                    cacti.add(new int[] {x, surface + 1, z, 1 + r % 3});
                }
            }
        }
        // the cacti once every dune is down, so none has sand heaped beside it after (the review, 2026-10-18)
        for (int[] c : cacti) {
            if (cactusRoom(level, at, c[0], c[1], c[2])) {
                for (int y = c[1]; y < c[1] + c[3]; y++) {
                    level.setBlock(at.set(c[0], y, c[2]), Blocks.CACTUS.defaultBlockState(), 2);
                }
            }
        }
    }

    /**
     * How high a dune may be at ({@code x}, {@code z}) for the lots about it: down to nothing a few blocks into an open lot
     * (its camp, field or heap kept clear) and toward a building's lot a block a block - never a straight wall of sand
     * along a lot's edge (the survey, 2026-10-18: the dunes stopped dead at every lot, a terrace ruled along the grid).
     */
    static int slope(java.util.List<Town.Lot> lots, int x, int z) {
        int cap = DUNE;
        for (Town.Lot lot : lots) {
            int x0 = lot.corner().getX();
            int z0 = lot.corner().getZ();
            int x1 = x0 + lot.size() - 1;
            int z1 = z0 + lot.size() - 1;
            if (lot.covers(x, z)) {
                if (lot.use() == Town.Use.BUILDING) {
                    return 0;
                }
                int depth = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
                cap = Math.min(cap, Math.max(0, 2 - depth));
            } else if (lot.use() == Town.Use.BUILDING) {
                int out = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
                cap = Math.min(cap, out - 1);
            }
        }
        return Math.max(0, cap);
    }

    /**
     * Where the sand has not come yet at the desert's edge: the ground gone to dry scrub - bare earth, a drift of sand,
     * withered grass - not the green of the next region nor a flat of grass the desert's colour (the survey, 2026-10-18:
     * read as mud).
     */
    private static void scrub(WorldGenLevel level, Town.Plan plan, BlockPos.MutableBlockPos at, int a, int b, int x, int z) {
        long seed = plan.seed();
        for (Town.Lot lot : plan.lots()) {
            if (lot.covers(x, z) && lot.use() == Town.Use.BUILDING) {
                return;
            }
        }
        int t = plan.top()[a][b];
        if (!level.getBlockState(at.set(x, t, z)).is(Blocks.GRASS_BLOCK)) {
            return;
        }
        double in = Region.looksInside(seed, x, z) / EDGE;               // 0 at the edge, 1 where the sand has it all
        int r = (int) Math.floorMod(Town.hash(seed, x, z, 307), 100L);
        double patch = Town.noise(seed, x, z, 4.0, 308);
        BlockState ground = patch < 0.25 + in * 0.35 ? Blocks.COARSE_DIRT.defaultBlockState()
                : r < 10 + in * 35 ? Blocks.SAND.defaultBlockState() : null;
        if (ground != null) {
            level.setBlock(at, ground, 2);
        }
        BlockState over = level.getBlockState(at.set(x, t + 1, z));
        if (green(over) || over.is(Blocks.SHORT_GRASS) || over.is(Blocks.TALL_GRASS) || over.is(Blocks.FERN)) {
            boolean tall = over.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (tall) {
                level.setBlock(at.set(x, t + 2, z), Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(at.set(x, t + 1, z), ground == null && r < 60 ? Blocks.SHORT_DRY_GRASS.defaultBlockState()
                    : r < 64 ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        } else if (over.isAir() && ground == null && r < 30) {
            level.setBlock(at, Blocks.SHORT_DRY_GRASS.defaultBlockState(), 2);
        }
    }

    /** The town's stone lying about, as the desert's: cobbles and bricks sandstone, their slabs sandstone slabs. */
    private static BlockState sandstone(BlockState state) {
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.STONE)) {
            return Blocks.SANDSTONE.defaultBlockState();
        }
        if (state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS)) {
            return Blocks.CUT_SANDSTONE.defaultBlockState();
        }
        if (state.is(Blocks.COBBLESTONE_SLAB) || state.is(Blocks.MOSSY_COBBLESTONE_SLAB)) {
            return Blocks.SANDSTONE_SLAB.withPropertiesOf(state);
        }
        if (state.is(Blocks.STONE_BRICK_SLAB) || state.is(Blocks.MOSSY_STONE_BRICK_SLAB)) {
            return Blocks.SMOOTH_SANDSTONE_SLAB.withPropertiesOf(state);
        }
        if (state.is(Blocks.GRAVEL)) {
            return Blocks.SAND.defaultBlockState();
        }
        return state;
    }

    /** Whether a cactus can stand at ({@code x}, {@code y}, {@code z}): on sand, nothing beside it for three up. */
    private static boolean cactusRoom(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int y, int z) {
        if (!level.getBlockState(at.set(x, y - 1, z)).is(Blocks.SAND)) {
            return false;
        }
        for (int dy = 0; dy < 3; dy++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!level.getBlockState(at.set(x + d.getStepX(), y + dy, z + d.getStepZ())).isAir()) {
                    return false;
                }
            }
            if (!level.getBlockState(at.set(x, y + dy, z)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** The old stone holding up higher ground, here sandstone, in patches. */
    static BlockState wall(long seed, int x, int y, int z) {
        double v = Town.noise(seed, x + y * 7, z - y * 5, 3.0, 305);
        return (v < 0.45 ? Blocks.SANDSTONE : v < 0.7 ? Blocks.SMOOTH_SANDSTONE : v < 0.9 ? Blocks.CUT_SANDSTONE : Blocks.CHISELED_SANDSTONE)
                .defaultBlockState();
    }

    // --- the buildings worn down -----------------------------------------------------------------------------------------

    /**
     * How many blocks of {@code lot}'s building still stand above its floor at ({@code x}, {@code z}): a ragged line a block
     * to five high, a jag of wall higher now and then; the game's desert houses (low already) a block more.
     */
    public static int standing(Town.Lot lot, int x, int z, boolean vanilla, int sheltered) {
        long seed = lot.seed();
        int keep = 1 + (int) Math.floor(Town.noise(seed, x, z, 6.0, 310) * 4.4);
        if (Town.noise(seed, x, z, 2.5, 311) > 0.8) {
            keep += 2;                                                  // a jag of wall left higher
        }
        return keep + (vanilla ? 1 : 0) + sheltered;
    }

    /**
     * How much more of a building near the desert's edge still stands ({@link #standing}): the sand thinner there, the
     * buildings at its very edge hardly worn.
     */
    public static int sheltered(long seed, Town.Lot lot) {
        double in = Region.inside(seed, lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2);
        return (int) Math.round(Math.max(0.0, 1.0 - in / (EDGE * 1.5)) * 10.0);
    }

    /**
     * The desert's own buildings (drafted 2026-10-18: a cistern house, a caravan stable, a courtyard house, a bazaar corner,
     * a dig camp, a caravanserai): thick-walled and built for the sand, more of each still stands ({@link #NATIVE_KEEP}).
     */
    static final java.util.Set<String> NATIVE = java.util.Set.of("xs22", "xs23", "xs24", "xs25", "s26", "s27");
    static final int NATIVE_KEEP = 3;
    /**
     * The desert's two landmarks, the tall timber workshop and inn from the Nookworks build comparison (handed over
     * 2026-10-18; Gemini 4 Argon's M16, Claude Opus 5.5's M17): rare, and worn only at their tops - roofs, towers and the
     * last floor fallen in, the floors below still standing.
     */
    static final java.util.Set<String> LANDMARK = java.util.Set.of("m16", "m17");
    static final int LANDMARK_KEEP = 16;

    /** Whether {@code lot}'s building is one of the desert's two landmarks ({@link #LANDMARK}): kept in its own timber. */
    public static boolean landmark(Town.Lot lot) {
        return built(lot) == LANDMARK_KEEP;
    }

    /** How much more of {@code lot}'s building stands for being one of the desert's own ({@link #NATIVE}). */
    public static int built(Town.Lot lot) {
        String name = lot.building().filter(id -> id.getNamespace().equals(NookOfTheDead.MODID) && id.getPath().startsWith("town/"))
                .map(id -> id.getPath().substring("town/".length())).orElse("");
        return LANDMARK.contains(name) ? LANDMARK_KEEP : NATIVE.contains(name) ? NATIVE_KEEP : 0;
    }

    /** Where the sand put in a building ({@code template}) can be brushed: the finds of a desert dig in each. */
    static void finds(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box, long seed) {
        int i = 0;
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.SUSPICIOUS_SAND)) {
            if (info.nbt() != null && (info.nbt().contains("LootTable") || info.nbt().contains("item"))) {
                continue;                                                // what the building's maker buried there stays
            }
            if (box.isInside(info.pos()) && level.getBlockEntity(info.pos()) instanceof net.minecraft.world.level.block.entity.BrushableBlockEntity sand
                    && sand.getItem().isEmpty()) {
                sand.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, seed + i++);
            }
        }
    }

    /** Whether what is at {@code pos} of {@code lot}'s building is gone with the rest of what stood above its footings. */
    public static boolean worn(Town.Lot lot, BlockPos pos, boolean vanilla, int sheltered) {
        return pos.getY() > lot.corner().getY() + standing(lot, pos.getX(), pos.getZ(), vanilla, sheltered);
    }

    /**
     * Whether {@code state} hangs from what is over it - a lantern, a bell from a beam, a chain: gone with the beam (or the
     * chain it hung by) when the wall was worn down to under it (the review, 2026-10-18: bells and lanterns left in mid-air).
     */
    private static boolean hung(BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING)
                || state.getBlock() instanceof net.minecraft.world.level.block.BellBlock
                && state.getValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT) == net.minecraft.world.level.block.state.properties.BellAttachType.CEILING
                || state.getBlock() instanceof net.minecraft.world.level.block.ChainBlock
                && state.getValue(net.minecraft.world.level.block.ChainBlock.AXIS) == Direction.Axis.Y;
    }

    /**
     * What wears a desert lot's building as it is set down: above its footings ({@link #standing}) nothing - blocks nor the
     * things in it; below, no glass, no cloth, no leaves, no doors, no beds (gone to the sun and the sand).
     */
    static StructureProcessor erode(Town.Lot lot, boolean vanilla, int sheltered) {
        return new StructureProcessor() {
            @Override
            public StructureTemplate.@Nullable StructureBlockInfo process(LevelReader level, BlockPos target, BlockPos reference,
                    StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo processed, StructurePlaceSettings settings,
                    @Nullable StructureTemplate template) {
                if (processed == null) {
                    return null;
                }
                BlockState state = processed.state();
                if (worn(lot, processed.pos(), vanilla, sheltered) || hung(state) && (worn(lot, processed.pos().above(), vanilla, sheltered)
                        || worn(lot, processed.pos().above(2), vanilla, sheltered)) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)
                        || state.is(net.minecraft.tags.BlockTags.WOOL) || state.is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
                        || state.getBlock() instanceof LeavesBlock || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof BedBlock
                        || state.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
                    return new StructureTemplate.StructureBlockInfo(processed.pos(), Blocks.AIR.defaultBlockState(), null);
                }
                return processed;
            }

            @Override
            public StructureTemplate.StructureEntityInfo processEntity(LevelReader level, BlockPos seed, StructureTemplate.StructureEntityInfo raw,
                    StructureTemplate.StructureEntityInfo info, StructurePlaceSettings settings, StructureTemplate template) {
                return info == null || worn(lot, info.blockPos, vanilla, sheltered) ? null : info;    // with the wall it hangs on
            }

            @Override
            public MapCodec<? extends StructureProcessor> codec() {
                return MapCodec.unit(this);                               // made in place, never written out
            }
        };
    }

    /**
     * Over a worn building's lot, its part in {@code box}: the earth of its ground sand, and sand drifted in on its floors -
     * nothing to two deep, deepest where the noise says, against what stands.
     */
    static void drift(WorldGenLevel level, Town.Lot lot, BoundingBox box, boolean vanilla, int sheltered) {
        sandOver(level, lot, box);
        BoundingBox foot = lot.footprint();
        int floor = lot.corner().getY();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(foot.minX(), box.minX()); x <= Math.min(foot.maxX(), box.maxX()); x++) {
            for (int z = Math.max(foot.minZ(), box.minZ()); z <= Math.min(foot.maxZ(), box.maxZ()); z++) {
                int deep = (int) Math.floor(Town.noise(lot.seed(), x, z, 4.0, 320) * 2.8);
                if (deep <= 0) {
                    continue;
                }
                // only on a floor (the ground or a step up from it): never on top of what is left of a wall (the review,
                // 2026-10-18: the sand capped the walls' stubs, false battlements)
                int top = Math.min(floor + 2, floor + standing(lot, x, z, vanilla, sheltered) + 1);
                for (int y = floor; y <= top; y++) {
                    if (level.getBlockState(at.set(x, y + 1, z)).isAir()
                            && level.getBlockState(at.set(x, y, z)).isFaceSturdy(level, at, Direction.UP)) {
                        for (int k = 1; k <= deep && level.getBlockState(at.set(x, y + k, z)).isAir(); k++) {
                            level.setBlock(at, Blocks.SAND.defaultBlockState(), 2);
                        }
                        break;
                    }
                }
            }
        }
    }

    /** The earth of {@code lot}'s own ground, its part in {@code box}, sand; its weeds gone. */
    private static void sandOver(WorldGenLevel level, Town.Lot lot, BoundingBox box) {
        BoundingBox foot = lot.footprint();
        int floor = lot.corner().getY();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(foot.minX(), box.minX()); x <= Math.min(foot.maxX(), box.maxX()); x++) {
            for (int z = Math.max(foot.minZ(), box.minZ()); z <= Math.min(foot.maxZ(), box.maxZ()); z++) {
                for (int y = floor - 2; y <= floor + 1; y++) {
                    BlockState state = level.getBlockState(at.set(x, y, z));
                    if (earth(state) && (state.is(Blocks.GRASS_BLOCK) || level.getBlockState(at.set(x, y + 1, z)).isAir())) {
                        level.setBlock(at.set(x, y, z), Blocks.SAND.defaultBlockState(), 2);
                    } else if (green(state)) {
                        level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    // --- the pyramids ----------------------------------------------------------------------------------------------------

    /** The game's pyramid, set down at a height of our choosing rather than the ground's it would look for itself. */
    private static final class Pyramid extends DesertPyramidPiece {
        Pyramid(RandomSource random, int west, int north, int floor) {
            super(random, west, north);
            this.boundingBox.move(0, floor - this.boundingBox.minY(), 0);
            this.heightPosition = floor;
        }

        BoundingBox box() {
            return this.boundingBox;
        }
    }

    /** Whether {@code lot}'s pyramid was dug out before anyone came here: one in two. */
    public static boolean looted(Town.Lot lot) {
        return (Town.hash(lot.seed(), 9, 0, 330) & 1) == 0;
    }

    /** {@code lot}'s pyramid, its part in {@code box}: as it was left, or already dug out ({@link #looted}). */
    static void pyramid(WorldGenLevel level, Town.Lot lot, BoundingBox box) {
        sandOver(level, lot, box);
        int west = lot.corner().getX() + (lot.size() - 21) / 2;
        int north = lot.corner().getZ() + (lot.size() - 21) / 2;
        Pyramid piece = new Pyramid(RandomSource.create(lot.seed()), west, north, lot.corner().getY());
        var server = level.getLevel();
        piece.postProcess(level, server.structureManager(), server.getChunkSource().getGenerator(), RandomSource.create(lot.seed() ^ 0x9A3), box,
                new ChunkPos(box.minX() >> 4, box.minZ() >> 4), BlockPos.ZERO);
        BoundingBox all = piece.box();
        // told apart before the box is made: an inverted one is swapped by the game (and logged as an error), so this chunk
        // outside the pyramid was never skipped (the play test, 2026-10-19: eight error lines for two pyramids)
        if (all.minX() > box.maxX() || all.maxX() < box.minX() || all.minZ() > box.maxZ() || all.maxZ() < box.minZ()) {
            return;
        }
        BoundingBox inner = new BoundingBox(Math.max(all.minX(), box.minX()), Math.max(all.minY() - 16, box.minY()), Math.max(all.minZ(), box.minZ()),
                Math.min(all.maxX(), box.maxX()), Math.min(all.maxY(), box.maxY()), Math.min(all.maxZ(), box.maxZ()));
        boolean looted = looted(lot);
        for (BlockPos p : BlockPos.betweenClosed(inner.minX(), inner.minY(), inner.minZ(), inner.maxX(), inner.maxY(), inner.maxZ())) {
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.CHEST) && level.getBlockEntity(p) instanceof RandomizableContainer chest) {
                if (looted) {
                    chest.setLootTable(null);                            // taken: nothing left in it
                } else {
                    chest.setLootTable(PYRAMID_LOOT, Town.hash(lot.seed(), p.getX(), p.getZ(), 331));
                }
            } else if (looted && (state.is(Blocks.TNT) || state.is(Blocks.STONE_PRESSURE_PLATE))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);    // the trap taken up
            }
        }
        if (!looted) {
            return;
        }
        // the way they dug in: through the floor over the trap's shaft (the game's own, under the coloured floor), a ladder
        // down it to the treasure room, their torches left about the hole
        int cx = all.minX() + 10;
        int cz = all.minZ() + 10;
        int floor = all.minY();
        for (int dz = -1; dz <= 0; dz++) {
            set(level, box, cx, floor, cz + dz, Blocks.AIR.defaultBlockState());
        }
        for (int y = floor - 9; y < floor; y++) {                      // not the lowest: the side opening is behind it in some turns
            set(level, box, cx, y, cz - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        for (int[] d : new int[][] {{2, 1}, {-2, -1}, {1, -3}}) {
            BlockPos torch = new BlockPos(cx + d[0], floor + 1, cz + d[1]);
            if (box.isInside(torch) && level.getBlockState(torch).isAir()) {
                level.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
            }
        }
    }

    // --- what players left -----------------------------------------------------------------------------------------------

    /**
     * On a desert lot with nothing built, its part in {@code box}, what players left there (or nothing, one in three): on
     * the open ground a camp walled with sandstone (a fire gone out, a chest, a bed's frame); on a field one gone dry, its
     * furrows and its empty ditch; on a heap of rubble a shaft sunk into the ground, a ladder down it, a cart track to it
     * and the cart left on it.
     */
    static void traces(WorldGenLevel level, Town.Lot lot, BoundingBox box, java.util.function.IntBinaryOperator ground) {
        long h = Town.hash(lot.seed(), 7, 0, 340);
        if (Math.floorMod(h, 3L) == 0) {
            return;
        }
        // each on the lot's own ground as it lies (the review, 2026-10-18: not at the street's height before it)
        int cx = lot.corner().getX() + lot.size() / 2;
        int cz = lot.corner().getZ() + lot.size() / 2;
        switch (lot.use()) {
            case YARD -> camp(level, lot, box, cx, cz, ground, h);
            case FIELD -> dryField(level, lot, box, ground, h);
            case RUBBLE -> shaft(level, lot, box, cx, cz, ground, h);
            default -> {
            }
        }
    }

    private static void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        if (box.isInside(p)) {
            level.setBlock(p, state, 2);
        }
    }

    /** A camp: a ring of sandstone two high round a 7 x 7, a gap for a way in, a fire gone out, a chest, a bed's frame. */
    private static void camp(WorldGenLevel level, Town.Lot lot, BoundingBox box, int cx, int cz, java.util.function.IntBinaryOperator ground, long h) {
        int gap = (int) Math.floorMod(h >> 8, 4L);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                int floor = ground.applyAsInt(cx + dx, cz + dz);
                for (int y = floor; y <= floor + 2; y++) {
                    set(level, box, cx + dx, y + 1, cz + dz, Blocks.AIR.defaultBlockState());
                }
                set(level, box, cx + dx, floor, cz + dz, Blocks.SAND.defaultBlockState());
                if (!edge) {
                    continue;
                }
                Direction side = dz == -4 ? Direction.NORTH : dz == 4 ? Direction.SOUTH : dx == -4 ? Direction.WEST : Direction.EAST;
                boolean door = side.get2DDataValue() == gap && Math.abs(side.getAxis() == Direction.Axis.X ? dz : dx) <= 1;
                int high = (int) Math.floorMod(Town.hash(lot.seed(), cx + dx, cz + dz, 341), 3L);      // some of it fallen
                for (int y = 1; y <= 2 && !door; y++) {
                    if (y == 2 && high == 0) {
                        break;
                    }
                    set(level, box, cx + dx, floor + y, cz + dz, (high == 1 ? Blocks.SANDSTONE_WALL : Blocks.SANDSTONE).defaultBlockState());
                }
            }
        }
        set(level, box, cx, ground.applyAsInt(cx, cz) + 1, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        BlockPos chest = new BlockPos(cx + 3, ground.applyAsInt(cx + 3, cz - 3) + 1, cz - 3);
        if (box.isInside(chest)) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
            if (level.getBlockEntity(chest) instanceof RandomizableContainer c) {
                c.setLootTable(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, NookOfTheDead.id("chests/supplies")),
                        Town.hash(lot.seed(), 1, 1, 342));
            }
        }
        set(level, box, cx - 3, ground.applyAsInt(cx - 3, cz + 3) + 1, cz + 3, Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, box, cx - 3, ground.applyAsInt(cx - 3, cz + 2) + 1, cz + 2, Blocks.SPRUCE_SLAB.defaultBlockState());                // the bed's frame, the bed gone
        set(level, box, cx - 3, ground.applyAsInt(cx - 3, cz + 1) + 1, cz + 1, Blocks.SPRUCE_SLAB.defaultBlockState());
        set(level, box, cx + 3, ground.applyAsInt(cx + 3, cz + 3) + 1, cz + 3, Blocks.BARREL.defaultBlockState());
    }

    /** A field gone dry: furrows of bare farmland, dead bushes in some rows, the ditch down the middle empty. */
    private static void dryField(WorldGenLevel level, Town.Lot lot, BoundingBox box, java.util.function.IntBinaryOperator ground, long h) {
        BoundingBox foot = lot.footprint();
        boolean alongX = (h >> 9 & 1) == 0;
        for (int x = Math.max(foot.minX() + 2, box.minX()); x <= Math.min(foot.maxX() - 2, box.maxX()); x++) {
            for (int z = Math.max(foot.minZ() + 2, box.minZ()); z <= Math.min(foot.maxZ() - 2, box.maxZ()); z++) {
                int across = alongX ? z - foot.minZ() : x - foot.minX();
                int floor = ground.applyAsInt(x, z);
                if (across == lot.size() / 2) {                          // the ditch, no water in it
                    set(level, box, x, floor, z, Blocks.AIR.defaultBlockState());
                    set(level, box, x, floor - 1, z, Blocks.SAND.defaultBlockState());
                    continue;
                }
                if (across % 3 == 0) {
                    continue;                                            // a path between the beds
                }
                // furrows of dry earth, not farmland: with nothing over it farmland went back to dirt in minutes (the
                // review, 2026-10-19)
                set(level, box, x, floor, z, (across % 3 == 1 ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState());
                set(level, box, x, floor + 1, z, Math.floorMod(Town.hash(lot.seed(), x, z, 343), 7L) == 0
                        ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** A shaft sunk into the ground: three across, a ladder down it to the depth they got to, a track to it, the cart left. */
    private static void shaft(WorldGenLevel level, Town.Lot lot, BoundingBox box, int cx, int cz, java.util.function.IntBinaryOperator ground, long h) {
        int depth = 10 + (int) Math.floorMod(h >> 10, 8L);
        int floor = ground.applyAsInt(cx, cz);                           // the shaft's own level, its middle's ground
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (rim) {
                    set(level, box, cx + dx, floor, cz + dz, Blocks.COBBLESTONE.defaultBlockState());
                    continue;
                }
                for (int y = floor - depth; y <= floor + 2; y++) {
                    set(level, box, cx + dx, y, cz + dz, Blocks.AIR.defaultBlockState());
                }
                set(level, box, cx + dx, floor - depth - 1, cz + dz, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        for (int y = floor - depth; y <= floor; y++) {                    // the ladder down the north wall
            set(level, box, cx, y, cz - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            if (y % 5 == 0) {
                set(level, box, cx + 1, y, cz, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST));
            }
        }
        // the spoil heap beside it, and the track running off from it
        for (int k = 0; k < 6; k++) {
            int sx = cx + 4 + (int) Math.floorMod(Town.hash(lot.seed(), k, 0, 344), 3L);
            int sz = cz - 2 + (int) Math.floorMod(Town.hash(lot.seed(), k, 1, 344), 5L);
            set(level, box, sx, ground.applyAsInt(sx, sz) + 1, sz, (k % 2 == 0 ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState());
        }
        // the track kept to the lot (the review, 2026-10-18: it ran on into the street and stopped at a chunk's edge)
        int length = Math.min(8 + (int) Math.floorMod(h >> 14, 6L), lot.footprint().maxZ() - cz - 2);
        for (int k = 3; k < 3 + length; k++) {
            int rail = ground.applyAsInt(cx, cz + k);
            if (Math.floorMod(Town.hash(lot.seed(), k, 2, 345), 5L) == 0) {
                continue;                                                // a rail gone
            }
            set(level, box, cx, rail, cz + k, Blocks.SAND.defaultBlockState());
            set(level, box, cx, rail + 1, cz + k, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                    RailShape.NORTH_SOUTH));
        }
        BlockPos cart = new BlockPos(cx, ground.applyAsInt(cx, cz + 4) + 1, cz + 4);
        if (box.isInside(cart)) {
            var minecart = net.minecraft.world.entity.EntityTypes.MINECART.create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (minecart != null) {
                minecart.snapTo(cart.getX() + 0.5, cart.getY(), cart.getZ() + 0.5, 0.0F, 0.0F);
                level.addFreshEntity(minecart);
            }
        }
    }

    private Desert() {}
}
