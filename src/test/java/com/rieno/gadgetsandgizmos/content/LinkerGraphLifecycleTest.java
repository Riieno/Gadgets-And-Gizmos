package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.graph.GraphReconciliation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LinkerGraphLifecycleTest{
    // Initialize the vanilla registries used by linker fixtures
    @BeforeAll
    static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    // Taking the linker out for editing must leave the graph and wires intact
    @Test
    void absentLinkerPreservesImportedSelectionsAndWires(){
        var graph = new AdvancedGraphDocument();
        var target = target(new BlockPos(3, 64, 5), ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of());
        var provider = mock(HolderLookup.Provider.class);
        AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, binding(target), List.of(target), provider);
        CompoundTag prev = graph.toTag();

        assertFalse(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, ItemStack.EMPTY, provider));
        assertEquals(prev, graph.toTag());
    }

    // Editing a target's role preserves its graph node, stable ports and existing wires
    @Test
    void inputOutputModeChangeRetainsTheSelectedPhysicalTarget(){
        var graph = new AdvancedGraphDocument();
        BlockPos pos = new BlockPos(3, 64, 5);
        var first = target(pos, ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of(Direction.NORTH));
        var next = target(pos, ContraptionNetworkLinkerData.LinkMode.INPUT, List.of(Direction.NORTH));
        var provider = mock(HolderLookup.Provider.class);
        CompoundTag root = binding(first);
        AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, root, List.of(first), provider);
        var prev = output(graph);
        var wires = List.copyOf(graph.edges());

        assertTrue(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, root, List.of(next), provider));
        assertSame(prev, output(graph));
        assertEquals(next.nodeId(), output(graph).data().getCompound("TargetData").getString("NodeId"));
        assertEquals(next.kind().id(), output(graph).data().getCompound("TargetData").getString("Kind"));
        assertEquals(wires, graph.edges());
    }

    // Linker bindings may be cleared during a role edit without deleting a retained graph selection
    @Test
    void retainedTargetSurvivesClearedBindingsAfterARoleEdit(){
        var graph = new AdvancedGraphDocument();
        BlockPos pos = new BlockPos(3, 64, 5);
        var first = target(pos, ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of());
        var next = target(pos, ContraptionNetworkLinkerData.LinkMode.INPUT, List.of());
        var provider = mock(HolderLookup.Provider.class);
        AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, binding(first), List.of(first), provider);
        var nodes = List.copyOf(graph.nodes());
        var wires = List.copyOf(graph.edges());

        AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, new CompoundTag(), List.of(next), provider);
        assertEquals(nodes, graph.nodes());
        assertEquals(wires, graph.edges());
    }

    // Removing the final binding must withdraw its generated nodes and every incident wire
    @Test
    void emptyLinkerRemovesImportsWithoutDeletingManualNodes(){
        var graph = new AdvancedGraphDocument();
        var target = target(new BlockPos(3, 64, 5), ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of());
        var provider = mock(HolderLookup.Provider.class);
        assertTrue(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph,
                binding(target), List.of(target), provider));
        var manual = new AdvancedGraphDocument.Node("manual", "constant_number", "", 0, 0, new CompoundTag());
        graph.nodes().add(manual);
        graph.edges().add(new AdvancedGraphDocument.Edge("manual-wire", "manual", "value", output(graph).id(), "value"));

        assertTrue(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph,
                new CompoundTag(), List.of(), provider));
        assertEquals(List.of(manual), graph.nodes());
        assertTrue(graph.edges().isEmpty());
        assertFalse(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph,
                new CompoundTag(), List.of(), provider));
    }

    // Changing a binding reuses its node and leaves existing graph wires attached
    @Test
    void retargetingReplacesTheOldTargetWithoutAccumulatingNodes(){
        var graph = new AdvancedGraphDocument();
        var first = target(new BlockPos(3, 64, 5), ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of());
        var next = target(new BlockPos(9, 64, 5), ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of());
        var provider = mock(HolderLookup.Provider.class);
        AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, binding(first), List.of(first), provider);
        var prev = output(graph);
        var wires = List.copyOf(graph.edges());

        assertTrue(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph,
                binding(next), List.of(next), provider));
        assertSame(prev, output(graph));
        assertEquals(next.nodeId(), output(graph).data().getCompound("TargetData").getString("NodeId"));
        assertEquals(wires, graph.edges());
        assertEquals(2, graph.nodes().size());
        assertFalse(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph,
                binding(next), List.of(next), provider));
    }

    // A saved reference cannot recreate a face removed from the linker's current targets
    @Test
    void removedFaceDoesNotRemainAnImportedOutput(){
        var graph = new AdvancedGraphDocument();
        BlockPos pos = new BlockPos(3, 64, 5);
        var first = target(pos, ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of(Direction.NORTH));
        var remaining = target(pos, ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of(Direction.EAST));
        var provider = mock(HolderLookup.Provider.class);
        CompoundTag root = binding(first);
        AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph, root, List.of(first), provider);

        assertTrue(AdvancedContraptionControllerBlockEntity.importLinkerBindingsIntoGraph(graph,
                root, List.of(remaining), provider));
        assertTrue(graph.nodes().stream().noneMatch(node -> node.type().endsWith("_output")));
        assertTrue(graph.edges().isEmpty());
    }

    // A still-present support block must not make an unlinked target writable
    @Test
    void removedLinkerTargetStopsWritesEvenWhileItsBlockExists() throws Exception{
        BlockPos pos = new BlockPos(3, 64, 5);
        Level level = mock(Level.class);
        when(level.isLoaded(any())).thenReturn(true);
        when(level.getBlockState(any())).thenReturn(Blocks.STONE.defaultBlockState());
        var controller = mock(AdvancedContraptionControllerBlockEntity.class, CALLS_REAL_METHODS);
        controller.setLevel(level);
        doReturn(ItemStack.EMPTY).when(controller).getStoredLinker();
        var field = net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("worldPosition");
        field.setAccessible(true);
        field.set(controller, BlockPos.ZERO);
        CompoundTag data = new CompoundTag();
        data.put("TargetData", target(pos, ContraptionNetworkLinkerData.LinkMode.OUTPUT, List.of()).toTag());
        var node = new AdvancedGraphDocument.Node("out", "set_block_data", "", 0, 0, data);
        try(var simulated = mockStatic(SimulatedHelper.class);
            var collector = mockStatic(SubLevelBlockEntityCollector.class)){
            assertFalse(controller.setGraphTargetData(node, Set.of("state_power"),
                    port -> AdvancedGraphDocument.Value.number(15)));
            verify(level, never()).setBlock(any(), any(), anyInt());
        }
    }

    // Runtime snapshots must stop exposing values belonging to retired sources
    @Test
    void recompilationDropsRetiredLivePorts(){
        var graph = new AdvancedGraphDocument();
        CompoundTag data = new CompoundTag();
        data.putDouble("Value", 12);
        var source = new AdvancedGraphDocument.Node("old-source", "constant_number", "", 0, 0, data);
        graph.nodes().add(source);
        graph.nodes().add(new AdvancedGraphDocument.Node("tick", "event_tick", "", 0, 0, new CompoundTag()));
        CompoundTag targetData = new CompoundTag(), ports = new CompoundTag();
        ports.putString("signal", "number");
        targetData.put("DynamicInputs", ports);
        graph.nodes().add(new AdvancedGraphDocument.Node("target", "set_block_data", "", 0, 0, targetData));
        graph.edges().add(new AdvancedGraphDocument.Edge("exec", "tick", "exec", "target", "exec"));
        graph.edges().add(new AdvancedGraphDocument.Edge("value", "old-source", "value", "target", "signal"));
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        Level level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(level.getGameTime()).thenReturn(100L);
        var runtime = new GraphRuntime(controller);
        runtime.tick(graph);
        assertEquals(12, runtime.liveOutput("old-source", "value").asNumber());

        GraphReconciliation.removeNodes(graph, node -> node.id().equals("old-source"));
        runtime.compile(graph);
        assertFalse(runtime.liveOutputs().containsKey("old-source:value"));
        assertTrue(runtime.liveInputs().keySet().stream().noneMatch(key -> key.startsWith("old-source:")));
    }

    // Build a current linker discovery entry for a block or selected faces
    private static ControllerDiscoveryNode target(BlockPos pos, ContraptionNetworkLinkerData.LinkMode mode,
                                                  List<Direction> faces){
        var linked = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "minecraft:stone", "Target", mode,
                faces.isEmpty() ? ContraptionNetworkLinkerData.TargetScope.BLOCK : ContraptionNetworkLinkerData.TargetScope.FACE,
                faces.stream().map(face -> new ContraptionNetworkLinkerData.LinkedFace(face, face.getSerializedName(), "")).toList());
        return ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst();
    }

    // Bind the selected output to one standard controller channel
    private static CompoundTag binding(ControllerDiscoveryNode target){
        CompoundTag root = new CompoundTag(), binds = new CompoundTag(), channel = new CompoundTag();
        Direction face = ContraptionNetworkLinkerData.faceOptionsForNode(target).stream()
                .map(ContraptionNetworkLinkerData.FaceOption::face).findFirst().orElse(null);
        channel.put("DirectTarget", ContraptionNetworkLinkerData.directTargetForFace(target, face).toTag());
        binds.put("throttle_up", channel);
        root.put("ChannelBinds", binds);
        return root;
    }

    // Get the imported output node
    private static AdvancedGraphDocument.Node output(AdvancedGraphDocument graph){
        return graph.nodes().stream().filter(node -> node.type().endsWith("_output")).findFirst().orElseThrow();
    }
}
