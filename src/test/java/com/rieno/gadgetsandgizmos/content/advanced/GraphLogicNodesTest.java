package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.advanced.export.AdvancedGraphLuaExporter;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphLogicNodesTest{
    @Test void newGatesEvaluateInlineAndWiredBooleanInputs(){
        boolean[][] cases = {{false,false,false,true,true,true},{false,true,true,false,true,false},
                {true,false,true,false,true,false},{true,true,false,false,false,true}};
        var gates = List.of("xor","nor","nand","xnor");
        for(boolean wired : List.of(false,true)) for(boolean[] row : cases){
            var graph = new AdvancedGraphDocument();
            for(int idx = 0; idx < gates.size(); idx++){
                var gate = add(graph,gates.get(idx),gates.get(idx));
                defaults(gate,"a",AdvancedGraphDocument.Value.bool(wired ? !row[0] : row[0]));
                defaults(gate,"b",AdvancedGraphDocument.Value.bool(wired ? !row[1] : row[1]));
                if(wired){
                    var a = add(graph,"a/"+idx,"constant_boolean");a.data().putBoolean("Value",row[0]);
                    var b = add(graph,"b/"+idx,"constant_boolean");b.data().putBoolean("Value",row[1]);
                    edge(graph,a.id(),"value",gate.id(),"a");edge(graph,b.id(),"value",gate.id(),"b");
                }
            }
            var runtime = runtime(graph);runtime.beginPreviewSample(graph);
            for(int idx = 0; idx < gates.size(); idx++){
                var value = runtime.previewOutput(graph,node(graph,gates.get(idx)),"value");
                assertEquals("boolean",value.type());assertEquals(row[idx+2],value.asBoolean(),gates.get(idx));
            }
            assertTrue(runtime.diagnostics().isEmpty(),runtime.diagnostics().toString());
        }
    }

    @Test void latchAcknowledgesEachPulseAndHoldsStateBetweenPulses(){
        var graph = latchGraph(false);var runtime = runtime(graph);
        runtime.tick(graph,false);
        assertFalse(readState(runtime,graph));
        assertEquals(0,graph.variables().get("pulses").asNumber());
        boolean[] states = {true,true,false,false,true};
        for(int idx = 0; idx < states.length; idx++){
            runtime.enqueue(states[idx] ? "set" : "reset");runtime.tick(graph,false);
            assertEquals(states[idx],readState(runtime,graph));
            assertEquals(states[idx],graph.variables().get("observed").asBoolean());
            assertEquals(idx+1,graph.variables().get("pulses").asNumber());
            runtime.tick(graph,false);
            assertEquals(states[idx],readState(runtime,graph));
            assertEquals(idx+1,graph.variables().get("pulses").asNumber());
        }
        assertTrue(runtime.diagnostics().isEmpty(),runtime.diagnostics().toString());
    }

    @Test void multiplePulsesRefreshDerivedValuesInTheSameFrame(){
        var graph = latchGraph(true);var runtime = runtime(graph);
        runtime.enqueue("both");runtime.tick(graph,false);
        assertFalse(readState(runtime,graph));
        assertFalse(graph.variables().get("observed").asBoolean(),"A derived gate retained the earlier Set value");
        assertEquals(2,graph.variables().get("pulses").asNumber());
        assertTrue(runtime.diagnostics().isEmpty(),runtime.diagnostics().toString());
    }

    @Test void savedRuntimeRestoresLatchStateAndPreviewDoesNotChangeIt(){
        var graph = latchGraph(false);var runtime = runtime(graph);
        runtime.enqueue("set");runtime.tick(graph,false);
        CompoundTag snapshot = runtime.createShutdownSnapshot();
        for(int idx = 0; idx < 3; idx++) assertTrue(readState(runtime,graph));
        assertEquals(snapshot,runtime.createShutdownSnapshot());
        var copy = AdvancedGraphDocument.fromTag(graph.toTag());
        var restored = runtime(copy);restored.compile(copy);restored.restoreShutdownSnapshot(snapshot);
        assertTrue(readState(restored,copy));
        restored.enqueue("reset");restored.tick(copy,false);
        assertFalse(readState(restored,copy));
        assertEquals(2,copy.variables().get("pulses").asNumber());
    }

    @Test void latchSchemaAndGateNamesAreAvailableAndLuaExportSupportsThem(){
        var definition = AdvancedGraphCatalog.get("sr_latch");
        assertEquals(Map.of("set","exec","reset","exec"),definition.inputs());
        assertEquals(Map.of("exec","exec","state","boolean"),definition.outputs());
        assertTrue(definition.stateful());
        assertEquals("SR-Latch",AdvancedGraphCatalog.displayName("sr_latch"));
        for(String gate : List.of("xor","nor","nand","xnor")){
            assertEquals("logic",AdvancedGraphCatalog.get(gate).category());
            assertEquals(gate.toUpperCase(java.util.Locale.ROOT),AdvancedGraphCatalog.displayName(gate));
        }
        var graph = latchGraph(false);
        // Export a ticking source using the same Set/Reset execution wiring
        graph.nodes().removeIf(node -> "event_trigger".equals(node.type()));
        graph.edges().removeIf(edge -> edge.fromNode().equals("set") || edge.fromNode().equals("reset"));
        graph.nodes().removeIf(node -> "counter".equals(node.id()) || "count".equals(node.id()));
        graph.edges().removeIf(edge -> "counter".equals(edge.toNode()) || "counter".equals(edge.fromNode()) || "count".equals(edge.toNode()));
        add(graph,"tick","event_tick");edge(graph,"tick","exec","latch","set");
        for(String gate : List.of("xor","nor","nand","xnor")) add(graph,gate,gate);
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);when(controller.getActiveGraph()).thenReturn(graph);
        var exported = new AdvancedGraphLuaExporter(controller).export("active",Map.of("allowTimerApproximation",true));
        assertTrue(exported.success(),exported.diagnostics().toString());
        assertTrue(exported.source().contains("__latches[\"latch\"]"));
        assertTrue(exported.source().contains("__incoming == \"set\""));
    }

    @Test void functionCallsKeepIndependentLatchStatesAndRouteBothExecutionInputs(){
        var graph = new AdvancedGraphDocument();
        var function = new AdvancedGraphDocument.FunctionGraph("memory","Memory");
        var in = new AdvancedGraphDocument.Node("in",AdvancedGraphFunctions.INPUT_TYPE,"",0,0,new CompoundTag());
        CompoundTag inputs = new CompoundTag();inputs.putString("set","exec");inputs.putString("reset","exec");in.data().put("DynamicOutputs",inputs);
        var out = new AdvancedGraphDocument.Node("out",AdvancedGraphFunctions.OUTPUT_TYPE,"",0,0,new CompoundTag());
        CompoundTag outputs = new CompoundTag();outputs.putString("exec","exec");outputs.putString("state","boolean");out.data().put("DynamicInputs",outputs);
        var latch = new AdvancedGraphDocument.Node("memory","sr_latch","",0,0,AdvancedGraphNodeFactory.createDefaultData("sr_latch",null));
        function.nodes().addAll(List.of(in,latch,out));
        function.edges().addAll(List.of(new AdvancedGraphDocument.Edge("set","in","set","memory","set"),
                new AdvancedGraphDocument.Edge("reset","in","reset","memory","reset"),
                new AdvancedGraphDocument.Edge("done","memory","exec","out","exec"),
                new AdvancedGraphDocument.Edge("state","memory","state","out","state")));
        graph.functions().add(function);
        for(String id : List.of("a","b")){
            var call = add(graph,id,AdvancedGraphFunctions.CALL_TYPE);AdvancedGraphFunctions.configureCall(call,function);
            var observer = add(graph,id+"_observer","variable_set");observer.data().putString("Variable",id);
            graph.variables().put(id,AdvancedGraphDocument.Value.bool(false));
            edge(graph,id,"state",observer.id(),"value");edge(graph,id,"exec",observer.id(),"exec");
            for(String port : List.of("set","reset")){
                var trigger = add(graph,id+port,"event_trigger");trigger.data().putString("Event",id+port);
                edge(graph,trigger.id(),"exec",id,port);
            }
        }
        var runtime = runtime(graph);
        for(String event : List.of("aset","bset","areset","breset")){
            runtime.enqueue(event);runtime.tick(graph,false);
            boolean a = event.equals("aset") || event.equals("bset");
            boolean b = event.equals("bset") || event.equals("areset");
            assertEquals(a,graph.variables().get("a").asBoolean(),event);
            assertEquals(b,graph.variables().get("b").asBoolean(),event);
        }
        assertTrue(runtime.diagnostics().isEmpty(),runtime.diagnostics().toString());
        edge(graph,"a","exec","a","set");runtime.compile(graph);
        runtime.enqueue("aset");runtime.tick(graph,false);
        assertTrue(runtime.diagnostics().stream().anyMatch(diagnostic -> "recursive_execution_path".equals(diagnostic.code())),
                "A real execution cycle bypassed the recursion guard");
    }

    private static GraphRuntime runtime(AdvancedGraphDocument graph){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.getActiveGraph()).thenReturn(graph);
        when(controller.getLevel()).thenReturn(mock(net.minecraft.world.level.Level.class));
        return new GraphRuntime(controller);
    }

    private static boolean readState(GraphRuntime runtime,AdvancedGraphDocument graph){
        runtime.beginPreviewSample(graph);return runtime.previewOutput(graph,node(graph,"latch"),"state").asBoolean();
    }

    private static AdvancedGraphDocument latchGraph(boolean both){
        var graph = new AdvancedGraphDocument();
        add(graph,"latch","sr_latch");
        if(both){
            var trigger = add(graph,"both","event_trigger");trigger.data().putString("Event","both");
            edge(graph,"both","exec","latch","set");edge(graph,"both","exec","latch","reset");
        }else for(String input : List.of("set","reset")){
            var trigger = add(graph,input,"event_trigger");trigger.data().putString("Event",input);
            edge(graph,input,"exec","latch",input);
        }
        var derived = add(graph,"derived","xor");defaults(derived,"b",AdvancedGraphDocument.Value.bool(false));
        edge(graph,"latch","state","derived","a");
        var observer = add(graph,"observer","variable_set");observer.data().putString("Variable","observed");
        edge(graph,"derived","value","observer","value");edge(graph,"latch","exec","observer","exec");
        var counter = add(graph,"counter","count");defaults(counter,"step",AdvancedGraphDocument.Value.number(1));
        var count = add(graph,"count","variable_set");count.data().putString("Variable","pulses");
        CompoundTag inputs = count.data().getCompound("DynamicInputs");inputs.putString("value","number");count.data().put("DynamicInputs",inputs);
        CompoundTag outputs = count.data().getCompound("DynamicOutputs");outputs.putString("value","number");count.data().put("DynamicOutputs",outputs);
        edge(graph,"latch","exec","counter","exec");edge(graph,"counter","value","count","value");edge(graph,"counter","exec","count","exec");
        graph.variables().put("observed",AdvancedGraphDocument.Value.bool(false));
        graph.variables().put("pulses",AdvancedGraphDocument.Value.number(0));
        return graph;
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph,String id,String type){
        var node = new AdvancedGraphDocument.Node(id,type,"",0,0,AdvancedGraphNodeFactory.createDefaultData(type,null));graph.nodes().add(node);return node;
    }
    private static AdvancedGraphDocument.Node node(AdvancedGraphDocument graph,String id){return graph.nodes().stream().filter(node -> node.id().equals(id)).findFirst().orElseThrow();}
    private static void defaults(AdvancedGraphDocument.Node node,String port,AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");defaults.put(port,value.toTag());node.data().put("Defaults",defaults);
    }
    private static void edge(AdvancedGraphDocument graph,String from,String output,String to,String input){graph.edges().add(new AdvancedGraphDocument.Edge(from+output+to+input,from,output,to,input));}
}
