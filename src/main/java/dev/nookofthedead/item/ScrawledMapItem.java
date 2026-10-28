package dev.nookofthedead.item;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.world.Institute;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The scraps of a map put together (four {@code map_scrap}, the user, 2026-10-15: "ボスの居場所は、特殊なアイテムをいくつか
 * 集めないとわからない"): opened in the fallen world it becomes a map of where the nearest research institute stands, a red
 * cross on it, as an explorer's map is; anywhere else its marks match nothing.
 */
public class ScrawledMapItem extends Item {
    /** How far out it looks, in region cells. */
    public static final int REACH = 6;

    public ScrawledMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos site = NookOfTheDead.fallen(server) ? Institute.nearest(server.getSeed(), player.getBlockX(), player.getBlockZ(), REACH) : null;
        if (site == null) {
            player.sendOverlayMessage(Component.translatable("message.nookofthedead.map_matches_nothing").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        ItemStack map = MapItem.create(server, site.getX(), site.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(server, map);
        MapItemSavedData.addTargetDecoration(map, site, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.nookofthedead.institute_map"));
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        ItemStack held = player.getItemInHand(hand);
        held.consume(1, player);
        if (held.isEmpty()) {
            // the map handed back as what the scrawl became: after a use the game puts that in the hand
            // (ItemStack#use, ServerPlayerGameMode#useItem), so a map set in the hand here was overwritten with the
            // emptied scrawl and lost - in survival the institute's map never came (found filming, 2026-10-29)
            return InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(map);
        }
        player.getInventory().placeItemBackInInventory(map, net.minecraft.util.Prediction.SERVER_ONLY);
        return InteractionResult.SUCCESS_SERVER;
    }
}
