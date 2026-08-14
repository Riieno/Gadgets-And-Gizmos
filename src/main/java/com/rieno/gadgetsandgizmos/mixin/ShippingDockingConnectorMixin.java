package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorAccess;
import com.simibubi.create.content.logistics.box.PackageItem;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Add shipping Docking Connector access to Shipping's docking connector
@Mixin(value = DockingConnectorBlockEntity.class, remap = false)
public abstract class ShippingDockingConnectorMixin implements ShippingDockingConnectorAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Controls whether items are being shipped
    @Unique private boolean createthrusters$shippingItems = true;
    // Controls whether packages are being shipped
    @Unique private boolean createthrusters$shippingPackages = true;
    // Controls whether fluids are being shipped
    @Unique private boolean createthrusters$shippingFluids = true;
    // Controls whether energy is being shipped
    @Unique private boolean createthrusters$shippingEnergy = true;
    // Current shipping package address
    @Unique private String createthrusters$shippingPackageAddress = "";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Configure the shipping transfers
    @Override
    public void createthrusters$configureShippingTransfers(
            boolean items,
            boolean packages,
            String packageAddress,
            boolean fluids,
            boolean energy
    ) {
        createthrusters$shippingItems = items;
        createthrusters$shippingPackages = packages;
        createthrusters$shippingPackageAddress = packageAddress == null ? "" : packageAddress.trim();
        createthrusters$shippingFluids = fluids;
        createthrusters$shippingEnergy = energy;
    }

    // Reset the shipping transfers
    @Override
    public void createthrusters$resetShippingTransfers() {
        createthrusters$shippingItems = true;
        createthrusters$shippingPackages = true;
        createthrusters$shippingPackageAddress = "";
        createthrusters$shippingFluids = true;
        createthrusters$shippingEnergy = true;
    }

    // Check if this allows item transfer
    @Override
    public boolean createthrusters$allowsItemTransfer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (!PackageItem.isPackage(stack)) {
            return createthrusters$shippingItems;
        }
        return createthrusters$shippingPackages
                && (createthrusters$shippingPackageAddress.isBlank()
                || PackageItem.matchAddress(stack, createthrusters$shippingPackageAddress));
    }

    // Check if this allows fluid transfer
    @Override
    public boolean createthrusters$allowsFluidTransfer() {
        return createthrusters$shippingFluids;
    }

    // Check if this allows energy transfer
    @Override
    public boolean createthrusters$allowsEnergyTransfer() {
        return createthrusters$shippingEnergy;
    }
}
