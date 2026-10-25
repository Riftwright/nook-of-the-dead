package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.plain;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.nookofthedead.entity.BreakSoftBlocksGoal;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.registry.ZwEntities;
import dev.nookofthedead.registry.ZwTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The runners at a wall they cannot get through, from the fix check of review r1 (its probes, kept): how often a crowd
 * round a stone hut looks for a way in, the stairs up to a shut door left standing, an idle runner's errand breaking only
 * toward the mirror.
 */
final class SiegeTests {
    private SiegeTests() {}

    static void register() {
        test("runners_round_a_stone_hut_search_seldom", 600, SiegeTests::stoneHut);
        test("runner_climbs_stairs_to_a_shut_door", 600, SiegeTests::stairs);
        test("runner_on_an_errand_breaks_only_toward_the_mirror", 600, SiegeTests::errand);
    }

    /** Ground navigation that counts the ways it works out (not those it hands back as it has them). */
    static final class CountingNav extends GroundPathNavigation {
        int searches;

        CountingNav(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength) {
            Path current = this.getPath();
            if (current == null || current.isDone() || !targets.contains(this.getTargetPos())) {
                this.searches++;
            }
            return super.createPath(targets, radiusOffset, above, reachRange, maxPathLength);
        }
    }

    /**
     * Sixty runners round a shut stone hut, a player inside: between ticks 100 and 400 they look for a way in no more than
     * about once in five seconds each (vanilla's attack looked once a second or more: about 400 to 700 searches here, each
     * run to its limit - the server's time went on it).
     */
    private static void stoneHut(GameTestHelper helper) {
        for (int x = -12; x <= 18; x++) {
            for (int z = -12; z <= 18; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y = 1; y <= 3; y++) {
                    boolean wall = x == 1 || x == 5 || z == 1 || z == 5 || y == 3;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        ServerPlayer prey = player(helper, GameType.SURVIVAL);
        prey.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
        List<MutatedZombie> runners = new ArrayList<>();
        List<CountingNav> navs = new ArrayList<>();
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 60; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 6 + random.nextDouble() * 7;
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new Vec3(3.5 + Math.cos(a) * d, 1, 3.5 + Math.sin(a) * d)));
            CountingNav nav = new CountingNav(runner, helper.getLevel());
            try {
                var field = Mob.class.getDeclaredField("navigation");
                field.setAccessible(true);
                field.set(runner, nav);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            navs.add(nav);
            runner.setTarget(prey);
            runners.add(runner);
        }
        helper.onEachTick(() -> runners.forEach(r -> {
            if (r.getTarget() == null) {
                r.setTarget(prey);
            }
        }));
        int[] at100 = {0};
        helper.runAfterDelay(100, () -> at100[0] = navs.stream().mapToInt(n -> n.searches).sum());
        helper.runAfterDelay(400, () -> {
            int searches = navs.stream().mapToInt(n -> n.searches).sum() - at100[0];
            runners.forEach(Entity::discard);
            leave(helper, prey);
            check(helper, searches <= 300, "60 runners worked out " + searches + " ways in 300 ticks (at most 300)");
            helper.succeed();
        });
    }

    /** A wooden stair up to a shut oak door, the prey behind it: the runner climbs and breaks the door, not the stairs. */
    private static void stairs(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int y = 1; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }
        var stair = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        helper.setBlock(new BlockPos(1, 1, 3), stair);
        helper.setBlock(new BlockPos(2, 2, 3), stair);
        helper.setBlock(new BlockPos(3, 3, 3), stair);
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        for (int x = 4; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.OPEN, false);
        helper.setBlock(new BlockPos(5, 4, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(5, 5, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        helper.setBlock(new BlockPos(7, 4, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(7, 5, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 6, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 6, 3), Blocks.STONE);
        ServerPlayer prey = player(helper, GameType.SURVIVAL);
        prey.snapTo(helper.absoluteVec(new Vec3(6.5, 4, 3.5)));
        MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new Vec3(0.5, 1, 3.5)));
        runner.setTarget(prey);
        helper.onEachTick(() -> {
            if (runner.getTarget() == null) {
                runner.setTarget(prey);
            }
            for (BlockPos p : new BlockPos[] {new BlockPos(1, 1, 3), new BlockPos(2, 2, 3), new BlockPos(3, 3, 3)}) {
                if (!helper.getBlockState(p).is(Blocks.OAK_STAIRS)) {
                    runner.discard();
                    leave(helper, prey);
                    throw helper.assertionException(Component.literal("a stair at " + p + " was broken"));
                }
            }
        });
        helper.succeedWhen(() -> {
            if (helper.getBlockState(new BlockPos(5, 4, 3)).is(Blocks.OAK_DOOR)) {
                throw helper.assertionException(Component.literal("the door still stands"));
            }
            runner.discard();
            leave(helper, prey);
        });
    }

    /**
     * An idle runner whose errand (the mirror) lies east of it behind stone, planks on its other sides: it breaks none of
     * them, they are not on its way.
     */
    private static void errand(GameTestHelper helper) {
        BlockPos mirror = new BlockPos(5, 1, 1);
        var state = dev.nookworks.registry.ModTravel.IF_MIRROR.get().defaultBlockState();
        helper.setBlock(mirror, state);
        helper.setBlock(mirror.above(), state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                DoubleBlockHalf.UPPER));
        BlockPos at = helper.absolutePos(mirror);
        BlockPos[] planks = {new BlockPos(0, 1, 1), new BlockPos(0, 2, 1), new BlockPos(1, 1, 0), new BlockPos(1, 2, 0),
                new BlockPos(1, 1, 2), new BlockPos(1, 2, 2)};
        for (BlockPos p : planks) {
            helper.setBlock(p, Blocks.OAK_PLANKS);
        }
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.STONE);
        MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new Vec3(1.5, 1, 1.5)));
        runner.goalSelector.addGoal(1, new BreakSoftBlocksGoal(runner, runner.mind(), level -> at));
        helper.runAfterDelay(400, () -> {
            int broken = 0;
            for (BlockPos p : planks) {
                broken += helper.getBlockState(p).is(Blocks.OAK_PLANKS) ? 0 : 1;
            }
            runner.discard();
            check(helper, broken == 0, broken + " planks not on its way were broken");
            helper.succeed();
        });
    }
}
