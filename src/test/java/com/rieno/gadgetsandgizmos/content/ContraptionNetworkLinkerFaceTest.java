package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Keep face selections through saved targets while preserving whole-block references
class ContraptionNetworkLinkerFaceTest{
    @Test
    void ordinaryTargetFaceSurvivesSerialization(){
        var node = new ControllerDiscoveryNode("test:chest", ControllerDiscoveryKind.UNKNOWN,
                "cargo", "minecraft:chest", "Chest", null, new BlockPos(1, 2, 3));
        var reference = ContraptionNetworkLinkerData.directTargetForFace(node, Direction.EAST);
        assertNotNull(reference);
        var restored = ControllerDirectTargetReference.fromTag(reference.toTag());
        assertEquals(Direction.EAST, ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(restored));
        assertEquals(node.nodeId(), restored.targetId());
        assertEquals(node.blockPos(), restored.blockPos());
    }

    @Test
    void wholeBlockReferenceRemainsUnchangedWithoutAFace(){
        var node = new ControllerDiscoveryNode("test:chest", ControllerDiscoveryKind.UNKNOWN,
                "cargo", "minecraft:chest", "Chest", null, new BlockPos(1, 2, 3));
        var reference = ContraptionNetworkLinkerData.directTargetForFace(node, null);
        assertEquals(node.asDirectTargetReference(), reference);
        assertNull(ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(reference));
        assertEquals(ContraptionNetworkLinkerData.TargetScope.BLOCK,
                ContraptionNetworkLinkerData.resolveTargetScope(null, node.blockPos(), null, Direction.EAST,
                        ContraptionNetworkLinkerData.TargetMode.BLOCK));
    }

    @Test
    void overlappingBlockCleanupKeepsVirtualFacesButRemovesWholeBlockBindings(){
        var pos = new BlockPos(1, 2, 3);
        var face = new ContraptionNetworkLinkerData.LinkedTarget(pos, null,
                "createthrusters:contraption_network_linker_plane", "Face",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.FACE,
                java.util.List.of(new ContraptionNetworkLinkerData.LinkedFace(Direction.EAST, "East", "")));
        var block = new ContraptionNetworkLinkerData.LinkedTarget(pos, null, "minecraft:stone", "Block",
                ContraptionNetworkLinkerData.LinkMode.OUTPUT, ContraptionNetworkLinkerData.TargetScope.BLOCK, java.util.List.of());
        assertFalse(ContraptionNetworkLinkerTracker.matchesBrokenTarget(face, pos, null, "minecraft:stone"));
        assertTrue(ContraptionNetworkLinkerTracker.matchesBrokenTarget(block, pos, null, "minecraft:stone"));
        var faceNode = ContraptionNetworkLinkerData.toDiscoveryNodes(java.util.List.of(face)).getFirst();
        var blockNode = ContraptionNetworkLinkerData.toDiscoveryNodes(java.util.List.of(block)).getFirst();
        var controller = new net.minecraft.nbt.CompoundTag();
        var targets = new net.minecraft.nbt.ListTag();targets.add(faceNode.toTag());targets.add(blockNode.toTag());
        controller.put("StoredTargets", targets);
        var direct = new net.minecraft.nbt.CompoundTag();
        direct.put("face", ContraptionNetworkLinkerData.directTargetForFace(faceNode, Direction.EAST).toTag());
        direct.put("block", blockNode.asDirectTargetReference().toTag());controller.put("DirectTargets", direct);
        assertTrue(ContraptionNetworkLinkerData.removeControllerTargetReferences(controller, pos, null,
                java.util.Set.of(blockNode.nodeId())));
        assertEquals(1, controller.getList("StoredTargets", 10).size());
        assertTrue(controller.getCompound("DirectTargets").contains("face"));
        assertFalse(controller.getCompound("DirectTargets").contains("block"));
        assertTrue(ContraptionNetworkLinkerData.removeControllerTargetReferences(controller, pos, null,
                java.util.Set.of(faceNode.nodeId())));
        assertTrue(controller.getList("StoredTargets", 10).isEmpty());
        assertTrue(controller.getCompound("DirectTargets").isEmpty());
    }
}
