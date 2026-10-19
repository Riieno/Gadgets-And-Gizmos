package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.compat.simulated.SchematicSubLevelReferenceRemapper;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SchematicTargetRemapTest{
    @Test void importedTargetsMoveTheirIdsAndFaceBindingsWhileKeepingWiresAndDefaults(){
        UUID source = UUID.randomUUID(), destination = UUID.randomUUID();
        BlockPos pos = new BlockPos(20,30,40), moved = pos.offset(80,0,-50);
        var oldTarget = target(source,pos);var newTarget = target(destination,moved);
        for(String type : List.of("get_block_data","set_block_data")){
            var node = new AdvancedGraphDocument.Node("data",type,"",0,0,new CompoundTag());
            node.data().put("TargetData",oldTarget.toTag());node.data().putString("Target",oldTarget.nodeId());
            ListTag targets = new ListTag();targets.add(oldTarget.toTag());
            node.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG,targets);
            CompoundTag faces = new CompoundTag();faces.putString(oldTarget.nodeId(),"east");
            node.data().put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG,faces);
            var schema = new GraphTargetPortLayout.Target(oldTarget.nodeId(),"Target",List.of(
                    new GraphTargetPortLayout.Port("powered","Powered","boolean")));
            boolean writable = "set_block_data".equals(type);
            AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node,List.of(schema),writable);
            String portsKey = writable ? "DynamicInputs" : "DynamicOutputs";
            CompoundTag ports = node.data().getCompound(portsKey).copy();
            String port = ports.getAllKeys().stream().filter(key -> key.endsWith("powered")).findFirst().orElseThrow();
            CompoundTag defaults = new CompoundTag();defaults.put(port,AdvancedGraphDocument.Value.bool(true).toTag());
            node.data().put("Defaults",defaults);
            CompoundTag root = new CompoundTag();root.put("Node",node.data());
            root.putString("WirePort",port);
            var context = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE,null);
            context.getMappings().put(source,new SubLevelSchematicSerializationContext.SchematicMapping(null,null,destination,
                    block -> ((BlockPos)block).offset(80,0,-50)));
            var previous = SubLevelSchematicSerializationContext.getCurrentContext();
            CompoundTag copy;
            try{
                SubLevelSchematicSerializationContext.setCurrentContext(context);
                copy = SchematicSubLevelReferenceRemapper.remappedCopy(root);
                assertEquals(copy,SchematicSubLevelReferenceRemapper.remappedCopy(copy));
            }finally{ SubLevelSchematicSerializationContext.setCurrentContext(previous); }
            CompoundTag imported = copy.getCompound("Node");
            var selected = ControllerDiscoveryNode.fromTag(imported.getCompound("TargetData"));
            assertEquals(newTarget.nodeId(),selected.nodeId());assertEquals(moved,selected.blockPos());
            assertTrue(selected.hasSameBlockTarget(newTarget));
            assertEquals("east",imported.getCompound(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG).getString(newTarget.nodeId()));
            assertFalse(imported.getCompound(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG).contains(oldTarget.nodeId()));
            assertEquals(oldTarget.nodeId(),imported.getCompound("DataTargetPortIds").getString(newTarget.nodeId()));
            assertEquals(newTarget.nodeId(),imported.getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG)
                    .getList(port,10).getCompound(0).getString("Target"));
            assertEquals(port,copy.getString("WirePort"));assertEquals(defaults,imported.getCompound("Defaults"));
            var restored = new AdvancedGraphDocument.Node("data",type,"",0,0,imported);
            AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(restored,selected);
            AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(restored,List.of(
                    new GraphTargetPortLayout.Target(newTarget.nodeId(),"Target",schema.ports())),writable);
            assertEquals(ports,restored.data().getCompound(portsKey));
            assertEquals(oldTarget.nodeId(),root.getCompound("Node").getString("Target"));
        }
    }

    @Test void packedPositionsWithSharedPrefixesDoNotRewriteEachOthersIdentifiers(){
        UUID source = UUID.randomUUID(), destination = UUID.randomUUID();
        BlockPos first = BlockPos.of(100), second = BlockPos.of(1000);
        CompoundTag root = new CompoundTag();ListTag ordered = new ListTag();
        ordered.add(target(source,first).toTag());ordered.add(target(source,second).toTag());root.put("Targets",ordered);
        var context = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE,null);
        context.getMappings().put(source,new SubLevelSchematicSerializationContext.SchematicMapping(null,null,destination,
                block -> ((BlockPos)block).offset(1,0,0)));
        var previous = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(context);
            var copy = SchematicSubLevelReferenceRemapper.remappedCopy(root);
            assertEquals(target(destination,first.east()).nodeId(),copy.getList("Targets",10).getCompound(0).getString("NodeId"));
            assertEquals(target(destination,second.east()).nodeId(),copy.getList("Targets",10).getCompound(1).getString("NodeId"));
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(previous); }
    }

    private static ControllerDiscoveryNode target(UUID body,BlockPos pos){
        return new ControllerDiscoveryNode("scm_picker:" + body + ":" + pos.asLong(),ControllerDiscoveryKind.MACHINE,
                "scm_picker","minecraft:lever","Target",body,pos);
    }
}
