package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The capital's crag and the castle on it ({@link Capital}): a rock standing up out of the city, cliffs on every side,
 * one road cut up round it - half a turn from its foot, where the main avenue comes in, to the castle's gate on top.
 * <p>
 * On top, inside a curtain wall with ten round towers: the gatehouse, its portcullis down and the passage behind it
 * barricaded from within; the keep, seven storeys and a spire over the whole city; the great hall and its throne; the
 * chapel; barracks, kitchen and stables along the wall; the court between, its carts still loaded with what was kept
 * from the city. Laid out on the castle's own axes - forward from the middle to the gate, and across - so every chunk
 * draws its part of it from the same plan.
 */
final class CapitalCastle {
    /** The keep: its middle {@link #KEEP_F} back of the castle's middle, {@link #KEEP_HALF} each way; its storeys. */
    static final int KEEP_F = -12;
    static final int KEEP_HALF = 10;
    static final int KEEP_STOREY = 6;
    static final int KEEP_STOREYS = 7;
    /** The curtain wall's inner face and thickness, its height over the top. */
    static final int CURTAIN = Capital.R_TOP - 4;
    static final int CURTAIN_T = 3;
    static final int CURTAIN_H = 11;
    static final int TOWERS = 10;
    static final double TOWER_R = 5.0;
    /** Half the road up's width. */
    static final double RAMP_HALF = 2.7;

    /** The road up's radius {@code u} of the way up (0 at its foot, 1 at the castle's gate). */
    static double rampRadius(double u, double foot) {
        double start = foot - 1.5;
        double end = Capital.R_TOP + 6;
        return start + (end - start) * u;
    }

    /**
     * How far up the road the column toward {@code angle} is, 0 to 1, or NaN where the road does not pass: from where the
     * main avenue reaches the crag (it winds; in game the road began beside it, behind rock) half round to the castle's
     * gate, opposite the avenue's.
     */
    static double rampU(Capital.Site s, double angle) {
        double a0 = Capital.wrap(Capital.avenue(s, 0, Capital.R_CRAG) - s.gate(0)) - 0.08;
        double phi = Capital.wrap(angle - s.gate(0));
        if (phi < a0 - 0.04) {
            phi += Math.PI * 2;
        }
        if (phi > Math.PI + 0.06) {
            return Double.NaN;
        }
        return Math.max(0, Math.min(1, (phi - a0) / (Math.PI - a0)));
    }

    /** The ground at the ramp's foot: the city's where the main avenue meets the crag. */
    static double rampFoot(Capital.Site s) {
        double g = Capital.avenue(s, 0, Capital.R_CRAG);
        double foot = Capital.cragFoot(s, g);
        return Capital.city(s, s.x() + Math.cos(g) * (foot + 4), s.z() + Math.sin(g) * (foot + 4));
    }

    /** The crag's rock at ({@code x}, {@code z}), {@code r} out toward {@code angle}, its foot at {@code foot}. */
    static double rock(Capital.Site s, int x, int z, double r, double angle, double foot) {
        double footGround = Capital.city(s, s.x() + Math.cos(angle) * foot, s.z() + Math.sin(angle) * foot);
        double t = Math.max(0, Math.min(1, (foot - r) / (foot - Capital.R_TOP)));
        double rough = (Town.noise(s.seed(), x, z, 6.0, 350) - 0.5) * 6 * (1 - Math.abs(2 * t - 1));
        double ledges = Math.floor((Town.noise(s.seed(), x, z, 11.0, 351)) * 3) * (t > 0.2 && t < 0.85 ? 1 : 0);
        double h = footGround + (Capital.TOP - 1 - footGround) * Math.pow(t, 0.42) + rough + ledges;
        return Math.min(Capital.TOP - 1, h);
    }

