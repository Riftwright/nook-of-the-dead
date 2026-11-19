package dev.nookofthedead;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * The add-on's settings ({@code nookofthedead-server.toml}, synced to its players as Nookworks' and Nook Technology's server
 * settings are; in a world, Esc > Mods > Nook of the Dead > Config - NeoForge greys synced settings out on the title
 * screen), read as they are now, so they may be changed while a world is open.
 * Each one's default is the game as it was before it was a setting.
 */
public final class ZwConfig {
    public static final ModConfigSpec SPEC;
    /**
     * Whether an elytra - or anything else that glides - opens in the If (the user, 2026-11-10: 「設定から、ゾンビの世界でエリトラの
     * 使用を許可するかしないかを、選択できるといいかな」; on by default, as it always was). Off, a glide does not start there
     * and one under way stops; everywhere else it is as it was ({@code dev.nookofthedead.mixin.GlideMixin}).
     */
    public static final ModConfigSpec.BooleanValue ELYTRA_IN_THE_IF;
    /**
     * How many of the dead come round a player ({@code dev.nookofthedead.entity.Horde}; the user, 2026-11-10, of the
     * settings offered: 「1－3は全部入れていいよ」): the swarm the horde keeps up about each player, and the most there are
     * in the If at once, both times {@link Crowd#times}.
     */
    public static final ModConfigSpec.EnumValue<Crowd> CROWD;
    /** How long a suppressant holds the infection back, in minutes (a long one twice as long): fifteen, as it was. */
    public static final ModConfigSpec.IntValue SUPPRESSANT_MINUTES;
    /** The fewest and the most minutes {@link #SUPPRESSANT_MINUTES} takes. */
    public static final int SUPPRESSANT_FEWEST = 5, SUPPRESSANT_MOST = 60;
    /**
     * How often a chest holds a suppressant (the user, 2026-11-19: 「抑制剤の出現頻度を、設定で変更できないかな。人によっては渋すぎて
     * ゲームにならないかも」): every chest table's chance of one, times {@link Finds#times} ({@code ZwLoot.SuppressantChance}).
     */
    public static final ModConfigSpec.EnumValue<Finds> SUPPRESSANT_FINDS;
    /**
     * Whether the dead break the If's mirrors - the way home ({@code dev.nookofthedead.entity.SmashMirrorGoal}; the user,
     * 2026-10-13: 「鏡も壊す仕様にして。ユーザーに絶望を！」, so on by default). Off, they neither go for one nor break
     * through one in their way; wooden walls and the rest they break as before.
     */
    public static final ModConfigSpec.BooleanValue MIRRORS_SMASHED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ELYTRA_IN_THE_IF = builder.comment("Whether an elytra (or anything else that glides) opens in the If. Off, no one glides there;",
                        "the usual world, the Nether and the End are as they were either way.")
                .translation(key("elytraInTheIf")).define("elytraInTheIf", true);
        CROWD = builder.comment("How many of the dead come round each player in the If: FEWER (about six in ten), NORMAL, or MORE",
                        "(about fourteen in ten; heavier for the computer). The most in the If at once goes with it.")
                .translation(key("crowd")).defineEnum("crowd", Crowd.NORMAL);
        SUPPRESSANT_MINUTES = builder.comment("How many minutes a suppressant holds the infection back (a long one, twice as many).",
                        "The head starts to swim three minutes before the end, however long it is.")
                .translation(key("suppressantMinutes")).defineInRange("suppressantMinutes", 15, SUPPRESSANT_FEWEST, SUPPRESSANT_MOST);
        SUPPRESSANT_FINDS = builder.comment("How often a chest holds a suppressant: FEWER (half as often), NORMAL, MORE (twice as often)",
                        "or PLENTY (four times). A chest's contents are settled when it is first opened, so it counts in every",
                        "chest not opened yet.")
                .translation(key("suppressantFinds")).defineEnum("suppressantFinds", Finds.NORMAL);
        MIRRORS_SMASHED = builder.comment("Whether the dead break the If's mirrors, the way home. Off, they leave every mirror be",
                        "(wooden doors and walls they still break, as the mobGriefing game rule says).")
                .translation(key("mirrorsSmashed")).define("mirrorsSmashed", true);
        SPEC = builder.build();
    }

    /** How many of the dead come: the swarm about each player times {@link #times}. */
    public enum Crowd implements TranslatableEnum {
        FEWER(0.6), NORMAL(1.0), MORE(1.4);

        public final double times;

        Crowd(double times) {
            this.times = times;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key("crowd." + this.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** How often a suppressant is found: every chest's chance of one times {@link #times}. */
    public enum Finds implements TranslatableEnum {
        FEWER(0.5), NORMAL(1.0), MORE(2.0), PLENTY(4.0);

        public final double times;

        Finds(double times) {
            this.times = times;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key("suppressantFinds." + this.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    /** The setting's word in the lang files ({@code nookofthedead.configuration.*}), which NeoForge's screen shows. */
    private static String key(String name) {
        return NookOfTheDead.MODID + ".configuration." + name;
    }

    /** Whether {@link #ELYTRA_IN_THE_IF} lets one glide now: so, too, before the settings are read. */
    public static boolean glidingInTheIf() {
        return !SPEC.isLoaded() || ELYTRA_IN_THE_IF.getAsBoolean();
    }

    /** How many times the usual swarm {@link #CROWD} asks for now (the usual, before the settings are read). */
    public static double crowd() {
        return SPEC.isLoaded() ? CROWD.get().times : 1.0;
    }

    /** How many times the usual chance of a suppressant in a chest {@link #SUPPRESSANT_FINDS} asks for (1 before it is read). */
    public static double suppressantFinds() {
        return SPEC.isLoaded() ? SUPPRESSANT_FINDS.get().times : 1.0;
    }

    /** A suppressant's hold, in ticks ({@link #SUPPRESSANT_MINUTES}; fifteen minutes before the settings are read). */
    public static int suppressantTicks() {
        return 20 * 60 * (SPEC.isLoaded() ? SUPPRESSANT_MINUTES.getAsInt() : 15);
    }

    /** Whether the dead break the If's mirrors now ({@link #MIRRORS_SMASHED}): so, too, before the settings are read. */
    public static boolean mirrorsSmashed() {
        return !SPEC.isLoaded() || MIRRORS_SMASHED.getAsBoolean();
    }

    private ZwConfig() {}
}
