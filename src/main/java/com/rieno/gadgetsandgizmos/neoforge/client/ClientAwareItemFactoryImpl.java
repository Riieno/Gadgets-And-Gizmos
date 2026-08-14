package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.registry.ClientAwareItemFactory;
import net.minecraft.world.item.Item;

// Create the client item variants
public class ClientAwareItemFactoryImpl implements ClientAwareItemFactory {
 /*--------------------------------------------------------##---------------------------------------------------------

 =======================================================================================================================
                                                        Functions
 =======================================================================================================================

 ------------------------------------------------------------##-----------------------------------------------------*/

 // Create the physics staff item
	@Override
	public PhysicsStaffItem createPhysicsStaffItem(Item.Properties properties) {
		return new ClientPhysicsStaffItem(properties);
	}

 // Create the entity launcher item
	@Override
	public EntityLauncherItem createEntityLauncherItem(Item.Properties properties) {
		return new ClientEntityLauncherItem(properties);
	}

 // Create the player mannequin item
	@Override
	public PlayerMannequinItem createPlayerMannequinItem(Item.Properties properties) {
		return new ClientPlayerMannequinItem(properties);
	}

 // Create the contraption network linker item
	@Override
	public ContraptionNetworkLinkerItem createContraptionNetworkLinkerItem(Item.Properties properties) {
		return new ClientContraptionNetworkLinkerItem(properties);
	}
}
