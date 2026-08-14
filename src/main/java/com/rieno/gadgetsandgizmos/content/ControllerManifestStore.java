package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphImageAssets;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

// Store controller manifests outside block NBT so large schedules stay cheap to load and sync
public final class ControllerManifestStore {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int STORAGE_VERSION = 1;
    public static final String TAG_CONTROLLER_MANIFEST_ID = "ControllerManifestId";
    public static final String TAG_CONTROLLER_MANIFEST_REVISION = "ControllerManifestRevision";
    public static final String TAG_CONTROLLER_MANIFEST_HASH = "ControllerManifestHash";
    public static final String TAG_CONTROLLER_MANIFEST_STORAGE_VERSION = "ControllerManifestStorageVersion";
    public static final String TAG_LINKER_MANIFEST_ID = "LinkerManifestId";
    public static final String TAG_LINKER_MANIFEST_REVISION = "LinkerManifestRevision";
    public static final String TAG_LINKER_MANIFEST_HASH = "LinkerManifestHash";
    public static final String TAG_LINKER_MANIFEST_STORAGE_VERSION = "LinkerManifestStorageVersion";

    private static final String ROOT_DIR = "advanced_controller_graphs";
    private static final String SHARED_GRAPHS_DIR = "shared_graphs";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller manifest store
    private ControllerManifestStore() {
    }

    // Store the manifest snapshot
    public record ManifestSnapshot(String id, int revision, String hash, int storageVersion, String kind,
                                   CompoundTag controllerData, CompoundTag draftGraph, CompoundTag activeGraph,
                                   CompoundTag linkerData) {
        // Get the controller data
        public CompoundTag controllerData() {
            return controllerData == null ? new CompoundTag() : controllerData.copy();
        }

        // Get the draft graph
        public CompoundTag draftGraph() {
            return draftGraph == null ? new CompoundTag() : draftGraph.copy();
        }

        // Get the active graph
        public CompoundTag activeGraph() {
            return activeGraph == null ? new CompoundTag() : activeGraph.copy();
        }

        // Get the linker data
        public CompoundTag linkerData() {
            return linkerData == null ? new CompoundTag() : linkerData.copy();
        }
    }

    // Store the shared graph entry
    public record SharedGraphEntry(String id, String name) {
        // Initialize the shared graph entry
        public SharedGraphEntry {
            id = id == null ? "" : id.trim();
            name = name == null || name.isBlank() ? id : name.trim();
        }
    }

    // Define the shared graph save mode values
    public enum SharedGraphSaveMode {
        REJECT,
        OVERWRITE,
        INCREMENT
    }

    // Define the shared graph save status values
    public enum SharedGraphSaveStatus {
        SAVED,
        EXISTS,
        FAILED
    }

