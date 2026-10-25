package dev.nookofthedead.ending;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Ascent;
import dev.nookofthedead.world.SkyBase;
import dev.nookofthedead.world.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The road's short films (the user, 2026-10-24: "全部"): a few seconds each, at its turns, telling where to go without a
 * word ({@link Film.Kind#VANDAL}, {@link Film.Kind#ASCENT}, {@link Film.Kind#BASE}):
 * <ul>
 * <li>The Vandal down: the camera goes to the refuge's door as its lamps come on and the plug draws back, and comes
 *     back - for every player near.</li>
 * <li>Come to the second institute: out to the yard, looking up the Tower from its foot to the base in the clouds.</li>
 * <li>Come up into the sky base: out over the drop, the laboratory shut off at the far end, and the way round over the
 *     roofs.</li>
 * </ul>
 * The arrivals once for each player ({@link #SEEN}, kept through death); none in creative or spectator. Esc skips them as
 * it does the others.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Cuts {
    /** Which of the arrivals a player has seen: a bit for each ({@link #INSTITUTE}, {@link #BASE}). */
    public static final Supplier<AttachmentType<Integer>> SEEN = dev.nookofthedead.infection.Infection.ATTACHMENT_TYPES.register("cuts_seen",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("seen"), seen -> seen != 0).copyOnDeath().build());
    static final int INSTITUTE = 1;
    static final int BASE = 2;
    /** How near the Vandal's fall a player is shown its door. */
    public static final double VANDAL_NEAR = 64.0;

    /** Where a player's eyes are and which way they look, as a film's anchors begin. */
    static List<Vec3> eyes(ServerPlayer player) {
        return List.of(player.getEyePosition(), new Vec3(player.getYRot(), player.getXRot(), 0));
    }

    static boolean playing(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !Film.watching(player);
    }

    /** The Vandal down by its door's {@code door} (the opening's middle) seen from {@code view}: shown to those near. */
    public static void vandal(ServerLevel level, Vec3 at, Vec3 door, Vec3 view) {
        for (ServerPlayer player : level.players()) {
            if (playing(player) && player.position().distanceToSqr(at) < VANDAL_NEAR * VANDAL_NEAR) {
                List<Vec3> e = eyes(player);
                Film.start(player, Film.Kind.VANDAL, List.of(e.get(0), e.get(1), door, view));
            }
        }
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !NookOfTheDead.fallen(player.level())
                || !playing(player) || Cure.cured(player.level())) {
            return;
        }
        ServerLevel level = player.level();
        int seen = player.getData(SEEN);
        Ascent.Site site = Ascent.site(level.getSeed());
        BlockPos c = Tower.centre(site);
        if ((seen & INSTITUTE) == 0 && player.blockPosition().distSqr(site.arrival()) < 12 * 12) {
            player.setData(SEEN, seen | INSTITUTE);
            ascent(player);
        } else if ((seen & BASE) == 0 && player.getY() >= Tower.BASE - 2 && player.getY() < Tower.BASE + 12
                && Math.abs(player.getX() - c.getX()) < 26 && Math.abs(player.getZ() - c.getZ()) < 26) {
            player.setData(SEEN, seen | BASE);
            base(player);
        }
    }

    /** The second institute's arrival, shown to {@code player} now (also /nzw film ascent). */
    public static void ascent(ServerPlayer player) {
        Ascent.Site site = Ascent.site(player.level().getSeed());
        BlockPos c = Tower.centre(site);
        List<Vec3> e = eyes(player);
        Vec3 foot = new Vec3(c.getX() + 0.5, site.ground() + 6, c.getZ() + 0.5);
        Vec3 top = new Vec3(c.getX() + 0.5, Tower.BASE + 12, c.getZ() + 0.5);
        Vec3 shot = new Vec3(c.getX() + 30.5, site.ground() + 8, c.getZ() + 34.5);          // out in the yard, off a corner
        Film.start(player, Film.Kind.ASCENT, List.of(e.get(0), e.get(1), foot, top, shot));
    }

    /** The sky base's arrival, shown to {@code player} now (also /nzw film base). */
    public static void base(ServerPlayer player) {
        Ascent.Site site = Ascent.site(player.level().getSeed());
        BlockPos c = Tower.centre(site);
        List<Vec3> e = eyes(player);
        Vec3 lab = Vec3.atBottomCenterOf(SkyBase.vaccine(site)).add(0, 2, 0);
        Vec3 landing = new Vec3(c.getX() + 17.5, Tower.BASE + 8, c.getZ() - 6.5);
        Vec3 shot = new Vec3(c.getX() + 38.5, Tower.BASE + 20, c.getZ() - 34.5);          // out over the drop, north-east
        Film.start(player, Film.Kind.BASE, List.of(e.get(0), e.get(1), lab, landing, shot));
    }

    private Cuts() {}
}
