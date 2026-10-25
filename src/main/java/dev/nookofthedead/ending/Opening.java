package dev.nookofthedead.ending;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The first time into the If (the user, 2026-10-19): a film - the monologue over slow views of the fallen town ("ここは
 * イフ / 誰もが望む、もしもの世界。/ 鏡の魔法によって、もう一つの、手付かずのオーバーワールドが広がっている…はずだった"), then
 * a look round the one who came, from behind them, that eases into their own eyes, and play begins. Until it has, the dead
 * are not let out near them ({@link #holding}). Once per player ({@link #SEEN}, kept through death); never in a cured If,
 * nor in creative or spectator.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Opening {
    public static final Supplier<AttachmentType<Boolean>> SEEN = dev.nookofthedead.infection.Infection.ATTACHMENT_TYPES.register("opening_seen",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("seen"), seen -> seen).copyOnDeath().build());

    /** How near to one watching the dead are held back. */
    public static final double HOLDING = 96.0;

    static void seen(ServerPlayer player) {
        player.setData(SEEN, true);
    }

    /** Whether the dead should be held back at a spot within {@link #HOLDING} of {@code player}. */
    public static boolean holding(Player player) {
        return Film.watching(player);
    }

    /** Plays it for {@code player}, if it is their first time. */
    public static void maybe(ServerPlayer player) {
        if (!player.isAlive() || player.getData(SEEN) || player.isCreative() || player.isSpectator() || !NookOfTheDead.fallen(player.level())
                || Cure.cured(player.level()) || Film.watching(player)) {
            return;
        }
        play(player);
    }

    /** Plays it for {@code player} now, seen or not. */
    public static void play(ServerPlayer player) {
        Vec3 at = player.position();
        List<Vec3> anchors = new java.util.ArrayList<>(List.of(
                at,                                               // 0: where they came in
                new Vec3(player.getYRot(), player.getXRot(), 0))); // 1: which way they faced (yaw in x, pitch in y)
        Vec3 camp = camp(player.level(), at, CAMP_REACH);
        if (camp != null) {
            anchors.add(camp);                                    // 2: a last holdout's roof near, if there is one
        }
        Film.start(player, Film.Kind.OPENING, anchors);
        if (player.level().isDarkOutside()) {
            // at night the town it looks round is lost in the dark: Night Vision for those two views, and taken back under
            // the black before "...はずだった" (the user, 2026-10-21: "夜の場合は、建物見回すときだけ暗視")
            Film.nightVision(player, true);
        }
    }

    /**
     * The nearest roof with a last holdout's camp on it within {@code reach} of {@code at} (the user, 2026-10-24: the
     * opening to show the town's traces): its white rag on its pole, the first thing the camera passes; null if none.
     */
    static Vec3 camp(net.minecraft.server.level.ServerLevel level, Vec3 at, double reach) {
        int bx0 = Math.floorDiv((int) at.x, dev.nookofthedead.world.Town.BLOCK);
        int bz0 = Math.floorDiv((int) at.z, dev.nookofthedead.world.Town.BLOCK);
        Vec3 best = null;
        for (int bx = bx0 - 1; bx <= bx0 + 1; bx++) {
            for (int bz = bz0 - 1; bz <= bz0 + 1; bz++) {
                for (var lot : dev.nookofthedead.world.Town.lots(level.getSeed(), bx, bz)) {
                    var where = dev.nookofthedead.world.Rooftops.standing(level, lot);
                    if (where != null) {
                        Vec3 v = Vec3.atBottomCenterOf(where);
                        double across = Math.hypot(v.x - at.x, v.z - at.z);
                        // not one they stand at (the camera would have nowhere to come from)
                        if (across > NEAREST && v.distanceTo(at) < reach && (best == null || v.distanceTo(at) < best.distanceTo(at))) {
                            best = v;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** How near a roof's camp must be to come into the opening (and, from the town's middle, the ending). */
    static final double CAMP_REACH = 90;
    static final double NEAREST = 16;

    /** The tick, under the black card, the town's views are over by: Night Vision taken back. */
    public static final int VIEWS_OVER = 362;

    static void at(ServerPlayer player, Film.Session session) {
        // the camera is the client's; nothing happens here meanwhile but the holding, and the night's sight taken back
        if (session.tick == VIEWS_OVER) {
            Film.nightVision(player, false);
        }
    }

    /** No natural spawn of the plague's dead near one watching the opening (the user: "この時点でゾンビがわき始める"). */
    @SubscribeEvent
    static void onSpawnCheck(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck event) {
        if (event.getEntity() instanceof dev.nookofthedead.entity.Mutated && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && NookOfTheDead.fallen(level)) {
            for (var player : level.players()) {
                if (holding(player) && player.distanceToSqr(event.getEntity()) < HOLDING * HOLDING) {
                    event.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    static void onEnter(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.level().getServer().execute(() -> maybe(player));     // once they have arrived
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            maybe(player);
        }
    }

    private Opening() {}
}
