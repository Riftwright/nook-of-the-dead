package dev.nookofthedead.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;

/**
 * A rat the plague took (the user, 2026-10-16: "感染ネズミ（動きやサイズはシルバーフィッシュ。石には潜らない）"; drawn as a rat, {@code client/RatModel}): as small
 * and quick as a silverfish, as quick to bite (the bite counts against the infection, {@link Mind#bit}), running in a
 * pack once one is struck - but it never hides in stone, nor wakes any there. Found in the town and the galleries.
 */
public class InfectedRat extends Silverfish implements Quiet {
    public InfectedRat(EntityType<? extends Silverfish> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.5).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // no hiding in stone, no waking of what hides there: a rat scurries about instead
        this.goalSelector.removeAllGoals(goal -> goal.getClass().getEnclosingClass() == Silverfish.class);
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : Float.MAX_VALUE;
        boolean hit = super.doHurtTarget(level, target);
        Mind.bit(hit, target, before);
        return hit;
    }
}
