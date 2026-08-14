package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Store and serialize Diagnostic Tablet Client App data
public final class DiagnosticTabletClientAppData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<SnapshotKey, CompoundTag> SNAPSHOTS = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet client app data
    private DiagnosticTabletClientAppData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the diagnostic tablet client app data
    public static synchronized void apply(ResourceLocation appId, CompoundTag data) {
        apply(appId, false, null, null, null, data);
    }

    // Apply the diagnostic tablet client app data
    public static synchronized void apply(ResourceLocation appId, boolean placedSource,
                                          @Nullable UUID sourceTabletId,
                                          @Nullable UUID sourceSubLevelId,
                                          @Nullable BlockPos sourceBlockPos,
                                          CompoundTag data) {
        if (appId != null) {
            SNAPSHOTS.put(new SnapshotKey(appId, placedSource, sourceTabletId,
                    sourceSubLevelId, sourceBlockPos),
                    data == null ? new CompoundTag() : data.copy());
        }
    }

    // Get the diagnostic tablet client app data value
    public static synchronized CompoundTag get(ResourceLocation appId) {
        return get(appId, false, null, null, null);
    }

    // Get the diagnostic tablet client app data value
    public static synchronized CompoundTag get(ResourceLocation appId, boolean placedSource,
                                                @Nullable UUID sourceTabletId,
                                                @Nullable UUID sourceSubLevelId,
                                                @Nullable BlockPos sourceBlockPos) {
        CompoundTag data = SNAPSHOTS.get(new SnapshotKey(appId, placedSource, sourceTabletId,
                sourceSubLevelId, sourceBlockPos));
        if (data == null && (placedSource || sourceTabletId != null
                || sourceSubLevelId != null || sourceBlockPos != null)) {
            data = SNAPSHOTS.get(new SnapshotKey(appId, false, null, null, null));
        }
        return data == null ? new CompoundTag() : data.copy();
    }

    // Clear the diagnostic tablet client app data
    public static synchronized void clear() {
        SNAPSHOTS.clear();
    }

    // Store the snapshot key
    private record SnapshotKey(ResourceLocation appId, boolean placedSource,
                               @Nullable UUID sourceTabletId,
                               @Nullable UUID sourceSubLevelId,
                               @Nullable BlockPos sourceBlockPos) {
        // Initialize the snapshot key
        private SnapshotKey {
            sourceBlockPos = sourceBlockPos == null ? null : sourceBlockPos.immutable();
        }
    }
}