    /** What column ({@code x}, {@code z}) on the crag is: the castle's top, the road up, or the rock. */
    static Capital.Column column(Capital.Site s, int x, int z, double r, double angle, double foot, double river) {
        if (r < Capital.R_TOP + 4) {
            return new Capital.Column(Capital.Kind.CASTLE, Capital.TOP, 0, 0, 0, 0, 0, r, angle, river);
        }
        double u = rampU(s, angle);
        double rock = rock(s, x, z, r, angle, foot);
        if (!Double.isNaN(u)) {
            double rr = rampRadius(u, foot);
            double h0 = rampFoot(s);
            double h = h0 + (Capital.TOP - h0) * u;
            if (Math.abs(r - rr) < RAMP_HALF) {
                int ground = (int) Math.floor(h);
                // the parapet on the drop side; the fraction for a slab's half step
                return new Capital.Column(Capital.Kind.RAMP, ground, 0, 0, h - ground, r - rr, 0, r, angle, river);
            }
            double footGround = Capital.city(s, s.x() + Math.cos(angle) * foot, s.z() + Math.sin(angle) * foot);
            if (r > rr) {
                // below the road on the drop side: the rock falls away from its parapet, down to the city
                rock = Math.max(footGround, Math.min(rock, h - 1 - (r - rr - RAMP_HALF) * 2.2));
            } else {
                rock = Math.max(rock, h + 3);                                      // above it on the cliff side
            }
        }
        return new Capital.Column(Capital.Kind.CRAG, (int) Math.round(Math.min(Capital.TOP - 1, rock)), 0, 0, 0, 0, 0, r, angle, river);
    }

    // --- drawing ------------------------------------------------------------------------------------------------------

