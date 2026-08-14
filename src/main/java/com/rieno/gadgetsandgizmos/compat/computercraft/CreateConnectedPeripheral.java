package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerSignalBus;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// Wrap Create connected peripherals while preserving their normal identity and equality rules
public final class CreateConnectedPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final BlockEntity blockEntity;
    // Create connected peripheral type
    private final String type;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create connected peripheral
    public CreateConnectedPeripheral(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        String path = blockId == null ? "machine" : blockId.getPath();
        this.type = "create_connected_" + path;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the type
    @Override
    public String getType() {
        return type;
    }

    // Compare this create connected peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof CreateConnectedPeripheral peripheral && peripheral.blockEntity == blockEntity;
    }

    // Get the block id
    @LuaFunction
    public final String getBlockId() {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        return id == null ? "create_connected:unknown" : id.toString();
    }

    // Get the block entity id
    @LuaFunction
    public final String getBlockEntityId() {
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id == null ? "create_connected:unknown" : id.toString();
    }

    // Get the speed
    @LuaFunction
    public final double getSpeed() {
        return blockEntity instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0.0D;
    }

    // Get the theoretical speed
    @LuaFunction
    public final double getTheoreticalSpeed() {
        return blockEntity instanceof KineticBlockEntity kinetic ? kinetic.getTheoreticalSpeed() : 0.0D;
    }

    // Get the generated speed
    @LuaFunction
    public final double getGeneratedSpeed() {
        return blockEntity instanceof KineticBlockEntity kinetic ? kinetic.getGeneratedSpeed() : 0.0D;
    }

    // Check if this has network
    @LuaFunction
    public final boolean hasNetwork() {
        return blockEntity instanceof KineticBlockEntity kinetic && kinetic.hasNetwork();
    }

    // Check if this is overstressed
    @LuaFunction
    public final boolean isOverstressed() {
        return blockEntity instanceof KineticBlockEntity kinetic && kinetic.isOverStressed();
    }

    // Get the stress
    @LuaFunction
    public final double getStress() {
        return blockEntity instanceof KineticBlockEntity kinetic ? numericField(kinetic, "stress") : 0.0D;
    }

    // Get the capacity
    @LuaFunction
    public final double getCapacity() {
        return blockEntity instanceof KineticBlockEntity kinetic ? numericField(kinetic, "capacity") : 0.0D;
    }

    // Get the network id
    @LuaFunction
    public final long getNetworkId() {
        if (blockEntity instanceof KineticBlockEntity kinetic && kinetic.network != null) {
            return kinetic.network;
        }
        return -1L;
    }

    // Get the source
    @LuaFunction
    public final Map<String, Object> getSource() {
        if (blockEntity instanceof KineticBlockEntity kinetic && kinetic.source != null) {
            return ComputerCraftPositionHelper.blockPosition(blockEntity, kinetic.source);
        }
        return Map.of();
    }

    // Get the output speed
    @LuaFunction
    public final double getOutputSpeed(String face) throws LuaException {
        Direction dir = parseDirection(face);
        if (!(blockEntity instanceof KineticBlockEntity kinetic)) {
            return 0.0D;
        }
        Object modifier = invokeOneArgForResult(kinetic, "getRotationSpeedModifier", Direction.class, dir);
        return kinetic.getSpeed() * (modifier instanceof Number num ? num.doubleValue() : 1.0D);
    }

    // Check if this is powered
    @LuaFunction
    public final boolean isPowered() {
        if (blockEntity.getBlockState().hasProperty(BlockStateProperties.POWERED)) {
            return blockEntity.getBlockState().getValue(BlockStateProperties.POWERED);
        }
        Object val = readField(blockEntity, "powered", "isBraking");
        return val instanceof Boolean bool && bool;
    }

    // Set the powered
    @LuaFunction(mainThread = true)
    public final void setPowered(boolean powered) {
        setInjectedSignal(powered ? 15 : 0);
    }

    // Set the signal
    @LuaFunction(mainThread = true)
    public final void setSignal(int signal) throws LuaException {
        if (signal < 0 || signal > 15) {
            throw new LuaException("signal must be between 0 and 15");
        }
        if (!invokeOneArg(blockEntity, "setSignal", int.class, signal)) {
            setInjectedSignal(signal);
        }
        markDirty();
    }

    // Get the signal
    @LuaFunction
    public final int getSignal() {
        Object val = invokeNoArgs(blockEntity, "getSignal");
        if (val instanceof Number num) {
            return Math.max(0, Math.min(15, num.intValue()));
        }
        return isPowered() ? 15 : 0;
    }

    // Get the battery level
    @LuaFunction
    public final double getBatteryLevel() {
        Object val = invokeNoArgs(blockEntity, "getBatteryLevel");
        return val instanceof Number num ? num.doubleValue() : 0.0D;
    }

    // Set the battery level
    @LuaFunction(mainThread = true)
    public final void setBatteryLevel(double level) throws LuaException {
        if (!invokeOneArg(blockEntity, "setBatteryLevel", double.class, level)) {
            throw new LuaException("this block is not a kinetic battery");
        }
        markDirty();
    }

    // Get the list controls
    @LuaFunction
    public final Map<String, Object> listControls() {
        Map<String, Object> controls = new LinkedHashMap<>();
        for (Class<?> current = blockEntity.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            if (!current.getName().startsWith("com.hlysine.create_connected.")) {
                continue;
            }
            for (Field field : current.getDeclaredFields()) {
                Object val = readField(blockEntity, field.getName());
                Object exposed = exposeControlValue(val);
                if (exposed != null) {
                    controls.putIfAbsent(field.getName(), exposed);
                }
            }
        }
        return controls;
    }

    // Get the control
    @LuaFunction
    public final Object getControl(String name) throws LuaException {
        String normalized = normalizeControlName(name);
        Field field = findConnectedField(blockEntity.getClass(), normalized);
        Object exposed = field == null ? null : exposeControlValue(readField(blockEntity, normalized));
        if (exposed == null) {
            throw new LuaException("unknown or unsupported control: " + name);
        }
        return exposed;
    }

    // Set the control
    @LuaFunction(mainThread = true)
    public final void setControl(String name, int value) throws LuaException {
        String normalized = normalizeControlName(name);
        Field field = findConnectedField(blockEntity.getClass(), normalized);
        if (field == null) {
            throw new LuaException("unknown or unsupported control: " + name);
        }
        Object control = readField(blockEntity, normalized);
        if (control instanceof ScrollValueBehaviour scrollValue) {
            scrollValue.setValue(value);
            refreshKinetics();
            return;
        }
        throw new LuaException("control is read-only: " + name);
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("type", type);
        status.put("blockId", getBlockId());
        status.put("blockEntityId", getBlockEntityId());
        status.put("speed", getSpeed());
        status.put("theoreticalSpeed", getTheoreticalSpeed());
        status.put("generatedSpeed", getGeneratedSpeed());
        status.put("networkId", getNetworkId());
        status.put("hasNetwork", hasNetwork());
        status.put("overstressed", isOverstressed());
        status.put("stress", getStress());
        status.put("capacity", getCapacity());
        status.put("powered", isPowered());
        status.put("signal", getSignal());
        status.put("batteryLevel", getBatteryLevel());
        status.put("source", getSource());
        status.put("controls", listControls());
        return status;
    }

    // Set the injected signal
    private void setInjectedSignal(int signal) {
        String sourceId = "cc:create_connected:" + blockEntity.getBlockPos().asLong();
        for (Direction dir : Direction.values()) {
            ContraptionNetworkLinkerSignalBus.setSignal(
                    blockEntity.getLevel(),
                    blockEntity.getBlockPos(),
                    dir,
                    sourceId + ":" + dir.getSerializedName(),
                    signal);
        }
        markDirty();
    }

    // Refresh the kinetics
    private void refreshKinetics() {
        if (blockEntity instanceof KineticBlockEntity kinetic && blockEntity.getLevel() != null
                && !blockEntity.getLevel().isClientSide) {
            kinetic.detachKinetics();
            kinetic.attachKinetics();
        }
        markDirty();
    }

    // Mark the dirty
    private void markDirty() {
        blockEntity.setChanged();
        if (blockEntity instanceof SmartBlockEntity smart) {
            smart.sendData();
        } else if (blockEntity.getLevel() != null) {
            blockEntity.getLevel().sendBlockUpdated(
                    blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }

    // Parse the direction
    private static Direction parseDirection(String val) throws LuaException {
        if (val != null) {
            for (Direction dir : Direction.values()) {
                if (dir.getSerializedName().equalsIgnoreCase(val.trim())) {
                    return dir;
                }
            }
        }
        throw new LuaException("face must be one of down, up, north, south, west, east");
    }

    // Normalize the control name
    private static String normalizeControlName(String name) {
        return name == null ? "" : name.trim();
    }

    // Get the expose control value
    private static Object exposeControlValue(Object val) {
        if (val instanceof ScrollValueBehaviour scrollValue) {
            return scrollValue.getValue();
        }
        if (val instanceof Number || val instanceof Boolean || val instanceof String) {
            return val;
        }
        if (val instanceof Enum<?> enumValue) {
            return enumValue.name().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    // Get the numeric field
    private static double numericField(Object target, String name) {
        Object val = readField(target, name);
        return val instanceof Number num ? num.doubleValue() : 0.0D;
    }

    // Invoke a method without arguments
    private static Object invokeNoArgs(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Run the one arg
    private static boolean invokeOneArg(Object target, String methodName, Class<?> parameterType, Object val) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, val);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Run the one arg for result
    private static Object invokeOneArgForResult(Object target, String methodName, Class<?> parameterType, Object val) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, val);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Read the field
    private static Object readField(Object target, String... names) {
        for (String name : names) {
            Field field = findField(target.getClass(), name);
            if (field == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    // Find the field
    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    // Find the connected field
    private static Field findConnectedField(Class<?> type, String name) {
        Field field = findField(type, name);
        return field != null && field.getDeclaringClass().getName().startsWith("com.hlysine.create_connected.")
                ? field
                : null;
    }
}
