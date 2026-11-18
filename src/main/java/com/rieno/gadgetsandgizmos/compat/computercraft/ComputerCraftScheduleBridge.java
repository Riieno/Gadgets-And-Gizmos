package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.peripheral.ControllerScheduleControl;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.script.ScriptArguments;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Server-authoritative ComputerCraft surface for the SCM-owned shipping Schedule Scratch document.
// It deliberately exposes schedule operations rather than raw Create NBT, so programs can create
// schedules without coupling to Create implementation tags or client-only Scratch widgets.
final class ComputerCraftScheduleBridge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final ControllerScheduleControl shared;
    static final int API_VERSION = ControllerScheduleControl.API_VERSION;
    static final List<String> API_METHOD_NAMES = ControllerScheduleControl.API_METHOD_NAMES;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    ComputerCraftScheduleBridge(AdvancedContraptionControllerBlockEntity controller) {
        this.shared = new ControllerScheduleControl(controller);
    }

    Map<String, Object> graph(Optional<String> requestedView) throws LuaException {
        try{ return shared.graph(requestedView); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    List<Map<String, Object>> blockTypes() { return shared.blockTypes(); }

    Map<String, Object> status() { return shared.status(); }

    Map<String, String> blockProperties(String nodeId) throws LuaException {
        try{ return shared.blockProperties(nodeId); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    List<Map<String, Object>> blockInputs(String nodeId) throws LuaException {
        try{ return shared.blockInputs(nodeId); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    Map<String, Object> mutate(IArguments arguments) throws LuaException {
        try{ return shared.mutate(new ScriptArguments(java.util.Arrays.asList(arguments.getAll()))); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    Map<String, Object> validate() { return shared.validate(); }

    Map<String, Object> apply(int expectedRevision) { return shared.apply(expectedRevision); }

    boolean start() { return shared.start(); }

    boolean control(String command) { return shared.control(command); }

    boolean readItem() { return shared.readItem(); }

    boolean writeItem() { return shared.writeItem(); }

}
