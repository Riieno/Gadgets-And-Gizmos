package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerDispatcher;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerPathing;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProfile;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerStatusSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerTask;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerTaskQueue;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerWorkOrder;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Assign multiple mannequin workers and execute their persistent logistics queues
public class WorkerPodBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int DISCOVERY_INTERVAL = 40;
    private static final int PATH_BUDGET = 96;
    private static final double MOVEMENT_SPEED = 0.18D;
    private static final double MAXIMUM_PATH_RANGE = 512.0D;
    private static final double ASSIGNMENT_RANGE = 16.0D;
    private static final int IDLE_WANDER_PAUSE_TICKS = 40;
    private static final String WORKER_CARGO_TOKEN_TAG = "WorkerCargoToken";
    private static final String WORKER_FLUID_CARGO_TAG = "WorkerFluidCargo";
    private static final Map<UUID, WeakReference<WorkerPodBlockEntity>> ACTIVE_PODS =
            new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private UUID podId = UUID.randomUUID();
    private final Map<UUID, WorkerRuntime> workers = new LinkedHashMap<>();
    private List<WorkerEndpointSnapshot> endpointSnapshots = List.of();
    private @Nullable UUID controllerSubLevelId;
    private @Nullable BlockPos controllerPos;
    private int discoveryTicks;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the worker pod block entity
    public WorkerPodBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.WORKER_POD.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tick every mannequin assigned to this base station
    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel)) return;
        ACTIVE_PODS.put(podId, new WeakReference<>(this));
        AdvancedContraptionControllerBlockEntity controller = owningController();
        if (controller != null) enrollPodOccupants(controller);
        boolean refreshEndpoints = ++discoveryTicks >= DISCOVERY_INTERVAL;
        if (refreshEndpoints) {
            discoveryTicks = 0;
            refreshLinkedEndpoints(controller);
        }
        for (WorkerRuntime runtime : List.copyOf(workers.values())) {
            tickWorker(serverLevel, controller, runtime, refreshEndpoints);
        }
    }

    // Remove a destroyed mannequin from its base station after its real cargo drops
    public static void workerDestroyed(UUID podId, UUID workerId) {
        if (podId == null || workerId == null) return;
        WeakReference<WorkerPodBlockEntity> reference = ACTIVE_PODS.get(podId);
        WorkerPodBlockEntity pod = reference == null ? null : reference.get();
        if (pod == null) {
            ACTIVE_PODS.remove(podId);
            return;
        }
        if (pod.workers.remove(workerId) == null) return;
        AdvancedContraptionControllerBlockEntity controller = pod.owningController();
        if (controller != null) controller.releaseWorker(pod.podId, workerId);
        pod.setChanged();
        pod.sendData();
    }

    // Stop advertising a removed base station through the live lookup
    @Override
    public void remove() {
        WeakReference<WorkerPodBlockEntity> reference = ACTIVE_PODS.get(podId);
        if (reference != null && reference.get() == this) ACTIVE_PODS.remove(podId);
        super.remove();
    }

    // Find every adjacent or SCM-linked Worker Pod for one ACC
    public static List<WorkerPodBlockEntity> linkedPods(AdvancedContraptionControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null) return List.of();
        Map<UUID, WorkerPodBlockEntity> pods = new LinkedHashMap<>();
        for (Direction direction : Direction.values()) {
            if (controller.getLevel().getBlockEntity(controller.getBlockPos().relative(direction))
                    instanceof WorkerPodBlockEntity pod) pods.putIfAbsent(pod.podId, pod);
        }
        for (ContraptionNetworkLinkerData.LinkedTarget target
                : ContraptionNetworkLinkerData.readTargets(controller.getStoredLinker())) {
            if (target.mode() != ContraptionNetworkLinkerData.LinkMode.SCM) continue;
            WorkerPodBlockEntity pod = SimulatedHelper.findBlockEntity(controller.getLevel(),
                    target.subLevelId(), target.blockPos(), WorkerPodBlockEntity.class);
            if (pod != null) pods.putIfAbsent(pod.podId, pod);
        }
        return List.copyOf(pods.values());
    }

    // Bind this base station to the ACC currently managing it
    public void bindController(AdvancedContraptionControllerBlockEntity controller) {
        if (controller == null) return;
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(controller);
        BlockPos pos = controller.getBlockPos().immutable();
        boolean changed = !Objects.equals(controllerSubLevelId, subLevelId) || !pos.equals(controllerPos);
        if (changed) {
            controllerSubLevelId = subLevelId;
            controllerPos = pos;
        }
        for (UUID workerId : workers.keySet()) {
            if (!controller.ownsWorker(workerId, podId)) controller.claimWorker(podId, workerId);
        }
        if (!changed) return;
        setChanged();
        sendData();
    }

    // Get this base station's persistent identity
    public UUID podId() {
        return podId;
    }

    // Get linked endpoint summaries for selectors
    public List<WorkerEndpointSnapshot> endpointSnapshots() {
        return endpointSnapshots;
    }

    // Get every assigned worker status and planned task queue
    public List<WorkerStatusSnapshot> workerSnapshots() {
        List<WorkerStatusSnapshot> snapshots = new ArrayList<>(workers.size());
        for (WorkerRuntime runtime : workers.values()) snapshots.add(snapshot(runtime));
        return List.copyOf(snapshots);
    }

    // Get mannequin ids currently assigned to this base station
    public Set<UUID> assignedWorkerIds() {
        return Set.copyOf(workers.keySet());
    }

    // Find nearby mannequins which can be assigned from the Worker Graph
    public List<PlayerMannequinEntity> assignableMannequins() {
        if (level == null) return List.of();
        AABB bounds = new AABB(worldPosition).inflate(ASSIGNMENT_RANGE);
        return level.getEntitiesOfClass(PlayerMannequinEntity.class, bounds, mannequin -> mannequin.isAlive()
                && (mannequin.assignedWorkerPod().isEmpty()
                || mannequin.assignedWorkerPod().filter(podId::equals).isPresent()));
    }

    // Replace the base station roster with validated nearby mannequins
    public void assignWorkers(Collection<UUID> workerIds) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        AdvancedContraptionControllerBlockEntity controller = owningController();
        Set<UUID> requested = workerIds == null ? Set.of() : new LinkedHashSet<>(workerIds);
        Map<UUID, PlayerMannequinEntity> candidates = new LinkedHashMap<>();
        for (PlayerMannequinEntity mannequin : assignableMannequins()) candidates.put(mannequin.getUUID(), mannequin);
        for (UUID existing : List.copyOf(workers.keySet())) {
            WorkerRuntime runtime = workers.get(existing);
            if (requested.contains(existing) || runtime != null && runtime.hasCargo()) continue;
            Entity entity = serverLevel.getEntity(existing);
            if (entity instanceof PlayerMannequinEntity mannequin
                    && mannequin.assignedWorkerPod().filter(podId::equals).isPresent()) {
                mannequin.setAssignedWorkerPod(null);
                mannequin.clearWorkerPresentation();
            }
            if (controller != null) controller.releaseWorker(podId, existing);
            workers.remove(existing);
        }
        for (UUID requestedId : requested) {
            PlayerMannequinEntity mannequin = candidates.get(requestedId);
            if (mannequin == null) continue;
            mannequin.setAssignedWorkerPod(podId);
            workers.computeIfAbsent(requestedId, ignored -> WorkerRuntime.create(mannequin));
            if (controller != null) controller.claimWorker(podId, requestedId);
        }
        setChanged();
        sendData();
    }

    // Get compatible assigned workers for graph dispatch
    public List<UUID> compatibleWorkers(WorkerResourceType type, Collection<UUID> selected) {
        Set<UUID> filter = selected == null ? Set.of() : Set.copyOf(selected);
        return workers.values().stream()
                .filter(runtime -> filter.isEmpty() || filter.contains(runtime.profile.workerId()))
                .filter(runtime -> runtime.profile.job().accepts(type))
                .map(runtime -> runtime.profile.workerId()).toList();
    }

    // Automatically enrol mannequins deliberately placed on this pod for its linked controller.
    private void enrollPodOccupants(AdvancedContraptionControllerBlockEntity controller) {
        if (level == null || controller == null) return;
        AABB podTop = new AABB(worldPosition.getX(), worldPosition.getY() + 0.85D, worldPosition.getZ(),
                worldPosition.getX() + 1.0D, worldPosition.getY() + 1.6D, worldPosition.getZ() + 1.0D);
        boolean changed = false;
        for (PlayerMannequinEntity mannequin : level.getEntitiesOfClass(PlayerMannequinEntity.class, podTop,
                candidate -> candidate.isAlive() && (candidate.assignedWorkerPod().isEmpty()
                        || candidate.assignedWorkerPod().filter(podId::equals).isPresent()))) {
            UUID workerId = mannequin.getUUID();
            mannequin.setAssignedWorkerPod(podId);
            if (!workers.containsKey(workerId)) {
                workers.put(workerId, WorkerRuntime.create(mannequin));
                changed = true;
            }
            if (!controller.ownsWorker(workerId, podId)) controller.claimWorker(podId, workerId);
        }
        if (changed) {
            setChanged();
            sendData();
        }
    }

    // Queue one graph order for a specific assigned worker
    public boolean submit(UUID workerId, WorkerWorkOrder order) {
        WorkerRuntime runtime = workers.get(workerId);
        if (runtime == null || order == null
                || !runtime.profile.job().accepts(order.task().resource().type())) return false;
        if (runtime.queue.contains(order.id())) return true;
        if (!runtime.queue.enqueue(order)) return false;
        runtime.profile = new WorkerProfile(runtime.profile.workerId(), runtime.profile.name(),
                runtime.profile.job(), true);
        runtime.status = runtime.active() ? "Task queued" : "Finding a delivery";
        setChanged();
        sendData();
        return true;
    }

    // Queue one order on the first compatible worker
    public boolean submit(WorkerWorkOrder order) {
        if (order == null) return false;
        for (UUID workerId : compatibleWorkers(order.task().resource().type(), List.of())) {
            if (submit(workerId, order)) return true;
        }
        return false;
    }

    // Cancel one queued order while keeping any worker's real carried cargo recoverable
    public boolean cancel(UUID orderId) {
        boolean changed = false;
        for (WorkerRuntime runtime : workers.values()) {
            WorkerWorkOrder current = runtime.queue.current();
            if (current != null && orderId != null && orderId.equals(current.id()) && runtime.hasCargo()) continue;
            if (!runtime.queue.cancel(orderId)) continue;
            resetRuntime(runtime, "Task cancelled");
            changed = true;
        }
        if (!changed) return false;
        setChanged();
        sendData();
        return true;
    }

    // Configure selected workers and replace their idle task queue
    public void configure(Collection<UUID> workerIds, String name, WorkerProfile.Job job,
                          WorkerResourceType resourceType, ResourceLocation resourceId, long amount,
                          boolean enabled, WorkerWorkOrder.Mode mode, @Nullable UUID sourceEndpoint,
                          @Nullable UUID destinationEndpoint, @Nullable UUID processorEndpoint,
                          ResourceLocation outputResourceId, long outputAmount) {
        Set<UUID> selected = workerIds == null ? Set.of() : Set.copyOf(workerIds);
        for (WorkerRuntime runtime : workers.values()) {
            if (!selected.contains(runtime.profile.workerId())) continue;
            if (runtime.hasCargo()) {
                runtime.status = "Finish or recover the carried delivery before reconfiguring";
                continue;
            }
            runtime.profile = new WorkerProfile(runtime.profile.workerId(), name, job, enabled);
            runtime.queue.clear();
            runtime.configuration = null;
            if (enabled) {
                UUID orderId = UUID.nameUUIDFromBytes((podId + ":" + runtime.profile.workerId()
                        + ":" + resourceType + ":" + resourceId + ":" + amount).getBytes(StandardCharsets.UTF_8));
                WorkerTask task = new WorkerTask(orderId, "Deliver " + resourceId,
                        new WorkerResourceKey(resourceType, resourceId), amount, 0L, 0, true);
                WorkerResourceKey output = new WorkerResourceKey(resourceType,
                        outputResourceId == null ? resourceId : outputResourceId);
                runtime.configuration = new WorkerWorkOrder(orderId, task, mode, sourceEndpoint,
                        destinationEndpoint, processorEndpoint, output, outputAmount);
                runtime.queue.enqueue(runtime.configuration);
            }
            resetRuntime(runtime, enabled ? "Finding a delivery" : "Worker disabled");
            PlayerMannequinEntity mannequin = findMannequin(runtime);
            if (mannequin != null) applyName(runtime, mannequin);
        }
        setChanged();
        sendData();
    }

    // Get a worker profile for compatibility with existing screens
    public WorkerProfile profile() {
        return workers.isEmpty() ? new WorkerProfile(podId, "Unassigned Pod", WorkerProfile.Job.ANY, false)
                : workers.values().iterator().next().profile;
    }

    // Get the first worker task for compatibility with existing screens
    public WorkerTask task() {
        WorkerWorkOrder order = workOrder();
        return order == null ? idleTask() : order.task();
    }

    // Get the first configured order for compatibility with existing screens
    public @Nullable WorkerWorkOrder workOrder() {
        return workers.isEmpty() ? null : workers.values().iterator().next().configuration;
    }

    // Get the first worker status for compatibility with existing screens
    public String status() {
        return workers.isEmpty() ? "No mannequins assigned" : workers.values().iterator().next().status;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tick one assigned worker
    private void tickWorker(ServerLevel serverLevel, @Nullable AdvancedContraptionControllerBlockEntity controller,
                            WorkerRuntime runtime, boolean refreshEndpoints) {
        PlayerMannequinEntity mannequin = findMannequin(runtime);
        if (mannequin == null) {
            runtime.status = "Assigned mannequin is unloaded";
            return;
        }
        runtime.lastPosition = mannequin.blockPosition();
        runtime.skinVariant = mannequin.getVariant().id();
        mannequin.setAssignedWorkerPod(podId);
        applyName(runtime, mannequin);
        if (runtime.hasCargo()) {
            if (runtime.cargoToken == null) {
                if (!syncHeldCargo(runtime, mannequin)) {
                    runtime.status = "Worker inventory is full; cargo is awaiting recovery";
                    presentWorker(runtime, mannequin);
                    return;
                }
                setChanged();
            } else if (!cargoStored(runtime, mannequin)) {
                releaseStoredCargo(runtime, mannequin);
                runtime.carried = null;
                runtime.cargoToken = null;
                mannequin.setWorkerCarryProp(ItemStack.EMPTY);
                resetRuntime(runtime, "Cargo recovered by player");
                setChanged();
            }
        } else if (mannequin.hasWorkerCarryProp()) {
            mannequin.setWorkerCarryProp(ItemStack.EMPTY);
        }
        WorkerWorkOrder order = runtime.queue.current();
        if (order == null) {
            if (runtime.idleNavigation == null && runtime.idlePauseTicks <= 0) resetRuntime(runtime, "Idle");
            idleWorker(controller, runtime, mannequin);
            presentWorker(runtime, mannequin);
            return;
        }
        WorkerTask task = order.task();
        if (controller == null) {
            resetRuntime(runtime, "Waiting for an SCM-linked ACC");
            presentWorker(runtime, mannequin);
            return;
        }
        if (runtime.hasCargo() && runtime.stage == Stage.IDLE) resumeCargo(controller, runtime, mannequin);
        boolean eligible = runtime.profile.enabled() && task.pending()
                && runtime.profile.job().accepts(task.resource().type());
        if (refreshEndpoints && eligible && runtime.stage == Stage.IDLE && !runtime.hasCargo()) {
            assign(controller, runtime, mannequin);
        }
        if (!eligible) {
            if (!task.pending()) {
                runtime.queue.completeCurrent();
                resetRuntime(runtime, runtime.queue.current() == null ? "Task complete" : "Next task queued");
                setChanged();
            } else {
                resetRuntime(runtime, "Worker disabled");
            }
            presentWorker(runtime, mannequin);
            return;
        }
        if (runtime.stage.moving()) navigateWorker(runtime, mannequin);
        if (runtime.stage == Stage.WAITING_PROCESSOR_OUTPUT) collectProcessorOutput(runtime, mannequin);
        presentWorker(runtime, mannequin);
    }

    // Assign the closest valid SCM-linked source and receiver
    private void assign(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                        PlayerMannequinEntity mannequin) {
        WorkerWorkOrder order = runtime.queue.current();
        if (order == null) return;
        List<WorkerStorageEndpoint> storageEndpoints = endpoints(controller, runtime,
                order.processing() || order.mode() == WorkerWorkOrder.Mode.FROG_PORT
                        || order.sourceEndpointId() != null || order.destinationEndpointId() != null);
        List<WorkerEndpoint> endpoints = new ArrayList<>(storageEndpoints);
        WorkerEndpoint playerDestination = playerEndpoint(order.destinationEndpointId());
        if (playerDestination != null) endpoints.add(playerDestination);
        runtime.assignment = WorkerDispatcher.assign(order, endpoints, mannequin.position(),
                carryingLimit(order.task().resource().type()));
        if (runtime.assignment == null || !runtime.assignment.assigned()) {
            runtime.assignment = null;
            runtime.status = unavailableAssignmentStatus(order, endpoints);
            sendData();
            return;
        }
        runtime.cargoSourceEndpoint = runtime.assignment.source().id();
        runtime.cargoProcessorEndpoint = runtime.assignment.processor() == null
                ? null : runtime.assignment.processor().id();
        runtime.cargoTargetEndpoint = runtime.assignment.target().id();
        runtime.deliveryAmount = runtime.assignment.amount();
        beginNavigation(runtime, Stage.MOVING_SOURCE, mannequin);
        runtime.status = "Live navigating to source";
        sendData();
    }

    // Resolve SCM endpoints in the worker's own level
    private List<WorkerStorageEndpoint> endpoints(AdvancedContraptionControllerBlockEntity controller,
                                                  WorkerRuntime runtime, boolean includeMachines) {
        UUID workerSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        List<WorkerStorageEndpoint> endpoints = WorkerStorageEndpoint.linked(controller, includeMachines).stream()
                .filter(endpoint -> Objects.equals(workerSubLevelId, endpoint.subLevelId())).toList();
        List<WorkerStorageEndpoint> docks = endpoints.stream().filter(WorkerStorageEndpoint::isDock).toList();
        WorkerWorkOrder order = runtime.queue.current();
        if (docks.isEmpty() || order == null) return endpoints;
        boolean unloadingDock = docks.stream().anyMatch(endpoint -> endpoint.available(order.task().resource()) > 0L);
        return endpoints.stream().map(endpoint -> endpoint.isDock()
                ? endpoint.withAccess(unloadingDock, !unloadingDock)
                : endpoint.withAccess(!unloadingDock, unloadingDock)).toList();
    }

    // Begin live ground navigation toward the endpoint selected by the current work stage
    private void beginNavigation(WorkerRuntime runtime, Stage destinationStage, PlayerMannequinEntity mannequin) {
        runtime.navigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                mannequin != null && mannequin.hasWorkerFlight());
        runtime.idleNavigation = null;
        runtime.idleDestination = null;
        runtime.idlePauseTicks = 0;
        runtime.stage = destinationStage;
    }

    // Update one worker from a collision-checked path prefix calculated from its current live position
    private void navigateWorker(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.assignment == null || !runtime.assignment.assigned()) {
            resetRuntime(runtime, "Delivery assignment expired");
            return;
        }
        WorkerEndpoint endpoint = switch (runtime.stage) {
            case MOVING_SOURCE -> runtime.assignment.source();
            case MOVING_PROCESSOR -> runtime.assignment.processor();
            case MOVING_TARGET -> runtime.assignment.target();
            default -> null;
        };
        if (endpoint == null) {
            resetRuntime(runtime, "Delivery endpoint is unavailable");
            return;
        }
        if (runtime.navigation == null) runtime.navigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                mannequin.hasWorkerFlight());
        Vec3 current = mannequin.position();
        Vec3 interaction = interactionPosition(endpoint, current);
        if (interaction == null) {
            runtime.navigation = null;
            runtime.status = "Waiting for a reachable exterior " + destinationName(runtime.stage) + " face";
            sendData();
            return;
        }
        WorkerPathing.NavigationStep update = runtime.navigation.advance(level, current,
                interaction, PATH_BUDGET, MOVEMENT_SPEED);
        if (update.unavailable()) {
            runtime.navigation = null;
            runtime.status = "Waiting for a live path to " + destinationName(runtime.stage);
            sendData();
            return;
        }
        Vec3 next = update.position();
        Vec3 movement = next.subtract(current);
        if (movement.lengthSqr() > 1.0E-9D) {
            mannequin.setYRot((float) Math.toDegrees(Math.atan2(-movement.x, movement.z)));
            mannequin.setPos(next);
            mannequin.setDeltaMovement(Vec3.ZERO);
            mannequin.setOnGround(true);
        }
        runtime.status = "Live navigating to " + destinationName(runtime.stage);
        if (update.arrived()) arrive(runtime, mannequin);
    }

    // Walk an idle worker between linked automation endpoints without assigning a home position
    private void idleWorker(@Nullable AdvancedContraptionControllerBlockEntity controller,
                            WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.idlePauseTicks > 0) {
            runtime.idlePauseTicks--;
            return;
        }
        if (runtime.idleDestination == null) {
            if (controller == null) {
                runtime.idlePauseTicks = IDLE_WANDER_PAUSE_TICKS;
                return;
            }
            List<WorkerStorageEndpoint> endpoints = endpoints(controller, runtime, true);
            for (int attempt = 0; attempt < Math.min(8, endpoints.size()); attempt++) {
                WorkerStorageEndpoint endpoint = endpoints.get(level.random.nextInt(endpoints.size()));
                Vec3 standing = interactionPosition(endpoint, mannequin.position());
                if (standing == null || standing.distanceToSqr(mannequin.position()) < 2.25D) continue;
                runtime.idleDestination = standing;
                runtime.idleNavigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                        mannequin.hasWorkerFlight());
                runtime.status = "Checking " + endpoint.label();
                break;
            }
            if (runtime.idleDestination == null) {
                runtime.idlePauseTicks = IDLE_WANDER_PAUSE_TICKS;
                return;
            }
        }
        WorkerPathing.LiveNavigator navigator = runtime.idleNavigation;
        if (navigator == null) {
            runtime.idleDestination = null;
            return;
        }
        Vec3 current = mannequin.position();
        WorkerPathing.NavigationStep update = navigator.advance(level, current, runtime.idleDestination,
                PATH_BUDGET, MOVEMENT_SPEED);
        if (update.unavailable() || update.arrived()) {
            runtime.idleNavigation = null;
            runtime.idleDestination = null;
            runtime.idlePauseTicks = IDLE_WANDER_PAUSE_TICKS + level.random.nextInt(IDLE_WANDER_PAUSE_TICKS);
            return;
        }
        Vec3 next = update.position();
        Vec3 movement = next.subtract(current);
        if (movement.lengthSqr() <= 1.0E-9D) return;
        mannequin.setYRot((float) Math.toDegrees(Math.atan2(-movement.x, movement.z)));
        mannequin.setPos(next);
        mannequin.setDeltaMovement(Vec3.ZERO);
        mannequin.setOnGround(true);
    }

    // Get the readable name for the current live-navigation destination
    private static String destinationName(Stage stage) {
        return switch (stage) {
            case MOVING_SOURCE -> "source";
            case MOVING_PROCESSOR -> "processor";
            case MOVING_TARGET -> "destination";
            default -> "endpoint";
        };
    }

    // Extract, process or deliver the worker's actual held cargo
    private void arrive(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        WorkerWorkOrder order = runtime.queue.current();
        if (order == null || runtime.assignment == null || !runtime.assignment.assigned()) {
            resetRuntime(runtime, "Delivery assignment expired");
            return;
        }
        if (runtime.stage == Stage.MOVING_SOURCE) {
            runtime.carried = runtime.assignment.source().extract(
                    order.task().resource(), runtime.assignment.amount(), false);
            if (!runtime.hasCargo()) {
                resetRuntime(runtime, "Source no longer has the resource");
                return;
            }
            runtime.deliveryAmount = runtime.carried.amount();
            runtime.cargoToProcessor = runtime.assignment.processor() != null;
            runtime.processingInputAmount = runtime.cargoToProcessor ? runtime.deliveryAmount : 0L;
            if (!syncHeldCargo(runtime, mannequin)) {
                runtime.assignment.source().insert(runtime.carried, false);
                runtime.carried = null;
                runtime.cargoToken = null;
                resetRuntime(runtime, "Worker inventory is full");
                setChanged();
                sendData();
                return;
            }
            mannequin.startWorkerInteraction();
            beginNavigation(runtime, runtime.assignment.processor() == null
                    ? Stage.MOVING_TARGET : Stage.MOVING_PROCESSOR, mannequin);
            runtime.status = runtime.assignment.processor() == null
                    ? "Live navigating to destination" : "Live navigating to processor";
            setChanged();
            sendData();
            return;
        }
        if (runtime.stage == Stage.MOVING_PROCESSOR && runtime.hasCargo()
                && runtime.assignment.processor() != null) {
            if (!canInsertHeldCargo(runtime.assignment.processor(), runtime.carried)) {
                runtime.status = "Waiting for processor input space";
                return;
            }
            long inserted = runtime.assignment.processor().insert(runtime.carried, false);
            runtime.carried = runtime.carried.remainderAfter(inserted);
            syncHeldCargo(runtime, mannequin);
            mannequin.startWorkerInteraction();
            if (runtime.hasCargo()) {
                runtime.status = "Waiting for processor input space";
                setChanged();
                return;
            }
            runtime.carried = null;
            runtime.cargoToProcessor = false;
            runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
            runtime.status = "Waiting for processed output";
            setChanged();
            sendData();
            return;
        }
        if (runtime.stage != Stage.MOVING_TARGET || !runtime.hasCargo()) return;
        if (!canInsertHeldCargo(runtime.assignment.target(), runtime.carried)) {
            runtime.status = "Waiting for destination space";
            return;
        }
        long inserted = runtime.assignment.target().insert(runtime.carried, false);
        runtime.carried = runtime.carried.remainderAfter(inserted);
        syncHeldCargo(runtime, mannequin);
        mannequin.startWorkerInteraction();
        if (runtime.hasCargo()) {
            runtime.status = "Waiting for destination space";
            setChanged();
            return;
        }
        runtime.carried = null;
        WorkerTask completed = order.task().complete(order.processing()
                ? runtime.processingInputAmount : runtime.deliveryAmount);
        runtime.queue.updateCurrent(order.withTask(completed));
        if (!completed.pending()) runtime.queue.completeCurrent();
        resetRuntime(runtime, runtime.queue.current() == null ? "Task complete" : "Next task queued");
        setChanged();
        sendData();
    }

    // Avoid partial fluid insertion because the held bucket is an actual recoverable item
    private static boolean canInsertHeldCargo(WorkerEndpoint endpoint, WorkerResourcePacket packet) {
        if (packet.resource().type() != WorkerResourceType.FLUID
                && packet.resource().type() != WorkerResourceType.FUEL) return true;
        return endpoint.insert(packet, true) >= packet.amount();
    }

    // Collect processed output and begin its live delivery
    private void collectProcessorOutput(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        WorkerWorkOrder order = runtime.queue.current();
        if (order == null || runtime.assignment == null || runtime.assignment.processor() == null) {
            resetRuntime(runtime, "Processor assignment expired");
            return;
        }
        if (runtime.hasCargo()) {
            runtime.status = "Deliver the current worker cargo before collecting output";
            return;
        }
        long requested = expectedProcessorOutput(order, runtime);
        long available = runtime.assignment.processor().available(order.outputResource());
        if (available <= 0L) return;
        runtime.carried = runtime.assignment.processor().extract(order.outputResource(),
                Math.min(requested, available), false);
        if (!runtime.hasCargo()) return;
        runtime.deliveryAmount = runtime.carried.amount();
        runtime.cargoToProcessor = false;
        if (!syncHeldCargo(runtime, mannequin)) {
            runtime.assignment.processor().insert(runtime.carried, false);
            runtime.carried = null;
            runtime.cargoToken = null;
            runtime.status = "Worker inventory is full";
            setChanged();
            sendData();
            return;
        }
        mannequin.startWorkerInteraction();
        beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
        runtime.status = "Live navigating to destination";
        setChanged();
        sendData();
    }

    // Scale a configured total output amount to the input currently held by this worker
    private static long expectedProcessorOutput(WorkerWorkOrder order, WorkerRuntime runtime) {
        long input = Math.max(1L, runtime.processingInputAmount);
        if (order.outputAmount() <= 0L) return input;
        long totalInput = Math.max(1L, order.task().requestedAmount());
        if (order.outputAmount() > Long.MAX_VALUE / input) return Long.MAX_VALUE;
        long scaled = order.outputAmount() * input;
        long whole = scaled / totalInput;
        return Math.max(1L, whole + (scaled % totalInput == 0L ? 0L : 1L));
    }

    // Rebuild an interrupted delivery from stable SCM endpoint ids
    private void resumeCargo(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                             PlayerMannequinEntity mannequin) {
        if (runtime.cargoSourceEndpoint == null || runtime.cargoTargetEndpoint == null) {
            runtime.status = "Holding cargo until its route is available";
            return;
        }
        List<WorkerStorageEndpoint> endpoints = endpoints(controller, runtime, true);
        WorkerEndpoint source = endpoint(endpoints, runtime.cargoSourceEndpoint);
        WorkerEndpoint processor = endpoint(endpoints, runtime.cargoProcessorEndpoint);
        WorkerEndpoint target = endpoint(endpoints, runtime.cargoTargetEndpoint);
        if (target == null) target = playerEndpoint(runtime.cargoTargetEndpoint);
        if (source == null || target == null || runtime.cargoToProcessor && processor == null) {
            runtime.status = "Holding cargo until its SCM endpoint is loaded";
            return;
        }
        runtime.assignment = new WorkerDispatcher.WorkAssignment(source, processor, target,
                Math.max(1L, runtime.deliveryAmount));
        beginNavigation(runtime, runtime.cargoToProcessor ? Stage.MOVING_PROCESSOR : Stage.MOVING_TARGET, mannequin);
        runtime.status = runtime.cargoToProcessor
                ? "Resuming live navigation to processor" : "Resuming live navigation to destination";
        sendData();
    }

    // Update one mannequin's work animation
    private void presentWorker(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.stage.moving() || runtime.idleDestination != null) {
            mannequin.setWorkerAnimation(runtime.hasCargo()
                    ? PlayerMannequinEntity.WorkerAnimation.CARRY_WALK
                    : PlayerMannequinEntity.WorkerAnimation.WALK);
        } else {
            mannequin.setWorkerAnimation(runtime.hasCargo()
                    ? PlayerMannequinEntity.WorkerAnimation.CARRY_IDLE
                    : PlayerMannequinEntity.WorkerAnimation.IDLE);
        }
    }

    // Synchronize packet custody to the worker inventory and show a Create package as its carry prop.
    private boolean syncHeldCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (!runtime.hasCargo()) {
            clearStoredCargo(runtime, mannequin);
            runtime.cargoToken = null;
            mannequin.setWorkerCarryProp(ItemStack.EMPTY);
            return true;
        }
        UUID token = runtime.cargoToken == null ? UUID.randomUUID() : runtime.cargoToken;
        if (!cargoStored(runtime, mannequin)) {
            clearStoredCargo(runtime, mannequin);
            List<ItemStack> cargo = cargoStacks(runtime.carried, token);
            if (cargo.isEmpty() || !canStoreCargo(mannequin.workerInventory(), cargo)) return false;
            storeCargo(mannequin.workerInventory(), cargo);
        }
        runtime.cargoToken = token;
        mannequin.setWorkerCarryProp(PackageStyles.getDefaultBox());
        return true;
    }

    // Create recoverable worker-inventory stacks for one resource packet.
    private ItemStack cargoStack(@Nullable WorkerResourcePacket packet) {
        if (packet == null || packet.isEmpty()) return ItemStack.EMPTY;
        if (packet.resource().type() == WorkerResourceType.ENERGY) {
            return CTItems.WORKER_ENERGY_BATTERY == null ? ItemStack.EMPTY
                    : WorkerEnergyBatteryItem.charged(CTItems.WORKER_ENERGY_BATTERY.get(), packet.amount());
        }
        if (packet.resource().type() == WorkerResourceType.FLUID
                || packet.resource().type() == WorkerResourceType.FUEL) {
            var fluid = BuiltInRegistries.FLUID.get(packet.resource().id());
            if (fluid == null) return ItemStack.EMPTY;
            ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid,
                    (int) Math.min(1000L, packet.amount())));
            if (!bucket.isEmpty()) return bucket;
            ItemStack carrier = new ItemStack(Items.BUCKET);
            CompoundTag data = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.putString(WORKER_FLUID_CARGO_TAG, packet.resource().id().toString());
            carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            carrier.set(DataComponents.CUSTOM_NAME, Component.literal("Bucket of "
                    + fluid.getFluidType().getDescription().getString()));
            return carrier;
        }
        ItemStack stack = packet.payload().contains("Stack")
                ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                : new ItemStack(BuiltInRegistries.ITEM.get(packet.resource().id()));
        return stack.isEmpty() ? ItemStack.EMPTY
                : stack.copyWithCount((int) Math.min(stack.getMaxStackSize(), packet.amount()));
    }

    // Split one packet into marked storage stacks so custody remains recoverable and player-visible.
    private List<ItemStack> cargoStacks(WorkerResourcePacket packet, UUID token) {
        ItemStack template = cargoStack(packet);
        if (template.isEmpty() || token == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        if (packet.resource().type() != WorkerResourceType.ITEM) {
            result.add(markCargo(template.copy(), token));
            return result;
        }
        long remaining = packet.amount();
        while (remaining > 0L) {
            int count = (int) Math.min(template.getMaxStackSize(), remaining);
            result.add(markCargo(template.copyWithCount(count), token));
            remaining -= count;
        }
        return result;
    }

    // Mark one real carried stack as belonging to a single in-progress worker transfer.
    private static ItemStack markCargo(ItemStack stack, UUID token) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putUUID(WORKER_CARGO_TOKEN_TAG, token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    // Check whether the worker inventory still contains every marked stack for the packet.
    private boolean cargoStored(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (!runtime.hasCargo() || runtime.cargoToken == null) return false;
        List<ItemStack> expected = cargoStacks(runtime.carried, runtime.cargoToken);
        if (expected.isEmpty()) return false;
        ItemStackHandler inventory = mannequin.workerInventory();
        List<ItemStack> actual = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (hasCargoToken(stack, runtime.cargoToken)) actual.add(stack.copy());
        }
        for (ItemStack required : expected) {
            int missing = required.getCount();
            for (ItemStack present : actual) {
                if (!ItemStack.isSameItemSameComponents(required, present)) continue;
                int used = Math.min(missing, present.getCount());
                missing -= used;
                present.shrink(used);
                if (missing == 0) break;
            }
            if (missing > 0) return false;
        }
        return actual.stream().allMatch(ItemStack::isEmpty);
    }

    // Check whether every cargo stack can fit before changing any worker storage.
    private static boolean canStoreCargo(ItemStackHandler inventory, List<ItemStack> cargo) {
        ItemStackHandler simulated = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            simulated.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        for (ItemStack next : cargo) {
            ItemStack remainder = next.copy();
            for (int slot = 0; slot < simulated.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = simulated.insertItem(slot, remainder, false);
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    // Insert cargo only after an all-or-nothing storage simulation succeeds.
    private static void storeCargo(ItemStackHandler inventory, List<ItemStack> cargo) {
        for (ItemStack next : cargo) {
            ItemStack remainder = next.copy();
            for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = inventory.insertItem(slot, remainder, false);
            }
        }
    }

    // Remove the marked representation when the endpoint accepts the resource or it is recovered.
    private static void clearStoredCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.cargoToken == null) return;
        ItemStackHandler inventory = mannequin.workerInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (hasCargoToken(stack, runtime.cargoToken)) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    // Release retained stacks to normal worker storage after a player recovers some cargo.
    private static void releaseStoredCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.cargoToken == null) return;
        ItemStackHandler inventory = mannequin.workerInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!hasCargoToken(stack, runtime.cargoToken)) continue;
            CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.remove(WORKER_CARGO_TOKEN_TAG);
            if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
            else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            inventory.setStackInSlot(slot, stack);
        }
    }

    // Check a stack for this runtime's hidden custody token.
    private static boolean hasCargoToken(ItemStack stack, UUID token) {
        if (stack == null || stack.isEmpty() || token == null) return false;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID(WORKER_CARGO_TOKEN_TAG)
                && token.equals(data.getUUID(WORKER_CARGO_TOKEN_TAG));
    }

    // Refresh SCM-linked endpoint contents for every worker selector
    private void refreshLinkedEndpoints(@Nullable AdvancedContraptionControllerBlockEntity controller) {
        UUID workerSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        List<WorkerEndpointSnapshot> next = controller == null ? List.of()
                : WorkerStorageEndpoint.linked(controller, true).stream()
                .filter(endpoint -> Objects.equals(workerSubLevelId, endpoint.subLevelId()))
                .map(WorkerStorageEndpoint::snapshot).toList();
        if (next.equals(endpointSnapshots)) return;
        endpointSnapshots = next;
        setChanged();
        sendData();
    }

    // Resolve the ACC which most recently bound this remote base station
    private @Nullable AdvancedContraptionControllerBlockEntity owningController() {
        if (level == null) return null;
        if (controllerPos != null) {
            AdvancedContraptionControllerBlockEntity controller = SimulatedHelper.findBlockEntity(level,
                    controllerSubLevelId, controllerPos, AdvancedContraptionControllerBlockEntity.class);
            if (controller != null && linkedPods(controller).stream().anyMatch(pod -> pod.podId.equals(podId))) {
                return controller;
            }
        }
        for (Direction direction : Direction.values()) {
            BlockEntity adjacent = level.getBlockEntity(worldPosition.relative(direction));
            if (adjacent instanceof AdvancedContraptionControllerBlockEntity controller) {
                bindController(controller);
                return controller;
            }
        }
        return null;
    }

    // Find one assigned mannequin entity
    private @Nullable PlayerMannequinEntity findMannequin(WorkerRuntime runtime) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        Entity entity = serverLevel.getEntity(runtime.profile.workerId());
        return entity instanceof PlayerMannequinEntity mannequin && mannequin.isAlive() ? mannequin : null;
    }

    // Apply the configured worker name
    private static void applyName(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (!mannequin.hasCustomName()
                || !mannequin.getCustomName().getString().equals(runtime.profile.name())) {
            mannequin.setCustomName(Component.literal(runtime.profile.name()));
            mannequin.setCustomNameVisible(true);
        }
    }

    // Create one remote status snapshot
    private WorkerStatusSnapshot snapshot(WorkerRuntime runtime) {
        PlayerMannequinEntity mannequin = findMannequin(runtime);
        BlockPos position = mannequin == null ? runtime.lastPosition : mannequin.blockPosition();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        return new WorkerStatusSnapshot(runtime.profile.workerId(), runtime.profile.name(),
                runtime.profile.job(), runtime.profile.enabled(), subLevelId, position, runtime.status,
                runtime.skinVariant, runtime.queue.current(), runtime.queue.planned());
    }

    // Find one live endpoint by persistent identity
    private static @Nullable WorkerEndpoint endpoint(List<? extends WorkerEndpoint> endpoints,
                                                     @Nullable UUID id) {
        if (id == null) return null;
        return endpoints.stream().filter(endpoint -> id.equals(endpoint.id())).findFirst().orElse(null);
    }

    // Adapt the explicitly requested online player into a live item-delivery target.
    private @Nullable WorkerEndpoint playerEndpoint(@Nullable UUID playerId) {
        if (playerId == null || !(level instanceof ServerLevel serverLevel)) return null;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
        if (player == null || player.serverLevel() != serverLevel) return null;
        return new WorkerPlayerEndpoint(player);
    }

    // Get a reachable interaction position beside an endpoint
    private @Nullable Vec3 interactionPosition(WorkerEndpoint endpoint, Vec3 origin) {
        return WorkerPathing.reachableInteractionPosition(level, endpoint.interactionBlocks(),
                endpoint.interactionFace(), origin);
    }

    // Get one worker's maximum transferable amount
    private static long carryingLimit(WorkerResourceType type) {
        return switch (type) {
            case ITEM -> 64L;
            case FLUID, FUEL -> 1000L;
            case ENERGY -> WorkerEnergyBatteryItem.MAX_ENERGY;
        };
    }

    // Explain which SCM endpoint requirement prevented a new delivery assignment
    private static String unavailableAssignmentStatus(WorkerWorkOrder order,
                                                      List<? extends WorkerEndpoint> endpoints) {
        if (endpoints.isEmpty()) return "No SCM endpoints loaded for this worker";
        WorkerResourceKey resource = order.task().resource();
        WorkerEndpoint selectedSource = endpoint(endpoints, order.sourceEndpointId());
        if (order.sourceEndpointId() != null) {
            if (selectedSource == null) return "Selected SCM source is unavailable";
            if (!selectedSource.canExtract(resource)) return "Selected SCM source cannot extract " + resource.id();
            if (selectedSource.available(resource) <= 0L) return "Selected SCM source has no " + resource.id();
        }
        WorkerEndpoint selectedTarget = endpoint(endpoints, order.destinationEndpointId());
        if (order.destinationEndpointId() != null) {
            if (selectedTarget == null) return "Selected SCM destination is unavailable";
            if (!selectedTarget.canInsert(resource)) {
                return "Selected SCM destination cannot receive " + resource.id();
            }
            if (selectedTarget.space(resource) <= 0L) {
                return "Selected SCM destination has no space for " + resource.id();
            }
        }
        boolean source = endpoints.stream().anyMatch(endpoint -> endpoint.canExtract(resource)
                && endpoint.available(resource) > 0L);
        boolean target = endpoints.stream().anyMatch(endpoint -> endpoint.canInsert(resource)
                && endpoint.space(resource) > 0L);
        if (!source) return "No SCM source has " + resource.id();
        if (!target) return "No SCM destination has space for " + resource.id();
        return "No matching SCM source and destination";
    }

    // Clear transient path state while retaining recoverable cargo
    private void resetRuntime(WorkerRuntime runtime, String nextStatus) {
        String resolved = runtime.hasCargo() ? nextStatus + "; retaining cargo" : nextStatus;
        runtime.navigation = null;
        runtime.stage = Stage.IDLE;
        if (!runtime.hasCargo()) {
            runtime.assignment = null;
            runtime.cargoSourceEndpoint = null;
            runtime.cargoProcessorEndpoint = null;
            runtime.cargoTargetEndpoint = null;
            runtime.cargoToProcessor = false;
            runtime.deliveryAmount = 0L;
            runtime.processingInputAmount = 0L;
        }
        runtime.status = resolved;
    }

    // Create an inert task for legacy screen access
    private static WorkerTask idleTask() {
        return new WorkerTask(UUID.randomUUID(), "Idle", WorkerResourceKey.energy(), 0L, 0L, 0, false);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Persistence
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read persistent base station and worker state
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        podId = tag.hasUUID("PodId") ? tag.getUUID("PodId") : UUID.randomUUID();
        controllerSubLevelId = tag.hasUUID("ControllerSubLevel") ? tag.getUUID("ControllerSubLevel") : null;
        controllerPos = tag.contains("ControllerPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("ControllerPos")) : null;
        ListTag endpointTags = tag.getList("WorkerEndpoints", Tag.TAG_COMPOUND);
        endpointSnapshots = endpointTags.stream()
                .map(value -> WorkerEndpointSnapshot.fromTag((CompoundTag) value)).toList();
        workers.clear();
        ListTag workerTags = tag.getList("Workers", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < workerTags.size(); idx++) {
            WorkerRuntime runtime = WorkerRuntime.fromTag(workerTags.getCompound(idx));
            workers.put(runtime.profile.workerId(), runtime);
        }
        if (workers.isEmpty() && tag.hasUUID("Mannequin") && tag.contains("Profile")) {
            WorkerRuntime migrated = WorkerRuntime.fromLegacy(tag);
            workers.put(migrated.profile.workerId(), migrated);
        }
    }

    // Write persistent base station and worker state
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putUUID("PodId", podId);
        if (controllerSubLevelId != null) tag.putUUID("ControllerSubLevel", controllerSubLevelId);
        if (controllerPos != null) tag.putLong("ControllerPos", controllerPos.asLong());
        ListTag endpointTags = new ListTag();
        for (WorkerEndpointSnapshot endpoint : endpointSnapshots) endpointTags.add(endpoint.toTag());
        tag.put("WorkerEndpoints", endpointTags);
        ListTag workerTags = new ListTag();
        for (WorkerRuntime runtime : workers.values()) workerTags.add(runtime.toTag());
        tag.put("Workers", workerTags);
    }

    // Retain the independent state of one assigned mannequin worker
    private static final class WorkerRuntime {
        private WorkerProfile profile;
        private WorkerTaskQueue queue = new WorkerTaskQueue();
        private @Nullable WorkerWorkOrder configuration;
        private @Nullable WorkerDispatcher.WorkAssignment assignment;
        private @Nullable WorkerResourcePacket carried;
        private @Nullable UUID cargoToken;
        private @Nullable UUID cargoSourceEndpoint;
        private @Nullable UUID cargoProcessorEndpoint;
        private @Nullable UUID cargoTargetEndpoint;
        private boolean cargoToProcessor;
        private long deliveryAmount;
        private long processingInputAmount;
        private @Nullable WorkerPathing.LiveNavigator navigation;
        private @Nullable WorkerPathing.LiveNavigator idleNavigation;
        private @Nullable Vec3 idleDestination;
        private int idlePauseTicks;
        private Stage stage = Stage.IDLE;
        private String status = "Idle";
        private BlockPos lastPosition = BlockPos.ZERO;
        private String skinVariant = "";

        // Initialize one assigned worker runtime
        private WorkerRuntime(WorkerProfile profile) {
            this.profile = profile;
        }

        // Create a runtime from a live mannequin
        private static WorkerRuntime create(PlayerMannequinEntity mannequin) {
            String name = mannequin.hasCustomName() ? mannequin.getCustomName().getString() : "Worker";
            WorkerRuntime runtime = new WorkerRuntime(new WorkerProfile(
                    mannequin.getUUID(), name, WorkerProfile.Job.ANY, true));
            runtime.lastPosition = mannequin.blockPosition();
            runtime.skinVariant = mannequin.getVariant().id();
            return runtime;
        }

        // Check whether this runtime has an active route
        private boolean active() {
            return stage != Stage.IDLE || queue.current() != null;
        }

        // Check whether this runtime retains extracted cargo
        private boolean hasCargo() {
            return carried != null && !carried.isEmpty();
        }

        // Serialize this worker runtime
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.put("Profile", profile.toTag());
            tag.put("Queue", queue.toTag());
            if (configuration != null) tag.put("Configuration", configuration.toTag());
            if (hasCargo()) tag.put("Cargo", carried.toTag());
            if (cargoToken != null) tag.putUUID("CargoToken", cargoToken);
            if (cargoSourceEndpoint != null) tag.putUUID("CargoSource", cargoSourceEndpoint);
            if (cargoProcessorEndpoint != null) tag.putUUID("CargoProcessor", cargoProcessorEndpoint);
            if (cargoTargetEndpoint != null) tag.putUUID("CargoTarget", cargoTargetEndpoint);
            tag.putBoolean("CargoToProcessor", cargoToProcessor);
            tag.putLong("DeliveryAmount", deliveryAmount);
            tag.putLong("ProcessingInputAmount", processingInputAmount);
            tag.putString("Status", status);
            tag.putLong("LastPosition", lastPosition.asLong());
            tag.putString("SkinVariant", skinVariant);
            return tag;
        }

        // Deserialize one worker runtime
        private static WorkerRuntime fromTag(CompoundTag tag) {
            WorkerRuntime runtime = new WorkerRuntime(WorkerProfile.fromTag(tag.getCompound("Profile")));
            runtime.queue = WorkerTaskQueue.fromTag(tag.getCompound("Queue"));
            runtime.configuration = tag.contains("Configuration", Tag.TAG_COMPOUND)
                    ? WorkerWorkOrder.fromTag(tag.getCompound("Configuration")) : runtime.queue.current();
            runtime.carried = tag.contains("Cargo", Tag.TAG_COMPOUND)
                    ? WorkerResourcePacket.fromTag(tag.getCompound("Cargo")) : null;
            if (runtime.carried != null && runtime.carried.isEmpty()) runtime.carried = null;
            runtime.cargoToken = tag.hasUUID("CargoToken") ? tag.getUUID("CargoToken") : null;
            runtime.cargoSourceEndpoint = tag.hasUUID("CargoSource") ? tag.getUUID("CargoSource") : null;
            runtime.cargoProcessorEndpoint = tag.hasUUID("CargoProcessor") ? tag.getUUID("CargoProcessor") : null;
            runtime.cargoTargetEndpoint = tag.hasUUID("CargoTarget") ? tag.getUUID("CargoTarget") : null;
            runtime.cargoToProcessor = tag.getBoolean("CargoToProcessor");
            runtime.deliveryAmount = Math.max(0L, tag.getLong("DeliveryAmount"));
            runtime.processingInputAmount = Math.max(0L, tag.getLong("ProcessingInputAmount"));
            runtime.status = tag.getString("Status");
            if (runtime.status.isBlank()) runtime.status = "Idle";
            runtime.lastPosition = BlockPos.of(tag.getLong("LastPosition"));
            runtime.skinVariant = tag.getString("SkinVariant");
            return runtime;
        }

        // Migrate the former single-worker pod format
        private static WorkerRuntime fromLegacy(CompoundTag tag) {
            WorkerProfile oldProfile = WorkerProfile.fromTag(tag.getCompound("Profile"));
            UUID mannequinId = tag.getUUID("Mannequin");
            WorkerRuntime runtime = new WorkerRuntime(new WorkerProfile(mannequinId,
                    oldProfile.name(), oldProfile.job(), oldProfile.enabled()));
            WorkerWorkOrder order = tag.contains("WorkOrder", Tag.TAG_COMPOUND)
                    ? WorkerWorkOrder.fromTag(tag.getCompound("WorkOrder")) : null;
            if (order != null) {
                runtime.configuration = order;
                runtime.queue.enqueue(order);
            }
            runtime.carried = tag.contains("Cargo", Tag.TAG_COMPOUND)
                    ? WorkerResourcePacket.fromTag(tag.getCompound("Cargo")) : null;
            runtime.cargoToken = tag.hasUUID("CargoToken") ? tag.getUUID("CargoToken") : null;
            runtime.cargoSourceEndpoint = tag.hasUUID("CargoSource") ? tag.getUUID("CargoSource") : null;
            runtime.cargoProcessorEndpoint = tag.hasUUID("CargoProcessor") ? tag.getUUID("CargoProcessor") : null;
            runtime.cargoTargetEndpoint = tag.hasUUID("CargoTarget") ? tag.getUUID("CargoTarget") : null;
            runtime.cargoToProcessor = tag.getBoolean("CargoToProcessor");
            runtime.deliveryAmount = tag.getLong("DeliveryAmount");
            runtime.processingInputAmount = tag.getLong("ProcessingInputAmount");
            runtime.status = tag.getString("Status");
            return runtime;
        }
    }

    // Track one worker's current path or interaction stage
    private enum Stage {
        IDLE,
        MOVING_SOURCE,
        MOVING_PROCESSOR,
        WAITING_PROCESSOR_OUTPUT,
        MOVING_TARGET;

        // Check whether this stage is actively navigating toward an endpoint
        private boolean moving() {
            return this == MOVING_SOURCE || this == MOVING_PROCESSOR || this == MOVING_TARGET;
        }
    }
}
