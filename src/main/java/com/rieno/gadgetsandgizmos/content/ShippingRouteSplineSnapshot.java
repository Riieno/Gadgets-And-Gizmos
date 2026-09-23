package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

// Store one detached server-authoritative SCM schedule route snapshot
public record ShippingRouteSplineSnapshot(UUID ownerId, long revision, List<Route> routes) {
    // Initialize the route snapshot
    public ShippingRouteSplineSnapshot {
        routes = routes == null ? List.of() : routes.stream()
                .filter(route -> route != null).toList();
    }

    // Store one prepared schedule leg and its authored waypoint controls
    public record Route(UUID id, int scheduleEntry, List<Vec3> waypoints) {
        // Initialize the prepared route leg
        public Route {
            waypoints = waypoints == null ? List.of() : waypoints.stream()
                    .filter(point -> point != null && Double.isFinite(point.x)
                            && Double.isFinite(point.y) && Double.isFinite(point.z))
                    .toList();
        }
    }

    // Define server-authoritative waypoint edits
    public enum EditAction {
        ADD,
        MOVE,
        REMOVE
    }
}
