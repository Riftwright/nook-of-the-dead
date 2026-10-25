package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * A bat the plague took (the user, 2026-10-16: "感染コウモリ（攻撃してくるコウモリ）"): it hangs and flits as a bat does,
 * but a player it sees within {@link #HUNT} blocks it goes for, darting in to bite (the bite counts against the
 * infection as the dead's do, {@link Mind#bit}) and off again before the next. Found where it is dark: the galleries,
 * the caves, the town at night.
 */
public class InfectedBat extends Bat implements Quiet, Enemy {
    /** How far off it goes for a player in sight; how long between bites. */
    public static final double HUNT = 16.0;
    public static final int BETWEEN_BITES = 30;
    private static final float BITE = 2.0F;
    private int nextBite;

    public InfectedBat(EntityType<? extends Bat> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.ATTACK_DAMAGE, BITE).add(Attributes.FOLLOW_RANGE, HUNT);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        Player prey = this.prey(level);
        if (prey == null) {
            super.customServerAiStep(level);                             // a bat: hanging, flitting about
            return;
        }
        if (this.isResting()) {
            this.setResting(false);
        }
        // in at the face, off to one side after a bite
        Vec3 aim = prey.getEyePosition().subtract(0, 0.3, 0);
        if (this.tickCount < this.nextBite) {
            aim = aim.add(Math.sin(this.getId() + this.tickCount * 0.2) * 3, 1.5, Math.cos(this.getId() + this.tickCount * 0.2) * 3);
        }
        Vec3 to = aim.subtract(this.position());
        Vec3 moving = this.getDeltaMovement();
        Vec3 next = moving.add((Math.signum(to.x) * 0.6 - moving.x) * 0.12, (Math.signum(to.y) * 0.6 - moving.y) * 0.12,
                (Math.signum(to.z) * 0.6 - moving.z) * 0.12);
        this.setDeltaMovement(next);
        this.setYRot((float) (Math.atan2(next.z, next.x) * 180.0 / Math.PI) - 90.0F);
        this.zza = 0.5F;
        if (this.tickCount >= this.nextBite && this.getBoundingBox().inflate(0.3).intersects(prey.getBoundingBox())) {
            float before = prey.getHealth();
            boolean hit = this.doHurtTarget(level, prey);
            Mind.bit(hit, prey, before);
            this.nextBite = this.tickCount + BETWEEN_BITES;
        }
    }

    /** The player it goes for: one in sight, near enough, not in creative or spectating; none on Peaceful. */
    private @Nullable Player prey(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return null;
        }
        Player near = level.getNearestPlayer(this, HUNT);
        if (near == null || near.isCreative() || near.isSpectator() || !this.getSensing().hasLineOfSight(near)) {
            return null;
        }
        this.setTarget(near);
        return near;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return true;
    }

    /** Where it comes on its own: in the dark only, and as the others of the plague do ({@link MutatedZombie#checkSpawnRules}). */
    public static boolean checkSpawnRules(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
            RandomSource random) {
        return level.getMaxLocalRawBrightness(pos) <= random.nextInt(5) && MutatedZombie.checkSpawnRules(type, level, reason, pos, random);
    }
}
