package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.AdvancedContraptionControllerPeripheralControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptArguments;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralDoc;
import com.rieno.gadgetsandgizmos.compat.computercraft.api.PeripheralTypeDoc;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Expose Advanced Contraption Controller controls and telemetry to ComputerCraft
@PeripheralTypeDoc("advanced_contraption_controller")
public class AdvancedContraptionControllerPeripheral extends AnalogueContraptionControllerPeripheral {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ComputerCraftPeripheralBridge peripheralBridge;
    private final AdvancedContraptionControllerPeripheralControl shared;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public AdvancedContraptionControllerPeripheral(AdvancedContraptionControllerBlockEntity blockEntity) {
        super(blockEntity, "advanced_contraption_controller");
        this.peripheralBridge = ComputerCraftPeripheralBridge.of(blockEntity);
        this.shared = new AdvancedContraptionControllerPeripheralControl(blockEntity);
    }

    @Override
    public Set<String> getAdditionalTypes(){ return shared.getAdditionalTypes(); }

    @Override
    public void attach(IComputerAccess computer){
        peripheralBridge.hub.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer){
        peripheralBridge.hub.detach(computer);
    }

    @LuaFunction
    @PeripheralDoc(name = "getPeripherals", signature = "getPeripherals(): table",
            description = "Maps linked target IDs such as thruster_0 to native names accepted by peripheral.wrap.")
    public final Map<String, String> getPeripherals(){
        return peripheralBridge.hub.getPeripherals();
    }

    @LuaFunction
    @PeripheralDoc(name = "peripheral", signature = "peripheral(id: string): string",
            description = "Returns the same native peripheral name as getPeripherals()[id]; errors for unavailable targets.")
    public final String peripheral(String id) throws LuaException{
        return peripheralBridge.hub.peripheral(id);
    }

    @LuaFunction
    @PeripheralDoc(name = "getNamesRemote", signature = "getNamesRemote(): table",
            description = "Lists native names for the ACC's currently available target peripherals.")
    public final List<String> getNamesRemote(){
        return peripheralBridge.hub.getNamesRemote();
    }

    @LuaFunction
    @PeripheralDoc(name = "isPresentRemote", signature = "isPresentRemote(name: string): boolean",
            description = "Checks whether a native target name is available through this ACC.")
    public final boolean isPresentRemote(String name){
        return peripheralBridge.hub.isPresentRemote(name);
    }

    @LuaFunction
    @PeripheralDoc(name = "getTypeRemote", signature = "getTypeRemote(name: string): string...|nil",
            description = "Returns all native types of an available target peripheral.")
    public final Object[] getTypeRemote(String name){
        return peripheralBridge.hub.getTypeRemote(name);
    }

    @LuaFunction
    @PeripheralDoc(name = "hasTypeRemote", signature = "hasTypeRemote(name: string, type: string): boolean|nil",
            description = "Checks the native types of an available target peripheral.")
    public final Boolean hasTypeRemote(String name, String type){
        return peripheralBridge.hub.hasTypeRemote(name, type);
    }

    @LuaFunction
    @PeripheralDoc(name = "getMethodsRemote", signature = "getMethodsRemote(name: string): table|nil",
            description = "Lists the target's native CC methods, including methods from other mods.")
    public final List<String> getMethodsRemote(String name){
        return peripheralBridge.hub.getMethodsRemote(name);
    }

