package com.rieno.gadgetsandgizmos.compat.jade;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.SmartBatteryBlock;
import com.rieno.gadgetsandgizmos.content.SmartBatteryBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

// Register the addon Jade plugin
@WailaPlugin
public final class CreateThrustersJadePlugin implements IWailaPlugin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the Create Thrusters jade plugin
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ThrusterJadeProvider.INSTANCE, ThrusterBlockEntity.class);
        registration.registerBlockDataProvider(SmartBatteryJadeProvider.INSTANCE, SmartBatteryBlockEntity.class);
    }

    // Register the client
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ThrusterJadeProvider.INSTANCE, ThrusterBlock.class);
        registration.registerBlockComponent(SmartBatteryJadeProvider.INSTANCE, SmartBatteryBlock.class);
    }
}
