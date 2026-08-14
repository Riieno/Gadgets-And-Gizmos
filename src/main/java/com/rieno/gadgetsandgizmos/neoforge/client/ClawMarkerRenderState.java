package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Store Claw Marker Render state
public final class ClawMarkerRenderState {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long MAX_AGE_NANOS = 250_000_000L;
    private static final Map<Long, MarkerEntry> MARKERS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw marker render state
    private ClawMarkerRenderState() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Publish the claw marker render state
    public static void publish(BlockPos sourcePos, Vec3 worldCenter, float radius, float signalFactor) {
        if (sourcePos == null || worldCenter == null || radius <= 0.0f) {
            return;
        }
        MARKERS.put(sourcePos.asLong(), new MarkerEntry(worldCenter, radius, signalFactor, System.nanoTime()));
    }

    // Get the active markers
    public static Collection<MarkerEntry> getActiveMarkers() {
        long now = System.nanoTime();
        MARKERS.entrySet().removeIf(entry -> (now - entry.getValue().timestampNanos) > MAX_AGE_NANOS);
        return new ArrayList<>(MARKERS.values());
    }

    // Clear every claw marker
    public static void clearAll() {
        MARKERS.clear();
    }

    // Handle the marker entry
    public static final class MarkerEntry {
        // World center
        public final Vec3 worldCenter;
        // Radius
        public final float radius;
        // Signal factor
        public final float signalFactor;
        // Timestamp nanos
        private final long timestampNanos;

        // Initialize the marker entry
        private MarkerEntry(Vec3 worldCenter, float radius, float signalFactor, long timestampNanos) {
            this.worldCenter = worldCenter;
            this.radius = radius;
            this.signalFactor = signalFactor;
            this.timestampNanos = timestampNanos;
        }
    }
}
