package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.*;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerTargetMap;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class TargetMapGameTests{
    private TargetMapGameTests(){}

    @GameTest(template = "empty_container",batch = "target_maps",timeoutTicks = 100)
    public static void namedMapsDriveEveryTargetAndRepeatedZeroWrites(GameTestHelper ctx) throws Exception{
        BlockPos host = ctx.absolutePos(new BlockPos(2,3,2));
        var level = ctx.getLevel();level.setBlock(host,CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(),3);
        var controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(host);
        BlockPos first = ctx.absolutePos(new BlockPos(4,3,4)), second = ctx.absolutePos(new BlockPos(7,3,4));
        level.setBlock(first,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT,true),2);
        level.setBlock(second,Blocks.REDSTONE_LAMP.defaultBlockState(),2);
        var linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
        ContraptionNetworkLinkerData.writeTargets(linker,List.of(link(first),link(second)),ContraptionNetworkLinkerData.LinkMode.OUTPUT);
        controller.getLinkerSlotHandler().setStackInSlot(0,linker);
        var map = controller.findNamedGraphTarget("Bank");
        ctx.assertTrue("map".equals(map.type()) && ControllerTargetMap.read(map.payload()).size() == 2,"Duplicate names lost a binding");
        var graph = new AdvancedGraphDocument();
        var convert = node("convert","convert_type");convert.data().putString("OutputType","target");
        defaults(convert,"value",AdvancedGraphDocument.Value.string("Bank"));
        var setter = node("setter","set_block_data");
        setter.data().putString(GraphSignalRange.VALUE_TYPE_TAG,"redstone");
        var event = node("event","event_trigger");event.data().putString("Event","write");
        var number = node("number","variable_get");number.data().putString("Variable","signal");
        var getter = node("getter","get_block_data");
        graph.nodes().addAll(List.of(convert,setter,event,number,getter));
        edge(graph,"convert","value","setter","target");edge(graph,"convert","value","getter","target");
        edge(graph,"event","exec","setter","exec");
        edge(graph,"number","value","setter",GraphSignalRange.REDSTONE_SIGNAL_PORT);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        var runtime = (GraphRuntime)field.get(controller);
        runtime.compile(graph);
        for(int strength : List.of(15,0,7,0)){
            graph.variables().put("signal",AdvancedGraphDocument.Value.number(strength));
            runtime.enqueue("write");runtime.tick(graph,false);
            ctx.assertTrue(level.getBestNeighborSignal(first) == strength,"First mapped target did not receive " + strength);
            ctx.assertTrue(level.getBestNeighborSignal(second) == strength,"Second mapped target did not receive " + strength);
        }
        AdvancedGraphTargets.apply(level,setter,runtime.previewInput(graph,setter,"target"),graph.edges());
        ctx.assertTrue(setter.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG,10).size() == 2,"Runtime did not expand the target list");
        ctx.assertTrue(AdvancedGraphTargetSignals.ports(setter).size() == 2,"Runtime did not expand independent redstone ports");
        AdvancedGraphTargets.apply(level,getter,runtime.previewInput(graph,getter,"target"),graph.edges());
        runtime.compile(graph);
        var ports = getter.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG);
        int litPorts = 0;
        for(String port : ports.getAllKeys()){
            if(!"state_lit".equals(ports.getList(port,10).getCompound(0).getString("Port"))) continue;
            var value = runtime.previewOutput(graph,getter,port);
            ctx.assertTrue("boolean".equals(value.type()),"Mapped getter lost its block state type");litPorts++;
        }
        ctx.assertTrue(litPorts == 2,"Getter did not expose both targets' data ports");
        convert.data().getCompound("Defaults").put("value",AdvancedGraphDocument.Value.string("Missing").toTag());
        graph.variables().put("signal",AdvancedGraphDocument.Value.number(15));
        runtime.compile(graph);runtime.enqueue("write");runtime.tick(graph,false);
        ctx.assertTrue(level.getBestNeighborSignal(first) == 0 && level.getBestNeighborSignal(second) == 0,
                "Failed lookup wrote to stale targets");
        AdvancedGraphTargets.apply(level,setter,runtime.previewInput(graph,setter,"target"),graph.edges());
        ctx.assertTrue(setter.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG,10).isEmpty(),"Failed lookup retained stale targets");
        ctx.assertTrue(!setter.data().contains("TargetData"),"Failed lookup retained its primary target");
        convert.data().getCompound("Defaults").put("value",AdvancedGraphDocument.Value.string("Bank").toTag());
        var function = new AdvancedGraphDocument.FunctionGraph("bank","Bank");
        var input = node("function_input","function_input");var output = node("function_output","function_output");
        CompoundTag exec = new CompoundTag();exec.putString("exec","exec");
        input.data().put("DynamicOutputs",exec.copy());output.data().put("DynamicInputs",exec.copy());
        function.nodes().addAll(List.of(input,convert,setter,number,output));
        graph.edges().stream().filter(wire -> setter.id().equals(wire.toNode()) && !"exec".equals(wire.toPort()))
                .forEach(function.edges()::add);
        function.edges().add(new AdvancedGraphDocument.Edge("function_start",input.id(),"exec",setter.id(),"exec"));
        function.edges().add(new AdvancedGraphDocument.Edge("function_end",setter.id(),"exec",output.id(),"exec"));
        var called = new AdvancedGraphDocument();called.functions().add(function);
        var call = node("call","function_call");AdvancedGraphFunctions.configureCall(call,function);
        called.nodes().addAll(List.of(event,call));edge(called,"event","exec","call","exec");
        for(int strength : List.of(3,0)){
            called.variables().put("signal",AdvancedGraphDocument.Value.number(strength));
            runtime.enqueue("write");runtime.tick(called,false);
            ctx.assertTrue(level.getBestNeighborSignal(first) == strength && level.getBestNeighborSignal(second) == strength,
                    "Target map wiring was lost inside a function");
        }
        var functionTargets = controller.previewGraphInput(called,setter,"target");
        ctx.assertTrue("map".equals(functionTargets.type()) && ControllerTargetMap.read(functionTargets.payload()).size() == 2,
                "Function editor could not resolve its wired target map");
        ctx.succeed();
    }

    private static ContraptionNetworkLinkerData.LinkedTarget link(BlockPos pos){
        return new ContraptionNetworkLinkerData.LinkedTarget(pos,null,"minecraft:redstone_lamp","Bank",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT,ContraptionNetworkLinkerData.TargetScope.BLOCK,List.of());
    }
    private static AdvancedGraphDocument.Node node(String id,String type){
        return new AdvancedGraphDocument.Node(id,type,"",0,0,AdvancedGraphNodeFactory.createDefaultData(type,null));
    }
    private static void defaults(AdvancedGraphDocument.Node node,String port,AdvancedGraphDocument.Value value){
        CompoundTag defaults = node.data().getCompound("Defaults");defaults.put(port,value.toTag());node.data().put("Defaults",defaults);
    }
    private static void edge(AdvancedGraphDocument graph,String from,String output,String to,String input){
        graph.edges().add(new AdvancedGraphDocument.Edge(from + output + to + input,from,output,to,input));
    }
}
