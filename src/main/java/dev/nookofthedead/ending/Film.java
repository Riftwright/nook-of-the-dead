package dev.nookofthedead.ending;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A film, as the server keeps it for whoever watches (PLAN_0.3.md, "The films"): while it plays they are held where the
 * film needs them - out of the way, unseen, unhurt, nothing to fall on - and moved when it needs them elsewhere (the
 * client is shown only what is loaded round the one watching); the camera is the client's ({@code client/FilmCamera}).
 * When it ends - or is skipped, Esc - they are put back where they were, as they were.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Film {
    /** The films, and how long each runs (ticks). */
    public enum Kind {
        ENDING(1480),
        OPENING(600),
        /** The road's short ones ({@link Cuts}). */
        VANDAL(90),
        ASCENT(130),
        BASE(130);

        public final int length;

        Kind(int length) {
            this.length = length;
        }
    }

    /** One watching: what, how far in, where they are held, and how they were. */
    public static final class Session {
        public final Kind kind;
        public final List<Vec3> anchors;
        final Vec3 origin;
        final float yRot;
        final float xRot;
        final boolean invulnerable;
        final boolean invisible;
        final boolean noGravity;
        public int tick;
        public Vec3 hold;
        /** Night Vision given for the film's own sake (the opening at night), to be taken back with it. */
        boolean nightVision;

        Session(Kind kind, List<Vec3> anchors, ServerPlayer player) {
            this.kind = kind;
            this.anchors = anchors;
            this.origin = player.position();
            this.yRot = player.getYRot();
            this.xRot = player.getXRot();
            this.invulnerable = player.getAbilities().invulnerable;
            this.invisible = player.isInvisible();
            this.noGravity = player.isNoGravity();
            this.hold = player.position();
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /** Plays {@code kind} for {@code player}, about {@code anchors} (its places, as the film reads them). */
    public static void start(ServerPlayer player, Kind kind, List<Vec3> anchors) {
        if (!player.isAlive()) {
            return;                                         // none for the dead (the review, 2026-10-19)
        }
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null) {                                   // one film over another: put back as before the first, then begun
            restore(player, old);
        }
        Session session = new Session(kind, anchors, player);
        SESSIONS.put(player.getUUID(), session);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        player.setInvisible(kind == Kind.ENDING);           // the opening shows them, from behind
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        unseen(player);
        FilmPayloads.send(player, new FilmPayloads.Play(kind.ordinal(), anchors));
    }

    /**
     * Whatever had {@code player} in its sights lets them go: none of the dead notices one watching a film (the user,
     * 2026-10-21: the dead already about were not held back by the opening - only new ones were). {@link #onTarget}
     * keeps it so while it plays.
     */
    static void unseen(ServerPlayer player) {
        for (var mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(128),
                m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    static void onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player player && watching(player)) {
            event.setCanceled(true);
        }
    }

    /** Night Vision for {@code player} while the film wants it (not over one of their own). */
    static void nightVision(ServerPlayer player, boolean on) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (on && !player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                    session.kind.length + 40, 0, true, false, false));
            session.nightVision = true;
        } else if (!on && session.nightVision) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            session.nightVision = false;
        }
    }

    /** Whether {@code player} is watching a film now. */
    public static boolean watching(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static Session session(Player player) {
        return SESSIONS.get(player.getUUID());
    }

    /** Esc: the film ends here, what it was to do done at once ({@code skipped}). */
    static void skip(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            end(player);
            if (session.kind == Kind.ENDING && !anyWatching(Kind.ENDING)) {
                Ending.skipped(player.level());             // the yard's dead, once no one watches any more
            }
        }
    }

    /** The film over: put back, and the client told. */
    public static void end(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            restore(player, session);
            FilmPayloads.send(player, new FilmPayloads.Play(-1, List.of()));
            if (session.kind == Kind.OPENING) {
                Opening.seen(player);
            }
        }
    }

    private static void restore(ServerPlayer player, Session session) {
        release(player, session);
        player.teleportTo(player.level(), session.origin.x, session.origin.y, session.origin.z, java.util.Set.<Relative>of(), session.yRot,
                session.xRot, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /**
     * What the film changed on {@code player}, put back as it is now owed: invulnerable as their game mode makes them,
     * unseen only if a potion makes them so (both may have changed meanwhile - the review, 2026-10-19), gravity as before.
     */
    private static void release(ServerPlayer player, Session session) {
        if (session.nightVision) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        }
        player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
        player.onUpdateAbilities();
        player.setInvisible(player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY));
        player.setNoGravity(session.noGravity);
    }

    /** Moves {@code player} to be held at {@code at} (the ground there made first). */
    public static void holdAt(ServerPlayer player, Vec3 at) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.hold = at;
        ServerLevel level = player.level();
        level.getChunk(net.minecraft.core.BlockPos.containing(at));
        player.teleportTo(level, at.x, at.y, at.z, java.util.Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.tick++;
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        if (player.position().distanceToSqr(session.hold) > 0.25) {
            player.teleportTo(player.level(), session.hold.x, session.hold.y, session.hold.z, java.util.Set.<Relative>of(), player.getYRot(),
                    player.getXRot(), true);
        }
        switch (session.kind) {
            case ENDING -> Ending.at(player, session);
            case OPENING -> Opening.at(player, session);
            default -> {
            }
        }
        if (session.tick >= session.kind.length) {
            end(player);
        }
    }

    /** Dead while watching (the film is no shield from /kill or the void): it ends, put back where they are. */
    @SubscribeEvent
    static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            Session session = SESSIONS.remove(player.getUUID());
            release(player, session);
            FilmPayloads.send(player, new FilmPayloads.Play(-1, List.of()));
            over(player, session);
        }
    }

    /**
     * A film left another way than Esc - dead, gone, through a portal: the ending's doings done all the same once the last
     * one watching it is out of it (the review, 2026-10-21: the town's staged dead stood on, the vessel stayed full).
     */
    private static void over(ServerPlayer player, Session session) {
        if (session.kind == Kind.ENDING && !anyWatching(Kind.ENDING) && player.level().getServer() != null) {
            ServerLevel there = player.level().getServer().getLevel(dev.nookworks.ifworld.IfWorld.KEY);
            if (there != null) {
                Ending.skipped(there);
            }
        }
    }

    /** The server stopping: everyone watching put back before they are saved (else saved floating, far off). */
    @SubscribeEvent
    static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            Session session = SESSIONS.remove(player.getUUID());
            if (session != null) {
                restore(player, session);
            }
        }
        SESSIONS.clear();
        Ending.forget(event.getServer());
    }

    /** Whether anyone is watching {@code kind} now. */
    static boolean anyWatching(Kind kind) {
        return SESSIONS.values().stream().anyMatch(s -> s.kind == kind);
    }

    @SubscribeEvent
    static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Session session = SESSIONS.remove(player.getUUID());
            if (session != null) {
                restore(player, session);
                over(player, session);
            }
        }
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
            Session session = SESSIONS.remove(player.getUUID());
            release(player, session);
            FilmPayloads.send(player, new FilmPayloads.Play(-1, List.of()));
            over(player, session);
        }
    }

    private Film() {}
}
