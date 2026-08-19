package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorage;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppEntitlementApi;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppEntitlementStore;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppPurchaseScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

// Store tablet data in SQLite and keep normal reads inside the in-memory cache
public final class DiagnosticTabletDatabase implements TabletStorage, TabletAppEntitlementStore, AutoCloseable {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final System.Logger LOGGER = System.getLogger(
            DiagnosticTabletDatabase.class.getName());
    private static final String DATABASE_NAME = "diagnostic_tablet.db";
    private static final Set<ResourceLocation> REQUIRED_INSTALLED_APPS = Set.of(
            DiagnosticTabletData.appId("settings"),
            DiagnosticTabletData.appId("app_store"));
    private static final Set<ResourceLocation> DEFAULT_INSTALLED_APPS = REQUIRED_INSTALLED_APPS;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared diagnostic tablet database
    private static volatile DiagnosticTabletDatabase active;

    // Active server
    private final MinecraftServer server;
    // Connection
    private final Connection connection;
    // Tracked tablets
    private final ConcurrentHashMap<UUID, CompoundTag> tablets = new ConcurrentHashMap<>();
    // Tracked apps
    private final ConcurrentHashMap<AppKey, AppRow> apps = new ConcurrentHashMap<>();
    // Pending tablet writes
    private final ConcurrentHashMap<UUID, CompoundTag> pendingTabletWrites =
            new ConcurrentHashMap<>();
    // Pending app writes
    private final ConcurrentHashMap<AppKey, AppRow> pendingAppWrites =
            new ConcurrentHashMap<>();
    // Write drain scheduled
    private final AtomicBoolean writeDrainScheduled = new AtomicBoolean();
    // Writer
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Create Thrusters tablet database");
        thread.setDaemon(true);
        return thread;
    });
    // Tracks whether writes are being accepted
    private boolean acceptingWrites = true;
    // Purchased app Entitlements
    private final Set<EntitlementKey> entitlements = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<EntitlementKey, Boolean> pendingEntitlements = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet database
    private DiagnosticTabletDatabase(MinecraftServer server) throws IOException, SQLException {
        this.server = server;
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATABASE_NAME);
        Files.createDirectories(path.getParent());
        connection = SqliteDriverLoader.connect("jdbc:sqlite:" + path.toAbsolutePath().normalize());
        configConnection();
        createSchema();
        loadCache();
        installMissingBuiltIns();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server started event
    public static void onServerStarted(ServerStartedEvent evt) {
        closeActive();
        try {
            DiagnosticTabletDatabase database = new DiagnosticTabletDatabase(evt.getServer());
            active = database;
            TabletStorageApi.install(database);
            TabletAppEntitlementApi.install(database);
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not open the smart tablet database", err);
        }
    }

    // Handle the server stopped event
    public static void onServerStopped(ServerStoppedEvent evt) {
        closeActive();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the server
    public static DiagnosticTabletDatabase forServer(MinecraftServer server) {
        DiagnosticTabletDatabase current = active;
        if (current != null && current.server == server) return current;
        synchronized (DiagnosticTabletDatabase.class) {
            current = active;
            if (current != null && current.server == server) return current;
            closeActive();
            try {
                current = new DiagnosticTabletDatabase(server);
                active = current;
                TabletStorageApi.install(current);
                TabletAppEntitlementApi.install(current);
                return current;
            } catch (IOException | SQLException err) {
                throw new IllegalStateException("Could not open the smart tablet database", err);
            }
        }
    }

    // Get the tablet
    @Override
    public CompoundTag tablet(UUID tabletId) {
        if (tabletId == null) return new CompoundTag();
        ensureTablet(tabletId);
        return tablets.get(tabletId).copy();
    }

    // Get the app
    @Override
    public CompoundTag app(UUID tabletId, ResourceLocation appId) {
        if (tabletId == null || appId == null) return new CompoundTag();
        ensureTablet(tabletId);
        AppRow row = apps.get(new AppKey(tabletId, appId));
        return row == null ? new CompoundTag() : row.data().copy();
    }

    // Get the installed apps
    @Override
    public Set<ResourceLocation> installedApps(UUID tabletId) {
        if (tabletId == null) return Set.of();
        ensureTablet(tabletId);
        Set<ResourceLocation> installed = new LinkedHashSet<>();
        apps.forEach((key, row) -> {
            if (key.tabletId().equals(tabletId) && row.installed()) installed.add(key.appId());
        });
        return Set.copyOf(installed);
    }

    // Update the tablet
    @Override
    public synchronized CompoundTag updateTablet(UUID tabletId,
                                                 UnaryOperator<CompoundTag> mutation) {
        if (tabletId == null || mutation == null) return new CompoundTag();
        ensureTablet(tabletId);
        CompoundTag prev = tablets.get(tabletId);
        CompoundTag next = mutation.apply(prev.copy());
        if (next == null) next = prev.copy();
        if (next.equals(prev)) {
            return prev.copy();
        }
        CompoundTag stored = next.copy();
        tablets.put(tabletId, stored);
        queueTabletWrite(tabletId, stored);
        return stored.copy();
    }

    // Update the app
    @Override
    public synchronized CompoundTag updateApp(UUID tabletId, ResourceLocation appId,
                                              UnaryOperator<CompoundTag> mutation) {
        if (tabletId == null || appId == null || mutation == null) return new CompoundTag();
        ensureTablet(tabletId);
        AppKey key = new AppKey(tabletId, appId);
        AppRow prev = apps.getOrDefault(key, new AppRow(false, new CompoundTag()));
        CompoundTag next = mutation.apply(prev.data().copy());
        if (next == null) next = prev.data().copy();
        if (next.equals(prev.data())) {
            return prev.data().copy();
        }
        AppRow stored = new AppRow(prev.installed(), next.copy());
        apps.put(key, stored);
        queueAppWrite(key, stored);
        return stored.data().copy();
    }

    // Set the installed
    @Override
    public synchronized boolean setInstalled(UUID tabletId, ResourceLocation appId,
                                             boolean installed) {
        if (tabletId == null || appId == null) return false;
        ensureTablet(tabletId);
        AppKey key = new AppKey(tabletId, appId);
        AppRow prev = apps.getOrDefault(key, new AppRow(false, new CompoundTag()));
        if (!installed && REQUIRED_INSTALLED_APPS.contains(appId)) {
            if (!prev.installed()) {
                AppRow stored = new AppRow(true, prev.data().copy());
                apps.put(key, stored);
                queueAppWrite(key, stored);
            }
            return false;
        }
        if (prev.installed() == installed) return true;
        AppRow stored = new AppRow(installed, prev.data().copy());
        apps.put(key, stored);
        queueAppWrite(key, stored);
        return true;
    }

    @Override
    public boolean owns(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId,
                        ResourceLocation appId) {
        EntitlementKey key = entitlementKey(scope, playerId, tabletId, appId);
        return key != null && entitlements.contains(key);
    }

    @Override
    public synchronized boolean grant(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId,
                                      ResourceLocation appId) {
        EntitlementKey key = entitlementKey(scope, playerId, tabletId, appId);
        if (key == null) return false;
        if (entitlements.add(key)) {
            pendingEntitlements.put(key, true);
            scheduleWriteDrain();
        }
        return true;
    }

    @Override
    public synchronized boolean revoke(TabletAppPurchaseScope scope, UUID playerId, UUID tabletId,
                                       ResourceLocation appId) {
        EntitlementKey key = entitlementKey(scope, playerId, tabletId, appId);
        if (key == null) return false;
        if (entitlements.remove(key)) {
            pendingEntitlements.put(key, false);
            scheduleWriteDrain();
        }
        return true;
    }

    private static EntitlementKey entitlementKey(TabletAppPurchaseScope scope, UUID playerId,
                                                 UUID tabletId, ResourceLocation appId) {
        if (scope == null || appId == null) return null;
        UUID ownerId = scope == TabletAppPurchaseScope.PLAYER ? playerId : tabletId;
        return ownerId == null ? null : new EntitlementKey(scope, ownerId, appId);
    }

    // Import the legacy
    public synchronized void importLegacy(UUID tabletId, DiagnosticTabletData.State state) {
        if (tabletId == null || state == null) return;
        ensureTablet(tabletId);
        ResourceLocation appId = state.app();
        AppRow currentApp = apps.get(new AppKey(tabletId, appId));
        if (currentApp == null || !currentApp.data().contains("MigratedLegacy")) {
            updateApp(tabletId, appId, data -> {
                if (state.binding() != null) data.put("SelectedBinding", state.binding().toTag());
                if (!state.selections().isEmpty()) {
                    net.minecraft.nbt.ListTag selections = new net.minecraft.nbt.ListTag();
                    state.selections().forEach(binding -> selections.add(binding.toTag()));
                    data.put("Selections", selections);
                }
                data.putBoolean("MigratedLegacy", true);
                return data;
            });
        }
        if (!state.redstoneLinkChannels().isEmpty()) {
            ResourceLocation redstoneLink = DiagnosticTabletData.appId("redstone_link");
            AppRow currentRedstoneLink = apps.get(new AppKey(tabletId, redstoneLink));
            if (currentRedstoneLink == null
                    || !currentRedstoneLink.data().contains("Channels")) {
                updateApp(tabletId, redstoneLink, data -> {
                    net.minecraft.nbt.ListTag channels = new net.minecraft.nbt.ListTag();
                    state.redstoneLinkChannels().forEach(channel -> channels.add(channel.toTag()));
                    data.put("Channels", channels);
                    return data;
                });
            }
        }
    }

    // Ensure the tablet
    private synchronized void ensureTablet(UUID tabletId) {
        if (tablets.containsKey(tabletId)) return;
        CompoundTag defaults = new CompoundTag();
        defaults.putString("Name", "Smart Tablet");
        defaults.putString("Wallpaper", "aurora");
        defaults.putString("Theme", "dark");
        updateNewTablet(tabletId, defaults);
    }

    // Update the new tablet
    private void updateNewTablet(UUID tabletId, CompoundTag defaults) {
        CompoundTag stored = defaults.copy();
        tablets.put(tabletId, stored);
        queueTabletWrite(tabletId, stored);
        for (ResourceLocation appId : DEFAULT_INSTALLED_APPS) {
            AppKey key = new AppKey(tabletId, appId);
            AppRow row = new AppRow(true, new CompoundTag());
            AppRow existing = apps.putIfAbsent(key, row);
            queueAppWrite(key, existing == null ? row : existing);
        }
    }

    // Queue the tablet write
    private void queueTabletWrite(UUID tabletId, CompoundTag data) {
        if (!acceptingWrites) return;
        pendingTabletWrites.put(tabletId, data.copy());
        scheduleWriteDrain();
    }

    // Queue the app write
    private void queueAppWrite(AppKey key, AppRow row) {
        if (!acceptingWrites) return;
        pendingAppWrites.put(key, new AppRow(row.installed(), row.data().copy()));
        scheduleWriteDrain();
    }

    // Schedule the write drain
    private void scheduleWriteDrain() {
        if (writeDrainScheduled.compareAndSet(false, true)) {
            writer.execute(this::drainPendingWrites);
        }
    }

    // Drain the pending writes
    private void drainPendingWrites() {
        try {
            while (true) {
                Map<UUID, CompoundTag> tabletBatch = removePendingTabletWrites();
                Map<AppKey, AppRow> appBatch = removePendingAppWrites();
                Map<EntitlementKey, Boolean> entitlementBatch = removePendingEntitlementWrites();
                if (tabletBatch.isEmpty() && appBatch.isEmpty() && entitlementBatch.isEmpty()) return;
                writeBatch(tabletBatch, appBatch, entitlementBatch);
            }
        } finally {
            writeDrainScheduled.set(false);
            if (!pendingTabletWrites.isEmpty() || !pendingAppWrites.isEmpty() || !pendingEntitlements.isEmpty()) {
                scheduleWriteDrain();
            }
        }
    }

    // Remove the pending tablet writes
    private Map<UUID, CompoundTag> removePendingTabletWrites() {
        Map<UUID, CompoundTag> batch = new LinkedHashMap<>();
        pendingTabletWrites.forEach((tabletId, data) -> {
            if (pendingTabletWrites.remove(tabletId, data)) {
                batch.put(tabletId, data);
            }
        });
        return batch;
    }

    // Remove the pending app writes
    private Map<AppKey, AppRow> removePendingAppWrites() {
        Map<AppKey, AppRow> batch = new LinkedHashMap<>();
        pendingAppWrites.forEach((key, row) -> {
            if (pendingAppWrites.remove(key, row)) {
                batch.put(key, row);
            }
        });
        return batch;
    }

    // Write the batch
    private void writeBatch(Map<UUID, CompoundTag> tabletBatch,
                        Map<AppKey, AppRow> appBatch,
                        Map<EntitlementKey, Boolean> entitlementBatch) {
    try {
        connection.setAutoCommit(false);
        try (PreparedStatement tablet = connection.prepareStatement("""
                INSERT INTO tablets(tablet_id, data, updated_at) VALUES(?, ?, ?)
                ON CONFLICT(tablet_id) DO UPDATE SET data=excluded.data,
                updated_at=excluded.updated_at
                """);
             PreparedStatement app = connection.prepareStatement("""
                INSERT INTO tablet_apps(tablet_id, app_id, installed, data, updated_at)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(tablet_id, app_id) DO UPDATE SET installed=excluded.installed,
                data=excluded.data, updated_at=excluded.updated_at
                """);
             PreparedStatement entitlementInsert = connection.prepareStatement("""
                INSERT OR REPLACE INTO tablet_app_entitlements(
                ownership_scope, owner_id, app_id, acquired_at) VALUES(?, ?, ?, ?)
                """);
             PreparedStatement entitlementDelete = connection.prepareStatement("""
                DELETE FROM tablet_app_entitlements
                WHERE ownership_scope=? AND owner_id=? AND app_id=?
                """)) {
            long updatedAt = System.currentTimeMillis();
            for (Map.Entry<UUID, CompoundTag> entry : tabletBatch.entrySet()) {
                tablet.setString(1, entry.getKey().toString());
                tablet.setBytes(2, encode(entry.getValue()));
                tablet.setLong(3, updatedAt);
                tablet.addBatch();
            }
            if (!tabletBatch.isEmpty()) tablet.executeBatch();

            for (Map.Entry<AppKey, AppRow> entry : appBatch.entrySet()) {
                app.setString(1, entry.getKey().tabletId().toString());
                app.setString(2, entry.getKey().appId().toString());
                app.setInt(3, entry.getValue().installed() ? 1 : 0);
                app.setBytes(4, encode(entry.getValue().data()));
                app.setLong(5, updatedAt);
                app.addBatch();
            }
            if (!appBatch.isEmpty()) app.executeBatch();

            boolean hasInserts = false;
            boolean hasDeletes = false;
            for (Map.Entry<EntitlementKey, Boolean> entry : entitlementBatch.entrySet()) {
                EntitlementKey key = entry.getKey();
                if (entry.getValue()) {
                    entitlementInsert.setString(1, key.scope().name());
                    entitlementInsert.setString(2, key.ownerId().toString());
                    entitlementInsert.setString(3, key.appId().toString());
                    entitlementInsert.setLong(4, updatedAt);
                    entitlementInsert.addBatch();
                    hasInserts = true;
                } else {
                    entitlementDelete.setString(1, key.scope().name());
                    entitlementDelete.setString(2, key.ownerId().toString());
                    entitlementDelete.setString(3, key.appId().toString());
                    entitlementDelete.addBatch();
                    hasDeletes = true;
                }
            }
            if (hasInserts) entitlementInsert.executeBatch();
            if (hasDeletes) entitlementDelete.executeBatch();
        }
        connection.commit();
    } catch (SQLException | IOException error) {
        rollbackQuietly();
        LOGGER.log(System.Logger.Level.ERROR,
                "Could not save diagnostic tablet data", error);
    } finally {
        setAutoCommitQuietly(true);
    }
}

    // Write the app
    private boolean writeApp(AppKey key, boolean installed, CompoundTag data) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO tablet_apps(tablet_id, app_id, installed, data, updated_at)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(tablet_id, app_id) DO UPDATE SET installed=excluded.installed,
                data=excluded.data, updated_at=excluded.updated_at
                """)) {
            statement.setString(1, key.tabletId().toString());
            statement.setString(2, key.appId().toString());
            statement.setInt(3, installed ? 1 : 0);
            statement.setBytes(4, encode(data));
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
            return true;
        } catch (SQLException | IOException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save tablet app data", err);
            return false;
        }
    }

    // Create the schema
    private void createSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tablets(
                    tablet_id TEXT PRIMARY KEY, data BLOB NOT NULL, updated_at INTEGER NOT NULL)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tablet_apps(
                    tablet_id TEXT NOT NULL, app_id TEXT NOT NULL, installed INTEGER NOT NULL,
                    data BLOB NOT NULL, updated_at INTEGER NOT NULL,
                    PRIMARY KEY(tablet_id, app_id))
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tablet_app_entitlements(
                    ownership_scope TEXT NOT NULL,
                    owner_id TEXT NOT NULL,
                    app_id TEXT NOT NULL,
                    acquired_at INTEGER NOT NULL,
                    PRIMARY KEY(ownership_scope, owner_id, app_id))
                    """);
        }
    }

    // Configure the connection
    private void configConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA temp_store=MEMORY");
        }
    }

    // Load the cache
    private void loadCache() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT tablet_id, data FROM tablets")) {
            while (rows.next()) {
                try {
                    tablets.put(UUID.fromString(rows.getString(1)), decode(rows.getBytes(2)));
                } catch (IllegalArgumentException | IOException err) {
                    LOGGER.log(System.Logger.Level.WARNING, "Ignored an invalid tablet row", err);
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT tablet_id, app_id, installed, data FROM tablet_apps")) {
            while (rows.next()) {
                try {
                    AppKey key = new AppKey(UUID.fromString(rows.getString(1)),
                            ResourceLocation.parse(rows.getString(2)));
                    apps.put(key, new AppRow(rows.getInt(3) != 0, decode(rows.getBytes(4))));
                } catch (IllegalArgumentException | IOException err) {
                    LOGGER.log(System.Logger.Level.WARNING, "Ignored an invalid tablet app row", err);
                }
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT ownership_scope, owner_id, app_id FROM tablet_app_entitlements")) {
            while (rows.next()) {
                try {
                    entitlements.add(new EntitlementKey(TabletAppPurchaseScope.valueOf(rows.getString(1)),
                    UUID.fromString(rows.getString(2)),
                    ResourceLocation.parse(rows.getString(3))));
                } catch (IllegalArgumentException err) {
                    LOGGER.log(System.Logger.Level.WARNING, "Ignored an invalid tablet app entitlement row", err);
                }
            }
        }
    }

    private Map<EntitlementKey, Boolean> removePendingEntitlementWrites(){
        Map<EntitlementKey, Boolean> batch = new LinkedHashMap<>();
        pendingEntitlements.forEach((key, installed) ->{
            if(pendingEntitlements.remove(key, installed)){
                batch.put(key, installed);
            }
        });
        return batch;
    }

    // Install the missing built ins
    private void installMissingBuiltIns() {
        for (UUID tabletId : Set.copyOf(tablets.keySet())) {
            for (ResourceLocation appId : DEFAULT_INSTALLED_APPS) {
                AppKey key = new AppKey(tabletId, appId);
                AppRow existing = apps.get(key);
                if (existing != null) {
                    if (REQUIRED_INSTALLED_APPS.contains(appId) && !existing.installed()
                            && writeApp(key, true, existing.data())) {
                        apps.put(key, new AppRow(true, existing.data()));
                    }
                    continue;
                }
                CompoundTag data = new CompoundTag();
                if (writeApp(key, true, data)) {
                    apps.put(key, new AppRow(true, data));
                }
            }
        }
    }

    // Encode the diagnostic tablet database
    private static byte[] encode(CompoundTag tag) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag == null ? new CompoundTag() : tag, output);
        return output.toByteArray();
    }

    // Decode the diagnostic tablet database
    private static CompoundTag decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) return new CompoundTag();
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
    }

    // Close the active
    private static synchronized void closeActive() {
        DiagnosticTabletDatabase current = active;
        active = null;
        if (current == null) return;
        TabletStorageApi.uninstall(current);
        TabletAppEntitlementApi.uninstall(current);
        try {
            current.close();
        } catch (Exception err) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not close the diagnostic tablet database", err);
        }
    }

    // Roll back the quietly
    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    // Set the auto commit quietly
    private void setAutoCommitQuietly(boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
        }
    }

    // Close the diagnostic tablet database
    @Override
    public synchronized void close() throws SQLException {
        acceptingWrites = false;
        if (!pendingTabletWrites.isEmpty() || !pendingAppWrites.isEmpty()
                || !pendingEntitlements.isEmpty()) {
            scheduleWriteDrain();
        }
        AtomicReference<SQLException> closeFailure = new AtomicReference<>();
        writer.execute(() -> {
            try {
                connection.close();
            } catch (SQLException error) {
                closeFailure.set(error);
            }
        });
        writer.shutdown();
        boolean terminated = false;
        try {
            terminated = writer.awaitTermination(15L, TimeUnit.SECONDS);
            if (!terminated) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Tablet data is still flushing after the shutdown timeout");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING,
                    "Interrupted while flushing diagnostic tablet data", error);
        }
        tablets.clear();
        apps.clear();
        entitlements.clear();
        if (terminated) {
            pendingTabletWrites.clear();
            pendingAppWrites.clear();
            pendingEntitlements.clear();
        }
        if (closeFailure.get() != null) {
            throw closeFailure.get();
        }
    }
    private record AppKey(UUID tabletId, ResourceLocation appId) {
    }

    private record AppRow(boolean installed, CompoundTag data) {
        private AppRow {
            data = data == null ? new CompoundTag() : data.copy();
        }
    }

    private record EntitlementKey(TabletAppPurchaseScope scope, UUID ownerId, ResourceLocation appId) {
    }
}
