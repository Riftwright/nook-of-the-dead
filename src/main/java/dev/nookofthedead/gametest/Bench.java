package dev.nookofthedead.gametest;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Capital;
import dev.nookofthedead.world.Region;
import dev.nookofthedead.world.Terrain;
import dev.nookofthedead.world.Town;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * For measuring only (dev runs, with the tests: left out of the published jar): {@code /nzwbench}, each result written to
 * the log as {@code [bench] ...}.
 * <ul>
 * <li>{@code gen <where> <r>} - brings the (2r+1)^2 chunks round a fresh spot of {@code where} (overworld, a region of the
 * If by name, sea, peak) to full at once, the server waiting on them: the time, the memory taken on every thread, the GCs.
 * <li>{@code tick <seconds>} - the server's ticks over that long: the mean, the middle, the slowest; the If's mobs.
 * <li>{@code heap} - the heap in use after a full GC.
 * </ul>
 */
final class Bench {
    /** The spots measured so far: a new one is never within {@link #APART} of one, whose chunks are made already. */
    private static final List<int[]> USED = new ArrayList<>();
    private static final int APART = 400;
    private static List<Long> ticks;
    private static int want;
    private static long started;
    private static CommandSourceStack asked;

    static void register() {
        NeoForge.EVENT_BUS.addListener(Bench::onCommands);
        NeoForge.EVENT_BUS.addListener(Bench::onTickPre);
        NeoForge.EVENT_BUS.addListener(Bench::onTickPost);
    }

