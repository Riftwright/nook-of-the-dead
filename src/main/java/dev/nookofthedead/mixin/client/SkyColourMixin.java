package dev.nookofthedead.mixin.client;

import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookofthedead.client.CuredSky;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * The air over a cured If cleared ({@link CuredSky}): the sky's colour, the fog's colour and how near the fog comes, as
 * the camera reads them from the biomes (in game, 2026-10-19: set through the fog events, the fallen biomes' dun sky and
 * haze stayed over the clear world).
 */
@Mixin(EnvironmentAttributeProbe.class)
abstract class SkyColourMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private <Value> void nookofthedead$curedSky(EnvironmentAttribute<Value> attribute, float partialTicks, CallbackInfoReturnable<Value> info) {
        Object cured = null;
        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            cured = CuredSky.colour((Vector3fc) info.getReturnValue(), partialTicks, CuredSky.BLUE);
        } else if (attribute == EnvironmentAttributes.FOG_COLOR) {
            cured = CuredSky.colour((Vector3fc) info.getReturnValue(), partialTicks, CuredSky.CLEAR);
        } else if (attribute == EnvironmentAttributes.FOG_START_DISTANCE) {
            cured = CuredSky.distance((Float) info.getReturnValue(), partialTicks, 0.0F);
        } else if (attribute == EnvironmentAttributes.FOG_END_DISTANCE) {
            cured = CuredSky.distance((Float) info.getReturnValue(), partialTicks, 1024.0F);
        }
        if (cured != null) {
            @SuppressWarnings("unchecked")
            Value value = (Value) cured;
            info.setReturnValue(value);
        }
    }
}
