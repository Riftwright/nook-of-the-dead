package dev.nookofthedead.entity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The thing that grew in the sewers (the user, 2026-10-19: "地下下水道に、植物っぽい大きな敵mob…中ボスみたいな（ボスバーは
 * いらない）"): rooted where it took hold, a bulb on a thick stalk, its petals shut over a mouth, vines round it. It does not
 * walk. Closed it is hard to hurt; when something living comes near it opens, and then it can be hurt - by a blade most of
 * all, by fire twice over. Arrows mostly glance off it, open or shut.
 * <p>
 * What it does to one who comes for it: a lash of its vines at arm's length and a little more; from further, a vine
 * thrown out that drags the one it catches in toward it; and, hurt, a breath of spores round its foot that sickens and
 * brings the plague on sooner. Left alone it heals. No bar on the screen: it is not the Vandal, only something down
 * there.
 */
public class SewerBloom extends Monster implements Quiet {
    /** Open (its petals back, the mouth bare): sent to the clients for its look. */
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(SewerBloom.class, EntityDataSerializers.BOOLEAN);
    /** Its last strike, for its look: a lash of the vines or one thrown out. */
    private static final EntityDataAccessor<Boolean> THROWING = SynchedEntityData.defineId(SewerBloom.class, EntityDataSerializers.BOOLEAN);

    /** The lash's reach from its middle, and how often. */
    public static final double LASH = 5.0;               // past a spear's reach from its side (the review, 2026-10-19)
    static final int LASH_EVERY = 26;
    /** The thrown vine: from how near to how far, how often. */
    public static final double GRAB_FROM = LASH;
    public static final double GRAB_TO = 15.0;
    static final int GRAB_EVERY = 20 * 7;
    /** Spores, when hurt: how often at most. */
    static final int SPORES_EVERY = 20 * 9;
    /** How near it has to be to open. */
    static final double WAKE = 15.0;

    private int lashIn;
    private int grabIn = 40;
    private int sporesIn;
    /** On the clients: how far open, eased (0 shut, 1 open), and the last tick's. */
    public float openness;
    public float opennessO;

    public SewerBloom(EntityType<? extends SewerBloom> type, Level level) {
        super(type, level);
        this.xpReward = 40;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 140.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(OPEN, false);
        entityData.define(THROWING, false);
    }

    public boolean open() {
        return this.entityData.get(OPEN);
    }

    /** Whether its last strike was a vine thrown out (else a lash). */
    public boolean throwing() {
        return this.entityData.get(THROWING);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    /** Rooted: nothing moves it, not a blow, not the water. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.opennessO = this.openness;
            this.openness = Mth.approach(this.openness, this.open() ? 1.0F : 0.0F, this.open() ? 0.08F : 0.05F);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Hardness.fit(this);
        LivingEntity target = this.getTarget();
        boolean near = target != null && target.isAlive() && this.distanceTo(target) < WAKE && this.hasLineOfSight(target);
        if (this.open() != near) {
            this.entityData.set(OPEN, near);
            this.playSound(near ? SoundEvents.CREAKING_ACTIVATE : SoundEvents.CREAKING_DEACTIVATE, 1.2F, 0.6F);
        }
        this.lashIn = Math.max(0, this.lashIn - 1);
        this.grabIn = Math.max(0, this.grabIn - 1);
        this.sporesIn = Math.max(0, this.sporesIn - 1);
        if (!near) {
            if (this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.heal(2.0F);                                       // left alone, it mends
            }
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.setYRot(this.yHeadRot);
        this.yBodyRot = this.yHeadRot;
        double reach = this.reachTo(target);
        if (reach <= LASH && this.lashIn == 0) {
            this.lashIn = LASH_EVERY;
            this.entityData.set(THROWING, false);
            this.swingForAttack(InteractionHand.MAIN_HAND);
            this.doHurtTarget(level, target);
        } else if (reach >= GRAB_FROM && reach <= GRAB_TO && this.grabIn == 0) {
            this.grabIn = GRAB_EVERY;
            this.grab(level, target);
        }
    }

    /** From its middle to the nearest of {@code target}, flat. */
    private double reachTo(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        return Math.max(0.0, Math.sqrt(dx * dx + dz * dz) - target.getBbWidth() / 2);
    }

    /** The lash: what a blow does, and the plague in it, as a bite's. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float before = target instanceof LivingEntity living ? living.getHealth() : 0;
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            this.playSound(SoundEvents.CREAKING_ATTACK, 1.0F, 0.7F);
        }
        Mind.bit(hit, target, before);
        return hit;
    }

    /** A vine thrown out: the one it catches is pulled in toward it, off their feet, and slowed a moment. */
    public void grab(ServerLevel level, LivingEntity target) {
        this.entityData.set(THROWING, true);
        this.swingForAttack(InteractionHand.MAIN_HAND);
        Vec3 from = this.position().add(0, this.getBbHeight() * 0.6, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 way = to.subtract(from);
        int steps = Math.max(4, (int) (way.length() * 2));
        for (int k = 0; k <= steps; k++) {
            Vec3 at = from.add(way.scale(k / (double) steps));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState()), at.x, at.y, at.z, 2,
                    0.05, 0.05, 0.05, 0.0);
        }
        Vec3 pull = way.multiply(-1, 0, -1).normalize().scale(Math.min(1.6, 0.35 + way.horizontalDistance() * 0.11));
        target.setDeltaMovement(pull.x, 0.42, pull.z);
        if (target instanceof ServerPlayer player) {         // a player moves themselves: told so, as a blow's knockback is
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        }
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1), this);
        level.playSound(null, target.blockPosition(), SoundEvents.CREAKING_TWITCH, SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /**
     * What it takes: shut, a quarter; open, all of a blow and twice of fire; a shot or thrown thing a quarter at most, and
     * nothing while it is shut. Not drowning, nor suffocation, nor a fall: it grows in the wet and the dark.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        if (source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return false;
        }
        boolean shot = source.getDirectEntity() instanceof Projectile;
        float taken = amount * (shot ? (this.open() ? 0.25F : 0.0F) : this.open() ? 1.0F : 0.25F);
        if (source.is(DamageTypeTags.IS_FIRE)) {
            taken = amount * (this.open() ? 2.0F : 0.25F);       // shut, the fire gets no more in than a blade (the review, 2026-10-19)
        }
        if (taken <= 0) {
            this.playSound(SoundEvents.BIG_DRIPLEAF_HIT, 1.0F, 0.6F);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, taken);
        if (hurt && this.sporesIn == 0 && this.getRandom().nextInt(3) > 0) {
            this.sporesIn = SPORES_EVERY;
            this.spores(level);
        }
        return hurt;
    }

    /** Its breath: a cloud of spores round its foot that sickens, and the plague in whoever it reaches, as a bite's. */
    public void spores(ServerLevel level) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, this.getX(), this.getY(), this.getZ());
        cloud.setOwner(this);
        cloud.setRadius(3.5F);
        cloud.setRadiusPerTick(-0.01F);
        cloud.setDuration(20 * 6);
        cloud.setWaitTime(4);
        cloud.setCustomParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 4, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 7, 0));
        level.addFreshEntity(cloud);
        level.playSound(null, this.blockPosition(), SoundEvents.CREAKING_SWAY, SoundSource.HOSTILE, 1.4F, 0.5F);
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, new AABB(this.blockPosition()).inflate(4.0));
        for (ServerPlayer player : near) {
            dev.nookofthedead.infection.Infection.bitten(player);
        }
    }

    /** A plant: its own spores, and any brewed thing, are nothing to it. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.CREAKING_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CREAKING_HEART_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CREAKING_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SporesIn", this.sporesIn);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.sporesIn = input.getIntOr("SporesIn", 0);
    }
}
