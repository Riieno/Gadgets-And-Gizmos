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

// Exercise pulsed inline assignment against a real server-side ACC
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class GraphVariableAssignmentGameTests{
    private GraphVariableAssignmentGameTests(){}

    @GameTest(template = "empty_container")
    public static void inlineSetterStoresTypedValuesOnEveryPulse(GameTestHelper ctx) throws Exception{
        BlockPos pos = ctx.absolutePos(new BlockPos(2,3,2));
        ctx.getLevel().setBlock(pos,CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(),3);
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getLevel().getBlockEntity(pos);
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        var runtime = (GraphRuntime)field.get(controller);
        for(var value : List.of(AdvancedGraphDocument.Value.number(42.5),AdvancedGraphDocument.Value.number(0),
                AdvancedGraphDocument.Value.bool(true),AdvancedGraphDocument.Value.bool(false),
                AdvancedGraphDocument.Value.string("Stored"),AdvancedGraphDocument.Value.string(""))){
            var graph = new AdvancedGraphDocument();
            graph.variables().put("test",AdvancedGraphDocument.Value.number(-12));
            var event = new AdvancedGraphDocument.Node("pulse","event_trigger","",0,0,AdvancedGraphNodeFactory.createDefaultData("event_trigger",null));
            event.data().putString("Event","variable_test");
            var setter = new AdvancedGraphDocument.Node("set","variable_set","",0,0,AdvancedGraphNodeFactory.createDefaultData("variable_set",null));
            setter.data().putString("Variable","test");
            CompoundTag defaults = new CompoundTag();defaults.put("value",value.toTag());defaults.put("default",AdvancedGraphDocument.Value.number(-12).toTag());setter.data().put("Defaults",defaults);
            CompoundTag inputs = new CompoundTag();inputs.putString("value",value.type());inputs.putString("default",value.type());setter.data().put("DynamicInputs",inputs);
            CompoundTag outputs = new CompoundTag();outputs.putString("value",value.type());setter.data().put("DynamicOutputs",outputs);
            graph.nodes().add(event);graph.nodes().add(setter);
            graph.edges().add(new AdvancedGraphDocument.Edge("pulse_to_set","pulse","exec","set","exec"));
            runtime.compile(graph);
            runtime.tick(graph,false);
            ctx.assertTrue(graph.variables().get("test").asNumber() == -12,"Inline assignment ran before a pulse");
            for(int pulse = 0; pulse < 3; pulse++){
                runtime.enqueue("variable_test");runtime.tick(graph,false);
                ctx.assertTrue(value.equals(graph.variables().get("test")),"Set Variable ignored inline " + value.type() + " on pulse " + pulse);
            }
            var restored = AdvancedGraphDocument.fromTag(graph.toTag());
            ctx.assertTrue(value.equals(restored.variables().get("test")),"Stored value was lost during graph serialization");
        }
        ctx.succeed();
    }
}
