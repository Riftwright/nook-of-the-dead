package dev.nookofthedead.infection;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.ZwAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The plague, as the user set it (2026-10-12): whoever steps into the fallen world is infected at once, and dies of it
 * unless a suppressant ({@link SuppressantEffect}) is drunk about every twenty minutes; in the last five the head swims;
 * back in the overworld it stops where it was. Beds work there as anywhere (the user, 2026-10-15: "普通に寝れて、普通に朝に
 * なる方が自然"; Nookworks counts the If's sleepers with the overworld's).
 * <p>
 * The time left is kept on the player in ticks ({@link #LEFT}, saved; -1 when not infected), counted down a second at a
 * time while in the fallen world and out of creative and spectator. Drinking sets it back to the full twenty minutes - it
 * does not add up, so a pocketful buys no more than one. Dying of anything ends it (the next step in starts it again).
 * <p>
 * Nothing is said of it in the chat or shown as a clock (the user, 2026-10-13: "感染タイマーとか…チャット欄や左上に出て
 * くる必要はないよ。説明的すぎる"): a zombie's groan as it takes hold, then the body's own signs - the head swimming in
 * the last five minutes, legs heavy and the light going in the last one.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Infection {
    public static final int NONE = -1;
    /** Fifteen minutes (the user, 2026-10-19: "抑制ポーションの効果は15分にする"). */
    public static final int FULL = 20 * 60 * 15;
    /** The last three: the head swims (the user, 2026-10-19: "3分前に目眩"). */
    public static final int SYMPTOMS = 20 * 60 * 3;
    /** The last one: legs heavy, the light going. */
    public static final int LAST_MINUTE = 20 * 60;

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NookOfTheDead.MODID);
    public static final Supplier<AttachmentType<Integer>> LEFT = ATTACHMENT_TYPES.register("infection",
            () -> AttachmentType.builder(() -> NONE)
                    .serialize(Codec.INT.fieldOf("ticks"), ticks -> ticks >= 0)
                    .copyOnDeath()                               // kept through a death at home ({@link #onRespawn})
                    .build());

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, NookOfTheDead.id("infection"));

    public static int left(Player player) {
        return player.getData(LEFT);
    }

    public static boolean infected(Player player) {
        return left(player) >= 0;
    }

    /** Infected now, the full time to run, unless already (back in, it runs on from where it stopped). */
    public static void infect(ServerPlayer player) {
        if (infected(player)) {
            return;
        }
        player.setData(LEFT, FULL);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_INFECT, SoundSource.PLAYERS, 0.8F, 0.8F);
        ZwAdvancements.award(player, "root");
    }

    /** A dose redstone lengthened: thirty minutes. */
    public static final int LONG = 20 * 60 * 30;

    /** A suppressant drunk: fifteen minutes again, the swimming head gone - and if it swam, felt clearing. */
    public static void suppress(ServerPlayer player) {
        suppress(player, FULL);
    }

    /** The same, holding it back {@code ticks} (a long dose: {@link #LONG}). */
    public static void suppress(ServerPlayer player, int ticks) {
        if (!infected(player)) {
            return;              // nothing to hold back (the review, 2026-10-19: one drunk at home made a player "infected" there)
        }
        boolean ill = infected(player) && left(player) <= SYMPTOMS;
        ZwAdvancements.award(player, "first_dose");
        if (infected(player) && left(player) <= LAST_MINUTE) {
            ZwAdvancements.award(player, "close_call");
        }
        player.setData(LEFT, ticks);
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.SLOWNESS);
        if (ill) {
            player.sendOverlayMessage(Component.translatable("message.nookofthedead.suppressed").withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * How much sooner a bite brings the end on Normal: five seconds a bite that drew blood, never past the last second
     * (twenty was too many: a crowd's bites ran out a fresh dose inside a minute even against good armour). Two on Easy,
     * twelve on Hard ({@link dev.nookofthedead.entity.Hardness#bite}).
     */
    public static final int BITE = 5 * 20;

    /**
     * Bitten by one of them (the films' bite: every one counts): a little less time left, if infected. Nothing says so; the
     * symptoms come that much sooner.
     */
    public static void bitten(ServerPlayer player) {
        // in the refuge it stands still, bites and all (the review, 2026-10-19)
        if (infected(player) && !player.isCreative() && !dev.nookofthedead.world.Refuge.inside(player.level(), player.blockPosition())) {
            int bite = dev.nookofthedead.entity.Hardness.of(player.level()).bite();
            player.setData(LEFT, Math.min(left(player), Math.max(20, left(player) - bite)));
        }
    }

    /** Over, as at death. */
    public static void clear(Player player) {
        player.setData(LEFT, NONE);
    }

    /** One second of the plague for {@code player}: counted only in the fallen world, and not in creative or spectator. */
    public static void second(ServerPlayer player) {
        if (!NookOfTheDead.fallen(player.level()) || player.isCreative() || player.isSpectator() || !player.isAlive()
                || dev.nookofthedead.ending.Film.watching(player)) {
            return;                                                     // nor while they watch a film (0.3)
        }
        if (dev.nookofthedead.ending.Cure.cured(player.level())) {
            if (infected(player)) {
                clear(player);                                          // the If cured: the plague in them too
            }
            return;
        }
        if (!infected(player)) {
            infect(player);
            return;
        }
        if (dev.nookofthedead.world.Refuge.inside(player.level(), player.blockPosition())) {
            return;                         // in the institute's refuge it stands still (the user, 2026-10-19), told by nothing
        }
        int left = Math.max(0, left(player) - 20);
        player.setData(LEFT, left);
        symptoms(player, left);
        if (left == 0) {
            ServerLevel level = player.level();
            player.hurtServer(level, player.damageSources().source(DAMAGE), Float.MAX_VALUE);
        }
    }

    private static void symptoms(ServerPlayer player, int left) {
        if (left > SYMPTOMS || left == 0) {
            return;
        }
        if (left % (20 * 30) == 0) { // every half minute, the world tilts
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 9, 0, false, false, true));
            player.sendOverlayMessage(Component.translatable(left > LAST_MINUTE ? "message.nookofthedead.dizzy" : "message.nookofthedead.dying")
                    .withStyle(left > LAST_MINUTE ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
        if (left <= LAST_MINUTE) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0, false, false, true));
            if (left % (20 * 10) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 4, 0, false, false, true));
                player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.PLAYERS, 0.4F, 0.6F);
            }
        }
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            second(player);
        }
    }

    @SubscribeEvent
    static void onEnter(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && NookOfTheDead.fallen(player.level())
                && !player.isCreative() && !player.isSpectator() && !dev.nookofthedead.ending.Cure.cured(player.level())) {
            infect(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // over only with a death over there: one at home leaves it as it stood, to go on when they go back (Nookworks'
        // review, 2026-10-19: dying at home, the gear put by, gave a fresh fifteen minutes)
        if (!event.isEndConquered() && endsWith(event.getEntity().getLastDeathLocation())) {
            clear(event.getEntity());
        }
    }

    /** Whether a death at {@code at} ends it: one in the If (or one nobody knows the place of). */
    public static boolean endsWith(java.util.Optional<net.minecraft.core.GlobalPos> at) {
        return at.map(where -> where.dimension().equals(dev.nookworks.ifworld.IfWorld.KEY)).orElse(true);
    }


    private Infection() {}
}
