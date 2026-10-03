package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
            SnapshotKey key = new SnapshotKey(appId, placedSource, sourceTabletId, sourceSubLevelId, sourceBlockPos);
            if(data != null && data.contains("Error") && com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.ownsId(appId)){
                CompoundTag res = SNAPSHOTS.getOrDefault(key, new CompoundTag()).copy();
                res.putString("Error", data.getString("Error"));
                if(appId.equals(com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.BLOCKMATES.id())){
                    ListTag missing = new ListTag();
                    missing.add(StringTag.valueOf(data.getString("Error")));
                    res.putUUID("RequestResponseId", UUID.randomUUID());
                    res.putBoolean("RequestFailed", true);
                    res.put("Missing", missing);
                }
                SNAPSHOTS.put(key, res);
                return;
            }
            CompoundTag next = data == null ? new CompoundTag() : data.copy();
            if(appId.equals(com.rieno.gadgetsandgizmos.content.tablet.PaidTabletApps.BLOCKMATES.id())
                    && !next.hasUUID("RequestResponseId")){
                CompoundTag previous = SNAPSHOTS.get(key);
                if(previous != null && previous.hasUUID("RequestResponseId")){
                    next.putUUID("RequestResponseId", previous.getUUID("RequestResponseId"));
                    next.putBoolean("RequestFailed", previous.getBoolean("RequestFailed"));
                    next.put("Missing", previous.getList("Missing", net.minecraft.nbt.Tag.TAG_STRING).copy());
                }
            }
            SNAPSHOTS.put(key, next);
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