    // Store shared graph save results
    public record SharedGraphSaveResult(SharedGraphSaveStatus status, String id,
                                        @Nullable ManifestSnapshot snapshot) {
        // Initialize the shared graph save result
        public SharedGraphSaveResult {
            status = status == null ? SharedGraphSaveStatus.FAILED : status;
            id = id == null ? "" : id;
        }

        // Check if the controller manifest was saved
        public boolean saved() {
            return status == SharedGraphSaveStatus.SAVED && snapshot != null;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this has controller metadata
    public static boolean hasControllerMetadata(CompoundTag tag) {
        return tag != null && tag.contains(TAG_CONTROLLER_MANIFEST_ID, Tag.TAG_STRING);
    }

    // Check if this has linker metadata
    public static boolean hasLinkerMetadata(CompoundTag tag) {
        return tag != null && tag.contains(TAG_LINKER_MANIFEST_ID, Tag.TAG_STRING);
    }

    // Load the controller from metadata
    public static @Nullable ManifestSnapshot loadControllerFromMetadata(CompoundTag tag) {
        return loadControllerFromMetadata(tag, null);
    }

    // Load the controller from metadata
    public static @Nullable ManifestSnapshot loadControllerFromMetadata(CompoundTag tag, @Nullable Level level) {
        if (!hasControllerMetadata(tag)) {
            return null;
        }
        return loadController(tag.getString(TAG_CONTROLLER_MANIFEST_ID), level);
    }

    // Load the linker from metadata
    public static @Nullable ManifestSnapshot loadLinkerFromMetadata(CompoundTag tag) {
        if (!hasLinkerMetadata(tag)) {
            return null;
        }
        return loadLinker(tag.getString(TAG_LINKER_MANIFEST_ID));
    }

    // Load the controller
    public static @Nullable ManifestSnapshot loadController(String manifestId) {
        return loadController(manifestId, null);
    }

    // Load the controller
    public static @Nullable ManifestSnapshot loadController(String manifestId, @Nullable Level level) {
        ManifestSnapshot sqliteSnapshot = ControllerSqliteStore.loadController(manifestId, level);
        if (sqliteSnapshot != null) {
            return sqliteSnapshot;
        }
        ManifestSnapshot legacySnapshot = readManifest(controllerPath(manifestId, level), legacyControllerPath(manifestId), false);
        if (legacySnapshot != null) {
            ControllerSqliteStore.importController(legacySnapshot, level);
        }
        return legacySnapshot;
    }

    // Delete the controller
    public static boolean deleteController(String manifestId, @Nullable Level level) {
        if (manifestId == null || manifestId.isBlank()) {
            return false;
        }
        return ControllerSqliteStore.deleteController(manifestId, level);
    }

    // Load the linker
    public static @Nullable ManifestSnapshot loadLinker(String manifestId) {
        ManifestSnapshot sqliteSnapshot = ControllerSqliteStore.loadLinker(manifestId);
        if (sqliteSnapshot != null) {
            return sqliteSnapshot;
        }
        ManifestSnapshot legacySnapshot = readManifest(linkerPath(manifestId), legacyLinkerPath(manifestId), true);
        if (legacySnapshot != null) {
            ControllerSqliteStore.importLinker(legacySnapshot);
        }
        return legacySnapshot;
    }

    // Save the controller
    public static @Nullable ManifestSnapshot saveController(String manifestId, String kind,
                                                            @Nullable Level level, BlockPos ownerPos,
                                                            @Nullable UUID subLevelId, CompoundTag controllerData,
                                                            @Nullable CompoundTag draftGraph,
                                                            @Nullable CompoundTag activeGraph,
                                                            int currentRevision) {
        String id = stableManifestId(manifestId);
        String safeKind = kind == null || kind.isBlank() ? "base_controller" : kind.trim();
        int nextRevision = Math.max(0, currentRevision) + 1;
        return ControllerSqliteStore.saveController(id, nextRevision, safeKind, level, ownerPos,
                subLevelId, controllerData, draftGraph, activeGraph, insertedLinkerManifestId(controllerData));
    }

    // Save the linker
    public static @Nullable ManifestSnapshot saveLinker(String manifestId, CompoundTag linkerData,
                                                        int currentRevision) {
        String id = stableManifestId(manifestId);
        int nextRevision = Math.max(0, currentRevision) + 1;
        return ControllerSqliteStore.saveLinker(id, nextRevision, linkerData);
    }

    // Save the shared graph
    public static SharedGraphSaveResult saveSharedGraph(String requestedName, CompoundTag linkerData,
                                                         SharedGraphSaveMode saveMode) {
        return saveSharedGraph(sharedGraphsDir(), requestedName, linkerData, saveMode);
    }

    // Get the shared graphs directory
    public static Path sharedGraphsDirectory() {
        return sharedGraphsDir();
    }

    // Get the shared graph JSON
    public static String sharedGraphJson(String requestedName, AdvancedGraphDocument graph) {
        String displayName = requestedName == null || requestedName.isBlank()
                ? "Contraption Graph" : requestedName.trim();
        String id = safeSharedBaseName(displayName);
        AdvancedGraphDocument graphToShare = graph == null ? new AdvancedGraphDocument() : graph.copy();
        graphToShare.removeUnusedVariables();
        CompoundTag linkerData = ContraptionNetworkLinkerData.sharedRootWithGraph(
                displayName, graphToShare);
        JsonObject root = linkerRootJson(id, linkerData);
        root.addProperty("displayName", displayName);
        return GSON.toJson(root);
    }

    // Save the shared graph
    static synchronized SharedGraphSaveResult saveSharedGraph(Path dir, String requestedName,
                                                              CompoundTag linkerData,
                                                              SharedGraphSaveMode saveMode) {
        return saveSharedGraph(dir, requestedName, "", linkerData, saveMode);
    }

    // Save the shared graph
    private static SharedGraphSaveResult saveSharedGraph(Path dir, String requestedName,
                                                         String requestedDisplayName,
                                                         CompoundTag linkerData,
                                                         SharedGraphSaveMode saveMode) {
        Path targetDirectory = dir == null ? sharedGraphsDir() : dir;
        String baseName = safeSharedBaseName(requestedName);
        SharedGraphSaveMode mode = saveMode == null ? SharedGraphSaveMode.REJECT : saveMode;
        Path basePath = sharedGraphPath(targetDirectory, baseName);
        boolean baseExists = Files.exists(basePath);
        if (mode == SharedGraphSaveMode.REJECT && baseExists) {
            return new SharedGraphSaveResult(SharedGraphSaveStatus.EXISTS, baseName, null);
        }
        String id = mode == SharedGraphSaveMode.INCREMENT
                ? uniqueSharedGraphId(targetDirectory, baseName) : baseName;
        String displayName = requestedDisplayName == null || requestedDisplayName.isBlank()
                ? sharedDisplayName(requestedName, baseName, id) : requestedDisplayName.trim();
        JsonObject root = linkerRootJson(id, linkerData);
        root.addProperty("displayName", displayName);
        int revision = 1;
        if (mode == SharedGraphSaveMode.OVERWRITE && baseExists) {
            ManifestSnapshot existing = readManifest(basePath, true);
            if (existing != null) {
                revision = Math.max(1, existing.revision() + 1);
            }
        }
        ManifestSnapshot snapshot = writeManifest(sharedGraphPath(targetDirectory, id), root, id, revision,
                "contraption_network_linker",
                new CompoundTag(), new CompoundTag(), new CompoundTag(), linkerData);
        return snapshot == null
                ? new SharedGraphSaveResult(SharedGraphSaveStatus.FAILED, id, null)
                : new SharedGraphSaveResult(SharedGraphSaveStatus.SAVED, id, snapshot);
    }

    // Load the shared graph
    public static @Nullable ManifestSnapshot loadSharedGraph(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return null;
        }
        return readManifest(sharedGraphPath(manifestId), true);
    }

    // Read the shared graph JSON
    public static @Nullable String readSharedGraphJson(String manifestId) {
        if (manifestId == null || manifestId.isBlank()) {
            return null;
        }
        Path path = sharedGraphPath(manifestId);
        try {
            return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to read shared graph manifest {}", path, err);
            return null;
        }
    }

    // Import the shared graph
    public static SharedGraphSaveResult importSharedGraph(String requestedName, String json,
                                                          SharedGraphSaveMode saveMode) {
        return importSharedGraph(sharedGraphsDir(), requestedName, json, saveMode);
    }

    // Import the shared graph
    static synchronized SharedGraphSaveResult importSharedGraph(Path dir, String requestedName, String json,
                                                                SharedGraphSaveMode saveMode) {
        try {
            JsonElement parsed = JsonParser.parseString(json == null ? "" : json);
            if (!parsed.isJsonObject()) {
                return new SharedGraphSaveResult(SharedGraphSaveStatus.FAILED, "", null);
            }
            JsonObject root = parsed.getAsJsonObject();
            String kind = string(root, "kind");
            if (!kind.isBlank() && !"contraption_network_linker".equals(kind)) {
                return new SharedGraphSaveResult(SharedGraphSaveStatus.FAILED, "", null);
            }
            CompoundTag linkerData = compound(root, "linkerData");
            if (!linkerData.contains("StoredGraphs") && root.has("storedGraphs")
                    && !root.get("storedGraphs").isJsonNull()) {
                Tag storedGraphs = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, root.get("storedGraphs"));
                linkerData.put("StoredGraphs", storedGraphs);
            }
            hydrateSharedGraphImages(root, linkerData);
            String name = requestedName == null ? "" : requestedName.trim();
            if (name.isBlank()) name = string(root, "displayName");
            if (name.isBlank()) name = string(root, "linkerManifestId");
            return saveSharedGraph(dir, name, string(root, "displayName"), linkerData, saveMode);
        } catch (Exception err) {
            Create.LOGGER.warn("Failed to import shared graph manifest", err);
            return new SharedGraphSaveResult(SharedGraphSaveStatus.FAILED, "", null);
        }
    }

