package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The builder's marker signs (BUILDING.md, a word in [ ] on the first line) turned into what they mark as a building goes
 * in; the sign itself goes. In this prototype:
 * <ul>
 * <li>{@code [zombie]}, {@code [zombie 5]}: that many zombies, kept until someone comes near ({@link ZombieMarkerBlock});</li>
 * <li>{@code [elite]}: one fallen player, in diamond;</li>
 * <li>{@code [loot]}, {@code [loot food]} (medical, weapon, tools, supplies...): the chest or barrel next to it filled anew each time from
 * {@code nookofthedead:chests/<kind>} (what the builder put in it stays too); with none next to it, a chest there;</li>
 * <li>{@code [potion]}: three times in five, a chest that may hold a suppressant;</li>
 * <li>{@code [brew]}: the brewing stand beside it with what was left in it - often a suppressant (the user, 2026-10-15:
 * "醸造台に抑制ポーションがたまに入れっぱなし…研究所みたいなところに沢山"), sometimes water, an awkward potion, a wart;</li>
 * <li>{@code [diary]}: a lectern with a page of someone's diary ({@link Diaries});</li>
 * <li>{@code [vandal]}: the boss of a research institute ({@link dev.nookofthedead.entity.Vandal});</li>
 * <li>anything else ({@code [memo …]}, and {@code [villager]}, {@code [wolf]}, {@code [cow]} not made yet): nothing.</li>
 * </ul>
 * A template's markers are read once from its signs' first lines and kept, a {@code [loot]} marker with the container
 * beside it in the template: a town is made a chunk at a time, and the marker and its chest may fall in different chunks,
 * made in either order - each gets its part when its own chunk is made (r1: made first, the marker found no chest yet,
 * set one of its own, and the builder's chest stayed empty).
 * <p>
 * A chest or barrel the builder left empty and marked with nothing is filled too, {@link #FILL_EMPTY} times in a hundred,
 * with general loot ({@link #autos}; the user, 2026-10-15: "俺が建てた建物のなかのチェストにも、ルートテーブル入れていいよ");
 * what the builder put in one stays as it is.
 */
public final class Markers {
    /**
     * A marker sign in a template: where (in the template), the sign as it stands, its first line, and for a
     * {@code [loot]} sign the container beside it in the template, if there is one.
     */
    public record Marker(BlockPos local, BlockState sign, String text, @Nullable BlockPos container) {}

    private static final Map<StructureTemplate, List<Marker>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<StructureTemplate, List<BlockPos>> EMPTIES = Collections.synchronizedMap(new WeakHashMap<>());
    /** Of the empty, unmarked containers, how many in a hundred are filled. */
    public static final int FILL_EMPTY = 65;
    /**
     * Of the builder's empty Nookworks Crates, how many in a hundred have something left in them (the user, 2026-10-19:
     * "木箱にはいつも何も入っていない。たまにはなんか入れてあげて").
     */
    public static final int FILL_CRATE = 35;

    private static final net.minecraft.resources.Identifier CRATE = net.minecraft.resources.Identifier.fromNamespaceAndPath("nookworks", "crate");

    /** Whether {@code block} is Nookworks' Crate. */
    static boolean crate(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).equals(CRATE);
    }
    private static final Map<StructureTemplate, List<BlockPos>> FLOORS = Collections.synchronizedMap(new WeakHashMap<>());
    /** Of the buildings, how many in a hundred have someone's remains somewhere inside, on top of any the builder marked. */
    public static final int STRAYS = 35;
    /** Of those, how many in a hundred have what they carried set down beside them. */
    public static final int STRAY_SUPPLIES = 40;

    public static List<Marker> of(StructureTemplate template) {
        return CACHE.computeIfAbsent(template, Markers::read);
    }

    /** The chests and barrels of {@code template} the builder left empty and no {@code [loot]} sign marks (in the template). */
    public static List<BlockPos> autos(StructureTemplate template) {
        return EMPTIES.computeIfAbsent(template, t -> {
            java.util.Set<BlockPos> marked = new java.util.HashSet<>();
            for (Marker m : of(t)) {
                if (m.container() != null) {
                    marked.add(m.container());
                }
            }
            List<BlockPos> found = new ArrayList<>();
            StructurePlaceSettings plain = new StructurePlaceSettings();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (holdsLoot(block) && (block instanceof ChestBlock || block instanceof net.minecraft.world.level.block.BarrelBlock || crate(block))) {
                    for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, plain, block, false)) {
                        boolean empty = info.nbt() == null || info.nbt().getListOrEmpty("Items").isEmpty();
                        if (empty && (info.nbt() == null || !info.nbt().contains("LootTable")) && !marked.contains(info.pos())) {
                            found.add(info.pos());
                        }
                    }
                }
            }
            return List.copyOf(found);
        });
    }

    private static List<Marker> read(StructureTemplate template) {
        List<Marker> markers = new ArrayList<>();
        StructurePlaceSettings plain = new StructurePlaceSettings();
        java.util.Set<BlockPos> containers = new java.util.HashSet<>();
        java.util.Set<BlockPos> plainOnes = new java.util.HashSet<>();      // the game's own chests and barrels
        for (Block block : BuiltInRegistries.BLOCK) {
            if (holdsLoot(block)) {
                boolean vanilla = BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, plain, block, false)) {
                    containers.add(info.pos());
                    if (vanilla) {
                        plainOnes.add(info.pos());
                    }
                }
            }
        }
        java.util.Set<BlockPos> stands = new java.util.HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, plain, Blocks.BREWING_STAND, false)) {
            stands.add(info.pos());
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().is(BlockTags.ALL_SIGNS)) {
                continue;
            }
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, plain, block, false)) {
                String text = firstLine(info.nbt());
                if (text != null && text.startsWith("[")) {
                    BlockPos container = null;
                    String lower = text.toLowerCase(Locale.ROOT);
                    java.util.Set<BlockPos> beside = lower.startsWith("[loot") ? containers : lower.startsWith("[brew") ? stands : java.util.Set.of();
                    // the game's own chest or barrel beside it before anything else that holds things (the review,
                    // 2026-10-19: a Nookworks Crate found first was filled, the barrel the builder meant left empty)
                    for (int pass = 0; pass < 2 && container == null; pass++) {
                        for (Direction side : Direction.values()) {
                            BlockPos by = info.pos().relative(side);
                            if (beside.contains(by) && (pass == 1 || beside != containers || plainOnes.contains(by))) {
                                container = by;
                                break;
                            }
                        }
                    }
                    markers.add(new Marker(info.pos(), info.state(), text, container));
                }
            }
        }
        return List.copyOf(markers);
    }

    /** The blocks whose block entity takes a loot table (chests, barrels, shulker boxes, dispensers, pots...). */
    private static final java.util.function.Supplier<java.util.Set<Block>> LOOT_HOLDERS = com.google.common.base.Suppliers.memoize(() -> {
        java.util.Set<Block> found = new java.util.HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (block instanceof net.minecraft.world.level.block.EntityBlock entityBlock && state.hasBlockEntity()) {
                try {
                    if (entityBlock.newBlockEntity(BlockPos.ZERO, state) instanceof RandomizableContainer) {
                        found.add(block);
                    }
                } catch (RuntimeException ignored) {
                    // a block entity that cannot be made out of a world is no container of ours
                }
            }
        }
        return java.util.Set.copyOf(found);
    });

    private static boolean holdsLoot(Block block) {
        return LOOT_HOLDERS.get().contains(block);
    }

    /** A sign's first line as plain text, from its saved data (a plain string, or a text component's "text"). */
    static @Nullable String firstLine(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return null;
        }
        ListTag lines = nbt.getCompoundOrEmpty("front_text").getListOrEmpty("messages");
        if (lines.isEmpty()) {
            return null;
        }
        Tag line = lines.get(0);
        Optional<String> text = line instanceof CompoundTag component ? component.getString("text") : line.asString();
        return text.map(String::trim).orElse(null);
    }

    /** The markers of {@code template}, placed at {@code origin} with {@code settings}, that fall inside {@code box}. */
    static void apply(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed) {
        apply(level, template, origin, settings, box, seed, at -> false);
    }

    /** As {@link #apply}, but for the markers at a place (in the template) {@code passed} says a variant took over. */
    static void apply(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box,
            long seed, java.util.function.Predicate<BlockPos> passed) {
        for (Marker marker : of(template)) {
            if (passed.test(marker.local())) {
                continue;
            }
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, marker.local()).offset(origin);
            RandomSource random = RandomSource.create(seed ^ at.asLong());
            if (marker.container() != null) {
                // its chest's chunk fills the chest, its own chunk takes the sign away, whichever comes first
                BlockPos chest = StructureTemplate.calculateRelativePosition(settings, marker.container()).offset(origin);
                if (box.isInside(chest) && level.getBlockEntity(chest) instanceof RandomizableContainer container) {
                    fill(level, chest, container, table(kind(marker.text())), random.nextLong());
                }
                if (box.isInside(chest) && level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand) {
                    brew(stand, random);
                }
                if (box.isInside(at)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
                continue;
            }
            if (!box.isInside(at)) {
                continue;
            }
            BlockState sign = marker.sign().mirror(settings.getMirror()).rotate(settings.getRotation());
            make(level, at, facing(sign), marker.text(), random);
        }
        for (BlockPos local : autos(template)) {                             // the builder's empty chests, most of them filled
            if (passed.test(local)) {
                continue;
            }
            BlockPos at = StructureTemplate.calculateRelativePosition(settings, local).offset(origin);
            RandomSource random = RandomSource.create(seed ^ at.asLong() ^ 0xC4E57L);
            // the refuge's are as they were left: the machines' output chests empty, the stores as stocked (Refuge)
            if (box.isInside(at) && random.nextInt(100) < (crate(level.getBlockState(at).getBlock()) ? FILL_CRATE : FILL_EMPTY) && !Refuge.inside(level, at)
                    && level.getBlockEntity(at) instanceof RandomizableContainer container) {
                // what was left about the place, the suppressant in it rare (the review, 2026-10-19: a store-house's
                // hundred barrels held fifteen or twenty doses); medicine is where the builder marked it
                container.setLootTable(table("stash"), random.nextLong());
            }
        }
    }

    /**
     * Someone who never got out, in one building in three or so ({@link #STRAYS}; the user, 2026-10-16: "建物内や地下に…
     * 白骨化した死体。たまにそのそばに物資"): their remains on one of the building's floors under its roof - chosen the same
     * from every chunk, made by the chunk it falls in - and now and then what they carried in a barrel beside them.
     */
    static void stray(WorldGenLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox box, long seed) {
        RandomSource roll = RandomSource.create(seed ^ 0x5EB0D1E5L);
        List<BlockPos> floors = floors(template);
        if (floors.isEmpty() || roll.nextInt(100) >= STRAYS) {
            return;
        }
        BlockPos at = StructureTemplate.calculateRelativePosition(settings, floors.get(roll.nextInt(floors.size()))).offset(origin);
        if (!box.isInside(at) || !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()
                || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
            return;                                                      // a variant or the wear took the spot: no one there
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(roll);
        for (Direction side : Direction.Plane.HORIZONTAL) {               // back to a wall, if there is one beside
            BlockPos behind = at.relative(side);
            if (level.getBlockState(behind).isFaceSturdy(level, behind, side.getOpposite())) {
                facing = side.getOpposite();
                break;
            }
        }
        remains(level, at, facing, roll);
        if (roll.nextInt(100) >= STRAY_SUPPLIES) {
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos by = at.relative(side);
            if (side != facing.getOpposite() && box.isInside(by) && level.getBlockState(by).isAir()
                    && level.getBlockState(by.below()).isFaceSturdy(level, by.below(), Direction.UP)) {
                level.setBlock(by, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 2);
                if (level.getBlockEntity(by) instanceof RandomizableContainer barrel) {
                    String[] kinds = {"supplies", "food", "medical", "weapon", "general"};
                    barrel.setLootTable(table(kinds[roll.nextInt(kinds.length)]), roll.nextLong());
                }
                return;
            }
        }
    }

    /**
     * Where in {@code template} one could lie (kept): an empty place two high over something, with the building over it
     * within a few blocks - its floors, under its roof.
     */
    public static List<BlockPos> floors(StructureTemplate template) {
        return FLOORS.computeIfAbsent(template, t -> {
            StructurePlaceSettings plain = new StructurePlaceSettings();
            java.util.Set<BlockPos> air = new java.util.HashSet<>();
            for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, plain, Blocks.AIR, false)) {
                air.add(info.pos());
            }
            net.minecraft.core.Vec3i size = t.getSize();
            List<BlockPos> found = new ArrayList<>();
            for (BlockPos p : air) {
                if (p.getY() == 0 || !air.contains(p.above()) || air.contains(p.below())) {
                    continue;
                }
                for (int up = 2; up <= 6 && p.getY() + up < size.getY(); up++) {
                    if (!air.contains(p.above(up))) {
                        found.add(p);
                        break;
                    }
                }
            }
            found.sort(java.util.Comparator.comparingLong(BlockPos::asLong));  // the same order every time
            return List.copyOf(found);
        });
    }

    /**
     * What was left in a brewing stand: in each of its three places, a suppressant now and then (a splash one once in a
     * while), else water, an awkward potion, or nothing; a wart waiting to go in, a little blaze powder. One in two has at
     * least one suppressant.
     */
    public static void brew(net.minecraft.world.level.block.entity.BrewingStandBlockEntity stand, RandomSource random) {
        for (int slot = 0; slot < 3; slot++) {
            int r = random.nextInt(100);
            net.minecraft.world.item.ItemStack bottle;
            if (r < 20) {
                bottle = dev.nookofthedead.registry.ZwEffects.bottle(random.nextInt(5) == 0 ? net.minecraft.world.item.Items.SPLASH_POTION
                        : net.minecraft.world.item.Items.POTION);
            } else if (r < 46) {
                bottle = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.WATER);
            } else if (r < 60) {
                bottle = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                        net.minecraft.world.item.alchemy.Potions.AWKWARD);
            } else {
                bottle = net.minecraft.world.item.ItemStack.EMPTY;
            }
            stand.setItem(slot, bottle);
        }
        if (random.nextInt(4) == 0) {
            stand.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_WART, 1 + random.nextInt(3)));
        }
        if (random.nextInt(3) == 0) {
            stand.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER, 1 + random.nextInt(2)));
        }
    }

    /** What {@code text} marks, made at {@code at} (facing where the sign faced). */
    public static void make(WorldGenLevel level, BlockPos at, Direction facing, String text, RandomSource random) {
        String[] words = text.substring(1, text.endsWith("]") ? text.length() - 1 : text.length()).trim().toLowerCase(Locale.ROOT).split("\\s+");
        String what = words.length > 0 ? words[0] : "";
        String detail = words.length > 1 ? words[1] : "";
        // the sign goes first, its block entity with it: set straight over a block entity in a chunk being made, the new
        // block kept the sign's, and markers, lecterns and chests worked nowhere in the town (r1)
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        switch (what) {
            case "zombie" -> level.setBlock(at, ZombieMarkerBlock.of(number(detail, 1), false), 2);
            case "elite" -> level.setBlock(at, ZombieMarkerBlock.of(1, true), 2);
            case "vandal" -> vandal(level, at, facing);
            case "remains" -> remains(level, at, facing, random);
            case "zombie_villager" -> {
                for (int i = 0; i < number(detail, 1); i++) {
                    zombieVillager(level, at, facing, random);
                }
            }
            case "loot" -> loot(level, at, facing, detail.isEmpty() ? "general" : detail, random);
            case "potion" -> {
                if (random.nextInt(5) < 3) {
                    chest(level, at, facing, "potion", random);
                } else {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            case "diary" -> {
                level.setBlock(at, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing)
                        .setValue(LecternBlock.HAS_BOOK, true), 2);
                if (level.getBlockEntity(at) instanceof LecternBlockEntity lectern) {
                    lectern.setBook(Diaries.book(random));
                }
            }
            default -> level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /**
     * A survivor's remains where the sign was: slumped against the wall behind the sign if there is one, else lying; dressed
     * in what they had ({@link dev.nookofthedead.entity.Remains}). Lying, the body reaches the better part of a block past
     * its own each way along it, so it is laid along a way with room at both ends, and sat up where there is none (the user,
     * 2026-10-18: bodies sunk into hay bales and beds, laid at any angle across what stood by them).
     */
    public static void remains(WorldGenLevel level, BlockPos at, Direction facing, RandomSource random) {
        remains(level, at, facing, random, net.minecraft.world.item.ItemStack.EMPTY);
    }

    /** Bodies held back while a chunk of the capital is drawn: laid once all its columns are (null when not holding). */
    private static final ThreadLocal<java.util.List<Runnable>> DEFERRED = new ThreadLocal<>();

    /**
     * Holding back the bodies laid from now on this thread ({@code true}), or laying those held back and no longer holding:
     * a body looks at what stands beside it to lie or sit against, and in the capital, drawn a column at a time, the
     * column beside may not be drawn yet (the review: a body could lie across a wall drawn after it).
     */
    public static void defer(boolean hold) {
        if (hold) {
            DEFERRED.set(new java.util.ArrayList<>());
            return;
        }
        java.util.List<Runnable> held = DEFERRED.get();
        DEFERRED.remove();
        if (held != null) {
            held.forEach(Runnable::run);
        }
    }

    /** As {@link #remains(WorldGenLevel, BlockPos, Direction, RandomSource)}, {@code held} in its hand (a launch key). */
    public static void remains(WorldGenLevel level, BlockPos at, Direction facing, RandomSource random, net.minecraft.world.item.ItemStack held) {
        java.util.List<Runnable> later = DEFERRED.get();
        if (later != null) {
            BlockPos fixed = at.immutable();
            net.minecraft.world.item.ItemStack kept = held.copy();
            later.add(() -> lay(level, fixed, facing, random, kept));
            return;
        }
        lay(level, at, facing, random, held);
    }

    private static void lay(WorldGenLevel level, BlockPos at, Direction facing, RandomSource random, net.minecraft.world.item.ItemStack held) {
        dev.nookofthedead.entity.Remains body = dev.nookofthedead.registry.ZwEntities.REMAINS.get().create(level.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (body == null) {
            return;
        }
        BlockPos behind = at.relative(facing.getOpposite());
        boolean wall = known(level, at, behind) && level.getBlockState(behind).isFaceSturdy(level, behind, facing);
        Direction along = null;
        if (!wall) {
            Direction first = random.nextBoolean() ? Direction.NORTH : Direction.EAST;
            for (Direction way : new Direction[] {first, first.getClockWise()}) {
                if (room(level, at, at.relative(way)) && room(level, at, at.relative(way.getOpposite()))) {
                    along = random.nextBoolean() ? way : way.getOpposite();
                    break;
                }
            }
        }
        body.kind(along != null ? dev.nookofthedead.entity.Remains.LYING : dev.nookofthedead.entity.Remains.SITTING);
        // a little off square, as one fallen, never so far as to reach into the blocks beside
        float yaw = along != null ? along.toYRot() + (random.nextFloat() - 0.5F) * 16.0F : facing.toYRot();
        double back = wall ? -0.2 : 0.0;
        body.snapTo(at.getX() + 0.5 + facing.getStepX() * back, at.getY(), at.getZ() + 0.5 + facing.getStepZ() * back, yaw, 0.0F);
        body.setYHeadRot(yaw);
        body.setYBodyRot(yaw);
        body.dress(random);
        if (!held.isEmpty()) {
            body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held.copy());
        }
        level.addFreshEntity(body);
    }

    /** Whether a body lying on the floor can reach into {@code pos}: nothing there, or nothing higher than a carpet. */
    private static boolean room(WorldGenLevel level, BlockPos at, BlockPos pos) {
        if (!known(level, at, pos)) {
            return false;
        }
        net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
    }

    /**
     * Whether what stands at {@code pos} can be told while {@code at} is being made: in generation only within its own
     * chunk - the next one's building may not be set down yet and reads as air (the review, 2026-10-19: one body in four,
     * on a chunk's edge, still lay across hay put down after it). The same from every chunk order.
     */
    private static boolean known(WorldGenLevel level, BlockPos at, BlockPos pos) {
        return level instanceof net.minecraft.server.level.ServerLevel
                || (pos.getX() >> 4) == (at.getX() >> 4) && (pos.getZ() >> 4) == (at.getZ() >> 4);
    }

    /**
     * An ordinary zombie villager (vanilla's: curable with a golden apple and weakness) kept where the sign was, in whatever
     * trade it had - the traders shut in at their own stalls.
     */
    private static void zombieVillager(WorldGenLevel level, BlockPos at, Direction facing, RandomSource random) {
        net.minecraft.world.entity.monster.zombie.ZombieVillager villager = net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER.create(
                level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (villager == null) {
            return;
        }
        villager.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, facing.toYRot(), 0.0F);
        // an adult, alone: no baby, no chicken to ride
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE,
                new net.minecraft.world.entity.monster.zombie.Zombie.ZombieGroupData(false, false));
        level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getRandom(random)
                .ifPresent(job -> villager.setVillagerData(villager.getVillagerData().withProfession(job)));
        villager.setBaby(false);
        villager.setCanPickUpLoot(false);
        villager.setPersistenceRequired();
        level.addFreshEntity(villager);
    }

    /** The Vandal, standing where its sign was, facing as the sign faced; kept for good. */
    private static void vandal(WorldGenLevel level, BlockPos at, Direction facing) {
        dev.nookofthedead.entity.Vandal vandal = dev.nookofthedead.registry.ZwEntities.VANDAL.get().create(level.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (vandal == null) {
            return;
        }
        vandal.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, facing.toYRot(), 0.0F);
        vandal.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
        vandal.setPersistenceRequired();
        level.addFreshEntity(vandal);
    }

    /** The container next to {@code at} given loot of {@code kind}; a chest at {@code at} if there is none. */
    private static void loot(WorldGenLevel level, BlockPos at, Direction facing, String kind, RandomSource random) {
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        for (Direction side : Direction.values()) {
            BlockEntity next = level.getBlockEntity(at.relative(side));
            if (next instanceof RandomizableContainer container) {
                container.setLootTable(table(kind), random.nextLong());
                return;
            }
        }
        chest(level, at, facing, kind, random);
    }

    private static void chest(WorldGenLevel level, BlockPos at, Direction facing, String kind, RandomSource random) {
        level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
        if (level.getBlockEntity(at) instanceof RandomizableContainer container) {
            container.setLootTable(table(kind), random.nextLong());
        }
    }

    /** The kind of loot a {@code [loot …]} sign names: its second word, general without one. */
    static String kind(String text) {
        String[] words = text.substring(1, text.endsWith("]") ? text.length() - 1 : text.length()).trim().toLowerCase(Locale.ROOT).split("\\s+");
        return words.length > 1 ? words[1] : "general";
    }

    /**
     * {@code container} given {@code table}'s loot: as a loot table, rolled when first opened - unless the builder left
     * something in it, which a loot table would lose (the game saves one or the other, never both: the review, 2026-10-19)
     * - then rolled into its empty places now.
     */
    static void fill(WorldGenLevel level, BlockPos at, RandomizableContainer container, ResourceKey<LootTable> table, long seed) {
        if (container.isEmpty()) {
            container.setLootTable(table, seed);
            return;
        }
        net.minecraft.server.level.ServerLevel server = level.getLevel();
        net.minecraft.world.level.storage.loot.LootParams params = new net.minecraft.world.level.storage.loot.LootParams.Builder(server)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(at))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        server.getServer().reloadableRegistries().getLootTable(table).fill(container, params, seed);
    }

    /**
     * The loot table for {@code kind}: food, medical, weapon, potion, tools, supplies, stash (what was left lying about:
     * the builder's empty containers), or general for any other word.
     */
    public static ResourceKey<LootTable> table(String kind) {
        String known = switch (kind) {
            case "food", "medical", "weapon", "potion", "tools", "supplies", "stash" -> kind;   // stash: the review, 2026-10-19 - left out, it fell back to general
            default -> "general";
        };
        return ResourceKey.create(Registries.LOOT_TABLE, NookOfTheDead.id("chests/" + known));
    }

    /** The way a sign faces: a wall sign's, or a standing one's nearest of the four. */
    static Direction facing(BlockState sign) {
        if (sign.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return sign.getValue(HorizontalDirectionalBlock.FACING);
        }
        if (sign.hasProperty(BlockStateProperties.ROTATION_16)) {
            return RotationSegment.convertToDirection(sign.getValue(BlockStateProperties.ROTATION_16)).orElse(Direction.NORTH);
        }
        return Direction.NORTH;
    }

    private static int number(String word, int otherwise) {
        try {
            return Integer.parseInt(word);
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    private Markers() {}
}
