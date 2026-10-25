package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Small things the 0.3 places share, to look lived in and lost rather than generated (the visual review, 2026-10-19):
 * stains sunk flush in the floor in blobs (not carpets, which read as doormats), bones lying down with a skull (not an
 * upright bone block, which read as a cream cube), brewing stands with something in them, lamps hung from a ceiling.
 * Each draws only inside {@code box}, from the seed and the place alone.
 */
final class Dress {
    /** Whether the floor at ({@code x}, {@code z}) is stained: a blob of the noise, darkest at its middle. */
    static Block stain(long seed, int x, int z, long salt) {
        double n = Town.noise(seed, x, z, 2.0, salt);
        return n > 0.88 ? Blocks.DYED_TERRACOTTA.black() : n > 0.80 ? Blocks.DYED_TERRACOTTA.brown() : null;
    }

    /** Bones lying at {@code at} (in the floor's place, {@code sunk}, or on it), and now and then a skull on them. */
    static void bones(WorldGenLevel level, BoundingBox box, BlockPos at, long h, boolean sunk) {
        BlockPos bone = sunk ? at.below() : at;
        put(level, box, bone, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                (h & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z));
        if (((h >> 1) & 1) == 0) {
            put(level, box, bone.above(), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, (int) Math.floorMod(h >> 2, 16L)));
        }
    }

    /** A brewing stand at {@code at}, with what was left in it. */
    static void brewingStand(WorldGenLevel level, BoundingBox box, BlockPos at, long h) {
        if (!box.isInside(at)) {
            return;
        }
        level.setBlock(at, Blocks.BREWING_STAND.defaultBlockState(), 2);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand) {
            Markers.brew(stand, RandomSource.create(h));
            BlockState state = Blocks.BREWING_STAND.defaultBlockState();
            for (int slot = 0; slot < 3; slot++) {
                state = state.setValue(net.minecraft.world.level.block.BrewingStandBlock.HAS_BOTTLE[slot], !stand.getItem(slot).isEmpty());
            }
            level.setBlock(at, state, 2);
        }
    }

    /** A lantern hung from the block above {@code at}. */
    static void hangingLantern(WorldGenLevel level, BoundingBox box, BlockPos at) {
        put(level, box, at, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    static void put(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private Dress() {}
}
