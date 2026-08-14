package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

// Handle powered zipline riding
public interface ZiplineRidingController {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Try to mount
    void tryMount(Player player, BlockPos pos);

    // Check if this should cull first person connector
    boolean shouldCullFirstPersonConnector();

    // Get the instance
    @Nullable
    static ZiplineRidingController getInstance() {
        return Holder.instance;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Install the zipline riding
    static void install(ZiplineRidingController controller) {
        Holder.instance = controller;
    }

    // Hold the zipline riding controller
    final class Holder {
        // Shared instance
        private static volatile ZiplineRidingController instance;

        // Initialize the holder
        private Holder() {
        }
    }
}
