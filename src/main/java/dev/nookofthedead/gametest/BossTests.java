package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.plain;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.MutatedZombie;
import dev.nookofthedead.entity.Vandal;
import dev.nookofthedead.item.VandalBloodItem;
import dev.nookofthedead.registry.ZwEffects;
import dev.nookofthedead.registry.ZwEntities;
import dev.nookofthedead.registry.ZwItems;
import dev.nookofthedead.world.Institute;
import dev.nookofthedead.world.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The Vandal, its institute and what leads to it and comes of it. */
final class BossTests {
    static void register() {
        // big, heavy, tough; it smashes stone in its way but not obsidian or an iron vault; its roar brings the dead; the
        // suppressant does nothing to it; it shows a boss bar to whoever sees it
        test("vandal_is_the_boss", 200, helper -> {
            Vandal vandal = helper.spawn(ZwEntities.VANDAL.get(), new BlockPos(1, 1, 3));
            vandal.setYRot(-90);
            vandal.setYHeadRot(-90);
            vandal.setYBodyRot(-90);
            try {
                check(helper, vandal.getAttributeValue(Attributes.SCALE) >= 2.4 && vandal.getMaxHealth() >= 300
                        && vandal.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "big, tough, unshaken");
                // its body as big as it looks, from the start (not only once its scale is changed)
                check(helper, vandal.getBbHeight() > 4.5 && vandal.getBbWidth() > 1.4,
                        "its frame is its size: " + vandal.getBbWidth() + " x " + vandal.getBbHeight());
                check(helper, vandal.isPersistenceRequired() && !vandal.removeWhenFarAway(10000), "it never despawns");
                for (int y = 1; y <= 3; y++) {                          // stone before it (east), obsidian and iron beside
                    helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE_BRICKS);
                    helper.setBlock(new BlockPos(2, y, 4), Blocks.OBSIDIAN);
                }
                var smash = vandal.wouldSmash().stream().map(p -> helper.relativePos(p)).toList();
                check(helper, smash.contains(new BlockPos(2, 1, 3)) && smash.stream().noneMatch(p -> p.getZ() == 4),
                        "it would smash the stone, not the obsidian: " + smash + " facing " + vandal.getDirection());
                MutatedZombie dead = plain(helper.spawn(ZwEntities.MUTATED_ZOMBIE.get(), new BlockPos(5, 1, 5)));
                vandal.roarNow(helper.getLevel());
                check(helper, dead.mind().looking() || dead.getTarget() != null, "its roar brings the dead");
                dead.discard();
                ZwEffects.SUPPRESSANT.get().applyInstantaneousEffect(helper.getLevel(), null, null, vandal, 0, 1.0);
                check(helper, vandal.isAlive() && !vandal.isRemoved(), "the suppressant does nothing to it");
                // no cheap way to wear it down or hold it: a fall does nothing to it, a minecart will not take it up
                float whole = vandal.getHealth();
                vandal.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 60.0F);
                check(helper, vandal.getHealth() == whole, "a fall does nothing to it");
                // nothing of the place either - lava, fire, drowning, Nookworks' Liquid Experience credited to a player near
                // (the user, 2026-10-19) - only what a player does to it
                var sources = helper.getLevel().damageSources();
                var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                var xp = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "liquid_experience")));
                for (var source : new java.util.ArrayList<>(java.util.List.of(sources.lava(), sources.onFire(), sources.drown(), sources.inWall()))) {
                    vandal.hurtServer(helper.getLevel(), source, 20.0F);
                }
                if (xp.isPresent()) {
                    vandal.hurtServer(helper.getLevel(), new net.minecraft.world.damagesource.DamageSource(xp.get(), null, player), 20.0F);
                }
                check(helper, vandal.getHealth() == whole, "nothing of the place hurts it");
                check(helper, xp.isPresent(), "Nookworks' Liquid Experience is there to try");
                vandal.hurtServer(helper.getLevel(), sources.playerAttack(player), 20.0F);
                check(helper, vandal.getHealth() < whole, "a player's blow does");
                // and Nookworks' laser, set up and fired on purpose (the user, 2026-10-19: "レーザーは効いてもいい")
                var laser = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "laser")));
                check(helper, laser.isPresent() && Vandal.hurtBy(new net.minecraft.world.damagesource.DamageSource(laser.get())),
                        "a laser does");
                // nothing shot or thrown (the user, 2026-10-19: "飛び道具効かない")
                var arrow = net.minecraft.world.entity.EntityTypes.ARROW.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                check(helper, arrow != null && !Vandal.hurtBy(sources.arrow((net.minecraft.world.entity.projectile.arrow.AbstractArrow) arrow, player)),
                        "an arrow does nothing to it");
                var cart = net.minecraft.world.entity.EntityTypes.MINECART.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                cart.snapTo(vandal.getX(), vandal.getY(), vandal.getZ(), 0.0F, 0.0F);
                helper.getLevel().addFreshEntity(cart);
                vandal.startRiding(cart);
                check(helper, !vandal.isPassenger(), "a minecart will not take it");
                cart.discard();
            } finally {
                vandal.discard();
            }
            helper.succeed();
        });

        // the way to it: four scraps make a scrawled map (outside the fallen world its marks match nothing); the
        // institutes stand now and then in the town and the settlements, never at home
        // the sewer bloom (0.3): rooted; shut, a blow does a quarter and an arrow nothing; fire twice; woken by one near, it
        // opens and lashes; a vine thrown drags one far off in toward it; it never despawns and has no bar
        test("sewer_bloom_is_rooted_and_guarded", 60, helper -> {
            var level = helper.getLevel();
            var sources = level.damageSources();
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            dev.nookofthedead.entity.SewerBloom shut = helper.spawn(ZwEntities.SEWER_BLOOM.get(), new BlockPos(2, 1, 2));
            dev.nookofthedead.entity.SewerBloom shot = helper.spawn(ZwEntities.SEWER_BLOOM.get(), new BlockPos(6, 1, 2));
            dev.nookofthedead.entity.SewerBloom burnt = helper.spawn(ZwEntities.SEWER_BLOOM.get(), new BlockPos(2, 1, 6));
            float whole = shut.getMaxHealth();
            check(helper, whole >= 120 && shut.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0 && !shut.isPushable()
                    && shut.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.0, "big, rooted");
            check(helper, shut.isPersistenceRequired() && !shut.removeWhenFarAway(10000), "it never despawns");
            check(helper, !shut.open(), "shut, with no one near");
            shut.hurtServer(level, sources.playerAttack(player), 8.0F);
            float lost = whole - shut.getHealth();                     // a quarter of 8, less what its armour takes
            check(helper, lost > 1.0F && lost <= 2.01F, "shut, a blow does a quarter: " + lost);
            var arrow = net.minecraft.world.entity.EntityTypes.ARROW.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            shot.hurtServer(level, sources.arrow(arrow, player), 9.0F);
            check(helper, shot.getHealth() == whole, "shut, an arrow nothing");
            burnt.hurtServer(level, sources.onFire(), 4.0F);
            check(helper, Math.abs(burnt.getHealth() - (whole - 1.0F)) < 0.01F, "shut, fire a quarter: " + burnt.getHealth());
            float before = burnt.getHealth();
            burnt.hurtServer(level, sources.fall(), 50.0F);
            check(helper, burnt.getHealth() == before, "a fall nothing");
            // a vine thrown at one far off: they are drawn in, toward it
            var far = helper.makeMockPlayer(GameType.SURVIVAL);
            far.setPos(helper.absoluteVec(new Vec3(12.5, 1, 2.5)));
            shot.grab(level, far);
            check(helper, far.getDeltaMovement().x < -0.5, "drawn in toward it: " + far.getDeltaMovement());
            // one near wakes it: open, and the lash
            player.setPos(helper.absoluteVec(new Vec3(2.5, 1, 4.6)));
            shut.setTarget(player);
            helper.runAfterDelay(4, () -> {
                check(helper, shut.open(), "one near: it opens");
                helper.succeed();
            });
        });

        // the road to the end (0.3): one second institute a world, about seven thousand out, the same asked twice; no lot of
        // the town's laid on it; at the back of each refuge, their note on a lectern and three Lode Pearls bound to its lodestone
        test("the_refuge_points_the_way_on", helper -> {
            long seed = helper.getLevel().getSeed();
            var site = dev.nookofthedead.world.Ascent.site(seed);
            double out = Math.hypot(site.x(), site.z());
            check(helper, Math.abs(out - dev.nookofthedead.world.Ascent.DISTANCE) < 2, "about seven thousand out: " + out);
            check(helper, dev.nookofthedead.world.Ascent.site(seed).equals(site), "the same asked twice");
            int bx = Math.floorDiv(site.x(), dev.nookofthedead.world.Town.BLOCK);
            int bz = Math.floorDiv(site.z(), dev.nookofthedead.world.Town.BLOCK);
            check(helper, dev.nookofthedead.world.Town.lots(seed, bx, bz).stream().noneMatch(l -> l.covers(site.x(), site.z())), "no lot of the town's on it");
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
            BlockPos under = helper.absolutePos(new BlockPos(1, 1, 1)).subtract(new BlockPos(21, 2, 60));
            var box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)),
                    helper.absolutePos(new BlockPos(4, 3, 4)));
            dev.nookofthedead.world.Refuge.leftBehind(helper.getLevel(), under,
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), box);
            var lectern = helper.getBlockEntity(new BlockPos(1, 1, 1), net.minecraft.world.level.block.entity.LecternBlockEntity.class);
            check(helper, lectern.hasBook() && lectern.getBook().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT) != null,
                    "their note on a lectern");
            var chest = helper.getBlockEntity(new BlockPos(2, 1, 1), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            ItemStack pearls = ItemStack.EMPTY;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(dev.nookworks.registry.ModItems.LODE_PEARL.get())) {
                    pearls = chest.getItem(i);
                }
            }
            var to = dev.nookworks.pearl.LodePearlItem.lodestone(pearls);
            check(helper, pearls.getCount() == dev.nookofthedead.world.Refuge.LEFT && to.isPresent()
                    && to.get().pos().equals(site.lodestone()) && to.get().dimension().equals(dev.nookworks.ifworld.IfWorld.KEY),
                    "three pearls to the second institute: " + pearls + " " + to);
            helper.succeed();
        });

        // a roofed, lit room keeps the newly come from being sent to one in it - but not on Hard or in Hardcore (the user,
        // 2026-10-19); out in the open, neither
        test("a_room_shelters_but_not_on_hard", 100, helper -> {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    for (int y = 1; y < 5; y++) {
                        boolean wall = x == 0 || x == 4 || z == 0 || z == 4 || y == 4;
                        helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                    }
                }
            }
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.GLOWSTONE);
            BlockPos in = helper.absolutePos(new BlockPos(2, 1, 2));
            BlockPos out = helper.absolutePos(new BlockPos(2, 6, 2));
            helper.succeedWhen(() -> {
                check(helper, dev.nookofthedead.entity.Horde.sheltered(helper.getLevel(), in, false), "in the room: sheltered");
                check(helper, !dev.nookofthedead.entity.Horde.sheltered(helper.getLevel(), in, true), "on Hard, not");
                check(helper, !dev.nookofthedead.entity.Horde.sheltered(helper.getLevel(), out, false), "out in the open, not");
            });
        });

        // the refuge sealed (0.3): its bulkheads unbreakable - no tool, no blast, no Vandal, no Wither, nothing moves them -
        // set round it in the rock; the passage from the hall left open, its plug of iron left to dig, its floor sealed
        test("the_refuge_is_sealed_but_from_the_hall", helper -> {
            var bulkhead = dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get().defaultBlockState();
            check(helper, bulkhead.getDestroySpeed(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) < 0, "no tool breaks it");
            check(helper, bulkhead.getBlock().getExplosionResistance() >= 3600000.0F, "no blast breaks it");
            check(helper, bulkhead.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    dev.nookofthedead.NookOfTheDead.id("vandal_proof"))), "the Vandal does not");
            check(helper, bulkhead.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE), "nor the Wither");
            check(helper, bulkhead.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.IMMOVEABLE, "nothing moves it");
            for (int x = 0; x < 3; x++) {
                for (int y = 1; y < 4; y++) {
                    helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
                }
            }
            // the template's (36..38, 1..3, 36) - the plug's floor and side, the plug - laid on the test's (0..2, 1..3, 1)
            BlockPos under = helper.absolutePos(new BlockPos(0, 1, 1)).subtract(new BlockPos(36, 1, 36));
            var box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)),
                    helper.absolutePos(new BlockPos(4, 4, 4)));
            dev.nookofthedead.world.Refuge.hull(helper.getLevel(), under,
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(), box);
            check(helper, helper.getBlockState(new BlockPos(1, 1, 1)).is(bulkhead.getBlock()), "the floor under the plug sealed");
            check(helper, helper.getBlockState(new BlockPos(0, 2, 1)).is(bulkhead.getBlock()), "the passage's side sealed");
            check(helper, helper.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.STONE), "the plug's place left to dig");
            helper.succeed();
        });

        // the Tower's measures: every working lift within an Elevator's reach (64) whatever the ground, the broken stretch out
        // of it; the stair's sides coming out exactly at the second relay; the third relay and the base in the stratosphere
        test("the_tower_adds_up", helper -> {
            int reach = 64;
            for (int ground = dev.nookofthedead.world.Town.SURFACE + 1; ground <= dev.nookofthedead.world.Town.SURFACE + 40; ground++) {
                var site = new dev.nookofthedead.world.Ascent.Site(0, 0, ground);
                int landing = dev.nookofthedead.world.Tower.landing(site);
                check(helper, landing - ground <= reach && dev.nookofthedead.world.Tower.R1 - landing <= reach,
                        "the lift to the first relay from ground " + ground);
            }
            check(helper, dev.nookofthedead.world.Tower.R2 - dev.nookofthedead.world.Tower.R1 > reach
                    && dev.nookofthedead.world.Tower.R3 - dev.nookofthedead.world.Tower.R2 > reach, "the broken stretches out of the lift's reach");
            check(helper, (dev.nookofthedead.world.Tower.R2 - dev.nookofthedead.world.Tower.R1) % 9 == 0, "the stair comes out at the second relay");
            check(helper, dev.nookofthedead.world.Tower.BASE - dev.nookofthedead.world.Tower.R3 <= reach, "the last lift");
            check(helper, dev.nookofthedead.world.Tower.R3 >= dev.nookofthedead.world.RegionBiomes.STRATOSPHERE
                    && dev.nookofthedead.world.Tower.BASE + 36 <= 447, "the third relay in the thin air, the mast under the ceiling");
            helper.succeed();
        });

        // the sky base's (0.3): the thin air's zombie leaps at its prey and is not hurt by a fall that would; the Sentry
        // Golem shoots a bolt, never sets on the dead, and the dead never on it; Smite is nothing to it
        test("the_sky_base_has_its_own", 60, helper -> {
            var level = helper.getLevel();
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(7.5, 1, 2.5)));
            dev.nookofthedead.entity.ThinAirZombie thin = plain(helper.spawn(ZwEntities.THIN_AIR_ZOMBIE.get(), new BlockPos(2, 1, 2)));
            thin.leap(player, 5.0);
            check(helper, thin.getDeltaMovement().y > 0.4 && thin.getDeltaMovement().x > 0.3, "it leaps at its prey: " + thin.getDeltaMovement());
            dev.nookofthedead.entity.SentryGolem golem = helper.spawn(ZwEntities.SENTRY_GOLEM.get(), new BlockPos(2, 1, 6));
            check(helper, !thin.canAttack(golem) && !golem.canAttack(thin) && golem.canAttack(player), "the guard and the dead leave each other be");
            check(helper, !golem.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE), "Smite is nothing to it");
            check(helper, golem.isPersistenceRequired() && !golem.removeWhenFarAway(10000), "it never despawns");
            int before = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(2, 1, 6))).inflate(4)).size();
            golem.performRangedAttack(player, 1.0F);
            int after = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.arrow.Arrow.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(2, 1, 6))).inflate(4)).size();
            check(helper, after == before + 1, "a bolt shot");
            helper.succeed();
        });

        // the dowsing rod's secret (0.3): in the If it points to the nearest institute - the middle of its lot
        test("the_rod_finds_the_nearest_institute", 200, helper -> {
            var server = helper.getLevel().getServer();
            var world = dev.nookworks.ifworld.IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            var ifLevel = dev.nookworks.ifworld.IfLevel.open(server);
            long seed = ifLevel.getSeed();
            int[] found = null;
            for (int r = 0; r <= 40 && found == null; r++) {
                for (int bx = -r; bx <= r && found == null; bx++) {
                    for (int bz = -r; bz <= r && found == null; bz++) {
                        if (Math.max(Math.abs(bx), Math.abs(bz)) == r && Institute.at(seed, bx, bz)) {
                            found = new int[] {bx, bz};
                        }
                    }
                }
            }
            check(helper, found != null, "an institute within forty blocks of the grid");
            int b = dev.nookofthedead.world.Town.BLOCK;
            BlockPos near = new BlockPos(found[0] * b + b / 2, 70, found[1] * b + b / 2);
            BlockPos institute = dev.nookofthedead.world.Dowsing.nearest(ifLevel, near);
            check(helper, institute != null && Math.floorDiv(institute.getX(), b) == found[0] && Math.floorDiv(institute.getZ(), b) == found[1],
                    "it points into that institute's block: " + institute);
            helper.succeed();
        });

        test("map_scraps_lead_to_an_institute", helper -> {
            var recipes = helper.getLevel().getServer().getRecipeManager();
            for (String id : new String[] {"scrawled_map", "cleansed_flesh", "brewing/suppressant"}) {
                check(helper, recipes.byKey(ResourceKey.create(Registries.RECIPE, NookOfTheDead.id(id))).isPresent(), id + " is made");
            }
            ServerPlayer reader = player(helper, GameType.SURVIVAL);
            try {
                reader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZwItems.SCRAWLED_MAP.get()));
                InteractionResult result = ZwItems.SCRAWLED_MAP.get().use(helper.getLevel(), reader, InteractionHand.MAIN_HAND);
                check(helper, result == InteractionResult.FAIL && reader.getMainHandItem().is(ZwItems.SCRAWLED_MAP.get()),
                        "outside the fallen world it stays a scrawl");
            } finally {
                leave(helper, reader);
            }
            long seed = 20261015L;
            int sites = 0;
            for (int qx = -6; qx <= 6; qx++) {
                for (int qz = -6; qz <= 6; qz++) {
                    long[] site = Institute.site(seed, qx, qz);
                    if (site == null) {
                        continue;
                    }
                    sites++;
                    Region r = Region.of(seed, (int) site[0], (int) site[1]);
                    check(helper, r == Region.CITY || r == Region.SETTLEMENT, "an institute in the town or a settlement");
                    check(helper, Math.abs(site[0]) > Region.HOME || Math.abs(site[1]) > Region.HOME, "never where one comes in");
                    check(helper, Institute.at(seed, (int) site[0], (int) site[1]), "its block knows it");
                }
            }
            check(helper, sites > 3 && sites < 13 * 13 / 2, "rare: " + sites + " in 169 cells");
            check(helper, Institute.nearest(seed, 0, 0, 6) != null, "one within reach of home");
            helper.succeed();
        });

        // under the world's cap, the dead far from everyone and after nobody make room for a player come late (only the
        // dead in loaded chunks count, so the player is the one far off here: what matters is how far apart they are)
        test("runner_horde_makes_room_for_one_come_late", helper -> {
            ServerPlayer here = player(helper, GameType.SURVIVAL);
            here.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 3.5)));
            ServerPlayer late = player(helper, GameType.SURVIVAL);
            late.snapTo(helper.absoluteVec(new Vec3(90.5, 1, 3.5)));
            java.util.function.Function<Vec3, MutatedZombie> come = at -> {    // as the horde's own come: not kept for good
                MutatedZombie z = plain(ZwEntities.MUTATED_ZOMBIE.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT));
                Vec3 w = helper.absoluteVec(at);
                z.snapTo(w.x, w.y, w.z, 0, 0);
                helper.getLevel().addFreshEntity(z);
                return z;
            };
            MutatedZombie idle = come.apply(new Vec3(1.5, 1, 1.5));
            MutatedZombie hunting = come.apply(new Vec3(5.5, 1, 5.5));
            hunting.setTarget(here);
            try {
                dev.nookofthedead.entity.Horde.thin(helper.getLevel(), java.util.List.of(here, late));
                check(helper, !idle.isRemoved(), "one near a player stays");
                dev.nookofthedead.entity.Horde.thin(helper.getLevel(), java.util.List.of(late));
                check(helper, idle.isRemoved() && !hunting.isRemoved(), "one far from every player, after nobody, makes room; one after someone stays");
            } finally {
                idle.discard();
                hunting.discard();
                leave(helper, here);
                leave(helper, late);
            }
            helper.succeed();
        });

        // the Vandal's blood makes cleansed flesh of rotten flesh and comes back a little less full, sixteen times
        test("vandal_blood_comes_back_from_the_grid", helper -> {
            ItemStack blood = new ItemStack(ZwItems.VANDAL_BLOOD.get());
            int uses = 0;
            while (!blood.isEmpty() && uses < 100) {
                var left = blood.getItem().getCraftingRemainder(blood);
                uses++;
                blood = left == null ? ItemStack.EMPTY : left.create();
            }
            check(helper, uses == VandalBloodItem.USES, "it lasts " + uses + " crafts");
            var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(
                    ResourceKey.create(Registries.LOOT_TABLE, NookOfTheDead.id("entities/vandal")));
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY,
                            ZwEntities.MUTATED_ZOMBIE.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,
                            helper.getLevel().damageSources().generic())
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
            check(helper, table.getRandomItems(params, 1L).stream().anyMatch(s -> s.is(ZwItems.VANDAL_BLOOD.get())), "it always leaves its blood");
            check(helper, !new ItemStack(Items.ROTTEN_FLESH).is(ZwItems.CLEANSED_FLESH.get()), "cleansed flesh is its own");
            helper.succeed();
        });
    }

    private BossTests() {}
}
