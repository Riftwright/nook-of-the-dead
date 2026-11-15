package dev.nookofthedead.world;

import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * What a building's unmarked chests, barrels and crates hold, by what the building was (1.2, the user, 2026-11-15:
 * 「チェストの中身などにこだわったり」): a house's larder and linen, a forge's iron and coal, a clinic's bottles and
 * bandages, a jail's guard's kit - {@code loot_table/chests/stash_<kind>}, written by {@code tools/gen_loot.py}. A building
 * not named here keeps the plain stash, as every building had before.
 */
public final class Stashes {
    /** The kinds, each a table {@code stash_<kind>}. */
    public static final java.util.List<String> KINDS = java.util.List.of("home", "inn", "shop", "workshop", "farm", "medical",
            "church", "office", "guard", "store", "camp");

    /** Each building (its template's name under {@code town/}) and its kind. */
    static final Map<String, String> KIND = Map.ofEntries(
            // houses
            Map.entry("s1", "home"), Map.entry("s2", "home"), Map.entry("s2_bare", "home"), Map.entry("s3", "home"), Map.entry("s5", "home"),
            Map.entry("s6", "home"), Map.entry("m1", "home"), Map.entry("m2", "home"), Map.entry("xs9", "home"), Map.entry("xs10", "home"),
            Map.entry("xs11", "home"), Map.entry("xs12", "home"), Map.entry("xs14", "home"), Map.entry("xs15", "home"), Map.entry("xs24", "home"),
            // taverns and inns
            Map.entry("xs13", "inn"), Map.entry("s22", "inn"), Map.entry("s27", "inn"), Map.entry("m17", "inn"),
            // shops and markets
            Map.entry("xs2", "shop"), Map.entry("xs16", "shop"), Map.entry("xs25", "shop"), Map.entry("s18", "shop"), Map.entry("m14", "shop"),
            // trades and workshops
            Map.entry("s9", "workshop"), Map.entry("s10", "workshop"), Map.entry("s21", "workshop"), Map.entry("s28", "workshop"),
            Map.entry("m10", "workshop"), Map.entry("m16", "workshop"),
            // farms
            Map.entry("s4", "farm"), Map.entry("s14", "farm"), Map.entry("s15", "farm"), Map.entry("s20", "farm"), Map.entry("xs8", "farm"),
            Map.entry("xs17", "farm"), Map.entry("xs23", "farm"),
            // medicine
            Map.entry("s13", "medical"), Map.entry("s17", "medical"), Map.entry("s24", "medical"), Map.entry("m11", "medical"),
            Map.entry("m13", "medical"), Map.entry("xs19", "medical"), Map.entry("institute", "medical"),
            // churches
            Map.entry("xs3", "church"), Map.entry("xs7", "church"), Map.entry("s23", "church"), Map.entry("m3", "church"),
            // schools, libraries, offices
            Map.entry("s25", "office"), Map.entry("s29", "office"), Map.entry("m12", "office"),
            // guards
            Map.entry("xs18", "guard"), Map.entry("xs21", "guard"), Map.entry("s12", "guard"), Map.entry("s19", "guard"), Map.entry("m15", "guard"),
            // stores
            Map.entry("xs6", "store"), Map.entry("xs20", "store"), Map.entry("xs22", "store"), Map.entry("s11", "store"), Map.entry("s16", "store"),
            Map.entry("m6", "store"),
            // camps and those who held out
            Map.entry("xs4", "camp"), Map.entry("s26", "camp"), Map.entry("s30", "camp"), Map.entry("m7", "camp"), Map.entry("m8", "camp"),
            Map.entry("m9", "camp"),
            // 1.2's buildings
            Map.entry("xs26", "inn"), Map.entry("xs27", "office"), Map.entry("xs28", "home"), Map.entry("xs29", "medical"),
            Map.entry("xs30", "guard"), Map.entry("s31", "farm"), Map.entry("s32", "farm"), Map.entry("s33", "camp"), Map.entry("s34", "workshop"),
            Map.entry("s35", "store"), Map.entry("m18", "farm"), Map.entry("m19", "workshop"), Map.entry("m20", "workshop"));

    /** The kind of {@code building} ({@code town/<name>}), if it has one. */
    public static Optional<String> kind(Optional<Identifier> building) {
        return building.filter(id -> id.getNamespace().equals(dev.nookofthedead.NookOfTheDead.MODID) && id.getPath().startsWith("town/"))
                .map(id -> KIND.get(id.getPath().substring(5)));
    }

    /** The table {@code building}'s unmarked containers are filled from. */
    public static ResourceKey<LootTable> table(Optional<Identifier> building) {
        return Markers.table(kind(building).map(k -> "stash_" + k).orElse("stash"));
    }

    private Stashes() {}
}
