package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.NavigationTablePeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;


import java.util.Map;

// Read and edit navigation targets through the same rules used by the table screen
@PeripheralTypeDoc("navigation_table")
public class NavigationTablePeripheral extends GadgetsPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final NavigationTablePeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public NavigationTablePeripheral(BlockEntity blockEntity, NavigationTableExtensionAccess ext) {
        super(blockEntity, "navigation_table");
        this.shared = new NavigationTablePeripheralControl(blockEntity, ext);
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
    @PeripheralDoc(name = "getSlotCount", signature = "getSlotCount(): number",
            description = "Returns the slot count.")
    public final int getSlotCount() { return shared.getSlotCount(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedSlot", signature = "getSelectedSlot(): number",
            description = "Returns the selected slot.")
    public final int getSelectedSlot() { return shared.getSelectedSlot(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSelectedSlot", signature = "setSelectedSlot(slot:number)",
            description = "Sets the selected slot.")
    public final void setSelectedSlot(int slot) throws LuaException {
        try{ shared.setSelectedSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "nextSlot", signature = "nextSlot(): number",
            description = "Returns the next slot.")
    public final int nextSlot() { return shared.nextSlot(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "previousSlot", signature = "previousSlot(): number",
            description = "Returns the previous slot.")
    public final int previousSlot() { return shared.previousSlot(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSlot", signature = "getSlot(slot:number): table",
            description = "Returns the slot.")
    public final Map<String, Object> getSlot(int slot) throws LuaException {
        try{ return shared.getSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasMap", signature = "hasMap(slot:number): boolean",
            description = "Returns whether this has map.")
    public final boolean hasMap(int slot) throws LuaException {
        try{ return shared.hasMap(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMapName", signature = "getMapName(slot:number): string",
            description = "Returns the map name.")
    public final String getMapName(int slot) throws LuaException {
        try{ return shared.getMapName(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSlotTarget", signature = "getSlotTarget(slot:number): table",
            description = "Returns the slot target.")
    public final Map<String, Object> getSlotTarget(int slot) throws LuaException {
        try{ return shared.getSlotTarget(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listSlots", signature = "listSlots(): table[]",
            description = "Returns the list slots.")
    public final Object[] listSlots() { return shared.listSlots(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFilledSlotCount", signature = "getFilledSlotCount(): number",
            description = "Returns the filled slot count.")
    public final int getFilledSlotCount() { return shared.getFilledSlotCount(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearSlot", signature = "clearSlot(slot:number)",
            description = "Clears the slot.")
    public final void clearSlot(int slot) throws LuaException {
        try{ shared.clearSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAllSlots", signature = "clearAllSlots()",
            description = "Clears every navigation slot.")
    public final void clearAllSlots() { shared.clearAllSlots(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getState", signature = "getState(): string",
            description = "Returns the state.")
    public final String getState() { return shared.getState(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setState", signature = "setState(state:string)",
            description = "Sets the state.")
    public final void setState(String state) throws LuaException {
        try{ shared.setState(state); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isRunning", signature = "isRunning(): boolean",
            description = "Returns whether this is running.")
    public final boolean isRunning() { return shared.isRunning(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isPaused", signature = "isPaused(): boolean",
            description = "Returns whether this is paused.")
    public final boolean isPaused() { return shared.isPaused(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isIdle", signature = "isIdle(): boolean",
            description = "Returns whether this is idle.")
    public final boolean isIdle() { return shared.isIdle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "start", signature = "start()",
            description = "Start the navigation table peripheral.")
    public final void start() { shared.start(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pause", signature = "pause()",
            description = "Pause the navigation table peripheral.")
    public final void pause() { shared.pause(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "stop", signature = "stop()",
            description = "Stop the navigation table peripheral.")
    public final void stop() { shared.stop(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTablePosition", signature = "getTablePosition(): table",
            description = "Returns the table position.")
    public final Map<String, Object> getTablePosition() { return shared.getTablePosition(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBlockPos", signature = "getBlockPos(): table",
            description = "Returns the block pos.")
    public final Map<String, Object> getBlockPos() { return shared.getBlockPos(); }

    @LuaFunction(value = "getblock", mainThread = true)
    @PeripheralDoc(name = "getblock", signature = "getblock(): table",
            description = "Returns the block.")
    public final Map<String, Object> getBlock() { return shared.getBlock(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
    public final double getCurrentAngle() { return shared.getCurrentAngle(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getVector", signature = "getVector(): table",
            description = "Returns the vector.")
    public final Map<String, Object> getVector() { return shared.getVector(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasTarget", signature = "hasTarget(): boolean",
            description = "Returns whether this has target.")
    public final boolean hasTarget() { return shared.hasTarget(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasTargetInSlot", signature = "hasTargetInSlot(slot:number): boolean",
            description = "Returns whether this has target in slot.")
    public final boolean hasTargetInSlot(int slot) throws LuaException {
        try{ return shared.hasTargetInSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetLabel", signature = "getTargetLabel(): string?",
            description = "Returns the target label.")
    public final String getTargetLabel() { return shared.getTargetLabel(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetLabelInSlot", signature = "getTargetLabelInSlot(slot:number): string?",
            description = "Returns the target label in slot.")
    public final String getTargetLabelInSlot(int slot) throws LuaException {
        try{ return shared.getTargetLabelInSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTarget", signature = "getTarget(): table",
            description = "Returns the target.")
    public final Map<String, Object> getTarget() { return shared.getTarget(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetPosition", signature = "getTargetPosition(): table?",
            description = "Returns the target position.")
    public final Map<String, Object> getTargetPosition() { return shared.getTargetPosition(); }

    @LuaFunction(value = "getTargetPos", mainThread = true)
    @PeripheralDoc(name = "getTargetPos", signature = "getTargetPos(): table?",
            description = "Returns the target pos.")
    public final Map<String, Object> getTargetPos() { return shared.getTargetPos(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetPositionInSlot", signature = "getTargetPositionInSlot(slot:number): table?",
            description = "Returns the target position in slot.")
    public final Map<String, Object> getTargetPositionInSlot(int slot) throws LuaException {
        try{ return shared.getTargetPositionInSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetDistance", signature = "getTargetDistance(): number",
            description = "Returns the target distance.")
    public final double getTargetDistance() { return shared.getTargetDistance(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetDistanceInSlot", signature = "getTargetDistanceInSlot(slot:number): number",
            description = "Returns the target distance in slot.")
    public final double getTargetDistanceInSlot(int slot) throws LuaException {
        try{ return shared.getTargetDistanceInSlot(slot); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSelectedMapInfo", signature = "getSelectedMapInfo(): table",
            description = "Returns the selected map info.")
    public final Map<String, Object> getSelectedMapInfo() throws LuaException {
        try{ return shared.getSelectedMapInfo(); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
