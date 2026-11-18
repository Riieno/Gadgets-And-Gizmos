package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.ExternalMachinePeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

// Give supported external machines one guarded ComputerCraft control surface
@PeripheralTypeDoc({"laser_pointer", "laser_sensor", "analogue_transmission", "redstone_accumulator", "redstone_inductor", "redstone_magnet", "optical_sensor", "docking_connector", "altitude_sensor", "hot_air_burner", "steam_vent", "mounted_potato_cannon"})
public class ExternalMachinePeripheral extends GadgetsPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ExternalMachinePeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ExternalMachinePeripheral(BlockEntity blockEntity, String type) {
        super(blockEntity, type);
        this.shared = new ExternalMachinePeripheralControl(blockEntity, type);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() { return shared.getName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name:string)",
            description = "Sets the name.")
    public final void setName(String name) { shared.setName(name); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns current signal-like value (0-15) when available.")
    public final int getSignal() { return shared.getSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSignal", signature = "setSignal(signal:number)",
            description = "Sets signal-like value (0-15) when the target block entity supports it.")
    public final void setSignal(int signal) throws LuaException {
        try{ shared.setSignal(signal); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isPowered", signature = "isPowered(): boolean",
            description = "Returns whether this is powered.")
    public final boolean isPowered() { return shared.isPowered(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRange", signature = "getRange(): number",
            description = "Returns current laser/sensor range for blocks that expose a range behaviour.")
    public final double getRange() { return shared.getRange(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setRange", signature = "setRange(range:number)",
            description = "Sets range for compatible sensors/pointers.")
    public final void setRange(int range) throws LuaException {
        try{ shared.setRange(range); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasHit", signature = "hasHit(): boolean",
            description = "Returns whether this has hit.")
    public final boolean hasHit() { return shared.hasHit(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getDistance", signature = "getDistance(): number",
            description = "Returns the distance.")
    public final double getDistance() { return shared.getDistance(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getColor", signature = "getColor(): number",
            description = "Returns laser color as an integer RGB value.")
    public final int getColor() { return shared.getColor(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setColor", signature = "setColor(color:number)",
            description = "Sets laser color as RGB integer (0x000000 to 0xFFFFFF).")
    public final void setColor(int color) throws LuaException {
        try{ shared.setColor(color); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isRainbow", signature = "isRainbow(): boolean",
            description = "Returns true when rainbow laser mode is enabled.")
    public final boolean isRainbow() { return shared.isRainbow(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setRainbow", signature = "setRainbow(enabled:boolean)",
            description = "Enables/disables rainbow laser mode where supported.")
    public final void setRainbow(boolean enabled) throws LuaException {
        try{ shared.setRainbow(enabled); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAirPressure", signature = "getAirPressure(): number",
            description = "Returns atmosphere pressure value for altitude-capable blocks.")
    public final double getAirPressure() { return shared.getAirPressure(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getWorldHeight", signature = "getWorldHeight(): number",
            description = "Returns projected world height for altitude-capable blocks.")
    public final double getWorldHeight() { return shared.getWorldHeight(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGasOutput", signature = "getGasOutput(): number",
            description = "Returns current lifting-gas output for burner/vent blocks.")
    public final double getGasOutput() { return shared.getGasOutput(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getState", signature = "getState(): string",
            description = "Returns the block entity state enum/string when exposed.")
    public final String getState() { return shared.getState(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isBlocked", signature = "isBlocked(): boolean",
            description = "Returns blocked state for mounted potato cannon-style blocks.")
    public final boolean isBlocked() { return shared.isBlocked(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBlockedLength", signature = "getBlockedLength(): number",
            description = "Returns the blocked length.")
    public final double getBlockedLength() { return shared.getBlockedLength(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the block's local cardinal facing.")
    public final String getFacing() { return shared.getFacing(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the facing as a projected world-space vector.")
    public final Map<String, Object> getWorldFacing() { return shared.getWorldFacing(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getPosition", signature = "getPosition(): table",
            description = "Returns projected world x/y/z with local coordinates and sub-level identity.")
    public final Map<String, Object> getPosition() { return shared.getPosition(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getClassName", signature = "getClassName(): string",
            description = "Returns the class name.")
    public final String getClassName() { return shared.getClassName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns a full machine status table.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
