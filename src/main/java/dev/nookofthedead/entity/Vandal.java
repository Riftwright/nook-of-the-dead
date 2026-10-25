package dev.nookofthedead.entity;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Vandal (a working name, the user's, 2026-10-15: "研究所の奥にいるタイラントみたいな感じ…初期ロットのゾンビで変異を
 * 繰り返して他のゾンビよりも強くなってる"): one of the first of them, gone on changing, kept at the bottom of the
 * research institute. Well over twice a zombie's size (scaled up {@code 2.4} times), heavy and slow to stagger, it walks through stone - any block of
 * {@link #HARDEST} or softer in its way goes ({@link SmashGoal}), only the hardest and the vault's iron stop it
 * ({@code #nookofthedead:vandal_proof}); now and then it rushes at whoever it is after ({@link ChargeGoal}); and it roars
 * ({@link #ROAR_EVERY}), and every one of them within {@link #ROAR_REACH} comes to it. Wounded to half it goes faster and
 * roars more (its eyes burn red: client/VandalRenderer). A boss bar shows it to those near. It never despawns; the suppressant does nothing to it (it is not one
 * of the mutated); what it leaves is its blood, which makes the suppressant brewable ({@code vandal_blood}).
 * <p>
 * Fair as a fight: it is slow between rushes, a rush runs straight and can be stepped aside from, its roar comes with a
 * wind-up (it stands and the sound starts before the dead come), and the hall is full of pillars to put between.
 */
public class Vandal extends Zombie implements Quiet {
    public static final double SPEED = 0.27;
    /** Blocks no harder than this it smashes through (stone is 1.5, deepslate 3, iron bars and iron doors 5). */
    public static final float HARDEST = 5.0F;
    /** Ticks between roars, calm and wounded. */
    public static final int ROAR_EVERY = 400;
    public static final int ROAR_EVERY_WOUNDED = 200;
    public static final double ROAR_REACH = 48.0;
    public static final TagKey<Block> PROOF = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            dev.nookofthedead.NookOfTheDead.id("vandal_proof"));
    private static final Identifier ENRAGED = dev.nookofthedead.NookOfTheDead.id("vandal_enraged");

    private final ServerBossEvent bar = (ServerBossEvent) new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.nookofthedead.vandal"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);
    private int roarIn = ROAR_EVERY / 2;
    private int windUp;
    /** How long its roar is still shown after it rang out. */
    private int roared;

    /**
     * What it is doing, sent to the clients for its look (client/VandalModel): {@link #CALM}, {@link #ROARING} (the
     * wind-up and the roar: the head thrown back, the jaw wide), {@link #CROUCHING} (a rush's wind-up: down low),
     * {@link #RUSHING} (the rush: flat out on all fours).
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> POSE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(Vandal.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    public static final byte CALM = 0;
    public static final byte ROARING = 1;
    public static final byte CROUCHING = 2;
    public static final byte RUSHING = 3;

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(POSE, CALM);
    }

    public byte pose() {
        return this.entityData.get(POSE);
    }

    void pose(byte pose) {
        if (this.entityData.get(POSE) != pose) {
            this.entityData.set(POSE, pose);
        }
    }

    public Vandal(EntityType<? extends Vandal> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.setPersistenceRequired();
        // its size by its scale: the frame was sized before there were attributes to scale it by, and a scale that is only
        // the default is never "changed" to resize it (the review, 2026-10-18: it went through a doorway three high)
        this.refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.SCALE, 2.4)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SmashGoal(this));
        this.goalSelector.addGoal(2, new ChargeGoal(this));
        this.goalSelector.addGoal(3, new ZombieAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void addBehaviourGoals() {
        // none of the zombie's own: registerGoals above is all of it
    }

    public boolean wounded() {
        return this.getHealth() <= this.getMaxHealth() / 2;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Hardness.fit(this);
        this.bar.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 10 == 0) {
            this.showBar(level);
        }
        if (this.wounded() && !this.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(ENRAGED)) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(ENRAGED, 0.25,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            this.bar.setColor(BossEvent.BossBarColor.PURPLE);
        }
        if (this.windUp > 0) {                                          // the roar's wind-up: it stands, the sound has begun
            this.getNavigation().stop();
            this.pose(ROARING);
            if (--this.windUp == 0) {
                this.roar(level);
                this.roared = 20;
            }
            return;
        }
        if (this.roared > 0 && --this.roared == 0 && this.pose() == ROARING) {
            this.pose(CALM);
        }
        if (this.getTarget() != null && --this.roarIn <= 0) {
            this.roarIn = (int) Math.round((this.wounded() ? ROAR_EVERY_WOUNDED : ROAR_EVERY) * Hardness.of(level).roar());
            this.windUp = 25;
            this.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
        }
        // no way round to its prey (a wall right across): it walks straight at them, into the wall, and SmashGoal breaks it
        // (the review, 2026-10-18: with no path it only stood there)
        LivingEntity prey = this.getTarget();
        if (prey == null) {
            this.stuck = 0;
            return;
        }
        // what it can reach it strikes, sight or no sight: round a pillar, the one cutting at it from behind is within its
        // arm (the play test, 2026-10-19: wedged against the tank's iron column, its blows never came - the game's melee
        // asks for a clear line from its eyes)
        if (this.reachRound > 0) {
            this.reachRound--;
        } else if (this.level() instanceof ServerLevel server && this.isWithinMeleeAttackRange(prey) && !this.getSensing().hasLineOfSight(prey)) {
            this.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            this.doHurtTarget(server, prey);
            this.reachRound = 20;
        }
        // held fast - two seconds hardly moved with its prey near and out of its sight or something it cannot break before
        // it - it steps round to one side for a moment, as one would round a pillar (the play test, 2026-10-19: pressed
        // against the tank's iron column with the player behind it, it stood there and was cut down without a blow back)
        if (this.tickCount % 40 == 0) {
            boolean held = this.position().distanceToSqr(this.lastSeenAt) < 0.25 && this.distanceToSqr(prey) < 14 * 14
                    && (!this.getSensing().hasLineOfSight(prey) || this.horizontalCollision && this.wouldSmash().isEmpty());
            this.stuck = held ? this.stuck + 1 : 0;
            this.lastSeenAt = this.position();
            if (this.stuck >= 1) {
                this.stuck = 0;
                Vec3 to = prey.position().subtract(this.position());
                Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(this.getRandom().nextBoolean() ? 5.0 : -5.0);
                this.aside = this.position().add(side);
                this.asideFor = 30;
                this.getNavigation().stop();
            }
        }
        if (this.asideFor > 0) {
            this.asideFor--;
            this.getMoveControl().setWantedPosition(this.aside.x, this.getY(), this.aside.z, 1.0);
            return;
        }
        if (this.getNavigation().isDone() && this.distanceToSqr(prey) > 9.0 && Math.abs(prey.getY() - this.getY()) < 3.0) {
            this.getMoveControl().setWantedPosition(prey.getX(), this.getY(), prey.getZ(), 1.0);
        }
    }

    /** For stepping round what holds it fast: where it stood two seconds ago, how many checks it has not moved, where to. */
    private Vec3 lastSeenAt = Vec3.ZERO;
    private int stuck;
    private Vec3 aside = Vec3.ZERO;
    private int asideFor;
    /** Ticks till it may strike round what is between it and its prey again. */
    private int reachRound;

    /**
     * Its reach, as long as its arms are: half again a man's height out from its frame (vanilla's is a man's, the same
     * for a giant: the play test, 2026-10-19, stood within a sword's reach of it and out of its own).
     */
    @Override
    public boolean isWithinMeleeAttackRange(LivingEntity target) {
        return this.getAttackBoundingBox(1.6).intersects(target.getHitbox());
    }

    /**
     * Its roar: every one of the dead within {@link #ROAR_REACH} comes (to where it is, or after its prey if they would
     * notice them), and whoever stands close is shaken (slowed a moment).
     */
    void roar(ServerLevel level) {
        this.playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.7F);
        level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getEyeY(), this.getZ(), 1, 0, 0, 0, 0);
        LivingEntity prey = this.getTarget();
        for (Zombie dead : level.getEntitiesOfClass(Zombie.class, this.getBoundingBox().inflate(ROAR_REACH), z -> z instanceof Mutated)) {
            Mind mind = Mind.of(dead);
            if (prey instanceof Player player && mind.notices(player)) {
                dead.setTarget(player);
            } else {
                mind.lead(this.position());
            }
        }
        for (Player near : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(8.0), p -> !p.isCreative() && !p.isSpectator())) {
            near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1));
        }
    }

    /**
     * Its bar only for those face to face with it - near and in sight, or the one it hunts - not for anyone the game keeps
     * it in view for (some ten chunks: a bar over the town's roofs, from deep under the ground).
     */
    private void showBar(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            boolean facing = player.isAlive() && !player.isSpectator() && player.distanceToSqr(this) < BAR_REACH * BAR_REACH
                    && (player == this.getTarget() || this.getSensing().hasLineOfSight(player));
            if (facing) {
                this.bar.addPlayer(player);
            } else if (this.bar.getPlayers().contains(player)) {
                this.bar.removePlayer(player);
            }
        }
    }

    /** How near one must be to see its bar. */
    public static final double BAR_REACH = 24.0;

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bar.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
    }

    /** No reinforcements, no leader's bonus: vanilla's zombie would call more of its own type - more Vandals - when hit. */
    @Override
    protected void handleAttributes(float difficultyModifier, net.minecraft.world.entity.EntitySpawnReason spawnReason) {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
    }

    /**
     * No door-breaking (the review, 2026-10-19: vanilla gave it to one in ten on Hard - its goal broke doors past the
     * traits and past Dark Matter - and kept it in their saves). What they break is BreakSoftBlocksGoal's.
     */
    @Override
    public void setCanBreakDoors(boolean canBreakDoors) {
        super.setCanBreakDoors(false);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData groupData) {
        // alone: no chicken to ride (vanilla's jockey roll put an adult on a live chicken: the review, 2026-10-19)
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason,
                groupData != null ? groupData : new net.minecraft.world.entity.monster.zombie.Zombie.ZombieGroupData(false, false));
        if (this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.CARVED_PUMPKIN)
                || this.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.JACK_O_LANTERN)) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);   // vanilla's Halloween
        }
        this.setBaby(false);
        this.setCanPickUpLoot(false);
        this.setHealth(this.getMaxHealth());
        return data;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // nor from inside the refuge: its way in is too low for it, and was a safe slit to strike from (the review, 2026-10-19)
        if (source.getEntity() != null && dev.nookofthedead.world.Refuge.inside(level, source.getEntity().blockPosition())) {
            return false;
        }
        if (!hurtBy(source) || !super.hurtServer(level, source, amount)) {
            return false;
        }
        if (source.getEntity() == null && this.getTarget() == null) {
            // hurt by what has no one behind it - a laser fired from out of its sight - it goes for the nearest one who
            // could have (Nookworks' review, 2026-10-19: it stood where it was and died)
            net.minecraft.world.entity.player.Player near = level.getNearestPlayer(this.getX(), this.getY(), this.getZ(), UNSEEN_REACH,
                    p -> p.isAlive() && !p.isSpectator() && !((net.minecraft.world.entity.player.Player) p).isCreative()
                            && !dev.nookofthedead.world.Refuge.inside(level, p.blockPosition()));
            if (near != null) {
                this.setTarget(near);
            }
        }
        return true;
    }

    /** How far it looks for the one behind a hurt with no one behind it. */
    public static final double UNSEEN_REACH = 96.0;

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bar.setName(this.getDisplayName());           // a name tag shows on its bar at once, as the Wither's does
    }

    /**
     * Whether {@code source} can hurt it: only what a player does to it - a blow, a shot, a thrown grenade or potion. Not
     * a fall nor its own kind's blows (the self-check, 2026-10-18: one led off a ledge was worn down by its own weight),
     * and nothing of the place - lava, fire, drowning, a wall closing on it, Nookworks' Liquid Experience and mercury (the
     * user, 2026-10-19, told it would die standing in a pool of Liquid Experience poured from the gallery: "環境ダメージは
     * 無効にしましょう。水銀も効かない"). Nookworks' laser does, a thing set up and fired on purpose ("レーザーは効いても
     * いい"). Those that pass all invulnerability (/kill, the void) still do.
     */
    public static boolean hurtBy(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        // nor anything shot or thrown: arrows, tridents, grenades, potions (the user, 2026-10-19: "飛び道具効かない") - it is
        // fought hand to hand
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
            return false;
        }
        var type = source.typeHolder().unwrapKey().map(k -> k.identifier());
        if (type.map(id -> id.getNamespace().equals("nookworks")).orElse(false)) {
            return type.get().getPath().equals("laser");
        }
        return source.getEntity() instanceof net.minecraft.world.entity.player.Player;
    }

    /** Its institute, and which of its Vandals it is ({@link dev.nookofthedead.world.VandalKeeper}); null till it is given one. */
    private @org.jspecify.annotations.Nullable String home;
    private int count;

    public @org.jspecify.annotations.Nullable String home() {
        return this.home;
    }

    public int count() {
        return this.count;
    }

    public void home(String home, int count) {
        this.home = home;
        this.count = count;
    }

    /**
     * Kept on Peaceful, as the Ender Dragon is (the user, 2026-10-23: turned Peaceful, it was taken away, and the refuge's
     * door with it shut for good). Peaceful takes the harm out of its blows as out of every mob's.
     */
    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            super.checkDespawn();                                       // kept for good otherwise too (setPersistenceRequired)
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("RoarIn", this.roarIn);
        if (this.home != null) {
            output.putString("Home", this.home);
            output.putInt("Count", this.count);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.roarIn = input.getIntOr("RoarIn", ROAR_EVERY / 2);
        this.home = input.getString("Home").orElse(null);
        this.count = input.getIntOr("Count", 0);
        if (this.hasCustomName()) {
            this.bar.setName(this.getDisplayName());
        }
    }

    /**
     * Through what stands between it and its prey: when it is held up against something, the blocks before it - as wide
     * and as high as it is - that are no harder than {@link #HARDEST} and not {@code #vandal_proof}, after a short pause,
     * all at once, with a crash.
     */
    static class SmashGoal extends Goal {
        private final Vandal vandal;
        private int time;

        SmashGoal(Vandal vandal) {
            this.vandal = vandal;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.vandal.getTarget() != null && this.vandal.horizontalCollision && this.vandal.windUp == 0 && !this.inTheWay().isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return this.time > 0 && this.vandal.getTarget() != null;
        }

        @Override
        public void start() {
            this.time = 18;
            this.vandal.getNavigation().stop();
            this.vandal.playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 2.0F, 0.5F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity prey = this.vandal.getTarget();
            if (prey != null) {
                this.vandal.getLookControl().setLookAt(prey);
            }
            if (--this.time == 0 && this.vandal.level() instanceof ServerLevel level) {
                for (BlockPos at : this.inTheWay()) {
                    level.destroyBlock(at, false, this.vandal);
                }
                level.playSound(null, this.vandal.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
            }
        }

        /**
         * The blocks it would smash: those its body would walk into next, toward its prey (or as it faces, with none) - a
         * slab before its front as wide and high as it is.
         */
        java.util.List<BlockPos> inTheWay() {
            Level level = this.vandal.level();
            LivingEntity prey = this.vandal.getTarget();
            Direction toward = prey == null ? this.vandal.getDirection()
                    : Direction.fromYRot(Math.toDegrees(Math.atan2(-(prey.getX() - this.vandal.getX()), prey.getZ() - this.vandal.getZ())));
            net.minecraft.world.phys.AABB body = this.vandal.getBoundingBox().deflate(0.01);
            double reach = 0.9;
            net.minecraft.world.phys.AABB slab = switch (toward) {
                case EAST -> new net.minecraft.world.phys.AABB(body.maxX, body.minY, body.minZ, body.maxX + reach, body.maxY, body.maxZ);
                case WEST -> new net.minecraft.world.phys.AABB(body.minX - reach, body.minY, body.minZ, body.minX, body.maxY, body.maxZ);
                case SOUTH -> new net.minecraft.world.phys.AABB(body.minX, body.minY, body.maxZ, body.maxX, body.maxY, body.maxZ + reach);
                default -> new net.minecraft.world.phys.AABB(body.minX, body.minY, body.minZ - reach, body.maxX, body.maxY, body.minZ);
            };
            java.util.List<BlockPos> found = new java.util.ArrayList<>();
            for (BlockPos each : BlockPos.betweenClosed(BlockPos.containing(slab.minX, slab.minY, slab.minZ), BlockPos.containing(slab.maxX, slab.maxY, slab.maxZ))) {
                BlockPos at = each.immutable();
                BlockState state = level.getBlockState(at);
                float hardness = state.getDestroySpeed(level, at);
                if (!state.isAir() && hardness >= 0 && hardness <= HARDEST && !state.is(PROOF) && !state.getCollisionShape(level, at).isEmpty()
                        && !dev.nookofthedead.world.Refuge.inside(level, at)   // the refuge they walled off from it
                        && (!(level instanceof ServerLevel server) || net.neoforged.neoforge.common.CommonHooks.canEntityDestroy(server, at, this.vandal))) {
                    found.add(at);
                }
            }
            return found;
        }
    }

    /**
     * Its rush: prey in sight between {@link #NEAR} and {@link #FAR} away, it lowers its head a moment (a wind-up with a
     * growl), then runs straight at where they stood, twice as fast, for a second and a half; whoever it hits is thrown.
     * Then a while before the next.
     */
    static class ChargeGoal extends Goal {
        static final double NEAR = 5.0;
        static final double FAR = 18.0;
        static final int REST = 160;
        private final Vandal vandal;
        private int phase;
        private int rest = 60;
        private Vec3 at = Vec3.ZERO;

        ChargeGoal(Vandal vandal) {
            this.vandal = vandal;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (--this.rest > 0 || this.vandal.windUp > 0) {
                return false;
            }
            LivingEntity prey = this.vandal.getTarget();
            if (prey == null) {
                return false;
            }
            double far = this.vandal.distanceToSqr(prey);
            return far > NEAR * NEAR && far < FAR * FAR && this.vandal.getSensing().hasLineOfSight(prey);
        }

        @Override
        public boolean canContinueToUse() {
            return this.phase > 0;
        }

        @Override
        public void start() {
            this.phase = 50;                                             // 20 of wind-up, 30 of running
            this.vandal.getNavigation().stop();
            this.vandal.playSound(SoundEvents.RAVAGER_STUNNED, 2.0F, 0.6F);
            LivingEntity prey = this.vandal.getTarget();
            this.at = prey == null ? this.vandal.position() : prey.position();
        }

        @Override
        public void stop() {
            this.phase = 0;
            this.rest = this.vandal.wounded() ? REST / 2 : REST;
            this.vandal.pose(CALM);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.phase--;
            this.vandal.getLookControl().setLookAt(this.at.x, this.at.y + 1, this.at.z);
            if (this.phase > 30) {
                this.vandal.pose(CROUCHING);
                return;
            }
            this.vandal.pose(RUSHING);
            Vec3 way = this.at.subtract(this.vandal.position()).multiply(1, 0, 1);
            if (way.lengthSqr() < 1.0) {
                this.phase = 0;
                return;
            }
            Vec3 push = way.normalize().scale(SPEED * 2.2);
            this.vandal.setDeltaMovement(push.x, this.vandal.getDeltaMovement().y, push.z);
            for (LivingEntity hit : this.vandal.level().getEntitiesOfClass(LivingEntity.class, this.vandal.getBoundingBox().inflate(0.4),
                    e -> e != this.vandal && e instanceof Player p && !p.isCreative() && !p.isSpectator())) {
                if (this.vandal.level() instanceof ServerLevel level) {
                    this.vandal.doHurtTarget(level, hit);
                    hit.push(push.x * 2.5, 0.45, push.z * 2.5);
                    hit.needsSync = true;
                }
                this.phase = 0;
            }
            if (this.vandal.horizontalCollision) {                      // ran into a wall: stunned a moment
                this.phase = 0;
                this.vandal.playSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 2.0F, 0.5F);
            }
        }
    }

    /** For tests: the boss bar's players. */
    public java.util.Collection<ServerPlayer> watchers() {
        return this.bar.getPlayers();
    }

    /** For tests: start a roar now (no wind-up). */
    public void roarNow(ServerLevel level) {
        this.roar(level);
    }

    /** For tests: what it would smash in front of it now. */
    public java.util.List<BlockPos> wouldSmash() {
        return new SmashGoal(this).inTheWay();
    }

}
