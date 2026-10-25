package dev.nookofthedead.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The church of the ones who called it salvation (diary 5, STORY.md: "広場で、あれを「救い」と呼ぶ人たちを見た。噛まれた人の
 * 前にひざまずいて、歌っていた"; the user, 2026-10-23, of a cult church, told as traces only - nobody is left
 * to be one of them). In some of the town's churches: the pews pushed back from the middle of the nave, and there the
 * bitten one they knelt to, standing still (one of the dead, to come out as one comes in); round it a ring of them in
 * white, knelt, never got up; white candles burnt down between; white rags strewn and white banners stood about the nave;
 * on a lectern before the ring what they sang ({@link Notes#SALVATION}).
 */
public final class Salvation {
    /**
     * The church it can be, and where in it (the template's own blocks): the ruined church (m3), its nave's middle on the
     * aisle (the chapel is too small for it), and the nave the rags are strewn over.
     */
    static final String CHURCH = "town/m3";
    static final BlockPos MIDDLE = new BlockPos(39, 3, 24);
    static final BoundingBox NAVE = new BoundingBox(33, 3, 11, 46, 3, 36);
    /** Of those, how many in a hundred were theirs. */
    public static final int SHARE = 50;
    /** How far out the ring of them knelt, and the candles. */
    static final int RING = 3;
    static final int CANDLES = 2;

    /** Whether {@code lot}'s church was theirs. */
    public static boolean theirs(Town.Lot lot) {
        return lot.building().map(id -> CHURCH.equals(id.getPath())).orElse(false)
                && Math.floorMod(Town.hash(lot.seed(), 2, 2, 174), 100) < SHARE;
    }

    /** For commands: before the ring in {@code lot}'s church, in the world, if it was theirs. */
    public static BlockPos where(net.minecraft.server.level.ServerLevel level, Town.Lot lot) {
        StructureTemplate template = lot.building().flatMap(id -> level.getServer().getStructureTemplateManager().get(id)).orElse(null);
        if (!theirs(lot) || template == null) {
            return null;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.rotation()).setMirror(lot.mirror());
        return StructureTemplate.calculateRelativePosition(settings, MIDDLE.south(RING + 3)).offset(Town.placedAt(lot, template));
    }

    /** Their church: on {@code lot}'s building placed at {@code origin}, inside {@code box}; not one fallen in or worn by sand. */
    static void draw(WorldGenLevel level, Town.Lot lot, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox box, boolean whole) {
        if (!whole || !theirs(lot)) {
            return;
        }
        long h = Town.hash(lot.seed(), 3, 3, 175);
        Place place = new Place(level, origin, settings, box);
        for (int dx = -RING - 1; dx <= RING + 1; dx++) {                    // the pews pushed back to make room for the ring
            for (int dz = -RING - 1; dz <= RING + 1; dz++) {
                if (dx * dx + dz * dz <= (RING + 1) * (RING + 1)) {
                    place.clear(MIDDLE.offset(dx, 0, dz));
                }
            }
        }
        place.clear(MIDDLE.south(RING + 2));
        place.put(MIDDLE, ZombieMarkerBlock.of(1, false));                   // the one they knelt to
        for (int k = 0; k < 6; k++) {                                       // the ring of them, knelt, facing it
            double a = Math.PI * 2 * k / 6 + (h & 7) * 0.1;
            BlockPos at = MIDDLE.offset((int) Math.round(Math.cos(a) * RING), 0, (int) Math.round(Math.sin(a) * RING));
            place.kneeler(at, MIDDLE, h ^ k * 0x9E37L);
        }
        for (int k = 0; k < 8; k++) {                                       // the candles between, burnt down
            double a = Math.PI * 2 * k / 8 + 0.4;
            BlockPos at = MIDDLE.offset((int) Math.round(Math.cos(a) * CANDLES), 0, (int) Math.round(Math.sin(a) * CANDLES));
            place.put(at, Blocks.DYED_CANDLE.white().defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + (int) Math.floorMod(h >> k, 4L)));
        }
        RandomSource random = RandomSource.create(h);
        for (int x = NAVE.minX(); x <= NAVE.maxX(); x++) {                  // white rags strewn about, banners stood up
            for (int z = NAVE.minZ(); z <= NAVE.maxZ(); z++) {
                BlockPos p = new BlockPos(x, NAVE.minY(), z);
                if (p.distManhattan(MIDDLE) <= RING + 2) {
                    continue;
                }
                long r = Math.floorMod(Town.hash(lot.seed(), x, z, 176), 100);
                if (r < 12) {
                    place.put(p, Blocks.CARPET.white().defaultBlockState());
                } else if (r < 14) {
                    place.put(p, Blocks.BANNER.white().defaultBlockState().setValue(BlockStateProperties.ROTATION_16, random.nextInt(16)));
                } else if (r < 16) {
                    place.put(p, Blocks.DYED_CANDLE.white().defaultBlockState().setValue(BlockStateProperties.CANDLES, 1 + random.nextInt(3)));
                }
            }
        }
        BlockPos lectern = place.world(MIDDLE.south(RING + 2));              // their song, on a lectern before the ring
        if (place.free(lectern)) {
            Direction facing = settings.getRotation().rotate(settings.getMirror().mirror(Direction.SOUTH));
            level.setBlock(lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true), 2);
            if (level.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity stand) {
                stand.setBook(Notes.book(Notes.SALVATION));
            }
        }
    }

    /** The church's floor, in its template's blocks, set down in the world. */
    private record Place(WorldGenLevel level, BlockPos origin, StructurePlaceSettings settings, BoundingBox box) {
        BlockPos world(BlockPos local) {
            return StructureTemplate.calculateRelativePosition(this.settings, local).offset(this.origin);
        }

        boolean free(BlockPos at) {
            return this.box.isInside(at) && this.level.getBlockState(at).isAir()
                    && this.level.getBlockState(at.below()).isFaceSturdy(this.level, at.below(), Direction.UP);
        }

        /** A pew or a carpet at {@code local} taken away. */
        void clear(BlockPos local) {
            BlockPos at = this.world(local);
            if (this.box.isInside(at)) {
                BlockState was = this.level.getBlockState(at);
                if (was.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                        || was.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock) {
                    this.level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        void put(BlockPos local, BlockState state) {
            BlockPos at = this.world(local);
            if (this.free(at)) {
                this.level.setBlock(at, state, 2);
            }
        }

        /** One of them, knelt at {@code local} facing {@code toward}, in white. */
        void kneeler(BlockPos local, BlockPos toward, long seed) {
            BlockPos at = this.world(local);
            if (!this.free(at)) {
                return;
            }
            var body = dev.nookofthedead.registry.ZwEntities.REMAINS.get().create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (body == null) {
                return;
            }
            BlockPos to = this.world(toward);
            float yaw = (float) Math.toDegrees(Math.atan2(-(to.getX() - at.getX()), to.getZ() - at.getZ()));
            body.kind(dev.nookofthedead.entity.Remains.SITTING);
            body.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
            body.setYHeadRot(yaw);
            body.setYBodyRot(yaw);
            body.dress(RandomSource.create(seed));
            for (var piece : new net.minecraft.world.item.Item[] {Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS}) {
                ItemStack white = new ItemStack(piece);
                white.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0xE8E4D8));
                body.setItemSlot(piece == Items.LEATHER_CHESTPLATE ? EquipmentSlot.CHEST : EquipmentSlot.LEGS, white);
            }
            body.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            this.level.addFreshEntity(body);
        }
    }

    private Salvation() {}
}
