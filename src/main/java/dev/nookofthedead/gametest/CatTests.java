package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The infected cat (the user, 2026-10-23): up onto what the dead cannot climb. */
final class CatTests {
    static void register() {
        // its prey up on a wall three high, two blocks off: it leaps up after them
        test("an_infected_cat_leaps_up_a_wall", 120, helper -> {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
                }
            }
            var player = ZwGameTests.player(helper, net.minecraft.world.level.GameType.SURVIVAL);
            player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 4, 2))));
            var cat = helper.spawn(ZwEntities.INFECTED_CAT.get(), new BlockPos(2, 1, 2));
            cat.setTarget(player);
            double top = player.getY() - 0.1;
            helper.succeedWhen(() -> {
                cat.setTarget(player);
                helper.assertTrue(cat.getY() >= top, "the cat up on the wall: " + cat.getY() + " < " + top + ", target " + cat.getTarget() + ", leaps " + cat.leaps
                        + ", ground " + cat.onGround() + ", sees " + cat.getSensing().hasLineOfSight(player));
                cat.discard();
                ZwGameTests.leave(helper, player);
            });
        });
    }
}
