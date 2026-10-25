package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.plain;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.HashSet;
import java.util.Set;

import dev.nookofthedead.entity.Hearing;
import dev.nookofthedead.entity.Mind;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.entity.MutatedZombieVillager;
import dev.nookofthedead.entity.Traits;
import dev.nookofthedead.infection.Infection;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** The mutated zombies' traits, eyes and ears (Mind, Traits, Hearing), the bite, ladders and the zombie villager. */
final class SensesTests {
    static void register() {
        // every kind of each trait comes up, the pace and sight are set by them, and they are kept through a save
        test("traits_come_in_every_kind_and_are_kept", helper -> {
            Set<Enum<?>> seen = new HashSet<>();
            for (int i = 0; i < 3000; i++) {
                Traits t = Traits.roll(helper.getLevel().getRandom());
                seen.addAll(java.util.List.of(t.pace(), t.chase(), t.sense(), t.breaker(), t.voice()));
            }
            for (Enum<?>[] kinds : Traits.all()) {
                for (Enum<?> kind : kinds) {
                    check(helper, seen.contains(kind), kind + " never came up");
                }
            }
            MutatedZombie z = MutatedZombie.spawnAt(helper.getLevel(), ZwEntities.MUTATED_ZOMBIE.get(), helper.absolutePos(new BlockPos(3, 1, 3)));
            Traits t = z.mind().traits();
            check(helper, z.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) == t.pace().speed, "its pace is its trait's");
            check(helper, z.getAttributeBaseValue(Attributes.FOLLOW_RANGE) == t.sense().sight, "its sight is its trait's");
            Traits odd = new Traits(Traits.Pace.SPRINT, Traits.Chase.FLANK, Traits.Sense.DULL, Traits.Breaker.NONE, Traits.Voice.SCREAM);
            TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            odd.save(out);
            Traits back = Traits.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), out.buildResult()));
            check(helper, back.equals(odd), "kept through a save: " + back);
            z.discard();
            helper.succeed();
        });

        // it does not see through a stone wall; with the wall gone it does
        test("runner_does_not_see_through_a_wall", 260, helper -> {
            for (int z = 0; z <= 6; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
                }
            }
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 3.5)));
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            helper.runAfterDelay(40, () -> {
                check(helper, z.getTarget() == null, "it saw through the wall");
                for (int zz = 0; zz <= 6; zz++) {
                    for (int y = 1; y <= 4; y++) {
                        helper.setBlock(new BlockPos(3, y, zz), Blocks.AIR);
                    }
                }
                helper.runAfterDelay(100, () -> {                           // its target goal looks now and then, not every tick
                    boolean seen = z.getTarget() == prey;
                    z.discard();
                    leave(helper, prey);
                    check(helper, seen, "in the open it sees them");
                    helper.succeed();
                });
            });
        });

        // a door opened behind a wall is heard and gone to see; a sneaking step, a step on a carpet are not heard at all;
        // nothing is heard through wool; the dull ones hear less far than the keen
        test("runner_hears_through_a_wall_not_a_creeping_step", helper -> {
            for (int z = 0; z <= 6; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
                }
            }
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 3.5)));
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            Vec3 at = prey.position();
            try {
                check(helper, Hearing.loudness(GameEvent.STEP, prey, Blocks.STONE.defaultBlockState()) > 0, "a step is a sound");
                check(helper, Hearing.loudness(GameEvent.STEP, prey, Blocks.CARPET.white().defaultBlockState()) == 0, "a step on a carpet is none");
                prey.setPose(Pose.CROUCHING);
                prey.setShiftKeyDown(true);
                check(helper, Hearing.loudness(GameEvent.STEP, prey, Blocks.STONE.defaultBlockState()) == 0, "a creeping step is none");
                check(helper, Hearing.loudness(GameEvent.BLOCK_OPEN, prey, null) > 0, "a door opened creeping still is one");
                prey.setShiftKeyDown(false);
                prey.setPose(Pose.STANDING);
                Hearing.sound(helper.getLevel(), GameEvent.STEP, at, prey, Blocks.CARPET.white().defaultBlockState());
                check(helper, !z.mind().looking(), "nothing heard of a step on a carpet");
                Hearing.sound(helper.getLevel(), GameEvent.BLOCK_OPEN, at, prey, null);
                check(helper, z.mind().looking() && z.getTarget() == null, "the door heard through the wall, gone to see, not seen");
                z.mind().looked();
                for (int y = 1; y <= 4; y++) {
                    for (int zz = 0; zz <= 6; zz++) {
                        helper.setBlock(new BlockPos(3, y, zz), Blocks.WOOL.white());
                    }
                }
                Hearing.sound(helper.getLevel(), GameEvent.BLOCK_OPEN, at, prey, null);
                check(helper, !z.mind().looking(), "nothing heard through wool");
                Vec3 far = z.getEyePosition().add(0, 0, -3);
                z.mind().set(new Traits(Traits.Pace.RUN, Traits.Chase.DIRECT, Traits.Sense.DULL, Traits.Breaker.SOFT));
                check(helper, !Hearing.hears(helper.getLevel(), z, z.mind(), far, 4), "a dull one does not hear 4 at 3");
                z.mind().set(new Traits(Traits.Pace.RUN, Traits.Chase.DIRECT, Traits.Sense.KEEN, Traits.Breaker.SOFT));
                check(helper, Hearing.hears(helper.getLevel(), z, z.mind(), far, 4), "a keen one does");
            } finally {
                z.discard();
                leave(helper, prey);
            }
            helper.succeed();
        });

        // a thing thrown into the street draws it off to where it landed
        test("runner_goes_to_see_where_a_thing_landed", 200, helper -> {
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 1)));
            Vec3 landed = helper.absoluteVec(new Vec3(5.5, 1, 5.5));
            Hearing.sound(helper.getLevel(), GameEvent.PROJECTILE_LAND, landed, null, null);
            helper.succeedWhen(() -> {
                if (z.distanceToSqr(landed) > 2.5 * 2.5) {
                    throw helper.assertionException(net.minecraft.network.chat.Component.literal("not there yet: " + z.position().subtract(helper.absoluteVec(Vec3.ZERO))));
                }
                z.discard();
            });
        });

        // prey out of sight behind a wall, keeping quiet, is let go after a while, and looked for where it was
        test("runner_lets_go_of_prey_it_neither_sees_nor_hears", Mind.LOSE + 60, helper -> {
            for (int z = 0; z <= 6; z++) {
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
                }
            }
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 3.5)));
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            z.setTarget(prey);
            helper.runAfterDelay(Mind.LOSE + 30, () -> {
                boolean let = z.getTarget() == null;
                boolean looks = z.mind().looking();
                z.discard();
                leave(helper, prey);
                check(helper, let, "it let them go");
                check(helper, looks, "and looks where they were");
                helper.succeed();
            });
        });

        // up a ladder is out of reach: it cannot climb one
        test("runner_cannot_climb_a_ladder", 200, helper -> {
            for (int y = 1; y <= 6; y++) {
                helper.setBlock(new BlockPos(3, y, 4), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, net.minecraft.core.Direction.NORTH));
            }
            helper.setBlock(new BlockPos(3, 7, 4), Blocks.STONE);
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(3.5, 8, 4.5)));
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(3, 1, 3)));
            z.mind().set(new Traits(Traits.Pace.RUN, Traits.Chase.DIRECT, Traits.Sense.UPWARD, Traits.Breaker.SOFT));
            z.setTarget(prey);
            check(helper, !z.onClimbable(), "a ladder is no climb to it");
            helper.runAfterDelay(120, () -> {
                double y = z.getY() - helper.absoluteVec(Vec3.ZERO).y;
                z.discard();
                leave(helper, prey);
                check(helper, y < 2.5, "it stayed at the foot of the ladder (at " + y + ")");
                helper.succeed();
            });
        });

        // one that breaks nothing stands at a plank wall
        test("runner_that_breaks_nothing_leaves_planks_be", 260, helper -> {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(3, 1, z), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(3, 2, z), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(3, 3, z), Blocks.STONE);
            }
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 3.5)));
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(1, 1, 3)));
            z.mind().set(new Traits(Traits.Pace.RUN, Traits.Chase.DIRECT, Traits.Sense.PLAIN, Traits.Breaker.NONE));
            z.setTarget(prey);
            helper.runAfterDelay(220, () -> {
                int planks = 0;
                for (int zz = 0; zz <= 6; zz++) {
                    planks += helper.getBlockState(new BlockPos(3, 1, zz)).is(Blocks.OAK_PLANKS) ? 1 : 0;
                    planks += helper.getBlockState(new BlockPos(3, 2, zz)).is(Blocks.OAK_PLANKS) ? 1 : 0;
                }
                z.discard();
                leave(helper, prey);
                check(helper, planks == 14, "the wall stands whole: " + planks + " of 14");
                helper.succeed();
            });
        });

        // a bite that draws blood takes five seconds off the time left
        test("a_bite_takes_time_off", helper -> {
            ServerPlayer bitten = player(helper, GameType.SURVIVAL);
            try {
                bitten.setData(Infection.LEFT, Infection.FULL);
                Mind.bit(true, bitten);
                check(helper, Infection.left(bitten) == Infection.FULL - Infection.BITE, "bitten: " + Infection.left(bitten));
                Mind.bit(false, bitten);
                check(helper, Infection.left(bitten) == Infection.FULL - Infection.BITE, "a miss is no bite");
                bitten.setData(Infection.LEFT, 30);
                Mind.bit(true, bitten);
                check(helper, Infection.left(bitten) > 0, "a bite never ends it outright");
            } finally {
                leave(helper, bitten);
            }
            helper.succeed();
        });

        // the one that was a villager: a golden apple does nothing for it, weakened or not
        test("mutated_zombie_villager_takes_no_apple", helper -> {
            MutatedZombieVillager z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(), new BlockPos(3, 1, 3)));
            ServerPlayer curer = player(helper, GameType.SURVIVAL);
            try {
                z.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
                curer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE));
                z.mobInteract(curer, InteractionHand.MAIN_HAND);
                check(helper, !z.isConverting(), "it is not being cured");
                check(helper, curer.getMainHandItem().is(Items.GOLDEN_APPLE), "the apple was not taken");
                check(helper, !z.onClimbable() && Mind.of(z) != null, "it is one of ours in all else");
            } finally {
                z.discard();
                leave(helper, curer);
            }
            helper.succeed();
        });

        // one left standing in a building stays put till a sound wakes it, then goes to see
        test("runner_left_standing_stays_till_it_hears", 300, helper -> {
            for (int x = 1; x <= 5; x++) {                               // a room round it: nothing of the other tests in sight
                for (int z = 1; z <= 5; z++) {
                    if (x == 1 || x == 5 || z == 1 || z == 5) {
                        for (int y = 1; y <= 3; y++) {
                            helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
                        }
                    }
                }
            }
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(3, 1, 3)));
            z.mind().stand();
            Vec3 from = z.position();
            helper.runAfterDelay(160, () -> {
                double moved = Math.sqrt(z.distanceToSqr(from.x, z.getY(), from.z));
                check(helper, moved < 1.0, "it stood where it was left (moved " + moved + ")");
                Hearing.sound(helper.getLevel(), GameEvent.BLOCK_CLOSE, helper.absoluteVec(new Vec3(0.5, 1, 0.5)), null, null);
                check(helper, !z.mind().standing() && z.mind().looking(), "a door heard: awake, gone to see");
                z.discard();
                helper.succeed();
            });
        });

        // whoever dies there gets up as one of them, with their name
        test("the_dead_get_up_as_one_of_them", helper -> {
            ServerPlayer dead = player(helper, GameType.SURVIVAL);
            dead.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            MutatedZombie risen = dev.nookofthedead.entity.Turning.turn(helper.getLevel(), dead);
            try {
                check(helper, risen.isAlive() && risen.getCustomName() != null && risen.getCustomName().getString().equals(dead.getName().getString()),
                        "one of them with their name");
                check(helper, risen.isPersistenceRequired(), "and it stays");
            } finally {
                risen.discard();
                leave(helper, dead);
            }
            helper.succeed();
        });

        // the tab loads whole (a broken one is dropped on load and never shows): each of ours there, off the root, with its
        // title and description in English and Japanese; a suppressant drunk gives "One More Day"
        test("advancements_are_whole", helper -> {
            var all = helper.getLevel().getServer().getAdvancements().getAllAdvancements();
            String[] names = {"root", "first_kill", "first_dose", "close_call", "put_down", "cured", "till_morning", "familiar_face", "institute_map", "vandal", "brewed"};
            for (String name : names) {
                var holder = helper.getLevel().getServer().getAdvancements().get(dev.nookofthedead.NookOfTheDead.id(name));
                check(helper, holder != null, name + " did not load");
                check(helper, name.equals("root") == holder.value().parent().isEmpty(), name + ": only the root has no parent");
                for (String part : new String[] {"title", "description"}) {
                    String key = "advancements.nookofthedead." + name + "." + part;
                    check(helper, net.minecraft.locale.Language.getInstance().has(key), key + " untranslated");
                }
            }
            long ours = all.stream().filter(a -> a.id().getNamespace().equals(dev.nookofthedead.NookOfTheDead.MODID)).count();
            check(helper, ours == names.length, "an advancement not checked here: " + ours);
            ServerPlayer drinker = player(helper, GameType.SURVIVAL);
            try {
                Infection.suppress(drinker);                                  // not infected: nothing held back, nothing given
                check(helper, !dev.nookofthedead.ZwAdvancements.has(drinker, "first_dose") && !Infection.infected(drinker),
                        "a suppressant taken while not infected changes nothing");
                drinker.setData(Infection.LEFT, Infection.FULL / 2);
                Infection.suppress(drinker);
                check(helper, dev.nookofthedead.ZwAdvancements.has(drinker, "first_dose"), "a suppressant taken while infected is One More Day");
            } finally {
                leave(helper, drinker);
            }
            helper.succeed();
        });

        // the animals the plague took go for a player they see, will not be tamed, fed or bred; the wolf always snarls
        test("mutated_animals_hunt_and_are_never_tamed", 400, helper -> {
            ServerPlayer prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new Vec3(5.5, 1, 5.5)));
            var cow = helper.spawn(ZwEntities.ZOMBIE_COW.get(), new BlockPos(1, 1, 1));
            var wolf = helper.spawn(ZwEntities.ZOMBIE_WOLF.get(), new BlockPos(1, 1, 5));
            var horse = helper.spawn(ZwEntities.MUTATED_ZOMBIE_HORSE.get(), new BlockPos(5, 1, 1));
            check(helper, wolf.isAngry(), "the wolf snarls");
            prey.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BONE));
            check(helper, wolf.mobInteract(prey, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS && !wolf.isTame(),
                    "a bone tames no wolf of theirs");
            prey.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            check(helper, cow.mobInteract(prey, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.PASS
                    && prey.getMainHandItem().is(Items.BUCKET), "no milk from their cow");
            check(helper, !cow.isFood(new ItemStack(Items.WHEAT)) && !horse.isFood(new ItemStack(Items.GOLDEN_CARROT)), "nothing feeds them");
            helper.succeedWhen(() -> {                                    // their target goals look now and then: wait for all three
                boolean all = cow.getTarget() == prey && wolf.getTarget() == prey && horse.getTarget() == prey;
                check(helper, all, "each goes for the player it sees: " + cow.getTarget() + " / " + wolf.getTarget() + " / " + horse.getTarget());
                cow.discard();
                wolf.discard();
                horse.discard();
                leave(helper, prey);
            });
        });

        // the chests' loot tables hold the suppressant as a potion (a table's bad field is dropped quietly: a bare bottle)
        test("chests_hold_the_suppressant_potion", helper -> {
            var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(dev.nookofthedead.world.Markers.table("potion"));
            int found = 0;
            for (int i = 0; i < 40; i++) {
                var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
                for (ItemStack stack : table.getRandomItems(params, i)) {
                    var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                    found += contents != null && contents.is(dev.nookofthedead.registry.ZwEffects.SUPPRESSANT_POTION) ? 1 : 0;
                }
            }
            check(helper, found > 10, "suppressants in the potion chests: " + found + " in 40");
            helper.succeed();
        });

        // every language has every line English has, and no other - Nookworks' languages, its regional copies too (written by
        // tools/gen_lang_copies.py: Hong Kong's the Taiwanese text, the Latin American the Spanish, Portugal's the Brazilian)
        test("languages_are_whole", helper -> {
            java.util.Map<String, java.util.Set<String>> keys = new java.util.HashMap<>();
            for (String lang : new String[] {"en_us", "ja_jp", "zh_cn", "zh_tw", "zh_hk", "ko_kr", "es_es", "es_mx", "es_ar", "es_cl",
                    "es_ec", "es_uy", "es_ve", "ru_ru", "pt_br", "pt_pt"}) {
                try (var in = SensesTests.class.getResourceAsStream("/assets/nookofthedead/lang/" + lang + ".json")) {
                    check(helper, in != null, lang + " is missing");
                    var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    keys.put(lang, json.getAsJsonObject().keySet());
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            for (var entry : keys.entrySet()) {
                java.util.Set<String> missing = new java.util.TreeSet<>(keys.get("en_us"));
                missing.removeAll(entry.getValue());
                java.util.Set<String> extra = new java.util.TreeSet<>(entry.getValue());
                extra.removeAll(keys.get("en_us"));
                check(helper, missing.isEmpty() && extra.isEmpty(), entry.getKey() + ": missing " + missing + ", extra " + extra);
            }
            helper.succeed();
        });

        // a diary is which one its first reader has not read yet: all of them before any comes twice (the user,
        // 2026-10-19: "同じ内容の日記を何度も見ることになるのは微妙"), and every page of each is in the language files
        test("diaries_come_unread_first", helper -> {
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            java.util.Set<Integer> read = new java.util.HashSet<>();
            int n = dev.nookofthedead.world.Diaries.PAGES.length;
            int town = n - dev.nookofthedead.world.Diaries.ONLY.size();   // a town's: all but those of one place only
            for (int k = 0; k < town; k++) {
                var book = dev.nookofthedead.world.Diaries.book(helper.getLevel().getRandom());
                check(helper, dev.nookofthedead.world.Diaries.unread(book), "a found diary is unread");
                dev.nookofthedead.world.Diaries.open(player, book);
                check(helper, !dev.nookofthedead.world.Diaries.unread(book), "opened, it is one");
                var pages = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
                String key = ((net.minecraft.network.chat.contents.TranslatableContents) pages.getFirst().raw().getContents()).getKey();
                read.add(Integer.parseInt(key.split("\\.")[2]));
            }
            check(helper, read.size() == town, "all " + town + " of a town's read before a second: " + read.size());
            var again = dev.nookofthedead.world.Diaries.book(helper.getLevel().getRandom());
            dev.nookofthedead.world.Diaries.open(player, again);
            check(helper, !dev.nookofthedead.world.Diaries.unread(again), "all read, it is one of them still");
            try (var in = SensesTests.class.getResourceAsStream("/assets/nookofthedead/lang/en_us.json")) {
                var keys = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().keySet();
                for (int d = 1; d <= n; d++) {
                    for (int page = 1; page <= dev.nookofthedead.world.Diaries.PAGES[d - 1]; page++) {
                        check(helper, keys.contains("diary.nookofthedead." + d + "." + page), "diary " + d + " page " + page + " is written");
                    }
                    check(helper, !keys.contains("diary.nookofthedead." + d + "." + (dev.nookofthedead.world.Diaries.PAGES[d - 1] + 1)),
                            "diary " + d + " has no page past its count");
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            helper.succeed();
        });

        // the suppressant thrown at one puts its mutation down: an ordinary zombie, or zombie villager (the same villager), in
        // its place; drunk or splashed, it gives a player twenty minutes; the brewing stand makes it splash and lingering
        test("suppressant_puts_the_mutation_down", helper -> {
            MutatedZombie z = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(2, 1, 2)));
            MutatedZombieVillager v = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(), new BlockPos(4, 1, 4)));
            var data = v.getVillagerData();
            var effect = dev.nookofthedead.registry.ZwEffects.SUPPRESSANT.get();
            effect.applyInstantaneousEffect(helper.getLevel(), null, null, z, 0, 1.0);
            effect.applyInstantaneousEffect(helper.getLevel(), null, null, v, 0, 1.0);
            check(helper, z.isRemoved() && v.isRemoved(), "the mutated ones are gone");
            var plainZombies = helper.getLevel().getEntities(net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.phys.AABB.ofSize(
                    helper.absoluteVec(new Vec3(3, 1, 3)), 10, 6, 10), e -> true);
            var plainVillagers = helper.getLevel().getEntities(net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER, net.minecraft.world.phys.AABB.ofSize(
                    helper.absoluteVec(new Vec3(3, 1, 3)), 10, 6, 10), e -> true);
            try {
                check(helper, plainZombies.size() == 1 && !(plainZombies.getFirst() instanceof dev.nookofthedead.entity.Mutated),
                        "an ordinary zombie in its place");
                check(helper, plainVillagers.size() == 1 && !(plainVillagers.getFirst() instanceof dev.nookofthedead.entity.Mutated)
                        && plainVillagers.getFirst().getVillagerData().equals(data), "an ordinary zombie villager, the same villager");
                ServerPlayer drinker = player(helper, GameType.SURVIVAL);
                drinker.setData(Infection.LEFT, 100);
                effect.applyInstantaneousEffect(helper.getLevel(), null, null, drinker, 0, 1.0);
                check(helper, Infection.left(drinker) == Infection.FULL, "a player splashed has twenty minutes again");
                leave(helper, drinker);
                var recipes = helper.getLevel().getServer().getRecipeManager();
                for (String id : new String[] {"brewing/splash_suppressant", "brewing/lingering_suppressant"}) {
                    check(helper, recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                            dev.nookofthedead.NookOfTheDead.id(id))).isPresent(), id + " is brewed");
                }
            } finally {
                plainZombies.forEach(net.minecraft.world.entity.Entity::discard);
                plainVillagers.forEach(net.minecraft.world.entity.Entity::discard);
            }
            helper.succeed();
        });
    }

    private SensesTests() {}
}
