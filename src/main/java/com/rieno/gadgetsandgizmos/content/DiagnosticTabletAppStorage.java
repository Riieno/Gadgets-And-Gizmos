package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;

// Keep each tablet app inside its own part of the cached database
public final class DiagnosticTabletAppStorage {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet app storage
    private DiagnosticTabletAppStorage() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the data
    public static CompoundTag data(MinecraftServer server, UUID tabletId, ResourceLocation appId) {
        return TabletStorageApi.storage().app(tabletId, appId);
    }

    // Get the selected binding
    public static DiagnosticTabletData.Binding selectedBinding(
            MinecraftServer server, UUID tabletId, ResourceLocation appId) {
        return DiagnosticTabletData.Binding.fromTag(
                data(server, tabletId, appId).getCompound("SelectedBinding"));
    }

    // Get the bindings
    public static List<DiagnosticTabletData.Binding> bindings(
            MinecraftServer server, UUID tabletId, ResourceLocation appId) {
        ListTag tags = data(server, tabletId, appId).getList("Bindings", Tag.TAG_COMPOUND);
        List<DiagnosticTabletData.Binding> res = new ArrayList<>();
        for (int idx = 0; idx < tags.size() && res.size() < 128; idx++) {
            DiagnosticTabletData.Binding binding = DiagnosticTabletData.Binding.fromTag(
                    tags.getCompound(idx));
            if (binding != null) res.add(binding);
        }
        return List.copyOf(res);
    }

