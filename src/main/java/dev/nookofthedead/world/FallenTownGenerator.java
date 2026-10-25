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
    }
}
