package dev.nookofthedead.world;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A small, fixed-size memory of one number per block of the town's grid and world seed, for the questions world
 * generation asks over and over of the same few blocks (a chunk's every column asks its block's region, and its
 * neighbours'). Direct-mapped: a newer block takes an older one's slot, so it never grows; no locks and no boxing, safe
 * from any thread (each slot holds an unchanging entry, and a lost write only means working it out again).
 */
final class BlockCache {
    private record Entry(long seed, int bx, int bz, int value) {}

    private final Entry[] slots;
    private final int mask;

    /** A cache of {@code 2^bits} slots. */
    BlockCache(int bits) {
        this.slots = new Entry[1 << bits];
        this.mask = (1 << bits) - 1;
    }

    /** What is kept for block ({@code bx}, {@code bz}) of {@code seed}'s world, worked out by {@code work} if not. */
    int get(long seed, int bx, int bz, IntSupplier work) {
        int slot = (int) (Town.hash(seed, bx, bz, 7) & this.mask);
        Entry e = this.slots[slot];
        if (e != null && e.bx == bx && e.bz == bz && e.seed == seed) {
            return e.value;
        }
        int value = work.getAsInt();
        this.slots[slot] = new Entry(seed, bx, bz, value);
        return value;
    }

    /** The same for a thing worked out per block (it must not change once made: every caller gets the one kept). */
    static final class Of<T> {
        private record Entry<T>(long seed, int bx, int bz, T value) {}

        private final Entry<?>[] slots;
        private final int mask;

        Of(int bits) {
            this.slots = new Entry<?>[1 << bits];
            this.mask = (1 << bits) - 1;
        }

        @SuppressWarnings("unchecked")
        T get(long seed, int bx, int bz, Supplier<T> work) {
            int slot = (int) (Town.hash(seed, bx, bz, 7) & this.mask);
            Entry<T> e = (Entry<T>) this.slots[slot];
            if (e != null && e.bx == bx && e.bz == bz && e.seed == seed) {
                return e.value;
            }
            T value = work.get();
            this.slots[slot] = new Entry<>(seed, bx, bz, value);
            return value;
        }

        /** Forgets all that is kept (what it was worked out from has changed). */
        void clear() {
            java.util.Arrays.fill(this.slots, null);
        }
    }
}
