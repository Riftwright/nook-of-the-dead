package dev.nookofthedead.entity;

import dev.nookofthedead.registry.ZwEntities;
import dev.nookofthedead.registry.ZwItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A makeshift grenade thrown (the user, 2026-10-17: "一か所に固まってるゾンビを一気に倒せる…ブロックは破壊せずにゾンビだけ"): it
 * flies as a thrown bottle does, stops where it lands, and after {@link #FUSE} bursts. What lives within {@link #REACH}
 * is hurt - most at the middle, nothing at the edge, half through a wall - the thrower too, if near; no block is broken,
 * and nothing that is not alive (a frame, a painting, things on the ground) is touched. And it is loud: every one of the
 * dead within {@link Hearing#BELL} comes to see ({@link Hearing#toll}) - a crowd put down brings the next.
 */
public class Grenade extends ThrowableItemProjectile {
    /** From the throw to the burst: two and a half seconds. */
    public static final int FUSE = 50;
    /** How far it hurts. */
    public static final double REACH = 5.0;
    /** What it does at its middle: a mutated zombie (10) goes within about three blocks. */
    public static final float DAMAGE = 30.0F;

    private int fuse = FUSE;
    private boolean landed;

    public Grenade(EntityType<? extends Grenade> type, Level level) {
        super(type, level);
    }

    public Grenade(Level level, LivingEntity thrower, ItemStack stack) {
        super(ZwEntities.GRENADE.get(), thrower, level, stack);
    }

    public Grenade(Level level, double x, double y, double z, ItemStack stack) {
        super(ZwEntities.GRENADE.get(), x, y, z, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ZwItems.GRENADE.get();
    }

    @Override
    public void tick() {
        if (this.landed) {
            this.baseTick();                                           // lying still: no flight, the fuse burns on
        } else {
            super.tick();
        }
        if (this.level().isClientSide()) {
            if (this.tickCount % 2 == 0) {                              // the fuse smoking
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.25, this.getZ(), 0.0, 0.02, 0.0);
            }
            return;
        }
        if (--this.fuse <= 0) {
            this.burst((ServerLevel) this.level());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        // it stops where it hits, a hair off the face it hit
        Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()).scale(0.1));
        this.setPos(at.x, at.y, at.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.landed = true;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        this.setDeltaMovement(this.getDeltaMovement().scale(-0.1));     // off whoever it struck, to drop at their feet
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);                                               // no burst on striking: the fuse decides
    }

    /**
     * The burst at its place: the living round it hurt (not an armour stand, nor a survivor's remains: fixtures, as a
     * frame is - the review, 2026-10-18), and the dead about called to it.
     */
    public void burst(ServerLevel level) {
        Vec3 at = this.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.9F + this.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 6, 1.2, 0.6, 1.2, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 20, 1.0, 0.5, 1.0, 0.02);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(REACH),
                e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand) && !(e instanceof Remains))) {
            Vec3 middle = living.getBoundingBox().getCenter();
            double d = middle.distanceTo(at);
            if (d > REACH) {
                continue;
            }
            boolean walled = level.clip(new ClipContext(at.add(0, 0.2, 0), middle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                    .getType() != HitResult.Type.MISS;
            float hurt = (float) (DAMAGE * (1.0 - d / REACH) * (walled ? 0.5 : 1.0));
            if (hurt > 0.5F && living.hurtServer(level, this.damageSources().explosion(this, this.getOwner()), hurt)) {
                Vec3 away = middle.subtract(at).normalize().scale(0.6 * (1.0 - d / REACH));
                living.push(away.x, 0.25 + away.y * 0.5, away.z);
            }
        }
        level.gameEvent(this, GameEvent.EXPLODE, at);
        Hearing.toll(level, at);
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Fuse", this.fuse);
        output.putBoolean("Landed", this.landed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.fuse = input.getIntOr("Fuse", FUSE);
        this.landed = input.getBooleanOr("Landed", false);
    }

    /** For tests: to burst on the next tick. */
    public void light(int ticks) {
        this.fuse = ticks;
    }
}
