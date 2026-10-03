package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedGraphWireLimitTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void graphRoundTripKeepsWiresBeyondTheFormerLimit() {
        AdvancedGraphDocument graph = wiredGraph(600);
        AdvancedGraphDocument restored = AdvancedGraphDocument.fromTag(graph.toTag());
        assertEquals(600, restored.edges().size());
        assertEquals("wire-599", restored.edges().get(599).id());
    }

    @Test
    void oversizedGraphIsRejectedWithoutSilentlyDroppingTheExtraWire() {
        AdvancedGraphDocument restored = AdvancedGraphDocument.fromTag(
                wiredGraph(AdvancedGraphDocument.MAX_EDGES + 1).toTag());
        assertEquals(AdvancedGraphDocument.MAX_EDGES + 1, restored.edges().size());
        assertTrue(AdvancedGraphValidator.validate(restored, true, true).diagnostics().stream()
                .anyMatch(diagnostic -> "edge_limit".equals(diagnostic.code())));
    }

    private static AdvancedGraphDocument wiredGraph(int count) {
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.nodes().add(new AdvancedGraphDocument.Node("source", "number", "Source", 0, 0,
                new CompoundTag()));
        graph.nodes().add(new AdvancedGraphDocument.Node("target", "number", "Target", 0, 0,
                new CompoundTag()));
        for (int index = 0; index < count; index++) {
            graph.edges().add(new AdvancedGraphDocument.Edge("wire-" + index,
                    "source", "value", "target", "value" + index));
        }
        return graph;
    }
}
