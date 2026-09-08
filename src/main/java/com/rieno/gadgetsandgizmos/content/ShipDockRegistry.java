package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import net.createmod.catnip.data.Glob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

// Keep loaded Ship Docks indexed so schedules can find them without scanning every level
public final class ShipDockRegistry {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<MinecraftServer, ShipDockRegistry> INSTANCES = new WeakHashMap<>();
    private static final double APPROACH_DISTANCE = 10.0D;
    private static final long SHIP_TELEMETRY_TTL_MILLIS = 15_000L;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Active server
    private final MinecraftServer server;
    // Tracked docks
    private final Map<UUID, Dock> docks = new HashMap<>();
    // Docks indexed by connector
    private final Map<ConnectorKey, Set<UUID>> docksByConnector = new HashMap<>();
    // Tracked unavailable docks
    private final Set<UUID> unavailableDocks = new HashSet<>();
    // Telemetry indexed by dock
    private final Map<UUID, Map<UUID, ShipTelemetry>> telemetryByDock = new HashMap<>();
    // Tracks whether ship dock is loaded
    private boolean loaded;
    // Current revision
    private long revision;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock
    private ShipDockRegistry(MinecraftServer server) {
        this.server = server;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ship dock value
    public static synchronized ShipDockRegistry get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, ShipDockRegistry::new);
    }

    // Finish the shutdown
    public static synchronized void finishShutdown(MinecraftServer server) {
        ShipDockRegistry registry = server == null ? null : INSTANCES.remove(server);
        if (registry != null) {
            registry.clearRuntimeState();
        }
    }

    // Clear the runtime state
    private synchronized void clearRuntimeState() {
        docks.clear();
        docksByConnector.clear();
        unavailableDocks.clear();
        telemetryByDock.clear();
        loaded = false;
    }

    // Update the ship dock
    public synchronized void update(ShipDockBlockEntity blockEntity) {
        ensureLoaded();
        Dock dock = blockEntity.createRouteRecord();
        if (dock == null) {
            return;
        }
        Dock prev = docks.get(dock.id());
        boolean definitionChanged = !sameDefinition(prev, dock);
        boolean becameAvailable = unavailableDocks.remove(dock.id());
        if (!definitionChanged && !becameAvailable) {
            return;
        }
        unindexDock(prev);
        docks.put(dock.id(), dock);
        indexDock(dock);
        revision++;
        ShippingRouteDatabase.upsert(server, dock);
    }

    // Remove the ship dock
    public synchronized void remove(UUID id) {
        ensureLoaded();
        unavailableDocks.remove(id);
        telemetryByDock.remove(id);
        ShipDockScheduler.get(server).removeDock(id);
        Dock removed = docks.remove(id);
        unindexDock(removed);
        if (removed != null) {
            revision++;
        }
        ShippingRouteDatabase.delete(server, id);
    }

    // Publish the telemetry
    public synchronized void publishTelemetry(UUID dockId, ShipTelemetry telemetry) {
        if (dockId == null || telemetry == null || telemetry.shipId() == null) {
            return;
        }
        purgeExpiredTelemetry();
        telemetryByDock.computeIfAbsent(dockId, ignored -> new HashMap<>())
                .put(telemetry.shipId(), telemetry);
    }

    // Clear the telemetry
    public synchronized void clearTelemetry(UUID shipId) {
        if (shipId == null) {
            return;
        }
        telemetryByDock.values().forEach(telemetry -> telemetry.remove(shipId));
        telemetryByDock.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    // Get the telemetry
    public synchronized List<ShipTelemetry> telemetry(UUID dockId) {
        if (dockId == null) {
            return List.of();
        }
        purgeExpiredTelemetry();
        return telemetryByDock.getOrDefault(dockId, Map.of()).values().stream()
                .sorted(Comparator
                        .comparingLong(ShipTelemetry::etaSeconds)
                        .thenComparing(ShipTelemetry::shipName)
                        .thenComparing(ShipTelemetry::shipId))
                .toList();
    }

    // Purge the expired telemetry
    private void purgeExpiredTelemetry() {
        long cutoff = System.currentTimeMillis() - SHIP_TELEMETRY_TTL_MILLIS;
        telemetryByDock.values().forEach(telemetry -> telemetry.values()
                .removeIf(update -> update.updatedAtMillis() < cutoff));
        telemetryByDock.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    // Get the ship dock value
    public synchronized @Nullable Dock get(UUID id) {
        ensureLoaded();
        return resolve(docks.get(id), false);
    }

    // Get every dock in the dimension
    public synchronized List<Dock> allIn(ResourceLocation dimension) {
        ensureLoaded();
        return docks.values().stream()
                .filter(dock -> dock.dimension().equals(dimension))
                .map(dock -> resolve(dock, true))
                .filter(Objects::nonNull)
                .sorted(DOCK_ORDER)
                .toList();
    }

    // Check if this is an assigned connector
    public synchronized boolean isAssignedConnector(
            ResourceLocation dimension,
            @Nullable UUID subLevelId,
            BlockPos pos
    ) {
        if (dimension == null || pos == null) {
            return false;
        }
        ensureLoaded();
        return docksByConnector.containsKey(new ConnectorKey(
                dimension, subLevelId, pos));
    }

    // Get the dock for connector
    synchronized @Nullable Dock dockForConnector(
            ResourceLocation dimension,
            @Nullable UUID subLevelId,
            BlockPos pos
    ) {
        if (dimension == null || pos == null) {
            return null;
        }
        ensureLoaded();
        Set<UUID> dockIds = docksByConnector.getOrDefault(
                new ConnectorKey(dimension, subLevelId, pos), Set.of());
        return List.copyOf(dockIds).stream()
                .map(docks::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // Remove a connector registration from every Ship Dock that owns it
    public synchronized boolean removeLinkedDockingConnector(
            ResourceLocation dimension,
            @Nullable UUID subLevelId,
            BlockPos pos
    ) {
        if (dimension == null || pos == null) {
            return false;
        }
        ensureLoaded();
        ConnectorKey key = new ConnectorKey(dimension, subLevelId, pos);
        Set<UUID> dockIds = Set.copyOf(docksByConnector.getOrDefault(key, Set.of()));
        boolean removed = false;
        for (UUID dockId : dockIds) {
            Dock dock = docks.get(dockId);
            ShipDockBlockEntity liveDock = findLoadedDock(dock);
            if (liveDock != null && liveDock.removeLinkedDockingConnector(
                    new ShipDockBlockEntity.ConnectorReference(subLevelId, pos), false)) {
                removeConnectorTarget(dock, key);
                removed = true;
            }
        }
        return removed;
    }

    // Check if this is a refueling connector
    public synchronized boolean isRefuelingConnector(
            ResourceLocation dimension,
            @Nullable UUID subLevelId,
            BlockPos pos
    ) {
        if (dimension == null || pos == null) return false;
        ensureLoaded();
        return docksByConnector.getOrDefault(
                        new ConnectorKey(dimension, subLevelId, pos), Set.of())
                .stream().map(docks::get).filter(Objects::nonNull).anyMatch(Dock::refuel);
    }

    // Get the matching
    public synchronized List<Dock> matching(ResourceLocation dimension, String glob) {
        return allIn(dimension).stream()
                .filter(dock -> addressMatches(dock.name(), glob))
                .toList();
    }

    // Check if the dock address matches
    static boolean addressMatches(String dockName, String glob) {
        String filter = glob == null || glob.isBlank() ? "*" : glob.trim();
        String pattern = Glob.toRegexPattern(filter, "");
        return (dockName == null ? "" : dockName).matches(pattern);
    }

    // Get the revision
    public synchronized long revision() {
        ensureLoaded();
        return revision;
    }

    // Get the nearest
    public @Nullable Dock nearest(
            ResourceLocation dimension,
            Vec3 origin,
            Predicate<Dock> predicate
    ) {
        return allIn(dimension).stream()
                .filter(predicate)
                .min(Comparator.comparingDouble(dock -> dock.approach().distanceToSqr(origin)))
                .orElse(null);
    }

    // Get the nearest refueling
    public @Nullable Dock nearestRefueling(
            ResourceLocation dimension,
            Vec3 origin,
            Predicate<Dock> predicate
    ) {
        return nearestService(dimension, origin, "refuel", predicate);
    }

    // Get the nearest packages
    public @Nullable Dock nearestPackages(
            ResourceLocation dimension,
            Vec3 origin,
            Predicate<Dock> predicate
    ) {
        return nearestService(dimension, origin, "packages", predicate);
    }

    // Get the nearest service
    private @Nullable Dock nearestService(
            ResourceLocation dimension,
            Vec3 origin,
            String service,
            Predicate<Dock> predicate
    ) {
        return allIn(dimension).stream()
                .filter(dock -> switch (service) {
                    case "refuel" -> dock.refuel();
                    case "restock" -> dock.restock();
                    case "packages" -> dock.packages();
                    default -> false;
                })
                .filter(predicate)
                .min(Comparator.comparingDouble(dock ->
                        dock.approach().distanceToSqr(origin)))
                .orElse(null);
    }

    // Get the refueling
    public static Predicate<Dock> refueling() {
        return new ServicePredicate("refuel", Dock::refuel);
    }

    // Get the restocking
    public static Predicate<Dock> restocking() {
        return new ServicePredicate("restock", Dock::restock);
    }

    // Get the packages
    public static Predicate<Dock> packages() {
        return new ServicePredicate("packages", Dock::packages);
    }

    // Ensure the loaded
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        for (Dock dock : ShippingRouteDatabase.all(server)) {
            docks.put(dock.id(), dock);
            indexDock(dock);
        }
        loaded = true;
        revision++;
    }

    // Check if this uses the same definition
    static boolean sameDefinition(@Nullable Dock first, @Nullable Dock second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(first.id(), second.id())
                && Objects.equals(first.dimension(), second.dimension())
                && Objects.equals(first.subLevelId(), second.subLevelId())
                && Objects.equals(first.pos(), second.pos())
                && Objects.equals(first.worldPosition(), second.worldPosition())
                && Objects.equals(first.facing(), second.facing())
                && Objects.equals(first.name(), second.name())
                && first.refuel() == second.refuel()
                && first.restock() == second.restock()
                && first.packages() == second.packages()
                && Objects.equals(first.connectorSubLevelId(), second.connectorSubLevelId())
                && Objects.equals(first.connectorPos(), second.connectorPos())
                && Objects.equals(first.connectorWorldPosition(), second.connectorWorldPosition())
                && Objects.equals(first.connectorFacing(), second.connectorFacing())
                && Objects.equals(first.connectorUp(), second.connectorUp())
                && Objects.equals(first.connectorTargets(), second.connectorTargets())
                && sameLandingZones(first.landingZones(), second.landingZones());
    }

    // Check if this uses the same landing zones
    private static boolean sameLandingZones(
            List<LandingZoneTarget> first,
            List<LandingZoneTarget> second
    ) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.size() != second.size()) {
            return false;
        }
        for (int idx = 0; idx < first.size(); idx++) {
            LandingZoneTarget left = first.get(idx);
            LandingZoneTarget right = second.get(idx);
            if (!left.id().equals(right.id())
                    || !left.name().equals(right.name())
                    || !left.min().equals(right.min())
                    || !left.max().equals(right.max())
                    || left.queueOrder() != right.queueOrder()
                    || left.airborne() != right.airborne()
                    || !sameVector(left.worldCenter(), right.worldCenter())
                    || !sameVector(left.worldUp(), right.worldUp())
                    || !sameBounds(left.worldBounds(), right.worldBounds())) {
                return false;
            }
        }
        return true;
    }

    // Check if this uses the same vector
    private static boolean sameVector(Vec3 first, Vec3 second) {
        return Double.compare(first.x, second.x) == 0
                && Double.compare(first.y, second.y) == 0
                && Double.compare(first.z, second.z) == 0;
    }

    // Check if this uses the same bounds
    private static boolean sameBounds(AABB first, AABB second) {
        return Double.compare(first.minX, second.minX) == 0
                && Double.compare(first.minY, second.minY) == 0
                && Double.compare(first.minZ, second.minZ) == 0
                && Double.compare(first.maxX, second.maxX) == 0
                && Double.compare(first.maxY, second.maxY) == 0
                && Double.compare(first.maxZ, second.maxZ) == 0;
    }

    // Resolve the ship dock
    private Dock resolve(@Nullable Dock dock, boolean requestSubLevelLoad) {
        if (dock == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dock.dimension()));
        if (level == null) {
            markUnavailable(dock, false);
            return null;
        }
        if (dock.subLevelId() == null) {
            if (level.isLoaded(dock.pos())) {
                var blockEntity = level.getBlockEntity(dock.pos());
                if (!(blockEntity instanceof ShipDockBlockEntity liveDock)
                        || !liveDock.getDockId().equals(dock.id())) {
                    markUnavailable(dock, true);
                    return null;
                }
            }
            markAvailable(dock.id());
            return resolveStoredPose(level, dock, null, requestSubLevelLoad);
        }
        Object subLevel = requestSubLevelLoad
                ? SubLevelBlockEntityCollector.ensureSubLevelLoaded(level, dock.subLevelId())
                : SubLevelBlockEntityCollector.getSubLevel(level, dock.subLevelId());
        if (subLevel == null) {
            return dock;
        }
        var blockEntity = requestSubLevelLoad
                ? SimulatedHelper.findBlockEntityExact(
                        level, dock.subLevelId(), dock.pos())
                : SimulatedHelper.findLoadedBlockEntityExact(
                        level, dock.subLevelId(), dock.pos());
        if (!(blockEntity instanceof ShipDockBlockEntity liveDock)
                || !liveDock.getDockId().equals(dock.id())) {
            return dock;
        }
        markAvailable(dock.id());
        return resolveStoredPose(level, dock, subLevel, requestSubLevelLoad);
    }

    // Load and resolve the live block entity for a stored dock
    private @Nullable ShipDockBlockEntity findLoadedDock(@Nullable Dock dock) {
        if (dock == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dock.dimension()));
        if (level == null) {
            return null;
        }
        if (dock.subLevelId() == null) {
            level.getChunkAt(dock.pos());
        } else if (!SubLevelBlockEntityCollector.ensureTargetLoaded(
                level, dock.subLevelId(), dock.pos())) {
            return null;
        }
        var blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, dock.subLevelId(), dock.pos());
        return blockEntity instanceof ShipDockBlockEntity liveDock
                && liveDock.getDockId().equals(dock.id()) ? liveDock : null;
    }

    // Persist the connector removal without probing the still-present breaking block
    private void removeConnectorTarget(Dock dock, ConnectorKey key) {
        if (dock == null) {
            return;
        }
        List<ConnectorTarget> connectorTargets = dock.connectorTargets().stream()
                .filter(target -> !Objects.equals(target.subLevelId(), key.subLevelId())
                        || !target.pos().equals(key.position()))
                .toList();
        if (connectorTargets.size() == dock.connectorTargets().size()) {
            return;
        }
        ConnectorTarget primary = connectorTargets.isEmpty() ? null : connectorTargets.getFirst();
        Dock updated = new Dock(
                dock.id(), dock.dimension(), dock.subLevelId(), dock.pos(),
                dock.worldPosition(), dock.facing(), dock.name(), dock.refuel(),
                dock.restock(), dock.packages(),
                primary == null ? null : primary.subLevelId(),
                primary == null ? null : primary.pos(),
                primary == null ? null : primary.worldPosition(),
                primary == null ? null : primary.facing(),
                primary == null ? null : primary.up(),
                System.currentTimeMillis(), connectorTargets, dock.landingZones());
        unindexDock(dock);
        docks.put(updated.id(), updated);
        indexDock(updated);
        revision++;
        ShippingRouteDatabase.upsert(server, updated);
    }

    // Resolve the stored pose
    private Dock resolveStoredPose(
            ServerLevel level,
            Dock dock,
            @Nullable Object dockSubLevel,
            boolean requestSubLevelLoad) {
        Vec3 worldPosition = dockSubLevel == null ? dock.pos().getCenter()
                : SimulatedHelper.toContainingWorldPosition(
                        dockSubLevel, dock.pos().getCenter());
        Vec3 worldFacing = dockSubLevel == null ? dock.facing()
                : SimulatedHelper.toContainingWorldDirection(dockSubLevel, dock.facing());
        List<ConnectorTarget> connectorTargets = new ArrayList<>();
        for (ConnectorTarget target : dock.connectorTargets()) {
            ConnectorTarget resolved = resolveConnectorTarget(
                    level, dockSubLevel, dock.subLevelId(), target, requestSubLevelLoad);
            if (resolved != null) {
                connectorTargets.add(resolved);
            }
        }
        ConnectorTarget connector = connectorTargets.isEmpty()
                ? null : connectorTargets.getFirst();
        List<LandingZoneTarget> landingZones = dock.landingZones().stream()
                .map(zone -> resolveLandingZoneTarget(level, dockSubLevel, zone))
                .toList();
        return new Dock(
                dock.id(), dock.dimension(), dock.subLevelId(), dock.pos(),
                worldPosition == null ? dock.worldPosition() : worldPosition,
                worldFacing == null ? dock.facing() : worldFacing,
                dock.name(), dock.refuel(), dock.restock(), dock.packages(),
                connector == null ? null : connector.subLevelId(),
                connector == null ? null : connector.pos(),
                connector == null ? null : connector.worldPosition(),
                connector == null ? null : connector.facing(),
                connector == null ? null : connector.up(),
                dock.updatedAt(), connectorTargets, landingZones);
    }

    // Resolve the landing zone target
    private LandingZoneTarget resolveLandingZoneTarget(
            ServerLevel level,
            @Nullable Object dockSubLevel,
            LandingZoneTarget zone
    ) {
        if (dockSubLevel == null) {
            return LandingZoneTarget.transformed(
                    zone.id(), zone.name(), zone.min(), zone.max(), zone.queueOrder(),
                    zone.airborne(),
                    pos -> pos);
        }
        return LandingZoneTarget.transformed(
                zone.id(), zone.name(), zone.min(), zone.max(), zone.queueOrder(),
                zone.airborne(),
                pos -> {
                    Vec3 world = SimulatedHelper.toContainingWorldPosition(
                            dockSubLevel, pos);
                    return SimulatedHelper.projectOutOfSubLevels(
                            level, world == null ? pos : world);
                });
    }

    // Resolve the connector target
    private @Nullable ConnectorTarget resolveConnectorTarget(
            ServerLevel level,
            @Nullable Object dockSubLevel,
            @Nullable UUID dockSubLevelId,
            ConnectorTarget target,
            boolean requestSubLevelLoad) {
        Object connectorSubLevel = target.subLevelId() == null ? null
                : target.subLevelId().equals(dockSubLevelId) ? dockSubLevel
                : requestSubLevelLoad
                ? SubLevelBlockEntityCollector.ensureSubLevelLoaded(level, target.subLevelId())
                : SubLevelBlockEntityCollector.getSubLevel(level, target.subLevelId());
        if (requestSubLevelLoad) {
            if (target.subLevelId() == null) {
                level.getChunkAt(target.pos());
            } else {
                SubLevelBlockEntityCollector.ensureTargetLoaded(
                        level, target.subLevelId(), target.pos());
                if (connectorSubLevel == null) {
                    connectorSubLevel = SubLevelBlockEntityCollector.getSubLevel(
                            level, target.subLevelId());
                }
            }
        }
        if (target.subLevelId() != null && connectorSubLevel == null) {
            return target;
        }
        Level targetLevel = connectorSubLevel instanceof SubLevel subLevel
                ? subLevel.getLevel() : level;
        if (!targetLevel.isLoaded(target.pos())) {
            return target;
        }
        BlockState state = targetLevel.getBlockState(target.pos());
        if (!isDockingConnector(state)) {
            return null;
        }
        Direction dir = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
        Vec3 localFacing = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 localUp = Dock.connectorUpForFacing(localFacing);
        Vec3 localTip = target.pos().getCenter().add(localFacing.scale(1.5D));
        Vec3 worldTip = connectorSubLevel == null ? localTip
                : SimulatedHelper.toContainingWorldPosition(connectorSubLevel, localTip);
        Vec3 worldFacing = connectorSubLevel == null ? localFacing
                : SimulatedHelper.toContainingWorldDirection(connectorSubLevel, localFacing);
        Vec3 worldUp = connectorSubLevel == null ? localUp
                : SimulatedHelper.toContainingWorldDirection(connectorSubLevel, localUp);
        return new ConnectorTarget(
                target.subLevelId(), target.pos(),
                worldTip == null ? target.worldPosition() : worldTip,
                worldFacing == null ? target.facing() : worldFacing,
                worldUp == null ? target.up() : worldUp);
    }

    // Check if this is a docking connector
    private static boolean isDockingConnector(BlockState state) {
        ResourceLocation id = state == null ? null
                : BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "simulated".equals(id.getNamespace())
                && "docking_connector".equals(id.getPath());
    }

    // Mark the unavailable
    private void markUnavailable(Dock dock, boolean deleteStoredRoute) {
        if (unavailableDocks.add(dock.id())) {
            unindexDock(dock);
            revision++;
            if (deleteStoredRoute) {
                ShipDockScheduler.get(server).removeDock(dock.id());
                ShippingRouteDatabase.delete(server, dock.id());
            }
        }
    }

    // Mark the available
    private void markAvailable(UUID dockId) {
        if (unavailableDocks.remove(dockId)) {
            indexDock(docks.get(dockId));
            revision++;
        }
    }

    // Index the dock
    private void indexDock(@Nullable Dock dock) {
        if (dock == null || unavailableDocks.contains(dock.id())) {
            return;
        }
        for (ConnectorTarget target : dock.connectorTargets()) {
            docksByConnector.computeIfAbsent(new ConnectorKey(
                    dock.dimension(), target.subLevelId(), target.pos()),
                    ignored -> new HashSet<>()).add(dock.id());
        }
    }

    // Unindex the dock
    private void unindexDock(@Nullable Dock dock) {
        if (dock == null) {
            return;
        }
        for (ConnectorTarget target : dock.connectorTargets()) {
            ConnectorKey key = new ConnectorKey(
                    dock.dimension(), target.subLevelId(), target.pos());
            Set<UUID> dockIds = docksByConnector.get(key);
            if (dockIds == null) {
                continue;
            }
            dockIds.remove(dock.id());
            if (dockIds.isEmpty()) {
                docksByConnector.remove(key);
            }
        }
    }

    // Store the connector key
    private record ConnectorKey(
            ResourceLocation dimension, @Nullable UUID subLevelId, BlockPos position) {
        // Initialize the connector key
        private ConnectorKey {
            position = position.immutable();
        }
    }

    // Store the ship telemetry
    public record ShipTelemetry(
            UUID shipId,
            String shipName,
            String currentStop,
            String targetName,
            String nextStop,
            String journeyStatus,
            String journeyPhase,
            Vec3 currentPosition,
            double distanceToTarget,
            long etaSeconds,
            double fuelRatio,
            double progressPercent,
            long updatedAtMillis
    ) {
        // Initialize the ship telemetry
        public ShipTelemetry(
                UUID shipId,
                String shipName,
                String targetName,
                String journeyStatus,
                Vec3 currentPosition,
                double distanceToTarget,
                long etaSeconds,
                long updatedAtMillis
        ) {
            this(shipId, shipName, "At sea", targetName, "", journeyStatus, "idle",
                    currentPosition, distanceToTarget, etaSeconds, 1.0D, 0.0D,
                    updatedAtMillis);
        }

        // Initialize the ship telemetry
        public ShipTelemetry {
            shipName = shipName == null || shipName.isBlank() ? "Unnamed Ship" : shipName;
            currentStop = currentStop == null || currentStop.isBlank() ? "At sea" : currentStop;
            targetName = targetName == null || targetName.isBlank() ? "Awaiting route" : targetName;
            nextStop = nextStop == null ? "" : nextStop;
            journeyStatus = journeyStatus == null || journeyStatus.isBlank()
                    ? "Awaiting route" : journeyStatus;
            journeyPhase = journeyPhase == null || journeyPhase.isBlank() ? "idle" : journeyPhase;
            currentPosition = currentPosition == null ? Vec3.ZERO : currentPosition;
            distanceToTarget = Math.max(0.0D, distanceToTarget);
            etaSeconds = Math.max(-1L, etaSeconds);
            fuelRatio = Math.max(0.0D, Math.min(1.0D, fuelRatio));
            progressPercent = Math.max(0.0D, Math.min(100.0D, progressPercent));
            updatedAtMillis = Math.max(0L, updatedAtMillis);
        }
    }

    // Store the dock
    public record Dock(
            UUID id,
            ResourceLocation dimension,
            @Nullable UUID subLevelId,
            BlockPos pos,
            Vec3 worldPosition,
            Vec3 facing,
            String name,
            boolean refuel,
            boolean restock,
            boolean packages,
            @Nullable UUID connectorSubLevelId,
            @Nullable BlockPos connectorPos,
            @Nullable Vec3 connectorWorldPosition,
            @Nullable Vec3 connectorFacing,
            @Nullable Vec3 connectorUp,
            long updatedAt,
            List<ConnectorTarget> connectorTargets,
            List<LandingZoneTarget> landingZones
    ) {
        // Initialize the dock
        public Dock(
                UUID id,
                ResourceLocation dimension,
                @Nullable UUID subLevelId,
                BlockPos pos,
                Vec3 worldPosition,
                Vec3 facing,
                String name,
                boolean refuel,
                boolean restock,
                boolean packages,
                @Nullable UUID connectorSubLevelId,
                @Nullable BlockPos connectorPos,
                @Nullable Vec3 connectorWorldPosition,
                @Nullable Vec3 connectorFacing,
                @Nullable Vec3 connectorUp,
                long updatedAt,
                List<ConnectorTarget> connectorTargets
        ) {
            this(id, dimension, subLevelId, pos, worldPosition, facing, name,
                    refuel, restock, packages, connectorSubLevelId, connectorPos,
                    connectorWorldPosition, connectorFacing, connectorUp, updatedAt,
                    connectorTargets, List.of());
        }

        // Initialize the dock
        public Dock(
                UUID id,
                ResourceLocation dimension,
                @Nullable UUID subLevelId,
                BlockPos pos,
                Vec3 worldPosition,
                Vec3 facing,
                String name,
                boolean refuel,
                boolean restock,
                boolean packages,
                @Nullable UUID connectorSubLevelId,
                @Nullable BlockPos connectorPos,
                @Nullable Vec3 connectorWorldPosition,
                @Nullable Vec3 connectorFacing,
                @Nullable Vec3 connectorUp,
                long updatedAt
        ) {
            this(id, dimension, subLevelId, pos, worldPosition, facing, name,
                    refuel, restock, packages, connectorSubLevelId, connectorPos,
                    connectorWorldPosition, connectorFacing, connectorUp, updatedAt,
                    primaryConnectorTarget(connectorSubLevelId, connectorPos,
                            connectorWorldPosition, connectorFacing, connectorUp),
                    List.of());
        }

        // Initialize the dock
        public Dock(
                UUID id,
                ResourceLocation dimension,
                @Nullable UUID subLevelId,
                BlockPos pos,
                Vec3 worldPosition,
                Vec3 facing,
                String name,
                boolean refuel,
                boolean restock,
                boolean packages,
                @Nullable UUID connectorSubLevelId,
                @Nullable BlockPos connectorPos,
                @Nullable Vec3 connectorWorldPosition,
                @Nullable Vec3 connectorFacing,
                long updatedAt
        ) {
            this(id, dimension, subLevelId, pos, worldPosition, facing, name,
                    refuel, restock, packages, connectorSubLevelId, connectorPos,
                    connectorWorldPosition, connectorFacing,
                    connectorUpForFacing(connectorFacing), updatedAt);
        }

        // Initialize the dock
        public Dock {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            worldPosition = worldPosition == null ? pos.getCenter() : worldPosition;
            facing = normalize(facing, new Vec3(0.0D, 0.0D, -1.0D));
            name = name == null || name.isBlank() ? "Ship Dock" : name;
            connectorPos = connectorPos == null ? null : connectorPos.immutable();
            connectorFacing = connectorFacing == null ? null
                    : normalize(connectorFacing, Vec3.ZERO);
            connectorUp = connectorFacing == null ? null
                    : normalize(connectorUp, connectorUpForFacing(connectorFacing));
            connectorTargets = connectorTargets == null ? List.of()
                    : connectorTargets.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            landingZones = landingZones == null ? List.of()
                    : landingZones.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(LandingZoneTarget::queueOrder)
                            .thenComparing(LandingZoneTarget::id))
                    .distinct()
                    .toList();
            if (connectorTargets.isEmpty()) {
                connectorTargets = primaryConnectorTarget(
                        connectorSubLevelId, connectorPos, connectorWorldPosition,
                        connectorFacing, connectorUp);
            }
        }

        // Copy the dock with the connector
        public Dock withConnector(ConnectorTarget target) {
            if (target == null) return this;
            return new Dock(id, dimension, subLevelId, pos, worldPosition, facing,
                    name, refuel, restock, packages, target.subLevelId(), target.pos(),
                    target.worldPosition(), target.facing(), target.up(), updatedAt,
                    connectorTargets, landingZones);
        }

        // Copy the dock with the landing zones
        public Dock withLandingZones(List<LandingZoneTarget> zones) {
            return new Dock(id, dimension, subLevelId, pos, worldPosition, facing,
                    name, refuel, restock, packages, connectorSubLevelId, connectorPos,
                    connectorWorldPosition, connectorFacing, connectorUp, updatedAt,
                    connectorTargets, zones);
        }

        // Get the approach
        public Vec3 approach() {
            if (connectorWorldPosition != null && connectorFacing != null
                    && connectorFacing.lengthSqr() > 1.0E-9D) {
                return connectorWorldPosition.add(connectorFacing.scale(APPROACH_DISTANCE));
            }
            return worldPosition.add(facing.scale(4.0D)).add(0.0D, 1.5D, 0.0D);
        }

        // Get the docking target
        public Vec3 dockingTarget() {
            return connectorWorldPosition == null ? worldPosition : connectorWorldPosition;
        }

        // Check if this has docking connector
        public boolean hasDockingConnector() {
            return connectorPos != null && connectorWorldPosition != null
                    && connectorFacing != null && connectorFacing.lengthSqr() > 1.0E-9D;
        }

        // Get the holding position
        public Vec3 holdingPosition(int queuePosition) {
            Vec3 outward = hasDockingConnector() ? connectorFacing : facing;
            Vec3 horizontal = new Vec3(outward.x, 0.0D, outward.z);
            if (horizontal.lengthSqr() < 1.0E-9D) {
                horizontal = new Vec3(facing.x, 0.0D, facing.z);
            }
            horizontal = normalize(horizontal, new Vec3(0.0D, 0.0D, 1.0D));
            Vec3 sideways = new Vec3(-horizontal.z, 0.0D, horizontal.x);
            int slot = Math.max(0, queuePosition - 1);
            int row = slot / 2;
            int lane = slot % 2 == 0 ? -1 : 1;
            return approach()
                    .add(horizontal.scale(20.0D + row * 10.0D))
                    .add(sideways.scale(lane * (18.0D + row * 4.0D)))
                    .add(0.0D, row * 6.0D, 0.0D);
        }

        // Normalize the dock
        private static Vec3 normalize(Vec3 val, Vec3 fallback) {
            return val == null || val.lengthSqr() < 1.0E-12D ? fallback : val.normalize();
        }

        // Get the connector up for facing
        static Vec3 connectorUpForFacing(@Nullable Vec3 connectorFacing) {
            Vec3 facing = normalize(connectorFacing, new Vec3(0.0D, 0.0D, -1.0D));
            Vec3 reference = Math.abs(facing.y) < 0.9D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(0.0D, 0.0D, 1.0D);
            return normalize(reference.subtract(facing.scale(reference.dot(facing))), Vec3.ZERO);
        }

        // Get the primary connector target
        private static List<ConnectorTarget> primaryConnectorTarget(
                @Nullable UUID subLevelId,
                @Nullable BlockPos pos,
                @Nullable Vec3 worldPosition,
                @Nullable Vec3 facing,
                @Nullable Vec3 up
        ) {
            if (pos == null || worldPosition == null || facing == null) {
                return List.of();
            }
            return List.of(new ConnectorTarget(subLevelId, pos, worldPosition, facing, up));
        }
    }

    // Store the landing zone target
    public record LandingZoneTarget(
            UUID id,
            String name,
            BlockPos min,
            BlockPos max,
            int queueOrder,
            boolean airborne,
            Vec3 worldCenter,
            Vec3 worldUp,
            AABB worldBounds
    ) {
        // Initialize the landing zone target
        public LandingZoneTarget {
            if (id == null) {
                throw new IllegalArgumentException("Landing zone id is required");
            }
            BlockPos first = min == null ? BlockPos.ZERO : min;
            BlockPos second = max == null ? first : max;
            min = new BlockPos(
                    Math.min(first.getX(), second.getX()),
                    Math.min(first.getY(), second.getY()),
                    Math.min(first.getZ(), second.getZ()));
            max = new BlockPos(
                    Math.max(first.getX(), second.getX()),
                    Math.max(first.getY(), second.getY()),
                    Math.max(first.getZ(), second.getZ()));
            name = name == null || name.isBlank() ? "Landing Zone" : name;
            queueOrder = Math.max(0, queueOrder);
            Vec3 fallbackCenter = new Vec3(
                    (min.getX() + max.getX() + 1.0D) * 0.5D,
                    max.getY() + 1.0D,
                    (min.getZ() + max.getZ() + 1.0D) * 0.5D);
            worldCenter = worldCenter == null ? fallbackCenter : worldCenter;
            worldUp = worldUp == null || worldUp.lengthSqr() < 1.0E-12D
                    ? new Vec3(0.0D, 1.0D, 0.0D) : worldUp.normalize();
            AABB fallbackBounds = new AABB(
                    min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
            AABB bounds = worldBounds == null ? fallbackBounds : worldBounds;
            worldBounds = new AABB(
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ);
        }

        // Transform one landing zone into world space
        public static LandingZoneTarget transformed(
                UUID id,
                String name,
                BlockPos min,
                BlockPos max,
                int queueOrder,
                boolean airborne,
                UnaryOperator<Vec3> transform
        ) {
            BlockPos first = min == null ? BlockPos.ZERO : min;
            BlockPos second = max == null ? first : max;
            int minX = Math.min(first.getX(), second.getX());
            int minY = Math.min(first.getY(), second.getY());
            int minZ = Math.min(first.getZ(), second.getZ());
            int maxX = Math.max(first.getX(), second.getX());
            int maxY = Math.max(first.getY(), second.getY());
            int maxZ = Math.max(first.getZ(), second.getZ());
            UnaryOperator<Vec3> transformer = transform == null ? pos -> pos : transform;
            double worldMinX = Double.POSITIVE_INFINITY;
            double worldMinY = Double.POSITIVE_INFINITY;
            double worldMinZ = Double.POSITIVE_INFINITY;
            double worldMaxX = Double.NEGATIVE_INFINITY;
            double worldMaxY = Double.NEGATIVE_INFINITY;
            double worldMaxZ = Double.NEGATIVE_INFINITY;
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        Vec3 local = new Vec3(
                                x == 0 ? minX : maxX + 1.0D,
                                y == 0 ? minY : maxY + 1.0D,
                                z == 0 ? minZ : maxZ + 1.0D);
                        Vec3 world = transformer.apply(local);
                        if (world == null) {
                            world = local;
                        }
                        worldMinX = Math.min(worldMinX, world.x);
                        worldMinY = Math.min(worldMinY, world.y);
                        worldMinZ = Math.min(worldMinZ, world.z);
                        worldMaxX = Math.max(worldMaxX, world.x);
                        worldMaxY = Math.max(worldMaxY, world.y);
                        worldMaxZ = Math.max(worldMaxZ, world.z);
                    }
                }
            }
            Vec3 localCenter = new Vec3(
                    (minX + maxX + 1.0D) * 0.5D,
                    maxY + 1.0D,
                    (minZ + maxZ + 1.0D) * 0.5D);
            Vec3 worldCenter = transformer.apply(localCenter);
            if (worldCenter == null) {
                worldCenter = localCenter;
            }
            Vec3 localBelow = localCenter.add(0.0D, -1.0D, 0.0D);
            Vec3 worldBelow = transformer.apply(localBelow);
            Vec3 worldUp = worldBelow == null
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : worldCenter.subtract(worldBelow);
            return new LandingZoneTarget(
                    id, name, new BlockPos(minX, minY, minZ),
                    new BlockPos(maxX, maxY, maxZ), queueOrder, airborne,
                    worldCenter, worldUp,
                    new AABB(worldMinX, worldMinY, worldMinZ,
                            worldMaxX, worldMaxY, worldMaxZ));
        }
    }

    // Store the connector target
    public record ConnectorTarget(
            @Nullable UUID subLevelId,
            BlockPos pos,
            Vec3 worldPosition,
            Vec3 facing,
            Vec3 up
    ) {
        // Initialize the connector target
        public ConnectorTarget {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            worldPosition = worldPosition == null ? pos.getCenter() : worldPosition;
            facing = Dock.normalize(facing, new Vec3(0.0D, 0.0D, -1.0D));
            up = Dock.normalize(up, Dock.connectorUpForFacing(facing));
        }
    }

    // Store the service predicate
    private record ServicePredicate(String column, Predicate<Dock> delegate)
            implements Predicate<Dock> {
        // Test the service predicate
        @Override
        public boolean test(Dock dock) {
            return delegate.test(dock);
        }
    }

    private static final Comparator<Dock> DOCK_ORDER = Comparator
            .comparing((Dock dock) -> dock.name().toLowerCase(Locale.ROOT))
            .thenComparing(Dock::id);
}
