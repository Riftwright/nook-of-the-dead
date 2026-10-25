package dev.nookofthedead.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Vanilla's zombie attack, with patience at a wall it cannot get through. Vanilla works its way out afresh every few
 * ticks; with its prey out of reach - shut in stone - every search runs to its limit, and a crowd of runners round a
 * stone hut slowed the server to a crawl (r1: 75 of them, 22 ms a tick, 65 at worst). Once its way stops short of its prey,
 * a runner keeps to the way it has and looks for another only after {@link #WAIT} ticks, or as soon as its prey moves
 * (never within a few blocks of it, where it fights as vanilla's does).
 * Soft walls are {@link BreakSoftBlocksGoal}'s, which goes first.
 */
public class RunnerAttackGoal extends ZombieAttackGoal {
    /** Ticks between searches while its prey is out of reach and stays put. */
    public static final int WAIT = 100;
    /** Nearer its prey than this it never waits: a way that ends a step short still brings it within a blow. */
    private static final double NEAR = 3.0;
    private final Zombie zombie;
    private int waiting;
    private double fromX;
    private double fromY;
    private double fromZ;

    public RunnerAttackGoal(Zombie zombie) {
        super(zombie, 1.0, false);
        this.zombie = zombie;
    }

    @Override
    public void start() {
        super.start();
        this.waiting = 0;
    }

    /**
     * Kept running while it waits: vanilla's goal ends once its mob stands at the end of its way, and then looks for a way
     * afresh every second to start again - each search run to its limit with the prey out of reach (r1 fix check: no
     * fewer searches than vanilla round a stone hut).
     */
    @Override
    public boolean canContinueToUse() {
        LivingEntity prey = this.zombie.getTarget();
        if (this.waiting > 0 && prey != null && prey.isAlive() && prey.level() == this.zombie.level()) {
            return true;
        }
        return super.canContinueToUse();
    }

    @Override
    public void tick() {
        LivingEntity prey = this.zombie.getTarget();
        if (prey != null && this.waiting > 0 && prey.distanceToSqr(this.fromX, this.fromY, this.fromZ) < 4.0 && this.zombie.distanceToSqr(prey) > NEAR * NEAR) {
            this.waiting--;
            this.zombie.getLookControl().setLookAt(prey, 30.0F, 30.0F);
            return;
        }
        this.waiting = 0;
        super.tick();
        Path path = this.zombie.getNavigation().getPath();
        if (prey != null && path != null && !path.canReach() && this.zombie.distanceToSqr(prey) > NEAR * NEAR) {
            this.waiting = WAIT;
            this.fromX = prey.getX();
            this.fromY = prey.getY();
            this.fromZ = prey.getZ();
        }
    }

    /** Whether it is waiting out a way that does not reach. For tests. */
    public boolean waiting() {
        return this.waiting > 0;
    }
}
