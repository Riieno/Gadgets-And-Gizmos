package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Expose one aileron target and its live angle to ComputerCraft
public class AileronBearingPeripheral implements IPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bound block entity
    private final AileronBearingBlockEntity blockEntity;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing peripheral
    public AileronBearingPeripheral(AileronBearingBlockEntity blockEntity) {
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
        return "aileron_bearing";
    }

    // Compare this aileron bearing peripheral with another object
    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof AileronBearingPeripheral peripheral
                && peripheral.blockEntity == blockEntity;
    }

    // Get the head mode
    @LuaFunction
    public final String getHeadMode() {
        return blockEntity.getHeadMode().serializedName();
    }

    // Set the head mode
    @LuaFunction
    public final void setHeadMode(String modeName) throws LuaException {
        blockEntity.setHeadMode(parseHeadMode(modeName));
    }

    // Get the control mode
    @LuaFunction
    public final String getControlMode() {
        return blockEntity.getControlMode().serializedName();
    }

    // Set the control mode
    @LuaFunction
    public final void setControlMode(String modeName) throws LuaException {
        blockEntity.setControlMode(parseControlMode(modeName));
    }

    // Get the active control mode
    @LuaFunction
    public final String getActiveControlMode() {
        return blockEntity.getActiveControlMode().serializedName();
    }

    // Get the angles
    @LuaFunction
    public final Map<String, Object> getAngles() {
        Map<String, Object> angles = new LinkedHashMap<>();
        addHeadAngles(angles, BearingHead.PRIMARY);
        addHeadAngles(angles, BearingHead.SECONDARY);
        return angles;
    }

    // Set the angles
    @LuaFunction
    public final void setAngles(double primaryDegrees, double secondaryDegrees) throws LuaException {
        validateFinite(primaryDegrees, "primaryDegrees");
        validateFinite(secondaryDegrees, "secondaryDegrees");
        blockEntity.setHeadTargetAngle(BearingHead.PRIMARY, primaryDegrees);
        blockEntity.setHeadTargetAngle(BearingHead.SECONDARY, secondaryDegrees);
    }

    // Set the head angle
    @LuaFunction
    public final void setHeadAngle(String headName, double angleDegrees) throws LuaException {
        validateFinite(angleDegrees, "angleDegrees");
        blockEntity.setHeadTargetAngle(parseHead(headName), angleDegrees);
    }

    // Clear the head angle
    @LuaFunction
    public final void clearHeadAngle(String headName) throws LuaException {
        blockEntity.clearHeadTargetOverride(parseHead(headName));
    }

    // Clear the angles
    @LuaFunction
    public final void clearAngles() {
        blockEntity.clearHeadTargetOverride(BearingHead.PRIMARY);
        blockEntity.clearHeadTargetOverride(BearingHead.SECONDARY);
    }

    // Get the ranges
    @LuaFunction
    public final Map<String, Object> getRanges() {
        Map<String, Object> ranges = new LinkedHashMap<>();
        addHeadRange(ranges, BearingHead.PRIMARY);
        addHeadRange(ranges, BearingHead.SECONDARY);
        return ranges;
    }

    // Set the head range
    @LuaFunction
    public final void setHeadRange(String headName, double minDegrees, double maxDegrees) throws LuaException {
        validateFinite(minDegrees, "minDegrees");
        validateFinite(maxDegrees, "maxDegrees");
        blockEntity.setAngleRange(parseHead(headName), minDegrees, maxDegrees);
    }

    // Get the signals
    @LuaFunction
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
    @LuaFunction
    public final boolean assemble(String headName) throws LuaException {
        return blockEntity.tryAssembleMountedBlock(parseHead(headName));
    }

    // Disassemble the aileron bearing peripheral
    @LuaFunction
    public final void disassemble(String headName) throws LuaException {
        blockEntity.disassembleMountedBlock(parseHead(headName));
    }

    // Check if this is assembled
    @LuaFunction
    public final boolean isAssembled(String headName) throws LuaException {
        return blockEntity.isMountedAssemblyPresent(parseHead(headName));
    }

    // Get the assemblies
    @LuaFunction
    public final Map<String, Object> getAssemblies() {
        Map<String, Object> assemblies = new LinkedHashMap<>();
        for (BearingHead head : BearingHead.values()) {
            assemblies.put(head.serializedName(), blockEntity.isMountedAssemblyPresent(head));
        }
        return assemblies;
    }

    // Get the status
    @LuaFunction
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

    // List the exposed peripheral methods
    @LuaFunction
    public final List<String> methods() {
        return List.of(
                "getHeadMode(): string",
                "setHeadMode(mode: 'single'|'mirrored'|'opposed'|'precise'|'free_single'|'free_mirrored'|'free_opposed')",
                "getControlMode(): string",
                "setControlMode(mode: 'auto'|'redstone'|'servo'|'computer')",
                "getActiveControlMode(): string",
                "getAngles(): table",
                "setAngles(primaryDegrees: number, secondaryDegrees: number)",
                "setHeadAngle(head: string, angleDegrees: number)",
                "clearHeadAngle(head: string)",
                "clearAngles()",
                "getRanges(): table",
                "setHeadRange(head: string, minDegrees: number, maxDegrees: number)",
                "getSignals(): table",
                "assemble(head: string): boolean",
                "disassemble(head: string)",
                "isAssembled(head: string): boolean",
                "getAssemblies(): table",
                "getStatus(): table",
                "methods(): string[]",
                "help(method?: string): string|table");
    }

    // Get the help
    @LuaFunction
    public final Object help(Optional<String> method) throws LuaException {
        Map<String, String> help = new LinkedHashMap<>();
        help.put("setHeadMode", "setHeadMode('single'|'mirrored'|'opposed'|'precise'|'free_single'|'free_mirrored'|'free_opposed')");
        help.put("setControlMode", "setControlMode('auto'|'redstone'|'servo'|'computer')");
        help.put("setAngles", "setAngles(primaryDegrees, secondaryDegrees)");
        help.put("setHeadAngle", "setHeadAngle('primary'|'secondary'|'cyan'|'orange'|'left'|'right', angleDegrees)");
        help.put("clearHeadAngle", "clearHeadAngle(head) clears that head's computer target override");
        help.put("setHeadRange", "setHeadRange('primary'|'secondary'|'cyan'|'orange'|'left'|'right', minDegrees, maxDegrees)");
        help.put("assemble", "assemble(head) attempts to assemble the selected head");
        help.put("disassemble", "disassemble(head) disassembles the selected head");
        help.put("isAssembled", "isAssembled(head) returns whether the selected head is assembled");
        help.put("getStatus", "getStatus() -> table of modes, angles, ranges, signals, and assemblies");
        help.put("methods", "methods() -> list of all callable peripheral methods");
        help.put("help", "help() -> all docs, help('name') -> one entry");
        if (method.isEmpty()) {
            return help;
        }
        String key = method.get();
        if (!help.containsKey(key)) {
            throw new LuaException("unknown method '" + key + "'");
        }
        return help.get(key);
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
