package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.CreateConnectedPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

// Wrap Create connected peripherals while preserving their normal identity and equality rules
@PeripheralTypeDoc(prefixes = "create_connected_")
public final class CreateConnectedPeripheral extends GadgetsPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final CreateConnectedPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public CreateConnectedPeripheral(BlockEntity blockEntity) {
        super(blockEntity, CreateConnectedPeripheralControl.peripheralType(blockEntity));
        this.shared = new CreateConnectedPeripheralControl(blockEntity);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBlockId", signature = "getBlockId(): string",
            description = "Returns the block id.")
    public final String getBlockId() { return shared.getBlockId(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBlockEntityId", signature = "getBlockEntityId(): string",
            description = "Returns the block entity id.")
    public final String getBlockEntityId() { return shared.getBlockEntityId(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSpeed", signature = "getSpeed(): number",
            description = "Returns the speed.")
    public final double getSpeed() { return shared.getSpeed(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTheoreticalSpeed", signature = "getTheoreticalSpeed(): number",
            description = "Returns the theoretical speed.")
    public final double getTheoreticalSpeed() { return shared.getTheoreticalSpeed(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGeneratedSpeed", signature = "getGeneratedSpeed(): number",
            description = "Returns the generated speed.")
    public final double getGeneratedSpeed() { return shared.getGeneratedSpeed(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "hasNetwork", signature = "hasNetwork(): boolean",
            description = "Returns whether this has network.")
    public final boolean hasNetwork() { return shared.hasNetwork(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isOverstressed", signature = "isOverstressed(): boolean",
            description = "Returns whether this is overstressed.")
    public final boolean isOverstressed() { return shared.isOverstressed(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStress", signature = "getStress(): number",
            description = "Returns the stress.")
    public final double getStress() { return shared.getStress(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCapacity", signature = "getCapacity(): number",
            description = "Returns the capacity.")
    public final double getCapacity() { return shared.getCapacity(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getNetworkId", signature = "getNetworkId(): number",
            description = "Returns the network id.")
    public final long getNetworkId() { return shared.getNetworkId(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSource", signature = "getSource(): table",
            description = "Returns the source.")
    public final Map<String, Object> getSource() { return shared.getSource(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getOutputSpeed", signature = "getOutputSpeed(face: string): number",
            description = "Returns the output speed.")
    public final double getOutputSpeed(String face) throws LuaException {
        try{ return shared.getOutputSpeed(face); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isPowered", signature = "isPowered(): boolean",
            description = "Returns whether this is powered.")
    public final boolean isPowered() { return shared.isPowered(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setPowered", signature = "setPowered(powered: boolean)",
            description = "Sets the powered.")
    public final void setPowered(boolean powered) { shared.setPowered(powered); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setSignal", signature = "setSignal(signal: number)",
            description = "Sets the signal.")
    public final void setSignal(int signal) throws LuaException {
        try{ shared.setSignal(signal); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() { return shared.getSignal(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBatteryLevel", signature = "getBatteryLevel(): number",
            description = "Returns the battery level.")
    public final double getBatteryLevel() { return shared.getBatteryLevel(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setBatteryLevel", signature = "setBatteryLevel(level: number)",
            description = "Sets the battery level.")
    public final void setBatteryLevel(double level) throws LuaException {
        try{ shared.setBatteryLevel(level); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listControls", signature = "listControls(): table",
            description = "Returns the list controls.")
    public final Map<String, Object> listControls() { return shared.listControls(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControl", signature = "getControl(name: string): any",
            description = "Returns the control.")
    public final Object getControl(String name) throws LuaException {
        try{ return shared.getControl(name); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControl", signature = "setControl(name: string, value: number)",
            description = "Sets the control.")
    public final void setControl(String name, int value) throws LuaException {
        try{ shared.setControl(name, value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

}
