package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider;

import java.util.List;
import java.util.Map;

// Provide external graph data
public interface AdvancedGraphDataProvider extends BlockEntityDataProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the graph readable data
    Map<String, String> graphReadableData();

    // Get the graph writable data
    Map<String, String> graphWritableData();

    // Get the graph writable options
    default Map<String, List<String>> graphWritableOptions() {
        return Map.of();
    }

    // Read the graph data
    AdvancedGraphDocument.Value readGraphData(String field);

    // Write the graph data
    boolean writeGraphData(String field, AdvancedGraphDocument.Value val);

    // Read one library graph value
    @Override
    default GraphValue readGraphValue(String field) {
        return GraphRuntime.toLibraryValue(readGraphData(field));
    }

    // Write one library graph value
    @Override
    default boolean writeGraphValue(String field, GraphValue val) {
        return writeGraphData(field, GraphRuntime.fromLibraryValue(val));
    }
}
