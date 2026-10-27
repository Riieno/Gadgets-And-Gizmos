package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.TargetBindingCopies;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

// Keep copied targets in the controller without sharing the inserted linker's identity or bindings
final class ControllerStoredTargets{
    private CompoundTag root = new CompoundTag();
    private int revision;
    private List<ContraptionNetworkLinkerData.LinkedTarget> cachedTargets = List.of();
    private List<ContraptionNetworkLinkerData.LinkedArea> cachedAreas = List.of();

    CompoundTag snapshot(){
        CompoundTag tag = root.copy();
        tag.putInt("Revision", revision);
        return tag;
    }

    void read(CompoundTag tag){
        root = tag.copy();
        revision = Math.max(0, root.getInt("Revision"));
        root.remove("Revision");
        refreshCache();
    }

    List<ContraptionNetworkLinkerData.LinkedTarget> targets(){
        return cachedTargets;
    }

    List<ContraptionNetworkLinkerData.LinkedArea> areas(){
        return cachedAreas;
    }

    boolean pull(ItemStack linker){
        CompoundTag next = ContraptionNetworkLinkerData.writeClientEditRoot(ItemStack.EMPTY,
                merge(targets(), ContraptionNetworkLinkerData.readTargets(linker)),
                TargetBindingCopies.merge(areas(), ContraptionNetworkLinkerData.readAreas(linker),
                        ContraptionNetworkLinkerData.LinkedArea::id, (saved, incoming) -> saved),
                root.contains("EditMode") ? ContraptionNetworkLinkerData.LinkMode.byId(root.getString("EditMode"))
                        : ContraptionNetworkLinkerData.getEditMode(linker),
                ContraptionNetworkLinkerData.TargetMode.FACE);
        return replace(next);
    }

    void push(ItemStack linker){
        CompoundTag next = ContraptionNetworkLinkerData.writeClientEditRoot(ItemStack.EMPTY,
                merge(ContraptionNetworkLinkerData.readTargets(linker), targets()),
                TargetBindingCopies.merge(ContraptionNetworkLinkerData.readAreas(linker), areas(),
                        ContraptionNetworkLinkerData.LinkedArea::id, (saved, incoming) -> saved),
                ContraptionNetworkLinkerData.getEditMode(linker), ContraptionNetworkLinkerData.getTargetMode(linker));
        ContraptionNetworkLinkerData.writeRoot(linker,
                ContraptionNetworkLinkerData.mergeRootWithExistingBindings(linker, next));
    }

    boolean edit(CompoundTag edited, int expectedRevision){
        if(expectedRevision != revision) return false;
        CompoundTag next = ContraptionNetworkLinkerData.sanitizeAndMergeClientEdit(edited, root);
        if(next == null) return false;
        next.put("Targets", ContraptionNetworkLinkerData.writeRoot(merge(List.of(), ContraptionNetworkLinkerData.readTargets(next)),
                ContraptionNetworkLinkerData.LinkMode.OUTPUT).get("Targets"));
        replace(next);
        return true;
    }

    boolean replaceTargets(List<ContraptionNetworkLinkerData.LinkedTarget> targets){
        if(cachedTargets.equals(targets)) return false;
        CompoundTag next = root.copy();
        next.put("Targets", ContraptionNetworkLinkerData.writeRoot(targets,
                ContraptionNetworkLinkerData.LinkMode.OUTPUT).get("Targets"));
        return replace(next);
    }

    private boolean replace(CompoundTag next){
        if(root.equals(next)) return false;
        root = next.copy();
        revision++;
        refreshCache();
        return true;
    }

    private void refreshCache(){
        cachedTargets = List.copyOf(ContraptionNetworkLinkerData.readTargets(root));
        cachedAreas = List.copyOf(ContraptionNetworkLinkerData.readAreas(root));
    }

    // Retain destination aliases and add only missing faces when the same target is copied again
    static List<ContraptionNetworkLinkerData.LinkedTarget> merge(
            List<ContraptionNetworkLinkerData.LinkedTarget> destination,
            List<ContraptionNetworkLinkerData.LinkedTarget> source){
        return TargetBindingCopies.merge(destination, source, ControllerStoredTargets::identity,
                (saved, incoming) -> new ContraptionNetworkLinkerData.LinkedTarget(saved.blockPos(), saved.subLevelId(),
                        saved.blockId(), saved.label().isBlank() ? incoming.label() : saved.label(), saved.mode(), saved.scope(),
                        TargetBindingCopies.merge(saved.faces(), incoming.faces(),
                                face -> face.face().getSerializedName() + ":" + face.signalKey(), (a, b) -> a)));
    }

    private static Identity identity(ContraptionNetworkLinkerData.LinkedTarget target){
        return new Identity(target.blockPos(), target.subLevelId(), target.blockId(), target.mode(), target.scope());
    }

    private record Identity(BlockPos pos, UUID subLevelId, String blockId,
                            ContraptionNetworkLinkerData.LinkMode mode, ContraptionNetworkLinkerData.TargetScope scope){}
}
