package dev.nookofthedead.ending;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookofthedead.NookOfTheDead;
import dev.nookofthedead.registry.ZwItems;
import dev.nookworks.ifworld.IfWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Launch keys lost for good ({@link dev.nookofthedead.item.LaunchKeyItem}), counted for the If they were lost in, and set
 * down again before the vaccine's console the next time a player comes up to it - so the end of the road can never be
 * closed by a key gone (the user, 2026-10-23). A new If counts from nothing.
 */
@EventBusSubscriber(modid = NookOfTheDead.MODID)
public final class LostKeys extends SavedData {
    private static final Codec<LostKeys> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("if").forGetter(k -> k.ifNumber),
            Codec.INT.fieldOf("lost").forGetter(k -> k.lost)).apply(i, LostKeys::new));
    public static final SavedDataType<LostKeys> TYPE = new SavedDataType<>(NookOfTheDead.id("lost_keys"), LostKeys::new, CODEC);

    /** How near the console a player comes for the lost keys to be set down there. */
    public static final double NEAR = 24.0;

    private String ifNumber;
    private int lost;

    private LostKeys() {
        this("", 0);
    }

    private LostKeys(String ifNumber, int lost) {
        this.ifNumber = ifNumber;
        this.lost = lost;
    }

    private static LostKeys get(MinecraftServer server) {
        LostKeys keys = server.getDataStorage().computeIfAbsent(TYPE);
        String now = IfWorld.get(server).number();
        if (!now.equals(keys.ifNumber)) {                         // another If: nothing lost in it yet
            keys.ifNumber = now;
            keys.lost = 0;
            keys.setDirty();
        }
        return keys;
    }

    /** {@code count} keys lost for good. */
    public static void lost(MinecraftServer server, int count) {
        LostKeys keys = get(server);
        keys.lost += count;
        keys.setDirty();
        NookOfTheDead.LOGGER.info("{} launch key(s) lost; they will be found again before the vaccine's console", count);
    }

    /** How many are waiting to be set down again. */
    public static int waiting(MinecraftServer server) {
        return get(server).lost;
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0 || player.isSpectator()
                || !NookOfTheDead.fallen(player.level())) {
            return;
        }
        ServerLevel level = player.level();
        LostKeys keys = get(level.getServer());
        if (keys.lost <= 0) {
            return;
        }
        BlockPos v = dev.nookofthedead.world.SkyBase.vaccine(dev.nookofthedead.world.Ascent.site(level.getSeed()));
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(v)) > NEAR * NEAR || !level.isLoaded(v)) {
            return;
        }
        BlockPos at = v.south();                                  // before the console, where one stands to use it
        ItemEntity key = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
                new ItemStack(ZwItems.LAUNCH_KEY.get(), keys.lost), 0, 0, 0);
        key.setPickUpDelay(10);
        level.addFreshEntity(key);
        NookOfTheDead.LOGGER.info("{} lost launch key(s) set down again before the console at {}", keys.lost, at);
        keys.lost = 0;
        keys.setDirty();
    }
}
