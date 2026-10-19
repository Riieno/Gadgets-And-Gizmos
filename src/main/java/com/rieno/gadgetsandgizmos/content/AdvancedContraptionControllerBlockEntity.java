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
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.chat.ServerChatApi;
import com.rieno.gadgetsandgizmos.lib.chat.ServerTitleApi;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValueMirrors;
import com.rieno.gadgetsandgizmos.content.advanced.GraphSignalRange;
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
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPortGroups;
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
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlTelemetry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ControllerRuntimeSyncPayload;
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
import dev.simulated_team.simulated.content.blocks.altitude_sensor.AltitudeSensorBlockEntity;
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
import com.rieno.gadgetsandgizmos.lib.graph.GraphTargetPortLayout;
import com.rieno.gadgetsandgizmos.lib.graph.GraphReconciliation;
import com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagCopies;
import com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelBlockTargetApi;
import com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelTrackingApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

// Handle ACC graphs, manifests, displays and SCM control
public class AdvancedContraptionControllerBlockEntity extends AnalogueContraptionControllerBlockEntity implements dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int GRAPH_OBSERVER_SAMPLE_INTERVAL = 5;
    private static final int GRAPH_OBSERVER_EDGE_BUDGET = 32;
    private static final int GRAPH_RUNTIME_SEND_INTERVAL = 5;
    private static final int ACC_DISPLAY_UPDATE_INTERVAL = 2;
    
    private static final int ACC_DISPLAY_TARGET_RETRY_INTERVAL = 20;
private static final int GRAPH_DATA_SCHEMA_REFRESH_INTERVAL = 4;
    private static final long CLIENT_PROFILER_SAMPLE_TIMEOUT_TICKS = 40L;
    private static final String GRAPH_DATA_SCHEMA_REVISION_TAG = "GraphDataSchemaRevision";
    
    private static final String GRAPH_DATA_SCHEMA_REVISIONS_TAG = "GraphDataSchemaRevisions";
private static final String GRAPH_VERSIONS_TAG = "AdvancedGraphVersions";
    
    private static final String SHIPPING_SCHEDULE_DRAFT_GRAPH_TAG = "ShippingScheduleDraftGraph";

    private static final String SHIPPING_SCHEDULE_ACTIVE_GRAPH_TAG = "ShippingScheduleActiveGraph";
private static final String GOGGLES_TRACKER_PAIRS_TAG = "GogglesTrackerPairs";
    private static final String SHIP_CONTROL_MAP_ID_TAG = "ShipControlMapId";
    private static final String SHIP_NAME_TAG = "ShipName";
    
    private static final String SCM_PERSISTENCE_UUID_TAG = "ScmPersistenceUuid";

    private static final String SCM_DISPLAY_PROGRESS_TAG = "ScmDisplayProgress";
private static final String SHIP_FLIGHT_BEHAVIOR_TAG = "ShipFlightBehavior";
    private static final String SHIP_CONTROL_MODE_TAG = "ShipControlMode";
    
    private static final String SCM_CONFIGURATION_PROFILE_TAG = "ScmConfigurationProfile";

    private static final String SHIP_PERMISSION_PLACER_TAG = ControllerPhysicalState.PLACER;

    private static final String SHIP_PERMISSION_MODULE_PLACER_TAG = ControllerPhysicalState.MODULE_PLACER;
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
    // Renderers retain topology references while values arrive through the runtime payload
    private final com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache<
            com.rieno.gadgetsandgizmos.lib.graph.GraphReadView<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge>>
            activeGraphReadCache = new com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache<>();
    private int graphAliasRevision = -1;
    private boolean graphAliasSaveConflict;
    private boolean suppressGraphAliasPublish;
    private CompoundTag graphAliasDraft = new CompoundTag();
    private CompoundTag graphAliasActive = new CompoundTag();
    // Graph runtime
        // Hidden host-only Scratch schedule graph. It deliberately uses the same
    // document/node/edge contract as the normal ACC graph rather than a
    // second ad-hoc schedule format.
    private AdvancedGraphDocument shippingScheduleDraftGraph = new AdvancedGraphDocument();

    private AdvancedGraphDocument shippingScheduleActiveGraph = new AdvancedGraphDocument();
private final GraphRuntime graphRuntime = new GraphRuntime(this);
    private ServerChatApi.Subscription chatSubscription;
    private final com.rieno.gadgetsandgizmos.lib.plot.PlotPointTimeline plotPoints =
            new com.rieno.gadgetsandgizmos.lib.plot.PlotPointTimeline();
    private boolean legacyPlotCleanupPending;

    public void recordPlotPoint(String name, double y, String color) {
        recordPlotPoint(name, 0.0D, y,
                com.rieno.gadgetsandgizmos.lib.plot.PlotPointTimeline.parseColor(color),
                com.rieno.gadgetsandgizmos.lib.plot.PlotPointTimeline.XMode.IGNORE);
    }

    public void recordPlotPoint(String name, double x, double y, int color,
                                com.rieno.gadgetsandgizmos.lib.plot.PlotPointTimeline.XMode mode) {
        if (level == null || level.isClientSide) {
            return;
        }
        plotPoints.add(name, x, y, color, level.getGameTime(), mode);
    }

    public void resetPlotPoints(String name) {
        if (level == null || level.isClientSide) return;
        plotPoints.reset(name);
    }

    public CompoundTag plotPointData() {
        return plotPoints.toTag();
    }

    public CompoundTag plotPointData(UUID knownTimelineId, long knownRevision) {
        return plotPoints.toUpdateTag(knownTimelineId, knownRevision);
    }
    // Ship stock network cache
    private final ShipStockNetworkCache shipStockNetworkCache = new ShipStockNetworkCache(this);
    // Tracked ship logistics runs
    private final List<ShipLogisticsRun> shipLogisticsRuns = new ArrayList<>();
    // Ship control runtime
    private final ShipControlModuleRuntime shipControlRuntime = new ShipControlModuleRuntime(this);
    private ScmControlTelemetry clientScmControlTelemetry = ScmControlTelemetry.IDLE;
    private long clientScmControlTelemetryTime;
    private boolean lastScmControlSyncActive;
    // Shipping schedule runtime
    private final ShippingScheduleRuntime shippingScheduleRuntime = new ShippingScheduleRuntime(this);
    // Current ship name
        // Last shipping status sent to the client block entity. Schedule state is
    // server-authoritative, but goggles build their tooltip from the client
    // copy and otherwise keep displaying the transient load message.
    private String lastSyncedShippingScheduleStatus = "";
private String shipName = "Unnamed Ship";
    // Current ship flight behavior
        // Stable across relocation so an SCM can recover its SQLite manifest after the hull id changes.
    private UUID scmPersistenceId = UUID.randomUUID();

    private @Nullable UUID scmPersistenceSubLevelId;

    private boolean scmPersistenceRestoreAttempted;
    // Whether SCM and schedule route preparation broadcasts its progress to operators
    private boolean scmDisplayProgress;
private ScmFlightBehavior shipFlightBehavior = ScmFlightBehavior.DEFAULT;
    // Current ship control mode
    private ScmControlMode shipControlMode = ScmControlModeRegistry.resolve("airship");
    // Graph versions
        // Player-authored group, blacklist and action assignment data. It is separate
    // from the measured map so a rescan never silently overwrites player intent.
    private ScmConfigurationProfile scmConfigurationProfile = ScmConfigurationProfile.empty();
    private boolean scmChunkLoading;

    private @Nullable UUID shipPermissionPlacerId;

    private @Nullable UUID shipPermissionModulePlacerId;
