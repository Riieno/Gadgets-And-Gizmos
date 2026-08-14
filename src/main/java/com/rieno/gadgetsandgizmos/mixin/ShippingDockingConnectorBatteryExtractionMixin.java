package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorBatteryOwner;
import com.rieno.gadgetsandgizmos.content.WirelessDockingTransfer;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBattery;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.multiloader.energy.SingleBattery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add shipping Docking Connector Battery Extraction access to Shipping's docking connector
@Mixin(value = SingleBattery.class, remap = false)
public abstract class ShippingDockingConnectorBatteryExtractionMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current energy
    @Shadow protected int energy;
    // Current throughput
    @Shadow protected int throughput;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Route the energy extraction
    @Inject(method = "extractEnergy", at = @At("HEAD"), cancellable = true)
    private void createthrusters$routeEnergyExtraction(
            int toExtract,
            boolean simulate,
            CallbackInfoReturnable<Integer> callback
    ) {
        if (!((Object) this instanceof DockingConnectorBattery battery)
                || !(battery instanceof ShippingDockingConnectorBatteryOwner owner)
                || !(owner.createthrusters$getConnectorOwner()
                instanceof DockingConnectorBlockEntity stationConnector)) {
            return;
        }
        DockingConnectorBlockEntity shipConnector = stationConnector.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesEnergy(
                stationConnector, shipConnector)) {
            return;
        }
        int requested = Math.min(Math.max(0, toExtract), throughput);
        int direct = Math.min(requested, Math.min(Math.max(0, energy), throughput));
        int network = WirelessDockingTransfer.extractEnergy(
                stationConnector, shipConnector, requested - direct, simulate);
        if (!simulate && direct > 0) {
            energy -= direct;
        }
        callback.setReturnValue(direct + network);
    }

    // Handle the advertise wireless energy
    @Inject(method = "getEnergy", at = @At("HEAD"), cancellable = true)
    private void createthrusters$advertiseWirelessEnergy(
            CallbackInfoReturnable<Integer> callback
    ) {
        if (!((Object) this instanceof DockingConnectorBattery battery)
                || !(battery instanceof ShippingDockingConnectorBatteryOwner owner)
                || !(owner.createthrusters$getConnectorOwner()
                instanceof DockingConnectorBlockEntity stationConnector)) {
            return;
        }
        DockingConnectorBlockEntity shipConnector = stationConnector.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesEnergy(
                stationConnector, shipConnector)) {
            return;
        }
        callback.setReturnValue((int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, (long) energy + WirelessDockingTransfer.storedEnergy(
                        stationConnector, shipConnector))));
    }
}
