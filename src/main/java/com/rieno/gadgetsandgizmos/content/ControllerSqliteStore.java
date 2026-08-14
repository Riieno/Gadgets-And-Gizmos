package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphImageAssets;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

// Store large controller state in SQLite and keep its frequently used lookups cached
final class ControllerSqliteStore {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DATABASE_NAME = "gadgets_graphs.db";
    private static final Map<Path, Connection> CONNECTIONS = new LinkedHashMap<>();
    private static final LinkerTargetCache LINKER_TARGET_CACHE = new LinkerTargetCache();
    private static final Map<Connection, Map<String, ControllerWriteState>> CONTROLLER_WRITE_CACHE =
            new IdentityHashMap<>();
    private static final Set<Path> MIGRATED_LINKER_PATHS = new LinkedHashSet<>();
    private static final String TAG_LINKER_ID = "LinkerId";
    private static final String TAG_EDIT_MODE = "EditMode";
    private static final String TAG_TARGET_MODE = "TargetMode";
    private static final String TAG_CHANNEL_BINDS = "ChannelBinds";
    private static final String TAG_CUSTOM_ENTRY_BINDS = "CustomEntryBinds";
    private static final String TAG_STORED_CONTROLLER_MANIFESTS = "StoredControllerManifests";
    private static final String TAG_STORED_GRAPHS = "StoredGraphs";
    private static final String TAG_SELECTED_GRAPH_ID = "SelectedGraphId";
    private static final String TAG_GRAPH_ID = "GraphId";
    private static final String TAG_GRAPH_NAME = "Name";
    private static final String TAG_GRAPH = "Graph";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller sqlite store
    private ControllerSqliteStore() {
    }

    // Store the linker header
    record LinkerHeader(@Nullable UUID linkerId, ContraptionNetworkLinkerData.LinkMode editMode,
                        ContraptionNetworkLinkerData.TargetMode targetMode) {
    }

