package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.plain;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.nookofthedead.ZwConfig;
import dev.nookofthedead.entity.Horde;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.entity.SmashMirrorGoal;
import dev.nookofthedead.infection.Infection;
import dev.nookofthedead.registry.ZwEffects;
import dev.nookofthedead.registry.ZwEntities;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfMirrorBlock;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.registry.ModTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/** The add-on's settings ({@link ZwConfig}) doing what they say. */
final class SettingsTests {
    static void register() {
        // the elytra setting: on (the default), one glides in the If as anywhere; off, not in the If, and still at home
        test("if_elytra_setting", helper -> {
            var server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel theIf = IfLevel.open(server);
            boolean was = ZwConfig.ELYTRA_IN_THE_IF.get();
            try {
                check(helper, ZwConfig.ELYTRA_IN_THE_IF.getDefault(), "elytra are allowed in the If by default");
                ZwConfig.ELYTRA_IN_THE_IF.set(true);
                check(helper, glides(theIf), "allowed, one glides in the If");
                check(helper, glides(helper.getLevel()), "allowed, one glides at home");
                ZwConfig.ELYTRA_IN_THE_IF.set(false);
                check(helper, !glides(theIf), "not allowed, one glided in the If");
                check(helper, glides(helper.getLevel()), "not allowed in the If, one glides at home all the same");
            } finally {
                ZwConfig.ELYTRA_IN_THE_IF.set(was);
            }
            helper.succeed();
        });

        // the crowd setting: the usual by default; fewer and more scale the swarm and the If's most alike
        test("if_crowd_setting", helper -> {
            ZwConfig.Crowd was = ZwConfig.CROWD.get();
            try {
                check(helper, ZwConfig.CROWD.getDefault() == ZwConfig.Crowd.NORMAL, "the usual crowd by default");
                ZwConfig.CROWD.set(ZwConfig.Crowd.NORMAL);
                check(helper, ZwConfig.crowd() == 1.0 && Horde.worldMax() == Horde.WORLD_MAX, "the usual: as it was");
                ZwConfig.CROWD.set(ZwConfig.Crowd.FEWER);
                check(helper, ZwConfig.crowd() < 1.0 && Horde.worldMax() < Horde.WORLD_MAX, "fewer: fewer, and fewer at most");
                ZwConfig.CROWD.set(ZwConfig.Crowd.MORE);
                check(helper, ZwConfig.crowd() > 1.0 && Horde.worldMax() > Horde.WORLD_MAX, "more: more, and more at most");
            } finally {
                ZwConfig.CROWD.set(was);
            }
            helper.succeed();
        });

        // the suppressant setting: fifteen minutes by default; set to five, a dose holds five and a long one ten
        test("if_suppressant_setting", helper -> {
            int was = ZwConfig.SUPPRESSANT_MINUTES.get();
            ServerPlayer player = player(helper, GameType.SURVIVAL);
            try {
                check(helper, ZwConfig.SUPPRESSANT_MINUTES.getDefault() == 15, "fifteen minutes by default");
                ZwConfig.SUPPRESSANT_MINUTES.set(5);
                player.setData(Infection.LEFT, 1000);
                ZwEffects.bottle(Items.POTION).finishUsingItem(helper.getLevel(), player);
                check(helper, Infection.left(player) == 20 * 60 * 5, "five minutes set, a dose gave " + Infection.left(player) / 20 + "s");
                ItemStack longOne = new ItemStack(Items.POTION);
                longOne.set(DataComponents.POTION_CONTENTS, new PotionContents(ZwEffects.LONG_SUPPRESSANT_POTION));
                longOne.finishUsingItem(helper.getLevel(), player);
                check(helper, Infection.left(player) == 20 * 60 * 10, "five minutes set, a long dose gave " + Infection.left(player) / 20 + "s");
                ZwConfig.SUPPRESSANT_MINUTES.set(15);
                Infection.clear(player);
                player.setData(Infection.LEFT, 1000);
                Infection.suppress(player);
                check(helper, Infection.left(player) == Infection.FULL, "fifteen set, a dose gives fifteen as it always did");
            } finally {
                ZwConfig.SUPPRESSANT_MINUTES.set(was);
                leave(helper, player);
            }
            helper.succeed();
        });

        // the mirror setting, a runner after someone behind a wall with a mirror in it: off, it leaves the mirror be; on
        // again, it breaks through (so the wall is one it would break)
        test("if_mirror_setting_in_a_chase", 600, helper -> {
            for (int x = 0; x <= 6; x++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
                    helper.setBlock(new BlockPos(0, y, x), Blocks.STONE);
                    helper.setBlock(new BlockPos(6, y, x), Blocks.STONE);
                }
            }
            for (int z = 1; z <= 5; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
                }
            }
            BlockPos at = new BlockPos(3, 1, 3);
            BlockState mirror = ModTravel.IF_MIRROR.get().defaultBlockState().setValue(IfMirrorBlock.FACING, Direction.EAST);
            helper.setBlock(at, mirror);
            helper.setBlock(at.above(), mirror.setValue(IfMirrorBlock.HALF, DoubleBlockHalf.UPPER));
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 3.5)));
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            runner.setTarget(prey);
            offThenOn(helper, at, () -> runner.setTarget(prey), () -> {
                runner.discard();
                leave(helper, prey);
            });
        });

        // and an idle runner near a mirror it would go for: off, it does not; on again, it smashes it
        test("if_mirror_setting_idle", 600, helper -> {
            BlockPos at = new BlockPos(5, 1, 5);
            BlockState mirror = ModTravel.IF_MIRROR.get().defaultBlockState();
            helper.setBlock(at, mirror);
            helper.setBlock(at.above(), mirror.setValue(IfMirrorBlock.HALF, DoubleBlockHalf.UPPER));
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 1)));
            BlockPos there = helper.absolutePos(at);
            runner.goalSelector.addGoal(4, new SmashMirrorGoal(runner, level -> there));
            offThenOn(helper, at, () -> {}, runner::discard);
        });
    }

    /** Ticks the mirror setting stays off in the mirror tests: long enough for a runner to have broken a mirror twice over. */
    private static final int OFF_FOR = 240;

    /**
     * The mirror at {@code at} (relative) standing through {@link #OFF_FOR} ticks with the mirror setting off, then, the
     * setting on again, broken; {@code keepOn} each tick (a chase held), {@code done} at the end. The setting is left on
     * (its default) after the off ticks, whatever comes after.
     */
    private static void offThenOn(GameTestHelper helper, BlockPos at, Runnable keepOn, Runnable done) {
        check(helper, ZwConfig.MIRRORS_SMASHED.getDefault(), "the dead break mirrors by default");
        ZwConfig.MIRRORS_SMASHED.set(false);
        long start = helper.getTick();
        boolean[] on = {false};
        helper.onEachTick(() -> {
            keepOn.run();
            if (!on[0] && helper.getTick() - start < OFF_FOR) {
                check(helper, helper.getBlockState(at).is(ModTravel.IF_MIRROR.get()),
                        "the setting off, a mirror was broken at tick " + (helper.getTick() - start));
            } else if (!on[0]) {
                on[0] = true;
                ZwConfig.MIRRORS_SMASHED.set(true);
            }
        });
        helper.succeedWhen(() -> {
            check(helper, on[0] && !helper.getBlockState(at).is(ModTravel.IF_MIRROR.get()), "the mirror still stands");
            done.run();
        });
    }

    /** Whether a player high in {@code level}'s air, with gliding as an elytra gives it, opens their wings. */
    private static boolean glides(ServerLevel level) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-glider"),
                ClientInformation.createDefault());
        BlockPos high = new BlockPos(0, level.getMaxY() - 8, 0);
        player.setPos(high.getX() + 0.5, high.getY(), high.getZ() + 0.5);   // (not snapTo: it tells a connection this one has not)
        player.setOnGround(false);
        player.getAttribute(NeoForgeMod.GLIDING_FLIGHT).setBaseValue(1.0);
        return player.tryToStartFallFlying();
    }

    private SettingsTests() {}
}
