package com.rieno.gadgetsandgizmos.compat.create;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

// Route Create display data into the universal adapter
public final class UniversalDisplayAdapterTarget extends DisplayTarget {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Accept the text
    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext ctx) {
        if (ctx.getTargetBlockEntity() instanceof UniversalDisplayAdapterBlockEntity adapter) {
            adapter.acceptDisplayLinkText(line, text);
        }
    }

    // Get the provide stats
    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext ctx) {
        return new DisplayTargetStats(64, 128, this);
    }
}
