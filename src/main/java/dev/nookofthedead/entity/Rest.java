package dev.nookofthedead.entity;

import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Sleep in the fallen world behind walls (PLAN.md, 2026-10-15: "If では普通に眠れて、普通に朝になる"): the game keeps
 * anyone from sleeping with a monster within eight blocks of the bed, walls or not, and the dead gather at the walls of
 * wherever one holes up (the review, 2026-10-19: so the beds over there seldom worked). Over there the dead that cannot
 * see the sleeper - the other side of a wall or a floor - do not keep them awake.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Rest {
    @SubscribeEvent
    static void onSleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() != Player.BedSleepingProblem.NOT_SAFE || !NookOfTheDead.fallen(player.level())) {
            return;
        }
        Vec3 bed = Vec3.atBottomCenterOf(event.getPos());
        List<Monster> near = player.level().getEntitiesOfClass(Monster.class, new AABB(bed.x - 8, bed.y - 5, bed.z - 8, bed.x + 8, bed.y + 5, bed.z + 8),
                m -> m.isPreventingPlayerRest(player.level(), player));
        // any of them - the mutated, the rats and scorpions, a kept villager, the Vandal (the review, 2026-10-19)
        if (near.stream().allMatch(m -> !m.hasLineOfSight(player))) {
            event.setProblem(null);
        }
    }

    /**
     * Woken over there to the morning: Till Morning (the review, 2026-10-19: given by the game's slept_in_bed it came the
     * moment one lay down, the night not slept through).
     */
    @SubscribeEvent
    static void onWake(net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && NookOfTheDead.fallen(player.level())
                && !event.wakeImmediately() && !player.level().isDarkOutside()) {
            dev.nookofthedead.ZwAdvancements.award(player, "till_morning");
        }
    }

    private Rest() {}
}
