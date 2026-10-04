package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccDisplayExternalSourceRoutingTest {
    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
    }

    @Test
    void wiredSourceOverridesSavedSourceWithoutChangingDisplayTarget() {
        CompoundTag data = new CompoundTag();
        CompoundTag savedSource = new CompoundTag();
        savedSource.putLong("BlockPos", 17L);
        data.put("SourceData", savedSource);
        CompoundTag displayTarget = new CompoundTag();
        displayTarget.putLong("BlockPos", 29L);
        data.put("TargetData", displayTarget);
        AdvancedGraphDocument.Node node = new AdvancedGraphDocument.Node(
                "external", "acc_display_external", "", 0, 0, data);
        CompoundTag wiredSource = new CompoundTag();
        wiredSource.putLong("BlockPos", 41L);

        assertEquals("target", AdvancedGraphCatalog.inputs(node).get("source"));
        assertEquals(41L, AccDisplayBlockEntity.externalSourceData(node,
                Map.of("external:source", AdvancedGraphDocument.Value.target(wiredSource))).getLong("BlockPos"));
        assertEquals(17L, AccDisplayBlockEntity.externalSourceData(node, Map.of()).getLong("BlockPos"));
        assertEquals(29L, node.data().getCompound("TargetData").getLong("BlockPos"));
    }
}
