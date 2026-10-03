package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupedRedstoneOutputTest {
    @BeforeAll
    static void bootstrap() { ControllerTestBootstrap.bootstrap(); }

    @Test
    void sharedRedstoneReachesEveryTargetAlongsideTheirIndividualDataWrites() {
        String signal = GraphSignalRange.REDSTONE_SIGNAL_PORT;
        ListTag targets = new ListTag();
        CompoundTag bindings = new CompoundTag();
        Map<String, AdvancedGraphDocument.Value> writes = new LinkedHashMap<>();
        Map<String, Map<String, Double>> expected = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) {
            var linked = new ContraptionNetworkLinkerData.LinkedTarget(new BlockPos(i, 64, 1), null,
                    "createthrusters:thruster", "Thruster " + i, ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                    ContraptionNetworkLinkerData.TargetScope.BLOCK, List.of());
            var discovery = ContraptionNetworkLinkerData.toDiscoveryNodes(List.of(linked)).getFirst();
            targets.add(discovery.toTag());
            CompoundTag binding = new CompoundTag();
            binding.putString("Target", discovery.nodeId());
            binding.putString("Port", "throttle");
            ListTag entries = new ListTag(); entries.add(binding);
            bindings.put("target_" + i + "_throttle", entries);
            writes.put("target_" + i + "_throttle", AdvancedGraphDocument.Value.number(i / 2.0));
            expected.put(discovery.nodeId(), Map.of("throttle", i / 2.0, signal, 0.75));
        }
        writes.put(signal, AdvancedGraphDocument.Value.number(0.75));
        CompoundTag data = new CompoundTag();
        data.put(AdvancedGraphCatalog.DATA_TARGETS_TAG, targets);
        data.put(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, bindings);
        var node = new AdvancedGraphDocument.Node("group", "set_block_data", "", 0, 0, data);
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        Map<String, Map<String, Double>> actual = new LinkedHashMap<>();
        when(controller.setGraphTargetData(any(), anySet(), any())).thenAnswer(call -> {
            AdvancedGraphDocument.Node targetNode = call.getArgument(0);
            if (targetNode == node) return call.callRealMethod();
            Set<String> ports = call.getArgument(1);
            Function<String, AdvancedGraphDocument.Value> values = call.getArgument(2);
            Map<String, Double> targetWrites = new LinkedHashMap<>();
            for (String port : ports) targetWrites.put(port, values.apply(port).asNumber());
            actual.put(targetNode.data().getString("Target"), targetWrites);
            return true;
        });
        assertTrue(controller.setGraphTargetData(node, writes.keySet(), writes::get));
        assertEquals(expected, actual);

        Map<String, Double> strengths = new HashMap<>();
        for (String id : expected.keySet()) strengths.put(id, 11D);
        when(controller.getGraphTargetData(any(), eq(signal))).thenAnswer(call -> {
            AdvancedGraphDocument.Node targetNode = call.getArgument(0);
            if (targetNode == node) return call.callRealMethod();
            return AdvancedGraphDocument.Value.number(strengths.get(targetNode.data().getString("Target")));
        });
        assertEquals(11, controller.getGraphTargetData(node, signal).asNumber());
        strengths.put(expected.keySet().iterator().next(), 0D);
        assertEquals(-1, controller.getGraphTargetData(node, signal).asNumber(),
                "One matching target must not suppress writes to the other targets");
    }
}
