package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorTankOwner;
import com.rieno.gadgetsandgizmos.content.WirelessDockingTransfer;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.multiloader.tanks.SingleTank;
import dev.simulated_team.simulated.multiloader.tanks.neoforge.SingleTankWrapper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add shipping Docking Connector Fluid Wrapper access to Shipping's docking connector
@Mixin(value = SingleTankWrapper.class, remap = false)
public abstract class ShippingDockingConnectorFluidWrapperMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current tank
    @Shadow @Final private SingleTank tank;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Unique
    private static final ThreadLocal<Boolean> CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE =
            ThreadLocal.withInitial(() -> false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the advertise wireless fluid
    @Inject(method = "getFluid", at = @At("HEAD"), cancellable = true)
    private void createthrusters$advertiseWirelessFluid(
            CallbackInfoReturnable<FluidStack> callback
    ) {
        if (tank.amount > 0L
                || !(tank instanceof ShippingDockingConnectorTankOwner owner)
                || !(owner.createthrusters$getConnectorOwner()
                instanceof DockingConnectorBlockEntity stationConnector)) {
            return;
        }
        if (CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE.get()) {
            return;
        }
        DockingConnectorBlockEntity shipConnector = stationConnector.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesFluids(
                stationConnector, shipConnector)) {
            return;
        }
        CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE.set(true);
        try {
            FluidStack advertised = WirelessDockingTransfer.peekFluid(
                    stationConnector, shipConnector);
            if (!advertised.isEmpty()) {
                callback.setReturnValue(advertised.copyWithAmount((int) Math.min(
                        Integer.MAX_VALUE, Math.min(tank.capacity, advertised.getAmount()))));
            }
        } finally {
            CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE.remove();
        }
    }

    // Drain the advertised wireless fluid
    @Inject(method = "drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("HEAD"), cancellable = true)
    private void createthrusters$drainAdvertisedWirelessFluid(
            int maxDrain,
            IFluidHandler.FluidAction action,
            CallbackInfoReturnable<FluidStack> callback
    ) {
        if (tank.amount > 0L || maxDrain <= 0
                || !(tank instanceof ShippingDockingConnectorTankOwner owner)
                || !(owner.createthrusters$getConnectorOwner()
                instanceof DockingConnectorBlockEntity stationConnector)) {
            return;
        }
        if (CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE.get()) {
            return;
        }
        DockingConnectorBlockEntity shipConnector = stationConnector.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesFluids(
                stationConnector, shipConnector)) {
            return;
        }
        CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE.set(true);
        try {
            FluidStack advertised = WirelessDockingTransfer.peekFluid(
                    stationConnector, shipConnector);
            if (advertised.isEmpty()) {
                return;
            }
            callback.setReturnValue(WirelessDockingTransfer.extractFluid(
                    stationConnector, shipConnector, advertised, maxDrain,
                    action.simulate()));
        } finally {
            CREATETHRUSTERS$WIRELESS_ROUTE_ACTIVE.remove();
        }
    }
}
