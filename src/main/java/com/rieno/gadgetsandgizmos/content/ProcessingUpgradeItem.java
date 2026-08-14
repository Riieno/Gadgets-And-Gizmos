package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

// Carry one tier of speed or efficiency processing for a thruster
public class ProcessingUpgradeItem extends Item {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Processing upgrade item type
    private final ThrusterBlockEntity.ProcessingUpgradeType type;
    // Tier
    private final int tier;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the processing upgrade item
    public ProcessingUpgradeItem(Properties properties, ThrusterBlockEntity.ProcessingUpgradeType type, int tier) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    public ThrusterBlockEntity.ProcessingUpgradeType getType() {
        return type;
    }

    // Get the tier
    public int getTier() {
        return tier;
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        CTTooltipHelper.addCreateDescription(this, tooltip);
    }
}
