package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.plain;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookofthedead.entity.InfectedRat;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.registry.ZwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Dark Matter's safe corner, the dead that cannot be kept or farmed, and the small things the plague took. */
final class ShelterTests {
    static void register() {
        // within a Dark Matter's reach (lit or not) none come: seven out as water spreads, from the layer under it to three above
        test("dark_matter_keeps_the_dead_from_coming", helper -> {
            ServerLevel level = helper.getLevel();
            var darkMatter = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nookworks", "dark_matter"));
            helper.setBlock(new BlockPos(1, 1, 1), darkMatter);
            check(helper, MutatedZombie.safe(level, helper.absolutePos(new BlockPos(5, 2, 4))), "five and three across, a layer up: safe");
            check(helper, MutatedZombie.safe(level, helper.absolutePos(new BlockPos(1, 4, 1))), "three up: safe");
            check(helper, !MutatedZombie.safe(level, helper.absolutePos(new BlockPos(6, 1, 4))), "eight across: not");
            check(helper, !MutatedZombie.safe(level, helper.absolutePos(new BlockPos(1, 5, 1))), "four up: not");
            helper.succeed();
        });

        // they will not sit in a boat; and what dies of anything but a player's hand leaves nothing
        test("the_dead_take_no_boat_and_traps_get_nothing", helper -> {
            ServerLevel level = helper.getLevel();
            MutatedZombie zombie = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(2, 1, 2)));
            var boat = helper.spawn(EntityTypes.OAK_BOAT, new BlockPos(4, 1, 4));
            check(helper, !zombie.startRiding(boat, false, false) && !zombie.isPassenger(), "no boat for the dead");
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            zombie.setDropChance(EquipmentSlot.HEAD, 2.0F);
            zombie.hurtServer(level, level.damageSources().fall(), 1000);
            AABB round = new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(3);
            check(helper, !zombie.isAlive() && level.getEntitiesOfClass(ItemEntity.class, round).isEmpty(), "a fall leaves nothing");
            boat.discard();
            helper.succeed();
        });

        // a rat runs as a silverfish runs, but never into stone
        test("rats_do_not_hide_in_stone", helper -> {
            InfectedRat rat = helper.spawn(ZwEntities.INFECTED_RAT.get(), new BlockPos(2, 1, 2));
            boolean hides = rat.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal().getClass().getEnclosingClass() == net.minecraft.world.entity.monster.Silverfish.class);
            check(helper, !hides, "no silverfish's hiding or waking");
            rat.discard();
            helper.succeed();
        });

        // a bat of the plague goes for a player it sees and bites
        test("infected_bats_bite", 200, helper -> {
            var prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 3.5)));
            var bat = helper.spawn(ZwEntities.INFECTED_BAT.get(), new BlockPos(1, 3, 1));
            helper.succeedWhen(() -> {
                if (prey.getHealth() >= prey.getMaxHealth()) {
                    throw helper.assertionException(Component.literal("not bitten yet"));
                }
                bat.discard();
            });
        });

        // in the fallen world one held - named here - goes all the same once no one is near
        test("if_the_named_dead_go_too", helper -> {
            var server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            BlockPos at = new BlockPos(4000, 120, 4000);
            ifLevel.getChunk(at);
            MutatedZombie named = ZwEntities.MUTATED_ZOMBIE.get().create(ifLevel, EntitySpawnReason.COMMAND);
            MutatedZombie plainOne = ZwEntities.MUTATED_ZOMBIE.get().create(ifLevel, EntitySpawnReason.COMMAND);
            named.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            plainOne.snapTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
            named.setCustomName(Component.literal("Pet"));
            named.setPersistenceRequired();
            ifLevel.addFreshEntity(named);
            ifLevel.addFreshEntity(plainOne);
            try {
                named.checkDespawn();
                check(helper, named.isRemoved(), "the named one goes");
                check(helper, dev.nookofthedead.entity.Unkept.held(named) && !dev.nookofthedead.entity.Unkept.held(plainOne), "named is held");
            } finally {
                named.discard();
                plainOne.discard();
            }
            helper.succeed();
        });
    }

    static void registerTraces() {
        // a bell rung: every one of them in its reach sets off for it, those past it do not
        test("runner_called_by_a_bell", helper -> {
            MutatedZombie near = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(2, 1, 2)));
            MutatedZombie behind = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(6, 1, 6)));
            net.minecraft.world.phys.Vec3 bell = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 1, 4)));
            int heard = dev.nookofthedead.entity.Hearing.toll(helper.getLevel(), bell);
            check(helper, heard >= 2, "both heard it: " + heard);
            check(helper, near.mind().lead() != null && near.mind().lead().equals(bell) && behind.mind().lead() != null, "both going to it");
            helper.succeed();
        });

        // a screamer that sees someone brings every idle one in its reach after them
        test("runner_brought_by_a_scream", helper -> {
            MutatedZombie screamer = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(2, 1, 2)));
            screamer.mind().set(new dev.nookofthedead.entity.Traits(dev.nookofthedead.entity.Traits.Pace.RUN, dev.nookofthedead.entity.Traits.Chase.DIRECT,
                    dev.nookofthedead.entity.Traits.Sense.PLAIN, dev.nookofthedead.entity.Traits.Breaker.SOFT, dev.nookofthedead.entity.Traits.Voice.SCREAM));
            check(helper, screamer.screamer(), "a screamer shows as one (its look, for the clients)");
            var villager = helper.spawn(ZwEntities.MUTATED_ZOMBIE_VILLAGER.get(), new BlockPos(1, 1, 5));
            villager.mind().set(new dev.nookofthedead.entity.Traits(dev.nookofthedead.entity.Traits.Pace.RUN, dev.nookofthedead.entity.Traits.Chase.DIRECT,
                    dev.nookofthedead.entity.Traits.Sense.PLAIN, dev.nookofthedead.entity.Traits.Breaker.SOFT, dev.nookofthedead.entity.Traits.Voice.SCREAM));
            check(helper, villager.mind().traits().voice() == dev.nookofthedead.entity.Traits.Voice.GROAN, "a zombie villager never screams");
            villager.discard();
            MutatedZombie other = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(6, 1, 6)));
            var prey = player(helper, GameType.SURVIVAL);
            prey.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 2))));
            int came = screamer.mind().scream(helper.getLevel(), prey);
            boolean after = other.getTarget() == prey;
            ZwGameTests.leave(helper, prey);
            check(helper, came >= 1 && after, "the other one is after them: " + came);
            helper.succeed();
        });

        // a grenade bursts: the dead round it go, a block beside it stays (glass, the most breakable), and an armour stand
        // (a fixture, as a frame is)
        test("runner_put_down_by_a_grenade", 80, helper -> {
            ServerLevel level = helper.getLevel();
            MutatedZombie a = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(3, 1, 2)));
            MutatedZombie b = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(2, 1, 4)));
            a.setNoAi(true);
            b.setNoAi(true);
            helper.setBlock(new BlockPos(4, 1, 4), Blocks.GLASS);
            var stand = helper.spawn(net.minecraft.world.entity.EntityTypes.ARMOR_STAND, new BlockPos(4, 1, 2));
            var remains = helper.spawn(ZwEntities.REMAINS.get(), new BlockPos(2, 1, 2));
            var at = helper.absolutePos(new BlockPos(3, 1, 3));
            var grenade = new dev.nookofthedead.entity.Grenade(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5,
                    new ItemStack(dev.nookofthedead.registry.ZwItems.GRENADE.get()));
            grenade.light(2);
            level.addFreshEntity(grenade);
            helper.runAfterDelay(10, () -> {
                check(helper, !a.isAlive() && !b.isAlive(), "the two beside it put down");
                check(helper, helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.GLASS), "the glass beside it whole");
                check(helper, stand.isAlive() && !stand.isRemoved(), "the armour stand beside it standing");
                check(helper, !remains.isRemoved(), "the remains beside it whole");
                check(helper, grenade.isRemoved(), "the grenade gone");
                helper.succeed();
            });
        });

        // the plague's animals leave something only to a player's hand: struck once by a player and then killed by a fall
        // (a trap), nothing (the review, 2026-10-18: the one blow let the trap's kill drop it all); killed by the player, it does
        test("trapped_after_a_blow_leaves_nothing", 60, helper -> {
            ServerLevel level = helper.getLevel();
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var trapped = helper.spawn(ZwEntities.ZOMBIE_COW.get(), new BlockPos(1, 1, 1));
            trapped.setNoAi(true);
            trapped.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
            trapped.hurtServer(level, level.damageSources().fall(), 1000.0F);
            for (int k = 0; k < 4; k++) {                               // a few: what one drops is to chance
                var struck = helper.spawn(ZwEntities.ZOMBIE_COW.get(), new BlockPos(4, 1, 4));
                struck.setNoAi(true);
                struck.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
            }
            helper.runAfterDelay(5, () -> {
                var near = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(1, 1, 1))).inflate(1.5);
                var far = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(4, 1, 4))).inflate(1.5);
                check(helper, level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, near).isEmpty(),
                        "the trapped one left nothing");
                check(helper, !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, far).isEmpty(),
                        "the ones the player killed left something");
                helper.succeed();
            });
        });

        // the traces lie about the town: some blocks walled in, a fire's band of burnt houses, canals with their water
        // level below their banks - and the same asked twice
        test("the_town_shows_what_happened", helper -> {
            long seed = 20261017L;
            int walled = 0;
            int burnt = 0;
            int canals = 0;
            for (int bx = -30; bx <= 30; bx++) {
                for (int bz = -30; bz <= 30; bz++) {
                    walled += dev.nookofthedead.world.Traces.quarantined(seed, bx, bz) ? 1 : 0;
                    int x = bx * 80 + 40;
                    int z = bz * 80 + 40;
                    burnt += dev.nookofthedead.world.Traces.burning(seed, x, z) ? 1 : 0;
                }
            }
            for (int x = -1200; x < 1200; x += 3) {
                for (int z = -1200; z < 1200; z += 37) {
                    int c = dev.nookofthedead.world.Traces.canal(seed, x, z);
                    if (c > 0) {
                        canals++;
                        check(helper, c == dev.nookofthedead.world.Traces.canal(seed, x, z) && c < dev.nookofthedead.world.Town.height(seed, x, z) + 0.5,
                                "a canal's water under its ground at " + x + "," + z);
                    }
                }
            }
            check(helper, walled > 0 && burnt > 0 && canals > 0, "walled " + walled + ", burnt " + burnt + ", canal columns " + canals);
            helper.succeed();
        });
    }

    private ShelterTests() {}
}
