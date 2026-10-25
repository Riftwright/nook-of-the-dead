package dev.nookofthedead.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * What the Vandal leaves (the user's idea, 2026-10-15: "ボスを倒すと、抑制ポーションが自前で生成できるようになる"): a vial
 * of its blood. In the crafting grid with rotten flesh it makes cleansed flesh, the brewing stand's way to the
 * suppressant (awkward potion + cleansed flesh) - and the vial comes back, a little less in it each time, for
 * {@link #USES} uses.
 */
public class VandalBloodItem extends Item {
    public static final int USES = 16;

    public VandalBloodItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack)) {
            return super.getCraftingRemainder(instance);
        }
        ItemStack left = stack.copyWithCount(1);
        left.setDamageValue(left.getDamageValue() + 1);
        return left.getDamageValue() >= left.getMaxDamage() ? null : ItemStackTemplate.fromNonEmptyStack(left);
    }
}
