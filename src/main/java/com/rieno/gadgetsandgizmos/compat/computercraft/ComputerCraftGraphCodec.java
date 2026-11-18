package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.script.ScriptGraphCodec;
import com.rieno.gadgetsandgizmos.lib.script.ScriptException;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeDefinition;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphDocumentSnapshot;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphEditResult;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphMutation;
import dan200.computercraft.api.lua.LuaException;

import java.util.List;
import java.util.Map;

// Bound every table crossing the CC and ACC graph boundary
public final class ComputerCraftGraphCodec {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        VARIABLES
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private ComputerCraftGraphCodec(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static Map<String, Object> snapshot(GraphDocumentSnapshot value) { return ScriptGraphCodec.snapshot(value); }

    public static List<Map<String, Object>> nodeTypes(
            List<GraphNodeDefinition> definitions) { return ScriptGraphCodec.nodeTypes(definitions); }

    public static Map<String, Object> result(GraphEditResult value) { return ScriptGraphCodec.result(value); }

    public static List<GraphMutation> mutations(Map<?, ?> table)
            throws LuaException {
        try{ return ScriptGraphCodec.mutations(table); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    public static Object graphValue(GraphValue value) { return ScriptGraphCodec.graphValue(value); }

    public static Object plainGraphValue(GraphValue value) { return ScriptGraphCodec.plainGraphValue(value); }

    public static GraphValue runtimeValue(Object raw) throws LuaException {
        try{ return ScriptGraphCodec.runtimeValue(raw); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

    public static GraphValue plainRuntimeValue(Object raw) throws LuaException {
        try{ return ScriptGraphCodec.plainRuntimeValue(raw); }
        catch(ScriptException ex){ throw new LuaException(ex.getMessage()); }
    }

}
