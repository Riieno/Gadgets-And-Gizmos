package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.Map;

// Expose bearing pose and attached-thruster control through a stable ComputerCraft API
@PeripheralTypeDoc("thruster_bearing")
public class ThrusterBearingPeripheral extends BearingThrusterPeripheral<ThrusterBearingBlockEntity> {
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

    // Initialize the thruster bearing peripheral
    public ThrusterBearingPeripheral(ThrusterBearingBlockEntity blockEntity) {
        super(blockEntity, "thruster_bearing");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the shared attached-thruster controls
    @Override
    protected AssemblyThrusterControl control(){
        return blockEntity.getBearingAssembly().controls();
    }

    // Get the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the forward signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getForwardSignal", signature = "getForwardSignal(): number",
            description = "Returns the forward signal.")
    public final int getForwardSignal() {
        return blockEntity.getForwardSignal();
    }

    // Get the backward signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBackwardSignal", signature = "getBackwardSignal(): number",
            description = "Returns the backward signal.")
    public final int getBackwardSignal() {
        return blockEntity.getBackwardSignal();
    }

    // Get the pivot angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getPivotAngle", signature = "getPivotAngle(): number",
            description = "Returns the pivot angle.")
    public final double getPivotAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the current angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
    public final double getCurrentAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the target angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getTargetAngle", signature = "getTargetAngle(): number",
            description = "Returns the target angle.")
    public final double getTargetAngle() {
        return blockEntity.getTargetAngleDegrees();
    }

    // Get the bearing control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBearingControlMode", signature = "getBearingControlMode(): string",
            description = "Returns the bearing control mode.")
    public final String getBearingControlMode() {
        return blockEntity.getControlMode().name().toLowerCase();
    }

    // Get the servo input angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getServoInputAngle", signature = "getServoInputAngle(): number",
            description = "Returns the servo input angle.")
    public final double getServoInputAngle() {
        return blockEntity.getServoInputAngleDegrees();
    }

    // Get the min angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMinAngle", signature = "getMinAngle(): number",
            description = "Returns the min angle.")
    public final double getMinAngle() {
        return blockEntity.getMinAngleDegrees();
    }

    // Get the max angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getMaxAngle", signature = "getMaxAngle(): number",
            description = "Returns the max angle.")
    public final double getMaxAngle() {
        return blockEntity.getMaxAngleDegrees();
    }

    // Set the pivot angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setPivotAngle", signature = "setPivotAngle(angleDeg: number)",
            description = "Sets the pivot angle.")
    public final void setPivotAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setPivotAngleDegrees(angleDeg);
    }

    // Set the bearing control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setBearingControlMode", signature = "setBearingControlMode(mode: 'auto'|'redstone'|'computer'|'servo')",
            description = "Sets the bearing control mode.")
    public final void setBearingControlMode(String mode) throws LuaException {
        if (mode == null) {
            throw new LuaException("mode must be 'auto', 'redstone', 'computer' or 'servo'");
        }
        String normalized = mode.trim().toLowerCase();
        ThrusterBearingBlockEntity.ControlMode controlMode = switch (normalized) {
            case "auto" -> ThrusterBearingBlockEntity.ControlMode.AUTO;
            case "redstone" -> ThrusterBearingBlockEntity.ControlMode.REDSTONE;
            case "computer" -> ThrusterBearingBlockEntity.ControlMode.COMPUTER;
            case "servo" -> ThrusterBearingBlockEntity.ControlMode.SERVO;
            default -> throw new LuaException("mode must be 'auto', 'redstone', 'computer' or 'servo'");
        };
        blockEntity.setControlMode(controlMode);
    }

    // Set the min angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMinAngle", signature = "setMinAngle(angleDeg: number)",
            description = "Sets the min angle.")
    public final void setMinAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setMinAngleDegrees(angleDeg);
    }

    // Set the max angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setMaxAngle", signature = "setMaxAngle(angleDeg: number)",
            description = "Sets the max angle.")
    public final void setMaxAngle(double angleDeg) throws LuaException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new LuaException("angleDeg must be a finite number");
        }
        blockEntity.setMaxAngleDegrees(angleDeg);
    }

    // Clear the pivot override
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearPivotOverride", signature = "clearPivotOverride()",
            description = "Clears the pivot override.")
    public final void clearPivotOverride() {
        blockEntity.clearPivotOverride();
    }

    // Get the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode(id|'all'): string|table",
            description = "Returns the control mode.")
    public final Object getControlMode(String thrusterId) throws LuaException{
        try{ return control().getControlMode(thrusterId); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Set the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(id|'all', mode: 'auto'|'redstone'|'computer')",
            description = "Sets the control mode.")
    public final void setControlMode(String thrusterId, String mode) throws LuaException{
        try{ control().setControlMode(thrusterId, mode); }
        catch(IllegalArgumentException ex){ throw new LuaException(ex.getMessage()); }
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("facing", getFacing());
        status.put("worldFacing", getWorldFacing());
        status.put("forwardSignal", blockEntity.getForwardSignal());
        status.put("backwardSignal", blockEntity.getBackwardSignal());
        status.put("controlMode", blockEntity.getControlMode().name().toLowerCase());
        status.put("servoInputAngle", blockEntity.getServoInputAngleDegrees());
        status.put("pivotAngle", blockEntity.getCurrentPivotAngleDegrees());
        status.put("currentAngle", blockEntity.getCurrentPivotAngleDegrees());
        status.put("targetAngle", blockEntity.getTargetAngleDegrees());
        status.put("minAngle", blockEntity.getMinAngleDegrees());
        status.put("maxAngle", blockEntity.getMaxAngleDegrees());
        status.put("totalRealThrust", blockEntity.getAssemblyRealThrust());
        status.put("totalLiftCapacity", blockEntity.getAssemblyLiftCapacity());
        status.put("thrusterCount", blockEntity.getAttachedThrustersById().size());
        status.put("thrusters", listThrusters());
        status.put("rcsThrusterCount", blockEntity.getAttachedRcsThrustersById().size());
        status.put("rcsThrusters", listRcsThrusters());
        return status;
    }

    // Get the facing
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the facing.")
    public final String getFacing() {
        return blockEntity.getFacing().name().toLowerCase();
    }

    // Get the world facing
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the world facing.")
    public final Map<String, Object> getWorldFacing() {
        return ComputerCraftPositionHelper.worldDirection(blockEntity, blockEntity.getFacing());
    }

    // Set the facing
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setFacing", signature = "setFacing(direction: string)",
            description = "Sets the facing.")
    public final void setFacing(String direction) throws LuaException {
        Direction dir;
        try {
            dir = Direction.valueOf(direction.trim().toUpperCase());
        } catch (Exception e) {
            throw new LuaException("direction must be one of north/south/east/west/up/down");
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        var state = blockEntity.getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            level.setBlock(blockEntity.getBlockPos(), state.setValue(BlockStateProperties.FACING, dir), 3);
        }
    }
}
