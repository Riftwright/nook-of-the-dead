package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.entity.MutatedZombie;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The mutated zombie as vanilla's zombie - but a screamer in the other player's clothes, ginger hair, a green tunic, its
 * mouth open (the user, 2026-10-17; the skin painted by tools/paint_screamer.py, nothing of vanilla's).
 */
public class MutatedRenderer extends ZombieRenderer {
    private static final Identifier SCREAMER = NookOfTheDead.id("textures/entity/screamer.png");

    public MutatedRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** A zombie's state, and whether it screams. */
    public static class State extends ZombieRenderState {
        public boolean screamer;
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Zombie zombie, ZombieRenderState state, float partialTicks) {
        super.extractRenderState(zombie, state, partialTicks);
        if (state instanceof State own) {
            own.screamer = zombie instanceof MutatedZombie mutated && mutated.screamer();
        }
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return state instanceof State own && own.screamer && !state.isBaby ? SCREAMER : super.getTextureLocation(state);
    }
}
