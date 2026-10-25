package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The capital's crows (the user, 2026-10-25: "王都に沢山群がってたら雰囲気出る"): they wheel in flocks round its spires
 * and towers - the keep's, the cathedral's, the council's clock - over a city of the dead, and now and then one drops on
 * someone crossing the open below, pecks, and is off up again. Only in the capital, brought by their own flocking
 * ({@link Crows}) and not the game's spawning: they take nothing from the dead's share of the world.
 */
public class CapitalCrow extends AmbientCreature {
    /** How far below it looks for someone to drop on; how long between drops; the peck. */
    public static final double DROP = 20.0;
    public static final int BETWEEN_DROPS = 260;
    private static final float PECK = 1.5F;

    /** The spire it wheels round, and how wide and high. */
    private @Nullable BlockPos roost;
    private double radius = 9;
    private double height = 4;
    private @Nullable Player prey;
    private int nextDrop;
    private int scatter;

    public CapitalCrow(EntityType<? extends CapitalCrow> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.nextDrop = 100 + this.getRandom().nextInt(BETWEEN_DROPS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.ATTACK_DAMAGE, PECK).add(Attributes.FOLLOW_RANGE, DROP);
    }

    /** Sets the spire it wheels round, and its own way round it. */
    public void roost(BlockPos roost) {
        this.roost = roost;
        this.radius = 6 + this.getRandom().nextDouble() * 9;
        this.height = this.getRandom().nextDouble() * 9 - 1;
    }

    public @Nullable BlockPos roost() {
        return this.roost;
    }

    /** For tests: ready to drop on whoever it sees next, its rest over. */
    public void readyToDrop() {
        this.nextDrop = this.tickCount;
    }

    public boolean dropping() {
        return this.prey != null;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Vec3 aim;
        double speed;
        if (this.scatter > 0) {
            // scattered: up and away from where it was
            this.scatter--;
            aim = this.position().add(this.getDeltaMovement().normalize().scale(4)).add(0, 3, 0);
            speed = 0.6;
        } else if (this.prey != null) {
            Player prey = this.prey;
            if (!prey.isAlive() || prey.isSpectator() || prey.isCreative() || this.distanceToSqr(prey) > (DROP + 12) * (DROP + 12)) {
                this.prey = null;
                return;
            }
            aim = prey.getEyePosition().subtract(0, 0.2, 0);
            speed = 0.7;
            if (this.getBoundingBox().inflate(0.4).intersects(prey.getBoundingBox())) {
                this.doHurtTarget(level, prey);
                this.prey = null;
                this.nextDrop = this.tickCount + BETWEEN_DROPS + this.getRandom().nextInt(BETWEEN_DROPS);
                this.scatter = 30;
                this.playSound(SoundEvents.PARROT_AMBIENT, 1.0F, 0.55F);
            }
        } else {
            if (this.roost == null) {
                aim = this.position().add(0, 0.5, 0);
            } else {
                // round the spire, each its own way and pace, rising and falling a little
                double t = (this.tickCount + this.getId() * 37) * (0.018 + (this.getId() % 7) * 0.002) * (this.getId() % 2 == 0 ? 1 : -1);
                aim = new Vec3(this.roost.getX() + 0.5 + Math.cos(t) * this.radius,
                        this.roost.getY() + this.height + Math.sin(t * 2.3) * 1.5, this.roost.getZ() + 0.5 + Math.sin(t) * this.radius);
            }
            speed = 0.42;
            if (this.tickCount >= this.nextDrop && this.tickCount % 20 == 0) {
                this.prey = this.pickPrey(level);
                if (this.prey == null) {
                    this.nextDrop = this.tickCount + 60;
                }
            }
        }
        Vec3 to = aim.subtract(this.position());
        double len = to.length();
        Vec3 want = len < 0.01 ? Vec3.ZERO : to.scale(speed / Math.max(len, 1.0));
        Vec3 moving = this.getDeltaMovement();
        Vec3 next = moving.add(want.subtract(moving).scale(0.12));
        this.setDeltaMovement(next);
        if (next.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Math.atan2(next.z, next.x) * 180.0 / Math.PI) - 90.0F;
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
        }
    }

    /**
     * One to drop on: below it, in the open under the sky, in sight; never on Peaceful, never more than two of the flock
     * on one at a time.
     */
    private @Nullable Player pickPrey(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return null;
        }
        Player near = level.getNearestPlayer(this, DROP);
        if (near == null || near.isCreative() || near.isSpectator() || near.getY() > this.getY() || !this.getSensing().hasLineOfSight(near)
                || !level.canSeeSky(near.blockPosition().above())) {
            return null;
        }
        int already = level.getEntitiesOfClass(CapitalCrow.class, this.getBoundingBox().inflate(48), c -> c.prey == near).size();
        return already >= 2 || this.getRandom().nextInt(2) != 0 ? null : near;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt) {
            // the flock round it breaks up, cawing
            for (CapitalCrow other : level.getEntitiesOfClass(CapitalCrow.class, this.getBoundingBox().inflate(16))) {
                other.prey = null;
                other.scatter = 40 + other.getRandom().nextInt(40);
            }
        }
        return hurt;
    }

    @Override
    public void checkDespawn() {
        // gone once nobody is near the capital's sky; else they stay (the game's ambient despawn thinned the flock as soon as
        // one was thirty blocks off)
        if (this.level().getNearestPlayer(this, Crows.GONE) == null) {
            this.discard();
        } else {
            this.setNoActionTime(0);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState onState, BlockPos pos) {
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return this.getRandom().nextInt(3) == 0 ? SoundEvents.PARROT_AMBIENT : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F + this.getRandom().nextFloat() * 0.12F;                 // the parrot's call, low: a caw
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.roost != null) {
            output.putLong("Roost", this.roost.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getLong("Roost").ifPresent(r -> this.roost(BlockPos.of(r)));
    }
}
