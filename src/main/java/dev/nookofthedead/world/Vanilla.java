package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Vanilla's own ruins, now and then (the user, 2026-10-16: "バニラにゾンビ村ってあるじゃん？それから、荒廃したポータルとか。
 * この二つをたまーに生成する"): a zombie village - the plains village's zombie houses round a meeting point, filling a
 * block of the country ({@link #village}) - and a ruined portal standing on a lot of its own ({@link #portal}). The
 * templates are the game's (by name; nothing of them is shipped), set down as ours are, their jigsaw blocks turned
 * into what they stand for.
 */
public final class Vanilla {
    /** Of the region cells of the country (wilds, farmland), how many in a hundred have a zombie village. */
    public static final int VILLAGES = 35;
    /** Of the lots, how many in ten thousand are a ruined portal. */
    public static final int PORTALS = 40;

    private static final List<Identifier> HOUSES = names("village/plains/zombie/houses/", "plains_stable_1", "plains_small_house_1",
            "plains_small_house_2", "plains_small_house_3", "plains_small_house_4", "plains_small_house_5", "plains_small_house_6",
            "plains_small_house_7", "plains_small_house_8", "plains_shepherds_house_1", "plains_medium_house_1", "plains_medium_house_2",
            "plains_fletcher_house_1", "plains_butcher_shop_2", "plains_big_house_1", "plains_animal_pen_3", "plains_meeting_point_4",
            "plains_meeting_point_5");
    private static final List<Identifier> CENTRES = names("village/plains/zombie/town_centers/", "plains_meeting_point_1",
            "plains_meeting_point_2", "plains_meeting_point_3", "plains_fountain_01");
    private static final List<Identifier> PORTAL_NAMES = names("ruined_portal/", "portal_1", "portal_2", "portal_3", "portal_4",
            "portal_5", "portal_6", "portal_7", "portal_8", "portal_9", "portal_10", "giant_portal_1", "giant_portal_2", "giant_portal_3");

    /** The desert village's houses, the zombie village's own where it has them, and its meeting points ({@link Desert}). */
    public static final List<Identifier> DESERT_SMALL = names("village/desert/zombie/houses/", "desert_small_house_1", "desert_small_house_2",
            "desert_small_house_3", "desert_small_house_4", "desert_small_house_5", "desert_small_house_6", "desert_small_house_7",
            "desert_small_house_8");
    public static final List<Identifier> DESERT_MEDIUM = List.copyOf(java.util.stream.Stream.concat(
            names("village/desert/zombie/houses/", "desert_medium_house_1", "desert_medium_house_2").stream(),
            names("village/desert/houses/", "desert_temple_1", "desert_temple_2", "desert_library_1", "desert_tannery_1", "desert_mason_1",
                    "desert_armorer_1", "desert_butcher_shop_1", "desert_cartographer_house_1", "desert_fletcher_house_1",
                    "desert_tool_smith_1", "desert_weaponsmith_1", "desert_shepherd_house_1").stream()).toList());
    public static final List<Identifier> DESERT_CENTRES = names("village/desert/zombie/town_centers/", "desert_meeting_point_1",
            "desert_meeting_point_2", "desert_meeting_point_3");

    /** Every template of the game's this may set down (for the tests: each must be there). */
    public static List<Identifier> templates() {
        List<Identifier> all = new ArrayList<>(HOUSES);
        all.addAll(DESERT_SMALL);
        all.addAll(DESERT_MEDIUM);
        all.addAll(DESERT_CENTRES);
        all.addAll(CENTRES);
        all.addAll(PORTAL_NAMES);
        return all;
    }

    private static List<Identifier> names(String folder, String... names) {
        List<Identifier> ids = new ArrayList<>();
        for (String n : names) {
            ids.add(Identifier.withDefaultNamespace(folder + n));
        }
        return List.copyOf(ids);
    }

    /** Whether {@code id} is one of the game's own templates (set down level with the ground, its jigsaws resolved). */
    public static boolean is(Identifier id) {
        return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE);
    }

    /**
     * The game's houses whose entrance (their entrance jigsaw) faces north or south as built, not west as the rest (the
     * review, 2026-10-18: read from their templates; turned as the others, a third of the desert's had their doors to the side).
     */
    private static final java.util.Set<String> NORTH_DOORS = java.util.Set.of("desert_small_house_5", "desert_small_house_8",
            "desert_medium_house_2", "desert_library_1", "desert_tannery_1", "desert_weaponsmith_1");
    private static final java.util.Set<String> SOUTH_DOORS = java.util.Set.of("desert_small_house_3", "desert_small_house_4",
            "desert_shepherd_house_1");

    /** Which way the door of the game's house {@code id} faces as built. */
    public static net.minecraft.core.Direction entrance(Identifier id) {
        String name = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        return NORTH_DOORS.contains(name) ? net.minecraft.core.Direction.NORTH
                : SOUTH_DOORS.contains(name) ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.WEST;
    }

    /** Whether {@code id} is a village house or centre (where its zombie villagers are). */
    public static boolean village(Identifier id) {
        return is(id) && id.getPath().startsWith("village/");
    }

    /** Whether block ({@code bx}, {@code bz}) of the grid is a zombie village. */
    public static boolean villageAt(long seed, int bx, int bz) {
        if (Capital.near(seed, bx * Town.BLOCK, bz * Town.BLOCK, Town.BLOCK, 8)) {
            return false;                                                   // never under the capital (2026-10-24)
        }
        int qx = Math.floorDiv(bx, Region.SIZE);
        int qz = Math.floorDiv(bz, Region.SIZE);
        long h = Town.hash(seed, qx, qz, 150);
        if (Math.floorMod(h, 100) >= VILLAGES) {
            return false;
        }
        int vx = qx * Region.SIZE + 1 + (int) Math.floorMod(h >> 8, (long) Region.SIZE - 2);
        int vz = qz * Region.SIZE + 1 + (int) Math.floorMod(h >> 20, (long) Region.SIZE - 2);
        if (vx != bx || vz != bz) {
            return false;
        }
        // out in the country, the blocks all round it country too: not a hamlet against the town's walls (the user,
        // 2026-10-17: the village's picture "調整不足を自白しているようなもの")
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!Town.rural(Region.of(seed, bx + dx, bz + dz))) {
                    return false;
                }
            }
        }
        return Terrain.lake(seed, bx, bz) == null && Terrain.cave(seed, bx, bz) == null && !Institute.at(seed, bx, bz);
    }

    /**
     * A zombie village's lots in block ({@code bx}, {@code bz}): its meeting point in the middle, and round it, on the lots of
     * a grid of 16, some dozen of the plains' zombie houses, each facing the middle.
     */
    static List<Town.Lot> village(long seed, int bx, int bz, java.util.function.BiFunction<Integer, Integer, Integer> level) {
        List<Town.Lot> lots = new ArrayList<>();
        int x0 = bx * Town.BLOCK + 8;
        int z0 = bz * Town.BLOCK + 8;
        long h = Town.hash(seed, bx, bz, 151);
        for (int i = 0; i < 4; i++) {
            for (int k = 0; k < 4; k++) {
                long q = Town.hash(h, i, k, 152);
                int x = x0 + i * 16;
                int z = z0 + k * 16;
                boolean middle = (i == 1 || i == 2) && (k == 1 || k == 2);
                if (middle && !(i == 1 && k == 1)) {
                    continue;                                              // the middle four: one meeting point, the rest open
                }
                if (!middle && Math.floorMod(q, 100) < 25) {
                    continue;                                              // a gap here and there
                }
                Identifier id = middle ? pick(CENTRES, q >> 8) : pick(HOUSES, q >> 8);
                int cx = middle ? x + 8 : x;
                int cz = middle ? z + 8 : z;
                Direction front = middle ? Direction.from2DDataValue((int) Math.floorMod(q >> 30, 4L))
                        : Math.abs(i - 1.5) > Math.abs(k - 1.5) ? (i < 2 ? Direction.EAST : Direction.WEST) : (k < 2 ? Direction.SOUTH : Direction.NORTH);
                lots.add(Town.Lot.facing(new BlockPos(cx, level.apply(cx + 8, cz + 8), cz), Town.XS_LOT, Town.Use.BUILDING, Optional.of(id), q, front));
            }
        }
        return lots;
    }

    /** A ruined portal for a lot of {@code size}, now and then ({@link #PORTALS} in ten thousand), else none. */
    static Optional<Identifier> portal(long q, int size) {
        if (size > Town.S_LOT || Math.floorMod(Town.hash(q, 0, 0, 153), 10000) >= PORTALS) {
            return Optional.empty();
        }
        return Optional.of(pick(PORTAL_NAMES, q >> 12));
    }

    private static Identifier pick(List<Identifier> list, long roll) {
        return list.get((int) Math.floorMod(roll, (long) list.size()));
    }

    private Vanilla() {}
}
