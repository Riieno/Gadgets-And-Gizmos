package com.rieno.gadgetsandgizmos.graph.compile;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;

public class Handels {
    public static String event(AdvancedGraphDocument.Node node){
        return node.data().getString("Event");
    }

}
