package dev.nookofthedead.client.dev;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.nookofthedead.NookOfTheDead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Development only (this package stays out of the jar): a camera that flies along a path, for the trailer (the user,
 * 2026-10-17: "ハリウッドのゾンビ映画のPVみたいなやつ"; Replay Mod and Flashback being Fabric's only). A path is a file
 * {@code run-agent/camera/<name>.txt}, one keyframe a line:
 * <pre>
 * # seconds  x y z  yaw pitch  [roll] [fov]       (or: seconds  x y z  look tx ty tz  [roll] [fov])
 * 0     5400 125 4000   180 10
 * 4.5   5400 122 4010   look 5420 118 4030   0 60
 * ease both                 (or in, out, linear: how the whole run starts and stops; both if not said)
 * fps 30                    (the film's frames a second, 30 if not said)
 * slow 4                    (while filming the game runs this many times slower, so every frame gets drawn)
 * mode spectator            (or creative, survival: the player's game mode for it; spectator if not said)
 * hand                      (the hand and what it holds in view: the HUD hidden without hiding it)
 * hud                       (the hand and the HUD in view - the hotbar, hearts: a how-to shot; the chat kept out)
 * hud chat                  (and the chat too: a command typed in it)
 * third                     (the camera apart from the player, who stays where they stand and is seen: a shot of the
 *                            player - put them in place with the commands; the dead come for them, not the camera)
 * film                      (the camera left to a film of the game's own - client/FilmCamera, started by a command
 *                            here - and only filmed: the keyframes give no more than how long)
 * shake 0.4                 (a hand-held camera: degrees it drifts by)
 * /time set 18000           (a command run as the path starts)
 * at 3.2 /summon ...        (a command run that far into it)
 * at 3.2 shake 1.5          (the hand-held drift from then on, eased into)
 * at 3.2 kick 4             (a jolt of that many degrees, dying away in half a second)
 * at 3.2 swing              (the held hand swung, as in a blow - with "hand", it is seen)
 * at 3.2 ring x y z        (the bell there rung, as if struck - the dead hear it as any bell)
 * </pre>
 * x y z are where the player's feet are (the eye is 1.62 above, standing); yaw and pitch as F3 shows them. The camera
 * goes through every keyframe on a smooth curve (Catmull-Rom). It keeps the game's own time - ticks and the part of a
 * tick each frame is drawn at - so it stays with everything moving in the world, and with {@code /tick rate}.
 * <ul>
 * <li>{@code /nzwcam play <name>} - fly it; {@code /nzwcam record <name>} - fly it and film it: each frame, as the game
 * drew it, read back and handed to ffmpeg (to {@code run-agent/videos/<name>_<time>.mp4}), one frame each 1/fps of the
 * game's time however slowly the game draws them - so with {@code slow} a heavy shader still makes a smooth film;</li>
 * <li>{@code /nzwcam mark <name> [seconds]} - add where you stand and look as a keyframe (seconds after the last, 3 if
 * not said); {@code /nzwcam go <name> <n>} - stand at keyframe n, to look; {@code /nzwcam stop}.</li>
 * </ul>
 * ffmpeg is looked for on the PATH, or given by {@code -Dnzwcam.ffmpeg=<path>}.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class CameraPath {
    private static final Path DIR = Path.of("camera");
    private static final Path VIDEOS = Path.of("videos");
    /** How long the last frame is held after the path's end, in seconds. */
    private static final double HOLD = 0.5;

    private record Key(double t, double x, double y, double z, double yaw, double pitch, double roll, double fov) {
    }

    /**
     * A command run at {@code t}, or (with no command) the hand-held drift set to {@code shake}, or a jolt of {@code kick},
     * or the bell at {@code bell} rung.
     */
    private record Cue(double t, String command, double shake, double kick, net.minecraft.core.BlockPos bell) {
        Cue(double t, String command, double shake, double kick) {
            this(t, command, shake, kick, null);
        }
    }

    private record Path_(List<Key> keys, List<String> start, List<Cue> cues, String ease, int fps, double slow, GameType mode, boolean hand,
            double shake, boolean film, boolean third, boolean hud, boolean chat) {
        double length() {
            return this.keys.getLast().t();
        }
    }

    /** What is playing: the path, the game tick it began at (with the part of a tick), the cues still to run. */
    private static Path_ playing;
    private static double began;
    private static int nextCue;
    private static boolean hudWasHidden;
    private static GameType modeWas;
    /** Where the current frame puts the camera (its angles and fov are set as the frame asks for them). */
    private static Key now;
    /** The hand-held drift: from, to, and when the change began; the last jolt and when. */
    private static double shakeFrom;
    private static double shakeTo;
    private static double shakeAt;
    private static double kick;
    private static double kickAt;

    /**
     * A film being made: ffmpeg, the frames on their way to it, how many are still being read back from the card, and
     * whether it is to be finished once they are in.
     */
    private static final class Film {
        final BlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(24);
        final java.util.concurrent.atomic.AtomicInteger reading = new java.util.concurrent.atomic.AtomicInteger();
        volatile boolean closing;

        void put(byte[] frame) {
            try {
                this.frames.put(frame);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /** The end sent once nothing is still being read back. */
        void endIfDone() {
            if (this.closing && this.reading.get() == 0) {
                this.put(END);
            }
        }
    }

    private static final byte[] END = new byte[0];
    /** The film being made, if any; the next frame's time, frames to take of this one, how many doubled. */
    private static Film ffmpeg;
    private static double nextFrame;
    private static int frameNo;
    private static int take;
    private static int doubled;
    private static int filmWidth;
    private static int filmHeight;

    private CameraPath() {
    }

    @SubscribeEvent
    static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nzwcam")
                .then(Commands.literal("play").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> play(StringArgumentType.getString(c, "name"), false))))
                .then(Commands.literal("record").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> play(StringArgumentType.getString(c, "name"), true))))
                .then(Commands.literal("stop").executes(c -> {
                    stop("stopped");
                    return 1;
                }))
                .then(Commands.literal("mark").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> mark(StringArgumentType.getString(c, "name"), -1))
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0))
                                .executes(c -> mark(StringArgumentType.getString(c, "name"), DoubleArgumentType.getDouble(c, "seconds"))))))
                .then(Commands.literal("go").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("n", IntegerArgumentType.integer(0))
                                .executes(c -> go(StringArgumentType.getString(c, "name"), IntegerArgumentType.getInteger(c, "n")))))));
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        NookOfTheDead.LOGGER.info("[nzwcam] {}", text);
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[nzwcam] " + text));
        }
    }

    private static Path file(String name) {
        return DIR.resolve(name + ".txt");
    }

    /** A path read from its file; null (and said why) if it cannot be. */
    private static Path_ read(String name) {
        Path file = file(name);
        if (!Files.exists(file)) {
            say("no " + file.toAbsolutePath());
            return null;
        }
        List<Key> keys = new ArrayList<>();
        List<String> start = new ArrayList<>();
        List<Cue> cues = new ArrayList<>();
        String ease = "both";
        int fps = 30;
        double slow = 1;
        double shake = 0;
        GameType mode = GameType.SPECTATOR;
        boolean hand = false;
        boolean film = false;
        boolean third = false;
        boolean hud = false;
        boolean chat = false;
        try {
            int n = 0;
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                n++;
                String line = raw.replace("﻿", "").strip();          // a byte-order mark from an editor ignored
                int hash = line.indexOf('#');
                if (hash >= 0 && !line.startsWith("/") && !line.startsWith("at ")) {
                    line = line.substring(0, hash).strip();
                }
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("/")) {
                    start.add(line.substring(1));
                } else if (line.startsWith("at ")) {
                    String[] p = line.substring(3).strip().split("\\s+", 2);
                    double t = Double.parseDouble(p[0]);
                    String what = p[1].strip();
                    if (what.startsWith("shake ")) {
                        cues.add(new Cue(t, null, Double.parseDouble(what.substring(6).strip()), 0));
                    } else if (what.startsWith("ring ")) {
                        String[] at = what.substring(5).strip().split("\\s+");
                        cues.add(new Cue(t, null, Double.NaN, 0, net.minecraft.core.BlockPos.containing(Double.parseDouble(at[0]),
                                Double.parseDouble(at[1]), Double.parseDouble(at[2]))));
                    } else if (what.startsWith("kick ")) {
                        cues.add(new Cue(t, null, Double.NaN, Double.parseDouble(what.substring(5).strip())));
                    } else {
                        cues.add(new Cue(t, what.startsWith("/") ? what.substring(1) : what, Double.NaN, 0));
                    }
                } else if (line.startsWith("ease ")) {
                    ease = line.substring(5).strip();
                } else if (line.startsWith("fps ")) {
                    fps = Integer.parseInt(line.substring(4).strip());
                } else if (line.startsWith("slow ")) {
                    slow = Double.parseDouble(line.substring(5).strip());
                } else if (line.startsWith("shake ")) {
                    shake = Double.parseDouble(line.substring(6).strip());
                } else if (line.startsWith("mode ")) {
                    mode = GameType.byName(line.substring(5).strip(), GameType.SPECTATOR);
                } else if (line.equals("hand")) {
                    hand = true;
                } else if (line.equals("hud") || line.equals("hud chat")) {
                    hand = true;
                    hud = true;
                    chat = line.endsWith("chat");
                } else if (line.equals("film")) {
                    film = true;
                } else if (line.equals("third")) {
                    third = true;
                } else {
                    String[] p = line.split("\\s+");
                    if (p.length >= 8 && p[4].equals("look")) {
                        // looking at a point: the yaw and pitch that face it from there
                        double x = Double.parseDouble(p[1]), y = Double.parseDouble(p[2]), z = Double.parseDouble(p[3]);
                        double dx = Double.parseDouble(p[5]) - x, dy = Double.parseDouble(p[6]) - (y + 1.62), dz = Double.parseDouble(p[7]) - z;
                        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
                        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                        keys.add(new Key(Double.parseDouble(p[0]), x, y, z, yaw, pitch, p.length > 8 ? Double.parseDouble(p[8]) : 0,
                                p.length > 9 ? Double.parseDouble(p[9]) : Double.NaN));
                        continue;
                    }
                    if (p.length < 6) {
                        say(file.getFileName() + " line " + n + ": seconds x y z yaw pitch [roll] [fov]");
                        return null;
                    }
                    double[] v = new double[p.length];
                    for (int i = 0; i < p.length; i++) {
                        v[i] = Double.parseDouble(p[i]);
                    }
                    keys.add(new Key(v[0], v[1], v[2], v[3], v[4], v[5], p.length > 6 ? v[6] : 0, p.length > 7 ? v[7] : Double.NaN));
                }
            }
        } catch (IOException | RuntimeException e) {
            say(file.getFileName() + ": " + e.getMessage());
            return null;
        }
        if (keys.isEmpty()) {
            say(file.getFileName() + " has no keyframes");
            return null;
        }
        keys.sort((a, b) -> Double.compare(a.t(), b.t()));
        cues.sort((a, b) -> Double.compare(a.t(), b.t()));
        // the yaw unwound, so it turns the short way from each keyframe to the next
        List<Key> unwound = new ArrayList<>();
        double last = keys.getFirst().yaw();
        for (Key k : keys) {
            double yaw = last + Mth.wrapDegrees(k.yaw() - last);
            unwound.add(new Key(k.t(), k.x(), k.y(), k.z(), yaw, k.pitch(), k.roll(), k.fov()));
            last = yaw;
        }
        return new Path_(unwound, start, cues, ease, fps, slow, mode, hand, shake, film, third, hud, chat);
    }

    /** The game's time now, in ticks with the part of a tick this frame is drawn at. */
    private static double ticks(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    private static int play(String name, boolean record) {
        Minecraft mc = Minecraft.getInstance();
        Path_ path = read(name);
        if (path == null || mc.player == null || mc.gameMode == null) {
            return 0;
        }
        stop(null);
        modeWas = mc.gameMode.getPlayerMode();
        if (modeWas != path.mode()) {
            mc.player.connection.sendCommand("gamemode " + path.mode().getName());
        }
        // the player put at the first keyframe by the server first: a jump of the camera from far off is otherwise taken
        // for cheating outside spectator ("moved wrongly"), the player kept where it was - and the dead go after it there
        Key first = path.keys().getFirst();
        if (!path.third()) mc.player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", first.x(), first.y(), first.z(),
                Mth.wrapDegrees(first.yaw()), Mth.clamp(first.pitch(), -90, 90)));
        if (record && path.slow() > 1) {
            mc.player.connection.sendCommand(String.format(Locale.ROOT, "tick rate %.3f", 20.0 / path.slow()));
        }
        for (String command : path.start()) {
            mc.player.connection.sendCommand(command);
        }
        hudWasHidden = mc.gui.hud.isHidden();
        if (hudWasHidden == path.hand()) {                              // hidden, unless the hand is to be seen
            mc.gui.hud.toggle();
        }
        playing = path;
        nextCue = 0;
        shakeFrom = shakeTo = path.shake();
        shakeAt = -1;
        kick = 0;
        began = ticks(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (record && !film(name, path.fps())) {
            say("ffmpeg did not start - playing without filming");
        }
        return 1;
    }

    /** Starts ffmpeg taking the frames as raw pixels; false if it would not start. */
    private static boolean film(String name, int fps) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        try {
            Files.createDirectories(VIDEOS);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd_HHmmss"));
            Path out = VIDEOS.resolve(name + "_" + stamp + ".mp4").toAbsolutePath();
            filmWidth = target.width;
            filmHeight = target.height;
            ProcessBuilder builder = new ProcessBuilder(System.getProperty("nzwcam.ffmpeg", "ffmpeg"), "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", filmWidth + "x" + filmHeight, "-r", Integer.toString(fps), "-i", "-",
                    "-vf", "vflip,pad=ceil(iw/2)*2:ceil(ih/2)*2", "-c:v", "libx264", "-preset", "medium", "-crf", "12", "-pix_fmt", "yuv420p",
                    out.toString());
            builder.redirectErrorStream(true);
            builder.redirectOutput(VIDEOS.resolve("ffmpeg.log").toFile());
            Process process = builder.start();
            Film film = new Film();
            BlockingQueue<byte[]> queue = film.frames;
            Thread thread = new Thread(() -> {
                try (OutputStream to = new BufferedOutputStream(process.getOutputStream(), 1 << 22)) {
                    for (byte[] frame = queue.take(); frame != END; frame = queue.take()) {
                        to.write(frame);
                    }
                } catch (IOException | InterruptedException e) {
                    NookOfTheDead.LOGGER.warn("[nzwcam] filming stopped: {}", e.toString());
                }
                try {
                    process.waitFor();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                NookOfTheDead.LOGGER.info("[nzwcam] film written: {}", out);
            }, "nzwcam-film");
            thread.setDaemon(true);
            thread.start();
            ffmpeg = film;
            nextFrame = 0;
            frameNo = 0;
            take = 0;
            doubled = 0;
            say("filming to " + out + " (" + filmWidth + "x" + filmHeight + ", " + fps + " fps)");
            return true;
        } catch (IOException e) {
            ffmpeg = null;
            return false;
        }
    }

    /** Ends the playing (if any): the HUD and game mode back as they were, the game's speed back, the film finished. */
    private static void stop(String why) {
        dev.nookofthedead.client.FilmCamera.devView = null;
        Minecraft mc = Minecraft.getInstance();
        Path_ was = playing;
        if (was != null) {
            if (mc.gui.hud.isHidden() != hudWasHidden) {
                mc.gui.hud.toggle();
            }
            if (mc.player != null) {
                if (ffmpeg != null && was.slow() > 1) {
                    mc.player.connection.sendCommand("tick rate 20");
                }
                if (modeWas != null && modeWas != was.mode()) {
                    mc.player.connection.sendCommand("gamemode " + modeWas.getName());
                }
            }
        }
        playing = null;
        now = null;
        if (ffmpeg != null) {
            Film film = ffmpeg;
            ffmpeg = null;
            film.closing = true;                                        // the writer closes ffmpeg's input: it finishes the file
            film.endIfDone();
            say(frameNo + " frames filmed" + (doubled > 0 ? ", " + doubled + " of them doubled (the game drew too few: slow it more)" : ""));
        }
        if (was != null && why != null) {
            say(why);
        }
    }

    private static int mark(String name, double seconds) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        Path file = file(name);
        try {
            Files.createDirectories(DIR);
            double t = 0;
            if (Files.exists(file)) {
                Path_ path = read(name);
                if (path != null) {
                    t = path.length() + (seconds < 0 ? 3 : seconds);
                }
            } else {
                Files.writeString(file, "# seconds  x y z  yaw pitch  [roll] [fov]\n", StandardCharsets.UTF_8);
            }
            String line = String.format(Locale.ROOT, "%.2f  %.2f %.2f %.2f  %.1f %.1f%n", t, player.getX(), player.getY(), player.getZ(),
                    Mth.wrapDegrees(player.getYRot()), player.getXRot());
            Files.writeString(file, line, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            say("marked " + line.strip());
            return 1;
        } catch (IOException e) {
            say(e.getMessage());
            return 0;
        }
    }

    private static int go(String name, int n) {
        Path_ path = read(name);
        LocalPlayer player = Minecraft.getInstance().player;
        if (path == null || player == null) {
            return 0;
        }
        if (n >= path.keys().size()) {
            say(name + " has " + path.keys().size() + " keyframes");
            return 0;
        }
        Key k = path.keys().get(n);
        player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", k.x(), k.y(), k.z(), k.yaw(), k.pitch()));
        return 1;
    }

    private static double ease(String mode, double u) {
        return switch (mode) {
            case "linear" -> u;
            case "in" -> u * u;
            case "out" -> 1 - (1 - u) * (1 - u);
            default -> u * u * (3 - 2 * u);
        };
    }

    /** Catmull-Rom through p1..p2 at s (0..1), p0 and p3 the keyframes either side. */
    private static double spline(double p0, double p1, double p2, double p3, double s) {
        double s2 = s * s;
        double s3 = s2 * s;
        return 0.5 * (2 * p1 + (-p0 + p2) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s2 + (-p0 + 3 * p1 - 3 * p2 + p3) * s3);
    }

    /** Where the path is at {@code t} seconds. */
    private static Key at(Path_ path, double t) {
        List<Key> k = path.keys();
        if (k.size() == 1 || t <= k.getFirst().t()) {
            return k.getFirst();
        }
        if (t >= path.length()) {
            return k.getLast();
        }
        int i = 0;
        while (k.get(i + 1).t() < t) {
            i++;
        }
        Key a = k.get(Math.max(0, i - 1)), b = k.get(i), c = k.get(i + 1), d = k.get(Math.min(k.size() - 1, i + 2));
        double s = (t - b.t()) / Math.max(1e-6, c.t() - b.t());
        double fov = Double.isNaN(b.fov()) && Double.isNaN(c.fov()) ? Double.NaN
                : Mth.lerp(s * s * (3 - 2 * s), Double.isNaN(b.fov()) ? c.fov() : b.fov(), Double.isNaN(c.fov()) ? b.fov() : c.fov());
        return new Key(t, spline(a.x(), b.x(), c.x(), d.x(), s), spline(a.y(), b.y(), c.y(), d.y(), s), spline(a.z(), b.z(), c.z(), d.z(), s),
                spline(a.yaw(), b.yaw(), c.yaw(), d.yaw(), s), spline(a.pitch(), b.pitch(), c.pitch(), d.pitch(), s),
                spline(a.roll(), b.roll(), c.roll(), d.roll(), s), fov);
    }

    /** A smooth wobble about 0 (roughly -1..1) at {@code t} seconds, one of a few ({@code k}) that do not move together. */
    private static double drift(double t, int k) {
        return (Math.sin(t * 1.93 + k * 1.7) * 0.5 + Math.sin(t * 3.37 + k * 4.1) * 0.3 + Math.sin(t * 7.11 + k * 2.9) * 0.2) * 1.15;
    }

    /**
     * Each frame, as the camera is set up for it - after the game has worked out the moment it draws, before it takes the
     * camera's place from the player: the player put where the path is at that moment, so the frame shows it then.
     */
    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (playing == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            stop(null);
            return;
        }
        double elapsed = (ticks((float) event.getPartialTick()) - began) / 20.0;
        double length = playing.length();
        double time = elapsed;
        if (ffmpeg != null) {
            // the film's frames fall each 1/fps of the game's time: this frame is one if it has reached the next, and the
            // camera is put exactly there; if the game drew too few to reach one of them, this one stands in for it too
            take = 0;
            double step = 1.0 / playing.fps();
            if (elapsed + 1e-9 >= nextFrame) {
                time = nextFrame;
                while (elapsed + 1e-9 >= nextFrame) {
                    take++;
                    nextFrame += step;
                }
                doubled += take - 1;
            }
        }
        if (time > length + HOLD) {                                     // a moment held on the last frame, then done
            stop("done");
            return;
        }
        while (nextCue < playing.cues().size() && playing.cues().get(nextCue).t() <= time) {
            Cue cue = playing.cues().get(nextCue++);
            if ("swing".equals(cue.command())) {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            } else if (cue.command() != null) {
                player.connection.sendCommand(cue.command());
            } else if (cue.bell() != null) {
                ring(cue.bell());
            } else if (!Double.isNaN(cue.shake())) {
                shakeFrom = shaking(cue.t());
                shakeTo = cue.shake();
                shakeAt = cue.t();
            } else {
                kick = cue.kick();
                kickAt = cue.t();
            }
        }
        if (playing.film()) {
            now = null;                                                 // the film has the camera; this only films it
            return;
        }
        double t = length <= 0 ? 0 : ease(playing.ease(), Math.min(1, time / length)) * length;
        Key k = at(playing, t);
        // the hand-held drift and any jolt, on top
        double amount = shaking(time);
        double jolt = kick == 0 ? 0 : kick * Math.exp(-(time - kickAt) / 0.18);
        double yaw = k.yaw() + amount * drift(time, 0) + jolt * Math.sin((time - kickAt) * 41.0);
        double pitch = k.pitch() + amount * 0.7 * drift(time, 1) + jolt * 0.8 * Math.sin((time - kickAt) * 53.0 + 1.0);
        double roll = k.roll() + amount * 0.45 * drift(time, 2) + jolt * 0.3 * Math.sin((time - kickAt) * 37.0 + 2.0);
        double lift = amount * 0.02 * drift(time, 3);
        now = new Key(time, k.x(), k.y() + lift, k.z(), yaw, Mth.clamp(pitch, -90, 90), roll, k.fov());
        if (playing.third()) {                                          // the camera on its own, the player left be, and seen
            Key at = now;
            dev.nookofthedead.client.FilmCamera.devView = partial -> new dev.nookofthedead.client.FilmCamera.View(
                    new Vec3(at.x(), at.y() + 1.62, at.z()), (float) at.yaw(), (float) at.pitch(), true, 0, 0, 0, List.of(), 0,
                    (float) at.roll(), 1);
            event.setYaw((float) now.yaw());
            event.setPitch((float) now.pitch());
            event.setRoll((float) now.roll());
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(now.x(), now.y(), now.z());
        player.xo = player.xOld = now.x();
        player.yo = player.yOld = now.y();
        player.zo = player.zOld = now.z();
        player.setYRot((float) now.yaw());
        player.setXRot((float) now.pitch());
        player.yRotO = (float) now.yaw();
        player.xRotO = (float) now.pitch();
        player.setYHeadRot((float) now.yaw());
        player.yHeadRotO = (float) now.yaw();
        event.setYaw((float) now.yaw());
        event.setPitch((float) now.pitch());
        event.setRoll((float) now.roll());
    }

    /** The bell at {@code pos} struck, in the world the game is hosting (this is a single-player tool). */
    private static void ring(net.minecraft.core.BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.level == null) {
            return;
        }
        var dimension = mc.level.dimension();
        server.execute(() -> {
            var level = server.getLevel(dimension);
            if (level != null && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BellBlock bell) {
                bell.attemptToRing(level, pos, net.minecraft.core.Direction.EAST);   // swung across the view of one in front
            }
        });
    }

    /** How much the camera drifts at {@code time}: eased from the last setting to the new over 0.4 seconds. */
    private static double shaking(double time) {
        if (shakeAt < 0) {
            return shakeTo;
        }
        double u = Mth.clamp((time - shakeAt) / 0.4, 0, 1);
        return Mth.lerp(u * u * (3 - 2 * u), shakeFrom, shakeTo);
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        if (now != null && !Double.isNaN(now.fov())) {
            event.setFOV((float) now.fov());
        }
    }

    /** With the hand in view the HUD is not hidden, so it is not drawn instead - nor the outline on the block looked at. */
    @SubscribeEvent
    static void onGui(RenderGuiEvent.Pre event) {
        if (playing != null && playing.hand() && !playing.hud()) {
            event.setCanceled(true);
        }
    }

    /** In a HUD shot the chat is left out, unless the path shows it: the path's own commands would be read in it. */
    @SubscribeEvent
    static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (playing != null && playing.hud() && !playing.chat() && event.getName().equals(VanillaGuiLayers.CHAT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (playing != null) {
            event.setCanceled(true);
        }
    }

    /** Once the frame is drawn: if it is one of the film's, its pixels read back and sent on (as many times as it stands for). */
    @SubscribeEvent
    static void onFrameDrawn(RenderFrameEvent.Post event) {
        if (ffmpeg == null || take == 0) {
            return;
        }
        int times = take;
        take = 0;
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null) {
            return;
        }
        if (target.width != filmWidth || target.height != filmHeight) {
            stop("the window changed size - filming stopped");
            return;
        }
        Film film = ffmpeg;
        int size = filmWidth * filmHeight * texture.getFormat().blockSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "nzwcam frame", 9, size);
        frameNo += times;
        film.reading.incrementAndGet();
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView read = buffer.map(true, false)) {
                ByteBuffer data = read.data();
                byte[] pixels = new byte[size];
                data.get(0, pixels);
                for (int i = 0; i < times; i++) {
                    film.put(pixels);
                }
            }
            buffer.close();
            film.reading.decrementAndGet();
            film.endIfDone();
        }, 0);
    }
}
