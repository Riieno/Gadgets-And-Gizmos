package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraTrackingNodeTest{
    @Test void cameraNodeOffersAnOptInBooleanInItsBody(){
        assertEquals("boolean", AdvancedGraphCatalog.get("camera").inputs().get("auto_tracking"));
        var data = AdvancedGraphNodeFactory.createDefaultData("camera", AdvancedGraphNodeFactory.Context.EMPTY);
        assertEquals("AutoTracking", data.getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG).getString("auto_tracking"));
        var encoded = data.getCompound("Defaults").getCompound("auto_tracking");
        assertEquals("boolean", encoded.getString("Type"));
        assertFalse(encoded.getCompound("Payload").getBoolean("Value"));
        assertEquals("boolean", AdvancedGraphCatalog.get("camera").inputs().get("lock_target"));
        assertEquals("LockTarget", data.getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG).getString("lock_target"));
        assertFalse(data.getCompound("Defaults").getCompound("lock_target").getCompound("Payload").getBoolean("Value"));
    }

    @Test void olderSavedNodesGainADisabledOptionAndPreserveExistingChoices(){
        CompoundTag saved = new CompoundTag();
        ListTag nodes = new ListTag();
        for(boolean configured : new boolean[]{false, true}){
            CompoundTag node = new CompoundTag();
            node.putString("Id", configured ? "configured" : "old");
            node.putString("Type", "camera");
            if(configured){
                var data = AdvancedGraphNodeFactory.createDefaultData("camera", AdvancedGraphNodeFactory.Context.EMPTY);
                data.getCompound("Defaults").put("auto_tracking", AdvancedGraphNodeFactory.graphDefault("boolean", true));
                data.getCompound("Defaults").put("lock_target", AdvancedGraphNodeFactory.graphDefault("boolean", true));
                node.put("Data", data);
            }
            nodes.add(node);
        }
        saved.put("Nodes", nodes);
        var graph = AdvancedGraphDocument.fromTag(saved);
        assertEquals(2, graph.nodes().size());
        for(var node : graph.nodes()){
            assertEquals("AutoTracking", node.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG).getString("auto_tracking"));
            assertEquals("configured".equals(node.id()),
                    node.data().getCompound("Defaults").getCompound("auto_tracking").getCompound("Payload").getBoolean("Value"));
            assertEquals("LockTarget", node.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG).getString("lock_target"));
            assertEquals("configured".equals(node.id()),
                    node.data().getCompound("Defaults").getCompound("lock_target").getCompound("Payload").getBoolean("Value"));
        }
    }
}
