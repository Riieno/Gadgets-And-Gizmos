package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

// Give the advanced controller its embedded shape, runtime block entity and server ticker
public class AdvancedContraptionControllerBlock extends AnalogueContraptionControllerBlock {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller block
    public AdvancedContraptionControllerBlock(Properties properties) {
        super(properties);
    }

    // Record the player placing the ACC before it joins a ship
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if(!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof AdvancedContraptionControllerBlockEntity controller){
            controller.setShipPermissionPlacerId(player.getUUID());
        }
    }

    // Release the ship when its controller is removed, including embedded SCM mounts
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean isMoving){
        if(!level.isClientSide && !isMoving && state.getBlock() != next.getBlock()
                && level.getBlockEntity(pos) instanceof AdvancedContraptionControllerBlockEntity controller
                && !controller.isAssemblyTransferPending()){
            ShipPermissions.unclaim(level, pos);
        }
        super.onRemove(state, level, pos, next, isMoving);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the controller shape
    @Override
    protected VoxelShape getControllerShape(BlockState state) {
        Direction surfaceNormal = state.getValue(FACING);
        Direction horizontalFacing = state.getValue(HORIZONTAL_FACING);
        int mountOffset = state.getValue(EMBEDDED_SLAB) ? state.getValue(MOUNT_OFFSET) : 0;
        return AdvancedContraptionControllerShapes.shapeForMount(
                surfaceNormal, horizontalFacing, mountOffset);
    }

    // Create the block entity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedContraptionControllerBlockEntity(pos, state);
    }

    // Get the ticker
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide && type == CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<AdvancedContraptionControllerBlockEntity>)
                    AdvancedContraptionControllerBlockEntity::tickServer;
        }
        return null;
    }
}
