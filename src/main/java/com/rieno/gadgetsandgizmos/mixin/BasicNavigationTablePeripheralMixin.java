package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.NavigationTablePeripheralData;
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
    @LuaFunction(value = {"getblock", "getBlockPos", "getTablePosition"})
    public final Map<String, Object> createthrusters$getTablePosition() {
        return NavigationTablePeripheralData.tablePosition(blockEntity);
    }

    // Get the target position
    @LuaFunction(value = {"getTargetPos", "getTargetPosition"})
    public final Map<String, Object> createthrusters$getTargetPosition() {
        return NavigationTablePeripheralData.targetPosition(
                blockEntity, blockEntity.getTargetPosition(false));
    }

    // Get the target distance
    @LuaFunction
    public final double getTargetDistance() {
        return NavigationTablePeripheralData.targetDistance(
                blockEntity, blockEntity.getTargetPosition(false));
    }
}
