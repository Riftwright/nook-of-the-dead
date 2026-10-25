package dev.nookofthedead.ending;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The vaccine, finished, in the sky base's laboratory ({@link dev.nookofthedead.world.SkyBase#vaccine}): its dispersal
 * console, a desk before the vessel. Nothing breaks it.
 * <p>
 * It will not start without its {@link #NEEDED} launch keys (the user, 2026-10-21: a console a button would start, left
 * unstarted, asks why; the keys' holders turned, and the work was done and could not be let go): each one used on it goes
 * in and stays ({@link #KEYS}). With all of them in, used, it lets the vaccine go over the If ({@link Ending}) - the film
 * the first time, at once after. While the vaccine goes out, used, it asks whether to stop it (the client's
 * confirmation, {@link FilmPayloads.AskStop}): stopped, the If is fallen again ({@link Cure#stop}) till it is started once
 * more.
 */
public class VaccineBlock extends Block {
    /** The launch keys it takes. */
    public static final int NEEDED = 4;
    /** How many are in. */
    public static final IntegerProperty KEYS = IntegerProperty.create("keys", 0, NEEDED);

    /** The desk (models/block/vaccine.json): its body, the sloped panel, the riser of lamps and gauge at its back. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 2, 15, 16, 16);

    public VaccineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KEYS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KEYS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A launch key, put in. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(dev.nookofthedead.registry.ZwItems.LAUNCH_KEY.get()) || state.getValue(KEYS) >= NEEDED) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            int keys = state.getValue(KEYS) + 1;
            server.setBlock(pos, state.setValue(KEYS, keys), Block.UPDATE_ALL);
            stack.consume(1, player);
            server.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F + 0.1F * keys);
            if (keys == NEEDED) {
                server.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
            player.sendOverlayMessage(Component.translatable("block.nookofthedead.vaccine.key_in", keys, NEEDED));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)) {
            return InteractionResult.SUCCESS;
        }
        if (!dev.nookofthedead.NookOfTheDead.fallen(server)) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(KEYS) < NEEDED) {
            server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.6F, 0.5F);
            user.sendOverlayMessage(Component.translatable("block.nookofthedead.vaccine.keys", state.getValue(KEYS), NEEDED)
                    .withStyle(ChatFormatting.GRAY));
        } else if (Cure.cured(server.getServer())) {
            FilmPayloads.send(user, new FilmPayloads.AskStop(pos));
        } else if (Cure.released(server.getServer())) {
            Ending.resume(server, user, pos);
            user.sendOverlayMessage(Component.translatable("block.nookofthedead.vaccine.resumed"));
        } else {
            Ending.start(server, user, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** {@code player} confirmed it: the console at {@code pos} stopped, if they are at it and it is going. */
    static void stop(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        if (!dev.nookofthedead.NookOfTheDead.fallen(level) || player.isSpectator() || !player.isAlive()
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64 || !level.mayInteract(player, pos)
                || !level.getBlockState(pos).is(dev.nookofthedead.registry.ZwBlocks.VACCINE.get()) || !Cure.cured(level.getServer())) {
            return;
        }
        Cure.stop(level.getServer());
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.6F);
        player.sendOverlayMessage(Component.translatable("block.nookofthedead.vaccine.stopped").withStyle(ChatFormatting.RED));
        dev.nookofthedead.NookOfTheDead.LOGGER.info("The vaccine stopped ({} stopped the console): the If is fallen again",
                player.getName().getString());
    }
}
