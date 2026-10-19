package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagCopies;
import com.rieno.gadgetsandgizmos.lib.nbt.BlockStateSnapshots;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
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

    // Simulated Copycats' old normal slab uses the same axis, slab type and material slots
    static @Nullable BlockState backingState(HolderLookup.Provider provider, CompoundTag data){
        return BlockStateSnapshots.read(provider.lookupOrThrow(Registries.BLOCK), data, Map.of(
                ResourceLocation.fromNamespaceAndPath("sim_copycats", "normal_copycat_slab"),
                ResourceLocation.fromNamespaceAndPath("copycats", "copycat_slab")));
    }

    static CompoundTag recoverProgram(CompoundTag program, CompoundTag physical){
        return CompoundTagCopies.copyReplacing(program, physical, KEYS);
    }

    // Schematic programs carry their source attachment even when an intermediate shell saved an empty snapshot
    static CompoundTag readSchematicBlock(CompoundTag program, CompoundTag savedBlock){
        if(program.contains(EMBEDDED_BLOCK, Tag.TAG_COMPOUND)) return program;
        return readSavedBlock(program, savedBlock);
    }

    // Load only physical configuration for detached item-cost previews
    static CompoundTag materialData(CompoundTag savedBlock){
        CompoundTag physical = savedBlock.contains(TAG_KEY, Tag.TAG_COMPOUND)
                ? savedBlock.getCompound(TAG_KEY) : savedBlock;
        return CompoundTagCopies.copyReplacing(null, physical, KEYS);
    }

    // Older saved blocks keep their existing manifest-backed attachment data
    static CompoundTag readSavedBlock(CompoundTag program, CompoundTag savedBlock){
        return savedBlock.contains(TAG_KEY, Tag.TAG_COMPOUND)
                ? recoverProgram(program, savedBlock.getCompound(TAG_KEY)) : program;
    }
}
