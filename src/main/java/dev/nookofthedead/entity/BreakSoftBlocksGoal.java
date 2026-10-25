package dev.nookofthedead.entity;

import java.util.EnumSet;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Through a soft wall to its prey: a mob hunting someone nearby that is held up (it has run into something, or its way
 * ends short of its prey) by a block of {@code soft} (wood, glass, leaves, wool...) at its feet or head on the side
 * toward its prey, hits it until it breaks - longer for harder blocks, the
 * cracks showing as it goes - then runs on. Stone and iron stop it. Off where mob griefing is off. A mob with nobody to
 * hunt does the same on its way to a sound it heard ({@link Mind#lead}), or to its errand, if it has one (the If's mirror:
 * {@link SmashMirrorGoal}).
 * <p>
 * What it breaks, and how fast, is its {@link Traits.Breaker}'s: nothing, only what shatters, every soft block, or every
 * soft block in half the time. The more of them at a wall, the sooner it gives (the user, 2026-10-15, the films' door
 * that holds against one and not against a crowd): each other zombie of ours within {@link #CROWD} of the block adds half
 * a stroke again, up to {@link #CROWD_MOST} times as fast.
 */
public class BreakSoftBlocksGoal extends Goal {
    private final Mob mob;
    private final Mind mind;
    /** Where a mob with no prey is bound for, if anywhere; null for none. */
    private final @Nullable Function<ServerLevel, @Nullable BlockPos> errand;
    private @Nullable BlockPos target;
    private float time;
    private int needed;
    private int lastProgress = -1;
    /** How near its prey has to be for it to break through to it. */
    private static final double REACH = 24.0;
    /** How near a block the others pushing at it have to be. */
    public static final double CROWD = 2.5;
    /** At most this many times as fast, however many push. */
    public static final float CROWD_MOST = 3.0F;

    public BreakSoftBlocksGoal(Mob mob, Mind mind, @Nullable Function<ServerLevel, @Nullable BlockPos> errand) {
        this.mob = mob;
        this.mind = mind;
        this.errand = errand;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Where it is making for: its prey, near enough, or with none, a sound it heard, or its errand, near enough. */
    private @Nullable Vec3 heading(ServerLevel level) {
        LivingEntity prey = this.mob.getTarget();
        if (prey != null) {
            return prey.isAlive() && prey.level() == level && this.mob.distanceToSqr(prey) <= REACH * REACH ? prey.position() : null;
        }
        Vec3 lead = this.mind.lead();
        if (lead != null) {
            return this.mob.distanceToSqr(lead) <= REACH * REACH ? lead : null;
        }
        BlockPos to = this.errand == null ? null : this.errand.apply(level);
        return to != null && to.distToCenterSqr(this.mob.position()) <= SmashMirrorGoal.SCENT * SmashMirrorGoal.SCENT
                ? Vec3.atBottomCenterOf(to) : null;
    }

    /** Ticks to break {@code state}: a pane or leaves in about a second and a half, planks in four, a door in five and a half. */
    public static int breakTicks(BlockState state, Level level, BlockPos pos) {
        float hardness = Math.max(0.0F, state.getDestroySpeed(level, pos));
        return Math.round(20 + hardness * 30);
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level) || this.mind.breaks() == null) {
            return false;
        }
        Vec3 to = this.heading(level);
        if (to == null || !this.blocked(to)) {
            return false;
        }
        this.target = this.blockInTheWay(level, to, this.mob.getTarget() != null);
        // nothing broken into within a Dark Matter's reach or the institute's refuge (the review: Dark Matter kept no one
        // out of the room it stood in)
        if (this.target != null && dev.nookofthedead.world.Refuge.guarded(level, this.target)) {
            this.target = null;
        }
        return this.target != null;
    }

    /**
     * Whether it is held up: run into something; at the end of its way short of where it is going (a wall between leaves
     * only a way to the near side of it, where it would stand and stare); or right by its prey with a wall between them
     * (a door at the head of a narrow stair, the prey just behind it: r1 fix check). Not merely on a way that does not
     * reach: climbing stairs toward a shut door, each step ahead was "in the way" and the stairs were broken under it.
     */
    private boolean blocked(Vec3 to) {
        if (this.mob.horizontalCollision) {
            return true;
        }
        LivingEntity prey = this.mob.getTarget();
        if (prey != null && this.mob.distanceToSqr(prey) <= 2.5 * 2.5) {
            return !this.mob.getSensing().hasLineOfSight(prey);
        }
        // a way walked to its end; not no way yet (just come, before its chase has worked one out, it broke the stair in
        // front of it)
        Path path = this.mob.getNavigation().getPath();
        return path != null && path.isDone() && this.mob.distanceToSqr(to) > 2.5 * 2.5;
    }

    /**
     * The soft block in front of the mob, toward where it is going (or, after prey, the way it faces), at its feet or at
     * its head; none if there is none. On an errand only toward the errand: an idle runner near the mirror bit at any
     * wooden wall it faced (r1 fix check).
     */
    private @Nullable BlockPos blockInTheWay(ServerLevel level, Vec3 to, boolean hunting) {
        double dx = to.x - this.mob.getX();
        double dz = to.z - this.mob.getZ();
        Direction toward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos feet = this.mob.blockPosition();
        for (int up = 0; up <= 1; up++) {                // a thin thing (a mirror, a pane) it has half walked into
            BlockPos at = feet.above(up);
            if (this.breakable(level, at, up)) {
                return at;
            }
        }
        for (Direction side : hunting ? new Direction[] {toward, this.mob.getDirection()} : new Direction[] {toward}) {
            for (int up = 0; up <= 1; up++) {
                BlockPos at = feet.relative(side).above(up);
                if (this.breakable(level, at, up)) {
                    return at;
                }
            }
        }
        return null;
    }

    /**
     * Whether the block at {@code at} is a soft one standing in its way: something it would bump into, not a floor it
     * stands or steps on - at its feet, only what rises above a step (r1: one on a slab floor broke the floor under it,
     * and the stairs it climbed).
     */
    private boolean breakable(ServerLevel level, BlockPos at, int up) {
        BlockState state = level.getBlockState(at);
        if (!state.is(this.mind.breaks()) || state.getDestroySpeed(level, at) < 0 || !CommonHooks.canEntityDestroy(level, at, this.mob)) {
            return false;
        }
        var shape = state.getCollisionShape(level, at);
        if (shape.isEmpty()) {
            return false;
        }
        return up > 0 || at.getY() + shape.max(Direction.Axis.Y) > this.mob.getY() + this.mob.maxUpStep();
    }

    @Override
    public void start() {
        this.time = 0;
        this.lastProgress = -1;
        this.needed = (int) Math.max(10, Math.round(breakTicks(this.mob.level().getBlockState(this.target), this.mob.level(), this.target)
                * this.mind.traits().breaker().time * Hardness.of(this.mob.level()).breakTime()));
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean canContinueToUse() {
        return this.target != null && this.mob.level() instanceof ServerLevel level && this.heading(level) != null && this.time <= this.needed
                && this.mind.breaks() != null && this.mob.level().getBlockState(this.target).is(this.mind.breaks())
                && this.target.closerToCenterThan(this.mob.position(), 2.5)
                && !dev.nookofthedead.world.Refuge.guarded(level, this.target);    // Dark Matter set down mid-blow stops it too
    }

    @Override
    public void stop() {
        if (this.target != null) {
            this.mob.level().destroyBlockProgress(this.mob.getId(), this.target, -1);
        }
        this.target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** This tick's share of the work: one stroke, and half again for each other one of ours at the same wall. */
    private float push(Level level) {
        if (this.mob.tickCount % 10 != this.mob.getId() % 10 && this.pushing > 0) {
            return this.pushing;
        }
        Vec3 at = Vec3.atCenterOf(this.target);
        int others = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new net.minecraft.world.phys.AABB(at, at).inflate(CROWD),
                z -> z != this.mob && z instanceof Mutated && z.isAlive()).size();
        this.pushing = Math.min(CROWD_MOST, 1.0F + others * 0.5F);
        return this.pushing;
    }

    private float pushing;

    @Override
    public void tick() {
        if (this.target == null) {
            // broken last tick: a mob ticks its running goals every other tick without asking whether they go on, so
            // this can come round once more before stop (r1: the server crashed on every other wall broken)
            return;
        }
        Level level = this.mob.level();
        this.mob.getLookControl().setLookAt(this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);
        if (this.mob.getRandom().nextInt(12) == 0) {
            level.levelEvent(1019, this.target, 0); // the thump of a zombie at a door
            if (!this.mob.isSwinging()) {
                this.mob.swingForAttack(this.mob.getUsedItemHand());
            }
        }
        this.time += this.push(level);
        int progress = (int) ((float) this.time / this.needed * 10.0F);
        if (progress != this.lastProgress) {
            level.destroyBlockProgress(this.mob.getId(), this.target, progress);
            this.lastProgress = progress;
        }
        if (this.time >= this.needed) {
            if (level instanceof ServerLevel server && level.getBlockState(this.target).is(dev.nookworks.registry.ModTravel.IF_MIRROR.get())) {
                SmashMirrorGoal.smash(server, this.target, this.mob);   // the way home, and everyone hears it go
            } else {
                level.destroyBlock(this.target, false, this.mob);
                level.destroyBlockProgress(this.mob.getId(), this.target, -1);
            }
            this.target = null;
        }
    }
}
