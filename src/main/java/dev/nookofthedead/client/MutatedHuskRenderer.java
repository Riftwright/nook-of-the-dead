package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.MutatedZombie;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The mutated husk as the game's husk - but a screamer in the desert's own clothes (as {@link MutatedRenderer}'s town one:
 * the skin painted by tools/paint_screamer.py --husk, nothing of vanilla's).
 */
public class MutatedHuskRenderer extends HuskRenderer {
    private static final Identifier SCREAMER = NookOfTheDead.id("textures/entity/husk_screamer.png");

    public MutatedHuskRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new MutatedRenderer.State();
    }

    @Override
    public void extractRenderState(Zombie zombie, ZombieRenderState state, float partialTicks) {
        super.extractRenderState(zombie, state, partialTicks);
        if (state instanceof MutatedRenderer.State own) {
            own.screamer = zombie instanceof MutatedZombie mutated && mutated.screamer();
        }
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return state instanceof MutatedRenderer.State own && own.screamer && !state.isBaby ? SCREAMER : super.getTextureLocation(state);
    }
}
