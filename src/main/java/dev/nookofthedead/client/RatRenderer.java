package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.InfectedRat;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** The infected rat as {@link RatModel} draws it, in its own skin. */
public class RatRenderer extends MobRenderer<InfectedRat, LivingEntityRenderState, RatModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/infected_rat.png");

    public RatRenderer(EntityRendererProvider.Context context) {
        super(context, new RatModel(context.bakeLayer(RatModel.LAYER)), 0.22F);
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
