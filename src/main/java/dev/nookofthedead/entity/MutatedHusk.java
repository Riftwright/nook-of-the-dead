package dev.nookofthedead.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The desert's dead (the user, 2026-10-18: "沢山湧いてくる変異ハスク"): a {@link MutatedZombie} in all it does - its pace, its
 * senses, its voice ({@link Traits}), the walls it breaks - dried out by the sun to a husk: it sounds as one, and its blow
 * leaves a hunger, as a husk's does. Drawn as the game's husk ({@code client/ZwClient}), a screamer in the desert's own
 * clothes ({@code tools/paint_screamer.py --husk}). Put down with a suppressant, an ordinary husk is left.
 */
public class MutatedHusk extends MutatedZombie {
    public MutatedHusk(EntityType<? extends MutatedHusk> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && this.getMainHandItem().isEmpty() && target instanceof LivingEntity living) {
            float difficulty = level.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
            living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int) difficulty), this);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.HUSK_STEP;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(this.getStepSound(), 0.15F, 1.0F);
    }
}
