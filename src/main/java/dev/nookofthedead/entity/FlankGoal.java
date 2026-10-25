package dev.nookofthedead.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * The ones that come round (the user, 2026-10-15: "最短距離で詰めてくるやつと、ちょっと遠回りしてくるやつ"): one of
 * {@link Traits.Chase#FLANK}, still far from its prey, makes not for them but for a place off to one side of them - its own
 * side, always the same - and closes in from there once it is near. A crowd so comes at someone from more than one way, and
 * a street run down is not a street left clear. Near ({@link #NEAR}), or with no way to that place, it goes straight in as
 * the others ({@link RunnerAttackGoal}).
 */
public class FlankGoal extends Goal {
    /** Nearer than this it goes straight in. */
    public static final double NEAR = 10.0;
    /** Farther than this it does not bother: it is coming the long way anyway. */
    private static final double FAR = 48.0;
    /** How far off to the side it makes for, at most. */
    private static final double WIDE = 10.0;
    private static final int REPATH = 20;
    private final Zombie mob;
    private final Mind mind;
    private int repathIn;
    private int failed;

    public FlankGoal(Zombie mob, Mind mind) {
        this.mob = mob;
        this.mind = mind;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.mind.traits().chase() != Traits.Chase.FLANK || this.failed > 0 && --this.failed > 0) {
            return false;
        }
        LivingEntity prey = this.mob.getTarget();
        if (prey == null) {
            return false;
        }
        double far = this.mob.distanceToSqr(prey);
        return far > NEAR * NEAR && far < FAR * FAR;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity prey = this.mob.getTarget();
        if (prey == null || !prey.isAlive() || this.failed > 0) {
            return false;
        }
        double far = this.mob.distanceToSqr(prey);
        // near, gone too far, or its way round walked to its end short of the side: straight in as the others
        return far > NEAR * NEAR && far < FAR * FAR && !(this.repathIn < REPATH - 2 && this.mob.getNavigation().isDone());
    }

    @Override
    public void start() {
        this.repathIn = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity prey = this.mob.getTarget();
        if (prey == null || --this.repathIn > 0) {
            return;
        }
        this.repathIn = REPATH;
        Vec3 to = this.side(prey);
        if (!this.mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0)) {
            this.failed = 100;                                   // no way round: straight in, and try again in a while
        }
        this.mob.getLookControl().setLookAt(prey, 30.0F, 30.0F);
    }

    /** The place off to its side of {@code prey}: square to the line between them, nearer the farther it is. */
    Vec3 side(LivingEntity prey) {
        Vec3 off = prey.position().subtract(this.mob.position());
        double far = Math.sqrt(off.x * off.x + off.z * off.z);
        double sign = (this.mob.getId() & 1) == 0 ? 1 : -1;
        double wide = Math.min(WIDE, far * 0.5) * sign;
        // a little short of them, and out to the side
        return new Vec3(prey.getX() - off.x / far * 4 + -off.z / far * wide, prey.getY(), prey.getZ() - off.z / far * 4 + off.x / far * wide);
    }
}
