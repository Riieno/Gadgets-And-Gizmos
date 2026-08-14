package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Expose RCS Thruster controls and telemetry to ComputerCraft
public class RcsThrusterPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final RcsThrusterBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS thruster peripheral
    public RcsThrusterPeripheral(RcsThrusterBlockEntity blockEntity) {
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
        return "rcs_thruster";
    }

    // Compare this RCS thruster peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof RcsThrusterPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Set the throttle
    @LuaFunction
    public final void setThrottle(String nozzle, double throttle) throws LuaException {
        Direction dir = parseNozzle(nozzle);
        if (!Double.isFinite(throttle) || throttle < 0.0D || throttle > 1.0D) {
            throw new LuaException("throttle must be between 0.0 and 1.0");
        }
        blockEntity.setComputerThrottle(dir, (float) throttle);
    }

    // Get the throttle
    @LuaFunction
    public final double getThrottle(String nozzle) throws LuaException {
        return blockEntity.getThrottle(parseNozzle(nozzle));
    }

    // Clear the throttle
    @LuaFunction
    public final void clearThrottle(String nozzle) throws LuaException {
        blockEntity.clearComputerThrottle(parseNozzle(nozzle));
    }

    // Clear every thruster throttle
    @LuaFunction
    public final void clearAllThrottles() {
        for (Direction nozzle : Direction.Plane.HORIZONTAL) {
            blockEntity.clearComputerThrottle(nozzle);
        }
    }

    // Get the thrust
    @LuaFunction
    public final double getThrust(String nozzle) throws LuaException {
        return blockEntity.getNozzleThrust(parseNozzle(nozzle));
    }

    // Get the max thrust
    @LuaFunction
    public final double getMaxThrust() {
        return blockEntity.getMaxNozzleThrust();
    }

    // Get the RPM
    @LuaFunction
    public final double getRPM() {
        return blockEntity.getSpeed();
    }

    // Check if this is active
    @LuaFunction
    public final boolean isActive(String nozzle) throws LuaException {
        return blockEntity.isNozzleActive(parseNozzle(nozzle));
    }

    // Get the status
    @LuaFunction
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

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "setThrottle(nozzle: 'north'|'east'|'south'|'west', throttle: number 0..1)",
                "getThrottle(nozzle): number",
                "clearThrottle(nozzle)",
                "clearAllThrottles()",
                "getThrust(nozzle): number",
                "getMaxThrust(): number",
                "getRPM(): number",
                "isActive(nozzle): boolean",
                "getStatus(): table");
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
