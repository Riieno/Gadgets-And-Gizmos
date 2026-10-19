package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphLogicGameTests{
    private GraphLogicGameTests(){}

    @GameTest(template = "empty_container")
    public static void gatesAndLatchWorkOnAServerController(GameTestHelper ctx) throws Exception{
        BlockPos pos = ctx.absolutePos(new BlockPos(2,3,2));
        ctx.getLevel().setBlock(pos,CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(),3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        var runtime = (GraphRuntime)field.get(controller);
        boolean[][] cases = {{false,false,false,true,true,true},{false,true,true,false,true,false},
                {true,false,true,false,true,false},{true,true,false,false,false,true}};
        var gates = List.of("xor","nor","nand","xnor");
        for(boolean[] row : cases){
            var graph = new AdvancedGraphDocument();
            var event = add(graph,"event","event_trigger");event.data().putString("Event","logic");
            for(int idx = 0; idx < gates.size(); idx++){
                String id = gates.get(idx);var gate = add(graph,id,id);
                defaults(gate,"a",AdvancedGraphDocument.Value.bool(row[0]));defaults(gate,"b",AdvancedGraphDocument.Value.bool(row[1]));
                var setter = add(graph,id+"_set","variable_set");setter.data().putString("Variable",id);
                graph.variables().put(id,AdvancedGraphDocument.Value.bool(!row[idx+2]));
                edge(graph,"event","exec",setter.id(),"exec");edge(graph,id,"value",setter.id(),"value");
            }
            runtime.compile(graph);runtime.enqueue("logic");runtime.tick(graph,false);
            for(int idx = 0; idx < gates.size(); idx++) ctx.assertTrue(graph.variables().get(gates.get(idx)).asBoolean() == row[idx+2],"Incorrect truth table: "+gates.get(idx));
        }
        var graph = new AdvancedGraphDocument();add(graph,"latch","sr_latch");
        for(String port : List.of("set","reset")){
            var event = add(graph,port,"event_trigger");event.data().putString("Event",port);edge(graph,port,"exec","latch",port);
        }
        var observer = add(graph,"observer","variable_set");observer.data().putString("Variable","state");
        edge(graph,"latch","state","observer","value");edge(graph,"latch","exec","observer","exec");
        var counter = add(graph,"counter","count");defaults(counter,"step",AdvancedGraphDocument.Value.number(1));
        var count = add(graph,"count","variable_set");count.data().putString("Variable","pulses");
        CompoundTag inputs = count.data().getCompound("DynamicInputs");inputs.putString("value","number");count.data().put("DynamicInputs",inputs);
        CompoundTag outputs = count.data().getCompound("DynamicOutputs");outputs.putString("value","number");count.data().put("DynamicOutputs",outputs);
        edge(graph,"latch","exec","counter","exec");edge(graph,"counter","value","count","value");edge(graph,"counter","exec","count","exec");
        graph.variables().put("state",AdvancedGraphDocument.Value.bool(false));graph.variables().put("pulses",AdvancedGraphDocument.Value.number(0));
        runtime.compile(graph);runtime.tick(graph,false);
        boolean[] states = {true,true,false,false,true};
        for(int idx = 0; idx < states.length; idx++){
            runtime.enqueue(states[idx] ? "set" : "reset");runtime.tick(graph,false);
            ctx.assertTrue(graph.variables().get("state").asBoolean() == states[idx],"Latch failed on pulse "+idx);
            ctx.assertTrue(graph.variables().get("pulses").asNumber() == idx+1,"Latch did not acknowledge repeated Set/Reset");
            runtime.tick(graph,false);
            ctx.assertTrue(graph.variables().get("pulses").asNumber() == idx+1,"Latch fired without an input pulse");
        }
        ctx.assertTrue(runtime.diagnostics().isEmpty(),runtime.diagnostics().toString());ctx.succeed();
    }

    private static AdvancedGraphDocument.Node add(AdvancedGraphDocument graph,String id,String type){
        var node = new AdvancedGraphDocument.Node(id,type,"",0,0,AdvancedGraphNodeFactory.createDefaultData(type,null));graph.nodes().add(node);return node;
    }
    private static void defaults(AdvancedGraphDocument.Node node,String port,AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");defaults.put(port,value.toTag());node.data().put("Defaults",defaults);
    }
    private static void edge(AdvancedGraphDocument graph,String from,String output,String to,String input){graph.edges().add(new AdvancedGraphDocument.Edge(from+output+to+input,from,output,to,input));}
}
