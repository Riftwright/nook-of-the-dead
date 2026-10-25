package dev.nookofthedead.item;

import dev.nookofthedead.entity.Grenade;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/**
 * The makeshift grenade ({@link Grenade}): thrown as a bottle is, a little further, one a second; a dispenser throws it
 * too.
 */
public class GrenadeItem extends Item implements ProjectileItem {
    /** Between two throws. */
    public static final int COOLDOWN = 20;

    public GrenadeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F,
                0.7F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel server) {
            Projectile.spawnProjectileFromRotation(Grenade::new, server, stack, player, -10.0F, 0.9F, 1.0F);
        }
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        return new Grenade(level, position.x(), position.y(), position.z(), stack);
    }
}
