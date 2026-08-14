package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Store calibrated SCM maps in SQLite and keep normal map reads cached in memory
public final class ShipControlMapStore {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String DATABASE_NAME = "ship-control-maps.db";
    private static final System.Logger LOGGER = System.getLogger(ShipControlMapStore.class.getName());
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active cache
    private static volatile MapCache activeCache;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship control map store
    private ShipControlMapStore() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server started event
    public static void onServerStarted(ServerStartedEvent evt) {
        cacheFor(evt.getServer());
    }

    // Handle the server stopped event
    public static void onServerStopped(ServerStoppedEvent evt) {
        MapCache current = activeCache;
        if (current != null && current.server() == evt.getServer()) {
            activeCache = null;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Save the ship control map store
    public static boolean save(@Nullable Level level, ShipControlMap map) {
        if (map == null) {
            return false;
        }
        try (Connection connection = open(level)) {
            if (connection == null) {
                return false;
            }
            connection.setAutoCommit(false);
            try {
                upsertMap(connection, map);
                replaceUnits(connection, map);
                connection.commit();
                MapCache cache = cacheFor(level == null ? null : level.getServer());
                if (cache != null) {
                    cache.maps().put(map.id(), map);
                }
                return true;
            } catch (SQLException err) {
                connection.rollback();
                throw err;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save ship control map " + map.id(), err);
            return false;
        }
    }

    // Load the ship control map store
    public static @Nullable ShipControlMap load(@Nullable Level level, @Nullable UUID mapId) {
        if (mapId == null) {
            return null;
        }
        MapCache cache = cacheFor(level == null ? null : level.getServer());
        if (cache != null) {
            return cache.maps().get(mapId);
        }
        try (Connection connection = open(level)) {
            if (connection == null) {
                return null;
            }
            ShipControlMap header = loadHeader(connection, mapId);
            if (header == null) {
                return null;
            }
            List<ShipControlMap.PropulsionUnit> units = loadUnits(connection, mapId);
            return new ShipControlMap(header.id(), header.dimension(), header.rootSubLevelId(),
                    header.controllerPosition(), header.centerOfMass(), units,
                    loadBearings(connection, mapId),
                    loadDockingConnectors(connection, mapId),
                    loadCrnDisplays(connection, mapId),
                    loadAccDisplays(connection, mapId), header.updatedAt());
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load ship control map " + mapId, err);
            return null;
        }
    }

    // Get the database path
    public static @Nullable Path databasePath(@Nullable Level level) {
        if (level == null || level.getServer() == null) {
            return null;
        }
        return databasePath(level.getServer());
    }

    // Get the database path
    private static Path databasePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATABASE_NAME);
    }

    // Get the cache
    private static @Nullable MapCache cacheFor(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        MapCache current = activeCache;
        if (current != null && current.server() == server) {
            return current;
        }
        synchronized (ShipControlMapStore.class) {
            current = activeCache;
            if (current != null && current.server() == server) {
                return current;
            }
            try {
                MapCache loaded = new MapCache(server,
                        new ConcurrentHashMap<>(loadAll(server)));
                activeCache = loaded;
                return loaded;
            } catch (IOException | SQLException err) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Could not preload ship control maps", err);
                return null;
            }
        }
    }

