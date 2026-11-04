package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

// Bind the library's assembly controls to the addon's documented Lua surface
public abstract class BearingThrusterPeripheral<T extends BlockEntity> extends GadgetsPeripheral<T>{
    protected BearingThrusterPeripheral(T be, String type){ super(be, type); }

    protected abstract AssemblyThrusterControl control();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the list thrusters
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listThrusters", signature = "listThrusters(): table",
            description = "Returns the list thrusters.")
    public final Map<String, Object> listThrusters(){
        return control().listThrusters();
    }

    // Get the list of RCS thrusters
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listRcsThrusters", signature = "listRcsThrusters(): table",
            description = "Returns the RCS thrusters attached to the bearing head.")
    public final Map<String, Object> listRcsThrusters(){
        return control().listRcsThrusters();
    }

    // Get the RCS thruster count
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRcsThrusterCount", signature = "getRcsThrusterCount(): number",
            description = "Returns the attached RCS thruster count.")
    public final int getRcsThrusterCount(){
        return control().getRcsThrusterCount();
    }

    // Set one RCS nozzle throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setRcsThrottle", signature = "setRcsThrottle(id|'all', nozzle, throttle: number 0..1 or 0..100)",
            description = "Sets one nozzle throttle on an attached RCS thruster.")
    public final void setRcsThrottle(String id, String nozzle, double throttle) throws LuaException{
        try{ control().setRcsThrottle(id, nozzle, throttle); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get one RCS nozzle throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRcsThrottle", signature = "getRcsThrottle(id, nozzle): number",
            description = "Returns one nozzle throttle from an attached RCS thruster.")
    public final double getRcsThrottle(String id, String nozzle) throws LuaException{
        try{ return control().getRcsThrottle(id, nozzle); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Clear one RCS nozzle throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearRcsThrottle", signature = "clearRcsThrottle(id|'all', nozzle)",
            description = "Clears one nozzle throttle on an attached RCS thruster.")
    public final void clearRcsThrottle(String id, String nozzle) throws LuaException{
        try{ control().clearRcsThrottle(id, nozzle); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get one RCS thruster status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRcsThrusterStatus", signature = "getRcsThrusterStatus(id): table",
            description = "Returns the status of one attached RCS thruster.")
    public final Map<String, Object> getRcsThrusterStatus(String id) throws LuaException{
        try{ return control().getRcsThrusterStatus(id); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the thruster count
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrusterCount", signature = "getThrusterCount(): number",
            description = "Returns the thruster count.")
    public final int getThrusterCount(){
        return control().getThrusterCount();
    }

    // Get the owned thrusters
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getOwnedThrusters", signature = "getOwnedThrusters(): string[]",
            description = "Returns the owned thrusters.")
    public final List<String> getOwnedThrusters(){
        return control().getOwnedThrusters();
    }

    // Get the ids
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "ids", signature = "ids(): string[]",
            description = "Returns the ids.")
    public final List<String> ids(){
        return control().ids();
    }

    // Get the network info
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNetworkInfo", signature = "getNetworkInfo(): table",
            description = "Returns the network info.")
    public final Map<String, Object> getNetworkInfo(){
        return control().getNetworkInfo();
    }

    // Handle the thruster alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "thrusterAlias", signature = "thrusterAlias(id|alias, alias: string)",
            description = "Handle the thruster alias.")
    public final void thrusterAlias(String thrusterId, String alias) throws LuaException{
        try{ control().thrusterAlias(thrusterId, alias); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Set the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setThrottle", signature = "setThrottle(id|'all', throttle: number 0..1 or 0..100)",
            description = "Sets the throttle.")
    public final void setThrottle(String thrusterId, double throttle) throws LuaException{
        try{ control().setThrottle(thrusterId, throttle); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the throttle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottle", signature = "getThrottle(id|'all'): number|table",
            description = "Returns the throttle.")
    public final Object getThrottle(String thrusterId) throws LuaException{
        try{ return control().getThrottle(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the throttle map
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrottleMap", signature = "getThrottleMap(id|'all'): table",
            description = "Returns the throttle map.")
    public final Map<String, Object> getThrottleMap(String thrusterId) throws LuaException{
        try{ return control().getThrottleMap(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Set the enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setEnabled", signature = "setEnabled(id|'all', enabled: boolean)",
            description = "Sets the enabled.")
    public final void setEnabled(String thrusterId, boolean enabled) throws LuaException{
        try{ control().setEnabled(thrusterId, enabled); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Check if the bearing is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isEnabled", signature = "isEnabled(id|'all'): boolean|table",
            description = "Returns whether the bearing is enabled.")
    public final Object isEnabled(String thrusterId) throws LuaException{
        try{ return control().isEnabled(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the fuel
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuel", signature = "getFuel(id|'all'): number|table",
            description = "Returns the fuel.")
    public final Object getFuel(String thrusterId) throws LuaException{
        try{ return control().getFuel(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the fuel capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelCapacity", signature = "getFuelCapacity(id|'all'): number|table",
            description = "Returns the fuel capacity.")
    public final Object getFuelCapacity(String thrusterId) throws LuaException{
        try{ return control().getFuelCapacity(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the fuel type
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFuelType", signature = "getFuelType(id|'all'): string|table",
            description = "Returns the fuel type.")
    public final Object getFuelType(String thrusterId) throws LuaException{
        try{ return control().getFuelType(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the burn time seconds
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBurnTimeSeconds", signature = "getBurnTimeSeconds(id|'all'): number|table",
            description = "Returns the burn time seconds.")
    public final Object getBurnTimeSeconds(String thrusterId) throws LuaException{
        try{ return control().getBurnTimeSeconds(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrust", signature = "getThrust(id|'all'): number|table",
            description = "Returns the thrust.")
    public final Object getThrust(String thrusterId) throws LuaException{
        try{ return control().getThrust(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the real thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRealThrust", signature = "getRealThrust(id|'all'): number|table",
            description = "Returns the real thrust.")
    public final Object getRealThrust(String thrusterId) throws LuaException{
        try{ return control().getRealThrust(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the lift capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLiftCapacity", signature = "getLiftCapacity(id|'all'): number|table",
            description = "Returns the lift capacity.")
    public final Object getLiftCapacity(String thrusterId) throws LuaException{
        try{ return control().getLiftCapacity(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the total real thrust
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTotalRealThrust", signature = "getTotalRealThrust(): number",
            description = "Returns the total real thrust.")
    public final double getTotalRealThrust(){
        return control().getTotalRealThrust();
    }

    // Get the total lift capacity
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTotalLiftCapacity", signature = "getTotalLiftCapacity(): number",
            description = "Returns the total lift capacity.")
    public final double getTotalLiftCapacity(){
        return control().getTotalLiftCapacity();
    }

    // Get the airflow
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAirflow", signature = "getAirflow(id|'all'): number|table",
            description = "Returns the airflow.")
    public final Object getAirflow(String thrusterId) throws LuaException{
        try{ return control().getAirflow(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Check if the bearing is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isActive", signature = "isActive(id|'all'): boolean|table",
            description = "Returns whether the bearing is active.")
    public final Object isActive(String thrusterId) throws LuaException{
        try{ return control().isActive(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Check if soul mode is enabled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isSoulMode", signature = "isSoulMode(id|'all'): boolean|table",
            description = "Returns whether soul mode is enabled.")
    public final Object isSoulMode(String thrusterId) throws LuaException{
        try{ return control().isSoulMode(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Set the soul mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSoulMode", signature = "setSoulMode(id|'all', enabled: boolean)",
            description = "Sets the soul mode.")
    public final void setSoulMode(String thrusterId, boolean enabled) throws LuaException{
        try{ control().setSoulMode(thrusterId, enabled); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Clear the throttle override
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearThrottleOverride", signature = "clearThrottleOverride(id|'all')",
            description = "Clears the throttle override.")
    public final void clearThrottleOverride(String thrusterId) throws LuaException{
        try{ control().clearThrottleOverride(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the redstone signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRedstoneSignal", signature = "getRedstoneSignal(id|'all'): number|table",
            description = "Returns the redstone signal.")
    public final Object getRedstoneSignal(String thrusterId) throws LuaException{
        try{ return control().getRedstoneSignal(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the thruster status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getThrusterStatus", signature = "getThrusterStatus(id|'all'): table",
            description = "Returns the thruster status.")
    public final Object getThrusterStatus(String thrusterId) throws LuaException{
        try{ return control().getThrusterStatus(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }
}
