package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/**
 * The fallen world's ground (PLAN.md: "ほぼ平坦…基本的には用意した建造物で埋め尽くされている"): flat - bedrock at
 * {@link #BOTTOM}, stone, dirt, grass to {@link Town#SURFACE}, nothing below - in biomes of its own, one to a region
 * ({@link Region}, {@link RegionBiomes}: only the dead come, no animals), no vanilla structures, and on it the town
 * ({@link Town}) and the country round it ({@link Country}), laid out chunk by chunk as each is decorated.
 * <p>
 * It stands in for Nookworks' If generator ({@code IfLevelMixin}): the If keeps its seed, its mirror, its gates. Like
 * Nookworks' own it is never written into a world's list of dimensions, so it answers to the flat generator's codec.
 */
public class FallenTownGenerator extends FlatLevelSource {
    public static final ResourceKey<Biome> BIOME = ResourceKey.create(Registries.BIOME, NookOfTheDead.id("fallen_town"));
    /** The bedrock's height: the ground goes no deeper, and under it there is nothing. */
    public static final int BOTTOM = 0;

    public FallenTownGenerator(FlatLevelGeneratorSettings settings) {
        super(settings);
    }

    /** The generator, its biomes the regions' ({@link RegionBiomes}) for the If of {@code seed}. */
    public static FallenTownGenerator create(HolderGetter<Biome> biomes, long seed) {
        Holder<Biome> biome = biomes.getOrThrow(BIOME);
        List<FlatLayerInfo> layers = new ArrayList<>();
        // nothing under y 0, bedrock at it (the user, 2026-10-13: "地下はy0くらいまででよくない？あんまり地下に潜るゲームじゃ
        // ないし"): stone from y 1, then six of dirt, the grass at the town's surface
        layers.add(new FlatLayerInfo(BOTTOM - (-64), Blocks.AIR));
        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        layers.add(new FlatLayerInfo(Town.SURFACE - BOTTOM - 7, Blocks.STONE));
        layers.add(new FlatLayerInfo(6, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
        Optional<HolderSet<net.minecraft.world.level.levelgen.structure.StructureSet>> none = Optional.of(HolderSet.direct(List.of()));
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(none, biome, List.of()).withBiomeAndLayers(layers, none, biome);
        FallenTownGenerator generator = new FallenTownGenerator(settings);
        ((dev.nookofthedead.mixin.ChunkGeneratorAccessor) generator).nookofthedead$setBiomeSource(new RegionBiomes(biomes, seed));
        return generator;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return FlatLevelSource.CODEC;
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        Town.decorate(level, chunk);
        halves(chunk);
        sand(level, chunk);
    }

    /**
     * Grass, ferns, flowers and berry bushes standing on sand once the chunk is drawn - the desert's sand drifted under
     * what grew there, a yard's flowers at its edge (the audit, 2026-11-15) - taken away; what grows on sand stays.
     */
    private static void sand(WorldGenLevel level, ChunkAccess chunk) {
        net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
                for (int y = top + 1; y >= Math.max(chunk.getMinY() + 1, top - 30); y--) {
                    net.minecraft.world.level.block.state.BlockState state = chunk.getBlockState(at.set(x, y, z));
                    if (state.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock
                            && !(state.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock)
                            && chunk.getBlockState(at.set(x, y - 1, z)).is(net.minecraft.tags.BlockTags.SAND)
                            && !state.canSurvive(level, at.set(x, y, z))) {
                        chunk.setBlockState(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
                    }
                }
            }
        }
    }

    /**
     * Tall plants that lost a half once the chunk was drawn - a tuft of tall grass whose foot a cart, a barricade or a
     * fence was set down over after it (the audit, 2026-11-15: grass tops standing on fences, slabs and walls) - taken away.
     * Within the chunk only, so the same from every order.
     */
    private static void halves(ChunkAccess chunk) {
        net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        net.minecraft.core.BlockPos.MutableBlockPos other = new net.minecraft.core.BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < chunk.getSections().length; i++) {
            var section = chunk.getSections()[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock)) {
                continue;
            }
            int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        net.minecraft.world.level.block.state.BlockState state = section.getBlockState(x, y, z);
                        if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock)
                                || !state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
                            continue;
                        }
                        boolean lower = state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
                        at.set(x0 + x, y0 + y, z0 + z);
                        other.set(at.getX(), at.getY() + (lower ? 1 : -1), at.getZ());
                        if (other.getY() < chunk.getMinY() || other.getY() > chunk.getMaxY()) {
                            continue;
                        }
                        net.minecraft.world.level.block.state.BlockState there = chunk.getBlockState(other);
                        if (!there.is(state.getBlock()) || there.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)
                                == state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
                            chunk.setBlockState(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
                        }
                    }
                }
            }
        }
    }
}
