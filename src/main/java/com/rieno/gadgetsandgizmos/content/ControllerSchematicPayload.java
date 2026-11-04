package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.Create;
import com.rieno.gadgetsandgizmos.lib.inventory.ItemStackNbtSanitizer;
import com.rieno.gadgetsandgizmos.lib.nbt.ChunkedTagPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

// Carry Controller Schematic state between the client and server
final class ControllerSchematicPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final String BLOCK_ENTITY_TAG = "ControllerSchematicPayload";
    static final String CONTROLLER_DATA_TAG = "ControllerData";
    static final String LINKER_DATA_TAG = "LinkerData";
    static final String CONTROLLER_KIND_TAG = "ControllerKind";
    static final String PLACEMENT_PREPARED_TAG = "PlacementPrepared";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller schematic
    private ControllerSchematicPayload() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write a detached payload without replacing a valid envelope when encoding fails
    static boolean write(CompoundTag blockEntityTag, CompoundTag payload){
        if(blockEntityTag == null || payload == null || payload.isEmpty()) return false;
        try{
            CompoundTag copy = payload.copy();
            discardLegacyPlotPoints(copy);
            blockEntityTag.put(BLOCK_ENTITY_TAG, ChunkedTagPayload.encode(copy));
            return true;
        }catch(RuntimeException err){
            Create.LOGGER.warn("Failed to encode controller schematic payload", err);
            return false;
        }
    }

    // Read without consuming the template or clearing chunks before the database import succeeds
    static @Nullable CompoundTag read(CompoundTag blockEntityTag){
        if(blockEntityTag == null || !blockEntityTag.contains(BLOCK_ENTITY_TAG, Tag.TAG_COMPOUND)) return null;
        try{
            CompoundTag payload = ItemStackNbtSanitizer.withoutUnavailableItems(
                    ChunkedTagPayload.decode(blockEntityTag.getCompound(BLOCK_ENTITY_TAG)));
            discardLegacyPlotPoints(payload);
            return payload;
        }catch(RuntimeException err){
            Create.LOGGER.warn("Failed to decode controller schematic payload", err);
            return null;
        }
    }

    // Consume a decoded payload only for adapters which immediately write a replacement
    static @Nullable CompoundTag take(CompoundTag blockEntityTag){
        CompoundTag payload = read(blockEntityTag);
        if(payload != null) blockEntityTag.remove(BLOCK_ENTITY_TAG);
        return payload;
    }

    // Expand reference-only schematic data before a copied block can adopt its source identity
    static CompoundTag copyMetadata(CompoundTag saved, @Nullable Level level, HolderLookup.Provider provider){
        var snapshot = ControllerManifestStore.loadControllerFromMetadata(saved, level);
        if(snapshot == null) throw new IllegalArgumentException("The schematic controller graph is unavailable");
        CompoundTag payload = new CompoundTag();
        CompoundTag data = snapshot.controllerData();
        if(!snapshot.draftGraph().isEmpty()) data.put("AdvancedDraftGraph", snapshot.draftGraph());
        if(!snapshot.activeGraph().isEmpty()) data.put("AdvancedActiveGraph", snapshot.activeGraph());
        payload.putString(CONTROLLER_KIND_TAG, snapshot.kind());
        payload.put(CONTROLLER_DATA_TAG, data);
        var items = data.getCompound("LinkerSlot").getList("Items", Tag.TAG_COMPOUND);
        if(!items.isEmpty()){
            ItemStack linker = ItemStack.parseOptional(provider, items.getCompound(0));
            CompoundTag root = ContraptionNetworkLinkerData.copyAuthoritativeRootForSchematic(linker);
            if(!root.isEmpty()) payload.put(LINKER_DATA_TAG, root);
        }
        CompoundTag copy = saved.copy();
        for(String key : java.util.List.of(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID,
                ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION, ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH,
                ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION)) copy.remove(key);
        if(!write(copy, payload)) throw new IllegalArgumentException("The schematic controller graph could not be copied");
        return copy;
    }

    private static void discardLegacyPlotPoints(@Nullable CompoundTag payload){
        if(payload != null && payload.contains(CONTROLLER_DATA_TAG, Tag.TAG_COMPOUND))
            payload.getCompound(CONTROLLER_DATA_TAG).remove("PlotPoints");
    }
}
