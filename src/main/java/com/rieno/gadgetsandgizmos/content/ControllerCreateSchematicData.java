package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Move controller-owned references with a Create schematic without retaining its source database identity
final class ControllerCreateSchematicData{
    private static final String SOURCE_POS = "CreateControllerPos";
    private static final String SOURCE_SUBLEVEL = "CreateControllerSubLevel";

    private ControllerCreateSchematicData(){}

    // Keep the original controller frame through detached previews and safe NBT conversion
    static void captureSource(CompoundTag payload, BlockPos pos, @Nullable UUID subLevelId){
        if(payload.contains(SOURCE_POS, Tag.TAG_LONG)) return;
        payload.putLong(SOURCE_POS, pos.asLong());
        if(subLevelId != null) payload.putUUID(SOURCE_SUBLEVEL, subLevelId);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rotate relative offsets around the saved controller, then translate them to its placed position
    static void remap(CompoundTag payload, BlockPos dest, UnaryOperator<BlockPos> rotation,
                      UnaryOperator<Direction> faces, @Nullable UUID destId){
        if(!payload.contains(SOURCE_POS, Tag.TAG_LONG)) return;
        BlockPos src = BlockPos.of(payload.getLong(SOURCE_POS));
        UUID srcId = payload.hasUUID(SOURCE_SUBLEVEL) ? payload.getUUID(SOURCE_SUBLEVEL) : null;
        BlockPos origin = rotation.apply(BlockPos.ZERO);
        UnaryOperator<BlockPos> positions = pos -> dest.offset(rotation.apply(pos.subtract(src)).subtract(origin));
        Map<String, String> ids = new LinkedHashMap<>();
        remapTags(payload, null, srcId, destId, positions, faces, ids);
        if(!ids.isEmpty()){
            String expression = ids.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed())
                    .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
            Pattern pattern = Pattern.compile(expression);
            rewriteIds(payload, val -> pattern.matcher(val).replaceAll(match -> Matcher.quoteReplacement(ids.get(match.group()))), false);
        }
        payload.putLong(SOURCE_POS, dest.asLong());
        payload.remove(SOURCE_SUBLEVEL);
        if(destId != null) payload.putUUID(SOURCE_SUBLEVEL, destId);
    }

    // Keep external references intact while transforming local targets, areas and selected faces
    private static void remapTags(Tag raw, @Nullable UUID inheritedId, @Nullable UUID srcId, @Nullable UUID destId,
                                  UnaryOperator<BlockPos> positions, UnaryOperator<Direction> faces,
                                  Map<String, String> ids){
        if(raw instanceof ListTag list){
            for(Tag child : list) remapTags(child, inheritedId, srcId, destId, positions, faces, ids);
            return;
        }
        if(!(raw instanceof CompoundTag tag)) return;
        UUID refId = tag.hasUUID("SubLevelId") ? tag.getUUID("SubLevelId") : inheritedId;
        var prevTargets = tag.contains("Targets", Tag.TAG_LIST)
                ? ContraptionNetworkLinkerData.readTargets(tag) : List.<ContraptionNetworkLinkerData.LinkedTarget>of();
        if(tag.contains("DataTargets", Tag.TAG_LIST)){
            CompoundTag portIds = tag.getCompound("DataTargetPortIds");
            for(Tag child : tag.getList("DataTargets", Tag.TAG_COMPOUND)){
                String id = ((CompoundTag) child).getString("NodeId");
                if(!id.isBlank() && !portIds.contains(id)) portIds.putString(id, id);
            }
            if(!portIds.isEmpty()) tag.put("DataTargetPortIds", portIds);
        }
        if(Objects.equals(refId, srcId)){
            boolean located = false;
            for(String key : List.of("BlockPos", "Min", "Max", "AreaStart")){
                if(!tag.contains(key, Tag.TAG_LONG)) continue;
                BlockPos old = BlockPos.of(tag.getLong(key));
                BlockPos next = positions.apply(old);
                tag.putLong(key, next.asLong());
                ids.put("@" + old.asLong() + "#" + frame(srcId), "@" + next.asLong() + "#" + frame(destId));
                located = true;
            }
            if(tag.contains("Pos", Tag.TAG_LONG) && tag.contains("Face", Tag.TAG_STRING))
                tag.putLong("Pos", positions.apply(BlockPos.of(tag.getLong("Pos"))).asLong());
            if(tag.contains("Min", Tag.TAG_LONG) && tag.contains("Max", Tag.TAG_LONG)){
                BlockPos min = BlockPos.of(tag.getLong("Min"));
                BlockPos max = BlockPos.of(tag.getLong("Max"));
                tag.putLong("Min", new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ())).asLong());
                tag.putLong("Max", new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ())).asLong());
            }
            for(String key : List.of("Face", "Side", "LocalOutputSide")){
                Direction dir = Direction.byName(tag.getString(key));
                if(dir != null) tag.putString(key, faces.apply(dir).getSerializedName());
            }
            CompoundTag outputs = tag.getCompound("LocalOutputSides");
            for(String key : outputs.getAllKeys()){
                Direction dir = Direction.byName(outputs.getString(key));
                if(dir != null) outputs.putString(key, faces.apply(dir).getSerializedName());
            }
            if(located){
                tag.remove("SubLevelId");
                if(destId != null) tag.putUUID("SubLevelId", destId);
            }
        }
        for(String key : List.copyOf(tag.getAllKeys())) remapTags(tag.get(key), refId, srcId, destId, positions, faces, ids);
        if(!prevTargets.isEmpty()){
            for(var target : prevTargets){
                if(!Objects.equals(target.subLevelId(), srcId)) continue;
                var selectedFaces = target.faces().stream().map(face -> new ContraptionNetworkLinkerData.LinkedFace(
                        faces.apply(face.face()), face.label(), face.signalKey())).toList();
                var next = new ContraptionNetworkLinkerData.LinkedTarget(positions.apply(target.blockPos()), destId,
                        target.blockId(), target.label(), target.mode(), target.scope(), selectedFaces);
                ids.put(ContraptionNetworkLinkerData.nodeIdForTarget(target), ContraptionNetworkLinkerData.nodeIdForTarget(next));
            }
        }
    }

    // Rewrite both lookup keys and graph references while retaining generated port seeds
    private static void rewriteIds(Tag raw, UnaryOperator<String> rewrite, boolean seeds){
        if(raw instanceof CompoundTag tag){
            Map<String, Tag> next = new LinkedHashMap<>();
            for(String key : List.copyOf(tag.getAllKeys())){
                Tag child = tag.get(key);
                if(child instanceof StringTag text) child = StringTag.valueOf(seeds ? text.getAsString() : rewrite.apply(text.getAsString()));
                else rewriteIds(child, rewrite, "DataTargetPortIds".equals(key));
                next.put(rewrite.apply(key), child);
            }
            for(String key : new ArrayList<>(tag.getAllKeys())) tag.remove(key);
            next.forEach(tag::put);
        }else if(raw instanceof ListTag list){
            for(int idx = 0; idx < list.size(); idx++){
                Tag child = list.get(idx);
                if(child instanceof StringTag text) list.set(idx, StringTag.valueOf(rewrite.apply(text.getAsString())));
                else rewriteIds(child, rewrite, false);
            }
        }
    }

    private static String frame(@Nullable UUID id){ return id == null ? "world" : id.toString(); }
}
