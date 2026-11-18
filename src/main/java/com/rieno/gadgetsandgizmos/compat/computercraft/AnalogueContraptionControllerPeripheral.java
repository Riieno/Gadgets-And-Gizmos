package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.AnalogueContraptionControllerPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.GadgetsPeripheral;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerBlockEntity;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;

import java.util.List;
import java.util.Map;

// Expose safe controller configuration and live controls through the ComputerCraft peripheral API
@PeripheralTypeDoc("analogue_contraption_controller")
public class AnalogueContraptionControllerPeripheral
        extends GadgetsPeripheral<AnalogueContraptionControllerBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final AnalogueContraptionControllerPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public AnalogueContraptionControllerPeripheral(AnalogueContraptionControllerBlockEntity blockEntity) {
        this(blockEntity, "analogue_contraption_controller");
    }

    protected AnalogueContraptionControllerPeripheral(
            AnalogueContraptionControllerBlockEntity blockEntity,
            String peripheralType) {
        super(blockEntity, peripheralType);
        this.shared = new AnalogueContraptionControllerPeripheralControl(blockEntity, peripheralType);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getName", signature = "getName(): string",
            description = "Returns the name.")
    public final String getName() { return shared.getName(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setName", signature = "setName(name: string)",
            description = "Sets the name.")
    public final void setName(String name) { shared.setName(name); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listInputs", signature = "listInputs(): table",
            description = "Returns the list inputs.")
    public final List<Map<String, Object>> listInputs() { return shared.listInputs(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listInputIds", signature = "listInputIds(): table",
            description = "Returns the list input ids.")
    public final List<String> listInputIds() { return shared.listInputIds(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listChannels", signature = "listChannels(): table",
            description = "Returns the list channels.")
    public final List<Map<String, Object>> listChannels() { return shared.listChannels(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listAxes", signature = "listAxes(): table",
            description = "Returns the list axes.")
    public final List<String> listAxes() { return shared.listAxes(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "addInput", signature = "addInput(label: string): string",
            description = "Add the input.")
    public final String addInput(String label) { return shared.addInput(label); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAlias", signature = "getAlias(idOrAlias: string): string",
            description = "Returns the alias.")
    public final String getAlias(String idOrAlias) throws LuaException {
        try{ return shared.getAlias(idOrAlias); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputAlias", signature = "getInputAlias(idOrAlias: string): string",
            description = "Returns the input alias.")
    public final String getInputAlias(String idOrAlias) throws LuaException {
        try{ return shared.getInputAlias(idOrAlias); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setAlias", signature = "setAlias(idOrAlias: string, alias: string): string",
            description = "Sets the alias.")
    public final String setAlias(String idOrAlias, String alias) throws LuaException {
        try{ return shared.setAlias(idOrAlias, alias); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputAlias", signature = "setInputAlias(idOrAlias: string, alias: string): string",
            description = "Sets the input alias.")
    public final String setInputAlias(String idOrAlias, String alias) throws LuaException {
        try{ return shared.setInputAlias(idOrAlias, alias); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelAlias", signature = "setChannelAlias(idOrAlias: string, alias: string): string",
            description = "Sets the channel alias.")
    public final String setChannelAlias(String idOrAlias, String alias) throws LuaException {
        try{ return shared.setChannelAlias(idOrAlias, alias); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "addCustomEntry", signature = "addCustomEntry(): string",
            description = "Add the custom entry.")
    public final String addCustomEntry() { return shared.addCustomEntry(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "removeInput", signature = "removeInput(idOrLabel: string)",
            description = "Remove the input.")
    public final void removeInput(String idOrLabel) throws LuaException {
        try{ shared.removeInput(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "removeCustomEntry", signature = "removeCustomEntry(idOrLabel: string)",
            description = "Remove the custom entry.")
    public final void removeCustomEntry(String idOrLabel) throws LuaException {
        try{ shared.removeCustomEntry(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listCustomEntries", signature = "listCustomEntries(): table",
            description = "Returns the list custom entries.")
    public final List<Map<String, Object>> listCustomEntries() { return shared.listCustomEntries(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInput", signature = "getInput(idOrLabel: string): table",
            description = "Returns the input.")
    public final Map<String, Object> getInput(String idOrLabel) throws LuaException {
        try{ return shared.getInput(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputByKey", signature = "getInputByKey(key: string): table",
            description = "Returns the input by key.")
    public final Map<String, Object> getInputByKey(String key) throws LuaException {
        try{ return shared.getInputByKey(key); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputByKeyCode", signature = "getInputByKeyCode(keyCode: number): table",
            description = "Returns the input by key code.")
    public final Map<String, Object> getInputByKeyCode(int keyCode) throws LuaException {
        try{ return shared.getInputByKeyCode(keyCode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBoundKeyState", signature = "getBoundKeyState(key: string): table",
            description = "Returns the bound key state.")
    public final Map<String, Object> getBoundKeyState(String key) throws LuaException {
        try{ return shared.getBoundKeyState(key); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getBoundKeyCodeState", signature = "getBoundKeyCodeState(keyCode: number): table",
            description = "Returns the bound key code state.")
    public final Map<String, Object> getBoundKeyCodeState(int keyCode) throws LuaException {
        try{ return shared.getBoundKeyCodeState(keyCode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getChannel", signature = "getChannel(idOrLabel: string): table",
            description = "Returns the channel.")
    public final Map<String, Object> getChannel(String idOrLabel) throws LuaException {
        try{ return shared.getChannel(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCustomEntry", signature = "getCustomEntry(idOrLabel: string): table",
            description = "Returns the custom entry.")
    public final Map<String, Object> getCustomEntry(String idOrLabel) throws LuaException {
        try{ return shared.getCustomEntry(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputConfig", signature = "getInputConfig(idOrLabel: string): table",
            description = "Returns the input config.")
    public final Map<String, Object> getInputConfig(String idOrLabel) throws LuaException {
        try{ return shared.getInputConfig(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getChannelConfig", signature = "getChannelConfig(idOrLabel: string): table",
            description = "Returns the channel config.")
    public final Map<String, Object> getChannelConfig(String idOrLabel) throws LuaException {
        try{ return shared.getChannelConfig(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInput", signature = "setInput(idOrLabel: string, value: number)",
            description = "Sets the input.")
    public final void setInput(String idOrLabel, double value) throws LuaException {
        try{ shared.setInput(idOrLabel, value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannel", signature = "setChannel(idOrLabel: string, value: number)",
            description = "Sets the channel.")
    public final void setChannel(String idOrLabel, double value) throws LuaException {
        try{ shared.setChannel(idOrLabel, value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputByKey", signature = "setInputByKey(key: string, value: number): number",
            description = "Sets the input by key.")
    public final int setInputByKey(String key, double value) throws LuaException {
        try{ return shared.setInputByKey(key, value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputByKeyCode", signature = "setInputByKeyCode(keyCode: number, value: number): number",
            description = "Sets the input by key code.")
    public final int setInputByKeyCode(int keyCode, double value) throws LuaException {
        try{ return shared.setInputByKeyCode(keyCode, value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getInputValue", signature = "getInputValue(idOrLabel: string): number",
            description = "Returns the input value.")
    public final double getInputValue(String idOrLabel) throws LuaException {
        try{ return shared.getInputValue(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getCustomEntryValue", signature = "getCustomEntryValue(idOrLabel: string): number",
            description = "Returns the custom entry value.")
    public final double getCustomEntryValue(String idOrLabel) throws LuaException {
        try{ return shared.getCustomEntryValue(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getChannelMode", signature = "getChannelMode(idOrLabel: string): string",
            description = "Returns the channel mode.")
    public final String getChannelMode(String idOrLabel) throws LuaException {
        try{ return shared.getChannelMode(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelMode", signature = "setChannelMode(idOrLabel: string, mode: string)",
            description = "Sets the channel mode.")
    public final void setChannelMode(String idOrLabel, String mode) throws LuaException {
        try{ shared.setChannelMode(idOrLabel, mode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputConfig", signature = "setInputConfig(idOrLabel: string, config: table)",
            description = "Sets the input config.")
    public final void setInputConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        try{ shared.setInputConfig(idOrLabel, config); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelConfig", signature = "setChannelConfig(idOrLabel: string, config: table)",
            description = "Sets the channel config.")
    public final void setChannelConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        try{ shared.setChannelConfig(idOrLabel, config); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setCustomEntryConfig", signature = "setCustomEntryConfig(idOrLabel: string, config: table)",
            description = "Sets the custom entry config.")
    public final void setCustomEntryConfig(String idOrLabel, Map<?, ?> config) throws LuaException {
        try{ shared.setCustomEntryConfig(idOrLabel, config); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "press", signature = "press(idOrLabel: string)",
            description = "Press the analogue contraption controller peripheral.")
    public final void press(String idOrLabel) throws LuaException {
        try{ shared.press(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressInput", signature = "pressInput(idOrLabel: string)",
            description = "Press the input.")
    public final void pressInput(String idOrLabel) throws LuaException {
        try{ shared.pressInput(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressCustomEntry", signature = "pressCustomEntry(idOrLabel: string)",
            description = "Press the custom entry.")
    public final void pressCustomEntry(String idOrLabel) throws LuaException {
        try{ shared.pressCustomEntry(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressKey", signature = "pressKey(key: string): number",
            description = "Press the key.")
    public final int pressKey(String key) throws LuaException {
        try{ return shared.pressKey(key); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressKeyCode", signature = "pressKeyCode(keyCode: number): number",
            description = "Press the key code.")
    public final int pressKeyCode(int keyCode) throws LuaException {
        try{ return shared.pressKeyCode(keyCode); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressInputStepDown", signature = "pressInputStepDown(idOrLabel: string)",
            description = "Press the input step down.")
    public final void pressInputStepDown(String idOrLabel) throws LuaException {
        try{ shared.pressInputStepDown(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pressCustomEntryStepDown", signature = "pressCustomEntryStepDown(idOrLabel: string)",
            description = "Press the custom entry step down.")
    public final void pressCustomEntryStepDown(String idOrLabel) throws LuaException {
        try{ shared.pressCustomEntryStepDown(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "reset", signature = "reset(idOrLabel: string)",
            description = "Reset the analogue contraption controller peripheral.")
    public final void reset(String idOrLabel) throws LuaException {
        try{ shared.reset(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "resetInput", signature = "resetInput(idOrLabel: string)",
            description = "Reset the input.")
    public final void resetInput(String idOrLabel) throws LuaException {
        try{ shared.resetInput(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "resetAll", signature = "resetAll()",
            description = "Reset every analogue control signal.")
    public final void resetAll() { shared.resetAll(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isInputActive", signature = "isInputActive(idOrLabel: string): boolean",
            description = "Returns whether the input is active.")
    public final boolean isInputActive(String idOrLabel) throws LuaException {
        try{ return shared.isInputActive(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "isCustomEntryActive", signature = "isCustomEntryActive(idOrLabel: string): boolean",
            description = "Returns whether the custom entry is active.")
    public final boolean isCustomEntryActive(String idOrLabel) throws LuaException {
        try{ return shared.isCustomEntryActive(idOrLabel); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getLocalOutput", signature = "getLocalOutput(side: string): number",
            description = "Returns the local output.")
    public final int getLocalOutput(String side) throws LuaException {
        try{ return shared.getLocalOutput(side); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setLocalOutputSide", signature = "setLocalOutputSide(idOrLabel: string, side: string)",
            description = "Sets the local output side.")
    public final void setLocalOutputSide(String idOrLabel, String side) throws LuaException {
        try{ shared.setLocalOutputSide(idOrLabel, side); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelFrequency", signature = "setChannelFrequency(idOrLabel: string, first: string, second: string)",
            description = "Sets the channel frequency.")
    public final void setChannelFrequency(String idOrLabel, String first, String second) throws LuaException {
        try{ shared.setChannelFrequency(idOrLabel, first, second); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setInputFrequency", signature = "setInputFrequency(idOrLabel: string, first: string, second: string)",
            description = "Sets the input frequency.")
    public final void setInputFrequency(String idOrLabel, String first, String second) throws LuaException {
        try{ shared.setInputFrequency(idOrLabel, first, second); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelDirectTarget", signature = "setChannelDirectTarget(idOrLabel: string, target: table)",
            description = "Sets the channel direct target.")
    public final void setChannelDirectTarget(String idOrLabel, Map<?, ?> target) throws LuaException {
        try{ shared.setChannelDirectTarget(idOrLabel, target); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setChannelInputTarget", signature = "setChannelInputTarget(idOrLabel: string, target: table)",
            description = "Sets the channel input target.")
    public final void setChannelInputTarget(String idOrLabel, Map<?, ?> target) throws LuaException {
        try{ shared.setChannelInputTarget(idOrLabel, target); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAllSignals", signature = "getAllSignals(): table",
            description = "Returns all signals.")
    public final Map<String, Object> getAllSignals() { return shared.getAllSignals(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getAxis", signature = "getAxis(name: string): table",
            description = "Returns the axis.")
    public final Map<String, Object> getAxis(String name) throws LuaException {
        try{ return shared.getAxis(name); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

}