    /** Draws a column of the crag or the ramp. */
    static void drawCrag(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        int top = c.ground();
        for (int y = Capital.WATER - 6; y <= top; y++) {
            set(level, box, at.set(x, y, z), rockBlock(s, x, y, z));
        }
        if (c.kind() == Capital.Kind.RAMP) {
            double frac = c.edge();
            boolean parapet = c.party() > 1.9 && rampU(s, c.angle()) > 0.08;      // the drop side's edge, once there is a drop
            BlockState paving = ((x * 3 + z) % 5 == 0 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState();
            set(level, box, at.set(x, top, z), CapitalHouses.weather(s, paving.getBlock(), x, top, z, false).defaultBlockState());
            for (int y = top + 1; y <= top + 5; y++) {
                set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
            }
            if (parapet) {
                set(level, box, at.set(x, top + 1, z), CapitalDraw.along(Blocks.STONE_BRICK_WALL.defaultBlockState(), CapitalDraw.tangent(c.angle())));
            } else if (frac >= 0.5) {
                set(level, box, at.set(x, top + 1, z), Blocks.STONE_BRICK_SLAB.defaultBlockState());
            } else {
                // up the last of the road, the petitioners: fallen where the arrows and the crush left them
                long h = Math.floorMod(Town.hash(s.seed(), x, z, 409), 100L);
                if (rampU(s, c.angle()) > 0.7 && h < 12) {
                    net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(h * 7919 + x);
                    Markers.remains(level, at.set(x, top + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));
                }
            }
            return;
        }
        // the rock's face: grass and moss where it lies flat enough, a shrub, vines down the cliffs
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 352), 1000L);
        double cover = Town.noise(s.seed(), x, z, 5.0, 353);
        if (cover < 0.4) {
            set(level, box, at.set(x, top, z), (cover < 0.25 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK).defaultBlockState());
            if (r < 90) {
                set(level, box, at.set(x, top + 1, z), (r < 30 ? Blocks.FERN : r < 60 ? Blocks.SHORT_GRASS : Blocks.SWEET_BERRY_BUSH).defaultBlockState());
            }
        }
        for (int y = top + 1; y <= Capital.TOP + 4; y++) {
            set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    static BlockState rockBlock(Capital.Site s, int x, int y, int z) {
        double v = Town.noise(s.seed(), x + y * 5, z - y * 3, 4.0, 354);
        Block b = v < 0.35 ? Blocks.STONE : v < 0.55 ? Blocks.ANDESITE : v < 0.7 ? Blocks.TUFF : v < 0.85 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE;
        return b.defaultBlockState();
    }

    /** The castle's own axes at ({@code x}, {@code z}): forward to the gate, across; from its middle. */
    static double[] local(Capital.Site s, double x, double z) {
        double g = s.gate(0) + Math.PI;
        double dx = x + 0.5 - s.x();
        double dz = z + 0.5 - s.z();
        double f = dx * Math.cos(g) + dz * Math.sin(g);
        double l = -dx * Math.sin(g) + dz * Math.cos(g);
        return new double[] {f, l};
    }

    /** The way out (forward) as a direction of the world, the nearest of the four. */
    static Direction forward(Capital.Site s) {
        double g = s.gate(0) + Math.PI;
        return Direction.getApproximateNearest(Math.cos(g), 0, Math.sin(g));
    }

    /** Draws a column of the castle's top. */
    static void draw(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        int top = Capital.TOP;
        for (int y = Capital.WATER - 6; y < top; y++) {
            set(level, box, at.set(x, y, z), rockBlock(s, x, y, z));
        }
        double[] fl = local(s, x, z);
        double f = fl[0];
        double l = fl[1];
        double r = c.r();
        double angle = c.angle();
        RandomSource random = RandomSource.create(Town.hash(s.seed(), x, z, 355));
        double gateAngle = s.gate(0) + Math.PI;
        // the towers on the wall
        for (int k = 0; k < TOWERS; k++) {
            double ta = gateAngle + Math.PI / TOWERS + k * 2 * Math.PI / TOWERS;
            double tr = CURTAIN + CURTAIN_T / 2.0;
            double tx = s.x() + Math.cos(ta) * tr;
            double tz = s.z() + Math.sin(ta) * tr;
            double d = Math.hypot(x + 0.5 - tx, z + 0.5 - tz);
            if (d < TOWER_R) {
                tower(level, s, x, z, d, top, top + CURTAIN_H + 7, box, at, k, tx, tz);
                return;
            }
        }
        // the gatehouse's two towers
        for (int side = -1; side <= 1; side += 2) {
            double tx = s.x() + Math.cos(gateAngle) * (CURTAIN + 1) - Math.sin(gateAngle) * side * 7;
            double tz = s.z() + Math.sin(gateAngle) * (CURTAIN + 1) + Math.cos(gateAngle) * side * 7;
            double d = Math.hypot(x + 0.5 - tx, z + 0.5 - tz);
            if (d < 4.2) {
                tower(level, s, x, z, d, top, top + CURTAIN_H + 9, box, at, 20 + side, tx, tz);
                return;
            }
        }
        // the plateau's edge outside the wall: a rim of rock; before the gate, the ones who came with their petition
        if (r >= CURTAIN + CURTAIN_T) {
            set(level, box, at.set(x, top, z), rockBlock(s, x, top, z));
            clear(level, box, at, x, z, top + 1, top + 4);
            if (f > 0 && Math.abs(l) < 7 && Math.floorMod(Town.hash(s.seed(), x, z, 408), 100L) < 22) {
                Markers.remains(level, at.set(x, top + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER));
            }
            return;
        }
        // the curtain wall, and the gate through it
        if (r >= CURTAIN && r < CURTAIN + CURTAIN_T) {
            boolean gate = Math.abs(l) < 2.6 && f > 0;
            curtain(level, s, x, z, r, top, gate, box, at, random);
            return;
        }
        // the keep
        if (Math.abs(f - KEEP_F) <= KEEP_HALF + 0.5 && Math.abs(l) <= KEEP_HALF + 0.5) {
            keep(level, s, x, z, f - KEEP_F, l, top, box, at, random);
            return;
        }
        // the keep's corner turrets
        for (int cf = -1; cf <= 1; cf += 2) {
            for (int cl = -1; cl <= 1; cl += 2) {
                double d = Math.hypot(f - (KEEP_F + cf * KEEP_HALF), l - cl * KEEP_HALF);
                if (d < 3.4) {
                    turret(level, s, x, z, d, top, box, at);
                    return;
                }
            }
        }
        // the great hall
        if (f >= -4 && f <= 20 && l >= -30 && l <= -16) {
            CapitalCastleRooms.greatHall(level, s, x, z, f, l, top, box, at, random);
            return;
        }
        // the chapel
        if (f >= 2 && f <= 16 && l >= 17 && l <= 26) {
            chapel(level, s, x, z, f, l, top, box, at, random);
            return;
        }
        // barracks, kitchen and stables along the wall behind
        double off = Math.abs(Capital.wrap(angle - gateAngle));
        if (r >= CURTAIN - 7 && off > 1.65 && off < 2.55) {
            leanTo(level, s, x, z, r, top, box, at, random);
            return;
        }
        court(level, s, x, z, f, l, r, top, box, at, random);
    }

    private static void clear(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos at, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            set(level, box, at.set(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockState stone(Capital.Site s, int x, int y, int z) {
        return CapitalHouses.weather(s, Blocks.STONE_BRICKS, x, y, z, false).defaultBlockState();
    }

    /** A round tower {@code d} from its middle: a shell with floors, a cone of slate. */
    private static void tower(WorldGenLevel level, Capital.Site s, int x, int z, double d, int top, int height, BoundingBox box,
            BlockPos.MutableBlockPos at, int k, double tx, double tz) {
        double radius = k >= 19 ? 4.2 : TOWER_R;
        boolean shell = d > radius - 1.6;
        Tower t = Tower.of(s, tx, tz, x, z, radius);
        int walk = top + CURTAIN_H;
        for (int y = top; y <= height; y++) {
            int h = y - top;
            BlockState state;
            if (shell) {
                boolean slit = h % 5 == 3 && Math.floorMod(x * 3 + z * 5, 4) == 0;
                // a door from the court at its foot; onto the wall's walk either side
                boolean door = t.inward() && h >= 1 && h <= 3;
                boolean walkway = t.onWall(CURTAIN, CURTAIN + CURTAIN_T) && y > walk && y <= walk + 2;
                state = slit || door || walkway ? Blocks.AIR.defaultBlockState() : stone(s, x, y, z);
            } else if (t.ladder() && h >= 1 && y <= height - 1) {
                state = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, t.ladderFacing());
            } else {
                // its floors: one level with the wall's walk, one under it, one over
                state = h == 0 || (h + 1) % 6 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        // a cone of slate: higher toward the middle
        int cone = (int) Math.floor((radius - d) * 1.9);
        int outer = (int) Math.floor(Math.max(0, radius - d - 1) * 1.9);
        BlockState slate = Blocks.DEEPSLATE_TILES.defaultBlockState();
        for (int y = height + 1; y <= height + 1 + cone; y++) {
            set(level, box, at.set(x, y, z), y > height + 1 + outer ? slate : Blocks.AIR.defaultBlockState());
        }
        if (d < 0.8) {
            for (int y = height + 2 + cone; y <= height + 4 + cone; y++) {
                set(level, box, at.set(x, y, z), (y == height + 4 + cone ? Blocks.LIGHTNING_ROD.waxed().weathered() : Blocks.DEEPSLATE_TILE_WALL).defaultBlockState());
            }
        }
    }

    /** The curtain wall: solid to its walk, a parapet on the outside; the gate's passage through it, barred and blocked. */
    private static void curtain(WorldGenLevel level, Capital.Site s, int x, int z, double r, int top, boolean gate, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        int walk = top + CURTAIN_H;
        boolean outer = r >= CURTAIN + CURTAIN_T - 1;
        for (int y = top; y <= walk; y++) {
            if (gate && y > top && y <= top + 6) {
                // the portcullis, jammed a little over half way down: under it, a way in
                boolean portcullis = r >= CURTAIN + 1 && r < CURTAIN + 2 && y > top + 3;
                set(level, box, at.set(x, y, z), portcullis ? CapitalDraw.along(Blocks.IRON_BARS.defaultBlockState(), CapitalDraw.tangent(s.gate(0) + Math.PI))
                        : Blocks.AIR.defaultBlockState());
                continue;
            }
            set(level, box, at.set(x, y, z), stone(s, x, y, z));
        }
        if (outer) {
            boolean merlon = Math.floorMod(x + z, 2) == 0;
            set(level, box, at.set(x, walk + 1, z), merlon ? stone(s, x, walk + 1, z) : Blocks.AIR.defaultBlockState());
            set(level, box, at.set(x, walk + 2, z), Blocks.AIR.defaultBlockState());
        } else {
            clear(level, box, at, x, z, walk + 1, walk + 3);
        }
    }

    /** The keep at ({@code kf}, {@code kl}) from its middle: walls two thick, seven storeys, a hipped roof and a spire. */
    private static void keep(WorldGenLevel level, Capital.Site s, int x, int z, double kf, double kl, int top, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        double edge = Math.min(KEEP_HALF + 0.5 - Math.abs(kf), KEEP_HALF + 0.5 - Math.abs(kl));
        boolean wall = edge < 2;
        boolean outerFace = edge < 1;
        int roofBase = top + KEEP_STOREY * KEEP_STOREYS;
        // the way in: a door on the court's side (forward), two wide
        boolean door = kf > KEEP_HALF - 1.5 && Math.abs(kl) < 1.6;
        int ladderF = KEEP_HALF - 3;
        int ladderL = -(KEEP_HALF - 2);
        boolean ladder = Math.round(kf) == ladderF && Math.round(kl) == ladderL;
        int hatch = top + KEEP_STOREY * (KEEP_STOREYS - 1);
        for (int y = top; y <= roofBase; y++) {
            int inStorey = (y - top) % KEEP_STOREY;
            BlockState state;
            if (wall) {
                // windows through the whole wall (the review: a niche in its outer face, the storeys dark), every fourth
                // block along each face
                double alongFace = Math.abs(kf) > Math.abs(kl) ? kl : kf;
                boolean window = inStorey >= 2 && inStorey <= 3 && y > top + KEEP_STOREY
                        && Math.floorMod((int) Math.floor(alongFace), 4) == 1 && Math.abs(Math.abs(kf) - Math.abs(kl)) > 2;
                if (door && y > top && y <= top + 3) {
                    state = Blocks.AIR.defaultBlockState();
                } else if (window && y > top + KEEP_STOREY * (KEEP_STOREYS - 1)) {
                    // the king's room: its windows boarded from inside, a crack left here and there
                    state = Math.floorMod(Town.hash(s.seed(), x, y, z), 100L) < 18 ? Blocks.AIR.defaultBlockState()
                            : Blocks.SPRUCE_PLANKS.defaultBlockState();
                } else if (window) {
                    state = Blocks.AIR.defaultBlockState();
                } else if (!outerFace && CapitalCastleRooms.panel(inStorey) != null && y > top) {
                    state = CapitalCastleRooms.panel(inStorey);                 // oak panelling, a block high, round every room
                } else {
                    state = (Math.abs(Math.abs(kf) - Math.abs(kl)) < 0.6 && outerFace ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : stone(s, x, y, z));
                }
            } else if (inStorey == 0 && ladder && y == hatch) {
                // the hatch into the king's room, shut over the ladder (it opens from below)
                state = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP);
            } else if (inStorey == 0) {
                state = ladder && y > top && y < hatch ? ladderInKeep(s)
                        : (y == top ? Blocks.POLISHED_ANDESITE : Blocks.DARK_OAK_PLANKS).defaultBlockState();
            } else if (ladder && y < hatch) {
                state = ladderInKeep(s);
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (!wall) {
            CapitalStory.keepRoom(level, s, x, z, kf, kl, top, box, at, random, ladder);
            if (!ladder) {
                CapitalCastleRooms.keepDress(level, s, x, z, kf, kl, edge, top, box, at);
            }
        }
        // the parapet round the top, then the roof: steep, hipped, to a spire in the middle
        if (outerFace) {
            boolean merlon = Math.floorMod(x + z, 2) == 0;
            set(level, box, at.set(x, roofBase + 1, z), stone(s, x, roofBase + 1, z));
            set(level, box, at.set(x, roofBase + 2, z), merlon ? stone(s, x, roofBase + 2, z) : Blocks.AIR.defaultBlockState());
            return;
        }
        if (wall) {
            set(level, box, at.set(x, roofBase + 1, z), Blocks.STONE_BRICK_SLAB.defaultBlockState());
            return;
        }
        double in = Math.min(KEEP_HALF - 1.5 - Math.abs(kf), KEEP_HALF - 1.5 - Math.abs(kl));
        int rise = (int) Math.floor(Math.max(0, in) * 1.7);
        int outer = (int) Math.floor(Math.max(0, in - 1) * 1.7);
        for (int y = roofBase + 1; y < roofBase + 1 + rise; y++) {
            set(level, box, at.set(x, y, z), (y > roofBase + 1 + outer ? Blocks.DEEPSLATE_TILES : Blocks.AIR).defaultBlockState());
        }
        set(level, box, at.set(x, roofBase + 1 + rise, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
        if (Math.abs(kf) < 1.1 && Math.abs(kl) < 1.1) {
            for (int y = roofBase + 2 + rise; y <= roofBase + 22 + rise; y++) {
                set(level, box, at.set(x, y, z), (y < roofBase + 16 + rise ? Blocks.DEEPSLATE_TILES : Blocks.DEEPSLATE_TILE_WALL).defaultBlockState());
            }
            set(level, box, at.set(x, roofBase + 23 + rise, z), Blocks.LIGHTNING_ROD.waxed().weathered().defaultBlockState());
        }
    }

    /** The keep's ladder, up its inside wall on the -across side: facing away from it. */
    private static BlockState ladderInKeep(Capital.Site s) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, sideways(s, 1));
    }

    /** A turret at the keep's corner: round, higher than the keep's walls, a cone. */
    private static void turret(WorldGenLevel level, Capital.Site s, int x, int z, double d, int top, BoundingBox box, BlockPos.MutableBlockPos at) {
        int height = top + KEEP_STOREY * KEEP_STOREYS + 8;
        for (int y = top; y <= height; y++) {
            set(level, box, at.set(x, y, z), d > 2.0 || y < top + 10 ? stone(s, x, y, z) : Blocks.AIR.defaultBlockState());
        }
        int cone = (int) Math.floor((3.4 - d) * 2.4);
        int outer = (int) Math.floor(Math.max(0, 2.4 - d) * 2.4);
        for (int y = height + 1; y <= height + 1 + cone; y++) {
            set(level, box, at.set(x, y, z), y > height + 1 + outer ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    /** The world's direction for across the castle ({@code sign} +1: toward +l). */
    static Direction sideways(Capital.Site s, int sign) {
        double g = s.gate(0) + Math.PI;
        return Direction.getApproximateNearest(-Math.sin(g) * sign, 0, Math.cos(g) * sign);
    }

    static BlockState glass(Capital.Site s, int x, int y, int z) {
        long r = Math.floorMod(Town.hash(s.seed(), x * 13L + y, z, 356), 100L);
        if (r < 45) {
            return Blocks.AIR.defaultBlockState();
        }
        Block pane = r < 60 ? Blocks.STAINED_GLASS_PANE.red() : r < 75 ? Blocks.STAINED_GLASS_PANE.blue() : r < 85 ? Blocks.STAINED_GLASS_PANE.yellow()
                : Blocks.STAINED_GLASS_PANE.gray();
        return CapitalDraw.along(pane.defaultBlockState(), forward(s).getAxis());    // in the long sides, along the castle's axis
    }

    /** The chapel: stained glass, a gable, a bell cote over its door. */
    private static void chapel(WorldGenLevel level, Capital.Site s, int x, int z, double f, double l, int top, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        double across = Math.min(l - 17, 26 - l);
        double along = Math.min(f - 2, 16 - f);
        boolean wall = across < 1 || along < 1;
        int height = top + 9;
        for (int y = top; y <= height; y++) {
            BlockState state;
            if (wall) {
                boolean window = across < 1 && y >= top + 3 && y <= top + 7 && Math.floorMod((int) Math.round(f), 3) == 0;
                boolean door = along < 1 && f > 10 && Math.abs(l - 21.5) < 1.1 && y > top && y <= top + 3;
                // and the east end's tall window over the altar
                boolean east = along < 1 && f < 10 && Math.abs(l - 21.5) < 1.6 && y >= top + 3 && y <= top + 7;
                state = window ? glass(s, x, y, z) : east ? CapitalDraw.along(glass(s, x, y, z), sideways(s, 1).getAxis())
                        : door ? Blocks.AIR.defaultBlockState() : stone(s, x, y, z);
            } else if (y == top) {
                state = (Math.floorMod(x + z, 2) == 0 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (!wall) {
            CapitalCastleRooms.chapelFloor(level, s, x, z, f, l - 21.5, top, box, at, random);
        }
        int rise = (int) Math.floor(Math.max(0, across) * 1.2);
        int outer = (int) Math.floor(Math.max(0, across - 1) * 1.2);
        int y = height + 1 + rise;
        for (int k = height + 1; k < y; k++) {
            set(level, box, at.set(x, k, z), along < 1 ? stone(s, x, k, z) : k > height + 1 + outer ? Blocks.DEEPSLATE_TILES.defaultBlockState()
                    : Blocks.AIR.defaultBlockState());
        }
        set(level, box, at.set(x, y, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
        if (f > 14.5 && Math.abs(l - 21.5) < 1.1) {                       // the bell cote over the door
            for (int k = y + 1; k <= y + 5; k++) {
                set(level, box, at.set(x, k, z), (k == y + 3 ? Blocks.BELL : k == y + 5 ? Blocks.DEEPSLATE_TILES : Blocks.AIR).defaultBlockState());
            }
        }
    }

    /** A lean-to along the wall: barracks, kitchen, stables; its roof sloping down to the court. */
    private static void leanTo(WorldGenLevel level, Capital.Site s, int x, int z, double r, int top, BoundingBox box, BlockPos.MutableBlockPos at,
            RandomSource random) {
        double in = r - (CURTAIN - 7);                                    // 0 at the court side, 7 at the wall
        boolean front = in < 1;
        int height = top + 4 + (int) Math.floor(in * 0.6);
        for (int y = top; y <= height; y++) {
            BlockState state;
            if (front && y <= top + 3) {
                boolean opening = y > top && y <= top + 2 && Math.floorMod(x + z, 3) == 0;
                state = opening ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
            } else if (y == top) {
                state = Blocks.COARSE_DIRT.defaultBlockState();
            } else if (y == height) {
                state = Blocks.SPRUCE_PLANKS.defaultBlockState();
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            set(level, box, at.set(x, y, z), state);
        }
        if (front) {
            return;
        }
        CapitalCastleRooms.leanToInside(level, s, x, z, in, top, box, at, random);
    }

    /** The court: paving gone to grass, the well, the carts and barrels of what was kept from the city. */
    private static void court(WorldGenLevel level, Capital.Site s, int x, int z, double f, double l, double r, int top, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        double v = Town.noise(s.seed(), x, z, 5.0, 358);
        BlockState ground = v < 0.45 ? Blocks.STONE_BRICKS.defaultBlockState() : v < 0.6 ? Blocks.COBBLESTONE.defaultBlockState()
                : v < 0.8 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        set(level, box, at.set(x, top, z), ground);
        clear(level, box, at, x, z, top + 1, top + 4);
        // ladders up the curtain to its walk, here and there round the court
        if (r >= CURTAIN - 1) {
            for (double la : WALK_LADDERS) {
                if (r * Math.abs(Capital.wrap(c(s, x, z) - (s.gate(0) + Math.PI) - la)) < 0.5) {
                    Direction in = Direction.getApproximateNearest(s.x() - x - 0.5, 0, s.z() - z - 0.5);
                    for (int y = top + 1; y <= top + CURTAIN_H; y++) {
                        set(level, box, at.set(x, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, in));
                    }
                    return;
                }
            }
        }
        long h = Math.floorMod(Town.hash(s.seed(), x, z, 359), 1000L);
        double wf = 6;
        double wl = 4;
        if (Math.hypot(f - wf, l - wl) < 1.6) {                            // the well
            boolean rim = Math.hypot(f - wf, l - wl) > 0.7;
            set(level, box, at.set(x, top, z), rim ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.WATER.defaultBlockState());
            if (rim) {
                set(level, box, at.set(x, top + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
            return;
        }
        // the stores the court kept back: barrels, crates, hay, stacked against the hall
        boolean stores = f > -2 && f < 22 && l > -16 && l < -11;
        if (stores && h < 450) {
            Block b = h < 150 ? Blocks.BARREL : h < 250 ? Blocks.HAY_BLOCK : h < 350 ? dev.nookworks.registry.ModBlocks.CRATE.get() : Blocks.BARREL;
            set(level, box, at.set(x, top + 1, z), b.defaultBlockState());
            if (h < 120) {
                set(level, box, at.set(x, top + 2, z), Blocks.BARREL.defaultBlockState());
            }
            if (h < 25) {
                CapitalDraw.container(level, box, at.set(x, top + 1, z), Blocks.BARREL, "food", Town.hash(s.seed(), x, top, z));
            }
            return;
        }
        // behind the gate, the barricade: what they had, heaped against the portcullis
        if (f > CURTAIN - 7 && Math.abs(l) < 4.5 && Math.abs(l) > 0.6) {
            Block b = h < 300 ? dev.nookworks.registry.ModBlocks.CRATE.get() : h < 500 ? Blocks.BARREL : h < 700 ? Blocks.OAK_PLANKS : Blocks.COBBLESTONE;
            set(level, box, at.set(x, top + 1, z), b.defaultBlockState());
            if (h < 600 && f > CURTAIN - 4) {
                set(level, box, at.set(x, top + 2, z), (h < 300 ? Blocks.OAK_PLANKS : Blocks.COBBLESTONE).defaultBlockState());
            }
            return;
        }
        if (ground.is(Blocks.GRASS_BLOCK) && h < 160) {
            set(level, box, at.set(x, top + 1, z), (h < 60 ? Blocks.TALL_GRASS : h < 120 ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState());
        } else if (h > 994) {
            Markers.remains(level, at.set(x, top + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
        }
    }

    /** Where the ladders up the curtain are, by angle from the castle's gate. */
    static final double[] WALK_LADDERS = {0.5, -0.5, Math.PI - 0.35, Math.PI + 0.35};

    private static double c(Capital.Site s, int x, int z) {
        return Math.atan2(z + 0.5 - s.z(), x + 0.5 - s.x());
    }

    /**
     * A round tower's column in its own terms: {@code along} out from the capital's (or the castle's) middle through the
     * tower's, {@code side} across that, from the tower's middle.
     */
    record Tower(double along, double side, double ux, double uz, double radius, double tr) {
        static Tower of(Capital.Site s, double tx, double tz, int x, int z, double radius) {
            double ox = tx - s.x();
            double oz = tz - s.z();
            double len = Math.hypot(ox, oz);
            double ux = ox / len;
            double uz = oz / len;
            double dx = x + 0.5 - tx;
            double dz = z + 0.5 - tz;
            return new Tower(dx * ux + dz * uz, -dx * uz + dz * ux, ux, uz, radius, len);
        }

        /** On the side toward the middle, in line with the tower's own: its door. */
        boolean inward() {
            return this.along < 0 && Math.abs(this.side) < 1.2;
        }

        /** Where the wall from {@code from} to {@code to} out of the middle runs through the tower's shell. */
        boolean onWall(double from, double to) {
            double r = this.tr + this.along;
            return r >= from && r < to && Math.abs(this.side) > 1;
        }

        /** The ladder's column: inside, against the shell on the far side from the door. */
        boolean ladder() {
            return this.along > this.radius - 2.6 && this.along <= this.radius - 1.6 && Math.abs(this.side) < 0.6;
        }

        Direction ladderFacing() {
            return Direction.getApproximateNearest(-this.ux, 0, -this.uz);
        }
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    private CapitalCastle() {}
}
