package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamedLinkerTargetTest{
    @Test void namedBlocksPreserveIdentityPositionAndSubLevel(){
        var ship = UUID.randomUUID();
        var target = new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(3, 5, 7), ship,
                "test:engine", "Engine 12", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        var found = ContraptionNetworkLinkerData.findNamedTarget(List.of(target), " engine 12 ");
        assertNotNull(found);
        assertEquals(target.blockPos(), found.blockPos());
        assertEquals(ship, found.subLevelId());
        assertEquals(ContraptionNetworkLinkerData.nodeIdForTarget(target), found.nodeId());
        assertNull(ContraptionNetworkLinkerData.findNamedTarget(List.of(target), "Engine"));
        assertNull(ContraptionNetworkLinkerData.findNamedTarget(List.of(target), "Engine 13"));
        assertNull(ContraptionNetworkLinkerData.findNamedTarget(List.of(target), ""));
    }

    @Test void duplicatesUseStableLinkerOrderAndRenamesTakeEffect(){
        var first = block(new BlockPos(1, 2, 3), "Thruster 1");
        var second = block(new BlockPos(10, 2, 3), "Thruster 1");
        var found = ContraptionNetworkLinkerData.findNamedTarget(List.of(second, first), "Thruster 1");
        assertEquals(first.blockPos(), found.blockPos());
        var renamed = block(first.blockPos(), "Thruster 2");
        assertEquals(second.blockPos(), ContraptionNetworkLinkerData.findNamedTarget(List.of(renamed, second), "Thruster 1").blockPos());
        assertEquals(first.blockPos(), ContraptionNetworkLinkerData.findNamedTarget(List.of(renamed, second), "Thruster 2").blockPos());
    }

    @Test void faceAliasesRetainTheSelectedFaceAndSignal(){
        var face = new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "Starboard", "");
        var target = new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(5, 4, 3), null,
                "minecraft:chest", "Cargo", ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                ContraptionNetworkLinkerData.TargetScope.FACE, List.of(face));
        var found = ContraptionNetworkLinkerData.findNamedTarget(List.of(target), "starboard");
        assertNotNull(found);
        assertEquals("Starboard", found.label());
        assertEquals(Direction.EAST, ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(found.asDirectTargetReference()));
        assertNotNull(ContraptionNetworkLinkerData.findNamedTarget(List.of(target), "Cargo"));
    }

    @Test void scmBindingsAreAvailableWithoutExpandingBeyondTheStoredLinker(){
        var target = new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(7, 4, 2), null,
                "test:engine", "Lift", ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
        assertNotNull(ContraptionNetworkLinkerData.findNamedTarget(List.of(target), "Lift"));
        assertNull(ContraptionNetworkLinkerData.findNamedTarget(List.of(), "Lift"));
    }

    @Test void convertNodeUsesTheHostLookupAndPreservesExistingConversions(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var target = ContraptionNetworkLinkerData.findNamedTarget(List.of(block(new BlockPos(2, 3, 4), "Engine 1")), "Engine 1");
        var expected = AdvancedGraphDocument.Value.target(target.toTag());
        when(controller.findNamedGraphTarget("Engine 1")).thenReturn(expected);
        var graph = new AdvancedGraphDocument();
        CompoundTag data = new CompoundTag(); data.putString("OutputType", "target");
        CompoundTag defaults = new CompoundTag();
        defaults.put("value", AdvancedGraphDocument.Value.string("Engine 1").toTag()); data.put("Defaults", defaults);
        var node = new AdvancedGraphDocument.Node("convert", "convert_type", "", 0, 0, data);
        graph.nodes().add(node);
        var runtime = new GraphRuntime(controller);
        runtime.compile(graph);
        assertEquals(expected, runtime.previewOutput(graph, node, "value"));
        verify(controller).findNamedGraphTarget("Engine 1");
        assertEquals(target, ControllerDiscoveryNode.fromTag(expected.payload()));
        defaults = node.data().getCompound("Defaults");
        defaults.put("value", expected.toTag());
        runtime.compile(graph);
        assertEquals(expected, runtime.previewOutput(graph, node, "value"));
        verify(controller, times(1)).findNamedGraphTarget("Engine 1");
        node.data().putString("OutputType", "number");
        defaults.put("value", AdvancedGraphDocument.Value.string("12.5").toTag());
        runtime.compile(graph);
        assertEquals(12.5, runtime.previewOutput(graph, node, "value").asNumber());
        var missing = emptyTargetGraph();
        var empty = new GraphRuntime(null).previewOutput(missing, missing.nodes().getFirst(), "value");
        assertEquals("target", empty.type());
        assertTrue(empty.payload().isEmpty());
    }

    @Test void convertNodeResolvesBothInlineAnyTextAndWiredStrings(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var target = ContraptionNetworkLinkerData.findNamedTarget(List.of(block(BlockPos.ZERO, "Engine 2")), "Engine 2");
        var expected = AdvancedGraphDocument.Value.target(target.toTag());
        when(controller.findNamedGraphTarget("Engine 2")).thenReturn(expected);
        var graph = new AdvancedGraphDocument();
        CompoundTag data = new CompoundTag(); data.putString("OutputType", "target");
        CompoundTag defaults = new CompoundTag();
        defaults.put("value", new AdvancedGraphDocument.Value("any", AdvancedGraphDocument.Value.string("Engine 2").payload()).toTag());
        data.put("Defaults", defaults);
        var node = new AdvancedGraphDocument.Node("convert", "convert_type", "", 0, 0, data);
        graph.nodes().add(node);
        var runtime = new GraphRuntime(controller);
        runtime.compile(graph);
        assertEquals(expected, runtime.previewOutput(graph, node, "value"));
        CompoundTag text = new CompoundTag(); text.putString("Value", "Engine 2");
        graph.nodes().add(new AdvancedGraphDocument.Node("text", "constant_string", "", 0, 0, text));
        graph.edges().add(new AdvancedGraphDocument.Edge("name", "text", "value", "convert", "value"));
        runtime.compile(graph);
        assertEquals(expected, runtime.previewOutput(graph, node, "value"));
        verify(controller, times(2)).findNamedGraphTarget("Engine 2");
    }

    private static AdvancedGraphDocument emptyTargetGraph(){
        var graph = new AdvancedGraphDocument(); var data = new CompoundTag();
        data.putString("OutputType", "target"); data.putString("Value", "Missing");
        graph.nodes().add(new AdvancedGraphDocument.Node("convert", "convert_type", "", 0, 0, data));
        return graph;
    }

    private static ContraptionNetworkLinkerData.LinkedTarget block(BlockPos pos, String name){
        return new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "test:engine", name,
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
    }
}
