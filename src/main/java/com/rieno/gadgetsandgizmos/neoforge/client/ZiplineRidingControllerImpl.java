package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ZiplineRidingController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

// Handle powered zipline riding on the client
public class ZiplineRidingControllerImpl implements ZiplineRidingController {
 /*--------------------------------------------------------##---------------------------------------------------------

 =======================================================================================================================
                                                        Functions
 =======================================================================================================================

 ------------------------------------------------------------##-----------------------------------------------------*/

 // Try to mount
	@Override
	public void tryMount(Player player, BlockPos pos) {
		PoweredZiplineRidingHandler.tryMount(player, pos);
	}

 // Check if this should cull first person connector
	@Override
	public boolean shouldCullFirstPersonConnector() {
		return PoweredZiplineRidingHandler.shouldCullFirstPersonConnector();
	}
}
