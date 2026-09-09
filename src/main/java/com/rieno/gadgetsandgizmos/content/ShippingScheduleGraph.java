package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// Adapt Create schedules to the normal ACC node-and-edge document contract used by the hidden Scratch surface.
public final class ShippingScheduleGraph {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String TEMPLATE_ID = "shipping_schedule";
    public static final String ENTRY_TAG = "ShippingScheduleEntry";
    public static final String CONDITION_PARENT_TAG = "ShippingScheduleConditionParent";
    public static final String CONDITION_GROUP_TAG = "ShippingScheduleConditionGroup";
    public static final String CONDITION_INDEX_TAG = "ShippingScheduleConditionIndex";
    // A wait condition can be created as a normal Scratch block before it is
    // dropped into a compatible schedule-step C block. Once attached, its
    // authoritative representation moves into that ScheduleEntry instead.
    public static final String DETACHED_CONDITION_TAG = "ShippingScheduleDetachedCondition";
    public static final String SCRATCH_PARENT_TAG = "ScratchParent";
    // Stable sibling order for blocks nested in a generic Scratch C block.
    // ScheduleEntry conditions have their own semantic group/index ordering.
    public static final String SCRATCH_ORDER_TAG = "ScratchOrder";
    public static final String FLOW_KIND_TAG = "ScratchFlow";
    public static final String FLOW_REPEAT_TAG = "Repeat";
    public static final String FLOW_TARGET_TAG = "Target";
    public static final String CYCLIC_VARIABLE = "__shipping_schedule_cyclic";
    public static final String INSTRUCTION_PREFIX = "shipping_schedule:instruction:";
    public static final String CONDITION_PREFIX = "shipping_schedule:condition:";
    public static final String FLOW_PREFIX = "shipping_schedule:flow:";
    private static final String NEXT_PORT = "next";
    private static final String CONDITION_PORT = "condition";
    private static final int SCRATCH_STATEMENT_HEIGHT = 32;
    private static final int SCRATCH_CONDITION_TOP = 29;
    private static final int SCRATCH_CONDITION_STEP = 32;
    private static final int SCRATCH_CONDITION_FOOT = 17;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Prevent construction
    private ShippingScheduleGraph() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Convert an installed Create schedule into a normal ACC graph document. Each node owns one untouched ScheduleEntry tag.
    public static AdvancedGraphDocument fromSchedule(Schedule schedule, HolderLookup.Provider registries) {
        AdvancedGraphDocument graph = new AdvancedGraphDocument();
        graph.setTemplateId(TEMPLATE_ID);
        if (schedule == null) return graph;
        graph.variables().put(CYCLIC_VARIABLE, AdvancedGraphDocument.Value.bool(schedule.cyclic));
        String previous = null;
        double y = 80.0D;
        for (ScheduleEntry entry : schedule.entries) {
            if (entry == null || entry.instruction == null) continue;
            String nodeId = "shipping_entry_" + UUID.randomUUID().toString().replace('-', '_');
            CompoundTag data = new CompoundTag();
            data.put(ENTRY_TAG, entry.write(registries));
            ResourceLocation instructionId = entry.instruction.getId();
            AdvancedGraphDocument.Node instructionNode = new AdvancedGraphDocument.Node(nodeId,
                    instructionBlockType(instructionId), title(instructionId), 80, y, data);
            graph.nodes().add(instructionNode);
            rebuildConditionBlocks(graph, instructionNode, entry, registries);
            if (previous != null) {
                graph.edges().add(new AdvancedGraphDocument.Edge(
                        "shipping_edge_" + UUID.randomUUID().toString().replace('-', '_'),
                        previous, NEXT_PORT, nodeId, NEXT_PORT));
            }
            previous = nodeId;
            y += scratchHeightForConditions(entry.conditions);
        }
        return graph;
    }

    // Materialize the graph's ordered block chain into the Create schedule runtime model.
    public static Schedule toSchedule(AdvancedGraphDocument graph, HolderLookup.Provider registries) {
        if (graph == null || !TEMPLATE_ID.equals(graph.templateId())) return null;
        List<ScheduleEntry> entries = new ArrayList<>();
        for (AdvancedGraphDocument.Node node : orderedNodes(graph)) {
            if (!isInstructionBlock(node.type()) || !node.data().contains(ENTRY_TAG)) continue;
            ScheduleEntry entry = ScheduleEntry.fromTag(registries, node.data().getCompound(ENTRY_TAG));
            if (entry != null && entry.instruction != null) entries.add(entry);
        }
        if (entries.isEmpty()) return null;
        AdvancedGraphDocument.Value cyclic = graph.variables().get(CYCLIC_VARIABLE);
        return new Schedule(entries, cyclic != null && cyclic.asBoolean(), 0);
    }

    // Check whether a graph still represents the installed legacy schedule.
    // Runtime progress is intentionally not compared: route movement must not
    // replace a player's Scratch layout just because the active stop changed.
    public static boolean representsSchedule(AdvancedGraphDocument graph, Schedule schedule,
                                             HolderLookup.Provider registries) {
        if (graph == null || schedule == null || !TEMPLATE_ID.equals(graph.templateId())) return false;
        List<AdvancedGraphDocument.Node> nodes = orderedNodes(graph);
        if (nodes.size() != schedule.entries.size()) return false;
        AdvancedGraphDocument.Value cyclic = graph.variables().get(CYCLIC_VARIABLE);
        if ((cyclic != null && cyclic.asBoolean()) != schedule.cyclic) return false;
        for (int index = 0; index < nodes.size(); index++) {
            AdvancedGraphDocument.Node node = nodes.get(index);
            if (!node.data().contains(ENTRY_TAG)) return false;
            ScheduleEntry graphEntry = ScheduleEntry.fromTag(registries, node.data().getCompound(ENTRY_TAG));
            ScheduleEntry liveEntry = schedule.entries.get(index);
            if (graphEntry == null || liveEntry == null
                    || !graphEntry.write(registries).equals(liveEntry.write(registries))) {
                return false;
            }
        }
        return true;
    }

    // Add one default instruction block at the end of the schedule chain.
    public static boolean appendInstruction(AdvancedGraphDocument graph, ResourceLocation instructionId,
                                            HolderLookup.Provider registries) {
        if (graph == null || instructionId == null) return false;
        ScheduleInstruction instruction = createInstruction(instructionId);
        if (instruction == null || graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) return false;
        ScheduleEntry entry = new ScheduleEntry(instruction, new ArrayList<>());
        String nodeId = "shipping_entry_" + UUID.randomUUID().toString().replace('-', '_');
        CompoundTag data = new CompoundTag();
        data.put(ENTRY_TAG, entry.write(registries));
        List<AdvancedGraphDocument.Node> ordered = orderedNodes(graph);
        AdvancedGraphDocument.Node previous = ordered.isEmpty() ? null : ordered.getLast();
        double x = previous == null ? 80.0D : previous.x();
        double y = previous == null ? 80.0D : previous.y() + scratchVisualHeight(graph, previous.id());
        graph.nodes().add(new AdvancedGraphDocument.Node(nodeId, instructionBlockType(instructionId),
                title(instructionId), x, y, data));
        if (previous != null && graph.edges().size() < AdvancedGraphDocument.MAX_EDGES) {
            graph.edges().add(new AdvancedGraphDocument.Edge(
                    "shipping_edge_" + UUID.randomUUID().toString().replace('-', '_'),
                    previous.id(), NEXT_PORT, nodeId, NEXT_PORT));
        }
        ensureTemplate(graph);
        return true;
    }

