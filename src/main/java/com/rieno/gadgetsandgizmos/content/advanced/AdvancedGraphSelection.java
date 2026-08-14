package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Copy, delete and move selected graph nodes while preserving their internal edges
public final class AdvancedGraphSelection {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String COMMENT_GROUP_NODE_IDS = "NodeIds";

    // Store append results
    public record AppendResult(Set<String> nodeIds, Set<String> groupIds) {
        // Initialize the append result
        public AppendResult {
            nodeIds = Set.copyOf(nodeIds);
            groupIds = Set.copyOf(groupIds);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph selection
    private AdvancedGraphSelection() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Copy the advanced graph selection
    public static AdvancedGraphDocument copy(AdvancedGraphDocument src, Set<String> selectedNodeIds) {
        return copy(src, selectedNodeIds, Set.of());
    }

    // Copy the advanced graph selection
    public static AdvancedGraphDocument copy(AdvancedGraphDocument src, Set<String> selectedNodeIds,
                                             Set<String> selectedGroupIds) {
        AdvancedGraphDocument selection = new AdvancedGraphDocument();
        if (src == null) {
            return selection;
        }

        Set<String> copiedNodeIds = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : src.nodes()) {
            if (selectedNodeIds == null || !selectedNodeIds.contains(node.id())) {
                continue;
            }
            selection.nodes().add(new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(),
                    node.x(), node.y(), node.data()));
            copiedNodeIds.add(node.id());
        }
        for (AdvancedGraphDocument.Edge edge : src.edges()) {
            if (copiedNodeIds.contains(edge.fromNode()) && copiedNodeIds.contains(edge.toNode())) {
                selection.edges().add(edge);
            }
        }
        if (selectedGroupIds != null && !selectedGroupIds.isEmpty()) {
            for (CompoundTag group : src.groups()) {
                if (selectedGroupIds.contains(group.getString("Id"))) {
                    CompoundTag copiedGroup = group.copy();
                    Set<String> copiedMembers = commentGroupNodeIds(group);
                    copiedMembers.retainAll(copiedNodeIds);
                    setCommentGroupNodeIds(copiedGroup, copiedMembers);
                    selection.groups().add(copiedGroup);
                }
            }
        }
        copyReferencedVariables(src, selection);
        return selection;
    }

    // Add the copy
    public static Set<String> appendCopy(AdvancedGraphDocument src, AdvancedGraphDocument destination,
                                         double offsetX, double offsetY) {
        return appendCopyWithGroups(src, destination, offsetX, offsetY).nodeIds();
    }

    // Add the copy with groups
    public static AppendResult appendCopyWithGroups(AdvancedGraphDocument src,
                                                    AdvancedGraphDocument destination,
                                                    double offsetX, double offsetY) {
        Set<String> copiedNodeIds = new LinkedHashSet<>();
        Set<String> copiedGroupIds = new LinkedHashSet<>();
        if (src == null || destination == null) {
            return new AppendResult(copiedNodeIds, copiedGroupIds);
        }

        Map<String, String> remappedNodeIds = new LinkedHashMap<>();
        Map<String, String> remappedVariables = appendReferencedVars(src, destination);
        for (AdvancedGraphDocument.Node node : src.nodes()) {
            if (destination.nodes().size() >= AdvancedGraphDocument.maxNodes()) {
                break;
            }
            String copiedId = UUID.randomUUID().toString();
            CompoundTag copiedData = node.data().copy();
            String variable = referencedVariable(node);
            String remappedVariable = remappedVariables.get(variable);
            if (remappedVariable != null) {
                copiedData.putString("Variable", remappedVariable);
                if ("variable_set".equals(node.type())) {
                    copiedData.putString("RegisteredVariable", remappedVariable);
                }
            }
            destination.nodes().add(new AdvancedGraphDocument.Node(copiedId, node.type(), node.label(),
                    node.x() + offsetX, node.y() + offsetY, copiedData));
            remappedNodeIds.put(node.id(), copiedId);
            copiedNodeIds.add(copiedId);
        }
        for (AdvancedGraphDocument.Edge edge : src.edges()) {
            if (destination.edges().size() >= AdvancedGraphDocument.MAX_EDGES) {
                break;
            }
            String fromNode = remappedNodeIds.get(edge.fromNode());
            String toNode = remappedNodeIds.get(edge.toNode());
            if (fromNode != null && toNode != null) {
                destination.edges().add(new AdvancedGraphDocument.Edge(UUID.randomUUID().toString(),
                        fromNode, edge.fromPort(), toNode, edge.toPort()));
            }
        }
        for (CompoundTag group : src.groups()) {
            CompoundTag copiedGroup = group.copy();
            String copiedId = UUID.randomUUID().toString();
            copiedGroup.putString("Id", copiedId);
            copiedGroup.putDouble("X", group.getDouble("X") + offsetX);
            copiedGroup.putDouble("Y", group.getDouble("Y") + offsetY);
            Set<String> copiedMembers = new LinkedHashSet<>();
            for (String memberId : commentGroupNodeIds(group)) {
                String copiedMemberId = remappedNodeIds.get(memberId);
                if (copiedMemberId != null) {
                    copiedMembers.add(copiedMemberId);
                }
            }
            setCommentGroupNodeIds(copiedGroup, copiedMembers);
            destination.groups().add(copiedGroup);
            copiedGroupIds.add(copiedId);
        }
        return new AppendResult(copiedNodeIds, copiedGroupIds);
    }

