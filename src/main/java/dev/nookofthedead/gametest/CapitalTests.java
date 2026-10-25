package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.world.Capital;
import dev.nookofthedead.world.Region;
import dev.nookofthedead.world.Town;

/** The capital (2026-10-24): walled, built close, its streets winding; its edge easing into the land round it. */
final class CapitalTests {
    static void register() {
        // inside the wall, the most of it houses wall to wall, a good part streets and squares; the wall unbroken but for
        // its gates, the river's way through and where it fell; the castle on the crag in the middle
        test("the_capital_is_walled_and_built_close", helper -> {
            for (long seed : new long[] {20261025L, -77L, 5150L}) {
                int[] c = Capital.centre(seed);
                int built = 0;
                int ways = 0;
                int all = 0;
                for (int dx = -260; dx <= 260; dx += 3) {
                    for (int dz = -260; dz <= 260; dz += 3) {
                        double r = Math.hypot(dx, dz);
                        if (r > Capital.WALL - 12 || r < 90) {
                            continue;
                        }
                        Capital.Column col = Capital.column(seed, c[0] + dx, c[1] + dz);
                        check(helper, col != null, "inside the capital");
                        all++;
                        built += col.kind() == Capital.Kind.BUILDING ? 1 : 0;
                        ways += col.kind() == Capital.Kind.STREET || col.kind() == Capital.Kind.SQUARE ? 1 : 0;
                    }
                }
                check(helper, built > all * 0.5, "built close: " + built + " of " + all);
                check(helper, ways > all * 0.15 && ways < all * 0.45, "streets enough: " + ways + " of " + all);
                Capital.Column middle = Capital.column(seed, c[0], c[1]);
                check(helper, middle.kind() == Capital.Kind.CASTLE, "the castle in the middle: " + middle.kind());
                int wall = 0;
                for (int k = 0; k < 720; k++) {
                    double a = k * Math.PI / 360;
                    Capital.Column col = Capital.column(seed, (int) Math.floor(c[0] + Math.cos(a) * (Capital.WALL + 1.5)),
                            (int) Math.floor(c[1] + Math.sin(a) * (Capital.WALL + 1.5)));
                    wall += col.kind() == Capital.Kind.WALL ? 1 : 0;
                }
                check(helper, wall > 600, "the wall all round: " + wall);
                check(helper, Region.at(seed, c[0], c[1]) == Region.CAPITAL, "its own region");
            }
            helper.succeed();
        });

        // where the capital ends, its ground is the town's, to the block: no step at its edge
        test("the_capital_eases_into_the_land", helper -> {
            long seed = 20261025L;
            int[] c = Capital.centre(seed);
            for (int k = 0; k < 360; k++) {
                double a = k * Math.PI / 180;
                int x = (int) Math.floor(c[0] + Math.cos(a) * (Capital.ZONE - 1.2));
                int z = (int) Math.floor(c[1] + Math.sin(a) * (Capital.ZONE - 1.2));
                Capital.Column col = Capital.column(seed, x, z);
                if (col == null || col.kind() != Capital.Kind.APRON) {
                    continue;
                }
                Town.Plan plan = Town.plan(seed, x - 1, z - 1);
                double h = Town.height(seed, x, z);
                check(helper, Math.abs(Town.height(seed, x + 1, z) - h) <= 0.6, "the land's ground is smooth across the capital's edge at " + x + "," + z);
                check(helper, plan.top()[1][1] > Town.SURFACE - 12 && plan.top()[1][1] < Town.SURFACE + 25, "a planned ground there");
            }
            helper.succeed();
        });

        // the road up the crag goes from the avenue to the castle's gate, a step at a time
        test("the_road_up_the_crag_is_walked", helper -> {
            for (long seed : new long[] {20261025L, -77L, 5150L, 621465L, 1L}) {
                String problem = Capital.rampProblem(seed);
                check(helper, problem == null, "seed " + seed + ": " + problem);
            }
            helper.succeed();
        });

        // a crow wheeling over someone out in the open drops on them, pecks, and is off up again; the game's spawning never
        // brings one (they are the capital's own flocking's)
        test("a_crow_drops_on_someone_in_the_open", 400, helper -> {
            var prey = dev.nookofthedead.gametest.ZwGameTests.player(helper, net.minecraft.world.level.GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
            var crow = helper.spawn(dev.nookofthedead.registry.ZwEntities.CAPITAL_CROW.get(), new net.minecraft.core.BlockPos(1, 8, 1));
            crow.roost(helper.absolutePos(new net.minecraft.core.BlockPos(1, 9, 1)));
            crow.readyToDrop();
            check(helper, dev.nookofthedead.registry.ZwEntities.CAPITAL_CROW.get().getCategory() == net.minecraft.world.entity.MobCategory.AMBIENT,
                    "not of the monsters' share");
            helper.succeedWhen(() -> {
                if (prey.getHealth() >= prey.getMaxHealth()) {
                    crow.readyToDrop();
                    throw helper.assertionException(net.minecraft.network.chat.Component.literal("not pecked yet"));
                }
                crow.discard();
                dev.nookofthedead.gametest.ZwGameTests.leave(helper, prey);
            });
        });

        // the king, shut in his room at the top of the keep, is kept there however far off everyone is: not taken for one
        // a player penned (in game, 2026-10-24: the pen rule took him the first time no one was near)
        test("the_king_keeps_his_room", helper -> {
            var server = helper.getLevel().getServer();
            dev.nookworks.ifworld.IfWorld world = dev.nookworks.ifworld.IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            net.minecraft.server.level.ServerLevel ifLevel = dev.nookworks.ifworld.IfLevel.open(server);
            net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(4100, 140, 4100);
            ifLevel.getChunk(at);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 3; dy++) {
                        boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                        ifLevel.setBlock(at.offset(dx, dy, dz), (shell ? net.minecraft.world.level.block.Blocks.STONE_BRICKS
                                : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 2);
                    }
                }
            }
            var king = dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get().create(ifLevel,
                    net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            king.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            king.setPersistenceRequired();
            king.addTag(dev.nookofthedead.entity.Unkept.KEPT);
            ifLevel.addFreshEntity(king);
            try {
                king.checkDespawn();
                check(helper, !king.isRemoved(), "the king stays in his room");
                check(helper, !dev.nookofthedead.entity.Unkept.held(king), "not held");
            } finally {
                king.discard();
            }
            helper.succeed();
        });
    }

    private CapitalTests() {}
}
