package dev.nookofthedead.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookofthedead.entity.Remains;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;

/**
 * A survivor's remains: vanilla's skeleton (its own picture, by name) in what they wore, either slumped sitting with the
 * head fallen forward, its back to where it faces from, or lying on its back.
 */
public class RemainsRenderer extends HumanoidMobRenderer<Remains, RemainsRenderer.State, SkeletonModel<RemainsRenderer.State>> {
    private static final Identifier BONES = Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

    public static class State extends SkeletonRenderState {
        public byte lies;
    }

    public RemainsRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletonModel<>(context.bakeLayer(ModelLayers.SKELETON)), 0.3F);
        this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.SKELETON_ARMOR, context.getModelSet(), SkeletonModel::new),
                context.getEquipmentRenderer()));
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return BONES;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Remains entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.lies = entity.kind();
        state.isPassenger = state.lies == Remains.SITTING;            // the legs out in front, as one sitting
        state.xRot = state.lies == Remains.SITTING ? 50.0F : 0.0F;     // the head fallen forward
        state.yRot = 0.0F;
        state.walkAnimationSpeed = 0.0F;
        state.walkAnimationPos = 0.0F;
        state.ageInTicks = 0.0F;                                       // no idle swaying of the arms
    }

    @Override
    protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float entityScale) {
        super.setupRotations(state, poseStack, bodyRot, entityScale);
        if (state.lies == Remains.SITTING) {
            poseStack.translate(0.0F, -0.62F, 0.0F);                   // down onto the floor
            poseStack.rotateDegrees(Axis.XP, -12.0F);        // leaning back against the wall
        } else {
            poseStack.translate(0.0F, 0.32F, 0.0F);
            poseStack.rotateDegrees(Axis.XP, -90.0F);        // on its back
            poseStack.translate(0.0F, -0.9F, 0.0F);
        }
    }
}
