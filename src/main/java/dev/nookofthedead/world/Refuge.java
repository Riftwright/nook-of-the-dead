package dev.nookofthedead.world;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The last of them sealed themselves in past the Vandal's hall, in the rock under the institute's labs (the user,
 * 2026-10-19, after the review found nowhere to breathe and the Vandal paying in what one had already: the refuge "でよい" -
 * Nookworks' machines still wired and running, so that using them where they stand beats carrying them home; brewing,
 * a lodestone, and "この避難所内では、感染の進行も止まる"). Inside it: none of the dead come out or are led in, none break
 * in, and the plague stands still. What keeps it is shown and never told - the ring of Dark Matter they laid round it,
 * the iron they walled it with, and all round it in the rock the plate they sealed it in ({@link #hull}): nothing breaks
 * that, so the one way in is from the Vandal's hall, through the iron plug.
 * <p>
 * Its rooms are a box of the deep part ({@link #LOCAL}, in the template's own blocks), placed with it: found for any place
 * by the block it is in, the same from everywhere.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = dev.nookofthedead.NookOfTheDead.MODID)
public final class Refuge {
    /**
     * The refuge in {@link Town#INSTITUTE_DEEP}'s own blocks: south of the hall, under the labs, below the observation gallery (y 9
     * up), and its lower floor under it ({@link RefugeRooms}).
     */
    public static final BoundingBox LOCAL = new BoundingBox(8, RefugeRooms.FLOOR, 37, 56, 8, 62);

    /** Each institute's refuge in the world, by its block (none where the block has no institute). */
    private static final Map<Long, BoundingBox> PLACED = new ConcurrentHashMap<>();
    private static final BoundingBox NONE = new BoundingBox(0, 0, 0, 0, 0, 0);

    /** Whether {@code pos} is in an institute's refuge. */
    public static boolean inside(LevelReader level, BlockPos pos) {
        ServerLevel server = level instanceof ServerLevel s ? s
                : level instanceof net.minecraft.world.level.ServerLevelAccessor a ? a.getLevel() : null;
        if (server == null || !dev.nookofthedead.NookOfTheDead.fallen(server)) {
            return false;
        }
        BoundingBox box = box(server, Math.floorDiv(pos.getX(), Town.BLOCK), Math.floorDiv(pos.getZ(), Town.BLOCK));
        return box != null && box.isInside(pos);
    }

    /** Where none of the dead break in or are let in: the refuge, or a Dark Matter's reach ({@link Sanctuary}). */
    public static boolean guarded(LevelReader level, BlockPos pos) {
        return inside(level, pos) || Sanctuary.near(level, pos);
    }

    /** The refuge of the institute in block ({@code bx}, {@code bz}), placed; null if there is none there. */
    public static @Nullable BoundingBox box(ServerLevel level, int bx, int bz) {
        long seed = level.getSeed();
        if (!Institute.at(seed, bx, bz)) {
            return null;
        }
        BoundingBox box = PLACED.computeIfAbsent(seed * 31 + ((long) bx << 32 ^ (bz & 0xffffffffL)), k -> {
            Town.Lot lot = Town.lots(seed, bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                    .findFirst().orElse(null);
            StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
            if (lot == null || deep == null) {
                return NONE;
            }
            BlockPos under = Town.deepOrigin(lot, deep);
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
            BlockPos a = StructureTemplate.calculateRelativePosition(settings, new BlockPos(LOCAL.minX(), LOCAL.minY(), LOCAL.minZ())).offset(under);
            BlockPos b = StructureTemplate.calculateRelativePosition(settings, new BlockPos(LOCAL.maxX(), LOCAL.maxY(), LOCAL.maxZ())).offset(under);
            return BoundingBox.fromCorners(a, b);
        });
        return box == NONE ? null : box;
    }

    /**
     * The refuge sealed all round in bulkheads ({@code nookofthedead:bulkhead}), at the cells of {@link #HULL} (in the deep
     * part's own blocks; worked out by tools/refuge_hull.py from the template as shipped): the rock face to face with it
     * outside, the shell itself where the outside is open (the observation gallery over it), and the walls of the passage
     * from the hall to its iron plug (the user, 2026-10-19: one could dig in from anywhere and never meet the Vandal). Set
     * down after the deep part is placed, as {@link #leftBehind}.
     */
    public static void hull(net.minecraft.world.level.LevelAccessor level, BlockPos under, StructurePlaceSettings settings, BoundingBox box) {
        var bulkhead = dev.nookofthedead.registry.ZwBlocks.BULKHEAD.get().defaultBlockState();
        for (String run : HULL.trim().split(" ")) {
            String[] p = run.split(",");
            int y = Integer.parseInt(p[0]);
            int z = Integer.parseInt(p[1]);
            for (int x = Integer.parseInt(p[2]); x <= Integer.parseInt(p[3]); x++) {
                BlockPos at = StructureTemplate.calculateRelativePosition(settings, new BlockPos(x, y, z)).offset(under);
                if (box.isInside(at) && !level.getBlockState(at).isAir()) {
                    level.setBlock(at, bulkhead, 2);
                }
            }
        }
    }

    // BEGIN GENERATED by tools/refuge_hull.py
    static final String HULL = ""
            + "0,39,7,57 0,40,7,57 0,41,7,57 0,42,7,57 0,43,7,57 0,44,7,57 0,45,7,57 0,46,7,57 0,47,7,57 0,48,7,57 0,49,7,57 "
            + "0,50,7,57 0,51,7,57 0,52,7,57 0,53,7,57 0,54,7,57 0,55,7,57 0,56,7,57 0,57,7,57 0,58,7,57 0,59,7,57 0,60,7,57 "
            + "0,61,7,57 0,62,7,57 0,63,7,57 1,35,36,39 1,36,36,39 1,37,36,39 1,38,7,57 1,39,6,6 1,39,58,58 1,40,6,6 "
            + "1,40,58,58 1,41,6,6 1,41,58,58 1,42,6,6 1,42,58,58 1,43,6,6 1,43,58,58 1,44,6,6 1,44,58,58 1,45,6,6 "
            + "1,45,58,58 1,46,6,6 1,46,58,58 1,47,6,6 1,47,58,58 1,48,6,6 1,48,58,58 1,49,6,6 1,49,58,58 1,50,6,6 "
            + "1,50,58,58 1,51,6,6 1,51,58,58 1,52,6,6 1,52,58,58 1,53,6,6 1,53,58,58 1,54,6,6 1,54,58,58 1,55,6,6 "
            + "1,55,58,58 1,56,6,6 1,56,58,58 1,57,6,6 1,57,58,58 1,58,6,6 1,58,58,58 1,59,6,6 1,59,58,58 1,60,6,6 "
            + "1,60,58,58 1,61,6,6 1,61,58,58 1,62,6,6 1,62,58,58 1,63,6,6 1,63,58,58 1,64,7,57 2,35,36,36 2,35,39,39 "
            + "2,36,36,36 2,36,39,39 2,37,36,36 2,37,39,39 2,38,7,36 2,38,39,57 2,39,6,6 2,39,58,58 2,40,6,6 2,40,58,58 "
            + "2,41,6,6 2,41,58,58 2,42,6,6 2,42,58,58 2,43,6,6 2,43,58,58 2,44,6,6 2,44,58,58 2,45,6,6 2,45,58,58 2,46,6,6 "
            + "2,46,58,58 2,47,6,6 2,47,58,58 2,48,6,6 2,48,58,58 2,49,6,6 2,49,58,58 2,50,6,6 2,50,58,58 2,51,6,6 "
            + "2,51,58,58 2,52,6,6 2,52,58,58 2,53,6,6 2,53,58,58 2,54,6,6 2,54,58,58 2,55,6,6 2,55,58,58 2,56,6,6 "
            + "2,56,58,58 2,57,6,6 2,57,58,58 2,58,6,6 2,58,58,58 2,59,6,6 2,59,58,58 2,60,6,6 2,60,58,58 2,61,6,6 "
            + "2,61,58,58 2,62,6,6 2,62,58,58 2,63,6,6 2,63,58,58 2,64,7,57 3,35,36,36 3,35,39,39 3,36,36,36 3,36,39,39 "
            + "3,37,36,36 3,37,39,39 3,38,7,36 3,38,39,57 3,39,6,6 3,39,58,58 3,40,6,6 3,40,58,58 3,41,6,6 3,41,58,58 "
            + "3,42,6,6 3,42,58,58 3,43,6,6 3,43,58,58 3,44,6,6 3,44,58,58 3,45,6,6 3,45,58,58 3,46,6,6 3,46,58,58 3,47,6,6 "
            + "3,47,58,58 3,48,6,6 3,48,58,58 3,49,6,6 3,49,58,58 3,50,6,6 3,50,58,58 3,51,6,6 3,51,58,58 3,52,6,6 "
            + "3,52,58,58 3,53,6,6 3,53,58,58 3,54,6,6 3,54,58,58 3,55,6,6 3,55,58,58 3,56,6,6 3,56,58,58 3,57,6,6 "
            + "3,57,58,58 3,58,6,6 3,58,58,58 3,59,6,6 3,59,58,58 3,60,6,6 3,60,58,58 3,61,6,6 3,61,58,58 3,62,6,6 "
            + "3,62,58,58 3,63,6,6 3,63,58,58 3,64,7,57 4,35,36,36 4,35,39,39 4,36,36,36 4,36,39,39 4,37,36,36 4,37,39,39 "
            + "4,38,7,36 4,38,39,57 4,39,6,6 4,39,58,58 4,40,6,6 4,40,58,58 4,41,6,6 4,41,58,58 4,42,6,6 4,42,58,58 4,43,6,6 "
            + "4,43,58,58 4,44,6,6 4,44,58,58 4,45,6,6 4,45,58,58 4,46,6,6 4,46,58,58 4,47,6,6 4,47,58,58 4,48,6,6 "
            + "4,48,58,58 4,49,6,6 4,49,58,58 4,50,6,6 4,50,58,58 4,51,6,6 4,51,58,58 4,52,6,6 4,52,58,58 4,53,6,6 "
            + "4,53,58,58 4,54,6,6 4,54,58,58 4,55,6,6 4,55,58,58 4,56,6,6 4,56,58,58 4,57,6,6 4,57,58,58 4,58,6,6 "
            + "4,58,58,58 4,59,6,6 4,59,58,58 4,60,6,6 4,60,58,58 4,61,6,6 4,61,58,58 4,62,6,6 4,62,58,58 4,63,6,6 "
            + "4,63,58,58 4,64,7,57 5,35,36,39 5,36,36,39 5,37,36,39 5,38,7,57 5,39,6,6 5,39,58,58 5,40,6,6 5,40,58,58 "
            + "5,41,6,6 5,41,58,58 5,42,6,6 5,42,58,58 5,43,6,6 5,43,58,58 5,44,6,6 5,44,58,58 5,45,6,6 5,45,58,58 5,46,6,6 "
            + "5,46,58,58 5,47,6,6 5,47,58,58 5,48,6,6 5,48,58,58 5,49,6,6 5,49,58,58 5,50,6,6 5,50,58,58 5,51,6,6 "
            + "5,51,58,58 5,52,6,6 5,52,58,58 5,53,6,6 5,53,58,58 5,54,6,6 5,54,58,58 5,55,6,6 5,55,58,58 5,56,6,6 "
            + "5,56,58,58 5,57,6,6 5,57,58,58 5,58,6,6 5,58,58,58 5,59,6,6 5,59,58,58 5,60,6,6 5,60,58,58 5,61,6,6 "
            + "5,61,58,58 5,62,6,6 5,62,58,58 5,63,6,6 5,63,58,58 5,64,7,57 6,38,7,57 6,39,6,6 6,39,58,58 6,40,6,6 "
            + "6,40,58,58 6,41,6,6 6,41,58,58 6,42,6,6 6,42,58,58 6,43,6,6 6,43,58,58 6,44,6,6 6,44,58,58 6,45,6,6 "
            + "6,45,58,58 6,46,6,6 6,46,58,58 6,47,6,6 6,47,58,58 6,48,6,6 6,48,58,58 6,49,6,6 6,49,58,58 6,50,6,6 "
            + "6,50,58,58 6,51,6,6 6,51,58,58 6,52,6,6 6,52,58,58 6,53,6,6 6,53,58,58 6,54,6,6 6,54,58,58 6,55,6,6 "
            + "6,55,58,58 6,56,6,6 6,56,58,58 6,57,6,6 6,57,58,58 6,58,6,6 6,58,58,58 6,59,6,6 6,59,58,58 6,60,6,6 "
            + "6,60,58,58 6,61,6,6 6,61,58,58 6,62,6,6 6,62,58,58 6,63,6,6 6,63,58,58 6,64,7,57 7,38,7,57 7,39,6,6 "
            + "7,39,58,58 7,40,6,6 7,40,58,58 7,41,6,6 7,41,58,58 7,42,6,6 7,42,58,58 7,43,6,6 7,43,58,58 7,44,6,6 "
            + "7,44,58,58 7,45,6,6 7,45,58,58 7,46,6,6 7,46,58,58 7,47,6,6 7,47,58,58 7,48,6,6 7,48,58,58 7,49,6,6 "
            + "7,49,58,58 7,50,6,6 7,50,58,58 7,51,6,6 7,51,58,58 7,52,6,6 7,52,58,58 7,53,6,6 7,53,58,58 7,54,6,6 "
            + "7,54,58,58 7,55,6,6 7,55,58,58 7,56,6,6 7,56,58,58 7,57,6,6 7,57,58,58 7,58,6,6 7,58,58,58 7,59,6,6 "
            + "7,59,58,58 7,60,6,6 7,60,58,58 7,61,6,6 7,61,58,58 7,62,6,6 7,62,58,58 7,63,6,6 7,63,58,58 7,64,7,57 "
            + "8,38,7,57 8,39,6,6 8,39,17,28 8,39,31,33 8,39,35,35 8,39,37,39 8,39,41,44 8,39,46,46 8,39,58,58 8,40,6,6 "
            + "8,40,58,58 8,41,6,6 8,41,58,58 8,42,6,6 8,42,58,58 8,43,6,6 8,43,58,58 8,44,6,6 8,44,58,58 8,45,6,6 "
            + "8,45,58,58 8,46,6,6 8,46,58,58 8,47,6,6 8,47,58,58 8,48,6,6 8,48,58,58 8,49,6,6 8,49,58,58 8,50,6,6 "
            + "8,50,58,58 8,51,6,6 8,51,58,58 8,52,6,6 8,52,58,58 8,53,6,6 8,53,58,58 8,54,6,6 8,54,58,58 8,55,6,6 "
            + "8,55,58,58 8,56,6,6 8,56,58,58 8,57,6,6 8,57,58,58 8,58,6,6 8,58,58,58 8,59,6,6 8,59,58,58 8,60,6,6 "
            + "8,60,58,58 8,61,6,6 8,61,58,58 8,62,6,6 8,62,58,58 8,63,6,6 8,63,58,58 8,64,7,57 9,39,7,16 9,39,29,30 "
            + "9,39,34,34 9,39,36,36 9,39,40,40 9,39,45,45 9,39,47,57 9,40,7,57 9,41,7,57 9,42,7,57 9,43,7,57 9,44,7,57 "
            + "9,45,7,57 9,46,7,57 9,47,7,57 9,48,7,57 9,49,7,57 9,50,7,57 9,51,7,57 9,52,7,57 9,53,7,57 9,54,7,57 9,55,7,57 "
            + "9,56,7,57 9,57,7,57 9,58,7,57 9,59,7,57 9,60,7,57 9,61,7,57 9,62,7,57 9,63,7,57 ";
    // END GENERATED

    /** In the room at the back ("the way out", build_world/ai_l2_refuge.py): the note's lectern, and the chest beside it. */
    static final BlockPos NOTE = new BlockPos(21, 2, 60);
    static final BlockPos PEARLS = new BlockPos(22, 2, 60);
    static final BlockPos CANDLES = new BlockPos(20, 2, 60);
    /** How many Lode Pearls they left. */
    public static final int LEFT = 3;

    /**
     * What the last of them left at the back of the refuge for whoever came after (the user, 2026-10-19): a note on a
     * lectern - their words, the user's, as given: "ここにたどり着いたということは、彼を眠らせてくれたのか？感謝する。
     * 我々は地上から避難することにした。人類を絶やしてはいけない" - and in the chest beside it {@link #LEFT} Lode Pearls bound to
     * the lodestone where they went ({@link Ascent}) - Nookworks' own (the user, 2026-10-21; it was a pearl of this mod's). Set down after the deep part is placed ({@code under}, {@code settings} as it was).
     */
    public static void leftBehind(net.minecraft.world.level.WorldGenLevel level, BlockPos under, StructurePlaceSettings settings, BoundingBox box) {
        BlockPos note = StructureTemplate.calculateRelativePosition(settings, NOTE).offset(under);
        BlockPos chest = StructureTemplate.calculateRelativePosition(settings, PEARLS).offset(under);
        if (box.isInside(note)) {
            net.minecraft.core.Direction facing = settings.getRotation().rotate(settings.getMirror().mirror(net.minecraft.core.Direction.NORTH));
            level.setBlock(note, net.minecraft.world.level.block.Blocks.LECTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LecternBlock.FACING, facing)
                    .setValue(net.minecraft.world.level.block.LecternBlock.HAS_BOOK, true), 2);
            if (level.getBlockEntity(note) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
                lectern.setBook(note());
            }
            // a light left beside it for whoever came: a barrel, candles lit on it (the visual review, 2026-10-19)
            BlockPos stand = StructureTemplate.calculateRelativePosition(settings, CANDLES).offset(under);
            if (box.isInside(stand) && level.getBlockState(stand).isAir() && level.getBlockState(stand.above()).isAir()) {
                level.setBlock(stand, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, net.minecraft.core.Direction.UP), 2);
                level.setBlock(stand.above(), net.minecraft.world.level.block.Blocks.CANDLE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                        .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true), 2);
            }
        }
        if (box.isInside(chest) && level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity stash) {
            Ascent.Site site = Ascent.site(level.getSeed());
            int slot = 13;
            for (int i = 0; i < stash.getContainerSize() && !stash.getItem(slot).isEmpty(); i++) {
                slot = i;                                           // into a free slot if something lies in the middle one
            }
            if (stash.getItem(slot).isEmpty()) {
                stash.setItem(slot, dev.nookworks.pearl.LodePearlItem.bound(
                        net.minecraft.core.GlobalPos.of(dev.nookworks.ifworld.IfWorld.KEY, site.lodestone())).copyWithCount(LEFT));
                stash.setChanged();
            }
            for (int i = 0; i < stash.getContainerSize(); i++) {        // and how they made the suppressant (the story's review)
                if (stash.getItem(i).isEmpty()) {
                    stash.setItem(i, Notes.book(Notes.PREPARATION));
                    stash.setChanged();
                    break;
                }
            }
        }
    }

    /** How long the refuge's chest takes to have a Lode Pearl again for one with none (a day of the game). */
    public static final long REFILL = 24000;

    /**
     * Never without a way on (the user, 2026-10-23: "第二研究所行きのロードパールもうっかり無くして詰むことが無いように"):
     * one in the refuge who has no Lode Pearl to the second institute on them, while its chest has none either, finds one
     * there again - once a game day for that chest, so the refuge is no well of pearls. The chest itself put back if it
     * went.
     */
    static void wayOn(net.minecraft.server.level.ServerPlayer player, ServerLevel level, BlockPos at) {
        net.minecraft.core.GlobalPos to = net.minecraft.core.GlobalPos.of(dev.nookworks.ifworld.IfWorld.KEY, Ascent.site(level.getSeed()).lodestone());
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> pearl = s -> s.is(dev.nookworks.registry.ModItems.LODE_PEARL.get())
                && dev.nookworks.pearl.LodePearlItem.lodestone(s).map(to::equals).orElse(false);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (pearl.test(player.getInventory().getItem(i))) {
                return;
            }
        }
        if (!level.isLoaded(at)) {
            return;
        }
        if (level.getBlockState(at).isAir()) {
            level.setBlock(at, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        }
        if (!(level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity stash)) {
            return;
        }
        int free = -1;
        for (int i = 0; i < stash.getContainerSize(); i++) {
            if (pearl.test(stash.getItem(i))) {
                return;
            }
            if (free < 0 && stash.getItem(i).isEmpty()) {
                free = i;
            }
        }
        long now = level.getGameTime();
        long last = stash.getPersistentData().getLongOr("nookofthedead_refilled", Long.MIN_VALUE / 2);
        if (free < 0 || now - last < REFILL) {
            return;
        }
        stash.setItem(free, dev.nookworks.pearl.LodePearlItem.bound(to));
        stash.getPersistentData().putLong("nookofthedead_refilled", now);
        stash.setChanged();
        dev.nookofthedead.NookOfTheDead.LOGGER.info("The refuge's chest at {} has a Lode Pearl to the second institute again", at);
    }

    /**
     * The lodestones the road's Lode Pearls are bound to - the second institute's, the relays' - put back if they went
     * (broken for the taking): a pearl bound to one gone is only an ender pearl (Nookworks), and the way on with it.
     */
    static void standing(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.level();
        Ascent.Site site = Ascent.site(level.getSeed());
        java.util.List<BlockPos> stones = new java.util.ArrayList<>(java.util.List.of(site.lodestone()));
        for (int deck : new int[] {Tower.R1, Tower.R2, Tower.R3}) {
            stones.add(Tower.lodestone(site, deck));
        }
        for (BlockPos stone : stones) {
            if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(stone)) < 48 * 48 && level.isLoaded(stone)
                    && !level.getBlockState(stone).is(net.minecraft.world.level.block.Blocks.LODESTONE)
                    && level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                            new net.minecraft.world.phys.AABB(stone).inflate(1.5)).isEmpty()) {
                level.setBlock(stone, net.minecraft.world.level.block.Blocks.LODESTONE.defaultBlockState(), 3);
            }
        }
    }

    /**
     * A refuge made before 0.3 has no note and no pearls (the review, 2026-10-19: a player who had beaten the Vandal under
     * 0.2 had no way on). The first time a player stands in a refuge, it is given them if it has no note yet - once only for
     * each refuge (kept with the world, {@link Retrofitted}), so a note broken later brings back no pearls.
     */
    @net.neoforged.bus.api.SubscribeEvent
    static void onTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) || player.tickCount % 100 != 0) {
            return;
        }
        sealOld(player);
        RefugeRooms.retrofit(player);
        RefugeRooms.watch(player);
        SkyBase.refit(player);
        if (dev.nookofthedead.NookOfTheDead.fallen(player.level())) {
            standing(player);
        }
        if (!inside(player.level(), player.blockPosition())) {
            return;
        }
        ServerLevel level = player.level();
        int bx = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        Town.Lot lot = Town.lots(level.getSeed(), bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
        if (lot == null || deep == null) {
            return;
        }
        BlockPos under = Town.deepOrigin(lot, deep);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        wayOn(player, level, StructureTemplate.calculateRelativePosition(settings, PEARLS).offset(under));
        Retrofitted done = level.getServer().getDataStorage().computeIfAbsent(Retrofitted.TYPE);
        String key = dev.nookworks.ifworld.IfWorld.get(level.getServer()).number() + ":" + bx + "," + bz;
        if (done.refuges.contains(key)) {
            return;
        }
        BlockPos note = StructureTemplate.calculateRelativePosition(settings, NOTE).offset(under);
        if (!level.getBlockState(note).is(net.minecraft.world.level.block.Blocks.LECTERN)) {
            leftBehind(level, under, settings, BoundingBox.infinite());
            dev.nookofthedead.NookOfTheDead.LOGGER.info("The refuge at {} given its note (made before 0.3)", note);
        }
        done.refuges.add(key);
        done.setDirty();
    }

    /**
     * The machine hall's empty north half made a workshop (the user, 2026-10-20: the refuge's circuit "もっとゴージャス", and
     * there "好きな機械を置きまくっていい"): along its north wall, between the two ways in, Nookworks' machines of 1.1 and
     * before - two Generators and a third at the end, Electric Furnaces, Crushers, Compressors, a Printing Press, a Repair
     * Box and two Recyclers - on a line of Energy Cable of their own laid in front of them, joined as they would be set
     * down by hand; a barrel of coal for the Generators; scrap stacked in the corner for the Recyclers and Compressors.
     * In the template's own blocks (x along the hall, z its depth, y its floor at 2).
     */
    public static void workshop(net.minecraft.world.level.WorldGenLevel level, BlockPos under, StructurePlaceSettings settings, BoundingBox box) {
        net.minecraft.core.Direction south = settings.getRotation().rotate(settings.getMirror().mirror(net.minecraft.core.Direction.SOUTH));
        net.minecraft.world.level.block.Block[] row = {
                dev.nookworks.registry.ModBlocks.GENERATOR.get(), dev.nookworks.registry.ModBlocks.GENERATOR.get(),
                dev.nookworks.registry.ModBlocks.ELECTRIC_FURNACE.get(), dev.nookworks.registry.ModBlocks.ELECTRIC_FURNACE.get(),
                dev.nookworks.registry.ModBlocks.CRUSHER.get(), dev.nookworks.registry.ModBlocks.CRUSHER.get(),
                dev.nookworks.registry.ModRecycling.COMPRESSOR.get(), dev.nookworks.registry.ModRecycling.COMPRESSOR.get(),
                dev.nookworks.registry.ModBlocks.PRINTING_PRESS.get(), dev.nookworks.registry.ModBlocks.REPAIR_BOX.get(),
                dev.nookworks.registry.ModRecycling.RECYCLER.get(), dev.nookworks.registry.ModRecycling.RECYCLER.get(),
                dev.nookworks.registry.ModBlocks.GENERATOR.get()};
        java.util.List<BlockPos> cables = new java.util.ArrayList<>();
        for (int i = 0; i < row.length; i++) {
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, new BlockPos(34 + i, 2, 48)).offset(under);
            net.minecraft.world.level.block.state.BlockState state = row[i].defaultBlockState();
            if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, south);
            }
            if (box.isInside(at) && level.getBlockState(at).isAir()) {
                level.setBlock(at, state, 2);
                if (row[i] instanceof dev.nookworks.recycling.RecyclerBlock && level.getBlockState(at.above()).isAir()) {
                    level.setBlock(at.above(), state.setValue(dev.nookworks.recycling.RecyclerBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                }
            }
            BlockPos cable = StructureTemplate.calculateRelativePosition(settings, new BlockPos(34 + i, 2, 49)).offset(under);
            if (box.isInside(cable) && level.getBlockState(cable).isAir()) {
                level.setBlock(cable, dev.nookworks.registry.ModBlocks.ENERGY_CABLE.get().defaultBlockState(), 2);
                cables.add(cable);
            }
        }
        for (BlockPos cable : cables) {                                     // joined to each other and to the machines, as set by hand
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cable);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                net.minecraft.world.level.block.state.BlockState next = level.getBlockState(cable.relative(d));
                boolean join = next.getBlock() instanceof dev.nookworks.block.EnergyCableBlock
                        || java.util.Arrays.asList(row).contains(next.getBlock())
                        && !(next.getBlock() instanceof dev.nookworks.recycling.RecyclerBlock
                        && next.getValue(dev.nookworks.recycling.RecyclerBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
                state = state.setValue(net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION.get(d), join);
            }
            level.setBlock(cable, state, 2);
        }
        BlockPos coal = StructureTemplate.calculateRelativePosition(settings, new BlockPos(33, 2, 48)).offset(under);
        if (box.isInside(coal) && level.getBlockState(coal).isAir()) {
            level.setBlock(coal, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, south), 2);
            if (level.getBlockEntity(coal) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
                for (int slot = 0; slot < 4; slot++) {
                    barrel.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 64));
                }
            }
        }
        long h = 0x5C4A9L;                                                  // the scrap in the corner: bales two high, sheets against them
        for (int x = 26; x <= 28; x++) {
            for (int z = 48; z <= 50; z++) {
                h = h * 6364136223846793005L + x * 31L + z;
                int high = (x == 26 || z == 48) ? 2 : (x == 27 && z == 49) ? 1 : 0;
                for (int y = 2; y < 2 + high; y++) {
                    BlockPos at = StructureTemplate.calculateRelativePosition(settings, new BlockPos(x, y, z)).offset(under);
                    if (box.isInside(at) && level.getBlockState(at).isAir()) {
                        level.setBlock(at, Props.bale(h >> (y * 3)), 2);
                    }
                }
            }
        }
        for (int z = 49; z <= 50; z++) {
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, new BlockPos(29, 2, z)).offset(under);
            net.minecraft.core.Direction east = settings.getRotation().rotate(settings.getMirror().mirror(net.minecraft.core.Direction.EAST));
            if (box.isInside(at) && level.getBlockState(at).isAir()) {
                level.setBlock(at, Props.sheet(z * 7919L, east, east.getAxis(), false), 2);
            }
        }
    }

    /** How near a player comes before a refuge made before the bulkheads is sealed in them: well before one could dig to it. */
    static final int SEAL_FROM = 48;

    /**
     * A refuge made before the bulkheads (0.3, the user, 2026-10-19) is sealed in them the first time a player comes within
     * {@link #SEAL_FROM} of it, its chunks loaded round them - once only (kept with the world, {@link Retrofitted}). What a
     * player had dug already stays dug: only rock is turned to bulkhead.
     */
    static void sealOld(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.level();
        if (!dev.nookofthedead.NookOfTheDead.fallen(level)) {
            return;
        }
        int bx = Math.floorDiv(player.getBlockX(), Town.BLOCK);
        int bz = Math.floorDiv(player.getBlockZ(), Town.BLOCK);
        BoundingBox refuge = box(level, bx, bz);
        if (refuge == null || !refuge.inflatedBy(SEAL_FROM).isInside(player.blockPosition())) {
            return;
        }
        Retrofitted done = level.getServer().getDataStorage().computeIfAbsent(Retrofitted.TYPE);
        String key = "hull:" + dev.nookworks.ifworld.IfWorld.get(level.getServer()).number() + ":" + bx + "," + bz;
        BoundingBox around = refuge.inflatedBy(12);
        if (done.refuges.contains(key) || !level.hasChunksAt(around.minX(), around.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
            return;
        }
        Town.Lot lot = Town.lots(level.getSeed(), bx, bz).stream().filter(l -> l.building().map(Town.INSTITUTE::equals).orElse(false))
                .findFirst().orElse(null);
        StructureTemplate deep = level.getServer().getStructureTemplateManager().get(Town.INSTITUTE_DEEP).orElse(null);
        if (lot == null || deep == null) {
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        if (!RefugeRooms.drawn(level, Town.deepOrigin(lot, deep), settings)) {   // one made with its rooms was sealed as it was made
            hull(level, Town.deepOrigin(lot, deep), settings, BoundingBox.infinite());
        }
        done.refuges.add(key);
        done.setDirty();
        dev.nookofthedead.NookOfTheDead.LOGGER.info("The refuge at {} sealed in bulkheads (made before them)", refuge.getCenter());
    }

    /** The refuges already seen to (by If number and block). */
    static final class Retrofitted extends net.minecraft.world.level.saveddata.SavedData {
        static final com.mojang.serialization.Codec<Retrofitted> CODEC = com.mojang.serialization.Codec.STRING.listOf()
                .xmap(list -> new Retrofitted(new java.util.HashSet<>(list)), r -> java.util.List.copyOf(r.refuges));
        static final net.minecraft.world.level.saveddata.SavedDataType<Retrofitted> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
                dev.nookofthedead.NookOfTheDead.id("refuges_seen"), () -> new Retrofitted(new java.util.HashSet<>()), CODEC);
        final java.util.Set<String> refuges;

        Retrofitted(java.util.Set<String> refuges) {
            this.refuges = refuges;
        }
    }

    /** Their note: a written book, its one page in the reader's language. */
    public static net.minecraft.world.item.ItemStack note() {
        var book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
        book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough("Note"), "?", 3,
                java.util.List.of(net.minecraft.server.network.Filterable.passThrough(
                        net.minecraft.network.chat.Component.translatable("book.nookofthedead.refuge_note"))), true));
        book.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable("item.nookofthedead.refuge_note"));
        return book;
    }

    private Refuge() {}
}
