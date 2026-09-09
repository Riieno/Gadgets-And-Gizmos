package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.createrailwaysnavigator.RailwayNavigatorGraphCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmBuiltinControlModes;
import com.rieno.gadgetsandgizmos.lib.navigation.SablePathfinder;
import com.rieno.gadgetsandgizmos.lib.shipping.ShipDockScheduler;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.CargoThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.FluidThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.IdleCargoCondition;
import com.simibubi.create.content.trains.schedule.condition.ItemThresholdCondition;
import com.simibubi.create.content.trains.schedule.condition.PlayerPassengerCondition;
import com.simibubi.create.content.trains.schedule.condition.RedstoneLinkCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.StationPoweredCondition;
import com.simibubi.create.content.trains.schedule.condition.StationUnloadedCondition;
import com.simibubi.create.content.trains.schedule.condition.TimeOfDayCondition;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ChangeThrottleInstruction;
import com.simibubi.create.content.trains.schedule.destination.ChangeTitleInstruction;
import com.simibubi.create.content.trains.schedule.destination.DeliverPackagesInstruction;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.FetchPackagesInstruction;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.Collections;
import java.util.WeakHashMap;

// Drive an SCM through shipping stops while keeping pilot, docking and cargo state resumable
public final class ShippingScheduleRuntime {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final System.Logger LOGGER = System.getLogger(ShippingScheduleRuntime.class.getName());
    private static final String NAVIGATION_COMMAND = "shipping_schedule_route";
    private static final String ROUTE_TERMINAL_COMMAND =
            "shipping_schedule_route_terminal";
    private static final String QUEUE_HOVER_COMMAND = "shipping_schedule_queue_hover";
    private static final String PARK_NAVIGATION_COMMAND = "shipping_schedule_park";
    private static final String PARK_HOLD_COMMAND = "shipping_schedule_park_hold";
    private static final String DOCKING_COMMAND = "shipping_schedule_dock";
    private static final String CONNECTORLESS_ARRIVAL_RESOURCE = "arrival|";
    private static final String UNDOCK_BRAKE_COMMAND = "shipping_schedule_undock_brake";
    private static final String UNDOCK_HOVER_COMMAND = "shipping_schedule_undock_hover";
    private static final ResourceLocation CREATE_ADDITION_ENERGY_THRESHOLD =
            ResourceLocation.fromNamespaceAndPath("createaddition", "energy_threshold");
    private static final String LOAD_INDUCED_SCM_PAUSE =
            "Shipping schedule paused: initialize the ship control module first";
    private static final double FUEL_RESERVE = 0.10D;
    private static final double MAX_CRUISE_SPEED = 28.0D;
    private static final double MIN_CRUISE_SPEED = MAX_CRUISE_SPEED * 0.05D;
    private static final double MAX_DOCKING_SPEED = 1.0D;
    private static final double CONNECTORLESS_ARRIVAL_PADDING = 2.0D;
    private static final double ROUTE_TERMINAL_MIN_STANDOFF = 12.0D;
    private static final double ROUTE_TERMINAL_CLEARANCE = 4.0D;
    private static final double ROUTE_TERMINAL_MIN_CAPTURE_RADIUS = 4.0D;
    private static final double ROUTE_TERMINAL_MAX_CAPTURE_RADIUS = 32.0D;
    private static final long UNDOCK_SETTLE_TICKS = 20L;
    private static final long DOCKING_STALL_TICKS = 120L;
    private static final int MAX_DOCKING_RETRIES_PER_CONNECTOR = 3;
    private static final long FAILED_DOCKING_SLOT_COOLDOWN_TICKS = 600L;
    private static final long LOST_ROUTE_SLOT_COOLDOWN_TICKS = 200L;
    private static final int MAX_FAILED_DOCKING_SLOTS = 32;
    private static final double MAGNETIC_CAPTURE_RANGE = 4.0D;
    private static final double MAGNETIC_CAPTURE_ALIGNMENT = 0.5D;
    private static final long ROUTE_PROGRESS_STALL_TICKS = 600L;
    private static final long ROUTE_DIVERGENCE_GRACE_TICKS = 100L;
    private static final double ROUTE_PROGRESS_DISTANCE = 0.5D;
    private static final double ROUTE_DIVERGENCE_DISTANCE = 96.0D;
    private static final long DOCK_TELEMETRY_INTERVAL = 10L;
    private static final long RUNTIME_VALIDATION_INTERVAL = 5L;
    private static final long DOCK_HEARTBEAT_INTERVAL = 10L;
    private static final double LANDING_ZONE_CLEARANCE = 0.5D;
    private static final double PARK_ARRIVAL_TOLERANCE = 1.0D;
    private static final double QUEUE_HOLD_CAPTURE_RADIUS = 1.5D;
    private static final double QUEUE_ROUTE_CLEARANCE_PADDING = 4.0D;
    private static final long METRICS_FUEL_REFRESH_TICKS = 20L;
    private static final int MAX_MANIFEST_STOP_OUTPUTS = 64;
    private static final Set<ShippingScheduleRuntime> LIVE_RUNTIMES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shipping schedule controller
    private final AdvancedContraptionControllerBlockEntity controller;
    // Current schedule
    private @Nullable Schedule schedule;
    // Current pilot id
    private @Nullable UUID pilotId;
    // Current dock id
    private @Nullable UUID currentDockId;
    // Attached dock id
    private @Nullable UUID attachedDockId;
    // Current diverted target id
    private @Nullable UUID divertedTargetId;
    // Current auto refuel
    private ShippingAutoRefuelSettings autoRefuel = ShippingAutoRefuelSettings.DEFAULT;
    // Tracks whether auto refuel is active
    private boolean autoRefuelActive;
    // Tracks whether auto refuel returning is set
    private boolean autoRefuelReturning;
    // Current auto refuel resume entry
    private int autoRefuelResumeEntry = -1;
    // Current auto refuel resume phase
    private Phase autoRefuelResumePhase = Phase.PRE_TRANSIT;
    // Current auto refuel resume dock id
    private @Nullable UUID autoRefuelResumeDockId;
    // Current auto refuel resume condition progress
    private int[] autoRefuelResumeConditionProgress = new int[0];
    // Current auto refuel resume condition started
    private long[] autoRefuelResumeConditionStarted = new long[0];
    // Current auto refuel resume last cargo exchange
    private long autoRefuelResumeLastCargoExchange;
    // Auto refuel started tick
    private long autoRefuelStartedTick;
    // Current phase
    private Phase phase = Phase.IDLE;
    // Current phase before pause
    private Phase phaseBeforePause = Phase.PRE_TRANSIT;
    // Current entry
    private int currentEntry;
    // Tracked coupling endpoints
    private List<ShipCouplerBlockEntity.EndpointKey> couplingEndpoints = List.of();
    // Current route throttle
    private double routeThrottle = 0.6D;
    // Route speed
    private double routeSpeed = speedForThrottle(routeThrottle);
    // Current ship connector index
    private int currentShipConnectorIndex = -1;
    // Current connectorless hover target
    private @Nullable Vec3 connectorlessHoverTarget;
    // Current parking zone id
    private @Nullable UUID currentParkingZoneId;
    // Current parking target
    private @Nullable Vec3 parkingTarget;
    // Current parking kind
    private @Nullable ParkingKind parkingKind;
    // Current dock queue landing zone dock id
    private @Nullable UUID currentHoldingDockId;
    // Current dock queue landing zone id
    private @Nullable UUID currentHoldingZoneId;
    // Phase started tick
    private long phaseStartedTick;
    // Last target refresh tick
    private long lastTargetRefreshTick = Long.MIN_VALUE;
    // Last docking progress tick
    private long lastDockingProgressTick = Long.MIN_VALUE;
    // Current best docking distance
    private double bestDockingDistance = Double.POSITIVE_INFINITY;
    // Tracks whether magnetic capture is active
    private boolean magneticCaptureActive;
    // Last route progress tick
    private long lastRouteProgressTick = Long.MIN_VALUE;
    // Current best route distance
    private double bestRouteDistance = Double.POSITIVE_INFINITY;
    // Docking retry count
    private int dockingRetryCount;
    // Current title
    private String title = "";
    // Current shipping schedule status
    private String status = "No shipping schedule";
    // Last graph command
    private String lastGraphCommand = "";
    // Current condition progress
    private int[] conditionProgress = new int[0];
    // Current condition started
    private long[] conditionStarted = new long[0];
    // Last cargo exchange tick
    private long lastCargoExchangeTick;
    // Last dock telemetry tick
    private long lastDockTelemetryTick = Long.MIN_VALUE;
    // Last runtime validation tick
    private long lastRuntimeValidationTick = Long.MIN_VALUE;
    // Last dock heartbeat tick
    private long lastDockHeartbeatTick = Long.MIN_VALUE;
    // Last travel metrics tick
    private long lastTravelMetricsTick = Long.MIN_VALUE;
    // Last metrics fuel tick
    private long lastMetricsFuelTick = Long.MIN_VALUE;
    // Cached travel metrics
    private @Nullable TravelMetrics cachedTravelMetrics;
    // Cached metrics fuel
    private ShipCargoAutomation.FuelStatus cachedMetricsFuel =
            new ShipCargoAutomation.FuelStatus(0.0D, 0.0D, 0.0D, false);
    // Last stop name
    private String lastStopName = "";
    // Tracks whether queue after undocking is set
    private boolean queueAfterUndocking;
    // Continue toward the coarse pre-calculated dock terminal after undocking.
    private boolean routeTerminalTransitPending;
    // Current restored dock ownership
    private @Nullable UUID restoredDockOwnership;
    // Cached route dock
    private final Map<UUID, ShipDockRegistry.Dock> routeDockCache = new HashMap<>();
    // Cached route candidate
    private final Map<Integer, List<UUID>> routeCandidateCache = new HashMap<>();
    // Cached connector choice
    private final Map<DockConnectorChoice, ShipDockRegistry.ConnectorTarget> connectorChoiceCache =
            new HashMap<>();
    // Route dock live refresh tick count
    private final Map<UUID, Long> routeDockLiveRefreshTicks = new HashMap<>();
    // Pending dock candidates
    private List<UUID> pendingDockCandidates = List.of();
    // Docking slots temporarily excluded after failed approaches
    private final Map<ShipDockScheduler.DockSlot, Long> failedDockingSlots = new HashMap<>();
    // Current route cache revision
    private long routeCacheRevision = Long.MIN_VALUE;
    // Cached pilot
    private @Nullable LivingEntity cachedPilot;
    // Tracks whether the blaze burner is the pilot
    private boolean blazeBurnerPilot;
    // Tracks whether runtime is validated
    private boolean runtimeValidated;
    // Tracks whether shutdown snapshot is pending
    private boolean shutdownSnapshotPending;
    // Invalid pilot since tick
    private long invalidPilotSinceTick = Long.MIN_VALUE;
    // Tracks whether resume lifecycle pause is pending
    private boolean resumeLifecyclePausePending;
    // Tracks whether a recovery brake/hover command set must be cleared before motion resumes
    private boolean dockingRecoveryControlActive;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping schedule
    public ShippingScheduleRuntime(AdvancedContraptionControllerBlockEntity controller) {
        this.controller = controller;
        LIVE_RUNTIMES.add(this);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Close every server shipping runtime
    public static void closeAllForServer(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<ShippingScheduleRuntime> runtimes;
        synchronized (LIVE_RUNTIMES) {
            runtimes = List.copyOf(LIVE_RUNTIMES);
        }
        for (ShippingScheduleRuntime runtime : runtimes) {
            Level level = runtime.controller.getLevel();
            if (level != null && level.getServer() == server) {
                runtime.prepareForServerShutdown();
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the shipping schedule
    public void tick() {
        Level level = controller.getLevel();
        if (shutdownSnapshotPending || level == null || level.isClientSide || schedule == null) {
            return;
        }
        long gameTime = level.getGameTime();
        int spread = Math.max(1, Math.min(4,
                CTConfigs.SERVER.shippingScheduleWorkSpreadTicks.get()));
        if (intervalElapsed(gameTime, lastRuntimeValidationTick,
                Math.max(RUNTIME_VALIDATION_INTERVAL, spread))) {
            lastRuntimeValidationTick = gameTime;
            validateAndRefreshRuntime();
        }
        if (!runtimeValidated) {
            return;
        }
        publishDockTelemetry();
        if (intervalElapsed(gameTime, lastDockHeartbeatTick, DOCK_HEARTBEAT_INTERVAL)) {
            lastDockHeartbeatTick = gameTime;
            heartbeatDockReservation();
        }
        if (spread > 1 && Math.floorMod(gameTime
                + Long.hashCode(controller.getBlockPos().asLong()), spread) != 0) {
            return;
        }
        tickSchedulePhase();
        controller.releaseShipControlAfterLoad();
    }

    // Resume the schedule after loading
    void resumeAfterLoad() {
        LIVE_RUNTIMES.add(this);
        shutdownSnapshotPending = false;
        runtimeValidated = false;
        cachedPilot = null;
        invalidPilotSinceTick = Long.MIN_VALUE;
        lastTargetRefreshTick = Long.MIN_VALUE;
        lastDockingProgressTick = Long.MIN_VALUE;
        resetRouteProgress();
        lastDockTelemetryTick = Long.MIN_VALUE;
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        if (phase == Phase.TRANSITING || phase == Phase.NAVIGATING
                || phase == Phase.WAITING_FOR_DOCK || phase == Phase.PARKING
                || phase == Phase.WAITING_FOR_PARK) {
            phase = Phase.PRE_TRANSIT;
            routeTerminalTransitPending = false;
            status = "Resuming shipping schedule navigation after load";
        }
        invalidateRouteCache();
    }

    // Force the resume after load
    void forceResumeAfterLoad() {
        if (schedule == null || schedule.entries.isEmpty()) {
            return;
        }
        shutdownSnapshotPending = false;
        runtimeValidated = false;
        cachedPilot = null;
        invalidPilotSinceTick = Long.MIN_VALUE;
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        if (resumeLifecyclePausePending || phase == Phase.TRANSITING || phase == Phase.NAVIGATING
                || phase == Phase.WAITING_FOR_DOCK || phase == Phase.PARKING
                || phase == Phase.WAITING_FOR_PARK
                || phase == Phase.INITIALIZING || phase == Phase.IDLE) {
            phase = Phase.PRE_TRANSIT;
            routeTerminalTransitPending = false;
            status = "Resuming shipping schedule after load timeout";
            resumeLifecyclePausePending = false;
        }
        invalidateRouteCache();
        controller.setChanged();
    }

    // Suspend the chunk unload
    void suspendForChunkUnload() {
        runtimeValidated = false;
        cachedPilot = null;
        invalidPilotSinceTick = Long.MIN_VALUE;
        lastTargetRefreshTick = Long.MIN_VALUE;
        lastDockingProgressTick = Long.MIN_VALUE;
        resetRouteProgress();
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        boolean preserveParkingState = currentParkInstruction() != null
                && currentDockId != null && currentParkingZoneId != null
                && parkingTarget != null;
        if (schedule != null && phase != Phase.IDLE && phase != Phase.PAUSED
                && phase != Phase.COUPLING && phase != Phase.DONE) {
            clearDockTelemetry();
            if (!preserveParkingState) {
                stopDockingForShutdown();
            }
            releaseAllReservations();
            if (!preserveParkingState) {
                currentDockId = null;
                clearParkingState();
            }
            attachedDockId = null;
            currentShipConnectorIndex = -1;
            connectorlessHoverTarget = null;
            queueAfterUndocking = false;
            routeTerminalTransitPending = false;
            magneticCaptureActive = false;
            restoredDockOwnership = null;
            pendingDockCandidates = List.of();
            if (phase != Phase.PARKED) {
                phase = Phase.PRE_TRANSIT;
            }
            status = "Resuming shipping schedule after chunk reload";
        }
        invalidateRouteCache();
    }

    // Validate and refresh the runtime
    private boolean validateAndRefreshRuntime() {
        if (schedule.entries.isEmpty()) {
            controller.releaseShipControlAfterLoad();
            clearRuntimeState("No shipping schedule");
            runtimeValidated = false;
            return false;
        }
        PilotState pilotState = pilotState();
        if (pilotState != PilotState.VALID) {
            if (pilotState == PilotState.INVALID && !pilotValidationGraceActive()) {
                controller.releaseShipControlAfterLoad();
                clearRuntimeState("Shipping schedule removed: its pilot is no longer available");
                runtimeValidated = false;
                return false;
            }
            if (pilotId == null) {
                status = "Waiting for a shipping pilot assignment";
                runtimeValidated = false;
                return false;
            }
            status = pilotState == PilotState.UNAVAILABLE
                    ? "Shipping route active while its pilot loads"
                    : "Shipping route active while its pilot seat restores";
        }
        invalidPilotSinceTick = Long.MIN_VALUE;
        refreshRouteCache();
        if (!controller.hasShipControlModule()) {
            if (controller.isShipControlRestoringAfterLoad()) {
                status = "Waiting for ship control module to load";
                runtimeValidated = false;
                return false;
            }
            if (phase == Phase.DOCKING) {
                cancelDockingAttempt(cachedDock(currentDockId));
            }
            releaseAllReservations();
            resetRouteProgress();
            phase = Phase.PAUSED;
            status = "Shipping schedule paused: ship control module is not attached";
            runtimeValidated = false;
            return false;
        }
        if (!controller.getShipControlGraphValue("ready").asBoolean()) {
            if (controller.isShipControlRestoringAfterLoad()) {
                status = "Waiting for ship control module initialization to restore";
                runtimeValidated = false;
                return false;
            }
            if (phase == Phase.DOCKING) {
                cancelDockingAttempt(cachedDock(currentDockId));
            }
            releaseAllReservations();
            resetRouteProgress();
            phase = Phase.PAUSED;
            status = "Shipping schedule paused: initialize the ship control module first";
            runtimeValidated = false;
            return false;
        }
        if (resumeLifecyclePausePending) {
            phase = Phase.PRE_TRANSIT;
            status = "Resuming shipping schedule after ship control restored";
            resumeLifecyclePausePending = false;
        }
        if (phase == Phase.IDLE || phase == Phase.INITIALIZING) {
            phase = Phase.PRE_TRANSIT;
        }
        if (currentEntry < firstOperationalEntry() || currentEntry >= schedule.entries.size()) {
            currentEntry = firstOperationalEntry();
        }
        runtimeValidated = true;
        return true;
    }

    // Update the schedule phase
    private void tickSchedulePhase() {
        if (autoRefuelActive && phase == Phase.PRE_TRANSIT) {
            restartAutoRefuelRoute();
            return;
        }
        if (startAutoRefuelIfNeeded()) {
            return;
        }
        switch (phase) {
            case PRE_TRANSIT -> startCurrentEntry();
            case COUPLING -> tickCoupling();
            case UNDOCKING -> tickUndocking();
            case WAITING_FOR_DOCK -> tickWaitingForDock();
            case WAITING_FOR_PARK -> tickWaitingForPark();
            case TRANSITING -> tickRouteTerminalTransit();
            case NAVIGATING -> tickNavigation();
            case PARKING -> tickParking();
            case DOCKING -> tickDocking();
            case DOCKED, WAITING -> tickDocked();
            case PARKED -> tickParked();
            case DONE -> maintainCompletedStop();
            case IDLE, INITIALIZING, PAUSED -> {
            }
        }
    }

    // Start the auto refuel if needed
    private boolean startAutoRefuelIfNeeded() {
        boolean retryingPreviousRefuelPause = phase == Phase.PAUSED
                && status.startsWith("Auto Refuel paused the schedule");
        if (!autoRefuel.enabled() || autoRefuelActive || controller.getLevel() == null
                || phase == Phase.IDLE || phase == Phase.INITIALIZING
                || phase == Phase.COUPLING || isParkingPhase()
                || phase == Phase.PAUSED && !retryingPreviousRefuelPause || phase == Phase.DONE) {
            return false;
        }
        ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(
                controller, routeThrottle);
        if (fuel.infinite() || fuel.capacity() <= 0.0D
                || !autoRefuel.shouldRefuel(fuel.ratio())) {
            return false;
        }
        List<ShipDockRegistry.Dock> candidates = autoRefuelCandidates(fuel);
        if (candidates.isEmpty()) {
            status = "Auto Refuel is waiting for a matching fuel dock: "
                    + autoRefuel.dockFilter();
            controller.setChanged();
            return false;
        }

        autoRefuelActive = true;
        autoRefuelReturning = false;
        autoRefuelResumeEntry = currentEntry;
        autoRefuelResumePhase = retryingPreviousRefuelPause ? phaseBeforePause : phase;
        autoRefuelResumeDockId = currentDockId;
        autoRefuelResumeConditionProgress = Arrays.copyOf(
                conditionProgress, conditionProgress.length);
        autoRefuelResumeConditionStarted = Arrays.copyOf(
                conditionStarted, conditionStarted.length);
        autoRefuelResumeLastCargoExchange = lastCargoExchangeTick;
        autoRefuelStartedTick = controller.getLevel().getGameTime();
        if (phase == Phase.DOCKING && currentDockId != null) {
            cancelDockingAttempt(cachedDock(currentDockId));
        }
        divertedTargetId = null;
        beginRoute(candidates, false);
        status = "Auto Refuel interrupted the schedule at "
                + Math.round(fuel.ratio() * 100.0D) + "% fuel";
        controller.setChanged();
        return true;
    }

    // Restart the auto refuel route
    private void restartAutoRefuelRoute() {
        if (controller.getLevel() == null) {
            return;
        }
        if (autoRefuelReturning) {
            ShipDockRegistry.Dock resumeDock = cachedDock(autoRefuelResumeDockId);
            if (resumeDock != null) {
                beginRoute(List.of(resumeDock), false);
                return;
            }
            abortAutoRefuel("Auto Refuel paused the schedule: the interrupted dock is unavailable");
            return;
        }
        List<ShipDockRegistry.Dock> candidates = autoRefuelCandidates(
                ShipCargoAutomation.fuelStatus(controller, routeThrottle));
        if (candidates.isEmpty()) {
            abortAutoRefuel("Auto Refuel paused the schedule: no reachable fuel dock matches "
                    + autoRefuel.dockFilter());
            return;
        }
        beginRoute(candidates, false);
    }

    // Get the auto refuel candidates
    private List<ShipDockRegistry.Dock> autoRefuelCandidates(
            ShipCargoAutomation.FuelStatus fuel
    ) {
        Vec3 origin = currentPosition();
        refreshRouteCache();
        return routeDockCache.values().stream()
                .filter(ShipDockRegistry.Dock::refuel)
                .filter(dock -> ShipDockRegistry.addressMatches(
                        dock.name(), autoRefuel.dockFilter()))
                .sorted(Comparator
                        .comparing((ShipDockRegistry.Dock dock) -> !fuel.canReach(
                                origin.distanceTo(dock.approach()), routeSpeed))
                        .thenComparing(dock -> !ShipCargoAutomation.dockHasFuelSupply(
                                controller, dock))
                        .thenComparingDouble((ShipDockRegistry.Dock dock) ->
                                dock.approach().distanceToSqr(origin))
                        .thenComparing(ShipDockRegistry.Dock::name)
                        .thenComparing(ShipDockRegistry.Dock::id))
                .toList();
    }

    // Get the first operational entry
    private int firstOperationalEntry() {
        return schedule != null && !schedule.entries.isEmpty()
                && schedule.entries.getFirst().instruction instanceof RefuelIfInstruction ? 1 : 0;
    }

    // Clear the auto refuel interruption
    private void clearAutoRefuelInterruption() {
        autoRefuelActive = false;
        autoRefuelReturning = false;
        autoRefuelResumeEntry = -1;
        autoRefuelResumePhase = Phase.PRE_TRANSIT;
        autoRefuelResumeDockId = null;
        autoRefuelResumeConditionProgress = new int[0];
        autoRefuelResumeConditionStarted = new long[0];
        autoRefuelResumeLastCargoExchange = 0L;
        autoRefuelStartedTick = 0L;
    }

    // Prepare the server shutdown
    public void prepareForServerShutdown() {
        if (shutdownSnapshotPending) {
            return;
        }
        boolean preserveParkingState = currentParkInstruction() != null
                && currentDockId != null && currentParkingZoneId != null
                && parkingTarget != null;
        boolean preserveParkedPhase = preserveParkingState
                && (phase == Phase.PARKED || phase == Phase.DONE);
        shutdownSnapshotPending = true;
        runtimeValidated = false;
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        cachedPilot = null;
        runShutdownCleanup("clear dock telemetry", this::clearDockTelemetry);
        runShutdownCleanup("release the dock reservation", this::releaseAllReservations);
        runShutdownCleanup("disengage docking connectors", this::stopDockingForShutdown);
        if (schedule != null && phase != Phase.COUPLING && !preserveParkedPhase) {
            phase = Phase.PRE_TRANSIT;
        }
        if (!preserveParkingState) {
            currentDockId = null;
            clearParkingState();
        }
        attachedDockId = null;
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        clearHoldingState();
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        dockingRetryCount = 0;
        lastDockingProgressTick = Long.MIN_VALUE;
        bestDockingDistance = Double.POSITIVE_INFINITY;
        magneticCaptureActive = false;
        resetRouteProgress();
        restoredDockOwnership = null;
        pendingDockCandidates = List.of();
        invalidateRouteCache();
        LIVE_RUNTIMES.remove(this);
    }

    // Stop the docking for shutdown
    private void stopDockingForShutdown() {
        if (currentParkInstruction() != null && currentParkingZoneId != null) {
            controller.activateShipDockingConnector(-1);
            return;
        }
        UUID activeDockId = attachedDockId == null ? currentDockId : attachedDockId;
        ShipDockRegistry.Dock activeDock = routeDockCache.get(activeDockId);
        if (activeDock == null && activeDockId != null && controller.getLevel() != null
                && controller.getLevel().getServer() != null) {
            activeDock = ShipDockRegistry.get(controller.getLevel().getServer()).get(activeDockId);
        }
        if (activeDock != null) {
            ShipDockRegistry.Dock dock = activeDock;
            activeDock = dock.connectorTargets().stream()
                    .filter(target -> DockingConnectorAutomation.hasConnectionState(
                            DockingConnectorAutomation.resolve(
                                    controller.getLevel(), target.subLevelId(), target.pos())))
                    .findFirst()
                    .map(dock::withConnector)
                    .orElse(dock);
        }
        if (activeDock != null) {
            BlockEntityPair pair = connectorPair(activeDock);
            DockingConnectorAutomation.configureTransfers(
                    pair.dock(), false, false, "", false, false);
            DockingConnectorAutomation.resetTransfers(pair.ship());
            DockingConnectorAutomation.disengage(pair.ship(), pair.dock());
        }
        controller.activateShipDockingConnector(-1);
    }

    // Run the shutdown cleanup
    private static void runShutdownCleanup(String action, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException err) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to " + action + " while stopping a shipping schedule", err);
        }
    }

    // Install the shipping schedule
    public boolean install(Schedule incoming, @Nullable UUID pilot) {
        return install(incoming, pilot, ShippingAutoRefuelSettings.DEFAULT);
    }

    // Install the shipping schedule
    public boolean install(Schedule incoming, @Nullable UUID pilot,
                           ShippingAutoRefuelSettings autoRefuel) {
        return install(incoming, pilot, autoRefuel, false);
    }

    // Install the shipping schedule
    public boolean install(Schedule incoming, @Nullable UUID pilot,
                           ShippingAutoRefuelSettings autoRefuel, boolean blazeBurnerPilot) {
        if (shutdownSnapshotPending || incoming == null || incoming.entries.isEmpty()
                || controller.getLevel() == null) {
            return false;
        }
        clearDockTelemetry();
        disengageAttachedConnectors();
        releaseAllReservations();
        resetRouteProgress();
        HolderLookup.Provider registries = controller.getLevel().registryAccess();
        schedule = Schedule.fromTag(registries, incoming.write(registries));
        pilotId = pilot;
        this.blazeBurnerPilot = blazeBurnerPilot;
        ShippingAutoRefuelSettings manifestPolicy = ShippingAutoRefuelSettings.fromSchedule(schedule);
        this.autoRefuel = manifestPolicy.enabled() ? manifestPolicy : ShippingAutoRefuelSettings.DEFAULT;
        cachedPilot = null;
        int firstEntry = firstOperationalEntry();
        if (firstEntry >= schedule.entries.size()) {
            schedule = null;
            pilotId = null;
            this.blazeBurnerPilot = false;
            return false;
        }
        currentEntry = Math.max(firstEntry,
                Math.min(schedule.savedProgress, schedule.entries.size() - 1));
        couplingEndpoints = List.of();
        currentDockId = null;
        attachedDockId = null;
        divertedTargetId = null;
        clearAutoRefuelInterruption();
        routeThrottle = 0.6D;
        routeSpeed = speedForThrottle(routeThrottle);
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        clearParkingState();
        clearHoldingState();
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        restoredDockOwnership = null;
        pendingDockCandidates = List.of();
        failedDockingSlots.clear();
        lastDockTelemetryTick = Long.MIN_VALUE;
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        lastTravelMetricsTick = Long.MIN_VALUE;
        lastMetricsFuelTick = Long.MIN_VALUE;
        cachedTravelMetrics = null;
        lastStopName = "";
        lastGraphCommand = "";
        phase = Phase.PRE_TRANSIT;
        status = "Shipping schedule accepted";
        runtimeValidated = false;
        shutdownSnapshotPending = false;
        invalidateRouteCache();
        refreshRouteCache();
        controller.setChanged();
        return true;
    }

    // Replace an already installed schedule without dropping its pilot assignment or requiring a schedule item round trip.
    public boolean replaceSchedule(Schedule incoming) {
        if (shutdownSnapshotPending || schedule == null || incoming == null || incoming.entries.isEmpty()
                || controller.getLevel() == null) {
            return false;
        }
        HolderLookup.Provider registries = controller.getLevel().registryAccess();
        Schedule replacement = Schedule.fromTag(registries, incoming.write(registries));
        if (replacement.entries.isEmpty()) {
            return false;
        }
        int previousEntry = currentEntry;
        schedule = replacement;
        autoRefuel = ShippingAutoRefuelSettings.fromSchedule(replacement);
        currentEntry = Math.max(firstOperationalEntry(), Math.min(previousEntry, replacement.entries.size() - 1));
        if (currentEntry >= replacement.entries.size()) {
            currentEntry = firstOperationalEntry();
        }
        conditionProgress = new int[0];
        conditionStarted = new long[0];
        clearAutoRefuelInterruption();
        clearHoldingState();
        invalidateRouteCache();
        refreshRouteCache();
        status = "Shipping schedule updated from ACC";
        controller.setChanged();
        return true;
    }

    // Copy the live schedule for the ACC's paired Scratch graph.
    public @Nullable Schedule scheduleCopy() {
        if (schedule == null || controller.getLevel() == null) {
            return null;
        }
        return Schedule.fromTag(controller.getLevel().registryAccess(),
                schedule.write(controller.getLevel().registryAccess()));
    }

    // Remove the shipping schedule
    public ItemStack remove() {
        if (shutdownSnapshotPending || schedule == null || controller.getLevel() == null) {
            return ItemStack.EMPTY;
        }
        schedule.savedProgress = Math.max(0, currentEntry);
        ItemStack res = new ItemStack(CTItems.SHIPPING_SCHEDULE.get());
        res.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(controller.getLevel().registryAccess()));
        ShippingAutoRefuelSettings.write(res, autoRefuel);
        clearRuntimeState("No shipping schedule");
        return res;
    }

    // Check if this has schedule
    public boolean hasSchedule() {
        return schedule != null && !schedule.entries.isEmpty();
    }

    // Check if this has blaze burner pilot
    public boolean hasBlazeBurnerPilot() {
        return hasSchedule() && blazeBurnerPilot;
    }

    // Check if this requires control after load
    boolean requiresControlAfterLoad() {
        return schedule != null && !schedule.entries.isEmpty() && pilotId != null
                && (phase != Phase.DONE || connectorlessHoverTarget != null
                || parkingTarget != null);
    }

    // Sanitize the schematic payload
    static void sanitizeSchematicPayload(CompoundTag controllerData) {
        if (controllerData == null
                || !controllerData.contains("ShippingScheduleRuntime", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag runtime = controllerData.getCompound("ShippingScheduleRuntime");
        runtime.remove("Pilot");
        runtime.remove("BlazeBurnerPilot");
        runtime.putBoolean("ShutdownSnapshot", false);
    }

    // Detach the pilot for schematic import
    void detachPilotForSchematicImport() {
        pilotId = null;
        blazeBurnerPilot = false;
        cachedPilot = null;
        invalidPilotSinceTick = Long.MIN_VALUE;
        runtimeValidated = false;
        shutdownSnapshotPending = false;
        resumeLifecyclePausePending = false;
    }

    // Copy the schedule item
    public ItemStack copyScheduleItem() {
        if (schedule == null || controller.getLevel() == null) return ItemStack.EMPTY;
        ItemStack res = new ItemStack(CTItems.SHIPPING_SCHEDULE.get());
        res.set(AllDataComponents.TRAIN_SCHEDULE,
                schedule.write(controller.getLevel().registryAccess()));
        ShippingAutoRefuelSettings.write(res, autoRefuel);
        return res;
    }

    // Get the pilot id
    public @Nullable UUID pilotId() {
        return pilotId;
    }

    // Get the status
    public String status() {
        return status;
    }

    // Run the graph command
    public boolean executeGraphCommand(String nodeType) {
        if (shutdownSnapshotPending) {
            return false;
        }
        String command = nodeType == null ? "" : nodeType;
        if (command.equals(lastGraphCommand) && commandStateIsUnchanged(command)) {
            return schedule != null;
        }
        boolean success = switch (command) {
            case "shipping_pause" -> pause();
            case "shipping_resume" -> resume();
            case "shipping_stop" -> stop();
            case "shipping_restart" -> restart();
            case "shipping_skip" -> skip();
            default -> false;
        };
        if (success) {
            lastGraphCommand = command;
            controller.setChanged();
        }
        return success;
    }

    // Invoke an explicit UI Start action without graph-command de-duplication.
    // Repeated Start clicks are intentionally allowed to restart an SCM route.
    public boolean restartFromController() {
        if (shutdownSnapshotPending) return false;
        boolean restarted = restart();
        if (restarted) {
            lastGraphCommand = "";
            controller.setChanged();
        }
        return restarted;
    }

    // Check if the command state is unchanged
    private boolean commandStateIsUnchanged(String command) {
        return switch (command) {
            case "shipping_pause" -> phase == Phase.PAUSED;
            case "shipping_resume" -> phase != Phase.PAUSED;
            case "shipping_stop" -> phase == Phase.PAUSED && currentEntry == firstOperationalEntry();
            case "shipping_restart", "shipping_skip" -> true;
            default -> false;
        };
    }

    // Pause the shipping schedule
    private boolean pause() {
        if (schedule == null || phase == Phase.PAUSED) {
            return schedule != null;
        }
        suspendConnectorTransfers();
        if (phase == Phase.DOCKING) {
            cancelDockingAttempt(cachedDock(currentDockId));
        }
        releaseAllReservations();
        resetRouteProgress();
        phaseBeforePause = phase == Phase.DOCKING
                || phase == Phase.TRANSITING || phase == Phase.NAVIGATING
                || phase == Phase.WAITING_FOR_DOCK
                || phase == Phase.PARKING || phase == Phase.PARKED
                || phase == Phase.WAITING_FOR_PARK
                || phase == Phase.UNDOCKING ? Phase.PRE_TRANSIT : phase;
        phase = Phase.PAUSED;
        status = "Shipping schedule paused by the controller graph";
        controller.executeShipControlGraphCommand(
                "shipping_schedule_pause", "ship_stop", Map.of());
        controller.setChanged();
        return true;
    }

    // Resume the shipping schedule
    private boolean resume() {
        if (schedule == null) {
            return false;
        }
        if (phase != Phase.PAUSED) {
            return true;
        }
        phase = phaseBeforePause == Phase.PAUSED || phaseBeforePause == Phase.IDLE
                || phaseBeforePause == Phase.INITIALIZING || phaseBeforePause == Phase.DONE
                ? Phase.PRE_TRANSIT : phaseBeforePause;
        status = "Shipping schedule resumed by the controller graph";
        phaseStartedTick = controller.getLevel() == null
                ? 0L : controller.getLevel().getGameTime();
        controller.setChanged();
        return true;
    }

    // Stop keeps the SCM-owned schedule installed, returns its cursor to the
    // first operational entry, and leaves it paused until the player resumes
    // or starts it again. This deliberately does not manufacture or require a
    // shipping-schedule item.
    private boolean stop() {
        if (schedule == null) return false;
        if (phase != Phase.PAUSED) pause();
        currentEntry = firstOperationalEntry();
        schedule.savedProgress = currentEntry;
        phaseBeforePause = Phase.PRE_TRANSIT;
        status = "Shipping schedule stopped by the controller";
        controller.setChanged();
        return true;
    }

    // Restart the shipping schedule
    private boolean restart() {
        if (schedule == null) {
            return false;
        }
        controller.executeShipControlGraphCommand(
                "shipping_schedule_reset", "ship_stop", Map.of());
        disengageAttachedConnectors();
        releaseAllReservations();
        resetRouteProgress();
        currentEntry = firstOperationalEntry();
        couplingEndpoints = List.of();
        schedule.savedProgress = currentEntry;
        currentDockId = null;
        divertedTargetId = null;
        clearAutoRefuelInterruption();
        connectorlessHoverTarget = null;
        clearParkingState();
        clearHoldingState();
        pendingDockCandidates = List.of();
        failedDockingSlots.clear();
        routeTerminalTransitPending = false;
        conditionProgress = new int[0];
        conditionStarted = new long[0];
        phase = Phase.PRE_TRANSIT;
        phaseBeforePause = Phase.PRE_TRANSIT;
        status = "Shipping schedule restarted by the controller graph";
        controller.setChanged();
        return true;
    }

    // Skip the current shipping step
    private boolean skip() {
        if (schedule == null || schedule.entries.isEmpty()) {
            return false;
        }
        skipCurrentEntry("Shipping stop skipped by the controller graph");
        return true;
    }

    // Get the CRN display data
    public RailwayNavigatorGraphCompat.ShipDisplayData crnDisplayData(UUID shipId) {
        TravelMetrics metrics = travelMetrics();
        return new RailwayNavigatorGraphCompat.ShipDisplayData(
                shipId,
                metrics.shipName(),
                metrics.scheduleTitle(),
                metrics.active(),
                metrics.pilotPresent(),
                metrics.docked(),
                metrics.waiting(),
                metrics.status(),
                metrics.phase(),
                metrics.currentStop(),
                metrics.targetStop(),
                metrics.nextStop(),
                metrics.currentEntry(),
                metrics.nextEntry(),
                metrics.stopCount(),
                metrics.remainingStops(),
                metrics.etaSeconds(),
                metrics.cruiseSpeed(),
                metrics.fuelRatio(),
                metrics.progressPercent(),
                metrics.distanceToTarget(),
                metrics.position(),
                metrics.targetPosition());
    }

    // Get the graph value
    public AdvancedGraphDocument.Value graphValue(String port) {
        // ------------------------------------TRAVEL METRICS------------------------------------
        TravelMetrics metrics = travelMetrics();
        // -----------------------------------------------------GRAPH OUTPUTS-----------------------------------------------------
        return switch (port == null ? "" : port) {
            case "shipping_active" -> AdvancedGraphDocument.Value.bool(metrics.active());
            case "shipping_pilot_present" ->
                    AdvancedGraphDocument.Value.bool(metrics.pilotPresent());
            case "shipping_docked" -> AdvancedGraphDocument.Value.bool(metrics.docked());
            case "shipping_waiting" -> AdvancedGraphDocument.Value.bool(metrics.waiting());
            case "shipping_diverted" -> AdvancedGraphDocument.Value.bool(metrics.diverted());
            case "shipping_needs_refuel" ->
                    AdvancedGraphDocument.Value.bool(metrics.needsRefuel());
            case "shipping_target_has_connector" ->
                    AdvancedGraphDocument.Value.bool(metrics.targetHasConnector());
            case "shipping_name" -> AdvancedGraphDocument.Value.string(metrics.shipName());
            case "shipping_schedule_title" ->
                    AdvancedGraphDocument.Value.string(metrics.scheduleTitle());
            case "shipping_status" -> AdvancedGraphDocument.Value.string(metrics.status());
            case "shipping_phase" -> AdvancedGraphDocument.Value.string(metrics.phase());
            case "shipping_current_stop" ->
                    AdvancedGraphDocument.Value.string(metrics.currentStop());
            case "shipping_target_stop" ->
                    AdvancedGraphDocument.Value.string(metrics.targetStop());
            case "shipping_next_stop" ->
                    AdvancedGraphDocument.Value.string(metrics.nextStop());
            case "shipping_current_entry" ->
                    AdvancedGraphDocument.Value.number(metrics.currentEntry());
            case "shipping_next_entry" ->
                    AdvancedGraphDocument.Value.number(metrics.nextEntry());
            case "shipping_stop_count" ->
                    AdvancedGraphDocument.Value.number(metrics.stopCount());
            case "shipping_remaining_stops" ->
                    AdvancedGraphDocument.Value.number(metrics.remainingStops());
            case "shipping_progress_percent" ->
                    AdvancedGraphDocument.Value.number(metrics.progressPercent());
            case "shipping_eta_seconds" ->
                    AdvancedGraphDocument.Value.number(metrics.etaSeconds());
            case "shipping_eta_minutes" ->
                    AdvancedGraphDocument.Value.number(metrics.etaSeconds() < 0L
                            ? -1.0D : metrics.etaSeconds() / 60.0D);
            case "shipping_distance_to_target" ->
                    AdvancedGraphDocument.Value.number(metrics.distanceToTarget());
            case "shipping_throttle" ->
                    AdvancedGraphDocument.Value.number(metrics.throttle());
            case "shipping_cruise_speed" ->
                    AdvancedGraphDocument.Value.number(metrics.cruiseSpeed());
            case "shipping_phase_elapsed_seconds" ->
                    AdvancedGraphDocument.Value.number(metrics.phaseElapsedSeconds());
            case "shipping_position_x" ->
                    AdvancedGraphDocument.Value.number(metrics.position().x);
            case "shipping_position_y" ->
                    AdvancedGraphDocument.Value.number(metrics.position().y);
            case "shipping_position_z" ->
                    AdvancedGraphDocument.Value.number(metrics.position().z);
            case "shipping_target_x" ->
                    AdvancedGraphDocument.Value.number(metrics.targetPosition().x);
            case "shipping_target_y" ->
                    AdvancedGraphDocument.Value.number(metrics.targetPosition().y);
            case "shipping_target_z" ->
                    AdvancedGraphDocument.Value.number(metrics.targetPosition().z);
            case "shipping_fuel_amount" ->
                    AdvancedGraphDocument.Value.number(metrics.fuelAmount());
            case "shipping_fuel_capacity" ->
                    AdvancedGraphDocument.Value.number(metrics.fuelCapacity());
            case "shipping_fuel_ratio" ->
                    AdvancedGraphDocument.Value.number(metrics.fuelRatio());
            case "shipping_fuel_reserve" -> AdvancedGraphDocument.Value.number(FUEL_RESERVE);
            case "shipping_fuel_use_per_tick" ->
                    AdvancedGraphDocument.Value.number(metrics.fuelUsePerTick());
            case "shipping_fuel_reserve_seconds" ->
                    AdvancedGraphDocument.Value.number(metrics.fuelReserveSeconds());
            case "shipping_manifest_title" ->
                    AdvancedGraphDocument.Value.string(metrics.scheduleTitle());
            case "shipping_manifest_current_stop" ->
                    AdvancedGraphDocument.Value.string(metrics.currentStop());
            case "shipping_manifest_target_stop" ->
                    AdvancedGraphDocument.Value.string(metrics.targetStop());
            case "shipping_manifest_next_stop" ->
                    AdvancedGraphDocument.Value.string(metrics.nextStop());
            case "shipping_manifest_stop_count" ->
                    AdvancedGraphDocument.Value.number(metrics.stopCount());
            case "shipping_manifest_current_entry" ->
                    AdvancedGraphDocument.Value.number(metrics.currentEntry());
            case "shipping_manifest_next_entry" ->
                    AdvancedGraphDocument.Value.number(metrics.nextEntry());
            case "shipping_manifest_is_cyclic" ->
                    AdvancedGraphDocument.Value.bool(metrics.cyclic());
            case "shipping_manifest_stops" -> manifestStopsValue();
            default -> AdvancedGraphDocument.Value.number(0.0D);
        };
    }

    // Get the travel metrics
    private TravelMetrics travelMetrics() {
        Level level = controller.getLevel();
        long now = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (level != null && cachedTravelMetrics != null && lastTravelMetricsTick == now) {
            return cachedTravelMetrics;
        }
        if (schedule != null) {
            refreshRouteCache();
        }
        if (level != null && intervalElapsed(now, lastMetricsFuelTick,
                METRICS_FUEL_REFRESH_TICKS)) {
            cachedMetricsFuel = ShipCargoAutomation.fuelStatus(controller, routeThrottle);
            lastMetricsFuelTick = now;
        }
        ShipDockRegistry.Dock target = currentTargetDock();
        ShipDockRegistry.Dock next = nextScheduledDock(
                (phase == Phase.DOCKED || phase == Phase.WAITING
                        || phase == Phase.PARKED || isCompletedPark())
                        ? currentEntry + 1 : currentEntry);
        Vec3 pos = currentPosition();
        Vec3 targetPosition = parkingTarget != null && currentParkInstruction() != null
                ? parkingTarget : target == null ? Vec3.ZERO
                : phase == Phase.TRANSITING || routeTerminalTransitPending
                ? scheduleRouteTerminal(target).position() : target.approach();
        double distance = target == null ? 0.0D : pos.distanceTo(targetPosition);
        long eta = estimatedEtaSeconds(distance);
        int entries = schedule == null ? 0 : schedule.entries.size();
        int normalizedEntry = entries == 0 ? 0 : Math.max(0, Math.min(currentEntry, entries - 1));
        int nextEntry = nextScheduledEntry(
                (phase == Phase.DOCKED || phase == Phase.WAITING
                        || phase == Phase.PARKED || isCompletedPark())
                        ? normalizedEntry + 1 : normalizedEntry);
        int stopCount = manifestStopCount();
        int remainingStops = remainingManifestStopCount(normalizedEntry);
        double fuelReserveSeconds = cachedMetricsFuel.infinite()
                || cachedMetricsFuel.usePerTick() <= 0.0D ? -1.0D
                : Math.max(0.0D, (cachedMetricsFuel.amount()
                - cachedMetricsFuel.capacity() * FUEL_RESERVE)
                / cachedMetricsFuel.usePerTick() / 20.0D);
        boolean docked = phase == Phase.DOCKED || phase == Phase.WAITING
                || phase == Phase.PARKED || isCompletedPark();
        cachedTravelMetrics = new TravelMetrics(
                schedule != null, hasValidPilot(), docked,
                phase == Phase.WAITING || phase == Phase.WAITING_FOR_DOCK
                        || phase == Phase.WAITING_FOR_PARK,
                autoRefuelActive || divertedTargetId != null,
                cachedMetricsFuel.capacity() > 0.0D && cachedMetricsFuel.ratio() <= FUEL_RESERVE,
                target != null && currentParkInstruction() == null
                        && usesPhysicalDocking(target),
                controller.getShipName(), title, status, phase.name().toLowerCase(java.util.Locale.ROOT),
                docked && target != null ? target.name()
                        : lastStopName.isBlank() ? "At sea" : lastStopName,
                target == null ? "" : target.name(), next == null ? "" : next.name(),
                normalizedEntry, nextEntry, stopCount, remainingStops,
                entries == 0 ? 0.0D : (normalizedEntry + 1) * 100.0D / entries,
                eta, distance, routeThrottle, routeSpeed,
                level == null ? 0.0D : Math.max(0.0D, now - phaseStartedTick) / 20.0D,
                pos, targetPosition, cachedMetricsFuel.amount(), cachedMetricsFuel.capacity(),
                cachedMetricsFuel.ratio(), cachedMetricsFuel.usePerTick(), fuelReserveSeconds,
                schedule != null && schedule.cyclic);
        lastTravelMetricsTick = now;
        return cachedTravelMetrics;
    }

    // Get the estimated eta seconds
    private long estimatedEtaSeconds(double distance) {
        return switch (phase) {
            case DOCKED, WAITING, PARKED -> 0L;
            case TRANSITING, NAVIGATING, DOCKING, UNDOCKING, WAITING_FOR_DOCK,
                    WAITING_FOR_PARK, PARKING ->
                    Math.max(1L, (long) Math.ceil(distance / Math.max(0.25D, routeSpeed)));
            default -> -1L;
        };
    }

    // Get the current target dock
    private @Nullable ShipDockRegistry.Dock currentTargetDock() {
        if (currentDockId != null) {
            return cachedDock(currentDockId);
        }
        return scheduledDock(currentEntry);
    }

    // Get the scheduled dock
    private @Nullable ShipDockRegistry.Dock scheduledDock(int entryIndex) {
        if (schedule == null || entryIndex < 0 || entryIndex >= schedule.entries.size()) {
            return null;
        }
        return routeCandidateCache.getOrDefault(entryIndex, List.of()).stream()
                .map(routeDockCache::get)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // Get the next scheduled dock
    private @Nullable ShipDockRegistry.Dock nextScheduledDock(int startEntry) {
        int nextEntry = nextScheduledEntry(startEntry);
        return nextEntry < 0 ? null : scheduledDock(nextEntry);
    }

    // Get the next scheduled entry
    private int nextScheduledEntry(int startEntry) {
        if (schedule == null || schedule.entries.isEmpty()) {
            return -1;
        }
        int count = schedule.entries.size();
        for (int offset = 0; offset < count; offset++) {
            int idx = startEntry + offset;
            if (idx >= count) {
                if (!schedule.cyclic) {
                    break;
                }
                idx %= count;
            }
            if (scheduledDock(idx) != null) {
                return idx;
            }
        }
        return -1;
    }

    // Get the manifest stop count
    private int manifestStopCount() {
        if (schedule == null) {
            return 0;
        }
        int count = 0;
        for (int idx = 0; idx < schedule.entries.size(); idx++) {
            if (scheduledDock(idx) != null) {
                count++;
            }
        }
        return count;
    }

    // Get the remaining manifest stop count
    private int remainingManifestStopCount(int startEntry) {
        if (schedule == null || schedule.entries.isEmpty()) {
            return 0;
        }
        int count = schedule.entries.size();
        int remaining = 0;
        for (int offset = 0; offset < count; offset++) {
            int idx = startEntry + offset;
            if (idx >= count) {
                if (!schedule.cyclic) {
                    break;
                }
                idx %= count;
            }
            if (scheduledDock(idx) != null) {
                remaining++;
            }
        }
        return remaining;
    }

    // Get the manifest stops value
    private AdvancedGraphDocument.Value manifestStopsValue() {
        CompoundTag values = new CompoundTag();
        if (schedule == null) {
            return AdvancedGraphDocument.Value.list(values);
        }
        int entries = schedule.entries.size();
        int start = Math.max(0, Math.min(currentEntry, Math.max(0, entries - 1)));
        int emitted = 0;
        for (int offset = 0; offset < entries && emitted < MAX_MANIFEST_STOP_OUTPUTS; offset++) {
            int idx = start + offset;
            if (idx >= entries) {
                if (!schedule.cyclic) {
                    break;
                }
                idx %= entries;
            }
            ShipDockRegistry.Dock dock = scheduledDock(idx);
            if (dock == null) {
                continue;
            }
            CompoundTag stop = new CompoundTag();
            putGraphValue(stop, "entry", AdvancedGraphDocument.Value.number(idx));
            putGraphValue(stop, "name", AdvancedGraphDocument.Value.string(dock.name()));
            putGraphValue(stop, "refuel", AdvancedGraphDocument.Value.bool(dock.refuel()));
            putGraphValue(stop, "restock", AdvancedGraphDocument.Value.bool(dock.restock()));
            putGraphValue(stop, "packages", AdvancedGraphDocument.Value.bool(dock.packages()));
            values.put(Integer.toString(emitted++), graphValueTag(
                    AdvancedGraphDocument.Value.map(stop)));
        }
        return AdvancedGraphDocument.Value.list(values);
    }

    // Put the graph value
    private static void putGraphValue(
            CompoundTag parent, String key, AdvancedGraphDocument.Value val
    ) {
        parent.put(key, graphValueTag(val));
    }

    // Get the graph value tag
    private static CompoundTag graphValueTag(AdvancedGraphDocument.Value val) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", val.type());
        tag.put("Payload", val.payload().copy());
        return tag;
    }

    // Check if this has valid pilot
    private boolean hasValidPilot() {
        return pilotState() == PilotState.VALID;
    }

    // Check if this has present pilot
    boolean hasPresentPilot() {
        return hasValidPilot();
    }

    // Get the pilot state
    private PilotState pilotState() {
        if (blazeBurnerPilot) {
            return ShippingSchedulePilot.isBlazeBurnerPilot(controller)
                    ? PilotState.VALID : PilotState.INVALID;
        }
        LivingEntity pilot = assignedPilot();
        if (pilot == null) {
            return PilotState.UNAVAILABLE;
        }
        return pilot.isAlive() && ShippingSchedulePilot.isSeatedAtController(pilot, controller)
                ? PilotState.VALID : PilotState.INVALID;
    }

    // Check if the pilot validation grace is active
    private boolean pilotValidationGraceActive() {
        Level level = controller.getLevel();
        if (level == null) {
            return true;
        }
        long now = level.getGameTime();
        if (invalidPilotSinceTick == Long.MIN_VALUE || now < invalidPilotSinceTick) {
            invalidPilotSinceTick = now;
        }
        return now - invalidPilotSinceTick < 200L;
    }

    // Get the assigned pilot
    private @Nullable LivingEntity assignedPilot() {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            cachedPilot = null;
            return null;
        }
        if (cachedPilot != null && !cachedPilot.isRemoved()
                && pilotId.equals(cachedPilot.getUUID())) {
            return cachedPilot;
        }
        for (ServerLevel level : controller.getLevel().getServer().getAllLevels()) {
            Entity entity = level.getEntity(pilotId);
            if (entity instanceof LivingEntity pilot) {
                cachedPilot = pilot;
                return pilot;
            }
        }
        cachedPilot = null;
        return null;
    }

    // Clear the runtime state
    private void clearRuntimeState(String nextStatus) {
        ShippingSchedulePilot.clearTrainHat(assignedPilot());
        clearDockTelemetry();
        disengageAttachedConnectors();
        releaseAllReservations();
        resetRouteProgress();
        controller.executeShipControlGraphCommand(
                "shipping_schedule_stop", "ship_stop", Map.of());
        schedule = null;
        autoRefuel = ShippingAutoRefuelSettings.DEFAULT;
        pilotId = null;
        blazeBurnerPilot = false;
        cachedPilot = null;
        currentDockId = null;
        attachedDockId = null;
        divertedTargetId = null;
        clearAutoRefuelInterruption();
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        restoredDockOwnership = null;
        pendingDockCandidates = List.of();
        failedDockingSlots.clear();
        cachedTravelMetrics = null;
        lastTravelMetricsTick = Long.MIN_VALUE;
        lastStopName = "";
        lastGraphCommand = "";
        conditionProgress = new int[0];
        conditionStarted = new long[0];
        couplingEndpoints = List.of();
        phase = Phase.IDLE;
        status = nextStatus;
        runtimeValidated = false;
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        shutdownSnapshotPending = false;
        invalidPilotSinceTick = Long.MIN_VALUE;
        resumeLifecyclePausePending = false;
        invalidateRouteCache();
        controller.setChanged();
    }

    // Publish the dock telemetry
    private void publishDockTelemetry() {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide || level.getServer() == null
                || pilotId == null || !intervalElapsed(
                level.getGameTime(), lastDockTelemetryTick, DOCK_TELEMETRY_INTERVAL)) {
            return;
        }
        lastDockTelemetryTick = level.getGameTime();
        Set<UUID> manifestDocks = new HashSet<>();
        routeCandidateCache.values().forEach(manifestDocks::addAll);
        manifestDocks.addAll(pendingDockCandidates);
        if (currentDockId != null) {
            manifestDocks.add(currentDockId);
        }
        if (attachedDockId != null) {
            manifestDocks.add(attachedDockId);
        }
        if (divertedTargetId != null) {
            manifestDocks.add(divertedTargetId);
        }
        if (manifestDocks.isEmpty()) {
            return;
        }

        TravelMetrics metrics = travelMetrics();
        ShipDockRegistry.ShipTelemetry telemetry = new ShipDockRegistry.ShipTelemetry(
                pilotId, metrics.shipName(), metrics.currentStop(), metrics.targetStop(),
                metrics.nextStop(), metrics.status(), metrics.phase(), metrics.position(),
                metrics.distanceToTarget(), metrics.etaSeconds(), metrics.fuelRatio(),
                metrics.progressPercent(), System.currentTimeMillis());
        ShipDockRegistry registry = ShipDockRegistry.get(level.getServer());
        for (UUID dockId : manifestDocks) {
            registry.publishTelemetry(dockId, telemetry);
        }
    }

    // Clear the dock telemetry
    private void clearDockTelemetry() {
        if (pilotId != null && controller.getLevel() != null
                && controller.getLevel().getServer() != null) {
            ShipDockRegistry.get(controller.getLevel().getServer()).clearTelemetry(pilotId);
        }
        lastDockTelemetryTick = Long.MIN_VALUE;
    }

    // Invalidate the route cache
    private void invalidateRouteCache() {
        routeDockCache.clear();
        routeCandidateCache.clear();
        connectorChoiceCache.clear();
        routeDockLiveRefreshTicks.clear();
        routeCacheRevision = Long.MIN_VALUE;
    }

    // Refresh the route cache
    private void refreshRouteCache() {
        if (schedule == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        ShipDockRegistry registry = ShipDockRegistry.get(
                controller.getLevel().getServer());
        long registryRevision = registry.revision();
        if (routeCacheRevision == registryRevision) {
            return;
        }
        List<ShipDockRegistry.Dock> all = registry.allIn(
                controller.getLevel().dimension().location());
        routeDockCache.clear();
        routeCandidateCache.clear();
        for (ShipDockRegistry.Dock dock : all) {
            routeDockCache.put(dock.id(), dock);
        }
        long gameTime = controller.getLevel().getGameTime();
        routeDockCache.keySet().forEach(id -> routeDockLiveRefreshTicks.put(id, gameTime));
        for (int idx = 0; idx < schedule.entries.size(); idx++) {
            ScheduleEntry entry = schedule.entries.get(idx);
            List<UUID> candidates;
            if (entry.instruction instanceof DestinationInstruction destination) {
                candidates = all.stream()
                        .filter(dock -> ShipDockRegistry.addressMatches(
                                dock.name(), destination.getFilter()))
                        .map(ShipDockRegistry.Dock::id)
                        .toList();
            } else if (entry.instruction instanceof RefuelIfInstruction refuel) {
                candidates = all.stream()
                        .filter(dock -> ShipDockRegistry.addressMatches(
                                dock.name(), refuel.dockFilter()))
                        .filter(ShipDockRegistry.Dock::refuel)
                        .map(ShipDockRegistry.Dock::id)
                        .toList();
            } else if (entry.instruction instanceof ParkAtInstruction park) {
                candidates = all.stream()
                        .filter(dock -> ShipDockRegistry.addressMatches(
                                dock.name(), park.dockFilter()))
                        .map(ShipDockRegistry.Dock::id)
                        .toList();
            } else if (entry.instruction instanceof DeliverPackagesInstruction
                    || entry.instruction instanceof FetchPackagesInstruction) {
                candidates = all.stream()
                        .filter(ShipDockRegistry.Dock::packages)
                        .map(ShipDockRegistry.Dock::id)
                        .toList();
            } else {
                candidates = List.of();
            }
            routeCandidateCache.put(idx, candidates);
        }
        routeCacheRevision = registryRevision;
    }

    // Resolve the full visible schedule into ordered navigation legs and queue them without starting travel.
    public boolean precalculateRoutes() {
        return precalculateRoutes(schedule);
    }

    // Resolve a controller-owned schedule graph into route legs without installing or starting it.
    public boolean precalculateRoutes(@Nullable Schedule routeSchedule) {
        if (routeSchedule == null || routeSchedule.entries.isEmpty() || controller.getLevel() == null
                || controller.getLevel().isClientSide || controller.getLevel().getServer() == null) return false;
        List<ShipDockRegistry.Dock> docks = ShipDockRegistry.get(controller.getLevel().getServer()).allIn(
                controller.getLevel().dimension().location());
        List<List<ShipControlModuleRuntime.ScheduledRouteDestination>> stopLayers = new ArrayList<>();
        for (int index = 0; index < routeSchedule.entries.size(); index++) {
            int entryIndex = index;
            ScheduleEntry entry = routeSchedule.entries.get(index);
            List<ShipControlModuleRuntime.ScheduledRouteDestination> layer = routeCandidates(entry, docks)
                    .stream()
                    .sorted(Comparator.comparing(candidate -> candidate.id().toString()))
                    .map(dock -> new ShipControlModuleRuntime.ScheduledRouteDestination(
                            entryIndex, dock.id(), precalculatedRouteTarget(dock)))
                    .toList();
            if (!layer.isEmpty()) {
                stopLayers.add(layer);
            }
        }
        // Every destination that the live schedule may select must have retained graph geometry.
        // Connect adjacent candidate layers instead of choosing one dock during pre-calculation;
        // the schedule remains the owner of the actual destination at run time.
        List<ShipControlModuleRuntime.ScheduledRouteDestination> destinations = new ArrayList<>();
        for (int index = 1; index < stopLayers.size(); index++) {
            addScheduledRouteLayer(destinations, stopLayers.get(index - 1), stopLayers.get(index));
        }
        if (routeSchedule.cyclic && stopLayers.size() > 1) {
            addScheduledRouteLayer(destinations, stopLayers.getLast(), stopLayers.getFirst());
        }
        return controller.queueShippingScheduleRoutes(destinations);
    }

    // Add the complete directed edge set between two adjacent schedule-stop candidate layers.
    private static void addScheduledRouteLayer(
            List<ShipControlModuleRuntime.ScheduledRouteDestination> destinations,
            List<ShipControlModuleRuntime.ScheduledRouteDestination> origins,
            List<ShipControlModuleRuntime.ScheduledRouteDestination> targets
    ) {
        for (ShipControlModuleRuntime.ScheduledRouteDestination origin : origins) {
            for (ShipControlModuleRuntime.ScheduledRouteDestination target : targets) {
                if (origin.target().distanceToSqr(target.target()) <= 1.0E-8D) continue;
                destinations.add(new ShipControlModuleRuntime.ScheduledRouteDestination(
                        target.scheduleEntry(), target.dockId(), origin.target(), target.target()));
            }
        }
    }

    // Resolve one schedule entry to its currently available dock candidates.
    private List<ShipDockRegistry.Dock> routeCandidates(
            ScheduleEntry entry, List<ShipDockRegistry.Dock> docks
    ) {
        if (entry == null || docks == null || docks.isEmpty()) return List.of();
        if (entry.instruction instanceof DestinationInstruction destination) {
            return docks.stream().filter(dock -> ShipDockRegistry.addressMatches(
                    dock.name(), destination.getFilter())).toList();
        }
        if (entry.instruction instanceof RefuelIfInstruction refuel) {
            return docks.stream().filter(dock -> dock.refuel()
                    && ShipDockRegistry.addressMatches(dock.name(), refuel.dockFilter())).toList();
        }
        if (entry.instruction instanceof ParkAtInstruction park) {
            return docks.stream().filter(dock -> ShipDockRegistry.addressMatches(
                    dock.name(), park.dockFilter())).toList();
        }
        if (entry.instruction instanceof DeliverPackagesInstruction
                || entry.instruction instanceof FetchPackagesInstruction) {
            return docks.stream().filter(ShipDockRegistry.Dock::packages).toList();
        }
        return List.of();
    }

    // Keep persistent routes independent from a provisioned connector selected only on live arrival.
    private Vec3 precalculatedRouteTarget(ShipDockRegistry.Dock dock) {
        return scheduleRouteTerminal(dock).position();
    }

    // Get the cached dock
    private @Nullable ShipDockRegistry.Dock cachedDock(@Nullable UUID id) {
        if (id == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return null;
        }
        refreshRouteCache();
        ShipDockRegistry.Dock dock = routeDockCache.get(id);
        long gameTime = controller.getLevel().getGameTime();
        int refreshTicks = Math.max(1, Math.min(4,
                CTConfigs.SERVER.shippingScheduleWorkSpreadTicks.get()));
        long lastRefresh = routeDockLiveRefreshTicks.getOrDefault(id, Long.MIN_VALUE);
        boolean movingDock = dock != null && dock.subLevelId() != null;
        if (dock != null && (!movingDock
                || lastRefresh != Long.MIN_VALUE
                && gameTime >= lastRefresh && gameTime - lastRefresh < refreshTicks)) {
            return selectConnectorForEntry(dock, currentEntry);
        }
        dock = ShipDockRegistry.get(controller.getLevel().getServer()).get(id);
        if (dock == null) {
            routeDockCache.remove(id);
            routeDockLiveRefreshTicks.remove(id);
            return null;
        }
        dock = selectConnectorForEntry(dock, currentEntry);
        routeDockCache.put(id, dock);
        routeDockLiveRefreshTicks.put(id, gameTime);
        return dock;
    }

    // Get the current route candidates
    private List<ShipDockRegistry.Dock> currentRouteCandidates() {
        refreshRouteCache();
        Vec3 origin = currentPosition();
        return routeCandidateCache.getOrDefault(currentEntry, List.of()).stream()
                .map(routeDockCache::get)
                .filter(java.util.Objects::nonNull)
                .map(dock -> selectConnectorForEntry(dock, currentEntry))
                .sorted(Comparator
                        .comparingDouble((ShipDockRegistry.Dock dock) ->
                                navigationTarget(dock, usesPhysicalDocking(dock))
                                        .distanceToSqr(origin))
                        .thenComparing(ShipDockRegistry.Dock::name)
                        .thenComparing(ShipDockRegistry.Dock::id))
                .toList();
    }

    // Select the connector for entry
    private ShipDockRegistry.Dock selectConnectorForEntry(
            ShipDockRegistry.Dock dock, int entryIndex
    ) {
        return selectConnectorForEntry(dock, entryIndex, schedule);
    }

    // Select the connector for an installed or graph-owned schedule entry.
    private ShipDockRegistry.Dock selectConnectorForEntry(
            ShipDockRegistry.Dock dock, int entryIndex, @Nullable Schedule routeSchedule
    ) {
        if (dock == null || dock.connectorTargets().size() <= 1 || routeSchedule == null
                || entryIndex < 0 || entryIndex >= routeSchedule.entries.size()) {
            return dock;
        }
        DockConnectorChoice choice = new DockConnectorChoice(dock.id(), entryIndex);
        ShipDockRegistry.ConnectorTarget cached = connectorChoiceCache.get(choice);
        ShipDockRegistry.ConnectorTarget selected = cached == null ? null
                : dock.connectorTargets().stream()
                .filter(target -> java.util.Objects.equals(
                        target.subLevelId(), cached.subLevelId())
                        && target.pos().equals(cached.pos()))
                .findFirst().orElse(null);
        if (selected == null) {
            ScheduleEntry entry = routeSchedule.entries.get(entryIndex);
            selected = dock.connectorTargets().stream()
                    .max(Comparator.comparingInt(target -> connectorSupplyScore(
                            dock.withConnector(target), entry)))
                    .orElse(dock.connectorTargets().getFirst());
        }
        connectorChoiceCache.put(choice, selected);
        return dock.withConnector(selected);
    }

    // Get the connector supply score
    private int connectorSupplyScore(
            ShipDockRegistry.Dock dock, ScheduleEntry entry
    ) {
        int score = 0;
        if (entry.instruction instanceof FetchPackagesInstruction fetch
                && ShipCargoAutomation.dockHasPackage(controller, dock, fetch.getFilter())) {
            score += 10_000;
        }
        if (entry.instruction instanceof DeliverPackagesInstruction
                && ShipCargoAutomation.dockCanReceivePackage(controller, dock)) {
            score += 10_000;
        }
        if (entry.instruction instanceof RefuelIfInstruction
                && dock.refuel()
                && ShipCargoAutomation.dockHasFuelSupply(controller, dock)) {
            score += 10_000;
        }
        for (List<ScheduleWaitCondition> column : entry.conditions) {
            for (ScheduleWaitCondition condition : column) {
                if (condition instanceof ItemThresholdCondition items
                        && ShipCargoAutomation.countDockItems(
                        controller, dock, items.getItem(0)) > 0L) {
                    score += 1_000;
                } else if (condition instanceof FluidThresholdCondition fluids
                        && ShipCargoAutomation.countDockFluids(
                        controller, dock, fluids.getItem(0)) > 0L) {
                    score += 1_000;
                } else if (isEnergyCargoCondition(condition)
                        && ShipCargoAutomation.countDockEnergy(controller, dock) > 0L) {
                    score += 1_000;
                }
            }
        }
        if (dock.refuel() && ShipCargoAutomation.dockHasFuelSupply(controller, dock)) {
            score += 100;
        }
        if (dock.restock() && ShipCargoAutomation.dockHasRestockCargo(controller, dock)) {
            score += 50;
        }
        if (dock.packages() && ShipCargoAutomation.dockHasPackage(controller, dock, "")) {
            score += 25;
        }
        return score;
    }

    // Start the current entry
    private void startCurrentEntry() {
        if (schedule == null || controller.getLevel() == null) {
            return;
        }
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        if (entry.instruction instanceof ChangeThrottleInstruction throttle) {
            routeThrottle = Math.max(0.05D, Math.min(1.0D, throttle.getThrottle()));
            routeSpeed = speedForThrottle(routeThrottle);
            status = "Cruising throttle set to " + Math.round(throttle.getThrottle() * 100.0F) + "%";
            advanceEntry();
            return;
        }
        if (entry.instruction instanceof ChangeTitleInstruction rename) {
            title = rename.getScheduleTitle();
            status = title.isBlank() ? "Shipping schedule renamed" : "Shipping schedule: " + title;
            advanceEntry();
            return;
        }
        if (entry.instruction instanceof CarriageCouplingInstruction coupling) {
            phase = Phase.COUPLING;
            phaseStartedTick = controller.getLevel().getGameTime();
            status = couplingStatus(coupling, "in progress");
            controller.setChanged();
            tickCoupling();
            return;
        }
        if (entry.instruction instanceof ParkAtInstruction) {
            List<ShipDockRegistry.Dock> candidates = currentRouteCandidates();
            if (candidates.isEmpty()) {
                status = "Waiting for a matching ship dock landing zone";
                controller.setChanged();
                return;
            }
            beginParking(candidates);
            return;
        }

        Vec3 origin = currentPosition();
        List<ShipDockRegistry.Dock> candidates = List.of();
        if (entry.instruction instanceof DestinationInstruction destination) {
            candidates = currentRouteCandidates();
        } else if (entry.instruction instanceof DeliverPackagesInstruction) {
            if (!ShipCargoAutomation.hasPackages(controller)) {
                status = "No packages aboard for delivery";
                advanceEntry();
                return;
            }
            candidates = currentRouteCandidates().stream()
                    .filter(dock -> ShipCargoAutomation.hasPackageForDock(
                            controller, dock.name()))
                    .sorted(java.util.Comparator.comparingDouble(dock ->
                            dock.approach().distanceToSqr(origin)))
                    .toList();
        } else if (entry.instruction instanceof FetchPackagesInstruction fetch) {
            candidates = currentRouteCandidates().stream()
                    .filter(dock -> ShipCargoAutomation.dockHasPackage(
                            controller, dock, fetch.getFilter()))
                    .sorted(java.util.Comparator.comparingDouble(dock ->
                            dock.approach().distanceToSqr(origin)))
                    .toList();
        } else if (entry.instruction instanceof RefuelIfInstruction) {
            status = currentEntry == 0
                    ? "Auto Refuel policy activated"
                    : "Refuel check completed";
            advanceEntry();
            return;
        }
        if (candidates.isEmpty()) {
            skipCurrentEntry("Skipped unavailable ship dock destination");
            return;
        }
        beginRoute(candidates, true);
    }

    // Update the coupling
    private void tickCoupling() {
        CarriageCouplingInstruction coupling = currentCouplingInstruction();
        if (coupling == null) {
            couplingEndpoints = List.of();
            phase = Phase.PRE_TRANSIT;
            status = "Carriage coupling action could not be restored; retrying the schedule entry";
            controller.setChanged();
            return;
        }
        if (couplingEndpoints.isEmpty()) {
            couplingEndpoints = ShipCouplerService.selectEndpoints(
                    controller, coupling.endpointSelector());
            if (couplingEndpoints.isEmpty()) {
                pauseCoupling(coupling, "has no matching endpoint");
                return;
            }
            controller.setChanged();
        }
        ShipCouplerService.Result res;
        try {
            res = ShipCouplerService.command(
                    controller, couplingRequestKey(), coupling.attach(), couplingEndpoints);
        } catch (RuntimeException err) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Unable to execute the shipping carriage coupling action", err);
            pauseCoupling(coupling, "failed");
            return;
        }
        if (res == null) {
            pauseCoupling(coupling, "failed");
            return;
        }
        switch (res) {
            case COMPLETE -> {
                status = couplingStatus(coupling, "complete");
                couplingEndpoints = List.of();
                advanceEntry();
            }
            case PENDING -> status = couplingStatus(coupling, "in progress");
            case NO_TARGET -> pauseCoupling(coupling, "has no matching endpoint");
            case FAILED -> pauseCoupling(coupling, "failed");
        }
    }

    // Get the coupling request key
    private String couplingRequestKey() {
        return "shipping_schedule:coupling:" + currentEntry;
    }

    // Get the current coupling instruction
    private @Nullable CarriageCouplingInstruction currentCouplingInstruction() {
        if (schedule == null || currentEntry < 0 || currentEntry >= schedule.entries.size()) {
            return null;
        }
        return schedule.entries.get(currentEntry).instruction
                instanceof CarriageCouplingInstruction coupling ? coupling : null;
    }

    // Pause the coupling
    private void pauseCoupling(CarriageCouplingInstruction coupling, String detail) {
        phaseBeforePause = Phase.COUPLING;
        phase = Phase.PAUSED;
        status = couplingStatus(coupling, detail)
                + "; resume to retry or skip this action";
        controller.setChanged();
    }

    // Get the coupling status
    private static String couplingStatus(
            CarriageCouplingInstruction coupling, String detail
    ) {
        String operation = coupling.attach() ? "Coupling" : "Decoupling";
        String endpoint = coupling.endpointSelector() < 0
                ? "all carriage endpoints"
                : "carriage endpoint #" + coupling.endpointSelector();
        return operation + " " + endpoint + " " + detail;
    }

    // Begin the route
    private void beginRoute(
            List<ShipDockRegistry.Dock> requestedCandidates,
            boolean permitDiversion
    ) {
        if (controller.getLevel() == null || requestedCandidates == null
                || requestedCandidates.isEmpty()) {
            return;
        }
        List<ShipDockRegistry.Dock> actualCandidates = requestedCandidates;
        if (permitDiversion) {
            divertedTargetId = null;
        }
        status = autoRefuelActive
                ? autoRefuelReturning
                ? "Auto Refuel: returning to the interrupted task"
                : "Auto Refuel: waiting for an available fuel dock"
                : "Waiting for an available ship dock";

        releaseAllReservations();
        clearParkingState();
        clearHoldingState();
        routeTerminalTransitPending = false;
        pendingDockCandidates = actualCandidates.stream()
                .map(ShipDockRegistry.Dock::id)
                .toList();
        ShipDockRegistry.Dock routeTerminal = routeTerminalCandidate(actualCandidates);
        if (routeTerminal != null) {
            startRouteTerminalTransit(routeTerminal);
            return;
        }
        beginLiveDockApproach(actualCandidates);
    }

    // Run the stable live reservation and connector assignment after planned transit finishes.
    private void beginLiveDockApproach(List<ShipDockRegistry.Dock> actualCandidates) {
        if (actualCandidates == null || actualCandidates.isEmpty()) return;
        routeTerminalTransitPending = false;
        // This list is the stable scheduler's complete authority for the live phase. At a planned
        // terminal it contains only that terminal's dock, preventing heartbeat/arrival retries
        // from silently expanding back to a different schedule candidate.
        pendingDockCandidates = actualCandidates.stream()
                .map(ShipDockRegistry.Dock::id)
                .toList();
        ShipDockScheduler.Lease lease = requestDockLease(actualCandidates);
        if (!lease.granted()) {
            ShipDockRegistry.Dock target = navigationCandidate(actualCandidates);
            if (target == null) {
                skipCurrentEntry("Skipped unavailable ship dock destination");
                return;
            }
            enterDockQueue(actualCandidates, lease);
            return;
        }
        ShipDockRegistry.Dock target = dockForLease(actualCandidates, lease);
        if (target == null) {
            skipCurrentEntry("Skipped unavailable ship dock destination");
            return;
        }
        startReservedRoute(target);
    }

    // Traverse the reusable route before provisioning may redirect local final approach control.
    private void startRouteTerminalTransit(ShipDockRegistry.Dock target) {
        if (controller.getLevel() == null || target == null) return;
        boolean wasAttached = needsUndockingStabilization(attachedDockId);
        queueAfterUndocking = false;
        routeTerminalTransitPending = true;
        restoredDockOwnership = null;
        releaseReservationChannel("holding");
        clearHoldingState();
        disengageAttachedConnectors();
        currentDockId = target.id();
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        controller.activateShipDockingConnector(-1);
        phaseStartedTick = controller.getLevel().getGameTime();
        lastTargetRefreshTick = Long.MIN_VALUE;
        resetRouteProgress(target);
        if (wasAttached && issueUndockingControl()) {
            phase = Phase.UNDOCKING;
            status = "Stabilizing after undocking";
        } else if (issueRouteTerminalTransit(target)) {
            phase = Phase.TRANSITING;
            status = "Following the planned route toward " + target.name();
        } else {
            routeTerminalTransitPending = false;
            resetRouteProgress();
            issueDockingRecoveryHold();
            phase = Phase.PRE_TRANSIT;
            status = "Route control unavailable; stabilizing and retrying";
        }
        controller.setChanged();
    }

    // Start the reserved route
    private void startReservedRoute(ShipDockRegistry.Dock target) {
        if (controller.getLevel() == null) {
            return;
        }
        ShipDockRegistry.Dock previousDock = cachedDock(currentDockId);
        ShipDockScheduler.DockSlot previousSlot = previousDock == null
                ? null : schedulerSlot(previousDock);
        boolean wasAttached = needsUndockingStabilization(attachedDockId);
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        restoredDockOwnership = null;
        releaseReservationChannel("holding");
        clearHoldingState();
        disengageAttachedConnectors();
        currentDockId = target.id();
        if (target.connectorPos() != null) {
            target.connectorTargets().stream()
                    .filter(candidate -> java.util.Objects.equals(
                            candidate.subLevelId(), target.connectorSubLevelId())
                            && candidate.pos().equals(target.connectorPos()))
                    .findFirst()
                    .ifPresent(candidate -> connectorChoiceCache.put(
                            new DockConnectorChoice(target.id(), currentEntry), candidate));
        }
        ShipControlModuleRuntime.MappedDockingConnector shipConnector =
                usesPhysicalDocking(target) ? selectShipConnectorForEntry(target) : null;
        currentShipConnectorIndex = shipConnector == null ? -1 : shipConnector.index();
        if (previousSlot == null || !previousSlot.equals(schedulerSlot(target))) {
            dockingRetryCount = 0;
        }
        connectorlessHoverTarget = currentShipConnectorIndex < 0
                ? navigationTarget(target, false) : null;
        controller.activateShipDockingConnector(-1);
        phaseStartedTick = controller.getLevel().getGameTime();
        lastTargetRefreshTick = Long.MIN_VALUE;
        resetRouteProgress(target);
        if (wasAttached && issueUndockingControl()) {
            phase = Phase.UNDOCKING;
            status = "Stabilizing after undocking";
        } else if (issueNavigation(target)) {
            phase = Phase.NAVIGATING;
            status = "Navigating to " + target.name();
        } else {
            releaseDockReservation();
            resetRouteProgress();
            issueDockingRecoveryHold();
            phase = Phase.PRE_TRANSIT;
            status = "Navigation control unavailable; stabilizing and retrying";
        }
        controller.setChanged();
    }

    // Begin parking at one matching landing zone
    private void beginParking(List<ShipDockRegistry.Dock> candidates) {
        if (controller.getLevel() == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        releaseAllReservations();
        clearHoldingState();
        pendingDockCandidates = candidates.stream()
                .map(ShipDockRegistry.Dock::id)
                .toList();
        LandingZoneRequest request = requestLandingZoneLease("park", candidates,
                currentDockId, currentParkingZoneId, false);
        LandingZoneLeaseTarget target = landingZoneForLease(request);
        if (target == null) {
            enterParkQueue(candidates, request.lease());
            return;
        }
        startReservedParking(target);
    }

    // Start the route to one reserved landing zone
    private void startReservedParking(LandingZoneLeaseTarget target) {
        if (controller.getLevel() == null || target == null) {
            return;
        }
        boolean wasAttached = needsUndockingStabilization(attachedDockId);
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        releaseReservationChannel("holding");
        clearHoldingState();
        disengageAttachedConnectors();
        currentDockId = target.dock().id();
        currentParkingZoneId = target.zone().id();
        parkingKind = target.zone().airborne()
                ? ParkingKind.AIRBORNE : ParkingKind.GROUND;
        parkingTarget = landingZoneTarget(target.zone());
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        phaseStartedTick = controller.getLevel().getGameTime();
        lastTargetRefreshTick = Long.MIN_VALUE;
        resetRouteProgress();
        if (wasAttached && issueUndockingControl()) {
            phase = Phase.UNDOCKING;
            status = "Stabilizing before parking at " + target.dock().name();
        } else {
            phase = Phase.PARKING;
            if (issueParkingNavigation(parkingTarget)) {
                status = "Parking at " + target.dock().name()
                        + " / " + target.zone().name();
            } else {
                status = "Parking control unavailable; stabilizing and retrying";
            }
        }
        controller.setChanged();
    }

    // Enter the landing zone queue
    private void enterParkQueue(
            List<ShipDockRegistry.Dock> candidates,
            ShipDockScheduler.Lease lease
    ) {
        if (controller.getLevel() == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        ShipDockRegistry.Dock waitingDock = navigationCandidate(candidates);
        if (waitingDock == null) {
            return;
        }
        boolean wasAttached = needsUndockingStabilization(attachedDockId);
        disengageAttachedConnectors();
        currentDockId = waitingDock.id();
        currentParkingZoneId = null;
        parkingKind = isAirshipMode() ? ParkingKind.AIRBORNE : ParkingKind.GROUND;
        parkingTarget = dockHoldingPosition(waitingDock, lease.holdingPlacement());
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        phaseStartedTick = controller.getLevel().getGameTime();
        lastTargetRefreshTick = Long.MIN_VALUE;
        resetRouteProgress();
        if (wasAttached && issueUndockingControl()) {
            queueAfterUndocking = true;
            phase = Phase.UNDOCKING;
            status = "Stabilizing before joining the landing zone queue";
        } else {
            queueAfterUndocking = false;
            phase = Phase.WAITING_FOR_PARK;
            issueParkingNavigation(parkingTarget);
            status = "Waiting near " + waitingDock.name()
                    + " for a free landing zone (queue "
                    + Math.max(1, lease.queuePosition()) + ")";
        }
        controller.setChanged();
    }

    // Update the landing zone queue
    private void tickWaitingForPark() {
        if (controller.getLevel() == null) {
            return;
        }
        List<ShipDockRegistry.Dock> candidates = parkingCandidates();
        if (candidates.isEmpty()) {
            phase = Phase.PRE_TRANSIT;
            status = "Waiting for a matching ship dock landing zone";
            return;
        }
        LandingZoneRequest request = requestLandingZoneLease(
                "park", candidates, null, null, false);
        LandingZoneLeaseTarget assigned = landingZoneForLease(request);
        if (assigned != null) {
            startReservedParking(assigned);
            return;
        }
        ShipDockRegistry.Dock waitingDock = navigationCandidate(candidates);
        if (waitingDock == null) {
            return;
        }
        currentDockId = waitingDock.id();
        parkingTarget = dockHoldingPosition(waitingDock, request.lease().holdingPlacement());
        long now = controller.getLevel().getGameTime();
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueParkingNavigation(parkingTarget);
        }
        status = "Waiting near " + waitingDock.name()
                + " for a free landing zone (queue "
                + Math.max(1, request.lease().queuePosition()) + ")";
    }

    // Update the landing zone approach
    private void tickParking() {
        if (controller.getLevel() == null || currentDockId == null
                || currentParkingZoneId == null) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        List<ShipDockRegistry.Dock> candidates = parkingCandidates();
        if (candidates.isEmpty()) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        LandingZoneRequest request = requestLandingZoneLease(
                "park", candidates, currentDockId, currentParkingZoneId, true);
        LandingZoneLeaseTarget assigned = landingZoneForLease(request);
        if (assigned == null) {
            enterParkQueue(candidates, request.lease());
            return;
        }
        if (!assigned.dock().id().equals(currentDockId)
                || !assigned.zone().id().equals(currentParkingZoneId)) {
            startReservedParking(assigned);
            return;
        }
        parkingKind = assigned.zone().airborne()
                ? ParkingKind.AIRBORNE : ParkingKind.GROUND;
        parkingTarget = landingZoneTarget(assigned.zone());
        long now = controller.getLevel().getGameTime();
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueParkingNavigation(parkingTarget);
        }
        if (!connectorlessHoverTargetReached(
                currentPosition(), parkingTarget, parkArrivalTolerance())) {
            return;
        }
        issueParkingHold();
        phase = Phase.PARKED;
        phaseStartedTick = now;
        lastStopName = assigned.dock().name();
        initConditions();
        status = parkingKind == ParkingKind.AIRBORNE
                ? "Hovering at " + assigned.dock().name() + " / " + assigned.zone().name()
                : "Parked at " + assigned.dock().name() + " / " + assigned.zone().name();
        controller.setChanged();
    }

    // Update the parked schedule entry
    private void tickParked() {
        if (schedule == null || controller.getLevel() == null
                || currentDockId == null || currentParkingZoneId == null) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        List<ShipDockRegistry.Dock> candidates = parkingCandidates();
        if (candidates.isEmpty()) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        LandingZoneRequest request = requestLandingZoneLease(
                "park", candidates, currentDockId, currentParkingZoneId, true);
        LandingZoneLeaseTarget assigned = landingZoneForLease(request);
        if (assigned == null) {
            enterParkQueue(candidates, request.lease());
            return;
        }
        if (!assigned.dock().id().equals(currentDockId)
                || !assigned.zone().id().equals(currentParkingZoneId)) {
            startReservedParking(assigned);
            return;
        }
        parkingKind = assigned.zone().airborne()
                ? ParkingKind.AIRBORNE : ParkingKind.GROUND;
        parkingTarget = landingZoneTarget(assigned.zone());
        long now = controller.getLevel().getGameTime();
        if (!connectorlessHoverTargetReached(
                currentPosition(), parkingTarget, parkArrivalTolerance() * 2.0D)) {
            phase = Phase.PARKING;
            phaseStartedTick = now;
            issueParkingNavigation(parkingTarget);
            status = "Returning to the reserved landing zone at " + assigned.dock().name();
            return;
        }
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueParkingHold();
        }
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        if (entry.conditions.isEmpty() || conditionsComplete(entry.conditions)) {
            finishParkEntry(assigned.dock());
        } else {
            status = "Waiting while parked at " + assigned.dock().name();
        }
    }

