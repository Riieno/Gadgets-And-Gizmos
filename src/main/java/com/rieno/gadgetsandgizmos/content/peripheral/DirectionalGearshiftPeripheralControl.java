package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptBlockPeripheral;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityPosition;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
@PeripheralTypeDoc("directional_gearshift")
public class DirectionalGearshiftPeripheralControl extends ScriptBlockPeripheral<BlockEntity> {
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

    // Initialize the directional gearshift peripheral
    public DirectionalGearshiftPeripheralControl(BlockEntity blockEntity) {
        super(blockEntity, "directional_gearshift");
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

    // Check if the left is powered
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isLeftPowered", signature = "isLeftPowered(): boolean",
            description = "Returns whether the left is powered.")
    public final boolean isLeftPowered() {
        BlockState state = currentState();
        return state.hasProperty(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED)
            && state.getValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.LEFT_POWERED);
    }

    // Check if the right is powered
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isRightPowered", signature = "isRightPowered(): boolean",
            description = "Returns whether the right is powered.")
    public final boolean isRightPowered() {
        BlockState state = currentState();
        return state.hasProperty(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED)
            && state.getValue(dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock.RIGHT_POWERED);
    }

    // Set the left
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setLeft", signature = "setLeft(powered: boolean)",
            description = "Sets the left.")
    public final void setLeft(boolean powered) {
        applyOutputs(powered, isRightPowered());
    }

    // Set the right
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setRight", signature = "setRight(powered: boolean)",
            description = "Sets the right.")
    public final void setRight(boolean powered) {
        applyOutputs(isLeftPowered(), powered);
    }

    // Set the outputs
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setOutputs", signature = "setOutputs(leftPowered: boolean, rightPowered: boolean)",
            description = "Sets the outputs.")
    public final void setOutputs(boolean leftPowered, boolean rightPowered) {
        applyOutputs(leftPowered, rightPowered);
    }

    // Clear the directional gearshift peripheral
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clear", signature = "clear()",
            description = "Clears the directional gearshift peripheral.")
    public final void clear() {
        applyOutputs(false, false);
    }

    // Get the rotation modifier
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRotationModifier", signature = "getRotationModifier(face: string): number",
            description = "Returns the rotation modifier.")
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
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
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
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the facing.")
    public final String getFacing() {
        Direction dir = getLocalFacing();
        return dir == null ? "unknown" : dir.getName();
    }

    // Get the world facing
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the world facing.")
    public final Map<String, Object> getWorldFacing() {
        Direction dir = getLocalFacing();
        return dir == null ? Map.of() : BlockEntityPosition.worldDirection(blockEntity, dir);
    }

    // Get the position
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getPosition", signature = "getPosition(): table",
            description = "Returns the position.")
    public final Map<String, Object> getPosition() {
        return BlockEntityPosition.blockPosition(blockEntity);
    }

    // Get the class name
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getClassName", signature = "getClassName(): string",
            description = "Returns the class name.")
    public final String getClassName() {
        return blockEntity.getClass().getName();
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
                KineticBlockEntity kineticBlockEntity = blockEntity instanceof KineticBlockEntity kinetic
                        ? kinetic
                        : null;
                if (kineticBlockEntity != null) {
                    kineticBlockEntity.detachKinetics();
                }
                level.setBlock(blockEntity.getBlockPos(), state, 2);
                if (kineticBlockEntity != null && !kineticBlockEntity.isRemoved()) {
                    kineticBlockEntity.attachKinetics();
                    kineticBlockEntity.setChanged();
                    kineticBlockEntity.sendData();
                }
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
