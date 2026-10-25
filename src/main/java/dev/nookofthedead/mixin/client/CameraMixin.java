package dev.nookofthedead.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.nookofthedead.client.FilmCamera;
import net.minecraft.client.Camera;

/**
 * While a film plays ({@link FilmCamera}), the camera is where the film has it, not at the player's eyes: set after the
 * game has set it from the player, before the frame's view is worked out from it. Whether the player is seen (a shot
 * from behind them) is the film's too.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void nookofthedead$film(float partialTicks, CallbackInfo info) {
        FilmCamera.View view = FilmCamera.view(partialTicks);
        if (view != null) {
            this.setRotation(view.yaw(), view.pitch(), view.roll());
            this.setPosition(view.pos().x, view.pos().y, view.pos().z);
            this.detached = view.detached();
        }
    }
}
