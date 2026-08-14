package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

// Draw the client Network Linker item
public class ClientContraptionNetworkLinkerItem extends ContraptionNetworkLinkerItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the client contraption network linker item
    public ClientContraptionNetworkLinkerItem(Properties properties) {
        super(properties);
    }

    // Initialize the client
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new ContraptionNetworkLinkerItemRenderer()));
    }
}
