package dev.nookofthedead.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookofthedead.client.FallenView;
import dev.nookworks.client.render.DaemonView;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.travel.DaemonDestinations;

/**
 * The If as Nookworks' mirrors and doors show it ({@code DaemonView#pixel}): with this mod in, the fallen one
 * ({@link FallenView}) in place of its meadow at dawn. Every other place is drawn as Nookworks draws it.
 */
@Mixin(DaemonView.class)
abstract class DaemonViewMixin {
    @Inject(method = "pixel(Ldev/nookworks/travel/DaemonDestinations$Look;IIFLdev/nookworks/client/render/DaemonView$Eye;)I", at = @At("HEAD"),
            cancellable = true, require = 0)   // only a look through the door: a Nookworks 1.1.x reworking it must not stop the game
    private static void nookofthedead$fallen(DaemonDestinations.Look look, int x, int y, float time, DaemonView.Eye eye,
            CallbackInfoReturnable<Integer> result) {
        // the If's look is Nookworks' own, known without the registries
        if (look == DaemonDestinations.look(IfWorld.KEY, null)) {
            result.setReturnValue(FallenView.pixel(x, y, time, eye));
        }
    }
}
