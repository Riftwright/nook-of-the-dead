package dev.nookofthedead.entity;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/**
 * The dead are not kept, nor farmed (the user, 2026-10-16: "ボートに乗せたモブを大量に置いて湧きをコントロールする、
 * ゾンビトラップを作る、とかを簡単にはさせない…名札やボートに乗せてもデスポーンする"):
 * <ul>
 * <li>they will not sit in a boat or a minecart;</li>
 * <li>one held all the same - riding, on a lead, given a name, or shut in a pit or a pen ({@link Mind#penned}) - goes as the others do once no one is within
 * {@link #GONE} blocks, and none held counts towards the dead about a player ({@link Horde}): a boat-load of them keeps no
 * others away;</li>
 * <li>the mutated dead leave nothing at all; the animals and the rest, nothing when they die of anything but a player's
 * hand - a fall, lava, drowning, a crush of their own: a trap gives no loot.</li>
 * </ul>
 * One risen from a player (named, {@link Turning#RISEN}), one the world placed for good ({@link #KEPT}) and the Vandal keep as
 * they always did.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Unkept {
    /** How far the nearest player may be before one held goes. */
    public static final double GONE = 32.0;
    /** The tag of one the world placed for good where it is: the capital's king, shut in his own room - not penned by a player. */
    public static final String KEPT = "nookofthedead.kept";

    /** Whether {@code entity} is one of the plague's: the mutated dead and the animals, bats and rats it took. */
    public static boolean plague(Entity entity) {
        // not the Vandal, nor the ones the world placed once and never again (the review, 2026-10-19: a name tag took a Sentry
        // Golem or a sewer bloom for good)
        return entity instanceof Mutated || entity instanceof Quiet && !(entity instanceof Vandal) && !(entity instanceof SewerBloom)
                && !(entity instanceof SentryGolem);
    }

    /** Whether {@code mob} is held where it would not go on its own: riding, on a lead, named by someone. */
    public static boolean held(Mob mob) {
        if (mob.entityTags().contains(Turning.RISEN) || mob.entityTags().contains(KEPT)) {
            return false;                                                    // kept as they always were, wherever they are
        }
        if (mob.isPassenger() || mob.isLeashed() || mob.hasCustomName()) {
            return true;
        }
        Mind mind = Mind.of(mob);
        return mind != null && mob.level() instanceof ServerLevel level && mind.penned(level);    // shut in a pit or a pen
    }

    @SubscribeEvent
    static void onDespawn(MobDespawnEvent event) {
        Mob mob = event.getEntity();
        if (plague(mob) && held(mob) && mob.level() instanceof ServerLevel level && NookOfTheDead.fallen(level)
                && level.getNearestPlayer(mob, GONE) == null) {
            event.setResult(MobDespawnEvent.Result.ALLOW);
        }
    }

    @SubscribeEvent
    static void onMount(EntityMountEvent event) {
        // the Vandal neither (the self-check, 2026-10-18: a minecart took it up, whatever its size, and held it for good)
        if (event.isMounting() && (plague(event.getEntityMounting()) || event.getEntityMounting() instanceof Vandal)
                && event.getEntityBeingMounted() instanceof VehicleEntity) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        var dead = event.getEntity();
        // the mutated dead leave nothing at all (the user, 2026-10-16: "そもそも変異ゾンビは何もドロップしなくていい"): not what
        // they wore, not what they held; the plague's animals, only to a player's hand - what killed it a player's blow or
        // shot, or the fire a player's blade set (not a trap after one blow: the review, 2026-10-18)
        var source = event.getSource();
        boolean byHand = source.getEntity() instanceof Player
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && dead.getLastHurtByPlayer() != null && !dead.isInLava();
        if (dead instanceof Mutated || plague(dead) && !byHand) {
            // but a launch key one carried (the sky base's key-holders, a thin-air zombie now and then): the end of the road
            // waits on it (in game, 2026-10-23: the key-holder's key went with the rest)
            event.getDrops().removeIf(drop -> !drop.getItem().is(dev.nookofthedead.registry.ZwItems.LAUNCH_KEY.get()));
            if (event.getDrops().isEmpty()) {
                event.setCanceled(true);
            }
        }
    }

    private Unkept() {}
}
