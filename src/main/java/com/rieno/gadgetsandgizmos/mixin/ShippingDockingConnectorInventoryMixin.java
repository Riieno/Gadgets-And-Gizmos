package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorAccess;
import com.rieno.gadgetsandgizmos.content.WirelessDockingTransfer;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorDuoInventory;
import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add shipping Docking Connector Inventory access to Shipping's docking connector
@Mixin(value = DockingConnectorDuoInventory.class, remap = false)
public abstract class ShippingDockingConnectorInventoryMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current our connector
    @Unique private DockingConnectorBlockEntity createthrusters$ourConnector;
    // Current their connector
    @Unique private DockingConnectorBlockEntity createthrusters$theirConnector;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remember the connector
    @Inject(method = "<init>", at = @At("RETURN"))
    private void createthrusters$rememberConnector(
            DockingConnectorBlockEntity ourConnector,
            DockingConnectorBlockEntity theirConnector,
            CallbackInfo callback
    ) {
        createthrusters$ourConnector = ourConnector;
        createthrusters$theirConnector = theirConnector;
    }

    // Filter the general insert
    @Inject(method = "insertGeneral", at = @At("HEAD"), cancellable = true)
    private void createthrusters$filterGeneralInsert(
            ItemInfoWrapper info,
            int amountToInsert,
            boolean simulate,
            CallbackInfoReturnable<Integer> callback
    ) {
        ItemStack stack = ItemInfoWrapper.generateFromInfo(info);
        if (!createthrusters$allows(stack)) {
            callback.setReturnValue(0);
            return;
        }
        if (!WirelessDockingTransfer.routesItems(
                createthrusters$ourConnector, createthrusters$theirConnector)) {
            return;
        }
        ItemStack offered = stack.copyWithCount(Math.max(0, amountToInsert));
        ItemStack remainder = WirelessDockingTransfer.insertItem(
                createthrusters$ourConnector, createthrusters$theirConnector,
                offered, simulate);
        int networkAccepted = offered.getCount() - remainder.getCount();
        int connectorAccepted = remainder.isEmpty() ? 0
                : createthrusters$theirConnector.inventory.insertGeneral(
                        info, remainder.getCount(), simulate);
        callback.setReturnValue(networkAccepted + connectorAccepted);
    }

    // Filter the slot insert
    @Inject(method = "insertSlot", at = @At("HEAD"), cancellable = true)
    private void createthrusters$filterSlotInsert(
            ItemStack stack,
            int slot,
            boolean simulate,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (!createthrusters$allows(stack)) {
            callback.setReturnValue(stack);
            return;
        }
        if (slot != 0 || !WirelessDockingTransfer.routesItems(
                createthrusters$ourConnector, createthrusters$theirConnector)) {
            return;
        }
        ItemStack remainder = WirelessDockingTransfer.insertItem(
                createthrusters$ourConnector, createthrusters$theirConnector,
                stack, simulate);
        callback.setReturnValue(remainder.isEmpty() ? ItemStack.EMPTY
                : createthrusters$theirConnector.inventory.insertSlot(
                        remainder, 0, simulate));
    }

    // Route the general extraction
    @Inject(method = "extractGeneral", at = @At("HEAD"), cancellable = true)
    private void createthrusters$routeGeneralExtraction(
            ItemInfoWrapper info,
            int amountToExtract,
            boolean simulate,
            CallbackInfoReturnable<Integer> callback
    ) {
        if (!WirelessDockingTransfer.routesItems(
                createthrusters$ourConnector, createthrusters$theirConnector)) {
            return;
        }
        int direct = createthrusters$ourConnector.inventory.extractGeneral(
                info, amountToExtract, simulate);
        ItemStack wireless = WirelessDockingTransfer.extractItem(
                createthrusters$ourConnector, createthrusters$theirConnector,
                ItemInfoWrapper.generateFromInfo(info),
                Math.max(0, amountToExtract - direct), simulate);
        callback.setReturnValue(direct + wireless.getCount());
    }

    // Route the slot extraction
    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void createthrusters$routeSlotExtraction(
            int slot,
            int amountToExtract,
            boolean simulate,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (slot != 1 || !WirelessDockingTransfer.routesItems(
                createthrusters$ourConnector, createthrusters$theirConnector)) {
            return;
        }
        ItemStack direct = createthrusters$ourConnector.inventory.extractSlot(
                0, amountToExtract, simulate);
        ItemStack wireless = WirelessDockingTransfer.extractItem(
                createthrusters$ourConnector, createthrusters$theirConnector,
                direct.isEmpty() ? null : direct,
                Math.max(0, amountToExtract - direct.getCount()), simulate);
        if (direct.isEmpty()) {
            callback.setReturnValue(wireless);
            return;
        }
        if (!wireless.isEmpty()) {
            direct.grow(wireless.getCount());
        }
        callback.setReturnValue(direct);
    }

    // Handle the advertise wireless item
    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void createthrusters$advertiseWirelessItem(
            int slot,
            CallbackInfoReturnable<ItemStack> callback
    ) {
        if (slot != 1 || !createthrusters$ourConnector.inventory.isEmpty()
                || !WirelessDockingTransfer.routesItems(
                createthrusters$ourConnector, createthrusters$theirConnector)) {
            return;
        }
        ItemStack advertised = WirelessDockingTransfer.peekItem(
                createthrusters$ourConnector, createthrusters$theirConnector);
        if (!advertised.isEmpty()) {
            callback.setReturnValue(advertised);
        }
    }

    // Check if this allows the value
    @Unique
    private boolean createthrusters$allows(ItemStack stack) {
        return !(createthrusters$ourConnector instanceof ShippingDockingConnectorAccess access)
                || access.createthrusters$allowsItemTransfer(stack);
    }
}
