package dev.nookofthedead.client;

import dev.nookofthedead.NookOfTheDead;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The fog lifted off a cured If (the user, 2026-10-19: "霧が晴れて美しい世界に"): the fallen world's murk - its biomes'
 * fog, drawn in close and the colour of dust - opened out to the full view and turned the blue of a clear day; by the
 * ending's film as it plays ({@link FilmCamera#fogClear}), for good once it is cured.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID, value = Dist.CLIENT)
public final class CuredSky {
    /** The clear air's fog, #C0D8FF. */
    public static final float[] CLEAR = {0.75F, 0.85F, 1.0F};

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    static void onFog(ViewportEvent.RenderFog event) {
        var level = Minecraft.getInstance().level;
        if (level == null || !NookOfTheDead.fallen(level) || event.getType() != FogType.ATMOSPHERIC) {
            return;
        }
        float clear = clear((float) event.getPartialTick());
        if (clear <= 0.0F) {
            return;
        }
        float full = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
        event.setFarPlaneDistance(event.getFarPlaneDistance() + (full - event.getFarPlaneDistance()) * clear);
        event.setNearPlaneDistance(event.getNearPlaneDistance() + (full * 0.75F - event.getNearPlaneDistance()) * clear);
    }

    /** A clear day's sky, as the overworld's plains have it. */
    public static final float[] BLUE = {0.47F, 0.65F, 1.0F};

    /**
     * A sky or fog colour over a cured If: the fallen biome's blended to {@code to} as the fog lifts, as bright as the
     * colour it replaces (night stays night); null to leave it.
     */
    public static org.joml.Vector3fc colour(org.joml.Vector3fc colour, float partial, float[] to) {
        float clear = clear(partial);
        if (colour == null || clear <= 0.0F) {
            return null;
        }
        float day = Math.min(1.0F, (colour.x() + colour.y() + colour.z()) / 3.0F / 0.6F);
        return new org.joml.Vector3f(colour.x() + (to[0] * day - colour.x()) * clear, colour.y() + (to[1] * day - colour.y()) * clear,
                colour.z() + (to[2] * day - colour.z()) * clear);
    }

    /** A fog distance over a cured If, drawn out to the overworld's ({@code to}) as the fog lifts; null to leave it. */
    public static Float distance(Float value, float partial, float to) {
        float clear = clear(partial);
        if (value == null || clear <= 0.0F) {
            return null;
        }
        return value + (to - value) * clear;
    }

    /** How clear the air at the camera is: 0 fallen as it was (and always up in the stratosphere, its own). */
    private static float clear(float partial) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !NookOfTheDead.fallen(level) || stratosphere(mc.gameRenderer.mainCamera())) {
            return 0.0F;
        }
        return FilmCamera.fogClear(partial);
    }

    private static boolean stratosphere(net.minecraft.client.Camera camera) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBiome(net.minecraft.core.BlockPos.containing(camera.position())).is(dev.nookofthedead.world.RegionBiomes.THIN_AIR);
    }

    private CuredSky() {}
}