    private static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nzwbench").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("gen").then(Commands.argument("where", StringArgumentType.word())
                        .then(Commands.argument("r", IntegerArgumentType.integer(1, 16))
                                .executes(c -> gen(c.getSource(), StringArgumentType.getString(c, "where"), IntegerArgumentType.getInteger(c, "r"))))))
                .then(Commands.literal("tick").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(c -> {
                            ticks = new ArrayList<>();
                            want = IntegerArgumentType.getInteger(c, "seconds") * 20;
                            asked = c.getSource();
                            return 1;
                        })))
                .then(Commands.literal("crowd").then(Commands.argument("n", IntegerArgumentType.integer(1, 1000))
                        .executes(c -> crowd(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("crows").then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
                        .then(Commands.argument("at", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(c -> crows(c.getSource(), IntegerArgumentType.getInteger(c, "n"),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c, "at"))))))
                .then(Commands.literal("heap").executes(c -> {
                    say(c.getSource(), "heap " + heapMb() + " MB in use after GC, of " + Runtime.getRuntime().maxMemory() / (1 << 20));
                    return 1;
                })));
    }

    private static int gen(CommandSourceStack source, String where, int r) {
        MinecraftServer server = source.getServer();
        ServerLevel level;
        int[] at;
        if (where.equals("overworld")) {
            level = server.overworld();
            at = new int[] {200_000 + USED.size() * 4_000, 200_000};
        } else {
            if (!IfWorld.get(server).rolled()) {
                say(source, "the If is not rolled");
                return 0;
            }
            level = IfLevel.open(server);
            at = spot(level.getSeed(), where);
        }
        if (at == null) {
            say(source, "no fresh " + where + " near");
            return 0;
        }
        USED.add(at);
        int cx0 = at[0] >> 4;
        int cz0 = at[1] >> 4;
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long gcCount0 = gcCount();
        long gcTime0 = gcTime();
        long alloc0 = threads.getTotalThreadAllocatedBytes();
        long t0 = System.nanoTime();
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                all.add(level.getChunkSource().getChunkFuture(cx0 + dx, cz0 + dz, ChunkStatus.FULL, true));
            }
        }
        while (!all.stream().allMatch(CompletableFuture::isDone)) {
            if (!level.getChunkSource().pollTask()) {
                LockSupport.parkNanos(100_000L);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        long mb = (threads.getTotalThreadAllocatedBytes() - alloc0) >> 20;
        int n = all.size();
        say(source, String.format("gen %s at %d %d: %d chunks in %d ms (%.1f ms a chunk), %d MB allocated (%.1f a chunk), %d GCs %d ms",
                where, at[0], at[1], n, ms, ms / (double) n, mb, mb / (double) n, gcCount() - gcCount0, gcTime() - gcTime0));
        return 1;
    }

    /** {@code n} of the mutated dead on the ground round the player, within 32 blocks, each as it would come. */
    private static int crowd(CommandSourceStack source, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        var random = level.getRandom();
        int made = 0;
        for (int i = 0; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 8 + random.nextDouble() * 24;
            int x = (int) Math.floor(player.getX() + Math.cos(a) * d);
            int z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var zombie = dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            if (zombie == null) {
                continue;
            }
            zombie.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
            if (level.addFreshEntity(zombie)) {
                made++;
            }
        }
        say(source, "crowd of " + made);
        return made;
    }

    /** {@code n} of the capital's crows wheeling round {@code at}, as a flock round a spire (for filming them near). */
    private static int crows(CommandSourceStack source, int n, net.minecraft.core.BlockPos at) {
        ServerLevel level = source.getLevel();
        var random = level.getRandom();
        for (int i = 0; i < n; i++) {
            var crow = dev.nookofthedead.registry.ZwEntities.CAPITAL_CROW.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (crow == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2;
            double d = 6 + random.nextDouble() * 8;
            crow.snapTo(at.getX() + 0.5 + Math.cos(a) * d, at.getY() + random.nextDouble() * 6, at.getZ() + 0.5 + Math.sin(a) * d, 0, 0);
            crow.roost(at);
            level.addFreshEntity(crow);
        }
        say(source, n + " crows round " + at.toShortString());
        return n;
    }

    /** The middle of a fresh grid block of {@code where} with the same all round it; the capital's middle; a sea's, a peak's. */
    private static int[] spot(long seed, String where) {
        if (where.equals("capital")) {                                  // its middle, then four spots in its city
            int[] c = Capital.centre(seed);
            for (int[] d : new int[][] {{0, 0}, {-180, -40}, {180, 40}, {40, -180}, {-40, 180}}) {
                if (fresh(c[0] + d[0], c[1] + d[1], 200)) {
                    return new int[] {c[0] + d[0], c[1] + d[1]};
                }
            }
            return null;
        }
        if (where.equals("sea") || where.equals("peak")) {
            int side = Terrain.FEATURE_CELL;
            for (int q = 0; q < 40 * 40; q++) {
                int qx = q % 40 - 20;
                int qz = q / 40 - 20;
                Object found = Terrain.square(seed, qx, qz);
                if (found instanceof Terrain.Sea sea && where.equals("sea") && fresh(sea.x(), sea.z(), APART)) {
                    return new int[] {sea.x(), sea.z()};
                }
                if (found instanceof Terrain.Peak peak && where.equals("peak") && fresh(peak.x(), peak.z(), APART)) {
                    return new int[] {peak.x(), peak.z()};
                }
            }
            return null;
        }
        Region region = Region.valueOf(where.toUpperCase(java.util.Locale.ROOT));
        for (int ring = 4; ring <= 60; ring++) {                       // past where one comes in: made already
            for (int bx = -ring; bx <= ring; bx++) {
                for (int bz = -ring; bz <= ring; bz++) {
                    if (Math.max(Math.abs(bx), Math.abs(bz)) != ring) {
                        continue;
                    }
                    int x = bx * Town.BLOCK + Town.BLOCK / 2;
                    int z = bz * Town.BLOCK + Town.BLOCK / 2;
                    if (!fresh(x, z, APART) || Capital.near(seed, x - 100, z - 100, 200, 0) || !Terrain.free(seed, x - 100, z - 100, 200)) {
                        continue;
                    }
                    boolean same = true;
                    for (int i = -1; i <= 1 && same; i++) {
                        for (int j = -1; j <= 1 && same; j++) {
                            same = Region.of(seed, bx + i, bz + j) == region;
                        }
                    }
                    if (same) {
                        return new int[] {x, z};
                    }
                }
            }
        }
        return null;
    }

    private static void onTickPre(ServerTickEvent.Pre event) {
        if (ticks != null) {
            started = System.nanoTime();
        }
    }

    private static void onTickPost(ServerTickEvent.Post event) {
        if (ticks == null || started == 0) {
            return;
        }
        ticks.add(System.nanoTime() - started);
        if (ticks.size() < want) {
            return;
        }
        long[] t = ticks.stream().mapToLong(Long::longValue).sorted().toArray();
        ticks = null;
        started = 0;
        double mean = Arrays.stream(t).average().orElse(0) / 1e6;
        int mobs = 0;
        int ours = 0;
        java.util.Map<String, Integer> kinds = new java.util.HashMap<>();
        ServerLevel level = event.getServer().getLevel(IfWorld.KEY);
        if (level != null) {
            for (Entity e : level.getAllEntities()) {
                mobs++;
                kinds.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 1, Integer::sum);
                if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NookOfTheDead.MODID)) {
                    ours++;
                }
            }
        }
        say(asked, String.format("tick over %d: mean %.2f ms, middle %.2f, 95%% %.2f, slowest %.2f; the If's entities %d, ours %d; heap %d MB",
                t.length, mean, t[t.length / 2] / 1e6, t[(int) (t.length * 0.95)] / 1e6, t[t.length - 1] / 1e6, mobs, ours,
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20) + "; "
                + kinds.entrySet().stream().sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed()).limit(8)
                        .map(k -> k.getKey() + " " + k.getValue()).collect(java.util.stream.Collectors.joining(", ")));
    }

    private static long heapMb() {
        System.gc();
        System.gc();
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private static long gcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private static boolean fresh(int x, int z, int apart) {
        return USED.stream().noneMatch(u -> Math.abs(u[0] - x) < apart && Math.abs(u[1] - z) < apart);
    }

    private static void say(CommandSourceStack source, String text) {
        NookOfTheDead.LOGGER.info("[bench] {}", text);
        if (source != null) {
            source.sendSuccess(() -> Component.literal(text), false);
        }
    }

    private Bench() {}
}