    // Get the comment group node ids
    public static Set<String> commentGroupNodeIds(CompoundTag group) {
        Set<String> nodeIds = new LinkedHashSet<>();
        if (group == null) {
            return nodeIds;
        }
        ListTag tags = group.getList(COMMENT_GROUP_NODE_IDS, Tag.TAG_STRING);
        for (int idx = 0; idx < tags.size(); idx++) {
            String nodeId = tags.getString(idx);
            if (!nodeId.isBlank()) {
                nodeIds.add(nodeId);
            }
        }
        return nodeIds;
    }

    // Set the comment group node ids
    public static void setCommentGroupNodeIds(CompoundTag group, Set<String> nodeIds) {
        if (group == null) {
            return;
        }
        ListTag tags = new ListTag();
        if (nodeIds != null) {
            for (String nodeId : nodeIds) {
                if (nodeId != null && !nodeId.isBlank()) {
                    tags.add(StringTag.valueOf(nodeId));
                }
            }
        }
        group.put(COMMENT_GROUP_NODE_IDS, tags);
    }

    // Copy the referenced variables
    private static void copyReferencedVariables(AdvancedGraphDocument src, AdvancedGraphDocument destination) {
        for (AdvancedGraphDocument.Node node : destination.nodes()) {
            String variable = referencedVariable(node);
            AdvancedGraphDocument.Value val = src.variables().get(variable);
            if (val != null) {
                destination.variables().putIfAbsent(variable,
                        new AdvancedGraphDocument.Value(val.type(), val.payload()));
            }
        }
    }

    // Add the referenced vars
    private static Map<String, String> appendReferencedVars(AdvancedGraphDocument src,
                                                                  AdvancedGraphDocument destination) {
        Map<String, String> remappedVariables = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : src.nodes()) {
            String variable = referencedVariable(node);
            AdvancedGraphDocument.Value sourceValue = src.variables().get(variable);
            if (variable.isBlank() || sourceValue == null || remappedVariables.containsKey(variable)) {
                continue;
            }
            String destinationVariable = variable;
            AdvancedGraphDocument.Value existingValue = destination.variables().get(variable);
            if (existingValue != null && !existingValue.type().equals(sourceValue.type())) {
                destinationVariable = availableVariableName(variable, destination.variables().keySet());
            }
            remappedVariables.put(variable, destinationVariable);
            destination.variables().putIfAbsent(destinationVariable,
                    new AdvancedGraphDocument.Value(sourceValue.type(), sourceValue.payload()));
        }
        return remappedVariables;
    }

    // Get the available variable name
    private static String availableVariableName(String requestedName, Set<String> existingNames) {
        for (int copy = 1; ; copy++) {
            String suffix = copy == 1 ? "_copy" : "_copy_" + copy;
            int prefixLength = Math.max(1, 64 - suffix.length());
            String prefix = requestedName.substring(0, Math.min(requestedName.length(), prefixLength));
            String candidate = prefix + suffix;
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
        }
    }

    // Get the referenced variable
    private static String referencedVariable(AdvancedGraphDocument.Node node) {
        if (node == null || !("variable_get".equals(node.type()) || "variable_set".equals(node.type())
                || "event_variable_change".equals(node.type()))) {
            return "";
        }
        return node.data().getString("Variable");
    }
}