    // Store the linker targets
    record LinkerTargets(boolean found, @Nullable UUID linkerId,
                         ContraptionNetworkLinkerData.LinkMode editMode,
                         ContraptionNetworkLinkerData.TargetMode targetMode,
                         List<ContraptionNetworkLinkerData.LinkedTarget> targets) {
        // Initialize the linker targets
        LinkerTargets {
            editMode = editMode == null ? ContraptionNetworkLinkerData.LinkMode.OUTPUT : editMode;
            targetMode = targetMode == null ? ContraptionNetworkLinkerData.TargetMode.AUTO : targetMode;
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    // Store the broken block cleanup
    record BrokenBlockCleanup(int updatedLinkers, int updatedControllers) {
    }

    // Expose the linker target loader
    @FunctionalInterface
    interface LinkerTargetLoader {
        // Load the linker target loader
        LinkerTargets load() throws SQLException;
    }

    // Handle the linker target cache
    static final class LinkerTargetCache {
        // Tracked entries
        private final Map<Connection, Map<String, LinkerTargets>> entries = new IdentityHashMap<>();

        // Get the linker target cache value
        @Nullable LinkerTargets get(Connection connection, String manifestId) {
            Map<String, LinkerTargets> connectionEntries = entries.get(connection);
            return connectionEntries == null ? null : connectionEntries.get(manifestId);
        }

        // Put the linker target cache
        void put(Connection connection, String manifestId, LinkerTargets targets) {
            entries.computeIfAbsent(connection, ignored -> new LinkedHashMap<>()).put(manifestId, targets);
        }

        // Load or cache linker targets
        LinkerTargets getOrLoad(Connection connection, String manifestId, LinkerTargetLoader loader)
                throws SQLException {
            LinkerTargets cached = get(connection, manifestId);
            if (cached != null) {
                return cached;
            }
            LinkerTargets loaded = loader.load();
            put(connection, manifestId, loaded);
            return loaded;
        }

        // Invalidate the linker target cache
        void invalidate(Connection connection, String manifestId) {
            Map<String, LinkerTargets> connectionEntries = entries.get(connection);
            if (connectionEntries == null) {
                return;
            }
            connectionEntries.remove(manifestId);
            if (connectionEntries.isEmpty()) {
                entries.remove(connection);
            }
        }

        // Clear the linker target cache
        void clear(Connection connection) {
            entries.remove(connection);
        }

        // Clear the linker target cache
        void clearAll() {
            entries.clear();
        }

        // Get the connection count
        int connectionCount() {
            return entries.size();
        }

        // Get the entry count
        int entryCount(Connection connection) {
            Map<String, LinkerTargets> connectionEntries = entries.get(connection);
            return connectionEntries == null ? 0 : connectionEntries.size();
        }
    }

    // Store the indexed owner
    private record IndexedOwner(String ownerType, String ownerId, String nodeId) {
    }

    // Store the controller owner
    private record ControllerOwner(String id, String kind, int revision, BlockPos blockPos,
                                   @Nullable UUID subLevelId) {
    }

    // Store the controller index data
    private record ControllerIndexData(String id, String dimension, CompoundTag controllerData) {
    }

    // Store controller write state
    private record ControllerWriteState(
            String kind,
            String dimension,
            BlockPos position,
            String subLevelId,
            String insertedLinkerId,
            CompoundTag controllerData,
            CompoundTag draftGraph,
            CompoundTag activeGraph,
            CompoundTag graphHistory,
            int revision,
            String hash
    ) {
        // Check if this matches the value
        private boolean matches(
                String requestedKind,
                String requestedDimension,
                BlockPos requestedPosition,
                String requestedSubLevelId,
                String requestedInsertedLinkerId,
                CompoundTag requestedControllerData,
                CompoundTag requestedDraftGraph,
                CompoundTag requestedActiveGraph,
                CompoundTag requestedGraphHistory
        ) {
            return kind.equals(requestedKind)
                    && dimension.equals(requestedDimension)
                    && position.equals(requestedPosition)
                    && subLevelId.equals(requestedSubLevelId)
                    && insertedLinkerId.equals(requestedInsertedLinkerId)
                    && controllerData.equals(requestedControllerData)
                    && draftGraph.equals(requestedDraftGraph)
                    && activeGraph.equals(requestedActiveGraph)
                    && graphHistory.equals(requestedGraphHistory);
        }

        // Get the snapshot
        private ControllerManifestStore.ManifestSnapshot snapshot(String id) {
            return new ControllerManifestStore.ManifestSnapshot(
                    id, revision, hash, ControllerManifestStore.STORAGE_VERSION, kind,
                    controllerData.copy(), draftGraph.copy(), activeGraph.copy(), new CompoundTag());
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the cached controller write
    private static @Nullable ControllerWriteState cachedControllerWrite(
            Connection connection,
            String id
    ) {
        Map<String, ControllerWriteState> entries = CONTROLLER_WRITE_CACHE.get(connection);
        return entries == null ? null : entries.get(id);
    }

    // Cache the controller write
    private static void cacheControllerWrite(
            Connection connection,
            String id,
            ControllerWriteState state
    ) {
        CONTROLLER_WRITE_CACHE.computeIfAbsent(
                connection, ignored -> new LinkedHashMap<>()).put(id, state);
    }

    // Invalidate the ctrl write
    private static void invalidateCtrlWrite(Connection connection, String id) {
        Map<String, ControllerWriteState> entries = CONTROLLER_WRITE_CACHE.get(connection);
        if (entries == null) {
            return;
        }
        entries.remove(id);
        if (entries.isEmpty()) {
            CONTROLLER_WRITE_CACHE.remove(connection);
        }
    }

    // Load the controller
    static synchronized @Nullable ControllerManifestStore.ManifestSnapshot loadController(String manifestId,
                                                                                          @Nullable Level level) {
        if (manifestId == null || manifestId.isBlank()) {
            return null;
        }
        Connection connection = connection(level);
        if (connection == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT kind, dimension, block_x, block_y, block_z, sublevel_id,
                       revision, content_hash, controller_data_nbt, manifest_json
                FROM controllers
                WHERE id = ?
                """)) {
            statement.setString(1, manifestId);
            try (ResultSet res = statement.executeQuery()) {
                if (!res.next()) {
                    return null;
                }
                byte[] controllerBytes = res.getBytes("controller_data_nbt");
                if (controllerBytes == null || controllerBytes.length == 0) {
                    String legacyJson = res.getString("manifest_json");
                    if (legacyJson != null && !legacyJson.isBlank()) {
                        ControllerManifestStore.ManifestSnapshot legacySnapshot = snapshotFromJson(legacyJson, false);
                        if (legacySnapshot != null) {
                            importController(legacySnapshot, level);
                            return legacySnapshot;
                        }
                    }
                    return null;
                }
                CompoundTag controllerData = compoundFromBytes(controllerBytes);
                CompoundTag draftGraph = loadGraph(connection, "controller", manifestId, "draft");
                CompoundTag activeGraph = loadGraph(connection, "controller", manifestId, "active");
                CompoundTag graphHistory = loadGraph(connection, "controller", manifestId, "history");
                if (graphHistory.contains("Versions", Tag.TAG_LIST)) {
                    controllerData.put("AdvancedGraphVersions",
                            graphHistory.getList("Versions", Tag.TAG_COMPOUND).copy());
                }
                ControllerManifestStore.ManifestSnapshot snapshot = new ControllerManifestStore.ManifestSnapshot(
                        manifestId,
                        res.getInt("revision"),
                        res.getString("content_hash"),
                        ControllerManifestStore.STORAGE_VERSION,
                        res.getString("kind"),
                        controllerData,
                        draftGraph,
                        activeGraph,
                        new CompoundTag());
                cacheControllerWrite(connection, manifestId, new ControllerWriteState(
                        snapshot.kind(), res.getString("dimension"),
                        new BlockPos(res.getInt("block_x"), res.getInt("block_y"),
                                res.getInt("block_z")),
                        res.getString("sublevel_id"), insertedLinkerManifestId(controllerData),
                        controllerDataForStorage(controllerData), draftGraph.copy(), activeGraph.copy(),
                        controllerGraphHistory(controllerData), snapshot.revision(), snapshot.hash()));
                return snapshot;
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to load controller {} from SQLite", manifestId, err);
            return null;
        }
    }

    // Load the linker
    static synchronized @Nullable ControllerManifestStore.ManifestSnapshot loadLinker(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return null;
        }
        Connection connection = connection(null);
        if (connection == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT revision, content_hash, linker_uuid, edit_mode, target_mode,
                       channel_binds_nbt, custom_entry_binds_nbt, stored_controller_manifests_nbt,
                       selected_graph_id
                FROM linkers
                WHERE id = ?
                """)) {
            statement.setString(1, manifestId);
            try (ResultSet res = statement.executeQuery()) {
                if (!res.next()) {
                    return null;
                }
                CompoundTag linkerData = loadLinkerRoot(
                        connection,
                        manifestId,
                        res.getString("linker_uuid"),
                        res.getString("edit_mode"),
                        res.getString("target_mode"),
                        res.getBytes("channel_binds_nbt"),
                        res.getBytes("custom_entry_binds_nbt"),
                        res.getBytes("stored_controller_manifests_nbt"),
                        res.getString("selected_graph_id"));
                return new ControllerManifestStore.ManifestSnapshot(
                        manifestId,
                        res.getInt("revision"),
                        res.getString("content_hash"),
                        ControllerManifestStore.STORAGE_VERSION,
                        "contraption_network_linker",
                        new CompoundTag(),
                        new CompoundTag(),
                        new CompoundTag(),
                        linkerData);
            }
        } catch (SQLException err) {
            LINKER_TARGET_CACHE.invalidate(connection, manifestId);
            Create.LOGGER.warn("Failed to load linker {} from SQLite", manifestId, err);
            return null;
        }
    }

    // Load the linker header
    static synchronized @Nullable LinkerHeader loadLinkerHeader(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return null;
        }
        Connection connection = connection(null);
        if (connection == null) {
            return null;
        }
        LinkerTargets cached = LINKER_TARGET_CACHE.get(connection, manifestId);
        if (cached != null) {
            return cached.found() ? new LinkerHeader(cached.linkerId(), cached.editMode(), cached.targetMode()) : null;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT linker_uuid, edit_mode, target_mode
                FROM linkers
                WHERE id = ?
                """)) {
            statement.setString(1, manifestId);
            try (ResultSet res = statement.executeQuery()) {
                if (!res.next()) {
                    return null;
                }
                return new LinkerHeader(
                        parseUuid(res.getString("linker_uuid")),
                        ContraptionNetworkLinkerData.LinkMode.byId(res.getString("edit_mode")),
                        ContraptionNetworkLinkerData.TargetMode.byId(res.getString("target_mode")));
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to load linker header {} from SQLite", manifestId, err);
            return null;
        }
    }

    // Load the linker revision
    static synchronized int loadLinkerRevision(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return 0;
        }
        Connection connection = connection(null);
        if (connection == null) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM linkers WHERE id = ?")) {
            statement.setString(1, manifestId);
            try (ResultSet res = statement.executeQuery()) {
                return res.next() ? Math.max(0, res.getInt(1)) : 0;
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to load linker revision {} from SQLite", manifestId, err);
            return 0;
        }
    }

    // Load the linker stored graphs
    static synchronized ListTag loadLinkerStoredGraphs(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return new ListTag();
        }
        Connection connection = connection(null);
        if (connection == null) {
            return new ListTag();
        }
        try {
            return loadStoredGraphs(connection, "linker", manifestId);
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to load stored graphs for linker {}", manifestId, err);
            return new ListTag();
        }
    }

    // Load the linker targets
    static synchronized LinkerTargets loadLinkerTargets(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return new LinkerTargets(false, null, null, null, List.of());
        }
        Connection connection = connection(null);
        if (connection == null) {
            return new LinkerTargets(false, null, null, null, List.of());
        }
        try {
            return LINKER_TARGET_CACHE.getOrLoad(connection, manifestId,
                    () -> loadLinkerTargetsWithPresence(connection, manifestId));
        } catch (SQLException err) {
            LINKER_TARGET_CACHE.invalidate(connection, manifestId);
            Create.LOGGER.warn("Failed to load linker targets {} from SQLite", manifestId, err);
            return new LinkerTargets(false, null, null, null, List.of());
        }
    }

    // Save the controller
    static synchronized @Nullable ControllerManifestStore.ManifestSnapshot saveController(
            String id,
            int revision,
            String kind,
            @Nullable Level level,
            @Nullable BlockPos ownerPos,
            @Nullable UUID subLevelId,
            @Nullable CompoundTag controllerData,
            @Nullable CompoundTag draftGraph,
            @Nullable CompoundTag activeGraph,
            String insertedLinkerManifestId) {
        // ------------------------------------SNAPSHOT CHECKS------------------------------------
        Connection connection = connection(level);
        if (connection == null) {
            return null;
        }
        // ------------------------------------CONTROLLER SNAPSHOT------------------------------------
        CompoundTag controllerCopy = controllerDataForStorage(controllerData);
        CompoundTag draftCopy = draftGraph == null ? new CompoundTag() : draftGraph.copy();
        CompoundTag activeCopy = activeGraph == null ? new CompoundTag() : activeGraph.copy();
        CompoundTag historyCopy = controllerGraphHistory(controllerData);
        String dimension = level == null ? "" : level.dimension().location().toString();
        BlockPos pos = ownerPos == null ? BlockPos.ZERO : ownerPos;
        String subLevel = subLevelId == null ? "" : subLevelId.toString();
        String insertedLinker = insertedLinkerManifestId == null ? "" : insertedLinkerManifestId;
        ControllerWriteState cached = cachedControllerWrite(connection, id);
        if (cached != null && cached.matches(
                kind, dimension, pos, subLevel, insertedLinker,
                controllerCopy, draftCopy, activeCopy, historyCopy)) {
            return cached.snapshot(id);
        }
        // -----------------------------------------------------CONTENT HASH-----------------------------------------------------
        byte[] controllerBytes = nbtBytes(controllerCopy);
        byte[] draftBytes = nbtBytes(draftCopy);
        byte[] activeBytes = nbtBytes(activeCopy);
        byte[] historyBytes = nbtBytes(historyCopy);
        String hash = sha256("controller:" + id + ":" + kind,
                controllerBytes, draftBytes, activeBytes, historyBytes);
        String customName = controllerCopy.getString("CustomName");
        long gameTime = level == null ? 0L : level.getGameTime();
        String updatedAt = Instant.now().toString();
        String compiledHash = sha256("active:" + id, activeBytes);

        try {
            // ------------------------------------DATABASE TRANSACTION------------------------------------
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO controllers (
                            id, kind, dimension, block_x, block_y, block_z, sublevel_id,
                            custom_name, revision, content_hash, compiled_program_hash,
                            updated_at_game_time, updated_at, manifest_json, controller_data_nbt
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            kind = excluded.kind,
                            dimension = excluded.dimension,
                            block_x = excluded.block_x,
                            block_y = excluded.block_y,
                            block_z = excluded.block_z,
                            sublevel_id = excluded.sublevel_id,
                            custom_name = excluded.custom_name,
                            revision = excluded.revision,
                            content_hash = excluded.content_hash,
                            compiled_program_hash = excluded.compiled_program_hash,
                            updated_at_game_time = excluded.updated_at_game_time,
                            updated_at = excluded.updated_at,
                            manifest_json = excluded.manifest_json,
                            controller_data_nbt = excluded.controller_data_nbt
                        """)) {
                    statement.setString(1, id);
                    statement.setString(2, kind);
                    statement.setString(3, dimension);
                    statement.setInt(4, pos.getX());
                    statement.setInt(5, pos.getY());
                    statement.setInt(6, pos.getZ());
                    statement.setString(7, subLevel);
                    statement.setString(8, customName);
                    statement.setInt(9, revision);
                    statement.setString(10, hash);
                    statement.setString(11, compiledHash);
                    statement.setLong(12, gameTime);
                    statement.setString(13, updatedAt);
                    statement.setString(14, "");
                    setNullableBytes(statement, 15, controllerBytes);
                    statement.executeUpdate();
                }
                upsertGraph(connection, "controller", id, "draft", "", draftCopy, revision, updatedAt);
                upsertGraph(connection, "controller", id, "active", "", activeCopy, revision, updatedAt);
                upsertGraph(connection, "controller", id, "history", "", historyCopy, revision, updatedAt);
                replaceCtrlLinkedBlocks(connection, id, dimension, controllerCopy);
                if (!insertedLinker.isBlank()) {
                    attachLinkerToCtrl(connection, insertedLinker, id, dimension);
                }
                connection.commit();
            } catch (SQLException | RuntimeException err) {
                connection.rollback();
                throw err;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            // ------------------------------------CACHE UPDATE------------------------------------
            ControllerManifestStore.ManifestSnapshot snapshot = new ControllerManifestStore.ManifestSnapshot(id, revision, hash,
                    ControllerManifestStore.STORAGE_VERSION, kind, controllerCopy, draftCopy, activeCopy,
                    new CompoundTag());
            cacheControllerWrite(connection, id, new ControllerWriteState(
                    kind, dimension, pos, subLevel, insertedLinker,
                    controllerCopy.copy(), draftCopy.copy(), activeCopy.copy(), historyCopy.copy(),
                    revision, hash));
            return snapshot;
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to save controller {} to SQLite", id, err);
            return null;
        }
    }

