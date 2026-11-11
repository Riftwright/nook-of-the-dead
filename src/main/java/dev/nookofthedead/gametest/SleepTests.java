package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.List;

import dev.nookofthedead.ending.Film;
import dev.nookofthedead.ending.Opening;
import dev.nookworks.ifworld.IfGates;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Sleeping in the If, as Nookworks has it (its {@code if_sleepers_count_with_the_overworlds_players}): the If's nights are
 * the overworld's, and so is the vote to sleep one through. Asked by Nookworks' review (2026-11-11), whose test of it
 * failed with this mod in: a sleeper there was woken.
 */
final class SleepTests {
    static void register() {
        // one who has seen the opening, asleep in the If while another is awake at home, sleeps on; both asleep, the night
        // passes and both wake
        test("if_sleepers_count_with_the_overworlds_players", 400, helper -> sleepers(helper, true));
        // one in the If for the first time is in the opening film: held where they came in, not let lie in a bed meanwhile
        test("if_the_opening_holds_a_newcomer_out_of_bed", 400, helper -> sleepers(helper, false));
    }

    private static void sleepers(GameTestHelper helper, boolean seenTheOpening) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        IfWorld world = IfWorld.get(server);
        while (!world.rolled()) {
            world.addThrow(1 + level.getRandom().nextInt(6));
        }
        ServerLevel ifLevel = IfLevel.open(server);
        var rules = server.overworld().getGameRules();
        int percentWas = rules.get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        rules.set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 100, server);
        long[] clockWas = {-1};
        level.dimensionTypeRegistration().value().defaultClock().ifPresent(clock -> {
            clockWas[0] = server.clockManager().getInstance(clock).totalTicks();
            server.clockManager().setTotalTicks(clock, 18_000);      // midnight
        });
        Runnable putBack = () -> {
            rules.set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, percentWas, server);
            level.dimensionTypeRegistration().value().defaultClock().ifPresent(clock -> server.clockManager().setTotalTicks(clock, clockWas[0]));
        };
        ServerPlayer awake = player(helper, GameType.SURVIVAL);
        awake.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        ServerPlayer sleeper = player(helper, GameType.SURVIVAL);
        sleeper.setData(Opening.SEEN, seenTheOpening);
        BlockPos ifBed = new BlockPos(8, ifLevel.getMaxY() - 8, 8);
        for (BlockPos floor : List.of(ifBed.below(), ifBed.south().below(), ifBed.east().below())) {
            ifLevel.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        }
        bed(ifLevel, ifBed);
        IfGates.passing(() -> sleeper.teleport(new TeleportTransition(ifLevel, Vec3.atBottomCenterOf(ifBed.east()), Vec3.ZERO, 0, 0,
                TeleportTransition.DO_NOTHING)));
        check(helper, sleeper.level() == ifLevel, "one player in the If");
        // a test's players have no connection to tick them: a sleeper is ticked here, or would never sleep long enough
        helper.onEachTick(() -> {
            for (ServerPlayer player : List.of(sleeper, awake)) {
                if (player.isSleeping() || Film.watching(player)) {
                    player.doTick();
                }
            }
        });
        helper.runAfterDelay(5, () -> {
            check(helper, Film.watching(sleeper) != seenTheOpening, seenTheOpening ? "the opening played again" : "no opening for a newcomer");
            sleeper.startSleeping(ifBed);
            ifLevel.updateSleepingPlayerList();
            helper.runAfterDelay(120, () -> {
                if (!seenTheOpening) {
                    check(helper, !sleeper.isSleeping() && Film.watching(sleeper), "a newcomer slept through the opening: "
                            + sleeper.isSleeping() + " / " + Film.watching(sleeper));
                    Film.end(sleeper);
                    done(helper, ifLevel, ifBed, putBack, sleeper, awake);
                    return;
                }
                check(helper, sleeper.isSleeping(), "asleep in the If, with the overworld's player awake: no night skipped, still asleep");
                BlockPos home = helper.absolutePos(new BlockPos(3, 1, 3));
                bed(level, home);
                awake.startSleeping(home);
                level.updateSleepingPlayerList();
                helper.runAfterDelay(120, () -> {
                    check(helper, !sleeper.isSleeping() && !awake.isSleeping(),
                            "both asleep, the night passes and both wake: " + sleeper.isSleeping() + " / " + awake.isSleeping()
                                    + "; on the server: " + server.getPlayerList().getPlayers().stream()
                                    .map(p -> p.getGameProfile().name() + "@" + p.level().dimension().identifier() + (p.isSleeping() ? " asleep" : " awake")
                                            + (p.isSpectator() ? " spectating" : ""))
                                    .toList());
                    done(helper, ifLevel, ifBed, putBack, sleeper, awake);
                });
            });
        });
    }

    private static void done(GameTestHelper helper, ServerLevel ifLevel, BlockPos ifBed, Runnable putBack, ServerPlayer... players) {
        ifLevel.removeBlock(ifBed, false);
        ifLevel.removeBlock(ifBed.south(), false);
        putBack.run();
        for (ServerPlayer player : players) {
            leave(helper, player);
        }
        helper.succeed();
    }

    private static void bed(ServerLevel level, BlockPos head) {
        BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private SleepTests() {}
}
