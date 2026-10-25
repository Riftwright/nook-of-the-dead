package dev.nookofthedead.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The thin-air zombie as {@link ThinAirModel} draws it - one of the base's researchers in a torn lab coat - in its own skin
 * (tools/paint_thin_air.py), a dull amber light in its eyes that goes out as it dies. It is never a baby, so its one
 * model serves for both; its armour is drawn on the zombie's own armour layers.
 */
public class ThinAirRenderer extends AbstractZombieRenderer<Zombie, ZombieRenderState, ThinAirModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/thin_air_zombie.png");
    private static final RenderType EYES = RenderTypes.eyes(NookOfTheDead.id("textures/entity/thin_air_zombie_eyes.png"));

    public ThinAirRenderer(EntityRendererProvider.Context context) {
        super(context, new ThinAirModel(context.bakeLayer(ThinAirModel.LAYER)), new ThinAirModel(context.bakeLayer(ThinAirModel.LAYER)),
                ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), ThinAirModel::new),
                ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), ThinAirModel::new));
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, ZombieRenderState state, float yRot, float xRot) {
                if (state.deathTime < 8.0F) {
                    collector.order(1).submitModel(this.getParentModel(), state, poseStack, EYES, light, OverlayTexture.NO_OVERLAY,
                            state.outlineColor);
                }
            }

            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new ZombieRenderState();
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return SKIN;
    }
}
