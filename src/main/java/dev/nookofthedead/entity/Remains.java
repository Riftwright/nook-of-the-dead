package dev.nookofthedead.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A survivor's remains (the user, 2026-10-16: "しばらく逃げ延びていたけど、力尽きて白骨化している…近くに彼が使っていた物資が
 * のこっている場合もある"): bones in what they wore, slumped against a wall or lying where they fell - not an armour stand
 * with a skull, but a skeleton's own frame drawn sitting or lying ({@code RemainsRenderer}). It does nothing and is moved
 * by nothing; struck by a player it falls apart into bones (and some of what it wore). It stays for good.
 */
public class Remains extends Mob {
    /** How it lies: slumped against a wall, or flat on its back. */
    public static final byte SITTING = 0;
    public static final byte LYING = 1;
    private static final EntityDataAccessor<Byte> POSE_KIND = SynchedEntityData.defineId(Remains.class, EntityDataSerializers.BYTE);

    public Remains(EntityType<? extends Remains> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setSilent(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(POSE_KIND, SITTING);
    }

    public byte kind() {
        return this.entityData.get(POSE_KIND);
    }

    public void kind(byte kind) {
        this.entityData.set(POSE_KIND, kind);
    }

    /** What they had on when they died: a little armour of the poorer kinds, often none; worn, kept now and then. */
    public void dress(RandomSource random) {
        if (random.nextInt(3) > 0) {
            this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.LEATHER_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
        }
        if (random.nextInt(3) == 0) {
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(random.nextBoolean() ? Items.LEATHER_HELMET : Items.IRON_HELMET));
        }
        if (random.nextInt(2) == 0) {
            this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
        }
        if (random.nextInt(2) == 0) {
            this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack worn = this.getItemBySlot(slot);
            if (worn.isDamageableItem()) {
                worn.setDamageValue(worn.getMaxDamage() * (5 + random.nextInt(4)) / 10);
            }
            this.setDropChance(slot, 0.0F);                               // dropped as worn, by hand (hurtServer)
        }
    }

    /** Of what it wore, how much is left whole enough to take when it falls apart. */
    public static final float WORN_DROPS = 0.35F;

    /** Struck by a player, it falls apart; nothing else harms it. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isRemoved()) {
            return false;
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            ItemStack held = this.getItemBySlot(EquipmentSlot.MAINHAND);
            if (held.is(dev.nookofthedead.registry.ZwItems.LAUNCH_KEY.get())) {   // its key not gone with it: found at the console
                dev.nookofthedead.ending.LostKeys.lost(level.getServer(), held.getCount());
            }
            this.discard();                                              // /kill, the void
            return true;
        }
        if (!(source.getEntity() instanceof Player)) {
            return false;
        }
        this.playSound(SoundEvents.SKELETON_DEATH, 0.6F, 0.7F);
        this.dropAllDeathLoot(level, source);
        for (EquipmentSlot slot : EquipmentSlot.values()) {               // some of what it wore, as worn as it was
            ItemStack worn = this.getItemBySlot(slot);
            // a launch key it held always (the vaccine's console wants four, and two of them are on the dead - 0.3)
            if (!worn.isEmpty() && (worn.is(dev.nookofthedead.registry.ZwItems.LAUNCH_KEY.get()) || this.random.nextFloat() < WORN_DROPS)) {
                this.spawnAtLocation(level, worn.copy());
            }
        }
        this.discard();
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("Lies", this.kind());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.kind(input.getByteOr("Lies", SITTING));
        this.setNoAi(true);
    }
}