private final AdvancedGraphVersionHistory graphVersions = new AdvancedGraphVersionHistory();
    // Tracked goggles tracker pairs
    private final Map<UUID, String> gogglesTrackerPairs = new LinkedHashMap<>();
    private final Map<UUID, String> gogglesTrackerItems = new LinkedHashMap<>();
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
        // Retry selected displays that were unavailable during chunk loading
    private boolean accDisplayTargetsUnavailable;
    // Last attempt to resolve the selected display targets
    private long lastAccDisplayTargetRetryTick = Long.MIN_VALUE;
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
    private Map<String, AdvancedGraphDocument.Value> clientGraphVariables = Map.of();
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
    
    private long lastShipTrackingSample = Long.MIN_VALUE;

    private final Map<String, ShipTrackingSnapshot> shipTrackingSnapshots = new LinkedHashMap<>();

    private final Map<UUID, TrackedShipMetadata> trackedShips = new LinkedHashMap<>();
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
        // Tracks whether the current manifest read came from a client update packet
    private boolean readingClientUpdatePacket;
    // Dock names synchronized for native schedule destination completions
    private List<String> shippingScheduleDestinationNames = List.of();
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
            restoreScmPersistenceIfAvailable();
            serverRuntimeInitialized = true;
            AccDisplayControllerRegistry.register(this);
            AdvancedControllerNamedEventBus.register(this);
            ensureChatListener();
            refreshGraphRuntime(true);
            restoreShutdownSnapshot();
            shipControlRuntime.resumeAfterLoad(
                    shippingScheduleRuntime.requiresControlAfterLoad());
            shippingScheduleRuntime.resumeAfterLoad();
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
        Level currentLevel = getLevel();
        ensureChatListener();
        if (legacyPlotCleanupPending && currentLevel != null && !currentLevel.isClientSide) {
            legacyPlotCleanupPending = false;
            saveControllerManifestNow();
            setChanged();
        }
        if (currentLevel != null && !currentLevel.isClientSide
                && Math.floorMod(currentLevel.getGameTime() + getBlockPos().asLong(), 20L) == 0L) {
            syncGraphAlias();
        }
        if (currentLevel != null && !currentLevel.isClientSide
                && Math.floorMod(currentLevel.getGameTime() + getBlockPos().asLong(),
                GRAPH_DATA_SCHEMA_REFRESH_INTERVAL) == 0L) {
            refreshChangedGraphDataSchemas();
        }
        restoreScmPersistenceIfAvailable();
        syncScmPersistenceSubLevel();
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.tick(this);
        shipStockNetworkCache.tick();
        shipControlRuntime.tick();
        syncScmGoggleControl();
        if (currentLevel != null && !currentLevel.isClientSide
                && Math.floorMod(currentLevel.getGameTime() + getBlockPos().asLong(), 20L) == 0L) {
            ShipPermissions.refresh(this);
        }
        syncShipInit();
        shippingScheduleRuntime.tick();
        String shippingStatus = shippingScheduleRuntime.status();
        if (!Objects.equals(lastSyncedShippingScheduleStatus, shippingStatus)) {
            lastSyncedShippingScheduleStatus = shippingStatus;
            setChanged();
            sendData();
        }
        if (getLevel() != null) {
            resetStaleMouseAxes();
            checkGraphBindings();
            flushGraphBindingSync();
            if (!getLevel().isClientSide && Math.floorMod(getLevel().getGameTime() + getBlockPos().asLong(), 10L) == 0L) {
                
            }
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
                boolean samplePassiveOutputs = !publishedAccDisplays.isEmpty()
                        || !observers.isEmpty() && Math.floorMod(
                        getLevel().getGameTime() + getBlockPos().asLong(), 4L) == 0L;
                if (graphRuntime.needsRegularTick(activeGraph, samplePassiveOutputs)
                        || graphRuntime.hasPendingWork()) {
                    graphRuntime.tick(activeGraph, samplePassiveOutputs);
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
        plotPoints.clear();
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("ShipControl", shipControlRuntime.createShutdownSnapshot());
        snapshot.put("GraphRuntime", graphRuntime.createShutdownSnapshot());
        pendingServerShutdownSnapshot = snapshot;
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.suspend(this);
        shippingScheduleRuntime.prepareForServerShutdown();
        graphRuntime.prepareForServerShutdown();
        shipControlRuntime.prepareForServerShutdown();
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
        boolean installed = shippingScheduleRuntime.install(schedule, pilotId);
        if (installed) syncShippingScheduleGraphFromRuntime();
        return installed;
    }

    // Install the shipping schedule
    public boolean installShippingSchedule(com.simibubi.create.content.trains.schedule.Schedule schedule,
                                           @Nullable UUID pilotId,
                                           ShippingAutoRefuelSettings autoRefuel) {
        boolean installed = shippingScheduleRuntime.install(schedule, pilotId, autoRefuel);
        if (installed) syncShippingScheduleGraphFromRuntime();
        return installed;
    }

    // Install the blaze burner shipping schedule
    public boolean installShippingSchedule(com.simibubi.create.content.trains.schedule.Schedule schedule,
                                           UUID pilotId,
                                           ShippingAutoRefuelSettings autoRefuel,
                                           boolean blazeBurnerPilot) {
        boolean installed = shippingScheduleRuntime.install(schedule, pilotId, autoRefuel, blazeBurnerPilot);
        if (installed) syncShippingScheduleGraphFromRuntime();
        return installed;
    }

    // Remove the shipping schedule
    public ItemStack removeShippingSchedule() {
        ItemStack schedule = shippingScheduleRuntime.remove();
        ShippingScheduleRouteData.bind(schedule, scmPersistenceId);
        return schedule;
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
        ItemStack schedule = shippingScheduleRuntime.copyScheduleItem();
        ShippingScheduleRouteData.bind(schedule, scmPersistenceId);
        return schedule;
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
        if ("shipping_start".equals(nodeType)) {
            return startShippingScheduleGraph();
        }
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
        if (CTTooltipHelper.showGoggleDetails(isPlayerSneaking)) {
            res |= addScmGoggleControl(tooltip);
        }
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

    @Override
    protected boolean showGoggleAxes(boolean showDetails) {
        return !showDetails || !scmGoggleControl().active();
    }

    private boolean addScmGoggleControl(List<Component> tooltip) {
        ScmControlTelemetry telemetry = scmGoggleControl();
        if (!telemetry.active()) return false;
        tooltip.add(CTTooltipHelper.line("SCM Autopilot",
                CTTooltipHelper.value("Correction -> Demand", ChatFormatting.GOLD)));
        for (String axis : List.of("pitch", "yaw", "roll", "throttle", "strafe", "lift")) {
            String label = axis.substring(0, 1).toUpperCase(Locale.ROOT) + axis.substring(1);
            tooltip.add(CTTooltipHelper.line(label,
                    CTTooltipHelper.value(
                            CTTooltipHelper.signedDecimal(telemetry.correction().value(axis))
                                    + " -> "
                                    + CTTooltipHelper.signedDecimal(telemetry.demand().value(axis)),
                            ChatFormatting.AQUA)));
        }
        if (Math.abs(telemetry.driveDirection()) > 0.005D) {
            tooltip.add(CTTooltipHelper.line("Drive Direction",
                    CTTooltipHelper.value(CTTooltipHelper.signedDecimal(
                            telemetry.driveDirection()), ChatFormatting.YELLOW)));
        }
        if (telemetry.acceleration() > 0.005D) {
            tooltip.add(CTTooltipHelper.line("Acceleration",
                    CTTooltipHelper.value(CTTooltipHelper.signedDecimal(
                            telemetry.acceleration()), ChatFormatting.YELLOW)));
        }
        if (telemetry.deceleration() > 0.005D) {
            tooltip.add(CTTooltipHelper.line("Deceleration",
                    CTTooltipHelper.value(CTTooltipHelper.signedDecimal(
                            telemetry.deceleration()), ChatFormatting.YELLOW)));
        }
        if (telemetry.brake() > 0.005D) {
            tooltip.add(CTTooltipHelper.line("Brake",
                    CTTooltipHelper.value(CTTooltipHelper.signedDecimal(
                            telemetry.brake()), ChatFormatting.YELLOW)));
        }
        return true;
    }

    private ScmControlTelemetry scmGoggleControl() {
        if (getLevel() != null && !getLevel().isClientSide) {
            return shipControlRuntime.goggleControlTelemetry();
        }
        return net.minecraft.Util.getMillis() - clientScmControlTelemetryTime <= 1_000L
                ? clientScmControlTelemetry : ScmControlTelemetry.IDLE;
    }

    public void applyClientScmControlTelemetry(ScmControlTelemetry telemetry) {
        if (getLevel() == null || !getLevel().isClientSide) return;
        clientScmControlTelemetry = telemetry == null ? ScmControlTelemetry.IDLE : telemetry;
        clientScmControlTelemetryTime = net.minecraft.Util.getMillis();
    }

    @Override
    protected ControllerRuntimeSyncPayload runtimeSyncPayload(UUID subLevelId) {
        return new ControllerRuntimeSyncPayload(getBlockPos(), subLevelId,
                getOmeterOutputSignal(), shipControlRuntime.goggleControlTelemetry());
    }

    private void syncScmGoggleControl() {
        if (getLevel() == null || getLevel().isClientSide) return;
        boolean active = shipControlRuntime.goggleControlTelemetry().active();
        if (!active && !lastScmControlSyncActive) return;
        if (active && Math.floorMod(getLevel().getGameTime(), 4L) != 0L) return;
        lastScmControlSyncActive = active;
        sendRuntimeData();
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
        boolean runtimeChanged = runtimeRevision != lastGraphRuntimeMirrorRevision;
        if (!observerSampleDue && !runtimeChanged) {
            return;
        }
        Map<String, AdvancedGraphDocument.Value> liveInputs = new LinkedHashMap<>(graphRuntime.liveInputs());
        Map<String, AdvancedGraphDocument.Value> liveOutputs = GraphValueMirrors.merge(
                graphRuntime.liveOutputs(), graphObserverOutputs, graphRuntime::isRetainedOutput);
        liveInputs.putAll(graphObserverInputs);
        Map<String, Long> executionPulses = graphRuntime.executionPulses();
        lastGraphRuntimeMirrorRevision = runtimeRevision;
        if (runtimeChanged || !graphLiveInputs.equals(liveInputs) || !graphLiveOutputs.equals(liveOutputs)
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
        graphRuntime.beginPreviewSample(graph);
        Map<String, AdvancedGraphDocument.Node> nodes = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            nodes.put(node.id(), node);
        }
        Set<String> sampledInputs = new HashSet<>();
        Set<String> sampledOutputs = new HashSet<>();
        // This diagnostic has no outgoing wires by design. Sample its complete
        // server-owned state so an unconnected node is still useful in the editor.
        Map<String, AdvancedGraphDocument.Value> brainValues = null;
        // Include function nodes when resolving preview values too.
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            if (!"scm_brain_debug".equals(node.type())) continue;
            if (brainValues == null) {
                brainValues = new LinkedHashMap<>();
                for (Map.Entry<String, String> output :
                        AdvancedGraphCatalog.outputs(node).entrySet()) {
                    if ("exec".equals(output.getValue())) continue;
                    brainValues.put(output.getKey(),
                            graphRuntime.previewOutput(graph, node, output.getKey()));
                }
            }
            for (Map.Entry<String, AdvancedGraphDocument.Value> output :
                    brainValues.entrySet()) {
                String outputKey = node.id() + ":" + output.getKey();
                outputs.put(outputKey, output.getValue());
                sampledOutputs.add(outputKey);
            }
        }
        if (graph.edges().isEmpty()) {
            graphObserverSampleCursor = 0;
            return;
        }
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
            UUID playerId = graphRuntime.bindingInteractionPlayer(bindingId, !active);
            graphRuntime.setBindingActive(bindingId, active, playerId);
            graphRuntime.enqueue("channel:" + bindingId, playerId);
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
                    observer.player(), getBlockPos(), subLevelId, draftGraph, activeGraph,
                    shippingScheduleDraftGraph, shippingScheduleActiveGraph);
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
            var personal = graphRuntime.playerHudValues(observer.player().getUUID());
            Map<String, AdvancedGraphLiveValue> playerInputs = new LinkedHashMap<>(liveInputs);
            Map<String, AdvancedGraphLiveValue> playerOutputs = new LinkedHashMap<>(liveOutputs);
            playerInputs.putAll(graphLiveValues(personal.inputs()));
            playerOutputs.putAll(graphLiveValues(personal.outputs()));
            Map<UUID, String> trackerPairs = trackerPairsForObserver(
                    gogglesTrackerPairs,
                    observer.pairId(),
                    observer.containerId() >= 0);
            PacketDistributor.sendToPlayer(observer.player(), new AdvancedControllerRuntimePayload(
                    getBlockPos(), subLevelId, observer.containerId(),
                    trackerPairs,
                    playerInputs, playerOutputs, graphExecutionPulses, graphRuntime.previewVariables(observer.player().getUUID()), getGogglesTrackerPairItems().entrySet().stream()
                            .filter(entry -> trackerPairs.containsKey(entry.getKey()))
                            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
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
        var personal = graphRuntime.playerHudValues(player.getUUID());
        Map<String, AdvancedGraphDocument.Value> playerInputs = new LinkedHashMap<>(graphRuntime.liveInputs());
        Map<String, AdvancedGraphDocument.Value> playerOutputs = new LinkedHashMap<>(graphRuntime.liveOutputs());
        playerInputs.putAll(personal.inputs());
        playerOutputs.putAll(personal.outputs());
        PacketDistributor.sendToPlayer(player, new AdvancedControllerRuntimePayload(
                getBlockPos(), SimulatedHelper.getContainingSubLevelId(this), containerId,
                getGogglesTrackerPairLabels(),
                graphLiveValues(playerInputs),
                graphLiveValues(playerOutputs),
                graphRuntime.executionPulses(), graphRuntime.previewVariables(player.getUUID()), getGogglesTrackerPairItems()));
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

    // Get a detached document for editing or export
    public AdvancedGraphDocument getActiveGraph() {
        return activeGraph.copy();
    }

    // Borrow applied topology on the owning thread; node data must remain read-only
    public com.rieno.gadgetsandgizmos.lib.graph.GraphReadView<AdvancedGraphDocument.Node, AdvancedGraphDocument.Edge>
    activeGraphReadView(){
        return activeGraphReadCache.get(activeGraph, activeGraph.revision(),
                () -> com.rieno.gadgetsandgizmos.lib.graph.GraphReadView.of(activeGraph));
    }

    // Get the active graph view
    AdvancedGraphDocument activeGraphView() {
        return activeGraph;
    }

    // Resolve a dynamic Target through the stored linker's authoritative names and face aliases
    public AdvancedGraphDocument.Value findNamedGraphTarget(String name){
        List<ControllerDiscoveryNode> targets = ContraptionNetworkLinkerData.findNamedTargets(
                ContraptionNetworkLinkerData.readTargets(getStoredLinker()), name);
        if(targets.size() > 1) return AdvancedGraphDocument.Value.map(
                com.rieno.gadgetsandgizmos.lib.discovery.ControllerTargetMap.encode(targets));
        return AdvancedGraphDocument.Value.target(targets.isEmpty() ? new CompoundTag() : targets.getFirst().toTag());
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

    private static boolean draftFitsStorage(AdvancedGraphDocument graph) {
        if (graph.totalEdgeCount() > AdvancedGraphDocument.MAX_EDGES) return false;
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.write(graph.toTag(), new java.io.DataOutputStream(bytes));
            return bytes.size() <= AdvancedGraphDocument.MAX_SERIALIZED_BYTES;
        } catch (java.io.IOException err) {
            return false;
        }
    }

    // Save the draft
    public boolean saveDraft(AdvancedGraphDocument graph, int expectedRevision) {
        syncGraphAlias();
        if (graph == null || expectedRevision != draftGraph.revision()) {
            return false;
        }
        if (!draftFitsStorage(graph)) return false;
        resetDeletedGraphOutputs(draftGraph, graph);
        resetDeletedGraphTargetWrites(draftGraph, graph);
        AdvancedGraphPortState.mergePersistentValues(draftGraph, graph);
        AdvancedGraphPortState.mergePersistentValues(activeGraph, graph);
        com.rieno.gadgetsandgizmos.lib.graph.GraphSaveState.mergeVariables(draftGraph.variables(), graph.variables(),
                val -> new AdvancedGraphDocument.Value(val.type(), val.payload(), val.entryOrder()));
        com.rieno.gadgetsandgizmos.lib.graph.GraphSaveState.mergeVariables(activeGraph.variables(), graph.variables(),
                val -> new AdvancedGraphDocument.Value(val.type(), val.payload(), val.entryOrder()));
        AdvancedGraphPortState.clearAuthoredEdits(graph);
        graphVersions.pushIfChanged(draftGraph, graph);
        draftGraph = graph.copy();
        draftGraph.removeUnusedVariables();
        refreshDataPorts(draftGraph);
        lastGraphObserverSample = Long.MIN_VALUE;
        draftGraph.setRevision(expectedRevision + 1);
        graphRuntime.compile(draftGraph);
        graphAliasSaveConflict = false;
        saveControllerManifestNow();
        if (graphAliasSaveConflict) return false;
        invalidateGraphPresentationCaches();
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
        syncGraphAlias();
        AdvancedGraphValidator.Result res = validateDraft();
        if (!res.valid()) {
            return false;
        }
        Map<String, Double> retainedInputValues = graphInputValues(activeGraph);
        clearGraphTargetWrites(activeGraph);
        clearGraphOutputs(activeGraph);
        clearGraphRoutedState(activeGraph);
        com.rieno.gadgetsandgizmos.lib.graph.GraphSaveState.mergeVariables(activeGraph.variables(), draftGraph.variables(),
                val -> new AdvancedGraphDocument.Value(val.type(), val.payload(), val.entryOrder()));
        graphRuntime.clearForGraphSave(draftGraph);
        lastGraphChannelValues.clear();
        activeGraph = draftGraph.copy();
        refreshDataPorts(activeGraph);
        removeImplicitPresetBindings(activeGraph);
        applyGraphBindings(activeGraph);
        graphRuntime.compile(activeGraph);
        restoreGraphInputValues(retainedInputValues);
        graphAliasSaveConflict = false;
        saveControllerManifestNow();
        if (graphAliasSaveConflict) return false;
        setChanged();
        sendData();
        graphRuntime.enqueue("applied");
        refreshGraphPresentation();
        return true;
    }

    // Drop retained layouts and force the next publication to use the saved graph
    private void invalidateGraphPresentationCaches(){
        activeGraphReadCache.invalidate();
        gogglesGraphSnapshotFingerprints.clear();
        lastGraphRuntimeObserverScan = Long.MIN_VALUE;
        lastGraphObserverSample = Long.MIN_VALUE;
        accDisplayRuntimeSnapshotRevision = Long.MIN_VALUE;
        accDisplayStructureRevision = Integer.MIN_VALUE;
        lastAccDisplayGraphRevision = Integer.MIN_VALUE;
        accDisplayTargetsDirty = true;
        accDisplayFramesDirty = true;
        for(AccDisplayBlockEntity display : List.copyOf(publishedAccDisplays)){
            if(display != null && !display.isRemoved()) display.invalidateControllerGraph(this);
        }
    }

    // Publish changed HUD topology and display widgets before returning from Save/Apply
    private void refreshGraphPresentation(){
        invalidateGraphPresentationCaches();
        if(level == null || level.isClientSide) return;
        List<GraphRuntimeObserver> observers = graphRuntimeObservers();
        syncGogglesGraphs(observers);
        publishAccDisplayUpdates();
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
        return loadSharedGraphData(snapshot.linkerData(), snapshot.id());
    }

    // Import client graph data on the server after the normal controller access checks
    public String loadClientSharedGraph(AdvancedGraphDocument graph, String name){
        if(getLevel() == null || getLevel().isClientSide) return "";
        return loadSharedGraphData(ContraptionNetworkLinkerData.sharedRootWithGraph(name, graph), name);
    }

    private String loadSharedGraphData(CompoundTag data, String name){
        List<ContraptionNetworkLinkerData.StoredGraph> storedGraphs =
                ContraptionNetworkLinkerData.readStoredGraphs(data);
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
        return "Loaded shared graph " + name;
    }

    // Handle the destroyed event
    @Override
    public void onDestroyed() {
        if (isAssemblyTransferPending()) {
            shipControlRuntime.suspendForAssemblyTransfer();
            return;
        }
        ShipPermissions.unclaim(getLevel(), getBlockPos());
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
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.remove(this);
        releaseAccDisplays();
        AccDisplayControllerRegistry.unregister(this);
        AdvancedControllerNamedEventBus.unregister(this);
        closeChatListener();
        shipStockNetworkCache.clear();
        shipControlRuntime.close();
        
        super.remove();
    }

    // Handle the chunk unloaded event
    @Override
    public void onChunkUnloaded() {
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.suspend(this);
        
        releaseAccDisplays();
        AccDisplayControllerRegistry.unregister(this);
        AdvancedControllerNamedEventBus.unregister(this);
        closeChatListener();
        shippingScheduleRuntime.suspendForChunkUnload();
        shipControlRuntime.suspendForChunkUnload();
        super.onChunkUnloaded();
    }

    // Handle the controller manifest reloaded event
    @Override
    protected void onControllerManifestReloaded() {
        refreshGraphRuntime(true);
        graphAliasRevision = -1;
        syncGraphAlias();
    }

    // Bind this controller identity to one shared graph name. An existing alias supplies its graph.
    public boolean renameGraphAlias(String requestedAlias) {
        Level level = getLevel();
        if (level == null || level.isClientSide || requestedAlias == null
                || requestedAlias.strip().length() > 64) return false;
        syncGraphAlias();
        suppressGraphAliasPublish = true;
        try {
            saveControllerManifestNow();
        } finally {
            suppressGraphAliasPublish = false;
        }
        String controllerId = controllerManifestId();
        if (controllerId.isBlank()) return false;
        ControllerSqliteStore.ControllerAliasGraph bound = ControllerSqliteStore.bindControllerAlias(
                level, controllerId, requestedAlias, draftGraph.toTag(), activeGraph.toTag());
        if (bound == null) return false;
        setCustomName(requestedAlias);
        if (bound.alias().isEmpty()) {
            graphAliasRevision = -1;
            graphAliasDraft = new CompoundTag();
            graphAliasActive = new CompoundTag();
        } else {
            adoptGraphAlias(bound);
        }
        suppressGraphAliasPublish = true;
        try {
            saveControllerManifestNow();
        } finally {
            suppressGraphAliasPublish = false;
        }
        sendGraphAliasSnapshot();
        return true;
    }

    // Pick up edits made by another controller, including after a chunk reload.
    private void syncGraphAlias() {
        Level level = getLevel();
        if (level == null || level.isClientSide || controllerManifestId().isBlank()) return;
        ControllerSqliteStore.ControllerAliasGraph shared =
                ControllerSqliteStore.controllerAliasGraph(level, controllerManifestId());
        if (shared == null) {
            graphAliasRevision = -1;
            return;
        }
        if (shared.revision() != graphAliasRevision || !shared.alias().equalsIgnoreCase(
                getCustomName() == null ? "" : getCustomName())) {
            setCustomName(shared.alias());
            adoptGraphAlias(shared);
            suppressGraphAliasPublish = true;
            try {
                saveControllerManifestNow();
            } finally {
                suppressGraphAliasPublish = false;
            }
            sendGraphAliasSnapshot();
        }
    }

    private void adoptGraphAlias(ControllerSqliteStore.ControllerAliasGraph shared) {
        graphAliasRevision = shared.revision();
        graphAliasDraft = shared.draft().copy();
        graphAliasActive = shared.active().copy();
        boolean draftChanged = !draftGraph.toTag().equals(graphAliasDraft);
        boolean activeChanged = !activeGraph.toTag().equals(graphAliasActive);
        if (!draftChanged && !activeChanged) return;
        if (draftChanged) {
            draftGraph = AdvancedGraphDocument.fromTag(graphAliasDraft);
            refreshDataPorts(draftGraph);
        }
        if (activeChanged) {
            Map<String, Double> retainedInputValues = graphInputValues(activeGraph);
            clearGraphTargetWrites(activeGraph);
            clearGraphOutputs(activeGraph);
            clearGraphRoutedState(activeGraph);
            graphRuntime.clear();
            activeGraph = AdvancedGraphDocument.fromTag(graphAliasActive);
            refreshDataPorts(activeGraph);
            applyGraphBindings(activeGraph);
            graphRuntime.compile(activeGraph);
            restoreGraphInputValues(retainedInputValues);
        }
        lastGraphObserverSample = Long.MIN_VALUE;
        setChanged();
        sendData();
    }

    private void sendGraphAliasSnapshot() {
        Level level = getLevel();
        if (level == null || level.getServer() == null) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof AdvancedContraptionControllerMenu menu
                    && menu.getMenuConfigTargetBlockEntity() == this) {
                AdvancedControllerGraphSnapshotPayload.send(player, getBlockPos(),
                        SimulatedHelper.getContainingSubLevelId(this), draftGraph, activeGraph,
                        shippingScheduleDraftGraph, shippingScheduleActiveGraph);
            }
        }
    }


    @Override
    protected void onControllerManifestSaved(ControllerManifestStore.ManifestSnapshot snapshot) {
        if (snapshot != null && graphAliasRevision >= 0 && !suppressGraphAliasPublish) {
            CompoundTag draft = draftGraph.toTag();
            CompoundTag active = activeGraph.toTag();
            if (!draft.equals(graphAliasDraft) || !active.equals(graphAliasActive)) {
                ControllerSqliteStore.ControllerAliasGraph published =
                        ControllerSqliteStore.publishControllerAliasGraph(getLevel(), snapshot.id(),
                                graphAliasRevision, draft, active);
                if (published != null) {
                    graphAliasRevision = published.revision();
                    graphAliasDraft = draft;
                    graphAliasActive = active;
                } else {
                    graphAliasSaveConflict = true;
                    graphAliasRevision = -1;
                    syncGraphAlias();
                }
            }
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (snapshot == null || subLevelId == null || scmPersistenceId == null
                || !isMountedOnShipControlModule()) {
            return;
        }
        if (ControllerSqliteStore.saveScmPersistence(
                getLevel(), subLevelId, scmPersistenceId, snapshot.id())) {
            scmPersistenceSubLevelId = subLevelId;
        }
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

    // Apply the graph bindings
    private void applyGraphBindings(AdvancedGraphDocument graph) {
        if (level != null && !level.isClientSide) {
            ControllerRedstoneCompat.clearSource(
                    level, worldPosition.asLong() + ":graph");
        }
        Map<String, GraphRouteConfig> graphOwnedRoutes = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
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
            if (currentTarget != null || isLinkerGraphTarget(targetNode)) targetNode = currentTarget;
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
        for(CustomKeyEntry entry : List.copyOf(getCustomKeyEntries())){
            if(entry.owner == ControllerBindingOwner.GRAPH && !graphOwnedRoutes.containsKey(entry.id())){
                removeRuntimeCustomKeyEntry(entry.id());
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
        if (input && ("discovered_target_input".equals(nodeType) || "linker_face_input".equals(nodeType))) {
            setChannelInputTarget(binding, target);
        } else if (output && ("direct_target_output".equals(nodeType) || "linker_face_output".equals(nodeType))) {
            setChannelDirectTarget(binding, target);
        }
        if ("local_redstone_output".equals(nodeType)) {
            setLocalOutputSide(binding, localSide);
        }
        if(("discovered_target_input".equals(nodeType) || "linker_face_input".equals(nodeType)
                || "direct_target_output".equals(nodeType) || "linker_face_output".equals(nodeType))
                && !getStoredLinker().isEmpty()){
            ContraptionNetworkLinkerData.writeChannelBinding(getStoredLinker(), binding,
                    getDirectTarget(binding), getInputTarget(binding));
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
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
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
        if (graph == null) {
            return;
        }
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            if (!isGraphTargetOutputNode(node)) {
                continue;
            }
            Set<String> resetPorts = graphTargetResetPorts(graph, node);
            if (resetPorts.isEmpty()) {
                continue;
            }
            resetGraphTargetWrite(node, resetPorts);
        }
    }

    // Reset every block target currently controlled by the active graph.
    public void resetGraphTargetOutputs() {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        clearGraphTargetWrites(activeGraph);
    }

    // Reset target writes belonging to output nodes deleted from a saved draft.
    private void resetDeletedGraphTargetWrites(AdvancedGraphDocument previous,
                                               AdvancedGraphDocument replacement) {
        for (AdvancedGraphDocument.Node previousNode : graphNodesIncludingFunctions(previous)) {
            if (!isGraphTargetOutputNode(previousNode)) continue;
            Set<String> resetPorts = new LinkedHashSet<>(
                    graphTargetResetPorts(previous, previousNode));
            if (resetPorts.isEmpty()) continue;
            for (AdvancedGraphDocument.Node replacementNode
                    : graphNodesIncludingFunctions(replacement)) {
                if (sameGraphOutputTarget(previousNode, replacementNode)) {
                    resetPorts.removeAll(graphTargetResetPorts(replacement, replacementNode));
                }
            }
            if (!resetPorts.isEmpty()) {
                resetGraphTargetWrite(previousNode, resetPorts);
            }
        }
    }

    // Reset values left by output bindings deleted from a saved draft.
    private void resetDeletedGraphOutputs(AdvancedGraphDocument previous,
                                          AdvancedGraphDocument replacement) {
        Set<String> retainedBindings = new HashSet<>();
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(replacement)) {
            if (!isGraphOutputNode(node)) continue;
            String binding = graphOutputBinding(node);
            if (!binding.isBlank()) retainedBindings.add(binding);
        }
        Map<String, Double> resetValues = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(previous)) {
            if (!isGraphOutputNode(node)) continue;
            String binding = graphOutputBinding(node);
            if (!binding.isBlank() && !retainedBindings.contains(binding)) {
                resetValues.put(binding, 0.0);
            }
        }
        if (!resetValues.isEmpty()) {
            setGraphBindingValues(resetValues, false);
        }
    }

    // Reset one graph target output to its node defaults.
    private void resetGraphTargetWrite(AdvancedGraphDocument.Node node, Set<String> ports) {
        if(level != null && !level.isClientSide && (ports.contains("direct_signal")
                || ports.contains(GraphSignalRange.REDSTONE_SIGNAL_PORT))){
            ControllerDirectTargetReference target = graphDirectTargetReference(node);
            if(target != null){
                ControllerRedstoneCompat.writeTarget(level, target, null, graphDirectSignalSourceId(node), 0);
            }
            clearGraphTargetSignals(node);
        }
        setGraphTargetData(node, ports, port -> graphTargetResetValue(node, port));
    }

    // Get a typed default for one graph target write.
    private AdvancedGraphDocument.Value graphTargetResetValue(AdvancedGraphDocument.Node node, String port) {
        if (GraphSignalRange.isRedstonePort(node, port)) {
            return AdvancedGraphDocument.Value.number(0);
        }
        CompoundTag defaults = node.data().getCompound("Defaults");
        if (defaults.contains(port, Tag.TAG_COMPOUND)) {
            CompoundTag value = defaults.getCompound(port);
            return new AdvancedGraphDocument.Value(value.getString("Type"), value.getCompound("Payload"));
        }
        CompoundTag groupedPorts = dataPortGroup(node, port);
        if (!groupedPorts.isEmpty()) {
            CompoundTag values = new CompoundTag();
            for (String field : groupedPorts.getAllKeys()) {
                values.put(field, graphValueTag(graphTargetResetValue(node, field)));
            }
            return AdvancedGraphDocument.Value.map(values);
        }
        return graphDefaultValue(AdvancedGraphCatalog.inputs(node).get(port));
    }

    // Create a neutral graph value of one port type.
    private static AdvancedGraphDocument.Value graphDefaultValue(String type) {
        if (type == null) return AdvancedGraphDocument.Value.number(0.0);
        return switch (type) {
            case "boolean" -> AdvancedGraphDocument.Value.bool(false);
            case "string" -> AdvancedGraphDocument.Value.string("");
            case "direction" -> AdvancedGraphDocument.Value.direction("");
            case "frequency" -> AdvancedGraphDocument.Value.frequency(new CompoundTag());
            case "target" -> AdvancedGraphDocument.Value.target(new CompoundTag());
            case "list" -> AdvancedGraphDocument.Value.list(new CompoundTag());
            case "map" -> AdvancedGraphDocument.Value.map(new CompoundTag());
            default -> AdvancedGraphDocument.Value.number(0.0);
        };
    }

    // Get the graph target reset ports
    static Set<String> graphTargetResetPorts(AdvancedGraphDocument graph, AdvancedGraphDocument.Node node) {
        if (graph == null || !isGraphTargetOutputNode(node)) {
            return Set.of();
        }
        Set<String> resetPorts = new LinkedHashSet<>();
        if (!"set_block_data".equals(node.type())) {
            resetPorts.add("direct_signal");
            resetPorts.addAll(DIRECT_AXIS_CHANNELS);
            return resetPorts;
        }
        CompoundTag prefilledInputs = node.data().getCompound("PrefilledInputs");
        for (AdvancedGraphDocument.Edge edge : graphEdgesForNode(graph, node)) {
            if (edge != null && node.id().equals(edge.toNode())) {
                String port = graphTargetSourcePort(node, edge.toPort());
                if (isGraphTargetWritablePort(node, port)) {
                    resetPorts.add(port);
                }
            }
        }
        for (String port : node.data().getCompound("Defaults").getAllKeys()) {
            String sourcePort = graphTargetSourcePort(node, port);
            if (!prefilledInputs.contains(port) && isGraphTargetWritablePort(node, sourcePort)) {
                resetPorts.add(sourcePort);
            }
        }
        for (String port : node.data().getCompound("ForceWriteInputs").getAllKeys()) {
            if (node.data().getCompound("ForceWriteInputs").getBoolean(port)) {
                String sourcePort = graphTargetSourcePort(node, port);
                if (isGraphTargetWritablePort(node, sourcePort)) {
                    resetPorts.add(sourcePort);
                }
            }
        }
        return resetPorts;
    }

    // Check if this node can write graph target data.
    private static boolean isGraphTargetOutputNode(AdvancedGraphDocument.Node node) {
        return node != null && ("set_block_data".equals(node.type())
                || "direct_target_output".equals(node.type())
                || "linker_face_output".equals(node.type()));
    }

    // Check if one Set Data port can write to the target.
    private static boolean isGraphTargetWritablePort(AdvancedGraphDocument.Node node, String port) {
        return port != null && !port.isBlank() && !"exec".equals(port)
                && !"target".equals(port) && !"face".equals(port)
                && !"state_waterlogged".equals(port)
                && AdvancedGraphCatalog.inputs(node).containsKey(port);
    }

    // Resolve an inline MAP child to the parent port written by the runtime.
    private static String graphTargetSourcePort(AdvancedGraphDocument.Node node, String port) {
        CompoundTag mapping = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG)
                .getCompound(port);
        String source = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG);
        return source.isBlank() ? port : source;
    }

    // Check whether two output nodes write to the same target and face.
    private static boolean sameGraphOutputTarget(AdvancedGraphDocument.Node first,
                                                 AdvancedGraphDocument.Node second) {
        return isGraphTargetOutputNode(first) && isGraphTargetOutputNode(second)
                && first.data().getCompound("TargetData")
                .equals(second.data().getCompound("TargetData"))
                && first.data().getCompound("Defaults").getCompound("face")
                .equals(second.data().getCompound("Defaults").getCompound("face"));
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
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
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
        int nextRevision = Math.max(0, draftGraph.revision()) + 1;
        AdvancedGraphDocument template = AdvancedGraphTemplates.create(templateId);
        graphVersions.pushIfChanged(draftGraph, template);
        draftGraph = template;
        draftGraph.setRevision(nextRevision);
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
        if(active){
            com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.record(getLevel(), getBlockPos(), player);
        }
        // Only controller use begins or ends the piloting session
        if("use".equals(keyPressed)){
            if(active){
                physicalInteractionParticipants.add(player.getUUID());
            }else{
                physicalInteractionParticipants.remove(player.getUUID());
            }
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

    // Subscribe while this placed or portable controller is loaded
    private void ensureChatListener(){
        if(chatSubscription != null || getLevel() == null || getLevel().isClientSide
                || getLevel().getServer() == null || serverShutdownPrepared || isRemoved()) return;
        chatSubscription = ServerChatApi.intercept(getLevel().getServer(), message -> {
            if(isRemoved() || serverShutdownPrepared || !isControllerRuntimeLoaded()) return false;
            GraphRuntime.ChatCapture captured = graphRuntime.captureChatMessage(activeGraph, message);
            if(captured.queued()) lastGraphObserverSample = Long.MIN_VALUE;
            return captured.hidden();
        });
    }

    private void closeChatListener(){
        if(chatSubscription == null) return;
        chatSubscription.close();
        chatSubscription = null;
    }

    public boolean announceGraphTitle(List<String> players, @Nullable UUID playerId, ServerTitleApi.Action action,
                                      ServerTitleApi.Text text, ServerTitleApi.Animation animation){
        if(getLevel() == null || getLevel().isClientSide || getLevel().getServer() == null) return false;
        return ServerTitleApi.send(getLevel().getServer(), players, playerId, action, text, animation);
    }

    // Apply the server gate and the controller's location before nearby delivery
    public boolean sendGraphChatMessage(String message, boolean nearby, @Nullable UUID playerId){
        return sendGraphChatMessage(message, nearby, playerId, List.of());
    }

    // Explicit player lists allow unattended logging; empty lists retain triggering-player delivery
    public boolean sendGraphChatMessage(String message, boolean nearby, @Nullable UUID playerId, List<String> players){
        if(getLevel() == null || getLevel().isClientSide || getLevel().getServer() == null) return false;
        if(nearby) return ServerChatApi.sendNearby(this, message, CTConfigs.SERVER.allowNearbyChatBroadcast.get());
        if(players != null && !players.isEmpty()) return ServerChatApi.sendToPlayers(getLevel().getServer(), players, message);
        return ServerChatApi.send(getLevel().getServer(), playerId, message, false, false);
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
        Map<UUID, String> items = getGogglesTrackerPairItems();
        gogglesTrackerPairs.forEach((id, label) -> pairs.add(new GogglesTrackerPair(id, label, items.get(id))));
        return List.copyOf(pairs);
    }

    // Get the goggles tracker pair labels
    public Map<UUID, String> getGogglesTrackerPairLabels() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(gogglesTrackerPairs));
    }

    // Preserve the paired item even when it is offline or stored; repair older bindings when worn
    public Map<UUID, String> getGogglesTrackerPairItems(){
        var server = getLevel() == null ? null : getLevel().getServer();
        if(server != null){
            for(UUID id : gogglesTrackerPairs.keySet()){
                if(gogglesTrackerItems.containsKey(id)) continue;
                var wearer = ControllerRuntimeObserver.findGogglesWearer(server, id);
                if(wearer == null) continue;
                ItemStack stack = ControllerRuntimeObserver.wornGoggles(wearer);
                if(!stack.isEmpty()){
                    gogglesTrackerItems.put(id, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    setChanged();
                }
            }
        }
        return Map.copyOf(gogglesTrackerItems);
    }

    public void applyClientGogglesTrackerItems(Map<UUID, String> items){
        if(getLevel() == null || !getLevel().isClientSide) return;
        gogglesTrackerItems.clear();
        items.forEach((id, item) -> { if(gogglesTrackerPairs.containsKey(id)) gogglesTrackerItems.put(id, item); });
    }

    public String nextGogglesTrackerPairLabel(ItemStack stack){
        if(!(stack.getItem() instanceof PilotCapItem)) return nextGogglesTrackerPairLabel();
        int idx = 1;
        while(gogglesTrackerPairs.containsValue("Pilot Hat " + idx)) idx++;
        return "Pilot Hat " + idx;
    }

    public void registerGogglesTrackerPair(UUID pairId, String label, ItemStack stack){
        if(pairId == null || stack == null || stack.isEmpty()) return;
        gogglesTrackerItems.put(pairId, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        setChanged();
        registerGogglesTrackerPair(pairId, label);
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

    // Revoke a pair immediately, including glasses that are currently offline or stored
    public boolean unregisterGogglesTrackerPair(UUID pairId){
        if(pairId == null || !gogglesTrackerPairs.containsKey(pairId)) return false;
        var observers = graphRuntimeObservers();
        gogglesTrackerPairs.remove(pairId);
        gogglesTrackerItems.remove(pairId);
        lastGogglesTrackingSample = Long.MIN_VALUE;
        gogglesTrackingSnapshots.clear();
        setChanged();
        sendData();
        sendGraphRuntimeData(observers);
        return true;
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
        GraphValue extra = tracking.telemetry().get(port == null ? "" : port);
        if (extra != null) return GraphRuntime.fromLibraryValue(extra);
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
            return null;
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
        double range = wearer instanceof Player player ? Math.max(player.blockInteractionRange(), 8.0D) : 8.0D;
        Map<String, GraphValue> telemetry = EntityTelemetryApi.sample(wearer, trackingOrigin(),
                trackingForward(), trackingUp(), getLevel().dimension().location().toString(), range);
        AdvancedGraphDocument.Value lookingAt = telemetry.get("looking_at_block").asBoolean()
                ? GraphRuntime.fromLibraryValue(telemetry.get("look_block_position")) : emptyTrackingVector();
        return new GogglesTrackingSnapshot(true, new Vec3(feet.x, feet.y, feet.z),
                wearer.level().dimension().location().toString(),
                subLevelId == null ? "" : subLevelId.toString(), wearerType,
                wearer.getDisplayName().getString(), wearer.getUUID().toString(),
                trackingVector(facing.x, facing.y, facing.z), lookingAt, telemetry);
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
    public record GogglesTrackerPair(UUID id, String label, String itemId) {
        public GogglesTrackerPair(UUID id, String label){ this(id, label, "createthrusters:physics_goggles"); }
        // Initialize the goggles tracker pair
        public GogglesTrackerPair {
            label = label == null || label.isBlank() ? "Goggles" : label;
            itemId = itemId == null || itemId.isBlank() ? "createthrusters:physics_goggles" : itemId;
        }
    }

    // Store the goggles tracking snapshot
    private record GogglesTrackingSnapshot(boolean available, Vec3 position, String dimension,
                                           String subLevel, String wearerType, String wearerName,
                                           String wearerUuid,
                                           AdvancedGraphDocument.Value playerFacing,
                                           AdvancedGraphDocument.Value lookingAt,
                                           Map<String, GraphValue> telemetry) {
        // Create an unavailable goggles tracking snapshot
        private static GogglesTrackingSnapshot unavailable() {
            return new GogglesTrackingSnapshot(false, Vec3.ZERO, "", "", "", "", "",
                    emptyTrackingVector(), emptyTrackingVector(), EntityTelemetryApi.unavailable());
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
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
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
        return NotationScmModel.from(storedShipControlMap(), scmConfigurationProfile);
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
        closeChatListener();
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

    // Preserve the player responsible for authenticated HUD input
    public boolean handleHudInteraction(ServerPlayer player, String nodeId, String interactionId, AdvancedGraphDocument.Value value){
        com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.record(getLevel(), getBlockPos(), player);
        return graphRuntime.withInteractionPlayer(player.getUUID(), () -> handleHudInteraction(nodeId, interactionId, value));
    }
    public boolean handleHudElementInteraction(ServerPlayer player, String nodeId, String interactionId, AdvancedGraphDocument.Value value){
        com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.record(getLevel(), getBlockPos(), player);
        return graphRuntime.withInteractionPlayer(player.getUUID(), () -> handleHudElementInteraction(nodeId, interactionId, value));
    }
    public boolean handleHudButtonInteraction(ServerPlayer player, String nodeId, String interactionId){
        return graphRuntime.withInteractionPlayer(player.getUUID(), () -> handleHudButtonInteraction(nodeId, interactionId));
    }
    public boolean handleHudToggleInteraction(ServerPlayer player, String nodeId, String interactionId){
        return graphRuntime.withInteractionPlayer(player.getUUID(), () -> handleHudToggleInteraction(nodeId, interactionId));
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
                case "button" -> value != null && "boolean".equals(value.type())
                        && (value.asBoolean() ? handleHudButtonInteraction(nodeId, interactionId)
                        : getLevel() != null && !getLevel().isClientSide
                        && graphRuntime.enqueueHudButtonRelease(nodeId, interactionId, getLevel().getGameTime()));
                case "toggle" -> value != null && "boolean".equals(value.type()) && value.asBoolean()
                        && handleHudToggleInteraction(nodeId, interactionId);
                case "slider" -> handleHudSliderInteraction(nodeId, interactionId, elm, value);
                case "text_input" -> value != null && "string".equals(value.type())
                        && value.asString().length() <= AdvancedHudInteractions.MAX_TEXT_INPUT_LENGTH
                        && handleHudInteraction(nodeId, interactionId, value);
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
        if(graph != null && node != null && !graph.nodes().contains(node)){
            for(var function : graph.functions()){
                if(function.nodes().contains(node)) return graphRuntime.previewFunctionInput(graph, function.id(), node, port);
            }
        }
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

    // Get complete client values for evaluating the open graph
    public Map<String, AdvancedGraphDocument.Value> getClientGraphVariablesSnapshot(){
        return Map.copyOf(clientGraphVariables);
    }

    public Map<String, AdvancedGraphDocument.Value> getClientGraphOutputsSnapshot(){
        Map<String, AdvancedGraphDocument.Value> values = new LinkedHashMap<>();
        clientGraphLiveOutputs.forEach((key, val) -> { if(val.value() != null) values.put(key, val.value()); });
        return Map.copyOf(values);
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
                if (AccDisplayBlockEntity.isShippingInformationNode(node)) {
                    publishesShipInformation = true;
                    break;
                }
            }
            accDisplayStructureRevision = graphRevision;
        }
        boolean graphChanged = graphRevision != lastAccDisplayGraphRevision;
        long gameTime = level.getGameTime();
        boolean retryTargets = accDisplayTargetsUnavailable
                && (lastAccDisplayTargetRetryTick == Long.MIN_VALUE
                || gameTime < lastAccDisplayTargetRetryTick
                || gameTime - lastAccDisplayTargetRetryTick >= ACC_DISPLAY_TARGET_RETRY_INTERVAL);
        boolean refreshTargets = graphChanged || accDisplayTargetsDirty || retryTargets;

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
            lastAccDisplayTargetRetryTick = gameTime;
        }

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
                || accDisplayFramesDirty
                // SCM telemetry changes independently from graph variables.
                // Refresh it on the existing bounded display cadence.
                || updateDue;
        if (!frameChanged && !refreshTargets) {
            return;
        }

        Map<String, AdvancedGraphDocument.Value> inputs = getAccDisplayRuntimeInputsSnapshot();
        Map<String, AdvancedGraphDocument.Value> outputs = getAccDisplayRuntimeOutputsSnapshot();
        publishDisplayUpdates(inputs, outputs);
        for (AccDisplayBlockEntity display : List.copyOf(publishedAccDisplays)) {
            if (display == null || display.isRemoved()) {
                publishedAccDisplays.remove(display);
                accDisplayTargetsDirty = true;
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
            if (node == null || (!AccDisplayBlockEntity.isShippingInformationNode(node)
                    && !"acc_display_scm_information".equals(node.type()))
                    || !AccDisplayBlockEntity.contentNodeVisible(node, inputs, outputs)) {
                continue;
            }
            UniversalDisplayAdapterBlockEntity adapter = displayAdapter(node);
            if (adapter == null) {
                continue;
            }
            CompoundTag frame = new CompoundTag();
            boolean shipping = AccDisplayBlockEntity.isShippingInformationNode(node);
            frame.putString("Source", shipping ? "ACC Display Shipping Information"
                    : "ACC Display SCM Information");
            if (shipping) {
                AccDisplayBlockEntity.populateCrnFrame(frame, this, node, inputs, outputs);
            } else {
                AccDisplayBlockEntity.populateScmFrame(frame, this, node, inputs, outputs);
            }
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
        accDisplayTargetsUnavailable = false;
        for (AdvancedGraphDocument.Node node : activeGraph.nodes()) {
            if (node == null || (!node.type().startsWith("acc_display_")
                    && !"acc_hologram_widget".equals(node.type()))) {
                continue;
            }
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(
                    node.data().getCompound("TargetData"));
            if (target == null || target.blockPos() == null) {
                continue;
            }
            TargetAccess access = cachedGraphTarget(node);
            BlockEntity blockEntity = access == null ? null : access.level().getBlockEntity(access.pos());
            if (!(blockEntity instanceof AccDisplayBlockEntity display) || display.isRemoved()) {
                if(blockEntity == null || blockEntity.isRemoved()) accDisplayTargetsUnavailable = true;
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
        applyClientGraphRuntime(trackerPairs, liveInputs, liveOutputs, executionPulses, Map.of());
    }

    public void applyClientGraphRuntime(Map<UUID, String> trackerPairs,
                                        Map<String, AdvancedGraphLiveValue> liveInputs,
                                        Map<String, AdvancedGraphLiveValue> liveOutputs,
                                        Map<String, Long> executionPulses,
                                        Map<String, AdvancedGraphDocument.Value> variables) {
        if (getLevel() == null || !getLevel().isClientSide) {
            return;
        }
        gogglesTrackerPairs.clear();
        if (trackerPairs != null) {
            gogglesTrackerPairs.putAll(trackerPairs);
        }
        clientGraphLiveInputs = new LinkedHashMap<>(liveInputs == null ? Map.of() : liveInputs);
        clientGraphLiveOutputs = new LinkedHashMap<>(liveOutputs == null ? Map.of() : liveOutputs);
        clientGraphVariables = variables == null ? Map.of() : Map.copyOf(variables);
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
        activeGraphReadCache.invalidate();
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
        List<DataTargetPortBinding> bindings = dataTargetPortBindings(node, port);
        if (GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(port) && bindings.size() > 1) {
            Double commonStrength = null;
            for (DataTargetPortBinding binding : bindings) {
                AdvancedGraphDocument.Node targetNode = dataTargetNode(node, binding.targetId());
                if (targetNode == null) return AdvancedGraphDocument.Value.number(-1);
                double strength = getGraphTargetData(targetNode, port).asNumber();
                if (commonStrength != null && commonStrength != strength) {
                    return AdvancedGraphDocument.Value.number(-1);
                }
                commonStrength = strength;
            }
            return AdvancedGraphDocument.Value.number(commonStrength == null ? 0 : commonStrength);
        }
        if (!bindings.isEmpty()) {
            DataTargetPortBinding binding = bindings.getFirst();
            AdvancedGraphDocument.Node targetNode = dataTargetNode(node, binding.targetId());
            return targetNode == null ? AdvancedGraphDocument.Value.number(0)
                    : getGraphTargetData(targetNode, binding.port());
        }
        CompoundTag groupedPorts = dataPortGroup(node, port);
        if (!groupedPorts.isEmpty()) {
            CompoundTag values = new CompoundTag();
            for (String field : groupedPorts.getAllKeys()) {
                values.put(field, graphValueTag(getGraphTargetData(node, field)));
            }
            return AdvancedGraphDocument.Value.map(values);
        }
        // Read diagram and resolved block targets
        ControllerDiscoveryNode discovery =
                ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if(ContraptionDiagramControllerCompat.isTarget(discovery)){
            ControllerDiscoveryNode current = findCurrentTarget(discovery);
            if(current != null){
                discovery = current;
                node.data().putString("Target", current.nodeId());
                node.data().put("TargetData", current.toTag());
            }
        }
        AdvancedGraphDocument.Value diagramValue =
                ContraptionDiagramControllerCompat.readTargetData(level, discovery, port);
        if (diagramValue != null) return diagramValue;

        TargetAccess target = cachedGraphTarget(node);
        if (target == null) return AdvancedGraphDocument.Value.number(0);
        BlockState state = target.level().getBlockState(target.pos());
        BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
        if ("discovered_target_input".equals(node.type()) && "value".equals(port)) {
            boolean redstone = state.isSignalSource()
                    || blockEntity instanceof com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
            if (redstone && !(blockEntity instanceof DirectionalAnalogSource)
                    && ExternalBlockEntityDirectControlCompat.sampleDirectSignal(blockEntity) == null
                    && AeroworksControllerCompat.sampleDirectSignal(blockEntity, node.data().getString("BindingId")) == null) {
                return getGraphTargetData(node, GraphSignalRange.REDSTONE_SIGNAL_PORT);
            }
            return getGraphTargetData(node, "direct_signal");
        }
        if (blockEntity instanceof AccDisplayBlockEntity display && "display_mode".equals(port)) {
            return AdvancedGraphDocument.Value.string(display.displayMode());
        }
        if ("altitude".equals(port) && blockEntity instanceof AltitudeSensorBlockEntity altitudeSensor) {
            return AdvancedGraphDocument.Value.number(altitudeSensor.getWorldHeight());
        }
        if ("distance_to_target".equals(port) && blockEntity instanceof NavTableBlockEntity navigationTable) {
            return AdvancedGraphDocument.Value.number(NavigationTableGraphData.targetDistance(
                    navigationTable, navigationTable.getTargetPosition(false)));
        }
        if ("target_coordinates".equals(port) && blockEntity instanceof NavTableBlockEntity navigationTable) {
            return NavigationTableGraphData.targetCoordinates(navigationTable);
        }
        if ("angle_to_target".equals(port) && blockEntity instanceof NavTableBlockEntity navigationTable) {
            return AdvancedGraphDocument.Value.number(navigationTable.getRelativeAngle());
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
        if (GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(port)) {
            return AdvancedGraphDocument.Value.number(Math.round(
                    ("set_block_data".equals(node.type()) || node.type().endsWith("_output")
                            ? ControllerRedstoneCompat.sampleWrittenRedstoneTarget(
                                    level, graphDirectTargetReference(node), graphRedstoneSignalSourceId(node))
                            : ControllerRedstoneCompat.sampleRedstoneTarget(level, graphDirectTargetReference(node))) * 15.0D));
        }
        if ("direct_signal".equals(port)) {
            Double signal = ExternalBlockEntityDirectControlCompat.sampleDirectSignal(blockEntity);
            if (signal != null) return AdvancedGraphDocument.Value.number(signal);
            ControllerDirectTargetReference directTarget = graphDirectTargetReference(node);
            if (directTarget != null && directTarget.isBound()) {
                return AdvancedGraphDocument.Value.number(
                        "set_block_data".equals(node.type()) || node.type().endsWith("_output")
                                ? ControllerRedstoneCompat.sampleWrittenTarget(level, directTarget, graphDirectSignalSourceId(node))
                                : ControllerRedstoneCompat.sampleTarget(level, node.data().getString("BindingId"), directTarget));
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
            case "x" -> AdvancedGraphDocument.Value.number(graphTargetWorldPosition(
                    target.level(), target.pos(), blockEntity).x);
            case "y" -> AdvancedGraphDocument.Value.number(graphTargetWorldPosition(
                    target.level(), target.pos(), blockEntity).y);
            case "z" -> AdvancedGraphDocument.Value.number(graphTargetWorldPosition(
                    target.level(), target.pos(), blockEntity).z);
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

    // Store one graph value inside a generated MAP payload
    private static CompoundTag graphValueTag(AdvancedGraphDocument.Value value) {
        AdvancedGraphDocument.Value resolved = value == null
                ? AdvancedGraphDocument.Value.number(0.0D) : value;
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", resolved.type());
        tag.put("Payload", resolved.payload().copy());
        return tag;
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
        Direction face = target.side() == null ? configuredDirection(node, "face") : target.side();
        if (face == null) {
            face = singleLinkerFace(node);
        }
        if (face == null) {
            return target.level().getBestNeighborSignal(target.pos());
        }
        return graphFaceRedstonePower(target.level(), target.pos(), state, face);
    }

    // Resolve a target's world-space centre through Sable before exposing coordinates to a graph.
    private static Vec3 graphTargetWorldPosition(Level targetLevel, BlockPos targetPos,
                                                  @Nullable BlockEntity blockEntity) {
        Vec3 localPosition = Vec3.atCenterOf(targetPos);
        return blockEntity == null
                ? SimulatedHelper.projectOutOfSubLevels(targetLevel, localPosition)
                : SimulatedHelper.toGlobalWorldPosition(blockEntity, localPosition);
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
            if (!SubLevelBlockEntityCollector.isTargetLoaded(targetLevel, null, attachedPos)) {
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
        if (node != null && activePorts != null && values != null
                && ("set_block_data".equals(node.type()) || "get_block_data".equals(node.type()))) {
            Map<String, Map<String, AdvancedGraphDocument.Value>> targetWrites = new LinkedHashMap<>();
            for (String port : activePorts) {
                if ("merge_like_ports".equals(port)) continue;
                AdvancedGraphDocument.Value value = values.apply(port);
                for (DataTargetPortBinding binding : dataTargetPortBindings(node, port)) {
                    targetWrites.computeIfAbsent(binding.targetId(), ignored -> new LinkedHashMap<>())
                            .put(binding.port(), value);
                }
            }
            if (!targetWrites.isEmpty()) {
                boolean success = true;
                for (var entry : targetWrites.entrySet()) {
                    AdvancedGraphDocument.Node targetNode = dataTargetNode(node, entry.getKey());
                    if (targetNode == null) {
                        success = false;
                        continue;
                    }
                    Map<String, AdvancedGraphDocument.Value> writes = entry.getValue();
                    success &= setGraphTargetData(targetNode, writes.keySet(), writes::get);
                }
                return success;
            }
        }
        // Validate and snapshot every requested write
        TargetAccess target = resolveGraphTarget(node);
        if (target == null || activePorts == null || values == null) return false;
        Map<String, AdvancedGraphDocument.Value> safeValues = new LinkedHashMap<>();
        Set<String> safePorts = new LinkedHashSet<>();
        for (String port : activePorts) {
            AdvancedGraphDocument.Value val = values.apply(port);
            if (GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(port) && val != null
                    && !Double.isFinite(val.asNumber())) {
                val = AdvancedGraphDocument.Value.number(GraphSignalRange.outputStrength(node, val.asNumber()));
            }
            if (!isSafeGraphWriteValue(val)) {
                return false;
            }
            CompoundTag groupedFields = dataPortGroup(node, port);
            if (!groupedFields.isEmpty()) {
                if (!"map".equals(val.type())) {
                    return false;
                }
                for (String field : groupedFields.getAllKeys()) {
                    if (!val.payload().contains(field, Tag.TAG_COMPOUND)) continue;
                    CompoundTag encodedValue = val.payload().getCompound(field);
                    AdvancedGraphDocument.Value groupedValue = new AdvancedGraphDocument.Value(
                            encodedValue.getString("Type"), encodedValue.getCompound("Payload"));
                    if (!isSafeGraphWriteValue(groupedValue)) {
                        return false;
                    }
                    safePorts.add(field);
                    safeValues.put(field, groupedValue);
                }
                continue;
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
        if("set_block_data".equals(node.type()) && safePorts.contains("direct_signal")){
            safeValues.put("direct_signal", GraphSignalRange.targetValue(
                    node, "direct_signal", safeValues.get("direct_signal")));
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
                if (!GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(port) && !writablePorts.contains(port)) {
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
        Map<String, GraphValue> registeredWrites = new LinkedHashMap<>();
        for (String port : activePorts) {
            if (registeredWritableData.containsKey(port)) {
                registeredWrites.put(port, GraphRuntime.toLibraryValue(values.apply(port)));
            }
        }
        changed |= BlockEntityDataAdapterRegistry.writeAll(blockEntity, registeredWrites);
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
        if (activePorts.contains(GraphSignalRange.REDSTONE_SIGNAL_PORT)) {
            ControllerDirectTargetReference redstoneTarget = graphDirectTargetReference(node);
            if (redstoneTarget != null && redstoneTarget.isBound()) {
                ControllerRedstoneCompat.writeRedstoneTarget(level, redstoneTarget, null,
                        graphRedstoneSignalSourceId(node),
                        GraphSignalRange.outputStrength(node, values.apply(GraphSignalRange.REDSTONE_SIGNAL_PORT).asNumber()));
                changed = true;
            }
        }
        if (activePorts.contains("direct_signal") && !nixieDirectSignalHandled
                && !CreateRotationSpeedControllerGraphCompat.hasActiveWritePort(blockEntity, activePorts)) {
            AdvancedGraphDocument.Value rawDirectSignal = values.apply(GRAPH_RAW_DIRECT_SIGNAL_PORT);
            double requestedSignal = blockEntity instanceof ThrusterBearingBlockEntity bearing
                    && bearing.getAngleMode() == ThrusterBearingBlockEntity.AngleMode.SWIVEL
                    && rawDirectSignal != null ? rawDirectSignal.asNumber()
                    : values.apply("direct_signal").asNumber();
            float clampedSignal = (float) net.minecraft.util.Mth.clamp(requestedSignal,
                    blockEntity instanceof ThrusterBearingBlockEntity bearing
                            && bearing.getAngleMode() == ThrusterBearingBlockEntity.AngleMode.SWIVEL
                            ? -1.0D : 0.0D, 1.0D);
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

    // Set Data is an assignment: another setter for the same target replaces its previous signal
    private String graphRedstoneSignalSourceId(AdvancedGraphDocument.Node node){
        if(node == null || !"set_block_data".equals(node.type())) return graphDirectSignalSourceId(node);
        ControllerDirectTargetReference target = graphDirectTargetReference(node);
        if(target == null || target.blockPos() == null) return graphDirectSignalSourceId(node);
        return worldPosition.asLong() + ":graph:set_data:" + target.subLevelId() + ":" + target.blockPos().asLong() + ":"
                + ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(target) + ":"
                + ContraptionNetworkLinkerData.isFaceProxyId(target.targetId());
    }

    // Clear both continuous node outputs and imperative Set Data assignments
    private void clearGraphTargetSignals(AdvancedGraphDocument.Node node){
        ControllerRedstoneCompat.clearSource(level, graphDirectSignalSourceId(node));
        if(node == null || !"set_block_data".equals(node.type())) return;
        if(node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND).isEmpty()){
            ControllerRedstoneCompat.clearSource(level, graphRedstoneSignalSourceId(node));
            return;
        }
        for(Tag raw : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)){
            if(!(raw instanceof CompoundTag tag)) continue;
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(tag);
            AdvancedGraphDocument.Node scoped = target == null ? null : dataTargetNode(node, target.nodeId());
            if(scoped != null) ControllerRedstoneCompat.clearSource(level, graphRedstoneSignalSourceId(scoped));
        }
    }

    // Get the graph direct signal source id
    private String graphDirectSignalSourceId(AdvancedGraphDocument.Node node) {
        String nodeId = node == null || node.id() == null ? "unknown" : node.id();
        return worldPosition.asLong() + ":graph:output:" + nodeId;
    }

    // Get the graph direct target reference
    private static @Nullable ControllerDirectTargetReference graphDirectTargetReference(AdvancedGraphDocument.Node node) {
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if (discovery == null) {
            return null;
        }
        Direction face = selectedDataTargetFace(node, discovery);
        return ContraptionNetworkLinkerData.directTargetForFace(discovery,
                face == null ? configuredDirection(node, "face") : face);
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

    // Refresh graph data schemas whose target provider changed
    private void refreshChangedGraphDataSchemas() {
        boolean activeChanged = graphDataSchemaChanged(activeGraph);
        boolean draftChanged = graphDataSchemaChanged(draftGraph);
        if (!activeChanged && !draftChanged) return;
        if (activeChanged) refreshDataPorts(activeGraph);
        if (draftChanged) refreshDataPorts(draftGraph);
        setChanged();
        sendData();
    }

    // Check whether one graph has a changed target data schema
    private boolean graphDataSchemaChanged(AdvancedGraphDocument graph) {
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            if (!"get_block_data".equals(node.type()) && !"set_block_data".equals(node.type())) continue;
            TargetAccess target = resolveGraphTarget(node);
            if(!node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND).isEmpty()){
                CompoundTag revisions = multiDataTargetSchemaRevisions(node);
                if(revisions != null && !revisions.equals(node.data().getCompound(GRAPH_DATA_SCHEMA_REVISIONS_TAG))){
                    return true;
                }
                continue;
            }
            if (target == null) continue;
            BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
            if (!BlockEntityDataAdapterRegistry.isDataSchemaReady(blockEntity)) continue;
            long revision = BlockEntityDataAdapterRegistry.dataSchemaRevision(blockEntity);
            if (!node.data().contains(GRAPH_DATA_SCHEMA_REVISION_TAG, Tag.TAG_LONG)
                    || node.data().getLong(GRAPH_DATA_SCHEMA_REVISION_TAG) != revision) {
                return true;
            }
        }
        return false;
    }

    // Refresh the data ports
    private void refreshDataPorts(AdvancedGraphDocument graph) {
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            boolean getData = "get_block_data".equals(node.type());
            boolean setData = "set_block_data".equals(node.type());
            boolean directInput = "discovered_target_input".equals(node.type()) || "linker_face_input".equals(node.type());
            boolean directOutput = "direct_target_output".equals(node.type()) || "linker_face_output".equals(node.type());
            if (!getData && !setData && !directInput && !directOutput) continue;
            if((getData || setData) && graphEdgesForNode(graph, node).stream()
                    .anyMatch(edge -> node.id().equals(edge.toNode()) && "target".equals(edge.toPort()))){
                com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphTargets.apply(level, node,
                        previewGraphInput(graph, node, "target"), graphEdgesForNode(graph, node));
            }
            if ((getData || setData) && !node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG,
                    Tag.TAG_COMPOUND).isEmpty()) {
                refreshMultiDataTargetPorts(node);
                com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphTargetSignals.upgrade(node, graphEdgesForNode(graph, node));
                continue;
            }
            ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(
                    node.data().getCompound("TargetData"));
            boolean writable = setData || directOutput;
            configureDataTargetFaceOptions(node, discovery);
            if (ContraptionDiagramControllerCompat.isTarget(discovery)) {
                CompoundTag ports = getData && !writable
                        ? ContraptionDiagramControllerCompat.readablePorts()
                        : new CompoundTag();
                clearDataPortGroups(node);
                restoreInlineMapPorts(node, ports, !writable);
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
            if ((getData || setData) && !BlockEntityDataAdapterRegistry.isDataSchemaReady(blockEntity)) {
                continue;
            }
            String aeroworksSection = getData || setData
                    ? configureAeroworksGraphSection(node, blockEntity)
                    : null;
            CompoundTag ports = getData || setData
                    ? graphDataPorts(target.level(), target.pos(), writable, aeroworksSection)
                    : graphDirectAxisPorts(blockEntity, writable);
            if (getData || setData) {
                ports = configureDataPortGroups(node, ports, blockEntity, writable);
            } else {
                clearDataPortGroups(node);
            }
            restoreInlineMapPorts(node, ports, !writable);
            CompoundTag labels = getData && !writable
                    ? graphReadableDataPortLabels(target.level(), target.pos())
                    : new CompoundTag();
            node.data().remove("OutputLabels");
            node.data().put(writable ? "DynamicInputs" : "DynamicOutputs", ports);
            node.data().put(writable ? "InputOptions" : "OutputOptions",
                    graphDataPortOptions(target.level(), target.pos(), writable));
            if (writable) {
                restoreInlineMapInputOptions(node);
            }
            if (getData || setData) {
                node.data().putLong(GRAPH_DATA_SCHEMA_REVISION_TAG,
                        BlockEntityDataAdapterRegistry.dataSchemaRevision(blockEntity));
            }
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

    // Keep valid inline MAP breakout ports when a target refresh replaces its derived port schema.
    public static void restoreInlineMapPorts(AdvancedGraphDocument.Node node, CompoundTag ports,
                                             boolean output) {
        if (node == null || ports == null) {
            return;
        }
        String mappingsKey = output ? AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG
                : AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG;
        String dynamicPortsKey = output ? "DynamicOutputs" : "DynamicInputs";
        CompoundTag mappings = node.data().getCompound(mappingsKey);
        if (mappings.isEmpty()) {
            return;
        }
        CompoundTag previousPorts = node.data().getCompound(dynamicPortsKey);
        CompoundTag retainedMappings = new CompoundTag();
        for (String inlinePort : mappings.getAllKeys()) {
            CompoundTag mapping = mappings.getCompound(inlinePort);
            String source = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG);
            String key = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG);
            if (inlinePort.isBlank() || source.isBlank() || key.isBlank()
                    || inlinePort.equals(source) || !"map".equals(ports.getString(source))) {
                continue;
            }
            String type = dataPortGroup(node, source).getString(key);
            if (type.isBlank()) {
                type = previousPorts.getString(inlinePort);
            }
            if (type.isBlank() || "exec".equals(type)) {
                continue;
            }
            ports.putString(inlinePort, type);
            retainedMappings.put(inlinePort, mapping.copy());
        }
        if (retainedMappings.isEmpty()) {
            node.data().remove(mappingsKey);
        } else {
            node.data().put(mappingsKey, retainedMappings);
        }
    }

    // Restore selectable values for retained inline MAP input ports
    public static void restoreInlineMapInputOptions(AdvancedGraphDocument.Node node) {
        if (node == null) return;
        CompoundTag mappings = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG);
        if (mappings.isEmpty()) return;
        CompoundTag options = node.data().getCompound("InputOptions").copy();
        for (String inlinePort : mappings.getAllKeys()) {
            CompoundTag mapping = mappings.getCompound(inlinePort);
            String source = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG);
            String key = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG);
            if (source.isBlank() || key.isBlank()
                    || !dataPortGroup(node, source).contains(key)
                    || !options.contains(key, Tag.TAG_LIST)) {
                options.remove(inlinePort);
                continue;
            }
            options.put(inlinePort, options.get(key).copy());
            normalizeInlineMapOptionDefault(node, inlinePort,
                    dataPortGroup(node, source).getString(key), options.getList(key, Tag.TAG_STRING));
        }
        if (options.isEmpty()) node.data().remove("InputOptions");
        else node.data().put("InputOptions", options);
    }

    // Repair defaults created before typed MAP fields retained their value type
    private static void normalizeInlineMapOptionDefault(AdvancedGraphDocument.Node node, String port,
                                                        String type, ListTag options) {
        if (!"string".equals(type) || options == null || options.isEmpty()) return;
        CompoundTag defaults = node.data().getCompound("Defaults");
        String current = defaults.getCompound(port).getCompound("Payload").getString("Value");
        for (int index = 0; index < options.size(); index++) {
            if (options.getString(index).equals(current)) return;
        }
        String selected = options.getString(0);
        for (int index = 0; index < options.size(); index++) {
            String candidate = options.getString(index);
            if (!candidate.isBlank() && current.contains(candidate)) {
                selected = candidate;
                break;
            }
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("Value", selected);
        CompoundTag encoded = new CompoundTag();
        encoded.putString("Type", "string");
        encoded.put("Payload", payload);
        defaults.put(port, encoded);
        node.data().put("Defaults", defaults);
        AdvancedGraphPortState.updatePersistentValueTag(node, port, false, encoded);
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
        if (configured == null || !directions.contains(configured)) {
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

        List<Direction> directions = isPickedGraphTarget(target) ? List.of(Direction.values()) : target == null
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
            Direction selected = selectedDataTargetFace(node, target);
            Direction configured = selected == null ? configuredDirection(node, "face") : selected;
            if (configured == null || !directions.contains(configured)) {
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
                outputs = com.rieno.gadgetsandgizmos.lib.graph.GraphDynamicPortSchema.retainConnectedOutputs(
                        outputs, node.data().getCompound("DynamicOutputs"), graphEdgesForNode(graph, node).stream()
                                .filter(edge -> edge.fromNode().equals(node.id()))
                                .map(AdvancedGraphDocument.Edge::fromPort).toList());
                if (outputs.isEmpty()) {
                    node.data().remove("DynamicOutputs");
                } else {
                    node.data().put("DynamicOutputs", outputs);
                }
            }
            if ("list_get".equals(node.type())) {
                AdvancedGraphDocument.Value val = graphRuntime.previewOutput(graph, node, "value");
                CompoundTag outputs = node.data().getCompound("DynamicOutputs");
                boolean wired = graphEdgesForNode(graph, node).stream().anyMatch(edge ->
                        edge.fromNode().equals(node.id()) && "value".equals(edge.fromPort()));
                if (!wired || !outputs.contains("value")) outputs.putString("value", val.type());
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
        if (level == null || pos == null || !SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)) return labels;
        LinkedTypewriterGraphCompat.readableLabels(level.getBlockEntity(pos)).forEach(labels::putString);
        return labels;
    }

    // Get the graph data ports
    public static CompoundTag graphDataPorts(Level level, BlockPos pos, boolean writable,
                                             @Nullable String aeroworksSectionId) {
        // Resolve the target and its block state ports
        CompoundTag ports = new CompoundTag();
        if (level == null || pos == null || !SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)) return ports;
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
            boolean hasSpecificControlSchema = BlockEntityDataAdapterRegistry.hasPrimaryWritableData(blockEntity);
            Map<String, String> externalPorts = ExternalBlockEntityDirectControlCompat.writableData(blockEntity);
            externalPorts.forEach(ports::putString);
            hasSpecificControlSchema |= !externalPorts.isEmpty();
            if (blockEntity instanceof ThrusterBearingBlockEntity) {
                ports.putString("direct_signal", "number");
            }
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
            ports.putString("angle_to_target", "number");
        }
        if (blockEntity instanceof AltitudeSensorBlockEntity) {
            ports.putString("altitude", "number");
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

    // Add generated MAP ports while retaining the underlying data-port schema
    public static CompoundTag configureDataPortGroups(AdvancedGraphDocument.Node node, CompoundTag ports) {
        return configureDataPortGroups(node, ports, Map.of());
    }

    // Add generated MAP ports and provider-defined groups
    public static CompoundTag configureDataPortGroups(AdvancedGraphDocument.Node node, CompoundTag ports,
                                                      BlockEntity blockEntity, boolean writable) {
        if(writable) configureGraphInputVisibility(node, blockEntity, "");
        Map<String, Map<String, String>> groups = writable
                ? BlockEntityDataAdapterRegistry.writableDataPortGroups(blockEntity)
                : BlockEntityDataAdapterRegistry.readableDataPortGroups(blockEntity);
        return configureDataPortGroups(node, ports, groups);
    }

    // Capture reusable provider conditions alongside the target's port schema
    private static void configureGraphInputVisibility(AdvancedGraphDocument.Node node, BlockEntity blockEntity, String target){
        if(node == null) return;
        com.rieno.gadgetsandgizmos.lib.graph.GraphInputVisibility.configure(node.data(), target,
                blockEntity instanceof com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider provider
                        ? provider.graphWritableInputConditions() : Map.of());
    }

    // Add generated MAP ports and the supplied explicit groups
    public static CompoundTag configureDataPortGroups(AdvancedGraphDocument.Node node, CompoundTag ports,
                                                      Map<String, Map<String, String>> explicitGroups) {
        CompoundTag resolved = ports == null ? new CompoundTag() : ports.copy();
        if (node == null || (!"get_block_data".equals(node.type())
                && !"set_block_data".equals(node.type()))) {
            clearDataPortGroups(node);
            return resolved;
        }

        Map<String, String> rawPorts = new LinkedHashMap<>();
        resolved.getAllKeys().forEach(port -> rawPorts.put(port, resolved.getString(port)));
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        Set<String> explicitlyGroupedPorts = new HashSet<>();
        if (explicitGroups != null) {
            explicitGroups.forEach((group, entries) -> {
                if (group == null || group.isBlank() || entries == null || entries.isEmpty()) return;
                Map<String, String> fields = new LinkedHashMap<>();
                entries.forEach((port, type) -> {
                    if (rawPorts.containsKey(port) && explicitlyGroupedPorts.add(port)) {
                        fields.put(port, rawPorts.get(port));
                    }
                });
                if (!fields.isEmpty()) {
                    groups.put(group, fields);
                }
            });
        }
        Map<String, Map<String, String>> inferredGroups = BlockEntityDataPortGroups.group(rawPorts);
        inferredGroups.forEach((group, entries) -> {
            boolean overlapsExplicitGroup = entries.keySet().stream().anyMatch(explicitlyGroupedPorts::contains);
            if (!overlapsExplicitGroup) {
                groups.putIfAbsent(group, entries);
            }
        });
        if (groups.isEmpty()) {
            clearDataPortGroups(node);
            return resolved;
        }

        CompoundTag schemas = new CompoundTag();
        groups.forEach((group, entries) -> {
            CompoundTag fields = new CompoundTag();
            entries.forEach(fields::putString);
            schemas.put(group, fields);
            resolved.putString(group, "map");
        });
        node.data().put(AdvancedGraphCatalog.DATA_PORT_GROUPS_TAG, schemas);
        return resolved;
    }

    // Get one generated MAP port schema
    public static CompoundTag dataPortGroup(@Nullable AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null || port.isBlank()) {
            return new CompoundTag();
        }
        return AdvancedGraphCatalog.dataPortGroups(node)
                .getCompound(port).copy();
    }

    // Find the generated MAP port which owns one retained data port
    public static String dataPortGroupFor(@Nullable AdvancedGraphDocument.Node node, String port) {
        if (node == null || port == null || port.isBlank()) {
            return "";
        }
        CompoundTag groups = AdvancedGraphCatalog.dataPortGroups(node);
        for (String group : groups.getAllKeys()) {
            if (groups.getCompound(group).contains(port)) {
                return group;
            }
        }
        return "";
    }

    // Remove stale generated MAP schemas
    public static void clearDataPortGroups(@Nullable AdvancedGraphDocument.Node node) {
        if (node != null) {
            node.data().remove(AdvancedGraphCatalog.DATA_PORT_GROUPS_TAG);
        }
    }

    // Check if this is a graph container target
    public static boolean isGraphContainerTarget(Level level, BlockPos pos) {
        if (level == null || pos == null || !SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)) return false;
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
        if (level == null || pos == null || !SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)) return opts;
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
        if(isLinkerGraphTarget(discovery)){
            ControllerDiscoveryNode current = findCurrentTarget(discovery);
            if(current == null || !graphTargetFaceAvailable(node, current)) return null;
            discovery = current;
            node.data().putString("Target", current.nodeId());
            node.data().put("TargetData", current.toTag());
        }
        ControllerDirectTargetReference resolvedReference =
                ControllerRedstoneCompat.resolveMovingTarget(level, isPickedGraphTarget(discovery)
                        ? discovery.asDirectTargetReference().withFace(selectedDataTargetFace(node, discovery))
                        : discovery.asDirectTargetReference());
        if(isPickedGraphTarget(discovery) && resolvedReference == null) return null;
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
            updatePickedGraphTargetLocation(node, discovery);
        }
        BlockEntity blockEntity =
                SimulatedHelper.findLoadedBlockEntityExact(level, discovery.subLevelId(), discovery.blockPos());
        Level targetLevel = blockEntity != null && blockEntity.getLevel() != null
                ? blockEntity.getLevel()
                : SubLevelBlockEntityCollector.resolveTargetLevel(level, discovery.subLevelId());
        boolean targetLoaded = blockEntity != null || SubLevelBlockEntityCollector.isTargetLoaded(
                level, discovery.subLevelId(), discovery.blockPos());
        if (!targetLoaded || targetLevel == null) {
            return null;
        }
        if (blockEntity == null && targetLevel.getBlockState(discovery.blockPos()).isAir()
                && !linkedFaceSupportPresent(discovery)) {
            ControllerDiscoveryNode current = findCurrentTarget(discovery);
            if (current != null && current.blockPos() != null) {
                node.data().putString("Target", current.nodeId());
                node.data().putString("TargetLabel", current.label().isBlank() ? current.nodeId() : current.label());
                node.data().put("TargetData", current.toTag());
                discovery = current;
                blockEntity =
                        SimulatedHelper.findLoadedBlockEntityExact(
                                level, discovery.subLevelId(), discovery.blockPos());
                targetLevel = blockEntity != null && blockEntity.getLevel() != null
                        ? blockEntity.getLevel()
                        : SubLevelBlockEntityCollector.resolveTargetLevel(level, discovery.subLevelId());
                targetLoaded = blockEntity != null || SubLevelBlockEntityCollector.isTargetLoaded(
                        level, discovery.subLevelId(), discovery.blockPos());
            }
        }
        if (!targetLoaded || targetLevel == null) {
            return null;
        }
        BlockPos targetPos = blockEntity != null ? blockEntity.getBlockPos() : discovery.blockPos();
        Direction face = isPickedGraphTarget(discovery)
                ? SubLevelBlockTargetApi.resolveFace(level, resolvedReference) : configuredDirection(node, "face");
        TargetAccess target = new TargetAccess(targetLevel, targetPos, face);
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
        if (node == null || target == null) return target;
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if(discovery == null || !ContraptionNetworkLinkerData.nodeUsesFaceOptions(discovery)
                || discovery.kind() != ControllerDiscoveryKind.LINKER_FACE_INPUT
                && discovery.kind() != ControllerDiscoveryKind.LINKER_FACE_OUTPUT){
            return target;
        }
        if(!target.pos().equals(discovery.blockPos())) return target;
        if (!(target.level().getBlockState(target.pos()).getBlock()
                instanceof ContraptionNetworkLinkerPlaneBlock)
                && !"createthrusters:contraption_network_linker_plane".equalsIgnoreCase(discovery.blockId())) return target;
        Direction side = selectedDataTargetFace(node, discovery);
        if (side == null) {
            return null;
        }
        BlockPos attachedPos = target.pos().relative(side.getOpposite());
        if (!SubLevelBlockEntityCollector.isTargetLoaded(target.level(), null, attachedPos)
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
        ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if(discovery == null || !ContraptionNetworkLinkerData.nodeUsesFaceOptions(discovery)
                || discovery.kind() != ControllerDiscoveryKind.LINKER_FACE_INPUT
                && discovery.kind() != ControllerDiscoveryKind.LINKER_FACE_OUTPUT){
            return target;
        }
        if(!target.pos().equals(discovery.blockPos())) return target;
        Direction side = target.side() == null ? singleLinkerFace(node) : target.side();
        if (side == null) {
            return target;
        }
        BlockPos attachedPos = target.pos().relative(side.getOpposite());
        if (!SubLevelBlockEntityCollector.isTargetLoaded(target.level(), null, attachedPos)
                || target.level().getBlockState(attachedPos).isAir()) {
            return target;
        }
        return new TargetAccess(target.level(), attachedPos, side);
    }

    // Find the current target
    private ControllerDiscoveryNode findCurrentTarget(ControllerDiscoveryNode stale) {
        if (stale == null || level == null) return null;
        if(isLinkerGraphTarget(stale)){
            ControllerDirectTargetReference moved = ControllerRedstoneCompat.resolveMovingTarget(level, stale.asDirectTargetReference());
            if(moved != null && moved.blockPos() != null){
                stale = new ControllerDiscoveryNode(ContraptionNetworkLinkerData.baseNodeIdFromTargetId(moved.targetId()),
                        stale.kind(), moved.groupId(), stale.blockId(), stale.label(), moved.subLevelId(), moved.blockPos());
            }
        }
        ControllerDiscoveryNode sameLocation = null;
        boolean ambiguousSameLocation = false;
        ControllerDiscoveryNode exact = null;
        ControllerDiscoveryNode fallback = null;
        boolean linked = isLinkerGraphTarget(stale);
        List<ControllerDiscoveryNode> candidates = linked
                ? ContraptionNetworkLinkerData.toDiscoveryNodes(getStoredLinker()) : getAssignableTargets();
        for (ControllerDiscoveryNode candidate : candidates) {
            if (!linked && !graphTargetExists(candidate)) continue;
            if (candidate.nodeId().equals(stale.nodeId())) {
                exact = candidate;
                continue;
            }
            if ((linked || candidate.kind() == stale.kind())
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
            if (linked || !candidate.blockId().equals(stale.blockId()) || !candidate.label().equals(stale.label())) continue;
            if (fallback != null) return null;
            fallback = candidate;
        }
        ControllerDiscoveryNode res = exact != null ? exact
                : sameLocation != null && !ambiguousSameLocation ? sameLocation : fallback;
        return res;
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
        return loadedGraphTargetBlockExists(candidate);
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
        return loadedGraphTargetBlockExists(candidate);
    }

    // Check a block-only target in the level that actually owns it
    private boolean loadedGraphTargetBlockExists(ControllerDiscoveryNode candidate) {
        if (!SubLevelBlockEntityCollector.isTargetLoaded(
                level, candidate.subLevelId(), candidate.blockPos())) {
            return false;
        }
        Level targetLevel = SubLevelBlockEntityCollector.resolveTargetLevel(level, candidate.subLevelId());
        return targetLevel != null && !targetLevel.getBlockState(candidate.blockPos()).isAir();
    }

    // Refresh the current target
    private ControllerDiscoveryNode refreshCurrentTarget(ControllerDiscoveryNode stale) {
        if (stale == null || stale.blockPos() == null || level == null) return null;
        if(isLinkerGraphTarget(stale)) return findCurrentTarget(stale);
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
        if(linkedFaceSupportPresent(stale)) return stale;
        ControllerDiscoveryNode current = findCurrentTarget(stale);
        if (current != null && !current.equals(stale)) {
            return current;
        }
        return storedGraphTargetExists(stale) ? stale : current;
    }

    // Update graph-owned target records after the linker tracker has persisted
    // a Sable assembly/disassembly remap.
    @Override
    public void onTrackedLinkerDataUpdated() {
        super.onTrackedLinkerDataUpdated();
        if (getLevel() != null && !getLevel().isClientSide) {
            reconcileGraphTargets();
        }
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
            graphRuntime.compile(activeGraph);
        }
        if (draftChanged) {
            refreshDataPorts(draftGraph);
        }
        saveControllerManifestNow();
        setChanged();
        sendData();
    }

    // Get the graph output values
    private Map<String, Double> graphOutputValues(AdvancedGraphDocument graph) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
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
        if(getStoredLinker().isEmpty()) return false;
        List<ControllerDiscoveryNode> linkerTargets = graphLinkerTargets(getStoredLinker());
        boolean changed = false;
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
            ControllerDiscoveryNode source = ControllerDiscoveryNode.fromTag(node.data().getCompound("SourceData"));
            if(isLinkerGraphTarget(source)){
                ControllerDiscoveryNode currentSource = retainedCurrentLinkerGraphTarget(node, source, linkerTargets);
                if(currentSource == null){
                    node.data().remove("SourceData");
                    node.data().remove("Source");
                    node.data().remove("SourceLabel");
                    node.data().remove("SourceFace");
                    node.data().getCompound("Defaults").remove("source");
                    changed = true;
                }else if(!currentSource.equals(source)){
                    remapGraphTargetSelection(node, source, currentSource);
                    node.data().put("SourceData", currentSource.toTag());
                    node.data().putString("Source", currentSource.nodeId());
                    node.data().putString("SourceLabel", currentSource.label());
                    changed = true;
                }
            }
            ListTag targets = node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND);
            ListTag retainedTargets = new ListTag();
            for(Tag raw : targets){
                ControllerDiscoveryNode selected = ControllerDiscoveryNode.fromTag((CompoundTag) raw);
                ControllerDiscoveryNode currentSelected = isLinkerGraphTarget(selected)
                        ? retainedCurrentLinkerGraphTarget(node, selected, linkerTargets) : selected;
                if(currentSelected != null){
                    if(!currentSelected.equals(selected)) remapGraphTargetSelection(node, selected, currentSelected);
                    retainedTargets.add(currentSelected.toTag());
                }
            }
            if(!targets.equals(retainedTargets)){
                node.data().put(AdvancedGraphCatalog.DATA_TARGETS_TAG, retainedTargets);
                changed = true;
            }
            
            
            
            ControllerDiscoveryNode stale = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
            ControllerDiscoveryNode current = isLinkerGraphTarget(stale)
                    ? retainedCurrentLinkerGraphTarget(node, stale, linkerTargets) : refreshCurrentTarget(stale);
            if(isLinkerGraphTarget(stale) && current == null){
                if(isGraphTargetOutputNode(node)) resetGraphTargetWrite(node, graphTargetResetPorts(graph, node));
                clearGraphTargetSignals(node);
                node.data().remove("Target");
                node.data().remove("TargetLabel");
                node.data().remove("TargetData");
                node.data().getCompound("Defaults").remove("target");
                changed = true;
                continue;
            }
            if (current != null && !current.equals(stale)) {
                remapGraphTargetSelection(node, stale, current);
                node.data().putString("Target", current.nodeId());
                node.data().putString("TargetLabel", current.label().isBlank() ? current.nodeId() : current.label());
                node.data().put("TargetData", current.toTag());
                changed = true;
            }
        }
        if(changed) graph.setRevision(graph.revision() + 1);
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
        if (getLevel() == null || getLevel().isClientSide || linker == null) {
            return;
        }
        graphTargetAccessCache.clear();
        missingGraphTargetAccessCache.clear();
        graphTargetAccessCacheTick = Long.MIN_VALUE;
        
        for(AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(activeGraph)){
            if(hasUnavailableLinkerGraphTarget(node, linker.isEmpty())){
                clearGraphTargetSignals(node);
            }
        }
        if(linker.isEmpty()){
            applyGraphBindings(activeGraph);
            graphRuntime.compile(activeGraph);
            queueGraphBindingSync();
            setChanged();
            sendData();
            return;
        }
        boolean changed = importInsertedLinkerBindings(linker, forceGraphReplace);
        reconcileGraphTargets();
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
        if(linker.isEmpty()) return false;
        if (forceGraphReplace) {
            ContraptionNetworkLinkerData.StoredGraph storedGraph =
                    ContraptionNetworkLinkerData.readSelectedStoredGraph(linker);
            if (storedGraph != null && !storedGraph.graphTag().isEmpty()) {
                return replaceGraphFromStoredLinker(storedGraph, true);
            }
        }
        List<ControllerDiscoveryNode> linkerTargets = graphLinkerTargets(linker);
        HolderLookup.Provider provider = getLevel().registryAccess();
        AdvancedGraphDocument prev = activeGraph.copy();
        boolean draftChanged = importLinkerBindingsIntoGraph(draftGraph, linker, linkerTargets, provider);
        boolean activeChanged = importLinkerBindingsIntoGraph(activeGraph, linker, linkerTargets, provider);
        if(activeChanged){
            resetDeletedGraphTargetWrites(prev, activeGraph);
            resetDeletedGraphOutputs(prev, activeGraph);
        }
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
        return importLinkerBindingsIntoGraph(graph, linker, graphLinkerTargets(linker), provider);
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
        for (AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)) {
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
        AdvancedGraphDocument desired = new AdvancedGraphDocument();
        populateLinkerBindings(desired, channelBindings, customEntryBindings, linkerTargets, provider);
        Set<String> retained = new HashSet<>();
        desired.nodes().forEach(node -> retained.add(importedBindingKey(node)));
        Set<String> retainedBindings = new HashSet<>();
        for(AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)){
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
            if(node.data().getBoolean("ImportedLinkerBinding") && isLinkerGraphTarget(target)
                    && retainedLinkerGraphTarget(node, target, linkerTargets) != null){
                retained.add(importedBindingKey(node));
                retainedBindings.add(node.data().getString("BindingId"));
            }
        }
        for(AdvancedGraphDocument.Node node : graphNodesIncludingFunctions(graph)){
            if(node.data().getBoolean("ImportedLinkerBinding") && !node.data().contains("TargetData")
                    && retainedBindings.contains(node.data().getString("BindingId"))) retained.add(importedBindingKey(node));
        }
        Set<String> seen = new HashSet<>();
        boolean changed = !GraphReconciliation.removeNodes(graph, node ->
                node.data().getBoolean("ImportedLinkerBinding")
                        && (!retained.contains(importedBindingKey(node)) || !seen.add(importedBindingKey(node)))).isEmpty();
        for(AdvancedGraphDocument.FunctionGraph function : graph.functions()){
            changed |= !GraphReconciliation.removeNodes(function, node ->
                    node.data().getBoolean("ImportedLinkerBinding")
                            && !retained.contains(importedBindingKey(node))).isEmpty();
        }
        return populateLinkerBindings(graph, channelBindings, customEntryBindings, linkerTargets, provider) || changed;
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
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        return upsertImportedNode(graph, new AdvancedGraphDocument.Node(nodeId, type, nodeLabel,
                input ? -260 : 120, y, data));
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
        return upsertImportedNode(graph, new AdvancedGraphDocument.Node(nodeId, type,
                input ? "Linker Input" : "Linker Output", input ? -260 : 120, y, data));
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
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        data.putString("FrequencyFirst", firstId);
        data.putString("FrequencySecond", secondId);
        putFrequencyStack(data, "FrequencyFirst", first, provider);
        putFrequencyStack(data, "FrequencySecond", second, provider);
        putGraphDefault(data, "frequency", "frequency", firstId + "|" + secondId);
        return upsertImportedNode(graph, new AdvancedGraphDocument.Node(nodeId, type,
                input ? "Redstone Link Input" : "Redstone Link Output", input ? -260 : 120, y, data));
    }

    // Ensure the local redstone graph node
    private static ImportedNode ensureLocalRedstoneGraphNode(AdvancedGraphDocument graph, String bindingId, String label,
                                                             boolean graphOwned, int keyCode, Direction side) {
        String nodeId = importNodeId("local_redstone_output", bindingId, side.getSerializedName());
        double y = nextImportedNodeY(graph);
        CompoundTag data = bindingNodeData(bindingId, label, graphOwned, keyCode);
        data.putBoolean("ImportedLinkerBinding", true);
        data.putString("Face", side.getSerializedName());
        putGraphDefault(data, "face", "direction", side.getSerializedName());
        return upsertImportedNode(graph, new AdvancedGraphDocument.Node(nodeId, "local_redstone_output",
                "Local Redstone Output", 120, y, data));
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
                Direction face = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(target);
                if(ContraptionNetworkLinkerData.nodeUsesFaceOptions(node) && face != null
                        && ContraptionNetworkLinkerData.directTargetForFace(node, face) == null) return null;
                return node;
            }
        }
        if(ContraptionNetworkLinkerData.isLinkerFaceTarget(target)){
            ControllerDiscoveryKind kind = ControllerDiscoveryKind.byId(target.targetTypeId());
            ControllerDiscoveryNode stale = new ControllerDiscoveryNode(baseNodeId,
                    kind == null ? ControllerDiscoveryKind.UNKNOWN : kind, target.groupId(), "", target.label(),
                    target.subLevelId(), target.blockPos());
            ControllerDiscoveryNode current = matchingLinkerTarget(stale, linkerTargets);
            Direction face = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(target);
            if(current == null || face != null && ContraptionNetworkLinkerData.nodeUsesFaceOptions(current)
                    && ContraptionNetworkLinkerData.directTargetForFace(current, face) == null) return null;
            return current;
        }
        if(ContraptionNetworkLinkerData.isLinkerFaceTarget(target)) return null;
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
        Vec3 worldPosition = graphTargetWorldPosition(level, pos, blockEntity);
        data.putDouble("x", worldPosition.x);
        data.putDouble("y", worldPosition.y);
        data.putDouble("z", worldPosition.z);
        data.putInt("redstone_power", level.getBestNeighborSignal(pos));
        if (blockEntity instanceof AltitudeSensorBlockEntity altitudeSensor) {
            data.putDouble("altitude", altitudeSensor.getWorldHeight());
        }
        if (blockEntity instanceof NavTableBlockEntity navigationTable) {
            data.putDouble("distance_to_target", NavigationTableGraphData.targetDistance(
                    navigationTable, navigationTable.getTargetPosition(false)));
            putGraphValue(data, "target_coordinates",
                    NavigationTableGraphData.targetCoordinates(navigationTable));
            data.putDouble("angle_to_target", navigationTable.getRelativeAngle());
        }
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
        if (level == null || pos == null || !SubLevelBlockEntityCollector.isTargetLoaded(level, null, pos)) return new CompoundTag();
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
        tag.remove("PlotPoints");
        tag.put(SHIPPING_SCHEDULE_DRAFT_GRAPH_TAG, shippingScheduleDraftGraph.toTag());
        tag.put(SHIPPING_SCHEDULE_ACTIVE_GRAPH_TAG, shippingScheduleActiveGraph.toTag());
        tag.put(GRAPH_VERSIONS_TAG, graphVersions.toTag());
        ListTag trackerPairs = new ListTag();
        gogglesTrackerPairs.forEach((id, label) -> {
            CompoundTag pair = new CompoundTag();
            pair.putUUID("Id", id);
            pair.putString("Label", label);
            pair.putString("ItemId", gogglesTrackerItems.getOrDefault(id, ""));
            trackerPairs.add(pair);
        });
        tag.put(GOGGLES_TRACKER_PAIRS_TAG, trackerPairs);
        if (shipControlRuntime.mapId() != null) {
            tag.putUUID(SHIP_CONTROL_MAP_ID_TAG, shipControlRuntime.mapId());
        }
        tag.putString(SHIP_NAME_TAG, shipName);
        tag.putUUID(SCM_PERSISTENCE_UUID_TAG, scmPersistenceId);
        
        tag.putBoolean(SCM_DISPLAY_PROGRESS_TAG, scmDisplayProgress);
        tag.putString(SHIP_FLIGHT_BEHAVIOR_TAG, shipFlightBehavior.id());
        tag.putString(SHIP_CONTROL_MODE_TAG,
                ScmControlModeRegistry.serialize(shipControlMode.id()));
        tag.put(SCM_CONFIGURATION_PROFILE_TAG, scmConfigurationProfile.toTag());
        tag.putBoolean("ScmChunkLoading", scmChunkLoading);
        shippingScheduleRuntime.write(tag, provider);
        shipControlRuntime.writePrecalculatedRouteCache(tag);
        if (pendingServerShutdownSnapshot != null) {
            tag.put(SERVER_SHUTDOWN_SNAPSHOT_TAG,
                    pendingServerShutdownSnapshot.copy());
        }else if(serverRuntimeInitialized && getLevel() != null && !getLevel().isClientSide
                && shipControlRuntime.mapId() != null){
            // Chunk saves need the same command and graph recovery state as a clean shutdown.
            CompoundTag snapshot = new CompoundTag();
            snapshot.put("ShipControl", shipControlRuntime.createShutdownSnapshot());
            snapshot.put("GraphRuntime", graphRuntime.createShutdownSnapshot());
            tag.put(SERVER_SHUTDOWN_SNAPSHOT_TAG, snapshot);
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
        controllerData.remove(SCM_PERSISTENCE_UUID_TAG);
        controllerData.remove("ScmPrecalculatedRoutes");
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
        plotPoints.clear();
        if (tag.contains("PlotPoints")) {
            tag.remove("PlotPoints");
            legacyPlotCleanupPending = true;
        }
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
        if (tag.contains(SHIPPING_SCHEDULE_DRAFT_GRAPH_TAG, Tag.TAG_COMPOUND)) {
            shippingScheduleDraftGraph = AdvancedGraphDocument.fromTag(
                    tag.getCompound(SHIPPING_SCHEDULE_DRAFT_GRAPH_TAG));
        } else {
            shippingScheduleDraftGraph = new AdvancedGraphDocument();
        }
        if (tag.contains(SHIPPING_SCHEDULE_ACTIVE_GRAPH_TAG, Tag.TAG_COMPOUND)) {
            shippingScheduleActiveGraph = AdvancedGraphDocument.fromTag(
                    tag.getCompound(SHIPPING_SCHEDULE_ACTIVE_GRAPH_TAG));
        } else {
            shippingScheduleActiveGraph = shippingScheduleDraftGraph.copy();
        }
        graphVersions.fromTag(tag.getList(GRAPH_VERSIONS_TAG, Tag.TAG_COMPOUND));
        gogglesTrackerPairs.clear();
        gogglesTrackerItems.clear();
        ListTag trackerPairs = tag.getList(GOGGLES_TRACKER_PAIRS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < trackerPairs.size(); idx++) {
            CompoundTag pair = trackerPairs.getCompound(idx);
            if (!pair.hasUUID("Id")) {
                continue;
            }
            String label = pair.getString("Label");
            if(!pair.getString("ItemId").isBlank()) gogglesTrackerItems.put(pair.getUUID("Id"), pair.getString("ItemId"));
            gogglesTrackerPairs.put(pair.getUUID("Id"),
                    label.isBlank() ? "Goggles " + (gogglesTrackerPairs.size() + 1) : label);
        }
        lastGogglesTrackingSample = Long.MIN_VALUE;
        gogglesTrackingSnapshots.clear();
        shipControlRuntime.setMapId(tag.hasUUID(SHIP_CONTROL_MAP_ID_TAG)
                ? tag.getUUID(SHIP_CONTROL_MAP_ID_TAG) : null);
        scmPersistenceId = tag.hasUUID(SCM_PERSISTENCE_UUID_TAG)
                ? tag.getUUID(SCM_PERSISTENCE_UUID_TAG) : scmPersistenceId;
        
        String restoredShipName = tag.getString(SHIP_NAME_TAG).strip();
        shipName = restoredShipName.isBlank() ? "Unnamed Ship" : restoredShipName;
        scmDisplayProgress = tag.getBoolean(SCM_DISPLAY_PROGRESS_TAG);
        shipControlRuntime.setDisplayProgressPreference(scmDisplayProgress);
        shipFlightBehavior = ScmFlightBehavior.fromId(
                tag.getString(SHIP_FLIGHT_BEHAVIOR_TAG));
        shipControlMode = ScmControlModeRegistry.resolve(
                tag.getString(SHIP_CONTROL_MODE_TAG));
        // Existing saved controllers retained their ships before this preference existed
        if(tag.contains("ScmChunkLoading")) scmChunkLoading = tag.getBoolean("ScmChunkLoading");
        else if(!readingClientUpdatePacket) scmChunkLoading = true;
        scmConfigurationProfile = tag.contains(SCM_CONFIGURATION_PROFILE_TAG, Tag.TAG_COMPOUND)
                ? ScmConfigurationProfile.fromTag(tag.getCompound(SCM_CONFIGURATION_PROFILE_TAG))
                : ScmConfigurationProfile.empty();
        shipPermissionPlacerId = tag.hasUUID(SHIP_PERMISSION_PLACER_TAG)
                ? tag.getUUID(SHIP_PERMISSION_PLACER_TAG) : null;
        shipPermissionModulePlacerId = tag.hasUUID(SHIP_PERMISSION_MODULE_PLACER_TAG)
                ? tag.getUUID(SHIP_PERMISSION_MODULE_PLACER_TAG) : null;
        shippingScheduleRuntime.read(tag, provider, !readingClientUpdatePacket);
        shipControlRuntime.readPrecalculatedRouteCache(tag);
        if (!readingClientUpdatePacket && shippingScheduleDraftGraph.nodes().isEmpty()
                && shippingScheduleRuntime.hasSchedule()) {
            syncShippingScheduleGraphFromRuntime();
        }
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
        tag.put("FlightControl", com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.save(this));
        if (!clientPacket) {
            shipControlRuntime.writePersistentCouplerState(tag);
        }
        ListTag logistics = new ListTag();
        for(ShipLogisticsRun run : shipLogisticsRuns) logistics.add(run.toTag());
        tag.put("ShipLogisticsRuns", logistics);
        tag.remove("WirelessStockEndpoints");
        if (clientPacket) {
            ListTag destinations = new ListTag();
            for(String name : shippingScheduleDestinationNames(ResourceLocation.fromNamespaceAndPath("create", "destination"))){
                destinations.add(net.minecraft.nbt.StringTag.valueOf(name));
            }
            tag.put("ShippingScheduleDestinations", destinations);
            tag.remove("AdvancedDraftGraph");
            tag.remove("AdvancedActiveGraph");
            tag.remove(SHIPPING_SCHEDULE_DRAFT_GRAPH_TAG);
            tag.remove(SHIPPING_SCHEDULE_ACTIVE_GRAPH_TAG);
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
        AdvancedGraphDocument retainedClientScheduleDraft = clientPacket ? shippingScheduleDraftGraph : null;
        AdvancedGraphDocument retainedClientScheduleActive = clientPacket ? shippingScheduleActiveGraph : null;
        boolean previousClientUpdateRead = readingClientUpdatePacket;
        readingClientUpdatePacket = clientPacket;
        try {
            super.read(tag, provider, clientPacket);
            com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.load(this, tag.getCompound("FlightControl"));
        } finally {
            readingClientUpdatePacket = previousClientUpdateRead;
        }
        if (!clientPacket) {
            shipControlRuntime.readPersistentCouplerState(tag);
            shipLogisticsRuns.clear();
            ListTag logistics = tag.getList("ShipLogisticsRuns", Tag.TAG_COMPOUND);
            for(int idx = 0; idx < logistics.size(); idx++){
                ShipLogisticsRun run = ShipLogisticsRun.fromTag(logistics.getCompound(idx));
                if(run != null) shipLogisticsRuns.add(run);
            }
            if(shipLogisticsRuns.isEmpty()){
                List<ShipLogisticsRun.Endpoint> stock = new ArrayList<>();
                List<ShipLogisticsRun.Endpoint> fuel = new ArrayList<>();
                ListTag legacy = tag.getList("WirelessStockEndpoints", Tag.TAG_COMPOUND);
                for(int idx = 0; idx < legacy.size(); idx++){
                    CompoundTag entry = legacy.getCompound(idx);
                    ShipLogisticsRun.Endpoint endpoint = ShipLogisticsRun.Endpoint.fromTag(entry);
                    if(endpoint != null) (entry.getBoolean("Fuel") ? fuel : stock).add(endpoint);
                }
                if(!stock.isEmpty()){
                    shipLogisticsRuns.add(ShipLogisticsRun.create(ShipLogisticsRun.ResourceType.ITEM, "Imported Item Run", stock));
                    shipLogisticsRuns.add(ShipLogisticsRun.create(ShipLogisticsRun.ResourceType.FLUID, "Imported Fluid Run", stock));
                    shipLogisticsRuns.add(ShipLogisticsRun.create(ShipLogisticsRun.ResourceType.ENERGY, "Imported FE Run", stock));
                }
                if(!fuel.isEmpty()) shipLogisticsRuns.add(ShipLogisticsRun.create(ShipLogisticsRun.ResourceType.FUEL, "Imported Fuel Run", fuel));
            }
            shipStockNetworkCache.invalidate();
        }
        if (clientPacket && !tag.contains("AdvancedDraftGraph", Tag.TAG_COMPOUND)) {
            draftGraph = retainedClientDraft;
        }
        if (clientPacket && !tag.contains("AdvancedActiveGraph", Tag.TAG_COMPOUND)) {
            activeGraph = retainedClientActive;
        }
        if (clientPacket && !tag.contains(SHIPPING_SCHEDULE_DRAFT_GRAPH_TAG, Tag.TAG_COMPOUND)) {
            shippingScheduleDraftGraph = retainedClientScheduleDraft;
        }
        if (clientPacket && !tag.contains(SHIPPING_SCHEDULE_ACTIVE_GRAPH_TAG, Tag.TAG_COMPOUND)) {
            shippingScheduleActiveGraph = retainedClientScheduleActive;
        }
        if (clientPacket) {
            shippingScheduleDestinationNames = tag.getList("ShippingScheduleDestinations", Tag.TAG_STRING)
                    .stream().map(Tag::getAsString).toList();
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
        if(isVirtual()) return;
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
        hydrateShippingScheduleGraphFromRuntime();
        if (player instanceof ServerPlayer serverPlayer) {
            AdvancedControllerGraphSnapshotPayload.send(
                    serverPlayer,
                    getBlockPos(),
                    SimulatedHelper.getContainingSubLevelId(this),
                    draftGraph,
                    activeGraph,
                    shippingScheduleDraftGraph,
                    shippingScheduleActiveGraph);
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


    record FluidContainerRecipe(ResourceLocation fluidId, ResourceLocation emptyContainerId, int millibuckets){}


    // Forward physics to the native components hosted by this ACC
    @Override
    public void sable$physicsTick(dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel,
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double dt){
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.physicsTick(this, subLevel, handle, dt);
    }


    // Get the SCM chunk-loading preference
    public boolean isScmChunkLoading(){ return scmChunkLoading; }

    // Keep the SCM loading preference independent of graph and calibration edits
    public boolean setScmChunkLoading(boolean enabled){
        if(!isMountedOnShipControlModule() || getLevel() == null || getLevel().isClientSide) return false;
        scmChunkLoading = enabled;
        shipControlRuntime.refreshChunkLoadingPreference();
        setChanged();
        sendData();
        return true;
    }

    // Check if this ACC is physically mounted on an SCM. The broader runtime
    // attachment check intentionally accepts an ACC Display Slab for legacy
    // control/display behaviour; the dedicated SCM editor must only appear
    // when the controller is actually on a Ship Control Module.
    public boolean isMountedOnShipControlModule() {
        if (CTBlocks.SHIP_CONTROL_MODULE == null) {
            return false;
        }
        var module = CTBlocks.SHIP_CONTROL_MODULE.get();
        if (getEmbeddedBlockState() != null && getEmbeddedBlockState().is(module)) {
            return true;
        }
        Level level = getLevel();
        if(level == null) return false;
        for(Direction direction : Direction.values()){
            BlockState mountedState = level.getBlockState(getBlockPos().relative(direction));
            if(mountedState.is(module)
                    && ShipControlModuleBlock.exposedFace(mountedState) == direction.getOpposite()) return true;
        }
        return false;
    }


    public @Nullable UUID shipPermissionPlacerId(){ return shipPermissionPlacerId; }


    public @Nullable UUID shipPermissionModulePlacerId(){ return shipPermissionModulePlacerId; }


    public void setShipPermissionPlacerId(@Nullable UUID id){
        shipPermissionPlacerId = id;
        setChanged();
    }


    public void setShipPermissionModulePlacerId(@Nullable UUID id){
        shipPermissionModulePlacerId = id;
        setChanged();
    }


    // Check whether the hidden Schedule Scratch surface is available. This is
    // deliberately server-authoritative and combines the physical SCM mount,
    // completed SCM attachment, and the pilot immediately beside the ACC.
    public boolean hasShippingScheduleWorkspace() {
        return isMountedOnShipControlModule() && hasShipControlModule()
                && ShippingSchedulePilot.hasScheduleWorkspacePilot(this);
    }


    // Get the persisted SCM configuration profile
    public ScmConfigurationProfile getScmConfigurationProfile() {
        return scmConfigurationProfile;
    }


    // Replace the SCM configuration profile after it has been validated by the
    // server-side configuration workflow.
    public void setScmConfigurationProfile(ScmConfigurationProfile profile) {
        scmConfigurationProfile = profile == null ? ScmConfigurationProfile.empty() : profile;
        setChanged();
        sendData();
    }


    // Reconcile auto-discovered docking connectors without changing player assignments
    public void reconcileScmDockingConnectors(
            java.util.Collection<ShipControlMap.DockingConnector> connectors
    ) {
        if (!scmConfigurationProfile.reconcileDockingConnectors(connectors)) return;
        setChanged();
        sendData();
    }


    // Get one ship docking connector's player-selected binding group
    public ScmConfigurationProfile.DockingConnectorGroup getScmDockingConnectorGroup(
            UUID subLevelId, BlockPos position
    ) {
        return scmConfigurationProfile.dockingConnectorGroup(subLevelId, position);
    }


    // Get optional control-capable suggestions from the last discovery pass.
    // The configuration screen itself may select any live block on the craft.
    public List<ScmConfigurationProfile.UnitReference> getScmConfigurationCandidates() {
        List<ScmConfigurationProfile.UnitReference> discovered =
                shipControlRuntime.configurationCandidates();
        if (!discovered.isEmpty()) {
            return discovered;
        }
        ShipControlMap stored = storedShipControlMap();
        if (stored == null) {
            return List.of();
        }
        return stored.units().stream().map(ScmConfigurationProfile.UnitReference::of)
                .distinct().toList();
    }


    // Get every live docking connector in the assembled ship.
    public List<ScmConfigurationProfile.DockingConnectorReference> getScmConfigurationDockingConnectors() {
        return shipControlRuntime.configurationDockingConnectors();
    }


    // Get the parent Sable body used by the live calibration viewer.
    public @Nullable UUID getScmConfigurationRootSubLevelId() {
        return shipControlRuntime.configurationRootSubLevelId();
    }


    // Get the connected Sable bodies that make up the live SCM calibration view.
    public List<UUID> getScmConfigurationViewSubLevelIds() {
        return shipControlRuntime.configurationViewSubLevelIds();
    }


    // Get the detached SCM preview scene used when the owning craft is not
    // currently streamed to the client that opened this controller.
    public List<ShipControlModuleRuntime.ConfigurationPreviewBlock> getScmConfigurationPreviewBlocks(
            int maximumBlocks
    ) {
        return shipControlRuntime.configurationPreviewBlocks(maximumBlocks);
    }


    // Begin the discovery-only SCM pass used by the configuration modal. It
    // reads the assembled craft but deliberately never freezes or pulses it.
    public boolean scanScmConfiguration() {
        return shipControlRuntime.scanConfiguration("scm_configuration", Map.of(), Map.of());
    }


    // Rebuild the current SCM groups from their live control adapters
    public boolean reinitializeScmConfiguration() {
        return shipControlRuntime.rebuildConfiguredControlMap();
    }


    // Return the stable configuration identity without requiring a discovery
    // scan. It is persisted with the controller and becomes the subsequent
    // profile-driven initialization map id.
    public UUID getScmConfigurationMapId() {
        return shipControlRuntime.ensureConfigurationMapId();
    }


    public boolean isScmConfigurationScanning() {
        return shipControlRuntime.isConfigurationScanning();
    }


    public String getScmConfigurationStatus() {
        return shipControlRuntime.initializationStatus();
    }


    // Preserve disabled saved choices while rejecting new selections blocked by the server
    public boolean canApplyScmConfigurationProfile(ScmConfigurationProfile requested){
        if(requested == null) return false;
        return (Objects.equals(requested.vehicleType(), scmConfigurationProfile.vehicleType())
                || com.rieno.gadgetsandgizmos.lib.scm.ScmVehicleClassifier.isAvailableSelection(requested.vehicleType()))
                && (Objects.equals(requested.steeringType(), scmConfigurationProfile.steeringType())
                || com.rieno.gadgetsandgizmos.lib.scm.ScmSteeringMode.isAvailableSelection(requested.steeringType()));
    }


    // Validate a player-authored profile against live Sable body membership.
    // The player may select every real block in the craft; whether an action
    // can control it is resolved through the safe SCM adaptor/probe API during
    // profile-driven initialization.
    public boolean replaceScmConfigurationProfile(ScmConfigurationProfile requested) {
        UUID targetMapId = getScmConfigurationMapId();
        if(!canApplyScmConfigurationProfile(requested)) return false;
        List<ScmConfigurationProfile.Group> groups = requested.groups().stream()
                .map(group -> new ScmConfigurationProfile.Group(group.id(), group.label(),
                        group.units().stream().filter(shipControlRuntime::isConfigurationTarget)
                                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))))
                .filter(group -> !group.id().isBlank())
                .toList();
        Map<String, String> actions = new LinkedHashMap<>();
        requested.actionGroups().forEach((action, group) -> {
            if (AdvancedGraphCatalog.isScmActionDispatchType(action)
                    || ScmConfigurationProfile.isAutoAction(action)
                    || ScmConfigurationProfile.isAccelerationAction(action)
                    || ScmConfigurationProfile.isIkAction(action)) {
                actions.put(action, group);
            }
        });
        ScmConfigurationProfile sanitized = ScmConfigurationProfile.empty();
        sanitized.setOrientationOverride(requested.orientationOverride());
        sanitized.setVehicleType(requested.vehicleType());
        sanitized.setSteeringType(requested.steeringType());
        sanitized.setIkGait(requested.ikGait());
        Set<ScmConfigurationProfile.DockingConnectorReference> liveDockingConnectors =
                new LinkedHashSet<>(getScmConfigurationDockingConnectors());
        Map<ScmConfigurationProfile.DockingConnectorReference,
                ScmConfigurationProfile.DockingConnectorGroup> dockingConnectorGroups =
                requested.dockingConnectorGroups();
        if (!liveDockingConnectors.isEmpty()) {
            dockingConnectorGroups = requested.dockingConnectorGroups().entrySet().stream()
                    .filter(entry -> liveDockingConnectors.contains(entry.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (first, second) -> second, LinkedHashMap::new));
        }
        sanitized.replaceDockingConnectorGroups(dockingConnectorGroups);
        sanitized.replace(targetMapId, groups, actions,
                requested.excludedUnits().stream().filter(shipControlRuntime::isConfigurationTarget)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        CompoundTag previousBindings = scmConfigurationProfile.toTag();
        CompoundTag nextBindings = sanitized.toTag();
        previousBindings.remove("Orientation");
        nextBindings.remove("Orientation");
        previousBindings.remove("MapId");
        nextBindings.remove("MapId");
        setScmConfigurationProfile(sanitized);
        shipControlRuntime.refreshVehicleType();
        // A profile is the live control declaration, not deferred calibration
        // UI state. Rebuild the allocator map immediately so newly assigned
        // blocks/faces can be driven by graph, pilot and schedule commands.
        if(!previousBindings.equals(nextBindings)){
            shipControlRuntime.rebuildConfiguredControlMap();
        }
        shipControlRuntime.refreshOrientation();
        return true;
    }


    // The ACC above the SCM owns the craft frame, not its propulsion or current velocity.
    public ScmOrientation getScmDefaultOrientation(){
        BlockState state = getBlockState();
        Direction forward = state.hasProperty(AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                ? state.getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING) : Direction.NORTH;
        Direction up = state.hasProperty(AnalogueContraptionControllerBlock.FACING)
                ? state.getValue(AnalogueContraptionControllerBlock.FACING) : Direction.UP;
        return ScmOrientation.fromMount(forward, up);
    }


    public ScmOrientation getScmOrientation(){
        return scmConfigurationProfile.resolveOrientation(getScmDefaultOrientation());
    }


    public String getScmDetectedVehicleType() {
        return shipControlRuntime.detectedVehicleType();
    }


    public String getScmDetectedSteeringType() {
        return shipControlRuntime.detectedSteeringType();
    }


    // Report live thruster presence independently of preview filters and authored bindings
    public boolean hasScmConfigurationThrusters(){
        return shipControlRuntime.configurationHasThrusters();
    }


    // Get the stable SCM route owner id
    public UUID getScmPersistenceId() {
        return scmPersistenceId;
    }


    // Check whether SCM progress messages are enabled from its workspace.
    public boolean scmDisplayProgress() {
        return scmDisplayProgress;
    }


    // Set SCM progress messages without requiring the retired Initialize node.
    public void setScmDisplayProgress(boolean enabled) {
        if (scmDisplayProgress == enabled) return;
        scmDisplayProgress = enabled;
        shipControlRuntime.setDisplayProgressPreference(enabled);
        setChanged();
        sendData();
    }


    // Get the configured ship up direction in world space
    public Vec3 getShipUpWorld(){
        return shipControlRuntime.shipUpWorld();
    }


    // Get the mounted seats retained by the SCM map
    public List<ShipControlMap.Seat> getMappedShipSeats() {
        return shipControlRuntime.mappedSeats();
    }


    // Reuse a caller-owned detached copy while the applied graph remains unchanged
    public AdvancedGraphDocument activeGraphSnapshot(
            com.rieno.gadgetsandgizmos.lib.util.RevisionSnapshotCache<AdvancedGraphDocument> cache){
        return cache.get(activeGraph, activeGraph.revision(), activeGraph::copy);
    }


    // Get the hidden Schedule Scratch graph draft. Callers must still pass the
    // Schedule-workspace check before displaying or editing this document.
    public AdvancedGraphDocument getShippingScheduleDraftGraph() {
        hydrateShippingScheduleGraphFromRuntime();
        return shippingScheduleDraftGraph.copy();
    }


    // Supply known ship docks to destination, parking and refuel input completions
    public List<String> shippingScheduleDestinationNames(ResourceLocation inputId){
        if(inputId == null || !Set.of("destination", "park_at", "refuel_if").contains(inputId.getPath())) return List.of();
        if(level == null || level.isClientSide || level.getServer() == null) return shippingScheduleDestinationNames;
        return ShipDockRegistry.get(level.getServer()).allIn(level.dimension().location()).stream()
                .map(ShipDockRegistry.Dock::name).distinct().sorted().limit(4096).toList();
    }


    // Get the active Schedule Scratch graph.
    public AdvancedGraphDocument getShippingScheduleActiveGraph() {
        hydrateShippingScheduleGraphFromRuntime();
        return shippingScheduleActiveGraph.copy();
    }


    // Save one controller-owned Scratch schedule graph. An already-running
    // SCM receives the updated route; an inactive SCM waits for Start.
    public boolean saveShippingScheduleGraph(AdvancedGraphDocument graph, int expectedRevision) {
        hydrateShippingScheduleGraphFromRuntime();
        if (!hasShippingScheduleWorkspace() || graph == null
                || expectedRevision != shippingScheduleDraftGraph.revision()
                || getLevel() == null || getLevel().isClientSide) {
            return false;
        }
        AdvancedGraphDocument candidate = graph.copy();
        ShippingScheduleGraph.ensureTemplate(candidate);
        com.simibubi.create.content.trains.schedule.Schedule schedule =
                ShippingScheduleGraph.toSchedule(candidate, getLevel().registryAccess());
        // Saving is not a hidden start button. A live SCM runtime receives
        // the edited schedule immediately, while an inactive controller keeps
        // the graph ready for the explicit Start control.
        if (shippingScheduleRuntime.hasSchedule() && (schedule == null || schedule.entries.isEmpty()
                || !shippingScheduleRuntime.replaceSchedule(schedule))) {
            return false;
        }
        candidate.setRevision(shippingScheduleDraftGraph.revision() + 1);
        boolean wasFollowing = followsShippingScheduleRoute();
        shippingScheduleDraftGraph = candidate;
        shippingScheduleActiveGraph = candidate.copy();
        if(wasFollowing != followsShippingScheduleRoute()) shipControlRuntime.resetScheduledRouteFollowing();
        if(schedule != null && !schedule.entries.isEmpty()) shippingScheduleRuntime.ensureEditableRoutes(schedule);
        setChanged();
        sendData();
        return true;
    }


    // Explicitly import the physical schedule held by the attached pilot. Once
    // materialised, the SCM's stored Scratch graph remains authoritative until
    // the player deliberately reads the item again.
    public boolean readShippingScheduleFromPilot() {
        if (!hasShippingScheduleWorkspace() || getLevel() == null || getLevel().isClientSide) return false;
        com.simibubi.create.content.trains.schedule.Schedule schedule = ShippingSchedulePilot.adjacentPilotSchedule(this);
        if (schedule == null || schedule.entries.isEmpty()) return false;
        syncShippingScheduleGraphFromSchedule(schedule);
        sendData();
        return true;
    }


    // Explicitly write the SCM-owned Scratch graph to its live Create runtime
    // and paired held schedule item. This is intentionally separate from a
    // Read so an old item cannot silently overwrite an edited controller graph.
    public boolean writeShippingScheduleToPilot() {
        if (!hasShippingScheduleWorkspace() || getLevel() == null || getLevel().isClientSide) return false;
        AdvancedGraphDocument candidate = shippingScheduleDraftGraph.copy();
        ShippingScheduleGraph.ensureTemplate(candidate);
        com.simibubi.create.content.trains.schedule.Schedule schedule =
                ShippingScheduleGraph.toSchedule(candidate, getLevel().registryAccess());
        if (schedule == null || schedule.entries.isEmpty()) return false;
        if (shippingScheduleRuntime.hasSchedule() && !shippingScheduleRuntime.replaceSchedule(schedule)) return false;
        boolean wroteItem = ShippingSchedulePilot.writeAdjacentPilotSchedule(this, schedule);
        if (!wroteItem) return false;
        shippingScheduleActiveGraph = candidate.copy();
        setChanged();
        sendData();
        return true;
    }


    // Start the controller-owned Scratch schedule without depending on a
    // physical shipping-schedule item. A seated pilot (or blaze pilot) is
    // still required because it is the runtime actor, but the document stored
    // by this SCM is the authority.
    public boolean startShippingScheduleGraph() {
        if (!hasShippingScheduleWorkspace() || getLevel() == null || getLevel().isClientSide) return false;
        AdvancedGraphDocument candidate = shippingScheduleDraftGraph.copy();
        ShippingScheduleGraph.ensureTemplate(candidate);
        com.simibubi.create.content.trains.schedule.Schedule schedule =
                ShippingScheduleGraph.toSchedule(candidate, getLevel().registryAccess());
        if (schedule == null || schedule.entries.isEmpty()) return false;
        boolean updated = shippingScheduleRuntime.hasSchedule()
                ? shippingScheduleRuntime.replaceSchedule(schedule)
                : installScheduleGraphRuntime(schedule);
        if (!updated) return false;
        shippingScheduleActiveGraph = candidate.copy();
        if (!shippingScheduleRuntime.restartFromController()) return false;
        setChanged();
        sendData();
        return true;
    }


    // Route the visible SCM controls through the same server-authoritative
    // runtime commands as schedule graph nodes.
    public boolean controlShippingScheduleGraph(String command) {
        if (!hasShippingScheduleWorkspace() || getLevel() == null || getLevel().isClientSide) return false;
        return shippingScheduleRuntime.executeGraphCommand(command);
    }


    // Queue the complete visible schedule route without starting or changing the schedule.
    public boolean precalculateShippingScheduleRoute() {
        if (!hasShippingScheduleWorkspace() || getLevel() == null || getLevel().isClientSide) return false;
        AdvancedGraphDocument graph = shippingScheduleDraftGraph.copy();
        ShippingScheduleGraph.ensureTemplate(graph);
        com.simibubi.create.content.trains.schedule.Schedule schedule =
                ShippingScheduleGraph.toSchedule(graph, getLevel().registryAccess());
        return schedule != null && !schedule.entries.isEmpty()
                && shippingScheduleRuntime.precalculateRoutes(schedule);
    }


    // Delete prepared schedule-route geometry without changing the schedule graph.
    public boolean deleteShippingScheduleRoute() {
        if (!hasShippingScheduleWorkspace() || getLevel() == null || getLevel().isClientSide) return false;
        return shipControlRuntime.deleteScheduledRoutes();
    }


    // Queue resolved schedule legs through the SCM route planner with their schedule entry identity.
    boolean queueShippingScheduleRoutes(
            List<ShipControlModuleRuntime.ScheduledRouteDestination> destinations
    ) {
        return shipControlRuntime.queueScheduledRouteDestinations(destinations);
    }


    public boolean followsShippingScheduleRoute(){
        return shippingScheduleDraftGraph.variables().getOrDefault(ShippingScheduleGraph.FOLLOW_ROUTE_VARIABLE,
                AdvancedGraphDocument.Value.bool(true)).asBoolean();
    }


    boolean createEditableShippingScheduleRoutes(List<ShipControlModuleRuntime.ScheduledRouteDestination> destinations){
        return shipControlRuntime.createEditableScheduledRoutes(destinations);
    }


    // Get detached retained route geometry used to place schedule-owned dock queues.
    List<List<Vec3>> shippingScheduleRoutePolylines(
            int scheduleEntry,
            Vec3 target,
            double targetTolerance
    ) {
        return shipControlRuntime.scheduledRoutePolylines(
                scheduleEntry, target, targetTolerance);
    }


    // Check whether a schedule-route calculation is currently queued.
    public boolean isPrecalculatingShippingScheduleRoute() {
        return shipControlRuntime.isPrecalculatingScheduledRoutes();
    }


    // Get the live queued schedule-route calculation progress.
    public double shippingScheduleRoutePrecalculationProgress() {
        return shipControlRuntime.scheduledRoutePrecalculationProgress();
    }


    // Start a schedule graph without requiring the retired schedule item.
    private boolean installScheduleGraphRuntime(com.simibubi.create.content.trains.schedule.Schedule schedule) {
        if (schedule == null || getLevel() == null) {
            return false;
        }
        if (ShippingSchedulePilot.isBlazeBurnerPilot(this)) {
            String identity = getLevel().dimension().location() + "|" + getBlockPos().asLong();
            UUID pilot = UUID.nameUUIDFromBytes(("shipping_schedule_blaze|" + identity)
                    .getBytes(StandardCharsets.UTF_8));
            return shippingScheduleRuntime.install(schedule, pilot,
                    ShippingAutoRefuelSettings.fromSchedule(schedule), true);
        }
        LivingEntity pilot = ShippingSchedulePilot.findAdjacentSeatedPilot(this);
        return pilot != null && shippingScheduleRuntime.install(schedule, pilot.getUUID(),
                ShippingAutoRefuelSettings.fromSchedule(schedule));
    }


    // Convert an installed legacy item schedule to its graph representation.
    private void syncShippingScheduleGraphFromRuntime() {
        if (getLevel() == null) {
            return;
        }
        com.simibubi.create.content.trains.schedule.Schedule schedule = shippingScheduleRuntime.scheduleCopy();
        if (schedule == null) return;
        syncShippingScheduleGraphFromSchedule(schedule);
    }


    // Materialize a live or legacy held schedule into the reusable graph
    // contract. Keeping this separate from the runtime reader lets older
    // pilots populate Schedule immediately without first being reassigned.
    private void syncShippingScheduleGraphFromSchedule(
            com.simibubi.create.content.trains.schedule.Schedule schedule
    ) {
        if (getLevel() == null || schedule == null) return;
        AdvancedGraphDocument graph = ShippingScheduleGraph.fromSchedule(schedule, getLevel().registryAccess());
        int nextRevision = Math.max(shippingScheduleDraftGraph.revision(), shippingScheduleActiveGraph.revision()) + 1;
        graph.setRevision(nextRevision);
        shippingScheduleDraftGraph = graph;
        shippingScheduleActiveGraph = graph.copy();
        setChanged();
    }


    // Older worlds can already have a schedule runtime in memory while the
    // new graph tags are absent. Menu creation and snapshots must repair that
    // state too; relying only on the NBT read path left a valid held schedule
    // looking like an empty Schedule tab until the controller reloaded.
    private void hydrateShippingScheduleGraphFromRuntime() {
        if (getLevel() == null || getLevel().isClientSide) {
            return;
        }
        com.simibubi.create.content.trains.schedule.Schedule schedule = shippingScheduleRuntime.scheduleCopy();
        if (schedule == null) schedule = ShippingSchedulePilot.adjacentPilotSchedule(this);
        if (schedule == null || schedule.entries.isEmpty()) return;
        // A populated graph is controller-owned. It must never be silently
        // replaced just because the held item/runtime has changed; the explicit
        // Read action handles that direction. Empty legacy state still imports
        // automatically so existing pilots open with a useful Schedule tab.
        if (shippingScheduleDraftGraph.nodes().isEmpty()) {
            syncShippingScheduleGraphFromSchedule(schedule);
        }
    }


    // Mirror native GUI settings without replacing an independently edited graph draft
    public void updateNativeGraphSettings(String nodeId, String type, Map<String, com.rieno.gadgetsandgizmos.lib.graph.GraphValue> settings){
        if(level == null || level.isClientSide || settings.isEmpty()) return;
        var active = activeGraph.nodes().stream().filter(node -> node.id().equals(nodeId) && node.type().equals(type)).findFirst().orElse(null);
        if(active == null) return;
        CompoundTag defaults = active.data().getCompound("Defaults");
        var draft = draftGraph.nodes().stream().filter(node -> node.id().equals(nodeId) && node.type().equals(type)).findFirst().orElse(null);
        CompoundTag draftDefaults = draft == null ? null : draft.data().getCompound("Defaults");
        boolean changed = false;
        boolean draftChanged = false;
        for(var pair : settings.entrySet()){
            var tag = GraphRuntime.fromLibraryValue(pair.getValue()).toTag();
            if(tag.equals(defaults.get(pair.getKey()))) continue;
            if(draftDefaults != null && java.util.Objects.equals(defaults.get(pair.getKey()), draftDefaults.get(pair.getKey()))){
                draftDefaults.put(pair.getKey(), tag.copy());
                draftChanged = true;
            }
            defaults.put(pair.getKey(), tag);
            changed = true;
        }
        if(!changed) return;
        active.data().put("Defaults", defaults);
        activeGraph.setRevision(activeGraph.revision() + 1);
        if(draftChanged){
            draft.data().put("Defaults", draftDefaults);
            draftGraph.setRevision(draftGraph.revision() + 1);
        }
        graphRuntime.compile(activeGraph);
        saveControllerManifestNow();
        setChanged();
        sendData();
    }

    // Retain the player responsible for a transported graph event
    public void receiveNamedControllerEvent(String name, AdvancedGraphDocument.Value data, @Nullable UUID playerId){
        graphRuntime.enqueueNamedControllerEvent(name, data == null ? AdvancedGraphDocument.Value.number(0) : data, playerId);
    }


    // Read the nearest detection and the complete ordered detection list
    public AdvancedGraphDocument.Value getShipTrackingValue(String nodeId, String filter, double range, String port) {
        ServerLevel level = SableLevelApi.serverLevel(getLevel());
        if (level == null || getLevel().isClientSide) return GraphRuntime.defaultShipTrackingValue(port);
        long gameTime = level.getGameTime();
        if (lastShipTrackingSample != gameTime) {
            lastShipTrackingSample = gameTime;
            shipTrackingSnapshots.clear();
            trackedShips.clear();
        }
        ShipTrackingSnapshot snapshot = shipTrackingSnapshots.get(nodeId);
        if (snapshot == null || !Objects.equals(snapshot.filter(), filter)
                || Double.compare(snapshot.range(), range) != 0) {
            List<SubLevelTrackingApi.Target> targets = SubLevelTrackingApi.scan(level, trackingOrigin(),
                    trackingForward(), trackingUp(), SableLevelApi.containingId(this), filter, range,
                    body -> trackedShipMetadata(body).name(),
                    body -> trackedShipMetadata(body).controlModule());
            snapshot = new ShipTrackingSnapshot(filter, range, targets);
            shipTrackingSnapshots.put(nodeId, snapshot);
        }
        if ("count".equals(port)) return AdvancedGraphDocument.Value.number(snapshot.targets().size());
        if ("ships".equals(port)) return GraphRuntime.fromLibraryValue(GraphValue.list(
                snapshot.targets().stream().map(this::shipTrackingData).toList()));
        if (snapshot.targets().isEmpty()) return GraphRuntime.defaultShipTrackingValue(port);
        GraphValue val = shipTrackingData(snapshot.targets().getFirst()).get(port == null ? "" : port);
        return val == null ? GraphRuntime.defaultShipTrackingValue(port) : GraphRuntime.fromLibraryValue(val);
    }


    // Adapt library detections to the addon node's ports
    private Map<String, GraphValue> shipTrackingData(SubLevelTrackingApi.Target target) {
        return Map.of("available", GraphValue.bool(true), "distance", GraphValue.number(target.distance()),
                "coordinates", EntityTelemetryApi.vector(target.position()),
                "relative_angle", EntityTelemetryApi.angles(target.angles()),
                "ship_name", GraphValue.string(target.name()), "sub_level", GraphValue.string(target.id().toString()));
    }


    // Resolve the controller's world-space position and mounted axes
    private Vec3 trackingOrigin() {
        return SableTransformApi.toWorldPosition(this, getBlockPos().getCenter());
    }


    private Vec3 trackingForward() {
        return SableTransformApi.toWorldDirection(this, getScmDefaultOrientation().forwardVector());
    }


    private Vec3 trackingUp() {
        return SableTransformApi.toWorldDirection(this, getScmDefaultOrientation().upVector());
    }


    private record ShipTrackingSnapshot(String filter, double range, List<SubLevelTrackingApi.Target> targets) {
    }


    // Resolve both tracker predicates from one body discovery pass
    private TrackedShipMetadata trackedShipMetadata(dev.ryanhcode.sable.sublevel.SubLevel body){
        return trackedShips.computeIfAbsent(body.getUniqueId(), id -> {
            boolean controlModule = false;
            AdvancedContraptionControllerBlockEntity named = null;
            for(BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(body)){
                if(blockEntity instanceof ShipControlModuleBlockEntity) controlModule = true;
                if(blockEntity instanceof AdvancedContraptionControllerBlockEntity acc
                        && acc.isMountedOnShipControlModule()){
                    controlModule = true;
                    if(named == null || acc.getBlockPos().asLong() < named.getBlockPos().asLong()) named = acc;
                }
            }
            return new TrackedShipMetadata(named == null ? "" : named.getShipName(), controlModule);
        });
    }


    private record TrackedShipMetadata(String name, boolean controlModule){}


    // Attribute input devices and portable controller actions before they queue graph work
    public void runGraphInteraction(ServerPlayer player, Runnable action){
        if(player == null) return;
        if(getLevel() != null && getLevel().getBlockEntity(getBlockPos()) == this){
            com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.record(getLevel(), getBlockPos(), player);
        }
        graphRuntime.withInteractionPlayer(player.getUUID(), () -> { action.run(); return null; });
    }


    public void handleMouseInput(ServerPlayer player, String input, double value, boolean active){
        runGraphInteraction(player, () -> handleMouseInput(input, value, active));
    }


    public void applyHardwareControllerInput(ServerPlayer player, Map<String, Double> values){
        runGraphInteraction(player, () -> applyHardwareControllerInput(values));
    }


    public void handleControllerKeyInput(ServerPlayer player, String channelId, boolean pressed){
        runGraphInteraction(player, () -> handleControllerKeyInput(channelId, pressed));
    }


    // Refresh native input sampling without replacing the applied graph's compiled program
    public void beginActiveGraphPreview(){
        graphRuntime.beginPreviewSample(activeGraph);
    }


    // Resolve detached node ids through the controller-owned compiled graph
    public AdvancedGraphDocument.Value previewActiveGraphInput(AdvancedGraphDocument.Node node, String port){
        return graphRuntime.previewInput(activeGraph, node, port);
    }


    // Get the display adapter
    private @Nullable UniversalDisplayAdapterBlockEntity displayAdapter(AdvancedGraphDocument.Node node) {
        TargetAccess target = cachedGraphTarget(node);
        if(target == null) return null;
        BlockEntity blockEntity = target.level().getBlockEntity(target.pos());
        return blockEntity instanceof UniversalDisplayAdapterBlockEntity adapter
                ? adapter : null;
    }


    // Get the source bindings for one generated multi-target port.
    private static List<DataTargetPortBinding> dataTargetPortBindings(AdvancedGraphDocument.Node node,
                                                                      String port) {
        if (node == null || port == null || port.isBlank()) return List.of();
        List<DataTargetPortBinding> bindings = new ArrayList<>();
        // The common redstone input applies to every selected target, including
        // grouped Set Data nodes. Data controls retain their own port bindings.
        if (GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(port)) {
            for (Tag raw : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)) {
                if (!(raw instanceof CompoundTag entry)) continue;
                ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(entry);
                if (target != null) bindings.add(new DataTargetPortBinding(target.nodeId(), port));
            }
            return bindings;
        }
        ListTag entries = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG)
                .getList(port, Tag.TAG_COMPOUND);
        for (Tag raw : entries) {
            if (!(raw instanceof CompoundTag entry)) continue;
            String targetId = entry.getString("Target");
            String sourcePort = entry.getString("Port");
            if (!targetId.isBlank() && !sourcePort.isBlank()) {
                bindings.add(new DataTargetPortBinding(targetId, sourcePort));
            }
        }
        return bindings;
    }


    // Create a target-scoped node that uses one retained multi-target source target.
    private static @Nullable AdvancedGraphDocument.Node dataTargetNode(AdvancedGraphDocument.Node node,
                                                                        String targetId) {
        if (node == null || targetId == null || targetId.isBlank()) return null;
        ListTag targets = node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : targets) {
            if (!(raw instanceof CompoundTag target)) continue;
            ControllerDiscoveryNode discovery = ControllerDiscoveryNode.fromTag(target);
            if (discovery == null || !targetId.equals(discovery.nodeId())) continue;
            CompoundTag data = CompoundTagCopies.copyExcept(node.data(), Set.of(
                    AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, AdvancedGraphCatalog.DATA_TARGETS_TAG,
                    GRAPH_DATA_SCHEMA_REVISIONS_TAG, "Target", "TargetLabel", "TargetData"));
            data.putString("Target", discovery.nodeId());
            data.putString("TargetLabel", discovery.label().isBlank()
                    ? discovery.nodeId() : discovery.label());
            data.put("TargetData", discovery.toTag());
            Direction face = selectedDataTargetFace(node, discovery);
            if (face != null) {
                putGraphDefault(data, "face", "direction", face.getSerializedName());
            }
            return new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(),
                    node.x(), node.y(), data);
        }
        return null;
    }


    // Store one generated port binding.
    private record DataTargetPortBinding(String targetId, String port) {
    }


    // Refresh one composed Get Data or Set Data target schema
    private void refreshMultiDataTargetPorts(AdvancedGraphDocument.Node node) {
        if (node == null || level == null) return;
        CompoundTag revisions = multiDataTargetSchemaRevisions(node);
        if(revisions == null) return;
        boolean writable = "set_block_data".equals(node.type());
        List<GraphTargetPortLayout.Target> targets = new ArrayList<>();
        ListTag configured = node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND);
        for (Tag raw : configured) {
            if (!(raw instanceof CompoundTag encoded)) continue;
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(encoded);
            GraphTargetPortLayout.Target layout = graphDataTargetPortLayout(level, node, target, writable);
            if (layout != null) targets.add(layout);
        }
        if (targets.size() != configured.size()) return;
        applyDataTargetPortLayout(node, targets, writable);
        node.data().put(GRAPH_DATA_SCHEMA_REVISIONS_TAG, revisions);
        ControllerDiscoveryNode primary = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        configureDataTargetFaceOptions(node, primary);
    }


    // Retain each composed target's revision after its schema is refreshed
    private @Nullable CompoundTag multiDataTargetSchemaRevisions(AdvancedGraphDocument.Node node){
        CompoundTag revisions = new CompoundTag();
        for(Tag raw : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)){
            if(!(raw instanceof CompoundTag encoded)) return null;
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag(encoded);
            if(target == null) return null;
            if(ContraptionDiagramControllerCompat.isTarget(target)){
                revisions.putLong(target.nodeId(), 0L);
                continue;
            }
            GraphDataTarget resolved = resolveGraphDataTarget(level, node, target);
            if(resolved == null) return null;
            BlockEntity blockEntity = resolved.level().getBlockEntity(resolved.pos());
            if(!BlockEntityDataAdapterRegistry.isDataSchemaReady(blockEntity)) return null;
            revisions.putLong(target.nodeId(), BlockEntityDataAdapterRegistry.dataSchemaRevision(blockEntity));
        }
        return revisions;
    }


    // Build one resolved Get Data or Set Data target schema
    public static @Nullable GraphTargetPortLayout.Target graphDataTargetPortLayout(
            @Nullable Level ownerLevel, @Nullable AdvancedGraphDocument.Node node,
            @Nullable ControllerDiscoveryNode target, boolean writable) {
        if (ownerLevel == null || node == null || target == null || target.blockPos() == null) return null;
        CompoundTag ports;
        CompoundTag labels = new CompoundTag();
        CompoundTag options = new CompoundTag();
        if (ContraptionDiagramControllerCompat.isTarget(target)) {
            ports = writable ? new CompoundTag() : ContraptionDiagramControllerCompat.readablePorts();
        } else {
            GraphDataTarget resolved = resolveGraphDataTarget(ownerLevel, node, target);
            if (resolved == null) return null;
            ports = graphDataPorts(resolved.level(), resolved.pos(), writable);
            if (!writable) labels = graphReadableDataPortLabels(resolved.level(), resolved.pos());
            if (writable){
                options = graphDataPortOptions(resolved.level(), resolved.pos(), true);
                configureGraphInputVisibility(node, resolved.level().getBlockEntity(resolved.pos()), target.nodeId());
            }
        }
        List<GraphTargetPortLayout.Port> entries = new ArrayList<>();
        for (String port : ports.getAllKeys()) {
            String type = ports.getString(port);
            if (type.isBlank() || "exec".equals(type)) continue;
            String label = labels.getString(port);
            List<String> values = options.getList(port, Tag.TAG_STRING).stream()
                    .map(Tag::getAsString).toList();
            entries.add(new GraphTargetPortLayout.Port(port,
                    label.isBlank() ? graphDataPortLabel(port) : label, type, values));
        }
        String label = com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection.label(target,
                selectedDataTargetFace(node, target), node.data().getCompound(
                        com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection.ALIASES_TAG).getString(target.nodeId()));
        return new GraphTargetPortLayout.Target(target.nodeId(), label, entries);
    }


    // Resolve a moving data target and its face-bound linker support
    public static @Nullable GraphDataTarget resolveGraphDataTarget(
            @Nullable Level ownerLevel, @Nullable AdvancedGraphDocument.Node node,
            @Nullable ControllerDiscoveryNode target) {
        if (ownerLevel == null || target == null || target.blockPos() == null) return null;
        Direction side = selectedDataTargetFace(node, target);
        if(isPickedGraphTarget(target)){
            var reference = ControllerRedstoneCompat.resolveMovingTarget(ownerLevel,
                    target.asDirectTargetReference().withFace(side));
            if(reference == null) return null;
            target = target.withLocation(reference.subLevelId(), reference.blockPos());
            updatePickedGraphTargetLocation(node, target);
            side = SubLevelBlockTargetApi.resolveFace(ownerLevel, reference);
        }
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                ownerLevel, target.subLevelId(), target.blockPos());
        Level targetLevel = blockEntity != null && blockEntity.getLevel() != null
                ? blockEntity.getLevel()
                : SubLevelBlockEntityCollector.resolveTargetLevel(ownerLevel, target.subLevelId());
        if (targetLevel == null) return null;
        BlockPos targetPos = blockEntity == null ? target.blockPos() : blockEntity.getBlockPos();
        if (!SubLevelBlockEntityCollector.isTargetLoaded(targetLevel, target.subLevelId(), targetPos)
                || targetLevel.getBlockState(targetPos).isAir()) return null;
        if (!(targetLevel.getBlockState(targetPos).getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)
                && !"createthrusters:contraption_network_linker_plane".equalsIgnoreCase(target.blockId())) {
            return new GraphDataTarget(targetLevel, targetPos, side);
        }
        if (side == null) return null;
        BlockPos attachedPos = targetPos.relative(side.getOpposite());
        if (!SubLevelBlockEntityCollector.isTargetLoaded(targetLevel, target.subLevelId(), attachedPos)
                || targetLevel.getBlockState(attachedPos).isAir()) return null;
        return new GraphDataTarget(targetLevel, attachedPos, side);
    }


    // Get one data target's selected or deterministic linker face
    public static @Nullable Direction selectedDataTargetFace(
            @Nullable AdvancedGraphDocument.Node node, @Nullable ControllerDiscoveryNode target) {
        if (target == null) return null;
        Direction stored = node == null ? null : Direction.byName(node.data()
                .getCompound(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG).getString(target.nodeId()));
        List<Direction> faces = ContraptionNetworkLinkerData.faceOptionsForNode(target).stream()
                .map(ContraptionNetworkLinkerData.FaceOption::face)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (faces.isEmpty()){
            if(stored != null) return stored;
            return node != null && isPickedGraphTarget(target) ? configuredDirection(node, "face") : null;
        }
        if (stored != null && faces.contains(stored)) return stored;
        boolean primary = node != null && target.nodeId().equals(node.data().getString("Target"));
        Direction configured = primary ? configuredDirection(node, "face") : null;
        if (configured != null && faces.contains(configured)) return configured;
        return faces.getFirst();
    }


    // Identify direct block picks whose stable bindings use native block tracking
    private static boolean isPickedGraphTarget(@Nullable ControllerDiscoveryNode target){
        return target != null && target.nodeId().startsWith("scm_picker:");
    }


    // Keep picker previews at the current block without changing generated port or face keys
    private static void updatePickedGraphTargetLocation(@Nullable AdvancedGraphDocument.Node node,
            ControllerDiscoveryNode target){
        if(node == null || !isPickedGraphTarget(target)) return;
        CompoundTag data = node.data();
        ControllerDiscoveryNode primary = ControllerDiscoveryNode.fromTag(data.getCompound("TargetData"));
        if(primary != null && primary.nodeId().equals(target.nodeId())) data.put("TargetData", target.toTag());
        ListTag targets = data.getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND);
        for(int idx = 0; idx < targets.size(); idx++){
            ControllerDiscoveryNode current = ControllerDiscoveryNode.fromTag(targets.getCompound(idx));
            if(current != null && current.nodeId().equals(target.nodeId())) targets.set(idx, target.toTag());
        }
    }


    // Format a generated data port label
    private static String graphDataPortLabel(String port) {
        if ("direct_signal".equals(port)) return "Value";
        if ("north_south_output_speed_percent".equals(port)) return "N/S Output Speed %";
        if ("east_west_output_speed_percent".equals(port)) return "E/W Output Speed %";
        StringBuilder label = new StringBuilder();
        boolean wordStart = true;
        for (char chr : port.toCharArray()) {
            if (chr == '_') {
                label.append(' ');
                wordStart = true;
            } else {
                label.append(wordStart ? Character.toUpperCase(chr) : chr);
                wordStart = false;
            }
        }
        return label.toString();
    }


    // Apply the composed multi-target port schema to a Get Data or Set Data node.
    public static void applyDataTargetPortLayout(AdvancedGraphDocument.Node node,
                                                 List<GraphTargetPortLayout.Target> targets,
                                                 boolean writable) {
        if (node == null) {
            return;
        }
        boolean mergeLikePorts = node.data().getBoolean(AdvancedGraphCatalog.MERGE_LIKE_PORTS_TAG);
        Map<String, String> stableTargetIds = new LinkedHashMap<>();
        CompoundTag portIds = node.data().getCompound("DataTargetPortIds");
        portIds.getAllKeys().forEach(id -> stableTargetIds.put(id, portIds.getString(id)));
        if(writable){
            targets = targets.stream().map(target -> {
                List<GraphTargetPortLayout.Port> ports = new ArrayList<>(target.ports());
                if(ports.stream().noneMatch(port -> GraphSignalRange.REDSTONE_SIGNAL_PORT.equals(port.id()))){
                    ports.add(new GraphTargetPortLayout.Port(GraphSignalRange.REDSTONE_SIGNAL_PORT,
                            "Redstone Signal Strength", "number"));
                }
                return new GraphTargetPortLayout.Target(target.id(), target.label(), ports);
            }).toList();
        }
        GraphTargetPortLayout.Layout layout = GraphTargetPortLayout.compose(targets, mergeLikePorts, stableTargetIds,
                writable ? Set.of(GraphSignalRange.REDSTONE_SIGNAL_PORT) : Set.of());
        CompoundTag dynamicInputs = new CompoundTag();
        CompoundTag dynamicOutputs = new CompoundTag();
        if (writable) {
            layout.ports().forEach(dynamicInputs::putString);
        } else {
            layout.ports().forEach(dynamicOutputs::putString);
        }
        CompoundTag inputLabels = new CompoundTag();
        CompoundTag outputLabels = new CompoundTag();
        layout.labels().forEach(writable ? inputLabels::putString : outputLabels::putString);
        CompoundTag inputOptions = new CompoundTag();
        if (writable) {
            layout.options().forEach((port, entries) -> {
                ListTag values = new ListTag();
                entries.forEach(value -> values.add(StringTag.valueOf(value)));
                if (!values.isEmpty()) inputOptions.put(port, values);
            });
        }

        CompoundTag bindings = new CompoundTag();
        layout.bindings().forEach((port, entries) -> {
            ListTag encodedEntries = new ListTag();
            for (GraphTargetPortLayout.Binding entry : entries) {
                CompoundTag encoded = new CompoundTag();
                encoded.putString("Target", entry.targetId());
                encoded.putString("Port", entry.portId());
                encodedEntries.add(encoded);
            }
            bindings.put(port, encodedEntries);
        });

        CompoundTag sections = new CompoundTag();
        CompoundTag side = new CompoundTag();
        for (GraphTargetPortLayout.Section section : layout.sections()) {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("Label", section.label());
            ListTag ports = new ListTag();
            for (String port : section.ports()) {
                ports.add(StringTag.valueOf(port));
            }
            encoded.put("Ports", ports);
            side.put(section.id(), encoded);
        }
        sections.put(writable ? "Inputs" : "Outputs", side);
        CompoundTag collapsed = node.data().getCompound(AdvancedGraphCatalog.COLLAPSED_PORT_SECTIONS_TAG);
        String prefix = writable ? "I:" : "O:";
        for (GraphTargetPortLayout.Section section : layout.sections()) {
            String key = prefix + section.id();
            if (!collapsed.contains(key)) collapsed.putBoolean(key, true);
        }

        restoreInlineMapPorts(node, writable ? dynamicInputs : dynamicOutputs, !writable);
        clearDataPortGroups(node);
        node.data().put("DynamicInputs", dynamicInputs);
        if (writable) {
            node.data().remove("DynamicOutputs");
            node.data().put("InputOptions", inputOptions);
            restoreInlineMapInputOptions(node);
        } else {
            node.data().put("DynamicOutputs", dynamicOutputs);
        }
        node.data().put(AdvancedGraphCatalog.INPUT_LABELS_TAG, inputLabels);
        if (writable) {
            node.data().remove("OutputLabels");
        } else {
            node.data().put("OutputLabels", outputLabels);
        }
        node.data().put(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG, bindings);
        node.data().put(AdvancedGraphCatalog.PORT_SECTIONS_TAG, sections);
        node.data().put(AdvancedGraphCatalog.COLLAPSED_PORT_SECTIONS_TAG, collapsed);
        node.data().putBoolean(AdvancedGraphCatalog.MERGE_LIKE_PORTS_TAG, mergeLikePorts);
    }


    // Resolve the selected loaded camera through the existing target API
    public @Nullable CameraBlockEntity graphCamera(AdvancedGraphDocument.Node node, AdvancedGraphDocument.Value val){
        AdvancedGraphDocument.Node selected = node;
        if(val != null && "target".equals(val.type()) && val.payload().contains("BlockPos")){
            CompoundTag data = node.data().copy();
            data.put("TargetData", val.payload().copy());
            selected = new AdvancedGraphDocument.Node(node.id(), node.type(), node.label(), node.x(), node.y(), data);
        }
        TargetAccess target = resolveGraphTarget(selected);
        if(target == null) return null;
        return target.level().getBlockEntity(target.pos()) instanceof CameraBlockEntity camera ? camera : null;
    }


    // Read backend interaction ownership through the same loaded target used for graph signals
    public @Nullable UUID graphInputPlayer(AdvancedGraphDocument.Node node){
        return graphInputPlayer(node, true);
    }

    // Read retained ownership when sampling a player's personal HUD
    public @Nullable UUID graphInputPlayer(AdvancedGraphDocument.Node node, boolean recent){
        if(node == null || level == null) return null;
        TargetAccess target = null;
        if(node.data().contains("TargetData") && ("discovered_target_input".equals(node.type())
                || "linker_face_input".equals(node.type()) || "get_block_data".equals(node.type()))){
            target = resolveGraphTarget(node);
        }else if("local_redstone_input".equals(node.type())){
            Direction face = configuredDirection(node, "face");
            if(face != null) target = new TargetAccess(level, getBlockPos().relative(face), null);
        }else if("controller_channel_input".equals(node.type()) || "gamepad_input".equals(node.type())
                || "mouse_input".equals(node.type()) || "event_key".equals(node.type())){
            target = new TargetAccess(level, getBlockPos(), null);
        }
        if(target == null) return null;
        var origin = recent ? com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.recent(target.level(), target.pos(), 4)
                : com.rieno.gadgetsandgizmos.lib.interaction.BlockInteractionTracker.last(target.level(), target.pos());
        return origin == null ? null : origin.playerId();
    }


    // Use the camera hit as the authenticated player carried by a display or other graph event
    public boolean interactGraphCamera(AdvancedGraphDocument.Node node, AdvancedGraphDocument.Value target, @Nullable UUID playerId){
        CameraBlockEntity camera = graphCamera(node, target);
        if(camera == null || playerId == null || level == null || level.getServer() == null) return false;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        return player != null && com.rieno.gadgetsandgizmos.lib.view.ViewBlockInteractions.interact(player,
                com.rieno.gadgetsandgizmos.lib.view.ViewReference.of(camera));
    }

    // Enter the selected camera only for the player responsible for this execution
    public void controlGraphCamera(AdvancedGraphDocument.Node node, AdvancedGraphDocument.Value target, @Nullable UUID playerId){
        CameraBlockEntity camera = graphCamera(node, target);
        if(camera == null || level == null || level.getServer() == null) return;
        var ref = com.rieno.gadgetsandgizmos.lib.view.ViewReference.of(camera);
        if(playerId != null){
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if(player != null) com.rieno.gadgetsandgizmos.lib.view.RemoteViewSessions.open(player, ref);
            return;
        }
    }


    // Keep a face target bound to its support when the replaceable plane cell is occupied
    private boolean linkedFaceSupportPresent(ControllerDiscoveryNode discovery){
        if(discovery == null || discovery.blockPos() == null || level == null) return false;
        for(var linked : ContraptionNetworkLinkerData.readTargets(getStoredLinker())){
            if(!linked.scope().usesFaces() || !ContraptionNetworkLinkerData.nodeIdForTarget(linked).equals(discovery.nodeId()))
                continue;
            Level targetLevel = SubLevelBlockEntityCollector.resolveTargetLevel(level, linked.subLevelId());
            if(targetLevel == null) return false;
            for(var face : linked.faces()){
                BlockPos support = linked.blockPos().relative(face.face().getOpposite());
                if(targetLevel.isLoaded(support) && !targetLevel.getBlockState(support).isAir()) return true;
            }
        }
        return false;
    }


    // Linker targets require current membership even while their blocks still exist
    private static boolean isLinkerGraphTarget(@Nullable ControllerDiscoveryNode target){
        return target != null && ContraptionNetworkLinkerData.isLinkerFaceTarget(target.asDirectTargetReference());
    }


    // Keep a removed face unavailable instead of choosing another face automatically
    private static boolean graphTargetFaceAvailable(AdvancedGraphDocument.Node node, ControllerDiscoveryNode target){
        Direction face = configuredDirection(node, "face");
        return !ContraptionNetworkLinkerData.nodeUsesFaceOptions(target) || face == null
                || ContraptionNetworkLinkerData.faceOptionsForNode(target).stream().anyMatch(option -> option.face() == face);
    }


    // Withdraw held signals from single and composed targets whose linker membership is unavailable
    private boolean hasUnavailableLinkerGraphTarget(AdvancedGraphDocument.Node node, boolean absent){
        List<ControllerDiscoveryNode> selected = new ArrayList<>();
        ControllerDiscoveryNode primary = ControllerDiscoveryNode.fromTag(node.data().getCompound("TargetData"));
        if(primary != null) selected.add(primary);
        for(Tag raw : node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND)){
            ControllerDiscoveryNode target = ControllerDiscoveryNode.fromTag((CompoundTag) raw);
            if(target != null) selected.add(target);
        }
        for(ControllerDiscoveryNode target : selected){
            if(!isLinkerGraphTarget(target)) continue;
            ControllerDiscoveryNode current = absent ? null : findCurrentTarget(target);
            if(current == null || retainedLinkerGraphTarget(node, target, List.of(current)) == null) return true;
        }
        return false;
    }


    // Include SCM membership when retaining selections while keeping signal discovery unchanged
    private static List<ControllerDiscoveryNode> graphLinkerTargets(ItemStack linker){
        return ContraptionNetworkLinkerData.toDiscoveryNodes(ContraptionNetworkLinkerData.readTargets(linker).stream()
                .map(target -> target.mode() == ContraptionNetworkLinkerData.LinkMode.SCM
                        ? new ContraptionNetworkLinkerData.LinkedTarget(target.blockPos(), target.subLevelId(),
                        target.blockId(), target.label(), ContraptionNetworkLinkerData.LinkMode.OUTPUT,
                        target.scope(), target.faces()) : target).toList());
    }


    // Follow a Sable assembly remap before checking the retained linker's membership
    private @Nullable ControllerDiscoveryNode retainedCurrentLinkerGraphTarget(AdvancedGraphDocument.Node node,
            ControllerDiscoveryNode stale, List<ControllerDiscoveryNode> linkerTargets){
        ControllerDiscoveryNode res = retainedLinkerGraphTarget(node, stale, linkerTargets);
        if(res != null || stale == null || level == null) return res;
        ControllerDirectTargetReference moved = ControllerRedstoneCompat.resolveMovingTarget(level, stale.asDirectTargetReference());
        if(moved == null || moved.blockPos() == null) return null;
        ControllerDiscoveryNode relocated = new ControllerDiscoveryNode(
                ContraptionNetworkLinkerData.baseNodeIdFromTargetId(moved.targetId()), stale.kind(), moved.groupId(),
                stale.blockId(), stale.label(), moved.subLevelId(), moved.blockPos());
        return retainedLinkerGraphTarget(node, relocated, linkerTargets);
    }


    // Update references without changing a retained target's generated ports or selected face
    private static void remapGraphTargetSelection(AdvancedGraphDocument.Node node,
            ControllerDiscoveryNode stale, ControllerDiscoveryNode current){
        String prevId = stale.nodeId(), nextId = current.nodeId();
        CompoundTag defaults = node.data().getCompound("Defaults");
        for(String port : List.of("target", "source")){
            CompoundTag entry = defaults.getCompound(port);
            if(prevId.equals(entry.getCompound("Payload").getString("NodeId"))){
                entry.put("Payload", current.toTag());
            }
        }
        if(prevId.equals(nextId)) return;
        if(!node.data().getList(AdvancedGraphCatalog.DATA_TARGETS_TAG, Tag.TAG_COMPOUND).isEmpty()){
            CompoundTag portIds = node.data().getCompound("DataTargetPortIds");
            String stableId = portIds.contains(prevId) ? portIds.getString(prevId) : prevId;
            portIds.remove(prevId);
            portIds.putString(nextId, stableId);
            node.data().put("DataTargetPortIds", portIds);
        }
        CompoundTag aliases = node.data().getCompound(com.rieno.gadgetsandgizmos.lib.graph.GraphTargetSelection.ALIASES_TAG);
        if(aliases.contains(prevId)){
            aliases.putString(nextId, aliases.getString(prevId));
            aliases.remove(prevId);
        }
        CompoundTag faces = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG);
        if(faces.contains(prevId)){
            Tag face = faces.get(prevId).copy();
            faces.remove(prevId);
            faces.put(nextId, face);
        }
        CompoundTag bindings = node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_BINDINGS_TAG);
        for(String port : bindings.getAllKeys()){
            for(Tag raw : bindings.getList(port, Tag.TAG_COMPOUND)){
                CompoundTag binding = (CompoundTag) raw;
                if(prevId.equals(binding.getString("Target"))) binding.putString("Target", nextId);
            }
        }
        for(String port : List.of("target", "source")){
            CompoundTag entry = defaults.getCompound(port);
            if(prevId.equals(entry.getCompound("Payload").getString("Value"))){
                putGraphDefault(node.data(), port, "target", nextId);
            }
        }
    }


    // Retain a physical target only while its previously selected face remains on the linker
    private static @Nullable ControllerDiscoveryNode retainedLinkerGraphTarget(AdvancedGraphDocument.Node node,
            ControllerDiscoveryNode stale, List<ControllerDiscoveryNode> linkerTargets){
        if(stale == null) return null;
        ControllerDiscoveryNode current = matchingLinkerTarget(stale, linkerTargets);
        if(current == null) return null;
        Direction face = Direction.byName(node.data().getCompound(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG)
                .getString(stale.nodeId()));
        if(face == null && stale.nodeId().equals(node.data().getString("Source"))){
            face = Direction.byName(node.data().getString("SourceFace"));
        }
        if(face == null && stale.nodeId().equals(node.data().getString("Target"))) face = configuredDirection(node, "face");
        List<ContraptionNetworkLinkerData.FaceOption> prevFaces = ContraptionNetworkLinkerData.faceOptionsForNode(stale);
        if(face == null && prevFaces.size() == 1) face = prevFaces.getFirst().face();
        Direction selected = face;
        if(selected != null && ContraptionNetworkLinkerData.nodeUsesFaceOptions(current)
                && ContraptionNetworkLinkerData.faceOptionsForNode(current).stream()
                .noneMatch(option -> option.face() == selected)) return null;
        return current;
    }


    // Match physical membership independently of the linker's current input/output role
    private static @Nullable ControllerDiscoveryNode matchingLinkerTarget(ControllerDiscoveryNode stale,
            List<ControllerDiscoveryNode> linkerTargets){
        ControllerDiscoveryNode res = null;
        for(ControllerDiscoveryNode candidate : linkerTargets){
            if(candidate == null) continue;
            if(candidate.nodeId().equals(stale.nodeId())) return candidate;
            if(!candidate.hasSameBlockTarget(stale)) continue;
            if(res != null) return null;
            res = candidate;
        }
        return res;
    }


    // Populate the current linker bindings after retired imports have been removed
    private static boolean populateLinkerBindings(AdvancedGraphDocument graph,
            Map<String, ContraptionNetworkLinkerData.ChannelBind> channelBindings,
            List<CompoundTag> customEntryBindings, List<ControllerDiscoveryNode> linkerTargets,
            HolderLookup.Provider provider){
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


    // Identify one imported input or output independently of its selected target
    private static String importedBindingKey(AdvancedGraphDocument.Node node){
        return node.type() + ":" + node.data().getString("BindingId");
    }


    // Retarget imports in place so existing wires and layout keep their identifiers
    private static ImportedNode upsertImportedNode(AdvancedGraphDocument graph, AdvancedGraphDocument.Node desired){
        for(AdvancedGraphDocument.Node node : graph.nodes()){
            if(!node.data().getBoolean("ImportedLinkerBinding")
                    || !importedBindingKey(node).equals(importedBindingKey(desired))) continue;
            CompoundTag prev = node.data().copy();
            for(String key : List.of("BindingId", "BindingLabel", "GraphOwnedBinding", "KeyCode",
                    "Target", "TargetLabel", "TargetData", "Face", "FrequencyFirst", "FrequencySecond",
                    "FrequencyFirstStack", "FrequencySecondStack")){
                node.data().remove(key);
                if(desired.data().contains(key)) node.data().put(key, desired.data().get(key).copy());
            }
            CompoundTag defaults = node.data().getCompound("Defaults");
            for(String port : List.of("target", "face", "frequency")){
                defaults.remove(port);
                if(desired.data().getCompound("Defaults").contains(port)){
                    defaults.put(port, desired.data().getCompound("Defaults").get(port).copy());
                }
            }
            if(!defaults.isEmpty()) node.data().put("Defaults", defaults);
            return new ImportedNode(node.id(), !prev.equals(node.data()));
        }
        if(graph.nodes().size() >= AdvancedGraphDocument.maxNodes()) return null;
        graph.nodes().add(desired);
        return new ImportedNode(desired.id(), true);
    }


    // Expose one resolved data target
    public record GraphDataTarget(Level level, BlockPos pos, @Nullable Direction side) {
    }


    // Retain an SCM manifest after its block is broken so a replacement on the same hull can recover it.
    @Override
    protected boolean retainControllerManifestAfterDestroy() {
        return SimulatedHelper.getContainingSubLevelId(this) != null;
    }


    // Restore settings only after a real module has been placed on the retained hull
    private void restoreScmPersistenceIfAvailable() {
        if (scmPersistenceRestoreAttempted || getLevel() == null || getLevel().isClientSide
                || !controllerManifestId().isBlank() || !isMountedOnShipControlModule()) {
            return;
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (subLevelId == null) {
            return;
        }
        scmPersistenceRestoreAttempted = true;
        ControllerSqliteStore.ScmPersistenceBinding binding =
                ControllerSqliteStore.findScmPersistence(getLevel(), subLevelId, scmPersistenceId);
        if (binding == null) {
            return;
        }
        if (restoreControllerManifest(binding.manifestId(), getLevel().registryAccess())) {
            scmPersistenceId = binding.scmId();
            scmPersistenceSubLevelId = subLevelId;
            setChanged();
            sendData();
        }
    }


    // Persist a surviving SCM when only its Sable sublevel id has changed.
    private void syncScmPersistenceSubLevel() {
        if (getLevel() == null || getLevel().isClientSide || controllerManifestId().isBlank()
                || !isMountedOnShipControlModule()) {
            return;
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (subLevelId != null && !subLevelId.equals(scmPersistenceSubLevelId)) {
            saveControllerManifestNow();
        }
    }


    // Keep the current placers with their physical mount when a retained program is recovered
    @Override
    protected void writeControllerPhysicalState(CompoundTag tag, HolderLookup.Provider provider){
        super.writeControllerPhysicalState(tag, provider);
        if(shipPermissionPlacerId != null) tag.putUUID(SHIP_PERMISSION_PLACER_TAG, shipPermissionPlacerId);
        if(shipPermissionModulePlacerId != null) tag.putUUID(SHIP_PERMISSION_MODULE_PLACER_TAG, shipPermissionModulePlacerId);
    }
}
