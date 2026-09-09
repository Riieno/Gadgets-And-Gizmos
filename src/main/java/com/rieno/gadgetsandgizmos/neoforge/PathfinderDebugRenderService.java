package com.rieno.gadgetsandgizmos.neoforge;

import com.rieno.gadgetsandgizmos.content.ShipControlModuleRuntime;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.scm.AutopilotDebugSnapshot;
import com.rieno.gadgetsandgizmos.neoforge.network.PathfinderDebugRendererPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Maintain operator subscriptions for pathfinder and SCM brain snapshots.
public final class PathfinderDebugRenderService {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int OP_PERMISSION_LEVEL = 2;
    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final Set<UUID> ROUTE_SUBSCRIBERS = new HashSet<>();
    private static final Set<UUID> BRAIN_SUBSCRIBERS = new HashSet<>();
    // A debug overlay is a snapshot, not a per-tick stream. Retain the last
    // route identities sent to each operator so static full routes are not
    // re-encoded and re-sent every five ticks.
    private static final Map<UUID, List<RouteStamp>> LAST_SENT_ROUTES = new HashMap<>();
    private static final Map<UUID, List<BrainStamp>> LAST_SENT_BRAINS =
            new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the pathfinder debug subscription service.
    private PathfinderDebugRenderService() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set one player's operator route overlay state.
    public static boolean setEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            ROUTE_SUBSCRIBERS.add(player.getUUID());
        } else {
            ROUTE_SUBSCRIBERS.remove(player.getUUID());
            LAST_SENT_ROUTES.remove(player.getUUID());
        }
        syncPlayer(player);
        return enabled;
    }

    // Set one player's operator SCM brain overlay state.
    public static boolean setBrainEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            BRAIN_SUBSCRIBERS.add(player.getUUID());
        } else {
            BRAIN_SUBSCRIBERS.remove(player.getUUID());
            LAST_SENT_BRAINS.remove(player.getUUID());
        }
        syncBrainCollectionState();
        syncPlayer(player);
        return enabled;
    }

    // Toggle one player's operator debug overlay state.
    public static boolean toggle(ServerPlayer player) {
        return setEnabled(player,
                player != null && !ROUTE_SUBSCRIBERS.contains(player.getUUID()));
    }

    // Toggle one player's operator SCM brain overlay state.
    public static boolean toggleBrain(ServerPlayer player) {
        return setBrainEnabled(player,
                player != null && !BRAIN_SUBSCRIBERS.contains(player.getUUID()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh subscribed operator snapshots.
    public static void onServerTick(ServerTickEvent.Post evt) {
        MinecraftServer server = evt.getServer();
        if (ROUTE_SUBSCRIBERS.isEmpty() && BRAIN_SUBSCRIBERS.isEmpty()
                || server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        Set<UUID> subscribers = new HashSet<>(ROUTE_SUBSCRIBERS);
        subscribers.addAll(BRAIN_SUBSCRIBERS);
        Iterator<UUID> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
            UUID subscriber = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(subscriber);
            if (player == null || !player.hasPermissions(OP_PERMISSION_LEVEL)) {
                ROUTE_SUBSCRIBERS.remove(subscriber);
                BRAIN_SUBSCRIBERS.remove(subscriber);
                LAST_SENT_ROUTES.remove(subscriber);
                LAST_SENT_BRAINS.remove(subscriber);
                continue;
            }
            boolean routesEnabled = ROUTE_SUBSCRIBERS.contains(subscriber);
            boolean brainsEnabled = BRAIN_SUBSCRIBERS.contains(subscriber);
            List<SablePathfinder.DebugRoute> routes = routesEnabled
                    ? ShipControlModuleRuntime.pathfinderDebugRoutes(player.serverLevel())
                    : List.of();
            List<RouteStamp> routeStamps = routeStamps(routes);
            List<AutopilotDebugSnapshot> brains = brainsEnabled
                    ? ShipControlModuleRuntime.autopilotDebugSnapshots(player.serverLevel())
                    : List.of();
            List<BrainStamp> brainStamps = brainStamps(brains);
            if (routesEnabled && !routeStamps.equals(LAST_SENT_ROUTES.get(subscriber))
                    || brainsEnabled && !brainStamps.equals(LAST_SENT_BRAINS.get(subscriber))) {
                send(player, routesEnabled, brainsEnabled, routes, brains);
                updateStamps(subscriber, routesEnabled, brainsEnabled,
                        routeStamps, brainStamps);
            }
        }
        syncBrainCollectionState();
    }

    // Clear server-scoped operator subscriptions.
    public static void onServerStopped(ServerStoppedEvent evt) {
        ROUTE_SUBSCRIBERS.clear();
        BRAIN_SUBSCRIBERS.clear();
        LAST_SENT_ROUTES.clear();
        LAST_SENT_BRAINS.clear();
        ShipControlModuleRuntime.setAutopilotBrainDebugCollectionEnabled(false);
    }

    // Send one complete independently controlled route/brain snapshot.
    private static void send(ServerPlayer player, boolean routesEnabled,
                             boolean brainsEnabled,
                             List<SablePathfinder.DebugRoute> routes,
                             List<AutopilotDebugSnapshot> brains) {
        PacketDistributor.sendToPlayer(player, new PathfinderDebugRendererPayload(
                routesEnabled, brainsEnabled,
                routesEnabled ? routes : List.of(),
                brainsEnabled ? brains : List.of()));
    }

    // Force one player's current independent overlay states to the client.
    private static void syncPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        boolean routesEnabled = ROUTE_SUBSCRIBERS.contains(playerId);
        boolean brainsEnabled = BRAIN_SUBSCRIBERS.contains(playerId);
        List<SablePathfinder.DebugRoute> routes = routesEnabled
                ? ShipControlModuleRuntime.pathfinderDebugRoutes(player.serverLevel())
                : List.of();
        List<AutopilotDebugSnapshot> brains = brainsEnabled
                ? ShipControlModuleRuntime.autopilotDebugSnapshots(player.serverLevel())
                : List.of();
        send(player, routesEnabled, brainsEnabled, routes, brains);
        updateStamps(playerId, routesEnabled, brainsEnabled,
                routeStamps(routes), brainStamps(brains));
    }

    // Keep capture ownership tied only to brain consumers, not route rendering.
    private static void syncBrainCollectionState() {
        ShipControlModuleRuntime.setAutopilotBrainDebugCollectionEnabled(
                !BRAIN_SUBSCRIBERS.isEmpty());
    }

    // Update retained stamps for only the overlays this player currently watches.
    private static void updateStamps(
            UUID playerId,
            boolean routesEnabled,
            boolean brainsEnabled,
            List<RouteStamp> routeStamps,
            List<BrainStamp> brainStamps
    ) {
        if (routesEnabled) {
            LAST_SENT_ROUTES.put(playerId, routeStamps);
        } else {
            LAST_SENT_ROUTES.remove(playerId);
        }
        if (brainsEnabled) {
            LAST_SENT_BRAINS.put(playerId, brainStamps);
        } else {
            LAST_SENT_BRAINS.remove(playerId);
        }
    }

    // Create a shallow stamp; retained immutable waypoint lists identify an unchanged path.
    private static List<RouteStamp> routeStamps(List<SablePathfinder.DebugRoute> routes) {
        return routes.stream().map(RouteStamp::new).toList();
    }

    // Ignore snapshot time when the visible vehicle state itself is unchanged.
    private static List<BrainStamp> brainStamps(
            List<AutopilotDebugSnapshot> snapshots
    ) {
        return snapshots.stream().map(BrainStamp::new).toList();
    }

    // Compare waypoint list identity so a static large route does not require a deep comparison.
    private record RouteStamp(SablePathfinder.DebugRoute route) {
        @Override
        public boolean equals(Object other) {
            return other instanceof RouteStamp stamp
                    && route.id().equals(stamp.route.id())
                    && route.origin().equals(stamp.route.origin())
                    && route.target().equals(stamp.route.target())
                    && route.outcome() == stamp.route.outcome()
                    && route.targetLegValidated() == stamp.route.targetLegValidated()
                    && route.style() == stamp.route.style()
                    && route.waypoints() == stamp.route.waypoints()
                    && route.checkedSegments() == stamp.route.checkedSegments();
        }

        @Override
        public int hashCode() {
            int result = route.id().hashCode();
            result = 31 * result + route.origin().hashCode();
            result = 31 * result + route.target().hashCode();
            result = 31 * result + route.outcome().hashCode();
            result = 31 * result + Boolean.hashCode(route.targetLegValidated());
            result = 31 * result + route.style().hashCode();
            result = 31 * result + System.identityHashCode(route.waypoints());
            return 31 * result + System.identityHashCode(route.checkedSegments());
        }
    }

    // Compare only visible brain content so stationary debug panels do not resend every sample.
    private record BrainStamp(
            UUID vehicleId,
            String vehicleName,
            net.minecraft.world.phys.Vec3 anchor,
            AutopilotDebugSnapshot.State state,
            List<AutopilotDebugSnapshot.Section> sections
    ) {
        private BrainStamp(AutopilotDebugSnapshot snapshot) {
            this(snapshot.vehicleId(), snapshot.vehicleName(), snapshot.anchor(),
                    snapshot.state(), snapshot.sections());
        }
    }
}
