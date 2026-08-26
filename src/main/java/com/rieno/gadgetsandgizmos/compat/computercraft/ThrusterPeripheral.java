package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Expose Thruster controls and telemetry to ComputerCraft
@PeripheralTypeDoc("thruster")
public class ThrusterPeripheral extends GadgetsPeripheral<ThrusterBlockEntity> {
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

    // Initialize the thruster peripheral
    public ThrusterPeripheral(ThrusterBlockEntity blockEntity) {
        super(blockEntity, "thruster");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setThrottle", signature = "setThrottle(throttle: number 0..1)",
            description = "Switches to COMPUTER control mode.")
    public final void setThrottle(double throttle) throws LuaException {
        if (Double.isNaN(throttle) || throttle < 0.0 || throttle > 1.0) {
            throw new LuaException("throttle must be between 0.0 and 1.0");
        }
        blockEntity.setThrottle((float) throttle);
    }

    // Get the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottle", signature = "getThrottle(): number",
            description = "Current applied throttle.")
    public final double getThrottle() {
        return blockEntity.getThrottle();
    }

    // Set the enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setEnabled", signature = "setEnabled(enabled: boolean)",
            description = "Toggle thruster.")
    public final void setEnabled(boolean enabled) {
        blockEntity.setEnabled(enabled);
    }

    // Check if this is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isEnabled", signature = "isEnabled(): boolean",
            description = "True/false.")
    public final boolean isEnabled() {
        return blockEntity.isEnabled();
    }

    // Get the fuel
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuel", signature = "getFuel(): number",
            description = "Current fuel amount (mB).")
    public final int getFuel() {
        return blockEntity.getFuelAmount();
    }

    // Get the fuel capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelCapacity", signature = "getFuelCapacity(): number",
            description = "Tank capacity (mB).")
    public final int getFuelCapacity() {
        return blockEntity.getFuelCapacity();
    }

    // Get the fuel type
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelType", signature = "getFuelType(): string",
            description = "Registry id of current fluid fuel, or empty string.")
    public final String getFuelType() {
        return blockEntity.getFuelTypeId();
    }

    // Get the burn time seconds
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBurnTimeSeconds", signature = "getBurnTimeSeconds(): number",
            description = "Estimated burn time at current throttle.")
    public final double getBurnTimeSeconds() {
        return blockEntity.getEstimatedBurnSeconds();
    }

    // Get the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode(): string",
            description = "'redstone' or 'computer'.")
    public final String getControlMode() {
        return blockEntity.getControlMode().name().toLowerCase();
    }

    // Set the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(mode: 'auto'|'redstone'|'computer')",
            description = "SetControlMode(mode) where mode is 'auto', 'redstone', or 'computer'.")
    public final void setControlMode(String mode) throws LuaException {
        if (mode == null) {
            throw new LuaException("mode must be 'auto', 'redstone' or 'computer'");
        }
        String normalized = mode.trim().toLowerCase();
        if (normalized.equals("auto")) {
            blockEntity.setControlMode(ThrusterBlockEntity.ControlMode.AUTO);
            return;
        }
        if (normalized.equals("redstone")) {
            blockEntity.setControlMode(ThrusterBlockEntity.ControlMode.REDSTONE);
            return;
        }
        if (normalized.equals("computer")) {
            blockEntity.setControlMode(ThrusterBlockEntity.ControlMode.COMPUTER);
            return;
        }
        throw new LuaException("mode must be 'auto', 'redstone' or 'computer'");
    }

    // Get the thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrust", signature = "getThrust(): number",
            description = "Current thrust output.")
    public final double getThrust() {
        return blockEntity.getThrust();
    }

    // Get the real thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRealThrust", signature = "getRealThrust(): number",
            description = "Current scaled real thrust output used by Aeronautics/Sable physics.")
    public final double getRealThrust() {
        return blockEntity.getRealThrust();
    }

    // Get the lift capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLiftCapacity", signature = "getLiftCapacity(): number",
            description = "Current lift capacity derived from real thrust and local gravity.")
    public final double getLiftCapacity() {
        return blockEntity.getLiftCapacity();
    }

    // Get the airflow
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAirflow", signature = "getAirflow(): number",
            description = "Current airflow output.")
    public final double getAirflow() {
        return blockEntity.getAirflow();
    }

    // Check if this is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(): boolean",
            description = "True when enabled, fueled, and throttled.")
    public final boolean isActive() {
        return blockEntity.isActive();
    }

    // Check if this is a soul mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isSoulMode", signature = "isSoulMode(): boolean",
            description = "True when haunting/soul mode is enabled.")
    public final boolean isSoulMode() {
        return blockEntity.isSoulThruster();
    }

    // Set the soul mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSoulMode", signature = "setSoulMode(enabled: boolean)",
            description = "Switch normal/soul mode.")
    public final void setSoulMode(boolean soulMode) {
        if (soulMode) {
            blockEntity.enableSoulThruster();
        } else {
            blockEntity.disableSoulThruster();
        }
    }

    // Clear the throttle override
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearThrottleOverride", signature = "clearThrottleOverride()",
            description = "Return throttle to redstone control.")
    public final void clearThrottleOverride() {
        blockEntity.clearCcThrottleOverride();
    }

    // Get the redstone signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstoneSignal", signature = "getRedstoneSignal(): number",
            description = "Current neighboring redstone strength.")
    public final int getRedstoneSignal() {
        return blockEntity.getSignalStrength();
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Table of telemetry values.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", blockEntity.isEnabled());
        status.put("throttle", blockEntity.getThrottle());
        status.put("computerThrottle", blockEntity.getComputerThrottle());
        status.put("controlMode", blockEntity.getControlMode().name().toLowerCase());
        status.put("fuel", blockEntity.getFuelAmount());
        status.put("fuelCapacity", blockEntity.getFuelCapacity());
        status.put("fuelType", blockEntity.getFuelTypeId());
        status.put("burnTimeSeconds", blockEntity.getEstimatedBurnSeconds());
        status.put("thrust", blockEntity.getThrust());
        status.put("realThrust", blockEntity.getRealThrust());
        status.put("liftCapacity", blockEntity.getLiftCapacity());
        status.put("airflow", blockEntity.getAirflow());
        status.put("active", blockEntity.isActive());
        status.put("soulMode", blockEntity.isSoulThruster());
        status.put("redstoneSignal", blockEntity.getSignalStrength());
        return status;
    }
}
