package com.rieno.gadgetsandgizmos.content.advanced.runtime;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphExecutionContext;
import com.rieno.gadgetsandgizmos.lib.graph.GraphNodeExecutor;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;

import java.util.HashMap;
import java.util.Map;

@FunctionalInterface
public interface AdvancedGraphNodeExecutor extends GraphNodeExecutor {

    // Run the graph node executor
    void executeAdvanced(
        GraphExecutionContext ctx,
        Map<String, AdvancedGraphDocument.Value> inputs,
        GraphValueSink sink
    );

    @Override
    default Map<String, GraphValue> execute(GraphExecutionContext ctx, Map<String, GraphValue> rawInputs) {
        HashMap<String, AdvancedGraphDocument.Value> map = new HashMap<>();
        rawInputs.forEach((s, graphValue) -> {
            map.put(s, GraphRuntime.fromLibraryValue(graphValue));
        });

        var rawOutput = new HashMap<String, GraphValue>();
        executeAdvanced(ctx, map,(port, value) -> {
            rawOutput.put(port,GraphRuntime.toLibraryValue(value));
        });
        return rawOutput;
    }
}
