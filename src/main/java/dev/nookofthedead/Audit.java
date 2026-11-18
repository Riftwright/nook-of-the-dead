package dev.nookofthedead;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /nzw audit [radius]}: what looks wrong round a player, for the visual checks (the user, 2026-11-15:
 * 「浮いてるランタンや額縁がないか」「ブロックに埋まっている遺体がないか」): every block that could not stand where it is (a
 * lantern hung from nothing, a torch on air, a carpet over a hole - the block's own canSurvive), every frame, painting and
 * poster with nothing behind it or inside a block, every body sunk into a block. Counted by kind in chat; every place in
 * the log, a line each ({@code AUDIT ...}), to read back.
 */
public final class Audit {
    static int run(ServerPlayer player, int radius) {
        ServerLevel level = player.level();
        BlockPos at = player.blockPosition();
        Map<String, List<BlockPos>> found = new TreeMap<>();
        int x0 = at.getX() - radius, x1 = at.getX() + radius, z0 = at.getZ() - radius, z1 = at.getZ() + radius;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!level.isLoaded(p.set(x, at.getY(), z))) {
                    continue;
                }
                // the whole column from its top down through the buildings' cellars and the tunnels under them
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                for (int y = Math.max(level.getMinY(), top - 48); y <= top; y++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir() || state.canSurvive(level, p)) {
                        continue;
                    }
                    found.computeIfAbsent("unsupported " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + " on "
                            + BuiltInRegistries.BLOCK.getKey(level.getBlockState(p.below()).getBlock()).getPath(), k -> new ArrayList<>()).add(p.immutable());
                }
            }
        }
        floating(level, x0, x1, z0, z1, found);
        stairs(level, x0, x1, z0, z1, at.getY(), found);
        AABB around = new AABB(x0, level.getMinY(), z0, x1 + 1, level.getMaxY(), z1 + 1);
        for (Entity e : level.getEntities((Entity) null, around, e -> true)) {
            String kind = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (e instanceof HangingEntity hanging) {
                if (!hanging.survives()) {
                    found.computeIfAbsent("loose " + kind, k -> new ArrayList<>()).add(e.blockPosition());
                } else if (!level.getBlockState(e.blockPosition()).getCollisionShape(level, e.blockPosition()).isEmpty()) {
                    String what = e instanceof net.minecraft.world.entity.decoration.ItemFrame frame
                            ? " (" + BuiltInRegistries.ITEM.getKey(frame.getItem().getItem()).getPath() + (frame.isInvisible() ? ", invisible" : "") + ")" : "";
                    found.computeIfAbsent("in a block " + kind + " in " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(e.blockPosition()).getBlock()).getPath()
                            + what, k -> new ArrayList<>()).add(e.blockPosition());
                }
            } else if (e instanceof dev.nookofthedead.entity.Remains body) {
                // the body as it is drawn: lying, low and a block long along its way; sitting, a block high; a little in from its
                // edges, and over what a body may lie across (Markers.room: a carpet, a trapdoor, a rail, up to a fifth of a block)
                boolean lying = body.kind() == dev.nookofthedead.entity.Remains.LYING;
                double h = lying ? 0.35 : 0.9;
                Vec3 c = body.position();
                List<AABB> parts = new ArrayList<>();
                parts.add(new AABB(c.x - 0.25, c.y + 0.22, c.z - 0.25, c.x + 0.25, c.y + h, c.z + 0.25));
                if (lying) {
                    double yaw = Math.toRadians(body.getYRot());
                    double dx = -Math.sin(yaw) * 0.85, dz = Math.cos(yaw) * 0.85;
                    parts.add(new AABB(c.x + dx - 0.15, c.y + 0.22, c.z + dz - 0.15, c.x + dx + 0.15, c.y + h, c.z + dz + 0.15));
                    parts.add(new AABB(c.x - dx - 0.15, c.y + 0.22, c.z - dz - 0.15, c.x - dx + 0.15, c.y + h, c.z - dz + 0.15));
                }
                for (AABB part : parts) {
                    if (level.getBlockCollisions(body, part).iterator().hasNext()) {
                        BlockPos in = BlockPos.containing(part.getCenter());
                        String what = BuiltInRegistries.BLOCK.getKey(level.getBlockState(in).getBlock()).getPath();
                        found.computeIfAbsent("buried remains" + (lying ? " (lying)" : " (sitting)") + " in " + what, k -> new ArrayList<>()).add(e.blockPosition());
                        break;
                    }
                }
            }
        }
        int total = 0;
        for (var entry : found.entrySet()) {
            total += entry.getValue().size();
            List<BlockPos> where = entry.getValue();
            player.sendSystemMessage(Component.literal(entry.getKey() + ": " + where.size() + " (" + where.get(0).toShortString()
                    + (where.size() > 1 ? "; " + where.get(where.size() / 2).toShortString() : "") + ")"));
            for (BlockPos w : where) {
                NookOfTheDead.LOGGER.info("AUDIT {} at {} {} {}", entry.getKey(), w.getX(), w.getY(), w.getZ());
            }
        }
        int all = total;
        player.sendSystemMessage(Component.literal("audit within " + radius + ": " + all + " found"));
        NookOfTheDead.LOGGER.info("AUDIT done within {} of {}: {} found", radius, at.toShortString(), all);
        return 1;
    }

    /**
     * Whatever stands in the air (the user, 2026-11-18: a building's roof and top room hanging high over the town): every
     * lump of blocks within the square not joined to the ground, face to face, through other blocks - leaves, vines and
     * liquids not counted as blocks. What reaches the square's sides counts as held (its hold may be outside). A lump is
     * reported once, at its lowest block, with its size.
     */
    private static void floating(ServerLevel level, int x0, int x1, int z0, int z1, Map<String, List<BlockPos>> found) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!level.isLoaded(p.set(x, 0, z))) {
                    return;                                              // the whole square loaded, or no reading of it
                }
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                low = Math.min(low, top);
                high = Math.max(high, top);
            }
        }
        low = Math.max(level.getMinY(), low - 4);
        int nx = x1 - x0 + 1, ny = high - low + 1, nz = z1 - z0 + 1;
        java.util.BitSet solid = new java.util.BitSet(nx * ny * nz);
        for (int x = 0; x < nx; x++) {
            for (int z = 0; z < nz; z++) {
                for (int y = 0; y < ny; y++) {
                    BlockState state = level.getBlockState(p.set(x0 + x, low + y, z0 + z));
                    if (!state.isAir() && state.getFluidState().isEmpty() && !state.is(net.minecraft.tags.BlockTags.LEAVES)
                            && !state.is(net.minecraft.world.level.block.Blocks.VINE) && !state.is(net.minecraft.world.level.block.Blocks.GLOW_LICHEN)) {
                        solid.set((x * nz + z) * ny + y);
                    }
                }
            }
        }
        java.util.BitSet held = new java.util.BitSet(nx * ny * nz);
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = 0; x < nx; x++) {
            for (int z = 0; z < nz; z++) {
                for (int y = 0; y < ny; y++) {
                    int i = (x * nz + z) * ny + y;
                    if (solid.get(i) && (y == 0 || x == 0 || z == 0 || x == nx - 1 || z == nz - 1)) {
                        held.set(i);
                        queue.add(new int[] {x, y, z});
                    }
                }
            }
        }
        int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] f : faces) {
                int x = c[0] + f[0], y = c[1] + f[1], z = c[2] + f[2];
                if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) {
                    continue;
                }
                int i = (x * nz + z) * ny + y;
                if (solid.get(i) && !held.get(i)) {
                    held.set(i);
                    queue.add(new int[] {x, y, z});
                }
            }
        }
        java.util.BitSet seen = new java.util.BitSet(nx * ny * nz);
        for (int i = solid.nextSetBit(0); i >= 0; i = solid.nextSetBit(i + 1)) {
            if (held.get(i) || seen.get(i)) {
                continue;
            }
            // one lump: its size, its lowest block, the block there
            int size = 0;
            int[] lowest = null;
            seen.set(i);
            queue.add(new int[] {i / ny / nz, i % ny, (i / ny) % nz});
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                size++;
                if (lowest == null || c[1] < lowest[1]) {
                    lowest = c;
                }
                for (int[] f : faces) {
                    int x = c[0] + f[0], y = c[1] + f[1], z = c[2] + f[2];
                    if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) {
                        continue;
                    }
                    int j = (x * nz + z) * ny + y;
                    if (solid.get(j) && !held.get(j) && !seen.get(j)) {
                        seen.set(j);
                        queue.add(new int[] {x, y, z});
                    }
                }
            }
            BlockPos at = new BlockPos(x0 + lowest[0], low + lowest[1], z0 + lowest[2]);
            String what = BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).getPath();
            found.computeIfAbsent((size >= 20 ? "floating lump " : "floating bit ") + what + " x" + size, k -> new ArrayList<>()).add(at);
        }
    }

    /**
     * Flights that stop a block short (the user, 2026-11-18, of the institutes' stairs: the last stair under the floor's
     * edge, a whole block to climb from it - "Bの方が自然"): a stair, the right way up, with room over it, whose way up
     * meets a whole block with a floor's room over it - one more stair belongs where that block is, level with the floor.
     */
    private static void stairs(ServerLevel level, int x0, int x1, int z0, int z1, int y, Map<String, List<BlockPos>> found) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!level.isLoaded(p.set(x, y, z))) {
                    continue;
                }
                for (int h = Math.max(level.getMinY(), y - 48); h <= Math.min(level.getMaxY() - 4, y + 48); h++) {
                    BlockState state = level.getBlockState(p.set(x, h, z));
                    if (!(state.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
                            || state.getValue(net.minecraft.world.level.block.StairBlock.HALF) != net.minecraft.world.level.block.state.properties.Half.BOTTOM) {
                        continue;
                    }
                    net.minecraft.core.Direction up = state.getValue(net.minecraft.world.level.block.StairBlock.FACING);
                    BlockPos stair = p.immutable();
                    BlockPos edge = stair.relative(up).above();                  // where the floor begins, as the flight climbs
                    if (open(level, stair.above()) && whole(level, edge) && open(level, edge.above())) {
                        found.computeIfAbsent("stair a block short (up " + up.getName() + ") of " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(edge).getBlock()).getPath(),
                                k -> new ArrayList<>()).add(stair);
                    }
                }
            }
        }
    }

    private static boolean open(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
    }

    private static boolean whole(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return !(state.getBlock() instanceof net.minecraft.world.level.block.StairBlock) && state.isFaceSturdy(level, at, net.minecraft.core.Direction.UP)
                && state.getCollisionShape(level, at).max(net.minecraft.core.Direction.Axis.Y) >= 0.99;
    }

    private Audit() {}
}
