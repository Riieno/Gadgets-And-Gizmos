package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

// Add each supporter head as a fixed-price wandering trader offer
public final class SupporterHeadWanderingTraderTrades {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int EMERALD_COST = 1;
    private static final int MAX_USES = 12;
    private static final int TRADER_EXPERIENCE = 1;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the supporter head wandering trader trades
    private SupporterHeadWanderingTraderTrades() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the supporter head trade entries
    public static void addTrades(WandererTradesEvent evt) {
        if (!CTFeatureToggles.isItemEnabled("player_mannequin")) return;
        for (PlayerMannequinVariant variant : PlayerMannequinVariants.all()) {
            evt.getGenericTrades().add((trader, random) -> createOffer(variant));
        }
    }

    // Create one fixed-price supporter head offer
    private static MerchantOffer createOffer(PlayerMannequinVariant variant) {
        ItemStack stack = SupporterHeads.createStack(variant);
        if (stack.isEmpty()) return null;
        return new MerchantOffer(new ItemCost(Items.EMERALD, EMERALD_COST), stack,
                MAX_USES, TRADER_EXPERIENCE, 0.0F);
    }
}
