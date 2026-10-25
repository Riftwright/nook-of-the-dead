package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.InfectedScorpion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** The infected scorpion as {@link ScorpionModel} draws it, in its own skin. */
public class ScorpionRenderer extends MobRenderer<InfectedScorpion, ScorpionRenderer.State, ScorpionModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/infected_scorpion.png");

    public ScorpionRenderer(EntityRendererProvider.Context context) {
        super(context, new ScorpionModel(context.bakeLayer(ScorpionModel.LAYER)), 0.45F);
    }

    /** A living thing's state, and how far into a strike (0 to 1). */
    public static class State extends LivingEntityRenderState {
        public float strike;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(InfectedScorpion scorpion, State state, float partialTicks) {
        super.extractRenderState(scorpion, state, partialTicks);
        state.strike = scorpion.getSwingAnimation(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN;
    }
}
