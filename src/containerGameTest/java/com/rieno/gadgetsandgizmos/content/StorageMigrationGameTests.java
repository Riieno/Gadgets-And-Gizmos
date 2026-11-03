package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

// Keep passive loading from converting legacy storage while retaining current persistence
@GameTestHolder("createthrusters_storage_regressions")
@PrefixGameTestTemplate(false)
public final class StorageMigrationGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Leave nested legacy items and orphan records untouched on repeated chunk loads and saves
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void chunkEventsLeaveLegacyStorageUntouched(GameTestHelper helper){
        CompoundTag linker = new CompoundTag();
        CompoundTag root = new CompoundTag();
        root.putUUID("LinkerId", UUID.randomUUID());
        linker.put(ContraptionNetworkLinkerData.TAG_ROOT, root);
        CompoundTag controller = new CompoundTag();
        controller.putString("id", "createthrusters:advanced_contraption_controller");
        controller.put("Channels", new CompoundTag());
        ListTag items = new ListTag();
        items.add(linker);
        items.add(controller);
        CompoundTag barrel = new CompoundTag();
        barrel.putString("id", "minecraft:barrel");
        barrel.put("Items", items);
        CompoundTag orphan = controller.copy();
        orphan.putInt("x", 1_600_000);
        orphan.putInt("y", 64);
        orphan.putInt("z", 1_600_000);
        ListTag blockEntities = new ListTag();
        blockEntities.add(barrel);
        blockEntities.add(orphan);
        CompoundTag data = new CompoundTag();
        data.put("block_entities", blockEntities);
        CompoundTag prev = data.copy();
        LevelChunk chunk = new LevelChunk(helper.getLevel(), new ChunkPos(100_000, 100_000));
        chunk.setUnsaved(false);
        for(int idx = 0; idx < 2; idx++){
            NeoForge.EVENT_BUS.post(new ChunkDataEvent.Load(chunk, data, ChunkType.LEVELCHUNK));
            NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, helper.getLevel(), data));
        }
        helper.assertTrue(prev.equals(data), "Chunk events changed legacy controller or linker data");
        helper.assertTrue(!chunk.isUnsaved(), "Legacy cleanup marked the chunk for saving");
        helper.succeed();
    }

    // Leave legacy item payloads in place during inventory maintenance and snapshot sync
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void inventoryTicksLeaveLegacyStorageUntouched(GameTestHelper helper){
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "LegacyStorage"));
        CompoundTag data = new CompoundTag();
        data.putString("id", "createthrusters:analogue_contraption_controller");
        data.put("Channels", new CompoundTag());
        for(var item : List.of(CTItems.ANALOGUE_CONTRAPTION_CONTROLLER.get(),
                CTItems.PORTABLE_CONTRAPTION_CONTROLLER.get(), CTItems.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get())){
            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
            item.inventoryTick(stack, helper.getLevel(), player, 0, false);
            helper.assertTrue(data.equals(stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag()),
                    "An inventory tick converted a legacy controller");
        }
        ItemStack linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
        CompoundTag root = new CompoundTag();
        root.putUUID("LinkerId", UUID.randomUUID());
        CompoundTag custom = new CompoundTag();
        custom.put(ContraptionNetworkLinkerData.TAG_ROOT, root);
        linker.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        player.tickCount = 20;
        linker.getItem().inventoryTick(linker, helper.getLevel(), player, 0, false);
        helper.assertTrue(!ContraptionNetworkLinkerData.ensureStoredIdentity(linker),
                "Passive identity maintenance imported a legacy linker");
        helper.assertTrue(ContraptionNetworkLinkerData.clientSnapshotForSync(linker) == null,
                "Snapshot sync imported a legacy linker");
        helper.assertTrue(custom.equals(linker.get(DataComponents.CUSTOM_DATA).copyTag()),
                "Inventory maintenance changed a legacy linker payload");
        helper.succeed();
    }

    // Open legacy SQLite rows without converting them and retain current controller snapshots
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 40)
    public static void databaseLoadsLeaveLegacyStorageUntouched(GameTestHelper helper) throws Exception{
        var level = helper.getLevel();
        String modernId = UUID.randomUUID().toString();
        String modernLinkerId = UUID.randomUUID().toString();
        String legacyId = UUID.randomUUID().toString();
        CompoundTag data = new CompoundTag();
        data.putString("CustomName", "Current storage");
        var saved = ControllerManifestStore.saveController(modernId, "base_controller", level, BlockPos.ZERO,
                null, data, new CompoundTag(), new CompoundTag(), 0);
        helper.assertTrue(saved != null, "Current controller storage could not save");
        CompoundTag linkerData = new CompoundTag();
        UUID linkerId = UUID.randomUUID();
        linkerData.putUUID("LinkerId", linkerId);
        helper.assertTrue(ControllerManifestStore.saveLinker(modernLinkerId, linkerData, 0) != null,
                "Current linker storage could not save");
        var path = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/gadgets_graphs.db");
        String json = "{\"controllerManifestId\":\"" + legacyId
                + "\",\"kind\":\"base_controller\",\"controllerData\":\"{CustomName:Legacy}\"}";
        try(Connection conn = SqliteDriverLoader.connect("jdbc:sqlite:" + path.toAbsolutePath())){
            try(var stmt = conn.prepareStatement("INSERT INTO controllers(id, kind, dimension, block_x, block_y, block_z, manifest_json) VALUES (?, 'base_controller', '', 0, 0, 0, ?)")){
                stmt.setString(1, legacyId);
                stmt.setString(2, json);
                stmt.executeUpdate();
            }
            try(var stmt = conn.createStatement()){
                stmt.executeUpdate("UPDATE schema_meta SET value = '2' WHERE key = 'schema_version'");
            }
        }
        ControllerSqliteStore.closeAll();
        helper.assertTrue(ControllerManifestStore.loadController(legacyId, level) == null,
                "Loading an old SQLite row converted its JSON payload");
        var loaded = ControllerManifestStore.loadController(modernId, level);
        helper.assertTrue(loaded != null && data.equals(loaded.controllerData()),
                "Current controller storage did not survive reopening");
        var loadedLinker = ControllerManifestStore.loadLinker(modernLinkerId);
        helper.assertTrue(loadedLinker != null && linkerId.equals(loadedLinker.linkerData().getUUID("LinkerId")),
                "Current linker storage did not survive reopening");
        try(Connection conn = SqliteDriverLoader.connect("jdbc:sqlite:" + path.toAbsolutePath())){
            try(var stmt = conn.prepareStatement("SELECT manifest_json, controller_data_nbt FROM controllers WHERE id = ?")){
                stmt.setString(1, legacyId);
                try(var res = stmt.executeQuery()){
                    helper.assertTrue(res.next() && json.equals(res.getString(1)) && res.getBytes(2) == null,
                            "Opening SQLite rewrote a legacy manifest");
                }
            }
            try(var stmt = conn.createStatement(); var res = stmt.executeQuery("SELECT value FROM schema_meta WHERE key = 'schema_version'")){
                helper.assertTrue(res.next() && "2".equals(res.getString(1)), "Opening SQLite ran legacy reindexing");
            }
        }
        String fileId = UUID.randomUUID().toString();
        var dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/advanced_controller_graphs");
        var controllerFile = dir.resolve("controllers/" + fileId + ".json");
        var linkerFile = dir.resolve("linkers/" + fileId + ".json");
        var sharedDir = dir.resolve(fileId).resolve("shared_graphs");
        var sharedFile = sharedDir.resolve(fileId + ".json");
        Files.createDirectories(controllerFile.getParent());
        Files.createDirectories(linkerFile.getParent());
        Files.createDirectories(sharedDir);
        Files.writeString(controllerFile, json.replace(legacyId, fileId));
        String sharedJson = "{\"linkerManifestId\":\"" + fileId + "\",\"linkerData\":\"{}\"}";
        Files.writeString(linkerFile, sharedJson);
        Files.writeString(sharedFile, sharedJson);
        try{
            helper.assertTrue(ControllerManifestStore.loadController(fileId, level) == null
                    && ControllerManifestStore.loadLinker(fileId) == null, "A missing SQLite record imported a legacy file");
            ControllerManifestStore.loadLinkerManifestData();
            helper.assertTrue(ControllerManifestStore.loadLinker(fileId) == null, "Listing linkers imported legacy files");
            helper.assertTrue(ControllerManifestStore.loadSharedGraph(sharedDir, fileId) != null
                    && sharedJson.equals(Files.readString(sharedFile)), "Reading a shared graph rewrote its storage format");
        }finally{
            Files.deleteIfExists(controllerFile);
            Files.deleteIfExists(linkerFile);
            Files.deleteIfExists(sharedFile);
        }
        helper.succeed();
    }

    // Refresh a placed tablet without importing its old app state into the database
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void tabletRefreshLeavesLegacyStorageUntouched(GameTestHelper helper){
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, CTBlocks.DIAGNOSTIC_TABLET.get());
        var tablet = (DiagnosticTabletBlockEntity)helper.getBlockEntity(pos);
        UUID id = UUID.randomUUID();
        var state = DiagnosticTabletData.State.DEFAULT.withTabletId(id).withBinding(
                new DiagnosticTabletData.Binding("controller", null, helper.absolutePos(pos), "Legacy binding"));
        tablet.setState(state);
        DiagnosticTabletRedstoneLinkRuntime.refreshPlaced(tablet);
        var data = DiagnosticTabletDatabase.forServer(helper.getLevel().getServer()).app(id, state.app());
        helper.assertTrue(!data.contains("MigratedLegacy") && !data.contains("SelectedBinding"),
                "Refreshing a tablet imported legacy app data");
        helper.succeed();
    }

    // Persist a new schematic linker copy through the explicit placement path
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void schematicPlacementRetainsCurrentStorage(GameTestHelper helper){
        ItemStack linker = new ItemStack(CTItems.CONTRAPTION_NETWORK_LINKER.get());
        CompoundTag root = new CompoundTag();
        root.putUUID("LinkerId", UUID.randomUUID());
        helper.assertTrue(ContraptionNetworkLinkerData.installSchematicCopy(linker, root)
                && ContraptionNetworkLinkerData.persistSchematicCopy(linker), "Schematic placement could not persist a linker copy");
        CompoundTag data = linker.get(DataComponents.CUSTOM_DATA).copyTag();
        helper.assertTrue(!data.contains(ContraptionNetworkLinkerData.TAG_ROOT)
                && ControllerManifestStore.loadLinkerFromMetadata(data) != null, "Schematic placement lost its current SQLite record");
        helper.succeed();
    }
}
