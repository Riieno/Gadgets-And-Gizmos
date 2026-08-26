package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.NavigationTablePeripheralData;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaFunction;
import dev.simulated_team.simulated.compat.computercraft.peripherals.SimPeripheral;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.Map;

// Expose Basic Navigation Table to ComputerCraft
@Pseudo
@Mixin(
        targets = "dev.simulated_team.simulated.compat.computercraft.peripherals.NavTablePeripheral",
        remap = false
)
@PeripheralTypeDoc("navigation_table")
public abstract class BasicNavigationTablePeripheralMixin extends SimPeripheral<NavTableBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the basic navigation table peripheral
    protected BasicNavigationTablePeripheralMixin(NavTableBlockEntity blockEntity) {
        super(blockEntity);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the table position
    @LuaFunction("getTablePosition")
    @PeripheralDoc(name = "getTablePosition", signature = "getTablePosition(): table",
            description = "Returns the table position.")
    public final Map<String, Object> createthrusters$getTablePosition() {
        return NavigationTablePeripheralData.tablePosition(blockEntity);
    }

    // Get the block pos
    @LuaFunction("getBlockPos")
    @PeripheralDoc(name = "getBlockPos", signature = "getBlockPos(): table",
            description = "Returns the block pos.")
    public final Map<String, Object> createthrusters$getBlockPos() {
        return createthrusters$getTablePosition();
    }

    // Get the block
    @LuaFunction("getblock")
    @PeripheralDoc(name = "getblock", signature = "getblock(): table",
            description = "Returns the block.")
    public final Map<String, Object> createthrusters$getBlock() {
        return createthrusters$getTablePosition();
    }

    // Get the target position
    @LuaFunction("getTargetPosition")
    @PeripheralDoc(name = "getTargetPosition", signature = "getTargetPosition(): table?",
            description = "Returns the target position.")
    public final Map<String, Object> createthrusters$getTargetPosition() {
        return NavigationTablePeripheralData.targetPosition(
                blockEntity, blockEntity.getTargetPosition(false));
    }

    // Get the target pos
    @LuaFunction("getTargetPos")
    @PeripheralDoc(name = "getTargetPos", signature = "getTargetPos(): table?",
            description = "Returns the target pos.")
    public final Map<String, Object> createthrusters$getTargetPos() {
        return createthrusters$getTargetPosition();
    }

    // Get the target distance
    @LuaFunction
    @PeripheralDoc(name = "getTargetDistance", signature = "getTargetDistance(): number",
            description = "Returns the target distance.")
    public final double getTargetDistance() {
        return NavigationTablePeripheralData.targetDistance(
                blockEntity, blockEntity.getTargetPosition(false));
    }
}
