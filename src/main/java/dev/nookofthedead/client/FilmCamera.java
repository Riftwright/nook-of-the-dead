package dev.nookofthedead.client;

import java.util.ArrayList;
import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.ending.Cure;
import dev.nookofthedead.ending.Ending;
import dev.nookofthedead.ending.Film;
import dev.nookofthedead.ending.FilmPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The films as the client shows them ({@link Film}; PLAN_0.3.md "The films"): the camera taken from the player and moved
 * through the film's shots about its anchors, the screen bars top and bottom, the words over it, fades between; the hand
 * and the screen's furniture hidden; Esc skips. Each shot is a function of the film's time, so it plays the same however
 * the frames fall. {@code CameraMixin} sets the camera from {@link #view}.
 * <ul>
 * <li>The ending: the mist bursting in the laboratory; pouring off the sky base, seen from out in the air; coming down over
 *     the second institute's yard where the dead burn; the fog lifting as the camera rises over a clear world; the
 *     credits over a slow pan.</li>
 * <li>The opening: the monologue over two slow views of the town about the one who came; then a look round them from
 *     behind that closes in and eases into their own eyes.</li>
 * </ul>
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class FilmCamera {
    private static Film.Kind kind;
    private static List<Vec3> anchors = List.of();
    private static int ticks;

    /**
     * Where the camera is and looks, whether the player is seen, what is over the screen, how clear the air is, the
     * camera's roll, and how far its field of view has come back to the player's own (0 the film's 60, 1 theirs).
     */
    public record View(Vec3 pos, float yaw, float pitch, boolean detached, float black, float white, float bars, List<Line> lines, float fogClear,
            float roll, float ownFov) {}

    /** After a film, its last black lifting and its bars drawing back, over this many ticks (counting down). */
    private static int after;
    private static Film.Kind afterKind;

    /** A line of words over the film, its brightness (0 to 1), and how it stands: 0 a caption in the lower bar, 1 a credit, 2 a title. */
    public record Line(Component text, float alpha, int size) {}

    public static boolean playing() {
        return kind != null && Minecraft.getInstance().player != null;
    }

    static void play(FilmPayloads.Play payload) {
        var mc = Minecraft.getInstance();
        if (payload.film() < 0) {
            leave();
            return;
        }
        kind = Film.Kind.values()[payload.film()];
        anchors = payload.anchors();
        ticks = 0;
        EASED.reset();
        if (kind == Film.Kind.OPENING) {
            mc.getMusicManager().stopPlaying();                  // silence carries the words
        }
    }

    /**
     * Out of the film, as play's own: the hand raised, the black lifting and the bars drawing back - whether the server
     * ended it or Esc did (the user, 2026-10-21: skipped, it cut straight to play).
     */
    private static void leave() {
        var mc = Minecraft.getInstance();
        if (kind != null) {
            afterKind = kind;
            // the road's short ones end with their bars already gone: none to draw back, unless skipped
            boolean shortOne = kind != Film.Kind.ENDING && kind != Film.Kind.OPENING;
            after = kind == Film.Kind.ENDING ? AFTER_ENDING : shortOne && ticks >= kind.length - 14 ? 0 : AFTER;
            if (mc.player != null) {
                mc.player.itemUsed(net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.player.itemUsed(net.minecraft.world.InteractionHand.OFF_HAND);
            }
        }
        kind = null;
    }

    /**
     * How long the black takes to lift after a film; after the ending, black first while the ground about where they
     * stood comes back in (they were held far off for its last shots).
     */
    private static final int AFTER = 20;
    private static final int AFTER_ENDING = 40;

    /**
     * Development only: a camera put in from outside the game's films (client/dev/CameraPath's "third": a shot of the
     * player from apart), or null. Never set in play.
     */
    public static java.util.function.Function<Float, View> devView;

    /** The film's view now, or null when none plays. */
    public static View view(float partial) {
        if (devView != null && Minecraft.getInstance().player != null) {
            return devView.apply(partial);
        }
        if (!playing()) {
            return null;
        }
        float t = ticks + partial;
        return switch (kind) {
            case ENDING -> ending(t);
            case OPENING -> opening(t);
            case VANDAL -> vandal(t);
            case ASCENT, BASE -> arrival(t);
        };
    }

    // --- the road's short ones (Cuts) ---------------------------------------------------------------------------------

    /**
     * The Vandal down: from their eyes out across the hall to the refuge's door as it opens, held there, and back - no
     * black, the field of view their own.
     */
    private static View vandal(float t) {
        Vec3 eye = anchors.get(0);
        float yaw = (float) anchors.get(1).x;
        float pitch = (float) anchors.get(1).y;
        Vec3 door = anchors.get(2);
        Vec3 view = anchors.get(3);
        Vec3 ahead = eye.add(lookVector(yaw, pitch).scale(10));
        List<Line> lines = new ArrayList<>();
        float bars = smooth(t / 12) * (1 - smooth((t - 76) / 14));
        if (t < 30) {
            float k = easeSine(t / 30);
            return own(look(eye.lerp(view, k), ahead.lerp(door, k), true, 0, 0, bars, lines, 0));
        }
        if (t < 62) {
            float k = (t - 30) / 32;
            return own(look(view.lerp(door, 0.12 * k), door, true, 0, 0, bars, lines, 0));
        }
        float k = easeSine(Math.min(1, (t - 62) / 26));
        Vec3 pos = view.lerp(door, 0.12).lerp(eye, k);
        View v = look(pos, door.lerp(ahead, k), k < 0.9F, 0, 0, bars, lines, 0);
        return new View(pos, Mth.rotLerp(smooth((t - 76) / 12), v.yaw(), yaw), Mth.lerp(smooth((t - 76) / 12), v.pitch(), pitch),
                pos.distanceTo(eye) > 0.35, 0, 0, bars, lines, 0, 0, 1);
    }

    /**
     * Come to the second institute, or up into the sky base: a black cut out of their eyes, one slow shot - up the Tower
     * from its foot to the clouds, or from out over the drop across the base to the way round - and a black cut back.
     */
    private static View arrival(float t) {
        Vec3 eye = anchors.get(0);
        float yaw = (float) anchors.get(1).x;
        float pitch = (float) anchors.get(1).y;
        Vec3 from = anchors.get(2);
        Vec3 to = anchors.get(3);
        Vec3 shot = anchors.get(4);
        int length = kind.length;
        List<Line> lines = new ArrayList<>();
        float black = cut(t, 0, 10, 20) + cut(t, length - 22, length - 12, length - 2);
        float bars = t < 10 ? smooth(t / 10) : t > length - 12 ? 1 - smooth((t - (length - 12)) / 10) : 1;
        if (t < 10 || t >= length - 12) {
            return new View(eye, yaw, pitch, false, Math.min(1, black), 0, bars, lines, 0, 0, 1);
        }
        float k = easeSine((t - 10) / (length - 22));
        boolean up = kind == Film.Kind.ASCENT;
        Vec3 pos = up ? clear(shot, 4).add(0, 6 * k, 0) : shot.add(-8 * k, 3 * k, 8 * k);
        return own(look(pos, from.lerp(to, k), true, Math.min(1, black), 0, bars, lines, 0));
    }

    /** A view kept at the player's own field of view. */
    private static View own(View v) {
        return new View(v.pos(), v.yaw(), v.pitch(), v.detached(), v.black(), v.white(), v.bars(), v.lines(), v.fogClear(), v.roll(), 1);
    }

    /** The way {@code yaw} and {@code pitch} look. */
    private static Vec3 lookVector(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    // --- the ending -------------------------------------------------------------------------------------------------
    // (the visual review's timeline, 2026-10-19, and the user's of 2026-10-21: the console's alarm; the burst pulled back
    // from; the orbit falling away with the mist; carried off white over the world, the first of the monologue over it;
    // down over the town by the If's mirror, where the dead burn and fall; a white-out; the fog lifting at dawn as the
    // camera rises over it, the monologue going on; the credits panning on from there, no jump)

    private static View ending(float t) {
        Vec3 vaccine = anchors.get(0);
        Vec3 base = anchors.get(1);
        Vec3 town = anchors.get(2);
        Vec3 from = anchors.get(3);
        Vec3 to = anchors.get(4);
        List<Line> lines = new ArrayList<>();
        float black = fadeIn(t, 1440, 1475);
        float white = t < Ending.BURST ? 0 : t < Ending.BURST + 8 ? fadeIn(t, Ending.BURST + 2, Ending.BURST + 8)
                : t < Ending.DIVE ? 1 - fadeIn(t, Ending.BURST + 8, Ending.BURST + 45)
                : t < Ending.TOWN ? fadeIn(t, Ending.DIVE + 15, Ending.DIVE + 45)
                : t < Ending.WHITE ? 1 - fadeIn(t, Ending.TOWN, Ending.TOWN + 30)
                : t < Ending.RISE ? fadeIn(t, Ending.WHITE, Ending.WHITE + 20) : 1 - fadeIn(t, Ending.RISE, Ending.RISE + 40);
        line(lines, t, 330, 445, "film.nookofthedead.ending.1");
        line(lines, t, 470, 585, "film.nookofthedead.ending.2");
        line(lines, t, 660, 790, "film.nookofthedead.ending.3");
        line(lines, t, 810, 920, "film.nookofthedead.ending.4");
        line(lines, t, 945, 1075, "film.nookofthedead.ending.5");
        credit(lines, t, 1110, 1205, "film.nookofthedead.ending.title", 2);
        credit(lines, t, 1215, 1295, "film.nookofthedead.ending.author", 1);
        credit(lines, t, 1305, 1385, "film.nookofthedead.ending.nookworks", 1);
        credit(lines, t, 1395, 1462, "film.nookofthedead.ending.thanks", 1);
        if (t < Ending.BURST) {                                  // the console's alarm: close on it, easing in
            float k = easeSine(t / Ending.BURST);
            return look(vaccine.add(0, 1.7, 2.8 - 0.6 * k), vaccine.add(0, 1.0, -0.6), false, black, white, 1, lines, 0);
        }
        if (t < Ending.OUT) {                                    // the laboratory: the burst, pulled back from
            float u = (t - Ending.BURST) / (Ending.OUT - Ending.BURST);
            float e = 1 - (1 - u) * (1 - u);
            // from the console out to the whole of it, the vessel behind running down (the console a step out from the
            // glass since 2026-10-20: the pull back a step shorter, to end where it did)
            Vec3 pos = vaccine.add(0, 1.6 + 1.2 * e, 2.2 + 5.3 * e);
            float since = t - Ending.BURST;
            float shake = since < 60 ? (float) (2.5 * Math.sin(since * 1.7) * Math.exp(-since / 18)) : 0;
            View v = look(pos, vaccine.add(0, 1.0 + 1.6 * e, -0.6), false, black, white, 1, lines, 0);
            return new View(v.pos(), v.yaw(), v.pitch() + shake / 4, false, black, white, 1, lines, 0, shake, 0);
        }
        Vec3 orbitEnd = base.add(Math.cos(1.8) * 46, -6, Math.sin(1.8) * 46);
        if (t < Ending.DIVE) {                                   // out in the air: the mist pours off the base; it falls away
            float k = (t - Ending.OUT) / (Ending.DIVE - Ending.OUT);
            double a = 0.6 + k * 1.2;
            Vec3 pos = base.add(Math.cos(a) * 46, 14 - 20 * easeSine(k), Math.sin(a) * 46);
            float bank = -2.0F * Math.min(1, Math.min(k * 7.5F, (1 - k) * 7.5F));
            View v = look(pos, base.add(0, 2, 0), false, black, white, 1, lines, 0);
            return new View(v.pos(), v.yaw(), v.pitch(), false, black, white, 1, lines, 0, bank, 0);
        }
        if (t < Ending.TO_TOWN) {                                // down into the mist after it, the white filling the screen
            float k = easeSine((t - Ending.DIVE) / (Ending.TO_TOWN - Ending.DIVE));
            Vec3 pos = orbitEnd.add(0, -30 * k, 0);
            return look(pos, base.add(0, 2 - 40 * k, 0), false, black, white, 1, lines, 0);
        }
        Vec3 middle = town.add(0, 1.2, 0);
        if (t < Ending.RISE) {                                   // the town: the mist sinks over it, the dead burn and fall
            float k = easeSine((t - Ending.TOWN) / (Ending.WHITE - Ending.TOWN));
            return look(from.lerp(to, k), middle, false, black, white, 1, lines, 0);
        }
        Vec3 out = to.subtract(town).multiply(1, 0, 1).normalize();
        Vec3 high = town.add(out.scale(28)).add(0, 60, 0);
        Vec3 sunrise = town.add(120, 0, 0);                      // east, where the morning comes from: over the town to it
        if (t < 860) {                                           // the fog lifts as the camera rises, at dawn
            float k = easeSine((t - Ending.RISE) / (860 - Ending.RISE));
            float clear = smoother((t - Ending.RISE - 10) / 170);
            return look(to.lerp(high, k), middle.lerp(sunrise, k), false, black, white, 1, lines, clear);
        }
        // the credits, going back over what the town left: the well they went down, a roof they held (the user,
        // 2026-10-24), each come to through a dip to black; with neither near, the pan below goes on to the end
        List<Vec3> left = new ArrayList<>();
        for (int i = 5; i < anchors.size(); i++) {
            if (anchors.get(i).y > Ending.NONE.y + 1) {
                left.add(anchors.get(i));
            }
        }
        if (!left.isEmpty() && t >= RECAP - DIP) {
            float each = (Film.Kind.ENDING.length - RECAP) / (float) left.size();
            int i = Math.min(left.size() - 1, (int) Math.max(0, (t - RECAP) / each));
            float cut = RECAP + i * each;
            float dip = t < RECAP ? fadeIn(t, RECAP - DIP, RECAP) : 1 - fadeIn(t, cut, cut + DIP);
            if (i + 1 < left.size()) {
                dip = Math.max(dip, fadeIn(t, cut + each - DIP, cut + each));
            }
            if (t >= RECAP) {
                return recap(left.get(i), i == 0 && anchors.get(5).y > Ending.NONE.y + 1, town, Math.min(1, (t - cut) / each),
                        Math.max(black, dip), lines);
            }
            black = Math.max(black, dip);
        }
        // the monologue's end and the credits: panning on from there, slowly round toward the north
        float k = easeSine(Math.min(1, (t - 860) / 620));
        View v = look(high.add(0, 0.02 * (t - 860), 0), sunrise, false, black, white, 1, lines, 1);
        return new View(v.pos(), v.yaw() + 35 * k, v.pitch() - 4 * k, false, black, white, 1, lines, 1, 0, 0);
    }

    /** When the credits' going back over the town begins, and how long a dip to black between its shots. */
    private static final float RECAP = 1112;
    private static final float DIP = 12;

    /**
     * One of the credits' shots, {@code k} through it: round a shelter's well ({@code well}), low, looking down it; or past
     * a holdout's roof, its white rag. From the town's side of it, so the streets lie behind.
     */
    private static View recap(Vec3 at, boolean well, Vec3 town, float k, float black, List<Line> lines) {
        Vec3 target = at.add(0, well ? -0.5 : 1.2, 0);
        // raised the same all through the shot, enough to see it from its start, middle and end (no jumps)
        double lift = 0;
        for (float e = 0; e <= 1; e += 0.5F) {
            Vec3 p = recapAt(at, well, town, e);
            lift = Math.max(lift, sighted(clear(p, 2), target).y - p.y);
        }
        return look(recapAt(at, well, town, easeSine(k)).add(0, lift, 0), target, false, black, 0, 1, lines, 1);
    }

    private static Vec3 recapAt(Vec3 at, boolean well, Vec3 town, float e) {
        Vec3 away = at.subtract(town).multiply(1, 0, 1);
        double a0 = away.lengthSqr() < 1 ? 0 : Math.atan2(away.z, away.x) + Math.PI;
        if (well) {
            double a = a0 - 0.5 + 0.7 * e;
            return at.add(Math.cos(a) * 8, 4.5 - 1.0 * e, Math.sin(a) * 8);
        }
        double a = a0 - 0.6 + 0.9 * e;
        return at.add(Math.cos(a) * 13, 7 - 2.0 * e, Math.sin(a) * 13);
    }

    private static void credit(List<Line> lines, float t, float from, float to, String key, int size) {
        float a = fadeIn(t, from, from + 15) * (1 - fadeIn(t, to - 15, to));
        if (t >= from && t < to && a > 0.01F) {
            lines.add(new Line(Component.translatable(key), a, size));
        }
    }

    // --- the opening ------------------------------------------------------------------------------------------------
    // (the visual review's timeline: a crane down the way the player faces; a low reverse shot of them small among the
    // ruins; a black card for "...はずだった"; round to their right shoulder and into their eyes, the pitch, the field of
    // view and the hand eased to play's own)

    private static View opening(float t) {
        Vec3 at = anchors.get(0);
        float yaw = (float) anchors.get(1).x;
        float pitch = (float) anchors.get(1).y;
        Vec3 f = facing(yaw);
        Vec3 rgt = new Vec3(-f.z, 0, f.x);                      // their right
        List<Line> lines = new ArrayList<>();
        line(lines, t, 40, 120, "film.nookofthedead.opening.1");
        line(lines, t, 125, 192, "film.nookofthedead.opening.2");
        line(lines, t, 212, 335, "film.nookofthedead.opening.3");
        credit(lines, t, 365, 415, "film.nookofthedead.opening.4", 1);
        float black = (1 - fadeIn(t, 30, 60)) + cut(t, 192, 202, 212) + fadeIn(t, 340, 358) * (1 - fadeIn(t, 420, 445));
        // what the ground and the ruins make of each shot (kept over them, raised till they see) is eased, never taken at
        // once: taken frame by frame, it jumped a block at a time as the camera crossed the town's roofs and steps, and
        // again as the ground came in (the user, 2026-10-21: "カクカクするときがある")
        if (t < 202 && anchors.size() > 2) {                     // past a holdout's roof - its white rag - on toward them
            Vec3 camp = anchors.get(2);
            Vec3 away = camp.subtract(at).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-away.z, 0, away.x);
            float k = easeSine(t / 195);
            Vec3 pos = camp.add(away.scale(10)).add(side.scale(6)).add(0, 9, 0).lerp(camp.subtract(away.scale(6)).add(side.scale(3)).add(0, 5, 0), k);
            double lift = EASED.to(1, t, Math.max(0, clear(pos, 3).y - pos.y), 6, true);
            return look(pos.add(0, lift, 0), camp.add(0, 1.5, 0).lerp(at.add(0, 2, 0), smooth((t - 40) / 150)), false, black, 0, 1, lines, 0);
        }
        if (t < 202) {                                           // a crane down the way they face
            float k = easeSine(t / 195);
            Vec3 pos = at.subtract(f.scale(26)).add(rgt.scale(20)).add(0, 30, 0).lerp(at.subtract(f.scale(12)).add(rgt.scale(12)).add(0, 20, 0), k);
            double lift = EASED.to(1, t, Math.max(0, clear(pos, 3).y - pos.y), 6, true);
            return look(pos.add(0, lift, 0), at.add(f.scale(34)).add(0, 2, 0), false, black, 0, 1, lines, 0);
        }
        if (t < 420) {                                           // low, looking back at them small among the ruins
            Vec3 pos = at.add(f.scale(28)).add(rgt.scale(-5 + 0.07 * (Math.min(t, 345) - 202)));
            Vec3 seeing = sighted(clear(new Vec3(pos.x, ground(pos) + 2.2, pos.z), 0), at.add(0, 1.2, 0));
            pos = new Vec3(pos.x, EASED.to(2, t, seeing.y, 6, true), pos.z);
            return look(pos, at.add(0, 1.2, 0), true, black, 0, 1, lines, 0);
        }
        Vec3 eye = at.add(0, 1.62, 0);
        float bars = 1 - smooth((t - 540) / 50);
        float fovEase = smooth((t - 560) / 40);
        if (t < 546) {                                           // round to the right shoulder
            float k = easeSine((t - 420) / 126);
            float phi = yaw + 110 * (1 - k);
            Vec3 fp = facing(phi);
            double r = Mth.lerp(k, 6.0, 1.8);
            Vec3 wanted = eye.subtract(fp.scale(r)).add(rgt.scale(0.65 * k)).add(0, Mth.lerp(k, 0.9, 0.25), 0);
            double room = EASED.to(3, t, clip(eye, wanted).distanceTo(eye), 6, false);   // in fast, out slow
            Vec3 pos = eye.add(wanted.subtract(eye).normalize().scale(room));
            return look(pos, eye.add(fp.scale(10)).subtract(rgt.scale(1.2)), true, black, 0, bars, lines, 0);
        }
        // and into their eyes, to the way they look
        float k = easeCubic(Math.min(1, (t - 546) / 44));
        Vec3 shoulder = clip(eye, eye.subtract(f.scale(1.8)).add(rgt.scale(0.65)).add(0, 0.25, 0));
        Vec3 pos = shoulder.lerp(eye, k);
        View from = look(shoulder, eye.add(f.scale(10)).subtract(rgt.scale(1.2)), true, 0, 0, 0, lines, 0);
        return new View(pos, Mth.rotLerp(k, from.yaw(), yaw), Mth.lerp(k, from.pitch(), pitch), pos.distanceTo(eye) > 0.35, black, 0, bars,
                lines, 0, 0, fovEase);
    }

    /**
     * A value of a shot's eased toward where the ground and the ruins put it (in the film's own time: the same however the
     * frames fall), set at once as each shot begins. Away from what is in the way - up over it ({@code up}), or in
     * nearer the player before it - it goes in a third of {@code tau} ticks; back, the way clear, over {@code tau}.
     */
    private static final class Eased {
        private int shot = -1;
        private float last;
        private double value;

        double to(int shot, float t, double target, double tau, boolean up) {
            if (shot != this.shot || t < this.last) {
                this.shot = shot;
                this.last = t;
                return this.value = target;
            }
            float dt = t - this.last;
            if (dt > 0) {
                double k = (target > this.value) == up ? tau / 3 : tau;
                this.value += (target - this.value) * (1 - Math.exp(-dt / k));
                this.last = t;
            }
            return this.value;
        }

        void reset() {
            this.shot = -1;
        }
    }

    private static final Eased EASED = new Eased();

    private static void line(List<Line> lines, float t, float from, float to, String key) {
        float a = fadeIn(t, from, from + 15) * (1 - fadeIn(t, to - 15, to));
        if (t >= from && t < to && a > 0.01F) {
            lines.add(new Line(Component.translatable(key), a, 0));
        }
    }

    // --- the shots' arithmetic --------------------------------------------------------------------------------------

    /** The way {@code yaw} faces, flat. */
    private static Vec3 facing(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    private static int ground(Vec3 at) {
        var level = Minecraft.getInstance().level;
        return level == null ? (int) at.y : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(at.x), Mth.floor(at.z));
    }

    /** {@code pos}, at least {@code above} over the ground there. */
    private static Vec3 clear(Vec3 pos, int above) {
        return new Vec3(pos.x, Math.max(pos.y, ground(pos) + above), pos.z);
    }

    /** {@code pos} raised (two at a time, to fourteen) till it sees {@code at}. */
    private static Vec3 sighted(Vec3 pos, Vec3 at) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return pos;
        }
        for (int k = 0; k < 7; k++) {
            var hit = level.clip(new net.minecraft.world.level.ClipContext(pos, at, net.minecraft.world.level.ClipContext.Block.VISUAL,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return pos;
            }
            pos = pos.add(0, 2, 0);
        }
        return pos;
    }

    /** Where a camera wanted at {@code wanted}, behind {@code eye}, can be: short of what is between them, as the game's own. */
    private static Vec3 clip(Vec3 eye, Vec3 wanted) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return wanted;
        }
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, wanted, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return wanted;
        }
        Vec3 way = wanted.subtract(eye);
        double room = Math.max(0, hit.getLocation().distanceTo(eye) - 0.3);
        return eye.add(way.normalize().scale(Math.min(room, way.length())));
    }

    private static View look(Vec3 pos, Vec3 at, boolean detached, float black, float white, float bars, List<Line> lines, float fogClear) {
        Vec3 d = at.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new View(pos, yaw, pitch, detached, Math.min(1, black), white, bars, lines, fogClear, 0, 0);
    }

    private static float fadeIn(float t, float from, float to) {
        return t <= from ? 0 : t >= to ? 1 : (t - from) / (to - from);
    }

    /** Black over a cut: in from {@code from} to {@code mid}, out to {@code to}. */
    private static float cut(float t, float from, float mid, float to) {
        return t < from || t > to ? 0 : t < mid ? (t - from) / (mid - from) : 1 - (t - mid) / (to - mid);
    }

    private static float smooth(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    private static float smoother(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * k * (k * (k * 6 - 15) + 10);
    }

    private static float easeSine(float k) {
        k = Mth.clamp(k, 0, 1);
        return (float) (0.5 - 0.5 * Math.cos(Math.PI * k));
    }

    private static float easeCubic(float k) {
        k = Mth.clamp(k, 0, 1);
        return k < 0.5F ? 4 * k * k * k : 1 - (float) Math.pow(-2 * k + 2, 3) / 2;
    }

    /** How clear the If's air is now, for {@code CuredSky}: by the film while the ending plays, else by the cure. */
    public static float fogClear(float partial) {
        View v = view(partial);
        if (v != null && kind == Film.Kind.ENDING) {
            return v.fogClear();
        }
        return Cure.clientCured ? 1 : 0;
    }

    // --- the client's side of it ------------------------------------------------------------------------------------

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (after > 0) {
            after--;
        }
        if (!playing()) {
            return;
        }
        ticks++;
        sounds();
        if (kind == Film.Kind.ENDING) {
            mist();
        }
        if (ticks > kind.length + 40) {                          // the server has ended it, or will: never stuck in it
            leave();
        }
    }

    /** Vanilla's own sounds at the films' moments, heard only by the one watching. */
    private static void sounds() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || anchors.isEmpty()) {
            return;
        }
        if (kind == Film.Kind.OPENING) {
            // the town's quiet (the user, 2026-10-21: its sounds may be added): wind, a door off in the ruins, glass
            Vec3 at = anchors.get(0);
            Vec3 f = facing((float) anchors.get(1).x);
            if (ticks == 20 || ticks == 230) {
                ui(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, 0.22F, 0.45F);
            } else if (ticks == 95) {                            // a bell, far off - once, and never again
                local(at.add(f.scale(-60)).add(f.z * 40, 8, -f.x * 40), net.minecraft.sounds.SoundEvents.BELL_BLOCK, 0.6F, 0.7F);
            } else if (ticks == 150) {
                local(at.add(f.scale(18)).add(f.z * 14, 0, -f.x * 14), net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, 0.5F, 0.6F);
            } else if (ticks == 262) {                           // and somewhere, a scream cut short
                local(at.add(f.scale(40)).add(-f.z * 30, 0, f.x * 30), net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_HURT, 0.35F, 0.55F);
            } else if (ticks == 290) {
                local(at.add(f.scale(-16)), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 0.25F, 1.4F);
            } else if (ticks == 362) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),
                        1.0F, 0.7F));
            } else if (ticks == 425) {                           // the dead, far ahead: they begin as play does
                local(at.add(f.scale(25)), net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, 0.5F, 0.7F);
            }
        } else if (kind == Film.Kind.ENDING) {
            if (ticks < Ending.BURST && ticks % 20 == 0) {       // the console's alarm: two notes, three times
                ui(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.75F);
            } else if (ticks < Ending.BURST && ticks % 20 == 8) {
                ui(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.56F);
            } else if (ticks == Ending.BURST + 2) {
                ui(net.minecraft.sounds.SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0F, 0.6F);
            } else if (ticks == 180 || ticks == 250) {
                ui(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, 0.4F, 0.5F);
            } else if (ticks == Ending.DIVE + 20) {               // carried away over the world
                ui(net.minecraft.sounds.SoundEvents.ELYTRA_FLYING, 0.35F, 0.55F);
            } else if (ticks == Ending.BURN + 8 || ticks == Ending.BURN + 40 || ticks == Ending.BURN + 72) {
                Vec3 town = anchors.get(2);
                var random = player.getRandom();
                local(town.add(random.nextGaussian() * 5, 1, random.nextGaussian() * 5), net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, 1.0F,
                        0.8F + random.nextFloat() * 0.3F);
            } else if (ticks == Ending.RISE + 30) {               // morning
                ui(net.minecraft.sounds.SoundEvents.BELL_RESONATE, 0.5F, 0.8F);
            }
        }
    }

    private static void ui(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static void local(Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().player.level().playLocalSound(at.x, at.y, at.z, sound, net.minecraft.sounds.SoundSource.AMBIENT, volume, pitch,
                false);
    }

    /**
     * The vaccine's mist: in the laboratory a burst of cloud; outside, slow smoke in the vaccine's pale blue, scaled up so
     * it reads from out in the air (the visual review, 2026-10-19: a cloud particle at forty blocks was a few pixels, and
     * in the yard it read as snow) - curtains over the base's rim, a plume from its top, a fall round the camera as it
     * dives after it, a thick sinking mist over the town.
     */
    private static void mist() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || anchors.size() < 5) {
            return;
        }
        var random = level.getRandom();
        Vec3 base = anchors.get(1);
        if (ticks >= Ending.BURST && ticks < Ending.OUT) {
            Vec3 v = anchors.get(0);
            for (int i = 0; i < 8; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, v.x + random.nextGaussian() * 1.5, v.y + 1 + random.nextDouble() * 3,
                        v.z + random.nextGaussian() * 1.5, random.nextGaussian() * 0.15, 0.05, random.nextGaussian() * 0.15);
            }
        } else if (ticks >= Ending.OUT && ticks < Ending.DIVE) {
            for (int i = 0; i < 8; i++) {                        // curtains over the rim
                double edge = (random.nextDouble() * 2 - 1) * 23;
                int side = random.nextInt(4);
                double x = side < 2 ? (side == 0 ? 23 : -23) + random.nextDouble() * 2 : edge;
                double z = side < 2 ? edge : (side == 2 ? 23 : -23) + random.nextDouble() * 2;
                double out = 0.03 / Math.max(1, Math.hypot(x, z) / 23);
                smoke(base.x + x, base.y - 1 + random.nextDouble() * 2, base.z + z, x * out / 23, -0.10 - random.nextDouble() * 0.08, z * out / 23);
            }
            if (ticks < Ending.OUT + 60) {
                for (int i = 0; i < 4; i++) {                    // the plume from its top
                    smoke(base.x, base.y + 12, base.z, random.nextGaussian() * 0.08, 0.25, random.nextGaussian() * 0.08);
                }
            }
        } else if (ticks >= Ending.DIVE && ticks < Ending.TO_TOWN) {
            Vec3 cam = mc.gameRenderer.mainCamera().position();
            for (int i = 0; i < 8; i++) {
                smoke(cam.x + random.nextGaussian() * 6, cam.y - 2 + random.nextDouble() * 6, cam.z + random.nextGaussian() * 6, 0, -0.5, 0);
            }
        } else if (ticks >= Ending.TO_TOWN + 20 && ticks < Ending.WHITE) {
            Vec3 town = anchors.get(2);
            Vec3 away = town.subtract(anchors.get(3)).multiply(1, 0, 1).normalize();
            for (int i = 0; i < 3; i++) {                        // over the town beyond the dead, sinking (in game, 2026-10-21:
                double along = 4 + random.nextDouble() * 26;     // between the camera and them, it hid them)
                double side = (random.nextDouble() - 0.5) * 40;
                smoke(town.x + away.x * along - away.z * side, town.y + 10 + random.nextDouble() * 14, town.z + away.z * along + away.x * side,
                        0, -0.06, 0);
            }
        }
    }

    private static void smoke(double x, double y, double z, double vx, double vy, double vz) {
        var engine = Minecraft.getInstance().particleEngine;
        var p = engine.createParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y, z, vx, vy, vz);
        if (p != null) {
            p.scale(2.2F);
            p.setLifetime(140 + Minecraft.getInstance().level.getRandom().nextInt(80));
            if (p instanceof net.minecraft.client.particle.SingleQuadParticle quad) {
                quad.setColor(0.80F, 0.91F, 0.96F);
            }
        }
    }

    /** The film's field of view: 60 for its shots, eased back to the player's own as the opening hands over. */
    @SubscribeEvent
    static void onFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        View v = view((float) event.getPartialTick());
        if (v != null) {
            event.setFOV(Mth.lerp(v.ownFov(), 60.0F, event.getFOV()));
        }
    }

    /** The end credits' own music, from the fog lifting on; it plays on into the morning of play. */
    @SubscribeEvent
    static void onMusic(net.neoforged.neoforge.client.event.SelectMusicEvent event) {
        if (kind == Film.Kind.ENDING && ticks >= Ending.RISE + 20) {
            event.setMusic(net.minecraft.sounds.Musics.CREDITS);
        }
    }

    @SubscribeEvent
    static void onHud(RenderGuiLayerEvent.Pre event) {
        if (playing()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onHand(RenderHandEvent event) {
        if (playing()) {
            event.setCanceled(true);
        }
    }

    /** The keys do nothing while it plays: not walking (the move already worked out this tick, undone), not using. */
    @SubscribeEvent
    static void onInput(MovementInputUpdateEvent event) {
        if (playing()) {
            event.getInput().keyPresses = net.minecraft.world.entity.player.Input.EMPTY;
            stillInput(event.getInput());
        }
    }

    private static java.lang.reflect.Field moveVector;

    private static void stillInput(net.minecraft.client.player.ClientInput input) {
        try {
            if (moveVector == null) {
                moveVector = net.minecraft.client.player.ClientInput.class.getDeclaredField("moveVector");
                moveVector.setAccessible(true);
            }
            moveVector.set(input, net.minecraft.world.phys.Vec2.ZERO);
        } catch (ReflectiveOperationException e) {
            moveVector = null;                                   // walking is pulled back by the server instead
        }
    }

    @SubscribeEvent
    static void onInteract(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (playing()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /**
     * Esc - the key, pressed - skips it; nothing else does (the review, 2026-10-19: the window losing focus opens the
     * pause screen, which was taken for Esc). No pause screen nor inventory opens over it.
     */
    @SubscribeEvent
    static void onKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (playing() && event.getKey() == 256 && event.getAction() == 1 /* Esc, pressed */
                && Minecraft.getInstance().gui.screen() == null) {
            leave();
            ClientPacketDistributor.sendToServer(new FilmPayloads.Skip());
        }
    }

    @SubscribeEvent
    static void onScreen(ScreenEvent.Opening event) {
        if (playing() && (event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen
                || event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            event.setCanceled(true);
        }
    }

    /** Leaving the server: nothing of a film carried to the next one joined. */
    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        kind = null;
        Cure.clientCured = false;
    }

    /**
     * The bars, the fades, and the words over all of it (the visual review, 2026-10-19: drawn under the black, the words
     * were hidden by the film's own dips; long lines ran off the screen; scaled by halves, the pixels smeared).
     */
    @SubscribeEvent
    static void onOverlay(RenderGuiEvent.Post event) {
        GuiGraphicsExtractor g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        View v = view(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (v == null) {
            if (after > 0) {                                     // out of a film: the black lifts, the bars draw back
                float k = Math.min(1, after / (float) AFTER);
                int bar = (int) (h * 0.11F * k);
                g.fill(0, 0, w, bar, 0xFF000000);
                g.fill(0, h - bar, w, h, 0xFF000000);
                if (afterKind == Film.Kind.ENDING) {
                    g.fill(0, 0, w, h, ((int) (k * 255) << 24));
                }
            }
            return;
        }
        int bar = (int) (h * 0.11F * v.bars());
        if (bar > 0) {
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (v.white() > 0.01F) {                                 // between the bars: the words in them stay readable over it
            g.fill(0, bar, w, h - bar, ((int) (Mth.clamp(v.white(), 0, 1) * 255) << 24) | 0xF4F8FA);
        }
        if (v.black() > 0.01F) {
            g.fill(0, 0, w, h, ((int) (Mth.clamp(v.black(), 0, 1) * 255) << 24));
        }
        var mc = Minecraft.getInstance();
        var font = mc.font;
        if (bar > 12) {                                          // that it can be skipped, small in the top bar's corner (the
            Component skip = Component.translatable("film.nookofthedead.skip");   // lower bar's words run long)
            g.text(font, skip, w - font.width(skip) - 8, bar / 2 - 4, 0x90C8C8C0, false);
        }
        int gs = Math.max(1, (int) mc.getWindow().getGuiScale());
        int y = h - Math.max(bar, 28) / 2 - 4;
        for (Line line : v.lines()) {
            int alpha = Mth.clamp((int) (line.alpha() * 255), 4, 255);
            float want = line.size() == 2 ? 2.0F : line.size() == 1 ? 1.5F : 1.25F;
            float scale = Math.max(1, Math.round(gs * want)) / (float) gs;   // whole screen pixels
            List<net.minecraft.util.FormattedCharSequence> rows = font.split(line.text(), (int) (w * 0.88F / scale));
            if (rows.size() > 2 && scale > 1) {
                scale = 1;
                rows = font.split(line.text(), (int) (w * 0.88F));
            }
            float rowH = 11 * scale;
            if (line.size() > 0) {                               // a card in the middle: a soft band behind it, a shadow
                float top = h / 2.0F - rows.size() * rowH / 2;
                int band = (int) (0x50 * line.alpha());
                g.fillGradient(0, (int) (h / 2.0F - 28), w, h / 2, 0, band << 24);
                g.fillGradient(0, h / 2, w, (int) (h / 2.0F + 28), band << 24, 0);
                for (int i = 0; i < rows.size(); i++) {
                    g.pose().pushMatrix();
                    g.pose().translate(w / 2.0F, top + i * rowH);
                    g.pose().scale(scale, scale);
                    g.text(font, rows.get(i), -font.width(rows.get(i)) / 2, 0, (alpha << 24) | 0xF2F2EC, true);
                    g.pose().popMatrix();
                }
            } else {                                             // a caption in the lower bar, upward from its baseline
                for (int i = rows.size() - 1; i >= 0; i--) {
                    g.pose().pushMatrix();
                    g.pose().translate(w / 2.0F, y);
                    g.pose().scale(scale, scale);
                    g.centeredText(font, rows.get(i), 0, 0, (alpha << 24) | 0xE8E8E0);
                    g.pose().popMatrix();
                    y -= (int) rowH;
                }
            }
        }
    }

    /**
     * The vaccine's console, going, used: stop it? (the user, 2026-10-21: "この機械をとめると、再び世界がゾンビであふれる
     * 可能性があります。本当に良いですか" - asked first, at least). Yes tells the server; no leaves it going.
     */
    private static void askStop(net.minecraft.core.BlockPos pos) {
        var mc = Minecraft.getInstance();
        mc.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) {
                ClientPacketDistributor.sendToServer(new FilmPayloads.Stop(pos));
            }
            mc.gui.setScreen(null);
        }, Component.translatable("block.nookofthedead.vaccine.confirm.title"), Component.translatable("block.nookofthedead.vaccine.confirm"),
                Component.translatable("block.nookofthedead.vaccine.confirm.stop"), net.minecraft.network.chat.CommonComponents.GUI_CANCEL));
    }

    /** The server's messages: a film to play or stop; the If cured or not; the console's question. */
    @EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
    static final class Handlers {
        @SubscribeEvent
        static void register(RegisterClientPayloadHandlersEvent event) {
            event.register(FilmPayloads.Play.TYPE, (payload, context) -> context.enqueueWork(() -> play(payload)));
            event.register(FilmPayloads.Cured.TYPE, (payload, context) -> context.enqueueWork(() -> Cure.clientCured = payload.cured()));
            event.register(FilmPayloads.AskStop.TYPE, (payload, context) -> context.enqueueWork(() -> askStop(payload.pos())));
        }
    }

    private FilmCamera() {}
}
