package com.rieno.gadgetsandgizmos.content.advanced.runtime;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;

import java.util.Map;

@FunctionalInterface
public interface GraphValueSink {
    default GraphValueSink output(String port, AdvancedGraphDocument.Value value){
        __outputRaw(port, value);
        return this;
    }
    default GraphValueSink output(Map<String, AdvancedGraphDocument.Value> map){
        map.forEach(this::__outputRaw);
        return this;
    }


    void __outputRaw(String port, AdvancedGraphDocument.Value value);


}
