package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagCopies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Set;

// Keep placed backing blocks and their ownership separate from recoverable controller programs
final class ControllerPhysicalState{
    static final String TAG_KEY = "ControllerPhysicalState";
    static final String EMBEDDED_BLOCK = "EmbeddedSlab";
    static final String EMBEDDED_MATERIAL = "EmbeddedSlabMaterial";
    static final String EMBEDDED_BLOCK_ENTITY = "EmbeddedBlockEntity";
    static final String PLACER = "ShipPermissionPlacer";
    static final String MODULE_PLACER = "ShipPermissionModulePlacer";
    private static final Set<String> KEYS = Set.of(EMBEDDED_BLOCK, EMBEDDED_MATERIAL,
            EMBEDDED_BLOCK_ENTITY, PLACER, MODULE_PLACER);

    private ControllerPhysicalState(){}

    static CompoundTag recoverProgram(CompoundTag program, CompoundTag physical){
        return CompoundTagCopies.copyReplacing(program, physical, KEYS);
    }

    // Older saved blocks keep their existing manifest-backed attachment data
    static CompoundTag readSavedBlock(CompoundTag program, CompoundTag savedBlock){
        return savedBlock.contains(TAG_KEY, Tag.TAG_COMPOUND)
                ? recoverProgram(program, savedBlock.getCompound(TAG_KEY)) : program;
    }
}
