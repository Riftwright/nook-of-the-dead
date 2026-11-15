package dev.nookofthedead;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.nookofthedead.world.Markers;
import dev.nookofthedead.world.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /nzw view <building> <k>}: into the nearest {@code building} (a town template's name, or a story's), stood where a
 * room opens out most and looking down its longest clear way - for photographs that show a room, not a wall (the user,
 * 2026-11-15: 「中も撮り直してチェックして」). The places are its floors ranked by how far one sees from them, a few blocks
 * apart, one per storey before a second on any; {@code k} picks one.
 */
public final class RoomView {
    /** How far a look is followed. */
    static final double REACH = 20.0;

    static int run(ServerPlayer player, String name, int k) {
        ServerLevel level = player.level();
        Town.Lot lot = nearest(level, player, name);
        if (lot == null) {
            player.sendSystemMessage(Component.literal("No " + name + " near"));
            return 0;
        }
        Optional<StructureTemplate> template = lot.building().flatMap(id -> level.getServer().getStructureTemplateManager().get(id));
        if (template.isEmpty()) {
            return 0;
        }
        for (int x = lot.corner().getX(); x <= lot.corner().getX() + lot.size(); x += 16) {
            for (int z = lot.corner().getZ(); z <= lot.corner().getZ() + lot.size(); z += 16) {
                level.getChunk(x >> 4, z >> 4);
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        BlockPos origin = Town.origin(lot, template.get());
        record Spot(BlockPos at, double score, float yaw) {}
        List<Spot> spots = new ArrayList<>();
        for (BlockPos local : Markers.floors(template.get())) {
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || !roofed(level, at)) {
                continue;                                                // a room has a ceiling: no yards, no roofless ruins
            }
            Vec3 eye = new Vec3(at.getX() + 0.5, at.getY() + 1.6, at.getZ() + 0.5);
            double best = 0, sum = 0;
            float yaw = 0;
            int walled = 0, closed = 0;
            for (int d = 0; d < 16; d++) {
                float a = d * 22.5F;
                double run = ray(level, eye, a, lot);
                if (run < 0) {
                    continue;                                            // out of a window or a door: not a look at the room
                }
                sum += run;
                walled += d % 4 == 0 ? 1 : 0;
                closed += run <= 12 ? 1 : 0;
                if (run > best) {
                    best = run;
                    yaw = a;
                }
            }
            if (best >= 4 && walled >= 3 && closed >= 13) {              // a room: walls on three sides, closed nearly all round
                spots.add(new Spot(at, best + sum / 16, yaw));
            }
        }
        spots.sort(Comparator.comparingDouble(Spot::score).reversed());
        List<Spot> picked = new ArrayList<>();
        java.util.Set<Integer> storeys = new java.util.HashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (Spot s : spots) {
                boolean apart = picked.stream().allMatch(p -> p.at().distSqr(s.at()) >= 36);
                if (apart && (pass == 1 || storeys.add(s.at().getY() / 4))) {
                    if (!picked.contains(s)) {
                        picked.add(s);
                    }
                }
            }
        }
        if (picked.isEmpty()) {
            player.sendSystemMessage(Component.literal("No open room in " + name + " (" + lot.building().map(Object::toString).orElse("?") + ")"));
            return 0;
        }
        Spot s = picked.get(Math.floorMod(k, picked.size()));
        player.teleportTo(level, s.at().getX() + 0.5, s.at().getY() + 0.1, s.at().getZ() + 0.5, java.util.Set.<Relative>of(), s.yaw(), 12, true);
        player.sendSystemMessage(Component.literal(name + " (" + lot.building().map(b -> b.getPath()).orElse("?") + ") view " + Math.floorMod(k, picked.size()) + "/" + picked.size() + " at "
                + s.at().toShortString() + " looking " + (int) s.yaw()));
        return 1;
    }

    /** Whether something solid stands over {@code at} within five blocks: a ceiling. */
    static boolean roofed(ServerLevel level, BlockPos at) {
        for (int up = 2; up <= 6; up++) {
            var state = level.getBlockState(at.above(up));
            if (!state.getCollisionShape(level, at.above(up)).isEmpty()) {
                return !(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock);   // a tree's crown is not a ceiling
            }
        }
        return false;
    }

