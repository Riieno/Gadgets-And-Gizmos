package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Expose one aileron target and its live angle to ComputerCraft
@PeripheralTypeDoc("aileron_bearing")
public class AileronBearingPeripheral extends GadgetsPeripheral<AileronBearingBlockEntity> {
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
    public AileronBearingPeripheral(AileronBearingBlockEntity blockEntity) {
        super(blockEntity, "aileron_bearing");
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the head mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getHeadMode", signature = "getHeadMode(): string",
            description = "Returns the head mode.")
    public final String getHeadMode() {
        return blockEntity.getHeadMode().serializedName();
    }

    // Set the head mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setHeadMode", signature = "setHeadMode(mode: 'single'|'mirrored'|'opposed'|'precise'|'free_single'|'free_mirrored'|'free_opposed')",
            description = "Sets the head mode.")
    public final void setHeadMode(String modeName) throws LuaException {
        blockEntity.setHeadMode(parseHeadMode(modeName));
    }

    // Get the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getControlMode", signature = "getControlMode(): string",
            description = "Returns the control mode.")
    public final String getControlMode() {
        return blockEntity.getControlMode().serializedName();
    }

    // Set the control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setControlMode", signature = "setControlMode(mode: 'auto'|'redstone'|'servo'|'computer')",
            description = "Sets the control mode.")
    public final void setControlMode(String modeName) throws LuaException {
        blockEntity.setControlMode(parseControlMode(modeName));
    }

    // Get the active control mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getActiveControlMode", signature = "getActiveControlMode(): string",
            description = "Returns the active control mode.")
    public final String getActiveControlMode() {
        return blockEntity.getActiveControlMode().serializedName();
    }

    // Get the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAngles", signature = "getAngles(): table",
            description = "Returns the angles.")
    public final Map<String, Object> getAngles() {
        Map<String, Object> angles = new LinkedHashMap<>();
        addHeadAngles(angles, BearingHead.PRIMARY);
        addHeadAngles(angles, BearingHead.SECONDARY);
        return angles;
    }

    // Set the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAngles", signature = "setAngles(primaryDegrees: number, secondaryDegrees: number)",
            description = "Sets the angles.")
    public final void setAngles(double primaryDegrees, double secondaryDegrees) throws LuaException {
        validateFinite(primaryDegrees, "primaryDegrees");
        validateFinite(secondaryDegrees, "secondaryDegrees");
        blockEntity.setHeadTargetAngle(BearingHead.PRIMARY, primaryDegrees);
        blockEntity.setHeadTargetAngle(BearingHead.SECONDARY, secondaryDegrees);
    }

    // Set the head angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setHeadAngle", signature = "setHeadAngle(head: string, angleDegrees: number)",
            description = "Sets the head angle.")
    public final void setHeadAngle(String headName, double angleDegrees) throws LuaException {
        validateFinite(angleDegrees, "angleDegrees");
        blockEntity.setHeadTargetAngle(parseHead(headName), angleDegrees);
    }

    // Clear the head angle
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearHeadAngle", signature = "clearHeadAngle(head: string)",
            description = "Clears the head angle.")
    public final void clearHeadAngle(String headName) throws LuaException {
        blockEntity.clearHeadTargetOverride(parseHead(headName));
    }

    // Clear the angles
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "clearAngles", signature = "clearAngles()",
            description = "Clears the angles.")
    public final void clearAngles() {
        blockEntity.clearHeadTargetOverride(BearingHead.PRIMARY);
        blockEntity.clearHeadTargetOverride(BearingHead.SECONDARY);
    }

    // Get the ranges
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getRanges", signature = "getRanges(): table",
            description = "Returns the ranges.")
    public final Map<String, Object> getRanges() {
        Map<String, Object> ranges = new LinkedHashMap<>();
        addHeadRange(ranges, BearingHead.PRIMARY);
        addHeadRange(ranges, BearingHead.SECONDARY);
        return ranges;
    }

    // Set the head range
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setHeadRange", signature = "setHeadRange(head: string, minDegrees: number, maxDegrees: number)",
            description = "Sets the head range.")
    public final void setHeadRange(String headName, double minDegrees, double maxDegrees) throws LuaException {
        validateFinite(minDegrees, "minDegrees");
        validateFinite(maxDegrees, "maxDegrees");
        blockEntity.setAngleRange(parseHead(headName), minDegrees, maxDegrees);
    }

    // Get the signals
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getSignals", signature = "getSignals(): table",
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
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "assemble", signature = "assemble(head: string): boolean",
            description = "Assemble the aileron bearing peripheral.")
    public final boolean assemble(String headName) throws LuaException {
        return blockEntity.tryAssembleMountedBlock(parseHead(headName));
    }

    // Disassemble the aileron bearing peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "disassemble", signature = "disassemble(head: string)",
            description = "Disassemble the aileron bearing peripheral.")
    public final void disassemble(String headName) throws LuaException {
        blockEntity.disassembleMountedBlock(parseHead(headName));
    }

    // Check if this is assembled
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isAssembled", signature = "isAssembled(head: string): boolean",
            description = "Returns whether this is assembled.")
    public final boolean isAssembled(String headName) throws LuaException {
        return blockEntity.isMountedAssemblyPresent(parseHead(headName));
    }

    // Get the assemblies
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAssemblies", signature = "getAssemblies(): table",
            description = "Returns the assemblies.")
    public final Map<String, Object> getAssemblies() {
        Map<String, Object> assemblies = new LinkedHashMap<>();
        for (BearingHead head : BearingHead.values()) {
            assemblies.put(head.serializedName(), blockEntity.isMountedAssemblyPresent(head));
        }
        return assemblies;
    }

    // Get the status
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getStatus", signature = "getStatus(): table",
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
    private static void validateFinite(double val, String name) throws LuaException {
        if (!Double.isFinite(val)) {
            throw new LuaException(name + " must be finite");
        }
    }

    // Parse the head
    private static BearingHead parseHead(String headName) throws LuaException {
        if (headName == null) {
            throw new LuaException("head must be 'primary', 'secondary', 'cyan', 'orange', 'left', or 'right'");
        }
        BearingHead head =
                BearingHead.byName(headName, null);
        if (head == null) {
            throw new LuaException("head must be 'primary', 'secondary', 'cyan', 'orange', 'left', or 'right'");
        }
        return head;
    }

    // Parse the head mode
    private static AileronBearingBlockEntity.HeadMode parseHeadMode(String modeName) throws LuaException {
        if (modeName == null) {
            throw new LuaException("invalid head mode");
        }
        AileronBearingBlockEntity.HeadMode mode =
                AileronBearingBlockEntity.HeadMode.byName(modeName.trim().toLowerCase(Locale.ROOT), null);
        if (mode == null) {
            throw new LuaException("invalid head mode");
        }
        return mode;
    }

    // Parse the control mode
    private static AileronBearingBlockEntity.ControlMode parseControlMode(String modeName) throws LuaException {
        if (modeName == null) {
            throw new LuaException("control mode must be 'auto', 'redstone', 'servo' or 'computer'");
        }
        AileronBearingBlockEntity.ControlMode mode =
                AileronBearingBlockEntity.ControlMode.byName(modeName.trim().toLowerCase(Locale.ROOT), null);
        if (mode == null) {
            throw new LuaException("control mode must be 'auto', 'redstone', 'servo' or 'computer'");
        }
        return mode;
    }
}
