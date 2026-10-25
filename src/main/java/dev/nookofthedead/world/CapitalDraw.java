package dev.nookofthedead.world;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Draws the capital's part of a chunk ({@link Capital}): every column it owns, from its ground up - the walls and the
 * gates, the moat and the ground outside, the streets and the squares, the river, its quays and bridges, the houses
 * ({@link CapitalHouses}), the crag and the castle ({@link CapitalCastle}), the cathedral and the hall
 * ({@link CapitalLandmarks}). The town draws the rest of the chunk ({@link Town#decorate}).
 */
final class CapitalDraw {
    /**
     * The gates: all shut against the country when the fever came (the diaries: none opened); 2, the south gate, its
     * portcullis since jammed half way, a way in under it - and where the wall fell (BREACHES).
     */
    static boolean gateOpen(int k) {
        return k == 2;
    }

    /** How high the city wall stands over its ground; its towers'. */
    static final int WALL_H = 14;
    static final int TOWER_H = 21;
    static final int WALL_TOWERS = 26;

    static void decorate(WorldGenLevel level, BoundingBox box) {
        long seed = level.getSeed();
        Capital.Site s = Capital.site(seed);
        int x0 = box.minX() - 1;
        int z0 = box.minZ() - 1;
        Capital.Column[][] w = new Capital.Column[18][18];
        boolean any = false;
        boolean apron = false;
        for (int a = 0; a < 18; a++) {
            for (int b = 0; b < 18; b++) {
                w[a][b] = Capital.column(seed, x0 + a, z0 + b);
                if (a >= 1 && a <= 16 && b >= 1 && b <= 16 && w[a][b] != null) {
                    any = true;
                    apron |= w[a][b].kind() == Capital.Kind.APRON || w[a][b].kind() == Capital.Kind.ROAD;
                }
            }
        }
        if (!any) {
            return;
        }
        Town.Plan plan = apron ? Town.plan(seed, x0, z0) : null;
        CapitalHouses.Memo memo = new CapitalHouses.Memo();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        Diaries.at(Diaries.Place.CAPITAL);
        Markers.defer(true);
        try {
            for (int a = 1; a <= 16; a++) {
                for (int b = 1; b <= 16; b++) {
                    Capital.Column c = w[a][b];
                    if (c == null) {
                        continue;
                    }
                    int x = x0 + a;
                    int z = z0 + b;
                    // the wall's towers stand out over its foot and in over the ring road: whole, whatever is under them
                    if (c.r() >= Capital.WALL - 4 && c.r() < Capital.WALL + 9 && towerAt(level, s, c, x, z, box, at)) {
                        continue;
                    }
                    switch (c.kind()) {
                        case APRON, ROAD, BERM -> CapitalOutside.draw(level, s, c, x, z, plan == null ? c.ground() : plan.top()[a][b], box, at);
                        case MOAT -> moat(level, s, c, x, z, box, at);
                        case CAUSEWAY -> causeway(level, s, c, x, z, box, at);
                        case WALL, GATE, WATERGATE -> wall(level, s, c, x, z, box, at);
                        case STREET, SQUARE -> {
                            street(level, s, w, a, b, c, x, z, box, at);
                            if (c.kind() == Capital.Kind.STREET && c.upper() > 0) {
                                CapitalHouses.draw(level, s, w, a, b, x, z, box, memo, at);
                            }
                            CapitalFacades.draw(level, s, w, a, b, c, x, z, box, at);
                        }
                        case BRIDGE -> bridge(level, s, w, a, b, c, x, z, box, at);
                        case RIVER -> river(level, s, c, x, z, box, at);
                        case QUAY -> {
                            quay(level, s, w, a, b, c, x, z, box, at);
                            CapitalFacades.quayLadder(level, s, w, a, b, c, x, z, box, at);
                            CapitalFacades.draw(level, s, w, a, b, c, x, z, box, at);
                        }
                        case BUILDING -> {
                            foundation(level, s, x, z, c.ground(), box, at);
                            CapitalHouses.draw(level, s, w, a, b, x, z, box, memo, at);
                        }
                        case CRAG, RAMP -> CapitalCastle.drawCrag(level, s, c, x, z, box, at);
                        case OLDWALL -> oldWall(level, s, w, a, b, c, x, z, box, at);
                        case CASTLE -> {
                            Diaries.at(Diaries.Place.CASTLE);
                            CapitalCastle.draw(level, s, c, x, z, box, at);
                            Diaries.at(Diaries.Place.CAPITAL);
                        }
                        case CATHEDRAL, HALL -> CapitalLandmarks.draw(level, s, c, x, z, box, at);
                    }
                }
            }
        } finally {
            Markers.defer(false);                                           // the bodies last, every wall round them drawn
            Diaries.at(Diaries.Place.TOWN);
        }
    }

    // --- ground ---------------------------------------------------------------------------------------------------

    /** Old stone in patches: the faces of terraces, quays, foundations. */
    static BlockState oldStone(Capital.Site s, int x, int y, int z) {
        double v = Town.noise(s.seed(), x + y * 7, z - y * 5, 3.0, 370);
        Block block = v < 0.35 ? Blocks.STONE_BRICKS : v < 0.55 ? Blocks.MOSSY_STONE_BRICKS : v < 0.75 ? Blocks.COBBLESTONE
                : v < 0.9 ? Blocks.MOSSY_COBBLESTONE : Blocks.CRACKED_STONE_BRICKS;
        return block.defaultBlockState();
    }

    /** The ground under a column up to {@code top} (not its top block): old stone where it stands above the land's own. */
    static void foundation(WorldGenLevel level, Capital.Site s, int x, int z, int top, BoundingBox box, BlockPos.MutableBlockPos at) {
        for (int y = Capital.WATER - 4; y < top; y++) {
            set(level, box, at.set(x, y, z), y < Capital.WATER - 1 ? Blocks.STONE.defaultBlockState() : oldStone(s, x, y, z));
        }
        for (int y = top; y <= Town.SURFACE; y++) {
            set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    /** Earth under a column to {@code top}: stone, then dirt, its top block left to the caller. */
    static void earth(WorldGenLevel level, int x, int z, int top, BoundingBox box, BlockPos.MutableBlockPos at) {
        for (int y = Capital.WATER - 4; y < top; y++) {
            set(level, box, at.set(x, y, z), (y < top - 3 ? Blocks.STONE : Blocks.DIRT).defaultBlockState());
        }
        for (int y = top; y <= Town.SURFACE; y++) {
            set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    // --- the streets ------------------------------------------------------------------------------------------------

    /** Whether column {@code c} is in the burnt quarter: beyond the river, round the main gate's way in. */
    static boolean burnt(Capital.Site s, Capital.Column c) {
        // the whole of the far bank: shut off at its bridges, then burnt (the story's review: half of it stood unburnt,
        // the proclamations and the minutes speak of all of it)
        return c.river() > Capital.QUAY_HALF && c.r() < Capital.WALL;
    }

    /** Whether the neighbour of ({@code a}, {@code b}) toward {@code out} is a main way (an avenue, a ring, a square). */
    static boolean front(Capital.Site s, Capital.Column[][] w, int a, int b, Direction out) {
        int na = a + out.getStepX();
        int nb = b + out.getStepZ();
        if (na < 0 || nb < 0 || na >= w.length || nb >= w.length || w[na][nb] == null) {
            return false;
        }
        Capital.Column n = w[na][nb];
        return n.kind() == Capital.Kind.SQUARE || n.kind() == Capital.Kind.STREET && n.block() == 0;
    }

    private static void street(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int z,
            BoundingBox box, BlockPos.MutableBlockPos at) {
        int y = c.ground();
        foundation(level, s, x, z, y, box, at);
        boolean burnt = burnt(s, c);
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 371), 1000L);
        double v = Town.noise(s.seed(), x, z, 6.0, 372);
        Block paving;
        if (c.kind() == Capital.Kind.SQUARE) {
            paving = v < 0.5 ? Blocks.STONE_BRICKS : v < 0.7 ? Blocks.POLISHED_ANDESITE : v < 0.85 ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS;
        } else if (c.block() == 0) {
            paving = v < 0.45 ? Blocks.STONE_BRICKS : v < 0.7 ? Blocks.COBBLESTONE : v < 0.85 ? Blocks.ANDESITE : Blocks.MOSSY_COBBLESTONE;
        } else {
            paving = v < 0.4 ? Blocks.COBBLESTONE : v < 0.6 ? Blocks.MOSSY_COBBLESTONE : v < 0.75 ? Blocks.GRAVEL : v < 0.88 ? Blocks.ANDESITE : Blocks.COARSE_DIRT;
        }
        if (burnt) {
            paving = v < 0.4 ? Blocks.COBBLED_DEEPSLATE : v < 0.7 ? Blocks.TUFF : v < 0.85 ? Blocks.GRAVEL : Blocks.BLACKSTONE;
        }
        set(level, box, at.set(x, y, z), paving.defaultBlockState());
        clearAbove(level, box, at, x, z, y + 1, y + (c.upper() > 0 ? 3 : 6));
        // a step where the street rises a block to a neighbour
        Direction up = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = w[a + d.getStepX()][b + d.getStepZ()];
            if (n != null && (n.kind() == Capital.Kind.STREET || n.kind() == Capital.Kind.SQUARE || n.kind() == Capital.Kind.BRIDGE)
                    && n.ground() == y + 1) {
                up = d;
                break;
            }
        }
        if (up != null) {
            Block stair = paving == Blocks.STONE_BRICKS || paving == Blocks.CRACKED_STONE_BRICKS || paving == Blocks.MOSSY_STONE_BRICKS
                    ? Blocks.STONE_BRICK_STAIRS : paving == Blocks.ANDESITE || paving == Blocks.POLISHED_ANDESITE ? Blocks.ANDESITE_STAIRS
                    : Blocks.COBBLESTONE_STAIRS;
            set(level, box, at.set(x, y + 1, z), stair.defaultBlockState().setValue(StairBlock.FACING, up));
            return;
        }
        if (c.kind() == Capital.Kind.SQUARE && !burnt && CapitalStreets.stall(level, s, x, y, z, box, at)) {
            return;                                                         // a market stall
        }
        // the square's well, at its middle; by it, the stand the proclamations were read from
        if (c.kind() == Capital.Kind.SQUARE) {
            Capital.Grid g = Capital.blocksOf(c.block());
            double[] p = Capital.seedOf(s.seed(), Capital.keyI(c.block()), Capital.keyJ(c.block()), g.size(), g.salt());
            double cx = p[0] - Capital.warpX(s.seed(), (int) p[0], (int) p[1]);
            double cz = p[1] - Capital.warpZ(s.seed(), (int) p[0], (int) p[1]);
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            if (d < 2.6) {
                CapitalStory.fountain(level, s, x, y, z, d, box, at);
                return;
            }
            if (x == (int) Math.floor(cx) + 3 && z == (int) Math.floor(cz) && !burnt) {
                CapitalStory.lectern(level, box, at.set(x, y + 1, z).immutable(), Notes.any(Notes.CAPITAL, RandomSource.create(Town.hash(s.seed(), x, z, 393))),
                        Direction.WEST);
                return;
            }
        }
        if (c.r() >= Capital.WALL - 1) {
            CapitalStreets.gatePoster(level, s, c, x, y, z, box);
        }
        CapitalStory.onStreet(level, s, w, a, b, c, x, y, z, burnt, r, box, at);
    }

    /**
     * What stands of the old town's wall: a broad ruin along the old town's edge, as high as a house here, worn to a stump
     * there, gone where the streets came through; old stone, ivy on it. The old town's ground stands higher behind it.
     */
    private static void oldWall(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int z,
            BoundingBox box, BlockPos.MutableBlockPos at) {
        int y = c.ground();
        foundation(level, s, x, z, y, box, at);
        set(level, box, at.set(x, y, z), oldStone(s, x, y, z));
        // gone where a lane of the old town runs into it
        boolean gap = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = w[a + d.getStepX()][b + d.getStepZ()];
            if (n != null && n.kind() == Capital.Kind.STREET && n.block() != 0) {
                gap = true;
            }
        }
        double v = Town.noise(s.seed(), x, z, 9.0, 397);
        int height = gap ? 0 : (int) Math.round(Math.max(0, (v - 0.25) * 14));
        for (int k = 1; k <= height; k++) {
            set(level, box, at.set(x, y + k, z), oldStone(s, x, y + k, z));
        }
        clearAbove(level, box, at, x, z, y + height + 1, y + height + 4);
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 398), 100L);
        if (height > 2 && r < 40) {
            set(level, box, at.set(x, y + height + 1, z), (r < 20 ? Blocks.MOSS_CARPET : Blocks.SHORT_GRASS).defaultBlockState());
        } else if (height == 0 && r < 30) {
            set(level, box, at.set(x, y + 1, z), (r < 15 ? Blocks.COBBLESTONE_SLAB : Blocks.MOSSY_COBBLESTONE).defaultBlockState());
        }
    }

    static void clearAbove(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos at, int x, int z, int from, int to) {
        for (int y = from; y <= Math.max(to, Town.SURFACE); y++) {
            set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    // --- the river ----------------------------------------------------------------------------------------------------

    private static void river(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        int bed = Capital.WATER - 3;
        for (int y = Capital.WATER - 7; y < bed; y++) {
            set(level, box, at.set(x, y, z), Blocks.STONE.defaultBlockState());
        }
        double n = Town.noise(s.seed(), x, z, 4.0, 373);
        set(level, box, at.set(x, bed, z), (n < 0.4 ? Blocks.GRAVEL : n < 0.7 ? Blocks.MUD : n < 0.85 ? Blocks.CLAY : Blocks.SAND).defaultBlockState());
        for (int y = bed + 1; y <= Capital.WATER; y++) {
            set(level, box, at.set(x, y, z), Blocks.WATER.defaultBlockState());
        }
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 374), 1000L);
        if (r < 60) {
            set(level, box, at.set(x, bed + 1, z), (r < 40 ? Blocks.SEAGRASS : Blocks.KELP_PLANT).defaultBlockState());
        }
        clearAbove(level, box, at, x, z, Capital.WATER + 1, Capital.WATER + 2);
        CapitalStory.onRiver(level, s, c, x, z, r, box, at);
    }

    private static void quay(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int z,
            BoundingBox box, BlockPos.MutableBlockPos at) {
        int y = c.ground();
        foundation(level, s, x, z, y, box, at);
        boolean edge = Math.abs(c.river()) < Capital.RIVER_HALF + 1;
        double v = Town.noise(s.seed(), x, z, 5.0, 375);
        set(level, box, at.set(x, y, z), (edge ? Blocks.POLISHED_ANDESITE : v < 0.5 ? Blocks.STONE_BRICKS : v < 0.75 ? Blocks.COBBLESTONE
                : Blocks.MOSSY_STONE_BRICKS).defaultBlockState());
        clearAbove(level, box, at, x, z, y + 1, y + 4);
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 376), 1000L);
        if (edge && CapitalStreets.cranePost(level, s, c, x, z, box, at)) {
            return;
        }
        if (edge && r < 40) {
            set(level, box, at.set(x, y + 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());     // a mooring post
        } else if (!edge && r < 25) {
            set(level, box, at.set(x, y + 1, z), (r < 12 ? Blocks.BARREL : dev.nookworks.registry.ModBlocks.CRATE.get()).defaultBlockState());
        } else if (r > 996) {
            Markers.remains(level, at.set(x, y + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(r)),
                    RandomSource.create(r));
        }
    }

    /** A bridge over the river: its deck at the street's level, one arch under it from quay to quay, a parapet each side. */
    private static void bridge(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int z,
            BoundingBox box, BlockPos.MutableBlockPos at) {
        int deck = c.ground();
        double t = Math.min(1, Math.abs(c.river()) / Capital.QUAY_HALF);
        int crown = Math.max(Capital.WATER + 3, deck - 2);
        int underside = (int) Math.round(Capital.WATER + 2 + (crown - Capital.WATER - 2) * Math.sqrt(Math.max(0, 1 - t * t)));
        boolean water = Math.abs(c.river()) < Capital.RIVER_HALF;
        int bed = Capital.WATER - 3;
        for (int y = Capital.WATER - 7; y <= deck; y++) {
            BlockState state;
            if (y < bed) {
                state = Blocks.STONE.defaultBlockState();
            } else if (y >= underside) {
                state = y == deck ? Blocks.STONE_BRICKS.defaultBlockState() : oldStone(s, x, y, z);
            } else if (water) {
                state = y == bed ? Blocks.GRAVEL.defaultBlockState() : y <= Capital.WATER ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            } else {
                state = y <= Capital.QUAY ? oldStone(s, x, y, z) : Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        clearAbove(level, box, at, x, z, deck + 1, deck + 5);
        if (c.edge() >= 0) {
            bridgeShop(level, s, c, x, deck, z, box, at);
            return;
        }
        // the parapet: where the column beside is not bridge or street
        boolean parapet = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = w[a + d.getStepX()][b + d.getStepZ()];
            if (n != null && (n.kind() == Capital.Kind.RIVER || n.kind() == Capital.Kind.QUAY)) {
                parapet = true;
            }
        }
        if (parapet) {
            set(level, box, at.set(x, deck + 1, z), along(Blocks.STONE_BRICK_WALL.defaultBlockState(), across(s)));
        } else {
            CapitalStory.onBridge(level, s, c, x, deck, z, box, at);
        }
    }

    /**
     * A shop on the main bridge: a row of them down each side of its way, wall to wall, two storeys of timber and plaster
     * over the water, open to the way at the front, the upper floor over the counter; a roof sloping down to the river.
     */
    private static void bridgeShop(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int deck, int z, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        double t = c.edge();                                              // 0 at the way, SHOPS at the river side
        double along = c.river() + Capital.QUAY_HALF;
        int pos = Math.floorMod((int) Math.floor(along), 5);
        int seg = (int) Math.floor(along / 5);
        boolean back = t > Capital.SHOPS - 1.1;
        boolean party = pos == 0;
        boolean front = t < 1;
        long h = Town.hash(s.seed(), seg, (long) Math.signum(Capital.wrap(c.angle() - Capital.avenue(s, 0, c.r()))), 406);
        boolean burnt = c.river() > 0;
        Block infill = burnt ? Blocks.TUFF : Math.floorMod(h, 3L) == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.CALCITE;
        Block frame = burnt ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
        int rise = (int) Math.floor(Math.max(0, Capital.SHOPS - t) * 0.9);
        // a ladder up to the room over the shop, against the next one's party wall
        boolean ladder = pos == 4 && t >= 2 && t < 3;
        Direction ladderFacing = Direction.getApproximateNearest(s.az(), 0, -s.ax());
        for (int y = deck + 1; y <= deck + 8 + rise; y++) {
            int k = y - deck;
            Block block;
            if (ladder && k <= 4) {
                set(level, box, at.set(x, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ladderFacing));
                continue;
            }
            if (k > 8) {
                block = party ? frame : front ? infill : Blocks.AIR;               // the gables, up under the roof
            } else if (k == 4) {
                block = back || party ? frame : Blocks.SPRUCE_PLANKS;            // the upper floor
            } else if (back || party) {
                boolean window = back && !party && (k == 2 || k == 6) && pos % 2 == 1;
                block = window ? (burnt ? Blocks.AIR : Blocks.GLASS_PANE) : party || k == 8 ? frame : infill;
                if (block == Blocks.GLASS_PANE) {
                    set(level, box, at.set(x, y, z), along(Blocks.GLASS_PANE.defaultBlockState(), across(s)));
                    continue;
                }
            } else if (front && k <= 3) {
                block = k == 1 && pos == 2 ? Blocks.SPRUCE_PLANKS : Blocks.AIR;   // the counter
            } else if (front && k >= 5) {
                block = k == 8 ? frame : infill;                                  // the upper floor's front, over the counter
            } else {
                block = Blocks.AIR;
            }
            set(level, box, at.set(x, y, z), CapitalHouses.weather(s, block, x, y, z, burnt).defaultBlockState());
        }
        // the roof: down from the way to the river
        if (!(burnt && Town.noise(s.seed(), x, z, 4.0, 407) < 0.6)) {
            set(level, box, at.set(x, deck + 9 + rise, z), Blocks.BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, towardWay(s, c)));
        }
    }

    /** From a shop on the main bridge toward its way. */
    private static Direction towardWay(Capital.Site s, Capital.Column c) {
        double way = Capital.avenue(s, 0, c.r());
        double side = Math.signum(Capital.wrap(c.angle() - way));
        // across the way: perpendicular to the avenue (which runs out along the gate's angle), back toward its middle
        double ax = Math.sin(way) * side;
        double az = -Math.cos(way) * side;
        return Direction.getApproximateNearest(ax, 0, az);
    }

    // --- outside the wall ---------------------------------------------------------------------------------------------

    private static void moat(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        boolean bank = c.r() < Capital.MOAT_IN + 1 || c.r() >= Capital.MOAT_OUT - 1;
        int ground = c.ground();
        // before a breach, the wall's fall filled the moat: a heap of its stone to cross on (the review: no way in but
        // the south gate, the moat with sheer banks)
        for (double bAngle : BREACHES) {
            double arc = c.r() * Math.abs(Capital.wrap(c.angle() - s.gate(0) - bAngle));
            if (arc < 4.5 + Town.noise(s.seed(), x, z, 3.0, 380) * 2) {
                int top = ground - (int) Math.floor(Math.max(0, arc - 3) * 0.7);
                for (int y = Capital.WATER - 7; y <= Math.max(top, Capital.WATER); y++) {
                    double v = Town.noise(s.seed(), x + y * 5, z, 2.0, 381);
                    set(level, box, at.set(x, y, z), y <= top ? (v < 0.4 ? Blocks.COBBLESTONE : v < 0.7 ? Blocks.MOSSY_COBBLESTONE : v < 0.85
                            ? Blocks.STONE_BRICKS : Blocks.GRAVEL).defaultBlockState() : y <= Capital.WATER ? Blocks.WATER.defaultBlockState()
                            : Blocks.AIR.defaultBlockState());
                }
                clearAbove(level, box, at, x, z, Math.max(top, Capital.WATER) + 1, Math.max(top, Capital.WATER) + 3);
                return;
            }
        }
        int bed = Capital.WATER - 3;
        for (int y = Capital.WATER - 7; y <= Math.max(ground, Capital.WATER); y++) {
            BlockState state;
            if (bank) {
                state = y <= ground ? oldStone(s, x, y, z) : Blocks.AIR.defaultBlockState();
            } else if (y < bed) {
                state = Blocks.STONE.defaultBlockState();
            } else if (y == bed) {
                state = Blocks.MUD.defaultBlockState();
            } else {
                state = y <= Capital.WATER ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        clearAbove(level, box, at, x, z, Math.max(ground, Capital.WATER) + 1, Math.max(ground, Capital.WATER) + 2);
        if (!bank && Math.floorMod(Town.hash(s.seed(), x, z, 377), 1000L) < 50) {
            set(level, box, at.set(x, Capital.WATER - 2, z), Blocks.SEAGRASS.defaultBlockState());
        }
    }

    private static void causeway(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        int y = c.ground();
        foundation(level, s, x, z, y, box, at);
        set(level, box, at.set(x, y, z), (Town.noise(s.seed(), x, z, 4.0, 378) < 0.6 ? Blocks.STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState());
        clearAbove(level, box, at, x, z, y + 1, y + 5);
        double arc = c.r() * Math.abs(Capital.wrap(c.angle() - nearestGate(s, c.angle())));
        if (arc > 2.7 && c.r() >= Capital.MOAT_IN - 1 && c.r() < Capital.MOAT_OUT + 1) {
            set(level, box, at.set(x, y + 1, z), along(Blocks.STONE_BRICK_WALL.defaultBlockState(),
                    tangent(c.angle()) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X));   // the causeway's sides run out from the gate
        }
    }

    static double nearestGate(Capital.Site s, double angle) {
        double best = 0;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double d = Math.abs(Capital.wrap(angle - s.gate(k)));
            if (d < bestD) {
                bestD = d;
                best = s.gate(k);
            }
        }
        return best;
    }

    static int nearestGateIndex(Capital.Site s, double angle) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            double d = Math.abs(Capital.wrap(angle - s.gate(k)));
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        return best;
    }

    // --- the wall -------------------------------------------------------------------------------------------------------

    /** Where the wall is broken through, at these angles from the main gate (a section fallen, its rubble a way over). */
    static final double[] BREACHES = {0.62, 2.35, 4.1};

    private static void wall(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        int base = c.ground();
        double r = c.r();
        double angle = c.angle();
        foundation(level, s, x, z, base, box, at);
        int gate = nearestGateIndex(s, angle);
        double gateArc = r * Math.abs(Capital.wrap(angle - s.gate(gate)));
        // a breach
        for (double bAngle : BREACHES) {
            double arc = r * Math.abs(Capital.wrap(angle - s.gate(0) - bAngle));
            if (arc < 6) {
                // the wall's stump, rising from the gap a block at a time to either side: walked up over the rubble
                int left = (int) Math.floor(base + arc * 0.8 + Town.noise(s.seed(), x, z, 3.0, 379) * 1.2);
                for (int y = base; y <= base + WALL_H + 2; y++) {
                    set(level, box, at.set(x, y, z), y <= left ? (y > left - 2 ? Blocks.COBBLESTONE.defaultBlockState() : oldStone(s, x, y, z))
                            : Blocks.AIR.defaultBlockState());
                }
                return;
            }
        }
        boolean gateHere = c.kind() == Capital.Kind.GATE;
        boolean water = c.kind() == Capital.Kind.WATERGATE;
        int top = base + WALL_H;
        boolean outer = r >= Capital.WALL + Capital.WALL_T - 1;
        for (int y = (water ? Capital.WATER - 7 : base); y <= top + (gateHere ? 4 : 0); y++) {
            BlockState state;
            if (water && y <= Capital.WATER + 3) {
                boolean grate = r >= Capital.WALL + 1 && r < Capital.WALL + 2 && y > Capital.WATER - 3;
                state = y < Capital.WATER - 3 ? Blocks.STONE.defaultBlockState() : y == Capital.WATER - 3 ? Blocks.GRAVEL.defaultBlockState()
                        : grate ? along(Blocks.IRON_BARS.defaultBlockState(), tangent(c.angle())).setValue(BlockStateProperties.WATERLOGGED, y <= Capital.WATER)
                        : y <= Capital.WATER ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            } else if (gateHere && y > base && y <= base + 7 && gateArc < 2.6) {
                boolean portcullis = r >= Capital.WALL + 1 && r < Capital.WALL + 2;
                state = portcullis && (!gateOpen(gate) || gate == 2 && y > base + 3) ? along(Blocks.IRON_BARS.defaultBlockState(), tangent(c.angle()))
                        : y == base + 7 ? oldStone(s, x, y, z) : Blocks.AIR.defaultBlockState();

            } else if (gateHere && y == base && gateArc < 2.6) {
                state = Blocks.STONE_BRICKS.defaultBlockState();
            } else {
                state = (y == top && !outer) ? Blocks.STONE_BRICKS.defaultBlockState() : oldStone(s, x, y, z);
            }
            set(level, box, at.set(x, y, z), state);
        }
        // the walk's parapet, outward
        if (outer) {
            boolean merlon = Math.floorMod(x + z, 2) == 0;
            set(level, box, at.set(x, top + 1 + (gateHere ? 4 : 0), z), merlon ? oldStone(s, x, top + 1, z) : Blocks.AIR.defaultBlockState());
        }
        clearAbove(level, box, at, x, z, top + 2 + (gateHere ? 4 : 0), top + 6);
    }

    /**
     * The wall's towers: round, one every {@link #WALL_TOWERS}th of the way round, and a pair either side of each gate. Draws
     * column ({@code x}, {@code z}) of one if it is under one; whether it was.
     */
    private static boolean towerAt(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        double angle = c.angle();
        int base = (int) Math.round(Capital.city(s, s.x() + Math.cos(angle) * (Capital.WALL + 2), s.z() + Math.sin(angle) * (Capital.WALL + 2)));
        for (int k = 0; k < 4; k++) {
            double ga = s.gate(k);
            for (int side = -1; side <= 1; side += 2) {
                double gx = s.x() + Math.cos(ga) * (Capital.WALL + 2) - Math.sin(ga) * side * 7;
                double gz = s.z() + Math.sin(ga) * (Capital.WALL + 2) + Math.cos(ga) * side * 7;
                double gd = Math.hypot(x + 0.5 - gx, z + 0.5 - gz);
                if (gd < 4.6) {
                    wallTower(level, s, x, z, base, gd, 4.6, TOWER_H + 3, box, at, gx, gz);
                    return true;
                }
            }
        }
        double step = Math.PI * 2 / WALL_TOWERS;
        double rel = Capital.wrap(angle - s.gate(0) - step / 2);
        double nearest = Math.round(rel / step) * step + s.gate(0) + step / 2;
        if (Math.abs(Capital.wrap(nearest - nearestGate(s, nearest))) * Capital.WALL < 14) {
            return false;                                                   // none crowding a gate's own pair
        }
        for (double b : BREACHES) {
            if (Math.abs(Capital.wrap(nearest - s.gate(0) - b)) * Capital.WALL < 10) {
                return false;                                               // nor where the wall came down
            }
        }
        double tx = s.x() + Math.cos(nearest) * (Capital.WALL + 3);
        double tz = s.z() + Math.sin(nearest) * (Capital.WALL + 3);
        double td = Math.hypot(x + 0.5 - tx, z + 0.5 - tz);
        if (td < 5.0 && Math.abs(c.river()) > Capital.RIVER_HALF + 2) {
            wallTower(level, s, x, z, base, td, 5.0, TOWER_H, box, at, tx, tz);
            return true;
        }
        return false;
    }

    private static void wallTower(WorldGenLevel level, Capital.Site s, int x, int z, int base, double d, double radius, int height,
            BoundingBox box, BlockPos.MutableBlockPos at, double tx, double tz) {
        boolean shell = d > radius - 1.5;
        int top = base + height;
        CapitalCastle.Tower t = CapitalCastle.Tower.of(s, tx, tz, x, z, radius);
        for (int y = Capital.WATER - 4; y < base; y++) {
            set(level, box, at.set(x, y, z), oldStone(s, x, y, z));
        }
        for (int y = base; y <= top; y++) {
            int h = y - base;
            BlockState state;
            if (shell) {
                boolean slit = h % 5 == 3 && Math.floorMod(x * 3 + z, 5) == 0;
                // a door from the city at its foot; onto the wall's walk either side
                boolean door = t.inward() && h >= 1 && h <= 3;
                boolean walkway = t.onWall(Capital.WALL, Capital.WALL + Capital.WALL_T) && h > WALL_H && h <= WALL_H + 2;
                state = slit || door || walkway ? Blocks.AIR.defaultBlockState() : oldStone(s, x, y, z);
            } else if (t.ladder() && h >= 1 && y < top) {
                state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, t.ladderFacing());
            } else {
                state = y == base || h % 7 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (shell) {
            boolean merlon = Math.floorMod(x + z, 2) == 0;
            set(level, box, at.set(x, top + 1, z), merlon ? oldStone(s, x, top + 1, z) : Blocks.AIR.defaultBlockState());
        }
        clearAbove(level, box, at, x, z, top + 2, top + 4);
    }

    // --- helpers ------------------------------------------------------------------------------------------------------

    /** A container of {@code block} at {@code at}, given {@code kind}'s loot. */
    static void container(WorldGenLevel level, BoundingBox box, BlockPos at, Block block, String kind, long seed) {
        if (!box.isInside(at)) {
            return;
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, Direction.UP);
        }
        level.setBlock(at, state, 2);
        if (level.getBlockEntity(at) instanceof RandomizableContainer container) {
            container.setLootTable(Markers.table(kind), seed);
        }
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    /**
     * A pane, bars, a fence or a wall joined to its neighbours along {@code axis} - the run of the wall or the parapet it is
     * set in. Set without neighbour updates, they stood alone, thin posts (the review); every one is set whole here.
     */
    static BlockState along(BlockState state, Direction.Axis axis) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                state = state.setValue(net.minecraft.world.level.block.CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d), d.getAxis() == axis);
            }
        } else if (state.getBlock() instanceof net.minecraft.world.level.block.WallBlock) {
            var low = net.minecraft.world.level.block.state.properties.WallSide.LOW;
            var none = net.minecraft.world.level.block.state.properties.WallSide.NONE;
            boolean x = axis == Direction.Axis.X;
            state = state.setValue(BlockStateProperties.EAST_WALL, x ? low : none).setValue(BlockStateProperties.WEST_WALL, x ? low : none)
                    .setValue(BlockStateProperties.NORTH_WALL, x ? none : low).setValue(BlockStateProperties.SOUTH_WALL, x ? none : low)
                    .setValue(BlockStateProperties.UP, false);
        }
        return state;
    }

    /** The axis of the world nearest the run of a ring round the capital's middle at {@code angle} (its tangent). */
    static Direction.Axis tangent(double angle) {
        return Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)) ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** The axis of the world nearest across the river (the way its bridges run). */
    static Direction.Axis across(Capital.Site s) {
        return Math.abs(s.az()) > Math.abs(s.ax()) ? Direction.Axis.X : Direction.Axis.Z;
    }

    private CapitalDraw() {}
}
