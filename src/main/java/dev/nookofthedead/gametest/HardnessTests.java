package dev.nookofthedead.gametest;

import static dev.nookofthedead.gametest.ZwGameTests.check;
import static dev.nookofthedead.gametest.ZwGameTests.test;

import dev.nookofthedead.entity.Hardness;
import dev.nookofthedead.entity.Traits;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;

/** The fallen world by the game's difficulty (the user, 2026-10-23: "ハードは本当に難しくさせたらいいんだ"). */
final class HardnessTests {
    static void register() {
        // the quick, the keen, the strong and the screamers: fewer on Easy, more on Hard
        test("the_dead_are_worse_on_hard", helper -> {
            int easy = dangerous(Hardness.EASY);
            int normal = dangerous(Hardness.NORMAL);
            int hard = dangerous(Hardness.HARD);
            check(helper, easy < normal && normal < hard, "easy " + easy + ", normal " + normal + ", hard " + hard);
            check(helper, Hardness.EASY.crowd() < 1 && Hardness.HARD.crowd() > 1, "the crowds");
            check(helper, Hardness.EASY.bite() < Hardness.NORMAL.bite() && Hardness.NORMAL.bite() < Hardness.HARD.bite(), "the bites");
            helper.succeed();
        });

        // a boss's health goes with the difficulty, as hurt as it was: from Normal to Hard and on down to Easy
        test("a_boss_is_as_strong_as_the_game_is_hard", helper -> {
            var vandal = dev.nookofthedead.registry.ZwEntities.VANDAL.get().create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
            vandal.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
            float base = vandal.getMaxHealth();
            Hardness.fit(vandal, Hardness.HARD);
            check(helper, Math.abs(vandal.getMaxHealth() - base * 1.5F) < 0.01F && vandal.getHealth() == vandal.getMaxHealth(),
                    "hard: " + vandal.getHealth() + "/" + vandal.getMaxHealth());
            vandal.setHealth(vandal.getMaxHealth() / 2);
            Hardness.fit(vandal, Hardness.EASY);
            check(helper, Math.abs(vandal.getMaxHealth() - base * 0.75F) < 0.01F && Math.abs(vandal.getHealth() - base * 0.375F) < 0.01F,
                    "easy, half hurt: " + vandal.getHealth() + "/" + vandal.getMaxHealth());
            Hardness.fit(vandal, Hardness.NORMAL);
            check(helper, Math.abs(vandal.getMaxHealth() - base) < 0.01F, "normal again: " + vandal.getMaxHealth());
            helper.succeed();
        });
    }

    /** The Vandal kept to one, and kept (VandalKeeper, the user, 2026-10-23). */
    static void registerVandal() {
        // a Vandal behind its institute's count - one put back after it went - is the old one, to be taken away
        test("an_old_vandal_gives_way", helper -> {
            var level = helper.getLevel();
            var vandal = dev.nookofthedead.registry.ZwEntities.VANDAL.get().create(level, EntitySpawnReason.TRIGGERED);
            String key = "test:" + helper.absolutePos(BlockPos.ZERO).asLong();
            vandal.home(key, dev.nookofthedead.world.VandalKeeper.count(level, key));
            check(helper, !dev.nookofthedead.world.VandalKeeper.stale(level, vandal), "the current one stays");
            vandal.home(key, dev.nookofthedead.world.VandalKeeper.count(level, key) - 1);
            check(helper, dev.nookofthedead.world.VandalKeeper.stale(level, vandal), "one behind the count gives way");
            helper.succeed();
        });
    }

    private static int dangerous(Hardness hardness) {
        RandomSource random = RandomSource.create(43L);
        int n = 0;
        for (int i = 0; i < 4000; i++) {
            Traits t = Traits.roll(random, hardness);
            n += (t.pace() == Traits.Pace.SPRINT ? 1 : 0) + (t.sense() == Traits.Sense.KEEN ? 1 : 0)
                    + (t.breaker() == Traits.Breaker.STRONG ? 1 : 0) + (t.voice() == Traits.Voice.SCREAM ? 1 : 0);
        }
        return n;
    }
}
