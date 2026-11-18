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
import com.rieno.gadgetsandgizmos.lib.control.AssemblyThrusterControl;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Expose one aileron target and its live angle to ComputerCraft
@PeripheralTypeDoc("aileron_bearing")
public class AileronBearingPeripheralControl extends AssemblyThrusterPeripheral<AileronBearingBlockEntity> {
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

    // Initialize the aileron bearing peripheral
    public AileronBearingPeripheralControl(AileronBearingBlockEntity blockEntity) {
        super(blockEntity, "aileron_bearing");
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

    // Get the head mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getHeadMode", signature = "getHeadMode(): string",
            description = "Returns the head mode.")
    public final String getHeadMode() {
        return blockEntity.getHeadMode().serializedName();
    }

    // Set the head mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setHeadMode", signature = "setHeadMode(mode: 'single'|'mirrored'|'opposed'|'precise'|'free_single'|'free_mirrored'|'free_opposed')",
            description = "Sets the head mode.")
    public final void setHeadMode(String modeName) throws ScriptException {
        blockEntity.setHeadMode(parseHeadMode(modeName));
    }

    // Get the control mode
    public final String getControlMode() {
        return blockEntity.getControlMode().serializedName();
    }

    // Set the control mode
    public final void setControlMode(String modeName) throws ScriptException {
        blockEntity.setControlMode(parseControlMode(modeName));
    }

    // Read the bearing mode or one attached thruster mode
    @ScriptFunctionBinding(value = "getControlMode", mainThread = true)
    @ScriptMethodDoc(name = "getControlMode", signature = "getControlMode([id|'all']): string|table",
            description = "Returns the bearing mode without an ID, or attached thruster modes with an ID.")
    public final Object getLuaControlMode(Optional<String> id) throws ScriptException{
        if(id.isEmpty()) return getControlMode();
        try{ return control().getControlMode(id.get()); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Set the bearing mode or attached thruster modes
    @ScriptFunctionBinding(value = "setControlMode", mainThread = true)
    @ScriptMethodDoc(name = "setControlMode", signature = "setControlMode(mode) | setControlMode(id|'all', mode)",
            description = "Sets the bearing mode with one argument, or attached thruster modes with two arguments.")
    public final void setLuaControlMode(String idOrMode, Optional<String> mode) throws ScriptException{
        if(mode.isEmpty()){
            setControlMode(idOrMode);
            return;
        }
        try{ control().setControlMode(idOrMode, mode.get()); }
        catch(IllegalArgumentException ex){ throw new ScriptException(ex.getMessage()); }
    }

    // Get the active control mode
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getActiveControlMode", signature = "getActiveControlMode(): string",
            description = "Returns the active control mode.")
    public final String getActiveControlMode() {
        return blockEntity.getActiveControlMode().serializedName();
    }

    // Get the angles
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() {
        Map<String, Object> angles = new LinkedHashMap<>();
        addHeadAngles(angles, BearingHead.PRIMARY);
        addHeadAngles(angles, BearingHead.SECONDARY);
        return angles;
    }

    // Set the angles
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setAngles", signature = "setAngles(primaryDegrees: number, secondaryDegrees: number)",
            description = "Sets the angles.")
    public final void setAngles(double primaryDegrees, double secondaryDegrees) throws ScriptException {
        validateFinite(primaryDegrees, "primaryDegrees");
        validateFinite(secondaryDegrees, "secondaryDegrees");
        blockEntity.setHeadTargetAngle(BearingHead.PRIMARY, primaryDegrees);
        blockEntity.setHeadTargetAngle(BearingHead.SECONDARY, secondaryDegrees);
    }

    // Set the head angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setHeadAngle", signature = "setHeadAngle(head: string, angleDegrees: number)",
            description = "Sets the head angle.")
    public final void setHeadAngle(String headName, double angleDegrees) throws ScriptException {
        validateFinite(angleDegrees, "angleDegrees");
        blockEntity.setHeadTargetAngle(parseHead(headName), angleDegrees);
    }

    // Clear the head angle
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearHeadAngle", signature = "clearHeadAngle(head: string)",
            description = "Clears the head angle.")
    public final void clearHeadAngle(String headName) throws ScriptException {
        blockEntity.clearHeadTargetOverride(parseHead(headName));
    }

    // Clear the angles
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "clearAngles", signature = "clearAngles()",
            description = "Clears the angles.")
    public final void clearAngles() {
        blockEntity.clearHeadTargetOverride(BearingHead.PRIMARY);
        blockEntity.clearHeadTargetOverride(BearingHead.SECONDARY);
    }

    // Get the ranges
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getRanges", signature = "getRanges(): table",
            description = "Returns the ranges.")
    public final Map<String, Object> getRanges() {
        Map<String, Object> ranges = new LinkedHashMap<>();
        addHeadRange(ranges, BearingHead.PRIMARY);
        addHeadRange(ranges, BearingHead.SECONDARY);
        return ranges;
    }

    // Set the head range
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "setHeadRange", signature = "setHeadRange(head: string, minDegrees: number, maxDegrees: number)",
            description = "Sets the head range.")
    public final void setHeadRange(String headName, double minDegrees, double maxDegrees) throws ScriptException {
        validateFinite(minDegrees, "minDegrees");
        validateFinite(maxDegrees, "maxDegrees");
        blockEntity.setAngleRange(parseHead(headName), minDegrees, maxDegrees);
    }

    // Get the signals
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getSignals", signature = "getSignals(): table",
            description = "Returns the signals.")
    public final Map<String, Object> getSignals() {
        Map<String, Object> signals = new LinkedHashMap<>();
        for (BearingHead head : BearingHead.values()) {
            Map<String, Object> headSignals = new LinkedHashMap<>();
            headSignals.put("cw", blockEntity.getSignal(head, AileronBearingBlockEntity.ControlDirection.CW));
            headSignals.put("ccw", blockEntity.getSignal(head, AileronBearingBlockEntity.ControlDirection.CCW));
            signals.put(head.serializedName(), headSignals);
        }
        return signals;
    }

    // Assemble the aileron bearing peripheral
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "assemble", signature = "assemble(head: string): boolean",
            description = "Assemble the aileron bearing peripheral.")
    public final boolean assemble(String headName) throws ScriptException {
        return blockEntity.tryAssembleMountedBlock(parseHead(headName));
    }

    // Disassemble the aileron bearing peripheral
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "disassemble", signature = "disassemble(head: string)",
            description = "Disassemble the aileron bearing peripheral.")
    public final void disassemble(String headName) throws ScriptException {
        blockEntity.disassembleMountedBlock(parseHead(headName));
    }

    // Check if this is assembled
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "isAssembled", signature = "isAssembled(head: string): boolean",
            description = "Returns whether this is assembled.")
    public final boolean isAssembled(String headName) throws ScriptException {
        return blockEntity.isMountedAssemblyPresent(parseHead(headName));
    }

    // Get the assemblies
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getAssemblies", signature = "getAssemblies(): table",
            description = "Returns the assemblies.")
    public final Map<String, Object> getAssemblies() {
        Map<String, Object> assemblies = new LinkedHashMap<>();
        for (BearingHead head : BearingHead.values()) {
            assemblies.put(head.serializedName(), blockEntity.isMountedAssemblyPresent(head));
        }
        return assemblies;
    }

    // Get the status
    @ScriptFunctionBinding(mainThread = true)
    @ScriptMethodDoc(name = "getStatus", signature = "getStatus(): table",
            description = "Returns the status.")
    public final Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("headMode", getHeadMode());
        status.put("controlMode", getControlMode());
        status.put("activeControlMode", getActiveControlMode());
        status.put("angles", getAngles());
        status.put("ranges", getRanges());
        status.put("signals", getSignals());
        status.put("assemblies", getAssemblies());
        status.put("thrusterCount", getThrusterCount());
        status.put("thrusters", listThrusters());
        status.put("rcsThrusterCount", getRcsThrusterCount());
        status.put("rcsThrusters", listRcsThrusters());
        status.put("totalRealThrust", getTotalRealThrust());
        status.put("totalLiftCapacity", getTotalLiftCapacity());
        return status;
    }

    // Add the head angles
    private void addHeadAngles(Map<String, Object> angles, BearingHead head) {
        Map<String, Object> headAngles = new LinkedHashMap<>();
        headAngles.put("angle", blockEntity.getHeadAngle(head));
        headAngles.put("target", blockEntity.getHeadTargetAngle(head));
        headAngles.put("computerOverride", blockEntity.hasHeadTargetOverride(head));
        angles.put(head.serializedName(), headAngles);
    }

    // Add the head range
    private void addHeadRange(Map<String, Object> ranges, BearingHead head) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("min", blockEntity.getMinAngle(head));
        range.put("max", blockEntity.getMaxAngle(head));
        ranges.put(head.serializedName(), range);
    }

    // Validate the finite
    private static void validateFinite(double val, String name) throws ScriptException {
        if (!Double.isFinite(val)) {
            throw new ScriptException(name + " must be finite");
        }
    }

    // Parse the head
    private static BearingHead parseHead(String headName) throws ScriptException {
        if (headName == null) {
            throw new ScriptException("head must be 'primary', 'secondary', 'cyan', 'orange', 'left', or 'right'");
        }
        BearingHead head =
                BearingHead.byName(headName, null);
        if (head == null) {
            throw new ScriptException("head must be 'primary', 'secondary', 'cyan', 'orange', 'left', or 'right'");
        }
        return head;
    }

    // Parse the head mode
    private static AileronBearingBlockEntity.HeadMode parseHeadMode(String modeName) throws ScriptException {
        if (modeName == null) {
            throw new ScriptException("invalid head mode");
        }
        AileronBearingBlockEntity.HeadMode mode =
                AileronBearingBlockEntity.HeadMode.byName(modeName.trim().toLowerCase(Locale.ROOT), null);
        if (mode == null) {
            throw new ScriptException("invalid head mode");
        }
        return mode;
    }

    // Parse the control mode
    private static AileronBearingBlockEntity.ControlMode parseControlMode(String modeName) throws ScriptException {
        if (modeName == null) {
            throw new ScriptException("control mode must be 'auto', 'redstone', 'servo' or 'computer'");
        }
        AileronBearingBlockEntity.ControlMode mode =
                AileronBearingBlockEntity.ControlMode.byName(modeName.trim().toLowerCase(Locale.ROOT), null);
        if (mode == null) {
            throw new ScriptException("control mode must be 'auto', 'redstone', 'servo' or 'computer'");
        }
        return mode;
    }
}
