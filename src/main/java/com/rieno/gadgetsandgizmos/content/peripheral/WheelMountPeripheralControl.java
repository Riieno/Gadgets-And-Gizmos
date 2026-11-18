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
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.compat.computercraft.WheelMountControlBridge;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

// Expose Wheel Mount controls and telemetry to ComputerCraft
@PeripheralTypeDoc("wheel_mount")
public class WheelMountPeripheralControl extends ScriptBlockPeripheral<BlockEntity> {
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

    // Initialize the wheel mount peripheral
    public WheelMountPeripheralControl(BlockEntity blockEntity) {
        super(blockEntity, "wheel_mount");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the left
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setLeft", signature = "setLeft(value: number)",
            description = "Sets the left.")
    public final void setLeft(double value) throws ScriptException {
        applyDirectSignal("yaw_left", value, "left");
    }

    // Set the right
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setRight", signature = "setRight(value: number)",
            description = "Sets the right.")
    public final void setRight(double value) throws ScriptException {
        applyDirectSignal("yaw_right", value, "right");
    }

    // Set the brake
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setBrake", signature = "setBrake(value: number)",
            description = "Sets the brake.")
    public final void setBrake(double value) throws ScriptException {
        applyDirectSignal("throttle_down", value, "brake");
    }

    // Set the controls
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setControls", signature = "setControls(left: number, right: number, brake: number)",
            description = "Sets the controls.")
    public final void setControls(double left, double right, double brake) throws ScriptException {
        float leftValue = requireUnitValue(left, "left");
        float rightValue = requireUnitValue(right, "right");
        float brakeValue = requireUnitValue(brake, "brake");

        WheelMountControlBridge bridge = bridgeOrThrow();
        bridge.ct$setDirectInputs(leftValue, rightValue, brakeValue);

        if (blockEntity instanceof IDirectControlReceiver receiver) {
            receiver.applyDirectControllerSignal("yaw_left", leftValue);
            receiver.applyDirectControllerSignal("yaw_right", rightValue);
            receiver.applyDirectControllerSignal("throttle_down", brakeValue);
        }
    }

    // Clear the controls
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearControls", signature = "clearControls()",
            description = "Clears the controls.")
    public final void clearControls() throws ScriptException {
        setControls(0.0, 0.0, 0.0);
    }

    // Get the status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() throws ScriptException {
        WheelMountControlBridge bridge = bridgeOrThrow();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("left", bridge.ct$getLeftOverride());
        status.put("right", bridge.ct$getRightOverride());
        status.put("brake", bridge.ct$getBrakeOverride());
        status.put("steeringSignal", bridge.ct$getEffectiveSteeringSignal());
        status.put("angle", invokeDouble("getLerpedAngle", 1.0f));
        status.put("extension", invokeDouble("getLerpedExtension", 1.0f));
        return status;
    }

    // Apply the direct signal
    private void applyDirectSignal(String channel, double val, String name) throws ScriptException {
        float signal = requireUnitValue(val, name);
        if (!(blockEntity instanceof IDirectControlReceiver receiver)) {
            throw new ScriptException("wheel mount direct receiver not available");
        }
        receiver.applyDirectControllerSignal(channel, signal);
    }

    // Get the require unit value
    private static float requireUnitValue(double val, String name) throws ScriptException {
        if (!Double.isFinite(val)) {
            throw new ScriptException(name + " must be a finite number");
        }
        if (val < 0.0 || val > 1.0) {
            throw new ScriptException(name + " must be between 0.0 and 1.0");
        }
        return Mth.clamp((float) val, 0.0f, 1.0f);
    }

    // Get the bridge or throw
    private WheelMountControlBridge bridgeOrThrow() throws ScriptException {
        if (blockEntity instanceof WheelMountControlBridge bridge) {
            return bridge;
        }
        throw new ScriptException("wheel mount control bridge unavailable");
    }

    // Run the double
    private double invokeDouble(String methodName, float partialTicks) {
        try {
            Method method = blockEntity.getClass().getMethod(methodName, float.class);
            Object res = method.invoke(blockEntity, partialTicks);
            if (res instanceof Number num) {
                return num.doubleValue();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
        return 0.0D;
    }
}
