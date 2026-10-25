package dev.nookofthedead.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * For checking the If's ground as made, not as planned (the user, 2026-10-24: "チャンクの境目に直線的で不自然な崖とか、変に
 * 陥没したり盛り上がってる道路とかないか"): the square of chunks round a point made in full, then
 * <ul>
 * <li><b>seams</b> - a step of {@link #STEP} or more in the ground right across a chunk's edge, the ground flat on both sides
 *     of it, along {@link #RUN} or more of the edge: a cliff no hill would make. The same is counted across the middles of
 *     the chunks, as a measure of what the town's own walls and terraces give anyway;</li>
 * <li><b>roads</b> - in the town, a street's column more than {@link #OFF} off the height the town planned for it.</li>
 * </ul>
 * The ground is the top block that is ground (earth, sand, stone, a street's paving), looked for down from the top through
 * what stands on it. Written to {@code nzw_seams.txt} in the game's folder; the worst are told in the chat.
 */
public final class SeamScan {
    static final int STEP = 3;
    static final int RUN = 4;
    static final int OFF = 2;
    /** How far down from the top the ground is looked for, through a building. */
    static final int DOWN = 24;

    /** A run along one line: where it starts, which way the line goes, how long, the biggest step. */
    public record Run(int x, int z, boolean alongZ, int length, int step, String region, boolean edge) {}

    /** What one scan found. */
    public record Report(List<Run> seams, List<Run> middles, List<BlockPos> roads, int columns) {}

    public static Report scan(ServerLevel level, int centreX, int centreZ, int radius) {
        int cx0 = (centreX >> 4) - radius;
        int cz0 = (centreZ >> 4) - radius;
        int n = (2 * radius + 1) * 16;
        int x0 = cx0 * 16;
        int z0 = cz0 * 16;
        for (int cx = cx0; cx < cx0 + 2 * radius + 1; cx++) {
            for (int cz = cz0; cz < cz0 + 2 * radius + 1; cz++) {
                level.getChunk(cx, cz);                                     // made in full
            }
        }
        int[][] h = new int[n][n];
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                h[a][b] = ground(level, x0 + a, z0 + b);
            }
        }
        List<Run> seams = new ArrayList<>();
        List<Run> middles = new ArrayList<>();
        for (int line = 2; line < n - 2; line++) {
            boolean edge = line % 16 == 0;
            boolean middle = line % 16 == 8;
            if (!edge && !middle) {
                continue;
            }
            List<Run> into = edge ? seams : middles;
            runs(level, h, n, x0, z0, line, true, edge, into);              // across x = line
            runs(level, h, n, x0, z0, line, false, edge, into);             // across z = line
        }
        List<BlockPos> roads = new ArrayList<>();
        long seed = level.getSeed();
        for (int tx = 0; tx < n / 16; tx++) {
            for (int tz = 0; tz < n / 16; tz++) {
                int px = x0 + tx * 16;
                int pz = z0 + tz * 16;
                Region region = Region.at(seed, px + 8, pz + 8);
                if (region != Region.CITY && region != Region.SETTLEMENT) {
                    continue;
                }
                Town.Plan plan = Town.plan(seed, px - 1, pz - 1);
                for (int a = 1; a <= 16; a++) {
                    for (int b = 1; b <= 16; b++) {
                        if (plan.kind()[a][b] != Town.Ground.STREET || plan.built()[a][b]) {
                            continue;
                        }
                        int x = plan.x0() + a;
                        int z = plan.z0() + b;
                        int got = h[x - x0][z - z0];
                        if (got != Integer.MIN_VALUE && Math.abs(got - plan.top()[a][b]) > OFF && !sunk(seed, x, z)) {
                            roads.add(new BlockPos(x, got - plan.top()[a][b], z));
                        }
                    }
                }
            }
        }
        seams.sort((p, q) -> Integer.compare(q.length * q.step, p.length * p.step));
        middles.sort((p, q) -> Integer.compare(q.length * q.step, p.length * p.step));
        return new Report(seams, middles, roads, n * n);
    }

    /** The runs of steps across one line ({@code acrossX}: the line x = x0 + line, along z), into {@code into}. */
    private static void runs(ServerLevel level, int[][] h, int n, int x0, int z0, int line, boolean acrossX, boolean edge, List<Run> into) {
        int start = -1;
        int sign = 0;
        int big = 0;
        for (int k = 0; k <= n; k++) {
            int d = 0;
            boolean flat = false;
            if (k < n) {
                int before2 = acrossX ? h[line - 2][k] : h[k][line - 2];
                int before = acrossX ? h[line - 1][k] : h[k][line - 1];
                int after = acrossX ? h[line][k] : h[k][line];
                int after2 = acrossX ? h[line + 1][k] : h[k][line + 1];
                if (before != Integer.MIN_VALUE && after != Integer.MIN_VALUE && before2 != Integer.MIN_VALUE && after2 != Integer.MIN_VALUE) {
                    d = after - before;
                    flat = Math.abs(before - before2) <= 1 && Math.abs(after2 - after) <= 1;
                }
            }
            int s = flat && Math.abs(d) >= STEP ? Integer.signum(d) : 0;
            if (s != 0 && s == sign) {
                big = Math.max(big, Math.abs(d));
                continue;
            }
            if (sign != 0 && k - start >= RUN) {
                int x = acrossX ? x0 + line : x0 + start;
                int z = acrossX ? z0 + start : z0 + line;
                into.add(new Run(x, z, acrossX, k - start, big, Region.at(level.getSeed(), x, z).name(), edge));
            }
            sign = s;
            start = k;
            big = Math.abs(d);
        }
    }

    /** Whether ({@code x}, {@code z}) is in a sinkhole (meant: a street fallen into the galleries, {@link Tunnels#sinkholeAt}). */
    static boolean sunk(long seed, int x, int z) {
        int gx = Math.round((float) x / Tunnels.GRID);
        int gz = Math.round((float) z / Tunnels.GRID);
        return Math.abs(x - gx * Tunnels.GRID) <= Tunnels.SINK_REACH + 1 && Math.abs(z - gz * Tunnels.GRID) <= Tunnels.SINK_REACH + 1
                && Tunnels.sinkholeAt(seed, gx, gz);
    }

    /** The height of the ground's top in column ({@code x}, {@code z}) (the block above it), or MIN_VALUE if none is near the top. */
    static int ground(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top - 1, z);
        for (int k = 0; k < DOWN; k++, at.move(0, -1, 0)) {
            BlockState state = level.getBlockState(at);
            if (isGround(state)) {
                return at.getY() + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.PACKED_MUD) || state.is(Blocks.MUD) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.TERRACOTTA);
    }

    /** The report written to {@code nzw_seams.txt} (appended), and its first lines for the chat. */
    public static List<String> write(ServerLevel level, int x, int z, int radius, Report r) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("If seed %d, round %d %d (%d chunks a side): %d seams on chunk edges, %d on chunk middles (the town's own), %d street columns off",
                level.getSeed(), x, z, 2 * radius + 1, r.seams().size(), r.middles().size(), r.roads().size()));
        for (Run s : r.seams().subList(0, Math.min(40, r.seams().size()))) {
            lines.add(String.format("  seam %s %d %d %s %d long, step %d (%s)", s.alongZ() ? "x=" : "z=", s.x(), s.z(),
                    s.alongZ() ? "along z" : "along x", s.length(), s.step(), s.region()));
        }
        for (Run s : r.middles().subList(0, Math.min(10, r.middles().size()))) {
            lines.add(String.format("  middle %d %d %s %d long, step %d (%s)", s.x(), s.z(), s.alongZ() ? "along z" : "along x",
                    s.length(), s.step(), s.region()));
        }
        for (BlockPos p : r.roads().subList(0, Math.min(60, r.roads().size()))) {
            lines.add(String.format("  road %d %d off %+d", p.getX(), p.getZ(), p.getY()));
        }
        try {
            Files.writeString(Path.of("nzw_seams.txt"), String.join("\n", lines) + "\n\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            lines.add("(could not write nzw_seams.txt: " + e + ")");
        }
        return lines;
    }

    private SeamScan() {}
}
