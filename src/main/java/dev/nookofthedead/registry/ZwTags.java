package dev.nookofthedead.registry;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ZwTags {
    /** What a runner breaks through to its prey: wood, glass, leaves, wool and the like; stone and iron hold. */
    public static final TagKey<Block> SOFT = TagKey.create(Registries.BLOCK, NookOfTheDead.id("soft"));
    /** What even a weak one breaks through: what shatters or tears - glass, leaves, wool, a bed, the If's mirror. */
    public static final TagKey<Block> FRAGILE = TagKey.create(Registries.BLOCK, NookOfTheDead.id("fragile"));

    private ZwTags() {}
}
