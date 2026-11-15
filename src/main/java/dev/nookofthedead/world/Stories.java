package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * What happened in a building, over the building as its builder made it (1.2, the user, 2026-11-15: 「細部やバリエーション」;
 * and before, of the town, 「差分やバリエーションを加えて、没入感を」「同じ建物でも同じに見えにくくする」): in some of the town's
 * houses, shops and works one of a few endings is left in its rooms - drawn from the building's own floors, doors and
 * containers, so it fits any building, turned and mirrored with it:
 * <ul>
 * <li>{@link Story#RANSACKED}: gone through for what was in it - its containers opened and emptied, its doors left open,
 *     books thrown down, the rugs pulled up;</li>
 * <li>{@link Story#BARRICADED}: held - its windows sheeted over, its ground-floor doors piled shut from inside, a bed and
 *     their stores upstairs; and the one door they came through, broken in, blood inside it;</li>
 * <li>{@link Story#NEST}: the dead's - bones and blood on the floors, webs in the corners, some of them still in it;</li>
 * <li>{@link Story#LAST_STAND}: someone fought on at the top of it - their body with their weapon, their kit beside them,
 *     blood down the stairs' way; below, the door broken and the dead still there.</li>
 * </ul>
 * Not in the buildings people held out in to the last (they have their own, {@link Props#held}), the institute, the parks
 * and squares, a building burnt, fallen in or taken by the sand, nor under a variant (its own story).
 * Every choice is the lot's and the place's, never the chunk's, so a building drawn across chunks is one story.
 */
public final class Stories {
    public enum Story { RANSACKED, BARRICADED, NEST, LAST_STAND }

    /** Of the buildings that may have one, how many in a hundred have a story. */
    public static final int CHANCE = 45;
    /** Each story's weight. */
    static final int[] WEIGHTS = {35, 30, 20, 15};
    /** The buildings with no story (besides the held ones and the institute): a park, a square, a fountain, a well, a memorial. */
    static final Set<String> NONE = Set.of("xs1", "xs2", "xs3", "xs4", "xs5", "s1", "s7", "s8", "m4", "m5");

    /** The story of a building at {@code building} on a lot of {@code seed}, if it has one. */
    public static Optional<Story> of(Optional<Identifier> building, long seed) {
        if (building.isEmpty() || !building.get().getNamespace().equals(dev.nookofthedead.NookOfTheDead.MODID)
                || !building.get().getPath().startsWith("town/")) {
            return Optional.empty();
        }
        String name = building.get().getPath().substring(5);
        if (NONE.contains(name) || Props.SHELTERS.contains(building.get().getPath()) || Props.LIBRARY.equals(building.get().getPath())
                || name.startsWith("institute")) {
            return Optional.empty();
        }
        long h = Town.hash(seed, 0x570L, 0x21EL, 0x5707L);
        if (Math.floorMod(h, 100L) >= CHANCE) {
            return Optional.empty();
        }
        int total = 0;
        for (int w : WEIGHTS) {
            total += w;
        }
        int pick = (int) Math.floorMod(h >> 12, (long) total);
        for (int i = 0; i < WEIGHTS.length; i++) {
            pick -= WEIGHTS[i];
            if (pick < 0) {
                return Optional.of(Story.values()[i]);
            }
        }
        return Optional.empty();
    }

    /** What a template offers a story, in the template: its floors, its ground floor's, its top floor's, its doors. */
    public record Rooms(List<BlockPos> floors, List<BlockPos> ground, List<BlockPos> top, List<Door> doors, Set<BlockPos> under) {}

    /** A ground-floor door (its lower half, in the template) and its way in: the side its building's floor is on. */
    public record Door(BlockPos at, Direction in) {}

    private static final Map<StructureTemplate, Rooms> ROOMS = Collections.synchronizedMap(new WeakHashMap<>());

    public static Rooms rooms(StructureTemplate template) {
        return ROOMS.computeIfAbsent(template, t -> {
            List<BlockPos> floors = Markers.floors(t);
            Set<BlockPos> under = new HashSet<>(floors);
            int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
            for (BlockPos p : floors) {
                low = Math.min(low, p.getY());
                high = Math.max(high, p.getY());
            }
            List<BlockPos> ground = new ArrayList<>(), top = new ArrayList<>();
            for (BlockPos p : floors) {
                if (p.getY() <= low + 1) {
                    ground.add(p);
                }
                if (high - low >= 4 && p.getY() >= high - 1) {
                    top.add(p);
                }
            }
            List<Door> doors = new ArrayList<>();
            StructurePlaceSettings plain = new StructurePlaceSettings();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!(block instanceof DoorBlock)) {
                    continue;
                }
                for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, plain, block, false)) {
                    if (info.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || info.pos().getY() > low + 1) {
                        continue;
                    }
                    Direction facing = info.state().getValue(DoorBlock.FACING);
                    for (Direction side : new Direction[] {facing, facing.getOpposite()}) {
                        if (under.contains(info.pos().relative(side))) {
                            doors.add(new Door(info.pos(), side));
                            break;
                        }
                    }
                }
            }
            doors.sort(java.util.Comparator.comparingLong(d -> d.at().asLong()));
            return new Rooms(List.copyOf(floors), List.copyOf(ground), List.copyOf(top), List.copyOf(doors), Set.copyOf(under));
        });
    }

    /** Whether {@code story} sheets the building's windows over (set as it is placed: {@link Props#boarded(int)}). */
    public static boolean boards(Optional<Story> story) {
        return story.map(s -> s == Story.BARRICADED).orElse(false);
    }

    /** {@code story} drawn in the building {@code template}, placed at {@code origin} with {@code settings}, inside {@code box}. */
    public static void draw(WorldGenLevel level, Story story, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox box, long seed) {
        Rooms rooms = rooms(template);
        if (rooms.floors().isEmpty()) {
            return;
        }
        Place place = new Place(level, template, origin, settings, box, seed, rooms);
        switch (story) {
            case RANSACKED -> place.ransacked();
            case BARRICADED -> place.barricaded();
            case NEST -> place.nest();
            case LAST_STAND -> place.lastStand();
        }
    }

    /** One building's story being drawn. */
    private record Place(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed, Rooms rooms) {
        BlockPos world(BlockPos local) {
            return StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
        }

        Direction world(Direction local) {
            return settings.getRotation().rotate(settings.getMirror().mirror(local));
        }

        /** A number for {@code local} and {@code salt}: the same from every chunk. */
        long h(BlockPos local, long salt) {
            return Town.hash(seed ^ salt, local.getX(), local.getZ() * 4099L + local.getY(), salt);
        }

        boolean chance(BlockPos local, long salt, int perMille) {
            return Math.floorMod(h(local, salt), 1000L) < perMille;
        }

        /** Whether one can set something down at {@code at} (in the world, inside the box): air, on a full floor. */
        boolean free(BlockPos at) {
            return box.isInside(at) && level.getBlockState(at).isAir() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
                    && !Markers.taken(at);
        }

        void put(BlockPos at, BlockState state) {
            if (box.isInside(at)) {
                level.setBlock(at, state, 2);
            }
        }

        /** The way out from the wall beside {@code at}, if one is there; else a way from the place's number. */
        Direction offWall(BlockPos at, long h) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos behind = at.relative(side);
                if (level.getBlockState(behind).isFaceSturdy(level, behind, side.getOpposite())) {
                    return side.getOpposite();
                }
            }
            return Direction.from2DDataValue((int) Math.floorMod(h, 4L));
        }

        boolean byWall(BlockPos at) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos behind = at.relative(side);
                if (level.getBlockState(behind).isFaceSturdy(level, behind, side.getOpposite())) {
                    return true;
                }
            }
            return false;
        }

        /** {@code n} places of {@code from} picked by the lot (the same from every chunk), by walls if {@code walled} can be. */
        List<BlockPos> pick(List<BlockPos> from, int n, long salt) {
            List<BlockPos> sorted = new ArrayList<>(from);
            sorted.sort(java.util.Comparator.comparingLong(p -> h(p, salt)));
            return sorted.subList(0, Math.min(n, sorted.size()));
        }

        /** Blood soaked into the floor under {@code local}, in blobs: the floor's block stained, where it is a whole block. */
        void blood(BlockPos local, long salt) {
            BlockPos at = world(local);
            Block stain = Dress.stain(seed ^ salt, at.getX(), at.getZ(), 0xB100DL);
            BlockPos floor = at.below();
            if (stain != null && box.isInside(floor) && level.getBlockState(at).isAir()
                    && level.getBlockState(floor).isCollisionShapeFullBlock(level, floor) && !level.getBlockState(floor).hasBlockEntity()) {
                level.setBlock(floor, stain.defaultBlockState(), 2);
            }
        }

        /** Some of the dead left standing in it, at {@code n} of {@code from}. */
        void dead(List<BlockPos> from, int n, long salt) {
            for (BlockPos local : pick(from, n, salt)) {
                BlockPos at = world(local);
                if (free(at) && level.getBlockState(at.above()).isAir()) {
                    Markers.make(level, at, offWall(at, h(local, salt)), "[zombie " + (1 + Math.floorMod(h(local, salt) >> 4, 2L)) + "]",
                            RandomSource.create(h(local, salt ^ 0xDEADL)));
                }
            }
        }

        /** One ground-floor door broken in: its leaves gone, blood just inside it. */
        void brokenIn(Door door, long salt) {
            BlockPos at = world(door.at());
            if (!box.isInside(at) || !(level.getBlockState(at).getBlock() instanceof DoorBlock)) {
                return;
            }
            put(at.above(), Blocks.AIR.defaultBlockState());
            put(at, Blocks.AIR.defaultBlockState());
            BlockPos in = door.at().relative(door.in());
            for (BlockPos near : BlockPos.betweenClosed(in.offset(-2, 0, -2), in.offset(2, 0, 2))) {
                if (rooms.under().contains(near) && chance(near, salt, 700)) {
                    BlockPos w = world(near.immutable());
                    BlockPos floor = w.below();
                    if (box.isInside(floor) && level.getBlockState(w).isAir() && level.getBlockState(floor).isCollisionShapeFullBlock(level, floor)
                            && !level.getBlockState(floor).hasBlockEntity()) {
                        level.setBlock(floor, (chance(near, salt ^ 1, 500) ? Blocks.DYED_TERRACOTTA.brown() : Blocks.DYED_TERRACOTTA.black()).defaultBlockState(), 2);
                    }
                }
            }
        }

        void ransacked() {
            long salt = 0x2A5AC;
            for (BlockPos local : Markers.autos(template)) {                // its own containers, most gone through
                BlockPos at = world(local);
                if (!box.isInside(at) || !chance(local, salt, 650)) {
                    continue;
                }
                if (level.getBlockEntity(at) instanceof RandomizableContainer container) {
                    container.setLootTable(null);
                    container.clearContent();
                }
                BlockState state = level.getBlockState(at);
                if (state.hasProperty(BarrelBlock.OPEN)) {
                    level.setBlock(at, state.setValue(BarrelBlock.OPEN, true), 2);
                }
            }
            for (Door door : rooms.doors()) {                               // the doors left open behind them
                BlockPos at = world(door.at());
                for (BlockPos half : new BlockPos[] {at, at.above()}) {
                    BlockState state = level.getBlockState(half);
                    if (box.isInside(half) && state.getBlock() instanceof DoorBlock) {
                        level.setBlock(half, state.setValue(DoorBlock.OPEN, true), 2);
                    }
                }
            }
            for (BlockPos local : rooms.floors()) {                         // the rugs pulled up, the books thrown down
                BlockPos at = world(local);
                if (!box.isInside(at)) {
                    continue;
                }
                if (level.getBlockState(at).is(BlockTags.WOOL_CARPETS) && chance(local, salt ^ 2, 450)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                } else if (chance(local, salt ^ 3, 22) && free(at)) {
                    RandomSource random = RandomSource.create(h(local, salt ^ 4));
                    Props.pile(level, box, at, 1 + random.nextInt(3), random.nextFloat() * 360, random,
                            () -> random.nextInt(6) == 0 ? new ItemStack(Items.WRITABLE_BOOK) : new ItemStack(Items.BOOK));
                }
            }
        }

        void barricaded() {
            long salt = 0xBA221C;
            List<Door> doors = rooms.doors();
            int breach = doors.isEmpty() ? -1 : (int) Math.floorMod(Town.hash(seed, 0xB2EAL, doors.size(), salt), (long) doors.size());
            for (int i = 0; i < doors.size(); i++) {
                Door door = doors.get(i);
                if (i == breach) {
                    brokenIn(door, salt);
                    continue;
                }
                BlockPos in = door.at().relative(door.in());                // piled shut from inside: a crate or a barrel, planks over it
                BlockPos at = world(in);
                if (!free(at)) {
                    continue;
                }
                long hh = h(in, salt);
                BlockState low = switch ((int) Math.floorMod(hh, 3L)) {
                    case 0 -> BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nookworks", "crate")).defaultBlockState();
                    case 1 -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
                    default -> Blocks.SPRUCE_PLANKS.defaultBlockState();
                };
                put(at, low);
                if (box.isInside(at.above()) && level.getBlockState(at.above()).isAir()) {
                    put(at.above(), ((hh >> 3) & 1) == 0 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.SPRUCE_SLAB.defaultBlockState());
                }
            }
            // where they slept and kept their stores: up the house if it has an upstairs
            List<BlockPos> rest = rooms.top().isEmpty() ? rooms.ground() : rooms.top();
            List<BlockPos> walled = new ArrayList<>();
            for (BlockPos local : rest) {
                if (byWall(world(local))) {
                    walled.add(local);
                }
            }
            for (BlockPos local : pick(walled.isEmpty() ? rest : walled, 1, salt ^ 5)) {
                BlockPos at = world(local);
                Direction out = offWall(at, h(local, salt));
                Block bed = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("straw_bed"));
                Direction along = out.getClockWise();
                BlockPos foot = at.relative(along);
                if (free(at) && free(foot) && bed.defaultBlockState().hasProperty(BedBlock.PART)) {
                    put(at, bed.defaultBlockState().setValue(BedBlock.FACING, along.getOpposite()).setValue(BedBlock.PART, BedPart.HEAD));
                    put(foot, bed.defaultBlockState().setValue(BedBlock.FACING, along.getOpposite()).setValue(BedBlock.PART, BedPart.FOOT));
                }
                BlockPos store = at.relative(along.getOpposite());
                if (free(store)) {
                    put(store, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                    if (level.getBlockEntity(store) instanceof RandomizableContainer barrel) {
                        barrel.setLootTable(Markers.table("stash_camp"), h(local, salt ^ 6));
                    }
                }
                BlockPos candle = store.relative(along.getOpposite());
                if (free(candle)) {
                    put(candle, Blocks.CANDLE.defaultBlockState());
                }
            }
        }

        void nest() {
            long salt = 0x4E57;
            for (BlockPos local : rooms.floors()) {
                BlockPos at = world(local);
                if (!box.isInside(at)) {
                    continue;
                }
                blood(local, salt);
                if (chance(local, salt ^ 1, 18) && free(at)) {
                    Dress.bones(level, box, at, h(local, salt ^ 2), false);
                }
                // webs up in the corners: the top of the room over it, where two walls meet
                if (chance(local, salt ^ 3, 120)) {
                    BlockPos up = at;
                    for (int i = 0; i < 6 && box.isInside(up.above()) && level.getBlockState(up.above()).isAir(); i++) {
                        up = up.above();
                    }
                    int walls = 0;
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        walls += level.getBlockState(up.relative(side)).isFaceSturdy(level, up.relative(side), side.getOpposite()) ? 1 : 0;
                    }
                    if (walls >= 2 && !up.equals(at) && level.getBlockState(up).isAir()) {
                        put(up, Blocks.COBWEB.defaultBlockState());
                    }
                }
            }
            dead(rooms.ground(), 2 + (int) Math.floorMod(seed >> 7, 2L), salt ^ 4);
        }

        void lastStand() {
            long salt = 0x1A57;
            List<BlockPos> up = rooms.top().isEmpty() ? rooms.ground() : rooms.top();
            List<BlockPos> walled = new ArrayList<>();
            for (BlockPos local : up) {
                if (byWall(world(local))) {
                    walled.add(local);
                }
            }
            // the first place there is room in, in the lot's order, by a wall where one is (a place taken by what was put there
            // is passed by)
            List<BlockPos> order = new ArrayList<>(pick(walled, walled.size(), salt));
            order.addAll(pick(up, up.size(), salt));
            for (BlockPos local : order) {
                BlockPos at = world(local);
                if (!box.isInside(at)) {
                    break;                                                  // another chunk's to draw: never two bodies
                }
                if (!free(at) || !level.getBlockState(at.above()).isAir()) {
                    continue;
                }
                Direction out = offWall(at, h(local, salt));
                BlockPos kit = null;                                        // room for their kit beside them, or another place
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (side != out.getOpposite() && free(at.relative(side))) {
                        kit = at.relative(side);
                        break;
                    }
                }
                if (kit == null) {
                    continue;
                }
                RandomSource random = RandomSource.create(h(local, salt ^ 1));
                ItemStack[] weapons = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.STONE_SPEAR), new ItemStack(Items.IRON_AXE),
                        new ItemStack(Items.CROSSBOW), new ItemStack(Items.COPPER_SWORD)};
                ItemStack weapon = weapons[random.nextInt(weapons.length)];
                weapon.setDamageValue(weapon.getMaxDamage() * (6 + random.nextInt(3)) / 10);
                put(kit, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));   // the kit first: the body lies clear of it
                if (level.getBlockEntity(kit) instanceof RandomizableContainer barrel) {
                    barrel.setLootTable(Markers.table("stash_guard"), random.nextLong());
                }
                Markers.remains(level, at, out, random, weapon);
                for (BlockPos near : BlockPos.betweenClosed(local.offset(-3, 0, -3), local.offset(3, 0, 3))) {
                    if (rooms.under().contains(near)) {
                        blood(near.immutable(), salt ^ 2);
                    }
                }
                break;
            }
            if (!rooms.doors().isEmpty()) {
                brokenIn(rooms.doors().get((int) Math.floorMod(seed >> 11, (long) rooms.doors().size())), salt ^ 3);
            }
            dead(rooms.ground(), 2, salt ^ 4);
        }
    }

    private Stories() {}
}
