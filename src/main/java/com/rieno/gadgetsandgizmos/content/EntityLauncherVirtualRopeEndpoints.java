package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Track virtual rope endpoints before a launcher rope has physical blocks
public final class EntityLauncherVirtualRopeEndpoints {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Key, LauncherEndpointBlockEntity> ENDPOINTS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher virtual rope endpoints
    private EntityLauncherVirtualRopeEndpoints() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get or create the entity launcher virtual rope endpoints
    public static LauncherEndpointBlockEntity getOrCreate(ServerLevel level, @Nullable UUID subLevelId,
                                                          BlockPos pos, Vec3 attachmentPoint) {
        Key key = new Key(level.dimension(), subLevelId, pos.immutable());
        LauncherEndpointBlockEntity endpoint = ENDPOINTS.compute(key, (ignored, existing) -> {
            if (existing != null && !existing.isRemoved()) {
                return existing;
            }
            LauncherEndpointBlockEntity created = new LauncherEndpointBlockEntity(pos.immutable(),
                    CTBlocks.LAUNCHER_ENDPOINT.get().defaultBlockState());
            created.markVirtual();
            created.setLevel(level);
            return created;
        });
        endpoint.setAttachmentPoint(attachmentPoint);
        return endpoint;
    }

    // Find the entity launcher virtual rope endpoints
    @Nullable
    public static BlockEntity find(ServerLevel level, @Nullable UUID subLevelId, BlockPos pos) {
        LauncherEndpointBlockEntity endpoint = ENDPOINTS.get(new Key(level.dimension(), subLevelId, pos));
        return endpoint == null || endpoint.isRemoved() ? null : endpoint;
    }

    // Remove the entity launcher virtual rope endpoints
    public static void remove(ServerLevel level, @Nullable UUID subLevelId, BlockPos pos) {
        LauncherEndpointBlockEntity endpoint = ENDPOINTS.remove(new Key(level.dimension(), subLevelId, pos));
        if (endpoint == null) {
            return;
        }
        endpoint.detachVirtualRope();
        endpoint.setRemoved();
    }

    // Store the key
    private record Key(ResourceKey<Level> dimension, @Nullable UUID subLevelId, BlockPos pos) {
        // Initialize the key
        private Key {
            pos = pos.immutable();
        }
    }
}
