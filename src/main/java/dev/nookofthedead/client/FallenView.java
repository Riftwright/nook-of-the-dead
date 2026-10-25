package dev.nookofthedead.client;

import dev.nookworks.client.render.DaemonView;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The If as an If Mirror (or a door to it) shows it while this mod is in (the user, 2026-10-16: "少しよどんだ空気と、
 * 崩れかかった建物がポツポツと見える、不穏な雰囲気の絵…普通のifの世界とは様子が違う"): not Nookworks' meadow at dawn but
 * a dead one - a stagnant, yellowed sky, grass gone grey-brown, an old road leading off, and here and there a house
 * standing broken against the haze, its windows dark; ash drifting down, and far off a thread of smoke. Nothing more is
 * said: no one, nothing moving but the air.
 * <p>
 * Drawn as {@link DaemonView} draws its places - each pixel what the eye sees through it, so stepping aside shows more of
 * one side and near things slide past far ones - with the same sizes, so it sits in the same glass.
 */
public final class FallenView {
    private static final int WIDTH = DaemonView.WIDTH;
    private static final float FLOOR = DaemonView.HEIGHT;
    private static final float NEAREST = 4;
    private static final float HILLS = 520;
    private static final float SKY_SPAN = 0.12F;
    private static final float ROAD_HALF_WIDTH = 6;
    private static final int SEED = 0x6E0D;

    private static final int SKY_TOP = 0x3E4239;
    private static final int SKY_LOW = 0x9C9572;
    private static final int MURK = 0x5E5A44;
    private static final int GRASS = 0x55523C;
    private static final int GRASS_LIT = 0x66614A;
    private static final int GRASS_DARK = 0x433F30;
    private static final int ROAD = 0x67645B;
    private static final int ROAD_LIT = 0x7A766A;
    private static final int CRACK = 0x3F3D37;
    private static final int MOSS = 0x5A6038;
    private static final int[] WALLS = {0x4C4A44, 0x5C4136, 0x7C7562, 0x44403A};
    private static final int WINDOW = 0x1B1A17;
    private static final int ASH = 0xB9B49E;
    private static final int SMOKE = 0x34332D;
    private static final int HILL = 0x4E4D3C;
    private static final int HILL_CREST = 0x5E5C46;
    /** The houses standing about: how many, and how far back the farthest. */
    private static final int HOUSES = 11;
    private static final float FARTHEST = 480;

    private FallenView() {}

    /** The colour (0xAARRGGBB, opaque) of the pixel at column {@code x}, row {@code y} at {@code time} ticks, seen from {@code eye}. */
    public static int pixel(int x, int y, float time, DaemonView.Eye eye) {
        float away = Math.max(NEAREST, eye.away());
        float du = (x + 0.5F - eye.across()) / away;
        float dv = (y + 0.5F - eye.down()) / away;
        float ground = dv > 0 && eye.down() < FLOOR ? (FLOOR - eye.down()) / dv - away : Float.POSITIVE_INFINITY;
        float gx = ground < Float.POSITIVE_INFINITY ? eye.across() + du * (away + ground) : 0;
        return 0xFF000000 | scene(eye, away, x, y, du, dv, ground, gx, time);
    }

    private static int scene(DaemonView.Eye eye, float away, int x, int y, float du, float dv, float ground, float gx, float time) {
        if (ash(eye, away, x, y, time)) {
            return mix(ASH, SKY_LOW, 0.35F);
        }
        // the nearest house the ray meets before the ground
        float nearest = Math.min(ground, HILLS);
        int house = -1;
        int wall = 0;
        for (int i = 0; i < HOUSES; i++) {
            float depth = 110 + hash(i, 1, SEED) * (FARTHEST - 110);
            if (depth >= nearest) {
                continue;
            }
            float side = hash(i, 2, SEED) < 0.5F ? -1 : 1;
            float across = road(depth) + side * (ROAD_HALF_WIDTH + 6 + hash(i, 3, SEED) * (10 + depth * 0.3F));
            float half = 9 + hash(i, 4, SEED) * 12;
            float height = 36 + hash(i, 5, SEED) * 52;
            float k = away / (away + depth);
            float left = eye.across() + (across - half - eye.across()) * k;
            float right = eye.across() + (across + half - eye.across()) * k;
            if (x + 0.5F < left || x + 0.5F >= right) {
                continue;
            }
            float top = eye.down() + (FLOOR - height - eye.down()) * k;
            float bottom = eye.down() + (FLOOR - eye.down()) * k;
            if (y + 0.5F < top || y + 0.5F >= bottom) {
                continue;
            }
            // where on its face: u across from its left edge, v up from its foot, in the place's pixels
            float u = (x + 0.5F - eye.across()) / k + eye.across() - (across - half);
            float v = FLOOR - ((y + 0.5F - eye.down()) / k + eye.down());
            // its top broken off unevenly, more on some than others; a corner fallen in
            float damage = 0.15F + 0.55F * hash(i, 6, SEED);
            float standing = height * (1 - damage * hash(Mth.floor(u / 6), i, SEED + 7));
            if (u > 2 * half * 0.7F && hash(i, 8, SEED) > 0.5F) {
                standing = Math.min(standing, height * 0.45F);
            }
            if (v > standing) {
                continue;
            }
            nearest = depth;
            house = i;
            boolean window = v > 10 && Mth.positiveModulo(u, 10) >= 4 && Mth.positiveModulo(u, 10) < 7
                    && Mth.positiveModulo(v, 14) >= 5 && Mth.positiveModulo(v, 14) < 10;
            int colour = WALLS[(int) (hash(i, 9, SEED) * WALLS.length) % WALLS.length];
            if (window) {
                colour = WINDOW;
            } else if (v > standing - 2.5F * (away + depth) / away) {
                colour = mix(colour, 0x000000, 0.25F);                   // the broken edge, in shadow
            } else if (fbm(u / 7, v / 7, SEED + i) > 0.62F) {
                colour = mix(colour, MOSS, 0.35F);                       // damp and moss
            }
            wall = colour;
        }
        if (house >= 0) {
            return haze(wall, nearest, 520);
        }
        if (ground < HILLS) {
            float fromRoad = Math.abs(gx - road(ground));
            int colour;
            if (fromRoad < ROAD_HALF_WIDTH) {
                float c = fbm(gx / 5, ground / 5, SEED + 30);
                colour = fromRoad > ROAD_HALF_WIDTH - 1.2F ? mix(ROAD, GRASS, 0.5F) : c > 0.66F ? ROAD_LIT : ROAD;
                if (Math.abs(fbm(gx / 9, ground / 9, SEED + 31) - 0.5F) < 0.012F) {
                    colour = CRACK;
                } else if (fbm(gx / 12, ground / 12, SEED + 32) > 0.72F) {
                    colour = mix(colour, MOSS, 0.6F);                    // weeds through it
                }
            } else {
                float n = fbm(gx / 30, ground / 30, SEED + 20);
                colour = n > 0.62F ? GRASS_LIT : n < 0.36F ? GRASS_DARK : GRASS;
            }
            return haze(colour, ground, 600);
        }
        float t = away + HILLS;
        float hillTop = FLOOR - 20 - 22 * fbm((eye.across() + du * t) / 110, 0.5F, SEED + 100);
        float at = eye.down() + dv * t;
        if (at >= hillTop) {
            return mix(at < hillTop + t / away ? HILL_CREST : HILL, SKY_LOW, 0.55F);
        }
        if (smoke(du, dv, time)) {
            return mix(SMOKE, SKY_LOW, 0.3F + 0.5F * Math.clamp(-dv / 0.5F, 0.0F, 1.0F));
        }
        return sky(du, dv, time);
    }

    /** The sky: heavy and yellowed low down, a dull grey above, a murk drifting slowly through it - no clouds to speak of. */
    private static int sky(float du, float dv, float time) {
        float up = Math.clamp(-dv / SKY_SPAN, 0.0F, 1.0F);
        int base = mix(SKY_LOW, SKY_TOP, smooth(up));
        float c = fbm(du * 3 + time * 0.0006F, -dv * 9, SEED + 50);
        return c > 0.6F ? mix(base, MURK, 0.45F) : c > 0.52F ? mix(base, MURK, 0.2F) : base;
    }

    /** A thread of smoke going up from beyond the hills, off to one side, leaning and wavering as it goes. */
    private static boolean smoke(float du, float dv, float time) {
        if (dv > 0) {
            return false;
        }
        float rise = -dv;
        float centre = 0.55F + rise * 0.35F + 0.03F * Mth.sin(time * 0.01F + rise * 18);
        float half = 0.012F + rise * 0.06F;
        return Math.abs(du - centre) < half && fbm(du * 30, rise * 30 - time * 0.01F, SEED + 60) > 0.38F;
    }

    /** Ash coming down slowly through the air near and far: whether a fleck is at pixel {@code x}, {@code y}. */
    private static boolean ash(DaemonView.Eye eye, float away, int x, int y, float time) {
        for (int i = 0; i < 14; i++) {
            float depth = 12 + hash(i, 10, SEED) * 220;
            float across = WIDTH / 2.0F + (hash(i, 11, SEED) - 0.5F) * (16 + depth * 1.2F) + 4 * Mth.sin(time * 0.015F + i);
            float fall = 90 - (time * (0.04F + 0.05F * hash(i, 12, SEED)) + hash(i, 13, SEED) * 90) % 90;
            float k = away / (away + depth);
            if (Mth.floor(eye.across() + (across - eye.across()) * k) == x && Mth.floor(eye.down() + (FLOOR - fall - eye.down()) * k) == y) {
                return true;
            }
        }
        return false;
    }

    /** Where the old road is across, {@code depth} back: out of the glass, then wandering a little. */
    private static float road(float depth) {
        return WIDTH / 2.0F + 18 * Mth.sin(depth / 120 + 1.3F) * smooth(Math.min(1.0F, depth / 140));
    }

    /** {@code colour} seen through {@code depth} of the thick air: taken towards the low sky's colour. */
    private static int haze(int colour, float depth, float clear) {
        return mix(colour, SKY_LOW, 0.85F * smooth(Math.clamp(depth / clear, 0.0F, 1.0F)));
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 0x85EBCA77;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float noise(float x, float y, int seed) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = smooth(x - ix);
        float fy = smooth(y - iy);
        float a = hash(ix, iy, seed);
        float b = hash(ix + 1, iy, seed);
        float c = hash(ix, iy + 1, seed);
        float d = hash(ix + 1, iy + 1, seed);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private static float fbm(float x, float y, int seed) {
        return noise(x, y, seed) * 0.65F + noise(x * 2.1F, y * 2.1F, seed + 7) * 0.35F;
    }

    private static int mix(int a, int b, float t) {
        return ARGB.srgbLerp(t, a, b) & 0xFFFFFF;
    }
}