    // Load every stored ship control map
    private static Map<UUID, ShipControlMap> loadAll(MinecraftServer server)
            throws IOException, SQLException {
        Path path = databasePath(server);
        Files.createDirectories(path.getParent());
        try (Connection connection = open(path)) {
            Map<UUID, ShipControlMap> loaded = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT map_id FROM ship_maps ORDER BY map_id");
                 ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    UUID mapId;
                    try {
                        mapId = UUID.fromString(res.getString(1));
                    } catch (IllegalArgumentException err) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Skipping ship control map with an invalid id", err);
                        continue;
                    }
                    ShipControlMap map = loadCompleteMap(connection, mapId);
                    if (map != null) {
                        loaded.put(mapId, map);
                    }
                }
            }
            return Map.copyOf(loaded);
        }
    }

    // Open the ship control map store
    private static @Nullable Connection open(@Nullable Level level) throws IOException, SQLException {
        Path path = databasePath(level);
        if (path == null) {
            return null;
        }
        return open(path);
    }

    // Open the ship control map store
    private static Connection open(Path path) throws IOException, SQLException {
        Files.createDirectories(path.getParent());
        Connection connection = SqliteDriverLoader.connect("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        installSchema(connection);
        return connection;
    }

    // Load the complete map
    private static @Nullable ShipControlMap loadCompleteMap(Connection connection, UUID mapId)
            throws SQLException {
        ShipControlMap header = loadHeader(connection, mapId);
        if (header == null) {
            return null;
        }
        return new ShipControlMap(header.id(), header.dimension(), header.rootSubLevelId(),
                header.controllerPosition(), header.centerOfMass(), loadUnits(connection, mapId),
                loadBearings(connection, mapId), loadDockingConnectors(connection, mapId),
                loadCrnDisplays(connection, mapId), loadAccDisplays(connection, mapId),
                header.updatedAt());
    }

    // Store the map cache
    private record MapCache(MinecraftServer server,
                            ConcurrentHashMap<UUID, ShipControlMap> maps) {
    }

    // Install the schema
    private static void installSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // ------------------------------------MAP / PROPULSION------------------------------------
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ship_maps (
                        map_id TEXT PRIMARY KEY,
                        dimension TEXT NOT NULL,
                        root_sublevel_id TEXT NOT NULL,
                        controller_x INTEGER NOT NULL,
                        controller_y INTEGER NOT NULL,
                        controller_z INTEGER NOT NULL,
                        center_x REAL NOT NULL,
                        center_y REAL NOT NULL,
                        center_z REAL NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS propulsion_units (
                        map_id TEXT NOT NULL,
                        unit_index INTEGER NOT NULL,
                        sublevel_id TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        block_id TEXT NOT NULL,
                        adapter TEXT NOT NULL,
                        controllable INTEGER NOT NULL,
                        root_x REAL NOT NULL,
                        root_y REAL NOT NULL,
                        root_z REAL NOT NULL,
                        force_x REAL NOT NULL,
                        force_y REAL NOT NULL,
                        force_z REAL NOT NULL,
                        min_control REAL NOT NULL,
                        max_control REAL NOT NULL,
                        min_thrust REAL NOT NULL,
                        max_thrust REAL NOT NULL,
                        max_speed REAL NOT NULL,
                        PRIMARY KEY (map_id, unit_index),
                        FOREIGN KEY (map_id) REFERENCES ship_maps(map_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS propulsion_samples (
                        map_id TEXT NOT NULL,
                        unit_index INTEGER NOT NULL,
                        sample_index INTEGER NOT NULL,
                        min_control REAL NOT NULL DEFAULT 0.0,
                        max_control REAL NOT NULL DEFAULT 1.0,
                        control REAL NOT NULL,
                        speed REAL NOT NULL,
                        thrust REAL NOT NULL,
                        active INTEGER NOT NULL,
                        PRIMARY KEY (map_id, unit_index, sample_index),
                        FOREIGN KEY (map_id, unit_index)
                            REFERENCES propulsion_units(map_id, unit_index) ON DELETE CASCADE
                    )
                    """);
            // ------------------------------------CONTROL ENDPOINTS------------------------------------
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_docking_connectors (
                        map_id TEXT NOT NULL,
                        connector_index INTEGER NOT NULL,
                        sublevel_id TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        root_tip_x REAL NOT NULL,
                        root_tip_y REAL NOT NULL,
                        root_tip_z REAL NOT NULL,
                        root_facing_x REAL NOT NULL,
                        root_facing_y REAL NOT NULL,
                        root_facing_z REAL NOT NULL,
                        PRIMARY KEY (map_id, connector_index),
                        FOREIGN KEY (map_id) REFERENCES ship_maps(map_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_crn_displays (
                        map_id TEXT NOT NULL,
                        sublevel_id TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        PRIMARY KEY (map_id, sublevel_id, block_x, block_y, block_z),
                        FOREIGN KEY (map_id) REFERENCES ship_maps(map_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_acc_displays (
                        map_id TEXT NOT NULL,
                        sublevel_id TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        PRIMARY KEY (map_id, sublevel_id, block_x, block_y, block_z),
                        FOREIGN KEY (map_id) REFERENCES ship_maps(map_id) ON DELETE CASCADE
                    )
                    """);
            // ------------------------------------BEARING MAPS------------------------------------
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_bearings (
                        map_id TEXT NOT NULL,
                        bearing_index INTEGER NOT NULL,
                        host_sublevel_id TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        block_id TEXT NOT NULL,
                        adapter TEXT NOT NULL,
                        min_x REAL NOT NULL,
                        max_x REAL NOT NULL,
                        min_z REAL NOT NULL,
                        max_z REAL NOT NULL,
                        PRIMARY KEY (map_id, bearing_index),
                        FOREIGN KEY (map_id) REFERENCES ship_maps(map_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_bearing_children (
                        map_id TEXT NOT NULL,
                        bearing_index INTEGER NOT NULL,
                        child_index INTEGER NOT NULL,
                        child_sublevel_id TEXT NOT NULL,
                        PRIMARY KEY (map_id, bearing_index, child_index),
                        FOREIGN KEY (map_id, bearing_index)
                            REFERENCES control_bearings(map_id, bearing_index) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_bearing_poses (
                        map_id TEXT NOT NULL,
                        bearing_index INTEGER NOT NULL,
                        pose_index INTEGER NOT NULL,
                        angle_x REAL NOT NULL,
                        angle_z REAL NOT NULL,
                        max_aerodynamic_force REAL NOT NULL DEFAULT 0.0,
                        max_aerodynamic_torque REAL NOT NULL DEFAULT 0.0,
                        PRIMARY KEY (map_id, bearing_index, pose_index),
                        FOREIGN KEY (map_id, bearing_index)
                            REFERENCES control_bearings(map_id, bearing_index) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_bearing_responses (
                        map_id TEXT NOT NULL,
                        bearing_index INTEGER NOT NULL,
                        pose_index INTEGER NOT NULL,
                        unit_index INTEGER NOT NULL,
                        root_x REAL NOT NULL,
                        root_y REAL NOT NULL,
                        root_z REAL NOT NULL,
                        force_x REAL NOT NULL,
                        force_y REAL NOT NULL,
                        force_z REAL NOT NULL,
                        max_thrust REAL NOT NULL,
                        PRIMARY KEY (map_id, bearing_index, pose_index, unit_index),
                        FOREIGN KEY (map_id, bearing_index, pose_index)
                            REFERENCES control_bearing_poses(map_id, bearing_index, pose_index) ON DELETE CASCADE,
                        FOREIGN KEY (map_id, unit_index)
                            REFERENCES propulsion_units(map_id, unit_index) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS control_bearing_aerodynamic_surfaces (
                        map_id TEXT NOT NULL,
                        bearing_index INTEGER NOT NULL,
                        pose_index INTEGER NOT NULL,
                        surface_index INTEGER NOT NULL,
                        sublevel_id TEXT NOT NULL,
                        block_x INTEGER NOT NULL,
                        block_y INTEGER NOT NULL,
                        block_z INTEGER NOT NULL,
                        block_id TEXT NOT NULL,
                        root_x REAL NOT NULL,
                        root_y REAL NOT NULL,
                        root_z REAL NOT NULL,
                        normal_x REAL NOT NULL,
                        normal_y REAL NOT NULL,
                        normal_z REAL NOT NULL,
                        parallel_drag_scalar REAL NOT NULL,
                        directionless_drag_scalar REAL NOT NULL,
                        lift_scalar REAL NOT NULL,
                        PRIMARY KEY (map_id, bearing_index, pose_index, surface_index),
                        FOREIGN KEY (map_id, bearing_index, pose_index)
                            REFERENCES control_bearing_poses(map_id, bearing_index, pose_index)
                            ON DELETE CASCADE
                    )
                    """);
            // ------------------------------------LOOKUP INDEXES------------------------------------
            statement.execute("CREATE INDEX IF NOT EXISTS propulsion_units_sublevel ON propulsion_units(sublevel_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS control_docking_connectors_sublevel "
                    + "ON control_docking_connectors(sublevel_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS control_crn_displays_sublevel "
                    + "ON control_crn_displays(sublevel_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS control_acc_displays_sublevel "
                    + "ON control_acc_displays(sublevel_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS control_bearings_host ON control_bearings(host_sublevel_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS control_bearing_children_sublevel "
                    + "ON control_bearing_children(child_sublevel_id)");
        }
        // -----------------------------------------------------SCHEMA UPGRADES---------------------------------------------------
        ensureColumn(connection, "propulsion_samples", "min_control",
                "REAL NOT NULL DEFAULT 0.0");
        ensureColumn(connection, "propulsion_samples", "max_control",
                "REAL NOT NULL DEFAULT 1.0");
        ensureColumn(connection, "control_bearing_poses", "max_aerodynamic_force",
                "REAL NOT NULL DEFAULT 0.0");
        ensureColumn(connection, "control_bearing_poses", "max_aerodynamic_torque",
                "REAL NOT NULL DEFAULT 0.0");
    }

    // Ensure the column
    private static void ensureColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet res = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (res.next()) {
                if (column.equalsIgnoreCase(res.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    // Update the map
    private static void upsertMap(Connection connection, ShipControlMap map) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ship_maps (
                    map_id, dimension, root_sublevel_id, controller_x, controller_y, controller_z,
                    center_x, center_y, center_z, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(map_id) DO UPDATE SET
                    dimension=excluded.dimension,
                    root_sublevel_id=excluded.root_sublevel_id,
                    controller_x=excluded.controller_x,
                    controller_y=excluded.controller_y,
                    controller_z=excluded.controller_z,
                    center_x=excluded.center_x,
                    center_y=excluded.center_y,
                    center_z=excluded.center_z,
                    updated_at=excluded.updated_at
                """)) {
            statement.setString(1, map.id().toString());
            statement.setString(2, map.dimension());
            statement.setString(3, map.rootSubLevelId().toString());
            statement.setInt(4, map.controllerPosition().getX());
            statement.setInt(5, map.controllerPosition().getY());
            statement.setInt(6, map.controllerPosition().getZ());
            statement.setDouble(7, map.centerOfMass().x);
            statement.setDouble(8, map.centerOfMass().y);
            statement.setDouble(9, map.centerOfMass().z);
            statement.setLong(10, map.updatedAt());
            statement.executeUpdate();
        }
    }

    // Replace the units
    private static void replaceUnits(Connection connection, ShipControlMap map) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM control_acc_displays WHERE map_id = ?")) {
            delete.setString(1, map.id().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM control_crn_displays WHERE map_id = ?")) {
            delete.setString(1, map.id().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM control_docking_connectors WHERE map_id = ?")) {
            delete.setString(1, map.id().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM control_bearings WHERE map_id = ?")) {
            delete.setString(1, map.id().toString());
            delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement(
                // ------------------------------------EXISTING MAP ROWS------------------------------------
                "DELETE FROM propulsion_units WHERE map_id = ?")) {
            delete.setString(1, map.id().toString());
            delete.executeUpdate();
        }
        // ------------------------------------PROPULSION UNITS------------------------------------
        try (PreparedStatement unitStatement = connection.prepareStatement("""
                INSERT INTO propulsion_units (
                    map_id, unit_index, sublevel_id, block_x, block_y, block_z, block_id, adapter,
                    controllable, root_x, root_y, root_z, force_x, force_y, force_z,
                    min_control, max_control, min_thrust, max_thrust, max_speed
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement sampleStatement = connection.prepareStatement("""
                INSERT INTO propulsion_samples (
                    map_id, unit_index, sample_index, min_control, max_control,
                    control, speed, thrust, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ShipControlMap.PropulsionUnit unit : map.units()) {
                unitStatement.setString(1, map.id().toString());
                unitStatement.setInt(2, unit.index());
                unitStatement.setString(3, unit.subLevelId().toString());
                unitStatement.setInt(4, unit.blockPosition().getX());
                unitStatement.setInt(5, unit.blockPosition().getY());
                unitStatement.setInt(6, unit.blockPosition().getZ());
                unitStatement.setString(7, unit.blockId());
                unitStatement.setString(8, unit.adapter());
                unitStatement.setInt(9, unit.controllable() ? 1 : 0);
                unitStatement.setDouble(10, unit.rootPosition().x);
                unitStatement.setDouble(11, unit.rootPosition().y);
                unitStatement.setDouble(12, unit.rootPosition().z);
                unitStatement.setDouble(13, unit.forceDirection().x);
                unitStatement.setDouble(14, unit.forceDirection().y);
                unitStatement.setDouble(15, unit.forceDirection().z);
                unitStatement.setDouble(16, unit.minControl());
                unitStatement.setDouble(17, unit.maxControl());
                unitStatement.setDouble(18, unit.minThrust());
                unitStatement.setDouble(19, unit.maxThrust());
                unitStatement.setDouble(20, unit.maxSpeed());
                unitStatement.addBatch();

                for (int sampleIndex = 0; sampleIndex < unit.samples().size(); sampleIndex++) {
                    ShipControlMap.CalibrationSample sample = unit.samples().get(sampleIndex);
                    sampleStatement.setString(1, map.id().toString());
                    sampleStatement.setInt(2, unit.index());
                    sampleStatement.setInt(3, sampleIndex);
                    sampleStatement.setDouble(4, sample.minControl());
                    sampleStatement.setDouble(5, sample.maxControl());
                    sampleStatement.setDouble(6, sample.control());
                    sampleStatement.setDouble(7, sample.speed());
                    sampleStatement.setDouble(8, sample.thrust());
                    sampleStatement.setInt(9, sample.active() ? 1 : 0);
                    sampleStatement.addBatch();
                }
            }
            unitStatement.executeBatch();
            sampleStatement.executeBatch();
        }
        // ------------------------------------MAP COMPONENTS------------------------------------
        insertDockingConnectors(connection, map);
        insertCrnDisplays(connection, map);
        insertAccDisplays(connection, map);
        insertBearings(connection, map);
    }

    // Insert the docking connectors
    private static void insertDockingConnectors(Connection connection, ShipControlMap map)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO control_docking_connectors (
                    map_id, connector_index, sublevel_id, block_x, block_y, block_z,
                    root_tip_x, root_tip_y, root_tip_z,
                    root_facing_x, root_facing_y, root_facing_z
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ShipControlMap.DockingConnector connector : map.dockingConnectors()) {
                statement.setString(1, map.id().toString());
                statement.setInt(2, connector.index());
                statement.setString(3, connector.subLevelId().toString());
                statement.setInt(4, connector.blockPosition().getX());
                statement.setInt(5, connector.blockPosition().getY());
                statement.setInt(6, connector.blockPosition().getZ());
                statement.setDouble(7, connector.rootTipPosition().x);
                statement.setDouble(8, connector.rootTipPosition().y);
                statement.setDouble(9, connector.rootTipPosition().z);
                statement.setDouble(10, connector.rootFacing().x);
                statement.setDouble(11, connector.rootFacing().y);
                statement.setDouble(12, connector.rootFacing().z);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // Insert the CRN displays
    private static void insertCrnDisplays(Connection connection, ShipControlMap map)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO control_crn_displays (
                    map_id, sublevel_id, block_x, block_y, block_z
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            for (ShipControlMap.CrnDisplay display : map.crnDisplays()) {
                statement.setString(1, map.id().toString());
                statement.setString(2, display.subLevelId().toString());
                statement.setInt(3, display.blockPosition().getX());
                statement.setInt(4, display.blockPosition().getY());
                statement.setInt(5, display.blockPosition().getZ());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // Insert the ACC displays
    private static void insertAccDisplays(Connection connection, ShipControlMap map)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO control_acc_displays (
                    map_id, sublevel_id, block_x, block_y, block_z
                ) VALUES (?, ?, ?, ?, ?)
                """)) {
            for (ShipControlMap.AccDisplay display : map.accDisplays()) {
                statement.setString(1, map.id().toString());
                statement.setString(2, display.subLevelId().toString());
                statement.setInt(3, display.blockPosition().getX());
                statement.setInt(4, display.blockPosition().getY());
                statement.setInt(5, display.blockPosition().getZ());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // Insert the bearings
    private static void insertBearings(Connection connection, ShipControlMap map) throws SQLException {
        // ------------------------------------BEARING STATEMENTS------------------------------------
        try (PreparedStatement bearingStatement = connection.prepareStatement("""
                INSERT INTO control_bearings (
                    map_id, bearing_index, host_sublevel_id, block_x, block_y, block_z,
                    block_id, adapter, min_x, max_x, min_z, max_z
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement childStatement = connection.prepareStatement("""
                INSERT INTO control_bearing_children (
                    map_id, bearing_index, child_index, child_sublevel_id
                ) VALUES (?, ?, ?, ?)
                """);
             PreparedStatement poseStatement = connection.prepareStatement("""
                INSERT INTO control_bearing_poses (
                    map_id, bearing_index, pose_index, angle_x, angle_z,
                    max_aerodynamic_force, max_aerodynamic_torque
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement responseStatement = connection.prepareStatement("""
                INSERT INTO control_bearing_responses (
                    map_id, bearing_index, pose_index, unit_index,
                    root_x, root_y, root_z, force_x, force_y, force_z, max_thrust
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement surfaceStatement = connection.prepareStatement("""
                INSERT INTO control_bearing_aerodynamic_surfaces (
                    map_id, bearing_index, pose_index, surface_index,
                    sublevel_id, block_x, block_y, block_z, block_id,
                    root_x, root_y, root_z, normal_x, normal_y, normal_z,
                    parallel_drag_scalar, directionless_drag_scalar, lift_scalar
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            String mapId = map.id().toString();
            // ------------------------------------BEARING ROWS------------------------------------
            for (ShipControlMap.BearingUnit bearing : map.bearings()) {
                bearingStatement.setString(1, mapId);
                bearingStatement.setInt(2, bearing.index());
                bearingStatement.setString(3, bearing.hostSubLevelId().toString());
                bearingStatement.setInt(4, bearing.blockPosition().getX());
                bearingStatement.setInt(5, bearing.blockPosition().getY());
                bearingStatement.setInt(6, bearing.blockPosition().getZ());
                bearingStatement.setString(7, bearing.blockId());
                bearingStatement.setString(8, bearing.adapter());
                bearingStatement.setDouble(9, bearing.minX());
                bearingStatement.setDouble(10, bearing.maxX());
                bearingStatement.setDouble(11, bearing.minZ());
                bearingStatement.setDouble(12, bearing.maxZ());
                bearingStatement.addBatch();

                // ------------------------------------CHILD SUB-LEVELS------------------------------------
                for (int childIndex = 0; childIndex < bearing.childSubLevelIds().size(); childIndex++) {
                    childStatement.setString(1, mapId);
                    childStatement.setInt(2, bearing.index());
                    childStatement.setInt(3, childIndex);
                    childStatement.setString(4, bearing.childSubLevelIds().get(childIndex).toString());
                    childStatement.addBatch();
                }
                // ------------------------------------POSE RESPONSES------------------------------------
                for (int poseIndex = 0; poseIndex < bearing.poses().size(); poseIndex++) {
                    ShipControlMap.BearingPose pose = bearing.poses().get(poseIndex);
                    poseStatement.setString(1, mapId);
                    poseStatement.setInt(2, bearing.index());
                    poseStatement.setInt(3, poseIndex);
                    poseStatement.setDouble(4, pose.angleX());
                    poseStatement.setDouble(5, pose.angleZ());
                    poseStatement.setDouble(6, pose.maxAerodynamicForce());
                    poseStatement.setDouble(7, pose.maxAerodynamicTorque());
                    poseStatement.addBatch();
                    for (ShipControlMap.BearingResponse resp : pose.responses()) {
                        responseStatement.setString(1, mapId);
                        responseStatement.setInt(2, bearing.index());
                        responseStatement.setInt(3, poseIndex);
                        responseStatement.setInt(4, resp.propulsionUnitIndex());
                        responseStatement.setDouble(5, resp.rootPosition().x);
                        responseStatement.setDouble(6, resp.rootPosition().y);
                        responseStatement.setDouble(7, resp.rootPosition().z);
                        responseStatement.setDouble(8, resp.forceDirection().x);
                        responseStatement.setDouble(9, resp.forceDirection().y);
                        responseStatement.setDouble(10, resp.forceDirection().z);
                        responseStatement.setDouble(11, resp.maxThrust());
                        responseStatement.addBatch();
                    }
                    for (ShipControlMap.AerodynamicSurface surface : pose.aerodynamicSurfaces()) {
                        surfaceStatement.setString(1, mapId);
                        surfaceStatement.setInt(2, bearing.index());
                        surfaceStatement.setInt(3, poseIndex);
                        surfaceStatement.setInt(4, surface.surfaceIndex());
                        surfaceStatement.setString(5, surface.subLevelId().toString());
                        surfaceStatement.setInt(6, surface.blockPosition().getX());
                        surfaceStatement.setInt(7, surface.blockPosition().getY());
                        surfaceStatement.setInt(8, surface.blockPosition().getZ());
                        surfaceStatement.setString(9, surface.blockId());
                        surfaceStatement.setDouble(10, surface.rootPosition().x);
                        surfaceStatement.setDouble(11, surface.rootPosition().y);
                        surfaceStatement.setDouble(12, surface.rootPosition().z);
                        surfaceStatement.setDouble(13, surface.normal().x);
                        surfaceStatement.setDouble(14, surface.normal().y);
                        surfaceStatement.setDouble(15, surface.normal().z);
                        surfaceStatement.setDouble(16, surface.parallelDragScalar());
                        surfaceStatement.setDouble(17, surface.directionlessDragScalar());
                        surfaceStatement.setDouble(18, surface.liftScalar());
                        surfaceStatement.addBatch();
                    }
                }
            }
            // ------------------------------------BATCH COMMIT------------------------------------
            bearingStatement.executeBatch();
            childStatement.executeBatch();
            poseStatement.executeBatch();
            responseStatement.executeBatch();
            surfaceStatement.executeBatch();
        }
    }

    // Load the header
    private static @Nullable ShipControlMap loadHeader(Connection connection, UUID mapId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT dimension, root_sublevel_id, controller_x, controller_y, controller_z,
                       center_x, center_y, center_z, updated_at
                FROM ship_maps WHERE map_id = ?
                """)) {
            statement.setString(1, mapId.toString());
            try (ResultSet res = statement.executeQuery()) {
                if (!res.next()) {
                    return null;
                }
                return new ShipControlMap(mapId, res.getString(1), UUID.fromString(res.getString(2)),
                        new BlockPos(res.getInt(3), res.getInt(4), res.getInt(5)),
                        new Vec3(res.getDouble(6), res.getDouble(7), res.getDouble(8)),
                        List.of(), res.getLong(9));
            }
        }
    }

    // Load the units
    private static List<ShipControlMap.PropulsionUnit> loadUnits(Connection connection, UUID mapId)
            throws SQLException {
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT unit_index, sublevel_id, block_x, block_y, block_z, block_id, adapter,
                       controllable, root_x, root_y, root_z, force_x, force_y, force_z,
                       min_control, max_control, min_thrust, max_thrust, max_speed
                FROM propulsion_units WHERE map_id = ? ORDER BY unit_index
                """)) {
            statement.setString(1, mapId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    int unitIndex = res.getInt(1);
                    units.add(new ShipControlMap.PropulsionUnit(unitIndex, UUID.fromString(res.getString(2)),
                            new BlockPos(res.getInt(3), res.getInt(4), res.getInt(5)),
                            res.getString(6), res.getString(7), res.getInt(8) != 0,
                            new Vec3(res.getDouble(9), res.getDouble(10), res.getDouble(11)),
                            new Vec3(res.getDouble(12), res.getDouble(13), res.getDouble(14)),
                            res.getDouble(15), res.getDouble(16), res.getDouble(17),
                            res.getDouble(18), res.getDouble(19),
                            loadSamples(connection, mapId, unitIndex)));
                }
            }
        }
        return units;
    }

    // Load the samples
    private static List<ShipControlMap.CalibrationSample> loadSamples(
            Connection connection, UUID mapId, int unitIndex) throws SQLException {
        List<ShipControlMap.CalibrationSample> samples = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT min_control, max_control, control, speed, thrust, active
                FROM propulsion_samples
                WHERE map_id = ? AND unit_index = ?
                ORDER BY sample_index
                """)) {
            statement.setString(1, mapId.toString());
            statement.setInt(2, unitIndex);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    samples.add(new ShipControlMap.CalibrationSample(
                            res.getDouble(1), res.getDouble(2),
                            res.getDouble(3), res.getDouble(4),
                            res.getDouble(5), res.getInt(6) != 0));
                }
            }
        }
        return samples;
    }

    // Load the bearings
    private static List<ShipControlMap.BearingUnit> loadBearings(Connection connection, UUID mapId)
            throws SQLException {
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT bearing_index, host_sublevel_id, block_x, block_y, block_z,
                       block_id, adapter, min_x, max_x, min_z, max_z
                FROM control_bearings WHERE map_id = ? ORDER BY bearing_index
                """)) {
            statement.setString(1, mapId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    int bearingIndex = res.getInt(1);
                    bearings.add(new ShipControlMap.BearingUnit(
                            bearingIndex,
                            UUID.fromString(res.getString(2)),
                            new BlockPos(res.getInt(3), res.getInt(4), res.getInt(5)),
                            res.getString(6), res.getString(7),
                            loadBearingChildren(connection, mapId, bearingIndex),
                            res.getDouble(8), res.getDouble(9),
                            res.getDouble(10), res.getDouble(11),
                            loadBearingPoses(connection, mapId, bearingIndex)));
                }
            }
        }
        return bearings;
    }

    // Load the docking connectors
    private static List<ShipControlMap.DockingConnector> loadDockingConnectors(
            Connection connection, UUID mapId
    ) throws SQLException {
        List<ShipControlMap.DockingConnector> connectors = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT connector_index, sublevel_id, block_x, block_y, block_z,
                       root_tip_x, root_tip_y, root_tip_z,
                       root_facing_x, root_facing_y, root_facing_z
                FROM control_docking_connectors
                WHERE map_id = ? ORDER BY connector_index
                """)) {
            statement.setString(1, mapId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    connectors.add(new ShipControlMap.DockingConnector(
                            res.getInt(1), UUID.fromString(res.getString(2)),
                            new BlockPos(res.getInt(3), res.getInt(4), res.getInt(5)),
                            new Vec3(res.getDouble(6), res.getDouble(7), res.getDouble(8)),
                            new Vec3(res.getDouble(9), res.getDouble(10), res.getDouble(11))));
                }
            }
        }
        return connectors;
    }

    // Load the CRN displays
    private static List<ShipControlMap.CrnDisplay> loadCrnDisplays(
            Connection connection, UUID mapId
    ) throws SQLException {
        List<ShipControlMap.CrnDisplay> displays = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sublevel_id, block_x, block_y, block_z
                FROM control_crn_displays
                WHERE map_id = ? ORDER BY sublevel_id, block_x, block_y, block_z
                """)) {
            statement.setString(1, mapId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    displays.add(new ShipControlMap.CrnDisplay(
                            UUID.fromString(res.getString(1)),
                            new BlockPos(res.getInt(2), res.getInt(3), res.getInt(4))));
                }
            }
        }
        return displays;
    }

    // Load the ACC displays
    private static List<ShipControlMap.AccDisplay> loadAccDisplays(
            Connection connection, UUID mapId
    ) throws SQLException {
        List<ShipControlMap.AccDisplay> displays = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sublevel_id, block_x, block_y, block_z
                FROM control_acc_displays
                WHERE map_id = ? ORDER BY sublevel_id, block_x, block_y, block_z
                """)) {
            statement.setString(1, mapId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    displays.add(new ShipControlMap.AccDisplay(
                            UUID.fromString(res.getString(1)),
                            new BlockPos(res.getInt(2), res.getInt(3), res.getInt(4))));
                }
            }
        }
        return displays;
    }

    // Load the bearing children
    private static List<UUID> loadBearingChildren(
            Connection connection, UUID mapId, int bearingIndex
    ) throws SQLException {
        List<UUID> children = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT child_sublevel_id FROM control_bearing_children
                WHERE map_id = ? AND bearing_index = ? ORDER BY child_index
                """)) {
            statement.setString(1, mapId.toString());
            statement.setInt(2, bearingIndex);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    children.add(UUID.fromString(res.getString(1)));
                }
            }
        }
        return children;
    }

    // Load the bearing poses
    private static List<ShipControlMap.BearingPose> loadBearingPoses(
            Connection connection, UUID mapId, int bearingIndex
    ) throws SQLException {
        List<ShipControlMap.BearingPose> poses = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pose_index, angle_x, angle_z,
                       max_aerodynamic_force, max_aerodynamic_torque
                FROM control_bearing_poses
                WHERE map_id = ? AND bearing_index = ? ORDER BY pose_index
                """)) {
            statement.setString(1, mapId.toString());
            statement.setInt(2, bearingIndex);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    int poseIndex = res.getInt(1);
                    poses.add(new ShipControlMap.BearingPose(
                            res.getDouble(2), res.getDouble(3),
                            loadBearingResponses(connection, mapId, bearingIndex, poseIndex),
                            loadAerodynamicSurfaces(
                                    connection, mapId, bearingIndex, poseIndex),
                            res.getDouble(4), res.getDouble(5)));
                }
            }
        }
        return poses;
    }

    // Load the bearing responses
    private static List<ShipControlMap.BearingResponse> loadBearingResponses(
            Connection connection, UUID mapId, int bearingIndex, int poseIndex
    ) throws SQLException {
        List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT unit_index, root_x, root_y, root_z,
                       force_x, force_y, force_z, max_thrust
                FROM control_bearing_responses
                WHERE map_id = ? AND bearing_index = ? AND pose_index = ?
                ORDER BY unit_index
                """)) {
            statement.setString(1, mapId.toString());
            statement.setInt(2, bearingIndex);
            statement.setInt(3, poseIndex);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    responses.add(new ShipControlMap.BearingResponse(
                            res.getInt(1),
                            new Vec3(res.getDouble(2), res.getDouble(3), res.getDouble(4)),
                            new Vec3(res.getDouble(5), res.getDouble(6), res.getDouble(7)),
                            res.getDouble(8)));
                }
            }
        }
        return responses;
    }

    // Load the aerodynamic surfaces
    private static List<ShipControlMap.AerodynamicSurface> loadAerodynamicSurfaces(
            Connection connection, UUID mapId, int bearingIndex, int poseIndex
    ) throws SQLException {
        List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT surface_index, sublevel_id, block_x, block_y, block_z, block_id,
                       root_x, root_y, root_z, normal_x, normal_y, normal_z,
                       parallel_drag_scalar, directionless_drag_scalar, lift_scalar
                FROM control_bearing_aerodynamic_surfaces
                WHERE map_id = ? AND bearing_index = ? AND pose_index = ?
                ORDER BY surface_index
                """)) {
            statement.setString(1, mapId.toString());
            statement.setInt(2, bearingIndex);
            statement.setInt(3, poseIndex);
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    surfaces.add(new ShipControlMap.AerodynamicSurface(
                            res.getInt(1), UUID.fromString(res.getString(2)),
                            new BlockPos(res.getInt(3), res.getInt(4), res.getInt(5)),
                            res.getString(6),
                            new Vec3(res.getDouble(7), res.getDouble(8), res.getDouble(9)),
                            new Vec3(res.getDouble(10), res.getDouble(11), res.getDouble(12)),
                            res.getDouble(13), res.getDouble(14), res.getDouble(15)));
                }
            }
        }
        return surfaces;
    }
}
