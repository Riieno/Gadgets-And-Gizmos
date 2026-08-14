package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

// Refresh physics for link-only sub-levels
final class LinkOnlySubLevelPhysics {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the link only sub level physics
    private LinkOnlySubLevelPhysics() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh the link only sublevel physics
    static boolean refresh(ServerSubLevel child, ServerSubLevelContainer container) {
        child.getPlot().updateBoundingBox();
        child.buildMassTracker();
        child.updateMergedMassData(1.0F);
        if (child.getMassTracker().getCenterOfMass() != null) {
            container.physicsSystem().getPipeline().onStatsChanged(child);
            return true;
        }
        container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
        return false;
    }
}
