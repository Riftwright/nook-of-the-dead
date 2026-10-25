package dev.nookofthedead.world;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The fallen world's biomes, one to a {@link Region}: what F3 shows, the grass and the sky, and who spawns. It answers by
 * place alone, from the world's seed - the same grid the town is laid out on. Like the generator it serves
 * ({@link FallenTownGenerator}) it is never written into a world's list of dimensions: it has no codec of its own.
 */
public class RegionBiomes extends BiomeSource {
    private final long seed;
    private final Map<Region, Holder<Biome>> biomes = new EnumMap<>(Region.class);
    /** Below {@link Tunnels#DEPTHS}: the galleries' own. */
    private final Holder<Biome> depths;
    /** From {@link #STRATOSPHERE} up: the thin air, its own sky (0.3). */
    private final Holder<Biome> thin;
    /** Where the stratosphere begins: the If's sky goes dark blue, the clouds lie below. */
    public static final int STRATOSPHERE = 352;

    public RegionBiomes(HolderGetter<Biome> lookup, long seed) {
        this.seed = seed;
        for (Region region : Region.values()) {
            this.biomes.put(region, lookup.getOrThrow(region.biome));
        }
        this.depths = lookup.getOrThrow(DEPTHS);
        this.thin = lookup.getOrThrow(THIN_AIR);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("the fallen world's biomes are made with its generator, never read or written");
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return java.util.stream.Stream.concat(this.biomes.values().stream(), Stream.of(this.depths, this.thin));
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this::biome;
    }

    /** The biome at quart ({@code quartX}, {@code quartZ}) (a quart is four blocks), at any height. */
    public Holder<Biome> biome(int quartX, int quartY, int quartZ) {
        if (quartY * 4 + 2 < Tunnels.DEPTHS) {
            return this.depths;
        }
        if (quartY * 4 >= STRATOSPHERE) {
            return this.thin;
        }
        return this.biomes.get(Region.looks(this.seed, quartX * 4 + 2, quartZ * 4 + 2));     // its edge wandering, not ruled
    }

    public static final net.minecraft.resources.ResourceKey<Biome> DEPTHS = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.BIOME, dev.nookofthedead.NookOfTheDead.id("fallen_depths"));

    public static final net.minecraft.resources.ResourceKey<Biome> THIN_AIR = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.BIOME, dev.nookofthedead.NookOfTheDead.id("stratosphere"));

    public long seed() {
        return this.seed;
    }
}