    // Add one default wait condition to a selected instruction block as its own OR column.
    public static boolean appendCondition(AdvancedGraphDocument graph, String nodeId, ResourceLocation conditionId,
                                          HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node node = instructionNode(graph, nodeId);
        if (node == null || !isInstructionBlock(node.type()) || conditionId == null
                || !node.data().contains(ENTRY_TAG)) return false;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, node.data().getCompound(ENTRY_TAG));
        ScheduleWaitCondition condition = createCondition(conditionId);
        if (entry == null || entry.instruction == null || !entry.instruction.supportsConditions() || condition == null) {
            return false;
        }
        entry.conditions.add(new ArrayList<>(List.of(condition)));
        node.data().put(ENTRY_TAG, entry.write(registries));
        rebuildConditionBlocks(graph, node, entry, registries);
        reflowInstructionStack(graph, node.id());
        return true;
    }

    // Add a free wait-condition block. This makes the palette useful without
    // first selecting a schedule step; dropping it into a compatible C block
    // promotes it into that step's real Create condition list.
    public static String appendDetachedCondition(AdvancedGraphDocument graph, ResourceLocation conditionId,
                                                 double x, double y, HolderLookup.Provider registries) {
        if (graph == null || conditionId == null || !Double.isFinite(x) || !Double.isFinite(y)
                || graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) return "";
        ScheduleWaitCondition condition = createCondition(conditionId);
        if (condition == null) return "";
        String id = "shipping_condition_" + UUID.randomUUID().toString().replace('-', '_');
        CompoundTag data = new CompoundTag();
        data.put(DETACHED_CONDITION_TAG, condition.write(registries));
        graph.nodes().add(new AdvancedGraphDocument.Node(id, conditionBlockType(condition.getId()),
                title(condition.getId()), x, y, data));
        ensureTemplate(graph);
        return id;
    }

    // Attach a free condition to a schedule-step C block and return the id of
    // its regenerated semantic child node. Empty means the target does not
    // accept normal Create wait conditions.
    public static String attachDetachedCondition(AdvancedGraphDocument graph, String conditionNodeId,
                                                  String instructionNodeId, HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node conditionNode = node(graph, conditionNodeId);
        AdvancedGraphDocument.Node instruction = instructionNode(graph, instructionNodeId);
        if (conditionNode == null || !isDetachedCondition(conditionNode) || instruction == null
                || !instruction.data().contains(ENTRY_TAG)) return "";
        ScheduleWaitCondition condition = detachedCondition(conditionNode, registries);
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        if (condition == null || entry == null || entry.instruction == null || !entry.instruction.supportsConditions()) {
            return "";
        }
        entry.conditions.add(new ArrayList<>(List.of(condition)));
        int group = entry.conditions.size() - 1;
        graph.nodes().removeIf(node -> node != null && conditionNode.id().equals(node.id()));
        graph.edges().removeIf(edge -> edge != null && (conditionNode.id().equals(edge.fromNode())
                || conditionNode.id().equals(edge.toNode())));
        instruction.data().put(ENTRY_TAG, entry.write(registries));
        rebuildConditionBlocks(graph, instruction, entry, registries);
        reflowInstructionStack(graph, instruction.id());
        return conditionNodeId(graph, instruction.id(), group, 0);
    }

    // Move an attached condition between compatible schedule entries.
    public static String moveConditionToInstruction(AdvancedGraphDocument graph, String conditionNodeId,
                                                    String targetInstructionId, HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node conditionNode = node(graph, conditionNodeId);
        if (conditionNode == null || !isConditionBlock(conditionNode.type())) return "";
        if (isDetachedCondition(conditionNode)) {
            return attachDetachedCondition(graph, conditionNodeId, targetInstructionId, registries);
        }
        AdvancedGraphDocument.Node source = instructionNode(graph, conditionParent(conditionNode));
        AdvancedGraphDocument.Node target = instructionNode(graph, targetInstructionId);
        if (source == null || target == null || !source.data().contains(ENTRY_TAG) || !target.data().contains(ENTRY_TAG)) {
            return "";
        }
        if (source.id().equals(target.id())) return conditionNode.id();
        ScheduleEntry sourceEntry = ScheduleEntry.fromTag(registries, source.data().getCompound(ENTRY_TAG));
        ScheduleEntry targetEntry = ScheduleEntry.fromTag(registries, target.data().getCompound(ENTRY_TAG));
        int group = conditionNode.data().getInt(CONDITION_GROUP_TAG);
        int index = conditionNode.data().getInt(CONDITION_INDEX_TAG);
        if (sourceEntry == null || targetEntry == null || targetEntry.instruction == null
                || !targetEntry.instruction.supportsConditions() || group < 0 || group >= sourceEntry.conditions.size()
                || index < 0 || index >= sourceEntry.conditions.get(group).size()) return "";
        ScheduleWaitCondition condition = sourceEntry.conditions.get(group).remove(index);
        if (sourceEntry.conditions.get(group).isEmpty()) sourceEntry.conditions.remove(group);
        targetEntry.conditions.add(new ArrayList<>(List.of(condition)));
        int targetGroup = targetEntry.conditions.size() - 1;
        source.data().put(ENTRY_TAG, sourceEntry.write(registries));
        target.data().put(ENTRY_TAG, targetEntry.write(registries));
        rebuildConditionBlocks(graph, source, sourceEntry, registries);
        rebuildConditionBlocks(graph, target, targetEntry, registries);
        reflowInstructionStack(graph, source.id());
        reflowInstructionStack(graph, target.id());
        return conditionNodeId(graph, target.id(), targetGroup, 0);
    }

    // Promote an attached condition back to an independently positioned
    // Scratch condition. It no longer affects the Create schedule until it is
    // dropped into a compatible instruction again.
    public static boolean detachCondition(AdvancedGraphDocument graph, String nodeId, double x, double y,
                                          HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node conditionNode = node(graph, nodeId);
        if (conditionNode == null || !isConditionBlock(conditionNode.type())) return false;
        if (isDetachedCondition(conditionNode)) return moveBlock(graph, nodeId, x, y);
        AdvancedGraphDocument.Node instruction = instructionNode(graph, conditionParent(conditionNode));
        if (instruction == null || !instruction.data().contains(ENTRY_TAG)) return false;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        int group = conditionNode.data().getInt(CONDITION_GROUP_TAG);
        int index = conditionNode.data().getInt(CONDITION_INDEX_TAG);
        if (entry == null || group < 0 || group >= entry.conditions.size()
                || index < 0 || index >= entry.conditions.get(group).size()) return false;
        ScheduleWaitCondition condition = entry.conditions.get(group).remove(index);
        if (entry.conditions.get(group).isEmpty()) entry.conditions.remove(group);
        instruction.data().put(ENTRY_TAG, entry.write(registries));
        graph.nodes().removeIf(node -> node != null && nodeId.equals(node.id()));
        graph.edges().removeIf(edge -> edge != null && (nodeId.equals(edge.fromNode()) || nodeId.equals(edge.toNode())));
        CompoundTag data = new CompoundTag();
        data.put(DETACHED_CONDITION_TAG, condition.write(registries));
        graph.nodes().add(new AdvancedGraphDocument.Node(nodeId, conditionBlockType(condition.getId()),
                title(condition.getId()), x, y, data));
        rebuildConditionBlocks(graph, instruction, entry, registries);
        reflowInstructionStack(graph, instruction.id());
        return true;
    }

    // Add one pure Scratch flow-control block. Flow blocks intentionally use
    // the same document nodes and edges as every other ACC graph surface; the
    // Create schedule adapter ignores them when materialising legacy entries.
    public static boolean appendFlowBlock(AdvancedGraphDocument graph, String flowKind, double x, double y) {
        if (graph == null || flowKind == null || flowKind.isBlank()
                || graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) return false;
        String normalized = flowKind.trim().toLowerCase(java.util.Locale.ROOT);
        String id = "shipping_flow_" + UUID.randomUUID().toString().replace('-', '_');
        CompoundTag data = new CompoundTag();
        data.putString(FLOW_KIND_TAG, normalized);
        if ("repeat".equals(normalized)) data.putInt(FLOW_REPEAT_TAG, 2);
        if ("jump".equals(normalized)) data.putString(FLOW_TARGET_TAG, "");
        graph.nodes().add(new AdvancedGraphDocument.Node(id, flowBlockType(normalized), title(ResourceLocation.parse(
                "shipping_schedule/" + normalized)), x, y, data));
        ensureTemplate(graph);
        return true;
    }

    // Move a visible Scratch block without changing any schedule semantics.
    // A condition follows its owning instruction when that instruction moves.
    public static boolean moveBlock(AdvancedGraphDocument graph, String nodeId, double x, double y) {
        if (graph == null || nodeId == null || !Double.isFinite(x) || !Double.isFinite(y)) return false;
        for (int index = 0; index < graph.nodes().size(); index++) {
            AdvancedGraphDocument.Node node = graph.nodes().get(index);
            if (!nodeId.equals(node.id())) continue;
            double dx = x - node.x();
            double dy = y - node.y();
            graph.nodes().set(index, new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(), x, y, node.data()));
            if (isInstructionBlock(node.type())) moveOwnedConditions(graph, node.id(), dx, dy);
            if (isFlowBlock(node.type())) moveOwnedScratchChildren(graph, node.id(), dx, dy, new LinkedHashSet<>());
            return true;
        }
        return false;
    }

    // Nest a generic flow block inside a Scratch C block while retaining both
    // nodes as normal graph data. Schedule conditions retain their semantic
    // instruction owner and are never reassigned through this visual relation.
    public static boolean setScratchParent(AdvancedGraphDocument graph, String childId, String parentId) {
        AdvancedGraphDocument.Node child = node(graph, childId);
        if (child == null || isConditionBlock(child.type()) && !isDetachedCondition(child)) return false;
        String previousParent = scratchParent(child);
        List<AdvancedGraphDocument.Node> previousSiblings = previousParent.isBlank()
                ? List.of() : scratchChildren(graph, previousParent, "");
        if (parentId == null || parentId.isBlank()) {
            child.data().remove(SCRATCH_PARENT_TAG);
            child.data().remove(SCRATCH_ORDER_TAG);
            if (!previousParent.isBlank()) {
                // Clear the old chain with the moved child still included.
                // Otherwise its former neighbours can retain a stale edge to
                // it after a drag out of the C block.
                clearScratchStackEdges(graph, previousParent, previousSiblings);
                normalizeScratchChildren(graph, previousParent);
            }
            return true;
        }
        AdvancedGraphDocument.Node parent = node(graph, parentId);
        if (parent == null || !isScratchContainer(parent) || parent.id().equals(child.id())) return false;
        Set<String> visited = new LinkedHashSet<>();
        String ancestor = parent.id();
        while (ancestor != null && !ancestor.isBlank() && visited.add(ancestor)) {
            if (child.id().equals(ancestor)) return false;
            AdvancedGraphDocument.Node ancestorNode = node(graph, ancestor);
            ancestor = ancestorNode == null ? "" : scratchParent(ancestorNode);
        }
        child.data().putString(SCRATCH_PARENT_TAG, parent.id());
        if (!previousParent.isBlank() && !previousParent.equals(parent.id())) {
            clearScratchStackEdges(graph, previousParent, previousSiblings);
            normalizeScratchChildren(graph, previousParent);
        }
        normalizeScratchChildren(graph, parent.id());
        return true;
    }

    // Place one generic Scratch block at an explicit index in a C block. This
    // persists drag-and-drop order independently from raw node insertion order
    // and rebuilds only the visual stack edges for the affected containers.
    public static boolean placeScratchChild(AdvancedGraphDocument graph, String childId, String parentId,
                                            int insertionIndex) {
        AdvancedGraphDocument.Node child = node(graph, childId);
        if (child == null || parentId == null || parentId.isBlank()) return false;
        String previousParent = scratchParent(child);
        if (!setScratchParent(graph, childId, parentId)) return false;
        List<AdvancedGraphDocument.Node> siblings = scratchChildren(graph, parentId, childId);
        int clamped = Math.max(0, Math.min(insertionIndex, siblings.size()));
        siblings.add(clamped, child);
        writeScratchSiblingOrder(siblings);
        rebuildScratchStackEdges(graph, parentId, siblings);
        if (!previousParent.isBlank() && !previousParent.equals(parentId)) {
            normalizeScratchChildren(graph, previousParent);
        }
        return true;
    }

    // Return a persistent sibling insertion index for a top/bottom snap.
    public static int scratchChildInsertionIndex(AdvancedGraphDocument graph, String parentId,
                                                 String siblingId, boolean after) {
        List<AdvancedGraphDocument.Node> siblings = scratchChildren(graph, parentId, "");
        for (int index = 0; index < siblings.size(); index++) {
            if (siblingId.equals(siblings.get(index).id())) return index + (after ? 1 : 0);
        }
        return siblings.size();
    }

    // Store a normal graph edge for a Scratch stack connection. It deliberately
    // uses distinct ports so Create's legacy schedule ordering edges stay
    // authoritative for actual autopilot execution.
    public static boolean connectScratchBlocks(AdvancedGraphDocument graph, String fromId, String toId) {
        if (graph == null || fromId == null || toId == null || fromId.equals(toId)
                || node(graph, fromId) == null || node(graph, toId) == null) return false;
        graph.edges().removeIf(edge -> edge != null && fromId.equals(edge.fromNode())
                && "scratch_next".equals(edge.fromPort()));
        if (graph.edges().size() >= AdvancedGraphDocument.MAX_EDGES) return false;
        graph.edges().add(new AdvancedGraphDocument.Edge(
                "shipping_scratch_edge_" + UUID.randomUUID().toString().replace('-', '_'),
                fromId, "scratch_next", toId, "scratch_next"));
        return true;
    }

    // Insert one block directly before another in a Scratch-only statement
    // chain. This leaves Create's next edges untouched, so legacy schedule
    // execution order remains owned by its normal instruction chain.
    public static boolean insertScratchBlockBefore(AdvancedGraphDocument graph, String nodeId, String beforeId) {
        if (graph == null || nodeId == null || beforeId == null || nodeId.equals(beforeId)
                || node(graph, nodeId) == null || node(graph, beforeId) == null) return false;
        String previous = "";
        for (AdvancedGraphDocument.Edge edge : graph.edges()) {
            if (edge != null && beforeId.equals(edge.toNode())
                    && "scratch_next".equals(edge.fromPort()) && "scratch_next".equals(edge.toPort())) {
                previous = edge.fromNode();
                break;
            }
        }
        if (!previous.isBlank()) connectScratchBlocks(graph, previous, nodeId);
        return connectScratchBlocks(graph, nodeId, beforeId);
    }

    // Remove a non-Create Scratch flow block and detach any nested children so
    // an editor never leaves invisible parent references behind.
    public static boolean removeFlowBlock(AdvancedGraphDocument graph, String nodeId) {
        AdvancedGraphDocument.Node flow = node(graph, nodeId);
        if (flow == null || !isFlowBlock(flow.type())) return false;
        String outerParent = scratchParent(flow);
        List<AdvancedGraphDocument.Node> outerSiblings = outerParent.isBlank()
                ? List.of() : scratchChildren(graph, outerParent, "");
        graph.nodes().removeIf(node -> node != null && nodeId.equals(node.id()));
        graph.edges().removeIf(edge -> edge != null && (nodeId.equals(edge.fromNode()) || nodeId.equals(edge.toNode())));
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node != null && nodeId.equals(node.data().getString(SCRATCH_PARENT_TAG))) {
                node.data().remove(SCRATCH_PARENT_TAG);
                node.data().remove(SCRATCH_ORDER_TAG);
            }
        }
        if (!outerParent.isBlank()) {
            clearScratchStackEdges(graph, outerParent, outerSiblings);
            normalizeScratchChildren(graph, outerParent);
        }
        return true;
    }

    // Reorder a real schedule instruction at a specific destination index.
    // This is used by the Scratch connector snap, not by freeform movement.
    public static boolean moveInstructionToIndex(AdvancedGraphDocument graph, String nodeId, int targetIndex) {
        if (graph == null) return false;
        List<AdvancedGraphDocument.Node> ordered = new ArrayList<>(orderedNodes(graph));
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(nodeId)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;
        AdvancedGraphDocument.Node moved = ordered.remove(index);
        targetIndex = Math.max(0, Math.min(targetIndex, ordered.size()));
        ordered.add(targetIndex, moved);
        rebuildSequenceEdges(graph, ordered);
        return index != targetIndex;
    }

    // Remove one instruction and rebuild the sequence edges.
    public static boolean removeInstruction(AdvancedGraphDocument graph, String nodeId) {
        AdvancedGraphDocument.Node node = node(graph, nodeId);
        if (node == null || !isInstructionBlock(node.type())) return false;
        Set<String> removedIds = new LinkedHashSet<>();
        removedIds.add(node.id());
        for (AdvancedGraphDocument.Node candidate : graph.nodes()) {
            if (isConditionBlock(candidate.type()) && node.id().equals(conditionParent(candidate))) {
                removedIds.add(candidate.id());
            }
        }
        graph.nodes().removeIf(candidate -> removedIds.contains(candidate.id()));
        graph.edges().removeIf(edge -> edge != null
                && (removedIds.contains(edge.fromNode()) || removedIds.contains(edge.toNode())));
        rebuildSequenceEdges(graph);
        return true;
    }

    // Remove one condition block and update the serialized Create entry it belongs to.
    public static boolean removeCondition(AdvancedGraphDocument graph, String nodeId,
                                          HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node conditionNode = node(graph, nodeId);
        if (conditionNode == null || !isConditionBlock(conditionNode.type())) return false;
        if (isDetachedCondition(conditionNode)) {
            String parentId = scratchParent(conditionNode);
            List<AdvancedGraphDocument.Node> siblings = parentId.isBlank()
                    ? List.of() : scratchChildren(graph, parentId, "");
            graph.nodes().removeIf(node -> node != null && nodeId.equals(node.id()));
            graph.edges().removeIf(edge -> edge != null && (nodeId.equals(edge.fromNode()) || nodeId.equals(edge.toNode())));
            if (!parentId.isBlank()) {
                clearScratchStackEdges(graph, parentId, siblings);
                normalizeScratchChildren(graph, parentId);
            }
            return true;
        }
        AdvancedGraphDocument.Node instruction = node(graph, conditionParent(conditionNode));
        if (instruction == null || !instruction.data().contains(ENTRY_TAG)) return false;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        int group = conditionNode.data().getInt(CONDITION_GROUP_TAG);
        int index = conditionNode.data().getInt(CONDITION_INDEX_TAG);
        if (entry == null || group < 0 || group >= entry.conditions.size()
                || index < 0 || index >= entry.conditions.get(group).size()) {
            return false;
        }
        entry.conditions.get(group).remove(index);
        if (entry.conditions.get(group).isEmpty()) entry.conditions.remove(group);
        instruction.data().put(ENTRY_TAG, entry.write(registries));
        rebuildConditionBlocks(graph, instruction, entry, registries);
        reflowInstructionStack(graph, instruction.id());
        return true;
    }

    // Move a block in schedule order and rebuild its graph edges.
    public static boolean moveInstruction(AdvancedGraphDocument graph, String nodeId, int offset) {
        if (graph == null || offset == 0) return false;
        List<AdvancedGraphDocument.Node> ordered = new ArrayList<>(orderedNodes(graph));
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id().equals(nodeId)) {
                index = i;
                break;
            }
        }
        int target = index + offset;
        if (index < 0 || target < 0 || target >= ordered.size()) return false;
        AdvancedGraphDocument.Node moved = ordered.remove(index);
        ordered.add(target, moved);
        graph.nodes().removeIf(node -> isInstructionBlock(node.type()));
        graph.nodes().addAll(ordered);
        rebuildSequenceEdges(graph, ordered);
        return true;
    }

    // Toggle the schedule's normal Create cyclic flag.
    public static void setCyclic(AdvancedGraphDocument graph, boolean cyclic) {
        if (graph == null) return;
        ensureTemplate(graph);
        graph.variables().put(CYCLIC_VARIABLE, AdvancedGraphDocument.Value.bool(cyclic));
    }

    // Get a block type id for an instruction.
    public static String instructionBlockType(ResourceLocation id) {
        return INSTRUCTION_PREFIX + (id == null ? "minecraft:destination" : id);
    }

    // Get a block type id for a condition.
    public static String conditionBlockType(ResourceLocation id) {
        return CONDITION_PREFIX + (id == null ? "minecraft:delay" : id);
    }

    // Get a graph type id for a reusable Scratch flow-control block.
    public static String flowBlockType(String flowKind) {
        return FLOW_PREFIX + (flowKind == null || flowKind.isBlank() ? "start"
                : flowKind.trim().toLowerCase(java.util.Locale.ROOT));
    }

    // Return the registered Create instruction IDs without exposing Create's
    // mutable supplier list to programmatic schedule editors.
    public static List<ResourceLocation> instructionTypes() {
        return Schedule.INSTRUCTION_TYPES.stream().map(Pair::getFirst).toList();
    }

    // Return the registered Create wait-condition IDs.
    public static List<ResourceLocation> conditionTypes() {
        return Schedule.CONDITION_TYPES.stream().map(Pair::getFirst).toList();
    }

    // Check if this is a persisted instruction node.
    public static boolean isInstructionBlock(String type) {
        return type != null && type.startsWith(INSTRUCTION_PREFIX);
    }

    // Check if this node is a visible condition block owned by one schedule instruction.
    public static boolean isConditionBlock(String type) {
        return type != null && type.startsWith(CONDITION_PREFIX);
    }

    // Check if a block is a non-Create Scratch control-flow node.
    public static boolean isFlowBlock(String type) {
        return type != null && type.startsWith(FLOW_PREFIX);
    }

    // Resolve the reusable flow kind stored in a block type.
    public static String flowKind(String type) {
        return !isFlowBlock(type) ? "" : type.substring(FLOW_PREFIX.length());
    }

    // The only generic C blocks are the two structured flow controls. Start
    // and End are flow blocks too, but must stay terminal Scratch shapes.
    public static boolean isScratchContainer(AdvancedGraphDocument.Node node) {
        if (node == null || !isFlowBlock(node.type())) return false;
        String kind = flowKind(node.type());
        return "repeat".equals(kind) || "loop".equals(kind);
    }

    // Resolve a resource id embedded in an instruction block type.
    public static ResourceLocation instructionId(String type) {
        if (!isInstructionBlock(type)) return null;
        try {
            return ResourceLocation.parse(type.substring(INSTRUCTION_PREFIX.length()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // Keep a graph explicitly identified as a shipping schedule document.
    public static void ensureTemplate(AdvancedGraphDocument graph) {
        if (graph == null) return;
        graph.setTemplateId(TEMPLATE_ID);
        graph.variables().putIfAbsent(CYCLIC_VARIABLE, AdvancedGraphDocument.Value.bool(false));
    }

    // Resolve the graph's next-edge chain, appending disconnected legacy blocks in their stored order.
    public static List<AdvancedGraphDocument.Node> orderedNodes(AdvancedGraphDocument graph) {
        if (graph == null) return List.of();
        Map<String, AdvancedGraphDocument.Node> nodes = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node != null && isInstructionBlock(node.type())) nodes.put(node.id(), node);
        }
        Map<String, String> next = new LinkedHashMap<>();
        Set<String> targets = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Edge edge : graph.edges()) {
            if (edge == null || !NEXT_PORT.equals(edge.fromPort()) || !NEXT_PORT.equals(edge.toPort())
                    || !nodes.containsKey(edge.fromNode()) || !nodes.containsKey(edge.toNode())) continue;
            if (next.putIfAbsent(edge.fromNode(), edge.toNode()) == null) targets.add(edge.toNode());
        }
        List<AdvancedGraphDocument.Node> ordered = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (String id : nodes.keySet()) {
            if (!targets.contains(id)) appendChain(id, nodes, next, visited, ordered);
        }
        for (String id : nodes.keySet()) appendChain(id, nodes, next, visited, ordered);
        return ordered;
    }

    // Get every Scratch block in the presentation order: an instruction followed
    // by its real condition child nodes, rather than a secondary condition UI.
    public static List<AdvancedGraphDocument.Node> orderedBlocks(AdvancedGraphDocument graph) {
        if (graph == null) return List.of();
        List<AdvancedGraphDocument.Node> ordered = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node instruction : orderedNodes(graph)) {
            ordered.add(instruction);
            visited.add(instruction.id());
            List<AdvancedGraphDocument.Node> conditions = new ArrayList<>();
            for (AdvancedGraphDocument.Node candidate : graph.nodes()) {
                if (isConditionBlock(candidate.type()) && instruction.id().equals(conditionParent(candidate))) {
                    conditions.add(candidate);
                }
            }
            conditions.sort(java.util.Comparator
                    .comparingInt((AdvancedGraphDocument.Node condition) -> condition.data().getInt(CONDITION_GROUP_TAG))
                    .thenComparingInt(condition -> condition.data().getInt(CONDITION_INDEX_TAG)));
            for (AdvancedGraphDocument.Node condition : conditions) {
                ordered.add(condition);
                visited.add(condition.id());
            }
        }
        // Include flow-control and disconnected blocks too. They remain normal
        // graph nodes, letting one Schedule document contain several separate
        // Scratch script groups even though only instruction nodes materialise
        // into Create's linear legacy schedule runtime.
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node == null || visited.contains(node.id()) || !scratchParent(node).isBlank()) continue;
            appendScratchBranch(graph, node, ordered, visited, new LinkedHashSet<>());
        }
        // Preserve malformed/disconnected parent data as a visible free block
        // rather than hiding it from the editor.
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node != null && visited.add(node.id())) ordered.add(node);
        }
        return ordered;
    }

    // Append a generic branch in its persisted sibling order. Semantic Create
    // conditions are already emitted directly after their instruction above.
    private static void appendScratchBranch(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node,
                                            List<AdvancedGraphDocument.Node> ordered, Set<String> visited,
                                            Set<String> visiting) {
        if (node == null || !visited.add(node.id()) || !visiting.add(node.id())) return;
        ordered.add(node);
        for (AdvancedGraphDocument.Node child : scratchChildren(graph, node.id(), "")) {
            appendScratchBranch(graph, child, ordered, visited, visiting);
        }
        visiting.remove(node.id());
    }

    // Resolve direct generic C-block children. Attached Create conditions use
    // CONDITION_PARENT_TAG and therefore remain under the schedule runtime,
    // not this visual-only ordering contract.
    private static List<AdvancedGraphDocument.Node> scratchChildren(AdvancedGraphDocument graph, String parentId,
                                                                      String excludedId) {
        List<AdvancedGraphDocument.Node> children = new ArrayList<>();
        if (graph == null || parentId == null || parentId.isBlank()) return children;
        int documentIndex = 0;
        Map<String, Integer> fallbackOrder = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node candidate : graph.nodes()) {
            if (candidate != null) fallbackOrder.put(candidate.id(), documentIndex++);
            if (candidate == null || candidate.id().equals(excludedId)
                    || !parentId.equals(scratchParent(candidate))
                    || isConditionBlock(candidate.type()) && !isDetachedCondition(candidate)) continue;
            children.add(candidate);
        }
        children.sort(java.util.Comparator
                .comparingInt((AdvancedGraphDocument.Node candidate) -> candidate.data().contains(SCRATCH_ORDER_TAG,
                        Tag.TAG_INT) ? candidate.data().getInt(SCRATCH_ORDER_TAG) : Integer.MAX_VALUE)
                .thenComparingInt(candidate -> fallbackOrder.getOrDefault(candidate.id(), Integer.MAX_VALUE)));
        return children;
    }

    // Store compact sibling ranks after every visual nested drop.
    private static void writeScratchSiblingOrder(List<AdvancedGraphDocument.Node> siblings) {
        for (int index = 0; index < siblings.size(); index++) {
            siblings.get(index).data().putInt(SCRATCH_ORDER_TAG, index);
        }
    }

    private static void normalizeScratchChildren(AdvancedGraphDocument graph, String parentId) {
        List<AdvancedGraphDocument.Node> siblings = scratchChildren(graph, parentId, "");
        writeScratchSiblingOrder(siblings);
        rebuildScratchStackEdges(graph, parentId, siblings);
    }

    // Remove one container's old direct child chain before rebuilding it. The
    // caller may pass the pre-move sibling set, which is essential when a child
    // has just been promoted out of the container.
    private static void clearScratchStackEdges(AdvancedGraphDocument graph, String parentId,
                                               List<AdvancedGraphDocument.Node> siblings) {
        if (graph == null || parentId == null || parentId.isBlank()) return;
        Set<String> ids = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node sibling : siblings) ids.add(sibling.id());
        graph.edges().removeIf(edge -> edge != null && "scratch_next".equals(edge.fromPort())
                && "scratch_next".equals(edge.toPort())
                && (parentId.equals(edge.fromNode())
                || ids.contains(edge.fromNode()) && ids.contains(edge.toNode())));
    }

    // Rebuild only the visual connections belonging to a single C block. An
    // inner C block's own child edges stay intact.
    private static void rebuildScratchStackEdges(AdvancedGraphDocument graph, String parentId,
                                                 List<AdvancedGraphDocument.Node> siblings) {
        if (graph == null || parentId == null || parentId.isBlank()) return;
        Set<String> ids = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node sibling : siblings) ids.add(sibling.id());
        clearScratchStackEdges(graph, parentId, siblings);
        String previous = parentId;
        for (AdvancedGraphDocument.Node sibling : siblings) {
            if (graph.edges().size() >= AdvancedGraphDocument.MAX_EDGES) break;
            graph.edges().add(new AdvancedGraphDocument.Edge(
                    "shipping_scratch_edge_" + UUID.randomUUID().toString().replace('-', '_'),
                    previous, "scratch_next", sibling.id(), "scratch_next"));
            previous = sibling.id();
        }
    }

    // Append one acyclic edge chain.
    private static void appendChain(String id, Map<String, AdvancedGraphDocument.Node> nodes,
                                    Map<String, String> next, Set<String> visited,
                                    List<AdvancedGraphDocument.Node> ordered) {
        String current = id;
        while (current != null && visited.add(current)) {
            AdvancedGraphDocument.Node node = nodes.get(current);
            if (node == null) break;
            ordered.add(node);
            current = next.get(current);
        }
    }

    // Rebuild the semantic next edges while leaving unrelated metadata edges untouched.
    private static void rebuildSequenceEdges(AdvancedGraphDocument graph) {
        rebuildSequenceEdges(graph, orderedNodes(graph));
    }

    // Rebuild sequence edges from a caller-provided order when the node list
    // was just reordered and the old edges would otherwise win the ordering.
    private static void rebuildSequenceEdges(AdvancedGraphDocument graph,
                                             List<AdvancedGraphDocument.Node> ordered) {
        graph.edges().removeIf(edge -> edge != null && NEXT_PORT.equals(edge.fromPort()) && NEXT_PORT.equals(edge.toPort()));
        for (int index = 1; index < ordered.size() && graph.edges().size() < AdvancedGraphDocument.MAX_EDGES; index++) {
            graph.edges().add(new AdvancedGraphDocument.Edge(
                    "shipping_edge_" + UUID.randomUUID().toString().replace('-', '_'),
                    ordered.get(index - 1).id(), NEXT_PORT, ordered.get(index).id(), NEXT_PORT));
        }
    }

    // Keep the nested visual condition script attached when its instruction is
    // freely moved on the Scratch canvas.
    private static void moveOwnedConditions(AdvancedGraphDocument graph, String instructionId,
                                            double dx, double dy) {
        for (int index = 0; index < graph.nodes().size(); index++) {
            AdvancedGraphDocument.Node candidate = graph.nodes().get(index);
            if (!isConditionBlock(candidate.type()) || !instructionId.equals(conditionParent(candidate))) continue;
            graph.nodes().set(index, new AdvancedGraphDocument.Node(candidate.id(), candidate.type(),
                    candidate.label(), candidate.x() + dx, candidate.y() + dy, candidate.data()));
        }
    }

    private static void moveOwnedScratchChildren(AdvancedGraphDocument graph, String parentId,
                                                 double dx, double dy, Set<String> visited) {
        if (graph == null || !visited.add(parentId)) return;
        for (int index = 0; index < graph.nodes().size(); index++) {
            AdvancedGraphDocument.Node candidate = graph.nodes().get(index);
            if (!parentId.equals(candidate.data().getString(SCRATCH_PARENT_TAG))) continue;
            graph.nodes().set(index, new AdvancedGraphDocument.Node(candidate.id(), candidate.type(),
                    candidate.label(), candidate.x() + dx, candidate.y() + dy, candidate.data()));
            moveOwnedScratchChildren(graph, candidate.id(), dx, dy, visited);
        }
    }

    // Recreate condition nodes after a Create ScheduleEntry changes. The root
    // retains the exact serialized entry; child nodes make each condition a
    // first-class block in the shared graph document contract.
    private static void rebuildConditionBlocks(AdvancedGraphDocument graph,
                                               AdvancedGraphDocument.Node instruction,
                                               ScheduleEntry entry,
                                               HolderLookup.Provider registries) {
        if (graph == null || instruction == null || entry == null) return;
        Set<String> removed = new LinkedHashSet<>();
        Map<String, AdvancedGraphDocument.Node> previous = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (isConditionBlock(node.type()) && instruction.id().equals(conditionParent(node))) {
                removed.add(node.id());
                previous.put(node.data().getInt(CONDITION_GROUP_TAG) + ":"
                        + node.data().getInt(CONDITION_INDEX_TAG), node);
            }
        }
        graph.nodes().removeIf(node -> removed.contains(node.id()));
        graph.edges().removeIf(edge -> edge != null
                && (removed.contains(edge.fromNode()) || removed.contains(edge.toNode())));
        int visualIndex = 0;
        for (int group = 0; group < entry.conditions.size(); group++) {
            List<ScheduleWaitCondition> conditions = entry.conditions.get(group);
            for (int index = 0; index < conditions.size(); index++) {
                ScheduleWaitCondition condition = conditions.get(index);
                if (condition == null) continue;
                String conditionId = "shipping_condition_" + UUID.randomUUID().toString().replace('-', '_');
                CompoundTag data = new CompoundTag();
                data.putString(CONDITION_PARENT_TAG, instruction.id());
                data.putInt(CONDITION_GROUP_TAG, group);
                data.putInt(CONDITION_INDEX_TAG, index);
                AdvancedGraphDocument.Node prior = previous.get(group + ":" + index);
                String visualId = prior == null ? conditionId : prior.id();
                graph.nodes().add(new AdvancedGraphDocument.Node(visualId,
                        conditionBlockType(condition.getId()), title(condition.getId()),
                        instruction.x() + 23, instruction.y() + SCRATCH_CONDITION_TOP
                                + visualIndex++ * SCRATCH_CONDITION_STEP, data));
                if (graph.edges().size() < AdvancedGraphDocument.MAX_EDGES) {
                    graph.edges().add(new AdvancedGraphDocument.Edge(
                            "shipping_condition_edge_" + UUID.randomUUID().toString().replace('-', '_'),
                            instruction.id(), CONDITION_PORT, visualId, CONDITION_PORT));
                }
            }
        }
    }

    // Convert one entry's condition columns into the height of its enclosing Scratch C block.
    private static int scratchHeightForConditions(List<List<ScheduleWaitCondition>> conditions) {
        int count = 0;
        if (conditions != null) {
            for (List<ScheduleWaitCondition> group : conditions) {
                if (group != null) count += group.size();
            }
        }
        return count == 0 ? SCRATCH_STATEMENT_HEIGHT
                : SCRATCH_CONDITION_TOP + count * SCRATCH_CONDITION_STEP + SCRATCH_CONDITION_FOOT;
    }

    // Keep following root schedule steps seated directly on the C block's
    // lower connector whenever adding/removing a condition changes its height.
    private static void reflowInstructionStack(AdvancedGraphDocument graph, String fromNodeId) {
        if (graph == null || fromNodeId == null) return;
        List<AdvancedGraphDocument.Node> ordered = orderedNodes(graph);
        int start = -1;
        for (int index = 0; index < ordered.size(); index++) {
            if (fromNodeId.equals(ordered.get(index).id())) {
                start = index;
                break;
            }
        }
        if (start < 0) return;
        for (int index = start + 1; index < ordered.size(); index++) {
            AdvancedGraphDocument.Node previous = ordered.get(index - 1);
            AdvancedGraphDocument.Node current = ordered.get(index);
            moveBlock(graph, current.id(), previous.x(), previous.y() + scratchVisualHeight(graph, previous.id()));
        }
    }

    // Find one node by id.
    private static AdvancedGraphDocument.Node node(AdvancedGraphDocument graph, String nodeId) {
        if (graph == null || nodeId == null) return null;
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (nodeId.equals(node.id())) return node;
        }
        return null;
    }

    // Resolve the owning instruction for either an instruction or condition selection.
    private static AdvancedGraphDocument.Node instructionNode(AdvancedGraphDocument graph, String nodeId) {
        AdvancedGraphDocument.Node selected = node(graph, nodeId);
        if (selected == null) return null;
        return isInstructionBlock(selected.type()) ? selected : node(graph, conditionParent(selected));
    }

    // Read a condition node's owner without allowing arbitrary data to create a link.
    public static String conditionParent(AdvancedGraphDocument.Node node) {
        return node == null || !isConditionBlock(node.type()) ? ""
                : node.data().getString(CONDITION_PARENT_TAG);
    }

    // Check whether a condition currently exists as a free Scratch block.
    public static boolean isDetachedCondition(AdvancedGraphDocument.Node node) {
        return node != null && isConditionBlock(node.type()) && node.data().contains(DETACHED_CONDITION_TAG);
    }

    // Resolve the visual parent used by the generic Scratch renderer. Create
    // conditions retain their real instruction parent; flow blocks can opt in
    // through the ordinary node-data field shared by all graph documents.
    public static String scratchParent(AdvancedGraphDocument.Node node) {
        if (node == null) return "";
        String conditionParent = conditionParent(node);
        return !conditionParent.isBlank() ? conditionParent : node.data().getString(SCRATCH_PARENT_TAG);
    }

    // Return the visible height consumed by one block, including nested Create conditions.
    public static int scratchVisualHeight(AdvancedGraphDocument graph, String nodeId) {
        AdvancedGraphDocument.Node node = node(graph, nodeId);
        if (node == null) return SCRATCH_STATEMENT_HEIGHT;
        if (isInstructionBlock(node.type())) {
            int conditions = 0;
            for (AdvancedGraphDocument.Node candidate : graph.nodes()) {
                if (candidate != null && isConditionBlock(candidate.type())
                        && node.id().equals(conditionParent(candidate))) {
                    conditions++;
                }
            }
            return conditions == 0 ? SCRATCH_STATEMENT_HEIGHT
                    : SCRATCH_CONDITION_TOP + conditions * SCRATCH_CONDITION_STEP + SCRATCH_CONDITION_FOOT;
        }
        String flow = flowKind(node.type());
        return isFlowBlock(node.type()) && ("repeat".equals(flow) || "loop".equals(flow))
                ? SCRATCH_CONDITION_TOP + SCRATCH_STATEMENT_HEIGHT + SCRATCH_CONDITION_FOOT
                : SCRATCH_STATEMENT_HEIGHT;
    }

    // Read how many Create item parameters the selected instruction or wait condition owns.
    public static int inputSlotCount(AdvancedGraphDocument graph, String nodeId,
                                     HolderLookup.Provider registries) {
        IScheduleInput input = scheduleInput(graph, nodeId, registries);
        return input == null ? 0 : Math.max(0, Math.min(2, input.slotsTargeted()));
    }

    // Read one Create item parameter without exposing its implementation object to the UI.
    public static ItemStack inputSlot(AdvancedGraphDocument graph, String nodeId, int slot,
                                      HolderLookup.Provider registries) {
        IScheduleInput input = scheduleInput(graph, nodeId, registries);
        if (input == null || slot < 0 || slot >= inputSlotCount(graph, nodeId, registries)) return ItemStack.EMPTY;
        ItemStack stack = input.getItem(slot);
        return stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    // Write one Create item parameter through the parent ScheduleEntry and refresh child blocks.
    public static boolean setInputSlot(AdvancedGraphDocument graph, String nodeId, int slot, ItemStack stack,
                                       HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node node = node(graph, nodeId);
        if (node != null && isDetachedCondition(node)) {
            ScheduleWaitCondition condition = detachedCondition(node, registries);
            if (condition == null || slot < 0 || slot >= Math.min(2, condition.slotsTargeted())) return false;
            condition.setItem(slot, stack == null ? ItemStack.EMPTY : stack.copyWithCount(1));
            node.data().put(DETACHED_CONDITION_TAG, condition.write(registries));
            return true;
        }
        AdvancedGraphDocument.Node instruction = instructionNode(graph, nodeId);
        if (node == null || instruction == null || !instruction.data().contains(ENTRY_TAG)) return false;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        IScheduleInput input = scheduleInput(entry, node);
        if (entry == null || input == null || slot < 0 || slot >= Math.min(2, input.slotsTargeted())) return false;
        input.setItem(slot, stack == null ? ItemStack.EMPTY : stack.copyWithCount(1));
        instruction.data().put(ENTRY_TAG, entry.write(registries));
        rebuildConditionBlocks(graph, instruction, entry, registries);
        return true;
    }

    // Label a Create item parameter with familiar frequency wording where appropriate.
    public static String inputSlotLabel(AdvancedGraphDocument.Node node, int slot) {
        if (node != null && node.type().endsWith("redstone_link")) return slot == 0 ? "Frequency A" : "Frequency B";
        return slot == 0 ? "Parameter A" : "Parameter B";
    }

    // Return every simple editable property carried by a schedule or flow
    // block. This intentionally exposes the underlying ScheduleDataEntry data
    // rather than duplicating individual Create configuration screens.
    public static Map<String, String> editableProperties(AdvancedGraphDocument graph, String nodeId,
                                                           HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node node = node(graph, nodeId);
        if (node == null) return Map.of();
        CompoundTag data = editableData(graph, node, registries);
        if (data == null) return Map.of();
        Map<String, String> properties = new LinkedHashMap<>();
        for (String key : data.getAllKeys()) {
            if (FLOW_KIND_TAG.equals(key) || SCRATCH_PARENT_TAG.equals(key) || SCRATCH_ORDER_TAG.equals(key)
                    || CONDITION_PARENT_TAG.equals(key)
                    || CONDITION_GROUP_TAG.equals(key) || CONDITION_INDEX_TAG.equals(key)
                    || DETACHED_CONDITION_TAG.equals(key)
                    || ENTRY_TAG.equals(key)) continue;
            Tag value = data.get(key);
            if (value != null && isSimpleProperty(value)) properties.put(key, value.getAsString());
        }
        addImplicitEditableProperties(node, properties);
        return Map.copyOf(properties);
    }

    // Give raw data properties friendly labels in both the canvas field and
    // the shared ACC config sidebar.
    public static String propertyLabel(AdvancedGraphDocument.Node node, String key) {
        if (key == null) return "Value";
        if ("Text".equals(key) && node != null && node.type().endsWith("destination")) return "Destination";
        if ("Text".equals(key) && node != null && node.type().endsWith("park_at")) return "Dock";
        if (FLOW_REPEAT_TAG.equals(key)) return "Repeat count";
        if (FLOW_TARGET_TAG.equals(key)) return "Jump target";
        return key.replace('_', ' ');
    }

    // Get the first useful property to show inline in the Scratch input pill.
    public static String primaryProperty(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node,
                                         HolderLookup.Provider registries) {
        if (node == null) return "";
        Map<String, String> properties = editableProperties(graph, node.id(), registries);
        String key = primaryPropertyKey(properties);
        return key == null ? "" : properties.getOrDefault(key, "");
    }

    // Resolve the primary editable key even when its current value is intentionally blank.
    public static String primaryPropertyKey(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node,
                                            HolderLookup.Provider registries) {
        if (node == null) return "";
        return primaryPropertyKey(editableProperties(graph, node.id(), registries));
    }

    private static String primaryPropertyKey(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return "";
        for (String preferred : List.of("Text", "Destination", "Dock", "Threshold", "Value", "Time",
                FLOW_REPEAT_TAG, FLOW_TARGET_TAG)) {
            if (properties.containsKey(preferred)) return preferred;
        }
        return properties.keySet().stream().findFirst().orElse("");
    }

    // Write one simple property through the authoritative ScheduleEntry tag.
    // Re-serialising the entry preserves Create's normal instruction and item
    // data while making the Scratch sidebar and inline field edit the same
    // paired shipping-schedule item state.
    public static boolean setProperty(AdvancedGraphDocument graph, String nodeId, String key, String value,
                                      HolderLookup.Provider registries) {
        if (graph == null || nodeId == null || key == null || key.isBlank()) return false;
        AdvancedGraphDocument.Node node = node(graph, nodeId);
        if (node == null) return false;
        if (isFlowBlock(node.type())) {
            putSimpleValue(node.data(), key, value);
            return true;
        }
        if (isDetachedCondition(node)) {
            ScheduleWaitCondition condition = detachedCondition(node, registries);
            if (condition == null) return false;
            ensureImplicitProperty(condition.getData(), node, key);
            putSimpleValue(condition.getData(), key, value);
            node.data().put(DETACHED_CONDITION_TAG, condition.write(registries));
            return true;
        }
        AdvancedGraphDocument.Node instruction = instructionNode(graph, node.id());
        if (instruction == null || !instruction.data().contains(ENTRY_TAG)) return false;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        if (entry == null || entry.instruction == null) return false;
        CompoundTag data;
        if (isConditionBlock(node.type())) {
            int group = node.data().getInt(CONDITION_GROUP_TAG);
            int index = node.data().getInt(CONDITION_INDEX_TAG);
            if (group < 0 || group >= entry.conditions.size()
                    || index < 0 || index >= entry.conditions.get(group).size()) return false;
            data = entry.conditions.get(group).get(index).getData();
        } else {
            data = entry.instruction.getData();
        }
        ensureImplicitProperty(data, node, key);
        putSimpleValue(data, key, value);
        instruction.data().put(ENTRY_TAG, entry.write(registries));
        // Rebuild the child descriptions to keep condition values and their
        // inline fields synchronised. Existing child ids and positions survive.
        rebuildConditionBlocks(graph, instruction, entry, registries);
        return true;
    }

    private static CompoundTag editableData(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node,
                                             HolderLookup.Provider registries) {
        if (isFlowBlock(node.type())) return node.data();
        if (isDetachedCondition(node)) {
            ScheduleWaitCondition condition = detachedCondition(node, registries);
            return condition == null ? null : condition.getData();
        }
        AdvancedGraphDocument.Node instruction = instructionNode(graph, node.id());
        if (instruction == null || !instruction.data().contains(ENTRY_TAG)) return null;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        if (entry == null || entry.instruction == null) return null;
        if (!isConditionBlock(node.type())) return entry.instruction.getData();
        int group = node.data().getInt(CONDITION_GROUP_TAG);
        int index = node.data().getInt(CONDITION_INDEX_TAG);
        if (group < 0 || group >= entry.conditions.size()
                || index < 0 || index >= entry.conditions.get(group).size()) return null;
        return entry.conditions.get(group).get(index).getData();
    }

    // Resolve an editable ScheduleDataEntry as Create's public schedule-input interface.
    private static IScheduleInput scheduleInput(AdvancedGraphDocument graph, String nodeId,
                                                HolderLookup.Provider registries) {
        AdvancedGraphDocument.Node node = node(graph, nodeId);
        if (node != null && isDetachedCondition(node)) return detachedCondition(node, registries);
        AdvancedGraphDocument.Node instruction = instructionNode(graph, nodeId);
        if (node == null || instruction == null || !instruction.data().contains(ENTRY_TAG)) return null;
        ScheduleEntry entry = ScheduleEntry.fromTag(registries, instruction.data().getCompound(ENTRY_TAG));
        return scheduleInput(entry, node);
    }

    // Resolve the selected entry member after its parent ScheduleEntry has been parsed.
    private static IScheduleInput scheduleInput(ScheduleEntry entry, AdvancedGraphDocument.Node node) {
        if (entry == null || entry.instruction == null || node == null) return null;
        if (!isConditionBlock(node.type())) return entry.instruction;
        int group = node.data().getInt(CONDITION_GROUP_TAG);
        int index = node.data().getInt(CONDITION_INDEX_TAG);
        if (group < 0 || group >= entry.conditions.size() || index < 0 || index >= entry.conditions.get(group).size()) {
            return null;
        }
        return entry.conditions.get(group).get(index);
    }

    private static boolean isSimpleProperty(Tag value) {
        byte type = value.getId();
        return type == Tag.TAG_STRING || type == Tag.TAG_BYTE || type == Tag.TAG_SHORT || type == Tag.TAG_INT
                || type == Tag.TAG_LONG || type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE;
    }

    // Several of Create's text and selector controls intentionally leave their
    // default value out of NBT. The Scratch inspector must still expose them:
    // absent data is a default, not a lack of configuration.
    private static void addImplicitEditableProperties(AdvancedGraphDocument.Node node,
                                                       Map<String, String> properties) {
        if (node == null || properties == null) return;
        String type = node.type();
        if (isInstructionBlock(type)) {
            ResourceLocation id = instructionId(type);
            if (id != null && switch (id.getPath()) {
                case "destination", "package_retrieval", "rename", "park_at" -> true;
                default -> false;
            }) properties.putIfAbsent("Text", "");
        }
        if (isConditionBlock(type) && type.endsWith("redstone_link")) {
            properties.putIfAbsent("Inverted", "0");
        }
    }

    private static void ensureImplicitProperty(CompoundTag data, AdvancedGraphDocument.Node node, String key) {
        if (data == null || data.contains(key) || key == null) return;
        if ("Inverted".equals(key) && node != null && node.type().endsWith("redstone_link")) {
            data.putInt(key, 0);
        }
    }

    private static void putSimpleValue(CompoundTag data, String key, String requested) {
        if (data == null || key == null || key.isBlank()) return;
        String value = requested == null ? "" : requested.trim();
        Tag existing = data.get(key);
        try {
            if (existing != null) {
                switch (existing.getId()) {
                    case Tag.TAG_BYTE -> data.putByte(key, "true".equalsIgnoreCase(value) ? (byte) 1
                            : "false".equalsIgnoreCase(value) ? (byte) 0 : Byte.parseByte(value));
                    case Tag.TAG_SHORT -> data.putShort(key, Short.parseShort(value));
                    case Tag.TAG_INT -> data.putInt(key, Integer.parseInt(value));
                    case Tag.TAG_LONG -> data.putLong(key, Long.parseLong(value));
                    case Tag.TAG_FLOAT -> data.putFloat(key, Float.parseFloat(value));
                    case Tag.TAG_DOUBLE -> data.putDouble(key, Double.parseDouble(value));
                    default -> data.putString(key, value);
                }
            } else {
                data.putString(key, value);
            }
        } catch (NumberFormatException ignored) {
            // Do not corrupt typed Create settings on an incomplete text edit.
        }
    }

    // Decode the authoritative value carried by a free wait-condition block.
    private static ScheduleWaitCondition detachedCondition(AdvancedGraphDocument.Node node,
                                                           HolderLookup.Provider registries) {
        if (!isDetachedCondition(node) || registries == null) return null;
        try {
            return ScheduleWaitCondition.fromTag(registries, node.data().getCompound(DETACHED_CONDITION_TAG));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // Find a regenerated condition visual by its real Create owner/group/index.
    private static String conditionNodeId(AdvancedGraphDocument graph, String parent, int group, int index) {
        for (AdvancedGraphDocument.Node candidate : graph.nodes()) {
            if (candidate != null && isConditionBlock(candidate.type()) && parent.equals(conditionParent(candidate))
                    && candidate.data().getInt(CONDITION_GROUP_TAG) == group
                    && candidate.data().getInt(CONDITION_INDEX_TAG) == index) {
                return candidate.id();
            }
        }
        return "";
    }

    // Create one registered Create instruction.
    private static ScheduleInstruction createInstruction(ResourceLocation id) {
        for (Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> entry : Schedule.INSTRUCTION_TYPES) {
            if (id.equals(entry.getFirst())) return entry.getSecond().get();
        }
        return null;
    }

    // Create one registered Create condition.
    private static ScheduleWaitCondition createCondition(ResourceLocation id) {
        for (Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> entry : Schedule.CONDITION_TYPES) {
            if (id.equals(entry.getFirst())) return entry.getSecond().get();
        }
        return null;
    }

    // Convert an id into a compact display title.
    private static String title(ResourceLocation id) {
        if (id == null) return "Schedule Block";
        StringBuilder title = new StringBuilder();
        for (String word : id.getPath().replace('-', '_').split("_")) {
            if (word.isBlank()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.isEmpty() ? id.toString() : title.toString();
    }
}
