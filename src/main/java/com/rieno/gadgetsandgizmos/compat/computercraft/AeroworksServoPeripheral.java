package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Expose Aeroworks Servo controls and telemetry to ComputerCraft
@PeripheralTypeDoc("servo_bearing")
public final class AeroworksServoPeripheral extends GadgetsPeripheral<BlockEntity> {
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

    // Initialize the aeroworks servo peripheral
    public AeroworksServoPeripheral(BlockEntity blockEntity) {
        super(blockEntity, "servo_bearing");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the current angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetAngle", signature = "getTargetAngle(): number",
            description = "Returns the target angle.")
    public final double getTargetAngle() {
        return fieldNumber("target");
    }

    // Check if this is moving
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isMoving", signature = "isMoving(): boolean",
            description = "Returns whether this is moving.")
    public final boolean isMoving() {
        Object val = fieldValue("moving");
        return val instanceof Boolean moving && moving;
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("currentAngle", getCurrentAngle());
        status.put("targetAngle", getTargetAngle());
        status.put("moving", isMoving());
        status.put("position", ComputerCraftPositionHelper.blockPosition(blockEntity));
        return status;
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
