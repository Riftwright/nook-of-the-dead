package dev.nookofthedead.infection;

import org.jspecify.annotations.Nullable;

import dev.nookofthedead.entity.Mutated;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.InstantaneousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

/**
 * The suppressant, as a potion's one effect (the user, 2026-10-15: "抑制ポーションは…醸造台でスプラッシュ化や残留化はできる"):
 * drunk, or splashed on, a player has the infection held back the full twenty minutes ({@link Infection#suppress}); a
 * mutated zombie it falls on has its mutation put down at once (the user: "走るゾンビにスプラッシュ化した抑制ポーションを
 * つかうと、変異がおさえられて即座にバニラゾンビに変化する") and is an ordinary zombie from then on - slow, burnt by the sun -
 * or, one that was a villager, an ordinary zombie villager, which a golden apple and weakness cure as anywhere.
 */
public class SuppressantEffect extends InstantaneousMobEffect {
    public SuppressantEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x8fd6a6);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        this.applyInstantaneousEffect(level, null, null, mob, amplification, 1.0);
        return true;
    }

    @Override
    public void applyInstantaneousEffect(ServerLevel level, @Nullable Entity source, @Nullable Entity owner, LivingEntity mob, int amplification,
            double scale) {
        if (mob instanceof ServerPlayer player) {
            Infection.suppress(player, amplification > 0 ? Infection.LONG : Infection.FULL);   // the long one's level is 1
        } else if (mob instanceof Mutated && mob instanceof Zombie zombie && zombie.isAlive()) {
            subdue(level, zombie);
            if (owner instanceof ServerPlayer thrower) {
                dev.nookofthedead.ZwAdvancements.award(thrower, "put_down");
            }
        }
    }

    /** {@code zombie}'s mutation put down: an ordinary zombie (or zombie villager) in its place, what it wore kept. */
    public static @Nullable Zombie subdue(ServerLevel level, Zombie zombie) {
        Zombie plain;
        if (zombie instanceof ZombieVillager villager) {
            plain = villager.convertTo(EntityTypes.ZOMBIE_VILLAGER, ConversionParams.single(villager, true, true), z -> {
                z.setVillagerData(villager.getVillagerData());
                z.setVillagerDataFinalized(true);
            });
        } else if (zombie instanceof dev.nookofthedead.entity.MutatedHusk) {
            plain = zombie.convertTo(EntityTypes.HUSK, ConversionParams.single(zombie, true, true), z -> {});    // the desert's: a husk
        } else {
            plain = zombie.convertTo(EntityTypes.ZOMBIE, ConversionParams.single(zombie, true, true), z -> {});
        }
        if (plain != null) {
            level.playSound(null, plain.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, plain.getX(), plain.getY() + 1.0, plain.getZ(), 12, 0.3, 0.6, 0.3, 0.0);
        }
        return plain;
    }
}