    /**
     * How far one sees from {@code eye} along the level at {@code yaw} before something stops the eye, up to
     * {@link #REACH}; -1 where the look goes out of {@code lot} first (through a window or a door) or reaches nothing.
     */
    static double ray(ServerLevel level, Vec3 eye, float yaw, Town.Lot lot) {
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int x0 = lot.corner().getX(), z0 = lot.corner().getZ(), x1 = x0 + lot.size() - 1, z1 = z0 + lot.size() - 1;
        for (double t = 0.5; t <= REACH; t += 0.5) {
            p.set(eye.x + dx * t, eye.y, eye.z + dz * t);
            if (p.getX() < x0 || p.getX() > x1 || p.getZ() < z0 || p.getZ() > z1) {
                return -1;
            }
            var state = level.getBlockState(p);
            if (state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                return -1;                                              // a pane, an open door: the eye goes on outside
            }
            if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty()) {
                return t;
            }
        }
        return -1;
    }

    /**
     * {@code /nzw look}: from where the player stands, to the place in the room about them (on this floor, under a ceiling,
     * within {@code reach}) where it opens out most, looking down its longest way - for rooms that are no town lot's (the
     * capital's houses).
     */
    static int around(ServerPlayer player, int reach) {
        ServerLevel level = player.level();
        BlockPos at0 = player.blockPosition();
        BlockPos best = null;
        double score = -1;
        float yaw = 0;
        for (BlockPos p : BlockPos.betweenClosed(at0.offset(-reach, -1, -reach), at0.offset(reach, 1, reach))) {
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !roofed(level, p)
                    || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                continue;
            }
            Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + 1.6, p.getZ() + 0.5);
            double longest = 0, sum = 0;
            float way = 0;
            int closed = 0;
            for (int d = 0; d < 16; d++) {
                float a = d * 22.5F;
                double run = rayIn(level, eye, a, at0, reach + 2);
                if (run < 0) {
                    continue;
                }
                closed++;
                sum += run;
                if (run > longest && d % 4 == 0) {                       // faced square to the walls: never into a corner's seam
                    longest = run;
                    way = a;
                }
            }
            double sc = closed >= 13 && longest >= 4 ? longest + sum / 16 : -1;
            if (sc > score) {
                score = sc;
                best = p.immutable();
                yaw = way;
            }
        }
        if (best == null) {
            player.sendSystemMessage(Component.literal("No room round here"));
            return 0;
        }
        // stand back against the wall behind, so the room is before the camera
        player.teleportTo(level, best.getX() + 0.5, best.getY() + 0.1, best.getZ() + 0.5, java.util.Set.<Relative>of(), yaw, 18, true);
        player.sendSystemMessage(Component.literal("look at " + best.toShortString() + " looking " + (int) yaw));
        return 1;
    }

    /** As {@link #ray}, bounded by a box {@code reach} about {@code centre} instead of a lot. */
    static double rayIn(ServerLevel level, Vec3 eye, float yaw, BlockPos centre, int reach) {
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double t = 0.5; t <= REACH; t += 0.5) {
            p.set(eye.x + dx * t, eye.y, eye.z + dz * t);
            if (Math.abs(p.getX() - centre.getX()) > reach || Math.abs(p.getZ() - centre.getZ()) > reach) {
                return -1;
            }
            var state = level.getBlockState(p);
            if (state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) {
                return -1;
            }
            if (!state.getCollisionShape(level, p).isEmpty()) {
                return t;
            }
        }
        return -1;
    }

    /** The nearest lot round {@code player} with the town building {@code name}. */
    static Town.Lot nearest(ServerLevel level, ServerPlayer player, String name) {
        Identifier id = NookOfTheDead.id("town/" + name.toLowerCase(java.util.Locale.ROOT));
        dev.nookofthedead.world.Stories.Story story = java.util.Arrays.stream(dev.nookofthedead.world.Stories.Story.values())
                .filter(st -> st.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        int bx0 = Math.floorDiv(player.getBlockX(), Town.BLOCK), bz0 = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        for (int r = 0; r <= 24; r++) {
            Town.Lot best = null;
            double nearest = Double.MAX_VALUE;
            for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                    if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r) {
                        continue;
                    }
                    for (Town.Lot lot : Town.lots(level.getSeed(), bx, bz)) {
                        double d = player.distanceToSqr(lot.corner().getX() + lot.size() / 2.0, player.getY(), lot.corner().getZ() + lot.size() / 2.0);
                        boolean is = story != null ? dev.nookofthedead.world.Stories.of(lot.building(), lot.seed()).filter(story::equals).isPresent()
                                && !dev.nookofthedead.world.Traces.burning(level.getSeed(), lot.corner().getX() + lot.size() / 2, lot.corner().getZ() + lot.size() / 2)
                                : lot.building().map(id::equals).orElse(false);
                        if (lot.use() == Town.Use.BUILDING && is && d < nearest) {
                            best = lot;
                            nearest = d;
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private RoomView() {}
}
