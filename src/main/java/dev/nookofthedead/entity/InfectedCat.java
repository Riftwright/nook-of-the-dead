package dev.nookofthedead.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A cat the plague took, of the dead villages (the user, 2026-10-23: "ゾンビ村に感染猫を追加したらどうかな"): as the other
 * animals it took ({@link Infected}) - gone for anyone it sees, its bite counting against the infection ({@link Mind#bit}) -
 * but a cat still: it leaps up after its prey ({@link Leap}) onto what the dead cannot get onto - a fence, a wall, a low
 * roof - though no ladder. Small, quick, not much to it. It looks as a village's cats do, grey and sick ({@code ZwClient}).
 */
public class InfectedCat extends Cat implements Quiet {
    /** The highest it leaps, above where it stands. */
    public static final double LEAP = 4.5;
    /** How near (flat) its prey must be for it to leap. */
    public static final double LEAP_REACH = 5.0;
    /** Ticks between leaps. */
    public static final int LEAP_EVERY = 30;

    /** How many times it has leapt (for tests). */
    public int leaps;

    public InfectedCat(EntityType<? extends Cat> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Cat.createAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void registerGoals() {
        Infected.goals(this, 1.3, 0.8);
        this.goalSelector.addGoal(1, new Leap(this));
    }

    /** No going shy of players when untamed, as a cat does: it has no tameness to weigh. */
    @Override
    protected void reassessTameGoals() {
    }

    @Override
    public void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Infected.rouse(level, this);
        if ((this.tickCount + this.getId()) % 120 == 0 && this.getRandom().nextInt(3) == 0) {
            this.hiss();                                                // no purring, no mewing: only that
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : Float.MAX_VALUE;
        boolean hit = super.doHurtTarget(level, target);
        Mind.bit(hit, target, before);
        return hit;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    @Override
    public @Nullable Cat getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.hasCustomName() && !this.isPersistenceRequired();
    }

    /**
     * Up after its prey: its prey a little way off and above it, or it not getting nearer on foot (a fence, a wall in the
     * way), and it on the ground - a leap, as high as it must be and no higher than {@link #LEAP}, toward them, with a hiss.
     */
    static final class Leap extends Goal {
        private final InfectedCat cat;
        private int wait;

        Leap(InfectedCat cat) {
            this.cat = cat;
            this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.wait > 0) {
                this.wait--;
                return false;
            }
            LivingEntity prey = this.cat.getTarget();
            if (prey == null || !prey.isAlive() || !this.cat.onGround()) {
                return false;
            }
            double dx = prey.getX() - this.cat.getX();
            double dz = prey.getZ() - this.cat.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            double up = prey.getY() - this.cat.getY();
            if (flat > LEAP_REACH || up > LEAP || up < -1) {
                return false;
            }
            boolean above = up >= 0.9;
            boolean stuck = flat > 1.5 && this.cat.horizontalCollision;
            return (above || stuck) && this.cat.getSensing().hasLineOfSight(prey);
        }

        @Override
        public void start() {
            LivingEntity prey = this.cat.getTarget();
            if (prey == null) {
                return;
            }
            Vec3 to = prey.position().subtract(this.cat.position());
            double up = Math.max(1.6, Math.min(LEAP, to.y + 0.6));
            Vec3 flat = new Vec3(to.x, 0, to.z);
            Vec3 along = flat.lengthSqr() > 1.0E-4 ? flat.normalize().scale(Math.min(0.55, 0.12 + flat.length() * 0.09)) : Vec3.ZERO;
            this.cat.setDeltaMovement(along.x, Math.sqrt(0.2 * (up + 0.5)), along.z);
            this.cat.needsSync = true;
            this.cat.getNavigation().stop();
            this.cat.hiss();
            this.cat.leaps++;
            this.wait = LEAP_EVERY;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
