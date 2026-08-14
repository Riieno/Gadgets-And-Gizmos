package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Expose Thruster controls and telemetry to ComputerCraft
public class ThrusterPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final ThrusterBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster peripheral
    public ThrusterPeripheral(ThrusterBlockEntity blockEntity) {
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
        return "thruster";
    }

    // Compare this thruster peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ThrusterPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Set the throttle
    @LuaFunction
    public final void setThrottle(double throttle) throws LuaException {
        if (Double.isNaN(throttle) || throttle < 0.0 || throttle > 1.0) {
            throw new LuaException("throttle must be between 0.0 and 1.0");
        }
        blockEntity.setThrottle((float) throttle);
    }

    // Get the throttle
    @LuaFunction
    public final double getThrottle() {
        return blockEntity.getThrottle();
    }

    // Set the enabled
    @LuaFunction
    public final void setEnabled(boolean enabled) {
        blockEntity.setEnabled(enabled);
    }

    // Check if this is enabled
    @LuaFunction
    public final boolean isEnabled() {
        return blockEntity.isEnabled();
    }

    // Get the fuel
    @LuaFunction
    public final int getFuel() {
        return blockEntity.getFuelAmount();
    }

    // Get the fuel capacity
    @LuaFunction
    public final int getFuelCapacity() {
        return blockEntity.getFuelCapacity();
    }

    // Get the fuel type
    @LuaFunction
    public final String getFuelType() {
        return blockEntity.getFuelTypeId();
    }

    // Get the burn time seconds
    @LuaFunction
    public final double getBurnTimeSeconds() {
        return blockEntity.getEstimatedBurnSeconds();
    }

    // Get the name
    @LuaFunction
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the control mode
    @LuaFunction
    public final String getControlMode() {
        return blockEntity.getControlMode().name().toLowerCase();
    }

    // Set the control mode
    @LuaFunction
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
    @LuaFunction
    public final double getThrust() {
        return blockEntity.getThrust();
    }

    // Get the real thrust
    @LuaFunction
    public final double getRealThrust() {
        return blockEntity.getRealThrust();
    }

    // Get the lift capacity
    @LuaFunction
    public final double getLiftCapacity() {
        return blockEntity.getLiftCapacity();
    }

    // Get the airflow
    @LuaFunction
    public final double getAirflow() {
        return blockEntity.getAirflow();
    }

    // Check if this is active
    @LuaFunction
    public final boolean isActive() {
        return blockEntity.isActive();
    }

    // Check if this is a soul mode
    @LuaFunction
    public final boolean isSoulMode() {
        return blockEntity.isSoulThruster();
    }

    // Set the soul mode
    @LuaFunction
    public final void setSoulMode(boolean soulMode) {
        if (soulMode) {
            blockEntity.enableSoulThruster();
        } else {
            blockEntity.disableSoulThruster();
        }
    }

    // Clear the throttle override
    @LuaFunction
    public final void clearThrottleOverride() {
        blockEntity.clearCcThrottleOverride();
    }

    // Get the redstone signal
    @LuaFunction
    public final int getRedstoneSignal() {
        return blockEntity.getSignalStrength();
    }

    // Get the status
    @LuaFunction
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

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "setThrottle(throttle: number 0..1)",
                "getThrottle(): number",
                "setEnabled(enabled: boolean)",
                "isEnabled(): boolean",
                "getFuel(): number",
                "getFuelCapacity(): number",
                "getFuelType(): string",
                "getBurnTimeSeconds(): number",
                "getControlMode(): string",
                "setControlMode(mode: 'auto'|'redstone'|'computer')",
                "clearThrottleOverride()",
                "getThrust(): number",
                "getRealThrust(): number",
                "getLiftCapacity(): number",
                "getAirflow(): number",
                "isActive(): boolean",
                "isSoulMode(): boolean",
                "setSoulMode(enabled: boolean)",
                "getRedstoneSignal(): number",
                "getStatus(): table",
                "help(method?: string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> docs = new HashMap<>();
        docs.put("setThrottle", "setThrottle(throttle:number 0..1) - switches to COMPUTER control mode");
        docs.put("getThrottle", "getThrottle() -> current applied throttle");
        docs.put("setEnabled", "setEnabled(enabled:boolean) -> toggle thruster");
        docs.put("isEnabled", "isEnabled() -> true/false");
        docs.put("getFuel", "getFuel() -> current fuel amount (mB)");
        docs.put("getFuelCapacity", "getFuelCapacity() -> tank capacity (mB)");
        docs.put("getFuelType", "getFuelType() -> registry id of current fluid fuel, or empty string");
        docs.put("getBurnTimeSeconds", "getBurnTimeSeconds() -> estimated burn time at current throttle");
        docs.put("getControlMode", "getControlMode() -> 'redstone' or 'computer'");
        docs.put("setControlMode", "setControlMode(mode) where mode is 'auto', 'redstone', or 'computer'");
        docs.put("clearThrottleOverride", "clearThrottleOverride() -> return throttle to redstone control");
        docs.put("getThrust", "getThrust() -> current thrust output");
        docs.put("getRealThrust", "getRealThrust() -> current scaled real thrust output used by Aeronautics/Sable physics");
        docs.put("getLiftCapacity", "getLiftCapacity() -> current lift capacity derived from real thrust and local gravity");
        docs.put("getAirflow", "getAirflow() -> current airflow output");
        docs.put("isActive", "isActive() -> true when enabled, fueled, and throttled");
        docs.put("isSoulMode", "isSoulMode() -> true when haunting/soul mode is enabled");
        docs.put("setSoulMode", "setSoulMode(enabled) -> switch normal/soul mode");
        docs.put("getRedstoneSignal", "getRedstoneSignal() -> current neighboring redstone strength");
        docs.put("getStatus", "getStatus() -> table of telemetry values");
        docs.put("methods", "methods() -> list of all callable peripheral methods");
        docs.put("help", "help() -> all docs, help('name') -> one entry");

        if (method.isEmpty()) {
            return docs;
        }
        String key = method.get();
        if (!docs.containsKey(key)) {
            throw new LuaException("unknown method '" + key + "'");
        }
        return docs.get(key);
    }
}
