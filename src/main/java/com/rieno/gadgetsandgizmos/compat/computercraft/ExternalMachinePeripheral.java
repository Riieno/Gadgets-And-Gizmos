package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Give supported external machines one guarded ComputerCraft control surface
public class ExternalMachinePeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final BlockEntity blockEntity;
    // External machine peripheral type
    private final String type;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the external machine peripheral
    public ExternalMachinePeripheral(BlockEntity blockEntity, String type) {
        this.blockEntity = blockEntity;
        this.type = type;
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

    // Compare this external machine peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ExternalMachinePeripheral peripheral
                && peripheral.blockEntity == blockEntity
                && peripheral.type.equals(type);
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
    @LuaFunction(mainThread = true)
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
        Object val = invokeNoArgs("getSignal", "getOutputSignal", "getPower");
        if (val instanceof Number num) {
            return clampSignal(num.intValue());
        }
        Object field = readField("signalStrength", "signal", "outputSignal", "currentPower", "bestPower");
        if (field instanceof Number num) {
            return clampSignal(num.intValue());
        }
        return 0;
    }

    // Set the signal
    @LuaFunction(mainThread = true)
    public final void setSignal(int signal) throws LuaException {
        if (signal < 0 || signal > 15) {
            throw new LuaException("signal must be between 0 and 15");
        }
        boolean applied = invokeOneArg("setSignal", int.class, signal)
                || invokeOneArg("setSignal", Integer.class, signal)
                || invokeOneArg("setSignalStrength", int.class, signal)
                || invokeOneArg("setSignalStrength", Integer.class, signal)
                || invokeOneArg("setOutputSignal", int.class, signal)
                || invokeOneArg("setOutputSignal", Integer.class, signal)
                || writeField("signalStrength", signal)
                || writeField("signal", signal)
                || writeField("outputSignal", signal)
                || writeField("currentPower", signal)
                || writeField("bestPower", signal);
        if (!applied) {
            throw new LuaException("this block does not expose a writable signal interface");
        }
        markDirty();
    }

    // Check if this is powered
    @LuaFunction
    public final boolean isPowered() {
        Object val = invokeNoArgs("isPowered", "hasPower", "isActive", "magnetActive");
        if (val instanceof Boolean b) {
            return b;
        }
        Object field = readField("powered");
        if (field instanceof Boolean b) {
            return b;
        }
        if (blockEntity.getBlockState().hasProperty(BlockStateProperties.POWERED)) {
            return blockEntity.getBlockState().getValue(BlockStateProperties.POWERED);
        }
        return getSignal() > 0;
    }

    // Get the range
    @LuaFunction
    public final double getRange() {
        Object val = invokeNoArgs("getLaserRange", "getRange");
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return 0.0;
    }

    // Set the range
    @LuaFunction(mainThread = true)
    public final void setRange(int range) throws LuaException {
        if (range < 1) {
            throw new LuaException("range must be >= 1");
        }
        boolean applied = invokeOneArg("setRange", int.class, range)
                || invokeOneArg("setRange", Integer.class, range);
        if (!applied) {
            throw new LuaException("this block does not support setRange");
        }
        markDirty();
    }

    // Check if this has hit
    @LuaFunction
    public final boolean hasHit() {
        Object val = invokeNoArgs("hasHit");
        if (val instanceof Boolean b) {
            return b;
        }
        Object distance = invokeNoArgs("getHitBlockDistance");
        if (distance instanceof Number n) {
            return n.doubleValue() > 0.0;
        }
        return false;
    }

    // Get the distance
    @LuaFunction
    public final double getDistance() {
        Object val = invokeNoArgs("getHitBlockDistance", "getRayDistance");
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        Object field = readField("closestHitDistance", "blockedLength");
        if (field instanceof Number num) {
            return num.doubleValue();
        }
        return -1.0;
    }

    // Get the color
    @LuaFunction
    public final int getColor() {
        Object val = invokeNoArgs("getLaserColor");
        if (val instanceof Number num) {
            return num.intValue();
        }
        Object field = readField("laserColor");
        if (field instanceof Number num) {
            return num.intValue();
        }
        return 0;
    }

    // Set the color
    @LuaFunction(mainThread = true)
    public final void setColor(int color) throws LuaException {
        if (color < 0 || color > 0xFFFFFF) {
            throw new LuaException("color must be in the range 0x000000 to 0xFFFFFF");
        }
        boolean applied = invokeOneArg("setLaserColor", int.class, color)
                || invokeOneArg("setLaserColor", Integer.class, color)
                || writeField("laserColor", color);
        if (!applied) {
            throw new LuaException("this block does not support setColor");
        }
        markDirty();
    }

    // Check if this is rainbow
    @LuaFunction
    public final boolean isRainbow() {
        Object val = invokeNoArgs("isRainbow");
        if (val instanceof Boolean b) {
            return b;
        }
        Object field = readField("rainbow");
        return field instanceof Boolean b && b;
    }

    // Set the rainbow
    @LuaFunction(mainThread = true)
    public final void setRainbow(boolean enabled) throws LuaException {
        boolean applied = invokeOneArg("setRainbow", boolean.class, enabled)
                || invokeOneArg("setRainbow", Boolean.class, enabled)
                || writeField("rainbow", enabled);
        if (!applied) {
            throw new LuaException("this block does not support setRainbow");
        }
        markDirty();
    }

    // Get the air pressure
    @LuaFunction
    public final double getAirPressure() {
        Object val = invokeNoArgs("getAirPressure");
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return -1.0;
    }

    // Get the world height
    @LuaFunction
    public final double getWorldHeight() {
        if (SimulatedHelper.getContainingSubLevelId(blockEntity) != null) {
            return ((Number) getPosition().get("y")).doubleValue();
        }
        Object val = invokeNoArgs("getWorldHeight", "getHeight");
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return ((Number) getPosition().get("y")).doubleValue();
    }

    // Get the gas output
    @LuaFunction
    public final double getGasOutput() {
        Object val = invokeNoArgs("getGasOutput");
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        return 0.0;
    }

    // Get the state
    @LuaFunction
    public final String getState() {
        Object val = invokeNoArgs("getState");
        if (val instanceof Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
        if (val != null) {
            return String.valueOf(val);
        }
        Object field = readField("currentState", "state");
        if (field instanceof Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
        if (field != null) {
            return String.valueOf(field);
        }
        return "unknown";
    }

    // Check if this is blocked
    @LuaFunction
    public final boolean isBlocked() {
        Object val = invokeNoArgs("isBlocked");
        if (val instanceof Boolean b) {
            return b;
        }
        Object field = readField("blocked");
        return field instanceof Boolean b && b;
    }

    // Get the blocked length
    @LuaFunction
    public final double getBlockedLength() {
        Object val = invokeNoArgs("getBlockedLength");
        if (val instanceof Number num) {
            return num.doubleValue();
        }
        Object field = readField("blockedLength");
        if (field instanceof Number num) {
            return num.doubleValue();
        }
        return 0.0;
    }

    // Get the facing
    @LuaFunction
    public final String getFacing() {
        Direction dir = getLocalFacing();
        return dir == null ? "unknown" : dir.getName();
    }

    // Get the world facing
    @LuaFunction
    public final Map<String, Object> getWorldFacing() {
        Direction dir = getLocalFacing();
        return dir == null ? Map.of() : ComputerCraftPositionHelper.worldDirection(blockEntity, dir);
    }

    // Get the position
    @LuaFunction
    public final Map<String, Object> getPosition() {
        return ComputerCraftPositionHelper.blockPosition(blockEntity);
    }

    // Get the class name
    @LuaFunction
    public final String getClassName() {
        return blockEntity.getClass().getName();
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", getType());
        out.put("name", getName());
        out.put("signal", getSignal());
        out.put("powered", isPowered());
        out.put("range", getRange());
        out.put("hasHit", hasHit());
        out.put("distance", getDistance());
        out.put("worldHeight", getWorldHeight());
        out.put("airPressure", getAirPressure());
        out.put("gasOutput", getGasOutput());
        out.put("state", getState());
        out.put("facing", getFacing());
        out.put("worldFacing", getWorldFacing());
        out.put("blocked", isBlocked());
        out.put("blockedLength", getBlockedLength());
        out.put("color", getColor());
        out.put("rainbow", isRainbow());
        out.put("position", getPosition());
        out.put("className", getClassName());
        return out;
    }

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getName(): string",
                "setName(name:string)",
                "getSignal(): number",
                "setSignal(signal:number)",
                "isPowered(): boolean",
                "getRange(): number",
                "setRange(range:number)",
                "hasHit(): boolean",
                "getDistance(): number",
                "getColor(): number",
                "setColor(color:number)",
                "isRainbow(): boolean",
                "setRainbow(enabled:boolean)",
                "getAirPressure(): number",
                "getWorldHeight(): number",
                "getGasOutput(): number",
                "getState(): string",
                "isBlocked(): boolean",
                "getBlockedLength(): number",
                "getFacing(): string",
                "getWorldFacing(): table",
                "getPosition(): table",
                "getClassName(): string",
                "getStatus(): table",
                "help(method?: string): string|table"
        );
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) {
        Map<String, String> docs = new LinkedHashMap<>();
        docs.put("getSignal", "Returns current signal-like value (0-15) when available.");
        docs.put("setSignal", "Sets signal-like value (0-15) when the target block entity supports it.");
        docs.put("getRange", "Returns current laser/sensor range for blocks that expose a range behaviour.");
        docs.put("setRange", "Sets range for compatible sensors/pointers.");
        docs.put("getColor", "Returns laser color as an integer RGB value.");
        docs.put("setColor", "Sets laser color as RGB integer (0x000000 to 0xFFFFFF). ");
        docs.put("isRainbow", "Returns true when rainbow laser mode is enabled.");
        docs.put("setRainbow", "Enables/disables rainbow laser mode where supported.");
        docs.put("getAirPressure", "Returns atmosphere pressure value for altitude-capable blocks.");
        docs.put("getWorldHeight", "Returns projected world height for altitude-capable blocks.");
        docs.put("getGasOutput", "Returns current lifting-gas output for burner/vent blocks.");
        docs.put("getState", "Returns the block entity state enum/string when exposed.");
        docs.put("isBlocked", "Returns blocked state for mounted potato cannon-style blocks.");
        docs.put("getFacing", "Returns the block's local cardinal facing.");
        docs.put("getWorldFacing", "Returns the facing as a projected world-space vector.");
        docs.put("getPosition", "Returns projected world x/y/z with local coordinates and sub-level identity.");
        docs.put("getStatus", "Returns a full machine status table.");

        if (method.isEmpty()) {
            return docs;
        }

        String key = method.get().trim();
        if (key.isEmpty()) {
            return docs;
        }

        String entry = docs.get(key);
        return entry == null ? "No help found for method: " + key : entry;
    }

    // Invoke a method without arguments
    private Object invokeNoArgs(String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = blockEntity.getClass().getMethod(methodName);
                return method.invoke(blockEntity);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
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
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

    // Read the field
    private Object readField(String... fieldNames) {
        for (String fieldName : fieldNames) {
            try {
                Field field = blockEntity.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(blockEntity);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        }
        return null;
    }

    // Write the field
    private boolean writeField(String fieldName, Object val) {
        try {
            Field field = blockEntity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(blockEntity, val);
            return true;
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return false;
        }
    }

    // Clamp the signal
    private static int clampSignal(int val) {
        if (val < 0) {
            return 0;
        }
        return Math.min(val, 15);
    }

    // Get the local facing
    private Direction getLocalFacing() {
        Object val = invokeNoArgs("getDirection");
        if (val instanceof Direction dir) {
            return dir;
        }
        if (blockEntity.getBlockState().hasProperty(BlockStateProperties.FACING)) {
            return blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
        }
        return null;
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
