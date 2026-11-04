package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

// Carry Schematic Block Entity Config state between the client and server
public final class SchematicBlockEntityConfigPayload {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final String PAYLOAD_TAG = "createthrusters:schematic_block_data";
    private static final String SHIP_DOCK_ID_TAG = "DockId";
    private static final List<String> POSITION_KEYS =
            List.of("id", "x", "y", "z", "keepPacked");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the schematic block entity config
    private SchematicBlockEntityConfigPayload() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this supports the value
    public static boolean supports(ResourceLocation blockEntityTypeId) {
        if (blockEntityTypeId == null
                || !CreateThrusters.MOD_ID.equals(blockEntityTypeId.getNamespace())) {
            return false;
        }
        return !"analogue_contraption_controller".equals(blockEntityTypeId.getPath())
                && !"advanced_contraption_controller".equals(blockEntityTypeId.getPath());
    }

    // Capture the sable save
    public static void captureForSableSave(CompoundTag tag) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null
                && ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
            capture(tag);
        }
    }

    // Restore the sable placement
    public static boolean restoreForSablePlacement(CompoundTag tag) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null
                && ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
            restore(tag);
            return true;
        }
        return false;
    }

    // Clear the placed ship dock identity
    public static void clearShipDockPlacementIdentity(CompoundTag tag) {
        if (tag != null) {
            tag.remove(SHIP_DOCK_ID_TAG);
            if (tag.contains(PAYLOAD_TAG, Tag.TAG_COMPOUND)) {
                tag.getCompound(PAYLOAD_TAG).remove(SHIP_DOCK_ID_TAG);
            }
        }
    }

    // Capture the schematic block entity config
    static void capture(CompoundTag tag) {
        CompoundTag snapshot = tag.copy();
        snapshot.remove(PAYLOAD_TAG);
        for (String key : POSITION_KEYS) {
            snapshot.remove(key);
        }
        tag.put(PAYLOAD_TAG, snapshot);
    }

    // Restore the schematic block entity config
    static boolean restore(CompoundTag tag) {
        if (!tag.contains(PAYLOAD_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag snapshot = tag.getCompound(PAYLOAD_TAG).copy();
        tag.remove(PAYLOAD_TAG);
        snapshot.remove(PAYLOAD_TAG);
        for (String key : POSITION_KEYS) {
            snapshot.remove(key);
        }
        for (String key : snapshot.getAllKeys()) {
            Tag val = snapshot.get(key);
            if (val != null) {
                tag.put(key, val.copy());
            }
        }
        return true;
    }
}
