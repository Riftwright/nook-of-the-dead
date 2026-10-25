package dev.nookofthedead.client;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.Vandal;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The Vandal as {@link VandalModel} draws it, in its own skin, its eyes glowing faintly in the dark - red, once it is
 * wounded to half (when it goes faster and roars more: {@link Vandal#wounded}), and going out as it dies. What it is
 * doing ({@link Vandal#pose}) eases in and out over a few frames rather than snapping: a roar's rearing, a rush's crouch
 * and run. It does not topple over dead as other creatures do: the model sinks down where it stands.
 */
public class VandalRenderer extends MobRenderer<Vandal, VandalRenderer.State, VandalModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/vandal.png");
    private static final RenderType EYES = RenderTypes.eyes(NookOfTheDead.id("textures/entity/vandal_eyes.png"));
    private static final RenderType RAGE_EYES = RenderTypes.eyes(NookOfTheDead.id("textures/entity/vandal_eyes_rage.png"));
    /**
     * How far each Vandal is into each pose and into its rage, eased by the clock - so a roar's rearing takes as long at 30
     * frames a second as at 144 (the review, 2026-10-18) - and when it was last drawn (per entity: the render state is made
     * afresh each frame).
     */
    private static final Map<Vandal, Eased> EASED = new WeakHashMap<>();
    /** How fast each eases in or out, in whole poses a second: a roar's rearing a quarter second, a crouch a sixth. */
    private static final float ROAR_RATE = 4.0F;
    private static final float CROUCH_RATE = 6.0F;
    private static final float RAGE_RATE = 1.5F;

    private static final class Eased {
        float roar;
        float crouch;
        float rush;
        float rage;
        long at = System.nanoTime();
    }
    /** The development client (Nookworks' agent input on): a Vandal named for a pose holds it, to be looked at. */
    private static final boolean LOOKING = Boolean.getBoolean("nookworks.agentInput");
    /**
     * How far back the model is drawn (blocks, before its scale): its bulk is well ahead of its feet, the head and arms
     * thrust forward, so drawn back this far its chest and shoulders stand over the zombie's frame it is hit by (and
     * turns on), and its shadow lies under it.
     */
    private static final float BACK = 6.0F / 16.0F;

    public VandalRenderer(EntityRendererProvider.Context context) {
        super(context, new VandalModel(context.bakeLayer(VandalModel.LAYER)), 1.1F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
                if (state.deathTime < 8.0F) {                               // the light in them goes out as it dies
                    collector.order(1).submitModel(this.getParentModel(), state, poseStack, state.rage > 0.5F ? RAGE_EYES : EYES, light,
                            OverlayTexture.NO_OVERLAY, state.outlineColor);
                }
            }

            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    /** A zombie's state, and how far into a roar, a crouch, a rush and its rage. */
    public static class State extends ZombieRenderState {
        public float roar;
        public float crouch;
        public float rush;
        public float rage;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Vandal vandal, State state, float partialTicks) {
        super.extractRenderState(vandal, state, partialTicks);
        state.isAggressive = vandal.isAggressive();
        byte pose = vandal.pose();
        boolean raging = vandal.getHealth() <= vandal.getMaxHealth() / 2.0F;
        if (LOOKING && vandal.getCustomName() != null) {                  // the agent client, for looking at each pose
            String name = vandal.getCustomName().getString();
            pose = name.contains("roar") ? Vandal.ROARING : name.contains("crouch") ? Vandal.CROUCHING : name.contains("rush") ? Vandal.RUSHING : pose;
            raging |= name.contains("rage");
            if (name.contains("walk") || name.contains("rush")) {
                state.walkAnimationSpeed = 1.0F;
                state.walkAnimationPos = state.ageInTicks * 0.7F;
            }
            if (name.contains("dead")) {
                state.deathTime = 20.0F;
            }
            if (name.contains("swipe")) {
                state.swingAnimation = 0.5F;
            }
        }
        Eased eased = EASED.computeIfAbsent(vandal, v -> new Eased());
        long now = System.nanoTime();
        float seconds = Math.min(0.1F, (now - eased.at) / 1.0E9F);   // a long pause (a menu) is not a long step
        eased.at = now;
        eased.roar = toward(eased.roar, pose == Vandal.ROARING ? 1.0F : 0.0F, ROAR_RATE * seconds);
        eased.crouch = toward(eased.crouch, pose == Vandal.CROUCHING ? 1.0F : 0.0F, CROUCH_RATE * seconds);
        eased.rush = toward(eased.rush, pose == Vandal.RUSHING ? 1.0F : 0.0F, CROUCH_RATE * seconds);
        eased.rage = toward(eased.rage, raging ? 1.0F : 0.0F, RAGE_RATE * seconds);
        state.roar = smooth(eased.roar);
        state.crouch = smooth(eased.crouch);
        state.rush = smooth(eased.rush);
        state.rage = smooth(eased.rage);
    }

    private static float toward(float from, float to, float step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }

    @Override
    protected void scale(State state, PoseStack poseStack) {
        poseStack.translate(0.0F, 0.0F, BACK);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN;
    }
}
