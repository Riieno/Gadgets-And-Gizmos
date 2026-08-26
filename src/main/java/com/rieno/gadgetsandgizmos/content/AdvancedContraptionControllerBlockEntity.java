package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphFunctions;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphPortState;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphProfilerMath;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphTemplates;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphVersionHistory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedControllerNamedEventBus;
import com.rieno.gadgetsandgizmos.content.advanced.NavigationTableGraphData;
import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import com.rieno.gadgetsandgizmos.compat.create.CreateFantasizingGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.CreateNixieTubeGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.CreateRotationSpeedControllerGraphCompat;
import com.rieno.gadgetsandgizmos.compat.create.NavigationTableGraphCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.ContraptionDiagramControllerCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.LinkedTypewriterGraphCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.controller.ExternalBlockEntityDirectControlCompat;
import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import com.rieno.gadgetsandgizmos.lib.probe.BlockStateDataAccess;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSource;
import com.rieno.gadgetsandgizmos.lib.control.LinkedOrientationSource;
import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.lib.control.OrientationTarget;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import com.rieno.gadgetsandgizmos.lib.control.CustomKeyEntry;
import com.rieno.gadgetsandgizmos.lib.control.ControllerBindingOwner;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.scm.ScmFlightBehavior;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import com.rieno.gadgetsandgizmos.neoforge.network.GraphSoundPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueChannelMode;
import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplaySourceRegistry;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipLogisticsRun;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Queue;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;

