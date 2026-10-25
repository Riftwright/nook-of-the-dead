package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * The fallen world's zombie, the Mutated Zombie (変異ゾンビ, the user's name, 2026-10-15): quick (as a sprinting player, some
 * a little slower or faster - {@link Traits}), not burnt by the sun, weaker than vanilla's one to one but hardly pushed back -
 * they press in as a crowd - and breaking through soft blocks to get at whoever it hunts ({@link BreakSoftBlocksGoal}). It
 * sees only what is in sight and hears what is not ({@link Mind}, {@link Hearing}). It cannot climb a ladder or a vine:
 * up a ladder and the ladder broken is the way out of a crowd, as in the films. Up a stair it climbs, it keeps coming. It
 * never comes as a baby, calls no reinforcements of its own and carries nothing; it drops little. Most come with the
 * {@link Horde}. An elite one (a fallen player, {@link #elite}) wears what that player wore.
 */
public class MutatedZombie extends Zombie implements Mutated {
    /** The common pace (a mob's pace goes with the square of it - 0.36 is a sprinting player's): see {@link Traits.Pace}. */
    public static final double SPEED = Traits.Pace.RUN.speed;
    /** At most this many within {@link #CROWD_RADIUS} of a natural spawn (the {@link Horde} keeps its own count). */
    public static final int CROWD = 30;
    public static final double CROWD_RADIUS = 32.0;

    /** Made on first asking: vanilla adds the goals from its constructor, before this class's fields are set. */
    private Mind mind;

    /**
     * Whether it is a screamer ({@link Traits.Voice#SCREAM}), sent to the clients: it is drawn in the other player's
     * clothes, so it can be told from the rest (the user, 2026-10-17: "叫ぶ個体は、見た目で違いが分かるように…アレックス
     * ライクな服装"; {@code client/ZwClient}).
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> SCREAMER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MutatedZombie.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(SCREAMER, false);
    }

    public boolean screamer() {
        return this.entityData.get(SCREAMER);
    }

    void screamer(boolean screamer) {
        this.entityData.set(SCREAMER, screamer);
    }

    public MutatedZombie(EntityType<? extends MutatedZombie> type, Level level) {
        super(type, level);
    }

    @Override
    public Mind mind() {
        if (this.mind == null) {
            this.mind = new Mind(this);
        }
        return this.mind;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.FOLLOW_RANGE, Traits.Sense.PLAIN.sight)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ATTACK_DAMAGE, 2.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 0.0);
    }

    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        this.mind().goals(this.goalSelector, this.targetSelector);
    }

    /** Whether it notices {@code prey} where they are ({@link Mind#notices}). */
    public boolean notices(LivingEntity prey) {
        return this.mind().notices(prey);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.mind().step(level);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        float before = target instanceof net.minecraft.world.entity.LivingEntity living ? living.getHealth() : 0;
        boolean hit = super.doHurtTarget(level, target);
        Mind.bit(hit, target, before);
        return hit;
    }

    /** Never the sky base's guards: they were set against the living, and the dead leave them be (0.3). */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return !(target instanceof SentryGolem) && super.canAttack(target);
    }

    @Override
    public boolean onClimbable() {
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

    /** No leaders, no reinforcements: how many come is the spawning's to say ({@link #CROWD}). */
    @Override
    protected void handleAttributes(float difficultyModifier, EntitySpawnReason spawnReason) {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
    }

    @Override
    protected void randomizeReinforcementsChance() {
        this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // empty-handed: what it had went with the town
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
        this.mind().roll(this.getRandom());
        return data;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.mind().save(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.mind().load(input);
    }

    /** A fallen player: diamond worn and a sword, far tougher; what it wears it keeps when it dies. */
    public void elite() {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0F);
        }
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        this.setHealth(40.0F);
        this.setPersistenceRequired();
        this.addTag(ELITE);
    }

    /** The tag a fallen player carries. */
    public static final String ELITE = "nookofthedead.elite";

    /**
     * Where one may come on its own: anywhere a mob could stand, by day or night - but not under a roof with a light
     * ({@code block light 8} or more), the one safe kind of place - and not where {@link #CROWD} are about already.
     */
    public static <T extends Mob> boolean checkSpawnRules(EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
            RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        // vanilla's ground, or the plague's own beyond it (Footing: glass, a bottom slab - the review, 2026-10-21: vanilla's
        // check here still kept them off a floor the placement had let them onto)
        BlockPos under = pos.below();
        net.minecraft.world.level.block.state.BlockState below = level.getBlockState(under);
        if (!EntitySpawnReason.isSpawner(reason) && !below.isValidSpawn(level, under, type) && !Footing.floor(below)) {
            return false;
        }
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return true;
        }
        if (safe(level, pos)) {
            return false;
        }
        return level.getEntitiesOfClass(Zombie.class, new AABB(pos).inflate(CROWD_RADIUS), z -> z instanceof Mutated).size() < CROWD;
    }

    /**
     * Under a roof and lit, in a Dark Matter's reach ({@link dev.nookofthedead.world.Sanctuary}) or in the institute's
     * refuge ({@link dev.nookofthedead.world.Refuge}): where none come on their own.
     */
    public static boolean safe(ServerLevelAccessor level, BlockPos pos) {
        return !level.canSeeSky(pos) && level.getBrightness(LightLayer.BLOCK, pos) >= 8 || dev.nookofthedead.world.Refuge.guarded(level, pos);
    }

    /** For tests: one made and placed at {@code pos} in {@code level}, as the world would spawn one. */
    public static <T extends Zombie> T spawnAt(ServerLevel level, EntityType<T> type, BlockPos pos) {
        T zombie = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        zombie.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
        level.addFreshEntity(zombie);
        return zombie;
    }
}
