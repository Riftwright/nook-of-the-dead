package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.ZombieCow;
import net.minecraft.client.model.animal.cow.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The infected cow: the cow's own shape, whatever its kind was, in a skin of ours (tools/paint_zombie_cow.py) - rotting,
 * the hide torn on the ribs, an eye gone (the user, 2026-10-25: a recoloured cow looked like nothing much).
 */
public class ZombieCowRenderer extends MobRenderer<ZombieCow, LivingEntityRenderState, CowModel> {
    private static final Identifier SKIN = NookOfTheDead.id("textures/entity/zombie_cow.png");

    public ZombieCowRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel(context.bakeLayer(ModelLayers.COW)), 0.7F);
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
