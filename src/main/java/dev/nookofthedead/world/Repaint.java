package dev.nookofthedead.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A building as it stands on one lot and not the next (the user, 2026-10-16: "同じ建物ばかり繰り返し出てくる…差分や
 * バリエーションを加えて、没入感を感じられるように"): the same house a street over built of other timber, with another
 * roof, and gone further to ruin or to green - all from its lot's seed, the same from every chunk.
 * <ul>
 * <li>Its timber: the builder's woods turned round among oak, spruce, dark oak and birch (each kind to another, so a
 * dark frame on a light wall stays a dark frame on a light wall) - or left as built.</li>
 * <li>Its colours: the terracotta, wool and concrete turned round among the town's dull ones, the same way.</li>
 * <li>How far gone: {@link Wear#KEPT} as the others (glass gone here and there, stone cracked - {@code Town.WEAR}),
 * {@link Wear#WORN} (most of the glass, mossed and cracked all over, the cloth rotted), {@link Wear#GREEN} (overgrown:
 * moss in every crack), {@link Wear#BURNT} (its timber charred dark, its cloth burnt black, no glass).</li>
 * </ul>
 * And one time in {@link #BOARDED} the windows boarded up - someone held out in it - most of them nailed over with planks.
 * Nothing is taken away but glass and cloth, so nothing comes to stand on air. The buildings the user built themselves
 * keep their own timber and colours ({@link #OWN}).
 */
public final class Repaint implements StructureProcessor {
    /** How far gone a building is. */
    public enum Wear { KEPT, WORN, GREEN, BURNT }

    /** The buildings the user built (or reworked) with their own hands: their timber and colours as they made them. */
    public static final Set<String> OWN = Set.of("town/s2", "town/s3", "town/s4", "town/s5", "town/s7", "town/s8", "town/m1", "town/m2",
            "town/m4");

    private static final List<String> WOODS = List.of("oak", "spruce", "dark_oak", "birch");
    private static final List<String> WOOD_PARTS = List.of("%s_planks", "%s_stairs", "%s_slab", "%s_fence", "%s_fence_gate", "%s_door",
            "%s_trapdoor", "%s_log", "%s_wood", "stripped_%s_log", "stripped_%s_wood", "%s_pressure_plate", "%s_button");
    private static final List<String> COLOURS = List.of("brown", "red", "gray", "light_gray", "white", "black", "cyan", "green");
    private static final List<String> COLOUR_PARTS = List.of("%s_terracotta", "%s_wool", "%s_carpet", "%s_concrete", "%s_glazed_terracotta");

    /** One building in this many has its windows boarded up. */
    public static final int BOARDED = 6;

    private final Map<Block, Block> swaps;
    private final Wear wear;
    private final boolean boarded;

    private Repaint(Map<Block, Block> swaps, Wear wear, boolean boarded) {
        this.swaps = swaps;
        this.wear = wear;
        this.boarded = boarded;
    }

    /**
     * The look of the building {@code id} on a lot of seed {@code seed}: one time in three its timber and colours as
     * built, else turned round; mostly kept as the others, sometimes worn, overgrown, or burnt.
     */
    public static Repaint of(Identifier id, long seed) {
        return of(id, seed, false);
    }

    /** As {@link #of(Identifier, long)}, burnt for certain where a fire went through ({@link Traces#burning}). */
    public static Repaint of(Identifier id, long seed, boolean fire) {
        Map<Block, Block> swaps = new HashMap<>();
        boolean own = OWN.contains(id.getPath());
        int woods = own ? 0 : (int) Math.floorMod(Town.hash(seed, 1, 0, 171), 6L);    // 0, 1: as built; 2..5 a turn
        int colours = own ? 0 : (int) Math.floorMod(Town.hash(seed, 2, 0, 171), 5L);  // 0, 1: as built
        int roll = (int) Math.floorMod(Town.hash(seed, 3, 0, 171), 100L);
        Wear wear = fire ? Wear.BURNT : roll < 55 ? Wear.KEPT : roll < 78 ? Wear.WORN : roll < 92 ? Wear.GREEN : Wear.BURNT;
        if (own && wear == Wear.BURNT) {
            // the author's own are never charred: burning turns their timber and cloth (the review, 2026-10-18) - worn instead,
            // even where a fire went through
            wear = Wear.WORN;
        }
        if (wear == Wear.BURNT) {
            turn(swaps, WOODS, WOOD_PARTS, w -> "dark_oak");            // charred
            turn(swaps, COLOURS, List.of("%s_wool", "%s_carpet"), c -> c.equals("black") ? "black" : "gray");
        } else {
            if (woods >= 2) {
                int by = woods - 1;
                turn(swaps, WOODS, WOOD_PARTS, w -> WOODS.get((WOODS.indexOf(w) + by) % WOODS.size()));
            }
            if (colours >= 2) {
                int by = colours * 3 - 4;                                  // 2, 5, 8: a turn that moves every colour
                turn(swaps, COLOURS, COLOUR_PARTS, c -> COLOURS.get((COLOURS.indexOf(c) + by) % COLOURS.size()));
            }
        }
        boolean boarded = wear != Wear.BURNT && Math.floorMod(Town.hash(seed, 4, 0, 171), (long) BOARDED) == 0;
        return new Repaint(Map.copyOf(swaps), wear, boarded);
    }

    public Wear wear() {
        return this.wear;
    }

    /** Each {@code part} of each of {@code kinds} to that part of the kind {@code to} names, where both blocks are. */
    private static void turn(Map<Block, Block> swaps, List<String> kinds, List<String> parts, java.util.function.UnaryOperator<String> to) {
        for (String kind : kinds) {
            String into = to.apply(kind);
            if (into.equals(kind)) {
                continue;
            }
            for (String part : parts) {
                Block from = block(String.format(part, kind));
                Block onto = block(String.format(part, into));
                if (from != null && onto != null) {
                    swaps.put(from, onto);
                }
            }
        }
    }

    private static @Nullable Block block(String path) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElse(null);
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo process(LevelReader level, BlockPos target, BlockPos reference,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo info, StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        BlockState state = info.state();
        Block swapped = this.swaps.get(state.getBlock());
        if (swapped != null) {
            state = swapped.withPropertiesOf(state);
        }
        if (this.boarded && (state.is(Blocks.GLASS_PANE) || state.is(Blocks.GLASS)) && settings.getRandom(info.pos()).nextInt(10) < 8) {
            state = (settings.getRandom(info.pos().above()).nextBoolean() ? Blocks.SPRUCE_PLANKS : Blocks.OAK_PLANKS).defaultBlockState();
        } else if (this.wear != Wear.KEPT) {
            RandomSource random = settings.getRandom(info.pos());
            state = this.worn(state, random);
        }
        return state == info.state() ? info : new StructureTemplate.StructureBlockInfo(info.pos(), state, info.nbt());
    }

    private BlockState worn(BlockState state, RandomSource random) {
        Block block = state.getBlock();
        float r = random.nextFloat();
        switch (this.wear) {
            case WORN -> {
                if ((block == Blocks.GLASS_PANE || block == Blocks.GLASS) && r < 0.55F || block instanceof net.minecraft.world.level.block.WoolCarpetBlock && r < 0.4F) {
                    return Blocks.AIR.defaultBlockState();
                }
                if (block == Blocks.STONE_BRICKS && r < 0.25F) {
                    return r < 0.12F ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                }
                if (block == Blocks.COBBLESTONE && r < 0.25F) {
                    return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                }
            }
            case GREEN -> {
                if (block == Blocks.STONE_BRICKS && r < 0.45F) {
                    return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                }
                if (block == Blocks.COBBLESTONE && r < 0.5F) {
                    return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                }
                if (block == Blocks.STONE_BRICK_STAIRS && r < 0.4F) {
                    return Blocks.MOSSY_STONE_BRICK_STAIRS.withPropertiesOf(state);
                }
                if (block == Blocks.STONE_BRICK_SLAB && r < 0.4F) {
                    return Blocks.MOSSY_STONE_BRICK_SLAB.withPropertiesOf(state);
                }
                if (block == Blocks.COBBLESTONE_STAIRS && r < 0.4F) {
                    return Blocks.MOSSY_COBBLESTONE_STAIRS.withPropertiesOf(state);
                }
                if ((block == Blocks.GLASS_PANE || block == Blocks.GLASS) && r < 0.4F) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
            case BURNT -> {
                if (block == Blocks.GLASS_PANE || block == Blocks.GLASS || block instanceof net.minecraft.world.level.block.WoolCarpetBlock && r < 0.7F) {
                    return Blocks.AIR.defaultBlockState();
                }
                if (block == Blocks.STONE_BRICKS && r < 0.35F) {
                    return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                }
            }
            default -> {
            }
        }
        return state;
    }

    @Override
    public MapCodec<? extends StructureProcessor> codec() {
        return MapCodec.unit(this);                                       // made in place, never written out
    }
}
