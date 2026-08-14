package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnection;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnectionProvider;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyCache;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Give redstone, graphs and schedules one stable way to couple or release carriage endpoints
public final class ShipCouplerService {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long COMMAND_SESSION_GAP_TICKS = 40L;
    private static final long REGISTRY_VALIDATION_TICKS = 20L;
    private static final long TOPOLOGY_CACHE_PRUNE_TICKS = 200L;
    private static final long TOPOLOGY_CACHE_IDLE_TICKS = 1_200L;
    private static final int MAX_TOPOLOGY_ROOTS_PER_LEVEL = 128;
    private static final String LEGACY_REQUEST_KEY = "legacy";
    private static final int EXACT_ENDPOINT_SELECTOR = Integer.MIN_VALUE;
    private static final WeakHashMap<AdvancedContraptionControllerBlockEntity,
            Map<String, CommandSession>>
            COMMAND_SESSIONS = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, LevelState> LOADED_COUPLERS =
            new WeakHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship coupler service
    private ShipCouplerService() {
    }

    // Store the operation result
    public enum Result {
        COMPLETE,
        PENDING,
        NO_TARGET,
        FAILED
    }

    // Store the status
    public record Status(int couplerCount, int coupledCount, int carriageCount,
                         boolean anyCoupled, boolean allCoupled, String description) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the command
    public static Result command(AdvancedContraptionControllerBlockEntity controller,
                                 boolean attach, int selector) {
        return command(controller, LEGACY_REQUEST_KEY, attach, selector);
    }

    // Get the command
    public static Result command(AdvancedContraptionControllerBlockEntity controller,
                                 String requestKey, boolean attach, int selector) {
        if (controller == null || controller.getLevel() == null) {
            return Result.FAILED;
        }
        String key = normalizeRequestKey(requestKey);
        synchronized (COMMAND_SESSIONS) {
            long gameTime = controller.getLevel().getGameTime();
            Map<String, CommandSession> sessions = sessions(controller, gameTime);
            CommandSession session = sessions.get(key);
            if (session == null || session.attach() != attach
                    || session.selector() != selector) {
                List<ShipCouplerBlockEntity.EndpointKey> endpoints =
                        selectEndpoints(controller, selector);
                if (endpoints.isEmpty()) {
                    sessions.remove(key);
                    removeEmptySessions(controller, sessions);
                    return Result.NO_TARGET;
                }
                session = new CommandSession(
                        attach, selector, endpoints, gameTime, false);
            }
            return command(controller, sessions, key, session, gameTime);
        }
    }

    // Get the command
    public static Result command(AdvancedContraptionControllerBlockEntity controller,
                                 String requestKey, boolean attach,
                                 List<ShipCouplerBlockEntity.EndpointKey> endpoints) {
        if (controller == null || controller.getLevel() == null) {
            return Result.FAILED;
        }
        List<ShipCouplerBlockEntity.EndpointKey> selected = normalizedEndpoints(endpoints);
        if (selected.isEmpty()) {
            return Result.NO_TARGET;
        }
        String key = normalizeRequestKey(requestKey);
        synchronized (COMMAND_SESSIONS) {
            long gameTime = controller.getLevel().getGameTime();
            Map<String, CommandSession> sessions = sessions(controller, gameTime);
            CommandSession session = sessions.get(key);
            if (session == null || session.attach() != attach
                    || !session.endpoints().equals(selected)) {
                session = new CommandSession(
                        attach, EXACT_ENDPOINT_SELECTOR, selected, gameTime, false);
            }
            return command(controller, sessions, key, session, gameTime);
        }
    }

    // Select the endpoints
    public static List<ShipCouplerBlockEntity.EndpointKey> selectEndpoints(
            AdvancedContraptionControllerBlockEntity controller, int selector) {
        List<ShipCouplerBlockEntity> available = couplers(controller);
        if (available.isEmpty() || selector < -1 || selector >= available.size()) {
            return List.of();
        }
        List<ShipCouplerBlockEntity> selected = selector == -1
                ? available : List.of(available.get(selector));
        return selected.stream()
                .map(ShipCouplerBlockEntity::endpointKey)
                .filter(Objects::nonNull)
                .toList();
    }

