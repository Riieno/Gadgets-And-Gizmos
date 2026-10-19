package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Upgrade shared Set Data signals without losing authored values or incoming wires
public final class AdvancedGraphTargetSignals{
    private AdvancedGraphTargetSignals(){}

    public static List<String> ports(AdvancedGraphDocument.Node node){
        if(node == null || !"set_block_data".equals(node.type())) return List.of();
        CompoundTag bindings = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG);
        return bindings.getAllKeys().stream().filter(port -> isTargetSignal(node, port)).sorted().toList();
    }

    public static boolean isTargetSignal(AdvancedGraphDocument.Node node, String port){
        if(node == null || port == null || !"set_block_data".equals(node.type())) return false;
        ListTag bindings = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG)
                .getList(port, Tag.TAG_COMPOUND);
        if(bindings.isEmpty()) return false;
        for(Tag raw : bindings){
            if(!(raw instanceof CompoundTag binding)
                    || !GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(binding.getString("Port"))) return false;
        }
        return true;
    }

    // Called on graph load and after target schema refresh, before obsolete wires are removed
    public static void upgrade(AdvancedGraphDocument.Node node, List<AdvancedGraphDocument.Edge> edges){
        if(node == null || !"set_block_data".equals(node.type())) return;
        List<GraphTargetPortLayout.Target> targets = new ArrayList<>();
        for(Tag raw : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)){
            if(!(raw instanceof CompoundTag tag)) continue;
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(tag);
            if(target != null) targets.add(new GraphTargetPortLayout.Target(target.nodeId(),
                    com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection.label(target,
                            com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity.selectedDataTargetFace(node, target),
                            node.data().getCompound(com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection.ALIASES_TAG).getString(target.nodeId())),
                    List.of(new GraphTargetPortLayout.Port(GraphSignalRange.REDSTONE_SIGNAL_PORT,
                            "Redstone Signal Strength", "number"))));
        }
        if(targets.isEmpty()) return;
        Map<String, String> stableIds = new LinkedHashMap<>();
        CompoundTag ids = node.data().getCompound("DataTargetPortIds");
        ids.getAllKeys().forEach(id -> stableIds.put(id, ids.getString(id)));
        var layout = GraphTargetPortLayout.compose(targets, false, stableIds);
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        CompoundTag labels = node.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG);
        CompoundTag bindings = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG);
        CompoundTag sections = node.data().getCompound(AdvancedGraphCatalog.PORT_SECTIONS_TAG);
        CompoundTag inputSections = sections.getCompound("Inputs");
        CompoundTag collapsed = node.data().getCompound(AdvancedGraphCatalog.COLLAPSED_PORT_SECTIONS_TAG);
        String legacy = GraphSignalRange.REDSTONE_SIGNAL_PORT;
        for(String port : layout.ports().keySet()){
            inputs.putString(port, "number");
            labels.putString(port, "Redstone Signal Strength");
            ListTag entries = new ListTag();
            for(var binding : layout.bindings().get(port)){
                CompoundTag entry = new CompoundTag();
                entry.putString("Target", binding.targetId());
                entry.putString("Port", binding.portId());
                entries.add(entry);
            }
            bindings.put(port, entries);
            for(String key : List.of("Defaults", "PrefilledInputs", "ForceWriteInputs", "PersistentPorts", "PersistentPortValues")){
                CompoundTag values = node.data().getCompound(key);
                String prefix = key.startsWith("Persistent") ? "input:" : "";
                if(values.contains(prefix + legacy) && !values.contains(prefix + port)){
                    values.put(prefix + port, values.get(prefix + legacy).copy());
                    node.data().put(key, values);
                }
            }
        }
        for(var section : layout.sections()){
            CompoundTag encoded = inputSections.getCompound(section.id());
            encoded.putString("Label", section.label());
            ListTag sectionPorts = encoded.getList("Ports", Tag.TAG_STRING);
            for(String port : section.ports()){
                if(!sectionPorts.contains(StringTag.valueOf(port))) sectionPorts.add(StringTag.valueOf(port));
            }
            encoded.put("Ports", sectionPorts);
            inputSections.put(section.id(), encoded);
            if(!collapsed.contains("I:" + section.id())) collapsed.putBoolean("I:" + section.id(), true);
        }
        sections.put("Inputs", inputSections);
        node.data().put("DynamicInputs", inputs);
        node.data().put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
        node.data().put(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, bindings);
        node.data().put(AdvancedGraphCatalog.PORT_SECTIONS_TAG, sections);
        node.data().put(AdvancedGraphCatalog.COLLAPSED_PORT_SECTIONS_TAG, collapsed);
        if(edges != null){
            List<AdvancedGraphDocument.Edge> old = edges.stream()
                    .filter(edge -> node.id().equals(edge.toNode()) && legacy.equals(edge.toPort())).toList();
            edges.removeAll(old);
            for(var edge : old){
                int index = 0;
                for(String port : layout.ports().keySet()){
                    if(edges.stream().noneMatch(existing -> node.id().equals(existing.toNode()) && port.equals(existing.toPort()))){
                        String id = index == 0 ? edge.id() : edge.id() + "_target_" + index;
                        while(containsEdgeId(edges, id)) id += "_";
                        edges.add(new AdvancedGraphDocument.Edge(id, edge.fromNode(), edge.fromPort(), node.id(), port));
                    }
                    index++;
                }
            }
        }
        for(String key : List.of("DynamicInputs", AdvancedGraphCatalog.INPUT_LABELS_TAG, "InputOptions",
                "Defaults", "PrefilledInputs", "ForceWriteInputs", "PersistentPorts", "PersistentPortValues")){
            CompoundTag values = node.data().getCompound(key);
            values.remove((key.startsWith("Persistent") ? "input:" : "") + legacy);
        }
    }

    private static boolean containsEdgeId(List<AdvancedGraphDocument.Edge> edges, String id){
        return edges.stream().anyMatch(edge -> id.equals(edge.id()));
    }
}
