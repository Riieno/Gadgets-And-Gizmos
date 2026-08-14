package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ShippingDockingConnectorBatteryOwner;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBattery;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Give a supported docking battery a reference back to the block entity which owns it
@Mixin(value = DockingConnectorBlockEntity.class, remap = false)
public abstract class ShippingDockingConnectorBatteryOwnerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current battery
    @Shadow public DockingConnectorBattery battery;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Attach the battery owner
    @Inject(method = "<init>", at = @At("RETURN"))
    private void createthrusters$attachBatteryOwner(CallbackInfo callback) {
        if (battery instanceof ShippingDockingConnectorBatteryOwner owner) {
            owner.createthrusters$setConnectorOwner((DockingConnectorBlockEntity) (Object) this);
        }
    }
}