    // Add the binding
    public static void addBinding(MinecraftServer server, UUID tabletId, ResourceLocation appId,
                                  DiagnosticTabletData.Binding binding, boolean select) {
        if (tabletId == null || appId == null || binding == null) return;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag tags = data.getList("Bindings", Tag.TAG_COMPOUND);
            ListTag next = new ListTag();
            for (int idx = 0; idx < tags.size(); idx++) {
                DiagnosticTabletData.Binding existing = DiagnosticTabletData.Binding.fromTag(
                        tags.getCompound(idx));
                if (existing != null && !sameTarget(existing, binding)) next.add(existing.toTag());
            }
            next.add(binding.toTag());
            while (next.size() > 128) next.remove(0);
            data.put("Bindings", next);
            if (select) data.put("SelectedBinding", binding.toTag());
            return data;
        });
    }

    // Select the binding
    public static boolean selectBinding(MinecraftServer server, UUID tabletId,
                                        ResourceLocation appId, String key) {
        for (DiagnosticTabletData.Binding binding : bindings(server, tabletId, appId)) {
            if (key(binding).equals(key)) {
                TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
                    data.put("SelectedBinding", binding.toTag());
                    return data;
                });
                return true;
            }
        }
        return false;
    }

    // Remove the binding
    public static void removeBinding(MinecraftServer server, UUID tabletId,
                                     ResourceLocation appId, String key) {
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag tags = data.getList("Bindings", Tag.TAG_COMPOUND);
            ListTag next = new ListTag();
            for (int idx = 0; idx < tags.size(); idx++) {
                DiagnosticTabletData.Binding binding = DiagnosticTabletData.Binding.fromTag(
                        tags.getCompound(idx));
                if (binding != null && !key(binding).equals(key)) next.add(binding.toTag());
            }
            data.put("Bindings", next);
            DiagnosticTabletData.Binding selected = DiagnosticTabletData.Binding.fromTag(
                    data.getCompound("SelectedBinding"));
            if (selected != null && key(selected).equals(key)) data.remove("SelectedBinding");
            return data;
        });
    }

    // Rename the tablet binding
    public static boolean renameBinding(MinecraftServer server, UUID tabletId,
                                        ResourceLocation appId, String key, String label) {
        if (key == null || key.isBlank() || label == null || label.isBlank()) return false;
        String nextLabel = label.strip();
        if (nextLabel.length() > 48) nextLabel = nextLabel.substring(0, 48);
        String finalLabel = nextLabel;
        boolean exists = bindings(server, tabletId, appId).stream().anyMatch(binding -> key(binding).equals(key));
        if (!exists) return false;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag tags = data.getList("Bindings", Tag.TAG_COMPOUND);
            ListTag next = new ListTag();
            for (int idx = 0; idx < tags.size(); idx++) {
                DiagnosticTabletData.Binding binding = DiagnosticTabletData.Binding.fromTag(tags.getCompound(idx));
                if (binding == null) continue;
                DiagnosticTabletData.Binding renamed = key(binding).equals(key)
                        ? new DiagnosticTabletData.Binding(binding.type(), binding.subLevelId(),
                        binding.pos(), finalLabel) : binding;
                next.add(renamed.toTag());
                if (key(binding).equals(key)
                        && key(DiagnosticTabletData.Binding.fromTag(data.getCompound("SelectedBinding"))).equals(key)) {
                    data.put("SelectedBinding", renamed.toTag());
                }
            }
            data.put("Bindings", next);
            return data;
        });
        return true;
    }

    // Get the selections
    public static List<DiagnosticTabletData.Binding> selections(
            MinecraftServer server, UUID tabletId, ResourceLocation appId) {
        ListTag tags = data(server, tabletId, appId).getList("Selections", Tag.TAG_COMPOUND);
        List<DiagnosticTabletData.Binding> res = new ArrayList<>();
        for (int idx = 0; idx < tags.size() && res.size() < 128; idx++) {
            DiagnosticTabletData.Binding binding = DiagnosticTabletData.Binding.fromTag(tags.getCompound(idx));
            if (binding != null) res.add(binding);
        }
        return List.copyOf(res);
    }

    // Add the selection
    public static void addSelection(MinecraftServer server, UUID tabletId, ResourceLocation appId,
                                    DiagnosticTabletData.Binding binding) {
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag tags = data.getList("Selections", Tag.TAG_COMPOUND);
            ListTag next = new ListTag();
            for (int idx = 0; idx < tags.size(); idx++) {
                DiagnosticTabletData.Binding existing = DiagnosticTabletData.Binding.fromTag(tags.getCompound(idx));
                if (existing != null && !sameTarget(existing, binding)) next.add(existing.toTag());
            }
            next.add(binding.toTag());
            while (next.size() > 128) next.remove(0);
            data.put("Selections", next);
            return data;
        });
    }

    // Clear the selections
    public static void clearSelections(MinecraftServer server, UUID tabletId, ResourceLocation appId) {
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            data.remove("Selections");
            return data;
        });
    }

    // Save the workspace target
    public static void saveWorkspaceTarget(MinecraftServer server, UUID tabletId,
                                           ResourceLocation appId, WorkspaceTarget target) {
        if (tabletId == null || appId == null || target == null) return;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag stored = data.getList("WorkspaceTargets", Tag.TAG_COMPOUND);
            ListTag next = new ListTag();
            for (int idx = 0; idx < stored.size(); idx++) {
                WorkspaceTarget existing = WorkspaceTarget.fromTag(stored.getCompound(idx));
                if (existing != null && !(existing.kind().equals(target.kind())
                        && existing.name().equalsIgnoreCase(target.name()))) {
                    next.add(existing.toTag());
                }
            }
            next.add(target.toTag());
            while (next.size() > 256) next.remove(0);
            data.put("WorkspaceTargets", next);
            return data;
        });
    }

    // Get the workspace targets
    public static List<WorkspaceTarget> workspaceTargets(MinecraftServer server, UUID tabletId,
                                                          ResourceLocation appId, String kind) {
        ListTag stored = data(server, tabletId, appId).getList("WorkspaceTargets", Tag.TAG_COMPOUND);
        List<WorkspaceTarget> res = new ArrayList<>();
        for (int idx = 0; idx < stored.size(); idx++) {
            WorkspaceTarget target = WorkspaceTarget.fromTag(stored.getCompound(idx));
            if (target != null && target.kind().equals(kind)) res.add(target);
        }
        res.sort(Comparator.comparing(WorkspaceTarget::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(res);
    }

    // Get the workspace target
    public static WorkspaceTarget workspaceTarget(MinecraftServer server, UUID tabletId,
                                                   ResourceLocation appId, String kind, String name) {
        if (name == null || name.isBlank()) return null;
        return workspaceTargets(server, tabletId, appId, kind).stream()
                .filter(target -> target.name().equalsIgnoreCase(name.strip()))
                .findFirst().orElse(null);
    }

    // Remove the workspace target
    public static boolean removeWorkspaceTarget(MinecraftServer server, UUID tabletId,
                                                ResourceLocation appId, String kind, String name) {
        if (tabletId == null || name == null || name.isBlank()) return false;
        boolean exists = workspaceTarget(server, tabletId, appId, kind, name) != null;
        if (!exists) return false;
        TabletStorageApi.storage().updateApp(tabletId, appId, data -> {
            ListTag stored = data.getList("WorkspaceTargets", Tag.TAG_COMPOUND);
            ListTag next = new ListTag();
            for (int idx = 0; idx < stored.size(); idx++) {
                WorkspaceTarget target = WorkspaceTarget.fromTag(stored.getCompound(idx));
                if (target != null && !(target.kind().equals(kind)
                        && target.name().equalsIgnoreCase(name.strip()))) next.add(target.toTag());
            }
            data.put("WorkspaceTargets", next);
            return data;
        });
        return true;
    }

    // Handle key
    public static String key(DiagnosticTabletData.Binding binding) {
        if (binding == null) return "";
        return (binding.subLevelId() == null ? "world" : binding.subLevelId().toString())
                + ":" + binding.pos().asLong();
    }

    // Check if this uses the same target
    private static boolean sameTarget(DiagnosticTabletData.Binding first,
                                      DiagnosticTabletData.Binding second) {
        return first.pos().equals(second.pos())
                && java.util.Objects.equals(first.subLevelId(), second.subLevelId());
    }

    // Store the workspace target
    public record WorkspaceTarget(String kind, String name, String dimension, BlockPos pos) {
        // Initialize the workspace target
        public WorkspaceTarget {
            kind = kind == null ? "" : kind;
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
        }

        // Write the workspace target data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Kind", kind);
            tag.putString("Name", name);
            tag.putString("Dimension", dimension);
            tag.putInt("X", pos.getX());
            tag.putInt("Y", pos.getY());
            tag.putInt("Z", pos.getZ());
            return tag;
        }

        // Read the workspace target data
        static WorkspaceTarget fromTag(CompoundTag tag) {
            if (tag == null || !tag.contains("Kind") || !tag.contains("Name")) return null;
            return new WorkspaceTarget(tag.getString("Kind"), tag.getString("Name"),
                    tag.getString("Dimension"), new BlockPos(tag.getInt("X"),
                    tag.getInt("Y"), tag.getInt("Z")));
        }
    }
}
