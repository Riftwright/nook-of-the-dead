package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * What the capital's people left, and where ({@link Capital}): never told, only left (the story's rule, STORY.md) - the
 * barricades where the river quarter was shut off and then burnt; the dead heaped before the castle's gate with what they
 * had come to ask for still in their hands; the cathedral full of those who went there for sanctuary; the council's hall
 * where the rations were handed out, then not; the castle's stores, full; at the top of the keep, the one who shut the
 * gate, and what he wrote. The diaries of each place ({@link Diaries.Place#CAPITAL}, {@link Diaries.Place#CASTLE}...), and
 * the proclamations posted in the squares ({@link Notes#CAPITAL}).
 */
final class CapitalStory {
    /** The diaries the capital's places hold, by number (46 on; STORY.md, "都"). */
    static final int KING = 46;
    static final int CHAMBERLAIN = 47;
    static final int GUARD = 48;
    static final int PETITION = 49;
    static final int PRIEST = 50;
    static final int PRINTER = 51;
    static final int RIVERSIDE = 52;
    static final int FIRE = 53;
    static final int REFUGEE = 54;
    static final int MINUTES = 55;

    /** A lectern at {@code at}, a diary of {@code place} on it. */
    static void lectern(WorldGenLevel level, BoundingBox box, BlockPos at, RandomSource random, Diaries.Place place) {
        lectern(level, box, at, Diaries.book(random, place), Direction.Plane.HORIZONTAL.getRandomDirection(random));
    }

    static void lectern(WorldGenLevel level, BoundingBox box, BlockPos at, ItemStack book, Direction facing) {
        if (!box.isInside(at)) {
            return;
        }
        level.setBlock(at, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true), 2);
        if (level.getBlockEntity(at) instanceof LecternBlockEntity stand) {
            stand.setBook(book);
        }
    }

    /**
     * Whether castle coordinate {@code v} (the castle's axes run through its middle column's middle, so every column's is a
     * half) is the one column at {@code n}: one king, one lectern - not the two or four a half-block's tolerance took in.
     */
    private static boolean one(double v, int n) {
        return Math.floor(v) == n;
    }

    static void set(WorldGenLevel level, BoundingBox box, BlockPos at, BlockState state) {
        if (box.isInside(at)) {
            level.setBlock(at, state, 2);
        }
    }

    // --- the streets ----------------------------------------------------------------------------------------------

    /** A square's well: a round basin, dry or with a little rain in it, a post in its middle. */
    static void fountain(WorldGenLevel level, Capital.Site s, int x, int y, int z, double d, BoundingBox box, BlockPos.MutableBlockPos at) {
        if (d >= 1.6) {
            set(level, box, at.set(x, y + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState());
            return;
        }
        if (d < 0.7) {
            set(level, box, at.set(x, y + 1, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            set(level, box, at.set(x, y + 2, z), Blocks.STONE_BRICK_WALL.defaultBlockState());
            return;
        }
        boolean wet = Math.floorMod(Town.hash(s.seed(), x >> 3, z >> 3, 390), 3L) == 0;
        set(level, box, at.set(x, y, z), wet ? Blocks.WATER.defaultBlockState() : Blocks.MUD.defaultBlockState());
    }

    /**
     * What lies in a street: the rubble of the houses, moss, now and then a cart or a body; lamp posts along the main ways;
     * where the burnt quarter was shut off, the barricade across the street still standing.
     */
    static void onStreet(WorldGenLevel level, Capital.Site s, Capital.Column[][] w, int a, int b, Capital.Column c, int x, int y, int z,
            boolean burnt, long r, BoundingBox box, BlockPos.MutableBlockPos at) {
        double off = Math.abs(Capital.wrap(c.angle() - s.gate(0)));
        if (burnt) {
            if (r < 70) {
                set(level, box, at.set(x, y + 1, z), (r < 30 ? Blocks.CARPET.black() : r < 50 ? Blocks.CARPET.gray() : Blocks.COBBLED_DEEPSLATE_SLAB)
                        .defaultBlockState());
            } else if (r < 80) {
                set(level, box, at.set(x, y + 1, z), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS,
                        r % 2 == 0 ? Direction.Axis.X : Direction.Axis.Z));          // a beam, fallen from a roof
            } else if (r > 993) {
                Markers.remains(level, at.set(x, y + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(r)),
                        RandomSource.create(r * 3 + x));
            }
            return;
        }
        // lamp posts along the main ways, every so often, at the edge
        if (c.block() == 0 && c.kind() == Capital.Kind.STREET && Math.floorMod(x * 7 + z * 13, 23) == 0 && edgeOfWay(w, a, b)) {
            set(level, box, at.set(x, y + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(level, box, at.set(x, y + 2, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(level, box, at.set(x, y + 3, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
            set(level, box, at.set(x, y + 4, z), r < 600 ? Blocks.LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState());
            return;
        }
        double heap = Town.noise(s.seed(), x, z, 7.0, 391);
        if (heap > 0.88 && r < 700) {
            BlockState stone = r < 250 ? Blocks.COBBLESTONE.defaultBlockState() : r < 400 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : r < 550 ? Blocks.COBBLESTONE_SLAB.defaultBlockState() : Blocks.SPRUCE_SLAB.defaultBlockState();
            set(level, box, at.set(x, y + 1, z), stone);
            return;
        }
        if (r < 45) {
            set(level, box, at.set(x, y + 1, z), Blocks.MOSS_CARPET.defaultBlockState());
        } else if (r < 52) {
            set(level, box, at.set(x, y + 1, z), (r % 2 == 0 ? Blocks.BARREL : dev.nookworks.registry.ModBlocks.CRATE.get()).defaultBlockState());
        } else if (r > 996) {
            Markers.remains(level, at.set(x, y + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(r)),
                    RandomSource.create(r * 5 + z));
        }
    }

    private static boolean edgeOfWay(Capital.Column[][] w, int a, int b) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Capital.Column n = w[a + d.getStepX()][b + d.getStepZ()];
            if (n != null && n.kind() == Capital.Kind.BUILDING) {
                return true;
            }
        }
        return false;
    }

    /** A barricade across a street: carts, planks, stones and crates, as high as a man, a gap here and there pushed through. */
    static void barricade(WorldGenLevel level, Capital.Site s, int x, int y, int z, long r, BoundingBox box, BlockPos.MutableBlockPos at) {
        Block low = r < 250 ? Blocks.COBBLESTONE : r < 450 ? Blocks.OAK_PLANKS : r < 600 ? dev.nookworks.registry.ModBlocks.CRATE.get()
                : r < 750 ? Blocks.SPRUCE_FENCE : Blocks.COBBLESTONE_WALL;
        if (r > 920) {
            return;                                                           // pushed through, here
        }
        set(level, box, at.set(x, y + 1, z), low.defaultBlockState());
        if (r < 700) {
            set(level, box, at.set(x, y + 2, z), (r < 300 ? Blocks.OAK_PLANKS : r < 500 ? Blocks.SPRUCE_FENCE : Blocks.COBBLESTONE_WALL).defaultBlockState());
        }
        if (r < 120) {
            Markers.remains(level, at.set(x, y + 3, z).immutable(), Direction.NORTH, RandomSource.create(r));
        }
    }

    /** On the river: now and then a boat's wreck, sunk to its rails. */
    static void onRiver(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int z, long r, BoundingBox box, BlockPos.MutableBlockPos at) {
        CapitalStreets.onWater(level, s, c, x, z, r, box, at);
        if (r > 997) {
            set(level, box, at.set(x, Capital.WATER, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        }
    }

    /**
     * On a bridge: where the river quarter's bridges come to the city's side, the barricade they put across them - the
     * river quarter was shut off at the water.
     */
    static void onBridge(WorldGenLevel level, Capital.Site s, Capital.Column c, int x, int y, int z, BoundingBox box, BlockPos.MutableBlockPos at) {
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 392), 1000L);
        boolean nearSide = c.river() < -Capital.RIVER_HALF + 1 && c.river() > -Capital.RIVER_HALF - 1;
        if (nearSide) {
            barricade(level, s, x, y, z, r, box, at);
            return;
        }
        if (r > 995) {
            Markers.remains(level, at.set(x, y + 1, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(r)),
                    RandomSource.create(r));
        }
    }

    // --- the castle -----------------------------------------------------------------------------------------------

    /**
     * A room of the keep, storey by storey: the stores at the bottom (full), the guard room, the kitchen, the chancery and
     * its books, the royal rooms, the chapel; at the top, where the king shut himself in, the king.
     */
    static void keepRoom(WorldGenLevel level, Capital.Site s, int x, int z, double kf, double kl, int top, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random, boolean ladder) {
        if (ladder) {
            return;
        }
        long h = Math.floorMod(Town.hash(s.seed(), x, z, 393), 1000L);
        boolean byWall = Math.min(CapitalCastle.KEEP_HALF - 1.5 - Math.abs(kf), CapitalCastle.KEEP_HALF - 1.5 - Math.abs(kl)) < 1;
        for (int storey = 0; storey < CapitalCastle.KEEP_STOREYS; storey++) {
            int y = top + storey * CapitalCastle.KEEP_STOREY + 1;
            long r = Math.floorMod(h * 31 + storey * 97, 1000L);
            switch (storey) {
                case 0 -> {                                                    // the stores: what the city was not given
                    if (r < 420) {
                        Block b = r < 140 ? Blocks.BARREL : r < 260 ? Blocks.HAY_BLOCK : r < 340 ? dev.nookworks.registry.ModBlocks.CRATE.get()
                                : Blocks.BARREL;
                        set(level, box, at.set(x, y, z), b.defaultBlockState());
                        if (r < 200) {
                            set(level, box, at.set(x, y + 1, z), (r < 100 ? Blocks.BARREL : Blocks.HAY_BLOCK).defaultBlockState());
                        }
                        if (r < 30) {
                            CapitalDraw.container(level, box, at.set(x, y, z), Blocks.BARREL, "food", Town.hash(s.seed(), x, y, z));
                        }
                    }
                }
                case 1 -> {                                                    // the guard room
                    if (byWall && r < 120) {
                        CapitalDraw.container(level, box, at.set(x, y, z), Blocks.CHEST, "weapon", Town.hash(s.seed(), x, y, z));
                    } else if (one(kl, 0) && kf > -5 && kf < 4) {
                        // the guards' long table, their stools along it, their dice and their cups left on it
                        set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.DESKS.get("spruce").get().defaultBlockState()
                                .setValue(dev.nookworks.furniture.DeskBlock.joined(CapitalCastle.forward(s)), kf < 3)
                                .setValue(dev.nookworks.furniture.DeskBlock.joined(CapitalCastle.forward(s).getOpposite()), kf > -4));
                        if (r < 250) {
                            CapitalRooms.bottles(level, box, at.set(x, y + 1, z).immutable(), 1 + (int) (r % 3), random);
                        } else if (r < 330) {
                            set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState());
                        }
                    } else if ((one(kl, 1) || one(kl, -1)) && kf > -5 && kf < 4 && r < 700) {
                        set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.STOOLS.get("spruce").get().defaultBlockState());
                    } else if (r > 985) {
                        set(level, box, at.set(x, y, z), ZombieMarkerBlock.of(2, false));
                    }
                }
                case 2 -> {                                                    // the kitchen and the table
                    if (byWall && r < 160) {
                        set(level, box, at.set(x, y, z), (r < 50 ? Blocks.FURNACE : r < 90 ? Blocks.SMOKER : r < 130 ? Blocks.BARREL
                                : Blocks.CAULDRON).defaultBlockState());
                    } else if (!byWall && Math.abs(kl) < 1.6 && Math.abs(kf) < 6) {
                        set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.DESKS.get("dark_oak").get().defaultBlockState()
                                .setValue(dev.nookworks.furniture.DeskBlock.joined(CapitalCastle.forward(s)), kf < 5)
                                .setValue(dev.nookworks.furniture.DeskBlock.joined(CapitalCastle.forward(s).getOpposite()), kf > -5));
                        if (r < 200) {
                            CapitalRooms.tray(level, box, at.set(x, y + 1, z).immutable(), random);
                        } else if (r < 260) {
                            set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState());
                        }
                    }
                }
                case 3 -> {                                                    // the chancery: shelves of records
                    if (byWall && r < 760) {
                        // the records to the ceiling, some shelves of the bound sort, some of the ledgers in their slots
                        Direction into = CapitalCastleRooms.into(s, kf, kl);
                        for (int k = 0; k < 3; k++) {
                            if (into != null && Math.floorMod(r >> (k * 3), 3L) == 0) {
                                CapitalRooms.chiseled(level, box, at.set(x, y + k, z).immutable(), into, random);
                            } else {
                                set(level, box, at.set(x, y + k, z), Blocks.BOOKSHELF.defaultBlockState());
                            }
                        }
                    } else if (!byWall && Math.abs(kl) > 4 && Math.abs(kl) < 6 && Math.abs(kf) < 6 && r < 400) {
                        // the clerks' desks down the room, papers on them
                        set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.DESKS.get("dark_oak").get().defaultBlockState());
                        if (r < 160) {
                            Props.pile(level, box, at.set(x, y + 1, z).immutable(), 1 + (int) (r % 3), (float) (r * 7 % 360), random,
                                    () -> Props.found(level, random));
                        } else if (r < 200) {
                            set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState());
                        }
                    } else if (one(kf, 0) && one(kl, 2)) {
                        // his desk, his book on it
                        set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.DESKS.get("dark_oak").get().defaultBlockState());
                        Props.pile(level, box, at.set(x, y + 1, z).immutable(), 1, CapitalCastle.forward(s).toYRot(), random,
                                () -> Diaries.book(CHAMBERLAIN));
                    } else if (one(kf, 1) && one(kl, 2)) {
                        set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.CHAIRS.get("dark_oak").get().defaultBlockState()
                                .setValue(BlockStateProperties.HORIZONTAL_FACING, CapitalCastle.forward(s).getOpposite()));
                    } else if (one(kf, -9) && one(kl, 0)) {
                        // the hearth the petitions went into, and their ash
                        set(level, box, at.set(x, y, z), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                    } else if (one(kf, -8) && Math.abs(kl) < 1.6) {
                        set(level, box, at.set(x, y, z), Blocks.CARPET.lightGray().defaultBlockState());
                    }
                }
                case 4 -> courtRoom(level, s, x, y, z, kf, kl, byWall, r, box, at, random);   // the room where he gave audience
                case 5 -> {                                                    // the private chapel
                    if (one(kf, 6) && one(kl, -8)) {
                        // at the foot of the last ladder, under the shut hatch: the one who came up with the key, knocking
                        set(level, box, at.set(x, y, z), ZombieMarkerBlock.of(1, false));
                    } else if (!byWall) {
                        // the household's chapel: benches either side of the aisle, the altar at the back
                        CapitalCastleRooms.chapelFloor(level, s, x, z, kf + 12, kl, y - 1, box, at, random);   // its altar the row by the back wall
                    }
                }
                default -> kingsRoom(level, s, x, y, z, (int) Math.floor(kf), (int) Math.floor(kl), byWall, r, box, at, random);
            }
        }
    }

    /**
     * The fifth storey, where he gave audience: a great rug to the chair of state at the back under its canopy, chairs
     * along the sides for those let up, the court's chests by the walls; the hearth at the front.
     */
    private static void courtRoom(WorldGenLevel level, Capital.Site s, int x, int y, int z, double kf, double kl, boolean byWall, long r,
            BoundingBox box, BlockPos.MutableBlockPos at, RandomSource random) {
        Direction fwd = CapitalCastle.forward(s);
        int fk = (int) Math.floor(kf);
        int lk = (int) Math.floor(kl);
        if (fk >= -7 && fk <= 5 && lk >= -3 && lk <= 2) {
            // the rug, gold-bordered
            boolean border = fk == -7 || fk == 5 || lk == -3 || lk == 2;
            set(level, box, at.set(x, y, z), (border ? Blocks.CARPET.yellow() : Blocks.CARPET.red()).defaultBlockState());
            if (fk == -7 && (lk == -1 || lk == 0)) {
                // the chair of state, a step up at the rug's head
                set(level, box, at.set(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                set(level, box, at.set(x, y + 1, z), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, fwd.getOpposite()));
            }
            return;
        }
        if (fk == -8 && lk >= -2 && lk <= 1) {
            // its canopy: a dark frame over it
            set(level, box, at.set(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            set(level, box, at.set(x, y + 3, z), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.TOP));
            if (lk == -2 || lk == 1) {
                set(level, box, at.set(x, y + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
                set(level, box, at.set(x, y + 2, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
            return;
        }
        if ((lk == -5 || lk == 4) && fk >= -6 && fk <= 4 && Math.floorMod(fk, 2) == 0) {
            // chairs down both sides for those let up, facing across
            set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.CHAIRS.get("dark_oak").get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, lk < 0 ? CapitalCastle.sideways(s, 1) : CapitalCastle.sideways(s, -1)));
            return;
        }
        if (byWall && lk == 0 && fk > 0) {
            set(level, box, at.set(x, y, z), Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
            return;
        }
        if (byWall && r < 300) {
            CapitalDraw.container(level, box, at.set(x, y, z), Blocks.CHEST, "general", Town.hash(s.seed(), x, y, z));
            return;
        }
        if (r > 985) {
            set(level, box, at.set(x, y, z), ZombieMarkerBlock.of(1, false));
        }
    }

    /**
     * The top of the keep, the king's own room ({@code fk}, {@code lk} the column from its middle, the door's way forward):
     * he went up with his bed, the treasury and what food there was, boarded the windows and shut the hatch over the ladder
     * behind him. His chair at the back, his book beside it; the rug he walked; and in the middle, him.
     */
    private static void kingsRoom(WorldGenLevel level, Capital.Site s, int x, int y, int z, int fk, int lk, boolean byWall, long r,
            BoundingBox box, BlockPos.MutableBlockPos at, RandomSource random) {
        Direction forward = CapitalCastle.forward(s);
        if (fk == 0 && lk == 0) {
            set(level, box, at.set(x, y, z), Blocks.CARPET.red().defaultBlockState());
            king(level, s, at.set(x, y, z).immutable(), box);
        } else if (fk == -8 && lk == 0) {
            set(level, box, at.set(x, y, z), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, forward.getOpposite()));
        } else if (fk == -8 && Math.abs(lk) == 1) {
            set(level, box, at.set(x, y, z), Blocks.CARPET.red().defaultBlockState());
        } else if (fk == -7 && lk == 1) {
            // the table by his chair, and his book on it, where he wrote it
            set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.DESKS.get("dark_oak").get().defaultBlockState());
            Props.pile(level, box, at.set(x, y + 1, z).immutable(), 1, forward.toYRot(), random, () -> Diaries.book(KING));
        } else if (fk <= -7 && lk >= 4) {
            // the treasury, all of it, for nothing: gold to the wall, what the realm had that was worth more at the back
            Block heap = fk == -9 && lk == 8 || fk == -8 && lk == 8 ? Blocks.NETHERITE_BLOCK
                    : fk == -9 && lk == 7 ? dev.nookworks.registry.ModBlocks.VELARIUM_BLOCK.get() : Blocks.GOLD_BLOCK;
            set(level, box, at.set(x, y, z), heap.defaultBlockState());
            if (fk == -9 || lk >= 7) {
                set(level, box, at.set(x, y + 1, z), (r < 300 ? Blocks.RAW_GOLD_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState());
            }
        } else if (fk == -6 && lk == 5) {
            treasury(level, box, at.set(x, y, z).immutable(), forward);
        } else if ((fk == -8 || fk == -7) && lk == -5) {
            // his bed, carried up
            set(level, box, at.set(x, y, z), Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, forward.getOpposite())
                    .setValue(BedBlock.PART, fk == -8 ? BedPart.HEAD : BedPart.FOOT));
        } else if (fk == -8 && lk == -3) {
            set(level, box, at.set(x, y, z), Blocks.BARREL.defaultBlockState());
            set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState());
        } else if (lk >= 6 && fk > -6 && fk < 7 && r < 520) {
            // what food there was, along the wall: most of it opened, eaten
            if (r < 90) {
                CapitalDraw.container(level, box, at.set(x, y, z), Blocks.BARREL, "food", Town.hash(s.seed(), x, y, z));
            } else {
                set(level, box, at.set(x, y, z), (r < 300 ? Blocks.BARREL : dev.nookworks.registry.ModBlocks.CRATE.get()).defaultBlockState());
                if (r < 160 && lk == 8) {
                    set(level, box, at.set(x, y + 1, z), Blocks.BARREL.defaultBlockState());
                }
            }
        } else if (fk >= -4 && fk <= 3 && Math.abs(lk + 0.5) <= 2.5) {
            boolean border = fk == -4 || fk == 3 || lk == -3 || lk == 2;
            set(level, box, at.set(x, y, z), (border ? Blocks.CARPET.yellow() : Blocks.CARPET.red()).defaultBlockState());
        }
    }

    /**
     * The king's own chest: what he took up with him (the user, in game: reaching the top of the castle in survival is hard
     * going - something worth it there): tempered Velarium, netherite, a pair of wings no one ever flew away on.
     */
    private static void treasury(WorldGenLevel level, BoundingBox box, BlockPos at, Direction facing) {
        if (!box.isInside(at)) {
            return;
        }
        level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 2);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            ItemStack[] hoard = {
                    new ItemStack(dev.nookworks.registry.ModItems.TEMPERED_VELARIUM_INGOT.get(), 6),
                    new ItemStack(Items.NETHERITE_INGOT, 3),
                    new ItemStack(Items.ELYTRA),
                    new ItemStack(Items.DIAMOND, 12),
                    new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                    new ItemStack(Items.GOLD_INGOT, 32),
                    new ItemStack(Items.EMERALD, 16),
                    new ItemStack(Items.GOLDEN_APPLE, 3),
            };
            int[] slots = {4, 10, 13, 16, 22, 1, 25, 7};
            for (int i = 0; i < hoard.length; i++) {
                chest.setItem(slots[i], hoard[i]);
            }
        }
    }

    /** The king: one of them now, in his crown, at the top of his keep; kept for good. */
    static void king(WorldGenLevel level, Capital.Site s, BlockPos at, BoundingBox box) {
        if (!box.isInside(at)) {
            return;
        }
        var king = dev.nookofthedead.registry.ZwEntities.MUTATED_ZOMBIE.get().create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (king == null) {
            return;
        }
        king.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, CapitalCastle.forward(s).toYRot(), 0.0F);
        king.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
        king.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        ItemStack robe = new ItemStack(Items.LEATHER_CHESTPLATE);
        robe.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x7A1E2C));
        king.setItemSlot(EquipmentSlot.CHEST, robe);
        king.setDropChance(EquipmentSlot.HEAD, 0);
        king.setDropChance(EquipmentSlot.CHEST, 0);
        king.setCustomName(Component.translatable("entity.nookofthedead.king"));
        king.addTag(dev.nookofthedead.entity.Unkept.KEPT);
        king.setPersistenceRequired();
        level.addFreshEntity(king);
    }

    // --- the cathedral and the hall -------------------------------------------------------------------------------

    /**
     * The cathedral's floor: the runner up the nave to the chancel, a step up to it, the choir's stalls, the altar and its
     * candlesticks; the pews down the nave either side; sconces along the aisles; chandeliers on chains over the nave; and
     * everywhere the people who came for sanctuary - sat in the pews, lying in the aisles, heaped against the doors they
     * barred.
     */
    static void cathedralFloor(WorldGenLevel level, Capital.Site s, int x, int z, double a0, double b0, int base, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random, boolean tower) {
        int y = base + 1;
        int a = (int) Math.round(a0);
        int b = (int) Math.round(b0);
        int ab = Math.abs(b);
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 395), 1000L);
        CapitalLandmarks.Frame f = CapitalLandmarks.cathedral(s);
        Direction toAltar = f.front().getOpposite();
        Direction side = f.front().getClockWise();                           // toward +b
        int nave = CapitalLandmarks.NAVE;
        if (tower) {
            if (r < 40) {
                set(level, box, at.set(x, y, z), Blocks.COBWEB.defaultBlockState());
            }
            return;
        }
        if (CapitalLandmarks.nave(f, a, b, 1) != null) {
            return;                                                         // a pier
        }
        if (a < 4 && ab < 3) {                                              // the doors, barred from inside: the pews piled against them
            if (r < 700) {
                set(level, box, at.set(x, y, z), (r < 300 ? Blocks.DARK_OAK_STAIRS : r < 500 ? Blocks.OAK_PLANKS : Blocks.DARK_OAK_PLANKS)
                        .defaultBlockState());
                if (r < 350) {
                    set(level, box, at.set(x, y + 1, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
                }
            }
            if (r > 880) {
                Markers.remains(level, at.set(x, y, z).immutable(), f.front(), random);
            }
            return;
        }
        chandelier(level, s, x, z, a, b, base, side, box, at);
        // the chancel: a step up across the nave, then raised to the apse
        if (a >= nave - 6 && ab < CapitalLandmarks.NAVE_W) {
            if (a == nave - 6) {
                set(level, box, at.set(x, y, z), Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toAltar));
                return;
            }
            set(level, box, at.set(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            int up = y + 1;
            if ((a == nave || a == nave + 1) && ab <= 2) {
                // the altar, its cloth, the cross of gold and the candles
                set(level, box, at.set(x, up, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                set(level, box, at.set(x, up + 1, z), a == nave && ab == 0 ? Blocks.GOLD_BLOCK.defaultBlockState()
                        : Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) (r % 4)));
            } else if (a == nave - 3 && ab == 0) {
                lectern(level, box, at.set(x, up, z).immutable(), Diaries.book(PRIEST), f.front());
            } else if (a == nave - 1 && ab == 3) {
                // candlesticks either side, taller than a man
                set(level, box, at.set(x, up, z), Blocks.IRON_BARS.defaultBlockState());
                set(level, box, at.set(x, up + 1, z), Blocks.IRON_BARS.defaultBlockState());
                set(level, box, at.set(x, up + 2, z), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 3));
            } else if (a >= nave - 5 && a <= nave - 2 && (ab == 5 || ab == 6)) {
                // the choir's stalls, facing across
                set(level, box, at.set(x, up, z), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, b > 0 ? side : side.getOpposite()));
            } else if (ab < 2 && a < nave) {
                set(level, box, at.set(x, up, z), Blocks.CARPET.red().defaultBlockState());
            }
            return;
        }
        boolean crossing = a >= CapitalLandmarks.TRANSEPT_FROM && a <= CapitalLandmarks.TRANSEPT_TO;
        // the runner up the nave's middle
        if (ab <= 1 && a >= 4) {
            set(level, box, at.set(x, y, z), Blocks.CARPET.red().defaultBlockState());
            if (r > 985) {
                Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
            }
            return;
        }
        // the pews: either side of the runner, every other row, their backs to the door
        if (!crossing && ab >= 2 && ab <= 4 && a > 8 && a < nave - 7 && Math.floorMod(a, 2) == 0) {
            set(level, box, at.set(x, y, z), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.front()));
            if (r < 160) {
                Markers.remains(level, at.set(x, y, z).immutable(), toAltar, random);   // sat where they sat
            }
            return;
        }
        // in the arms of the transept, a side altar against each end wall
        if (crossing && ab == CapitalLandmarks.TRANSEPT_W - 1 && Math.abs(a - (CapitalLandmarks.TRANSEPT_FROM + CapitalLandmarks.TRANSEPT_TO) / 2) <= 1) {
            set(level, box, at.set(x, y, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) (r % 4)));
            return;
        }
        // along the aisles' walls, a sconce at every buttress
        if (!crossing && ab == CapitalLandmarks.NAVE_W - 1 && Math.floorMod(a, 4) == 0) {
            set(level, box, at.set(x, y + 2, z), dev.nookworks.registry.ModBlocks.WALL_SCONCE.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, b > 0 ? side.getOpposite() : side));
        }
        if (r < 70) {
            Markers.remains(level, at.set(x, y, z).immutable(), Direction.Plane.HORIZONTAL.getRandomDirection(random), random);
        } else if (r < 100) {
            set(level, box, at.set(x, y, z), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) (r % 4)));
        } else if (r < 112) {
            set(level, box, at.set(x, y, z), ZombieMarkerBlock.of(1 + (int) (r % 3), false));
        }
    }

    /** A chandelier over the nave's middle every twelve: chains from the vault, a ring with its arms, lanterns long out. */
    private static void chandelier(WorldGenLevel level, Capital.Site s, int x, int z, int a, int b, int base, Direction side, BoundingBox box,
            BlockPos.MutableBlockPos at) {
        int ring = base + 13;
        int c = Math.floorMod(a - 6, 12);
        boolean lit = a >= 12 && a < CapitalLandmarks.NAVE - 6 && !(a >= CapitalLandmarks.TRANSEPT_FROM - 2 && a <= CapitalLandmarks.TRANSEPT_TO + 2);
        if (!lit) {
            return;
        }
        if (c == 0 && b == 0) {
            for (int y = ring + 1; y <= base + 21; y++) {
                set(level, box, at.set(x, y, z), Blocks.IRON_CHAIN.defaultBlockState());
            }
            set(level, box, at.set(x, ring, z), Blocks.IRON_BARS.defaultBlockState());
            set(level, box, at.set(x, ring - 1, z), dev.nookworks.registry.ModBlocks.UNLIT_LANTERN.get().defaultBlockState()
                    .setValue(BlockStateProperties.HANGING, true));
            return;
        }
        boolean armAlong = b == 0 && (c == 1 || c == 11 || c == 2 || c == 10);
        boolean armAcross = c == 0 && Math.abs(b) <= 2;
        if (armAlong || armAcross) {
            Direction.Axis axis = armAlong ? side.getClockWise().getAxis() : side.getAxis();
            set(level, box, at.set(x, ring, z), Blocks.IRON_CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, axis));
            if (c == 2 || c == 10 || Math.abs(b) == 2) {
                set(level, box, at.set(x, ring - 1, z), dev.nookworks.registry.ModBlocks.UNLIT_LANTERN.get().defaultBlockState()
                        .setValue(BlockStateProperties.HANGING, true));
            }
        }
    }

    /**
     * The council's hall: the arcade on the square where the rations were handed out (the counters, the empty barrels, the
     * ledger); the council's chamber over it, its long table, its minutes; the archive at the top.
     */
    static void hallRooms(WorldGenLevel level, Capital.Site s, int x, int z, double a, double b, int base, int storey, BoundingBox box,
            BlockPos.MutableBlockPos at, RandomSource random) {
        long r = Math.floorMod(Town.hash(s.seed(), x, z, 396), 1000L);
        CapitalLandmarks.Frame f = CapitalLandmarks.hall(s);
        // the ground floor: the counter across it where the rations were handed out, boarded up higher once they ran short;
        // the barrels behind it empty; before it, the ones who queued with their tickets, and the crush that came after
        int y = base + 1;
        if (Math.abs(a - 5) < 0.6 && Math.abs(b) < 11) {
            set(level, box, at.set(x, y, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
            if (r < 120) {
                set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState());
            } else if (r < 520) {
                set(level, box, at.set(x, y + 1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (r < 300) {
                    set(level, box, at.set(x, y + 2, z), Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            }
        } else if (a > 6 && a < 15 && r < 260) {
            set(level, box, at.set(x, y, z), Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,
                    r < 90 ? Direction.UP : f.front().getClockWise()));                           // some knocked over
        } else if (a < 5 && r > 925) {
            Markers.remains(level, at.set(x, y, z).immutable(), f.front().getOpposite(), random,
                    r > 960 ? new ItemStack(Items.PAPER) : ItemStack.EMPTY);
        }
        // the chamber: the long table, the chairs, the minutes on the clerk's desk at its head
        y = base + storey + 1;
        if (Math.abs(b) < 1.1 && a > 3 && a < 14) {
            // one table of desks set together
            BlockState desk = dev.nookworks.registry.ModWoods.DESKS.get("dark_oak").get().defaultBlockState();
            Direction in = f.front().getOpposite();
            Direction side = f.front().getClockWise();
            desk = desk.setValue(dev.nookworks.furniture.DeskBlock.joined(in), a < 13).setValue(dev.nookworks.furniture.DeskBlock.joined(in.getOpposite()), a > 4)
                    .setValue(dev.nookworks.furniture.DeskBlock.joined(side), b < 1).setValue(dev.nookworks.furniture.DeskBlock.joined(side.getOpposite()), b > -1);
            set(level, box, at.set(x, y, z), desk);
            if (r < 120) {
                set(level, box, at.set(x, y + 1, z), Blocks.CANDLE.defaultBlockState());
            }
        } else if (Math.abs(b) == 2 && (a == 5 || a == 7 || a == 9 || a == 11)) {
            // the councillors' chairs, facing the table; one pushed back askew - his name struck from the minutes
            Direction toTable = b > 0 ? f.front().getCounterClockWise() : f.front().getClockWise();
            boolean struck = a == 9 && b == 2;
            set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.CHAIRS.get("dark_oak").get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, struck ? f.front() : toTable));
        } else if (a == 13 && b == -2) {
            Markers.remains(level, at.set(x, y, z).immutable(), f.front().getCounterClockWise(), random);   // the clerk, the last one
        } else if (a == 14 && b == 0) {
            // the clerk's desk at the table's head, the minutes on it
            set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.DESKS.get("dark_oak").get().defaultBlockState());
            Props.pile(level, box, at.set(x, y + 1, z).immutable(), 1, f.front().toYRot(), random, () -> Diaries.book(MINUTES));
        } else if (a == 15 && b == 0) {
            set(level, box, at.set(x, y, z), dev.nookworks.registry.ModWoods.CHAIRS.get("dark_oak").get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, f.front()));
        }
        // the archive
        y = base + 2 * storey + 1;
        double in = Math.min(Math.min(a + 0.5, CapitalLandmarks.HALL_D - 0.5 - a), CapitalLandmarks.HALL_W + 0.5 - Math.abs(b));
        if (in < 2.2 || Math.floorMod((int) Math.round(b), 4) == 0 && a > 3 && a < 14) {
            set(level, box, at.set(x, y, z), Blocks.BOOKSHELF.defaultBlockState());
            set(level, box, at.set(x, y + 1, z), Blocks.BOOKSHELF.defaultBlockState());
        }
    }

    private CapitalStory() {}
}
