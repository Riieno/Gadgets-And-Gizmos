package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;

public record EventNode(String restEvent,AdvancedGraphDocument.Node node) {
    public String id() {
        return node.id();
    }
}