    // Complete one parking schedule entry
    private void finishParkEntry(ShipDockRegistry.Dock dock) {
        if (schedule == null) {
            return;
        }
        boolean finalEntry = currentEntry >= schedule.entries.size() - 1;
        if (finalEntry && !schedule.cyclic) {
            phase = Phase.DONE;
            schedule.savedProgress = currentEntry;
            status = parkingKind == ParkingKind.AIRBORNE
                    ? "Shipping schedule complete; hovering at " + dock.name()
                    : "Shipping schedule complete; parked at " + dock.name();
            controller.setChanged();
            return;
        }
        releaseReservationChannel("park");
        clearParkingState();
        advanceEntry();
    }

    // Request one landing zone resource channel
    private LandingZoneRequest requestLandingZoneLease(
            String channel,
            List<ShipDockRegistry.Dock> docks,
            @Nullable UUID ownedDockId,
            @Nullable UUID ownedZoneId,
            boolean committed
    ) {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return new LandingZoneRequest(
                    new ShipDockScheduler.Lease(null, 0), List.of());
        }
        ShipDockScheduler.VesselEnvelope envelope = vesselEnvelope();
        Vec3 origin = currentPosition();
        List<LandingZoneLeaseTarget> targets = new ArrayList<>();
        for (ShipDockRegistry.Dock dock : docks) {
            for (ShipDockRegistry.LandingZoneTarget zone : dock.landingZones()) {
                if (zone.airborne() && !isAirshipMode()
                        || !envelope.fitsFootprint(
                        zone.worldBounds().getXsize(), zone.worldBounds().getZsize(),
                        LANDING_ZONE_CLEARANCE)) {
                    continue;
                }
                targets.add(new LandingZoneLeaseTarget(
                        dock, zone, landingZoneSlot(dock, zone)));
            }
        }
        Set<ShipDockScheduler.DockSlot> occupied = new HashSet<>();
        for (LandingZoneLeaseTarget target : targets) {
            if (landingZoneOccupied(target.dock(), target.zone(), envelope)) {
                occupied.add(target.slot());
            }
        }
        targets.sort(Comparator
                .comparing((LandingZoneLeaseTarget target) -> !(
                        target.dock().id().equals(ownedDockId)
                                && target.zone().id().equals(ownedZoneId)))
                .thenComparing(target -> occupied.contains(target.slot()))
                .thenComparingDouble(target -> origin.distanceToSqr(
                        landingZoneTarget(target.zone())))
                .thenComparing(target -> target.dock().name())
                .thenComparing(target -> target.dock().id())
                .thenComparingInt(target -> target.zone().queueOrder())
                .thenComparing(target -> target.zone().id()));
        ShipDockScheduler.DockSlot owned = !committed && !"holding".equals(channel)
                ? null : targets.stream()
                .filter(target -> target.dock().id().equals(ownedDockId)
                        && target.zone().id().equals(ownedZoneId))
                .map(LandingZoneLeaseTarget::slot)
                .findFirst().orElse(null);
        double distance = targets.stream()
                .mapToDouble(target -> origin.distanceTo(
                        landingZoneTarget(target.zone())))
                .min().orElse(Double.POSITIVE_INFINITY);
        long now = controller.getLevel().getServer().getTickCount();
        ShipDockScheduler.Lease lease = ShipDockScheduler.get(
                controller.getLevel().getServer()).request(
                new ShipDockScheduler.RequestKey(pilotId, channel),
                targets.stream().map(LandingZoneLeaseTarget::slot).toList(),
                occupied, owned,
                new ShipDockScheduler.RequestPriority(
                        distance, committed ? 0L : estimatedDockEtaTicks(distance), committed),
                envelope, now);
        return new LandingZoneRequest(lease, List.copyOf(targets));
    }

    // Get the landing zone for one scheduler lease
    private static @Nullable LandingZoneLeaseTarget landingZoneForLease(
            LandingZoneRequest request
    ) {
        if (request == null || request.lease() == null || !request.lease().granted()) {
            return null;
        }
        return request.targets().stream()
                .filter(target -> target.dock().id().equals(request.lease().dockId())
                        && target.slot().resourceKey().equals(
                        request.lease().resourceKey()))
                .findFirst().orElse(null);
    }

    // Get one landing zone scheduler slot
    private static ShipDockScheduler.DockSlot landingZoneSlot(
            ShipDockRegistry.Dock dock,
            ShipDockRegistry.LandingZoneTarget zone
    ) {
        return ShipDockScheduler.DockSlot.resource(
                dock.id(), "landing-zone|" + dock.id() + "|" + zone.id());
    }

    // Get one landing zone by id
    private static @Nullable ShipDockRegistry.LandingZoneTarget landingZoneById(
            @Nullable ShipDockRegistry.Dock dock,
            @Nullable UUID zoneId
    ) {
        if (dock == null || zoneId == null) {
            return null;
        }
        return dock.landingZones().stream()
                .filter(zone -> zone.id().equals(zoneId))
                .findFirst().orElse(null);
    }

    // Check if another loaded assembly occupies one landing zone
    private boolean landingZoneOccupied(
            ShipDockRegistry.Dock dock,
            ShipDockRegistry.LandingZoneTarget zone,
            ShipDockScheduler.VesselEnvelope envelope
    ) {
        if (controller.getLevel() == null) {
            return true;
        }
        Vec3 clearance = zone.worldUp().scale(
                envelope.height() + LANDING_ZONE_CLEARANCE);
        AABB occupancyBounds = zone.worldBounds()
                .inflate(LANDING_ZONE_CLEARANCE)
                .expandTowards(clearance);
        Set<UUID> excluded = new HashSet<>(controller.getMappedShipSubLevelIds());
        if (dock.subLevelId() != null) {
            excluded.add(dock.subLevelId());
        }
        Level rootLevel = SableLevelApi.serverLevel(controller.getLevel());
        for (SubLevel subLevel : SableTransformApi.intersecting(
                rootLevel, occupancyBounds)) {
            if (!excluded.contains(subLevel.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    // Get the controller target for one landing zone
    private Vec3 landingZoneTarget(ShipDockRegistry.LandingZoneTarget zone) {
        return zone.worldCenter().add(zone.worldUp().scale(
                vesselEnvelope().bottomOffset() + 0.05D));
    }

    // Issue parking navigation
    private boolean issueParkingNavigation(@Nullable Vec3 target) {
        if (controller.getLevel() == null || target == null) {
            return false;
        }
        clearDockingRecoveryControl();
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        return controller.executeShipControlGraphCommand(
                PARK_NAVIGATION_COMMAND, "ship_follow",
                routeControlParameters(Map.of(
                        "x", target.x, "y", target.y, "z", target.z,
                        "speed", Math.min(routeSpeed, 8.0D),
                        "follow_distance", 0.0D,
                        "avoid_collisions", 1.0D)));
    }

    // Hold the ship at its reserved landing zone
    private void issueParkingHold() {
        if (controller.getLevel() == null || parkingTarget == null) {
            return;
        }
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        if (parkingKind == ParkingKind.AIRBORNE) {
            controller.executeShipControlGraphCommand(
                    PARK_HOLD_COMMAND, "ship_follow",
                    Map.of("x", parkingTarget.x, "y", parkingTarget.y,
                            "z", parkingTarget.z, "speed", Math.min(routeSpeed, 4.0D),
                            "follow_distance", 0.0D, "avoid_collisions", 1.0D));
            return;
        }
        controller.executeShipControlGraphCommand(
                PARK_HOLD_COMMAND + "_stop", "ship_stop", Map.of());
        controller.executeShipControlGraphCommand(
                PARK_HOLD_COMMAND + "_brake", "ship_decelerate",
                Map.of("strength", 1.0D));
    }

    // Get the parking arrival tolerance
    private double parkArrivalTolerance() {
        return Math.max(PARK_ARRIVAL_TOLERANCE,
                Math.min(2.5D, vesselEnvelope().horizontalRadius() * 0.2D));
    }

    // Check if the current SCM can use airborne landing zones
    private boolean isAirshipMode() {
        return controller.getShipControlMode().id().equals(
                ScmBuiltinControlModes.AIRSHIP_ID);
    }

    // Check if the current SCM controls a ground vehicle.
    private boolean isCarMode() {
        return controller.getShipControlMode().id().equals(
                ScmBuiltinControlModes.CAR_ID);
    }

    // Select the ship connector for entry
    private @Nullable ShipControlModuleRuntime.MappedDockingConnector selectShipConnectorForEntry(
            ShipDockRegistry.Dock target
    ) {
        return selectShipConnectorForEntry(target, schedule, currentEntry);
    }

    // Select the ship connector for an installed or graph-owned schedule entry.
    private @Nullable ShipControlModuleRuntime.MappedDockingConnector selectShipConnectorForEntry(
            ShipDockRegistry.Dock target, @Nullable Schedule routeSchedule, int entryIndex
    ) {
        if (routeSchedule == null || entryIndex < 0 || entryIndex >= routeSchedule.entries.size()) {
            return controller.selectShipDockingConnector(
                    target.connectorFacing().scale(-1.0D), target.dockingTarget());
        }
        ScheduleEntry entry = routeSchedule.entries.get(entryIndex);
        Vec3 desiredFacing = target.connectorFacing().scale(-1.0D).normalize();
        return controller.getShipDockingConnectors().stream()
                .max(Comparator.comparingDouble(connector -> {
                    double score = connector.worldFacing().normalize().dot(desiredFacing) * 100.0D;
                    score -= Math.min(1_000.0D,
                            connector.worldTipPosition().distanceTo(target.dockingTarget())) * 0.01D;
                    if (entry.instruction instanceof DeliverPackagesInstruction
                            && ShipCargoAutomation.shipConnectorAdvertisesPackage(
                            controller, connector, target.name())) {
                        score += 10_000.0D;
                    }
                    if (entry.instruction instanceof FetchPackagesInstruction
                            && ShipCargoAutomation.shipConnectorHasStockLink(controller, connector)) {
                        score += 500.0D;
                    }
                    if (target.restock()
                            && ShipCargoAutomation.shipConnectorSupportsItems(controller, connector)) {
                        score += 500.0D;
                    }
                    if (target.refuel()
                            && (ShipCargoAutomation.shipConnectorSupportsFluids(controller, connector)
                            || ShipCargoAutomation.shipConnectorSupportsEnergy(controller, connector))) {
                        score += 500.0D;
                    }
                    for (List<ScheduleWaitCondition> column : entry.conditions) {
                        for (ScheduleWaitCondition condition : column) {
                            if (condition instanceof ItemThresholdCondition items) {
                                if (ShipCargoAutomation.shipConnectorSupportsItems(
                                        controller, connector)) {
                                    score += 500.0D;
                                }
                                if (ShipCargoAutomation.shipConnectorAdvertisedItems(
                                        controller, connector, items.getItem(0)) > 0L) {
                                    score += 1_000.0D;
                                }
                            }
                            if (condition instanceof FluidThresholdCondition fluids) {
                                if (ShipCargoAutomation.shipConnectorSupportsFluids(
                                        controller, connector)) {
                                    score += 500.0D;
                                }
                                if (ShipCargoAutomation.shipConnectorAdvertisedFluids(
                                        controller, connector, fluids.getItem(0)) > 0L) {
                                    score += 1_000.0D;
                                }
                            }
                            if (isEnergyCargoCondition(condition)
                                    && ShipCargoAutomation.shipConnectorSupportsEnergy(
                                    controller, connector)) {
                                score += 500.0D;
                            }
                        }
                    }
                    return score - connector.index() * 1.0E-6D;
                }))
                .orElse(null);
    }

    // Request the dock lease
    private ShipDockScheduler.Lease requestDockLease(
            List<ShipDockRegistry.Dock> candidates
    ) {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return new ShipDockScheduler.Lease(null, 0);
        }
        Set<ShipDockScheduler.DockSlot> physicallyOccupied = new HashSet<>();
        ShipDockScheduler.DockSlot ownedDock = schedulerOwnedSlot(candidates);
        long now = controller.getLevel().getServer().getTickCount();
        purgeFailedDockingSlots(now);
        for (ShipDockRegistry.Dock dock : candidates) {
            if (!usesPhysicalDocking(dock)) continue;
            for (ShipDockRegistry.ConnectorTarget target : dock.connectorTargets()) {
                ShipDockRegistry.Dock candidate = dock.withConnector(target);
                ShipDockScheduler.DockSlot slot = schedulerSlot(candidate);
                if (!slot.equals(ownedDock) && DockingConnectorAutomation.hasConnectionState(
                        DockingConnectorAutomation.resolve(
                                controller.getLevel(), target.subLevelId(), target.pos()))) {
                    physicallyOccupied.add(slot);
                }
            }
        }
        List<ShipDockScheduler.DockSlot> availableSlots = candidates.stream()
                .flatMap(dock -> schedulerSlots(dock).stream())
                .filter(slot -> slot.equals(ownedDock)
                        || !failedDockingSlots.containsKey(slot))
                .toList();
        return ShipDockScheduler.get(controller.getLevel().getServer()).request(
                new ShipDockScheduler.RequestKey(pilotId, "dock"),
                availableSlots,
                physicallyOccupied,
                ownedDock,
                dockRequestPriority(candidates, ownedDock != null),
                vesselEnvelope(),
                now);
    }

    // Get the conservative vessel envelope
    private ShipDockScheduler.VesselEnvelope vesselEnvelope() {
        com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyBoundsApi.Envelope envelope =
                controller.getShipEnvelope();
        return envelope == null
                ? new ShipDockScheduler.VesselEnvelope(1.0D, 1.0D, 0.5D)
                : new ShipDockScheduler.VesselEnvelope(
                envelope.horizontalRadius(), envelope.height(), envelope.bottomOffset());
    }

    // Remove expired failed docking slots
    private void purgeFailedDockingSlots(long now) {
        failedDockingSlots.entrySet().removeIf(entry ->
                now < 0L || entry.getValue() <= now);
    }

    // Temporarily exclude one failed docking slot
    private void coolDownDockingSlot(ShipDockRegistry.Dock dock, long durationTicks) {
        if (dock == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        long expires = controller.getLevel().getServer().getTickCount()
                + Math.max(1L, durationTicks);
        ShipDockScheduler.DockSlot slot = schedulerSlot(dock);
        failedDockingSlots.put(slot, expires);
        while (failedDockingSlots.size() > MAX_FAILED_DOCKING_SLOTS) {
            ShipDockScheduler.DockSlot earliest = failedDockingSlots.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (earliest == null) {
                break;
            }
            failedDockingSlots.remove(earliest);
        }
        connectorChoiceCache.remove(new DockConnectorChoice(dock.id(), currentEntry));
    }

    // Get the dock request priority
    private ShipDockScheduler.RequestPriority dockRequestPriority(
            List<ShipDockRegistry.Dock> candidates,
            boolean ownsCandidateDock
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return new ShipDockScheduler.RequestPriority(
                    Double.POSITIVE_INFINITY, -1L, false);
        }
        Vec3 origin = currentPosition();
        double distance = candidates.stream()
                .mapToDouble(candidate -> origin.distanceTo(navigationTarget(
                        candidate, usesPhysicalDocking(candidate))))
                .filter(Double::isFinite)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
        boolean committed = ownsCandidateDock && (phase == Phase.DOCKING
                || phase == Phase.DOCKED
                || phase == Phase.WAITING
                || attachedDockId != null);
        long etaTicks = committed ? 0L : estimatedDockEtaTicks(distance);
        return new ShipDockScheduler.RequestPriority(distance, etaTicks, committed);
    }

    // Get the estimated dock eta ticks
    private long estimatedDockEtaTicks(double distance) {
        if (!Double.isFinite(distance)) {
            return -1L;
        }
        double speed = switch (phase) {
            case DOCKING -> MAX_DOCKING_SPEED;
            case WAITING_FOR_DOCK -> Math.min(routeSpeed, 8.0D);
            case IDLE, INITIALIZING, COUPLING, PAUSED, DONE, DOCKED, WAITING -> 0.0D;
            default -> routeSpeed;
        };
        if (speed <= 0.0D) {
            return -1L;
        }
        return Math.max(1L, (long) Math.ceil(distance / speed));
    }

    // Get the scheduler slots
    private List<ShipDockScheduler.DockSlot> schedulerSlots(ShipDockRegistry.Dock dock) {
        if (dock == null) return List.of();
        if (!usesPhysicalDocking(dock)) return List.of(dockArrivalSlot(dock));
        return dock.connectorTargets().stream()
                .map(dock::withConnector)
                .map(this::schedulerSlot)
                .distinct()
                .toList();
    }

    // Get the scheduler slot
    private ShipDockScheduler.DockSlot schedulerSlot(ShipDockRegistry.Dock dock) {
        if (!usesPhysicalDocking(dock)) return dockArrivalSlot(dock);
        String connectorKey = dock.connectorPos() == null ? null
                : dock.dimension() + "|"
                + (dock.connectorSubLevelId() == null
                ? "world" : dock.connectorSubLevelId())
                + "|" + dock.connectorPos().asLong();
        return new ShipDockScheduler.DockSlot(dock.id(), connectorKey);
    }

    // Keep a connectorless stop independent from every provisioned physical connector.
    private static ShipDockScheduler.DockSlot dockArrivalSlot(ShipDockRegistry.Dock dock) {
        return ShipDockScheduler.DockSlot.resource(
                dock.id(), CONNECTORLESS_ARRIVAL_RESOURCE + dock.id());
    }

    // Get the scheduler owned slot
    private @Nullable ShipDockScheduler.DockSlot schedulerOwnedSlot(
            List<ShipDockRegistry.Dock> candidates
    ) {
        UUID ownedDockId = schedulerOwnedDock();
        if (ownedDockId == null) {
            return null;
        }
        ShipDockRegistry.Dock ownedDock = candidates.stream()
                .filter(dock -> ownedDockId.equals(dock.id()))
                .findFirst().orElse(null);
        if (ownedDock == null) {
            return null;
        }
        ShipDockRegistry.Dock selected = selectConnectorForEntry(ownedDock, currentEntry);
        if (phase == Phase.DOCKING || restoredDockOwnership != null) {
            for (ShipDockRegistry.ConnectorTarget target : ownedDock.connectorTargets()) {
                ShipDockRegistry.Dock candidate = ownedDock.withConnector(target);
                BlockEntityPair pair = connectorPair(candidate);
                if (DockingConnectorAutomation.isMagneticCapturePair(
                        pair.ship(), pair.dock())) {
                    connectorChoiceCache.put(new DockConnectorChoice(
                            ownedDock.id(), currentEntry), target);
                    return schedulerSlot(candidate);
                }
            }
            return schedulerSlot(selected);
        }
        BlockEntityPair pair = connectorPair(selected);
        return DockingConnectorAutomation.isMagneticCapturePair(pair.ship(), pair.dock())
                ? schedulerSlot(selected) : null;
    }

    // Get the dock for lease
    private @Nullable ShipDockRegistry.Dock dockForLease(
            List<ShipDockRegistry.Dock> candidates,
            ShipDockScheduler.Lease lease
    ) {
        if (lease == null || !lease.granted()) {
            return null;
        }
        ShipDockRegistry.Dock dock = candidates.stream()
                .filter(candidate -> candidate.id().equals(lease.dockId()))
                .findFirst().orElse(null);
        if (dock == null || lease.connectorKey() == null
                || !usesPhysicalDocking(dock)) {
            return dock;
        }
        ShipDockRegistry.ConnectorTarget target = dock.connectorTargets().stream()
                .filter(candidate -> schedulerSlot(dock.withConnector(candidate)).connectorKey()
                        .equals(lease.connectorKey()))
                .findFirst().orElse(null);
        if (target == null) {
            return null;
        }
        return dock.withConnector(target);
    }

    // Get the navigation candidate
    private @Nullable ShipDockRegistry.Dock navigationCandidate(
            List<ShipDockRegistry.Dock> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Vec3 origin = currentPosition();
        return candidates.stream()
                .min(Comparator
                        .comparingDouble((ShipDockRegistry.Dock candidate) ->
                                origin.distanceToSqr(navigationTarget(
                                        candidate, usesPhysicalDocking(candidate))))
                        .thenComparing(candidate -> candidate.id().toString()))
                .orElse(null);
    }

    // Select the schedule-owned route terminal before requesting any destination provision.
    private @Nullable ShipDockRegistry.Dock routeTerminalCandidate(
            List<ShipDockRegistry.Dock> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) return null;
        Vec3 origin = currentPosition();
        ShipDockRegistry.Dock selected = candidates.stream()
                .min(Comparator
                        .comparingDouble((ShipDockRegistry.Dock dock) ->
                                precalculatedRouteTarget(dock).distanceToSqr(origin))
                        .thenComparing(dock -> dock.id().toString()))
                .orElse(null);
        return selected;
    }

    // Get the pending dock candidates resolved
    private List<ShipDockRegistry.Dock> pendingDockCandidatesResolved() {
        return pendingDockCandidates.stream()
                .map(this::cachedDock)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // Get the current parking candidates
    private List<ShipDockRegistry.Dock> parkingCandidates() {
        List<ShipDockRegistry.Dock> candidates = pendingDockCandidatesResolved();
        if (candidates.isEmpty() && currentParkInstruction() != null) {
            candidates = currentRouteCandidates();
            pendingDockCandidates = candidates.stream()
                    .map(ShipDockRegistry.Dock::id)
                    .toList();
        }
        return candidates;
    }

    // Get the scheduler owned dock
    private @Nullable UUID schedulerOwnedDock() {
        if (attachedDockId != null) {
            return attachedDockId;
        }
        if (restoredDockOwnership != null) {
            return restoredDockOwnership;
        }
        return phase == Phase.DOCKING || phase == Phase.DOCKED || phase == Phase.WAITING
                ? currentDockId : null;
    }

    // Enter the dock queue
    private void enterDockQueue(
            List<ShipDockRegistry.Dock> candidates,
            ShipDockScheduler.Lease lease
    ) {
        if (controller.getLevel() == null || candidates.isEmpty()) {
            return;
        }
        ShipDockRegistry.Dock waitingDock = navigationCandidate(candidates);
        if (waitingDock == null) {
            return;
        }
        LandingZoneRequest holdingRequest = requestLandingZoneLease(
                "holding", candidates, currentHoldingDockId, currentHoldingZoneId, false);
        LandingZoneLeaseTarget holdingTarget = landingZoneForLease(holdingRequest);
        if (holdingTarget != null) {
            waitingDock = holdingTarget.dock();
            currentHoldingDockId = holdingTarget.dock().id();
            currentHoldingZoneId = holdingTarget.zone().id();
        } else {
            clearHoldingState();
        }
        currentDockId = waitingDock.id();
        boolean wasAttached = needsUndockingStabilization(attachedDockId);
        disengageAttachedConnectors();
        currentShipConnectorIndex = -1;
        connectorlessHoverTarget = null;
        phaseStartedTick = controller.getLevel().getGameTime();
        lastTargetRefreshTick = Long.MIN_VALUE;
        resetRouteProgress();
        if (wasAttached && issueUndockingControl()) {
            queueAfterUndocking = true;
            phase = Phase.UNDOCKING;
            status = "Stabilizing before joining the dock queue";
        } else {
            queueAfterUndocking = false;
            phase = Phase.WAITING_FOR_DOCK;
            issueQueueHoverNav(waitingDock, lease, holdingTarget);
            status = "Waiting for " + waitingDock.name()
                    + " (dock queue " + Math.max(1, lease.queuePosition()) + ")";
        }
        controller.setChanged();
    }

    // Update the waiting for dock
    private void tickWaitingForDock() {
        if (controller.getLevel() == null) {
            return;
        }
        List<ShipDockRegistry.Dock> candidates = pendingDockCandidatesResolved();
        if (candidates.isEmpty()) {
            skipCurrentEntry("Skipped unavailable ship dock destination");
            return;
        }
        ShipDockScheduler.Lease lease = requestDockLease(candidates);
        if (lease.granted()) {
            ShipDockRegistry.Dock target = dockForLease(candidates, lease);
            if (target != null) {
                startReservedRoute(target);
                return;
            }
        }
        ShipDockRegistry.Dock waitingDock = navigationCandidate(candidates);
        if (waitingDock == null) {
            skipCurrentEntry("Skipped unavailable ship dock destination");
            return;
        }
        LandingZoneRequest holdingRequest = requestLandingZoneLease(
                "holding", candidates, currentHoldingDockId, currentHoldingZoneId, false);
        LandingZoneLeaseTarget holdingTarget = landingZoneForLease(holdingRequest);
        if (holdingTarget != null) {
            waitingDock = holdingTarget.dock();
            currentHoldingDockId = holdingTarget.dock().id();
            currentHoldingZoneId = holdingTarget.zone().id();
        } else {
            clearHoldingState();
        }
        currentDockId = waitingDock.id();
        long now = controller.getLevel().getGameTime();
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueQueueHoverNav(waitingDock, lease, holdingTarget);
        }
        status = "Waiting for " + waitingDock.name()
                + " (dock queue " + Math.max(1, lease.queuePosition()) + ")";
    }

    // Issue the queue hover nav
    private boolean issueQueueHoverNav(
            ShipDockRegistry.Dock dock,
            ShipDockScheduler.Lease lease,
            @Nullable LandingZoneLeaseTarget holdingTarget
    ) {
        if (controller.getLevel() == null) {
            return false;
        }
        Vec3 target = holdingTarget == null
                ? dockHoldingPosition(dock, lease.holdingPlacement())
                : landingZoneTarget(holdingTarget.zone());
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        return controller.executeShipControlGraphCommand(
                QUEUE_HOVER_COMMAND, "ship_follow",
                routeControlParameters(Map.of(
                        "x", target.x, "y", target.y, "z", target.z,
                        "speed", Math.min(routeSpeed, 8.0D),
                        "follow_distance", QUEUE_HOLD_CAPTURE_RADIUS,
                        "avoid_collisions", 1.0D)));
    }

    // Acquire the dock on arrival
    private boolean acquireDockAtArrival(ShipDockRegistry.Dock currentDock) {
        List<ShipDockRegistry.Dock> candidates = pendingDockCandidatesResolved();
        if (candidates.isEmpty()) {
            candidates = List.of(currentDock);
        }
        ShipDockScheduler.Lease lease = requestDockLease(candidates);
        if (!lease.granted()) {
            enterDockQueue(candidates, lease);
            return false;
        }
        ShipDockRegistry.Dock reassigned = dockForLease(candidates, lease);
        if (reassigned == null) {
            skipCurrentEntry("Skipped unavailable ship dock destination");
            return false;
        }
        boolean sameDock = currentDock.id().equals(reassigned.id())
                && (currentDock.connectorPos() == null
                || schedulerSlot(currentDock).equals(schedulerSlot(reassigned)));
        if (!sameDock) {
            startReservedRoute(reassigned);
            return false;
        }
        return true;
    }

    // Update the dock reservation
    private void heartbeatDockReservation() {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        if (currentParkInstruction() != null
                && phase != Phase.IDLE && phase != Phase.PAUSED) {
            heartbeatParkingReservation();
            return;
        }
        if (phase == Phase.IDLE || phase == Phase.PAUSED || phase == Phase.DONE) {
            return;
        }
        // Coarse transit intentionally owns no connector lease. Provision the slot only at handoff.
        if (phase == Phase.TRANSITING
                || phase == Phase.UNDOCKING && routeTerminalTransitPending) return;
        if (pendingDockCandidates.isEmpty() && currentDockId == null
                && attachedDockId == null && restoredDockOwnership == null) {
            return;
        }
        List<ShipDockRegistry.Dock> candidates = pendingDockCandidatesResolved();
        if (candidates.isEmpty() && currentDockId != null) {
            ShipDockRegistry.Dock currentDock = cachedDock(currentDockId);
            if (currentDock != null) {
                candidates = List.of(currentDock);
            }
        }
        if (!candidates.isEmpty()) {
            ShipDockScheduler.Lease lease = requestDockLease(candidates);
            if (phase != Phase.NAVIGATING && phase != Phase.WAITING_FOR_DOCK) {
                return;
            }
            if (!lease.granted()) {
                if (phase == Phase.DOCKING) {
                    ShipDockRegistry.Dock current = cachedDock(currentDockId);
                    cancelDockingAttempt(current);
                    issueDockingRecoveryHold();
                    enterDockQueue(candidates, lease);
                } else if (phase == Phase.NAVIGATING) {
                    enterDockQueue(candidates, lease);
                }
                return;
            }
            ShipDockRegistry.Dock assigned = dockForLease(candidates, lease);
            ShipDockRegistry.Dock current = cachedDock(currentDockId);
            if (assigned != null && (current == null
                    || !schedulerSlot(current).equals(schedulerSlot(assigned)))) {
                if (phase == Phase.DOCKING) {
                    cancelDockingAttempt(current);
                    issueDockingRecoveryHold();
                }
                startReservedRoute(assigned);
            }
        }
    }

    // Update the active parking reservation
    private void heartbeatParkingReservation() {
        List<ShipDockRegistry.Dock> candidates = parkingCandidates();
        if (candidates.isEmpty() && currentDockId != null) {
            ShipDockRegistry.Dock current = cachedDock(currentDockId);
            if (current != null) {
                candidates = List.of(current);
            }
        }
        if (candidates.isEmpty()) {
            releaseReservationChannel("park");
            currentDockId = null;
            clearParkingState();
            phase = Phase.PRE_TRANSIT;
            status = "Waiting for a matching ship dock landing zone";
            return;
        }
        boolean ownsZone = currentParkingZoneId != null
                && (phase == Phase.PARKING || phase == Phase.PARKED
                || isCompletedPark() || phase == Phase.UNDOCKING);
        LandingZoneRequest request = requestLandingZoneLease(
                "park", candidates,
                ownsZone ? currentDockId : null,
                ownsZone ? currentParkingZoneId : null,
                ownsZone);
        LandingZoneLeaseTarget assigned = landingZoneForLease(request);
        if (assigned == null) {
            if (phase != Phase.WAITING_FOR_PARK) {
                enterParkQueue(candidates, request.lease());
            }
            return;
        }
        if (!assigned.dock().id().equals(currentDockId)
                || !assigned.zone().id().equals(currentParkingZoneId)) {
            startReservedParking(assigned);
        }
    }

    // Release the dock reservation
    private void releaseDockReservation() {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        ShipDockScheduler.get(controller.getLevel().getServer()).release(
                ShipDockScheduler.RequestKey.primary(pilotId));
    }

    // Release every reservation owned by this ship
    private void releaseAllReservations() {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        ShipDockScheduler.get(controller.getLevel().getServer()).release(pilotId);
    }

    // Release one reservation channel owned by this ship
    private void releaseReservationChannel(String channel) {
        if (pilotId == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        ShipDockScheduler.get(controller.getLevel().getServer()).release(
                new ShipDockScheduler.RequestKey(pilotId, channel));
    }

    // Clear the active parking target
    private void clearParkingState() {
        currentParkingZoneId = null;
        parkingTarget = null;
        parkingKind = null;
    }

    // Clear the active dock queue landing zone
    private void clearHoldingState() {
        currentHoldingDockId = null;
        currentHoldingZoneId = null;
    }

    // Get the current parking instruction
    private @Nullable ParkAtInstruction currentParkInstruction() {
        if (schedule == null || currentEntry < 0 || currentEntry >= schedule.entries.size()) {
            return null;
        }
        return schedule.entries.get(currentEntry).instruction
                instanceof ParkAtInstruction park ? park : null;
    }

    // Check if a parking phase is active
    private boolean isParkingPhase() {
        return phase == Phase.WAITING_FOR_PARK
                || phase == Phase.PARKING || phase == Phase.PARKED;
    }

    // Check if the completed schedule owns a parking zone
    private boolean isCompletedPark() {
        return phase == Phase.DONE && currentParkInstruction() != null
                && currentDockId != null && currentParkingZoneId != null
                && parkingTarget != null;
    }

    // Reset the route progress
    private void resetRouteProgress() {
        bestRouteDistance = Double.POSITIVE_INFINITY;
        lastRouteProgressTick = Long.MIN_VALUE;
    }

    // Reset the route progress
    private void resetRouteProgress(ShipDockRegistry.Dock dock) {
        if (dock == null || controller.getLevel() == null) {
            resetRouteProgress();
            return;
        }
        double distance = currentPosition().distanceTo(routeProgressTarget(dock));
        bestRouteDistance = Double.isFinite(distance)
                ? Math.max(0.0D, distance) : Double.POSITIVE_INFINITY;
        lastRouteProgressTick = controller.getLevel().getGameTime();
    }

    // Check if the route reservation was lost
    private boolean routeReservationLost(ShipDockRegistry.Dock dock, long now) {
        if (dock == null) {
            return false;
        }
        double distance = currentPosition().distanceTo(routeProgressTarget(dock));
        if (!Double.isFinite(distance)) {
            return false;
        }
        distance = Math.max(0.0D, distance);
        if (!Double.isFinite(bestRouteDistance)) {
            bestRouteDistance = distance;
            lastRouteProgressTick = now;
            return false;
        }
        if (distance + ROUTE_PROGRESS_DISTANCE < bestRouteDistance) {
            bestRouteDistance = distance;
            lastRouteProgressTick = now;
            return false;
        }
        long stalledFor = Math.max(0L, now - lastRouteProgressTick);
        double divergenceAllowance = Math.max(ROUTE_DIVERGENCE_DISTANCE,
                Math.min(512.0D, routeSpeed * 20.0D));
        return (distance > bestRouteDistance + divergenceAllowance
                && stalledFor >= ROUTE_DIVERGENCE_GRACE_TICKS)
                || stalledFor >= ROUTE_PROGRESS_STALL_TICKS;
    }

    // Track progress against the route phase which currently owns translation.
    private Vec3 routeProgressTarget(ShipDockRegistry.Dock dock) {
        return phase == Phase.TRANSITING || routeTerminalTransitPending
                ? scheduleRouteTerminal(dock).position() : navigationTarget(dock);
    }

    // Replan a lost coarse route without cooling down a connector which was never assigned.
    private void pauseLostRouteTerminalTransit() {
        issueDockingRecoveryHold();
        resetRouteProgress();
        currentDockId = null;
        currentShipConnectorIndex = -1;
        routeTerminalTransitPending = false;
        magneticCaptureActive = false;
        phase = Phase.PRE_TRANSIT;
        status = "Route progress was lost; stabilizing and replanning";
        controller.setChanged();
    }

    // Pause the lost dock approach
    private void pauseLostDockApproach(ShipDockRegistry.Dock dock) {
        issueDockingRecoveryHold();
        coolDownDockingSlot(dock, LOST_ROUTE_SLOT_COOLDOWN_TICKS);
        releaseDockReservation();
        resetRouteProgress();
        currentDockId = null;
        currentShipConnectorIndex = -1;
        routeTerminalTransitPending = false;
        magneticCaptureActive = false;
        phase = Phase.PRE_TRANSIT;
        status = "Route progress was lost; stabilizing and replanning";
        controller.setChanged();
    }

    // Retry or skip the unavailable dock
    private void retryOrSkipUnavailableDock(UUID unavailableDockId) {
        List<ShipDockRegistry.Dock> alternatives = pendingDockCandidates.stream()
                .filter(id -> !id.equals(unavailableDockId))
                .map(this::cachedDock)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!alternatives.isEmpty()) {
            beginRoute(alternatives, divertedTargetId == null);
            return;
        }
        skipCurrentEntry("Skipped unavailable ship dock destination");
    }

    // Skip the current entry
    private void skipCurrentEntry(String msg) {
        controller.executeShipControlGraphCommand(
                "shipping_schedule_stop", "ship_stop", Map.of());
        disengageAttachedConnectors();
        releaseAllReservations();
        clearParkingState();
        clearHoldingState();
        if (autoRefuelActive) {
            abortAutoRefuel("Auto Refuel paused the schedule: " + msg);
            return;
        }
        currentDockId = null;
        divertedTargetId = null;
        clearAutoRefuelInterruption();
        connectorlessHoverTarget = null;
        pendingDockCandidates = List.of();
        status = msg;
        advanceEntry();
    }

    // Update the undocking
    private void tickUndocking() {
        if (controller.getLevel() == null || currentDockId == null) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        if (controller.getLevel().getGameTime() - phaseStartedTick
                < UNDOCK_SETTLE_TICKS) {
            return;
        }
        if (queueAfterUndocking) {
            queueAfterUndocking = false;
            controller.executeShipControlGraphCommand(
                    "shipping_schedule_undock_release", "ship_stop", Map.of());
            if (currentParkInstruction() != null) {
                phase = Phase.WAITING_FOR_PARK;
                tickWaitingForPark();
            } else {
                phase = Phase.WAITING_FOR_DOCK;
                tickWaitingForDock();
            }
            return;
        }
        if (routeTerminalTransitPending) {
            ShipDockRegistry.Dock dock = cachedDock(currentDockId);
            controller.executeShipControlGraphCommand(
                    "shipping_schedule_undock_release", "ship_stop", Map.of());
            if (dock == null) {
                retryOrSkipUnavailableDock(currentDockId);
                return;
            }
            if (!issueRouteTerminalTransit(dock)) {
                routeTerminalTransitPending = false;
                resetRouteProgress();
                issueDockingRecoveryHold();
                phase = Phase.PRE_TRANSIT;
                status = "Route control unavailable; stabilizing and retrying";
                return;
            }
            phase = Phase.TRANSITING;
            phaseStartedTick = controller.getLevel().getGameTime();
            resetRouteProgress(dock);
            status = "Following the planned route toward " + dock.name();
            controller.setChanged();
            return;
        }
        if (currentParkInstruction() != null && currentParkingZoneId != null
                && parkingTarget != null) {
            controller.executeShipControlGraphCommand(
                    "shipping_schedule_undock_release", "ship_stop", Map.of());
            phase = Phase.PARKING;
            phaseStartedTick = controller.getLevel().getGameTime();
            status = issueParkingNavigation(parkingTarget)
                    ? "Parking after undocking"
                    : "Parking control unavailable; stabilizing and retrying";
            controller.setChanged();
            return;
        }
        ShipDockRegistry.Dock dock = cachedDock(currentDockId);
        controller.executeShipControlGraphCommand(
                "shipping_schedule_undock_release", "ship_stop", Map.of());
        if (dock == null) {
            retryOrSkipUnavailableDock(currentDockId);
            return;
        }
        if (!issueNavigation(dock)) {
            releaseDockReservation();
            resetRouteProgress();
            issueDockingRecoveryHold();
            phase = Phase.PRE_TRANSIT;
            status = "Navigation control unavailable; stabilizing and retrying";
            return;
        }
        phase = Phase.NAVIGATING;
        phaseStartedTick = controller.getLevel().getGameTime();
        resetRouteProgress(dock);
        status = "Navigating to " + dock.name();
        controller.setChanged();
    }

    // Hand a completed cached route to the stable live dock reservation and approach flow.
    private void tickRouteTerminalTransit() {
        if (controller.getLevel() == null || currentDockId == null) {
            routeTerminalTransitPending = false;
            phase = Phase.PRE_TRANSIT;
            return;
        }
        ShipDockRegistry.Dock dock = cachedDock(currentDockId);
        if (dock == null) {
            retryOrSkipUnavailableDock(currentDockId);
            return;
        }
        ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(controller, routeThrottle);
        if (!autoRefuel.enabled() && !dock.refuel()
                && fuel.capacity() > 0.0D && fuel.ratio() <= FUEL_RESERVE) {
            routeTerminalTransitPending = false;
            resetRouteProgress();
            phase = Phase.PAUSED;
            status = "Shipping schedule stopped at the 10% fuel reserve";
            controller.executeShipControlGraphCommand(
                    "shipping_schedule_stop", "ship_stop", Map.of());
            return;
        }
        long now = controller.getLevel().getGameTime();
        if (routeReservationLost(dock, now)) {
            pauseLostRouteTerminalTransit();
            return;
        }
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueRouteTerminalTransit(dock);
        }
        if (!controller.isShipControlGraphCommandComplete(
                ROUTE_TERMINAL_COMMAND, "ship_navigate")) return;
        routeTerminalTransitPending = false;
        status = usesPhysicalDocking(dock)
                ? "Assigning a docking connector at " + dock.name()
                : "Provisioning arrival at " + dock.name();
        // The schedule selected this dock before calculated transit began. Reopening the complete
        // candidate set here could provision a different dock and make live approach disagree with
        // the terminal just reached, producing a route/final-navigation oscillation.
        beginLiveDockApproach(List.of(dock));
        controller.setChanged();
    }

    // Update the navigation
    private void tickNavigation() {
        if (controller.getLevel() == null || currentDockId == null) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        ShipDockRegistry.Dock dock = cachedDock(currentDockId);
        if (dock == null) {
            retryOrSkipUnavailableDock(currentDockId);
            return;
        }
        ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(controller, routeThrottle);
        if (!autoRefuel.enabled() && !dock.refuel()
                && fuel.capacity() > 0.0D && fuel.ratio() <= FUEL_RESERVE) {
            releaseDockReservation();
            resetRouteProgress();
            phase = Phase.PAUSED;
            status = "Shipping schedule stopped at the 10% fuel reserve";
            controller.executeShipControlGraphCommand("shipping_schedule_stop", "ship_stop", Map.of());
            return;
        }
        long now = controller.getLevel().getGameTime();
        if (routeReservationLost(dock, now)) {
            pauseLostDockApproach(dock);
            return;
        }
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueNavigation(dock);
        }
        if (!hasUsableConnectorPair(dock)) {
            Vec3 target = navigationTarget(dock, false);
            if (!connectorlessHoverTargetReached(
                    currentPosition(), target, connectorlessArrivalTolerance())) {
                return;
            }
            if (!acquireDockAtArrival(dock)) {
                return;
            }
            currentShipConnectorIndex = -1;
            connectorlessHoverTarget = target;
            attachedDockId = null;
            phase = Phase.DOCKED;
            lastStopName = dock.name();
            initConditions();
            status = "Hovering at " + dock.name();
            controller.setChanged();
            return;
        }
        if (!controller.isShipControlGraphCommandComplete(NAVIGATION_COMMAND, "ship_navigate")) {
            return;
        }
        if (!acquireDockAtArrival(dock)) {
            return;
        }
        if (!issueDocking(dock)) {
            releaseDockReservation();
            resetRouteProgress();
            issueDockingRecoveryHold();
            phase = Phase.PRE_TRANSIT;
            status = "Docking control unavailable; replanning the route";
            return;
        }
        controller.activateShipDockingConnector(currentShipConnectorIndex);
        phase = Phase.DOCKING;
        phaseStartedTick = now;
        lastDockingProgressTick = now;
        bestDockingDistance = Double.POSITIVE_INFINITY;
        magneticCaptureActive = false;
        status = "Docking at " + dock.name();
        controller.setChanged();
    }

    // Update the docking
    private void tickDocking() {
        if (controller.getLevel() == null || currentDockId == null) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        // ------------------------------------DOCK VALIDATION------------------------------------
        ShipDockRegistry.Dock dock = cachedDock(currentDockId);
        if (dock == null) {
            retryOrSkipUnavailableDock(currentDockId);
            return;
        }
        if (!hasUsableConnectorPair(dock)) {
            cancelDockingAttempt(dock);
            issueDockingRecoveryHold();
            attachedDockId = null;
            dockingRetryCount = 0;
            lastDockingProgressTick = Long.MIN_VALUE;
            bestDockingDistance = Double.POSITIVE_INFINITY;
            releaseDockReservation();
            List<ShipDockRegistry.Dock> candidates = pendingDockCandidates.stream()
                    .map(this::cachedDock)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!candidates.isEmpty()) {
                currentDockId = null;
                currentShipConnectorIndex = -1;
                phase = Phase.PRE_TRANSIT;
                beginLiveDockApproach(candidates);
                status = "Docking connector unloaded; reserving an available connector";
            } else {
                phase = Phase.PRE_TRANSIT;
                status = "Docking connector unloaded; replanning the route";
            }
            controller.setChanged();
            return;
        }
        // ------------------------------------MAGNETIC CAPTURE------------------------------------
        controller.activateShipDockingConnector(currentShipConnectorIndex);
        BlockEntityPair pair = connectorPair(dock);
        configConnectorTransfers(dock, pair);
        boolean magneticPowered = DockingConnectorAutomation.engage(pair.ship(), pair.dock());
        ShipControlModuleRuntime.MappedDockingConnector mappedConnector =
                controller.getShipDockingConnector(currentShipConnectorIndex);
        // ------------------------------------CAPTURE TELEMETRY------------------------------------
        double dockingDistance = mappedConnector == null
                ? Double.POSITIVE_INFINITY
                : mappedConnector.worldTipPosition().distanceTo(dock.dockingTarget());
        double dockingAlignment = mappedConnector == null
                ? -1.0D : mappedConnector.worldFacing().dot(
                dock.connectorFacing().scale(-1.0D));
        boolean magneticCapture = magneticPowered
                && dockingDistance <= MAGNETIC_CAPTURE_RANGE
                && dockingAlignment >= MAGNETIC_CAPTURE_ALIGNMENT
                && DockingConnectorAutomation.isMagneticCapturePair(pair.ship(), pair.dock());
        if (magneticCapture && !magneticCaptureActive) {
            magneticCaptureActive = true;
            controller.setShipDockingMagneticCapture(currentShipConnectorIndex, true);
            status = "Magnetic capture at " + dock.name();
            controller.setChanged();
        } else if (!magneticCapture && magneticCaptureActive) {
            magneticCaptureActive = false;
            controller.setShipDockingMagneticCapture(currentShipConnectorIndex, false);
            lastTargetRefreshTick = Long.MIN_VALUE;
        }
        // ------------------------------------DOCKING PROGRESS------------------------------------
        long now = controller.getLevel().getGameTime();
        if (Double.isFinite(dockingDistance)
                && dockingDistance + 0.05D < bestDockingDistance) {
            bestDockingDistance = dockingDistance;
            lastDockingProgressTick = now;
        }
        if (!magneticCaptureActive && intervalElapsed(now, lastTargetRefreshTick, 5L)) {
            issueDocking(dock);
        }
        if (DockingConnectorAutomation.isLockedPair(pair.ship(), pair.dock())) {
            failedDockingSlots.remove(schedulerSlot(dock));
            attachedDockId = dock.id();
            controller.executeShipControlGraphCommand(
                    "shipping_schedule_hold", "ship_stop", Map.of());
            phase = Phase.DOCKED;
            dockingRetryCount = 0;
            lastDockingProgressTick = Long.MIN_VALUE;
            bestDockingDistance = Double.POSITIVE_INFINITY;
            magneticCaptureActive = false;
            controller.setShipDockingMagneticCapture(currentShipConnectorIndex, false);
            lastStopName = dock.name();
            initConditions();
            status = "Docked at " + dock.name();
            controller.setChanged();
            return;
        }
        // -----------------------------------------------------STALL CHECK-----------------------------------------------------
        boolean stalled = lastDockingProgressTick != Long.MIN_VALUE
                && now - lastDockingProgressTick > DOCKING_STALL_TICKS;
        if (stalled || now - phaseStartedTick > 600L) {
            recoverFromFailedDocking(dock, now);
        }
    }

    // Get the size-aware dock holding position
    private Vec3 dockHoldingPosition(
            ShipDockRegistry.Dock dock,
            ShipDockScheduler.HoldingPlacement placement
    ) {
        ShipDockScheduler.HoldingPlacement safe = placement == null
                ? new ShipDockScheduler.HoldingPlacement(16.0D, -10.0D, 0.0D)
                : placement;
        Vec3 outward = dock.hasDockingConnector() ? dock.connectorFacing() : dock.facing();
        Vec3 horizontal = new Vec3(outward.x, 0.0D, outward.z);
        if (horizontal.lengthSqr() < 1.0E-9D) {
            horizontal = new Vec3(dock.facing().x, 0.0D, dock.facing().z);
        }
        horizontal = horizontal.lengthSqr() < 1.0E-9D
                ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
        double lowerHullClearance = Math.max(0.0D,
                vesselEnvelope().bottomOffset() - 0.5D);
        double routeClearance = Math.max(8.0D,
                vesselEnvelope().horizontalRadius() * 2.0D
                        + QUEUE_ROUTE_CLEARANCE_PADDING);
        List<List<Vec3>> routes = controller.shippingScheduleRoutePolylines(
                currentEntry, precalculatedRouteTarget(dock), 1.0E-4D);
        return ShipDockScheduler.resolveHoldingPosition(
                dock.approach(), horizontal, safe,
                safe.vertical() + lowerHullClearance,
                routes, routeClearance);
    }

    // Recover from failed docking
    private void recoverFromFailedDocking(ShipDockRegistry.Dock dock, long now) {
        cancelDockingAttempt(dock);
        issueDockingRecoveryHold();
        dockingRetryCount++;
        lastDockingProgressTick = Long.MIN_VALUE;
        bestDockingDistance = Double.POSITIVE_INFINITY;
        magneticCaptureActive = false;
        if (dockingRetryCount >= MAX_DOCKING_RETRIES_PER_CONNECTOR) {
            dockingRetryCount = 0;
            coolDownDockingSlot(dock, FAILED_DOCKING_SLOT_COOLDOWN_TICKS);
            releaseDockReservation();
            List<ShipDockRegistry.Dock> candidates = pendingDockCandidates.stream()
                    .map(this::cachedDock)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!candidates.isEmpty()) {
                currentDockId = null;
                currentShipConnectorIndex = -1;
                phase = Phase.PRE_TRANSIT;
                beginLiveDockApproach(candidates);
                status = "Docking path blocked; selecting another available connector";
                return;
            }
        }
        if (issueNavigation(dock)) {
            phase = Phase.NAVIGATING;
            phaseStartedTick = now;
            status = "Docking path blocked; returning to the collision-safe approach";
        } else {
            phase = Phase.PRE_TRANSIT;
            phaseStartedTick = now;
            status = "Docking path blocked; replanning the route";
        }
        controller.setChanged();
    }

    // Issue the docking recovery hold
    private void issueDockingRecoveryHold() {
        if (controller.getLevel() == null) {
            return;
        }
        controller.executeShipControlGraphCommand(
                "shipping_schedule_dock_recovery_stop", "ship_stop", Map.of());
        controller.executeShipControlGraphCommand(
                "shipping_schedule_dock_recovery_brake", "ship_decelerate",
                Map.of("strength", 1.0D));
        controller.executeShipControlGraphCommand(
                "shipping_schedule_dock_recovery_hold", "ship_hover",
                Map.of("y", currentPosition().y, "strength", 1.0D));
        dockingRecoveryControlActive = true;
    }

    // Clear recovery-only commands before starting a new movement command. A
    // stop is intentionally issued once at the transition: ship commands are
    // composable, so otherwise the recovery brake and hover keep opposing the
    // replacement navigation or docking command indefinitely.
    private void clearDockingRecoveryControl() {
        if (!dockingRecoveryControlActive || controller.getLevel() == null) {
            return;
        }
        if (controller.executeShipControlGraphCommand(
                "shipping_schedule_dock_recovery_clear", "ship_stop", Map.of())) {
            dockingRecoveryControlActive = false;
        }
    }

    // Follow the reusable schedule route to its dock-specific handoff region.
    private boolean issueRouteTerminalTransit(ShipDockRegistry.Dock dock) {
        if (controller.getLevel() == null || dock == null) return false;
        clearDockingRecoveryControl();
        SablePathfinder.RouteTerminal terminal = scheduleRouteTerminal(dock);
        Vec3 target = terminal.position();
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        return controller.executeShipControlGraphCommand(
                ROUTE_TERMINAL_COMMAND, "ship_navigate",
                routeControlParameters(Map.of(
                        "x", target.x, "y", target.y, "z", target.z,
                        "speed", routeSpeed,
                        "tolerance", terminal.captureRadius(),
                        "avoid_collisions", 1.0D,
                        "schedule_route_entry", (double) currentEntry)),
                Map.of("target_point", "center_of_mass"));
    }

    // Issue the live final approach selected by the dock scheduler.
    private boolean issueNavigation(ShipDockRegistry.Dock dock) {
        if (controller.getLevel() == null) {
            return false;
        }
        clearDockingRecoveryControl();
        boolean useDockingConnector = hasUsableConnectorPair(dock);
        Vec3 approach = navigationTarget(dock);
        if (!useDockingConnector) {
            connectorlessHoverTarget = approach;
            return issueConnectorlessHover(approach);
        }
        connectorlessHoverTarget = null;
        Vec3 dockingDirection = useDockingConnector
                ? dock.connectorFacing().scale(-1.0D) : Vec3.ZERO;
        Vec3 dockingUp = useDockingConnector ? dock.connectorUp() : Vec3.ZERO;
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        Map<String, Double> values = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("x", approach.x), Map.entry("y", approach.y),
                Map.entry("z", approach.z), Map.entry("speed", routeSpeed),
                Map.entry("tolerance", 1.25D),
                Map.entry("avoid_collisions", 1.0D),
                Map.entry("lock_rotation", 0.0D),
                Map.entry("target_direction_x", dockingDirection.x),
                Map.entry("target_direction_y", dockingDirection.y),
                Map.entry("target_direction_z", dockingDirection.z),
                Map.entry("target_up_x", dockingUp.x),
                Map.entry("target_up_y", dockingUp.y),
                Map.entry("target_up_z", dockingUp.z)));
        if (!useDockingConnector) {
            values.put("schedule_route_entry", (double) currentEntry);
        }
        return controller.executeShipControlGraphCommand(
                NAVIGATION_COMMAND, "ship_navigate",
                routeControlParameters(values),
                Map.of("target_point", useDockingConnector
                        ? "docking_connector:" + currentShipConnectorIndex
                        : "center_of_mass"));
    }

    // Issue the connectorless hover
    private boolean issueConnectorlessHover(Vec3 target) {
        if (controller.getLevel() == null || target == null) {
            return false;
        }
        clearDockingRecoveryControl();
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        return controller.executeShipControlGraphCommand(
                NAVIGATION_COMMAND, "ship_follow",
                routeControlParameters(Map.of(
                        "x", target.x, "y", target.y, "z", target.z,
                        "speed", routeSpeed, "follow_distance", 0.0D,
                        "avoid_collisions", 1.0D,
                        "schedule_route_entry", (double) currentEntry)));
    }

    // Maintain the connectorless stop
    private void maintainConnectorlessHover() {
        if (controller.getLevel() == null || connectorlessHoverTarget == null) {
            return;
        }
        long now = controller.getLevel().getGameTime();
        if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
            issueConnectorlessHover(connectorlessHoverTarget);
        }
    }

    // Maintain the final completed stop
    private void maintainCompletedStop() {
        if (isCompletedPark()) {
            ShipDockRegistry.Dock dock = cachedDock(currentDockId);
            ShipDockRegistry.LandingZoneTarget zone = landingZoneById(
                    dock, currentParkingZoneId);
            if (zone != null) {
                parkingKind = zone.airborne()
                        ? ParkingKind.AIRBORNE : ParkingKind.GROUND;
                parkingTarget = landingZoneTarget(zone);
                long now = controller.getLevel() == null
                        ? Long.MIN_VALUE : controller.getLevel().getGameTime();
                if (intervalElapsed(now, lastTargetRefreshTick, 20L)) {
                    issueParkingHold();
                }
            }
            return;
        }
        maintainConnectorlessHover();
    }

    // Issue the undocking control
    private boolean issueUndockingControl() {
        if (controller.getLevel() == null) {
            return false;
        }
        controller.executeShipControlGraphCommand(
                "shipping_schedule_undock_reset", "ship_stop", Map.of());
        boolean braking = controller.executeShipControlGraphCommand(
                UNDOCK_BRAKE_COMMAND, "ship_decelerate",
                Map.of("strength", 1.0D));
        boolean hovering = controller.executeShipControlGraphCommand(
                UNDOCK_HOVER_COMMAND, "ship_hover",
                Map.of("strength", 1.0D));
        return braking && hovering;
    }

    // Issue the docking
    private boolean issueDocking(ShipDockRegistry.Dock dock) {
        if (controller.getLevel() == null || dock.connectorFacing() == null
                || dock.connectorUp() == null) {
            return false;
        }
        clearDockingRecoveryControl();
        controller.setShipDockingMagneticCapture(currentShipConnectorIndex, false);
        Vec3 target = dock.dockingTarget();
        Vec3 facing = dock.connectorFacing().scale(-1.0D);
        Vec3 up = dock.connectorUp();
        lastTargetRefreshTick = controller.getLevel().getGameTime();
        return controller.executeShipControlGraphCommand(
                DOCKING_COMMAND, "ship_dock",
                routeControlParameters(Map.ofEntries(
                        Map.entry("x", target.x), Map.entry("y", target.y),
                        Map.entry("z", target.z),
                        Map.entry("speed", Math.min(routeSpeed, MAX_DOCKING_SPEED)),
                        Map.entry("tolerance", 0.18D),
                        Map.entry("avoid_collisions", 1.0D),
                        Map.entry("target_direction_x", facing.x),
                        Map.entry("target_direction_y", facing.y),
                        Map.entry("target_direction_z", facing.z),
                        Map.entry("target_up_x", up.x),
                        Map.entry("target_up_y", up.y),
                        Map.entry("target_up_z", up.z))),
                Map.of("target_point", "docking_connector:" + currentShipConnectorIndex));
    }

    // The schedule's Change Throttle value is the direct propulsion request
    // for every vehicle mode. Target speed remains the physical navigation and
    // stopping limit, while this value controls how strongly the configured
    // Forward/Backward/Strafe or Acceleration channels drive toward that limit.
    private Map<String, Double> routeControlParameters(Map<String, Double> values) {
        Map<String, Double> routed = new LinkedHashMap<>(values);
        routed.put("drive_throttle", routeThrottle);
        return Map.copyOf(routed);
    }

    // Update the docked
    private void tickDocked() {
        // Check the active dock and connector
        if (schedule == null || controller.getLevel() == null || currentDockId == null) {
            phase = Phase.PRE_TRANSIT;
            return;
        }
        if (connectorlessHoverTarget != null) {
            maintainConnectorlessHover();
        }
        ShipDockRegistry.Dock dock = cachedDock(currentDockId);
        if (dock == null) {
            retryOrSkipUnavailableDock(currentDockId);
            return;
        }
        lastStopName = dock.name();
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        TransferPlan transferPlan = activeTransferPlan(dock, entry);
        boolean connected = hasUsableConnectorPair(dock);
        if (dock.hasDockingConnector() && currentShipConnectorIndex >= 0 && !connected) {
            cancelDockingAttempt(dock);
            attachedDockId = null;
            releaseDockReservation();
            List<ShipDockRegistry.Dock> candidates = pendingDockCandidates.stream()
                    .map(this::cachedDock)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!candidates.isEmpty()) {
                beginLiveDockApproach(candidates);
                status = "Docking connection was lost; reserving an available connector";
            } else {
                phase = Phase.PRE_TRANSIT;
                status = "Docking connection was lost; replanning the route";
            }
            controller.setChanged();
            return;
        }
        // Lock the connector pair
        if (connected) {
            BlockEntityPair connectorPair = connectorPair(dock);
            configConnectorTransfers(dock, connectorPair, transferPlan);
            boolean locked = DockingConnectorAutomation.isLockedPair(
                    connectorPair.ship(), connectorPair.dock());
            if (!locked) {
                DockingConnectorAutomation.engage(connectorPair.ship(), connectorPair.dock());
                locked = DockingConnectorAutomation.isLockedPair(
                        connectorPair.ship(), connectorPair.dock());
            }
            if (!locked) {
                phase = Phase.DOCKING;
                phaseStartedTick = controller.getLevel().getGameTime();
                magneticCaptureActive = false;
                status = "Re-engaging docking connectors at " + dock.name();
                return;
            }
        }
        if (autoRefuelActive && autoRefuelReturning) {
            resumeInterruptedSchedule();
            return;
        }
        // Transfer dock cargo and fuel
        int exchanged = 0;
        if (connected && WirelessDockingTransfer.isEnabled(
                controller.getLevel().getServer())) {
            ShipControlModuleRuntime.MappedDockingConnector connector =
                    controller.getShipDockingConnector(currentShipConnectorIndex);
            exchanged += ShipCargoAutomation.wirelessDockingTransfer(
                    controller, dock, connector, true,
                    transferPlan.items() == TransferDirection.PICKUP,
                    transferPlan.fluids() == TransferDirection.PICKUP,
                    transferPlan.energy() == TransferDirection.PICKUP,
                    transferPlan.fuelRun(),
                    transferPlan.itemFilters(), transferPlan.fluidFilters());
            exchanged += ShipCargoAutomation.wirelessDockingTransfer(
                    controller, dock, connector, false,
                    transferPlan.items() == TransferDirection.DROPOFF,
                    transferPlan.fluids() == TransferDirection.DROPOFF,
                    transferPlan.energy() == TransferDirection.DROPOFF,
                    transferPlan.fuelRun(),
                    transferPlan.itemFilters(), transferPlan.fluidFilters());
        } else {
            boolean pickupFluids = transferPlan.fluids() == TransferDirection.PICKUP;
            boolean pickupEnergy = transferPlan.energy() == TransferDirection.PICKUP;
            if (connected && dock.refuel() && (pickupFluids || pickupEnergy)) {
                exchanged += ShipCargoAutomation.refuel(
                        controller, dock, transferPlan.fluidFilters(),
                        pickupFluids, pickupEnergy);
            }
            if (connected && dock.refuel() && transferPlan.fuelRun()
                    && transferPlan.items() == TransferDirection.PICKUP) {
                exchanged += ShipCargoAutomation.refuelItemsFromDock(
                        controller, dock, transferPlan.itemFilters());
            }
            if (connected && dock.restock()
                    && transferPlan.items() == TransferDirection.PICKUP) {
                exchanged += ShipCargoAutomation.restock(
                        controller, dock, transferPlan.itemFilters());
            }
        }
        if (connected && transferPlan.fuelRun()) {
            exchanged += ShipCargoAutomation.refuelFromShipRuns(
                    controller, transferPlan.itemFilters(), transferPlan.fluidFilters(),
                    transferPlan.items() == TransferDirection.PICKUP,
                    transferPlan.fluids() == TransferDirection.PICKUP,
                    transferPlan.energy() == TransferDirection.PICKUP);
        }
        if (connected && entry.instruction instanceof DeliverPackagesInstruction && dock.packages()) {
            exchanged += ShipCargoAutomation.deliverPackages(controller, dock);
        } else if (connected && entry.instruction instanceof FetchPackagesInstruction fetch && dock.packages()) {
            exchanged += ShipCargoAutomation.collectPackages(controller, dock, fetch.getFilter());
        }
        if (exchanged > 0) {
            lastCargoExchangeTick = controller.getLevel().getGameTime();
        }

        // Resolve auto refuel and route diversions
        if (autoRefuelActive) {
            ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(
                    controller, routeThrottle);
            if (!fuelRefillComplete(fuel)) {
                phase = Phase.WAITING;
                status = "Auto Refuel at " + dock.name() + " ("
                        + Math.round(fuel.ratio() * 100.0D) + "%)";
                return;
            }
            beginAutoRefuelReturn(dock);
            return;
        }

        if (divertedTargetId != null) {
            ShipDockRegistry.Dock resumed = cachedDock(divertedTargetId);
            if (resumed == null) {
                divertedTargetId = null;
                skipCurrentEntry("Skipped unavailable ship dock destination");
                return;
            }
            ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(controller, routeThrottle);
            if (fuel.canReach(currentPosition().distanceTo(resumed.approach()), routeSpeed)) {
                divertedTargetId = null;
                beginRoute(List.of(resumed), false);
            } else {
                phase = Phase.WAITING;
                status = "Refueling at " + dock.name() + " before resuming the route";
            }
            return;
        }

        // Finish the current schedule entry
        if (entry.instruction instanceof RefuelIfInstruction) {
            ShipCargoAutomation.FuelStatus fuel = ShipCargoAutomation.fuelStatus(
                    controller, routeThrottle);
            if (!fuelRefillComplete(fuel)) {
                phase = Phase.WAITING;
                status = "Refueling at " + dock.name() + " ("
                        + Math.round(fuel.ratio() * 100.0D) + "%)";
                return;
            }
            status = "Refueling complete at " + dock.name();
            advanceEntry();
            return;
        }

        if (entry.conditions.isEmpty() || conditionsComplete(entry.conditions)) {
            advanceEntry();
        } else {
            phase = Phase.WAITING;
            status = "Waiting at " + dock.name();
        }
    }

    // Begin the auto refuel return
    private void beginAutoRefuelReturn(ShipDockRegistry.Dock fuelDock) {
        if (controller.getLevel() == null) {
            return;
        }
        ShipDockRegistry.Dock resumeDock = cachedDock(autoRefuelResumeDockId);
        if (resumeDock == null || resumeDock.id().equals(fuelDock.id())) {
            resumeInterruptedSchedule();
            return;
        }
        autoRefuelReturning = true;
        beginRoute(List.of(resumeDock), false);
        status = "Auto Refuel complete; returning to " + resumeDock.name();
    }

    // Resume the interrupted schedule
    private void resumeInterruptedSchedule() {
        if (schedule == null || controller.getLevel() == null) {
            clearAutoRefuelInterruption();
            return;
        }
        int restoredEntry = Math.max(firstOperationalEntry(), Math.min(
                autoRefuelResumeEntry, schedule.entries.size() - 1));
        Phase restoredPhase = autoRefuelResumePhase;
        UUID restoredDock = autoRefuelResumeDockId;
        int[] restoredProgress = Arrays.copyOf(autoRefuelResumeConditionProgress,
                autoRefuelResumeConditionProgress.length);
        long[] restoredStarted = Arrays.copyOf(autoRefuelResumeConditionStarted,
                autoRefuelResumeConditionStarted.length);
        long interruptionTicks = Math.max(0L,
                controller.getLevel().getGameTime() - autoRefuelStartedTick);
        for (int idx = 0; idx < restoredStarted.length; idx++) {
            restoredStarted[idx] += interruptionTicks;
        }
        long restoredCargoTick = autoRefuelResumeLastCargoExchange + interruptionTicks;
        boolean resumedAtTarget = restoredDock != null
                && restoredDock.equals(currentDockId);
        boolean restoreDockedState = resumedAtTarget
                && (restoredPhase == Phase.DOCKED || restoredPhase == Phase.WAITING);
        currentEntry = restoredEntry;
        if (restoreDockedState) {
            conditionProgress = restoredProgress;
            conditionStarted = restoredStarted;
            lastCargoExchangeTick = restoredCargoTick;
        }
        pendingDockCandidates = List.of();
        failedDockingSlots.clear();
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        divertedTargetId = null;
        clearAutoRefuelInterruption();
        if (restoreDockedState) {
            phase = restoredPhase == Phase.WAITING ? Phase.WAITING : Phase.DOCKED;
            status = "Auto Refuel complete; resumed the interrupted manifest task";
        } else {
            currentDockId = null;
            phase = Phase.PRE_TRANSIT;
            status = "Auto Refuel complete; resuming the interrupted manifest task";
        }
        schedule.savedProgress = currentEntry;
        controller.setChanged();
    }

    // Abort the auto refuel
    private void abortAutoRefuel(String msg) {
        if (!autoRefuelActive || schedule == null) {
            return;
        }
        currentEntry = Math.max(firstOperationalEntry(), Math.min(
                autoRefuelResumeEntry, schedule.entries.size() - 1));
        conditionProgress = Arrays.copyOf(autoRefuelResumeConditionProgress,
                autoRefuelResumeConditionProgress.length);
        conditionStarted = Arrays.copyOf(autoRefuelResumeConditionStarted,
                autoRefuelResumeConditionStarted.length);
        lastCargoExchangeTick = autoRefuelResumeLastCargoExchange;
        clearAutoRefuelInterruption();
        releaseAllReservations();
        resetRouteProgress();
        currentDockId = null;
        pendingDockCandidates = List.of();
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        phaseBeforePause = Phase.PRE_TRANSIT;
        phase = Phase.PAUSED;
        status = msg;
        schedule.savedProgress = currentEntry;
        controller.setChanged();
    }

    // Initialize the conditions
    private void initConditions() {
        if (schedule == null || controller.getLevel() == null) {
            return;
        }
        List<List<ScheduleWaitCondition>> columns = schedule.entries.get(currentEntry).conditions;
        conditionProgress = new int[columns.size()];
        conditionStarted = new long[columns.size()];
        Arrays.fill(conditionStarted, controller.getLevel().getGameTime());
        lastCargoExchangeTick = controller.getLevel().getGameTime();
    }

    // Configure the connector transfers
    private void configConnectorTransfers(ShipDockRegistry.Dock dock, BlockEntityPair pair) {
        if (dock == null || pair == null || schedule == null
                || currentEntry < 0 || currentEntry >= schedule.entries.size()) {
            return;
        }
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        configConnectorTransfers(dock, pair, activeTransferPlan(dock, entry));
    }

    // Configure the connector transfers
    private void configConnectorTransfers(
            ShipDockRegistry.Dock dock,
            BlockEntityPair pair,
            TransferPlan transferPlan
    ) {
        if (dock == null || pair == null || schedule == null
                || currentEntry < 0 || currentEntry >= schedule.entries.size()) {
            return;
        }
        ScheduleEntry entry = schedule.entries.get(currentEntry);
        boolean collectPackages = entry.instruction instanceof FetchPackagesInstruction
                && dock.packages();
        String collectionAddress = entry.instruction instanceof FetchPackagesInstruction fetch
                ? fetch.getFilter() : "";
        DockingConnectorAutomation.configureTransfers(
                pair.dock(), dock.restock()
                        && transferPlan.items() == TransferDirection.PICKUP,
                collectPackages, collectionAddress,
                dock.refuel() && transferPlan.fluids() == TransferDirection.PICKUP,
                dock.refuel() && transferPlan.energy() == TransferDirection.PICKUP);
        DockingConnectorAutomation.resetTransfers(pair.ship());
    }

    // Get the active transfer plan
    private TransferPlan activeTransferPlan(
            ShipDockRegistry.Dock dock, ScheduleEntry entry
    ) {
        // -----------------------------------------------------AUTO REFUEL-----------------------------------------------------
        if (autoRefuelActive && !autoRefuelReturning) {
            return new TransferPlan(
                    TransferDirection.PICKUP, TransferDirection.PICKUP,
                    TransferDirection.PICKUP, true, List.of(), List.of());
        }
        // ------------------------------------TRANSFER STATE------------------------------------
        TransferDirection itemDirection = TransferDirection.NONE;
        TransferDirection fluidDirection = TransferDirection.NONE;
        TransferDirection energyDirection = TransferDirection.NONE;
        boolean hasFluidCondition = false;
        boolean hasEnergyCondition = false;
        RefuelIfInstruction refuelIf = entry.instruction instanceof RefuelIfInstruction instruction
                ? instruction : null;
        List<ItemStack> itemFilters = new ArrayList<>();
        List<ItemStack> fluidFilters = new ArrayList<>();
        List<List<ScheduleWaitCondition>> columns = entry.conditions;
        // ------------------------------------SCHEDULE CONDITIONS------------------------------------
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            List<ScheduleWaitCondition> column = columns.get(columnIndex);
            int progress = conditionProgress.length == columns.size()
                    ? conditionProgress[columnIndex] : 0;
            if (column.isEmpty() || progress < 0 || progress >= column.size()) {
                continue;
            }
            ScheduleWaitCondition condition = column.get(progress);
            if (condition instanceof ItemThresholdCondition items) {
                long amount = items.getMeasure() == 1
                        ? ShipCargoAutomation.countFullItemStacks(
                                controller, items.getItem(0))
                        : ShipCargoAutomation.countItems(controller, items.getItem(0));
                TransferDirection dir = transferDirection(
                        items.getOperator(), amount, items.getThreshold());
                itemDirection = mergeDirection(itemDirection, dir);
                if (dir != TransferDirection.NONE) {
                    itemFilters.add(items.getItem(0).copy());
                }
            } else if (condition instanceof FluidThresholdCondition fluids) {
                hasFluidCondition = true;
                long amount = ShipCargoAutomation.countFluids(
                        controller, fluids.getItem(0)) / 1000L;
                TransferDirection dir = transferDirection(
                        fluids.getOperator(), amount, fluids.getThreshold());
                fluidDirection = mergeDirection(fluidDirection, dir);
                if (dir != TransferDirection.NONE) {
                    fluidFilters.add(fluids.getItem(0).copy());
                }
            } else if (isEnergyCargoCondition(condition)
                    && condition instanceof CargoThresholdCondition energy) {
                hasEnergyCondition = true;
                long amount = ShipCargoAutomation.resourceStatus(controller).energy();
                energyDirection = mergeDirection(energyDirection, transferDirection(
                        energy.getOperator(), amount,
                        EnergyCargoUnits.toFe(energy.getThreshold(), energy.getMeasure())));
            }
        }
        // -----------------------------------------------------DOCK DEFAULTS-----------------------------------------------------
        if (dock.refuel() && !hasFluidCondition && !hasEnergyCondition) {
            fluidDirection = TransferDirection.PICKUP;
        }
        energyDirection = dock.refuel()
                ? (hasEnergyCondition
                ? energyDirection
                : (hasFluidCondition ? fluidDirection : TransferDirection.PICKUP))
                : energyDirection;
        // -----------------------------------------------------FUEL FILTER-----------------------------------------------------
        if (refuelIf != null) {
            itemDirection = TransferDirection.NONE;
            fluidDirection = TransferDirection.NONE;
            energyDirection = TransferDirection.NONE;
            if (refuelIf.usesEnergy()) {
                energyDirection = TransferDirection.PICKUP;
            } else if (!refuelIf.hasFuelFilter()) {
                itemDirection = TransferDirection.PICKUP;
                fluidDirection = TransferDirection.PICKUP;
                energyDirection = TransferDirection.PICKUP;
            } else {
                ItemStack selectedFuel = refuelIf.getItem(0);
                if (!refuelIf.fuelFilter().fluid(controller.getLevel()).isEmpty()) {
                    fluidDirection = TransferDirection.PICKUP;
                    fluidFilters.add(selectedFuel);
                } else if (ShipCargoAutomation.isBurnableItem(selectedFuel)) {
                    itemDirection = TransferDirection.PICKUP;
                    itemFilters.add(selectedFuel);
                }
            }
        }
        boolean fuelRun = dock.refuel() && !hasFluidCondition && !hasEnergyCondition
                || refuelIf != null;
        if (fuelRun && itemDirection == TransferDirection.NONE && refuelIf == null) {
            itemDirection = TransferDirection.PICKUP;
        }
        return new TransferPlan(
                itemDirection, fluidDirection, energyDirection, fuelRun,
                List.copyOf(itemFilters), List.copyOf(fluidFilters));
    }

    // Check if the fuel refill is complete
    static boolean fuelRefillComplete(ShipCargoAutomation.FuelStatus fuel) {
        return fuel.infinite() || fuel.capacity() <= 0.0D
                || fuel.amount() >= fuel.capacity() * 0.999D;
    }

    // Check if this is energy cargo condition
    private static boolean isEnergyCargoCondition(ScheduleWaitCondition condition) {
        return condition instanceof CargoThresholdCondition
                && CREATE_ADDITION_ENERGY_THRESHOLD.equals(condition.getId());
    }

    // Get the transfer direction
    static TransferDirection transferDirection(
            CargoThresholdCondition.Ops operator, long amount, int threshold
    ) {
        return transferDirection(operator, amount, (long) threshold);
    }

    // Get the transfer direction
    static TransferDirection transferDirection(
            CargoThresholdCondition.Ops operator, long amount, long threshold
    ) {
        if (operator == null) {
            return TransferDirection.NONE;
        }
        return switch (operator) {
            case GREATER -> amount > threshold
                    ? TransferDirection.NONE : TransferDirection.PICKUP;
            case LESS -> amount < threshold
                    ? TransferDirection.NONE : TransferDirection.DROPOFF;
            case EQUAL -> amount < threshold
                    ? TransferDirection.PICKUP
                    : amount > threshold
                    ? TransferDirection.DROPOFF : TransferDirection.NONE;
        };
    }

    // Merge the direction
    private static TransferDirection mergeDirection(
            TransferDirection current, TransferDirection candidate
    ) {
        if (current == TransferDirection.CONFLICT) {
            return current;
        }
        if (candidate == TransferDirection.NONE) {
            return current;
        }
        if (current == TransferDirection.NONE || current == candidate) {
            return candidate;
        }
        return TransferDirection.CONFLICT;
    }

    // Check if the conditions are complete
    private boolean conditionsComplete(List<List<ScheduleWaitCondition>> columns) {
        if (controller.getLevel() == null || currentDockId == null) {
            return false;
        }
        if (columns.isEmpty()) {
            return true;
        }
        if (conditionProgress.length != columns.size()) {
            initConditions();
        }
        long now = controller.getLevel().getGameTime();
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            List<ScheduleWaitCondition> column = columns.get(columnIndex);
            if (column.isEmpty() || conditionProgress[columnIndex] >= column.size()) {
                return true;
            }
            ScheduleWaitCondition condition = column.get(conditionProgress[columnIndex]);
            if (conditionComplete(condition, conditionStarted[columnIndex], now)) {
                conditionProgress[columnIndex]++;
                conditionStarted[columnIndex] = now;
                if (conditionProgress[columnIndex] >= column.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    // Check if the condition is complete
    private boolean conditionComplete(ScheduleWaitCondition condition, long started, long now) {
        if (controller.getLevel() == null || currentDockId == null) {
            return false;
        }
        ShipDockRegistry.Dock dock = cachedDock(currentDockId);
        if (condition instanceof TimedWaitCondition timed) {
            long reference = condition instanceof IdleCargoCondition ? lastCargoExchangeTick : started;
            long elapsed = now - reference;
            return condition instanceof IdleCargoCondition
                    ? elapsed > timed.totalWaitTicks()
                    : elapsed >= timed.totalWaitTicks();
        }
        if (condition instanceof TimeOfDayCondition time) {
            CompoundTag data = condition.write(controller.getLevel().registryAccess()).getCompound("Data");
            int targetHour = data.getInt("Hour");
            int targetMinute = data.getInt("Minute");
            int targetTicks = (int) (((targetHour + 18) % 24 * 1000
                    + Math.ceil(targetMinute / 60.0F * 1000.0F)) % time.getRotation());
            int difference = (int) (controller.getLevel().getDayTime() % time.getRotation()) - targetTicks;
            return difference >= 0 && difference <= 40;
        }
        if (condition instanceof ItemThresholdCondition items) {
            long amount = items.getMeasure() == 1
                    ? ShipCargoAutomation.countFullItemStacks(controller, items.getItem(0))
                    : ShipCargoAutomation.countItems(controller, items.getItem(0));
            return items.getOperator().test((int) Math.min(Integer.MAX_VALUE, amount), items.getThreshold());
        }
        if (condition instanceof FluidThresholdCondition fluids) {
            long amount = ShipCargoAutomation.countFluids(controller, fluids.getItem(0)) / 1000L;
            return fluids.getOperator().test((int) Math.min(Integer.MAX_VALUE, amount), fluids.getThreshold());
        }
        if (isEnergyCargoCondition(condition)
                && condition instanceof CargoThresholdCondition energy) {
            long amount = ShipCargoAutomation.resourceStatus(controller).energy();
            return EnergyCargoUnits.test(energy.getOperator(), amount,
                    EnergyCargoUnits.toFe(energy.getThreshold(), energy.getMeasure()));
        }
        if (condition instanceof RedstoneLinkCondition link) {
            return Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(link.freq) != link.lowActivation();
        }
        if (condition instanceof PlayerPassengerCondition passengers) {
            int count = countShipPlayers();
            return passengers.canOvershoot() ? count >= passengers.getTarget() : count == passengers.getTarget();
        }
        if (condition instanceof StationUnloadedCondition) {
            ServerLevel level = dock == null ? null : dockLevel(dock);
            return level != null && !level.isPositionEntityTicking(dock.pos());
        }
        if (condition instanceof StationPoweredCondition) {
            ServerLevel level = dock == null ? null : dockLevel(dock);
            return level != null && level.isLoaded(dock.pos()) && level.hasNeighborSignal(dock.pos());
        }
        return false;
    }

    // Count the ship players
    private int countShipPlayers() {
        if (controller.getLevel() == null || controller.getLevel().getServer() == null) {
            return 0;
        }
        Object controllerSubLevel = com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                .getContainingSubLevel(controller);
        int count = 0;
        for (net.minecraft.server.level.ServerPlayer player : controller.getLevel().getServer().getPlayerList().getPlayers()) {
            if (player.isPassenger() && com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper
                    .getEntityTrackingSubLevel(player) == controllerSubLevel) {
                count++;
            }
        }
        return count;
    }

    // Get the dock level
    private @Nullable ServerLevel dockLevel(ShipDockRegistry.Dock dock) {
        if (controller.getLevel() == null || controller.getLevel().getServer() == null) {
            return null;
        }
        return controller.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dock.dimension()));
    }

    // Get the current position
    private Vec3 currentPosition() {
        AdvancedGraphDocument.Value x = controller.getShipControlGraphValue("x");
        AdvancedGraphDocument.Value y = controller.getShipControlGraphValue("y");
        AdvancedGraphDocument.Value z = controller.getShipControlGraphValue("z");
        return new Vec3(x.asNumber(), y.asNumber(), z.asNumber());
    }

    // Get the connector pair
    private BlockEntityPair connectorPair(ShipDockRegistry.Dock dock) {
        if (controller.getLevel() == null) {
            return BlockEntityPair.EMPTY;
        }
        ShipControlModuleRuntime.MappedDockingConnector mapped =
                controller.getShipDockingConnector(currentShipConnectorIndex);
        net.minecraft.world.level.block.entity.BlockEntity ship = mapped == null ? null
                : DockingConnectorAutomation.resolve(
                        controller.getLevel(), mapped.subLevelId(), mapped.blockPosition());
        net.minecraft.world.level.block.entity.BlockEntity station =
                DockingConnectorAutomation.resolve(controller.getLevel(),
                        dock.connectorSubLevelId(), dock.connectorPos());
        return new BlockEntityPair(ship, station);
    }

    // Check if this has usable connector pair
    private boolean hasUsableConnectorPair(ShipDockRegistry.Dock dock) {
        return hasUsableConnectorPair(dock,
                controller.getShipDockingConnector(currentShipConnectorIndex));
    }

    // Check a selected ship connector without changing the live docking state.
    private boolean hasUsableConnectorPair(
            ShipDockRegistry.Dock dock,
            @Nullable ShipControlModuleRuntime.MappedDockingConnector shipConnector
    ) {
        if (shipConnector == null || dock == null || !dock.hasDockingConnector()
                || controller.getLevel() == null) return false;
        BlockEntity ship = DockingConnectorAutomation.resolve(
                controller.getLevel(), shipConnector.subLevelId(), shipConnector.blockPosition());
        BlockEntity station = DockingConnectorAutomation.resolve(controller.getLevel(),
                dock.connectorSubLevelId(), dock.connectorPos());
        return ship != null && station != null;
    }

    // Check whether this vehicle and dock support the physical connector handoff flow.
    private boolean usesPhysicalDocking(ShipDockRegistry.Dock dock) {
        return dock != null && dock.hasDockingConnector()
                && !controller.getShipDockingConnectors().isEmpty();
    }

    // Keep persistent route geometry near the dock block and independent from connector assignment.
    private SablePathfinder.RouteTerminal dockRouteTerminal(ShipDockRegistry.Dock dock) {
        ShipDockScheduler.VesselEnvelope envelope = vesselEnvelope();
        double standOff = Math.max(ROUTE_TERMINAL_MIN_STANDOFF,
                envelope.horizontalRadius() + ROUTE_TERMINAL_CLEARANCE);
        double verticalOffset = Math.max(1.5D, envelope.bottomOffset() + 1.0D);
        double captureRadius = Math.max(ROUTE_TERMINAL_MIN_CAPTURE_RADIUS,
                Math.min(ROUTE_TERMINAL_MAX_CAPTURE_RADIUS,
                        standOff * 0.5D));
        return SablePathfinder.routeTerminal(
                dock.worldPosition(), dock.facing(), standOff,
                verticalOffset, captureRadius);
    }

    // Resolve the route handoff without letting provisioning replace planned transit.
    private SablePathfinder.RouteTerminal scheduleRouteTerminal(ShipDockRegistry.Dock dock) {
        if (usesPhysicalDocking(dock)) return dockRouteTerminal(dock);
        return new SablePathfinder.RouteTerminal(
                navigationTarget(dock, false), connectorlessArrivalTolerance());
    }

    // Get the complete-hull connectorless arrival distance plus the authored padding.
    private double connectorlessArrivalTolerance() {
        return connectorlessArrivalTolerance(controller.getShipEnvelope());
    }

    // Resolve connectorless arrival tolerance without constructing collision geometry.
    static double connectorlessArrivalTolerance(@Nullable SableAssemblyBoundsApi.Envelope envelope) {
        SableAssemblyBoundsApi.Envelope safe = envelope == null
                ? SableAssemblyBoundsApi.Envelope.DEFAULT : envelope;
        return safe.targetOverlapTolerance(CONNECTORLESS_ARRIVAL_PADDING);
    }

    // Get the navigation target
    private Vec3 navigationTarget(ShipDockRegistry.Dock dock) {
        return navigationTarget(dock, hasUsableConnectorPair(dock));
    }


    // Get the navigation target
    static Vec3 navigationTarget(ShipDockRegistry.Dock dock, boolean useDockingConnector) {
        if (useDockingConnector) {
            return dock.approach();
        }
        return dock.worldPosition().add(dock.facing().scale(4.0D)).add(0.0D, 1.5D, 0.0D);
    }

    // Check if the connectorless hover target was reached
    static boolean connectorlessHoverTargetReached(
            Vec3 currentPosition,
            Vec3 targetPosition,
            double tolerance
    ) {
        if (currentPosition == null || targetPosition == null) {
            return false;
        }
        double distanceSquared = currentPosition.distanceToSqr(targetPosition);
        double allowed = Math.max(0.0D, tolerance);
        return Double.isFinite(distanceSquared) && distanceSquared <= allowed * allowed;
    }

    // Disengage the attached connectors
    private void disengageAttachedConnectors() {
        if (controller.getLevel() != null && attachedDockId != null) {
            ShipDockRegistry.Dock dock = cachedDock(attachedDockId);
            if (dock != null) {
                BlockEntityPair pair = connectorPair(dock);
                DockingConnectorAutomation.disengage(pair.ship(), pair.dock());
            }
        }
        attachedDockId = null;
        controller.activateShipDockingConnector(-1);
    }

    // Cancel the docking attempt
    private void cancelDockingAttempt(@Nullable ShipDockRegistry.Dock dock) {
        if (dock != null) {
            BlockEntityPair pair = connectorPair(dock);
            DockingConnectorAutomation.disengage(pair.ship(), pair.dock());
        }
        magneticCaptureActive = false;
        controller.setShipDockingMagneticCapture(currentShipConnectorIndex, false);
        controller.activateShipDockingConnector(-1);
    }

    // Suspend the connector transfers
    private void suspendConnectorTransfers() {
        UUID dockId = currentDockId != null ? currentDockId : attachedDockId;
        if (dockId == null) {
            return;
        }
        ShipDockRegistry.Dock dock = cachedDock(dockId);
        if (dock == null) {
            return;
        }
        BlockEntityPair pair = connectorPair(dock);
        DockingConnectorAutomation.configureTransfers(
                pair.dock(), false, false, "", false, false);
        DockingConnectorAutomation.resetTransfers(pair.ship());
    }

    // Get the speed for throttle
    static double speedForThrottle(double throttle) {
        double normalized = Math.max(0.0D, Math.min(1.0D, throttle));
        return Math.max(MIN_CRUISE_SPEED, normalized * MAX_CRUISE_SPEED);
    }

    // Check if this needs undocking stabilization
    static boolean needsUndockingStabilization(@Nullable UUID dockId) {
        return dockId != null;
    }

    // Check if the interval elapsed
    static boolean intervalElapsed(long now, long prev, long interval) {
        return prev == Long.MIN_VALUE || now < prev || now - prev >= interval;
    }

    // Advance the entry
    private void advanceEntry() {
        if (schedule == null) {
            return;
        }
        boolean preserveConnectorlessHover = connectorlessHoverTarget != null;
        suspendConnectorTransfers();
        if (attachedDockId == null) {
            releaseAllReservations();
        }
        couplingEndpoints = List.of();
        connectorChoiceCache.keySet().removeIf(choice -> choice.entryIndex() == currentEntry);
        currentEntry++;
        currentDockId = null;
        pendingDockCandidates = List.of();
        failedDockingSlots.clear();
        queueAfterUndocking = false;
        routeTerminalTransitPending = false;
        restoredDockOwnership = null;
        conditionProgress = new int[0];
        conditionStarted = new long[0];
        if (currentEntry >= schedule.entries.size()) {
            if (schedule.cyclic) {
                currentEntry = firstOperationalEntry();
                phase = Phase.PRE_TRANSIT;
                status = "Repeating shipping schedule";
            } else {
                currentEntry = schedule.entries.size() - 1;
                phase = Phase.DONE;
                status = preserveConnectorlessHover
                        ? "Shipping schedule complete; hovering at " + lastStopName
                        : "Shipping schedule complete";
                if (!preserveConnectorlessHover) {
                    controller.executeShipControlGraphCommand(
                            "shipping_schedule_stop", "ship_stop", Map.of());
                }
            }
        } else {
            phase = Phase.PRE_TRANSIT;
        }
        schedule.savedProgress = Math.max(0, currentEntry);
        controller.setChanged();
    }

    // Store the dock connector choice
    private record DockConnectorChoice(@Nullable UUID dockId, int entryIndex) {
    }

    // Store one landing zone scheduler target
    private record LandingZoneLeaseTarget(
            ShipDockRegistry.Dock dock,
            ShipDockRegistry.LandingZoneTarget zone,
            ShipDockScheduler.DockSlot slot
    ) {
    }

    // Store one landing zone scheduler request
    private record LandingZoneRequest(
            ShipDockScheduler.Lease lease,
            List<LandingZoneLeaseTarget> targets
    ) {
    }

    // Write the shipping schedule
    public void write(CompoundTag parent, HolderLookup.Provider provider) {
        if (schedule == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        schedule.savedProgress = Math.max(0, currentEntry);
        tag.put("Schedule", schedule.write(provider));
        tag.put("AutoRefuel", autoRefuel.toTag());
        tag.putBoolean("AutoRefuelActive", autoRefuelActive);
        tag.putBoolean("AutoRefuelReturning", autoRefuelReturning);
        tag.putInt("AutoRefuelResumeEntry", autoRefuelResumeEntry);
        tag.putString("AutoRefuelResumePhase", autoRefuelResumePhase.name());
        if (autoRefuelResumeDockId != null) {
            tag.putUUID("AutoRefuelResumeDock", autoRefuelResumeDockId);
        }
        tag.putIntArray("AutoRefuelConditionProgress", autoRefuelResumeConditionProgress);
        tag.putLongArray("AutoRefuelConditionStarted", autoRefuelResumeConditionStarted);
        tag.putLong("AutoRefuelLastCargoExchange", autoRefuelResumeLastCargoExchange);
        tag.putLong("AutoRefuelStartedTick", autoRefuelStartedTick);
        if (pilotId != null) {
            tag.putUUID("Pilot", pilotId);
        }
        tag.putBoolean("BlazeBurnerPilot", blazeBurnerPilot);
        if (currentDockId != null) {
            tag.putUUID("CurrentDock", currentDockId);
        }
        if (attachedDockId != null) {
            tag.putUUID("AttachedDock", attachedDockId);
        }
        if (divertedTargetId != null) {
            tag.putUUID("DivertedTarget", divertedTargetId);
        }
        tag.putString("Phase", phase.name());
        tag.putString("PhaseBeforePause", phaseBeforePause.name());
        tag.putInt("CurrentEntry", currentEntry);
        if (!couplingEndpoints.isEmpty()) {
            tag.put("CouplingEndpoints",
                    ShipCouplerService.writeEndpointKeys(couplingEndpoints));
        }
        tag.putLong("PhaseStartedTick", phaseStartedTick);
        tag.putLong("LastTargetRefreshTick", lastTargetRefreshTick);
        tag.putIntArray("ConditionProgress", conditionProgress);
        tag.putLongArray("ConditionStarted", conditionStarted);
        tag.putBoolean("QueueAfterUndocking", queueAfterUndocking);
        tag.putBoolean("RouteTerminalTransitPending", routeTerminalTransitPending);
        tag.putBoolean("ShutdownSnapshot", shutdownSnapshotPending);
        tag.putDouble("RouteSpeed", routeSpeed);
        tag.putDouble("RouteThrottle", routeThrottle);
        tag.putInt("ShipConnector", currentShipConnectorIndex);
        if (connectorlessHoverTarget != null) {
            tag.putDouble("HoverTargetX", connectorlessHoverTarget.x);
            tag.putDouble("HoverTargetY", connectorlessHoverTarget.y);
            tag.putDouble("HoverTargetZ", connectorlessHoverTarget.z);
        }
        if (currentParkingZoneId != null) {
            tag.putUUID("ParkingZone", currentParkingZoneId);
        }
        if (parkingTarget != null) {
            tag.putDouble("ParkingTargetX", parkingTarget.x);
            tag.putDouble("ParkingTargetY", parkingTarget.y);
            tag.putDouble("ParkingTargetZ", parkingTarget.z);
        }
        if (parkingKind != null) {
            tag.putString("ParkingKind", parkingKind.name());
        }
        tag.putString("Title", title);
        tag.putString("Status", status);
        tag.putString("LastStop", lastStopName);
        tag.putString("LastGraphCommand", lastGraphCommand);
        tag.putLong("LastCargoExchange", lastCargoExchangeTick);
        parent.put("ShippingScheduleRuntime", tag);
    }

    // Read the shipping schedule
    public void read(CompoundTag parent, HolderLookup.Provider provider) {
        read(parent, provider, true);
    }

    // Read the shipping schedule runtime. Persistent server loads recover
    // interrupted physical actions; client synchronization packets must retain
    // the exact authoritative phase and status sent by the server.
    public void read(CompoundTag parent, HolderLookup.Provider provider,
                     boolean recoverAfterLoad) {
        // -----------------------------------------------------DEFAULT STATE-----------------------------------------------------
        if (!parent.contains("ShippingScheduleRuntime", Tag.TAG_COMPOUND)) {
            schedule = null;
            blazeBurnerPilot = false;
            autoRefuel = ShippingAutoRefuelSettings.DEFAULT;
            clearAutoRefuelInterruption();
            phase = Phase.IDLE;
            couplingEndpoints = List.of();
            connectorlessHoverTarget = null;
            clearParkingState();
            clearHoldingState();
            lastGraphCommand = "";
            runtimeValidated = false;
            lastRuntimeValidationTick = Long.MIN_VALUE;
            lastDockHeartbeatTick = Long.MIN_VALUE;
            resetRouteProgress();
            shutdownSnapshotPending = false;
            invalidPilotSinceTick = Long.MIN_VALUE;
            resumeLifecyclePausePending = false;
            dockingRecoveryControlActive = false;
            invalidateRouteCache();
            return;
        }
        // -----------------------------------------------------SAVED STATE-----------------------------------------------------
        CompoundTag tag = parent.getCompound("ShippingScheduleRuntime");
        schedule = Schedule.fromTag(provider, tag.getCompound("Schedule"));
        autoRefuel = ShippingAutoRefuelSettings.fromSchedule(schedule);
        autoRefuelActive = tag.getBoolean("AutoRefuelActive");
        autoRefuelReturning = tag.getBoolean("AutoRefuelReturning");
        autoRefuelResumeEntry = tag.contains("AutoRefuelResumeEntry")
                ? tag.getInt("AutoRefuelResumeEntry") : -1;
        try {
            autoRefuelResumePhase = Phase.valueOf(tag.getString("AutoRefuelResumePhase"));
        } catch (IllegalArgumentException err) {
            autoRefuelResumePhase = Phase.PRE_TRANSIT;
        }
        autoRefuelResumeDockId = tag.hasUUID("AutoRefuelResumeDock")
                ? tag.getUUID("AutoRefuelResumeDock") : null;
        autoRefuelResumeConditionProgress = tag.getIntArray("AutoRefuelConditionProgress");
        autoRefuelResumeConditionStarted = tag.getLongArray("AutoRefuelConditionStarted");
        autoRefuelResumeLastCargoExchange = tag.getLong("AutoRefuelLastCargoExchange");
        autoRefuelStartedTick = tag.getLong("AutoRefuelStartedTick");
        // -----------------------------------------------------ROUTE STATE-----------------------------------------------------
        pilotId = tag.hasUUID("Pilot") ? tag.getUUID("Pilot") : null;
        blazeBurnerPilot = tag.getBoolean("BlazeBurnerPilot");
        currentDockId = tag.hasUUID("CurrentDock") ? tag.getUUID("CurrentDock") : null;
        attachedDockId = tag.hasUUID("AttachedDock") ? tag.getUUID("AttachedDock") : null;
        divertedTargetId = tag.hasUUID("DivertedTarget") ? tag.getUUID("DivertedTarget") : null;
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException err) {
            phase = Phase.PRE_TRANSIT;
        }
        try {
            phaseBeforePause = Phase.valueOf(tag.getString("PhaseBeforePause"));
        } catch (IllegalArgumentException err) {
            phaseBeforePause = Phase.PRE_TRANSIT;
        }
        currentEntry = tag.getInt("CurrentEntry");
        couplingEndpoints = ShipCouplerService.readEndpointKeys(
                tag.getList("CouplingEndpoints", Tag.TAG_COMPOUND));
        phaseStartedTick = tag.getLong("PhaseStartedTick");
        lastTargetRefreshTick = tag.getLong("LastTargetRefreshTick");
        routeThrottle = tag.contains("RouteThrottle")
                ? Math.max(0.05D, Math.min(1.0D, tag.getDouble("RouteThrottle"))) : 0.6D;
        routeSpeed = speedForThrottle(routeThrottle);
        currentShipConnectorIndex = tag.contains("ShipConnector")
                ? tag.getInt("ShipConnector") : -1;
        connectorlessHoverTarget = tag.contains("HoverTargetX", Tag.TAG_DOUBLE)
                && tag.contains("HoverTargetY", Tag.TAG_DOUBLE)
                && tag.contains("HoverTargetZ", Tag.TAG_DOUBLE)
                ? new Vec3(tag.getDouble("HoverTargetX"),
                tag.getDouble("HoverTargetY"), tag.getDouble("HoverTargetZ"))
                : null;
        currentParkingZoneId = tag.hasUUID("ParkingZone")
                ? tag.getUUID("ParkingZone") : null;
        parkingTarget = tag.contains("ParkingTargetX", Tag.TAG_DOUBLE)
                && tag.contains("ParkingTargetY", Tag.TAG_DOUBLE)
                && tag.contains("ParkingTargetZ", Tag.TAG_DOUBLE)
                ? new Vec3(tag.getDouble("ParkingTargetX"),
                tag.getDouble("ParkingTargetY"), tag.getDouble("ParkingTargetZ"))
                : null;
        try {
            parkingKind = tag.contains("ParkingKind", Tag.TAG_STRING)
                    ? ParkingKind.valueOf(tag.getString("ParkingKind")) : null;
        } catch (IllegalArgumentException err) {
            parkingKind = null;
        }
        title = tag.getString("Title");
        status = tag.getString("Status");
        lastStopName = tag.getString("LastStop");
        lastGraphCommand = tag.getString("LastGraphCommand");
        lastCargoExchangeTick = tag.getLong("LastCargoExchange");
        // -----------------------------------------------------RUNTIME RESET-----------------------------------------------------
        lastDockTelemetryTick = Long.MIN_VALUE;
        lastRuntimeValidationTick = Long.MIN_VALUE;
        lastDockHeartbeatTick = Long.MIN_VALUE;
        lastTravelMetricsTick = Long.MIN_VALUE;
        lastMetricsFuelTick = Long.MIN_VALUE;
        resetRouteProgress();
        cachedTravelMetrics = null;
        conditionProgress = tag.getIntArray("ConditionProgress");
        conditionStarted = tag.getLongArray("ConditionStarted");
        pendingDockCandidates = List.of();
        clearHoldingState();
        queueAfterUndocking = tag.getBoolean("QueueAfterUndocking");
        routeTerminalTransitPending = tag.getBoolean("RouteTerminalTransitPending");
        restoredDockOwnership = phase == Phase.DOCKED || phase == Phase.WAITING
                ? currentDockId : null;
        invalidateRouteCache();
        shutdownSnapshotPending = false;
        runtimeValidated = false;
        invalidPilotSinceTick = Long.MIN_VALUE;
        resumeLifecyclePausePending = phase == Phase.PAUSED
                && LOAD_INDUCED_SCM_PAUSE.equals(status);
        dockingRecoveryControlActive = recoverAfterLoad && phase != Phase.IDLE;
        // -----------------------------------------------------RESUME STATE-----------------------------------------------------
        if (recoverAfterLoad) {
            if (phase == Phase.COUPLING && currentCouplingInstruction() == null) {
                couplingEndpoints = List.of();
                phase = Phase.PRE_TRANSIT;
                status = "Carriage coupling action will restart from the saved schedule entry";
            }
            if (phaseBeforePause == Phase.COUPLING && currentCouplingInstruction() == null) {
                couplingEndpoints = List.of();
                phaseBeforePause = Phase.PRE_TRANSIT;
            }
            if (phase != Phase.COUPLING && phaseBeforePause != Phase.COUPLING) {
                couplingEndpoints = List.of();
            }
            if (phase == Phase.TRANSITING || phase == Phase.NAVIGATING || phase == Phase.PARKING
                    || phase == Phase.WAITING_FOR_PARK) {
                phase = Phase.PRE_TRANSIT;
                routeTerminalTransitPending = false;
                status = "Resuming shipping schedule navigation after load";
            }
            if (phase == Phase.DOCKING || phase == Phase.UNDOCKING
                    || phase == Phase.WAITING_FOR_DOCK) {
                controller.activateShipDockingConnector(-1);
                attachedDockId = null;
                restoredDockOwnership = null;
                phase = Phase.PRE_TRANSIT;
                status = "Resuming shipping schedule after dock state restored";
            }
            if ((phase == Phase.PARKED || phase == Phase.DONE)
                    && (currentParkInstruction() == null || currentDockId == null
                    || currentParkingZoneId == null || parkingTarget == null)) {
                clearParkingState();
                phase = Phase.PRE_TRANSIT;
                status = "Resuming shipping schedule after parking state restored";
            }
        }
    }

    // Store the travel metrics
    private record TravelMetrics(
            boolean active,
            boolean pilotPresent,
            boolean docked,
            boolean waiting,
            boolean diverted,
            boolean needsRefuel,
            boolean targetHasConnector,
            String shipName,
            String scheduleTitle,
            String status,
            String phase,
            String currentStop,
            String targetStop,
            String nextStop,
            int currentEntry,
            int nextEntry,
            int stopCount,
            int remainingStops,
            double progressPercent,
            long etaSeconds,
            double distanceToTarget,
            double throttle,
            double cruiseSpeed,
            double phaseElapsedSeconds,
            Vec3 position,
            Vec3 targetPosition,
            double fuelAmount,
            double fuelCapacity,
            double fuelRatio,
            double fuelUsePerTick,
            double fuelReserveSeconds,
            boolean cyclic
    ) {
    }

    // Define the transfer direction values
    enum TransferDirection {
        NONE,
        PICKUP,
        DROPOFF,
        CONFLICT
    }

    // Store the transfer plan
    private record TransferPlan(
            TransferDirection items,
            TransferDirection fluids,
            TransferDirection energy,
            boolean fuelRun,
            List<ItemStack> itemFilters,
            List<ItemStack> fluidFilters
    ) {
    }

    // Store pilot state
    private enum PilotState {
        VALID,
        UNAVAILABLE,
        INVALID
    }

    // Define the phase values
    private enum Phase {
        IDLE,
        INITIALIZING,
        PRE_TRANSIT,
        COUPLING,
        UNDOCKING,
        WAITING_FOR_DOCK,
        WAITING_FOR_PARK,
        TRANSITING,
        NAVIGATING,
        PARKING,
        DOCKING,
        DOCKED,
        PARKED,
        WAITING,
        PAUSED,
        DONE
    }

    // Define the parking kind values
    private enum ParkingKind {
        GROUND,
        AIRBORNE
    }

    // Store the block entity pair
    private record BlockEntityPair(
            @Nullable net.minecraft.world.level.block.entity.BlockEntity ship,
            @Nullable net.minecraft.world.level.block.entity.BlockEntity dock
    ) {
        private static final BlockEntityPair EMPTY = new BlockEntityPair(null, null);
    }
}
