package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.navigation.DockingApproachAxis;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Hand surface vehicles from route travel to docking through their selected connector
final class ShipDockApproachHandoff{
    private ShipDockApproachHandoff(){}

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
