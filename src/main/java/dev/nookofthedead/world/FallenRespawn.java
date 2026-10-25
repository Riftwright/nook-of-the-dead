package dev.nookofthedead.world;

import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.NookOfTheDead;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * Dying in the fallen world takes one home (the user, 2026-10-13: "死んだらオーバーワールドに帰ることにしようか"): a bed
 * there sets no respawn point - one sleeps there, but wakes at home after dying - and one already there (set before this was so) sends its
 * player to the overworld's spawn instead. What is lost there stays there. A point an operator forces there with
 * {@code /spawnpoint} is kept.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class FallenRespawn {
    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (event.getNewSpawn() != null && !event.isForced() && event.getSpawnLevel().equals(IfWorld.KEY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerRespawnPositionEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        if (event.getTeleportTransition().newLevel().dimension().equals(IfWorld.KEY) && (config == null || !config.forced())) {
            event.setTeleportTransition(TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING));
        }
    }

    private FallenRespawn() {}
}
