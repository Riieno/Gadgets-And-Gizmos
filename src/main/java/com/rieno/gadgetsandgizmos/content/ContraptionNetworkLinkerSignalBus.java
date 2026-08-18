package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.control.FaceBoundSignalRoute;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Share cached redstone signals between linker targets without recursively querying the same network
public final class ContraptionNetworkLinkerSignalBus {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<Level, Map<TargetFaceKey, Map<String, SignalEntry>>> SIGNALS = new WeakHashMap<>();
    private static final Map<Level, Map<PlaneSignalKey, Map<String, Integer>>> PLANE_SIGNALS = new WeakHashMap<>();
    private static final ThreadLocal<SignalQueryCache> QUERY_CACHE = ThreadLocal.withInitial(SignalQueryCache::new);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked injected snapshots
    private static volatile List<InjectedSignalSnapshot> injectedSnapshots = List.of();
    // Tracked plane signal snapshots
    private static volatile List<PlaneSignalSnapshot> planeSignalSnapshots = List.of();
    // Tracks whether injected targets are available
    private static volatile boolean hasInjectedTargets;
    // Tracks whether plane signal targets are available
    private static volatile boolean hasPlaneSignalTargets;
    // Shared signal revision
    private static volatile long signalRevision;
    // Shared plane signal revision
    private static volatile long planeSignalRevision;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker signal bus
    private ContraptionNetworkLinkerSignalBus() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the signal
    public static void setSignal(Level level, BlockPos targetPos, Direction targetFace, String sourceId, int strength) {
        setSignal(level, null, targetPos, targetFace, sourceId, strength, null, false);
    }

    // Set the signal
    public static void setSignal(Level level, BlockPos targetPos, Direction targetFace, String sourceId, int strength,
                                  @Nullable String propertyKey) {
        setSignal(level, null, targetPos, targetFace, sourceId, strength, propertyKey, false);
    }

    // Set the block signal
    public static void setBlockSignal(Level level,
                                      @Nullable UUID subLevelId,
                                      BlockPos targetPos,
                                      String sourceId,
                                      int strength) {
        setSignal(level, subLevelId, targetPos, null, sourceId, strength, null, true);
    }

    // Set the plane signal
    public static void setPlaneSignal(Level level,
                                      @Nullable UUID subLevelId,
                                      BlockPos planePos,
                                      Direction planeFace,
                                      String sourceId,
                                      int strength) {
        if (level == null || planePos == null || planeFace == null || sourceId == null || sourceId.isBlank()) {
            return;
        }

        Level signalLevel = resolveSignalLevel(level);
        BlockPos immutablePos = planePos.immutable();
        UUID resolvedSubLevelId = resolveTargetSubLevelId(level, immutablePos, subLevelId);
        if (!SubLevelBlockEntityCollector.ensureTargetLoaded(level, resolvedSubLevelId, immutablePos)) {
            return;
        }

        boolean changed = false;
        synchronized (SIGNALS) {
            Map<PlaneSignalKey, Map<String, Integer>> byPlane = PLANE_SIGNALS.get(signalLevel);
            PlaneSignalKey key = new PlaneSignalKey(immutablePos, planeFace, resolvedSubLevelId);
            Map<String, Integer> bySource = byPlane == null ? null : byPlane.get(key);
            int previousMax = maxPlaneSignal(bySource);
            Integer previousForSource = bySource == null ? null : bySource.get(sourceId);

            if (strength <= 0) {
                if (bySource != null) {
                    bySource.remove(sourceId);
                }
            } else {
                if (byPlane == null) {
                    byPlane = new HashMap<>();
                    PLANE_SIGNALS.put(signalLevel, byPlane);
                }
                if (bySource == null) {
                    bySource = new HashMap<>();
                    byPlane.put(key, bySource);
                }
                bySource.put(sourceId, Math.min(15, strength));
            }

            int nextMax = maxPlaneSignal(bySource);
            Integer nextForSource = bySource == null ? null : bySource.get(sourceId);
            changed = !Objects.equals(previousForSource, nextForSource) || previousMax != nextMax;

            if (bySource != null && bySource.isEmpty() && byPlane != null) {
                byPlane.remove(key);
            }
            if (byPlane != null && byPlane.isEmpty()) {
                PLANE_SIGNALS.remove(signalLevel);
                if (changed) {
                    refreshPlaneSignalSnapshotLocked(signalLevel, null);
                }
            } else if (changed) {
                refreshPlaneSignalSnapshotLocked(signalLevel, byPlane);
            }
        }

        if (changed) {
            notifyPlaneSignalTarget(level, resolvedSubLevelId, immutablePos, planeFace,
                    level.getBlockState(immutablePos).getBlock());
        }
    }

    // Get the source plane signal
    public static int sourcePlaneSignal(Level level,
                                        @Nullable UUID subLevelId,
                                        BlockPos planePos,
                                        Direction planeFace,
                                        String sourceId) {
        if (level == null || planePos == null || planeFace == null
                || sourceId == null || sourceId.isBlank()) {
            return 0;
        }
        Level signalLevel = resolveSignalLevel(level);
        UUID resolvedSubLevelId = resolveTargetSubLevelId(level, planePos, subLevelId);
        synchronized (SIGNALS) {
            Map<PlaneSignalKey, Map<String, Integer>> byPlane = PLANE_SIGNALS.get(signalLevel);
            if (byPlane == null) return 0;
            Map<String, Integer> bySource = byPlane.get(
                    new PlaneSignalKey(planePos.immutable(), planeFace, resolvedSubLevelId));
            return bySource == null ? 0 : Mth.clamp(bySource.getOrDefault(sourceId, 0), 0, 15);
        }
    }

    // Get the source signal
    public static int sourceSignal(Level level,
                                   @Nullable UUID subLevelId,
                                   BlockPos targetPos,
                                   @Nullable Direction targetFace,
                                   String sourceId) {
        if (level == null || targetPos == null || sourceId == null || sourceId.isBlank()) {
            return 0;
        }
        Level signalLevel = resolveSignalLevel(level);
        UUID resolvedSubLevelId = resolveTargetSubLevelId(level, targetPos, subLevelId);
        synchronized (SIGNALS) {
            Map<TargetFaceKey, Map<String, SignalEntry>> byTarget = SIGNALS.get(signalLevel);
            if (byTarget == null) return 0;
            Map<String, SignalEntry> bySource = byTarget.get(
                    new TargetFaceKey(targetPos.immutable(), targetFace, resolvedSubLevelId));
            return bySource == null ? 0
                    : Mth.clamp(bySource.getOrDefault(sourceId, SignalEntry.ZERO).strength(), 0, 15);
        }
    }

