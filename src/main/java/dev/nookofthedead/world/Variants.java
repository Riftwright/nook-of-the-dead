package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.mixin.StructureTemplateAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A building's variants (the user, 2026-10-13: the traces of people who held out from the zombies on a tower's roof,
 * "数分の一の確率で出てくる"; built as a separate build - "差分用の建築を別で作ればいい"): a copy of a building, changed - a roof
 * camp on its tower, a barricaded door, the place burnt - taken out with {@code /nzw variant <building> <name> <corners>}
 * as only what differs from the building as it is in the mod: every block that is not the same there (air too, where
 * something was taken away) and every fixture that is not there. Saved as {@code town/variant/<building>/<name>} at the
 * building's size, so it goes down over the building where it stands, turned and mirrored with it.
 * <p>
 * One time in {@link #CHANCE} a building comes with one of its variants, picked by the lot's seed. The building's marker
 * signs where the variant changed something give way to the variant's own. A variant changing what the building is later
 * changed in, in the same places, is made again from its copy.
 */
public final class Variants {
    /** One building in this many comes with a variant, if it has any. */
    public static final int CHANCE = 4;
    static final String FOLDER = "town/variant/";

    private static final Map<StructureTemplateManager, Map<String, List<Identifier>>> FOUND = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<StructureTemplate, Set<BlockPos>> CHANGED = Collections.synchronizedMap(new WeakHashMap<>());

    /** The variants of {@code building} ({@code town/<name>}) that the game has, by id. */
    public static List<Identifier> of(StructureTemplateManager manager, Identifier building) {
        Map<String, List<Identifier>> all = FOUND.computeIfAbsent(manager, m -> {
            Map<String, List<Identifier>> found = new HashMap<>();
            m.listTemplates().filter(id -> id.getNamespace().equals(NookOfTheDead.MODID) && id.getPath().startsWith(FOLDER))
                    .sorted().forEach(id -> {
                        String rest = id.getPath().substring(FOLDER.length());
                        int slash = rest.indexOf('/');
                        if (slash > 0) {
                            found.computeIfAbsent(rest.substring(0, slash), k -> new ArrayList<>()).add(id);
                        }
                    });
            return found;
        });
        String name = building.getPath().startsWith("town/") ? building.getPath().substring("town/".length()) : building.getPath();
        return all.getOrDefault(name, List.of());
    }

    /** The variant a building on a lot of {@code seed} comes with, if any. */
    public static Optional<StructureTemplate> pick(StructureTemplateManager manager, Identifier building, long seed) {
        List<Identifier> variants = of(manager, building);
        long h = Town.hash(seed, 7, 0, 50);
        if (variants.isEmpty() || Math.floorMod(h, (long) CHANCE) != 0) {
            return Optional.empty();
        }
        return manager.get(variants.get((int) Math.floorMod(h >> 8, (long) variants.size())));
    }

    /** Where (in the building) {@code variant} sets something down, air included. */
    public static Set<BlockPos> changed(StructureTemplate variant) {
        return CHANGED.computeIfAbsent(variant, v -> {
            Set<BlockPos> at = new HashSet<>();
            for (var palette : ((StructureTemplateAccessor) v).nookofthedead$palettes()) {
                for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                    at.add(info.pos());
                }
            }
            return Set.copyOf(at);
        });
    }

    /**
     * Of {@code copy} (a whole copy of the building, changed, taken out at the building's size), only what differs from
     * {@code building}: the blocks not the same there (air where something went), the fixtures not there. Null when
     * nothing differs.
     */
    public static StructureTemplate patch(StructureTemplate building, StructureTemplate copy, HolderGetter<Block> blocks) {
        Map<BlockPos, StructureTemplate.StructureBlockInfo> was = new HashMap<>();
        for (var palette : ((StructureTemplateAccessor) building).nookofthedead$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                was.put(info.pos(), info);
            }
            break;
        }
        CompoundTag tag = copy.save(new CompoundTag());
        ListTag kept = new ListTag();
        ListTag palette = tag.getListOrEmpty("palette");
        for (Tag entry : tag.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag) entry;
            BlockPos pos = pos(block.getListOrEmpty("pos"));
            StructureTemplate.StructureBlockInfo before = was.get(pos);
            BlockState now = net.minecraft.nbt.NbtUtils.readBlockState(blocks, palette.getCompoundOrEmpty(block.getIntOr("state", 0)));
            CompoundTag nbt = block.getCompound("nbt").orElse(null);
            boolean same = before != null ? before.state().equals(now) && Objects.equals(before.nbt(), nbt) : now.isAir();
            if (!same) {
                kept.add(block);
            }
        }
        if (kept.isEmpty()) {
            return null;
        }
        tag.put("blocks", kept);
        ListTag things = new ListTag();
        List<StructureTemplate.StructureEntityInfo> had = ((StructureTemplateAccessor) building).nookofthedead$entities();
        for (Tag entry : tag.getListOrEmpty("entities")) {
            CompoundTag thing = (CompoundTag) entry;
            BlockPos at = pos(thing.getListOrEmpty("blockPos"));
            String id = thing.getCompoundOrEmpty("nbt").getStringOr("id", "");
            if (had.stream().noneMatch(e -> e.blockPos.equals(at) && e.nbt.getStringOr("id", "").equals(id))) {
                things.add(thing);
            }
        }
        tag.put("entities", things);
        StructureTemplate patch = new StructureTemplate();
        patch.load(blocks, tag);
        return patch;
    }

    private static BlockPos pos(ListTag xyz) {
        return new BlockPos(xyz.getIntOr(0, 0), xyz.getIntOr(1, 0), xyz.getIntOr(2, 0));
    }

    private Variants() {}
}
