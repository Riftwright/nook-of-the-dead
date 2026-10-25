package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.CapitalCrow;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** The capital's crow as {@link CrowModel} draws it, in its own skin. */
public class CrowRenderer extends MobRenderer<CapitalCrow, LivingEntityRenderState, CrowModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/capital_crow.png");

    public CrowRenderer(EntityRendererProvider.Context context) {
        super(context, new CrowModel(context.bakeLayer(CrowModel.LAYER)), 0.2F);
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return SKIN;
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }
}
