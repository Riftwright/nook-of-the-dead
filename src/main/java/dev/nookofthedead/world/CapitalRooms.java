package dev.nookofthedead.world;

import java.util.function.Supplier;

import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The rooms of the capital's houses ({@link CapitalHouses}): what each storey was - a shop or a workshop on the street, a
 * kitchen, a living room, bedrooms, a store under the roof - and what was left in it. A table with its chairs round it and
 * what was on it; beds, shelves and cupboards against the walls the house shares with its neighbours (never against a
 * window or a door); a counter and the shelves behind it in the shops; a lantern over the table, long out; now and then a
 * diary left on the table or the floor rather than set out on a lectern (the user, in game: who writes a diary on a
 * lectern in the middle of all that, and leaves it lying open there twenty years?).
 * <p>
 * Every piece is a pure function of the world's seed and the columns round it, the columns beyond the chunk's window asked
 * of {@link Capital#column} - so a table across a chunk's edge has its chairs on both sides, and a bed its two halves.
 * Furniture is Nookworks': chairs, stools, desks, wall shelves, books lying and standing, bottles, trays, lanterns gone out.
 */
final class CapitalRooms {
    /** What a storey of a house was. */
    enum Room { SHOP, WORKSHOP, KITCHEN, LIVING, BEDROOM, STORE }

    /** The trade of a house's shop or workshop. */
    enum Trade { BAKER, APOTHECARY, GROCER, SMITH, CARPENTER, TAILOR, SCRIBE }

    /** One column's room in context: the house, the storey, the floor's height; how to look at the columns round it. */
    record Here(Capital.Site s, Capital.Column[][] w, int a, int b, int x, int z, CapitalHouses.House house, int storey, int floor,
            int[] ladder) {
        Capital.Column col(int dx, int dz) {
            int na = this.a + dx;
            int nb = this.b + dz;
            if (na >= 0 && nb >= 0 && na < this.w.length && nb < this.w.length && this.w[na][nb] != null) {
                return this.w[na][nb];
            }
            return Capital.column(this.s.seed(), this.x + dx, this.z + dz);
        }
    }

    static Room room(CapitalHouses.House house, int storey) {
        long pick = Math.floorMod(Town.hash(house.hash(), storey, 0, 420), 100L);
        if (storey == 0) {
            return pick < 45 ? Room.SHOP : pick < 75 ? Room.WORKSHOP : Room.KITCHEN;
        }
        if (storey == house.storeys() - 1 && house.storeys() > 2) {
            return pick < 45 ? Room.STORE : Room.BEDROOM;
        }
        if (storey == 1) {
            return pick < 55 ? Room.KITCHEN : Room.LIVING;
        }
        return pick < 60 ? Room.BEDROOM : Room.LIVING;
    }

    static Trade trade(CapitalHouses.House house) {
        return Trade.values()[(int) Math.floorMod(Town.hash(house.hash(), 7, 7, 421), (long) Trade.values().length)];
    }

    /** The wood of a house's furniture. */
    private static String wood(CapitalHouses.House house) {
        return switch ((int) Math.floorMod(house.hash() >> 29, 4L)) {
            case 0 -> "spruce";
            case 1 -> "dark_oak";
            case 2 -> "oak";
            default -> "birch";
        };
    }

    // --- where things go ------------------------------------------------------------------------------------------

    private static boolean ours(Capital.Column n, CapitalHouses.House house) {
        return n != null && n.kind() == Capital.Kind.BUILDING && (n.block() * 0x9E3779B97F4A7C15L ^ n.parcel()) == house.key();
    }

    /** How far into the room column {@code n} is at the floor's height: 0 for a wall, else its distance in from the walls. */
    private static double inside(Here h, Capital.Column n) {
        if (!ours(n, h.house())) {
            return 0;
        }
        double e = CapitalHouses.edgeAt(n, h.house(), h.floor() + 1);
        return e < 1 || n.party() < 1 ? 0 : Math.min(e, n.party());
    }

    private static boolean ladderAt(Here h, int dx, int dz) {
        int[] l = h.ladder();
        return l != null && l[1] == h.z() + dz && (l[0] == h.x() + dx || l[0] + 1 == h.x() + dx);
    }

    /** Deep in the room at ({@code dx}, {@code dz}) from here: two clear of every wall, not the ladder - for a table, a chair. */
    private static boolean deep(Here h, int dx, int dz) {
        return inside(h, h.col(dx, dz)) >= 2 && !ladderAt(h, dx, dz);
    }

    /**
     * The one wall beside column ({@code dx}, {@code dz}) from here, toward it, if it is a wall the house shares with the
     * next house (no window, no door in it) and the only wall there; null otherwise.
     */
    private static Direction sharedWall(Here h, int dx, int dz) {
        if (inside(h, h.col(dx, dz)) < 1 || ladderAt(h, dx, dz)) {
            return null;
        }
        Direction found = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (inside(h, h.col(dx + d.getStepX(), dz + d.getStepZ())) >= 1) {
                continue;
            }
            if (found != null) {
                return null;                                                   // a corner
            }
            found = d;
        }
        if (found == null) {
            return null;
        }
        Capital.Column beyond = h.col(dx + 2 * found.getStepX(), dz + 2 * found.getStepZ());
        Capital.Column wall = h.col(dx + found.getStepX(), dz + found.getStepZ());
        boolean party = wall != null && wall.kind() == Capital.Kind.BUILDING && beyond != null && beyond.kind() == Capital.Kind.BUILDING
                && !(ours(beyond, h.house()) && inside(h, beyond) >= 1);
        return party ? found : null;
    }

    /** Any wall beside column ({@code dx}, {@code dz}) - a window's or a door's too: for what stands low against it. */
    private static Direction anyWall(Here h, int dx, int dz) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (inside(h, h.col(dx + d.getStepX(), dz + d.getStepZ())) < 1) {
                return d;
            }
        }
        return null;
    }

    private static boolean tables(Room room) {
        return room == Room.KITCHEN || room == Room.LIVING || room == Room.WORKSHOP || room == Room.SHOP;
    }

    /**
     * The table of the five-by-five cell column ({@code x}, {@code z}) is in, on this floor: {x, z} of its west half (it is
     * two long, east-west), or null where the cell has none or there is no room for it and two chairs.
     */
    private static int[] table(Here h, int cellX, int cellZ) {
        long t = Town.hash(h.s().seed() ^ h.floor(), cellX, cellZ, 422);
        if (Math.floorMod(t, 100L) >= 70) {
            return null;
        }
        int ax = cellX * 5 + 1 + (int) Math.floorMod(t >> 8, 3L);
        int az = cellZ * 5 + 1 + (int) Math.floorMod(t >> 12, 3L);
        int dx = ax - h.x();
        int dz = az - h.z();
        if (!deep(h, dx, dz) || !deep(h, dx + 1, dz)) {
            return null;
        }
        return new int[] {ax, az};
    }

    /** What stands on the floor at this column. */
    private enum Piece { NONE, TABLE, CHAIR, BED_HEAD, BED_FOOT, BOOKS_TALL, CUPBOARD, COUNTER, HEARTH, STACK, LOW }

    private record Placed(Piece piece, Direction facing, int[] table) {}

    private static final Placed NOTHING = new Placed(Piece.NONE, null, null);

    private static Placed piece(Here h, Room room) {
        // the table and its chairs: this cell's table, or the west cell's (whose chairs reach one into this)
        if (tables(room)) {
            int cx = Math.floorDiv(h.x(), 5);
            int cz = Math.floorDiv(h.z(), 5);
            for (int k = 0; k <= 1; k++) {
                int[] t = table(h, cx - k, cz);
                if (t == null) {
                    continue;
                }
                int tx = t[0] - h.x();
                int tz = t[1] - h.z();
                if (tz == 0 && (tx == 0 || tx == -1)) {
                    return new Placed(Piece.TABLE, tx == 0 ? Direction.EAST : Direction.WEST, t);
                }
                // its chairs: one each side, one at each end, facing it
                if (tz == 1 && tx == 0 || tz == -1 && tx == -1 || tz == 0 && (tx == 1 || tx == -2)) {
                    if (deep(h, 0, 0)) {
                        Direction facing = tz == 1 ? Direction.SOUTH : tz == -1 ? Direction.NORTH : tx == 1 ? Direction.EAST : Direction.WEST;
                        return new Placed(Piece.CHAIR, facing, t);
                    }
                }
            }
        }
        // a bed: its head against a shared wall, its foot a block into the room
        if (room == Room.BEDROOM) {
            Direction wall = sharedWall(h, 0, 0);
            if (wall != null && bedHead(h, 0, 0, wall)) {
                return new Placed(Piece.BED_HEAD, wall, null);
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Direction headWall = sharedWall(h, d.getStepX(), d.getStepZ());
                if (headWall == d && bedHead(h, d.getStepX(), d.getStepZ(), d)) {
                    return new Placed(Piece.BED_FOOT, d, null);
                }
            }
        }
        Direction wall = sharedWall(h, 0, 0);
        long r = Math.floorMod(Town.hash(h.s().seed() ^ h.floor(), h.x(), h.z(), 423), 1000L);
        if (wall != null) {
            switch (room) {
                case SHOP -> {
                    return new Placed(r < 450 ? Piece.COUNTER : r < 750 ? Piece.STACK : Piece.CUPBOARD, wall, null);
                }
                case WORKSHOP -> {
                    return new Placed(r < 350 ? Piece.COUNTER : r < 650 ? Piece.LOW : r < 850 ? Piece.STACK : Piece.NONE, wall, null);
                }
                case KITCHEN -> {
                    return new Placed(r < 200 ? Piece.HEARTH : r < 520 ? Piece.COUNTER : r < 700 ? Piece.CUPBOARD : r < 850 ? Piece.LOW
                            : Piece.NONE, wall, null);
                }
                case LIVING -> {
                    return new Placed(r < 380 ? Piece.BOOKS_TALL : r < 560 ? Piece.CUPBOARD : r < 720 ? Piece.LOW : Piece.NONE, wall, null);
                }
                case BEDROOM -> {
                    return new Placed(r < 250 ? Piece.CUPBOARD : r < 450 ? Piece.LOW : r < 560 ? Piece.BOOKS_TALL : Piece.NONE, wall, null);
                }
                case STORE -> {
                    return new Placed(r < 700 ? Piece.STACK : Piece.LOW, wall, null);
                }
            }
        }
        Direction low = anyWall(h, 0, 0);
        if (low != null && inside(h, h.col(0, 0)) >= 1 && !ladderAt(h, 0, 0) && r < 160 && room != Room.SHOP) {
            return new Placed(Piece.LOW, low, null);                           // under a window: a chest, a barrel, a stool
        }
        return NOTHING;
    }

    /** Whether a bed's head is at ({@code dx}, {@code dz}), against wall {@code wall}: now and then, where its foot has room. */
    private static boolean bedHead(Here h, int dx, int dz, Direction wall) {
        long b = Town.hash(h.s().seed() ^ h.floor(), h.x() + dx, h.z() + dz, 424);
        if (Math.floorMod(b, 6L) != 0) {
            return false;
        }
        int fx = dx - wall.getStepX();
        int fz = dz - wall.getStepZ();
        return inside(h, h.col(fx, fz)) >= 1 && !ladderAt(h, fx, fz) && anyWallCount(h, fx, fz) <= 1;
    }

    private static int anyWallCount(Here h, int dx, int dz) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (inside(h, h.col(dx + d.getStepX(), dz + d.getStepZ())) < 1) {
                n++;
            }
        }
        return n;
    }

    // --- drawing ------------------------------------------------------------------------------------------------------

    /**
     * Furnishes column ({@code a}, {@code b}) of a house at height {@code y} (inside a room: not its walls, floors or
     * ladder): what stands on the floor at the storey's first block, what is on it or hangs on the wall at the second,
     * what hangs from the ceiling at the third.
     */
    static void furnish(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c,
            CapitalHouses.House house, int[] ladder, int x, int y, int z, boolean burnt, BoundingBox box, BlockPos.MutableBlockPos at) {
        int storey = (y - house.level()) / CapitalHouses.STOREY;
        int inStorey = (y - house.level()) % CapitalHouses.STOREY;
        int floor = house.level() + storey * CapitalHouses.STOREY;
        Here h = new Here(s, w, a, b, x, z, house, storey, floor, ladder);
        RandomSource random = RandomSource.create(Town.hash(s.seed(), x * 31L + y, z, 425));
        // the column's roll on this floor: the same at each of its heights, so a piece's two blocks agree
        long r = Math.floorMod(Town.hash(s.seed() ^ floor, x, z, 349), 1000L);
        if (burnt) {
            charred(level, h, inStorey, r, y, box, at, random);
            return;
        }
        Room room = room(house, storey);
        Placed p = piece(h, room);
        if (inStorey == 1) {
            ground(level, h, room, p, r, y, box, at, random);
        } else if (inStorey == 2) {
            upon(level, h, room, p, r, y, box, at, random);
        } else if (inStorey == 3) {
            Direction shelf = shelf(h, room, p, r);
            if (shelf != null) {
                shelfTop(level, h, room, r, new BlockPos(x, y, z), box, random, shelf.getOpposite());
            } else if (p.piece() == Piece.TABLE && p.facing() == Direction.EAST && Math.floorMod(Town.hash(s.seed(), x, y, 426), 3L) == 0) {
                set(level, box, at.set(x, y, z), ModBlocks.UNLIT_LANTERN.get().defaultBlockState().setValue(BlockStateProperties.HANGING, true));
            } else if (r < 18 && anyWall(h, 0, 0) != null) {
                set(level, box, at.set(x, y, z), Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    /** At the floor: the piece itself. */
    private static void ground(WorldGenLevel level, Here h, Room room, Placed p, long r, int y, BoundingBox box, BlockPos.MutableBlockPos at,
            RandomSource random) {
        String wood = wood(h.house());
        int x = h.x();
        int z = h.z();
        Direction out = p.facing() == null ? null : p.facing().getOpposite();       // from the wall into the room
        switch (p.piece()) {
            case TABLE -> set(level, box, at.set(x, y, z), ModWoods.DESKS.get(wood).get().defaultBlockState()
                    .setValue(dev.nookworks.furniture.DeskBlock.joined(p.facing()), true));
            case CHAIR -> {
                // a chair at the table, or knocked over (left lying on its back: a stool's height, turned)
                boolean over = r < 120;
                Block chair = over || room == Room.WORKSHOP || room == Room.SHOP ? ModWoods.STOOLS.get(wood).get() : ModWoods.CHAIRS.get(wood).get();
                set(level, box, at.set(x, y, z), chair.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                        over ? p.facing().getClockWise() : p.facing()));
            }
            case BED_HEAD, BED_FOOT -> {
                Block bed = switch ((int) Math.floorMod(h.house().hash() >> 33, 4L)) {
                    case 0 -> Blocks.BED.red();
                    case 1 -> Blocks.BED.brown();
                    case 2 -> Blocks.BED.white();
                    default -> Blocks.BED.lightGray();
                };
                set(level, box, at.set(x, y, z), bed.defaultBlockState().setValue(BedBlock.FACING, p.facing())
                        .setValue(BedBlock.PART, p.piece() == Piece.BED_HEAD ? BedPart.HEAD : BedPart.FOOT));
            }
            case BOOKS_TALL -> {
                if (r % 3 == 0) {
                    chiseled(level, box, at.set(x, y, z).immutable(), out, random);
                } else {
                    set(level, box, at.set(x, y, z), Blocks.BOOKSHELF.defaultBlockState());
                }
            }
            case CUPBOARD -> {
                if (r % 2 == 0) {
                    CapitalDraw.container(level, box, at.set(x, y, z), Blocks.CHEST, h.storey() == 0 ? "general" : "supplies",
                            Town.hash(h.s().seed(), x, y, z));
                    orient(level, box, at.set(x, y, z), out);
                } else {
                    CapitalDraw.container(level, box, at.set(x, y, z), Blocks.BARREL, room == Room.KITCHEN ? "food" : "general",
                            Town.hash(h.s().seed(), x, y, z));
                }
            }
            case COUNTER -> set(level, box, at.set(x, y, z), ModWoods.DESKS.get(wood).get().defaultBlockState());
            case HEARTH -> set(level, box, at.set(x, y, z), (r % 3 == 0 ? Blocks.SMOKER : Blocks.FURNACE).defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, out));
            case STACK -> {
                Block b = switch (room) {
                    case SHOP -> trade(h.house()) == Trade.BAKER || trade(h.house()) == Trade.GROCER ? Blocks.BARREL : ModBlocks.CRATE.get();
                    case STORE -> r % 4 == 0 ? Blocks.HAY_BLOCK : r % 4 == 1 ? ModBlocks.CRATE.get() : Blocks.BARREL;
                    default -> Blocks.BARREL;
                };
                if (r % 5 == 0) {
                    CapitalDraw.container(level, box, at.set(x, y, z), b, room == Room.STORE ? "supplies" : "general", Town.hash(h.s().seed(), x, y, z));
                } else {
                    set(level, box, at.set(x, y, z), b.defaultBlockState());
                }
            }
            case LOW -> {
                if (room == Room.WORKSHOP) {
                    Block tool = switch (trade(h.house())) {
                        case SMITH -> r % 2 == 0 ? Blocks.ANVIL : Blocks.GRINDSTONE;
                        case CARPENTER -> r % 2 == 0 ? Blocks.CRAFTING_TABLE : Blocks.STRIPPED_SPRUCE_LOG;
                        case TAILOR -> r % 2 == 0 ? Blocks.LOOM : ModBlocks.CRATE.get();
                        case SCRIBE -> Blocks.CARTOGRAPHY_TABLE;
                        default -> r % 2 == 0 ? Blocks.CRAFTING_TABLE : Blocks.BARREL;
                    };
                    BlockState state = tool.defaultBlockState();
                    if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                        state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, out.getClockWise());
                    }
                    set(level, box, at.set(x, y, z), state);
                } else if (r % 3 == 0) {
                    set(level, box, at.set(x, y, z), ModWoods.STOOLS.get(wood(h.house())).get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, out));
                } else if (r % 3 == 1) {
                    set(level, box, at.set(x, y, z), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
                } else {
                    CapitalDraw.container(level, box, at.set(x, y, z), Blocks.CHEST, "food", Town.hash(h.s().seed(), x, y, z));
                    orient(level, box, at.set(x, y, z), out);
                }
            }
            case NONE -> loose(level, h, room, r, y, box, at, random);
        }
    }

    /** At the second block: what is on the piece, or hung on the wall over it. */
    private static void upon(WorldGenLevel level, Here h, Room room, Placed p, long r, int y, BoundingBox box, BlockPos.MutableBlockPos at,
            RandomSource random) {
        int x = h.x();
        int z = h.z();
        BlockPos here = new BlockPos(x, y, z);
        switch (p.piece()) {
            case TABLE -> {
                // the house's diary, now and then, on its table - where whoever wrote it sat
                if (p.facing() == Direction.EAST && diaryHouse(h) && h.storey() == diaryStorey(h.house())) {
                    Props.pile(level, box, here, 1, (float) (r % 360), random, () -> Diaries.book(random, Diaries.Place.CAPITAL));
                    return;
                }
                tableTop(level, h, room, r, here, box, random);
            }
            case BOOKS_TALL -> {
                if (r % 3 == 0) {
                    chiseled(level, box, here, p.facing().getOpposite(), random);
                } else {
                    set(level, box, here, Blocks.BOOKSHELF.defaultBlockState());
                }
            }
            case COUNTER -> counterTop(level, h, room, r, here, box, random);
            case HEARTH -> {
                if (r % 2 == 0) {
                    set(level, box, here, dev.nookworks.registry.ModCooking.IRON_POT.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, p.facing().getOpposite()));
                }
            }
            case STACK -> {
                if (r % 3 != 0) {
                    set(level, box, here, (room == Room.STORE && r % 2 == 0 ? Blocks.HAY_BLOCK : r % 2 == 0 ? Blocks.BARREL : ModBlocks.CRATE.get())
                            .defaultBlockState());
                }
            }
            case CUPBOARD, LOW, NONE -> {
                // a shelf on the shared wall over it (what was left on it is drawn a block up, at the third)
                Direction wall = shelf(h, room, p, r);
                if (wall != null) {
                    set(level, box, here, ModWoods.WALL_SHELVES.get(wood(h.house())).get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, wall.getOpposite()));
                }
            }
            default -> {
            }
        }
    }

    /** The shared wall a shelf hangs on over this column's piece, toward the wall; null for none. */
    private static Direction shelf(Here h, Room room, Placed p, long r) {
        if (room == Room.STORE || r % 3 != 0 || !(p.piece() == Piece.CUPBOARD || p.piece() == Piece.LOW || p.piece() == Piece.NONE)) {
            return null;
        }
        return sharedWall(h, 0, 0);
    }

    private static void tableTop(WorldGenLevel level, Here h, Room room, long r, BlockPos at, BoundingBox box, RandomSource random) {
        long k = r % 10;
        if (k < 2) {
            set(level, box, at, Blocks.CANDLE.defaultBlockState());
        } else if (k < 4 && room == Room.KITCHEN) {
            tray(level, box, at, random);
        } else if (k < 5) {
            bottles(level, box, at, 1 + (int) (r % 3), random);
        } else if (k < 6) {
            Props.pile(level, box, at, 1 + (int) (r % 2), (float) (r * 37 % 360), random, () -> Props.found(level, random));
        } else if (k < 7) {
            set(level, box, at, (r % 2 == 0 ? Blocks.FLOWER_POT : Blocks.POTTED_DEAD_BUSH).defaultBlockState());
        }
    }

    private static void counterTop(WorldGenLevel level, Here h, Room room, long r, BlockPos at, BoundingBox box, RandomSource random) {
        Trade trade = trade(h.house());
        if (room == Room.SHOP && trade == Trade.BAKER || room == Room.KITCHEN && r % 3 == 0) {
            tray(level, box, at, random);
        } else if (room == Room.SHOP && trade == Trade.APOTHECARY || r % 4 == 1) {
            bottles(level, box, at, 1 + (int) (r % 4), random);
        } else if (room == Room.SHOP && trade == Trade.SCRIBE || r % 5 == 2) {
            Props.pile(level, box, at, 1 + (int) (r % 3), (float) (r * 53 % 360), random, () -> Props.found(level, random));
        } else if (r % 4 == 3) {
            set(level, box, at, Blocks.CANDLE.defaultBlockState());
        }
    }

    private static void shelfTop(WorldGenLevel level, Here h, Room room, long r, BlockPos at, BoundingBox box, RandomSource random, Direction out) {
        long k = (r >> 2) % 6;
        if (k == 0 || room == Room.LIVING && k < 3) {
            Props.row(level, box, at, out, (r & 1) == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT, 2 + (int) (r % 4), () -> Props.found(level, random));
        } else if (k == 1) {
            bottles(level, box, at, 1 + (int) (r % 3), random);
        } else if (k == 2) {
            set(level, box, at, (r % 2 == 0 ? dev.nookworks.registry.GlazedPots.EMPTY.get() : Blocks.POTTED_DEAD_BUSH).defaultBlockState());
        } else if (k == 3) {
            set(level, box, at, ModBlocks.UNLIT_LANTERN.get().defaultBlockState());
        }
    }

    /** On the floor, where nothing stands: a rug, a fallen book, one of them still in the house, someone who died here. */
    private static void loose(WorldGenLevel level, Here h, Room room, long r, int y, BoundingBox box, BlockPos.MutableBlockPos at,
            RandomSource random) {
        int x = h.x();
        int z = h.z();
        if (r < 40 && (room == Room.LIVING || room == Room.BEDROOM)) {
            Block carpet = r < 15 ? Blocks.CARPET.red() : r < 28 ? Blocks.CARPET.brown() : Blocks.CARPET.gray();
            set(level, box, at.set(x, y, z), carpet.defaultBlockState());
        } else if (r < 48) {
            Props.pile(level, box, at.set(x, y, z).immutable(), 1, (float) (r * 29 % 360), random, () -> Props.found(level, random));
        } else if (r < 54 && h.storey() == 0) {
            set(level, box, at.set(x, y, z), ZombieMarkerBlock.of(1 + (int) (r % 2), false));      // one of them, still in the house
        } else if (r < 56) {
            Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
        } else if (r < 58) {
            set(level, box, at.set(x, y, z), ModBlocks.PEBBLES.get().defaultBlockState());          // plaster down from the ceiling
        }
    }

    /** A burnt house's rooms: nothing left standing but what would not burn, the floor black with what fell. */
    private static void charred(WorldGenLevel level, Here h, int inStorey, long r, int y, BoundingBox box, BlockPos.MutableBlockPos at,
            RandomSource random) {
        if (inStorey != 1) {
            return;
        }
        int x = h.x();
        int z = h.z();
        if (r < 60) {
            set(level, box, at.set(x, y, z), (r < 25 ? Blocks.CARPET.black() : r < 45 ? Blocks.CARPET.gray() : Blocks.COAL_BLOCK).defaultBlockState());
        } else if (r < 75) {
            set(level, box, at.set(x, y, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,
                    r % 2 == 0 ? Direction.Axis.X : Direction.Axis.Z));                              // a beam come down
        } else if (r < 82) {
            set(level, box, at.set(x, y, z), (r % 2 == 0 ? Blocks.CAULDRON : Blocks.ANVIL).defaultBlockState());   // what iron there was
        } else if (r < 85) {
            Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
        }
    }

    // --- the house's diary ---------------------------------------------------------------------------------------

    /** Whether the house kept a diary: one in sixteen. */
    private static boolean diaryHouse(Here h) {
        return Math.floorMod(h.house().hash() >> 37, 16L) == 0;
    }

    /** The storey it was kept on: a living room or a kitchen, the first above the street with a table. */
    private static int diaryStorey(CapitalHouses.House house) {
        for (int k = 1; k < house.storeys(); k++) {
            Room room = room(house, k);
            if (room == Room.LIVING || room == Room.KITCHEN) {
                return k;
            }
        }
        return 0;
    }

    // --- Nookworks' props -------------------------------------------------------------------------------------------

    /** A chiseled bookshelf with a few books still in it; its state set with its slots first (its own setter needs a level). */
    static void chiseled(WorldGenLevel level, BoundingBox box, BlockPos at, Direction out, RandomSource random) {
        if (!box.isInside(at)) {
            return;
        }
        boolean[] filled = new boolean[6];
        BlockState state = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, out);
        for (int i = 0; i < 6; i++) {
            filled[i] = random.nextInt(3) != 0;
            state = state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i), filled[i]);
        }
        level.setBlock(at, state, 2);
        if (level.getBlockEntity(at) instanceof ChiseledBookShelfBlockEntity shelf) {
            for (int i = 0; i < 6; i++) {
                if (filled[i]) {
                    shelf.getItems().set(i, Props.found(level, random));
                }
            }
        }
    }

    /** A chiseled bookshelf with {@code book} in it among others: where a diary was put away. */
    static void chiseledWith(WorldGenLevel level, BoundingBox box, BlockPos at, Direction out, ItemStack book, RandomSource random) {
        if (!box.isInside(at)) {
            return;
        }
        int slot = random.nextInt(6);
        BlockState state = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(ChiseledBookShelfBlock.FACING, out);
        boolean[] filled = new boolean[6];
        for (int i = 0; i < 6; i++) {
            filled[i] = i == slot || random.nextInt(2) == 0;
            state = state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i), filled[i]);
        }
        level.setBlock(at, state, 2);
        if (level.getBlockEntity(at) instanceof ChiseledBookShelfBlockEntity shelf) {
            for (int i = 0; i < 6; i++) {
                if (filled[i]) {
                    shelf.getItems().set(i, i == slot ? book : Props.found(level, random));
                }
            }
        }
    }

    /** Bottles standing, empty: what the apothecary left, what was drunk. */
    static void bottles(WorldGenLevel level, BoundingBox box, BlockPos at, int n, RandomSource random) {
        if (!box.isInside(at)) {
            return;
        }
        n = Math.max(1, Math.min(4, n));
        level.setBlock(at, ModBlocks.PLACED_BOTTLES.get().defaultBlockState().setValue(dev.nookworks.decor.PlacedBottlesBlock.BOTTLES, n), 2);
        if (level.getBlockEntity(at) instanceof dev.nookworks.decor.PlacedBottlesBlockEntity placed) {
            for (int i = 0; i < n; i++) {
                placed.add(new ItemStack(Items.GLASS_BOTTLE));
            }
        }
    }

    /** A wooden tray with what was on it: bread gone hard, a bowl. */
    static void tray(WorldGenLevel level, BoundingBox box, BlockPos at, RandomSource random) {
        if (!box.isInside(at)) {
            return;
        }
        level.setBlock(at, dev.nookworks.registry.ModKitchen.WOODEN_TRAY.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)), 2);
        if (level.getBlockEntity(at) instanceof dev.nookworks.cooking.TrayBlockEntity tray) {
            int n = 1 + random.nextInt(3);
            for (int i = 0; i < n; i++) {
                tray.items().set(i, new ItemStack(random.nextInt(3) == 0 ? Items.BOWL : Items.BREAD));
            }
        }
    }

    private static void orient(WorldGenLevel level, BoundingBox box, BlockPos at, Direction out) {
        if (box.isInside(at) && out != null) {
            BlockState state = level.getBlockState(at);
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                level.setBlock(at, state.setValue(BlockStateProperties.HORIZONTAL_FACING, out), 2);
            }
        }
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    @SuppressWarnings("unused")
    private static Supplier<ItemStack> none() {
        return () -> ItemStack.EMPTY;
    }

    private CapitalRooms() {}
}
