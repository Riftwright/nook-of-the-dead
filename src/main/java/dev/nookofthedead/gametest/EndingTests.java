package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.ending.Film;
import dev.nookofthedead.ending.VaccineBlock;
import dev.nookofthedead.registry.ZwBlocks;
import dev.nookofthedead.registry.ZwItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The vaccine's console and its keys, and the films (0.3): what the user asked of them on 2026-10-21. */
final class EndingTests {
    static void register() {
        // four launch keys go in, one at a time, and a fifth does not; until they are all in, it does not start
        test("the_console_takes_four_keys", helper -> {
            helper.setBlock(new BlockPos(1, 1, 1), ZwBlocks.VACCINE.get());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZwItems.LAUNCH_KEY.get(), 5));
            BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
            var hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
            for (int i = 0; i < 5; i++) {
                var state = helper.getLevel().getBlockState(at);
                state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            }
            check(helper, helper.getLevel().getBlockState(at).getValue(VaccineBlock.KEYS) == VaccineBlock.NEEDED,
                    "four keys in: " + helper.getLevel().getBlockState(at).getValue(VaccineBlock.KEYS));
            check(helper, player.getMainHandItem().getCount() == 1, "the fifth kept: " + player.getMainHandItem().getCount());
            helper.succeed();
        });

        // one watching a film is noticed by none of the dead: the If's own pay them no mind while it plays, and hear nothing
        // of them (the user: the dead already about should not come for the one watching); vanilla's own take no
        // invulnerable one for a target, which a film makes them
        test("the_dead_leave_one_watching_be", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
            var dead = dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get().create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
            dead.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3))));
            check(helper, dead.mind().notices(player), "before: it would notice them");
            Film.start(player, Film.Kind.OPENING, java.util.List.of(player.position(), new Vec3(0, 0, 0)));
            check(helper, !dead.mind().notices(player), "watching: it does not");
            check(helper, dev.nookofthedead.entity.Hearing.loudness(net.minecraft.world.level.gameevent.GameEvent.STEP, player, null) == 0,
                    "nor hears them");
            Film.end(player);
            check(helper, dead.mind().notices(player), "the film over: it does again");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /** Nookworks' review of 2026-10-19: what of its own could be turned against NotD, shut. */
    static void registerLoopholes() {
        // the Vandal is a boss and none of the plague's can be bottled: no egg of them, no spawner of them
        test("the_plague_is_not_bottled", helper -> {
            check(helper, dev.nookofthedead.registry.ZwEntities.VANDAL.get().builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES),
                    "the Vandal a boss");
            for (var type : java.util.List.of(dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get(),
                    dev.nookofthedead.registry.ZwEntities.THIN_AIR_ZOMBIE.get(), dev.nookofthedead.registry.ZwEntities.ZOMBIE_COW.get(),
                    dev.nookofthedead.registry.ZwEntities.VANDAL.get())) {
                check(helper, type.builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.CAPTURING_NOT_SUPPORTED), "not to be bottled: " + type);
            }
            helper.succeed();
        });

        // what the dead come out standing on: on their own, glass and a bottom slab too but no stair; a horde's runner,
        // anything with a floor to it - never leaves or a fence (the user, 2026-10-21: a floor of glass or slabs was a
        // safe ground)
        test("the_dead_stand_on_glass_and_slabs", helper -> {
            var type = dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get();
            var floors = new net.minecraft.world.level.block.state.BlockState[] {
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState(),
                    net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
                    net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState()};
            boolean[] natural = {true, true, true, false, false, false};
            boolean[] runner = {true, true, true, true, false, false};
            for (int i = 0; i < floors.length; i++) {
                helper.setBlock(new BlockPos(i, 1, 1), floors[i]);
            }
            for (int i = 0; i < floors.length; i++) {
                BlockPos at = helper.absolutePos(new BlockPos(i, 2, 1));
                check(helper, dev.nookofthedead.entity.Footing.NATURAL.isSpawnPositionOk(helper.getLevel(), at, type) == natural[i],
                        "on their own, on " + floors[i] + ": " + natural[i]);
                check(helper, dev.nookofthedead.entity.Footing.runner(helper.getLevel(), at, type) == runner[i],
                        "a runner, on " + floors[i] + ": " + runner[i]);
            }
            helper.succeed();
        });

        // what the dead come out on of their own, by the spawn rules themselves too: glass and a bottom slab (the review,
        // 2026-10-21: vanilla's check in them still kept the dead off what the placement let them onto)
        test("the_dead_spawn_on_glass", helper -> {
            var type = dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get();
            helper.setBlock(new BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.GLASS);
            helper.setBlock(new BlockPos(3, 1, 1), net.minecraft.world.level.block.Blocks.OAK_SLAB);
            for (int x : new int[] {1, 3}) {
                BlockPos at = helper.absolutePos(new BlockPos(x, 2, 1));
                check(helper, net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, helper.getLevel(),
                        net.minecraft.world.entity.EntitySpawnReason.NATURAL, at, helper.getLevel().getRandom()), "they come on " + x);
            }
            helper.succeed();
        });

        // the thin-air dead now and then carry a launch key - only to a player's hand, and not every time (the review,
        // 2026-10-21: the list form of conditions was read as none, and every one dropped one)
        test("the_thin_air_dead_seldom_carry_a_key", helper -> {
            var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.LOOT_TABLE, dev.nookofthedead.NookOfTheDead.id("entities/thin_air_zombie")));
            var dead = dev.nookofthedead.registry.ZwEntities.THIN_AIR_ZOMBIE.get().create(helper.getLevel(), EntitySpawnReason.COMMAND);
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, dead)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,
                            helper.getLevel().damageSources().generic())
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
            int keys = 0;
            for (long seed = 0; seed < 200; seed++) {
                keys += table.getRandomItems(params, seed).size();
            }
            check(helper, keys == 0, "none without a player's hand: " + keys);
            helper.succeed();
        });

        // which diary one finds is of where it was found: the inn's own first (the man with the box), the sky base's only up
        // there and never in a town, the sand's only in the sand (the story's review, 2026-10-23)
        test("diaries_are_of_their_place", helper -> {
            var random = helper.getLevel().getRandom();
            var place = dev.nookofthedead.world.Diaries.Place.class;
            check(helper, dev.nookofthedead.world.Diaries.choose(java.util.List.of(), dev.nookofthedead.world.Diaries.Place.INN, random) == 8,
                    "the inn's: the man with the box");
            check(helper, dev.nookofthedead.world.Diaries.choose(java.util.List.of(), dev.nookofthedead.world.Diaries.Place.SKY, random) == 29,
                    "the sky base's: the top of the tower");
            for (int k = 0; k < 400; k++) {
                int n = dev.nookofthedead.world.Diaries.choose(java.util.List.of(), dev.nookofthedead.world.Diaries.Place.TOWN, random);
                check(helper, n != 29 && n != 20, "never in a town: " + n);
            }
            java.util.List<Integer> all = new java.util.ArrayList<>();
            for (int n = 1; n <= dev.nookofthedead.world.Diaries.PAGES.length; n++) {
                all.add(n);
            }
            int any = dev.nookofthedead.world.Diaries.choose(all, dev.nookofthedead.world.Diaries.Place.TOWN, random);
            check(helper, any != 29 && any != 20, "all read, still none of another place's: " + any);
            helper.succeed();
        });

        // a launch key lying about is not burnt, crushed or blown up, and does not go when its time on the ground is up (the
        // user, 2026-10-23: no key may be lost for good)
        test("a_launch_key_is_not_lost", helper -> {
            var key = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(), helper.absoluteVec(new Vec3(1.5, 2, 1.5)).x,
                    helper.absoluteVec(new Vec3(1.5, 2, 1.5)).y, helper.absoluteVec(new Vec3(1.5, 2, 1.5)).z, new ItemStack(ZwItems.LAUNCH_KEY.get()));
            var sources = helper.getLevel().damageSources();
            for (var source : java.util.List.of(sources.inFire(), sources.lava(), sources.cactus(), sources.explosion(null, null))) {
                key.hurtServer(helper.getLevel(), source, 100.0F);
                check(helper, !key.isRemoved(), "not destroyed by " + source.getMsgId());
            }
            check(helper, ZwItems.LAUNCH_KEY.get().getEntityLifespan(key.getItem(), helper.getLevel()) == Integer.MAX_VALUE, "never despawns");
            int before = dev.nookofthedead.ending.LostKeys.waiting(helper.getLevel().getServer());
            dev.nookofthedead.ending.LostKeys.lost(helper.getLevel().getServer(), 1);
            check(helper, dev.nookofthedead.ending.LostKeys.waiting(helper.getLevel().getServer()) == before + 1, "a lost one counted, to turn up");
            helper.succeed();
        });

        // a death over there ends the infection; one at home leaves it as it stood
        test("only_a_death_there_ends_it", helper -> {
            var home = java.util.Optional.of(net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO));
            var there = java.util.Optional.of(net.minecraft.core.GlobalPos.of(dev.nookworks.ifworld.IfWorld.KEY, BlockPos.ZERO));
            check(helper, dev.nookofthedead.infection.Infection.endsWith(there), "died there: over");
            check(helper, !dev.nookofthedead.infection.Infection.endsWith(home), "died at home: kept");
            helper.succeed();
        });
    }

    private EndingTests() {}
}
