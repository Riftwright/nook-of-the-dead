package dev.nookofthedead.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.FallenTownGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Where the If is made (Nookworks' {@code dev.nookworks.ifworld.IfLevel#open}): its ground from {@link FallenTownGenerator}
 * rather than the overworld's double, and none of the overworld's own spawners (phantoms, patrols,
 * cats, sieges, the wandering trader). Its seed, mirror and gates stay Nookworks'. Only the If's level is touched, and only
 * as it is made; a world made before this mod keeps the If chunks it has (Nookworks' {@code /nookworks if reset} clears them).
 */
@Mixin(targets = "dev.nookworks.ifworld.IfLevel")
abstract class IfLevelMixin {
    @ModifyArg(method = "open", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/LevelStem;<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/OptionalLong;)V"),
            index = 1)
    private static ChunkGenerator nookofthedead$fallenGround(ChunkGenerator generator) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return generator;
        }
        NookOfTheDead.LOGGER.info("The If is the fallen town");
        long seed = dev.nookworks.ifworld.IfWorld.seedOf(dev.nookworks.ifworld.IfWorld.get(server).number());
        return FallenTownGenerator.create(server.registryAccess().lookupOrThrow(Registries.BIOME), seed);
    }

    /**
     * The If's own dimension type ({@code nookofthedead:fallen}): the overworld's in all but its height - 512, up to y 447,
     * for the sky base at the top of the way (0.3; the Nookworks session, 2026-10-19: coordinate scale 1.0 and the
     * overworld's attributes kept, and nothing in Nookworks reads the If's height). An If made before opens taller, the
     * new air above its old ground empty.
     */
    @ModifyArg(method = "open", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/LevelStem;<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/OptionalLong;)V"),
            index = 0)
    private static net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> nookofthedead$taller(
            net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> type) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return type;
        }
        return server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).get(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION_TYPE, NookOfTheDead.id("fallen"))).<net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType>>map(h -> h)
                .orElse(type);
    }

    @ModifyArg(method = "open", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/world/level/storage/ServerLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/world/level/dimension/LevelStem;ZJLjava/util/List;Z)V"),
            index = 8)
    private static List<CustomSpawner> nookofthedead$noSpawners(List<CustomSpawner> spawners) {
        return List.of();
    }
}
