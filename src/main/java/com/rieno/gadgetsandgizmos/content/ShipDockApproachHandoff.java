package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Hand surface vehicles from route travel to docking through their selected connector
final class ShipDockApproachHandoff{
    private ShipDockApproachHandoff(){}

    // Detect connectorless arrival along the dock's placement UP direction
    private static DockingApproachAxis connectorlessAxis(ShipDockRegistry.Dock dock){
        return new DockingApproachAxis(dock.worldPosition(), dock.up(), dock.approachPath().distance());
    }

    // Keep a tall vehicle at its current height while centering it above the dock
    static Vec3 connectorlessTarget(ShipDockRegistry.Dock dock, Vec3 position){
        return connectorlessAxis(dock).project(position);
    }

    // Require the control point above the dock while allowing the body to overlap its vertical range
    static boolean connectorlessReached(ShipDockRegistry.Dock dock, Vec3 position, SableAssemblyBoundsApi.Envelope envelope){
        if(position.distanceToSqr(connectorlessTarget(dock, position)) > 0.25D) return false;
        double radius = envelope.horizontalRadius();
        return connectorlessAxis(dock).overlaps(new AABB(position.x - radius, position.y - envelope.bottomOffset(),
                position.z - radius, position.x + radius, position.y + envelope.height() - envelope.bottomOffset(), position.z + radius));
    }

    // Retain the existing route handoff for airborne vehicles and connectorless stops
    static boolean applies(ScmControlMode mode, ShipDockRegistry.Dock dock){
        return mode.usesSurfaceNavigation() && dock != null && dock.hasDockingConnector();
    }

    // Use the selected station connector's saved detection range
    private static DockingApproachAxis axis(ShipDockRegistry.Dock dock){
        return new DockingApproachAxis(dock.dockingTarget(), dock.connectorFacing(), dock.approachPath().distance());
    }

    // Guide the assigned connector toward the nearest point rather than a fixed offset
    static Vec3 target(ShipDockRegistry.Dock dock, ShipControlModuleRuntime.MappedDockingConnector connector){
        return axis(dock).closestPoint(connector.worldTipPosition());
    }

    // Test only the assigned connector's contact volume against the complete detection segment
    static boolean reached(ShipDockRegistry.Dock dock, ShipControlModuleRuntime.MappedDockingConnector connector){
        return connector != null && axis(dock).overlaps(AABB.ofSize(connector.worldTipPosition(), 1, 1, 1));
    }
}
