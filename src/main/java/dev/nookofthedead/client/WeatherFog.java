package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Region;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The weather over the fallen world (the user, 2026-10-17: "天気で群れが変わる…見通しが悪くなり"): in the rain the fog
 * closes in by up to two fifths, in a storm by half - and the dead are more ({@code Horde.weather}). Vanilla's own rain fog
 * stops at 96 blocks, further out than the fallen world's fog already is, so it does nothing there.
 * <p>
 * In the desert ({@link Region#DESERT}; no rain falls there) the same weather is a sandstorm: the fog closes in to a fifth
 * and turns the sand's colour, and the sand blows past (the user, 2026-10-18: a desert like the films').
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class WeatherFog {
    /** The sandstorm's colour. */
    private static final float[] SAND = {0.78F, 0.66F, 0.46F};
    /** How far into a sandstorm the camera is, eased as one walks in and out of the desert. */
    private static float storm;

    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !NookOfTheDead.fallen(level) || event.getType() != FogType.ATMOSPHERIC) {
            return;
        }
        float t = (float) event.getPartialTick();
        float closer = Mth.lerp(storm, 0.4F * level.getRainLevel(t) + 0.1F * level.getThunderLevel(t), 0.8F * level.getRainLevel(t));
        if (closer > 0.0F) {
            event.scaleFarPlaneDistance(1.0F - closer);
            event.scaleNearPlaneDistance(1.0F - closer);
        }
    }

    @SubscribeEvent
    static void onFogColour(ViewportEvent.ComputeFogColor event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !NookOfTheDead.fallen(level) || storm <= 0.0F) {
            return;
        }
        float k = storm * level.getRainLevel((float) event.getPartialTick());
        event.setRed(Mth.lerp(k, event.getRed(), SAND[0]));
        event.setGreen(Mth.lerp(k, event.getGreen(), SAND[1]));
        event.setBlue(Mth.lerp(k, event.getBlue(), SAND[2]));
    }

    /** Each tick: how far into the desert one is, and in a sandstorm, the sand blowing past. */
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null || !NookOfTheDead.fallen(level)) {
            storm = 0.0F;
            return;
        }
        boolean desert = level.getBiome(mc.player.blockPosition()).is(Region.DESERT.biome);
        storm = Mth.clamp(storm + (desert ? 0.02F : -0.02F), 0.0F, 1.0F);
        float strength = storm * level.getRainLevel(1.0F);
        if (strength <= 0.05F || mc.isPaused()) {
            return;
        }
        var random = level.getRandom();
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
        for (int i = 0; i < (int) (40 * strength); i++) {
            double x = mc.player.getX() + (random.nextDouble() - 0.5) * 24.0;
            double y = mc.player.getY() + random.nextDouble() * 6.0 - 1.0;
            double z = mc.player.getZ() + (random.nextDouble() - 0.5) * 24.0;
            if (level.canSeeSky(BlockPos.containing(x, y, z))) {
                level.addParticle(dust, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }

    private WeatherFog() {}
}
