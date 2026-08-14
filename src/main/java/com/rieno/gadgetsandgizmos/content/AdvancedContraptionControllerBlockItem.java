package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

// Place advanced controllers through the shared controller item behavior
public class AdvancedContraptionControllerBlockItem extends AnalogueContraptionControllerBlockItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller block item
    public AdvancedContraptionControllerBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }
}
