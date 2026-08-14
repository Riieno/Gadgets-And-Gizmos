package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// Remove obsolete controller state from loaded chunks after an older save is upgraded
@EventBusSubscriber(modid = CreateThrusters.MOD_ID)
public final class LegacyAdvancedControllerChunkCleanup {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ADVANCED_CONTROLLER_ID =
            CreateThrusters.MOD_ID + ":advanced_contraption_controller";
    private static final String ANALOGUE_CONTROLLER_ID =
            CreateThrusters.MOD_ID + ":analogue_contraption_controller";
    private static final Set<String> CONTROLLER_DATA_KEYS = Set.of(
            "Channels",
            "Axes",
            "Bindings",
            "InputBindings",
            "DirectTargets",
            "InputTargets",
            "LocalOutputSides",
            "KeyBindings",
            "BindingModes",
            "BindingPresets",
            "StoredTargets",
            "LinkerSlot",
            "EmbeddedSlab",
            "EmbeddedSlabMaterial",
            "EmbeddedBlockEntity",
            "ControllerAssemblyTransfer",
            "CustomName",
            "OmeterOutputSignal",
            "CustomKeyEntries",
            "AdvancedDraftGraph",
            "AdvancedActiveGraph",
            "AdvancedGraphVersions",
            "GogglesTrackerPairs",
            "ShipControlMapId");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the legacy advanced controller chunk cleanup
    private LegacyAdvancedControllerChunkCleanup() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the chunk load event
    @SubscribeEvent
    public static void onChunkLoad(ChunkDataEvent.Load event) {
        removeOrphans(event, true);
        if (event.getLevel() instanceof Level level) {
            int migrated = migrateLegacyStorage(event.getData(), level);
            if (migrated > 0) {
                event.getChunk().setUnsaved(true);
                LOGGER.info("Migrated {} legacy controller/linker record(s) to SQLite in chunk {}",
                        migrated, event.getChunk().getPos());
            }
        }
    }

    // Handle the chunk save event
    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        removeOrphans(event, false);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Remove the orphans
    private static void removeOrphans(ChunkDataEvent evt, boolean markUnsaved) {
        int removed = removeOrphanedAdvancedControllers(evt.getData(), pos ->
                evt.getChunk().getBlockState(pos).is(CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get()));
        if (removed == 0) {
            return;
        }
        if (markUnsaved) {
            evt.getChunk().setUnsaved(true);
        }
        LOGGER.warn("Removed {} orphaned legacy advanced controller record(s) from chunk {}",
                removed, evt.getChunk().getPos());
    }

    // Remove the orphaned advanced controllers
    static int removeOrphanedAdvancedControllers(CompoundTag chunkData, Predicate<BlockPos> controllerAt) {
        if (chunkData == null || controllerAt == null) {
            return 0;
        }
        ListTag blockEntities = chunkData.getList("block_entities", Tag.TAG_COMPOUND);
        int removed = 0;
        for (int idx = blockEntities.size() - 1; idx >= 0; idx--) {
            CompoundTag blockEntity = blockEntities.getCompound(idx);
            if (!ADVANCED_CONTROLLER_ID.equals(blockEntity.getString("id"))) {
                continue;
            }
            boolean hasPosition = blockEntity.contains("x", Tag.TAG_ANY_NUMERIC)
                    && blockEntity.contains("y", Tag.TAG_ANY_NUMERIC)
                    && blockEntity.contains("z", Tag.TAG_ANY_NUMERIC);
            BlockPos pos = new BlockPos(blockEntity.getInt("x"), blockEntity.getInt("y"), blockEntity.getInt("z"));
            if (!hasPosition || !controllerAt.test(pos)) {
                blockEntities.remove(idx);
                removed++;
            }
        }
        return removed;
    }

