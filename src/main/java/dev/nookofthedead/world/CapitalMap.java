package dev.nookofthedead.world;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * For checking the capital's plan from outside the game ({@code /nzw capmap}): every column it owns as a pixel of
 * {@code nzw_capital.png} in the game's folder - each house its own colour, the streets grey (the main ways lighter),
 * the squares pale, the river and the moat blue, the quays and bridges sand, the wall and the gates near black, the crag
 * brown, the castle red, the great buildings purple, the ground outside green. No world is needed: it asks the plan only.
 */
public final class CapitalMap {
    public static String write(long seed, File file) throws IOException {
        Capital.Site s = Capital.site(seed);
        int size = Capital.ZONE * 2 + 1;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        int[] counts = new int[Capital.Kind.values().length];
        for (int dx = -Capital.ZONE; dx <= Capital.ZONE; dx++) {
            for (int dz = -Capital.ZONE; dz <= Capital.ZONE; dz++) {
                Capital.Column c = Capital.column(seed, s.x() + dx, s.z() + dz);
                int rgb;
                if (c == null) {
                    rgb = 0x202020;
                } else {
                    counts[c.kind().ordinal()]++;
                    rgb = switch (c.kind()) {
                        case APRON -> 0x5f7f3f;
                        case ROAD -> 0x9a8a6a;
                        case BERM -> 0x6f8f4f;
                        case MOAT, RIVER -> 0x3050a0;
                        case CAUSEWAY, BRIDGE, QUAY -> 0xc0b080;
                        case WALL, GATE, WATERGATE -> 0x181818;
                        case STREET -> c.upper() > 0 ? 0x606070 : c.block() == 0 ? 0xb0b0b0 : 0x808080;
                        case SQUARE -> 0xe0e0d0;
                        case BUILDING -> {
                            long h = c.block() * 0x9E3779B97F4A7C15L ^ c.parcel();
                            int v = (int) (h ^ h >>> 29);
                            int base = 0x40 + (v & 0x7f);
                            int edge = c.party() < 1 || c.edge() < 1 ? 0x30 : 0;
                            int r = Math.max(0, base + 0x30 - edge);
                            int g = Math.max(0, ((v >> 8) & 0x5f) + 0x30 - edge);
                            int b = Math.max(0, ((v >> 16) & 0x3f) + 0x20 - edge);
                            yield r << 16 | g << 8 | b;
                        }
                        case CRAG -> 0x6a5440;
                        case OLDWALL -> 0x403830;
                        case RAMP -> 0xd0c090;
                        case CASTLE -> 0xa03030;
                        case CATHEDRAL, HALL -> 0x8040a0;
                    };
                }
                image.setRGB(dx + Capital.ZONE, dz + Capital.ZONE, rgb);
            }
        }
        javax.imageio.ImageIO.write(image, "png", file);
        StringBuilder out = new StringBuilder("capital at " + s.x() + " " + s.z() + ":");
        for (Capital.Kind k : Capital.Kind.values()) {
            out.append(' ').append(k.name().toLowerCase()).append('=').append(counts[k.ordinal()]);
        }
        return out.toString();
    }

    /** Where to look ({@code /nzw capspots}): the gates, the castle's gate, the great buildings, the main bridge, the burnt quarter. */
    public static String spots(long seed) {
        Capital.Site s = Capital.site(seed);
        StringBuilder out = new StringBuilder("capital " + s.x() + " " + s.z() + " river " + Math.round(Math.toDegrees(s.river())));
        for (int k = 0; k < 4; k++) {
            out.append(" gate").append(k).append(' ').append(at(s, s.gate(k), Capital.WALL));
        }
        out.append(" castlegate ").append(at(s, s.gate(0) + Math.PI, CapitalCastle.CURTAIN + 2));
        CapitalLandmarks.Frame c = CapitalLandmarks.cathedral(s);
        CapitalLandmarks.Frame h = CapitalLandmarks.hall(s);
        out.append(" cathedral ").append(c.x()).append(',').append(c.z()).append(' ').append(c.front());
        out.append(" hall ").append(h.x()).append(',').append(h.z()).append(' ').append(h.front());
        out.append(" bridge ").append(at(s, Capital.avenue(s, 0, Capital.RIVER_OFF), Capital.RIVER_OFF));
        out.append(" burnt ").append(at(s, s.gate(0) + 0.3, Capital.RIVER_OFF + 40));
        return out.toString();
    }

    /**
     * For looking in the capital's houses ({@code /nzw capin dx dz storey}): into the nearest room of an unburnt house round
     * ({@code dx}, {@code dz}) from the middle at least two in from its walls, on storey {@code storey} if it has one; what
     * the room is.
     */
    public static String into(net.minecraft.server.level.ServerPlayer player, int dx, int dz, int storey) {
        long seed = player.level().getSeed();
        Capital.Site s = Capital.site(seed);
        for (int ring = 0; ring < 30; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x = s.x() + dx + i;
                    int z = s.z() + dz + j;
                    Capital.Column c = Capital.column(seed, x, z);
                    if (c == null || c.kind() != Capital.Kind.BUILDING || c.edge() < 2.5 || c.party() < 2.5 || CapitalDraw.burnt(s, c)) {
                        continue;
                    }
                    CapitalHouses.House h = CapitalHouses.house(s, c);
                    int k = Math.min(storey, h.storeys() - 1);
                    int y = h.level() + k * CapitalHouses.STOREY + 1;
                    player.level().getChunk(x >> 4, z >> 4);
                    player.teleportTo(player.level(), x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), 20, true);
                    return "in " + (x - s.x()) + " " + (z - s.z()) + ": storey " + k + " of " + h.storeys() + ", "
                            + CapitalRooms.room(h, k) + (k == 0 ? " " + CapitalRooms.trade(h) : "");
                }
            }
        }
        return "no house near";
    }

    private static String at(Capital.Site s, double angle, double r) {
        return (int) Math.floor(s.x() + Math.cos(angle) * r) + "," + (int) Math.floor(s.z() + Math.sin(angle) * r);
    }

    private CapitalMap() {}
}
