package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerTargetMap;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Bind runtime Target values through the same schemas and collapsible sections as the target modal
public final class AdvancedGraphTargets{
    private AdvancedGraphTargets(){}

    public static boolean multiple(AdvancedGraphDocument.Node node){
        return node != null && ("get_block_data".equals(node.type()) || "set_block_data".equals(node.type()));
    }

    public static boolean apply(Level level, AdvancedGraphDocument.Node node, AdvancedGraphDocument.Value value,
                                List<AdvancedGraphDocument.Edge> edges){
        if(node == null || value == null || !("target".equals(value.type()) || "map".equals(value.type()))) return false;
        List<ControllerDiscoveryNode> targets = ControllerTargetMap.read(value.payload());
        if(!multiple(node) && targets.size() > 1) targets = List.of(targets.getFirst());
        ListTag encoded = new ListTag();targets.forEach(target -> encoded.add(target.toTag()));
        CompoundTag data = node.data();
        CompoundTag primary = targets.isEmpty() ? new CompoundTag() : targets.getFirst().toTag();
        boolean same = Objects.equals(primary, data.getCompound("TargetData"))
                && (!multiple(node) || encoded.equals(data.getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)));
        if(same && !data.getBoolean("WiredTargetSchemaPending")) return false;
        boolean pending = false;
        List<GraphTargetPortLayout.Target> schemas = new ArrayList<>();
        if(multiple(node)){
            for(ControllerDiscoveryNode target : targets){
                var schema = AdvancedContraptionControllerBlockEntity.graphDataTargetPortLayout(level, node, target,
                        "set_block_data".equals(node.type()));
                if(schema == null){
                    pending = true;
                    schema = new GraphTargetPortLayout.Target(target.nodeId(), target.label(), List.of());
                }
                schemas.add(schema);
            }
        }
        if(pending) data.putBoolean("WiredTargetSchemaPending", true);
        else data.remove("WiredTargetSchemaPending");
        if(targets.isEmpty()){
            data.remove("TargetData");data.remove("Target");data.remove("TargetLabel");
        }else{
            data.put("TargetData", primary);
            data.putString("Target", targets.getFirst().nodeId());
            data.putString("TargetLabel", targets.getFirst().label());
        }
        if(multiple(node)){
            data.put(AdvancedGraphCatalog.DATA_TARGETS_TAG, encoded);
            AdvancedContraptionControllerBlockEntity.applyDataTargetPortLayout(node, schemas, "set_block_data".equals(node.type()));
            AdvancedGraphTargetSignals.upgrade(node, edges);
            AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node, targets.isEmpty() ? null : targets.getFirst());
        }
        return true;
    }
}
