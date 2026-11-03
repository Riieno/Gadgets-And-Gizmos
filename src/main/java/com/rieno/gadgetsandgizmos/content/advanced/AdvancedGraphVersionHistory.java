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
    private final ArrayDeque<Version> versions = new ArrayDeque<>();
    private final com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagHistory documents =
            new com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagHistory(MAX_VERSIONS);

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
        Version first = versions.peekFirst();
        if (first != null && sameGraph(AdvancedGraphDocument.fromTag(documents.get(0)), graph)) {
            return;
        }
        documents.push(graph.toTag());
        versions.addFirst(new Version(graph.revision(), Math.max(0L, savedAt)));
        while (versions.size() > MAX_VERSIONS) {
            versions.removeLast();
        }
    }

    // Read a retained version before committing a permission-checked rollback
    public AdvancedGraphDocument version(int idx){
        return idx < 0 || idx >= versions.size() ? null : AdvancedGraphDocument.fromTag(documents.get(idx));
    }

    // Roll back the advanced graph version history
    public AdvancedGraphDocument rollback(int idx, AdvancedGraphDocument current) {
        if (idx < 0 || idx >= versions.size()) {
            return null;
        }
        AdvancedGraphDocument restored = AdvancedGraphDocument.fromTag(documents.get(idx));
        Iterator<Version> iterator = versions.iterator();
        Version selected = null;
        for (int i = 0; iterator.hasNext(); i++) {
            Version snapshot = iterator.next();
            if (i == idx) {
                selected = snapshot;
                iterator.remove();
                documents.remove(idx);
                break;
            }
        }
        if (selected == null) {
            return null;
        }
        push(current, System.currentTimeMillis());
        return restored;
    }

    // Get the entries
    public List<Entry> entries() {
        List<Entry> res = new ArrayList<>(versions.size());
        int idx = 0;
        for (Version snapshot : versions) {
            res.add(new Entry(idx++, snapshot.revision(), snapshot.savedAt()));
        }
        return List.copyOf(res);
    }

    // Write the advanced graph version history data
    public ListTag toTag() {
        ListTag res = new ListTag();
        int idx = 0;
        for (Version snapshot : versions) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("SavedAt", snapshot.savedAt());
            tag.put("Graph", documents.get(idx++));
            res.add(tag);
        }
        return res;
    }

    // Read the advanced graph version history data
    public void fromTag(ListTag tags) {
        versions.clear();
        documents.clear();
        if (tags == null) {
            return;
        }
        List<CompoundTag> retained = new ArrayList<>();
        for (int i = 0; i < tags.size() && retained.size() < MAX_VERSIONS; i++) {
            CompoundTag tag = tags.getCompound(i);
            if (!tag.contains("Graph", Tag.TAG_COMPOUND)) {
                continue;
            }
            var graph = AdvancedGraphDocument.fromTag(tag.getCompound("Graph"));
            retained.add(graph.toTag());
            versions.addLast(new Version(graph.revision(), Math.max(0L, tag.getLong("SavedAt"))));
        }
        for(int idx = retained.size() - 1; idx >= 0; idx--) documents.push(retained.get(idx));
    }

    // Check if this uses the same graph
    private static boolean sameGraph(AdvancedGraphDocument first, AdvancedGraphDocument second) {
        CompoundTag firstTag = first.toTag();
        CompoundTag secondTag = second.toTag();
        firstTag.remove("Revision");
        secondTag.remove("Revision");
        return Objects.equals(firstTag, secondTag);
    }

    // Retain version metadata independently of the compact document history
    private record Version(int revision, long savedAt) {
    }
}
