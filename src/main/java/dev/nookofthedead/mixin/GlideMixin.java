package dev.nookofthedead.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.ZwConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * No one glides in the If when {@link ZwConfig#ELYTRA_IN_THE_IF} is off. {@code canGlide} is where both sides ask: the
 * client as its player jumps in the air ({@code Player#tryToStartFallFlying}, so the elytra does not open, nor is the
 * server asked), the server as the player asks it to start and every tick of a glide ({@code updateFallFlying}, so one
 * under way when the setting is turned off comes down). The client says why, once a try, as the elytra stays shut.
 */
@Mixin(LivingEntity.class)
abstract class GlideMixin {
    @Inject(method = "canGlide(Z)Z", at = @At("RETURN"), cancellable = true)
    private void nookofthedead$noGlidingInTheIf(boolean isNeoForgeConnection, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!cir.getReturnValueZ() || !(self instanceof Player player) || !NookOfTheDead.fallen(self.level())
                || ZwConfig.glidingInTheIf()) {
            return;
        }
        cir.setReturnValue(false);
        if (self.level().isClientSide() && !player.isFallFlying()) {
            player.sendOverlayMessage(Component.translatable("message.nookofthedead.no_gliding").withStyle(ChatFormatting.GRAY));
        }
    }
}
