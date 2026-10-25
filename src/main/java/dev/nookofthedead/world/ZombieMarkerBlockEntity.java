package dev.nookofthedead.world;

import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.registry.ZwBlocks;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The waiting behind a {@link ZombieMarkerBlock}: twice a second, is anyone (not in creative or spectator) near? */
public class ZombieMarkerBlockEntity extends BlockEntity {
    /** How near someone has to come. */
    public static final double REACH = 20.0;

    public ZombieMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ZwBlocks.ZOMBIE_MARKER_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ZombieMarkerBlockEntity marker) {
        if (level.getGameTime() % 10 != 0 || !(level instanceof ServerLevel server) || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (dev.nookofthedead.ending.Cure.cured(server.getServer())) {
            return;              // cured: nobody comes out while the vaccine goes out - and again if it is stopped (2026-10-21)
        }
        // the nearest not watching a film (one watching is not there for them: 2026-10-21, a friend beside them was)
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, REACH,
                EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(e -> !(e instanceof Player p && dev.nookofthedead.ending.Film.watching(p))));
        if (near == null || Sanctuary.near(server, pos)) {
            return;                                                     // in a Dark Matter's reach they stay where they are
        }
        release(server, pos, state, near);
    }

    /**
     * Out they come round the marker as {@code prey} comes near, and the marker goes: standing where they were left
     * ({@link dev.nookofthedead.entity.Mind#stand}), till they see someone or hear something - not knowing who comes
     * through the walls. One in {@link dev.nookofthedead.entity.Horde#VILLAGERS} was a villager.
     */
    public static void release(ServerLevel level, BlockPos pos, BlockState state, Player prey) {
        // one more on Hard, one fewer on Easy, never none (Hardness, 2026-10-23)
        int count = Math.max(1, state.getValue(ZombieMarkerBlock.COUNT) + dev.nookofthedead.entity.Hardness.of(level).marked());
        boolean elite = state.getValue(ZombieMarkerBlock.ELITE);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (int i = 0; i < count; i++) {
            BlockPos at = roomNear(level, pos, i);
            // up in the thin air, its own dead (0.3)
            net.minecraft.world.entity.monster.zombie.Zombie runner = (pos.getY() >= RegionBiomes.STRATOSPHERE ? ZwEntities.THIN_AIR_ZOMBIE.get()
                    : elite ? ZwEntities.MUTATED_ZOMBIE.get()
                    : dev.nookofthedead.entity.Horde.runner(level, at, true)).create(level, EntitySpawnReason.TRIGGERED);
            if (runner == null) {
                continue;
            }
            runner.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
            runner.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
            if (elite && runner instanceof MutatedZombie fallen && !(runner instanceof dev.nookofthedead.entity.ThinAirZombie)) {
                fallen.elite();
            }
            dev.nookofthedead.entity.Mind.of(runner).stand();
            level.addFreshEntity(runner);
        }
    }

    /** A free spot by the marker to stand one in (the marker's own for the first, or when there is none). */
    private static BlockPos roomNear(ServerLevel level, BlockPos pos, int index) {
        if (index == 0) {
            return pos;
        }
        for (int tries = 0; tries < 8; tries++) {
            BlockPos at = pos.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                    && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                    && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
                return at;
            }
        }
        return pos;
    }
}
