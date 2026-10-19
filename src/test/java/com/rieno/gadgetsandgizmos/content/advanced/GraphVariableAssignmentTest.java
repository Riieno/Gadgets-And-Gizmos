package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphVariableAssignmentTest{
    @Test void inlineValuesAreStoredAndReturnedForEverySupportedPayload(){
        CompoundTag map = new CompoundTag(); map.put("answer", AdvancedGraphDocument.Value.number(42).toTag());
        for(var value : List.of(AdvancedGraphDocument.Value.number(37.5), AdvancedGraphDocument.Value.number(0),
                AdvancedGraphDocument.Value.bool(true), AdvancedGraphDocument.Value.bool(false),
                AdvancedGraphDocument.Value.string("engaged"), AdvancedGraphDocument.Value.string(""),
                AdvancedGraphDocument.Value.map(map), AdvancedGraphDocument.Value.list(map),
                AdvancedGraphDocument.Value.direction("north"))){
            var fixture = new Fixture();
            var setter = fixture.setter("set", "test", fallback(value));
            defaults(setter,"value",value);
            fixture.getter("get","test");
            fixture.edge("tick","exec","set","exec");
            fixture.runtime.tick(fixture.graph,false);
            assertEquals(value,fixture.graph.variables().get("test"),value.type());
            fixture.runtime.beginPreviewSample(fixture.graph);
            assertEquals(value,fixture.runtime.previewOutput(fixture.graph,fixture.node("get"),"value"));
            assertEquals(value,fixture.runtime.liveOutputs().get("set:value"));
            assertTrue(fixture.runtime.diagnostics().isEmpty(),fixture.runtime.diagnostics().toString());
        }
    }

    @Test void aWireOverridesTheInlineValueAndLegacyDefaultStillWorks(){
        for(boolean wire : List.of(false,true)){
            var fixture = new Fixture();
            var setter = fixture.setter("set","test",AdvancedGraphDocument.Value.number(12));
            if(wire){
                defaults(setter,"value",AdvancedGraphDocument.Value.number(99));
                var constant = fixture.add("constant","constant_number");constant.data().putDouble("Value",0);
                fixture.edge("constant","value","set","value");
            }
            fixture.edge("tick","exec","set","exec");
            fixture.runtime.tick(fixture.graph,false);
            assertEquals(wire ? 0D : 12D,fixture.graph.variables().get("test").asNumber());
        }
    }

    @Test void inlineValueIsWrittenOnlyWhenPulsedAndSurvivesSerialization(){
        var fixture = new Fixture();
        var setter = fixture.setter("set","test",AdvancedGraphDocument.Value.number(-1));
        defaults(setter,"value",AdvancedGraphDocument.Value.number(24));
        fixture.getter("get","test");
        fixture.runtime.tick(fixture.graph,false);
        assertEquals(-1,fixture.graph.variables().get("test").asNumber());
        fixture.edge("tick","exec","set","exec");
        fixture.runtime.compile(fixture.graph);
        for(int pulse = 0; pulse < 3; pulse++) fixture.runtime.tick(fixture.graph,false);
        var restored = AdvancedGraphDocument.fromTag(fixture.graph.toTag());
        assertEquals(24,restored.variables().get("test").asNumber());
        var preview = new GraphRuntime(null,true);preview.beginPreviewSample(restored);
        var getter = restored.nodes().stream().filter(node->node.id().equals("get")).findFirst().orElseThrow();
        assertEquals(24,preview.previewOutput(restored,getter,"value").asNumber());
    }

    @Test void legacyInlinePropertyAndLuaExportUseTheSameAssignmentRule(){
        var fixture = new Fixture();
        var setter = fixture.setter("set","test",AdvancedGraphDocument.Value.number(12));
        setter.data().putDouble("Value",37.5);
        fixture.edge("tick","exec","set","exec");
        fixture.runtime.tick(fixture.graph,false);
        assertEquals(37.5,fixture.graph.variables().get("test").asNumber());
        var exporter = new com.rieno.gadgetsandgizmos.content.advanced.export.AdvancedGraphLuaExporter(fixture.controller);
        var result = exporter.export("active",java.util.Map.of("allowTimerApproximation",true));
        assertTrue(result.success(),result.diagnostics().toString());
        assertTrue(result.source().contains("local __newValue = 37.5"),result.source());
        defaults(setter,"value",AdvancedGraphDocument.Value.number(0));
        result = exporter.export("active",java.util.Map.of("allowTimerApproximation",true));
        assertTrue(result.success(),result.diagnostics().toString());
        assertTrue(result.source().contains("local __newValue = 0"),result.source());
    }

    private static AdvancedGraphDocument.Value fallback(AdvancedGraphDocument.Value value){
        return switch(value.type()){
            case "number" -> AdvancedGraphDocument.Value.number(-12);
            case "boolean" -> AdvancedGraphDocument.Value.bool(!value.asBoolean());
            case "string" -> AdvancedGraphDocument.Value.string("fallback");
            case "direction" -> AdvancedGraphDocument.Value.direction("south");
            case "map" -> AdvancedGraphDocument.Value.map(new CompoundTag());
            case "list" -> AdvancedGraphDocument.Value.list(new CompoundTag());
            default -> throw new AssertionError(value.type());
        };
    }

    private static void defaults(AdvancedGraphDocument.Node node,String port,AdvancedGraphDocument.Value value){
        CompoundTag data = node.data().getCompound("Defaults");data.put(port,value.toTag());node.data().put("Defaults",data);
        CompoundTag inputs = node.data().getCompound("DynamicInputs");inputs.putString(port,value.type());node.data().put("DynamicInputs",inputs);
    }

    private static final class Fixture{
        final AdvancedGraphDocument graph = new AdvancedGraphDocument();
        final GraphRuntime runtime;
        final AdvancedContraptionControllerBlockEntity controller;
        Fixture(){
            controller = mock(AdvancedContraptionControllerBlockEntity.class);
            when(controller.getActiveGraph()).thenReturn(graph);
            var level = mock(Level.class);when(controller.getLevel()).thenReturn(level);
            runtime = new GraphRuntime(controller);
            add("tick","event_tick");
        }
        AdvancedGraphDocument.Node add(String id,String type){
            var node = new AdvancedGraphDocument.Node(id,type,"",0,0,AdvancedGraphNodeFactory.createDefaultData(type,null));
            graph.nodes().add(node);return node;
        }
        AdvancedGraphDocument.Node setter(String id,String variable,AdvancedGraphDocument.Value value){
            var node = add(id,"variable_set");node.data().putString("Variable",variable);
            defaults(node,"default",value);
            graph.variables().putIfAbsent(variable,value);
            CompoundTag inputs = node.data().getCompound("DynamicInputs");inputs.putString("value",value.type());node.data().put("DynamicInputs",inputs);
            var outputs = node.data().getCompound("DynamicOutputs");outputs.putString("value",value.type());node.data().put("DynamicOutputs",outputs);
            return node;
        }
        void getter(String id,String variable){var node=add(id,"variable_get");node.data().putString("Variable",variable);}
        AdvancedGraphDocument.Node node(String id){return graph.nodes().stream().filter(node->node.id().equals(id)).findFirst().orElseThrow();}
        void edge(String from,String port,String to,String input){graph.edges().add(new AdvancedGraphDocument.Edge(from+port+to+input,from,port,to,input));}
    }
}
