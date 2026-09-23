package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

// Store one cached controller write state
record ControllerSqliteControllerWriteState(
        String kind,
        String dimension,
        BlockPos position,
        String subLevelId,
        String insertedLinkerId,
        CompoundTag controllerData,
        CompoundTag draftGraph,
        CompoundTag activeGraph,
        CompoundTag graphHistory,
        int revision,
        String hash
) {
    // Check if this matches the value
    boolean matches(
            String requestedKind,
            String requestedDimension,
            BlockPos requestedPosition,
            String requestedSubLevelId,
            String requestedInsertedLinkerId,
            CompoundTag requestedControllerData,
            CompoundTag requestedDraftGraph,
            CompoundTag requestedActiveGraph,
            CompoundTag requestedGraphHistory
    ) {
        return kind.equals(requestedKind)
                && dimension.equals(requestedDimension)
                && position.equals(requestedPosition)
                && subLevelId.equals(requestedSubLevelId)
                && insertedLinkerId.equals(requestedInsertedLinkerId)
                && controllerData.equals(requestedControllerData)
                && draftGraph.equals(requestedDraftGraph)
                && activeGraph.equals(requestedActiveGraph)
                && graphHistory.equals(requestedGraphHistory);
    }

    // Get the snapshot
    ControllerManifestStore.ManifestSnapshot snapshot(String id) {
        return new ControllerManifestStore.ManifestSnapshot(
                id, revision, hash, ControllerManifestStore.STORAGE_VERSION, kind,
                controllerData.copy(), draftGraph.copy(), activeGraph.copy(), new CompoundTag());
    }
}
