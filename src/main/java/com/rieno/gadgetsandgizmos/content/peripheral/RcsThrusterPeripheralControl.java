package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptBlockPeripheral;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Expose RCS Thruster controls and telemetry to ComputerCraft
@PeripheralTypeDoc("rcs_thruster")
public class RcsThrusterPeripheralControl extends ScriptBlockPeripheral<RcsThrusterBlockEntity> {
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
    public RcsThrusterPeripheralControl(RcsThrusterBlockEntity blockEntity) {
        super(blockEntity, "rcs_thruster");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the throttle
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "setThrottle", signature = "setThrottle(nozzle: 'north'|'east'|'south'|'west', throttle: number 0..1)",
            description = "Sets the throttle.")
    public final void setThrottle(String nozzle, double throttle) throws ScriptException {
        Direction dir = parseNozzle(nozzle);
        if (!Double.isFinite(throttle) || throttle < 0.0D || throttle > 1.0D) {
            throw new ScriptException("throttle must be between 0.0 and 1.0");
        }
        blockEntity.setComputerThrottleFast(dir, (float) throttle);
    }

    // Get the throttle
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "getThrottle", signature = "getThrottle(nozzle): number",
            description = "Returns the throttle.")
    public final double getThrottle(String nozzle) throws ScriptException {
        return blockEntity.getThrottle(parseNozzle(nozzle));
    }

    // Clear the throttle
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "clearThrottle", signature = "clearThrottle(nozzle)",
            description = "Clears the throttle.")
    public final void clearThrottle(String nozzle) throws ScriptException {
        blockEntity.clearComputerThrottleFast(parseNozzle(nozzle));
    }

    // Clear every thruster throttle
    @ScriptFunctionBinding
    @ScriptMethodDoc(name = "clearAllThrottles", signature = "clearAllThrottles()",
            description = "Clears every thruster throttle.")
    public final void clearAllThrottles() {
        for (Direction nozzle : Direction.Plane.HORIZONTAL) {
            blockEntity.clearComputerThrottleFast(nozzle);
        }
    }

    // Get the thrust
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getThrust", signature = "getThrust(nozzle): number",
            description = "Returns the thrust.")
    public final double getThrust(String nozzle) throws ScriptException {
        return blockEntity.getNozzleThrust(parseNozzle(nozzle));
    }

    // Get the max thrust
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getMaxThrust", signature = "getMaxThrust(): number",
            description = "Returns the max thrust.")
    public final double getMaxThrust() {
        return blockEntity.getMaxNozzleThrust();
    }

    // Get the RPM
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRPM", signature = "getRPM(): number",
            description = "Returns the RPM.")
    public final double getRPM() {
        return blockEntity.getSpeed();
    }

    // Check if this is active
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isActive", signature = "isActive(nozzle): boolean",
            description = "Returns whether this is active.")
    public final boolean isActive(String nozzle) throws ScriptException {
        return blockEntity.isNozzleActive(parseNozzle(nozzle));
    }

    // Get the status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
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
    private static Direction parseNozzle(String val) throws ScriptException {
        String normalized = val == null ? "" : val.trim().toLowerCase(Locale.ROOT);
        Direction dir = RcsThrusterBlockEntity.nozzleFromChannel(normalized);
        if (dir == null || !normalized.equals(dir.getSerializedName())) {
            throw new ScriptException("nozzle must be 'north', 'east', 'south' or 'west'");
        }
        return dir;
    }
}
