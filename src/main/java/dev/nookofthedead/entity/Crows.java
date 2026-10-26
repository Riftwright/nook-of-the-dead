package dev.nookofthedead.entity;

import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The capital's flocks ({@link CapitalCrow}): while someone is in or about the capital, its spires keep their crows -
 * {@link #FLOCK} in all, a few more come in at a time out of the sky over the spires, gone again once nobody is near.
 * Their own, outside the game's spawning: the dead's share of the world is untouched by them (the user, 2026-10-25:
 * "湧き上限を食い潰して、ゾンビが少なくなっちゃわないか心配").
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Crows {
    /** How many crows the capital's sky keeps while someone is near; how many come in at once; how often it looks. */
    public static final int FLOCK = 26;
    public static final int AT_ONCE = 3;
    public static final int EVERY = 40;
    /** How far from everyone they go. */
    public static final double GONE = 220.0;

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !NookOfTheDead.fallen(level) || level.getGameTime() % EVERY != 7
                || dev.nookofthedead.ending.Cure.cured(level.getServer())) {
            return;                                                     // none over a cured If (the review, 2026-10-25)
        }
        long seed = level.getSeed();
        int[] middle = dev.nookofthedead.world.Capital.centre(seed);
        boolean near = false;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - middle[0];
            double dz = player.getZ() - middle[1];
            near |= !player.isSpectator() && dx * dx + dz * dz < (dev.nookofthedead.world.Capital.ZONE + 60.0) * (dev.nookofthedead.world.Capital.ZONE + 60.0);
        }
        if (!near) {
            return;
        }
        List<BlockPos> roosts = dev.nookofthedead.world.CapitalRoosts.of(seed);
        int have = level.getEntities(ZwEntities.CAPITAL_CROW.get(), c -> true).size();
        for (int i = 0; i < AT_ONCE && have < FLOCK; i++, have++) {
            BlockPos roost = roosts.get(level.getRandom().nextInt(roosts.size()));
            if (!level.isLoaded(roost)) {
                continue;
            }
            CapitalCrow crow = ZwEntities.CAPITAL_CROW.get().create(level, EntitySpawnReason.NATURAL);
            if (crow == null) {
                continue;
            }
            // in out of the sky, high over its spire
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            crow.snapTo(roost.getX() + 0.5 + Math.cos(a) * 18, roost.getY() + 14, roost.getZ() + 0.5 + Math.sin(a) * 18, 0, 0);
            crow.roost(roost);
            level.addFreshEntity(crow);
        }
    }

    private Crows() {}
}
