package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorAccess;
import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorBatteryOwner;
import com.rieno.gadgetsandgizmos.content.WirelessDockingTransfer;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBattery;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.multiloader.energy.SingleBattery;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Add shipping Docking Connector Battery access to Shipping's docking connector
@Mixin(value = DockingConnectorBattery.class, remap = false)
public abstract class ShippingDockingConnectorBatteryMixin implements ShippingDockingConnectorBatteryOwner {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current connector owner
    @Unique private BlockEntity createthrusters$connectorOwner;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the super receive energy
    @Shadow protected abstract int superReceiveEnergy(int amount, boolean simulate);

    // Set the connector owner
    @Override
    public void createthrusters$setConnectorOwner(BlockEntity owner) {
        createthrusters$connectorOwner = owner;
    }

    // Get the connector owner
    @Override
    public BlockEntity createthrusters$getConnectorOwner() {
        return createthrusters$connectorOwner;
    }

    // Receive the direct
    @Override
    public int createthrusters$receiveDirect(int amount, boolean simulate) {
        return superReceiveEnergy(amount, simulate);
    }

    // Filter the energy insert
    @Inject(method = "receiveEnergy", at = @At("HEAD"), cancellable = true)
    private void createthrusters$filterEnergyInsert(
            int toReceive,
            boolean simulate,
            CallbackInfoReturnable<Integer> callback
    ) {
        if (createthrusters$connectorOwner instanceof ShippingDockingConnectorAccess access
                && !access.createthrusters$allowsEnergyTransfer()) {
            callback.setReturnValue(0);
            return;
        }
        if (!(createthrusters$connectorOwner instanceof DockingConnectorBlockEntity stationConnector)) {
            return;
        }
        DockingConnectorBlockEntity shipConnector = stationConnector.getOtherConnector();
        if (shipConnector == null || !WirelessDockingTransfer.routesEnergy(
                stationConnector, shipConnector)) {
            return;
        }
        int requested = Math.min(Math.max(0, toReceive),
                ((SingleBattery) (Object) this).getThroughput());
        int networkAccepted = WirelessDockingTransfer.insertEnergy(
                stationConnector, shipConnector, requested, true);
        int remaining = requested - networkAccepted;
        int connectorAccepted = shipConnector.battery
                instanceof ShippingDockingConnectorBatteryOwner batteryAccess
                ? batteryAccess.createthrusters$receiveDirect(remaining, true) : 0;
        if (simulate) {
            callback.setReturnValue(networkAccepted + connectorAccepted);
            return;
        }
        int actualNetwork = WirelessDockingTransfer.insertEnergy(
                stationConnector, shipConnector, requested, false);
        int actualRemaining = requested - actualNetwork;
        int actualConnector = shipConnector.battery
                instanceof ShippingDockingConnectorBatteryOwner batteryAccess
                ? batteryAccess.createthrusters$receiveDirect(actualRemaining, false) : 0;
        callback.setReturnValue(actualNetwork + actualConnector);
    }
}
