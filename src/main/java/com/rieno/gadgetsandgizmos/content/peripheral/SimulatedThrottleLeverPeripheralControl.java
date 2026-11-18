package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptBlockPeripheral;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

// Expose Simulated Throttle Lever controls and telemetry to ComputerCraft
@PeripheralTypeDoc("simulated_throttle_lever")
public class SimulatedThrottleLeverPeripheralControl extends ScriptBlockPeripheral<BlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the simulated throttle lever peripheral
    public SimulatedThrottleLeverPeripheralControl(BlockEntity blockEntity) {
        super(blockEntity, "simulated_throttle_lever");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the name
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
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
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
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
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getSignal", signature = "getSignal(): number",
            description = "Returns the signal.")
    public final int getSignal() {
        Object val = invokeNoArgs("getSignal", "getOutputSignal", "getRedstoneSignal");
        if (val instanceof Number num) {
            return Math.max(0, Math.min(15, num.intValue()));
        }
        return 0;
    }

    // Set the signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setSignal", signature = "setSignal(signal: number)",
            description = "Sets the signal.")
    public final void setSignal(int signal) throws ScriptException {
        if (signal < 0 || signal > 15) {
            throw new ScriptException("signal must be between 0 and 15");
        }
        boolean applied = invokeOneArg("setSignal", int.class, signal)
                || invokeOneArg("setSignal", Integer.class, signal);
        if (!applied) {
            throw new ScriptException("this throttle lever does not support setSignal via peripheral");
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