    // Load the shared graph
    static @Nullable ManifestSnapshot loadSharedGraph(Path path) {
        return path == null ? null : readManifest(path, true);
    }

    // Get the list shared graphs
    public static List<SharedGraphEntry> listSharedGraphs() {
        Path dir = sharedGraphsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<SharedGraphEntry> entries = new ArrayList<>();
        try (var paths = Files.list(dir)) {
            paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                            String id = string(root, "linkerManifestId");
                            if (id.isBlank()) {
                                id = stripJsonSuffix(path.getFileName().toString());
                            }
                            String name = string(root, "displayName");
                            entries.add(new SharedGraphEntry(id, name.isBlank() ? id : name));
                        } catch (Exception err) {
                            Create.LOGGER.warn("Failed to read shared graph manifest {}", path, err);
                        }
                    });
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to list shared graph manifests {}", dir, err);
        }
        entries.sort(Comparator.comparing(SharedGraphEntry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SharedGraphEntry::id));
        return List.copyOf(entries);
    }

    // Rewrite the linker manifests
    public static int rewriteLinkerManifests(Function<CompoundTag, Boolean> rewriter) {
        if (rewriter == null) {
            return 0;
        }
        importLegacyLinkerManifests();
        return ControllerSqliteStore.rewriteLinkers(rewriter);
    }

    // Load the linker manifest data
    public static List<CompoundTag> loadLinkerManifestData() {
        importLegacyLinkerManifests();
        return ControllerSqliteStore.loadLinkerData();
    }

    // Write the controller metadata
    public static void writeControllerMetadata(CompoundTag tag, ManifestSnapshot snapshot) {
        if (tag == null || snapshot == null) {
            return;
        }
        tag.putString(TAG_CONTROLLER_MANIFEST_ID, snapshot.id());
        tag.remove(TAG_CONTROLLER_MANIFEST_REVISION);
        tag.remove(TAG_CONTROLLER_MANIFEST_HASH);
        tag.remove(TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
    }

    // Write the linker metadata
    public static void writeLinkerMetadata(CompoundTag tag, ManifestSnapshot snapshot) {
        if (tag == null || snapshot == null) {
            return;
        }
        tag.putString(TAG_LINKER_MANIFEST_ID, snapshot.id());
        tag.remove(TAG_LINKER_MANIFEST_REVISION);
        tag.remove(TAG_LINKER_MANIFEST_HASH);
        tag.remove(TAG_LINKER_MANIFEST_STORAGE_VERSION);
    }

    // Get the controller manifest id
    public static String controllerManifestId(CompoundTag tag) {
        return tag != null && tag.contains(TAG_CONTROLLER_MANIFEST_ID, Tag.TAG_STRING)
                ? tag.getString(TAG_CONTROLLER_MANIFEST_ID) : "";
    }

    // Get the controller manifest revision
    public static int controllerManifestRevision(CompoundTag tag) {
        return tag != null && tag.contains(TAG_CONTROLLER_MANIFEST_REVISION, Tag.TAG_INT)
                ? tag.getInt(TAG_CONTROLLER_MANIFEST_REVISION) : 0;
    }

    // Get the controller manifest hash
    public static String controllerManifestHash(CompoundTag tag) {
        return tag != null && tag.contains(TAG_CONTROLLER_MANIFEST_HASH, Tag.TAG_STRING)
                ? tag.getString(TAG_CONTROLLER_MANIFEST_HASH) : "";
    }

    // Get the linker manifest id
    public static String linkerManifestId(CompoundTag tag) {
        return tag != null && tag.contains(TAG_LINKER_MANIFEST_ID, Tag.TAG_STRING)
                ? tag.getString(TAG_LINKER_MANIFEST_ID) : "";
    }

    // Get the linker manifest revision
    public static int linkerManifestRevision(CompoundTag tag) {
        return tag != null && tag.contains(TAG_LINKER_MANIFEST_REVISION, Tag.TAG_INT)
                ? tag.getInt(TAG_LINKER_MANIFEST_REVISION) : 0;
    }

    // Get the linker manifest hash
    public static String linkerManifestHash(CompoundTag tag) {
        return tag != null && tag.contains(TAG_LINKER_MANIFEST_HASH, Tag.TAG_STRING)
                ? tag.getString(TAG_LINKER_MANIFEST_HASH) : "";
    }

    // Read the manifest
    private static @Nullable ManifestSnapshot readManifest(Path path, boolean linker) {
        return readManifest(path, null, linker);
    }

    // Rewrite the linker manifests in the directory
    private static int rewriteLinkerManifestsIn(Path dir,
                                                Function<CompoundTag, Boolean> rewriter,
                                                Set<String> visitedIds) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int changed = 0;
        try (var paths = Files.list(dir)) {
            for (Path path : paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json")).toList()) {
                ManifestSnapshot snapshot = readManifest(path, true);
                if (snapshot == null || !visitedIds.add(snapshot.id())) {
                    continue;
                }
                CompoundTag linkerData = snapshot.linkerData();
                if (Boolean.TRUE.equals(rewriter.apply(linkerData))) {
                    saveLinker(snapshot.id(), linkerData, snapshot.revision());
                    changed++;
                }
            }
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to rewrite linker manifests in {}", dir, err);
        }
        return changed;
    }

    // Load the linker manifest data
    private static void loadLinkerManifestDataFrom(Path dir,
                                                   Set<String> visitedIds,
                                                   List<CompoundTag> output) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.list(dir)) {
            for (Path path : paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json")).toList()) {
                ManifestSnapshot snapshot = readManifest(path, true);
                if (snapshot != null && visitedIds.add(snapshot.id())) {
                    output.add(snapshot.linkerData());
                }
            }
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to read linker manifests in {}", dir, err);
        }
    }

    // Read the manifest
    private static @Nullable ManifestSnapshot readManifest(Path path, @Nullable Path fallbackPath, boolean linker) {
        try {
            Path selectedPath = path;
            if (!Files.exists(selectedPath) && fallbackPath != null
                    && !Objects.equals(selectedPath, fallbackPath) && Files.exists(fallbackPath)) {
                selectedPath = fallbackPath;
            }
            boolean loadedFromFallback = !Objects.equals(selectedPath, path);
            if (!Files.exists(selectedPath)) {
                Create.LOGGER.warn("Controller manifest is missing: {}", path);
                return null;
            }
            JsonObject root = JsonParser.parseString(Files.readString(selectedPath, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean legacyJson = manifestUsesLegacyNbt(root);
            String kind = string(root, "kind");
            String id = linker ? string(root, "linkerManifestId") : string(root, "controllerManifestId");
            if (id.isBlank()) {
                id = stripJsonSuffix(selectedPath.getFileName().toString());
            }
            int revision = root.has("revision") ? root.get("revision").getAsInt() : 0;
            int storageVersion = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : STORAGE_VERSION;
            String hash = string(root, "contentHash");
            CompoundTag controllerData = compound(root, "controllerData");
            CompoundTag draftGraph = compound(root, "draftGraph");
            CompoundTag activeGraph = compound(root, "activeGraph");
            CompoundTag linkerData = compound(root, "linkerData");
            hydrateSharedGraphImages(root, linkerData);
            ManifestSnapshot snapshot = new ManifestSnapshot(id, revision, hash, storageVersion, kind,
                    controllerData, draftGraph, activeGraph, linkerData);
            if ((loadedFromFallback || legacyJson) && isSharedGraphPath(selectedPath)) {
                migrateManifest(path, linker, root, snapshot);
            }
            return snapshot;
        } catch (Exception err) {
            Create.LOGGER.warn("Failed to read controller manifest {}", path, err);
            return null;
        }
    }

    // Write the manifest
    private static @Nullable ManifestSnapshot writeManifest(Path path, JsonObject root, String id, int revision,
                                                            String kind, @Nullable CompoundTag controllerData,
                                                            @Nullable CompoundTag draftGraph,
                                                            @Nullable CompoundTag activeGraph,
                                                            @Nullable CompoundTag linkerData) {
        try {
            Files.createDirectories(path.getParent());
            Path tmpRoot = tmpDirFor(path);
            Files.createDirectories(tmpRoot);
            root.addProperty("revision", revision);
            root.remove("contentHash");
            String hash = sha256(GSON.toJson(root));
            root.addProperty("contentHash", hash);
            String json = GSON.toJson(root);
            Path tmp = tmpRoot.resolve(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return new ManifestSnapshot(id, revision, hash, STORAGE_VERSION, kind,
                    controllerData, draftGraph, activeGraph, linkerData);
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to write controller manifest {}", path, err);
            return null;
        }
    }

    // Delete the manifest
    private static boolean deleteManifest(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException err) {
            Create.LOGGER.warn("Failed to delete controller manifest {}", path, err);
            return false;
        }
    }

    // Migrate the manifest
    private static void migrateManifest(Path destination, boolean linker, JsonObject sourceRoot,
                                        ManifestSnapshot snapshot) {
        if (destination == null || snapshot == null) {
            return;
        }
        JsonObject migrated = sourceRoot == null ? new JsonObject() : sourceRoot.deepCopy();
        migrated.addProperty("schemaVersion", STORAGE_VERSION);
        migrated.addProperty("kind", snapshot.kind() == null || snapshot.kind().isBlank()
                ? (linker ? "contraption_network_linker" : "base_controller") : snapshot.kind());
        if (linker) {
            migrated.addProperty("linkerManifestId", snapshot.id());
            CompoundTag linkerData = snapshot.linkerData();
            JsonObject linkerRoot = linkerRootJson(snapshot.id(), linkerData);
            for (String key : linkerRoot.keySet()) {
                migrated.add(key, linkerRoot.get(key));
            }
            writeManifest(destination, migrated, snapshot.id(), snapshot.revision(), "contraption_network_linker",
                    new CompoundTag(), new CompoundTag(), new CompoundTag(), linkerData);
            return;
        }

        CompoundTag controllerData = snapshot.controllerData();
        CompoundTag draftGraph = snapshot.draftGraph();
        CompoundTag activeGraph = snapshot.activeGraph();
        String kind = snapshot.kind() == null || snapshot.kind().isBlank() ? "base_controller" : snapshot.kind();
        migrated.addProperty("controllerManifestId", snapshot.id());
        migrated.add("controllerData", nbtJson(controllerData));
        migrated.add("storedTargets", nbtSectionJson(controllerData, "StoredTargets"));
        migrated.add("standardChannels", standardChannels(controllerData));
        migrated.add("customEntries", nbtSectionJson(controllerData, "CustomKeyEntries"));
        migrated.addProperty("insertedLinkerManifestId", insertedLinkerManifestId(controllerData));
        migrated.addProperty("ometerOutputSignal", controllerData.getInt("OmeterOutputSignal"));
        migrated.addProperty("customName", controllerData.getString("CustomName"));
        if ("advanced_controller".equals(kind) || !draftGraph.isEmpty() || !activeGraph.isEmpty()) {
            migrated.add("draftGraph", nbtJson(draftGraph));
            migrated.add("activeGraph", nbtJson(activeGraph));
            migrated.addProperty("compiledProgramHash", sha256(GSON.toJson(nbtJson(activeGraph))));
        }
        writeManifest(destination, migrated, snapshot.id(), snapshot.revision(), kind,
                controllerData, draftGraph, activeGraph, new CompoundTag());
    }

    // Get the linker root JSON
    private static JsonObject linkerRootJson(String id, @Nullable CompoundTag linkerData) {
        CompoundTag portableLinkerData = linkerData == null ? new CompoundTag() : linkerData.copy();
        JsonArray graphImages = separateSharedGraphImages(portableLinkerData);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", STORAGE_VERSION);
        root.addProperty("kind", "contraption_network_linker");
        root.addProperty("linkerManifestId", id);
        if (linkerData != null && linkerData.hasUUID("LinkerId")) {
            root.addProperty("linkerId", linkerData.getUUID("LinkerId").toString());
        } else {
            root.addProperty("linkerId", "");
        }
        root.add("targets", nbtSectionJson(portableLinkerData, "Targets"));
        root.add("channelBindings", nbtSectionJson(portableLinkerData, "ChannelBinds"));
        root.add("customEntryBindings", nbtSectionJson(portableLinkerData, "CustomEntryBinds"));
        root.add("storedControllerManifests", nbtSectionJson(portableLinkerData, "StoredControllerManifests"));
        root.add("storedGraphs", nbtSectionJson(portableLinkerData, "StoredGraphs"));
        root.add("graphImages", graphImages);
        root.add("linkerData", nbtJson(portableLinkerData));
        root.addProperty("updatedAtGameTime", 0L);
        root.addProperty("updatedAt", Instant.now().toString());
        return root;
    }

    // Get the separate shared graph images
    private static JsonArray separateSharedGraphImages(CompoundTag linkerData) {
        JsonArray images = new JsonArray();
        if (linkerData == null || !linkerData.contains("StoredGraphs", Tag.TAG_LIST)) {
            return images;
        }
        var graphs = linkerData.getList("StoredGraphs", Tag.TAG_COMPOUND);
        for (int graphIndex = 0; graphIndex < graphs.size(); graphIndex++) {
            CompoundTag entry = graphs.getCompound(graphIndex);
            String graphId = entry.getString("GraphId");
            if (graphId.isBlank()) {
                graphId = "stored_" + graphIndex;
            }
            AdvancedGraphImageAssets.StoredGraph stored =
                    AdvancedGraphImageAssets.separate(entry.getCompound("Graph"));
            entry.put("Graph", stored.graph());
            for (AdvancedGraphImageAssets.ImageAsset asset : stored.assets()) {
                JsonObject image = new JsonObject();
                image.addProperty("graphId", graphId);
                image.addProperty("assetId", asset.id());
                image.addProperty("mediaType", asset.mimeType());
                image.addProperty("base64", asset.base64());
                images.add(image);
            }
        }
        linkerData.put("StoredGraphs", graphs);
        return images;
    }

    // Hydrate the shared graph images
    private static void hydrateSharedGraphImages(JsonObject root, CompoundTag linkerData) {
        if (root == null || linkerData == null || !root.has("graphImages")
                || !root.get("graphImages").isJsonArray()
                || !linkerData.contains("StoredGraphs", Tag.TAG_LIST)) {
            return;
        }
        Map<String, List<AdvancedGraphImageAssets.ImageAsset>> byGraph = new LinkedHashMap<>();
        for (JsonElement elm : root.getAsJsonArray("graphImages")) {
            if (!elm.isJsonObject()) {
                continue;
            }
            JsonObject image = elm.getAsJsonObject();
            String graphId = string(image, "graphId");
            String assetId = string(image, "assetId");
            String mediaType = string(image, "mediaType");
            String base64 = string(image, "base64");
            if (graphId.isBlank() || assetId.isBlank() || base64.isBlank()) {
                continue;
            }
            byGraph.computeIfAbsent(graphId, ignored -> new ArrayList<>())
                    .add(new AdvancedGraphImageAssets.ImageAsset(assetId, mediaType, base64));
        }
        var graphs = linkerData.getList("StoredGraphs", Tag.TAG_COMPOUND);
        for (int graphIndex = 0; graphIndex < graphs.size(); graphIndex++) {
            CompoundTag entry = graphs.getCompound(graphIndex);
            String graphId = entry.getString("GraphId");
            if (graphId.isBlank()) {
                graphId = "stored_" + graphIndex;
            }
            List<AdvancedGraphImageAssets.ImageAsset> assets = byGraph.get(graphId);
            if (assets != null && !assets.isEmpty()) {
                entry.put("Graph", AdvancedGraphImageAssets.hydrateCopy(
                        entry.getCompound("Graph"), assets));
            }
        }
        linkerData.put("StoredGraphs", graphs);
    }

    // Get the owner
    private static JsonObject owner(@Nullable Level level, BlockPos pos, @Nullable UUID subLevelId) {
        JsonObject owner = new JsonObject();
        owner.addProperty("dimension", level == null ? "" : level.dimension().location().toString());
        owner.addProperty("blockPos", pos == null ? "" : pos.getX() + "," + pos.getY() + "," + pos.getZ());
        owner.addProperty("sublevelId", subLevelId == null ? "" : subLevelId.toString());
        return owner;
    }

    // Get the standard channels
    private static JsonObject standardChannels(@Nullable CompoundTag controllerData) {
        JsonObject standard = new JsonObject();
        standard.add("channels", nbtSectionJson(controllerData, "Channels"));
        standard.add("axes", nbtSectionJson(controllerData, "Axes"));
        standard.add("outputFrequencies", nbtSectionJson(controllerData, "Bindings"));
        standard.add("inputFrequencies", nbtSectionJson(controllerData, "InputBindings"));
        standard.add("directTargets", nbtSectionJson(controllerData, "DirectTargets"));
        standard.add("inputTargets", nbtSectionJson(controllerData, "InputTargets"));
        standard.add("localOutputSides", nbtSectionJson(controllerData, "LocalOutputSides"));
        standard.add("keyBindings", nbtSectionJson(controllerData, "KeyBindings"));
        standard.add("bindingModes", nbtSectionJson(controllerData, "BindingModes"));
        standard.add("bindingPresets", nbtSectionJson(controllerData, "BindingPresets"));
        return standard;
    }

    // Get the inserted linker manifest id
    private static String insertedLinkerManifestId(@Nullable CompoundTag controllerData) {
        if (controllerData == null || !controllerData.contains("LinkerSlot", Tag.TAG_COMPOUND)) {
            return "";
        }
        CompoundTag slot = controllerData.getCompound("LinkerSlot");
        String res = findStringRecursive(slot, TAG_LINKER_MANIFEST_ID);
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
            Create.LOGGER.warn("Failed to convert manifest field {} from JSON to NBT", key, err);
        }
        return new CompoundTag();
    }

    // Check if the manifest uses legacy NBT
    private static boolean manifestUsesLegacyNbt(JsonObject root) {
        if (root == null) {
            return false;
        }
        if (legacyPrimitive(root, "controllerData")
                || legacyPrimitive(root, "storedTargets")
                || legacyPrimitive(root, "customEntries")
                || legacyPrimitive(root, "draftGraph")
                || legacyPrimitive(root, "activeGraph")
                || legacyPrimitive(root, "targets")
                || legacyPrimitive(root, "channelBindings")
                || legacyPrimitive(root, "customEntryBindings")
                || legacyPrimitive(root, "storedControllerManifests")
                || legacyPrimitive(root, "storedGraphs")
                || legacyPrimitive(root, "linkerData")) {
            return true;
        }
        if (root.has("standardChannels") && root.get("standardChannels").isJsonObject()) {
            JsonObject standard = root.getAsJsonObject("standardChannels");
            for (String key : standard.keySet()) {
                if (legacyPrimitive(standard, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Check if the legacy value is primitive
    private static boolean legacyPrimitive(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonPrimitive();
    }

    // Get the NBT JSON
    private static JsonElement nbtJson(@Nullable CompoundTag tag) {
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag == null ? new CompoundTag() : tag);
    }

    // Get the NBT section JSON
    private static JsonElement nbtSectionJson(@Nullable CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) {
            return JsonNull.INSTANCE;
        }
        return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag.get(key));
    }

    // Get the string
    private static String string(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : "";
    }

    // Get the stable manifest id
    private static String stableManifestId(String manifestId) {
        if (manifestId != null && !manifestId.isBlank()) {
            return manifestId.trim();
        }
        return UUID.randomUUID().toString();
    }

    // Get the controller path
    private static Path controllerPath(String manifestId, @Nullable Level level) {
        return rootDir(level).resolve("controllers").resolve(safeFileName(manifestId) + ".json");
    }

    // Get the legacy controller path
    private static Path legacyControllerPath(String manifestId) {
        return legacyRootDir().resolve("controllers").resolve(safeFileName(manifestId) + ".json");
    }

    // Get the linker path
    private static Path linkerPath(String manifestId) {
        return rootDir().resolve("linkers").resolve(safeFileName(manifestId) + ".json");
    }

    // Get the legacy linker path
    private static Path legacyLinkerPath(String manifestId) {
        return legacyRootDir().resolve("linkers").resolve(safeFileName(manifestId) + ".json");
    }

    // Get the shared graph path
    private static Path sharedGraphPath(String manifestId) {
        return sharedGraphPath(sharedGraphsDir(), manifestId);
    }

    // Get the shared graph path
    private static Path sharedGraphPath(Path dir, String manifestId) {
        return dir.resolve(safeFileName(manifestId) + ".json");
    }

    // Get the root dir
    private static Path rootDir() {
        return rootDir(null);
    }

    // Get the root dir
    private static Path rootDir(@Nullable Level level) {
        MinecraftServer server = currentServer(level);
        if (server != null) {
            return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ROOT_DIR);
        }
        return legacyRootDir();
    }

    // Get the legacy root dir
    private static Path legacyRootDir() {
        return FMLPaths.GAMEDIR.get().resolve(ROOT_DIR);
    }

    // Get the shared graphs dir
    private static Path sharedGraphsDir() {
        return FMLPaths.GAMEDIR.get().resolve(SHARED_GRAPHS_DIR);
    }

    // Get the tmp dir
    private static Path tmpDirFor(Path manifestPath) {
        Path parent = manifestPath.getParent();
        if (parent != null && parent.getFileName() != null
                && SHARED_GRAPHS_DIR.equals(parent.getFileName().toString())) {
            return parent.resolve("tmp");
        }
        Path root = parent == null ? null : parent.getParent();
        return root == null ? rootDir().resolve("tmp") : root.resolve("tmp");
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

    // Import the legacy linker manifests
    private static void importLegacyLinkerManifests() {
        ControllerSqliteStore.importLegacyLinkers(rootDir().resolve("linkers"), path -> readManifest(path, true));
        Path legacy = legacyRootDir().resolve("linkers");
        if (!Objects.equals(legacy, rootDir().resolve("linkers"))) {
            ControllerSqliteStore.importLegacyLinkers(legacy, path -> readManifest(path, true));
        }
    }

    // Check if this is shared graph path
    private static boolean isSharedGraphPath(Path path) {
        Path parent = path == null ? null : path.getParent();
        return parent != null && parent.getFileName() != null
                && SHARED_GRAPHS_DIR.equals(parent.getFileName().toString());
    }

    // Get the safe shared base name
    private static String safeSharedBaseName(String requestedName) {
        String safe = safeFileName(requestedName);
        return safe == null || safe.isBlank() ? "graph" : safe;
    }

    // Get the unique shared graph id
    private static String uniqueSharedGraphId(Path dir, String baseName) {
        String safeBase = baseName == null || baseName.isBlank() ? "graph" : baseName;
        String candidate = safeBase;
        for (int suffix = 2; Files.exists(sharedGraphPath(dir, candidate)); suffix++) {
            candidate = safeBase + "_" + suffix;
        }
        return candidate;
    }

    // Get the shared display name
    private static String sharedDisplayName(String requestedName, String baseName, String id) {
        String display = requestedName == null || requestedName.isBlank() ? baseName : requestedName.trim();
        if (id.equals(baseName)) {
            return display;
        }
        String suffix = id.substring(Math.min(id.length(), baseName.length()));
        if (suffix.startsWith("_")) {
            return display + " " + suffix.substring(1);
        }
        return display;
    }

    // Get the safe file name
    private static String safeFileName(String manifestId) {
        String normalized = manifestId == null ? "" : manifestId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            normalized = UUID.randomUUID().toString();
        }
        return normalized.replaceAll("[^a-z0-9._-]", "_");
    }

    // Strip the JSON file suffix
    private static String stripJsonSuffix(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    // Calculate the SHA-256 hash
    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte val : bytes) {
                builder.append(String.format("%02x", val));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException err) {
            return Integer.toHexString(ObjectsHash.hash(text));
        }
    }

    // Handle the objects hash
    private static final class ObjectsHash {
        // Get the hash
        private static int hash(String val) {
            return val == null ? 0 : val.hashCode();
        }
    }
}
