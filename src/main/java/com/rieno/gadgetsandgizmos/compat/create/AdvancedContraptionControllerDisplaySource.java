package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// Show ACC data on Create displays
public class AdvancedContraptionControllerDisplaySource extends NumericSingleLineDisplaySource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the provide line
    @Override
    protected MutableComponent provideLine(DisplayLinkContext ctx, DisplayTargetStats stats) {
        if (!(ctx.getSourceBlockEntity() instanceof AdvancedContraptionControllerBlockEntity controller)) {
            return EMPTY_LINE;
        }
        return Component.literal(controller.getCombinedOutputSignalText());
    }

    // Check if this allows labeling
    @Override
    protected boolean allowsLabeling(DisplayLinkContext ctx) {
        return true;
    }

    // Get the passive refresh ticks
    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }

    // Check if the display should reset passively
    @Override
    public boolean shouldPassiveReset() {
        return false;
    }
}
