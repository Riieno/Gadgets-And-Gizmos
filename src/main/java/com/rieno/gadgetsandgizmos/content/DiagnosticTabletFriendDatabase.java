package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;

// Store trusted tablet users and keep friendship checks cached per server
public final class DiagnosticTabletFriendDatabase {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String DATABASE_NAME = "tablet_friends.db";
    private static final System.Logger LOGGER = System.getLogger(
            DiagnosticTabletFriendDatabase.class.getName());
    private static final Map<UUID, List<FriendRef>> FRIENDS = new LinkedHashMap<>();
    private static final Map<UUID, List<RequestRef>> REQUESTS = new LinkedHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cached server
    private static MinecraftServer cachedServer;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet friend database
    private DiagnosticTabletFriendDatabase() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server started event
    public static synchronized void onServerStarted(ServerStartedEvent evt) {
        loadCache(evt.getServer());
    }

    // Handle the server stopped event
    public static synchronized void onServerStopped(ServerStoppedEvent evt) {
        if (cachedServer == evt.getServer()) {
            cachedServer = null;
            FRIENDS.clear();
            REQUESTS.clear();
        }
    }

    // Handle the diagnostic tablet friend database
    public static TabletActionHandler.Result handle(TabletActionContext ctx, TabletAction action) {
        ServerPlayer player = ctx.player();
        String targetName = action.arguments().getOrDefault("value", "").trim();
        TabletActionHandler.Result res = switch (action.actionId()) {
            case "add_friend" -> request(player, targetName);
            case "accept_friend" -> accept(player, targetName);
            case "remove_friend" -> remove(player, targetName);
            case "navigate_friend" -> navigateToFriend(ctx, targetName);
            case "refresh" -> success("block360 refreshed");
            default -> TabletActionHandler.Result.failure(Component.literal("Unknown block360 action"));
        };
        sendSnapshot(ctx);
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Navigate the friend
    private static TabletActionHandler.Result navigateToFriend(TabletActionContext ctx,
                                                                String friendId) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return failure("The tablet identity is not available");
        ensureLoaded(ctx.player().server);
        FriendRef match;
        synchronized (DiagnosticTabletFriendDatabase.class) {
            match = FRIENDS.getOrDefault(ctx.player().getUUID(), List.of()).stream()
                    .filter(friend -> friend.uuid().toString().equals(friendId)
                            || friend.name().equalsIgnoreCase(friendId))
                    .findFirst().orElse(null);
        }
        ServerPlayer friend = match == null ? null
                : ctx.player().server.getPlayerList().getPlayer(match.uuid());
        if (friend == null) return failure("That friend is not online");
        CompoundTag location = new CompoundTag();
        location.putUUID("Player", friend.getUUID());
        location.putString("Name", friend.getGameProfile().getName());
        location.putString("Dimension", friend.level().dimension().location().toString());
        location.putInt("X", friend.blockPosition().getX());
        location.putInt("Y", friend.blockPosition().getY());
        location.putInt("Z", friend.blockPosition().getZ());
        TabletStorageApi.storage().updateShared(tabletId,
                TabletAppRegistry.definition(DiagnosticTabletData.appId("block360")),
                "friend_location", prev -> location);
        DiagnosticTabletData.Binding binding = DiagnosticTabletAppStorage.selectedBinding(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("scm"));
        if (binding == null) return failure("Select a ship in Ship Control first");
        net.minecraft.world.level.block.entity.BlockEntity target =
                SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                        binding.subLevelId(), binding.pos());
        TabletActionContext shipContext = new TabletActionContext(ctx.player(), ctx.tablet(),
                binding.subLevelId(), binding.pos(), ctx.placedSource(), tabletId,
                ctx.sourceSubLevelId(), ctx.sourceBlockPos());
        return DiagnosticTabletScmActions.execute(shipContext,
                new TabletAction(DiagnosticTabletData.appId("scm"), "ships", "navigate",
                        Map.of("value", friend.blockPosition().getX() + ","
                                + friend.blockPosition().getY() + ","
                                + friend.blockPosition().getZ())), target);
    }

    // Request the diagnostic tablet friend database
    public static TabletActionHandler.Result request(ServerPlayer requester, ServerPlayer target) {
        TabletActionHandler.Result res = target == null ? failure("Player is not online")
                : request(requester, target.getGameProfile().getName());
        sendSnapshot(requester);
        return res;
    }

    // Request the diagnostic tablet friend database
    private static TabletActionHandler.Result request(ServerPlayer requester, String targetName) {
        ServerPlayer target = requester.server.getPlayerList().getPlayerByName(targetName);
        if (target == null || target.getUUID().equals(requester.getUUID())) {
            return failure("Choose another online player");
        }
        try (Connection connection = open(requester.server);
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO friend_requests(requester, target, requester_name, target_name)
                     VALUES(?, ?, ?, ?)
                     ON CONFLICT(requester, target) DO UPDATE SET requester_name=excluded.requester_name,
                     target_name=excluded.target_name
                     """)) {
            statement.setString(1, requester.getUUID().toString());
            statement.setString(2, target.getUUID().toString());
            statement.setString(3, requester.getGameProfile().getName());
            statement.setString(4, target.getGameProfile().getName());
            statement.executeUpdate();
            synchronized (DiagnosticTabletFriendDatabase.class) {
                ensureLoaded(requester.server);
                List<RequestRef> requests = new ArrayList<>(REQUESTS.getOrDefault(
                        target.getUUID(), List.of()));
                requests.removeIf(req -> req.requester().equals(requester.getUUID()));
                requests.add(new RequestRef(requester.getUUID(),
                        requester.getGameProfile().getName()));
                REQUESTS.put(target.getUUID(), sortedRequests(requests));
            }
            sendSnapshot(target);
            return success("Friend request sent");
        } catch (IOException | SQLException err) {
            return databaseFailure(err);
        }
    }

    // Accept the diagnostic tablet friend database
    private static TabletActionHandler.Result accept(ServerPlayer target, String requesterName) {
        ensureLoaded(target.server);
        RequestRef req;
        synchronized (DiagnosticTabletFriendDatabase.class) {
            req = REQUESTS.getOrDefault(target.getUUID(), List.of()).stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(requesterName)
                            || candidate.requester().toString().equals(requesterName))
                    .findFirst().orElse(null);
        }
        if (req == null) return failure("No matching friend request");
        UUID requester = req.requester();
        ServerPlayer requesterPlayer = target.server.getPlayerList().getPlayer(requester);
        String requesterDisplayName = requesterPlayer == null ? req.name()
                : requesterPlayer.getGameProfile().getName();
        String first = requester.compareTo(target.getUUID()) < 0
                ? requester.toString() : target.getUUID().toString();
        String second = requester.compareTo(target.getUUID()) < 0
                ? target.getUUID().toString() : requester.toString();
        try (Connection connection = open(target.server);
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO friendships(first_player, second_player, first_name, second_name)
                     VALUES(?, ?, ?, ?)
                     ON CONFLICT(first_player, second_player) DO UPDATE SET
                     first_name=excluded.first_name, second_name=excluded.second_name
                     """);
             PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM friend_requests WHERE requester=? AND target=?")) {
            insert.setString(1, first);
            insert.setString(2, second);
            insert.setString(3, first.equals(requester.toString())
                    ? requesterDisplayName : target.getGameProfile().getName());
            insert.setString(4, second.equals(requester.toString())
                    ? requesterDisplayName : target.getGameProfile().getName());
            insert.executeUpdate();
            delete.setString(1, requester.toString());
            delete.setString(2, target.getUUID().toString());
            delete.executeUpdate();
            synchronized (DiagnosticTabletFriendDatabase.class) {
                List<RequestRef> pending = new ArrayList<>(REQUESTS.getOrDefault(
                        target.getUUID(), List.of()));
                pending.removeIf(candidate -> candidate.requester().equals(requester));
                REQUESTS.put(target.getUUID(), List.copyOf(pending));
                putFriend(requester, target.getUUID(), target.getGameProfile().getName());
                putFriend(target.getUUID(), requester, requesterDisplayName);
            }
            if (requesterPlayer != null) {
                sendSnapshot(requesterPlayer);
            }
            return success("Friend request accepted");
        } catch (IOException | SQLException err) {
            return databaseFailure(err);
        }
    }

    // Remove the diagnostic tablet friend database
    private static TabletActionHandler.Result remove(ServerPlayer player, String friendName) {
        ensureLoaded(player.server);
        FriendRef match;
        synchronized (DiagnosticTabletFriendDatabase.class) {
            match = FRIENDS.getOrDefault(player.getUUID(), List.of()).stream()
                    .filter(friend -> friend.uuid().toString().equals(friendName)
                            || friend.name().equalsIgnoreCase(friendName))
                    .findFirst().orElse(null);
        }
        if (match == null) return failure("No matching friend");
        try (Connection connection = open(player.server);
             PreparedStatement statement = connection.prepareStatement(
                     """
                     DELETE FROM friendships WHERE
                     (first_player=? AND second_player=?) OR
                     (second_player=? AND first_player=?)
                     """)) {
            String playerId = player.getUUID().toString();
            statement.setString(1, playerId);
            statement.setString(2, match.uuid().toString());
            statement.setString(3, playerId);
            statement.setString(4, match.uuid().toString());
            if (statement.executeUpdate() == 0) return failure("No matching friend");
            synchronized (DiagnosticTabletFriendDatabase.class) {
                removeFriend(player.getUUID(), match.uuid());
                removeFriend(match.uuid(), player.getUUID());
            }
            return success("Friend removed");
        } catch (IOException | SQLException err) {
            return databaseFailure(err);
        }
    }

    // Get the friends
    public static List<ServerPlayer> friends(ServerPlayer player) {
        ensureLoaded(player.server);
        List<ServerPlayer> res = new ArrayList<>();
        List<FriendRef> cached;
        synchronized (DiagnosticTabletFriendDatabase.class) {
            cached = FRIENDS.getOrDefault(player.getUUID(), List.of());
        }
        for (FriendRef friend : cached) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(friend.uuid());
            if (online != null) res.add(online);
        }
        return List.copyOf(res);
    }

    // Send the snapshot
    public static void sendSnapshot(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("block360"), snapshot(player)));
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx) {
        CompoundTag snapshot = snapshot(ctx.player());
        if (ctx.sourceTabletId() != null) {
            snapshot.put("Settings", DiagnosticTabletAppStorage.data(ctx.player().server,
                    ctx.sourceTabletId(), DiagnosticTabletData.appId("block360"))
                    .getCompound("Settings").copy());
            CompoundTag shared = new CompoundTag();
            shared.put("Friends", snapshot.getList("Friends", net.minecraft.nbt.Tag.TAG_COMPOUND).copy());
            TabletStorageApi.storage().updateShared(ctx.sourceTabletId(),
                    TabletAppRegistry.definition(DiagnosticTabletData.appId("block360")),
                    "friend_location", prev -> shared);
        }
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("block360"), ctx, snapshot));
    }

    // Get the snapshot
    private static CompoundTag snapshot(ServerPlayer player) {
        ensureLoaded(player.server);
        CompoundTag snapshot = new CompoundTag();
        ListTag pending = new ListTag();
        ListTag friends = new ListTag();
        List<RequestRef> cachedRequests;
        List<FriendRef> cachedFriends;
        synchronized (DiagnosticTabletFriendDatabase.class) {
            cachedRequests = REQUESTS.getOrDefault(player.getUUID(), List.of());
            cachedFriends = FRIENDS.getOrDefault(player.getUUID(), List.of());
        }
        for (RequestRef req : cachedRequests) {
            CompoundTag row = new CompoundTag();
            row.putString("Uuid", req.requester().toString());
            row.putString("Name", req.name());
            pending.add(row);
        }
        for (FriendRef friend : cachedFriends) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(friend.uuid());
            CompoundTag row = new CompoundTag();
            row.putString("Uuid", friend.uuid().toString());
            row.putString("Name", online == null ? friend.name()
                    : online.getGameProfile().getName());
            row.putBoolean("Online", online != null);
            if (online != null) {
                row.putString("Dimension", online.level().dimension().location().toString());
                row.putInt("X", online.blockPosition().getX());
                row.putInt("Y", online.blockPosition().getY());
                row.putInt("Z", online.blockPosition().getZ());
            }
            friends.add(row);
        }
        snapshot.put("Pending", pending);
        snapshot.put("Friends", friends);
        return snapshot;
    }

    // Ensure the loaded
    private static synchronized void ensureLoaded(MinecraftServer server) {
        if (cachedServer != server) loadCache(server);
    }

    // Load the cache
    private static synchronized void loadCache(MinecraftServer server) {
        FRIENDS.clear();
        REQUESTS.clear();
        try (Connection connection = open(server);
             Statement statement = connection.createStatement();
             ResultSet requests = statement.executeQuery(
                     "SELECT requester, target, requester_name FROM friend_requests")) {
            while (requests.next()) {
                UUID requester = UUID.fromString(requests.getString("requester"));
                UUID target = UUID.fromString(requests.getString("target"));
                List<RequestRef> values = new ArrayList<>(REQUESTS.getOrDefault(target, List.of()));
                values.add(new RequestRef(requester, requests.getString("requester_name")));
                REQUESTS.put(target, sortedRequests(values));
            }
        } catch (IOException | SQLException | IllegalArgumentException err) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not cache block360 requests", err);
        }
        try (Connection connection = open(server);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT first_player, second_player, first_name, second_name FROM friendships")) {
            while (rows.next()) {
                UUID first = UUID.fromString(rows.getString("first_player"));
                UUID second = UUID.fromString(rows.getString("second_player"));
                putFriend(first, second, displayName(rows.getString("second_name"), second));
                putFriend(second, first, displayName(rows.getString("first_name"), first));
            }
            cachedServer = server;
        } catch (IOException | SQLException | IllegalArgumentException err) {
            cachedServer = server;
            LOGGER.log(System.Logger.Level.ERROR, "Could not cache block360 friendships", err);
        }
    }

    // Put the friend
    private static void putFriend(UUID owner, UUID friend, String name) {
        List<FriendRef> values = new ArrayList<>(FRIENDS.getOrDefault(owner, List.of()));
        values.removeIf(val -> val.uuid().equals(friend));
        values.add(new FriendRef(friend, displayName(name, friend)));
        values.sort(Comparator.comparing(FriendRef::name, String.CASE_INSENSITIVE_ORDER));
        FRIENDS.put(owner, List.copyOf(values));
    }

    // Remove the friend
    private static void removeFriend(UUID owner, UUID friend) {
        List<FriendRef> values = new ArrayList<>(FRIENDS.getOrDefault(owner, List.of()));
        values.removeIf(val -> val.uuid().equals(friend));
        FRIENDS.put(owner, List.copyOf(values));
    }

    // Get the sorted requests
    private static List<RequestRef> sortedRequests(List<RequestRef> values) {
        values.sort(Comparator.comparing(RequestRef::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(values);
    }

    // Get the diagnostic tablet friend database display name
    private static String displayName(String name, UUID fallback) {
        return name == null || name.isBlank() ? fallback.toString() : name;
    }

    // Store the friend ref
    private record FriendRef(UUID uuid, String name) {
    }

    // Store the request ref
    private record RequestRef(UUID requester, String name) {
    }

    // Open the diagnostic tablet friend database
    private static Connection open(MinecraftServer server) throws IOException, SQLException {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATABASE_NAME);
        Files.createDirectories(path.getParent());
        Connection connection = SqliteDriverLoader.connect("jdbc:sqlite:" + path.toAbsolutePath().normalize());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS friend_requests(
                    requester TEXT NOT NULL, target TEXT NOT NULL,
                    requester_name TEXT NOT NULL, target_name TEXT NOT NULL,
                    PRIMARY KEY(requester, target))
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS friendships(
                    first_player TEXT NOT NULL, second_player TEXT NOT NULL,
                    first_name TEXT, second_name TEXT,
                    PRIMARY KEY(first_player, second_player))
                    """);
            ensureColumn(connection, "friendships", "first_name", "TEXT");
            ensureColumn(connection, "friendships", "second_name", "TEXT");
        }
        return connection;
    }

    // Ensure the column
    private static void ensureColumn(Connection connection, String table,
                                     String column, String type) throws SQLException {
        try (Statement query = connection.createStatement();
             ResultSet columns = query.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) return;
            }
        }
        try (Statement alter = connection.createStatement()) {
            alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    // Get the database failure
    private static TabletActionHandler.Result databaseFailure(Exception err) {
        LOGGER.log(System.Logger.Level.ERROR, "block360 database operation failed", err);
        return failure("block360 database is unavailable");
    }

    // Create a successful diagnostic tablet friend database
    private static TabletActionHandler.Result success(String msg) {
        return TabletActionHandler.Result.success(Component.literal(msg));
    }

    // Create a failed diagnostic tablet friend database
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }
}
