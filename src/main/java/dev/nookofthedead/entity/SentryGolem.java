package dev.nookofthedead.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The sky base's guard (the user, 2026-10-19: "本来は人類を守るはずだったゴーレムが排除しようとしてくる"): an iron golem the
 * base built to keep the outside out, and now set on everyone who comes in. It shoots bolts from range, and close it
 * strikes as a golem does; iron (Smite does nothing to it), it does not burn, it is heavy. The dead leave it be and it
 * them: it was set against the living. In peaceful it stands, and shoots no one.
 */
public class SentryGolem extends IronGolem implements RangedAttackMob, Quiet {
    /** How near before it closes in to strike instead of shooting. */
    static final double CLOSE = 4.0;

    public SentryGolem(EntityType<? extends SentryGolem> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return super.canUse() && SentryGolem.this.close();
            }

            @Override
            public boolean canContinueToUse() {
                return super.canContinueToUse() && SentryGolem.this.close();
            }
        });
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 0.8, 30, 45, 18.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    boolean close() {
        LivingEntity target = this.getTarget();
        return target != null && this.distanceToSqr(target) < CLOSE * CLOSE;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Hardness.fit(this);
        if (level.getDifficulty() == Difficulty.PEACEFUL && this.getTarget() != null) {
            this.setTarget(null);
        }
        this.setAggressive(this.getTarget() != null);      // seen by the client: the launcher held up at them
    }

    /** Only ever after the living: a dead one crowding it (IronGolem's push) does not turn it (the review, 2026-10-19). */
    @Override
    public void setTarget(@org.jspecify.annotations.Nullable LivingEntity target) {
        super.setTarget(target instanceof Player ? target : null);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target instanceof Player && super.canAttack(target);
    }

    /** A bolt, hard and flat: as a skeleton's arrow, faster. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Arrow bolt = new Arrow(level, this, new ItemStack(Items.ARROW), null);
        bolt.setBaseDamage(1.4);              // its speed and the difficulty make a skeleton's arrow and a half of it (the review)
        bolt.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.4) - bolt.getY();
        double dz = target.getZ() - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        bolt.shoot(dx, dy + flat * 0.12, dz, 2.2F, 6 - level.getDifficulty().getId());
        level.addFreshEntity(bolt);
        level.playSound(null, this.blockPosition(), SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 1.0F, 0.7F);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }
}
