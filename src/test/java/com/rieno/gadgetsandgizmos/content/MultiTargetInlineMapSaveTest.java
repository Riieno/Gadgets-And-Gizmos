package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiTargetInlineMapSaveTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void targetSchemaRefreshKeepsExpandedMapOutputAndWire() {
        String mapPort = "target_engine__telemetry";
        String inlinePort = "target_engine__telemetry__rpm";
        CompoundTag data = new CompoundTag();
        CompoundTag outputs = new CompoundTag();
        outputs.putString(mapPort, "map");
        outputs.putString(inlinePort, "number");
        data.put("DynamicOutputs", outputs);
        CompoundTag mapping = new CompoundTag();
        mapping.putString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG, mapPort);
        mapping.putString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG, "rpm");
        CompoundTag mappings = new CompoundTag();
        mappings.put(inlinePort, mapping);
        data.put(AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG, mappings);
        data.putString("Target", "engine");

        AdvancedGraphDocument.Node source = new AdvancedGraphDocument.Node(
                "source", "get_block_data", "", 0, 0, data);
        AdvancedGraphDocument.Node sink = new AdvancedGraphDocument.Node(
                "sink", "add", "", 0, 0, new CompoundTag());
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.nodes().add(source);
        graph.nodes().add(sink);
        graph.edges().add(new AdvancedGraphDocument.Edge(
                "rpm-wire", "source", inlinePort, "sink", "a"));

        AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(source,
                List.of(new GraphTargetPortLayout.Target("engine", "Engine",
                        List.of(new GraphTargetPortLayout.Port("telemetry", "Telemetry", "map")))), false);

        assertEquals("number", AdvancedGraphCatalog.outputs(source).get(inlinePort));
        assertTrue(source.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG)
                .contains(inlinePort));
        assertTrue(com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator
                .validate(graph, true, true).valid());
    }
}
