package dev.nookofthedead.entity;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * How hard the fallen world is, by the game's own difficulty (the user, 2026-10-23: "ゲーム自体のハード、ノーマル、イージーの
 * 変更に応じて、ゾンビなどの挙動を調整してもいいかも。ハードは本当に難しくさせたらいいんだ"). Normal is the world as it was;
 * Easy lighter all round; Hard - and Hardcore, whose difficulty is Hard - heavier all round, and a room is no shelter
 * ({@link Horde#sheltered}). Peaceful has none of the dead about, and counts as Easy for what is left.
 *
 * @param crowd       of the dead that come round a player ({@link Horde#gather})
 * @param perWave     at most this many come out at a time
 * @param bite        how much sooner a bite that drew blood brings the end (ticks; {@link dev.nookofthedead.infection.Infection#bitten})
 * @param calmFloor   what the dead a player put down take off the crowd, down to this share of it at most
 * @param behind      how far behind someone getting away a runner has lost them ({@link Horde#BEHIND})
 * @param rare        how much likelier the dangerous kinds are (the quick, the keen, the strong, the screamers: {@link Traits#roll})
 * @param breakTime   of the time the ones that break things take to
 * @param hearing     of how far every one of them hears
 * @param boss        of the health of the Vandal, the thing in the sewers and the Sentry Golems
 * @param roar        of the time between the Vandal's roars
 * @param marked      how many more come out of each place the dead were left standing in a building (never fewer than one)
 * @param smellsBlood whether, a player wounded to half, the idle ones round them come for them
 */
public record Hardness(double crowd, int perWave, int bite, double calmFloor, double behind, double rare, double breakTime,
        double hearing, double boss, double roar, int marked, boolean smellsBlood) {
    public static final Hardness EASY = new Hardness(0.6, 4, 2 * 20, 1.0 / 3, 16.0, 0.5, 1.25, 0.8, 0.75, 1.25, -1, false);
    public static final Hardness NORMAL = new Hardness(1.0, 6, 5 * 20, 0.5, 20.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0, false);
    public static final Hardness HARD = new Hardness(1.5, 9, 12 * 20, 2.0 / 3, 32.0, 2.0, 0.75, 1.25, 1.5, 0.6, 1, true);

    /** How hard {@code level} is now. */
    public static Hardness of(Level level) {
        if (level.getLevelData().isHardcore()) {
            return HARD;
        }
        Difficulty difficulty = level.getDifficulty();
        return difficulty == Difficulty.HARD ? HARD : difficulty == Difficulty.NORMAL ? NORMAL : EASY;
    }

    /** The modifier a boss's health is kept at the difficulty with. */
    public static final Identifier BOSS = NookOfTheDead.id("difficulty");

    /**
     * {@code mob}'s health kept to the difficulty ({@link #boss}), now and whenever the difficulty is changed: as hurt as
     * it was, as a share of all it has. Called from its own server step.
     */
    public static void fit(Mob mob) {
        if (mob.tickCount % 20 == 0) {
            fit(mob, of(mob.level()));
        }
    }

    /** {@code mob}'s health kept to {@code hardness}. */
    public static void fit(Mob mob, Hardness hardness) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        double want = hardness.boss - 1.0;
        AttributeModifier now = health == null ? null : health.getModifier(BOSS);
        if (health == null || (now == null ? want == 0 : now.amount() == want)) {
            return;
        }
        float share = mob.getHealth() / mob.getMaxHealth();
        health.removeModifier(BOSS);
        if (want != 0) {
            health.addPermanentModifier(new AttributeModifier(BOSS, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        mob.setHealth(Math.max(1.0F, share * mob.getMaxHealth()));
    }
}
