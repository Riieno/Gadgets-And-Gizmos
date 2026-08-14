package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.trains.schedule.ScheduleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// Add addon schedule instructions to the standard Shipping schedule tooltip
public class ShippingScheduleItem extends ScheduleItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping schedule item
    public ShippingScheduleItem(Item.Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        var schedule = getSchedule(ctx.registries(), stack);
        ShippingAutoRefuelSettings settings = ShippingAutoRefuelSettings.fromSchedule(schedule);
        if (settings.enabled()) {
            tooltip.add(Component.literal("Auto Refuel below " + settings.thresholdPercent()
                    + "% at " + settings.dockFilter()).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("The first manifest instruction is active for the full schedule")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
