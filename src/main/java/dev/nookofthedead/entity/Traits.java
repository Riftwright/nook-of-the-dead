package dev.nookofthedead.entity;

import java.util.List;

import dev.nookofthedead.registry.ZwTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What one mutated zombie is like inside (the user, 2026-10-15: "見た目や表示される名前は同じだが、内部の挙動が違うゾンビを
 * 数種類"): how fast it runs, how it closes in, how far and which way it sees and hears, what it can break. Its health is
 * the same as every other's. Rolled once as it first comes into the world and kept with it; nothing outside shows it, so a
 * player learns one by how it behaves.
 */
public record Traits(Pace pace, Chase chase, Sense sense, Breaker breaker, Voice voice) {
    /** Traits with the common voice. */
    public Traits(Pace pace, Chase chase, Sense sense, Breaker breaker) {
        this(pace, chase, sense, breaker, Voice.GROAN);
    }

    /** The traits a zombie that had none comes back with (one from before there were any). */
    public static final Traits PLAIN = new Traits(Pace.RUN, Chase.DIRECT, Sense.PLAIN, Breaker.SOFT);

    /**
     * Its pace, as the movement speed attribute (a mob's ground pace goes with the square of it: 0.36 runs as a sprinting
     * player). The quick ones are few: they outrun a sprinting player, and a chase that cannot be won is no fun.
     */
    public enum Pace {
        SHAMBLE(0.31, 30), RUN(0.36, 55), SPRINT(0.40, 15);

        public final double speed;
        final int weight;

        Pace(double speed, int weight) {
            this.speed = speed;
            this.weight = weight;
        }
    }

    /** How it closes in: straight at its prey, or round to one side first and in from there ({@link FlankGoal}). */
    public enum Chase {
        DIRECT(65), FLANK(35);

        final int weight;

        Chase(int weight) {
            this.weight = weight;
        }
    }

    /**
     * Its senses: how far it sees along the ground ({@code sight}, its follow range), how far above it it still notices
     * someone from anywhere ({@code looksUp}) and from how near below otherwise ({@code under}), and how far it hears, as a
     * share of what a sound carries ({@code hearing}). The dull ones see little but are still roused by the others round
     * them, as zombified piglins are ({@link Mind#rouse}).
     */
    public enum Sense {
        /** The common one: far along the street, little up. */
        PLAIN(40, 4, 8, 1.0, 40),
        /** Looks up: a roof is no hiding place from it. */
        UPWARD(32, 12, 20, 1.0, 15),
        /** Sees a long way down the street, hardly up at all. */
        LEVEL(56, 3, 5, 1.0, 20),
        /** Sees and hears far all round: the one that finds you. */
        KEEN(64, 6, 12, 1.5, 7),
        /** Sees and hears little: walked past, it goes on standing there. */
        DULL(16, 3, 6, 0.6, 18);

        public final double sight;
        public final double looksUp;
        public final double under;
        public final double hearing;
        final int weight;

        Sense(double sight, double looksUp, double under, double hearing, int weight) {
            this.sight = sight;
            this.looksUp = looksUp;
            this.under = under;
            this.hearing = hearing;
            this.weight = weight;
        }
    }

    /**
     * What it breaks to get at someone: nothing; only what shatters or tears (glass, leaves, wool); every soft block (wood,
     * glass, earth - {@code #nookofthedead:soft}); or every soft block in half the time. Stone stops them all.
     */
    public enum Breaker {
        NONE(null, 1.0, 20), FRAGILE(ZwTags.FRAGILE, 1.0, 25), SOFT(ZwTags.SOFT, 1.0, 40), STRONG(ZwTags.SOFT, 0.5, 15);

        public final TagKey<Block> breaks;
        /** Of the time a block takes to break. */
        public final double time;
        final int weight;

        Breaker(TagKey<Block> breaks, double time, int weight) {
            this.breaks = breaks;
            this.time = time;
            this.weight = weight;
        }
    }

    /**
     * What it does when it sees someone: groans, as they all do - or, now and then, screams, and every one of them in
     * {@link Mind#SCREAM_REACH} comes (the user, 2026-10-17: "叫ぶ個体"). It looks like the rest: the scream is the tell.
     */
    public enum Voice {
        GROAN(96), SCREAM(4);

        final int weight;

        Voice(int weight) {
            this.weight = weight;
        }
    }

    public static Traits roll(RandomSource random) {
        return roll(random, Hardness.NORMAL);
    }

    /**
     * Rolled as the game is hard: the dangerous kinds - the quick, the keen, the strong, the screamers - {@code rare} times
     * as likely as on Normal ({@link Hardness#rare}).
     */
    public static Traits roll(RandomSource random, Hardness hardness) {
        double rare = hardness.rare();
        return new Traits(pick(Pace.values(), random, rare), pick(Chase.values(), random, rare), pick(Sense.values(), random, rare),
                pick(Breaker.values(), random, rare), pick(Voice.values(), random, rare));
    }

    /** The kinds that make it harder: their weights go with the difficulty. */
    static boolean dangerous(Enum<?> value) {
        return value == Pace.SPRINT || value == Sense.KEEN || value == Breaker.STRONG || value == Voice.SCREAM;
    }

    private static <T extends Enum<T>> T pick(T[] values, RandomSource random, double rare) {
        int total = 0;
        for (T value : values) {
            total += weight(value, rare);
        }
        int r = random.nextInt(total);
        for (T value : values) {
            r -= weight(value, rare);
            if (r < 0) {
                return value;
            }
        }
        return values[values.length - 1];
    }

    private static int weight(Enum<?> value, double rare) {
        return dangerous(value) ? Math.max(1, (int) Math.round(weight(value) * rare)) : weight(value);
    }

    private static int weight(Enum<?> value) {
        return switch (value) {
            case Pace p -> p.weight;
            case Chase c -> c.weight;
            case Sense s -> s.weight;
            case Breaker b -> b.weight;
            case Voice v -> v.weight;
            default -> 1;
        };
    }

    public void save(ValueOutput output) {
        output.putString("Pace", this.pace.name());
        output.putString("Chase", this.chase.name());
        output.putString("Sense", this.sense.name());
        output.putString("Breaker", this.breaker.name());
        output.putString("Voice", this.voice.name());
    }

    public static Traits load(ValueInput input) {
        return new Traits(read(input, "Pace", Pace.class, PLAIN.pace), read(input, "Chase", Chase.class, PLAIN.chase),
                read(input, "Sense", Sense.class, PLAIN.sense), read(input, "Breaker", Breaker.class, PLAIN.breaker),
                read(input, "Voice", Voice.class, PLAIN.voice));
    }

    private static <T extends Enum<T>> T read(ValueInput input, String key, Class<T> type, T otherwise) {
        String name = input.getStringOr(key, "");
        for (T value : type.getEnumConstants()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return otherwise;
    }

    /** Every kind there is of each, for tests. */
    public static List<Enum<?>[]> all() {
        return List.of(Pace.values(), Chase.values(), Sense.values(), Breaker.values(), Voice.values());
    }
}
