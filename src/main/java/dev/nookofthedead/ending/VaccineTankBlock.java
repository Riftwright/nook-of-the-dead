package dev.nookofthedead.ending;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The vaccine in its vessel, behind the console in the sky base's laboratory ({@link dev.nookofthedead.world.SkyBase#apparatus}):
 * a pale blue that lights the room, the sky seen through it. Let go ({@link Ending}), it runs out and the glass stands
 * empty. Nothing breaks it.
 */
public class VaccineTankBlock extends TransparentBlock {
    public static final BooleanProperty FULL = BooleanProperty.create("full");

    public VaccineTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FULL, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FULL);
    }
}