    // Set the signal
    public static void setSignal(Level level,
                                 @Nullable UUID subLevelId,
                                 BlockPos targetPos,
                                 @Nullable Direction targetFace,
                                 String sourceId,
                                 int strength,
                                 @Nullable String propertyKey,
                                 boolean forceInjectedRedstone) {
        // -----------------------------------------------------TARGET CHECKS-----------------------------------------------------
        if (level == null || targetPos == null || sourceId == null || sourceId.isBlank()
                || (targetFace == null && !forceInjectedRedstone)) {
            return;
        }

        // ------------------------------------TARGET RESOLUTION------------------------------------
        Level signalLevel = resolveSignalLevel(level);
        BlockPos immutablePos = targetPos.immutable();
        UUID resolvedSubLevelId = resolveTargetSubLevelId(level, immutablePos, subLevelId);
        if (!SubLevelBlockEntityCollector.ensureTargetLoaded(level, resolvedSubLevelId, immutablePos)) {
            return;
        }
        BlockState targetState = level.getBlockState(immutablePos);
        boolean stateBackedOutput = !forceInjectedRedstone && canRepresentAsTargetState(targetState, propertyKey);
        boolean sourceChanged;
        boolean stateMaxChanged;
        boolean injectedMaxChanged;
        int newSignalStrength;
        // ------------------------------------SIGNAL AGGREGATION------------------------------------
        synchronized (SIGNALS) {
            Map<TargetFaceKey, Map<String, SignalEntry>> byTarget = SIGNALS.computeIfAbsent(signalLevel, ignored -> new HashMap<>());
            TargetFaceKey key = new TargetFaceKey(immutablePos, targetFace, resolvedSubLevelId);
            Map<String, SignalEntry> bySource = byTarget.computeIfAbsent(key, ignored -> new HashMap<>());
            int previousMax = maxSignal(bySource, false);
            int previousInjectedMax = maxSignal(bySource, true);
            SignalEntry previousForSource = bySource.getOrDefault(sourceId, SignalEntry.ZERO);
            if (strength <= 0) {
                bySource.remove(sourceId);
            } else {
                bySource.put(sourceId, new SignalEntry(Math.min(15, strength), !stateBackedOutput));
            }
            SignalEntry nextForSource = bySource.getOrDefault(sourceId, SignalEntry.ZERO);
            int nextMax = maxSignal(bySource, false);
            int nextInjectedMax = maxSignal(bySource, true);
            newSignalStrength = nextMax;
            sourceChanged = !previousForSource.equals(nextForSource);
            stateMaxChanged = previousMax != nextMax;
            injectedMaxChanged = previousInjectedMax != nextInjectedMax;
            if (bySource.isEmpty()) {
                byTarget.remove(key);
            }
            if (byTarget.isEmpty()) {
                SIGNALS.remove(signalLevel);
                if (injectedMaxChanged) {
                    refreshInjectedSnapshotLocked(signalLevel, null);
                }
            } else if (injectedMaxChanged) {
                refreshInjectedSnapshotLocked(signalLevel, byTarget);
            }
        }
        // -----------------------------------------------------BLOCK STATE-----------------------------------------------------
        BlockState updated = targetState;
        boolean shouldBeActive = newSignalStrength > 0;

        if (stateBackedOutput) {
            if (propertyKey != null && !propertyKey.isBlank()) {
                BlockState keyedBoolean = trySetNamedBoolean(updated, propertyKey, shouldBeActive);
                if (keyedBoolean != updated) {
                    updated = keyedBoolean;
                }
                BlockState keyedInteger = trySetNamedInteger(updated, propertyKey, newSignalStrength);
                if (keyedInteger != updated) {
                    updated = keyedInteger;
                }
            }

            if (updated.hasProperty(BlockStateProperties.POWER)) {
                updated = updated.setValue(BlockStateProperties.POWER, newSignalStrength);
            }
            if (updated.hasProperty(BlockStateProperties.LIT)) {
                updated = updated.setValue(BlockStateProperties.LIT, shouldBeActive);
            }
            if (updated.hasProperty(BlockStateProperties.POWERED)) {
                updated = updated.setValue(BlockStateProperties.POWERED, shouldBeActive);
            }

            BlockState namedPower = trySetNamedInteger(updated, "power", newSignalStrength);
            if (namedPower != updated) {
                updated = namedPower;
            }
            BlockState namedSignal = trySetNamedInteger(updated, "signal", newSignalStrength);
            if (namedSignal != updated) {
                updated = namedSignal;
            }
            BlockState namedStrength = trySetNamedInteger(updated, "strength", newSignalStrength);
            if (namedStrength != updated) {
                updated = namedStrength;
            }

            BlockState namedLit = trySetNamedBoolean(updated, "lit", shouldBeActive);
            if (namedLit != updated) {
                updated = namedLit;
            }
            BlockState namedPowered = trySetNamedBoolean(updated, "powered", shouldBeActive);
            if (namedPowered != updated) {
                updated = namedPowered;
            }
        }

        if (!sourceChanged && updated == targetState) {
            return;
        }

        // -----------------------------------------------------STATE COMMIT-----------------------------------------------------
        boolean stateApplied = false;
        if (updated != targetState) {
            KineticBlockEntity kineticBlockEntity = level.getBlockEntity(immutablePos) instanceof KineticBlockEntity kinetic
                    ? kinetic
                    : null;
            if (kineticBlockEntity != null && !level.isClientSide) {
                kineticBlockEntity.detachKinetics();
            }
            level.setBlock(immutablePos, updated, 2);
            stateApplied = true;
            if (kineticBlockEntity != null && !level.isClientSide && !kineticBlockEntity.isRemoved()) {
                kineticBlockEntity.attachKinetics();
                kineticBlockEntity.setChanged();
                kineticBlockEntity.sendData();
            }
        }

        if (injectedMaxChanged && !stateBackedOutput) {
            notifySignalTarget(level, resolvedSubLevelId, immutablePos, targetFace, updated.getBlock());
        } else if (stateApplied || stateMaxChanged) {
            if (!isSignalTargetLoaded(level, resolvedSubLevelId, immutablePos)) {
                return;
            }
            level.sendBlockUpdated(immutablePos, targetState, updated, 2);

            if (stateApplied && isPropagatingStateSource(updated)
                    && isSignalTargetLoaded(level, resolvedSubLevelId, immutablePos)) {
                notifyTargetOutputNeighbors(level, immutablePos, updated.getBlock());
            }
        }
    }

    // Check if the signal target is loaded
    private static boolean isSignalTargetLoaded(Level level, @Nullable UUID subLevelId, BlockPos targetPos) {
        return SubLevelBlockEntityCollector.ensureTargetLoaded(level, subLevelId, targetPos);
    }

