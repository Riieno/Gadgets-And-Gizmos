package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;

// Handle Vertical Axis Variant Block
public class VerticalAxisVariantBlockItem extends CTTooltipBlockItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Description id
    private final String descriptionId;

    // Force axis
    private final Direction.Axis forceAxis;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vertical axis variant block item
    public VerticalAxisVariantBlockItem(Block block, Item.Properties properties, String descriptionId) {
        this(block, properties, descriptionId, null);
    }

    // Initialize the vertical axis variant block item
    public VerticalAxisVariantBlockItem(Block block, Item.Properties properties, String descriptionId,
                                        Direction.Axis forceAxis) {
        super(block, properties);
        this.descriptionId = descriptionId;
        this.forceAxis = forceAxis;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the description id
    @Override
    public String getDescriptionId() {
        return descriptionId;
    }

    // Register the blocks
    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {

    }

    // Update the custom block entity tag
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.AXIS)) {
            return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        }

        Direction.Axis axis;
        if (forceAxis != null) {
            axis = forceAxis;
        } else {
            Direction.Axis preferredAxis = null;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos adjacentPos = pos.relative(side);
                BlockState adjacentState = level.getBlockState(adjacentPos);
                if (!(adjacentState.getBlock() instanceof IRotate rotate)
                        || !rotate.hasShaftTowards((LevelReader) level, adjacentPos, adjacentState, side.getOpposite())) {
                    continue;
                }

                if (preferredAxis != null && preferredAxis != side.getAxis()) {
                    preferredAxis = null;
                    break;
                }

                preferredAxis = side.getAxis();
            }

            if (preferredAxis == null) {
                Direction.Axis fallback = player.getDirection().getClockWise().getAxis();

                axis = (fallback == Direction.Axis.Y) ? Direction.Axis.Z : fallback;
            } else {
                axis = (preferredAxis == Direction.Axis.X) ? Direction.Axis.Z : Direction.Axis.X;
            }
        }

        BlockState updatedState = state.setValue(BlockStateProperties.AXIS, axis);
        if (updatedState.hasProperty(BlockStateProperties.FACING) && axis != Direction.Axis.Y) {

            updatedState = updatedState.setValue(BlockStateProperties.FACING, Direction.UP);
        }

        level.setBlockAndUpdate(pos, updatedState);
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }
}
