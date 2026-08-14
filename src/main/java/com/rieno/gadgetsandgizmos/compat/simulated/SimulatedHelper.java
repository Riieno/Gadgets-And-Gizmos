package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.List;

// Keep Sable level lookups and coordinate conversion in one guarded compatibility layer
public final class SimulatedHelper {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String GIMBAL_SENSOR_CLASS =
            "dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity";
    private static final String STEERING_WHEEL_CLASS =
            "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlockEntity";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared gimbal class
    private static Class<?> gimbalClass;
    // Resolved get x angle method
    private static Method getXAngle;
    // Resolved get z angle method
    private static Method getZAngle;
    // Resolved gimbal get power method
    private static Method gimbalGetPower;
    // Resolved gimbal axis behaviour field
    private static Field gimbalAxisBehaviour;
    // Resolved gimbal primary value field
    private static Field gimbalPrimaryValue;
    // Resolved gimbal secondary value field
    private static Field gimbalSecondaryValue;
    // Shared steering wheel class
    private static Class<?> steeringWheelClass;
    // Resolved steering wheel get angle method
    private static Method steeringWheelGetAngle;
    // Resolved steering wheel angle input field
    private static Field steeringWheelAngleInput;
    // Resolved steering wheel angle input get value method
    private static Method steeringWheelAngleInputGetValue;
    // Tracks whether simulated is initialized
    private static boolean initialized;
    // Tracks whether simulated is available
    private static boolean available;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the simulated
    private SimulatedHelper() {
    }

