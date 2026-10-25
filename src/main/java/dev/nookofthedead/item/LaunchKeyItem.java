package dev.nookofthedead.item;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * A launch key of the vaccine's console ({@link dev.nookofthedead.ending.VaccineBlock}). The end of the road waits on four
 * of them, so none may be lost for good (the user, 2026-10-23: "鍵をなくしたり、鍵持ちゾンビを下に落としたりして、鍵が入手
 * 困難になって詰むことが無いように"):
 * <ul>
 * <li>Dropped, it lies where it fell for as long as it takes: it never despawns, and nothing burns, crushes or blows it
 *     up (fire, lava, cactus, explosions, an anvil).</li>
 * <li>Gone all the same - out of the bottom of the world, or a key-holder fallen off the sky base and dead far below it -
 *     it is counted lost, and turns up again before the console the next time a player comes up to it
 *     ({@link dev.nookofthedead.ending.LostKeys}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public class LaunchKeyItem extends Item {
    /** The tag of a key-holder of the sky base (SkyBase): a thin-air zombie that holds its key and always lets it fall. */
    public static final String HOLDER = "nookofthedead_key_holder";

    public LaunchKeyItem(Properties properties) {
        super(properties);
    }

    /** Lying on the ground, it waits as long as it must. */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    static boolean key(Entity entity) {
        return entity instanceof ItemEntity item && item.getItem().is(ZwItems.LAUNCH_KEY.get());
    }

    /** Nothing harms a key lying on the ground but what harms everything (/kill, the void). */
    @SubscribeEvent
    static void onHurt(EntityInvulnerabilityCheckEvent event) {
        if (key(event.getEntity()) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setInvulnerable(true);
        }
    }

    /** A key on the ground gone some other way than into a hand, a hopper or another stack: lost, to turn up again. */
    @SubscribeEvent
    static void onGone(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !key(event.getEntity())) {
            return;
        }
        Entity.RemovalReason why = event.getEntity().getRemovalReason();
        ItemStack left = ((ItemEntity) event.getEntity()).getItem();
        if (why != null && why.shouldDestroy() && !left.isEmpty()) {
            dev.nookofthedead.ending.LostKeys.lost(level.getServer(), left.getCount());
        }
    }

    /**
     * One of the dead with a key in its hands put down where the game drops nothing from mobs (the {@code mob_drops} rule
     * off): its key counted lost, to turn up before the console (in game, 2026-10-23: a world with the rule off had the
     * key-holders' keys gone with them).
     */
    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob) || !(mob.level() instanceof ServerLevel level)
                || level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS)) {
            return;
        }
        int keys = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.VALUES) {
            ItemStack worn = mob.getItemBySlot(slot);
            if (worn.is(ZwItems.LAUNCH_KEY.get())) {
                keys += worn.getCount();
            }
        }
        if (keys > 0) {
            dev.nookofthedead.ending.LostKeys.lost(level.getServer(), keys);
        }
    }

    /**
     * A key-holder dead far below the base (fallen off it): its key counted lost, to turn up before the console, not left
     * on the ground four hundred blocks down.
     */
    @SubscribeEvent
    static void onHolderDrops(LivingDropsEvent event) {
        if (!event.getEntity().entityTags().contains(HOLDER) || !(event.getEntity().level() instanceof ServerLevel level)
                || event.getEntity().getY() >= dev.nookofthedead.world.Tower.BASE - 8) {
            return;
        }
        event.getDrops().removeIf(drop -> {
            if (drop.getItem().is(ZwItems.LAUNCH_KEY.get())) {
                dev.nookofthedead.ending.LostKeys.lost(level.getServer(), drop.getItem().getCount());
                drop.setItem(ItemStack.EMPTY);
                return true;
            }
            return false;
        });
    }
}
