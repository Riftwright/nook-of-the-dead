package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.plain;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.infection.Infection;
import dev.nookofthedead.registry.ZwEntities;
import dev.nookofthedead.registry.ZwItems;
import dev.nookofthedead.world.ZombieMarkerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;

/** The runners and the infection. */
final class PlagueTests {
    static void register() {
        // every one of the plague's creatures is undead to the game - Smite cuts it, a potion of healing hurts it (the
        // user, 2026-10-18: "感染牛とか感染ネズミとかヴァンダルとか、ちゃんとアンデット特攻適用されるの？" - none was) - and a
        // mutated horse comes alone, not ridden by a plain zombie with a spear as vanilla's does
        test("the_plague_is_undead", 40, helper -> {
            for (var type : java.util.List.of(ZwEntities.MUTATED_ZOMBIE.get(), ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(),
                    ZwEntities.MUTATED_ZOMBIE_HORSE.get(), ZwEntities.ZOMBIE_COW.get(), ZwEntities.ZOMBIE_WOLF.get(), ZwEntities.INFECTED_CAT.get(), ZwEntities.INFECTED_RAT.get(),
                    ZwEntities.INFECTED_BAT.get(), ZwEntities.VANDAL.get(), ZwEntities.MUTATED_HUSK.get(), ZwEntities.INFECTED_CAMEL.get(),
                    ZwEntities.INFECTED_SCORPION.get())) {
                check(helper, type.builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE)
                        && type.builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.UNDEAD),
                        type.toShortString() + " is undead, Smite cuts it");
            }
            var horse = ZwEntities.MUTATED_ZOMBIE_HORSE.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.NATURAL);
            horse.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 2.5)), 0.0F, 0.0F);
            horse.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(horse.blockPosition()),
                    net.minecraft.world.entity.EntitySpawnReason.NATURAL, null);
            check(helper, horse.getPassengers().isEmpty(), "a mutated horse comes alone");
            helper.succeed();
        });

        // quick, weaker than a zombie, never a baby, and the sun does not burn it
        test("runner_is_quick_and_the_sun_spares_it", 120, helper -> {
            helper.setTime(6000);
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(3, 1, 3)));
            check(helper, runner.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.3, "a runner is quick");
            check(helper, runner.getMaxHealth() < 20, "a runner is weaker than a zombie");
            check(helper, !runner.isBaby(), "never a baby");
            helper.runAfterDelay(80, () -> {
                check(helper, !runner.isOnFire() && runner.isAlive(), "the midday sun does not burn it");
                helper.succeed();
            });
        });

        // a plank wall between a runner and its prey is broken through
        test("runner_breaks_through_planks", 1200, helper -> {
            for (int x = 0; x <= 6; x++) {                               // a pen it cannot leave, of stone
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
                    helper.setBlock(new BlockPos(0, y, x), Blocks.STONE);
                    helper.setBlock(new BlockPos(6, y, x), Blocks.STONE);
                }
            }
            for (int z = 1; z <= 5; z++) {                               // the wall across it: planks, stone above
                helper.setBlock(new BlockPos(3, 1, z), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(3, 2, z), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(3, 3, z), Blocks.STONE);
            }
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 3.5)));
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            runner.setTarget(prey);
            long[] brokenAt = {-1};
            helper.succeedWhen(() -> {
                boolean broken = false;
                for (int z = 1; z <= 5; z++) {
                    broken |= helper.getBlockState(new BlockPos(3, 1, z)).isAir() || helper.getBlockState(new BlockPos(3, 2, z)).isAir();
                }
                if (!broken) {
                    runner.setTarget(prey);
                    throw helper.assertionException(net.minecraft.network.chat.Component.literal("the wall still stands"));
                }
                // alive a few ticks on, odd and even: the tick after a break crashed the server one time in two (r1)
                if (brokenAt[0] < 0) {
                    brokenAt[0] = helper.getTick();
                }
                if (helper.getTick() - brokenAt[0] < 6 || !runner.isAlive()) {
                    throw helper.assertionException(net.minecraft.network.chat.Component.literal("the runner lives on after the break"));
                }
                runner.discard();
                leave(helper, prey);
            });
        });

        // a runner on a floor of wooden slabs, its prey up out of reach, leaves the floor under it be (r1: it broke it)
        test("runner_keeps_the_floor_it_stands_on", 200, helper -> {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_SLAB);
                }
            }
            helper.setBlock(new BlockPos(2, 5, 2), Blocks.STONE);
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 6, 2.5)));
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new net.minecraft.world.phys.Vec3(1.5, 1.5, 1.5)));
            runner.setTarget(prey);
            helper.runAfterDelay(160, () -> {
                int slabs = 0;
                for (int x = 0; x <= 4; x++) {
                    for (int z = 0; z <= 4; z++) {
                        slabs += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SLAB) ? 1 : 0;
                    }
                }
                runner.discard();
                leave(helper, prey);
                check(helper, slabs == 25, "the slab floor stands whole: " + slabs + " of 25");
                helper.succeed();
            });
        });

        // a mirror walled in stone it cannot get to: it gives up after a few tries and rests, not searching every tick (r1)
        test("runner_leaves_a_mirror_walled_in_stone_be", 400, helper -> {
            BlockPos mirror = new BlockPos(8, 1, 4);
            for (int dx = -1; dx <= 1; dx++) {                          // walled in stone, the wall against it
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 1; y <= 3; y++) {
                        if (dx != 0 || dz != 0 || y == 3) {
                            helper.setBlock(mirror.offset(dx, y - 1, dz), Blocks.STONE);
                        }
                    }
                }
            }
            var state = dev.nookworks.registry.ModTravel.IF_MIRROR.get().defaultBlockState();
            helper.setBlock(mirror, state);
            helper.setBlock(mirror.above(), state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            BlockPos at = helper.absolutePos(mirror);
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            var goal = new dev.nookofthedead.entity.SmashMirrorGoal(runner, level -> at);
            runner.goalSelector.addGoal(4, goal);
            helper.succeedWhen(() -> {
                if (!goal.resting()) {
                    throw helper.assertionException(net.minecraft.network.chat.Component.literal("still trying for the walled-in mirror"));
                }
                check(helper, helper.getBlockState(mirror).is(dev.nookworks.registry.ModTravel.IF_MIRROR.get()), "the mirror stands");
                runner.discard();
            });
        });

        // the fixtures of a building go out with it in /nzw export; mobs and dropped things do not (r1: nothing went)
        test("export_keeps_fixtures_not_mobs", helper -> {
            java.util.function.Function<String, net.minecraft.nbt.CompoundTag> tagged = id -> {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.putString("id", id);
                return tag;
            };
            check(helper, dev.nookofthedead.ZwCommands.kept(tagged.apply("minecraft:item_frame")), "an item frame goes");
            check(helper, dev.nookofthedead.ZwCommands.kept(tagged.apply("minecraft:painting")), "a painting goes");
            check(helper, dev.nookofthedead.ZwCommands.kept(tagged.apply("minecraft:armor_stand")), "an armour stand goes");
            check(helper, dev.nookofthedead.ZwCommands.kept(tagged.apply("nookworks:poster")), "a Nookworks poster goes");
            check(helper, !dev.nookofthedead.ZwCommands.kept(tagged.apply("minecraft:item")), "a dropped item stays");
            check(helper, !dev.nookofthedead.ZwCommands.kept(tagged.apply("minecraft:zombie")), "a zombie stays");
            check(helper, !dev.nookofthedead.ZwCommands.kept(tagged.apply("nookofthedead:runner")), "a runner stays");
            helper.succeed();
        });

        // round a hunted player the horde comes out of sight, at a distance, making for where they are; none for a creative one
        test("runner_horde_gathers_round_a_hunted_player", helper -> {
            ServerPlayer hunted = player(helper, GameType.SURVIVAL);
            hunted.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 3.5)));
            java.util.Set<net.minecraft.world.entity.monster.zombie.Zombie> before = new java.util.HashSet<>(helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.zombie.Zombie.class, new net.minecraft.world.phys.AABB(hunted.blockPosition()).inflate(80)));
            int came = dev.nookofthedead.entity.Horde.gather(helper.getLevel(), hunted);
            java.util.List<net.minecraft.world.entity.monster.zombie.Zombie> runners = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.monster.zombie.Zombie.class, new net.minecraft.world.phys.AABB(hunted.blockPosition()).inflate(80),
                    r -> !before.contains(r));
            try {
                check(helper, came > 0 && came <= dev.nookofthedead.entity.Horde.PER_WAVE, "a wave of a few came (" + came + ")");
                check(helper, runners.size() <= came, "the dead among them, the rest the country's animals (" + runners.size() + " of " + came + ")");
                for (var r : runners) {
                    double d = Math.sqrt(r.distanceToSqr(hunted.getX(), r.getY(), hunted.getZ()));
                    check(helper, d >= dev.nookofthedead.entity.Horde.MIN_FROM - 1, "none came close by (" + d + ")");
                    var lead = dev.nookofthedead.entity.Mind.of(r).lead();
                    check(helper, r.getTarget() == null && lead != null && lead.distanceTo(hunted.position()) < 1,
                            "each makes for where the hunted player is, not knowing them yet");
                }
                hunted.setGameMode(GameType.CREATIVE);
                check(helper, dev.nookofthedead.entity.Horde.gather(helper.getLevel(), hunted) == 0, "none come for a creative player");
            } finally {
                runners.forEach(r -> r.discard());
                leave(helper, hunted);
            }
            helper.succeed();
        });

        // up on a roof (the user, 2026-10-13: "横方向には索敵強いけど、縦方向にはそうでもない" - from a roof the dead should be
        // seen wandering the town, not all gathered below): one on the street far off along it notices a player on the
        // street, but not one ten up (one right below does); those after a player gone up where they get no higher give
        // them up after a while and do not take them up again at once; and the horde's newcomers round someone on a roof
        // come out only to wander
        test("runner_looks_along_the_street_not_up", 200, helper -> {
            // in the arena's corners (7 x 7): one outside it may be in a chunk that does not tick
            helper.setBlock(new BlockPos(0, 10, 0), Blocks.STONE);                 // the roof it stands on
            ServerPlayer up = player(helper, GameType.SURVIVAL);
            up.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 11, 0.5)));
            MutatedZombie below = plain(MutatedZombie.spawnAt(helper.getLevel(), ZwEntities.MUTATED_ZOMBIE.get(), helper.absolutePos(new BlockPos(2, 1, 2))));
            MutatedZombie off = plain(MutatedZombie.spawnAt(helper.getLevel(), ZwEntities.MUTATED_ZOMBIE.get(), helper.absolutePos(new BlockPos(6, 1, 6))));
            check(helper, below.notices(up) && !off.notices(up), "a player ten up is noticed from right below, not from 8.5 off");
            up.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5)));
            check(helper, off.notices(up), "the same player down on the street is");
            up.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 11, 0.5)));
            below.setTarget(up);
            off.setTarget(up);
            java.util.Set<net.minecraft.world.entity.monster.zombie.Zombie> before = new java.util.HashSet<>(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(up.blockPosition()).inflate(80)));
            int came = dev.nookofthedead.entity.Horde.gather(helper.getLevel(), up, null);
            java.util.List<net.minecraft.world.entity.monster.zombie.Zombie> newcomers = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(up.blockPosition()).inflate(80), r -> !before.contains(r));
            helper.runAfterDelay(dev.nookofthedead.entity.Mind.GIVES_UP + 20, () -> {
                try {
                    check(helper, off.getTarget() == null && below.getTarget() == null, "both gave the player on the roof up (off: "
                            + off.getTarget() + " alive " + off.isAlive() + " at " + off.blockPosition() + ", below: " + below.getTarget()
                            + " alive " + below.isAlive() + " at " + below.blockPosition() + ", player at " + up.blockPosition() + ")");
                    check(helper, !below.notices(up), "and the one right below takes no notice of them up there now");
                    check(helper, came == 0 || newcomers.stream().noneMatch(r -> r.getTarget() == up || dev.nookofthedead.entity.Mind.of(r).looking()),
                            "the newcomers round a player on a roof wander (" + came + " came)");
                } finally {
                    below.discard();
                    off.discard();
                    newcomers.forEach(r -> r.discard());
                    leave(helper, up);
                }
                helper.succeed();
            });
        });

        // someone running away east (the user, 2026-10-13: the crowd behind took every place, none ahead): the runners left
        // far behind, out of sight behind a wall, go; one ahead stays; and most of those that come out come out ahead
        test("runner_horde_lets_the_ones_left_behind_go_and_comes_ahead", helper -> {
            ServerPlayer hunted = player(helper, GameType.SURVIVAL);
            hunted.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 3.5)));
            for (int z = 0; z <= 6; z++) {                               // a wall on the west side, between them and the ones behind
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(0, y, z), Blocks.STONE);
                }
            }
            net.minecraft.world.phys.Vec3 east = new net.minecraft.world.phys.Vec3(1, 0, 0);
            java.util.List<MutatedZombie> behind = new java.util.ArrayList<>();
            for (int k = 0; k < 3; k++) {                                 // as the horde's own come: not kept for good
                MutatedZombie r = ZwEntities.MUTATED_ZOMBIE.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
                net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(-27.5 - k, 1, 3.5));
                r.snapTo(at.x, at.y, at.z, 0, 0);
                helper.getLevel().addFreshEntity(r);
                r.setTarget(hunted);
                behind.add(r);
            }
            MutatedZombie ahead = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(30, 1, 3)));
            java.util.Set<net.minecraft.world.entity.monster.zombie.Zombie> before = new java.util.HashSet<>(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(hunted.blockPosition()).inflate(80)));
            int came = 0;
            for (int wave = 0; wave < 3; wave++) {                       // a few waves: one alone is only six throws
                came += dev.nookofthedead.entity.Horde.gather(helper.getLevel(), hunted, east);
            }
            java.util.List<net.minecraft.world.entity.monster.zombie.Zombie> fresh = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                    new net.minecraft.world.phys.AABB(hunted.blockPosition()).inflate(80), r -> !before.contains(r));
            try {
                check(helper, behind.stream().allMatch(MutatedZombie::isRemoved), "the ones left behind out of sight are gone");
                check(helper, !ahead.isRemoved(), "the one ahead stays");
                long forward = fresh.stream().filter(r -> r.getX() > hunted.getX()).count();
                check(helper, came > 0 && forward * 2 >= fresh.size(), "most newcomers come out ahead: " + forward + " of " + fresh.size());
            } finally {
                fresh.forEach(net.minecraft.world.entity.monster.zombie.Zombie::discard);
                ahead.discard();
                leave(helper, hunted);
            }
            helper.succeed();
        });

        // a mirror in the way of a chase is smashed like any soft thing, nothing of it left
        test("runner_smashes_a_mirror_in_its_way", 500, helper -> {
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
            net.minecraft.world.level.block.state.BlockState mirror = dev.nookworks.registry.ModTravel.IF_MIRROR.get().defaultBlockState()
                    .setValue(dev.nookworks.ifworld.IfMirrorBlock.FACING, net.minecraft.core.Direction.EAST);
            helper.setBlock(new BlockPos(3, 1, 3), mirror);
            helper.setBlock(new BlockPos(3, 2, 3), mirror.setValue(dev.nookworks.ifworld.IfMirrorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 1, 3.5)));
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            runner.setTarget(prey);
            helper.succeedWhen(() -> {
                if (helper.getBlockState(new BlockPos(3, 1, 3)).is(dev.nookworks.registry.ModTravel.IF_MIRROR.get())) {
                    runner.setTarget(prey);
                    throw helper.assertionException(net.minecraft.network.chat.Component.literal("the mirror still stands"));
                }
                check(helper, helper.getEntities(net.minecraft.world.entity.EntityTypes.ITEM, new BlockPos(3, 1, 3), 3).isEmpty(),
                        "nothing of the mirror is left to pick up");
                runner.discard();
                leave(helper, prey);
            });
        });

        // an idle runner near the mirror it is pointed to (in play, the If's own) goes to it and smashes it
        test("runner_smashes_the_way_home", 600, helper -> {
            BlockPos at = new BlockPos(5, 1, 5);
            net.minecraft.world.level.block.state.BlockState mirror = dev.nookworks.registry.ModTravel.IF_MIRROR.get().defaultBlockState();
            helper.setBlock(at, mirror);
            helper.setBlock(at.above(), mirror.setValue(dev.nookworks.ifworld.IfMirrorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            MutatedZombie runner = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 1)));
            BlockPos there = helper.absolutePos(at);
            runner.goalSelector.addGoal(4, new dev.nookofthedead.entity.SmashMirrorGoal(runner, level -> there));
            long[] smashedAt = {-1};
            helper.succeedWhen(() -> {
                check(helper, !helper.getBlockState(at).is(dev.nookworks.registry.ModTravel.IF_MIRROR.get()), "the mirror still stands");
                // alive a few ticks on, odd and even: the tick after a smash crashed the server one time in two (r1)
                if (smashedAt[0] < 0) {
                    smashedAt[0] = helper.getTick();
                }
                check(helper, helper.getTick() - smashedAt[0] >= 6 && runner.isAlive(), "the runner lives on after the smash");
                runner.discard();
            });
        });

        // a [zombie 2] marker lets two out when a player comes near, and is gone; they stand there till they see or hear someone
        test("runner_marker_lets_its_zombies_out", 200, helper -> {
            helper.setBlock(new BlockPos(3, 1, 3), ZombieMarkerBlock.of(2, false));
            for (int z = 0; z <= 6; z++) {                               // the player behind a wall: near, but not seen
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
                }
            }
            ServerPlayer near = player(helper, GameType.SURVIVAL);
            near.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
            helper.succeedWhen(() -> {
                check(helper, !helper.getBlockState(new BlockPos(3, 1, 3)).is(dev.nookofthedead.registry.ZwBlocks.ZOMBIE_MARKER.get()),
                        "the marker has gone");
                var out = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                        net.minecraft.world.phys.AABB.ofSize(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 3.5)), 12, 6, 12),
                        z -> z instanceof dev.nookofthedead.entity.Mutated);
                check(helper, out.size() >= 2, "two came out");
                check(helper, out.stream().allMatch(z -> dev.nookofthedead.entity.Mind.of(z).standing() && z.getTarget() == null),
                        "standing where they were left, not knowing who came");
                out.forEach(net.minecraft.world.entity.Entity::discard);
                leave(helper, near);
            });
        });

        // a roof and a light keep them off; the open sky does not
        test("runners_keep_off_roofed_lit_places", 200, helper -> {
            for (int x = 1; x <= 5; x++) {
                for (int z = 1; z <= 5; z++) {
                    helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                }
            }
            helper.setBlock(new BlockPos(3, 3, 3), Blocks.GLOWSTONE);
            helper.succeedWhen(() -> {
                check(helper, MutatedZombie.safe(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3))), "under the lit roof is safe");
                check(helper, !MutatedZombie.safe(helper.getLevel(), helper.absolutePos(new BlockPos(3, 5, 3))), "on the roof is not");
            });
        });

        // outside the fallen world it neither starts nor runs; a suppressant sets the full twenty minutes, the bottle left
        test("infection_waits_outside_and_a_suppressant_resets_it", helper -> {
            ServerPlayer player = player(helper, GameType.SURVIVAL);
            Infection.second(player);
            check(helper, !Infection.infected(player), "nobody is infected outside the fallen world");
            player.setData(Infection.LEFT, 1000);
            Infection.second(player);
            check(helper, Infection.left(player) == 1000, "the infection stops outside the fallen world");
            ItemStack left = dev.nookofthedead.registry.ZwEffects.bottle(Items.POTION).finishUsingItem(helper.getLevel(), player);
            check(helper, Infection.left(player) == Infection.FULL && Infection.FULL == 20 * 60 * 15, "a suppressant gives fifteen minutes again");
            check(helper, left.is(Items.GLASS_BOTTLE), "the bottle is left");
            // the long one, brewed with redstone: thirty
            ItemStack longOne = new ItemStack(Items.POTION);
            longOne.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                    new net.minecraft.world.item.alchemy.PotionContents(dev.nookofthedead.registry.ZwEffects.LONG_SUPPRESSANT_POTION));
            longOne.finishUsingItem(helper.getLevel(), player);
            check(helper, Infection.left(player) == Infection.LONG && Infection.LONG == 20 * 60 * 30, "a long suppressant gives thirty");
            leave(helper, player);
            helper.succeed();
        });

        // a bed in the fallen world sets no respawn point: dying there takes one home (the user, 2026-10-13); an
        // operator's forced one is kept
        test("fallen_world_bed_sets_no_respawn_point", helper -> {
            ServerPlayer player = player(helper, GameType.SURVIVAL);
            BlockPos home = helper.absolutePos(BlockPos.ZERO);
            BlockPos there = new BlockPos(8, 64, 8);
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, home, 0, 0), false), false);
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(IfWorld.KEY, there, 0, 0), false), false);
            ServerPlayer.RespawnConfig kept = player.getRespawnConfig();
            check(helper, kept != null && kept.respawnData().dimension().equals(Level.OVERWORLD) && kept.respawnData().pos().equals(home),
                    "the overworld's bed is still the respawn point, not " + kept);
            player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(IfWorld.KEY, there, 0, 0), true), false);
            check(helper, player.getRespawnConfig() != null && player.getRespawnConfig().respawnData().dimension().equals(IfWorld.KEY),
                    "an operator's /spawnpoint there is kept");
            leave(helper, player);
            helper.succeed();
        });
    }

    private PlagueTests() {}
}
