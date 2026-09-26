package com.rieno.gadgetsandgizmos.graph.eval;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.SneakyThrows;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;

public interface Ports {
    @SneakyThrows
    static Ports fromRecord(Record record) {
        Object2ObjectMap<String, Method> methods = new Object2ObjectOpenHashMap<>();
        Class<? extends Record> recordClass = record.getClass();
        for(RecordComponent component : recordClass.getRecordComponents()) {
            Method method1 = recordClass.getDeclaredMethod(component.getName());
            method1.setAccessible(true);
            methods.put(component.getName(), method1);
        }
        return new Ports() {
            @SneakyThrows
            @Override
            public <T> T get(String portName) {
                Method method = methods.get(portName);
                if(method == null) return null;
                return (T) method.invoke(record);
            }
        };
    }

    @SneakyThrows
    static Ports fromMap(Map<String, Object> map) {
        return new Ports() {
            @SneakyThrows
            @Override
            public <T> T get(String portName) {
                var value = map.get(portName);
                if(value == null) return null;
                return (T) value;
            }
        };
    }

    <T> T get(String portName);
}
