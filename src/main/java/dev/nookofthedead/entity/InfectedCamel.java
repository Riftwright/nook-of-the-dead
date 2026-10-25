package dev.nookofthedead.entity;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * A camel the plague took (the user, 2026-10-18: "感染ラクダ"): the game's camel husk to look at, gone for anyone it sees
 * ({@link Infected}) - tall, seen coming from far off over the dunes, and its weight behind its blow. Not ridden, tamed,
 * fed or led. Its own mind - the camel's, that sits down and wanders - is not ticked: only the plague's simple one.
 */
public class InfectedCamel extends CamelHusk implements Quiet {
    public InfectedCamel(EntityType<? extends Camel> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Camel.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        Infected.goals(this, 1.4, 0.6);
    }

    /** Not the camel's brain (it would sit it down, walk it off): the goals alone, and its kind roused. */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        Infected.rouse(level, this);
    }

    /** Alone: no rider with it, whatever the game's would bring. */
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        for (Entity rider : java.util.List.copyOf(this.getPassengers())) {
            rider.stopRiding();
            rider.discard();
        }
        return data;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return InteractionResult.PASS;
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
    public @Nullable Camel getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.hasCustomName();
    }
}
