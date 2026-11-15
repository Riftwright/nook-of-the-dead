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

    private Audit() {}
}
