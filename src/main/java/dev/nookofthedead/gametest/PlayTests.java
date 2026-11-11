package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.leave;
import static dev.nookofthedead.gametest.ZwGameTests.player;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.ending.VaccineBlock;
import dev.nookofthedead.item.ScrawledMapItem;
import dev.nookofthedead.registry.ZwBlocks;
import dev.nookofthedead.registry.ZwItems;
import dev.nookofthedead.world.Institute;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The road's own items used as a player uses them - through the game's way in (ServerPlayerGameMode, the crafting menu's
 * clicks), in survival - not by calling the item. The released 1.0 lost the institute's map that way (the game puts what
 * a use hands back into the hand after it), and a test that called {@code Item#use} could not see it.
 */
final class PlayTests {
    static void register() {
        test("scrawled_map_unfolds_in_survival", helper -> {
            var server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            BlockPos site = null;
            for (int i = 0; site == null && i < 40; i++) {
                site = Institute.nearest(ifLevel.getSeed(), i * 4000, 0, ScrawledMapItem.REACH);
            }
            check(helper, site != null, "an institute to point to");
            for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
                for (int held : new int[] {1, 2}) {
                    ServerPlayer reader = player(helper, mode);
                    try {
                        reader.setPos(site.getX() + 0.5, 100, site.getZ() + 0.5);
                        reader.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZwItems.SCRAWLED_MAP.get(), held));
                        reader.gameMode.useItem(reader, ifLevel, reader.getMainHandItem(), InteractionHand.MAIN_HAND);
                        int maps = 0;
                        int scrawls = 0;
                        for (ItemStack stack : reader.getInventory()) {
                            if (stack.is(Items.FILLED_MAP) && stack.get(DataComponents.ITEM_NAME) != null) {
                                maps += stack.getCount();
                            } else if (stack.is(ZwItems.SCRAWLED_MAP.get())) {
                                scrawls += stack.getCount();
                            }
                        }
                        int left = mode == GameType.CREATIVE ? held : held - 1;
                        check(helper, maps == 1 && scrawls == left,
                                mode + " with " + held + ": " + maps + " institute map(s), " + scrawls + " scrawl(s) left");
                    } finally {
                        leave(helper, reader);
                    }
                }
            }
            helper.succeed();
        });

        test("launch_keys_go_in_in_survival", helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlockAndUpdate(at, ZwBlocks.VACCINE.get().defaultBlockState());
            ServerPlayer holder = player(helper, GameType.SURVIVAL);
            try {
                holder.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
                holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZwItems.LAUNCH_KEY.get(), 5));
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false);
                for (int i = 1; i <= 5; i++) {
                    holder.gameMode.useItemOn(holder, level, holder.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                    int keys = level.getBlockState(at).getValue(VaccineBlock.KEYS);
                    int want = Math.min(i, VaccineBlock.NEEDED);
                    check(helper, keys == want, "after " + i + " uses " + keys + " keys in");
                    check(helper, holder.getMainHandItem().getCount() == 5 - want,
                            "after " + i + " uses " + holder.getMainHandItem().getCount() + " in hand");
                }
            } finally {
                leave(helper, holder);
            }
            helper.succeed();
        });

        // one of the dead held where it is (here named; the sky base's key-holders, standing in their rooms) goes when no one
        // is near - but never with a launch key in its hands: the holders went so, and the end of the road with them
        test("key_holders_never_despawn", helper -> {
            var server = helper.getLevel().getServer();
            IfWorld world = IfWorld.get(server);
            while (!world.rolled()) {
                world.addThrow(1 + helper.getLevel().getRandom().nextInt(6));
            }
            ServerLevel ifLevel = IfLevel.open(server);
            // on Peaceful the type is let in, so the base's key-holders are made, loaded and kept there (ThinAirZombie)
            check(helper, dev.nookofthedead.registry.ZwEntities.THIN_AIR_ZOMBIE.get().isAllowedInPeaceful(),
                    "thin-air zombies let into a Peaceful world");
            BlockPos at = new BlockPos(-20000, 120, 20000);
            ifLevel.getChunk(at);
            for (boolean holding : new boolean[] {true, false}) {
                var dead = dev.nookofthedead.registry.ZwEntities.THIN_AIR_ZOMBIE.get().create(ifLevel,
                        net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
                check(helper, dead != null, "one is made");
                dead.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                if (holding) {
                    dead.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ZwItems.LAUNCH_KEY.get()));
                }
                dead.setPersistenceRequired();
                dead.setCustomName(net.minecraft.network.chat.Component.literal("held"));
                ifLevel.addFreshEntity(dead);
                try {
                    net.neoforged.neoforge.event.EventHooks.checkMobDespawn(dead);
                    check(helper, dead.isRemoved() != holding,
                            (holding ? "with a key it went" : "without a key it stayed - the test holds none") + ": " + dead);
                } finally {
                    dead.discard();
                }
            }
            helper.succeed();
        });

        // the map marks only an institute that is there: its grounds laid out in the town, none by the second institute or in
        // the capital, where a site may fall but none is built
        test("scrawled_map_marks_a_built_institute", helper -> {
            int asked = 0;
            for (long seed : new long[] {IfWorld.seedOf("555315"), IfWorld.seedOf("233431"), IfWorld.seedOf("614252"), 20261015L}) {
                for (int x = -16000; x <= 16000; x += 800) {
                    for (int z = -16000; z <= 16000; z += 800) {
                        BlockPos found = Institute.nearest(seed, x, z, ScrawledMapItem.REACH);
                        if (found == null) {
                            continue;
                        }
                        asked++;
                        int bx = Math.floorDiv(found.getX(), dev.nookofthedead.world.Town.BLOCK);
                        int bz = Math.floorDiv(found.getZ(), dev.nookofthedead.world.Town.BLOCK);
                        check(helper, Institute.at(seed, bx, bz), "the map marked block " + bx + "," + bz + " where none is laid");
                        boolean built = false;
                        for (var lot : dev.nookofthedead.world.Town.lots(seed, bx, bz)) {
                            built |= lot.building().map(dev.nookofthedead.world.Town.INSTITUTE::equals).orElse(false);
                        }
                        check(helper, built, "no institute's grounds in block " + bx + "," + bz + " the map marked");
                    }
                }
            }
            check(helper, asked > 100, "the map asked often enough: " + asked);
            helper.succeed();
        });

        // in the recipe book once one of what makes them is picked up (the user, 2026-10-29: the grenade was never there)
        test("recipes_show_in_the_book", helper -> {
            String[][] unlocks = {{"grenade", "minecraft:gunpowder"}, {"scrawled_map", "nookofthedead:map_scrap"},
                    {"cleansed_flesh", "nookofthedead:vandal_blood"}};
            for (String[] unlock : unlocks) {
                ServerPlayer finder = player(helper, GameType.SURVIVAL);
                try {
                    var recipe = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                            dev.nookofthedead.NookOfTheDead.id(unlock[0]));
                    check(helper, !finder.getRecipeBook().contains(recipe), unlock[0] + " known before");
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(unlock[1]));
                    finder.getInventory().add(new ItemStack(item));
                    finder.inventoryMenu.broadcastChanges();
                    check(helper, finder.getRecipeBook().contains(recipe), unlock[0] + " not in the book after " + unlock[1]);
                } finally {
                    leave(helper, finder);
                }
            }
            helper.succeed();
        });

        // the vial comes back a little emptier each time, sixteen times, by a click on the result and by a shift-click
        test("vandal_blood_crafts_sixteen_times", helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
            level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
            for (boolean shift : new boolean[] {false, true}) {
                ServerPlayer crafter = player(helper, GameType.SURVIVAL);
                try {
                    CraftingMenu menu = new CraftingMenu(1, crafter.getInventory(), ContainerLevelAccess.create(level, table));
                    menu.getSlot(1).set(new ItemStack(ZwItems.VANDAL_BLOOD.get()));
                    for (int s = 2; s <= 5; s++) {
                        menu.getSlot(s).set(new ItemStack(Items.ROTTEN_FLESH, 20));
                    }
                    if (shift) {
                        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, crafter);
                    } else {
                        for (int i = 1; i <= 16; i++) {
                            menu.clicked(0, 0, ContainerInput.PICKUP, crafter);
                            ItemStack vial = menu.getSlot(1).getItem();
                            check(helper, i == 16 ? vial.isEmpty() : vial.is(ZwItems.VANDAL_BLOOD.get()) && vial.getDamageValue() == i,
                                    "after " + i + " crafts the vial is " + vial + " worn " + vial.getDamageValue());
                        }
                    }
                    int made = menu.getCarried().is(ZwItems.CLEANSED_FLESH.get()) ? menu.getCarried().getCount() : 0;
                    for (ItemStack stack : crafter.getInventory()) {
                        if (stack.is(ZwItems.CLEANSED_FLESH.get())) {
                            made += stack.getCount();
                        }
                    }
                    check(helper, made == 64, (shift ? "shift-clicked" : "clicked") + ": " + made + " cleansed flesh");
                    check(helper, menu.getSlot(1).getItem().isEmpty(), "the vial spent after sixteen");
                    check(helper, menu.getSlot(2).getItem().getCount() == 4, "one rotten flesh from each slot a craft");
                    menu.setCarried(ItemStack.EMPTY);
                } finally {
                    leave(helper, crafter);
                }
            }
            helper.succeed();
        });
    }

    private PlayTests() {}
}