// Handle ACC graphs, manifests, displays and SCM control
public class AdvancedContraptionControllerBlockEntity extends AnalogueContraptionControllerBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int GRAPH_OBSERVER_SAMPLE_INTERVAL = 5;
    private static final int GRAPH_OBSERVER_EDGE_BUDGET = 32;
    private static final int GRAPH_RUNTIME_SEND_INTERVAL = 5;
    private static final int ACC_DISPLAY_UPDATE_INTERVAL = 2;
    private static final long CLIENT_PROFILER_SAMPLE_TIMEOUT_TICKS = 40L;
    private static final String GRAPH_VERSIONS_TAG = "AdvancedGraphVersions";
    private static final String GOGGLES_TRACKER_PAIRS_TAG = "GogglesTrackerPairs";
    private static final String SHIP_CONTROL_MAP_ID_TAG = "ShipControlMapId";
    private static final String SHIP_NAME_TAG = "ShipName";
    private static final String SHIP_FLIGHT_BEHAVIOR_TAG = "ShipFlightBehavior";
    private static final String SHIP_CONTROL_MODE_TAG = "ShipControlMode";
    private static final String SCHEMATIC_SHIP_CONTROL_MAP_TAG = "ShipControlMap";
    private static final String SHIP_INITIALIZATION_VISIBLE_TAG = "ShipInitializationVisible";
    private static final String SHIP_INITIALIZATION_PERCENT_TAG = "ShipInitializationPercent";
    private static final String SHIP_INITIALIZATION_STATUS_TAG = "ShipInitializationStatus";
    private static final String SHIP_INITIALIZATION_VIEWER_TAG = "ShipInitializationViewer";
    private static final String SERVER_SHUTDOWN_SNAPSHOT_TAG = "ServerShutdownSnapshot";
    public static final String GRAPH_RAW_DIRECT_SIGNAL_PORT = "__raw_direct_signal";
    private static final Set<String> MOUSE_INPUT_TYPES = Set.of(
            "left_click", "right_click", "middle_click", "scroll_up", "scroll_down", "mouse_x", "mouse_y");
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draft graph
    private AdvancedGraphDocument draftGraph = new AdvancedGraphDocument();
    // Active graph
    private AdvancedGraphDocument activeGraph = new AdvancedGraphDocument();
    // Graph runtime
    private final GraphRuntime graphRuntime = new GraphRuntime(this);
    // Ship stock network cache
    private final ShipStockNetworkCache shipStockNetworkCache = new ShipStockNetworkCache(this);
    // Tracked ship logistics runs
    private final List<ShipLogisticsRun> shipLogisticsRuns = new ArrayList<>();
    // Ship control runtime
    private final ShipControlModuleRuntime shipControlRuntime = new ShipControlModuleRuntime(this);
    // Shipping schedule runtime
    private final ShippingScheduleRuntime shippingScheduleRuntime = new ShippingScheduleRuntime(this);
    // Current ship name
    private String shipName = "Unnamed Ship";
    // Current ship flight behavior
    private ScmFlightBehavior shipFlightBehavior = ScmFlightBehavior.DEFAULT;
    // Current ship control mode
    private ScmControlMode shipControlMode = ScmControlModeRegistry.resolve("airship");
    // Graph versions
    private final AdvancedGraphVersionHistory graphVersions = new AdvancedGraphVersionHistory();
    // Tracked goggles tracker pairs
    private final Map<UUID, String> gogglesTrackerPairs = new LinkedHashMap<>();
    // Tracked goggles tracking snapshots
    private final Map<UUID, GogglesTrackingSnapshot> gogglesTrackingSnapshots = new LinkedHashMap<>();
    // Tracked graph diagnostics
    private List<AdvancedGraphValidator.Diagnostic> graphDiagnostics = List.of();
    // Last graph channel values
    private final Map<String, Double> lastGraphChannelValues = new LinkedHashMap<>();
    // Tracked graph live inputs
    private Map<String, AdvancedGraphDocument.Value> graphLiveInputs = new LinkedHashMap<>();
    // Tracked graph live outputs
    private Map<String, AdvancedGraphDocument.Value> graphLiveOutputs = new LinkedHashMap<>();
    // Tracked graph observer inputs
    private Map<String, AdvancedGraphDocument.Value> graphObserverInputs = new LinkedHashMap<>();
    // Tracked graph observer outputs
    private Map<String, AdvancedGraphDocument.Value> graphObserverOutputs = new LinkedHashMap<>();
    // Cached graph runtime observers
    private List<GraphRuntimeObserver> cachedGraphRuntimeObservers = List.of();
    // Last graph runtime observer scan
    private long lastGraphRuntimeObserverScan = Long.MIN_VALUE;
    // Last graph runtime send tick
    private long lastGraphRuntimeSendTick = Long.MIN_VALUE;
    // Tracks whether graph runtime payload is dirty
    private boolean graphRuntimePayloadDirty;
    // Current graph observer sample cursor
    private int graphObserverSampleCursor;
    // Current graph observer sample graph
    private AdvancedGraphDocument graphObserverSampleGraph;
    // Current graph observer sample revision
    private int graphObserverSampleRevision = Integer.MIN_VALUE;
    // Tracked direct runtime send states
    private final Map<String, DirectRuntimeSendState> directRuntimeSendStates = new LinkedHashMap<>();
    // Acc display runtime snapshot revision
    private long accDisplayRuntimeSnapshotRevision = Long.MIN_VALUE;
    // Tracked ACC display runtime inputs
    private Map<String, AdvancedGraphDocument.Value> accDisplayRuntimeInputs = Map.of();
    // Tracked ACC display runtime outputs
    private Map<String, AdvancedGraphDocument.Value> accDisplayRuntimeOutputs = Map.of();
    // Tracked published ACC displays
    private final Set<AccDisplayBlockEntity> publishedAccDisplays =
            Collections.newSetFromMap(new IdentityHashMap<>());
    // Tracked published display adapters
    private final Set<UniversalDisplayAdapterBlockEntity> publishedDisplayAdapters =
            Collections.newSetFromMap(new IdentityHashMap<>());
    // Tracks whether ACC display targets are dirty
    private boolean accDisplayTargetsDirty = true;
    // Tracks whether ACC display frames are dirty
    private boolean accDisplayFramesDirty = true;
    // Last ACC display runtime revision
    private long lastAccDisplayRuntimeRevision = Long.MIN_VALUE;
    // Last ACC display update tick
    private long lastAccDisplayUpdateTick = Long.MIN_VALUE;
    // Last ACC display graph revision
    private int lastAccDisplayGraphRevision = Integer.MIN_VALUE;
    // Last ACC display status hash
    private int lastAccDisplayStatusHash = Integer.MIN_VALUE;
    // Current ACC display structure revision
    private int accDisplayStructureRevision = Integer.MIN_VALUE;
    // Controls whether to publish ship information
    private boolean publishesShipInformation;
    // Tracked client graph live inputs
    private Map<String, AdvancedGraphLiveValue> clientGraphLiveInputs = new LinkedHashMap<>();
    // Tracked client graph live outputs
    private Map<String, AdvancedGraphLiveValue> clientGraphLiveOutputs = new LinkedHashMap<>();
    // Current client graph snapshot draft revision
    private int clientGraphSnapshotDraftRevision;
    // Current client graph snapshot active revision
    private int clientGraphSnapshotActiveRevision;
    // Tracks whether client graph snapshot is applied
    private boolean clientGraphSnapshotApplied;
    // Tracked graph execution pulses
    private Map<String, Long> graphExecutionPulses = new LinkedHashMap<>();
    // Tracked goggles graph snapshot fingerprints
    private final Map<UUID, Integer> gogglesGraphSnapshotFingerprints = new LinkedHashMap<>();
    // Cached graph target access
    private final Map<AdvancedGraphDocument.Node, TargetAccess> graphTargetAccessCache = new IdentityHashMap<>();
    // Cached missing graph target access
    private final Set<AdvancedGraphDocument.Node> missingGraphTargetAccessCache =
            Collections.newSetFromMap(new IdentityHashMap<>());
    // Graph target access cache tick
    private long graphTargetAccessCacheTick = Long.MIN_VALUE;
    // Cached dependency draft graph
    private AdvancedGraphDocument cachedDependencyDraftGraph;
    // Cached dependency active graph
    private AdvancedGraphDocument cachedDependencyActiveGraph;
    // Cached dependency draft revision
    private int cachedDependencyDraftRevision = -1;
    // Cached dependency active revision
    private int cachedDependencyActiveRevision = -1;
    // Cached dependency ship control map
    private @Nullable ShipControlMap cachedDependencyShipControlMap;
    // Cached dependency ship control map id
    private @Nullable UUID cachedDependencyShipControlMapId;
    // Cached additional schematic sub-level ids
    private Set<UUID> cachedAdditionalSchematicSubLevelIds = Set.of();
    // Cached stored ship control map id
    private @Nullable UUID cachedStoredShipControlMapId;
    // Cached stored ship control map
    private @Nullable ShipControlMap cachedStoredShipControlMap;
    // Tracks whether the stored ship control map cache was checked
    private boolean cachedStoredShipControlMapAttempted;
    // Pending schematic ship control map
    private @Nullable CompoundTag pendingSchematicShipControlMap;
    // Last graph observer sample
    private long lastGraphObserverSample = Long.MIN_VALUE;
    // Last client profiler sample
    private long lastClientProfilerSample = Long.MIN_VALUE;
    // Last goggles tracking sample
    private long lastGogglesTrackingSample = Long.MIN_VALUE;
    // Current client profiler fps
    private double clientProfilerFps;
    // Current client profiler frame time millis
    private double clientProfilerFrameTimeMillis;
    // Tracks whether client profiler sample is initialized
    private boolean clientProfilerSampleInitialized;
    // Tracks whether graph observer sampled draft is set
    private boolean graphObserverSampledDraft;
    // Tracks whether graph owned binding sync is queued
    private boolean graphOwnedBindingSyncQueued;
    // Tracks whether server shutdown is prepared
    private boolean serverShutdownPrepared;
    // Tracks whether server runtime is initialized
    private boolean serverRuntimeInitialized;
    // Last graph runtime mirror revision
    private long lastGraphRuntimeMirrorRevision = Long.MIN_VALUE;
    // Pending server shutdown snapshot
    private @Nullable CompoundTag pendingServerShutdownSnapshot;
    // Tracks whether graph owned binding channels are verified
    private boolean graphOwnedBindingChannelsVerified;
    // Tracked mouse input values
    private final Map<String, Double> mouseInputValues = new LinkedHashMap<>();
    // Active mouse inputs
    private final Set<String> activeMouseInputs = new HashSet<>();
    // Tracked transient mouse inputs
    private final Set<String> transientMouseInputs = new HashSet<>();
    // Mouse input last movement tick count
    private final Map<String, Long> mouseInputLastMovementTicks = new LinkedHashMap<>();
    // Tracked physical interaction participants
    private final Set<UUID> physicalInteractionParticipants = new HashSet<>();
    // Last synced ship initialization visible state
    private boolean lastSyncedShipInitializationVisible;
    // Last synced ship initialization percent
    private int lastSyncedShipInitializationPercent = -1;
    // Last synced ship initialization status
    private String lastSyncedShipInitializationStatus = "";
    // Tracks whether ship initialization progress is visible
    private boolean shipInitializationProgressVisible;
    // Current ship initialization progress percent
    private int shipInitializationProgressPercent;
    // Current ship initialization progress status
    private String shipInitializationProgressStatus = "";
    // Current ship initialization progress viewer id
    private @Nullable UUID shipInitializationProgressViewerId;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller
    public AdvancedContraptionControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the assignable targets
    @Override
    public List<ControllerDiscoveryNode> getAssignableTargets() {
        Map<String, ControllerDiscoveryNode> merged = new LinkedHashMap<>();
        for (ControllerDiscoveryNode node : super.getAssignableTargets()) {
            merged.putIfAbsent(node.nodeId(), node);
        }
        for (ControllerDiscoveryNode node : discoverAccDisplayTargets()) {
            merged.putIfAbsent(node.nodeId(), node);
        }
        return new ArrayList<>(merged.values());
    }

    // Discover the ACC display targets
    private List<ControllerDiscoveryNode> discoverAccDisplayTargets() {
        if (level == null) {
            return List.of();
        }
        Map<BlockPos, AccDisplayBlockEntity> displays = new LinkedHashMap<>();
        Map<BlockPos, UniversalDisplayAdapterBlockEntity> adapters = new LinkedHashMap<>();
        Map<BlockPos, BlockEntity> externalSources = new LinkedHashMap<>();
        for (Direction dir : Direction.values()) {
            BlockEntity adjacent = level.getBlockEntity(worldPosition.relative(dir));
            addDisplayTarget(displays, adjacent);
            addDisplayAdapter(adapters, adjacent);
            addExternalDisplaySource(externalSources, adjacent);
        }

        List<ControllerDiscoveryNode> res = new ArrayList<>();
        for (AccDisplayBlockEntity display : displays.values()) {
            UUID subLevelId = SimulatedHelper.getContainingSubLevelId(display);
            BlockPos pos = display.getBlockPos();
            String group = subLevelId == null ? "world" : "sublevel:" + subLevelId;
            String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(
                    display.getBlockState().getBlock()));
            String nodeId = "acc_display::" + group + "::" + pos.asLong();
            String label = display.getBlockState().getBlock().getName().getString()
                    + " [" + pos.toShortString() + "]";
            res.add(new ControllerDiscoveryNode(nodeId, ControllerDiscoveryKind.DISPLAY,
                    group, blockId, label, subLevelId, pos));
        }
        for (UniversalDisplayAdapterBlockEntity adapter : adapters.values()) {
            UUID subLevelId = SimulatedHelper.getContainingSubLevelId(adapter);
            BlockPos pos = adapter.getBlockPos();
            String group = subLevelId == null ? "world" : "sublevel:" + subLevelId;
            String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(
                    adapter.getBlockState().getBlock()));
            String nodeId = "display_adapter::" + group + "::" + pos.asLong();
            String label = adapter.getBlockState().getBlock().getName().getString()
                    + " [" + pos.toShortString() + "]";
            res.add(new ControllerDiscoveryNode(nodeId, ControllerDiscoveryKind.DISPLAY_ADAPTER,
                    group, blockId, label, subLevelId, pos));
        }
        for (BlockEntity src : externalSources.values()) {
            UUID subLevelId = SimulatedHelper.getContainingSubLevelId(src);
            BlockPos pos = src.getBlockPos();
            String group = subLevelId == null ? "world" : "sublevel:" + subLevelId;
            String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(
                    src.getBlockState().getBlock()));
            String nodeId = "display_source::" + group + "::" + pos.asLong();
            String label = src.getBlockState().getBlock().getName().getString()
                    + " [" + pos.toShortString() + "]";
            res.add(new ControllerDiscoveryNode(nodeId, ControllerDiscoveryKind.DISPLAY_ADAPTER,
                    group, blockId, label, subLevelId, pos));
        }
        return res;
    }

    // Add the display target
    private static void addDisplayTarget(Map<BlockPos, AccDisplayBlockEntity> displays,
                                         @Nullable BlockEntity candidate) {
        if (!(candidate instanceof AccDisplayBlockEntity display)) {
            return;
        }
        AccDisplayBlockEntity root = display.networkRoot();
        if (root == null) {
            root = display;
        }
        displays.putIfAbsent(root.getBlockPos(), root);
    }

    // Add the display adapter
    private static void addDisplayAdapter(Map<BlockPos, UniversalDisplayAdapterBlockEntity> adapters,
                                          @Nullable BlockEntity candidate) {
        if (candidate instanceof UniversalDisplayAdapterBlockEntity adapter) {
            adapters.putIfAbsent(adapter.getBlockPos(), adapter);
        }
    }

    // Add the external display source
    private static void addExternalDisplaySource(Map<BlockPos, BlockEntity> sources,
                                                 @Nullable BlockEntity candidate) {
        if (AccDisplaySourceRegistry.isSource(candidate)) {
            sources.putIfAbsent(candidate.getBlockPos(), candidate);
        }
    }

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, AdvancedContraptionControllerBlockEntity be) {
        be.tick();
    }

    // Initialize the advanced contraption controller
    @Override
    public void initialize() {
        super.initialize();
        if (getLevel() != null && !getLevel().isClientSide
                && !AccDisplayControllerRegistry.isStopping(getLevel())) {
            serverRuntimeInitialized = true;
            AccDisplayControllerRegistry.register(this);
            AdvancedControllerNamedEventBus.register(this);
            refreshGraphRuntime(true);
            restoreShutdownSnapshot();
            shipControlRuntime.resumeAfterLoad(
                    shippingScheduleRuntime.requiresControlAfterLoad());
            shippingScheduleRuntime.resumeAfterLoad();
            if (!shipLogisticsRuns.isEmpty()) {
                shipStockNetworkCache.snapshot();
            }
        }
    }

    // Check if the controller runtime is idle
    @Override
    protected boolean isControllerRuntimeIdle() {
        return isControllerRuntimeIdleIgnoringBindings(graphRuntime.graphOwnedBindings(activeGraph), true);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the advanced contraption controller
    @Override
    public void tick() {
        if (AccDisplayControllerRegistry.isStopping(getLevel())
                || serverShutdownPrepared || !isControllerRuntimeLoaded()) {
            return;
        }
        super.tick();
        shipControlRuntime.tick();
        syncShipInit();
        shippingScheduleRuntime.tick();
        if (getLevel() != null && !getLevel().isClientSide
                && Math.floorMod(getLevel().getGameTime() + getBlockPos().asLong(), 4L) == 0L
                && shipLogisticsRuns.stream().anyMatch(run ->
                run.resourceType() == ShipLogisticsRun.ResourceType.FUEL)) {
            ShipCargoAutomation.refuelFromShipRuns(this, List.of(), true, true);
        }
        if (getLevel() != null) {
            resetStaleMouseAxes();
            checkGraphBindings();
            flushGraphBindingSync();
            boolean activeGraphHasContent = hasGraphContent(activeGraph);
            boolean anyGraphHasContent = activeGraphHasContent || hasGraphContent(draftGraph);
            List<GraphRuntimeObserver> observers = cachedGraphRuntimeObservers();
            syncGogglesGraphs(observers);
            if (!anyGraphHasContent && !graphRuntime.hasPendingWork()) {
                clearIdleGraphMirrors();
                publishAccDisplayUpdates();
                return;
            }
            if (activeGraphHasContent && graphRuntime.needsBindingPolling(activeGraph)) {
                trackGraphChannelChanges();
            }
            if (activeGraphHasContent || graphRuntime.hasPendingWork()) {
                if (graphRuntime.needsRegularTick(activeGraph, true)// !observers.isEmpty())
                        || graphRuntime.hasPendingWork()) {
                    graphRuntime.tick(activeGraph, true); //!observers.isEmpty());
                }
            }
            if (!transientMouseInputs.isEmpty()) {
                for (String input : transientMouseInputs) {
                    mouseInputValues.remove(input);
                    activeMouseInputs.remove(input);
                }
                transientMouseInputs.clear();
            }
            updateGraphMirrors(observers);
            publishAccDisplayUpdates();
            if (!graphRuntime.diagnostics().isEmpty()) {
                graphDiagnostics = graphRuntime.diagnostics();
            }
        }
    }

    // Prepare the server shutdown
    void prepareForServerShutdown() {
        if (serverShutdownPrepared) {
            return;
        }
        serverShutdownPrepared = true;
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("ShipControl", shipControlRuntime.createShutdownSnapshot());
        snapshot.put("GraphRuntime", graphRuntime.createShutdownSnapshot());
        pendingServerShutdownSnapshot = snapshot;
        shippingScheduleRuntime.prepareForServerShutdown();
        graphRuntime.prepareForServerShutdown();
        shipControlRuntime.prepareForServerShutdown();
        shipStockNetworkCache.clear();
        cachedGraphRuntimeObservers = List.of();
        graphObserverInputs.clear();
        graphObserverOutputs.clear();
        graphOwnedBindingSyncQueued = false;
        setChanged();
    }

    // Check if this has ship control module
    public boolean hasShipControlModule() {
        return shipControlRuntime.isModuleAttached();
    }

    // Get the configured ship flight behavior
    public ScmFlightBehavior getConfiguredShipFlightBehavior() {
        return shipFlightBehavior;
    }

    // Get the active ship flight behavior
    ScmFlightBehavior activeShipFlightBehavior() {
        return hasShipControlModule() && shippingScheduleRuntime.hasPresentPilot()
                ? shipFlightBehavior : ScmFlightBehavior.DEFAULT;
    }

    // Get the ship control mode
    public ScmControlMode getShipControlMode() {
        return shipControlMode;
    }

    // Set the ship control mode
    void setShipControlMode(@Nullable String modeId) {
        ScmControlMode resolved = ScmControlModeRegistry.resolve(modeId);
        if (!resolved.id().equals(shipControlMode.id())) {
            shipControlMode = resolved;
            setChanged();
        }
    }

    // Check if ship control is initializing
    public boolean isShipControlInitializing() {
        return shipControlRuntime.isInitializing();
    }

    // Check if ship control is restoring after a load
    boolean isShipControlRestoringAfterLoad() {
        return shipControlRuntime.isRestoringAfterLoad();
    }

    // Release the ship control after load
    void releaseShipControlAfterLoad() {
        shipControlRuntime.requestPostLoadPoseRelease();
    }

    // Force the shipping schedule resume after load
    void forceShippingScheduleResumeAfterLoad() {
        shippingScheduleRuntime.forceResumeAfterLoad();
    }

    // Cancel the ship control load hold
    void cancelShipControlLoadHold() {
        shipControlRuntime.cancelPostLoadPoseHold();
    }

    // Check if ship initialization progress should be displayed
    public boolean displaysShipInitializationProgress() {
        return shipInitializationProgressVisible;
    }

    // Check if ship initialization progress should be displayed for the player
    public boolean displaysShipInitializationProgressFor(@Nullable Player player) {
        return shipInitializationProgressVisible && player != null
                && shipInitializationProgressViewerId != null
                && shipInitializationProgressViewerId.equals(player.getUUID());
    }

    // Get the ship initialization progress percent
    public int getShipInitializationProgressPercent() {
        return shipInitializationProgressPercent;
    }

    // Get the ship initialization progress status
    public String getShipInitializationProgressStatus() {
        return shipInitializationProgressStatus;
    }

    // Sync the ship init
    private void syncShipInit() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean visible = shipControlRuntime.displaysInitializationProgress();
        int percent = ShipControlModuleRuntime.progressPercent(
                shipControlRuntime.initializationProgress());
        String status = visible ? shipControlRuntime.initializationStatus() : "";
        if (visible == lastSyncedShipInitializationVisible
                && percent == lastSyncedShipInitializationPercent
                && status.equals(lastSyncedShipInitializationStatus)) {
            return;
        }
        if (!visible && lastSyncedShipInitializationVisible) {
            shipControlRuntime.syncInitializationDisplays(false, 0, "");
        }
        lastSyncedShipInitializationVisible = visible;
        lastSyncedShipInitializationPercent = percent;
        lastSyncedShipInitializationStatus = status;
        shipInitializationProgressVisible = visible;
        shipInitializationProgressPercent = percent;
        shipInitializationProgressStatus = status;
        if (!visible) {
            shipInitializationProgressViewerId = null;
        }
        sendData();
    }

    // Install the shipping schedule
    public boolean installShippingSchedule(com.simibubi.create.content.trains.schedule.Schedule schedule,
                                           @Nullable UUID pilotId) {
        return shippingScheduleRuntime.install(schedule, pilotId);
    }

    // Install the shipping schedule
    public boolean installShippingSchedule(com.simibubi.create.content.trains.schedule.Schedule schedule,
                                           @Nullable UUID pilotId,
                                           ShippingAutoRefuelSettings autoRefuel) {
        return shippingScheduleRuntime.install(schedule, pilotId, autoRefuel);
    }

    // Install the blaze burner shipping schedule
    public boolean installShippingSchedule(com.simibubi.create.content.trains.schedule.Schedule schedule,
                                           UUID pilotId,
                                           ShippingAutoRefuelSettings autoRefuel,
                                           boolean blazeBurnerPilot) {
        return shippingScheduleRuntime.install(schedule, pilotId, autoRefuel, blazeBurnerPilot);
    }

    // Remove the shipping schedule
    public ItemStack removeShippingSchedule() {
        return shippingScheduleRuntime.remove();
    }

    // Check if this has shipping schedule
    public boolean hasShippingSchedule() {
        return shippingScheduleRuntime.hasSchedule();
    }

    // Check if this has an active shipping pilot
    public boolean hasActiveShippingSchedulePilot() {
        return shippingScheduleRuntime.hasPresentPilot();
    }

    // Check if this has a blaze burner shipping pilot
    public boolean hasBlazeBurnerShippingPilot() {
        return shippingScheduleRuntime.hasBlazeBurnerPilot();
    }

    // Copy the shipping schedule
    public ItemStack copyShippingSchedule() {
        return shippingScheduleRuntime.copyScheduleItem();
    }

    // Get the shipping schedule pilot id
    public @Nullable UUID getShippingSchedulePilotId() {
        return shippingScheduleRuntime.pilotId();
    }

    // Get the shipping schedule status
    public String getShippingScheduleStatus() {
        return shippingScheduleRuntime.status();
    }

    // Run the shipping schedule graph command
    public boolean executeShippingScheduleGraphCommand(String nodeType) {
        return shippingScheduleRuntime.executeGraphCommand(nodeType);
    }

    // Get the CRN ship display data
    public RailwayNavigatorGraphCompat.ShipDisplayData getCrnShipDisplayData(UUID shipId) {
        return shippingScheduleRuntime.crnDisplayData(shipId);
    }

    // Get the CRN ship display data
    public RailwayNavigatorGraphCompat.ShipDisplayData getCrnShipDisplayData() {
        UUID shipId = shipControlRuntime.mapId();
        if (shipId == null) {
            shipId = SimulatedHelper.getContainingSubLevelId(this);
        }
        return shippingScheduleRuntime.crnDisplayData(
                shipId == null ? new UUID(0L, 0L) : shipId);
    }

    // Get the ship name
    public String getShipName() {
        return shipName;
    }

    // Set the ship name
    public void setShipName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isBlank()) {
            return;
        }
        normalized = normalized.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(64)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
        if (normalized.isBlank() || shipName.equals(normalized)) {
            return;
        }
        shipName = normalized;
        setChanged();
        sendData();
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean res = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!shippingScheduleRuntime.hasSchedule()) {
            return res;
        }
        ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(this);
        tooltip.add(CTTooltipHelper.line("Shipping",
                CTTooltipHelper.value(shippingScheduleRuntime.status(), ChatFormatting.GOLD)));
        tooltip.add(CTTooltipHelper.line("Fuel Reserve",
                CTTooltipHelper.value(Math.round(fuel.ratio() * 100.0D) + "%", fuel.ratio() > 0.1D
                        ? ChatFormatting.GREEN : ChatFormatting.RED)));
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       DISPLAYS / RUNTIME
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the ship control graph command
    public boolean executeShipControlGraphCommand(String nodeType, Map<String, Double> parameters) {
        return shipControlRuntime.execute(nodeType, parameters);
    }

    // Run the ship control graph command
    public boolean executeShipControlGraphCommand(
            String commandId, String nodeType, Map<String, Double> parameters
    ) {
        return shipControlRuntime.execute(commandId, nodeType, parameters);
    }

    // Run the ship control graph command
    public boolean executeShipControlGraphCommand(
            String commandId,
            String nodeType,
            Map<String, Double> parameters,
            Map<String, String> textParameters
    ) {
        if ("ship_flight_behavior".equals(nodeType)) {
            return applyShipFlightBehavior(textParameters.get("behavior"));
        }
        return shipControlRuntime.execute(
                commandId, nodeType, parameters, textParameters);
    }

    // Run the ship control graph command
    public boolean executeShipControlGraphCommand(
            @Nullable UUID progressViewerId,
            String commandId,
            String nodeType,
            Map<String, Double> parameters,
            Map<String, String> textParameters
    ) {
        boolean accepted = "ship_flight_behavior".equals(nodeType)
                ? applyShipFlightBehavior(textParameters.get("behavior"))
                : shipControlRuntime.execute(
                        commandId, nodeType, parameters, textParameters);
        if (accepted && "ship_initialize".equals(nodeType)) {
            shipInitializationProgressViewerId = progressViewerId;
        }
        return accepted;
    }

    // Check if the ship control graph command is pending
    public boolean isShipControlGraphCommandPending(String commandId, String nodeType) {
        if ("ship_flight_behavior".equals(nodeType)) {
            return false;
        }
        return shipControlRuntime.isCommandPending(commandId, nodeType);
    }

    // Check if the ship control graph command is complete
    public boolean isShipControlGraphCommandComplete(String commandId, String nodeType) {
        if ("ship_flight_behavior".equals(nodeType)) {
            return true;
        }
        return shipControlRuntime.isCommandComplete(commandId, nodeType);
    }

    // Apply the ship flight behavior
    private boolean applyShipFlightBehavior(@Nullable String behaviorId) {
        String requestedId = behaviorId == null || behaviorId.isBlank()
                ? ScmFlightBehavior.PREFER_SHIP_DIRECTION.id() : behaviorId;
        if (!hasShipControlModule() || !shippingScheduleRuntime.hasPresentPilot()
                || !ScmFlightBehavior.isKnownId(requestedId)) {
            return false;
        }
        ScmFlightBehavior behavior = ScmFlightBehavior.fromId(requestedId);
        if (shipFlightBehavior != behavior) {
            shipFlightBehavior = behavior;
            setChanged();
            sendData();
        }
        return true;
    }

    // Get the ship control graph command value
    public AdvancedGraphDocument.Value getShipControlGraphCommandValue(
            String commandId, String nodeType, String port
    ) {
        return shipControlRuntime.commandGraphValue(commandId, nodeType, port);
    }

    // Get the ship control graph value
    public AdvancedGraphDocument.Value getShipControlGraphValue(String port) {
        if (port != null && port.startsWith("shipping_")) {
            return shippingScheduleRuntime.graphValue(port);
        }
        return shipControlRuntime.graphValue(port);
    }

    // Get the ship control graph value
    public AdvancedGraphDocument.Value getShipControlGraphValue(
            String port, double collisionDetectionDistance
    ) {
        if (port != null && port.startsWith("shipping_")) {
            return shippingScheduleRuntime.graphValue(port);
        }
        return shipControlRuntime.graphValue(port, collisionDetectionDistance);
    }

    // Get the ship control graph value
    public AdvancedGraphDocument.Value getShipControlGraphValue(
            String port,
            double collisionDetectionDistance,
            double collisionPollRate
    ) {
        if (port != null && port.startsWith("shipping_")) {
            return shippingScheduleRuntime.graphValue(port);
        }
        return shipControlRuntime.graphValue(
                port, collisionDetectionDistance, collisionPollRate);
    }

    // Select the ship docking connector
    public @Nullable ShipControlModuleRuntime.MappedDockingConnector selectShipDockingConnector(
            Vec3 desiredWorldFacing
    ) {
        return shipControlRuntime.selectDockingConnector(desiredWorldFacing);
    }

    // Select the ship docking connector
    public @Nullable ShipControlModuleRuntime.MappedDockingConnector selectShipDockingConnector(
            Vec3 desiredWorldFacing,
            Vec3 targetWorldPosition
    ) {
        return shipControlRuntime.selectDockingConnector(
                desiredWorldFacing, targetWorldPosition);
    }

    // Get the ship docking connector
    public @Nullable ShipControlModuleRuntime.MappedDockingConnector getShipDockingConnector(
            int connectorIndex
    ) {
        return shipControlRuntime.mappedDockingConnector(connectorIndex);
    }

    // Get the ship docking connectors
    public List<ShipControlModuleRuntime.MappedDockingConnector> getShipDockingConnectors() {
        return shipControlRuntime.mappedDockingConnectors();
    }

    // Get the current ship envelope
    public SableAssemblyBoundsApi.Envelope getShipEnvelope() {
        return shipControlRuntime.shipEnvelope();
    }

    // Activate the ship docking connector
    public void activateShipDockingConnector(int connectorIndex) {
        shipControlRuntime.activateDockingConnector(connectorIndex);
    }

    // Set the ship docking magnetic capture
    void setShipDockingMagneticCapture(int connectorIndex, boolean active) {
        shipControlRuntime.setDockingMagneticCapture(connectorIndex, active);
    }

    // Get the ship stock network snapshot
    public ShipStockNetworkCache.Snapshot getShipStockNetworkSnapshot() {
        return shipStockNetworkCache.snapshot();
    }

    // Get the ship stock network access
    public ShipStockNetworkCache.NetworkAccess getShipStockNetworkAccess(
            UUID subLevelId, BlockPos connectorPosition
    ) {
        return shipStockNetworkCache.accessFor(subLevelId, connectorPosition);
    }

    // Get the ship stock network access
    public ShipStockNetworkCache.NetworkAccess getShipStockNetworkAccess(
            UUID subLevelId,
            BlockPos connectorPosition,
            Set<ShipLogisticsRun.ResourceType> resourceTypes
    ) {
        return shipStockNetworkCache.accessFor(subLevelId, connectorPosition, resourceTypes);
    }

    // Get the ship logistics run access
    public ShipStockNetworkCache.NetworkAccess getShipLogisticsRunAccess(
            ShipLogisticsRun.ResourceType resourceType
    ) {
        return shipStockNetworkCache.accessFor(resourceType);
    }

    // Get the mapped ship sublevel ids
    public Set<UUID> getMappedShipSubLevelIds() {
        return shipControlRuntime.mappedSubLevelIds();
    }

    // Update the graph mirrors
    private void updateGraphMirrors(List<GraphRuntimeObserver> observers) {
        if (observers.isEmpty()) {
            graphLiveInputs.clear();
            graphLiveOutputs.clear();
            graphObserverInputs.clear();
            graphObserverOutputs.clear();
            graphExecutionPulses.clear();
            lastGraphObserverSample = Long.MIN_VALUE;
            lastGraphRuntimeMirrorRevision = Long.MIN_VALUE;
            lastGraphRuntimeSendTick = Long.MIN_VALUE;
            graphRuntimePayloadDirty = false;
            graphObserverSampleCursor = 0;
            graphObserverSampleGraph = null;
            graphObserverSampleRevision = Integer.MIN_VALUE;
            return;
        }
        boolean sampleDraft = observers.stream().anyMatch(observer -> observer.containerId() >= 0);
        long gameTime = level == null ? 0L : level.getGameTime();
        boolean observerSampleDue = lastGraphObserverSample == Long.MIN_VALUE
                || gameTime - lastGraphObserverSample >= GRAPH_OBSERVER_SAMPLE_INTERVAL
                || sampleDraft != graphObserverSampledDraft;
        if (observerSampleDue) {
            AdvancedGraphDocument observedGraph = sampleDraft ? draftGraph : activeGraph;
            if (graphObserverSampleGraph != observedGraph
                    || graphObserverSampleRevision != observedGraph.revision()) {
                graphObserverInputs = new LinkedHashMap<>();
                graphObserverOutputs = new LinkedHashMap<>();
                graphObserverSampleCursor = 0;
                graphObserverSampleGraph = observedGraph;
                graphObserverSampleRevision = observedGraph.revision();
            }
            sampleGraphObservers(observedGraph,
                    graphObserverInputs, graphObserverOutputs);
            lastGraphObserverSample = gameTime;
            graphObserverSampledDraft = sampleDraft;
        }
        long runtimeRevision = graphRuntime.liveValueRevision();
        if (!observerSampleDue && runtimeRevision == lastGraphRuntimeMirrorRevision) {
            return;
        }
        Map<String, AdvancedGraphDocument.Value> liveInputs = new LinkedHashMap<>(graphRuntime.liveInputs());
        Map<String, AdvancedGraphDocument.Value> liveOutputs = new LinkedHashMap<>(graphRuntime.liveOutputs());
        liveInputs.putAll(graphObserverInputs);
        liveOutputs.putAll(graphObserverOutputs);
        Map<String, Long> executionPulses = graphRuntime.executionPulses();
        lastGraphRuntimeMirrorRevision = runtimeRevision;
        if (!graphLiveInputs.equals(liveInputs) || !graphLiveOutputs.equals(liveOutputs)
                || !graphExecutionPulses.equals(executionPulses)) {
            graphLiveInputs = liveInputs;
            graphLiveOutputs = liveOutputs;
            graphExecutionPulses = new LinkedHashMap<>(executionPulses);
            graphRuntimePayloadDirty = true;
        }
        if (graphRuntimePayloadDirty
                && (lastGraphRuntimeSendTick == Long.MIN_VALUE
                || gameTime - lastGraphRuntimeSendTick >= GRAPH_RUNTIME_SEND_INTERVAL)) {
            sendGraphRuntimeData(observers);
            lastGraphRuntimeSendTick = gameTime;
            graphRuntimePayloadDirty = false;
        }
    }

    // Sample the graph observers
    private void sampleGraphObservers(AdvancedGraphDocument graph,
                                           Map<String, AdvancedGraphDocument.Value> inputs,
                                           Map<String, AdvancedGraphDocument.Value> outputs) {
        if (graph == null) return;
        if (graph.edges().isEmpty()) {
            graphObserverSampleCursor = 0;
            return;
        }
        graphRuntime.beginPreviewSample(graph);
        Map<String, AdvancedGraphDocument.Node> nodes = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            nodes.put(node.id(), node);
        }
        Set<String> sampledInputs = new HashSet<>();
        Set<String> sampledOutputs = new HashSet<>();
        int edgeCount = graph.edges().size();
        int examined = 0;
        int edgeIndex = Math.floorMod(graphObserverSampleCursor, edgeCount);
        while (examined < Math.min(GRAPH_OBSERVER_EDGE_BUDGET, edgeCount)) {
            AdvancedGraphDocument.Edge edge = graph.edges().get(edgeIndex);
            edgeIndex = (edgeIndex + 1) % edgeCount;
            examined++;
            AdvancedGraphDocument.Node src = nodes.get(edge.fromNode());
            AdvancedGraphDocument.Node target = nodes.get(edge.toNode());
            if (src == null || target == null) {
                continue;
            }
            String outputType = AdvancedGraphCatalog.outputs(src).get(edge.fromPort());
            String inputType = AdvancedGraphCatalog.inputs(target).get(edge.toPort());
            if (outputType == null || inputType == null
                    || "exec".equals(outputType) || "exec".equals(inputType)) {
                continue;
            }
            try {
                String outputKey = src.id() + ":" + edge.fromPort();
                if (sampledOutputs.add(outputKey)) {
                    outputs.put(outputKey, graphRuntime.previewOutput(graph, src, edge.fromPort()));
                }
                String inputKey = target.id() + ":" + edge.toPort();
                if (sampledInputs.add(inputKey)) {
                    inputs.put(inputKey, graphRuntime.previewInput(graph, target, edge.toPort()));
                }
            } catch (RuntimeException ignored) {
            }
        }
        graphObserverSampleCursor = edgeIndex;
    }

    // Track the graph channel changes
    private void trackGraphChannelChanges() {
        Set<String> activeBindings = new HashSet<>();
        for (String bindingId : graphRuntime.polledBindings(activeGraph)) {
            trackGraphBindingChange(bindingId, activeBindings);
        }
        lastGraphChannelValues.keySet().removeIf(binding -> !activeBindings.contains(binding));
    }

    // Track the graph binding change
    private void trackGraphBindingChange(String bindingId, Set<String> activeBindings) {
        if (bindingId == null || bindingId.isBlank()) {
            return;
        }
        activeBindings.add(bindingId);
        double val = getGraphBindingValue(bindingId);
        boolean active = isGraphBindingActive(bindingId);
        Double prev = lastGraphChannelValues.put(bindingId, val);
        if (prev == null) {
            if (active) {
                graphRuntime.setBindingActive(bindingId, true);
            }
            return;
        }
        if (prev != null && Math.abs(prev - val) > 0.0001) {
            graphRuntime.setBindingActive(bindingId, active);
            graphRuntime.enqueue("channel:" + bindingId);
        }
    }

    // Clear the idle graph mirrors
    private void clearIdleGraphMirrors() {
        if (!lastGraphChannelValues.isEmpty()) {
            lastGraphChannelValues.clear();
        }
        boolean hadRuntimeData = !graphLiveInputs.isEmpty() || !graphLiveOutputs.isEmpty()
                || !graphObserverInputs.isEmpty() || !graphObserverOutputs.isEmpty()
                || !graphExecutionPulses.isEmpty();
        if (hadRuntimeData) {
            graphLiveInputs = new LinkedHashMap<>();
            graphLiveOutputs = new LinkedHashMap<>();
            graphObserverInputs = new LinkedHashMap<>();
            graphObserverOutputs = new LinkedHashMap<>();
            graphExecutionPulses = new LinkedHashMap<>();
        }
        lastGraphObserverSample = Long.MIN_VALUE;
        graphObserverSampleCursor = 0;
        graphObserverSampleGraph = null;
        graphObserverSampleRevision = Integer.MIN_VALUE;
        if (hadRuntimeData) sendGraphRuntimeData(graphRuntimeObservers());
    }

    // Get the ship logistics runs
    public List<ShipLogisticsRun> shipLogisticsRuns() {
        return List.copyOf(shipLogisticsRuns);
    }

    // Keep the original flat stock endpoint working
    public void configureWirelessStockEndpoint(UUID subLevelId, BlockPos pos, boolean fuel) {
        List<ShipLogisticsRun.ResourceType> types = fuel
                ? List.of(ShipLogisticsRun.ResourceType.FUEL)
                : List.of(ShipLogisticsRun.ResourceType.ITEM,
                ShipLogisticsRun.ResourceType.FLUID, ShipLogisticsRun.ResourceType.ENERGY);
        for (ShipLogisticsRun.ResourceType type : types) {
            ShipLogisticsRun run = shipLogisticsRuns.stream()
                    .filter(candidate -> candidate.resourceType() == type
                            && candidate.name().equals(type.label() + " Run"))
                    .findFirst().orElseGet(() -> ShipLogisticsRun.create(
                            type, type.label() + " Run", List.of()));
            List<ShipLogisticsRun.Endpoint> endpoints = new ArrayList<>(run.endpoints());
            ShipLogisticsRun.Endpoint endpoint = new ShipLogisticsRun.Endpoint(subLevelId, pos);
            if (!endpoints.contains(endpoint)) endpoints.add(endpoint);
            configureShipLogisticsRun(run.withEndpoints(endpoints));
        }
    }

    // Configure the ship logistics run
    public void configureShipLogisticsRun(ShipLogisticsRun run) {
        if (run == null) return;
        shipLogisticsRuns.removeIf(existing -> existing.id().equals(run.id()));
        shipLogisticsRuns.add(run);
        shipLogisticsRuns.sort(Comparator.comparing(ShipLogisticsRun::name,
                String.CASE_INSENSITIVE_ORDER).thenComparing(runValue -> runValue.id().toString()));
        shipStockNetworkCache.invalidate();
        setChanged();
        sendData();
    }

    // Remove the ship logistics run
    public boolean removeShipLogisticsRun(UUID runId) {
        if (runId == null || !shipLogisticsRuns.removeIf(run -> run.id().equals(runId))) {
            return false;
        }
        shipStockNetworkCache.invalidate();
        setChanged();
        sendData();
        return true;
    }

    // Toggle the ship logistics run endpoint
    public boolean toggleShipLogisticsRunEndpoint(
            UUID runId, UUID subLevelId, BlockPos position
    ) {
        ShipLogisticsRun run = shipLogisticsRuns.stream()
                .filter(candidate -> candidate.id().equals(runId)).findFirst().orElse(null);
        if (run == null || position == null) return false;
        ShipLogisticsRun.Endpoint endpoint = new ShipLogisticsRun.Endpoint(subLevelId, position);
        List<ShipLogisticsRun.Endpoint> endpoints = new ArrayList<>(run.endpoints());
        boolean added;
        if (endpoints.remove(endpoint)) {
            added = false;
        } else {
            endpoints.add(endpoint);
            added = true;
        }
        configureShipLogisticsRun(run.withEndpoints(endpoints));
        return added;
    }

    // Get the graph runtime observers
    private List<GraphRuntimeObserver> graphRuntimeObservers() {
        return graphRuntimeObservers(true);
    }

    // Get the graph runtime observers
    private List<GraphRuntimeObserver> graphRuntimeObservers(boolean liveMenus) {
        if (getLevel() == null || getLevel().isClientSide || getLevel().getServer() == null) {
            return List.of();
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        boolean portable = this instanceof PortableAdvancedContraptionControllerBlockEntity;
        return ControllerRuntimeObserver.graphObservers(
                        getLevel().getServer(), this, getBlockPos(), subLevelId,
                        portable, gogglesTrackerPairs.keySet(), liveMenus).stream()
                .map(observer -> new GraphRuntimeObserver(
                        observer.player(), observer.containerId(), observer.pairId()))
                .toList();
    }

    // Get the cached graph runtime observers
    private List<GraphRuntimeObserver> cachedGraphRuntimeObservers() {
        if (getLevel() == null || getLevel().isClientSide) {
            cachedGraphRuntimeObservers = List.of();
            lastGraphRuntimeObserverScan = Long.MIN_VALUE;
            return cachedGraphRuntimeObservers;
        }
        long gameTime = getLevel().getGameTime();
        if (lastGraphRuntimeObserverScan != Long.MIN_VALUE
                && gameTime >= lastGraphRuntimeObserverScan
                && gameTime - lastGraphRuntimeObserverScan < GRAPH_OBSERVER_SAMPLE_INTERVAL) {
            return cachedGraphRuntimeObservers;
        }
        cachedGraphRuntimeObservers = List.copyOf(graphRuntimeObservers(false));
        lastGraphRuntimeObserverScan = gameTime;
        return cachedGraphRuntimeObservers;
    }

    // Sync the goggles graphs
    private void syncGogglesGraphs(List<GraphRuntimeObserver> observers) {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        if (observers.stream().noneMatch(observer -> observer.containerId() < 0)) {
            gogglesGraphSnapshotFingerprints.clear();
            return;
        }
        Set<UUID> observedPlayers = new HashSet<>();
        List<GraphRuntimeObserver> newlySyncedObservers = new ArrayList<>();
        int fingerprint = 31 * System.identityHashCode(activeGraph) + activeGraph.revision();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        for (GraphRuntimeObserver observer : observers) {
            if (observer.containerId() >= 0) {
                continue;
            }
            UUID playerId = observer.player().getUUID();
            observedPlayers.add(playerId);
            if (!shouldSendGogglesGraphSnapshot(
                    gogglesGraphSnapshotFingerprints, playerId, fingerprint)) {
                continue;
            }
            AdvancedControllerGraphSnapshotPayload.send(
                    observer.player(), getBlockPos(), subLevelId, draftGraph, activeGraph);
            newlySyncedObservers.add(observer);
        }
        gogglesGraphSnapshotFingerprints.keySet().removeIf(
                playerId -> !observedPlayers.contains(playerId));
        if (!newlySyncedObservers.isEmpty()) {
            sendGraphRuntimeData(newlySyncedObservers);
        }
    }

    // Check if this should send goggles graph snapshot
    static boolean shouldSendGogglesGraphSnapshot(
            Map<UUID, Integer> sentFingerprints, UUID playerId, int fingerprint
    ) {
        if (sentFingerprints == null || playerId == null) {
            return false;
        }
        Integer prev = sentFingerprints.put(playerId, fingerprint);
        return prev == null || prev != fingerprint;
    }

    // Send the graph runtime data
    private void sendGraphRuntimeData(List<GraphRuntimeObserver> observers) {
        if (observers == null || observers.isEmpty()) {
            return;
        }
        Map<String, AdvancedGraphLiveValue> liveInputs = graphLiveValues(graphLiveInputs);
        Map<String, AdvancedGraphLiveValue> liveOutputs = graphLiveValues(graphLiveOutputs);
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        for (GraphRuntimeObserver observer : observers) {
            Map<UUID, String> trackerPairs = trackerPairsForObserver(
                    gogglesTrackerPairs,
                    observer.pairId(),
                    observer.containerId() >= 0);
            PacketDistributor.sendToPlayer(observer.player(), new AdvancedControllerRuntimePayload(
                    getBlockPos(), subLevelId, observer.containerId(),
                    trackerPairs,
                    liveInputs, liveOutputs, graphExecutionPulses));
        }
    }

    // Get the tracker pairs for observer
    static Map<UUID, String> trackerPairsForObserver(
            Map<UUID, String> allPairs, UUID wornPairId, boolean controllerMenuOpen
    ) {
        if (allPairs == null || allPairs.isEmpty()) {
            return Map.of();
        }
        if (controllerMenuOpen) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(allPairs));
        }
        String label = wornPairId == null ? null : allPairs.get(wornPairId);
        return label == null ? Map.of() : Map.of(wornPairId, label);
    }

    // Send the graph runtime data
    public void sendGraphRuntimeDataTo(ServerPlayer player, int containerId) {
        if (player == null || getLevel() == null || getLevel().isClientSide) {
            return;
        }
        long gameTime = getLevel().getGameTime();
        long runtimeRevision = graphRuntime.liveValueRevision();
        String sendKey = player.getUUID() + ":" + containerId;
        DirectRuntimeSendState prev = directRuntimeSendStates.get(sendKey);
        if (prev != null && (prev.runtimeRevision() == runtimeRevision
                || gameTime - prev.gameTime() < GRAPH_RUNTIME_SEND_INTERVAL)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AdvancedControllerRuntimePayload(
                getBlockPos(), SimulatedHelper.getContainingSubLevelId(this), containerId,
                getGogglesTrackerPairLabels(),
                graphLiveValues(graphRuntime.liveInputs()),
                graphLiveValues(graphRuntime.liveOutputs()),
                graphRuntime.executionPulses()));
        directRuntimeSendStates.put(sendKey, new DirectRuntimeSendState(gameTime, runtimeRevision));
    }

    // Get the graph live values
    private static Map<String, AdvancedGraphLiveValue> graphLiveValues(
            Map<String, AdvancedGraphDocument.Value> values) {
        Map<String, AdvancedGraphLiveValue> res = new LinkedHashMap<>(values.size());
        values.forEach((key, val) -> res.put(key, AdvancedGraphLiveValue.from(val)));
        return res;
    }

    // Store the graph runtime observer
    private record GraphRuntimeObserver(ServerPlayer player, int containerId, UUID pairId) {
    }

    // Store direct runtime send state
    private record DirectRuntimeSendState(long gameTime, long runtimeRevision) {
    }

    // Check if this has graph content
    private static boolean hasGraphContent(AdvancedGraphDocument graph) {
        return graph != null && (!graph.nodes().isEmpty() || !graph.edges().isEmpty());
    }

    // Get the draft graph
    public AdvancedGraphDocument getDraftGraph() {
        return draftGraph.copy();
    }

    // Get the active graph
    public AdvancedGraphDocument getActiveGraph() {
        return activeGraph.copy();
    }

    // Get the active graph view
    AdvancedGraphDocument activeGraphView() {
        return activeGraph;
    }

    // Get the active graph revision
    public int getActiveGraphRevision() {
        return activeGraph.revision();
    }

    // Get the graph diagnostics
    public List<AdvancedGraphValidator.Diagnostic> getGraphDiagnostics() {
        return graphDiagnostics;
    }

    // Get the graph versions
    public List<AdvancedGraphVersionHistory.Entry> getGraphVersions() {
        return graphVersions.entries();
    }

    // Save the draft
    public boolean saveDraft(AdvancedGraphDocument graph, int expectedRevision) {
        if (graph == null || expectedRevision != draftGraph.revision()) {
            return false;
        }
        AdvancedGraphPortState.mergePersistentValues(draftGraph, graph);
        AdvancedGraphPortState.mergePersistentValues(activeGraph, graph);
        graphVersions.pushIfChanged(draftGraph, graph);
        draftGraph = graph.copy();
        draftGraph.removeUnusedVariables();
        refreshDataPorts(draftGraph);
        lastGraphObserverSample = Long.MIN_VALUE;
        draftGraph.setRevision(expectedRevision + 1);
        graphRuntime.compile(draftGraph);
        saveControllerManifestNow();
        storeGraphOnInsertedLinker(defaultStoredGraphName());
        setChanged();
        sendData();
        return true;
    }

    // Validate the draft
    public AdvancedGraphValidator.Result validateDraft() {
        AdvancedGraphValidator.Result res = AdvancedGraphValidator.validate(
                draftGraph, isGogglesTrackerAvailable(), isControllerTrackerAvailable());
        graphDiagnostics = res.diagnostics();
        return res;
    }

    // Apply the draft
    public boolean applyDraft() {
        AdvancedGraphValidator.Result res = validateDraft();
        if (!res.valid()) {
            return false;
        }
        Map<String, Double> retainedInputValues = graphInputValues(activeGraph);
        clearGraphTargetWrites(activeGraph);
        clearGraphOutputs(activeGraph);
        clearGraphRoutedState(activeGraph);
        graphRuntime.clear();
        lastGraphChannelValues.clear();
        activeGraph = draftGraph.copy();
        refreshDataPorts(activeGraph);
        removeImplicitPresetBindings(activeGraph);
        applyGraphBindings(activeGraph);
        graphRuntime.compile(activeGraph);
        restoreGraphInputValues(retainedInputValues);
        saveControllerManifestNow();
        setChanged();
        sendData();
        graphRuntime.enqueue("applied");
        return true;
    }

    // Roll back the graph version
    public RollbackResult rollbackGraphVersion(int index, int expectedRevision) {
        if (expectedRevision != draftGraph.revision()) {
            return new RollbackResult(false, false,
                    "Controller changed before the version could be restored", draftGraph.copy());
        }
        AdvancedGraphDocument restored = graphVersions.rollback(index, draftGraph);
        if (restored == null) {
            return new RollbackResult(false, false, "Graph version is no longer available", draftGraph.copy());
        }
        restored.setRevision(draftGraph.revision() + 1);
        draftGraph = restored;
        draftGraph.removeUnusedVariables();
        refreshDataPorts(draftGraph);
        lastGraphObserverSample = Long.MIN_VALUE;
        graphRuntime.compile(draftGraph);
        boolean applied = applyDraft();
        if (!applied) {
            saveControllerManifestNow();
            setChanged();
            sendData();
        }
        return new RollbackResult(true, applied,
                applied ? "Graph version restored and applied" : "Graph version restored but failed validation",
                draftGraph.copy());
    }

    // Store rollback results
    public record RollbackResult(boolean restored, boolean applied, String message,
                                 AdvancedGraphDocument graph) {
    }

    // Get the graph input values
    private Map<String, Double> graphInputValues(AdvancedGraphDocument graph) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String binding : graphRuntime.polledBindings(graph)) {
            values.put(binding, getGraphBindingValue(binding));
        }
        return values;
    }

    // Restore the graph input values
    private void restoreGraphInputValues(Map<String, Double> retainedValues) {
        if (retainedValues.isEmpty()) return;
        Set<String> activeInputs = graphRuntime.polledBindings(activeGraph);
        retainedValues.keySet().removeIf(binding -> !activeInputs.contains(binding));
        if (!retainedValues.isEmpty()) {
            setGraphBindingValues(retainedValues, false);
        }
    }

    // Update the client profiler sample
    public void updateClientProfilerSample(int fps, long frameTimeNanos) {
        clientProfilerFps = Mth.clamp(fps, 0, 1000);
        double sample = Mth.clamp(frameTimeNanos / 1_000_000.0D, 0.0D, 1000.0D);
        clientProfilerFrameTimeMillis = clientProfilerSampleInitialized
                ? AdvancedGraphProfilerMath.exponentialMovingAverage(
                clientProfilerFrameTimeMillis, sample, 0.2D)
                : sample;
        clientProfilerSampleInitialized = true;
        lastClientProfilerSample = level == null ? 0L : level.getGameTime();
        lastGraphObserverSample = Long.MIN_VALUE;
    }

    // Get the graph profiler value
    public double getGraphProfilerValue(String nodeType) {
        return switch (nodeType) {
            case "profiler_fps" -> hasFreshClientProfilerSample() ? clientProfilerFps : 0.0D;
            case "profiler_frametime" -> hasFreshClientProfilerSample() ? clientProfilerFrameTimeMillis : 0.0D;
            case "profiler_mspt" -> {
                var server = level == null ? null : level.getServer();
                yield server == null ? 0.0D : server.getAverageTickTimeNanos() / 1_000_000.0D;
            }
            case "profiler_tps" -> {
                var server = level == null ? null : level.getServer();
                if (server == null) {
                    yield 0.0D;
                }
                yield AdvancedGraphProfilerMath.ticksPerSecond(
                        server.getAverageTickTimeNanos() / 1_000_000.0D,
                        server.tickRateManager().tickrate());
            }
            default -> 0.0D;
        };
    }

    // Check if this has fresh client profiler sample
    private boolean hasFreshClientProfilerSample() {
        if (lastClientProfilerSample == Long.MIN_VALUE) {
            return false;
        }
        long gameTime = level == null ? 0L : level.getGameTime();
        return gameTime - lastClientProfilerSample <= CLIENT_PROFILER_SAMPLE_TIMEOUT_TICKS;
    }

    // Store shared graph share results
    public record SharedGraphShareResult(boolean saved, boolean conflict, String message) {
        // Initialize the shared graph share result
        public SharedGraphShareResult {
            message = message == null ? "" : message;
        }
    }

    // Get the share graph to inserted linker
    public SharedGraphShareResult shareGraphToInsertedLinker(AdvancedGraphDocument graph, String requestedName,
                                                              ControllerManifestStore.SharedGraphSaveMode saveMode) {
        if (getLevel() == null || getLevel().isClientSide) {
            return new SharedGraphShareResult(false, false, "");
        }
        if (requestedName == null || requestedName.isBlank()) {
            return new SharedGraphShareResult(false, false, "A graph name is required");
        }
        AdvancedGraphDocument graphToStore = graph == null ? draftGraph.copy() : graph.copy();
        graphToStore.removeUnusedVariables();
        CompoundTag sharedRoot = ContraptionNetworkLinkerData.sharedRootWithGraph(requestedName, graphToStore);
        ControllerManifestStore.SharedGraphSaveResult res =
                ControllerManifestStore.saveSharedGraph(requestedName, sharedRoot, saveMode);
        if (res.status() == ControllerManifestStore.SharedGraphSaveStatus.EXISTS) {
            return new SharedGraphShareResult(false, true,
                    "A shared graph named " + requestedName.trim() + " already exists");
        }
        if (!res.saved()) {
            return new SharedGraphShareResult(false, false, "Failed to save shared graph");
        }
        setChanged();
        sendData();
        return new SharedGraphShareResult(true, false, "Shared graph saved as " + res.id());
    }

    // Load the shared graph into inserted linker
    public String loadSharedGraphIntoInsertedLinker(String manifestId) {
        if (getLevel() == null || getLevel().isClientSide) {
            return "";
        }
        ControllerManifestStore.ManifestSnapshot snapshot = ControllerManifestStore.loadSharedGraph(manifestId);
        if (snapshot == null || snapshot.linkerData().isEmpty()) {
            return "Shared graph not found";
        }
        List<ContraptionNetworkLinkerData.StoredGraph> storedGraphs =
                ContraptionNetworkLinkerData.readStoredGraphs(snapshot.linkerData());
        if (storedGraphs.isEmpty()) {
            return "Shared graph not found";
        }
        replaceGraphFromStoredLinker(storedGraphs.getFirst(), true);
        refreshDataPorts(draftGraph);
        refreshDataPorts(activeGraph);
        applyGraphBindings(activeGraph);
        graphRuntime.compile(activeGraph);
        graphOwnedBindingChannelsVerified = true;
        queueGraphBindingSync();
        saveControllerManifestNow();
        setChanged();
        sendData();
        graphRuntime.enqueue("shared_graph_import");
        return "Loaded shared graph " + snapshot.id();
    }

    // Handle the destroyed event
    @Override
    public void onDestroyed() {
        if (isAssemblyTransferPending()) {
            shipControlRuntime.suspendForAssemblyTransfer();
            return;
        }
        releaseAccDisplays();
        shipStockNetworkCache.clear();
        shipControlRuntime.close();
        if (getLevel() != null && !getLevel().isClientSide) {
            clearGraphTargetWrites(activeGraph);
            clearGraphOutputs(activeGraph);
            clearGraphRoutedState(activeGraph);
            resetGraphBindingsBatch();
            graphRuntime.clear();
            lastGraphChannelValues.clear();
            graphLiveInputs = new LinkedHashMap<>();
            graphLiveOutputs = new LinkedHashMap<>();
            graphExecutionPulses = new LinkedHashMap<>();
        }
        super.onDestroyed();
    }

    // Remove the advanced contraption controller
    @Override
    public void remove() {
        releaseAccDisplays();
        AccDisplayControllerRegistry.unregister(this);
        AdvancedControllerNamedEventBus.unregister(this);
        shipStockNetworkCache.clear();
        shipControlRuntime.close();
        super.remove();
    }

    // Handle the chunk unloaded event
    @Override
    public void onChunkUnloaded() {
        releaseAccDisplays();
        AccDisplayControllerRegistry.unregister(this);
        AdvancedControllerNamedEventBus.unregister(this);
        shipStockNetworkCache.clear();
        shippingScheduleRuntime.suspendForChunkUnload();
        shipControlRuntime.suspendForChunkUnload();
        super.onChunkUnloaded();
    }

    // Handle the controller manifest reloaded event
    @Override
    protected void onControllerManifestReloaded() {
        refreshGraphRuntime(true);
    }

    // Queue the graph binding sync
    private void queueGraphBindingSync() {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        if (!graphRuntime.graphOwnedBindings(activeGraph).isEmpty()) {
            graphOwnedBindingSyncQueued = true;
        }
    }

    // Validate the graph bindings
    private void checkGraphBindings() {
        if (graphOwnedBindingChannelsVerified || getLevel() == null || getLevel().isClientSide) {
            return;
        }
        graphOwnedBindingChannelsVerified = true;
        if (graphRuntime.graphOwnedBindings(activeGraph).isEmpty()) {
            return;
        }
        applyGraphBindings(activeGraph);
        graphRuntime.compile(activeGraph);
        graphOwnedBindingSyncQueued = true;
    }

    // Flush the graph binding sync
    private void flushGraphBindingSync() {
        if (!graphOwnedBindingSyncQueued) {
            return;
        }
        graphOwnedBindingSyncQueued = false;
        setChanged();
        sendData();
    }

    // Store the graph on the inserted linker
    private boolean storeGraphOnInsertedLinker(String name) {
        ItemStack linker = getStoredLinker();
        if (linker.isEmpty()) {
            return false;
        }
        return ContraptionNetworkLinkerData.cloneControllerDraftGraph(
                linker, controllerManifestId(), name);
    }

    // Create the default stored graph name
    private String defaultStoredGraphName() {
        String name = getCustomName() == null ? "" : getCustomName();
        return name.isBlank() ? "Advanced Controller Graph" : name;
    }

    // Apply the graph bindings
    private void applyGraphBindings(AdvancedGraphDocument graph) {
        if (level != null && !level.isClientSide) {
            ControllerRedstoneCompat.clearSource(
                    level, worldPosition.asLong() + ":graph");
        }
        Map<String, GraphRouteConfig> graphOwnedRoutes = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            clearStaleOutputRoute(node);
            String firstId = node.data().getString("FrequencyFirst");
            String secondId = node.data().getString("FrequencySecond");
            ItemStack first = frequencyStack(node, "FrequencyFirst");
            ItemStack second = frequencyStack(node, "FrequencySecond");
            String explicitBinding = node.data().getString("BindingId");
            String binding = explicitBinding;
            if (binding.isBlank()) binding = node.data().getString("RouteBindingId");
            if (binding.isBlank()) binding = node.data().getString("Channel");
            boolean routedNode = requiresGraphRoute(node.type());
            boolean graphOwnedRoute = routedNode
                    && (node.data().getBoolean("GraphOwnedBinding")
                    || node.data().getString("RouteBindingId").startsWith("graph_")
                    || explicitBinding.startsWith("graph_"));
            if ("wireless_frequency_output".equals(node.type()) && explicitBinding.isBlank()
                    && !first.isEmpty() && !second.isEmpty()) {
                binding = wirelessOutputRoute(firstId, secondId);
                node.data().remove("BindingId");
                node.data().putString("RouteBindingId", binding);
                node.data().putString("BindingLabel", "Redstone Link Output");
                node.data().putBoolean("GraphOwnedBinding", true);
                graphOwnedRoute = true;
            }
            if ("local_redstone_output".equals(node.type()) && explicitBinding.isBlank()
                    && graphOwnedRoute) {
                binding = automaticGraphRoute(node);
                node.data().putString("RouteBindingId", binding);
                node.data().putString("BindingLabel", "Local Redstone Output");
            }
            if (binding.isBlank() && routedNode) {
                binding = automaticGraphRoute(node);
                node.data().putString("RouteBindingId", binding);
                node.data().putString("BindingLabel", "Graph Route");
                node.data().putBoolean("GraphOwnedBinding", true);
                graphOwnedRoute = true;
            }
            if (binding.isBlank()) continue;
            ControllerDiscoveryNode targetNode = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
            ControllerDiscoveryNode currentTarget = findCurrentTarget(targetNode);
            if (currentTarget != null) targetNode = currentTarget;
            Direction configuredFace = configuredDirection(node, "face");
            ControllerDirectTargetReference target = targetNode == null ? null
                    : ContraptionNetworkLinkerData.directTargetForFace(targetNode, configuredFace);
            boolean input = node.type().endsWith("_input");
            boolean output = node.type().endsWith("_output");
            Direction localSide = configuredDirection(node, "face");
            if (graphOwnedRoute) {
                graphOwnedRoutes.computeIfAbsent(binding, GraphRouteConfig::new)
                        .merge(node, input, output, localSide, first, second, target);
            } else {
                applyStandardGraphBinding(binding, node.type(), input, output, localSide, target);
                if ("wireless_frequency_input".equals(node.type())) {
                    setChannelInputFrequency(binding, first, second);
                } else if ("wireless_frequency_output".equals(node.type())) {
                    setChannelFrequency(binding, first, second);
                }
            }
        }
        for (GraphRouteConfig route : graphOwnedRoutes.values()) {
            applyCustomKeyEntry(route.bindingId, route.keyCode, -1, route.label,
                    AnalogueChannelMode.DIRECT, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0,
                    route.localSide,
                    route.first, route.second,
                    route.inputFirst, route.inputSecond,
                    route.directTarget, route.inputTarget, "none",
                    ControllerBindingOwner.GRAPH);
        }
    }

    // Clear the stale output route
    private static void clearStaleOutputRoute(AdvancedGraphDocument.Node node) {
        if (node == null || (!"direct_target_output".equals(node.type()) && !"linker_face_output".equals(node.type()))) {
            return;
        }
        if (!node.data().getString("BindingId").isBlank()) {
            return;
        }
        String routeBinding = node.data().getString("RouteBindingId");
        if (routeBinding.isBlank() || !isGraphOwnedBinding(node, routeBinding)) {
            return;
        }
        node.data().remove("RouteBindingId");
        node.data().remove("GraphOwnedBinding");
        node.data().remove("KeyCode");
        if ("Graph Route".equals(node.data().getString("BindingLabel"))) {
            node.data().remove("BindingLabel");
        }
    }

    // Apply the standard graph binding
    private void applyStandardGraphBinding(String binding, String nodeType, boolean input, boolean output,
                                           @Nullable Direction localSide,
                                           @Nullable ControllerDirectTargetReference target) {
        if (binding == null || binding.isBlank() || getChannel(binding) == null) {
            return;
        }
        if (input && target != null) {
            setChannelInputTarget(binding, target);
        } else if (output && target != null) {
            setChannelDirectTarget(binding, target);
        }
        if ("local_redstone_output".equals(nodeType)) {
            setLocalOutputSide(binding, localSide);
        }
    }

    // Store graph route settings
    private static final class GraphRouteConfig {
        // Binding id
        private final String bindingId;
        // Current key code
        private int keyCode = -1;
        // Current display label
        private String label = "Graph Route";
        // Local side
        private @Nullable Direction localSide;
        // Current first entry
        private ItemStack first = ItemStack.EMPTY;
        // Current second entry
        private ItemStack second = ItemStack.EMPTY;
        // Input first
        private ItemStack inputFirst = ItemStack.EMPTY;
        // Input second
        private ItemStack inputSecond = ItemStack.EMPTY;
        // Current direct target
        private @Nullable ControllerDirectTargetReference directTarget;
        // Input target
        private @Nullable ControllerDirectTargetReference inputTarget;

        // Initialize the graph route config
        private GraphRouteConfig(String bindingId) {
            this.bindingId = bindingId;
        }

        // Merge the graph route config
        private void merge(AdvancedGraphDocument.Node node, boolean input, boolean output,
                           @Nullable Direction localSide, ItemStack first, ItemStack second,
                           @Nullable ControllerDirectTargetReference target) {
            if (node.data().contains("KeyCode", Tag.TAG_INT)) {
                keyCode = node.data().getInt("KeyCode");
            }
            String bindingLabel = node.data().getString("BindingLabel");
            if (!bindingLabel.isBlank()) {
                label = bindingLabel;
            } else if (!node.label().isBlank()) {
                label = node.label();
            }
            switch (node.type()) {
                case "wireless_frequency_input" -> {
                    inputFirst = first;
                    inputSecond = second;
                }
                case "wireless_frequency_output" -> {
                    this.first = first;
                    this.second = second;
                }
                case "local_redstone_output" -> this.localSide = localSide;
                default -> {
                    if (input && target != null) {
                        inputTarget = target;
                    } else if (output && target != null) {
                        directTarget = target;
                    }
                }
            }
        }
    }

    // Check if this requires graph route
    private static boolean requiresGraphRoute(String nodeType) {
        return switch (nodeType) {
            case "local_redstone_input", "local_redstone_output",
                 "wireless_frequency_input", "wireless_frequency_output",
                 "discovered_target_input", "linker_face_input",
                 "controller_channel_input", "controller_channel_output" -> true;
            default -> false;
        };
    }

    // Get the configured direction
    private static Direction configuredDirection(AdvancedGraphDocument.Node node, String port) {
        String property = Character.toUpperCase(port.charAt(0)) + port.substring(1);
        CompoundTag defaultTag = node.data().getCompound("Defaults").getCompound(port);
        Direction dir = Direction.byName(defaultTag.getCompound("Payload").getString("Value"));
        if (dir != null) return dir;
        dir = Direction.byName(node.data().getString(property));
        if (dir != null) return dir;
        return node.type().startsWith("linker_face") || node.type().startsWith("local_redstone")
                ? Direction.NORTH : null;
    }

    // Clear the graph outputs
    private void clearGraphOutputs(AdvancedGraphDocument graph) {
        Map<String, Double> resetValues = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (!node.type().endsWith("_output")) continue;
            String binding = node.data().getString("BindingId");
            if (binding.isBlank()) binding = node.data().getString("RouteBindingId");
            if (binding.isBlank()) binding = node.data().getString("Channel");
            if (!binding.isBlank()) resetValues.put(binding, 0.0);
        }
        if (!resetValues.isEmpty()) {
            setGraphBindingValues(resetValues, false);
        }
    }

    // Clear the graph target writes
    private void clearGraphTargetWrites(AdvancedGraphDocument graph) {
        if (graph == null || graph.nodes().isEmpty()) {
            return;
        }
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node == null || (!"set_block_data".equals(node.type())
                    && !"direct_target_output".equals(node.type())
                    && !"linker_face_output".equals(node.type()))) {
                continue;
            }
            Set<String> resetPorts = graphTargetResetPorts(graph, node);
            if (resetPorts.isEmpty()) {
                continue;
            }
            setGraphTargetData(node, resetPorts, port -> AdvancedGraphDocument.Value.number(0.0));
        }
    }

    // Get the graph target reset ports
    static Set<String> graphTargetResetPorts(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node) {
        if (graph == null || node == null) {
            return Set.of();
        }
        Set<String> resetPorts = new LinkedHashSet<>();
        boolean aeroworksController = isAeroworksControllerGraphTarget(node);
        if (!"set_block_data".equals(node.type())) {
            resetPorts.add("direct_signal");
            resetPorts.addAll(DIRECT_AXIS_CHANNELS);
        }
        for (AdvancedGraphDocument.Edge edge : graph.edges()) {
            if (edge != null && node.id().equals(edge.toNode())
                    && (isResettableGraphTargetPort(edge.toPort())
                    || aeroworksController && isAeroworksControllerPort(edge.toPort()))) {
                resetPorts.add(edge.toPort());
            }
        }
        for (String port : node.data().getCompound("Defaults").getAllKeys()) {
            if (isResettableGraphTargetPort(port)
                    || aeroworksController && isAeroworksControllerPort(port)) {
                resetPorts.add(port);
            }
        }
        return resetPorts;
    }

    // Check if this is a resettable graph target port
    private static boolean isResettableGraphTargetPort(String port) {
        return "direct_signal".equals(port) || DIRECT_AXIS_CHANNELS.contains(port);
    }

    // Check if this is an aeroworks controller graph target
    private static boolean isAeroworksControllerGraphTarget(AdvancedGraphDocument.Node node) {
        ControllerDiscoveryNode target = node == null ? null
                : ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        return target != null && AeroworksControllerCompat.CONTROL_DESK.equalsIgnoreCase(target.blockId());
    }

    // Check if this is an aeroworks controller port
    private static boolean isAeroworksControllerPort(String port) {
        return port != null && (port.startsWith("controller_socket_")
                || port.startsWith("controller_part_"));
    }

    // Clear the graph routed state
    private void clearGraphRoutedState(AdvancedGraphDocument graph) {
        Set<String> graphOwnedCustomBindings = new HashSet<>();
        for (CustomKeyEntry entry : getCustomKeyEntries()) {
            if (entry != null && (entry.owner == ControllerBindingOwner.GRAPH
                    || entry.id().startsWith("graph_"))) {
                graphOwnedCustomBindings.add(entry.id());
            }
        }
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            String binding = node.data().getString("BindingId");
            if (binding.isBlank()) binding = node.data().getString("RouteBindingId");
            if (binding.isBlank()) binding = node.data().getString("Channel");
            if (binding.isBlank()) {
                continue;
            }
            if (isGraphOwnedBinding(node, binding)) {
                graphOwnedCustomBindings.add(binding);
                continue;
            }
            if (getChannel(binding) == null) {
                continue;
            }
            switch (node.type()) {
                case "discovered_target_input", "linker_face_input" -> setChannelInputTarget(binding, null);
                case "direct_target_output", "linker_face_output" -> setChannelDirectTarget(binding, null);
                case "wireless_frequency_input" -> setChannelInputFrequency(binding, ItemStack.EMPTY, ItemStack.EMPTY);
                case "wireless_frequency_output" -> setChannelFrequency(binding, ItemStack.EMPTY, ItemStack.EMPTY);
                case "local_redstone_output" -> setLocalOutputSide(binding, null);
                default -> {
                }
            }
        }
        for (String binding : graphOwnedCustomBindings) {
            removeCustomKeyEntry(binding);
        }
    }

    // Check if this is a graph owned binding
    private static boolean isGraphOwnedBinding(AdvancedGraphDocument.Node node, String binding) {
        return node.data().getBoolean("GraphOwnedBinding")
                || binding.startsWith("graph_")
                || node.data().getString("RouteBindingId").startsWith("graph_");
    }

    // Get the wireless output route
    private static String wirelessOutputRoute(String first, String second) {
        UUID route = UUID.nameUUIDFromBytes((first + "\u0000" + second).getBytes(StandardCharsets.UTF_8));
        return "graph_wireless_" + route.toString().replace('-', '_');
    }

    // Get the automatic graph route
    private static String automaticGraphRoute(AdvancedGraphDocument.Node node) {
        if (node != null && "local_redstone_output".equals(node.type())) {
            Direction face = configuredDirection(node, "face");
            if (face != null) {
                return "graph_local_output_" + face.getName();
            }
        }
        return "graph_" + (node == null ? "route"
                : node.id().replaceAll("[^A-Za-z0-9_]", "_"));
    }

    // Get the frequency stack
    private ItemStack frequencyStack(AdvancedGraphDocument.Node node, String property) {
        if (node != null && getLevel() != null && node.data().contains(property + "Stack", Tag.TAG_COMPOUND)) {
            ItemStack stored = ItemStack.parseOptional(getLevel().registryAccess(),
                    node.data().getCompound(property + "Stack"));
            if (!stored.isEmpty()) {
                stored.setCount(1);
                return stored;
            }
        }
        return frequencyStackFromId(node == null ? "" : node.data().getString(property));
    }

    // Get the frequency stack from id
    private static ItemStack frequencyStackFromId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(location).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // Select the template
    public void selectTemplate(String templateId) {
        AdvancedGraphDocument template = AdvancedGraphTemplates.create(templateId);
        graphVersions.pushIfChanged(draftGraph, template);
        draftGraph = template;
        draftGraph.setRevision(1);
        applyDraft();
    }

    // Trigger the graph event
    public void triggerGraphEvent(String eventId) {
        triggerGraphEvent(eventId, null);
    }

    // Trigger the graph event
    public void triggerGraphEvent(String eventId, @Nullable UUID triggeringPlayerId) {
        graphRuntime.enqueue(eventId, triggeringPlayerId);
    }

    // Try to trigger the graph event
    public boolean tryTriggerGraphEvent(
            String eventId, @Nullable UUID triggeringPlayerId) {
        return graphRuntime.tryEnqueue(eventId, triggeringPlayerId);
    }

    // Record controller use without counting the graph editor
    public void handlePhysicalInteraction(ServerPlayer player, boolean active,
                                          boolean remote, String keyPressed) {
        if (player == null || getLevel() == null || getLevel().isClientSide) {
            return;
        }
        if (active) {
            physicalInteractionParticipants.add(player.getUUID());
        } else {
            physicalInteractionParticipants.remove(player.getUUID());
        }
        graphRuntime.enqueuePhysicalInteraction(player, active, remote, keyPressed);
    }

    // Check if this has physical interaction participant
    public boolean hasPhysicalInteractionParticipant(UUID playerId) {
        return playerId != null && physicalInteractionParticipants.contains(playerId);
    }

    // Publish the named controller event
    public void publishNamedControllerEvent(String name, AdvancedGraphDocument.Value data) {
        publishNamedControllerEvent(name, data, 0);
    }

    // Publish the named controller event
    public void publishNamedControllerEvent(String name, AdvancedGraphDocument.Value data, int maximumDistance) {
        AdvancedControllerNamedEventBus.publish(this, name,
                data == null ? AdvancedGraphDocument.Value.number(0) : data, maximumDistance);
    }

    // Get the named controller event position
    public Vec3 namedControllerEventPosition() {
        return SimulatedHelper.toGlobalWorldPosition(this, Vec3.atCenterOf(getBlockPos()));
    }

    // Receive the named controller event
    public void receiveNamedControllerEvent(String name, AdvancedGraphDocument.Value data) {
        graphRuntime.enqueueNamedControllerEvent(name,
                data == null ? AdvancedGraphDocument.Value.number(0) : data);
    }

    // Check if the goggles tracker is available
    public boolean isGogglesTrackerAvailable() {
        return !gogglesTrackerPairs.isEmpty();
    }

    // Check if the controller tracker is available
    public boolean isControllerTrackerAvailable() {
        return false;
    }

    // Get the goggles tracker pairs
    public List<GogglesTrackerPair> getGogglesTrackerPairs() {
        List<GogglesTrackerPair> pairs = new ArrayList<>(gogglesTrackerPairs.size());
        gogglesTrackerPairs.forEach((id, label) -> pairs.add(new GogglesTrackerPair(id, label)));
        return List.copyOf(pairs);
    }

    // Get the goggles tracker pair labels
    public Map<UUID, String> getGogglesTrackerPairLabels() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(gogglesTrackerPairs));
    }

    // Check if the player has goggles pair
    public static boolean playerHasGogglesPair(Player player, UUID pairId) {
        return pairId != null && pairId.equals(ControllerRuntimeObserver.gogglesPairId(player));
    }

    // Check if the controller data has goggles tracker pair
    public static boolean controllerDataHasGogglesTrackerPair(CompoundTag controllerData, UUID pairId) {
        if (controllerData == null || pairId == null) {
            return false;
        }
        ListTag trackerPairs = controllerData.getList(GOGGLES_TRACKER_PAIRS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < trackerPairs.size(); idx++) {
            CompoundTag pair = trackerPairs.getCompound(idx);
            if (pair.hasUUID("Id") && pairId.equals(pair.getUUID("Id"))) {
                return true;
            }
        }
        return false;
    }

    // Get the next goggles tracker pair label
    public String nextGogglesTrackerPairLabel() {
        int idx = 1;
        Set<String> labels = new HashSet<>();
        gogglesTrackerPairs.values().forEach(label -> labels.add(label.toLowerCase(Locale.ROOT)));
        while (labels.contains(("Goggles " + idx).toLowerCase(Locale.ROOT))) {
            idx++;
        }
        return "Goggles " + idx;
    }

    // Register the goggles tracker pair
    public void registerGogglesTrackerPair(UUID pairId, String pairLabel) {
        if (pairId == null) {
            return;
        }
        String normalizedLabel = pairLabel == null || pairLabel.isBlank()
                ? nextGogglesTrackerPairLabel() : pairLabel.trim();
        if (Objects.equals(gogglesTrackerPairs.put(pairId, normalizedLabel), normalizedLabel)) {
            return;
        }
        lastGogglesTrackingSample = Long.MIN_VALUE;
        gogglesTrackingSnapshots.clear();
        setChanged();
        sendData();
        sendGraphRuntimeData(graphRuntimeObservers());
    }

    // Get the goggles tracking value
    public AdvancedGraphDocument.Value getGogglesTrackingValue(String selectedPair, String port) {
        GogglesTrackingSnapshot tracking = gogglesTrackingSnapshot(selectedPair);
        return switch (port == null ? "" : port) {
            case "available" -> AdvancedGraphDocument.Value.bool(tracking.available());
            case "holding_player", "is_player" -> AdvancedGraphDocument.Value.bool(
                    "player".equals(tracking.wearerType()));
            case "is_mannequin" -> AdvancedGraphDocument.Value.bool(
                    "mannequin".equals(tracking.wearerType()));
            case "is_armor_stand" -> AdvancedGraphDocument.Value.bool(
                    "armor_stand".equals(tracking.wearerType()));
            case "on_lectern" -> AdvancedGraphDocument.Value.bool(false);
            case "x" -> AdvancedGraphDocument.Value.number(tracking.position().x);
            case "y" -> AdvancedGraphDocument.Value.number(tracking.position().y);
            case "z" -> AdvancedGraphDocument.Value.number(tracking.position().z);
            case "dimension" -> AdvancedGraphDocument.Value.string(tracking.dimension());
            case "sub_level" -> AdvancedGraphDocument.Value.string(tracking.subLevel());
            case "wearer_type" -> AdvancedGraphDocument.Value.string(tracking.wearerType());
            case "wearer_name", "player_name" -> AdvancedGraphDocument.Value.string(tracking.wearerName());
            case "wearer_uuid", "player_uuid" -> AdvancedGraphDocument.Value.string(tracking.wearerUuid());
            case "player_facing" -> tracking.playerFacing();
            case "looking_at" -> tracking.lookingAt();
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Get the goggles tracking snapshot
    private GogglesTrackingSnapshot gogglesTrackingSnapshot(String selectedPair) {
        UUID pairId = selectedGogglesTrackerPair(selectedPair);
        if (pairId == null || getLevel() == null || getLevel().isClientSide || getLevel().getServer() == null) {
            return GogglesTrackingSnapshot.unavailable();
        }
        long gameTime = getLevel().getGameTime();
        if (lastGogglesTrackingSample != gameTime) {
            lastGogglesTrackingSample = gameTime;
            gogglesTrackingSnapshots.clear();
        }
        return gogglesTrackingSnapshots.computeIfAbsent(pairId, this::findGogglesTrackingSnapshot);
    }

    // Get the selected goggles tracker pair
    private UUID selectedGogglesTrackerPair(String selectedPair) {
        if (selectedPair != null && !selectedPair.isBlank()) {
            try {
                UUID requested = UUID.fromString(selectedPair);
                if (gogglesTrackerPairs.containsKey(requested)) {
                    return requested;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return gogglesTrackerPairs.keySet().stream().findFirst().orElse(null);
    }

    // Find the goggles tracking snapshot
    private GogglesTrackingSnapshot findGogglesTrackingSnapshot(UUID pairId) {
        LivingEntity wearer = ControllerRuntimeObserver.findGogglesWearer(getLevel().getServer(), pairId);
        if (wearer == null) {
            return GogglesTrackingSnapshot.unavailable();
        }
        Vector3d feet = Sable.HELPER.getFeetPos(wearer, 0.0F);
        Object subLevel = SimulatedHelper.getEntityTrackingSubLevel(wearer);
        UUID subLevelId = SimulatedHelper.getSubLevelId(subLevel);
        String wearerType = wearer instanceof Player ? "player"
                : wearer instanceof PlayerMannequinEntity ? "mannequin"
                : wearer instanceof ArmorStand ? "armor_stand" : "entity";
        Vec3 facing = wearer.getLookAngle();
        AdvancedGraphDocument.Value lookingAt = emptyTrackingVector();
        if (wearer instanceof Player player) {
            HitResult hit = player.pick(Math.max(player.blockInteractionRange(), 8.0D), 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                lookingAt = trackingVector(pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return new GogglesTrackingSnapshot(true, new Vec3(feet.x, feet.y, feet.z),
                wearer.level().dimension().location().toString(),
                subLevelId == null ? "" : subLevelId.toString(), wearerType,
                wearer.getDisplayName().getString(), wearer.getUUID().toString(),
                trackingVector(facing.x, facing.y, facing.z), lookingAt);
    }

    // Get the portable tracking value
    public AdvancedGraphDocument.Value getPortableTrackingValue(String port) {
        return switch (port == null ? "" : port) {
            case "available", "holding_player", "on_lectern" -> AdvancedGraphDocument.Value.bool(false);
            case "dimension", "sub_level", "player_name", "player_uuid" ->
                    AdvancedGraphDocument.Value.string("");
            case "player_facing", "looking_at" -> emptyTrackingVector();
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Get the empty tracking vector
    private static AdvancedGraphDocument.Value emptyTrackingVector() {
        return trackingVector(0.0D, 0.0D, 0.0D);
    }

    // Get the tracking vector
    private static AdvancedGraphDocument.Value trackingVector(double x, double y, double z) {
        CompoundTag values = new CompoundTag();
        values.put("x", trackingNumber(x));
        values.put("y", trackingNumber(y));
        values.put("z", trackingNumber(z));
        return AdvancedGraphDocument.Value.map(values);
    }

    // Get the tracking number
    private static CompoundTag trackingNumber(double num) {
        CompoundTag val = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        val.putString("Type", "number");
        payload.putDouble("Value", num);
        val.put("Payload", payload);
        return val;
    }

    // Store the goggles tracker pair
    public record GogglesTrackerPair(UUID id, String label) {
        // Initialize the goggles tracker pair
        public GogglesTrackerPair {
            label = label == null || label.isBlank() ? "Goggles" : label;
        }
    }

    // Store the goggles tracking snapshot
    private record GogglesTrackingSnapshot(boolean available, Vec3 position, String dimension,
                                           String subLevel, String wearerType, String wearerName,
                                           String wearerUuid,
                                           AdvancedGraphDocument.Value playerFacing,
                                           AdvancedGraphDocument.Value lookingAt) {
        // Create an unavailable goggles tracking snapshot
        private static GogglesTrackingSnapshot unavailable() {
            return new GogglesTrackingSnapshot(false, Vec3.ZERO, "", "", "", "", "",
                    emptyTrackingVector(), emptyTrackingVector());
        }
    }

    // Get the configured mouse inputs
    public Set<String> getConfiguredMouseInputs() {
        Set<String> configured = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
            if (!"mouse_input".equals(node.type())) {
                continue;
            }
            String input = normalizeMouseInput(node.data().getString("MouseInput"));
            if (!input.isBlank()) configured.add(input);
        }
        return Collections.unmodifiableSet(configured);
    }

    // Collect the additional schematic sublevel ids
    @Override
    protected void collectAdditionalSchematicSubLevelIds(Set<UUID> subLevelIds) {
        ShipControlMap map = storedShipControlMap();
        UUID mapId = shipControlRuntime.mapId();
        if (cachedDependencyActiveGraph != activeGraph
                || cachedDependencyDraftGraph != draftGraph
                || cachedDependencyActiveRevision != activeGraph.revision()
                || cachedDependencyDraftRevision != draftGraph.revision()
                || cachedDependencyShipControlMap != map
                || !Objects.equals(cachedDependencyShipControlMapId, mapId)) {
            Set<UUID> dependencies = new LinkedHashSet<>();
            collectGraphSubLevelIds(activeGraph, dependencies);
            collectGraphSubLevelIds(draftGraph, dependencies);
            if (map != null) {
                dependencies.add(map.rootSubLevelId());
                for (ShipControlMap.PropulsionUnit unit : map.units()) {
                    dependencies.add(unit.subLevelId());
                }
                for (ShipControlMap.BearingUnit bearing : map.bearings()) {
                    dependencies.add(bearing.hostSubLevelId());
                    dependencies.addAll(bearing.childSubLevelIds());
                    for (ShipControlMap.BearingPose pose : bearing.poses()) {
                        for (ShipControlMap.AerodynamicSurface surface : pose.aerodynamicSurfaces()) {
                            dependencies.add(surface.subLevelId());
                        }
                    }
                }
            }
            cachedDependencyActiveGraph = activeGraph;
            cachedDependencyDraftGraph = draftGraph;
            cachedDependencyActiveRevision = activeGraph.revision();
            cachedDependencyDraftRevision = draftGraph.revision();
            cachedDependencyShipControlMap = map;
            cachedDependencyShipControlMapId = mapId;
            cachedAdditionalSchematicSubLevelIds = Set.copyOf(dependencies);
        }
        subLevelIds.addAll(cachedAdditionalSchematicSubLevelIds);
    }

    // Collect the graph sublevel ids
    private static void collectGraphSubLevelIds(AdvancedGraphDocument graph, Set<UUID> subLevelIds) {
        if (graph == null) return;
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
            if (target != null && target.subLevelId() != null) subLevelIds.add(target.subLevelId());
        }
    }

    // Get the stored ship control map
    private @Nullable ShipControlMap storedShipControlMap() {
        return storedShipControlMap(false);
    }

    // Get the stored ship control map
    private @Nullable ShipControlMap storedShipControlMap(boolean retryMissing) {
        ShipControlMap runtimeMap = shipControlRuntime.storedMap();
        if (runtimeMap != null) {
            cachedStoredShipControlMapId = runtimeMap.id();
            cachedStoredShipControlMap = runtimeMap;
            cachedStoredShipControlMapAttempted = true;
            return runtimeMap;
        }
        UUID mapId = shipControlRuntime.mapId();
        if (mapId == null || getLevel() == null) {
            return null;
        }
        if (!Objects.equals(cachedStoredShipControlMapId, mapId)) {
            cachedStoredShipControlMapId = mapId;
            cachedStoredShipControlMap = null;
            cachedStoredShipControlMapAttempted = false;
        }
        if (!cachedStoredShipControlMapAttempted
                || retryMissing && cachedStoredShipControlMap == null) {
            cachedStoredShipControlMapAttempted = true;
            cachedStoredShipControlMap = ShipControlMapStore.load(getLevel(), mapId);
        }
        return cachedStoredShipControlMap;
    }

    // Get the local ship control map
    @Nullable ShipControlMap localShipControlMap() {
        return storedShipControlMap();
    }

    // Get the local ship control map
    @Nullable ShipControlMap localShipControlMap(boolean retryMissing) {
        return storedShipControlMap(retryMissing);
    }

    // Check if this can share local ship control map
    boolean canShareLocalShipControlMap() {
        return shipControlRuntime.canShareStoredMap();
    }

    // Set the ship control assembly aggregator
    void setShipControlAssemblyAggregator(boolean aggregator) {
        shipControlRuntime.setAssemblyAggregator(aggregator);
    }

    // Get the notation SCM model
    public NotationScmModel getNotationScmModel() {
        return NotationScmModel.from(storedShipControlMap());
    }

    // Get the notation draft owner id
    public String notationDraftOwnerId() {
        String manifestId = controllerManifestId();
        if (manifestId.isBlank() && getLevel() != null && !getLevel().isClientSide) {
            saveControllerManifestNow();
            manifestId = controllerManifestId();
        }
        if (!manifestId.isBlank()) {
            return manifestId;
        }
        String dimension = getLevel() == null ? "unknown" : getLevel().dimension().location().toString();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        return dimension + "|" + (subLevelId == null ? "world" : subLevelId) + "|" + getBlockPos().asLong();
    }

    // Get the mouse input value
    public double getMouseInputValue(String input) {
        return mouseInputValues.getOrDefault(normalizeMouseInput(input), 0.0D);
    }

    // Check if the mouse input is active
    public boolean isMouseInputActive(String input) {
        return activeMouseInputs.contains(normalizeMouseInput(input));
    }

    // Handle the mouse input
    public void handleMouseInput(String input, double value, boolean active) {
        String normalized = normalizeMouseInput(input);
        if (normalized.isBlank() || !getConfiguredMouseInputs().contains(normalized)) {
            return;
        }
        double normalizedValue = Mth.clamp(value, -1.0D, 1.0D);
        if (normalized.endsWith("click")) {
            normalizedValue = active ? 1.0D : 0.0D;
        }
        mouseInputValues.put(normalized, normalizedValue);
        if (normalized.startsWith("mouse_") && Math.abs(normalizedValue) > 1.0E-4D && getLevel() != null) {
            mouseInputLastMovementTicks.put(normalized, getLevel().getGameTime());
        }
        boolean wasActive = activeMouseInputs.contains(normalized);
        if (active) activeMouseInputs.add(normalized);
        else activeMouseInputs.remove(normalized);
        if (wasActive != active) {
            graphRuntime.enqueue("mouse:" + normalized + ":" + (active ? "active" : "inactive"));
        }
        if (normalized.startsWith("scroll_")) transientMouseInputs.add(normalized);
    }

    // Apply the client mouse input
    public void applyClientMouseInput(String input, double value, boolean active) {
        String normalized = normalizeMouseInput(input);
        if (normalized.isBlank()) return;
        mouseInputValues.put(normalized, Mth.clamp(value, -1.0D, 1.0D));
        if (normalized.startsWith("mouse_") && Math.abs(value) > 1.0E-4D && getLevel() != null) {
            mouseInputLastMovementTicks.put(normalized, getLevel().getGameTime());
        }
        if (active) activeMouseInputs.add(normalized);
        else activeMouseInputs.remove(normalized);
    }

    // Clear the mouse inputs
    public void clearMouseInputs() {
        mouseInputValues.clear();
        activeMouseInputs.clear();
        transientMouseInputs.clear();
        mouseInputLastMovementTicks.clear();
    }

    // Reset the stale mouse axes
    private void resetStaleMouseAxes() {
        if (getLevel() == null || mouseInputLastMovementTicks.isEmpty()) {
            return;
        }
        Map<String, Integer> resetTimeouts = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
            if (!"mouse_input".equals(node.type()) || !mouseNodeDefaultBoolean(node, "reset_axis")) {
                continue;
            }
            String input = normalizeMouseInput(node.data().getString("MouseInput"));
            if (!"mouse_x".equals(input) && !"mouse_y".equals(input)) {
                continue;
            }
            int timeout = Mth.clamp((int) Math.round(mouseNodeDefaultNumber(node, "timeout", 20.0D)), 1, 1200);
            resetTimeouts.merge(input, timeout, Math::min);
        }
        long gameTime = getLevel().getGameTime();
        resetTimeouts.forEach((input, timeout) -> {
            long lastMovement = mouseInputLastMovementTicks.getOrDefault(input, gameTime);
            if (gameTime - lastMovement < timeout) {
                return;
            }
            double prev = mouseInputValues.getOrDefault(input, 0.0D);
            boolean wasActive = activeMouseInputs.remove(input);
            mouseInputValues.put(input, 0.0D);
            mouseInputLastMovementTicks.remove(input);
            if ((wasActive || Math.abs(prev) > 1.0E-4D) && !getLevel().isClientSide) {
                graphRuntime.enqueue("mouse:" + input + ":inactive");
            }
        });
    }

    // Handle mouse node default boolean
    private static boolean mouseNodeDefaultBoolean(AdvancedGraphDocument.Node node, String port) {
        CompoundTag val = node.data().getCompound("Defaults").getCompound(port);
        return val.getCompound("Payload").getBoolean("Value");
    }

    // Handle mouse node default number
    private static double mouseNodeDefaultNumber(AdvancedGraphDocument.Node node, String port, double fallback) {
        CompoundTag defaults = node.data().getCompound("Defaults");
        if (!defaults.contains(port)) {
            return fallback;
        }
        return defaults.getCompound(port).getCompound("Payload").getDouble("Value");
    }

    // Deactivate the portable outputs
    @Override
    public void deactivatePortableOutputs() {
        clearMouseInputs();
        super.deactivatePortableOutputs();
    }

    // Normalize the mouse input
    public static String normalizeMouseInput(String input) {
        if (input == null) return "";
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("scroll".equals(normalized)) normalized = "scroll_up";
        return MOUSE_INPUT_TYPES.contains(normalized) ? normalized : "";
    }

    // Handle the controller key input
    @Override
    public boolean handleControllerKeyInput(String channelId, boolean pressed) {
        restoreGraphOwnedBindingChannel(channelId);
        boolean changed = super.handleControllerKeyInput(channelId, pressed);
        graphRuntime.setBindingActive(channelId, pressed);
        graphRuntime.enqueue("key:" + channelId + ":" + (pressed ? "pressed" : "released"));
        return changed;
    }

    // Restore the graph owned binding channel
    private void restoreGraphOwnedBindingChannel(String bindingId) {
        String normalizedBinding = baseBindingId(bindingId);
        if (normalizedBinding.isBlank() || getLevel() == null || getLevel().isClientSide) {
            return;
        }
        if (!graphRuntime.graphOwnedBindings(activeGraph).contains(normalizedBinding)
                || hasCustomKeyEntry(normalizedBinding)) {
            return;
        }
        applyGraphBindings(activeGraph);
        graphRuntime.compile(activeGraph);
        graphOwnedBindingChannelsVerified = true;
        setChanged();
        sendData();
    }

    // Check if this has custom key entry
    private boolean hasCustomKeyEntry(String bindingId) {
        for (CustomKeyEntry entry : getCustomKeyEntries()) {
            if (entry != null && Objects.equals(entry.id(), bindingId)) {
                return true;
            }
        }
        return false;
    }

    // Get the base binding id
    private static String baseBindingId(String bindingId) {
        if (bindingId == null) {
            return "";
        }
        String normalized = bindingId.trim();
        return normalized.endsWith("#step_down")
                ? normalized.substring(0, normalized.length() - "#step_down".length())
                : normalized;
    }

    // Collect the graph owned key bindings
    public void collectGraphOwnedKeyBindings(int keyCode, Set<String> bindings) {
        if (keyCode < 0 || bindings == null) {
            return;
        }
        collectGraphOwnedKeyBindings(activeGraph, keyCode, bindings);
    }

    // Collect the graph owned key codes
    public void collectGraphOwnedKeyCodes(Set<Integer> keyCodes) {
        if (keyCodes == null) {
            return;
        }
        for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
            String binding = graphNodeBindingId(node);
            if (binding.isBlank() || !isGraphOwnedBinding(node, binding) || !node.data().contains("KeyCode")) {
                continue;
            }
            int keyCode = node.data().getInt("KeyCode");
            if (keyCode >= 0) {
                keyCodes.add(keyCode);
            }
        }
    }

    // Handle the HUD interaction
    public boolean handleHudInteraction(String nodeId, String interactionId,
                                        AdvancedGraphDocument.Value value) {
        if (getLevel() == null || getLevel().isClientSide
                || nodeId == null || nodeId.isBlank()
                || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        boolean accepted = graphRuntime.enqueueHudInteraction(
                nodeId, interactionId, value);
        if (accepted) {
            lastGraphObserverSample = Long.MIN_VALUE;
        }
        return accepted;
    }

    // Handle a validated HUD element interaction
    public boolean handleHudElementInteraction(String nodeId, String interactionId,
                                               AdvancedGraphDocument.Value value) {
        if (nodeId == null || nodeId.isBlank() || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        AdvancedGraphDocument.Node node = activeGraph.nodes().stream()
                .filter(candidate -> candidate != null && nodeId.equals(candidate.id())
                        && "advanced_hud_element".equals(candidate.type()))
                .findFirst().orElse(null);
        if (node == null) {
            return false;
        }
        ListTag elements = node.data().getList("WidgetElements", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < elements.size(); idx++) {
            CompoundTag elm = elements.getCompound(idx);
            if (!interactionId.equals(elm.getString("InteractionId"))) {
                continue;
            }
            return switch (elm.getString("Type")) {
                case "button" -> value != null && "boolean".equals(value.type()) && value.asBoolean()
                        && handleHudButtonInteraction(nodeId, interactionId);
                case "toggle" -> value != null && "boolean".equals(value.type()) && value.asBoolean()
                        && handleHudToggleInteraction(nodeId, interactionId);
                case "slider" -> handleHudSliderInteraction(nodeId, interactionId, elm, value);
                default -> false;
            };
        }
        return false;
    }

    // Handle a validated HUD slider interaction
    private boolean handleHudSliderInteraction(String nodeId, String interactionId,
                                               CompoundTag elm, AdvancedGraphDocument.Value value) {
        if (value == null || !"number".equals(value.type()) || !Double.isFinite(value.asNumber())) {
            return false;
        }
        CompoundTag resolved = AdvancedHudElementBinding.resolvedCopy(
                elm, port -> graphRuntime.liveInput(nodeId, port));
        AdvancedHudElementStyle.applyDefaults(resolved);
        double minimum = resolved.getDouble("Min");
        double maximum = resolved.getDouble("Max");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            return false;
        }
        if (!(maximum > minimum)) {
            maximum = minimum + 1.0D;
        }
        double step = resolved.getDouble("Step");
        double resolvedValue = Mth.clamp(value.asNumber(), minimum, maximum);
        if (step > 0.0D && Double.isFinite(step)) {
            resolvedValue = minimum + Math.round((resolvedValue - minimum) / step) * step;
        }
        return handleHudInteraction(nodeId, interactionId,
                AdvancedGraphDocument.Value.number(Mth.clamp(resolvedValue, minimum, maximum)));
    }

    // Handle a momentary HUD button interaction
    public boolean handleHudButtonInteraction(String nodeId, String interactionId) {
        if (getLevel() == null || getLevel().isClientSide
                || nodeId == null || nodeId.isBlank()
                || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        boolean accepted = graphRuntime.enqueueHudButtonInteraction(
                nodeId, interactionId, getLevel().getGameTime());
        if (accepted) {
            lastGraphObserverSample = Long.MIN_VALUE;
        }
        return accepted;
    }

    // Handle an authoritative HUD toggle interaction
    public boolean handleHudToggleInteraction(String nodeId, String interactionId) {
        if (getLevel() == null || getLevel().isClientSide
                || nodeId == null || nodeId.isBlank()
                || interactionId == null || interactionId.isBlank()) {
            return false;
        }
        boolean accepted = graphRuntime.enqueueHudToggleInteraction(
                nodeId, interactionId);
        if (accepted) {
            lastGraphObserverSample = Long.MIN_VALUE;
        }
        return accepted;
    }

    // Collect the graph owned key bindings
    private static void collectGraphOwnedKeyBindings(AdvancedGraphDocument graph, int keyCode, Set<String> bindings) {
        if (graph == null) {
            return;
        }
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node == null || !node.data().contains("KeyCode") || node.data().getInt("KeyCode") != keyCode) {
                continue;
            }
            String binding = graphNodeBindingId(node);
            if (!binding.isBlank() && isGraphOwnedBinding(node, binding)) {
                bindings.add(binding);
            }
        }
    }

    // Get the graph node binding id
    private static String graphNodeBindingId(AdvancedGraphDocument.Node node) {
        if (node == null) {
            return "";
        }
        String binding = node.data().getString("BindingId");
        if (binding.isBlank()) {
            binding = node.data().getString("RouteBindingId");
        }
        return binding.isBlank() ? node.data().getString("Channel") : binding;
    }

    // Set the graph variable
    public boolean setGraphVariable(String name, AdvancedGraphDocument.Value value) {
        if (!AdvancedGraphDocument.isValidVariableName(name) || value == null) {
            return false;
        }
        AdvancedGraphDocument.Value prev = activeGraph.variables().get(name);
        if (java.util.Objects.equals(prev, value)) {
            return true;
        }
        activeGraph.variables().put(name, value);
        if (draftGraph.variables().containsKey(name)) {
            draftGraph.variables().put(name, value);
        }
        setChanged();
        graphRuntime.enqueue("variable:" + name);
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                  GRAPH PERSISTENCE / HISTORY
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Mark the graph runtime changed
    public void markGraphRuntimeChanged() {
        setChanged();
    }

    // Mark the graph runtime changed
    public void markGraphRuntimeChanged(String variable) {
        setChanged();
        if (variable != null && !variable.isBlank()) graphRuntime.enqueue("variable:" + variable);
    }

    // Persist the graph port value
    public void persistGraphPortValue(
            String runtimeNodeId,
            CompoundTag runtimeNodeData,
            String port,
            boolean output,
            AdvancedGraphDocument.Value value
    ) {
        if (runtimeNodeData == null
                || !AdvancedGraphPortState.isPersistent(runtimeNodeData, port, output)) {
            return;
        }
        boolean changed = updatePersistentGraphPort(
                activeGraph, runtimeNodeId, runtimeNodeData, port, output, value);
        changed |= updatePersistentGraphPort(
                draftGraph, runtimeNodeId, runtimeNodeData, port, output, value);
        if (changed) {
            lastGraphObserverSample = Long.MIN_VALUE;
            setChanged();
        }
    }

    // Update the persistent graph port
    private static boolean updatePersistentGraphPort(
            AdvancedGraphDocument graph,
            String runtimeNodeId,
            CompoundTag runtimeNodeData,
            String port,
            boolean output,
            AdvancedGraphDocument.Value val
    ) {
        AdvancedGraphDocument.Node node = graphNodeForRuntime(
                graph, runtimeNodeId, runtimeNodeData);
        if (node == null || !AdvancedGraphPortState.isPersistent(node, port, output)) {
            return false;
        }
        if (output && "switch".equals(node.type())
                && AdvancedGraphCatalog.switchDataMode(node)) {
            return false;
        }
        return AdvancedGraphPortState.updatePersistentValue(node, port, output, val);
    }

    // Get the graph node for runtime
    private static AdvancedGraphDocument.Node graphNodeForRuntime(
            AdvancedGraphDocument graph, String runtimeNodeId, CompoundTag runtimeNodeData
    ) {
        if (graph == null) {
            return null;
        }
        String functionId = runtimeNodeData.getString(
                AdvancedGraphFunctions.RUNTIME_FUNCTION_ID);
        String sourceNodeId = runtimeNodeData.getString(
                AdvancedGraphFunctions.RUNTIME_SOURCE_NODE_ID);
        if (!functionId.isBlank() && !sourceNodeId.isBlank()) {
            AdvancedGraphDocument.FunctionGraph function = graph.function(functionId);
            if (function != null) {
                for (AdvancedGraphDocument.Node node : function.nodes()) {
                    if (sourceNodeId.equals(node.id())) {
                        return node;
                    }
                }
            }
            return null;
        }
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node.id().equals(runtimeNodeId)) {
                return node;
            }
        }
        return null;
    }

    // Get the preview graph input
    public AdvancedGraphDocument.Value previewGraphInput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        return graphRuntime.previewInput(graph, node, port);
    }

    // Get the preview graph output
    public AdvancedGraphDocument.Value previewGraphOutput(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node, String port) {
        return graphRuntime.previewOutput(graph, node, port);
    }

    // Get the graph live input
    public AdvancedGraphLiveValue getGraphLiveInput(String nodeId, String port) {
        return clientGraphLiveInputs.get(nodeId + ":" + port);
    }

    // Get the graph live output
    public AdvancedGraphLiveValue getGraphLiveOutput(String nodeId, String port) {
        return clientGraphLiveOutputs.get(nodeId + ":" + port);
    }

    // Get the graph live inputs snapshot
    public Map<String, AdvancedGraphDocument.Value> getGraphLiveInputsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(graphLiveInputs));
    }

    // Get the graph live outputs snapshot
    public Map<String, AdvancedGraphDocument.Value> getGraphLiveOutputsSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(graphLiveOutputs));
    }

    // Get the server graph runtime outputs snapshot
    public Map<String, AdvancedGraphDocument.Value> getGraphRuntimeOutputsSnapshot() {
        return graphRuntime.liveOutputs();
    }

    // Get every public output for one active graph node
    public Map<String, AdvancedGraphDocument.Value> getGraphNodeOutputsSnapshot(
            AdvancedGraphDocument.Node node) {
        if (node == null || activeGraph.nodes().stream()
                .noneMatch(candidate -> candidate.id().equals(node.id()))) {
            return Map.of();
        }
        Map<String, AdvancedGraphDocument.Value> liveOutputs = graphRuntime.liveOutputs();
        Map<String, AdvancedGraphDocument.Value> outputs = new LinkedHashMap<>();
        GraphRuntime queryRuntime = null;
        for (Map.Entry<String, String> port : AdvancedGraphCatalog.outputs(node).entrySet()) {
            if ("exec".equals(port.getValue()) || port.getKey().startsWith("__")) {
                continue;
            }
            AdvancedGraphDocument.Value value = liveOutputs.get(
                    node.id() + ":" + port.getKey());
            if (value == null) {
                if (queryRuntime == null) {
                    queryRuntime = new GraphRuntime(this, true);
                    queryRuntime.beginPreviewSample(activeGraph);
                }
                value = queryRuntime.previewOutput(activeGraph, node, port.getKey());
            }
            outputs.put(port.getKey(), value);
        }
        return Collections.unmodifiableMap(outputs);
    }

    // Get the ACC display runtime inputs snapshot
    public Map<String, AdvancedGraphDocument.Value> getAccDisplayRuntimeInputsSnapshot() {
        refreshAccDisplaySnapshot();
        return accDisplayRuntimeInputs;
    }

    // Get the ACC display runtime outputs snapshot
    public Map<String, AdvancedGraphDocument.Value> getAccDisplayRuntimeOutputsSnapshot() {
        refreshAccDisplaySnapshot();
        return accDisplayRuntimeOutputs;
    }

    // Get execution pulses for ACC display publication
    public Map<String, Long> getAccDisplayExecutionPulsesSnapshot() {
        return graphRuntime.executionPulses();
    }

    // Refresh the ACC display snapshot
    private void refreshAccDisplaySnapshot() {
        long runtimeRevision = graphRuntime.liveValueRevision();
        if (runtimeRevision == accDisplayRuntimeSnapshotRevision) {
            return;
        }
        accDisplayRuntimeSnapshotRevision = runtimeRevision;
        accDisplayRuntimeInputs = graphRuntime.liveInputs();
        accDisplayRuntimeOutputs = graphRuntime.liveOutputs();
    }

    // Publish the ACC display updates
    private void publishAccDisplayUpdates() {
        if (level == null || level.isClientSide) {
            return;
        }
        int graphRevision = getActiveGraphRevision();
        long runtimeRevision = graphRuntime.liveValueRevision();
        if (graphRevision != accDisplayStructureRevision) {
            publishesShipInformation = false;
            for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
                if (node != null && "acc_display_crn".equals(node.type())) {
                    publishesShipInformation = true;
                    break;
                }
            }
            accDisplayStructureRevision = graphRevision;
        }
        boolean graphChanged = graphRevision != lastAccDisplayGraphRevision;
        boolean refreshTargets = graphChanged || accDisplayTargetsDirty;

        if (refreshTargets) {
            Set<AccDisplayBlockEntity> currentTargets = directAccDisplayTargets();
            for (AccDisplayBlockEntity prev : List.copyOf(publishedAccDisplays)) {
                if (!currentTargets.contains(prev)) {
                    prev.releaseController(this);
                }
            }
            publishedAccDisplays.clear();
            publishedAccDisplays.addAll(currentTargets);
            accDisplayTargetsDirty = false;
        }

        long gameTime = level.getGameTime();
        boolean updateDue = lastAccDisplayUpdateTick == Long.MIN_VALUE
                || gameTime < lastAccDisplayUpdateTick
                || gameTime - lastAccDisplayUpdateTick >= ACC_DISPLAY_UPDATE_INTERVAL;
        boolean runtimeChanged = runtimeRevision != lastAccDisplayRuntimeRevision;
        if (!graphChanged && !refreshTargets && !accDisplayFramesDirty
                && !runtimeChanged && !updateDue) {
            return;
        }
        lastAccDisplayUpdateTick = gameTime;
        int statusHash = Objects.hash(shipInitializationProgressVisible,
                shipInitializationProgressPercent, shipInitializationProgressStatus,
                publishesShipInformation ? shipInformationStatusHash() : 0);
        boolean frameChanged = graphChanged
                || runtimeChanged
                || statusHash != lastAccDisplayStatusHash
                || accDisplayFramesDirty;
        if (!frameChanged && !refreshTargets) {
            return;
        }

        Map<String, AdvancedGraphDocument.Value> inputs = getAccDisplayRuntimeInputsSnapshot();
        Map<String, AdvancedGraphDocument.Value> outputs = getAccDisplayRuntimeOutputsSnapshot();
        publishDisplayUpdates(inputs, outputs);
        for (AccDisplayBlockEntity display : List.copyOf(publishedAccDisplays)) {
            if (display == null || display.isRemoved()) {
                publishedAccDisplays.remove(display);
                continue;
            }
            display.receiveControllerUpdate(this, inputs, outputs);
        }
        lastAccDisplayGraphRevision = graphRevision;
        lastAccDisplayRuntimeRevision = runtimeRevision;
        lastAccDisplayStatusHash = statusHash;
        accDisplayFramesDirty = false;
    }

    // Publish the display updates
    private void publishDisplayUpdates(
            Map<String, AdvancedGraphDocument.Value> inputs,
            Map<String, AdvancedGraphDocument.Value> outputs
    ) {
        Set<UniversalDisplayAdapterBlockEntity> current =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
            if (node == null || !"acc_display_crn".equals(node.type())
                    || !AccDisplayBlockEntity.contentNodeVisible(node, inputs, outputs)) {
                continue;
            }
            UniversalDisplayAdapterBlockEntity adapter = displayAdapter(
                    node.data().getCompound("TargetData"));
            if (adapter == null) {
                continue;
            }
            CompoundTag frame = new CompoundTag();
            frame.putString("Source", "ACC Display Ship Information");
            AccDisplayBlockEntity.populateCrnFrame(frame, this, node, inputs, outputs);
            adapter.acceptAccDisplayPresentation(this, frame);
            current.add(adapter);
        }
        for (UniversalDisplayAdapterBlockEntity prev :
                List.copyOf(publishedDisplayAdapters)) {
            if (!current.contains(prev) && prev != null && !prev.isRemoved()) {
                prev.clearAccDisplayPresentation(this);
            }
        }
        publishedDisplayAdapters.clear();
        publishedDisplayAdapters.addAll(current);
    }

    // Request the ACC display target refresh
    void requestAccDisplayTargetRefresh() {
        accDisplayTargetsDirty = true;
    }

    // Request the ACC display frame refresh
    void requestAccDisplayFrameRefresh() {
        accDisplayFramesDirty = true;
    }

    // Get the ship information status hash
    private int shipInformationStatusHash() {
        RailwayNavigatorGraphCompat.ShipDisplayData data = getCrnShipDisplayData();
        return Objects.hash(data.shipId(), data.shipName(), data.scheduleTitle(),
                data.scheduleActive(), data.pilotPresent(), data.docked(), data.waiting(),
                data.status(), data.phase(), data.currentStop(), data.targetStop(),
                data.nextStop(), data.currentEntry(), data.nextEntry(), data.stopCount(),
                data.remainingStops(), data.etaSeconds(),
                Math.round(data.cruiseSpeed() * 10.0D),
                Math.round(data.fuelRatio() * 1000.0D),
                Math.round(data.progressPercent() * 10.0D),
                Math.round(data.distanceToTarget()));
    }

    // Get the display adapter
    private @Nullable UniversalDisplayAdapterBlockEntity displayAdapter(CompoundTag targetData) {
        ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(targetData);
        if (target == null || target.blockPos() == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, target.subLevelId(), target.blockPos());
        return blockEntity instanceof UniversalDisplayAdapterBlockEntity adapter
                ? adapter : null;
    }

    // Get the direct ACC display targets
    private Set<AccDisplayBlockEntity> directAccDisplayTargets() {
        Set<AccDisplayBlockEntity> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
            if (node == null || !node.type().startsWith("acc_display_")) {
                continue;
            }
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(
                    node.data().getCompound("TargetData"));
            if (target == null || target.blockPos() == null) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, target.subLevelId(), target.blockPos());
            if (!(blockEntity instanceof AccDisplayBlockEntity display)) {
                continue;
            }
            AccDisplayBlockEntity root = display.networkRoot();
            targets.add(root == null ? display : root);
        }
        return targets;
    }

    // Release the ACC displays
    private void releaseAccDisplays() {
        for (AccDisplayBlockEntity display : List.copyOf(publishedAccDisplays)) {
            if (display != null && !display.isRemoved()) {
                display.releaseController(this);
            }
        }
        publishedAccDisplays.clear();
        for (UniversalDisplayAdapterBlockEntity adapter :
                List.copyOf(publishedDisplayAdapters)) {
            if (adapter != null && !adapter.isRemoved()) {
                adapter.clearAccDisplayPresentation(this);
            }
        }
        publishedDisplayAdapters.clear();
    }

    // Get the graph execution pulses snapshot
    public Map<String, Long> getGraphExecutionPulsesSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(graphExecutionPulses));
    }

    // Apply the client graph runtime
    public void applyClientGraphRuntime(Map<UUID, String> trackerPairs,
                                        Map<String, AdvancedGraphLiveValue> liveInputs,
                                        Map<String, AdvancedGraphLiveValue> liveOutputs,
                                        Map<String, Long> executionPulses) {
        if (getLevel() == null || !getLevel().isClientSide) {
            return;
        }
        gogglesTrackerPairs.clear();
        if (trackerPairs != null) {
            gogglesTrackerPairs.putAll(trackerPairs);
        }
        clientGraphLiveInputs = new LinkedHashMap<>(liveInputs == null ? Map.of() : liveInputs);
        clientGraphLiveOutputs = new LinkedHashMap<>(liveOutputs == null ? Map.of() : liveOutputs);
        graphExecutionPulses = new LinkedHashMap<>(executionPulses == null ? Map.of() : executionPulses);
    }

    // Check if this has client graph snapshot
    public boolean hasClientGraphSnapshot(int draftRevision, int activeRevision) {
        return clientGraphSnapshotApplied
                && clientGraphSnapshotDraftRevision == draftRevision
                && clientGraphSnapshotActiveRevision == activeRevision;
    }

    // Apply the client graph snapshot
    public void applyClientGraphSnapshot(int draftRevision, int activeRevision,
                                         AdvancedGraphDocument draft, AdvancedGraphDocument active) {
        if (getLevel() == null || !getLevel().isClientSide) {
            return;
        }
        draftGraph = draft == null ? new AdvancedGraphDocument() : draft.copy();
        activeGraph = active == null ? new AdvancedGraphDocument() : active.copy();
        clientGraphSnapshotDraftRevision = draftRevision;
        clientGraphSnapshotActiveRevision = activeRevision;
        clientGraphSnapshotApplied = true;
        removeImplicitPresetBindings(draftGraph);
        removeImplicitPresetBindings(activeGraph);
        graphRuntime.compile(activeGraph);
    }

    // Get the combined output signal
    public int getCombinedOutputSignal() {
        return getOmeterOutputSignal();
    }

    // Get the combined output signal text
    public String getCombinedOutputSignalText() {
        return String.format(Locale.ROOT, "%02d", getCombinedOutputSignal());
    }

    // Get the graph execution pulse
    public long getGraphExecutionPulse(String edgeKey) {
        return graphExecutionPulses.getOrDefault(edgeKey, Long.MIN_VALUE);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           TARGET I/O
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the graph target data
    public AdvancedGraphDocument.Value getGraphTargetData(AdvancedGraphDocument.Node node, String port) {
        // Read diagram and resolved block targets
        ControllerDiscoveryNode discovery =
                ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        AdvancedGraphDocument.Value diagramValue =
                ContraptionDiagramControllerCompat.readTargetData(level, discovery, port);
        if (diagramValue != null) return diagramValue;

        TargetAccess target = cachedGraphTarget(node);
        if (target == null) return AdvancedGraphDocument.Value.number(0);
        BlockState state = target.level().getBlockState(target.pos());
        BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
        if (blockEntity instanceof AccDisplayBlockEntity display && "display_mode".equals(port)) {
            return AdvancedGraphDocument.Value.string(display.displayMode());
        }
        if ("distance_to_target".equals(port) && blockEntity instanceof NavTableBlockEntity navigationTable) {
            return AdvancedGraphDocument.Value.number(NavigationTableGraphData.targetDistance(
                    navigationTable, navigationTable.getTargetPosition(false)));
        }
        if ("target_coordinates".equals(port) && blockEntity instanceof NavTableBlockEntity navigationTable) {
            return NavigationTableGraphData.targetCoordinates(navigationTable);
        }
        // Read native and provider backed values
        AdvancedGraphDocument.Value navigationTableValue = NavigationTableGraphCompat.read(blockEntity, port);
        if (navigationTableValue != null) return navigationTableValue;
        GraphValue registeredValue = BlockEntityDataAdapterRegistry.read(blockEntity, port);
        if (registeredValue != null) {
            return GraphRuntime.fromLibraryValue(registeredValue);
        }
        AdvancedGraphDocument.Value externalValue = ExternalBlockEntityDirectControlCompat.readData(blockEntity, port);
        if (externalValue != null) return externalValue;
        AdvancedGraphDocument.Value doubleButtonPower = doubleButtonRedstonePower(blockEntity, port);
        if (doubleButtonPower != null) return doubleButtonPower;
        Direction gimbalDirection = gimbalSignalDirection(port);
        if (SimulatedHelper.isGimbalSensor(blockEntity) && gimbalDirection != null) {
            return AdvancedGraphDocument.Value.number(SimulatedHelper.getGimbalPower(blockEntity, gimbalDirection));
        }
        if (blockEntity instanceof DirectionalAnalogSource src && DIRECTIONAL_DATA_PORTS.contains(port)) {
            DirectionalAnalogSnapshot snapshot = src.getDirectionalAnalogSnapshot();
            if (snapshot == null) snapshot = DirectionalAnalogSnapshot.ZERO;
            return switch (port) {
                case "axis_x" -> AdvancedGraphDocument.Value.number(snapshot.localX());
                case "axis_z" -> AdvancedGraphDocument.Value.number(snapshot.localZ());
                case "forward" -> AdvancedGraphDocument.Value.number(snapshot.forward());
                case "backward" -> AdvancedGraphDocument.Value.number(snapshot.backward());
                case "left" -> AdvancedGraphDocument.Value.number(snapshot.left());
                case "right" -> AdvancedGraphDocument.Value.number(snapshot.right());
                case "magnitude" -> AdvancedGraphDocument.Value.number(snapshot.magnitude());
                case "active" -> AdvancedGraphDocument.Value.bool(src.isDirectionalAnalogActive());
                default -> AdvancedGraphDocument.Value.number(0);
            };
        }
        if (blockEntity instanceof LinkedOrientationSource src && ("angle_x".equals(port) || "angle_z".equals(port))) {
            double[] angles = src.getLinkedAnglesRadians();
            return AdvancedGraphDocument.Value.number(angles == null ? 0 : angles["angle_x".equals(port) ? 0 : 1]);
        }
        if (SimulatedHelper.isGimbalSensor(blockEntity) && ("angle_x".equals(port) || "angle_z".equals(port))) {
            double[] angles = SimulatedHelper.getAngles(blockEntity);
            return AdvancedGraphDocument.Value.number(angles == null ? 0 : angles["angle_x".equals(port) ? 0 : 1]);
        }
        // Read displays and inventories
        if (blockEntity instanceof DisplayLinkBlockEntity displayLink) {
            if ("display_text".equals(port)) return AdvancedGraphDocument.Value.string(displayText(displayLink));
            if ("target_line".equals(port)) return AdvancedGraphDocument.Value.number(displayLink.targetLine);
        }
        if (blockEntity instanceof ClipboardBlockEntity clipboard) {
            if ("clipboard_text".equals(port)) {
                return AdvancedGraphDocument.Value.string(clipboardText(clipboard));
            }
            if ("clipboard_lines".equals(port)) {
                return AdvancedGraphDocument.Value.map(
                        clipboardLineMap(ClipboardEntry.readAll(clipboard.components())));
            }
        }
        if (port.startsWith("item_slot_")) {
            IItemHandler itemHandler = findItemHandler(target.level(), target.pos(), state, blockEntity);
            int slot = parseSuffix(port, "item_slot_");
            if (itemHandler != null && slot >= 0 && slot < itemHandler.getSlots()) {
                return AdvancedGraphDocument.Value.string(itemStackText(itemHandler.getStackInSlot(slot)));
            }
        }
        if (port.startsWith("fluid_tank_")) {
            IFluidHandler fluidHandler = findFluidHandler(target.level(), target.pos(), state, blockEntity);
            int tank = parseSuffix(port, "fluid_tank_");
            if (fluidHandler != null && tank >= 0 && tank < fluidHandler.getTanks()) {
                return AdvancedGraphDocument.Value.string(fluidStackText(fluidHandler.getFluidInTank(tank)));
            }
        }
        // Read direct controls and optional integrations
        if ("direct_signal".equals(port)) {
            Double signal = ExternalBlockEntityDirectControlCompat.sampleDirectSignal(blockEntity);
            if (signal != null) return AdvancedGraphDocument.Value.number(signal);
            ControllerDirectTargetReference directTarget = graphDirectTargetReference(node);
            if (directTarget != null && directTarget.isBound()) {
                return AdvancedGraphDocument.Value.number(ControllerRedstoneCompat.sampleWrittenTarget(
                        level, directTarget, graphDirectSignalSourceId(node)));
            }
        }
        AdvancedGraphDocument.Value rotationSpeedController =
                CreateRotationSpeedControllerGraphCompat.read(blockEntity, port);
        if (rotationSpeedController != null) return rotationSpeedController;
        AdvancedGraphDocument.Value fantasizing = CreateFantasizingGraphCompat.read(blockEntity, port);
        if (fantasizing != null) return fantasizing;
        AdvancedGraphDocument.Value aeroworks = AeroworksControllerCompat.read(blockEntity, port);
        if (aeroworks != null) return aeroworks;
        AdvancedGraphDocument.Value linkedTypewriter = LinkedTypewriterGraphCompat.read(blockEntity, port);
        if (linkedTypewriter != null) return linkedTypewriter;
        AdvancedGraphDocument.Value railwayNavigator = RailwayNavigatorGraphCompat.read(blockEntity, port);
        if (railwayNavigator != null) return railwayNavigator;
        // Read block state and common capability data
        GraphValue stateValue = BlockStateDataAccess.read(state, port);
        if (stateValue != null) {
            return GraphRuntime.fromLibraryValue(stateValue);
        }
        return switch (port) {
            case "data" -> AdvancedGraphDocument.Value.map(blockDataSnapshot(
                    target.level(), target.pos(), state, blockEntity, target.side()));
            case "block_id" -> AdvancedGraphDocument.Value.string(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())));
            case "block_entity_type" -> AdvancedGraphDocument.Value.string(blockEntity == null ? "" :
                    String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())));
            case "x" -> AdvancedGraphDocument.Value.number(target.pos().getX());
            case "y" -> AdvancedGraphDocument.Value.number(target.pos().getY());
            case "z" -> AdvancedGraphDocument.Value.number(target.pos().getZ());
            case "redstone_power" -> AdvancedGraphDocument.Value.number(graphRedstonePower(node, target, state));
            case "state_properties" -> AdvancedGraphDocument.Value.map(stateProperties(state));
            case "items" -> AdvancedGraphDocument.Value.list(itemSummary(
                    target.level(), target.pos(), state, blockEntity, target.side()).entries());
            case "item_count" -> AdvancedGraphDocument.Value.number(itemSummary(
                    target.level(), target.pos(), state, blockEntity, target.side()).amount());
            case "item_capacity" -> AdvancedGraphDocument.Value.number(itemSummary(
                    target.level(), target.pos(), state, blockEntity, target.side()).capacity());
            case "item_fill" -> AdvancedGraphDocument.Value.number(itemSummary(
                    target.level(), target.pos(), state, blockEntity, target.side()).fill());
            case "fluids" -> AdvancedGraphDocument.Value.list(fluidSummary(target.level(), target.pos(), state, blockEntity).entries());
            case "fluid_amount" -> AdvancedGraphDocument.Value.number(fluidSummary(target.level(), target.pos(), state, blockEntity).amount());
            case "fluid_capacity" -> AdvancedGraphDocument.Value.number(fluidSummary(target.level(), target.pos(), state, blockEntity).capacity());
            case "fluid_fill" -> AdvancedGraphDocument.Value.number(fluidSummary(target.level(), target.pos(), state, blockEntity).fill());
            case "energy" -> AdvancedGraphDocument.Value.number(energySummary(target.level(), target.pos(), state, blockEntity)[0]);
            case "energy_capacity" -> AdvancedGraphDocument.Value.number(energySummary(target.level(), target.pos(), state, blockEntity)[1]);
            case "energy_fill" -> {
                double[] energy = energySummary(target.level(), target.pos(), state, blockEntity);
                yield AdvancedGraphDocument.Value.number(energy[1] <= 0 ? 0 : energy[0] / energy[1]);
            }
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Set the graph display mode
    public boolean setGraphDisplayMode(AdvancedGraphDocument.Node node, String mode) {
        TargetAccess target = cachedGraphTarget(node);
        if (target == null) {
            return false;
        }
        BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
        if (!(blockEntity instanceof AccDisplayBlockEntity display)) {
            return false;
        }
        display.setDisplayMode(mode);
        return true;
    }

    // Get the graph redstone power
    private static int graphRedstonePower(AdvancedGraphDocument.Node node, TargetAccess target, BlockState state) {
        Direction face = configuredDirection(node, "face");
        if (face == null) {
            face = singleLinkerFace(node);
        }
        if (face == null) {
            return target.level().getBestNeighborSignal(target.pos());
        }
        return graphFaceRedstonePower(target.level(), target.pos(), state, face);
    }

    // Find the only linker face
    private static @Nullable Direction singleLinkerFace(AdvancedGraphDocument.Node node) {
        if (node == null) {
            return null;
        }
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if (discovery == null) {
            return null;
        }
        List<ContraptionNetworkLinkerData.FaceOption> opts =
                ContraptionNetworkLinkerData.faceOptionsForNode(discovery);
        return opts.size() == 1 ? opts.getFirst().face() : null;
    }

    // Get the graph face redstone power
    private static int graphFaceRedstonePower(Level targetLevel, BlockPos targetPos, BlockState targetState, Direction face) {
        if (targetLevel == null || targetPos == null || targetState == null || face == null) {
            return 0;
        }
        if (targetState.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock) {
            if (targetLevel.getBlockEntity(targetPos) instanceof ContraptionNetworkLinkerPlaneBlockEntity plane) {
                return plane.sampleInputSignal(face);
            }
            BlockPos attachedPos = targetPos.relative(face.getOpposite());
            if (!targetLevel.isLoaded(attachedPos)) {
                return 0;
            }
            BlockState attachedState = targetLevel.getBlockState(attachedPos);
            if (attachedState.isAir()) {
                return 0;
            }
            return graphBlockFaceRedstonePower(targetLevel, attachedPos, attachedState, face);
        }
        return graphBlockFaceRedstonePower(targetLevel, targetPos, targetState, face);
    }

    // Get the graph block face redstone power
    private static int graphBlockFaceRedstonePower(Level level, BlockPos pos, BlockState state, Direction face) {
        return ControllerRedstoneCompat.sampleFaceOutputSignal(level, pos, state, face);
    }

    // Set the graph target data
    public boolean setGraphTargetData(AdvancedGraphDocument.Node node, Set<String> activePorts,
                                      Function<String, AdvancedGraphDocument.Value> values) {
        // Validate and snapshot every requested write
        TargetAccess target = resolveGraphTarget(node);
        if (target == null || activePorts == null || values == null) return false;
        Map<String, AdvancedGraphDocument.Value> safeValues = new LinkedHashMap<>();
        Set<String> safePorts = new LinkedHashSet<>();
        for (String port : activePorts) {
            AdvancedGraphDocument.Value val = values.apply(port);
            if (!isSafeGraphWriteValue(val)) {
                return false;
            }
            safePorts.add(port);
            safeValues.put(port, val);
        }
        if (safePorts.contains("direct_signal")
                && ("direct_target_output".equals(node.type()) || "linker_face_output".equals(node.type()))) {
            AdvancedGraphDocument.Value rawSignal = values.apply(GRAPH_RAW_DIRECT_SIGNAL_PORT);
            if (rawSignal != null) {
                if (!isSafeGraphWriteValue(rawSignal)) {
                    return false;
                }
                safeValues.put(GRAPH_RAW_DIRECT_SIGNAL_PORT, rawSignal);
            }
        }
        activePorts = safePorts;
        values = safeValues::get;
        // Write block state and specialized integrations
        BlockState state = target.level().getBlockState(target.pos());
        BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
        if ("set_block_data".equals(node.type())) {
            String aeroworksSection = configureAeroworksGraphSection(node, blockEntity);
            CompoundTag writablePorts = graphDataPorts(
                    target.level(), target.pos(), true, aeroworksSection);
            for (String port : activePorts) {
                if (!writablePorts.contains(port)) {
                    return false;
                }
            }
        }
        Map<String, GraphValue> stateWrites = new LinkedHashMap<>();
        for (String port : activePorts) {
            if (port != null && port.startsWith(BlockStateDataAccess.PORT_PREFIX)) {
                stateWrites.put(port, GraphRuntime.toLibraryValue(values.apply(port)));
            }
        }
        boolean changed = BlockStateDataAccess.write(target.level(), target.pos(), stateWrites);
        if (changed) {
            state = target.level().getBlockState(target.pos());
            blockEntity = target.level().getBlockEntity(target.pos());
        }
        TargetAccess attachedTarget = resolveAttachedLinkerTarget(node, target);
        boolean nixieTarget = CreateNixieTubeGraphCompat.isTarget(attachedTarget.level(), attachedTarget.pos());
        boolean nixieStringHandled = nixieTarget && activePorts.contains(CreateNixieTubeGraphCompat.STRING_PORT);
        if (nixieStringHandled) {
            changed |= CreateNixieTubeGraphCompat.writeText(
                    attachedTarget.level(), attachedTarget.pos(),
                    values.apply(CreateNixieTubeGraphCompat.STRING_PORT).asString());
        }
        boolean nixieDirectSignalHandled = nixieTarget && activePorts.contains("direct_signal");
        if (nixieDirectSignalHandled && !nixieStringHandled) {
            AdvancedGraphDocument.Value rawSignal = values.apply(GRAPH_RAW_DIRECT_SIGNAL_PORT);
            changed |= CreateNixieTubeGraphCompat.writeNumber(
                    attachedTarget.level(), attachedTarget.pos(),
                    rawSignal == null ? values.apply("direct_signal").asNumber() : rawSignal.asNumber());
        }
        boolean rotationSpeedControllerHandled = CreateRotationSpeedControllerGraphCompat.write(
                blockEntity, activePorts, values);
        changed |= rotationSpeedControllerHandled;
        changed |= CreateFantasizingGraphCompat.write(blockEntity, activePorts, values);
        changed |= NavigationTableGraphCompat.write(blockEntity, activePorts, values);
        // Write orientation and provider data
        if (blockEntity instanceof OrientationTarget orientationTarget
                && (activePorts.contains("angle_x") || activePorts.contains("angle_z"))) {
            double[] current = blockEntity instanceof LinkedOrientationSource src ? src.getLinkedAnglesRadians() : null;
            double xAngle = activePorts.contains("angle_x") ? values.apply("angle_x").asNumber()
                    : current == null ? 0 : current[0];
            double zAngle = activePorts.contains("angle_z") ? values.apply("angle_z").asNumber()
                    : current == null ? 0 : current[1];
            if (!Double.isFinite(xAngle) || !Double.isFinite(zAngle)) {
                return false;
            }
            OrientationPayload payload = new OrientationPayload(xAngle, zAngle,
                    OrientationMath.directionFromAngles(xAngle, zAngle), true,
                    level == null ? 0 : level.getGameTime());
            if (orientationTarget.canAcceptOrientationPayload(payload)) {
                orientationTarget.applyOrientationPayload(payload);
                changed = true;
            }
        }
        Map<String, String> registeredWritableData =
                BlockEntityDataAdapterRegistry.writableData(blockEntity);
        for (String port : activePorts) {
            if (registeredWritableData.containsKey(port)) {
                changed |= BlockEntityDataAdapterRegistry.write(
                        blockEntity, port, GraphRuntime.toLibraryValue(values.apply(port)));
            }
        }
        for (String port : activePorts) {
            if (ExternalBlockEntityDirectControlCompat.writableData(blockEntity).containsKey(port)) {
                changed |= ExternalBlockEntityDirectControlCompat.writeData(blockEntity, port, values.apply(port));
            }
        }
        // Write display and clipboard data
        if (blockEntity instanceof DisplayLinkBlockEntity displayLink) {
            if (activePorts.contains("display_text")) {
                ListTag lines = new ListTag();
                for (String line : values.apply("display_text").asString().split("\\R", -1)) lines.add(StringTag.valueOf(line));
                displayLink.getSourceConfig().put("ComputerSourceList", lines);
                displayLink.tickSource();
                changed = true;
            }
            if (activePorts.contains("target_line")) {
                displayLink.targetLine = Math.max(0, (int) values.apply("target_line").asNumber());
                displayLink.tickSource();
                changed = true;
            }
        }
        if (blockEntity instanceof ClipboardBlockEntity clipboard) {
            if (activePorts.contains("clipboard_text")) {
                changed |= writeClipboardText(clipboard, values.apply("clipboard_text").asString());
            }
            if (activePorts.contains("clipboard_lines")) {
                changed |= writeClipboardLines(clipboard, values.apply("clipboard_lines").payload());
            }
        }
        // Write direct controls and remaining integrations
        if (activePorts.contains("direct_signal") && !nixieDirectSignalHandled
                && !CreateRotationSpeedControllerGraphCompat.hasActiveWritePort(blockEntity, activePorts)) {
            float clampedSignal = (float) net.minecraft.util.Mth.clamp(values.apply("direct_signal").asNumber(), 0, 1);
            boolean directSignalHandled = false;
            if (blockEntity instanceof IDirectControlReceiver receiver) {
                receiver.applyDirectControllerSignal(directSignalChannel(node), clampedSignal);
                directSignalHandled = true;
                changed = true;
            } else if (ExternalBlockEntityDirectControlCompat.applyDirectSignal(
                    blockEntity, directSignalChannel(node), clampedSignal)) {
                directSignalHandled = true;
                changed = true;
            }
            if (!directSignalHandled) {
                changed |= writeGraphDirectSignal(node, blockEntity, clampedSignal);
            }
        }
        if (blockEntity instanceof IDirectControlReceiver receiver) {
            for (String channel : DIRECT_AXIS_CHANNELS) {
                if (!activePorts.contains(channel)) continue;
                receiver.applyDirectControllerSignal(channel,
                        (float) net.minecraft.util.Mth.clamp(values.apply(channel).asNumber(), -1, 1));
                changed = true;
            }
        }
        for (String port : activePorts) {
            changed |= AeroworksControllerCompat.write(blockEntity, port, values.apply(port),
                    graphDirectSignalSourceId(node));
            changed |= RailwayNavigatorGraphCompat.write(blockEntity, port, values.apply(port));
        }
        if (changed && blockEntity != null) blockEntity.setChanged();
        return changed;
    }

    // Check if this is a safe graph write value
    static boolean isSafeGraphWriteValue(@Nullable AdvancedGraphDocument.Value value) {
        if (value == null) {
            return false;
        }
        return !value.payload().contains("Value", Tag.TAG_ANY_NUMERIC)
                || Double.isFinite(value.payload().getDouble("Value"));
    }

    // Get the direct signal channel
    private static String directSignalChannel(AdvancedGraphDocument.Node node) {
        if (node == null || !"linker_face_output".equals(node.type())) {
            return "advanced_graph";
        }
        Direction face = configuredDirection(node, "face");
        if (face == null) {
            return "advanced_graph";
        }
        return switch (face) {
            case NORTH -> "yaw_left";
            case SOUTH -> "yaw_right";
            case WEST -> "roll_left";
            case EAST -> "roll_right";
            case UP -> "pitch_up";
            case DOWN -> "pitch_down";
        };
    }

    // Write the graph direct signal
    private boolean writeGraphDirectSignal(AdvancedGraphDocument.Node node,
                                           @Nullable BlockEntity blockEntity,
                                           float val) {
        ControllerDirectTargetReference target = graphDirectTargetReference(node);
        if (target == null || !target.isBound()) {
            return false;
        }
        ControllerRedstoneCompat.writeTarget(level, target, blockEntity,
                graphDirectSignalSourceId(node), val);
        return true;
    }

    // Get the graph direct signal source id
    private String graphDirectSignalSourceId(AdvancedGraphDocument.Node node) {
        return worldPosition.asLong() + ":graph_output";
    }

    // Get the graph direct target reference
    private static @Nullable ControllerDirectTargetReference graphDirectTargetReference(AdvancedGraphDocument.Node node) {
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if (discovery == null) {
            return null;
        }
        return ContraptionNetworkLinkerData.directTargetForFace(discovery, configuredDirection(node, "face"));
    }

    // Get every node in the root graph and its function bodies
    private static List<AdvancedGraphDocument.Node> graphNodesIncludingFunctions(
            AdvancedGraphDocument graph) {
        if (graph == null) return List.of();
        List<AdvancedGraphDocument.Node> nodes = new ArrayList<>(graph.nodes());
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            nodes.addAll(function.nodes());
        }
        return nodes;
    }

    // Get the edges which own one root or function node
    private static List<AdvancedGraphDocument.Edge> graphEdgesForNode(
            AdvancedGraphDocument graph, AdvancedGraphDocument.Node node) {
        if (graph == null || node == null) return List.of();
        if (graph.nodes().contains(node)) return graph.edges();
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            if (function.nodes().contains(node)) return function.edges();
        }
        return List.of();
    }

    // Refresh the data ports
    private void refreshDataPorts(AdvancedGraphDocument graph) {
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            boolean getData = "get_block_data".equals(node.type());
            boolean setData = "set_block_data".equals(node.type());
            boolean directInput = "discovered_target_input".equals(node.type()) || "linker_face_input".equals(node.type());
            boolean directOutput = "direct_target_output".equals(node.type()) || "linker_face_output".equals(node.type());
            if (!getData && !setData && !directInput && !directOutput) continue;
            ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(
                    node.data().getCompound("TargetData"));
            boolean writable = setData || directOutput;
            configureDataTargetFaceOptions(node, discovery);
            if (ContraptionDiagramControllerCompat.isTarget(discovery)) {
                CompoundTag ports = getData && !writable
                        ? ContraptionDiagramControllerCompat.readablePorts()
                        : new CompoundTag();
                node.data().remove("OutputLabels");
                node.data().put(writable ? "DynamicInputs" : "DynamicOutputs", ports);
                node.data().put(writable ? "InputOptions" : "OutputOptions", new CompoundTag());
                configureLinkerFaceInputOptions(node, discovery);
                configureDataTargetFaceOptions(node, discovery);
                continue;
            }
            TargetAccess target = resolveGraphTarget(node);
            if (target == null) continue;
            BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
            String aeroworksSection = getData || setData
                    ? configureAeroworksGraphSection(node, blockEntity)
                    : null;
            CompoundTag ports = getData || setData
                    ? graphDataPorts(target.level(), target.pos(), writable, aeroworksSection)
                    : graphDirectAxisPorts(blockEntity, writable);
            CompoundTag labels = getData && !writable
                    ? graphReadableDataPortLabels(target.level(), target.pos())
                    : new CompoundTag();
            node.data().remove("OutputLabels");
            node.data().put(writable ? "DynamicInputs" : "DynamicOutputs", ports);
            node.data().put(writable ? "InputOptions" : "OutputOptions",
                    graphDataPortOptions(target.level(), target.pos(), writable));
            if (!labels.isEmpty()) {
                node.data().put("OutputLabels", labels);
            }
            if (aeroworksSection != null) {
                Map<String, String> inputs = AdvancedGraphCatalog.inputs(node);
                Map<String, String> outputs = AdvancedGraphCatalog.outputs(node);
                graphEdgesForNode(graph, node).removeIf(edge ->
                        (edge.fromNode().equals(node.id()) && !outputs.containsKey(edge.fromPort()))
                                || (edge.toNode().equals(node.id()) && !inputs.containsKey(edge.toPort())));
            }
            configureLinkerFaceInputOptions(node, discovery);
            configureDataTargetFaceOptions(node, discovery);
        }
        refreshStructuredDataPorts(graph);
    }

    // Configure the aeroworks graph section
    public static @Nullable String configureAeroworksGraphSection(AdvancedGraphDocument.Node node,
                                                                  @Nullable BlockEntity blockEntity) {
        if (node == null) {
            return null;
        }
        AeroworksControllerCompat.ConsoleSection section = AeroworksControllerCompat.resolveConsoleSection(
                blockEntity, node.data().getString(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY));
        if (section == null) {
            node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY);
            node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY);
            return null;
        }
        node.data().putString(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY, section.id());
        node.data().putString(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY, section.label());
        return section.id();
    }

    // Configure the linker face input options
    public static void configureLinkerFaceInputOptions(AdvancedGraphDocument.Node node,
                                                        @Nullable ControllerDiscoveryNode target) {
        if (node == null || (!"linker_face_input".equals(node.type())
                && !"linker_face_output".equals(node.type()))) {
            return;
        }
        CompoundTag inputOptions = node.data().getCompound("InputOptions");
        inputOptions.remove("face");
        if (target == null) {
            node.data().put("InputOptions", inputOptions);
            return;
        }
        List<ContraptionNetworkLinkerData.FaceOption> faceOptions =
                ContraptionNetworkLinkerData.faceOptionsForNode(target);
        boolean selectable = ContraptionNetworkLinkerData.nodeUsesFaceOptions(target)
                || faceOptions.stream().anyMatch(option -> option.optionChannel() != null);
        List<Direction> directions = faceOptions.stream()
                .map(ContraptionNetworkLinkerData.FaceOption::face)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!selectable || directions.isEmpty()) {
            node.data().put("InputOptions", inputOptions);
            return;
        }
        ListTag values = new ListTag();
        for (Direction dir : directions) {
            values.add(StringTag.valueOf(dir.getSerializedName()));
        }
        inputOptions.put("face", values);
        node.data().put("InputOptions", inputOptions);
        Direction configured = configuredDirection(node, "face");
        if (!directions.contains(configured)) {
            putGraphDefault(node.data(), "face", "direction", directions.getFirst().getSerializedName());
        }
    }

    // Configure the Get / Set Data linker face options
    public static void configureDataTargetFaceOptions(AdvancedGraphDocument.Node node,
                                                       @Nullable ControllerDiscoveryNode target) {
        if (node == null || (!"get_block_data".equals(node.type())
                && !"set_block_data".equals(node.type()))) {
            return;
        }

        CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs");
        CompoundTag inputOptions = node.data().getCompound("InputOptions");
        dynamicInputs.remove("face");
        inputOptions.remove("face");

        List<Direction> directions = target == null
                || !ContraptionNetworkLinkerData.nodeUsesFaceOptions(target)
                ? List.of()
                : ContraptionNetworkLinkerData.faceOptionsForNode(target).stream()
                .map(ContraptionNetworkLinkerData.FaceOption::face)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (directions.size() > 1) {
            dynamicInputs.putString("face", "direction");
            ListTag values = new ListTag();
            for (Direction direction : directions) {
                values.add(StringTag.valueOf(direction.getSerializedName()));
            }
            inputOptions.put("face", values);
            Direction configured = configuredDirection(node, "face");
            if (!directions.contains(configured)) {
                putGraphDefault(node.data(), "face", "direction",
                        directions.getFirst().getSerializedName());
            }
        } else {
            CompoundTag defaults = node.data().getCompound("Defaults");
            defaults.remove("face");
            if (defaults.isEmpty()) node.data().remove("Defaults");
            else node.data().put("Defaults", defaults);
            CompoundTag prefilled = node.data().getCompound("PrefilledInputs");
            prefilled.remove("face");
            if (prefilled.isEmpty()) node.data().remove("PrefilledInputs");
            else node.data().put("PrefilledInputs", prefilled);
        }

        if (dynamicInputs.isEmpty()) node.data().remove("DynamicInputs");
        else node.data().put("DynamicInputs", dynamicInputs);
        if (inputOptions.isEmpty()) node.data().remove("InputOptions");
        else node.data().put("InputOptions", inputOptions);
    }

    // Refresh the structured data ports
    private void refreshStructuredDataPorts(AdvancedGraphDocument graph) {
        if (graph == null) {
            return;
        }
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            if (node == null) {
                continue;
            }
            if ("split_list".equals(node.type()) || "json_split".equals(node.type())
                    || "break_out".equals(node.type())) {
                CompoundTag outputs = GraphRuntime.splitListOutputsFor(
                        graphRuntime.previewInput(graph, node, "value"));
                if (outputs.isEmpty()) {
                    node.data().remove("DynamicOutputs");
                } else {
                    node.data().put("DynamicOutputs", outputs);
                }
            }
            if ("list_get".equals(node.type())) {
                AdvancedGraphDocument.Value val = graphRuntime.previewOutput(graph, node, "value");
                CompoundTag outputs = node.data().getCompound("DynamicOutputs");
                outputs.putString("value", val.type());
                node.data().put("DynamicOutputs", outputs);
            }
        }
    }

    // Get the graph direct axis ports
    public static CompoundTag graphDirectAxisPorts(BlockEntity blockEntity, boolean writable) {
        CompoundTag ports = new CompoundTag();
        Map<String, String> aeroworksPorts = writable
                ? AeroworksControllerCompat.writableData(blockEntity)
                : AeroworksControllerCompat.readableData(blockEntity);
        aeroworksPorts.forEach(ports::putString);
        Map<String, String> registeredPorts = writable
                ? BlockEntityDataAdapterRegistry.writableData(blockEntity)
                : BlockEntityDataAdapterRegistry.readableData(blockEntity);
        registeredPorts.forEach(ports::putString);
        Map<String, String> externalPorts = writable
                ? ExternalBlockEntityDirectControlCompat.writableData(blockEntity)
                : ExternalBlockEntityDirectControlCompat.readableData(blockEntity);
        externalPorts.forEach(ports::putString);
        Map<String, String> createPorts = writable
                ? CreateRotationSpeedControllerGraphCompat.writableData(blockEntity)
                : CreateRotationSpeedControllerGraphCompat.readableData(blockEntity);
        createPorts.forEach(ports::putString);
        Map<String, String> fantasizingPorts = writable
                ? CreateFantasizingGraphCompat.writableData(blockEntity)
                : CreateFantasizingGraphCompat.readableData(blockEntity);
        fantasizingPorts.forEach(ports::putString);
        Map<String, String> navigationPorts = writable
                ? NavigationTableGraphCompat.writableData(blockEntity)
                : NavigationTableGraphCompat.readableData(blockEntity);
        navigationPorts.forEach(ports::putString);
        Map<String, String> railwayPorts = writable
                ? RailwayNavigatorGraphCompat.writableData(blockEntity)
                : RailwayNavigatorGraphCompat.readableData(blockEntity);
        railwayPorts.forEach(ports::putString);
        CompoundTag resolved = addDirectPortFallbacks(ports, writable,
                blockEntity instanceof DirectionalAnalogSource,
                blockEntity instanceof LinkedOrientationSource || SimulatedHelper.isGimbalSensor(blockEntity),
                blockEntity instanceof OrientationTarget,
                blockEntity instanceof IDirectControlReceiver && supportsAxisControl(blockEntity));
        if (writable) removeUnsafeGraphWritePorts(resolved);
        return resolved;
    }

    // Add the direct port fallbacks
    public static CompoundTag addDirectPortFallbacks(CompoundTag ports, boolean writable,
                                                       boolean directionalSource, boolean orientationSource,
                                                       boolean orientationTarget, boolean axisControl) {
        CompoundTag resolved = ports == null ? new CompoundTag() : ports;
        boolean hasSpecificSchema = !resolved.isEmpty();
        if (!writable && !hasSpecificSchema && directionalSource) {
            for (String port : DIRECTIONAL_DATA_PORTS) {
                resolved.putString(port, "active".equals(port) ? "boolean" : "number");
            }
        }
        if (!writable && !hasSpecificSchema && orientationSource) {
            resolved.putString("angle_x", "number");
            resolved.putString("angle_z", "number");
        }
        if (writable && !hasSpecificSchema && orientationTarget) {
            resolved.putString("angle_x", "number");
            resolved.putString("angle_z", "number");
        }
        if (writable && !hasSpecificSchema && axisControl) {
            for (String channel : DIRECT_AXIS_CHANNELS) resolved.putString(channel, "number");
        }
        return resolved;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           DATA PORTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the graph data ports
    public static CompoundTag graphDataPorts(Level level, BlockPos pos, boolean writable) {
        return graphDataPorts(level, pos, writable, null);
    }

    // Get the graph readable data port labels
    public static CompoundTag graphReadableDataPortLabels(Level level, BlockPos pos) {
        CompoundTag labels = new CompoundTag();
        if (level == null || pos == null || !level.isLoaded(pos)) return labels;
        LinkedTypewriterGraphCompat.readableLabels(level.getBlockEntity(pos)).forEach(labels::putString);
        return labels;
    }

    // Get the graph data ports
    public static CompoundTag graphDataPorts(Level level, BlockPos pos, boolean writable,
                                             @Nullable String aeroworksSectionId) {
        // Resolve the target and its block state ports
        CompoundTag ports = new CompoundTag();
        if (level == null || pos == null || !level.isLoaded(pos)) return ports;
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        AeroworksControllerCompat.ConsoleSection aeroworksSection =
                AeroworksControllerCompat.resolveConsoleSection(blockEntity, aeroworksSectionId);
        // Limit Aeroworks console sections to their own schema
        if (aeroworksSectionId != null && !aeroworksSectionId.isBlank() && aeroworksSection != null) {
            Map<String, String> sectionPorts = writable
                    ? AeroworksControllerCompat.writableData(blockEntity, aeroworksSection.id())
                    : AeroworksControllerCompat.readableData(blockEntity, aeroworksSection.id());
            sectionPorts.forEach(ports::putString);
            if (writable) removeUnsafeGraphWritePorts(ports);
            return ports;
        }
        Map<String, String> registeredPorts = writable
                ? BlockEntityDataAdapterRegistry.writableData(blockEntity)
                : BlockEntityDataAdapterRegistry.readableData(blockEntity);
        registeredPorts.forEach(ports::putString);
        BlockStateDataAccess.data(state, writable).forEach(ports::putString);
        // Build writable ports from specialized controls
        if (writable) {
            boolean hasSpecificControlSchema = !registeredPorts.isEmpty();
            Map<String, String> externalPorts = ExternalBlockEntityDirectControlCompat.writableData(blockEntity);
            externalPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !externalPorts.isEmpty();
            if (blockEntity instanceof DisplayLinkBlockEntity) {
                ports.putString("display_text", "string");
                ports.putString("target_line", "number");
            }
            if (blockEntity instanceof ClipboardBlockEntity) {
                ports.putString("clipboard_text", "string");
                ports.putString("clipboard_lines", "map");
            }
            if (!hasSpecificControlSchema
                    && !CreateRotationSpeedControllerGraphCompat.isTarget(blockEntity)
                    && (blockEntity instanceof IDirectControlReceiver
                    || ExternalBlockEntityDirectControlCompat.sampleDirectSignal(blockEntity) != null)) {
                ports.putString("direct_signal", "number");
            }
            Map<String, String> createPorts = CreateRotationSpeedControllerGraphCompat.writableData(blockEntity);
            createPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !createPorts.isEmpty();
            Map<String, String> fantasizingPorts = CreateFantasizingGraphCompat.writableData(blockEntity);
            fantasizingPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !fantasizingPorts.isEmpty();
            Map<String, String> navigationPorts = NavigationTableGraphCompat.writableData(blockEntity);
            navigationPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !navigationPorts.isEmpty();
            Map<String, String> nixiePorts = CreateNixieTubeGraphCompat.writableData(blockEntity);
            nixiePorts.forEach(ports::putString);
            hasSpecificControlSchema |= !nixiePorts.isEmpty();
            Map<String, String> aeroworksPorts = AeroworksControllerCompat.writableData(blockEntity);
            aeroworksPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !aeroworksPorts.isEmpty();
            Map<String, String> railwayPorts = RailwayNavigatorGraphCompat.writableData(blockEntity);
            railwayPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !railwayPorts.isEmpty();
            if (!hasSpecificControlSchema && blockEntity instanceof OrientationTarget) {
                ports.putString("angle_x", "number");
                ports.putString("angle_z", "number");
            }
            if (!hasSpecificControlSchema
                    && blockEntity instanceof IDirectControlReceiver && supportsAxisControl(blockEntity)) {
                for (String channel : DIRECT_AXIS_CHANNELS) ports.putString(channel, "number");
            }
            removeUnsafeGraphWritePorts(ports);
            return ports;
        }
        // Build readable capability and provider ports
        ports.putString("block_id", "string");
        ports.putString("block_entity_type", "string");
        ports.putString("x", "number");
        ports.putString("y", "number");
        ports.putString("z", "number");
        ports.putString("redstone_power", "number");
        addDoubleButtonDataPorts(ports, blockEntity);
        if (hasMultiblockItemHandler(level, pos, state, blockEntity)) {
            ports.putString("items", "list");
            ports.putString("item_count", "number");
            ports.putString("item_capacity", "number");
            ports.putString("item_fill", "number");
        }
        if (hasMultiblockFluidHandler(level, pos, state, blockEntity)) {
            ports.putString("fluids", "list");
            ports.putString("fluid_amount", "number");
            ports.putString("fluid_capacity", "number");
            ports.putString("fluid_fill", "number");
        }
        if (energySummary(level, pos, state, blockEntity)[1] > 0) {
            ports.putString("energy", "number");
            ports.putString("energy_capacity", "number");
            ports.putString("energy_fill", "number");
        }
        if (blockEntity instanceof NavTableBlockEntity) {
            ports.putString("distance_to_target", "number");
            ports.putString("target_coordinates", "map");
        }
        ExternalBlockEntityDirectControlCompat.readableData(blockEntity).forEach(ports::putString);
        if (blockEntity instanceof DisplayLinkBlockEntity) {
            ports.putString("display_text", "string");
            ports.putString("target_line", "number");
        }
        if (blockEntity instanceof ClipboardBlockEntity) {
            ports.putString("clipboard_text", "string");
            ports.putString("clipboard_lines", "map");
        }
        if (!CreateRotationSpeedControllerGraphCompat.isTarget(blockEntity)
                && (blockEntity instanceof IDirectControlReceiver
                || ExternalBlockEntityDirectControlCompat.sampleDirectSignal(blockEntity) != null)) {
            ports.putString("direct_signal", "number");
        }
        if (blockEntity instanceof DirectionalAnalogSource) {
            ports.putString("axis_x", "number");
            ports.putString("axis_z", "number");
            ports.putString("forward", "number");
            ports.putString("backward", "number");
            ports.putString("left", "number");
            ports.putString("right", "number");
            ports.putString("magnitude", "number");
            ports.putString("active", "boolean");
        }
        // Add orientation and optional mod ports
        LinkedTypewriterGraphCompat.readableData(blockEntity).forEach(ports::putString);
        if (blockEntity instanceof LinkedOrientationSource || SimulatedHelper.isGimbalSensor(blockEntity)) {
            ports.putString("angle_x", "number");
            ports.putString("angle_z", "number");
        }
        if (SimulatedHelper.isGimbalSensor(blockEntity)) {
            for (String port : GIMBAL_SIGNAL_PORTS) {
                ports.putString(port, "number");
            }
        }
        CreateRotationSpeedControllerGraphCompat.readableData(blockEntity).forEach(ports::putString);
        CreateFantasizingGraphCompat.readableData(blockEntity).forEach(ports::putString);
        NavigationTableGraphCompat.readableData(blockEntity).forEach(ports::putString);
        AeroworksControllerCompat.readableData(blockEntity).forEach(ports::putString);
        RailwayNavigatorGraphCompat.readableData(blockEntity).forEach(ports::putString);
        return ports;
    }

    // Check if this is a graph container target
    public static boolean isGraphContainerTarget(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!BlockEntityDataAdapterRegistry.readableData(blockEntity).isEmpty()
                || !BlockEntityDataAdapterRegistry.writableData(blockEntity).isEmpty()) {
            return true;
        }
        if (blockEntity instanceof OrientationTarget || blockEntity instanceof IDirectControlReceiver) {
            return false;
        }
        return blockEntity instanceof Container
                || findItemHandler(level, pos, state, blockEntity) != null
                || findFluidHandler(level, pos, state, blockEntity) != null;
    }

    // Get the graph data port options
    public static CompoundTag graphDataPortOptions(Level level, BlockPos pos, boolean writable) {
        CompoundTag opts = new CompoundTag();
        if (level == null || pos == null || !level.isLoaded(pos)) return opts;
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        BlockStateDataAccess.options(state, writable).forEach((port, entries) -> {
            ListTag values = new ListTag();
            entries.forEach(value -> values.add(StringTag.valueOf(value)));
            if (!values.isEmpty()) {
                opts.put(port, values);
            }
        });
        if (writable) {
            mergeOptions(opts, registeredDataOptions(blockEntity));
            mergeOptions(opts, CreateRotationSpeedControllerGraphCompat.writableOptions(blockEntity));
            mergeOptions(opts, NavigationTableGraphCompat.writableOptions(blockEntity));
            mergeOptions(opts, AeroworksControllerCompat.writableOptions(blockEntity));
            mergeOptions(opts, RailwayNavigatorGraphCompat.writableOptions(blockEntity));
            removeUnsafeGraphWritePorts(opts);
        }
        return opts;
    }

    // Get the registered graph data options
    private static CompoundTag registeredDataOptions(BlockEntity blockEntity) {
        CompoundTag opts = new CompoundTag();
        BlockEntityDataAdapterRegistry.writableOptions(blockEntity).forEach((port, entries) -> {
            ListTag values = new ListTag();
            entries.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .forEach(value -> values.add(StringTag.valueOf(value)));
            if (!values.isEmpty()) {
                opts.put(port, values);
            }
        });
        return opts;
    }

    // Merge the options
    private static void mergeOptions(CompoundTag target, CompoundTag src) {
        for (String key : src.getAllKeys()) target.put(key, src.get(key).copy());
    }

    // Remove ports which could replace stored item contents
    private static void removeUnsafeGraphWritePorts(CompoundTag ports) {
        if (ports == null || ports.isEmpty()) {
            return;
        }
        for (String port : List.copyOf(ports.getAllKeys())) {
            if (!port.startsWith(BlockStateDataAccess.PORT_PREFIX)
                    && BlockEntityDataAccessPolicy.isItemContentMutation(
                    port, ports.getString(port))) {
                ports.remove(port);
            }
        }
    }

    // Get the display text
    private static String displayText(DisplayLinkBlockEntity displayLink) {
        ListTag lines = displayLink.getSourceConfig().getList("ComputerSourceList", net.minecraft.nbt.Tag.TAG_STRING);
        StringBuilder text = new StringBuilder();
        for (int idx = 0; idx < lines.size(); idx++) {
            if (!text.isEmpty()) text.append('\n');
            text.append(lines.getString(idx));
        }
        return text.toString();
    }

    // Get the clipboard text
    private static String clipboardText(ClipboardBlockEntity clipboard) {
        StringBuilder text = new StringBuilder();
        for (List<ClipboardEntry> page : ClipboardEntry.readAll(clipboard.components())) {
            for (ClipboardEntry entry : page) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(entry.text.getString());
            }
        }
        return text.toString();
    }

    // Write the clipboard text
    private static boolean writeClipboardText(ClipboardBlockEntity clipboard, String text) {
        List<ClipboardEntry> page = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\\R", -1)) {
            page.add(new ClipboardEntry(false, Component.literal(line)));
        }
        return writeClipboardPages(clipboard, List.of(page));
    }

    // Write the clipboard lines
    private static boolean writeClipboardLines(ClipboardBlockEntity clipboard, CompoundTag lineMap) {
        List<List<ClipboardEntry>> pages =
                applyClipboardLineMap(ClipboardEntry.readAll(clipboard.components()), lineMap);
        if (pages == null) {
            return false;
        }
        return writeClipboardPages(clipboard, pages);
    }

    // Write the clipboard pages
    private static boolean writeClipboardPages(
            ClipboardBlockEntity clipboard,
            List<List<ClipboardEntry>> pages
    ) {
        ItemStack stack = new ItemStack(clipboard.getBlockState().getBlock().asItem());
        stack.applyComponents(clipboard.components());
        ClipboardContent current = clipboard.components().get(AllDataComponents.CLIPBOARD_CONTENT);
        ClipboardContent updated = current == null
                ? new ClipboardContent(ClipboardOverrides.ClipboardType.WRITTEN, pages, false)
                : current.setType(ClipboardOverrides.ClipboardType.WRITTEN)
                        .setPages(pages)
                        .setReadOnly(false);
        stack.set(AllDataComponents.CLIPBOARD_CONTENT, updated);
        clipboard.setComponents(stack.getComponents());
        clipboard.updateWrittenState();
        clipboard.notifyUpdate();
        return true;
    }

    // Get the clipboard line map
    static CompoundTag clipboardLineMap(List<List<ClipboardEntry>> pages) {
        List<String> text = new ArrayList<>();
        for (List<ClipboardEntry> page : pages == null ? List.<List<ClipboardEntry>>of() : pages) {
            for (ClipboardEntry entry : page) {
                text.add(entry == null ? "" : entry.text.getString());
            }
        }
        return clipboardLineMapValues(text);
    }

    // Get the clipboard line map values
    static CompoundTag clipboardLineMapValues(List<String> text) {
        CompoundTag lines = new CompoundTag();
        int line = 0;
        for (String entry : text == null ? List.<String>of() : text) {
            CompoundTag val = new CompoundTag();
            CompoundTag payload = new CompoundTag();
            val.putString("Type", "string");
            payload.putString("Value", entry == null ? "" : entry);
            val.put("Payload", payload);
            lines.put(Integer.toString(line++), val);
        }
        return lines;
    }

    // Apply the clipboard line map
    static @Nullable List<List<ClipboardEntry>> applyClipboardLineMap(
            List<List<ClipboardEntry>> currentPages,
            CompoundTag lineMap
    ) {
        if (lineMap == null || lineMap.isEmpty()) {
            return null;
        }
        Map<Integer, String> updates = clipboardLineUpdates(lineMap);
        if (updates.isEmpty()) {
            return null;
        }

        List<List<ClipboardEntry>> pages = new ArrayList<>();
        if (currentPages != null) {
            for (List<ClipboardEntry> page : currentPages) {
                pages.add(new ArrayList<>(page));
            }
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        for (Map.Entry<Integer, String> update : updates.entrySet()) {
            ensureClipboardLine(pages, update.getKey());
            replaceClipboardLine(pages, update.getKey(), update.getValue());
        }
        return pages;
    }

    // Get the clipboard line updates
    static Map<Integer, String> clipboardLineUpdates(CompoundTag lineMap) {
        Map<Integer, String> updates = new java.util.TreeMap<>();
        if (lineMap == null) {
            return updates;
        }
        for (String key : lineMap.getAllKeys()) {
            int line = parseSuffix(key, "");
            if (line >= 0) {
                updates.put(line, graphListString(lineMap, key));
            }
        }
        return updates;
    }

    // Ensure the clipboard line
    private static void ensureClipboardLine(List<List<ClipboardEntry>> pages, int requestedLine) {
        int lineCount = pages.stream().mapToInt(List::size).sum();
        while (lineCount <= requestedLine) {
            List<ClipboardEntry> page = pages.getLast();
            if (page.size() >= 7) {
                page = new ArrayList<>();
                pages.add(page);
            }
            page.add(new ClipboardEntry(false, Component.empty()));
            lineCount++;
        }
    }

    // Replace the clipboard line
    private static void replaceClipboardLine(
            List<List<ClipboardEntry>> pages,
            int requestedLine,
            String text
    ) {
        int offset = requestedLine;
        for (List<ClipboardEntry> page : pages) {
            if (offset >= page.size()) {
                offset -= page.size();
                continue;
            }
            ClipboardEntry current = page.get(offset);
            ClipboardEntry replacement = new ClipboardEntry(
                    current != null && current.checked,
                    Component.literal(text == null ? "" : text));
            if (current != null && !current.icon.isEmpty()) {
                replacement.displayItem(current.icon.copy(), current.itemAmount);
            }
            page.set(offset, replacement);
            return;
        }
    }

    // Get the graph list string
    private static String graphListString(CompoundTag values, String key) {
        if (!values.contains(key)) return "";
        net.minecraft.nbt.Tag tag = values.get(key);
        if (tag instanceof CompoundTag val && val.contains("Payload")) {
            return valueText(new AdvancedGraphDocument.Value(val.getString("Type"), val.getCompound("Payload")));
        }
        return tag == null ? "" : tag.getAsString();
    }

    // Parse the suffix
    private static int parseSuffix(String val, String prefix) {
        try {
            return Integer.parseInt(val.substring(prefix.length()));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    // Get the item stack text
    private static String itemStackText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id + " x " + stack.getCount();
    }

    // Get the fluid stack text
    private static String fluidStackText(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id == null ? "" : id + " x " + stack.getAmount();
    }

    // Resolve the graph target
    private TargetAccess resolveGraphTarget(AdvancedGraphDocument.Node node) {
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if (discovery == null || discovery.blockPos() == null || level == null) return null;
        ControllerDirectTargetReference resolvedReference =
                ControllerRedstoneCompat.resolveMovingTarget(level, discovery.asDirectTargetReference());
        if (resolvedReference != null && (!Objects.equals(discovery.subLevelId(), resolvedReference.subLevelId())
                || !Objects.equals(discovery.blockPos(), resolvedReference.blockPos())
                || !Objects.equals(discovery.nodeId(), ContraptionNetworkLinkerData.baseNodeIdFromTargetId(resolvedReference.targetId())))) {
            discovery = new ControllerDiscoveryNode(
                    ContraptionNetworkLinkerData.baseNodeIdFromTargetId(resolvedReference.targetId()),
                    discovery.kind(),
                    resolvedReference.groupId(),
                    discovery.blockId(),
                    discovery.label(),
                    resolvedReference.subLevelId(),
                    resolvedReference.blockPos());
            node.data().putString("Target", discovery.nodeId());
            node.data().putString("TargetLabel", discovery.label().isBlank() ? discovery.nodeId() : discovery.label());
            node.data().put("TargetData", discovery.toTag());
        }
        BlockEntity blockEntity =
                SimulatedHelper.findLoadedBlockEntityExact(level, discovery.subLevelId(), discovery.blockPos());
        boolean targetLoaded = blockEntity != null || SubLevelBlockEntityCollector.isTargetLoaded(
                level, discovery.subLevelId(), discovery.blockPos());
        if (!targetLoaded) {
            return null;
        }
        if (blockEntity == null && level.getBlockState(discovery.blockPos()).isAir()) {
            ControllerDiscoveryNode current = findCurrentTarget(discovery);
            if (current != null && current.blockPos() != null) {
                node.data().putString("Target", current.nodeId());
                node.data().putString("TargetLabel", current.label().isBlank() ? current.nodeId() : current.label());
                node.data().put("TargetData", current.toTag());
                discovery = current;
                blockEntity =
                        SimulatedHelper.findLoadedBlockEntityExact(
                                level, discovery.subLevelId(), discovery.blockPos());
                targetLoaded = blockEntity != null || SubLevelBlockEntityCollector.isTargetLoaded(
                        level, discovery.subLevelId(), discovery.blockPos());
            }
        }
        if (!targetLoaded) {
            return null;
        }
        Level targetLevel = blockEntity != null && blockEntity.getLevel() != null ? blockEntity.getLevel() : level;
        BlockPos targetPos = blockEntity != null ? blockEntity.getBlockPos() : discovery.blockPos();
        TargetAccess target = new TargetAccess(targetLevel, targetPos, configuredDirection(node, "face"));
        return resolveAttachedDataTarget(node, target);
    }

    // Get the cached graph target
    private @Nullable TargetAccess cachedGraphTarget(AdvancedGraphDocument.Node node) {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (graphTargetAccessCacheTick != gameTime) {
            graphTargetAccessCacheTick = gameTime;
            graphTargetAccessCache.clear();
            missingGraphTargetAccessCache.clear();
        }
        TargetAccess cached = graphTargetAccessCache.get(node);
        if (cached != null) {
            return cached;
        }
        if (missingGraphTargetAccessCache.contains(node)) {
            return null;
        }
        TargetAccess resolved = resolveGraphTarget(node);
        if (resolved == null) {
            missingGraphTargetAccessCache.add(node);
        } else {
            graphTargetAccessCache.put(node, resolved);
        }
        return resolved;
    }

    // Resolve the attached data target
    private static @Nullable TargetAccess resolveAttachedDataTarget(
            AdvancedGraphDocument.Node node, TargetAccess target) {
        if (node == null || target == null
                || (!"get_block_data".equals(node.type()) && !"set_block_data".equals(node.type()))) {
            return target;
        }
        BlockState state = target.level().getBlockState(target.pos());
        if (!(state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)) {
            return target;
        }
        Direction side = target.side() == null ? singleLinkerFace(node) : target.side();
        if (side == null) {
            return null;
        }
        BlockPos attachedPos = target.pos().relative(side.getOpposite());
        if (!target.level().isLoaded(attachedPos)
                || target.level().getBlockState(attachedPos).isAir()) {
            return null;
        }
        return new TargetAccess(target.level(), attachedPos, side);
    }

    // Resolve the attached linker target
    private static TargetAccess resolveAttachedLinkerTarget(AdvancedGraphDocument.Node node, TargetAccess target) {
        if (node == null || target == null) {
            return target;
        }
        BlockState state = target.level().getBlockState(target.pos());
        if (!(state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)) {
            return target;
        }
        Direction side = target.side() == null ? singleLinkerFace(node) : target.side();
        if (side == null) {
            return target;
        }
        BlockPos attachedPos = target.pos().relative(side.getOpposite());
        if (!target.level().isLoaded(attachedPos) || target.level().getBlockState(attachedPos).isAir()) {
            return target;
        }
        return new TargetAccess(target.level(), attachedPos, side);
    }

    // Find the current target
    private ControllerDiscoveryNode findCurrentTarget(ControllerDiscoveryNode stale) {
        if (stale == null || level == null) return null;
        ControllerDiscoveryNode sameLocation = null;
        boolean ambiguousSameLocation = false;
        ControllerDiscoveryNode exact = null;
        ControllerDiscoveryNode fallback = null;
        for (ControllerDiscoveryNode candidate : getAssignableTargets()) {
            if (!graphTargetExists(candidate)) continue;
            if (candidate.nodeId().equals(stale.nodeId())) {
                exact = candidate;
                continue;
            }
            if (candidate.kind() == stale.kind()
                    && candidate.blockId().equals(stale.blockId())
                    && Objects.equals(candidate.subLevelId(), stale.subLevelId())
                    && Objects.equals(candidate.blockPos(), stale.blockPos())) {
                if (sameLocation != null) {
                    sameLocation = null;
                    ambiguousSameLocation = true;
                } else if (!ambiguousSameLocation) {
                    sameLocation = candidate;
                }
                continue;
            }
            if (!candidate.blockId().equals(stale.blockId()) || !candidate.label().equals(stale.label())) continue;
            if (fallback != null) return null;
            fallback = candidate;
        }
        return sameLocation != null && !ambiguousSameLocation ? sameLocation : exact != null ? exact : fallback;
    }

    // Check if the graph target exists
    private boolean graphTargetExists(ControllerDiscoveryNode candidate) {
        if (candidate == null || candidate.blockPos() == null || level == null) return false;
        if (ContraptionDiagramControllerCompat.isTarget(candidate)) {
            return ContraptionDiagramControllerCompat.targetExists(level, candidate);
        }
        ControllerDirectTargetReference resolved =
                ControllerRedstoneCompat.resolveMovingTarget(level, candidate.asDirectTargetReference());
        if (resolved != null && resolved.blockPos() != null) {
            candidate = new ControllerDiscoveryNode(
                    ContraptionNetworkLinkerData.baseNodeIdFromTargetId(resolved.targetId()),
                    candidate.kind(), resolved.groupId(), candidate.blockId(), candidate.label(),
                    resolved.subLevelId(), resolved.blockPos());
        }
        if (SimulatedHelper.findLoadedBlockEntityExact(
                level, candidate.subLevelId(), candidate.blockPos()) != null) {
            return true;
        }
        return SubLevelBlockEntityCollector.isTargetLoaded(
                level, candidate.subLevelId(), candidate.blockPos())
                && !level.getBlockState(candidate.blockPos()).isAir();
    }

    // Check if the stored graph target exists
    private boolean storedGraphTargetExists(ControllerDiscoveryNode candidate) {
        if (candidate == null || candidate.blockPos() == null || level == null) return false;
        if (ContraptionDiagramControllerCompat.isTarget(candidate)) {
            return ContraptionDiagramControllerCompat.targetExists(level, candidate);
        }
        if (SimulatedHelper.findLoadedBlockEntityExact(
                level, candidate.subLevelId(), candidate.blockPos()) != null) {
            return true;
        }
        return SubLevelBlockEntityCollector.isTargetLoaded(
                level, candidate.subLevelId(), candidate.blockPos())
                && !level.getBlockState(candidate.blockPos()).isAir();
    }

    // Refresh the current target
    private ControllerDiscoveryNode refreshCurrentTarget(ControllerDiscoveryNode stale) {
        if (stale == null || stale.blockPos() == null || level == null) return null;
        ControllerDirectTargetReference resolved =
                ControllerRedstoneCompat.resolveMovingTarget(level, stale.asDirectTargetReference());
        if (resolved != null && resolved.blockPos() != null) {
            ControllerDiscoveryNode moved = new ControllerDiscoveryNode(
                    ContraptionNetworkLinkerData.baseNodeIdFromTargetId(resolved.targetId()),
                    stale.kind(),
                    resolved.groupId(),
                    stale.blockId(),
                    stale.label(),
                    resolved.subLevelId(),
                    resolved.blockPos());
            if (!moved.equals(stale)) {
                return moved;
            }
        }
        ControllerDiscoveryNode current = findCurrentTarget(stale);
        if (current != null && !current.equals(stale)) {
            return current;
        }
        return storedGraphTargetExists(stale) ? stale : current;
    }

    // Reconcile the graph targets
    private void reconcileGraphTargets() {
        boolean activeChanged = reconcileGraphTargets(activeGraph);
        boolean draftChanged = reconcileGraphTargets(draftGraph);
        if (!activeChanged && !draftChanged) return;
        if (activeChanged) {
            Map<String, Double> outputValues = graphOutputValues(activeGraph);
            Map<String, Double> resetValues = new LinkedHashMap<>();
            outputValues.keySet().forEach(binding -> resetValues.put(binding, 0.0));
            setGraphBindingValues(resetValues, false);
            refreshDataPorts(activeGraph);
            applyGraphBindings(activeGraph);
            setGraphBindingValues(outputValues, false);
        }
        if (draftChanged) refreshDataPorts(draftGraph);
        setChanged();
        sendData();
    }

    // Get the graph output values
    private Map<String, Double> graphOutputValues(AdvancedGraphDocument graph) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (!isGraphOutputNode(node)) continue;
            String binding = graphOutputBinding(node);
            if (!binding.isBlank()) values.put(binding, getGraphBindingValue(binding));
        }
        return values;
    }

    // Check if this is a graph output node
    private static boolean isGraphOutputNode(AdvancedGraphDocument.Node node) {
        return node != null && node.type().endsWith("_output");
    }

    // Get the graph output binding
    private static String graphOutputBinding(AdvancedGraphDocument.Node node) {
        String binding = node.data().getString("BindingId");
        if (binding.isBlank()) binding = node.data().getString("RouteBindingId");
        if (binding.isBlank()) binding = node.data().getString("Channel");
        return binding;
    }

    // Reconcile the graph targets
    private boolean reconcileGraphTargets(AdvancedGraphDocument graph) {
        boolean changed = false;
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            ControllerDiscoveryNode stale = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
            ControllerDiscoveryNode current = refreshCurrentTarget(stale);
            if (current == null || current.equals(stale)) continue;
            node.data().putString("Target", current.nodeId());
            node.data().putString("TargetLabel", current.label().isBlank() ? current.nodeId() : current.label());
            node.data().put("TargetData", current.toTag());
            changed = true;
        }
        return changed;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         LINKER IMPORTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the inserted linker synchronized event
    @Override
    protected void onInsertedLinkerSynchronized(ItemStack linker) {
        onInsertedLinkerSynchronized(linker, false);
    }

    // Handle the inserted linker synchronized event
    private void onInsertedLinkerSynchronized(ItemStack linker, boolean forceGraphReplace) {
        super.onInsertedLinkerSynchronized(linker);
        if (getLevel() == null || getLevel().isClientSide || linker == null || linker.isEmpty()) {
            return;
        }
        boolean changed = importInsertedLinkerBindings(linker, forceGraphReplace);
        refreshDataPorts(draftGraph);
        refreshDataPorts(activeGraph);
        applyGraphBindings(activeGraph);
        graphRuntime.compile(activeGraph);
        graphOwnedBindingChannelsVerified = true;
        queueGraphBindingSync();
        setChanged();
        sendData();
        if (changed) {
            graphRuntime.enqueue("linker_import");
        }
    }

    // Import the inserted linker bindings
    private boolean importInsertedLinkerBindings(ItemStack linker, boolean forceGraphReplace) {
        if (forceGraphReplace) {
            ContraptionNetworkLinkerData.StoredGraph storedGraph =
                    ContraptionNetworkLinkerData.readSelectedStoredGraph(linker);
            if (storedGraph != null && !storedGraph.graphTag().isEmpty()) {
                return replaceGraphFromStoredLinker(storedGraph, true);
            }
        }
        List<ControllerDiscoveryNode> linkerTargets = ContraptionNetworkLinkerData.toDiscoveryNodes(linker);
        HolderLookup.Provider provider = getLevel().registryAccess();
        boolean draftChanged = importLinkerBindingsIntoGraph(draftGraph, linker, linkerTargets, provider);
        boolean activeChanged = importLinkerBindingsIntoGraph(activeGraph, linker, linkerTargets, provider);
        if (draftChanged) {
            draftGraph.setRevision(draftGraph.revision() + 1);
        }
        if (activeChanged) {
            activeGraph.setRevision(activeGraph.revision() + 1);
        }
        return draftChanged || activeChanged;
    }

    // Replace the graph from stored linker
    private boolean replaceGraphFromStoredLinker(ContraptionNetworkLinkerData.StoredGraph storedGraph) {
        return replaceGraphFromStoredLinker(storedGraph, false);
    }

    // Replace the graph from stored linker
    private boolean replaceGraphFromStoredLinker(ContraptionNetworkLinkerData.StoredGraph storedGraph,
                                                 boolean forceReplace) {
        AdvancedGraphDocument imported = AdvancedGraphDocument.fromTag(storedGraph.graphTag());
        removeImplicitPresetBindings(imported);
        refreshDataPorts(imported);
        CompoundTag importedTag = imported.toTag();
        if (!forceReplace && Objects.equals(draftGraph.toTag(), importedTag) && Objects.equals(activeGraph.toTag(), importedTag)) {
            return false;
        }
        graphVersions.pushIfChanged(draftGraph, imported);
        clearGraphTargetWrites(activeGraph);
        clearGraphOutputs(activeGraph);
        clearGraphRoutedState(activeGraph);
        resetGraphBindingsBatch();
        lastGraphChannelValues.clear();
        graphLiveInputs = new LinkedHashMap<>();
        graphLiveOutputs = new LinkedHashMap<>();
        graphExecutionPulses = new LinkedHashMap<>();
        graphRuntime.clear();
        draftGraph = imported.copy();
        activeGraph = imported.copy();
        draftGraph.setRevision(draftGraph.revision() + 1);
        activeGraph.setRevision(activeGraph.revision() + 1);
        return true;
    }

    // Import the linker bindings into graph
    public static boolean importLinkerBindingsIntoGraph(AdvancedGraphDocument graph, ItemStack linker,
                                                        HolderLookup.Provider provider) {
        if (graph == null || linker == null || linker.isEmpty() || provider == null) {
            return false;
        }
        return importLinkerBindingsIntoGraph(graph, linker, ContraptionNetworkLinkerData.toDiscoveryNodes(linker), provider);
    }

    // Import the controller data into graph
    public static boolean importControllerDataIntoGraph(AdvancedGraphDocument graph, CompoundTag controllerData,
                                                        HolderLookup.Provider provider) {
        if (graph == null || controllerData == null || provider == null) {
            return false;
        }
        List<ControllerDiscoveryNode> storedTargets = storedTargetsFromControllerData(controllerData);
        boolean changed = false;

        CompoundTag channelsTag = controllerData.getCompound("Channels");
        CompoundTag directTargetsTag = controllerData.getCompound("DirectTargets");
        CompoundTag inputTargetsTag = controllerData.getCompound("InputTargets");
        CompoundTag bindingsTag = controllerData.getCompound("Bindings");
        CompoundTag inputBindingsTag = controllerData.getCompound("InputBindings");
        CompoundTag localOutputsTag = controllerData.getCompound("LocalOutputSides");
        CompoundTag keyBindingsTag = controllerData.getCompound("KeyBindings");

        Set<String> channelIds = new LinkedHashSet<>();
        channelIds.addAll(channelsTag.getAllKeys());
        channelIds.addAll(directTargetsTag.getAllKeys());
        channelIds.addAll(inputTargetsTag.getAllKeys());
        channelIds.addAll(bindingsTag.getAllKeys());
        channelIds.addAll(inputBindingsTag.getAllKeys());
        channelIds.addAll(localOutputsTag.getAllKeys());
        channelIds.addAll(keyBindingsTag.getAllKeys());

        for (String channelId : channelIds) {
            if (channelId == null || channelId.isBlank()) {
                continue;
            }
            String label = channelId;
            int keyCode = keyBindingsTag.contains(channelId) ? keyBindingsTag.getInt(channelId) : -1;
            ControllerDirectTargetReference directTarget = directTargetsTag.contains(channelId, Tag.TAG_COMPOUND)
                    ? ControllerDirectTargetReference.fromTag(directTargetsTag.getCompound(channelId)) : null;
            ControllerDirectTargetReference inputTarget = inputTargetsTag.contains(channelId, Tag.TAG_COMPOUND)
                    ? ControllerDirectTargetReference.fromTag(inputTargetsTag.getCompound(channelId)) : null;
            FrequencyBinding outputFrequency = bindingsTag.contains(channelId, Tag.TAG_COMPOUND)
                    ? FrequencyBinding.fromTag(bindingsTag.getCompound(channelId), provider) : null;
            FrequencyBinding inputFrequency = inputBindingsTag.contains(channelId, Tag.TAG_COMPOUND)
                    ? FrequencyBinding.fromTag(inputBindingsTag.getCompound(channelId), provider) : null;
            Direction localSide = localOutputsTag.contains(channelId)
                    ? Direction.byName(localOutputsTag.getString(channelId)) : null;

            ImportedNode src = null;
            if (isBound(inputTarget)) {
                src = ensureTargetGraphNode(graph, channelId, label, inputTarget, true, false, keyCode, storedTargets);
            } else if (inputFrequency != null && hasFrequencyPair(inputFrequency.first(), inputFrequency.second())) {
                src = ensureWirelessGraphNode(graph, channelId, label, true, false, keyCode,
                        inputFrequency.first(), inputFrequency.second(), provider);
            } else {
                src = ensureControllerBindingNode(graph, channelId, label, true, false, keyCode,
                        "base_controller_source:" + channelId, "Configured Channel");
            }
            changed |= src != null && src.changed();

            ImportedNode output = null;
            if (isBound(directTarget)) {
                output = ensureTargetGraphNode(graph, channelId, label, directTarget, false, false, keyCode, storedTargets);
            } else if (outputFrequency != null && hasFrequencyPair(outputFrequency.first(), outputFrequency.second())) {
                output = ensureWirelessGraphNode(graph, channelId, label, false, false, keyCode,
                        outputFrequency.first(), outputFrequency.second(), provider);
            } else if (localSide != null) {
                output = ensureLocalRedstoneGraphNode(graph, channelId, label, false, keyCode, localSide);
            }
            changed |= output != null && output.changed();
            if (src != null && output != null) {
                changed |= ensureRouteEdges(graph, src.nodeId(), output.nodeId());
            }
        }

        ListTag customKeyEntries = controllerData.getList("CustomKeyEntries", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < customKeyEntries.size(); idx++) {
            CustomKeyEntry entry = CustomKeyEntry.fromTag(customKeyEntries.getCompound(idx), provider);
            if (entry == null) {
                continue;
            }
            changed |= importCustomEntryIntoGraph(graph, entry, storedTargets, provider);
        }
        return changed;
    }

    // Complete the assembly transfer
    @Override
    protected boolean onAssemblyTransferCompleted(@Nullable UUID sourceSubLevelId,
                                                  @Nullable UUID destinationSubLevelId,
                                                  BlockPos offset) {
        boolean draftChanged = remapGraphTargetsForAssembly(draftGraph, sourceSubLevelId, destinationSubLevelId, offset);
        boolean activeChanged = remapGraphTargetsForAssembly(activeGraph, sourceSubLevelId, destinationSubLevelId, offset);
        if (draftChanged) {
            draftGraph.setRevision(draftGraph.revision() + 1);
            refreshDataPorts(draftGraph);
        }
        if (activeChanged) {
            activeGraph.setRevision(activeGraph.revision() + 1);
            refreshDataPorts(activeGraph);
            applyGraphBindings(activeGraph);
        }
        refreshGraphRuntime(true);
        graphRuntime.enqueue("assembly_transfer");
        shipControlRuntime.invalidateForAssemblyTransfer();
        return draftChanged || activeChanged;
    }

    // Remap the graph targets for assembly
    private boolean remapGraphTargetsForAssembly(AdvancedGraphDocument graph,
                                                 @Nullable UUID sourceSubLevelId,
                                                 @Nullable UUID destinationSubLevelId,
                                                 BlockPos offset) {
        if (graph == null || offset == null) {
            return false;
        }
        boolean changed = false;
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (node == null || !node.data().contains("TargetData", Tag.TAG_COMPOUND)) {
                continue;
            }
            ControllerDiscoveryNode current = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
            ControllerDiscoveryNode remapped = remapMovedDiscoveryNode(
                    current, sourceSubLevelId, destinationSubLevelId, offset);
            if (remapped == null || Objects.equals(current, remapped)) {
                continue;
            }
            node.data().putString("Target", remapped.nodeId());
            node.data().putString("TargetLabel", remapped.label().isBlank() ? remapped.nodeId() : remapped.label());
            node.data().put("TargetData", remapped.toTag());
            changed = true;
        }
        return changed;
    }

    // Get the stored targets from controller data
    private static List<ControllerDiscoveryNode> storedTargetsFromControllerData(CompoundTag controllerData) {
        List<ControllerDiscoveryNode> targets = new ArrayList<>();
        ListTag storedTargetsTag = controllerData.getList("StoredTargets", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < storedTargetsTag.size(); idx++) {
            ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(storedTargetsTag.getCompound(idx));
            if (node != null && node.isValid()) {
                targets.add(node);
            }
        }
        return targets;
    }

    // Import the linker bindings into graph
    private static boolean importLinkerBindingsIntoGraph(AdvancedGraphDocument graph, ItemStack linker,
                                                         List<ControllerDiscoveryNode> linkerTargets,
                                                         HolderLookup.Provider provider) {
        if (graph == null || linker == null || linker.isEmpty() || provider == null) {
            return false;
        }
        return importLinkerBindingsIntoGraph(
                graph,
                ContraptionNetworkLinkerData.readChannelBindings(linker),
                ContraptionNetworkLinkerData.readCustomEntryBindings(linker),
                linkerTargets,
                provider);
    }

    // Import the linker bindings into graph
    static boolean importLinkerBindingsIntoGraph(AdvancedGraphDocument graph, CompoundTag linkerRoot,
                                                 List<ControllerDiscoveryNode> linkerTargets,
                                                 HolderLookup.Provider provider) {
        if (linkerRoot == null) {
            return false;
        }
        return importLinkerBindingsIntoGraph(
                graph,
                ContraptionNetworkLinkerData.readChannelBindings(linkerRoot),
                ContraptionNetworkLinkerData.readCustomEntryBindings(linkerRoot),
                linkerTargets,
                provider);
    }

    // Import the linker bindings into graph
    private static boolean importLinkerBindingsIntoGraph(
            AdvancedGraphDocument graph,
            Map<String, ContraptionNetworkLinkerData.ChannelBind> channelBindings,
            List<CompoundTag> customEntryBindings,
            List<ControllerDiscoveryNode> linkerTargets,
            HolderLookup.Provider provider) {
        if (graph == null || provider == null) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, ContraptionNetworkLinkerData.ChannelBind> entry : channelBindings.entrySet()) {
            changed |= importChannelBindingIntoGraph(graph, entry.getKey(), entry.getValue(), linkerTargets);
        }
        for (CompoundTag tag : customEntryBindings) {
            CustomKeyEntry entry = CustomKeyEntry.fromTag(tag, provider);
            if (entry == null || entry.id() == null || entry.id().isBlank()) {
                continue;
            }
            changed |= importCustomEntryIntoGraph(graph, entry, linkerTargets, provider);
        }
        return changed;
    }

    // Import the channel binding into graph
    private static boolean importChannelBindingIntoGraph(AdvancedGraphDocument graph, String channelId,
                                                         ContraptionNetworkLinkerData.ChannelBind binding,
                                                         List<ControllerDiscoveryNode> linkerTargets) {
        if (channelId == null || channelId.isBlank() || binding == null) {
            return false;
        }
        boolean changed = false;
        String label = channelId;
        ImportedNode src = null;
        if (isBound(binding.inputTarget())) {
            src = ensureTargetGraphNode(graph, channelId, label, binding.inputTarget(), true, false, -1, linkerTargets);
        } else if (isBound(binding.directTarget())) {
            src = ensureControllerBindingNode(graph, channelId, label, true, false, -1,
                    "standard_source:" + channelId, "Configured Channel");
        }
        changed |= src != null && src.changed();

        ImportedNode output = null;
        if (isBound(binding.directTarget())) {
            output = ensureTargetGraphNode(graph, channelId, label, binding.directTarget(), false, false, -1, linkerTargets);
        }
        changed |= output != null && output.changed();
        if (src != null && output != null) {
            changed |= ensureRouteEdges(graph, src.nodeId(), output.nodeId());
        }
        return changed;
    }

    // Import the custom entry into graph
    private static boolean importCustomEntryIntoGraph(AdvancedGraphDocument graph, CustomKeyEntry entry,
                                                      List<ControllerDiscoveryNode> linkerTargets,
                                                      HolderLookup.Provider provider) {
        String bindingId = entry.id();
        String label = entry.label == null || entry.label.isBlank() ? "Custom" : entry.label;
        boolean changed = false;
        ImportedNode src = null;
        if (isBound(entry.inputTarget)) {
            src = ensureTargetGraphNode(graph, bindingId, label, entry.inputTarget, true, true,
                    entry.keyCode, linkerTargets);
        } else if (hasFrequencyPair(entry.inputFirst, entry.inputSecond)) {
            src = ensureWirelessGraphNode(graph, bindingId, label, true, true, entry.keyCode,
                    entry.inputFirst, entry.inputSecond, provider);
        } else {
            src = ensureControllerBindingNode(graph, bindingId, label, true, true, entry.keyCode,
                    "custom_source:" + bindingId, "Configured Key Input");
        }
        changed |= src != null && src.changed();

        ImportedNode output = null;
        if (isBound(entry.directTarget)) {
            output = ensureTargetGraphNode(graph, bindingId, label, entry.directTarget, false, true,
                    entry.keyCode, linkerTargets);
        } else if (hasFrequencyPair(entry.first, entry.second)) {
            output = ensureWirelessGraphNode(graph, bindingId, label, false, true, entry.keyCode,
                    entry.first, entry.second, provider);
        } else if (entry.localOutputSide != null) {
            output = ensureLocalRedstoneGraphNode(graph, bindingId, label, true, entry.keyCode,
                    entry.localOutputSide);
        }
        changed |= output != null && output.changed();
        if (src != null && output != null) {
            changed |= ensureRouteEdges(graph, src.nodeId(), output.nodeId());
        }
        return changed;
    }

    // Ensure the controller binding node
    private static ImportedNode ensureControllerBindingNode(AdvancedGraphDocument graph, String bindingId, String label,
                                                            boolean input, boolean graphOwned, int keyCode,
                                                            String seed, String nodeLabel) {
        String type = input ? "controller_channel_input" : "controller_channel_output";
        String nodeId = importNodeId(type, bindingId, seed);
        if (nodeExists(graph, nodeId)) {
            return new ImportedNode(nodeId, false);
        }
        if (graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) {
            return null;
        }
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        graph.nodes().add(new AdvancedGraphDocument.Node(nodeId, type, nodeLabel,
                input ? -260 : 120, y, data));
        return new ImportedNode(nodeId, true);
    }

    // Ensure the target graph node
    private static ImportedNode ensureTargetGraphNode(AdvancedGraphDocument graph, String bindingId, String label,
                                                      ControllerDirectTargetReference target, boolean input,
                                                      boolean graphOwned, int keyCode,
                                                      List<ControllerDiscoveryNode> linkerTargets) {
        ControllerDiscoveryNode discovery = discoveryForTarget(target, linkerTargets);
        if (discovery == null) {
            return null;
        }
        String type = targetNodeType(target, input);
        String nodeId = importNodeId(type, bindingId, target.targetId());
        if (nodeExists(graph, nodeId)) {
            return new ImportedNode(nodeId, false);
        }
        if (graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) {
            return null;
        }
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        data.putString("Target", discovery.nodeId());
        data.putString("TargetLabel", discovery.label().isBlank() ? discovery.nodeId() : discovery.label());
        data.put("TargetData", discovery.toTag());
        putGraphDefault(data, "target", "target", discovery.nodeId());
        Direction face = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(target);
        if (face != null || type.startsWith("linker_face")) {
            Direction storedFace = face == null ? Direction.NORTH : face;
            data.putString("Face", storedFace.getSerializedName());
            putGraphDefault(data, "face", "direction", storedFace.getSerializedName());
        }
        graph.nodes().add(new AdvancedGraphDocument.Node(nodeId, type,
                input ? "Linker Input" : "Linker Output", input ? -260 : 120, y, data));
        return new ImportedNode(nodeId, true);
    }

    // Ensure the wireless graph node
    private static ImportedNode ensureWirelessGraphNode(AdvancedGraphDocument graph, String bindingId, String label,
                                                         boolean input, boolean graphOwned, int keyCode,
                                                         ItemStack first, ItemStack second,
                                                         HolderLookup.Provider provider) {
        String firstId = itemId(first);
        String secondId = itemId(second);
        if (firstId.isBlank() && secondId.isBlank()) {
            return null;
        }
        String type = input ? "wireless_frequency_input" : "wireless_frequency_output";
        String nodeId = importNodeId(type, bindingId, firstId + "|" + secondId);
        if (nodeExists(graph, nodeId)) {
            return new ImportedNode(nodeId, false);
        }
        if (graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) {
            return null;
        }
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        data.putString("FrequencyFirst", firstId);
        data.putString("FrequencySecond", secondId);
        putFrequencyStack(data, "FrequencyFirst", first, provider);
        putFrequencyStack(data, "FrequencySecond", second, provider);
        putGraphDefault(data, "frequency", "frequency", firstId + "|" + secondId);
        graph.nodes().add(new AdvancedGraphDocument.Node(nodeId, type,
                input ? "Redstone Link Input" : "Redstone Link Output", input ? -260 : 120, y, data));
        return new ImportedNode(nodeId, true);
    }

    // Ensure the local redstone graph node
    private static ImportedNode ensureLocalRedstoneGraphNode(AdvancedGraphDocument graph, String bindingId, String label,
                                                             boolean graphOwned, int keyCode, Direction side) {
        String nodeId = importNodeId("local_redstone_output", bindingId, side.getSerializedName());
        if (nodeExists(graph, nodeId)) {
            return new ImportedNode(nodeId, false);
        }
        if (graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) {
            return null;
        }
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        data.putString("Face", side.getSerializedName());
        putGraphDefault(data, "face", "direction", side.getSerializedName());
        graph.nodes().add(new AdvancedGraphDocument.Node(nodeId, "local_redstone_output",
                "Local Redstone Output", 120, y, data));
        return new ImportedNode(nodeId, true);
    }

    // Get the binding node data
    private static CompoundTag bindingNodeData(String bindingId, String label, boolean graphOwned, int keyCode) {
        CompoundTag data = new CompoundTag();
        data.putString("BindingId", bindingId);
        data.putString("BindingLabel", label == null || label.isBlank() ? bindingId : label);
        if (graphOwned) {
            data.putBoolean("GraphOwnedBinding", true);
        }
        if (keyCode >= 0) {
            data.putInt("KeyCode", keyCode);
        }
        return data;
    }

    // Put the graph default
    private static void putGraphDefault(CompoundTag data, String port, String type, Object val) {
        CompoundTag defaults = data.getCompound("Defaults");
        CompoundTag entry = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        entry.putString("Type", type);
        if (val instanceof Boolean bool) {
            payload.putBoolean("Value", bool);
        } else if (val instanceof Number num) {
            payload.putDouble("Value", num.doubleValue());
        } else {
            payload.putString("Value", String.valueOf(val));
        }
        entry.put("Payload", payload);
        defaults.put(port, entry);
        data.put("Defaults", defaults);
    }

    // Ensure the route edges
    private static boolean ensureRouteEdges(AdvancedGraphDocument graph, String sourceNodeId, String outputNodeId) {
        boolean changed = false;
        changed |= ensureGraphEdge(graph, sourceNodeId, "exec", outputNodeId, "exec");
        changed |= ensureGraphEdge(graph, sourceNodeId, "value", outputNodeId, "value");
        return changed;
    }

    // Ensure the graph edge
    private static boolean ensureGraphEdge(AdvancedGraphDocument graph, String fromNode, String fromPort,
                                           String toNode, String toPort) {
        if (graph.edges().stream().anyMatch(edge -> edge.toNode().equals(toNode) && edge.toPort().equals(toPort))) {
            return false;
        }
        if (graph.edges().size() >= AdvancedGraphDocument.MAX_EDGES) {
            return false;
        }
        String seed = fromNode + "\u0000" + fromPort + "\u0000" + toNode + "\u0000" + toPort;
        String edgeId = "linker_import_edge_" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                .toString().replace('-', '_');
        graph.edges().add(new AdvancedGraphDocument.Edge(edgeId, fromNode, fromPort, toNode, toPort));
        return true;
    }

    // Get the discovery for target
    private static ControllerDiscoveryNode discoveryForTarget(ControllerDirectTargetReference target,
                                                              List<ControllerDiscoveryNode> linkerTargets) {
        if (!isBound(target)) {
            return null;
        }
        String baseNodeId = ContraptionNetworkLinkerData.baseNodeIdFromTargetId(target.targetId());
        for (ControllerDiscoveryNode node : linkerTargets) {
            if (node != null && node.nodeId().equals(baseNodeId)) {
                return node;
            }
        }
        ControllerDiscoveryKind kind = ControllerDiscoveryKind.byId(target.targetTypeId());
        return new ControllerDiscoveryNode(
                baseNodeId,
                kind == null ? ControllerDiscoveryKind.UNKNOWN : kind,
                target.groupId(),
                "",
                target.label(),
                target.subLevelId(),
                target.blockPos());
    }

    // Get the target node type
    private static String targetNodeType(ControllerDirectTargetReference target, boolean input) {
        if (input) {
            return ContraptionNetworkLinkerData.isLinkerFaceTarget(target)
                    && !ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(target)
                    ? "linker_face_input" : "discovered_target_input";
        }
        return ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(target)
                ? "linker_face_output" : "direct_target_output";
    }

    // Check if the node exists
    private static boolean nodeExists(AdvancedGraphDocument graph, String nodeId) {
        return graph.nodes().stream().anyMatch(node -> node.id().equals(nodeId));
    }

    // Get the next imported node y
    private static double nextImportedNodeY(AdvancedGraphDocument graph) {
        long importedNodes = graph.nodes().stream()
                .filter(node -> node.id().startsWith("linker_import_"))
                .count();
        return 80 + importedNodes * 96;
    }

    // Import the node id
    private static String importNodeId(String type, String bindingId, String seed) {
        String hashSeed = type + "\u0000" + bindingId + "\u0000" + seed;
        String hash = UUID.nameUUIDFromBytes(hashSeed.getBytes(StandardCharsets.UTF_8))
                .toString().replace('-', '_');
        return "linker_import_" + type + "_" + hash;
    }

    // Check if this is bound
    private static boolean isBound(@Nullable ControllerDirectTargetReference target) {
        return target != null && target.isBound();
    }

    // Check if this has frequency pair
    private static boolean hasFrequencyPair(ItemStack first, ItemStack second) {
        return (first != null && !first.isEmpty()) || (second != null && !second.isEmpty());
    }

    // Put the frequency stack
    private static void putFrequencyStack(CompoundTag data, String property, ItemStack stack,
                                          HolderLookup.Provider provider) {
        if (data == null || property == null || property.isBlank()) {
            return;
        }
        if (stack == null || stack.isEmpty() || provider == null) {
            data.remove(property + "Stack");
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        data.put(property + "Stack", copy.saveOptional(provider));
    }

    // Get the item id
    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    // Store the imported node
    private record ImportedNode(String nodeId, boolean changed) {
    }

    private static final List<String> DIRECT_AXIS_CHANNELS = List.of(
            "yaw_left", "yaw_right", "pitch_up", "pitch_down", "roll_left", "roll_right");
    private static final Set<String> DIRECTIONAL_DATA_PORTS = Set.of(
            "axis_x", "axis_z", "forward", "backward", "left", "right", "magnitude", "active");
    private static final List<String> GIMBAL_SIGNAL_PORTS = List.of(
            "north_signal", "east_signal", "south_signal", "west_signal");
    private static final String DOUBLE_BUTTON_TOP_REDSTONE_POWER = "top_redstone_power";
    private static final String DOUBLE_BUTTON_BOTTOM_REDSTONE_POWER = "bottom_redstone_power";
    private static final int MULTIBLOCK_SCAN_LIMIT = 128;

    // Get the gimbal signal direction
    private static @Nullable Direction gimbalSignalDirection(String port) {
        return switch (port) {
            case "north_signal" -> Direction.NORTH;
            case "east_signal" -> Direction.EAST;
            case "south_signal" -> Direction.SOUTH;
            case "west_signal" -> Direction.WEST;
            default -> null;
        };
    }

    // Play the graph sound
    public void playGraphSound(String nodeId, String soundId, boolean playAtLocation,
                               double x, double y, double z, double volume, double pitch,
                               boolean loop, boolean stop) {
        if (level == null || level.isClientSide) return;
        String manifest = controllerManifestId();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        String playbackId = (manifest.isBlank() ? level.dimension().location() + ":" + worldPosition.asLong() : manifest)
                + ":" + (subLevelId == null ? "world" : subLevelId) + ":" + nodeId;
        Vec3 localPosition = playAtLocation ? new Vec3(x, y, z) : worldPosition.getCenter();
        Vec3 soundPosition = SimulatedHelper.toGlobalWorldPosition(this, localPosition);
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) return;
        if (stop) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                    new GraphSoundPayload(playbackId, "", soundPosition.x, soundPosition.y, soundPosition.z,
                            0.0F, 1.0F, false, true));
            return;
        }
        if (soundId == null || soundId.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(soundId.trim());
        if (id == null) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (sound == null) return;
        float clampedVolume = (float) Math.max(0.0D, Math.min(16.0D, volume));
        float clampedPitch = (float) Math.max(0.01D, Math.min(4.0D, pitch));
        double radius = Math.max(32.0D, clampedVolume * 16.0D);
        PacketDistributor.sendToPlayersNear(serverLevel, null,
                soundPosition.x, soundPosition.y, soundPosition.z, radius,
                new GraphSoundPayload(playbackId, id.toString(),
                        soundPosition.x, soundPosition.y, soundPosition.z,
                        clampedVolume, clampedPitch, loop, false));
    }

    // Check if this supports axis control
    private static boolean supportsAxisControl(BlockEntity blockEntity) {
        return blockEntity instanceof OrientationTarget
                || blockEntity.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("gimbal");
    }

    // Get the block data snapshot
    private static CompoundTag blockDataSnapshot(Level level, BlockPos pos, BlockState state,
                                                 BlockEntity blockEntity, @Nullable Direction targetSide) {
        CompoundTag data = stateProperties(state);
        data.putString("block_id", String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())));
        data.putString("block_entity_type", blockEntity == null ? "" : String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())));
        data.putInt("x", pos.getX());
        data.putInt("y", pos.getY());
        data.putInt("z", pos.getZ());
        data.putInt("redstone_power", level.getBestNeighborSignal(pos));
        putDoubleButtonData(data, blockEntity);
        if (hasMultiblockItemHandler(level, pos, state, blockEntity)) {
            Summary items = itemSummary(level, pos, state, blockEntity, targetSide);
            data.put("items", items.entries());
            data.putInt("item_count", items.amount());
            data.putInt("item_capacity", items.capacity());
            data.putDouble("item_fill", items.fill());
        }
        if (hasMultiblockFluidHandler(level, pos, state, blockEntity)) {
            Summary fluids = fluidSummary(level, pos, state, blockEntity);
            data.put("fluids", fluids.entries());
            data.putInt("fluid_amount", fluids.amount());
            data.putInt("fluid_capacity", fluids.capacity());
            data.putDouble("fluid_fill", fluids.fill());
        }
        double[] energy = energySummary(level, pos, state, blockEntity);
        if (energy[1] > 0) {
            data.putDouble("energy", energy[0]);
            data.putDouble("energy_capacity", energy[1]);
            data.putDouble("energy_fill", energy[0] / energy[1]);
        }
        for (String field : BlockEntityDataAdapterRegistry.readableData(blockEntity).keySet()) {
            GraphValue value = BlockEntityDataAdapterRegistry.read(blockEntity, field);
            if (value != null) {
                putGraphValue(data, field, GraphRuntime.fromLibraryValue(value));
            }
        }
        for (String field : ExternalBlockEntityDirectControlCompat.readableData(blockEntity).keySet()) {
            AdvancedGraphDocument.Value val = ExternalBlockEntityDirectControlCompat.readData(blockEntity, field);
            if (val != null) putGraphValue(data, field, val);
        }
        for (String field : AeroworksControllerCompat.readableData(blockEntity).keySet()) {
            AdvancedGraphDocument.Value val = AeroworksControllerCompat.read(blockEntity, field);
            if (val != null) putGraphValue(data, field, val);
        }
        for (String field : CreateRotationSpeedControllerGraphCompat.readableData(blockEntity).keySet()) {
            AdvancedGraphDocument.Value val = CreateRotationSpeedControllerGraphCompat.read(blockEntity, field);
            if (val != null) putGraphValue(data, field, val);
        }
        for (String field : CreateFantasizingGraphCompat.readableData(blockEntity).keySet()) {
            AdvancedGraphDocument.Value val = CreateFantasizingGraphCompat.read(blockEntity, field);
            if (val != null) putGraphValue(data, field, val);
        }
        for (String field : NavigationTableGraphCompat.readableData(blockEntity).keySet()) {
            AdvancedGraphDocument.Value val = NavigationTableGraphCompat.read(blockEntity, field);
            if (val != null) putGraphValue(data, field, val);
        }
        for (String field : RailwayNavigatorGraphCompat.readableData(blockEntity).keySet()) {
            AdvancedGraphDocument.Value val = RailwayNavigatorGraphCompat.read(blockEntity, field);
            if (val != null) putGraphValue(data, field, val);
        }
        return data;
    }

    // Get the graph block data snapshot
    public static CompoundTag graphBlockDataSnapshot(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return new CompoundTag();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return new CompoundTag();
        return blockDataSnapshot(level, pos, state, level.getBlockEntity(pos), null);
    }

    // Add the double button data ports
    private static void addDoubleButtonDataPorts(CompoundTag ports, BlockEntity blockEntity) {
        if (!(blockEntity instanceof DoubleButtonBlockEntity)) return;
        ports.putString(DOUBLE_BUTTON_TOP_REDSTONE_POWER, "number");
        ports.putString(DOUBLE_BUTTON_BOTTOM_REDSTONE_POWER, "number");
    }

    // Get the double button redstone power
    @Nullable
    private static AdvancedGraphDocument.Value doubleButtonRedstonePower(BlockEntity blockEntity, String port) {
        if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)) return null;
        return switch (port) {
            case DOUBLE_BUTTON_TOP_REDSTONE_POWER -> AdvancedGraphDocument.Value.number(
                    doubleButton.getSignal(DoubleButtonBlockEntity.ButtonHalf.TOP));
            case DOUBLE_BUTTON_BOTTOM_REDSTONE_POWER -> AdvancedGraphDocument.Value.number(
                    doubleButton.getSignal(DoubleButtonBlockEntity.ButtonHalf.BOTTOM));
            default -> null;
        };
    }

    // Put the double button data
    private static void putDoubleButtonData(CompoundTag data, BlockEntity blockEntity) {
        if (!(blockEntity instanceof DoubleButtonBlockEntity doubleButton)) return;
        data.putInt(DOUBLE_BUTTON_TOP_REDSTONE_POWER, doubleButton.getSignal(DoubleButtonBlockEntity.ButtonHalf.TOP));
        data.putInt(DOUBLE_BUTTON_BOTTOM_REDSTONE_POWER, doubleButton.getSignal(DoubleButtonBlockEntity.ButtonHalf.BOTTOM));
    }

    // Put the graph value
    private static void putGraphValue(CompoundTag target, String field, AdvancedGraphDocument.Value val) {
        switch (val.type()) {
            case "boolean" -> target.putBoolean(field, val.asBoolean());
            case "number" -> target.putDouble(field, val.asNumber());
            case "string", "direction" -> target.putString(field, val.asString());
            default -> {
                CompoundTag encoded = new CompoundTag();
                encoded.putString("Type", val.type());
                encoded.put("Payload", val.payload().copy());
                target.put(field, encoded);
            }
        }
    }

    // Get the state properties
    private static CompoundTag stateProperties(BlockState state) {
        CompoundTag properties = new CompoundTag();
        for (Property<?> property : state.getProperties()) properties.putString(property.getName(), propertyValue(state, property));
        return properties;
    }

    // Get the property value
    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    // Get the value text
    private static String valueText(AdvancedGraphDocument.Value val) {
        return switch (val.type()) {
            case "boolean" -> Boolean.toString(val.asBoolean());
            case "number" -> {
                double num = val.asNumber();
                yield num == Math.rint(num) ? Long.toString(Math.round(num)) : Double.toString(num);
            }
            default -> val.asString();
        };
    }

    // Get the item summary
    private static Summary itemSummary(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                                       @Nullable Direction targetSide) {
        CompoundTag entries = new CompoundTag();
        int amount = 0;
        int capacity = 0;
        int outputEntry = 0;
        Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos candidate : connectedCapabilityPositions(level, pos, state, true)) {
            BlockState candidateState = level.getBlockState(candidate);
            BlockEntity candidateBlockEntity = level.getBlockEntity(candidate);
            IItemHandler handler = findItemHandler(level, candidate, candidateState, candidateBlockEntity,
                    candidate.equals(pos) ? targetSide : null);
            if (handler == null || !seenHandlers.add(handler)) continue;
            Summary summary = itemSummary(handler, outputEntry);
            entries.merge(summary.entries());
            amount += summary.amount();
            capacity += summary.capacity();
            outputEntry += summary.entries().size();
        }
        return new Summary(entries, amount, capacity);
    }

    // Get the fluid summary
    private static Summary fluidSummary(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        CompoundTag entries = new CompoundTag();
        int amount = 0;
        int capacity = 0;
        int outputTank = 0;
        Set<IFluidHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> seenSnapshots = new HashSet<>();
        for (BlockPos candidate : connectedCapabilityPositions(level, pos, state, false)) {
            BlockState candidateState = level.getBlockState(candidate);
            BlockEntity candidateBlockEntity = level.getBlockEntity(candidate);
            IFluidHandler handler = findFluidHandler(level, candidate, candidateState, candidateBlockEntity);
            if (handler == null || !seenHandlers.add(handler)) continue;
            Summary summary = fluidSummary(handler, outputTank);
            String snapshot = summary.capacity() + ":" + summary.amount() + ":" + summary.entries();
            if (!seenSnapshots.add(snapshot)) continue;
            entries.merge(summary.entries());
            amount += summary.amount();
            capacity += summary.capacity();
            outputTank += handler.getTanks();
        }
        return new Summary(entries, amount, capacity);
    }

    // Get the item summary
    private static Summary itemSummary(IItemHandler handler, int offset) {
        CompoundTag entries = new CompoundTag();
        int entry = offset;
        ShippingManifestBlock.ItemContents contents = ShippingManifestBlock.inspectItems(handler);
        for (ShippingManifestBlock.ManifestEntry item : contents.entries()) {
            entries.putString(Integer.toString(entry++),
                    BuiltInRegistries.ITEM.getKey(item.stack.getItem()) + " x" + item.amount);
        }
        return new Summary(entries, saturatedInt(contents.totalAmount()), saturatedInt(contents.totalCapacity()));
    }

    // Get the saturated int
    private static int saturatedInt(long val) {
        return val <= 0L ? 0 : val >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) val;
    }

    // Get the fluid summary
    private static Summary fluidSummary(IFluidHandler handler, int offset) {
        CompoundTag entries = new CompoundTag();
        int amount = 0;
        int capacity = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            var stack = handler.getFluidInTank(tank);
            capacity += handler.getTankCapacity(tank);
            amount += stack.getAmount();
            if (!stack.isEmpty()) entries.putString(Integer.toString(offset + tank),
                    BuiltInRegistries.FLUID.getKey(stack.getFluid()) + " x" + stack.getAmount());
        }
        return new Summary(entries, amount, capacity);
    }

    // Check if this has multiblock item handler
    private static boolean hasMultiblockItemHandler(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        return findItemHandler(level, pos, state, blockEntity) != null
                || connectedCapabilityPositions(level, pos, state, true).size() > 1;
    }

    // Check if this has multiblock fluid handler
    private static boolean hasMultiblockFluidHandler(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        return findFluidHandler(level, pos, state, blockEntity) != null
                || connectedCapabilityPositions(level, pos, state, false).size() > 1;
    }

    // Get the connected capability positions
    private static List<BlockPos> connectedCapabilityPositions(Level level, BlockPos start, BlockState startState, boolean items) {
        if (level == null || start == null || startState == null || !level.isLoaded(start)) return List.of();
        List<BlockPos> positions = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && positions.size() < MULTIBLOCK_SCAN_LIMIT) {
            BlockPos current = queue.remove();
            if (!level.isLoaded(current)) continue;
            BlockState state = level.getBlockState(current);
            if (state.getBlock() != startState.getBlock()) continue;
            BlockEntity blockEntity = level.getBlockEntity(current);
            boolean hasCapability = items
                    ? findItemHandler(level, current, state, blockEntity) != null
                    : findFluidHandler(level, current, state, blockEntity) != null;
            if (hasCapability) positions.add(current);
            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                if (visited.size() >= MULTIBLOCK_SCAN_LIMIT || !visited.add(next) || !level.isLoaded(next)) continue;
                if (level.getBlockState(next).getBlock() == startState.getBlock()) queue.add(next);
            }
        }
        return positions.isEmpty() && level.isLoaded(start) ? List.of(start) : positions;
    }

    // Get the energy summary
    private static double[] energySummary(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, null);
        if (storage == null) {
            for (Direction dir : Direction.values()) {
                storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, blockEntity, dir);
                if (storage != null) break;
            }
        }
        return storage == null ? new double[]{0, 0} : new double[]{storage.getEnergyStored(), storage.getMaxEnergyStored()};
    }

    // Find the item handler
    private static IItemHandler findItemHandler(Level level, BlockPos pos, BlockState state,
                                                BlockEntity blockEntity) {
        return findItemHandler(level, pos, state, blockEntity, null);
    }

    // Find the item handler
    private static IItemHandler findItemHandler(Level level, BlockPos pos, BlockState state,
                                                BlockEntity blockEntity, @Nullable Direction targetSide) {
        IItemHandler attachedHandler = null;
        if (targetSide != null) {
            attachedHandler = ShippingManifestBlock.findItemHandler(level, pos, targetSide);
        }
        List<IItemHandler> handlers = new ArrayList<>();
        Set<IItemHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Direction dir : Direction.values()) {
            IItemHandler handler = ShippingManifestBlock.findItemHandler(level, pos, dir);
            if (handler == null || !seenHandlers.add(handler)) {
                continue;
            }
            handlers.add(handler);
        }
        return ContainerItemAccess.select(attachedHandler, handlers,
                handler -> ShippingManifestBlock.inspectItems(handler).totalAmount());
    }

    // Find the fluid handler
    private static IFluidHandler findFluidHandler(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, null);
        if (handler != null) return handler;
        for (Direction dir : Direction.values()) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, blockEntity, dir);
            if (handler != null) return handler;
        }
        return null;
    }

    // Expose target
    private record TargetAccess(Level level, BlockPos pos, @Nullable Direction side) {
    }

    // Store the summary
    private record Summary(CompoundTag entries, int amount, int capacity) {
        private static final Summary EMPTY = new Summary(new CompoundTag(), 0, 0);

        // Fill the summary
        private double fill() {
            return capacity <= 0 ? 0 : amount / (double) capacity;
        }
    }

    // Get the controller manifest kind
    @Override
    protected String controllerManifestKind() {
        return "advanced_controller";
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         SERIALIZATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the additional controller manifest data
    @Override
    protected void writeAdditionalControllerManifestData(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("AdvancedDraftGraph", draftGraph.toTag());
        tag.put("AdvancedActiveGraph", activeGraph.toTag());
        tag.put(GRAPH_VERSIONS_TAG, graphVersions.toTag());
        ListTag trackerPairs = new ListTag();
        gogglesTrackerPairs.forEach((id, label) -> {
            CompoundTag pair = new CompoundTag();
            pair.putUUID("Id", id);
            pair.putString("Label", label);
            trackerPairs.add(pair);
        });
        tag.put(GOGGLES_TRACKER_PAIRS_TAG, trackerPairs);
        if (shipControlRuntime.mapId() != null) {
            tag.putUUID(SHIP_CONTROL_MAP_ID_TAG, shipControlRuntime.mapId());
        }
        tag.putString(SHIP_NAME_TAG, shipName);
        tag.putString(SHIP_FLIGHT_BEHAVIOR_TAG, shipFlightBehavior.id());
        tag.putString(SHIP_CONTROL_MODE_TAG,
                ScmControlModeRegistry.serialize(shipControlMode.id()));
        shippingScheduleRuntime.write(tag, provider);
        if (pendingServerShutdownSnapshot != null) {
            tag.put(SERVER_SHUTDOWN_SNAPSHOT_TAG,
                    pendingServerShutdownSnapshot.copy());
        }
    }

    // Remove graph-owned custom entries from schematic controller data
    private static void removeGraphOwnedCustomEntries(CompoundTag controllerData) {
        if (controllerData == null || !controllerData.contains("CustomKeyEntries", Tag.TAG_LIST)) {
            return;
        }
        ListTag stored = controllerData.getList("CustomKeyEntries", Tag.TAG_COMPOUND);
        ListTag retained = new ListTag();
        for (int idx = 0; idx < stored.size(); idx++) {
            CompoundTag entry = stored.getCompound(idx);
            String id = entry.getString("Id");
            ControllerBindingOwner owner = ControllerBindingOwner.fromId(entry.getString("Owner"));
            if (owner != ControllerBindingOwner.GRAPH && !id.startsWith("graph_")) {
                retained.add(entry.copy());
            }
        }
        controllerData.put("CustomKeyEntries", retained);
    }

    // Write the additional controller schematic payload
    @Override
    protected void writeAdditionalControllerSchematicPayload(
            CompoundTag payload,
            HolderLookup.Provider provider) {
        CompoundTag controllerData = payload.getCompound(
                ControllerSchematicPayload.CONTROLLER_DATA_TAG);
        removeGraphOwnedCustomEntries(controllerData);
        controllerData.remove(SHIP_CONTROL_MAP_ID_TAG);
        controllerData.remove(SERVER_SHUTDOWN_SNAPSHOT_TAG);
        ShippingScheduleRuntime.sanitizeSchematicPayload(controllerData);
        ShipControlMap map = storedShipControlMap();
        if (map != null) {
            payload.put(
                    SCHEMATIC_SHIP_CONTROL_MAP_TAG,
                    ShipControlMapSchematicCodec.write(map));
        }
    }

    // Remap the additional controller schematic payload
    @Override
    protected void remapAdditionalControllerSchematicPayload(
            CompoundTag payload,
            SubLevelSchematicSerializationContext context) {
        if (!payload.contains(SCHEMATIC_SHIP_CONTROL_MAP_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag remapped = ShipControlMapSchematicCodec.remap(
                payload.getCompound(SCHEMATIC_SHIP_CONTROL_MAP_TAG), context);
        if (remapped == null || remapped.isEmpty()) {
            payload.remove(SCHEMATIC_SHIP_CONTROL_MAP_TAG);
        } else {
            payload.put(SCHEMATIC_SHIP_CONTROL_MAP_TAG, remapped);
        }
    }

    // Read the additional controller schematic payload
    @Override
    protected void readAdditionalControllerSchematicPayload(
            CompoundTag payload,
            HolderLookup.Provider provider) {
        CompoundTag controllerData = payload.getCompound(
                ControllerSchematicPayload.CONTROLLER_DATA_TAG);
        controllerData.remove(SERVER_SHUTDOWN_SNAPSHOT_TAG);
        ShippingScheduleRuntime.sanitizeSchematicPayload(controllerData);
        pendingServerShutdownSnapshot = null;
        shippingScheduleRuntime.detachPilotForSchematicImport();
        cancelShipControlLoadHold();
        pendingSchematicShipControlMap =
                payload.contains(SCHEMATIC_SHIP_CONTROL_MAP_TAG, Tag.TAG_COMPOUND)
                        ? payload.getCompound(SCHEMATIC_SHIP_CONTROL_MAP_TAG).copy()
                        : null;
        shipControlRuntime.setMapId(null);
    }

    // Finalize the additional controller schematic import
    @Override
    protected boolean finalizeAdditionalControllerSchematicImport(CompoundTag payload) {
        if (pendingSchematicShipControlMap == null) {
            shipControlRuntime.setMapId(null);
            return true;
        }
        if (getLevel() == null || getLevel().isClientSide) {
            return false;
        }
        ShipControlMap map =
                ShipControlMapSchematicCodec.read(pendingSchematicShipControlMap);
        UUID containingSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (map == null || containingSubLevelId == null
                || !containingSubLevelId.equals(map.rootSubLevelId())
                || !getBlockPos().equals(map.controllerPosition())) {
            pendingSchematicShipControlMap = null;
            shipControlRuntime.setMapId(null);
            return true;
        }
        ShipControlMap rebound = ShipControlMapSchematicCodec.withDimension(
                map, getLevel().dimension().location().toString());
        if (!ShipControlMapStore.save(getLevel(), rebound)) {
            return false;
        }
        shipControlRuntime.setMapId(rebound.id());
        pendingSchematicShipControlMap = null;
        return true;
    }

    // Read the additional controller manifest data
    @Override
    protected void readAdditionalControllerManifestData(CompoundTag tag, HolderLookup.Provider provider,
                                                        String manifestKind) {
        if (tag.contains("AdvancedDraftGraph", Tag.TAG_COMPOUND)) {
            draftGraph = AdvancedGraphDocument.fromTag(tag.getCompound("AdvancedDraftGraph"));
        } else if ("base_controller".equals(manifestKind)) {
            draftGraph = ControllerManifestGraphSynthesizer.synthesize(tag, provider);
        } else {
            draftGraph = new AdvancedGraphDocument();
        }

        if (tag.contains("AdvancedActiveGraph", Tag.TAG_COMPOUND)) {
            activeGraph = AdvancedGraphDocument.fromTag(tag.getCompound("AdvancedActiveGraph"));
        } else {
            activeGraph = draftGraph.copy();
        }
        graphVersions.fromTag(tag.getList(GRAPH_VERSIONS_TAG, Tag.TAG_COMPOUND));
        gogglesTrackerPairs.clear();
        ListTag trackerPairs = tag.getList(GOGGLES_TRACKER_PAIRS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < trackerPairs.size(); idx++) {
            CompoundTag pair = trackerPairs.getCompound(idx);
            if (!pair.hasUUID("Id")) {
                continue;
            }
            String label = pair.getString("Label");
            gogglesTrackerPairs.put(pair.getUUID("Id"),
                    label.isBlank() ? "Goggles " + (gogglesTrackerPairs.size() + 1) : label);
        }
        lastGogglesTrackingSample = Long.MIN_VALUE;
        gogglesTrackingSnapshots.clear();
        shipControlRuntime.setMapId(tag.hasUUID(SHIP_CONTROL_MAP_ID_TAG)
                ? tag.getUUID(SHIP_CONTROL_MAP_ID_TAG) : null);
        String restoredShipName = tag.getString(SHIP_NAME_TAG).strip();
        shipName = restoredShipName.isBlank() ? "Unnamed Ship" : restoredShipName;
        shipFlightBehavior = ScmFlightBehavior.fromId(
                tag.getString(SHIP_FLIGHT_BEHAVIOR_TAG));
        shipControlMode = ScmControlModeRegistry.resolve(
                tag.getString(SHIP_CONTROL_MODE_TAG));
        shippingScheduleRuntime.read(tag, provider);
        pendingServerShutdownSnapshot = tag.contains(
                SERVER_SHUTDOWN_SNAPSHOT_TAG, Tag.TAG_COMPOUND)
                ? tag.getCompound(SERVER_SHUTDOWN_SNAPSHOT_TAG).copy() : null;
        removeImplicitPresetBindings(draftGraph);
        removeImplicitPresetBindings(activeGraph);
    }

    // Write the advanced contraption controller
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        if (!clientPacket) {
            shipControlRuntime.writePersistentCouplerState(tag);
        }
        ListTag logisticsRuns = new ListTag();
        for (ShipLogisticsRun run : shipLogisticsRuns) logisticsRuns.add(run.toTag());
        tag.put("ShipLogisticsRuns", logisticsRuns);
        tag.remove("WirelessStockEndpoints");
        if (clientPacket) {
            tag.remove("AdvancedDraftGraph");
            tag.remove("AdvancedActiveGraph");
            tag.remove(GRAPH_VERSIONS_TAG);
            tag.putBoolean(SHIP_INITIALIZATION_VISIBLE_TAG, shipInitializationProgressVisible);
            tag.putInt(SHIP_INITIALIZATION_PERCENT_TAG, shipInitializationProgressPercent);
            tag.putString(SHIP_INITIALIZATION_STATUS_TAG, shipInitializationProgressStatus);
            if (shipInitializationProgressViewerId != null) {
                tag.putUUID(SHIP_INITIALIZATION_VIEWER_TAG, shipInitializationProgressViewerId);
            } else {
                tag.remove(SHIP_INITIALIZATION_VIEWER_TAG);
            }
        }
    }

    // Read the advanced contraption controller
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        AdvancedGraphDocument retainedClientDraft = clientPacket ? draftGraph : null;
        AdvancedGraphDocument retainedClientActive = clientPacket ? activeGraph : null;
        super.read(tag, provider, clientPacket);
        if (!clientPacket) {
            shipControlRuntime.readPersistentCouplerState(tag);
        }
        shipLogisticsRuns.clear();
        ListTag logisticsRuns = tag.getList("ShipLogisticsRuns", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < logisticsRuns.size(); idx++) {
            ShipLogisticsRun run = ShipLogisticsRun.fromTag(logisticsRuns.getCompound(idx));
            if (run != null) shipLogisticsRuns.add(run);
        }
        if (shipLogisticsRuns.isEmpty()) {
            ListTag wireless = tag.getList("WirelessStockEndpoints", Tag.TAG_COMPOUND);
            List<ShipLogisticsRun.Endpoint> stockEndpoints = new ArrayList<>();
            List<ShipLogisticsRun.Endpoint> fuelEndpoints = new ArrayList<>();
            for (int idx = 0; idx < wireless.size(); idx++) {
                CompoundTag row = wireless.getCompound(idx);
                if (!row.hasUUID("SubLevel") || !row.contains("Pos", Tag.TAG_LONG)) continue;
                ShipLogisticsRun.Endpoint endpoint = new ShipLogisticsRun.Endpoint(
                        row.getUUID("SubLevel"), BlockPos.of(row.getLong("Pos")));
                (row.getBoolean("Fuel") ? fuelEndpoints : stockEndpoints).add(endpoint);
            }
            if (!stockEndpoints.isEmpty()) {
                shipLogisticsRuns.add(ShipLogisticsRun.create(
                        ShipLogisticsRun.ResourceType.ITEM, "Imported Item Run", stockEndpoints));
                shipLogisticsRuns.add(ShipLogisticsRun.create(
                        ShipLogisticsRun.ResourceType.FLUID, "Imported Fluid Run", stockEndpoints));
                shipLogisticsRuns.add(ShipLogisticsRun.create(
                        ShipLogisticsRun.ResourceType.ENERGY, "Imported FE Run", stockEndpoints));
            }
            if (!fuelEndpoints.isEmpty()) {
                shipLogisticsRuns.add(ShipLogisticsRun.create(
                        ShipLogisticsRun.ResourceType.FUEL, "Imported Fuel Run", fuelEndpoints));
            }
        }
        shipStockNetworkCache.invalidate();
        if (clientPacket && !tag.contains("AdvancedDraftGraph", Tag.TAG_COMPOUND)) {
            draftGraph = retainedClientDraft;
        }
        if (clientPacket && !tag.contains("AdvancedActiveGraph", Tag.TAG_COMPOUND)) {
            activeGraph = retainedClientActive;
        }
        if (clientPacket) {
            shipInitializationProgressVisible = tag.getBoolean(SHIP_INITIALIZATION_VISIBLE_TAG);
            shipInitializationProgressPercent = Mth.clamp(
                    tag.getInt(SHIP_INITIALIZATION_PERCENT_TAG), 0, 100);
            shipInitializationProgressStatus = tag.getString(SHIP_INITIALIZATION_STATUS_TAG);
            shipInitializationProgressViewerId = tag.hasUUID(SHIP_INITIALIZATION_VIEWER_TAG)
                    ? tag.getUUID(SHIP_INITIALIZATION_VIEWER_TAG) : null;
        }
        removeImplicitPresetBindings(draftGraph);
        removeImplicitPresetBindings(activeGraph);
        refreshGraphRuntime(!clientPacket);
        if (!clientPacket && serverRuntimeInitialized) {
            restoreShutdownSnapshot();
        }
    }

    // Restore the shutdown snapshot
    private void restoreShutdownSnapshot() {
        if (pendingServerShutdownSnapshot == null) {
            return;
        }
        shipControlRuntime.restoreShutdownSnapshot(
                pendingServerShutdownSnapshot.getCompound("ShipControl"));
        graphRuntime.restoreShutdownSnapshot(
                pendingServerShutdownSnapshot.getCompound("GraphRuntime"));
        pendingServerShutdownSnapshot = null;
        serverShutdownPrepared = false;
    }

    // Refresh the graph runtime
    private void refreshGraphRuntime(boolean clearRuntimeState) {
        draftGraph.removeUnusedVariables();
        activeGraph.removeUnusedVariables();
        removeImplicitPresetBindings(draftGraph);
        removeImplicitPresetBindings(activeGraph);
        if (clearRuntimeState) {
            graphRuntime.clear();
            lastGraphChannelValues.clear();
            graphLiveInputs = new LinkedHashMap<>();
            graphLiveOutputs = new LinkedHashMap<>();
            graphExecutionPulses = new LinkedHashMap<>();
            graphOwnedBindingSyncQueued = false;
        }
        graphRuntime.compile(activeGraph);
        if (clearRuntimeState) {
            boolean serverSide = getLevel() != null && !getLevel().isClientSide;
            graphOwnedBindingChannelsVerified = bindingChannelsVerifiedAfterLoad(
                    serverSide, graphRuntime.graphOwnedBindings(activeGraph).isEmpty());
        }
    }

    // Check if the binding channels are verified after load
    static boolean bindingChannelsVerifiedAfterLoad(boolean serverSide, boolean noGraphOwnedBindings) {
        return !serverSide || noGraphOwnedBindings;
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            AdvancedControllerGraphSnapshotPayload.send(
                    serverPlayer,
                    getBlockPos(),
                    SimulatedHelper.getContainingSubLevelId(this),
                    draftGraph,
                    activeGraph);
            ItemStack linker = getStoredLinker();
            ContraptionNetworkLinkerData.migrateLegacyStorage(linker);
            ContraptionNetworkLinkerSnapshotPayload.sendIfChanged(serverPlayer, linker);
        }
        return new AdvancedContraptionControllerMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.advanced_controller.config.title");
    }

    // Send the menu data
    @Override
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        super.sendToMenu(buffer);
        buffer.writeVarInt(draftGraph.revision());
        buffer.writeVarInt(activeGraph.revision());
    }

    // Remove the implicit preset bindings
    private static void removeImplicitPresetBindings(AdvancedGraphDocument graph) {
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (!node.data().getString("BindingId").isBlank()) continue;
            String legacyChannel = node.data().getString("Channel");
            for (AnalogueControlChannel preset : AnalogueControlChannel.values()) {
                if (preset.id().equals(legacyChannel)) {
                    node.data().remove("Channel");
                    break;
                }
            }
        }
    }
}