    @LuaFunction
    @PeripheralDoc(name = "callRemote", signature = "callRemote(name: string, method: string, ...): any...",
            description = "Forwards a native peripheral call, preserving its arguments, return values and yields.")
    public final MethodResult callRemote(IComputerAccess computer, ILuaContext ctx, IArguments args) throws LuaException{
        return peripheralBridge.hub.callRemote(computer, ctx, args);
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphApiVersion", signature = "getGraphApiVersion(): number",
            description = "Returns the graph API version.")
    public final int getGraphApiVersion() { return shared.getGraphApiVersion(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listGraphApiMethods", signature = "listGraphApiMethods(): table",
            description = "Returns the graph API methods.")
    public final List<String> listGraphApiMethods() { return shared.listGraphApiMethods(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphApiHelp", signature = "getGraphApiHelp([method: string]): table|string",
            description = "Returns the graph API help.")
    public final Object getGraphApiHelp(Optional<String> method) throws LuaException {
        try{ return shared.getGraphApiHelp(method); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphStatus", signature = "getGraphStatus(): table",
            description = "Returns the graph status.")
    public final Map<String, Object> getGraphStatus() { return shared.getGraphStatus(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listGraphVariables", signature = "listGraphVariables(): table",
            description = "Returns the list graph variables.")
    public final List<String> listGraphVariables() { return shared.listGraphVariables(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphVariable", signature = "getGraphVariable(name: string): any",
            description = "Returns the graph variable.")
    public final Object getGraphVariable(String name) throws LuaException {
        try{ return shared.getGraphVariable(name); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "setGraphVariable", signature = "setGraphVariable(name: string, value: any)",
            description = "Sets the graph variable.")
    public final void setGraphVariable(String name, Object value) throws LuaException {
        try{ shared.setGraphVariable(name, value); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "publishNamedEvent", signature = "publishNamedEvent(name: string, data: any, [maximumDistance: number])",
            description = "Publishes a Named Event through ACC graphs, installed shared transports and optional CC rednet listeners.")
    public final void publishNamedEvent(String name, Object data,
                                        Optional<Integer> maximumDistance) throws LuaException {
        try{ shared.publishNamedEvent(name, data, maximumDistance); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "receiveNamedEvent", signature = "receiveNamedEvent(name: string, data: any)",
            description = "Delivers an external Named Event to this ACC without retransmitting it.")
    public final void receiveNamedEvent(String name, Object data) throws LuaException {
        try{ shared.receiveNamedEvent(name, data); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "triggerGraphEvent", signature = "triggerGraphEvent(eventId: string)",
            description = "Triggers the graph event.")
    public final void triggerGraphEvent(String eventId) { shared.triggerGraphEvent(eventId); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "tryTriggerGraphEvent", signature = "tryTriggerGraphEvent(eventId: string): boolean",
            description = "Attempts to trigger the graph event.")
    public final boolean tryTriggerGraphEvent(String eventId) { return shared.tryTriggerGraphEvent(eventId); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphDiagnostics", signature = "getGraphDiagnostics(): table",
            description = "Returns the graph diagnostics.")
    public final List<Map<String, String>> getGraphDiagnostics() { return shared.getGraphDiagnostics(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "validateDraft", signature = "validateDraft(): boolean",
            description = "Validates the draft.")
    public final boolean validateDraft() { return shared.validateDraft(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "applyDraft", signature = "applyDraft(): boolean",
            description = "Applies the draft.")
    public final boolean applyDraft() { return shared.applyDraft(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraph", signature = "getGraph([view: string]): table",
            description = "Returns one graph view.")
    public final Map<String, Object> getGraph(Optional<String> view) throws LuaException {
        try{ return shared.getGraph(view); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getGraphNodeOutputs", signature = "getGraphNodeOutputs(nodeIdOrAlias: string): table",
            description = "Returns every current public data output for one active root graph node, keyed by port name.")
    public final Map<String, Object> getGraphNodeOutputs(String nodeIdOrAlias) throws LuaException {
        try{ return shared.getGraphNodeOutputs(nodeIdOrAlias); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listGraphNodeTypes", signature = "listGraphNodeTypes(): table",
            description = "Returns every registered graph node type.")
    public final List<Map<String, Object>> listGraphNodeTypes() { return shared.listGraphNodeTypes(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "mutateGraph", signature = "mutateGraph(expectedRevision: number, operations: table): table",
            description = "Mutates the draft graph atomically.")
    public final Map<String, Object> mutateGraph(IArguments arguments) throws LuaException {
        try{ return shared.mutateGraph(new ScriptArguments(java.util.Arrays.asList(arguments.getAll()))); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "validateGraph", signature = "validateGraph(): table",
            description = "Validates the draft graph.")
    public final Map<String, Object> validateGraph() { return shared.validateGraph(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "applyGraph", signature = "applyGraph(expectedRevision: number): table",
            description = "Applies the draft graph when its revision still matches.")
    public final Map<String, Object> applyGraph(int expectedRevision) { return shared.applyGraph(expectedRevision); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "exportGraphLua", signature = "exportGraphLua(view: string, [options: table]): string",
            description = "Exports one graph view as bounded Lua source.")
    public final String exportGraphLua(
            String view,
            Optional<Map<?, ?>> options
    ) throws LuaException {
        try{ return shared.exportGraphLua(view, options); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getScheduleApiVersion", signature = "getScheduleApiVersion(): number",
            description = "Returns the SCM shipping schedule API version.")
    public final int getScheduleApiVersion() { return shared.getScheduleApiVersion(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listScheduleApiMethods", signature = "listScheduleApiMethods(): table",
            description = "Returns stable SCM shipping schedule API method signatures.")
    public final List<String> listScheduleApiMethods() { return shared.listScheduleApiMethods(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getScheduleApiHelp", signature = "getScheduleApiHelp([method: string]): table|string",
            description = "Returns all SCM shipping schedule API help or help for one method.")
    public final Object getScheduleApiHelp(Optional<String> method) throws LuaException {
        try{ return shared.getScheduleApiHelp(method); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getScheduleGraph", signature = "getScheduleGraph([view: string]): table",
            description = "Returns the controller-owned shipping schedule Scratch graph view.")
    public final Map<String, Object> getScheduleGraph(Optional<String> view) throws LuaException {
        try{ return shared.getScheduleGraph(view); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "listScheduleBlockTypes", signature = "listScheduleBlockTypes(): table",
            description = "Returns the available shipping schedule instruction, condition and flow block types.")
    public final List<Map<String, Object>> listScheduleBlockTypes() { return shared.listScheduleBlockTypes(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getScheduleStatus", signature = "getScheduleStatus(): table",
            description = "Returns SCM schedule availability, revisions, route state and live telemetry.")
    public final Map<String, Object> getScheduleStatus() { return shared.getScheduleStatus(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getScheduleBlockProperties", signature = "getScheduleBlockProperties(nodeId: string): table",
            description = "Returns the editable simple properties for one shipping schedule block.")
    public final Map<String, String> getScheduleBlockProperties(String nodeId) throws LuaException {
        try{ return shared.getScheduleBlockProperties(nodeId); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "getScheduleBlockInputs", signature = "getScheduleBlockInputs(nodeId: string): table",
            description = "Returns the item and frequency input slots for one shipping schedule block.")
    public final List<Map<String, Object>> getScheduleBlockInputs(String nodeId) throws LuaException {
        try{ return shared.getScheduleBlockInputs(nodeId); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "mutateScheduleGraph", signature = "mutateScheduleGraph(expectedRevision: number, operations: table): table",
            description = "Applies one bounded atomic shipping schedule mutation batch to the SCM-owned graph.")
    public final Map<String, Object> mutateScheduleGraph(IArguments arguments) throws LuaException {
        try{ return shared.mutateScheduleGraph(new ScriptArguments(java.util.Arrays.asList(arguments.getAll()))); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "validateScheduleGraph", signature = "validateScheduleGraph(): table",
            description = "Validates that the controller-owned schedule graph contains executable schedule steps.")
    public final Map<String, Object> validateScheduleGraph() { return shared.validateScheduleGraph(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "applyScheduleGraph", signature = "applyScheduleGraph(expectedRevision: number): table",
            description = "Confirms an SCM schedule revision; schedule saves are active immediately.")
    public final Map<String, Object> applyScheduleGraph(int expectedRevision) { return shared.applyScheduleGraph(expectedRevision); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "startSchedule", signature = "startSchedule(): boolean",
            description = "Starts or restarts the controller-owned schedule without requiring a schedule item.")
    public final boolean startSchedule() { return shared.startSchedule(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "pauseSchedule", signature = "pauseSchedule(): boolean",
            description = "Pauses the installed SCM shipping schedule.")
    public final boolean pauseSchedule() { return shared.pauseSchedule(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "resumeSchedule", signature = "resumeSchedule(): boolean",
            description = "Resumes the installed SCM shipping schedule.")
    public final boolean resumeSchedule() { return shared.resumeSchedule(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "stopSchedule", signature = "stopSchedule(): boolean",
            description = "Stops the installed SCM shipping schedule and resets it to its first step.")
    public final boolean stopSchedule() { return shared.stopSchedule(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "restartSchedule", signature = "restartSchedule(): boolean",
            description = "Restarts the installed SCM shipping schedule from its first step.")
    public final boolean restartSchedule() { return shared.restartSchedule(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "skipSchedule", signature = "skipSchedule(): boolean",
            description = "Skips the current SCM shipping schedule step.")
    public final boolean skipSchedule() { return shared.skipSchedule(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "readScheduleItem", signature = "readScheduleItem(): boolean",
            description = "Reads the adjacent pilot's held shipping schedule item into the SCM-owned graph.")
    public final boolean readScheduleItem() { return shared.readScheduleItem(); }

    @LuaFunction(mainThread = true)
    @PeripheralDoc(name = "writeScheduleItem", signature = "writeScheduleItem(): boolean",
            description = "Writes the SCM-owned schedule graph to the adjacent pilot's held shipping schedule item.")
    public final boolean writeScheduleItem() { return shared.writeScheduleItem(); }

}
