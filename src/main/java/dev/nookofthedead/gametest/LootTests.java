package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Markers;
import dev.nookofthedead.world.Stashes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The ruins' chest loot (1.2): every table there is loads and gives something, and every building of the town has a stash
 * kind or is one of the few with none (a park, a square: no house's larder there).
 */
final class LootTests {
    /** The buildings with no stash kind of their own: their empty containers keep the plain stash. */
    static final Set<String> PLAIN = Set.of("xs1", "xs5", "s7", "s8", "m4", "m5", "institute_deep");
    static final List<String> MARKED = List.of("food", "general", "medical", "potion", "supplies", "tools", "weapon", "stash");

    static void register() {
        test("loot_tables_load_and_give", helper -> {
            ServerLevel level = helper.getLevel();
            List<String> tables = new ArrayList<>(MARKED);
            Stashes.KINDS.forEach(k -> tables.add("stash_" + k));
            for (String name : tables) {
                var key = Markers.table(name);
                // the name itself, never another table it fell back to (stash_* once all fell back to general, unseen)
                check(helper, key.identifier().getPath().equals("chests/" + name), "chests/" + name + " is taken for " + key.identifier());
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                check(helper, table != LootTable.EMPTY, "no loot table chests/" + name);
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,
                        Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO))).create(LootContextParamSets.CHEST);
                int given = 0;
                for (int roll = 0; roll < 20; roll++) {
                    for (ItemStack stack : table.getRandomItems(params, RandomSource.create(roll))) {
                        given += stack.isEmpty() ? 0 : 1;
                    }
                }
                check(helper, given > 0, "chests/" + name + " gave nothing in twenty rolls");
            }
            helper.succeed();
        });
        // the setting for how often a suppressant is found (the user, 2026-11-19): a house's stash, 3 in 100 as it was;
        // four times as often with PLENTY, half with FEWER - set back as it was after
        test("loot_suppressant_finds_follow_the_setting", helper -> {
            ServerLevel level = helper.getLevel();
            LootTable table = level.getServer().reloadableRegistries().getLootTable(Markers.table("stash_home"));
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,
                    Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO))).create(LootContextParamSets.CHEST);
            var setting = dev.nookofthedead.ZwConfig.SUPPRESSANT_FINDS;
            var was = setting.get();
            int[] found = new int[3];
            var finds = List.of(dev.nookofthedead.ZwConfig.Finds.FEWER, dev.nookofthedead.ZwConfig.Finds.NORMAL, dev.nookofthedead.ZwConfig.Finds.PLENTY);
            try {
                for (int f = 0; f < 3; f++) {
                    setting.set(finds.get(f));
                    for (int roll = 0; roll < 4000; roll++) {
                        for (ItemStack stack : table.getRandomItems(params, RandomSource.create(roll * 31L + 7))) {
                            var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                            if (contents != null && contents.potion().map(p -> p.is(dev.nookofthedead.NookOfTheDead.id("suppressant"))
                                    || p.is(dev.nookofthedead.NookOfTheDead.id("long_suppressant"))).orElse(false)) {
                                found[f]++;
                            }
                        }
                    }
                }
            } finally {
                setting.set(was);
            }
            // 4000 rolls: about 60 (1.5 in 100), 120 (3 in 100) and 480 (12 in 100)
            check(helper, found[0] > 25 && found[0] < 100, "FEWER found " + found[0] + " in 4000");
            check(helper, found[1] > 70 && found[1] < 180, "NORMAL found " + found[1] + " in 4000");
            check(helper, found[2] > 380 && found[2] < 600, "PLENTY found " + found[2] + " in 4000");
            helper.succeed();
        });
        test("loot_every_building_has_a_stash_kind", helper -> {
            var found = helper.getLevel().getServer().getResourceManager().listResources("structure/town",
                    id -> id.getNamespace().equals(NookOfTheDead.MODID) && id.getPath().endsWith(".nbt"));
            List<String> none = new ArrayList<>();
            for (Identifier file : found.keySet()) {
                String name = file.getPath().substring("structure/town/".length(), file.getPath().length() - 4);
                if (name.contains("/")) {
                    continue;                       // a variant's file, under its own folder
                }
                if (Stashes.kind(Optional.of(NookOfTheDead.id("town/" + name))).isEmpty() && !PLAIN.contains(name)) {
                    none.add(name);
                }
            }
            check(helper, none.isEmpty(), "buildings with no stash kind (world/Stashes.KIND): " + none);
            helper.succeed();
        });
    }

    private LootTests() {}
}