    // Initialize the simulated
    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("simulated")) {
            return;
        }
        try {
            gimbalClass = Class.forName(GIMBAL_SENSOR_CLASS);
            getXAngle = gimbalClass.getMethod("getXAngle");
            getZAngle = gimbalClass.getMethod("getZAngle");
            try {
                gimbalGetPower = gimbalClass.getMethod("getPower", Direction.class);
            } catch (NoSuchMethodException ignored) {
                gimbalGetPower = null;
            }
            initGimbalLimitReflection();
            available = true;
        } catch (Exception e) {

            available = false;
        }

        try {
            steeringWheelClass = Class.forName(STEERING_WHEEL_CLASS);
            steeringWheelGetAngle = steeringWheelClass.getMethod("getAngle");
            steeringWheelAngleInput = steeringWheelClass.getField("angleInput");
            steeringWheelAngleInputGetValue = steeringWheelAngleInput.getType().getMethod("getValue");
        } catch (Exception e) {
            steeringWheelClass = null;
            steeringWheelGetAngle = null;
            steeringWheelAngleInput = null;
            steeringWheelAngleInputGetValue = null;
        }
    }

    // Initialize the gimbal limit reflection
    private static void initGimbalLimitReflection() {
        gimbalAxisBehaviour = null;
        gimbalPrimaryValue = null;
        gimbalSecondaryValue = null;
        try {
            gimbalAxisBehaviour = gimbalClass.getDeclaredField("axisBehaviour");
            gimbalAxisBehaviour.setAccessible(true);
            Class<?> axisBehaviourClass = gimbalAxisBehaviour.getType();
            gimbalPrimaryValue = axisBehaviourClass.getDeclaredField("primaryValue");
            gimbalPrimaryValue.setAccessible(true);
            gimbalSecondaryValue = axisBehaviourClass.getDeclaredField("secondaryValue");
            gimbalSecondaryValue.setAccessible(true);
        } catch (Exception ignored) {
            gimbalAxisBehaviour = null;
            gimbalPrimaryValue = null;
            gimbalSecondaryValue = null;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is available
    public static boolean isAvailable() {
        init();
        return available;
    }

    // Check if this is a gimbal sensor
    public static boolean isGimbalSensor(Object blockEntity) {
        init();
        return available && gimbalClass != null && gimbalClass.isInstance(blockEntity);
    }

    // Check if this is steering wheel
    public static boolean isSteeringWheel(Object blockEntity) {
        init();
        return steeringWheelClass != null && steeringWheelClass.isInstance(blockEntity);
    }

    // Get the steering wheel angle
    public static float getSteeringWheelAngle(Object steeringWheelBE) {
        init();
        if (steeringWheelGetAngle == null || !isSteeringWheel(steeringWheelBE)) {
            return Float.NaN;
        }
        try {
            return (float) steeringWheelGetAngle.invoke(steeringWheelBE);
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    // Get the steering wheel angle limit degrees
    public static double getSteeringWheelAngleLimitDegrees(Object steeringWheelBE) {
        init();
        if (steeringWheelAngleInput == null || steeringWheelAngleInputGetValue == null
                || !isSteeringWheel(steeringWheelBE)) {
            return Double.NaN;
        }
        try {
            Object angleInput = steeringWheelAngleInput.get(steeringWheelBE);
            Object res = angleInput == null ? null : steeringWheelAngleInputGetValue.invoke(angleInput);
            return res instanceof Number num ? Math.abs(num.doubleValue()) : Double.NaN;
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    // Get the angles
    public static @Nullable double[] getAngles(Object gimbalSensorBE) {
        init();
        if (!available || !isGimbalSensor(gimbalSensorBE)) {
            return null;
        }
        try {
            double x = (double) getXAngle.invoke(gimbalSensorBE);
            double z = (double) getZAngle.invoke(gimbalSensorBE);
            return new double[]{x, z};
        } catch (Exception e) {
            return null;
        }
    }

    // Get the gimbal power
    public static int getGimbalPower(Object gimbalSensorBE, Direction dir) {
        init();
        if (!available || !isGimbalSensor(gimbalSensorBE) || dir == null || gimbalGetPower == null) {
            return 0;
        }
        try {
            Object power = gimbalGetPower.invoke(gimbalSensorBE, dir);
            return power instanceof Number num ? Math.max(0, Math.min(15, num.intValue())) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    // Get the gimbal sensor angle limit degrees
    public static double getGimbalSensorAngleLimitDegrees(Object gimbalSensorBE, Direction dir) {
        init();
        if (!available || !isGimbalSensor(gimbalSensorBE) || gimbalAxisBehaviour == null
                || gimbalPrimaryValue == null || gimbalSecondaryValue == null) {
            return Double.NaN;
        }
        try {
            Object axisBehaviour = gimbalAxisBehaviour.get(gimbalSensorBE);
            if (axisBehaviour == null) {
                return Double.NaN;
            }
            double primaryLimit = readNumericField(axisBehaviour, gimbalPrimaryValue, 45.0D);
            double secondaryLimit = readNumericField(axisBehaviour, gimbalSecondaryValue, 45.0D);
            Direction.Axis horizontalAxis = gimbalSensorBE instanceof BlockEntity blockEntity
                    ? getHorizontalAxis(blockEntity.getBlockState())
                    : null;
            boolean primarySide = dir != null && horizontalAxis != null
                    && dir.getAxis() == horizontalAxis;
            return Math.abs(primarySide ? primaryLimit : secondaryLimit);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    // Read the numeric field
    private static double readNumericField(Object instance, Field field, double fallback) throws IllegalAccessException {
        Object val = field.get(instance);
        return val instanceof Number num ? num.doubleValue() : fallback;
    }

    // Get the horizontal axis
    private static @Nullable Direction.Axis getHorizontalAxis(BlockState state) {
        if (state == null) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (!"horizontal_axis".equals(property.getName())) {
                continue;
            }
            Object val = getPropertyValue(state, property);
            if (val instanceof Direction.Axis axis) {
                return axis;
            }
        }
        return null;
    }

    // Get the property value
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable Object getPropertyValue(BlockState state, Property<?> property) {
        return state.getValue((Property) property);
    }

    // Get the angles from world
    public static @Nullable double[] getAnglesFromWorld(Level level, BlockPos pos) {
        BlockEntity be = findBlockEntityIncludingSubLevels(level, pos);
        if (be == null) {
            return null;
        }
        return getAngles(be);
    }

    // Find the block entity including sub levels
    public static @Nullable BlockEntity findBlockEntityIncludingSubLevels(Level level, BlockPos pos) {
        return BlockEntityLookupApi.findIncludingSubLevels(level, pos);
    }

    // Resolve the block position including sub levels
    public static BlockEntityLookupApi.ResolvedBlockPosition resolveBlockPositionIncludingSubLevels(
            Level level, BlockPos pos) {
        return BlockEntityLookupApi.resolveIncludingSubLevels(level, pos);
    }

    // Find the block entity including sub levels
    public static <T extends BlockEntity> @Nullable T findBlockEntityIncludingSubLevels(Level level, BlockPos pos, Class<T> type) {
        return BlockEntityLookupApi.findIncludingSubLevels(level, pos, type);
    }

    // Find the block entity
    public static @Nullable BlockEntity findBlockEntity(Level level, @Nullable UUID subLevelId, BlockPos pos) {
        return BlockEntityLookupApi.find(level, subLevelId, pos);
    }

    // Find the block entity exact
    public static @Nullable BlockEntity findBlockEntityExact(Level level, @Nullable UUID subLevelId, BlockPos pos) {
        return BlockEntityLookupApi.findExact(level, subLevelId, pos);
    }

    // Find the loaded block entity exact
    public static @Nullable BlockEntity findLoadedBlockEntityExact(Level level, @Nullable UUID subLevelId,
                                                                    BlockPos pos) {
        return BlockEntityLookupApi.findLoadedExact(level, subLevelId, pos);
    }

    // Find the block entity
    public static <T extends BlockEntity> @Nullable T findBlockEntity(Level level, @Nullable UUID subLevelId, BlockPos pos, Class<T> type) {
        return BlockEntityLookupApi.find(level, subLevelId, pos, type);
    }

    // Find the block entity in sublevel
    public static <T extends BlockEntity> @Nullable T findBlockEntityInSubLevel(Object subLevel, BlockPos pos, Class<T> type) {
        return BlockEntityLookupApi.findInSubLevel(
                subLevel instanceof SubLevel sableSubLevel ? sableSubLevel : null, pos, type);
    }

    // Get the distance squared with sub levels
    public static double distanceSquaredWithSubLevels(Level level, Vec3 from, Vec3 to) {
        return SableTransformApi.distanceSquared(level, from, to);
    }

    // Convert the simulated to containing local direction
    public static Vec3 toContainingLocalDirection(BlockEntity blockEntity, Vec3 worldDirection) {
        return SableTransformApi.toLocalDirection(blockEntity, worldDirection);
    }

    // Convert the simulated to containing world direction
    public static Vec3 toContainingWorldDirection(BlockEntity blockEntity, Vec3 localDirection) {
        return SableTransformApi.toWorldDirection(blockEntity, localDirection);
    }

    // Convert the simulated to containing local direction
    public static Vec3 toContainingLocalDirection(Object subLevel, Vec3 worldDirection) {
        return SableTransformApi.toLocalDirection(subLevel instanceof SubLevel sableSubLevel ? sableSubLevel : null,
                worldDirection);
    }

    // Convert the simulated to containing world direction
    public static Vec3 toContainingWorldDirection(Object subLevel, Vec3 localDirection) {
        return SableTransformApi.toWorldDirection(subLevel instanceof SubLevel sableSubLevel ? sableSubLevel : null,
                localDirection);
    }

    // Convert the simulated to entity local movement
    public static Vec3 toEntityLocalMovement(Entity entity, Vec3 worldMovement) {
        init();
        if (entity == null || worldMovement == null || worldMovement.lengthSqr() < 1.0E-6D) {
            return worldMovement;
        }
        Object subLevel = getEntityTrackingSubLevel(entity);
        if (subLevel == null) {
            subLevel = getContainingSubLevel(entity.level(), entity.position());
        }
        if (subLevel == null) {
            return worldMovement;
        }
        double length = worldMovement.length();
        Vec3 localDirection = toContainingLocalDirection(subLevel, worldMovement);
        return localDirection.lengthSqr() < 1.0E-6D ? worldMovement : localDirection.normalize().scale(length);
    }

    // Convert the simulated to containing world position
    public static Vec3 toContainingWorldPosition(BlockEntity blockEntity, Vec3 localPosition) {
        return SableTransformApi.toWorldPosition(blockEntity, localPosition);
    }

    // Convert the simulated to global world position
    public static Vec3 toGlobalWorldPosition(BlockEntity blockEntity, Vec3 localPosition) {
        if (blockEntity == null || localPosition == null) {
            return localPosition;
        }

        Vec3 projected = toContainingWorldPosition(blockEntity, localPosition);
        return projectOutOfSubLevels(blockEntity.getLevel(), projected);
    }

    // Convert the simulated to render frame position
    public static Vec3 toRenderFramePosition(BlockEntity blockEntity, Vec3 localPosition, BlockEntity renderOrigin) {
        if (blockEntity == null || localPosition == null || renderOrigin == null) {
            return localPosition;
        }

        Vec3 global = toGlobalWorldPosition(blockEntity, localPosition);
        Object renderSubLevel = getContainingSubLevel(renderOrigin);
        if (renderSubLevel == null) {
            return global;
        }

        Vec3 local = toContainingLocalPosition(renderSubLevel, global);
        return local == null ? global : local;
    }

    // Convert the simulated to containing local position
    public static Vec3 toContainingLocalPosition(BlockEntity blockEntity, Vec3 worldPosition) {
        return SableTransformApi.toLocalPosition(blockEntity, worldPosition);
    }

    // Convert the simulated to block local hit position
    public static Vec3 toBlockLocalHitPosition(
            BlockEntity blockEntity, BlockHitResult hit
    ) {
        if (blockEntity == null || hit == null) {
            return hit == null ? Vec3.ZERO : hit.getLocation();
        }
        Vec3 location = hit.getLocation();
        BlockPos pos = blockEntity.getBlockPos();
        double epsilon = 1.0E-4D;
        boolean alreadyLocal = hit.getBlockPos().equals(pos)
                && location.x >= pos.getX() - epsilon
                && location.x <= pos.getX() + 1.0D + epsilon
                && location.y >= pos.getY() - epsilon
                && location.y <= pos.getY() + 1.0D + epsilon
                && location.z >= pos.getZ() - epsilon
                && location.z <= pos.getZ() + 1.0D + epsilon;
        return alreadyLocal ? location : toContainingLocalPosition(blockEntity, location);
    }

    // Get the containing sublevel
    public static @Nullable Object getContainingSubLevel(Level level, Position pos) {
        return SableLevelApi.containing(level, pos);
    }

    // Get the containing sublevel
    public static @Nullable Object getContainingSubLevel(BlockEntity blockEntity) {
        return SableLevelApi.containing(blockEntity);
    }

    // Get the entity tracking sublevel
    public static @Nullable Object getEntityTrackingSubLevel(Entity entity) {
        return SableLevelApi.tracking(entity);
    }

    // Kick the entity to the sublevel
    public static void kickEntityToSubLevel(Object subLevel, Entity entity) {
        if (subLevel instanceof SubLevel sableSubLevel) {
            SableTransformApi.kick(sableSubLevel, entity);
        }
    }

    // Get the intersecting sub levels
    public static List<Object> getIntersectingSubLevels(Level level, AABB bounds) {
        return SableTransformApi.intersecting(level, bounds).stream()
                .map(subLevel -> (Object) subLevel)
                .toList();
    }

    // Convert the simulated to containing world position
    public static @Nullable Vec3 toContainingWorldPosition(Object subLevel, Vec3 localPosition) {
        init();
        if (subLevel == null || localPosition == null) {
            return null;
        }
        Vec3 transformed = tryToContainingWorldPosition(subLevel, localPosition);
        return transformed == null ? localPosition : transformed;
    }

    // Try to containing world position
    public static @Nullable Vec3 tryToContainingWorldPosition(Object subLevel, Vec3 localPosition) {
        if (!(subLevel instanceof SubLevel sableSubLevel) || localPosition == null) {
            return null;
        }
        return SableTransformApi.toWorldPosition(sableSubLevel, localPosition);
    }

    // Convert the simulated to containing local position
    public static @Nullable Vec3 toContainingLocalPosition(Object subLevel, Vec3 worldPosition) {
        init();
        if (subLevel == null || worldPosition == null) {
            return null;
        }
        Vec3 transformed = tryToContainingLocalPosition(subLevel, worldPosition);
        return transformed == null ? worldPosition : transformed;
    }

    // Try to containing local position
    public static @Nullable Vec3 tryToContainingLocalPosition(Object subLevel, Vec3 worldPosition) {
        if (!(subLevel instanceof SubLevel sableSubLevel) || worldPosition == null) {
            return null;
        }
        return SableTransformApi.toLocalPosition(sableSubLevel, worldPosition);
    }

    // Get the sublevel id
    public static @Nullable UUID getSubLevelId(@Nullable Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel ? sableSubLevel.getUniqueId() : null;
    }

    // Get the project out of sublevel
    public static Vec3 projectOutOfSubLevel(Level level, Vec3 pos) {
        return SableTransformApi.projectOutOne(level, pos);
    }

    // Get the project out of sub levels
    public static Vec3 projectOutOfSubLevels(Level level, Vec3 pos) {
        return SableTransformApi.projectOut(level, pos);
    }

    // Get the containing sublevel id
    public static @Nullable UUID getContainingSubLevelId(BlockEntity blockEntity) {
        return SableLevelApi.containingId(blockEntity);
    }
}
