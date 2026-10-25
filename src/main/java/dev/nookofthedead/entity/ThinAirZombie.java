package dev.nookofthedead.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The dead of the sky base (the user, 2026-10-19: "天空施設の酸素が少ない中で独自に進化したゾンビ"): changed by the thin air
 * up there. Leaner and quicker, paler, with a deep slow breath; they leap at their prey across a gap and come down
 * lightly; and they do not need to see one to come - in the hum of the base they hear a heart beat (within
 * {@link #HEARS}), through walls. Everything else of the mutated is theirs (the bite, the traits).
 */
public class ThinAirZombie extends MutatedZombie {
    /** How far they hear one through the base's hum. */
    public static final double HEARS = 20.0;
    /** A leap: from how near to how far, and how often. */
    static final double LEAP_FROM = 3.0;
    static final double LEAP_TO = 8.0;
    static final int LEAP_EVERY = 50;

    private int leapIn = 20;

    public ThinAirZombie(EntityType<? extends ThinAirZombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MutatedZombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.ATTACK_DAMAGE, 3.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 8.0);
    }

    /** Its slow deep breath, seen as frost in the cold (the visual review, 2026-10-19). */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() && this.isAlive() && this.tickCount % 50 < 2) {
            Vec3 look = this.getLookAngle();
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, this.getX() + look.x * 0.35, this.getEyeY() - 0.15,
                    this.getZ() + look.z * 0.35, look.x * 0.02, 0.01, look.z * 0.02);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.leapIn = Math.max(0, this.leapIn - 1);
        LivingEntity target = this.getTarget();
        if ((target == null || !target.isAlive()) && this.tickCount % 20 == 0) {
            Player heard = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), HEARS, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
            if (heard != null && !dev.nookofthedead.world.Refuge.guarded(level, heard.blockPosition())
                    && Math.abs(heard.getY() - this.getY()) < 6) {          // on its floor: one above or below it is not after
                this.setTarget(heard);                                   // heard, not seen
            }
            return;
        }
        if (target != null && this.onGround() && this.leapIn == 0) {
            double d = this.distanceTo(target);
            if (d >= LEAP_FROM && d <= LEAP_TO && this.hasLineOfSight(target) && target.onGround() && this.groundTo(level, target)) {
                this.leap(target, d);
            }
        }
    }

    /**
     * Whether there is ground all the way to {@code target}, no more than a step lower than either end: no leap over an
     * edge (the review, 2026-10-19: they leapt off the deck at a player by the rail and fell four hundred blocks).
     */
    boolean groundTo(ServerLevel level, LivingEntity target) {
        Vec3 from = this.position();
        Vec3 to = target.position();
        int steps = (int) Math.ceil(from.distanceTo(to));
        for (int k = 1; k <= steps + 1; k++) {
            Vec3 at = from.lerp(to, Math.min(1.0, k / (double) steps));
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at.x, Math.min(from.y, to.y), at.z);
            boolean ground = false;
            for (int down = 1; down <= 2 && !ground; down++) {
                ground = !level.getBlockState(pos.below(down)).getCollisionShape(level, pos.below(down)).isEmpty();
            }
            if (!ground) {
                return false;
            }
        }
        return true;
    }

    /** A leap at {@code target}, {@code d} away: up and over the gap to land on them, no further. */
    public void leap(LivingEntity target, double d) {
        this.leapIn = LEAP_EVERY;
        Vec3 way = target.position().subtract(this.position()).multiply(1, 0, 1);
        double across = way.length();
        way = way.normalize();
        double strength = Math.min(0.9, 0.2 + across * 0.075);
        this.setDeltaMovement(way.x * strength, 0.55, way.z * strength);
        this.playSound(SoundEvents.ZOMBIE_AMBIENT, 1.0F, 0.6F);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource source) {
        return super.causeFallDamage(fallDistance, damageModifier * 0.4F, source);
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }
}
