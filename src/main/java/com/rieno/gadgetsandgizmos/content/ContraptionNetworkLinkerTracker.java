package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.ContraptionDiagramControllerCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import org.joml.Vector3d;

// Keep linker targets attached to their moving sub-level positions and rebuild their lookup index when needed
public final class ContraptionNetworkLinkerTracker extends SavedData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<MinecraftServer, ContraptionNetworkLinkerTracker> LOADED_TRACKERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final String FILE_ID = "createthrusters_linker_tracker";
    private static final String LINKERS_KEY = "Linkers";
    private static final String LINKER_ID_KEY = "LinkerId";
    private static final String CONTROLLER_KEY = "Controller";
    private static final String TARGETS_KEY = "Targets";
    private static final String DIMENSION_KEY = "Dimension";
    private static final String ANCHOR_X_KEY = "AnchorX";
    private static final String ANCHOR_Y_KEY = "AnchorY";
    private static final String ANCHOR_Z_KEY = "AnchorZ";
    private static final String CURRENT_SUBLEVEL_KEY = "CurrentSubLevelId";
    private static final String CURRENT_LOCAL_POS_KEY = "CurrentLocalPos";
    private static final String NODE_ID_KEY = "NodeId";
    private static final String NODE_ALIASES_KEY = "NodeAliases";
    private static final String TRACKING_POINT_ID_KEY = "TrackingPointId";
    private static final String PLANE_ORIENTATION_KEY = "PlaneOrientation";
    private static final String PENDING_FACE_QUARTER_TURNS_KEY = "PendingFaceQuarterTurns";
    private static final String BLOCK_ID_KEY = "BlockId";
    private static final String LABEL_KEY = "Label";
    private static final String MODE_KEY = "Mode";
    private static final String SCOPE_KEY = "Scope";
    private static final String FACES_KEY = "Faces";
    private static final String FACE_KEY = "Face";
    private static final String FACE_LABEL_KEY = "FaceLabel";
    private static final String FACE_SIGNAL_KEY = "FaceSignalKey";
    private static final long RELOCATION_GRACE_TICKS = 1_200L;
    private static final long SAFETY_RECONCILE_INTERVAL_TICKS = 20L;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked linkers
    private final Map<UUID, LinkerRecord> trackedLinkers = new LinkedHashMap<>();
    // Targets indexed by node id
    private final Map<String, List<IndexedTarget>> targetsByNodeId = new LinkedHashMap<>();
    // Tracks whether target index is dirty
    private boolean targetIndexDirty = true;
    // Tracks whether contraption network linker tracker is shutting down
    private boolean shuttingDown;
    // Last safety reconcile tick
    private long lastSafetyReconcileTick = Long.MIN_VALUE;
    // Tracks whether reconciliation is requested
    private boolean reconciliationRequested = true;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the contraption network linker tracker value
    public static ContraptionNetworkLinkerTracker get(MinecraftServer server) {
        ContraptionNetworkLinkerTracker pending = LOADED_TRACKERS.get(server);
        ServerLevel overworld = server == null ? null : server.overworld();
        if (overworld == null) {
            // Sable restores plot chunks while ServerLevel construction is still in progress
            // Keep the requested reconciliation until the overworld can own the saved data
            if (pending != null) {
                return pending;
            }
            ContraptionNetworkLinkerTracker tracker = new ContraptionNetworkLinkerTracker();
            LOADED_TRACKERS.put(server, tracker);
            return tracker;
        }
        ContraptionNetworkLinkerTracker tracker = overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        ContraptionNetworkLinkerTracker::new,
                        (tag, provider) -> ContraptionNetworkLinkerTracker.load(tag, provider),
                        null),
                FILE_ID);
        LOADED_TRACKERS.put(server, tracker);
        return tracker;
    }

    // Begin the shutdown if loaded
    public static void beginShutdownIfLoaded(MinecraftServer server) {
        ContraptionNetworkLinkerTracker tracker = server == null
                ? null : LOADED_TRACKERS.get(server);
        if (tracker != null) {
            tracker.beginShutdown();
        }
    }

    // Finish the shutdown
    public static void finishShutdown(MinecraftServer server) {
        if (server != null) {
            LOADED_TRACKERS.remove(server);
        }
    }

    // Load the contraption network linker tracker
    private static ContraptionNetworkLinkerTracker load(CompoundTag tag, HolderLookup.Provider provider) {
        ContraptionNetworkLinkerTracker tracker = new ContraptionNetworkLinkerTracker();
        ListTag linkersTag = tag.getList(LINKERS_KEY, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < linkersTag.size(); idx++) {
            CompoundTag linkerTag = linkersTag.getCompound(idx);
            if (!linkerTag.hasUUID(LINKER_ID_KEY)) {
                continue;
            }
            UUID linkerId = linkerTag.getUUID(LINKER_ID_KEY);
            LinkerRecord record = LinkerRecord.load(linkerTag);
            if (record != null) {
                tracker.trackedLinkers.put(linkerId, record);
            }
        }
        return tracker;
    }

    // Sync the controller
    public void syncController(AnalogueContraptionControllerBlockEntity controller) {
        syncController(controller, false);
    }

    // Sync and reconcile the controller
    public boolean syncAndReconcileController(AnalogueContraptionControllerBlockEntity controller) {
        return syncController(controller, true);
    }

    // Observe the linker
    public boolean observeLinker(ServerLevel observationLevel, ItemStack linker) {
        if (observationLevel == null || linker == null || linker.isEmpty()) {
            return false;
        }

        ContraptionNetworkLinkerData.TargetReadResult targetRead =
                ContraptionNetworkLinkerData.readAuthoritativeTargets(linker);
        if (!targetRead.authoritative()) {
            return false;
        }
        List<ContraptionNetworkLinkerData.LinkedTarget> itemTargets = targetRead.targets();
        UUID linkerId = targetRead.linkerId();
        if (itemTargets.isEmpty()) {
            if (linkerId != null && removeLinkerRecord(linkerId, observationLevel.getServer())) {
                setDirty();
            }
            return false;
        }

        if (linkerId == null) {
            linkerId = ContraptionNetworkLinkerData.getOrCreateLinkerId(linker);
        }
        LinkerRecord record = trackedLinkers.get(linkerId);
        boolean changed = false;
        if (record == null) {
            record = new LinkerRecord();
            record.dimensionId = observationLevel.dimension().location().toString();
            trackedLinkers.put(linkerId, record);
            invalidateTargetIndex();
            changed = true;
        }

        ServerLevel targetLevel = resolveLevel(observationLevel.getServer(), record.dimensionId);
        if (targetLevel == null) {
            if (changed) {
                setDirty();
            }
            return false;
        }
        changed |= syncTrackedTargets(record, targetLevel, itemTargets);
        ReconcileResult reconcileResult = reconcileTrackedLinker(record, targetLevel, linker, itemTargets,
                targetRead.editMode(), targetRead.targetMode());
        changed |= reconcileResult.changed();
        if (changed) {
            setDirty();
        }
        return reconcileResult.itemChanged();
    }

    // Check if this can mutate linker
    public boolean canMutateLinker(ServerLevel level, ItemStack linker) {
        if (level == null || linker == null || linker.isEmpty()) {
            return false;
        }
        ContraptionNetworkLinkerData.TargetReadResult targetRead =
                ContraptionNetworkLinkerData.readAuthoritativeTargets(linker);
        if (!targetRead.authoritative()) {
            return false;
        }
        UUID linkerId = targetRead.linkerId();
        LinkerRecord record = linkerId == null ? null : trackedLinkers.get(linkerId);
        return record == null || Objects.equals(record.dimensionId, level.dimension().location().toString());
    }

    // Sync the controller
    private boolean syncController(AnalogueContraptionControllerBlockEntity controller, boolean reconcileNow) {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide) {
            return false;
        }

        ControllerLocator locator = ControllerLocator.capture(controller);
        ItemStack linker = controller.getStoredLinker();
        ServerLevel serverLevel = SableLevelApi.serverLevel(controller.getLevel());
        if (linker.isEmpty()) {
            if (detachController(locator, null)) {
                setDirty();
            }
            return false;
        }

        ContraptionNetworkLinkerData.TargetReadResult targetRead =
                ContraptionNetworkLinkerData.readAuthoritativeTargets(linker);
        if (!targetRead.authoritative()) {
            return false;
        }
        List<ContraptionNetworkLinkerData.LinkedTarget> itemTargets = targetRead.targets();
        UUID linkerId = targetRead.linkerId();
        if (itemTargets.isEmpty()) {
            boolean changed = detachController(locator, null);
            if (linkerId != null && serverLevel != null) {
                changed |= removeLinkerRecord(linkerId, serverLevel.getServer());
            }
            if (changed) {
                setDirty();
            }
            return false;
        }

        if (linkerId == null) {
            linkerId = ContraptionNetworkLinkerData.getOrCreateLinkerId(linker);
        }
        boolean changed = detachController(locator, linkerId);
        LinkerRecord record = trackedLinkers.get(linkerId);
        if (record == null) {
            record = new LinkerRecord();
            record.dimensionId = locator.dimensionId;
            trackedLinkers.put(linkerId, record);
            invalidateTargetIndex();
            changed = true;
        }
        if (!Objects.equals(record.dimensionId, locator.dimensionId)) {
            if (changed) {
                setDirty();
            }
            return false;
        }
        if (record.controller == null || !record.controller.sameResolvedLocation(locator)) {
            record.controller = locator;
            changed = true;
        }

        ServerLevel targetLevel = serverLevel == null
                ? null
                : resolveLevel(serverLevel.getServer(), record.dimensionId);
        if (targetLevel != null && syncTrackedTargets(record, targetLevel, itemTargets)) {
            changed = true;
        }
        boolean itemChanged = false;
        if (reconcileNow && targetLevel != null) {
            ReconcileResult reconcileResult = reconcileTrackedLinker(record, targetLevel, linker, itemTargets,
                    targetRead.editMode(), targetRead.targetMode());
            changed |= reconcileResult.changed();
            itemChanged = reconcileResult.itemChanged();
        }
        if (changed) {
            setDirty();
        }
        return itemChanged;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the contraption network linker tracker
    public void tick(MinecraftServer server) {
        if (server == null || shuttingDown) {
            return;
        }
        long gameTick = server.getTickCount();
        if (!reconciliationRequested && lastSafetyReconcileTick != Long.MIN_VALUE
                && gameTick >= lastSafetyReconcileTick
                && gameTick - lastSafetyReconcileTick < SAFETY_RECONCILE_INTERVAL_TICKS) {
            return;
        }
        reconciliationRequested = false;
        lastSafetyReconcileTick = gameTick;

        boolean dirty = false;
        // ------------------------------------TRACKED LINKERS------------------------------------
        Iterator<Map.Entry<UUID, LinkerRecord>> iterator = trackedLinkers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LinkerRecord> entry = iterator.next();
            UUID linkerId = entry.getKey();
            LinkerRecord record = entry.getValue();
            ServerLevel targetLevel = resolveLevel(server, record.dimensionId);
            if (targetLevel == null) {
                continue;
            }

            for (TargetRecord target : record.targets) {
                if (target.resolve(targetLevel)) {
                    dirty = true;
                }
            }

            // ------------------------------------CONTROLLER LOCATION------------------------------------
            ControllerLocator locator = record.controller;
            if (locator == null) {
                continue;
            }
            ServerLevel controllerLevel = resolveLevel(server, locator.dimensionId);
            if (controllerLevel == null) {
                continue;
            }

            ResolvedLocation controllerLocation = resolveLocation(controllerLevel,
                    locator.anchorX,
                    locator.anchorY,
                    locator.anchorZ,
                    locator.currentSubLevelId,
                    locator.currentLocalPos);
            if (locator.apply(controllerLocation)) {
                dirty = true;
            }

            AnalogueContraptionControllerBlockEntity controller = resolveController(controllerLevel, locator);
            if (controller == null) {
                if (locator.confirmedMissing(controllerLevel)) {
                    record.controller = null;
                    dirty = true;
                }
                continue;
            }
            locator.markAvailable();

            // ------------------------------------LINKER DATA------------------------------------
            ItemStack linker = controller.getStoredLinker();
            ContraptionNetworkLinkerData.TargetReadResult targetRead =
                    ContraptionNetworkLinkerData.readAuthoritativeTargets(linker);
            if (!targetRead.authoritative()) {
                continue;
            }
            UUID currentLinkerId = targetRead.linkerId();
            if (linker.isEmpty() || currentLinkerId == null || !currentLinkerId.equals(linkerId)) {
                record.controller = null;
                dirty = true;
                continue;
            }
            if (targetRead.targets().isEmpty()) {
                record.removeTrackingPoints(targetLevel);
                iterator.remove();
                invalidateTargetIndex();
                dirty = true;
                continue;
            }
            if (syncTrackedTargets(record, targetLevel, targetRead.targets())) {
                dirty = true;
            }

            // ------------------------------------TARGET RECONCILIATION------------------------------------
            ReconcileResult reconcileResult = reconcileTrackedLinker(
                    record, targetLevel, linker, targetRead.targets(),
                    targetRead.editMode(), targetRead.targetMode());
            if (reconcileResult.itemChanged()) {
                controller.onTrackedLinkerDataUpdated();
            }
            if (reconcileResult.changed()) {
                dirty = true;
            }
        }

        // -----------------------------------------------------SAVE TRACKER-----------------------------------------------------
        if (dirty) {
            setDirty();
        }
    }

    // Request the reconciliation
    public void requestReconciliation() {
        reconciliationRequested = true;
    }

    // Begin the shutdown
    public void beginShutdown() {
        shuttingDown = true;
    }

    // Get the active tracking point ids
    public Set<UUID> activeTrackingPointIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (LinkerRecord record : trackedLinkers.values()) {
            record.collectTrackingPointIds(ids);
        }
        return ids;
    }

    // Remap the assembly targets
    public void remapAssemblyTargets(ServerLevel level,
                                     BoundingBox3ic assemblyBounds,
                                     @Nullable ServerSubLevel destinationSubLevel,
                                     SubLevelAssemblyHelper.AssemblyTransform transform) {
        if (level == null || assemblyBounds == null || transform == null) {
            return;
        }
        String dimensionId = level.dimension().location().toString();
        ServerLevel resultingLevel = transform.getLevel();
        boolean changedDimension = resultingLevel != level;
        boolean changed = false;
        for (LinkerRecord linker : trackedLinkers.values()) {
            if (!Objects.equals(linker.dimensionId, dimensionId)) {
                continue;
            }
            boolean controllerMoved = linker.controller != null
                    && linker.controller.movesWithAssemblyBounds(assemblyBounds);
            boolean allUnattachedTargetsMoved = linker.controller == null
                    && !linker.targets.isEmpty()
                    && linker.targets.stream().allMatch(
                    target -> target.movesWithAssemblyBounds(assemblyBounds));
            if (controllerMoved) {
                changed |= linker.controller.remapAssemblyLocation(
                        resultingLevel, destinationSubLevel, transform);
            }
            if (changedDimension && (controllerMoved || allUnattachedTargetsMoved)) {
                linker.dimensionId = resultingLevel.dimension().location().toString();
                changed = true;
            }
            for (TargetRecord target : linker.targets) {
                if (changedDimension && target.movesWithAssemblyBounds(assemblyBounds)) {
                    target.removeTrackingPoint(level);
                }
                changed |= target.remapAssemblyTarget(
                        resultingLevel, assemblyBounds, destinationSubLevel, transform);
            }
        }
        if (changed) {
            setDirty();
        }
    }

    // Collect the links
    public List<ContraptionNetworkLinkerData.LinkedTarget> collectLinks(ServerLevel level, LinkMatcher matcher) {
        if (level == null || matcher == null) {
            return List.of();
        }
        List<ContraptionNetworkLinkerData.LinkedTarget> links = new ArrayList<>();
        for (LinkerRecord record : trackedLinkers.values()) {
            ServerLevel recordLevel = resolveLevel(level.getServer(), record.dimensionId);
            if (recordLevel != level) {
                continue;
            }
            for (TargetRecord target : record.targets) {
                target.resolve(level);
                ContraptionNetworkLinkerData.LinkedTarget linkedTarget = target.toLinkedTarget();
                if (matcher.matches(linkedTarget, target.worldCenter())) {
                    links.add(linkedTarget);
                }
            }
        }
        return List.copyOf(links);
    }

    // Clear the links
    public ClearLinksSummary clearLinks(ServerLevel level, LinkMatcher matcher) {
        if (level == null || matcher == null) {
            return new ClearLinksSummary(0, 0);
        }
        int updatedLinkers = 0;
        int removedTargets = 0;
        boolean dirty = false;
        for (Map.Entry<UUID, LinkerRecord> entry : trackedLinkers.entrySet()) {
            LinkerRecord record = entry.getValue();
            ServerLevel recordLevel = resolveLevel(level.getServer(), record.dimensionId);
            if (recordLevel != level) {
                continue;
            }
            List<TargetRecord> nextRecords = new ArrayList<>();
            Set<String> removedNodeIds = new LinkedHashSet<>();
            for (TargetRecord target : record.targets) {
                if (target.resolve(level)) {
                    dirty = true;
                }
                ContraptionNetworkLinkerData.LinkedTarget linkedTarget = target.toLinkedTarget();
                if (matcher.matches(linkedTarget, target.worldCenter())) {
                    removedNodeIds.add(target.nodeId);
                    target.removeTrackingPoint(level);
                    removedTargets++;
                    dirty = true;
                    continue;
                }
                nextRecords.add(target);
            }

            if (removedNodeIds.isEmpty()) {
                continue;
            }

            record.targets = nextRecords;
            invalidateTargetIndex();
            ControllerLocator locator = record.controller;
            ServerLevel controllerLevel = locator == null ? null : resolveLevel(level.getServer(), locator.dimensionId);
            AnalogueContraptionControllerBlockEntity controller = controllerLevel == null
                    ? null
                    : resolveController(controllerLevel, locator);
            if (controller == null) {
                continue;
            }
            ItemStack linker = controller.getStoredLinker();
            ContraptionNetworkLinkerData.TargetReadResult targetRead =
                    ContraptionNetworkLinkerData.readAuthoritativeTargets(linker);
            UUID currentLinkerId = targetRead.linkerId();
            if (!targetRead.authoritative() || linker.isEmpty() || currentLinkerId == null
                    || !currentLinkerId.equals(entry.getKey())) {
                continue;
            }
            List<ContraptionNetworkLinkerData.LinkedTarget> nextTargets = new ArrayList<>();
            for (TargetRecord target : nextRecords) {
                nextTargets.add(target.toLinkedTarget());
            }
            if (ContraptionNetworkLinkerData.rewriteTrackedTargets(
                    linker,
                    nextTargets,
                    targetRead.editMode(),
                    targetRead.targetMode(),
                    Map.of(),
                    removedNodeIds,
                    Map.of())) {
                controller.onTrackedLinkerDataUpdated();
                updatedLinkers++;
            }
        }
        if (dirty) {
            setDirty();
        }
        return new ClearLinksSummary(updatedLinkers, removedTargets);
    }

    // Resolve the target reference
    public @Nullable ControllerDirectTargetReference resolveTargetReference(Level level,
                                                                            @Nullable ControllerDirectTargetReference reference) {
        if (level == null || reference == null || !reference.isBound() || level.getServer() == null
                || !ContraptionNetworkLinkerData.isLinkerFaceTarget(reference)) {
            return reference;
        }
        ServerLevel serverLevel = level.getServer().getLevel(level.dimension());
        if (serverLevel == null) {
            return reference;
        }
        String baseNodeId = ContraptionNetworkLinkerData.baseNodeIdFromTargetId(reference.targetId());
        for (IndexedTarget indexed : indexedTargets(baseNodeId)) {
            if (resolveLevel(level.getServer(), indexed.dimensionId()) != serverLevel) {
                continue;
            }
            TargetRecord target = indexed.target();
            boolean changed = target.resolve(serverLevel);
            boolean identityChanged = false;
            String previousNodeId = target.nodeId;
            String nextNodeId = target.rebuildNodeId();
            if (!Objects.equals(previousNodeId, nextNodeId)) {
                target.rememberNodeId(previousNodeId);
                target.nodeId = nextNodeId;
                changed = true;
                identityChanged = true;
            }
            if (target.rememberNodeId(baseNodeId)) {
                changed = true;
                identityChanged = true;
            }
            if (identityChanged) {
                invalidateTargetIndex();
            }
            if (changed) {
                setDirty();
            }
            return target.rewriteReference(reference);
        }
        return reference;
    }

    // Get the indexed targets
    private List<IndexedTarget> indexedTargets(String nodeId) {
        if (targetIndexDirty) {
            rebuildTargetIndex();
        }
        return targetsByNodeId.getOrDefault(nodeId, List.of());
    }

    // Rebuild the target index
    private void rebuildTargetIndex() {
        targetsByNodeId.clear();
        for (LinkerRecord linker : trackedLinkers.values()) {
            for (TargetRecord target : linker.targets) {
                Set<String> identities = new LinkedHashSet<>(target.nodeAliases);
                identities.add(target.nodeId);
                IndexedTarget indexed = new IndexedTarget(linker.dimensionId, target);
                for (String identity : identities) {
                    if (identity == null || identity.isBlank()) {
                        continue;
                    }
                    targetsByNodeId.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(indexed);
                }
            }
        }
        targetIndexDirty = false;
    }

    // Invalidate the target index
    private void invalidateTargetIndex() {
        targetIndexDirty = true;
    }

    // Save the contraption network linker tracker
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkersTag = new ListTag();
        for (Map.Entry<UUID, LinkerRecord> entry : trackedLinkers.entrySet()) {
            CompoundTag linkerTag = entry.getValue().save();
            linkerTag.putUUID(LINKER_ID_KEY, entry.getKey());
            linkersTag.add(linkerTag);
        }
        tag.put(LINKERS_KEY, linkersTag);
        return tag;
    }

    // Detach the controller
    private boolean detachController(ControllerLocator locator, @Nullable UUID keepLinkerId) {
        boolean changed = false;
        for (Map.Entry<UUID, LinkerRecord> entry : trackedLinkers.entrySet()) {
            if (keepLinkerId != null && keepLinkerId.equals(entry.getKey())) {
                continue;
            }
            ControllerLocator controller = entry.getValue().controller;
            if (controller != null && controller.sameResolvedLocation(locator)) {
                entry.getValue().controller = null;
                changed = true;
            }
        }
        return changed;
    }

    // Remove the linker record
    private boolean removeLinkerRecord(UUID linkerId, MinecraftServer server) {
        LinkerRecord removed = trackedLinkers.remove(linkerId);
        if (removed == null) {
            return false;
        }
        invalidateTargetIndex();
        removed.removeTrackingPoints(resolveLevel(server, removed.dimensionId));
        return true;
    }

    // Sync the tracked targets
    private boolean syncTrackedTargets(LinkerRecord record, Level level,
                                       List<ContraptionNetworkLinkerData.LinkedTarget> itemTargets) {
        Set<TargetRecord> matchedRecords = Collections.newSetFromMap(new IdentityHashMap<>());

        List<TargetRecord> nextTargets = new ArrayList<>();
        boolean changed = record.targets.size() != itemTargets.size();
        boolean identityChanged = changed;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        for (ContraptionNetworkLinkerData.LinkedTarget linkedTarget : itemTargets) {
            String nodeId = ContraptionNetworkLinkerData.nodeIdForTarget(linkedTarget);
            TargetRecord next = null;
            for (TargetRecord existing : record.targets) {
                if (!matchedRecords.contains(existing) && existing.matchesNodeId(nodeId)) {
                    next = existing;
                    matchedRecords.add(existing);
                    break;
                }
            }
            if (next == null) {
                next = TargetRecord.capture(level, linkedTarget);
                changed = true;
                identityChanged = true;
            } else {
                String previousNodeId = next.nodeId;
                Set<String> previousAliases = Set.copyOf(next.nodeAliases);
                if (serverLevel != null && next.resolve(serverLevel)) {
                    changed = true;
                }
                boolean preserveTrackedLocation = shouldPreserveTrackedLocation(
                        next.trackingPointId != null,
                        next.currentSubLevelId,
                        next.currentLocalPos,
                        linkedTarget);
                if (next.refreshFromLinkedTarget(level, linkedTarget, preserveTrackedLocation)) {
                    changed = true;
                }
                if (!Objects.equals(previousNodeId, next.nodeId)
                        || !Objects.equals(previousAliases, next.nodeAliases)) {
                    identityChanged = true;
                }
            }
            if (serverLevel != null && next.ensureTrackingPoint(serverLevel)) {
                changed = true;
            }
            nextTargets.add(next);
        }

        if (matchedRecords.size() != record.targets.size()) {
            if (serverLevel != null) {
                for (TargetRecord removed : record.targets) {
                    if (!matchedRecords.contains(removed)) {
                        removed.removeTrackingPoint(serverLevel);
                    }
                }
            }
            changed = true;
        }
        if (changed) {
            record.targets = nextTargets;
        }
        if (identityChanged) {
            invalidateTargetIndex();
        }
        return changed;
    }

    // Check if this should preserve tracked location
    static boolean shouldPreserveTrackedLocation(boolean hasTrackingPoint,
                                                  @Nullable UUID currentSubLevelId,
                                                  @Nullable BlockPos currentLocalPos,
                                                  ContraptionNetworkLinkerData.LinkedTarget incomingTarget) {
        return hasTrackingPoint && incomingTarget != null
                && (!Objects.equals(currentSubLevelId, incomingTarget.subLevelId())
                || !Objects.equals(currentLocalPos, incomingTarget.blockPos()));
    }

    // Check if this matches broken target
    static boolean matchesBrokenTarget(ContraptionNetworkLinkerData.LinkedTarget target,
                                       BlockPos brokenPos,
                                       @Nullable UUID brokenSubLevelId,
                                       String brokenBlockId) {
        return matchesBrokenTarget(target, brokenPos, brokenSubLevelId);
    }

    // Check if this matches broken target
    static boolean matchesBrokenTarget(ContraptionNetworkLinkerData.LinkedTarget target,
                                       BlockPos brokenPos,
                                       @Nullable UUID brokenSubLevelId) {
        return target != null && brokenPos != null
                && Objects.equals(target.blockPos(), brokenPos)
                && Objects.equals(target.subLevelId(), brokenSubLevelId);
    }

    // Get the attached block position
    static BlockPos attachedBlockPosition(BlockPos planePos, Direction face) {
        return planePos.relative(face.getOpposite());
    }

    // Reconcile the tracked linker
    private ReconcileResult reconcileTrackedLinker(LinkerRecord record, ServerLevel level, ItemStack linker,
                                                    List<ContraptionNetworkLinkerData.LinkedTarget> currentTargets,
                                                    ContraptionNetworkLinkerData.LinkMode editMode,
                                                    ContraptionNetworkLinkerData.TargetMode targetMode) {
        // ------------------------------------RECONCILIATION SETUP------------------------------------
        List<ContraptionNetworkLinkerData.LinkedTarget> nextTargets = new ArrayList<>();
        List<TargetRecord> nextRecords = new ArrayList<>();
        Map<String, String> rewrittenNodeIds = new LinkedHashMap<>();
        Map<String, Integer> faceQuarterTurns = new LinkedHashMap<>();
        Set<String> removedNodeIds = new LinkedHashSet<>();
        boolean trackerChanged = false;
        boolean identityChanged = false;

        // ------------------------------------TRACKED TARGETS------------------------------------
        for (TargetRecord recordTarget : record.targets) {
            String previousNodeId = recordTarget.nodeId;
            if (recordTarget.resolve(level)) {
                trackerChanged = true;
            }
            if (recordTarget.pruneMissingFaceSupports(level)) {
                trackerChanged = true;
            }
            recordTarget.restoreFacePlane(level);
            if (recordTarget.isDestroyed(level)) {
                removedNodeIds.add(previousNodeId);
                recordTarget.removeTrackingPoint(level);
                trackerChanged = true;
                identityChanged = true;
                continue;
            }

            String nextNodeId = recordTarget.rebuildNodeId();
            if (!Objects.equals(previousNodeId, nextNodeId)) {
                recordTarget.rememberNodeId(previousNodeId);
                rewrittenNodeIds.put(previousNodeId, nextNodeId);
                trackerChanged = true;
                identityChanged = true;
            }
            if (recordTarget.pendingFaceQuarterTurns != 0) {
                rewrittenNodeIds.put(previousNodeId, nextNodeId);
                faceQuarterTurns.put(previousNodeId, recordTarget.pendingFaceQuarterTurns);
            }
            recordTarget.nodeId = nextNodeId;
            nextTargets.add(recordTarget.toLinkedTarget());
            nextRecords.add(recordTarget);
        }

        record.targets = nextRecords;
        if (identityChanged) {
            invalidateTargetIndex();
        }

        // ------------------------------------GRAPH REFERENCES------------------------------------
        Map<String, TargetRecord> recordsByNodeId = new LinkedHashMap<>();
        Map<String, ControllerDiscoveryNode> graphReplacements = new LinkedHashMap<>();
        for (TargetRecord target : nextRecords) {
            recordsByNodeId.put(target.nodeId, target);
        }
        if (!rewrittenNodeIds.isEmpty() || !removedNodeIds.isEmpty()) {
            for (Map.Entry<String, String> entry : rewrittenNodeIds.entrySet()) {
                TargetRecord target = recordsByNodeId.get(entry.getValue());
                if (target != null
                        && target.mode != ContraptionNetworkLinkerData.LinkMode.SCM) {
                    graphReplacements.put(entry.getKey(), target.toDiscoveryNode());
                }
            }
        }

        // ------------------------------------LINKER REWRITE------------------------------------
        boolean rewriteRequired = !nextTargets.equals(currentTargets)
                || !graphReplacements.isEmpty()
                || !removedNodeIds.isEmpty();
        boolean itemChanged = rewriteRequired && ContraptionNetworkLinkerData.rewriteTrackedTargets(
                linker,
                nextTargets,
                editMode,
                targetMode,
                graphReplacements,
                removedNodeIds,
                faceQuarterTurns);

        // -----------------------------------------------------FACE ROTATION-----------------------------------------------------
        if (!faceQuarterTurns.isEmpty()) {
            for (TargetRecord target : nextRecords) {
                target.pendingFaceQuarterTurns = 0;
            }
            trackerChanged = true;
        }

        return new ReconcileResult(trackerChanged, itemChanged);
    }

    // Resolve the level
    private static @Nullable ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        try {
            ResourceLocation id = ResourceLocation.parse(dimensionId);
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        } catch (Exception ignored) {
            return null;
        }
    }

    // Resolve the controller
    private static AnalogueContraptionControllerBlockEntity resolveController(ServerLevel level, ControllerLocator locator) {
        if (locator.currentLocalPos == null) {
            return null;
        }
        return SimulatedHelper.findBlockEntity(level, locator.currentSubLevelId, locator.currentLocalPos,
                AnalogueContraptionControllerBlockEntity.class);
    }

    // Check if this is a definitely missing controller
    private static boolean isDefinitelyMissingCtrl(ServerLevel level, ControllerLocator locator) {
        if (locator.currentLocalPos == null) {
            return false;
        }
        if (locator.currentSubLevelId != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, locator.currentSubLevelId);
            return subLevel != null
                    && SimulatedHelper.findBlockEntity(level, locator.currentSubLevelId, locator.currentLocalPos,
                    AnalogueContraptionControllerBlockEntity.class) == null;
        }
        BlockPos worldPos = BlockPos.containing(locator.anchorX, locator.anchorY, locator.anchorZ);
        return level.hasChunkAt(worldPos)
                && SimulatedHelper.findBlockEntity(level, null, locator.currentLocalPos,
                AnalogueContraptionControllerBlockEntity.class) == null;
    }

    // Resolve the location
    private static ResolvedLocation resolveLocation(ServerLevel level,
                                                    double anchorX,
                                                    double anchorY,
                                                    double anchorZ,
                                                    @Nullable UUID currentSubLevelId,
                                                    @Nullable BlockPos currentLocalPos) {
        Vec3 anchor = new Vec3(anchorX, anchorY, anchorZ);
        if (currentSubLevelId != null && currentLocalPos != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, currentSubLevelId);
            if (subLevel == null) {
                return new ResolvedLocation(
                        anchor.x, anchor.y, anchor.z, currentSubLevelId, currentLocalPos);
            }
            Vec3 transformed = SimulatedHelper.toContainingWorldPosition(
                    subLevel, Vec3.atCenterOf(currentLocalPos));
            if (transformed != null) {
                anchor = transformed;
            }
        }

        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(level, anchor);
        if (containingSubLevel != null) {
            UUID resolvedSubLevelId = SimulatedHelper.getSubLevelId(containingSubLevel);
            Vec3 local = SimulatedHelper.toContainingLocalPosition(containingSubLevel, anchor);
            BlockPos localPos = local == null ? BlockPos.containing(anchor) : BlockPos.containing(local);
            return new ResolvedLocation(anchor.x, anchor.y, anchor.z, resolvedSubLevelId, localPos);
        }

        return new ResolvedLocation(anchor.x, anchor.y, anchor.z, null, BlockPos.containing(anchor));
    }

    // Handle the linker record
    private static final class LinkerRecord {
        // Current dimension id
        private String dimensionId = Level.OVERWORLD.location().toString();
        // Current linker record controller
        private @Nullable ControllerLocator controller;
        // Tracked targets
        private List<TargetRecord> targets = new ArrayList<>();

        // Collect the tracking point ids
        private void collectTrackingPointIds(Set<UUID> ids) {
            for (TargetRecord target : targets) {
                if (target.trackingPointId != null) {
                    ids.add(target.trackingPointId);
                }
            }
        }

        // Remove the tracking points
        private void removeTrackingPoints(@Nullable ServerLevel level) {
            if (level == null) {
                return;
            }
            for (TargetRecord target : targets) {
                target.removeTrackingPoint(level);
            }
        }

        // Save the linker record
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(DIMENSION_KEY, dimensionId);
            if (controller != null) {
                tag.put(CONTROLLER_KEY, controller.save());
            }
            ListTag targetsTag = new ListTag();
            for (TargetRecord target : targets) {
                targetsTag.add(target.save());
            }
            tag.put(TARGETS_KEY, targetsTag);
            return tag;
        }

        // Load the linker record
        private static @Nullable LinkerRecord load(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return null;
            }
            LinkerRecord record = new LinkerRecord();
            if (tag.contains(CONTROLLER_KEY, Tag.TAG_COMPOUND)) {
                record.controller = ControllerLocator.load(tag.getCompound(CONTROLLER_KEY));
            }
            record.dimensionId = tag.contains(DIMENSION_KEY, Tag.TAG_STRING)
                    ? tag.getString(DIMENSION_KEY)
                    : record.controller != null
                    ? record.controller.dimensionId
                    : Level.OVERWORLD.location().toString();
            ListTag targetsTag = tag.getList(TARGETS_KEY, Tag.TAG_COMPOUND);
            for (int idx = 0; idx < targetsTag.size(); idx++) {
                TargetRecord target = TargetRecord.load(targetsTag.getCompound(idx));
                if (target != null) {
                    record.targets.add(target);
                }
            }
            return record;
        }
    }

    // Handle the controller locator
    private static final class ControllerLocator {
        // Current dimension id
        private String dimensionId = Level.OVERWORLD.location().toString();
        // Current anchor x
        private double anchorX;
        // Current anchor y
        private double anchorY;
        // Current anchor z
        private double anchorZ;
        // Current sub-level id
        private @Nullable UUID currentSubLevelId;
        // Current local pos
        private @Nullable BlockPos currentLocalPos = BlockPos.ZERO;
        // Missing since tick
        private long missingSinceTick = Long.MIN_VALUE;

        // Capture the controller locator
        private static ControllerLocator capture(AnalogueContraptionControllerBlockEntity controller) {
            ControllerLocator locator = new ControllerLocator();
            Level level = controller.getLevel();
            locator.dimensionId = level == null ? Level.OVERWORLD.location().toString() : level.dimension().location().toString();
            locator.currentSubLevelId = SimulatedHelper.getContainingSubLevelId(controller);
            locator.currentLocalPos = controller.getBlockPos().immutable();
            Vec3 anchor = locator.currentSubLevelId == null
                    ? Vec3.atCenterOf(controller.getBlockPos())
                    : SimulatedHelper.toContainingWorldPosition(controller, Vec3.atCenterOf(controller.getBlockPos()));
            if (anchor == null) {
                anchor = Vec3.atCenterOf(controller.getBlockPos());
            }
            locator.anchorX = anchor.x;
            locator.anchorY = anchor.y;
            locator.anchorZ = anchor.z;
            return locator;
        }

        // Apply the controller locator
        private boolean apply(ResolvedLocation location) {
            boolean changed = false;
            if (Double.compare(anchorX, location.anchorX) != 0) {
                anchorX = location.anchorX;
                changed = true;
            }
            if (Double.compare(anchorY, location.anchorY) != 0) {
                anchorY = location.anchorY;
                changed = true;
            }
            if (Double.compare(anchorZ, location.anchorZ) != 0) {
                anchorZ = location.anchorZ;
                changed = true;
            }
            if (!Objects.equals(currentSubLevelId, location.currentSubLevelId)) {
                currentSubLevelId = location.currentSubLevelId;
                changed = true;
            }
            if (!Objects.equals(currentLocalPos, location.currentLocalPos)) {
                currentLocalPos = location.currentLocalPos == null ? null : location.currentLocalPos.immutable();
                changed = true;
            }
            return changed;
        }

        // Check if this uses the same resolved location
        private boolean sameResolvedLocation(ControllerLocator other) {
            return other != null
                    && Objects.equals(dimensionId, other.dimensionId)
                    && Objects.equals(currentSubLevelId, other.currentSubLevelId)
                    && Objects.equals(currentLocalPos, other.currentLocalPos);
        }

        // Check if the target moves with the assembly bounds
        private boolean movesWithAssemblyBounds(BoundingBox3ic assemblyBounds) {
            return currentLocalPos != null && assemblyBounds != null
                    && assemblyBounds.contains(trackingPointPosition(currentLocalPos));
        }

        // Remap the assembly location
        private boolean remapAssemblyLocation(
                ServerLevel resultingLevel,
                @Nullable ServerSubLevel destinationSubLevel,
                SubLevelAssemblyHelper.AssemblyTransform transform
        ) {
            if (resultingLevel == null || currentLocalPos == null || transform == null) {
                return false;
            }
            BlockPos previousPos = currentLocalPos;
            UUID previousSubLevelId = currentSubLevelId;
            String previousDimension = dimensionId;
            currentLocalPos = transform.apply(previousPos).immutable();
            currentSubLevelId = destinationSubLevel == null
                    ? null : destinationSubLevel.getUniqueId();
            dimensionId = resultingLevel.dimension().location().toString();
            Vec3 anchor = resolveAnchor(
                    resultingLevel, currentSubLevelId, currentLocalPos);
            anchorX = anchor.x;
            anchorY = anchor.y;
            anchorZ = anchor.z;
            markAvailable();
            return !Objects.equals(previousPos, currentLocalPos)
                    || !Objects.equals(previousSubLevelId, currentSubLevelId)
                    || !Objects.equals(previousDimension, dimensionId);
        }

        // Check if the tracked target is confirmed missing
        private boolean confirmedMissing(ServerLevel level) {
            if (!isDefinitelyMissingCtrl(level, this)) {
                markAvailable();
                return false;
            }
            long now = level.getGameTime();
            if (missingSinceTick == Long.MIN_VALUE || now < missingSinceTick) {
                missingSinceTick = now;
                return false;
            }
            return now - missingSinceTick >= RELOCATION_GRACE_TICKS;
        }

        // Mark the available
        private void markAvailable() {
            missingSinceTick = Long.MIN_VALUE;
        }

        // Save the controller locator
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(DIMENSION_KEY, dimensionId);
            tag.putDouble(ANCHOR_X_KEY, anchorX);
            tag.putDouble(ANCHOR_Y_KEY, anchorY);
            tag.putDouble(ANCHOR_Z_KEY, anchorZ);
            if (currentSubLevelId != null) {
                tag.putUUID(CURRENT_SUBLEVEL_KEY, currentSubLevelId);
            }
            if (currentLocalPos != null) {
                tag.putLong(CURRENT_LOCAL_POS_KEY, currentLocalPos.asLong());
            }
            return tag;
        }

        // Load the controller locator
        private static ControllerLocator load(CompoundTag tag) {
            ControllerLocator locator = new ControllerLocator();
            locator.dimensionId = tag.contains(DIMENSION_KEY, Tag.TAG_STRING)
                    ? tag.getString(DIMENSION_KEY)
                    : Level.OVERWORLD.location().toString();
            locator.anchorX = tag.getDouble(ANCHOR_X_KEY);
            locator.anchorY = tag.getDouble(ANCHOR_Y_KEY);
            locator.anchorZ = tag.getDouble(ANCHOR_Z_KEY);
            locator.currentSubLevelId = tag.hasUUID(CURRENT_SUBLEVEL_KEY) ? tag.getUUID(CURRENT_SUBLEVEL_KEY) : null;
            locator.currentLocalPos = tag.contains(CURRENT_LOCAL_POS_KEY)
                    ? BlockPos.of(tag.getLong(CURRENT_LOCAL_POS_KEY))
                    : null;
            return locator;
        }
    }

    // Handle the target record
    private static final class TargetRecord {
        // Current node id
        private String nodeId = "";
        // Tracked node aliases
        private final Set<String> nodeAliases = new LinkedHashSet<>();
        // Current tracking point id
        private @Nullable UUID trackingPointId;
        // Current plane orientation
        private Direction planeOrientation = Direction.NORTH;
        // Tracks whether plane orientation is known
        private boolean planeOrientationKnown;
        // Pending face quarter turns
        private int pendingFaceQuarterTurns;
        // Current block id
        private String blockId = "minecraft:air";
        // Current display label
        private String label = "Target";
        // Current target record mode
        private ContraptionNetworkLinkerData.LinkMode mode = ContraptionNetworkLinkerData.LinkMode.OUTPUT;
        // Current scope
        private ContraptionNetworkLinkerData.TargetScope scope = ContraptionNetworkLinkerData.TargetScope.FACE;
        // Tracked faces
        private List<ContraptionNetworkLinkerData.LinkedFace> faces = List.of();
        // Current anchor x
        private double anchorX;
        // Current anchor y
        private double anchorY;
        // Current anchor z
        private double anchorZ;
        // Current sub-level id
        private @Nullable UUID currentSubLevelId;
        // Current local pos
        private @Nullable BlockPos currentLocalPos;
        // Missing since tick
        private long missingSinceTick = Long.MIN_VALUE;
        // Missing face since tick count
        private final Map<Direction, Long> missingFaceSinceTicks =
                new java.util.EnumMap<>(Direction.class);

        // Capture the target record
        private static TargetRecord capture(Level level, ContraptionNetworkLinkerData.LinkedTarget target) {
            TargetRecord record = new TargetRecord();
            record.refreshFromLinkedTarget(level, target, false);
            return record;
        }

        // Refresh the linked target
        private boolean refreshFromLinkedTarget(Level level, ContraptionNetworkLinkerData.LinkedTarget target,
                                                boolean preserveTrackedLocation) {
            boolean changed = false;
            if (target.scope().usesFaces() && pendingFaceQuarterTurns != 0
                    && !Objects.equals(target.faces(), faces)) {
                target = rotateTargetFaces(target, pendingFaceQuarterTurns);
            }
            String nextNodeId = ContraptionNetworkLinkerData.nodeIdForTarget(target);
            boolean preserveTrackedIdentity = preserveTrackedLocation || pendingFaceQuarterTurns != 0;
            if (!preserveTrackedIdentity && !Objects.equals(nodeId, nextNodeId)) {
                rememberNodeId(nodeId);
                nodeId = nextNodeId;
                changed = true;
            }
            if (!Objects.equals(blockId, target.blockId())) {
                blockId = target.blockId();
                changed = true;
            }
            if (!Objects.equals(label, target.label())) {
                label = target.label();
                changed = true;
            }
            if (mode != target.mode()) {
                mode = target.mode();
                changed = true;
            }
            if (scope != target.scope()) {
                scope = target.scope();
                changed = true;
            }
            List<ContraptionNetworkLinkerData.LinkedFace> nextFaces = List.copyOf(target.faces());
            if (!Objects.equals(faces, nextFaces)) {
                faces = nextFaces;
                changed = true;
            }
            if (!preserveTrackedLocation) {
                if (!Objects.equals(currentSubLevelId, target.subLevelId())) {
                    currentSubLevelId = target.subLevelId();
                    changed = true;
                }
                BlockPos targetPos = target.blockPos() == null ? null : target.blockPos().immutable();
                if (!Objects.equals(currentLocalPos, targetPos)) {
                    currentLocalPos = targetPos;
                    changed = true;
                }
            }

            Vec3 anchor = resolveAnchor(level, currentSubLevelId, currentLocalPos);
            if (Double.compare(anchorX, anchor.x) != 0) {
                anchorX = anchor.x;
                changed = true;
            }
            if (Double.compare(anchorY, anchor.y) != 0) {
                anchorY = anchor.y;
                changed = true;
            }
            if (Double.compare(anchorZ, anchor.z) != 0) {
                anchorZ = anchor.z;
                changed = true;
            }
            ServerLevel serverLevel = SableLevelApi.serverLevel(level);
            if (!planeOrientationKnown && serverLevel != null) {
                Direction orientation = currentPlaneOrientation(serverLevel);
                if (orientation != null) {
                    planeOrientation = orientation;
                    planeOrientationKnown = true;
                    changed = true;
                }
            }
            return changed;
        }

        // Ensure the tracking point
        private boolean ensureTrackingPoint(ServerLevel level) {
            if (level == null || currentLocalPos == null) {
                return false;
            }
            if (trackingPointId == null) {
                trackingPointId = UUID.randomUUID();
                writeTrackingPoint(level);
                return true;
            }
            SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
            TrackingPoint trackingPoint = data.getTrackingPoint(trackingPointId);
            if (trackingPoint == null) {
                writeTrackingPoint(level);
                return true;
            }
            boolean sameLocation = trackingPoint.inSubLevel() == (currentSubLevelId != null)
                    && Objects.equals(trackingPoint.subLevelID(), currentSubLevelId)
                    && currentLocalPos.equals(BlockPos.containing(
                    trackingPoint.point().x(), trackingPoint.point().y(), trackingPoint.point().z()));
            if (sameLocation && !isBlockTrackingPoint(trackingPoint.point(), currentLocalPos)) {
                writeTrackingPoint(level);
                return true;
            }
            return false;
        }

        // Remap the assembly target
        private boolean remapAssemblyTarget(ServerLevel level,
                                            BoundingBox3ic assemblyBounds,
                                            @Nullable ServerSubLevel destinationSubLevel,
                                            SubLevelAssemblyHelper.AssemblyTransform transform) {
            if (currentLocalPos == null || !targetMovesWithAssemblyBounds(
                    scope, currentLocalPos, faces, assemblyBounds)) {
                return false;
            }
            BlockPos previousPos = currentLocalPos;
            UUID previousSubLevelId = currentSubLevelId;
            BlockPos nextPos = transform.apply(previousPos);
            UUID nextSubLevelId = resolveAssemblyDestinationSubLevelId(
                    level, destinationSubLevel, scope, previousPos, faces, assemblyBounds, transform);
            currentLocalPos = nextPos.immutable();
            currentSubLevelId = nextSubLevelId;
            Vec3 anchor = resolveAnchor(level, currentSubLevelId, currentLocalPos);
            anchorX = anchor.x;
            anchorY = anchor.y;
            anchorZ = anchor.z;
            writeTrackingPoint(level);
            return !Objects.equals(previousPos, nextPos)
                    || !Objects.equals(previousSubLevelId, nextSubLevelId);
        }

        // Check if the target moves with the assembly bounds
        private boolean movesWithAssemblyBounds(BoundingBox3ic assemblyBounds) {
            return currentLocalPos != null && targetMovesWithAssemblyBounds(
                    scope, currentLocalPos, faces, assemblyBounds);
        }

        // Remove the tracking point
        private void removeTrackingPoint(ServerLevel level) {
            if (level == null || trackingPointId == null) {
                return;
            }
            SubLevelTrackingPointSavedData.getOrLoad(level).removeTrackingPoint(trackingPointId);
        }

        // Write the tracking point
        private void writeTrackingPoint(ServerLevel level) {
            if (level == null || trackingPointId == null || currentLocalPos == null) {
                return;
            }
            SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
            Object subLevel = currentSubLevelId == null ? null
                    : SubLevelBlockEntityCollector.getSubLevel(level, currentSubLevelId);
            GlobalSavedSubLevelPointer pointer = subLevel instanceof ServerSubLevel serverSubLevel
                    ? serverSubLevel.getLastSerializationPointer()
                    : null;
            Vector3d point = trackingPointPosition(currentLocalPos);
            Vector3d placeholder = currentSubLevelId == null ? null : new Vector3d(anchorX, anchorY, anchorZ);
            data.setTrackingPoint(trackingPointId,
                    new TrackingPoint(currentSubLevelId != null, currentSubLevelId, pointer, point, placeholder));
        }

        // Resolve the target record
        private boolean resolve(ServerLevel level) {
            // ------------------------------------TRACKING POINT------------------------------------
            if (level != null && trackingPointId != null) {
                SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(level);
                TrackingPoint trackingPoint = data.getTrackingPoint(trackingPointId);
                if (trackingPoint != null) {
                    boolean changed = false;
                    // ------------------------------------SUB-LEVEL POSITION------------------------------------
                    if (trackingPoint.inSubLevel()) {
                        UUID nextSubLevel = trackingPoint.subLevelID();
                        BlockPos nextLocalPos = BlockPos.containing(trackingPoint.point().x(), trackingPoint.point().y(), trackingPoint.point().z());
                        if (!Objects.equals(currentSubLevelId, nextSubLevel)) {
                            currentSubLevelId = nextSubLevel;
                            changed = true;
                        }
                        if (!Objects.equals(currentLocalPos, nextLocalPos)) {
                            currentLocalPos = nextLocalPos;
                            changed = true;
                        }

                        Object subLevel = nextSubLevel == null ? null : SubLevelBlockEntityCollector.getSubLevel(level, nextSubLevel);
                        Vec3 worldPoint = subLevel == null
                                ? null
                                : SimulatedHelper.toContainingWorldPosition(subLevel,
                                Vec3.atCenterOf(nextLocalPos));
                        Vector3d placeholder = trackingPoint.globalPlaceholderPosition();
                        double nextAnchorX = worldPoint != null ? worldPoint.x : (placeholder != null ? placeholder.x() : anchorX);
                        double nextAnchorY = worldPoint != null ? worldPoint.y : (placeholder != null ? placeholder.y() : anchorY);
                        double nextAnchorZ = worldPoint != null ? worldPoint.z : (placeholder != null ? placeholder.z() : anchorZ);
                        if (Double.compare(anchorX, nextAnchorX) != 0) {
                            anchorX = nextAnchorX;
                            changed = true;
                        }
                        if (Double.compare(anchorY, nextAnchorY) != 0) {
                            anchorY = nextAnchorY;
                            changed = true;
                        }
                        if (Double.compare(anchorZ, nextAnchorZ) != 0) {
                            anchorZ = nextAnchorZ;
                            changed = true;
                        }
                        return updatePlaneOrientation(level) || changed;
                    }

                    // ------------------------------------WORLD POSITION------------------------------------
                    BlockPos worldPos = BlockPos.containing(trackingPoint.point().x(), trackingPoint.point().y(), trackingPoint.point().z());
                    if (SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, worldPos)) {
                        Object containingSubLevel = SimulatedHelper.getContainingSubLevel(
                                level, Vec3.atCenterOf(worldPos));
                        UUID containingSubLevelId = SimulatedHelper.getSubLevelId(containingSubLevel);
                        if (containingSubLevelId != null) {
                            currentSubLevelId = containingSubLevelId;
                            currentLocalPos = worldPos;
                            Vec3 nestedAnchor = resolveAnchor(level, currentSubLevelId, currentLocalPos);
                            anchorX = nestedAnchor.x;
                            anchorY = nestedAnchor.y;
                            anchorZ = nestedAnchor.z;
                            writeTrackingPoint(level);
                            updatePlaneOrientation(level);
                            return true;
                        }
                    }
                    if (currentSubLevelId != null) {
                        currentSubLevelId = null;
                        changed = true;
                    }
                    if (!Objects.equals(currentLocalPos, worldPos)) {
                        currentLocalPos = worldPos;
                        changed = true;
                    }
                    Vec3 worldAnchor = trackingAnchorPosition(worldPos);
                    if (Double.compare(anchorX, worldAnchor.x) != 0) {
                        anchorX = worldAnchor.x;
                        changed = true;
                    }
                    if (Double.compare(anchorY, worldAnchor.y) != 0) {
                        anchorY = worldAnchor.y;
                        changed = true;
                    }
                    if (Double.compare(anchorZ, worldAnchor.z) != 0) {
                        anchorZ = worldAnchor.z;
                        changed = true;
                    }
                    return updatePlaneOrientation(level) || changed;
                }
                writeTrackingPoint(level);
            }

            // ------------------------------------FALLBACK LOCATION------------------------------------
            ResolvedLocation resolved = resolveLocation(level, anchorX, anchorY, anchorZ, currentSubLevelId, currentLocalPos);
            boolean changed = false;
            if (Double.compare(anchorX, resolved.anchorX) != 0) {
                anchorX = resolved.anchorX;
                changed = true;
            }
            if (Double.compare(anchorY, resolved.anchorY) != 0) {
                anchorY = resolved.anchorY;
                changed = true;
            }
            if (Double.compare(anchorZ, resolved.anchorZ) != 0) {
                anchorZ = resolved.anchorZ;
                changed = true;
            }
            if (!Objects.equals(currentSubLevelId, resolved.currentSubLevelId)) {
                currentSubLevelId = resolved.currentSubLevelId;
                changed = true;
            }
            if (!Objects.equals(currentLocalPos, resolved.currentLocalPos)) {
                currentLocalPos = resolved.currentLocalPos == null ? null : resolved.currentLocalPos.immutable();
                changed = true;
            }
            return updatePlaneOrientation(level) || changed;
        }

        // Update the plane orientation
        private boolean updatePlaneOrientation(ServerLevel level) {
            Direction currentOrientation = currentPlaneOrientation(level);
            if (currentOrientation == null) {
                return false;
            }
            if (!planeOrientationKnown) {
                planeOrientation = currentOrientation;
                planeOrientationKnown = true;
                return true;
            }
            int quarterTurns = ContraptionNetworkLinkerPlaneBlock.horizontalQuarterTurns(
                    planeOrientation, currentOrientation);
            if (quarterTurns == 0) {
                return false;
            }
            faces = rotateFaces(faces, quarterTurns);
            pendingFaceQuarterTurns = Math.floorMod(pendingFaceQuarterTurns + quarterTurns, 4);
            planeOrientation = currentOrientation;
            return true;
        }

        // Get the current plane orientation
        private @Nullable Direction currentPlaneOrientation(ServerLevel level) {
            if (level == null || currentLocalPos == null || !scope.usesFaces()) {
                return null;
            }
            BlockState state = null;
            if (currentSubLevelId == null) {
                if (level.hasChunkAt(currentLocalPos)) {
                    state = level.getBlockState(currentLocalPos);
                }
            } else {
                BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, currentSubLevelId, currentLocalPos);
                if (blockEntity != null) {
                    state = blockEntity.getBlockState();
                } else if (SubLevelBlockEntityCollector.getSubLevel(level, currentSubLevelId) instanceof Level subLevel
                        && subLevel.hasChunkAt(currentLocalPos)) {
                    state = subLevel.getBlockState(currentLocalPos);
                }
            }
            if (state == null || !(state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)
                    || !state.hasProperty(ContraptionNetworkLinkerPlaneBlock.ORIENTATION)) {
                return null;
            }
            return state.getValue(ContraptionNetworkLinkerPlaneBlock.ORIENTATION);
        }

        // Rotate the pending face
        private Direction rotatePendingFace(Direction face) {
            return ContraptionNetworkLinkerPlaneBlock.rotateDirection(face, pendingFaceQuarterTurns);
        }

        // Rotate the target faces
        private static ContraptionNetworkLinkerData.LinkedTarget rotateTargetFaces(
                ContraptionNetworkLinkerData.LinkedTarget target, int quarterTurns) {
            return new ContraptionNetworkLinkerData.LinkedTarget(
                    target.blockPos(), target.subLevelId(), target.blockId(), target.label(),
                    target.mode(), target.scope(), rotateFaces(target.faces(), quarterTurns));
        }

        // Rotate the faces
        private static List<ContraptionNetworkLinkerData.LinkedFace> rotateFaces(
                List<ContraptionNetworkLinkerData.LinkedFace> src, int quarterTurns) {
            if (src == null || src.isEmpty() || Math.floorMod(quarterTurns, 4) == 0) {
                return src == null ? List.of() : List.copyOf(src);
            }
            List<ContraptionNetworkLinkerData.LinkedFace> rotated = new ArrayList<>();
            for (ContraptionNetworkLinkerData.LinkedFace face : src) {
                Direction rotatedDirection = ContraptionNetworkLinkerPlaneBlock.rotateDirection(
                        face.face(), quarterTurns);
                String label = face.label();
                if (label != null && label.equalsIgnoreCase(defaultFaceLabel(face.face()))) {
                    label = defaultFaceLabel(rotatedDirection);
                }
                rotated.add(new ContraptionNetworkLinkerData.LinkedFace(
                        rotatedDirection, label, face.signalKey()));
            }
            return List.copyOf(rotated);
        }

        // Create the default face label
        private static String defaultFaceLabel(Direction dir) {
            String val = dir.getSerializedName();
            return Character.toUpperCase(val.charAt(0)) + val.substring(1);
        }

        // Check if this is destroyed
        private boolean isDestroyed(ServerLevel level) {
            if (currentLocalPos == null) {
                return true;
            }
            if (ContraptionNetworkLinkerData.isContraptionDiagramTarget(blockId)) {
                return confirmedMissing(level,
                        !ContraptionDiagramControllerCompat.targetExists(level, toDiscoveryNode()));
            }
            Level targetLevel = targetLevel(level);
            if (targetLevel == null) {
                return false;
            }
            if (scope.usesFaces()) {
                return confirmedMissing(level,
                        faceSupportStatus(targetLevel) == FaceSupportStatus.MISSING);
            }
            if (!targetLevel.hasChunkAt(currentLocalPos)) {
                return false;
            }
            BlockState state = targetLevel.getBlockState(currentLocalPos);
            if (state.isAir()) {
                return confirmedMissing(level, true);
            }
            String currentBlockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(state.getBlock()).toString();
            return confirmedMissing(level, !Objects.equals(blockId, currentBlockId));
        }

        // Check if the tracked target is confirmed missing
        private boolean confirmedMissing(ServerLevel level, boolean missing) {
            if (!missing) {
                missingSinceTick = Long.MIN_VALUE;
                return false;
            }
            long now = level.getGameTime();
            if (missingSinceTick == Long.MIN_VALUE || now < missingSinceTick) {
                missingSinceTick = now;
                return false;
            }
            return now - missingSinceTick >= RELOCATION_GRACE_TICKS;
        }

        // Prune missing face supports
        private boolean pruneMissingFaceSupports(ServerLevel level) {
            if (!scope.usesFaces() || currentLocalPos == null || faces.isEmpty()) {
                return false;
            }
            Level targetLevel = targetLevel(level);
            if (targetLevel == null) {
                return false;
            }
            List<ContraptionNetworkLinkerData.LinkedFace> survivingFaces = new ArrayList<>();
            long now = level.getGameTime();
            for (ContraptionNetworkLinkerData.LinkedFace face : faces) {
                BlockPos attachedPos = attachedBlockPosition(currentLocalPos, face.face());
                if (!targetLevel.hasChunkAt(attachedPos)
                        || !targetLevel.getBlockState(attachedPos).isAir()) {
                    missingFaceSinceTicks.remove(face.face());
                    survivingFaces.add(face);
                    continue;
                }
                long missingSince = missingFaceSinceTicks.getOrDefault(
                        face.face(), Long.MIN_VALUE);
                if (missingSince == Long.MIN_VALUE || now < missingSince) {
                    missingFaceSinceTicks.put(face.face(), now);
                    survivingFaces.add(face);
                } else if (now - missingSince < RELOCATION_GRACE_TICKS) {
                    survivingFaces.add(face);
                }
            }
            Set<Direction> retainedDirections = survivingFaces.stream()
                    .map(ContraptionNetworkLinkerData.LinkedFace::face)
                    .collect(java.util.stream.Collectors.toSet());
            missingFaceSinceTicks.keySet().removeIf(
                    dir -> !retainedDirections.contains(dir));
            if (survivingFaces.size() == faces.size()) {
                return false;
            }
            faces = List.copyOf(survivingFaces);
            return true;
        }

        // Restore the face plane
        private void restoreFacePlane(ServerLevel level) {
            if (!scope.usesFaces() || currentLocalPos == null) {
                return;
            }
            Level targetLevel = targetLevel(level);
            if (targetLevel == null || faceSupportStatus(targetLevel) != FaceSupportStatus.PRESENT
                    || !targetLevel.hasChunkAt(currentLocalPos)) {
                return;
            }
            BlockState state = targetLevel.getBlockState(currentLocalPos);
            if (state.isAir()) {
                if (!targetLevel.setBlock(currentLocalPos,
                        com.rieno.gadgetsandgizmos.registry.CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get()
                                .defaultBlockState(), 3)) {
                    return;
                }
                state = targetLevel.getBlockState(currentLocalPos);
            }
            if (!(state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)
                    || !(targetLevel.getBlockEntity(currentLocalPos)
                    instanceof ContraptionNetworkLinkerPlaneBlockEntity plane)) {
                return;
            }
            for (ContraptionNetworkLinkerData.LinkedFace face : faces) {
                BlockPos attachedPos = attachedBlockPosition(currentLocalPos, face.face());
                if (!targetLevel.hasChunkAt(attachedPos) || targetLevel.getBlockState(attachedPos).isAir()) {
                    continue;
                }
                if (!plane.hasPlane(face.face()) || plane.getMode(face.face()) != mode) {
                    plane.setPlane(face.face(), mode);
                }
            }
        }

        // Get the face support status
        private FaceSupportStatus faceSupportStatus(Level targetLevel) {
            if (currentLocalPos == null || faces.isEmpty()) {
                return FaceSupportStatus.MISSING;
            }
            boolean unavailable = false;
            for (ContraptionNetworkLinkerData.LinkedFace face : faces) {
                BlockPos attachedPos = attachedBlockPosition(currentLocalPos, face.face());
                if (!targetLevel.hasChunkAt(attachedPos)) {
                    unavailable = true;
                    continue;
                }
                if (!targetLevel.getBlockState(attachedPos).isAir()) {
                    return FaceSupportStatus.PRESENT;
                }
            }
            return unavailable ? FaceSupportStatus.UNAVAILABLE : FaceSupportStatus.MISSING;
        }

        // Get the target level
        private @Nullable Level targetLevel(ServerLevel level) {
            if (currentSubLevelId == null) {
                return level;
            }
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, currentSubLevelId);
            return subLevel instanceof Level resolved ? resolved : null;
        }

        // Define the face support status values
        private enum FaceSupportStatus {
            PRESENT,
            MISSING,
            UNAVAILABLE
        }

        // Rebuild the node id
        private String rebuildNodeId() {
            return ContraptionNetworkLinkerData.nodeIdForTarget(toLinkedTarget());
        }

        // Check if this matches node id
        private boolean matchesNodeId(String candidate) {
            return Objects.equals(nodeId, candidate) || nodeAliases.contains(candidate);
        }

        // Remember the node id
        private boolean rememberNodeId(String candidate) {
            return candidate != null && !candidate.isBlank() && !candidate.equals(nodeId) && nodeAliases.add(candidate);
        }

        // Rewrite the reference
        private ControllerDirectTargetReference rewriteReference(ControllerDirectTargetReference reference) {
            String nextTargetId = ContraptionNetworkLinkerData.replaceBaseNodeId(reference.targetId(), nodeId);
            String nextLabel = reference.label();
            Direction selectedFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(reference);
            if (selectedFace != null && pendingFaceQuarterTurns != 0) {
                ControllerDirectTargetReference rotated = ContraptionNetworkLinkerData.directTargetForFace(
                        toDiscoveryNode(), rotatePendingFace(selectedFace));
                if (rotated != null) {
                    nextTargetId = rotated.targetId();
                    nextLabel = rotated.label();
                }
            }
            String nextGroupId = currentSubLevelId == null ? "world" : "sublevel:" + currentSubLevelId;
            return new ControllerDirectTargetReference(
                    nextTargetId,
                    reference.targetTypeId(),
                    nextGroupId,
                    nextLabel,
                    reference.compatModeId(),
                    currentSubLevelId,
                    currentLocalPos);
        }

        // Convert the target record to linked target
        private ContraptionNetworkLinkerData.LinkedTarget toLinkedTarget() {
            return new ContraptionNetworkLinkerData.LinkedTarget(
                    currentLocalPos == null ? BlockPos.ZERO : currentLocalPos,
                    currentSubLevelId,
                    blockId,
                    label,
                    mode,
                    scope,
                    faces);
        }

        // Get the world center
        private Vec3 worldCenter() {
            return new Vec3(anchorX, anchorY, anchorZ);
        }

        // Convert the target record to discovery node
        private ControllerDiscoveryNode toDiscoveryNode() {
            String groupId = currentSubLevelId == null ? "world" : "sublevel:" + currentSubLevelId;
            return new ControllerDiscoveryNode(
                    nodeId,
                    mode.discoveryKind(),
                    groupId,
                    blockId,
                    label,
                    currentSubLevelId,
                    currentLocalPos == null ? BlockPos.ZERO : currentLocalPos);
        }

        // Save the target record
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(NODE_ID_KEY, nodeId);
            ListTag aliasesTag = new ListTag();
            for (String alias : nodeAliases) {
                aliasesTag.add(net.minecraft.nbt.StringTag.valueOf(alias));
            }
            tag.put(NODE_ALIASES_KEY, aliasesTag);
            if (trackingPointId != null) {
                tag.putUUID(TRACKING_POINT_ID_KEY, trackingPointId);
            }
            if (planeOrientationKnown) {
                tag.putString(PLANE_ORIENTATION_KEY, planeOrientation.getSerializedName());
            }
            if (pendingFaceQuarterTurns != 0) {
                tag.putInt(PENDING_FACE_QUARTER_TURNS_KEY, pendingFaceQuarterTurns);
            }
            tag.putString(BLOCK_ID_KEY, blockId);
            tag.putString(LABEL_KEY, label);
            tag.putString(MODE_KEY, mode.id());
            tag.putString(SCOPE_KEY, scope.id());
            tag.putDouble(ANCHOR_X_KEY, anchorX);
            tag.putDouble(ANCHOR_Y_KEY, anchorY);
            tag.putDouble(ANCHOR_Z_KEY, anchorZ);
            if (currentSubLevelId != null) {
                tag.putUUID(CURRENT_SUBLEVEL_KEY, currentSubLevelId);
            }
            if (currentLocalPos != null) {
                tag.putLong(CURRENT_LOCAL_POS_KEY, currentLocalPos.asLong());
            }
            ListTag facesTag = new ListTag();
            for (ContraptionNetworkLinkerData.LinkedFace face : faces) {
                CompoundTag faceTag = new CompoundTag();
                faceTag.putString(FACE_KEY, face.face().getSerializedName());
                if (face.label() != null && !face.label().isBlank()) {
                    faceTag.putString(FACE_LABEL_KEY, face.label());
                }
                if (face.signalKey() != null && !face.signalKey().isBlank()) {
                    faceTag.putString(FACE_SIGNAL_KEY, face.signalKey());
                }
                facesTag.add(faceTag);
            }
            tag.put(FACES_KEY, facesTag);
            return tag;
        }

        // Load the target record
        private static @Nullable TargetRecord load(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return null;
            }
            TargetRecord record = new TargetRecord();
            record.nodeId = tag.getString(NODE_ID_KEY);
            ListTag aliasesTag = tag.getList(NODE_ALIASES_KEY, Tag.TAG_STRING);
            for (int idx = 0; idx < aliasesTag.size(); idx++) {
                record.rememberNodeId(aliasesTag.getString(idx));
            }
            record.trackingPointId = tag.hasUUID(TRACKING_POINT_ID_KEY) ? tag.getUUID(TRACKING_POINT_ID_KEY) : null;
            Direction loadedOrientation = Direction.byName(tag.getString(PLANE_ORIENTATION_KEY));
            if (loadedOrientation != null && loadedOrientation.getAxis().isHorizontal()) {
                record.planeOrientation = loadedOrientation;
                record.planeOrientationKnown = true;
            }
            record.pendingFaceQuarterTurns = Math.floorMod(tag.getInt(PENDING_FACE_QUARTER_TURNS_KEY), 4);
            record.blockId = tag.contains(BLOCK_ID_KEY, Tag.TAG_STRING) ? tag.getString(BLOCK_ID_KEY) : "minecraft:air";
            record.label = tag.contains(LABEL_KEY, Tag.TAG_STRING) ? tag.getString(LABEL_KEY) : "Target";
            record.mode = ContraptionNetworkLinkerData.LinkMode.byId(tag.getString(MODE_KEY));
            record.scope = ContraptionNetworkLinkerData.TargetScope.byId(tag.getString(SCOPE_KEY));
            record.anchorX = tag.getDouble(ANCHOR_X_KEY);
            record.anchorY = tag.getDouble(ANCHOR_Y_KEY);
            record.anchorZ = tag.getDouble(ANCHOR_Z_KEY);
            record.currentSubLevelId = tag.hasUUID(CURRENT_SUBLEVEL_KEY) ? tag.getUUID(CURRENT_SUBLEVEL_KEY) : null;
            record.currentLocalPos = tag.contains(CURRENT_LOCAL_POS_KEY)
                    ? BlockPos.of(tag.getLong(CURRENT_LOCAL_POS_KEY))
                    : null;
            ListTag facesTag = tag.getList(FACES_KEY, Tag.TAG_COMPOUND);
            List<ContraptionNetworkLinkerData.LinkedFace> loadedFaces = new ArrayList<>();
            for (int idx = 0; idx < facesTag.size(); idx++) {
                CompoundTag faceTag = facesTag.getCompound(idx);
                net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(faceTag.getString(FACE_KEY));
                if (dir == null) {
                    continue;
                }
                loadedFaces.add(new ContraptionNetworkLinkerData.LinkedFace(
                        dir,
                        faceTag.getString(FACE_LABEL_KEY),
                        faceTag.getString(FACE_SIGNAL_KEY)));
            }
            record.faces = List.copyOf(loadedFaces);
            return record;
        }
    }

    // Handle the resolved location
    private static final class ResolvedLocation {
        // Anchor x
        private final double anchorX;
        // Anchor y
        private final double anchorY;
        // Anchor z
        private final double anchorZ;
        // Current sub-level id
        private final @Nullable UUID currentSubLevelId;
        // Current local pos
        private final @Nullable BlockPos currentLocalPos;

        // Initialize the resolved location
        private ResolvedLocation(double anchorX, double anchorY, double anchorZ,
                                 @Nullable UUID currentSubLevelId, @Nullable BlockPos currentLocalPos) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.currentSubLevelId = currentSubLevelId;
            this.currentLocalPos = currentLocalPos == null ? null : currentLocalPos.immutable();
        }
    }

    // Store reconcile results
    private record ReconcileResult(boolean trackerChanged, boolean itemChanged) {
        // Check if the linker tracking state changed
        private boolean changed() {
            return trackerChanged || itemChanged;
        }
    }

    // Store the indexed target
    private record IndexedTarget(String dimensionId, TargetRecord target) {
    }

    // Expose the link matcher
    @FunctionalInterface
    public interface LinkMatcher {
        // Check if this matches the value
        boolean matches(ContraptionNetworkLinkerData.LinkedTarget target, Vec3 worldCenter);
    }

    // Store the clear links summary
    public record ClearLinksSummary(int updatedLinkers, int removedTargets) {
    }

    // Resolve the anchor
    private static Vec3 resolveAnchor(Level level, @Nullable UUID currentSubLevelId, @Nullable BlockPos currentLocalPos) {
        if (level == null || currentLocalPos == null) {
            return Vec3.ZERO;
        }
        if (currentSubLevelId == null) {
            return Vec3.atCenterOf(currentLocalPos);
        }
        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, currentSubLevelId);
        if (subLevel == null) {
            return Vec3.atCenterOf(currentLocalPos);
        }
        Vec3 transformed = SimulatedHelper.toContainingWorldPosition(subLevel, Vec3.atCenterOf(currentLocalPos));
        return transformed == null ? Vec3.atCenterOf(currentLocalPos) : transformed;
    }

    // Get the tracking point position
    static Vector3d trackingPointPosition(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    // Get the tracking anchor position
    static Vec3 trackingAnchorPosition(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    // Check if this is block tracking point
    private static boolean isBlockTrackingPoint(Vector3d point, BlockPos pos) {
        return Double.compare(point.x(), pos.getX() + 0.5D) == 0
                && Double.compare(point.y(), pos.getY() + 0.5D) == 0
                && Double.compare(point.z(), pos.getZ() + 0.5D) == 0;
    }

    // Check if the target moves with the assembly bounds
    static boolean targetMovesWithAssemblyBounds(ContraptionNetworkLinkerData.TargetScope scope,
                                                  BlockPos targetPos,
                                                  List<ContraptionNetworkLinkerData.LinkedFace> faces,
                                                  BoundingBox3ic assemblyBounds) {
        if (scope == null || targetPos == null || assemblyBounds == null) {
            return false;
        }
        if (assemblyBounds.contains(trackingPointPosition(targetPos))) {
            return true;
        }
        if (!scope.usesFaces() || faces == null) {
            return false;
        }
        for (ContraptionNetworkLinkerData.LinkedFace face : faces) {
            if (face != null && assemblyBounds.contains(trackingPointPosition(
                    attachedBlockPosition(targetPos, face.face())))) {
                return true;
            }
        }
        return false;
    }

    // Resolve the assembly destination sublevel id
    private static @Nullable UUID resolveAssemblyDestinationSubLevelId(
            ServerLevel level,
            @Nullable ServerSubLevel destinationSubLevel,
            ContraptionNetworkLinkerData.TargetScope scope,
            BlockPos sourceTargetPos,
            List<ContraptionNetworkLinkerData.LinkedFace> faces,
            BoundingBox3ic assemblyBounds,
            SubLevelAssemblyHelper.AssemblyTransform transform) {
        if (destinationSubLevel != null) {
            return destinationSubLevel.getUniqueId();
        }
        ServerLevel resultingLevel = transform.getLevel();
        for (BlockPos probePos : assemblyDestinationProbePositions(
                scope, sourceTargetPos, faces, assemblyBounds, transform)) {
            UUID containingSubLevelId = SimulatedHelper.getSubLevelId(
                    SimulatedHelper.getContainingSubLevel(
                            resultingLevel, Vec3.atCenterOf(probePos)));
            if (containingSubLevelId != null) {
                return containingSubLevelId;
            }
        }
        return null;
    }

    // Get the assembly destination probe positions
    static List<BlockPos> assemblyDestinationProbePositions(
            ContraptionNetworkLinkerData.TargetScope scope,
            BlockPos sourceTargetPos,
            List<ContraptionNetworkLinkerData.LinkedFace> faces,
            BoundingBox3ic assemblyBounds,
            SubLevelAssemblyHelper.AssemblyTransform transform) {
        if (scope == null || sourceTargetPos == null || assemblyBounds == null || transform == null) {
            return List.of();
        }
        Set<BlockPos> probePositions = new LinkedHashSet<>();
        if (scope.usesFaces() && faces != null) {
            for (ContraptionNetworkLinkerData.LinkedFace face : faces) {
                if (face == null) {
                    continue;
                }
                BlockPos supportingPos = attachedBlockPosition(sourceTargetPos, face.face());
                if (assemblyBounds.contains(trackingPointPosition(supportingPos))) {
                    probePositions.add(transform.apply(supportingPos).immutable());
                }
            }
        }
        probePositions.add(transform.apply(sourceTargetPos).immutable());
        return List.copyOf(probePositions);
    }
}
