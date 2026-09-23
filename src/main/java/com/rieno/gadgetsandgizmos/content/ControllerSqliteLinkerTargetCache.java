package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// Cache linker targets per database connection
final class ControllerSqliteLinkerTargetCache {
    // Tracked entries
    private final Map<Connection, Map<String, ControllerSqliteStore.LinkerTargets>> entries = new IdentityHashMap<>();

    // Get the linker target cache value
    @Nullable ControllerSqliteStore.LinkerTargets get(Connection connection, String manifestId) {
        Map<String, ControllerSqliteStore.LinkerTargets> connectionEntries = entries.get(connection);
        return connectionEntries == null ? null : connectionEntries.get(manifestId);
    }

    // Put the linker target cache
    void put(Connection connection, String manifestId, ControllerSqliteStore.LinkerTargets targets) {
        entries.computeIfAbsent(connection, ignored -> new LinkedHashMap<>()).put(manifestId, targets);
    }

    // Load or cache linker targets
    ControllerSqliteStore.LinkerTargets getOrLoad(Connection connection,
                                                   String manifestId,
                                                   ControllerSqliteStore.LinkerTargetLoader loader) throws SQLException {
        ControllerSqliteStore.LinkerTargets cached = get(connection, manifestId);
        if (cached != null) {
            return cached;
        }
        ControllerSqliteStore.LinkerTargets loaded = loader.load();
        put(connection, manifestId, loaded);
        return loaded;
    }

    // Invalidate the linker target cache
    void invalidate(Connection connection, String manifestId) {
        Map<String, ControllerSqliteStore.LinkerTargets> connectionEntries = entries.get(connection);
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
        Map<String, ControllerSqliteStore.LinkerTargets> connectionEntries = entries.get(connection);
        return connectionEntries == null ? 0 : connectionEntries.size();
    }
}
