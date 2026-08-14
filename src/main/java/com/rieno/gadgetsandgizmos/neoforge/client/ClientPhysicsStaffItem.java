package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

// Draw the client Physics Staff item
public class ClientPhysicsStaffItem extends PhysicsStaffItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the client physics staff item
    public ClientPhysicsStaffItem(Properties properties) {
        super(properties);
    }

    // Initialize the client
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new PhysicsStaffItemRenderer()));
    }
}
