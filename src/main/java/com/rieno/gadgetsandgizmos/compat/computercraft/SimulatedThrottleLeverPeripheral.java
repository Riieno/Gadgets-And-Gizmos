package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

// Expose Simulated Throttle Lever controls and telemetry to ComputerCraft
public class SimulatedThrottleLeverPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final BlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the simulated throttle lever peripheral
    public SimulatedThrottleLeverPeripheral(BlockEntity blockEntity) {
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
        return "simulated_throttle_lever";
    }

    // Compare this simulated throttle lever peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof SimulatedThrottleLeverPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the name
    @LuaFunction
    public final String getName() {
        Object val = invokeNoArgs("getCustomName", "getName");
        if (val instanceof Component component) {
            return component.getString();
        }
        if (val instanceof String string) {
            return string;
        }
        return "";
    }

    // Set the name
    @LuaFunction
    public final void setName(String name) {
        String normalized = name == null || name.isBlank() ? null : name.strip();
        boolean applied = invokeOneArg("setCustomName", String.class, normalized)
                || invokeOneArg("setName", String.class, normalized)
                || invokeOneArg("setCustomName", Component.class,
                normalized == null ? Component.empty() : Component.literal(normalized));
        if (applied) {
            markDirty();
        }
    }

    // Get the signal
    @LuaFunction
    public final int getSignal() {
        Object val = invokeNoArgs("getSignal", "getOutputSignal", "getRedstoneSignal");
        if (val instanceof Number num) {
            return Math.max(0, Math.min(15, num.intValue()));
        }
        return 0;
    }

    // Set the signal
    @LuaFunction
    public final void setSignal(int signal) throws LuaException {
        if (signal < 0 || signal > 15) {
            throw new LuaException("signal must be between 0 and 15");
        }
        boolean applied = invokeOneArg("setSignal", int.class, signal)
                || invokeOneArg("setSignal", Integer.class, signal);
        if (!applied) {
            throw new LuaException("this throttle lever does not support setSignal via peripheral");
        }
        markDirty();
    }

    // Invoke a method without arguments
    private Object invokeNoArgs(String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = blockEntity.getClass().getMethod(methodName);
                return method.invoke(blockEntity);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    // Run the one arg
    private boolean invokeOneArg(String methodName, Class<?> parameterType, Object arg) {
        try {
            Method method = blockEntity.getClass().getMethod(methodName, parameterType);
            method.invoke(blockEntity, arg);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Mark the dirty
    private void markDirty() {
        blockEntity.setChanged();
        if (blockEntity.getLevel() != null) {
            blockEntity.getLevel().sendBlockUpdated(blockEntity.getBlockPos(),
                    blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }
}
