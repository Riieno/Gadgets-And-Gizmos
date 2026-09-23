package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticHostBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

// Pass cogwheel rotation in one selectable direction
public class RatchetCogwheelBlock extends CogWheelBlock implements VirtualKineticHostBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ratchet cogwheel block
    public RatchetCogwheelBlock(boolean large, Properties properties) {
        super(large, properties);
        registerDefaultState(defaultBlockState().setValue(REVERSED, false));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the block state definition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REVERSED);
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return CTBlockEntities.RATCHET_COGWHEEL.get();
    }

    // Keep the real kinetic node on the cog while the virtual node owns the shaft
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    // Keep both virtual kinetic networks when only the gate direction changes
    @Override
    public boolean ct$areVirtualKineticStatesEquivalent(BlockState prev, BlockState next) {
        return areStatesKineticallyEquivalent(prev, next);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Flip the allowed rotation direction with an empty hand
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            BlockState next = state.cycle(REVERSED);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable(next.getValue(REVERSED)
                    ? "createthrusters.ratchet_cogwheel.direction.reversed"
                    : "createthrusters.ratchet_cogwheel.direction.forward"), true);
        }
        return InteractionResult.SUCCESS;
    }
}
