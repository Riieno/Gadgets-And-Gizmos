package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.content.PhysicsStaffItem;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinItem;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

// Create matching client and server items
public interface ClientAwareItemFactory {

 /*--------------------------------------------------------##---------------------------------------------------------

 =======================================================================================================================
                                                        Functions
 =======================================================================================================================

 ------------------------------------------------------------##-----------------------------------------------------*/

 // Create the physics staff item
	PhysicsStaffItem createPhysicsStaffItem(Item.Properties properties);

 // Create the entity launcher item
	EntityLauncherItem createEntityLauncherItem(Item.Properties properties);

 // Create the player mannequin item
	PlayerMannequinItem createPlayerMannequinItem(Item.Properties properties);

 // Create the contraption network linker item
	ContraptionNetworkLinkerItem createContraptionNetworkLinkerItem(Item.Properties properties);

 // Get the instance
	@Nullable
	static ClientAwareItemFactory getInstance() {
		if (!FMLEnvironment.dist.isClient()) {
			return null;
		}
		try {
			Class<?> clientFactoryClass = Class.forName(
				"com.rieno.gadgetsandgizmos.neoforge.client.ClientAwareItemFactoryImpl");
			Object instance = clientFactoryClass.getDeclaredConstructor().newInstance();
			if (instance instanceof ClientAwareItemFactory factory) {
				return factory;
			}
		} catch (Exception ignored) {

		}
		return null;
	}
}
