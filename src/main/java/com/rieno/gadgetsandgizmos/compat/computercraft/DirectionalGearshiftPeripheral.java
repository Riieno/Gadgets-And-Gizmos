package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.mixin.DirectionalGearshiftBlockInvoker;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

// Expose Directional Gearshift controls and telemetry to ComputerCraft
public class DirectionalGearshiftPeripheral implements IPeripheral {
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

    // Initialize the directional gearshift peripheral
    public DirectionalGearshiftPeripheral(BlockEntity blockEntity) {
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
        return "directional_gearshift";
    }

    // Compare this directional gearshift peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof DirectionalGearshiftPeripheral peripheral
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

    // Check if the left is powered
    @LuaFunction
    public final boolean isLeftPowered() {
        BlockState state = currentState();
        return state.hasProperty(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED)
            && state.getValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED);
    }

    // Check if the right is powered
    @LuaFunction
    public final boolean isRightPowered() {
        BlockState state = currentState();
        return state.hasProperty(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED)
            && state.getValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED);
    }

    // Set the left
    @LuaFunction(mainThread = true)
    public final void setLeft(boolean powered) {
        applyOutputs(powered, isRightPowered());
    }

    // Set the right
    @LuaFunction(mainThread = true)
    public final void setRight(boolean powered) {
        applyOutputs(isLeftPowered(), powered);
    }

    // Set the outputs
    @LuaFunction(mainThread = true)
    public final void setOutputs(boolean leftPowered, boolean rightPowered) {
        applyOutputs(leftPowered, rightPowered);
    }

    // Clear the directional gearshift peripheral
    @LuaFunction(mainThread = true)
    public final void clear() {
        applyOutputs(false, false);
    }

    // Get the rotation modifier
    @LuaFunction
    public final int getRotationModifier(String face) {
        Direction dir = parseDirection(face);
        if (dir == null) {
            return 0;
        }
        Object val = invokeOneArgResult("getRotationSpeedModifier", Direction.class, dir);
        if (val instanceof Number num) {
            return Math.round(num.floatValue());
        }
        return 0;
    }

    // Get the status
    @LuaFunction
    public final Map<String, Object> getStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("leftPowered", isLeftPowered());
        out.put("rightPowered", isRightPowered());
        out.put("sourceFacing", getFacing());
        out.put("worldFacing", getWorldFacing());
        out.put("blockPos", getPosition());
        out.put("className", getClassName());
        return out;
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

    // Get the help
    @LuaFunction
    public final String help() {
        return "Directional gearshift API: setLeft(boolean), setRight(boolean), setOutputs(boolean, boolean), clear(), isLeftPowered(), isRightPowered(), getRotationModifier(face), getFacing(), getWorldFacing(), getPosition(), getStatus()";
    }

    // Apply the outputs
    private void applyOutputs(boolean leftPowered, boolean rightPowered) {
        BlockState state = currentState();
        boolean changed = false;
        if (state.hasProperty(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED)
                && state.getValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED) != leftPowered) {
            state = state.setValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED, leftPowered);
            changed = true;
        }
        if (state.hasProperty(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED)
                && state.getValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED) != rightPowered) {
            state = state.setValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED, rightPowered);
            changed = true;
        }
        if (changed) {
            blockEntity.setChanged();
            Level level = blockEntity.getLevel();
            if (level != null && !level.isClientSide) {
                if (state.getBlock() instanceof DirectionalGearshiftBlockInvoker invoker) {
                    invoker.ct$detachKinetics(level, blockEntity.getBlockPos(), true);
                }
                level.setBlock(blockEntity.getBlockPos(), state, 2);
            }
        }
    }

    // Get the current state
    private BlockState currentState() {
        if (blockEntity.getLevel() == null) {
            return blockEntity.getBlockState();
        }
        return blockEntity.getLevel().getBlockState(blockEntity.getBlockPos());
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

    // Run the one arg result
    private Object invokeOneArgResult(String methodName, Class<?> parameterType, Object arg) {
        try {
            Method method = blockEntity.getClass().getMethod(methodName, parameterType);
            return method.invoke(blockEntity, arg);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Parse the direction
    private static Direction parseDirection(String face) {
        if (face == null || face.isBlank()) {
            return null;
        }
        try {
            return Direction.valueOf(face.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Direction.byName(face.trim());
        }
    }

    // Get the local facing
    private Direction getLocalFacing() {
        Object val = invokeNoArgs("getDirection");
        if (val instanceof Direction dir) {
            return dir;
        }
        BlockState state = blockEntity.getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return null;
    }

    // Mark the dirty
    private void markDirty() {
        blockEntity.setChanged();
        if (blockEntity.getLevel() != null) {
            blockEntity.getLevel().sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }
}
