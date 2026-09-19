package com.rieno.gadgetsandgizmos.content.advanced.runtime;

import java.util.Map;

public interface AdvancedGraphCatalogExt {
    void register(String id, Map<String, String> inputs, Map<String, String> outputs,
                  boolean stateful, AdvancedGraphNodeExecutor executor);

    default NodeExtra register(String id, Map<String, String> inputs, Map<String, String> outputs,
                               AdvancedGraphNodeExecutor executor) {
        register(id,inputs,outputs,false,executor);
        return () -> id;
    }
}
