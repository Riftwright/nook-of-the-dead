package dev.nookofthedead.world;

import java.util.Locale;
import java.util.Optional;

import dev.nookofthedead.NookOfTheDead;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A secret, not in the guide (the user, 2026-10-19: "nookworksのnookダウジングで、最初の研究所（ヴァンダムがいるとこ）が見つかる
 * （ここは裏仕様。ガイドにも書かない）"): in the If, Nookworks' Nook Dowsing Rod pulls toward the nearest institute instead
 * of cinnabar - always, near one or far (the user, by way of the Nookworks session, 2026-10-19: the fallen If likely has
 * no cinnabar to find) - its needle set there as when it finds something, worn by a use, three seconds before the next.
 * A cinnabar search of the player's still running (begun before, elsewhere) is left to answer first, as the rod does.
 * Nookworks is not changed: without this mod the rod is as it was (its session's advice: answer the rod's use in the If
 * before it is used).
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class Dowsing {
    /** How far out it looks, in blocks of the town's grid. */
    static final int REACH = 60;

    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack rod = event.getItemStack();
        if (!(event.getEntity() instanceof ServerPlayer player) || !rod.is(dev.nookworks.registry.ModItems.NOOK_DOWSING_ROD.get())
                || !player.level().dimension().equals(IfWorld.KEY) || player.getCooldowns().isOnCooldown(rod)) {
            return;
        }
        if (dev.nookworks.dowsing.DowsingSearch.running(player.getUUID())) {
            return;                                   // its own search still out: it says so, and the needle is that search's
        }
        ServerLevel level = player.level();
        BlockPos institute = nearest(level, player.blockPosition());
        if (institute == null) {
            return;
        }
        rod.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), institute)), false));
        double dx = institute.getX() - player.getX();
        double dz = institute.getZ() - player.getZ();
        long distance = Math.max(10, Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10);
        player.sendOverlayMessage(Component.translatable("message.nookofthedead.dowsing", String.format(Locale.ROOT, "%,d", distance)));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.9F);
        rod.hurtAndBreak(1, player, event.getHand());
        player.getCooldowns().addCooldown(rod, 60);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** The middle of the nearest institute to {@code from}, out to {@link #REACH} blocks of the grid; null if none. */
    public static BlockPos nearest(ServerLevel level, BlockPos from) {
        long seed = level.getSeed();
        int bx0 = Math.floorDiv(from.getX(), Town.BLOCK);
        int bz0 = Math.floorDiv(from.getZ(), Town.BLOCK);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int r = 0; r <= REACH; r++) {
            if (best != null && (r - 1) * Town.BLOCK > Math.sqrt(bestDistance)) {
                break;                                                   // none further out can be nearer
            }
            for (int bx = bx0 - r; bx <= bx0 + r; bx++) {
                for (int bz = bz0 - r; bz <= bz0 + r; bz++) {
                    if (Math.max(Math.abs(bx - bx0), Math.abs(bz - bz0)) != r || !Institute.at(seed, bx, bz)) {
                        continue;
                    }
                    for (Town.Lot lot : Town.lots(seed, bx, bz)) {
                        if (lot.building().map(Town.INSTITUTE::equals).orElse(false)) {
                            BlockPos middle = lot.corner().offset(lot.size() / 2, 0, lot.size() / 2);
                            double d = middle.distSqr(from.atY(middle.getY()));
                            if (d < bestDistance) {
                                bestDistance = d;
                                best = middle;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private Dowsing() {}
}