    // Save the linker
    static synchronized @Nullable ControllerManifestStore.ManifestSnapshot saveLinker(String id,
                                                                                      int revision,
                                                                                      @Nullable CompoundTag linkerData) {
        Connection connection = connection(null);
        if (connection == null) {
            return null;
        }
        // ------------------------------------LINKER SNAPSHOT------------------------------------
        CompoundTag linkerCopy = linkerData == null ? new CompoundTag() : linkerData.copy();
        byte[] linkerBytes = nbtBytes(linkerCopy);
        String hash = sha256("linker:" + id + ":" + revision, linkerBytes);
        String updatedAt = Instant.now().toString();
        String linkerUuid = linkerCopy.hasUUID(TAG_LINKER_ID) ? linkerCopy.getUUID(TAG_LINKER_ID).toString() : "";
        String editMode = modeOrDefault(linkerCopy.getString(TAG_EDIT_MODE), ContraptionNetworkLinkerData.LinkMode.OUTPUT.id());
        String targetMode = modeOrDefault(linkerCopy.getString(TAG_TARGET_MODE), ContraptionNetworkLinkerData.TargetMode.AUTO.id());
        String selectedGraphId = linkerCopy.getString(TAG_SELECTED_GRAPH_ID);
        byte[] channelBinds = sectionBytes(linkerCopy, TAG_CHANNEL_BINDS);
        byte[] customEntryBinds = sectionBytes(linkerCopy, TAG_CUSTOM_ENTRY_BINDS);
        byte[] storedControllerManifests = sectionBytes(linkerCopy, TAG_STORED_CONTROLLER_MANIFESTS);
        LinkerTargets nextTargets = linkerTargetsFromData(linkerCopy);

        // ------------------------------------TRANSACTION SETUP------------------------------------
        try {
            LinkerTargets previousTargets = LINKER_TARGET_CACHE.get(connection, id);
            if (previousTargets == null) {
                previousTargets = loadLinkerTargetsWithPresence(connection, id);
            }
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // ------------------------------------LINKER ROW------------------------------------
                String existingControllerId = existingControllerId(connection, id);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO linkers (
                            id, controller_id, revision, content_hash,
                            updated_at_game_time, updated_at, manifest_json,
                            linker_uuid, edit_mode, target_mode,
                            channel_binds_nbt, custom_entry_binds_nbt, stored_controller_manifests_nbt,
                            selected_graph_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            controller_id = COALESCE(linkers.controller_id, excluded.controller_id),
                            revision = excluded.revision,
                            content_hash = excluded.content_hash,
                            updated_at_game_time = excluded.updated_at_game_time,
                            updated_at = excluded.updated_at,
                            manifest_json = excluded.manifest_json,
                            linker_uuid = excluded.linker_uuid,
                            edit_mode = excluded.edit_mode,
                            target_mode = excluded.target_mode,
                            channel_binds_nbt = excluded.channel_binds_nbt,
                            custom_entry_binds_nbt = excluded.custom_entry_binds_nbt,
                            stored_controller_manifests_nbt = excluded.stored_controller_manifests_nbt,
                            selected_graph_id = excluded.selected_graph_id
                        """)) {
                    statement.setString(1, id);
                    setNullableString(statement, 2, existingControllerId);
                    statement.setInt(3, revision);
                    statement.setString(4, hash);
                    statement.setLong(5, 0L);
                    statement.setString(6, updatedAt);
                    statement.setString(7, "");
                    statement.setString(8, linkerUuid);
                    statement.setString(9, editMode);
                    statement.setString(10, targetMode);
                    setNullableBytes(statement, 11, channelBinds);
                    setNullableBytes(statement, 12, customEntryBinds);
                    setNullableBytes(statement, 13, storedControllerManifests);
                    statement.setString(14, selectedGraphId);
                    statement.executeUpdate();
                }
                // ------------------------------------LINKED TARGETS------------------------------------
                if (!previousTargets.found()
                        || !Objects.equals(previousTargets.targets(), nextTargets.targets())) {
                    replaceLinkerLinkedBlocks(connection, id, existingLinkerDimension(connection, id), linkerCopy);
                }
                if (linkerCopy.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
                    upsertStoredGraphs(connection, "linker", id, linkerCopy, revision, updatedAt);
                }
                connection.commit();
            } catch (SQLException | RuntimeException err) {
                connection.rollback();
                throw err;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            // ------------------------------------CACHE UPDATE------------------------------------
            LINKER_TARGET_CACHE.put(connection, id, nextTargets);
            return new ControllerManifestStore.ManifestSnapshot(id, revision, hash,
                    ControllerManifestStore.STORAGE_VERSION, "contraption_network_linker",
                    new CompoundTag(), new CompoundTag(), new CompoundTag(), linkerCopy);
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to save linker {} to SQLite", id, err);
            return null;
        }
    }

    // Clone the controller draft graph to the linker
    static synchronized boolean cloneControllerDraftGraphToLinker(
            String controllerId, String linkerId, String graphName) {
        // -----------------------------------------------------CLONE CHECKS-----------------------------------------------------
        if (controllerId == null || controllerId.isBlank()
                || linkerId == null || linkerId.isBlank()) {
            return false;
        }
        Connection connection = connection(null);
        if (connection == null) {
            return false;
        }
        // ------------------------------------GRAPH METADATA------------------------------------
        String storedName = graphName == null || graphName.isBlank()
                ? "Advanced Controller Graph" : graphName.trim();
        String role = "draft";
        String updatedAt = Instant.now().toString();
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM graphs WHERE owner_type = 'linker' AND owner_id = ?")) {
                    statement.setString(1, linkerId);
                    statement.executeUpdate();
                }
                deleteGraphImages(connection, "linker", linkerId, null);
                int copied;
                try (PreparedStatement statement = connection.prepareStatement("""
                        // ------------------------------------GRAPH COPY------------------------------------
                        INSERT INTO graphs (
                            id, owner_type, owner_id, graph_role, graph_name, revision,
                            graph_json, graph_nbt, content_hash, needs_compilation, updated_at
                        )
                        SELECT ?, 'linker', ?, ?, ?, revision,
                               graph_json, graph_nbt, content_hash, needs_compilation, ?
                        FROM graphs
                        WHERE owner_type = 'controller' AND owner_id = ? AND graph_role = 'draft'
                        """)) {
                    statement.setString(1, "linker:" + linkerId + ":" + role);
                    statement.setString(2, linkerId);
                    statement.setString(3, role);
                    statement.setString(4, storedName);
                    statement.setString(5, updatedAt);
                    statement.setString(6, controllerId);
                    copied = statement.executeUpdate();
                }
                if (copied == 0) {
                    connection.rollback();
                    connection.setAutoCommit(previousAutoCommit);
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        // ------------------------------------IMAGE COPY------------------------------------
                        INSERT INTO graph_images (
                            owner_type, owner_id, graph_role, asset_id,
                            media_type, base64_data, updated_at
                        )
                        SELECT 'linker', ?, ?, asset_id, media_type, base64_data, ?
                        FROM graph_images
                        WHERE owner_type = 'controller' AND owner_id = ? AND graph_role = 'draft'
                        """)) {
                    statement.setString(1, linkerId);
                    statement.setString(2, role);
                    statement.setString(3, updatedAt);
                    statement.setString(4, controllerId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        // ------------------------------------LINKER ASSIGNMENT------------------------------------
                        UPDATE linkers
                        SET controller_id = ?, selected_graph_id = ?, revision = revision + 1,
                            updated_at = ?
                        WHERE id = ?
                        """)) {
                    statement.setString(1, controllerId);
                    statement.setString(2, role);
                    statement.setString(3, updatedAt);
                    statement.setString(4, linkerId);
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        connection.setAutoCommit(previousAutoCommit);
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException err) {
                connection.rollback();
                throw err;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to clone controller graph {} into linker {}",
                    controllerId, linkerId, err);
            return false;
        }
    }

    // Import the controller
    static synchronized boolean importController(ControllerManifestStore.ManifestSnapshot snapshot,
                                                 @Nullable Level level) {
        if (snapshot == null) {
            return false;
        }
        return saveController(snapshot.id(), Math.max(0, snapshot.revision()),
                snapshot.kind() == null || snapshot.kind().isBlank() ? "base_controller" : snapshot.kind(),
                level, BlockPos.ZERO, null, snapshot.controllerData(), snapshot.draftGraph(), snapshot.activeGraph(),
                insertedLinkerManifestId(snapshot.controllerData())) != null;
    }

    // Import the linker
    static synchronized boolean importLinker(ControllerManifestStore.ManifestSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return saveLinker(snapshot.id(), Math.max(0, snapshot.revision()), snapshot.linkerData()) != null;
    }

    // Delete the controller
    static synchronized boolean deleteController(String manifestId, @Nullable Level level) {
        if (manifestId == null || manifestId.isBlank()) {
            return false;
        }
        Connection connection = connection(level);
        if (connection == null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM controllers WHERE id = ?")) {
            statement.setString(1, manifestId);
            boolean deleted = statement.executeUpdate() > 0;
            if (deleted) {
                invalidateCtrlWrite(connection, manifestId);
            }
            return deleted;
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to delete controller {} from SQLite", manifestId, err);
            return false;
        }
    }

    // Rewrite the linkers
    static synchronized int rewriteLinkers(Function<CompoundTag, Boolean> rewriter) {
        if (rewriter == null) {
            return 0;
        }
        Connection connection = connection(null);
        if (connection == null) {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM linkers ORDER BY id")) {
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    ids.add(res.getString("id"));
                }
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to read linkers from SQLite", err);
            return 0;
        }

        int changed = 0;
        for (String id : ids) {
            ControllerManifestStore.ManifestSnapshot snapshot = loadLinker(id);
            if (snapshot == null) {
                continue;
            }
            CompoundTag linkerData = snapshot.linkerData();
            if (Boolean.TRUE.equals(rewriter.apply(linkerData))
                    && saveLinker(snapshot.id(), snapshot.revision() + 1, linkerData) != null) {
                changed++;
            }
        }
        return changed;
    }

    // Remove the linked block assignments
    static synchronized BrokenBlockCleanup removeLinkedBlockAssignments(Level level,
                                                                        BlockPos blockPos,
                                                                        @Nullable UUID subLevelId,
                                                                        String brokenBlockId) {
        if (level == null || blockPos == null) {
            return new BrokenBlockCleanup(0, 0);
        }
        Connection connection = connection(level);
        if (connection == null) {
            return new BrokenBlockCleanup(0, 0);
        }

        String dimension = level.dimension().location().toString();
        String subLevel = subLevelId == null ? "" : subLevelId.toString();
        // -----------------------------------------------------OWNER LOOKUP-----------------------------------------------------
        List<IndexedOwner> owners = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DISTINCT owner_type, owner_id, node_id
                FROM linked_blocks
                WHERE dimension = ? AND sublevel_id = ?
                  AND rel_x = ? AND rel_y = ? AND rel_z = ?
                """)) {
            statement.setString(1, dimension);
            statement.setString(2, subLevel);
            statement.setInt(3, blockPos.getX());
            statement.setInt(4, blockPos.getY());
            statement.setInt(5, blockPos.getZ());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    owners.add(new IndexedOwner(
                            res.getString("owner_type"),
                            res.getString("owner_id"),
                            nullToEmpty(res.getString("node_id"))));
                }
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to find linked assignments for broken block {}", blockPos, err);
            return new BrokenBlockCleanup(0, 0);
        }
        if (owners.isEmpty()) {
            return new BrokenBlockCleanup(0, 0);
        }

        // ------------------------------------AFFECTED OWNERS------------------------------------
        Set<String> removedNodeIds = new LinkedHashSet<>();
        Set<String> linkerIds = new LinkedHashSet<>();
        Set<String> controllerIds = new LinkedHashSet<>();
        for (IndexedOwner owner : owners) {
            if (!owner.nodeId().isBlank()) {
                removedNodeIds.add(ContraptionNetworkLinkerData.baseNodeIdFromTargetId(owner.nodeId()));
            }
            if ("linker".equals(owner.ownerType())) {
                linkerIds.add(owner.ownerId());
            } else if ("controller".equals(owner.ownerType())) {
                controllerIds.add(owner.ownerId());
            }
        }
        for (String linkerId : linkerIds) {
            try {
                String attachedControllerId = existingControllerId(connection, linkerId);
                if (attachedControllerId != null && !attachedControllerId.isBlank()) {
                    controllerIds.add(attachedControllerId);
                }
            } catch (SQLException err) {
                Create.LOGGER.warn("Failed to find the controller attached to linker {}", linkerId, err);
            }
        }

