package com.rieno.gadgetsandgizmos.content;


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           IMPORTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.level.block.state.BlockState;
// import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
// import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

public final class AccDisplaySurfaceLayout{

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/


    // Half the standard display pixels
    private static final int HALF_BLOCK_PIXELS = AccDisplayBlockEntity.PIXELS_PER_BLOCK / 2;
    // Quater the standard display pixels
    private static final int QUATER_BLOCK_PIXELS = AccDisplayBlockEntity.PIXELS_PER_BLOCK / 4;

    private AccDisplaySurfaceLayout(){}

    
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           METHODS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get visible pixels per row
    public static int visiblePixelsPerRow(BlockState state){
        return usesHalfHeight(state) ? HALF_BLOCK_PIXELS : AccDisplayBlockEntity.PIXELS_PER_BLOCK;
    }

    // Get usable pixel height
    public static int visibleSurfaceHeight(BlockState state, int height){
        return Math.max(1, Math.max(1, height) * visiblePixelsPerRow(state) - AccDisplayBlockEntity.BORDER_PIXELS * 2);
    }

    // Get the top Y corrdinates
    public static int srcTopPixels(BlockState state){
        if(!usesHalfHeight(state)) return 0;
        return switch (state.getValue(AccDisplayBlock.Y_ALIGNMENT)){
            case NEGATIVE -> HALF_BLOCK_PIXELS;
            case CENTER -> QUATER_BLOCK_PIXELS;
            case POSITIVE -> 0;
        };
    }

    // Get the source height
    public static int srcHeightPixels(BlockState state){
        return visiblePixelsPerRow(state);
    }


    
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Is the block a half height block?
    private static boolean usesHalfHeight(BlockState state){
        AccDisplayBlock.DisplayType type = displayType(state);
        return type == AccDisplayBlock.DisplayType.SLAB || type == AccDisplayBlock.DisplayType.HALF_PANEL;
    }

    // Get the display type
    private static AccDisplayBlock.DisplayType displayType(BlockState state){
        if(!(state.getBlock() instanceof AccDisplayBlock block)) throw new IllegalArgumentException("ACC Display layout requires an AccDisplayBlock state");
        return block.displayType();
    }
}