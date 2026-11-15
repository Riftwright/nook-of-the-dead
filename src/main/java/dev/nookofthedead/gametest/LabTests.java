package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Refuge;
import dev.nookofthedead.world.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The refuge's machine room under the institute (institute_deep), a Velarium line of Nookworks' machines found standing
 * still: one lever starts its fifteen Generators and the whole line runs, filling its chests (the Nookworks session's
 * spec, 2026-10-19). Asked by the user (2026-11-14) with Nookworks 1.6 out and 1.7 on the way: 「地下研究室の避難所に置いてある
 * 機械が、動かないものがある気がする」 - so the room is set down as the If sets it, the lever pulled, and the line watched for
 * three minutes: still filling its chests in the last of them, its mercury going round.
 */
final class LabTests {
    /** How long the line is watched, and the stretch at the end of it in which it must still be filling its chests. */
    static final int RUN = 3600, LATE = 1200;

    static void register() {
        test("lab_the_refuges_line_runs_from_its_lever", RUN + 400, helper -> line(helper, false));
        // a room made before its towers were piped (a world from 1.1 or before) given them while it runs, as Refuge.towerRefit
        // does when a player comes near
        test("lab_an_old_rooms_towers_piped_in_play", RUN + 400, helper -> line(helper, true));
    }

    private static void line(GameTestHelper helper, boolean old) {
        ServerLevel level = helper.getLevel();
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElseThrow();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        // well off the arena, low as it lies under an institute
        BlockPos under = new BlockPos(corner.getX() + (old ? 480 : 400), level.getMinY() + 2, corner.getZ());
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
        BoundingBox box = deep.getBoundingBox(settings, under);
        List<long[]> forced = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                forced.add(new long[] {cx, cz});
            }
        }
        deep.placeInWorld(level, under, under, settings, RandomSource.create(1), 2);
        Refuge.workshop(level, under, settings, box);
        if (!old) {
            Refuge.towerPipes(level, under, settings, box, 2);
        }
        BlockPos lever = null;
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY() + 8, box.maxZ())) {
            if (level.getBlockState(at).getBlock() instanceof LeverBlock) {
                lever = at.immutable();
                break;
            }
        }
        check(helper, lever != null, "no lever in the machine room");
        BlockPos pulled = lever;
        int[] chests = new int[3];      // the chests' items as the lever is pulled, LATE before the end, at the end
        int[] most = new int[1];        // the most mercury any Distiller holds at the end
        List<String> log = new ArrayList<>();
        helper.runAfterDelay(20, () -> {
            BlockState state = level.getBlockState(pulled);
            ((LeverBlock) state.getBlock()).pull(state, level, pulled, null);
            chests[0] = look(level, box, log, "pulled");
        });
        if (old) {
            helper.runAfterDelay(200, () -> Refuge.towerPipes(level, under, settings, BoundingBox.infinite(), 3));
        }
        for (int t = 400; t < RUN; t += 400) {
            int at = t;
            helper.runAfterDelay(20 + at, () -> {
                int now = look(level, box, log, at / 20 + " s");
                if (at == RUN - LATE) {
                    chests[1] = now;
                }
            });
        }
        helper.runAfterDelay(20 + RUN, () -> {
            chests[2] = look(level, box, log, "end");
            most[0] = held(level, box);
            log.forEach(NookOfTheDead.LOGGER::info);
            for (long[] c : forced) {
                level.setChunkForced((int) c[0], (int) c[1], false);
            }
            check(helper, chests[1] > chests[0], "the line filled nothing in its first " + (RUN - LATE) / 20 + " s: " + log.get(log.size() - 1));
            check(helper, chests[2] > chests[1], "the line stood still in its last " + LATE / 20 + " s (" + chests[1] + " -> " + chests[2]
                    + " items in its chests): " + log.get(log.size() - 1));
            // a Distiller whose mercury does not go back to the line fills (4,000 mB) and stops: Nookworks 1.6 sends it out at the
            // Condenser only (Refuge.towerPipes)
            check(helper, most[0] < 500, "a Distiller kept its mercury (" + most[0] + " mB): it does not go back to the line");
            helper.succeed();
        });
    }

    /** The most mercury any Vacuum Distiller of the line holds. */
    private static int held(ServerLevel level, BoundingBox box) {
        int most = 0;
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.minY() + 8, box.maxZ())) {
            BlockEntity be = level.getBlockEntity(at);
            if (be != null && "nookworks:distiller".equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString())) {
                most = Math.max(most, be.saveWithoutMetadata(level.registryAccess()).getIntOr("Mercury", 0));
            }
        }
        return most;
    }

    /**
     * The items in the room's chests, and a line for the log: the Generators burning, and the mercury each machine and
     * tank of the line holds.
     */
    private static int look(ServerLevel level, BoundingBox box, List<String> log, String when) {
        int items = 0, burning = 0, generators = 0;
        StringBuilder distillers = new StringBuilder(), stirring = new StringBuilder(), tanks = new StringBuilder();
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockEntity be = level.getBlockEntity(at);
            if (be == null) {
                continue;
            }
            if (be instanceof ChestBlockEntity chest) {
                items += count(chest);
                continue;
            }
            String id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
            if (!id.startsWith("nookworks:") || at.getY() > box.minY() + 8) {
                continue;
            }
            CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
            switch (id) {
                case "nookworks:generator" -> {
                    generators++;
                    if (tag.getIntOr("BurnTime", 0) > 0) {
                        burning++;
                    }
                }
                case "nookworks:distiller" -> distillers.append(' ').append(tag.getIntOr("Mercury", 0)).append('/').append(tag.getIntOr("Progress", 0));
                case "nookworks:stirring_tank" -> stirring.append(' ').append(tag.getIntOr("Mercury", 0)).append('/').append(tag.getIntOr("Progress", 0));
                case "nookworks:mercury_tank" -> tanks.append(' ').append(tag.getIntOr("Mercury", 0));
                default -> { }
            }
        }
        log.add("machine room, " + when + ": " + items + " items in chests; generators " + burning + "/" + generators
                + " burning; distillers (mercury/progress)" + distillers + "; stirring tanks" + stirring + "; mercury tanks" + tanks);
        return items;
    }

    private static int count(Container container) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            n += container.getItem(i).getCount();
        }
        return n;
    }

    private LabTests() {}
}
