package dev.nookofthedead.world;

import java.util.List;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The town's quarters (the user, 2026-10-13: "全部がランダムじゃなくて、地域ごとに生成される建物変えたりとかできる?"): the
 * town is cut into quarters a few hundred blocks across (each block of the grid goes with the nearest of a scatter of
 * quarter centres the seed fixes), and each quarter has its own mix: which buildings, how often a whole block is one big
 * building, how often a ring of little ones round a court, how many lots lie empty, how many streets are only alleys,
 * what the streets are paved with.
 * <ul>
 * <li>{@link #CENTRE}: the squares, the church, the big houses, stone townhouses; stone streets that go through; hardly
 * a lot empty.</li>
 * <li>{@link #RESIDENTIAL}: houses close together, the odd park and well; cobble and gravel streets, some only alleys.</li>
 * <li>{@link #OUTSKIRTS}: farmhouses, burnt-out homes, fields, the big park and graveyards; earth roads, many only
 * paths; much left empty.</li>
 * <li>{@link #OLD_TOWN}: packed as tight as it goes - every lot a house, the big ones, no parks, courts or gaps, lanes
 * more often than streets (the user, 2026-10-13: "馬鹿みたいに高密度な地域").</li>
 * <li>{@link #WASTE}: a few houses standing among rubble, dead fields and weeds (the user: "まばらに建物がある地域").</li>
 * </ul>
 */
public enum District {
    CENTRE(0.3, 0.3, 0.03, 0.0, 0.0, 0.3, 0.1,
            List.of(w("s2", 4), w("s7", 3), w("s3", 2), w("s5", 2), w("s12", 2), w("s17", 2), w("s18", 2), w("s19", 2), w("s22", 3), w("s8", 1),
                    w("s6", 1), w("s23", 2), w("s29", 2), new Weighted(Market.ID, 3)),
            List.of(w("m4", 4), w("m3", 3), w("m1", 2), w("m2", 2), w("m11", 1), w("m12", 1)),
            List.of(w("xs9", 3), w("xs10", 3), w("xs12", 3), w("xs11", 2), w("xs13", 3), w("xs16", 3), w("xs15", 2), w("xs2", 2), w("xs5", 2),
                    w("xs7", 2), w("xs1", 1), w("xs6", 1), w("xs14", 1)),
            List.of(w("s7", 3), w("s8", 1)),
            List.of(w("s9", 2), w("s10", 2), w("s13", 2), w("s11", 1), w("s25", 1)),
            List.of(w("m7", 2), w("m8", 1), w("m6", 1), w("m13", 2), w("m14", 2)),
            new Block[] {Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.ANDESITE,
                    Blocks.COBBLESTONE, Blocks.MOSSY_STONE_BRICKS}),
    RESIDENTIAL(0.12, 0.15, 0.04, 0.01, 0.0, 0.3, 0.25,
            List.of(w("s1", 3), w("s2", 3), w("s3", 3), w("s5", 3), w("s6", 2), w("s4", 1), w("s8", 1), w("s7", 1), w("s12", 1), w("s15", 1),
                    w("s17", 2), w("s18", 1), w("s22", 2), w("s23", 1), w("s29", 1), w("s28", 1), new Weighted(Market.ID, 1)),
            List.of(w("m1", 2), w("m2", 2), w("m5", 2), w("m3", 1), w("m9", 1), w("m11", 1)),
            List.of(w("xs9", 3), w("xs10", 3), w("xs11", 3), w("xs12", 2), w("xs15", 3), w("xs16", 2), w("xs13", 2), w("xs14", 2),
                    w("xs1", 2), w("xs4", 2), w("xs6", 2), w("xs7", 2), w("xs2", 1), w("xs3", 1), w("xs5", 1), w("xs21", 1)),
            List.of(w("s8", 2), w("s7", 1)),
            List.of(w("s9", 1), w("s10", 2), w("s11", 2), w("s13", 2), w("s25", 2)),
            List.of(w("m8", 2), w("m6", 1), w("m7", 1), w("m13", 1), w("m14", 1)),
            new Block[] {Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.COARSE_DIRT}),
    OUTSKIRTS(0.1, 0.05, 0.08, 0.12, 0.0, 0.25, 0.35,
            List.of(w("s4", 3), w("s6", 3), w("s1", 3), w("s15", 3), w("s14", 2), w("s8", 2), w("s5", 1), w("s12", 1), w("s24", 2), w("s28", 2), w("s30", 2)),
            List.of(w("m5", 3), w("m3", 2), w("m9", 2), w("m2", 1)),
            List.of(w("xs3", 3), w("xs4", 3), w("xs6", 2), w("xs8", 2), w("xs7", 1), w("xs1", 1), w("xs20", 2), w("xs21", 2)),
            List.of(w("s8", 1)),
            List.of(w("s9", 1), w("s10", 1), w("s11", 2), w("s13", 1)),
            List.of(w("m6", 2), w("m8", 1), w("m15", 2)),
            new Block[] {Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.PACKED_MUD}),
    OLD_TOWN(0.2, 0.0, 0.0, 0.0, 0.0, 0.5, 0.45,
            List.of(w("s2", 4), w("s3", 4), w("s5", 3), w("s4", 2), w("s12", 2), w("s18", 2), w("s19", 1), w("s22", 2), w("s1", 1),
                    w("s6", 1), w("s23", 1), w("s29", 1), new Weighted(Market.ID, 2)),
            List.of(w("m1", 3), w("m2", 3), w("m3", 1)),
            List.of(w("xs9", 4), w("xs10", 4), w("xs11", 4), w("xs12", 4), w("xs13", 4), w("xs14", 3), w("xs15", 4), w("xs16", 4),
                    w("xs6", 1), w("xs7", 1), w("xs2", 1), w("xs5", 1)),
            List.of(w("s7", 1)),
            List.of(w("s9", 2), w("s10", 1), w("s11", 2), w("s13", 2)),
            List.of(w("m7", 1), w("m8", 1), w("m6", 1), w("m14", 1)),
            new Block[] {Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.COBBLESTONE,
                    Blocks.ANDESITE}),
    WASTE(0.05, 0.05, 0.3, 0.2, 0.0, 0.45, 0.5,
            List.of(w("s1", 3), w("s4", 3), w("s6", 3), w("s12", 1), w("s8", 1), w("s24", 2), w("s28", 2), w("s30", 2)),
            List.of(w("m5", 3), w("m9", 2), w("m3", 1)),
            List.of(w("xs4", 3), w("xs3", 2), w("xs1", 2), w("xs6", 2), w("xs14", 3), w("xs18", 1), w("xs7", 1), w("xs5", 1), w("xs21", 2), w("xs20", 1)),
            List.of(w("s8", 1)),
            List.of(w("s11", 1), w("s10", 1)),
            List.of(w("m6", 1), w("m15", 1)),
            new Block[] {Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.DIRT_PATH, Blocks.ROOTED_DIRT}),
    /** The players' settlement ({@link Region#SETTLEMENT}): close built, every street through, stone paving, no gaps. */
    SETTLEMENT(0.3, 0.1, 0.02, 0.0, 0.0, 0.2, 0.0,
            List.of(w("s16", 3), w("s20", 3), w("s21", 3), w("s9", 3), w("s10", 3), w("s11", 3), w("s13", 2), w("s2", 2), w("s17", 1),
                    w("s22", 1), w("s3", 1), w("s5", 1), w("s23", 1), w("s28", 1)),
            List.of(w("m10", 4), w("m6", 3), w("m7", 1), w("m2", 1), w("m15", 2)),
            List.of(w("xs9", 2), w("xs10", 2), w("xs12", 2), w("xs2", 2), w("xs6", 2), w("xs16", 2), w("xs17", 2), w("xs18", 2), w("xs19", 2),
                    w("xs13", 1), w("xs1", 1), w("xs20", 2)),
            List.of(w("s7", 2), w("s8", 1)),
            List.of(w("s16", 1), w("s13", 1), w("s11", 1)),
            List.of(w("m10", 1), w("m6", 1), w("m8", 1)),
            new Block[] {Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICKS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS, Blocks.ANDESITE,
                    Blocks.CRACKED_STONE_BRICKS}),
    /**
     * The farmland ({@link Region#FARMLAND}): the blocks are fields, overgrown (Town's fields there), with a farmhouse or a
     * barn now and then; the lanes are earth.
     */
    FARM(0.15, 0.0, 0.02, 0.8, 0.05, 0.1, 1.0,
            List.of(w("s15", 5), w("s14", 5), w("s4", 3), w("s6", 2), w("s20", 1), w("s1", 1)),
            List.of(w("m9", 2), w("m2", 1), w("m5", 1)),
            List.of(w("xs8", 4), w("xs1", 3), w("xs17", 3), w("xs6", 2), w("xs4", 2), w("xs3", 1), w("xs20", 2)),
            List.of(w("s8", 1)),
            List.of(w("s11", 1)),
            List.of(w("m6", 1)),
            new Block[] {Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.GRAVEL, Blocks.DIRT_PATH, Blocks.COARSE_DIRT}),
    /**
     * The wilds ({@link Region#WILDS}): no streets but the odd trail, nothing built in most blocks ({@code yard} of them),
     * in the rest one small thing alone - a camp, a cabin, a grave.
     */
    WILD(0.0, 0.0, 0.0, 0.0, 0.7, 0.6, 1.0,
            List.of(w("s6", 3), w("s12", 2), w("s1", 2), w("s14", 1), w("s4", 1), w("s30", 2)),
            List.of(w("m9", 1)),
            List.of(w("xs4", 4), w("xs3", 2), w("xs7", 2), w("xs17", 1), w("xs18", 1), w("xs6", 1), w("xs1", 1)),
            List.of(w("s8", 1)),
            List.of(w("s11", 1)),
            List.of(w("m6", 1)),
            new Block[] {Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.DIRT_PATH, Blocks.COARSE_DIRT}),
    /**
     * The town the sand took ({@link Region#DESERT}, {@link Desert}): low, spread out, much of it gone to rubble and dunes;
     * its own buildings (a cistern house, a caravan stable, courtyard houses, a bazaar corner, a caravanserai, a dig camp)
     * and the town's houses worn to their footings, the desert village's among them; a pyramid now and then; the streets
     * mostly alleys under the sand.
     */
    DESERT(0.04, 0.06, 0.22, 0.08, 0.2, 0.55, 0.7,
            List.of(w("s27", 4), w("s26", 3), w("s1", 3), w("s4", 3), w("s6", 2), w("s14", 2), w("s15", 2), w("s12", 1), w("s5", 1),
                    w("s24", 1)),
            List.of(w("m5", 2), w("m9", 2), w("m3", 1)),
            join(v(Vanilla.DESERT_SMALL, 2), v(Vanilla.DESERT_MEDIUM, 1), List.of(w("xs24", 5), w("xs22", 4), w("xs23", 4), w("xs25", 4),
                    w("xs3", 2), w("xs4", 2), w("xs20", 1), w("xs21", 1), w("xs7", 1))),
            v(Vanilla.DESERT_CENTRES, 1),
            List.of(new Weighted(Desert.PYRAMID, 2), w("s11", 2), w("s10", 1)),
            List.of(w("m16", 1), w("m17", 1), w("m6", 1), w("m15", 1)),
            new Block[] {Blocks.SAND, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, Blocks.SAND, Blocks.CUT_SANDSTONE, Blocks.SAND});

    /** How likely a whole block is one big (M) building, or a ring of XS round a court. */
    public final double medium;
    public final double ring;
    /** Of a lot in a row or a ring: how likely rubble, a dead field; of a row's 32, how likely two XS rather than one S. */
    public final double rubble;
    public final double field;
    /** Of a lot, how likely left as it was, gone wild (the country's); of a wild block, how likely nothing built in it. */
    public final double yard;
    public final double small;
    /** How likely a street between two blocks here is only an alley (one going the whole way never is). */
    public final double alleys;
    // the lists: the mod's own, and any a data pack adds ({@link Buildings}, set anew on each reload)
    public volatile List<Weighted> sBuildings;
    public volatile List<Weighted> mBuildings;
    public volatile List<Weighted> xsBuildings;
    /** What stands in the court of a ring: S buildings that need no street. */
    public volatile List<Weighted> courts;
    /**
     * The places to find supplies in, now and then in place of an S building or an M one ({@link #RARE_S},
     * {@link #RARE_M}; the user, 2026-10-13: "道具屋、倉庫、避難所だったところ…それなりにレアにしたい"): the tool shops (S9,
     * S10), the warehouses (S11, M6), what were shelters (M7, M8) - the shops and the town hall in the centre, the
     * warehouses further out.
     */
    public volatile List<Weighted> rareS;
    public volatile List<Weighted> rareM;
    /** The mod's own lists, by kind, as built in. */
    private final java.util.Map<Buildings.Kind, List<Weighted>> own = new java.util.EnumMap<>(Buildings.Kind.class);
    /** How likely an S building is one of {@link #rareS}: one in thirty or so, about one in every six blocks. */
    public static final double RARE_S = 0.035;
    /** How likely a block's one M building is one of {@link #rareM}. */
    public static final double RARE_M = 0.25;
    /** The streets' top, from the noise's low end to its high end. */
    public final Block[] streets;

    District(double medium, double ring, double rubble, double field, double yard, double small, double alleys, List<Weighted> s, List<Weighted> m,
            List<Weighted> xs, List<Weighted> courts, List<Weighted> rareS, List<Weighted> rareM, Block[] streets) {
        this.medium = medium;
        this.ring = ring;
        this.rubble = rubble;
        this.field = field;
        this.yard = yard;
        this.small = small;
        this.alleys = alleys;
        this.sBuildings = s;
        this.mBuildings = m;
        this.xsBuildings = xs;
        this.courts = courts;
        this.rareS = rareS;
        this.rareM = rareM;
        this.streets = streets;
        this.own.put(Buildings.Kind.S, s);
        this.own.put(Buildings.Kind.M, m);
        this.own.put(Buildings.Kind.XS, xs);
        this.own.put(Buildings.Kind.COURT, courts);
        this.own.put(Buildings.Kind.RARE_S, rareS);
        this.own.put(Buildings.Kind.RARE_M, rareM);
    }

    /** Each district's lists: its own, and after them those {@code added} by data packs (all of them anew). */
    /** How many times the lists have been set ({@link #extend}): what was laid out from older ones is not used again. */
    public static int generation() {
        return generation;
    }

    private static volatile int generation;

    public static void extend(java.util.Map<District, java.util.Map<Buildings.Kind, List<Weighted>>> added) {
        for (District d : values()) {
            java.util.Map<Buildings.Kind, List<Weighted>> more = added.getOrDefault(d, java.util.Map.of());
            java.util.function.Function<Buildings.Kind, List<Weighted>> list = kind -> {
                List<Weighted> extra = more.getOrDefault(kind, List.of());
                if (extra.isEmpty()) {
                    return d.own.get(kind);
                }
                List<Weighted> all = new java.util.ArrayList<>(d.own.get(kind));
                all.addAll(extra);
                return List.copyOf(all);
            };
            d.sBuildings = list.apply(Buildings.Kind.S);
            d.mBuildings = list.apply(Buildings.Kind.M);
            d.xsBuildings = list.apply(Buildings.Kind.XS);
            d.courts = list.apply(Buildings.Kind.COURT);
            d.rareS = list.apply(Buildings.Kind.RARE_S);
            d.rareM = list.apply(Buildings.Kind.RARE_M);
        }
        generation++;
    }

    /** A building and how often it comes. */
    public record Weighted(Identifier id, int weight) {}

    private static Weighted w(String name, int weight) {
        return new Weighted(NookOfTheDead.id("town/" + name), weight);
    }

    /** Each of {@code ids} (the game's own templates) at {@code weight}. */
    private static List<Weighted> v(List<Identifier> ids, int weight) {
        return ids.stream().map(id -> new Weighted(id, weight)).toList();
    }

    @SafeVarargs
    private static List<Weighted> join(List<Weighted>... lists) {
        return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
    }

    /** One of {@code list} by its weights, from {@code roll} (any long; its value spread over them). */
    public static Identifier pick(List<Weighted> list, long roll) {
        long total = list.stream().mapToLong(Weighted::weight).sum();
        long r = Math.floorMod(roll, total);
        for (Weighted w : list) {
            r -= w.weight();
            if (r < 0) {
                return w.id();
            }
        }
        return list.getLast().id();
    }

    /** Quarter centres sit one to a square of this many grid blocks, somewhere in it. */
    public static final int QUARTER = 5;

    /**
     * The quarter that block ({@code bx}, {@code bz}) of the grid is in, for a world of {@code seed}: away from the town
     * its region's own ({@link #SETTLEMENT}, {@link #FARM}, {@link #WILD}); in the town, its quarter.
     */
    public static District of(long seed, int bx, int bz) {
        switch (Region.of(seed, bx, bz)) {
            case SETTLEMENT -> {
                return SETTLEMENT;
            }
            case FARMLAND -> {
                return FARM;
            }
            case WILDS -> {
                return WILD;
            }
            case DESERT -> {
                return DESERT;
            }
            case CITY -> {
            }
        }
        int qx = Math.floorDiv(bx, QUARTER);
        int qz = Math.floorDiv(bz, QUARTER);
        double best = Double.MAX_VALUE;
        District found = RESIDENTIAL;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long h = Town.hash(seed, qx + dx, qz + dz, 20);
                double cx = (qx + dx) * QUARTER + Math.floorMod(h, (long) QUARTER) + 0.5;
                double cz = (qz + dz) * QUARTER + Math.floorMod(h >> 12, (long) QUARTER) + 0.5;
                double d = (bx + 0.5 - cx) * (bx + 0.5 - cx) + (bz + 0.5 - cz) * (bz + 0.5 - cz);
                if (d < best) {
                    best = d;
                    int kind = (int) Math.floorMod(h >> 24, 10L);
                    found = kind < 1 ? CENTRE : kind < 3 ? OLD_TOWN : kind < 6 ? RESIDENTIAL : kind < 8 ? OUTSKIRTS : WASTE;
                }
            }
        }
        return found;
    }
}
