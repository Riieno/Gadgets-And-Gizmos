package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.item.ItemStack;

// Expose Shipping docking connector handlers
public interface ShippingDockingConnectorAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Configure the shipping transfers
    void createthrusters$configureShippingTransfers(
            boolean items,
            boolean packages,
            String packageAddress,
            boolean fluids,
            boolean energy
    );

    // Reset the shipping transfers
    void createthrusters$resetShippingTransfers();

    // Check if this allows item transfer
    boolean createthrusters$allowsItemTransfer(ItemStack stack);

    // Check if this allows fluid transfer
    boolean createthrusters$allowsFluidTransfer();

    // Check if this allows energy transfer
    boolean createthrusters$allowsEnergyTransfer();
}
