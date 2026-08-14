package com.rieno.gadgetsandgizmos.compat.createpropulsion;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

// Bridge Create Propulsion vector-thruster angles through cached reflective access
public final class PropulsionVectorThrusterAngles {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final float MAX_VECTOR_ANGLE_DEGREES = 30.0f;
    public static final String ANGLE_OVERRIDE_TAG = "CreateThrustersVectorAngleOverride";
    public static final String ANGLE_X_TAG = "CreateThrustersVectorAngleX";
    public static final String ANGLE_Y_TAG = "CreateThrustersVectorAngleY";

    private static final ClassValue<FieldAccess> PERIPHERAL_ACCESS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected FieldAccess computeValue(Class<?> type) {
            return new FieldAccess(findField(type, "blockEntity"));
        }
    };
    private static final ClassValue<VectorThrusterAccess> VECTOR_THRUSTER_ACCESS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected VectorThrusterAccess computeValue(Class<?> type) {
            return new VectorThrusterAccess(
                    findField(type, "controlMode"),
                    findField(type, "westLink"),
                    findField(type, "eastLink"),
                    findField(type, "downLink"),
                    findField(type, "upLink"),
                    findMethod(type, "dirtyThrust"),
                    findMethod(type, "setChanged"),
                    findMethod(type, "notifyUpdate"));
        }
    };
    private static final ClassValue<FrequencyLinkAccess> FREQUENCY_LINK_ACCESS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected FrequencyLinkAccess computeValue(Class<?> type) {
            return new FrequencyLinkAccess(findPublicMethod(type, "getFrequency", boolean.class));
        }
    };
    private static final ClassValue<FrequencyAccess> FREQUENCY_ACCESS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected FrequencyAccess computeValue(Class<?> type) {
            return new FrequencyAccess(findPublicMethod(type, "getStack"));
        }
    };
    private static final ClassValue<StackAccess> STACK_ACCESS = new ClassValue<>() {
        // Calculate the value
        @Override
        protected StackAccess computeValue(Class<?> type) {
            return new StackAccess(findPublicMethod(type, "isEmpty"));
        }
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the propulsion vector thruster angles
    private PropulsionVectorThrusterAngles() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the maximum angle degrees
    public static double maxAngleDegrees() {
        return MAX_VECTOR_ANGLE_DEGREES;
    }

    // Clamp the degrees
    public static float clampDegrees(double deg) {
        return Mth.clamp((float) deg, -MAX_VECTOR_ANGLE_DEGREES, MAX_VECTOR_ANGLE_DEGREES);
    }

    // Get the normalized angle
    public static float normalizedAngle(float deg) {
        return Mth.clamp(deg / MAX_VECTOR_ANGLE_DEGREES, -1.0f, 1.0f);
    }

    // Get the degrees from normalized
    public static float degreesFromNormalized(float normalized) {
        return Mth.clamp(normalized, -1.0f, 1.0f) * MAX_VECTOR_ANGLE_DEGREES;
    }

    // Write the propulsion vector thruster angles
    public static void write(CompoundTag tag, boolean override, float xDegrees, float yDegrees) {
        if (!override) {
            tag.remove(ANGLE_OVERRIDE_TAG);
            tag.remove(ANGLE_X_TAG);
            tag.remove(ANGLE_Y_TAG);
            return;
        }

        tag.putBoolean(ANGLE_OVERRIDE_TAG, true);
        tag.putFloat(ANGLE_X_TAG, clampDegrees(xDegrees));
        tag.putFloat(ANGLE_Y_TAG, clampDegrees(yDegrees));
    }

    // Check if this has stored override
    public static boolean hasStoredOverride(CompoundTag tag) {
        return tag.getBoolean(ANGLE_OVERRIDE_TAG);
    }

    // Read the stored angle
    public static float readStoredAngle(CompoundTag tag, String key) {
        return clampDegrees(tag.getFloat(key));
    }

    // Get the angles
    public static Map<String, Object> angles(float targetVectorX, float targetVectorY, float currentVectorX,
                                             float currentVectorY, boolean override) {
        Map<String, Object> angles = new LinkedHashMap<>();
        angles.put("targetX", degreesFromNormalized(targetVectorX));
        angles.put("targetY", degreesFromNormalized(targetVectorY));
        angles.put("currentX", degreesFromNormalized(currentVectorX));
        angles.put("currentY", degreesFromNormalized(currentVectorY));
        angles.put("override", override);
        angles.put("min", -maxAngleDegrees());
        angles.put("max", maxAngleDegrees());
        return angles;
    }

    // Check if this should ignore unconfigured vector signals
    public static boolean shouldIgnoreUnconfiguredVectorSignals(Object blockEntity) {
        return !isPeripheralControlled(blockEntity)
                && !hasConfiguredVectorLink(blockEntity, "westLink")
                && !hasConfiguredVectorLink(blockEntity, "eastLink")
                && !hasConfiguredVectorLink(blockEntity, "downLink")
                && !hasConfiguredVectorLink(blockEntity, "upLink");
    }

    // Sync the propulsion vector thruster angles
    public static void sync(Object blockEntity) {
        VectorThrusterAccess access = VECTOR_THRUSTER_ACCESS.get(blockEntity.getClass());
        invokeNoArg(blockEntity, access.dirtyThrust());
        invokeNoArg(blockEntity, access.setChanged());
        invokeNoArg(blockEntity, access.notifyUpdate());
    }

    // Find the angle target
    public static PropulsionVectorThrusterAngleAccess findAngleTarget(Object peripheral) {
        Field blockEntityField = PERIPHERAL_ACCESS.get(peripheral.getClass()).blockEntity();
        if (blockEntityField == null) {
            return null;
        }
        try {
            Object blockEntity = blockEntityField.get(peripheral);
            return blockEntity instanceof PropulsionVectorThrusterAngleAccess access ? access : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    // Check if peripheral control is active
    private static boolean isPeripheralControlled(Object blockEntity) {
        Field controlModeField = VECTOR_THRUSTER_ACCESS.get(blockEntity.getClass()).controlMode();
        if (controlModeField == null) {
            return false;
        }
        try {
            Object controlMode = controlModeField.get(blockEntity);
            return controlMode != null && "PERIPHERAL".equals(String.valueOf(controlMode));
        } catch (IllegalAccessException ignored) {
            return false;
        }
    }

    // Check if this has configured vector link
    private static boolean hasConfiguredVectorLink(Object blockEntity, String fieldName) {
        Field linkField = VECTOR_THRUSTER_ACCESS.get(blockEntity.getClass()).link(fieldName);
        if (linkField == null) {
            return true;
        }
        try {
            Object link = linkField.get(blockEntity);
            if (link == null) {
                return false;
            }
            return hasConfiguredFrequency(link, true) || hasConfiguredFrequency(link, false);
        } catch (IllegalAccessException ignored) {
            return true;
        }
    }

    // Check if this has configured frequency
    private static boolean hasConfiguredFrequency(Object link, boolean firstSlot) {
        Method getFrequency = FREQUENCY_LINK_ACCESS.get(link.getClass()).getFrequency();
        if (getFrequency == null) {
            return true;
        }
        try {
            Object frequency = getFrequency.invoke(link, firstSlot);
            if (frequency == null) {
                return false;
            }

            Method getStack = FREQUENCY_ACCESS.get(frequency.getClass()).getStack();
            if (getStack == null) {
                return true;
            }
            Object stack = getStack.invoke(frequency);
            if (stack == null) {
                return false;
            }

            Method isEmpty = STACK_ACCESS.get(stack.getClass()).isEmpty();
            if (isEmpty == null) {
                return true;
            }
            Object empty = isEmpty.invoke(stack);
            return empty instanceof Boolean isEmptyStack && !isEmptyStack;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    // Find the field
    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    // Find the method
    private static Method findMethod(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    // Find the public method
    private static Method findPublicMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    // Invoke a method without arguments
    private static void invokeNoArg(Object target, Method method) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Expose field
    private record FieldAccess(Field blockEntity) {
    }

    // Expose frequency link
    private record FrequencyLinkAccess(Method getFrequency) {
    }

    // Expose frequency
    private record FrequencyAccess(Method getStack) {
    }

    // Expose stack
    private record StackAccess(Method isEmpty) {
    }

    // Expose vector thruster
    private record VectorThrusterAccess(
            Field controlMode,
            Field westLink,
            Field eastLink,
            Field downLink,
            Field upLink,
            Method dirtyThrust,
            Method setChanged,
            Method notifyUpdate
    ) {
        // Get the link
        private Field link(String name) {
            return switch (name) {
                case "westLink" -> westLink;
                case "eastLink" -> eastLink;
                case "downLink" -> downLink;
                case "upLink" -> upLink;
                default -> null;
            };
        }
    }
}
