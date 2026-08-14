package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VirtualOrientationSourceBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expose Virtual Orientation Source controls and telemetry to ComputerCraft
public class VirtualOrientationSourcePeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final VirtualOrientationSourceBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the virtual orientation source peripheral
    public VirtualOrientationSourcePeripheral(VirtualOrientationSourceBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return "virtual_orientation_source";
    }

    // Compare this virtual orientation source peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof VirtualOrientationSourcePeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Set the angles
    @LuaFunction
    public final void setAngles(double xRadians, double zRadians) {
        blockEntity.setAnglesRadians(xRadians, zRadians);
    }

    // Set the angles degrees
    @LuaFunction
    public final void setAnglesDegrees(double xDegrees, double zDegrees) {
        blockEntity.setAnglesDegrees(xDegrees, zDegrees);
    }

    // Set the direction
    @LuaFunction
    public final void setDirection(double x, double y, double z) {
        blockEntity.setDirection(new Vec3(x, y, z));
    }

    // Clear the virtual orientation source peripheral
    @LuaFunction
    public final void clear() {
        blockEntity.clearVirtualOrientation();
    }

    // Check if this is active
    @LuaFunction
    public final boolean isActive() {
        return blockEntity.isOrientationSourceActive();
    }

    // Get the state
    @LuaFunction
    public final Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("active", blockEntity.isOrientationSourceActive());
        double[] angles = blockEntity.getLinkedAnglesRadians();
        if (angles != null) {
            state.put("xRadians", angles[0]);
            state.put("zRadians", angles[1]);
            state.put("xDegrees", Math.toDegrees(angles[0]));
            state.put("zDegrees", Math.toDegrees(angles[1]));
        }
        Vec3 dir = blockEntity.getLinkedDirection();
        if (dir != null) {
            state.put("dirX", dir.x);
            state.put("dirY", dir.y);
            state.put("dirZ", dir.z);
        }
        state.put("lastUpdateTick", blockEntity.getLastUpdateTick());
        return state;
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "setAngles(xRadians:number,zRadians:number)",
                "setAnglesDegrees(xDegrees:number,zDegrees:number)",
                "setDirection(x:number,y:number,z:number)",
                "clear()",
                "isActive(): boolean",
                "getState(): table"
        );
    }
}