        // ------------------------------------LINKER CLEANUP------------------------------------
        int updatedLinkers = 0;
        for (String linkerId : linkerIds) {
            ControllerManifestStore.ManifestSnapshot snapshot = loadLinker(linkerId);
            if (snapshot == null) {
                continue;
            }
            CompoundTag linkerData = snapshot.linkerData();
            if (ContraptionNetworkLinkerData.removeTargets(linkerData,
                    target -> ContraptionNetworkLinkerTracker.matchesBrokenTarget(
                            target, blockPos, subLevelId))
                    && saveLinker(snapshot.id(), snapshot.revision() + 1, linkerData) != null) {
                updatedLinkers++;
            }
        }

        // ------------------------------------CONTROLLER CLEANUP------------------------------------
        int updatedControllers = 0;
        for (String controllerId : controllerIds) {
            ControllerOwner owner = loadControllerOwner(connection, controllerId);
            ControllerManifestStore.ManifestSnapshot snapshot = loadController(controllerId, level);
            if (owner == null || snapshot == null) {
                continue;
            }
            CompoundTag controllerData = snapshot.controllerData();
            CompoundTag draftGraph = snapshot.draftGraph();
            CompoundTag activeGraph = snapshot.activeGraph();
            boolean changed = ContraptionNetworkLinkerData.removeControllerTargetReferences(
                    controllerData, blockPos, subLevelId, removedNodeIds);
            changed |= ContraptionNetworkLinkerData.removeGraphTargetReferences(draftGraph, removedNodeIds);
            changed |= ContraptionNetworkLinkerData.removeGraphTargetReferences(activeGraph, removedNodeIds);
            if (changed && saveController(
                    owner.id(), owner.revision() + 1, owner.kind(), level, owner.blockPos(), owner.subLevelId(),
                    controllerData, draftGraph, activeGraph, insertedLinkerManifestId(controllerData)) != null) {
                updatedControllers++;
            }
        }
        return new BrokenBlockCleanup(updatedLinkers, updatedControllers);
    }

    // Load the linker data
    static synchronized List<CompoundTag> loadLinkerData() {
        Connection connection = connection(null);
        if (connection == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM linkers ORDER BY id")) {
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    ids.add(res.getString("id"));
                }
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to list linker data from SQLite", err);
            return List.of();
        }

        List<CompoundTag> data = new ArrayList<>();
        for (String id : ids) {
            ControllerManifestStore.ManifestSnapshot snapshot = loadLinker(id);
            if (snapshot != null) {
                data.add(snapshot.linkerData());
            }
        }
        return List.copyOf(data);
    }

    // Close every controller database connection
    static synchronized void closeAll() {
        for (Connection connection : CONNECTIONS.values()) {
            try {
                connection.close();
            } catch (SQLException err) {
                Create.LOGGER.warn("Failed to close controller SQLite database", err);
            }
        }
        CONNECTIONS.clear();
        LINKER_TARGET_CACHE.clearAll();
        CONTROLLER_WRITE_CACHE.clear();
    }

    // Import the legacy linkers
    static synchronized void importLegacyLinkers(Path dir,
                                                 Function<Path, ControllerManifestStore.ManifestSnapshot> loader) {
        if (dir == null || loader == null || !Files.isDirectory(dir) || !MIGRATED_LINKER_PATHS.add(dir)) {
            return;
        }
        try (var paths = Files.list(dir)) {
            for (Path path : paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json")).toList()) {
                ControllerManifestStore.ManifestSnapshot snapshot = loader.apply(path);
                if (snapshot != null && loadLinker(snapshot.id()) == null) {
                    importLinker(snapshot);
                }
            }
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to import legacy linker manifests from {}", dir, err);
        }
    }

    // Get the connection
    private static @Nullable Connection connection(@Nullable Level level) {
        Path path = databasePath(level);
        if (path == null) {
            return null;
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            Connection existing = CONNECTIONS.get(normalized);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            if (existing != null) {
                LINKER_TARGET_CACHE.clear(existing);
                CONTROLLER_WRITE_CACHE.remove(existing);
            }
            Files.createDirectories(normalized.getParent());
            Connection connection = SqliteDriverLoader.connect("jdbc:sqlite:" + normalized);
            applyPragmas(connection);
            installSchema(connection);
            migrateLegacyManifestJson(connection);
            reindexCtrlTargetsV3(connection);
            CONNECTIONS.put(normalized, connection);
            return connection;
        } catch (SQLException | IOException err) {
            Create.LOGGER.warn("Controller SQLite database is unavailable", err);
            return null;
        }
    }

    // Apply the pragmas
    private static void applyPragmas(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA temp_store = MEMORY");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    // Install the schema
    private static void installSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // ------------------------------------METADATA / OWNERS------------------------------------
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS controllers (
                        id TEXT PRIMARY KEY,
                        kind TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        sublevel_id TEXT NOT NULL DEFAULT '',
                        custom_name TEXT NOT NULL DEFAULT '',
                        revision INTEGER NOT NULL DEFAULT 0,
                        content_hash TEXT NOT NULL DEFAULT '',
                        compiled_program_hash TEXT NOT NULL DEFAULT '',
                        updated_at_game_time INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL DEFAULT '',
                        manifest_json TEXT NOT NULL DEFAULT '',
                        controller_data_nbt BLOB
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS linkers (
                        id TEXT PRIMARY KEY,
                        controller_id TEXT,
                        revision INTEGER NOT NULL DEFAULT 0,
                        content_hash TEXT NOT NULL DEFAULT '',
                        updated_at_game_time INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL DEFAULT '',
                        manifest_json TEXT NOT NULL DEFAULT '',
                        linker_uuid TEXT NOT NULL DEFAULT '',
                        edit_mode TEXT NOT NULL DEFAULT 'output',
                        target_mode TEXT NOT NULL DEFAULT 'auto',
                        channel_binds_nbt BLOB,
                        custom_entry_binds_nbt BLOB,
                        stored_controller_manifests_nbt BLOB,
                        selected_graph_id TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(controller_id) REFERENCES controllers(id) ON DELETE SET NULL
                    )
                    """);
            // ------------------------------------GRAPHS / PROGRAMS------------------------------------
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS graphs (
                        id TEXT PRIMARY KEY,
                        owner_type TEXT NOT NULL,
                        owner_id TEXT NOT NULL,
                        graph_role TEXT NOT NULL,
                        graph_name TEXT NOT NULL DEFAULT '',
                        revision INTEGER NOT NULL DEFAULT 0,
                        graph_json TEXT NOT NULL DEFAULT '',
                        graph_nbt BLOB,
                        content_hash TEXT NOT NULL DEFAULT '',
                        needs_compilation INTEGER NOT NULL DEFAULT 0,
                        updated_at TEXT NOT NULL DEFAULT ''
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS graph_images (
                        owner_type TEXT NOT NULL,
                        owner_id TEXT NOT NULL,
                        graph_role TEXT NOT NULL,
                        asset_id TEXT NOT NULL,
                        media_type TEXT NOT NULL,
                        base64_data TEXT NOT NULL,
                        updated_at TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(owner_type, owner_id, graph_role, asset_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS compiled_programs (
                        owner_type TEXT NOT NULL,
                        owner_id TEXT NOT NULL,
                        graph_hash TEXT NOT NULL,
                        program_format TEXT NOT NULL,
                        program_blob BLOB NOT NULL,
                        compiled_at TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(owner_type, owner_id, graph_hash)
                    )
                    """);
            // ------------------------------------LINKED TARGETS / ACTIONS------------------------------------
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS linked_blocks (
                        id TEXT PRIMARY KEY,
                        owner_type TEXT NOT NULL,
                        owner_id TEXT NOT NULL,
                        linker_id TEXT,
                        node_id TEXT,
                        port_id TEXT,
                        link_kind TEXT NOT NULL,
                        scope TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        block_id TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        sublevel_id TEXT NOT NULL DEFAULT '',
                        rel_x INTEGER NOT NULL,
                        rel_y INTEGER NOT NULL,
                        rel_z INTEGER NOT NULL,
                        world_x INTEGER,
                        world_y INTEGER,
                        world_z INTEGER,
                        face TEXT NOT NULL DEFAULT '',
                        label TEXT NOT NULL DEFAULT '',
                        face_label TEXT NOT NULL DEFAULT '',
                        face_signal_key TEXT NOT NULL DEFAULT '',
                        target_json TEXT NOT NULL DEFAULT '{}',
                        created_at TEXT NOT NULL DEFAULT '',
                        updated_at TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(linker_id) REFERENCES linkers(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS runtime_actions (
                        id TEXT PRIMARY KEY,
                        controller_id TEXT NOT NULL,
                        linker_id TEXT,
                        linked_block_id TEXT,
                        action_type TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1,
                        payload_json TEXT NOT NULL DEFAULT '{}',
                        created_at_game_time INTEGER NOT NULL DEFAULT 0,
                        updated_at_game_time INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(controller_id) REFERENCES controllers(id) ON DELETE CASCADE,
                        FOREIGN KEY(linker_id) REFERENCES linkers(id) ON DELETE SET NULL,
                        FOREIGN KEY(linked_block_id) REFERENCES linked_blocks(id) ON DELETE SET NULL
                    )
                    """);
        }
        // -----------------------------------------------------SCHEMA UPGRADES---------------------------------------------------
        ensureColumn(connection, "controllers", "controller_data_nbt", "BLOB");
        ensureColumn(connection, "linkers", "linker_uuid", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(connection, "linkers", "edit_mode", "TEXT NOT NULL DEFAULT 'output'");
        ensureColumn(connection, "linkers", "target_mode", "TEXT NOT NULL DEFAULT 'auto'");
        ensureColumn(connection, "linkers", "channel_binds_nbt", "BLOB");
        ensureColumn(connection, "linkers", "custom_entry_binds_nbt", "BLOB");
        ensureColumn(connection, "linkers", "stored_controller_manifests_nbt", "BLOB");
        ensureColumn(connection, "linkers", "selected_graph_id", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(connection, "graphs", "graph_name", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(connection, "graphs", "graph_nbt", "BLOB");
        ensureColumn(connection, "linked_blocks", "face_label", "TEXT NOT NULL DEFAULT ''");
        ensureColumn(connection, "linked_blocks", "face_signal_key", "TEXT NOT NULL DEFAULT ''");
        // -----------------------------------------------------LOOKUP INDEXES----------------------------------------------------
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_controllers_owner ON controllers(dimension, block_x, block_y, block_z, sublevel_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_linkers_controller ON linkers(controller_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_graphs_owner ON graphs(owner_type, owner_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_graphs_role ON graphs(owner_type, owner_id, graph_role)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_graphs_needs_compilation ON graphs(needs_compilation)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_graph_images_owner ON graph_images(owner_type, owner_id, graph_role)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_linked_blocks_relative ON linked_blocks(dimension, sublevel_id, rel_x, rel_y, rel_z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_linked_blocks_world ON linked_blocks(dimension, world_x, world_y, world_z)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_linked_blocks_owner ON linked_blocks(owner_type, owner_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_linked_blocks_linker ON linked_blocks(linker_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_linked_blocks_block_id ON linked_blocks(block_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_runtime_actions_controller ON runtime_actions(controller_id, active)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_runtime_actions_linked_block ON runtime_actions(linked_block_id, active)");
        }
        // -----------------------------------------------------SCHEMA METADATA---------------------------------------------------
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO schema_meta(key, value) VALUES (?, ?)")) {
            statement.setString(1, "schema_version");
            statement.setString(2, "2");
            statement.executeUpdate();
            statement.setString(1, "created_by_mod");
            statement.setString(2, "createthrusters");
            statement.executeUpdate();
            statement.setString(1, "created_at");
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    // Ensure the column
    private static void ensureColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    if (column.equalsIgnoreCase(res.getString("name"))) {
                        return;
                    }
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    // Reindex the V3 controller targets
    private static void reindexCtrlTargetsV3(Connection connection) throws SQLException {
        int schemaVersion = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM schema_meta WHERE key = 'schema_version'")) {
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    try {
                        schemaVersion = Integer.parseInt(res.getString(1));
                    } catch (NumberFormatException ignored) {
                        schemaVersion = 0;
                    }
                }
            }
        }
        if (schemaVersion >= 3) {
            return;
        }

        List<ControllerIndexData> controllers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, dimension, controller_data_nbt FROM controllers ORDER BY id")) {
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    byte[] controllerBytes = res.getBytes("controller_data_nbt");
                    if (controllerBytes == null || controllerBytes.length == 0) {
                        continue;
                    }
                    controllers.add(new ControllerIndexData(
                            res.getString("id"),
                            nullToEmpty(res.getString("dimension")),
                            compoundFromBytes(controllerBytes)));
                }
            }
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (ControllerIndexData controller : controllers) {
                if (controller.controllerData().isEmpty()) {
                    continue;
                }
                replaceCtrlLinkedBlocks(
                        connection, controller.id(), controller.dimension(), controller.controllerData());
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO schema_meta(key, value) VALUES ('schema_version', '3') "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException err) {
            connection.rollback();
            throw err;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    // Migrate the legacy manifest JSON
    private static void migrateLegacyManifestJson(Connection connection) {
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int controllers = migrateLegacyCtrlRows(connection);
                int linkers = migrateLegacyLinkerRows(connection);
                connection.commit();
                if (controllers + linkers > 0) {
                    Create.LOGGER.info("Migrated {} controller and {} linker SQLite manifest row(s)", controllers, linkers);
                }
            } catch (SQLException | RuntimeException err) {
                connection.rollback();
                Create.LOGGER.warn("Failed to migrate legacy SQLite manifest JSON", err);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to prepare legacy SQLite manifest migration", err);
        }
    }

    // Migrate the legacy ctrl rows
    private static int migrateLegacyCtrlRows(Connection connection) throws SQLException {
        List<LegacyControllerRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, kind, revision, dimension, manifest_json
                FROM controllers
                WHERE manifest_json <> ''
                  AND (controller_data_nbt IS NULL OR length(controller_data_nbt) = 0)
                ORDER BY id
                """)) {
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    rows.add(new LegacyControllerRow(
                            res.getString("id"),
                            res.getString("kind"),
                            res.getInt("revision"),
                            res.getString("dimension"),
                            res.getString("manifest_json")));
                }
            }
        }

        int changed = 0;
        String updatedAt = Instant.now().toString();
        for (LegacyControllerRow row : rows) {
            ControllerManifestStore.ManifestSnapshot snapshot = snapshotFromJson(row.manifestJson, false);
            if (snapshot == null) {
                continue;
            }
            CompoundTag controllerData = snapshot.controllerData();
            String kind = snapshot.kind() == null || snapshot.kind().isBlank() ? row.kind : snapshot.kind();
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE controllers
                    SET kind = ?, controller_data_nbt = ?, manifest_json = ''
                    WHERE id = ?
                    """)) {
                statement.setString(1, kind == null || kind.isBlank() ? "base_controller" : kind);
                setNullableBytes(statement, 2, nbtBytes(controllerData));
                statement.setString(3, row.id);
                statement.executeUpdate();
            }
            upsertGraph(connection, "controller", row.id, "draft", "", snapshot.draftGraph(), row.revision, updatedAt);
            upsertGraph(connection, "controller", row.id, "active", "", snapshot.activeGraph(), row.revision, updatedAt);
            replaceCtrlLinkedBlocks(connection, row.id, row.dimension == null ? "" : row.dimension, controllerData);
            String insertedLinker = insertedLinkerManifestId(controllerData);
            if (!insertedLinker.isBlank()) {
                attachLinkerToCtrl(connection, insertedLinker, row.id, row.dimension == null ? "" : row.dimension);
            }
            changed++;
        }
        return changed;
    }

    // Migrate the legacy linker rows
    private static int migrateLegacyLinkerRows(Connection connection) throws SQLException {
        List<LegacyLinkerRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, revision, manifest_json
                FROM linkers
                WHERE manifest_json <> ''
                ORDER BY id
                """)) {
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    rows.add(new LegacyLinkerRow(
                            res.getString("id"),
                            res.getInt("revision"),
                            res.getString("manifest_json")));
                }
            }
        }

        int changed = 0;
        String updatedAt = Instant.now().toString();
        for (LegacyLinkerRow row : rows) {
            ControllerManifestStore.ManifestSnapshot snapshot = snapshotFromJson(row.manifestJson, true);
            if (snapshot == null) {
                continue;
            }
            CompoundTag linkerData = snapshot.linkerData();
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE linkers
                    SET linker_uuid = ?,
                        edit_mode = ?,
                        target_mode = ?,
                        channel_binds_nbt = ?,
                        custom_entry_binds_nbt = ?,
                        stored_controller_manifests_nbt = ?,
                        selected_graph_id = ?,
                        manifest_json = ''
                    WHERE id = ?
                    """)) {
                statement.setString(1, linkerData.hasUUID(TAG_LINKER_ID) ? linkerData.getUUID(TAG_LINKER_ID).toString() : "");
                statement.setString(2, modeOrDefault(linkerData.getString(TAG_EDIT_MODE), ContraptionNetworkLinkerData.LinkMode.OUTPUT.id()));
                statement.setString(3, modeOrDefault(linkerData.getString(TAG_TARGET_MODE), ContraptionNetworkLinkerData.TargetMode.AUTO.id()));
                setNullableBytes(statement, 4, sectionBytes(linkerData, TAG_CHANNEL_BINDS));
                setNullableBytes(statement, 5, sectionBytes(linkerData, TAG_CUSTOM_ENTRY_BINDS));
                setNullableBytes(statement, 6, sectionBytes(linkerData, TAG_STORED_CONTROLLER_MANIFESTS));
                statement.setString(7, linkerData.getString(TAG_SELECTED_GRAPH_ID));
                statement.setString(8, row.id);
                statement.executeUpdate();
            }
            replaceLinkerLinkedBlocks(connection, row.id, "", linkerData);
            upsertStoredGraphs(connection, "linker", row.id, linkerData, row.revision, updatedAt);
            changed++;
        }
        return changed;
    }

    // Load the linker root
    private static CompoundTag loadLinkerRoot(Connection connection,
                                             String manifestId,
                                             String linkerUuid,
                                             String editMode,
                                             String targetMode,
                                             byte @Nullable [] channelBinds,
                                             byte @Nullable [] customEntryBinds,
                                             byte @Nullable [] storedControllerManifests,
                                             String selectedGraphId) throws SQLException {
        UUID uuid = parseUuid(linkerUuid);
        ContraptionNetworkLinkerData.LinkMode resolvedEditMode = ContraptionNetworkLinkerData.LinkMode.byId(editMode);
        ContraptionNetworkLinkerData.TargetMode resolvedTargetMode = ContraptionNetworkLinkerData.TargetMode.byId(targetMode);
        LinkerTargets cached = LINKER_TARGET_CACHE.get(connection, manifestId);
        List<ContraptionNetworkLinkerData.LinkedTarget> targets;
        if (cached != null && cached.found()) {
            targets = cached.targets();
        } else {
            targets = loadLinkerTargets(connection, manifestId);
            LINKER_TARGET_CACHE.put(connection, manifestId,
                    new LinkerTargets(true, uuid, resolvedEditMode, resolvedTargetMode, targets));
        }
        CompoundTag root = ContraptionNetworkLinkerData.writeRoot(
                targets,
                resolvedEditMode,
                resolvedTargetMode);
        if (uuid != null) {
            root.putUUID(TAG_LINKER_ID, uuid);
        }
        putSection(root, TAG_CHANNEL_BINDS, channelBinds);
        putSection(root, TAG_CUSTOM_ENTRY_BINDS, customEntryBinds);
        putSection(root, TAG_STORED_CONTROLLER_MANIFESTS, storedControllerManifests);
        if (selectedGraphId != null && !selectedGraphId.isBlank()) {
            root.putString(TAG_SELECTED_GRAPH_ID, selectedGraphId);
        }
        return root;
    }

    // Get the linker targets from data
    static LinkerTargets linkerTargetsFromData(@Nullable CompoundTag linkerData) {
        CompoundTag root = linkerData == null ? new CompoundTag() : linkerData;
        UUID linkerId = root.hasUUID(TAG_LINKER_ID) ? root.getUUID(TAG_LINKER_ID) : null;
        return new LinkerTargets(
                true,
                linkerId,
                ContraptionNetworkLinkerData.LinkMode.byId(root.getString(TAG_EDIT_MODE)),
                ContraptionNetworkLinkerData.TargetMode.byId(root.getString(TAG_TARGET_MODE)),
                ContraptionNetworkLinkerData.readTargets(root));
    }

    // Get the controller data for storage
    static CompoundTag controllerDataForStorage(@Nullable CompoundTag controllerData) {
        CompoundTag copy = controllerData == null ? new CompoundTag() : controllerData.copy();
        copy.remove("AdvancedDraftGraph");
        copy.remove("AdvancedActiveGraph");
        copy.remove("AdvancedGraphVersions");
        copy.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID);
        copy.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION);
        copy.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH);
        copy.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
        return copy;
    }

    // Get the controller graph history
    static CompoundTag controllerGraphHistory(@Nullable CompoundTag controllerData) {
        CompoundTag history = new CompoundTag();
        if (controllerData != null && controllerData.contains("AdvancedGraphVersions", Tag.TAG_LIST)) {
            history.put("Versions",
                    controllerData.getList("AdvancedGraphVersions", Tag.TAG_COMPOUND).copy());
        }
        return history;
    }

    // Load the linker targets with presence
    private static LinkerTargets loadLinkerTargetsWithPresence(Connection connection, String manifestId)
            throws SQLException {
        Map<String, TargetBuilder> builders = new LinkedHashMap<>();
        boolean found = false;
        UUID linkerId = null;
        ContraptionNetworkLinkerData.LinkMode editMode = ContraptionNetworkLinkerData.LinkMode.OUTPUT;
        ContraptionNetworkLinkerData.TargetMode targetMode = ContraptionNetworkLinkerData.TargetMode.AUTO;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT l.id AS linker_row_id, l.linker_uuid, l.edit_mode, l.target_mode,
                       lb.id AS block_row_id, lb.node_id, lb.block_id, lb.sublevel_id,
                       lb.rel_x, lb.rel_y, lb.rel_z, lb.mode, lb.scope, lb.face,
                       lb.label, lb.face_label, lb.face_signal_key
                FROM linkers l
                LEFT JOIN linked_blocks lb ON lb.owner_type = 'linker' AND lb.owner_id = l.id
                WHERE l.id = ?
                ORDER BY lb.sublevel_id, lb.rel_x, lb.rel_y, lb.rel_z, lb.mode, lb.scope, lb.node_id, lb.face
                """)) {
            statement.setString(1, manifestId);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    found = true;
                    linkerId = parseUuid(res.getString("linker_uuid"));
                    editMode = ContraptionNetworkLinkerData.LinkMode.byId(res.getString("edit_mode"));
                    targetMode = ContraptionNetworkLinkerData.TargetMode.byId(res.getString("target_mode"));
                    if (res.getString("block_row_id") == null) {
                        continue;
                    }
                    addTargetRow(builders, res);
                }
            }
        }
        return new LinkerTargets(found, linkerId, editMode, targetMode, buildTargets(builders));
    }

    // Load the linker targets
    private static List<ContraptionNetworkLinkerData.LinkedTarget> loadLinkerTargets(Connection connection,
                                                                                     String manifestId)
            throws SQLException {
        Map<String, TargetBuilder> builders = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT node_id, block_id, sublevel_id, rel_x, rel_y, rel_z, mode, scope, face,
                       label, face_label, face_signal_key
                FROM linked_blocks
                WHERE owner_type = 'linker' AND owner_id = ?
                ORDER BY sublevel_id, rel_x, rel_y, rel_z, mode, scope, node_id, face
                """)) {
            statement.setString(1, manifestId);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    addTargetRow(builders, res);
                }
            }
        }
        return buildTargets(builders);
    }

    // Add the target row
    private static void addTargetRow(Map<String, TargetBuilder> builders, ResultSet res) throws SQLException {
        String subLevel = nullToEmpty(res.getString("sublevel_id"));
        BlockPos pos = new BlockPos(res.getInt("rel_x"), res.getInt("rel_y"), res.getInt("rel_z"));
        String mode = nullToEmpty(res.getString("mode"));
        String scope = nullToEmpty(res.getString("scope"));
        String blockId = nullToEmpty(res.getString("block_id"));
        String nodeId = nullToEmpty(res.getString("node_id"));
        String label = nullToEmpty(res.getString("label"));
        String key = nodeId.isBlank()
                ? subLevel + "|" + pos.asLong() + "|" + mode + "|" + scope + "|" + blockId
                : nodeId;
        TargetBuilder builder = builders.computeIfAbsent(key, ignored -> new TargetBuilder(
                pos,
                parseUuid(subLevel),
                blockId,
                label,
                ContraptionNetworkLinkerData.LinkMode.byId(mode),
                ContraptionNetworkLinkerData.TargetScope.byId(scope)));
        Direction dir = Direction.byName(nullToEmpty(res.getString("face")));
        if (dir != null) {
            builder.faces.add(new ContraptionNetworkLinkerData.LinkedFace(
                    dir,
                    nullToEmpty(res.getString("face_label")),
                    nullToEmpty(res.getString("face_signal_key"))));
        }
    }

    // Build the targets
    private static List<ContraptionNetworkLinkerData.LinkedTarget> buildTargets(Map<String, TargetBuilder> builders) {
        List<ContraptionNetworkLinkerData.LinkedTarget> targets = new ArrayList<>();
        for (TargetBuilder builder : builders.values()) {
            builder.faces.sort(Comparator.comparingInt(face -> face.face().ordinal()));
            if (builder.scope.usesFaces() && builder.faces.isEmpty()) {
                continue;
            }
            targets.add(new ContraptionNetworkLinkerData.LinkedTarget(
                    builder.pos,
                    builder.subLevelId,
                    builder.blockId,
                    builder.label,
                    builder.mode,
                    builder.scope,
                    builder.faces));
        }
        targets.sort(Comparator
                .comparing((ContraptionNetworkLinkerData.LinkedTarget target) ->
                        target.subLevelId() == null ? "" : target.subLevelId().toString())
                .thenComparing(target -> target.blockPos().asLong())
                .thenComparing(target -> target.mode().id())
                .thenComparing(target -> target.label().toLowerCase(java.util.Locale.ROOT)));
        return targets;
    }

    // Update the graph
    private static void upsertGraph(Connection connection, String ownerType, String ownerId, String role,
                                    String graphName, @Nullable CompoundTag graphTag, int revision, String updatedAt)
            throws SQLException {
        AdvancedGraphImageAssets.StoredGraph storedGraph =
                AdvancedGraphImageAssets.separate(graphTag);
        CompoundTag graph = storedGraph.graph();
        if (graph.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM graphs WHERE owner_type = ? AND owner_id = ? AND graph_role = ?")) {
                statement.setString(1, ownerType);
                statement.setString(2, ownerId);
                statement.setString(3, role);
                statement.executeUpdate();
            }
            deleteGraphImages(connection, ownerType, ownerId, role);
            return;
        }
        byte[] graphBytes = nbtBytes(graph);
        byte[][] hashValues = new byte[storedGraph.assets().size() + 1][];
        hashValues[0] = graphBytes;
        for (int idx = 0; idx < storedGraph.assets().size(); idx++) {
            AdvancedGraphImageAssets.ImageAsset asset = storedGraph.assets().get(idx);
            hashValues[idx + 1] = (asset.id() + "\0" + asset.mimeType() + "\0" + asset.base64())
                    .getBytes(StandardCharsets.UTF_8);
        }
        String hash = sha256("graph:" + ownerType + ":" + ownerId + ":" + role, hashValues);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO graphs (
                    id, owner_type, owner_id, graph_role, graph_name, revision,
                    graph_json, graph_nbt, content_hash, needs_compilation, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    graph_role = excluded.graph_role,
                    graph_name = excluded.graph_name,
                    revision = excluded.revision,
                    graph_json = excluded.graph_json,
                    graph_nbt = excluded.graph_nbt,
                    content_hash = excluded.content_hash,
                    needs_compilation = excluded.needs_compilation,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, ownerType + ":" + ownerId + ":" + role);
            statement.setString(2, ownerType);
            statement.setString(3, ownerId);
            statement.setString(4, role);
            statement.setString(5, graphName == null ? "" : graphName);
            statement.setInt(6, revision);
            statement.setString(7, "");
            setNullableBytes(statement, 8, graphBytes);
            statement.setString(9, hash);
            statement.setInt(10, 0);
            statement.setString(11, updatedAt);
            statement.executeUpdate();
        }
        replaceGraphImages(connection, ownerType, ownerId, role, storedGraph.assets(), updatedAt);
    }

    // Load the graph
    private static CompoundTag loadGraph(Connection connection, String ownerType, String ownerId, String role)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT graph_nbt
                FROM graphs
                WHERE owner_type = ? AND owner_id = ? AND graph_role = ?
                """)) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, role);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    CompoundTag graph = compoundFromBytes(res.getBytes("graph_nbt"));
                    return AdvancedGraphImageAssets.hydrateCopy(
                            graph, loadGraphImages(connection, ownerType, ownerId, role));
                }
            }
        }
        return new CompoundTag();
    }

    // Load the stored graphs
    private static ListTag loadStoredGraphs(Connection connection, String ownerType, String ownerId)
            throws SQLException {
        ListTag list = new ListTag();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT graph_role, graph_name, graph_nbt
                FROM graphs
                WHERE owner_type = ? AND owner_id = ?
                ORDER BY graph_role
                """)) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    String role = res.getString("graph_role");
                    CompoundTag graph = AdvancedGraphImageAssets.hydrateCopy(
                            compoundFromBytes(res.getBytes("graph_nbt")),
                            loadGraphImages(connection, ownerType, ownerId, role));
                    if (graph.isEmpty()) {
                        continue;
                    }
                    CompoundTag entry = new CompoundTag();
                    entry.putString(TAG_GRAPH_ID, role);
                    String graphName = res.getString("graph_name");
                    if (graphName != null && !graphName.isBlank()) {
                        entry.putString(TAG_GRAPH_NAME, graphName);
                    }
                    entry.put(TAG_GRAPH, graph);
                    list.add(entry);
                }
            }
        }
        return list;
    }

    // Update the stored graphs
    private static void upsertStoredGraphs(Connection connection, String ownerType, String ownerId,
                                           @Nullable CompoundTag linkerData, int revision, String updatedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM graphs WHERE owner_type = ? AND owner_id = ?")) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.executeUpdate();
        }
        deleteGraphImages(connection, ownerType, ownerId, null);
        if (linkerData == null || !linkerData.contains(TAG_STORED_GRAPHS, Tag.TAG_LIST)) {
            return;
        }
        ListTag graphs = linkerData.getList(TAG_STORED_GRAPHS, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < graphs.size(); idx++) {
            CompoundTag entry = graphs.getCompound(idx);
            CompoundTag graph = entry.getCompound(TAG_GRAPH);
            if (graph.isEmpty()) {
                continue;
            }
            String role = entry.getString(TAG_GRAPH_ID);
            if (role.isBlank()) {
                role = "stored_" + idx;
            }
            upsertGraph(connection, ownerType, ownerId, role, entry.getString(TAG_GRAPH_NAME), graph, revision, updatedAt);
        }
    }

    // Replace the graph images
    private static void replaceGraphImages(
            Connection connection, String ownerType, String ownerId, String role,
            List<AdvancedGraphImageAssets.ImageAsset> assets, String updatedAt) throws SQLException {
        deleteGraphImages(connection, ownerType, ownerId, role);
        if (assets == null || assets.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO graph_images (
                    owner_type, owner_id, graph_role, asset_id,
                    media_type, base64_data, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (AdvancedGraphImageAssets.ImageAsset asset : assets) {
                statement.setString(1, ownerType);
                statement.setString(2, ownerId);
                statement.setString(3, role);
                statement.setString(4, asset.id());
                statement.setString(5, asset.mimeType());
                statement.setString(6, asset.base64());
                statement.setString(7, updatedAt);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // Load the graph images
    private static List<AdvancedGraphImageAssets.ImageAsset> loadGraphImages(
            Connection connection, String ownerType, String ownerId, String role) throws SQLException {
        List<AdvancedGraphImageAssets.ImageAsset> assets = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT asset_id, media_type, base64_data
                FROM graph_images
                WHERE owner_type = ? AND owner_id = ? AND graph_role = ?
                ORDER BY asset_id
                """)) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.setString(3, role);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    assets.add(new AdvancedGraphImageAssets.ImageAsset(
                            res.getString("asset_id"),
                            res.getString("media_type"),
                            res.getString("base64_data")));
                }
            }
        }
        return assets;
    }

    // Delete the graph images
    private static void deleteGraphImages(
            Connection connection, String ownerType, String ownerId, @Nullable String role) throws SQLException {
        String sql = role == null
                ? "DELETE FROM graph_images WHERE owner_type = ? AND owner_id = ?"
                : "DELETE FROM graph_images WHERE owner_type = ? AND owner_id = ? AND graph_role = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            if (role != null) {
                statement.setString(3, role);
            }
            statement.executeUpdate();
        }
    }

    // Replace the linker linked blocks
    private static void replaceLinkerLinkedBlocks(Connection connection, String linkerId, String dimension,
                                                  @Nullable CompoundTag linkerData) throws SQLException {
        deleteLinkedBlocks(connection, "linker", linkerId);
        if (linkerData == null) {
            return;
        }
        List<ContraptionNetworkLinkerData.LinkedTarget> targets = ContraptionNetworkLinkerData.readTargets(linkerData);
        String now = Instant.now().toString();
        for (ContraptionNetworkLinkerData.LinkedTarget target : targets) {
            String nodeId = ContraptionNetworkLinkerData.nodeIdForTarget(target);
            if (target.scope().usesFaces() && !target.faces().isEmpty()) {
                for (ContraptionNetworkLinkerData.LinkedFace face : target.faces()) {
                    insertLinkedBlock(connection, new LinkedBlockRow(
                            sha256("linker:" + linkerId + ":" + nodeId + ":" + face.face().getSerializedName()),
                            "linker", linkerId, linkerId, nodeId, face.face().getSerializedName(),
                            "face_" + target.mode().id(), target.scope().id(), target.mode().id(),
                            target.blockId(), dimension, target.subLevelId(), target.blockPos(),
                            target.subLevelId() == null ? target.blockPos() : null,
                            face.face().getSerializedName(), target.label(), face.label(), face.signalKey(), now, now));
                }
                continue;
            }
            insertLinkedBlock(connection, new LinkedBlockRow(
                    sha256("linker:" + linkerId + ":" + nodeId),
                    "linker", linkerId, linkerId, nodeId, "",
                    "block_" + target.mode().id(), target.scope().id(), target.mode().id(),
                    target.blockId(), dimension, target.subLevelId(), target.blockPos(),
                    target.subLevelId() == null ? target.blockPos() : null,
                    "", target.label(), "", "", now, now));
        }
    }

    // Replace the ctrl linked blocks
    private static void replaceCtrlLinkedBlocks(Connection connection, String controllerId, String dimension,
                                                      @Nullable CompoundTag controllerData) throws SQLException {
        deleteLinkedBlocks(connection, "controller", controllerId);
        if (controllerData == null) {
            return;
        }
        String now = Instant.now().toString();
        collectCtrlTargets(connection, controllerId, dimension, "DirectTargets", "output", controllerData, now);
        collectCtrlTargets(connection, controllerId, dimension, "InputTargets", "input", controllerData, now);
        collectCustomCtrlTargets(connection, controllerId, dimension, controllerData, now);
    }

    // Collect the ctrl targets
    private static void collectCtrlTargets(Connection connection, String controllerId, String dimension,
                                                 String section, String mode, CompoundTag controllerData,
                                                 String now) throws SQLException {
        if (!controllerData.contains(section, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag targets = controllerData.getCompound(section);
        for (String portId : targets.getAllKeys()) {
            CompoundTag targetTag = targets.getCompound(portId);
            ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(targetTag);
            if (reference == null || reference.blockPos() == null) {
                continue;
            }
            String nodeId = reference.targetId();
            String blockId = reference.targetTypeId().isBlank() ? "unknown" : reference.targetTypeId();
            insertLinkedBlock(connection, new LinkedBlockRow(
                    sha256("controller:" + controllerId + ":" + section + ":" + portId + ":" + nodeId),
                    "controller", controllerId, null, nodeId, portId,
                    "block_" + mode, reference.subLevelId() == null ? "world" : "sublevel", mode,
                    blockId, dimension, reference.subLevelId(), reference.blockPos(),
                    reference.subLevelId() == null ? reference.blockPos() : null,
                    "", reference.label(), "", "", now, now));
        }
    }

    // Collect the custom ctrl targets
    private static void collectCustomCtrlTargets(Connection connection, String controllerId, String dimension,
                                                       CompoundTag controllerData, String now) throws SQLException {
        if (!controllerData.contains("CustomKeyEntries", Tag.TAG_LIST)) {
            return;
        }
        ListTag entries = controllerData.getList("CustomKeyEntries", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < entries.size(); idx++) {
            CompoundTag entry = entries.getCompound(idx);
            String entryId = entry.getString("Id");
            collectCustomCtrlTarget(connection, controllerId, dimension, entryId,
                    "DirectTarget", "output", entry, now);
            collectCustomCtrlTarget(connection, controllerId, dimension, entryId,
                    "InputTarget", "input", entry, now);
        }
    }

    // Collect the custom ctrl target
    private static void collectCustomCtrlTarget(Connection connection, String controllerId, String dimension,
                                                      String entryId, String targetKey, String mode,
                                                      CompoundTag entry, String now) throws SQLException {
        if (!entry.contains(targetKey, Tag.TAG_COMPOUND)) {
            return;
        }
        ControllerDirectTargetReference reference = ControllerDirectTargetReference.fromTag(entry.getCompound(targetKey));
        if (reference == null || reference.blockPos() == null) {
            return;
        }
        String nodeId = reference.targetId();
        String blockId = reference.targetTypeId().isBlank() ? "unknown" : reference.targetTypeId();
        String portId = "custom:" + entryId + ":" + targetKey;
        insertLinkedBlock(connection, new LinkedBlockRow(
                sha256("controller:" + controllerId + ":" + portId + ":" + nodeId),
                "controller", controllerId, null, nodeId, portId,
                "block_" + mode, reference.subLevelId() == null ? "world" : "sublevel", mode,
                blockId, dimension, reference.subLevelId(), reference.blockPos(),
                reference.subLevelId() == null ? reference.blockPos() : null,
                "", reference.label(), "", "", now, now));
    }

    // Delete the linked blocks
    private static void deleteLinkedBlocks(Connection connection, String ownerType, String ownerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM linked_blocks WHERE owner_type = ? AND owner_id = ?")) {
            statement.setString(1, ownerType);
            statement.setString(2, ownerId);
            statement.executeUpdate();
        }
    }

    // Insert the linked block
    private static void insertLinkedBlock(Connection connection, LinkedBlockRow row) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO linked_blocks (
                    id, owner_type, owner_id, linker_id, node_id, port_id,
                    link_kind, scope, mode, block_id, dimension, sublevel_id,
                    rel_x, rel_y, rel_z, world_x, world_y, world_z,
                    face, label, face_label, face_signal_key, target_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    owner_type = excluded.owner_type,
                    owner_id = excluded.owner_id,
                    linker_id = excluded.linker_id,
                    node_id = excluded.node_id,
                    port_id = excluded.port_id,
                    link_kind = excluded.link_kind,
                    scope = excluded.scope,
                    mode = excluded.mode,
                    block_id = excluded.block_id,
                    dimension = excluded.dimension,
                    sublevel_id = excluded.sublevel_id,
                    rel_x = excluded.rel_x,
                    rel_y = excluded.rel_y,
                    rel_z = excluded.rel_z,
                    world_x = excluded.world_x,
                    world_y = excluded.world_y,
                    world_z = excluded.world_z,
                    face = excluded.face,
                    label = excluded.label,
                    face_label = excluded.face_label,
                    face_signal_key = excluded.face_signal_key,
                    target_json = excluded.target_json,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, row.id);
            statement.setString(2, row.ownerType);
            statement.setString(3, row.ownerId);
            setNullableString(statement, 4, row.linkerId);
            statement.setString(5, row.nodeId);
            statement.setString(6, row.portId);
            statement.setString(7, row.linkKind);
            statement.setString(8, row.scope);
            statement.setString(9, row.mode);
            statement.setString(10, row.blockId);
            statement.setString(11, row.dimension);
            statement.setString(12, row.subLevelId == null ? "" : row.subLevelId.toString());
            statement.setInt(13, row.relativePos.getX());
            statement.setInt(14, row.relativePos.getY());
            statement.setInt(15, row.relativePos.getZ());
            setNullableInt(statement, 16, row.worldPos == null ? null : row.worldPos.getX());
            setNullableInt(statement, 17, row.worldPos == null ? null : row.worldPos.getY());
            setNullableInt(statement, 18, row.worldPos == null ? null : row.worldPos.getZ());
            statement.setString(19, row.face);
            statement.setString(20, row.label);
            statement.setString(21, row.faceLabel);
            statement.setString(22, row.faceSignalKey);
            statement.setString(23, "{}");
            statement.setString(24, row.createdAt);
            statement.setString(25, row.updatedAt);
            statement.executeUpdate();
        }
    }

    // Attach the linker to ctrl
    private static void attachLinkerToCtrl(Connection connection, String linkerId, String controllerId,
                                                 String dimension) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE linkers SET controller_id = ? WHERE id = ?")) {
            statement.setString(1, controllerId);
            statement.setString(2, linkerId);
            statement.executeUpdate();
        }
        if (dimension == null || dimension.isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE linked_blocks SET dimension = ? WHERE owner_type = 'linker' AND owner_id = ? AND dimension = ''")) {
            statement.setString(1, dimension);
            statement.setString(2, linkerId);
            statement.executeUpdate();
        }
    }

    // Get the existing controller id
    private static @Nullable String existingControllerId(Connection connection, String linkerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT controller_id FROM linkers WHERE id = ?")) {
            statement.setString(1, linkerId);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return res.getString(1);
                }
            }
        }
        return null;
    }

    // Get the existing linker dimension
    private static String existingLinkerDimension(Connection connection, String linkerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT dimension
                FROM linked_blocks
                WHERE owner_type = 'linker' AND owner_id = ? AND dimension <> ''
                LIMIT 1
                """)) {
            statement.setString(1, linkerId);
            try (ResultSet res = statement.executeQuery()) {
                if (res.next()) {
                    return nullToEmpty(res.getString(1));
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.dimension
                FROM linkers l
                JOIN controllers c ON c.id = l.controller_id
                WHERE l.id = ?
                """)) {
            statement.setString(1, linkerId);
            try (ResultSet res = statement.executeQuery()) {
                return res.next() ? nullToEmpty(res.getString(1)) : "";
            }
        }
    }

    // Load the controller owner
    private static @Nullable ControllerOwner loadControllerOwner(Connection connection, String controllerId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, kind, revision, block_x, block_y, block_z, sublevel_id
                FROM controllers
                WHERE id = ?
                """)) {
            statement.setString(1, controllerId);
            try (ResultSet res = statement.executeQuery()) {
                if (!res.next()) {
                    return null;
                }
                return new ControllerOwner(
                        res.getString("id"),
                        res.getString("kind"),
                        res.getInt("revision"),
                        new BlockPos(res.getInt("block_x"), res.getInt("block_y"), res.getInt("block_z")),
                        parseUuid(res.getString("sublevel_id")));
            }
        } catch (SQLException err) {
            Create.LOGGER.warn("Failed to load controller owner metadata for {}", controllerId, err);
            return null;
        }
    }

    // Get the section bytes
    private static @Nullable byte[] sectionBytes(@Nullable CompoundTag root, String key) {
        if (root == null || !root.contains(key)) {
            return null;
        }
        Tag section = root.get(key);
        if (section == null) {
            return null;
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("value", section.copy());
        return nbtBytes(wrapper);
    }

    // Put the section
    private static void putSection(CompoundTag root, String key, byte @Nullable [] bytes) {
        Tag section = sectionFromBytes(bytes);
        if (section != null) {
            root.put(key, section);
        }
    }

    // Get the section from bytes
    private static @Nullable Tag sectionFromBytes(byte @Nullable [] bytes) {
        CompoundTag wrapper = compoundFromBytes(bytes);
        Tag val = wrapper.get("value");
        return val == null ? null : val.copy();
    }

    // Get the snapshot from JSON
    private static @Nullable ControllerManifestStore.ManifestSnapshot snapshotFromJson(String json, boolean linker) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String id = linker ? string(root, "linkerManifestId") : string(root, "controllerManifestId");
            if (id.isBlank()) {
                return null;
            }
            int revision = root.has("revision") ? root.get("revision").getAsInt() : 0;
            int storageVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt()
                    : ControllerManifestStore.STORAGE_VERSION;
            String hash = string(root, "contentHash");
            String kind = string(root, "kind");
            return new ControllerManifestStore.ManifestSnapshot(
                    id,
                    revision,
                    hash,
                    storageVersion,
                    kind,
                    compound(root, "controllerData"),
                    compound(root, "draftGraph"),
                    compound(root, "activeGraph"),
                    compound(root, "linkerData"));
        } catch (Exception err) {
            Create.LOGGER.warn("Failed to decode legacy SQLite manifest JSON", err);
            return null;
        }
    }

    // Get the compound
    private static CompoundTag compound(JsonObject root, String key) throws CommandSyntaxException {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return new CompoundTag();
        }
        JsonElement elm = root.get(key);
        if (elm.isJsonPrimitive()) {
            String snbt = elm.getAsString();
            if (snbt == null || snbt.isBlank()) {
                return new CompoundTag();
            }
            return TagParser.parseTag(snbt);
        }
        try {
            Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, elm);
            if (tag instanceof CompoundTag compoundTag) {
                return compoundTag;
            }
        } catch (RuntimeException err) {
            Create.LOGGER.warn("Failed to convert legacy SQLite manifest field {} from JSON to NBT", key, err);
        }
        return new CompoundTag();
    }

    // Get the NBT bytes
    private static @Nullable byte[] nbtBytes(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            return output.toByteArray();
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to encode SQLite NBT blob", err);
            return null;
        }
    }

    // Get the compound from bytes
    private static CompoundTag compoundFromBytes(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new CompoundTag();
        }
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to decode SQLite NBT blob", err);
            return new CompoundTag();
        }
    }

    // Get the inserted linker manifest id
    private static String insertedLinkerManifestId(@Nullable CompoundTag controllerData) {
        if (controllerData == null || !controllerData.contains("LinkerSlot", Tag.TAG_COMPOUND)) {
            return "";
        }
        String res = findStringRecursive(controllerData.getCompound("LinkerSlot"),
                ControllerManifestStore.TAG_LINKER_MANIFEST_ID);
        return res == null ? "" : res;
    }

    // Find the string recursive
    private static @Nullable String findStringRecursive(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_STRING)) {
            return tag.getString(key);
        }
        for (String childKey : tag.getAllKeys()) {
            if (tag.contains(childKey, Tag.TAG_COMPOUND)) {
                String nested = findStringRecursive(tag.getCompound(childKey), key);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    // Get the mode or default
    private static String modeOrDefault(String val, String fallback) {
        return val == null || val.isBlank() ? fallback : val;
    }

    // Convert null text to an empty string
    private static String nullToEmpty(@Nullable String val) {
        return val == null ? "" : val;
    }

    // Get the string
    private static String string(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : "";
    }

    // Parse the UUID
    private static @Nullable UUID parseUuid(@Nullable String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    // Set the nullable string
    private static void setNullableString(PreparedStatement statement, int idx, @Nullable String val)
            throws SQLException {
        if (val == null) {
            statement.setNull(idx, Types.VARCHAR);
        } else {
            statement.setString(idx, val);
        }
    }

    // Set the nullable bytes
    private static void setNullableBytes(PreparedStatement statement, int idx, byte @Nullable [] val)
            throws SQLException {
        if (val == null || val.length == 0) {
            statement.setNull(idx, Types.BLOB);
        } else {
            statement.setBytes(idx, val);
        }
    }

    // Set the nullable int
    private static void setNullableInt(PreparedStatement statement, int idx, @Nullable Integer val)
            throws SQLException {
        if (val == null) {
            statement.setNull(idx, Types.INTEGER);
        } else {
            statement.setInt(idx, val);
        }
    }

    // Get the database path
    private static @Nullable Path databasePath(@Nullable Level level) {
        MinecraftServer server = currentServer(level);
        if (server == null) {
            return null;
        }
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATABASE_NAME);
    }

    // Get the current server
    private static @Nullable MinecraftServer currentServer(@Nullable Level level) {
        if (level != null && level.getServer() != null) {
            return level.getServer();
        }
        try {
            return ServerLifecycleHooks.getCurrentServer();
        } catch (RuntimeException err) {
            return null;
        }
    }

    // Calculate the SHA-256 text hash
    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException err) {
            return Integer.toHexString(Objects.hashCode(text));
        }
    }

    // Calculate the SHA-256 data hash
    private static String sha256(String text, byte @Nullable []... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            for (byte[] val : values) {
                if (val != null) {
                    digest.update(val);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException err) {
            return Integer.toHexString(Objects.hash(text, values == null ? 0 : values.length));
        }
    }

    // Get the hex
    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte val : bytes) {
            builder.append(String.format("%02x", val));
        }
        return builder.toString();
    }

    // Handle the target builder
    private static final class TargetBuilder {
        // Target builder position
        private final BlockPos pos;
        // Sub-level id
        private final @Nullable UUID subLevelId;
        // Block id
        private final String blockId;
        // Display label
        private final String label;
        // Target builder mode
        private final ContraptionNetworkLinkerData.LinkMode mode;
        // Scope
        private final ContraptionNetworkLinkerData.TargetScope scope;
        // Tracked faces
        private final List<ContraptionNetworkLinkerData.LinkedFace> faces = new ArrayList<>();

        // Initialize the target builder
        private TargetBuilder(BlockPos pos,
                              @Nullable UUID subLevelId,
                              String blockId,
                              String label,
                              ContraptionNetworkLinkerData.LinkMode mode,
                              ContraptionNetworkLinkerData.TargetScope scope) {
            this.pos = pos == null ? BlockPos.ZERO : pos.immutable();
            this.subLevelId = subLevelId;
            this.blockId = blockId == null ? "" : blockId;
            this.label = label == null ? "" : label;
            this.mode = mode == null ? ContraptionNetworkLinkerData.LinkMode.OUTPUT : mode;
            this.scope = scope == null ? ContraptionNetworkLinkerData.TargetScope.FACE : scope;
        }
    }

    // Store the legacy controller row
    private record LegacyControllerRow(
            String id,
            String kind,
            int revision,
            String dimension,
            String manifestJson) {
    }

    // Store the legacy linker row
    private record LegacyLinkerRow(
            String id,
            int revision,
            String manifestJson) {
    }

    // Store the linked block row
    private record LinkedBlockRow(
            String id,
            String ownerType,
            String ownerId,
            @Nullable String linkerId,
            String nodeId,
            String portId,
            String linkKind,
            String scope,
            String mode,
            String blockId,
            String dimension,
            @Nullable UUID subLevelId,
            BlockPos relativePos,
            @Nullable BlockPos worldPos,
            String face,
            String label,
            String faceLabel,
            String faceSignalKey,
            String createdAt,
            String updatedAt) {
    }
}
