package com.rieno.gadgetsandgizmos.compat.jade;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
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
    }

    // Register the client
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ThrusterJadeProvider.INSTANCE, ThrusterBlock.class);
    }
}
