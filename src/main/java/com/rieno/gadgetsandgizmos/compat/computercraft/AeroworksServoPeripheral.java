package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expose Aeroworks Servo controls and telemetry to ComputerCraft
public final class AeroworksServoPeripheral implements IPeripheral {
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

    // Initialize the aeroworks servo peripheral
    public AeroworksServoPeripheral(BlockEntity blockEntity) {
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
        return "servo_bearing";
    }

    // Compare this aeroworks servo peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof AeroworksServoPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the current angle
    @LuaFunction
    public final double getCurrentAngle() {
        try {
            Method method = blockEntity.getClass().getMethod(
                    "getInterpolatedAngle", float.class);
            Object res = method.invoke(blockEntity, 1.0F);
            return res instanceof Number num ? num.doubleValue() : 0.0D;
        } catch (ReflectiveOperationException ignored) {
            return fieldNumber("currentAngle");
        }
    }

    // Get the target angle
    @LuaFunction
    public final double getTargetAngle() {
        return fieldNumber("target");
    }

    // Check if this is moving
    @LuaFunction
    public final boolean isMoving() {
        Object val = fieldValue("moving");
        return val instanceof Boolean moving && moving;
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("currentAngle", getCurrentAngle());
        status.put("targetAngle", getTargetAngle());
        status.put("moving", isMoving());
        status.put("position", ComputerCraftPositionHelper.blockPosition(blockEntity));
        return status;
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getCurrentAngle(): number",
                "getTargetAngle(): number",
                "isMoving(): boolean",
                "getStatus(): table"
        );
    }

    // Get the field number
    private double fieldNumber(String name) {
        Object val = fieldValue(name);
        return val instanceof Number num ? num.doubleValue() : 0.0D;
    }

    // Get the field value
    private Object fieldValue(String name) {
        Class<?> type = blockEntity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(blockEntity);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
