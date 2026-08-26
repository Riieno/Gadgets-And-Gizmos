package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// Expose safe controller configuration and live controls through the ComputerCraft peripheral API
@PeripheralTypeDoc("analogue_contraption_controller")
public class AnalogueContraptionControllerPeripheral
        extends GadgetsPeripheral<AnalogueContraptionControllerBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<String, Integer> KEY_CODES = createKeyCodes();
    private static final Map<Integer, String> KEY_NAMES = createKeyNames();

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

    // Initialize the analogue contraption controller peripheral
    public AnalogueContraptionControllerPeripheral(AnalogueContraptionControllerBlockEntity blockEntity) {
        this(blockEntity, "analogue_contraption_controller");
    }

    // Initialize the analogue contraption controller peripheral with its public type
    protected AnalogueContraptionControllerPeripheral(
            AnalogueContraptionControllerBlockEntity blockEntity,
            String peripheralType) {
        super(blockEntity, peripheralType);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

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

    // Get the list inputs
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listInputs", signature = "listInputs(): table",
            description = "Returns the list inputs.")
    public final List<Map<String, Object>> listInputs() {
        return listCustomEntries();
    }

    // Get the list input ids
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listInputIds", signature = "listInputIds(): table",
            description = "Returns the list input ids.")
    public final List<String> listInputIds() {
        return blockEntity.getCustomKeyEntries().stream()
                .map(CustomKeyEntry::id)
                .toList();
    }

    // Get the list channels
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listChannels", signature = "listChannels(): table",
            description = "Returns the list channels.")
    public final List<Map<String, Object>> listChannels() {
        return listInputs();
    }

    // Get the list axes
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listAxes", signature = "listAxes(): table",
            description = "Returns the list axes.")
    public final List<String> listAxes() {
        return List.of();
    }

    // Add the input
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "addInput", signature = "addInput(label: string): string",
            description = "Add the input.")
    public final String addInput(String label) {
        String id = blockEntity.addCustomKeyEntry();
        CustomKeyEntry entry = findEntry(id);
        if (entry != null && label != null && !label.isBlank()) {
            blockEntity.applyCustomKeyEntry(entry.id(), entry.keyCode, entry.stepDownKeyCode, label.strip(),
                    entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                    entry.deadzone, entry.smoothing, entry.localOutputSide, entry.first, entry.second,
                    entry.inputFirst, entry.inputSecond, entry.directTarget, entry.inputTarget, entry.bindingPreset);
        }
        return id;
    }

    // Get the alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAlias", signature = "getAlias(idOrAlias: string): string",
            description = "Returns the alias.")
    public final String getAlias(String idOrAlias) throws LuaException {
        return resolvedAlias(requireEntry(idOrAlias));
    }

    // Get the input alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputAlias", signature = "getInputAlias(idOrAlias: string): string",
            description = "Returns the input alias.")
    public final String getInputAlias(String idOrAlias) throws LuaException {
        return getAlias(idOrAlias);
    }

    // Set the alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAlias", signature = "setAlias(idOrAlias: string, alias: string): string",
            description = "Sets the alias.")
    public final String setAlias(String idOrAlias, String alias) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrAlias);
        String sanitized = alias == null ? "" : alias.trim();
        if (!sanitized.isBlank()) {
            CustomKeyEntry collision = findEntryByAliasOrId(sanitized);
            if (collision != null && !Objects.equals(collision.id(), entry.id())) {
                throw new LuaException("alias '" + sanitized + "' is already used by input '" + collision.id() + "'");
            }
        }
        entry.alias = sanitized;
        applyEntry(entry, entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, entry.localOutputSide, entry.first, entry.second,
                entry.inputFirst, entry.inputSecond, entry.keyCode, entry.stepDownKeyCode, entry.label,
                entry.directTarget, entry.inputTarget, entry.bindingPreset);
        return resolvedAlias(entry);
    }

    // Set the input alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputAlias", signature = "setInputAlias(idOrAlias: string, alias: string): string",
            description = "Sets the input alias.")
    public final String setInputAlias(String idOrAlias, String alias) throws LuaException {
        return setAlias(idOrAlias, alias);
    }

    // Set the channel alias
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelAlias", signature = "setChannelAlias(idOrAlias: string, alias: string): string",
            description = "Sets the channel alias.")
    public final String setChannelAlias(String idOrAlias, String alias) throws LuaException {
        return setAlias(idOrAlias, alias);
    }

    // Add the custom entry
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "addCustomEntry", signature = "addCustomEntry(): string",
            description = "Add the custom entry.")
    public final String addCustomEntry() {
        return blockEntity.addCustomKeyEntry();
    }

    // Remove the input
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "removeInput", signature = "removeInput(idOrLabel: string)",
            description = "Remove the input.")
    public final void removeInput(String idOrLabel) throws LuaException {
        blockEntity.removeCustomKeyEntry(requireEntry(idOrLabel).id());
    }

    // Remove the custom entry
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "removeCustomEntry", signature = "removeCustomEntry(idOrLabel: string)",
            description = "Remove the custom entry.")
    public final void removeCustomEntry(String idOrLabel) throws LuaException {
        removeInput(idOrLabel);
    }

    // Get the list custom entries
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listCustomEntries", signature = "listCustomEntries(): table",
            description = "Returns the list custom entries.")
    public final List<Map<String, Object>> listCustomEntries() {
        return blockEntity.getCustomKeyEntries().stream()
                .map(this::describeEntry)
                .toList();
    }

    // Get the input
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInput", signature = "getInput(idOrLabel: string): table",
            description = "Returns the input.")
    public final Map<String, Object> getInput(String idOrLabel) throws LuaException {
        return describeEntry(requireEntry(idOrLabel));
    }

    // Get the input by key
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputByKey", signature = "getInputByKey(key: string): table",
            description = "Returns the input by key.")
    public final Map<String, Object> getInputByKey(String key) throws LuaException {
        int keyCode = requireKeyCode(key, "key");
        return describeBoundKey(keyCode, key);
    }

    // Get the input by key code
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputByKeyCode", signature = "getInputByKeyCode(keyCode: number): table",
            description = "Returns the input by key code.")
    public final Map<String, Object> getInputByKeyCode(int keyCode) throws LuaException {
        return describeBoundKey(keyCode, Integer.toString(keyCode));
    }

    // Get the bound key state
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBoundKeyState", signature = "getBoundKeyState(key: string): table",
            description = "Returns the bound key state.")
    public final Map<String, Object> getBoundKeyState(String key) throws LuaException {
        return getInputByKey(key);
    }

    // Get the bound key code state
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBoundKeyCodeState", signature = "getBoundKeyCodeState(keyCode: number): table",
            description = "Returns the bound key code state.")
    public final Map<String, Object> getBoundKeyCodeState(int keyCode) throws LuaException {
        return getInputByKeyCode(keyCode);
    }

    // Get the channel
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getChannel", signature = "getChannel(idOrLabel: string): table",
            description = "Returns the channel.")
    public final Map<String, Object> getChannel(String idOrLabel) throws LuaException {
        return getInput(idOrLabel);
    }

    // Get the custom entry
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCustomEntry", signature = "getCustomEntry(idOrLabel: string): table",
            description = "Returns the custom entry.")
    public final Map<String, Object> getCustomEntry(String idOrLabel) throws LuaException {
        return getInput(idOrLabel);
    }

    // Get the input config
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputConfig", signature = "getInputConfig(idOrLabel: string): table",
            description = "Returns the input config.")
    public final Map<String, Object> getInputConfig(String idOrLabel) throws LuaException {
        return getInput(idOrLabel);
    }

    // Get the channel config
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getChannelConfig", signature = "getChannelConfig(idOrLabel: string): table",
            description = "Returns the channel config.")
    public final Map<String, Object> getChannelConfig(String idOrLabel) throws LuaException {
        return getInput(idOrLabel);
    }

    // Set the input
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInput", signature = "setInput(idOrLabel: string, value: number)",
            description = "Sets the input.")
    public final void setInput(String idOrLabel, double value) throws LuaException {
        requireUnitValue(value, "value");
        CustomKeyEntry entry = requireEntry(idOrLabel);
        if (!blockEntity.setCustomEntryExactValue(entry.id(), value)) {
            throw new LuaException("input '" + idOrLabel + "' is not available");
        }
    }

    // Set the channel
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannel", signature = "setChannel(idOrLabel: string, value: number)",
            description = "Sets the channel.")
    public final void setChannel(String idOrLabel, double value) throws LuaException {
        setInput(idOrLabel, value);
    }

    // Set the input by key
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputByKey", signature = "setInputByKey(key: string, value: number): number",
            description = "Sets the input by key.")
    public final int setInputByKey(String key, double value) throws LuaException {
        requireUnitValue(value, "value");
        int keyCode = requireKeyCode(key, "key");
        return requireDispatchedTargets(blockEntity.setBoundKeyExactValue(keyCode, value), "key '" + key + "'");
    }

    // Set the input by key code
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputByKeyCode", signature = "setInputByKeyCode(keyCode: number, value: number): number",
            description = "Sets the input by key code.")
    public final int setInputByKeyCode(int keyCode, double value) throws LuaException {
        requireUnitValue(value, "value");
        return requireDispatchedTargets(blockEntity.setBoundKeyExactValue(keyCode, value), "key code '" + keyCode + "'");
    }

    // Get the input value
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputValue", signature = "getInputValue(idOrLabel: string): number",
            description = "Returns the input value.")
    public final double getInputValue(String idOrLabel) throws LuaException {
        return blockEntity.getCustomEntryValue(requireEntry(idOrLabel).id());
    }

    // Get the custom entry value
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCustomEntryValue", signature = "getCustomEntryValue(idOrLabel: string): number",
            description = "Returns the custom entry value.")
    public final double getCustomEntryValue(String idOrLabel) throws LuaException {
        return getInputValue(idOrLabel);
    }

    // Get the channel mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getChannelMode", signature = "getChannelMode(idOrLabel: string): string",
            description = "Returns the channel mode.")
    public final String getChannelMode(String idOrLabel) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        return entry.mode == null ? "ramp" : entry.mode.name().toLowerCase(Locale.ROOT);
    }

    // Set the channel mode
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelMode", signature = "setChannelMode(idOrLabel: string, mode: string)",
            description = "Sets the channel mode.")
    public final void setChannelMode(String idOrLabel, String mode) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        applyEntry(entry, parseMode(mode), entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, entry.localOutputSide, entry.first, entry.second,
                entry.inputFirst, entry.inputSecond, entry.keyCode, entry.stepDownKeyCode, entry.label,
                entry.directTarget, entry.inputTarget, entry.bindingPreset);
    }

    // Set the input config
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputConfig", signature = "setInputConfig(idOrLabel: string, config: table)",
            description = "Sets the input config.")
    public final void setInputConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        setEntryConfig(idOrLabel, config);
    }

    // Set the channel config
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelConfig", signature = "setChannelConfig(idOrLabel: string, config: table)",
            description = "Sets the channel config.")
    public final void setChannelConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        setEntryConfig(idOrLabel, config);
    }

    // Set the custom entry config
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setCustomEntryConfig", signature = "setCustomEntryConfig(idOrLabel: string, config: table)",
            description = "Sets the custom entry config.")
    public final void setCustomEntryConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        setEntryConfig(idOrLabel, config);
    }

    // Press the analogue contraption controller peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "press", signature = "press(idOrLabel: string)",
            description = "Press the analogue contraption controller peripheral.")
    public final void press(String idOrLabel) throws LuaException {
        pressInput(idOrLabel);
    }

    // Press the input
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressInput", signature = "pressInput(idOrLabel: string)",
            description = "Press the input.")
    public final void pressInput(String idOrLabel) throws LuaException {
        EntryMatch match = requireEntryMatch(idOrLabel);
        if (!tapKeyMatch(match)) {
            throw new LuaException("input '" + idOrLabel + "' is not available");
        }
    }

    // Press the custom entry
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressCustomEntry", signature = "pressCustomEntry(idOrLabel: string)",
            description = "Press the custom entry.")
    public final void pressCustomEntry(String idOrLabel) throws LuaException {
        pressInput(idOrLabel);
    }

    // Press the key
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressKey", signature = "pressKey(key: string): number",
            description = "Press the key.")
    public final int pressKey(String key) throws LuaException {
        int keyCode = requireKeyCode(key, "key");
        return requireDispatchedTargets(blockEntity.tapBoundKey(keyCode), "key '" + key + "'");
    }

    // Press the key code
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressKeyCode", signature = "pressKeyCode(keyCode: number): number",
            description = "Press the key code.")
    public final int pressKeyCode(int keyCode) throws LuaException {
        return requireDispatchedTargets(blockEntity.tapBoundKey(keyCode), "key code '" + keyCode + "'");
    }

    // Press the input step down
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressInputStepDown", signature = "pressInputStepDown(idOrLabel: string)",
            description = "Press the input step down.")
    public final void pressInputStepDown(String idOrLabel) throws LuaException {
        EntryMatch match = requireEntryMatch(idOrLabel);
        EntryMatch stepDown = new EntryMatch(match.entry(), true);
        if (!tapKeyMatch(stepDown)) {
            throw new LuaException("input '" + idOrLabel + "' is not available");
        }
    }

    // Press the custom entry step down
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressCustomEntryStepDown", signature = "pressCustomEntryStepDown(idOrLabel: string)",
            description = "Press the custom entry step down.")
    public final void pressCustomEntryStepDown(String idOrLabel) throws LuaException {
        pressInputStepDown(idOrLabel);
    }

    // Reset the analogue contraption controller peripheral
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "reset", signature = "reset(idOrLabel: string)",
            description = "Reset the analogue contraption controller peripheral.")
    public final void reset(String idOrLabel) throws LuaException {
        resetInput(idOrLabel);
    }

    // Reset the input
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "resetInput", signature = "resetInput(idOrLabel: string)",
            description = "Reset the input.")
    public final void resetInput(String idOrLabel) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        if (!blockEntity.resetCustomEntry(entry.id())) {
            throw new LuaException("input '" + idOrLabel + "' is not available");
        }
    }

    // Reset every analogue control signal
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "resetAll", signature = "resetAll()",
            description = "Reset every analogue control signal.")
    public final void resetAll() {
        for (CustomKeyEntry entry : blockEntity.getCustomKeyEntries()) {
            blockEntity.resetCustomEntry(entry.id());
        }
    }

    // Check if the input is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isInputActive", signature = "isInputActive(idOrLabel: string): boolean",
            description = "Returns whether the input is active.")
    public final boolean isInputActive(String idOrLabel) throws LuaException {
        return blockEntity.isCustomEntryActive(requireEntry(idOrLabel).id());
    }

    // Check if the custom entry is active
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isCustomEntryActive", signature = "isCustomEntryActive(idOrLabel: string): boolean",
            description = "Returns whether the custom entry is active.")
    public final boolean isCustomEntryActive(String idOrLabel) throws LuaException {
        return isInputActive(idOrLabel);
    }

    // Get the local output
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLocalOutput", signature = "getLocalOutput(side: string): number",
            description = "Returns the local output.")
    public final int getLocalOutput(String side) throws LuaException {
        return blockEntity.getLocalOutputSignal(parseDirection(side, "side"));
    }

    // Set the local output side
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setLocalOutputSide", signature = "setLocalOutputSide(idOrLabel: string, side: string)",
            description = "Sets the local output side.")
    public final void setLocalOutputSide(String idOrLabel, String side) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        applyEntry(entry, entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, parseDirectionOrEmpty(side, "side"), entry.first, entry.second,
                entry.inputFirst, entry.inputSecond, entry.keyCode, entry.stepDownKeyCode, entry.label,
                entry.directTarget, entry.inputTarget, entry.bindingPreset);
    }

    // Set the channel frequency
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelFrequency", signature = "setChannelFrequency(idOrLabel: string, first: string, second: string)",
            description = "Sets the channel frequency.")
    public final void setChannelFrequency(String idOrLabel, String first, String second) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        applyEntry(entry, entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, entry.localOutputSide, parseFrequency(first), parseFrequency(second),
                entry.inputFirst, entry.inputSecond, entry.keyCode, entry.stepDownKeyCode, entry.label,
                entry.directTarget, entry.inputTarget, entry.bindingPreset);
    }

    // Set the input frequency
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputFrequency", signature = "setInputFrequency(idOrLabel: string, first: string, second: string)",
            description = "Sets the input frequency.")
    public final void setInputFrequency(String idOrLabel, String first, String second) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        applyEntry(entry, entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, entry.localOutputSide, entry.first, entry.second,
                parseFrequency(first), parseFrequency(second), entry.keyCode, entry.stepDownKeyCode, entry.label,
                entry.directTarget, entry.inputTarget, entry.bindingPreset);
    }

    // Set the channel direct target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelDirectTarget", signature = "setChannelDirectTarget(idOrLabel: string, target: table)",
            description = "Sets the channel direct target.")
    public final void setChannelDirectTarget(String idOrLabel, Map<?, ?> target) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        applyEntry(entry, entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, entry.localOutputSide, entry.first, entry.second,
                entry.inputFirst, entry.inputSecond, entry.keyCode, entry.stepDownKeyCode, entry.label,
                parseDirectTarget(target, "target"), entry.inputTarget, entry.bindingPreset);
    }

    // Set the channel input target
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelInputTarget", signature = "setChannelInputTarget(idOrLabel: string, target: table)",
            description = "Sets the channel input target.")
    public final void setChannelInputTarget(String idOrLabel, Map<?, ?> target) throws LuaException {
        CustomKeyEntry entry = requireEntry(idOrLabel);
        applyEntry(entry, entry.mode, entry.riseRate, entry.fallRate, entry.stepAmount, entry.stepDownAmount,
                entry.deadzone, entry.smoothing, entry.localOutputSide, entry.first, entry.second,
                entry.inputFirst, entry.inputSecond, entry.keyCode, entry.stepDownKeyCode, entry.label,
                entry.directTarget, parseDirectTarget(target, "target"), entry.bindingPreset);
    }

    // Get all signals
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAllSignals", signature = "getAllSignals(): table",
            description = "Returns all signals.")
    public final Map<String, Object> getAllSignals() {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (CustomKeyEntry entry : blockEntity.getCustomKeyEntries()) {
            inputs.put(entry.id(), describeEntry(entry));
        }
        res.put("inputs", inputs);
        res.put("channels", inputs);
        res.put("axes", Map.of());
        return res;
    }

    // Get the axis
    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAxis", signature = "getAxis(name: string): table",
            description = "Returns the axis.")
    public final Map<String, Object> getAxis(String name) throws LuaException {
        throw new LuaException("axes are not exposed; use named inputs from listInputs()");
    }

    // Set the entry config
    private void setEntryConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        if (config == null) {
            throw new LuaException("config must be a table");
        }
        CustomKeyEntry entry = requireEntry(idOrLabel);
        AnalogueChannelMode mode = config.containsKey("mode") ? parseMode(requireString(config.get("mode"), "mode")) : entry.mode;
        double riseRate = config.containsKey("riseRate") ? requireNumber(config.get("riseRate"), "riseRate") : entry.riseRate;
        double fallRate = config.containsKey("fallRate") ? requireNumber(config.get("fallRate"), "fallRate") : entry.fallRate;
        double stepAmount = config.containsKey("stepAmount") ? requireNumber(config.get("stepAmount"), "stepAmount") : entry.stepAmount;
        double stepDownAmount = config.containsKey("stepDownAmount") ? requireNumber(config.get("stepDownAmount"), "stepDownAmount") : entry.stepDownAmount;
        double deadzone = config.containsKey("deadzone") ? requireNumber(config.get("deadzone"), "deadzone") : entry.deadzone;
        double smoothing = config.containsKey("smoothing") ? requireNumber(config.get("smoothing"), "smoothing") : entry.smoothing;
        Direction localSide = config.containsKey("localSide") ? parseDirectionOrEmpty(config.get("localSide"), "localSide") : entry.localOutputSide;
        ItemStack first = config.containsKey("frequencyA") ? parseFrequency(requireString(config.get("frequencyA"), "frequencyA")) : entry.first;
        ItemStack second = config.containsKey("frequencyB") ? parseFrequency(requireString(config.get("frequencyB"), "frequencyB")) : entry.second;
        ItemStack inputFirst = config.containsKey("inputFrequencyA") ? parseFrequency(requireString(config.get("inputFrequencyA"), "inputFrequencyA")) : entry.inputFirst;
        ItemStack inputSecond = config.containsKey("inputFrequencyB") ? parseFrequency(requireString(config.get("inputFrequencyB"), "inputFrequencyB")) : entry.inputSecond;
        int keyCode = config.containsKey("keyCode") ? (int) requireNumber(config.get("keyCode"), "keyCode")
                : config.containsKey("key") ? requireKeyCode(config.get("key"), "key") : entry.keyCode;
        int stepDownKeyCode = config.containsKey("stepDownKeyCode") ? (int) requireNumber(config.get("stepDownKeyCode"), "stepDownKeyCode")
                : config.containsKey("stepDownKey") ? requireKeyCode(config.get("stepDownKey"), "stepDownKey") : entry.stepDownKeyCode;
        String label = config.containsKey("label") ? requireString(config.get("label"), "label") : entry.label;
        String alias = config.containsKey("alias") ? requireString(config.get("alias"), "alias") : entry.alias;
        ControllerDirectTargetReference directTarget = config.containsKey("directTarget")
                ? parseDirectTarget(config.get("directTarget"), "directTarget") : entry.directTarget;
        ControllerDirectTargetReference inputTarget = config.containsKey("inputTarget")
                ? parseDirectTarget(config.get("inputTarget"), "inputTarget") : entry.inputTarget;
        String bindingPreset = config.containsKey("bindingPreset")
                ? requireString(config.get("bindingPreset"), "bindingPreset") : entry.bindingPreset;

        applyEntry(entry, mode, riseRate, fallRate, stepAmount, stepDownAmount, deadzone, smoothing, localSide,
                first, second, inputFirst, inputSecond, keyCode, stepDownKeyCode, label, directTarget, inputTarget,
                bindingPreset);
        if (config.containsKey("alias")) {
            setAlias(entry.id(), alias);
        }
        if (config.containsKey("value")) {
            setInput(entry.id(), requireNumber(config.get("value"), "value"));
        }
    }

    // Apply the entry
    private void applyEntry(CustomKeyEntry entry, AnalogueChannelMode mode, double riseRate, double fallRate,
                            double stepAmount, double stepDownAmount, double deadzone, double smoothing,
                            Direction localSide, ItemStack first, ItemStack second,
                            ItemStack inputFirst, ItemStack inputSecond, int keyCode, int stepDownKeyCode,
                            String label, ControllerDirectTargetReference directTarget,
                            ControllerDirectTargetReference inputTarget, String bindingPreset) {
        blockEntity.applyCustomKeyEntry(entry.id(), keyCode, stepDownKeyCode, label, mode, riseRate, fallRate,
                stepAmount, stepDownAmount, deadzone, smoothing, localSide, first, second, inputFirst, inputSecond,
                directTarget, inputTarget, bindingPreset);
    }

    // Get the require entry
    private CustomKeyEntry requireEntry(String idOrLabel) throws LuaException {
        return requireEntryMatch(idOrLabel).entry();
    }

    // Get the require entry match
    private EntryMatch requireEntryMatch(String idOrLabel) throws LuaException {
        EntryMatch match = findEntryMatch(idOrLabel);
        if (match == null) {
            throw new LuaException("unknown input '" + idOrLabel + "'");
        }
        return match;
    }

    // Find the entry
    private CustomKeyEntry findEntry(String idOrLabel) {
        EntryMatch match = findEntryMatch(idOrLabel);
        return match == null ? null : match.entry();
    }

    // Find the entry match
    private EntryMatch findEntryMatch(String idOrLabel) {
        String raw = idOrLabel == null ? "" : idOrLabel.trim();
        String normalized = raw.toLowerCase(Locale.ROOT);
        Integer requestedKeyCode = resolveKeyCode(raw);
        EntryMatch aliasMatch = null;
        EntryMatch defaultAliasMatch = null;
        EntryMatch labelMatch = null;
        EntryMatch keyMatch = null;
        EntryMatch stepDownKeyMatch = null;
        for (CustomKeyEntry entry : blockEntity.getCustomKeyEntries()) {
            if (Objects.equals(entry.id(), raw)) {
                return new EntryMatch(entry, false);
            }
            String alias = entry.alias == null ? "" : entry.alias.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && normalized.equals(alias)) {
                aliasMatch = new EntryMatch(entry, false);
            }
            String defaultAlias = defaultAlias(entry).toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && normalized.equals(defaultAlias)) {
                defaultAliasMatch = new EntryMatch(entry, false);
            }
            String label = entry.label == null ? "" : entry.label.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && normalized.equals(label)) {
                labelMatch = new EntryMatch(entry, false);
            }
            if (requestedKeyCode != null) {
                if (entry.keyCode == requestedKeyCode) {
                    keyMatch = new EntryMatch(entry, false);
                } else if (entry.stepDownKeyCode == requestedKeyCode) {
                    stepDownKeyMatch = new EntryMatch(entry, true);
                }
            }
        }
        if (aliasMatch != null) {
            return aliasMatch;
        }
        if (defaultAliasMatch != null) {
            return defaultAliasMatch;
        }
        if (labelMatch != null) {
            return labelMatch;
        }
        return keyMatch != null ? keyMatch : stepDownKeyMatch;
    }

    // Find the entry by alias or id
    private CustomKeyEntry findEntryByAliasOrId(String idOrAlias) {
        return findEntry(idOrAlias);
    }

    // Get the require dispatched targets
    private static int requireDispatchedTargets(int targets, String label) throws LuaException {
        if (targets <= 0) {
            throw new LuaException("no controller inputs are bound to " + label);
        }
        return targets;
    }

    // Find the entries by key code
    private List<EntryMatch> findEntriesByKeyCode(int keyCode) {
        List<EntryMatch> matches = new java.util.ArrayList<>();
        for (CustomKeyEntry entry : blockEntity.getCustomKeyEntries()) {
            if (entry.keyCode == keyCode) {
                matches.add(new EntryMatch(entry, false));
            }
            if (entry.stepDownKeyCode == keyCode) {
                matches.add(new EntryMatch(entry, true));
            }
        }
        return matches;
    }

    // Describe the bound key
    private Map<String, Object> describeBoundKey(int keyCode, String requestedLabel) throws LuaException {
        if (keyCode < 0) {
            throw new LuaException("unknown input bound to key '" + requestedLabel + "'");
        }
        List<Map<String, Object>> targets = new ArrayList<>();
        for (AnalogueControlChannel channel : AnalogueControlChannel.values()) {
            String channelId = channel.id();
            if (blockEntity.getKeyBinding(channelId) == keyCode) {
                targets.add(describeStandardKeyTarget(channel, keyCode));
            }
        }
        for (EntryMatch match : findEntriesByKeyCode(keyCode)) {
            targets.add(describeCustomKeyTarget(match.entry(), match.stepDown()));
        }
        if (targets.isEmpty()) {
            throw new LuaException("unknown input bound to key '" + requestedLabel + "'");
        }

        double val = 0.0D;
        int redstone = 0;
        boolean active = false;
        boolean pressed = false;
        List<String> targetIds = new ArrayList<>();
        for (Map<String, Object> target : targets) {
            val = Math.max(val, numberValue(target.get("value")));
            redstone = Math.max(redstone, (int) numberValue(target.get("redstone")));
            active |= booleanValue(target.get("active"));
            pressed |= booleanValue(target.get("pressed"));
            Object id = target.get("id");
            if (id instanceof String idString && !idString.isBlank()) {
                targetIds.add(idString);
            }
        }

        String keyName = keyName(keyCode);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", "key:" + keyCode);
        info.put("alias", keyName);
        info.put("defaultAlias", keyName);
        info.put("label", keyName);
        info.put("key", keyName);
        info.put("keyCode", keyCode);
        info.put("targetCount", targets.size());
        info.put("targetIds", targetIds);
        info.put("targets", targets);
        info.put("active", active);
        info.put("pressed", pressed);
        info.put("value", val);
        info.put("redstone", redstone);
        info.put("controlledState", stateTable(val, redstone, active, pressed, targets.size()));
        return info;
    }

    // Describe the standard key target
    private Map<String, Object> describeStandardKeyTarget(AnalogueControlChannel channel, int keyCode) {
        String channelId = channel.id();
        Map<String, Object> info = new LinkedHashMap<>(blockEntity.describeChannel(channelId));
        double val = blockEntity.getChannelValue(channelId);
        int redstone = blockEntity.getChannelRedstoneStrength(channelId);
        boolean pressed = blockEntity.isChannelPressed(channelId);
        boolean active = blockEntity.isChannelActive(channelId);
        info.put("type", "channel");
        info.put("id", channelId);
        info.put("alias", channelId);
        info.put("defaultAlias", channelId);
        info.put("key", keyName(keyCode));
        info.put("keyCode", keyCode);
        info.put("active", active);
        info.put("pressed", pressed);
        info.put("value", val);
        info.put("redstone", redstone);
        info.put("controlledState", stateTable(val, redstone, active, pressed, 1));
        return info;
    }

    // Describe the custom key target
    private Map<String, Object> describeCustomKeyTarget(CustomKeyEntry entry, boolean stepDown) {
        Map<String, Object> info = new LinkedHashMap<>(describeEntry(entry));
        double val = blockEntity.getCustomEntryValue(entry.id());
        int redstone = toRedstone(val);
        boolean active = blockEntity.isCustomEntryActive(entry.id());
        boolean pressed = stepDown
                ? blockEntity.isCustomEntryStepDownPressed(entry.id())
                : blockEntity.isCustomEntryMainPressed(entry.id());
        info.put("type", "custom_input");
        info.put("stepDown", stepDown);
        info.put("key", keyName(stepDown ? entry.stepDownKeyCode : entry.keyCode));
        info.put("keyCode", stepDown ? entry.stepDownKeyCode : entry.keyCode);
        info.put("active", active);
        info.put("pressed", pressed);
        info.put("value", val);
        info.put("redstone", redstone);
        info.put("controlledState", stateTable(val, redstone, active, pressed, 1));
        return info;
    }

    // Check if the key matches the tap entry
    private boolean tapKeyMatch(EntryMatch match) {
        if (match.stepDown()) {
            return blockEntity.tapCustomEntryStepDown(match.entry().id());
        }
        return blockEntity.tapCustomEntry(match.entry().id());
    }

    // Describe the entry
    private Map<String, Object> describeEntry(CustomKeyEntry entry) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", entry.id());
        info.put("alias", resolvedAlias(entry));
        info.put("customAlias", entry.alias == null ? "" : entry.alias);
        info.put("defaultAlias", defaultAlias(entry));
        info.put("label", entry.label);
        info.put("key", keyName(entry.keyCode));
        info.put("keyCode", entry.keyCode);
        info.put("stepDownKey", keyName(entry.stepDownKeyCode));
        info.put("stepDownKeyCode", entry.stepDownKeyCode);
        info.put("mode", entry.mode == null ? "ramp" : entry.mode.name().toLowerCase(Locale.ROOT));
        info.put("riseRate", entry.riseRate);
        info.put("fallRate", entry.fallRate);
        info.put("stepAmount", entry.stepAmount);
        info.put("stepDownAmount", entry.stepDownAmount);
        info.put("deadzone", entry.deadzone);
        info.put("smoothing", entry.smoothing);
        info.put("localSide", entry.localOutputSide == null ? "" : entry.localOutputSide.getSerializedName());
        info.put("frequencyA", itemId(entry.first));
        info.put("frequencyB", itemId(entry.second));
        info.put("inputFrequencyA", itemId(entry.inputFirst));
        info.put("inputFrequencyB", itemId(entry.inputSecond));
        info.put("directTarget", entry.directTarget == null ? Map.of() : describeDirectTarget(entry.directTarget));
        info.put("inputTarget", entry.inputTarget == null ? Map.of() : describeDirectTarget(entry.inputTarget));
        double val = blockEntity.getCustomEntryValue(entry.id());
        int redstone = toRedstone(val);
        boolean active = blockEntity.isCustomEntryActive(entry.id());
        boolean pressed = blockEntity.isCustomEntryPressed(entry.id());
        info.put("active", active);
        info.put("pressed", pressed);
        info.put("value", val);
        info.put("redstone", redstone);
        info.put("controlledState", stateTable(val, redstone, active, pressed, 1));
        info.put("bindingPreset", entry.bindingPreset == null ? "none" : entry.bindingPreset);
        return info;
    }

    // Get the state table
    private static Map<String, Object> stateTable(double val, int redstone, boolean active, boolean pressed, int targetCount) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("active", active);
        state.put("pressed", pressed);
        state.put("value", val);
        state.put("redstone", redstone);
        state.put("targetCount", targetCount);
        return state;
    }

    // Read the numeric value
    private static double numberValue(Object val) {
        return val instanceof Number num ? num.doubleValue() : 0.0D;
    }

    // Resolve the boolean value
    private static boolean booleanValue(Object val) {
        return val instanceof Boolean bool && bool;
    }

    // Convert the analogue contraption controller peripheral to redstone
    private static int toRedstone(double val) {
        return Math.max(0, Math.min(15, (int) Math.round(Math.max(0.0D, Math.min(1.0D, val)) * 15.0D)));
    }

    // Get the resolved alias
    private static String resolvedAlias(CustomKeyEntry entry) {
        String alias = entry.alias == null ? "" : entry.alias.trim();
        return alias.isBlank() ? defaultAlias(entry) : alias;
    }

    // Create the default alias
    private static String defaultAlias(CustomKeyEntry entry) {
        String label = entry.label == null ? "" : entry.label.trim();
        if (!label.isBlank() && !"custom".equalsIgnoreCase(label)) {
            return label;
        }
        if (entry.keyCode >= 0) {
            return keyName(entry.keyCode);
        }
        if (entry.stepDownKeyCode >= 0) {
            return keyName(entry.stepDownKeyCode);
        }
        return entry.id();
    }

    // Get the require key code
    private static int requireKeyCode(Object val, String name) throws LuaException {
        if (val instanceof Number num) {
            return num.intValue();
        }
        if (val instanceof String stringValue) {
            Integer keyCode = resolveKeyCode(stringValue);
            if (keyCode != null) {
                return keyCode;
            }
        }
        throw new LuaException(name + " must be a key name or numeric key code");
    }

    // Resolve the key code
    private static Integer resolveKeyCode(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        String normalized = normalizeKeyName(val);
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
        }
        if (normalized.length() == 1) {
            char character = normalized.charAt(0);
            if (character >= 'a' && character <= 'z') {
                return (int) Character.toUpperCase(character);
            }
            if (character >= '0' && character <= '9') {
                return (int) character;
            }
        }
        return KEY_CODES.get(normalized);
    }

    // Handle key name
    private static String keyName(int keyCode) {
        if (keyCode < 0) {
            return "";
        }
        if (keyCode >= 65 && keyCode <= 90) {
            return Character.toString((char) ('a' + (keyCode - 65)));
        }
        if (keyCode >= 48 && keyCode <= 57) {
            return Character.toString((char) keyCode);
        }
        if (keyCode >= 290 && keyCode <= 314) {
            return "f" + (keyCode - 289);
        }
        return KEY_NAMES.getOrDefault(keyCode, "key_" + keyCode);
    }

    // Normalize the key name
    private static String normalizeKeyName(String val) {
        String normalized = val.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        if (normalized.startsWith("key.keyboard.")) {
            normalized = normalized.substring("key.keyboard.".length());
        }
        if (normalized.startsWith("keyboard.")) {
            normalized = normalized.substring("keyboard.".length());
        }
        if (normalized.startsWith("glfw_key_")) {
            normalized = normalized.substring("glfw_key_".length());
        }
        if (normalized.startsWith("glfw.")) {
            normalized = normalized.substring("glfw.".length());
        }
        if (normalized.startsWith("key_")) {
            normalized = normalized.substring("key_".length());
        }
        return normalized;
    }

    // Create the key codes
    private static Map<String, Integer> createKeyCodes() {
        Map<String, Integer> keys = new HashMap<>();
        registerKey(keys, 32, "space");
        registerKey(keys, 39, "apostrophe", "quote");
        registerKey(keys, 44, "comma");
        registerKey(keys, 45, "minus", "dash");
        registerKey(keys, 46, "period", "dot");
        registerKey(keys, 47, "slash");
        registerKey(keys, 59, "semicolon");
        registerKey(keys, 61, "equal", "equals");
        registerKey(keys, 91, "left_bracket", "lbracket");
        registerKey(keys, 92, "backslash");
        registerKey(keys, 93, "right_bracket", "rbracket");
        registerKey(keys, 96, "grave_accent", "grave", "backtick");
        registerKey(keys, 256, "escape", "esc");
        registerKey(keys, 257, "enter", "return");
        registerKey(keys, 258, "tab");
        registerKey(keys, 259, "backspace");
        registerKey(keys, 260, "insert", "ins");
        registerKey(keys, 261, "delete", "del");
        registerKey(keys, 262, "right", "right_arrow");
        registerKey(keys, 263, "left", "left_arrow");
        registerKey(keys, 264, "down", "down_arrow");
        registerKey(keys, 265, "up", "up_arrow");
        registerKey(keys, 266, "page_up", "pageup");
        registerKey(keys, 267, "page_down", "pagedown");
        registerKey(keys, 268, "home");
        registerKey(keys, 269, "end");
        registerKey(keys, 280, "caps_lock", "capslock");
        registerKey(keys, 281, "scroll_lock", "scrolllock");
        registerKey(keys, 282, "num_lock", "numlock");
        registerKey(keys, 283, "print_screen", "printscreen");
        registerKey(keys, 284, "pause");
        for (int i = 1; i <= 25; i++) {
            registerKey(keys, 289 + i, "f" + i);
        }
        for (int i = 0; i <= 9; i++) {
            registerKey(keys, 320 + i, "kp_" + i, "numpad_" + i);
        }
        registerKey(keys, 330, "kp_decimal", "numpad_decimal");
        registerKey(keys, 331, "kp_divide", "numpad_divide");
        registerKey(keys, 332, "kp_multiply", "numpad_multiply");
        registerKey(keys, 333, "kp_subtract", "numpad_subtract");
        registerKey(keys, 334, "kp_add", "numpad_add");
        registerKey(keys, 335, "kp_enter", "numpad_enter");
        registerKey(keys, 336, "kp_equal", "numpad_equal");
        registerKey(keys, 340, "left_shift", "lshift", "shift");
        registerKey(keys, 341, "left_control", "left_ctrl", "lcontrol", "lctrl", "control", "ctrl");
        registerKey(keys, 342, "left_alt", "lalt", "alt");
        registerKey(keys, 343, "left_super", "left_windows", "lwin");
        registerKey(keys, 344, "right_shift", "rshift");
        registerKey(keys, 345, "right_control", "right_ctrl", "rcontrol", "rctrl");
        registerKey(keys, 346, "right_alt", "ralt");
        registerKey(keys, 347, "right_super", "right_windows", "rwin");
        registerKey(keys, 348, "menu");
        return Map.copyOf(keys);
    }

    // Create the key names
    private static Map<Integer, String> createKeyNames() {
        Map<Integer, String> names = new HashMap<>();
        registerKeyName(names, 32, "space");
        registerKeyName(names, 39, "apostrophe");
        registerKeyName(names, 44, "comma");
        registerKeyName(names, 45, "minus");
        registerKeyName(names, 46, "period");
        registerKeyName(names, 47, "slash");
        registerKeyName(names, 59, "semicolon");
        registerKeyName(names, 61, "equal");
        registerKeyName(names, 91, "left_bracket");
        registerKeyName(names, 92, "backslash");
        registerKeyName(names, 93, "right_bracket");
        registerKeyName(names, 96, "grave_accent");
        registerKeyName(names, 256, "escape");
        registerKeyName(names, 257, "enter");
        registerKeyName(names, 258, "tab");
        registerKeyName(names, 259, "backspace");
        registerKeyName(names, 260, "insert");
        registerKeyName(names, 261, "delete");
        registerKeyName(names, 262, "right");
        registerKeyName(names, 263, "left");
        registerKeyName(names, 264, "down");
        registerKeyName(names, 265, "up");
        registerKeyName(names, 266, "page_up");
        registerKeyName(names, 267, "page_down");
        registerKeyName(names, 268, "home");
        registerKeyName(names, 269, "end");
        registerKeyName(names, 280, "caps_lock");
        registerKeyName(names, 281, "scroll_lock");
        registerKeyName(names, 282, "num_lock");
        registerKeyName(names, 283, "print_screen");
        registerKeyName(names, 284, "pause");
        for (int i = 0; i <= 9; i++) {
            registerKeyName(names, 320 + i, "kp_" + i);
        }
        registerKeyName(names, 330, "kp_decimal");
        registerKeyName(names, 331, "kp_divide");
        registerKeyName(names, 332, "kp_multiply");
        registerKeyName(names, 333, "kp_subtract");
        registerKeyName(names, 334, "kp_add");
        registerKeyName(names, 335, "kp_enter");
        registerKeyName(names, 336, "kp_equal");
        registerKeyName(names, 340, "left_shift");
        registerKeyName(names, 341, "left_control");
        registerKeyName(names, 342, "left_alt");
        registerKeyName(names, 343, "left_super");
        registerKeyName(names, 344, "right_shift");
        registerKeyName(names, 345, "right_control");
        registerKeyName(names, 346, "right_alt");
        registerKeyName(names, 347, "right_super");
        registerKeyName(names, 348, "menu");
        return Map.copyOf(names);
    }

    // Register the key
    private static void registerKey(Map<String, Integer> keys, int code, String... names) {
        for (String name : names) {
            keys.put(name, code);
        }
    }

    // Register the key name
    private static void registerKeyName(Map<Integer, String> names, int code, String name) {
        names.put(code, name);
    }

    // Store the entry match
    private record EntryMatch(CustomKeyEntry entry, boolean stepDown) {
    }

    // Get the require number
    private static double requireNumber(Object val, String name) throws LuaException {
        if (!(val instanceof Number num)) {
            throw new LuaException(name + " must be a number");
        }
        double parsed = num.doubleValue();
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            throw new LuaException(name + " must be a finite number");
        }
        return parsed;
    }

    // Get the require string
    private static String requireString(Object val, String name) throws LuaException {
        if (!(val instanceof String stringValue)) {
            throw new LuaException(name + " must be a string");
        }
        return stringValue;
    }

    // Parse the mode
    private static AnalogueChannelMode parseMode(String mode) throws LuaException {
        if (mode == null || mode.isBlank()) {
            throw new LuaException("mode must be one of momentary, ramp, step, latch, direct");
        }
        try {
            return AnalogueChannelMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException err) {
            throw new LuaException("mode must be one of momentary, ramp, step, latch, direct");
        }
    }

    // Parse the direction
    private static Direction parseDirection(String val, String name) throws LuaException {
        Direction dir = Direction.byName(requireString(val, name));
        if (dir == null) {
            throw new LuaException(name + " must be north, south, east, west, up, or down");
        }
        return dir;
    }

    // Parse the direction or return no value
    private static Direction parseDirectionOrEmpty(Object val, String name) throws LuaException {
        if (val == null) {
            return null;
        }
        String stringValue = requireString(val, name);
        if (stringValue.isBlank()) {
            return null;
        }
        Direction dir = Direction.byName(stringValue);
        if (dir == null) {
            throw new LuaException(name + " must be empty, north, south, east, west, up, or down");
        }
        return dir;
    }

    // Parse the direct target
    private static ControllerDirectTargetReference parseDirectTarget(Object val, String name) throws LuaException {
        if (val == null) {
            return null;
        }
        if (!(val instanceof Map<?, ?> map)) {
            throw new LuaException(name + " must be a table");
        }
        if (map.isEmpty()) {
            return null;
        }
        String targetId = optionalString(map.get("targetId"));
        if (targetId.isBlank()) {
            return null;
        }
        UUID subLevelId = null;
        String subLevel = optionalString(map.get("subLevelId"));
        if (!subLevel.isBlank()) {
            try {
                subLevelId = UUID.fromString(subLevel);
            } catch (IllegalArgumentException err) {
                throw new LuaException(name + ".subLevelId must be a UUID string");
            }
        }
        return new ControllerDirectTargetReference(
                targetId,
                optionalString(map.get("targetTypeId")),
                optionalString(map.get("groupId")),
                optionalString(map.get("label")),
                subLevelId,
                parseBlockPos(map.get("blockPos"), name + ".blockPos"));
    }

    // Parse the block pos
    private static BlockPos parseBlockPos(Object val, String name) throws LuaException {
        if (val == null) {
            return null;
        }
        if (val instanceof String stringValue) {
            String[] parts = stringValue.trim().split("[, ]+");
            if (parts.length != 3) {
                throw new LuaException(name + " must be 'x y z'");
            }
            return new BlockPos(parseInt(parts[0], name), parseInt(parts[1], name), parseInt(parts[2], name));
        }
        if (val instanceof Map<?, ?> map) {
            return new BlockPos(
                    (int) requireNumber(map.get("x"), name + ".x"),
                    (int) requireNumber(map.get("y"), name + ".y"),
                    (int) requireNumber(map.get("z"), name + ".z"));
        }
        throw new LuaException(name + " must be a string or table with x, y, z");
    }

    // Parse the int
    private static int parseInt(String val, String name) throws LuaException {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException err) {
            throw new LuaException(name + " must contain integer coordinates");
        }
    }

    // Get the optional string
    private static String optionalString(Object val) {
        return val instanceof String stringValue ? stringValue.trim() : "";
    }

    // Describe the direct target
    private static Map<String, Object> describeDirectTarget(ControllerDirectTargetReference directTarget) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("targetId", directTarget.targetId());
        description.put("targetTypeId", directTarget.targetTypeId());
        description.put("groupId", directTarget.groupId());
        description.put("label", directTarget.label());
        description.put("subLevelId", directTarget.subLevelId() == null ? "" : directTarget.subLevelId().toString());
        description.put("blockPos", directTarget.blockPos() == null ? "" : directTarget.blockPos().toShortString());
        return description;
    }

    // Require the unit value
    private static void requireUnitValue(double val, String name) throws LuaException {
        if (val < 0.0D || val > 1.0D) {
            throw new LuaException(name + " must be between 0.0 and 1.0");
        }
    }

    // Parse the frequency
    private ItemStack parseFrequency(String itemId) throws LuaException {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location;
        try {
            location = ResourceLocation.parse(itemId.trim());
        } catch (Exception err) {
            throw new LuaException("invalid item id '" + itemId + "'");
        }
        Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        if (item == null) {
            throw new LuaException("unknown item id '" + itemId + "'");
        }
        return new ItemStack(item);
    }

    // Get the item id
    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.getItemHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
    }
}