    // Migrate the legacy storage
    static int migrateLegacyStorage(CompoundTag chunkData, Level level) {
        if (chunkData == null || level == null) {
            return 0;
        }
        int migrated = migrateNestedLinkers(chunkData);
        migrated += migrateNestedCtrlItems(chunkData, level);
        ListTag blockEntities = chunkData.getList("block_entities", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < blockEntities.size(); idx++) {
            CompoundTag blockEntity = blockEntities.getCompound(idx);
            String id = blockEntity.getString("id");
            if (!ANALOGUE_CONTROLLER_ID.equals(id) && !ADVANCED_CONTROLLER_ID.equals(id)) {
                continue;
            }

            boolean hasControllerData = CONTROLLER_DATA_KEYS.stream().anyMatch(blockEntity::contains);
            if (!hasControllerData) {
                if (ControllerManifestStore.hasControllerMetadata(blockEntity)
                        && hasLegacyCtrlMetadata(blockEntity)) {
                    String manifestId = ControllerManifestStore.controllerManifestId(blockEntity);
                    clearControllerPayload(blockEntity);
                    blockEntity.putString(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID, manifestId);
                    migrated++;
                }
                continue;
            }

            CompoundTag controllerData = blockEntity.copy();
            CompoundTag draftGraph = controllerData.getCompound("AdvancedDraftGraph").copy();
            CompoundTag activeGraph = controllerData.getCompound("AdvancedActiveGraph").copy();
            String manifestId = ControllerManifestStore.controllerManifestId(blockEntity);
            int currentRevision = ControllerManifestStore.controllerManifestRevision(blockEntity);
            if (currentRevision <= 0 && !manifestId.isBlank()) {
                ControllerManifestStore.ManifestSnapshot existing =
                        ControllerManifestStore.loadController(manifestId, level);
                if (existing != null) {
                    currentRevision = existing.revision();
                }
            }
            BlockPos pos = new BlockPos(
                    blockEntity.getInt("x"),
                    blockEntity.getInt("y"),
                    blockEntity.getInt("z"));
            ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.saveController(
                    manifestId,
                    ADVANCED_CONTROLLER_ID.equals(id) ? "advanced_controller" : "base_controller",
                    level,
                    pos,
                    SimulatedHelper.getSubLevelId(level),
                    controllerData,
                    draftGraph,
                    activeGraph,
                    currentRevision);
            if (snapshot == null) {
                continue;
            }

            clearControllerPayload(blockEntity);
            ControllerManifestStore.writeControllerMetadata(blockEntity, snapshot);
            migrated++;
        }
        return migrated;
    }

    // Migrate the nested ctrl items
    private static int migrateNestedCtrlItems(Tag tag, Level level) {
        if (tag == null) {
            return 0;
        }
        int migrated = 0;
        if (tag instanceof CompoundTag compound) {
            for (String key : List.copyOf(compound.getAllKeys())) {
                migrated += migrateNestedCtrlItems(compound.get(key), level);
            }
            String id = compound.getString("id");
            boolean controllerItemData = !compound.contains("x", Tag.TAG_ANY_NUMERIC)
                    && (ANALOGUE_CONTROLLER_ID.equals(id) || ADVANCED_CONTROLLER_ID.equals(id));
            if (controllerItemData && migrateCtrlItemData(compound, level, id)) {
                migrated++;
            }
        } else if (tag instanceof ListTag list) {
            for (int idx = 0; idx < list.size(); idx++) {
                migrated += migrateNestedCtrlItems(list.get(idx), level);
            }
        }
        return migrated;
    }

    // Migrate the ctrl item data
    private static boolean migrateCtrlItemData(CompoundTag itemData, Level level, String id) {
        boolean hasControllerData = CONTROLLER_DATA_KEYS.stream().anyMatch(itemData::contains);
        if (!hasControllerData) {
            if (!ControllerManifestStore.hasControllerMetadata(itemData)
                    || !hasLegacyCtrlMetadata(itemData)) {
                return false;
            }
            String manifestId = ControllerManifestStore.controllerManifestId(itemData);
            clearControllerPayload(itemData);
            itemData.putString(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID, manifestId);
            return true;
        }

        CompoundTag controllerData = itemData.copy();
        controllerData.remove("id");
        String manifestId = ControllerManifestStore.controllerManifestId(itemData);
        int currentRevision = ControllerManifestStore.controllerManifestRevision(itemData);
        if (currentRevision <= 0 && !manifestId.isBlank()) {
            ControllerManifestStore.ManifestSnapshot existing =
                    ControllerManifestStore.loadController(manifestId, level);
            if (existing != null) {
                currentRevision = existing.revision();
            }
        }
        ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.saveController(
                manifestId,
                ADVANCED_CONTROLLER_ID.equals(id) ? "advanced_controller" : "base_controller",
                level,
                BlockPos.ZERO,
                SimulatedHelper.getSubLevelId(level),
                controllerData,
                controllerData.getCompound("AdvancedDraftGraph"),
                controllerData.getCompound("AdvancedActiveGraph"),
                currentRevision);
        if (snapshot == null) {
            return false;
        }

        clearControllerPayload(itemData);
        ControllerManifestStore.writeControllerMetadata(itemData, snapshot);
        return true;
    }

    // Migrate the nested linkers
    private static int migrateNestedLinkers(Tag tag) {
        if (tag == null) {
            return 0;
        }
        int migrated = 0;
        if (tag instanceof CompoundTag compound) {
            for (String key : List.copyOf(compound.getAllKeys())) {
                migrated += migrateNestedLinkers(compound.get(key));
            }
            if (ContraptionNetworkLinkerData.migrateLegacyNbtContainer(compound)) {
                migrated++;
            }
        } else if (tag instanceof ListTag list) {
            for (int idx = 0; idx < list.size(); idx++) {
                migrated += migrateNestedLinkers(list.get(idx));
            }
        }
        return migrated;
    }

    // Check if this has legacy ctrl metadata
    private static boolean hasLegacyCtrlMetadata(CompoundTag tag) {
        return tag.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION)
                || tag.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH)
                || tag.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
    }

    // Clear the controller payload
    private static void clearControllerPayload(CompoundTag tag) {
        for (String key : CONTROLLER_DATA_KEYS) {
            tag.remove(key);
        }
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
    }
}
