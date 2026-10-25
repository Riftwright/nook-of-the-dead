package dev.nookofthedead.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Going to see (a sound heard, a place its prey was last - {@link Mind#lead}): it runs there and, once there, stands and
 * looks round a few seconds before it gives it up. Someone seen on the way is taken up at once (the target goals); a soft
 * wall in the way is broken through ({@link BreakSoftBlocksGoal}, which goes first).
 */
public class InvestigateGoal extends Goal {
    /** How near the place counts as there. */
    private static final double THERE = 2.5;
    /** Ticks it looks round once there. */
    public static final int LOOKS_ROUND = 60;
    /** Ticks between working out its way again (the place may have moved: a new sound). */
    private static final int REPATH = 20;
    private final Zombie mob;
    private final Mind mind;
    private @Nullable Vec3 going;
    private int lookingRound;
    private int repathIn;
    private int misses;

    public InvestigateGoal(Zombie mob, Mind mind) {
        this.mob = mob;
        this.mind = mind;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() == null && this.mind.lead() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.going = null;
        this.lookingRound = 0;
        this.repathIn = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 to = this.mind.lead();
        if (to == null) {
            return;
        }
        if (!to.equals(this.going)) {                            // somewhere new: off again
            this.going = to;
            this.lookingRound = 0;
            this.repathIn = 0;
            this.misses = 0;
        }
        if (this.lookingRound > 0) {
            if (--this.lookingRound == 0) {
                this.mind.looked();
            } else if (this.lookingRound % 20 == 0) {
                this.mob.getLookControl().setLookAt(this.mob.getX() + this.mob.getRandom().nextGaussian() * 6, this.mob.getEyeY(),
                        this.mob.getZ() + this.mob.getRandom().nextGaussian() * 6);
            }
            return;
        }
        double dx = to.x - this.mob.getX();
        double dz = to.z - this.mob.getZ();
        // there, or as near as its way goes (a way worked out and walked to its end)
        boolean walked = this.misses == 0 && this.repathIn > 0 && this.repathIn < REPATH - 2 && this.mob.getNavigation().isDone();
        if (dx * dx + dz * dz < THERE * THERE || walked) {
            this.mob.getNavigation().stop();
            this.lookingRound = LOOKS_ROUND;
            return;
        }
        if (--this.repathIn <= 0) {
            this.repathIn = REPATH;
            if (this.mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0)) {
                this.misses = 0;
            } else if (++this.misses >= 3) {               // no way there at all (in the air it works none out): it looks from here
                this.lookingRound = LOOKS_ROUND;
                return;
            }
        }
        this.mob.getLookControl().setLookAt(to.x, to.y + 1, to.z);
    }
}
