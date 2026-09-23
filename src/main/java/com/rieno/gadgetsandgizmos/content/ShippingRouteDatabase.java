package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.createmod.catnip.data.Glob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Store named shipping routes outside item NBT and keep their lookups local to one world
final class ShippingRouteDatabase {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final String DATABASE_NAME = "shipping_routes.db";
    private static final System.Logger LOGGER =
            System.getLogger(ShippingRouteDatabase.class.getName());
    private static final Set<Path> INITIALIZED_DATABASES =
            ConcurrentHashMap.newKeySet();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping route database
    private ShippingRouteDatabase() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Save the shipping route
    static boolean upsert(MinecraftServer server, ShipDockRegistry.Dock dock) {
        if (server == null || dock == null) {
            return false;
        }
        try (Connection connection = open(server)) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO ship_docks (
                             dock_id, address, address_key, dimension, sublevel_uuid,
                             local_x, local_y, local_z,
                             world_x, world_y, world_z,
                             facing_x, facing_y, facing_z,
                             connector_sublevel_uuid,
                             connector_local_x, connector_local_y, connector_local_z,
                             connector_world_x, connector_world_y, connector_world_z,
                             connector_facing_x, connector_facing_y, connector_facing_z,
                             refuel, restock, packages, updated_at
                         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         ON CONFLICT(dock_id) DO UPDATE SET
                             address=excluded.address,
                             address_key=excluded.address_key,
                             dimension=excluded.dimension,
                             sublevel_uuid=excluded.sublevel_uuid,
                             local_x=excluded.local_x,
                             local_y=excluded.local_y,
                             local_z=excluded.local_z,
                             world_x=excluded.world_x,
                             world_y=excluded.world_y,
                             world_z=excluded.world_z,
                             facing_x=excluded.facing_x,
                             facing_y=excluded.facing_y,
                             facing_z=excluded.facing_z,
                             connector_sublevel_uuid=excluded.connector_sublevel_uuid,
                             connector_local_x=excluded.connector_local_x,
                             connector_local_y=excluded.connector_local_y,
                             connector_local_z=excluded.connector_local_z,
                             connector_world_x=excluded.connector_world_x,
                             connector_world_y=excluded.connector_world_y,
                             connector_world_z=excluded.connector_world_z,
                             connector_facing_x=excluded.connector_facing_x,
                             connector_facing_y=excluded.connector_facing_y,
                             connector_facing_z=excluded.connector_facing_z,
                             refuel=excluded.refuel,
                             restock=excluded.restock,
                             packages=excluded.packages,
                             updated_at=excluded.updated_at
                         """)) {
                    bindDock(statement, dock);
                    statement.executeUpdate();
                }
                replaceConnectorTargets(connection, dock);
                replaceLandingZones(connection, dock);
                connection.commit();
                return true;
            } catch (SQLException err) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    err.addSuppressed(rollbackError);
                }
                throw err;
            }
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not update shipping dock " + dock.id(), err);
            return false;
        }
    }

    // Delete the shipping route database
    static void delete(MinecraftServer server, UUID dockId) {
        if (server == null || dockId == null) {
            return;
        }
        try (Connection connection = open(server);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM ship_docks WHERE dock_id = ?")) {
            statement.setString(1, dockId.toString());
            statement.executeUpdate();
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not remove shipping dock " + dockId, err);
        }
    }

    // Get the shipping route database value
    static @Nullable ShipDockRegistry.Dock get(MinecraftServer server, UUID dockId) {
        if (server == null || dockId == null) {
            return null;
        }
        try (Connection connection = open(server)) {
            ShipDockRegistry.Dock dock;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM ship_docks WHERE dock_id = ?")) {
                statement.setString(1, dockId.toString());
                try (ResultSet res = statement.executeQuery()) {
                    dock = res.next() ? readDockBase(res) : null;
                }
            }
            return dock == null ? null : readDockDetails(connection, dock);
        } catch (IOException | SQLException | RuntimeException err) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not look up shipping dock " + dockId, err);
            return null;
        }
    }

    // Get the matching
    static List<ShipDockRegistry.Dock> matching(
            MinecraftServer server, String dimension, String glob
    ) {
        String filter = glob == null || glob.isBlank() ? "*" : glob.trim();
        String regex = Glob.toRegexPattern(filter, "");
        String pattern = indexedPrefixLike(filter);
        return query(server, """
                SELECT * FROM ship_docks
                WHERE dimension = ? AND address_key LIKE ? ESCAPE '\\'
                ORDER BY address_key, dock_id
                """, statement -> {
            statement.setString(1, dimension);
            statement.setString(2, pattern);
        }).stream().filter(dock -> dock.name().matches(regex)).toList();
    }

    // Get the service docks
    static List<ShipDockRegistry.Dock> serviceDocks(
            MinecraftServer server, String dimension, String serviceColumn
    ) {
        String column = switch (serviceColumn) {
            case "refuel" -> "refuel";
            case "restock" -> "restock";
            case "packages" -> "packages";
            default -> "1";
        };
        return query(server, "SELECT * FROM ship_docks WHERE dimension = ? AND "
                + column + " = 1 ORDER BY address_key, dock_id", statement ->
                statement.setString(1, dimension));
    }

    // Load every dock in the dimension
    static List<ShipDockRegistry.Dock> allIn(MinecraftServer server, String dimension) {
        return query(server, """
                SELECT * FROM ship_docks
                WHERE dimension = ? ORDER BY address_key, dock_id
                """, statement -> statement.setString(1, dimension));
    }

    // Get all shipping route database values
    static List<ShipDockRegistry.Dock> all(MinecraftServer server) {
        return loadAll(server).docks();
    }

    // Load every persisted dock while reporting whether the backing store was actually readable.
    static DockLoad loadAll(MinecraftServer server) {
        if (server == null) return new DockLoad(List.of(), false);
        try (Connection connection = open(server)) {
            List<ShipDockRegistry.Dock> baseDocks = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT * FROM ship_docks
                    ORDER BY dimension, address_key, dock_id
                    """);
                 ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    try {
                        baseDocks.add(readDockBase(res));
                    } catch (RuntimeException | SQLException err) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Ignoring an invalid persisted ship dock record", err);
                    }
                }
            }
            // Child metadata is optional for destination validity. If a connector or landing-zone
            // row is damaged, retain the dock's durable identity and pose instead of making every
            // schedule which references it wait forever.
            List<ShipDockRegistry.Dock> docks = new ArrayList<>(baseDocks.size());
            for (ShipDockRegistry.Dock dock : baseDocks) {
                try {
                    docks.add(readDockDetails(connection, dock));
                } catch (RuntimeException | SQLException err) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Could not restore optional metadata for ship dock " + dock.id(), err);
                    docks.add(dock);
                }
            }
            return new DockLoad(List.copyOf(docks), true);
        } catch (IOException | SQLException | RuntimeException err) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not load the persisted ship dock registry", err);
            return new DockLoad(List.of(), false);
        }
    }

    // Store one complete persistent registry load.
    record DockLoad(List<ShipDockRegistry.Dock> docks, boolean complete) {
        DockLoad {
            docks = docks == null ? List.of() : List.copyOf(docks);
        }
    }

    // Query the shipping route database
    private static List<ShipDockRegistry.Dock> query(
            MinecraftServer server,
            String sql,
            StatementBinder binder
    ) {
        if (server == null) {
            return List.of();
        }
        List<ShipDockRegistry.Dock> docks = new ArrayList<>();
        try (Connection connection = open(server)) {
            List<ShipDockRegistry.Dock> baseDocks = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet res = statement.executeQuery()) {
                    while (res.next()) {
                        try {
                            baseDocks.add(readDockBase(res));
                        } catch (RuntimeException | SQLException err) {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "Ignoring an invalid persisted ship dock record", err);
                        }
                    }
                }
            }
            for (ShipDockRegistry.Dock dock : baseDocks) {
                try {
                    docks.add(readDockDetails(connection, dock));
                } catch (RuntimeException | SQLException err) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Could not restore optional metadata for ship dock " + dock.id(), err);
                    docks.add(dock);
                }
            }
        } catch (IOException | SQLException | RuntimeException err) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not query shipping routes", err);
        }
        return List.copyOf(docks);
    }

    // Open the shipping route database
    private static Connection open(MinecraftServer server) throws IOException, SQLException {
        return open(server.getWorldPath(LevelResource.ROOT));
    }

    // Open the shipping route database
    static Connection open(Path worldRoot) throws IOException, SQLException {
        Path path = worldRoot.resolve("data").resolve(DATABASE_NAME)
                .toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        if (!Files.isRegularFile(path)) {
            INITIALIZED_DATABASES.remove(path);
        }
        Connection connection = SqliteDriverLoader.connect("jdbc:sqlite:" + path);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("PRAGMA foreign_keys = ON");
            }
            synchronized (INITIALIZED_DATABASES) {
                if (!INITIALIZED_DATABASES.contains(path)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA journal_mode = WAL");
                    }
                    installSchema(connection);
                    INITIALIZED_DATABASES.add(path);
                }
            }
            return connection;
        } catch (SQLException | RuntimeException err) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                err.addSuppressed(closeException);
            }
            throw err;
        }
    }

    // Install the schema
    private static void installSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ship_docks (
                        dock_id TEXT PRIMARY KEY,
                        address TEXT NOT NULL,
                        address_key TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        sublevel_uuid TEXT,
                        local_x INTEGER NOT NULL,
                        local_y INTEGER NOT NULL,
                        local_z INTEGER NOT NULL,
                        world_x REAL NOT NULL,
                        world_y REAL NOT NULL,
                        world_z REAL NOT NULL,
                        facing_x REAL NOT NULL,
                        facing_y REAL NOT NULL,
                        facing_z REAL NOT NULL,
                        connector_sublevel_uuid TEXT,
                        connector_local_x INTEGER,
                        connector_local_y INTEGER,
                        connector_local_z INTEGER,
                        connector_world_x REAL,
                        connector_world_y REAL,
                        connector_world_z REAL,
                        connector_facing_x REAL,
                        connector_facing_y REAL,
                        connector_facing_z REAL,
                        refuel INTEGER NOT NULL,
                        restock INTEGER NOT NULL,
                        packages INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ship_dock_connectors (
                        dock_id TEXT NOT NULL,
                        connector_order INTEGER NOT NULL,
                        sublevel_uuid TEXT,
                        local_x INTEGER NOT NULL,
                        local_y INTEGER NOT NULL,
                        local_z INTEGER NOT NULL,
                        world_x REAL NOT NULL,
                        world_y REAL NOT NULL,
                        world_z REAL NOT NULL,
                        facing_x REAL NOT NULL,
                        facing_y REAL NOT NULL,
                        facing_z REAL NOT NULL,
                        up_x REAL NOT NULL,
                        up_y REAL NOT NULL,
                        up_z REAL NOT NULL,
                        PRIMARY KEY (dock_id, connector_order),
                        FOREIGN KEY (dock_id) REFERENCES ship_docks(dock_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ship_dock_landing_zones (
                        dock_id TEXT NOT NULL,
                        zone_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        queue_order INTEGER NOT NULL,
                        airborne INTEGER NOT NULL DEFAULT 0,
                        local_min_x INTEGER NOT NULL,
                        local_min_y INTEGER NOT NULL,
                        local_min_z INTEGER NOT NULL,
                        local_max_x INTEGER NOT NULL,
                        local_max_y INTEGER NOT NULL,
                        local_max_z INTEGER NOT NULL,
                        world_center_x REAL NOT NULL,
                        world_center_y REAL NOT NULL,
                        world_center_z REAL NOT NULL,
                        world_up_x REAL NOT NULL,
                        world_up_y REAL NOT NULL,
                        world_up_z REAL NOT NULL,
                        world_min_x REAL NOT NULL,
                        world_min_y REAL NOT NULL,
                        world_min_z REAL NOT NULL,
                        world_max_x REAL NOT NULL,
                        world_max_y REAL NOT NULL,
                        world_max_z REAL NOT NULL,
                        PRIMARY KEY (dock_id, zone_id),
                        FOREIGN KEY (dock_id) REFERENCES ship_docks(dock_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS ship_docks_address "
                    + "ON ship_docks(dimension, address_key)");
            statement.execute("CREATE INDEX IF NOT EXISTS ship_docks_refuel "
                    + "ON ship_docks(dimension, refuel)");
            statement.execute("CREATE INDEX IF NOT EXISTS ship_docks_restock "
                    + "ON ship_docks(dimension, restock)");
            statement.execute("CREATE INDEX IF NOT EXISTS ship_docks_packages "
                    + "ON ship_docks(dimension, packages)");
            statement.execute("CREATE INDEX IF NOT EXISTS ship_docks_sublevel "
                    + "ON ship_docks(sublevel_uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS ship_dock_connectors_order "
                    + "ON ship_dock_connectors(dock_id, connector_order)");
            statement.execute("CREATE INDEX IF NOT EXISTS ship_dock_landing_zones_order "
                    + "ON ship_dock_landing_zones(dock_id, queue_order, zone_id)");
        }
        installLandingZoneAirborneColumn(connection);
    }

    // Replace every persisted connector target for one dock
    private static void replaceConnectorTargets(
            Connection connection,
            ShipDockRegistry.Dock dock
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ship_dock_connectors WHERE dock_id = ?")) {
            statement.setString(1, dock.id().toString());
            statement.executeUpdate();
        }
        if (dock.connectorTargets().isEmpty()) return;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ship_dock_connectors (
                    dock_id, connector_order, sublevel_uuid,
                    local_x, local_y, local_z,
                    world_x, world_y, world_z,
                    facing_x, facing_y, facing_z,
                    up_x, up_y, up_z
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (int idx = 0; idx < dock.connectorTargets().size(); idx++) {
                ShipDockRegistry.ConnectorTarget target = dock.connectorTargets().get(idx);
                statement.setString(1, dock.id().toString());
                statement.setInt(2, idx);
                nullableUuid(statement, 3, target.subLevelId());
                statement.setInt(4, target.pos().getX());
                statement.setInt(5, target.pos().getY());
                statement.setInt(6, target.pos().getZ());
                statement.setDouble(7, target.worldPosition().x);
                statement.setDouble(8, target.worldPosition().y);
                statement.setDouble(9, target.worldPosition().z);
                statement.setDouble(10, target.facing().x);
                statement.setDouble(11, target.facing().y);
                statement.setDouble(12, target.facing().z);
                statement.setDouble(13, target.up().x);
                statement.setDouble(14, target.up().y);
                statement.setDouble(15, target.up().z);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // Install the landing zone airborne column
    private static void installLandingZoneAirborneColumn(
            Connection connection
    ) throws SQLException {
        boolean hasAirborneColumn = false;
        try (Statement statement = connection.createStatement();
             ResultSet res = statement.executeQuery(
                     "PRAGMA table_info(ship_dock_landing_zones)")) {
            while (res.next()) {
                if ("airborne".equalsIgnoreCase(res.getString("name"))) {
                    hasAirborneColumn = true;
                    break;
                }
            }
        }
        if (hasAirborneColumn) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ship_dock_landing_zones "
                    + "ADD COLUMN airborne INTEGER NOT NULL DEFAULT 0");
        }
    }

    // Replace the landing zones
    private static void replaceLandingZones(
            Connection connection,
            ShipDockRegistry.Dock dock
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM ship_dock_landing_zones WHERE dock_id = ?")) {
            statement.setString(1, dock.id().toString());
            statement.executeUpdate();
        }
        if (dock.landingZones().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO ship_dock_landing_zones (
                    dock_id, zone_id, name, queue_order, airborne,
                    local_min_x, local_min_y, local_min_z,
                    local_max_x, local_max_y, local_max_z,
                    world_center_x, world_center_y, world_center_z,
                    world_up_x, world_up_y, world_up_z,
                    world_min_x, world_min_y, world_min_z,
                    world_max_x, world_max_y, world_max_z
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ShipDockRegistry.LandingZoneTarget zone : dock.landingZones()) {
                statement.setString(1, dock.id().toString());
                statement.setString(2, zone.id().toString());
                statement.setString(3, zone.name());
                statement.setInt(4, zone.queueOrder());
                statement.setInt(5, zone.airborne() ? 1 : 0);
                statement.setInt(6, zone.min().getX());
                statement.setInt(7, zone.min().getY());
                statement.setInt(8, zone.min().getZ());
                statement.setInt(9, zone.max().getX());
                statement.setInt(10, zone.max().getY());
                statement.setInt(11, zone.max().getZ());
                statement.setDouble(12, zone.worldCenter().x);
                statement.setDouble(13, zone.worldCenter().y);
                statement.setDouble(14, zone.worldCenter().z);
                statement.setDouble(15, zone.worldUp().x);
                statement.setDouble(16, zone.worldUp().y);
                statement.setDouble(17, zone.worldUp().z);
                statement.setDouble(18, zone.worldBounds().minX);
                statement.setDouble(19, zone.worldBounds().minY);
                statement.setDouble(20, zone.worldBounds().minZ);
                statement.setDouble(21, zone.worldBounds().maxX);
                statement.setDouble(22, zone.worldBounds().maxY);
                statement.setDouble(23, zone.worldBounds().maxZ);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    // Bind the dock
    private static void bindDock(PreparedStatement statement, ShipDockRegistry.Dock dock)
            throws SQLException {
        statement.setString(1, dock.id().toString());
        statement.setString(2, dock.name());
        statement.setString(3, dock.name().toLowerCase(java.util.Locale.ROOT));
        statement.setString(4, dock.dimension().toString());
        nullableUuid(statement, 5, dock.subLevelId());
        statement.setInt(6, dock.pos().getX());
        statement.setInt(7, dock.pos().getY());
        statement.setInt(8, dock.pos().getZ());
        statement.setDouble(9, dock.worldPosition().x);
        statement.setDouble(10, dock.worldPosition().y);
        statement.setDouble(11, dock.worldPosition().z);
        statement.setDouble(12, dock.facing().x);
        statement.setDouble(13, dock.facing().y);
        statement.setDouble(14, dock.facing().z);
        nullableUuid(statement, 15, dock.connectorSubLevelId());
        nullableInt(statement, 16, dock.connectorPos() == null ? null : dock.connectorPos().getX());
        nullableInt(statement, 17, dock.connectorPos() == null ? null : dock.connectorPos().getY());
        nullableInt(statement, 18, dock.connectorPos() == null ? null : dock.connectorPos().getZ());
        nullableDouble(statement, 19, dock.connectorWorldPosition(), Axis.X);
        nullableDouble(statement, 20, dock.connectorWorldPosition(), Axis.Y);
        nullableDouble(statement, 21, dock.connectorWorldPosition(), Axis.Z);
        nullableDouble(statement, 22, dock.connectorFacing(), Axis.X);
        nullableDouble(statement, 23, dock.connectorFacing(), Axis.Y);
        nullableDouble(statement, 24, dock.connectorFacing(), Axis.Z);
        statement.setInt(25, dock.refuel() ? 1 : 0);
        statement.setInt(26, dock.restock() ? 1 : 0);
        statement.setInt(27, dock.packages() ? 1 : 0);
        statement.setLong(28, dock.updatedAt());
    }

    // Read the durable dock identity and pose without opening nested result sets.
    private static ShipDockRegistry.Dock readDockBase(ResultSet res) throws SQLException {
        UUID dockId = UUID.fromString(res.getString("dock_id"));
        return new ShipDockRegistry.Dock(
                dockId,
                net.minecraft.resources.ResourceLocation.parse(res.getString("dimension")),
                nullableUuid(res, "sublevel_uuid"),
                new BlockPos(
                        res.getInt("local_x"), res.getInt("local_y"), res.getInt("local_z")),
                new Vec3(
                        res.getDouble("world_x"), res.getDouble("world_y"), res.getDouble("world_z")),
                new Vec3(
                        res.getDouble("facing_x"), res.getDouble("facing_y"), res.getDouble("facing_z")),
                res.getString("address"),
                res.getInt("refuel") != 0,
                res.getInt("restock") != 0,
                res.getInt("packages") != 0,
                nullableUuid(res, "connector_sublevel_uuid"),
                nullableBlockPos(res),
                nullableVec3(res, "connector_world_x", "connector_world_y", "connector_world_z"),
                nullableVec3(res, "connector_facing_x", "connector_facing_y", "connector_facing_z"),
                res.getLong("updated_at"));
    }

    // Attach optional connector and landing-zone metadata after the primary row cursor closes.
    private static ShipDockRegistry.Dock readDockDetails(
            Connection connection,
            ShipDockRegistry.Dock base
    ) throws SQLException {
        UUID dockId = base.id();
        ShipDockRegistry.Dock dock = base;
        List<ShipDockRegistry.ConnectorTarget> connectorTargets =
                readConnectorTargets(connection, dockId);
        if (!connectorTargets.isEmpty()) {
            ShipDockRegistry.ConnectorTarget primary = connectorTargets.getFirst();
            dock = new ShipDockRegistry.Dock(
                    dock.id(), dock.dimension(), dock.subLevelId(), dock.pos(),
                    dock.worldPosition(), dock.facing(), dock.name(), dock.refuel(),
                    dock.restock(), dock.packages(), primary.subLevelId(), primary.pos(),
                    primary.worldPosition(), primary.facing(), primary.up(), dock.updatedAt(),
                    connectorTargets, List.of());
        }
        return dock.withLandingZones(readLandingZones(connection, dockId));
    }

    // Read every persisted connector target for one dock
    private static List<ShipDockRegistry.ConnectorTarget> readConnectorTargets(
            Connection connection,
            UUID dockId
    ) throws SQLException {
        List<ShipDockRegistry.ConnectorTarget> targets = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM ship_dock_connectors
                WHERE dock_id = ? ORDER BY connector_order
                """)) {
            statement.setString(1, dockId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    targets.add(new ShipDockRegistry.ConnectorTarget(
                            nullableUuid(res, "sublevel_uuid"),
                            new BlockPos(res.getInt("local_x"), res.getInt("local_y"),
                                    res.getInt("local_z")),
                            new Vec3(res.getDouble("world_x"), res.getDouble("world_y"),
                                    res.getDouble("world_z")),
                            new Vec3(res.getDouble("facing_x"), res.getDouble("facing_y"),
                                    res.getDouble("facing_z")),
                            new Vec3(res.getDouble("up_x"), res.getDouble("up_y"),
                                    res.getDouble("up_z"))));
                }
            }
        }
        return List.copyOf(targets);
    }

    // Read the landing zones
    private static List<ShipDockRegistry.LandingZoneTarget> readLandingZones(
            Connection connection,
            UUID dockId
    ) throws SQLException {
        List<ShipDockRegistry.LandingZoneTarget> zones = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM ship_dock_landing_zones
                WHERE dock_id = ? ORDER BY queue_order, zone_id
                """)) {
            statement.setString(1, dockId.toString());
            try (ResultSet res = statement.executeQuery()) {
                while (res.next()) {
                    zones.add(new ShipDockRegistry.LandingZoneTarget(
                            UUID.fromString(res.getString("zone_id")),
                            res.getString("name"),
                            new BlockPos(
                                    res.getInt("local_min_x"),
                                    res.getInt("local_min_y"),
                                    res.getInt("local_min_z")),
                            new BlockPos(
                                    res.getInt("local_max_x"),
                                    res.getInt("local_max_y"),
                                    res.getInt("local_max_z")),
                            res.getInt("queue_order"),
                            res.getInt("airborne") != 0,
                            new Vec3(
                                    res.getDouble("world_center_x"),
                                    res.getDouble("world_center_y"),
                                    res.getDouble("world_center_z")),
                            new Vec3(
                                    res.getDouble("world_up_x"),
                                    res.getDouble("world_up_y"),
                                    res.getDouble("world_up_z")),
                            new AABB(
                                    res.getDouble("world_min_x"),
                                    res.getDouble("world_min_y"),
                                    res.getDouble("world_min_z"),
                                    res.getDouble("world_max_x"),
                                    res.getDouble("world_max_y"),
                                    res.getDouble("world_max_z"))));
                }
            }
        }
        return List.copyOf(zones);
    }

    // Get the nullable block pos
    private static @Nullable net.minecraft.core.BlockPos nullableBlockPos(ResultSet res)
            throws SQLException {
        int x = res.getInt("connector_local_x");
        if (res.wasNull()) {
            return null;
        }
        return new net.minecraft.core.BlockPos(
                x, res.getInt("connector_local_y"), res.getInt("connector_local_z"));
    }

    // Read an optional position vector
    private static @Nullable net.minecraft.world.phys.Vec3 nullableVec3(
            ResultSet res, String xColumn, String yColumn, String zColumn
    ) throws SQLException {
        double x = res.getDouble(xColumn);
        if (res.wasNull()) {
            return null;
        }
        return new net.minecraft.world.phys.Vec3(
                x, res.getDouble(yColumn), res.getDouble(zColumn));
    }

    // Handle the nullable UUID
    private static void nullableUuid(PreparedStatement statement, int idx, @Nullable UUID val)
            throws SQLException {
        if (val == null) {
            statement.setNull(idx, java.sql.Types.VARCHAR);
        } else {
            statement.setString(idx, val.toString());
        }
    }

    // Get the nullable UUID
    private static @Nullable UUID nullableUuid(ResultSet res, String column) throws SQLException {
        String val = res.getString(column);
        return val == null || val.isBlank() ? null : UUID.fromString(val);
    }

    // Handle the nullable int
    private static void nullableInt(PreparedStatement statement, int idx, @Nullable Integer val)
            throws SQLException {
        if (val == null) {
            statement.setNull(idx, java.sql.Types.INTEGER);
        } else {
            statement.setInt(idx, val);
        }
    }

    // Handle the nullable double
    private static void nullableDouble(
            PreparedStatement statement,
            int idx,
            @Nullable net.minecraft.world.phys.Vec3 val,
            Axis axis
    ) throws SQLException {
        if (val == null) {
            statement.setNull(idx, java.sql.Types.REAL);
        } else {
            statement.setDouble(idx, axis.value(val));
        }
    }

    // Get the indexed prefix like
    static String indexedPrefixLike(String glob) {
        StringBuilder prefix = new StringBuilder();
        boolean escaped = false;
        for (int idx = 0; idx < glob.length(); idx++) {
            char character = glob.charAt(idx);
            if (!escaped && character == '\\') {
                escaped = true;
                continue;
            }
            if (!escaped && (character == '*' || character == '?'
                    || character == '[' || character == '{')) {
                break;
            }
            char normalized = Character.toLowerCase(character);
            if (normalized == '%' || normalized == '_' || normalized == '\\') {
                prefix.append('\\');
            }
            prefix.append(normalized);
            escaped = false;
        }
        return prefix.append('%').toString();
    }

    // Expose the statement binder
    @FunctionalInterface
    private interface StatementBinder {
        // Bind the statement binder
        void bind(PreparedStatement statement) throws SQLException;
    }

    // Define the axis values
    private enum Axis {
        X {
            // Get the value
            @Override
            double value(net.minecraft.world.phys.Vec3 vector) {
                return vector.x;
            }
        },
        Y {
            // Get the value
            @Override
            double value(net.minecraft.world.phys.Vec3 vector) {
                return vector.y;
            }
        },
        Z {
            // Get the value
            @Override
            double value(net.minecraft.world.phys.Vec3 vector) {
                return vector.z;
            }
        };

        // Get the value
        abstract double value(net.minecraft.world.phys.Vec3 vector);
    }
}
