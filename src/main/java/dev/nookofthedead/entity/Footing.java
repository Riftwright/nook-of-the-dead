package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What the plague's dead may come out standing on. Vanilla's ground rule kept none off glass, a bottom slab or a stair, and
 * a floor of them round a base was a safe ground as good as a lit roof and cheaper than Dark Matter (the user, 2026-10-21:
 * "ぬるくない？"). So:
 * <ul>
 * <li>On their own ({@link #NATURAL}): vanilla's ground, and glass and bottom slabs as well - not stairs, so the town's
 *     roofs (stairs and slabs) do not fill with them.</li>
 * <li>A horde's runners ({@link #runner}): anything with a floor to stand on, from a bottom slab to a full block - glass,
 *     stairs, bedrock, a glowing block too; never leaves (the treetops), a fence or a wall (too tall to stand on), magma,
 *     a barrier, or a block of this mod's that keeps them off.</li>
 * </ul>
 * Where none come at all is unchanged: under a lit roof, within Dark Matter, in the refuge ({@link MutatedZombie#safe}).
 */
public final class Footing {
    /** The plague's own ground, for natural spawning: vanilla's, glass and bottom slabs too. */
    public static final SpawnPlacementType NATURAL = new SpawnPlacementType() {
        @Override
        public boolean isSpawnPositionOk(LevelReader level, BlockPos pos, @Nullable EntityType<?> type) {
            if (SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, type)) {
                return true;
            }
            if (type == null || !level.getWorldBorder().isWithinBounds(pos) || !room(level, pos, type)) {
                return false;
            }
            return floor(level.getBlockState(pos.below()));
        }

        @Override
        public BlockPos adjustSpawnPosition(LevelReader level, BlockPos candidate) {
            return SpawnPlacementTypes.ON_GROUND.adjustSpawnPosition(level, candidate);
        }
    };

    /** The plague's floors beyond vanilla's ground: glass, and a bottom slab. */
    public static boolean floor(BlockState below) {
        return below.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)
                || below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** Whether a horde's runner of {@code type} could come out standing at {@code pos}. */
    public static boolean runner(LevelReader level, BlockPos pos, EntityType<?> type) {
        if (!level.getWorldBorder().isWithinBounds(pos) || !room(level, pos, type)) {
            return false;
        }
        BlockPos under = pos.below();
        BlockState below = level.getBlockState(under);
        if (below.is(BlockTags.LEAVES) || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.BARRIER)
                || BuiltInRegistries.BLOCK.getKey(below.getBlock()).getNamespace().equals(NookOfTheDead.MODID)) {
            return false;
        }
        if (below.isFaceSturdy(level, under, Direction.UP) && below.isValidSpawn(level, under, type)) {
            return true;
        }
        var shape = below.getCollisionShape(level, under);
        if (shape.isEmpty()) {
            return false;
        }
        double top = shape.max(Direction.Axis.Y);
        return top >= 0.5 && top <= 1.0;
    }

    /** Room for one at {@code pos}: its feet and head clear, as vanilla's spawning asks. */
    private static boolean room(LevelReader level, BlockPos pos, EntityType<?> type) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return NaturalSpawner.isValidEmptySpawnBlock(level, pos, feet, feet.getFluidState(), type)
                && NaturalSpawner.isValidEmptySpawnBlock(level, pos.above(), head, head.getFluidState(), type);
    }

    private Footing() {}
}
