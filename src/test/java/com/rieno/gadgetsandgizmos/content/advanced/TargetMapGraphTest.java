package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerTargetMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TargetMapGraphTest{
    @Test void duplicateNamesBecomeTypedMapsWhileSingleTargetPortsResolveTheFirst(){
        var first = linked(new BlockPos(1,2,3));var second = linked(new BlockPos(4,2,3));
        var targets = ContraptionNetworkLinkerData.findNamedTargets(List.of(second, first), " BANK ");
        assertEquals(2, targets.size());assertEquals(first.blockPos(), targets.getFirst().blockPos());
        var value = AdvancedGraphDocument.Value.map(ControllerTargetMap.encode(targets));
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        when(controller.findNamedGraphTarget("Bank")).thenReturn(value);
        var graph = new AdvancedGraphDocument();
        var convert = node("convert", "convert_type");convert.data().putString("OutputType", "target");
        CompoundTag defaults = new CompoundTag();defaults.put("value", AdvancedGraphDocument.Value.string("Bank").toTag());
        convert.data().put("Defaults", defaults);graph.nodes().add(convert);
        var multi = node("multi", "get_block_data");var single = node("single", "camera");
        graph.nodes().addAll(List.of(multi,single));
        graph.edges().add(new AdvancedGraphDocument.Edge("multi_wire", "convert", "value", "multi", "target"));
        graph.edges().add(new AdvancedGraphDocument.Edge("single_wire", "convert", "value", "single", "target"));
        var runtime = new GraphRuntime(controller);
        assertEquals("map", runtime.previewOutput(graph,convert,"value").type());
        assertEquals(value, runtime.previewInput(graph,multi,"target"));
        var selected = runtime.previewInput(graph,single,"target");
        assertEquals("target", selected.type());assertEquals(targets.getFirst().toTag(),selected.payload());
        assertTrue(AdvancedGraphCatalog.compatible("map","target"));
    }

    @Test void missingFaceDefaultsAreRepairedWithoutCrashingOrOverridingValidChoices(){
        var ship = UUID.randomUUID();var target = new ControllerDiscoveryNode("scm_picker:" + ship + ":0",
                ControllerDiscoveryKind.MACHINE,"scm_picker","minecraft:stone","Picked",ship,BlockPos.ZERO);
        for(String type : List.of("get_block_data","set_block_data")){
            var node = node("node",type);
            assertDoesNotThrow(() -> AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node,target));
            var directions = node.data().getCompound("InputOptions").getList("face",8);
            assertEquals(6,directions.size());
            var defaults = node.data().getCompound("Defaults");
            defaults.put("face",AdvancedGraphDocument.Value.direction("east").toTag());
            AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node,target);
            assertEquals("east",AdvancedGraphDocument.Value.fromTag(defaults.getCompound("face")).asString());
            defaults.put("face",AdvancedGraphDocument.Value.direction("invalid").toTag());
            assertDoesNotThrow(() -> AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node,target));
            assertNotNull(Direction.byName(AdvancedGraphDocument.Value.fromTag(node.data().getCompound("Defaults").getCompound("face")).asString()));
        }
    }

    private static AdvancedGraphDocument.Node node(String id,String type){
        return new AdvancedGraphDocument.Node(id,type,"",0,0,new CompoundTag());
    }

    private static ContraptionNetworkLinkerData.LinkedTarget linked(BlockPos pos){
        return new ContraptionNetworkLinkerData.LinkedTarget(pos,null,"minecraft:stone","Bank",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT,ContraptionNetworkLinkerData.TargetScope.BLOCK,List.of());
    }
}