    // Write the endpoint keys
    public static ListTag writeEndpointKeys(
            Collection<ShipCouplerBlockEntity.EndpointKey> endpoints) {
        ListTag encoded = new ListTag();
        for (ShipCouplerBlockEntity.EndpointKey endpoint : normalizedEndpoints(endpoints)) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("SubLevel", endpoint.subLevelId());
            entry.put("Position", NbtUtils.writeBlockPos(endpoint.position()));
            encoded.add(entry);
        }
        return encoded;
    }

    // Read the endpoint keys
    public static List<ShipCouplerBlockEntity.EndpointKey> readEndpointKeys(ListTag encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<ShipCouplerBlockEntity.EndpointKey> endpoints = new LinkedHashSet<>();
        for (int idx = 0; idx < encoded.size(); idx++) {
            CompoundTag entry = encoded.getCompound(idx);
            if (!entry.hasUUID("SubLevel") || !entry.contains("Position")) {
                continue;
            }
            BlockPos pos = NbtUtils.readBlockPos(entry, "Position").orElse(null);
            if (pos != null) {
                endpoints.add(new ShipCouplerBlockEntity.EndpointKey(
                        entry.getUUID("SubLevel"), pos));
            }
        }
        return List.copyOf(endpoints);
    }

    // Get the command
    private static Result command(
            AdvancedContraptionControllerBlockEntity controller,
            Map<String, CommandSession> sessions, String requestKey,
            CommandSession requested, long gameTime) {
        for (Map.Entry<String, CommandSession> entry : sessions.entrySet()) {
            CommandSession other = entry.getValue();
            if (!entry.getKey().equals(requestKey)
                    && other.attach() != requested.attach()
                    && overlaps(other.endpoints(), requested.endpoints())) {
                return Result.FAILED;
            }
        }
        if (requested.completed()) {
            return Result.COMPLETE;
        }
        CommandSession session = requested.polledAt(gameTime);
        sessions.put(requestKey, session);

        EndpointResolution resolution = resolveEndpoints(controller, session.endpoints());
        if (resolution.invalidTarget()) {
            sessions.remove(requestKey);
            removeEmptySessions(controller, sessions);
            return Result.FAILED;
        }
        if (!resolution.allLoaded()) {
            return Result.PENDING;
        }
        List<ShipCouplerBlockEntity> selected = resolution.couplers();
        if (selected.isEmpty()) {
            sessions.remove(requestKey);
            removeEmptySessions(controller, sessions);
            return Result.NO_TARGET;
        }
        boolean accepted = true;
        for (ShipCouplerBlockEntity coupler : selected) {
            accepted &= coupler.requestCoupled(session.attach());
        }
        if (!accepted) {
            sessions.remove(requestKey);
            removeEmptySessions(controller, sessions);
            return Result.FAILED;
        }
        boolean complete = session.attach()
                ? selected.stream().allMatch(ShipCouplerBlockEntity::isCoupled)
                : selected.stream().noneMatch(ShipCouplerBlockEntity::isCoupled);
        if (complete) {
            sessions.put(requestKey, session.completedAt(gameTime));
            return Result.COMPLETE;
        }
        return Result.PENDING;
    }

    // Get the sessions
    private static Map<String, CommandSession> sessions(
            AdvancedContraptionControllerBlockEntity controller, long gameTime) {
        Map<String, CommandSession> sessions = COMMAND_SESSIONS.computeIfAbsent(
                controller, ignored -> new LinkedHashMap<>());
        sessions.entrySet().removeIf(entry -> {
            CommandSession session = entry.getValue();
            return gameTime < session.lastPollTick()
                    || session.completed() && gameTime > session.lastPollTick()
                    || !session.completed()
                    && gameTime - session.lastPollTick() > COMMAND_SESSION_GAP_TICKS;
        });
        return sessions;
    }

    // Remove the empty sessions
    private static void removeEmptySessions(
            AdvancedContraptionControllerBlockEntity controller,
            Map<String, CommandSession> sessions) {
        if (sessions.isEmpty()) {
            COMMAND_SESSIONS.remove(controller);
        }
    }

    // Check if the endpoint sets overlap
    private static boolean overlaps(
            List<ShipCouplerBlockEntity.EndpointKey> first,
            List<ShipCouplerBlockEntity.EndpointKey> second) {
        Set<ShipCouplerBlockEntity.EndpointKey> smaller = first.size() <= second.size()
                ? Set.copyOf(first) : Set.copyOf(second);
        List<ShipCouplerBlockEntity.EndpointKey> larger = first.size() <= second.size()
                ? second : first;
        return larger.stream().anyMatch(smaller::contains);
    }

    // Normalize the request key
    private static String normalizeRequestKey(String requestKey) {
        return requestKey == null || requestKey.isBlank()
                ? LEGACY_REQUEST_KEY : requestKey;
    }

    // Get the normalized endpoints
    private static List<ShipCouplerBlockEntity.EndpointKey> normalizedEndpoints(
            Collection<ShipCouplerBlockEntity.EndpointKey> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<ShipCouplerBlockEntity.EndpointKey> normalized = new LinkedHashSet<>();
        endpoints.stream().filter(Objects::nonNull).forEach(normalized::add);
        return List.copyOf(normalized);
    }

    // Get the status
    public static Status status(AdvancedContraptionControllerBlockEntity controller) {
        List<ShipCouplerBlockEntity> available = couplers(controller);
        int coupled = (int) available.stream().filter(ShipCouplerBlockEntity::isCoupled).count();
        int carriageCount = attachedCarriageCount(controller);
        boolean any = coupled > 0;
        boolean all = !available.isEmpty() && coupled == available.size();
        String description = available.isEmpty() ? "none"
                : coupled == 0 ? "uncoupled"
                : all ? "coupled" : "partial";
        return new Status(available.size(), coupled, carriageCount, any, all, description);
    }

    // Get the coupler count
    public static int couplerCount(AdvancedContraptionControllerBlockEntity controller) {
        return status(controller).couplerCount();
    }

    // Get the coupled count
    public static int coupledCount(AdvancedContraptionControllerBlockEntity controller) {
        return status(controller).coupledCount();
    }

    // Get the carriage count
    public static int carriageCount(AdvancedContraptionControllerBlockEntity controller) {
        return status(controller).carriageCount();
    }

    // Check if any ship couplers are linked
    public static boolean anyCoupled(AdvancedContraptionControllerBlockEntity controller) {
        return status(controller).anyCoupled();
    }

    // Check if all coupled
    public static boolean allCoupled(AdvancedContraptionControllerBlockEntity controller) {
        return status(controller).allCoupled();
    }

    // Get the status text
    public static String statusText(AdvancedContraptionControllerBlockEntity controller) {
        return status(controller).description();
    }

    // Get the couplers
    public static List<ShipCouplerBlockEntity> couplers(
            @Nullable AdvancedContraptionControllerBlockEntity controller) {
        ServerSubLevel root = controller == null ? null : containingSubLevel(controller);
        if (root == null) {
            return List.of();
        }
        SableAssemblyTopologyApi.Topology topology = topology(root);
        Set<UUID> bodyIds = topology.available()
                ? topology.loadedBodyIds() : Set.of(root.getUniqueId());
        Map<ShipCouplerBlockEntity.EndpointKey, ShipCouplerBlockEntity> reachable =
                new LinkedHashMap<>();
        for (ShipCouplerBlockEntity coupler : loadedCouplers(root.getLevel())) {
            ShipCouplerBlockEntity.EndpointKey key = coupler.endpointKey();
            if (key != null && bodyIds.contains(key.subLevelId())) {
                reachable.put(key, coupler);
            }
        }
        return reachable.values().stream()
                .filter(coupler -> ownsLogicalEndpoint(coupler, reachable, topology))
                .sorted(Comparator
                        .comparingInt((ShipCouplerBlockEntity coupler) ->
                                carriageDepth(topology, coupler.endpointKey()))
                        .thenComparingInt(coupler -> bodyDepth(topology, coupler.endpointKey()))
                        .thenComparing(ShipCouplerBlockEntity::endpointKey))
                .toList();
    }

    // Check if this owns logical endpoint
    private static boolean ownsLogicalEndpoint(
            ShipCouplerBlockEntity coupler,
            Map<ShipCouplerBlockEntity.EndpointKey, ShipCouplerBlockEntity> reachable,
            SableAssemblyTopologyApi.Topology topology) {
        ShipCouplerBlockEntity.EndpointKey ownKey = coupler.endpointKey();
        ShipCouplerBlockEntity.EndpointKey partnerKey = partnerKey(coupler);
        if (ownKey == null || partnerKey == null) {
            return ownKey != null;
        }
        if (!reachable.containsKey(partnerKey)) {
            return true;
        }
        int ownCarriageDepth = carriageDepth(topology, ownKey);
        int partnerCarriageDepth = carriageDepth(topology, partnerKey);
        if (ownCarriageDepth != partnerCarriageDepth) {
            return ownCarriageDepth < partnerCarriageDepth;
        }
        int ownBodyDepth = bodyDepth(topology, ownKey);
        int partnerBodyDepth = bodyDepth(topology, partnerKey);
        return ownBodyDepth != partnerBodyDepth
                ? ownBodyDepth < partnerBodyDepth
                : ownKey.compareTo(partnerKey) < 0;
    }

    // Get the partner key
    private static @Nullable ShipCouplerBlockEntity.EndpointKey partnerKey(
            ShipCouplerBlockEntity coupler) {
        return coupler.partnerSubLevelId() == null || coupler.partnerPosition() == null
                ? null : new ShipCouplerBlockEntity.EndpointKey(
                        coupler.partnerSubLevelId(), coupler.partnerPosition());
    }

    // Get the carriage depth
    private static int carriageDepth(
            SableAssemblyTopologyApi.Topology topology,
            @Nullable ShipCouplerBlockEntity.EndpointKey endpoint) {
        if (endpoint == null) {
            return Integer.MAX_VALUE;
        }
        return topology.carriage(endpoint.subLevelId())
                .map(SableAssemblyTopologyApi.CarriagePartition::depth)
                .orElse(0);
    }

    // Get the body depth
    private static int bodyDepth(
            SableAssemblyTopologyApi.Topology topology,
            @Nullable ShipCouplerBlockEntity.EndpointKey endpoint) {
        if (endpoint == null) {
            return Integer.MAX_VALUE;
        }
        return topology.depth(endpoint.subLevelId()).orElse(0);
    }

    // Get the attached carriage count
    private static int attachedCarriageCount(
            @Nullable AdvancedContraptionControllerBlockEntity controller) {
        ServerSubLevel root = controller == null ? null : containingSubLevel(controller);
        if (root == null) {
            return 0;
        }
        SableAssemblyTopologyApi.Topology topology = topology(root);
        return topology.available() ? Math.max(0, topology.carriagePartitions().size() - 1) : 0;
    }

    // Find the mutual candidate
    static @Nullable ShipCouplerBlockEntity findMutualCandidate(ShipCouplerBlockEntity origin) {
        List<ShipCouplerBlockEntity> couplers = loadedCouplers(origin.getLevel());
        ShipCouplerBlockEntity best = bestCandidate(origin, couplers);
        if (best == null || bestCandidate(best, couplers) != origin) {
            return null;
        }
        ShipCouplerBlockEntity.EndpointKey originKey = origin.endpointKey();
        ShipCouplerBlockEntity.EndpointKey bestKey = best.endpointKey();
        return originKey != null && bestKey != null && originKey.compareTo(bestKey) < 0 ? best : null;
    }

    // Find the mutual magnetic candidate
    static @Nullable ShipCouplerBlockEntity findMutualMagneticCandidate(ShipCouplerBlockEntity origin) {
        List<ShipCouplerBlockEntity> couplers = loadedCouplers(origin.getLevel());
        ShipCouplerBlockEntity best = bestMagneticCandidate(origin, couplers);
        if (best == null || bestMagneticCandidate(best, couplers) != origin) {
            return null;
        }
        ShipCouplerBlockEntity.EndpointKey originKey = origin.endpointKey();
        ShipCouplerBlockEntity.EndpointKey bestKey = best.endpointKey();
        return originKey != null && bestKey != null && originKey.compareTo(bestKey) < 0 ? best : null;
    }

    // Resolve the partner
    static @Nullable ShipCouplerBlockEntity resolvePartner(ShipCouplerBlockEntity origin, boolean requestLoad) {
        if (origin.getLevel() == null || origin.partnerSubLevelId() == null || origin.partnerPosition() == null) {
            return null;
        }
        Level level = origin.getLevel();
        if (requestLoad) {
            SubLevelBlockEntityCollector.ensureTargetLoaded(
                    level, origin.partnerSubLevelId(), origin.partnerPosition());
        }
        BlockEntity target = SimulatedHelper.findLoadedBlockEntityExact(
                level, origin.partnerSubLevelId(), origin.partnerPosition());
        if (target instanceof ShipCouplerBlockEntity coupler && !coupler.isRemoved()) {
            registerLoaded(coupler);
            return coupler;
        }
        return null;
    }

    // Check if the partner target is loaded
    static boolean partnerTargetIsLoaded(ShipCouplerBlockEntity origin) {
        return origin.getLevel() != null
                && origin.partnerSubLevelId() != null
                && origin.partnerPosition() != null
                && SubLevelBlockEntityCollector.isTargetLoaded(
                        origin.getLevel(), origin.partnerSubLevelId(), origin.partnerPosition());
    }

    // Get the best candidate
    private static @Nullable ShipCouplerBlockEntity bestCandidate(
            ShipCouplerBlockEntity origin, List<ShipCouplerBlockEntity> couplers) {
        return couplers.stream()
                .filter(candidate -> candidate != origin)
                .filter(origin::canPairWith)
                .min(Comparator
                        .comparingDouble(origin::tipDistanceSquared)
                        .thenComparing(ShipCouplerBlockEntity::endpointKey,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    // Get the best magnetic candidate
    private static @Nullable ShipCouplerBlockEntity bestMagneticCandidate(
            ShipCouplerBlockEntity origin, List<ShipCouplerBlockEntity> couplers) {
        return couplers.stream()
                .filter(candidate -> candidate != origin)
                .filter(origin::canMagneticallyCaptureWith)
                .min(Comparator
                        .comparingDouble(origin::tipDistanceSquared)
                        .thenComparing(ShipCouplerBlockEntity::endpointKey,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    // Get the loaded couplers
    private static List<ShipCouplerBlockEntity> loadedCouplers(@Nullable Level level) {
        if (level == null || level.isClientSide) {
            return List.of();
        }
        ServerLevel root = resolveServerLevel(level);
        if (root == null) {
            return List.of();
        }
        List<ShipCouplerBlockEntity> res = new ArrayList<>();
        synchronized (LOADED_COUPLERS) {
            LevelState state = LOADED_COUPLERS.get(root);
            if (state == null) {
                return List.of();
            }
            long gameTime = root.getGameTime();
            if (state.snapshotRevision == state.membershipRevision
                    && gameTime >= state.lastSnapshotTick
                    && gameTime < state.nextValidationTick) {
                return state.sortedSnapshot;
            }
            int initialMembership = state.loadedByEndpoint.size();
            state.loadedByEndpoint.entrySet().removeIf(entry -> {
                ShipCouplerBlockEntity coupler = entry.getValue().get();
                if (!isLoadedRegistryEntry(root, entry.getKey(), coupler)) {
                    if (coupler != null) {
                        state.endpointByCoupler.remove(coupler);
                    }
                    return true;
                }
                res.add(coupler);
                return false;
            });
            if (state.loadedByEndpoint.size() != initialMembership) {
                state.membershipChanged();
            }
            res.sort(Comparator.comparing(ShipCouplerBlockEntity::endpointKey,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            state.sortedSnapshot = List.copyOf(res);
            state.snapshotRevision = state.membershipRevision;
            state.lastSnapshotTick = gameTime;
            state.nextValidationTick = gameTime + REGISTRY_VALIDATION_TICKS;
            return state.sortedSnapshot;
        }
    }

    // Register the loaded
    static void registerLoaded(@Nullable ShipCouplerBlockEntity coupler) {
        if (coupler == null || coupler.isRemoved() || coupler.getLevel() == null
                || coupler.getLevel().isClientSide) {
            return;
        }
        ServerLevel root = resolveServerLevel(coupler.getLevel());
        ShipCouplerBlockEntity.EndpointKey endpoint = coupler.endpointKey();
        if (root == null || endpoint == null) {
            return;
        }
        synchronized (LOADED_COUPLERS) {
            LevelState state = LOADED_COUPLERS.computeIfAbsent(root,
                    ignored -> new LevelState());
            state.pruneTopologyCaches(root.getGameTime());
            ShipCouplerBlockEntity.EndpointKey prev = state.endpointByCoupler.get(coupler);
            WeakReference<ShipCouplerBlockEntity> current =
                    state.loadedByEndpoint.get(endpoint);
            if (endpoint.equals(prev) && current != null && current.get() == coupler) {
                return;
            }
            state.endpointByCoupler.put(coupler, endpoint);
            if (prev != null && !prev.equals(endpoint)) {
                WeakReference<ShipCouplerBlockEntity> old = state.loadedByEndpoint.get(prev);
                if (old != null && old.get() == coupler) {
                    state.loadedByEndpoint.remove(prev);
                }
            }
            ShipCouplerBlockEntity replaced = current == null ? null : current.get();
            if (replaced != null && replaced != coupler) {
                state.endpointByCoupler.remove(replaced);
            }
            state.loadedByEndpoint.put(endpoint, new WeakReference<>(coupler));
            state.membershipChanged();
        }
    }

    // Remove the loaded
    static void unregisterLoaded(@Nullable ShipCouplerBlockEntity coupler) {
        if (coupler == null) {
            return;
        }
        synchronized (LOADED_COUPLERS) {
            for (LevelState state : LOADED_COUPLERS.values()) {
                ShipCouplerBlockEntity.EndpointKey endpoint =
                        state.endpointByCoupler.remove(coupler);
                if (endpoint == null) {
                    continue;
                }
                WeakReference<ShipCouplerBlockEntity> current =
                        state.loadedByEndpoint.get(endpoint);
                if (current != null
                        && (current.get() == null || current.get() == coupler)) {
                    state.loadedByEndpoint.remove(endpoint);
                }
                state.membershipChanged();
            }
        }
    }

    // Clear the loaded couplers
    static void clearLoadedCouplers(ServerLevel level) {
        synchronized (LOADED_COUPLERS) {
            LevelState removed = LOADED_COUPLERS.remove(level);
            if (removed != null) {
                removed.clear();
            }
        }
    }

    // Clear the loaded couplers
    static void clearLoadedCouplers(MinecraftServer server) {
        synchronized (LOADED_COUPLERS) {
            var iterator = LOADED_COUPLERS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ServerLevel, LevelState> entry = iterator.next();
                if (entry.getKey().getServer() == server) {
                    entry.getValue().clear();
                    iterator.remove();
                }
            }
        }
    }

    // Prune the topology caches
    static void pruneTopologyCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (LOADED_COUPLERS) {
            for (Map.Entry<ServerLevel, LevelState> entry : LOADED_COUPLERS.entrySet()) {
                if (entry.getKey().getServer() == server) {
                    entry.getValue().pruneTopologyCaches(entry.getKey().getGameTime());
                }
            }
        }
    }

    // Check if the registry entry is loaded
    private static boolean isLoadedRegistryEntry(
            ServerLevel root, ShipCouplerBlockEntity.EndpointKey endpoint,
            @Nullable ShipCouplerBlockEntity coupler) {
        if (coupler == null || coupler.isRemoved() || coupler.getLevel() == null
                || resolveServerLevel(coupler.getLevel()) != root
                || !endpoint.equals(coupler.endpointKey())
                || !coupler.getLevel().hasChunkAt(endpoint.position())) {
            return false;
        }
        return coupler.getLevel().getBlockEntity(endpoint.position()) == coupler;
    }

    // Resolve the server level
    private static @Nullable ServerLevel resolveServerLevel(Level level) {
        return SableLevelApi.serverLevel(level);
    }

    // Resolve the endpoints
    private static EndpointResolution resolveEndpoints(
            AdvancedContraptionControllerBlockEntity controller,
            List<ShipCouplerBlockEntity.EndpointKey> endpoints) {
        if (controller.getLevel() == null) {
            return new EndpointResolution(List.of(), false, true);
        }
        List<ShipCouplerBlockEntity> res = new ArrayList<>();
        boolean allLoaded = true;
        boolean invalidTarget = false;
        for (ShipCouplerBlockEntity.EndpointKey endpoint : endpoints) {
            SubLevelBlockEntityCollector.ensureTargetLoaded(
                    controller.getLevel(), endpoint.subLevelId(), endpoint.position());
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), endpoint.subLevelId(), endpoint.position());
            if (blockEntity instanceof ShipCouplerBlockEntity coupler && !coupler.isRemoved()) {
                registerLoaded(coupler);
                res.add(coupler);
            } else if (SubLevelBlockEntityCollector.isTargetLoaded(
                    controller.getLevel(), endpoint.subLevelId(), endpoint.position())) {
                invalidTarget = true;
            } else {
                allLoaded = false;
            }
        }
        return new EndpointResolution(List.copyOf(res), allLoaded, invalidTarget);
    }

    // Get the containing sublevel
    private static @Nullable ServerSubLevel containingSubLevel(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        try {
            Object containing = Sable.HELPER.getContaining(blockEntity);
            return containing instanceof ServerSubLevel body && !body.isRemoved() ? body : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Get the ship coupler topology
    static SableAssemblyTopologyApi.Topology topology(ServerSubLevel root) {
        SableAssemblyTopologyCache cache;
        synchronized (LOADED_COUPLERS) {
            ServerLevel level = root.getLevel();
            LevelState state = LOADED_COUPLERS.computeIfAbsent(level,
                    ignored -> new LevelState());
            cache = state.topologyCache(root, level.getGameTime());
        }
        return cache.get(root);
    }

    // Include the topology actor
    private static boolean includeTopologyActor(ServerSubLevel owner, BlockEntitySubLevelActor actor) {
        if (actor instanceof SableAssemblyConnectionProvider) {
            return true;
        }
        return !(actor instanceof AnalogueContraptionControllerBlockEntity)
                && !(actor instanceof ContraptionNetworkLinkerPlaneBlockEntity)
                && !(actor instanceof PhysicsGantryBeltWheelBlockEntity)
                && !(actor instanceof GyroscopeLinkBlockEntity)
                && !(actor instanceof ClawBlockEntity)
                && !(actor instanceof EntityLauncherAnchorBlockEntity)
                && !(actor instanceof ShipDockBlockEntity);
    }

    // Get the classify topology actor
    private static SableAssemblyConnection.Kind classifyTopologyActor(
            ServerSubLevel owner, BlockEntitySubLevelActor actor, ServerSubLevel target) {
        return actor instanceof ShipCouplerBlockEntity
                ? SableAssemblyConnection.Kind.CARRIAGE_COUPLER
                : SableAssemblyConnection.Kind.STRUCTURAL;
    }

    // Store the command session
    private record CommandSession(boolean attach, int selector,
                                  List<ShipCouplerBlockEntity.EndpointKey> endpoints,
                                  long lastPollTick, boolean completed) {
        // Initialize the command session
        private CommandSession {
            endpoints = List.copyOf(endpoints);
        }

        // Mark the command session as polled
        private CommandSession polledAt(long gameTime) {
            return new CommandSession(attach, selector, endpoints, gameTime, completed);
        }

        // Mark the command session as complete
        private CommandSession completedAt(long gameTime) {
            return new CommandSession(attach, selector, endpoints, gameTime, true);
        }
    }

    // Store the endpoint resolution
    private record EndpointResolution(List<ShipCouplerBlockEntity> couplers,
                                      boolean allLoaded, boolean invalidTarget) {
        // Initialize the endpoint resolution
        private EndpointResolution {
            couplers = List.copyOf(couplers);
        }
    }

    // Store level state
    private static final class LevelState {
        // Loaded indexed by endpoint
        private final Map<ShipCouplerBlockEntity.EndpointKey,
                WeakReference<ShipCouplerBlockEntity>> loadedByEndpoint =
                new LinkedHashMap<>();
        // Endpoint indexed by coupler
        private final WeakHashMap<ShipCouplerBlockEntity,
                ShipCouplerBlockEntity.EndpointKey> endpointByCoupler =
                new WeakHashMap<>();
        // Topology indexed by root
        private final LinkedHashMap<UUID, RootTopologyCache> topologyByRoot =
                new LinkedHashMap<>(16, 0.75F, true);
        // Current membership revision
        private long membershipRevision;
        // Current snapshot revision
        private long snapshotRevision = Long.MIN_VALUE;
        // Last snapshot tick
        private long lastSnapshotTick = Long.MIN_VALUE;
        // Next validation tick
        private long nextValidationTick = Long.MIN_VALUE;
        // Tracked sorted snapshot
        private List<ShipCouplerBlockEntity> sortedSnapshot = List.of();
        // Last topology prune tick
        private long lastTopologyPruneTick = Long.MIN_VALUE;

        // Handle the membership changed
        private void membershipChanged() {
            membershipRevision++;
            snapshotRevision = Long.MIN_VALUE;
            lastSnapshotTick = Long.MIN_VALUE;
            nextValidationTick = Long.MIN_VALUE;
            sortedSnapshot = List.of();
        }

        // Get the level topology cache
        private SableAssemblyTopologyCache topologyCache(
                ServerSubLevel root, long gameTime) {
            pruneTopologyCaches(gameTime);
            UUID rootId = root.getUniqueId();
            RootTopologyCache entry = topologyByRoot.get(rootId);
            if (entry == null || entry.root().get() != root) {
                if (entry != null) {
                    entry.cache().invalidate();
                }
                entry = new RootTopologyCache(
                        new WeakReference<>(root),
                        new SableAssemblyTopologyCache(
                                ShipCouplerService::includeTopologyActor,
                                ShipCouplerService::classifyTopologyActor),
                        gameTime);
                topologyByRoot.put(rootId, entry);
            } else {
                entry = entry.accessedAt(gameTime);
                topologyByRoot.put(rootId, entry);
            }
            trimTopologyCaches();
            return entry.cache();
        }

        // Prune the topology caches
        private void pruneTopologyCaches(long gameTime) {
            if (lastTopologyPruneTick != Long.MIN_VALUE
                    && gameTime >= lastTopologyPruneTick
                    && gameTime - lastTopologyPruneTick < TOPOLOGY_CACHE_PRUNE_TICKS) {
                return;
            }
            var iterator = topologyByRoot.entrySet().iterator();
            while (iterator.hasNext()) {
                RootTopologyCache entry = iterator.next().getValue();
                ServerSubLevel root = entry.root().get();
                boolean idle = entry.lastAccessTick() != Long.MIN_VALUE
                        && gameTime >= entry.lastAccessTick()
                        && gameTime - entry.lastAccessTick() >= TOPOLOGY_CACHE_IDLE_TICKS;
                if (root == null || root.isRemoved() || idle) {
                    entry.cache().invalidate();
                    iterator.remove();
                }
            }
            lastTopologyPruneTick = gameTime;
            trimTopologyCaches();
        }

        // Trim the topology caches
        private void trimTopologyCaches() {
            var iterator = topologyByRoot.entrySet().iterator();
            while (topologyByRoot.size() > MAX_TOPOLOGY_ROOTS_PER_LEVEL
                    && iterator.hasNext()) {
                RootTopologyCache entry = iterator.next().getValue();
                entry.cache().invalidate();
                iterator.remove();
            }
        }

        // Clear the level state
        private void clear() {
            topologyByRoot.values().forEach(entry -> entry.cache().invalidate());
            topologyByRoot.clear();
            loadedByEndpoint.clear();
            endpointByCoupler.clear();
            sortedSnapshot = List.of();
        }
    }

    // Store the root topology cache
    private record RootTopologyCache(
            WeakReference<ServerSubLevel> root,
            SableAssemblyTopologyCache cache,
            long lastAccessTick) {
        // Update the topology cache access time
        private RootTopologyCache accessedAt(long gameTime) {
            return new RootTopologyCache(root, cache, gameTime);
        }
    }
}
