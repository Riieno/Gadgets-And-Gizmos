package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Store function drafts per controller alongside the rest of the world's saved data
public final class NotationDraftStore {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String DATABASE_NAME = "notation-plotter.db";
    public static final int MAX_DRAFTS = 64;
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private static final System.Logger LOGGER = System.getLogger(NotationDraftStore.class.getName());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the notation draft store
    private NotationDraftStore() {
    }

    // Store the summary
    public record Summary(String id, String name, long updatedAt) {
        // Initialize the summary
        public Summary {
            id = id == null ? "" : id;
            name = name == null || name.isBlank() ? "Untitled function" : name.strip();
            updatedAt = Math.max(0L, updatedAt);
        }
    }

    // Store save results
    public record SaveResult(boolean saved, String id, String message) {
        // Initialize the save result
        public SaveResult {
            id = id == null ? "" : id;
            message = message == null ? "" : message;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the list
    public static List<Summary> list(@Nullable Level level, String ownerId) {
        return listAt(databasePath(level), ownerId);
    }

    // Get the list
    static synchronized List<Summary> listAt(@Nullable Path path, String ownerId) {
        String owner = normalizeOwner(ownerId);
        if (owner.isBlank()) return List.of();
        try (Connection connection = open(path)) {
            if (connection == null) return List.of();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, updated_at
                    FROM notation_drafts
                    WHERE owner_id = ?
                    ORDER BY updated_at DESC, name COLLATE NOCASE ASC
                    LIMIT ?
                    """)) {
                statement.setString(1, owner);
                statement.setInt(2, MAX_DRAFTS);
                List<Summary> results = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        results.add(new Summary(rows.getString("id"), rows.getString("name"),
                                rows.getLong("updated_at")));
                    }
                }
                return List.copyOf(results);
            }
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not list function drafts", err);
            return List.of();
        }
    }

    // Load the notation draft store
    public static CompoundTag load(@Nullable Level level, String ownerId, String draftId) {
        return loadAt(databasePath(level), ownerId, draftId);
    }

    // Load the notation draft store
    static synchronized CompoundTag loadAt(@Nullable Path path, String ownerId, String draftId) {
        String owner = normalizeOwner(ownerId);
        String id = normalizeId(draftId);
        if (owner.isBlank() || id.isBlank()) return new CompoundTag();
        try (Connection connection = open(path)) {
            if (connection == null) return new CompoundTag();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT payload_nbt
                    FROM notation_drafts
                    WHERE owner_id = ? AND id = ?
                    """)) {
                statement.setString(1, owner);
                statement.setString(2, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? decode(rows.getBytes("payload_nbt")) : new CompoundTag();
                }
            }
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not load function draft " + id, err);
            return new CompoundTag();
        }
    }

    // Save the notation draft store
    public static SaveResult save(@Nullable Level level, String ownerId,
                                  String requestedId, CompoundTag requestedDraft) {
        return saveAt(databasePath(level), ownerId, requestedId, requestedDraft);
    }

