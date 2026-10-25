package dev.nookofthedead.client;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.SentryGolem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Crackiness;

/**
 * The Sentry Golem as {@link SentryModel} draws it, in its own skin (tools/paint_sentry_golem.py). Its lights glow in
 * the dark - the two lamps under its brow, the aerial's tip, the launcher's bore, the vents of its pack, a status light
 * on its chest - and flicker once it is badly cracked, and go out as it dies. Its damage shows in cracks of its own on its own plates, worse
 * as it is hurt. It sways a little as it walks, a heavy thing.
 */
public class SentryRenderer extends MobRenderer<SentryGolem, SentryRenderer.State, SentryModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/sentry_golem.png");
    private static final RenderType LIGHTS = RenderTypes.eyes(NookOfTheDead.id("textures/entity/sentry_golem_eyes.png"));
    /** At its worst, the works glowing through its cracks. */
    private static final RenderType CRACK_GLOW = RenderTypes.eyes(NookOfTheDead.id("textures/entity/sentry_golem_crack_glow.png"));
    /** Bigger than the village's golem: the base's last guard (the strict grade: smaller than the Vandal, it did not loom). */
    private static final float SIZE = 1.15F;
    private static final Map<Crackiness.Level, Identifier> CRACKS = Map.of(
            Crackiness.Level.LOW, NookOfTheDead.id("textures/entity/sentry_golem_crack_low.png"),
            Crackiness.Level.MEDIUM, NookOfTheDead.id("textures/entity/sentry_golem_crack_medium.png"),
            Crackiness.Level.HIGH, NookOfTheDead.id("textures/entity/sentry_golem_crack_high.png"));

    /** A golem's state, and whether it has someone in its sights. */
    public static class State extends IronGolemRenderState {
        public boolean aiming;
    }

    public SentryRenderer(EntityRendererProvider.Context context) {
        super(context, new SentryModel(context.bakeLayer(SentryModel.LAYER)), 0.9F * SIZE);
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                if (!state.isInvisible && state.crackiness != Crackiness.Level.NONE) {
                    renderColoredCutoutModel(this.getParentModel(), CRACKS.get(state.crackiness), poseStack, collector, light, state, -1, 1);
                }
            }
        });
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                boolean flicker = state.crackiness == Crackiness.Level.HIGH && Math.floorMod((int) state.ageInTicks, 17) < 2;
                if (state.deathTime < 8.0F && !flicker) {
                    collector.order(2).submitModel(this.getParentModel(), state, poseStack, LIGHTS, light, OverlayTexture.NO_OVERLAY,
                            state.outlineColor);
                    if (state.crackiness == Crackiness.Level.HIGH) {
                        collector.order(3).submitModel(this.getParentModel(), state, poseStack, CRACK_GLOW, light, OverlayTexture.NO_OVERLAY,
                                state.outlineColor);
                    }
                }
            }

            @Override
            public RenderType renderType() {
                return LIGHTS;
            }
        });
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SentryGolem golem, State state, float partialTicks) {
        super.extractRenderState(golem, state, partialTicks);
        state.attackTicksRemaining = golem.getAttackAnimationTick() > 0.0F ? golem.getAttackAnimationTick() - partialTicks : 0.0F;
        state.crackiness = golem.getCrackiness();
        state.aiming = golem.isAggressive();
    }

    @Override
    protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.walkAnimationSpeed >= 0.01F) {
            float wave = (Math.abs((state.walkAnimationPos + 6.0F) % 13.0F - 6.5F) - 3.25F) / 3.25F;
            poseStack.rotateDegrees(Axis.ZP, 3.5F * wave);
        }
    }

    @Override
    protected void scale(State state, PoseStack poseStack) {
        poseStack.scale(SIZE, SIZE, SIZE);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN;
    }
}
