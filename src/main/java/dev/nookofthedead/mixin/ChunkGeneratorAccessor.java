package dev.nookofthedead.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** The fallen world's generator takes its biomes from the place ({@link dev.nookofthedead.world.RegionBiomes}), not one fixed. */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Mutable
    @Accessor("biomeSource")
    void nookofthedead$setBiomeSource(BiomeSource source);
}
