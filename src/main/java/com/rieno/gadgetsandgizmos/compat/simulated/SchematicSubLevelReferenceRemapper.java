package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Rewrite stored sub-level references when a schematic creates new ship IDs
public final class SchematicSubLevelReferenceRemapper {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the schematic sub level reference remapper
    private SchematicSubLevelReferenceRemapper() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the remapped copy
    public static CompoundTag remappedCopy(CompoundTag src) {
        CompoundTag copy = src.copy();
        remapInPlace(copy);
        return copy;
    }

    // Remap sublevel references in place
    public static void remapInPlace(CompoundTag root) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (root == null || ctx == null) return;
        List<IdentifierReplacement> replacements = new ArrayList<>();
        remapCompound(root, ctx, replacements);
        rewriteIdStrings(root, replacements);
    }

    // Remap the compound
    private static void remapCompound(CompoundTag compound,
                                      SubLevelSchematicSerializationContext ctx,
                                      List<IdentifierReplacement> replacements) {
        remapPair(compound, "SubLevelId", "BlockPos", ctx, replacements);
        for (String key : List.copyOf(compound.getAllKeys())) {
            Tag child = compound.get(key);
            if (child instanceof CompoundTag childCompound) {
                remapCompound(childCompound, ctx, replacements);
            } else if (child instanceof ListTag list) {
                for (Tag entry : list) {
                    if (entry instanceof CompoundTag entryCompound) {
                        remapCompound(entryCompound, ctx, replacements);
                    }
                }
            }
        }
    }

    // Remap the pair
    private static void remapPair(CompoundTag compound, String idKey, String posKey,
                                  SubLevelSchematicSerializationContext ctx,
                                  List<IdentifierReplacement> replacements) {
        if (!compound.hasUUID(idKey)) return;
        UUID oldId = compound.getUUID(idKey);
        BlockPos oldPos = compound.contains(posKey, Tag.TAG_LONG)
                ? BlockPos.of(compound.getLong(posKey))
                : null;
        RemappedBlockReference remapped = remapBlockReference(ctx, oldId, oldPos);
        if (remapped == null) {
            compound.remove(idKey);
            compound.remove(posKey);
            return;
        }
        compound.putUUID(idKey, remapped.subLevelId());
        if (remapped.blockPos() != null) {
            compound.putLong(posKey, remapped.blockPos().asLong());
        }
        if (remapped.changed() && oldPos != null && remapped.blockPos() != null) {
            replacements.add(new IdentifierReplacement(
                    oldId, oldPos, remapped.subLevelId(), remapped.blockPos()));
        }
    }

    // Remap the block reference
    public static RemappedBlockReference remapBlockReference(
            SubLevelSchematicSerializationContext ctx,
            UUID subLevelId,
            BlockPos blockPos) {
        if (ctx == null || subLevelId == null) return null;
        SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(subLevelId);
        if (mapping != null) {
            BlockPos transformed = blockPos == null ? null : mapping.transform().apply(blockPos);
            return new RemappedBlockReference(mapping.newUUID(), transformed, true);
        }
        if (isMappedDestination(ctx, subLevelId)) {
            return new RemappedBlockReference(subLevelId, blockPos, false);
        }
        return null;
    }

    // Remap the world position
    public static RemappedWorldPosition remapWorldPosition(
            SubLevelSchematicSerializationContext ctx,
            UUID subLevelId,
            Vec3 pos) {
        if (ctx == null || subLevelId == null || pos == null) return null;
        SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(subLevelId);
        if (mapping == null) {
            return isMappedDestination(ctx, subLevelId)
                    ? new RemappedWorldPosition(subLevelId, pos, false)
                    : null;
        }
        BlockPos floor = BlockPos.containing(pos);
        BlockPos transformedFloor = mapping.transform().apply(floor);
        Vec3 transformed = new Vec3(
                transformedFloor.getX() + pos.x - floor.getX(),
                transformedFloor.getY() + pos.y - floor.getY(),
                transformedFloor.getZ() + pos.z - floor.getZ());
        return new RemappedWorldPosition(mapping.newUUID(), transformed, true);
    }

    // Check if the destination is mapped
    public static boolean isMappedDestination(
            SubLevelSchematicSerializationContext ctx,
            UUID subLevelId) {
        if (ctx == null || subLevelId == null) return false;
        for (SubLevelSchematicSerializationContext.SchematicMapping mapping : ctx.getMappings().values()) {
            if (mapping != null && subLevelId.equals(mapping.newUUID())) return true;
        }
        return false;
    }

    // Rewrite the id strings
    private static void rewriteIdStrings(Tag tag, List<IdentifierReplacement> replacements) {
        if (tag == null || replacements.isEmpty()) return;
        if (tag instanceof CompoundTag compound) {
            for (String key : List.copyOf(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (child instanceof StringTag) {
                    compound.putString(key, rewriteIdString(compound.getString(key), replacements));
                } else {
                    rewriteIdStrings(child, replacements);
                }
            }
            return;
        }
        if (tag instanceof ListTag list) {
            for (int idx = 0; idx < list.size(); idx++) {
                Tag child = list.get(idx);
                if (child instanceof StringTag stringTag) {
                    list.set(idx, StringTag.valueOf(
                            rewriteIdString(stringTag.getAsString(), replacements)));
                } else {
                    rewriteIdStrings(child, replacements);
                }
            }
        }
    }

    // Rewrite the id string
    private static String rewriteIdString(String val, List<IdentifierReplacement> replacements) {
        String rewritten = val == null ? "" : val;
        for (IdentifierReplacement replacement : replacements) {
            rewritten = rewritten
                    .replace(replacement.oldLocator(), replacement.newLocator())
                    .replace(replacement.oldGroupId(), replacement.newGroupId());
        }
        return rewritten;
    }

    // Store the remapped block reference
    public record RemappedBlockReference(UUID subLevelId, BlockPos blockPos, boolean changed) {
    }

    // Store the remapped world position
    public record RemappedWorldPosition(UUID subLevelId, Vec3 position, boolean changed) {
    }

    // Store the identifier replacement
    private record IdentifierReplacement(UUID oldId, BlockPos oldPos, UUID newId, BlockPos newPos) {
        // Get the old locator
        private String oldLocator() {
            return "@" + oldPos.asLong() + "#" + oldId;
        }

        // Create the locator
        private String newLocator() {
            return "@" + newPos.asLong() + "#" + newId;
        }

        // Get the old group id
        private String oldGroupId() {
            return "sublevel:" + oldId;
        }

        // Create the group id
        private String newGroupId() {
            return "sublevel:" + newId;
        }
    }
}
