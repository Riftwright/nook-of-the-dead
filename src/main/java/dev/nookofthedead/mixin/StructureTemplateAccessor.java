package dev.nookofthedead.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The things a template keeps, so {@code /nzw export} can leave out what is only passing through (mobs, dropped items), and
 * its blocks, so a variant can be told from its building ({@code Variants}).
 */
@Mixin(StructureTemplate.class)
public interface StructureTemplateAccessor {
    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> nookofthedead$entities();

    @Accessor("palettes")
    List<StructureTemplate.Palette> nookofthedead$palettes();
}