    // Notify the signal target
    private static void notifySignalTarget(Level level,
                                           @Nullable UUID subLevelId,
                                           BlockPos targetPos,
                                           @Nullable Direction targetFace,
                                           Block sourceBlock) {
        if (!isSignalTargetLoaded(level, subLevelId, targetPos)) {
            return;
        }
        BlockState targetState = level.getBlockState(targetPos);
        if (targetFace == null) {
            for (Direction dir : Direction.values()) {
                level.neighborChanged(targetPos, sourceBlock, targetPos.relative(dir));
            }
        } else {
            level.neighborChanged(targetPos, sourceBlock, targetPos.relative(targetFace));
        }
        if (!isSignalTargetLoaded(level, subLevelId, targetPos)) {
            return;
        }
        level.sendBlockUpdated(targetPos, targetState, level.getBlockState(targetPos), 2);
    }

    // Notify the plane signal target
    private static void notifyPlaneSignalTarget(Level level,
                                                @Nullable UUID subLevelId,
                                                BlockPos planePos,
                                                Direction planeFace,
                                                Block sourceBlock) {
        if (level == null || planePos == null || planeFace == null) {
            return;
        }
        FaceBoundSignalRoute route = new FaceBoundSignalRoute(planePos, planeFace);
        BlockPos attachedPos = route.attachedPos();
        if (!isSignalTargetLoaded(level, subLevelId, attachedPos)) {
            return;
        }
        BlockState attachedState = level.getBlockState(attachedPos);
        level.neighborChanged(attachedPos, sourceBlock, planePos);
        if (!isSignalTargetLoaded(level, subLevelId, attachedPos)) {
            return;
        }
        level.sendBlockUpdated(attachedPos, attachedState, level.getBlockState(attachedPos), 2);
    }

    // Notify the target output neighbors
    private static void notifyTargetOutputNeighbors(Level level, BlockPos targetPos, Block sourceBlock) {
        level.updateNeighborsAt(targetPos, sourceBlock);
    }

    // Check if this is a propagating state source
    private static boolean isPropagatingStateSource(BlockState state) {
        return state != null && (state.isSignalSource() || state.hasAnalogOutputSignal());
    }

    // Get the maximum signal
    private static int maxSignal(Map<String, SignalEntry> bySource, boolean injectedOnly) {
        int max = 0;
        for (SignalEntry entry : bySource.values()) {
            if (entry == null || (injectedOnly && !entry.injectRedstone())) {
                continue;
            }
            if (entry.strength() > max) {
                max = entry.strength();
            }
        }
        return max;
    }

