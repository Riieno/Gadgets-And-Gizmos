package com.rieno.gadgetsandgizmos.compat.offroad;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyCache;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.Set;
import java.util.UUID;

// Keep wheel suspension terrain casts out of the assembly which carries the wheel
public interface WheelMountTerrainRaycastCompatibility {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the cache used by this wheel mount
    SableAssemblyTopologyCache ct$getWheelMountTerrainTopology();

    // Configure a wheel suspension terrain cast
    default void ct$configureWheelMountTerrainCast(
            ClipContextExtension context, SubLevel wheelSubLevel
    ) {
        context.sable$setIgnoredSubLevel(wheelSubLevel);
        if (!(wheelSubLevel instanceof ServerSubLevel serverSubLevel)) {
            return;
        }

        Set<UUID> connectedBodyIds = ct$getWheelMountTerrainTopology()
                .get(serverSubLevel).loadedBodyIds();
        context.sable$setSubLevelIgnoring(candidate -> candidate != null
                && connectedBodyIds.contains(candidate.getUniqueId()));
    }
}
