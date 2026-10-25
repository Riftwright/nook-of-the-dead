package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A mutated zombie that was a villager (the user, 2026-10-15: "見た目が均一すぎるから、村人ゾンビも時々混ぜて。挙動は他の
 * ゾンビと同じでよい"): it looks as vanilla's zombie villager, and does all a {@link MutatedZombie} does ({@link Mind}).
 * A golden apple does nothing for it: the mutation has to be put down first, with a suppressant thrown at it, which leaves an
 * ordinary zombie villager - and that one a golden apple and weakness cure, as in the overworld
 * ({@link dev.nookofthedead.infection.SuppressantEffect}).
 */
public class MutatedZombieVillager extends ZombieVillager implements Mutated {
    /** Made on first asking: vanilla adds the goals from its constructor, before this class's fields are set. */
    private Mind mind;

    public MutatedZombieVillager(EntityType<? extends MutatedZombieVillager> type, Level level) {
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
        return MutatedZombie.createAttributes();
    }

    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        this.mind().goals(this.goalSelector, this.targetSelector);
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

    /** Not cured as it is: a golden apple is not even taken. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
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

    /** Never the sky base's guards (as MutatedZombie). */
    @Override
    public boolean canAttack(net.minecraft.world.entity.LivingEntity target) {
        return !(target instanceof SentryGolem) && super.canAttack(target);
    }
}
