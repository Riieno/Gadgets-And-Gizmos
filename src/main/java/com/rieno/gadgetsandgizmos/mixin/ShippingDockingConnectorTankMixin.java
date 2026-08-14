package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorAccess;
import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorTankOwner;
import com.rieno.gadgetsandgizmos.content.WirelessDockingTransfer;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorTank;
import dev.simulated_team.simulated.multiloader.tanks.CFluidType;
import dev.simulated_team.simulated.multiloader.tanks.SingleTank;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add shipping Docking Connector Tank access to Shipping's docking connector
@Mixin(value = DockingConnectorTank.class, remap = false)
public abstract class ShippingDockingConnectorTankMixin
        implements ShippingDockingConnectorTankOwner {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current bound block entity
    @Shadow @Final private DockingConnectorBlockEntity blockEntity;
    // Current connected tank
    @Shadow private DockingConnectorTank connectedTank;
    // Tracks whether shipping docking connector tank is inserting
    @Shadow private boolean inserting;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the connector owner
    @Override
    public DockingConnectorBlockEntity createthrusters$getConnectorOwner() {
        return blockEntity;
    }

    // Route the fluid insert
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void createthrusters$routeFluidInsert(
            CFluidType insertedType,
            long maxAmount,
            boolean simulate,
            Runnable beforeApply,
            CallbackInfoReturnable<Long> callback
    ) {
        if (blockEntity instanceof ShippingDockingConnectorAccess access
                && !access.createthrusters$allowsFluidTransfer()) {
            callback.setReturnValue(0L);
            return;
        }
        DockingConnectorBlockEntity shipConnector = blockEntity.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesFluids(
                blockEntity, shipConnector)) {
            return;
        }
        SingleTank self = (SingleTank) (Object) this;
        long requested = Math.min(Math.max(0L, maxAmount), self.capacity);
        FluidStack offered = insertedType == null || insertedType.isBlank()
                ? FluidStack.EMPTY
                : new FluidStack(insertedType.fluid,
                (int) Math.min(Integer.MAX_VALUE, requested));
        int networkAccepted = WirelessDockingTransfer.insertFluid(
                blockEntity, shipConnector, offered, true);
        long remaining = Math.max(0L, requested - networkAccepted);
        long connectorAccepted = connectedTank == null ? 0L
                : SingleTank.calculateInsert(connectedTank, insertedType, remaining);
        if (simulate) {
            callback.setReturnValue(networkAccepted + connectorAccepted);
            return;
        }
        if (networkAccepted + connectorAccepted > 0L && beforeApply != null) {
            beforeApply.run();
        }
        int actualNetwork = WirelessDockingTransfer.insertFluid(
                blockEntity, shipConnector, offered, false);
        long actualRemaining = Math.max(0L, requested - actualNetwork);
        long actualConnector = connectedTank == null ? 0L
                : SingleTank.calculateInsert(connectedTank, insertedType, actualRemaining);
        if (connectedTank != null && actualConnector > 0L) {
            SingleTank.applyInsert(connectedTank, insertedType, actualConnector);
        }
        inserting = true;
        callback.setReturnValue(actualNetwork + actualConnector);
    }

    // Route the fluid extraction
    @Inject(method = "extract", at = @At("HEAD"), cancellable = true)
    private void createthrusters$routeFluidExtraction(
            CFluidType extractedType,
            long maxAmount,
            boolean simulate,
            Runnable beforeApply,
            CallbackInfoReturnable<Long> callback
    ) {
        inserting = false;
        DockingConnectorBlockEntity shipConnector = blockEntity.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesFluids(
                blockEntity, shipConnector)) {
            return;
        }
        SingleTank self = (SingleTank) (Object) this;
        long requested = Math.min(Math.max(0L, maxAmount), self.capacity);
        long direct = SingleTank.calculateExtract(self, extractedType, requested);
        int wirelessRequest = (int) Math.min(
                Integer.MAX_VALUE, Math.max(0L, requested - direct));
        FluidStack filter = extractedType == null || extractedType.isBlank()
                ? FluidStack.EMPTY : new FluidStack(extractedType.fluid, wirelessRequest);
        FluidStack network = WirelessDockingTransfer.extractFluid(
                blockEntity, shipConnector, filter, wirelessRequest, true);
        if (simulate) {
            callback.setReturnValue(direct + network.getAmount());
            return;
        }
        if (direct + network.getAmount() > 0L && beforeApply != null) {
            beforeApply.run();
        }
        if (direct > 0L) {
            SingleTank.applyExtract(self, direct);
        }
        FluidStack actualNetwork = WirelessDockingTransfer.extractFluid(
                blockEntity, shipConnector, filter, wirelessRequest, false);
        callback.setReturnValue(direct + actualNetwork.getAmount());
    }
}
