package com.rieno.gadgetsandgizmos.content.peripheral;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.AssemblyThrusterPeripheral;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFunctionBinding;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMethodDoc;
import com.rieno.gadgetsandgizmos.lib.physics.BlockEntityPosition;
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.LinkedHashMap;
import java.util.Map;

// Expose bearing pose and attached-thruster control through a stable ComputerCraft API
@PeripheralTypeDoc("thruster_bearing")
public class ThrusterBearingPeripheralControl extends AssemblyThrusterPeripheral<ThrusterBearingBlockEntity> {
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
    public ThrusterBearingPeripheralControl(ThrusterBearingBlockEntity blockEntity) {
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
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() {
        String name = blockEntity.getCustomName();
        return name != null ? name : "";
    }

    // Set the name
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) {
        blockEntity.setCustomName(name == null || name.isBlank() ? null : name.strip());
    }

    // Get the forward signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getForwardSignal", signature = "getForwardSignal(): number",
            description = "Returns the forward signal.")
    public final int getForwardSignal() {
        return blockEntity.getForwardSignal();
    }

    // Get the backward signal
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getBackwardSignal", signature = "getBackwardSignal(): number",
            description = "Returns the backward signal.")
    public final int getBackwardSignal() {
        return blockEntity.getBackwardSignal();
    }

    // Get the pivot angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getPivotAngle", signature = "getPivotAngle(): number",
            description = "Returns the pivot angle.")
    public final double getPivotAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the current angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getCurrentAngle", signature = "getCurrentAngle(): number",
            description = "Returns the current angle.")
    public final double getCurrentAngle() {
        return blockEntity.getCurrentPivotAngleDegrees();
    }

    // Get the target angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getTargetAngle", signature = "getTargetAngle(): number",
            description = "Returns the target angle.")
    public final double getTargetAngle() {
        return blockEntity.getTargetAngleDegrees();
    }

    // Get the bearing control mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getBearingControlMode", signature = "getBearingControlMode(): string",
            description = "Returns the bearing control mode.")
    public final String getBearingControlMode() {
        return blockEntity.getControlMode().name().toLowerCase();
    }

    // Get the servo input angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getServoInputAngle", signature = "getServoInputAngle(): number",
            description = "Returns the servo input angle.")
    public final double getServoInputAngle() {
        return blockEntity.getServoInputAngleDegrees();
    }

    // Get the min angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getMinAngle", signature = "getMinAngle(): number",
            description = "Returns the min angle.")
    public final double getMinAngle() {
        return blockEntity.getMinAngleDegrees();
    }

    // Get the max angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getMaxAngle", signature = "getMaxAngle(): number",
            description = "Returns the max angle.")
    public final double getMaxAngle() {
        return blockEntity.getMaxAngleDegrees();
    }

    // Set the pivot angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setPivotAngle", signature = "setPivotAngle(angleDeg: number)",
            description = "Sets the pivot angle.")
    public final void setPivotAngle(double angleDeg) throws ScriptException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new ScriptException("angleDeg must be a finite number");
        }
        blockEntity.setPivotAngleDegrees(angleDeg);
    }

    // Set the bearing control mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setBearingControlMode", signature = "setBearingControlMode(mode: 'auto'|'redstone'|'computer'|'servo')",
            description = "Sets the bearing control mode.")
    public final void setBearingControlMode(String mode) throws ScriptException {
        if (mode == null) {
            throw new ScriptException("mode must be 'auto', 'redstone', 'computer' or 'servo'");
        }
        String normalized = mode.trim().toLowerCase();
        ThrusterBearingBlockEntity.ControlMode controlMode = switch (normalized) {
            case "auto" -> ThrusterBearingBlockEntity.ControlMode.AUTO;
            case "redstone" -> ThrusterBearingBlockEntity.ControlMode.REDSTONE;
            case "computer" -> ThrusterBearingBlockEntity.ControlMode.COMPUTER;
            case "servo" -> ThrusterBearingBlockEntity.ControlMode.SERVO;
            default -> throw new ScriptException("mode must be 'auto', 'redstone', 'computer' or 'servo'");
        };
        blockEntity.setControlMode(controlMode);
    }

    // Set the min angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setMinAngle", signature = "setMinAngle(angleDeg: number)",
            description = "Sets the min angle.")
    public final void setMinAngle(double angleDeg) throws ScriptException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new ScriptException("angleDeg must be a finite number");
        }
        blockEntity.setMinAngleDegrees(angleDeg);
    }

    // Set the max angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setMaxAngle", signature = "setMaxAngle(angleDeg: number)",
            description = "Sets the max angle.")
    public final void setMaxAngle(double angleDeg) throws ScriptException {
        if (Double.isNaN(angleDeg) || Double.isInfinite(angleDeg)) {
            throw new ScriptException("angleDeg must be a finite number");
        }
        blockEntity.setMaxAngleDegrees(angleDeg);
    }

    // Clear the pivot override
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearPivotOverride", signature = "clearPivotOverride()",
            description = "Clears the pivot override.")
    public final void clearPivotOverride() {
        blockEntity.clearPivotOverride();
    }

    // Get the control mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getControlMode", signature = "getControlMode(id|'all'): string|table",
            description = "Returns the control mode.")
    public final Object getControlMode(String thrusterId) throws ScriptException{
        try{ return control().getControlMode(thrusterId); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Set the control mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setControlMode", signature = "setControlMode(id|'all', mode: 'auto'|'redstone'|'computer')",
            description = "Sets the control mode.")
    public final void setControlMode(String thrusterId, String mode) throws ScriptException{
        try{ control().setControlMode(thrusterId, mode); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
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
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getFacing", signature = "getFacing(): string",
            description = "Returns the facing.")
    public final String getFacing() {
        return blockEntity.getFacing().name().toLowerCase();
    }

    // Get the world facing
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getWorldFacing", signature = "getWorldFacing(): table",
            description = "Returns the world facing.")
    public final Map<String, Object> getWorldFacing() {
        return BlockEntityPosition.worldDirection(blockEntity, blockEntity.getFacing());
    }

    // Set the facing
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setFacing", signature = "setFacing(direction: string)",
            description = "Sets the facing.")
    public final void setFacing(String direction) throws ScriptException {
        Direction dir;
        try {
            dir = Direction.valueOf(direction.trim().toUpperCase());
        } catch (Exception e) {
            throw new ScriptException("direction must be one of north/south/east/west/up/down");
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
