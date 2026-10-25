package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * For measuring only (the agent client, with {@code -Dnookofthedead.fpsLog=true}; never on in a player's game): once a
 * second, the frames drawn in it written to the log as {@code [fps] N}, so how heavy a place is can be read afterwards.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class FpsLog {
    private static final boolean ON = Boolean.getBoolean("nookofthedead.fpsLog");
    private static int ticks;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!ON || ++ticks % 20 != 0 || Minecraft.getInstance().level == null) {
            return;
        }
        NookOfTheDead.LOGGER.info("[fps] {}", Minecraft.getInstance().getFps());
    }

    private FpsLog() {}
}
