package dev.nookofthedead;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * The fallen world's advancement tab ({@code data/nookofthedead/advancement/}, off {@code nookofthedead:root}; the user,
 * 2026-10-15: "進捗タブはぜひ作って"): vanilla's tone, a short title and one line of what to do. Those vanilla has no trigger
 * for hold one criterion, {@code awarded}, of {@code minecraft:impossible}, given here.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class ZwAdvancements {
    /** {@code player} given advancement {@code nookofthedead:<name>}, if not already. */
    public static void award(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(NookOfTheDead.id(name));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "awarded");
        }
    }

    /** Whether {@code player} has advancement {@code nookofthedead:<name>}. For tests. */
    public static boolean has(ServerPlayer player, String name) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(NookOfTheDead.id(name));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** A suppressant just brewed is marked so ({@link dev.nookofthedead.registry.ZwEffects#BREWED}). */
    @net.neoforged.bus.api.SubscribeEvent
    static void onBrew(net.neoforged.neoforge.event.brewing.PotionBrewEvent.Post event) {
        for (int i = 0; i < event.getLength(); i++) {
            if (dev.nookofthedead.registry.ZwEffects.isSuppressant(event.getItem(i))) {
                dev.nookofthedead.registry.ZwEffects.brewed(event.getItem(i));
            }
        }
    }

    /** Bottled Hope: a suppressant the stand brewed, taken out of it. */
    @net.neoforged.bus.api.SubscribeEvent
    static void onBrewed(net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && dev.nookofthedead.registry.ZwEffects.isSuppressant(event.getStack())
                && dev.nookofthedead.registry.ZwEffects.isBrewed(event.getStack())) {
            award(player, "brewed");
        }
    }

    private ZwAdvancements() {}
}
