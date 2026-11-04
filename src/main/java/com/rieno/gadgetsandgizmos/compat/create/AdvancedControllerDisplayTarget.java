package com.rieno.gadgetsandgizmos.compat.create;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.display.DisplayLinkData;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

// Accept independent Create display streams into the ACC graph
public final class AdvancedControllerDisplayTarget extends DisplayTarget{
    @Override public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext ctx){
        if(ctx.level().isClientSide || !(ctx.getTargetBlockEntity() instanceof AdvancedContraptionControllerBlockEntity controller)) return;
        var snapshot = DisplayLinkData.read(ctx.blockEntity());
        if(snapshot == null) snapshot = DisplayLinkData.capture(ctx, line, text);
        controller.acceptDisplayLink(snapshot);
    }

    @Override public DisplayTargetStats provideStats(DisplayLinkContext ctx){ return new DisplayTargetStats(64, 128, this); }
}
