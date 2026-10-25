package dev.nookofthedead.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where the capital's crows wheel ({@link dev.nookofthedead.entity.Crows}): over its spires and towers - the keep's spire,
 * the cathedral's crossing and its two towers, the council's clock tower, and four of the castle's wall towers - high,
 * a little above each top.
 */
public final class CapitalRoosts {
    private static volatile long knownSeed = Long.MIN_VALUE;
    private static volatile List<BlockPos> known = List.of();

    public static List<BlockPos> of(long seed) {
        if (knownSeed == seed) {
            return known;
        }
        Capital.Site s = Capital.site(seed);
        List<BlockPos> roosts = new ArrayList<>();
        // the keep's spire, over the castle's axes (forward to the gate, across)
        double g = s.gate(0) + Math.PI;
        double f = CapitalCastle.KEEP_F;
        int keepTop = Capital.TOP + CapitalCastle.KEEP_STOREY * CapitalCastle.KEEP_STOREYS + 30;
        roosts.add(BlockPos.containing(s.x() + Math.cos(g) * f, keepTop, s.z() + Math.sin(g) * f));
        // the castle's wall towers, four of them round
        for (int k = 0; k < CapitalCastle.TOWERS; k += 3) {
            double ta = g + Math.PI / CapitalCastle.TOWERS + k * 2 * Math.PI / CapitalCastle.TOWERS;
            double tr = CapitalCastle.CURTAIN + CapitalCastle.CURTAIN_T / 2.0;
            roosts.add(BlockPos.containing(s.x() + Math.cos(ta) * tr, Capital.TOP + CapitalCastle.CURTAIN_H + 16, s.z() + Math.sin(ta) * tr));
        }
        // the cathedral: its crossing, its towers
        CapitalLandmarks.Frame c = CapitalLandmarks.cathedral(s);
        Direction in = c.front().getOpposite();
        Direction side = c.front().getClockWise();
        int mid = (CapitalLandmarks.TRANSEPT_FROM + CapitalLandmarks.TRANSEPT_TO) / 2;
        roosts.add(new BlockPos(c.x() + in.getStepX() * mid, c.level() + 50, c.z() + in.getStepZ() * mid));
        for (int b = -1; b <= 1; b += 2) {
            int off = b * (CapitalLandmarks.NAVE_W - 1);
            roosts.add(new BlockPos(c.x() + in.getStepX() * 4 + side.getStepX() * off, c.level() + 58, c.z() + in.getStepZ() * 4 + side.getStepZ() * off));
        }
        // the council's clock tower
        CapitalLandmarks.Frame h = CapitalLandmarks.hall(s);
        Direction hin = h.front().getOpposite();
        roosts.add(new BlockPos(h.x() + hin.getStepX() * 3, h.level() + 44, h.z() + hin.getStepZ() * 3));
        known = List.copyOf(roosts);
        knownSeed = seed;
        return known;
    }

    private CapitalRoosts() {}
}
