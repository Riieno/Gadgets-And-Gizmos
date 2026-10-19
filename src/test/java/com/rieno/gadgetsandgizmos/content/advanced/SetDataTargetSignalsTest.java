package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SetDataTargetSignalsTest{
    @Test void oldSharedWiresAndInlineValuesSurviveLoadingAndBecomeIndependent(){
        for(boolean prefilled : List.of(false, true)){
            var graph = new AdvancedGraphDocument();
            var setter = setter();
            CompoundTag defaults = new CompoundTag();
            defaults.put(GraphSignalRange.REDSTONE_SIGNAL_PORT, AdvancedGraphDocument.Value.number(0).toTag());
            setter.data().put("Defaults", defaults);
            if(prefilled){
                CompoundTag markers = new CompoundTag();markers.putBoolean(GraphSignalRange.REDSTONE_SIGNAL_PORT, true);
                setter.data().put("PrefilledInputs", markers);
            }
            graph.nodes().add(setter);
            graph.edges().add(new AdvancedGraphDocument.Edge("wire", "source", "value", setter.id(), GraphSignalRange.REDSTONE_SIGNAL_PORT));
            var restored = AdvancedGraphDocument.fromTag(graph.toTag());
            var node = restored.nodes().getFirst();
            var ports = AdvancedGraphTargetSignals.ports(node);
            assertEquals(2, ports.size());
            assertFalse(AdvancedGraphCatalog.inputs(node).containsKey(GraphSignalRange.REDSTONE_SIGNAL_PORT));
            assertEquals(2, restored.edges().size());
            for(String port : ports){
                assertEquals(0, AdvancedGraphDocument.Value.fromTag(node.data().getCompound("Defaults").getCompound(port)).asNumber());
                assertEquals(prefilled, node.data().getCompound("PrefilledInputs").contains(port));
                assertTrue(restored.edges().stream().anyMatch(edge -> port.equals(edge.toPort())));
                assertEquals(15, GraphSignalRange.targetValue(node, port, AdvancedGraphDocument.Value.number(30)).asNumber());
            }
            node.data().getCompound("Defaults").put(ports.getFirst(), AdvancedGraphDocument.Value.number(7).toTag());
            var again = AdvancedGraphDocument.fromTag(restored.toTag());
            assertEquals(restored.toTag(), again.toTag());
        }
    }

    @Test void mergingDataPortsKeepsSignalControlsInEachTargetSection(){
        var node = setter();node.data().putBoolean(AdvancedGraphCatalog.MERGE_LIKE_PORTS_TAG, true);
        var fields = List.of(new GraphTargetPortLayout.Port("speed", "Speed", "number"));
        AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node,
                List.of(new GraphTargetPortLayout.Target("first", "First", fields),
                        new GraphTargetPortLayout.Target("second", "Second", fields)), true);
        assertEquals(2, AdvancedGraphTargetSignals.ports(node).size());
        var sections = node.data().getCompound(AdvancedGraphCatalog.PORT_SECTIONS_TAG).getCompound("Inputs");
        assertEquals(3, sections.size());
        assertEquals(1, sections.getCompound("merged").getList("Ports", 8).size());
        assertEquals(1, sections.getCompound("target_first").getList("Ports", 8).size());
        assertEquals(1, sections.getCompound("target_second").getList("Ports", 8).size());
    }

    private static AdvancedGraphDocument.Node setter(){
        var node = new AdvancedGraphDocument.Node("set", "set_block_data", "", 0, 0, new CompoundTag());
        ListTag targets = new ListTag();
        targets.add(new ControllerDiscoveryNode("first", ControllerDiscoveryKind.UNKNOWN, "", "minecraft:stone", "First", null, BlockPos.ZERO).toTag());
        targets.add(new ControllerDiscoveryNode("second", ControllerDiscoveryKind.UNKNOWN, "", "minecraft:stone", "Second", null, BlockPos.ZERO.east()).toTag());
        node.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        return node;
    }
}
