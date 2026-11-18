package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.DirectionalGearshiftPeripheralControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

// Expose Directional Gearshift controls and telemetry to ComputerCraft
@PeripheralTypeDoc("directional_gearshift")
public class DirectionalGearshiftPeripheral extends GadgetsPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final DirectionalGearshiftPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public DirectionalGearshiftPeripheral(BlockEntity blockEntity) {
        super(blockEntity, "directional_gearshift");
        this.shared = new DirectionalGearshiftPeripheralControl(blockEntity);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() { return shared.getName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) { shared.setName(name); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isLeftPowered", signature = "isLeftPowered(): boolean",
            description = "Returns whether the left is powered.")
    public final boolean isLeftPowered() { return shared.isLeftPowered(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isRightPowered", signature = "isRightPowered(): boolean",
            description = "Returns whether the right is powered.")
    public final boolean isRightPowered() { return shared.isRightPowered(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setLeft", signature = "setLeft(powered: boolean)",
            description = "Sets the left.")
    public final void setLeft(boolean powered) { shared.setLeft(powered); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setRight", signature = "setRight(powered: boolean)",
            description = "Sets the right.")
    public final void setRight(boolean powered) { shared.setRight(powered); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setOutputs", signature = "setOutputs(leftPowered: boolean, rightPowered: boolean)",
            description = "Sets the outputs.")
    public final void setOutputs(boolean leftPowered, boolean rightPowered) { shared.setOutputs(leftPowered, rightPowered); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clear", signature = "clear()",
            description = "Clears the directional gearshift peripheral.")
    public final void clear() { shared.clear(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRotationModifier", signature = "getRotationModifier(face: string): number",
            description = "Returns the rotation modifier.")
    public final int getRotationModifier(String face) { return shared.getRotationModifier(face); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() { return shared.getStatus(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the facing.")
    public final String getFacing() { return shared.getFacing(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the world facing.")
    public final Map<String, Object> getWorldFacing() { return shared.getWorldFacing(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getPosition", signature = "getPosition(): table",
            description = "Returns the position.")
    public final Map<String, Object> getPosition() { return shared.getPosition(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getClassName", signature = "getClassName(): string",
            description = "Returns the class name.")
    public final String getClassName() { return shared.getClassName(); }

}
