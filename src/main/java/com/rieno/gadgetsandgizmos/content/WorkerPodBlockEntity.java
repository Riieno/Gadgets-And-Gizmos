package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.create.worker.CreateWorkerRecipes;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerDispatcher;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerAssignmentRanker;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerArea;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerDelivery;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerDeliveryTravel;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerCarryCapacity;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerInventoryEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerNoEntryBoundary;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOutputLedger;
import com.rieno.gadgetsandgizmos.lib.inventory.ItemInventoryCapacity;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerPathing;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProfile;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlan;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import com.rieno.gadgetsandgizmos.lib.util.DeferredWorkScheduler;
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
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
    private static final int PROCESSOR_OUTPUT_STALL_TICKS = 20 * 60;
    private static final double POD_INTERIOR_HEIGHT = 0.05D;
    private static final double POD_RETURN_DISTANCE_SQR = 2.25D;
    private static final List<Direction> POD_EXIT_DIRECTIONS = List.of(
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private static final String WORKER_CARGO_TOKEN_TAG = "WorkerCargoToken";
    private static final String WORKER_FLUID_CARGO_TAG = "WorkerFluidCargo";
    private static final String WORKER_FLUID_AMOUNT_TAG = "WorkerFluidAmount";
    private static final Object PROCESSING_RECIPE_CACHE = new Object();
    private static final Object CRAFTING_RECIPE_CACHE = new Object();
    private static final Object STONECUTTER_RECIPE_CACHE = new Object();
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
    private Map<UUID, Map<WorkerResourceKey, Long>> planningWorkerStock = Map.of();
    private long planningInputsRevision;
    private @Nullable UUID controllerSubLevelId;
    private @Nullable BlockPos controllerPos;
    private int discoveryTicks = DISCOVERY_INTERVAL;
    private long noEntryReadTick = Long.MIN_VALUE;
    private List<WorkerArea> noEntryBounds = List.of();

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
    public void onLoad(){
        super.onLoad();
        if(level instanceof ServerLevel) ACTIVE_PODS.put(podId, new WeakReference<>(this));
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
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(controller.getLevel(),
                    target.subLevelId(), target.blockPos());
            WorkerPodBlockEntity pod = blockEntity instanceof WorkerPodBlockEntity workerPod
                    ? workerPod : null;
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
        discoveryTicks = DISCOVERY_INTERVAL;
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

    public long planningInputsRevision(){
        return planningInputsRevision;
    }

    // Get every assigned worker status and planned task queue
    public boolean setWorkerEnabled(UUID workerId, boolean enabled){
        WorkerRuntime runtime = workers.get(workerId);
        if(runtime == null) return false;
        runtime.profile = new WorkerProfile(workerId, runtime.profile.name(), runtime.profile.job(), enabled);
        runtime.status = enabled ? "Worker resumed" : "Worker paused";
        setChanged();
        sendData();
        return true;
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

    // Avoid compiling automatic routines while this pod has nobody to run them.
    public boolean hasAvailableWorker() {
        for (WorkerRuntime runtime : workers.values()) {
            if (runtime.profile.enabled() && findMannequin(runtime) != null) return true;
        }
        return false;
    }

    // Assign and store one mannequin placed directly into this pod
    boolean admitWorker(PlayerMannequinEntity mannequin) {
        if (mannequin == null || mannequin.isConstructionVisual() || mannequin.assignedWorkerPod()
                .filter(assignedPodId -> !assignedPodId.equals(podId)).isPresent()) return false;
        UUID workerId = mannequin.getUUID();
        mannequin.setAssignedWorkerPod(podId);
        WorkerRuntime runtime = workers.computeIfAbsent(workerId, ignored -> WorkerRuntime.create(mannequin));
        houseWorker(runtime, mannequin);
        AdvancedContraptionControllerBlockEntity controller = owningController();
        if (controller != null && !controller.ownsWorker(workerId, podId)) controller.claimWorker(podId, workerId);
        setChanged();
        sendData();
        return true;
    }

    // Find nearby mannequins which can be assigned from the Worker Graph
    public List<PlayerMannequinEntity> assignableMannequins() {
        if (level == null) return List.of();
        AABB bounds = new AABB(worldPosition).inflate(ASSIGNMENT_RANGE);
        return level.getEntitiesOfClass(PlayerMannequinEntity.class, bounds, mannequin -> mannequin.isAlive() && !mannequin.isConstructionVisual()
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

    // Expose the owning controller's current exclusion areas to the assigned worker entity
    public static List<WorkerArea> noEntryBounds(UUID podId, UUID workerId){
        if(podId == null || workerId == null) return List.of();
        WeakReference<WorkerPodBlockEntity> reference = ACTIVE_PODS.get(podId);
        WorkerPodBlockEntity pod = reference == null ? null : reference.get();
        return pod == null || !pod.workers.containsKey(workerId) ? List.of() : pod.noEntryBounds();
    }

    // Expose one worker's unreserved inventory for its own recipe plan
    public Map<WorkerResourceKey, Long> workerPlanningStock(UUID workerId){
        WorkerRuntime runtime = workers.get(workerId);
        PlayerMannequinEntity mannequin = runtime == null ? null : findMannequin(runtime);
        if(mannequin == null) return Map.of();
        Map<WorkerResourceKey, Long> stock = new LinkedHashMap<>(new WorkerInventoryEndpoint(workerId,
                mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                stack -> !hasWorkerCargoToken(stack), false).contents());
        toolInventory(workerId, mannequin).contents().forEach((resource, count) ->
                stock.merge(resource, count, (first, second) -> first >= Long.MAX_VALUE - second
                        ? Long.MAX_VALUE : first + second));
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null) wireless.contents().forEach((resource, count) ->
                stock.merge(resource, count, (first, second) -> first >= Long.MAX_VALUE - second
                        ? Long.MAX_VALUE : first + second));
        return Map.copyOf(stock);
    }

    public long workerMatchingItemStock(UUID workerId, WorkerResourceKey item,
                                        java.util.function.Predicate<ItemStack> matches){
        WorkerRuntime runtime = workers.get(workerId);
        PlayerMannequinEntity mannequin = runtime == null ? null : findMannequin(runtime);
        if(mannequin == null) return 0L;
        WorkerInventoryEndpoint inventory = new WorkerInventoryEndpoint(workerId,
                mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                stack -> !hasWorkerCargoToken(stack), false);
        long total = inventory.availableMatchingItem(item, matches);
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null) total += wireless.availableMatchingItem(item, matches);
        return total;
    }

    public boolean workerWirelessCanExtract(UUID workerId, WorkerResourceKey resource, long amount){
        WorkerRuntime runtime = workers.get(workerId);
        PlayerMannequinEntity mannequin = runtime == null ? null : findMannequin(runtime);
        WorkerInventoryEndpoint wireless = mannequin == null ? null : wirelessInventory(mannequin);
        return wireless != null && wireless.canExtract(resource)
                && wireless.available(resource) >= amount
                && wireless.extract(resource, amount, true).amount() == amount;
    }

    // Automatically enrol mannequins deliberately placed on this pod for its linked controller.
    private void enrollPodOccupants(AdvancedContraptionControllerBlockEntity controller) {
        if (level == null || controller == null) return;
        AABB podTop = new AABB(worldPosition.getX(), worldPosition.getY() + 1.85D, worldPosition.getZ(),
                worldPosition.getX() + 1.0D, worldPosition.getY() + 2.6D, worldPosition.getZ() + 1.0D);
        boolean changed = false;
        for (PlayerMannequinEntity mannequin : level.getEntitiesOfClass(PlayerMannequinEntity.class, podTop,
                candidate -> candidate.isAlive() && !candidate.isConstructionVisual() && (candidate.assignedWorkerPod().isEmpty()
                        || candidate.assignedWorkerPod().filter(podId::equals).isPresent()))) {
            UUID workerId = mannequin.getUUID();
            mannequin.setAssignedWorkerPod(podId);
            if (!workers.containsKey(workerId)) {
                workers.put(workerId, WorkerRuntime.create(mannequin));
                changed = true;
            }
            houseWorker(workers.get(workerId), mannequin);
            if (!controller.ownsWorker(workerId, podId)) controller.claimWorker(podId, workerId);
        }
        if (changed) {
            setChanged();
            sendData();
        }
    }

    // Check whether any assigned worker already owns this order
    public boolean hasQueuedOrder(UUID orderId){
        return orderId != null && workers.values().stream().anyMatch(runtime -> runtime.queue.contains(orderId));
    }

    public boolean hasQueuedOrder(UUID workerId, UUID orderId){
        WorkerRuntime runtime = workers.get(workerId);
        return runtime != null && orderId != null && runtime.queue.contains(orderId);
    }

    // Queue one graph order for a specific assigned worker
    public boolean submit(UUID workerId, WorkerWorkOrder order) {
        WorkerRuntime runtime = workers.get(workerId);
        if (runtime == null || order == null
                || !order.returningToStation() && !runtime.profile.job().accepts(order.task().resource().type())) {
            return false;
        }
        if (runtime.queue.contains(order.id())) return true;
        if (!runtime.queue.enqueue(order)) return false;
        runtime.profile = new WorkerProfile(runtime.profile.workerId(), runtime.profile.name(),
                runtime.profile.job(), true);
        runtime.status = runtime.active() ? "Task queued" : "Finding a delivery";
        setChanged();
        sendData();
        return true;
    }

    // Queue an ordered recipe chain for one assigned worker
    public boolean submit(UUID workerId, List<WorkerWorkOrder> orders) {
        WorkerRuntime runtime = workers.get(workerId);
        if (runtime == null || orders == null || orders.isEmpty()) return false;
        WorkerWorkOrder anchor = orders.getLast();
        if (anchor == null) return false;
        if (runtime.queue.contains(anchor.id())) return true;
        for (WorkerWorkOrder order : orders) {
            if (order == null || !order.returningToStation()
                    && !runtime.profile.job().accepts(order.task().resource().type())) return false;
        }
        if (!runtime.queue.enqueueAll(orders)) return false;
        runtime.profile = new WorkerProfile(runtime.profile.workerId(), runtime.profile.name(),
                runtime.profile.job(), true);
        runtime.status = runtime.queue.current() != null && runtime.queue.current().lookingUpRecipe()
                ? "Looking up recipe" : runtime.active() ? "Task chain queued" : "Finding a delivery";
        setChanged();
        sendData();
        return true;
    }

    // Queue one order on the least loaded compatible worker
    public boolean submit(WorkerWorkOrder order) {
        if (order == null) return false;
        WorkerResourceType type = order.returningToStation() ? null : order.task().resource().type();
        for(WorkerStatusSnapshot worker : WorkerAssignmentRanker.ordered(workerSnapshots(), type, null)){
            if(submit(worker.workerId(), order)) return true;
        }
        return false;
    }

    // Queue an ordered recipe chain on one least loaded compatible worker
    public boolean submit(List<WorkerWorkOrder> orders) {
        if (orders == null || orders.isEmpty()) return false;
        WorkerWorkOrder first = orders.getFirst();
        if (first == null) return false;
        WorkerResourceType type = first.returningToStation() ? null : first.task().resource().type();
        for(WorkerStatusSnapshot worker : WorkerAssignmentRanker.ordered(workerSnapshots(), type, null)){
            if(submit(worker.workerId(), orders)) return true;
        }
        return false;
    }

    // Cancel one queued order while keeping any worker's real carried cargo recoverable
    public boolean cancel(UUID orderId) {
        if (orderId == null) return false;
        boolean changed = false;
        for (WorkerRuntime runtime : workers.values()) {
            WorkerWorkOrder current = runtime.queue.current();
            boolean cancellingCurrent = current != null && orderId.equals(current.id());
            if (cancellingCurrent && runtime.hasCargo()) {
                runtime.cancelAfterDelivery = orderId;
                runtime.status = "Cancel after carried cargo is delivered";
                changed = true;
                continue;
            }
            if (!runtime.queue.cancel(orderId)) continue;
            com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(orderId);
            runtime.suspendedRecipes.remove(orderId);
            runtime.waitingProcessors.remove(orderId);
            if (cancellingCurrent) {
                clearRecipeProgress(runtime);
                resetRuntime(runtime, "Task cancelled");
            }
            else runtime.status = "Queued task cancelled";
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
            runtime.waitingProcessors.clear();
            runtime.suspendedRecipes.clear();
            runtime.configuration = null;
            clearRecipeProgress(runtime);
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

    // Apply a bundled, Mojang, or Steve fallback appearance to one assigned worker
    public boolean applyWorkerSkin(UUID workerId, String playerName, @Nullable PlayerMannequinVariant variant,
                                   String remoteSkinUrl, boolean steveSkin) {
        WorkerRuntime runtime = workers.get(workerId);
        PlayerMannequinEntity mannequin = runtime == null ? null : findMannequin(runtime);
        if (runtime == null || mannequin == null) return false;
        String name = playerName == null || playerName.isBlank() ? runtime.profile.name() : playerName.strip();
        if (name.isBlank()) name = "Worker";
        if (name.length() > 64) name = name.substring(0, 64);
        runtime.profile = new WorkerProfile(runtime.profile.workerId(), name,
                runtime.profile.job(), runtime.profile.enabled());
        mannequin.setRemoteSkinUrl("");
        mannequin.setSteveSkin(steveSkin);
        if (steveSkin) {
            runtime.skinVariant = "steve";
        } else if (variant != null) {
            mannequin.setVariant(variant);
            runtime.skinVariant = variant.id();
        } else if (remoteSkinUrl != null && !remoteSkinUrl.isBlank()) {
            mannequin.setVariant(PlayerMannequinVariants.DEFAULT_ID);
            mannequin.setRemoteSkinUrl(remoteSkinUrl);
            runtime.skinVariant = "remote";
        } else {
            return false;
        }
        applyName(runtime, mannequin);
        setChanged();
        sendData();
        return true;
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
                            WorkerRuntime runtime, boolean refreshEndpoints){
        try{
            advanceWorker(serverLevel, controller, runtime, refreshEndpoints);
        }catch(DeferredWorkScheduler.Pending pending){
            if(!"Looking up recipe".equals(runtime.status)){
                runtime.status = "Looking up recipe";
                sendData();
            }
            PlayerMannequinEntity mannequin = findMannequin(runtime);
            if(mannequin != null) presentWorker(runtime, mannequin);
        }
    }

    private void advanceWorker(ServerLevel serverLevel, @Nullable AdvancedContraptionControllerBlockEntity controller,
                                WorkerRuntime runtime, boolean refreshEndpoints) {
        if(runtime.queue.current() != null || runtime.travel != null){
            WorkerDeliveryTravel.retainPosition(serverLevel, runtime.profile.workerId(), runtime.lastPosition);
        }
        PlayerMannequinEntity mannequin = findMannequin(runtime);
        if (mannequin == null) {
            runtime.status = "Assigned mannequin is unloaded";
            return;
        }
        runtime.lastPosition = mannequin.blockPosition();
        runtime.tickPosition = mannequin.position();
        runtime.skinVariant = mannequin.getVariant().id();
        if(refreshEndpoints) ItemInventoryCapacity.compact(mannequin.workerInventory(),
                stack -> !hasWorkerCargoToken(stack));
        mannequin.setAssignedWorkerPod(podId);
        if (runtime.housed != mannequin.isHousedInWorkerPod()) mannequin.setHousedInWorkerPod(runtime.housed);
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
        // A processor's loose workpiece must remain at its output until its recipe ledger collects it.
        if(runtime.profile.enabled() && !runtime.housed
                && (runtime.queue.current() == null || runtime.queue.current().recipePlan() == null
                || !runtime.queue.current().recipePlan().requiresProcessor()))
            collectNearbyItems(serverLevel, mannequin);
        if (cancelDeferredOrder(runtime)) {
            setChanged();
            sendData();
            presentWorker(runtime, mannequin);
            return;
        }
        if(runtime.travel != null){
            WorkerDeliveryTravel.State travelState = runtime.travel.advance(serverLevel, mannequin, worldPosition,
                    mannequin.hasWorkerFlight());
            runtime.lastPosition = mannequin.blockPosition();
            setChanged();
            if(travelState == WorkerDeliveryTravel.State.HOME){
                runtime.travel = null;
                runtime.navigation = null;
                runtime.assignment = null;
                runtime.stage = Stage.IDLE;
            }else if(travelState != WorkerDeliveryTravel.State.DELIVERING){
                runtime.status = travelState == WorkerDeliveryTravel.State.RETURNING
                        ? "Walking out of view to return" : "Waiting for a clear delivery route";
                presentWorker(runtime, mannequin);
                return;
            }
        }
        if(controller != null && serverLevel.getGameTime() % 10L == 0L){
            renewTransportLocks(controller, runtime);
            resumeReadyProcessor(controller, runtime, mannequin);
        }
        WorkerWorkOrder order = runtime.queue.current();
        if (order == null) {
            if(controller != null && runtime.profile.enabled() && refreshEndpoints
                    && queueWorkerInventoryReturn(runtime, null, endpoints(controller, runtime, false), mannequin)){
                presentWorker(runtime, mannequin);
                return;
            }
            if (runtime.housed) {
                runtime.status = "Housed in Worker Pod";
                presentWorker(runtime, mannequin);
                return;
            }
            if (runtime.idleNavigation == null && runtime.idlePauseTicks <= 0) resetRuntime(runtime, "Idle");
            idleWorker(controller, runtime, mannequin);
            presentWorker(runtime, mannequin);
            return;
        }
        restoreRecipeProgress(runtime);
        if(order.lookingUpRecipe()){
            if(controller != null && runtime.profile.enabled() && refreshEndpoints){
                List<WorkerWorkOrder> orders = controller.retryWorkerRecipe(order,
                        workerPlanningStock(runtime.profile.workerId()));
                if(orders.stream().anyMatch(next -> !runtime.profile.job().accepts(next.task().resource().type()))){
                    runtime.status = "Worker cannot carry this recipe's ingredients";
                    presentWorker(runtime, mannequin);
                    return;
                }
                if(runtime.queue.replaceCurrent(orders)){
                    clearRecipeProgress(runtime);
                    resetRuntime(runtime, "Recipe found");
                    setChanged();
                    sendData();
                }else runtime.status = controller.workerRecipeFailure();
            }
            presentWorker(runtime, mannequin);
            return;
        }
        if (order.returningToStation()) {
            returnToStation(controller, runtime, mannequin, order);
            presentWorker(runtime, mannequin);
            return;
        }
        if (runtime.housed && !deployWorker(runtime, mannequin)) {
            runtime.status = "Waiting for a free block beside the Worker Pod";
            presentWorker(runtime, mannequin);
            return;
        }
        WorkerTask task = order.task();
        if (controller == null) {
            resetRuntime(runtime, "Waiting for an SCM-linked ACC");
            presentWorker(runtime, mannequin);
            return;
        }
        if(runtime.hasCargo() && runtime.stage == Stage.IDLE && runtime.returningSurplus){
            routeSurplus(runtime, mannequin);
            presentWorker(runtime, mannequin);
            return;
        }
        if (runtime.hasCargo() && runtime.stage == Stage.IDLE
                && useCarriedWorkerCraftInput(controller, runtime, mannequin)) {
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
                if(runtime.queue.current() == null) runtime.failedAssemblyAttempts = 0L;
                resetRuntime(runtime, runtime.queue.current() == null ? "Task complete" : "Next task queued");
                setChanged();
            } else {
                resetRuntime(runtime, "Worker disabled");
            }
            presentWorker(runtime, mannequin);
            return;
        }
        if (runtime.stage == Stage.IDLE && runtime.assignment == null && !runtime.hasCargo()) {
            presentWorker(runtime, mannequin);
            return;
        }
        if(runtime.stage == Stage.WAITING_PROCESSOR_OUTPUT && runtime.assignment == null){
            resumeProcessor(controller, runtime, mannequin);
        }
        if(refreshEndpoints && runtime.assignment != null && !runtime.assignment.source().isAvailable()
                && !runtime.hasCargo() && runtime.stage == Stage.MOVING_SOURCE){
            resetRuntime(runtime, "Waiting for loaded SCM source");
        }
        if (runtime.stage.moving()) navigateWorker(runtime, mannequin);
        if(runtime.stage == Stage.WAITING_PROCESSOR_OUTPUT && runtime.assignment != null) collectProcessorOutput(runtime, mannequin);
        if(runtime.stage == Stage.WAITING_PROCESSOR_OUTPUT && !runtime.hasCargo()) parkProcessorWait(runtime);
        presentWorker(runtime, mannequin);
    }

    // Let a deployed worker pick up nearby loose items, including partial stacks.
    private static void collectNearbyItems(ServerLevel level, PlayerMannequinEntity mannequin){
        var bounds = mannequin.getBoundingBox();
        if(bounds == null) return;
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class,
                bounds.inflate(0.65D, 0.25D, 0.65D),
                item -> item.isAlive() && !item.hasPickUpDelay());
        if(nearby == null) return;
        for(ItemEntity item : nearby){
            ItemStack stack = item.getItem();
            if(stack.isEmpty()) continue;
            ItemStack remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
                    mannequin.workerInventory(), stack.copy(), false);
            int collected = stack.getCount() - remainder.getCount();
            if(collected <= 0) continue;
            mannequin.take(item, collected);
            if(remainder.isEmpty()) item.discard();
            else item.setItem(remainder);
        }
    }

    // Renew leased machine gates for active and background processing orders
    private void renewTransportLocks(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime){
        WorkerWorkOrder current = runtime.queue.current();
        if(!runtime.profile.enabled()){
            if(current != null) com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(current.id());
            for(UUID id : runtime.waitingProcessors.keySet())
                com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(id);
            return;
        }
        if((current == null || runtime.recipeProcessorEndpoint == null || current.recipePlan() == null)
                && runtime.waitingProcessors.isEmpty()) return;
        List<WorkerStorageEndpoint> machines = endpoints(controller, runtime, true);
        if(current != null && current.recipePlan() != null && runtime.recipeProcessorEndpoint != null
                && runtime.stage != Stage.MOVING_TARGET){
            WorkerEndpoint processor = endpoint(machines, runtime.recipeProcessorEndpoint);
            if(processor instanceof WorkerStorageEndpoint machine)
                machine.isolateTransport(current.recipePlan(), current.id());
        }
        for(WorkerWorkOrder order : runtime.queue.planned()){
            CompoundTag wait = runtime.waitingProcessors.get(order.id());
            if(wait == null || order.recipePlan() == null || !wait.hasUUID("CargoProcessor")) continue;
            WorkerEndpoint processor = endpoint(machines, wait.getUUID("CargoProcessor"));
            if(processor instanceof WorkerStorageEndpoint machine)
                machine.isolateTransport(order.recipePlan(), order.id());
        }
    }

    // Assign the closest valid SCM-linked source and receiver
    private void assign(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                        PlayerMannequinEntity mannequin) {
        restoreRecipeProgress(runtime);
        WorkerWorkOrder order = runtime.queue.current();
        if (order == null) return;
        if(runtime.retryRecipe){
            List<WorkerWorkOrder> retry = controller.retryWorkerRecipe(order,
                    workerPlanningStock(runtime.profile.workerId()));
            if(!runtime.queue.replaceCurrent(retry)){
                runtime.status = "Assembly needs more resources before it can retry";
                return;
            }
            clearRecipeProgress(runtime);
            order = runtime.queue.current();
        }
        List<WorkerStorageEndpoint> storageEndpoints = endpoints(controller, runtime,
                order.processing() || order.mode() == WorkerWorkOrder.Mode.FROG_PORT
                        || order.sourceEndpointId() != null || order.destinationEndpointId() != null);
        List<WorkerEndpoint> endpoints = new ArrayList<>(storageEndpoints);
        WorkerInventoryEndpoint inventory = recipeInventory(runtime, mannequin);
        WorkerInventoryEndpoint stock = new WorkerInventoryEndpoint(runtime.profile.workerId(),
                mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                stack -> !hasWorkerCargoToken(stack), false);
        endpoints.add(inventory);
        if(order.recipePlan() != null) endpoints.add(toolInventory(runtime.profile.workerId(), mannequin));
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null && wireless.isAvailable()) endpoints.add(wireless);
        if(order.mode() == WorkerWorkOrder.Mode.RECLAIM_INPUT){
            UUID sourceId = order.sourceEndpointId();
            WorkerStorageEndpoint machine = storageEndpoints.stream()
                    .filter(endpoint -> endpoint.id().equals(sourceId))
                    .findFirst().orElse(null);
            if(machine == null || order.recipePlan() == null){
                runtime.status = "Paused machine is unavailable for ingredient recovery";
                return;
            }
            WorkerStorageEndpoint staged = machine.forStagedInputs(order.recipePlan());
            WorkerInventoryEndpoint target = new WorkerInventoryEndpoint(runtime.profile.workerId(),
                    mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                    stack -> !hasWorkerCargoToken(stack), true);
            WorkerResourceKey resource = order.task().resource();
            long amount = Math.min(order.task().remainingAmount(), Math.min(staged.available(resource),
                    Math.min(target.space(resource), carryingLimit(mannequin, resource))));
            if(amount <= 0L){
                runtime.status = "Waiting to recover staged machine ingredients";
                return;
            }
            runtime.assignment = new WorkerDispatcher.WorkAssignment(staged, null, target, amount);
            runtime.deliveryAmount = amount;
            beginNavigation(runtime, Stage.MOVING_SOURCE, mannequin);
            runtime.status = "Recovering staged machine ingredients";
            sendData();
            return;
        }
        if(completeStockedIntermediate(runtime, order, storageEndpoints, stock)) return;
        boolean returningInventory = order.mode() == WorkerWorkOrder.Mode.TRANSFER
                && runtime.profile.workerId().equals(order.sourceEndpointId())
                && order.destinationEndpointId() != null;
        boolean inventoryBlocked = carryingLimit(mannequin, order.task().resource()) <= 0L
                || order.recipePlan() != null && carryingLimit(mannequin, order.outputResource()) <= 0L;
        if(inventoryBlocked && !returningInventory && order.mode() != WorkerWorkOrder.Mode.FILL_CONTAINER
                && queueWorkerInventoryReturn(runtime, order, storageEndpoints, mannequin)) return;
        WorkerEndpoint playerDestination = playerEndpoint(order.destinationEndpointId());
        if (playerDestination != null) endpoints.add(playerDestination);
        if(order.mode() == WorkerWorkOrder.Mode.FILL_CONTAINER){
            runtime.assignment = fluidContainerAssignment(order, endpoints, mannequin);
            if(runtime.assignment == null || !runtime.assignment.assigned()){
                runtime.assignment = null;
                runtime.status = "Waiting for fluid, empty container, or player inventory space";
                sendData();
                return;
            }
            runtime.cargoSourceEndpoint = runtime.assignment.source().id();
            runtime.cargoProcessorEndpoint = runtime.assignment.processor().id();
            runtime.cargoTargetEndpoint = runtime.assignment.target().id();
            runtime.deliveryAmount = 1L;
            beginNavigation(runtime, Stage.MOVING_SOURCE, mannequin);
            runtime.status = "Collecting fluid for container";
            sendData();
            return;
        }
        if(com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.isPortableToolPlan(order.recipePlan())){
            craftWithPortableTool(controller, runtime, order, storageEndpoints, stock, wireless,
                    endpoints, mannequin);
            return;
        }
        if(creditPreloadedRecipeInputs(runtime, order, endpoints, mannequin)) return;
        order = selectTaggedRecipeInput(runtime, order, endpoints);
        runtime.assignment = recipePlanAssignment(order, runtime, endpoints, mannequin);
        if (runtime.assignment == null) runtime.assignment = internalCraftAssignment(order, endpoints, mannequin);
        if (runtime.assignment == null) {
            long carrying = carryingLimit(mannequin, order.task().resource());
            if(returningInventory) carrying = Math.max(carrying, order.task().remainingAmount());
            runtime.assignment = WorkerDispatcher.assign(order, endpoints, mannequin.position(),
                    carrying,
                    endpoint -> !(endpoint instanceof WorkerStorageEndpoint storage)
                            || storage.isProcessingMachine());
        }
        runtime.assignment = alignedCraftStationAssignment(order, runtime.assignment);
        if (runtime.assignment == null || !runtime.assignment.assigned()) {
            runtime.assignment = null;
            if(switchUnavailableRecipeRoute(controller, runtime, order, endpoints)) return;
            if(queueMissingRecipeInputs(controller, runtime, order, storageEndpoints, inventory, mannequin)) return;
            runtime.status = inventoryBlocked && !returningInventory
                    ? "Worker inventory is full; no linked storage accepts its items"
                    : order.recipePlan() == null ? unavailableAssignmentStatus(order, endpoints)
                    : unavailableRecipeAssignmentStatus(order, runtime, endpoints);
            sendData();
            return;
        }
        if(queueMachineSupplies(runtime, order, endpoints)) return;
        runtime.cargoSourceEndpoint = runtime.assignment.source().id();
        runtime.cargoProcessorEndpoint = runtime.assignment.processor() == null
                ? null : runtime.assignment.processor().id();
        runtime.cargoTargetEndpoint = runtime.assignment.target().id();
        if(order.recipePlan() != null && runtime.cargoProcessorEndpoint != null){
            runtime.recipeProcessorEndpoint = runtime.cargoProcessorEndpoint;
        }
        runtime.deliveryAmount = runtime.assignment.amount();
        beginNavigation(runtime, Stage.MOVING_SOURCE, mannequin);
        runtime.status = "Live navigating to source";
        sendData();
    }

    // Use a manually supplied intermediate only when live stock covers every later consumer
    private boolean completeStockedIntermediate(WorkerRuntime runtime, WorkerWorkOrder order,
                                                List<WorkerStorageEndpoint> endpoints,
                                                WorkerInventoryEndpoint inventory){
        if(order.recipePlan() == null || order.destinationEndpointId() != null || runtime.hasCargo()
                || runtime.recipeInputIndex != 0 || runtime.recipeInputDelivered != 0L
                || runtime.recipeBatchCount != 0L) return false;
        WorkerResourceKey result = order.outputResource();
        long demand = runtime.queue.plannedInputDemand(result);
        if(demand <= 0L) return false;
        long stocked = inventory.contents().getOrDefault(result, 0L);
        Set<UUID> seen = new HashSet<>();
        for(WorkerStorageEndpoint endpoint : endpoints){
            if(!endpoint.isWorkerRecipeSource() || !endpoint.isAvailable()
                    || order.sourceEndpointId() != null && !order.sourceEndpointId().equals(endpoint.id())
                    || !seen.add(endpoint.storageId()) || !endpoint.canExtract(result)) continue;
            long amount = endpoint.available(result);
            stocked = stocked > Long.MAX_VALUE - amount ? Long.MAX_VALUE : stocked + amount;
            if(stocked >= demand) break;
        }
        if(stocked < demand) return false;
        runtime.queue.updateCurrent(order.withTask(order.task().complete(order.task().remainingAmount())));
        runtime.queue.completeCurrent();
        clearRecipeProgress(runtime);
        resetRuntime(runtime, "Using stocked recipe result");
        setChanged();
        sendData();
        return true;
    }

    // Run a complete prerequisite chain before resuming a recipe whose live ingredients are missing
    private boolean queueMissingRecipeInputs(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                                             WorkerWorkOrder order, List<WorkerStorageEndpoint> endpoints,
                                             WorkerInventoryEndpoint inventory, PlayerMannequinEntity mannequin){
        if(order.recipePlan() == null || runtime.hasCargo() || runtime.suspendedRecipes.size() >= 64) return false;
        WorkerStorageEndpoint selectedProcessor = endpoints.stream()
                .map(endpoint -> endpoint.forRecipe(order.recipePlan()))
                .filter(endpoint -> endpoint.supportsRecipePlan(order.recipePlan()))
                .filter(endpoint -> order.processorEndpointId() == null
                        || order.processorEndpointId().equals(endpoint.id()))
                .filter(endpoint -> runtime.recipeProcessorEndpoint == null
                        || runtime.recipeProcessorEndpoint.equals(endpoint.id()))
                .min(java.util.Comparator.comparingInt(endpoint -> endpoint.routingPenalty(order.recipePlan())))
                .orElse(null);
        if(runtime.recipeBatchCount <= 0L){
            if(order.recipePlan().requiresProcessor() && selectedProcessor == null) return false;
            runtime.recipeBatchCount = recipeBatchCount(order, order.recipePlan(), selectedProcessor, mannequin);
            // A full processor must not prevent planning the ingredient that will free it.
            if(runtime.recipeBatchCount <= 0L) runtime.recipeBatchCount = 1L;
        }
        Map<WorkerResourceKey, Long> personalStock = new LinkedHashMap<>(inventory.contents());
        if(mannequin.workerCurios() != null)
            toolInventory(runtime.profile.workerId(), mannequin).contents().forEach((resource, count) ->
                    personalStock.merge(resource, count, (first, second) -> first >= Long.MAX_VALUE - second
                            ? Long.MAX_VALUE : first + second));
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null) wireless.contents().forEach((resource, count) ->
                personalStock.merge(resource, count, (first, second) -> first >= Long.MAX_VALUE - second
                        ? Long.MAX_VALUE : first + second));
        var chain = controller.workerRecipePrerequisites(order, runtime.recipeInputIndex,
                runtime.recipeInputDelivered, runtime.recipeBatchCount, endpoints, personalStock);
        boolean requiresStagedStock = false;
        if(!chain.executable() && selectedProcessor != null){
            WorkerStorageEndpoint stagedSource = selectedProcessor.forStagedInputs(order.recipePlan());
            for(var entry : stagedSource.stagedInputItems().entrySet()){
                WorkerResourceKey resource = entry.getKey();
                long amount = entry.getValue();
                if(amount > 0L && stagedSource.extract(resource, amount, true).amount() >= amount){
                    personalStock.merge(resource, amount, (first, second) -> first >= Long.MAX_VALUE - second
                            ? Long.MAX_VALUE : first + second);
                    requiresStagedStock = true;
                }
            }
            if(requiresStagedStock) chain = controller.workerRecipePrerequisites(order,
                    runtime.recipeInputIndex, runtime.recipeInputDelivered, runtime.recipeBatchCount,
                    endpoints, personalStock);
        }
        if(!chain.executable()){
            runtime.status = "Missing ingredients cannot be made from current stock and machine routes";
            return false;
        }
        List<WorkerWorkOrder> orders = new ArrayList<>();
        boolean sharedProcessor = selectedProcessor != null && (requiresStagedStock || chain.steps().stream().anyMatch(step ->
                step.plan().requiresProcessor() && selectedProcessor.supportsRecipePlan(step.plan())));
        if(sharedProcessor){
            WorkerStorageEndpoint staged = selectedProcessor.forStagedInputs(order.recipePlan());
            WorkerInventoryEndpoint recovery = new WorkerInventoryEndpoint(runtime.profile.workerId(),
                    mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                    stack -> !hasWorkerCargoToken(stack), true);
            for(var entry : staged.stagedInputItems().entrySet()){
                WorkerResourceKey resource = entry.getKey();
                long amount = entry.getValue();
                if(amount <= 0L) continue;
                if(staged.extract(resource, amount, true).amount() < amount || recovery.space(resource) < amount){
                    runtime.status = "Waiting for room to recover ingredients from the shared machine";
                    return false;
                }
                UUID reclaimId = UUID.randomUUID();
                WorkerTask reclaim = new WorkerTask(reclaimId, "Clear machine for prerequisite", resource,
                        amount, 0L, order.task().priority(), true);
                orders.add(new WorkerWorkOrder(reclaimId, reclaim, WorkerWorkOrder.Mode.RECLAIM_INPUT,
                        selectedProcessor.id(), runtime.profile.workerId(), null, resource, amount)
                        .withRecipePlan(order.recipePlan()));
            }
        }
        boolean reclaimed = !orders.isEmpty();
        orders.addAll(chain.orders(UUID.randomUUID(), order.task().name(), order.task().priority(),
                null, null, order.sourceEndpointId()));
        if(orders.isEmpty()) return false;
        if(orders.stream().anyMatch(prerequisite -> !runtime.profile.job().accepts(prerequisite.task().resource().type()))
                || !runtime.queue.prepend(orders)) return false;
        CompoundTag progress = new CompoundTag();
        progress.putUUID("Order", order.id());
        progress.putInt("RecipeInputIndex", reclaimed ? 0 : runtime.recipeInputIndex);
        progress.putLong("RecipeInputDelivered", reclaimed ? 0L : runtime.recipeInputDelivered);
        progress.putLong("RecipeBatchCount", reclaimed ? 0L : runtime.recipeBatchCount);
        if(runtime.recipeProcessorEndpoint != null) progress.putUUID("RecipeProcessor", runtime.recipeProcessorEndpoint);
        if(!reclaimed && runtime.processorOutputLedger != null)
            progress.put("ProcessorOutputLedger", runtime.processorOutputLedger.toTag());
        runtime.suspendedRecipes.put(order.id(), progress);
        clearRecipeProgress(runtime);
        resetRuntime(runtime, "Crafting missing recipe ingredients");
        discoveryTicks = DISCOVERY_INTERVAL;
        setChanged();
        sendData();
        return true;
    }

    // Deliver fuel through the machine's supply port before withdrawing recipe ingredients
    private boolean queueMachineSupplies(WorkerRuntime runtime, WorkerWorkOrder order,
                                          List<WorkerEndpoint> endpoints){
        if(order.recipePlan() == null || !(runtime.assignment.processor() instanceof WorkerStorageEndpoint processor)) return false;
        Map<WorkerResourceKey, Long> available = new java.util.LinkedHashMap<>();
        for(WorkerEndpoint endpoint : endpoints){
            if(order.sourceEndpointId() != null && !order.sourceEndpointId().equals(endpoint.id())
                    && !(endpoint instanceof WorkerInventoryEndpoint)) continue;
            if(endpoint instanceof WorkerStorageEndpoint storage && !storage.isWorkerRecipeSource()
                    && !storage.id().equals(order.sourceEndpointId())) continue;
            Map<WorkerResourceKey, Long> contents = endpoint instanceof WorkerInventoryEndpoint inventory ? inventory.contents()
                    : endpoint instanceof WorkerStorageEndpoint storage ? storage.snapshot().resources().stream()
                    .collect(java.util.stream.Collectors.toMap(amount -> amount.resource(), amount -> amount.amount(), Long::sum)) : Map.of();
            contents.forEach((resource, amount) -> {
                if(endpoint.canExtract(resource) && endpoint.available(resource) > 0L) available.merge(resource,
                        endpoint.available(resource), (first, second) -> first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second);
            });
        }
        var supply = processor.machineSupply(order.recipePlan(), available, Math.max(1L, runtime.recipeBatchCount));
        if(supply.ready()) return false;
        runtime.assignment = null;
        runtime.status = supply.status();
        if(supply.resource() == null){ sendData(); return true; }
        UUID id = UUID.randomUUID();
        WorkerTask task = new WorkerTask(id, supply.status(), supply.resource(), supply.amount(), 0L,
                order.task().priority(), true);
        UUID sourceId = order.sourceEndpointId();
        WorkerEndpoint carried = endpoints.stream().filter(endpoint -> endpoint instanceof WorkerInventoryEndpoint
                && endpoint.available(supply.resource()) >= supply.amount()).findFirst().orElse(null);
        if(carried != null) sourceId = carried.id();
        WorkerWorkOrder refill = new WorkerWorkOrder(id, task, WorkerWorkOrder.Mode.TRANSFER,
                supply.withdrawal() ? processor.id() : sourceId, supply.withdrawal() ? null : processor.id(),
                null, supply.resource(), supply.amount());
        if(!runtime.queue.prepend(List.of(refill))) return true;
        CompoundTag progress = new CompoundTag();
        progress.putInt("RecipeInputIndex", runtime.recipeInputIndex);
        progress.putLong("RecipeInputDelivered", runtime.recipeInputDelivered);
        progress.putLong("RecipeBatchCount", runtime.recipeBatchCount);
        if(runtime.recipeProcessorEndpoint != null) progress.putUUID("RecipeProcessor", runtime.recipeProcessorEndpoint);
        if(runtime.processorOutputLedger != null) progress.put("ProcessorOutputLedger", runtime.processorOutputLedger.toTag());
        runtime.suspendedRecipes.put(order.id(), progress);
        clearRecipeProgress(runtime);
        resetRuntime(runtime, supply.status());
        setChanged();
        sendData();
        return true;
    }

    // Portable grids and item-operated tools are recipe permissions, not inventories or workstations.
    // Gather one batch first, then commit its inputs and result in this single server tick.
    private void craftWithPortableTool(AdvancedContraptionControllerBlockEntity controller,
                                       WorkerRuntime runtime, WorkerWorkOrder order,
                                       List<WorkerStorageEndpoint> linked, WorkerInventoryEndpoint stock,
                                       @Nullable WorkerInventoryEndpoint wireless, List<WorkerEndpoint> endpoints,
                                       PlayerMannequinEntity mannequin){
        WorkerRecipePlan plan = order.recipePlan();
        if(plan == null || runtime.hasCargo()) return;
        if(!runtime.equippedRecipeTools.isEmpty()) restoreEquippedRecipeTools(runtime, mannequin);
        if(runtime.recipeInputIndex != 0 || runtime.recipeInputDelivered != 0L
                || runtime.recipeBatchCount != 0L){
            // Old per-slot progress may refer to ingredients stranded in a terminal grid.
            clearRecipeProgress(runtime);
            setChanged();
        }
        WorkerResourceKey toolKey = plan.inputs().getLast().resource();
        int toolSlot = -1;
        for(int slot = 0; slot < mannequin.workerCurios().getSlots(); slot++){
            ItemStack equipped = mannequin.workerCurios().getStackInSlot(slot);
            if(!equipped.isEmpty() && BuiltInRegistries.ITEM.getKey(equipped.getItem()).equals(toolKey.id())){
                toolSlot = slot;
                break;
            }
        }
        if(toolSlot < 0){
            setPortableStatus(runtime, "Required crafting tool is missing from Tools");
            return;
        }
        Map<WorkerResourceKey, Long> required = new LinkedHashMap<>();
        for(int idx = 0; idx < plan.inputs().size() - 1; idx++){
            WorkerRecipePlan.Input input = plan.inputs().get(idx);
            required.merge(input.resource(), input.amount(), Long::sum);
        }
        Map<WorkerResourceKey, Long> heldStock = stock.contents();
        Map<WorkerResourceKey, Long> wirelessStock = wireless == null ? Map.of() : wireless.contents();
        for(var ingredient : required.entrySet()){
            WorkerResourceKey key = ingredient.getKey();
            long held = heldStock.getOrDefault(key, 0L) + wirelessStock.getOrDefault(key, 0L);
            if(held >= ingredient.getValue()) continue;
            long missing = ingredient.getValue() - held;
            WorkerStorageEndpoint source = linked.stream()
                    .filter(endpoint -> endpoint.isWorkerRecipeSource() && endpoint.canExtract(key)
                            && (order.sourceEndpointId() == null || order.sourceEndpointId().equals(endpoint.id()))
                            && endpoint.available(key) > 0L)
                    .findFirst().orElse(null);
            WorkerInventoryEndpoint staging = new WorkerInventoryEndpoint(runtime.profile.workerId(),
                    mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                    stack -> !hasWorkerCargoToken(stack), true);
            if(source != null){
                long amount = Math.min(missing, Math.min(source.available(key), staging.space(key)));
                if(amount > 0L){
                    UUID id = UUID.randomUUID();
                    WorkerTask task = new WorkerTask(id, "Collect crafting ingredient", key, amount,
                            0L, order.task().priority(), true);
                    WorkerWorkOrder transfer = new WorkerWorkOrder(id, task, WorkerWorkOrder.Mode.TRANSFER,
                            source.id(), runtime.profile.workerId(), null, key, amount);
                    if(runtime.queue.prepend(List.of(transfer))){
                        resetRuntime(runtime, "Collecting ingredient for portable craft");
                        setChanged();
                        sendData();
                        return;
                    }
                }
            }
            if(source == null && (!order.id().equals(runtime.portablePrerequisiteOrder)
                    || runtime.portablePrerequisiteRevision != planningInputsRevision)){
                runtime.portablePrerequisiteOrder = order.id();
                runtime.portablePrerequisiteRevision = planningInputsRevision;
                if(queueMissingRecipeInputs(controller, runtime, order, linked, stock, mannequin)) return;
            }
            setPortableStatus(runtime, source == null ? "Waiting for crafting ingredient " + key.id()
                    : "Waiting for inventory space to collect " + key.id());
            return;
        }
        var crafted = com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid.craft(level, plan,
                Map.of(toolKey, mannequin.workerCurios().getStackInSlot(toolSlot).copy()));
        ItemStack result = crafted.output();
        if(result.isEmpty() || !BuiltInRegistries.ITEM.getKey(result.getItem()).equals(plan.result().id())){
            setPortableStatus(runtime, "Equipped tool cannot make the selected recipe");
            return;
        }
        WorkerResourcePacket output = portableCraftPacket(plan, crafted);
        WorkerEndpoint target;
        if(retainsWorkerCraftResult(runtime, order, output.resource(), output.amount())){
            target = new WorkerInventoryEndpoint(runtime.profile.workerId(), mannequin::blockPosition,
                    mannequin.workerInventory(), level.registryAccess(), stack -> !hasWorkerCargoToken(stack), true);
        }else{
            target = endpoints.stream().filter(endpoint -> endpoint != null
                            && (order.destinationEndpointId() == null
                            || order.destinationEndpointId().equals(endpoint.id()))
                            && (order.destinationEndpointId() != null
                            || !(endpoint instanceof WorkerStorageEndpoint storage) || storage.isWorkerManagedStorage())
                            && endpoint.acceptsDelivery() && endpoint.canInsert(output.resource())
                            && endpoint.space(output.resource()) >= output.amount())
                    .min(java.util.Comparator.<WorkerEndpoint>comparingInt(endpoint ->
                            endpoint.insertionPriority(output.resource()))
                            .thenComparingDouble(endpoint -> mannequin.position().distanceToSqr(
                                    Vec3.atCenterOf(endpoint.position())))).orElse(null);
        }
        if(target == null){
            setPortableStatus(runtime, "Waiting for destination space for portable craft");
            return;
        }
        List<Map.Entry<WorkerEndpoint, WorkerResourcePacket>> withdrawn = new ArrayList<>();
        for(var ingredient : required.entrySet()){
            long remaining = ingredient.getValue();
            for(WorkerEndpoint source : wireless == null ? List.<WorkerEndpoint>of(stock)
                    : List.of(stock, wireless)){
                while(remaining > 0L){
                    WorkerResourcePacket packet = source.extract(ingredient.getKey(), remaining, false);
                    if(packet.isEmpty()) break;
                    withdrawn.add(Map.entry(source, packet));
                    remaining -= packet.amount();
                }
                if(remaining == 0L) break;
            }
            if(remaining > 0L){
                refundPortableIngredients(withdrawn, stock, mannequin);
                runtime.status = "Crafting ingredients changed during collection";
                sendData();
                return;
            }
        }
        runtime.assignment = new WorkerDispatcher.WorkAssignment(stock, null, target, output.amount());
        runtime.carried = output;
        runtime.deliveryAmount = output.amount();
        runtime.processingInputAmount = 0L;
        runtime.cargoToProcessor = false;
        if(!syncHeldCargo(runtime, mannequin)){
            runtime.carried = null;
            runtime.cargoToken = null;
            refundPortableIngredients(withdrawn, stock, mannequin);
            resetRuntime(runtime, "Worker inventory is full for crafted output");
            setChanged();
            sendData();
            return;
        }
        List<ItemStack> remainders = crafted.remainders();
        ItemStack updatedTool = !remainders.isEmpty()
                && BuiltInRegistries.ITEM.getKey(remainders.getLast().getItem()).equals(toolKey.id())
                ? remainders.getLast().copy() : ItemStack.EMPTY;
        mannequin.workerCurios().setStackInSlot(toolSlot, updatedTool);
        returnCraftingRemainders(output, mannequin, runtime);
        runtime.recipeBatchCount = 1L;
        runtime.recipeInputIndex = plan.inputs().size();
        runtime.cargoSourceEndpoint = stock.id();
        runtime.cargoProcessorEndpoint = null;
        runtime.cargoTargetEndpoint = target.id();
        if(target.id().equals(runtime.profile.workerId())
                && retainsWorkerCraftResult(runtime, order, output.resource(), output.amount())){
            runtime.queue.completeCurrent();
            clearRecipeProgress(runtime);
            runtime.assignment = null;
            runtime.stage = Stage.IDLE;
            runtime.status = "Crafted intermediate with equipped tool";
        }else{
            mannequin.startWorkerInteraction();
            beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
            runtime.status = "Crafted with equipped tool";
            if(target instanceof WorkerInventoryEndpoint) arrive(runtime, mannequin);
        }
        setChanged();
        sendData();
    }

    private WorkerResourcePacket portableCraftPacket(WorkerRecipePlan plan,
            com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid.Result crafted){
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", crafted.output().saveOptional(level.registryAccess()));
        ListTag remainders = new ListTag();
        int end = crafted.remainders().size();
        if(end > 0 && BuiltInRegistries.ITEM.getKey(crafted.remainders().getLast().getItem())
                .equals(plan.inputs().getLast().resource().id())) end--;
        for(int idx = 0; idx < end; idx++){
            ItemStack stack = crafted.remainders().get(idx);
            if(!stack.isEmpty()) remainders.add(stack.saveOptional(level.registryAccess()));
        }
        payload.put("Remainders", remainders);
        return new WorkerResourcePacket(plan.result(), crafted.output().getCount(), payload);
    }

    private void setPortableStatus(WorkerRuntime runtime, String status){
        if(status.equals(runtime.status)) return;
        runtime.status = status;
        sendData();
    }

    private void refundPortableIngredients(List<Map.Entry<WorkerEndpoint, WorkerResourcePacket>> withdrawn,
                                           WorkerInventoryEndpoint stock, PlayerMannequinEntity mannequin){
        WorkerInventoryEndpoint recovery = new WorkerInventoryEndpoint(stock.id(), mannequin::blockPosition,
                mannequin.workerInventory(), level.registryAccess(), stack -> !hasWorkerCargoToken(stack), true);
        for(int idx = withdrawn.size() - 1; idx >= 0; idx--){
            var entry = withdrawn.get(idx);
            WorkerResourcePacket packet = entry.getValue();
            long returned = entry.getKey().insert(packet, false);
            if(returned < packet.amount()) returned += recovery.insert(packet.remainderAfter(returned), false);
            if(returned < packet.amount()){
                ItemStack stack = ItemStack.parseOptional(level.registryAccess(),
                        packet.payload().getCompound("Stack"));
                if(stack.isEmpty()) stack = new ItemStack(BuiltInRegistries.ITEM.get(packet.resource().id()));
                net.minecraft.world.Containers.dropItemStack(level, mannequin.getX(), mannequin.getY(),
                        mannequin.getZ(), stack.copyWithCount((int)(packet.amount() - returned)));
            }
        }
    }

    // A stored plan can lose its chosen ingredient after other jobs consume it. Before staging
    // any of this recipe, reconsider the output once from live stock so an alternative recipe
    // (for example, smelting raw copper instead of packing nuggets) can take over.
    private boolean switchUnavailableRecipeRoute(AdvancedContraptionControllerBlockEntity controller,
                                                  WorkerRuntime runtime, WorkerWorkOrder order,
                                                  List<? extends WorkerEndpoint> endpoints){
        WorkerRecipePlan plan = order.recipePlan();
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        if(plan == null || input == null || runtime.hasCargo() || order.task().completedAmount() > 0L
                || runtime.recipeInputIndex != 0 || runtime.recipeInputDelivered != 0L
                || runtime.recipeBatchCount != 0L || runtime.processorOutputLedger != null
                || order.id().equals(runtime.unavailableRouteReviewed)) return false;
        boolean source = endpoints.stream().map(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                        ? storage.forSource(plan) : endpoint).anyMatch(endpoint -> endpoint != null
                        && (order.sourceEndpointId() == null || order.sourceEndpointId().equals(endpoint.id())
                        || endpoint instanceof WorkerInventoryEndpoint)
                        && (order.sourceEndpointId() != null || !(endpoint instanceof WorkerStorageEndpoint storage)
                        || storage.isWorkerRecipeSource()) && endpoint.canExtract(input.resource())
                        && endpoint.available(input.resource()) > 0L);
        if(source) return false;
        List<WorkerWorkOrder> revised = controller.retryWorkerRecipe(order,
                workerPlanningStock(runtime.profile.workerId()));
        runtime.unavailableRouteReviewed = order.id();
        WorkerRecipePlan replacement = revised.stream().map(WorkerWorkOrder::recipePlan)
                .filter(Objects::nonNull).reduce((first, second) -> second).orElse(null);
        if(replacement == null || replacement.equals(plan) || !runtime.queue.replaceCurrent(revised)){
            setChanged();
            return false;
        }
        clearRecipeProgress(runtime);
        resetRuntime(runtime, "Switching to available recipe ingredients");
        setChanged();
        sendData();
        return true;
    }

    // Count ingredients already held in the selected machine before collecting missing supplies
    private boolean creditPreloadedRecipeInputs(WorkerRuntime runtime, WorkerWorkOrder order,
                                                List<? extends WorkerEndpoint> endpoints,
                                                PlayerMannequinEntity mannequin){
        WorkerRecipePlan plan = order.recipePlan();
        if(plan == null || plan.operation() != WorkerRecipePlan.Operation.PROCESSING
                || runtime.recipeInputIndex >= plan.inputs().size()) return false;
        WorkerStorageEndpoint processor = endpoints.stream().filter(WorkerStorageEndpoint.class::isInstance)
                .map(WorkerStorageEndpoint.class::cast).map(endpoint -> endpoint.forRecipe(plan))
                .filter(endpoint -> (order.processorEndpointId() == null || order.processorEndpointId().equals(endpoint.id()))
                        && (runtime.recipeProcessorEndpoint == null || runtime.recipeProcessorEndpoint.equals(endpoint.id()))
                        && endpoint.supportsRecipePlan(plan))
                .max(java.util.Comparator.comparingLong(endpoint -> {
                    List<Long> stocked = endpoint.preloadedInputs(plan);
                    long count = 0L;
                    for(int idx = 0; idx < stocked.size(); idx++){
                        count += Math.min(plan.inputs().get(idx).amount(), Math.max(0L, stocked.get(idx)));
                    }
                    return count;
                })).orElse(null);
        if(processor == null) return false;
        List<Long> stocked = processor.preloadedInputs(plan);
        if(stocked.stream().noneMatch(amount -> amount > 0L)) return false;
        if(runtime.recipeBatchCount <= 0L){
            runtime.recipeBatchCount = recipeBatchCount(order, plan, processor, mannequin);
            if(runtime.recipeBatchCount <= 0L) return false;
        }
        stocked = processor.preloadedInputs(plan, runtime.recipeBatchCount);
        runtime.recipeProcessorEndpoint = processor.id();
        if(runtime.recipeInputDelivered > 0L) return false;
        int initial = runtime.recipeInputIndex;
        while(runtime.recipeInputIndex < plan.inputs().size()){
            int idx = runtime.recipeInputIndex;
            long required = plan.inputRemaining(idx, runtime.recipeBatchCount, 0L);
            long credited = idx < stocked.size() ? Math.min(required, Math.max(0L, stocked.get(idx))) : 0L;
            if(credited == 0L) break;
            if(credited < required){
                runtime.recipeInputDelivered = credited;
                break;
            }
            runtime.recipeInputIndex++;
        }
        if(runtime.recipeInputIndex == initial && runtime.recipeInputDelivered == 0L) return false;
        if(runtime.recipeInputIndex < plan.inputs().size()){
            runtime.status = "Using ingredients already loaded in the machine";
            setChanged();
            sendData();
            return false;
        }
        WorkerEndpoint target = endpoints.stream().filter(endpoint -> endpoint != null
                        && !endpoint.id().equals(processor.id())
                        && (order.destinationEndpointId() == null || order.destinationEndpointId().equals(endpoint.id()))
                        && (order.destinationEndpointId() != null
                        || !(endpoint instanceof WorkerStorageEndpoint storage) || storage.isWorkerManagedStorage())
                        && endpoint.acceptsDelivery() && endpoint.canInsert(plan.result())
                        && endpoint.space(plan.result()) > 0L)
                .min(java.util.Comparator.<WorkerEndpoint>comparingInt(endpoint ->
                                endpoint.insertionPriority(plan.result()))
                        .thenComparingDouble(endpoint -> Vec3.atCenterOf(processor.position())
                                .distanceToSqr(Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if(target == null){
            runtime.status = "Waiting for storage for processed output";
            sendData();
            return true;
        }
        if(!processor.selectRecipeResult(plan) || !processor.isolateTransport(plan, order.id())){
            runtime.status = "Waiting for the machine's processing route";
            sendData();
            return true;
        }
        runtime.assignment = new WorkerDispatcher.WorkAssignment(processor, processor, target,
                expectedProcessorOutput(order, runtime.recipeBatchCount, 0L));
        runtime.cargoSourceEndpoint = processor.id();
        runtime.cargoProcessorEndpoint = processor.id();
        runtime.cargoTargetEndpoint = target.id();
        runtime.processorOutputLedger = WorkerOutputLedger.capture(processor.snapshot());
        processor.startRecipe(plan);
        runtime.processorOutputFingerprint = processorContentsFingerprint(processor);
        runtime.processorOutputStableSince = level.getGameTime();
        runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
        runtime.status = "Waiting for selected processed output";
        setChanged();
        sendData();
        return true;
    }

    // Move unreserved items into linked storage when they block worker cargo space
    private boolean queueWorkerInventoryReturn(WorkerRuntime runtime, @Nullable WorkerWorkOrder order,
                                               List<WorkerStorageEndpoint> endpoints,
                                               PlayerMannequinEntity mannequin){
        WorkerInventoryEndpoint stock = new WorkerInventoryEndpoint(runtime.profile.workerId(),
                mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                stack -> !hasWorkerCargoToken(stack), false);
        Set<WorkerResourceKey> needed = new HashSet<>();
        if(order != null){
            if(order.recipePlan() == null) needed.add(order.task().resource());
            else for(WorkerRecipePlan.Input input : order.recipePlan().inputs()) needed.addAll(input.alternatives());
            needed.add(order.outputResource());
        }
        for(WorkerWorkOrder planned : runtime.queue.planned()){
            needed.add(planned.outputResource());
            if(planned.recipePlan() != null)
                for(WorkerRecipePlan.Input input : planned.recipePlan().inputs()) needed.addAll(input.alternatives());
        }
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        for(int pass = 0; pass < 2; pass++){
            Set<WorkerResourceKey> seen = new HashSet<>();
            for(int slot = 0; slot < mannequin.workerInventory().getSlots(); slot++){
                ItemStack stack = mannequin.workerInventory().getStackInSlot(slot);
                if(stack.isEmpty() || hasWorkerCargoToken(stack)) continue;
                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if((pass == 0) == needed.contains(resource)) continue;
                if(!seen.add(resource)) continue;
                WorkerResourcePacket packet = stock.extract(resource, stack.getCount(), true);
                if(packet.isEmpty()) continue;
                List<WorkerEndpoint> targets = new ArrayList<>(endpoints.stream()
                        .filter(endpoint -> endpoint.isWorkerManagedStorage() && endpoint.canInsert(resource))
                        .map(endpoint -> (WorkerEndpoint) endpoint).toList());
                if(wireless != null && wireless.isAvailable() && wireless.canInsert(resource)) targets.add(wireless);
                targets = targets.stream()
                        .sorted(java.util.Comparator.comparingInt(
                                (WorkerEndpoint endpoint) -> endpoint.insertionPriority(resource))
                                .thenComparingDouble(endpoint -> mannequin.position().distanceToSqr(
                                        Vec3.atCenterOf(endpoint.position())))).toList();
                for(WorkerEndpoint target : targets){
                    long amount = packet.amount();
                    if(target.insert(packet, true) < amount) continue;
                    UUID id = UUID.randomUUID();
                    WorkerTask task = new WorkerTask(id, "Stow worker inventory", resource, amount,
                            0L, order == null ? 0 : order.task().priority(), true);
                    WorkerWorkOrder transfer = new WorkerWorkOrder(id, task, WorkerWorkOrder.Mode.TRANSFER,
                            runtime.profile.workerId(), target.id(), null, resource, amount);
                    if(!(order == null ? runtime.queue.enqueue(transfer)
                            : runtime.queue.prepend(List.of(transfer)))) return false;
                    if(order != null && order.recipePlan() != null){
                        CompoundTag progress = new CompoundTag();
                        progress.putInt("RecipeInputIndex", runtime.recipeInputIndex);
                        progress.putLong("RecipeInputDelivered", runtime.recipeInputDelivered);
                        progress.putLong("RecipeBatchCount", runtime.recipeBatchCount);
                        if(runtime.recipeProcessorEndpoint != null)
                            progress.putUUID("RecipeProcessor", runtime.recipeProcessorEndpoint);
                        if(runtime.processorOutputLedger != null)
                            progress.put("ProcessorOutputLedger", runtime.processorOutputLedger.toTag());
                        runtime.suspendedRecipes.put(order.id(), progress);
                        clearRecipeProgress(runtime);
                    }
                    resetRuntime(runtime, "Stowing worker inventory");
                    setChanged();
                    sendData();
                    return true;
                }
            }
        }
        return false;
    }

    // Keep prerequisite batches in the worker instead of requiring an external staging container
    private WorkerInventoryEndpoint recipeInventory(WorkerRuntime runtime, PlayerMannequinEntity mannequin){
        WorkerWorkOrder order = runtime.queue.current();
        boolean intermediate = order != null && (order.mode() == WorkerWorkOrder.Mode.RECLAIM_INPUT
                || order.mode() == WorkerWorkOrder.Mode.TRANSFER
                && runtime.profile.workerId().equals(order.destinationEndpointId())
                || order.recipePlan() != null && runtime.queue.planned().stream()
                .anyMatch(next -> next.recipePlan() == null && next.mode() == WorkerWorkOrder.Mode.TRANSFER
                        && next.task().resource().equals(order.outputResource())
                        || next.mode() == WorkerWorkOrder.Mode.FILL_CONTAINER
                        && next.outputResource().equals(order.outputResource())
                        || next.recipePlan() != null && next.recipePlan().inputs().stream()
                        .anyMatch(input -> input.resource().equals(order.outputResource()))));
        return new WorkerInventoryEndpoint(runtime.profile.workerId(), mannequin::blockPosition,
                mannequin.workerInventory(), level.registryAccess(), stack -> !hasWorkerCargoToken(stack), intermediate,
                stack -> com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.matchesInput(level,
                        order == null ? null : order.recipePlan(), stack));
    }

    // Tools are personal recipe sources, never destinations for ordinary delivery or idle sorting.
    private WorkerInventoryEndpoint toolInventory(UUID workerId, PlayerMannequinEntity mannequin){
        UUID id = UUID.nameUUIDFromBytes((workerId + ":tools").getBytes(StandardCharsets.UTF_8));
        return new WorkerInventoryEndpoint(id, mannequin::blockPosition, mannequin.workerCurios(),
                level.registryAccess(), stack -> true, false);
    }

    private @Nullable WorkerInventoryEndpoint wirelessInventory(PlayerMannequinEntity mannequin){
        if(!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;
        boolean ae2 = net.neoforged.fml.ModList.get() != null
                && net.neoforged.fml.ModList.get().isLoaded("ae2");
        var tools = new com.rieno.gadgetsandgizmos.compat.worker.WorkerToolStorageEndpoint(
                serverLevel, mannequin, ae2);
        return tools.isAvailable() ? tools : null;
    }

    // Resume consumed-input progress after a queued prerequisite has completed
    private static void restoreRecipeProgress(WorkerRuntime runtime){
        WorkerWorkOrder order = runtime.queue.current();
        if(order == null) return;
        CompoundTag progress = runtime.suspendedRecipes.remove(order.id());
        if(progress == null) return;
        runtime.recipeInputIndex = Math.max(0, progress.getInt("RecipeInputIndex"));
        runtime.recipeInputDelivered = Math.max(0L, progress.getLong("RecipeInputDelivered"));
        runtime.recipeBatchCount = Math.max(0L, progress.getLong("RecipeBatchCount"));
        runtime.recipeProcessorEndpoint = progress.hasUUID("RecipeProcessor") ? progress.getUUID("RecipeProcessor") : null;
        runtime.processorOutputLedger = progress.contains("ProcessorOutputLedger", Tag.TAG_COMPOUND)
                ? WorkerOutputLedger.fromTag(progress.getCompound("ProcessorOutputLedger")) : null;
    }

    // Assign the next exact ingredient of a multi-resource recipe plan.
    private @Nullable WorkerDispatcher.WorkAssignment recipePlanAssignment(WorkerWorkOrder order,
                                                                            WorkerRuntime runtime,
                                                                            List<? extends WorkerEndpoint> endpoints,
                                                                            PlayerMannequinEntity mannequin) {
        WorkerRecipePlan plan = order == null ? null : order.recipePlan();
        if (plan == null) return null;
        if (plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING) {
            return internalRecipePlanAssignment(order, runtime, endpoints, mannequin);
        }
        if (!plan.requiresProcessor()) return null;
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        if (input == null) return WorkerDispatcher.WorkAssignment.NONE;
        long remaining = Math.max(0L, input.amount() * Math.max(1L, runtime.recipeBatchCount)
                - runtime.recipeInputDelivered);
        if (remaining <= 0L) return WorkerDispatcher.WorkAssignment.NONE;
        WorkerResourceKey resource = input.resource();
        WorkerEndpoint source = endpoints.stream().map(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                        ? storage.forSource(plan) : endpoint).filter(endpoint -> endpoint != null
                        && (order.sourceEndpointId() == null || order.sourceEndpointId().equals(endpoint.id())
                        || endpoint instanceof WorkerInventoryEndpoint)
                        && (order.sourceEndpointId() != null || !(endpoint instanceof WorkerStorageEndpoint storage)
                        || storage.isWorkerRecipeSource()) && endpoint.canExtract(resource)
                        && endpoint.available(resource) > 0L)
                .min(java.util.Comparator.comparingDouble(endpoint -> mannequin.position().distanceToSqr(
                        Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if (source == null) return WorkerDispatcher.WorkAssignment.NONE;
        WorkerEndpoint processor = endpoints.stream().map(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                        ? storage.forRecipe(plan) : endpoint).filter(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                        && (order.processorEndpointId() == null || order.processorEndpointId().equals(endpoint.id()))
                        && (runtime.recipeProcessorEndpoint == null || runtime.recipeProcessorEndpoint.equals(endpoint.id()))
                        && storage.supportsRecipePlan(plan) && endpoint.canInsert(resource)
                        && endpoint.space(resource) > 0L
                        && (order.processorEndpointId() != null || runtime.recipeProcessorEndpoint != null
                        || !coveredByAreaMachine(storage, endpoints, plan)))
                .min(java.util.Comparator.<WorkerEndpoint>comparingInt(endpoint ->
                        endpoint instanceof WorkerStorageEndpoint storage && storage.isCraftingStation() ? 0 : 1)
                        .thenComparingInt(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                                ? storage.routingPenalty(plan) : 0)
                        .thenComparingInt(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                                && storage.isAreaMachine() ? 0 : 1)
                        .thenComparingDouble(endpoint -> Vec3.atCenterOf(source.position())
                        .distanceToSqr(Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if (processor == null) return WorkerDispatcher.WorkAssignment.NONE;
        if(runtime.recipeBatchCount <= 0L){
            runtime.recipeBatchCount = recipeBatchCount(order, plan, processor, mannequin);
            if(runtime.recipeBatchCount <= 0L) return WorkerDispatcher.WorkAssignment.NONE;
            remaining = input.amount() * runtime.recipeBatchCount;
        }
        WorkerEndpoint target = endpoints.stream().filter(endpoint -> endpoint != null && !endpoint.id().equals(processor.id())
                        && (order.destinationEndpointId() == null || order.destinationEndpointId().equals(endpoint.id()))
                        && (order.destinationEndpointId() != null
                        || !(endpoint instanceof WorkerStorageEndpoint storage) || storage.isWorkerManagedStorage())
                        && endpoint.acceptsDelivery() && endpoint.canInsert(plan.result()) && endpoint.space(plan.result()) > 0L)
                .min(java.util.Comparator.<WorkerEndpoint>comparingInt(endpoint -> endpoint.insertionPriority(plan.result()))
                        .thenComparingDouble(endpoint -> Vec3.atCenterOf(processor.position())
                        .distanceToSqr(Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if (target == null) return WorkerDispatcher.WorkAssignment.NONE;
        long amount = Math.min(remaining, carryingLimit(mannequin, resource));
        amount = Math.min(amount, processor.space(resource));
        return amount <= 0L ? WorkerDispatcher.WorkAssignment.NONE
                : new WorkerDispatcher.WorkAssignment(source, processor, target, amount);
    }

    // Reserve one exact fill at a time so a portable tank can hold more than a worker's fluid cargo.
    private @Nullable WorkerDispatcher.WorkAssignment fluidContainerAssignment(WorkerWorkOrder order,
                                                                                 List<? extends WorkerEndpoint> endpoints,
                                                                                 PlayerMannequinEntity mannequin){
        int millibuckets = (int)order.outputAmount();
        if(millibuckets <= 0 || millibuckets > Integer.MAX_VALUE) return null;
        WorkerEndpoint target = endpoint(endpoints, order.destinationEndpointId());
        if(target == null || !target.isAvailable()) return null;
        WorkerResourceKey container = order.outputResource();
        if(container == null || container.type() != WorkerResourceType.ITEM) return null;
        WorkerEndpoint containerSource = endpoints.stream().filter(source -> {
            if(!usableFluidContainer(source, container, order.task().resource(), millibuckets)) return false;
            ItemStack filled = filledContainer(source.extractMatchingItem(container, 1L,
                            stack -> canFillContainer(stack, order.task().resource(), millibuckets), true),
                    order.task().resource(), millibuckets);
            WorkerResourcePacket packet = itemPacket(filled);
            return !packet.isEmpty() && target.insert(packet, true) >= 1L;
        }).min(java.util.Comparator.comparingDouble(source -> mannequin.position().distanceToSqr(
                Vec3.atCenterOf(source.position())))).orElse(null);
        if(containerSource == null) return null;
        WorkerEndpoint fluidSource = endpoints.stream().filter(source -> source != null
                        && (source instanceof WorkerStorageEndpoint storage && storage.isWorkerRecipeSource()
                        || source instanceof WorkerInventoryEndpoint)
                        && source.canExtract(order.task().resource())
                        && source.available(order.task().resource()) >= millibuckets
                        && source.extract(order.task().resource(), millibuckets, true).amount() == millibuckets)
                .min(java.util.Comparator.comparingDouble(source -> Vec3.atCenterOf(containerSource.position())
                        .distanceToSqr(Vec3.atCenterOf(source.position())))).orElse(null);
        return fluidSource == null ? null : new WorkerDispatcher.WorkAssignment(containerSource,
                fluidSource, target, 1L);
    }

    private boolean usableFluidContainer(WorkerEndpoint source, WorkerResourceKey container,
                                         WorkerResourceKey fluid, int millibuckets){
        if(source == null || !(source instanceof WorkerInventoryEndpoint
                || source instanceof WorkerStorageEndpoint storage && storage.isWorkerRecipeSource())
                || !source.canExtract(container) || source.available(container) < 1L) return false;
        WorkerResourcePacket packet = source.extractMatchingItem(container, 1L,
                stack -> canFillContainer(stack, fluid, millibuckets), true);
        return !filledContainer(packet, fluid, millibuckets).isEmpty();
    }

    private boolean canFillContainer(ItemStack stack, WorkerResourceKey fluidKey, int millibuckets){
        if(stack == null || stack.isEmpty() || fluidKey == null
                || fluidKey.type() != WorkerResourceType.FLUID) return false;
        var fluid = BuiltInRegistries.FLUID.get(fluidKey.id());
        if(fluid == null) return false;
        IFluidHandler handler = WorkerContainerAccess.fluidHandler(stack.copyWithCount(1));
        return handler instanceof IFluidHandlerItem && handler.fill(new FluidStack(fluid, millibuckets),
                IFluidHandler.FluidAction.SIMULATE) == millibuckets;
    }

    private ItemStack filledContainer(WorkerResourcePacket packet, WorkerResourceKey fluidKey, int millibuckets){
        if(packet == null || packet.isEmpty() || packet.resource().type() != WorkerResourceType.ITEM
                || fluidKey == null || fluidKey.type() != WorkerResourceType.FLUID) return ItemStack.EMPTY;
        ItemStack stack = workerItemStack(packet).copyWithCount(1);
        IFluidHandler handler = WorkerContainerAccess.fluidHandler(stack);
        if(!(handler instanceof IFluidHandlerItem container)) return ItemStack.EMPTY;
        var fluid = BuiltInRegistries.FLUID.get(fluidKey.id());
        if(fluid == null || handler.fill(new FluidStack(fluid, millibuckets),
                IFluidHandler.FluidAction.SIMULATE) != millibuckets) return ItemStack.EMPTY;
        if(handler.fill(new FluidStack(fluid, millibuckets), IFluidHandler.FluidAction.EXECUTE)
                != millibuckets) return ItemStack.EMPTY;
        return container.getContainer().copy();
    }

    private WorkerResourcePacket itemPacket(ItemStack stack){
        if(stack == null || stack.isEmpty()) return WorkerResourcePacket.empty(WorkerResourceKey.energy());
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", stack.copyWithCount(1).saveOptional(level.registryAccess()));
        return new WorkerResourcePacket(new WorkerResourceKey(WorkerResourceType.ITEM,
                BuiltInRegistries.ITEM.getKey(stack.getItem())), 1L, payload);
    }

    // Collect the selected physical container before visiting the tank.
    private void collectFluidContainer(WorkerRuntime runtime, WorkerWorkOrder order,
                                       PlayerMannequinEntity mannequin){
        WorkerEndpoint containerSource = runtime.assignment == null ? null : runtime.assignment.source();
        if(containerSource == null || runtime.assignment.processor() == null){
            resetRuntime(runtime, "Container or fluid source is unavailable");
            return;
        }
        int millibuckets = (int)order.outputAmount();
        if(!usableFluidContainer(containerSource, order.outputResource(), order.task().resource(), millibuckets)
                || runtime.assignment.processor().extract(order.task().resource(), millibuckets, true).amount()
                != millibuckets){
            resetRuntime(runtime, "Waiting for empty container or fluid stock");
            return;
        }
        WorkerResourcePacket empty = containerSource.extractMatchingItem(order.outputResource(), 1L,
                stack -> canFillContainer(stack, order.task().resource(), millibuckets), false);
        if(empty.amount() != 1L){
            resetRuntime(runtime, "Empty container was moved before filling");
            return;
        }
        runtime.carried = empty;
        runtime.deliveryAmount = 1L;
        runtime.cargoToProcessor = true;
        if(!syncHeldCargo(runtime, mannequin)){
            runtime.carried = null;
            restoreEmptyFluidContainer(containerSource, empty, mannequin);
            resetRuntime(runtime, "Worker inventory has no space for empty container");
            return;
        }
        mannequin.startWorkerInteraction();
        beginNavigation(runtime, Stage.MOVING_PROCESSOR, mannequin);
        runtime.status = "Carrying container to fluid tank";
        setChanged();
        sendData();
    }

    // Fill only after the worker reaches the tank and the player can accept the finished item.
    private void fillCarriedFluidContainer(WorkerRuntime runtime, WorkerWorkOrder order,
                                           PlayerMannequinEntity mannequin){
        WorkerEndpoint fluidSource = runtime.assignment == null ? null : runtime.assignment.processor();
        if(fluidSource == null || !runtime.hasCargo()){
            resetRuntime(runtime, "Fluid tank or empty container is unavailable");
            return;
        }
        int millibuckets = (int)order.outputAmount();
        WorkerResourcePacket empty = runtime.carried;
        ItemStack filled = filledContainer(empty, order.task().resource(), millibuckets);
        WorkerResourcePacket packet = itemPacket(filled);
        if(packet.isEmpty() || runtime.assignment.target().insert(packet, true) < 1L
                || fluidSource.extract(order.task().resource(), millibuckets, true).amount() != millibuckets){
            runtime.status = "Waiting for fluid or filled-container delivery space";
            return;
        }
        WorkerResourcePacket fluid = fluidSource.extract(order.task().resource(), millibuckets, false);
        if(fluid.amount() != millibuckets){
            if(!fluid.isEmpty()) fluidSource.insert(fluid, false);
            runtime.status = "Waiting for fluid at tank";
            return;
        }
        runtime.carried = packet;
        runtime.deliveryAmount = 1L;
        runtime.processingInputAmount = 0L;
        runtime.cargoToProcessor = false;
        if(!syncHeldCargo(runtime, mannequin)){
            fluidSource.insert(fluid, false);
            runtime.carried = empty;
            syncHeldCargo(runtime, mannequin);
            runtime.status = "Worker inventory has no space for filled container";
            return;
        }
        mannequin.startWorkerInteraction();
        beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
        runtime.status = "Delivering filled fluid container";
        setChanged();
        sendData();
    }

    private void restoreEmptyFluidContainer(WorkerEndpoint source, WorkerResourcePacket empty,
                                            PlayerMannequinEntity mannequin){
        if(source.insert(empty, false) == empty.amount()) return;
        ItemStack stack = workerItemStack(empty);
        for(int slot = 0; slot < mannequin.workerInventory().getSlots() && !stack.isEmpty(); slot++)
            stack = mannequin.workerInventory().insertItem(slot, stack, false);
        if(!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
                mannequin.getX(), mannequin.getY(), mannequin.getZ(), stack);
    }

    // A face link and an area link can name the same machine; retain the bounded route
    private static boolean coveredByAreaMachine(WorkerStorageEndpoint candidate,
                                                List<? extends WorkerEndpoint> endpoints, WorkerRecipePlan plan){
        if(candidate.isAreaMachine()) return false;
        return endpoints.stream().filter(WorkerStorageEndpoint.class::isInstance)
                .map(WorkerStorageEndpoint.class::cast)
                .anyMatch(other -> other.isAreaMachine() && other.position().equals(candidate.position())
                        && java.util.Objects.equals(other.subLevelId(), candidate.subLevelId())
                        && other.supportsRecipePlan(plan));
    }

    // Assign one exact ingredient for a mixed recipe that fits the worker's built-in 2x2 grid.
    private @Nullable WorkerDispatcher.WorkAssignment internalRecipePlanAssignment(WorkerWorkOrder order,
                                                                                    WorkerRuntime runtime,
                                                                                    List<? extends WorkerEndpoint> endpoints,
                                                                                    PlayerMannequinEntity mannequin) {
        WorkerRecipePlan plan = order.recipePlan();
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        if (plan == null || input == null) return WorkerDispatcher.WorkAssignment.NONE;
        long remaining = Math.max(0L, input.amount() * Math.max(1L, runtime.recipeBatchCount)
                - runtime.recipeInputDelivered);
        if (remaining <= 0L) return WorkerDispatcher.WorkAssignment.NONE;
        WorkerResourceKey resource = input.resource();
        WorkerEndpoint source = endpoints.stream().map(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                        ? storage.forSource(plan) : endpoint).filter(endpoint -> endpoint != null
                        && (order.sourceEndpointId() == null || order.sourceEndpointId().equals(endpoint.id())
                        || endpoint instanceof WorkerInventoryEndpoint)
                        && (order.sourceEndpointId() != null || !(endpoint instanceof WorkerStorageEndpoint storage)
                        || storage.isWorkerRecipeSource()) && endpoint.canExtract(resource)
                        && endpoint.available(resource) > 0L)
                .min(java.util.Comparator.comparingDouble(endpoint -> mannequin.position().distanceToSqr(
                        Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if (source == null) return WorkerDispatcher.WorkAssignment.NONE;
        if(runtime.recipeBatchCount <= 0L){
            runtime.recipeBatchCount = recipeBatchCount(order, plan, null, mannequin);
            if(runtime.recipeBatchCount <= 0L) return WorkerDispatcher.WorkAssignment.NONE;
            remaining = input.amount() * runtime.recipeBatchCount;
        }
        WorkerEndpoint target;
        if (retainsWorkerCraftResult(runtime, order, plan.result(), plan.resultAmount())) {
            // The assignment needs an endpoint, but the crafted result remains in the worker inventory.
            target = source;
        } else {
            target = endpoints.stream().filter(endpoint -> endpoint != null
                            && (order.destinationEndpointId() == null || order.destinationEndpointId().equals(endpoint.id()))
                            && (order.destinationEndpointId() != null
                            || !(endpoint instanceof WorkerStorageEndpoint storage) || storage.isWorkerManagedStorage())
                            && endpoint.acceptsDelivery() && endpoint.canInsert(plan.result()) && endpoint.space(plan.result()) > 0L)
                    .min(java.util.Comparator.<WorkerEndpoint>comparingInt(endpoint -> endpoint.insertionPriority(plan.result()))
                            .thenComparingDouble(endpoint -> Vec3.atCenterOf(source.position())
                            .distanceToSqr(Vec3.atCenterOf(endpoint.position())))).orElse(null);
        }
        if (target == null) return WorkerDispatcher.WorkAssignment.NONE;
        long amount = Math.min(remaining, carryingLimit(mannequin, resource));
        return amount <= 0L ? WorkerDispatcher.WorkAssignment.NONE
                : new WorkerDispatcher.WorkAssignment(source, null, target, amount);
    }

    // Apply an exact crafted intermediate directly from the worker inventory to the next 2x2 recipe step.
    private boolean useCarriedWorkerCraftInput(AdvancedContraptionControllerBlockEntity controller,
                                               WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        WorkerWorkOrder order = runtime.queue.current();
        WorkerRecipePlan plan = order == null ? null : order.recipePlan();
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        WorkerResourcePacket carried = runtime.carried;
        if (plan == null || plan.operation() != WorkerRecipePlan.Operation.WORKER_CRAFTING
                || input == null || carried == null || !input.resource().equals(carried.resource())
                || carried.amount() % input.amount() != 0L) return false;
        long batches = runtime.recipeBatchCount > 0L ? runtime.recipeBatchCount
                : carried.amount() / input.amount();
        long remainingBatches = (order.task().remainingAmount() - 1L) / plan.resultAmount() + 1L;
        if(batches <= 0L || batches > remainingBatches
                || plan.inputRemaining(runtime.recipeInputIndex, batches, runtime.recipeInputDelivered)
                != carried.amount()) return false;
        List<WorkerStorageEndpoint> storageEndpoints = endpoints(controller, runtime, true);
        List<WorkerEndpoint> endpoints = new ArrayList<>(storageEndpoints);
        endpoints.add(recipeInventory(runtime, mannequin));
        endpoints.add(toolInventory(runtime.profile.workerId(), mannequin));
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null && wireless.isAvailable()) endpoints.add(wireless);
        WorkerEndpoint playerDestination = playerEndpoint(order.destinationEndpointId());
        if (playerDestination != null) endpoints.add(playerDestination);
        WorkerEndpoint source = endpoint(endpoints, runtime.cargoSourceEndpoint);
        if (source == null && !storageEndpoints.isEmpty()) source = storageEndpoints.getFirst();
        if (source == null) {
            runtime.status = "Holding crafted input until an SCM source is available";
            sendData();
            return true;
        }
        WorkerEndpoint target;
        if (retainsWorkerCraftResult(runtime, order, plan.result(), plan.resultAmount())) {
            target = source;
        } else {
            target = endpoints.stream().filter(candidate -> candidate != null
                            && (order.destinationEndpointId() == null
                            || order.destinationEndpointId().equals(candidate.id()))
                            && (order.destinationEndpointId() != null
                            || !(candidate instanceof WorkerStorageEndpoint storage)
                            || storage.isWorkerManagedStorage())
                            && candidate.acceptsDelivery() && candidate.canInsert(plan.result()) && candidate.space(plan.result()) > 0L)
                    .min(java.util.Comparator.<WorkerEndpoint>comparingInt(candidate -> candidate.insertionPriority(plan.result()))
                            .thenComparingDouble(candidate -> mannequin.position().distanceToSqr(
                            Vec3.atCenterOf(candidate.position())))).orElse(null);
        }
        if (target == null) {
            runtime.status = "Holding crafted input until the recipe result has space";
            sendData();
            return true;
        }
        if (!releasePortableCargo(runtime, mannequin)) {
            runtime.status = "Worker backpack cargo is unavailable";
            sendData();
            return true;
        }
        long delivered = carried.amount();
        runtime.carried = null;
        syncHeldCargo(runtime, mannequin);
        runtime.assignment = new WorkerDispatcher.WorkAssignment(source, null, target, delivered);
        runtime.cargoSourceEndpoint = source.id();
        runtime.cargoProcessorEndpoint = null;
        runtime.cargoTargetEndpoint = target.id();
        runtime.cargoToProcessor = false;
        runtime.recipeBatchCount = batches;
        advanceRecipePlan(runtime, order, mannequin, delivered, null);
        return true;
    }

    // Keep an exact intermediate result with its worker instead of staging it in an unrelated SCM machine.
    private static boolean retainsWorkerCraftResult(WorkerRuntime runtime, WorkerWorkOrder order,
                                                    WorkerResourceKey result, long resultAmount) {
        if (runtime == null || order == null || result == null || resultAmount <= 0L
                || order.task().requestedAmount() != resultAmount || runtime.queue.planned().isEmpty()) return false;
        WorkerWorkOrder next = runtime.queue.planned().getFirst();
        WorkerRecipePlan plan = next.recipePlan();
        if (plan == null || plan.operation() != WorkerRecipePlan.Operation.WORKER_CRAFTING
                || plan.inputs().isEmpty()) return false;
        CompoundTag progress = runtime.suspendedRecipes.get(next.id());
        int idx = progress == null ? 0 : Math.max(0, progress.getInt("RecipeInputIndex"));
        if(idx >= plan.inputs().size()) return false;
        WorkerRecipePlan.Input input = plan.inputs().get(idx);
        long delivered = progress == null ? 0L : Math.max(0L, progress.getLong("RecipeInputDelivered"));
        long batches = progress != null && progress.getLong("RecipeBatchCount") > 0L
                ? progress.getLong("RecipeBatchCount")
                : (next.task().remainingAmount() - 1L) / plan.resultAmount() + 1L;
        return result.equals(input.resource()) && resultAmount == plan.inputRemaining(idx, batches, delivered);
    }

    // Get the ingredient currently being collected for the active plan.
    private static @Nullable WorkerRecipePlan.Input activeRecipeInput(WorkerWorkOrder order,
                                                                       WorkerRuntime runtime) {
        WorkerRecipePlan plan = order == null ? null : order.recipePlan();
        if (plan == null || runtime == null || runtime.recipeInputIndex < 0
                || runtime.recipeInputIndex >= plan.inputs().size()) return null;
        return plan.inputs().get(runtime.recipeInputIndex);
    }

    // Use a stocked item accepted by the same live recipe ingredient when the planned variant is absent
    private WorkerWorkOrder selectTaggedRecipeInput(WorkerRuntime runtime, WorkerWorkOrder order,
                                                     List<? extends WorkerEndpoint> endpoints){
        WorkerRecipePlan plan = order.recipePlan();
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        if(plan == null || input == null || input.resource().type() != WorkerResourceType.ITEM) return order;
        boolean exact = endpoints.stream().map(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                        ? storage.forSource(plan) : endpoint).anyMatch(endpoint -> endpoint != null
                        && (order.sourceEndpointId() == null || order.sourceEndpointId().equals(endpoint.id())
                        || endpoint instanceof WorkerInventoryEndpoint)
                        && (order.sourceEndpointId() != null || !(endpoint instanceof WorkerStorageEndpoint storage)
                        || storage.isWorkerRecipeSource())
                        && endpoint.canExtract(input.resource()) && endpoint.available(input.resource()) > 0L);
        if(exact && supportsSelectedRecipe(order, plan, runtime, endpoints)) return order;
        for(WorkerEndpoint endpoint : endpoints){
            if(order.sourceEndpointId() != null && !order.sourceEndpointId().equals(endpoint.id())
                    && !(endpoint instanceof WorkerInventoryEndpoint)) continue;
            if(order.sourceEndpointId() == null && endpoint instanceof WorkerStorageEndpoint storage
                    && !storage.isWorkerRecipeSource()) continue;
            Map<WorkerResourceKey, Long> contents = endpoint instanceof WorkerStorageEndpoint storage
                    ? storage.snapshot().resources().stream().collect(java.util.stream.Collectors.toMap(
                    amount -> amount.resource(), amount -> amount.amount(), Long::sum))
                    : endpoint instanceof WorkerInventoryEndpoint inventory ? inventory.contents() : Map.of();
            WorkerEndpoint source = endpoint instanceof WorkerStorageEndpoint storage ? storage.forSource(plan) : endpoint;
            for(WorkerResourceKey resource : contents.keySet()){
                if(resource.equals(input.resource()) || !source.canExtract(resource) || source.available(resource) <= 0L) continue;
                WorkerRecipePlan alternate = com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog
                        .substituteInput(level, plan, runtime.recipeInputIndex, resource);
                if(alternate == null || !supportsSelectedRecipe(order, alternate, runtime, endpoints)) continue;
                WorkerWorkOrder updated = order.withRecipePlan(alternate);
                runtime.queue.updateCurrent(updated);
                setChanged();
                return updated;
            }
        }
        return order;
    }

    // Keep a substituted input on a processor that can produce the selected recipe result
    private static boolean supportsSelectedRecipe(WorkerWorkOrder order, WorkerRecipePlan plan,
                                                  WorkerRuntime runtime, List<? extends WorkerEndpoint> endpoints){
        if(!plan.requiresProcessor()) return true;
        return endpoints.stream().filter(WorkerStorageEndpoint.class::isInstance)
                .map(WorkerStorageEndpoint.class::cast)
                .anyMatch(storage -> (order.processorEndpointId() == null || order.processorEndpointId().equals(storage.id()))
                        && (runtime.recipeProcessorEndpoint == null || runtime.recipeProcessorEndpoint.equals(storage.id()))
                        && storage.supportsRecipePlan(plan));
    }

    // Select one visit large enough for the available worker and machine capacity
    private long recipeBatchCount(WorkerWorkOrder order, WorkerRecipePlan plan,
                                  @Nullable WorkerEndpoint processor, PlayerMannequinEntity mannequin){
        Map<WorkerResourceKey, Long> limits = new java.util.LinkedHashMap<>();
        long desired = (Math.max(1L, order.task().remainingAmount()) - 1L) / plan.resultAmount() + 1L;
        List<Long> preloaded = processor instanceof WorkerStorageEndpoint storage
                ? storage.preloadedInputs(plan, desired) : List.of();
        for(int idx = 0; idx < plan.inputs().size(); idx++){
            WorkerRecipePlan.Input input = plan.inputs().get(idx);
            long capacity = carryingLimit(mannequin, input.resource());
            if(processor != null) capacity = Math.min(capacity, processor.space(input.resource()));
            if(idx < preloaded.size()) capacity = Math.min(Long.MAX_VALUE - capacity,
                    Math.max(0L, preloaded.get(idx))) + capacity;
            limits.put(input.resource(), capacity);
        }
        long output = carryingLimit(mannequin, plan.result());
        if(processor != null && plan.result().type() == WorkerResourceType.ITEM){
            ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(plan.result().id()));
            if(result.isEmpty()) return 0L;
            output = Math.min(output, result.getMaxStackSize());
        }
        long batches = plan.batchesFor(order.task().remainingAmount(), limits, output);
        batches = Math.min(batches, com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.maximumToolBatch(plan));
        if(plan.operation() == WorkerRecipePlan.Operation.CRAFTING
                || plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING){
            for(int slot = 0; slot < mannequin.workerCurios().getSlots(); slot++){
                ItemStack tool = mannequin.workerCurios().getStackInSlot(slot);
                if(!tool.isEmpty() && plan.inputs().stream().anyMatch(input -> input.resource().type()
                        == WorkerResourceType.ITEM && tool.is(BuiltInRegistries.ITEM.get(input.resource().id()))))
                    return Math.min(1L, batches);
            }
        }
        return com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.hasChanceResult(level, plan)
                ? Math.min(1L, batches) : batches;
    }

    // Keep a worker source pickup aligned to the selected crafting station's recipe input count.
    private @Nullable WorkerDispatcher.WorkAssignment alignedCraftStationAssignment(WorkerWorkOrder order,
                                                                                     @Nullable WorkerDispatcher.WorkAssignment assignment) {
        if (order.recipePlan() != null || assignment == null || !assignment.assigned() || order.mode() != WorkerWorkOrder.Mode.AUTO_CRAFT
                || !(assignment.processor() instanceof WorkerStorageEndpoint station)
                || !station.isCraftingStation()) return assignment;
        int inputs = station.isStonecutter() ? 1 : craftingInputCount(order, 3);
        if (inputs <= 0) return WorkerDispatcher.WorkAssignment.NONE;
        long amount = assignment.amount() - assignment.amount() % inputs;
        return amount <= 0L ? WorkerDispatcher.WorkAssignment.NONE
                : new WorkerDispatcher.WorkAssignment(assignment.source(), assignment.processor(),
                assignment.target(), amount);
    }

    // Assign a 2x2 worker craft without reserving an unrelated linked machine.
    private @Nullable WorkerDispatcher.WorkAssignment internalCraftAssignment(WorkerWorkOrder order,
                                                                              List<? extends WorkerEndpoint> endpoints,
                                                                              PlayerMannequinEntity mannequin) {
        if (order.mode() != WorkerWorkOrder.Mode.AUTO_CRAFT || order.processorEndpointId() != null
                || internalCraftUnit(order) == null) return null;
        WorkerResourceKey input = order.task().resource();
        WorkerEndpoint source = endpoints.stream()
                .filter(endpoint -> endpoint != null
                        && (order.sourceEndpointId() == null || order.sourceEndpointId().equals(endpoint.id())
                        || endpoint instanceof WorkerInventoryEndpoint)
                        && (order.sourceEndpointId() != null
                        || !(endpoint instanceof WorkerStorageEndpoint storage)
                        || storage.isWorkerRecipeSource())
                        && endpoint.canExtract(input) && endpoint.available(input) > 0L)
                .min(java.util.Comparator.comparingDouble(endpoint -> mannequin.position().distanceToSqr(
                        Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if (source == null) return null;
        WorkerEndpoint target = endpoints.stream()
                .filter(endpoint -> endpoint != null && endpoint != source
                        && (order.destinationEndpointId() == null || order.destinationEndpointId().equals(endpoint.id()))
                        && endpoint.canInsert(order.outputResource()) && endpoint.space(order.outputResource()) > 0L)
                .min(java.util.Comparator.comparingDouble(endpoint -> Vec3.atCenterOf(source.position())
                        .distanceToSqr(Vec3.atCenterOf(endpoint.position())))).orElse(null);
        if (target == null) return null;
        CraftingUnit unit = internalCraftUnit(order);
        if (unit == null) return null;
        long amount = Math.min(order.task().remainingAmount(), carryingLimit(mannequin, input));
        long craftCapacity = target.space(order.outputResource()) / unit.result().getCount();
        if (craftCapacity <= 0L) return null;
        amount = Math.min(amount, craftCapacity * unit.inputCount());
        amount -= amount % unit.inputCount();
        return amount <= 0L ? null : new WorkerDispatcher.WorkAssignment(source, null, target, amount);
    }

    // Resolve SCM endpoints in the worker's own level
    private List<WorkerStorageEndpoint> endpoints(AdvancedContraptionControllerBlockEntity controller,
                                                  WorkerRuntime runtime, boolean includeMachines) {
        UUID workerSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        List<WorkerStorageEndpoint> endpoints = WorkerStorageEndpoint.linked(controller, includeMachines).stream()
                .filter(endpoint -> Objects.equals(workerSubLevelId, endpoint.subLevelId())).toList();
        List<WorkerStorageEndpoint> docks = endpoints.stream().filter(WorkerStorageEndpoint::isDock).toList();
        WorkerWorkOrder order = runtime.queue.current();
        if(docks.isEmpty() || order == null || order.recipePlan() != null
                || order.sourceEndpointId() != null || order.destinationEndpointId() != null) return endpoints;
        boolean unloadingDock = docks.stream().anyMatch(endpoint -> endpoint.available(order.task().resource()) > 0L);
        return endpoints.stream().map(endpoint -> endpoint.isDock()
                ? endpoint.withAccess(unloadingDock, !unloadingDock)
                : endpoint.withAccess(!unloadingDock, unloadingDock)).toList();
    }

    // Begin live ground navigation toward the endpoint selected by the current work stage
    private void beginNavigation(WorkerRuntime runtime, Stage destinationStage, PlayerMannequinEntity mannequin) {
        runtime.navigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                mannequin != null && mannequin.hasWorkerFlight(), noEntryPredicate());
        runtime.idleNavigation = null;
        runtime.idleDestination = null;
        runtime.idlePauseTicks = 0;
        runtime.stage = destinationStage;
    }

    // Move a worker back to the selected Worker Pod and store it after arrival
    private void returnToStation(@Nullable AdvancedContraptionControllerBlockEntity controller,
                                 WorkerRuntime runtime, PlayerMannequinEntity mannequin,
                                 WorkerWorkOrder order) {
        if (!runtime.profile.enabled()) {
            runtime.status = "Worker disabled";
            return;
        }
        WorkerPodBlockEntity targetPod = returnStation(controller, order.returnStationId());
        if (targetPod == null) {
            runtime.status = "Selected Worker Pod is unavailable";
            return;
        }
        if (runtime.hasCargo()) {
            runtime.status = "Deliver or recover carried cargo before returning";
            return;
        }
        if (runtime.housed && targetPod == this) {
            completeStationReturn(controller, runtime, mannequin, targetPod);
            return;
        }
        if (runtime.housed && !deployWorker(runtime, mannequin)) {
            runtime.status = "Waiting for a free block beside the Worker Pod";
            return;
        }
        if (targetPod.atPodEntrance(mannequin.position())) {
            completeStationReturn(controller, runtime, mannequin, targetPod);
            return;
        }
        Vec3 entry = targetPod.podEntryPosition(mannequin.position());
        if (entry == null) {
            runtime.navigation = null;
            runtime.status = "Waiting for a reachable Worker Pod entrance";
            return;
        }
        if (runtime.navigation == null) {
            runtime.navigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                    mannequin.hasWorkerFlight(), noEntryPredicate());
        }
        WorkerPathing.NavigationStep update = runtime.navigation.advance(level, mannequin.position(), entry,
                PATH_BUDGET, MOVEMENT_SPEED);
        if (update.unavailable()) {
            runtime.navigation = null;
            runtime.status = "Waiting for a path to the Worker Pod";
            return;
        }
        Vec3 next = update.position();
        if(noEntryPredicate().test(BlockPos.containing(next))){
            runtime.navigation = null;
            runtime.status = "Waiting for a route around a no-entry area";
            return;
        }
        Vec3 movement = next.subtract(mannequin.position());
        if (movement.lengthSqr() > 1.0E-9D) {
            mannequin.setYRot((float) Math.toDegrees(Math.atan2(-movement.x, movement.z)));
            mannequin.setPos(next);
            mannequin.setDeltaMovement(Vec3.ZERO);
            mannequin.setOnGround(true);
        }
        runtime.stage = Stage.MOVING_POD;
        runtime.status = "Returning to Worker Pod";
        if (update.arrived() || targetPod.atPodEntrance(mannequin.position())) {
            completeStationReturn(controller, runtime, mannequin, targetPod);
        }
    }

    // Get the loaded Worker Pod named by a return order
    private @Nullable WorkerPodBlockEntity returnStation(@Nullable AdvancedContraptionControllerBlockEntity controller,
                                                         @Nullable UUID stationId) {
        if (stationId == null) return null;
        if (podId.equals(stationId)) return this;
        if (controller != null) {
            WorkerPodBlockEntity linked = linkedPods(controller).stream()
                    .filter(pod -> stationId.equals(pod.podId)).findFirst().orElse(null);
            if (linked != null) return linked;
        }
        WeakReference<WorkerPodBlockEntity> reference = ACTIVE_PODS.get(stationId);
        return reference == null ? null : reference.get();
    }

    // Finish a station return and keep the worker inside its selected Worker Pod
    private void completeStationReturn(@Nullable AdvancedContraptionControllerBlockEntity controller,
                                       WorkerRuntime runtime, PlayerMannequinEntity mannequin,
                                       WorkerPodBlockEntity targetPod) {
        runtime.queue.completeCurrent();
        if(runtime.queue.current() == null) runtime.failedAssemblyAttempts = 0L;
        resetRuntime(runtime, "Returned to Worker Pod");
        if (targetPod != this) {
            workers.remove(runtime.profile.workerId());
            targetPod.workers.put(runtime.profile.workerId(), runtime);
            if (controller != null) {
                controller.releaseWorker(podId, runtime.profile.workerId());
                controller.claimWorker(targetPod.podId, runtime.profile.workerId());
            }
        }
        targetPod.houseWorker(runtime, mannequin);
        setChanged();
        sendData();
        if (targetPod != this) {
            targetPod.setChanged();
            targetPod.sendData();
        }
    }

    // Store one worker inside this pod until work requires deployment
    private void houseWorker(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime == null || mannequin == null) return;
        mannequin.setAssignedWorkerPod(podId);
        mannequin.noPhysics = true;
        mannequin.setNoGravity(true);
        mannequin.setHousedInWorkerPod(true);
        mannequin.setDeltaMovement(Vec3.ZERO);
        mannequin.setPos(Vec3.atBottomCenterOf(worldPosition).add(0.0D, POD_INTERIOR_HEIGHT, 0.0D));
        Direction facing = getBlockState().getValue(WorkerPodBlock.FACING);
        mannequin.setYRot((float) Math.toDegrees(Math.atan2(-facing.getStepX(), facing.getStepZ())));
        mannequin.setOnGround(false);
        runtime.housed = true;
        runtime.lastPosition = worldPosition;
        runtime.status = "Housed in Worker Pod";
    }

    // Spawn a housed worker on the first clear horizontal block beside its pod
    private boolean deployWorker(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime == null || mannequin == null) return false;
        mannequin.setHousedInWorkerPod(false);
        for (Direction direction : POD_EXIT_DIRECTIONS) {
            Vec3 position = Vec3.atBottomCenterOf(worldPosition.relative(direction));
            AABB bounds = mannequin.getBoundingBox().move(position.subtract(mannequin.position()));
            if (!level.noCollision(mannequin, bounds) || !level.getEntities(mannequin, bounds).isEmpty()) continue;
            mannequin.noPhysics = false;
            mannequin.setNoGravity(false);
            mannequin.setPos(position);
            mannequin.setDeltaMovement(Vec3.ZERO);
            mannequin.setOnGround(true);
            runtime.housed = false;
            runtime.lastPosition = mannequin.blockPosition();
            runtime.status = "Deployed from Worker Pod";
            return true;
        }
        mannequin.setHousedInWorkerPod(true);
        return false;
    }

    // Get a reachable standing position beside this Worker Pod
    private @Nullable Vec3 podEntryPosition(Vec3 origin) {
        Direction facing = getBlockState().getValue(WorkerPodBlock.FACING);
        return WorkerPathing.reachableInteractionPosition(level, List.of(worldPosition), facing, origin);
    }

    // Check whether a returning worker has reached the open front of this Worker Pod
    private boolean atPodEntrance(Vec3 position) {
        if (position == null) return false;
        Direction facing = getBlockState().getValue(WorkerPodBlock.FACING);
        return position.distanceToSqr(Vec3.atBottomCenterOf(worldPosition.relative(facing)))
                <= POD_RETURN_DISTANCE_SQR;
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
        if (endpoint == null || !endpoint.isAvailable()) {
            resetRuntime(runtime, "Delivery endpoint is unavailable");
            return;
        }
        if(endpoint instanceof WorkerInventoryEndpoint){
            arrive(runtime, mannequin);
            return;
        }
        if(runtime.stage == Stage.MOVING_TARGET && runtime.travel == null
                && endpoint instanceof WorkerPlayerEndpoint destination){
            runtime.travel = WorkerDeliveryTravel.begin(mannequin, destination.player(), 100.0D);
            if(runtime.travel != null){
                runtime.navigation = null;
                setChanged();
                return;
            }
        }
        if (runtime.navigation == null) runtime.navigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                mannequin.hasWorkerFlight(), noEntryPredicate());
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
            String destination = destinationName(runtime.stage);
            resetRuntime(runtime, "Waiting for a live path to " + destination);
            sendData();
            return;
        }
        Vec3 next = update.position();
        if(noEntryPredicate().test(BlockPos.containing(next))){
            runtime.navigation = null;
            runtime.status = "Waiting for a route around a no-entry area";
            return;
        }
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

    // Walk only on open ground outside linked machines and worker areas
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
            UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
            var areas = ContraptionNetworkLinkerData.readAreas(controller.getStoredLinker()).stream()
                    .filter(area -> Objects.equals(area.subLevelId(), subLevelId)).toList();
            Set<BlockPos> linked = new HashSet<>();
            ContraptionNetworkLinkerData.readTargets(controller.getStoredLinker()).stream()
                    .filter(target -> Objects.equals(target.subLevelId(), subLevelId))
                    .forEach(target -> linked.add(target.blockPos()));
            endpoints(controller, runtime, true).forEach(endpoint -> linked.addAll(endpoint.interactionBlocks()));
            java.util.function.Predicate<BlockPos> forbidden = pos -> linked.contains(pos)
                    || linked.contains(pos.below())
                    || areas.stream().anyMatch(area -> area.bounds().contains(pos)
                    || area.bounds().contains(pos.below()))
                    || !level.isLoaded(pos.below())
                    || !net.minecraft.world.level.block.Block.isShapeFullBlock(
                    level.getBlockState(pos.below()).getCollisionShape(level, pos.below()));
            BlockPos origin = mannequin.blockPosition();
            for (int attempt = 0; attempt < 24; attempt++) {
                BlockPos candidate = origin.offset(level.random.nextInt(13) - 6,
                        level.random.nextInt(5) - 2, level.random.nextInt(13) - 6);
                if(forbidden.test(candidate)) continue;
                Vec3 standing = WorkerPathing.standingPosition(level, candidate);
                if (standing == null || standing.distanceToSqr(mannequin.position()) < 4.0D) continue;
                runtime.idleDestination = standing;
                runtime.idleNavigation = WorkerPathing.liveNavigator(MAXIMUM_PATH_RANGE,
                        mannequin.hasWorkerFlight(), forbidden);
                runtime.status = "Idle";
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
        if(noEntryPredicate().test(BlockPos.containing(next))){
            runtime.idleNavigation = null;
            runtime.idleDestination = null;
            return;
        }
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
            if(order.mode() == WorkerWorkOrder.Mode.FILL_CONTAINER){
                collectFluidContainer(runtime, order, mannequin);
                return;
            }
            if(order.mode() == WorkerWorkOrder.Mode.RECLAIM_INPUT){
                runtime.carried = runtime.assignment.source().extract(order.task().resource(),
                        runtime.assignment.amount(), false);
                if(!runtime.hasCargo()){
                    resetRuntime(runtime, "Staged ingredient could not be removed from its machine");
                    return;
                }
                runtime.deliveryAmount = runtime.carried.amount();
                if(!syncHeldCargo(runtime, mannequin)){
                    if(runtime.assignment.source() instanceof WorkerStorageEndpoint staged)
                        staged.withAccess(true, true).insert(runtime.carried, false);
                    runtime.carried = null;
                    resetRuntime(runtime, "No space to hold recovered machine ingredients");
                    return;
                }
                mannequin.startWorkerInteraction();
                beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
                runtime.status = "Holding staged ingredients for the prerequisite";
                sendData();
                return;
            }
            WorkerRecipePlan.Input planInput = activeRecipeInput(order, runtime);
            WorkerResourceKey sourceResource = planInput == null ? order.task().resource() : planInput.resource();
            runtime.carried = runtime.assignment.source().extract(
                    sourceResource, runtime.assignment.amount(), false);
            if (!runtime.hasCargo()) {
                resetRuntime(runtime, "Source no longer has the resource");
                return;
            }
            if(order.recipePlan() != null && runtime.carried.resource().type() == WorkerResourceType.ITEM
                    && (runtime.assignment.source().id()
                    .equals(toolInventory(runtime.profile.workerId(), mannequin).id())
                    || com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.isReusableToolInput(
                    order.recipePlan(), runtime.recipeInputIndex))){
                ItemStack used = ItemStack.parseOptional(level.registryAccess(),
                        runtime.carried.payload().getCompound("Stack"));
                if(!used.isEmpty()) runtime.equippedRecipeTools.put(runtime.carried.resource(),
                        runtime.carried.payload().getCompound("Stack").copy());
            }
            runtime.deliveryAmount = runtime.carried.amount();
            runtime.cargoToProcessor = runtime.assignment.processor() != null;
            runtime.processingInputAmount = runtime.cargoToProcessor ? runtime.deliveryAmount : 0L;
            WorkerRecipePlan plan = order.recipePlan();
            if (plan != null && plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING) {
                prefetchRecipeInputs(runtime, order, mannequin);
                long delivered = runtime.carried.amount();
                runtime.carried = null;
                runtime.cargoToProcessor = false;
                syncHeldCargo(runtime, mannequin);
                mannequin.startWorkerInteraction();
                advanceRecipePlan(runtime, order, mannequin, delivered, null);
                return;
            }
            if (order.mode() == WorkerWorkOrder.Mode.AUTO_CRAFT && runtime.assignment.processor() == null) {
                WorkerResourcePacket input = runtime.carried;
                WorkerResourcePacket crafted = internalCraftResult(order, input.amount());
                if (crafted == null || crafted.isEmpty()) {
                    runtime.assignment.source().insert(input, false);
                    runtime.carried = null;
                    resetRuntime(runtime, "No matching 2x2 worker recipe");
                    return;
                }
                runtime.carried = crafted;
                runtime.deliveryAmount = crafted.amount();
                runtime.processingInputAmount = input.amount();
                runtime.cargoToProcessor = false;
                if (!syncHeldCargo(runtime, mannequin)) {
                    runtime.assignment.source().insert(input, false);
                    runtime.carried = null;
                    runtime.cargoToken = null;
                    resetRuntime(runtime, "Worker inventory is full");
                    setChanged();
                    sendData();
                    return;
                }
                mannequin.startWorkerInteraction();
                beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
                runtime.status = "Crafted with worker inventory";
                setChanged();
                sendData();
                return;
            }
            if (!syncHeldCargo(runtime, mannequin)) {
                runtime.assignment.source().insert(runtime.carried, false);
                runtime.carried = null;
                runtime.cargoToken = null;
                resetRuntime(runtime, "Worker inventory is full");
                setChanged();
                sendData();
                return;
            }
            prefetchRecipeInputs(runtime, order, mannequin);
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
            if(order.mode() == WorkerWorkOrder.Mode.FILL_CONTAINER){
                fillCarriedFluidContainer(runtime, order, mannequin);
                return;
            }
            WorkerRecipePlan plan = order.recipePlan();
            if (plan != null && plan.requiresProcessor()
                    && runtime.assignment.processor() instanceof WorkerStorageEndpoint station
                    && !station.selectRecipeResult(plan)) {
                if(redirectBlockedProcessor(runtime, order, mannequin)) return;
                runtime.status = "Selected processor cannot apply the requested result filter";
                return;
            }
            if(plan != null && plan.requiresProcessor()
                    && runtime.assignment.processor() instanceof WorkerStorageEndpoint station
                    && station.isAreaMachine()
                    && !station.isolateTransport(plan, order.id())){
                if(redirectBlockedProcessor(runtime, order, mannequin)) return;
                runtime.status = "Waiting for the machine's transport route";
                return;
            }
            if (plan != null && plan.requiresProcessor()
                    && plan.operation() == WorkerRecipePlan.Operation.CRAFTING
                    && runtime.assignment.processor() instanceof WorkerStorageEndpoint station
                    && station.isCraftingStation()) {
                long delivered = runtime.carried.amount();
                runtime.carried = null;
                runtime.cargoToProcessor = false;
                syncHeldCargo(runtime, mannequin);
                mannequin.startWorkerInteraction();
                if (advanceRecipePlan(runtime, order, mannequin, delivered, station)) return;
                return;
            }
            if (plan == null && order.mode() == WorkerWorkOrder.Mode.AUTO_CRAFT
                    && runtime.assignment.processor() instanceof WorkerStorageEndpoint station
                    && station.isCraftingStation()) {
                CraftedCargo crafted = craftAtStation(order, runtime.carried, station);
                if (crafted == null || crafted.packet().isEmpty()) {
                    runtime.status = "Selected crafting station cannot make the filtered result";
                    return;
                }
                runtime.carried = crafted.packet();
                runtime.deliveryAmount = crafted.packet().amount();
                runtime.processingInputAmount = crafted.inputAmount();
                runtime.cargoToProcessor = false;
                if (!syncHeldCargo(runtime, mannequin)) {
                    runtime.status = "Worker inventory is full";
                    return;
                }
                mannequin.startWorkerInteraction();
                beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
                runtime.status = "Crafted at " + station.label();
                setChanged();
                sendData();
                return;
            }
            if (plan == null && !prepareSelectedProcessorResult(order, runtime.carried, runtime.assignment.processor())) {
                runtime.status = "Selected result cannot be made from the processor input";
                return;
            }
            if(runtime.processorOutputLedger == null
                    && runtime.assignment.processor() instanceof WorkerStorageEndpoint storage
                    && !storage.isCraftingStation()){
                runtime.processorOutputLedger = WorkerOutputLedger.capture(storage.snapshot());
            }
            long insertable = runtime.assignment.processor().insert(runtime.carried, true);
            if (insertable <= 0L || runtime.carried.resource().type() != WorkerResourceType.ITEM
                    && insertable < runtime.carried.amount()) {
                if(redirectBlockedProcessor(runtime, order, mannequin)) return;
                runtime.status = "Waiting for processor input space";
                return;
            }
            if (!releasePortableCargo(runtime, mannequin)) {
                runtime.status = "Worker backpack cargo is unavailable";
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
            if(runtime.recirculatingRecipe){
                runtime.recirculatingRecipe = false;
                runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
                runtime.status = "Waiting for next assembly pass";
                setChanged();
                sendData();
                return;
            }
            if (plan != null && plan.requiresProcessor()
                    && advanceRecipePlan(runtime, order, mannequin,
                    Math.max(inserted, runtime.processingInputAmount), null)) return;
            if (cancelDeferredOrder(runtime)) {
                setChanged();
                sendData();
                return;
            }
            runtime.processorOutputFingerprint = processorContentsFingerprint(runtime.assignment.processor());
            runtime.processorOutputStableSince = level.getGameTime();
            runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
            runtime.status = "Waiting for processed output";
            setChanged();
            sendData();
            return;
        }
        if(runtime.stage != Stage.MOVING_TARGET || !runtime.hasCargo()) return;
        long requested = order.recipePlan() != null || runtime.assignment.target() instanceof WorkerPlayerEndpoint
                ? Math.min(runtime.deliveryAmount, order.task().remainingAmount()) : runtime.deliveryAmount;
        WorkerResourcePacket offered = runtime.returningSurplus ? runtime.carried
                : WorkerDelivery.requested(runtime.carried, requested - runtime.fulfilledDelivery);
        if(!runtime.returningSurplus && runtime.assignment.target() instanceof WorkerInventoryEndpoint target
                && target.id().equals(runtime.profile.workerId()) && !runtime.cargoUsesPortableStorage
                && offered.amount() == runtime.carried.amount() && cargoStored(runtime, mannequin)){
            releaseStoredCargo(runtime, mannequin);
            runtime.fulfilledDelivery += offered.amount();
            runtime.carried = null;
            syncHeldCargo(runtime, mannequin);
            mannequin.startWorkerInteraction();
            finishDelivery(runtime, order);
            return;
        }
        if(!offered.isEmpty()){
            long accepted = runtime.assignment.target().insert(offered, true);
            if(accepted <= 0L || offered.resource().type() != WorkerResourceType.ITEM && accepted < offered.amount()){
                runtime.status = "Waiting for destination space";
                return;
            }
            offered = WorkerDelivery.requested(offered, accepted);
            if(!releasePortableCargo(runtime, mannequin)){
                runtime.status = "Worker backpack cargo is unavailable";
                return;
            }
            long inserted = runtime.assignment.target().insert(offered, false);
            runtime.carried = runtime.carried.remainderAfter(inserted);
            if(!runtime.returningSurplus) runtime.fulfilledDelivery += inserted;
            syncHeldCargo(runtime, mannequin);
            mannequin.startWorkerInteraction();
            setChanged();
        }
        if(runtime.travel != null && runtime.assignment.target() instanceof WorkerPlayerEndpoint
                && runtime.fulfilledDelivery >= requested) runtime.travel.returnHome();
        if(runtime.hasCargo()){
            if(runtime.returningSurplus || runtime.fulfilledDelivery >= requested){
                routeSurplus(runtime, mannequin);
            }else runtime.status = "Waiting for destination space";
            return;
        }
        runtime.carried = null;
        finishDelivery(runtime, order);
    }

    // Route overproduction to matching stock or the next open SCM container
    private void routeSurplus(WorkerRuntime runtime, PlayerMannequinEntity mannequin){
        runtime.returningSurplus = true;
        if(runtime.travel != null){
            runtime.travel.returnHome();
            runtime.stage = Stage.IDLE;
            setChanged();
            return;
        }
        AdvancedContraptionControllerBlockEntity controller = owningController();
        WorkerEndpoint target = controller == null ? null : WorkerDelivery.storage(
                endpoints(controller, runtime, true), runtime.carried, mannequin.position());
        if(target == null){
            runtime.assignment = null;
            runtime.stage = Stage.IDLE;
            runtime.status = "Holding surplus until storage has space";
            setChanged();
            return;
        }
        WorkerEndpoint source = runtime.assignment == null ? target : runtime.assignment.source();
        runtime.assignment = new WorkerDispatcher.WorkAssignment(source, null, target, runtime.carried.amount());
        runtime.cargoTargetEndpoint = target.id();
        runtime.cargoToProcessor = false;
        beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
        runtime.status = "Returning surplus to storage";
        setChanged();
    }

    // Complete only the requested delivery after every surplus item has been stored
    private void finishDelivery(WorkerRuntime runtime, WorkerWorkOrder order){
        com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(order.id());
        if (cancelDeferredOrder(runtime)) {
            setChanged();
            sendData();
            return;
        }
        long completedAmount = order.recipePlan() != null ? runtime.fulfilledDelivery : order.processing()
                ? completedProcessingInput(order, runtime) : runtime.fulfilledDelivery;
        clearRecipeProgress(runtime);
        WorkerTask completed = order.task().complete(completedAmount);
        if(completedAmount == 0L && order.recipePlan() != null){
            runtime.retryRecipe = true;
            resetRuntime(runtime, "Retrying assembly after " + runtime.failedAssemblyAttempts + " failed attempt(s)");
        }else if (completed.pending()) {
            runtime.queue.updateCurrent(order.withTask(completed));
            resetRuntime(runtime, "Next task queued");
        } else if (order.returnsToStation()) {
            runtime.queue.recordCompleted(order.withTask(completed));
            WorkerTask returnTask = new WorkerTask(order.id(), "Return to Pod", WorkerResourceKey.energy(),
                    1L, 0L, order.task().priority(), true);
            runtime.queue.updateCurrent(WorkerWorkOrder.returnToStation(order.id(), returnTask,
                    order.returnStationId()));
            resetRuntime(runtime, "Returning to Worker Pod");
        } else {
            runtime.queue.updateCurrent(order.withTask(completed));
            runtime.queue.completeCurrent();
            resetRuntime(runtime, runtime.queue.current() == null ? "Task complete" : "Next task queued");
        }
        if(runtime.queue.current() == null) runtime.failedAssemblyAttempts = 0L;
        setChanged();
        sendData();
    }

    // Retry an untouched recipe batch at another compatible machine when its chosen input is blocked
    private boolean redirectBlockedProcessor(WorkerRuntime runtime, WorkerWorkOrder order,
                                             PlayerMannequinEntity mannequin){
        WorkerRecipePlan plan = order.recipePlan();
        if(plan == null || order.processorEndpointId() != null || !runtime.hasCargo()
                || runtime.recipeInputIndex != 0 || runtime.recipeInputDelivered != 0L
                || runtime.carried.amount() != runtime.processingInputAmount
                || runtime.assignment == null || runtime.assignment.processor() == null) return false;
        AdvancedContraptionControllerBlockEntity controller = owningController();
        if(controller == null) return false;
        WorkerEndpoint current = runtime.assignment.processor();
        List<WorkerStorageEndpoint> candidates = endpoints(controller, runtime, true).stream()
                .map(endpoint -> endpoint.forRecipe(plan))
                .filter(endpoint -> !endpoint.position().equals(current.position())
                        && endpoint.supportsRecipePlan(plan)
                        && endpoint.machineSupply(plan, Map.of(),
                        Math.max(1L, runtime.recipeBatchCount)).ready()
                        && endpoint.canInsert(runtime.carried.resource())
                        && endpoint.insert(runtime.carried, true) >= runtime.carried.amount())
                .sorted(java.util.Comparator.comparingInt((WorkerStorageEndpoint endpoint) ->
                                endpoint.routingPenalty(plan))
                        .thenComparingDouble(endpoint -> mannequin.position().distanceToSqr(
                                Vec3.atCenterOf(endpoint.position())))).toList();
        if(candidates.isEmpty()) return false;
        com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(order.id());
        for(WorkerStorageEndpoint candidate : candidates){
            if(!candidate.selectRecipeResult(plan) || !candidate.isolateTransport(plan, order.id())) continue;
            runtime.assignment = new WorkerDispatcher.WorkAssignment(runtime.assignment.source(), candidate,
                    runtime.assignment.target(), runtime.assignment.amount());
            runtime.cargoProcessorEndpoint = candidate.id();
            runtime.recipeProcessorEndpoint = candidate.id();
            runtime.processorOutputLedger = null;
            beginNavigation(runtime, Stage.MOVING_PROCESSOR, mannequin);
            runtime.status = "Rerouting recipe to available processor";
            setChanged();
            sendData();
            return true;
        }
        return false;
    }

    // Avoid partial fluid insertion because the held bucket is an actual recoverable item
    private static boolean canInsertHeldCargo(WorkerEndpoint endpoint, WorkerResourcePacket packet) {
        return endpoint != null && packet != null && endpoint.insert(packet, true) >= packet.amount();
    }

    // Claim matching player-loaded stacks so workers deliver every occupied inventory slot, not only runtime cargo.
    private boolean claimWorkerInventoryCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin,
                                              WorkerResourceKey resource, long maximumAmount) {
        if (runtime == null || mannequin == null || resource == null || resource.type() != WorkerResourceType.ITEM
                || maximumAmount <= 0L) return false;
        IItemHandlerModifiable inventory = mannequin.workerInventory();
        ItemStack template = ItemStack.EMPTY;
        List<Integer> claimedSlots = new ArrayList<>();
        long claimed = 0L;
        for (int slot = 0; slot < inventory.getSlots() && claimed < maximumAmount; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!matchesWorkerInventoryResource(stack, resource) || hasWorkerCargoToken(stack)) continue;
            if (!template.isEmpty() && !ItemStack.isSameItemSameComponents(template, stack)) continue;
            if (claimed + stack.getCount() > maximumAmount) continue;
            if (template.isEmpty()) template = stack.copyWithCount(1);
            claimedSlots.add(slot);
            claimed += stack.getCount();
        }
        if (template.isEmpty() || claimed <= 0L || claimedSlots.isEmpty()) return false;
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", template.saveOptional(level.registryAccess()));
        WorkerResourcePacket packet = new WorkerResourcePacket(resource, claimed, payload);
        if (!canInsertHeldCargo(runtime.assignment == null ? null : runtime.assignment.target(), packet)) return false;
        UUID token = UUID.randomUUID();
        for (int slot : claimedSlots) {
            inventory.setStackInSlot(slot, markCargo(inventory.getStackInSlot(slot).copy(), token));
        }
        runtime.carried = packet;
        runtime.cargoToken = token;
        runtime.cargoUsesPortableStorage = false;
        runtime.deliveryAmount = packet.amount();
        return syncHeldCargo(runtime, mannequin);
    }

    // Check whether one ordinary worker-inventory stack matches the current item delivery.
    private static boolean matchesWorkerInventoryResource(ItemStack stack, WorkerResourceKey resource) {
        return stack != null && !stack.isEmpty() && resource != null && resource.type() == WorkerResourceType.ITEM
                && resource.id().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    // Check whether one inventory stack is already reserved by any active worker cargo route.
    private static boolean hasWorkerCargoToken(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID(WORKER_CARGO_TOKEN_TAG);
    }

    // Advance a recipe plan after the worker delivers one exact input packet.
    private boolean advanceRecipePlan(WorkerRuntime runtime, WorkerWorkOrder order,
                                      PlayerMannequinEntity mannequin, long delivered,
                                      @Nullable WorkerStorageEndpoint craftingStation) {
        WorkerRecipePlan plan = order.recipePlan();
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        if (plan == null || input == null || delivered <= 0L) {
            resetRuntime(runtime, "Recipe ingredient delivery expired");
            return true;
        }
        long required = input.amount() * Math.max(1L, runtime.recipeBatchCount);
        runtime.recipeInputDelivered = Math.min(required, runtime.recipeInputDelivered + delivered);
        if (runtime.recipeInputDelivered < required) {
            resetRuntime(runtime, "Collecting remaining " + input.resource().id());
            setChanged();
            sendData();
            return true;
        }
        runtime.recipeInputIndex++;
        runtime.recipeInputDelivered = 0L;
        if (runtime.recipeInputIndex < plan.inputs().size()) {
            resetRuntime(runtime, "Collecting next recipe ingredient");
            setChanged();
            sendData();
            return true;
        }
        if (plan.operation() == WorkerRecipePlan.Operation.WORKER_CRAFTING
                || plan.operation() == WorkerRecipePlan.Operation.CRAFTING && craftingStation != null) {
            WorkerResourcePacket crafted = plannedCraftResult(plan, Math.max(1L, runtime.recipeBatchCount),
                    runtime, mannequin);
            if (crafted == null || crafted.isEmpty()) {
                if(WorkerWorldActions.handles(plan)){
                    refundWorldInputs(plan, Math.max(1L, runtime.recipeBatchCount), mannequin);
                    clearRecipeProgress(runtime);
                }
                restoreEquippedRecipeTools(runtime, mannequin);
                resetRuntime(runtime, "Selected crafting recipe is unavailable");
                setChanged();
                sendData();
                return true;
            }
            runtime.carried = crafted;
            runtime.deliveryAmount = crafted.amount();
            runtime.processingInputAmount = 0L;
            runtime.cargoToProcessor = false;
            if (!syncHeldCargo(runtime, mannequin)) {
                runtime.cargoToken = null;
                resetRuntime(runtime, "Worker inventory is full");
                setChanged();
                sendData();
                return true;
            }
            if (craftingStation == null
                    && retainsWorkerCraftResult(runtime, order, crafted.resource(), crafted.amount())) {
                runtime.queue.completeCurrent();
                clearRecipeProgress(runtime);
                runtime.assignment = null;
                runtime.stage = Stage.IDLE;
                runtime.status = "Crafted intermediate in worker inventory";
                returnCraftingRemainders(crafted, mannequin, runtime);
                setChanged();
                sendData();
                return true;
            }
            returnCraftingRemainders(crafted, mannequin, runtime);
            mannequin.startWorkerInteraction();
            beginNavigation(runtime, Stage.MOVING_TARGET, mannequin);
            runtime.status = craftingStation == null ? "Crafted with worker inventory"
                    : "Crafted at " + craftingStation.label();
            if(runtime.assignment.target() instanceof WorkerInventoryEndpoint) arrive(runtime, mannequin);
            setChanged();
            sendData();
            return true;
        }
        if(runtime.assignment.processor() instanceof WorkerStorageEndpoint processor) processor.startRecipe(plan);
        runtime.processorOutputFingerprint = processorContentsFingerprint(runtime.assignment.processor());
        runtime.processorOutputStableSince = level.getGameTime();
        runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
        runtime.status = "Waiting for selected processed output";
        setChanged();
        sendData();
        return true;
    }

    // Recreate the selected crafting result after the worker supplied every planned ingredient.
    private @Nullable WorkerResourcePacket plannedCraftResult(WorkerRecipePlan plan, long batches,
                                                              WorkerRuntime runtime,
                                                              PlayerMannequinEntity mannequin) {
        if (plan == null || plan.operation() != WorkerRecipePlan.Operation.CRAFTING
                && plan.operation() != WorkerRecipePlan.Operation.WORKER_CRAFTING) return null;
        Map<WorkerResourceKey, ItemStack> tools = new LinkedHashMap<>();
        runtime.equippedRecipeTools.forEach((resource, tag) -> tools.put(resource,
                ItemStack.parseOptional(level.registryAccess(), tag)));
        com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid.Result crafted;
        if(WorkerWorldActions.handles(plan)){
            try{
                crafted = level instanceof ServerLevel serverLevel
                        ? WorkerWorldActions.execute(serverLevel, mannequin, plan, tools, noEntryPredicate())
                        : com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid.Result.EMPTY;
            }catch(RuntimeException failedInteraction){
                crafted = com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid.Result.EMPTY;
            }
        }else crafted = com.rieno.gadgetsandgizmos.lib.worker.WorkerCraftingGrid.craft(level, plan, tools);
        ItemStack result = crafted.output();
        if(result.isEmpty() || !result.is(BuiltInRegistries.ITEM.get(plan.result().id()))) return null;
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", result.saveOptional(level.registryAccess()));
        ListTag remainders = new ListTag();
        for(long batch = 0L; batch < batches; batch++){
            for(ItemStack stack : crafted.remainders()) if(!stack.isEmpty()) remainders.add(stack.saveOptional(level.registryAccess()));
        }
        payload.put("Remainders", remainders);
        return new WorkerResourcePacket(plan.result(), result.getCount() * batches, payload);
    }

    // An in-world interaction may fail if its work spot changes; return every consumed input.
    private void refundWorldInputs(WorkerRecipePlan plan, long batches, PlayerMannequinEntity mannequin){
        for(int idx = 0; idx < plan.inputs().size(); idx++){
            if(com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.isReusableToolInput(plan, idx))
                continue;
            WorkerRecipePlan.Input input = plan.inputs().get(idx);
            if(input.resource().type() != WorkerResourceType.ITEM) continue;
            long remaining = Math.min(Integer.MAX_VALUE, input.amount() * batches);
            while(remaining > 0L){
                var item = BuiltInRegistries.ITEM.get(input.resource().id());
                ItemStack stack = new ItemStack(item, (int)Math.min(item.getDefaultMaxStackSize(), remaining));
                ItemStack leftover = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(
                        mannequin.workerInventory(), stack, false);
                if(!leftover.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
                        mannequin.getX(), mannequin.getY(), mannequin.getZ(), leftover);
                remaining -= stack.getCount();
            }
        }
    }

    // Return crafting containers to the worker, following vanilla's drop behaviour when its inventory is full
    private void returnCraftingRemainders(WorkerResourcePacket crafted, PlayerMannequinEntity mannequin,
                                          WorkerRuntime runtime){
        ListTag remainders = crafted.payload().getList("Remainders", Tag.TAG_COMPOUND);
        for(int idx = 0; idx < remainders.size(); idx++){
            ItemStack stack = ItemStack.parseOptional(level.registryAccess(), remainders.getCompound(idx));
            ResourceLocation remainderId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if(runtime.equippedRecipeTools.keySet().stream().anyMatch(resource -> resource.id()
                    .equals(remainderId))){
                for(int slot = 0; slot < mannequin.workerCurios().getSlots() && !stack.isEmpty(); slot++)
                    stack = mannequin.workerCurios().insertItem(slot, stack, false);
            }
            for(int slot = 0; slot < mannequin.workerInventory().getSlots() && !stack.isEmpty(); slot++){
                stack = mannequin.workerInventory().insertItem(slot, stack, false);
            }
            if(!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
                    mannequin.position().x, mannequin.position().y, mannequin.position().z, stack);
        }
        runtime.equippedRecipeTools.clear();
    }

    private void restoreEquippedRecipeTools(WorkerRuntime runtime, PlayerMannequinEntity mannequin){
        for(CompoundTag original : runtime.equippedRecipeTools.values()){
            ItemStack stack = ItemStack.parseOptional(level.registryAccess(), original);
            for(int slot = 0; slot < mannequin.workerCurios().getSlots() && !stack.isEmpty(); slot++)
                stack = mannequin.workerCurios().insertItem(slot, stack, false);
            if(!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
                    mannequin.position().x, mannequin.position().y, mannequin.position().z, stack);
        }
        runtime.equippedRecipeTools.clear();
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
        WorkerEndpoint processor = runtime.assignment.processor();
        if(!processor.isAvailable()){
            runtime.status = "Waiting for loaded processor";
            return;
        }
        long requested = expectedProcessorOutput(order, runtime);
        long available = processor.available(order.outputResource());
        long produced = runtime.processorOutputLedger == null ? available
                : runtime.processorOutputLedger.produced(order.outputResource(), available);
        WorkerResourceKey collectedResource = order.outputResource();
        if(produced < requested && order.recipePlan() != null && !runtime.queue.planned().isEmpty()){
            WorkerResourceKey substitute = taggedProcessorOutput(runtime, order, processor, requested,
                    runtime.processorOutputLedger);
            if(substitute != null){
                WorkerEndpoint target = runtime.assignment.target();
                if(!target.canInsert(substitute) || target.space(substitute) < requested){
                    AdvancedContraptionControllerBlockEntity controller = owningController();
                    target = controller == null ? null : WorkerDelivery.storage(endpoints(controller, runtime, true),
                            new WorkerResourcePacket(substitute, requested, new CompoundTag()), mannequin.position());
                }
                if(target == null){
                    runtime.status = "Waiting for storage for tagged recipe result";
                    return;
                }
                runtime.assignment = new WorkerDispatcher.WorkAssignment(runtime.assignment.source(), processor,
                        target, runtime.assignment.amount());
                runtime.cargoTargetEndpoint = target.id();
                collectedResource = substitute;
                available = processor.available(substitute);
                produced = runtime.processorOutputLedger == null ? available
                        : runtime.processorOutputLedger.produced(substitute, available);
            }
        }
        long fingerprint = processorContentsFingerprint(processor);
        long time = level.getGameTime();
        if (fingerprint != runtime.processorOutputFingerprint) {
            runtime.processorOutputFingerprint = fingerprint;
            runtime.processorOutputStableSince = time;
        }
        if(produced < requested && processor instanceof WorkerStorageEndpoint storage){
            WorkerResourcePacket intermediate = storage.recirculatingRecipeResult(order.recipePlan(),
                    runtime.processorOutputLedger);
            if(intermediate != null && !intermediate.isEmpty()){
                runtime.carried = intermediate;
                runtime.recirculatingRecipe = true;
                runtime.cargoToProcessor = true;
                runtime.cargoProcessorEndpoint = processor.id();
                syncHeldCargo(runtime, mannequin);
                beginNavigation(runtime, Stage.MOVING_PROCESSOR, mannequin);
                runtime.status = "Returning assembly for its next pass";
                setChanged();
                sendData();
                return;
            }
            WorkerResourcePacket alternate = storage.alternateRecipeResult(order.recipePlan(),
                    runtime.processorOutputLedger);
            if(alternate != null && !alternate.isEmpty()){
                com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(order.id());
                runtime.failedAssemblyAttempts++;
                runtime.carried = alternate;
                runtime.deliveryAmount = 0L;
                runtime.fulfilledDelivery = 0L;
                runtime.cargoToProcessor = false;
                syncHeldCargo(runtime, mannequin);
                routeSurplus(runtime, mannequin);
                runtime.status = "Storing failed assembly output (" + runtime.failedAssemblyAttempts + " failed)";
                return;
            }
        }
        if (produced >= requested) {
            runtime.carried = processor.extract(collectedResource, requested, false);
            if(!verifiedProcessorExtraction(processor, collectedResource, available, runtime.carried)){
                runtime.carried = null;
                runtime.status = "Processor output extraction was not confirmed";
                return;
            }
        } else if(order.recipePlan() == null && time - runtime.processorOutputStableSince >= PROCESSOR_OUTPUT_STALL_TICKS){
            WorkerResourceKey fallback = produced > 0L ? order.outputResource()
                    : processorAvailableResource(processor);
            if (fallback == null) {
                completeProcessorTask(runtime, order, "Processor output timed out");
                return;
            }
            long before = processor.available(fallback);
            long amount = before;
            if(runtime.processorOutputLedger != null) amount = runtime.processorOutputLedger.produced(fallback, amount);
            if(amount <= 0L){
                completeProcessorTask(runtime, order, "Processor output timed out");
                return;
            }
            runtime.carried = processor.extract(fallback, Math.min(carryingLimit(mannequin, fallback), amount), false);
            if(!verifiedProcessorExtraction(processor, fallback, before, runtime.carried)){
                runtime.carried = null;
                runtime.status = "Processor output extraction was not confirmed";
                return;
            }
            if (runtime.hasCargo() && !runtime.assignment.target().canInsert(runtime.carried.resource())) {
                processor.insert(runtime.carried, false);
                runtime.carried = null;
                completeProcessorTask(runtime, order, "Processor output timed out");
                return;
            }
        } else {
            runtime.status = "Waiting for processed output (" + produced + "/" + requested + ")";
            return;
        }
        if (!runtime.hasCargo()) return;
        com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(order.id());
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

    // A processing adapter must remove its claimed cargo from a live output port
    private static boolean verifiedProcessorExtraction(WorkerEndpoint processor, WorkerResourceKey resource,
                                                       long before, WorkerResourcePacket packet){
        if(packet == null || packet.isEmpty() || !resource.equals(packet.resource())) return false;
        if(!(processor instanceof WorkerStorageEndpoint storage) || !storage.isProcessingMachine()) return true;
        return WorkerOutputLedger.confirmsExtraction(packet, before, processor.available(resource));
    }

    // Load later item ingredients from the same source while the worker is already there
    private void prefetchRecipeInputs(WorkerRuntime runtime, WorkerWorkOrder order,
                                      PlayerMannequinEntity mannequin){
        WorkerRecipePlan plan = order.recipePlan();
        WorkerEndpoint source = runtime.assignment == null ? null : runtime.assignment.source();
        if(source == null || source instanceof WorkerInventoryEndpoint) return;
        WorkerInventoryEndpoint stock = new WorkerInventoryEndpoint(runtime.profile.workerId(),
                mannequin::blockPosition, mannequin.workerInventory(), level.registryAccess(),
                stack -> !hasWorkerCargoToken(stack), true);
        if(plan == null){
            WorkerWorkOrder next = runtime.queue.planned().isEmpty() ? null : runtime.queue.planned().getFirst();
            CompoundTag progress = next == null ? null : runtime.suspendedRecipes.get(next.id());
            if(next == null || next.recipePlan() == null || progress == null
                    || next.sourceEndpointId() != null && !next.sourceEndpointId().equals(source.id())) return;
            int idx = progress.getInt("RecipeInputIndex");
            long batches = progress.getLong("RecipeBatchCount");
            if(idx < 0 || idx >= next.recipePlan().inputs().size() || batches <= 0L) return;
            WorkerRecipePlan.Input input = next.recipePlan().inputs().get(idx);
            if(input.amount() > Long.MAX_VALUE / batches) return;
            prefetchInput(source, stock, input.resource(),
                    input.amount() * batches - progress.getLong("RecipeInputDelivered"));
            return;
        }
        if(runtime.recipeBatchCount <= 0L || runtime.recipeInputIndex + 1 >= plan.inputs().size()) return;
        for(int idx = runtime.recipeInputIndex + 1; idx < plan.inputs().size(); idx++){
            WorkerRecipePlan.Input input = plan.inputs().get(idx);
            if(input.amount() > Long.MAX_VALUE / runtime.recipeBatchCount) continue;
            prefetchInput(source, stock, input.resource(), input.amount() * runtime.recipeBatchCount);
        }
    }

    // Use free backpack slots for ingredients from the same source during an existing collection visit
    private static void prefetchInput(WorkerEndpoint source, WorkerInventoryEndpoint stock,
                                      WorkerResourceKey resource, long requested){
        if(resource.type() != WorkerResourceType.ITEM || !source.canExtract(resource)) return;
        long needed = Math.max(0L, requested - stock.available(resource));
        long amount = Math.min(needed, Math.min(source.available(resource), stock.space(resource)));
        if(amount <= 0L) return;
        WorkerResourcePacket offered = source.extract(resource, amount, true);
        if(offered.isEmpty() || stock.insert(offered, true) < offered.amount()) return;
        WorkerResourcePacket extracted = source.extract(resource, offered.amount(), false);
        if(extracted.isEmpty()) return;
        long inserted = stock.insert(extracted, false);
        if(inserted < extracted.amount()) source.insert(extracted.remainderAfter(inserted), false);
    }

    // Accept a different machine output only when a later recipe accepts it in place of this prerequisite
    private @Nullable WorkerResourceKey taggedProcessorOutput(WorkerRuntime runtime, WorkerWorkOrder order,
                                                               WorkerEndpoint processor, long requested,
                                                               @Nullable WorkerOutputLedger ledger){
        if(!(processor instanceof WorkerStorageEndpoint storage)) return null;
        for(var amount : storage.snapshot().resources()){
            WorkerResourceKey resource = amount.resource();
            if(resource.equals(order.outputResource()) || resource.type() != WorkerResourceType.ITEM
                    || (ledger == null ? processor.available(resource)
                    : ledger.produced(resource, processor.available(resource))) < requested) continue;
            boolean used = false;
            boolean accepted = true;
            for(WorkerWorkOrder next : runtime.queue.planned()){
                WorkerRecipePlan plan = next.recipePlan();
                if(plan == null) continue;
                for(int idx = 0; idx < plan.inputs().size(); idx++){
                    if(!plan.inputs().get(idx).resource().equals(order.outputResource())) continue;
                    used = true;
                    if(com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog
                            .substituteInput(level, plan, idx, resource) == null) accepted = false;
                }
            }
            if(used && accepted) return resource;
        }
        return null;
    }

    // Craft a selected one-input recipe in the worker's built-in 2x2 crafting grid.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable WorkerResourcePacket internalCraftResult(WorkerWorkOrder order, long inputAmount) {
        CraftingUnit unit = internalCraftUnit(order);
        return craftResult(order, inputAmount, unit);
    }

    // Produce one packet from an already selected crafting-grid recipe.
    private @Nullable WorkerResourcePacket craftResult(WorkerWorkOrder order, long inputAmount,
                                                       @Nullable CraftingUnit unit) {
        if (unit == null) return null;
        long crafts = inputAmount / unit.inputCount();
        if (crafts <= 0L) return null;
        long total = Math.min(Integer.MAX_VALUE, unit.result().getCount() * crafts);
        if (total <= 0L) return null;
        ItemStack crafted = unit.result().copyWithCount((int) Math.min(unit.result().getMaxStackSize(), total));
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", crafted.saveOptional(level.registryAccess()));
        return new WorkerResourcePacket(order.outputResource(), total, payload);
    }

    // Craft the carried input at a linked crafting table or stonecutter.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable CraftedCargo craftAtStation(WorkerWorkOrder order, WorkerResourcePacket input,
                                                   WorkerStorageEndpoint station) {
        if (input == null || input.isEmpty() || station == null) return null;
        if (!station.isStonecutter()) {
            CraftingUnit unit = craftingUnit(order, 3);
            WorkerResourcePacket result = craftResult(order, input.amount(), unit);
            if (unit == null || result == null || result.isEmpty()) return null;
            long consumed = input.amount() / unit.inputCount() * unit.inputCount();
            return new CraftedCargo(result, consumed);
        }
        ItemStack ingredient = workerItemStack(input);
        var output = BuiltInRegistries.ITEM.get(order.outputResource().id());
        if (ingredient.isEmpty() || output == null) return null;
        for(RecipeHolder<?> holder : WorkerRecipeCatalog.producingRecipes(level, order.outputResource())){
            if(!(holder.value() instanceof StonecutterRecipe)) continue;
            StonecutterRecipe recipe = (StonecutterRecipe) holder.value();
            if (!recipe.matches(new SingleRecipeInput(ingredient), level)) continue;
            ItemStack result = recipe.assemble(new SingleRecipeInput(ingredient), level.registryAccess());
            if (!result.is(output)) continue;
            long total = Math.min(Integer.MAX_VALUE, result.getCount() * input.amount());
            ItemStack crafted = result.copyWithCount((int) Math.min(result.getMaxStackSize(), total));
            CompoundTag payload = new CompoundTag();
            payload.put("Stack", crafted.saveOptional(level.registryAccess()));
            return new CraftedCargo(new WorkerResourcePacket(order.outputResource(), total, payload), input.amount());
        }
        return null;
    }

    // Resolve a selected recipe which fits the worker's 2x2 grid and uses one input item type.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable CraftingUnit internalCraftUnit(WorkerWorkOrder order) {
        return craftingUnit(order, 2);
    }

    // Resolve a selected same-input recipe up to the grid size of the active craft station.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private @Nullable CraftingUnit craftingUnit(WorkerWorkOrder order, int maximumGridSize) {
        if (order == null || order.task().resource().type() != WorkerResourceType.ITEM
                || order.outputResource().type() != WorkerResourceType.ITEM) return null;
        ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(order.task().resource().id()));
        var expectedOutput = BuiltInRegistries.ITEM.get(order.outputResource().id());
        if (input.isEmpty() || expectedOutput == null) return null;
        for (int size = 1; size <= Math.max(1, Math.min(3, maximumGridSize)); size++) {
            int slots = size * size;
            for (int mask = 1; mask < 1 << slots; mask++) {
                List<ItemStack> stacks = new ArrayList<>(slots);
                for (int slot = 0; slot < slots; slot++) {
                    stacks.add((mask & 1 << slot) == 0 ? ItemStack.EMPTY : input.copy());
                }
                CraftingInput grid = CraftingInput.of(size, size, stacks);
                for(RecipeHolder<?> holder : WorkerRecipeCatalog.producingRecipes(level, order.outputResource())){
                    if(!(holder.value() instanceof CraftingRecipe)) continue;
                    CraftingRecipe recipe = (CraftingRecipe) holder.value();
                    if (!recipe.matches(grid, level)) continue;
                    ItemStack result = recipe.assemble(grid, level.registryAccess());
                    if (result.is(expectedOutput)) return new CraftingUnit(result.copy(), Integer.bitCount(mask));
                }
            }
        }
        return null;
    }

    // Get the exact same-item ingredient count required by a selected crafting grid.
    private int craftingInputCount(WorkerWorkOrder order, int maximumGridSize) {
        CraftingUnit unit = craftingUnit(order, maximumGridSize);
        return unit == null ? 0 : unit.inputCount();
    }

    // Store one selected same-input recipe and the number of ingredients it consumes.
    private record CraftingUnit(ItemStack result, int inputCount) {
    }

    // Store a station recipe result with the source amount it consumed.
    private record CraftedCargo(WorkerResourcePacket packet, long inputAmount) {
    }

    // Select the filtered recipe result only when the carried ingredients can make it
    private boolean prepareSelectedProcessorResult(WorkerWorkOrder order, WorkerResourcePacket packet,
                                                    WorkerEndpoint processor) {
        if (order.outputResource().type() != WorkerResourceType.ITEM
                || packet.resource().type() != WorkerResourceType.ITEM
                || order.outputResource().id().equals(packet.resource().id())) return true;
        var output = BuiltInRegistries.ITEM.get(order.outputResource().id());
        ItemStack input = workerItemStack(packet);
        if (output == null || input.isEmpty()) return false;
        if (order.mode() == WorkerWorkOrder.Mode.AUTO_CRAFT) {
            CraftingInput grid = CraftingInput.of(1, 1, List.of(input));
            return WorkerRecipeCatalog.producingRecipes(level, order.outputResource()).stream()
                    .map(RecipeHolder::value)
                    .filter(CraftingRecipe.class::isInstance)
                    .map(CraftingRecipe.class::cast)
                    .anyMatch(recipe -> recipe.matches(grid, level)
                            && recipe.getResultItem(level.registryAccess()).is(output));
        }
        for(RecipeHolder<?> holder : WorkerRecipeCatalog.producingRecipes(level, order.outputResource())){
            if(!(holder.value() instanceof ProcessingRecipe<?, ?>)) continue;
            ProcessingRecipe<?, ?> recipe = (ProcessingRecipe<?, ?>) holder.value();
            int resultAmount = CreateWorkerRecipes.singleItemOutput(recipe, level, input, output);
            if(resultAmount <= 0) continue;
            WorkerRecipePlan selected = new WorkerRecipePlan(holder.id(), BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()),
                    WorkerRecipePlan.Operation.PROCESSING, List.of(new WorkerRecipePlan.Input(packet.resource(), 1)),
                    order.outputResource(), resultAmount);
            if(processor instanceof WorkerStorageEndpoint storage && !storage.selectRecipeResult(selected)) continue;
            return true;
        }
        return false;
    }

    // Recreate the carried item stack, including any components needed for recipe matching
    private ItemStack workerItemStack(WorkerResourcePacket packet) {
        ItemStack stack = packet.payload().contains("Stack", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                : ItemStack.EMPTY;
        if (stack.isEmpty()) {
            var item = BuiltInRegistries.ITEM.get(packet.resource().id());
            if (item == null) return ItemStack.EMPTY;
            stack = new ItemStack(item);
        }
        int amount = (int) Math.min((long) stack.getMaxStackSize(), Math.max(1L, packet.amount()));
        return stack.copyWithCount(amount);
    }

    // Get a fingerprint for detecting processor inventory changes while a worker waits
    private static long processorContentsFingerprint(WorkerEndpoint endpoint) {
        if (!(endpoint instanceof WorkerStorageEndpoint storage)) return 0L;
        long fingerprint = 1L;
        for (WorkerEndpointSnapshot.ResourceAmount amount : storage.snapshot().resources()) {
            fingerprint = 31L * fingerprint + amount.resource().hashCode();
            fingerprint = 31L * fingerprint + amount.amount();
        }
        return fingerprint;
    }

    // Get the first resource that remains in a processor after its output has stalled
    private static @Nullable WorkerResourceKey processorAvailableResource(WorkerEndpoint endpoint) {
        if (!(endpoint instanceof WorkerStorageEndpoint storage)) return null;
        return storage.snapshot().resources().stream()
                .filter(amount -> amount.amount() > 0L)
                .map(WorkerEndpointSnapshot.ResourceAmount::resource)
                .findFirst().orElse(null);
    }

    // Finish a process whose processor cannot provide any collectible result
    private void completeProcessorTask(WorkerRuntime runtime, WorkerWorkOrder order, String status) {
        com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(order.id());
        WorkerTask completed = order.task().complete(runtime.processingInputAmount);
        if (completed.pending()) {
            runtime.queue.updateCurrent(order.withTask(completed));
            resetRuntime(runtime, "Next task queued");
        } else {
            runtime.queue.updateCurrent(order.withTask(completed));
            runtime.queue.completeCurrent();
            resetRuntime(runtime, runtime.queue.current() == null ? status : "Next task queued");
        }
        setChanged();
        sendData();
    }

    // Scale a configured total output amount to the input currently held by this worker
    private long expectedProcessorOutput(WorkerWorkOrder order, WorkerRuntime runtime) {
        return expectedProcessorOutput(order, runtime.recipeBatchCount, runtime.processingInputAmount);
    }

    private long expectedProcessorOutput(WorkerWorkOrder order, long batchCount, long inputAmount){
        if(order.recipePlan() != null) return order.recipePlan().resultAmount() * Math.max(1L, batchCount);
        long input = Math.max(1L, inputAmount);
        long recipeOutput = recipeOutputAmount(order, input);
        if (recipeOutput > 0L) return recipeOutput;
        if (order.outputAmount() <= 0L) return input;
        long totalInput = Math.max(1L, order.task().requestedAmount());
        if (order.outputAmount() > Long.MAX_VALUE / input) return Long.MAX_VALUE;
        long scaled = order.outputAmount() * input;
        long whole = scaled / totalInput;
        return Math.max(1L, whole + (scaled % totalInput == 0L ? 0L : 1L));
    }

    // Convert a delivered recipe result back to the matching amount of consumed input.
    private long completedProcessingInput(WorkerWorkOrder order, WorkerRuntime runtime) {
        long input = Math.max(1L, runtime.processingInputAmount);
        long totalOutput = recipeOutputAmount(order, input);
        if (totalOutput <= 0L || runtime.deliveryAmount >= totalOutput) return input;
        if (input > Long.MAX_VALUE / runtime.deliveryAmount) return input;
        long scaled = input * runtime.deliveryAmount;
        long completed = Math.max(scaled / totalOutput, (scaled + totalOutput - 1L) / totalOutput);
        return Math.max(1L, Math.min(input, completed));
    }

    // Resolve the actual selected item result count for one delivered processor input.
    private long recipeOutputAmount(WorkerWorkOrder order, long inputAmount) {
        if(order == null || inputAmount <= 0L) return 0L;
        // Every planned visit executes one recipe batch, including fluid and assembly-stage outputs
        if(order.recipePlan() != null) return order.recipePlan().resultAmount();
        if (order.task().resource().type() != WorkerResourceType.ITEM
                || order.outputResource().type() != WorkerResourceType.ITEM) return 0L;
        ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(order.task().resource().id()));
        var output = BuiltInRegistries.ITEM.get(order.outputResource().id());
        if (input.isEmpty() || output == null) return 0L;
        if (order.mode() == WorkerWorkOrder.Mode.AUTO_CRAFT) {
            CraftingUnit unit = internalCraftUnit(order);
            return unit == null ? 0L : safeRecipeOutputAmount(unit.result().getCount(),
                    inputAmount / unit.inputCount());
        }
        if (order.mode() != WorkerWorkOrder.Mode.PROCESS) return 0L;
        for(RecipeHolder<?> holder : WorkerRecipeCatalog.producingRecipes(level, order.outputResource())){
            if(!(holder.value() instanceof ProcessingRecipe<?, ?>)) continue;
            ProcessingRecipe<?, ?> recipe = (ProcessingRecipe<?, ?>) holder.value();
            int resultAmount = CreateWorkerRecipes.singleItemOutput(recipe, level, input, output);
            if(resultAmount > 0) return safeRecipeOutputAmount(resultAmount, inputAmount);
        }
        return 0L;
    }

    // Multiply one recipe result count without overflowing a worker request.
    private static long safeRecipeOutputAmount(int resultCount, long inputAmount) {
        if (resultCount <= 0 || inputAmount <= 0L) return 0L;
        return inputAmount > Long.MAX_VALUE / resultCount ? Long.MAX_VALUE : resultCount * inputAmount;
    }

    // Leave a running machine while the worker performs an independent queued step
    private void parkProcessorWait(WorkerRuntime runtime){
        WorkerWorkOrder order = runtime.queue.current();
        if(order == null || runtime.cargoProcessorEndpoint == null || runtime.hasCargo()) return;
        runtime.waitingProcessors.put(order.id(), processorWaitTag(runtime, order));
        if(!runtime.queue.yieldCurrent(runtime.waitingProcessors.keySet())){
            runtime.waitingProcessors.remove(order.id());
            return;
        }
        resetRuntime(runtime, "Processing in background; next task queued");
        clearRecipeProgress(runtime);
        setChanged();
        sendData();
    }

    private static CompoundTag processorWaitTag(WorkerRuntime runtime, WorkerWorkOrder order){
        CompoundTag wait = new CompoundTag();
        wait.putUUID("Order", order.id());
        if(runtime.cargoSourceEndpoint != null) wait.putUUID("CargoSource", runtime.cargoSourceEndpoint);
        if(runtime.cargoProcessorEndpoint != null) wait.putUUID("CargoProcessor", runtime.cargoProcessorEndpoint);
        if(runtime.cargoTargetEndpoint != null) wait.putUUID("CargoTarget", runtime.cargoTargetEndpoint);
        if(runtime.recipeProcessorEndpoint != null) wait.putUUID("RecipeProcessor", runtime.recipeProcessorEndpoint);
        wait.putLong("DeliveryAmount", runtime.deliveryAmount);
        wait.putLong("ProcessingInputAmount", runtime.processingInputAmount);
        wait.putInt("RecipeInputIndex", runtime.recipeInputIndex);
        wait.putLong("RecipeInputDelivered", runtime.recipeInputDelivered);
        wait.putLong("RecipeBatchCount", runtime.recipeBatchCount);
        wait.putLong("ProcessorOutputFingerprint", runtime.processorOutputFingerprint);
        wait.putLong("ProcessorOutputStableSince", runtime.processorOutputStableSince);
        if(runtime.processorOutputLedger != null) wait.put("ProcessorOutputLedger", runtime.processorOutputLedger.toTag());
        return wait;
    }

    // Interrupt an idle task when a parked machine has produced its requested output
    private void resumeReadyProcessor(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                                      PlayerMannequinEntity mannequin){
        WorkerWorkOrder current = runtime.queue.current();
        if(current == null || runtime.waitingProcessors.isEmpty() || !runtime.profile.enabled()) return;
        boolean activeWait = runtime.stage == Stage.WAITING_PROCESSOR_OUTPUT;
        if(runtime.stage != Stage.IDLE && !activeWait || runtime.hasCargo()) return;
        CompoundTag currentWait = runtime.waitingProcessors.get(current.id());
        List<WorkerEndpoint> available = new ArrayList<>(endpoints(controller, runtime, true));
        available.add(recipeInventory(runtime, mannequin));
        if(currentWait != null && processorOutputReady(runtime, current, currentWait, available)){
            runtime.waitingProcessors.remove(current.id());
            restoreProcessorWait(runtime, currentWait);
            return;
        }
        if(activeWait && currentWait == null){
            currentWait = processorWaitTag(runtime, current);
            if(processorOutputReady(runtime, current, currentWait, available)) return;
        }
        WorkerWorkOrder ready = null;
        for(WorkerWorkOrder order : runtime.queue.planned()){
            CompoundTag wait = runtime.waitingProcessors.get(order.id());
            if(wait == null || !processorOutputReady(runtime, order, wait, available)) continue;
            if(ready == null || order.task().priority() > ready.task().priority()) ready = order;
        }
        if(ready == null){
            if(currentWait != null && !activeWait){
                runtime.waitingProcessors.remove(current.id());
                restoreProcessorWait(runtime, currentWait);
            }
            return;
        }
        if(currentWait != null) runtime.waitingProcessors.put(current.id(), currentWait);
        else if(current.recipePlan() != null && (runtime.recipeBatchCount > 0L || runtime.recipeInputIndex > 0)){
            CompoundTag progress = new CompoundTag();
            progress.putUUID("Order", current.id());
            progress.putInt("RecipeInputIndex", runtime.recipeInputIndex);
            progress.putLong("RecipeInputDelivered", runtime.recipeInputDelivered);
            progress.putLong("RecipeBatchCount", runtime.recipeBatchCount);
            if(runtime.recipeProcessorEndpoint != null) progress.putUUID("RecipeProcessor", runtime.recipeProcessorEndpoint);
            if(runtime.processorOutputLedger != null) progress.put("ProcessorOutputLedger", runtime.processorOutputLedger.toTag());
            runtime.suspendedRecipes.put(current.id(), progress);
        }
        if(!runtime.queue.promote(ready.id())) return;
        resetRuntime(runtime, "Collecting completed processing");
        clearRecipeProgress(runtime);
        restoreProcessorWait(runtime, runtime.waitingProcessors.remove(ready.id()));
        setChanged();
        sendData();
    }

    // Check a parked processor against live machine contents
    private boolean processorOutputReady(WorkerRuntime runtime, WorkerWorkOrder order,
                                         CompoundTag wait, List<WorkerEndpoint> endpoints){
        WorkerEndpoint processor = endpoint(endpoints, wait.hasUUID("CargoProcessor")
                ? wait.getUUID("CargoProcessor") : null);
        if(processor instanceof WorkerStorageEndpoint storage) processor = storage.forRecipe(order.recipePlan());
        if(processor == null || !processor.isAvailable()) return false;
        long expected = expectedProcessorOutput(order, wait.getLong("RecipeBatchCount"),
                wait.getLong("ProcessingInputAmount"));
        WorkerOutputLedger ledger = wait.contains("ProcessorOutputLedger", Tag.TAG_COMPOUND)
                ? WorkerOutputLedger.fromTag(wait.getCompound("ProcessorOutputLedger")) : null;
        long available = processor.available(order.outputResource());
        return (ledger == null ? available : ledger.produced(order.outputResource(), available)) >= expected
                || order.recipePlan() != null
                && (taggedProcessorOutput(runtime, order, processor, expected, ledger) != null
                || processor instanceof WorkerStorageEndpoint storage && storage.hasRecipeOutcome(order.recipePlan(), ledger));
    }

    // Restore the route and batch state before collecting machine output
    private static void restoreProcessorWait(WorkerRuntime runtime, CompoundTag wait){
        if(wait == null) return;
        runtime.cargoSourceEndpoint = wait.hasUUID("CargoSource") ? wait.getUUID("CargoSource") : null;
        runtime.cargoProcessorEndpoint = wait.hasUUID("CargoProcessor") ? wait.getUUID("CargoProcessor") : null;
        runtime.cargoTargetEndpoint = wait.hasUUID("CargoTarget") ? wait.getUUID("CargoTarget") : null;
        runtime.recipeProcessorEndpoint = wait.hasUUID("RecipeProcessor") ? wait.getUUID("RecipeProcessor") : null;
        runtime.deliveryAmount = wait.getLong("DeliveryAmount");
        runtime.processingInputAmount = wait.getLong("ProcessingInputAmount");
        runtime.recipeInputIndex = wait.getInt("RecipeInputIndex");
        runtime.recipeInputDelivered = wait.getLong("RecipeInputDelivered");
        runtime.recipeBatchCount = wait.getLong("RecipeBatchCount");
        runtime.processorOutputFingerprint = wait.getLong("ProcessorOutputFingerprint");
        runtime.processorOutputStableSince = wait.getLong("ProcessorOutputStableSince");
        runtime.processorOutputLedger = wait.contains("ProcessorOutputLedger", Tag.TAG_COMPOUND)
                ? WorkerOutputLedger.fromTag(wait.getCompound("ProcessorOutputLedger")) : null;
        runtime.assignment = null;
        runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
        runtime.status = "Collecting processed output";
    }

    // Restore an interrupted processor wait after the live SCM endpoints load
    private void resumeProcessor(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                                  PlayerMannequinEntity mannequin){
        List<WorkerEndpoint> endpoints = new ArrayList<>(endpoints(controller, runtime, true));
        endpoints.add(recipeInventory(runtime, mannequin));
        endpoints.add(toolInventory(runtime.profile.workerId(), mannequin));
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null && wireless.isAvailable()) endpoints.add(wireless);
        WorkerEndpoint processor = endpoint(endpoints, runtime.cargoProcessorEndpoint);
        if(processor instanceof WorkerStorageEndpoint storage && runtime.queue.current() != null){
            processor = storage.forRecipe(runtime.queue.current().recipePlan());
        }
        WorkerEndpoint target = endpoint(endpoints, runtime.cargoTargetEndpoint);
        if(target == null) target = playerEndpoint(runtime.cargoTargetEndpoint);
        if(processor == null || target == null){
            runtime.status = "Waiting for loaded SCM processor and destination";
            return;
        }
        runtime.assignment = new WorkerDispatcher.WorkAssignment(processor, processor, target,
                Math.max(1L, runtime.deliveryAmount));
        runtime.processorOutputStableSince = level.getGameTime();
    }

    // Rebuild an interrupted delivery from stable SCM endpoint ids
    private void resumeCargo(AdvancedContraptionControllerBlockEntity controller, WorkerRuntime runtime,
                             PlayerMannequinEntity mannequin) {
        if (runtime.cargoSourceEndpoint == null || runtime.cargoTargetEndpoint == null) {
            runtime.status = "Holding cargo until its route is available";
            return;
        }
        List<WorkerEndpoint> endpoints = new ArrayList<>(endpoints(controller, runtime, true));
        endpoints.add(recipeInventory(runtime, mannequin));
        endpoints.add(toolInventory(runtime.profile.workerId(), mannequin));
        WorkerInventoryEndpoint wireless = wirelessInventory(mannequin);
        if(wireless != null && wireless.isAvailable()) endpoints.add(wireless);
        WorkerEndpoint source = endpoint(endpoints, runtime.cargoSourceEndpoint);
        WorkerEndpoint processor = endpoint(endpoints, runtime.cargoProcessorEndpoint);
        if(processor instanceof WorkerStorageEndpoint storage && runtime.queue.current() != null){
            processor = storage.forRecipe(runtime.queue.current().recipePlan());
        }
        WorkerEndpoint target = endpoint(endpoints, runtime.cargoTargetEndpoint);
        if (target == null) target = playerEndpoint(runtime.cargoTargetEndpoint);
        if(source == null && target != null && !runtime.cargoToProcessor) source = target;
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
        if(runtime.tickPosition != null && mannequin.position().distanceToSqr(runtime.tickPosition) > 1.0E-6D){
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
            runtime.cargoUsesPortableStorage = false;
            mannequin.setWorkerCarryProp(ItemStack.EMPTY);
            return true;
        }
        UUID token = runtime.cargoToken == null ? UUID.randomUUID() : runtime.cargoToken;
        if (!cargoStored(runtime, mannequin)) {
            clearStoredCargo(runtime, mannequin);
            runtime.cargoUsesPortableStorage = storePortableCargo(runtime, mannequin, runtime.carried);
            if (!runtime.cargoUsesPortableStorage) {
                List<ItemStack> cargo = cargoStacks(runtime.carried, token);
                List<IItemHandler> handlers = workerCargoHandlers(mannequin);
                if (cargo.isEmpty() || !canStoreCargo(handlers, cargo)) return false;
                storeCargo(handlers, cargo);
            }
        }
        runtime.cargoToken = token;
        mannequin.setWorkerCarryProp(PackageStyles.getDefaultBox());
        return true;
    }

    // Create one recoverable worker-inventory stack for a resource packet.
    private ItemStack cargoStack(@Nullable WorkerResourcePacket packet) {
        if (packet == null || packet.isEmpty()) return ItemStack.EMPTY;
        if (packet.resource().type() == WorkerResourceType.ENERGY) {
            return CTItems.WORKER_ENERGY_BATTERY == null ? ItemStack.EMPTY
                    : WorkerEnergyBatteryItem.charged(CTItems.WORKER_ENERGY_BATTERY.get(), packet.amount());
        }
        if (packet.resource().type() == WorkerResourceType.FLUID
                || packet.resource().type() == WorkerResourceType.FUEL) {
            return fluidCargoStack(packet.resource(), packet.amount());
        }
        ItemStack stack = packet.payload().contains("Stack")
                ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                : new ItemStack(BuiltInRegistries.ITEM.get(packet.resource().id()));
        return stack.isEmpty() ? ItemStack.EMPTY
                : stack.copyWithCount((int) Math.min(stack.getMaxStackSize(), packet.amount()));
    }

    // Split one packet into marked storage stacks so custody remains recoverable and player-visible.
    private List<ItemStack> cargoStacks(WorkerResourcePacket packet, UUID token) {
        if (packet == null || packet.isEmpty() || token == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        long remaining = packet.amount();
        if (packet.resource().type() == WorkerResourceType.ITEM) {
            ItemStack template = cargoStack(packet);
            if (template.isEmpty()) return List.of();
            while (remaining > 0L) {
                int count = (int) Math.min(template.getMaxStackSize(), remaining);
                result.add(markCargo(template.copyWithCount(count), token));
                remaining -= count;
            }
            return result;
        }
        if (packet.resource().type() == WorkerResourceType.ENERGY) {
            if (CTItems.WORKER_ENERGY_BATTERY == null) return List.of();
            while (remaining > 0L) {
                long charge = Math.min(WorkerEnergyBatteryItem.MAX_ENERGY, remaining);
                result.add(markCargo(WorkerEnergyBatteryItem.charged(CTItems.WORKER_ENERGY_BATTERY.get(), charge), token));
                remaining -= charge;
            }
            return result;
        }
        while (remaining > 0L) {
            long amount = Math.min(1000L, remaining);
            ItemStack carrier = fluidCargoStack(packet.resource(), amount);
            if (carrier.isEmpty()) return List.of();
            result.add(markCargo(carrier, token));
            remaining -= amount;
        }
        return result;
    }

    // Create one bucket-sized visible representation for fluid cargo.
    private ItemStack fluidCargoStack(WorkerResourceKey resource, long amount) {
        if (resource == null || amount <= 0L) return ItemStack.EMPTY;
        var fluid = BuiltInRegistries.FLUID.get(resource.id());
        if (fluid == null) return ItemStack.EMPTY;
        if (amount >= 1000L) {
            ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, 1000));
            if (!bucket.isEmpty()) return bucket;
        }
        ItemStack carrier = new ItemStack(Items.BUCKET);
        CompoundTag data = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString(WORKER_FLUID_CARGO_TAG, resource.id().toString());
        data.putLong(WORKER_FLUID_AMOUNT_TAG, amount);
        carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        carrier.set(DataComponents.CUSTOM_NAME, Component.literal(amount + "mB of "
                + fluid.getFluidType().getDescription().getString()));
        return carrier;
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
        if (runtime.cargoUsesPortableStorage) return true;
        List<ItemStack> expected = cargoStacks(runtime.carried, runtime.cargoToken);
        if (expected.isEmpty()) return false;
        List<ItemStack> actual = new ArrayList<>();
        for (IItemHandler inventory : workerCargoHandlers(mannequin)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (hasCargoToken(stack, runtime.cargoToken)) actual.add(stack.copy());
            }
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
    private static boolean canStoreCargo(List<IItemHandler> inventories, List<ItemStack> cargo) {
        if (inventories == null || inventories.isEmpty() || cargo == null || cargo.isEmpty()) return false;
        ItemStack template = cargo.getFirst();
        if (template.isEmpty()) return false;
        long required = cargo.stream().mapToLong(ItemStack::getCount).sum();
        long capacity = 0L;
        for (IItemHandler inventory : inventories) {
            capacity += ItemInventoryCapacity.insertable(inventory, template, required - capacity);
            if(capacity >= required) return true;
        }
        return capacity >= required;
    }

    // Insert cargo only after an all-or-nothing storage simulation succeeds.
    private static void storeCargo(List<IItemHandler> inventories, List<ItemStack> cargo) {
        for (ItemStack next : cargo) {
            ItemStack remainder = next.copy();
            for (IItemHandler inventory : inventories) {
                for (int slot = 0; slot < inventory.getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = inventory.insertItem(slot, remainder, false);
                }
                if (remainder.isEmpty()) break;
            }
        }
    }

    // Remove the marked representation when the endpoint accepts the resource or it is recovered.
    private static void clearStoredCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.cargoToken == null) return;
        for (IItemHandler inventory : workerCargoHandlers(mannequin)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (hasCargoToken(stack, runtime.cargoToken)) inventory.extractItem(slot, stack.getCount(), false);
            }
        }
    }

    // Release retained stacks to normal worker storage after a player recovers some cargo.
    private static void releaseStoredCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime.cargoToken == null) return;
        if (runtime.cargoUsesPortableStorage) return;
        for (IItemHandler inventory : workerCargoHandlers(mannequin)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!hasCargoToken(stack, runtime.cargoToken)) continue;
                CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                data.remove(WORKER_CARGO_TOKEN_TAG);
                if (data.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                if (inventory instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(slot, stack);
            }
        }
        ItemInventoryCapacity.compact(mannequin.workerInventory(), stack -> !hasWorkerCargoToken(stack));
    }

    // Store fluid or FE directly in a worn portable container when it has the matching upgrade.
    private static boolean storePortableCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin,
                                              WorkerResourcePacket packet) {
        if (runtime == null || mannequin == null || packet == null || packet.isEmpty()) return false;
        if (packet.resource().type() == WorkerResourceType.ITEM) return false;
        long remaining = packet.amount();
        if (packet.resource().type() == WorkerResourceType.ENERGY) {
            for (IEnergyStorage storage : workerPortableEnergyStorages(mannequin)) {
                remaining -= storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, remaining), true);
            }
            if (remaining > 0L) return false;
            remaining = packet.amount();
            for (IEnergyStorage storage : workerPortableEnergyStorages(mannequin)) {
                if (remaining <= 0L) break;
                remaining -= storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, remaining), false);
            }
            return remaining == 0L;
        }
        var fluid = BuiltInRegistries.FLUID.get(packet.resource().id());
        if (fluid == null) return false;
        for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
            IFluidHandler storage = portable.fluidHandler();
            if (storage == null) continue;
            remaining -= storage.fill(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, remaining)),
                    IFluidHandler.FluidAction.SIMULATE);
        }
        if (remaining > 0L) return false;
        remaining = packet.amount();
        for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
            if (remaining <= 0L) break;
            IFluidHandler storage = portable.fluidHandler();
            if (storage == null) continue;
            remaining -= storage.fill(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, remaining)),
                    IFluidHandler.FluidAction.EXECUTE);
            portable.persistFluidContainer(storage);
        }
        return remaining == 0L;
    }

    // Release a direct portable cargo payload immediately before a fully simulated delivery.
    private static boolean releasePortableCargo(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        if (runtime == null || !runtime.cargoUsesPortableStorage || !runtime.hasCargo()) return true;
        WorkerResourcePacket packet = runtime.carried;
        long remaining = packet.amount();
        if (packet.resource().type() == WorkerResourceType.ENERGY) {
            for (IEnergyStorage storage : workerPortableEnergyStorages(mannequin)) {
                remaining -= storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, remaining), true);
            }
            if (remaining > 0L) return false;
            remaining = packet.amount();
            for (IEnergyStorage storage : workerPortableEnergyStorages(mannequin)) {
                if (remaining <= 0L) break;
                remaining -= storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, remaining), false);
            }
        } else {
            var fluid = BuiltInRegistries.FLUID.get(packet.resource().id());
            if (fluid == null) return false;
            for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
                IFluidHandler storage = portable.fluidHandler();
                if (storage == null) continue;
                remaining -= storage.drain(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, remaining)),
                        IFluidHandler.FluidAction.SIMULATE).getAmount();
            }
            if (remaining > 0L) return false;
            remaining = packet.amount();
            for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
                if (remaining <= 0L) break;
                IFluidHandler storage = portable.fluidHandler();
                if (storage == null) continue;
                remaining -= storage.drain(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, remaining)),
                        IFluidHandler.FluidAction.EXECUTE).getAmount();
                portable.persistFluidContainer(storage);
            }
        }
        if (remaining > 0L) return false;
        runtime.cargoUsesPortableStorage = false;
        return true;
    }

    // Get direct FE storage supplied by equipped backpack upgrades.
    private static List<IEnergyStorage> workerPortableEnergyStorages(PlayerMannequinEntity mannequin) {
        List<IEnergyStorage> storages = new ArrayList<>();
        for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
            IEnergyStorage storage = portable.energyStorage();
            if (storage != null) storages.add(storage);
        }
        return List.copyOf(storages);
    }

    // Get writable equipment and Curios stacks which may expose portable storage capabilities.
    private static List<WorkerPortableStorage> workerPortableStorage(PlayerMannequinEntity mannequin) {
        if (mannequin == null) return List.of();
        List<WorkerPortableStorage> stacks = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mannequin.getItemBySlot(slot);
            if (!stack.isEmpty()) stacks.add(new WorkerPortableStorage(stack,
                    updated -> mannequin.setItemSlot(slot, updated)));
        }
        for (int slot = 0; slot < mannequin.workerCurios().getSlots(); slot++) {
            ItemStack stack = mannequin.workerCurios().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                int curioSlot = slot;
                stacks.add(new WorkerPortableStorage(stack,
                        updated -> mannequin.workerCurios().setStackInSlot(curioSlot, updated)));
            }
        }
        return List.copyOf(stacks);
    }

    // Get normal worker storage followed by item storage in worn backpacks.
    private static List<IItemHandler> workerCargoHandlers(PlayerMannequinEntity mannequin) {
        if (mannequin == null) return List.of();
        List<IItemHandler> handlers = new ArrayList<>();
        handlers.add(mannequin.workerInventory());
        for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
            IItemHandler handler = portable.itemHandler();
            if (handler != null) handlers.add(handler);
        }
        return List.copyOf(handlers);
    }

    // Retain a writable portable stack so item capabilities can replace their backing container safely.
    private static final class WorkerPortableStorage {
        private ItemStack stack;
        private final Consumer<ItemStack> updater;

        // Initialize one equipment or Curios-backed portable storage stack.
        private WorkerPortableStorage(ItemStack stack, Consumer<ItemStack> updater) {
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            this.updater = updater;
        }

        // Get the item storage exposed by this portable stack.
        private @Nullable IItemHandler itemHandler() {
            if(com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.isPortableCraftingTool(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()))) return null;
            return WorkerContainerAccess.itemHandler(stack);
        }

        // Get the fluid storage exposed by this portable stack.
        private @Nullable IFluidHandler fluidHandler() {
            return WorkerContainerAccess.fluidHandler(stack);
        }

        // Get the FE storage exposed by this portable stack.
        private @Nullable IEnergyStorage energyStorage() {
            return WorkerContainerAccess.energyStorage(stack);
        }

        // Persist an item capability's replacement container back to its original equipment slot.
        private void persistFluidContainer(IFluidHandler storage) {
            if (!(storage instanceof IFluidHandlerItem container) || updater == null) return;
            ItemStack updated = container.getContainer();
            stack = updated == null ? ItemStack.EMPTY : updated.copy();
            updater.accept(stack);
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
        Map<UUID, Map<WorkerResourceKey, Long>> workerStock = new LinkedHashMap<>();
        for(UUID workerId : workers.keySet()) workerStock.put(workerId, workerPlanningStock(workerId));
        boolean endpointsChanged = !next.equals(endpointSnapshots);
        if(!workerStock.equals(planningWorkerStock) || endpointsChanged) planningInputsRevision++;
        planningWorkerStock = Map.copyOf(workerStock);
        if(!endpointsChanged) return;
        endpointSnapshots = next;
        setChanged();
        sendData();
    }

    // Resolve the ACC which most recently bound this remote base station
    private @Nullable AdvancedContraptionControllerBlockEntity owningController() {
        if (level == null) return null;
        if (controllerPos != null) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(level,
                    controllerSubLevelId, controllerPos);
            AdvancedContraptionControllerBlockEntity controller =
                    blockEntity instanceof AdvancedContraptionControllerBlockEntity advancedController
                            ? advancedController : null;
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

    // Name the requested product and current work in terms the player sees in-game.
    private static void applyName(WorkerRuntime runtime, PlayerMannequinEntity mannequin) {
        WorkerWorkOrder order = runtime.queue.current();
        String name = runtime.profile.name();
        if(order != null){
            if(!order.lookingUpRecipe() && "Looking up recipe".equals(runtime.status)) name += " | Looking up recipe";
            WorkerWorkOrder parent = runtime.queue.planned().stream()
                    .filter(next -> runtime.suspendedRecipes.containsKey(next.id()))
                    .findFirst().orElse(null);
            if(parent != null) name += " | " + workDescription(parent);
            name += " | " + workDescription(order);
        }
        if(name.length() > 120) name = name.substring(0, 117) + "...";
        if (!mannequin.hasCustomName()
                || !mannequin.getCustomName().getString().equals(name)) {
            mannequin.setCustomName(Component.literal(name));
            mannequin.setCustomNameVisible(true);
        }
    }

    private static String workDescription(WorkerWorkOrder order){
        if(order.lookingUpRecipe()) return "Looking up recipe for " + resourceName(order.outputResource());
        if(order.mode() == WorkerWorkOrder.Mode.RECLAIM_INPUT) return "Clearing Machine";
        if(order.returningToStation()) return "Returning to Pod";
        if(order.mode() == WorkerWorkOrder.Mode.FILL_CONTAINER)
            return "Filling " + resourceName(order.outputResource()) + " with "
                    + resourceName(order.task().resource());
        if(order.recipePlan() == null) return "Moving " + resourceName(order.task().resource());
        String verb = switch(order.recipePlan().operation()){
            case CRAFTING, WORKER_CRAFTING -> "Crafting";
            case PROCESSING -> switch(order.recipePlan().processorType().getPath()){
                case "smelting" -> "Smelting";
                case "blasting" -> "Blasting";
                case "smoking" -> "Smoking";
                case "pressing" -> "Pressing";
                case "mixing" -> "Mixing";
                case "cutting" -> "Cutting";
                case "crushing" -> "Crushing";
                case "sequenced_assembly" -> "Assembling";
                default -> "Processing";
            };
        };
        return verb + " " + resourceName(order.outputResource());
    }

    private static String resourceName(WorkerResourceKey resource){
        if(resource == null) return "Items";
        if(resource.type() == WorkerResourceType.ITEM){
            var item = BuiltInRegistries.ITEM.get(resource.id());
            if(item != null && item != Items.AIR) return new ItemStack(item).getHoverName().getString();
        }else if(resource.type() == WorkerResourceType.FLUID){
            var fluid = BuiltInRegistries.FLUID.get(resource.id());
            if(fluid != null) return fluid.getFluidType().getDescription().getString();
        }
        String path = resource.id().getPath().replace('_', ' ');
        return path.isEmpty() ? "Items" : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    // Create one remote status snapshot
    private WorkerStatusSnapshot snapshot(WorkerRuntime runtime) {
        PlayerMannequinEntity mannequin = findMannequin(runtime);
        BlockPos position = mannequin == null ? runtime.lastPosition : mannequin.blockPosition();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        return new WorkerStatusSnapshot(runtime.profile.workerId(), runtime.profile.name(),
                runtime.profile.job(), runtime.profile.enabled(), subLevelId, position, runtime.status,
                runtime.skinVariant, runtime.queue.current(), runtime.queue.planned(),
                runtime.queue.completed(), List.copyOf(runtime.waitingProcessors.keySet()),
                runtime.queue.completedWeight(), runtime.queue.completedTotal());
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
                endpoint.interactionFace(), origin, noEntryPredicate());
    }

    private java.util.function.Predicate<BlockPos> noEntryPredicate(){
        List<WorkerArea> forbidden = noEntryBounds();
        return pos -> WorkerNoEntryBoundary.blocks(pos, forbidden);
    }

    private List<WorkerArea> noEntryBounds(){
        if(level == null || worldPosition == null) return noEntryBounds;
        long now = level.getGameTime();
        if(noEntryReadTick == now) return noEntryBounds;
        noEntryReadTick = now;
        AdvancedContraptionControllerBlockEntity controller = owningController();
        if(controller == null) return noEntryBounds;
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        noEntryBounds = ContraptionNetworkLinkerData.readAreas(controller.getStoredLinker()).stream()
                .filter(area -> area.kind() == ContraptionNetworkLinkerData.AreaKind.NO_ENTRY
                        && Objects.equals(area.subLevelId(), subLevelId))
                .map(ContraptionNetworkLinkerData.LinkedArea::bounds).toList();
        return noEntryBounds;
    }

    // Get one worker's maximum transferable amount.
    private static long carryingLimit(PlayerMannequinEntity mannequin, WorkerResourceKey resource) {
        if(resource == null) return 0L;
        WorkerCarryCapacity capacity = new WorkerCarryCapacity(
                resource.type() == WorkerResourceType.ITEM ? workerCargoItemSpace(mannequin, resource) : 0L,
                resource.type() == WorkerResourceType.FLUID || resource.type() == WorkerResourceType.FUEL
                        ? workerCargoFluidSpace(mannequin, resource) : 0L,
                resource.type() == WorkerResourceType.ENERGY ? workerCargoEnergySpace(mannequin) : 0L);
        return capacity.amountFor(resource.type());
    }

    // Get item capacity from normal worker storage and worn portable item storage.
    private static long workerCargoItemSpace(PlayerMannequinEntity mannequin, WorkerResourceKey resource) {
        var item = BuiltInRegistries.ITEM.get(resource.id());
        if(item == null) return 0L;
        ItemStack template = markCargo(new ItemStack(item), UUID.randomUUID());
        long capacity = 0L;
        for (IItemHandler inventory : workerCargoHandlers(mannequin)) {
            capacity += ItemInventoryCapacity.insertable(inventory, template, Long.MAX_VALUE - capacity);
        }
        return capacity;
    }

    // Get fluid capacity from every empty worker cargo slot and worn portable storage.
    private static long workerCargoFluidSpace(PlayerMannequinEntity mannequin, WorkerResourceKey resource) {
        var fluid = BuiltInRegistries.FLUID.get(resource.id());
        if (fluid == null) return 1000L;
        long portableCapacity = 0L;
        for (WorkerPortableStorage portable : workerPortableStorage(mannequin)) {
            IFluidHandler storage = portable.fluidHandler();
            if (storage == null) continue;
            portableCapacity += storage.fill(new FluidStack(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
        }
        long inventoryCapacity = 0L;
        ItemStack carrier = new ItemStack(Items.BUCKET);
        for (IItemHandler inventory : workerCargoHandlers(mannequin)) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (inventory.getStackInSlot(slot).isEmpty()
                        && inventory.insertItem(slot, carrier, true).isEmpty()) inventoryCapacity += 1000L;
            }
        }
        return Math.max(1000L, Math.max(portableCapacity, inventoryCapacity));
    }

    // Get FE capacity from every empty worker cargo slot and worn portable storage.
    private static long workerCargoEnergySpace(PlayerMannequinEntity mannequin) {
        long portableCapacity = 0L;
        for (IEnergyStorage storage : workerPortableEnergyStorages(mannequin)) {
            portableCapacity += storage.receiveEnergy(Integer.MAX_VALUE, true);
        }
        long inventoryCapacity = 0L;
        if (CTItems.WORKER_ENERGY_BATTERY != null) {
            ItemStack battery = WorkerEnergyBatteryItem.charged(CTItems.WORKER_ENERGY_BATTERY.get(),
                    WorkerEnergyBatteryItem.MAX_ENERGY);
            for (IItemHandler inventory : workerCargoHandlers(mannequin)) {
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    if (inventory.getStackInSlot(slot).isEmpty()
                            && inventory.insertItem(slot, battery, true).isEmpty()) {
                        inventoryCapacity += WorkerEnergyBatteryItem.MAX_ENERGY;
                    }
                }
            }
        }
        return Math.max(WorkerEnergyBatteryItem.MAX_ENERGY, Math.max(portableCapacity, inventoryCapacity));
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

    // Explain why the current selected recipe input or processor cannot be assigned.
    private static String unavailableRecipeAssignmentStatus(WorkerWorkOrder order, WorkerRuntime runtime,
                                                            List<? extends WorkerEndpoint> endpoints) {
        WorkerRecipePlan plan = order == null ? null : order.recipePlan();
        WorkerRecipePlan.Input input = activeRecipeInput(order, runtime);
        if (plan == null || input == null) return "Selected recipe plan is unavailable";
        boolean source = endpoints.stream().anyMatch(endpoint -> endpoint.canExtract(input.resource())
                && endpoint.available(input.resource()) > 0L);
        if (!source) return "No SCM source has recipe input " + input.resource().id();
        boolean processor = !plan.requiresProcessor() || endpoints.stream().anyMatch(endpoint -> endpoint instanceof WorkerStorageEndpoint storage
                && storage.supportsRecipePlan(plan) && endpoint.canInsert(input.resource())
                && endpoint.space(input.resource()) > 0L);
        if (!processor) return "No SCM processor can run " + plan.processorType();
        boolean target = endpoints.stream().anyMatch(endpoint -> endpoint.canInsert(plan.result())
                && endpoint.space(plan.result()) > 0L);
        return target ? "No matching recipe route is available"
                : "No SCM destination has space for recipe result " + plan.result().id();
    }

    // Clear completed or cancelled recipe progress before assigning another plan.
    private static void clearRecipeProgress(WorkerRuntime runtime) {
        runtime.recipeInputIndex = 0;
        runtime.recipeInputDelivered = 0L;
        runtime.recipeBatchCount = 0L;
        runtime.processorOutputLedger = null;
        runtime.recipeProcessorEndpoint = null;
        runtime.retryRecipe = false;
        runtime.recirculatingRecipe = false;
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
            runtime.fulfilledDelivery = 0L;
            runtime.returningSurplus = false;
            runtime.processingInputAmount = 0L;
            runtime.processorOutputFingerprint = 0L;
            runtime.processorOutputStableSince = 0L;
        }
        runtime.status = resolved;
    }

    // Clear a cancelled order after its already-extracted cargo is safe.
    private boolean cancelDeferredOrder(WorkerRuntime runtime) {
        if (runtime.cancelAfterDelivery == null || runtime.hasCargo()) return false;
        UUID orderId = runtime.cancelAfterDelivery;
        com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.releaseOwner(orderId);
        runtime.cancelAfterDelivery = null;
        runtime.queue.cancel(orderId);
        runtime.suspendedRecipes.remove(orderId);
        runtime.waitingProcessors.remove(orderId);
        resetRuntime(runtime, "Task cancelled");
        return true;
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
        if(!clientPacket) discoveryTicks = DISCOVERY_INTERVAL;
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
        private final Map<UUID, CompoundTag> suspendedRecipes = new LinkedHashMap<>();
        private final Map<UUID, CompoundTag> waitingProcessors = new LinkedHashMap<>();
        private @Nullable WorkerWorkOrder configuration;
        private @Nullable WorkerDispatcher.WorkAssignment assignment;
        private @Nullable WorkerResourcePacket carried;
        private @Nullable UUID cargoToken;
        private @Nullable UUID cancelAfterDelivery;
        private @Nullable UUID cargoSourceEndpoint;
        private @Nullable UUID cargoProcessorEndpoint;
        private @Nullable UUID cargoTargetEndpoint;
        private boolean cargoToProcessor;
        private boolean recirculatingRecipe;
        private boolean cargoUsesPortableStorage;
        private long deliveryAmount;
        private long fulfilledDelivery;
        private boolean returningSurplus;
        private @Nullable UUID recipeProcessorEndpoint;
        private boolean retryRecipe;
        private @Nullable UUID unavailableRouteReviewed;
        private @Nullable UUID portablePrerequisiteOrder;
        private long portablePrerequisiteRevision = -1L;
        private long failedAssemblyAttempts;
        private @Nullable WorkerDeliveryTravel travel;
        private long processingInputAmount;
        private int recipeInputIndex;
        private long recipeInputDelivered;
        private long recipeBatchCount;
        private final Map<WorkerResourceKey, CompoundTag> equippedRecipeTools = new LinkedHashMap<>();
        private long processorOutputFingerprint;
        private long processorOutputStableSince;
        private @Nullable WorkerOutputLedger processorOutputLedger;
        private @Nullable WorkerPathing.LiveNavigator navigation;
        private @Nullable WorkerPathing.LiveNavigator idleNavigation;
        private @Nullable Vec3 idleDestination;
        private int idlePauseTicks;
        private boolean housed;
        private Stage stage = Stage.IDLE;
        private String status = "Idle";
        private BlockPos lastPosition = BlockPos.ZERO;
        private @Nullable Vec3 tickPosition;
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
            ListTag suspended = new ListTag();
            suspendedRecipes.values().forEach(progress -> suspended.add(progress.copy()));
            tag.put("SuspendedRecipes", suspended);
            ListTag waiting = new ListTag();
            waitingProcessors.values().forEach(progress -> waiting.add(progress.copy()));
            tag.put("WaitingProcessors", waiting);
            if (configuration != null) tag.put("Configuration", configuration.toTag());
            if (hasCargo()) tag.put("Cargo", carried.toTag());
            if (cargoToken != null) tag.putUUID("CargoToken", cargoToken);
            if (cancelAfterDelivery != null) tag.putUUID("CancelAfterDelivery", cancelAfterDelivery);
            if (cargoSourceEndpoint != null) tag.putUUID("CargoSource", cargoSourceEndpoint);
            if (cargoProcessorEndpoint != null) tag.putUUID("CargoProcessor", cargoProcessorEndpoint);
            if (cargoTargetEndpoint != null) tag.putUUID("CargoTarget", cargoTargetEndpoint);
            tag.putBoolean("CargoToProcessor", cargoToProcessor);
            tag.putBoolean("RecirculatingRecipe", recirculatingRecipe);
            tag.putBoolean("CargoUsesPortableStorage", cargoUsesPortableStorage);
            tag.putLong("DeliveryAmount", deliveryAmount);
            tag.putLong("FulfilledDelivery", fulfilledDelivery);
            tag.putBoolean("ReturningSurplus", returningSurplus);
            if(recipeProcessorEndpoint != null) tag.putUUID("RecipeProcessor", recipeProcessorEndpoint);
            tag.putBoolean("RetryRecipe", retryRecipe);
            if(unavailableRouteReviewed != null) tag.putUUID("UnavailableRouteReviewed", unavailableRouteReviewed);
            tag.putLong("FailedAssemblyAttempts", failedAssemblyAttempts);
            tag.putBoolean("WaitingProcessor", stage == Stage.WAITING_PROCESSOR_OUTPUT);
            if(travel != null) tag.put("DeliveryTravel", travel.toTag());
            tag.putLong("ProcessingInputAmount", processingInputAmount);
            tag.putInt("RecipeInputIndex", recipeInputIndex);
            tag.putLong("RecipeInputDelivered", recipeInputDelivered);
            tag.putLong("RecipeBatchCount", recipeBatchCount);
            ListTag equippedTools = new ListTag();
            equippedRecipeTools.forEach((resource, stack) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("Item", resource.id().toString());
                entry.put("Stack", stack.copy());
                equippedTools.add(entry);
            });
            tag.put("EquippedRecipeTools", equippedTools);
            tag.putLong("ProcessorOutputFingerprint", processorOutputFingerprint);
            tag.putLong("ProcessorOutputStableSince", processorOutputStableSince);
            if(processorOutputLedger != null) tag.put("ProcessorOutputLedger", processorOutputLedger.toTag());
            tag.putBoolean("Housed", housed);
            tag.putString("Status", status);
            tag.putLong("LastPosition", lastPosition.asLong());
            tag.putString("SkinVariant", skinVariant);
            return tag;
        }

        // Deserialize one worker runtime
        private static WorkerRuntime fromTag(CompoundTag tag) {
            WorkerRuntime runtime = new WorkerRuntime(WorkerProfile.fromTag(tag.getCompound("Profile")));
            runtime.queue = WorkerTaskQueue.fromTag(tag.getCompound("Queue"));
            for(Tag val : tag.getList("SuspendedRecipes", Tag.TAG_COMPOUND)){
                CompoundTag progress = (CompoundTag) val;
                if(progress.hasUUID("Order") && runtime.queue.contains(progress.getUUID("Order"))){
                    runtime.suspendedRecipes.put(progress.getUUID("Order"), progress.copy());
                }
            }
            for(Tag val : tag.getList("WaitingProcessors", Tag.TAG_COMPOUND)){
                CompoundTag wait = (CompoundTag) val;
                if(wait.hasUUID("Order") && runtime.queue.contains(wait.getUUID("Order"))){
                    runtime.waitingProcessors.put(wait.getUUID("Order"), wait.copy());
                }
            }
            runtime.configuration = tag.contains("Configuration", Tag.TAG_COMPOUND)
                    ? WorkerWorkOrder.fromTag(tag.getCompound("Configuration")) : runtime.queue.current();
            runtime.carried = tag.contains("Cargo", Tag.TAG_COMPOUND)
                    ? WorkerResourcePacket.fromTag(tag.getCompound("Cargo")) : null;
            if (runtime.carried != null && runtime.carried.isEmpty()) runtime.carried = null;
            runtime.cargoToken = tag.hasUUID("CargoToken") ? tag.getUUID("CargoToken") : null;
            runtime.cancelAfterDelivery = tag.hasUUID("CancelAfterDelivery")
                    ? tag.getUUID("CancelAfterDelivery") : null;
            runtime.cargoSourceEndpoint = tag.hasUUID("CargoSource") ? tag.getUUID("CargoSource") : null;
            runtime.cargoProcessorEndpoint = tag.hasUUID("CargoProcessor") ? tag.getUUID("CargoProcessor") : null;
            runtime.cargoTargetEndpoint = tag.hasUUID("CargoTarget") ? tag.getUUID("CargoTarget") : null;
            runtime.cargoToProcessor = tag.getBoolean("CargoToProcessor");
            runtime.recirculatingRecipe = tag.getBoolean("RecirculatingRecipe");
            runtime.cargoUsesPortableStorage = tag.getBoolean("CargoUsesPortableStorage");
            runtime.deliveryAmount = Math.max(0L, tag.getLong("DeliveryAmount"));
            runtime.fulfilledDelivery = Math.max(0L, tag.getLong("FulfilledDelivery"));
            runtime.returningSurplus = tag.getBoolean("ReturningSurplus");
            runtime.recipeProcessorEndpoint = tag.hasUUID("RecipeProcessor") ? tag.getUUID("RecipeProcessor") : null;
            runtime.retryRecipe = tag.getBoolean("RetryRecipe");
            runtime.unavailableRouteReviewed = tag.hasUUID("UnavailableRouteReviewed")
                    ? tag.getUUID("UnavailableRouteReviewed") : null;
            runtime.failedAssemblyAttempts = Math.max(0L, tag.getLong("FailedAssemblyAttempts"));
            if(tag.getBoolean("WaitingProcessor")) runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
            runtime.travel = tag.contains("DeliveryTravel") ? WorkerDeliveryTravel.fromTag(tag.getCompound("DeliveryTravel")) : null;
            runtime.processingInputAmount = Math.max(0L, tag.getLong("ProcessingInputAmount"));
            runtime.recipeInputIndex = Math.max(0, tag.getInt("RecipeInputIndex"));
            runtime.recipeInputDelivered = Math.max(0L, tag.getLong("RecipeInputDelivered"));
            runtime.recipeBatchCount = Math.max(0L, tag.getLong("RecipeBatchCount"));
            for(Tag value : tag.getList("EquippedRecipeTools", Tag.TAG_COMPOUND)){
                CompoundTag entry = (CompoundTag)value;
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("Item"));
                if(id != null && entry.contains("Stack", Tag.TAG_COMPOUND))
                    runtime.equippedRecipeTools.put(new WorkerResourceKey(WorkerResourceType.ITEM, id),
                            entry.getCompound("Stack").copy());
            }
            WorkerWorkOrder order = runtime.queue.current();
            if(!runtime.hasCargo() && order != null && order.recipePlan() != null
                    && order.recipePlan().requiresProcessor() && !order.recipePlan().inputs().isEmpty()
                    && runtime.recipeInputIndex >= order.recipePlan().inputs().size()){
                runtime.stage = Stage.WAITING_PROCESSOR_OUTPUT;
            }
            runtime.processorOutputFingerprint = tag.getLong("ProcessorOutputFingerprint");
            runtime.processorOutputStableSince = Math.max(0L, tag.getLong("ProcessorOutputStableSince"));
            runtime.processorOutputLedger = tag.contains("ProcessorOutputLedger", Tag.TAG_COMPOUND)
                    ? WorkerOutputLedger.fromTag(tag.getCompound("ProcessorOutputLedger")) : null;
            runtime.housed = tag.getBoolean("Housed");
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
            runtime.recirculatingRecipe = tag.getBoolean("RecirculatingRecipe");
            runtime.cargoUsesPortableStorage = tag.getBoolean("CargoUsesPortableStorage");
            runtime.deliveryAmount = tag.getLong("DeliveryAmount");
            runtime.processingInputAmount = tag.getLong("ProcessingInputAmount");
            runtime.recipeInputIndex = Math.max(0, tag.getInt("RecipeInputIndex"));
            runtime.recipeInputDelivered = Math.max(0L, tag.getLong("RecipeInputDelivered"));
            runtime.recipeBatchCount = Math.max(0L, tag.getLong("RecipeBatchCount"));
            runtime.processorOutputFingerprint = tag.getLong("ProcessorOutputFingerprint");
            runtime.processorOutputStableSince = tag.getLong("ProcessorOutputStableSince");
            runtime.processorOutputLedger = tag.contains("ProcessorOutputLedger", Tag.TAG_COMPOUND)
                    ? WorkerOutputLedger.fromTag(tag.getCompound("ProcessorOutputLedger")) : null;
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
        MOVING_TARGET,
        MOVING_POD;

        // Check whether this stage is actively navigating toward an endpoint
        private boolean moving() {
            return this == MOVING_SOURCE || this == MOVING_PROCESSOR || this == MOVING_TARGET
                    || this == MOVING_POD;
        }
    }
}
