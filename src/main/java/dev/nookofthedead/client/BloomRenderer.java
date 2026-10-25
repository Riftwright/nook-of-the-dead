package dev.nookofthedead.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.SewerBloom;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The sewer bloom as {@link BloomModel} draws it, in its own skin, at twice the model's size. */
public class BloomRenderer extends MobRenderer<SewerBloom, BloomRenderer.State, BloomModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/sewer_bloom.png");
    static final float SCALE = 2.0F;

    /** Its throat's ember: the one thing seen of it first in the sewer's dark (the visual review, 2026-10-19). */
    private static final net.minecraft.client.renderer.rendertype.RenderType GLOW =
            net.minecraft.client.renderer.rendertype.RenderTypes.eyes(NookOfTheDead.id("textures/entity/sewer_bloom_glow.png"));

    public BloomRenderer(EntityRendererProvider.Context context) {
        super(context, new BloomModel(context.bakeLayer(BloomModel.LAYER)), 1.4F);
        this.addLayer(new net.minecraft.client.renderer.entity.layers.EyesLayer<>(this) {
            @Override
            public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, int light, State state,
                    float yRot, float xRot) {
                if (state.deathTime < 8.0F) {                        // the ember goes out as it dies
                    collector.order(1).submitModel(this.getParentModel(), state, poseStack, GLOW, light,
                            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, state.outlineColor);
                }
            }

            @Override
            public net.minecraft.client.renderer.rendertype.RenderType renderType() {
                return GLOW;
            }
        });
    }

    /** A living thing's state, how far open (0 to 1), and how far into a lash or a thrown vine (0 to 1). */
    public static class State extends LivingEntityRenderState {
        public float openness;
        public float swing;
        public boolean lash;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SewerBloom bloom, State state, float partialTicks) {
        super.extractRenderState(bloom, state, partialTicks);
        state.openness = Mth.lerp(partialTicks, bloom.opennessO, bloom.openness);
        state.swing = bloom.getSwingAnimation(partialTicks);
        state.lash = !bloom.throwing();
        // all of it turned to where it looks (a thing that does not walk has no body to lag behind its head)
        state.bodyRot = Mth.rotLerp(partialTicks, bloom.yHeadRotO, bloom.yHeadRot);
        state.yRot = 0.0F;
    }

    @Override
    protected void scale(State state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN;
    }
}