    // Save the notation draft store
    static synchronized SaveResult saveAt(@Nullable Path path, String ownerId,
                                          String requestedId, CompoundTag requestedDraft) {
        String owner = normalizeOwner(ownerId);
        if (owner.isBlank()) return new SaveResult(false, "", "Controller storage is not ready");
        CompoundTag draft = requestedDraft == null ? new CompoundTag() : requestedDraft.copy();
        String id = normalizeId(requestedId);
        if (id.isBlank()) id = UUID.randomUUID().toString();
        String name = normalizeName(draft.getString("Name"));
        draft.putString("Id", id);
        draft.putString("Name", name);
        long updatedAt = System.currentTimeMillis();
        draft.putLong("UpdatedAt", updatedAt);
        byte[] payload = encode(draft);
        if (payload == null || payload.length > MAX_PAYLOAD_BYTES) {
            return new SaveResult(false, id, "Function draft is too large");
        }
        try (Connection connection = open(path)) {
            if (connection == null) return new SaveResult(false, id, "Function database is unavailable");
            try (PreparedStatement count = connection.prepareStatement(
                    "SELECT COUNT(*) FROM notation_drafts WHERE owner_id = ? AND id <> ?")) {
                count.setString(1, owner);
                count.setString(2, id);
                try (ResultSet res = count.executeQuery()) {
                    if (res.next() && res.getInt(1) >= MAX_DRAFTS) {
                        return new SaveResult(false, id, "This controller already has 64 function drafts");
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO notation_drafts(id, owner_id, name, payload_nbt, updated_at)
                    VALUES(?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        owner_id = excluded.owner_id,
                        name = excluded.name,
                        payload_nbt = excluded.payload_nbt,
                        updated_at = excluded.updated_at
                    WHERE notation_drafts.owner_id = excluded.owner_id
                    """)) {
                statement.setString(1, id);
                statement.setString(2, owner);
                statement.setString(3, name);
                statement.setBytes(4, payload);
                statement.setLong(5, updatedAt);
                int changed = statement.executeUpdate();
                return changed > 0
                        ? new SaveResult(true, id, "Saved function draft '" + name + "'")
                        : new SaveResult(false, id, "Draft identifier belongs to another controller");
            }
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save function draft " + id, err);
            return new SaveResult(false, id, "Could not save function draft");
        }
    }

    // Delete the notation draft store
    public static boolean delete(@Nullable Level level, String ownerId, String draftId) {
        return deleteAt(databasePath(level), ownerId, draftId);
    }

    // Delete the notation draft store
    static synchronized boolean deleteAt(@Nullable Path path, String ownerId, String draftId) {
        String owner = normalizeOwner(ownerId);
        String id = normalizeId(draftId);
        if (owner.isBlank() || id.isBlank()) return false;
        try (Connection connection = open(path)) {
            if (connection == null) return false;
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM notation_drafts WHERE owner_id = ? AND id = ?")) {
                statement.setString(1, owner);
                statement.setString(2, id);
                return statement.executeUpdate() > 0;
            }
        } catch (IOException | SQLException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not delete function draft " + id, err);
            return false;
        }
    }

    // Get the database path
    public static @Nullable Path databasePath(@Nullable Level level) {
        if (level == null || level.getServer() == null) return null;
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATABASE_NAME);
    }

    // Open the notation draft store
    private static @Nullable Connection open(@Nullable Path path) throws IOException, SQLException {
        if (path == null) return null;
        Files.createDirectories(path.getParent());
        Connection connection = SqliteDriverLoader.connect("jdbc:sqlite:" + path.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS notation_drafts (
                        id TEXT PRIMARY KEY,
                        owner_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        payload_nbt BLOB NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS notation_drafts_owner "
                    + "ON notation_drafts(owner_id, updated_at DESC)");
        }
        return connection;
    }

    // Encode the notation draft store
    private static byte @Nullable [] encode(CompoundTag tag) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag == null ? new CompoundTag() : tag, output);
            return output.toByteArray();
        } catch (IOException err) {
            return null;
        }
    }

    // Decode the notation draft store
    private static CompoundTag decode(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PAYLOAD_BYTES) return new CompoundTag();
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    NbtAccounter.create(8L * MAX_PAYLOAD_BYTES));
        } catch (IOException | RuntimeException err) {
            return new CompoundTag();
        }
    }

    // Normalize the owner
    private static String normalizeOwner(String ownerId) {
        String owner = ownerId == null ? "" : ownerId.strip();
        return owner.length() <= 192 ? owner : owner.substring(0, 192);
    }

    // Normalize the id
    private static String normalizeId(String draftId) {
        String id = draftId == null ? "" : draftId.strip();
        if (id.isBlank()) return "";
        try {
            return UUID.fromString(id).toString();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    // Normalize the name
    private static String normalizeName(String requested) {
        String name = requested == null ? "" : requested.strip();
        if (name.isBlank()) name = "Untitled function";
        return name.substring(0, Math.min(64, name.length()));
    }
}
