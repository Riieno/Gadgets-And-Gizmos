package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDirectThrottleAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngles;
import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import com.rieno.gadgetsandgizmos.compat.computercraft.WheelMountControlBridge;
import com.rieno.gadgetsandgizmos.compat.controller.ExternalBlockEntityDirectControlCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelParticleOcclusion;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.neoforge.ScmPathDebugService;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffInteractionGuard;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnection;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyCache;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticGraphHelper;
import com.rieno.gadgetsandgizmos.lib.navigation.GroundPathPlanner;
import com.rieno.gadgetsandgizmos.lib.navigation.LocalDetourPlanner;
import com.rieno.gadgetsandgizmos.lib.navigation.ReactiveCollisionAvoidance;
import com.rieno.gadgetsandgizmos.lib.navigation.WaypointProgressTracker;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbe;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlProbeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlAuthorityApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlMode;
import com.rieno.gadgetsandgizmos.lib.scm.ScmControlModeRegistry;
import com.rieno.gadgetsandgizmos.lib.scm.ScmFlightBehavior;
import com.rieno.gadgetsandgizmos.lib.scm.ScmMapCompositionApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmTarget;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.directional_gearshift.DirectionalGearshiftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3fc;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Map the ship and handle pilot, graph and schedule control
public final class ShipControlModuleRuntime {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CONTROL_CHANNEL = "ship_control_module";
    private static final String DIRECT_ACTUATOR_ADAPTER = "direct_signal_v5";
    private static final String ENVELOPE_ACTUATOR_ADAPTER = "digital_envelope_v3";
    private static final String PROPULSION_VECTOR_ADAPTER = "create_propulsion_vector_thruster";
    private static final double[] THROTTLE_STEPS = {0.0D, 0.15D, 0.35D, 0.6D, 0.8D, 1.0D};
    private static final double[] ENVELOPE_STEPS = {0.0D, 0.25D, 0.5D, 0.75D, 1.0D};
    private static final double[] V2_THROTTLE_STEPS = {
            0.0D, 0.125D, 0.25D, 0.375D, 0.5D, 0.625D, 0.75D, 0.875D, 1.0D};
    private static final double[] PRECISE_ENVELOPE_EFFECTIVE_STEPS = {
            0.0001D, 0.001D, 0.01D, 0.05D};
    private static final double[] V2_ENVELOPE_EFFECTIVE_STEPS = {
            0.0D, 0.0001D, 0.001D, 0.01D, 0.05D,
            0.125D, 0.25D, 0.375D, 0.5D, 0.625D, 0.75D, 0.875D, 1.0D};
    private static final double V2_MATRIX_PROBE_THROTTLE = 0.01D;
    private static final int V2_BEARING_SAMPLES_PER_AXIS = 9;
    private static final int BEARING_POSE_TIMEOUT_TICKS = 80;
    private static final int INITIALIZATION_STAGES = 5;
    private static final double ANGLE_TOLERANCE_DEGREES = 1.5D;
    private static final double ANGULAR_VELOCITY_TOLERANCE = 0.04D;
    private static final double POSITION_TOLERANCE = 0.35D;
    private static final double LINEAR_VELOCITY_TOLERANCE = 0.08D;
    private static final double CONTROL_TICK_SECONDS = 0.05D;
    private static final double POSITION_INTEGRAL_RANGE = 6.0D;
    private static final double POSITION_INTEGRAL_LIMIT = 8.0D;
    private static final double DOCKING_COLLISION_APPROACH_DISTANCE = 3.0D;
    private static final double POSITION_INTEGRAL_GAIN = 0.04D;
    private static final double POSITION_VELOCITY_GAIN = 0.6D;
    // Demands below this threshold do not select either side of an opposing
    // face-bound action pair. This keeps an idle scheduled vehicle neutral
    // instead of arbitrarily powering one of its two physical inputs.
    private static final double ACTION_DIRECTION_EPSILON = 1.0E-4D;
    private static final double COLLISION_SCAN_RANGE =
            AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE;
    private static final double COLLISION_HULL_MARGIN = 0.125D;
    // Navigation scans project the current motion through a braking envelope
    // instead of using the fixed telemetry range. This keeps the controller
    // looking far enough ahead as a craft gains speed, without imposing a
    // synthetic top-speed governor on the propulsion system.
    private static final double NAVIGATION_BRAKING_ACCELERATION = 2.5D;
    private static final double NAVIGATION_RESPONSE_SECONDS = 0.35D;
    private static final double NAVIGATION_LOOKAHEAD_SECONDS = 2.0D;
    // Roll the six hull directions across a few ticks instead of producing one long server hitch.
    private static final int COLLISION_TELEMETRY_DIRECTIONS_PER_TICK = 2;
    // Collision telemetry needs a representative leading face; navigation keeps its denser probe grid.
    private static final int COLLISION_TELEMETRY_PROBES_PER_BOUNDS = 16;
    private static final int MAX_COLLISION_TELEMETRY_CONFIGURATIONS = 16;
    private static final int COLLISION_NAVIGATION_PROBES_PER_BOUNDS = 64;
    private static final double NAVIGATION_MIN_CLEARANCE = 3.0D;
    private static final double NAVIGATION_PATH_MIN_STEP = 0.75D;
    private static final double NAVIGATION_PATH_MAX_STEP = 2.0D;
    private static final int NAVIGATION_FAILED_RETRY_TICKS = 40;
    private static final int LOCAL_DETOUR_RETRY_TICKS = 20;
    private static final int LOCAL_DETOUR_MAX_FORWARD_STEPS = 5;
    private static final int LOCAL_DETOUR_MAX_SIDE_STEPS = 4;
    private static final int LOCAL_DETOUR_MAX_VERTICAL_STEPS = 3;
    private static final int LOCAL_DETOUR_MAX_EXPANSIONS = 96;
    private static final int GROUND_NAVIGATION_STUCK_TICKS = 40;
    private static final double GROUND_NAVIGATION_MIN_CLEARANCE = 0.5D;
    private static final double GROUND_REVERSE_ALIGNMENT = -0.35D;
    private static final double GROUND_NAVIGATION_CONTACT_CLEARANCE = 0.25D;
    private static final double GROUND_NAVIGATION_WAYPOINT_RADIUS_STEPS = 0.5D;
    private static final double GROUND_BRAKING_ACCELERATION = 2.5D;
    private static final double GROUND_SPEED_CONTROL_GAIN = 0.18D;
    private static final double AIRCRAFT_MIN_SAFE_CLEARANCE = 14.0D;
    private static final double AIRCRAFT_MAX_SAFE_CLEARANCE = 32.0D;
    private static final double AIRCRAFT_LANDING_CLEARANCE = 0.65D;
    private static final double AIRCRAFT_MIN_APPROACH_DISTANCE = 28.0D;
    private static final double AIRCRAFT_LANDING_PHASE_DISTANCE = 6.0D;
    private static final double AIRCRAFT_CONTROL_ALIGNMENT = 0.3D;
    private static final double AIRCRAFT_FORWARD_ALIGNMENT = 0.65D;
    private static final double AIRCRAFT_MIN_MANEUVER_SPEED = 0.3D;
    private static final System.Logger LOGGER = System.getLogger(ShipControlModuleRuntime.class.getName());
    private static final Set<ShipControlModuleRuntime> LIVE_RUNTIMES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        ScmControlProbeRegistry.register(
                ResourceLocation.fromNamespaceAndPath("createthrusters", "builtin_scm_controls"),
                0, ShipControlModuleRuntime::createBuiltInScmProbes);
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Ship control module controller
    private final AdvancedContraptionControllerBlockEntity controller;
    // Assembly topology cache
    private final SableAssemblyTopologyCache assemblyTopologyCache =
            new SableAssemblyTopologyCache(
                    (owner, actor) -> isAssemblyTopologyActor(actor),
                    (owner, actor, target) -> SableAssemblyConnection.Kind.STRUCTURAL);
    // Tracked freeze handles
    private final List<PhysicsConstraintHandle> freezeHandles = new ArrayList<>();
    // Tracked nested freeze handles
    private final List<PhysicsConstraintHandle> nestedFreezeHandles = new ArrayList<>();
    // Freeze handles indexed by sub-level
    private final Map<UUID, PhysicsConstraintHandle> freezeHandlesBySubLevel =
            new LinkedHashMap<>();
    // Tracked calibration units
    private final List<CalibrationUnit> calibrationUnits = new ArrayList<>();
    // Tracked calibration bearings
    private final List<BearingCalibrationUnit> calibrationBearings = new ArrayList<>();
    // Tracked calibration docking connectors
    private final List<ShipControlMap.DockingConnector> calibrationDockingConnectors =
            new ArrayList<>();
    // Tracked calibration CRN displays
    private final List<ShipControlMap.CrnDisplay> calibrationCrnDisplays = new ArrayList<>();
    // Tracked initialization CRN displays
    private final Set<ShipControlMap.CrnDisplay> initCrnDisplays =
            new LinkedHashSet<>();
    // Tracked calibration ACC displays
    private final List<ShipControlMap.AccDisplay> calibrationAccDisplays = new ArrayList<>();
    // Tracked init ACC displays
    private final Set<ShipControlMap.AccDisplay> initAccDisplays =
            new LinkedHashSet<>();
    // Last init disp pct
    private int lastInitDispPct = -1;
    // Last init disp status
    private String lastInitDispStatus = "";
    // Tracked calibration sub levels
    private final Map<UUID, SubLevel> calibrationSubLevels = new LinkedHashMap<>();
    // Tracked initialization sub levels
    private final Map<UUID, SubLevel> initializationSubLevels = new LinkedHashMap<>();
    // Tracked init body states
    private final Map<UUID, InitializationBodyState> initBodyStates =
            new LinkedHashMap<>();
    // Tracked init protected sub-level ids
    private final Set<UUID> initProtectedSubLevelIds = new HashSet<>();
    // Tracked init tracking ids
    private final Map<UUID, UUID> initTrackingIds = new LinkedHashMap<>();
    // Tracked control actuators
    private final Map<Integer, Actuator> controlActuators = new LinkedHashMap<>();
    // Tracked reversed cal dirs
    private final Map<Integer, Boolean> reversedCalDirs = new HashMap<>();
    // Current cal dir map id
    private @Nullable UUID calDirMapId;
    // Tracked applied control values
    private final Map<Integer, Double> appliedControlValues = new LinkedHashMap<>();
    // Tracked control bearings
    private final Map<Integer, BearingActuator> controlBearings = new LinkedHashMap<>();
    // Selected bearing poses
    private final Map<Integer, Integer> selectedBearingPoses = new LinkedHashMap<>();
    // Tracked dynamic connector indices
    private final Map<DynamicConnectorKey, Integer> dynamicConnectorIndices =
            new LinkedHashMap<>();
    // Next dynamic connector idx
    private int nextDynamicConnectorIdx;
    // Pending orphan cleanup units
    private final Map<Integer, ShipControlMap.PropulsionUnit> pendingOrphanCleanupUnits =
            new LinkedHashMap<>();
    // Current initialization filters
    private InitializationFilters initializationFilters = InitializationFilters.NONE;
    // True only while gathering a player-facing configuration snapshot. This phase
    // deliberately reuses discovery without adding fixed constraints or pulsing an
    // actuator, so players can group/blacklist a moving but otherwise idle craft.
    private boolean configurationScanOnly;
    // A saved SCM profile already identifies the actuators the player intends
    // to control. In that case initialization reads those live adapters
    // directly instead of entering the old freeze-and-probe calibration path.
    private boolean profileDrivenInitialization;
    // Stable block identities from the most recent no-actuation configuration scan.
    private List<ScmConfigurationProfile.UnitReference> configurationCandidates = List.of();
    // Current map id
    private @Nullable UUID mapId;
    // Current map
    private @Nullable ShipControlMap map;
    // Active assembly map
    private @Nullable ShipControlMap activeAssemblyMap;
    // Active assembly primary map
    private @Nullable ShipControlMap activeAssemblyPrimaryMap;
    // Active assembly topology
    private @Nullable SableAssemblyTopologyApi.Topology activeAssemblyTopology;
    // Tracked main carriage maps
    private final Map<UUID, ShipControlMap> mainCarriageMaps = new LinkedHashMap<>();
    // Main carriage map validation tick count
    private final Map<UUID, Long> mainCarriageMapValidationTicks = new LinkedHashMap<>();
    // Tracked main carriage map validation revisions
    private final Map<UUID, Long> mainCarriageMapValidationRevisions = new LinkedHashMap<>();
    // Tracked dirty main carriage map ids
    private final Set<UUID> dirtyMainCarriageMapIds = new LinkedHashSet<>();
    // Tracked init foreign maps
    private Map<UUID, ForeignScmMap> initForeignMaps = Map.of();
    // Tracked init foreign sub-level ids
    private Set<UUID> initForeignSubLevelIds = Set.of();
    // Active SCM actuator owners
    private Map<AssemblyUnitIdentity, AdvancedContraptionControllerBlockEntity>
            activeScmActuatorOwners = Map.of();
    // Active assembly map signature
    private long activeAssemblyMapSignature = Long.MIN_VALUE;
    // Active assembly sub-level ids
    private Set<UUID> activeAssemblySubLevelIds = Set.of();
    // Active carriage count
    private int activeCarriageCount;
    // Absorbed SCM map count
    private int absorbedScmMapCount;
    // Last main carriage map safety refresh tick
    private long lastMainCarriageMapSafetyRefreshTick = Long.MIN_VALUE;
    // Current claimed assembly authority key
    private @Nullable String claimedAssemblyAuthorityKey;
    // Current controller authority id
    private @Nullable UUID controllerAuthorityId;
    // Tracks whether assembly aggregator is set
    private boolean assemblyAggregator;
    // Current suppressed foreign topology fingerprint
    private String suppressedForeignTopologyFingerprint = "";
    // Current reconciliation base map
    private @Nullable ShipControlMap reconciliationBaseMap;
    // Current prev init map
    private @Nullable ShipControlMap prevInitMap;
    // Current root sub-level
    private @Nullable ServerSubLevel rootSubLevel;
    // Current phase
    private Phase phase = Phase.IDLE;
    // Current ship control module status
    private String status = "Not initialized";
    // Current progress
    private double progress;
    // Calibration unit index
    private int calibrationUnitIndex;
    // Calibration sample index
    private int calibrationSampleIndex;
    // Sample settle tick count
    private int sampleSettleTicks;
    // Tracks whether sample is applied
    private boolean sampleApplied;
    // Tracks whether sample is neutralized
    private boolean sampleNeutralized;
    // Tracks whether sample neutral window started is set
    private boolean sampleNeutralWindowStarted;
    // Tracked sample baseline impulses
    private Map<UUID, ConstraintImpulse> sampleBaselineImpulses = Map.of();
    // Tracked sample baseline wheel contacts
    private Map<WheelContactKey, WheelMountControlBridge.PhysicalSample>
            sampleBaselineWheelContacts = Map.of();
    // Current sample neutral constraint response
    private ConstraintResponseSample sampleNeutralConstraintResponse =
            new ConstraintResponseSample(
                    new ConstraintResponse(Vec3.ZERO, Vec3.ZERO), 0L);
    // Current sample neutral wheel response
    private WheelContactResponse sampleNeutralWheelResponse =
            new WheelContactResponse(new ConstraintResponse(Vec3.ZERO, Vec3.ZERO), 0L);
    // Bearing calibration index
    private int bearingCalibrationIndex;
    // Bearing pose index
    private int bearingPoseIndex;
    // Bearing throttle index
    private int bearingThrottleIndex;
    // Bearing pose wait tick count
    private int bearingPoseWaitTicks;
    // Tracks whether bearing pose is applied
    private boolean bearingPoseApplied;
    // Tracks whether bearing throttle is applied
    private boolean bearingThrottleApplied;
    // Tracks whether map load is attempted
    private boolean mapLoadAttempted;
    // Last progress display tick
    private long lastProgressDisplayTick = Long.MIN_VALUE;
    // Tracks whether init final msg sent is set
    private boolean initFinalMsgSent;
    // Current orphan cleanup attempts remaining
    private int orphanCleanupAttemptsRemaining;
    // Next orphan cleanup tick
    private long nextOrphanCleanupTick = Long.MIN_VALUE;
    // Active commands
    private final Map<String, ActiveShipCommand> activeCommands = new LinkedHashMap<>();
    // Last issued commands
    private final Map<String, ActiveShipCommand> lastIssuedCommands = new LinkedHashMap<>();
    // Current magnetic connector idx
    private int magneticConnectorIdx = -1;
    // Tracked completed commands
    private final Set<String> completedCommands = new HashSet<>();
    // Tracked coupler control requests
    private final Map<String, CouplerControlRequest> couplerControlRequests =
            new LinkedHashMap<>();
    // Tracked navigation path states
    private final Map<String, NavigationPathState> navigationPathStates = new LinkedHashMap<>();
    // Tracked position integral errors
    private final Map<String, Vec3> positionIntegralErrors = new LinkedHashMap<>();
    // Attitude references retained by direct-vector airship navigation so a
    // constant off-centre actuator or external impulse cannot leave the craft
    // slowly rotating after the translational correction ends.
    private final Map<String, Vec3> airshipAttitudeHoldTargets = new LinkedHashMap<>();
    // Tracked aircraft navigation states
    private final Map<String, AircraftNavigationState> aircraftNavigationStates =
            new LinkedHashMap<>();
    // Tracked collision telemetry states
    private final Map<CollisionTelemetryKey, CollisionTelemetryState>
            collisionTelemetryStates = new LinkedHashMap<>();
    // Collision ctx tick
    private long collisionCtxTick = Long.MIN_VALUE;
    // Current collision ctx
    private @Nullable CollisionScanContext collisionCtx;
    // Current collision topology fingerprint
    private String collisionTopologyFingerprint = "";
    // Collision distance cache tick
    private long collisionDistanceCacheTick = Long.MIN_VALUE;
    // Cached collision distance
    private final Map<CollisionProbe, Double> collisionDistanceCache = new HashMap<>();
    // Path trace cache tick
    private long pathTraceCacheTick = Long.MIN_VALUE;
    // Cached path trace
    private final Map<CollisionProbe, Double> pathTraceCache = new HashMap<>();
    // Collision probe cache tick
    private long collisionProbeCacheTick = Long.MIN_VALUE;
    // Collision probe cache
    private final SubLevelParticleOcclusion.ProbeCache collisionProbeCache =
            new SubLevelParticleOcclusion.ProbeCache();
    // Telemetry tick
    private long telemetryTick = Long.MIN_VALUE;
    // Current telemetry root id
    private @Nullable UUID telemetryRootId;
    // Cached telemetry
    private Telemetry cachedTelemetry = Telemetry.EMPTY;
    // Cached structural simulation metrics. The SCM workspace and display
    // nodes can request many fields in one tick, so sample assembly physics once.
    private long simulationMetricsTick = Long.MIN_VALUE;
    private @Nullable UUID simulationMetricsRootId;
    private SimulationMetrics cachedSimulationMetrics = SimulationMetrics.EMPTY;
    // Connected sub levels tick
    private long connectedSubLevelsTick = Long.MIN_VALUE;
    // Current connected sub levels root id
    private @Nullable UUID connectedSubLevelsRootId;
    // Cached connected sub levels
    private List<SubLevel> cachedConnectedSubLevels = List.of();
    // Tracked connected sub-level idx
    private Map<UUID, SubLevel> connectedSubLevelIdx = Map.of();
    // Cached assembly topology
    private @Nullable SableAssemblyTopologyApi.Topology cachedAssemblyTopology;
    // Current navigation residency
    private @Nullable SableSubLevelResidency.Lease navigationResidency;
    // Current navigation residency owner
    private @Nullable UUID navigationResidencyOwner;
    // Navigation residency sync tick
    private long navigationResidencySyncTick = Long.MIN_VALUE;
    // Current navigation residency sync root id
    private @Nullable UUID navigationResidencySyncRootId;
    // Allocation workspace
    private final ShipControlAllocator.Workspace allocationWorkspace =
            new ShipControlAllocator.Workspace();
    // Tracked damage protected thrusters
    private final Set<ThrusterBlockEntity> damageProtectedThrusters = new HashSet<>();
    // Last damage protection refresh tick
    private long lastDamageProtectionRefreshTick = Long.MIN_VALUE;
    // Last CRN display publish tick
    private long lastCrnDisplayPublishTick = Long.MIN_VALUE;
    // Tracked controlled SCM wheels
    private final Set<WheelMountControlBridge> controlledScmWheels =
            Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    // Exact linker face bindings which own each wheel's signed inputs
    private final Map<WheelMountControlBridge, List<ScmTarget>> controlledScmWheelTargets =
            new java.util.IdentityHashMap<>();
    // Last SCM wheel refresh tick
    private long lastScmWheelRefreshTick = Long.MIN_VALUE;
    // Last SCM wheel topology fingerprint
    private String lastScmWheelTopologyFingerprint = "";
    // Last active SCM profile face-action fingerprint for wheel discovery
    private String lastScmWheelActionFingerprint = "";
    // Ground drive check tick
    private long groundDriveCheckTick = Long.MIN_VALUE;
    // Current ground drive topology fingerprint
    private String groundDriveTopologyFingerprint = "";
    // Tracks whether ground drive is cached
    private boolean cachedGroundDrive;
    // Scm wheel calibration tick
    private int scmWheelCalibrationTick;
    // Tracks whether SCM wheel calibration is complete
    private boolean scmWheelCalibrationComplete;
    // Tracks whether shutdown is prepared
    private boolean shutdownPrepared;
    // Tracks whether resume init after load is set
    private boolean resumeInitAfterLoad;
    // Current resume init tries
    private int resumeInitTries;
    // Tracks whether resume pose hold after load is set
    private boolean resumePoseHoldAfterLoad;
    // Tracks whether resume pose hold release is requested
    private boolean resumePoseHoldReleaseRequested;
    // Resume pose hold tick count
    private int resumePoseHoldTicks;
    // Resume pose hold timeout tick count
    private int resumePoseHoldTimeoutTicks;
    // Current resume pose hold load attempts
    private int resumePoseHoldLoadAttempts;
    // Current resumed initialization filters
    private InitializationFilters resumedInitializationFilters = InitializationFilters.NONE;
    // Current resumed initialization ship name
    private String resumedInitializationShipName = "";
    private static final int RESUME_POSE_HOLD_TICKS = 2;
    private static final int RESUME_POSE_HOLD_TIMEOUT_TICKS = 20 * 15;
    private static final long MAIN_CARRIAGE_MAP_SAFETY_REFRESH_TICKS = 200L;
    private static final int MAX_RESUME_POSE_HOLD_LOAD_ATTEMPTS = 200;
    // Tracks whether initialization V2 is set
    private boolean initializationV2;
    // Tracks whether V2 samples finalized is set
    private boolean v2SamplesFinalized;
    // Tracks whether V2 representative sweep started is set
    private boolean v2RepresentativeSweepStarted;
    // Current V2 completed propulsion samples
    private int v2CompletedPropulsionSamples;
    // Current V2 total propulsion samples
    private int v2TotalPropulsionSamples;
    // Car wheel first calibration index
    private int carWheelFirstCalibrationIndex = -1;
    // Tracks whether car wheel calibration is pending
    private boolean carWheelCalibrationPending;
    // Tracks whether car wheel calibration started is set
    private boolean carWheelCalibrationStarted;
    // Current car wheel drive actuator
    private @Nullable Actuator carWheelDriveActuator;

    // Initialize the ship control module
    public ShipControlModuleRuntime(AdvancedContraptionControllerBlockEntity controller) {
        this.controller = controller;
        LIVE_RUNTIMES.add(this);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Close every server control runtime
    public static void closeAllForServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<ShipControlModuleRuntime> runtimes;
        synchronized (LIVE_RUNTIMES) {
            runtimes = List.copyOf(LIVE_RUNTIMES);
        }
        for (ShipControlModuleRuntime runtime : runtimes) {
            Level level = runtime.controller.getLevel();
            if (level != null && level.getServer() == server) {
                runtime.discardForServerShutdown();
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                    PERSISTENCE / RESTORE
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the shutdown snapshot
    public CompoundTag createShutdownSnapshot() {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("Phase", phase.name());
        snapshot.putString("Status", status);
        snapshot.putDouble("Progress", progress);
        snapshot.putBoolean("ResumeInitialization", isInitializing());
        snapshot.putBoolean("HoldPoseUntilLoaded",
                phase != Phase.IDLE && phase != Phase.ERROR);
        snapshot.putBoolean("DisplayProgress", initializationFilters.displayProgress());
        snapshot.putBoolean("ForceFullInitialization",
                initializationFilters.forceFullInitialization());
        snapshot.putBoolean("IgnoreBearings", initializationFilters.ignoreBearings());
        snapshot.putBoolean("IgnoreSails", initializationFilters.ignoreSails());
        snapshot.putBoolean("IgnoreThrusters", initializationFilters.ignoreThrusters());
        snapshot.putString("ShipName", controller.getShipName());
        ListTag initializationTrackingPoints = new ListTag();
        initTrackingIds.forEach((subLevelId, trackingPointId) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("SubLevel", subLevelId);
            entry.putUUID("TrackingPoint", trackingPointId);
            initializationTrackingPoints.add(entry);
        });
        snapshot.put("InitializationTrackingPoints", initializationTrackingPoints);
        snapshot.put("ActiveCommands", commandListTag(activeCommands.values().stream()
                .filter(command -> !command.id().startsWith("shipping_schedule"))
                .toList()));
        snapshot.put("LastIssuedCommands", commandListTag(lastIssuedCommands.values().stream()
                .filter(command -> !command.id().startsWith("shipping_schedule"))
                .toList()));
        CompoundTag completed = new CompoundTag();
        completedCommands.forEach(command -> completed.putBoolean(command, true));
        snapshot.put("CompletedCommands", completed);
        snapshot.put("CouplerControlRequests", couplerControlRequestTag());
        return snapshot;
    }

    // Restore the shutdown snapshot
    public void restoreShutdownSnapshot(CompoundTag snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        shutdownPrepared = false;
        resumePoseHoldAfterLoad = snapshot.getBoolean("HoldPoseUntilLoaded");
        resumePoseHoldReleaseRequested = true;
        resumePoseHoldTicks = resumePoseHoldAfterLoad ? RESUME_POSE_HOLD_TICKS : 0;
        resumePoseHoldTimeoutTicks = resumePoseHoldAfterLoad
                ? RESUME_POSE_HOLD_TIMEOUT_TICKS : 0;
        resumePoseHoldLoadAttempts = 0;
        activeCommands.clear();
        readCommands(snapshot.getList("ActiveCommands", Tag.TAG_COMPOUND), activeCommands);
        lastIssuedCommands.clear();
        readCommands(snapshot.getList("LastIssuedCommands", Tag.TAG_COMPOUND),
                lastIssuedCommands);
        completedCommands.clear();
        completedCommands.addAll(snapshot.getCompound("CompletedCommands").getAllKeys());
        readCouplerReqs(snapshot.getList(
                "CouplerControlRequests", Tag.TAG_COMPOUND));
        initTrackingIds.clear();
        ListTag initializationTrackingPoints = snapshot.getList(
                "InitializationTrackingPoints", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < initializationTrackingPoints.size(); idx++) {
            CompoundTag entry = initializationTrackingPoints.getCompound(idx);
            if (entry.hasUUID("SubLevel") && entry.hasUUID("TrackingPoint")) {
                initTrackingIds.put(
                        entry.getUUID("SubLevel"), entry.getUUID("TrackingPoint"));
            }
        }
        if (snapshot.getBoolean("ResumeInitialization")) {
            resumedInitializationFilters = new InitializationFilters(
                    snapshot.getBoolean("DisplayProgress"),
                    snapshot.getBoolean("ForceFullInitialization"),
                    snapshot.getBoolean("IgnoreBearings"),
                    snapshot.getBoolean("IgnoreSails"),
                    snapshot.getBoolean("IgnoreThrusters"));
            resumedInitializationShipName = snapshot.getString("ShipName");
            resumeInitAfterLoad = true;
            resumeInitTries = 0;
            phase = Phase.IDLE;
            status = "Resuming ship initialization";
            progress = 0.0D;
        }
    }

    // Get the command list tag
    private static ListTag commandListTag(Collection<ActiveShipCommand> commands) {
        ListTag res = new ListTag();
        for (ActiveShipCommand command : commands) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", command.id());
            tag.putString("Type", command.type());
            tag.putDouble("Amount", command.amount());
            tag.putDouble("Strength", command.strength());
            tag.putDouble("TargetY", command.targetY());
            putVector(tag, "TargetPosition", command.targetPosition());
            tag.putDouble("TargetSpeed", command.targetSpeed());
            tag.putDouble("DriveThrottle", command.driveThrottle());
            tag.putDouble("Tolerance", command.tolerance());
            tag.putBoolean("AvoidCollisions", command.avoidCollisions());
            tag.putBoolean("LockRotation", command.lockRotation());
            putVector(tag, "TargetAttitude", command.targetAttitude());
            tag.putString("TargetPoint", command.targetPoint().serializedName());
            tag.putInt("TargetConnector", command.targetConnectorIndex());
            putVector(tag, "TargetDirection", command.targetDirection());
            putVector(tag, "TargetUp", command.targetUp());
            res.add(tag);
        }
        return res;
    }

    // Read the commands
    private static void readCommands(
            ListTag commands, Map<String, ActiveShipCommand> destination
    ) {
        for (int idx = 0; idx < commands.size(); idx++) {
            CompoundTag tag = commands.getCompound(idx);
            ActiveShipCommand command = new ActiveShipCommand(
                    tag.getString("Id"), tag.getString("Type"),
                    tag.getDouble("Amount"), tag.getDouble("Strength"),
                    tag.getDouble("TargetY"), readVector(tag, "TargetPosition"),
                    tag.getDouble("TargetSpeed"),
                    tag.contains("DriveThrottle")
                            ? tag.getDouble("DriveThrottle") : -1.0D,
                    tag.getDouble("Tolerance"),
                    tag.getBoolean("AvoidCollisions"), tag.getBoolean("LockRotation"),
                    readVector(tag, "TargetAttitude"),
                    ShipTargetPoint.fromSerialized(tag.getString("TargetPoint")),
                    tag.getInt("TargetConnector"),
                    readVector(tag, "TargetDirection"), readVector(tag, "TargetUp"));
            destination.put(commandKey(command.id(), command.type()), command);
        }
    }

    // Put the vector
    private static void putVector(CompoundTag tag, String key, Vec3 val) {
        Vec3 vector = val == null ? Vec3.ZERO : finite(val);
        CompoundTag encoded = new CompoundTag();
        encoded.putDouble("X", vector.x);
        encoded.putDouble("Y", vector.y);
        encoded.putDouble("Z", vector.z);
        tag.put(key, encoded);
    }

    // Read the vector
    private static Vec3 readVector(CompoundTag tag, String key) {
        CompoundTag encoded = tag.getCompound(key);
        return new Vec3(encoded.getDouble("X"), encoded.getDouble("Y"),
                encoded.getDouble("Z"));
    }

    // Prepare the server shutdown
    public void prepareForServerShutdown() {
        if (shutdownPrepared) {
            return;
        }
        shutdownPrepared = true;
        releaseControlAuthority();
        stopActuators();
        releaseFreezeHandles();
        freezeAtCurrentPoseForShutdown();
        releaseInitProtection();
        detachNavResidency();
        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        initCrnDisplays.clear();
        initAccDisplays.clear();
        calibrationSubLevels.clear();
        initializationSubLevels.clear();
        initBodyStates.clear();
        initProtectedSubLevelIds.clear();
        clearInitMapPlan();
        controlActuators.clear();
        reversedCalDirs.clear();
        calDirMapId = null;
        appliedControlValues.clear();
        controlBearings.clear();
        selectedBearingPoses.clear();
        damageProtectedThrusters.clear();
        clearPendingOrphanCleanup();
        clearDynamicConnectorRegistry();
        activeCommands.clear();
        lastIssuedCommands.clear();
        completedCommands.clear();
        couplerControlRequests.clear();
        navigationPathStates.clear();
        positionIntegralErrors.clear();
        airshipAttitudeHoldTargets.clear();
        aircraftNavigationStates.clear();
        collisionTelemetryStates.clear();
        collisionDistanceCache.clear();
        pathTraceCache.clear();
        collisionProbeCache.clear();
        collisionCtx = null;
        cachedConnectedSubLevels = List.of();
        connectedSubLevelIdx = Map.of();
        cachedAssemblyTopology = null;
        assemblyTopologyCache.invalidate();
        allocationWorkspace.reset();
        cachedTelemetry = Telemetry.EMPTY;
        cachedSimulationMetrics = SimulationMetrics.EMPTY;
        rootSubLevel = null;
        map = null;
        activeAssemblyMap = null;
        activeAssemblyPrimaryMap = null;
        activeAssemblyTopology = null;
        activeScmActuatorOwners = Map.of();
        reconciliationBaseMap = null;
        prevInitMap = null;
        phase = Phase.IDLE;
        LIVE_RUNTIMES.remove(this);
    }

    // Stop the actuators
    private void stopActuators() {
        Set<Actuator> actuators = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        calibrationUnits.forEach(unit -> actuators.add(unit.actuator));
        actuators.addAll(controlActuators.values());
        for (Actuator actuator : actuators) {
            try {
                if (actuator != null && actuator.isAvailable()) {
                    actuator.neutralize();
                }
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not stop a ship control actuator during shutdown", err);
            }
        }
        Set<BearingActuator> bearings = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        calibrationBearings.forEach(unit -> bearings.add(unit.actuator));
        bearings.addAll(controlBearings.values());
        for (BearingActuator bearing : bearings) {
            try {
                if (bearing != null) {
                    bearing.restore();
                }
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not stop a ship control bearing during shutdown", err);
            }
        }
        for (WheelMountControlBridge wheel : List.copyOf(controlledScmWheels)) {
            try {
                wheel.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not release wheel controls during shutdown", err);
            }
        }
        controlledScmWheels.clear();
        controlledScmWheelTargets.clear();
        clearScmFaceActionControls();
        lastScmWheelRefreshTick = Long.MIN_VALUE;
        lastScmWheelTopologyFingerprint = "";
        lastScmWheelActionFingerprint = "";
        groundDriveCheckTick = Long.MIN_VALUE;
        groundDriveTopologyFingerprint = "";
    }

    // Discard the server shutdown
    private void discardForServerShutdown() {
        prepareForServerShutdown();
    }

    // Resume control after loading
    void resumeAfterLoad(boolean waitForScheduleControl) {
        shutdownPrepared = false;
        LIVE_RUNTIMES.add(this);
        if (mapId == null) {
            cancelPostLoadPoseHold();
            return;
        }
        resumePoseHoldAfterLoad = true;
        resumePoseHoldReleaseRequested = !waitForScheduleControl;
        resumePoseHoldTicks = RESUME_POSE_HOLD_TICKS;
        resumePoseHoldTimeoutTicks = RESUME_POSE_HOLD_TIMEOUT_TICKS;
        resumePoseHoldLoadAttempts = 0;
    }

    // Request the post load pose release
    void requestPostLoadPoseRelease() {
        resumePoseHoldReleaseRequested = true;
    }

    // Cancel the post load pose hold
    void cancelPostLoadPoseHold() {
        releaseFreezeHandles();
        resumePoseHoldAfterLoad = false;
        resumePoseHoldReleaseRequested = false;
        resumePoseHoldTicks = 0;
        resumePoseHoldTimeoutTicks = 0;
        resumePoseHoldLoadAttempts = 0;
    }

    // Check if this is restoring after load
    boolean isRestoringAfterLoad() {
        return resumePoseHoldAfterLoad
                || mapId != null && (map == null || phase == Phase.IDLE);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the ship control module
    public void tick() {
        Level level = controller.getLevel();
        if (shutdownPrepared || level == null || level.isClientSide) {
            return;
        }
        if (!isModuleAttached()) {
            if (isInitializing()) {
                fail("Control module detached during initialization", null);
            }
            cancelPostLoadPoseHold();
            clearThrusterProtection();
            releaseControlActuators();
            resetControlState();
            return;
        }
        retainNavigationRoot();
        tickCouplerReqs();

        // -----------------------------------------------------MAP RESTORE-----------------------------------------------------
        if (phase == Phase.IDLE && map == null && mapId != null && !mapLoadAttempted) {
            loadMap();
        }

        if (resumePoseHoldAfterLoad) {
            if (!tickResumePoseHold()) {
                return;
            }
        }

        if (resumeInitAfterLoad && phase == Phase.IDLE) {
            if (!loadResumedInitializationSubLevels()) {
                resumeInitTries++;
                status = "Restoring ship sub-levels before initialization";
                if (resumeInitTries >= 200) {
                    resumeInitAfterLoad = false;
                    phase = Phase.ERROR;
                    status = "Ship initialization paused: one or more sub-levels could not be restored";
                    releaseInitTracking();
                    controller.setChanged();
                }
                return;
            }
            if (initializeControlModule("ship_initialize", resumedInitializationFilters,
                    resumedInitializationShipName,
                    ScmControlModeRegistry.serialize(controller.getShipControlMode().id()))) {
                resumeInitAfterLoad = false;
                resumeInitTries = 0;
            } else {
                resumeInitTries++;
                if (phase != Phase.IDLE || resumeInitTries >= 200) {
                    resumeInitAfterLoad = false;
                    if (phase == Phase.IDLE) {
                        phase = Phase.ERROR;
                        status = "Ship initialization paused: control module is not ready";
                    }
                    releaseInitTracking();
                    controller.setChanged();
                }
            }
            return;
        }

        if (!pendingOrphanCleanupUnits.isEmpty()
                && level.getGameTime() >= nextOrphanCleanupTick) {
            releaseOrphanedMappedThrusters();
        }
        if (isInitializing() && rootSubLevel != null) {
            claimControlAuthority(assemblyTopology(rootSubLevel));
        }
        if (phase == Phase.READY && map != null) {
            ServerSubLevel currentRoot = containingServerSubLevel();
            if (currentRoot != null
                    && map.rootSubLevelId().equals(currentRoot.getUniqueId())) {
                refreshActiveAssemblyMap(currentRoot);
            }
        }
        if (phase == Phase.READY && map != null
                && intervalElapsed(
                        level.getGameTime(), lastDamageProtectionRefreshTick, 20L)) {
            refreshThrusterProtection();
            lastDamageProtectionRefreshTick = level.getGameTime();
        }

        // -----------------------------------------------------PHASE UPDATE-----------------------------------------------------
        try {
            switch (phase) {
                case FREEZING -> freezeParentSubLevel();
                case SCANNING -> scanPropulsionUnits();
                case CALIBRATING -> tickCalibration();
                case SAVING -> saveCalibrationMap();
                case READY -> tickControl();
                case IDLE, CONFIGURING, ERROR -> {
                }
            }
        } catch (Exception err) {
            fail("Initialization failed: " + usefulMessage(err), err);
        }
        if (phase == Phase.READY) {
            publishMappedCrnDisplays();
        }
        if (isInitializing()) {
            showInitProgress(false, false);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       INITIALIZATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the control module
    public boolean initializeControlModule() {
        return initializeControlModule(
                "ship_initialize", InitializationFilters.NONE, "", "airship");
    }

    /**
     * Rebuild the live SCM map after the player changes the persisted control
     * profile.  This deliberately uses the profile-driven initializer: it
     * reads the declared adapters and their current geometry/capacity, without
     * freezing the craft or running the legacy response-curve sweep.
     */
    public boolean rebuildConfiguredControlMap() {
        return initializeControlModule(
                "ship_initialize", InitializationFilters.NONE,
                controller.getShipName(),
                ScmControlModeRegistry.serialize(controller.getShipControlMode().id()));
    }

    // Discover the current controllable blocks for the SCM calibration UI without
    // freezing the ship or running any physical calibration pulse.
    public boolean scanConfiguration(
            String commandId, Map<String, Double> parameters,
            Map<String, String> textParameters
    ) {
        configurationScanOnly = true;
        boolean started = initializeControlModule(commandId,
                InitializationFilters.fromParameters(parameters),
                textParameters == null ? "" : textParameters.get("ship_name"),
                textParameters == null ? "airship" : textParameters.get("control_mode"));
        if (started) {
            phase = Phase.SCANNING;
            status = "Scanning controllable blocks for SCM configuration";
            progress = 0.08D;
            beginCommand(commandId, "ship_scan_configuration");
        } else {
            configurationScanOnly = false;
        }
        return started;
    }

    // Initialize the control module
    private boolean initializeControlModule(
            String commandId,
            InitializationFilters filters,
            String requestedShipName,
            String requestedControlMode
    ) {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide || !isModuleAttached() || isInitializing()) {
            return false;
        }
        Object containing = SimulatedHelper.getContainingSubLevel(controller);
        if (!(containing instanceof ServerSubLevel serverSubLevel)) {
            phase = Phase.ERROR;
            status = "Controller is not part of a Sable sub-level";
            progress = 0.0D;
            return false;
        }
        // ------------------------------------ASSEMBLY TOPOLOGY------------------------------------
        assemblyTopologyCache.invalidate();
        SableAssemblyTopologyApi.Topology initializationTopology =
                assemblyTopology(serverSubLevel);
        if (!initializationTopology.available()) {
            phase = Phase.ERROR;
            status = "Connected carriage topology is unavailable; try initialization again";
            progress = 0.0D;
            return false;
        }

        // -----------------------------------------------------RUNTIME RESET-----------------------------------------------------
        if (!configurationScanOnly) {
            configurationCandidates = List.of();
        }
        clearThrusterProtection();
        controller.setShipName(requestedShipName);
        controller.setShipControlMode(requestedControlMode);
        syncInitializationDisplays(false, 0, "");
        releaseControlActuators();
        releaseFreezeHandles();
        restoreCalActuators();
        rootSubLevel = serverSubLevel;
        initializationFilters = filters == null ? InitializationFilters.NONE : filters;
        initializationV2 = CTConfigs.SERVER.enableScmInitializationV2.get()
                || isControlMode(ScmBuiltinControlModes.CAR_ID);
        Map<UUID, ForeignScmMap> detectedForeignMaps =
                foreignScmMaps(initializationTopology, true);
        suppressedForeignTopologyFingerprint =
                initializationFilters.forceFullInitialization()
                        ? initializationTopology.fingerprint() : "";
        initForeignMaps = initializationFilters.forceFullInitialization()
                ? Map.of() : detectedForeignMaps;
        initForeignSubLevelIds = foreignOwnedSubLevelIds(
                initializationTopology, initForeignMaps);
        boolean aggregatesCarriages = initializationTopology.carriagePartitions().size() > 1;
        setAssemblyAggregator(aggregatesCarriages);
        if (aggregatesCarriages) {
            detectedForeignMaps.values().stream()
                    .map(ForeignScmMap::controller)
                    .filter(java.util.Objects::nonNull)
                    .filter(scm -> scm != controller)
                    .forEach(scm -> scm.setShipControlAssemblyAggregator(false));
        }
        // -----------------------------------------------------MAP REUSE-----------------------------------------------------
        prevInitMap = currentInitMap(serverSubLevel);
        mapId = prevInitMap != null ? prevInitMap.id()
                : mapId == null ? UUID.randomUUID() : mapId;
        ScmConfigurationProfile scmConfiguration = controller.getScmConfigurationProfile();
        boolean hasConfiguredBindings = !configurationScanOnly
                && scmConfiguration.groups().stream().anyMatch(group -> !group.units().isEmpty());
        // A calibration profile is bound to this controller and its live Sable
        // block identities, not to one disposable generated map UUID. Requiring
        // that UUID to survive a map reload/rebuild silently sent valid profiles
        // through the legacy freeze-and-probe initializer. Rebase the profile to
        // the map about to be produced and use its declared live adapters.
        if (hasConfiguredBindings && !Objects.equals(scmConfiguration.mapId(), mapId)) {
            scmConfiguration.replace(mapId, scmConfiguration.groups(),
                    scmConfiguration.actionGroups(), scmConfiguration.excludedUnits());
            controller.setScmConfigurationProfile(scmConfiguration);
        }
        // Full initialization rebuilds the physical map, but it must retain
        // player-authored action routing. The complete map lets the SCM detect
        // changed geometry; liveGeometryMap() still restricts each command to
        // its configured groups afterwards.
        // A profile is a routing/configuration declaration, not a replacement
        // for the calibrated physical control map. The profile-only adapter
        // shortcut bypassed the proven freeze -> scan -> calibration pipeline
        // used by the live implementation, leaving normal thrusters, pilots
        // and schedules with a map that could not produce real control. Keep
        // every normal initialization on that pipeline; profile groups are
        // applied later by liveGeometryMap() as an allocation mask.
        profileDrivenInitialization = false;
        ShipControlMap reusableLocalMap = initializationV2
                || initializationFilters.forceFullInitialization()
                ? null : reusableInitMap(
                serverSubLevel, prevInitMap);
        reconciliationBaseMap = initializationReuseMap(
                serverSubLevel, reusableLocalMap,
                java.util.Objects.requireNonNull(mapId), initializationTopology,
                initForeignMaps);
        retainNavigationRoot();
        map = null;
        clearDynamicConnectorRegistry();
        mapLoadAttempted = true;
        clearPendingOrphanCleanup();
        // ------------------------------------CALIBRATION RESET------------------------------------
        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        calibrationSubLevels.clear();
        calibrationUnitIndex = 0;
        calibrationSampleIndex = 0;
        scmWheelCalibrationTick = 0;
        scmWheelCalibrationComplete = false;
        sampleSettleTicks = 0;
        resetCalSamples();
        carWheelFirstCalibrationIndex = -1;
        carWheelCalibrationPending = false;
        carWheelCalibrationStarted = false;
        carWheelDriveActuator = null;
        resetBearingCal();
        resetControlState();
        completedCommands.clear();
        captureInitAssembly(serverSubLevel, initializationTopology);
        initFinalMsgSent = false;
        lastProgressDisplayTick = Long.MIN_VALUE;
        // ------------------------------------START INITIALIZATION------------------------------------
        phase = configurationScanOnly ? Phase.SCANNING : Phase.FREEZING;
        status = configurationScanOnly
                ? "Scanning controllable blocks for SCM configuration"
                : "Freezing connected sub-levels";
        progress = 0.02D;
        signalAssemblyMapChanged();
        beginCommand(commandId, configurationScanOnly ? "ship_scan_configuration" : "ship_initialize");
        showInitProgress(false, false);
        controller.setChanged();
        return true;
    }

    // Get the current init map
    private @Nullable ShipControlMap currentInitMap(ServerSubLevel root) {
        ShipControlMap candidate = map;
        if (candidate == null && mapId != null) {
            candidate = ShipControlMapStore.load(controller.getLevel(), mapId);
        }
        return candidate != null && root.getUniqueId().equals(candidate.rootSubLevelId())
                ? candidate : null;
    }

    // Get the reusable init map
    private @Nullable ShipControlMap reusableInitMap(
            ServerSubLevel root,
            @Nullable ShipControlMap candidate
    ) {
        if (candidate == null
                || !root.getUniqueId().equals(candidate.rootSubLevelId())
                || requiresCalibrationRefresh(candidate)) {
            return null;
        }
        return candidate;
    }

    // Get the initialization reuse map
    private @Nullable ShipControlMap initializationReuseMap(
            ServerSubLevel root,
            @Nullable ShipControlMap reusableLocalMap,
            UUID targetMapId,
            SableAssemblyTopologyApi.Topology topology,
            Map<UUID, ForeignScmMap> foreignMaps
    ) {
        if (!topology.available()) {
            return reusableLocalMap;
        }
        if (reusableLocalMap == null && foreignMaps.isEmpty()) {
            return null;
        }

        Level level = controller.getLevel();
        String dimension = level == null
                ? "" : level.dimension().location().toString();
        ShipControlMap primaryMap = reusableLocalMap != null
                ? reusableLocalMap
                : new ShipControlMap(
                        targetMapId, dimension, root.getUniqueId(),
                        controller.getBlockPos(), controller.getBlockPos().getCenter(),
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        System.currentTimeMillis());
        Set<UUID> primaryOwned = new LinkedHashSet<>();
        List<ScmMapCompositionApi.Fragment<AssemblyMapSource>> attached = new ArrayList<>();
        for (SableAssemblyTopologyApi.CarriagePartition partition
                : topology.carriagePartitions()) {
            if (partition.primary()) {
                primaryOwned.addAll(partition.bodyIds());
                continue;
            }
            ForeignScmMap foreign = foreignMaps.get(partition.rootSubLevelId());
            if (foreign != null) {
                attached.add(new ScmMapCompositionApi.Fragment<>(
                        foreign.map().id(), foreign.map().rootSubLevelId(),
                        partition.bodyIds(), new AssemblyMapSource(
                        foreign.map(), true, foreign.controller())));
            } else {
                ShipControlMap carriageMap = mainCarriageMap(
                        root, topology, partition, primaryMap);
                if (carriageMap != null) {
                    attached.add(new ScmMapCompositionApi.Fragment<>(
                            carriageMap.id(), carriageMap.rootSubLevelId(),
                            partition.bodyIds(),
                            new AssemblyMapSource(carriageMap, true, controller)));
                }
            }
        }
        primaryOwned.add(root.getUniqueId());
        ScmMapCompositionApi.Composition<AssemblyMapSource> composition =
                ScmMapCompositionApi.compose(
                        new ScmMapCompositionApi.Fragment<>(
                                primaryMap.id(), root.getUniqueId(), primaryOwned,
                                new AssemblyMapSource(primaryMap, false, controller)),
                        attached, topology.loadedBodyIds());
        ShipControlMap composed = composeAssemblyMap(root, primaryMap, composition);
        if (targetMapId.equals(composed.id())) {
            return composed;
        }
        return new ShipControlMap(
                targetMapId, composed.dimension(), composed.rootSubLevelId(),
                composed.controllerPosition(), composed.centerOfMass(),
                composed.units(), composed.bearings(), composed.dockingConnectors(),
                composed.crnDisplays(), composed.accDisplays(), composed.updatedAt());
    }

    // Run the ship control module
    public boolean execute(String nodeType, Map<String, Double> parameters) {
        return execute(nodeType, nodeType, parameters);
    }

    // Run the ship control module
    public boolean execute(String commandId, String nodeType, Map<String, Double> parameters) {
        return execute(commandId, nodeType, parameters, Map.of());
    }

    // Run the ship control module
    public boolean execute(
            String commandId,
            String nodeType,
            Map<String, Double> parameters,
            Map<String, String> textParameters
    ) {
        // ------------------------------------COMMAND CHECKS------------------------------------
        if (shutdownPrepared) {
            return false;
        }
        if ("ship_initialize".equals(nodeType)) {
            return initializeControlModule(
                    commandId, InitializationFilters.fromParameters(parameters),
                    textParameters == null ? "" : textParameters.get("ship_name"),
                    textParameters == null ? "airship" : textParameters.get("control_mode"));
        }
        if ("ship_scan_configuration".equals(nodeType)) {
            return scanConfiguration(commandId, parameters, textParameters);
        }
        if ("ship_stop_initialization".equals(nodeType)) {
            boolean stopped = stopInitialization();
            if (stopped) {
                completedCommands.add(commandKey(commandId, nodeType));
            }
            return stopped;
        }
        if ("ship_couple_carriage".equals(nodeType)
                || "ship_decouple_carriage".equals(nodeType)) {
            Map<String, Double> values = parameters == null ? Map.of() : parameters;
            int selector = Mth.floor(finite(values.getOrDefault("endpoint", -1.0D)));
            return startCouplerReq(
                    commandId, nodeType,
                    "ship_couple_carriage".equals(nodeType), selector);
        }
        if (!isModuleAttached() || phase != Phase.READY || map == null) {
            return false;
        }

        Map<String, Double> values = parameters == null ? Map.of() : parameters;
        Map<String, String> textValues =
                textParameters == null ? Map.of() : textParameters;
        String normalizedId = normalizeCommandId(commandId, nodeType);
        ActiveShipCommand command;
        // -----------------------------------------------------COMMAND BUILD-----------------------------------------------------
        switch (nodeType) {
            case "ship_yaw", "ship_yaw_right", "ship_yaw_left",
                 "ship_pan", "ship_pitch", "ship_pitch_up", "ship_pitch_down",
                 "ship_tilt", "ship_roll", "ship_roll_right", "ship_roll_left",
                 "ship_accelerate", "ship_forward", "ship_reverse", "ship_strafe",
                 "ship_backward", "ship_strafe_left", "ship_strafe_right",
                 "ship_ascend", "ship_descend" ->
                    command = ActiveShipCommand.amount(normalizedId, nodeType, amount(values, "amount"));
            case "ship_stabilize" -> {
                Telemetry telemetry = telemetry();
                if (!telemetry.available()) {
                    return false;
                }
                command = ActiveShipCommand.attitude(
                        normalizedId, nodeType, strength(values), telemetry.eulerDegrees());
            }
            case "ship_decelerate", "ship_brake" ->
                    command = ActiveShipCommand.strength(normalizedId, nodeType, strength(values));
            case "ship_hover" -> {
                Telemetry telemetry = telemetry();
                if (!telemetry.available()) {
                    return false;
                }
                command = ActiveShipCommand.altitude(
                        normalizedId, nodeType, telemetry.position().y, 0.0D, strength(values));
            }
            case "ship_climb" -> command = ActiveShipCommand.altitude(
                    normalizedId, nodeType,
                    finite(values.getOrDefault("y", 0.0D)),
                    commandSpeed(values, 0.6D),
                    1.0D);
            case "ship_face" -> command = ActiveShipCommand.target(
                    normalizedId, nodeType, coordinates(values),
                    commandSpeed(values, 0.6D),
                    POSITION_TOLERANCE, false);
            case "ship_dock", "ship_navigate" -> {
                boolean lockRotation =
                        values.getOrDefault("lock_rotation", 0.0D) > 0.5D;
                Telemetry telemetry = telemetry();
                if (lockRotation && !telemetry.available()) {
                    return false;
                }
                boolean navigate = "ship_navigate".equals(nodeType);
                String serializedTargetPoint = textValues.get("target_point");
                command = ActiveShipCommand.target(
                        normalizedId, nodeType, coordinates(values),
                        commandSpeed(values, navigate ? 0.6D : 0.35D),
                        commandDriveThrottle(values),
                        positive(values.getOrDefault("tolerance", 0.75D), 0.75D),
                        collisionAvoidanceRequested(values),
                        lockRotation,
                        lockRotation ? telemetry.eulerDegrees() : Vec3.ZERO,
                        ShipTargetPoint.fromSerialized(
                                serializedTargetPoint),
                        ShipTargetPoint.connectorIndexFromSerialized(serializedTargetPoint),
                        new Vec3(
                                finite(values.getOrDefault("target_direction_x", 0.0D)),
                                finite(values.getOrDefault("target_direction_y", 0.0D)),
                                finite(values.getOrDefault("target_direction_z", 0.0D))),
                        new Vec3(
                                finite(values.getOrDefault("target_up_x", 0.0D)),
                                finite(values.getOrDefault("target_up_y", 0.0D)),
                                finite(values.getOrDefault("target_up_z", 0.0D))));
            }
            case "ship_follow" -> command = ActiveShipCommand.target(
                    normalizedId, nodeType, coordinates(values),
                    commandSpeed(values, 0.6D),
                    commandDriveThrottle(values),
                    Math.max(0.0D, finite(values.getOrDefault("follow_distance", 8.0D))),
                    collisionAvoidanceRequested(values));
            case "ship_stop" -> {
                resetControlState();
                releaseControlActuators();
                command = ActiveShipCommand.idle(normalizedId, nodeType);
            }
            default -> {
                return false;
            }
        }
        registerCommand(command);
        requestPostLoadPoseRelease();
        if ("ship_stop".equals(nodeType)) {
            completeCommand(command);
        }
        return true;
    }

    // Get the graph value
    public AdvancedGraphDocument.Value graphValue(String port) {
        return graphValue(port, COLLISION_SCAN_RANGE,
                AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE);
    }

    // Get the graph value
    public AdvancedGraphDocument.Value graphValue(
            String port, double collisionDetectionDistance
    ) {
        return graphValue(port, collisionDetectionDistance,
                AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE);
    }

    // Get the graph value
    public AdvancedGraphDocument.Value graphValue(
            String port,
            double collisionDetectionDistance,
            double collisionPollRate
    ) {
        // ------------------------------------TELEMETRY SOURCES------------------------------------
        double scanRange = AdvancedGraphCatalog.normalizeCollisionDetectionDistance(
                collisionDetectionDistance);
        Telemetry telemetry = switch (port) {
            case "x", "y", "z", "velocity_x", "velocity_y", "velocity_z", "speed",
                 "angular_velocity_x", "angular_velocity_y", "angular_velocity_z",
                 "yaw", "pitch", "roll", "nearest_collision_distance",
                 "collision_distance_forward", "collision_distance_backward",
                 "collision_distance_left", "collision_distance_right",
                 "collision_distance_up", "collision_distance_down",
                 "navigation_target_distance" -> telemetry();
            default -> Telemetry.EMPTY;
        };
        // ------------------------------------COLLISION TELEMETRY------------------------------------
        CollisionTelemetry collisions = switch (port) {
            case "nearest_collision_distance", "collision_distance_forward",
                 "collision_distance_backward", "collision_distance_left",
                 "collision_distance_right", "collision_distance_up",
                 "collision_distance_down" -> collisionTelemetry(
                    telemetry, scanRange, collisionPollRate);
            default -> CollisionTelemetry.EMPTY;
        };
        // ------------------------------------ASSEMBLY STATUS------------------------------------
        ShipControlMap currentMap = effectiveAssemblyMap();
        SimulationMetrics metrics = switch (port) {
            case "mass", "weight", "facing", "center_of_mass_x", "center_of_mass_y", "center_of_mass_z",
                 "center_of_lift_x", "center_of_lift_y", "center_of_lift_z" -> simulationMetrics(currentMap);
            default -> SimulationMetrics.EMPTY;
        };
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        ShipCouplerService.Status couplerStatus = switch (port) {
            case "coupler_count", "coupled_count", "carriage_count",
                 "any_coupled", "all_coupled", "coupler_status" ->
                    ShipCouplerService.status(controller);
            default -> null;
        };
        // -----------------------------------------------------GRAPH OUTPUTS-----------------------------------------------------
        return switch (port) {
            case "attached" -> AdvancedGraphDocument.Value.bool(isModuleAttached());
            case "initialized", "ready" -> AdvancedGraphDocument.Value.bool(phase == Phase.READY && map != null);
            case "initializing" -> AdvancedGraphDocument.Value.bool(isInitializing());
            case "progress" -> AdvancedGraphDocument.Value.number(progress);
            case "unit_count" -> AdvancedGraphDocument.Value.number(currentMap == null
                    ? calibrationUnits.size() : currentMap.units().size());
            case "bearing_count" -> AdvancedGraphDocument.Value.number(
                    currentMap == null ? calibrationBearings.stream()
                            .filter(bearing -> !bearing.actuator.directlyControlsPropulsion()).count()
                            : mappedBearingCount(currentMap));
            case "vector_thruster_count" -> AdvancedGraphDocument.Value.number(
                    currentMap == null ? calibrationBearings.stream()
                            .filter(bearing -> bearing.actuator.directlyControlsPropulsion()).count()
                            : mappedVectorThrusterCount(currentMap));
            case "docking_connector_count" -> AdvancedGraphDocument.Value.number(
                    currentMap == null ? calibrationDockingConnectors.size()
                            : mappedDockingConnectors().size());
            case "controllable_count" -> AdvancedGraphDocument.Value.number(currentMap == null
                    ? calibrationUnits.stream().filter(unit -> unit.actuator.controllable()).count()
                    : currentMap.controllableUnitCount());
            case "status" -> AdvancedGraphDocument.Value.string(status);
            case "map_id" -> AdvancedGraphDocument.Value.string(mapId == null ? "" : mapId.toString());
            case "configured" -> AdvancedGraphDocument.Value.bool(
                    configuration.isConfiguredFor(currentMap));
            case "scanning" -> AdvancedGraphDocument.Value.bool(isConfigurationScanning());
            case "candidate_count" -> AdvancedGraphDocument.Value.number(configurationCandidates.size());
            case "group_count" -> AdvancedGraphDocument.Value.number(configuration.groups().size());
            case "action_binding_count" -> AdvancedGraphDocument.Value.number(
                    configuration.actionGroups().size());
            case "excluded_unit_count" -> AdvancedGraphDocument.Value.number(
                    configuration.excludedUnits().size());
            case "action_groups" -> configurationActionGroups(configuration);
            case "coupler_count" -> AdvancedGraphDocument.Value.number(
                    couplerStatus == null ? 0 : couplerStatus.couplerCount());
            case "coupled_count" -> AdvancedGraphDocument.Value.number(
                    couplerStatus == null ? 0 : couplerStatus.coupledCount());
            case "carriage_count" -> AdvancedGraphDocument.Value.number(
                    Math.max(activeCarriageCount,
                            couplerStatus == null ? 0 : couplerStatus.carriageCount()));
            case "any_coupled" -> AdvancedGraphDocument.Value.bool(
                    couplerStatus != null && couplerStatus.anyCoupled());
            case "all_coupled" -> AdvancedGraphDocument.Value.bool(
                    couplerStatus != null && couplerStatus.allCoupled());
            case "coupler_status" -> AdvancedGraphDocument.Value.string(
                    couplerStatus == null ? "none" : couplerStatus.description());
            case "inertia_tensor" -> inertiaTensorValue();
            case "mass" -> AdvancedGraphDocument.Value.number(metrics.mass());
            case "weight" -> AdvancedGraphDocument.Value.number(metrics.mass() * 9.80665D);
            case "facing" -> AdvancedGraphDocument.Value.string(metrics.facing());
            case "center_of_mass_x" -> AdvancedGraphDocument.Value.number(metrics.centerOfMass().x);
            case "center_of_mass_y" -> AdvancedGraphDocument.Value.number(metrics.centerOfMass().y);
            case "center_of_mass_z" -> AdvancedGraphDocument.Value.number(metrics.centerOfMass().z);
            case "center_of_lift_x" -> AdvancedGraphDocument.Value.number(metrics.centerOfLift().x);
            case "center_of_lift_y" -> AdvancedGraphDocument.Value.number(metrics.centerOfLift().y);
            case "center_of_lift_z" -> AdvancedGraphDocument.Value.number(metrics.centerOfLift().z);
            case "x" -> AdvancedGraphDocument.Value.number(telemetry.position().x);
            case "y" -> AdvancedGraphDocument.Value.number(telemetry.position().y);
            case "z" -> AdvancedGraphDocument.Value.number(telemetry.position().z);
            case "velocity_x" -> AdvancedGraphDocument.Value.number(telemetry.velocity().x);
            case "velocity_y" -> AdvancedGraphDocument.Value.number(telemetry.velocity().y);
            case "velocity_z" -> AdvancedGraphDocument.Value.number(telemetry.velocity().z);
            case "speed" -> AdvancedGraphDocument.Value.number(telemetry.velocity().length());
            case "angular_velocity_x" -> AdvancedGraphDocument.Value.number(telemetry.angularVelocity().x);
            case "angular_velocity_y" -> AdvancedGraphDocument.Value.number(telemetry.angularVelocity().y);
            case "angular_velocity_z" -> AdvancedGraphDocument.Value.number(telemetry.angularVelocity().z);
            case "yaw" -> AdvancedGraphDocument.Value.number(telemetry.eulerDegrees().y);
            case "pitch" -> AdvancedGraphDocument.Value.number(telemetry.eulerDegrees().x);
            case "roll" -> AdvancedGraphDocument.Value.number(telemetry.eulerDegrees().z);
            case "nearest_collision_distance" ->
                    AdvancedGraphDocument.Value.number(collisions.nearest());
            case "collision_distance_forward" ->
                    AdvancedGraphDocument.Value.number(collisions.forward());
            case "collision_distance_backward" ->
                    AdvancedGraphDocument.Value.number(collisions.backward());
            case "collision_distance_left" ->
                    AdvancedGraphDocument.Value.number(collisions.left());
            case "collision_distance_right" ->
                    AdvancedGraphDocument.Value.number(collisions.right());
            case "collision_distance_up" ->
                    AdvancedGraphDocument.Value.number(collisions.up());
            case "collision_distance_down" ->
                    AdvancedGraphDocument.Value.number(collisions.down());
            case "collision_scan_range" -> AdvancedGraphDocument.Value.number(scanRange);
            case "navigation_target_distance" ->
                    AdvancedGraphDocument.Value.number(navigationTargetDistance(telemetry));
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Convert the profile action assignment table into a graph map without exposing
    // mutable NBT or server-side profile objects to a graph.
    private static AdvancedGraphDocument.Value configurationActionGroups(
            ScmConfigurationProfile configuration
    ) {
        CompoundTag values = new CompoundTag();
        configuration.actionGroups().forEach((action, group) -> {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("Type", "string");
            CompoundTag payload = new CompoundTag();
            payload.putString("Value", group);
            encoded.put("Payload", payload);
            values.put(action, encoded);
        });
        return AdvancedGraphDocument.Value.map(values);
    }

    // Get the inertia tensor value
    private AdvancedGraphDocument.Value inertiaTensorValue() {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return AdvancedGraphDocument.Value.map(new CompoundTag());
        }
        SableAssemblyDynamicsApi.Snapshot dynamics = SableAssemblyDynamicsApi.sample(
                assemblyTopology(root));
        if (!dynamics.massAvailable()) {
            return AdvancedGraphDocument.Value.map(new CompoundTag());
        }
        SableAssemblyDynamicsApi.Tensor matrix = dynamics.inertia();
        CompoundTag values = new CompoundTag();
        putMatrixValue(values, "m00", matrix.m00());
        putMatrixValue(values, "m01", matrix.m01());
        putMatrixValue(values, "m02", matrix.m02());
        putMatrixValue(values, "m10", matrix.m10());
        putMatrixValue(values, "m11", matrix.m11());
        putMatrixValue(values, "m12", matrix.m12());
        putMatrixValue(values, "m20", matrix.m20());
        putMatrixValue(values, "m21", matrix.m21());
        putMatrixValue(values, "m22", matrix.m22());
        return AdvancedGraphDocument.Value.map(values);
    }

    // Get the assembly metrics shared by the SCM screen and display nodes.
    private SimulationMetrics simulationMetrics(@Nullable ShipControlMap currentMap) {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) return SimulationMetrics.EMPTY;
        Level level = controller.getLevel();
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        UUID rootId = root.getUniqueId();
        if (simulationMetricsTick == gameTime && Objects.equals(simulationMetricsRootId, rootId)) {
            return cachedSimulationMetrics;
        }
        SableAssemblyDynamicsApi.Snapshot dynamics = SableAssemblyDynamicsApi.sample(assemblyTopology(root));
        Vec3 centerOfMass = dynamics.massAvailable() ? dynamics.centerOfMass()
                : currentMap == null ? Vec3.ZERO : currentMap.centerOfMass();
        Vec3 centerOfLift = centerOfLift(currentMap, centerOfMass);
        Vec3 forward = normalize(rootDirectionToWorld(controllerForwardRoot()), Vec3.ZERO);
        Direction facing = Direction.getNearest((float) forward.x, (float) forward.y, (float) forward.z);
        cachedSimulationMetrics = new SimulationMetrics(dynamics.mass(), finite(centerOfMass),
                finite(centerOfLift), facing == null ? "unknown" : facing.getSerializedName());
        simulationMetricsTick = gameTime;
        simulationMetricsRootId = rootId;
        return cachedSimulationMetrics;
    }

    // Resolve the force-weighted centre of upward-capable propulsion.
    private Vec3 centerOfLift(@Nullable ShipControlMap currentMap, Vec3 fallback) {
        if (currentMap == null || currentMap.units().isEmpty()) return finite(fallback);
        Vec3 up = normalize(controllerUpRoot(), new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 weighted = Vec3.ZERO;
        double totalLift = 0.0D;
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            if (unit == null || !unit.controllable()) continue;
            double lift = Math.max(0.0D, unit.forceDirection().dot(up)) * unit.maxThrust();
            if (!Double.isFinite(lift) || lift <= 1.0E-6D) continue;
            weighted = weighted.add(unit.rootPosition().scale(lift));
            totalLift += lift;
        }
        return totalLift <= 1.0E-6D ? finite(fallback) : finite(weighted.scale(1.0D / totalLift));
    }

    // Put the matrix value
    private static void putMatrixValue(CompoundTag values, String key, double val) {
        CompoundTag encoded = new CompoundTag();
        encoded.putString("Type", "number");
        CompoundTag payload = new CompoundTag();
        payload.putDouble("Value", Double.isFinite(val) ? val : 0.0D);
        encoded.put("Payload", payload);
        values.put(key, encoded);
    }

    // Write the persistent coupler state
    void writePersistentCouplerState(CompoundTag parent) {
        parent.putBoolean("ShipControlAssemblyAggregator", assemblyAggregator);
        if (suppressedForeignTopologyFingerprint.isBlank()) {
            parent.remove("ShipControlForeignMapSuppression");
        } else {
            parent.putString("ShipControlForeignMapSuppression",
                    suppressedForeignTopologyFingerprint);
        }
        if (couplerControlRequests.isEmpty()) {
            parent.remove("ShipControlCouplerRequests");
        } else {
            parent.put("ShipControlCouplerRequests", couplerControlRequestTag());
        }
    }

    // Read the persistent coupler state
    void readPersistentCouplerState(CompoundTag parent) {
        assemblyAggregator = parent.getBoolean("ShipControlAssemblyAggregator");
        suppressedForeignTopologyFingerprint = parent.getString(
                "ShipControlForeignMapSuppression");
        readCouplerReqs(parent.getList(
                "ShipControlCouplerRequests", Tag.TAG_COMPOUND));
    }

    // Get the coupler control request tag
    private ListTag couplerControlRequestTag() {
        ListTag encodedRequests = new ListTag();
        couplerControlRequests.values().forEach(req -> {
            CompoundTag encoded = new CompoundTag();
            encoded.putString("CommandId", req.commandId());
            encoded.putString("NodeType", req.nodeType());
            encoded.putBoolean("Attach", req.attach());
            encoded.putInt("Selector", req.selector());
            if (!req.endpoints().isEmpty()) {
                encoded.put("Endpoints",
                        ShipCouplerService.writeEndpointKeys(req.endpoints()));
            }
            encodedRequests.add(encoded);
        });
        return encodedRequests;
    }

    // Read the coupler reqs
    private void readCouplerReqs(ListTag encodedRequests) {
        couplerControlRequests.clear();
        for (int idx = 0; idx < encodedRequests.size(); idx++) {
            CompoundTag encoded = encodedRequests.getCompound(idx);
            String nodeType = encoded.getString("NodeType");
            if (!isCouplerControlNode(nodeType)) {
                continue;
            }
            CouplerControlRequest req = new CouplerControlRequest(
                    encoded.getString("CommandId"), nodeType,
                    encoded.getBoolean("Attach"),
                    encoded.contains("Selector") ? encoded.getInt("Selector") : -1,
                    ShipCouplerService.readEndpointKeys(
                            encoded.getList("Endpoints", Tag.TAG_COMPOUND)));
            couplerControlRequests.put(
                    commandKey(req.commandId(), req.nodeType()), req);
        }
    }

    // Check if the command is pending
    public boolean isCommandPending(String commandId, String nodeType) {
        String key = commandKey(commandId, nodeType);
        if (isCouplerControlNode(nodeType)) {
            return isModuleAttached()
                    && couplerControlRequests.containsKey(key)
                    && !completedCommands.contains(key);
        }
        boolean running = "ship_initialize".equals(nodeType)
                || "ship_scan_configuration".equals(nodeType)
                ? isInitializing()
                : phase == Phase.READY && map != null;
        return isModuleAttached()
                && running
                && activeCommands.containsKey(key)
                && !completedCommands.contains(key);
    }

    // Check if the command is complete
    public boolean isCommandComplete(String commandId, String nodeType) {
        return completedCommands.contains(commandKey(commandId, nodeType));
    }

    // Start the coupler req
    private boolean startCouplerReq(
            String commandId,
            String nodeType,
            boolean attach,
            int selector
    ) {
        if (!isModuleAttached() || selector < -1) {
            return false;
        }
        String key = commandKey(commandId, nodeType);
        CouplerControlRequest req = couplerControlRequests.get(key);
        if (req == null || req.attach() != attach
                || req.selector() != selector) {
            List<ShipCouplerBlockEntity.EndpointKey> endpoints =
                    ShipCouplerService.selectEndpoints(controller, selector);
            if (endpoints.isEmpty()) {
                if (couplerControlRequests.remove(key) != null) {
                    controller.setChanged();
                }
                return false;
            }
            req = new CouplerControlRequest(
                    normalizeCommandId(commandId, nodeType), nodeType,
                    attach, selector, endpoints);
        }
        if (req.endpoints().isEmpty()) {
            return false;
        }
        completedCommands.remove(key);
        return applyCouplerResult(
                key, req, ShipCouplerService.command(
                        controller, couplerServiceRequestKey(key),
                        attach, req.endpoints()));
    }

    // Update the coupler reqs
    private void tickCouplerReqs() {
        for (Map.Entry<String, CouplerControlRequest> entry
                 : List.copyOf(couplerControlRequests.entrySet())) {
            CouplerControlRequest req = entry.getValue();
            if (req.endpoints().isEmpty()) {
                List<ShipCouplerBlockEntity.EndpointKey> endpoints =
                        ShipCouplerService.selectEndpoints(
                                controller, req.selector());
                if (endpoints.isEmpty()) {
                    if (couplerControlRequests.remove(entry.getKey()) != null) {
                        controller.setChanged();
                    }
                    continue;
                }
                req = req.withEndpoints(endpoints);
                couplerControlRequests.put(entry.getKey(), req);
                controller.setChanged();
            }
            applyCouplerResult(
                    entry.getKey(), req,
                    ShipCouplerService.command(
                            controller, couplerServiceRequestKey(entry.getKey()),
                            req.attach(), req.endpoints()));
        }
    }

    // Get the coupler service request key
    private static String couplerServiceRequestKey(String commandKey) {
        return "scm_graph:" + commandKey;
    }

    // Apply the coupler result
    private boolean applyCouplerResult(
            String key,
            CouplerControlRequest req,
            ShipCouplerService.Result res
    ) {
        return switch (res) {
            case COMPLETE -> {
                boolean changed = couplerControlRequests.remove(key) != null;
                changed |= completedCommands.add(key);
                if (changed) {
                    controller.setChanged();
                }
                yield true;
            }
            case PENDING -> {
                CouplerControlRequest prev = couplerControlRequests.put(key, req);
                if (!req.equals(prev)) {
                    controller.setChanged();
                }
                yield true;
            }
            case NO_TARGET, FAILED -> {
                if (couplerControlRequests.remove(key) != null) {
                    controller.setChanged();
                }
                yield false;
            }
        };
    }

    // Check if this is a coupler control node
    private static boolean isCouplerControlNode(String nodeType) {
        return "ship_couple_carriage".equals(nodeType)
                || "ship_decouple_carriage".equals(nodeType);
    }

    // Get the command graph value
    public AdvancedGraphDocument.Value commandGraphValue(String commandId, String nodeType, String port) {
        return switch (port) {
            case "success" -> AdvancedGraphDocument.Value.bool(isCommandComplete(commandId, nodeType));
            case "progress" -> "ship_initialize".equals(nodeType)
                    || "ship_scan_configuration".equals(nodeType)
                    ? AdvancedGraphDocument.Value.string(initializationStageText())
                    : AdvancedGraphDocument.Value.number(0.0D);
            case "progress_percent" -> AdvancedGraphDocument.Value.number(progressPercent(progress));
            case "current_test" -> "ship_initialize".equals(nodeType)
                    || "ship_scan_configuration".equals(nodeType)
                    ? AdvancedGraphDocument.Value.string(initCurrentTest())
                    : AdvancedGraphDocument.Value.string("");
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Get the initialization stage text
    private String initializationStageText() {
        int stage = switch (phase) {
            case FREEZING -> 1;
            case SCANNING -> 2;
            case CALIBRATING -> 3;
            case SAVING -> 4;
            case READY -> 5;
            case CONFIGURING -> 2;
            case ERROR -> Math.max(1, Math.min(INITIALIZATION_STAGES,
                    (int) Math.ceil(progress * INITIALIZATION_STAGES)));
            case IDLE -> 0;
        };
        return formatInitializationStage(stage, INITIALIZATION_STAGES);
    }

    // Initialize the current test
    private String initCurrentTest() {
        return switch (phase) {
            case IDLE -> "Not running";
            case CONFIGURING -> "Awaiting SCM group configuration and calibration";
            case FREEZING -> "Freezing connected sub-levels";
            case SCANNING -> "Scanning propulsion providers, bearings, and aerodynamic surfaces";
            case CALIBRATING -> currentCalibrationTest();
            case SAVING -> "Saving control map";
            case READY -> "Initialization complete";
            case ERROR -> status;
        };
    }

    // Get the current calibration test
    private String currentCalibrationTest() {
        if (calibrationUnitIndex >= calibrationUnits.size() && !calibrationBearings.isEmpty()) {
            return bearingCalTest();
        }
        if (calibrationUnits.isEmpty() || calibrationUnitIndex < 0
                || calibrationUnitIndex >= calibrationUnits.size()) {
            return "Preparing propulsion tests";
        }
        CalibrationUnit unit = calibrationUnits.get(calibrationUnitIndex);
        if (unit.calibrationPoints.isEmpty() || calibrationSampleIndex < 0
                || calibrationSampleIndex >= unit.calibrationPoints.size()) {
            return "Preparing thruster (" + (calibrationUnitIndex + 1) + " of "
                    + calibrationUnits.size() + ")";
        }
        CalibrationPoint point = unit.calibrationPoints.get(calibrationSampleIndex);
        return "Thruster (" + (calibrationUnitIndex + 1) + " of " + calibrationUnits.size()
                + ") throttle " + compactTestValue(point.throttle())
                + " min thrust " + compactTestValue(point.minControl() * 100.0D)
                + " max thrust " + compactTestValue(point.maxControl() * 100.0D);
    }

    // Get the bearing cal test
    private String bearingCalTest() {
        if (bearingCalibrationIndex < 0 || bearingCalibrationIndex >= calibrationBearings.size()) {
            return "Preparing bearing tests";
        }
        BearingCalibrationUnit bearing = calibrationBearings.get(bearingCalibrationIndex);
        int poseIdx = Mth.clamp(
                bearingPoseIndex, 0, Math.max(0, bearing.poses.size() - 1));
        ShipBearingPlanner.Pose pose = bearing.poses.get(poseIdx);
        BearingPoseSample sample = bearing.poseSamples.get(poseIdx);
        if (!bearing.aerodynamicSurfaces.isEmpty() && !sample.aerodynamicsRecorded) {
            return bearing.actuator.displayName() + " (" + (bearingCalibrationIndex + 1)
                    + " of " + calibrationBearings.size() + ") angle x "
                    + compactTestValue(pose.angleX()) + " " + bearing.actuator.secondAxisLabel()
                    + " " + compactTestValue(pose.angleZ()) + " wind sweep "
                    + ShipBearingPlanner.WIND_SWEEP_TEST_COUNT + " flows over "
                    + bearing.aerodynamicSurfaces.size() + " sail surface"
                    + (bearing.aerodynamicSurfaces.size() == 1 ? "" : "s");
        }
        if (bearing.memberUnitIndices.isEmpty()) {
            return bearing.actuator.displayName() + " (" + (bearingCalibrationIndex + 1)
                    + " of " + calibrationBearings.size() + ") angle x "
                    + compactTestValue(pose.angleX()) + " " + bearing.actuator.secondAxisLabel()
                    + " " + compactTestValue(pose.angleZ()) + " aerodynamic map complete";
        }
        int displayMember = bearing.memberUnitIndices.stream()
                .filter(idx -> bearingThrottleIndex < calibrationUnits.get(idx).calibrationPoints.size())
                .findFirst().orElse(bearing.memberUnitIndices.getFirst());
        CalibrationPoint point = bearingCalibrationPoint(
                bearing, displayMember, bearingThrottleIndex);
        return bearing.actuator.displayName() + " (" + (bearingCalibrationIndex + 1)
                + " of " + calibrationBearings.size() + ") angle x "
                + compactTestValue(pose.angleX()) + " " + bearing.actuator.secondAxisLabel()
                + " " + compactTestValue(pose.angleZ())
                + " thruster " + (bearing.memberUnitIndices.indexOf(displayMember) + 1)
                + " of " + bearing.memberUnitIndices.size()
                + " throttle " + compactTestValue(point.throttle())
                + " min thrust " + compactTestValue(point.minControl() * 100.0D)
                + " max thrust " + compactTestValue(point.maxControl() * 100.0D)
                + " test " + (bearingThrottleIndex + 1)
                + " of " + bearingCalPointCount(bearing);
    }

    // Get the compact test value
    private static String compactTestValue(double val) {
        String formatted = String.format(Locale.ROOT, "%.3f", finite(val));
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, Math.max(1, end));
    }

    // Format the initialization stage
    static String formatInitializationStage(int stage, int total) {
        int safeTotal = Math.max(1, total);
        int safeStage = Mth.clamp(stage, 0, safeTotal);
        return "Stage (" + safeStage + ") of (" + safeTotal + ")";
    }

    // Get the progress percent
    static int progressPercent(double val) {
        return Mth.clamp((int) Math.round(finite(val) * 100.0D), 0, 100);
    }

    // Get the climb vertical correction
    static double climbVerticalCorrection(
            double targetY, double currentY, double verticalVelocity,
            double maximumSpeed, double strength
    ) {
        double error = finite(targetY) - finite(currentY);
        double speed = Math.max(0.0D, finite(maximumSpeed));
        double targetVerticalSpeed = Mth.clamp(error * 0.2D, -speed, speed);
        return Mth.clamp(error * 0.12D
                        + (targetVerticalSpeed - finite(verticalVelocity)) * 0.3D,
                -1.0D, 1.0D) * Mth.clamp(finite(strength), 0.0D, 1.0D);
    }

    // Check if this is module attached
    public boolean isModuleAttached() {
        if (CTBlocks.SHIP_CONTROL_MODULE == null) {
            return false;
        }
        var module = CTBlocks.SHIP_CONTROL_MODULE.get();
        if (controller.getEmbeddedBlockState() != null
                && controller.getEmbeddedBlockState().is(module)) {
            return true;
        }
        Level level = controller.getLevel();
        if (level == null) {
            return false;
        }
        BlockState support = level.getBlockState(controller.getBlockPos().below());
        return support.is(module) || CTBlocks.ACC_DISPLAY_SLAB != null
                && support.is(CTBlocks.ACC_DISPLAY_SLAB.get());
    }

    // Check if this is initializing
    public boolean isInitializing() {
        return phase == Phase.FREEZING || phase == Phase.SCANNING
                || phase == Phase.CALIBRATING || phase == Phase.SAVING;
    }

    // Return control-capable suggestions from the most recent optional
    // discovery pass. These are defaults only: the calibration modal may bind
    // any loaded block in the connected sub-level chain.
    public List<ScmConfigurationProfile.UnitReference> configurationCandidates() {
        return configurationCandidates;
    }

    // Allocate a stable profile/map identity without starting a discovery or
    // calibration pass. Configuration is a declaration, not a scan.
    public UUID ensureConfigurationMapId() {
        if (mapId == null) {
            mapId = UUID.randomUUID();
            controller.setChanged();
        }
        return mapId;
    }

    // Validate that a player-selected target is a real, loaded non-air block
    // on this SCM's current connected body chain. The actual control adapter is
    // resolved later, when the chosen SCM function is initialized.
    public boolean isConfigurationTarget(ScmConfigurationProfile.UnitReference reference) {
        if (reference == null || !reference.isValid() || controller.getLevel() == null) {
            return false;
        }
        if (!configurationViewSubLevelIds().contains(reference.subLevelId())) {
            return false;
        }
        SubLevel subLevel = SableLevelApi.subLevel(controller.getLevel(), reference.subLevelId());
        return subLevel != null && !subLevel.isRemoved()
                && !subLevel.getLevel().getBlockState(reference.blockPosition()).isAir();
    }

    // Return the live root which owns the SCM configuration view. This is kept
    // separate from the menu attachment: an ACC may sit on a child body while
    // the player needs the complete parent sub-level chain in the diagram.
    public @Nullable UUID configurationRootSubLevelId() {
        if (rootSubLevel != null && !rootSubLevel.isRemoved()) {
            return rootSubLevel.getUniqueId();
        }
        ServerSubLevel root = containingServerSubLevel();
        return root == null || root.isRemoved() ? null : root.getUniqueId();
    }

    /**
     * A bounded, detached craft scene used by the SCM editor when the player
     * can open the controller but is not client-tracking the assembled body.
     * Positions are converted into the root body's frame on the server; this
     * keeps the client preview visual-only while normal SCM save validation
     * remains server-authoritative.
     */
    public record ConfigurationPreviewBlock(
            UUID subLevelId, BlockPos position, BlockState state, Vec3 rootPosition
    ) {
        public ConfigurationPreviewBlock {
            position = position == null ? BlockPos.ZERO : position.immutable();
            state = state == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
            rootPosition = rootPosition == null ? Vec3.atLowerCornerOf(position) : rootPosition;
        }
    }

    public List<ConfigurationPreviewBlock> configurationPreviewBlocks(int maximumBlocks) {
        ServerSubLevel root = rootSubLevel != null && !rootSubLevel.isRemoved()
                ? rootSubLevel : containingServerSubLevel();
        if (root == null) return List.of();
        int limit = Mth.clamp(maximumBlocks, 1, 16_384);
        List<ConfigurationPreviewBlock> preview = new ArrayList<>();
        List<ServerSubLevel> bodies = initShipSubLevels(root).stream()
                .filter(ServerSubLevel.class::isInstance)
                .map(ServerSubLevel.class::cast)
                .filter(body -> !body.isRemoved())
                .sorted(Comparator.comparing(body -> body.getUniqueId().toString()))
                .toList();
        for (ServerSubLevel body : bodies) {
            if (preview.size() >= limit) break;
            int remaining = limit - preview.size();
            for (SubLevelBlockEntityCollector.LoadedBlock block
                    : SubLevelBlockEntityCollector.getLoadedBlocks(body, remaining)) {
                if (block.state().isAir()) continue;
                preview.add(new ConfigurationPreviewBlock(body.getUniqueId(), block.position(), block.state(),
                        rootPosition(root, body, Vec3.atLowerCornerOf(block.position()))));
            }
        }
        return List.copyOf(preview);
    }

    // Return the complete connected body chain for the client-side live SCM
    // preview. This is topology metadata only; the client still renders the
    // currently loaded Sable block states and cannot author control targets.
    public List<UUID> configurationViewSubLevelIds() {
        ServerSubLevel root = rootSubLevel != null && !rootSubLevel.isRemoved()
                ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return List.of();
        }
        return initShipSubLevels(root).stream()
                .filter(subLevel -> subLevel != null && !subLevel.isRemoved())
                .map(SubLevel::getUniqueId)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    // Distinguish the harmless discovery pass from a full frozen calibration.
    public boolean isConfigurationScanning() {
        return configurationScanOnly && phase == Phase.SCANNING;
    }

    // Check if this can share stored map
    boolean canShareStoredMap() {
        return isModuleAttached() && !isInitializing() && phase != Phase.ERROR
                && mapId != null;
    }

    // Set the assembly aggregator
    void setAssemblyAggregator(boolean aggregator) {
        if (assemblyAggregator == aggregator) {
            return;
        }
        assemblyAggregator = aggregator;
        controller.setChanged();
    }

    // Check if initialization progress should be displayed
    public boolean displaysInitializationProgress() {
        return isInitializing() && initializationFilters.displayProgress();
    }

    // Get the initialization progress
    public double initializationProgress() {
        return Mth.clamp(progress, 0.0D, 1.0D);
    }

    // Get the initialization status
    public String initializationStatus() {
        return status == null ? "" : status;
    }

    // Sync the initialization displays
    public void syncInitializationDisplays(
            boolean visible, int percent, String progressStatus
    ) {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        Set<ShipControlMap.CrnDisplay> crnUpdates = new LinkedHashSet<>();
        Set<ShipControlMap.AccDisplay> accUpdates = new LinkedHashSet<>();
        if (visible) {
            for (ShipControlMap.CrnDisplay display : calibrationCrnDisplays) {
                if (initCrnDisplays.add(display)) {
                    crnUpdates.add(display);
                }
            }
            for (ShipControlMap.AccDisplay display : calibrationAccDisplays) {
                if (initAccDisplays.add(display)) {
                    accUpdates.add(display);
                }
            }
            String normalizedStatus = progressStatus == null ? "" : progressStatus;
            if (percent != lastInitDispPct
                    || !normalizedStatus.equals(lastInitDispStatus)) {
                crnUpdates.addAll(initCrnDisplays);
                accUpdates.addAll(initAccDisplays);
                lastInitDispPct = percent;
                lastInitDispStatus = normalizedStatus;
            }
        } else {
            crnUpdates.addAll(initCrnDisplays);
            accUpdates.addAll(initAccDisplays);
        }
        for (ShipControlMap.CrnDisplay display : crnUpdates) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, display.subLevelId(), display.blockPosition());
            RailwayNavigatorGraphCompat.publishShipInitializationProgress(
                    blockEntity, visible, percent, progressStatus);
        }
        for (ShipControlMap.AccDisplay display : accUpdates) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, display.subLevelId(), display.blockPosition());
            if (blockEntity instanceof AccDisplayBlockEntity accDisplay) {
                accDisplay.acceptShipInitializationProgress(
                        visible, percent, progressStatus);
            }
        }
        if (!visible) {
            initCrnDisplays.clear();
            initAccDisplays.clear();
            lastInitDispPct = -1;
            lastInitDispStatus = "";
        }
    }

    // Map the id
    public @Nullable UUID mapId() {
        return mapId;
    }

    // Get the stored map
    @Nullable ShipControlMap storedMap() {
        return map;
    }

    // Get the effective assembly map
    private @Nullable ShipControlMap effectiveAssemblyMap() {
        return activeAssemblyMap != null ? activeAssemblyMap : map;
    }

    // Get the mapped sublevel ids
    public Set<UUID> mappedSubLevelIds() {
        if (!activeAssemblySubLevelIds.isEmpty()) {
            return activeAssemblySubLevelIds;
        }
        ShipControlMap currentMap = effectiveAssemblyMap();
        if (currentMap == null) {
            ServerSubLevel root = containingServerSubLevel();
            return root == null ? Set.of() : Set.of(root.getUniqueId());
        }
        Set<UUID> ids = new HashSet<>();
        ids.add(currentMap.rootSubLevelId());
        currentMap.units().forEach(unit -> ids.add(unit.subLevelId()));
        currentMap.bearings().forEach(bearing -> {
            ids.add(bearing.hostSubLevelId());
            ids.addAll(bearing.childSubLevelIds());
        });
        currentMap.dockingConnectors().forEach(connector ->
                ids.add(connector.subLevelId()));
        currentMap.crnDisplays().forEach(display -> ids.add(display.subLevelId()));
        currentMap.accDisplays().forEach(display -> ids.add(display.subLevelId()));
        return Set.copyOf(ids);
    }

    // Get the current ship envelope
    public SableAssemblyBoundsApi.Envelope shipEnvelope() {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return SableAssemblyBoundsApi.Envelope.DEFAULT;
        }
        ShipControlMap currentMap = effectiveAssemblyMap();
        Vec3 reference = currentMap == null ? null : worldPosition(
                root.logicalPose(), liveCenterOfMass(root, currentMap.centerOfMass()));
        return SableAssemblyBoundsApi.envelope(
                connectedShipSubLevels(root), reference);
    }

    // Select the docking connector
    public @Nullable MappedDockingConnector selectDockingConnector(Vec3 desiredWorldFacing) {
        return selectDockingConnector(desiredWorldFacing, null);
    }

    // Select the docking connector
    public @Nullable MappedDockingConnector selectDockingConnector(
            Vec3 desiredWorldFacing,
            @Nullable Vec3 targetWorldPosition
    ) {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        List<ShipControlMap.DockingConnector> connectors = availableDockingConnectors(root);
        if (root == null || connectors.isEmpty()) {
            return null;
        }
        Vec3 desired = normalize(desiredWorldFacing, Vec3.ZERO);
        Vec3 target = targetWorldPosition == null ? null : finite(targetWorldPosition);
        ShipControlMap.DockingConnector selected = connectors.stream()
                .max(Comparator.comparingDouble(connector -> {
                    MappedDockingConnector mapped = mappedDockingConnector(root, connector);
                    double alignment = desired.lengthSqr() <= 1.0E-12D
                            ? 0.0D : mapped.worldFacing().dot(desired);
                    double distancePenalty = target == null ? 0.0D
                            : Math.min(1_000.0D,
                                    mapped.worldTipPosition().distanceTo(target)) * 1.0E-4D;
                    return alignment - distancePenalty - connector.index() * 1.0E-9D;
                }))
                .orElse(null);
        return selected == null ? null : mappedDockingConnector(root, selected);
    }

    // Get the mapped docking connector
    public @Nullable MappedDockingConnector mappedDockingConnector(int connectorIndex) {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        ShipControlMap.DockingConnector connector = mappedConnector(connectorIndex);
        return root == null || connector == null
                ? null : mappedDockingConnector(root, connector);
    }

    // Get the mapped docking connectors
    public List<MappedDockingConnector> mappedDockingConnectors() {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        if (root == null || effectiveAssemblyMap() == null) {
            return List.of();
        }
        return availableDockingConnectors(root).stream()
                .map(connector -> mappedDockingConnector(root, connector))
                .toList();
    }

    // Activate the docking connector
    public void activateDockingConnector(int connectorIndex) {
        for (MappedDockingConnector connector : mappedDockingConnectors()) {
            BlockEntity blockEntity = DockingConnectorAutomation.resolve(
                    controller.getLevel(), connector.subLevelId(), connector.blockPosition());
            if (connector.index() == connectorIndex) {
                DockingConnectorAutomation.resetTransfers(blockEntity);
                DockingConnectorAutomation.setPowered(blockEntity, true);
            } else {
                DockingConnectorAutomation.disengage(blockEntity, null);
            }
        }
    }

    // Get the available docking connectors
    private List<ShipControlMap.DockingConnector> availableDockingConnectors(
            @Nullable ServerSubLevel root
    ) {
        ShipControlMap currentMap = effectiveAssemblyMap();
        if (root == null || currentMap == null) {
            return List.of();
        }
        List<ShipControlMap.DockingConnector> available =
                new ArrayList<>(currentMap.dockingConnectors());
        Set<DynamicConnectorKey> mappedKeys = new HashSet<>();
        int nextIdx = 0;
        for (ShipControlMap.DockingConnector connector : currentMap.dockingConnectors()) {
            mappedKeys.add(new DynamicConnectorKey(
                    connector.subLevelId(), connector.blockPosition()));
            nextIdx = Math.max(nextIdx, connector.index() + 1);
        }
        nextDynamicConnectorIdx = Math.max(nextDynamicConnectorIdx, nextIdx);

        for (ShipStockNetworkCache.Connector connector
                : controller.getShipStockNetworkSnapshot().connectors()) {
            DynamicConnectorKey key = new DynamicConnectorKey(
                    connector.subLevelId(), connector.position());
            if (mappedKeys.contains(key)) {
                continue;
            }
            Object resolved = SubLevelBlockEntityCollector.getSubLevel(
                    controller.getLevel(), connector.subLevelId());
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), connector.subLevelId(), connector.position());
            if (!(resolved instanceof SubLevel src)
                    || blockEntity == null || !isDockingConnector(blockEntity)) {
                continue;
            }
            Direction facing = blockEntity.getBlockState().getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            Vec3 localFacing = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 localTip = blockEntity.getBlockPos().getCenter().add(localFacing.scale(1.5D));
            int idx = dynamicConnectorIndices.computeIfAbsent(
                    key, ignored -> nextDynamicConnectorIdx++);
            available.add(new ShipControlMap.DockingConnector(
                    idx, connector.subLevelId(), connector.position(),
                    rootPosition(root, src, localTip),
                    rootDirection(root, src, localFacing)));
        }
        available.sort(Comparator.comparingInt(ShipControlMap.DockingConnector::index));
        return List.copyOf(available);
    }

    // Clear the dynamic connector registry
    private void clearDynamicConnectorRegistry() {
        dynamicConnectorIndices.clear();
        nextDynamicConnectorIdx = 0;
    }

    // Get the mapped docking connector
    private MappedDockingConnector mappedDockingConnector(
            ServerSubLevel root,
            ShipControlMap.DockingConnector connector
    ) {
        Object resolved = SubLevelBlockEntityCollector.getSubLevel(
                controller.getLevel(), connector.subLevelId());
        BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                controller.getLevel(), connector.subLevelId(), connector.blockPosition());
        if (resolved instanceof SubLevel src && blockEntity != null
                && isDockingConnector(blockEntity)) {
            Direction facing = blockEntity.getBlockState().getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            Vec3 localFacing = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 localUp = dockingConnectorUp(localFacing);
            Vec3 localTip = blockEntity.getBlockPos().getCenter()
                    .add(localFacing.scale(1.5D));
            return new MappedDockingConnector(
                    connector.index(), connector.subLevelId(), connector.blockPosition(),
                    worldPosition(src.logicalPose(), localTip),
                    worldDirection(src.logicalPose(), localFacing),
                    worldDirection(src.logicalPose(), localUp));
        }
        return new MappedDockingConnector(
                connector.index(), connector.subLevelId(), connector.blockPosition(),
                worldPosition(root.logicalPose(), connector.rootTipPosition()),
                normalize(rootDirectionToWorld(connector.rootFacing()), Vec3.ZERO),
                normalize(rootDirectionToWorld(
                        dockingConnectorUp(connector.rootFacing())), Vec3.ZERO));
    }

    // Set the map id
    public void setMapId(@Nullable UUID mapId) {
        boolean changed = !java.util.Objects.equals(this.mapId, mapId);
        if (mapId == null || changed) {
            cancelPostLoadPoseHold();
        }
        if (!changed) {
            return;
        }
        releaseNavResidency();
        clearThrusterProtection();
        releaseControlAuthority();
        releaseControlActuators();
        this.mapId = mapId;
        this.map = null;
        this.activeAssemblyMap = null;
        this.activeAssemblyPrimaryMap = null;
        this.activeAssemblyTopology = null;
        this.activeScmActuatorOwners = Map.of();
        this.mainCarriageMaps.clear();
        this.mainCarriageMapValidationTicks.clear();
        this.mainCarriageMapValidationRevisions.clear();
        this.dirtyMainCarriageMapIds.clear();
        this.activeAssemblyMapSignature = Long.MIN_VALUE;
        this.activeAssemblySubLevelIds = Set.of();
        this.activeCarriageCount = 0;
        this.absorbedScmMapCount = 0;
        this.lastMainCarriageMapSafetyRefreshTick = Long.MIN_VALUE;
        this.suppressedForeignTopologyFingerprint = "";
        clearDynamicConnectorRegistry();
        this.mapLoadAttempted = false;
        invalidateTelemetryCache();
        clearPendingOrphanCleanup();
        clearInitMapPlan();
        this.phase = Phase.IDLE;
        this.status = mapId == null ? "Not initialized" : "Loading stored control map";
        this.progress = 0.0D;
        signalAssemblyMapChanged();
    }

    // Suspend the assembly transfer
    public void suspendForAssemblyTransfer() {
        if (isInitializing()) {
        syncInitializationDisplays(false, 0, "");
            restoreCalActuators();
            releaseFreezeHandles();
            restoreInitAssembly();
            status = "Initialization interrupted by contraption assembly";
            showInitProgress(false, true);
            releaseInitProtection();
            releaseInitTracking();
            calibrationUnits.clear();
            calibrationBearings.clear();
            calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        calibrationSubLevels.clear();
            initializationSubLevels.clear();
            initBodyStates.clear();
            clearInitMapPlan();
            reconciliationBaseMap = null;
            prevInitMap = null;
            phase = Phase.IDLE;
            progress = 0.0D;
        }
        clearThrusterProtection();
        releaseControlActuators();
        resetControlState();
    }

    // Invalidate the assembly transfer
    public void invalidateForAssemblyTransfer() {
        suspendForAssemblyTransfer();
        releaseNavResidency();
        mapId = null;
        map = null;
        clearInitMapPlan();
        reconciliationBaseMap = null;
        clearDynamicConnectorRegistry();
        mapLoadAttempted = false;
        status = "Contraption changed; initialize the control module again";
        phase = Phase.ERROR;
        signalAssemblyMapChanged();
        controller.setChanged();
    }

    // Close the ship control module
    public void close() {
        close(true);
    }

    // Set the docking magnetic capture
    void setDockingMagneticCapture(int connectorIndex, boolean active) {
        magneticConnectorIdx = active ? connectorIndex : -1;
    }

    // Suspend the chunk unload
    public void suspendForChunkUnload() {
        resumePoseHoldAfterLoad = mapId != null;
        resumePoseHoldReleaseRequested = true;
        resumePoseHoldTicks = resumePoseHoldAfterLoad ? RESUME_POSE_HOLD_TICKS : 0;
        resumePoseHoldTimeoutTicks = resumePoseHoldAfterLoad
                ? RESUME_POSE_HOLD_TIMEOUT_TICKS : 0;
        resumePoseHoldLoadAttempts = 0;
        close(false);
    }

    // Close the ship control module
    private void close(boolean releaseResidency) {
        if (shutdownPrepared) {
            return;
        }
        Map<String, CouplerControlRequest> retainedCouplerRequests = releaseResidency
                ? Map.of() : new LinkedHashMap<>(couplerControlRequests);
        if (releaseResidency) {
            releaseNavResidency();
        } else {
            detachNavResidency();
        }
        syncInitializationDisplays(false, 0, "");
        restoreCalActuators();
        releaseFreezeHandles();
        restoreInitAssembly();
        releaseInitProtection();
        releaseInitTracking();
        releaseControlAuthority();
        clearThrusterProtection();
        releaseControlActuators();
        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        calibrationSubLevels.clear();
        initializationSubLevels.clear();
        initBodyStates.clear();
        clearInitMapPlan();
        reconciliationBaseMap = null;
        prevInitMap = null;
        clearPendingOrphanCleanup();
        clearDynamicConnectorRegistry();
        resetControlState();
        couplerControlRequests.putAll(retainedCouplerRequests);
        LIVE_RUNTIMES.remove(this);
    }

    // Stop the initialization
    private boolean stopInitialization() {
        if (!isInitializing()) {
            return false;
        }
        configurationScanOnly = false;
        releaseControlAuthority();
        syncInitializationDisplays(false, 0, "");
        restoreCalActuators();
        releaseFreezeHandles();
        restoreInitAssembly();
        releaseInitProtection();
        releaseInitTracking();
        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        calibrationSubLevels.clear();
        initializationSubLevels.clear();
        initBodyStates.clear();
        clearInitMapPlan();
        activeCommands.entrySet().removeIf(entry ->
                "ship_initialize".equals(entry.getValue().type())
                        || "ship_scan_configuration".equals(entry.getValue().type()));
        lastIssuedCommands.entrySet().removeIf(entry ->
                "ship_initialize".equals(entry.getValue().type())
                        || "ship_scan_configuration".equals(entry.getValue().type()));
        if (prevInitMap != null) {
            map = prevInitMap;
            mapId = prevInitMap.id();
            phase = Phase.READY;
            status = "Initialization stopped; previous control map restored";
            progress = 1.0D;
        } else {
            map = null;
            mapId = null;
            phase = Phase.IDLE;
            status = "Initialization stopped";
            progress = 0.0D;
        }
        reconciliationBaseMap = null;
        prevInitMap = null;
        initFinalMsgSent = true;
        invalidateTelemetryCache();
        signalAssemblyMapChanged();
        controller.setChanged();
        controller.sendData();
        return true;
    }

    // Load the map
    private void loadMap() {
        mapLoadAttempted = true;
        map = ShipControlMapStore.load(controller.getLevel(), mapId);
        if (map == null) {
            phase = Phase.ERROR;
            status = "Stored control map was not found";
            progress = 0.0D;
            return;
        }
        ServerSubLevel currentRoot = containingServerSubLevel();
        if (currentRoot == null || !map.rootSubLevelId().equals(currentRoot.getUniqueId())) {
            phase = Phase.ERROR;
            status = "Stored map belongs to a different contraption";
            progress = 0.0D;
            return;
        }
        rootSubLevel = currentRoot;
        queueOrphanedMappedThrusters(map);
        releaseOrphanedMappedThrusters();
        if (requiresCalibrationRefresh(map)) {
            phase = Phase.ERROR;
            status = "Stored thruster calibration is outdated; run Initialize Control Module again";
            progress = 0.0D;
            return;
        }
        phase = Phase.READY;
        status = readyStatus(map);
        progress = 1.0D;
        signalAssemblyMapChanged();
    }

    // Queue the orphaned mapped thrusters
    private void queueOrphanedMappedThrusters(ShipControlMap storedMap) {
        pendingOrphanCleanupUnits.clear();
        for (ShipControlMap.PropulsionUnit unit : storedMap.units()) {
            if ("createthrusters:thruster".equals(unit.blockId())) {
                pendingOrphanCleanupUnits.put(unit.index(), unit);
            }
        }
        orphanCleanupAttemptsRemaining = 200;
        nextOrphanCleanupTick = Long.MIN_VALUE;
    }

    // Release the orphaned mapped thrusters
    private void releaseOrphanedMappedThrusters() {
        for (ShipControlMap.PropulsionUnit unit
                : List.copyOf(pendingOrphanCleanupUnits.values())) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), unit.subLevelId(), unit.blockPosition());
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
                thruster.releaseOrphanedShipControl(CONTROL_CHANNEL);
                pendingOrphanCleanupUnits.remove(unit.index());
            } else if (blockEntity != null) {
                pendingOrphanCleanupUnits.remove(unit.index());
            }
        }
        orphanCleanupAttemptsRemaining--;
        if (pendingOrphanCleanupUnits.isEmpty() || orphanCleanupAttemptsRemaining <= 0) {
            clearPendingOrphanCleanup();
            return;
        }
        nextOrphanCleanupTick = controller.getLevel().getGameTime() + 5L;
    }

    // Clear the pending orphan cleanup
    private void clearPendingOrphanCleanup() {
        pendingOrphanCleanupUnits.clear();
        orphanCleanupAttemptsRemaining = 0;
        nextOrphanCleanupTick = Long.MIN_VALUE;
    }

    // Handle the freeze parent sublevel
    private void freezeParentSubLevel() throws ReflectiveOperationException {
        ServerSubLevel root = requireRootSubLevel();
        int subLevelCount = addPoseFreezeConstraints(root);
        phase = Phase.SCANNING;
        status = "Assembly fixed; scanning " + subLevelCount + " connected sub-level"
                + (subLevelCount == 1 ? "" : "s");
        progress = 0.08D;
    }

    // Add the pose freeze constraints
    private int addPoseFreezeConstraints(ServerSubLevel root)
            throws ReflectiveOperationException {
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(root.getLevel());
        if (physicsSystem == null) {
            throw new IllegalStateException("Sable physics system is unavailable");
        }
        Object pipeline = physicsSystem.getPipeline();
        List<ServerSubLevel> subLevels = initShipSubLevels(root).stream()
                .filter(ServerSubLevel.class::isInstance)
                .map(ServerSubLevel.class::cast)
                .sorted(Comparator.comparing(subLevel -> subLevel.getUniqueId().toString()))
                .toList();
        for (ServerSubLevel body : subLevels) {
            Object config = SableConstraintApi.fixedConfiguration(
                    new Vector3d(body.logicalPose().position()),
                    new Vector3d(body.logicalPose().rotationPoint()),
                    new Quaterniond(body.logicalPose().orientation()));
            Object constraint = SableConstraintApi.addConstraint(
                    pipeline, null, body, config);
            if (!(constraint instanceof PhysicsConstraintHandle handle)) {
                throw new IllegalStateException(
                        "Sable did not create a ship initialization constraint for "
                                + body.getUniqueId());
            }
            (body.getUniqueId().equals(root.getUniqueId())
                    ? freezeHandles : nestedFreezeHandles).add(handle);
            freezeHandlesBySubLevel.put(body.getUniqueId(), handle);
        }
        return subLevels.size();
    }

    // Handle the freeze at current pose for shutdown
    private void freezeAtCurrentPoseForShutdown() {
        if (!freezeHandles.isEmpty() || !nestedFreezeHandles.isEmpty()) {
            return;
        }
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return;
        }
        try {
            addPoseFreezeConstraints(root);
        } catch (ReflectiveOperationException | RuntimeException err) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not hold ship pose during server shutdown", err);
            releaseFreezeHandles();
        }
    }

    // Update the resume pose hold
    private boolean tickResumePoseHold() {
        if (freezeHandles.isEmpty() && nestedFreezeHandles.isEmpty()) {
            ServerSubLevel root = rootSubLevel != null
                    ? rootSubLevel : containingServerSubLevel();
            if (root == null) {
                return retryResumePoseHold();
            }
            try {
                if (addPoseFreezeConstraints(root) == 0) {
                    return retryResumePoseHold();
                }
                resumePoseHoldLoadAttempts = 0;
            } catch (ReflectiveOperationException | RuntimeException err) {
                return retryResumePoseHold();
            }
        }
        if (resumePoseHoldTicks > 0) {
            resumePoseHoldTicks--;
            status = "Holding ship pose while world physics loads";
            return false;
        }
        if (resumePoseHoldTimeoutTicks > 0) {
            resumePoseHoldTimeoutTicks--;
        }
        if (resumePoseHoldTimeoutTicks <= 0 && !resumePoseHoldReleaseRequested) {
            resumePoseHoldReleaseRequested = true;
            controller.forceShippingScheduleResumeAfterLoad();
            LOGGER.log(System.Logger.Level.WARNING,
                    "Force-releasing a ship pose hold after schedule control did not resume in time");
        }
        if (!resumePoseHoldReleaseRequested) {
            status = "Holding ship pose while schedule control resumes";
            return false;
        }
        releaseFreezeHandles();
        resumePoseHoldAfterLoad = false;
        resumePoseHoldReleaseRequested = false;
        resumePoseHoldTimeoutTicks = 0;
        resumePoseHoldLoadAttempts = 0;
        return true;
    }

    // Retry resuming the pose hold
    private boolean retryResumePoseHold() {
        resumePoseHoldLoadAttempts++;
        status = "Waiting for ship physics before resuming control";
        if (resumePoseHoldLoadAttempts >= MAX_RESUME_POSE_HOLD_LOAD_ATTEMPTS) {
            releaseFreezeHandles();
            resumePoseHoldAfterLoad = false;
            resumePoseHoldTicks = 0;
            resumePoseHoldTimeoutTicks = 0;
            controller.forceShippingScheduleResumeAfterLoad();
            LOGGER.log(System.Logger.Level.WARNING,
                    "Resuming ship control without a temporary post-load pose hold");
            return true;
        }
        return false;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         CALIBRATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scan the propulsion units
    private void scanPropulsionUnits() {
        ServerSubLevel root = requireRootSubLevel();
        List<SubLevel> allSubLevels =
                new ArrayList<>(initShipSubLevels(root));
        allSubLevels.sort(Comparator.comparing(subLevel -> subLevel.getUniqueId().toString()));
        List<SubLevel> ownedSubLevels = allSubLevels.stream()
                .filter(subLevel -> !initForeignSubLevelIds.contains(
                        subLevel.getUniqueId()))
                .toList();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        discoverCrnDisplays(ownedSubLevels);
        List<SubLevel> subLevels = ownedSubLevels;
        if (!initializationFilters.mapsBearings()) {
            Set<UUID> ignoredSubLevels = new HashSet<>(bearingChildSubLevels(subLevels));
            ignoredSubLevels.remove(root.getUniqueId());
            subLevels = subLevels.stream()
                    .filter(subLevel -> initializationFilters.mapsSubLevel(
                            ignoredSubLevels.contains(subLevel.getUniqueId())))
                    .toList();
        }
        // ------------------------------------PROPULSION UNITS------------------------------------
        calibrationUnits.clear();
        calibrationSubLevels.clear();
        calibrationDockingConnectors.clear();
        Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusableUnits =
                reusablePropulsionUnits(reconciliationBaseMap);
        subLevels.forEach(subLevel -> calibrationSubLevels.put(subLevel.getUniqueId(), subLevel));
        collectCalibrationDockingConnectors(root, subLevels);
        for (SubLevel subLevel : subLevels) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                List<Actuator> actuators = actuatorsFor(blockEntity);
                if (actuators.isEmpty()
                        || !initializationFilters.mapsPropulsion(isThrusterProvider(blockEntity))) {
                    continue;
                }
                ResourceLocation blockId =
                        BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
                for (Actuator actuator : actuators) {
                    Vec3 rootPosition = rootPosition(
                            root, subLevel, actuator.localForcePosition());
                    Vec3 forceDirection = rootDirection(
                            root, subLevel, actuator.localForceDirection());
                    calibrationUnits.add(new CalibrationUnit(
                            subLevel.getUniqueId(), blockEntity.getBlockPos(), blockId.toString(),
                            rootPosition, forceDirection, actuator,
                            reusableUnits.get(new PropulsionUnitKey(
                                    subLevel.getUniqueId(), blockEntity.getBlockPos(),
                                    blockId.toString(), actuator.kind()))));
                }
            }
        }
        // ------------------------------------LINKED CONTROLS------------------------------------
        discoverLinkedScmControls(root, ownedSubLevels, reusableUnits);
        // SCM configuration is an explicit extension of the normal control
        // surface. Merge its selected probes into this scan before calibration
        // instead of replacing the calibrated map with a live-adapter snapshot.
        // This is what makes a selected block face participate in the same
        // initialization and allocation path as ordinary thrusters and linker
        // targets.
        discoverConfiguredScmControls(root, ownedSubLevels, reusableUnits);
        calibrationUnits.sort(Comparator
                .comparing((CalibrationUnit unit) -> isControlMode(ScmBuiltinControlModes.CAR_ID)
                        && unit.actuator.isWheelControl())
                .thenComparing(unit -> unit.subLevelId.toString())
                .thenComparing(unit -> unit.blockPosition)
                .thenComparing(unit -> unit.actuator.kind()));
        if (isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            carWheelFirstCalibrationIndex = firstWheelCalIdx();
        }
        if (initializationV2) {
            configV2CalUnits();
        }
        List<AerodynamicSurfaceCalibration> aerodynamicSurfaces =
                initializationFilters.mapsSails()
                        ? findAeroSurfaces(subLevels) : List.of();
        calibrationBearings.clear();
        if (initializationFilters.mapsBearings()) {
            findCalBearings(subLevels, aerodynamicSurfaces);
        }
        calibrationUnitIndex = 0;
        calibrationSampleIndex = 0;
        sampleSettleTicks = 0;
        resetCalSamples();
        carWheelCalibrationPending = false;
        carWheelCalibrationStarted = false;
        carWheelDriveActuator = null;
        v2SamplesFinalized = false;
        v2RepresentativeSweepStarted = false;
        v2CompletedPropulsionSamples = 0;
        v2TotalPropulsionSamples = 0;
        resetBearingCal();
        if (configurationScanOnly) {
            configurationCandidates = calibrationUnits.stream()
                    .map(unit -> new ScmConfigurationProfile.UnitReference(
                            unit.subLevelId, unit.blockPosition, unit.actuator.kind()))
                    .distinct().toList();
            seedDefaultScmConfigurationGroups();
            configurationScanOnly = false;
            // Discovery must not replace an already calibrated control map. A new
            // craft simply returns to idle until the player has configured groups
            // and explicitly asks for calibration/activation.
            map = prevInitMap;
            mapId = map == null ? mapId : map.id();
            phase = map == null ? Phase.CONFIGURING : Phase.READY;
            status = "SCM configuration scan complete: " + configurationCandidates.size()
                    + " controllable block"
                    + (configurationCandidates.size() == 1 ? "" : "s") + " discovered";
            progress = 1.0D;
            activeCommands.values().stream()
                    .filter(command -> "ship_scan_configuration".equals(command.type()))
                    .forEach(this::completeCommand);
            signalAssemblyMapChanged();
            controller.setChanged();
            return;
        }
        if (isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            releaseNestedFreezeHandles();
        }
        phase = Phase.CALIBRATING;
        if (calibrationUnits.isEmpty() && calibrationBearings.isEmpty()) {
            status = initForeignMaps.isEmpty()
                    ? "No propulsion or aerodynamic control surfaces found"
                    : "Using " + initForeignMaps.size()
                    + " initialized carriage SCM map"
                    + (initForeignMaps.size() == 1 ? "" : "s");
        } else if (isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            status = "Testing linked drivetrain configurations against the terrain";
        } else {
            status = initializationV2
                    ? "V2 matrix probing propulsion and sampling representative control groups"
                    : "Calibrating propulsion, bearing geometry, and aerodynamic control surfaces";
        }
        progress = 0.15D;
    }

    // Seed visible, adapter-based groups for a newly discovered craft. This is
    // a starting layout only: it never overwrites an existing player profile
    // and deliberately does not bind a group to a ship action/function.
    private void seedDefaultScmConfigurationGroups() {
        if (mapId == null || configurationCandidates.isEmpty()) {
            return;
        }
        ScmConfigurationProfile existing = controller.getScmConfigurationProfile();
        if (mapId.equals(existing.mapId()) && !existing.groups().isEmpty()) {
            return;
        }
        Map<String, Set<ScmConfigurationProfile.UnitReference>> byAdapter = new LinkedHashMap<>();
        for (ScmConfigurationProfile.UnitReference unit : configurationCandidates) {
            String adapter = unit.adapter().isBlank() ? "actuator" : unit.adapter();
            byAdapter.computeIfAbsent(adapter, ignored -> new LinkedHashSet<>()).add(unit);
        }
        List<ScmConfigurationProfile.Group> groups = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, Set<ScmConfigurationProfile.UnitReference>> entry : byAdapter.entrySet()) {
            String baseId = "adapter_" + entry.getKey().replaceAll("[^a-zA-Z0-9_]", "_");
            if (baseId.length() > 56) {
                baseId = baseId.substring(0, 56);
            }
            String id = baseId;
            int suffix = 2;
            while (!ids.add(id)) {
                id = baseId + "_" + suffix++;
            }
            groups.add(new ScmConfigurationProfile.Group(id,
                    entry.getKey().replace('_', ' '), entry.getValue()));
        }
        ScmConfigurationProfile seeded = ScmConfigurationProfile.empty();
        seeded.replace(mapId, groups, Map.of(), existing.excludedUnits());
        controller.setScmConfigurationProfile(seeded);
    }

    /**
     * Build a control map from the explicit SCM configuration. Unlike the
     * compatibility initializer above this does not crawl the assembled body,
     * freeze it, or apply any calibration controls. The saved profile is the
     * player's declaration of the control surface; Sable poses and the block
     * adapters provide the current geometry and capacity at initialization
     * time, while ordinary live feedback deals with residual response error.
     */
    private void readConfiguredPropulsionUnits(ServerSubLevel root) {
        ScmConfigurationProfile profile = controller.getScmConfigurationProfile();
        Set<ScmConfigurationProfile.UnitReference> configured = new LinkedHashSet<>();
        profile.groups().forEach(group -> configured.addAll(group.units()));
        if (configured.isEmpty()) {
            profileDrivenInitialization = false;
            phase = Phase.CONFIGURING;
            status = "SCM configuration does not contain any assigned actuators";
            progress = 0.0D;
            return;
        }

        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationSubLevels.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusableUnits =
                reusablePropulsionUnits(reconciliationBaseMap);
        Set<UUID> visitedSubLevels = new LinkedHashSet<>();
        Set<ScmConfigurationProfile.UnitReference> resolved = new LinkedHashSet<>();
        Level lookupLevel = controller.getLevel();

        // Actuator groups are explicit, but docking connectors are passive ship
        // topology. They must always survive a profile-driven map rebuild or a
        // scheduled dock command has no connector to approach or activate.
        List<SubLevel> ownedSubLevels = new ArrayList<>(initShipSubLevels(root));
        ownedSubLevels.removeIf(subLevel -> subLevel == null || subLevel.isRemoved()
                || initForeignSubLevelIds.contains(subLevel.getUniqueId()));
        if (ownedSubLevels.stream().noneMatch(subLevel -> root.getUniqueId()
                .equals(subLevel.getUniqueId()))) {
            ownedSubLevels.add(root);
        }
        ownedSubLevels.sort(Comparator.comparing(subLevel -> subLevel.getUniqueId().toString()));
        ownedSubLevels.forEach(subLevel -> calibrationSubLevels.put(subLevel.getUniqueId(), subLevel));
        collectCalibrationDockingConnectors(root, ownedSubLevels);

        for (ScmConfigurationProfile.UnitReference reference : configured) {
            if (!reference.isValid()) {
                continue;
            }
            SubLevel subLevel = SableLevelApi.subLevel(lookupLevel, reference.subLevelId());
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            visitedSubLevels.add(subLevel.getUniqueId());
            calibrationSubLevels.put(subLevel.getUniqueId(), subLevel);
            // The collector can expose a render/assembly mirror for a loaded
            // sub-level. SCM probes must bind the authoritative live entity:
            // otherwise their liveness test rejects every unit before the
            // allocator is allowed to drive it.
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    lookupLevel, reference.subLevelId(), reference.blockPosition());
            if (blockEntity == null) {
                blockEntity = SubLevelBlockEntityCollector.getBlockEntity(
                        subLevel, reference.blockPosition());
            }
            BlockState state = blockEntity == null
                    ? subLevel.getLevel().getBlockState(reference.blockPosition())
                    : blockEntity.getBlockState();
            if (state.isAir()) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            List<Actuator> configuredActuators = configuredActuatorsFor(
                    reference, subLevel, blockEntity, root);
            for (Actuator actuator : configuredActuators) {
                calibrationUnits.add(new CalibrationUnit(
                        subLevel.getUniqueId(), reference.blockPosition(), blockId.toString(),
                        rootPosition(root, subLevel, actuator.localForcePosition()),
                        rootDirection(root, subLevel, actuator.localForceDirection()), actuator,
                        reusableUnits.get(new PropulsionUnitKey(
                                subLevel.getUniqueId(), reference.blockPosition(),
                                blockId.toString(), actuator.kind())), false, true));
                resolved.add(reference);
            }
        }
        calibrationUnits.sort(Comparator
                .comparing((CalibrationUnit unit) -> unit.subLevelId.toString())
                .thenComparing(unit -> unit.blockPosition)
                .thenComparing(unit -> unit.actuator.kind()));
        configurationCandidates = List.copyOf(configured);
        calibrationUnitIndex = 0;
        calibrationSampleIndex = 0;
        sampleSettleTicks = 0;
        resetCalSamples();
        profileDrivenInitialization = false;

        if (calibrationUnits.isEmpty()) {
            phase = Phase.CONFIGURING;
            status = "No configured SCM actuators are currently loaded";
            progress = 0.0D;
            return;
        }
        if (resolved.size() != configured.size()) {
            status = "Using " + calibrationUnits.size() + " configured SCM actuators ("
                    + (configured.size() - resolved.size()) + " unavailable)";
        } else {
            status = "Building SCM control map from configured live adapters";
        }
        // There is intentionally no CALIBRATING phase here. The map captures
        // current positions, orientations, controls and safe adapter limits;
        // live SCM feedback corrects error without a probe sweep.
        phase = Phase.SAVING;
        progress = 0.92D;
    }

    // Record passive docking topology for both discovery and explicit-profile
    // map construction. Connector indices are deterministic because schedule
    // data persists those indices in target-point references.
    private void collectCalibrationDockingConnectors(
            ServerSubLevel root,
            Collection<? extends SubLevel> subLevels
    ) {
        calibrationDockingConnectors.clear();
        if (root == null || subLevels == null) {
            return;
        }
        List<SubLevel> ordered = new ArrayList<>();
        for (SubLevel subLevel : subLevels) {
            if (subLevel != null && !subLevel.isRemoved()) {
                ordered.add(subLevel);
            }
        }
        ordered.sort(Comparator.comparing(subLevel -> subLevel.getUniqueId().toString()));
        for (SubLevel subLevel : ordered) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (!isDockingConnector(blockEntity)) {
                    continue;
                }
                Direction facing = blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
                Vec3 localFacing = Vec3.atLowerCornerOf(facing.getNormal());
                Vec3 localTip = blockEntity.getBlockPos().getCenter()
                        .add(localFacing.scale(1.5D));
                calibrationDockingConnectors.add(new ShipControlMap.DockingConnector(
                        calibrationDockingConnectors.size(), subLevel.getUniqueId(),
                        blockEntity.getBlockPos(), rootPosition(root, subLevel, localTip),
                        rootDirection(root, subLevel, localFacing)));
            }
        }
        calibrationDockingConnectors.sort(Comparator
                .comparing((ShipControlMap.DockingConnector connector) ->
                        connector.subLevelId().toString())
                .thenComparing(ShipControlMap.DockingConnector::blockPosition));
        for (int idx = 0; idx < calibrationDockingConnectors.size(); idx++) {
            ShipControlMap.DockingConnector connector = calibrationDockingConnectors.get(idx);
            calibrationDockingConnectors.set(idx, new ShipControlMap.DockingConnector(
                    idx, connector.subLevelId(), connector.blockPosition(),
                    connector.rootTipPosition(), connector.rootFacing()));
        }
    }

    // Resolve one player-authored block binding through the same SCM probe
    // registry used by linker targets. A selected face becomes a real injected
    // redstone plane, while adapter-specific probes (for example Directional
    // Gearshift) can translate that face into their native safe control path.
    private List<Actuator> configuredActuatorsFor(
            ScmConfigurationProfile.UnitReference reference,
            SubLevel subLevel,
            @Nullable BlockEntity blockEntity,
            ServerSubLevel root
    ) {
        List<Actuator> ordinary = actuatorsFor(blockEntity);
        boolean requestedProbe = reference.usesFaceControl()
                || reference.adapter().startsWith("scm:") || ordinary.isEmpty();
        if (requestedProbe) {
            BlockState state = subLevel.getLevel().getBlockState(reference.blockPosition());
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            Direction face = reference.face();
            ScmTarget target = new ScmTarget(reference.subLevelId(), reference.blockPosition(),
                    id == null ? "minecraft:air" : id.toString(),
                    state.getBlock().getName().getString(),
                    face == null ? null : reference.blockPosition().relative(face), face);
            Vec3 suggestedDirection = face == null ? Vec3.ZERO
                    : Vec3.atLowerCornerOf(face.getNormal());
            String requestedAdapter = configuredProbeAdapterId(reference.adapter());
            List<Actuator> probes = ScmControlProbeRegistry.create(
                            subLevel.getLevel(), blockEntity, target, suggestedDirection)
                    .stream().filter(probe -> requestedAdapter.isBlank()
                            || requestedAdapter.equals(probe.adapterId()))
                    .map(probe -> (Actuator) new ScmProbeActuator(
                            subLevel.getLevel(), target, blockEntity, probe))
                    .toList();
            if (!probes.isEmpty() || reference.adapter().startsWith("scm:")) {
                return probes;
            }
        }
        // This is an explicit profile binding, not automatic discovery. The
        // initialization filters only constrain the latter; applying them
        // here made an Auto-only profile silently drop its selected ordinary
        // actuators whenever the initializer was configured not to map
        // thruster providers.
        return ordinary.stream().filter(actuator -> reference.adapter().isBlank()
                || reference.adapter().equals(actuator.kind())).toList();
    }

    private static String configuredProbeAdapterId(String adapter) {
        if (adapter == null || !adapter.startsWith("scm:")) {
            return "";
        }
        String value = adapter.substring("scm:".length());
        int faceSuffix = value.indexOf(":face_");
        return faceSuffix < 0 ? value : value.substring(0, faceSuffix);
    }

    // Configure the V2 cal units
    private void configV2CalUnits() {
        if (isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            v2RepresentativeSweepStarted = true;
            v2TotalPropulsionSamples = 0;
            for (CalibrationUnit unit : calibrationUnits) {
                unit.v2RepresentativeIndex = -1;
                unit.v2Representative = false;
                boolean deferWheel = unit.actuator.isWheelControl();
                unit.calibrationPoints = unit.calibrationRequired && !deferWheel
                        ? v2RepresentativeSweep(unit.actuator) : List.of();
                v2TotalPropulsionSamples += unit.calibrationPoints.size();
            }
            return;
        }
        v2TotalPropulsionSamples = (int) calibrationUnits.stream()
                .filter(unit -> unit.calibrationRequired)
                .count();
        for (CalibrationUnit unit : calibrationUnits) {
            unit.v2RepresentativeIndex = -1;
            unit.v2Representative = false;
            unit.calibrationPoints = List.of(v2ProbePoint(unit.actuator));
        }
    }

    // Select the V2 representative units
    private void selectV2RepresentativeUnits() {
        Vec3 centerOfMass = assemblyCenterOfMass(
                requireRootSubLevel(), controller.getBlockPos().getCenter());
        Map<String, List<Integer>> families = new LinkedHashMap<>();
        for (int idx = 0; idx < calibrationUnits.size(); idx++) {
            CalibrationUnit unit = calibrationUnits.get(idx);
            families.computeIfAbsent(
                            v2ResponseFamilyKey(unit, centerOfMass),
                            ignored -> new ArrayList<>())
                    .add(idx);
        }
        int representativeSamples = 0;
        for (List<Integer> candidates : families.values()) {
            int representative = candidates.stream()
                    .max(Comparator
                            .comparingDouble((Integer idx) ->
                                    v2ProbeStrength(calibrationUnits.get(idx)))
                            .thenComparingDouble(idx -> calibrationUnits.get(idx)
                                    .actuator.theoreticalMaxThrust()))
                    .orElse(candidates.getFirst());
            for (int idx : candidates) {
                CalibrationUnit unit = calibrationUnits.get(idx);
                unit.v2RepresentativeIndex = representative;
                unit.v2Representative = idx == representative;
                unit.calibrationPoints = unit.v2Representative
                        ? v2RepresentativeSweep(unit.actuator) : List.of();
            }
            if (calibrationUnits.get(representative).calibrationRequired) {
                representativeSamples += calibrationUnits.get(representative)
                        .calibrationPoints.size();
            }
        }
        v2TotalPropulsionSamples += representativeSamples;
    }

    // Get the first wheel cal idx
    private int firstWheelCalIdx() {
        for (int idx = 0; idx < calibrationUnits.size(); idx++) {
            if (calibrationUnits.get(idx).actuator.isWheelControl()) {
                return idx;
            }
        }
        return -1;
    }

    // Discover the linked SCM controls
    private void discoverLinkedScmControls(
            ServerSubLevel root,
            List<SubLevel> subLevels,
            Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusableUnits
    ) {
        Map<UUID, SubLevel> subLevelsById = new LinkedHashMap<>();
        subLevels.forEach(subLevel -> subLevelsById.put(subLevel.getUniqueId(), subLevel));
        List<ScmTarget> targets = liveScmTargets(subLevelsById);
        if (targets.isEmpty()) {
            return;
        }
        Vec3 suggestedDir = suggestedScmTravelDir(root, targets, subLevelsById);
        for (LinkedScmProbe linked : linkedScmProbeConfigs(
                controller.getLevel(), targets, subLevelsById, root, suggestedDir,
                isControlMode(ScmBuiltinControlModes.CAR_ID))) {
            ScmTarget target = linked.target();
            SubLevel subLevel = linked.subLevel();
            Level targetLevel = linked.level();
            BlockEntity blockEntity = linked.blockEntity();
            if (blockEntity != null && (isThrusterProvider(blockEntity)
                    || !bearingActuatorsFor(blockEntity).isEmpty())) {
                continue;
            }
            Actuator actuator = new ScmProbeActuator(
                    targetLevel, target, blockEntity, linked.probe());
            Vec3 rootPosition = rootPosition(
                    root, subLevel, actuator.localForcePosition());
            Vec3 forceDirection = rootDirection(
                    root, subLevel, actuator.localForceDirection());
            calibrationUnits.add(new CalibrationUnit(
                    target.subLevelId(), target.blockPosition(), target.blockId(),
                    rootPosition, forceDirection, actuator,
                    reusableUnits.get(new PropulsionUnitKey(
                            target.subLevelId(), target.blockPosition(),
                            target.blockId(), actuator.kind()))));
        }
    }

    // Merge selected SCM configuration controls into the physical calibration
    // scan. A normal block actuator is already discovered above and is skipped
    // by its stable key; a face/probe control has its own SCM adapter key and
    // is therefore retained as an independent calibrated control surface.
    private void discoverConfiguredScmControls(
            ServerSubLevel root,
            List<SubLevel> subLevels,
            Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusableUnits
    ) {
        ScmConfigurationProfile profile = controller.getScmConfigurationProfile();
        List<ScmConfigurationProfile.UnitReference> references = profile.groups().stream()
                .flatMap(group -> group.units().stream())
                .filter(ScmConfigurationProfile.UnitReference::isValid)
                .distinct()
                .sorted(Comparator
                        .comparing((ScmConfigurationProfile.UnitReference reference) ->
                                reference.subLevelId().toString())
                        .thenComparing(ScmConfigurationProfile.UnitReference::blockPosition)
                        .thenComparing(ScmConfigurationProfile.UnitReference::adapter)
                        .thenComparing(reference -> reference.face() == null
                                ? "" : reference.face().getSerializedName()))
                .toList();
        if (references.isEmpty()) {
            return;
        }
        Map<UUID, SubLevel> subLevelsById = new LinkedHashMap<>();
        subLevels.forEach(subLevel -> subLevelsById.put(subLevel.getUniqueId(), subLevel));
        Set<PropulsionUnitKey> knownUnits = new LinkedHashSet<>();
        for (CalibrationUnit unit : calibrationUnits) {
            knownUnits.add(new PropulsionUnitKey(
                    unit.subLevelId, unit.blockPosition, unit.blockId, unit.actuator.kind()));
        }
        Level ownerLevel = controller.getLevel();
        for (ScmConfigurationProfile.UnitReference reference : references) {
            SubLevel subLevel = subLevelsById.get(reference.subLevelId());
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    ownerLevel, reference.subLevelId(), reference.blockPosition());
            if (blockEntity == null) {
                blockEntity = SubLevelBlockEntityCollector.getBlockEntity(
                        subLevel, reference.blockPosition());
            }
            BlockState state = blockEntity == null
                    ? subLevel.getLevel().getBlockState(reference.blockPosition())
                    : blockEntity.getBlockState();
            if (state.isAir()) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null) {
                continue;
            }
            for (Actuator actuator : configuredActuatorsFor(
                    reference, subLevel, blockEntity, root)) {
                PropulsionUnitKey key = new PropulsionUnitKey(
                        subLevel.getUniqueId(), reference.blockPosition(),
                        blockId.toString(), actuator.kind());
                if (!knownUnits.add(key)) {
                    continue;
                }
                calibrationUnits.add(new CalibrationUnit(
                        subLevel.getUniqueId(), reference.blockPosition(), blockId.toString(),
                        rootPosition(root, subLevel, actuator.localForcePosition()),
                        rootDirection(root, subLevel, actuator.localForceDirection()), actuator,
                        reusableUnits.get(key), false, reference.usesFaceControl()));
            }
        }
    }

    // Get the live SCM targets
    private List<ScmTarget> liveScmTargets(Map<UUID, SubLevel> subLevelsById) {
        return ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker()).stream().map(target -> {
            SubLevel subLevel = subLevelsById.get(target.subLevelId());
            if (subLevel == null) {
                return target;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), target.subLevelId(), target.blockPosition());
            BlockState state = blockEntity == null
                    ? subLevel.getLevel().getBlockState(target.blockPosition())
                    : blockEntity.getBlockState();
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            return new ScmTarget(target.subLevelId(), target.blockPosition(),
                    id == null ? target.blockId() : id.toString(),
                    target.label().isBlank() ? state.getBlock().getName().getString() : target.label(),
                    target.signalPosition(), target.signalFace(), target.controlChannelId());
        }).toList();
    }

    // Get the linked SCM probe configs
    private List<LinkedScmProbe> linkedScmProbeConfigs(
            Level ownerLevel,
            List<ScmTarget> targets,
            Map<UUID, ? extends SubLevel> subLevelsById,
            @Nullable ServerSubLevel directionFrame,
            Vec3 suggestedDir,
            boolean combineTargets
    ) {
        List<List<LinkedScmProbe>> optionsByTarget = new ArrayList<>();
        for (ScmTarget target : targets.stream()
                .sorted(Comparator.comparing(ScmTarget::stableId)).toList()) {
            SubLevel subLevel = subLevelsById.get(target.subLevelId());
            if (subLevel == null) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    ownerLevel, target.subLevelId(), target.blockPosition());
            if (blockEntity != null && (isThrusterProvider(blockEntity)
                    || !bearingActuatorsFor(blockEntity).isEmpty())) {
                continue;
            }
            Vec3 targetSuggestedDir = directionFrame == null || directionFrame == subLevel
                    ? suggestedDir
                    : transformDirectionBetween(
                    subLevel.logicalPose(), directionFrame.logicalPose(), suggestedDir);
            List<LinkedScmProbe> opts = ScmControlProbeRegistry.create(
                            subLevel.getLevel(), blockEntity, target,
                            targetSuggestedDir, targets).stream()
                    .map(probe -> new LinkedScmProbe(
                            subLevel, subLevel.getLevel(), target, blockEntity, probe))
                    .toList();
            if (!opts.isEmpty()) {
                optionsByTarget.add(opts);
            }
        }
        if (!combineTargets) {
            return optionsByTarget.stream().flatMap(Collection::stream).toList();
        }
        if (optionsByTarget.isEmpty()) {
            return List.of();
        }
        List<LinkedScmProbe> configs = new ArrayList<>();
        Set<String> configIds = new LinkedHashSet<>();
        List<List<LinkedScmProbe>> kineticComponents =
                kineticProbeComponents(optionsByTarget);
        for (List<LinkedScmProbe> opts : optionsByTarget) {
            String groupId = "scm_target:" + opts.getFirst().target().stableId();
            for (LinkedScmProbe option : opts) {
                List<LinkedScmProbe> domain = kineticComponents.stream()
                        .filter(component -> component.stream().anyMatch(member ->
                                member.target().stableId().equals(
                                        option.target().stableId())))
                        .findFirst().orElse(opts);
                addLinkedScmConfig(configs, configIds,
                        List.of(option), domain, groupId);
            }
        }
        for (List<LinkedScmProbe> component : kineticComponents) {
            addKineticStageConfigs(
                    configs, configIds, component);
        }
        return List.copyOf(configs);
    }

    // Add the linked SCM config
    private static void addLinkedScmConfig(
            List<LinkedScmProbe> res,
            Set<String> configIds,
            List<LinkedScmProbe> selected,
            List<LinkedScmProbe> domain,
            String groupId
    ) {
        if (selected.isEmpty()) {
            return;
        }
        String signature = groupId + ':' + selected.stream()
                .map(member -> member.target().stableId() + '=' + member.probe().adapterId())
                .sorted().reduce((left, right) -> left + '|' + right).orElse("");
        if (!configIds.add(signature)) {
            return;
        }
        LinkedScmProbe anchor = selected.getFirst();
        ScmControlProbe config = new CompositeScmProbe(
                selected, domain, groupId);
        res.add(new LinkedScmProbe(
                anchor.subLevel(), anchor.level(), anchor.target(),
                anchor.blockEntity(), config));
    }

    // Get the kinetic probe components
    private static List<List<LinkedScmProbe>> kineticProbeComponents(
            List<List<LinkedScmProbe>> optionsByTarget
    ) {
        Map<KineticBlockEntity, List<LinkedScmProbe>> probesByKinetic =
                new IdentityHashMap<>();
        for (LinkedScmProbe probe : optionsByTarget.stream().flatMap(Collection::stream).toList()) {
            if (!(probe.blockEntity() instanceof KineticBlockEntity kinetic)
                    || probe.probe().adapterId().startsWith("wheel_mount_")) {
                continue;
            }
            probesByKinetic.computeIfAbsent(kinetic, ignored -> new ArrayList<>()).add(probe);
        }
        Set<KineticBlockEntity> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<List<LinkedScmProbe>> components = new ArrayList<>();
        for (KineticBlockEntity start : probesByKinetic.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            List<LinkedScmProbe> component = new ArrayList<>();
            ArrayDeque<KineticBlockEntity> frontier = new ArrayDeque<>();
            frontier.addLast(start);
            while (!frontier.isEmpty()) {
                KineticBlockEntity current = frontier.removeFirst();
                component.addAll(probesByKinetic.getOrDefault(current, List.of()));
                for (KineticBlockEntity neighbour : KineticGraphHelper.getConnectedNeighbours(current)) {
                    if (neighbour != null && neighbour.getLevel() == current.getLevel()
                            && visited.add(neighbour)) {
                        frontier.addLast(neighbour);
                    }
                }
            }
            if (!component.isEmpty()) {
                components.add(component);
            }
        }
        return List.copyOf(components);
    }

    // Add the kinetic stage configs
    private static void addKineticStageConfigs(
            List<LinkedScmProbe> res,
            Set<String> configIds,
            List<LinkedScmProbe> component
    ) {
        Map<String, List<LinkedScmProbe>> optionsByTarget = new LinkedHashMap<>();
        for (LinkedScmProbe probe : component) {
            optionsByTarget.computeIfAbsent(probe.target().stableId(), ignored -> new ArrayList<>())
                    .add(probe);
        }
        Map<BlockPos, Integer> distances = kineticDistances(component);
        List<List<LinkedScmProbe>> orderedTargets = new ArrayList<>(optionsByTarget.values());
        orderedTargets.forEach(opts -> opts.sort(Comparator.comparing(
                option -> option.probe().adapterId())));
        orderedTargets.sort(Comparator
                .comparingInt((List<LinkedScmProbe> opts) -> distances.getOrDefault(
                        opts.getFirst().target().blockPosition(), Integer.MAX_VALUE))
                .thenComparing(opts -> opts.getFirst().target().stableId()));
        List<LinkedScmProbe> domain = orderedTargets.stream()
                .flatMap(Collection::stream).toList();
        String groupSeed = domain.stream().map(option -> option.target().stableId())
                .distinct().sorted().reduce((left, right) -> left + '|' + right)
                .orElse("kinetic_controls");
        String groupId = "scm_kinetic:" + UUID.nameUUIDFromBytes(
                groupSeed.getBytes(StandardCharsets.UTF_8));
        List<LinkedScmProbe> primary = orderedTargets.stream()
                .map(List::getFirst).toList();

        if (primary.size() < 2) {
            return;
        }

        for (int size = 2; size <= primary.size(); size++) {
            addLinkedScmConfig(res, configIds,
                    primary.subList(0, size), domain, groupId);
        }
        for (int targetIdx = 0; targetIdx < orderedTargets.size(); targetIdx++) {
            List<LinkedScmProbe> opts = orderedTargets.get(targetIdx);
            for (int optionIdx = 1; optionIdx < opts.size(); optionIdx++) {
                List<LinkedScmProbe> variant = new ArrayList<>(primary);
                variant.set(targetIdx, opts.get(optionIdx));
                addLinkedScmConfig(res, configIds,
                        variant, domain, groupId);
            }
        }
    }

    // Get the kinetic distances
    private static Map<BlockPos, Integer> kineticDistances(
            List<LinkedScmProbe> component
    ) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        ArrayDeque<KineticBlockEntity> frontier = new ArrayDeque<>();
        for (LinkedScmProbe probe : component) {
            if (!(probe.blockEntity() instanceof KineticBlockEntity kinetic)) {
                continue;
            }
            if (kinetic.source == null && distances.putIfAbsent(
                    kinetic.getBlockPos(), 0) == null) {
                frontier.addLast(kinetic);
                continue;
            }
            BlockEntity src = probe.level().getBlockEntity(kinetic.source);
            if (src instanceof KineticBlockEntity sourceKinetic
                    && distances.putIfAbsent(sourceKinetic.getBlockPos(), 0) == null) {
                frontier.addLast(sourceKinetic);
            }
        }
        if (frontier.isEmpty()) {
            component.stream()
                    .map(LinkedScmProbe::blockEntity)
                    .filter(KineticBlockEntity.class::isInstance)
                    .map(KineticBlockEntity.class::cast)
                    .min(Comparator.comparing(kinetic -> kinetic.getBlockPos()))
                    .ifPresent(kinetic -> {
                        distances.put(kinetic.getBlockPos(), 0);
                        frontier.addLast(kinetic);
                    });
        }
        while (!frontier.isEmpty() && distances.size() < 256) {
            KineticBlockEntity current = frontier.removeFirst();
            int distance = distances.getOrDefault(current.getBlockPos(), 0);
            for (KineticBlockEntity neighbour : KineticGraphHelper.getConnectedNeighbours(current)) {
                if (neighbour == null || distances.putIfAbsent(
                        neighbour.getBlockPos(), distance + 1) != null) {
                    continue;
                }
                frontier.addLast(neighbour);
            }
        }
        return distances;
    }

    // Get the suggested SCM travel dir
    private Vec3 suggestedScmTravelDir(
            ServerSubLevel root,
            List<ScmTarget> targets,
            Map<UUID, SubLevel> subLevelsById
    ) {
        Vec3 combined = Vec3.ZERO;
        for (ScmTarget target : targets) {
            if (!target.blockId().toLowerCase(Locale.ROOT).contains("wheel_mount")) {
                continue;
            }
            SubLevel subLevel = subLevelsById.get(target.subLevelId());
            if (subLevel == null) {
                continue;
            }
            combined = combined.add(rootDirection(
                    root, subLevel, wheelTravelDirection(
                            subLevel.getLevel().getBlockState(target.blockPosition()))));
        }
        if (combined.lengthSqr() > 1.0E-9D) {
            Vec3 dir = combined.normalize();
            Vec3 controllerForward = normalize(
                    new Vec3(controllerForwardRoot().x, 0.0D, controllerForwardRoot().z),
                    dir);
            return dir.dot(controllerForward) < 0.0D
                    ? dir.scale(-1.0D) : dir;
        }
        return controllerForwardRoot();
    }

    // Get the wheel travel direction
    private static Vec3 wheelTravelDirection(BlockState state) {
        if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction.Axis axle = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
            return axle == Direction.Axis.X
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
        }
        if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            Direction.Axis axle = state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            return axle == Direction.Axis.X
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
        }
        return Vec3.ZERO;
    }

    // Get the V2 probe strength
    private static double v2ProbeStrength(CalibrationUnit unit) {
        return unit.samples.stream()
                .filter(sample -> v2ProbeEnvelopeMatches(sample, unit.actuator))
                .min(Comparator.comparingDouble(sample -> Math.abs(
                        sample.control() - V2_MATRIX_PROBE_THROTTLE)))
                .map(sample -> Math.abs(sample.thrust()))
                .orElse(0.0D);
    }

    // Get the V2 response family key
    private String v2ResponseFamilyKey(CalibrationUnit unit, Vec3 centerOfMass) {
        Vec3 lever = unit.rootPosition.subtract(centerOfMass);
        Vec3 torque = lever.cross(unit.forceDirection);
        ShipControlMap.CalibrationSample probe = unit.samples.stream()
                .filter(sample -> v2ProbeEnvelopeMatches(sample, unit.actuator))
                .min(Comparator.comparingDouble(sample -> Math.abs(
                        sample.control() - V2_MATRIX_PROBE_THROTTLE)))
                .orElse(null);
        double theoreticalThrust = Math.abs(unit.actuator.theoreticalMaxThrust());
        double probeResponse = probe == null
                ? 0.0D : v2ProbeResponse(probe.thrust(), theoreticalThrust);
        return unit.blockId + '|' + unit.actuator.kind() + '|'
                + unit.actuator.controllable() + '|'
                + roundedControl(unit.actuator.minControl()) + '|'
                + roundedControl(unit.actuator.maxControl()) + '|'
                + roundedResponse(theoreticalThrust) + '|'
                + roundedResponse(probeResponse) + '|'
                + vectorOctant(unit.forceDirection) + '|'
                + canonicalInversionOctant(torque);
    }

    // Get the V2 probe response
    static double v2ProbeResponse(double measuredThrust, double theoreticalThrust) {
        double measured = Math.abs(finite(measuredThrust));
        double theoretical = Math.abs(finite(theoreticalThrust));
        return theoretical > 1.0E-9D ? measured / theoretical : measured;
    }

    // Get the rounded control
    private static String roundedControl(double val) {
        return Long.toString(Math.round(Mth.clamp(finite(val), 0.0D, 1.0D) * 1000.0D));
    }

    // Get the vector octant
    private static String vectorOctant(Vec3 vector) {
        Vec3 normalized = normalize(vector, Vec3.ZERO);
        return componentBand(normalized.x) + ":" + componentBand(normalized.y)
                + ":" + componentBand(normalized.z);
    }

    // Get the canonical inversion octant
    static String canonicalInversionOctant(Vec3 vector) {
        String direct = vectorOctant(vector);
        String inverted = vectorOctant(vector.scale(-1.0D));
        return direct.compareTo(inverted) <= 0 ? direct : inverted;
    }

    // Get the rounded response
    private static String roundedResponse(double val) {
        return Long.toString(Math.round(Mth.clamp(
                Math.abs(finite(val)), 0.0D, 1.0E9D) * 1000.0D));
    }

    // Get the component band
    private static int componentBand(double val) {
        return val > 0.2D ? 1 : val < -0.2D ? -1 : 0;
    }

    // Get the V2 probe point
    private static CalibrationPoint v2ProbePoint(Actuator actuator) {
        return new CalibrationPoint(actuator.minControl(), actuator.maxControl(),
                V2_MATRIX_PROBE_THROTTLE);
    }

    // Get the V2 representative sweep
    private static List<CalibrationPoint> v2RepresentativeSweep(Actuator actuator) {
        if (actuator.supportsControlEnvelope()) {
            return v2EnvelopeSweep();
        }
        List<CalibrationPoint> points = new ArrayList<>(V2_THROTTLE_STEPS.length);
        for (double throttle : V2_THROTTLE_STEPS) {
            points.add(new CalibrationPoint(
                    actuator.minControl(), actuator.maxControl(), throttle));
        }
        return List.copyOf(points);
    }

    // Get the V2 envelope sweep
    private static List<CalibrationPoint> v2EnvelopeSweep() {
        List<CalibrationPoint> points = new ArrayList<>(
                V2_ENVELOPE_EFFECTIVE_STEPS.length);
        for (double effectiveControl : V2_ENVELOPE_EFFECTIVE_STEPS) {
            double modulation = envelopeModulation(effectiveControl);
            points.add(new CalibrationPoint(0.0D, modulation, modulation));
        }
        return List.copyOf(points);
    }

    // Check if the V2 probe envelope matches the actuator
    private static boolean v2ProbeEnvelopeMatches(
            ShipControlMap.CalibrationSample sample,
            Actuator actuator
    ) {
        return nearlyEqual(sample.minControl(), actuator.minControl())
                && nearlyEqual(sample.maxControl(), actuator.maxControl());
    }

    // Get the V2 bearing poses
    private static List<ShipBearingPlanner.Pose> v2BearingPoses(BearingActuator actuator) {
        if ("vector_bearing".equals(actuator.kind())) {
            return ShipBearingPlanner.sampledVectorPoses(
                    Math.max(Math.abs(actuator.minX()), Math.abs(actuator.maxX())),
                    V2_BEARING_SAMPLES_PER_AXIS);
        }
        if (Math.abs(actuator.maxZ() - actuator.minZ()) > 1.0E-6D) {
            return ShipBearingPlanner.sampledIndependentAxisPoses(
                    actuator.minX(), actuator.maxX(), actuator.minZ(), actuator.maxZ(),
                    V2_BEARING_SAMPLES_PER_AXIS);
        }
        return ShipBearingPlanner.sampledSingleAxisPoses(
                actuator.minX(), actuator.maxX(), V2_BEARING_SAMPLES_PER_AXIS);
    }

    // Finalize the V2 representative samples
    private void finalizeV2RepresentativeSamples() {
        if (!initializationV2 || v2SamplesFinalized) {
            return;
        }
        v2SamplesFinalized = true;
        for (int idx = 0; idx < calibrationUnits.size(); idx++) {
            CalibrationUnit unit = calibrationUnits.get(idx);
            if (unit.v2Representative || unit.v2RepresentativeIndex < 0
                    || unit.v2RepresentativeIndex >= calibrationUnits.size()) {
                continue;
            }
            CalibrationUnit representative = calibrationUnits.get(
                    unit.v2RepresentativeIndex);
            if (representative.samples.isEmpty()) {
                continue;
            }
            ShipControlMap.CalibrationSample unitProbe = unit.samples.stream()
                    .filter(sample -> v2ProbeEnvelopeMatches(sample, unit.actuator))
                    .min(Comparator.comparingDouble(sample -> Math.abs(
                            sample.control() - V2_MATRIX_PROBE_THROTTLE)))
                    .orElse(null);
            ShipControlMap.CalibrationSample representativeProbe = representative.samples.stream()
                    .filter(sample -> v2ProbeEnvelopeMatches(
                            sample, representative.actuator))
                    .min(Comparator.comparingDouble(sample -> Math.abs(
                            sample.control() - V2_MATRIX_PROBE_THROTTLE)))
                    .orElse(null);
            double thrustScale = signedSampleScale(
                    unitProbe == null ? 0.0D : unitProbe.thrust(),
                    representativeProbe == null ? 0.0D : representativeProbe.thrust(),
                    unit.actuator.expectedThrustSign()
                            * unit.actuator.theoreticalMaxThrust(),
                    representative.actuator.expectedThrustSign()
                            * representative.actuator.theoreticalMaxThrust());
            double speedScale = sampleScale(
                    unitProbe == null ? 0.0D : unitProbe.speed(),
                    representativeProbe == null ? 0.0D : representativeProbe.speed(),
                    1.0D, 1.0D);
            List<ShipControlMap.CalibrationSample> adapted = new ArrayList<>();
            for (ShipControlMap.CalibrationSample sample : representative.samples) {
                if (unitProbe != null
                        && v2ProbeEnvelopeMatches(sample, representative.actuator)
                        && Math.abs(sample.control()
                        - V2_MATRIX_PROBE_THROTTLE) <= 1.0E-6D) {
                    adapted.add(unitProbe);
                    continue;
                }
                adapted.add(new ShipControlMap.CalibrationSample(
                        sample.minControl(), sample.maxControl(), sample.control(),
                        sample.speed() * speedScale, sample.thrust() * thrustScale,
                        sample.active() && Math.abs(thrustScale) > 1.0E-6D));
            }
            unit.samples.clear();
            unit.samples.addAll(adapted);
        }
    }

    // Sample the scale
    private static double sampleScale(
            double observed, double representative, double theoretical,
            double representativeTheoretical
    ) {
        double denominator = Math.abs(representative);
        if (denominator > 1.0E-9D) {
            return Mth.clamp(Math.abs(observed) / denominator, 0.0D, 64.0D);
        }
        double theoreticalDenominator = Math.abs(representativeTheoretical);
        if (theoreticalDenominator > 1.0E-9D) {
            return Mth.clamp(Math.abs(theoretical) / theoreticalDenominator, 0.0D, 64.0D);
        }
        return 1.0D;
    }

    // Get the signed sample scale
    static double signedSampleScale(
            double observed, double representative, double theoretical,
            double representativeTheoretical
    ) {
        double theoreticalDenominator = Math.abs(finite(representativeTheoretical));
        double magnitude;
        if (Math.abs(finite(theoretical)) > 1.0E-9D
                && theoreticalDenominator > 1.0E-9D) {
            magnitude = Math.abs(theoretical) / theoreticalDenominator;
        } else {
            double representativeMagnitude = Math.abs(finite(representative));
            if (representativeMagnitude <= 1.0E-9D) {
                magnitude = 1.0D;
            } else if (Math.abs(finite(observed)) <= 1.0E-9D) {
                magnitude = 0.0D;
            } else {
                magnitude = Math.abs(observed) / representativeMagnitude;
            }
        }
        magnitude = Mth.clamp(magnitude, 0.0D, 4.0D);
        double observedSign = nonZeroSign(observed, theoretical);
        double representativeSign = nonZeroSign(
                representative, representativeTheoretical);
        return magnitude * observedSign * representativeSign;
    }

    // Get the non zero sign
    private static double nonZeroSign(double measured, double fallback) {
        double sign = Math.signum(finite(measured));
        if (sign == 0.0D) {
            sign = Math.signum(finite(fallback));
        }
        return sign == 0.0D ? 1.0D : sign;
    }

    // Get the reusable propulsion units
    private static Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusablePropulsionUnits(
            @Nullable ShipControlMap prev
    ) {
        if (prev == null) {
            return Map.of();
        }
        Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> units = new LinkedHashMap<>();
        for (ShipControlMap.PropulsionUnit unit : prev.units()) {
            units.putIfAbsent(PropulsionUnitKey.of(unit), unit);
        }
        return units;
    }

    // Discover the CRN displays
    private void discoverCrnDisplays(List<SubLevel> subLevels) {
        for (SubLevel subLevel : subLevels) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (RailwayNavigatorGraphCompat.isAdvancedDisplayController(blockEntity)) {
                    calibrationCrnDisplays.add(new ShipControlMap.CrnDisplay(
                            subLevel.getUniqueId(), blockEntity.getBlockPos()));
                }
                if (blockEntity instanceof AccDisplayBlockEntity display
                        && display.isNetworkRoot()) {
                    calibrationAccDisplays.add(new ShipControlMap.AccDisplay(
                            subLevel.getUniqueId(), display.getBlockPos()));
                }
            }
        }
        List<ShipControlMap.CrnDisplay> uniqueDisplays = calibrationCrnDisplays.stream()
                .distinct()
                .sorted(Comparator
                        .comparing((ShipControlMap.CrnDisplay display) -> display.subLevelId().toString())
                        .thenComparing(ShipControlMap.CrnDisplay::blockPosition))
                .toList();
        calibrationCrnDisplays.clear();
        calibrationCrnDisplays.addAll(uniqueDisplays);
        List<ShipControlMap.AccDisplay> uniqueAccDisplays = calibrationAccDisplays.stream()
                .distinct()
                .sorted(Comparator
                        .comparing((ShipControlMap.AccDisplay display) -> display.subLevelId().toString())
                        .thenComparing(ShipControlMap.AccDisplay::blockPosition))
                .toList();
        calibrationAccDisplays.clear();
        calibrationAccDisplays.addAll(uniqueAccDisplays);
    }

    // Get the bearing child sub levels
    private static Set<UUID> bearingChildSubLevels(List<SubLevel> subLevels) {
        Set<UUID> children = new HashSet<>();
        Map<UUID, List<UUID>> hierarchy = new LinkedHashMap<>();
        for (SubLevel subLevel : subLevels) {
            for (BlockEntity blockEntity
                    : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                for (BearingActuator actuator : bearingActuatorsFor(blockEntity)) {
                    if (actuator.childSubLevelIds().isEmpty()) {
                        continue;
                    }
                    children.addAll(actuator.childSubLevelIds());
                    hierarchy.computeIfAbsent(
                                    subLevel.getUniqueId(), ignored -> new ArrayList<>())
                            .addAll(actuator.childSubLevelIds());
                }
            }
        }
        hierarchy.replaceAll((ignored, values) -> values.stream().distinct().toList());
        return ShipBearingPlanner.descendantSubLevels(children, hierarchy);
    }

    // Find the aero surfaces
    private List<AerodynamicSurfaceCalibration> findAeroSurfaces(
            List<SubLevel> subLevels
    ) {
        List<AerodynamicSurfaceCalibration> surfaces = new ArrayList<>();
        for (SubLevel subLevel : subLevels) {
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
                continue;
            }
            for (BlockSubLevelLiftProvider.LiftProviderContext ctx
                    : serverSubLevel.getPlot().getLiftProviders()) {
                if (!(ctx.state().getBlock() instanceof BlockSubLevelLiftProvider provider)) {
                    continue;
                }
                ResourceLocation blockId =
                        BuiltInRegistries.BLOCK.getKey(ctx.state().getBlock());
                surfaces.add(new AerodynamicSurfaceCalibration(
                        serverSubLevel.getUniqueId(), ctx.pos(), blockId.toString(),
                        ctx.dir(), provider.sable$getParallelDragScalar(),
                        provider.sable$getDirectionlessDragScalar(),
                        provider.sable$getLiftScalar()));
            }
        }
        surfaces.sort(Comparator
                .comparing((AerodynamicSurfaceCalibration surface) ->
                        surface.subLevelId.toString())
                .thenComparing(surface -> surface.blockPosition)
                .thenComparing(surface -> surface.blockId));
        return List.copyOf(surfaces);
    }

    // Find the cal bearings
    private void findCalBearings(
            List<SubLevel> subLevels,
            List<AerodynamicSurfaceCalibration> aerodynamicSurfaces
    ) {
        calibrationBearings.clear();
        Map<BearingUnitKey, ShipControlMap.BearingUnit> reusableBearings =
                reusableBearingUnits(reconciliationBaseMap);
        Map<Integer, ShipControlMap.PropulsionUnit> previousUnitsByIndex =
                propulsionUnitsByIndex(reconciliationBaseMap);
        List<BearingCandidate> candidates = new ArrayList<>();
        for (SubLevel subLevel : subLevels) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
                for (BearingActuator actuator : bearingActuatorsFor(blockEntity)) {
                    List<UUID> children = actuator.childSubLevelIds();
                    if (children.isEmpty() && !actuator.directlyControlsPropulsion()) {
                        continue;
                    }
                    candidates.add(new BearingCandidate(
                            subLevel.getUniqueId(), blockEntity.getBlockPos(),
                            blockId.toString(), actuator, children));
                }
            }
        }

        Map<UUID, List<UUID>> hierarchy = new LinkedHashMap<>();
        for (BearingCandidate candidate : candidates) {
            hierarchy.computeIfAbsent(candidate.hostSubLevelId, ignored -> new ArrayList<>())
                    .addAll(candidate.childSubLevelIds);
        }
        hierarchy.replaceAll((ignored, children) -> children.stream().distinct().toList());

        for (BearingCandidate candidate : candidates) {
            Set<UUID> descendants = ShipBearingPlanner.descendantSubLevels(
                    candidate.childSubLevelIds, hierarchy);
            List<Integer> memberUnitIndices = new ArrayList<>();
            if (candidate.actuator.directlyControlsPropulsion()) {
                for (int unitIndex = 0; unitIndex < calibrationUnits.size(); unitIndex++) {
                    CalibrationUnit unit = calibrationUnits.get(unitIndex);
                    if (candidate.hostSubLevelId.equals(unit.subLevelId)
                            && candidate.blockPosition.equals(unit.blockPosition)) {
                        memberUnitIndices.add(unitIndex);
                    }
                }
            } else {
                for (int unitIndex = 0; unitIndex < calibrationUnits.size(); unitIndex++) {
                    if (descendants.contains(calibrationUnits.get(unitIndex).subLevelId)) {
                        memberUnitIndices.add(unitIndex);
                    }
                }
            }
            List<AerodynamicSurfaceCalibration> memberSurfaces = aerodynamicSurfaces.stream()
                    .filter(surface -> descendants.contains(surface.subLevelId))
                    .toList();
            List<ShipBearingPlanner.Pose> outputPoses = candidate.actuator.poses();
            List<ShipBearingPlanner.Pose> poses = initializationV2
                    ? v2BearingPoses(candidate.actuator) : outputPoses;
            if ((!memberUnitIndices.isEmpty() || !memberSurfaces.isEmpty()) && !poses.isEmpty()) {
                ShipControlMap.BearingUnit prev = reusableBearings.get(
                        new BearingUnitKey(candidate.hostSubLevelId, candidate.blockPosition,
                                candidate.blockId, candidate.actuator.kind()));
                BearingReuse reuse = reusableBearing(
                        prev, candidate, memberUnitIndices, memberSurfaces, poses,
                        previousUnitsByIndex, calibrationUnits);
                calibrationBearings.add(new BearingCalibrationUnit(
                        candidate.hostSubLevelId, candidate.blockPosition, candidate.blockId,
                        candidate.actuator, candidate.childSubLevelIds, memberUnitIndices,
                        memberSurfaces, poses, outputPoses, initializationV2, reuse));
            }
        }
        calibrationBearings.sort(Comparator
                .comparingInt((BearingCalibrationUnit bearing) ->
                        bearing.memberUnitIndices.size() + bearing.aerodynamicSurfaces.size())
                .reversed()
                .thenComparing(bearing -> bearing.hostSubLevelId.toString())
                .thenComparing(bearing -> bearing.blockPosition)
                .thenComparing(bearing -> bearing.actuator.kind()));
    }

    // Get the reusable bearing units
    private static Map<BearingUnitKey, ShipControlMap.BearingUnit> reusableBearingUnits(
            @Nullable ShipControlMap prev
    ) {
        if (prev == null) {
            return Map.of();
        }
        Map<BearingUnitKey, ShipControlMap.BearingUnit> bearings = new LinkedHashMap<>();
        for (ShipControlMap.BearingUnit bearing : prev.bearings()) {
            bearings.putIfAbsent(BearingUnitKey.of(bearing), bearing);
        }
        return bearings;
    }

    // Get the propulsion units by index
    private static Map<Integer, ShipControlMap.PropulsionUnit> propulsionUnitsByIndex(
            @Nullable ShipControlMap prev
    ) {
        if (prev == null) {
            return Map.of();
        }
        Map<Integer, ShipControlMap.PropulsionUnit> units = new LinkedHashMap<>();
        prev.units().forEach(unit -> units.put(unit.index(), unit));
        return units;
    }

    // Get the reusable bearing
    private static @Nullable BearingReuse reusableBearing(
            @Nullable ShipControlMap.BearingUnit prev,
            BearingCandidate candidate,
            List<Integer> memberUnitIndices,
            List<AerodynamicSurfaceCalibration> surfaces,
            List<ShipBearingPlanner.Pose> poses,
            Map<Integer, ShipControlMap.PropulsionUnit> previousUnitsByIndex,
            List<CalibrationUnit> currentUnits
    ) {
        if (prev == null
                || !new HashSet<>(prev.childSubLevelIds())
                .equals(new HashSet<>(candidate.childSubLevelIds))
                || !nearlyEqual(prev.minX(), candidate.actuator.minX())
                || !nearlyEqual(prev.maxX(), candidate.actuator.maxX())
                || !nearlyEqual(prev.minZ(), candidate.actuator.minZ())
                || !nearlyEqual(prev.maxZ(), candidate.actuator.maxZ())
                || prev.poses().size() != poses.size()) {
            return null;
        }
        for (int idx = 0; idx < poses.size(); idx++) {
            ShipControlMap.BearingPose stored = prev.poses().get(idx);
            ShipBearingPlanner.Pose requested = poses.get(idx);
            if (!nearlyEqual(stored.angleX(), requested.angleX())
                    || !nearlyEqual(stored.angleZ(), requested.angleZ())) {
                return null;
            }
        }

        Map<PropulsionUnitKey, Integer> currentIndices = new LinkedHashMap<>();
        for (int memberIndex : memberUnitIndices) {
            currentIndices.put(currentUnits.get(memberIndex).key(), memberIndex);
        }
        Map<Integer, Integer> indexRemap = new LinkedHashMap<>();
        for (int oldIndex : prev.propulsionUnitIndices()) {
            ShipControlMap.PropulsionUnit oldUnit = previousUnitsByIndex.get(oldIndex);
            Integer currentIndex = oldUnit == null
                    ? null : currentIndices.get(PropulsionUnitKey.of(oldUnit));
            if (currentIndex == null) {
                return null;
            }
            indexRemap.put(oldIndex, currentIndex);
        }
        if (indexRemap.size() != currentIndices.size()) {
            return null;
        }

        Set<AerodynamicSurfaceKey> previousSurfaces = new HashSet<>();
        prev.poses().forEach(pose -> pose.aerodynamicSurfaces().forEach(surface ->
                previousSurfaces.add(AerodynamicSurfaceKey.of(surface))));
        Set<AerodynamicSurfaceKey> currentSurfaces = new HashSet<>();
        surfaces.forEach(surface -> currentSurfaces.add(AerodynamicSurfaceKey.of(surface)));
        if (!previousSurfaces.equals(currentSurfaces)) {
            return null;
        }
        return new BearingReuse(prev, Map.copyOf(indexRemap));
    }

    // Reset the bearing cal
    private void resetBearingCal() {
        bearingCalibrationIndex = 0;
        bearingPoseIndex = 0;
        bearingThrottleIndex = 0;
        bearingPoseWaitTicks = 0;
        bearingPoseApplied = false;
        bearingThrottleApplied = false;
    }

    // Update the calibration
    private void tickCalibration() {
        // ------------------------------------CALIBRATION QUEUE------------------------------------

        if (!isControlMode(ScmBuiltinControlModes.CAR_ID)
                && !scmWheelCalibrationComplete && tickScmWheelCalibration()) {
            return;
        }
        while (calibrationUnitIndex < calibrationUnits.size()
                && (!calibrationUnits.get(calibrationUnitIndex).calibrationRequired
                || calibrationUnits.get(calibrationUnitIndex).calibrationPoints.isEmpty())) {
            calibrationUnitIndex++;
            calibrationSampleIndex = 0;
        }
        if (calibrationUnitIndex >= calibrationUnits.size()) {
            if (initializationV2 && !v2RepresentativeSweepStarted) {
                selectV2RepresentativeUnits();
                v2RepresentativeSweepStarted = true;
                calibrationUnitIndex = 0;
                calibrationSampleIndex = 0;
                sampleSettleTicks = 0;
                resetCalSamples();
                updateCalProgress();
                return;
            }
            finalizeV2RepresentativeSamples();
            if (isControlMode(ScmBuiltinControlModes.CAR_ID)
                    && carWheelFirstCalibrationIndex >= 0
                    && !carWheelCalibrationStarted) {
                carWheelCalibrationPending = true;
                releaseNestedFreezeHandles();
                tickBearingCalibration();
                return;
            }
            if (isControlMode(ScmBuiltinControlModes.CAR_ID)
                    && carWheelCalibrationStarted) {
                restoreCarWheelDrive();
                releaseNestedFreezeHandles();
                phase = Phase.SAVING;
                status = "Saving control map";
                progress = 0.93D;
                return;
            }
            releaseNestedFreezeHandles();
            tickBearingCalibration();
            return;
        }

        CalibrationUnit unit = calibrationUnits.get(calibrationUnitIndex);
        List<CalibrationPoint> points = unit.calibrationPoints;
        CalibrationPoint point = points.get(calibrationSampleIndex);
        if (!sampleApplied) {
            if (unit.actuator.usesPhysicalCalibration() && !sampleNeutralized) {
                unit.actuator.neutralize();
                activateCarWheelDrive(unit);
                sampleNeutralized = true;
                sampleSettleTicks = 1;
                updateCalProgress();
                return;
            }
            if (unit.actuator.usesPhysicalCalibration() && sampleSettleTicks-- > 0) {
                return;
            }
            if (unit.actuator.usesPhysicalCalibration() && !sampleNeutralWindowStarted) {
                sampleBaselineImpulses = captureConstraintImpulses();
                sampleBaselineWheelContacts = captureWheelContactSamples();
                sampleNeutralWindowStarted = true;
                sampleSettleTicks = 1;
                return;
            }
            if (unit.actuator.usesPhysicalCalibration()) {
                Vec3 centerOfMass = assemblyCenterOfMass(
                        requireRootSubLevel(), controller.getBlockPos().getCenter());
                sampleNeutralConstraintResponse =
                        constraintResponseSince(sampleBaselineImpulses);
                sampleNeutralWheelResponse = wheelContactResponseSince(
                        sampleBaselineWheelContacts, centerOfMass);
                sampleBaselineImpulses = captureConstraintImpulses();
                sampleBaselineWheelContacts = captureWheelContactSamples();
            }
            applyCalibrationSample(unit, point);
            sampleApplied = true;
            sampleSettleTicks = 1;
            updateCalProgress();
            return;
        }
        if (sampleSettleTicks-- > 0) {
            return;
        }

        // -----------------------------------------------------RESPONSE SAMPLING-------------------------------------------------

        Reading reading = unit.actuator.read();
        if (unit.actuator.usesPhysicalCalibration()) {
            Vec3 centerOfMass = assemblyCenterOfMass(
                    requireRootSubLevel(), controller.getBlockPos().getCenter());
            ConstraintResponseSample constraintSample =
                    constraintResponseSince(sampleBaselineImpulses);
            ConstraintResponse constraintResponse = subtractResponse(
                    constraintSample.response(),
                    sampleNeutralConstraintResponse.response());
            WheelContactResponse wheelResponse = wheelContactResponseSince(
                    sampleBaselineWheelContacts, centerOfMass);
            ConstraintResponse resp = constraintSample.samples() > 0L
                    && sampleNeutralConstraintResponse.samples() > 0L
                    ? constraintResponse
                    : subtractResponse(
                            wheelResponse.response(), sampleNeutralWheelResponse.response());
            reading = unit.recordPhysicalResponse(reading.speed(), resp,
                    centerOfMass);
        }
        unit.samples.add(new ShipControlMap.CalibrationSample(
                point.minControl(), point.maxControl(), point.throttle(),
                reading.speed(), reading.thrust(), reading.active()));
        if (initializationV2) {
            v2CompletedPropulsionSamples++;
        }
        resetCalSamples();
        calibrationSampleIndex++;
        if (calibrationSampleIndex < points.size()) {
            updateCalProgress();
            return;
        }

        unit.actuator.restore();
        calibrationUnitIndex++;
        calibrationSampleIndex = 0;
        if (initializationV2 && !v2RepresentativeSweepStarted
                && calibrationUnitIndex >= calibrationUnits.size()) {
            selectV2RepresentativeUnits();
            v2RepresentativeSweepStarted = true;
            calibrationUnitIndex = 0;
        }
        updateCalProgress();
    }

    // Capture the constraint impulses
    private Map<UUID, ConstraintImpulse> captureConstraintImpulses() {
        Map<UUID, ConstraintImpulse> impulses = new LinkedHashMap<>();
        freezeHandlesBySubLevel.forEach((subLevelId, handle) -> {
            if (handle == null || !handle.isValid()) {
                return;
            }
            Vector3d linear = new Vector3d();
            Vector3d angular = new Vector3d();
            try {
                handle.getJointImpulses(linear, angular);
                impulses.put(subLevelId, new ConstraintImpulse(
                        new Vec3(linear.x, linear.y, linear.z),
                        new Vec3(angular.x, angular.y, angular.z)));
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Could not sample ship initialization constraint", err);
            }
        });
        return Map.copyOf(impulses);
    }

    // Capture the wheel contact samples
    private Map<WheelContactKey, WheelMountControlBridge.PhysicalSample>
            captureWheelContactSamples() {
        Map<WheelContactKey, WheelMountControlBridge.PhysicalSample> samples =
                new LinkedHashMap<>();
        for (Map.Entry<UUID, SubLevel> entry : calibrationSubLevels.entrySet()) {
            for (BlockEntity blockEntity :
                    SubLevelBlockEntityCollector.getBlockEntities(entry.getValue())) {
                if (blockEntity instanceof WheelMountControlBridge wheel) {
                    samples.put(new WheelContactKey(
                                    entry.getKey(), blockEntity.getBlockPos().immutable()),
                            wheel.ct$getPhysicalSample());
                }
            }
        }
        return Map.copyOf(samples);
    }

    // Get the wheel contact response since
    private WheelContactResponse wheelContactResponseSince(
            Map<WheelContactKey, WheelMountControlBridge.PhysicalSample> baseline,
            Vec3 centerOfMass
    ) {
        ServerSubLevel root = requireRootSubLevel();
        Map<WheelContactKey, WheelMountControlBridge.PhysicalSample> current =
                captureWheelContactSamples();
        Vec3 force = Vec3.ZERO;
        Vec3 torque = Vec3.ZERO;
        long samples = 0L;
        for (Map.Entry<WheelContactKey, WheelMountControlBridge.PhysicalSample> entry
                : current.entrySet()) {
            WheelMountControlBridge.PhysicalSample before = baseline.get(entry.getKey());
            WheelMountControlBridge.PhysicalSample after = entry.getValue();
            if (before == null || after == null) {
                continue;
            }
            long sampleCount = after.samples() - before.samples();
            if (sampleCount <= 0L) {
                continue;
            }
            SubLevel src = calibrationSubLevels.get(entry.getKey().subLevelId());
            if (src == null) {
                continue;
            }
            Vec3 localImpulse = finite(after.cumulativeImpulse()
                    .subtract(before.cumulativeImpulse())
                    .scale(1.0D / sampleCount));
            Vec3 rootImpulse = root == src
                    ? localImpulse
                    : transformDirectionBetween(
                    root.logicalPose(), src.logicalPose(), localImpulse);
            Vec3 rootPosition = rootPosition(
                    root, src, after.localPosition());
            force = force.add(rootImpulse);
            torque = torque.add(rootPosition.subtract(centerOfMass).cross(rootImpulse));
            samples += sampleCount;
        }
        return new WheelContactResponse(
                new ConstraintResponse(finite(force), finite(torque)), samples);
    }

    // Get the subtract response
    private static ConstraintResponse subtractResponse(
            ConstraintResponse resp, ConstraintResponse baseline
    ) {
        return new ConstraintResponse(
                finite(resp.force().subtract(baseline.force())),
                finite(resp.torque().subtract(baseline.torque())));
    }

    // Reset the cal samples
    private void resetCalSamples() {
        sampleApplied = false;
        sampleNeutralized = false;
        sampleNeutralWindowStarted = false;
        sampleBaselineImpulses = Map.of();
        sampleBaselineWheelContacts = Map.of();
        sampleNeutralConstraintResponse = new ConstraintResponseSample(
                new ConstraintResponse(Vec3.ZERO, Vec3.ZERO), 0L);
        sampleNeutralWheelResponse = new WheelContactResponse(
                new ConstraintResponse(Vec3.ZERO, Vec3.ZERO), 0L);
    }

    // Get the constraint response since
    private ConstraintResponseSample constraintResponseSince(
            Map<UUID, ConstraintImpulse> baseline
    ) {
        UUID rootId = requireRootSubLevel().getUniqueId();
        Map<UUID, ConstraintImpulse> current = captureConstraintImpulses();
        ConstraintImpulse before = baseline.get(rootId);
        ConstraintImpulse after = current.get(rootId);
        if (before == null || after == null) {
            return new ConstraintResponseSample(
                    new ConstraintResponse(Vec3.ZERO, Vec3.ZERO), 0L);
        }
        Vec3 force = worldDirectionToRoot(
                before.linear().subtract(after.linear()));
        Vec3 torque = worldDirectionToRoot(
                before.angular().subtract(after.angular()));
        return new ConstraintResponseSample(
                new ConstraintResponse(finite(force), finite(torque)), 1L);
    }

    // Update the SCM wheel calibration
    private boolean tickScmWheelCalibration() {
        Level level = controller.getLevel();
        if (level == null) {
            scmWheelCalibrationComplete = true;
            return false;
        }
        Set<WheelMountControlBridge> wheels =
                Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ScmTarget target : ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker())) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, target.subLevelId(), target.blockPosition());
            if (blockEntity instanceof WheelMountControlBridge wheel) {
                wheels.add(wheel);
            }
        }
        if (wheels.isEmpty()) {
            scmWheelCalibrationComplete = true;
            return false;
        }
        float left = scmWheelCalibrationTick < 4 ? 1.0F : 0.0F;
        float right = scmWheelCalibrationTick >= 4 && scmWheelCalibrationTick < 8
                ? 1.0F : 0.0F;
        for (WheelMountControlBridge wheel : wheels) {
            wheel.ct$setDirectInputs(left, right, 0.0F);
        }
        if (++scmWheelCalibrationTick >= 9) {
            for (WheelMountControlBridge wheel : wheels) {
                wheel.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
            }
            scmWheelCalibrationComplete = true;
            return false;
        }
        status = "Testing linked wheel steering (" + scmWheelCalibrationTick + " of 9)";
        return true;
    }

    // Update the bearing calibration
    private void tickBearingCalibration() {
        // ------------------------------------BEARING SELECTION------------------------------------
        while (bearingCalibrationIndex < calibrationBearings.size()
                && !calibrationBearings.get(bearingCalibrationIndex).calibrationRequired) {
            bearingCalibrationIndex++;
            bearingPoseIndex = 0;
            bearingThrottleIndex = 0;
        }
        if (bearingCalibrationIndex >= calibrationBearings.size()) {
            if (carWheelCalibrationPending) {
                beginCarWheelCalibration();
                return;
            }
            phase = Phase.SAVING;
            status = "Saving control map";
            progress = 0.93D;
            return;
        }

        // -----------------------------------------------------POSE SETUP-----------------------------------------------------
        BearingCalibrationUnit bearing = calibrationBearings.get(bearingCalibrationIndex);
        if (bearingPoseIndex >= bearing.poses.size()) {
            restoreBearingMemberActuators(bearing);
            bearing.actuator.restore();
            bearingCalibrationIndex++;
            bearingPoseIndex = 0;
            bearingThrottleIndex = 0;
            bearingPoseWaitTicks = 0;
            bearingPoseApplied = false;
            bearingThrottleApplied = false;
            updateCalProgress();
            return;
        }

        ShipBearingPlanner.Pose pose = bearing.poses.get(bearingPoseIndex);
        if (!bearingPoseApplied) {
            restoreBearingMemberActuators(bearing);
            bearing.actuator.applyPose(pose);
            bearingPoseApplied = true;
            bearingPoseWaitTicks = 0;
            bearingThrottleApplied = false;
            updateCalProgress();
            return;
        }
        if (!bearing.actuator.atPose(pose)) {
            if (bearingPoseWaitTicks++ < BEARING_POSE_TIMEOUT_TICKS) {
                return;
            }
            bearing.poseSamples.get(bearingPoseIndex).invalidate();
            restoreBearingMemberActuators(bearing);
            bearing.actuator.restore();
            restoreInitAssembly();
            status = "Skipped blocked calibration pose for "
                    + bearing.actuator.displayName();
            advanceBearingPose(bearing);
            updateCalProgress();
            return;
        }

        // -----------------------------------------------------POSE SAMPLES-----------------------------------------------------
        BearingPoseSample poseSample = bearing.poseSamples.get(bearingPoseIndex);
        if (!poseSample.aerodynamicsRecorded && !bearing.aerodynamicSurfaces.isEmpty()) {
            recordAerodynamicPose(bearing, poseSample, bearing.actuator.currentPose());
            if (bearing.memberUnitIndices.isEmpty()) {
                advanceBearingPose(bearing);
                updateCalProgress();
                return;
            }
        }

        if (initializationV2) {
            recordV2BearingPose(bearing, poseSample, bearing.actuator.currentPose());
            advanceBearingPose(bearing);
            updateCalProgress();
            return;
        }

        // ------------------------------------THROTTLE SAMPLES------------------------------------
        if (!bearingThrottleApplied) {
            for (int memberUnitIndex : bearing.memberUnitIndices) {
                CalibrationUnit member = calibrationUnits.get(memberUnitIndex);
                member.actuator.applyCalibration(
                        bearingCalibrationPoint(bearing, memberUnitIndex, bearingThrottleIndex));
            }
            bearingThrottleApplied = true;
            sampleSettleTicks = 1;
            updateCalProgress();
            return;
        }
        if (sampleSettleTicks-- > 0) {
            return;
        }

        // ------------------------------------RESPONSE RECORDING------------------------------------
        ShipBearingPlanner.Pose actualPose = bearing.actuator.currentPose();
        for (int memberUnitIndex : bearing.memberUnitIndices) {
            CalibrationUnit member = calibrationUnits.get(memberUnitIndex);
            SubLevel memberSubLevel = calibrationSubLevels.get(member.subLevelId);
            if (memberSubLevel == null) {
                continue;
            }
            Reading reading = member.actuator.read();
            Vec3 rootPosition = rootPosition(
                    requireRootSubLevel(), memberSubLevel, member.actuator.localForcePosition());
            Vec3 forceDirection = rootDirection(
                    requireRootSubLevel(), memberSubLevel, member.actuator.localForceDirection());
            if (Math.signum(reading.thrust()) * member.actuator.expectedThrustSign() < 0.0D) {
                forceDirection = forceDirection.scale(-1.0D);
            }
            poseSample.record(
                    actualPose, memberUnitIndex, rootPosition, forceDirection,
                    Math.abs(reading.thrust()));
        }

        bearingThrottleApplied = false;
        bearingThrottleIndex++;
        if (bearingThrottleIndex < bearingCalPointCount(bearing)) {
            updateCalProgress();
            return;
        }

        advanceBearingPose(bearing);
        updateCalProgress();
    }

    // Begin the car wheel calibration
    private void beginCarWheelCalibration() {
        carWheelCalibrationPending = false;
        carWheelCalibrationStarted = true;
        carWheelDriveActuator = selectCarWheelDrive();
        int wheelSamples = 0;
        for (CalibrationUnit unit : calibrationUnits) {
            if (!unit.actuator.isWheelControl() || !unit.calibrationRequired) {
                continue;
            }
            unit.calibrationPoints = v2RepresentativeSweep(unit.actuator);
            wheelSamples += unit.calibrationPoints.size();
        }
        v2TotalPropulsionSamples += wheelSamples;
        calibrationUnitIndex = Math.max(0, carWheelFirstCalibrationIndex);
        calibrationSampleIndex = 0;
        sampleSettleTicks = 0;
        resetCalSamples();
        status = carWheelDriveActuator == null
                ? "Testing linked wheel controls without a measured driveline"
                : "Testing linked wheel controls with the measured driveline active";
        if (carWheelDriveActuator != null) {
            carWheelDriveActuator.apply(1.0D);
        }
        updateCalProgress();
    }

    // Select the car wheel drive
    private @Nullable Actuator selectCarWheelDrive() {
        List<CalibrationUnit> candidates = calibrationUnits.stream()
                .filter(unit -> !unit.actuator.isWheelControl())
                .filter(unit -> unit.actuator.controllable())
                .toList();
        return candidates.stream()
                .filter(unit -> unit.actuator.isKineticControl())
                .max(Comparator.comparingInt(
                        (CalibrationUnit unit) -> unit.actuator.kineticControlSize())
                        .thenComparingDouble(this::carDriveStrength))
                .or(() -> candidates.stream().max(Comparator.comparingDouble(this::carDriveStrength)))
                .map(unit -> unit.actuator).orElse(null);
    }

    // Get the car drive strength
    private double carDriveStrength(CalibrationUnit unit) {
        return unit.samples.stream().mapToDouble(sample ->
                Math.abs(sample.thrust()) + Math.abs(sample.speed()) * 0.001D)
                .max().orElse(0.0D);
    }

    // Activate the car wheel drive
    private void activateCarWheelDrive(CalibrationUnit unit) {
        if (unit.actuator.isWheelControl() && carWheelDriveActuator != null) {
            carWheelDriveActuator.apply(1.0D);
        }
    }

    // Apply the calibration sample
    private void applyCalibrationSample(
            CalibrationUnit unit, CalibrationPoint point
    ) {
        if (unit.actuator.isWheelControl()
                && carWheelDriveActuator instanceof ScmProbeActuator drive
                && unit.actuator instanceof ScmProbeActuator wheel) {
            wheel.applyWith(drive, point.throttle());
            return;
        }
        unit.actuator.applyCalibration(point);
        activateCarWheelDrive(unit);
    }

    // Restore the car wheel drive
    private void restoreCarWheelDrive() {
        if (carWheelDriveActuator == null) {
            return;
        }
        try {
            carWheelDriveActuator.restore();
        } catch (RuntimeException err) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not restore the car driveline after wheel calibration", err);
        }
        carWheelDriveActuator = null;
    }

    // Record the V2 bearing pose
    private void recordV2BearingPose(
            BearingCalibrationUnit bearing,
            BearingPoseSample poseSample,
            ShipBearingPlanner.Pose actualPose
    ) {
        ServerSubLevel root = requireRootSubLevel();
        for (int memberUnitIndex : bearing.memberUnitIndices) {
            CalibrationUnit member = calibrationUnits.get(memberUnitIndex);
            SubLevel memberSubLevel = calibrationSubLevels.get(member.subLevelId);
            if (memberSubLevel == null) {
                continue;
            }
            double calibratedThrust = maximumCalibratedThrust(member);
            Vec3 rootPosition = rootPosition(
                    root, memberSubLevel, member.actuator.localForcePosition());
            Vec3 forceDirection = rootDirection(
                    root, memberSubLevel, member.actuator.localForceDirection());
            if (Math.signum(calibratedThrust) * member.actuator.expectedThrustSign() < 0.0D) {
                forceDirection = forceDirection.scale(-1.0D);
            }
            poseSample.record(
                    actualPose, memberUnitIndex, rootPosition, forceDirection,
                    Math.abs(calibratedThrust));
        }
    }

    // Record the aerodynamic pose
    private void recordAerodynamicPose(
            BearingCalibrationUnit bearing,
            BearingPoseSample poseSample,
            ShipBearingPlanner.Pose actualPose
    ) {
        ServerSubLevel root = requireRootSubLevel();
        List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
        double pressureTotal = 0.0D;
        int pressureSamples = 0;
        for (int surfaceIndex = 0;
             surfaceIndex < bearing.aerodynamicSurfaces.size();
             surfaceIndex++) {
            AerodynamicSurfaceCalibration surface =
                    bearing.aerodynamicSurfaces.get(surfaceIndex);
            SubLevel surfaceSubLevel = calibrationSubLevels.get(surface.subLevelId);
            if (surfaceSubLevel == null) {
                continue;
            }
            Vec3 localPosition = surface.blockPosition.getCenter();
            Vec3 mappedPosition = rootPosition(root, surfaceSubLevel, localPosition);
            Vec3 mappedNormal = rootDirection(root, surfaceSubLevel, surface.localNormal);
            surfaces.add(surface.toMapSurface(surfaceIndex, mappedPosition, mappedNormal));

            Vec3 worldPosition = worldPosition(surfaceSubLevel.logicalPose(), localPosition);
            double pressure = DimensionPhysicsData.getAirPressure(
                    root.getLevel(),
                    new Vector3d(worldPosition.x, worldPosition.y, worldPosition.z));
            if (Double.isFinite(pressure) && pressure >= 0.0D) {
                pressureTotal += pressure;
                pressureSamples++;
            }
        }
        double airPressure = pressureSamples == 0 ? 1.0D : pressureTotal / pressureSamples;
        Vec3 centerOfMass = assemblyCenterOfMass(root, Vec3.ZERO);
        ShipBearingPlanner.AerodynamicResponse authority =
                ShipBearingPlanner.windSweepAuthority(surfaces, centerOfMass, airPressure);
        poseSample.recordAerodynamics(
                actualPose, surfaces, authority.force().length(), authority.torque().length());
    }

    // Get the assembly center of mass
    private Vec3 assemblyCenterOfMass(ServerSubLevel root, Vec3 fallback) {
        List<MassPoint> massPoints = new ArrayList<>();
        for (SubLevel connected : connectedShipSubLevels(root)) {
            if (!(connected instanceof ServerSubLevel body)) {
                continue;
            }
            if (body.getMassTracker() == null) {
                continue;
            }
            double mass = body.getMassTracker().getMass();
            Vector3dc localCenter = body.getMassTracker().getCenterOfMass();
            if (!Double.isFinite(mass) || mass <= 0.0D || localCenter == null) {
                continue;
            }
            Vec3 rootCenter = rootPosition(
                    root, body, new Vec3(localCenter.x(), localCenter.y(), localCenter.z()));
            massPoints.add(new MassPoint(mass, rootCenter));
        }
        return resolveCombinedCenterOfMass(massPoints, fallback);
    }

    // Advance the bearing pose
    private void advanceBearingPose(BearingCalibrationUnit bearing) {
        restoreBearingMemberActuators(bearing);
        bearingThrottleIndex = 0;
        bearingPoseIndex++;
        bearingPoseWaitTicks = 0;
        bearingPoseApplied = false;
        bearingThrottleApplied = false;
    }

    // Restore the bearing member actuators
    private void restoreBearingMemberActuators(BearingCalibrationUnit bearing) {
        for (int memberUnitIndex : bearing.memberUnitIndices) {
            try {
                calibrationUnits.get(memberUnitIndex).actuator.restore();
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not restore propulsion actuator after bearing calibration", err);
            }
        }
    }

    // Get the bearing cal point count
    private int bearingCalPointCount(BearingCalibrationUnit bearing) {
        if (initializationV2) {
            return 1;
        }
        return bearing.memberUnitIndices.stream()
                .mapToInt(idx -> calibrationUnits.get(idx).calibrationPoints.size())
                .max().orElse(1);
    }

    // Get the bearing calibration point
    private CalibrationPoint bearingCalibrationPoint(
            BearingCalibrationUnit bearing,
            int memberUnitIndex,
            int pointIndex
    ) {
        CalibrationUnit member = calibrationUnits.get(memberUnitIndex);
        if (initializationV2) {
            return new CalibrationPoint(
                    member.actuator.minControl(), member.actuator.maxControl(), 1.0D);
        }
        List<CalibrationPoint> points = member.calibrationPoints;
        if (pointIndex >= 0 && pointIndex < points.size()) {
            return points.get(pointIndex);
        }
        return new CalibrationPoint(
                member.actuator.minControl(), member.actuator.maxControl(), 0.0D);
    }

    // Get the maximum imum calibrated thrust
    private static double maximumCalibratedThrust(CalibrationUnit unit) {
        ShipControlMap.CalibrationSample maximum = unit.samples.stream()
                .max(Comparator.comparingDouble(sample -> Math.abs(sample.thrust())))
                .orElse(null);
        if (maximum != null && Math.abs(maximum.thrust()) > 1.0E-6D) {
            return maximum.thrust();
        }
        return unit.actuator.requiresObservedResponse() ? 0.0D
                : unit.actuator.expectedThrustSign()
                * Math.max(0.0D, unit.actuator.theoreticalMaxThrust());
    }

    // Save the calibration map
    private void saveCalibrationMap() {
        ServerSubLevel root = requireRootSubLevel();
        restoreCalActuators();
        releaseFreezeHandles();
        restoreInitAssembly();
        UUID id = java.util.Objects.requireNonNull(mapId, "Control map id");
        Vec3 centerOfMass = assemblyCenterOfMass(
                root, rootPosition(root, root, controller.getBlockPos().getCenter()));
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        for (int idx = 0; idx < calibrationUnits.size(); idx++) {
            units.add(calibrationUnits.get(idx).toMapUnit(idx));
        }
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        for (BearingCalibrationUnit calibrationBearing : calibrationBearings) {
            ShipControlMap.BearingUnit bearing =
                    calibrationBearing.toMapUnit(bearings.size());
            if (!bearing.poses().isEmpty()) {
                bearings.add(bearing);
            }
        }
        Level level = controller.getLevel();
        String dimension = level == null ? "" : level.dimension().location().toString();
        ShipControlMap created = new ShipControlMap(id, dimension, root.getUniqueId(),
                controller.getBlockPos(), centerOfMass, units, bearings,
                calibrationDockingConnectors, calibrationCrnDisplays,
                calibrationAccDisplays, System.currentTimeMillis());
        if (!ShipControlMapStore.save(level, created)) {
            throw new IllegalStateException("Could not write " + ShipControlMapStore.DATABASE_NAME);
        }
        map = created;
        signalAssemblyMapChanged();
        phase = Phase.READY;
        status = readyStatus(created);
        progress = 1.0D;
        refreshActiveAssemblyMap(root, initForeignMaps);
        showInitProgress(true, false);
        releaseInitProtection();
        releaseInitTracking();
        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        calibrationSubLevels.clear();
        initializationSubLevels.clear();
        initBodyStates.clear();
        clearInitMapPlan();
        reconciliationBaseMap = null;
        prevInitMap = null;
        activeCommands.values().stream()
                .filter(command -> "ship_initialize".equals(command.type()))
                .forEach(this::completeCommand);
        controller.setChanged();
    }

    // Signal the assembly map change
    private void signalAssemblyMapChanged() {
        activeAssemblyPrimaryMap = null;
        activeAssemblyTopology = null;
        assemblyTopologyCache.invalidate();
        SableAssemblyTopologyInvalidation.invalidate(SableLevelApi.serverLevel(controller.getLevel()));
    }

    // Update the cal progress
    private void updateCalProgress() {
        int totalSamples = initializationV2 ? v2TotalPropulsionSamples
                : calibrationUnits.stream()
                .filter(unit -> unit.calibrationRequired)
                .mapToInt(unit -> unit.calibrationPoints.size())
                .sum();
        int bearingSamples = calibrationBearings.stream()
                .filter(bearing -> bearing.calibrationRequired)
                .mapToInt(bearing -> bearing.poses.size() * bearingCalPointCount(bearing))
                .sum();
        int completed = initializationV2 ? v2CompletedPropulsionSamples : 0;
        if (!initializationV2) {
            for (int idx = 0; idx < calibrationUnits.size(); idx++) {
                CalibrationUnit unit = calibrationUnits.get(idx);
                int samples = unit.calibrationRequired ? unit.calibrationPoints.size() : 0;
                completed += idx < calibrationUnitIndex ? samples
                        : idx == calibrationUnitIndex ? calibrationSampleIndex : 0;
            }
        }
        if (calibrationUnitIndex >= calibrationUnits.size()) {
            for (int idx = 0; idx < calibrationBearings.size(); idx++) {
                BearingCalibrationUnit bearing = calibrationBearings.get(idx);
                int pointsPerPose = bearing.calibrationRequired
                        ? bearingCalPointCount(bearing) : 0;
                int samples = bearing.calibrationRequired
                        ? bearing.poses.size() * pointsPerPose : 0;
                completed += idx < bearingCalibrationIndex ? samples
                        : idx == bearingCalibrationIndex
                        ? bearingPoseIndex * pointsPerPose + bearingThrottleIndex : 0;
            }
        }
        progress = 0.15D + 0.75D * completed / Math.max(1.0D, totalSamples + bearingSamples);
        if (calibrationUnitIndex < calibrationUnits.size()) {
            CalibrationUnit current = calibrationUnits.get(calibrationUnitIndex);
            status = initializationV2
                    ? current.v2Representative
                    ? "V2 sampling representative propulsion unit "
                    : "V2 matrix probing propulsion unit "
                    : "Calibrating propulsion unit ";
            status += Math.min(calibrationUnitIndex + 1, calibrationUnits.size())
                    + " of " + calibrationUnits.size();
        } else if (calibrationBearings.isEmpty()) {
            status = "Finishing propulsion calibration";
        } else {
            BearingCalibrationUnit current = calibrationBearings.get(
                    Mth.clamp(bearingCalibrationIndex, 0, calibrationBearings.size() - 1));
            status = (initializationV2 ? "V2 sampling and interpolating " : "Calibrating ")
                    + current.actuator.displayName() + " "
                    + Math.min(bearingCalibrationIndex + 1, calibrationBearings.size())
                    + " of " + calibrationBearings.size();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       ASSEMBLY MAPS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh the active assembly map
    private @Nullable ShipControlMap refreshActiveAssemblyMap(ServerSubLevel root) {
        return refreshActiveAssemblyMap(root, null);
    }

    // Refresh the active assembly map
    private @Nullable ShipControlMap refreshActiveAssemblyMap(
            ServerSubLevel root,
            @Nullable Map<UUID, ForeignScmMap> foreignMapSnapshot
    ) {
        ShipControlMap primaryMap = map;
        if (primaryMap == null) {
            activeAssemblyMap = null;
            activeAssemblyPrimaryMap = null;
            activeAssemblyTopology = null;
            activeScmActuatorOwners = Map.of();
            activeAssemblyMapSignature = Long.MIN_VALUE;
            activeAssemblySubLevelIds = Set.of();
            activeCarriageCount = 0;
            absorbedScmMapCount = 0;
            return null;
        }

        // -----------------------------------------------------ASSEMBLY TOPOLOGY-------------------------------------------------

        SableAssemblyTopologyApi.Topology topology = assemblyTopology(root);
        long gameTime = controller.getLevel() == null
                ? Long.MIN_VALUE : controller.getLevel().getGameTime();
        boolean safetyRefreshDue = !mainCarriageMaps.isEmpty()
                && intervalElapsed(gameTime, lastMainCarriageMapSafetyRefreshTick,
                MAIN_CARRIAGE_MAP_SAFETY_REFRESH_TICKS);
        if (foreignMapSnapshot == null && activeAssemblyMap != null
                && activeAssemblyPrimaryMap == primaryMap
                && activeAssemblyTopology == topology && !safetyRefreshDue) {
            return activeAssemblyMap;
        }
        updateCollisionTopology(topology);
        if (!topology.available()) {
            if (activeAssemblyMap != null
                    || activeAssemblyMapSignature != Long.MIN_VALUE
                    || !activeAssemblySubLevelIds.isEmpty()) {
                releaseControlAuthority();
                releaseControlActuators();
                allocationWorkspace.reset();
                reversedCalDirs.clear();
                calDirMapId = null;
            }
            activeAssemblyMap = null;
            activeAssemblyPrimaryMap = null;
            activeAssemblyTopology = topology;
            activeScmActuatorOwners = Map.of();
            activeAssemblyMapSignature = Long.MIN_VALUE;
            activeAssemblySubLevelIds = Set.of();
            activeCarriageCount = 0;
            absorbedScmMapCount = 0;
            status = "Waiting for connected carriage topology";
            return null;
        }

        // -----------------------------------------------------MAP COMPOSITION---------------------------------------------------

        Map<UUID, ForeignScmMap> foreignMaps;
        if (foreignMapSnapshot != null) {
            foreignMaps = Map.copyOf(foreignMapSnapshot);
        } else if (topology.fingerprint().equals(
                suppressedForeignTopologyFingerprint)) {
            foreignMaps = Map.of();
        } else {
            if (!suppressedForeignTopologyFingerprint.isBlank()) {
                suppressedForeignTopologyFingerprint = "";
                controller.setChanged();
            }
            foreignMaps = foreignScmMaps(topology);
        }
        Set<UUID> primaryOwned = new LinkedHashSet<>();
        Set<UUID> activeMainCarriageMapIds = new LinkedHashSet<>();
        List<ScmMapCompositionApi.Fragment<AssemblyMapSource>> attached = new ArrayList<>();
        for (SableAssemblyTopologyApi.CarriagePartition partition
                : topology.carriagePartitions()) {
            if (partition.primary()) {
                primaryOwned.addAll(partition.bodyIds());
                continue;
            }
            ForeignScmMap foreign = foreignMaps.get(partition.rootSubLevelId());
            if (foreign != null) {
                attached.add(new ScmMapCompositionApi.Fragment<>(
                        foreign.map().id(), foreign.map().rootSubLevelId(),
                        partition.bodyIds(), new AssemblyMapSource(
                        foreign.map(), true, foreign.controller())));
            } else {
                ShipControlMap carriageMap = mainCarriageMap(
                        root, topology, partition, primaryMap);
                if (carriageMap == null) {
                    continue;
                }
                activeMainCarriageMapIds.add(carriageMap.id());
                attached.add(new ScmMapCompositionApi.Fragment<>(
                        carriageMap.id(), carriageMap.rootSubLevelId(),
                        partition.bodyIds(), new AssemblyMapSource(
                        carriageMap, true, controller)));
            }
        }
        mainCarriageMaps.keySet().retainAll(activeMainCarriageMapIds);
        mainCarriageMapValidationTicks.keySet().retainAll(activeMainCarriageMapIds);
        mainCarriageMapValidationRevisions.keySet().retainAll(activeMainCarriageMapIds);
        dirtyMainCarriageMapIds.retainAll(activeMainCarriageMapIds);
        primaryOwned.add(root.getUniqueId());
        ScmMapCompositionApi.Fragment<AssemblyMapSource> primary =
                new ScmMapCompositionApi.Fragment<>(
                        primaryMap.id(), root.getUniqueId(), primaryOwned,
                        new AssemblyMapSource(primaryMap, false, controller));
        ScmMapCompositionApi.Composition<AssemblyMapSource> composition =
                ScmMapCompositionApi.compose(
                        primary, attached, topology.loadedBodyIds());
        long signature = activeAssemblySignature(topology, composition);
        if (activeAssemblyMap != null && activeAssemblyMapSignature == signature) {
            activeAssemblyMap = composeAssemblyMap(root, primaryMap, composition);
            activeAssemblyPrimaryMap = primaryMap;
            activeAssemblyTopology = topology;
            activeScmActuatorOwners = scmActuatorOwners(composition);
            activeAssemblySubLevelIds = topology.loadedBodyIds();
            activeCarriageCount = Math.max(0,
                    topology.carriagePartitions().size() - 1);
            absorbedScmMapCount = foreignMaps.size();
            lastMainCarriageMapSafetyRefreshTick = mainCarriageMaps.isEmpty()
                    ? Long.MIN_VALUE : gameTime;
            return activeAssemblyMap;
        }

        ShipControlMap composed = composeAssemblyMap(root, primaryMap, composition);
        releaseControlActuators();
        allocationWorkspace.reset();
        reversedCalDirs.clear();
        calDirMapId = null;
        activeAssemblyMap = composed;
        activeAssemblyPrimaryMap = primaryMap;
        activeAssemblyTopology = topology;
        activeScmActuatorOwners = scmActuatorOwners(composition);
        activeAssemblyMapSignature = signature;
        activeAssemblySubLevelIds = topology.loadedBodyIds();
        activeCarriageCount = Math.max(0,
                topology.carriagePartitions().size() - 1);
        absorbedScmMapCount = foreignMaps.size();
        lastMainCarriageMapSafetyRefreshTick = mainCarriageMaps.isEmpty()
                ? Long.MIN_VALUE : gameTime;
        status = readyStatus(composed)
                + "; " + activeCarriageCount + " connected carriage"
                + (activeCarriageCount == 1 ? "" : "s")
                + "; " + absorbedScmMapCount + " absorbed carriage SCM map"
                + (absorbedScmMapCount == 1 ? "" : "s");
        invalidateTelemetryCache();
        return composed;
    }

    // Get the SCM actuator owners
    private Map<AssemblyUnitIdentity, AdvancedContraptionControllerBlockEntity>
    scmActuatorOwners(
            ScmMapCompositionApi.Composition<AssemblyMapSource> composition
    ) {
        Map<AssemblyUnitIdentity, AdvancedContraptionControllerBlockEntity> owners =
                new LinkedHashMap<>();
        for (ScmMapCompositionApi.SelectedFragment<AssemblyMapSource> selection
                : composition.fragments()) {
            AssemblyMapSource src = selection.value();
            if (src == null || src.controller() == null) {
                continue;
            }
            for (ShipControlMap.PropulsionUnit unit : src.map().units()) {
                if (unit.adapter().startsWith("scm:")
                        && selection.effectiveSubLevelIds().contains(unit.subLevelId())) {
                    owners.putIfAbsent(new AssemblyUnitIdentity(
                                    unit.subLevelId(), unit.blockPosition(), unit.adapter()),
                            src.controller());
                }
            }
        }
        return Map.copyOf(owners);
    }

    // Get the foreign SCM maps
    private Map<UUID, ForeignScmMap> foreignScmMaps(
            SableAssemblyTopologyApi.Topology topology
    ) {
        return foreignScmMaps(topology, false);
    }

    // Get the foreign SCM maps
    private Map<UUID, ForeignScmMap> foreignScmMaps(
            SableAssemblyTopologyApi.Topology topology,
            boolean retryMissingMaps
    ) {
        Map<UUID, ForeignScmMap> res = new LinkedHashMap<>();
        Level level = controller.getLevel();
        String dimension = level == null
                ? "" : level.dimension().location().toString();
        for (SableAssemblyTopologyApi.CarriagePartition partition
                : topology.carriagePartitions()) {
            if (partition.primary()) {
                continue;
            }
            List<ForeignScmMap> candidates = new ArrayList<>();
            for (UUID bodyId : partition.bodyIds()) {
                ServerSubLevel body = topology.body(bodyId)
                        .map(SableAssemblyTopologyApi.Body::subLevel)
                        .orElse(null);
                if (body == null) {
                    continue;
                }
                for (BlockEntity blockEntity
                        : SubLevelBlockEntityCollector.getBlockEntities(body)) {
                    if (!(blockEntity instanceof AdvancedContraptionControllerBlockEntity scm)
                            || scm == controller || !scm.canShareLocalShipControlMap()) {
                        continue;
                    }
                    ShipControlMap foreignMap = scm.localShipControlMap(retryMissingMaps);
                    if (foreignMap == null
                            || !bodyId.equals(foreignMap.rootSubLevelId())
                            || !blockEntity.getBlockPos().equals(
                            foreignMap.controllerPosition())
                            || !dimension.equals(foreignMap.dimension())
                            || requiresCalibrationRefresh(foreignMap)) {
                        continue;
                    }
                    candidates.add(new ForeignScmMap(
                            foreignMap, bodyId, blockEntity.getBlockPos().immutable(), scm));
                }
            }
            candidates.stream()
                    .sorted(Comparator
                            .comparing((ForeignScmMap candidate) ->
                                    !partition.rootSubLevelId().equals(
                                            candidate.controllerSubLevelId()))
                            .thenComparingLong(candidate -> -mapSubLevelIds(candidate.map())
                                    .stream().filter(partition::contains).count())
                            .thenComparing(candidate ->
                                    candidate.controllerSubLevelId().toString())
                            .thenComparingLong(candidate ->
                                    candidate.controllerPosition().asLong())
                            .thenComparing(candidate -> candidate.map().id().toString()))
                    .findFirst()
                    .ifPresent(candidate -> res.put(
                            partition.rootSubLevelId(), candidate));
        }
        return Map.copyOf(res);
    }

    // Get the foreign owned sublevel ids
    private static Set<UUID> foreignOwnedSubLevelIds(
            SableAssemblyTopologyApi.Topology topology,
            Map<UUID, ForeignScmMap> foreignMaps
    ) {
        if (topology == null || foreignMaps == null || foreignMaps.isEmpty()) {
            return Set.of();
        }
        Set<UUID> owned = new LinkedHashSet<>();
        for (SableAssemblyTopologyApi.CarriagePartition partition
                : topology.carriagePartitions()) {
            if (foreignMaps.containsKey(partition.rootSubLevelId())) {
                owned.addAll(partition.bodyIds());
            }
        }
        return Set.copyOf(owned);
    }

    // Clear the init map plan
    private void clearInitMapPlan() {
        initForeignMaps = Map.of();
        initForeignSubLevelIds = Set.of();
    }

    // Get the active assembly signature
    private long activeAssemblySignature(
            SableAssemblyTopologyApi.Topology topology,
            ScmMapCompositionApi.Composition<AssemblyMapSource> composition
    ) {
        long signature = 0xcbf29ce484222325L;
        signature = mixSignature(signature, topology.fingerprint());
        signature = mixSignature(signature, composition.fingerprint());
        for (ScmMapCompositionApi.SelectedFragment<AssemblyMapSource> selection
                : composition.fragments()) {
            AssemblyMapSource src = selection.value();
            if (src == null) {
                continue;
            }
            signature = mixSignature(signature, src.map().id().toString());
            signature ^= src.map().updatedAt();
            signature *= 0x100000001b3L;
        }
        return signature;
    }

    // Get the mix signature
    private static long mixSignature(long signature, String val) {
        long mixed = signature;
        for (int idx = 0; idx < val.length(); idx++) {
            mixed ^= val.charAt(idx);
            mixed *= 0x100000001b3L;
        }
        return mixed;
    }

    // Get the main carriage fragment id
    private static UUID mainCarriageFragmentId(
            UUID primaryMapId,
            UUID carriageRootSubLevelId,
            Collection<UUID> coveredSubLevelIds
    ) {
        String key = "scm-carriage-map\u0000" + primaryMapId
                + '\u0000' + carriageRootSubLevelId;
        if (coveredSubLevelIds != null) {
            key += coveredSubLevelIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.comparing(UUID::toString))
                    .map(id -> "\u0000" + id)
                    .reduce("", String::concat);
        }
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    // Get the main carriage map
    private @Nullable ShipControlMap mainCarriageMap(
            ServerSubLevel assemblyRoot,
            SableAssemblyTopologyApi.Topology topology,
            SableAssemblyTopologyApi.CarriagePartition partition,
            ShipControlMap primaryMap
    ) {
        UUID fragmentId = mainCarriageFragmentId(
                primaryMap.id(), partition.rootSubLevelId(), partition.bodyIds());
        ShipControlMap cached = mainCarriageMaps.get(fragmentId);
        ServerSubLevel carriageRoot = topology.body(partition.rootSubLevelId())
                .map(SableAssemblyTopologyApi.Body::subLevel)
                .orElse(null);
        if (carriageRoot == null || carriageRoot.isRemoved()) {
            return null;
        }
        long topologyRevision = assemblyTopologyCache.revision();
        long gameTime = controller.getLevel() == null
                ? Long.MIN_VALUE : controller.getLevel().getGameTime();
        Long validatedAt = mainCarriageMapValidationTicks.get(fragmentId);
        Long validatedRevision = mainCarriageMapValidationRevisions.get(fragmentId);
        if (cached != null && validatedRevision != null
                && validatedRevision == topologyRevision
                && !intervalElapsed(gameTime, validatedAt == null
                        ? Long.MIN_VALUE : validatedAt,
                MAIN_CARRIAGE_MAP_SAFETY_REFRESH_TICKS)) {
            return cached;
        }

        ShipControlMap stored = cached == null
                ? ShipControlMapStore.load(controller.getLevel(), fragmentId)
                : null;
        boolean storedMatches = stored != null
                && fragmentId.equals(stored.id())
                && partition.rootSubLevelId().equals(stored.rootSubLevelId())
                && mapSubLevelIds(stored).stream().allMatch(partition::contains)
                && !requiresCalibrationRefresh(stored);
        ShipControlMap validStored = storedMatches ? stored : null;
        if (!carriageScanReady(topology, partition)) {
            ShipControlMap fallback = cached != null ? cached : validStored;
            if (fallback != null) {
                mainCarriageMaps.put(fragmentId, fallback);
            }
            return fallback;
        }

        Map<UUID, SubLevel> liveSubLevels = new LinkedHashMap<>();
        for (SableAssemblyTopologyApi.Body body : topology.bodies()) {
            if (body.subLevel() != null && !body.subLevel().isRemoved()) {
                liveSubLevels.put(body.subLevelId(), body.subLevel());
            }
        }
        ShipControlMap seed = primaryCarriageSeed(
                assemblyRoot, carriageRoot, partition, primaryMap,
                fragmentId, liveSubLevels);
        ShipControlMap reusable = cached != null
                ? cached : bestCalibratedCarriageSeed(seed, validStored);
        ShipControlMap scanned = scanMainCarriageMap(
                carriageRoot, partition, fragmentId,
                hasMappedCarriageContent(reusable) ? reusable : null);
        ShipControlMap prev = cached != null ? cached : validStored;
        if (sameCarriageMap(prev, scanned)) {
            mainCarriageMaps.put(fragmentId, prev);
            if (dirtyMainCarriageMapIds.contains(fragmentId)) {
                persistMainCarriageMap(fragmentId, prev);
            }
            mainCarriageMapValidationTicks.put(fragmentId, gameTime);
            mainCarriageMapValidationRevisions.put(fragmentId, topologyRevision);
            return prev;
        }
        if (prev == null && reusable != null
                && sameCarriageMap(reusable, scanned)) {
            scanned = reusable;
        }
        persistMainCarriageMap(fragmentId, scanned);
        mainCarriageMaps.put(fragmentId, scanned);
        mainCarriageMapValidationTicks.put(fragmentId, gameTime);
        mainCarriageMapValidationRevisions.put(fragmentId, topologyRevision);
        activeAssemblyMapSignature = Long.MIN_VALUE;
        return scanned;
    }

    // Check if the carriage scan is ready
    private static boolean carriageScanReady(
            SableAssemblyTopologyApi.Topology topology,
            SableAssemblyTopologyApi.CarriagePartition partition
    ) {
        try {
            for (UUID bodyId : partition.bodyIds()) {
                ServerSubLevel body = topology.body(bodyId)
                        .map(SableAssemblyTopologyApi.Body::subLevel)
                        .orElse(null);
                if (body == null || body.isRemoved() || body.getPlot() == null
                        || body.getPlot().getLoadedChunks().isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Persist the main carriage map
    private void persistMainCarriageMap(UUID fragmentId, ShipControlMap carriageMap) {
        if (ShipControlMapStore.save(controller.getLevel(), carriageMap)) {
            dirtyMainCarriageMapIds.remove(fragmentId);
            return;
        }
        dirtyMainCarriageMapIds.add(fragmentId);
        LOGGER.log(System.Logger.Level.WARNING,
                "Could not persist the dedicated carriage control map " + fragmentId);
    }

    // Get the best calibrated carriage seed
    private static @Nullable ShipControlMap bestCalibratedCarriageSeed(
            @Nullable ShipControlMap primarySeed,
            @Nullable ShipControlMap storedSeed
    ) {
        if (!hasMappedCarriageContent(primarySeed)) {
            return storedSeed;
        }
        if (!hasMappedCarriageContent(storedSeed)) {
            return primarySeed;
        }
        long primaryScore = carriageCalibrationScore(primarySeed);
        long storedScore = carriageCalibrationScore(storedSeed);
        if (primaryScore != storedScore) {
            return primaryScore > storedScore ? primarySeed : storedSeed;
        }
        return primarySeed.updatedAt() >= storedSeed.updatedAt()
                ? primarySeed : storedSeed;
    }

    // Get the carriage calibration score
    private static long carriageCalibrationScore(ShipControlMap candidate) {
        long calibratedUnits = candidate.units().stream()
                .filter(unit -> !unit.samples().isEmpty()).count();
        long calibrationSamples = candidate.units().stream()
                .mapToLong(unit -> unit.samples().size()).sum();
        long mappedContent = candidate.units().size()
                + candidate.bearings().size()
                + candidate.dockingConnectors().size()
                + candidate.crnDisplays().size()
                + candidate.accDisplays().size();
        return calibratedUnits * 1_000_000L
                + Math.min(999_999L, calibrationSamples * 1_000L + mappedContent);
    }

    // Check if this uses the same carriage map
    private static boolean sameCarriageMap(
            @Nullable ShipControlMap first,
            @Nullable ShipControlMap second
    ) {
        return first != null && second != null
                && Objects.equals(first.id(), second.id())
                && Objects.equals(first.dimension(), second.dimension())
                && Objects.equals(first.rootSubLevelId(), second.rootSubLevelId())
                && Objects.equals(first.controllerPosition(), second.controllerPosition())
                && sameMainCarriageUnits(first.units(), second.units())
                && Objects.equals(first.bearings(), second.bearings())
                && sameMainCarriageConnectors(
                        first.dockingConnectors(), second.dockingConnectors())
                && Objects.equals(first.crnDisplays(), second.crnDisplays())
                && Objects.equals(first.accDisplays(), second.accDisplays());
    }

    // Check if this uses the same main carriage units
    private static boolean sameMainCarriageUnits(
            List<ShipControlMap.PropulsionUnit> first,
            List<ShipControlMap.PropulsionUnit> second
    ) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int idx = 0; idx < first.size(); idx++) {
            ShipControlMap.PropulsionUnit left = first.get(idx);
            ShipControlMap.PropulsionUnit right = second.get(idx);
            if (!PropulsionUnitKey.of(left).equals(PropulsionUnitKey.of(right))
                    || left.controllable() != right.controllable()
                    || !nearlyEqual(left.minControl(), right.minControl())
                    || !nearlyEqual(left.maxControl(), right.maxControl())
                    || !nearlyEqual(left.minThrust(), right.minThrust())
                    || !nearlyEqual(left.maxThrust(), right.maxThrust())
                    || !nearlyEqual(left.maxSpeed(), right.maxSpeed())
                    || !Objects.equals(left.samples(), right.samples())) {
                return false;
            }
        }
        return true;
    }

    // Check if this uses the same main carriage connectors
    private static boolean sameMainCarriageConnectors(
            List<ShipControlMap.DockingConnector> first,
            List<ShipControlMap.DockingConnector> second
    ) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int idx = 0; idx < first.size(); idx++) {
            ShipControlMap.DockingConnector left = first.get(idx);
            ShipControlMap.DockingConnector right = second.get(idx);
            if (!Objects.equals(left.subLevelId(), right.subLevelId())
                    || !Objects.equals(left.blockPosition(), right.blockPosition())) {
                return false;
            }
        }
        return true;
    }

    // Get the primary carriage seed
    private ShipControlMap primaryCarriageSeed(
            ServerSubLevel assemblyRoot,
            ServerSubLevel carriageRoot,
            SableAssemblyTopologyApi.CarriagePartition partition,
            ShipControlMap primaryMap,
            UUID fragmentId,
            Map<UUID, SubLevel> liveSubLevels
    ) {
        ScmMapCompositionApi.Fragment<AssemblyMapSource> fragment =
                new ScmMapCompositionApi.Fragment<>(
                        fragmentId, carriageRoot.getUniqueId(), partition.bodyIds(),
                        new AssemblyMapSource(primaryMap, true, controller));
        ScmMapCompositionApi.Composition<AssemblyMapSource> composition =
                ScmMapCompositionApi.compose(
                        fragment, List.of(), partition.bodyIds());
        ShipControlMap transformed = composeAssemblyMap(
                carriageRoot, primaryMap, composition, liveSubLevels);
        Vec3 fallbackCenter = rootPosition(
                carriageRoot, assemblyRoot, primaryMap.centerOfMass());
        return new ShipControlMap(
                fragmentId, primaryMap.dimension(), carriageRoot.getUniqueId(),
                carriageRoot.getPlot().getCenterBlock(),
                rootBodyCenterOfMass(carriageRoot, fallbackCenter),
                transformed.units(), transformed.bearings(),
                transformed.dockingConnectors(), transformed.crnDisplays(),
                transformed.accDisplays(), primaryMap.updatedAt());
    }

    // Scan the main carriage map
    private ShipControlMap scanMainCarriageMap(
            ServerSubLevel carriageRoot,
            SableAssemblyTopologyApi.CarriagePartition partition,
            UUID fragmentId,
            @Nullable ShipControlMap reusableMap
    ) {
        List<SubLevel> bodies = partition.bodyIds().stream()
                .map(id -> SubLevelBlockEntityCollector.getSubLevel(
                        carriageRoot.getLevel(), id))
                .filter(SubLevel.class::isInstance)
                .map(SubLevel.class::cast)
                .filter(body -> !body.isRemoved())
                .sorted(Comparator.comparing(body -> body.getUniqueId().toString()))
                .toList();
        Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusableUnits =
                reusablePropulsionUnits(reusableMap);
        List<CalibrationUnit> discoveredUnits = new ArrayList<>();
        List<ShipControlMap.DockingConnector> connectors = new ArrayList<>();
        Set<ShipControlMap.CrnDisplay> crnDisplays = new LinkedHashSet<>();
        Set<ShipControlMap.AccDisplay> accDisplays = new LinkedHashSet<>();

        for (SubLevel body : bodies) {
            for (BlockEntity blockEntity
                    : SubLevelBlockEntityCollector.getBlockEntities(body)) {
                if (isDockingConnector(blockEntity)) {
                    Direction facing = blockEntity.getBlockState().getValue(
                            BlockStateProperties.FACING);
                    Vec3 localFacing = Vec3.atLowerCornerOf(facing.getNormal());
                    Vec3 localTip = blockEntity.getBlockPos().getCenter()
                            .add(localFacing.scale(1.5D));
                    connectors.add(new ShipControlMap.DockingConnector(
                            connectors.size(), body.getUniqueId(),
                            blockEntity.getBlockPos(),
                            rootPosition(carriageRoot, body, localTip),
                            rootDirection(carriageRoot, body, localFacing)));
                }
                if (RailwayNavigatorGraphCompat.isAdvancedDisplayController(blockEntity)) {
                    crnDisplays.add(new ShipControlMap.CrnDisplay(
                            body.getUniqueId(), blockEntity.getBlockPos()));
                }
                if (blockEntity instanceof AccDisplayBlockEntity display
                        && display.isNetworkRoot()) {
                    accDisplays.add(new ShipControlMap.AccDisplay(
                            body.getUniqueId(), display.getBlockPos()));
                }
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
                        blockEntity.getBlockState().getBlock());
                for (Actuator actuator : actuatorsFor(blockEntity)) {
                    String storedBlockId = blockId == null ? "" : blockId.toString();
                    discoveredUnits.add(new CalibrationUnit(
                            body.getUniqueId(), blockEntity.getBlockPos(), storedBlockId,
                            rootPosition(carriageRoot, body,
                                    actuator.localForcePosition()),
                            rootDirection(carriageRoot, body,
                                    actuator.localForceDirection()),
                            actuator, reusableUnits.get(new PropulsionUnitKey(
                                    body.getUniqueId(), blockEntity.getBlockPos(),
                                    storedBlockId, actuator.kind())), true));
                }
            }
        }
        discoverMainCarriageLinkedControls(
                carriageRoot, bodies, reusableUnits, discoveredUnits);
        discoveredUnits.sort(Comparator
                .comparing((CalibrationUnit unit) -> unit.subLevelId.toString())
                .thenComparing(unit -> unit.blockPosition)
                .thenComparing(unit -> unit.actuator.kind()));
        // ------------------------------------PROPULSION MAP------------------------------------
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        Map<PropulsionUnitKey, Integer> unitIndices = new LinkedHashMap<>();
        for (CalibrationUnit discovered : discoveredUnits) {
            ShipControlMap.PropulsionUnit unit = discovered.toMapUnit(units.size());
            if (unitIndices.putIfAbsent(PropulsionUnitKey.of(unit), unit.index()) == null) {
                units.add(unit);
            }
        }
        applyCarriageCapacityFallback(units);
        // ------------------------------------BEARINGS AND CONNECTORS------------------------------------
        List<ShipControlMap.BearingUnit> bearings = remapCarriageBearings(
                reusableMap, unitIndices, partition);
        connectors.sort(Comparator
                .comparing((ShipControlMap.DockingConnector connector) ->
                        connector.subLevelId().toString())
                .thenComparing(ShipControlMap.DockingConnector::blockPosition));
        for (int idx = 0; idx < connectors.size(); idx++) {
            ShipControlMap.DockingConnector connector = connectors.get(idx);
            connectors.set(idx, new ShipControlMap.DockingConnector(
                    idx, connector.subLevelId(), connector.blockPosition(),
                    connector.rootTipPosition(), connector.rootFacing()));
        }

        String dimension = controller.getLevel() == null
                ? "" : controller.getLevel().dimension().location().toString();
        return new ShipControlMap(
                fragmentId, dimension, carriageRoot.getUniqueId(),
                carriageRoot.getPlot().getCenterBlock(),
                rootBodyCenterOfMass(
                        carriageRoot, carriageRoot.getPlot().getCenterBlock().getCenter()),
                units, bearings, connectors,
                crnDisplays.stream().sorted(Comparator
                        .comparing((ShipControlMap.CrnDisplay display) ->
                                display.subLevelId().toString())
                        .thenComparing(ShipControlMap.CrnDisplay::blockPosition)).toList(),
                accDisplays.stream().sorted(Comparator
                        .comparing((ShipControlMap.AccDisplay display) ->
                                display.subLevelId().toString())
                        .thenComparing(ShipControlMap.AccDisplay::blockPosition)).toList(),
                System.currentTimeMillis());
    }

    // Apply the carriage capacity fallback
    private static void applyCarriageCapacityFallback(
            List<ShipControlMap.PropulsionUnit> units
    ) {
        List<Double> knownCapacities = units.stream()
                .filter(ShipControlMap.PropulsionUnit::controllable)
                .map(ShipControlMap.PropulsionUnit::maxThrust)
                .filter(capacity -> capacity > 1.0E-6D)
                .sorted()
                .toList();
        double fallbackCapacity;
        if (knownCapacities.isEmpty()) {
            fallbackCapacity = 1.0D;
        } else {
            int middle = knownCapacities.size() / 2;
            fallbackCapacity = knownCapacities.size() % 2 == 0
                    ? (knownCapacities.get(middle - 1) + knownCapacities.get(middle)) * 0.5D
                    : knownCapacities.get(middle);
        }
        for (int idx = 0; idx < units.size(); idx++) {
            ShipControlMap.PropulsionUnit unit = units.get(idx);
            if (!unit.controllable() || !unit.samples().isEmpty()
                    || unit.maxThrust() > 1.0E-6D) {
                continue;
            }
            units.set(idx, new ShipControlMap.PropulsionUnit(
                    unit.index(), unit.subLevelId(), unit.blockPosition(), unit.blockId(),
                    unit.adapter(), true, unit.rootPosition(), unit.forceDirection(),
                    unit.minControl(), unit.maxControl(), unit.minThrust(),
                    fallbackCapacity, unit.maxSpeed(), unit.samples()));
        }
    }

    // Discover the main carriage linked controls
    private void discoverMainCarriageLinkedControls(
            ServerSubLevel carriageRoot,
            List<SubLevel> bodies,
            Map<PropulsionUnitKey, ShipControlMap.PropulsionUnit> reusableUnits,
            List<CalibrationUnit> discoveredUnits
    ) {
        Map<UUID, SubLevel> bodiesById = new LinkedHashMap<>();
        bodies.forEach(body -> bodiesById.put(body.getUniqueId(), body));
        List<ScmTarget> targets = liveScmTargets(bodiesById).stream()
                .filter(target -> bodiesById.containsKey(target.subLevelId()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        Vec3 suggestedDir = suggestedScmTravelDir(carriageRoot, targets, bodiesById);
        for (LinkedScmProbe linked : linkedScmProbeConfigs(
                controller.getLevel(), targets, bodiesById, carriageRoot, suggestedDir,
                isControlMode(ScmBuiltinControlModes.CAR_ID))) {
            ScmTarget target = linked.target();
            SubLevel body = linked.subLevel();
            BlockEntity blockEntity = linked.blockEntity();
            if (body == null || blockEntity != null && (isThrusterProvider(blockEntity)
                    || !bearingActuatorsFor(blockEntity).isEmpty())) {
                continue;
            }
            Actuator actuator = new ScmProbeActuator(
                    body.getLevel(), target, blockEntity, linked.probe());
            discoveredUnits.add(new CalibrationUnit(
                    target.subLevelId(), target.blockPosition(), target.blockId(),
                    rootPosition(carriageRoot, body,
                            actuator.localForcePosition()),
                    rootDirection(carriageRoot, body,
                            actuator.localForceDirection()),
                    actuator, reusableUnits.get(new PropulsionUnitKey(
                            target.subLevelId(), target.blockPosition(),
                            target.blockId(), actuator.kind())), true));
        }
    }

    // Remap the carriage bearings
    private List<ShipControlMap.BearingUnit> remapCarriageBearings(
            @Nullable ShipControlMap reusableMap,
            Map<PropulsionUnitKey, Integer> currentUnitIndices,
            SableAssemblyTopologyApi.CarriagePartition partition
    ) {
        if (reusableMap == null || reusableMap.bearings().isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> indexRemap = new HashMap<>();
        for (ShipControlMap.PropulsionUnit prev : reusableMap.units()) {
            Integer current = currentUnitIndices.get(PropulsionUnitKey.of(prev));
            if (current != null) {
                indexRemap.put(prev.index(), current);
            }
        }
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        for (ShipControlMap.BearingUnit bearing : reusableMap.bearings()) {
            if (!partition.contains(bearing.hostSubLevelId())) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), bearing.hostSubLevelId(),
                    bearing.blockPosition());
            if (bearingActuatorForStored(blockEntity, bearing.adapter()) == null) {
                continue;
            }
            List<ShipControlMap.BearingPose> poses = new ArrayList<>();
            for (ShipControlMap.BearingPose pose : bearing.poses()) {
                List<ShipControlMap.BearingResponse> responses = pose.responses().stream()
                        .filter(resp -> indexRemap.containsKey(
                                resp.propulsionUnitIndex()))
                        .map(resp -> new ShipControlMap.BearingResponse(
                                indexRemap.get(resp.propulsionUnitIndex()),
                                resp.rootPosition(), resp.forceDirection(),
                                resp.maxThrust()))
                        .toList();
                poses.add(new ShipControlMap.BearingPose(
                        pose.angleX(), pose.angleZ(), responses,
                        pose.aerodynamicSurfaces().stream()
                                .filter(surface -> partition.contains(surface.subLevelId()))
                                .toList(),
                        pose.maxAerodynamicForce(), pose.maxAerodynamicTorque()));
            }
            bearings.add(new ShipControlMap.BearingUnit(
                    bearings.size(), bearing.hostSubLevelId(), bearing.blockPosition(),
                    bearing.blockId(), bearing.adapter(),
                    bearing.childSubLevelIds().stream()
                            .filter(partition::contains).toList(),
                    bearing.minX(), bearing.maxX(), bearing.minZ(), bearing.maxZ(), poses));
        }
        return List.copyOf(bearings);
    }

    // Check if this has mapped carriage content
    private static boolean hasMappedCarriageContent(@Nullable ShipControlMap candidate) {
        return candidate != null && (!candidate.units().isEmpty()
                || !candidate.bearings().isEmpty()
                || !candidate.dockingConnectors().isEmpty()
                || !candidate.crnDisplays().isEmpty()
                || !candidate.accDisplays().isEmpty());
    }

    // Get the compose assembly map
    private ShipControlMap composeAssemblyMap(
            ServerSubLevel root,
            ShipControlMap primaryMap,
            ScmMapCompositionApi.Composition<AssemblyMapSource> composition
    ) {
        return composeAssemblyMap(
                root, primaryMap, composition, connectedShipSubLevelIndex(root));
    }

    // Get the compose assembly map
    private ShipControlMap composeAssemblyMap(
            ServerSubLevel root,
            ShipControlMap primaryMap,
            ScmMapCompositionApi.Composition<AssemblyMapSource> composition,
            Map<UUID, SubLevel> liveSubLevels
    ) {
        // ------------------------------------PROPULSION UNITS------------------------------------
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        Map<AssemblyUnitIndex, Integer> unitIndexRemap = new HashMap<>();
        Map<AssemblyUnitIdentity, Integer> unitIdentities = new LinkedHashMap<>();

        for (ScmMapCompositionApi.SelectedFragment<AssemblyMapSource> selection
                : composition.fragments()) {
            AssemblyMapSource src = selection.value();
            if (src == null) {
                continue;
            }
            UUID fragmentId = selection.fragment().fragmentId();
            for (ShipControlMap.PropulsionUnit unit : src.map().units()) {
                if (!selection.effectiveSubLevelIds().contains(unit.subLevelId())) {
                    continue;
                }
                AssemblyUnitIdentity identity = new AssemblyUnitIdentity(
                        unit.subLevelId(), unit.blockPosition(), unit.adapter());
                Integer existingIndex = unitIdentities.get(identity);
                if (existingIndex != null) {
                    unitIndexRemap.put(
                            new AssemblyUnitIndex(fragmentId, unit.index()), existingIndex);
                    continue;
                }
                int newIndex = units.size();
                unitIdentities.put(identity, newIndex);
                unitIndexRemap.put(
                        new AssemblyUnitIndex(fragmentId, unit.index()), newIndex);
                units.add(new ShipControlMap.PropulsionUnit(
                        newIndex, unit.subLevelId(), unit.blockPosition(), unit.blockId(),
                        unit.adapter(), unit.controllable(),
                        assemblyPoint(root, liveSubLevels, src, unit.rootPosition()),
                        assemblyDirection(root, liveSubLevels, src, unit.forceDirection()),
                        unit.minControl(), unit.maxControl(), unit.minThrust(),
                        unit.maxThrust(), unit.maxSpeed(), unit.samples()));
            }
        }

        // ------------------------------------ASSEMBLY COMPONENTS------------------------------------
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        Set<AssemblyBearingIdentity> bearingIdentities = new LinkedHashSet<>();
        List<ShipControlMap.DockingConnector> connectors = new ArrayList<>();
        Set<AssemblyBlockIdentity> connectorIdentities = new LinkedHashSet<>();
        Set<ShipControlMap.CrnDisplay> crnDisplays = new LinkedHashSet<>();
        Set<ShipControlMap.AccDisplay> accDisplays = new LinkedHashSet<>();
        // ------------------------------------SOURCE FRAGMENTS------------------------------------
        long updatedAt = primaryMap.updatedAt();

        for (ScmMapCompositionApi.SelectedFragment<AssemblyMapSource> selection
                : composition.fragments()) {
            AssemblyMapSource src = selection.value();
            if (src == null) {
                continue;
            }
            ShipControlMap sourceMap = src.map();
            updatedAt = Math.max(updatedAt, sourceMap.updatedAt());
            UUID fragmentId = selection.fragment().fragmentId();
            // ------------------------------------BEARING UNITS------------------------------------
            for (ShipControlMap.BearingUnit bearing : sourceMap.bearings()) {
                if (!selection.effectiveSubLevelIds().contains(
                        bearing.hostSubLevelId())) {
                    continue;
                }
                AssemblyBearingIdentity identity = new AssemblyBearingIdentity(
                        bearing.hostSubLevelId(), bearing.blockPosition(), bearing.adapter());
                if (!bearingIdentities.add(identity)) {
                    continue;
                }
                List<ShipControlMap.BearingPose> poses = new ArrayList<>();
                for (ShipControlMap.BearingPose pose : bearing.poses()) {
                    List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
                    for (ShipControlMap.BearingResponse resp : pose.responses()) {
                        Integer remapped = unitIndexRemap.get(new AssemblyUnitIndex(
                                fragmentId, resp.propulsionUnitIndex()));
                        if (remapped == null) {
                            continue;
                        }
                        responses.add(new ShipControlMap.BearingResponse(
                                remapped,
                                assemblyPoint(root, liveSubLevels, src,
                                        resp.rootPosition()),
                                assemblyDirection(root, liveSubLevels, src,
                                        resp.forceDirection()),
                                resp.maxThrust()));
                    }
                    List<ShipControlMap.AerodynamicSurface> surfaces = pose
                            .aerodynamicSurfaces().stream()
                            .filter(surface -> selection.effectiveSubLevelIds()
                                    .contains(surface.subLevelId()))
                            .map(surface -> new ShipControlMap.AerodynamicSurface(
                                    surface.surfaceIndex(), surface.subLevelId(),
                                    surface.blockPosition(), surface.blockId(),
                                    assemblyPoint(root, liveSubLevels, src,
                                            surface.rootPosition()),
                                    assemblyDirection(root, liveSubLevels, src,
                                            surface.normal()),
                                    surface.parallelDragScalar(),
                                    surface.directionlessDragScalar(),
                                    surface.liftScalar()))
                            .toList();
                    poses.add(new ShipControlMap.BearingPose(
                            pose.angleX(), pose.angleZ(), responses, surfaces,
                            pose.maxAerodynamicForce(), pose.maxAerodynamicTorque()));
                }
                bearings.add(new ShipControlMap.BearingUnit(
                        bearings.size(), bearing.hostSubLevelId(), bearing.blockPosition(),
                        bearing.blockId(), bearing.adapter(),
                        bearing.childSubLevelIds().stream()
                                .filter(selection.effectiveSubLevelIds()::contains)
                                .toList(),
                        bearing.minX(), bearing.maxX(), bearing.minZ(), bearing.maxZ(),
                        poses));
            }
            for (ShipControlMap.DockingConnector connector : sourceMap.dockingConnectors()) {
                if (!selection.effectiveSubLevelIds().contains(connector.subLevelId())
                        || !connectorIdentities.add(new AssemblyBlockIdentity(
                        connector.subLevelId(), connector.blockPosition()))) {
                    continue;
                }
                connectors.add(new ShipControlMap.DockingConnector(
                        connectors.size(), connector.subLevelId(),
                        connector.blockPosition(),
                        assemblyPoint(root, liveSubLevels, src,
                                connector.rootTipPosition()),
                        assemblyDirection(root, liveSubLevels, src,
                                connector.rootFacing())));
            }
            sourceMap.crnDisplays().stream()
                    .filter(display -> selection.effectiveSubLevelIds()
                            .contains(display.subLevelId()))
                    .forEach(crnDisplays::add);
            sourceMap.accDisplays().stream()
                    .filter(display -> selection.effectiveSubLevelIds()
                            .contains(display.subLevelId()))
                    .forEach(accDisplays::add);
        }

        // -----------------------------------------------------FINAL MAP-----------------------------------------------------
        Vec3 centerOfMass = assemblyCenterOfMass(root, primaryMap.centerOfMass());
        return new ShipControlMap(
                primaryMap.id(), primaryMap.dimension(), root.getUniqueId(),
                primaryMap.controllerPosition(), centerOfMass, units, bearings,
                connectors, List.copyOf(crnDisplays), List.copyOf(accDisplays), updatedAt);
    }

    // Get the assembly point
    private static Vec3 assemblyPoint(
            ServerSubLevel root,
            Map<UUID, SubLevel> liveSubLevels,
            AssemblyMapSource src,
            Vec3 point
    ) {
        if (!src.foreign()) {
            return finite(point);
        }
        SubLevel frame = liveSubLevels.get(src.map().rootSubLevelId());
        if (frame == null) {
            Object resolved = SubLevelBlockEntityCollector.getSubLevel(
                    root.getLevel(), src.map().rootSubLevelId());
            frame = resolved instanceof SubLevel subLevel ? subLevel : null;
        }
        return frame == null ? finite(point) : rootPosition(root, frame, point);
    }

    // Get the assembly direction
    private static Vec3 assemblyDirection(
            ServerSubLevel root,
            Map<UUID, SubLevel> liveSubLevels,
            AssemblyMapSource src,
            Vec3 dir
    ) {
        if (!src.foreign()) {
            return normalize(dir, Vec3.ZERO);
        }
        SubLevel frame = liveSubLevels.get(src.map().rootSubLevelId());
        if (frame == null) {
            Object resolved = SubLevelBlockEntityCollector.getSubLevel(
                    root.getLevel(), src.map().rootSubLevelId());
            frame = resolved instanceof SubLevel subLevel ? subLevel : null;
        }
        return frame == null
                ? normalize(dir, Vec3.ZERO)
                : rootDirection(root, frame, dir);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           CONTROL
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Claim control authority
    private boolean claimControlAuthority(
            SableAssemblyTopologyApi.Topology topology
    ) {
        Level level = controller.getLevel();
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null || !topology.available()) {
            return false;
        }
        String assemblyKey = assemblyAuthorityKey(topology);
        UUID controllerId = controllerAuthorityId(topology);
        if (claimedAssemblyAuthorityKey != null
                && !claimedAssemblyAuthorityKey.equals(assemblyKey)) {
            ScmControlAuthorityApi.release(
                    server, claimedAssemblyAuthorityKey, controllerId);
        }
        ShipControlMap localMap = map;
        int priority = (assemblyAggregator ? 100_000 : 1_000)
                + (localMap == null ? 0
                : Math.min(10_000, mapSubLevelIds(localMap).size() * 100
                + localMap.controllableUnitCount()));
        long tick = level.getGameTime();
        ScmControlAuthorityApi.ClaimResult res = ScmControlAuthorityApi.claim(
                server, assemblyKey, controllerId, priority, tick, 3L);
        claimedAssemblyAuthorityKey = assemblyKey;
        return res.granted() && !res.ownerChanged();
    }

    // Release the control authority
    private void releaseControlAuthority() {
        Level level = controller.getLevel();
        MinecraftServer server = level == null ? null : level.getServer();
        if (server != null && claimedAssemblyAuthorityKey != null
                && controllerAuthorityId != null) {
            ScmControlAuthorityApi.release(
                    server, claimedAssemblyAuthorityKey, controllerAuthorityId);
        }
        claimedAssemblyAuthorityKey = null;
    }

    // Get the controller authority id
    private UUID controllerAuthorityId(SableAssemblyTopologyApi.Topology topology) {
        if (controllerAuthorityId == null) {
            UUID ownerId = containingServerSubLevel() == null
                    ? topology.rootSubLevelId()
                    : containingServerSubLevel().getUniqueId();
            String key = String.valueOf(ownerId) + ':'
                    + controller.getBlockPos().asLong();
            controllerAuthorityId = UUID.nameUUIDFromBytes(
                    key.getBytes(StandardCharsets.UTF_8));
        }
        return controllerAuthorityId;
    }

    // Get the assembly authority key
    private static String assemblyAuthorityKey(
            SableAssemblyTopologyApi.Topology topology
    ) {
        StringBuilder key = new StringBuilder("scm-assembly");
        topology.loadedBodyIds().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(id -> key.append(':').append(id));
        return UUID.nameUUIDFromBytes(
                key.toString().getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Map the sublevel ids
    private static Set<UUID> mapSubLevelIds(ShipControlMap src) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(src.rootSubLevelId());
        src.units().forEach(unit -> ids.add(unit.subLevelId()));
        src.bearings().forEach(bearing -> {
            ids.add(bearing.hostSubLevelId());
            ids.addAll(bearing.childSubLevelIds());
        });
        src.dockingConnectors().forEach(connector ->
                ids.add(connector.subLevelId()));
        return Set.copyOf(ids);
    }

    // Update the control
    private void tickControl() {
        if (map == null) {
            releaseNavResidency();
            return;
        }
        ServerSubLevel currentRoot = containingServerSubLevel();
        if (currentRoot == null || !map.rootSubLevelId().equals(currentRoot.getUniqueId())) {
            clearThrusterProtection();
            releaseControlActuators();
            releaseNavResidency();
            phase = Phase.ERROR;
            status = "Contraption changed; initialize the control module again";
            return;
        }
        rootSubLevel = currentRoot;

        ShipControlMap assemblyMap = activeAssemblyMap;
        SableAssemblyTopologyApi.Topology topology = assemblyTopology(currentRoot);
        SableAssemblyDynamicsApi.Snapshot dynamics =
                SableAssemblyDynamicsApi.sample(topology);
        Vec3 liveCenterOfMass = dynamics.massAvailable()
                ? dynamics.centerOfMass()
                : liveCenterOfMass(currentRoot, java.util.Objects.requireNonNull(map).centerOfMass());
        Telemetry telemetry = telemetry(currentRoot, liveCenterOfMass);
        cacheTelemetry(currentRoot, telemetry);
        updateNavResidency(currentRoot);
        if (!telemetry.available()) {
            releaseControlActuators();
            return;
        }

        if (!hasActiveControlCommands()) {
            airshipAttitudeHoldTargets.clear();
            releaseControlAuthority();
            releaseControlActuators();
            return;
        }
        if (!claimControlAuthority(topology)) {
            releaseControlActuators();
            return;
        }

        if (assemblyMap == null) {
            releaseControlActuators();
            return;
        }
        ShipControlMap effectiveMap = liveGeometryMap(
                currentRoot, assemblyMap, liveCenterOfMass);
        ControlDemand demand = demandFor(telemetry, effectiveMap, dynamics, topology);
        // Autonomous commands initially expose every channel they may need so
        // demandFor() can plan from the complete calibrated map. Once the
        // current force/torque demand is known, retain only the matching side
        // of every opposing action pair. In particular this prevents a
        // scheduled car from asserting both faces of one Directional Gearshift.
        effectiveMap = actionRoutedGeometryMap(
                effectiveMap, activeControlActionTypes(demand, effectiveMap));
        List<ShipControlAllocator.CarriageDemand> carriageDemands =
                carriageDemands(topology, dynamics, demand.torque());
        applyBearingPlan(effectiveMap, demand, liveCenterOfMass, telemetry,
                topology, carriageDemands);
        boolean prioritizeTranslation = demand.force().lengthSqr() > 1.0E-12D;
        ShipControlAllocator.Allocation allocation = ShipControlAllocator.allocateArticulated(
                effectiveMap, carriageDemands, demand.force(),
                demand.preferredDirection(), prioritizeTranslation, allocationWorkspace);
        applyAllocation(effectiveMap, allocation.controls());
        applyScmFaceActionControls(effectiveMap, demand);
        if (isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            applyCarControl(effectiveMap, demand, telemetry, topology);
        }
        applyAccelerationControls(effectiveMap, demand);
        // Apply the Brake group after every propulsion path. A block must not
        // be left at its acceleration value merely because it was also seen by
        // an allocator or a legacy drivetrain adapter earlier in this tick.
        applyConfiguredBrakeControls(effectiveMap, demand);
        updateCommandCompletion(telemetry);
    }

    // Get the carriage demands
    private static List<ShipControlAllocator.CarriageDemand> carriageDemands(
            SableAssemblyTopologyApi.Topology topology,
            SableAssemblyDynamicsApi.Snapshot dynamics,
            Vec3 requestedTorque
    ) {
        if (topology == null || !topology.available()) {
            return List.of();
        }
        List<ShipControlAllocator.CarriageDemand> demands = new ArrayList<>();
        for (SableAssemblyTopologyApi.CarriagePartition partition
                : topology.carriagePartitions()) {
            double mass = 0.0D;
            Vec3 weightedCenter = Vec3.ZERO;
            for (UUID bodyId : partition.bodyIds()) {
                SableAssemblyDynamicsApi.BodyDynamics body = dynamics.body(bodyId)
                        .orElse(null);
                if (body == null || !body.massAvailable()) {
                    continue;
                }
                mass += body.mass();
                weightedCenter = weightedCenter.add(
                        body.centerOfMass().scale(body.mass()));
            }
            Vec3 centerOfMass = mass > 1.0E-9D
                    ? weightedCenter.scale(1.0D / mass)
                    : dynamics.body(partition.rootSubLevelId())
                    .map(SableAssemblyDynamicsApi.BodyDynamics::centerOfMass)
                    .orElse(dynamics.centerOfMass());
            demands.add(new ShipControlAllocator.CarriageDemand(
                    partition.rootSubLevelId(), new LinkedHashSet<>(partition.bodyIds()),
                    centerOfMass, mass, requestedTorque, partition.primary()));
        }
        return List.copyOf(demands);
    }

    // Publish the mapped CRN displays
    private void publishMappedCrnDisplays() {
        ShipControlMap currentMap = effectiveAssemblyMap();
        Level level = controller.getLevel();
        if (currentMap == null || level == null
                || currentMap.crnDisplays().isEmpty() && currentMap.accDisplays().isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (!intervalElapsed(gameTime, lastCrnDisplayPublishTick, 20L)) {
            return;
        }
        lastCrnDisplayPublishTick = gameTime;
        RailwayNavigatorGraphCompat.ShipDisplayData displayData =
                controller.getCrnShipDisplayData(currentMap.id());
        boolean serviceAvailable = displayData.scheduleActive() && displayData.pilotPresent();
        for (ShipControlMap.AccDisplay display : currentMap.accDisplays()) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, display.subLevelId(), display.blockPosition());
            if (!(blockEntity instanceof AccDisplayBlockEntity accDisplay)) {
                continue;
            }
            boolean displayAvailable = ShipInformationDisplayModes.isAvailable(
                    accDisplay.displayMode(),
                    displayData.scheduleActive(),
                    displayData.pilotPresent());
            if (displayAvailable) {
                accDisplay.acceptMappedShipInformation(displayData);
            } else {
                accDisplay.clearMappedShipInformation();
            }
        }
        if (!serviceAvailable) {
            return;
        }
        Set<ShipControlMap.CrnDisplay> published = new HashSet<>();
        for (ShipControlMap.CrnDisplay display : currentMap.crnDisplays()) {
            if (!published.add(display)) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, display.subLevelId(), display.blockPosition());
            RailwayNavigatorGraphCompat.publishShipTelemetry(blockEntity, displayData);
        }
    }

    // Apply the bearing plan
    private void applyBearingPlan(
            ShipControlMap currentMap,
            ControlDemand demand,
            Vec3 centerOfMass,
            Telemetry telemetry,
            SableAssemblyTopologyApi.Topology topology,
            List<ShipControlAllocator.CarriageDemand> carriageDemands
    ) {
        if (currentMap.bearings().isEmpty()) {
            return;
        }
        // ------------------------------------AERODYNAMIC LOAD------------------------------------
        Vec3 relativeAirflow = relativeAirflowRoot(telemetry, centerOfMass);
        Vec3 angularVelocity = worldDirectionToRoot(telemetry.angularVelocity());
        double airPressure = airPressureAt(telemetry.position());
        for (ShipControlMap.BearingUnit bearing : currentMap.bearings()) {
            if (controlBearings.containsKey(bearing.index())) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), bearing.hostSubLevelId(), bearing.blockPosition());
            BearingActuator actuator = bearingActuatorForStored(blockEntity, bearing.adapter());
            if (actuator != null) {
                controlBearings.put(bearing.index(), actuator);
            }
        }
        List<ShipControlMap.BearingUnit> availableBearings = new ArrayList<>();
        Map<Integer, ShipBearingPlanner.Pose> currentPoses = new LinkedHashMap<>();
        for (ShipControlMap.BearingUnit bearing : currentMap.bearings()) {
            BearingActuator actuator = controlBearings.get(bearing.index());
            if (actuator == null) {
                continue;
            }
            availableBearings.add(bearing);
            currentPoses.put(bearing.index(), actuator.currentPose());
        }
        Map<Integer, Integer> poseSelections = new LinkedHashMap<>();
        if (topology != null && topology.available()
                && !topology.carriagePartitions().isEmpty()) {
            Map<UUID, ShipControlAllocator.CarriageDemand> demandsByCarriage =
                    carriageDemands == null ? Map.of() : carriageDemands.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    ShipControlAllocator.CarriageDemand::carriageId,
                                    java.util.function.Function.identity(),
                                    (first, second) -> first,
                                    LinkedHashMap::new));
            for (SableAssemblyTopologyApi.CarriagePartition partition
                    : topology.carriagePartitions()) {
                List<ShipControlMap.BearingUnit> partitionBearings =
                        availableBearings.stream()
                                .filter(bearing -> partition.contains(
                                        bearing.hostSubLevelId()))
                                .toList();
                if (partitionBearings.isEmpty()) {
                    continue;
                }
                ShipControlAllocator.CarriageDemand carriageDemand =
                        demandsByCarriage.get(partition.rootSubLevelId());
                Vec3 partitionCenter = carriageDemand == null
                        ? centerOfMass : carriageDemand.centerOfMass();
                Vec3 partitionTorque = partition.primary()
                        ? demand.torque()
                        : new Vec3(demand.torque().x, 0.0D, demand.torque().z);
                poseSelections.putAll(ShipBearingPlanner.selectPoses(
                        partitionBearings, demand.force(), partitionTorque,
                        partitionCenter, currentPoses,
                        relativeAirflowRoot(telemetry, partitionCenter),
                        angularVelocity, airPressure, partition.primary()));
            }
        } else {
            poseSelections.putAll(ShipBearingPlanner.selectPoses(
                    availableBearings, demand.force(), demand.torque(), centerOfMass,
                    currentPoses, relativeAirflow, angularVelocity, airPressure));
        }
        // ------------------------------------ACTUATOR OUTPUT------------------------------------
        for (ShipControlMap.BearingUnit bearing : availableBearings) {
            BearingActuator actuator = controlBearings.get(bearing.index());
            int poseIndex = poseSelections.getOrDefault(bearing.index(), -1);
            if (poseIndex < 0 || poseIndex >= bearing.poses().size()) {
                continue;
            }
            ShipControlMap.BearingPose storedPose = bearing.poses().get(poseIndex);
            ShipBearingPlanner.Pose selectedPose =
                    new ShipBearingPlanner.Pose(storedPose.angleX(), storedPose.angleZ());
            Integer prev = selectedBearingPoses.put(bearing.index(), poseIndex);
            if (prev == null || prev != poseIndex || !actuator.atPose(selectedPose)) {
                actuator.applyPose(selectedPose);
            }
        }
    }

    // Get the relative airflow root
    private Vec3 relativeAirflowRoot(Telemetry telemetry, Vec3 centerOfMass) {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        Level level = root == null ? controller.getLevel() : root.getLevel();
        if (level == null || !telemetry.available()) {
            return Vec3.ZERO;
        }
        Vec3 rootSample = rootBodyCenterOfMass(root, centerOfMass);
        Vec3 angularRoot = worldDirectionToRoot(telemetry.angularVelocity());
        Vec3 sampledVelocity;
        try {
            Vector3d relative = SubLevelHelper.getVelocityRelativeToAir(
                    level, new Vector3d(rootSample.x, rootSample.y, rootSample.z), new Vector3d());
            sampledVelocity =
                    worldDirectionToRoot(new Vec3(relative.x, relative.y, relative.z));
        } catch (RuntimeException err) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Could not sample Sable relative airflow; using rigid-body velocity", err);
            Vec3 sampleWorld = worldPosition(root.logicalPose(), rootSample);
            Vec3 pointVelocity = velocityAtPoint(
                    telemetry.velocity(), telemetry.angularVelocity(),
                    telemetry.position(), sampleWorld);
            sampledVelocity = worldDirectionToRoot(pointVelocity);
        }
        return sampledVelocity.add(
                angularRoot.cross(finite(centerOfMass).subtract(rootSample)));
    }

    // Get the air pressure
    private double airPressureAt(Vec3 worldPosition) {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        Level level = root == null ? controller.getLevel() : root.getLevel();
        if (level == null) {
            return 1.0D;
        }
        double pressure = DimensionPhysicsData.getAirPressure(
                level, new Vector3d(worldPosition.x, worldPosition.y, worldPosition.z));
        return Double.isFinite(pressure) && pressure >= 0.0D ? pressure : 1.0D;
    }

    // Get the live geometry map
    private ShipControlMap liveGeometryMap(
            ServerSubLevel root,
            ShipControlMap currentMap,
            Vec3 centerOfMass
    ) {
        if (!Objects.equals(calDirMapId, currentMap.id())) {
            reversedCalDirs.clear();
            calDirMapId = currentMap.id();
        }
        Map<UUID, SubLevel> liveSubLevels = connectedShipSubLevelIndex(root);
        List<ShipControlMap.PropulsionUnit> liveUnits = new ArrayList<>(currentMap.units().size());
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        Set<String> activeActions = activeControlActionTypes();
        boolean limitToActionGroups = configuration.isConfiguredFor(currentMap)
                && configuration.hasActionBindings() && !activeActions.isEmpty();
        Set<ScmConfigurationProfile.UnitReference> selectedUnits = limitToActionGroups
                ? configuration.unitsForExplicitActions(activeActions) : Set.of();
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            // A map without explicit routing uses its calibrated units as the
            // implicit control group. Optional Acceleration and Brake groups
            // therefore remain optional for direct-thrust craft.
            // A profile blacklist is applied after all dynamic Sable/adapter data is
            // refreshed. The allocator therefore sees a real zero-capacity unit and
            // cannot accidentally select a block the player excluded in calibration.
            boolean excludedByProfile = configuration.excludedUnits().stream()
                    .anyMatch(reference -> configurationReferenceMatchesUnit(reference, unit));
            boolean outsideActionGroup = limitToActionGroups && selectedUnits.stream()
                    .noneMatch(reference -> configurationReferenceMatchesUnit(reference, unit));
            if (excludedByProfile || outsideActionGroup) {
                liveUnits.add(withZeroLiveCapacity(unit));
                continue;
            }
            SubLevel subLevel = liveSubLevels.get(unit.subLevelId());
            if (subLevel == null) {
                liveUnits.add(withZeroLiveCapacity(unit));
                continue;
            }
            Actuator actuator = controlActuators.get(unit.index());
            if (actuator != null && !actuator.isAvailable()) {
                controlActuators.remove(unit.index());
                appliedControlValues.remove(unit.index());
                actuator = null;
            }
            if (actuator == null) {
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        controller.getLevel(), unit.subLevelId(), unit.blockPosition());
                actuator = actuatorForStored(blockEntity, unit, controller.getLevel());
                if (actuator != null && actuator.isAvailable() && actuator.controllable()) {
                    controlActuators.put(unit.index(), actuator);
                }
            }
            if (actuator == null || !actuator.isAvailable()
                    || unit.controllable() && !actuator.controllable()) {
                liveUnits.add(withZeroLiveCapacity(unit));
                continue;
            }
            Vec3 livePosition = actuator.usesPhysicalCalibration()
                    ? unit.rootPosition()
                    : rootPosition(root, subLevel, actuator.localForcePosition());
            Vec3 liveDirection = actuator.usesPhysicalCalibration()
                    ? unit.forceDirection()
                    : rootDirection(root, subLevel, actuator.localForceDirection());
            Actuator resolvedActuator = actuator;
            if (!actuator.usesPhysicalCalibration()
                    && reversedCalDirs.computeIfAbsent(unit.index(), ignored ->
                    isCalDirReversed(unit, resolvedActuator))) {
                liveDirection = liveDirection.scale(-1.0D);
            }
            double liveMaximumThrust = Math.max(0.0D, actuator.liveMaximumThrust(unit));
            // A configured face is a declared direct-control channel, not a
            // measured propulsion source. It must remain allocatable when the
            // physics sampler cannot observe an effect (for example, a wheel
            // resting on terrain). This also repairs already-saved maps which
            // were created before direct face capacity was retained at build
            // time. The declaration remains scoped to its active action group.
            boolean selectedFaceBinding = limitToActionGroups && selectedUnits.stream()
                    .anyMatch(reference -> reference.usesFaceControl()
                            && configurationReferenceMatchesUnit(reference, unit));
            if (selectedFaceBinding) {
                liveMaximumThrust = Math.max(liveMaximumThrust,
                        actuator.theoreticalMaxThrust());
            }
            liveUnits.add(new ShipControlMap.PropulsionUnit(
                    unit.index(), unit.subLevelId(), unit.blockPosition(), unit.blockId(),
                    unit.adapter(), unit.controllable(), livePosition, liveDirection,
                    unit.minControl(), unit.maxControl(),
                    Math.min(unit.minThrust(), liveMaximumThrust), liveMaximumThrust,
                    actuator.liveMaximumSpeed(unit), unit.samples()));
        }
        return new ShipControlMap(
                currentMap.id(), currentMap.dimension(), currentMap.rootSubLevelId(),
                currentMap.controllerPosition(), centerOfMass, liveUnits,
                currentMap.bearings(), currentMap.dockingConnectors(),
                currentMap.crnDisplays(), currentMap.accDisplays(),
                currentMap.updatedAt());
    }

    // Apply the current action routing without re-resolving live block entities.
    // This runs after demand calculation, when an autonomous command has chosen
    // the direction it actually needs. The initial live map still contains all
    // of the command's possible channels for calibration and demand planning.
    private ShipControlMap actionRoutedGeometryMap(
            ShipControlMap currentMap, Set<String> activeActions
    ) {
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        if (!configuration.isConfiguredFor(currentMap)
                || !configuration.hasActionBindings() || activeActions.isEmpty()) {
            return currentMap;
        }
        Set<ScmConfigurationProfile.UnitReference> selectedUnits =
                configuration.unitsForExplicitActions(activeActions);
        List<ShipControlMap.PropulsionUnit> routedUnits =
                new ArrayList<>(currentMap.units().size());
        boolean changed = false;
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            boolean excludedByProfile = configuration.excludedUnits().stream()
                    .anyMatch(reference -> configurationReferenceMatchesUnit(reference, unit));
            boolean outsideActionGroup = selectedUnits.stream()
                    .noneMatch(reference -> configurationReferenceMatchesUnit(reference, unit));
            if (excludedByProfile || outsideActionGroup) {
                routedUnits.add(withZeroLiveCapacity(unit));
                changed |= unit.maxThrust() > 1.0E-9D;
            } else {
                routedUnits.add(unit);
            }
        }
        if (!changed) {
            return currentMap;
        }
        return new ShipControlMap(
                currentMap.id(), currentMap.dimension(), currentMap.rootSubLevelId(),
                currentMap.controllerPosition(), currentMap.centerOfMass(), routedUnits,
                currentMap.bearings(), currentMap.dockingConnectors(),
                currentMap.crnDisplays(), currentMap.accDisplays(),
                currentMap.updatedAt());
    }

    // Return the explicit SCM action channels currently asking for allocation.
    // Direct graph controls use exactly their own action. Autonomous commands
    // first expose their possible directional channels so their demand can be
    // planned from the complete map; actionRoutedGeometryMap() then narrows
    // each opposing pair to the side demanded this tick.
    private Set<String> activeControlActionTypes() {
        return activeControlActionTypes(null);
    }

    // Return the action channels currently asking for allocation. A completed
    // demand lets autonomous controls select only the requested side of each
    // signed axis, while direct graph commands remain their exact action.
    private Set<String> activeControlActionTypes(@Nullable ControlDemand demand) {
        return activeControlActionTypes(demand, null);
    }

    // Return the action channels with optional measured authority for resolving
    // autonomous opposing groups. Direct commands and car drivetrain faces
    // retain their explicit authored direction.
    private Set<String> activeControlActionTypes(
            @Nullable ControlDemand demand,
            @Nullable ShipControlMap currentMap
    ) {
        if (activeCommands.isEmpty()) {
            return Set.of();
        }
        Set<String> actions = new LinkedHashSet<>();
        for (ActiveShipCommand command : activeCommands.values()) {
            String type = command.type();
            if ("ship_initialize".equals(type) || "ship_scan_configuration".equals(type)
                    || "ship_stop".equals(type)) {
                continue;
            }
            actions.addAll(routingActionsFor(type));
        }
        if (demand == null) {
            return actions;
        }
        actions = directionalActionTypes(actions, demand, currentMap);
        // Navigation braking is a first-class routed action. It is not an
        // inferred negative forward force: the player-configured Brake group
        // must be selected whenever the distance/collision speed plan asks
        // for braking.
        if (activeBrakeStrength(demand) > 1.0E-5D) {
            actions.add("ship_brake");
            actions.add("ship_decelerate");
        }
        return actions;
    }

    // Limit opposing action groups to the side represented by the current
    // control demand. Generic signed actions (such as ship_yaw) remain in the
    // set, so a block deliberately bound to the generic group continues to
    // receive its normal analog control.
    private Set<String> directionalActionTypes(
            Set<String> activeActions,
            ControlDemand demand,
            @Nullable ShipControlMap currentMap
    ) {
        if (activeActions.isEmpty()) {
            return Set.of();
        }
        Set<String> actions = new LinkedHashSet<>(activeActions);
        Vec3 forward = controllerForwardRoot();
        Vec3 up = controllerUpRoot();
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        double forwardDemand = Math.abs(demand.driveDirection()) > ACTION_DIRECTION_EPSILON
                ? demand.driveDirection() : demand.force().dot(forward);
        // Directional group names are an authored controller-frame contract.
        // Calibration describes the units inside each group; it must not rename
        // a player's Forward group to Backward (or Left to Right) because a
        // sampled force basis happens to be inverted. Manual graph controls and
        // autonomous navigation now select the exact same authored side.
        boolean measuredAutonomousRouting = false;
        retainDemandedAction(actions, "ship_forward", "ship_backward", forwardDemand);
        // `ship_reverse` was the original negative longitudinal action. It
        // remains a valid persisted graph/profile binding, while
        // `ship_backward` is its current UI name. A scheduled command must
        // therefore retain both names for negative demand and suppress both
        // names for positive or neutral demand. Otherwise an older profile
        // can have every usable propulsion unit masked even though the
        // command itself was accepted.
        if (!measuredAutonomousRouting
                && forwardDemand >= -ACTION_DIRECTION_EPSILON) {
            actions.remove("ship_reverse");
        }
        retainAutonomousActionPair(actions, "ship_strafe_right", "ship_strafe_left",
                demand.force(), false, demand.force().dot(right), currentMap,
                measuredAutonomousRouting);
        retainAutonomousActionPair(actions, "ship_ascend", "ship_descend",
                demand.force(), false, demand.force().dot(up), currentMap,
                measuredAutonomousRouting);
        retainAutonomousActionPair(actions, "ship_yaw_right", "ship_yaw_left",
                demand.torque(), true, demand.torque().dot(up), currentMap,
                measuredAutonomousRouting);
        retainAutonomousActionPair(actions, "ship_pitch_up", "ship_pitch_down",
                demand.torque(), true, demand.torque().dot(right), currentMap,
                measuredAutonomousRouting);
        retainAutonomousActionPair(actions, "ship_roll_right", "ship_roll_left",
                demand.torque(), true, demand.torque().dot(forward), currentMap,
                measuredAutonomousRouting);
        return actions;
    }

    // Resolve one autonomous signed pair from calibrated force/torque authority.
    // This makes a group mean "the blocks which cause this motion", matching
    // the SCM configuration UI, even when a controller/sub-level basis has a
    // reversed axis. Cars are deliberately excluded because their authored
    // gearshift faces may have no meaningful propulsion vector at rest.
    private void retainAutonomousActionPair(
            Set<String> actions,
            String positiveAction,
            String negativeAction,
            Vec3 requestedWrench,
            boolean torque,
            double signedFallback,
            @Nullable ShipControlMap currentMap,
            boolean measuredAutonomousRouting
    ) {
        if (measuredAutonomousRouting && currentMap != null) {
            retainMostAuthoritativeActions(actions, positiveAction,
                    List.of(negativeAction), requestedWrench, torque,
                    signedFallback, currentMap);
        } else {
            retainDemandedAction(actions, positiveAction, negativeAction, signedFallback);
        }
    }

    // Keep the authored side whose calibrated units can actually produce the
    // requested wrench. Fall back to the controller-frame sign for empty,
    // uncalibrated or tied groups.
    private void retainMostAuthoritativeActions(
            Set<String> actions,
            String positiveAction,
            List<String> negativeActions,
            Vec3 requestedWrench,
            boolean torque,
            double signedFallback,
            ShipControlMap currentMap
    ) {
        if (!actions.contains(positiveAction)
                && negativeActions.stream().noneMatch(actions::contains)) {
            return;
        }
        if (requestedWrench == null
                || requestedWrench.lengthSqr() <= ACTION_DIRECTION_EPSILON
                * ACTION_DIRECTION_EPSILON) {
            actions.remove(positiveAction);
            negativeActions.forEach(actions::remove);
            return;
        }
        double positiveAuthority = configuredActionAuthority(
                positiveAction, requestedWrench, torque, currentMap);
        double negativeAuthority = negativeActions.stream()
                .mapToDouble(action -> configuredActionAuthority(
                        action, requestedWrench, torque, currentMap))
                .max().orElse(0.0D);
        if (positiveAuthority > negativeAuthority + 1.0E-6D) {
            negativeActions.forEach(actions::remove);
        } else if (negativeAuthority > positiveAuthority + 1.0E-6D) {
            actions.remove(positiveAction);
        } else if (signedFallback > ACTION_DIRECTION_EPSILON) {
            negativeActions.forEach(actions::remove);
        } else if (signedFallback < -ACTION_DIRECTION_EPSILON) {
            actions.remove(positiveAction);
        } else {
            actions.remove(positiveAction);
            negativeActions.forEach(actions::remove);
        }
    }

    // Sum only positive authority because the non-negative allocator cannot use
    // a unit whose calibrated wrench points away from the current request.
    private double configuredActionAuthority(
            String action,
            Vec3 requestedWrench,
            boolean torque,
            ShipControlMap currentMap
    ) {
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        Set<ScmConfigurationProfile.UnitReference> references =
                configuration.unitsForActions(Set.of(action));
        if (references.isEmpty()) {
            return 0.0D;
        }
        Vec3 request = normalize(requestedWrench, Vec3.ZERO);
        double authority = 0.0D;
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D
                    || references.stream().noneMatch(reference ->
                    configurationReferenceMatchesUnit(reference, unit))) {
                continue;
            }
            Vec3 available = torque
                    ? unit.torqueDirection(currentMap.centerOfMass())
                    : unit.forceDirection().scale(unit.maxThrust());
            authority += Math.max(0.0D, available.dot(request));
        }
        return authority;
    }

    // Keep the positive or negative action for a signed demand. At rest both
    // sides are removed: a face binding represents a physical input and must
    // not stay energised simply because an autonomous command is still active.
    private static void retainDemandedAction(
            Set<String> actions, String positiveAction, String negativeAction,
            double signedDemand
    ) {
        if (signedDemand > ACTION_DIRECTION_EPSILON) {
            actions.remove(negativeAction);
        } else if (signedDemand < -ACTION_DIRECTION_EPSILON) {
            actions.remove(positiveAction);
        } else {
            actions.remove(positiveAction);
            actions.remove(negativeAction);
        }
    }

    private static Set<String> routingActionsFor(String commandType) {
        return switch (commandType) {
            case "ship_stabilize" -> Set.of(
                    "ship_stabilize",
                    "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan",
                    "ship_pitch", "ship_pitch_down", "ship_pitch_up", "ship_tilt",
                    "ship_roll", "ship_roll_left", "ship_roll_right");
            case "ship_hover", "ship_climb" -> Set.of(
                    commandType,
                    "ship_ascend", "ship_descend",
                    "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan",
                    "ship_pitch", "ship_pitch_down", "ship_pitch_up", "ship_tilt",
                    "ship_roll", "ship_roll_left", "ship_roll_right");
            case "ship_face" -> Set.of(
                    "ship_face",
                    "ship_ascend", "ship_descend",
                    "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan",
                    "ship_pitch", "ship_pitch_down", "ship_pitch_up", "ship_tilt",
                    "ship_roll", "ship_roll_left", "ship_roll_right");
            case "ship_dock", "ship_navigate", "ship_follow" -> Set.of(
                    commandType,
                    // Retired control nodes are still valid persisted action
                    // bindings. Keep their groups reachable from autonomous
                    // navigation; this preserves existing calibrated ships
                    // without making unassigned units eligible.
                    "ship_accelerate",
                    ScmConfigurationProfile.ACCELERATION_ACTION,
                    "ship_forward", "ship_backward", "ship_reverse",
                    "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                    "ship_ascend", "ship_descend",
                    "ship_yaw", "ship_yaw_left", "ship_yaw_right", "ship_pan",
                    "ship_pitch", "ship_pitch_down", "ship_pitch_up", "ship_tilt",
                    "ship_roll", "ship_roll_left", "ship_roll_right");
            // Acceleration is an analogue drive-chain channel, not a synonym
            // for forward. Every direct translational action must expose it,
            // while the action-specific group selects the physical direction.
            case "ship_accelerate", "ship_forward", "ship_reverse", "ship_backward",
                 "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                 "ship_ascend", "ship_descend" ->
                    Set.of(commandType, ScmConfigurationProfile.ACCELERATION_ACTION);
            default -> commandType == null || commandType.isBlank()
                    ? Set.of() : Set.of(commandType);
        };
    }

    // Match a player binding to one runtime map unit. Face-bound SCM probes
    // carry their face in the persisted adapter id, allowing e.g. the two
    // Directional Gearshift redstone inputs to be assigned to different graph
    // functions without one group waking the other side.
    private boolean configurationReferenceMatchesUnit(
            ScmConfigurationProfile.UnitReference reference,
            ShipControlMap.PropulsionUnit unit
    ) {
        if (reference == null || unit == null || !reference.matches(unit)) {
            return false;
        }
        if (!reference.usesFaceControl()) {
            return true;
        }
        String suffix = ":face_" + reference.face().getSerializedName();
        if (unit.adapter().endsWith(suffix)) {
            return true;
        }
        // Preserve maps saved before face-qualified SCM adapter ids existed.
        if (!unit.adapter().equals("scm:directional_gearshift_forward")
                && !unit.adapter().equals("scm:directional_gearshift_reverse")) {
            return !unit.adapter().contains(":face_");
        }
        SubLevel subLevel = controller.getLevel() == null ? null
                : SableLevelApi.subLevel(controller.getLevel(), reference.subLevelId());
        if (subLevel == null || subLevel.isRemoved()) {
            return false;
        }
        BlockState state = subLevel.getLevel().getBlockState(reference.blockPosition());
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        Direction leftFace = state.getValue(BlockStateProperties.FACING);
        return unit.adapter().endsWith("_forward")
                ? reference.face().equals(leftFace.getOpposite())
                : reference.face().equals(leftFace);
    }

    // Copy the ship control module with the zero live capacity
    private static ShipControlMap.PropulsionUnit withZeroLiveCapacity(
            ShipControlMap.PropulsionUnit unit
    ) {
        return new ShipControlMap.PropulsionUnit(
                unit.index(), unit.subLevelId(), unit.blockPosition(), unit.blockId(),
                unit.adapter(), unit.controllable(), unit.rootPosition(),
                unit.forceDirection(), unit.minControl(), unit.maxControl(),
                0.0D, 0.0D, 0.0D, unit.samples());
    }

    // Get the live center of mass
    private Vec3 liveCenterOfMass(ServerSubLevel root, Vec3 fallback) {
        return assemblyCenterOfMass(root, fallback);
    }

    // Resolve the live center of mass
    static Vec3 resolveLiveCenterOfMass(double mass, @Nullable Vector3dc center, Vec3 fallback) {
        if (!Double.isFinite(mass) || mass <= 0.0D || center == null
                || !Double.isFinite(center.x())
                || !Double.isFinite(center.y())
                || !Double.isFinite(center.z())) {
            return fallback;
        }
        return new Vec3(center.x(), center.y(), center.z());
    }

    // Resolve the combined center of mass
    static Vec3 resolveCombinedCenterOfMass(
            Collection<MassPoint> massPoints,
            Vec3 fallback
    ) {
        if (massPoints == null || massPoints.isEmpty()) {
            return fallback;
        }
        Vec3 weighted = Vec3.ZERO;
        double totalMass = 0.0D;
        for (MassPoint massPoint : massPoints) {
            if (massPoint == null || !Double.isFinite(massPoint.mass())
                    || massPoint.mass() <= 0.0D) {
                continue;
            }
            Vec3 pos = finite(massPoint.rootPosition());
            weighted = weighted.add(pos.scale(massPoint.mass()));
            totalMass += massPoint.mass();
        }
        return totalMass <= 1.0E-9D ? fallback : weighted.scale(1.0D / totalMass);
    }

    // Get the root body center of mass
    private static Vec3 rootBodyCenterOfMass(ServerSubLevel root, Vec3 fallback) {
        if (root == null || root.getMassTracker() == null) {
            return finite(fallback);
        }
        return resolveLiveCenterOfMass(
                root.getMassTracker().getMass(),
                root.getMassTracker().getCenterOfMass(),
                finite(fallback));
    }

    // Check if this is cal dir reversed
    private static boolean isCalDirReversed(
            ShipControlMap.PropulsionUnit unit,
            Actuator actuator
    ) {
        double observedSign = unit.samples().stream()
                .max(Comparator.comparingDouble(sample -> Math.abs(sample.thrust())))
                .map(sample -> Math.signum(sample.thrust()))
                .orElse(0.0D);
        return observedSign != 0.0D && observedSign * actuator.expectedThrustSign() < 0.0D;
    }

    // Get the demand
    private ControlDemand demandFor(
            Telemetry telemetry,
            ShipControlMap effectiveMap,
            SableAssemblyDynamicsApi.Snapshot dynamics,
            SableAssemblyTopologyApi.Topology topology
    ) {
        Vec3 forward = controllerForwardRoot();
        Vec3 up = controllerUpRoot();
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 force = Vec3.ZERO;
        Vec3 torque = Vec3.ZERO;
        GravityCompensation gravity = gravityCompensation(effectiveMap);
        Vec3 preferredDirection = Vec3.ZERO;
        double driveDirection = 0.0D;
        double driveStrength = 0.0D;
        double brakeStrength = 0.0D;
        Vec3 localVelocity = worldDirectionToRoot(telemetry.velocity());
        double uprightStabilizationStrength = 0.0D;
        double attitudeLockStrength = 0.0D;
        Vec3 attitudeLockTarget = null;
        boolean compensateGravity = false;
        // -----------------------------------------------------ACTIVE COMMANDS---------------------------------------------------

        for (Map.Entry<String, ActiveShipCommand> entry : activeCommands.entrySet()) {
            ActiveShipCommand command = entry.getValue();
            switch (command.type()) {
                case "ship_yaw", "ship_pan" -> torque = torque.add(up.scale(command.amount()));
                case "ship_yaw_right" -> torque = torque.add(up.scale(Math.abs(command.amount())));
                case "ship_yaw_left" -> torque = torque.add(up.scale(-Math.abs(command.amount())));
                case "ship_pitch", "ship_tilt" -> torque = torque.add(right.scale(command.amount()));
                case "ship_pitch_up" -> torque = torque.add(right.scale(Math.abs(command.amount())));
                case "ship_pitch_down" -> torque = torque.add(right.scale(-Math.abs(command.amount())));
                case "ship_roll" -> torque = torque.add(forward.scale(command.amount()));
                case "ship_roll_right" -> torque = torque.add(forward.scale(Math.abs(command.amount())));
                case "ship_roll_left" -> torque = torque.add(forward.scale(-Math.abs(command.amount())));
                case "ship_accelerate" -> {
                    force = force.add(forward.scale(command.amount()));
                    driveDirection += command.amount();
                    driveStrength = Math.max(driveStrength, Math.abs(command.amount()));
                }
                case "ship_forward" -> {
                    force = force.add(forward.scale(Math.abs(command.amount())));
                    driveDirection += Math.abs(command.amount());
                    driveStrength = Math.max(driveStrength, Math.abs(command.amount()));
                }
                case "ship_reverse" -> {
                    force = force.add(forward.scale(-Math.abs(command.amount())));
                    driveDirection -= Math.abs(command.amount());
                    driveStrength = Math.max(driveStrength, Math.abs(command.amount()));
                }
                case "ship_backward" -> {
                    force = force.add(forward.scale(-Math.abs(command.amount())));
                    driveDirection -= Math.abs(command.amount());
                    driveStrength = Math.max(driveStrength, Math.abs(command.amount()));
                }
                case "ship_strafe" -> {
                    double strafeError = command.amount() - localVelocity.dot(right);
                    double strafeDemand = Mth.clamp(strafeError * 0.6D, -1.0D, 1.0D);
                    force = force.add(right.scale(strafeDemand));
                    driveStrength = Math.max(driveStrength, Math.abs(strafeDemand));
                }
                case "ship_strafe_left" -> {
                    double strafeError = -Math.abs(command.amount()) - localVelocity.dot(right);
                    double strafeDemand = Mth.clamp(strafeError * 0.6D, -1.0D, 1.0D);
                    force = force.add(right.scale(strafeDemand));
                    driveStrength = Math.max(driveStrength, Math.abs(strafeDemand));
                }
                case "ship_strafe_right" -> {
                    double strafeError = Math.abs(command.amount()) - localVelocity.dot(right);
                    double strafeDemand = Mth.clamp(strafeError * 0.6D, -1.0D, 1.0D);
                    force = force.add(right.scale(strafeDemand));
                    driveStrength = Math.max(driveStrength, Math.abs(strafeDemand));
                }
                case "ship_ascend" -> {
                    force = force.add(up.scale(command.amount()));
                    driveStrength = Math.max(driveStrength, Math.abs(command.amount()));
                }
                case "ship_descend" -> {
                    force = force.add(up.scale(-command.amount()));
                    driveStrength = Math.max(driveStrength, Math.abs(command.amount()));
                }
                case "ship_stabilize" -> {
                    compensateGravity = true;
                    if (command.strength() >= attitudeLockStrength) {
                        attitudeLockStrength = command.strength();
                        attitudeLockTarget = command.targetAttitude();
                    }
                }
                case "ship_decelerate", "ship_brake" -> {
                    compensateGravity = true;
                    brakeStrength = Math.max(brakeStrength, command.strength());
                    if (!hasScmGroundDrive(topology)) {
                        force = force.add(localVelocity.scale(-0.35D * command.strength()));
                    }
                }
                case "ship_hover", "ship_climb" -> {
                    compensateGravity = true;
                    double verticalCorrection = climbVerticalCorrection(
                            command.targetY(), telemetry.position().y, telemetry.velocity().y,
                            command.targetSpeed(), command.strength());
                    force = force.add(worldDirectionToRoot(
                            new Vec3(0.0D, verticalCorrection, 0.0D)));
                    uprightStabilizationStrength = Math.max(
                            uprightStabilizationStrength, command.strength());
                }
                case "ship_face" -> {
                    compensateGravity = true;
                    torque = torque.add(faceTorque(
                            telemetry, command.targetPosition(), command.targetSpeed()));
                    uprightStabilizationStrength = Math.max(
                            uprightStabilizationStrength, 0.65D);
                }
                case "ship_dock" -> {
                    if (yieldsToDockingMagnet(command)) {
                        navigationPathStates.remove(entry.getKey() + ":docking_approach");
                        positionIntegralErrors.remove(entry.getKey());
                        compensateGravity = true;
                        break;
                    }
                    Telemetry targetTelemetry = targetPointTelemetry(
                            telemetry, command.targetPoint(), command.targetConnectorIndex());
                    Vec3 target = command.targetPosition();
                    navigationPathStates.remove(entry.getKey() + ":docking_approach");
                    Vec3 captureDirection = normalize(
                            target.subtract(targetTelemetry.position()), Vec3.ZERO);
                    NavigationGuidance guidance = new NavigationGuidance(
                            captureDirection, target, 0.25D,
                            false, true, false, captureDirection);
                    if (!isControlMode(ScmBuiltinControlModes.AIRSHIP_ID)) {
                        ModeControlDemand modeDemand = controlModeDemand(
                                entry.getKey() + ":docking_approach", targetTelemetry,
                                command, guidance,
                                forward, up, right, false, false);
                        ScmControlMode.ControlOutput output = modeDemand.output();
                        force = force.add(worldDirectionToRoot(output.force()));
                        torque = torque.add(worldDirectionToRoot(output.torque()));
                        driveDirection += output.driveDirection()
                                * Math.max(0.05D, output.force().length());
                        driveStrength = Math.max(driveStrength,
                                modeDemand.speedPlan().accelerationStrength());
                        brakeStrength = Math.max(brakeStrength,
                                modeDemand.speedPlan().brakeStrength());
                        compensateGravity |= output.compensateGravity();
                        uprightStabilizationStrength = Math.max(
                                uprightStabilizationStrength,
                                output.uprightStabilization());
                        if (command.targetDirection().lengthSqr() > 1.0E-12D) {
                            torque = torque.add(dockingAlignmentTorque(telemetry, command));
                        }
                    } else {
                        compensateGravity = true;
                        Vec3 approachForce = targetApproachForce(
                                entry.getKey(), targetTelemetry, command, null, target);
                        force = force.add(worldDirectionToRoot(approachForce));
                        if (command.lockRotation()) {
                            if (0.7D >= attitudeLockStrength) {
                                attitudeLockStrength = 0.7D;
                                attitudeLockTarget = command.targetAttitude();
                            }
                        } else if (command.targetDirection().lengthSqr() > 1.0E-12D) {
                            torque = torque.add(dockingAlignmentTorque(
                                    telemetry, command));
                            if (command.targetUp().lengthSqr() <= 1.0E-12D) {
                                uprightStabilizationStrength = Math.max(
                                        uprightStabilizationStrength, 0.8D);
                            }
                        } else {
                            torque = torque.add(faceTorque(
                                    targetTelemetry, command.targetPosition(), 0.45D));
                            uprightStabilizationStrength = Math.max(
                                    uprightStabilizationStrength, 0.7D);
                        }
                    }
                }
                case "ship_navigate", "ship_follow" -> {
                    Telemetry targetTelemetry = targetPointTelemetry(
                            telemetry, command.targetPoint(), command.targetConnectorIndex());
                    boolean finalAlignmentRequested =
                            command.targetDirection().lengthSqr() > 1.0E-12D;
                    double finalApproachDistance = Math.max(
                            DOCKING_COLLISION_APPROACH_DISTANCE,
                            command.tolerance() * 2.0D);
                    boolean finalAlignmentApproach = finalAlignmentRequested
                            && targetTelemetry.position().distanceTo(command.targetPosition())
                            <= finalApproachDistance;
                    NavigationGuidance guidance = navigationGuidance(
                            entry.getKey(), targetTelemetry, command,
                            command.targetPosition());
                    boolean preferShipDirection = !finalAlignmentApproach
                            && controller.activeShipFlightBehavior()
                            == ScmFlightBehavior.PREFER_SHIP_DIRECTION;
                    ModeControlDemand modeDemand = controlModeDemand(
                            entry.getKey(), targetTelemetry, command, guidance,
                            forward, up, right,
                            preferShipDirection, command.avoidCollisions());
                    ScmControlMode.ControlOutput output = modeDemand.output();
                    force = force.add(worldDirectionToRoot(output.force()));
                    torque = torque.add(worldDirectionToRoot(output.torque()));
                    driveDirection += output.driveDirection()
                            * Math.max(0.05D, output.force().length());
                    driveStrength = Math.max(driveStrength,
                            modeDemand.speedPlan().accelerationStrength());
                    brakeStrength = Math.max(brakeStrength,
                            modeDemand.speedPlan().brakeStrength());
                    compensateGravity |= output.compensateGravity();
                    uprightStabilizationStrength = Math.max(
                            uprightStabilizationStrength,
                            output.uprightStabilization());
                    if (command.lockRotation()) {
                        airshipAttitudeHoldTargets.remove(entry.getKey());
                        if (0.75D >= attitudeLockStrength) {
                            attitudeLockStrength = 0.75D;
                            attitudeLockTarget = command.targetAttitude();
                        }
                    } else if (finalAlignmentApproach && finalAlignmentRequested
                            && !isControlMode(ScmBuiltinControlModes.PLANE_ID)) {
                        airshipAttitudeHoldTargets.remove(entry.getKey());
                        torque = torque.add(dockingAlignmentTorque(
                                telemetry, command));
                        if (command.targetUp().lengthSqr() <= 1.0E-12D
                                && !isControlMode(ScmBuiltinControlModes.CAR_ID)) {
                            uprightStabilizationStrength = Math.max(
                                    uprightStabilizationStrength, 0.8D);
                        }
                    } else if (isControlMode(ScmBuiltinControlModes.AIRSHIP_ID)
                            && !preferShipDirection) {
                        Vec3 holdTarget = airshipAttitudeHoldTargets.computeIfAbsent(
                                entry.getKey(), ignored -> telemetry.eulerDegrees());
                        torque = torque.add(attitudeLockTorque(
                                holdTarget, telemetry.eulerDegrees(),
                                worldDirectionToRoot(telemetry.angularVelocity()), 0.65D));
                    } else {
                        airshipAttitudeHoldTargets.remove(entry.getKey());
                    }
                }
                default -> {
                }
            }
        }
        // -----------------------------------------------------STABILIZATION-----------------------------------------------------

        if (compensateGravity) {
            force = force.add(gravity.demand());
            preferredDirection = gravity.direction();
        }
        if (attitudeLockTarget != null && attitudeLockStrength > 1.0E-4D) {
            torque = torque.add(attitudeLockTorque(
                    attitudeLockTarget, telemetry.eulerDegrees(),
                    worldDirectionToRoot(telemetry.angularVelocity()),
                    attitudeLockStrength));
        } else if (uprightStabilizationStrength > 1.0E-4D) {
            torque = torque.add(uprightTorque(
                    telemetry, uprightStabilizationStrength));
        }
        torque = articulatedInertiaCompensatedTorque(torque, dynamics, topology);
        return new ControlDemand(
                clampComponents(force), clampComponents(torque), preferredDirection,
                Mth.clamp(finite(driveDirection), -1.0D, 1.0D),
                Mth.clamp(finite(driveStrength), 0.0D, 1.0D),
                Mth.clamp(finite(brakeStrength), 0.0D, 1.0D));
    }

    // Control the mode demand
    private ModeControlDemand controlModeDemand(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            NavigationGuidance guidance,
            Vec3 forwardRoot,
            Vec3 upRoot,
            Vec3 rightRoot,
            boolean preferForward,
            boolean avoidCollisions
    ) {
        Vec3 error = guidance.controlTarget().subtract(telemetry.position());
        Vec3 dir = normalize(guidance.direction(), normalize(error, Vec3.ZERO));
        Vec3 direct = normalize(error, Vec3.ZERO);
        boolean followsTarget = direct.lengthSqr() <= 1.0E-12D
                || dir.dot(direct) >= 0.9D;
        Vec3 integral = advancePositionIntegral(
                positionIntegralErrors.getOrDefault(commandKey, Vec3.ZERO),
                error, telemetry.velocity(), command.targetSpeed(),
                command.tolerance(), followsTarget);
        positionIntegralErrors.put(commandKey, integral);

        Vec3 forwardWorld = normalize(rootDirectionToWorld(forwardRoot),
                new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 upWorld = normalize(rootDirectionToWorld(upRoot),
                new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 rightWorld = normalize(rootDirectionToWorld(rightRoot),
                forwardWorld.cross(upWorld));
        boolean groundMode = isControlMode(ScmBuiltinControlModes.CAR_ID);
        NavigationSpeedPlan speedPlan = navigationSpeedPlan(
                commandKey, telemetry, command, guidance, dir, forwardWorld,
                groundMode, avoidCollisions);
        ScmControlMode.ControlInput input = new ScmControlMode.ControlInput(
                telemetry.position(), telemetry.velocity(), telemetry.angularVelocity(),
                forwardWorld, upWorld, rightWorld, guidance.controlTarget(), dir,
                integral, command.targetSpeed(), command.tolerance(),
                guidance.distanceResponse(), !guidance.brakeAtControlTarget(),
                speedPlan.forwardClearance(), speedPlan.reverseClearance(),
                speedPlan.permittedSpeed(), command.driveThrottle(),
                avoidCollisions, preferForward, guidance.reverseRecovery());
        return new ModeControlDemand(controller.getShipControlMode().navigate(input), speedPlan);
    }

    // Probe one host collision direction for the reusable reactive selector
    private ReactiveCollisionAvoidance.EscapeCandidate reactiveEscapeCandidate(
            Vec3 position,
            Vec3 direction,
            double scanRange,
            boolean groundVehicle
    ) {
        Vec3 normalized = normalize(direction, Vec3.ZERO);
        return new ReactiveCollisionAvoidance.EscapeCandidate(
                normalized, reactiveCollisionDistance(
                position, normalized, scanRange, groundVehicle));
    }

    // Probe one full-hull reactive collision direction
    private double reactiveCollisionDistance(
            Vec3 position,
            Vec3 direction,
            double scanRange,
            boolean groundVehicle
    ) {
        return groundVehicle
                ? groundCollisionDistance(position, direction, scanRange)
                : collisionDistance(position, direction, scanRange);
    }

    // Build the only speed plan used for the configured Acceleration and
    // Brake groups. The route's requested speed is capped by the speed at
    // which the craft can stop at its target and, when enabled, the speed at
    // which it can stop before the measured collision clearance. Heading,
    // gravity compensation and the allocator's force magnitude deliberately
    // do not participate in this calculation.
    private NavigationSpeedPlan navigationSpeedPlan(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            NavigationGuidance guidance,
            Vec3 pathDirection,
            Vec3 forwardWorld,
            boolean groundMode,
            boolean avoidCollisions
    ) {
        double scanRange = navigationCollisionScanRange(telemetry.velocity().length());
        double curveClearance = avoidCollisions
                && groundMode && guidance.obstacleAvoidanceRoute()
                ? groundCurveCollisionDistance(commandKey, telemetry.position(), scanRange)
                : -1.0D;
        double forwardClearance;
        double reverseClearance;
        if (!avoidCollisions) {
            forwardClearance = scanRange;
            reverseClearance = scanRange;
        } else if (curveClearance >= 0.0D) {
            // The chosen curve is the only drivetrain direction needed this
            // tick. Avoid two redundant full-hull axis sweeps on every route
            // sample; the opposite gear is reconsidered by the next replan.
            forwardClearance = scanRange;
            reverseClearance = scanRange;
            if (guidance.reverseRecovery()) {
                reverseClearance = curveClearance;
            } else {
                forwardClearance = curveClearance;
            }
        } else {
            forwardClearance = groundMode
                    ? groundCollisionDistance(telemetry.position(), forwardWorld, scanRange)
                    : collisionDistance(telemetry.position(), forwardWorld, scanRange);
            reverseClearance = groundMode
                    ? groundCollisionDistance(telemetry.position(), forwardWorld.scale(-1.0D), scanRange)
                    : collisionDistance(telemetry.position(), forwardWorld.scale(-1.0D), scanRange);
        }
        Vec3 travelDirection = normalize(pathDirection, forwardWorld);
        // A ground vehicle can only translate along its drivetrain axis while
        // it turns toward the route. Sweeping the hull along the waypoint
        // direction treats lateral space as required driving clearance and
        // can close propulsion completely whenever the car is not already
        // aligned. Measure the selected forward/reverse recovery axis instead;
        // the route direction remains authoritative for steering.
        Vec3 groundTravelDirection = guidance.reverseRecovery()
                ? forwardWorld.scale(-1.0D) : forwardWorld;
        // A selected ground detour is an arc the hull has already cleared.
        // Probe in the arc's first tangent, rather than repeatedly testing
        // the blocked direct chord and holding the car at zero throttle.
        if (groundMode && guidance.obstacleAvoidanceRoute()
                && guidance.collisionTravelDirection().lengthSqr() > 1.0E-12D) {
            groundTravelDirection = normalize(
                    new Vec3(guidance.collisionTravelDirection().x, 0.0D,
                            guidance.collisionTravelDirection().z),
                    groundTravelDirection);
        }
        double travelClearance = !avoidCollisions ? scanRange : groundMode
                ? (curveClearance >= 0.0D ? curveClearance
                : (guidance.obstacleAvoidanceRoute()
                ? groundCollisionDistance(
                        telemetry.position(), groundTravelDirection, scanRange)
                : (guidance.reverseRecovery() ? reverseClearance : forwardClearance)))
                : collisionDistance(telemetry.position(), travelDirection, scanRange);
        Vec3 measuredVelocity = groundMode
                ? new Vec3(telemetry.velocity().x, 0.0D, telemetry.velocity().z)
                : telemetry.velocity();
        Vec3 velocityDirection = normalize(measuredVelocity, travelDirection);
        if (avoidCollisions && telemetry.velocity().lengthSqr() > 1.0E-8D
                && curveClearance < 0.0D) {
            double velocityClearance = groundMode
                    ? groundCollisionDistance(
                            telemetry.position(), velocityDirection, scanRange)
                    : collisionDistance(telemetry.position(), velocityDirection, scanRange);
            travelClearance = Math.min(travelClearance, velocityClearance);
        }
        // Collision probes use a finite moving look-ahead, while the retained
        // direct route and its braking envelope own the real command target.
        Vec3 brakingTarget = guidance.brakeAtControlTarget()
                ? guidance.controlTarget() : command.targetPosition();
        double targetDistance = commandBrakingDistance(
                telemetry, command, brakingTarget, groundMode);
        double permittedSpeed = Math.min(command.targetSpeed(), navigationStoppingSpeed(
                targetDistance, command.tolerance()));
        if (avoidCollisions) {
            permittedSpeed = Math.min(permittedSpeed,
                    navigationSafeTravelSpeed(
                            travelClearance, groundMode
                                    ? GROUND_NAVIGATION_MIN_CLEARANCE
                                    : NAVIGATION_MIN_CLEARANCE));
        }
        permittedSpeed = Math.max(0.0D, finite(permittedSpeed));

        // Telemetry is the Sable rigid body's actual world velocity. Use its
        // magnitude here rather than a steering-axis projection: a sideways
        // slide still consumes stopping distance and must reduce drive / wake
        // the configured Brake group.
        double actualSpeed = Math.max(0.0D, finite(telemetry.velocity().length()));
        double speedBand = Math.max(0.25D,
                Math.max(1.0D, Math.max(actualSpeed, permittedSpeed))
                        * NAVIGATION_RESPONSE_SECONDS);
        double requestedThrottle = command.driveThrottle() >= 0.0D
                ? command.driveThrottle() : 1.0D;
        double accelerationStrength = actualSpeed + 1.0E-5D < permittedSpeed
                ? Math.min(requestedThrottle, Mth.clamp(
                (permittedSpeed - actualSpeed) / speedBand, 0.0D, 1.0D))
                : 0.0D;
        double brakeStrength = actualSpeed > permittedSpeed + 1.0E-5D
                ? Mth.clamp((actualSpeed - permittedSpeed) / speedBand, 0.0D, 1.0D)
                : 0.0D;
        return new NavigationSpeedPlan(forwardClearance, reverseClearance,
                permittedSpeed, accelerationStrength, brakeStrength);
    }

    // Use the authored alignment point for final-target speed management
    private double commandBrakingDistance(
            Telemetry telemetry,
            ActiveShipCommand command,
            Vec3 brakingTarget,
            boolean groundMode
    ) {
        return groundMode
                ? horizontalDistance(telemetry.position(), brakingTarget)
                : telemetry.position().distanceTo(brakingTarget);
    }

    // Check if this is a control mode
    private boolean isControlMode(ResourceLocation modeId) {
        return controller.getShipControlMode().id().equals(modeId);
    }

    // Get the prefer ship direction force
    private Vec3 preferShipDirectionForce(
            Vec3 approachWorld,
            Vec3 velocityWorld,
            Vec3 travelDirection,
            Vec3 forwardRoot
    ) {
        Vec3 approach = finite(approachWorld);
        Vec3 verticalDemand = worldDirectionToRoot(
                new Vec3(0.0D, approach.y, 0.0D));
        Vec3 horizontalTravel = new Vec3(
                travelDirection.x, 0.0D, travelDirection.z);
        if (horizontalTravel.lengthSqr() <= 1.0E-12D) {
            return verticalDemand;
        }
        horizontalTravel = horizontalTravel.normalize();
        Vec3 forwardWorld = rootDirectionToWorld(forwardRoot);
        Vec3 horizontalForward = new Vec3(
                forwardWorld.x, 0.0D, forwardWorld.z);
        if (horizontalForward.lengthSqr() <= 1.0E-12D) {
            return verticalDemand;
        }
        horizontalForward = horizontalForward.normalize();
        double alignment = Mth.clamp(
                horizontalForward.dot(horizontalTravel), -1.0D, 1.0D);
        Vec3 horizontalApproach = new Vec3(
                approach.x, 0.0D, approach.z);
        double forwardVelocity = finite(velocityWorld).dot(horizontalForward);
        double forwardDemand = horizontalApproach.dot(horizontalForward);
        double engagement = preferredDirectionForwardEngagement(alignment);

        // Brake before large turns to stop wide circles
        if (engagement <= 1.0E-6D) {
            forwardDemand = Math.min(0.0D, -forwardVelocity * 0.8D);
        } else {
            forwardDemand *= engagement;
        }
        return verticalDemand.add(forwardRoot.scale(
                Mth.clamp(forwardDemand, -1.0D, 1.0D)));
    }

    // Get the ground drive control
    private double groundDriveControl(
            Telemetry telemetry,
            ActiveShipCommand command,
            NavigationGuidance guidance,
            Vec3 forwardRoot
    ) {
        Vec3 travelDirection = new Vec3(
                guidance.direction().x, 0.0D, guidance.direction().z);
        Vec3 forwardWorld = rootDirectionToWorld(forwardRoot);
        forwardWorld = new Vec3(forwardWorld.x, 0.0D, forwardWorld.z);
        if (travelDirection.lengthSqr() <= 1.0E-12D
                || forwardWorld.lengthSqr() <= 1.0E-12D) {
            return 0.0D;
        }
        travelDirection = travelDirection.normalize();
        forwardWorld = forwardWorld.normalize();
        double alignment = Mth.clamp(forwardWorld.dot(travelDirection), -1.0D, 1.0D);
        double dir = alignment <= GROUND_REVERSE_ALIGNMENT ? -1.0D : 1.0D;
        double collisionRange = navigationCollisionScanRange(telemetry.velocity().length());
        double clearance = groundCollisionDistance(
                telemetry.position(), forwardWorld.scale(dir), collisionRange);
        if (dir > 0.0D && clearance <= NAVIGATION_MIN_CLEARANCE) {
            double reverseClearance = groundCollisionDistance(
                    telemetry.position(), forwardWorld.scale(-1.0D), collisionRange);
            if (reverseClearance > clearance + 1.0D) {
                dir = -1.0D;
                clearance = reverseClearance;
            }
        }
        double headingAlignment = Math.max(0.0D, dir * alignment);
        double targetDistance = horizontalDistance(
                telemetry.position(), guidance.controlTarget());
        double desiredSpeed = guidance.brakeAtControlTarget()
                ? targetSpeedForDistance(targetDistance, command.tolerance(),
                command.targetSpeed(), guidance.distanceResponse())
                : command.targetSpeed();
        desiredSpeed = Math.min(desiredSpeed, navigationSafeTravelSpeed(
                clearance, GROUND_NAVIGATION_MIN_CLEARANCE));
        double headingSpeedFloor = guidance.brakeAtControlTarget() ? 0.15D : 0.60D;
        desiredSpeed *= headingSpeedFloor
                + (1.0D - headingSpeedFloor) * headingAlignment;
        double currentSpeed = telemetry.velocity().dot(forwardWorld);
        return Mth.clamp((dir * desiredSpeed - currentSpeed)
                * GROUND_SPEED_CONTROL_GAIN, -1.0D, 1.0D);
    }

    // Get the ground collision distance
    private double groundCollisionDistance(Vec3 pos, Vec3 worldDir) {
        return groundCollisionDistance(pos, worldDir, COLLISION_SCAN_RANGE);
    }

    // Get the ground collision distance with a speed-dependent scan range.
    private double groundCollisionDistance(Vec3 pos, Vec3 worldDir, double range) {
        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null || worldDir.lengthSqr() <= 1.0E-12D) {
            return Math.max(0.0D, finite(range));
        }
        Vec3 dir = worldDir.normalize();
        HullBounds hull = shipHullBounds(pos, ctx.shipSubLevels());
        return pathTraceDistance(pos, dir, range,
                hull, ctx, true);
    }

    // Measure live clearance along the physical bicycle curve which the car
    // is actually following. A long straight tangent sweep rejects obstacles
    // which sit beside a safe turn and used to make cars stop even though the
    // already-planned arc avoided them. The bounded segment count keeps this
    // dynamic craft-to-craft check cheap while still following the curve.
    private double groundCurveCollisionDistance(
            String commandKey,
            Vec3 position,
            double range
    ) {
        NavigationPathState state = navigationPathStates.get(commandKey);
        CollisionScanContext ctx = collisionScanContext();
        double maximum = Math.max(0.0D, finite(range));
        if (state == null || ctx == null || !state.hasActiveGroundCurve()) {
            return -1.0D;
        }
        HullBounds hull = shipHullBounds(position, ctx.shipSubLevels());
        Vec3 cursor = finite(position);
        double checked = 0.0D;
        int remainingSamples = 4;
        for (int curveIdx = state.groundCurveIndex;
             curveIdx < state.groundRouteCurves.size()
                     && checked < maximum - 1.0E-6D && remainingSamples > 0;
             curveIdx++) {
            GroundPathPlanner.Curve curve = state.groundRouteCurves.get(curveIdx);
            double startFraction = curveIdx == state.groundCurveIndex
                    ? curve.nearestFraction(cursor) : 0.0D;
            double remainingLength = curve.length() * (1.0D - startFraction);
            if (remainingLength <= 1.0E-6D) {
                continue;
            }
            double inspectedLength = Math.min(maximum - checked, remainingLength);
            double fractionSpan = inspectedLength / Math.max(1.0E-9D, curve.length());
            int samples = Math.min(remainingSamples, Math.max(1, (int) Math.ceil(
                    inspectedLength / 1.5D)));
            for (int sample = 1; sample <= samples
                    && checked < maximum - 1.0E-6D; sample++) {
                double fraction = startFraction
                        + fractionSpan * sample / samples;
                Vec3 next = curve.pointAtFraction(fraction);
                next = new Vec3(next.x, cursor.y, next.z);
                Vec3 delta = next.subtract(cursor);
                double segmentLength = Math.min(delta.length(), maximum - checked);
                if (segmentLength <= 1.0E-6D) {
                    cursor = next;
                    continue;
                }
                Vec3 direction = delta.normalize();
                double clear = pathTraceDistance(
                        cursor, direction, segmentLength, hull, ctx, true);
                if (clear < segmentLength - 1.0E-4D) {
                    return checked + Math.max(0.0D, clear);
                }
                checked += segmentLength;
                cursor = cursor.add(direction.scale(segmentLength));
                if (segmentLength + 1.0E-6D < delta.length()) {
                    return checked;
                }
            }
            remainingSamples -= samples;
        }
        // The stored route was fully collision-tested when planned. Returning
        // the requested range after its checked portion means only a newly
        // intersecting live obstacle can close the speed envelope.
        return maximum;
    }

    // Get the ground safe speed
    private static double groundSafeSpeed(double clearance) {
        return navigationSafeTravelSpeed(
                clearance, GROUND_NAVIGATION_MIN_CLEARANCE);
    }

    // Get safe forward travel while turning
    static double preferredDirectionForwardEngagement(double alignment) {
        double normalized = (Mth.clamp(finite(alignment), -1.0D, 1.0D)
                - Math.cos(Math.toRadians(55.0D)))
                / (1.0D - Math.cos(Math.toRadians(55.0D)));
        normalized = Mth.clamp(normalized, 0.0D, 1.0D);
        return normalized * normalized * (3.0D - 2.0D * normalized);
    }

    // Keep yaw authority high until the route is aligned
    private double preferShipDirectionTurnStrength(
            Vec3 travelDirection,
            Vec3 forwardRoot
    ) {
        Vec3 horizontalTravel = new Vec3(
                finite(travelDirection).x, 0.0D, finite(travelDirection).z);
        Vec3 horizontalForwardWorld = rootDirectionToWorld(forwardRoot);
        horizontalForwardWorld = new Vec3(
                horizontalForwardWorld.x, 0.0D, horizontalForwardWorld.z);
        if (horizontalTravel.lengthSqr() <= 1.0E-12D
                || horizontalForwardWorld.lengthSqr() <= 1.0E-12D) {
            return 0.8D;
        }
        double alignment = horizontalTravel.normalize().dot(horizontalForwardWorld.normalize());
        return Mth.lerp(preferredDirectionForwardEngagement(alignment), 0.95D, 0.55D);
    }

    // Get the articulated inertia compensated torque
    private static Vec3 articulatedInertiaCompensatedTorque(
            Vec3 angularAccelerationDemand,
            SableAssemblyDynamicsApi.Snapshot dynamics,
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        if (topology == null || !topology.available()
                || topology.carriagePartitions().size() <= 1) {
            return inertiaCompensatedTorque(angularAccelerationDemand, dynamics);
        }
        Vec3 demand = angularAccelerationDemand == null
                ? Vec3.ZERO : angularAccelerationDemand;
        Vec3 rollPitch = inertiaCompensatedTorque(
                new Vec3(demand.x, 0.0D, demand.z), dynamics);
        SableAssemblyDynamicsApi.Snapshot primaryDynamics = dynamics == null
                ? null : dynamics.aggregate(primaryCarriageBodyIds(topology));
        Vec3 yaw = inertiaCompensatedTorque(
                new Vec3(0.0D, demand.y, 0.0D), primaryDynamics);
        return finite(new Vec3(rollPitch.x, yaw.y, rollPitch.z));
    }

    // Get the inertia compensated torque
    private static Vec3 inertiaCompensatedTorque(
            Vec3 angularAccelerationDemand,
            SableAssemblyDynamicsApi.Snapshot dynamics
    ) {
        if (dynamics == null || !dynamics.massAvailable()
                || angularAccelerationDemand == null
                || angularAccelerationDemand.lengthSqr() <= 1.0E-12D) {
            return angularAccelerationDemand == null ? Vec3.ZERO : angularAccelerationDemand;
        }
        double referenceInertia = dynamics.inertia().maximumDiagonal();
        if (!Double.isFinite(referenceInertia) || referenceInertia <= 1.0E-9D) {
            return angularAccelerationDemand;
        }
        Vec3 physicalTorque = dynamics.inertia().transform(angularAccelerationDemand)
                .scale(1.0D / referenceInertia);
        return finite(physicalTorque);
    }

    // Get the gravity compensation
    private GravityCompensation gravityCompensation(ShipControlMap effectiveMap) {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null || effectiveMap == null) {
            return GravityCompensation.NONE;
        }

        Vec3 gravityForce = Vec3.ZERO;
        for (SubLevel connected : connectedShipSubLevels(root)) {
            if (!(connected instanceof ServerSubLevel body)) {
                continue;
            }
            double mass = body.getMassTracker() == null ? 0.0D : body.getMassTracker().getMass();
            Vector3dc center = body.getMassTracker() == null ? null : body.getMassTracker().getCenterOfMass();
            if (!Double.isFinite(mass) || mass <= 0.0D || center == null) {
                continue;
            }

            Vec3 worldCenter = worldPosition(
                    body.logicalPose(), new Vec3(center.x(), center.y(), center.z()));
            Vector3d gravity = DimensionPhysicsData.getGravity(
                    body.getLevel(), new Vector3d(worldCenter.x, worldCenter.y, worldCenter.z));
            gravityForce = gravityForce.add(gravity.x * mass, gravity.y * mass, gravity.z * mass);
        }

        Vec3 counterGravityRoot = worldDirectionToRoot(gravityForce.scale(-1.0D));
        Vec3 dir = counterGravityRoot.lengthSqr() <= 1.0E-12D
                ? Vec3.ZERO : counterGravityRoot.normalize();
        return new GravityCompensation(
                ShipControlAllocator.normalizePhysicalForce(effectiveMap, counterGravityRoot), dir);
    }

    // Return the external acceleration at the assembled craft's mass centres.
    // The trajectory planner consumes this in world space; actuator allocation
    // remains server-authoritative and continues to use the calibrated map.
    private Vec3 aircraftExternalAcceleration() {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return Vec3.ZERO;
        }
        Vec3 weightedAcceleration = Vec3.ZERO;
        double totalMass = 0.0D;
        for (SubLevel connected : connectedShipSubLevels(root)) {
            if (!(connected instanceof ServerSubLevel body) || body.getMassTracker() == null) {
                continue;
            }
            double mass = body.getMassTracker().getMass();
            Vector3dc center = body.getMassTracker().getCenterOfMass();
            if (!Double.isFinite(mass) || mass <= 1.0E-9D || center == null) {
                continue;
            }
            Vec3 position = worldPosition(body.logicalPose(), new Vec3(center.x(), center.y(), center.z()));
            Vector3d gravity = DimensionPhysicsData.getGravity(
                    body.getLevel(), new Vector3d(position.x, position.y, position.z));
            weightedAcceleration = weightedAcceleration.add(
                    gravity.x * mass, gravity.y * mass, gravity.z * mass);
            totalMass += mass;
        }
        return totalMass <= 1.0E-9D ? Vec3.ZERO : finite(weightedAcceleration.scale(1.0D / totalMass));
    }

    // Get the upright torque
    private Vec3 uprightTorque(Telemetry telemetry, double strength) {
        Vec3 localAngularVelocity = worldDirectionToRoot(telemetry.angularVelocity());
        Vec3 torque = localAngularVelocity.scale(-0.4D * strength);
        Vec3 worldUp = rootDirectionToWorld(controllerUpRoot());
        Vec3 levelingAxisWorld = worldUp.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return torque.add(worldDirectionToRoot(levelingAxisWorld).scale(0.7D * strength));
    }

    // Get the attitude lock torque
    static Vec3 attitudeLockTorque(
            Vec3 targetEulerDegrees,
            Vec3 currentEulerDegrees,
            Vec3 localAngularVelocity,
            double strength
    ) {
        double clampedStrength = Mth.clamp(finite(strength), 0.0D, 1.0D);
        Vec3 error = attitudeRotationErrorRadians(
                finite(targetEulerDegrees), finite(currentEulerDegrees));
        Vec3 angular = finite(localAngularVelocity);
        double fortyFiveDegrees = Math.toRadians(45.0D);
        return new Vec3(
                Mth.clamp(error.x / fortyFiveDegrees - angular.x * 0.4D, -1.0D, 1.0D),
                Mth.clamp(error.y / fortyFiveDegrees - angular.y * 0.4D, -1.0D, 1.0D),
                Mth.clamp(error.z / fortyFiveDegrees - angular.z * 0.4D, -1.0D, 1.0D))
                .scale(clampedStrength);
    }

    // Get the attitude rotation error radians
    static Vec3 attitudeRotationErrorRadians(
            Vec3 targetEulerDegrees,
            Vec3 currentEulerDegrees
    ) {
        Vec3 target = finite(targetEulerDegrees);
        Vec3 current = finite(currentEulerDegrees);
        Quaterniond targetOrientation = new Quaterniond().rotationXYZ(
                Math.toRadians(target.x),
                Math.toRadians(target.y),
                Math.toRadians(target.z));
        Quaterniond currentOrientation = new Quaterniond().rotationXYZ(
                Math.toRadians(current.x),
                Math.toRadians(current.y),
                Math.toRadians(current.z));
        Quaterniond error = currentOrientation.conjugate()
                .mul(targetOrientation)
                .normalize();
        if (error.w() < 0.0D) {
            error.set(-error.x(), -error.y(), -error.z(), -error.w());
        }
        double vectorLength = Math.sqrt(
                error.x() * error.x()
                        + error.y() * error.y()
                        + error.z() * error.z());
        if (vectorLength <= 1.0E-12D) {
            return Vec3.ZERO;
        }
        double angle = 2.0D * Math.atan2(
                vectorLength, Mth.clamp(error.w(), 0.0D, 1.0D));
        double scale = angle / vectorLength;
        return new Vec3(
                error.x() * scale,
                error.y() * scale,
                error.z() * scale);
    }

    // Get the face torque
    private Vec3 faceTorque(Telemetry telemetry, Vec3 target, double strength) {
        Vec3 desired = target.subtract(telemetry.position());
        desired = new Vec3(desired.x, 0.0D, desired.z);
        if (desired.lengthSqr() <= 1.0E-9D) {
            return Vec3.ZERO;
        }
        Vec3 forwardWorld = rootDirectionToWorld(controllerForwardRoot());
        forwardWorld = normalize(new Vec3(forwardWorld.x, 0.0D, forwardWorld.z),
                new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 desiredWorld = desired.normalize();
        double signed = forwardWorld.cross(desiredWorld).y;
        double dot = Mth.clamp(forwardWorld.dot(desiredWorld), -1.0D, 1.0D);
        double yawError = Math.atan2(signed, dot);
        Vec3 yawWorld = new Vec3(0.0D,
                Mth.clamp(yawError * strength - telemetry.angularVelocity().y * 0.25D, -1.0D, 1.0D),
                0.0D);
        return worldDirectionToRoot(yawWorld);
    }

    // Apply the allocation
    private void applyAllocation(ShipControlMap currentMap, double[] controls) {
        double[] requested = new double[currentMap.units().size()];
        Map<String, Integer> groupWinners = new HashMap<>();
        for (int idx = 0; idx < currentMap.units().size(); idx++) {
            ShipControlMap.PropulsionUnit unit = currentMap.units().get(idx);
            if (!unit.controllable() || isAccelerationControlUnit(currentMap, unit)) {
                continue;
            }
            Actuator actuator = controlActuators.get(idx);
            if (actuator == null) {
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        controller.getLevel(), unit.subLevelId(), unit.blockPosition());
                actuator = actuatorForStored(blockEntity, unit, controller.getLevel());
                if (actuator != null && actuator.controllable()) {
                    controlActuators.put(idx, actuator);
                }
            }
            if (actuator == null || !actuator.controllable()) {
                continue;
            }
            if (actuator.isFaceActionControl() || isControlMode(ScmBuiltinControlModes.CAR_ID)
                    && (actuator.isKineticControl() || actuator.isWheelControl())) {
                continue;
            }
            double normalized = idx < controls.length ? controls[idx] : 0.0D;
            double calibrated = actuator.controlForDemand(unit, normalized);
            double control = mapAllocationControl(
                    calibrated, unit.minControl(), unit.maxControl(), actuator.mapsOwnControlRange());
            requested[idx] = control;
            String group = actuator.controlGroupId();
            if (!group.isBlank()) {
                Integer prev = groupWinners.get(group);
                if (prev == null || Math.abs(control) > Math.abs(requested[prev])) {
                    groupWinners.put(group, idx);
                }
            }
        }
        for (int idx = 0; idx < currentMap.units().size(); idx++) {
            Actuator actuator = controlActuators.get(idx);
            if (isAccelerationControlUnit(currentMap, currentMap.units().get(idx))) {
                continue;
            }
            if (actuator != null && (actuator.isFaceActionControl()
                    || isControlMode(ScmBuiltinControlModes.CAR_ID)
                    && (actuator.isKineticControl() || actuator.isWheelControl()))) {
                continue;
            }
            if (actuator == null || actuator.controlGroupId().isBlank()) {
                applyAllocatedControl(idx, actuator, requested[idx]);
            }
        }
        for (int idx : groupWinners.values()) {
            applyAllocatedControl(idx, controlActuators.get(idx), requested[idx]);
        }
    }

    // Drive face-bound controls from their SCM profile action group. A face's
    // linker/default label is only a compatibility fallback: once a player
    // assigns that exact face to (for example) Yaw Left, its group is the
    // authority. This is essential for four-wheel steering, where the left
    // and right physical inputs intentionally belong to the same yaw action.
    private void applyScmFaceActionControls(
            ShipControlMap currentMap, ControlDemand demand
    ) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        boolean explicitRouting = configuration != null
                && configuration.isConfiguredFor(currentMap)
                && configuration.hasActionBindings();
        // Clear every linker-face output first. A profile can move a
        // face from one action to another, and an old signal must never remain
        // asserted just because the new action happens to be idle this tick.
        List<ScmTarget> linkerTargets = ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker());
        for (ScmTarget target : linkerTargets) {
            if (!target.usesFaceControl()) {
                continue;
            }
            ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                    level, target.subLevelId(), target.signalPosition(), target.signalFace(),
                    scmFaceSignalSource(target), 0);
        }
        if (!explicitRouting) return;

        Set<String> activeActions = activeProfileFaceActions(
                activeControlActionTypes(demand, currentMap),
                activeBrakeStrength(demand) > 1.0E-5D);
        Map<ScmConfigurationProfile.UnitReference, Double> requestedSignals = new LinkedHashMap<>();
        for (String action : activeActions) {
            double strength = profileFaceActionStrength(action, demand);
            if (strength <= 1.0E-5D) {
                continue;
            }
            for (ScmConfigurationProfile.UnitReference reference
                    : configuration.unitsForActions(Set.of(action))) {
                if (reference.usesFaceControl()) {
                    requestedSignals.merge(reference, strength, Math::max);
                }
            }
        }

        Set<String> requestedSources = requestedSignals.keySet().stream()
                .map(ShipControlModuleRuntime::scmProfileFaceSignalSource)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (ScmConfigurationProfile.Group group : configuration.groups()) {
            for (ScmConfigurationProfile.UnitReference reference : group.units()) {
                if (reference.usesFaceControl()
                        && !requestedSources.contains(scmProfileFaceSignalSource(reference))) {
                    setProfileFaceSignal(level, reference, linkerTargets, 0);
                }
            }
        }
        requestedSignals.forEach((reference, strength) -> setProfileFaceSignal(
                level, reference, linkerTargets, Mth.clamp((int) Math.round(
                        strength * 15.0D), 0, 15)));
    }

    // Drive exactly one player-configured face. If the face is represented by
    // a linker target, retain that target's physical plane; otherwise use the
    // directly selected block face. No directional action is inferred here.
    private void setProfileFaceSignal(
            Level level,
            ScmConfigurationProfile.UnitReference reference,
            List<ScmTarget> linkerTargets,
            int strength
    ) {
        if (reference == null || !reference.usesFaceControl()) {
            return;
        }
        String source = scmProfileFaceSignalSource(reference);
        boolean linked = false;
        for (ScmTarget target : linkerTargets) {
            if (!target.usesFaceControl()
                    || !profileReferenceMatchesTarget(reference, target)) {
                continue;
            }
            ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                    level, target.subLevelId(), target.signalPosition(), target.signalFace(),
                    source, strength);
            linked = true;
        }
        if (!linked) {
            ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                    level, reference.subLevelId(),
                    reference.blockPosition().relative(reference.face()), reference.face(),
                    source, strength);
        }
    }

    private static String scmProfileFaceSignalSource(
            ScmConfigurationProfile.UnitReference reference
    ) {
        return CONTROL_CHANNEL + ":profile-face:" + reference.subLevelId() + ':'
                + reference.blockPosition().asLong() + ':'
                + reference.face().getSerializedName();
    }

    // Preserve the historic linker/default face interpretation for unprofiled
    // controllers. A configured SCM never enters this path.
    private void applyLinkerFaceActionControls(Level level, ControlDemand demand) {
        for (ScmTarget target : ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker())) {
            if (!target.usesFaceControl()) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, target.subLevelId(), target.blockPosition());
            if (blockEntity instanceof WheelMountControlBridge) {
                continue;
            }
            int signal = Mth.clamp((int) Math.round(
                    scmFaceActionStrength(target, demand) * 15.0D), 0, 15);
            ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                    level, target.subLevelId(), target.signalPosition(), target.signalFace(),
                    scmFaceSignalSource(target), signal);
        }
    }

    // Reduce the active command set to direct face actions. When an authored
    // directional action is present it wins over the old generic axis action;
    // otherwise a generic group remains available for existing profiles.
    private static Set<String> activeProfileFaceActions(
            Set<String> requestedActions, boolean braking
    ) {
        Set<String> actions = new LinkedHashSet<>();
        if (requestedActions != null) {
            requestedActions.stream().filter(ShipControlModuleRuntime::isProfileFaceAction)
                    .forEach(actions::add);
        }
        if (braking) {
            actions.add("ship_brake");
        }
        retainSpecificProfileFaceActions(actions, "ship_yaw", "ship_pan",
                "ship_yaw_left", "ship_yaw_right");
        retainSpecificProfileFaceActions(actions, "ship_pitch", "ship_tilt",
                "ship_pitch_up", "ship_pitch_down");
        retainSpecificProfileFaceActions(actions, "ship_roll", null,
                "ship_roll_left", "ship_roll_right");
        retainSpecificProfileFaceActions(actions, "ship_strafe", null,
                "ship_strafe_left", "ship_strafe_right");
        // Acceleration is the shared analogue drive-power channel, not a
        // generic direction. Keep it active alongside the selected Forward
        // or Backwards group so a car can select its gear and receive power
        // in the same tick.
        return Set.copyOf(actions);
    }

    private static boolean isProfileFaceAction(String action) {
        return switch (action) {
            case "ship_yaw", "ship_pan", "ship_yaw_left", "ship_yaw_right",
                 "ship_pitch", "ship_tilt", "ship_pitch_up", "ship_pitch_down",
                 "ship_roll", "ship_roll_left", "ship_roll_right",
                 "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                 "ship_accelerate", "ship_forward", "ship_backward", "ship_reverse",
                 "ship_ascend", "ship_descend", "ship_brake", "ship_decelerate" -> true;
            default -> false;
        };
    }

    private static void retainSpecificProfileFaceActions(
            Set<String> actions, String generic, @Nullable String legacyGeneric,
            String... specificActions
    ) {
        boolean hasSpecific = java.util.Arrays.stream(specificActions)
                .anyMatch(actions::contains);
        if (hasSpecific) {
            actions.remove(generic);
            if (legacyGeneric != null) {
                actions.remove(legacyGeneric);
            }
        }
    }

    private double profileFaceActionStrength(String action, ControlDemand demand) {
        Vec3 forward = controllerForwardRoot();
        Vec3 up = controllerUpRoot();
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        double forwardDemand = demand.force().dot(forward);
        double forwardDrive = demand.driveDirection() > ACTION_DIRECTION_EPSILON
                ? Math.max(demand.driveStrength(), Math.max(0.0D, forwardDemand))
                : Math.max(0.0D, forwardDemand);
        double backwardDrive = demand.driveDirection() < -ACTION_DIRECTION_EPSILON
                ? Math.max(demand.driveStrength(), Math.max(0.0D, -forwardDemand))
                : Math.max(0.0D, -forwardDemand);
        double strafeDemand = demand.force().dot(right);
        double liftDemand = demand.force().dot(up);
        double yawDemand = demand.torque().dot(up);
        double pitchDemand = demand.torque().dot(right);
        double rollDemand = demand.torque().dot(forward);
        return switch (action) {
            case "ship_yaw_left" -> Mth.clamp(-yawDemand, 0.0D, 1.0D);
            case "ship_yaw_right" -> Mth.clamp(yawDemand, 0.0D, 1.0D);
            case "ship_yaw", "ship_pan" -> Mth.clamp(Math.abs(yawDemand), 0.0D, 1.0D);
            case "ship_pitch_up" -> Mth.clamp(pitchDemand, 0.0D, 1.0D);
            case "ship_pitch_down" -> Mth.clamp(-pitchDemand, 0.0D, 1.0D);
            case "ship_pitch", "ship_tilt" -> Mth.clamp(Math.abs(pitchDemand), 0.0D, 1.0D);
            case "ship_roll_left" -> Mth.clamp(-rollDemand, 0.0D, 1.0D);
            case "ship_roll_right" -> Mth.clamp(rollDemand, 0.0D, 1.0D);
            case "ship_roll" -> Mth.clamp(Math.abs(rollDemand), 0.0D, 1.0D);
            case "ship_strafe_left" -> Mth.clamp(-strafeDemand, 0.0D, 1.0D);
            case "ship_strafe_right" -> Mth.clamp(strafeDemand, 0.0D, 1.0D);
            case "ship_strafe" -> Mth.clamp(Math.abs(strafeDemand), 0.0D, 1.0D);
            case "ship_forward" -> Mth.clamp(forwardDrive, 0.0D, 1.0D);
            case "ship_backward", "ship_reverse" -> Mth.clamp(backwardDrive, 0.0D, 1.0D);
            case "ship_accelerate" -> accelerationControlDemand(demand);
            case "ship_ascend" -> Mth.clamp(liftDemand, 0.0D, 1.0D);
            case "ship_descend" -> Mth.clamp(-liftDemand, 0.0D, 1.0D);
            case "ship_brake", "ship_decelerate" -> activeBrakeStrength(demand);
            default -> 0.0D;
        };
    }

    private double activeBrakeStrength() {
        return activeCommands.values().stream()
                .filter(command -> "ship_decelerate".equals(command.type())
                        || "ship_brake".equals(command.type()))
                .mapToDouble(ActiveShipCommand::strength)
                .max().orElse(0.0D);
    }

    // Combine an explicit graph Brake command with the automatic navigation
    // brake calculated from target distance and collision clearance.
    private double activeBrakeStrength(@Nullable ControlDemand demand) {
        double automatic = demand == null ? 0.0D : demand.brakeStrength();
        return Math.max(activeBrakeStrength(), Mth.clamp(automatic, 0.0D, 1.0D));
    }

    // Clear linker-plane outputs owned by this SCM when it stops or unloads.
    private void clearScmFaceActionControls() {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        List<ScmTarget> linkerTargets = ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker());
        for (ScmTarget target : linkerTargets) {
            if (target.usesFaceControl()) {
                ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                        level, target.subLevelId(), target.signalPosition(), target.signalFace(),
                        scmFaceSignalSource(target), 0);
            }
        }
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        if (configuration != null) {
            for (ScmConfigurationProfile.Group group : configuration.groups()) {
                for (ScmConfigurationProfile.UnitReference reference : group.units()) {
                    setProfileFaceSignal(level, reference, linkerTargets, 0);
                }
            }
        }
    }

    private static String scmFaceSignalSource(ScmTarget target) {
        return CONTROL_CHANNEL + ":face-action:" + target.stableId();
    }

    // Convert the final demand into the one direction named by a linker face.
    private double scmFaceActionStrength(ScmTarget target, ControlDemand demand) {
        String action = ContraptionNetworkLinkerData.scmTargetActionId(target);
        if (action.isBlank()) {
            return 0.0D;
        }
        Vec3 forward = controllerForwardRoot();
        Vec3 up = controllerUpRoot();
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        double forwardDemand = demand.force().dot(forward);
        double forwardDrive = demand.driveDirection() > ACTION_DIRECTION_EPSILON
                ? Math.max(demand.driveStrength(), Math.max(0.0D, forwardDemand))
                : Math.max(0.0D, forwardDemand);
        double backwardDrive = demand.driveDirection() < -ACTION_DIRECTION_EPSILON
                ? Math.max(demand.driveStrength(), Math.max(0.0D, -forwardDemand))
                : Math.max(0.0D, -forwardDemand);
        double strafeDemand = demand.force().dot(right);
        double liftDemand = demand.force().dot(up);
        double yawDemand = demand.torque().dot(up);
        double pitchDemand = demand.torque().dot(right);
        double rollDemand = demand.torque().dot(forward);
        return switch (action) {
            case "yaw_left" -> Mth.clamp(-yawDemand, 0.0D, 1.0D);
            case "yaw_right" -> Mth.clamp(yawDemand, 0.0D, 1.0D);
            case "pitch_up" -> Mth.clamp(pitchDemand, 0.0D, 1.0D);
            case "pitch_down" -> Mth.clamp(-pitchDemand, 0.0D, 1.0D);
            case "roll_left" -> Mth.clamp(-rollDemand, 0.0D, 1.0D);
            case "roll_right" -> Mth.clamp(rollDemand, 0.0D, 1.0D);
            case "strafe_left" -> Mth.clamp(-strafeDemand, 0.0D, 1.0D);
            case "strafe_right" -> Mth.clamp(strafeDemand, 0.0D, 1.0D);
            case "throttle_up", "forward" -> Mth.clamp(forwardDrive, 0.0D, 1.0D);
            case "throttle_down", "reverse" -> Mth.clamp(backwardDrive, 0.0D, 1.0D);
            case "lift_up", "ascend" -> Mth.clamp(liftDemand, 0.0D, 1.0D);
            case "lift_down", "descend" -> Mth.clamp(-liftDemand, 0.0D, 1.0D);
            default -> 0.0D;
        };
    }

    // Drive profile-declared analogue speed controls outside the propulsion
    // allocator. A transmission, governor or equivalent block changes the
    // output of a drive chain; it is not a force source with a world direction.
    private void applyAccelerationControls(
            ShipControlMap currentMap, ControlDemand demand
    ) {
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        if (configuration == null || !configuration.isConfiguredFor(currentMap)
                || configuration.accelerationUnits().isEmpty()) {
            return;
        }
        boolean active = hasActiveTranslationalCommand();
        double requested = active && activeBrakeStrength(demand) <= 1.0E-5D
                ? accelerationControlDemand(demand) : 0.0D;
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            if (!isAccelerationControlUnit(currentMap, unit)) {
                continue;
            }
            Actuator actuator = controlActuators.get(unit.index());
            if (actuator == null) {
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        controller.getLevel(), unit.subLevelId(), unit.blockPosition());
                actuator = actuatorForStored(blockEntity, unit, controller.getLevel());
                if (actuator != null && actuator.controllable() && actuator.isAvailable()) {
                    controlActuators.put(unit.index(), actuator);
                }
            }
            if (actuator == null || !actuator.controllable() || !actuator.isAvailable()) {
                continue;
            }
            double calibrated = actuator.controlForDemand(unit, requested);
            double control = mapAllocationControl(calibrated, unit.minControl(), unit.maxControl(),
                    actuator.mapsOwnControlRange());
            applyAllocatedControl(unit.index(), actuator, control);
        }
    }

    // Brake is an explicit physical action, not a force-vector request. The
    // normal allocator therefore has no non-zero wrench from which to infer a
    // control value. Apply only the authored Brake-group units directly, while
    // the wheel path below combines its three direct inputs atomically.
    private void applyConfiguredBrakeControls(
            ShipControlMap currentMap, ControlDemand demand
    ) {
        double strength = activeBrakeStrength(demand);
        if (strength <= 1.0E-5D) {
            return;
        }
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        Set<ScmConfigurationProfile.UnitReference> brakeUnits = configuredBrakeUnits(
                configuration, currentMap);
        boolean explicitRouting = configuration != null
                && configuration.isConfiguredFor(currentMap)
                && configuration.hasActionBindings();
        if (!explicitRouting) {
            for (WheelMountControlBridge wheel : controlledScmWheels) {
                wheel.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
            }
            controlledScmWheels.clear();
            controlledScmWheelTargets.clear();
            return;
        }
        // Brake is opt-in. Never substitute every live actuator for an absent
        // Brake group; acceleration, lift and steering blocks are unrelated
        // to a braking request.
        if (!explicitRouting || brakeUnits.isEmpty()) {
            return;
        }
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            if (!unit.controllable() || brakeUnits.stream()
                    .noneMatch(reference -> configurationReferenceMatchesUnit(reference, unit))) {
                continue;
            }
            Actuator actuator = controlActuators.get(unit.index());
            if (actuator == null) {
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        controller.getLevel(), unit.subLevelId(), unit.blockPosition());
                actuator = actuatorForStored(blockEntity, unit, controller.getLevel());
                if (actuator != null && actuator.controllable() && actuator.isAvailable()) {
                    controlActuators.put(unit.index(), actuator);
                }
            }
            if (actuator == null || !actuator.controllable() || !actuator.isAvailable()
                    || actuator.isWheelControl() || actuator.isFaceActionControl()) {
                continue;
            }
            double calibrated = actuator.controlForDemand(unit, strength);
            double control = mapAllocationControl(calibrated, unit.minControl(), unit.maxControl(),
                    actuator.mapsOwnControlRange());
            applyAllocatedControl(unit.index(), actuator, control);
        }
    }

    // Resolve the dedicated Brake action, including its legacy persisted name.
    // `unitsForActions` intentionally requires a binding for every supplied
    // action, so resolve each alias independently and combine their groups.
    private Set<ScmConfigurationProfile.UnitReference> configuredBrakeUnits(
            @Nullable ScmConfigurationProfile configuration,
            ShipControlMap currentMap
    ) {
        if (configuration == null || !configuration.isConfiguredFor(currentMap)
                || !configuration.hasActionBindings()) {
            return Set.of();
        }
        Set<ScmConfigurationProfile.UnitReference> units = new LinkedHashSet<>();
        units.addAll(configuration.unitsForActions(Set.of("ship_brake")));
        units.addAll(configuration.unitsForActions(Set.of("ship_decelerate")));
        return Set.copyOf(units);
    }

    // Keep the analogue drive-chain level separate from the combined physical
    // force. The latter also contains gravity hold, lateral correction and
    // docking alignment; using its magnitude made a small planned speed
    // request look like full acceleration whenever any of those corrections
    // was active.
    private double accelerationControlDemand(ControlDemand demand) {
        if (demand == null) {
            return 0.0D;
        }
        return Mth.clamp(demand.driveStrength(), 0.0D, 1.0D);
    }

    // Check whether the current command has a declared analogue drive
    // channel. This is deliberately based on the command kind, rather than
    // force output: the gearbox remains selected while the analogue channel
    // coasts at zero to prevent a moving car from pulsing its direction
    // control every time the speed error crosses zero.
    private boolean hasActiveAccelerationControl(
            ShipControlMap currentMap, ControlDemand demand
    ) {
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        return configuration != null && configuration.isConfiguredFor(currentMap)
                && !configuration.accelerationUnits().isEmpty()
                && hasActiveTranslationalCommand();
    }

    // Return whether an active command is asking an analogue drive-chain
    // control to participate. This deliberately does not depend on the
    // individual direction action: a transmission or throttle must work for
    // reverse and strafe just as it does for forward travel.
    private boolean hasActiveTranslationalCommand() {
        return activeCommands.values().stream().anyMatch(command -> switch (command.type()) {
            case "ship_dock", "ship_navigate", "ship_follow",
                 "ship_accelerate", "ship_forward", "ship_reverse", "ship_backward",
                 "ship_strafe", "ship_strafe_left", "ship_strafe_right",
                 "ship_ascend", "ship_descend" -> true;
            default -> false;
        });
    }

    private boolean isAccelerationControlUnit(
            ShipControlMap currentMap, ShipControlMap.PropulsionUnit unit
    ) {
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        return unit != null && configuration != null && configuration.isConfiguredFor(currentMap)
                && configuration.accelerationUnits().stream()
                .anyMatch(reference -> configurationReferenceMatchesUnit(reference, unit));
    }

    // Apply the car control
    private void applyCarControl(
            ShipControlMap currentMap,
            ControlDemand demand,
            Telemetry telemetry,
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        applyCarDrivetrain(currentMap, demand);
        applyScmWheelSteering(currentMap, demand, telemetry, topology);
    }

    // Apply the car drivetrain
    private void applyCarDrivetrain(
            ShipControlMap currentMap, ControlDemand demand
    ) {
        // Kinetic controls are applied outside the normal allocator path. A
        // face filtered out by actionRoutedGeometryMap() would otherwise keep
        // the signal from the previous tick, leaving a Directional Gearshift
        // with both physical faces asserted after a schedule changes course.
        neutralizeActionMaskedKineticControls(currentMap);
        double longitudinalDemand = Mth.clamp(
                demand.force().dot(controllerForwardRoot()), -1.0D, 1.0D);
        double requestedDirection = plannedDriveDirection(demand);
        double requestedDrive = requestedDirection == 0.0D
                ? 0.0D : Math.max(0.0D, longitudinalDemand * requestedDirection);
        boolean holdKineticDirection = hasActiveAccelerationControl(currentMap, demand)
                && Math.abs(requestedDirection) > ACTION_DIRECTION_EPSILON;
        double kineticDemand = holdKineticDirection ? 1.0D : requestedDrive;
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        // A face binding is an authored, discrete drivetrain command. It is
        // routed by its configured action rather than inferred from the
        // block's geometric force normal.
        Set<ScmConfigurationProfile.UnitReference> activeFaceBindings =
                activeConfiguredFaceBindings(configuration, currentMap, demand);
        List<KineticControlOption> options = new ArrayList<>();
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            // Profile routing represents a disallowed unit with zero live
            // capacity. The car-specific kinetic path must honour that mask as
            // well; otherwise it can wake a Forward gearshift while Yaw is
            // intentionally routed to the Auto wheel group.
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                continue;
            }
            if (isAccelerationControlUnit(currentMap, unit)) {
                continue;
            }
            Actuator actuator = controlActuators.get(unit.index());
            if (actuator == null || !actuator.isKineticControl()
                    || !actuator.isAvailable()) {
                continue;
            }
            Set<String> domain = actuator.kineticControlDomain();
            if (domain.isEmpty()) {
                domain = Set.of(unit.subLevelId() + ":" + unit.blockPosition());
            }
            options.add(new KineticControlOption(unit, actuator, domain));
        }
        for (List<KineticControlOption> domain : kineticControlDomains(options)) {
            KineticControlOption neutral = domain.stream().max(Comparator
                    .comparingInt((KineticControlOption option) ->
                            option.actuator().kineticControlSize())
                    .thenComparingInt(option -> option.domain().size()))
                    .orElse(null);
            KineticControlOption selected = null;
            double selectedScore = Double.NEGATIVE_INFINITY;
            if (kineticDemand > 1.0E-6D) {
                for (KineticControlOption option : domain) {
                    double alignment = option.unit().forceDirection()
                            .dot(controllerForwardRoot());
                    boolean explicitlyRouted = !activeFaceBindings.isEmpty();
                    // A configured gearshift face is an explicit directional
                    // choice. Restrict the domain to that authored face: the
                    // former code only disabled the geometric heuristic, so a
                    // Backward action could still select its Forward sibling
                    // solely because it scored higher.
                    if (explicitlyRouted && activeFaceBindings.stream().noneMatch(reference ->
                            configurationReferenceMatchesUnit(reference, option.unit()))) {
                        continue;
                    }
                    // A configured face's redstone-side normal is not a
                    // measurement of travel direction. Unconfigured/linker
                    // controls retain the legacy alignment check.
                    if (!explicitlyRouted && alignment * requestedDirection <= 0.05D) {
                        continue;
                    }
                    double score = option.actuator().kineticControlSize() * 100.0D
                            + Math.abs(alignment) * 10.0D
                            + option.unit().samples().stream()
                            .mapToDouble(sample -> Math.abs(sample.speed())
                                    + (sample.active() ? 1.0D : 0.0D))
                            .max().orElse(0.0D);
                    if (score > selectedScore) {
                        selectedScore = score;
                        selected = option;
                    }
                }
            }
            if (selected == null) {
                if (neutral != null) {
                    neutral.actuator().neutralize();
                    appliedControlValues.put(neutral.unit().index(),
                            neutral.actuator().neutralControl());
                }
                continue;
            }
            double control = selected.actuator().controlForDemand(
                    selected.unit(), kineticDemand);
            double applied = mapAllocationControl(
                    control, selected.unit().minControl(), selected.unit().maxControl(),
                    selected.actuator().mapsOwnControlRange());
            selected.actuator().apply(applied);
            appliedControlValues.put(selected.unit().index(), applied);
        }
    }

    // Get the configured face controls explicitly requested by this car command.
    // These bindings are safe to drive directly without a successful physical
    // calibration sweep: their face is the command target, not an inferred
    // force vector.
    private Set<ScmConfigurationProfile.UnitReference> activeConfiguredFaceBindings(
            ScmConfigurationProfile configuration,
            ShipControlMap currentMap,
            ControlDemand demand
    ) {
        if (configuration == null || !configuration.isConfiguredFor(currentMap)
                || !configuration.hasActionBindings()) {
            return Set.of();
        }
        return configuration.unitsForExplicitActions(activeControlActionTypes(demand)).stream()
                .filter(ScmConfigurationProfile.UnitReference::usesFaceControl)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    // Deassert face-scoped kinetic controls which profile action routing has
    // removed from the current car command. Do not touch live controls here:
    // their active side is selected and applied by applyCarDrivetrain().
    private void neutralizeActionMaskedKineticControls(ShipControlMap currentMap) {
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            if (!unit.controllable() || unit.maxThrust() > 1.0E-9D) {
                continue;
            }
            Actuator actuator = controlActuators.get(unit.index());
            if (actuator == null || !actuator.isKineticControl() || !actuator.isAvailable()) {
                continue;
            }
            actuator.neutralize();
            appliedControlValues.put(unit.index(), actuator.neutralControl());
        }
    }

    // Get the independent kinetic control domains
    private static List<List<KineticControlOption>> kineticControlDomains(
            List<KineticControlOption> options
    ) {
        List<List<KineticControlOption>> domains = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        for (int idx = 0; idx < options.size(); idx++) {
            if (!visited.add(idx)) {
                continue;
            }
            List<KineticControlOption> domain = new ArrayList<>();
            ArrayDeque<Integer> frontier = new ArrayDeque<>();
            frontier.addLast(idx);
            while (!frontier.isEmpty()) {
                KineticControlOption current = options.get(frontier.removeFirst());
                domain.add(current);
                for (int candidateIdx = 0; candidateIdx < options.size(); candidateIdx++) {
                    if (visited.contains(candidateIdx)
                            || Collections.disjoint(current.domain(),
                            options.get(candidateIdx).domain())) {
                        continue;
                    }
                    visited.add(candidateIdx);
                    frontier.addLast(candidateIdx);
                }
            }
            domain.sort(Comparator.comparingInt(option -> option.unit().index()));
            domains.add(List.copyOf(domain));
        }
        return List.copyOf(domains);
    }

    // Get the planned car travel direction
    private double plannedDriveDirection(ControlDemand demand) {
        double requested = Math.signum(demand.driveDirection());
        return requested != 0.0D ? requested : Math.signum(
                demand.force().dot(controllerForwardRoot()));
    }

    // Apply the allocated control
    private void applyAllocatedControl(int idx, @Nullable Actuator actuator, double control) {
        if (actuator == null || (!controlValueChanged(appliedControlValues.get(idx), control)
                && !actuator.requiresContinuousControl())) {
            return;
        }
        actuator.apply(control);
        appliedControlValues.put(idx, control);
    }

    // Apply the SCM wheel steering
    private void applyScmWheelSteering(
            ShipControlMap currentMap,
            ControlDemand demand,
            Telemetry telemetry,
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        if (!isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            for (WheelMountControlBridge wheel : controlledScmWheels) {
                wheel.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
            }
            return;
        }
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        String topologyFingerprint = topologyFingerprint(topology);
        Set<UUID> primaryBodyIds = primaryCarriageBodyIds(topology);
        boolean restrictToPrimary = restrictGroundDriveToPrimary(topology, primaryBodyIds);
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        boolean explicitRouting = configuration != null
                && configuration.isConfiguredFor(currentMap)
                && configuration.hasActionBindings();
        Vec3 torque = demand.torque();
        double torqueScale = Math.max(1.0D,
                Math.abs(torque.x) + Math.abs(torque.y) + Math.abs(torque.z));
        double steeringDemand = Mth.clamp(
                torque.dot(controllerUpRoot()) / torqueScale, -1.0D, 1.0D);
        double plannedDirection = plannedDriveDirection(demand);
        if (plannedDirection < 0.0D) {
            steeringDemand = -steeringDemand;
        }
        float steering = (float) steeringDemand;
        double requestedBrake = activeBrakeStrength(demand);
        // A temporary opposite drivetrain request is normal while following a
        // steering curve. It must not become a binary wheel brake; only the
        // explicit/progressive navigation Brake demand may engage the hubs.
        float brake = (float) Mth.clamp(requestedBrake, 0.0D, 1.0D);
        Set<String> profileFaceActions = activeProfileFaceActions(
                activeControlActionTypes(demand, currentMap), brake > 1.0E-5D);
        String actionFingerprint = profileFaceActions.stream().sorted()
                .reduce((left, right) -> left + '|' + right).orElse("");
        if (lastScmWheelRefreshTick == Long.MIN_VALUE
                || gameTime < lastScmWheelRefreshTick
                || gameTime - lastScmWheelRefreshTick >= 20L
                || !Objects.equals(lastScmWheelTopologyFingerprint, topologyFingerprint)
                || !Objects.equals(lastScmWheelActionFingerprint, actionFingerprint)) {
            Set<WheelMountControlBridge> current =
                    Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            Map<WheelMountControlBridge, List<ScmTarget>> currentTargets =
                    new java.util.IdentityHashMap<>();
            for (ScmTarget target : ContraptionNetworkLinkerData.scmTargets(
                    controller.getStoredLinker())) {
                if (restrictToPrimary && !primaryBodyIds.contains(target.subLevelId())) {
                    continue;
                }
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        level, target.subLevelId(), target.blockPosition());
                if (blockEntity instanceof WheelMountControlBridge wheel) {
                    if (isProfileWheelTargetSelected(
                            configuration, target, profileFaceActions)) {
                        current.add(wheel);
                    }
                    currentTargets.computeIfAbsent(wheel, ignored -> new ArrayList<>())
                            .add(target);
                }
            }
            // SCM configuration does not require linker targets. Select wheel
            // hubs from the live allocation map so a Yaw group can steer the
            // hubs the player assigned in the SCM tab. Explicit Brake faces
            // are also included even though braking has no force vector and
            // therefore normally carries zero allocator capacity.
            for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
                boolean profileWheelControl = explicitRouting
                        && isProfileWheelControlSelected(
                        configuration, unit, profileFaceActions);
                if (!unit.controllable() || !profileWheelControl
                        && unit.maxThrust() <= 1.0E-9D
                        || restrictToPrimary && !primaryBodyIds.contains(unit.subLevelId())) {
                    continue;
                }
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        level, unit.subLevelId(), unit.blockPosition());
                if (blockEntity instanceof WheelMountControlBridge wheel) {
                    current.add(wheel);
                }
            }
            for (WheelMountControlBridge prev : controlledScmWheels) {
                if (!current.contains(prev)) {
                    prev.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
                }
            }
            controlledScmWheels.clear();
            controlledScmWheels.addAll(current);
            controlledScmWheelTargets.clear();
            currentTargets.forEach((wheel, targets) -> {
                if (current.contains(wheel)) {
                    controlledScmWheelTargets.put(wheel, List.copyOf(targets));
                }
            });
            lastScmWheelRefreshTick = gameTime;
            lastScmWheelTopologyFingerprint = topologyFingerprint;
            lastScmWheelActionFingerprint = actionFingerprint;
        }
        if (controlledScmWheels.isEmpty()) {
            return;
        }
        // A face-routed WheelMount is controlled by the exact redstone plane
        // chosen in the SCM profile. Do not also inject left/right values at
        // the hub: that second path has no knowledge of the player's opposing
        // face assignment and is what turns a four-wheel steering setup into
        // crab steering. Releasing the direct overrides leaves only the
        // selected profile faces authoritative.
        if (explicitRouting && hasProfileWheelFaceBindings(level, configuration,
                profileFaceActions, restrictToPrimary, primaryBodyIds)) {
            for (WheelMountControlBridge wheel : controlledScmWheels) {
                wheel.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
            }
            return;
        }
        Map<WheelMountControlBridge, Set<WheelControl>> profileWheelControls =
                activeProfileWheelControls(level, configuration, currentMap,
                        profileFaceActions, steering, restrictToPrimary, primaryBodyIds);
        for (WheelMountControlBridge wheel : controlledScmWheels) {
            Set<WheelControl> controls = profileWheelControls.get(wheel);
            float directSteering = Math.abs(steering);
            // The selected action chooses the physical input. Do not infer
            // it from the torque sign: a Yaw Left group can deliberately
            // contain LEFT on one hub and RIGHT on another.
            wheel.ct$setDirectInputs(
                    controls != null && controls.contains(WheelControl.LEFT)
                            ? directSteering : 0.0F,
                    controls != null && controls.contains(WheelControl.RIGHT)
                            ? directSteering : 0.0F,
                    controls != null && controls.contains(WheelControl.BRAKE)
                            ? brake : 0.0F);
        }
    }

    // Check whether the active profile addresses any WheelMount through a
    // selected face. Both direct SCM face selections and linker face targets
    // count; their plane signal is the only allowed control path this tick.
    private boolean hasProfileWheelFaceBindings(
            Level level,
            ScmConfigurationProfile configuration,
            Set<String> activeActions,
            boolean restrictToPrimary,
            Set<UUID> primaryBodyIds
    ) {
        for (String action : activeActions) {
            for (ScmConfigurationProfile.UnitReference reference
                    : configuration.unitsForActions(Set.of(action))) {
                if (!reference.usesFaceControl()
                        || restrictToPrimary && !primaryBodyIds.contains(reference.subLevelId())) {
                    continue;
                }
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        level, reference.subLevelId(), reference.blockPosition());
                if (blockEntity instanceof WheelMountControlBridge) {
                    return true;
                }
            }
        }
        for (ScmTarget target : ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker())) {
            if (!target.usesFaceControl()
                    || restrictToPrimary && !primaryBodyIds.contains(target.subLevelId())) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, target.subLevelId(), target.blockPosition());
            if (!(blockEntity instanceof WheelMountControlBridge)) {
                continue;
            }
            for (String action : activeActions) {
                if (configuration.unitsForActions(Set.of(action)).stream()
                        .anyMatch(reference -> profileReferenceMatchesTarget(reference, target))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Determine whether a map unit is a player-routed wheel face for one of
    // the currently active SCM actions. This deliberately asks the profile,
    // not the Linker's face/default action metadata.
    private boolean isProfileWheelControlSelected(
            ScmConfigurationProfile configuration,
            ShipControlMap.PropulsionUnit unit,
            Set<String> activeActions
    ) {
        return wheelControlForScmUnit(unit) != null && activeActions.stream()
                .anyMatch(action -> configuration.unitsForActions(Set.of(action)).stream()
                        .anyMatch(reference -> configurationReferenceMatchesUnit(reference, unit)));
    }

    // Resolve the direct input for every configured wheel. The profile action
    // is authoritative: Yaw Left energises LEFT on every hub selected by its
    // group, and Yaw Right energises RIGHT. A selected face identifies the
    // hub to address; it does not cause the SCM to swap the requested action
    // based on that face's default linker semantics.
    private Map<WheelMountControlBridge, Set<WheelControl>> activeProfileWheelControls(
            Level level,
            ScmConfigurationProfile configuration,
            ShipControlMap currentMap,
            Set<String> activeActions,
            float steering,
            boolean restrictToPrimary,
            Set<UUID> primaryBodyIds
    ) {
        Map<WheelMountControlBridge, Set<WheelControl>> controls =
                new java.util.IdentityHashMap<>();
        List<ScmTarget> linkerTargets = ContraptionNetworkLinkerData.scmTargets(
                controller.getStoredLinker());

        // Linker face targets retain the same profile ownership rule as direct
        // selections. Never derive a second sign from a selected face here.
        for (ScmTarget target : linkerTargets) {
            if (!target.usesFaceControl()
                    || restrictToPrimary && !primaryBodyIds.contains(target.subLevelId())) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, target.subLevelId(), target.blockPosition());
            if (!(blockEntity instanceof WheelMountControlBridge wheel)) {
                continue;
            }
            for (String action : activeActions) {
                boolean selected = configuration.unitsForActions(Set.of(action)).stream()
                        .anyMatch(reference -> profileReferenceMatchesTarget(reference, target));
                if (!selected) {
                    continue;
                }
                WheelControl control = wheelControlForProfileAction(action, steering);
                if (control != null) {
                    controls.computeIfAbsent(wheel,
                                    ignored -> EnumSet.noneOf(WheelControl.class))
                            .add(control);
                }
            }
        }

        // Face-only profile selections do not need a linker. Their action is
        // applied uniformly to the selected WheelMount; the face is only the
        // player's precise target selector.
        for (String action : activeActions) {
            for (ScmConfigurationProfile.UnitReference reference
                    : configuration.unitsForActions(Set.of(action))) {
                WheelControl control = wheelControlForProfileAction(action, steering);
                if (control == null
                        || restrictToPrimary && !primaryBodyIds.contains(reference.subLevelId())) {
                    continue;
                }
                BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        level, reference.subLevelId(), reference.blockPosition());
                if (blockEntity instanceof WheelMountControlBridge wheel) {
                    controls.computeIfAbsent(wheel, ignored -> EnumSet.noneOf(WheelControl.class))
                            .add(control);
                }
            }
        }

        // Preserve support for profile references resolved through the live
        // map (including older profiles). All matching wheel probes collapse
        // to the same WheelMount instance, so one action still produces one
        // uniform direct input for that selected hub.
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            if (!unit.controllable()
                    || restrictToPrimary && !primaryBodyIds.contains(unit.subLevelId())) {
                continue;
            }
            if (wheelControlForScmUnit(unit) == null) {
                continue;
            }
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, unit.subLevelId(), unit.blockPosition());
            if (!(blockEntity instanceof WheelMountControlBridge wheel)) {
                continue;
            }
            for (String action : activeActions) {
                WheelControl requestedControl = wheelControlForProfileAction(action, steering);
                if (requestedControl == null
                        || configuration.unitsForActions(Set.of(action)).stream()
                        .noneMatch(reference -> configurationReferenceMatchesUnit(reference, unit))) {
                    continue;
                }
                controls.computeIfAbsent(wheel, ignored -> EnumSet.noneOf(WheelControl.class))
                        .add(requestedControl);
            }
        }
        return controls;
    }

    private boolean isProfileWheelTargetSelected(
            ScmConfigurationProfile configuration,
            ScmTarget target,
            Set<String> activeActions
    ) {
        return activeActions.stream()
                .anyMatch(action -> configuration.unitsForActions(Set.of(action)).stream()
                        .anyMatch(reference -> profileReferenceMatchesTarget(reference, target)));
    }

    private static boolean profileReferenceMatchesTarget(
            ScmConfigurationProfile.UnitReference reference, ScmTarget target
    ) {
        if (reference == null || target == null
                || !Objects.equals(reference.subLevelId(), target.subLevelId())
                || !reference.blockPosition().equals(target.blockPosition())) {
            return false;
        }
        // Adapter-qualified wheel probes are resolved through the live-map
        // fallback. Face bindings use the profile action uniformly instead.
        if (reference.adapter().contains("wheel_mount_")) {
            return false;
        }
        return !reference.usesFaceControl()
                || reference.face().equals(target.signalFace());
    }

    // A generic yaw command retains signed left/right behaviour. The explicit
    // directional actions deliberately do not inspect the target face: their
    // profile group already identifies every hub that must receive that exact
    // direct command.
    private static @Nullable WheelControl wheelControlForProfileAction(
            String action, float steering
    ) {
        return switch (action) {
            case "ship_yaw", "ship_pan" -> steering < -ACTION_DIRECTION_EPSILON
                    ? WheelControl.LEFT : steering > ACTION_DIRECTION_EPSILON
                    ? WheelControl.RIGHT : null;
            case "ship_yaw_left" -> WheelControl.LEFT;
            case "ship_yaw_right" -> WheelControl.RIGHT;
            case "ship_brake", "ship_decelerate" -> WheelControl.BRAKE;
            default -> null;
        };
    }

    private static @Nullable WheelControl wheelControlForScmUnit(
            ShipControlMap.PropulsionUnit unit
    ) {
        if (unit == null) {
            return null;
        }
        String adapter = unit.adapter();
        if (adapter.contains("wheel_mount_left")) {
            return WheelControl.LEFT;
        }
        if (adapter.contains("wheel_mount_right")) {
            return WheelControl.RIGHT;
        }
        return adapter.contains("wheel_mount_brake") ? WheelControl.BRAKE : null;
    }

    // Check if this has SCM ground drive
    private boolean hasScmGroundDrive() {
        return hasScmGroundDrive(activeAssemblyTopology);
    }

    // Check if this has SCM ground drive
    private boolean hasScmGroundDrive(@Nullable SableAssemblyTopologyApi.Topology topology) {
        Level level = controller.getLevel();
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        String topologyFingerprint = topologyFingerprint(topology);
        if (gameTime != Long.MIN_VALUE && groundDriveCheckTick == gameTime
                && Objects.equals(groundDriveTopologyFingerprint, topologyFingerprint)) {
            return cachedGroundDrive;
        }
        boolean groundDrive = !controlledScmWheels.isEmpty();
        Set<UUID> primaryBodyIds = primaryCarriageBodyIds(topology);
        boolean restrictToPrimary = restrictGroundDriveToPrimary(topology, primaryBodyIds);
        ShipControlMap currentMap = effectiveAssemblyMap();
        if (!groundDrive && currentMap != null) {
            for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
                if (restrictToPrimary && !primaryBodyIds.contains(unit.subLevelId())) {
                    continue;
                }
                if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                    continue;
                }
                Actuator actuator = controlActuators.get(unit.index());
                if (actuator != null && actuator.isAvailable()
                        && actuator.isKineticControl()) {
                    groundDrive = true;
                    break;
                }
            }
        }
        if (!groundDrive && level != null) {
            for (ScmTarget target : ContraptionNetworkLinkerData.scmTargets(
                    controller.getStoredLinker())) {
                if (restrictToPrimary && !primaryBodyIds.contains(target.subLevelId())) {
                    continue;
                }
                if (target.blockId().toLowerCase(Locale.ROOT).contains("wheel_mount")
                        || SimulatedHelper.findLoadedBlockEntityExact(
                        level, target.subLevelId(), target.blockPosition())
                        instanceof WheelMountControlBridge) {
                    groundDrive = true;
                    break;
                }
            }
        }
        groundDriveCheckTick = gameTime;
        groundDriveTopologyFingerprint = topologyFingerprint;
        cachedGroundDrive = groundDrive;
        return groundDrive;
    }

    // Control the value changed
    static boolean controlValueChanged(@Nullable Double previous, double current) {
        return previous == null || Math.abs(previous - current) > 1.0E-6D;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         TELEMETRY
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the telemetry
    private Telemetry telemetry() {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        ShipControlMap currentMap = effectiveAssemblyMap();
        if (root == null || currentMap == null) {
            return Telemetry.EMPTY;
        }
        Level level = controller.getLevel();
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        UUID rootId = root.getUniqueId();
        if (telemetryTick == gameTime && Objects.equals(telemetryRootId, rootId)) {
            return cachedTelemetry;
        }
        Telemetry telemetry = telemetry(root, liveCenterOfMass(root, currentMap.centerOfMass()));
        cacheTelemetry(root, telemetry);
        return telemetry;
    }

    // Cache the telemetry
    private void cacheTelemetry(ServerSubLevel root, Telemetry telemetry) {
        Level level = controller.getLevel();
        telemetryTick = level == null ? Long.MIN_VALUE : level.getGameTime();
        telemetryRootId = root == null ? null : root.getUniqueId();
        cachedTelemetry = telemetry == null ? Telemetry.EMPTY : telemetry;
    }

    // Invalidate the telemetry cache
    private void invalidateTelemetryCache() {
        telemetryTick = Long.MIN_VALUE;
        telemetryRootId = null;
        cachedTelemetry = Telemetry.EMPTY;
        simulationMetricsTick = Long.MIN_VALUE;
        simulationMetricsRootId = null;
        cachedSimulationMetrics = SimulationMetrics.EMPTY;
    }

    // Get the telemetry
    private Telemetry telemetry(ServerSubLevel root, Vec3 centerOfMass) {
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(root.getLevel());
        if (physicsSystem == null) {
            return Telemetry.EMPTY;
        }
        RigidBodyHandle handle = physicsSystem.getPhysicsHandle(root);
        if (handle == null || !handle.isValid()) {
            return Telemetry.EMPTY;
        }
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        Vec3 centerWorld = worldPosition(root.logicalPose(), centerOfMass);
        Vec3 originWorld = new Vec3(
                root.logicalPose().position().x(),
                root.logicalPose().position().y(),
                root.logicalPose().position().z());
        Vec3 pointVelocity = velocityAtPoint(
                new Vec3(linear.x, linear.y, linear.z),
                new Vec3(angular.x, angular.y, angular.z),
                originWorld, centerWorld);
        // Ground-drive feedback is the controlled root sub-level's velocity.
        // Wheel mounts are only command outputs, and a mass-weighted velocity
        // over coupled bodies can hide a stationary car's actual acceleration
        // or braking response. Air and articulated craft retain their
        // assembly-centre telemetry below.
        if (isControlMode(ScmBuiltinControlModes.CAR_ID)) {
            pointVelocity = new Vec3(linear.x, linear.y, linear.z);
        } else {
            pointVelocity = assemblyCenterVel(root, pointVelocity);
        }
        Vector3d euler = root.logicalPose().orientation().getEulerAnglesXYZ(new Vector3d());
        return new Telemetry(true, centerWorld,
                pointVelocity,
                new Vec3(angular.x, angular.y, angular.z),
                new Vec3(Math.toDegrees(euler.x), Math.toDegrees(euler.y), Math.toDegrees(euler.z)));
    }

    // Get the assembly center vel
    private Vec3 assemblyCenterVel(ServerSubLevel root, Vec3 fallback) {
        Vec3 weightedVelocity = Vec3.ZERO;
        double totalMass = 0.0D;
        for (SubLevel connected : connectedShipSubLevels(root)) {
            if (!(connected instanceof ServerSubLevel body)
                    || body.getMassTracker() == null) {
                continue;
            }
            double mass = body.getMassTracker().getMass();
            Vector3dc localCenter = body.getMassTracker().getCenterOfMass();
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (!Double.isFinite(mass) || mass <= 0.0D || localCenter == null
                    || handle == null || !handle.isValid()) {
                continue;
            }
            Vector3d linear = handle.getLinearVelocity(new Vector3d());
            Vector3d angular = handle.getAngularVelocity(new Vector3d());
            Vec3 bodyOrigin = new Vec3(
                    body.logicalPose().position().x(),
                    body.logicalPose().position().y(),
                    body.logicalPose().position().z());
            Vec3 bodyCenter = worldPosition(body.logicalPose(),
                    new Vec3(localCenter.x(), localCenter.y(), localCenter.z()));
            Vec3 centerVelocity = velocityAtPoint(
                    new Vec3(linear.x, linear.y, linear.z),
                    new Vec3(angular.x, angular.y, angular.z),
                    bodyOrigin, bodyCenter);
            weightedVelocity = weightedVelocity.add(centerVelocity.scale(mass));
            totalMass += mass;
        }
        return totalMass <= 1.0E-9D
                ? finite(fallback) : weightedVelocity.scale(1.0D / totalMass);
    }

    // Get the target point telemetry
    private Telemetry targetPointTelemetry(
            Telemetry centerTelemetry,
            ShipTargetPoint targetPoint,
            int connectorIdx
    ) {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        ShipTargetPoint selected = targetPoint == null
                ? ShipTargetPoint.CENTER_OF_MASS : targetPoint;
        if (!centerTelemetry.available() || root == null
                || selected.centerOfMass()) {
            return centerTelemetry;
        }

        if (selected.dockingConnector()) {
            ShipControlMap.DockingConnector connector = mappedConnector(connectorIdx);
            if (connector == null) {
                return centerTelemetry;
            }
            MappedDockingConnector mapped = mappedDockingConnector(root, connector);
            Vec3 worldPoint = mapped.worldTipPosition();
            Vec3 pointVelocity = connectorPointVelocity(
                    connector.subLevelId(), worldPoint, centerTelemetry);
            return new Telemetry(
                    true, worldPoint, pointVelocity,
                    centerTelemetry.angularVelocity(), centerTelemetry.eulerDegrees());
        }

        Vec3 forward = controllerForwardRoot();
        Vec3 up = controllerUpRoot();
        Vec3 right = normalize(
                forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 rootPoint = targetPointForHull(
                connectedShipHullCornersRoot(root), right, up, forward,
                selected, effectiveAssemblyMap() == null
                        ? Vec3.ZERO : effectiveAssemblyMap().centerOfMass());
        Vec3 worldPoint = worldPosition(root.logicalPose(), rootPoint);
        Vec3 pointVelocity = velocityAtPoint(
                centerTelemetry.velocity(), centerTelemetry.angularVelocity(),
                centerTelemetry.position(), worldPoint);
        return new Telemetry(
                true, worldPoint, pointVelocity,
                centerTelemetry.angularVelocity(), centerTelemetry.eulerDegrees());
    }

    // Get the mapped connector
    private @Nullable ShipControlMap.DockingConnector mappedConnector(int requestedIdx) {
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        List<ShipControlMap.DockingConnector> connectors = availableDockingConnectors(root);
        if (connectors.isEmpty()) {
            return null;
        }
        if (requestedIdx >= 0) {
            for (ShipControlMap.DockingConnector connector : connectors) {
                if (connector.index() == requestedIdx) {
                    return connector;
                }
            }
        }
        return connectors.getFirst();
    }

    // Get the docking alignment torque
    private Vec3 dockingAlignmentTorque(Telemetry telemetry, ActiveShipCommand command) {
        ShipControlMap.DockingConnector connector = mappedConnector(
                command.targetConnectorIndex());
        if (connector == null) {
            return Vec3.ZERO;
        }
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return Vec3.ZERO;
        }
        MappedDockingConnector mapped = mappedDockingConnector(root, connector);
        Vec3 currentWorld = normalize(mapped.worldFacing(), Vec3.ZERO);
        Vec3 desiredWorld = normalize(command.targetDirection(), currentWorld);
        Vec3 errorWorld = currentWorld.cross(desiredWorld).scale(2.4D);
        Vec3 requestedUp = perpendicularTo(command.targetUp(), desiredWorld);
        Vec3 currentUp = perpendicularTo(mapped.worldUp(), currentWorld);
        if (requestedUp.lengthSqr() > 1.0E-12D && currentUp.lengthSqr() > 1.0E-12D) {
            Vec3 upError = currentUp.cross(requestedUp);
            if (currentUp.dot(requestedUp) < -0.98D) {
                upError = desiredWorld;
            }
            errorWorld = errorWorld.add(upError.scale(1.6D));
        }
        Vec3 angularRoot = worldDirectionToRoot(telemetry.angularVelocity());
        return clampComponents(worldDirectionToRoot(errorWorld)
                .add(angularRoot.scale(-0.55D)));
    }

    // Check if the docking direction was reached
    private boolean dockingDirectionReached(ActiveShipCommand command) {
        return dockingDirectionWithin(command, 8.0D);
    }

    // Check connector-relative facing and roll alignment using one shared
    // tolerance for approach gating and final command completion.
    private boolean dockingDirectionWithin(
            ActiveShipCommand command,
            double toleranceDegrees
    ) {
        if (command.targetDirection().lengthSqr() <= 1.0E-12D) {
            return true;
        }
        ShipControlMap.DockingConnector connector = mappedConnector(
                command.targetConnectorIndex());
        if (connector == null) {
            return false;
        }
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return false;
        }
        MappedDockingConnector mapped = mappedDockingConnector(root, connector);
        Vec3 currentWorld = normalize(mapped.worldFacing(), Vec3.ZERO);
        Vec3 targetWorld = normalize(command.targetDirection(), Vec3.ZERO);
        double alignment = Math.cos(Math.toRadians(Mth.clamp(
                Math.abs(finite(toleranceDegrees)), 0.1D, 90.0D)));
        boolean facingAligned = currentWorld.lengthSqr() > 1.0E-12D
                && targetWorld.lengthSqr() > 1.0E-12D
                && currentWorld.dot(targetWorld) >= alignment;
        if (!facingAligned || command.targetUp().lengthSqr() <= 1.0E-12D) {
            return facingAligned;
        }
        Vec3 currentUp = perpendicularTo(mapped.worldUp(), currentWorld);
        Vec3 targetUp = perpendicularTo(command.targetUp(), targetWorld);
        return currentUp.lengthSqr() > 1.0E-12D
                && targetUp.lengthSqr() > 1.0E-12D
                && currentUp.dot(targetUp) >= alignment;
    }

    // Get the docking connector up
    private static Vec3 dockingConnectorUp(Vec3 facing) {
        Vec3 normal = normalize(facing, new Vec3(0.0D, 0.0D, -1.0D));
        Vec3 reference = Math.abs(normal.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(0.0D, 0.0D, 1.0D);
        return perpendicularTo(reference, normal);
    }

    // Get the perpendicular
    private static Vec3 perpendicularTo(Vec3 val, Vec3 normal) {
        Vec3 axis = normalize(normal, Vec3.ZERO);
        if (axis.lengthSqr() <= 1.0E-12D) {
            return Vec3.ZERO;
        }
        return normalize(val.subtract(axis.scale(val.dot(axis))), Vec3.ZERO);
    }

    // Get the connected ship hull corners root
    private List<Vec3> connectedShipHullCornersRoot(
            ServerSubLevel root
    ) {
        List<Vec3> corners = new ArrayList<>();
        for (SubLevel subLevel : connectedShipSubLevels(root)) {
            var bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == null
                    || bounds.maxX() < bounds.minX()
                    || bounds.maxY() < bounds.minY()
                    || bounds.maxZ() < bounds.minZ()) {
                continue;
            }
            double minX = bounds.minX();
            double minY = bounds.minY();
            double minZ = bounds.minZ();
            double maxX = bounds.maxX() + 1.0D;
            double maxY = bounds.maxY() + 1.0D;
            double maxZ = bounds.maxZ() + 1.0D;
            for (double x : new double[]{minX, maxX}) {
                for (double y : new double[]{minY, maxY}) {
                    for (double z : new double[]{minZ, maxZ}) {
                        corners.add(rootPosition(
                                root, subLevel, new Vec3(x, y, z)));
                    }
                }
            }
        }
        return List.copyOf(corners);
    }

    // Get the target point for hull
    static Vec3 targetPointForHull(
            Collection<Vec3> hullPoints,
            Vec3 rightDirection,
            Vec3 upDirection,
            Vec3 forwardDirection,
            ShipTargetPoint targetPoint,
            Vec3 fallback
    ) {
        ShipTargetPoint selected = targetPoint == null
                ? ShipTargetPoint.CENTER_OF_MASS : targetPoint;
        if (selected.centerOfMass() || hullPoints == null
                || hullPoints.isEmpty()) {
            return finite(fallback);
        }

        Vec3 right = normalize(
                rightDirection, new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 up = normalize(
                upDirection, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 forward = normalize(
                forwardDirection, new Vec3(0.0D, 0.0D, 1.0D));
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;
        double minUp = Double.POSITIVE_INFINITY;
        double maxUp = Double.NEGATIVE_INFINITY;
        double minForward = Double.POSITIVE_INFINITY;
        double maxForward = Double.NEGATIVE_INFINITY;
        for (Vec3 point : hullPoints) {
            Vec3 finitePoint = finite(point);
            double rightProjection = finitePoint.dot(right);
            double upProjection = finitePoint.dot(up);
            double forwardProjection = finitePoint.dot(forward);
            minRight = Math.min(minRight, rightProjection);
            maxRight = Math.max(maxRight, rightProjection);
            minUp = Math.min(minUp, upProjection);
            maxUp = Math.max(maxUp, upProjection);
            minForward = Math.min(minForward, forwardProjection);
            maxForward = Math.max(maxForward, forwardProjection);
        }
        if (!Double.isFinite(minRight) || !Double.isFinite(maxRight)
                || !Double.isFinite(minUp) || !Double.isFinite(maxUp)
                || !Double.isFinite(minForward)
                || !Double.isFinite(maxForward)) {
            return finite(fallback);
        }

        return right.scale(axisTarget(
                        minRight, maxRight, selected.right()))
                .add(up.scale(axisTarget(
                        minUp, maxUp, selected.up())))
                .add(forward.scale(axisTarget(
                        minForward, maxForward, selected.forward())));
    }

    // Get the axis target
    private static double axisTarget(
            double minimum,
            double maximum,
            int selector
    ) {
        if (selector < 0) {
            return minimum;
        }
        if (selector > 0) {
            return maximum;
        }
        return (minimum + maximum) * 0.5D;
    }

    // Get the velocity at point
    static Vec3 velocityAtPoint(
            Vec3 referenceVelocity,
            Vec3 angularVelocity,
            Vec3 referencePosition,
            Vec3 targetPosition
    ) {
        Vec3 vel = finite(referenceVelocity);
        Vec3 angular = finite(angularVelocity);
        Vec3 offset = finite(targetPosition).subtract(finite(referencePosition));
        return vel.add(angular.cross(offset));
    }

    // Get the target approach force
    private Vec3 targetApproachForce(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            @Nullable Vec3 requestedDirection,
            Vec3 targetPosition
    ) {
        return targetApproachForce(
                commandKey, telemetry, command, requestedDirection,
                targetPosition, command.tolerance(), command.targetSpeed());
    }

    // Get the target approach force
    private Vec3 targetApproachForce(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            @Nullable Vec3 requestedDirection,
            Vec3 targetPosition,
            double tolerance,
            double speedLimit
    ) {
        return targetApproachForce(
                commandKey, telemetry, command, requestedDirection,
                targetPosition, targetPosition, tolerance, 0.25D, speedLimit);
    }

    // Get the target approach force
    private Vec3 targetApproachForce(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            @Nullable Vec3 requestedDirection,
            Vec3 targetPosition,
            Vec3 speedTarget,
            double tolerance,
            double distanceResponse,
            double speedLimit
    ) {
        Vec3 error = finite(targetPosition).subtract(telemetry.position());
        Vec3 speedError = finite(speedTarget).subtract(telemetry.position());
        Vec3 dir = requestedDirection == null
                ? normalize(error, Vec3.ZERO)
                : normalize(requestedDirection, Vec3.ZERO);
        Vec3 direct = normalize(error, Vec3.ZERO);
        boolean followsTarget = direct.lengthSqr() <= 1.0E-12D
                || dir.dot(direct) >= 0.9D;
        Vec3 integral = advancePositionIntegral(
                positionIntegralErrors.getOrDefault(commandKey, Vec3.ZERO),
                error, telemetry.velocity(), command.targetSpeed(),
                tolerance, followsTarget);
        positionIntegralErrors.put(commandKey, integral);
        return positionControlForce(
                error, speedError.length(), telemetry.velocity(), dir,
                Math.min(command.targetSpeed(), Math.max(0.0D, finite(speedLimit))),
                tolerance, integral, distanceResponse);
    }

    // Advance the position integral
    static Vec3 advancePositionIntegral(
            Vec3 previous,
            Vec3 positionError,
            Vec3 velocity,
            double maximumSpeed,
            double tolerance,
            boolean followsTarget
    ) {
        Vec3 accumulated = finite(previous);
        Vec3 error = finite(positionError);
        double integrationRange = Math.max(
                POSITION_INTEGRAL_RANGE, Math.max(0.0D, finite(tolerance)) * 4.0D);
        double integrationSpeed = Math.max(
                1.0D, Math.max(0.0D, finite(maximumSpeed)) * 2.0D);
        if (followsTarget && error.length() <= integrationRange
                && finite(velocity).length() <= integrationSpeed) {
            accumulated = accumulated.add(error.scale(CONTROL_TICK_SECONDS));
        } else {
            accumulated = accumulated.scale(0.9D);
        }
        if (accumulated.lengthSqr()
                > POSITION_INTEGRAL_LIMIT * POSITION_INTEGRAL_LIMIT) {
            accumulated = accumulated.normalize().scale(POSITION_INTEGRAL_LIMIT);
        }
        return accumulated;
    }

    // Get the position control force
    static Vec3 positionControlForce(
            Vec3 positionError,
            Vec3 velocity,
            @Nullable Vec3 requestedDirection,
            double maximumSpeed,
            double tolerance,
            Vec3 accumulatedError
    ) {
        return positionControlForce(positionError, velocity, requestedDirection,
                maximumSpeed, tolerance, accumulatedError, 0.25D);
    }

    // Get the position control force
    private static Vec3 positionControlForce(
            Vec3 positionError,
            Vec3 vel,
            @Nullable Vec3 requestedDirection,
            double maximumSpeed,
            double tolerance,
            Vec3 accumulatedError,
            double distanceResponse
    ) {
        Vec3 error = finite(positionError);
        return positionControlForce(error, error.length(), vel, requestedDirection,
                maximumSpeed, tolerance, accumulatedError, distanceResponse);
    }

    // Keep a route waypoint as the steering target while applying its speed
    // envelope to the actual staging/final target. This avoids stopping at
    // every collision-avoidance point during an airship docking approach.
    private static Vec3 positionControlForce(
            Vec3 positionError,
            double speedReferenceDistance,
            Vec3 vel,
            @Nullable Vec3 requestedDirection,
            double maximumSpeed,
            double tolerance,
            Vec3 accumulatedError,
            double distanceResponse
    ) {
        Vec3 error = finite(positionError);
        Vec3 dir = requestedDirection == null
                ? normalize(error, Vec3.ZERO)
                : normalize(requestedDirection, Vec3.ZERO);
        double desiredSpeed = targetSpeedForDistance(
                speedReferenceDistance, tolerance, maximumSpeed, distanceResponse);
        Vec3 desiredVelocity = dir.scale(desiredSpeed);
        return desiredVelocity.subtract(finite(vel))
                .scale(POSITION_VELOCITY_GAIN)
                .add(finite(accumulatedError).scale(POSITION_INTEGRAL_GAIN));
    }

    // Get the dock control target
    private Vec3 dockControlTarget(
            Telemetry telemetry,
            ActiveShipCommand command
    ) {
        Vec3 target = command.targetPosition();
        Vec3 error = target.subtract(telemetry.position());
        if (error.lengthSqr() <= 1.0E-12D) {
            return target;
        }
        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null) {
            return target;
        }
        Vec3 dir = error.normalize();
        double hullExtent = shipHullExtent(
                telemetry.position(), dir, ctx.shipSubLevels());
        double blockingDistance =
                collisionDistance(telemetry.position(), dir);
        return dockControlTarget(
                telemetry.position(), target, hullExtent, blockingDistance,
                command.tolerance());
    }

    // Get the dock control target
    static Vec3 dockControlTarget(
            Vec3 currentPosition,
            Vec3 requestedTarget,
            double hullExtent,
            double blockingDistance,
            double tolerance
    ) {
        Vec3 current = finite(currentPosition);
        Vec3 target = finite(requestedTarget);
        Vec3 error = target.subtract(current);
        double distance = error.length();
        if (distance <= 1.0E-9D) {
            return target;
        }
        double extent = Math.max(0.0D, finite(hullExtent));
        double blocking = Math.max(0.0D, finite(blockingDistance));
        if (blocking >= COLLISION_SCAN_RANGE - 1.0E-6D) {
            return target;
        }
        double obstacleDistance = extent + COLLISION_HULL_MARGIN
                + blocking;
        double contactWindow = Math.max(1.0D,
                Math.max(0.0D, finite(tolerance)) * 2.0D);
        if (Math.abs(obstacleDistance - distance) > contactWindow) {
            return target;
        }
        double approachDistance =
                Math.max(0.0D, distance - extent - COLLISION_HULL_MARGIN);
        return current.add(error.scale(approachDistance / distance));
    }

    // Get the target speed for distance
    static double targetSpeedForDistance(double distance, double tolerance, double maximumSpeed) {
        return targetSpeedForDistance(distance, tolerance, maximumSpeed, 0.25D);
    }

    // Get the target speed for distance
    private static double targetSpeedForDistance(
            double distance,
            double tolerance,
            double maximumSpeed,
            double distanceResponse
    ) {
        double targetDistance = Math.max(0.0D, finite(distance));
        double captureRadius = Math.min(
                0.05D, Math.max(0.01D, Math.max(0.0D, finite(tolerance)) * 0.1D));
        if (targetDistance <= captureRadius) {
            return 0.0D;
        }
        return Math.min(Math.max(0.0D, finite(maximumSpeed)),
                targetDistance * Math.max(0.0D, finite(distanceResponse)));
    }

    // Get the range required to observe the current speed's complete braking
    // envelope. The extra look-ahead time gives the controller room to begin
    // reducing propulsion before it reaches its calculated stop point.
    static double navigationCollisionScanRange(double currentSpeed) {
        double speed = Math.max(0.0D, Math.abs(finite(currentSpeed)));
        return Math.max(COLLISION_SCAN_RANGE,
                NAVIGATION_MIN_CLEARANCE
                        + speed * NAVIGATION_LOOKAHEAD_SECONDS
                        + speed * speed / (2.0D * NAVIGATION_BRAKING_ACCELERATION));
    }

    // Get the maximum travel speed which can stop inside the measured
    // clearance after the controller's response delay.
    static double navigationSafeTravelSpeed(double clearance) {
        return navigationSafeTravelSpeed(clearance, NAVIGATION_MIN_CLEARANCE);
    }

    // Get the maximum safe speed for one vehicle clearance margin.
    private static double navigationSafeTravelSpeed(
            double clearance,
            double minimumClearance
    ) {
        double brakingDistance = Math.max(0.0D,
                finite(clearance) - Math.max(0.0D, finite(minimumClearance)));
        double acceleration = NAVIGATION_BRAKING_ACCELERATION;
        return Math.max(0.0D, acceleration * (Math.sqrt(
                NAVIGATION_RESPONSE_SECONDS * NAVIGATION_RESPONSE_SECONDS
                        + 2.0D * brakingDistance / acceleration)
                - NAVIGATION_RESPONSE_SECONDS));
    }

    // Get the speed which can stop at a command target instead of at an
    // obstacle. Tolerance is the capture radius, not extra braking room.
    static double navigationStoppingSpeed(double distance, double tolerance) {
        double brakingDistance = Math.max(0.0D,
                finite(distance) - Math.max(0.0D, finite(tolerance)));
        double acceleration = NAVIGATION_BRAKING_ACCELERATION;
        return Math.max(0.0D, acceleration * (Math.sqrt(
                NAVIGATION_RESPONSE_SECONDS * NAVIGATION_RESPONSE_SECONDS
                        + 2.0D * brakingDistance / acceleration)
                - NAVIGATION_RESPONSE_SECONDS));
    }

    // Get the navigation required clearance.
    static double navigationRequiredClearance(double requestedSpeed, double currentSpeed) {
        double speed = Math.max(Math.abs(finite(requestedSpeed)), Math.abs(finite(currentSpeed)));
        return navigationCollisionScanRange(speed);
    }

    // Check if this uses aircraft navigation
    static boolean usesAircraftNavigation(
            @Nullable ShipControlMap candidate,
            Vec3 forwardDirection,
            Vec3 upDirection
    ) {
        return usesAircraftNavigation(
                candidate, forwardDirection, upDirection, null);
    }

    // Check if this uses aircraft navigation
    private static boolean usesAircraftNavigation(
            @Nullable ShipControlMap candidate,
            Vec3 forwardDirection,
            Vec3 upDirection,
            @Nullable Set<UUID> authorityBodyIds
    ) {
        if (candidate == null) {
            return false;
        }
        boolean restrictAuthority = authorityBodyIds != null
                && !authorityBodyIds.isEmpty();
        Vec3 forward = normalize(forwardDirection, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 up = normalize(upDirection, new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        boolean hasForwardPropulsion = false;
        for (ShipControlMap.PropulsionUnit unit : candidate.units()) {
            if (restrictAuthority && !authorityBodyIds.contains(unit.subLevelId())) {
                continue;
            }
            if (!unit.controllable() || unit.maxThrust() <= 1.0E-9D) {
                continue;
            }
            Vec3 dir = normalize(unit.forceDirection(), Vec3.ZERO);
            hasForwardPropulsion |= dir.dot(forward) >= AIRCRAFT_FORWARD_ALIGNMENT;
            if (isDirectManeuveringDirection(dir, up, right)) {
                return false;
            }
        }
        for (ShipControlMap.BearingUnit bearing : candidate.bearings()) {
            if (restrictAuthority
                    && !authorityBodyIds.contains(bearing.hostSubLevelId())) {
                continue;
            }
            for (ShipControlMap.BearingPose pose : bearing.poses()) {
                for (ShipControlMap.BearingResponse resp : pose.responses()) {
                    int unitIndex = resp.propulsionUnitIndex();
                    if (unitIndex < 0 || unitIndex >= candidate.units().size()
                            || !candidate.units().get(unitIndex).controllable()
                            || restrictAuthority && !authorityBodyIds.contains(
                            candidate.units().get(unitIndex).subLevelId())
                            || resp.maxThrust() <= 1.0E-9D) {
                        continue;
                    }
                    Vec3 dir = normalize(resp.forceDirection(), Vec3.ZERO);
                    hasForwardPropulsion |=
                            dir.dot(forward) >= AIRCRAFT_FORWARD_ALIGNMENT;
                    if (isDirectManeuveringDirection(dir, up, right)) {
                        return false;
                    }
                }
            }
        }
        boolean hasAerodynamicControls = candidate.bearings().stream()
                .filter(bearing -> !restrictAuthority
                        || authorityBodyIds.contains(bearing.hostSubLevelId()))
                .flatMap(bearing -> bearing.poses().stream())
                .flatMap(pose -> pose.aerodynamicSurfaces().stream())
                .anyMatch(surface -> !restrictAuthority
                        || authorityBodyIds.contains(surface.subLevelId()));
        return hasForwardPropulsion && hasAerodynamicControls;
    }

    // Get the primary carriage body ids
    private static Set<UUID> primaryCarriageBodyIds(
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        if (topology == null || !topology.available()) {
            return Set.of();
        }
        return topology.carriagePartitions().stream()
                .filter(SableAssemblyTopologyApi.CarriagePartition::primary)
                .findFirst()
                .map(partition -> Set.copyOf(partition.bodyIds()))
                .orElseGet(Set::of);
    }

    // Only let the lead SCM carriage steer the route
    private static boolean restrictGroundDriveToPrimary(
            @Nullable SableAssemblyTopologyApi.Topology topology,
            Set<UUID> primaryBodyIds
    ) {
        return topology != null && topology.available()
                && topology.carriagePartitions().size() > 1
                && primaryBodyIds != null && !primaryBodyIds.isEmpty();
    }

    // Build the ship topology fingerprint
    private static String topologyFingerprint(
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        return topology != null && topology.available() ? topology.fingerprint() : "";
    }

    // Check if this is direct maneuvering direction
    private static boolean isDirectManeuveringDirection(
            Vec3 dir,
            Vec3 up,
            Vec3 right
    ) {
        return Math.abs(dir.dot(up)) >= AIRCRAFT_CONTROL_ALIGNMENT
                || Math.abs(dir.dot(right)) >= AIRCRAFT_CONTROL_ALIGNMENT;
    }

    // Get the horizontal distance
    static double horizontalDistance(Vec3 first, Vec3 second) {
        if (first == null || second == null) {
            return 0.0D;
        }
        return Math.hypot(second.x - first.x, second.z - first.z);
    }

    // Get the aircraft safe clearance
    static double aircraftSafeClearance(double requestedSpeed, double currentSpeed) {
        double speed = Math.max(Math.abs(finite(requestedSpeed)), Math.abs(finite(currentSpeed)));
        return Math.max(AIRCRAFT_MIN_SAFE_CLEARANCE,
                navigationCollisionScanRange(speed));
    }

    // Get the aircraft approach distance
    static double aircraftApproachDistance(double safeClearance, double flightSpeed) {
        return Math.max(AIRCRAFT_MIN_APPROACH_DISTANCE,
                Math.max(0.0D, finite(safeClearance)) * 1.5D
                        + Math.max(0.0D, finite(flightSpeed)) * 12.0D);
    }

    // Get the aircraft approach clearance
    static double aircraftApproachClearance(
            double horizontalDistance,
            double approachDistance,
            double safeClearance
    ) {
        double denominator = Math.max(1.0D,
                finite(approachDistance) - AIRCRAFT_LANDING_PHASE_DISTANCE);
        double fraction = Mth.clamp(
                (finite(horizontalDistance) - AIRCRAFT_LANDING_PHASE_DISTANCE)
                        / denominator,
                0.0D, 1.0D);
        fraction = fraction * fraction * (3.0D - 2.0D * fraction);
        return Mth.lerp(
                fraction, AIRCRAFT_LANDING_CLEARANCE,
                Math.max(AIRCRAFT_LANDING_CLEARANCE, finite(safeClearance)));
    }

    // Get the next aircraft flight phase
    static AircraftFlightPhase nextAircraftFlightPhase(
            @Nullable AircraftFlightPhase current,
            double groundClearance,
            double horizontalDistance,
            double safeClearance,
            double approachDistance
    ) {
        double clearance = Math.max(0.0D, finite(groundClearance));
        double distance = Math.max(0.0D, finite(horizontalDistance));
        double safe = Math.max(AIRCRAFT_MIN_SAFE_CLEARANCE, finite(safeClearance));
        AircraftFlightPhase phase = current == null
                ? clearance >= safe ? AircraftFlightPhase.CRUISE : AircraftFlightPhase.CLIMB
                : current;
        return switch (phase) {
            case CLIMB -> clearance < safe
                    ? AircraftFlightPhase.CLIMB
                    : distance <= approachDistance
                    ? AircraftFlightPhase.APPROACH : AircraftFlightPhase.CRUISE;
            case CRUISE -> clearance < safe * 0.6D
                    ? AircraftFlightPhase.CLIMB
                    : distance <= approachDistance
                    ? AircraftFlightPhase.APPROACH : AircraftFlightPhase.CRUISE;
            case APPROACH -> distance <= AIRCRAFT_LANDING_PHASE_DISTANCE
                    ? AircraftFlightPhase.LANDING : AircraftFlightPhase.APPROACH;
            case LANDING -> AircraftFlightPhase.LANDING;
        };
    }

    // Check if the aircraft reached the landing target
    static boolean aircraftLandingReached(
            double horizontalDistance,
            double verticalDistance,
            double groundClearance,
            Vec3 velocity,
            double tolerance
    ) {
        Vec3 motion = velocity == null ? Vec3.ZERO : velocity;
        double horizontalSpeed = Math.hypot(motion.x, motion.z);
        return horizontalDistance <= Math.max(1.25D, finite(tolerance))
                && Math.abs(finite(verticalDistance))
                        <= Math.max(1.25D, finite(tolerance))
                && groundClearance <= 1.25D
                && horizontalSpeed <= 0.18D
                && Math.abs(motion.y) <= 0.12D;
    }

    // Get the aircraft altitude error
    static double aircraftAltitudeError(
            @Nullable AircraftFlightPhase phase,
            double currentY,
            double targetY,
            double groundClearance,
            double minimumClearance
    ) {
        double clearanceError = finite(minimumClearance) - finite(groundClearance);
        if (phase == null || phase == AircraftFlightPhase.CLIMB) {
            return clearanceError;
        }
        double targetError = finite(targetY) - finite(currentY);
        return Math.max(targetError, clearanceError);
    }

    // Get the aircraft navigation distance
    static double aircraftNavigationDistance(
            @Nullable AircraftFlightPhase phase,
            Vec3 currentPosition,
            Vec3 targetPosition
    ) {
        if (currentPosition == null || targetPosition == null) {
            return 0.0D;
        }
        return phase == null || phase == AircraftFlightPhase.CLIMB
                ? horizontalDistance(currentPosition, targetPosition)
                : targetPosition.subtract(currentPosition).length();
    }

    // Check if the aircraft is aligned for approach
    static boolean aircraftApproachAligned(double headingErrorRadians) {
        return Math.abs(finite(headingErrorRadians)) <= Math.toRadians(35.0D);
    }

    // Check if the aircraft can turn
    static boolean aircraftMayTurn(
            @Nullable AircraftFlightPhase phase,
            boolean forwardHazard,
            double forwardSpeed,
            double desiredSpeed
    ) {
        double maneuverSpeed = Math.max(
                AIRCRAFT_MIN_MANEUVER_SPEED,
                Math.min(0.75D, Math.max(0.0D, finite(desiredSpeed)) * 0.55D));
        return phase != null && phase != AircraftFlightPhase.CLIMB
                && !forwardHazard && finite(forwardSpeed) >= maneuverSpeed;
    }

    // Get the aircraft pitch degrees
    static double aircraftPitchDegrees(Vec3 forwardWorld) {
        Vec3 forward = normalize(forwardWorld, new Vec3(0.0D, 0.0D, 1.0D));
        return Math.toDegrees(Math.asin(Mth.clamp(forward.y, -1.0D, 1.0D)));
    }

    // Get the aircraft bank degrees
    static double aircraftBankDegrees(Vec3 lateralWorld, Vec3 upWorld) {
        Vec3 lateral = normalize(lateralWorld, new Vec3(-1.0D, 0.0D, 0.0D));
        Vec3 up = normalize(upWorld, new Vec3(0.0D, 1.0D, 0.0D));
        return Math.toDegrees(Math.atan2(-lateral.y, up.y));
    }

    // Get the aircraft attitude control
    static AircraftAttitudeControl aircraftAttitudeControl(
            double altitudeCorrection,
            double headingErrorRadians,
            double currentPitchDegrees,
            double currentBankDegrees,
            double pitchRate,
            double rollRate,
            double yawRate,
            boolean mayTurn,
            boolean landing
    ) {
        double minimumPitch = landing ? -8.0D : -15.0D;
        double maximumPitch = landing ? 12.0D : 22.0D;
        double desiredPitch = Mth.clamp(
                finite(altitudeCorrection) * 24.0D, minimumPitch, maximumPitch);
        double pitchError = Mth.wrapDegrees(desiredPitch - finite(currentPitchDegrees));
        double pitch = Mth.clamp(
                pitchError / 25.0D - finite(pitchRate) * 0.32D,
                -0.65D, 0.65D);

        double bankLimit = landing ? 10.0D : 28.0D;
        double desiredBank = mayTurn
                ? Mth.clamp(Math.toDegrees(finite(headingErrorRadians)) * 0.7D,
                        -bankLimit, bankLimit)
                : 0.0D;
        double bankError = Mth.wrapDegrees(desiredBank - finite(currentBankDegrees));
        double roll = Mth.clamp(
                bankError / 30.0D - finite(rollRate) * 0.3D,
                -0.65D, 0.65D);
        double yaw = mayTurn
                ? Mth.clamp(
                        finite(headingErrorRadians) * 0.3D - finite(yawRate) * 0.2D,
                        -0.3D, 0.3D)
                : Mth.clamp(-finite(yawRate) * 0.3D, -0.25D, 0.25D);
        return new AircraftAttitudeControl(
                pitch, roll, yaw, desiredPitch, desiredBank);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         NAVIGATION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the aircraft guidance
    private AircraftGuidance aircraftGuidance(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            Vec3 requestedTarget,
            Vec3 forwardRoot,
            Vec3 upRoot,
            Vec3 rightRoot,
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        // ------------------------------------TARGET HEADING------------------------------------
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 forwardWorld = normalize(
                rootDirectionToWorld(forwardRoot), new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 horizontalForward = normalize(
                new Vec3(forwardWorld.x, 0.0D, forwardWorld.z),
                new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 targetPosition = finite(requestedTarget);
        Vec3 targetDelta = targetPosition.subtract(telemetry.position());
        Vec3 requestedTravelDirection = command.avoidCollisions()
                ? navigationGuidance(
                        commandKey, telemetry, command, targetPosition).direction()
                : null;
        Vec3 horizontalTargetDelta = new Vec3(
                targetDelta.x, 0.0D, targetDelta.z);
        Vec3 horizontalAvoidance = requestedTravelDirection == null
                ? Vec3.ZERO
                : new Vec3(
                        requestedTravelDirection.x, 0.0D,
                        requestedTravelDirection.z);
        boolean avoidanceBlocked = command.avoidCollisions()
                && requestedTravelDirection != null
                && requestedTravelDirection.lengthSqr() <= 1.0E-12D
                && targetDelta.lengthSqr() > 1.0E-12D;
        Vec3 horizontalTarget = avoidanceBlocked
                ? Vec3.ZERO
                : horizontalAvoidance.lengthSqr() > 1.0E-12D
                ? horizontalAvoidance
                : horizontalTargetDelta;
        double targetDistance = horizontalTargetDelta.length();
        Vec3 targetDirection = normalize(horizontalTarget, horizontalForward);
        double headingError = Math.atan2(
                horizontalForward.cross(targetDirection).y,
                Mth.clamp(horizontalForward.dot(targetDirection), -1.0D, 1.0D));
        boolean approachAligned = aircraftApproachAligned(headingError);
        double forwardSpeed = telemetry.velocity().dot(horizontalForward);
        double safeClearance = aircraftSafeClearance(
                command.targetSpeed(), telemetry.velocity().length());
        double collisionRange = navigationCollisionScanRange(telemetry.velocity().length());
        double approachDistance =
                aircraftApproachDistance(safeClearance, command.targetSpeed());
        double groundClearance =
                collisionDistance(telemetry.position(), worldUp.scale(-1.0D), collisionRange);

        // -----------------------------------------------------FLIGHT PHASE-----------------------------------------------------
        AircraftNavigationState state = aircraftNavigationStates.computeIfAbsent(
                commandKey, ignored -> new AircraftNavigationState());
        AircraftFlightPhase nextPhase = nextAircraftFlightPhase(
                state.phase, groundClearance, targetDistance,
                safeClearance, approachDistance);
        state.phase = nextPhase == AircraftFlightPhase.LANDING && !approachAligned
                ? AircraftFlightPhase.APPROACH : nextPhase;

        // ------------------------------------FLIGHT TARGETS------------------------------------
        double desiredClearance = switch (state.phase) {
            case CLIMB, CRUISE -> safeClearance;
            case APPROACH -> approachAligned
                    ? aircraftApproachClearance(
                            targetDistance, approachDistance, safeClearance)
                    : safeClearance;
            case LANDING -> AIRCRAFT_LANDING_CLEARANCE;
        };
        double desiredSpeed = switch (state.phase) {
            case CLIMB -> Math.max(0.75D, command.targetSpeed());
            case CRUISE -> Math.max(0.65D, command.targetSpeed());
            case APPROACH -> Math.min(0.75D,
                    Math.max(0.35D, command.targetSpeed() * 0.6D));
            case LANDING -> targetDistance <= Math.max(1.5D, command.tolerance() * 2.0D)
                    ? 0.0D : 0.25D;
        };
        double baselineThrottle = switch (state.phase) {
            case CLIMB -> 0.4D;
            case CRUISE -> 0.25D;
            case APPROACH -> 0.12D;
            case LANDING -> 0.0D;
        };

        // ------------------------------------COLLISION RESPONSE------------------------------------
        double forwardClearance = command.avoidCollisions()
                ? Math.min(
                        collisionDistance(telemetry.position(), horizontalForward, collisionRange),
                        collisionDistance(telemetry.position(), targetDirection, collisionRange))
                : collisionRange;
        double requiredForwardClearance = navigationRequiredClearance(
                desiredSpeed, telemetry.velocity().length());
        boolean forwardHazard = avoidanceBlocked || aircraftForwardHazard(
                command.avoidCollisions(), state.phase,
                forwardClearance, requiredForwardClearance);
        double altitudeError = aircraftAltitudeError(
                state.phase, telemetry.position().y, targetPosition.y,
                groundClearance, desiredClearance);
        double altitudeCorrection = Mth.clamp(
                altitudeError * 0.09D
                        - telemetry.velocity().y * 0.35D,
                -0.75D, 0.9D);
        double liftTrim = switch (state.phase) {
            case CLIMB -> 0.5D;
            case CRUISE -> 0.38D;
            case APPROACH -> 0.24D;
            case LANDING -> 0.16D;
        };
        double verticalControl = Mth.clamp(
                liftTrim + altitudeCorrection, -0.75D, 0.9D);
        if (forwardHazard) {
            altitudeCorrection = Math.max(altitudeCorrection, 0.75D);
            verticalControl = Math.max(verticalControl, 0.85D);
        }
        if (state.phase == AircraftFlightPhase.LANDING && groundClearance < 3.0D) {
            double flare = Mth.clamp(
                    -telemetry.velocity().y * 0.8D
                            + (3.0D - groundClearance) * 0.08D,
                    0.0D, 0.65D);
            altitudeCorrection = Math.max(altitudeCorrection, flare);
            verticalControl = Math.max(verticalControl, flare);
        }

        double forwardControl = Mth.clamp(
                (desiredSpeed - forwardSpeed) * 0.7D + baselineThrottle,
                -1.0D, 1.0D);
        if (forwardHazard) {
            forwardControl = Math.min(forwardControl,
                    horizontalAvoidance.lengthSqr() > 1.0E-12D ? 0.35D : 0.0D);
        }
        Vec3 force = forwardRoot.scale(forwardControl)
                .add(worldDirectionToRoot(worldUp).scale(verticalControl));

        // ------------------------------------ATTITUDE CONTROL------------------------------------
        Vec3 localAngularVelocity = worldDirectionToRoot(telemetry.angularVelocity());
        Vec3 lateralWorld = normalize(
                rootDirectionToWorld(rightRoot), new Vec3(-1.0D, 0.0D, 0.0D));
        Vec3 shipUpWorld = normalize(
                rootDirectionToWorld(upRoot), new Vec3(0.0D, 1.0D, 0.0D));
        boolean hasAvoidanceHeading = horizontalAvoidance.lengthSqr() > 1.0E-12D
                && horizontalTargetDelta.lengthSqr() > 1.0E-12D
                && horizontalAvoidance.normalize().dot(horizontalTargetDelta.normalize())
                        < 0.995D;
        boolean mayTurn = horizontalTarget.lengthSqr() > 1.0E-9D
                && aircraftMayTurn(
                        state.phase, forwardHazard && !hasAvoidanceHeading,
                        forwardSpeed, desiredSpeed);
        AircraftAttitudeControl attitude = aircraftAttitudeControl(
                altitudeCorrection, headingError,
                aircraftPitchDegrees(forwardWorld),
                aircraftBankDegrees(lateralWorld, shipUpWorld),
                localAngularVelocity.dot(rightRoot),
                localAngularVelocity.dot(forwardRoot),
                telemetry.angularVelocity().dot(worldUp),
                mayTurn, state.phase == AircraftFlightPhase.LANDING);
        Vec3 torque = rightRoot.scale(attitude.pitch())
                .add(forwardRoot.scale(attitude.roll()))
                .add(worldDirectionToRoot(worldUp).scale(attitude.yaw()));
        return new AircraftGuidance(force, torque, 0.0D);
    }

    // Check if a hazard blocks the aircraft
    static boolean aircraftForwardHazard(
            boolean avoidCollisions,
            @Nullable AircraftFlightPhase phase,
            double forwardClearance,
            double requiredClearance
    ) {
        return avoidCollisions
                && phase != AircraftFlightPhase.LANDING
                && finite(forwardClearance) < finite(requiredClearance);
    }

    // Get the navigation direction
    private Vec3 navigationDirection(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command
    ) {
        return navigationGuidance(
                commandKey, telemetry, command, command.targetPosition()).direction();
    }

    // Get the navigation guidance
    private NavigationGuidance navigationGuidance(
            String commandKey,
            Telemetry telemetry,
            ActiveShipCommand command,
            Vec3 targetPosition
    ) {
        boolean groundVehicle = isControlMode(ScmBuiltinControlModes.CAR_ID);
        boolean planeVehicle = isControlMode(ScmBuiltinControlModes.PLANE_ID);
        Vec3 position = telemetry.position();
        Vec3 target = finite(targetPosition);
        Vec3 error = target.subtract(position);
        if (groundVehicle) {
            error = new Vec3(error.x, 0.0D, error.z);
            target = new Vec3(target.x, position.y, target.z);
        }
        if (error.lengthSqr() <= 1.0E-12D) {
            navigationPathStates.remove(commandKey);
            return traceNavigationGuidance(commandKey, telemetry, target, groundVehicle,
                    null, true, NavigationGuidance.stopped(position));
        }

        Vec3 direct = error.normalize();
        if (!command.avoidCollisions()) {
            navigationPathStates.remove(commandKey);
            return traceNavigationGuidance(commandKey, telemetry, target, groundVehicle,
                    null, true, new NavigationGuidance(
                            direct, target, 0.25D, false, true, false, direct));
        }

        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null) {
            navigationPathStates.remove(commandKey);
            return traceNavigationGuidance(commandKey, telemetry, target, groundVehicle,
                    null, true, new NavigationGuidance(
                            direct, target, 0.25D, false, true, false, direct));
        }

        Vec3 forward = normalize(rootDirectionToWorld(controllerForwardRoot()), direct);
        Vec3 up = normalize(rootDirectionToWorld(controllerUpRoot()),
                new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        if (groundVehicle) {
            forward = normalize(new Vec3(forward.x, 0.0D, forward.z), direct);
            up = new Vec3(0.0D, 1.0D, 0.0D);
            right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        }

        HullBounds hull = shipHullBounds(position, ctx.shipSubLevels());
        double scanRange = navigationCollisionScanRange(telemetry.velocity().length());
        double lookahead = Math.min(error.length(), scanRange);
        double localStep = navigationPathStep(hull.maximumSpan());
        NavigationPathState state = navigationPathStates.get(commandKey);
        long gameTime = ctx.level().getGameTime();
        if (groundVehicle && state != null && state.reverseAtWaypoint()) {
            if (state.plannedTarget == null
                    || state.plannedTarget.distanceToSqr(target) > 4.0D) {
                navigationPathStates.remove(commandKey);
                state = null;
            } else {
                NavigationGuidance recovery = reverseRecoveryGuidance(
                        commandKey, telemetry, target, direct, forward,
                        hull, ctx, state, gameTime);
                if (recovery != null) return recovery;
                if (state.reverseForwardReleased) {
                    navigationPathStates.remove(commandKey);
                    state = null;
                } else {
                    // A completed or stalled recovery must not immediately
                    // pulse forward into the obstacle which selected reverse.
                    // Retire the reverse route, stop for this tick, then let
                    // the bounded forward detour search run on the next tick.
                    state.clearReverseRecovery();
                    state.nextDetourTick = Long.MIN_VALUE;
                    return traceNavigationGuidance(
                            commandKey, telemetry, target, true,
                            state, false, NavigationGuidance.stopped(position));
                }
            }
        }

        double directClearance = reactiveCollisionDistance(
                position, direct, lookahead, groundVehicle);
        double forwardClearance = groundVehicle
                ? groundCollisionDistance(position, forward, scanRange)
                : scanRange;
        WaypointProgressTracker.Observation progress = null;
        if (groundVehicle) {
            state = navigationPathStates.computeIfAbsent(
                    commandKey, ignored -> new NavigationPathState());
            progress = state.waypointProgress.observe(
                    position, target, Math.max(0.5D, command.tolerance()),
                    gameTime, GROUND_NAVIGATION_STUCK_TICKS, true);
        }

        double reactiveClearance = groundVehicle
                ? Math.min(lookahead, GROUND_NAVIGATION_MIN_CLEARANCE
                + Math.max(0.5D, telemetry.velocity().length() * 0.75D))
                : lookahead;
        if (!groundVehicle && state != null && state.hasLocalDetour()) {
            boolean targetMoved = state.plannedTarget == null
                    || state.plannedTarget.distanceToSqr(target)
                    > localStep * localStep;
            boolean directCorridorClear = directClearance >= lookahead - 1.0E-4D
                    && (!groundVehicle
                    || forwardClearance >= reactiveClearance - 1.0E-4D);
            if (targetMoved || directCorridorClear) {
                state.clearForwardDetour();
            } else {
                double waypointRadius = groundVehicle
                        ? groundNavigationWaypointRadius(hull, localStep)
                        : Math.max(0.75D, localStep * 0.5D);
                while (state.waypointIndex < state.waypoints.size()
                        && navigationWaypointReached(
                        position, state.waypoints.get(state.waypointIndex),
                        waypointRadius, hull, groundVehicle)) {
                    state.waypointIndex++;
                }
                if (state.waypointIndex < state.waypoints.size()) {
                    Vec3 waypoint = state.waypoints.get(state.waypointIndex);
                    if (localDetourSegmentClear(
                            position, waypoint, hull, ctx, groundVehicle)) {
                        Vec3 direction = normalize(
                                waypoint.subtract(position), direct);
                        return traceNavigationGuidance(
                                commandKey, telemetry, target, groundVehicle,
                                state, true, new NavigationGuidance(
                                direction, waypoint, 0.6D,
                                false, false, true, direction));
                    }
                    state.clearForwardDetour();
                    state.nextDetourTick = Long.MIN_VALUE;
                } else {
                    state.clearForwardDetour();
                }
            }
        }

        // Route selection uses the complete scanned corridor. The shorter
        // reactive clearance is only a braking envelope; using it here made a
        // car ignore a visible obstacle until it was too close to turn.
        boolean directTravelClear = directClearance >= lookahead - 1.0E-4D
                && (!groundVehicle
                || forwardClearance >= reactiveClearance - 1.0E-4D);
        if (directTravelClear) {
            if (!groundVehicle) navigationPathStates.remove(commandKey);
            return traceNavigationGuidance(commandKey, telemetry, target, groundVehicle,
                    null, true, new NavigationGuidance(
                            direct, target, 0.25D, false, true, false, direct));
        }

        if (state == null) {
            state = navigationPathStates.computeIfAbsent(
                    commandKey, ignored -> new NavigationPathState());
        }
        if (!groundVehicle && gameTime >= state.nextDetourTick) {
            Vec3 localGoal = position.add(direct.scale(lookahead));
            Vec3 routeForward = planeVehicle
                    ? forward : direct;
            Vec3 side = normalize(routeForward.cross(up), right);
            Vec3 vertical = normalize(side.cross(routeForward), up);
            if (vertical.dot(up) < 0.0D) {
                side = side.scale(-1.0D);
                vertical = vertical.scale(-1.0D);
            }
            int sideSteps = localDetourSideSteps(hull, localStep);
            int forwardSteps = Math.max(
                    LOCAL_DETOUR_MAX_FORWARD_STEPS, sideSteps + 2);
            LocalDetourPlanner.MovementModel lateralModel =
                    planeVehicle
                            ? LocalDetourPlanner.MovementModel.STEERING
                            : LocalDetourPlanner.MovementModel.PLANAR;
            List<Vec3> route = planLocalDetour(
                    position, localGoal, routeForward, side, vertical,
                    localStep, hull, ctx, false,
                    forwardSteps, sideSteps, 0, lateralModel);
            // Only after every nearby same-level route fails may a flying
            // vehicle spend altitude to pass above or below the obstacle.
            if (route.isEmpty()) {
                LocalDetourPlanner.MovementModel spatialModel = planeVehicle
                        ? LocalDetourPlanner.MovementModel.SPATIAL_STEERING
                        : LocalDetourPlanner.MovementModel.SPATIAL;
                route = planLocalDetour(
                        position, localGoal, routeForward, side, vertical,
                        localStep, hull, ctx, false,
                        forwardSteps, sideSteps,
                        LOCAL_DETOUR_MAX_VERTICAL_STEPS, spatialModel);
            }
            if (!route.isEmpty()) {
                state.setForwardDetour(route, target, gameTime);
                Vec3 waypoint = route.getFirst();
                Vec3 direction = normalize(
                        waypoint.subtract(position), direct);
                return traceNavigationGuidance(
                        commandKey, telemetry, target, groundVehicle,
                        state, true, new NavigationGuidance(
                        direction, waypoint, 0.6D,
                        false, false, true, direction));
            }
            state.nextDetourTick = gameTime + LOCAL_DETOUR_RETRY_TICKS;
        }

        Vec3 avoidance = simpleCollisionAvoidanceDirection(
                position, direct, forward,
                Math.min(directClearance, forwardClearance), up, right,
                scanRange, lookahead, groundVehicle);
        if (groundVehicle && avoidance.lengthSqr() > 1.0E-12D) {
            avoidance = ReactiveCollisionAvoidance.steeringDirection(
                    direct, avoidance, directClearance, lookahead,
                    Math.toRadians(35.0D));
        }
        if (groundVehicle) {
            double requiredClearance = navigationRequiredClearance(
                    command.targetSpeed(), telemetry.velocity().length());
            boolean noLateralEscape = avoidance.lengthSqr() <= 1.0E-12D;
            boolean unableToAdvance = forwardClearance < requiredClearance
                    && noLateralEscape
                    && progress != null
                    && progress.ticksWithoutProgress() >= 5L;
            boolean reverseNeeded = progress != null
                    && noLateralEscape
                    && (progress.stalled() || unableToAdvance);
            if (reverseNeeded
                    && gameTime >= state.nextReplanTick) {
                if (startReverseRecovery(
                        telemetry, target, direct, forward, up,
                        hull, ctx, state, scanRange, gameTime)) {
                    NavigationGuidance recovery = reverseRecoveryGuidance(
                            commandKey, telemetry, target, direct, forward,
                            hull, ctx, state, gameTime);
                    if (recovery != null) return recovery;
                }
                state.nextReplanTick = gameTime + NAVIGATION_FAILED_RETRY_TICKS;
            }
        }

        // A non-plane craft with real backward authority may make one short
        // retreat only after lateral, vertical and reactive escapes all fail.
        // Reaching that retained point restarts the ordered local search from
        // the newly-cleared position instead of committing to a long reverse.
        if (!groundVehicle && !planeVehicle
                && avoidance.lengthSqr() <= 1.0E-12D
                && state.consecutiveReverseEscapes == 0
                && (hasDirectionalAuthority(activeAssemblyMap,
                controllerForwardRoot().scale(-1.0D))
                || hasConfiguredAction("ship_backward", "ship_reverse"))) {
            Vec3 reverse = forward.scale(-1.0D);
            double reverseClearance = collisionDistance(
                    position, reverse, scanRange);
            double reverseDistance = Math.min(
                    localStep * 2.0D,
                    Math.max(0.0D, reverseClearance - NAVIGATION_MIN_CLEARANCE));
            if (reverseDistance >= localStep * 0.5D) {
                Vec3 reverseTarget = position.add(
                        reverse.scale(reverseDistance));
                state.setReverseEscapeDetour(
                        List.of(reverseTarget), target, gameTime);
                return traceNavigationGuidance(
                        commandKey, telemetry, target, false,
                        state, true, new NavigationGuidance(
                        reverse, reverseTarget, 0.6D,
                        false, false, true, reverse));
            }
        }

        if (avoidance.lengthSqr() <= 1.0E-12D) {
            return traceNavigationGuidance(commandKey, telemetry, target, groundVehicle,
                    null, false, NavigationGuidance.stopped(position));
        }
        if (groundVehicle) {
            // Keep the real destination as the target and alter only the live
            // steering direction. This is the stable reactive behaviour: no
            // sharp lateral waypoint exists for the car to miss or orbit.
            return traceNavigationGuidance(
                    commandKey, telemetry, target, true,
                    state, true, new NavigationGuidance(
                    avoidance, target, 0.25D,
                    false, true, true, avoidance));
        }
        double fallbackDistance = Math.min(lookahead,
                Math.max(localStep * 2.0D, reactiveClearance));
        Vec3 controlTarget = position.add(avoidance.scale(fallbackDistance));
        if (groundVehicle) {
            controlTarget = new Vec3(controlTarget.x, position.y, controlTarget.z);
        }
        state.setForwardDetour(List.of(controlTarget), target, gameTime);
        return traceNavigationGuidance(commandKey, telemetry, target, groundVehicle,
                state, true, new NavigationGuidance(
                        avoidance, controlTarget, 0.6D,
                        false, false, true, avoidance));
    }

    // Search one small local movement envelope. The host retains ownership of
    // the live world and full-hull collision query used by the library planner.
    private List<Vec3> planLocalDetour(
            Vec3 position,
            Vec3 localGoal,
            Vec3 direct,
            Vec3 side,
            Vec3 vertical,
            double step,
            HullBounds hull,
            CollisionScanContext ctx,
            boolean groundVehicle,
            int forwardSteps,
            int sideSteps,
            int verticalSteps,
            LocalDetourPlanner.MovementModel movementModel
    ) {
        return LocalDetourPlanner.plan(new LocalDetourPlanner.Request(
                position, localGoal, direct, side, vertical,
                step, forwardSteps, sideSteps, verticalSteps,
                LOCAL_DETOUR_MAX_EXPANSIONS, movementModel,
                (start, end) -> localDetourSegmentClear(
                        start, end, hull, ctx, groundVehicle)));
    }

    // Give a wide hull enough local lateral cells to actually clear its own
    // footprint while keeping the search bounded to a small nearby window.
    private static int localDetourSideSteps(HullBounds hull, double step) {
        double cell = Math.max(NAVIGATION_PATH_MIN_STEP, finite(step));
        int required = (int) Math.ceil(
                (hull.horizontalRadius() + cell * 2.0D) / cell);
        return Mth.clamp(required,
                LOCAL_DETOUR_MAX_SIDE_STEPS, 10);
    }

    // Get one immediate collision-free steering direction without building a route.
    private Vec3 simpleCollisionAvoidanceDirection(
            Vec3 position,
            Vec3 direct,
            Vec3 forward,
            double directClearance,
            Vec3 up,
            Vec3 right,
            double scanRange,
            double requiredClearance,
            boolean groundVehicle
    ) {
        double minimum = Math.min(scanRange,
                Math.max(0.0D, finite(requiredClearance)));
        List<Vec3> directions = new ArrayList<>();
        if (groundVehicle) {
            directions.add(rotateHorizontal(direct, Math.toRadians(35.0D)));
            directions.add(rotateHorizontal(direct, Math.toRadians(-35.0D)));
            directions.add(rotateHorizontal(direct, Math.toRadians(70.0D)));
            directions.add(rotateHorizontal(direct, Math.toRadians(-70.0D)));
            directions.add(rotateHorizontal(forward, Math.toRadians(35.0D)));
            directions.add(rotateHorizontal(forward, Math.toRadians(-35.0D)));
            directions.add(rotateHorizontal(forward, Math.toRadians(70.0D)));
            directions.add(rotateHorizontal(forward, Math.toRadians(-70.0D)));
        } else {
            directions.add(normalize(direct.add(right.scale(0.8D)), direct));
            directions.add(normalize(direct.subtract(right.scale(0.8D)), direct));
            directions.add(normalize(direct.add(up.scale(0.8D)), direct));
            directions.add(normalize(direct.subtract(up.scale(0.8D)), direct));
        }
        List<ReactiveCollisionAvoidance.EscapeCandidate> candidates = directions.stream()
                .map(direction -> reactiveEscapeCandidate(
                        position, direction, scanRange, groundVehicle))
                .toList();
        ReactiveCollisionAvoidance.Response response = ReactiveCollisionAvoidance.resolve(
                new ReactiveCollisionAvoidance.Request(
                        direct, directClearance, minimum, candidates));
        if (!response.hazard()) return direct;
        return response.escapeAvailable() ? response.escapeDirection() : Vec3.ZERO;
    }

    // Start one bounded reverse-only recovery after direct forward travel stalls.
    private boolean startReverseRecovery(
            Telemetry telemetry,
            Vec3 target,
            Vec3 targetDirection,
            Vec3 forward,
            Vec3 up,
            HullBounds hull,
            CollisionScanContext ctx,
            NavigationPathState state,
            double scanRange,
            long gameTime
    ) {
        Vec3 reverse = forward.scale(-1.0D);
        List<Vec3> candidates = List.of(
                reverse,
                rotateHorizontal(reverse, Math.toRadians(30.0D)),
                rotateHorizontal(reverse, Math.toRadians(-30.0D)),
                rotateHorizontal(reverse, Math.toRadians(55.0D)),
                rotateHorizontal(reverse, Math.toRadians(-55.0D)));
        Vec3 recoveryDirection = Vec3.ZERO;
        double recoveryClearance = 0.0D;
        for (Vec3 candidate : candidates) {
            double clearance = groundCollisionDistance(
                    telemetry.position(), candidate, scanRange);
            double score = clearance + Math.max(0.0D,
                    candidate.dot(targetDirection)) * 0.5D;
            double bestScore = recoveryClearance + (recoveryDirection.lengthSqr() <= 1.0E-12D
                    ? 0.0D : Math.max(0.0D, recoveryDirection.dot(targetDirection)) * 0.5D);
            if (score > bestScore) {
                recoveryDirection = candidate;
                recoveryClearance = clearance;
            }
        }
        double step = navigationPathStep(hull.maximumSpan());
        double recoveryDistance = Math.min(8.0D,
                Math.max(0.0D,
                        recoveryClearance - GROUND_NAVIGATION_MIN_CLEARANCE));
        if (recoveryDirection.lengthSqr() <= 1.0E-12D
                || recoveryDistance < 0.35D) {
            return false;
        }
        double recoveryStep = Math.min(step, recoveryDistance);
        Vec3 recoveryTarget = telemetry.position().add(
                recoveryDirection.scale(recoveryDistance));
        GroundPathPlanner.VehicleCapabilities capabilities =
                groundVehicleCapabilities(hull);
        GroundPathPlanner.Plan plan = GroundPathPlanner.planReverseRecovery(
                new GroundPathPlanner.PoseRequest(
                        telemetry.position(), recoveryTarget, forward,
                        capabilities, recoveryDistance + step * 2.0D,
                        Math.max(0.35D, Math.min(recoveryStep,
                                capabilities.minimumTurningRadius() * 0.45D)),
                        48, (start, end) -> pathPoseClear(
                        start.position(), start.forward(), up,
                        end.position(), end.forward(), up,
                        forward, up, hull, ctx, true)));
        if (plan.waypoints().isEmpty()) return false;
        state.waypoints = plan.waypoints().stream()
                .map(GroundPathPlanner.Waypoint::position).toList();
        state.reverseWaypoints = plan.waypoints().stream()
                .map(ignored -> true).toList();
        state.waypointIndex = 0;
        state.plannedTarget = target;
        state.setGroundRoute(plan.curves());
        state.waypointProgress.reset(
                telemetry.position(), state.waypoints.getFirst(), gameTime, true);
        state.reverseStartPosition = telemetry.position();
        state.minimumReverseDistance = Math.min(1.5D,
                Math.max(0.25D, recoveryDistance * 0.5D));
        state.reverseForwardReleased = false;
        state.nextReplanTick = Long.MAX_VALUE;
        state.exploredSegments = List.of();
        return true;
    }

    // Follow the retained reverse recovery until forward navigation can resume.
    private @Nullable NavigationGuidance reverseRecoveryGuidance(
            String commandKey,
            Telemetry telemetry,
            Vec3 target,
            Vec3 direct,
            Vec3 forward,
            HullBounds hull,
            CollisionScanContext ctx,
            NavigationPathState state,
            long gameTime
    ) {
        double scanRange = navigationCollisionScanRange(telemetry.velocity().length());
        double directLookahead = Math.min(scanRange,
                telemetry.position().distanceTo(target));
        double targetClearance = groundCollisionDistance(
                telemetry.position(), direct, directLookahead);
        double physicalForwardClearance = groundCollisionDistance(
                telemetry.position(), forward, directLookahead);
        double forwardClearance = Math.min(
                targetClearance, physicalForwardClearance);
        double reversedDistance = horizontalDistance(
                state.reverseStartPosition, telemetry.position());
        if (GroundPathPlanner.shouldRefreshReverseRoute(
                forward, direct, forwardClearance,
                directLookahead, reversedDistance,
                state.minimumReverseDistance)) {
            state.reverseForwardReleased = true;
            return null;
        }
        double waypointRadius = Math.max(0.75D,
                groundNavigationWaypointRadius(hull,
                        navigationPathStep(hull.maximumSpan())));
        while (state.waypointIndex < state.waypoints.size()) {
            Vec3 waypoint = state.waypoints.get(state.waypointIndex);
            double curveProgress = state.activeGroundCurveProgress(
                    telemetry.position());
            WaypointProgressTracker.Observation recoveryProgress =
                    state.waypointProgress.observe(
                            telemetry.position(), waypoint, waypointRadius,
                            gameTime, GROUND_NAVIGATION_STUCK_TICKS,
                            true, curveProgress);
            boolean reached = recoveryProgress.captured()
                    && curveProgress >= 0.65D || curveProgress >= 0.985D;
            if (recoveryProgress.stalled()) {
                state.reverseForwardReleased = false;
                return null;
            }
            if (!reached) break;
            state.waypointIndex++;
            state.completeGroundWaypoint(waypoint);
        }
        if (state.waypointIndex >= state.waypoints.size()) {
            state.reverseForwardReleased = false;
            return null;
        }

        Vec3 waypoint = state.waypoints.get(state.waypointIndex);
        GroundCurveFollow follow = state.groundCurveFollow(
                telemetry.position(), groundNavigationCurveLookahead(
                        hull, navigationPathStep(hull.maximumSpan()),
                        telemetry.velocity()));
        Vec3 controlTarget = follow == null ? waypoint : follow.target();
        Vec3 direction = follow == null
                ? normalize(waypoint.subtract(telemetry.position()), forward.scale(-1.0D))
                : follow.tangent();
        boolean finalRecoveryPoint = state.waypointIndex
                == state.waypoints.size() - 1;
        return traceNavigationGuidance(commandKey, telemetry, target, true,
                state, true, new NavigationGuidance(
                        direction, controlTarget, 0.6D, true,
                        finalRecoveryPoint, true, direction));
    }

    // Rotate one horizontal direction.
    private static Vec3 rotateHorizontal(Vec3 direction, double radians) {
        Vec3 horizontal = normalize(
                new Vec3(direction.x, 0.0D, direction.z),
                new Vec3(0.0D, 0.0D, 1.0D));
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new Vec3(
                horizontal.x * cos - horizontal.z * sin,
                0.0D,
                horizontal.x * sin + horizontal.z * cos);
    }

    // Forward the selected route to opted-in debugging clients without feeding any data back into navigation.
    private NavigationGuidance traceNavigationGuidance(
            String commandKey,
            Telemetry telemetry,
            Vec3 targetPosition,
            boolean groundVehicle,
            @Nullable NavigationPathState state,
            boolean currentSegmentClear,
            NavigationGuidance guidance
    ) {
        if (controller.getLevel() instanceof ServerLevel level) {
            List<Vec3> waypoints = state == null || state.waypoints.isEmpty()
                    ? (guidance.direction().lengthSqr() <= 1.0E-12D
                    ? List.of() : List.of(guidance.controlTarget()))
                    : state.waypoints;
            List<Boolean> reverseWaypoints = state == null || state.reverseWaypoints.isEmpty()
                    ? (waypoints.isEmpty() ? List.of() : List.of(guidance.reverseRecovery()))
                    : state.reverseWaypoints;
            int activeWaypoint = state == null ? 0 : state.waypointIndex;
            String debugStatus = (status == null ? "" : status)
                    + " | " + (groundVehicle ? "ground" : "flight")
                    + " | " + (waypoints.isEmpty() ? "no route" : "waypoint "
                    + Math.min(activeWaypoint + 1, waypoints.size()) + "/" + waypoints.size());
            ScmPathDebugService.publish(level, controller.getBlockPos(), commandKey, debugStatus,
                    groundVehicle, telemetry.position(), targetPosition, activeWaypoint,
                    currentSegmentClear, waypoints, reverseWaypoints,
                    state == null ? List.of() : state.exploredSegments);
        }
        return guidance;
    }

    // Get the navigation path lookahead
    static double navigationPathLookahead(double requestedSpeed, double currentSpeed) {
        double speed = Math.max(
                Math.abs(finite(requestedSpeed)), Math.abs(finite(currentSpeed)));
        return navigationCollisionScanRange(speed);
    }

    // Get the navigation path step
    static double navigationPathStep(double hullSpan) {
        return Mth.clamp(
                Math.max(0.0D, finite(hullSpan)) * 0.15D,
                NAVIGATION_PATH_MIN_STEP, NAVIGATION_PATH_MAX_STEP);
    }

    // Derive the steering envelope from live wheel locations when they are
    // available. The collision sweep remains the authority on body clearance;
    // these values tell the planner what curved motion the drivetrain can make.
    private GroundPathPlanner.VehicleCapabilities groundVehicleCapabilities(HullBounds hull) {
        double wheelbase = Math.max(1.25D, hull.horizontalRadius() * 2.0D);
        Vec3 controllerForward = normalize(new Vec3(
                controllerForwardRoot().x, 0.0D, controllerForwardRoot().z),
                new Vec3(0.0D, 0.0D, 1.0D));
        double leadingWheel = Double.NEGATIVE_INFINITY;
        double trailingWheel = Double.POSITIVE_INFINITY;
        for (WheelMountControlBridge wheel : controlledScmWheels) {
            WheelMountControlBridge.PhysicalSample sample = wheel.ct$getPhysicalSample();
            if (sample != null && sample.localPosition() != null) {
                Vec3 position = sample.localPosition();
                if (Double.isFinite(position.x) && Double.isFinite(position.z)) {
                    double longitudinalPosition = position.dot(controllerForward);
                    leadingWheel = Math.max(leadingWheel, longitudinalPosition);
                    trailingWheel = Math.min(trailingWheel, longitudinalPosition);
                }
            }
        }
        if (Double.isFinite(leadingWheel) && Double.isFinite(trailingWheel)) {
            // Track separation is not wheelbase. Using the diagonal between
            // two wheels inflated the minimum radius for wide vehicles and
            // generated curves they could not actually follow.
            wheelbase = Math.max(1.25D, leadingWheel - trailingWheel);
        }
        // Wheel mounts expose their positions but not a universal steering-lock
        // or suspension API. Use the common 32 degree travel as a conservative
        // fallback; hosts with a richer wheel bridge can pass its exact values
        // directly to the reusable planner.
        return new GroundPathPlanner.VehicleCapabilities(
                wheelbase, Math.toRadians(32.0D), 0.0D, 0.0D, 0.0D,
                hasDirectionalAuthority(activeAssemblyMap, controllerForwardRoot().scale(-1.0D))
                        || hasConfiguredAction("ship_backward", "ship_reverse"));
    }

    // Check whether the calibrated map can produce force along one local axis
    private static boolean hasDirectionalAuthority(
            @Nullable ShipControlMap currentMap,
            Vec3 direction
    ) {
        if (currentMap == null) return false;
        Vec3 axis = normalize(direction, Vec3.ZERO);
        if (axis.lengthSqr() <= 1.0E-12D) return false;
        double available = currentMap.units().stream()
                .filter(ShipControlMap.PropulsionUnit::controllable)
                .filter(unit -> unit.maxThrust() > 1.0E-9D)
                .mapToDouble(unit -> Math.max(0.0D,
                        unit.forceDirection().dot(axis)) * unit.maxThrust())
                .sum();
        return available > 1.0E-6D;
    }


    // Check whether any requested SCM action has a calibration group
    private boolean hasConfiguredAction(String... actions) {
        ScmConfigurationProfile configuration = controller.getScmConfigurationProfile();
        if (configuration == null || actions == null) return false;
        for (String action : actions) {
            if (action != null && configuration.hasActionGroupsFor(Set.of(action))) return true;
        }
        return false;
    }

    // Check if the navigation waypoint was reached
    static boolean navigationWaypointReached(
            Vec3 alignmentPoint,
            Vec3 waypoint,
            double radius
    ) {
        return finite(alignmentPoint).distanceTo(finite(waypoint))
                <= Math.max(0.0D, finite(radius));
    }

    // Accept an intermediate checkpoint when the assembled collision hull,
    // rather than only the controller/centre point, reaches it. Ground routes
    // deliberately ignore height so suspension and one-block terrain changes
    // cannot strand a vehicle at an otherwise completed maneuver endpoint.
    private static boolean navigationWaypointReached(
            Vec3 alignmentPoint,
            Vec3 waypoint,
            double radius,
            HullBounds hull,
            boolean groundVehicle
    ) {
        if (hull == null) {
            return navigationWaypointReached(alignmentPoint, waypoint, radius);
        }
        double distance = groundVehicle
                ? hull.horizontalDistanceToTarget(alignmentPoint, waypoint)
                : hull.distanceToTarget(alignmentPoint, waypoint);
        return navigationTargetReached(distance, radius);
    }

    // Ground checkpoints are logical manoeuvre endpoints. Let a wide hull
    // advance when it has already overlapped one instead of trying to drive
    // its origin back through the checkpoint.
    private static double groundNavigationWaypointRadius(HullBounds hull, double step) {
        return Math.max(1.0D, Math.min(
                Math.max(0.0D, finite(step)) * GROUND_NAVIGATION_WAYPOINT_RADIUS_STEPS,
                hull.horizontalRadius() + Math.max(1.0D, finite(step)) * 0.2D));
    }

    // Look ahead on the active physical curve without turning its collision
    // samples into route checkpoints. Faster or larger vehicles lead farther,
    // while the bound keeps the steering reference on the current maneuver.
    private static double groundNavigationCurveLookahead(
            HullBounds hull,
            double step,
            Vec3 velocity
    ) {
        double minimum = 0.75D;
        double maximum = Math.max(minimum, Math.max(0.0D, finite(step)) * 0.65D);
        double speed = new Vec3(finite(velocity).x, 0.0D, finite(velocity).z).length();
        return Mth.clamp(Math.max(hull.horizontalRadius() * 0.35D,
                speed * 0.18D + 1.0D), minimum, maximum);
    }

    // Check one translated full-hull segment for a local detour
    private boolean localDetourSegmentClear(
            Vec3 start,
            Vec3 end,
            HullBounds hull,
            CollisionScanContext ctx,
            boolean groundVehicle
    ) {
        Vec3 delta = finite(end).subtract(finite(start));
        double distance = delta.length();
        if (distance <= 1.0E-6D) return true;
        double clearance = pathTraceDistance(
                start, delta.scale(1.0D / distance), distance,
                hull, ctx, groundVehicle);
        return clearance >= distance - 1.0E-4D;
    }

    // Check the complete rotated hull sweep between two planned poses
    private boolean pathPoseClear(
            Vec3 start,
            Vec3 startForward,
            Vec3 startUp,
            Vec3 end,
            Vec3 endForward,
            Vec3 endUp,
            Vec3 referenceForward,
            Vec3 referenceUp,
            HullBounds hull,
            CollisionScanContext ctx,
            boolean groundVehicle
    ) {
        Vec3 delta = finite(end).subtract(finite(start));
        double distance = delta.length();
        if (distance <= 1.0E-6D) return true;
        List<AABB> startBounds = hull.worldBoundsAtPose(
                start, startForward, startUp, referenceForward, referenceUp,
                COLLISION_HULL_MARGIN, groundVehicle);
        List<AABB> endBounds = hull.worldBoundsAtPose(
                end, endForward, endUp, referenceForward, referenceUp,
                COLLISION_HULL_MARGIN, groundVehicle);
        List<AABB> rotationalSweep = new ArrayList<>(startBounds.size());
        for (int idx = 0; idx < startBounds.size(); idx++) {
            AABB from = startBounds.get(idx);
            AABB toAtStart = endBounds.get(Math.min(idx, endBounds.size() - 1))
                    .move(delta.scale(-1.0D));
            rotationalSweep.add(from.minmax(toAtStart));
        }
        double clearDistance = SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                ctx.level(), ctx.containingSubLevel(), start,
                delta.scale(1.0D / distance), distance,
                rotationalSweep, true, ctx.excludedSubLevelIds(), true);
        return clearDistance >= distance - 1.0E-4D;
    }

    // Get the path trace distance
    private double pathTraceDistance(
            Vec3 origin,
            Vec3 dir,
            double range,
            HullBounds hull,
            CollisionScanContext ctx,
            boolean groundVehicle
    ) {
        long gameTime = ctx.level().getGameTime();
        if (pathTraceCacheTick != gameTime) {
            pathTraceCacheTick = gameTime;
            pathTraceCache.clear();
        }
        CollisionProbe probe = new CollisionProbe(origin, dir, range, groundVehicle);
        Double cached = pathTraceCache.get(probe);
        if (cached != null) {
            return cached;
        }
        List<AABB> movingBounds = hull.worldBoundsAt(
                origin, COLLISION_HULL_MARGIN, groundVehicle);
        // Ground vehicles sweep their complete Sable collision hull against
        // actual sub-level block shapes. A sparse ray grid can slip under a
        // low obstacle when the vehicle is not a full block off the ground.
        double distance = groundVehicle
                ? SubLevelParticleOcclusion.findSweptBoundsBlockingDistance(
                        ctx.level(), ctx.containingSubLevel(), origin, dir, range,
                        movingBounds, true, ctx.excludedSubLevelIds(), true)
                : SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                        ctx.level(), ctx.containingSubLevel(), dir, range,
                        movingBounds, true, ctx.excludedSubLevelIds(), true,
                        COLLISION_NAVIGATION_PROBES_PER_BOUNDS,
                        collisionProbeCache(ctx.level()));
        pathTraceCache.put(probe, distance);
        return distance;
    }

    // Get the ship hull bounds
    private static HullBounds shipHullBounds(
            Vec3 shipCenterWorld,
            List<SubLevel> shipSubLevels
    ) {
        List<AABB> relativeBounds = new ArrayList<>();
        double minX = 0.0D;
        double minY = 0.0D;
        double minZ = 0.0D;
        double maxX = 0.0D;
        double maxY = 0.0D;
        double maxZ = 0.0D;
        for (SubLevel subLevel : shipSubLevels) {
            var bounds = subLevel.boundingBox();
            AABB relative = new AABB(
                    bounds.minX() - shipCenterWorld.x,
                    bounds.minY() - shipCenterWorld.y,
                    bounds.minZ() - shipCenterWorld.z,
                    bounds.maxX() - shipCenterWorld.x,
                    bounds.maxY() - shipCenterWorld.y,
                    bounds.maxZ() - shipCenterWorld.z);
            relativeBounds.add(relative);
            minX = Math.min(minX, relative.minX);
            minY = Math.min(minY, relative.minY);
            minZ = Math.min(minZ, relative.minZ);
            maxX = Math.max(maxX, relative.maxX);
            maxY = Math.max(maxY, relative.maxY);
            maxZ = Math.max(maxZ, relative.maxZ);
        }
        if (relativeBounds.isEmpty()) {
            relativeBounds.add(new AABB(
                    0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D));
        }
        return new HullBounds(
                List.copyOf(relativeBounds),
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Get the ship hull distance to target
    private double shipHullDistanceToTarget(
            Telemetry targetTelemetry,
            Vec3 targetPosition
    ) {
        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null) {
            return finite(targetPosition)
                    .distanceTo(targetTelemetry.position());
        }
        return shipHullBounds(
                targetTelemetry.position(), ctx.shipSubLevels())
                .distanceToTarget(targetTelemetry.position(), targetPosition);
    }

    // Check if the ship hull reached the target
    private boolean shipHullTargetReached(
            Telemetry targetTelemetry,
            Vec3 targetPosition,
            double tolerance
    ) {
        return navigationTargetReached(
                shipHullDistanceToTarget(targetTelemetry, targetPosition),
                tolerance);
    }

    // Require the authored alignment point to reach the final command target
    private boolean commandTargetReached(
            Telemetry targetTelemetry,
            ActiveShipCommand command
    ) {
        double distance = isControlMode(ScmBuiltinControlModes.CAR_ID)
                ? horizontalDistance(targetTelemetry.position(), command.targetPosition())
                : targetTelemetry.position().distanceTo(command.targetPosition());
        return navigationTargetReached(distance, command.tolerance());
    }

    // Check if the navigation target was reached
    static boolean navigationTargetReached(double hullDistance, double tolerance) {
        return Double.isFinite(hullDistance)
                && Math.max(0.0D, hullDistance)
                <= Math.max(0.0D, finite(tolerance));
    }

    // Get the ship hull dist to target
    private double shipHullDistToTarget(
            Telemetry targetTelemetry,
            Vec3 targetPosition
    ) {
        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null) {
            return horizontalDistance(
                    targetTelemetry.position(), targetPosition);
        }
        return shipHullBounds(targetTelemetry.position(), ctx.shipSubLevels())
                .horizontalDistanceToTarget(
                        targetTelemetry.position(), targetPosition);
    }

    // Get the target distance to bounds
    static double targetDistanceToBounds(
            Vec3 targetPosition,
            List<AABB> worldBounds
    ) {
        if (targetPosition == null || worldBounds == null || worldBounds.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3 target = finite(targetPosition);
        double nearestSquared = Double.POSITIVE_INFINITY;
        for (AABB bounds : worldBounds) {
            if (bounds == null) {
                continue;
            }
            double dx = Math.max(
                    Math.max(bounds.minX - target.x, 0.0D),
                    target.x - bounds.maxX);
            double dy = Math.max(
                    Math.max(bounds.minY - target.y, 0.0D),
                    target.y - bounds.maxY);
            double dz = Math.max(
                    Math.max(bounds.minZ - target.z, 0.0D),
                    target.z - bounds.maxZ);
            nearestSquared = Math.min(
                    nearestSquared, dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(nearestSquared);
    }

    // Get the collision telemetry
    private CollisionTelemetry collisionTelemetry(
            Telemetry telemetry,
            double requestedRange,
            double requestedPollRate
    ) {
        if (!telemetry.available()) {
            collisionTelemetryStates.clear();
            return CollisionTelemetry.EMPTY;
        }
        double scanRange = AdvancedGraphCatalog.normalizeCollisionDetectionDistance(
                requestedRange);
        long intervalTicks = AdvancedGraphCatalog.collisionPollIntervalTicks(
                requestedPollRate);
        long gameTime = controller.getLevel() == null
                ? Long.MIN_VALUE : controller.getLevel().getGameTime();
        CollisionTelemetryKey key = new CollisionTelemetryKey(scanRange, intervalTicks);
        CollisionTelemetryState state = collisionTelemetryStates.get(key);
        if (state == null) {
            if (collisionTelemetryStates.size()
                    >= MAX_COLLISION_TELEMETRY_CONFIGURATIONS) {
                CollisionTelemetryKey eldest = collisionTelemetryStates.keySet()
                        .iterator().next();
                collisionTelemetryStates.remove(eldest);
            }
            state = new CollisionTelemetryState(scanRange);
            collisionTelemetryStates.put(key, state);
        }
        if (state.workTick != gameTime) {
            state.workTick = gameTime;
            if (state.scan == null
                    && intervalElapsed(gameTime, state.completedTick,
                            intervalTicks)) {
                state.scan = startCollisionScan(
                        telemetry, scanRange, state.cached);
            }
            tickCollisionScan(state, gameTime);
        }
        return state.cached;
    }

    // Start the collision scan
    private @Nullable CollisionTelemetryScan startCollisionScan(
            Telemetry telemetry,
            double scanRange,
            CollisionTelemetry cached
    ) {
        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null) {
            return null;
        }
        Vec3 forward = normalize(rootDirectionToWorld(controllerForwardRoot()),
                new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 up = normalize(rootDirectionToWorld(controllerUpRoot()),
                new Vec3(0.0D, 1.0D, 0.0D));
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        HullBounds hull = shipHullBounds(
                telemetry.position(), ctx.shipSubLevels());
        return new CollisionTelemetryScan(
                scanRange,
                telemetry.position(),
                List.of(
                        forward, forward.scale(-1.0D), right.scale(-1.0D),
                        right, up, up.scale(-1.0D)),
                hull.worldBoundsAt(telemetry.position(), COLLISION_HULL_MARGIN),
                ctx,
                // Cache shape lookups for the complete rolling scan, then discard them with the scan.
                new SubLevelParticleOcclusion.ProbeCache(),
                cached);
    }

    // Update the collision scan
    private void tickCollisionScan(
            CollisionTelemetryState state,
            long gameTime
    ) {
        CollisionTelemetryScan work = state.scan;
        if (work == null) {
            return;
        }
        int remaining = Math.min(
                COLLISION_TELEMETRY_DIRECTIONS_PER_TICK,
                work.directions.size() - work.directionIndex);
        for (int idx = 0; idx < remaining; idx++) {
            work.distances[work.directionIndex] =
                    SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                            work.context.level(),
                            work.context.containingSubLevel(),
                            work.directions.get(work.directionIndex), work.range,
                            work.movingBounds, true,
                            work.context.excludedSubLevelIds(), true,
                            COLLISION_TELEMETRY_PROBES_PER_BOUNDS,
                            work.probeCache);
            work.directionIndex++;
            state.cached = work.telemetry();
            if (work.directionIndex >= work.directions.size()) {
                state.completedTick = gameTime;
                state.scan = null;
                break;
            }
        }
    }

    // Get the collision distance
    private double collisionDistance(Vec3 pos, Vec3 dir) {
        return collisionDistance(pos, dir, COLLISION_SCAN_RANGE);
    }

    // Get the collision probe cache
    private SubLevelParticleOcclusion.ProbeCache collisionProbeCache(Level level) {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (collisionProbeCacheTick != gameTime) {
            collisionProbeCacheTick = gameTime;
            collisionProbeCache.clear();
        }
        return collisionProbeCache;
    }

    // Get the collision distance
    private double collisionDistance(
            Vec3 pos, Vec3 worldDir, double range
    ) {
        double scanRange = AdvancedGraphCatalog.normalizeCollisionDetectionDistance(
                range);
        CollisionScanContext ctx = collisionScanContext();
        if (ctx == null || worldDir.lengthSqr() <= 1.0E-12D) {
            return scanRange;
        }
        Vec3 dir = worldDir.normalize();
        long gameTime = ctx.level().getGameTime();
        if (collisionDistanceCacheTick != gameTime) {
            collisionDistanceCacheTick = gameTime;
            collisionDistanceCache.clear();
        }
        CollisionProbe probe = new CollisionProbe(pos, dir, scanRange, false);
        Double cached = collisionDistanceCache.get(probe);
        if (cached != null) {
            return cached;
        }
        HullBounds hull = shipHullBounds(
                pos, ctx.shipSubLevels());
        double distance = SubLevelParticleOcclusion.findProbedBoundsBlockingDistance(
                ctx.level(), ctx.containingSubLevel(), dir, scanRange,
                hull.worldBoundsAt(pos, COLLISION_HULL_MARGIN),
                true, ctx.excludedSubLevelIds(), true,
                COLLISION_NAVIGATION_PROBES_PER_BOUNDS,
                collisionProbeCache(ctx.level()));
        collisionDistanceCache.put(probe, distance);
        return distance;
    }

    // Get the collision scan context
    private @Nullable CollisionScanContext collisionScanContext() {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return null;
        }
        // Collision queries must run against the containing dimension. Passing
        // a sub-level view limits Sable's spatial query to that vehicle and
        // makes other moving craft invisible to generic avoidance.
        Level level = SableLevelApi.serverLevel(root.getLevel());
        if (level == null) {
            level = root.getLevel();
        }
        if (level == null) {
            return null;
        }
        SableAssemblyTopologyApi.Topology topology = assemblyTopology(root);
        updateCollisionTopology(topology);
        List<SubLevel> shipSubLevels = topology.available()
                ? new ArrayList<>(topology.loadedBodies())
                : connectedShipSubLevels(root);
        if (collisionCtx != null
                && collisionCtx.level() == level
                && collisionCtx.containingSubLevel() == root
                && collisionCtxTick == connectedSubLevelsTick) {
            return collisionCtx;
        }
        Set<UUID> excludedIds = new HashSet<>();
        for (SubLevel subLevel : shipSubLevels) {
            excludedIds.add(subLevel.getUniqueId());
        }
        collisionCtx =
                new CollisionScanContext(
                        level, root, shipSubLevels, Set.copyOf(excludedIds));
        collisionCtxTick = connectedSubLevelsTick;
        return collisionCtx;
    }

    // Update the collision topology
    private void updateCollisionTopology(
            @Nullable SableAssemblyTopologyApi.Topology topology
    ) {
        String fingerprint = topology != null && topology.available()
                ? topology.fingerprint() : "";
        if (Objects.equals(collisionTopologyFingerprint, fingerprint)) {
            return;
        }
        collisionTopologyFingerprint = fingerprint;
        collisionTelemetryStates.clear();
        collisionCtxTick = Long.MIN_VALUE;
        collisionCtx = null;
        collisionDistanceCacheTick = Long.MIN_VALUE;
        collisionDistanceCache.clear();
        pathTraceCacheTick = Long.MIN_VALUE;
        pathTraceCache.clear();
        collisionProbeCacheTick = Long.MIN_VALUE;
        collisionProbeCache.clear();
        navigationPathStates.clear();
        aircraftNavigationStates.clear();
    }

    // Get the connected ship sub levels
    private List<SubLevel> connectedShipSubLevels(ServerSubLevel root) {
        SableAssemblyTopologyApi.Topology topology = assemblyTopologyCache.get(root);
        UUID rootId = root.getUniqueId();
        if (Objects.equals(connectedSubLevelsRootId, rootId)
                && !cachedConnectedSubLevels.isEmpty()
                && cachedConnectedSubLevels.stream().noneMatch(SubLevel::isRemoved)
                && cachedAssemblyTopology == topology) {
            return cachedConnectedSubLevels;
        }
        Map<UUID, SubLevel> connected = new LinkedHashMap<>();
        connected.put(rootId, root);
        if (topology.available()) {
            for (ServerSubLevel subLevel : topology.loadedBodies()) {
                if (subLevel != null && !subLevel.isRemoved()) {
                    connected.putIfAbsent(subLevel.getUniqueId(), subLevel);
                }
            }
        }
        List<SubLevel> res = List.copyOf(connected.values());
        connectedSubLevelsTick = assemblyTopologyCache.generation();
        connectedSubLevelsRootId = rootId;
        cachedConnectedSubLevels = res;
        connectedSubLevelIdx = Map.copyOf(connected);
        cachedAssemblyTopology = topology;
        return res;
    }

    // Get the assembly topology
    private SableAssemblyTopologyApi.Topology assemblyTopology(ServerSubLevel root) {
        SableAssemblyTopologyApi.Topology topology = assemblyTopologyCache.get(root);
        if (cachedAssemblyTopology != topology
                || !Objects.equals(connectedSubLevelsRootId, root.getUniqueId())) {
            connectedShipSubLevels(root);
        }
        return topology;
    }

    // Discover the assembly topology
    private static SableAssemblyTopologyApi.Topology discoverAssemblyTopology(
            ServerSubLevel root
    ) {
        return SableAssemblyTopologyApi.discover(
                root,
                (owner, actor) -> isAssemblyTopologyActor(actor),
                (owner, actor, target) -> SableAssemblyConnection.Kind.STRUCTURAL);
    }

    // Get the connected ship sublevel index
    private Map<UUID, SubLevel> connectedShipSubLevelIndex(ServerSubLevel root) {
        List<SubLevel> connected = connectedShipSubLevels(root);
        if (connectedSubLevelIdx.size() == connected.size()
                && Objects.equals(connectedSubLevelsRootId, root.getUniqueId())) {
            return connectedSubLevelIdx;
        }
        Map<UUID, SubLevel> indexed = new LinkedHashMap<>();
        for (SubLevel subLevel : connected) {
            if (subLevel != null && !subLevel.isRemoved()) {
                indexed.put(subLevel.getUniqueId(), subLevel);
            }
        }
        return Map.copyOf(indexed);
    }

    // Initialize the ship sub levels
    private List<SubLevel> initShipSubLevels(ServerSubLevel root) {
        if (initializationSubLevels.isEmpty()) {
            return connectedShipSubLevels(root);
        }
        return List.copyOf(initializationSubLevels.values());
    }

    // Discover the connected sub levels deep
    static List<SubLevel> discoverConnectedSubLevelsDeep(SubLevel root) {
        if (!(root instanceof ServerSubLevel serverRoot)) {
            return root == null || root.isRemoved() ? List.of() : List.of(root);
        }
        SableAssemblyTopologyApi.Topology topology = discoverAssemblyTopology(serverRoot);
        return topology.available()
                ? List.copyOf(topology.loadedBodies())
                : List.of(serverRoot);
    }

    // Check if this is assembly topology actor
    private static boolean isAssemblyTopologyActor(BlockEntitySubLevelActor actor) {
        if (!(actor instanceof BlockEntity blockEntity)) {
            return true;
        }
        return !isDockingConnector(blockEntity)
                && !(blockEntity instanceof AnalogueContraptionControllerBlockEntity)
                && !(blockEntity instanceof PhysicsGantryBeltWheelBlockEntity)
                && !(blockEntity instanceof GyroscopeLinkBlockEntity)
                && !(blockEntity instanceof ClawBlockEntity)
                && !(blockEntity instanceof EntityLauncherAnchorBlockEntity);
    }

    // Get the ship hull extent
    private static double shipHullExtent(
            Vec3 shipCenterWorld,
            Vec3 directionWorld,
            List<SubLevel> shipSubLevels
    ) {
        double extent = 0.0D;
        for (SubLevel subLevel : shipSubLevels) {
            var bounds = subLevel.boundingBox();
            double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
            double centerY = (bounds.minY() + bounds.maxY()) * 0.5D;
            double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5D;
            double halfX = Math.max(0.0D, (bounds.maxX() - bounds.minX()) * 0.5D);
            double halfY = Math.max(0.0D, (bounds.maxY() - bounds.minY()) * 0.5D);
            double halfZ = Math.max(0.0D, (bounds.maxZ() - bounds.minZ()) * 0.5D);
            double centerProjection = (centerX - shipCenterWorld.x) * directionWorld.x
                    + (centerY - shipCenterWorld.y) * directionWorld.y
                    + (centerZ - shipCenterWorld.z) * directionWorld.z;
            double radiusProjection = Math.abs(directionWorld.x) * halfX
                    + Math.abs(directionWorld.y) * halfY
                    + Math.abs(directionWorld.z) * halfZ;
            extent = Math.max(extent, centerProjection + radiusProjection);
        }
        return Math.max(0.0D, extent);
    }

    // Get the navigation target distance
    private double navigationTargetDistance(Telemetry telemetry) {
        if (!telemetry.available()) {
            return 0.0D;
        }
        ServerSubLevel root = rootSubLevel != null
                ? rootSubLevel : containingServerSubLevel();
        SableAssemblyTopologyApi.Topology topology = root == null
                ? null : assemblyTopology(root);
        boolean aircraftNavigation = usesAircraftNavigation(
                effectiveAssemblyMap(), controllerForwardRoot(), controllerUpRoot(),
                primaryCarriageBodyIds(topology));
        return activeCommands.entrySet().stream()
                .filter(entry -> "ship_navigate".equals(entry.getValue().type())
                        || "ship_dock".equals(entry.getValue().type())
                        || "ship_follow".equals(entry.getValue().type()))
                .mapToDouble(entry -> {
                    ActiveShipCommand command = entry.getValue();
                    Telemetry targetTelemetry = targetPointTelemetry(
                            telemetry, command.targetPoint(), command.targetConnectorIndex());
                    return aircraftNavigation
                            && ("ship_navigate".equals(command.type())
                            || "ship_follow".equals(command.type()))
                            && !command.lockRotation()
                            ? aircraftNavigationDistance(
                                    java.util.Optional.ofNullable(
                                                    aircraftNavigationStates.get(entry.getKey()))
                                            .map(state -> state.phase)
                                            .orElse(null),
                                    targetTelemetry.position(),
                                    command.targetPosition())
                            : command.targetPosition()
                                    .subtract(targetTelemetry.position()).length();
                })
                .min().orElse(0.0D);
    }

    // Retain the navigation root
    private void retainNavigationRoot() {
        UUID owner = mapId;
        ServerSubLevel root = containingServerSubLevel();
        if (owner == null || root == null || root.isRemoved()) {
            return;
        }
        navigationResidency(owner).retain(root);
    }

    // Update the nav residency
    private void updateNavResidency(ServerSubLevel root) {
        UUID owner = mapId;
        if (owner == null || root == null || root.isRemoved()) {
            releaseNavResidency();
            return;
        }
        UUID rootId = root.getUniqueId();
        List<SubLevel> connected = connectedShipSubLevels(root);
        long topologyGeneration = connectedSubLevelsTick;
        if (Objects.equals(navigationResidencyOwner, owner)
                && Objects.equals(navigationResidencySyncRootId, rootId)
                && navigationResidencySyncTick == topologyGeneration) {
            return;
        }
        navigationResidency(owner).synchronize(connected);
        navigationResidencySyncTick = topologyGeneration;
        navigationResidencySyncRootId = rootId;
    }

    // Get the navigation residency
    private SableSubLevelResidency.Lease navigationResidency(UUID owner) {
        if (navigationResidency == null
                || !Objects.equals(navigationResidencyOwner, owner)) {
            releaseNavResidency();
            navigationResidency = SableSubLevelResidency.lease("scm/" + owner);
            navigationResidencyOwner = owner;
        }
        return navigationResidency;
    }

    // Release the nav residency
    private void releaseNavResidency() {
        if (navigationResidency != null) {
            navigationResidency.close();
        }
        navigationResidency = null;
        navigationResidencyOwner = null;
        navigationResidencySyncTick = Long.MIN_VALUE;
        navigationResidencySyncRootId = null;
    }

    // Detach the nav residency
    private void detachNavResidency() {
        if (navigationResidency != null) {
            navigationResidency.detach();
        }
        navigationResidency = null;
        navigationResidencyOwner = null;
        navigationResidencySyncTick = Long.MIN_VALUE;
        navigationResidencySyncRootId = null;
    }

    // Get the controller forward root
    private Vec3 controllerForwardRoot() {
        Direction facing = controller.getBlockState().hasProperty(AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                ? controller.getBlockState().getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                : Direction.NORTH;
        return ctrlDirToRoot(Vec3.atLowerCornerOf(facing.getNormal()));
    }

    // Get the controller up root
    private Vec3 controllerUpRoot() {
        return ctrlDirToRoot(new Vec3(0.0D, 1.0D, 0.0D));
    }

    // Get the ctrl dir to root
    private Vec3 ctrlDirToRoot(Vec3 dir) {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        Object containing = SimulatedHelper.getContainingSubLevel(controller);
        if (root == null || containing == null) {
            return dir;
        }
        return containing instanceof SubLevel src
                ? rootDirection(root, src, dir)
                : dir;
    }

    // Get the world direction to root
    private Vec3 worldDirectionToRoot(Vec3 dir) {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return dir;
        }
        Vector3d local = new Vector3d(dir.x, dir.y, dir.z);
        root.logicalPose().orientation().transformInverse(local);
        return new Vec3(local.x, local.y, local.z);
    }

    // Get the root direction to world
    private Vec3 rootDirectionToWorld(Vec3 dir) {
        ServerSubLevel root = rootSubLevel != null ? rootSubLevel : containingServerSubLevel();
        if (root == null) {
            return dir;
        }
        Vector3d world = new Vector3d(dir.x, dir.y, dir.z);
        root.logicalPose().orientation().transform(world);
        return new Vec3(world.x, world.y, world.z);
    }

    // Get the root position
    private static Vec3 rootPosition(ServerSubLevel root, SubLevel src, Vec3 pos) {
        if (root == src) {
            return pos;
        }
        return transformPositionBetween(
                root.logicalPose(), src.logicalPose(), pos);
    }

    // Get the root direction
    private static Vec3 rootDirection(ServerSubLevel root, SubLevel src, Vec3 dir) {
        if (root == src) {
            return normalize(dir, dir);
        }
        return normalize(transformDirectionBetween(
                root.logicalPose(), src.logicalPose(), dir), dir);
    }

    // Transform a position between poses
    static Vec3 transformPositionBetween(
            Pose3dc targetPose,
            Pose3dc sourcePose,
            Vec3 pos
    ) {
        Vector3d world = new Vector3d(
                pos.x, pos.y, pos.z);
        sourcePose.transformPosition(world);
        targetPose.transformPositionInverse(world);
        return new Vec3(world.x, world.y, world.z);
    }

    // Transform a direction between poses
    static Vec3 transformDirectionBetween(
            Pose3dc targetPose,
            Pose3dc sourcePose,
            Vec3 dir
    ) {
        Vector3d world = new Vector3d(
                dir.x, dir.y, dir.z);
        sourcePose.orientation().transform(world);
        targetPose.orientation().transformInverse(world);
        return new Vec3(world.x, world.y, world.z);
    }

    // Get the world position
    private static Vec3 worldPosition(Pose3dc pose, Vec3 pos) {
        Vector3d world = new Vector3d(pos.x, pos.y, pos.z);
        pose.transformPosition(world);
        return new Vec3(world.x, world.y, world.z);
    }

    // Get the world direction
    private static Vec3 worldDirection(Pose3dc pose, Vec3 dir) {
        Vector3d world = new Vector3d(
                dir.x, dir.y, dir.z);
        pose.orientation().transform(world);
        return normalize(new Vec3(world.x, world.y, world.z), dir);
    }

    // Get the connector point velocity
    private Vec3 connectorPointVelocity(
            UUID connectorSubLevelId,
            Vec3 worldPoint,
            Telemetry fallback
    ) {
        Object resolved = SubLevelBlockEntityCollector.getSubLevel(
                controller.getLevel(), connectorSubLevelId);
        if (resolved instanceof ServerSubLevel body) {
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (handle != null && handle.isValid()) {
                Vector3d linear = handle.getLinearVelocity(new Vector3d());
                Vector3d angular = handle.getAngularVelocity(new Vector3d());
                Vec3 bodyOrigin = new Vec3(
                        body.logicalPose().position().x(),
                        body.logicalPose().position().y(),
                        body.logicalPose().position().z());
                return velocityAtPoint(
                        new Vec3(linear.x, linear.y, linear.z),
                        new Vec3(angular.x, angular.y, angular.z),
                        bodyOrigin, worldPoint);
            }
        }
        return velocityAtPoint(
                fallback.velocity(), fallback.angularVelocity(),
                fallback.position(), worldPoint);
    }

    // Get the containing server sublevel
    private @Nullable ServerSubLevel containingServerSubLevel() {
        Object containing = SimulatedHelper.getContainingSubLevel(controller);
        return containing instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Get the require root sublevel
    private ServerSubLevel requireRootSubLevel() {
        if (rootSubLevel == null) {
            throw new IllegalStateException("Containing Sable sub-level is unavailable");
        }
        return rootSubLevel;
    }

    // Begin the command
    private void beginCommand(String commandId, String commandType) {
        registerCommand(ActiveShipCommand.idle(
                normalizeCommandId(commandId, commandType), commandType));
    }

    // Register the command
    private void registerCommand(ActiveShipCommand command) {
        String key = commandKey(command.id(), command.type());
        ActiveShipCommand prev = lastIssuedCommands.get(key);
        boolean unchanged = prev != null && prev.sameRequestAs(command);
        boolean continuingTargetUpdate = prev != null
                && activeCommands.containsKey(key)
                && prev.continuesTargetUpdateWith(command);
        if (!command.disabled()
                && unchanged
                && (activeCommands.containsKey(key) || completedCommands.contains(key))) {
            return;
        }
        lastIssuedCommands.put(key, command);
        completedCommands.remove(key);
        if (!continuingTargetUpdate) {
            navigationPathStates.remove(key);
            positionIntegralErrors.remove(key);
            airshipAttitudeHoldTargets.remove(key);
            aircraftNavigationStates.remove(key);
        }
        if (isTranslationTarget(command.type())) {
            supersedeTranslationTargets(key, command.type());
        }
        if (command.disabled()) {
            activeCommands.remove(key);
            completedCommands.add(key);
            return;
        }
        activeCommands.put(key, command);
    }

    // Supersede the translation targets
    private void supersedeTranslationTargets(String incomingKey, String incomingType) {
        List<String> superseded = activeCommands.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(incomingKey))
                .filter(entry -> !commandsCompose(
                        entry.getValue().type(), incomingType))
                .map(Map.Entry::getKey)
                .toList();
        for (String key : superseded) {
            activeCommands.remove(key);
            navigationPathStates.remove(key);
            positionIntegralErrors.remove(key);
            airshipAttitudeHoldTargets.remove(key);
            aircraftNavigationStates.remove(key);
        }
    }

    // Check if this is a translation target
    private static boolean isTranslationTarget(String commandType) {
        return "ship_dock".equals(commandType)
                || "ship_navigate".equals(commandType)
                || "ship_follow".equals(commandType);
    }

    // Check if the commands can be combined
    static boolean commandsCompose(String firstType, String secondType) {
        return !isTranslationTarget(firstType) || !isTranslationTarget(secondType);
    }

    // Complete the command
    private void completeCommand(ActiveShipCommand command) {
        completedCommands.add(commandKey(command.id(), command.type()));
    }

    // Check if this has active control commands
    private boolean hasActiveControlCommands() {
        return activeCommands.values().stream().anyMatch(command ->
                !"ship_initialize".equals(command.type())
                        && !"ship_scan_configuration".equals(command.type())
                        && !"ship_stop".equals(command.type()));
    }

    // Update the command completion
    private void updateCommandCompletion(Telemetry telemetry) {
        for (ActiveShipCommand command : List.copyOf(activeCommands.values())) {
            String key = commandKey(command.id(), command.type());
            if (completedCommands.contains(key)) {
                continue;
            }
            boolean reached = switch (command.type()) {
                case "ship_strafe", "ship_strafe_left", "ship_strafe_right" -> strafeTargetReached(telemetry, command);
                case "ship_climb" -> Math.abs(command.targetY() - telemetry.position().y) <= POSITION_TOLERANCE
                    && Math.abs(telemetry.velocity().y) <= LINEAR_VELOCITY_TOLERANCE;
                case "ship_face" -> faceTargetReached(telemetry, command);
                case "ship_dock" -> {
                    Telemetry targetTelemetry = targetPointTelemetry(
                            telemetry, command.targetPoint(), command.targetConnectorIndex());
                    yield commandTargetReached(targetTelemetry, command)
                            && targetTelemetry.velocity().length()
                            <= LINEAR_VELOCITY_TOLERANCE
                            && dockingDirectionReached(command);
                }
                case "ship_navigate" -> {
                    Telemetry targetTelemetry = targetPointTelemetry(
                            telemetry, command.targetPoint(), command.targetConnectorIndex());
                    yield shipHullTargetReached(
                            targetTelemetry,
                            command.targetPosition(),
                            command.tolerance())
                            && (command.targetDirection().lengthSqr() <= 1.0E-12D
                            || dockingDirectionReached(command));
                }
                case "ship_hover" -> Math.abs(command.targetY() - telemetry.position().y) <= POSITION_TOLERANCE
                    && Math.abs(telemetry.velocity().y) <= LINEAR_VELOCITY_TOLERANCE;
                case "ship_stabilize" ->
                        stabilizationTargetReached(telemetry, command);
                case "ship_decelerate", "ship_brake" ->
                    telemetry.velocity().length() <= LINEAR_VELOCITY_TOLERANCE;
                case "ship_yaw", "ship_yaw_right", "ship_yaw_left",
                     "ship_pan", "ship_pitch", "ship_pitch_up", "ship_pitch_down",
                     "ship_tilt", "ship_roll", "ship_roll_right", "ship_roll_left",
                     "ship_accelerate", "ship_forward", "ship_reverse",
                     "ship_backward",
                     "ship_ascend", "ship_descend" -> true;
                default -> false;
            };
            if (reached) {
                completeCommand(command);
                if ("ship_decelerate".equals(command.type())
                        || "ship_brake".equals(command.type())) {
                    activeCommands.remove(key);
                }
            }
        }
    }

    // Check if the strafe target was reached
    private boolean strafeTargetReached(Telemetry telemetry, ActiveShipCommand command) {
        Vec3 forward = controllerForwardRoot();
        Vec3 up = controllerUpRoot();
        Vec3 right = normalize(forward.cross(up), new Vec3(1.0D, 0.0D, 0.0D));
        double currentSpeed = worldDirectionToRoot(telemetry.velocity()).dot(right);
        double targetSpeed = switch (command.type()) {
            case "ship_strafe_left" -> -Math.abs(command.amount());
            case "ship_strafe_right" -> Math.abs(command.amount());
            default -> command.amount();
        };
        return Math.abs(targetSpeed - currentSpeed) <= 0.05D
                && telemetry.angularVelocity().length() <= ANGULAR_VELOCITY_TOLERANCE;
    }

    // Check if the face target was reached
    private boolean faceTargetReached(Telemetry telemetry, ActiveShipCommand command) {
        Vec3 desired = command.targetPosition().subtract(telemetry.position());
        desired = new Vec3(desired.x, 0.0D, desired.z);
        if (desired.lengthSqr() <= 1.0E-9D) {
            return telemetry.angularVelocity().length() <= ANGULAR_VELOCITY_TOLERANCE;
        }
        Vec3 forward = rootDirectionToWorld(controllerForwardRoot());
        forward = normalize(new Vec3(forward.x, 0.0D, forward.z), new Vec3(0.0D, 0.0D, 1.0D));
        double error = Math.toDegrees(Math.acos(Mth.clamp(forward.dot(desired.normalize()), -1.0D, 1.0D)));
        return error <= ANGLE_TOLERANCE_DEGREES
                && telemetry.angularVelocity().length() <= ANGULAR_VELOCITY_TOLERANCE;
    }

    // Check if the stabilization target was reached
    private boolean stabilizationTargetReached(
            Telemetry telemetry,
            ActiveShipCommand command
    ) {
        Vec3 error = attitudeRotationErrorRadians(
                command.targetAttitude(), telemetry.eulerDegrees());
        return Math.toDegrees(error.length()) <= ANGLE_TOLERANCE_DEGREES
                && telemetry.angularVelocity().length() <= ANGULAR_VELOCITY_TOLERANCE;
    }

    // Get the attitude error degrees
    static Vec3 attitudeErrorDegrees(Vec3 target, Vec3 current) {
        return new Vec3(
                shortestAngleDegrees(target.x - current.x),
                shortestAngleDegrees(target.y - current.y),
                shortestAngleDegrees(target.z - current.z));
    }

    // Normalize the angle degrees
    static double normalizeAngleDegrees(double deg) {
        return shortestAngleDegrees(deg);
    }

    // Get the shortest angle degrees
    private static double shortestAngleDegrees(double deg) {
        double wrapped = finite(deg) % 360.0D;
        if (wrapped > 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    // Normalize the command id
    private static String normalizeCommandId(String commandId, String commandType) {
        return commandId == null || commandId.isBlank()
                ? commandType == null ? "" : commandType : commandId;
    }

    // Get the command key
    private static String commandKey(String commandId, String commandType) {
        return normalizeCommandId(commandId, commandType)
                + '\u0000' + (commandType == null ? "" : commandType);
    }

    // Reset the control state
    private void resetControlState() {
        releaseControlAuthority();
        magneticConnectorIdx = -1;
        activeCommands.clear();
        lastIssuedCommands.clear();
        couplerControlRequests.clear();
        navigationPathStates.clear();
        positionIntegralErrors.clear();
        airshipAttitudeHoldTargets.clear();
        aircraftNavigationStates.clear();
        collisionTelemetryStates.clear();
        collisionCtxTick = Long.MIN_VALUE;
        collisionCtx = null;
        collisionTopologyFingerprint = "";
        collisionDistanceCacheTick = Long.MIN_VALUE;
        collisionDistanceCache.clear();
        pathTraceCacheTick = Long.MIN_VALUE;
        pathTraceCache.clear();
        collisionProbeCacheTick = Long.MIN_VALUE;
        collisionProbeCache.clear();
        connectedSubLevelsTick = Long.MIN_VALUE;
        connectedSubLevelsRootId = null;
        cachedConnectedSubLevels = List.of();
        connectedSubLevelIdx = Map.of();
        cachedAssemblyTopology = null;
        assemblyTopologyCache.invalidate();
        activeAssemblyMap = null;
        activeAssemblyPrimaryMap = null;
        activeAssemblyTopology = null;
        activeScmActuatorOwners = Map.of();
        mainCarriageMaps.clear();
        mainCarriageMapValidationTicks.clear();
        mainCarriageMapValidationRevisions.clear();
        dirtyMainCarriageMapIds.clear();
        activeAssemblyMapSignature = Long.MIN_VALUE;
        activeAssemblySubLevelIds = Set.of();
        activeCarriageCount = 0;
        absorbedScmMapCount = 0;
        lastMainCarriageMapSafetyRefreshTick = Long.MIN_VALUE;
        allocationWorkspace.reset();
        groundDriveCheckTick = Long.MIN_VALUE;
        invalidateTelemetryCache();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                 INITIALIZATION TRACKING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Capture the init assembly
    private void captureInitAssembly(
            ServerSubLevel root,
            SableAssemblyTopologyApi.Topology topology
    ) {
        releaseInitProtection();
        releaseInitTracking();
        initializationSubLevels.clear();
        initBodyStates.clear();
        Map<UUID, SubLevel> captured = new LinkedHashMap<>();
        captured.put(root.getUniqueId(), root);
        if (topology != null && topology.available()) {
            topology.loadedBodies().stream()
                    .filter(body -> body != null && !body.isRemoved())
                    .forEach(body -> captured.putIfAbsent(
                            body.getUniqueId(), body));
        }
        List<SubLevel> connected = captured.values().stream()
                .sorted(Comparator.comparing(subLevel ->
                        subLevel.getUniqueId().toString()))
                .toList();
        for (SubLevel subLevel : connected) {
            initializationSubLevels.put(subLevel.getUniqueId(), subLevel);
            if (!(subLevel instanceof ServerSubLevel body)) {
                continue;
            }
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (handle == null || !handle.isValid()) {
                continue;
            }
            initBodyStates.put(body.getUniqueId(),
                    new InitializationBodyState(
                            new Pose3d(body.logicalPose()),
                            handle.getLinearVelocity(new Vector3d()),
                            handle.getAngularVelocity(new Vector3d())));
        }
        initProtectedSubLevelIds.addAll(initializationSubLevels.keySet());
        createInitTracking();
        PhysicsStaffInteractionGuard.protectInitializationTargets(
                initProtectedSubLevelIds);
    }

    // Check if the command yields to the docking magnet
    private boolean yieldsToDockingMagnet(ActiveShipCommand command) {
        return magneticConnectorIdx >= 0
                && command.targetConnectorIndex() == magneticConnectorIdx;
    }

    // Create the init tracking
    private void createInitTracking() {
        ServerLevel level = containingServerLevel();
        if (level == null) {
            return;
        }
        SubLevelTrackingPointSavedData trackingData =
                SubLevelTrackingPointSavedData.getOrLoad(level);
        for (SubLevel subLevel : initializationSubLevels.values()) {
            if (!(subLevel instanceof ServerSubLevel serverSubLevel)
                    || initTrackingIds.containsKey(subLevel.getUniqueId())) {
                continue;
            }
            UUID trackingPointId = trackingData.generateTrackingPoint(
                    serverSubLevel.getPlot().getCenterBlock().getCenter(),
                    serverSubLevel);
            if (trackingPointId != null) {
                initTrackingIds.put(
                        subLevel.getUniqueId(), trackingPointId);
            }
        }
    }

    // Load the resumed initialization sub levels
    private boolean loadResumedInitializationSubLevels() {
        Level level = controller.getLevel();
        if (level == null || initTrackingIds.isEmpty()) {
            return level != null;
        }
        boolean loaded = true;
        for (UUID subLevelId : initTrackingIds.keySet()) {
            loaded &= SubLevelBlockEntityCollector.ensureSubLevelLoaded(
                    level, subLevelId) != null;
        }
        return loaded;
    }

    // Get the containing server level
    private @Nullable ServerLevel containingServerLevel() {
        return SableLevelApi.serverLevel(controller.getLevel());
    }

    // Release the init tracking
    private void releaseInitTracking() {
        if (initTrackingIds.isEmpty()) {
            return;
        }
        ServerLevel level = containingServerLevel();
        if (level != null) {
            SubLevelTrackingPointSavedData trackingData =
                    SubLevelTrackingPointSavedData.getOrLoad(level);
            initTrackingIds.values()
                    .forEach(trackingData::removeTrackingPoint);
        }
        initTrackingIds.clear();
    }

    // Restore the init assembly
    private void restoreInitAssembly() {
        for (Map.Entry<UUID, InitializationBodyState> entry
                : initBodyStates.entrySet()) {
            Object resolved = SubLevelBlockEntityCollector.getSubLevel(
                    controller.getLevel(), entry.getKey());
            if (!(resolved instanceof ServerSubLevel body) || body.isRemoved()) {
                continue;
            }
            RigidBodyHandle handle = RigidBodyHandle.of(body);
            if (handle == null || !handle.isValid()) {
                continue;
            }
            InitializationBodyState state = entry.getValue();
            try {
                handle.teleport(state.pose().position(), state.pose().orientation());
                Vector3d currentLinear = handle.getLinearVelocity(new Vector3d());
                Vector3d currentAngular = handle.getAngularVelocity(new Vector3d());
                handle.addLinearAndAngularVelocity(
                        new Vector3d(state.linearVelocity()).sub(currentLinear),
                        new Vector3d(state.angularVelocity()).sub(currentAngular));
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Could not restore a sub-level after ship initialization", err);
            }
        }
    }

    // Release the init protection
    private void releaseInitProtection() {
        if (initProtectedSubLevelIds.isEmpty()) {
            return;
        }
        PhysicsStaffInteractionGuard.releaseInitializationTargets(
                initProtectedSubLevelIds);
        initProtectedSubLevelIds.clear();
    }

    // Show the init progress
    private void showInitProgress(boolean complete, boolean failed) {
        if (!initializationFilters.displayProgress() || initFinalMsgSent) {
            return;
        }
        Level level = controller.getLevel();
        if (level == null || level.getServer() == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (!complete && !failed
                && !intervalElapsed(gameTime, lastProgressDisplayTick, 5L)) {
            return;
        }
        Component msg;
        if (complete) {
            msg = Component.literal("Initialized").withStyle(ChatFormatting.GREEN);
            initFinalMsgSent = true;
        } else if (failed) {
            msg = Component.literal("Initialization Failed")
                    .withStyle(ChatFormatting.RED);
            initFinalMsgSent = true;
        } else {
            msg = Component.literal("Initializing ship controls: "
                            + progressPercent(progress) + "% - " + status)
                    .withStyle(ChatFormatting.GOLD);
        }
        Vec3 controllerWorld = rootSubLevel == null
                ? controller.getBlockPos().getCenter()
                : worldPosition(rootSubLevel.logicalPose(),
                        controller.getBlockPos().getCenter());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(level.dimension())) {
                continue;
            }
            UUID tracked = SimulatedHelper.getSubLevelId(
                    SimulatedHelper.getEntityTrackingSubLevel(player));
            if (initProtectedSubLevelIds.contains(tracked)
                    || player.distanceToSqr(controllerWorld) <= 128.0D * 128.0D) {
                player.displayClientMessage(msg, true);
            }
        }
        lastProgressDisplayTick = gameTime;
    }

    // Check if the interval elapsed
    private static boolean intervalElapsed(long now, long prev, long interval) {
        return prev == Long.MIN_VALUE || now < prev || now - prev >= interval;
    }

    // Restore the cal actuators
    private void restoreCalActuators() {
        for (CalibrationUnit unit : calibrationUnits) {
            try {
                unit.actuator.restore();
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not restore propulsion actuator", err);
            }
        }
        for (BearingCalibrationUnit bearing : calibrationBearings) {
            try {
                bearing.actuator.restore();
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not restore calibrated bearing", err);
            }
        }
    }

    // Release the freeze handles
    private void releaseFreezeHandles() {
        releaseConstraintHandles(nestedFreezeHandles);
        releaseConstraintHandles(freezeHandles);
        freezeHandlesBySubLevel.clear();
    }

    // Release the nested freeze handles
    private void releaseNestedFreezeHandles() {
        releaseConstraintHandles(nestedFreezeHandles);
    }

    // Release the constraint handles
    private void releaseConstraintHandles(List<PhysicsConstraintHandle> handles) {
        for (PhysicsConstraintHandle handle : handles) {
            freezeHandlesBySubLevel.values().removeIf(val -> val == handle);
            try {
                if (handle != null && handle.isValid()) {
                    handle.remove();
                }
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not release ship initialization constraint", err);
            }
        }
        handles.clear();
    }

    // Release the control actuators
    private void releaseControlActuators() {
        for (Actuator actuator : controlActuators.values()) {
            try {
                actuator.restore();
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not restore controlled propulsion actuator", err);
            }
        }
        controlActuators.clear();
        appliedControlValues.clear();
        for (BearingActuator actuator : controlBearings.values()) {
            try {
                actuator.restore();
            } catch (RuntimeException err) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not restore controlled bearing", err);
            }
        }
        controlBearings.clear();
        selectedBearingPoses.clear();
        for (WheelMountControlBridge wheel : controlledScmWheels) {
            wheel.ct$setDirectInputs(0.0F, 0.0F, 0.0F);
        }
        controlledScmWheels.clear();
        controlledScmWheelTargets.clear();
        clearScmFaceActionControls();
        lastScmWheelRefreshTick = Long.MIN_VALUE;
        lastScmWheelTopologyFingerprint = "";
        lastScmWheelActionFingerprint = "";
        groundDriveCheckTick = Long.MIN_VALUE;
        groundDriveTopologyFingerprint = "";
    }

    // Refresh the thruster protection
    private void refreshThrusterProtection() {
        ShipControlMap currentMap = effectiveAssemblyMap();
        if (currentMap == null || controller.getLevel() == null) {
            clearThrusterProtection();
            return;
        }
        Set<UUID> protectedSubLevelIds = mappedSubLevelIds();
        Set<ThrusterBlockEntity> currentThrusters = new HashSet<>();
        for (ShipControlMap.PropulsionUnit unit : currentMap.units()) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    controller.getLevel(), unit.subLevelId(), unit.blockPosition());
            if (!(blockEntity instanceof ThrusterBlockEntity thruster)) {
                continue;
            }
            thruster.setShipControlDamageProtectedSubLevels(
                    CONTROL_CHANNEL, protectedSubLevelIds);
            currentThrusters.add(thruster);
        }
        for (ThrusterBlockEntity thruster : List.copyOf(damageProtectedThrusters)) {
            if (!currentThrusters.contains(thruster)) {
                thruster.clearShipControlDamageProtection(CONTROL_CHANNEL);
            }
        }
        damageProtectedThrusters.clear();
        damageProtectedThrusters.addAll(currentThrusters);
    }

    // Clear the thruster protection
    private void clearThrusterProtection() {
        for (ThrusterBlockEntity thruster : List.copyOf(damageProtectedThrusters)) {
            thruster.clearShipControlDamageProtection(CONTROL_CHANNEL);
        }
        damageProtectedThrusters.clear();
        lastDamageProtectionRefreshTick = Long.MIN_VALUE;
    }

    // Mark the operation as failed
    private void fail(String msg, @Nullable Exception err) {
        configurationScanOnly = false;
        syncInitializationDisplays(false, 0, "");
        restoreCalActuators();
        releaseFreezeHandles();
        restoreInitAssembly();
        clearThrusterProtection();
        releaseControlActuators();
        releaseControlAuthority();
        phase = Phase.ERROR;
        status = msg;
        progress = 0.0D;
        showInitProgress(false, true);
        releaseInitProtection();
        releaseInitTracking();
        calibrationUnits.clear();
        calibrationBearings.clear();
        calibrationDockingConnectors.clear();
        calibrationCrnDisplays.clear();
        calibrationAccDisplays.clear();
        calibrationSubLevels.clear();
        initializationSubLevels.clear();
        initBodyStates.clear();
        clearInitMapPlan();
        reconciliationBaseMap = null;
        prevInitMap = null;
        if (err != null) {
            LOGGER.log(System.Logger.Level.ERROR, msg, err);
        }
    }

    // Get the ready status
    private static String readyStatus(ShipControlMap map) {
        long bearingCount = mappedBearingCount(map);
        long vectorThrusterCount = mappedVectorThrusterCount(map);
        long aerodynamicSurfaceCount = map.bearings().stream()
                .flatMap(bearing -> bearing.poses().stream().limit(1))
                .mapToLong(pose -> pose.aerodynamicSurfaces().size())
                .sum();
        return "Ready: " + map.controllableUnitCount() + " controllable of "
                + map.units().size() + " propulsion units; "
                + bearingCount + " control bearing" + (bearingCount == 1 ? "" : "s")
                + "; " + vectorThrusterCount + " vector thruster"
                + (vectorThrusterCount == 1 ? "" : "s")
                + "; " + map.dockingConnectors().size() + " docking connector"
                + (map.dockingConnectors().size() == 1 ? "" : "s")
                + "; " + map.crnDisplays().size() + " CRN display controller"
                + (map.crnDisplays().size() == 1 ? "" : "s")
                + "; " + map.accDisplays().size() + " ACC display"
                + (map.accDisplays().size() == 1 ? "" : "s")
                + "; " + aerodynamicSurfaceCount + " aerodynamic surface"
                + (aerodynamicSurfaceCount == 1 ? "" : "s");
    }

    // Get the mapped bearing count
    static long mappedBearingCount(@Nullable ShipControlMap map) {
        return map == null ? 0L : map.bearings().stream()
                .filter(bearing -> !PROPULSION_VECTOR_ADAPTER.equals(bearing.adapter()))
                .count();
    }

    // Get the mapped vector thruster count
    static long mappedVectorThrusterCount(@Nullable ShipControlMap map) {
        return map == null ? 0L : map.bearings().stream()
                .filter(bearing -> PROPULSION_VECTOR_ADAPTER.equals(bearing.adapter()))
                .count();
    }

    // Get the amount
    private static double amount(Map<String, Double> values, String key) {
        return Mth.clamp(finite(values.getOrDefault(key, 0.0D)), -1.0D, 1.0D);
    }

    // Get the strength
    private static double strength(Map<String, Double> values) {
        double val = finite(values.getOrDefault("strength", 1.0D));
        return val <= 0.0D ? 1.0D : Mth.clamp(val, 0.0D, 1.0D);
    }

    // Get the coordinates
    private static Vec3 coordinates(Map<String, Double> values) {
        return new Vec3(finite(values.getOrDefault("x", 0.0D)),
                finite(values.getOrDefault("y", 0.0D)),
                finite(values.getOrDefault("z", 0.0D)));
    }

    // Get the positive
    private static double positive(double val, double fallback) {
        return Double.isFinite(val) && val > 0.0D ? val : fallback;
    }

    // Get the command speed
    static double commandSpeed(Map<String, Double> values, double fallback) {
        if (values == null || !values.containsKey("speed")) {
            return Math.max(0.0D, finite(fallback));
        }
        return Math.max(0.0D, finite(values.get("speed")));
    }

    // Get an optional direct propulsion request. A negative value preserves
    // the legacy target-speed controller for graph and third-party commands
    // which do not opt into the schedule throttle contract.
    static double commandDriveThrottle(Map<String, Double> values) {
        if (values == null || !values.containsKey("drive_throttle")) {
            return -1.0D;
        }
        return Mth.clamp(finite(values.get("drive_throttle")), 0.0D, 1.0D);
    }

    // Check if collision avoidance was requested
    static boolean collisionAvoidanceRequested(Map<String, Double> values) {
        return values != null
                && finite(values.getOrDefault("avoid_collisions", 0.0D)) > 0.5D;
    }

    // Normalize the value to a finite result
    private static double finite(double val) {
        return Double.isFinite(val) ? val : 0.0D;
    }

    // Normalize the value to a finite result
    private static Vec3 finite(@Nullable Vec3 val) {
        if (val == null || !Double.isFinite(val.x)
                || !Double.isFinite(val.y) || !Double.isFinite(val.z)) {
            return Vec3.ZERO;
        }
        return val;
    }

    // Map the allocation control
    static double mapAllocationControl(double normalized, double minControl, double maxControl,
                                       boolean actuatorMapsOwnRange) {
        double clamped = Mth.clamp(finite(normalized), 0.0D, 1.0D);
        if (clamped <= 1.0E-8D) {
            return 0.0D;
        }
        if (actuatorMapsOwnRange) {
            return clamped;
        }
        double minimum = Mth.clamp(finite(minControl), 0.0D, 1.0D);
        double maximum = Mth.clamp(Math.max(minimum, finite(maxControl)), 0.0D, 1.0D);
        return Mth.lerp(clamped, minimum, maximum);
    }

    // Get the envelope modulation
    static double envelopeModulation(double normalizedControl) {
        return Math.sqrt(Mth.clamp(finite(normalizedControl), 0.0D, 1.0D));
    }

    // Check if this requires calibration refresh
    static boolean requiresCalibrationRefresh(@Nullable ShipControlMap candidate) {
        if (candidate == null) {
            return false;
        }
        boolean legacyActuator = candidate.units().stream()
                .anyMatch(unit -> "direct_signal".equals(unit.adapter())
                        || "direct_signal_v2".equals(unit.adapter())
                        || "direct_signal_v3".equals(unit.adapter())
                        || "direct_signal_v4".equals(unit.adapter())
                        || "digital_envelope".equals(unit.adapter())
                        || "digital_envelope_v2".equals(unit.adapter()));
        if (legacyActuator) {
            return true;
        }
        boolean missingVectorArticulation = candidate.units().stream()
                .filter(unit -> unit.blockId().startsWith("createpropulsion:")
                        && unit.blockId().contains("vector_thruster"))
                .anyMatch(unit -> candidate.bearings().stream().noneMatch(articulation ->
                        PROPULSION_VECTOR_ADAPTER.equals(articulation.adapter())
                                && articulation.hostSubLevelId().equals(unit.subLevelId())
                                && articulation.blockPosition().equals(unit.blockPosition())));
        if (missingVectorArticulation) {
            return true;
        }
        Map<Integer, ShipControlMap.PropulsionUnit> unitsByIndex = new HashMap<>();
        for (ShipControlMap.PropulsionUnit unit : candidate.units()) {
            unitsByIndex.putIfAbsent(unit.index(), unit);
        }
        return candidate.bearings().stream()
                .filter(bearing -> PROPULSION_VECTOR_ADAPTER.equals(bearing.adapter()))
                .flatMap(bearing -> bearing.poses().stream())
                .flatMap(pose -> pose.responses().stream())
                .anyMatch(resp -> {
                    ShipControlMap.PropulsionUnit unit = unitsByIndex.get(
                            resp.propulsionUnitIndex());
                    return unit != null && bearingAuthorityIsUnderreported(
                            unit.maxThrust(), resp.maxThrust());
                });
    }

    // Check if bearing authority is underreported
    static boolean bearingAuthorityIsUnderreported(
            double propulsionMaximum, double bearingMaximum
    ) {
        double calibratedMaximum = Math.max(0.0D, finite(propulsionMaximum));
        return calibratedMaximum > 1.0E-6D
                && Math.max(0.0D, finite(bearingMaximum))
                < calibratedMaximum * 0.5D;
    }

    // Clamp the components
    private static Vec3 clampComponents(Vec3 val) {
        return new Vec3(Mth.clamp(finite(val.x), -1.0D, 1.0D),
                Mth.clamp(finite(val.y), -1.0D, 1.0D),
                Mth.clamp(finite(val.z), -1.0D, 1.0D));
    }

    // Normalize the ship control module
    private static Vec3 normalize(@Nullable Vec3 val, Vec3 fallback) {
        return val == null || val.lengthSqr() <= 1.0E-12D ? fallback : val.normalize();
    }

    // Get the useful message
    private static String usefulMessage(Throwable throwable) {
        String msg = throwable.getMessage();
        return msg == null || msg.isBlank() ? throwable.getClass().getSimpleName() : msg;
    }

    // Check if this is thruster provider
    private static boolean isThrusterProvider(BlockEntity blockEntity) {
        if (blockEntity instanceof ThrusterBlockEntity
                || blockEntity instanceof RcsThrusterBlockEntity
                || blockEntity instanceof PropulsionVectorThrusterAngleAccess) {
            return true;
        }
        ResourceLocation blockId =
                BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        return isThrusterIdentifier(
                blockId == null ? "" : blockId.getPath(),
                blockEntity.getClass().getSimpleName());
    }

    // Check if this is a docking connector
    private static boolean isDockingConnector(BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.getBlockState() == null
                || !blockEntity.getBlockState().hasProperty(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(
                blockEntity.getBlockState().getBlock());
        return id != null && "simulated".equals(id.getNamespace())
                && "docking_connector".equals(id.getPath());
    }

    // Check if this is thruster identifier
    static boolean isThrusterIdentifier(String blockPath, String classSimpleName) {
        String path = blockPath == null ? "" : blockPath.toLowerCase(Locale.ROOT);
        String className = classSimpleName == null
                ? "" : classSimpleName.toLowerCase(Locale.ROOT);
        return path.contains("thruster") || className.contains("thruster");
    }

    // Get the actuator
    private static @Nullable Actuator actuatorFor(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return null;
        }
        if (blockEntity instanceof BlockEntityPropeller propeller) {
            if (blockEntity instanceof IDirectControlReceiver receiver) {
                return new DirectActuator(blockEntity, propeller, receiver);
            }
            Method throttleInput = firstMethod(blockEntity.getClass(), 1,
                    "setThrottle", "setPower",
                    "setThrustOutput", "setTargetThrust");
            if (throttleInput != null) {
                return new ReflectiveActuator(blockEntity, propeller, throttleInput);
            }
            if (blockEntity instanceof KineticBlockEntity kinetic) {
                return new KineticPropellerActuator(kinetic, propeller);
            }
            return new PassivePropellerActuator(blockEntity, propeller);
        }

        Method thrust = firstMethod(blockEntity.getClass(), "getCurrentThrust", "getThrust", "getPower");
        Method dir = firstMethod(blockEntity.getClass(),
                "getThrustDirectionLocal", "getLocalThrustDirection", "getThrustDirection");
        if (thrust != null && dir != null) {
            if (blockEntity instanceof IDirectControlReceiver receiver) {
                return new DirectReceiverActuator(
                        blockEntity, receiver, thrust, dir);
            }
            Method throttleInput = firstMethod(blockEntity.getClass(), 1,
                    "setThrottle", "setPower", "setThrustOutput", "setTargetThrust");
            return throttleInput != null
                    ? new ReflectiveActuator(blockEntity, null, throttleInput)
                    : new ReflectivePassiveActuator(blockEntity);
        }
        return null;
    }

    // Get the actuators
    private static List<Actuator> actuatorsFor(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof RcsThrusterBlockEntity rcsThruster && !blockEntity.isRemoved()) {
            List<Actuator> actuators = new ArrayList<>(4);
            for (Direction nozzle : List.of(
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                actuators.add(new RcsNozzleActuator(rcsThruster, nozzle));
            }
            return List.copyOf(actuators);
        }
        Actuator actuator = actuatorFor(blockEntity);
        return actuator == null ? List.of() : List.of(actuator);
    }

    // Get the actuator for stored
    private @Nullable Actuator actuatorForStored(
            @Nullable BlockEntity blockEntity,
            ShipControlMap.PropulsionUnit unit,
            @Nullable Level ownerLevel
    ) {
        String adapter = unit.adapter();
        if (adapter.startsWith("scm:") && ownerLevel != null) {
            Actuator configured = configuredScmActuatorForStored(blockEntity, unit, ownerLevel);
            if (configured != null) {
                return configured;
            }
            AdvancedContraptionControllerBlockEntity owningController =
                    activeScmActuatorOwners.getOrDefault(
                            new AssemblyUnitIdentity(unit.subLevelId(),
                                    unit.blockPosition(), unit.adapter()),
                            controller);
            List<ScmTarget> linkedTargets = ContraptionNetworkLinkerData.scmTargets(
                    owningController.getStoredLinker()).stream().map(linked -> {
                BlockEntity linkedEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        ownerLevel, linked.subLevelId(), linked.blockPosition());
                BlockState linkedState = linkedEntity == null
                        ? ownerLevel.getBlockState(linked.blockPosition())
                        : linkedEntity.getBlockState();
                ResourceLocation linkedId = BuiltInRegistries.BLOCK.getKey(linkedState.getBlock());
                return new ScmTarget(linked.subLevelId(), linked.blockPosition(),
                        linkedId == null ? linked.blockId() : linkedId.toString(), linked.label(),
                        linked.signalPosition(), linked.signalFace(), linked.controlChannelId());
            }).toList();
            ScmTarget target = linkedTargets.stream()
                    .filter(linked -> Objects.equals(linked.subLevelId(), unit.subLevelId())
                            && linked.blockPosition().equals(unit.blockPosition()))
                    .findFirst().orElse(null);
            if (target == null) {
                return null;
            }
            Map<UUID, SubLevel> linkedSubLevels = new LinkedHashMap<>();
            for (ScmTarget linked : linkedTargets) {
                Object linkedSubLevel = SubLevelBlockEntityCollector.getSubLevel(
                        ownerLevel, linked.subLevelId());
                if (linkedSubLevel instanceof SubLevel sableSubLevel
                        && !sableSubLevel.isRemoved()) {
                    linkedSubLevels.put(linked.subLevelId(), sableSubLevel);
                }
            }
            ServerSubLevel directionFrame = rootSubLevel != null
                    ? rootSubLevel : containingServerSubLevel();
            Vec3 suggestedDir = unit.forceDirection();
            for (LinkedScmProbe linked : linkedScmProbeConfigs(
                    ownerLevel, linkedTargets, linkedSubLevels, directionFrame, suggestedDir,
                    owningController.getShipControlMode().id().equals(
                            ScmBuiltinControlModes.CAR_ID))) {
                ScmProbeActuator candidate = new ScmProbeActuator(
                        linked.level(), linked.target(), linked.blockEntity(), linked.probe());
                if (adapter.equals(candidate.kind())
                        || adapter.equals("scm:" + linked.probe().adapterId())) {
                    return candidate;
                }
            }
            return null;
        }
        List<Actuator> actuators = actuatorsFor(blockEntity);
        for (Actuator actuator : actuators) {
            if (actuator.kind().equals(adapter)) {
                return actuator;
            }
        }
        return actuators.size() == 1 ? actuators.getFirst() : null;
    }

    // Recreate a profile-configured SCM probe directly from its persisted
    // block/face binding. This keeps custom registry probes and face-scoped
    // redstone controls alive after the initial map build; they do not depend
    // on an unrelated Contraption Network Linker target being present.
    private @Nullable Actuator configuredScmActuatorForStored(
            @Nullable BlockEntity initialBlockEntity,
            ShipControlMap.PropulsionUnit unit,
            Level ownerLevel
    ) {
        List<ScmConfigurationProfile.UnitReference> references = new ArrayList<>();
        controller.getScmConfigurationProfile().groups().forEach(group ->
                references.addAll(group.units()));
        for (ScmConfigurationProfile.UnitReference reference : references) {
            if (!configurationReferenceMatchesUnit(reference, unit)) {
                continue;
            }
            SubLevel subLevel = SableLevelApi.subLevel(ownerLevel, reference.subLevelId());
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }
            BlockEntity blockEntity = initialBlockEntity;
            if (blockEntity == null || blockEntity.getLevel() != subLevel.getLevel()
                    || !reference.blockPosition().equals(blockEntity.getBlockPos())) {
                // Prefer the live entity owned by the target sub-level. The
                // probe can then rebind from that same scoped lookup when the
                // moving sub-level replaces its wrapper. The collector is a
                // fallback solely for block-entity-less redstone controls.
                blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                        ownerLevel, reference.subLevelId(), reference.blockPosition());
                if (blockEntity == null) {
                    blockEntity = SubLevelBlockEntityCollector.getBlockEntity(
                            subLevel, reference.blockPosition());
                }
            }
            BlockState state = blockEntity == null
                    ? subLevel.getLevel().getBlockState(reference.blockPosition())
                    : blockEntity.getBlockState();
            if (state.isAir()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            Direction face = reference.face();
            ScmTarget target = new ScmTarget(reference.subLevelId(), reference.blockPosition(),
                    id == null ? unit.blockId() : id.toString(), state.getBlock().getName().getString(),
                    face == null ? null : reference.blockPosition().relative(face), face);
            Vec3 suggested = face == null ? unit.forceDirection()
                    : Vec3.atLowerCornerOf(face.getNormal());
            for (ScmControlProbe probe : ScmControlProbeRegistry.create(
                    subLevel.getLevel(), blockEntity, target, suggested)) {
                ScmProbeActuator actuator = new ScmProbeActuator(
                        subLevel.getLevel(), target, blockEntity, probe);
                if (unit.adapter().equals(actuator.kind())) {
                    return actuator;
                }
            }
        }
        return null;
    }

    // Create the built-in SCM probes
    private static List<ScmControlProbe> createBuiltInScmProbes(
            @Nullable BlockEntity blockEntity,
            ScmControlProbeRegistry.Context ctx
    ) {
        if (blockEntity instanceof WheelMountControlBridge wheel) {
            return List.of(
                    new WheelMountScmProbe(wheel, ctx.target(),
                            ctx.suggestedDirection(), WheelControl.LEFT),
                    new WheelMountScmProbe(wheel, ctx.target(),
                            ctx.suggestedDirection(), WheelControl.RIGHT),
                    new WheelMountScmProbe(wheel, ctx.target(),
                            ctx.suggestedDirection(), WheelControl.BRAKE));
        }
        BlockState state = ctx.level().getBlockState(
                ctx.target().blockPosition());
        if (state.isAir()) {
            return List.of();
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if ("simulated:directional_gearshift".equals(blockId)) {
            if (blockEntity instanceof IDirectControlReceiver receiver) {
                Vec3 dir = ctx.suggestedDirection().horizontalDistanceSqr() > 1.0E-9D
                        ? normalize(ctx.suggestedDirection(), new Vec3(0.0D, 0.0D, 1.0D))
                        : effectDirection(state, ctx.suggestedDirection());
                // A face-bound configuration selects one of the two physical
                // redstone inputs, not an arbitrary logical "forward" label.
                // DirectionalGearshiftBlock defines LEFT as FACING and RIGHT
                // as its opposite, so resolve the player's chosen block face
                // against the live state before creating the safe probe.
                if (ctx.target().usesFaceControl()
                        && state.hasProperty(BlockStateProperties.FACING)) {
                    Direction leftFace = state.getValue(BlockStateProperties.FACING);
                    Direction selectedFace = ctx.target().signalFace();
                    if (selectedFace.equals(leftFace) || selectedFace.equals(leftFace.getOpposite())) {
                        boolean forward = selectedFace.equals(leftFace.getOpposite());
                        return List.of(new DirectionalGearshiftScmProbe(
                                blockEntity, receiver, ctx.target(), dir, forward));
                    }
                    // The remaining faces are still valid generic redstone
                    // planes, but they are not inputs of this gearshift.
                    return List.of(new RedstoneScmProbe(
                            ctx.level(), blockEntity, ctx.target(), dir));
                }
                return List.of(
                        new DirectionalGearshiftScmProbe(
                                blockEntity, receiver, ctx.target(), dir, true),
                        new DirectionalGearshiftScmProbe(
                                blockEntity, receiver, ctx.target(), dir, false));
            }
            return List.of();
        }
        Vec3 dir = ctx.suggestedDirection().horizontalDistanceSqr() > 1.0E-9D
                ? normalize(ctx.suggestedDirection(), new Vec3(0.0D, 0.0D, 1.0D))
                : effectDirection(state, ctx.suggestedDirection());
        ScmControlProbe driveProbe = null;
        if (blockEntity instanceof VariableTransmissionBlockEntity transmission
                && state.getBlock() instanceof VariableTransmissionBlock block) {
            driveProbe = new VariableTransmissionScmProbe(transmission, block, dir);
        }
        if (driveProbe == null && blockEntity instanceof SpeedControllerBlockEntity speedController
                && speedController.targetSpeed != null) {
            driveProbe = new SpeedControllerScmProbe(speedController, dir);
        }
        if (driveProbe != null) {
            return List.of(driveProbe);
        }
        return List.of(new RedstoneScmProbe(
                ctx.level(), blockEntity, ctx.target(), dir));
    }

    // Get the effect direction
    private static Vec3 effectDirection(BlockState state, Vec3 fallback) {
        if (state != null && state.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
            if (dir.horizontalDistanceSqr() > 1.0E-9D) {
                return dir.normalize();
            }
        }
        if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return Vec3.atLowerCornerOf(
                    state.getValue(BlockStateProperties.HORIZONTAL_FACING).getNormal()).normalize();
        }
        if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
        }
        if (state != null && state.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            if (axis.isHorizontal()) {
                return axis == Direction.Axis.X
                        ? new Vec3(0.0D, 0.0D, 1.0D)
                        : new Vec3(1.0D, 0.0D, 0.0D);
            }
        }
        return fallback == null || fallback.horizontalDistanceSqr() <= 1.0E-9D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(fallback.x, 0.0D, fallback.z).normalize();
    }

    // Get the bearing actuators
    private static List<BearingActuator> bearingActuatorsFor(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return List.of();
        }
        if (blockEntity instanceof PropulsionVectorThrusterAngleAccess vectorThruster) {
            return List.of(new PropulsionVectorThrusterActuator(vectorThruster));
        }
        if (blockEntity instanceof VectorBearingBlockEntity vectorBearing) {
            return vectorBearing.getMountedSubLevelId() == null
                    ? List.of() : List.of(new VectorBearingActuator(vectorBearing));
        }
        if (blockEntity instanceof ThrusterBearingBlockEntity thrusterBearing) {
            return thrusterBearing.getSubLevelID() == null
                    ? List.of() : List.of(new ThrusterBearingActuator(thrusterBearing));
        }
        if (blockEntity instanceof AileronBearingBlockEntity aileronBearing) {
            List<BearingActuator> actuators = new ArrayList<>(2);
            for (BearingHead head : BearingHead.values()) {
                if (aileronBearing.getMountedSubLevelId(head) != null) {
                    actuators.add(new AileronBearingActuator(aileronBearing, head));
                }
            }
            return List.copyOf(actuators);
        }
        return List.of();
    }

    // Get the bearing actuator for stored
    private static @Nullable BearingActuator bearingActuatorForStored(
            @Nullable BlockEntity blockEntity,
            String adapter
    ) {
        for (BearingActuator actuator : bearingActuatorsFor(blockEntity)) {
            if (actuator.kind().equals(adapter)) {
                return actuator;
            }
        }
        return null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                   ACTUATOR ADAPTERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Expose the bearing actuator
    private interface BearingActuator {
        // Get the kind
        String kind();

        // Get the bearing actuator display name
        String displayName();

        // Get the second axis label
        default String secondAxisLabel() {
            return "z";
        }

        // Check if the actuator directly controls propulsion
        default boolean directlyControlsPropulsion() {
            return false;
        }

        // Get the child sublevel ids
        List<UUID> childSubLevelIds();

        // Get the poses
        List<ShipBearingPlanner.Pose> poses();

        // Get the minimum x
        double minX();

        // Get the maximum x
        double maxX();

        // Get the minimum z
        double minZ();

        // Get the maximum z
        double maxZ();

        // Apply the pose
        void applyPose(ShipBearingPlanner.Pose pose);

        // Get the current pose
        ShipBearingPlanner.Pose currentPose();

        // Check if this is at the target pose
        default boolean atPose(ShipBearingPlanner.Pose pose) {
            ShipBearingPlanner.Pose current = currentPose();
            return Math.abs(current.angleX() - pose.angleX()) <= ANGLE_TOLERANCE_DEGREES
                    && Math.abs(current.angleZ() - pose.angleZ()) <= ANGLE_TOLERANCE_DEGREES;
        }

        // Restore the bearing actuator
        void restore();
    }

    // Handle the abstract bearing actuator
    private abstract static class AbstractBearingActuator implements BearingActuator {
        // Tracks whether abstract bearing actuator is restored
        private boolean restored;

        // Mark the applied
        protected final void markApplied() {
            restored = false;
        }

        // Begin the restore
        protected final boolean beginRestore() {
            if (restored) {
                return false;
            }
            restored = true;
            return true;
        }
    }

    // Handle the propulsion vector thruster actuator
    private static final class PropulsionVectorThrusterActuator extends AbstractBearingActuator {
        // Thruster
        private final PropulsionVectorThrusterAngleAccess thruster;
        // Original override state
        private final boolean originalOverride;
        // Original x
        private final double originalX;
        // Original y
        private final double originalY;
        // Limit
        private final double limit;

        // Initialize the propulsion vector thruster actuator
        private PropulsionVectorThrusterActuator(PropulsionVectorThrusterAngleAccess thruster) {
            this.thruster = thruster;
            Map<String, Object> angles = readAngles();
            originalOverride = booleanValue(angles.get("override"), false);
            originalX = numberValue(angles.get("targetX"), 0.0D);
            originalY = numberValue(angles.get("targetY"), 0.0D);
            limit = PropulsionVectorThrusterAngles.maxAngleDegrees();
        }

        // Get the kind
        @Override
        public String kind() {
            return PROPULSION_VECTOR_ADAPTER;
        }

        // Get the propulsion vector thruster actuator display name
        @Override
        public String displayName() {
            return "Create Propulsion Vector Thruster";
        }

        // Get the second axis label
        @Override
        public String secondAxisLabel() {
            return "y";
        }

        // Check if the actuator directly controls propulsion
        @Override
        public boolean directlyControlsPropulsion() {
            return true;
        }

        // Get the child sublevel ids
        @Override
        public List<UUID> childSubLevelIds() {
            return List.of();
        }

        // Get the poses
        @Override
        public List<ShipBearingPlanner.Pose> poses() {
            return ShipBearingPlanner.independentAxisPoses(
                    -limit, limit, -limit, limit);
        }

        // Get the minimum x
        @Override
        public double minX() {
            return -limit;
        }

        // Get the maximum x
        @Override
        public double maxX() {
            return limit;
        }

        // Get the minimum z
        @Override
        public double minZ() {
            return -limit;
        }

        // Get the maximum z
        @Override
        public double maxZ() {
            return limit;
        }

        // Apply the pose
        @Override
        public void applyPose(ShipBearingPlanner.Pose pose) {
            markApplied();
            thruster.createThrusters$setVectorAngles(pose.angleX(), pose.angleZ());
        }

        // Get the current pose
        @Override
        public ShipBearingPlanner.Pose currentPose() {
            Map<String, Object> angles = readAngles();
            return new ShipBearingPlanner.Pose(
                    numberValue(angles.get("currentX"), 0.0D),
                    numberValue(angles.get("currentY"), 0.0D));
        }

        // Restore the propulsion vector thruster actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            if (originalOverride) {
                thruster.createThrusters$setVectorAngles(originalX, originalY);
            } else {
                thruster.createThrusters$clearVectorAngles();
            }
        }

        // Read the angles
        private Map<String, Object> readAngles() {
            try {
                Map<String, Object> angles = thruster.createThrusters$getVectorAngles();
                return angles == null ? Map.of() : angles;
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
    }

    // Handle the vector bearing actuator
    private static final class VectorBearingActuator extends AbstractBearingActuator {
        // Bearing
        private final VectorBearingBlockEntity bearing;
        // Original mode
        private final VectorBearingBlockEntity.ControlMode originalMode;
        // Original override state
        private final boolean originalOverride;
        // Original x
        private final double originalX;
        // Original z
        private final double originalZ;
        // Limit
        private final double limit;
        // Tracked children
        private final List<UUID> children;

        // Initialize the vector bearing actuator
        private VectorBearingActuator(VectorBearingBlockEntity bearing) {
            this.bearing = bearing;
            originalMode = bearing.getControlMode();
            originalOverride = bearing.hasComputerOverride();
            originalX = bearing.getComputerXDegrees();
            originalZ = bearing.getComputerZDegrees();
            limit = Math.max(0.0D, bearing.getMaxTiltDegrees());
            UUID child = bearing.getMountedSubLevelId();
            children = child == null ? List.of() : List.of(child);
        }

        // Get the kind
        @Override
        public String kind() {
            return "vector_bearing";
        }

        // Get the vector bearing actuator display name
        @Override
        public String displayName() {
            return "Vector Bearing";
        }

        // Get the child sublevel ids
        @Override
        public List<UUID> childSubLevelIds() {
            return children;
        }

        // Get the poses
        @Override
        public List<ShipBearingPlanner.Pose> poses() {
            return ShipBearingPlanner.vectorPoses(limit);
        }

        // Get the minimum x
        @Override
        public double minX() {
            return -limit;
        }

        // Get the maximum x
        @Override
        public double maxX() {
            return limit;
        }

        // Get the minimum z
        @Override
        public double minZ() {
            return -limit;
        }

        // Get the maximum z
        @Override
        public double maxZ() {
            return limit;
        }

        // Apply the pose
        @Override
        public void applyPose(ShipBearingPlanner.Pose pose) {
            markApplied();
            bearing.setControlMode(VectorBearingBlockEntity.ControlMode.COMPUTER);
            bearing.setComputerAnglesDegrees(pose.angleX(), pose.angleZ());
        }

        // Get the current pose
        @Override
        public ShipBearingPlanner.Pose currentPose() {
            return new ShipBearingPlanner.Pose(
                    bearing.getAppliedXDegrees(), bearing.getAppliedZDegrees());
        }

        // Restore the vector bearing actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            if (originalOverride) {
                bearing.setComputerAnglesDegrees(originalX, originalZ);
            } else {
                bearing.clearComputerAngles();
            }
            bearing.setControlMode(originalMode);
        }
    }

    // Handle the thruster bearing actuator
    private static final class ThrusterBearingActuator extends AbstractBearingActuator {
        // Bearing
        private final ThrusterBearingBlockEntity bearing;
        // Original mode
        private final ThrusterBearingBlockEntity.ControlMode originalMode;
        // Original override state
        private final boolean originalOverride;
        // Original command angle
        private final double originalCommandAngle;
        // Minimum
        private final double minimum;
        // Maximum
        private final double maximum;
        // Tracks whether swivels are set
        private final boolean swivels;
        // Tracked children
        private final List<UUID> children;

        // Initialize the thruster bearing actuator
        private ThrusterBearingActuator(ThrusterBearingBlockEntity bearing) {
            this.bearing = bearing;
            originalMode = bearing.getControlMode();
            originalOverride = bearing.hasPivotOverride();
            swivels = bearing.getAngleMode() == ThrusterBearingBlockEntity.AngleMode.SWIVEL;
            double originalAngle = bearing.isInverted()
                    ? -bearing.getSmoothedPivotAngleDeg() : bearing.getSmoothedPivotAngleDeg();
            originalCommandAngle = swivels ? wrapDegrees(originalAngle) : originalAngle;
            minimum = swivels ? 0.0D : bearing.getMinAngleDegrees();
            maximum = swivels
                    ? 360.0D - ShipBearingPlanner.ANGLE_STEP_DEGREES
                    : bearing.getMaxAngleDegrees();
            UUID child = bearing.getSubLevelID();
            children = child == null ? List.of() : List.of(child);
        }

        // Get the kind
        @Override
        public String kind() {
            return "thruster_bearing";
        }

        // Get the thruster bearing actuator display name
        @Override
        public String displayName() {
            return "Thruster Bearing";
        }

        // Get the child sublevel ids
        @Override
        public List<UUID> childSubLevelIds() {
            return children;
        }

        // Get the poses
        @Override
        public List<ShipBearingPlanner.Pose> poses() {
            return ShipBearingPlanner.singleAxisPoses(minimum, maximum);
        }

        // Get the minimum x
        @Override
        public double minX() {
            return minimum;
        }

        // Get the maximum x
        @Override
        public double maxX() {
            return maximum;
        }

        // Get the minimum z
        @Override
        public double minZ() {
            return 0.0D;
        }

        // Get the maximum z
        @Override
        public double maxZ() {
            return 0.0D;
        }

        // Apply the pose
        @Override
        public void applyPose(ShipBearingPlanner.Pose pose) {
            markApplied();
            bearing.setPivotAngleDegrees(pose.angleX());
        }

        // Get the current pose
        @Override
        public ShipBearingPlanner.Pose currentPose() {
            double fallback = bearing.getSmoothedPivotAngleDeg();
            Method physicalAngle = findMethod(
                    bearing.getClass(), "getLivePhysicalAngleDegrees", 1);
            double physical = numberValue(
                    physicalAngle == null ? null : invoke(bearing, physicalAngle, fallback),
                    fallback);
            double commandAngle = bearing.isInverted() ? -physical : physical;
            if (swivels) {
                commandAngle = wrapDegrees(commandAngle);
            }
            return new ShipBearingPlanner.Pose(commandAngle, 0.0D);
        }

        // Check if this is at the target pose
        @Override
        public boolean atPose(ShipBearingPlanner.Pose pose) {
            double current = currentPose().angleX();
            double difference = swivels
                    ? shortestAngleDegrees(current - pose.angleX())
                    : current - pose.angleX();
            return Math.abs(difference) <= ANGLE_TOLERANCE_DEGREES;
        }

        // Restore the thruster bearing actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            if (originalOverride) {
                bearing.setPivotAngleDegrees(originalCommandAngle);
            } else {
                bearing.clearPivotOverride();
            }
            bearing.setControlMode(originalMode);
        }

        // Wrap the degrees
        private static double wrapDegrees(double angle) {
            double wrapped = finite(angle) % 360.0D;
            return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
        }
    }

    // Handle the aileron bearing actuator
    private static final class AileronBearingActuator extends AbstractBearingActuator {
        // Bearing
        private final AileronBearingBlockEntity bearing;
        // Head
        private final BearingHead head;
        // Original mode
        private final AileronBearingBlockEntity.ControlMode originalMode;
        // Original head mode
        private final AileronBearingBlockEntity.HeadMode originalHeadMode;
        // Original override state
        private final boolean originalOverride;
        // Original target
        private final double originalTarget;
        // Minimum
        private final double minimum;
        // Maximum
        private final double maximum;
        // Tracked children
        private final List<UUID> children;

        // Initialize the aileron bearing actuator
        private AileronBearingActuator(
                AileronBearingBlockEntity bearing,
                BearingHead head
        ) {
            this.bearing = bearing;
            this.head = head;
            originalMode = bearing.getControlMode();
            originalHeadMode = bearing.getHeadMode();
            originalOverride = bearing.hasHeadTargetOverride(head);
            originalTarget = bearing.getHeadTargetAngle(head);
            minimum = bearing.getMinAngle(head);
            maximum = bearing.getMaxAngle(head);
            UUID child = bearing.getMountedSubLevelId(head);
            children = child == null ? List.of() : List.of(child);
        }

        // Get the kind
        @Override
        public String kind() {
            return "aileron_" + head.serializedName();
        }

        // Get the aileron bearing actuator display name
        @Override
        public String displayName() {
            return "Aileron Bearing " + (head == BearingHead.PRIMARY
                    ? "Primary" : "Secondary");
        }

        // Get the child sublevel ids
        @Override
        public List<UUID> childSubLevelIds() {
            return children;
        }

        // Get the poses
        @Override
        public List<ShipBearingPlanner.Pose> poses() {
            return ShipBearingPlanner.singleAxisPoses(minimum, maximum);
        }

        // Get the minimum x
        @Override
        public double minX() {
            return minimum;
        }

        // Get the maximum x
        @Override
        public double maxX() {
            return maximum;
        }

        // Get the minimum z
        @Override
        public double minZ() {
            return 0.0D;
        }

        // Get the maximum z
        @Override
        public double maxZ() {
            return 0.0D;
        }

        // Apply the pose
        @Override
        public void applyPose(ShipBearingPlanner.Pose pose) {
            markApplied();
            bearing.setHeadMode(AileronBearingBlockEntity.HeadMode.PRECISE);
            bearing.setHeadTargetAngle(head, pose.angleX());
        }

        // Get the current pose
        @Override
        public ShipBearingPlanner.Pose currentPose() {
            return new ShipBearingPlanner.Pose(bearing.getHeadAngle(head), 0.0D);
        }

        // Restore the aileron bearing actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            if (originalOverride) {
                bearing.setHeadTargetAngle(head, originalTarget);
            } else {
                bearing.clearHeadTargetOverride(head);
            }
            bearing.setHeadMode(originalHeadMode);
            bearing.setControlMode(originalMode);
        }
    }

    // Handle the SCM probe actuator
    private static final class ScmProbeActuator implements Actuator {
        // Level
        private final Level level;
        // SCM probe actuator target
        private final ScmTarget target;
        // Source block entity. A moving Sable sub-level may replace this
        // wrapper while keeping the same local block position, so it must be
        // rebound through the SCM registry instead of being treated as the
        // permanent identity of the controlled block.
        private @Nullable BlockEntity sourceBlockEntity;
        // Probe. This is replaced together with the source block entity when
        // a loaded sub-level exposes a new live wrapper for the same target.
        private ScmControlProbe probe;

        // Initialize the SCM probe actuator
        private ScmProbeActuator(
                Level level,
                ScmTarget target,
                @Nullable BlockEntity sourceBlockEntity,
                ScmControlProbe probe
        ) {
            this.level = level;
            this.target = target;
            this.sourceBlockEntity = sourceBlockEntity;
            this.probe = probe;
        }

        // Get the kind
        @Override
        public String kind() {
            return "scm:" + probe.adapterId()
                    + (target.usesFaceControl()
                    ? ":face_" + target.signalFace().getSerializedName() : "");
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return true;
        }

        // Control the group id
        @Override
        public String controlGroupId() {
            return probe.controlGroupId();
        }

        // Check if this is available
        @Override
        public boolean isAvailable() {
            return rebindLiveProbe();
        }

        // Rebind a probe after a moving sub-level replaces its block-entity
        // wrapper. Probes deliberately own the actual block access (direct
        // controller calls, variable-transmission power, custom integrations,
        // and so on), so retaining an old probe after this transition can
        // appear as a one-tick pulse even when the target position remains
        // valid. Recreating through the registry makes this apply to every
        // registered SCM control instead of special-casing individual blocks.
        private boolean rebindLiveProbe() {
            BlockEntity current = SimulatedHelper.findLoadedBlockEntityExact(
                    level, target.subLevelId(), target.blockPosition());
            if (current != null) {
                if (current.isRemoved() || current.getBlockState().isAir()) {
                    return false;
                }
                if (current != sourceBlockEntity) {
                    SubLevel subLevel = target.subLevelId() == null
                            ? null : SableLevelApi.subLevel(level, target.subLevelId());
                    Level targetLevel = subLevel == null ? level : subLevel.getLevel();
                    String adapterId = probe.adapterId();
                    ScmControlProbe replacement = ScmControlProbeRegistry.create(
                                    targetLevel, current, target,
                                    probe.localEffectDirection())
                            .stream().filter(candidate -> adapterId.equals(candidate.adapterId()))
                            .findFirst().orElse(null);
                    if (replacement == null) {
                        return false;
                    }
                    sourceBlockEntity = current;
                    probe = replacement;
                }
                return probe.isAvailable();
            }
            if (sourceBlockEntity != null) {
                return false;
            }
            if (!SubLevelBlockEntityCollector.isTargetLoaded(
                    level, target.subLevelId(), target.blockPosition())) {
                return false;
            }
            SubLevel subLevel = target.subLevelId() == null
                    ? null : SableLevelApi.subLevel(level, target.subLevelId());
            BlockState state = subLevel == null
                    ? level.getBlockState(target.blockPosition())
                    : subLevel.getLevel().getBlockState(target.blockPosition());
            return !state.isAir() && probe.isAvailable();
        }

        // Check if this requires continuous control
        @Override
        public boolean requiresContinuousControl() {
            return true;
        }

        // Check if this uses physical calibration
        @Override
        public boolean usesPhysicalCalibration() {
            return probe instanceof CompositeScmProbe;
        }

        // Check if this requires observed response
        @Override
        public boolean requiresObservedResponse() {
            return probe instanceof CompositeScmProbe;
        }

        // Face-bound targets have an authored signed action and are driven
        // directly from the final SCM demand rather than vector allocation.
        @Override
        public boolean isFaceActionControl() {
            return target.usesFaceControl();
        }

        // Check if this is wheel control
        @Override
        public boolean isWheelControl() {
            return probe instanceof CompositeScmProbe composite
                    && composite.isWheelConfiguration()
                    || probe instanceof WheelMountScmProbe;
        }

        // Check if this is a kinetic control
        @Override
        public boolean isKineticControl() {
            return probe instanceof CompositeScmProbe composite
                    && composite.isKineticConfiguration()
                    || probe instanceof DirectionalGearshiftScmProbe;
        }

        // Get the kinetic control size
        @Override
        public int kineticControlSize() {
            if (probe instanceof CompositeScmProbe composite) {
                return composite.kineticControlSize();
            }
            return probe instanceof DirectionalGearshiftScmProbe ? 1 : 0;
        }

        // Get the kinetic control domain
        @Override
        public Set<String> kineticControlDomain() {
            if (probe instanceof CompositeScmProbe composite) {
                return composite.controlDomain();
            }
            return probe instanceof DirectionalGearshiftScmProbe
                    ? Set.of("target:" + target.stableId()) : Set.of();
        }

        // Apply the SCM probe actuator
        @Override
        public void apply(double control) {
            if (rebindLiveProbe()) {
                probe.apply(Mth.clamp(control, minControl(), maxControl()));
            }
        }

        // Return the SCM probe actuator to neutral
        @Override
        public void neutralize() {
            probe.apply(neutralControl());
        }

        // Get the SCM probe actuator neutral control
        @Override
        public double neutralControl() {
            return Mth.clamp(probe.neutralControl(), minControl(), maxControl());
        }

        // Apply the SCM probe actuator
        private void applyWith(ScmProbeActuator companion, double control) {
            double clamped = Mth.clamp(control, minControl(), maxControl());
            if (probe instanceof CompositeScmProbe composite
                    && companion.probe instanceof CompositeScmProbe companionComposite) {
                composite.applyWith(companionComposite, clamped);
                return;
            }
            apply(clamped);
            companion.apply(1.0D);
        }

        // Read the SCM probe actuator
        @Override
        public Reading read() {
            if (!rebindLiveProbe()) {
                return new Reading(0.0D, 0.0D, false);
            }
            ScmControlProbe.Reading reading = probe.read();
            return new Reading(reading.speed(), reading.effect(), reading.active());
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            return probe.localEffectDirection();
        }

        // Get the local force position
        @Override
        public Vec3 localForcePosition() {
            return probe.localEffectPosition();
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return probe.minControl();
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return probe.maxControl();
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return Math.max(1.0D, Math.abs(maxControl() - minControl()));
        }

        // Restore the SCM probe actuator
        @Override
        public void restore() {
            if (rebindLiveProbe()) {
                probe.restore();
            }
        }

        // Check if the actuator maps its own control range
        @Override
        public boolean mapsOwnControlRange() {
            return true;
        }

        // Get the expected thrust sign
        @Override
        public double expectedThrustSign() {
            return 1.0D;
        }
    }

    // Store the linked SCM probe
    private record LinkedScmProbe(
            SubLevel subLevel,
            Level level,
            ScmTarget target,
            @Nullable BlockEntity blockEntity,
            ScmControlProbe probe
    ) {
    }

    // Handle the composite SCM probe
    private static final class CompositeScmProbe implements ScmControlProbe {
        // Tracked selected
        private final List<LinkedScmProbe> selected;
        // Tracked all probes
        private final List<LinkedScmProbe> allProbes;
        // Group id
        private final String groupId;
        // Adapter id
        private final String adapterId;
        // Composite SCM probe direction
        private final Vec3 direction;
        // Composite SCM probe control domain
        private final Set<String> controlDomain;

        // Initialize the composite SCM probe
        private CompositeScmProbe(
                List<LinkedScmProbe> selected,
                List<LinkedScmProbe> allProbes,
                String groupId
        ) {
            this.selected = List.copyOf(selected);
            this.allProbes = List.copyOf(allProbes);
            this.groupId = groupId;
            String signature = this.selected.stream()
                    .map(member -> member.target().stableId() + '='
                            + member.probe().adapterId())
                    .sorted().reduce((left, right) -> left + '|' + right)
                    .orElse("empty");
            this.adapterId = "configuration_" + UUID.nameUUIDFromBytes(
                    signature.getBytes(StandardCharsets.UTF_8));
            this.direction = normalize(
                    this.selected.getFirst().probe().localEffectDirection(),
                    new Vec3(0.0D, 0.0D, 1.0D));
            this.controlDomain = this.allProbes.stream()
                    .flatMap(member -> {
                        String targetId = "target:" + member.target().stableId();
                        String group = member.probe().controlGroupId();
                        return group == null || group.isBlank()
                                ? java.util.stream.Stream.of(targetId)
                                : java.util.stream.Stream.of(targetId, "group:" + group);
                    })
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return adapterId;
        }

        // Get the composite SCM probe display name
        @Override
        public String displayName() {
            return "Linked control configuration (" + selected.size() + ")";
        }

        // Control the group id
        @Override
        public String controlGroupId() {
            return groupId;
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Apply the composite SCM probe
        @Override
        public void apply(double control) {
            Set<ScmControlProbe> selectedProbes = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            selected.forEach(member -> selectedProbes.add(member.probe()));
            Set<String> selectedGroups = selected.stream()
                    .map(member -> member.probe().controlGroupId())
                    .filter(group -> group != null && !group.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (LinkedScmProbe member : allProbes) {
                String group = member.probe().controlGroupId();
                if (!selectedProbes.contains(member.probe())
                        && (group == null || group.isBlank()
                        || !selectedGroups.contains(group))) {
                    member.probe().apply(neutralValue(member.probe()));
                }
            }
            Set<ScmControlProbe> applied = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            for (LinkedScmProbe member : selected) {
                if (applied.add(member.probe())) {
                    member.probe().apply(control);
                }
            }
        }

        // Apply the composite SCM probe
        private void applyWith(CompositeScmProbe companion, double control) {
            Set<ScmControlProbe> active = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            selected.forEach(member -> active.add(member.probe()));
            companion.selected.forEach(member -> active.add(member.probe()));
            Set<String> activeGroups = java.util.stream.Stream
                    .concat(selected.stream(), companion.selected.stream())
                    .map(member -> member.probe().controlGroupId())
                    .filter(group -> group != null && !group.isBlank())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<ScmControlProbe> reset = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            for (LinkedScmProbe member : java.util.stream.Stream
                    .concat(allProbes.stream(), companion.allProbes.stream()).toList()) {
                String group = member.probe().controlGroupId();
                if (reset.add(member.probe()) && !active.contains(member.probe())
                        && (group == null || group.isBlank()
                        || !activeGroups.contains(group))) {
                    member.probe().apply(neutralValue(member.probe()));
                }
            }
            Set<ScmControlProbe> applied = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            for (LinkedScmProbe member : selected) {
                if (applied.add(member.probe())) {
                    member.probe().apply(control);
                }
            }
            for (LinkedScmProbe member : companion.selected) {
                if (applied.add(member.probe())) {
                    member.probe().apply(1.0D);
                }
            }
        }

        // Read the composite SCM probe
        @Override
        public Reading read() {
            double speed = 0.0D;
            double effect = 0.0D;
            boolean active = false;
            for (LinkedScmProbe member : selected) {
                Reading reading = member.probe().read();
                speed = Math.max(speed, Math.abs(reading.speed()));
                effect += Math.abs(reading.effect());
                active |= reading.active();
            }
            return new Reading(speed, effect, active);
        }

        // Get the local effect direction
        @Override
        public Vec3 localEffectDirection() {
            return direction;
        }

        // Get the local effect position
        @Override
        public Vec3 localEffectPosition() {
            return selected.getFirst().probe().localEffectPosition();
        }

        // Check if this is available
        @Override
        public boolean isAvailable() {
            return allProbes.stream().allMatch(member -> {
                BlockEntity blockEntity = member.blockEntity();
                if (!member.probe().isAvailable()) {
                    return false;
                }
                BlockEntity current = SimulatedHelper.findLoadedBlockEntityExact(
                        member.level(), member.target().subLevelId(),
                        member.target().blockPosition());
                if (blockEntity != null) {
                    return !blockEntity.isRemoved() && current == blockEntity
                            && !current.getBlockState().isAir();
                }
                if (!SubLevelBlockEntityCollector.isTargetLoaded(
                        member.level(), member.target().subLevelId(),
                        member.target().blockPosition())) {
                    return false;
                }
                SubLevel subLevel = member.target().subLevelId() == null
                        ? null : SableLevelApi.subLevel(
                        member.level(), member.target().subLevelId());
                BlockState state = subLevel == null
                        ? member.level().getBlockState(member.target().blockPosition())
                        : subLevel.getLevel().getBlockState(member.target().blockPosition());
                return !state.isAir();
            });
        }

        // Check if this is wheel configuration
        private boolean isWheelConfiguration() {
            return !selected.isEmpty()
                    && selected.stream().allMatch(member ->
                    member.probe() instanceof WheelMountScmProbe);
        }

        // Check if this is a kinetic configuration
        private boolean isKineticConfiguration() {
            return !selected.isEmpty()
                    && !isWheelConfiguration()
                    && selected.stream().anyMatch(member ->
                    member.blockEntity() instanceof KineticBlockEntity);
        }

        // Get the kinetic control size
        private int kineticControlSize() {
            return isKineticConfiguration() ? selected.size() : 0;
        }

        // Get the composite SCM probe control domain
        private Set<String> controlDomain() {
            return controlDomain;
        }

        // Get one probe's bounded neutral value
        private static double neutralValue(ScmControlProbe probe) {
            return Mth.clamp(finite(probe.neutralControl()),
                    probe.minControl(), probe.maxControl());
        }

        // Restore the composite SCM probe
        @Override
        public void restore() {
            for (LinkedScmProbe member : allProbes.reversed()) {
                member.probe().restore();
            }
        }
    }

    // Handle the speed controller SCM probe
    private static final class SpeedControllerScmProbe implements ScmControlProbe {
        private static final int PROBE_MAX_SPEED = 256;
        // Speed controller SCM probe controller
        private final SpeedControllerBlockEntity controller;
        // Speed controller SCM probe direction
        private final Vec3 direction;
        // Original target speed
        private final int originalTargetSpeed;

        // Initialize the speed controller SCM probe
        private SpeedControllerScmProbe(
                SpeedControllerBlockEntity controller, Vec3 dir
        ) {
            this.controller = controller;
            this.direction = dir;
            this.originalTargetSpeed = controller.targetSpeed.getValue();
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return "create_rotation_speed_controller";
        }

        // Get the speed controller SCM probe display name
        @Override
        public String displayName() {
            return "Rotation Speed Controller";
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Apply the speed controller SCM probe
        @Override
        public void apply(double control) {
            int target = (int) Math.round(
                    Mth.clamp(control, 0.0D, 1.0D) * PROBE_MAX_SPEED);
            if (controller.targetSpeed.getValue() == target) {
                return;
            }
            controller.targetSpeed.setValue(target);
            controller.setChanged();
            controller.sendData();
        }

        // Read the speed controller SCM probe
        @Override
        public Reading read() {
            double speed = Math.abs(controller.getSpeed());
            return new Reading(speed, speed, speed > 1.0E-4D);
        }

        // Get the local effect direction
        @Override
        public Vec3 localEffectDirection() {
            return direction;
        }

        // Get the local effect position
        @Override
        public Vec3 localEffectPosition() {
            return controller.getBlockPos().getCenter();
        }

        // Restore the speed controller SCM probe
        @Override
        public void restore() {
            if (controller.targetSpeed.getValue() == originalTargetSpeed) {
                return;
            }
            controller.targetSpeed.setValue(originalTargetSpeed);
            controller.setChanged();
            controller.sendData();
        }
    }

    // Handle the variable transmission SCM probe
    private static final class VariableTransmissionScmProbe implements ScmControlProbe {
        // Transmission
        private final VariableTransmissionBlockEntity transmission;
        // Block
        private final VariableTransmissionBlock block;
        // Variable transmission SCM probe direction
        private final Vec3 direction;
        // Original power
        private final int originalPower;

        // Initialize the variable transmission SCM probe
        private VariableTransmissionScmProbe(
                VariableTransmissionBlockEntity transmission,
                VariableTransmissionBlock block,
                Vec3 dir
        ) {
            this.transmission = transmission;
            this.block = block;
            this.direction = dir;
            this.originalPower = transmission.getBlockState().getValue(
                    VariableTransmissionBlock.POWER);
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return "variable_transmission";
        }

        // Get the variable transmission SCM probe display name
        @Override
        public String displayName() {
            return "Variable Transmission";
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Apply the variable transmission SCM probe
        @Override
        public void apply(double control) {
            Level level = transmission.getLevel();
            BlockState state = transmission.getBlockState();
            if (level == null || transmission.isRemoved()
                    || !(state.getBlock() instanceof VariableTransmissionBlock)) {
                return;
            }
            int power = Mth.clamp((int) Math.round(control * 15.0D), 0, 15);
            block.applyPower(level, transmission.getBlockPos(), state, power);
        }

        // Read the variable transmission SCM probe
        @Override
        public Reading read() {
            int power = transmission.getBlockState().getValue(
                    VariableTransmissionBlock.POWER);
            double outputSpeed = Math.abs(transmission.getSpeed()) * power / 15.0D;
            return new Reading(outputSpeed, outputSpeed,
                    power > 0 && outputSpeed > 1.0E-4D);
        }

        // Get the local effect direction
        @Override
        public Vec3 localEffectDirection() {
            return direction;
        }

        // Get the local effect position
        @Override
        public Vec3 localEffectPosition() {
            return transmission.getBlockPos().getCenter();
        }

        // Restore the variable transmission SCM probe
        @Override
        public void restore() {
            Level level = transmission.getLevel();
            BlockState state = transmission.getBlockState();
            if (level != null && !transmission.isRemoved()
                    && state.getBlock() instanceof VariableTransmissionBlock) {
                block.applyPower(level, transmission.getBlockPos(), state, originalPower);
            }
        }
    }

    // Handle the directional gearshift SCM probe
    private static final class DirectionalGearshiftScmProbe implements ScmControlProbe {
        // Bound block entity
        private final BlockEntity blockEntity;
        // Control
        private final IDirectControlReceiver control;
        // Directional gearshift SCM probe target
        private final ScmTarget target;
        // Directional gearshift SCM probe direction
        private final Vec3 direction;
        // Tracks whether forward is set
        private final boolean forward;
        // Original left state
        private final boolean originalLeft;
        // Original right state
        private final boolean originalRight;

        // Initialize the directional gearshift SCM probe
        private DirectionalGearshiftScmProbe(
                BlockEntity blockEntity,
                IDirectControlReceiver control,
                ScmTarget target,
                Vec3 dir,
                boolean forward
        ) {
            this.blockEntity = blockEntity;
            this.control = control;
            this.target = target;
            this.direction = dir;
            this.forward = forward;
            BlockState state = blockEntity.getBlockState();
            this.originalLeft = state.hasProperty(DirectionalGearshiftBlock.LEFT_POWERED)
                    && state.getValue(DirectionalGearshiftBlock.LEFT_POWERED);
            this.originalRight = state.hasProperty(DirectionalGearshiftBlock.RIGHT_POWERED)
                    && state.getValue(DirectionalGearshiftBlock.RIGHT_POWERED);
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return "directional_gearshift_" + (forward ? "forward" : "reverse");
        }

        // Get the directional gearshift SCM probe display name
        @Override
        public String displayName() {
            String name = target.label().isBlank() ? "Directional Gearshift" : target.label();
            return name + (forward ? " (Forward)" : " (Reverse)");
        }

        // Control the group id
        @Override
        public String controlGroupId() {
            return target.stableId();
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Apply the directional gearshift SCM probe
        @Override
        public void apply(double value) {
            boolean active = value > 1.0E-5D;
            control.applyDirectControllerSignal(forward ? "right" : "left",
                    active ? 1.0F : 0.0F);
        }

        // Read the directional gearshift SCM probe
        @Override
        public Reading read() {
            double speed = blockEntity instanceof KineticBlockEntity kinetic
                    ? Math.abs(kinetic.getSpeed()) : 0.0D;
            BlockState state = blockEntity.getBlockState();
            boolean active = state.hasProperty(forward
                    ? DirectionalGearshiftBlock.RIGHT_POWERED
                    : DirectionalGearshiftBlock.LEFT_POWERED)
                    && state.getValue(forward
                    ? DirectionalGearshiftBlock.RIGHT_POWERED
                    : DirectionalGearshiftBlock.LEFT_POWERED);
            return new Reading(speed, active ? Math.max(1.0D, speed) : 0.0D, active);
        }

        // Get the local effect direction
        @Override
        public Vec3 localEffectDirection() {
            return forward ? direction : direction.scale(-1.0D);
        }

        // Get the local effect position
        @Override
        public Vec3 localEffectPosition() {
            return blockEntity.getBlockPos().getCenter();
        }

        // Restore the directional gearshift SCM probe
        @Override
        public void restore() {
            control.applyDirectControllerSignal("left", originalLeft ? 1.0F : 0.0F);
            control.applyDirectControllerSignal("right", originalRight ? 1.0F : 0.0F);
        }
    }

    // Define the wheel control values
    private enum WheelControl {
        LEFT,
        RIGHT,
        BRAKE
    }

    // Handle the wheel mount SCM probe
    private static final class WheelMountScmProbe implements ScmControlProbe {
        // Wheel
        private final WheelMountControlBridge wheel;
        // Wheel mount SCM probe target
        private final ScmTarget target;
        // Wheel mount SCM probe direction
        private final Vec3 direction;
        // Control
        private final WheelControl control;
        // Original left
        private final float originalLeft;
        // Original right
        private final float originalRight;
        // Original brake
        private final float originalBrake;

        // Initialize the wheel mount SCM probe
        private WheelMountScmProbe(
                WheelMountControlBridge wheel,
                ScmTarget target,
                Vec3 dir,
                WheelControl control
        ) {
            this.wheel = wheel;
            this.target = target;
            this.direction = dir;
            this.control = control;
            this.originalLeft = wheel.ct$getLeftOverride();
            this.originalRight = wheel.ct$getRightOverride();
            this.originalBrake = wheel.ct$getBrakeOverride();
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return "wheel_mount_" + control.name().toLowerCase(Locale.ROOT);
        }

        // Get the wheel mount SCM probe display name
        @Override
        public String displayName() {
            String name = target.label().isBlank() ? "Wheel Mount" : target.label();
            return name + " (" + switch (control) {
                case LEFT -> "Left";
                case RIGHT -> "Right";
                case BRAKE -> "Brake";
            } + ")";
        }

        // Control the group id
        @Override
        public String controlGroupId() {
            return target.stableId();
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Apply the wheel mount SCM probe
        @Override
        public void apply(double value) {
            float applied = (float) Mth.clamp(value, 0.0D, 1.0D);
            wheel.ct$setDirectInputs(
                    control == WheelControl.LEFT ? applied : 0.0F,
                    control == WheelControl.RIGHT ? applied : 0.0F,
                    control == WheelControl.BRAKE ? applied : 0.0F);
        }

        // Read the wheel mount SCM probe
        @Override
        public Reading read() {
            double val = switch (control) {
                case LEFT -> wheel.ct$getLeftOverride();
                case RIGHT -> wheel.ct$getRightOverride();
                case BRAKE -> wheel.ct$getBrakeOverride();
            };
            return new Reading(0.0D, val, val > 1.0E-5D);
        }

        // Get the local effect direction
        @Override
        public Vec3 localEffectDirection() {
            return control == WheelControl.RIGHT ? direction.scale(-1.0D) : direction;
        }

        // Get the local effect position
        @Override
        public Vec3 localEffectPosition() {
            return target.blockPosition().getCenter();
        }

        // Restore the wheel mount SCM probe
        @Override
        public void restore() {
            wheel.ct$setDirectInputs(originalLeft, originalRight, originalBrake);
        }
    }

    // Handle the redstone SCM probe
    private static final class RedstoneScmProbe implements ScmControlProbe {
        // Level
        private final Level level;
        // Bound block entity
        private final @Nullable BlockEntity blockEntity;
        // Redstone SCM probe target
        private final ScmTarget target;
        // Redstone SCM probe direction
        private final Vec3 direction;
        // Source id
        private final String sourceId;

        // Initialize the redstone SCM probe
        private RedstoneScmProbe(
                Level level, @Nullable BlockEntity blockEntity,
                ScmTarget target, Vec3 dir
        ) {
            this.level = level;
            this.blockEntity = blockEntity;
            this.target = target;
            this.direction = dir;
            this.sourceId = CONTROL_CHANNEL + ":" + target.stableId();
        }

        // Get the adapter id
        @Override
        public String adapterId() {
            return "redstone_block_control";
        }

        // Get the redstone SCM probe display name
        @Override
        public String displayName() {
            return target.label().isBlank() ? target.blockId() : target.label();
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Apply the redstone SCM probe
        @Override
        public void apply(double control) {
            int signal = Mth.clamp((int) Math.round(control * 15.0D), 0, 15);
            if (target.usesFaceControl()) {
                ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                        level, target.subLevelId(), target.signalPosition(),
                        target.signalFace(), sourceId, signal);
            } else if (!ExternalBlockEntityDirectControlCompat.applyDirectSignal(
                    blockEntity, CONTROL_CHANNEL, (float) Mth.clamp(control, 0.0D, 1.0D))) {
                ContraptionNetworkLinkerSignalBus.setBlockSignal(
                        level, target.subLevelId(), target.blockPosition(), sourceId, signal);
            }
        }

        // Read the redstone SCM probe
        @Override
        public Reading read() {
            double speed = blockEntity instanceof KineticBlockEntity kinetic
                    ? Math.abs(kinetic.getSpeed())
                    : blockEntity == null ? 0.0D
                    : Math.abs(numberValue(invokeNoArg(blockEntity, "getSpeed"), 0.0D));
            int signal = ContraptionNetworkLinkerSignalBus.sourceSignal(
                    level, target.subLevelId(),
                    target.usesFaceControl() ? target.signalPosition() : target.blockPosition(),
                    target.usesFaceControl() ? target.signalFace() : null, sourceId);
            double effect = speed > 1.0E-4D ? speed : signal;
            return new Reading(speed, effect, effect > 1.0E-4D);
        }

        // Get the local effect direction
        @Override
        public Vec3 localEffectDirection() {
            return direction;
        }

        // Get the local effect position
        @Override
        public Vec3 localEffectPosition() {
            return target.blockPosition().getCenter();
        }

        // Restore the redstone SCM probe
        @Override
        public void restore() {
            if (target.usesFaceControl()) {
                ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                        level, target.subLevelId(), target.signalPosition(),
                        target.signalFace(), sourceId, 0);
            } else if (!ExternalBlockEntityDirectControlCompat.applyDirectSignal(
                    blockEntity, CONTROL_CHANNEL, 0.0F)) {
                ContraptionNetworkLinkerSignalBus.setBlockSignal(
                        level, target.subLevelId(), target.blockPosition(), sourceId, 0);
            }
        }
    }

    // Expose the actuator
    private interface Actuator {
        // Get the kind
        String kind();

        // Control the group id
        default String controlGroupId() {
            return "";
        }

        // Check if the mapped actuator can be controlled
        boolean controllable();

        // Check if this is available
        default boolean isAvailable() {
            return true;
        }

        // Check if this requires continuous control
        default boolean requiresContinuousControl() {
            return false;
        }

        // Check if this uses physical calibration
        default boolean usesPhysicalCalibration() {
            return false;
        }

        // Check if this requires observed response
        default boolean requiresObservedResponse() {
            return false;
        }

        // Check whether this actuator is driven by an exact linker action.
        default boolean isFaceActionControl() {
            return false;
        }

        // Check if this is wheel control
        default boolean isWheelControl() {
            return false;
        }

        // Check if this is a kinetic control
        default boolean isKineticControl() {
            return false;
        }

        // Get the kinetic control size
        default int kineticControlSize() {
            return 0;
        }

        // Get the kinetic control domain
        default Set<String> kineticControlDomain() {
            return Set.of();
        }

        // Apply the actuator
        void apply(double control);

        // Apply the calibration
        default void applyCalibration(CalibrationPoint point) {
            apply(point.throttle());
        }

        // Get the calibration points
        default List<CalibrationPoint> calibrationPoints() {
            if (!controllable()) {
                return List.of(new CalibrationPoint(minControl(), maxControl(), 0.0D));
            }
            return throttleSweep(minControl(), maxControl());
        }

        // Return the actuator to neutral
        default void neutralize() {
            applyCalibration(new CalibrationPoint(0.0D, 0.0D, 0.0D));
        }

        // Get the neutral control
        default double neutralControl() {
            return 0.0D;
        }

        // Read the actuator
        Reading read();

        // Get the local force direction
        Vec3 localForceDirection();

        // Get the local force position
        Vec3 localForcePosition();

        // Get the minimum control
        double minControl();

        // Get the maximum control
        double maxControl();

        // Get the theoretical max thrust
        double theoreticalMaxThrust();

        // Restore the actuator
        void restore();

        // Check if the actuator maps its own control range
        default boolean mapsOwnControlRange() {
            return false;
        }

        // Check if this supports control envelope
        default boolean supportsControlEnvelope() {
            return false;
        }

        // Control the demand
        default double controlForDemand(ShipControlMap.PropulsionUnit unit, double normalizedDemand) {
            return calibratedControl(unit, normalizedDemand);
        }

        // Get the expected thrust sign
        default double expectedThrustSign() {
            return -1.0D;
        }

        // Get the live maximum thrust
        default double liveMaximumThrust(ShipControlMap.PropulsionUnit unit) {
            return unit.maxThrust();
        }

        // Get the live maximum speed
        default double liveMaximumSpeed(ShipControlMap.PropulsionUnit unit) {
            return unit.maxSpeed();
        }
    }

    // Handle the abstract actuator
    private abstract static class AbstractActuator implements Actuator {
        // Bound block entity
        protected final BlockEntity blockEntity;
        // Tracks whether abstract actuator is restored
        private boolean restored;

        // Initialize the abstract actuator
        protected AbstractActuator(BlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        // Check if this is available
        @Override
        public final boolean isAvailable() {
            return !blockEntity.isRemoved();
        }

        // Mark the applied
        protected final void markApplied() {
            restored = false;
        }

        // Begin the restore
        protected final boolean beginRestore() {
            if (restored) {
                return false;
            }
            restored = true;
            return true;
        }

        // Get the speed
        protected double speed() {
            if (blockEntity instanceof KineticBlockEntity kinetic) {
                return kinetic.getSpeed();
            }
            return numberValue(invokeNoArg(blockEntity, "getSpeed"), 0.0D);
        }

        // Get the local force position
        @Override
        public Vec3 localForcePosition() {
            return blockEntity.getBlockPos().getCenter();
        }
    }

    // Handle the RCS nozzle actuator
    private static final class RcsNozzleActuator extends AbstractActuator {
        // Thruster
        private final RcsThrusterBlockEntity thruster;
        // Nozzle
        private final Direction nozzle;
        // Source id
        private final String sourceId;

        // Initialize the RCS nozzle actuator
        private RcsNozzleActuator(RcsThrusterBlockEntity thruster, Direction nozzle) {
            super(thruster);
            this.thruster = thruster;
            this.nozzle = nozzle;
            this.sourceId = CONTROL_CHANNEL + ":" + nozzle.getSerializedName();
        }

        // Get the kind
        @Override
        public String kind() {
            return "rcs_nozzle_" + nozzle.getSerializedName();
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return true;
        }

        // Apply the RCS nozzle actuator
        @Override
        public void apply(double control) {
            markApplied();
            thruster.setControllerThrottle(
                    nozzle, sourceId, (float) Mth.clamp(control, 0.0D, 1.0D));
        }

        // Read the RCS nozzle actuator
        @Override
        public Reading read() {
            return new Reading(
                    speed(), thruster.getNozzleThrust(nozzle), thruster.isNozzleActive(nozzle));
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            return thruster.getLocalForceDirection(nozzle);
        }

        // Get the local force position
        @Override
        public Vec3 localForcePosition() {
            return thruster.getLocalForcePosition(nozzle);
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return thruster.getMaxNozzleThrust();
        }

        // Check if the actuator maps its own control range
        @Override
        public boolean mapsOwnControlRange() {
            return true;
        }

        // Get the live maximum thrust
        @Override
        public double liveMaximumThrust(ShipControlMap.PropulsionUnit unit) {
            return thruster.getMaxNozzleThrust();
        }

        // Get the live maximum speed
        @Override
        public double liveMaximumSpeed(ShipControlMap.PropulsionUnit unit) {
            return Math.abs(thruster.getSpeed());
        }

        // Get the expected thrust sign
        @Override
        public double expectedThrustSign() {
            return 1.0D;
        }

        // Restore the RCS nozzle actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            thruster.clearControllerThrottle(nozzle, sourceId);
        }
    }

    // Handle the direct actuator
    private static final class DirectActuator extends AbstractActuator {
        // Propeller
        private final BlockEntityPropeller propeller;
        // Receiver
        private final IDirectControlReceiver receiver;
        // Original mode
        private final @Nullable ThrusterBlockEntity.ControlMode originalMode;
        // Original computer throttle
        private final float originalComputerThrottle;

        // Initialize the direct actuator
        private DirectActuator(BlockEntity blockEntity, BlockEntityPropeller propeller,
                               IDirectControlReceiver receiver) {
            super(blockEntity);
            this.propeller = propeller;
            this.receiver = receiver;
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
                originalMode = thruster.getControlMode();
                originalComputerThrottle = thruster.getComputerThrottle();
            } else {
                originalMode = null;
                originalComputerThrottle = 0.0F;
            }
        }

        // Get the kind
        @Override
        public String kind() {
            return DIRECT_ACTUATOR_ADAPTER;
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return true;
        }

        // Apply the direct actuator
        @Override
        public void apply(double control) {
            markApplied();
            double clamped = Mth.clamp(control, 0.0D, 1.0D);
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
                float modulation = (float) envelopeModulation(clamped);
                thruster.applyShipControlEnvelope(CONTROL_CHANNEL, 0.0F,
                        modulation, modulation);
            } else {
                receiver.applyDirectControllerSignal(CONTROL_CHANNEL, (float) clamped);
            }
        }

        // Apply the calibration
        @Override
        public void applyCalibration(CalibrationPoint point) {
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
                markApplied();
                thruster.applyShipControlEnvelope(CONTROL_CHANNEL,
                        (float) point.minControl(), (float) point.maxControl(),
                        (float) point.throttle());
                return;
            }
            apply(point.throttle());
        }

        // Get the calibration points
        @Override
        public List<CalibrationPoint> calibrationPoints() {
            return blockEntity instanceof ThrusterBlockEntity
                    ? envelopeSweep()
                    : throttleSweep(minControl(), maxControl());
        }

        // Read the direct actuator
        @Override
        public Reading read() {
            return new Reading(speed(), propeller.getScaledThrust(), propeller.isActive());
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            Vec3 dir = blockEntity instanceof ThrusterBlockEntity thruster
                    ? thruster.getLocalThrustDirection()
                    : Vec3.atLowerCornerOf(propeller.getBlockDirection().getNormal());
            return dir.scale(-1.0D);
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
                return thruster.getAvailableMaximumScaledThrust();
            }
            return Math.max(Math.abs(propeller.getScaledThrust()),
                    reflectedThrustLimit(blockEntity));
        }

        // Get the live maximum thrust
        @Override
        public double liveMaximumThrust(ShipControlMap.PropulsionUnit unit) {
            if (blockEntity instanceof ThrusterBlockEntity thruster) {
                return thruster.getAvailableMaximumScaledThrust();
            }
            return Math.max(unit.maxThrust(), Math.max(
                    Math.abs(propeller.getScaledThrust()),
                    reflectedThrustLimit(blockEntity)));
        }

        // Check if the actuator maps its own control range
        @Override
        public boolean mapsOwnControlRange() {
            return true;
        }

        // Check if this supports control envelope
        @Override
        public boolean supportsControlEnvelope() {
            return blockEntity instanceof ThrusterBlockEntity;
        }

        // Control the demand
        @Override
        public double controlForDemand(ShipControlMap.PropulsionUnit unit, double normalizedDemand) {
            return supportsControlEnvelope()
                    ? calibratedEnvelopeControl(unit, normalizedDemand)
                    : calibratedControl(unit, normalizedDemand);
        }

        // Restore the direct actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            if (blockEntity instanceof ThrusterBlockEntity thruster && originalMode != null) {
                thruster.clearShipControlEnvelope(CONTROL_CHANNEL);
                thruster.setThrottle(originalComputerThrottle);
                thruster.setControlMode(originalMode);
            } else {
                receiver.applyDirectControllerSignal(CONTROL_CHANNEL, 0.0F);
            }
        }
    }

    // Handle the direct receiver actuator
    private static final class DirectReceiverActuator extends AbstractActuator {
        // Receiver
        private final IDirectControlReceiver receiver;
        // Resolved thrust reader method
        private final Method thrustReader;
        // Resolved direction reader method
        private final Method directionReader;
        // Original direct control state
        private final boolean originalDirectControl;
        // Original direct throttle
        private final float originalDirectThrottle;

        // Initialize the direct receiver actuator
        private DirectReceiverActuator(
                BlockEntity blockEntity,
                IDirectControlReceiver receiver,
                Method thrustReader,
                Method directionReader
        ) {
            super(blockEntity);
            this.receiver = receiver;
            this.thrustReader = thrustReader;
            this.directionReader = directionReader;
            if (blockEntity instanceof PropulsionDirectThrottleAccess access) {
                originalDirectControl =
                        access.createThrusters$hasDirectThrottle();
                originalDirectThrottle =
                        access.createThrusters$getDirectThrottle();
            } else {
                originalDirectControl = false;
                originalDirectThrottle = 0.0F;
            }
        }

        // Get the kind
        @Override
        public String kind() {
            return blockEntity.getClass().getName().startsWith(
                    "dev.propulsionteam.propulsionsimulated.")
                    ? "create_propulsion_direct" : DIRECT_ACTUATOR_ADAPTER;
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return true;
        }

        // Apply the direct receiver actuator
        @Override
        public void apply(double control) {
            markApplied();
            receiver.applyDirectControllerSignal(
                    CONTROL_CHANNEL,
                    (float) Mth.clamp(control, 0.0D, 1.0D));
        }

        // Read the direct receiver actuator
        @Override
        public Reading read() {
            double thrust = numberValue(
                    invoke(blockEntity, thrustReader), 0.0D);
            return new Reading(
                    speed(), thrust,
                    booleanValue(firstResult(
                            blockEntity, "isActive", "isRunning"),
                            Math.abs(thrust) > 1.0E-6D));
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            Vec3 dir = vectorValue(
                    invoke(blockEntity, directionReader));
            return dir == null ? Vec3.ZERO : dir;
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return reflectedThrustLimit(blockEntity);
        }

        // Get the expected thrust sign
        @Override
        public double expectedThrustSign() {
            return 1.0D;
        }

        // Check if the actuator maps its own control range
        @Override
        public boolean mapsOwnControlRange() {
            return true;
        }

        // Control the demand
        @Override
        public double controlForDemand(
                ShipControlMap.PropulsionUnit unit,
                double normalizedDemand
        ) {
            return calibratedControl(unit, normalizedDemand);
        }

        // Restore the direct receiver actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            if (blockEntity instanceof PropulsionDirectThrottleAccess access) {
                if (originalDirectControl) {
                    access.createThrusters$setDirectThrottle(
                            originalDirectThrottle);
                } else {
                    access.createThrusters$clearDirectThrottle();
                }
                return;
            }
            receiver.applyDirectControllerSignal(CONTROL_CHANNEL, 0.0F);
        }
    }

    // Handle the passive propeller actuator
    private static final class PassivePropellerActuator extends AbstractActuator {
        // Propeller
        private final BlockEntityPropeller propeller;

        // Initialize the passive propeller actuator
        private PassivePropellerActuator(BlockEntity blockEntity, BlockEntityPropeller propeller) {
            super(blockEntity);
            this.propeller = propeller;
        }

        // Get the kind
        @Override
        public String kind() {
            return "passive_propeller";
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return false;
        }

        // Apply the passive propeller actuator
        @Override
        public void apply(double control) {
        }

        // Read the passive propeller actuator
        @Override
        public Reading read() {
            return new Reading(speed(), propeller.getScaledThrust(), propeller.isActive());
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            return Vec3.atLowerCornerOf(propeller.getBlockDirection().getNormal()).scale(-1.0D);
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 0.0D;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return Math.max(Math.abs(propeller.getScaledThrust()), reflectedThrustLimit(blockEntity));
        }

        // Restore the passive propeller actuator
        @Override
        public void restore() {
        }
    }

    // Handle the kinetic propeller actuator
    private static final class KineticPropellerActuator extends AbstractActuator {
        private static final double DEFAULT_TEST_SPEED = 256.0D;
        // Kinetic
        private final KineticBlockEntity kinetic;
        // Propeller
        private final BlockEntityPropeller propeller;
        // Original speed
        private final float originalSpeed;
        // Test speed
        private final double testSpeed;

        // Initialize the kinetic propeller actuator
        private KineticPropellerActuator(KineticBlockEntity kinetic, BlockEntityPropeller propeller) {
            super(kinetic);
            this.kinetic = kinetic;
            this.propeller = propeller;
            this.originalSpeed = kinetic.getSpeed();
            this.testSpeed = Math.max(DEFAULT_TEST_SPEED, Math.abs(originalSpeed));
        }

        // Get the kind
        @Override
        public String kind() {
            return "kinetic_speed";
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return true;
        }

        // Apply the kinetic propeller actuator
        @Override
        public void apply(double control) {
            markApplied();
            double dir = originalSpeed < 0.0F ? -1.0D : 1.0D;
            kinetic.setSpeed((float) (dir * Mth.clamp(control, 0.0D, 1.0D) * testSpeed));
            kinetic.setChanged();
        }

        // Read the kinetic propeller actuator
        @Override
        public Reading read() {
            return new Reading(kinetic.getSpeed(), propeller.getScaledThrust(), propeller.isActive());
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            return Vec3.atLowerCornerOf(propeller.getBlockDirection().getNormal()).scale(-1.0D);
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 1.0D;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return Math.max(Math.abs(propeller.getScaledThrust()), reflectedThrustLimit(kinetic));
        }

        // Restore the kinetic propeller actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            kinetic.setSpeed(originalSpeed);
            kinetic.setChanged();
        }
    }

    // Handle the reflective actuator
    private static final class ReflectiveActuator extends AbstractActuator {
        // Propeller
        private final @Nullable BlockEntityPropeller propeller;
        // Resolved throttle input method
        private final Method throttleInput;
        // Original throttle
        private final double originalThrottle;
        // Resolved minimum setter method
        private final @Nullable Method minimumSetter;
        // Resolved maximum setter method
        private final @Nullable Method maximumSetter;
        // Original minimum
        private final double originalMinimum;
        // Original maximum
        private final double originalMaximum;

        // Initialize the reflective actuator
        private ReflectiveActuator(BlockEntity blockEntity, @Nullable BlockEntityPropeller propeller,
                                   Method throttleInput) {
            super(blockEntity);
            this.propeller = propeller;
            this.throttleInput = throttleInput;
            this.originalThrottle = numberValue(firstResult(blockEntity,
                    "getThrottle", "getPower"), 0.0D);
            this.minimumSetter = firstMethod(blockEntity.getClass(), 1,
                    "setMinThrottle", "setMinimumThrottle", "setMinPower", "setMinimumPower");
            this.maximumSetter = firstMethod(blockEntity.getClass(), 1,
                    "setMaxThrottle", "setMaximumThrottle", "setMaxPower", "setMaximumPower",
                    "setThrustLimit");
            this.originalMinimum = Mth.clamp(numberValue(firstResult(blockEntity,
                    "getMinThrottle", "getMinimumThrottle", "getMinPower", "getMinimumPower"),
                    0.0D), 0.0D, 1.0D);
            this.originalMaximum = Mth.clamp(numberValue(firstResult(blockEntity,
                    "getMaxThrottle", "getMaximumThrottle", "getMaxPower",
                    "getMaximumPower", "getThrustLimit"), 1.0D), originalMinimum, 1.0D);
        }

        // Get the kind
        @Override
        public String kind() {
            if (supportsEnvelope()) {
                return ENVELOPE_ACTUATOR_ADAPTER;
            }
            return blockEntity.getClass().getName().startsWith(
                    "dev.propulsionteam.propulsionsimulated.")
                    ? "create_propulsion_throttle" : "throttle_input";
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return true;
        }

        // Apply the reflective actuator
        @Override
        public void apply(double control) {
            markApplied();
            double clamped = Mth.clamp(control, 0.0D, 1.0D);
            if (supportsEnvelope()) {
                double modulation = envelopeModulation(clamped);
                setEnvelope(0.0D, modulation);
                invokeNumber(blockEntity, throttleInput, modulation);
            } else {
                invokeNumber(blockEntity, throttleInput, clamped);
            }
        }

        // Apply the calibration
        @Override
        public void applyCalibration(CalibrationPoint point) {
            if (!supportsEnvelope()) {
                apply(point.throttle());
                return;
            }
            markApplied();
            setEnvelope(point.minControl(), point.maxControl());
            invokeNumber(blockEntity, throttleInput, point.throttle());
        }

        // Get the calibration points
        @Override
        public List<CalibrationPoint> calibrationPoints() {
            return supportsEnvelope()
                    ? envelopeSweep()
                    : throttleSweep(originalMinimum, originalMaximum);
        }

        // Read the reflective actuator
        @Override
        public Reading read() {
            double thrust = propeller != null ? propeller.getScaledThrust()
                    : numberValue(firstResult(blockEntity,
                    "getCurrentThrust", "getThrust", "getPower"), 0.0D);
            boolean active = propeller != null ? propeller.isActive()
                    : booleanValue(firstResult(blockEntity, "isActive", "isRunning"), Math.abs(thrust) > 1.0E-6D);
            return new Reading(speed(), thrust, active);
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            Object res = firstResult(blockEntity,
                    "getThrustDirectionLocal", "getLocalThrustDirection", "getThrustDirection");
            Vec3 dir = vectorValue(res);
            if (dir != null) {
                return dir;
            }
            if (propeller != null) {
                return Vec3.atLowerCornerOf(propeller.getBlockDirection().getNormal()).scale(-1.0D);
            }
            return Vec3.ZERO;
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return originalMinimum;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return originalMaximum;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return reflectedThrustLimit(blockEntity);
        }

        // Get the expected thrust sign
        @Override
        public double expectedThrustSign() {
            return propeller == null ? 1.0D : -1.0D;
        }

        // Check if the actuator maps its own control range
        @Override
        public boolean mapsOwnControlRange() {
            return true;
        }

        // Check if this supports control envelope
        @Override
        public boolean supportsControlEnvelope() {
            return supportsEnvelope();
        }

        // Control the demand
        @Override
        public double controlForDemand(ShipControlMap.PropulsionUnit unit, double normalizedDemand) {
            return supportsControlEnvelope()
                    ? calibratedEnvelopeControl(unit, normalizedDemand)
                    : calibratedControl(unit, normalizedDemand);
        }

        // Restore the reflective actuator
        @Override
        public void restore() {
            if (!beginRestore()) {
                return;
            }
            invokeNumber(blockEntity, throttleInput, originalThrottle);
            if (supportsEnvelope()) {
                setEnvelope(originalMinimum, originalMaximum);
            }
        }

        // Check if this supports envelope
        private boolean supportsEnvelope() {
            return minimumSetter != null && maximumSetter != null;
        }

        // Set the envelope
        private void setEnvelope(double minimum, double maximum) {
            if (!supportsEnvelope()) {
                return;
            }
            double clampedMinimum = Mth.clamp(minimum, 0.0D, 1.0D);
            double clampedMaximum = Mth.clamp(Math.max(clampedMinimum, maximum), 0.0D, 1.0D);
            invokeNumber(blockEntity, maximumSetter, 1.0D);
            invokeNumber(blockEntity, minimumSetter, clampedMinimum);
            invokeNumber(blockEntity, maximumSetter, clampedMaximum);
        }

    }

    // Handle the reflective passive actuator
    private static final class ReflectivePassiveActuator extends AbstractActuator {
        // Initialize the reflective passive actuator
        private ReflectivePassiveActuator(BlockEntity blockEntity) {
            super(blockEntity);
        }

        // Get the kind
        @Override
        public String kind() {
            return "reflective_passive";
        }

        // Check if the mapped actuator can be controlled
        @Override
        public boolean controllable() {
            return false;
        }

        // Apply the reflective passive actuator
        @Override
        public void apply(double control) {
        }

        // Read the reflective passive actuator
        @Override
        public Reading read() {
            double thrust = numberValue(firstResult(blockEntity,
                    "getCurrentThrust", "getThrust", "getPower"), 0.0D);
            return new Reading(speed(), thrust,
                    booleanValue(firstResult(blockEntity, "isActive", "isRunning"),
                            Math.abs(thrust) > 1.0E-6D));
        }

        // Get the local force direction
        @Override
        public Vec3 localForceDirection() {
            Vec3 dir = vectorValue(firstResult(blockEntity,
                    "getThrustDirectionLocal", "getLocalThrustDirection", "getThrustDirection"));
            return dir == null ? Vec3.ZERO : dir;
        }

        // Get the minimum control
        @Override
        public double minControl() {
            return 0.0D;
        }

        // Get the maximum control
        @Override
        public double maxControl() {
            return 0.0D;
        }

        // Get the theoretical max thrust
        @Override
        public double theoreticalMaxThrust() {
            return reflectedThrustLimit(blockEntity);
        }

        // Get the expected thrust sign
        @Override
        public double expectedThrustSign() {
            return 1.0D;
        }

        // Restore the reflective passive actuator
        @Override
        public void restore() {
        }
    }

    // Store the bearing candidate
    private record BearingCandidate(
            UUID hostSubLevelId,
            BlockPos blockPosition,
            String blockId,
            BearingActuator actuator,
            List<UUID> childSubLevelIds
    ) {
        // Initialize the bearing candidate
        private BearingCandidate {
            blockPosition = blockPosition.immutable();
            childSubLevelIds = List.copyOf(childSubLevelIds);
        }
    }

    // Handle the aerodynamic surface calibration
    private static final class AerodynamicSurfaceCalibration {
        // Sub-level id
        private final UUID subLevelId;
        // Block position
        private final BlockPos blockPosition;
        // Block id
        private final String blockId;
        // Local normal
        private final Vec3 localNormal;
        // Parallel drag scalar
        private final double parallelDragScalar;
        // Directionless drag scalar
        private final double directionlessDragScalar;
        // Lift scalar
        private final double liftScalar;

        // Initialize the aerodynamic surface calibration
        private AerodynamicSurfaceCalibration(
                UUID subLevelId,
                BlockPos blockPosition,
                String blockId,
                Vec3 localNormal,
                double parallelDragScalar,
                double directionlessDragScalar,
                double liftScalar
        ) {
            this.subLevelId = subLevelId;
            this.blockPosition = blockPosition.immutable();
            this.blockId = blockId;
            this.localNormal = normalize(localNormal, Vec3.ZERO);
            this.parallelDragScalar = Math.max(0.0D, finite(parallelDragScalar));
            this.directionlessDragScalar =
                    Math.max(0.0D, finite(directionlessDragScalar));
            this.liftScalar = Math.max(0.0D, finite(liftScalar));
        }

        // Convert the aerodynamic surface calibration to map surface
        private ShipControlMap.AerodynamicSurface toMapSurface(
                int surfaceIndex,
                Vec3 rootPosition,
                Vec3 rootNormal
        ) {
            return new ShipControlMap.AerodynamicSurface(
                    surfaceIndex, subLevelId, blockPosition, blockId,
                    rootPosition, rootNormal, parallelDragScalar,
                    directionlessDragScalar, liftScalar);
        }
    }

    // Handle the bearing calibration unit
    private static final class BearingCalibrationUnit {
        // Host sub-level id
        private final UUID hostSubLevelId;
        // Block position
        private final BlockPos blockPosition;
        // Block id
        private final String blockId;
        // Actuator
        private final BearingActuator actuator;
        // Tracked child sub-level ids
        private final List<UUID> childSubLevelIds;
        // Tracked member unit indices
        private final List<Integer> memberUnitIndices;
        // Tracked aerodynamic surfaces
        private final List<AerodynamicSurfaceCalibration> aerodynamicSurfaces;
        // Tracked poses
        private final List<ShipBearingPlanner.Pose> poses;
        // Output poses
        private final List<ShipBearingPlanner.Pose> outputPoses;
        // Tracked pose samples
        private final List<BearingPoseSample> poseSamples;
        // Tracked reused poses
        private final List<ShipControlMap.BearingPose> reusedPoses;
        // Tracks whether calibration required is set
        private final boolean calibrationRequired;
        // Tracks whether interpolate output is set
        private final boolean interpolateOutput;

        // Initialize the bearing calibration unit
        private BearingCalibrationUnit(
                UUID hostSubLevelId,
                BlockPos blockPosition,
                String blockId,
                BearingActuator actuator,
                List<UUID> childSubLevelIds,
                List<Integer> memberUnitIndices,
                List<AerodynamicSurfaceCalibration> aerodynamicSurfaces,
                List<ShipBearingPlanner.Pose> poses,
                List<ShipBearingPlanner.Pose> outputPoses,
                boolean interpolateOutput,
                @Nullable BearingReuse reuse
        ) {
            this.hostSubLevelId = hostSubLevelId;
            this.blockPosition = blockPosition.immutable();
            this.blockId = blockId;
            this.actuator = actuator;
            this.childSubLevelIds = List.copyOf(childSubLevelIds);
            this.memberUnitIndices = List.copyOf(memberUnitIndices);
            this.aerodynamicSurfaces = List.copyOf(aerodynamicSurfaces);
            this.poses = List.copyOf(poses);
            this.outputPoses = List.copyOf(outputPoses);
            this.poseSamples = poses.stream().map(BearingPoseSample::new).toList();
            this.calibrationRequired = reuse == null;
            this.interpolateOutput = interpolateOutput;
            this.reusedPoses = reuse == null
                    ? List.of() : remapBearingPoses(reuse.bearing(), reuse.unitIndexRemap());
        }

        // Convert the bearing calibration unit to map unit
        private ShipControlMap.BearingUnit toMapUnit(int idx) {
            return new ShipControlMap.BearingUnit(
                    idx, hostSubLevelId, blockPosition, blockId, actuator.kind(),
                    childSubLevelIds, actuator.minX(), actuator.maxX(),
                    actuator.minZ(), actuator.maxZ(),
                    calibrationRequired
                            ? calibratedOutputPoses()
                            : reusedPoses);
        }

        // Get the calibrated output poses
        private List<ShipControlMap.BearingPose> calibratedOutputPoses() {
            List<BearingPoseSample> validSamples = poseSamples.stream()
                    .filter(BearingPoseSample::valid).toList();
            if (!interpolateOutput || validSamples.isEmpty()) {
                return validSamples.stream().map(BearingPoseSample::toMapPose).toList();
            }
            return outputPoses.stream()
                    .map(pose -> interpolatePose(pose, validSamples))
                    .toList();
        }

        // Get the interpolate pose
        private static ShipControlMap.BearingPose interpolatePose(
                ShipBearingPlanner.Pose target,
                List<BearingPoseSample> samples
        ) {
            // ------------------------------------EXACT POSE------------------------------------
            BearingPoseSample exact = samples.stream()
                    .filter(sample -> Math.abs(sample.angleX - target.angleX()) <= 1.0E-6D
                            && Math.abs(sample.angleZ - target.angleZ()) <= 1.0E-6D)
                    .findFirst().orElse(null);
            if (exact != null) {
                return exact.toMapPose();
            }
            List<BearingPoseSample> nearest = samples.stream()
                    .sorted(Comparator.comparingDouble(sample -> poseDistanceSquared(
                            target, sample)))
                    .limit(4)
                    .toList();
            Map<BearingPoseSample, Double> weights = new LinkedHashMap<>();
            double totalWeight = 0.0D;
            for (BearingPoseSample sample : nearest) {
                double weight = 1.0D / Math.max(1.0E-6D,
                        Math.sqrt(poseDistanceSquared(target, sample)));
                weights.put(sample, weight);
                totalWeight += weight;
            }
            if (totalWeight <= 1.0E-9D) {
                return nearest.getFirst().toMapPose();
            }

            // ------------------------------------FORCE RESPONSES------------------------------------
            Set<Integer> responseIndices = new LinkedHashSet<>();
            nearest.forEach(sample -> responseIndices.addAll(sample.responses.keySet()));
            List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
            for (int unitIndex : responseIndices) {
                Vec3 pos = Vec3.ZERO;
                Vec3 dir = Vec3.ZERO;
                double thrust = 0.0D;
                double responseWeight = 0.0D;
                for (Map.Entry<BearingPoseSample, Double> entry : weights.entrySet()) {
                    ShipControlMap.BearingResponse resp =
                            entry.getKey().responses.get(unitIndex);
                    if (resp == null) {
                        continue;
                    }
                    double weight = entry.getValue();
                    pos = pos.add(resp.rootPosition().scale(weight));
                    dir = dir.add(resp.forceDirection().scale(weight));
                    thrust += resp.maxThrust() * weight;
                    responseWeight += weight;
                }
                if (responseWeight > 1.0E-9D) {
                    responses.add(new ShipControlMap.BearingResponse(
                            unitIndex, pos.scale(1.0D / responseWeight),
                            normalize(dir, Vec3.ZERO), thrust / responseWeight));
                }
            }

            Set<Integer> surfaceIndices = new LinkedHashSet<>();
            nearest.forEach(sample -> sample.aerodynamicSurfaces.forEach(surface ->
                    surfaceIndices.add(surface.surfaceIndex())));
            List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
            for (int surfaceIndex : surfaceIndices) {
                ShipControlMap.AerodynamicSurface template = null;
                Vec3 pos = Vec3.ZERO;
                Vec3 normal = Vec3.ZERO;
                double surfaceWeight = 0.0D;
                for (Map.Entry<BearingPoseSample, Double> entry : weights.entrySet()) {
                    ShipControlMap.AerodynamicSurface surface = entry.getKey()
                            .aerodynamicSurfaces.stream()
                            .filter(candidate -> candidate.surfaceIndex() == surfaceIndex)
                            .findFirst().orElse(null);
                    if (surface == null) {
                        continue;
                    }
                    template = surface;
                    double weight = entry.getValue();
                    pos = pos.add(surface.rootPosition().scale(weight));
                    normal = normal.add(surface.normal().scale(weight));
                    surfaceWeight += weight;
                }
                if (template != null && surfaceWeight > 1.0E-9D) {
                    surfaces.add(new ShipControlMap.AerodynamicSurface(
                            template.surfaceIndex(), template.subLevelId(),
                            template.blockPosition(), template.blockId(),
                            pos.scale(1.0D / surfaceWeight),
                            normalize(normal, template.normal()),
                            template.parallelDragScalar(),
                            template.directionlessDragScalar(), template.liftScalar()));
                }
            }
            double aerodynamicForce = 0.0D;
            double aerodynamicTorque = 0.0D;
            for (Map.Entry<BearingPoseSample, Double> entry : weights.entrySet()) {
                double normalizedWeight = entry.getValue() / totalWeight;
                aerodynamicForce += entry.getKey().maxAerodynamicForce * normalizedWeight;
                aerodynamicTorque += entry.getKey().maxAerodynamicTorque * normalizedWeight;
            }
            return new ShipControlMap.BearingPose(
                    target.angleX(), target.angleZ(), responses, surfaces,
                    aerodynamicForce, aerodynamicTorque);
        }

        // Get the pose distance squared
        private static double poseDistanceSquared(
                ShipBearingPlanner.Pose target, BearingPoseSample sample
        ) {
            double x = target.angleX() - sample.angleX;
            double z = target.angleZ() - sample.angleZ;
            return x * x + z * z;
        }

        // Remap the bearing poses
        private static List<ShipControlMap.BearingPose> remapBearingPoses(
                ShipControlMap.BearingUnit bearing,
                Map<Integer, Integer> unitIndexRemap
        ) {
            return bearing.poses().stream().map(pose -> new ShipControlMap.BearingPose(
                    pose.angleX(), pose.angleZ(),
                    pose.responses().stream().map(resp ->
                            new ShipControlMap.BearingResponse(
                                    unitIndexRemap.get(resp.propulsionUnitIndex()),
                                    resp.rootPosition(), resp.forceDirection(),
                                    resp.maxThrust())).toList(),
                    pose.aerodynamicSurfaces(), pose.maxAerodynamicForce(),
                    pose.maxAerodynamicTorque())).toList();
        }
    }

    // Handle the bearing pose sample
    private static final class BearingPoseSample {
        // Current angle x
        private double angleX;
        // Current angle z
        private double angleZ;
        // Tracked responses
        private final Map<Integer, ShipControlMap.BearingResponse> responses = new LinkedHashMap<>();
        // Tracked aerodynamic surfaces
        private List<ShipControlMap.AerodynamicSurface> aerodynamicSurfaces = List.of();
        // Maximum aerodynamic force
        private double maxAerodynamicForce;
        // Maximum aerodynamic torque
        private double maxAerodynamicTorque;
        // Tracks whether aerodynamics recorded is set
        private boolean aerodynamicsRecorded;
        // Tracks whether bearing pose sample is valid
        private boolean valid = true;

        // Initialize the bearing pose sample
        private BearingPoseSample(ShipBearingPlanner.Pose requestedPose) {
            angleX = requestedPose.angleX();
            angleZ = requestedPose.angleZ();
        }

        // Record the bearing pose sample
        private void record(
                ShipBearingPlanner.Pose actualPose,
                int unitIndex,
                Vec3 rootPosition,
                Vec3 forceDirection,
                double thrust
        ) {
            angleX = actualPose.angleX();
            angleZ = actualPose.angleZ();
            double maximum = Math.max(0.0D, finite(thrust));
            ShipControlMap.BearingResponse prev = responses.get(unitIndex);
            if (prev == null || maximum >= prev.maxThrust()) {
                responses.put(unitIndex, new ShipControlMap.BearingResponse(
                        unitIndex, rootPosition, forceDirection, maximum));
            }
        }

        // Record the aerodynamics
        private void recordAerodynamics(
                ShipBearingPlanner.Pose actualPose,
                List<ShipControlMap.AerodynamicSurface> surfaces,
                double maximumForce,
                double maximumTorque
        ) {
            angleX = actualPose.angleX();
            angleZ = actualPose.angleZ();
            aerodynamicSurfaces = List.copyOf(surfaces);
            maxAerodynamicForce = Math.max(0.0D, finite(maximumForce));
            maxAerodynamicTorque = Math.max(0.0D, finite(maximumTorque));
            aerodynamicsRecorded = true;
        }

        // Invalidate the bearing pose sample
        private void invalidate() {
            valid = false;
            responses.clear();
            aerodynamicSurfaces = List.of();
            maxAerodynamicForce = 0.0D;
            maxAerodynamicTorque = 0.0D;
            aerodynamicsRecorded = false;
        }

        // Check if this is valid
        private boolean valid() {
            return valid;
        }

        // Convert the bearing pose sample to map pose
        private ShipControlMap.BearingPose toMapPose() {
            return new ShipControlMap.BearingPose(
                    angleX, angleZ, List.copyOf(responses.values()),
                    aerodynamicSurfaces, maxAerodynamicForce, maxAerodynamicTorque);
        }
    }

    // Handle the calibration unit
    private static final class CalibrationUnit {
        // Sub-level id
        private final UUID subLevelId;
        // Block position
        private final BlockPos blockPosition;
        // Block id
        private final String blockId;
        // Current root position
        private Vec3 rootPosition;
        // Current force direction
        private Vec3 forceDirection;
        // Actuator
        private final Actuator actuator;
        // Tracked calibration points
        private List<CalibrationPoint> calibrationPoints;
        // Tracked samples
        private final List<ShipControlMap.CalibrationSample> samples = new ArrayList<>();
        // Tracks whether calibration required is set
        private final boolean calibrationRequired;
        // Reusable maximum thrust
        private final double reusableMaximumThrust;
        // Reusable maximum speed
        private final double reusableMaximumSpeed;
        // A direct profile binding deliberately skips physical probing. Its
        // adapter's declared live capacity is therefore the allocator input,
        // even when that adapter normally requests observed calibration data.
        private final boolean directLiveCapacity;
        // V2 representative index
        private int v2RepresentativeIndex = -1;
        // Tracks whether V2 representative is set
        private boolean v2Representative;
        // Current strongest physical response
        private double strongestPhysicalResponse;

        // Initialize the calibration unit
        private CalibrationUnit(UUID subLevelId, BlockPos blockPosition, String blockId,
                                Vec3 rootPosition, Vec3 forceDirection, Actuator actuator,
                                @Nullable ShipControlMap.PropulsionUnit reusable) {
            this(subLevelId, blockPosition, blockId, rootPosition, forceDirection,
                    actuator, reusable, false, false);
        }

        // Initialize the calibration unit
        private CalibrationUnit(UUID subLevelId, BlockPos blockPosition, String blockId,
                                Vec3 rootPosition, Vec3 forceDirection, Actuator actuator,
                                @Nullable ShipControlMap.PropulsionUnit reusable,
                                boolean preserveCompatibleReusableCapacity) {
            this(subLevelId, blockPosition, blockId, rootPosition, forceDirection, actuator,
                    reusable, preserveCompatibleReusableCapacity, false);
        }

        // Initialize the calibration unit
        private CalibrationUnit(UUID subLevelId, BlockPos blockPosition, String blockId,
                                Vec3 rootPosition, Vec3 forceDirection, Actuator actuator,
                                @Nullable ShipControlMap.PropulsionUnit reusable,
                                boolean preserveCompatibleReusableCapacity,
                                boolean directLiveCapacity) {
            this.subLevelId = subLevelId;
            this.blockPosition = blockPosition.immutable();
            this.blockId = blockId;
            this.rootPosition = rootPosition;
            this.forceDirection = forceDirection;
            this.actuator = actuator;
            this.directLiveCapacity = directLiveCapacity;
            this.calibrationPoints = List.copyOf(actuator.calibrationPoints());
            this.calibrationRequired = !canReuseCalibration(reusable, actuator, calibrationPoints);
            boolean compatibleReusable = canReuseCalibration(reusable, actuator);
            if (reusable != null && (!calibrationRequired
                    || (preserveCompatibleReusableCapacity && compatibleReusable))) {
                samples.addAll(reusable.samples());
            }
            if (preserveCompatibleReusableCapacity && compatibleReusable
                    && reusable != null) {
                reusableMaximumThrust = reusable.maxThrust();
                reusableMaximumSpeed = reusable.maxSpeed();
            } else {
                reusableMaximumThrust = 0.0D;
                reusableMaximumSpeed = 0.0D;
            }
        }

        // Handle key
        private PropulsionUnitKey key() {
            return new PropulsionUnitKey(
                    subLevelId, blockPosition, blockId, actuator.kind());
        }

        // Convert the calibration unit to map unit
        private ShipControlMap.PropulsionUnit toMapUnit(int idx) {
            double minThrust = samples.stream().mapToDouble(sample -> Math.abs(sample.thrust()))
                    .filter(val -> val > 1.0E-6D).min().orElse(0.0D);
            double maxThrust = samples.stream().mapToDouble(sample -> Math.abs(sample.thrust()))
                    .max().orElse(0.0D);
            maxThrust = Math.max(maxThrust, reusableMaximumThrust);
            if (maxThrust <= 1.0E-6D
                    && (directLiveCapacity || !actuator.requiresObservedResponse())) {
                maxThrust = actuator.theoreticalMaxThrust();
            }
            double maxSpeed = Math.max(reusableMaximumSpeed,
                    samples.stream().mapToDouble(sample -> Math.abs(sample.speed()))
                            .max().orElse(0.0D));
            double observedSign = samples.stream()
                    .max(Comparator.comparingDouble(sample -> Math.abs(sample.thrust())))
                    .map(sample -> Math.signum(sample.thrust()))
                    .orElse(0.0D);
            Vec3 calibratedDirection = observedSign != 0.0D
                    && observedSign * actuator.expectedThrustSign() < 0.0D
                    ? forceDirection.scale(-1.0D) : forceDirection;
            return new ShipControlMap.PropulsionUnit(idx, subLevelId, blockPosition, blockId,
                    actuator.kind(), actuator.controllable(), rootPosition, calibratedDirection,
                    actuator.minControl(), actuator.maxControl(), minThrust, maxThrust, maxSpeed, samples);
        }

        // Record the physical response
        private Reading recordPhysicalResponse(
                double speed,
                ConstraintResponse resp,
                Vec3 centerOfMass
        ) {
            Vec3 force = resp.force();
            Vec3 torque = resp.torque();
            double forceMagnitude = force.length();
            double leverScale = Math.max(1.0D, rootPosition.distanceTo(centerOfMass));
            double equivalentMagnitude = Math.max(
                    forceMagnitude, torque.length() / leverScale);
            if (equivalentMagnitude <= 1.0E-5D) {
                return new Reading(speed, 0.0D, false);
            }
            if (equivalentMagnitude > strongestPhysicalResponse) {
                strongestPhysicalResponse = equivalentMagnitude;
                if (forceMagnitude > 1.0E-5D) {
                    forceDirection = force.scale(1.0D / forceMagnitude);
                    Vec3 lever = force.cross(torque).scale(
                            1.0D / Math.max(1.0E-12D, force.lengthSqr()));
                    rootPosition = centerOfMass.add(lever);
                } else {
                    Vec3 torqueDirection = torque.normalize();
                    Vec3 reference = Math.abs(torqueDirection.y) < 0.8D
                            ? new Vec3(0.0D, 1.0D, 0.0D)
                            : new Vec3(1.0D, 0.0D, 0.0D);
                    Vec3 lever = normalize(torqueDirection.cross(reference),
                            new Vec3(1.0D, 0.0D, 0.0D)).scale(leverScale);
                    Vec3 equivalentForce = torque.cross(lever)
                            .scale(1.0D / Math.max(1.0E-12D, lever.lengthSqr()));
                    forceDirection = normalize(equivalentForce, forceDirection);
                    rootPosition = centerOfMass.add(lever);
                    equivalentMagnitude = equivalentForce.length();
                }
            }
            return new Reading(speed, equivalentMagnitude, true);
        }

    }

    // Check if this can reuse calibration
    private static boolean canReuseCalibration(
            @Nullable ShipControlMap.PropulsionUnit prev,
            Actuator actuator,
            List<CalibrationPoint> points
    ) {
        if (!canReuseCalibration(prev, actuator)
                || prev.samples().isEmpty()) {
            return false;
        }
        for (CalibrationPoint point : points) {
            boolean covered = prev.samples().stream().anyMatch(sample ->
                    nearlyEqual(sample.minControl(), point.minControl())
                            && nearlyEqual(sample.maxControl(), point.maxControl())
                            && nearlyEqual(sample.control(), point.throttle()));
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    // Check if this can reuse calibration
    private static boolean canReuseCalibration(
            @Nullable ShipControlMap.PropulsionUnit prev,
            @Nullable Actuator actuator
    ) {
        return prev != null && actuator != null
                && prev.adapter().equals(actuator.kind())
                && prev.controllable() == actuator.controllable()
                && nearlyEqual(prev.minControl(), actuator.minControl())
                && nearlyEqual(prev.maxControl(), actuator.maxControl());
    }

    // Check if the values are nearly equal
    private static boolean nearlyEqual(double first, double second) {
        return Math.abs(finite(first) - finite(second)) <= 1.0E-6D;
    }

    // Get the reflected thrust limit
    private static double reflectedThrustLimit(Object target) {
        double limit = 0.0D;
        for (String methodName : List.of("getRawThrustCap", "getBaseThrust", "getMaxThrust",
                "getMaximumThrust", "getThrustLimit")) {
            limit = Math.max(limit, Math.abs(numberValue(invokeNoArg(target, methodName), 0.0D)));
        }
        return limit;
    }

    // Get the calibrated control
    static double calibratedControl(ShipControlMap.PropulsionUnit unit, double normalizedDemand) {
        return calibratedControl(unit, normalizedDemand, false);
    }

    // Get the calibrated envelope control
    static double calibratedEnvelopeControl(
            ShipControlMap.PropulsionUnit unit,
            double normalizedDemand
    ) {
        return calibratedControl(unit, normalizedDemand, true);
    }

    // Get the calibrated control
    private static double calibratedControl(
            ShipControlMap.PropulsionUnit unit,
            double normalizedDemand,
            boolean envelopeControl
    ) {
        double demand = Mth.clamp(finite(normalizedDemand), 0.0D, 1.0D);
        if (unit == null || demand <= 1.0E-6D || unit.maxThrust() <= 1.0E-9D
                || unit.samples().isEmpty()) {
            return demand;
        }

        List<ShipControlMap.CalibrationSample> curve;
        if (envelopeControl) {
            curve = unit.samples().stream()
                    .sorted(Comparator.comparingDouble(
                            ShipControlModuleRuntime::effectiveEnvelopeControl))
                    .toList();
        } else {
            double envelopeMaximum = unit.samples().stream()
                    .mapToDouble(ShipControlMap.CalibrationSample::maxControl)
                    .max().orElse(unit.maxControl());
            double envelopeMinimum = unit.samples().stream()
                    .filter(sample -> Math.abs(
                            sample.maxControl() - envelopeMaximum) <= 1.0E-6D)
                    .mapToDouble(ShipControlMap.CalibrationSample::minControl)
                    .min().orElse(unit.minControl());
            curve = unit.samples().stream()
                    .filter(sample -> Math.abs(
                            sample.maxControl() - envelopeMaximum) <= 1.0E-6D)
                    .filter(sample -> Math.abs(
                            sample.minControl() - envelopeMinimum) <= 1.0E-6D)
                    .sorted(Comparator.comparingDouble(
                            ShipControlMap.CalibrationSample::control))
                    .toList();
        }
        if (curve.size() < 2) {
            return demand;
        }

        double targetThrust = demand * unit.maxThrust();
        double previousControl = 0.0D;
        double previousThrust = 0.0D;
        for (ShipControlMap.CalibrationSample sample : curve) {
            double control = envelopeControl
                    ? effectiveEnvelopeControl(sample)
                    : Mth.clamp(sample.control(), 0.0D, 1.0D);
            double thrust = Math.max(previousThrust, Math.abs(sample.thrust()));
            if (targetThrust <= thrust && thrust > previousThrust + 1.0E-9D) {
                double fraction = (targetThrust - previousThrust) / (thrust - previousThrust);
                return Mth.clamp(Mth.lerp(fraction, previousControl, control), 0.0D, 1.0D);
            }
            previousControl = control;
            previousThrust = thrust;
        }
        return previousThrust > 1.0E-9D ? previousControl : demand;
    }

    // Get the effective envelope control
    static double effectiveEnvelopeControl(
            ShipControlMap.CalibrationSample sample
    ) {
        if (sample == null) {
            return 0.0D;
        }
        return effectiveEnvelopeControl(
                sample.minControl(), sample.maxControl(), sample.control());
    }

    // Get the effective envelope control
    static double effectiveEnvelopeControl(
            double minimum, double maximum, double throttle
    ) {
        double normalizedThrottle = Mth.clamp(finite(throttle), 0.0D, 1.0D);
        if (normalizedThrottle <= 0.0D) {
            return 0.0D;
        }
        double clampedMinimum = Mth.clamp(finite(minimum), 0.0D, 1.0D);
        double clampedMaximum = Mth.clamp(
                Math.max(clampedMinimum, finite(maximum)), 0.0D, 1.0D);
        return Mth.lerp(normalizedThrottle, clampedMinimum, clampedMaximum);
    }

    // Get the throttle sweep
    private static List<CalibrationPoint> throttleSweep(double minimum, double maximum) {
        double clampedMinimum = Mth.clamp(minimum, 0.0D, 1.0D);
        double clampedMaximum = Mth.clamp(Math.max(clampedMinimum, maximum), 0.0D, 1.0D);
        List<CalibrationPoint> points = new ArrayList<>(THROTTLE_STEPS.length);
        for (double throttle : THROTTLE_STEPS) {
            points.add(new CalibrationPoint(clampedMinimum, clampedMaximum, throttle));
        }
        return List.copyOf(points);
    }

    // Get the envelope sweep
    private static List<CalibrationPoint> envelopeSweep() {
        List<CalibrationPoint> points = new ArrayList<>();
        for (double effectiveControl : PRECISE_ENVELOPE_EFFECTIVE_STEPS) {
            double modulation = envelopeModulation(effectiveControl);
            points.add(new CalibrationPoint(0.0D, modulation, modulation));
        }
        for (double maximumStep : ENVELOPE_STEPS) {
            for (double minimumStep : ENVELOPE_STEPS) {
                if (minimumStep > maximumStep) {
                    continue;
                }
                for (double throttle : THROTTLE_STEPS) {
                    points.add(new CalibrationPoint(minimumStep, maximumStep, throttle));
                }
            }
        }
        return List.copyOf(points);
    }

    // Get the first method
    private static @Nullable Method firstMethod(Class<?> type, String... names) {
        return firstMethod(type, 0, names);
    }

    // Get the first method
    private static @Nullable Method firstMethod(Class<?> type, int parameterCount, String... names) {
        for (String name : names) {
            Method method = findMethod(type, name, parameterCount);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    // Find the method
    private static @Nullable Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Method[] methods;
            try {
                methods = current.getDeclaredMethods();
            } catch (LinkageError | SecurityException ignored) {
                continue;
            }
            for (Method method : methods) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    try {
                        method.setAccessible(true);
                    } catch (RuntimeException ignored) {
                    }
                    return method;
                }
            }
        }
        return null;
    }

    // Get the first result
    private static @Nullable Object firstResult(Object target, String... methods) {
        for (String method : methods) {
            Object res = invokeNoArg(target, method);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    // Invoke a method without arguments
    private static @Nullable Object invokeNoArg(Object target, String methodName) {
        Method method = findMethod(target.getClass(), methodName, 0);
        return method == null ? null : invoke(target, method);
    }

    // Run the ship control module
    private static @Nullable Object invoke(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Run the number
    private static void invokeNumber(Object target, Method method, double val) {
        Class<?> parameter;
        try {
            parameter = method.getParameterTypes()[0];
        } catch (LinkageError | RuntimeException ignored) {
            return;
        }
        Object argument;
        if (parameter == float.class || parameter == Float.class) {
            argument = (float) val;
        } else if (parameter == int.class || parameter == Integer.class) {
            argument = (int) Math.round(val);
        } else {
            argument = val;
        }
        invoke(target, method, argument);
    }

    // Read the numeric value
    private static double numberValue(@Nullable Object val, double fallback) {
        return val instanceof Number num && Double.isFinite(num.doubleValue())
                ? num.doubleValue() : fallback;
    }

    // Resolve the boolean value
    private static boolean booleanValue(@Nullable Object val, boolean fallback) {
        return val instanceof Boolean bool ? bool : fallback;
    }

    // Get the vector value
    private static @Nullable Vec3 vectorValue(@Nullable Object val) {
        if (val instanceof Vec3 vec3) {
            return vec3.lengthSqr() <= 1.0E-12D ? Vec3.ZERO : vec3.normalize();
        }
        if (val instanceof Vector3dc vector) {
            Vec3 vec3 = new Vec3(vector.x(), vector.y(), vector.z());
            return vec3.lengthSqr() <= 1.0E-12D ? Vec3.ZERO : vec3.normalize();
        }
        if (val instanceof Vector3fc vector) {
            Vec3 vec3 = new Vec3(vector.x(), vector.y(), vector.z());
            return vec3.lengthSqr() <= 1.0E-12D ? Vec3.ZERO : vec3.normalize();
        }
        if (val instanceof Direction dir) {
            return Vec3.atLowerCornerOf(dir.getNormal());
        }
        return null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        NESTED DATA
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Define the phase values
    private enum Phase {
        IDLE,
        CONFIGURING,
        FREEZING,
        SCANNING,
        CALIBRATING,
        SAVING,
        READY,
        ERROR
    }

    // Store the reading
    private record Reading(double speed, double thrust, boolean active) {
    }

    // Store the constraint impulse
    private record ConstraintImpulse(Vec3 linear, Vec3 angular) {
    }

    // Store the constraint response
    private record ConstraintResponse(Vec3 force, Vec3 torque) {
    }

    // Store the sampled constraint response
    private record ConstraintResponseSample(ConstraintResponse response, long samples) {
    }

    // Store the wheel contact key
    private record WheelContactKey(UUID subLevelId, BlockPos blockPosition) {
    }

    // Store the wheel contact response
    private record WheelContactResponse(ConstraintResponse response, long samples) {
    }

    // Store the calibration point
    private record CalibrationPoint(double minControl, double maxControl, double throttle) {
        // Initialize the calibration point
        private CalibrationPoint {
            minControl = Mth.clamp(finite(minControl), 0.0D, 1.0D);
            maxControl = Mth.clamp(Math.max(minControl, finite(maxControl)), 0.0D, 1.0D);
            throttle = Mth.clamp(finite(throttle), 0.0D, 1.0D);
        }
    }

    // Store the control demand
    private record ControlDemand(
            Vec3 force,
            Vec3 torque,
            Vec3 preferredDirection,
            double driveDirection,
            double driveStrength,
            double brakeStrength
    ) {
    }

    // Keep the navigation controller's physical force request separate from
    // the scalar values written to the player's Acceleration and Brake groups.
    private record ModeControlDemand(
            ScmControlMode.ControlOutput output,
            NavigationSpeedPlan speedPlan
    ) {
    }

    // The speed envelope derived from the live route target and collision scan.
    private record NavigationSpeedPlan(
            double forwardClearance,
            double reverseClearance,
            double permittedSpeed,
            double accelerationStrength,
            double brakeStrength
    ) {
    }

    // Store one mapped kinetic control option
    private record KineticControlOption(
            ShipControlMap.PropulsionUnit unit,
            Actuator actuator,
            Set<String> domain
    ) {
        // Initialize the kinetic control option
        private KineticControlOption {
            domain = domain == null ? Set.of() : Set.copyOf(domain);
        }
    }

    // Store the navigation guidance
    record NavigationGuidance(
            Vec3 direction,
            Vec3 controlTarget,
            double distanceResponse,
            boolean reverseRecovery,
            boolean brakeAtControlTarget,
            boolean obstacleAvoidanceRoute,
            Vec3 collisionTravelDirection
    ) {
        // Initialize the navigation guidance
        NavigationGuidance {
            direction = direction == null ? Vec3.ZERO : finite(direction);
            controlTarget = controlTarget == null ? Vec3.ZERO : finite(controlTarget);
            distanceResponse = Math.max(0.0D, finite(distanceResponse));
            collisionTravelDirection = collisionTravelDirection == null
                    ? Vec3.ZERO : finite(collisionTravelDirection);
        }

        // Get the stopped
        private static NavigationGuidance stopped(Vec3 pos) {
            return new NavigationGuidance(Vec3.ZERO, pos, 1.0D, false, true,
                    false, Vec3.ZERO);
        }
    }

    // A continuous steering reference sampled from a two-point ground curve.
    private record GroundCurveFollow(Vec3 target, Vec3 tangent) {
    }

    // Store navigation path state
    private static final class NavigationPathState {
        // Tracked waypoints
        private List<Vec3> waypoints = List.of();
        // Gear selection for each tracked waypoint
        private List<Boolean> reverseWaypoints = List.of();
        // Reverse recovery segments used only by path debug rendering
        private List<ScmPathDebugService.PathSegment> exploredSegments = List.of();
        // Reverse-only recovery curves
        private List<GroundPathPlanner.Curve> groundRouteCurves = List.of();
        private int groundCurveIndex;
        // Waypoint index
        private int waypointIndex;
        // Planned target
        private @Nullable Vec3 plannedTarget;
        // Next replan tick
        private long nextReplanTick = Long.MIN_VALUE;
        // Next local detour search tick
        private long nextDetourTick = Long.MIN_VALUE;
        // Position where the current reverse recovery started
        private Vec3 reverseStartPosition = Vec3.ZERO;
        // Minimum distance to reverse before resuming forward travel
        private double minimumReverseDistance;
        // Consecutive non-ground retreats without a usable forward route
        private int consecutiveReverseEscapes;
        // Whether live clearance, rather than route exhaustion, ended reverse
        private boolean reverseForwardReleased;
        // Tick-to-tick waypoint capture and approach progress
        private final WaypointProgressTracker waypointProgress =
                new WaypointProgressTracker();

        // Check whether the current waypoint is a reverse maneuver
        private boolean reverseAtWaypoint() {
            return waypointIndex >= 0 && waypointIndex < reverseWaypoints.size()
                    && reverseWaypoints.get(waypointIndex);
        }

        // Check whether a forward local detour is active
        private boolean hasLocalDetour() {
            return waypointIndex >= 0 && waypointIndex < waypoints.size()
                    && !reverseAtWaypoint() && groundRouteCurves.isEmpty();
        }

        // Replace the forward local detour
        private void setForwardDetour(
                List<Vec3> route,
                Vec3 target,
                long currentTick
        ) {
            replaceLocalDetour(route, target, currentTick);
            consecutiveReverseEscapes = 0;
        }

        // Replace the one permitted non-ground reverse escape
        private void setReverseEscapeDetour(
                List<Vec3> route,
                Vec3 target,
                long currentTick
        ) {
            replaceLocalDetour(route, target, currentTick);
            consecutiveReverseEscapes++;
        }

        // Replace one retained local detour
        private void replaceLocalDetour(
                List<Vec3> route,
                Vec3 target,
                long currentTick
        ) {
            waypoints = route == null ? List.of() : List.copyOf(route);
            reverseWaypoints = waypoints.stream().map(ignored -> false).toList();
            waypointIndex = 0;
            plannedTarget = target;
            groundRouteCurves = List.of();
            groundCurveIndex = 0;
            nextDetourTick = currentTick + LOCAL_DETOUR_RETRY_TICKS;
        }

        // Clear the forward local detour
        private void clearForwardDetour() {
            waypoints = List.of();
            reverseWaypoints = List.of();
            waypointIndex = 0;
            plannedTarget = null;
            groundRouteCurves = List.of();
            groundCurveIndex = 0;
            nextDetourTick = Long.MIN_VALUE;
        }

        // Retire a reverse maneuver without discarding target progress state
        private void clearReverseRecovery() {
            waypoints = List.of();
            reverseWaypoints = List.of();
            waypointIndex = 0;
            plannedTarget = null;
            groundRouteCurves = List.of();
            groundCurveIndex = 0;
            reverseForwardReleased = false;
            nextReplanTick = Long.MIN_VALUE;
        }

        // Check whether the current sparse checkpoint has a physical curve.
        private boolean hasActiveGroundCurve() {
            return groundCurveIndex >= 0
                    && groundCurveIndex < groundRouteCurves.size();
        }

        // Require real travel along an active physical manoeuvre before its
        // hull-overlap endpoint test may complete it. Route rejoining remains
        // a separate intentional fast-forward operation below.
        private double activeGroundCurveProgress(Vec3 position) {
            return hasActiveGroundCurve()
                    ? groundRouteCurves.get(groundCurveIndex).nearestFraction(position)
                    : 1.0D;
        }

        // Replace the reverse recovery curves
        private void setGroundRoute(List<GroundPathPlanner.Curve> curves) {
            groundRouteCurves = curves == null ? List.of() : List.copyOf(curves);
            groundCurveIndex = 0;
        }

        // Advance exactly one maneuver when its sparse endpoint is completed.
        private void completeGroundWaypoint(Vec3 waypoint) {
            if (groundCurveIndex < groundRouteCurves.size()) groundCurveIndex++;
        }

        // Follow the active curve continuously. Its completion remains the
        // only route checkpoint, but the target/tangent evolve along its
        // capability-derived arc so the car cannot cut the corner or circle
        // around an unreachable chord.
        private @Nullable GroundCurveFollow groundCurveFollow(
                Vec3 position,
                double lookahead
        ) {
            if (groundCurveIndex < 0 || groundCurveIndex >= groundRouteCurves.size()) {
                return null;
            }
            GroundPathPlanner.Curve curve = groundRouteCurves.get(groundCurveIndex);
            double length = curve.length();
            double progress = curve.nearestFraction(position);
            double targetProgress = length <= 1.0E-8D ? 1.0D
                    : Math.min(1.0D, progress + Math.max(0.0D, lookahead) / length);
            Vec3 tangent = curve.tangentAtFraction(progress);
            return new GroundCurveFollow(curve.pointAtFraction(targetProgress), tangent);
        }
    }

    // Store the hull bounds
    private record HullBounds(
            List<AABB> relativeBounds,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        // Get the maximum imum span
        private double maximumSpan() {
            return Math.max(
                    Math.max(maxX - minX, maxY - minY),
                    maxZ - minZ);
        }

        // Get the largest horizontal distance from the controller origin to
        // the Sable collision hull.
        private double horizontalRadius() {
            double radius = 0.0D;
            for (AABB bounds : relativeBounds) {
                radius = Math.max(radius, Math.hypot(bounds.minX, bounds.minZ));
                radius = Math.max(radius, Math.hypot(bounds.minX, bounds.maxZ));
                radius = Math.max(radius, Math.hypot(bounds.maxX, bounds.minZ));
                radius = Math.max(radius, Math.hypot(bounds.maxX, bounds.maxZ));
            }
            return radius;
        }

        // Get the largest spatial distance from the controller origin to the
        // Sable collision hull.
        private double spatialRadius() {
            double radiusSqr = 0.0D;
            for (AABB bounds : relativeBounds) {
                for (double x : new double[]{bounds.minX, bounds.maxX}) {
                    for (double y : new double[]{bounds.minY, bounds.maxY}) {
                        for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                            radiusSqr = Math.max(radiusSqr, x * x + y * y + z * z);
                        }
                    }
                }
            }
            return Math.sqrt(radiusSqr);
        }

        // Get the world bounds
        private List<AABB> worldBoundsAt(Vec3 anchor, double margin) {
            return worldBoundsAt(anchor, margin, false);
        }

        // Get the world bounds
        private List<AABB> worldBoundsAt(
                Vec3 anchor,
                double margin,
                boolean preserveGroundClearance
        ) {
            Vec3 pos = finite(anchor);
            double inflation = Math.max(0.0D, finite(margin));
            List<AABB> worldBounds = new ArrayList<>(relativeBounds.size());
            for (AABB bounds : relativeBounds) {
                worldBounds.add(new AABB(
                        bounds.minX + pos.x - inflation,
                        bounds.minY + pos.y
                                + (preserveGroundClearance
                                ? GROUND_NAVIGATION_CONTACT_CLEARANCE : -inflation),
                        bounds.minZ + pos.z - inflation,
                        bounds.maxX + pos.x + inflation,
                        bounds.maxY + pos.y + inflation,
                        bounds.maxZ + pos.z + inflation));
            }
            return List.copyOf(worldBounds);
        }

        // Get conservative world bounds for one planned vehicle orientation
        private List<AABB> worldBoundsAtPose(
                Vec3 anchor,
                Vec3 forward,
                Vec3 up,
                Vec3 referenceForward,
                Vec3 referenceUp,
                double margin,
                boolean preserveGroundClearance
        ) {
            Vec3 pos = finite(anchor);
            Vec3 sourceForward = normalize(referenceForward,
                    new Vec3(0.0D, 0.0D, 1.0D));
            Vec3 sourceUp = normalize(referenceUp,
                    new Vec3(0.0D, 1.0D, 0.0D));
            Vec3 sourceRight = normalize(sourceForward.cross(sourceUp),
                    new Vec3(1.0D, 0.0D, 0.0D));
            Vec3 targetForward = normalize(forward, sourceForward);
            Vec3 targetUp = normalize(up, sourceUp);
            Vec3 targetRight = normalize(targetForward.cross(targetUp), sourceRight);
            targetUp = normalize(targetRight.cross(targetForward), targetUp);
            double inflation = Math.max(0.0D, finite(margin));
            List<AABB> worldBounds = new ArrayList<>(relativeBounds.size());
            for (AABB bounds : relativeBounds) {
                double minX = Double.POSITIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double maxY = Double.NEGATIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                for (double x : new double[]{bounds.minX, bounds.maxX}) {
                    for (double y : new double[]{bounds.minY, bounds.maxY}) {
                        for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                            Vec3 point = new Vec3(x, y, z);
                            Vec3 rotated = targetRight.scale(point.dot(sourceRight))
                                    .add(targetUp.scale(point.dot(sourceUp)))
                                    .add(targetForward.scale(point.dot(sourceForward)))
                                    .add(pos);
                            minX = Math.min(minX, rotated.x);
                            minY = Math.min(minY, rotated.y);
                            minZ = Math.min(minZ, rotated.z);
                            maxX = Math.max(maxX, rotated.x);
                            maxY = Math.max(maxY, rotated.y);
                            maxZ = Math.max(maxZ, rotated.z);
                        }
                    }
                }
                worldBounds.add(new AABB(
                        minX - inflation,
                        minY + (preserveGroundClearance
                                ? GROUND_NAVIGATION_CONTACT_CLEARANCE : -inflation),
                        minZ - inflation,
                        maxX + inflation, maxY + inflation, maxZ + inflation));
            }
            return List.copyOf(worldBounds);
        }

        // Get the distance to target
        private double distanceToTarget(Vec3 anchor, Vec3 target) {
            return targetDistanceToBounds(target, worldBoundsAt(anchor, 0.0D));
        }

        // Get horizontal distance from a target point to the assembled hull.
        private double horizontalDistanceToTarget(Vec3 anchor, Vec3 targetPosition) {
            Vec3 target = finite(targetPosition);
            double nearestSquared = Double.POSITIVE_INFINITY;
            for (AABB bounds : worldBoundsAt(anchor, 0.0D)) {
                double dx = Math.max(
                        Math.max(bounds.minX - target.x, 0.0D),
                        target.x - bounds.maxX);
                double dz = Math.max(
                        Math.max(bounds.minZ - target.z, 0.0D),
                        target.z - bounds.maxZ);
                nearestSquared = Math.min(nearestSquared, dx * dx + dz * dz);
            }
            return Math.sqrt(nearestSquared);
        }
    }

    // Store the initialization filters
    record InitializationFilters(
            boolean displayProgress,
            boolean forceFullInitialization,
            boolean ignoreBearings,
            boolean ignoreSails,
            boolean ignoreThrusters
    ) {
        private static final InitializationFilters NONE =
                new InitializationFilters(false, false, false, false, false);

        // Create the initialization filters from parameters
        static InitializationFilters fromParameters(Map<String, Double> parameters) {
            Map<String, Double> values = parameters == null ? Map.of() : parameters;
            return new InitializationFilters(
                    enabled(values.get("display_progress")),
                    enabled(values.get("force_full_initialization")),
                    enabled(values.get("ignore_bearings")),
                    enabled(values.get("ignore_sails")),
                    enabled(values.get("ignore_thrusters")));
        }

        // Check if the mapping includes bearings
        boolean mapsBearings() {
            return !ignoreBearings;
        }

        // Check if the mapping includes sails
        boolean mapsSails() {
            return !ignoreSails;
        }

        // Check if the mapping includes the sublevel
        boolean mapsSubLevel(boolean bearingDescendant) {
            return !ignoreBearings || !bearingDescendant;
        }

        // Check if the mapping includes propulsion
        boolean mapsPropulsion(boolean thrusterProvider) {
            return !ignoreThrusters || !thrusterProvider;
        }

        // Check if this is enabled
        private static boolean enabled(@Nullable Double val) {
            return val != null && Double.isFinite(val) && val > 0.5D;
        }
    }

    // Store the mass point
    record MassPoint(double mass, Vec3 rootPosition) {
        // Initialize the mass point
        MassPoint {
            mass = Double.isFinite(mass) ? Math.max(0.0D, mass) : 0.0D;
            rootPosition = finite(rootPosition);
        }
    }

    // Store the propulsion unit key
    private record PropulsionUnitKey(
            UUID subLevelId,
            BlockPos blockPosition,
            String blockId,
            String adapter
    ) {
        // Initialize the propulsion unit key
        private PropulsionUnitKey {
            blockPosition = blockPosition == null
                    ? BlockPos.ZERO : blockPosition.immutable();
            blockId = blockId == null ? "" : blockId;
            adapter = adapter == null ? "" : adapter;
        }

        // Create the propulsion unit key
        private static PropulsionUnitKey of(ShipControlMap.PropulsionUnit unit) {
            return new PropulsionUnitKey(
                    unit.subLevelId(), unit.blockPosition(), unit.blockId(), unit.adapter());
        }
    }

    // Store the bearing unit key
    private record BearingUnitKey(
            UUID hostSubLevelId,
            BlockPos blockPosition,
            String blockId,
            String adapter
    ) {
        // Initialize the bearing unit key
        private BearingUnitKey {
            blockPosition = blockPosition == null
                    ? BlockPos.ZERO : blockPosition.immutable();
            blockId = blockId == null ? "" : blockId;
            adapter = adapter == null ? "" : adapter;
        }

        // Create the bearing unit key
        private static BearingUnitKey of(ShipControlMap.BearingUnit bearing) {
            return new BearingUnitKey(
                    bearing.hostSubLevelId(), bearing.blockPosition(),
                    bearing.blockId(), bearing.adapter());
        }
    }

    // Store the aerodynamic surface key
    private record AerodynamicSurfaceKey(
            UUID subLevelId,
            BlockPos blockPosition,
            String blockId,
            double parallelDragScalar,
            double directionlessDragScalar,
            double liftScalar
    ) {
        // Initialize the aerodynamic surface key
        private AerodynamicSurfaceKey {
            blockPosition = blockPosition == null
                    ? BlockPos.ZERO : blockPosition.immutable();
            blockId = blockId == null ? "" : blockId;
            parallelDragScalar = finite(parallelDragScalar);
            directionlessDragScalar = finite(directionlessDragScalar);
            liftScalar = finite(liftScalar);
        }

        // Create the aerodynamic surface key
        private static AerodynamicSurfaceKey of(ShipControlMap.AerodynamicSurface surface) {
            return new AerodynamicSurfaceKey(
                    surface.subLevelId(), surface.blockPosition(), surface.blockId(),
                    surface.parallelDragScalar(), surface.directionlessDragScalar(),
                    surface.liftScalar());
        }

        // Create the aerodynamic surface key
        private static AerodynamicSurfaceKey of(AerodynamicSurfaceCalibration surface) {
            return new AerodynamicSurfaceKey(
                    surface.subLevelId, surface.blockPosition, surface.blockId,
                    surface.parallelDragScalar, surface.directionlessDragScalar,
                    surface.liftScalar);
        }
    }

    // Store the bearing reuse
    private record BearingReuse(
            ShipControlMap.BearingUnit bearing,
            Map<Integer, Integer> unitIndexRemap
    ) {
    }

    // Store initialization body state
    private record InitializationBodyState(
            Pose3d pose,
            Vector3d linearVelocity,
            Vector3d angularVelocity
    ) {
        // Initialize the initialization body state
        private InitializationBodyState {
            pose = new Pose3d(pose);
            linearVelocity = new Vector3d(linearVelocity);
            angularVelocity = new Vector3d(angularVelocity);
        }
    }

    // Store the aircraft attitude control
    record AircraftAttitudeControl(
            double pitch,
            double roll,
            double yaw,
            double desiredPitchDegrees,
            double desiredBankDegrees
    ) {
        // Initialize the aircraft attitude control
        AircraftAttitudeControl {
            pitch = Mth.clamp(finite(pitch), -1.0D, 1.0D);
            roll = Mth.clamp(finite(roll), -1.0D, 1.0D);
            yaw = Mth.clamp(finite(yaw), -1.0D, 1.0D);
            desiredPitchDegrees = finite(desiredPitchDegrees);
            desiredBankDegrees = finite(desiredBankDegrees);
        }
    }

    // Store the aircraft guidance
    record AircraftGuidance(Vec3 force, Vec3 torque, double stabilizationStrength) {
        // Initialize the aircraft guidance
        AircraftGuidance {
            force = force == null ? Vec3.ZERO : force;
            torque = torque == null ? Vec3.ZERO : torque;
            stabilizationStrength =
                    Mth.clamp(finite(stabilizationStrength), 0.0D, 1.0D);
        }
    }

    // Store the mapped docking connector
    public record MappedDockingConnector(
            int index,
            UUID subLevelId,
            BlockPos blockPosition,
            Vec3 worldTipPosition,
            Vec3 worldFacing,
            Vec3 worldUp
    ) {
        // Initialize the mapped docking connector
        public MappedDockingConnector(
                int index,
                UUID subLevelId,
                BlockPos blockPosition,
                Vec3 worldTipPosition,
                Vec3 worldFacing
        ) {
            this(index, subLevelId, blockPosition, worldTipPosition, worldFacing,
                    dockingConnectorUp(worldFacing));
        }

        // Get the target point name
        public String targetPointName() {
            return "docking_connector:" + index;
        }
    }

    // Store the dynamic connector key
    private record DynamicConnectorKey(UUID subLevelId, BlockPos blockPosition) {
        // Initialize the dynamic connector key
        private DynamicConnectorKey {
            blockPosition = blockPosition.immutable();
        }
    }

    // Store the foreign SCM map
    private record ForeignScmMap(
            ShipControlMap map,
            UUID controllerSubLevelId,
            BlockPos controllerPosition,
            AdvancedContraptionControllerBlockEntity controller
    ) {
    }

    // Store the assembly map source
    private record AssemblyMapSource(
            ShipControlMap map,
            boolean foreign,
            AdvancedContraptionControllerBlockEntity controller
    ) {
    }

    // Store the assembly unit index
    private record AssemblyUnitIndex(UUID fragmentId, int sourceIndex) {
    }

    // Store the assembly unit identity
    private record AssemblyUnitIdentity(
            UUID subLevelId,
            BlockPos blockPosition,
            String adapter
    ) {
    }

    // Store the assembly bearing identity
    private record AssemblyBearingIdentity(
            UUID subLevelId,
            BlockPos blockPosition,
            String adapter
    ) {
    }

    // Store the assembly block identity
    private record AssemblyBlockIdentity(UUID subLevelId, BlockPos blockPosition) {
    }

    // Store the coupler control request
    private record CouplerControlRequest(
            String commandId,
            String nodeType,
            boolean attach,
            int selector,
            List<ShipCouplerBlockEntity.EndpointKey> endpoints
    ) {
        // Initialize the coupler control request
        private CouplerControlRequest {
            endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        }

        // Copy the coupler control request with the endpoints
        private CouplerControlRequest withEndpoints(
                List<ShipCouplerBlockEntity.EndpointKey> selected) {
            return new CouplerControlRequest(
                    commandId, nodeType, attach, selector, selected);
        }
    }

    // Define the aircraft flight phase values
    enum AircraftFlightPhase {
        CLIMB,
        CRUISE,
        APPROACH,
        LANDING
    }

    // Store aircraft navigation state
    private static final class AircraftNavigationState {
        // Current phase
        private @Nullable AircraftFlightPhase phase;
    }

    // Store the active ship command
    private record ActiveShipCommand(
            String id,
            String type,
            double amount,
            double strength,
            double targetY,
            Vec3 targetPosition,
            double targetSpeed,
            double driveThrottle,
            double tolerance,
            boolean avoidCollisions,
            boolean lockRotation,
            Vec3 targetAttitude,
            ShipTargetPoint targetPoint,
            int targetConnectorIndex,
            Vec3 targetDirection,
            Vec3 targetUp
    ) {
        // Initialize the active ship command
        private ActiveShipCommand(
                String id,
                String type,
                double amount,
                double strength,
                double targetY,
                Vec3 targetPosition,
                double targetSpeed,
                double tolerance,
                boolean avoidCollisions,
                boolean lockRotation,
                Vec3 targetAttitude,
                ShipTargetPoint targetPoint
        ) {
            this(id, type, amount, strength, targetY, targetPosition,
                    targetSpeed, -1.0D, tolerance, avoidCollisions, lockRotation,
                    targetAttitude, targetPoint, -1, Vec3.ZERO, Vec3.ZERO);
        }

        // Initialize the active ship command
        private ActiveShipCommand {
            id = id == null ? "" : id;
            type = type == null ? "" : type;
            amount = Mth.clamp(finite(amount), -1.0D, 1.0D);
            strength = Mth.clamp(finite(strength), 0.0D, 1.0D);
            targetY = finite(targetY);
            targetPosition = targetPosition == null ? Vec3.ZERO : targetPosition;
            targetSpeed = Math.max(0.0D, finite(targetSpeed));
            driveThrottle = Double.isFinite(driveThrottle)
                    ? Mth.clamp(driveThrottle, 0.0D, 1.0D) : -1.0D;
            tolerance = Math.max(0.0D, finite(tolerance));
            targetAttitude =
                    targetAttitude == null ? Vec3.ZERO : finite(targetAttitude);
            targetPoint = targetPoint == null
                    ? ShipTargetPoint.CENTER_OF_MASS : targetPoint;
            targetConnectorIndex = Math.max(-1, targetConnectorIndex);
            targetDirection = targetDirection == null
                    ? Vec3.ZERO : normalize(targetDirection, Vec3.ZERO);
            targetUp = targetUp == null ? Vec3.ZERO : normalize(targetUp, Vec3.ZERO);
        }

        // Get the idle
        private static ActiveShipCommand idle(String id, String type) {
            return new ActiveShipCommand(
                    id, type, 0.0D, 0.0D, 0.0D,
                    Vec3.ZERO, 0.0D, 0.0D, false, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the amount
        private static ActiveShipCommand amount(String id, String type, double amount) {
            return new ActiveShipCommand(
                    id, type, amount, 0.0D, 0.0D,
                    Vec3.ZERO, 0.0D, 0.0D, false, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the strength
        private static ActiveShipCommand strength(String id, String type, double strength) {
            return new ActiveShipCommand(
                    id, type, 0.0D, strength, 0.0D,
                    Vec3.ZERO, 0.0D, 0.0D, false, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the attitude
        private static ActiveShipCommand attitude(
                String id,
                String type,
                double strength,
                Vec3 targetAttitude
        ) {
            return new ActiveShipCommand(
                    id, type, 0.0D, strength, 0.0D,
                    Vec3.ZERO, 0.0D, 0.0D,
                    false, true, targetAttitude,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the altitude
        private static ActiveShipCommand altitude(
                String id,
                String type,
                double targetY,
                double targetSpeed,
                double strength
        ) {
            return new ActiveShipCommand(
                    id, type, 0.0D, strength, targetY,
                    Vec3.ZERO, targetSpeed, POSITION_TOLERANCE,
                    false, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the target
        private static ActiveShipCommand target(
                String id,
                String type,
                Vec3 target,
                double targetSpeed,
                double tolerance,
                boolean avoidCollisions
        ) {
            return target(id, type, target, targetSpeed, tolerance,
                    avoidCollisions, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the target with a direct propulsion request.
        private static ActiveShipCommand target(
                String id,
                String type,
                Vec3 target,
                double targetSpeed,
                double driveThrottle,
                double tolerance,
                boolean avoidCollisions
        ) {
            return target(id, type, target, targetSpeed, driveThrottle, tolerance,
                    avoidCollisions, false, Vec3.ZERO,
                    ShipTargetPoint.CENTER_OF_MASS);
        }

        // Get the target
        private static ActiveShipCommand target(
                String id,
                String type,
                Vec3 target,
                double targetSpeed,
                double tolerance,
                boolean avoidCollisions,
                boolean lockRotation,
                Vec3 targetAttitude,
                ShipTargetPoint targetPoint
        ) {
            return target(id, type, target, targetSpeed, -1.0D, tolerance,
                    avoidCollisions, lockRotation, targetAttitude,
                    targetPoint, -1, Vec3.ZERO, Vec3.ZERO);
        }

        // Get the target with a direct propulsion request.
        private static ActiveShipCommand target(
                String id,
                String type,
                Vec3 target,
                double targetSpeed,
                double driveThrottle,
                double tolerance,
                boolean avoidCollisions,
                boolean lockRotation,
                Vec3 targetAttitude,
                ShipTargetPoint targetPoint
        ) {
            return target(id, type, target, targetSpeed, driveThrottle, tolerance,
                    avoidCollisions, lockRotation, targetAttitude,
                    targetPoint, -1, Vec3.ZERO, Vec3.ZERO);
        }

        // Get the target
        private static ActiveShipCommand target(
                String id,
                String type,
                Vec3 target,
                double targetSpeed,
                double tolerance,
                boolean avoidCollisions,
                boolean lockRotation,
                Vec3 targetAttitude,
                ShipTargetPoint targetPoint,
                int targetConnectorIndex,
                Vec3 targetDirection,
                Vec3 targetUp
        ) {
            return target(id, type, target, targetSpeed, -1.0D, tolerance,
                    avoidCollisions, lockRotation, targetAttitude, targetPoint,
                    targetConnectorIndex, targetDirection, targetUp);
        }

        // Get the target with a direct propulsion request.
        private static ActiveShipCommand target(
                String id,
                String type,
                Vec3 target,
                double targetSpeed,
                double driveThrottle,
                double tolerance,
                boolean avoidCollisions,
                boolean lockRotation,
                Vec3 targetAttitude,
                ShipTargetPoint targetPoint,
                int targetConnectorIndex,
                Vec3 targetDirection,
                Vec3 targetUp
        ) {
            return new ActiveShipCommand(
                    id, type, 0.0D, 1.0D, target.y,
                    target, targetSpeed, driveThrottle, tolerance, avoidCollisions,
                    lockRotation, targetAttitude, targetPoint,
                    targetConnectorIndex, targetDirection, targetUp);
        }

        // Check if the actuator is disabled
        private boolean disabled() {
            return switch (type) {
                case "ship_yaw", "ship_yaw_right", "ship_yaw_left",
                     "ship_pan", "ship_pitch", "ship_pitch_up", "ship_pitch_down",
                     "ship_tilt", "ship_roll", "ship_roll_right", "ship_roll_left",
                     "ship_accelerate", "ship_forward", "ship_reverse", "ship_strafe",
                     "ship_backward", "ship_strafe_left", "ship_strafe_right",
                     "ship_ascend", "ship_descend" ->
                        Math.abs(amount) <= 1.0E-4D;
                case "ship_stabilize", "ship_decelerate", "ship_brake", "ship_hover" ->
                        strength <= 1.0E-4D;
                default -> false;
            };
        }

        // Check if this uses the same request
        private boolean sameRequestAs(ActiveShipCommand other) {
            if (other == null || !id.equals(other.id) || !type.equals(other.type)) {
                return false;
            }
            return switch (type) {
                case "ship_yaw", "ship_yaw_right", "ship_yaw_left",
                     "ship_pan", "ship_pitch", "ship_pitch_up", "ship_pitch_down",
                     "ship_tilt", "ship_roll", "ship_roll_right", "ship_roll_left",
                     "ship_accelerate", "ship_forward", "ship_reverse", "ship_strafe",
                     "ship_backward", "ship_strafe_left", "ship_strafe_right",
                     "ship_ascend", "ship_descend" ->
                        Double.compare(amount, other.amount) == 0;
                case "ship_stabilize", "ship_decelerate", "ship_brake", "ship_hover" ->
                        Double.compare(strength, other.strength) == 0;
                case "ship_climb" ->
                        Double.compare(targetY, other.targetY) == 0
                                && Double.compare(targetSpeed, other.targetSpeed) == 0
                                && Double.compare(strength, other.strength) == 0;
                case "ship_face" ->
                        targetPosition.equals(other.targetPosition)
                                && Double.compare(targetSpeed, other.targetSpeed) == 0
                                && Double.compare(tolerance, other.tolerance) == 0;
                case "ship_dock", "ship_navigate", "ship_follow" ->
                        targetPosition.equals(other.targetPosition)
                                && Double.compare(targetSpeed, other.targetSpeed) == 0
                                && Double.compare(driveThrottle, other.driveThrottle) == 0
                                && Double.compare(tolerance, other.tolerance) == 0
                                && avoidCollisions == other.avoidCollisions
                                && lockRotation == other.lockRotation
                                && targetPoint == other.targetPoint
                                && targetConnectorIndex == other.targetConnectorIndex
                                && targetDirection.equals(other.targetDirection)
                                && targetUp.equals(other.targetUp);
                default -> equals(other);
            };
        }

        // Check if this continues the target update
        private boolean continuesTargetUpdateWith(ActiveShipCommand other) {
            return other != null
                    && ("ship_follow".equals(type)
                            || "ship_navigate".equals(type)
                            || "ship_dock".equals(type))
                    && type.equals(other.type)
                    && id.equals(other.id)
                    && Double.compare(targetSpeed, other.targetSpeed) == 0
                    && Double.compare(driveThrottle, other.driveThrottle) == 0
                    && Double.compare(tolerance, other.tolerance) == 0
                    && avoidCollisions == other.avoidCollisions
                    && lockRotation == other.lockRotation
                    && targetPoint == other.targetPoint
                    && targetConnectorIndex == other.targetConnectorIndex
                    && targetDirection.equals(other.targetDirection)
                    && targetUp.equals(other.targetUp);
        }
    }

    // Store the collision telemetry
    private record CollisionTelemetry(
            double nearest,
            double forward,
            double backward,
            double left,
            double right,
            double up,
            double down
    ) {
        private static final CollisionTelemetry EMPTY =
                new CollisionTelemetry(
                        0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

        // Get the range
        private static CollisionTelemetry atRange(double range) {
            double val = Math.max(0.0D, finite(range));
            return new CollisionTelemetry(
                    val, val, val, val, val, val, val);
        }
    }

    // Store the collision telemetry key
    private record CollisionTelemetryKey(double range, long intervalTicks) {
    }

    // Store collision telemetry state
    private static final class CollisionTelemetryState {
        // Completed tick
        private long completedTick = Long.MIN_VALUE;
        // Work tick
        private long workTick = Long.MIN_VALUE;
        // Current cached
        private CollisionTelemetry cached;
        // Current scan
        private @Nullable CollisionTelemetryScan scan;

        // Initialize the collision telemetry state
        private CollisionTelemetryState(double range) {
            cached = CollisionTelemetry.atRange(range);
        }
    }

    // Handle the collision telemetry scan
    private static final class CollisionTelemetryScan {
        // Range
        private final double range;
        // Origin
        private final Vec3 origin;
        // Tracked directions
        private final List<Vec3> directions;
        // Tracked moving bounds
        private final List<AABB> movingBounds;
        // Context
        private final CollisionScanContext context;
        // Probe cache
        private final SubLevelParticleOcclusion.ProbeCache probeCache;
        // Distances
        private final double[] distances;
        // Direction index
        private int directionIndex;

        // Initialize the collision telemetry scan
        private CollisionTelemetryScan(
                double range,
                Vec3 origin,
                List<Vec3> directions,
                List<AABB> movingBounds,
                CollisionScanContext ctx,
                SubLevelParticleOcclusion.ProbeCache probeCache,
                CollisionTelemetry initial
        ) {
            this.range = range;
            this.origin = origin;
            this.directions = directions;
            this.movingBounds = movingBounds;
            this.context = ctx;
            this.probeCache = probeCache;
            this.distances = new double[] {
                    initial.forward(), initial.backward(), initial.left(),
                    initial.right(), initial.up(), initial.down()
            };
        }

        // Get the telemetry
        private CollisionTelemetry telemetry() {
            double nearest = distances[0];
            for (int idx = 1; idx < distances.length; idx++) {
                nearest = Math.min(nearest, distances[idx]);
            }
            return new CollisionTelemetry(
                    nearest,
                    distances[0], distances[1], distances[2], distances[3],
                    distances[4], distances[5]);
        }
    }

    // Store collision scan context
    private record CollisionScanContext(
            Level level,
            ServerSubLevel containingSubLevel,
            List<SubLevel> shipSubLevels,
            Set<UUID> excludedSubLevelIds
    ) {
    }

    // Store the collision probe
    private record CollisionProbe(
            Vec3 origin,
            Vec3 direction,
            double range,
            boolean preserveGroundClearance
    ) {
    }

    // Store the gravity compensation
    private record GravityCompensation(Vec3 demand, Vec3 direction) {
        private static final GravityCompensation NONE =
                new GravityCompensation(Vec3.ZERO, Vec3.ZERO);
    }

    // Store the telemetry
    private record Telemetry(boolean available, Vec3 position, Vec3 velocity,
                             Vec3 angularVelocity, Vec3 eulerDegrees) {
        private static final Telemetry EMPTY = new Telemetry(false, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
    }

    // Immutable server-authoritative structural metrics for SCM presentation.
    private record SimulationMetrics(double mass, Vec3 centerOfMass, Vec3 centerOfLift, String facing) {
        private static final SimulationMetrics EMPTY = new SimulationMetrics(0.0D, Vec3.ZERO, Vec3.ZERO, "unknown");

        private SimulationMetrics {
            mass = Double.isFinite(mass) && mass > 0.0D ? mass : 0.0D;
            centerOfMass = finite(centerOfMass);
            centerOfLift = finite(centerOfLift);
            facing = facing == null || facing.isBlank() ? "unknown" : facing;
        }
    }
}