    // Check if this can represent as target state
    private static boolean canRepresentAsTargetState(BlockState state, @Nullable String propertyKey) {
        if (state == null) {
            return false;
        }

        if (requiresInjectedRedstone(state)) {
            return false;
        }
        if (propertyKey != null && !propertyKey.isBlank()
                && (canSetNamedBoolean(state, propertyKey) || canSetNamedInteger(state, propertyKey))) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.POWER)
                || state.hasProperty(BlockStateProperties.LIT)
                || state.hasProperty(BlockStateProperties.POWERED)
                || canSetNamedInteger(state, "power")
                || canSetNamedInteger(state, "signal")
                || canSetNamedInteger(state, "strength")
                || canSetNamedBoolean(state, "lit")
                || canSetNamedBoolean(state, "powered");
    }

    // Check if this requires injected redstone
    static boolean requiresInjectedRedstone(BlockState state) {
        return state != null && (state.getBlock() instanceof RedStoneWireBlock
                || state.getBlock() instanceof RedstoneLampBlock);
    }

    // Check if this can set named boolean
    private static boolean canSetNamedBoolean(BlockState state, String propertyName) {
        return findNamedBoolean(state, propertyName) != null;
    }

    // Check if this can set named integer
    private static boolean canSetNamedInteger(BlockState state, String propertyName) {
        return findNamedInteger(state, propertyName) != null;
    }

    // Try to set named boolean
    private static BlockState trySetNamedBoolean(BlockState state, String propertyName, boolean val) {
        String normalizedName = propertyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (normalizedName.isBlank()) {
            return state;
        }
        net.minecraft.world.level.block.state.properties.BooleanProperty boolProp = findNamedBoolean(state, normalizedName);
        if (boolProp != null) {
            return state.setValue(boolProp, val);
        }
        return state;
    }

    // Try to set named integer
    private static BlockState trySetNamedInteger(BlockState state, String propertyName, int val) {
        String normalizedName = propertyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (normalizedName.isBlank()) {
            return state;
        }
        IntegerProperty intProp = findNamedInteger(state, normalizedName);
        if (intProp != null) {
            int min = intProp.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
            int max = intProp.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
            int clamped = Math.max(min, Math.min(val, max));
            if (state.getValue(intProp) != clamped) {
                return state.setValue(intProp, clamped);
            }
            return state;
        }
        return state;
    }

    // Find the named boolean
    private static @Nullable net.minecraft.world.level.block.state.properties.BooleanProperty findNamedBoolean(BlockState state, String propertyName) {
        String normalizedName = propertyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (normalizedName.isBlank()) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty boolProp)) {
                continue;
            }
            String propName = property.getName().toLowerCase(Locale.ROOT);
            if (propName.equals(normalizedName) || propName.contains(normalizedName) || normalizedName.contains(propName)) {
                return boolProp;
            }
        }
        return null;
    }

    // Find the named integer
    private static @Nullable IntegerProperty findNamedInteger(BlockState state, String propertyName) {
        String normalizedName = propertyName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (normalizedName.isBlank()) {
            return null;
        }
        for (Property<?> property : state.getProperties()) {
            if (!(property instanceof IntegerProperty intProp)) {
                continue;
            }
            String propName = property.getName().toLowerCase(Locale.ROOT);
            if (propName.equals(normalizedName) || propName.contains(normalizedName) || normalizedName.contains(propName)) {
                return intProp;
            }
        }
        return null;
    }

    // Get the injected signal
    public static int getInjectedSignal(Level level, BlockPos queriedPos, Direction towardTarget) {
        if (level == null || queriedPos == null || towardTarget == null) {
            return 0;
        }
        if (!hasInjectedTargets && !hasPlaneSignalTargets) {
            return 0;
        }
        Level signalLevel = resolveSignalLevel(level);
        InjectedSignalSnapshot snapshot = hasInjectedTargets ? injectedSnapshot(signalLevel) : null;
        PlaneSignalSnapshot planeSnapshot = hasPlaneSignalTargets ? planeSignalSnapshot(signalLevel) : null;
        if (snapshot == null && planeSnapshot == null) {
            return 0;
        }

        BlockPos immutablePos = queriedPos.immutable();
        SignalQueryCache cache = QUERY_CACHE.get();
        int res = 0;
        if (snapshot != null) {
            SignalQueryKey cacheKey = new SignalQueryKey(immutablePos, towardTarget);
            long gameTime = queryGameTime(signalLevel, level);
            Integer cached = cache.getInjected(signalLevel, level, gameTime, snapshot.revision(), cacheKey);
            if (cached != null) {
                res = cached;
            } else {
                res = computeInjectedSignal(snapshot, level, immutablePos, towardTarget, cache);
                cache.putInjected(signalLevel, level, gameTime, snapshot.revision(), cacheKey, res);
            }
        }

        if (planeSnapshot != null && res < 15) {
            res = Math.max(res, computePlaneInjectedSignal(planeSnapshot, level, immutablePos, towardTarget, cache));
        }
        return res;
    }

    // Check if this has neighbor signal
    public static boolean hasNeighborSignal(Level level, BlockPos targetPos) {
        return getBestNeighborSignal(level, targetPos) > 0;
    }

    // Get the best neighbor signal
    public static int getBestNeighborSignal(Level level, BlockPos targetPos) {
        if (level == null || targetPos == null) {
            return 0;
        }
        if (!hasInjectedTargets && !hasPlaneSignalTargets) {
            return 0;
        }
        Level signalLevel = resolveSignalLevel(level);
        InjectedSignalSnapshot snapshot = hasInjectedTargets ? injectedSnapshot(signalLevel) : null;
        PlaneSignalSnapshot planeSnapshot = hasPlaneSignalTargets ? planeSignalSnapshot(signalLevel) : null;
        if (snapshot == null && planeSnapshot == null) {
            return 0;
        }

        BlockPos immutablePos = targetPos.immutable();
        SignalQueryCache cache = QUERY_CACHE.get();
        int res = 0;
        if (snapshot != null) {
            long gameTime = queryGameTime(signalLevel, level);
            Integer cached = cache.getBest(signalLevel, level, gameTime, snapshot.revision(), immutablePos);
            if (cached != null) {
                res = cached;
            } else {
                res = computeBestNeighborSignal(snapshot, level, immutablePos, cache);
                cache.putBest(signalLevel, level, gameTime, snapshot.revision(), immutablePos, res);
            }
        }

        if (planeSnapshot != null && res < 15) {
            res = Math.max(res, computeBestPlaneNeighborSignal(planeSnapshot, level, immutablePos, cache));
        }
        return res;
    }

    // Calculate the injected signal
    private static int computeInjectedSignal(InjectedSignalSnapshot snapshot,
                                             Level level,
                                             BlockPos queriedPos,
                                             Direction towardTarget,
                                             SignalQueryCache cache) {
        BlockPos behindQuery = queriedPos.relative(towardTarget.getOpposite());
        BlockPos aheadQuery = queriedPos.relative(towardTarget);
        boolean hasWorldTarget = snapshot.hasWorldTargetNear(behindQuery, queriedPos, aheadQuery);
        Set<UUID> candidateSubLevelIds = snapshot.subLevelIdsNear(behindQuery, queriedPos, aheadQuery);
        if (!hasWorldTarget && candidateSubLevelIds.isEmpty()) {
            return 0;
        }

        int max = 0;
        if (hasWorldTarget) {
            max = Math.max(max, lookupSignal(snapshot, behindQuery, towardTarget, null));
            max = Math.max(max, lookupSignal(snapshot, queriedPos, towardTarget, null));
            max = Math.max(max, lookupSignal(snapshot, aheadQuery, towardTarget.getOpposite(), null));
            max = Math.max(max, lookupSignal(snapshot, queriedPos, towardTarget.getOpposite(), null));
        }

        if (max >= 15 || candidateSubLevelIds.isEmpty()) {
            return max;
        }

        UUID queriedSubLevelId = cache.getSubLevelId(level, queriedPos, candidateSubLevelIds);
        if (queriedSubLevelId == null) {
            return max;
        }
        max = Math.max(max, lookupSignal(snapshot, behindQuery, towardTarget, queriedSubLevelId));
        max = Math.max(max, lookupSignal(snapshot, queriedPos, towardTarget, queriedSubLevelId));
        max = Math.max(max, lookupSignal(snapshot, aheadQuery, towardTarget.getOpposite(), queriedSubLevelId));
        max = Math.max(max, lookupSignal(snapshot, queriedPos, towardTarget.getOpposite(), queriedSubLevelId));
        return max;
    }

    // Calculate the best neighbor signal
    private static int computeBestNeighborSignal(InjectedSignalSnapshot snapshot,
                                                 Level level,
                                                 BlockPos targetPos,
                                                 SignalQueryCache cache) {
        boolean hasWorldTarget = snapshot.hasWorldTargetNear(targetPos, null, null);
        Set<UUID> candidateSubLevelIds = snapshot.subLevelIdsAt(targetPos);
        if (!hasWorldTarget && candidateSubLevelIds.isEmpty()) {
            return 0;
        }

        int max = 0;
        if (hasWorldTarget) {
            for (Direction dir : DIRECTIONS) {
                max = Math.max(max, lookupSignal(snapshot, targetPos, dir, null));
            }
        }

        if (max >= 15 || candidateSubLevelIds.isEmpty()) {
            return max;
        }

        UUID targetSubLevelId = cache.getSubLevelId(level, targetPos, candidateSubLevelIds);
        if (targetSubLevelId == null) {
            return max;
        }
        for (Direction dir : DIRECTIONS) {
            max = Math.max(max, lookupSignal(snapshot, targetPos, dir, targetSubLevelId));
        }
        return max;
    }

    // Get the maximum plane signal
    private static int maxPlaneSignal(@Nullable Map<String, Integer> bySource) {
        if (bySource == null || bySource.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (Integer strength : bySource.values()) {
            if (strength != null) {
                max = Math.max(max, strength);
            }
        }
        return max;
    }

    // Calculate the plane injected signal
    private static int computePlaneInjectedSignal(PlaneSignalSnapshot snapshot,
                                                  Level level,
                                                  BlockPos queriedPos,
                                                  Direction towardTarget,
                                                  SignalQueryCache cache) {
        int max = lookupPlaneSignal(snapshot, queriedPos, towardTarget, null);
        if (max >= 15) {
            return max;
        }

        Set<UUID> candidateSubLevelIds = snapshot.subLevelIdsAt(queriedPos);
        if (candidateSubLevelIds.isEmpty()) {
            return max;
        }

        UUID queriedSubLevelId = cache.getSubLevelId(level, queriedPos, candidateSubLevelIds);
        if (queriedSubLevelId == null) {
            return max;
        }
        return Math.max(max, lookupPlaneSignal(snapshot, queriedPos, towardTarget, queriedSubLevelId));
    }

    // Get the plane block signal
    public static int getPlaneBlockSignal(Level level, BlockPos planePos, Direction side) {
        if (level == null || planePos == null || side == null || !hasPlaneSignalTargets) {
            return 0;
        }
        Level signalLevel = resolveSignalLevel(level);
        PlaneSignalSnapshot snapshot = planeSignalSnapshot(signalLevel);
        if (snapshot == null) {
            return 0;
        }
        return computePlaneInjectedSignal(snapshot, level, planePos.immutable(), side, QUERY_CACHE.get());
    }

    // Calculate the best plane neighbor signal
    private static int computeBestPlaneNeighborSignal(PlaneSignalSnapshot snapshot,
                                                      Level level,
                                                      BlockPos targetPos,
                                                      SignalQueryCache cache) {
        int max = lookupAttachedPlaneSignal(snapshot, targetPos, null);
        for (Direction dir : DIRECTIONS) {
            BlockPos planePos = targetPos.relative(dir);
            max = Math.max(max, lookupPlaneSignal(snapshot, planePos, dir, null));
        }

        if (max >= 15) {
            return max;
        }

        Set<UUID> attachedSubLevelIds = snapshot.subLevelIdsAt(targetPos);
        if (!attachedSubLevelIds.isEmpty()) {
            UUID targetSubLevelId = cache.getSubLevelId(level, targetPos, attachedSubLevelIds);
            if (targetSubLevelId != null) {
                max = Math.max(max, lookupAttachedPlaneSignal(snapshot, targetPos, targetSubLevelId));
            }
        }

        if (max >= 15) {
            return max;
        }

        for (Direction dir : DIRECTIONS) {
            BlockPos planePos = targetPos.relative(dir);
            Set<UUID> candidateSubLevelIds = snapshot.subLevelIdsAt(planePos);
            if (candidateSubLevelIds.isEmpty()) {
                continue;
            }
            UUID subLevelId = cache.getSubLevelId(level, planePos, candidateSubLevelIds);
            if (subLevelId != null) {
                max = Math.max(max, lookupPlaneSignal(snapshot, planePos, dir, subLevelId));
            }
            if (max >= 15) {
                return max;
            }
        }
        return max;
    }

    // Get the injected snapshot
    private static @Nullable InjectedSignalSnapshot injectedSnapshot(@Nullable Level signalLevel) {
        if (signalLevel == null) {
            return null;
        }
        for (InjectedSignalSnapshot snapshot : injectedSnapshots) {
            if (snapshot.level() == signalLevel) {
                return snapshot;
            }
        }
        return null;
    }

    // Get the plane signal snapshot
    private static @Nullable PlaneSignalSnapshot planeSignalSnapshot(@Nullable Level signalLevel) {
        if (signalLevel == null) {
            return null;
        }
        for (PlaneSignalSnapshot snapshot : planeSignalSnapshots) {
            if (snapshot.level() == signalLevel) {
                return snapshot;
            }
        }
        return null;
    }

    // Query the game time
    private static long queryGameTime(@Nullable Level signalLevel, Level fallbackLevel) {
        try {
            return signalLevel == null ? fallbackLevel.getGameTime() : signalLevel.getGameTime();
        } catch (RuntimeException ignored) {
            return Long.MIN_VALUE;
        }
    }

    // Refresh the injected snapshot locked
    private static void refreshInjectedSnapshotLocked(Level signalLevel,
                                                      @Nullable Map<TargetFaceKey, Map<String, SignalEntry>> byTarget) {
        if (signalLevel == null) {
            return;
        }

        InjectedSignalSnapshot nextSnapshot = null;
        long revision = ++signalRevision;
        if (byTarget != null && !byTarget.isEmpty()) {
            Map<TargetFaceKey, Integer> strengths = new HashMap<>();
            Set<BlockPos> worldPositions = new HashSet<>();
            Map<BlockPos, Set<UUID>> subLevelIdsByPosition = new HashMap<>();
            for (Map.Entry<TargetFaceKey, Map<String, SignalEntry>> entry : byTarget.entrySet()) {
                TargetFaceKey key = entry.getKey();
                int injectedStrength = maxSignal(entry.getValue(), true);
                if (key == null || injectedStrength <= 0) {
                    continue;
                }
                TargetFaceKey snapshotKey = new TargetFaceKey(key.targetPos().immutable(), key.targetFace(), key.subLevelId());
                strengths.put(snapshotKey, injectedStrength);
                if (snapshotKey.subLevelId() == null) {
                    worldPositions.add(snapshotKey.targetPos());
                } else {
                    subLevelIdsByPosition
                            .computeIfAbsent(snapshotKey.targetPos(), ignored -> new HashSet<>())
                            .add(snapshotKey.subLevelId());
                }
            }
            if (!strengths.isEmpty()) {
                nextSnapshot = new InjectedSignalSnapshot(
                        signalLevel,
                        Map.copyOf(strengths),
                        Set.copyOf(worldPositions),
                        freezeSetMap(subLevelIdsByPosition),
                        revision);
            }
        }
        updateInjectedSnapshotListLocked(signalLevel, nextSnapshot);
    }

    // Update the injected snapshot list locked
    private static void updateInjectedSnapshotListLocked(Level signalLevel,
                                                         @Nullable InjectedSignalSnapshot nextSnapshot) {
        List<InjectedSignalSnapshot> current = injectedSnapshots;
        List<InjectedSignalSnapshot> next = new ArrayList<>(current.size() + (nextSnapshot == null ? 0 : 1));
        boolean replaced = false;
        for (InjectedSignalSnapshot snapshot : current) {
            if (snapshot.level() == signalLevel) {
                replaced = true;
                if (nextSnapshot != null) {
                    next.add(nextSnapshot);
                }
                continue;
            }
            next.add(snapshot);
        }
        if (!replaced && nextSnapshot != null) {
            next.add(nextSnapshot);
        }
        injectedSnapshots = next.isEmpty() ? List.of() : List.copyOf(next);
        hasInjectedTargets = !next.isEmpty();
    }

    // Refresh the plane signal snapshot locked
    private static void refreshPlaneSignalSnapshotLocked(Level signalLevel,
                                                         @Nullable Map<PlaneSignalKey, Map<String, Integer>> byPlane) {
        if (signalLevel == null) {
            return;
        }

        PlaneSignalSnapshot nextSnapshot = null;
        long revision = ++planeSignalRevision;
        if (byPlane != null && !byPlane.isEmpty()) {
            Map<PlaneSignalKey, Integer> strengths = new HashMap<>();
            Map<PlaneAttachmentKey, Integer> attachedStrengths = new HashMap<>();
            Map<BlockPos, Set<UUID>> subLevelIdsByPosition = new HashMap<>();
            for (Map.Entry<PlaneSignalKey, Map<String, Integer>> entry : byPlane.entrySet()) {
                PlaneSignalKey key = entry.getKey();
                int strength = maxPlaneSignal(entry.getValue());
                if (key == null || strength <= 0) {
                    continue;
                }
                PlaneSignalKey snapshotKey = new PlaneSignalKey(
                        key.planePos().immutable(),
                        key.planeFace(),
                        key.subLevelId());
                strengths.put(snapshotKey, strength);
                PlaneAttachmentKey attachmentKey = new PlaneAttachmentKey(
                        snapshotKey.planePos().relative(snapshotKey.planeFace().getOpposite()),
                        snapshotKey.subLevelId());
                attachedStrengths.merge(attachmentKey, strength, Math::max);
                if (snapshotKey.subLevelId() != null) {
                    subLevelIdsByPosition
                            .computeIfAbsent(snapshotKey.planePos(), ignored -> new HashSet<>())
                            .add(snapshotKey.subLevelId());
                    subLevelIdsByPosition
                            .computeIfAbsent(attachmentKey.attachedPos(), ignored -> new HashSet<>())
                            .add(snapshotKey.subLevelId());
                }
            }
            if (!strengths.isEmpty()) {
                nextSnapshot = new PlaneSignalSnapshot(
                        signalLevel,
                        Map.copyOf(strengths),
                        Map.copyOf(attachedStrengths),
                        freezeSetMap(subLevelIdsByPosition),
                        revision);
            }
        }
        updatePlaneSignalSnapshotListLocked(signalLevel, nextSnapshot);
    }

    // Update the plane signal snapshot list locked
    private static void updatePlaneSignalSnapshotListLocked(Level signalLevel,
                                                            @Nullable PlaneSignalSnapshot nextSnapshot) {
        List<PlaneSignalSnapshot> current = planeSignalSnapshots;
        List<PlaneSignalSnapshot> next = new ArrayList<>(current.size() + (nextSnapshot == null ? 0 : 1));
        boolean replaced = false;
        for (PlaneSignalSnapshot snapshot : current) {
            if (snapshot.level() == signalLevel) {
                replaced = true;
                if (nextSnapshot != null) {
                    next.add(nextSnapshot);
                }
                continue;
            }
            next.add(snapshot);
        }
        if (!replaced && nextSnapshot != null) {
            next.add(nextSnapshot);
        }
        planeSignalSnapshots = next.isEmpty() ? List.of() : List.copyOf(next);
        hasPlaneSignalTargets = !next.isEmpty();
    }

    // Get the freeze set map
    private static Map<BlockPos, Set<UUID>> freezeSetMap(Map<BlockPos, Set<UUID>> src) {
        if (src == null || src.isEmpty()) {
            return Map.of();
        }
        Map<BlockPos, Set<UUID>> frozen = new HashMap<>();
        src.forEach((pos, ids) -> {
            if (pos != null && ids != null && !ids.isEmpty()) {
                frozen.put(pos, Set.copyOf(ids));
            }
        });
        return frozen.isEmpty() ? Map.of() : Map.copyOf(frozen);
    }

    // Clear the source
    public static void clearSource(Level level, String sourceId) {
        if (level == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        clearPlaneSource(level, sourceId);
        Level signalLevel = resolveSignalLevel(level);
        Set<TargetFaceKey> changedTargets = new java.util.LinkedHashSet<>();
        synchronized (SIGNALS) {
            Map<TargetFaceKey, Map<String, SignalEntry>> byTarget = SIGNALS.get(signalLevel);
            if (byTarget == null || byTarget.isEmpty()) {
                return;
            }
            var targetIterator = byTarget.entrySet().iterator();
            while (targetIterator.hasNext()) {
                Map.Entry<TargetFaceKey, Map<String, SignalEntry>> targetEntry = targetIterator.next();
                Map<String, SignalEntry> bySource = targetEntry.getValue();
                int previousInjectedMax = maxSignal(bySource, true);
                bySource.keySet().removeIf(key -> key.equals(sourceId) || key.startsWith(sourceId + ":"));
                if (previousInjectedMax != maxSignal(bySource, true)) {
                    changedTargets.add(targetEntry.getKey());
                }
                if (bySource.isEmpty()) {
                    targetIterator.remove();
                }
            }
            if (byTarget.isEmpty()) {
                SIGNALS.remove(signalLevel);
                if (!changedTargets.isEmpty()) {
                    refreshInjectedSnapshotLocked(signalLevel, null);
                }
            } else if (!changedTargets.isEmpty()) {
                refreshInjectedSnapshotLocked(signalLevel, byTarget);
            }
        }
        for (TargetFaceKey key : changedTargets) {
            Level targetLevel = resolveTargetLevel(signalLevel, key.subLevelId());
            if (targetLevel != null
                    && SubLevelBlockEntityCollector.ensureTargetLoaded(targetLevel, key.subLevelId(), key.targetPos())) {
                notifySignalTarget(targetLevel, key.subLevelId(), key.targetPos(), key.targetFace(),
                        targetLevel.getBlockState(key.targetPos()).getBlock());
            }
        }
    }

    // Clear the plane source
    private static void clearPlaneSource(Level level, String sourceId) {
        Level signalLevel = resolveSignalLevel(level);
        Set<PlaneSignalKey> changedPlanes = new java.util.LinkedHashSet<>();
        synchronized (SIGNALS) {
            Map<PlaneSignalKey, Map<String, Integer>> byPlane = PLANE_SIGNALS.get(signalLevel);
            if (byPlane == null || byPlane.isEmpty()) {
                return;
            }
            var planeIterator = byPlane.entrySet().iterator();
            while (planeIterator.hasNext()) {
                Map.Entry<PlaneSignalKey, Map<String, Integer>> planeEntry = planeIterator.next();
                Map<String, Integer> bySource = planeEntry.getValue();
                int previousMax = maxPlaneSignal(bySource);
                bySource.keySet().removeIf(key -> key.equals(sourceId) || key.startsWith(sourceId + ":"));
                if (previousMax != maxPlaneSignal(bySource)) {
                    changedPlanes.add(planeEntry.getKey());
                }
                if (bySource.isEmpty()) {
                    planeIterator.remove();
                }
            }
            if (byPlane.isEmpty()) {
                PLANE_SIGNALS.remove(signalLevel);
                if (!changedPlanes.isEmpty()) {
                    refreshPlaneSignalSnapshotLocked(signalLevel, null);
                }
            } else if (!changedPlanes.isEmpty()) {
                refreshPlaneSignalSnapshotLocked(signalLevel, byPlane);
            }
        }
        for (PlaneSignalKey key : changedPlanes) {
            Level targetLevel = resolveTargetLevel(signalLevel, key.subLevelId());
            if (targetLevel != null
                    && SubLevelBlockEntityCollector.ensureTargetLoaded(targetLevel, key.subLevelId(), key.planePos())) {
                notifyPlaneSignalTarget(targetLevel, key.subLevelId(), key.planePos(), key.planeFace(),
                        targetLevel.getBlockState(key.planePos()).getBlock());
            }
        }
    }

    // Clear every cached linker signal
    public static void clearAll() {
        synchronized (SIGNALS) {
            SIGNALS.clear();
            PLANE_SIGNALS.clear();
            injectedSnapshots = List.of();
            planeSignalSnapshots = List.of();
            hasInjectedTargets = false;
            hasPlaneSignalTargets = false;
            signalRevision++;
            planeSignalRevision++;
        }
        QUERY_CACHE.remove();
    }

    // Get the lookup signal
    private static int lookupSignal(InjectedSignalSnapshot snapshot,
                                    BlockPos targetPos,
                                    Direction targetFace,
                                    @Nullable UUID subLevelId) {
        if (snapshot == null || targetPos == null || targetFace == null) {
            return 0;
        }
        int max = lookupSignalKey(snapshot, targetPos, targetFace, subLevelId);
        if (subLevelId != null) {
            max = Math.max(max, lookupSignalKey(snapshot, targetPos, targetFace, null));
        }
        max = Math.max(max, lookupSignalKey(snapshot, targetPos, null, subLevelId));
        if (subLevelId != null) {
            max = Math.max(max, lookupSignalKey(snapshot, targetPos, null, null));
        }
        return max;
    }

    // Get the lookup signal key
    private static int lookupSignalKey(InjectedSignalSnapshot snapshot,
                                       BlockPos targetPos,
                                       @Nullable Direction targetFace,
                                       @Nullable UUID subLevelId) {
        return snapshot.strengths().getOrDefault(new TargetFaceKey(targetPos, targetFace, subLevelId), 0);
    }

    // Get the lookup plane signal
    private static int lookupPlaneSignal(PlaneSignalSnapshot snapshot,
                                         BlockPos planePos,
                                         Direction planeFace,
                                         @Nullable UUID subLevelId) {
        if (snapshot == null || planePos == null || planeFace == null) {
            return 0;
        }
        int max = snapshot.strengths().getOrDefault(new PlaneSignalKey(planePos, planeFace, subLevelId), 0);
        if (subLevelId != null) {
            max = Math.max(max, snapshot.strengths().getOrDefault(new PlaneSignalKey(planePos, planeFace, null), 0));
        }
        return max;
    }

    // Get the lookup attached plane signal
    private static int lookupAttachedPlaneSignal(PlaneSignalSnapshot snapshot,
                                                 BlockPos attachedPos,
                                                 @Nullable UUID subLevelId) {
        if (snapshot == null || attachedPos == null) {
            return 0;
        }
        int max = snapshot.attachedStrengths().getOrDefault(new PlaneAttachmentKey(attachedPos, subLevelId), 0);
        if (subLevelId != null) {
            max = Math.max(max, snapshot.attachedStrengths().getOrDefault(new PlaneAttachmentKey(attachedPos, null), 0));
        }
        return max;
    }

    // Resolve the signal level
    private static Level resolveSignalLevel(Level level) {
        if (level == null) {
            return null;
        }
        Level serverLevel = SableLevelApi.serverLevel(level);
        return serverLevel == null ? level : serverLevel;
    }

    // Resolve the target level
    private static @Nullable Level resolveTargetLevel(Level rootLevel, @Nullable UUID subLevelId) {
        if (rootLevel == null || subLevelId == null) {
            return rootLevel;
        }
        return SubLevelBlockEntityCollector.ensureSubLevelLoaded(rootLevel, subLevelId) == null ? null : rootLevel;
    }

    // Resolve the target sublevel id
    private static @Nullable UUID resolveTargetSubLevelId(Level level, BlockPos pos, @Nullable UUID explicitSubLevelId) {
        return resolveTargetSubLevelIdUncached(level, pos, explicitSubLevelId, null);
    }

    // Resolve the target sublevel id uncached
    private static @Nullable UUID resolveTargetSubLevelIdUncached(Level level,
                                                                  BlockPos pos,
                                                                  @Nullable UUID explicitSubLevelId,
                                                                  @Nullable Set<UUID> candidateSubLevelIds) {
        if (explicitSubLevelId != null) {
            return matchesCandidateSubLevel(explicitSubLevelId, candidateSubLevelIds) ? explicitSubLevelId : null;
        }
        if (level == null || pos == null) {
            return null;
        }
        UUID fromContaining = SableLevelApi.containingId(level, Vec3.atCenterOf(pos));
        return matchesCandidateSubLevel(fromContaining, candidateSubLevelIds) ? fromContaining : null;
    }

    // Check if this matches candidate sublevel
    private static boolean matchesCandidateSubLevel(@Nullable UUID subLevelId, @Nullable Set<UUID> candidateSubLevelIds) {
        return subLevelId != null && (candidateSubLevelIds == null || candidateSubLevelIds.contains(subLevelId));
    }

    // Store the injected signal snapshot
    private record InjectedSignalSnapshot(Level level,
                                          Map<TargetFaceKey, Integer> strengths,
                                          Set<BlockPos> worldTargetPositions,
                                          Map<BlockPos, Set<UUID>> subLevelIdsByPosition,
                                          long revision) {
        // Check for a nearby world target
        private boolean hasWorldTargetNear(@Nullable BlockPos first,
                                           @Nullable BlockPos second,
                                           @Nullable BlockPos third) {
            return containsAny(worldTargetPositions, first, second, third);
        }

        // Get the sublevel ids
        private Set<UUID> subLevelIdsAt(@Nullable BlockPos pos) {
            if (pos == null) {
                return Set.of();
            }
            return subLevelIdsByPosition.getOrDefault(pos, Set.of());
        }

        // Get nearby sublevel ids
        private Set<UUID> subLevelIdsNear(@Nullable BlockPos first,
                                          @Nullable BlockPos second,
                                          @Nullable BlockPos third) {
            if (subLevelIdsByPosition.isEmpty()) {
                return Set.of();
            }
            Set<UUID> ids = new HashSet<>();
            addSubLevelIds(ids, first);
            addSubLevelIds(ids, second);
            addSubLevelIds(ids, third);
            return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
        }

        // Add the sublevel ids
        private void addSubLevelIds(Set<UUID> ids, @Nullable BlockPos pos) {
            if (pos != null) {
                ids.addAll(subLevelIdsByPosition.getOrDefault(pos, Set.of()));
            }
        }

        // Check if the positions contain any candidate
        private static boolean containsAny(Set<BlockPos> positions,
                                           @Nullable BlockPos first,
                                           @Nullable BlockPos second,
                                           @Nullable BlockPos third) {
            return !positions.isEmpty()
                    && ((first != null && positions.contains(first))
                    || (second != null && positions.contains(second))
                    || (third != null && positions.contains(third)));
        }
    }

    // Store the plane signal snapshot
    private record PlaneSignalSnapshot(Level level,
                                       Map<PlaneSignalKey, Integer> strengths,
                                       Map<PlaneAttachmentKey, Integer> attachedStrengths,
                                       Map<BlockPos, Set<UUID>> subLevelIdsByPosition,
                                       long revision) {
        // Get the sublevel ids
        private Set<UUID> subLevelIdsAt(@Nullable BlockPos pos) {
            if (pos == null) {
                return Set.of();
            }
            return subLevelIdsByPosition.getOrDefault(pos, Set.of());
        }
    }

    // Store the plane attachment key
    private record PlaneAttachmentKey(BlockPos attachedPos, @Nullable UUID subLevelId) {
        // Compare this plane attachment key with another object
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PlaneAttachmentKey key)) {
                return false;
            }
            return Objects.equals(attachedPos, key.attachedPos)
                    && Objects.equals(subLevelId, key.subLevelId);
        }

        // Generate the plane attachment key hash
        @Override
        public int hashCode() {
            return Objects.hash(attachedPos, subLevelId);
        }
    }

    // Store the signal query key
    private record SignalQueryKey(BlockPos pos, Direction direction) {
    }

    // Handle the signal query cache
    private static final class SignalQueryCache {
        // Current signal level
        private @Nullable Level signalLevel;
        // Current query level
        private @Nullable Level queryLevel;
        // Current game time
        private long gameTime = Long.MIN_VALUE;
        // Current revision
        private long revision = Long.MIN_VALUE;
        // Tracked injected signals
        private final Map<SignalQueryKey, Integer> injectedSignals = new HashMap<>();
        // Tracked best neighbor signals
        private final Map<BlockPos, Integer> bestNeighborSignals = new HashMap<>();
        // Tracked sub-level ids
        private final Map<BlockPos, UUID> subLevelIds = new HashMap<>();
        // Tracked sub-level misses
        private final Set<BlockPos> subLevelMisses = new HashSet<>();

        // Get the injected
        private @Nullable Integer getInjected(Level signalLevel,
                                              Level queryLevel,
                                              long gameTime,
                                              long revision,
                                              SignalQueryKey key) {
            prepare(signalLevel, queryLevel, gameTime, revision);
            return injectedSignals.get(key);
        }

        // Put the injected
        private void putInjected(Level signalLevel,
                                 Level queryLevel,
                                 long gameTime,
                                 long revision,
                                 SignalQueryKey key,
                                 int val) {
            prepare(signalLevel, queryLevel, gameTime, revision);
            injectedSignals.put(key, val);
        }

        // Get the best
        private @Nullable Integer getBest(Level signalLevel,
                                          Level queryLevel,
                                          long gameTime,
                                          long revision,
                                          BlockPos pos) {
            prepare(signalLevel, queryLevel, gameTime, revision);
            return bestNeighborSignals.get(pos);
        }

        // Put the best
        private void putBest(Level signalLevel,
                             Level queryLevel,
                             long gameTime,
                             long revision,
                             BlockPos pos,
                             int val) {
            prepare(signalLevel, queryLevel, gameTime, revision);
            bestNeighborSignals.put(pos, val);
        }

        // Get the sublevel id
        private @Nullable UUID getSubLevelId(Level queryLevel, BlockPos pos, Set<UUID> candidateSubLevelIds) {
            if (queryLevel == null || pos == null || candidateSubLevelIds == null || candidateSubLevelIds.isEmpty()) {
                return null;
            }
            UUID cached = subLevelIds.get(pos);
            if (cached != null) {
                return candidateSubLevelIds.contains(cached) ? cached : null;
            }
            if (subLevelMisses.contains(pos)) {
                return null;
            }
            UUID resolved = resolveTargetSubLevelIdUncached(queryLevel, pos, null, null);
            if (resolved == null) {
                subLevelMisses.add(pos);
                return null;
            }
            subLevelIds.put(pos, resolved);
            return candidateSubLevelIds.contains(resolved) ? resolved : null;
        }

        // Prepare the signal query cache
        private void prepare(Level signalLevel, Level queryLevel, long gameTime, long revision) {
            if (this.signalLevel == signalLevel
                    && this.queryLevel == queryLevel
                    && this.gameTime == gameTime
                    && this.revision == revision) {
                return;
            }
            this.signalLevel = signalLevel;
            this.queryLevel = queryLevel;
            this.gameTime = gameTime;
            this.revision = revision;
            injectedSignals.clear();
            bestNeighborSignals.clear();
            subLevelIds.clear();
            subLevelMisses.clear();
        }
    }

    // Store the signal entry
    private record SignalEntry(int strength, boolean injectRedstone) {
        private static final SignalEntry ZERO = new SignalEntry(0, false);
    }

    // Store the plane signal key
    private record PlaneSignalKey(BlockPos planePos, Direction planeFace, @Nullable UUID subLevelId) {
        // Compare this plane signal key with another object
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PlaneSignalKey key)) {
                return false;
            }
            return Objects.equals(planePos, key.planePos)
                    && planeFace == key.planeFace
                    && Objects.equals(subLevelId, key.subLevelId);
        }

        // Generate the plane signal key hash
        @Override
        public int hashCode() {
            return Objects.hash(planePos, planeFace, subLevelId);
        }
    }

    // Store the target face key
    private record TargetFaceKey(BlockPos targetPos, @Nullable Direction targetFace, @Nullable UUID subLevelId) {
        // Compare this target face key with another object
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TargetFaceKey key)) {
                return false;
            }
            return Objects.equals(targetPos, key.targetPos)
                    && targetFace == key.targetFace
                    && Objects.equals(subLevelId, key.subLevelId);
        }

        // Generate the target face key hash
        @Override
        public int hashCode() {
            return Objects.hash(targetPos, targetFace, subLevelId);
        }
    }
}
