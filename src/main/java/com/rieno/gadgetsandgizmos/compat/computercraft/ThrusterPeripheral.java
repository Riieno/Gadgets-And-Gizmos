package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.ThrusterPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.Map;

// Expose Thruster controls and telemetry to ComputerCraft
@PeripheralTypeDoc("thruster")
public class ThrusterPeripheral extends GadgetsPeripheral<ThrusterBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ThrusterPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ThrusterPeripheral(ThrusterBlockEntity blockEntity) {
        super(blockEntity, "thruster");
        this.shared = new ThrusterPeripheralControl(blockEntity);
    }

    @Override
    public ThrusterBlockEntity getTarget() { return shared.getTarget(); }

    @LuaFunction
    @PeripheralDoc(name = "setThrottle", signature = "setThrottle(throttle: number 0..1)",
            description = "Switches to COMPUTER control mode.")
    public final void setThrottle(double throttle) throws LuaException {
        try{ shared.setThrottle(throttle); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottle", signature = "getThrottle(): number",
            description = "Current applied throttle.")
    public final double getThrottle() { return shared.getThrottle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setEnabled", signature = "setEnabled(enabled: boolean)",
            description = "Toggle thruster.")
    public final void setEnabled(boolean enabled) { shared.setEnabled(enabled); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isEnabled", signature = "isEnabled(): boolean",
            description = "True/false.")
    public final boolean isEnabled() { return shared.isEnabled(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuel", signature = "getFuel(): number",
            description = "Current fuel amount (mB).")
    public final int getFuel() { return shared.getFuel(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelCapacity", signature = "getFuelCapacity(): number",
            description = "Tank capacity (mB).")
    public final int getFuelCapacity() { return shared.getFuelCapacity(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelType", signature = "getFuelType(): string",
            description = "Registry id of current fluid fuel, or empty string.")
    public final String getFuelType() { return shared.getFuelType(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBurnTimeSeconds", signature = "getBurnTimeSeconds(): number",
            description = "Estimated burn time at current throttle.")
    public final double getBurnTimeSeconds() { return shared.getBurnTimeSeconds(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() { return shared.getName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) { shared.setName(name); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode(): string",
            description = "'redstone' or 'computer'.")
    public final String getControlMode() { return shared.getControlMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(mode: 'auto'|'redstone'|'computer')",
            description = "SetControlMode(mode) where mode is 'auto', 'redstone', or 'computer'.")
    public final void setControlMode(String mode) throws LuaException {
        try{ shared.setControlMode(mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrust", signature = "getThrust(): number",
            description = "Current thrust output.")
    public final double getThrust() { return shared.getThrust(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRealThrust", signature = "getRealThrust(): number",
            description = "Current scaled real thrust output used by Aeronautics/Sable physics.")
    public final double getRealThrust() { return shared.getRealThrust(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLiftCapacity", signature = "getLiftCapacity(): number",
            description = "Current lift capacity derived from real thrust and local gravity.")
    public final double getLiftCapacity() { return shared.getLiftCapacity(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAirflow", signature = "getAirflow(): number",
            description = "Current airflow output.")
    public final double getAirflow() { return shared.getAirflow(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(): boolean",
            description = "True when enabled, fueled, and throttled.")
    public final boolean isActive() { return shared.isActive(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isSoulMode", signature = "isSoulMode(): boolean",
            description = "True when haunting/soul mode is enabled.")
    public final boolean isSoulMode() { return shared.isSoulMode(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSoulMode", signature = "setSoulMode(enabled: boolean)",
            description = "Switch normal/soul mode.")
    public final void setSoulMode(boolean soulMode) { shared.setSoulMode(soulMode); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearThrottleOverride", signature = "clearThrottleOverride()",
            description = "Return throttle to redstone control.")
    public final void clearThrottleOverride() { shared.clearThrottleOverride(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstoneSignal", signature = "getRedstoneSignal(): number",
            description = "Current neighboring redstone strength.")
    public final int getRedstoneSignal() { return shared.getRedstoneSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Table of telemetry values.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
