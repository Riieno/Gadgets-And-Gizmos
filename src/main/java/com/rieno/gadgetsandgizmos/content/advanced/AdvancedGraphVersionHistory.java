package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

// Store bounded graph revisions and restore earlier revisions on request
public final class AdvancedGraphVersionHistory {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAX_VERSIONS = 15;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Versions
    private final ArrayDeque<Snapshot> versions = new ArrayDeque<>();

    // Store the entry
    public record Entry(int index, int revision, long savedAt) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Push the advanced graph version history if changed
    public void pushIfChanged(AdvancedGraphDocument current, AdvancedGraphDocument replacement) {
        if (current == null || replacement == null || sameGraph(current, replacement)) {
            return;
        }
        push(current, System.currentTimeMillis());
    }

    // Push the advanced graph version history
    public void push(AdvancedGraphDocument graph, long savedAt) {
        if (graph == null) {
            return;
        }
        Snapshot first = versions.peekFirst();
        if (first != null && sameGraph(first.graph(), graph)) {
            return;
        }
        versions.addFirst(new Snapshot(graph.copy(), Math.max(0L, savedAt)));
        while (versions.size() > MAX_VERSIONS) {
            versions.removeLast();
        }
    }

    // Roll back the advanced graph version history
    public AdvancedGraphDocument rollback(int idx, AdvancedGraphDocument current) {
        if (idx < 0 || idx >= versions.size()) {
            return null;
        }
        Iterator<Snapshot> iterator = versions.iterator();
        Snapshot selected = null;
        for (int i = 0; iterator.hasNext(); i++) {
            Snapshot snapshot = iterator.next();
            if (i == idx) {
                selected = snapshot;
                iterator.remove();
                break;
            }
        }
        if (selected == null) {
            return null;
        }
        push(current, System.currentTimeMillis());
        return selected.graph().copy();
    }

    // Get the entries
    public List<Entry> entries() {
        List<Entry> res = new ArrayList<>(versions.size());
        int idx = 0;
        for (Snapshot snapshot : versions) {
            res.add(new Entry(idx++, snapshot.graph().revision(), snapshot.savedAt()));
        }
        return List.copyOf(res);
    }

    // Write the advanced graph version history data
    public ListTag toTag() {
        ListTag res = new ListTag();
        for (Snapshot snapshot : versions) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("SavedAt", snapshot.savedAt());
            tag.put("Graph", snapshot.graph().toTag());
            res.add(tag);
        }
        return res;
    }

    // Read the advanced graph version history data
    public void fromTag(ListTag tags) {
        versions.clear();
        if (tags == null) {
            return;
        }
        for (int i = 0; i < tags.size() && versions.size() < MAX_VERSIONS; i++) {
            CompoundTag tag = tags.getCompound(i);
            if (!tag.contains("Graph", Tag.TAG_COMPOUND)) {
                continue;
            }
            versions.addLast(new Snapshot(
                    AdvancedGraphDocument.fromTag(tag.getCompound("Graph")),
                    Math.max(0L, tag.getLong("SavedAt"))));
        }
    }

    // Check if this uses the same graph
    private static boolean sameGraph(AdvancedGraphDocument first, AdvancedGraphDocument second) {
        CompoundTag firstTag = first.toTag();
        CompoundTag secondTag = second.toTag();
        firstTag.remove("Revision");
        secondTag.remove("Revision");
        return Objects.equals(firstTag, secondTag);
    }

    // Store the snapshot
    private record Snapshot(AdvancedGraphDocument graph, long savedAt) {
    }
}
