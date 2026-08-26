package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Expose RCS Thruster controls and telemetry to ComputerCraft
@PeripheralTypeDoc("rcs_thruster")
public class RcsThrusterPeripheral extends GadgetsPeripheral<RcsThrusterBlockEntity> {
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

    // Initialize the RCS thruster peripheral
    public RcsThrusterPeripheral(RcsThrusterBlockEntity blockEntity) {
        super(blockEntity, "rcs_thruster");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setThrottle", signature = "setThrottle(nozzle: 'north'|'east'|'south'|'west', throttle: number 0..1)",
            description = "Sets the throttle.")
    public final void setThrottle(String nozzle, double throttle) throws LuaException {
        Direction dir = parseNozzle(nozzle);
        if (!Double.isFinite(throttle) || throttle < 0.0D || throttle > 1.0D) {
            throw new LuaException("throttle must be between 0.0 and 1.0");
        }
        blockEntity.setComputerThrottle(dir, (float) throttle);
    }

    // Get the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottle", signature = "getThrottle(nozzle): number",
            description = "Returns the throttle.")
    public final double getThrottle(String nozzle) throws LuaException {
        return blockEntity.getThrottle(parseNozzle(nozzle));
    }

    // Clear the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearThrottle", signature = "clearThrottle(nozzle)",
            description = "Clears the throttle.")
    public final void clearThrottle(String nozzle) throws LuaException {
        blockEntity.clearComputerThrottle(parseNozzle(nozzle));
    }

    // Clear every thruster throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAllThrottles", signature = "clearAllThrottles()",
            description = "Clears every thruster throttle.")
    public final void clearAllThrottles() {
        for (Direction nozzle : Direction.Plane.HORIZONTAL) {
            blockEntity.clearComputerThrottle(nozzle);
        }
    }

    // Get the thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrust", signature = "getThrust(nozzle): number",
            description = "Returns the thrust.")
    public final double getThrust(String nozzle) throws LuaException {
        return blockEntity.getNozzleThrust(parseNozzle(nozzle));
    }

    // Get the max thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxThrust", signature = "getMaxThrust(): number",
            description = "Returns the max thrust.")
    public final double getMaxThrust() {
        return blockEntity.getMaxNozzleThrust();
    }

    // Get the RPM
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRPM", signature = "getRPM(): number",
            description = "Returns the RPM.")
    public final double getRPM() {
        return blockEntity.getSpeed();
    }

    // Check if this is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(nozzle): boolean",
            description = "Returns whether this is active.")
    public final boolean isActive(String nozzle) throws LuaException {
        return blockEntity.isNozzleActive(parseNozzle(nozzle));
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("rpm", blockEntity.getSpeed());
        status.put("maxThrust", blockEntity.getMaxNozzleThrust());
        for (Direction nozzle : List.of(
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            String name = nozzle.getSerializedName();
            Map<String, Object> nozzleStatus = new LinkedHashMap<>();
            nozzleStatus.put("throttle", blockEntity.getThrottle(nozzle));
            nozzleStatus.put("redstoneThrottle", blockEntity.getRedstoneThrottle(nozzle));
            nozzleStatus.put("computerOverride", blockEntity.hasComputerThrottle(nozzle));
            nozzleStatus.put("thrust", blockEntity.getNozzleThrust(nozzle));
            nozzleStatus.put("active", blockEntity.isNozzleActive(nozzle));
            status.put(name, nozzleStatus);
        }
        return status;
    }

    // Parse the nozzle
    private static Direction parseNozzle(String val) throws LuaException {
        String normalized = val == null ? "" : val.trim().toLowerCase(Locale.ROOT);
        Direction dir = RcsThrusterBlockEntity.nozzleFromChannel(normalized);
        if (dir == null || !normalized.equals(dir.getSerializedName())) {
            throw new LuaException("nozzle must be 'north', 'east', 'south' or 'west'");
        }
        return dir;
    }
}
