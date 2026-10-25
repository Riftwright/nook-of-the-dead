package dev.nookofthedead.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * The animals the plague took (the user, 2026-10-15: "農場にいる、ゾンビ牛、ゾンビホース / 森にいる、ゾンビオオカミ"): what a
 * cow, a horse or a wolf was, gone for anyone it sees - no longer tamed, bred, milked or ridden. Their brains are all the
 * same and simple: a player in sight within their follow range is gone for, and the others of their kind round them
 * that see the same player are roused to it (a herd stampeding, a pack running together); a blow sets them on whoever
 * struck it. They do not hear as the mutated zombies do, nor break walls.
 */
public final class Infected {
    /** How far one that has found a player rouses its kind round it. */
    public static final double ROUSE = 14.0;

    /** In place of the animal's own goals (what was there - grazing, begging, following an owner - all gone). */
    public static void goals(PathfinderMob mob, double charge, double wander) {
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, charge, true));
        mob.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(mob, wander));
        mob.goalSelector.addGoal(6, new LookAtPlayerGoal(mob, Player.class, 8.0F));
        mob.goalSelector.addGoal(7, new RandomLookAroundGoal(mob));
        mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }

    /** Once a second, the idle ones of {@code mob}'s kind round it that can see its player set on them too. */
    public static void rouse(ServerLevel level, PathfinderMob mob) {
        if ((mob.tickCount + mob.getId()) % 20 != 0 || !(mob.getTarget() instanceof Player prey) || !mob.getSensing().hasLineOfSight(prey)) {
            return;
        }
        for (PathfinderMob other : level.getEntitiesOfClass(mob.getClass(), mob.getBoundingBox().inflate(ROUSE),
                o -> o != mob && o.getTarget() == null && o.getSensing().hasLineOfSight(prey))) {
            other.setTarget(prey);
        }
    }

    private Infected() {}
}
