package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

// Expose Wheel Mount controls and telemetry to ComputerCraft
public class WheelMountPeripheral implements IPeripheral {
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

    // Initialize the wheel mount peripheral
    public WheelMountPeripheral(BlockEntity blockEntity) {
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
        return "wheel_mount";
    }

    // Compare this wheel mount peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof WheelMountPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Set the left
    @LuaFunction
    public final void setLeft(double value) throws LuaException {
        applyDirectSignal("yaw_left", value, "left");
    }

    // Set the right
    @LuaFunction
    public final void setRight(double value) throws LuaException {
        applyDirectSignal("yaw_right", value, "right");
    }

    // Set the brake
    @LuaFunction
    public final void setBrake(double value) throws LuaException {
        applyDirectSignal("throttle_down", value, "brake");
    }

    // Set the controls
    @LuaFunction
    public final void setControls(double left, double right, double brake) throws LuaException {
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
    @LuaFunction
    public final void clearControls() throws LuaException {
        setControls(0.0, 0.0, 0.0);
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() throws LuaException {
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
    private void applyDirectSignal(String channel, double val, String name) throws LuaException {
        float signal = requireUnitValue(val, name);
        if (!(blockEntity instanceof IDirectControlReceiver receiver)) {
            throw new LuaException("wheel mount direct receiver not available");
        }
        receiver.applyDirectControllerSignal(channel, signal);
    }

    // Get the require unit value
    private static float requireUnitValue(double val, String name) throws LuaException {
        if (!Double.isFinite(val)) {
            throw new LuaException(name + " must be a finite number");
        }
        if (val < 0.0 || val > 1.0) {
            throw new LuaException(name + " must be between 0.0 and 1.0");
        }
        return Mth.clamp((float) val, 0.0f, 1.0f);
    }

    // Get the bridge or throw
    private WheelMountControlBridge bridgeOrThrow() throws LuaException {
        if (blockEntity instanceof WheelMountControlBridge bridge) {
            return bridge;
        }
        throw new LuaException("wheel mount control bridge unavailable");
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
