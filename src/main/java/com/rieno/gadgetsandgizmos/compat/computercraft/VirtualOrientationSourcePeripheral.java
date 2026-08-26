package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.VirtualOrientationSourceBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expose Virtual Orientation Source controls and telemetry to ComputerCraft
@PeripheralTypeDoc("virtual_orientation_source")
public class VirtualOrientationSourcePeripheral extends GadgetsPeripheral<VirtualOrientationSourceBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the virtual orientation source peripheral
    public VirtualOrientationSourcePeripheral(VirtualOrientationSourceBlockEntity blockEntity) {
        super(blockEntity, "virtual_orientation_source");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAngles", signature = "setAngles(xRadians:number,zRadians:number)",
            description = "Sets the angles.")
    public final void setAngles(double xRadians, double zRadians) {
        blockEntity.setAnglesRadians(xRadians, zRadians);
    }

    // Set the angles degrees
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAnglesDegrees", signature = "setAnglesDegrees(xDegrees:number,zDegrees:number)",
            description = "Sets the angles degrees.")
    public final void setAnglesDegrees(double xDegrees, double zDegrees) {
        blockEntity.setAnglesDegrees(xDegrees, zDegrees);
    }

    // Set the direction
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setDirection", signature = "setDirection(x:number,y:number,z:number)",
            description = "Sets the direction.")
    public final void setDirection(double x, double y, double z) {
        blockEntity.setDirection(new Vec3(x, y, z));
    }

    // Clear the virtual orientation source peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clear", signature = "clear()",
            description = "Clears the virtual orientation source peripheral.")
    public final void clear() {
        blockEntity.clearVirtualOrientation();
    }

    // Check if this is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(): boolean",
            description = "Returns whether this is active.")
    public final boolean isActive() {
        return blockEntity.isOrientationSourceActive();
    }

    // Get the state
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getState", signature = "getState(): table",
            description = "Returns the state.")
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
}
