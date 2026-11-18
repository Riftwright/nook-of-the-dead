package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Flights that stopped a block short of their floor (the user, 2026-11-18, of the institutes' stairs: the last stair under
 * the floor's edge, a whole block to climb from it - "Bの方が自然"): one more stair where the floor begins, level with it.
 * The institute's two flights up to its upper floor, the refuge's stair down from the machine hall and the tower's stair
 * onto its second relay are drawn so since 1.2.2; one made before is given its stair the first time a player comes near
 * ({@link #refit}, once only, kept with the world).
 */
public final class Steps {
    /** The institute's two flights up to its upper floor, two wide: their top stairs, in the template's own blocks. */
    static final List<BlockPos> INSTITUTE = List.of(new BlockPos(21, 9, 22), new BlockPos(21, 9, 23), new BlockPos(42, 9, 22),
            new BlockPos(42, 9, 23));
    /** The refuge's stair down from the machine hall: its top stair as drawn before 1.2.2, in the deep part's own blocks. */
    static final BlockPos REFUGE = new BlockPos(34, 0, 51);
    /** How near a player comes before a flight made before is finished. */
    static final int NEAR = 48;

    /**
     * If {@code top} is a stair, the right way up, whose way up meets a whole block with room over it (the floor's edge, a
     * block over the stair): that block made a stair like it, level with the floor. Whether it was.
     */
    public static boolean finish(LevelAccessor level, BlockPos top) {
        BlockState stair = level.getBlockState(top);
        if (!(stair.getBlock() instanceof StairBlock) || stair.getValue(StairBlock.HALF) != Half.BOTTOM) {
            return false;
        }
        BlockPos edge = top.relative(stair.getValue(StairBlock.FACING)).above();
        BlockState there = level.getBlockState(edge);
        if (there.getBlock() instanceof StairBlock || !there.isFaceSturdy(level, edge, Direction.UP)
                || !level.getBlockState(edge.above()).getCollisionShape(level, edge.above()).isEmpty()) {
            return false;
        }
        level.setBlock(edge, stair, Block.UPDATE_ALL);
        return true;
    }

    /** Where the tower's stair now ends under its second relay (before 1.2.2): its top stairs, one a lane. */
    static List<BlockPos> towerTops(BlockPos centre) {
        List<BlockPos> tops = new ArrayList<>();
        for (int lane = Tower.LANE_IN; lane <= Tower.LANE_OUT; lane++) {
            tops.add(centre.relative(Direction.WEST, lane).relative(Direction.SOUTH, 3).atY(Tower.R2 - 1));
        }
        return tops;
    }

    /** The flights near {@code player} made before 1.2.2 finished: the institute's and its refuge's, the tower's. */
    static void refit(ServerPlayer player) {
        ServerLevel level = player.level();
        if (!NookOfTheDead.fallen(level)) {
            return;
        }
        Refuge.Retrofitted done = level.getServer().getDataStorage().computeIfAbsent(Refuge.Retrofitted.TYPE);
        String number = dev.nookworks.ifworld.IfWorld.get(level.getServer()).number();
        long seed = level.getSeed();
        int bx = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        Town.Lot lot = Town.lots(seed, bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        if (lot != null && lot.footprint().inflatedBy(NEAR).isInside(player.getBlockX(), lot.corner().getY(), player.getBlockZ())) {
            String key = "steps:" + number + ":" + bx + "," + bz;
            var manager = level.getServer().getStructureTemplateManager();
            StructureTemplate template = manager.get(Town.INSTITUTE).orElse(null);
            StructureTemplate deep = manager.get(Town.INSTITUTE_DEEP).orElse(null);
            if (!done.refuges.contains(key) && template != null && deep != null) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
                List<BlockPos> tops = new ArrayList<>();
                BlockPos origin = Town.origin(lot, template);
                for (BlockPos p : INSTITUTE) {
                    tops.add(StructureTemplate.calculateRelativePosition(settings, p).offset(origin));
                }
                tops.add(StructureTemplate.calculateRelativePosition(settings, REFUGE).offset(Town.deepOrigin(lot, deep)));
                finishAll(level, tops, done, key, "the institute's");
            }
        }
        BlockPos centre = Tower.centre(Ascent.site(seed));
        if (Math.abs(player.getBlockX() - centre.getX()) <= NEAR && Math.abs(player.getBlockZ() - centre.getZ()) <= NEAR
                && Math.abs(player.getBlockY() - Tower.R2) <= NEAR) {
            String key = "steps:tower:" + number;
            if (!done.refuges.contains(key)) {
                finishAll(level, towerTops(centre), done, key, "the tower's");
            }
        }
    }

    private static void finishAll(ServerLevel level, List<BlockPos> tops, Refuge.Retrofitted done, String key, String what) {
        for (BlockPos top : tops) {
            if (!level.isLoaded(top) || !level.isLoaded(top.above(2).relative(Direction.NORTH, 2)) || !level.isLoaded(top.above(2).relative(Direction.SOUTH, 2))
                    || !level.isLoaded(top.above(2).relative(Direction.EAST, 2)) || !level.isLoaded(top.above(2).relative(Direction.WEST, 2))) {
                return;                                                      // all of it loaded, or none of it touched
            }
        }
        int n = 0;
        for (BlockPos top : tops) {
            n += finish(level, top) ? 1 : 0;
        }
        done.refuges.add(key);
        done.setDirty();
        if (n > 0) {
            NookOfTheDead.LOGGER.info("{} {} stair(s) finished at their floors (made before 1.2.2)", what, n);
        }
    }

    private Steps() {}
}
