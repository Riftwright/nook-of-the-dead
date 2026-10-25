package dev.nookofthedead.world;

import org.jspecify.annotations.Nullable;

import dev.nookofthedead.registry.ZwBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a ruin's {@code [zombie N]} or {@code [elite]} sign becomes when the ruin is made: an unseen, untouchable block that
 * lets its {@link #COUNT} zombies out (one fallen player, if {@link #ELITE}) when someone comes near, and is gone
 * ({@link ZombieMarkerBlockEntity}). Kept zombies rather than spawned ones: they are not there to be despawned while
 * nobody is about, and they come out of the house the builder put them in.
 */
public class ZombieMarkerBlock extends BaseEntityBlock {
    public static final int MAX = 8;
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, MAX);
    public static final BooleanProperty ELITE = BooleanProperty.create("elite");

    public ZombieMarkerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(COUNT, 1).setValue(ELITE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COUNT, ELITE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZombieMarkerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ZwBlocks.ZOMBIE_MARKER_ENTITY.get(), ZombieMarkerBlockEntity::serverTick);
    }

    /** A marker for {@code count} zombies (as many as {@link #MAX}), or one fallen player. */
    public static BlockState of(int count, boolean elite) {
        return ZwBlocks.ZOMBIE_MARKER.get().defaultBlockState()
                .setValue(COUNT, Math.max(1, Math.min(MAX, count))).setValue(ELITE, elite);
    }
}
