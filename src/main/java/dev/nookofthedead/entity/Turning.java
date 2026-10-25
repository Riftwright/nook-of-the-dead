package dev.nookofthedead.entity;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Whoever dies in the fallen world gets up again as one of them (the films' friend who turns): a mutated zombie with
 * their name stands up where they fell, carrying nothing - what they had lies on the ground there, as it always has
 * (PLAN.md, 2026-10-13). It stays until killed, so a friend going back for the things meets them there.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Turning {
    /** The tag a risen player carries, to know one when it is killed. */
    public static final String RISEN = "nookofthedead.risen";

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().entityTags().contains(RISEN) && event.getSource().getEntity() instanceof ServerPlayer killer) {
            dev.nookofthedead.ZwAdvancements.award(killer, "familiar_face");
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level && NookOfTheDead.fallen(level)
                && !player.isSpectator()) {
            turn(level, player);
        }
    }

    /** One of them, named for {@code player}, where they are. */
    public static MutatedZombie turn(ServerLevel level, ServerPlayer player) {
        MutatedZombie risen = ZwEntities.MUTATED_ZOMBIE.get().create(level, EntitySpawnReason.CONVERSION);
        risen.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        risen.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.CONVERSION, null);
        risen.setCustomName(player.getName().copy());
        risen.setPersistenceRequired();
        risen.addTag(RISEN);
        level.addFreshEntity(risen);
        return risen;
    }

    /**
     * A villager the dead kill in the fallen world gets up as one of them too: the game turns it into a zombie villager
     * (as anywhere), and here that is a mutated one, what it was kept (the self-check, 2026-10-18: it rose plain - one
     * that burnt in the sun and a golden apple would cure).
     */
    @SubscribeEvent
    static void onConverted(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.npc.villager.Villager)
                || !(event.getOutcome() instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager plain) || plain instanceof Mutated
                || !(plain.level() instanceof ServerLevel level) || !NookOfTheDead.fallen(level)) {
            return;
        }
        plain.convertTo(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(), net.minecraft.world.entity.ConversionParams.single(plain, true, true), z -> {
            z.setVillagerData(plain.getVillagerData());
            z.setVillagerDataFinalized(true);
            z.mind().roll(z.getRandom());      // its own pace and senses, as any that comes (the review, 2026-10-19: always plain)
        });
    }

    private Turning() {}
}
