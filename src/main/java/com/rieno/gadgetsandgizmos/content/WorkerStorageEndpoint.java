package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerArea;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineSite;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerMachine;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerItemPort;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerMachineRegistry;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeDefinition;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerOutputLedger;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipePlan;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Adapt smart storage, Ship Docks and manifested standard storage for workers
public final class WorkerStorageEndpoint implements WorkerEndpoint {
    private static final Map<Level, Map<AreaCacheKey, AreaCacheEntry>> AREA_CACHE = new WeakHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private final Level level;
    private final BlockPos pos;
    private final @Nullable Direction side;
    private final @Nullable UUID subLevelId;
    private final UUID id;
    private final String label;
    private final String blockId;
    private final String referenceId;
    private final String assignedRecipeId;
    private final boolean extractionAllowed;
    private final boolean insertionAllowed;
    private final boolean explicitResourceAccess;
    private List<BlockPos> areaOutputPositions = List.of();
    private List<WorkerMachineSite.Port> machineInputPorts = List.of();
    private List<WorkerMachineSite.Port> machineOutputPorts = List.of();
    private List<String> assignedRecipeIds = List.of();
    private @Nullable WorkerArea areaBounds;
    private @Nullable WorkerRecipePlan activeRecipe;
    private @Nullable WorkerRecipePlan sourceRecipe;
    private boolean stagedInputView;
    private long machineCacheTick = Long.MIN_VALUE;
    private @Nullable WorkerMachine machineCache;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the worker storage endpoint
    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        this(level, pos, side, null, "", "", "", true, true, false);
    }

    // Initialize an endpoint view with explicit worker access directions
    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side,
                                  boolean extractionAllowed, boolean insertionAllowed) {
        this(level, pos, side, null, "", "", "", extractionAllowed, insertionAllowed, false);
    }

    // Initialize a linked endpoint view
    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side,
                                  @Nullable UUID subLevelId, String label, String blockId, String referenceId,
                                  boolean extractionAllowed, boolean insertionAllowed,
                                  boolean explicitResourceAccess) {
        this(level, pos, side, subLevelId, label, blockId, referenceId,
                extractionAllowed, insertionAllowed, explicitResourceAccess, "");
    }

    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side,
                                  @Nullable UUID subLevelId, String label, String blockId, String referenceId,
                                  boolean extractionAllowed, boolean insertionAllowed,
                                  boolean explicitResourceAccess, String assignedRecipeId) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.subLevelId = subLevelId;
        this.label = label == null ? "" : label;
        this.blockId = blockId == null ? "" : blockId;
        this.referenceId = referenceId == null ? "" : referenceId;
        this.assignedRecipeId = assignedRecipeId == null ? "" : assignedRecipeId;
        this.extractionAllowed = extractionAllowed;
        this.insertionAllowed = insertionAllowed;
        this.explicitResourceAccess = explicitResourceAccess;
        id = com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointIdentity.of(level.dimension().location(), subLevelId, pos, side);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Discover loaded smart, dock and manifested endpoints without loading chunks
    public static List<WorkerStorageEndpoint> discover(Level level, BlockPos center, int chunkRadius) {
        Map<BlockPos, WorkerStorageEndpoint> endpoints = new LinkedHashMap<>();
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(
                level, center, Math.max(1, chunkRadius))) {
            if (blockEntity instanceof SmartVaultBlockEntity
                    || blockEntity instanceof SmartTankBlockEntity
                    || blockEntity instanceof SmartBatteryBlockEntity
                    || blockEntity instanceof ShipDockBlockEntity
                    || ManifestTabletStorage.attached(level, blockEntity.getBlockPos())) {
                BlockPos pos = blockEntity.getBlockPos();
                endpoints.putIfAbsent(pos, new WorkerStorageEndpoint(level, pos, null,
                        SimulatedHelper.getContainingSubLevelId(blockEntity), "", "", "", true, true, false));
                continue;
            }
            if (!(blockEntity instanceof ShippingManifestBlockEntity)) continue;
            BlockState state = blockEntity.getBlockState();
            if (!(state.getBlock() instanceof ShippingManifestBlock)) continue;
            BlockPos targetPos = ShippingManifestBlock.attachedTargetPos(blockEntity.getBlockPos(), state);
            Direction targetSide = ShippingManifestBlock.attachedTargetSide(state);
            endpoints.putIfAbsent(targetPos, new WorkerStorageEndpoint(level, targetPos, targetSide,
                    SimulatedHelper.getContainingSubLevelId(blockEntity), "", "", "", true, true, false));
        }
        return new ArrayList<>(endpoints.values());
    }

    // Create the managed endpoint represented by a loaded block entity
    public static @Nullable WorkerStorageEndpoint managed(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null) {
            return null;
        }
        Level level = blockEntity.getLevel();
        BlockPos targetPos = blockEntity.getBlockPos();
        Direction targetSide = null;
        if (blockEntity instanceof DiagnosticTabletBlockEntity tablet) {
            targetPos = tablet.getBlockPos().relative(tablet.getBlockState()
                    .getValue(DiagnosticTabletBlock.FACING).getOpposite());
        }
        if (blockEntity instanceof ShippingManifestBlockEntity manifest) {
            BlockState state = manifest.getBlockState();
            if (!(state.getBlock() instanceof ShippingManifestBlock)) {
                return null;
            }
            targetPos = ShippingManifestBlock.attachedTargetPos(manifest.getBlockPos(), state);
            targetSide = ShippingManifestBlock.attachedTargetSide(state);
        }
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(blockEntity);
        BlockState targetState = level.getBlockState(targetPos);
        String blockId = BuiltInRegistries.BLOCK.getKey(targetState.getBlock()).toString();
        String label = targetState.getBlock().getName().getString();
        String reference = "worker:managed:" + subLevelId + ":" + targetPos.asLong()
                + ":" + (targetSide == null ? "all" : targetSide.getName());
        WorkerStorageEndpoint endpoint = new WorkerStorageEndpoint(level, targetPos, targetSide,
                subLevelId, label, blockId, reference, true, true, false);
        return endpoint.isManagedStorage() && endpoint.hasAnyCapability() ? endpoint : null;
    }

    // Resolve storage or machine endpoints explicitly targeted by the ACC linker
    public static List<WorkerStorageEndpoint> linked(AdvancedContraptionControllerBlockEntity controller,
                                                     boolean includeMachines) {
        if (controller == null || controller.getLevel() == null) return List.of();
        Map<UUID, WorkerStorageEndpoint> endpoints = new LinkedHashMap<>();
        for (ContraptionNetworkLinkerData.LinkedTarget target
                : ContraptionNetworkLinkerData.readTargets(controller.getStoredLinker())) {
            if (target.mode() != ContraptionNetworkLinkerData.LinkMode.SCM) continue;
            List<LinkedPosition> positions = linkedPositions(controller.getLevel(), target);
            if (includeMachines) positions = expandProcessingPositions(positions);
            for (LinkedPosition linked : positions) {
                String targetReference = ContraptionNetworkLinkerData.nodeIdForTarget(target);
                String linkedBlockId = BuiltInRegistries.BLOCK.getKey(
                        linked.level().getBlockState(linked.pos()).getBlock()).toString();
                String linkedLabel = target.label();
                if (linked.side() != null) {
                    targetReference += "::worker:" + linked.pos().asLong() + ":" + linked.side().getName();
                    linkedLabel += " -> " + linked.level().getBlockState(linked.pos()).getBlock()
                            .getName().getString();
                }
                WorkerStorageEndpoint endpoint = new WorkerStorageEndpoint(
                        linked.level(), linked.pos(), linked.side(), target.subLevelId(),
                        linkedLabel, linkedBlockId, targetReference,
                        true, true, true);
                if(!endpoint.isAvailable() || !endpoint.hasAnyCapability()) continue;
                if(!includeMachines && !endpoint.isWorkerManagedStorage()) continue;
                endpoints.putIfAbsent(endpoint.id(), endpoint);
            }
        }
        for(ContraptionNetworkLinkerData.LinkedArea area
                : ContraptionNetworkLinkerData.readAreas(controller.getStoredLinker())){
            if(area.kind() == ContraptionNetworkLinkerData.AreaKind.NO_ENTRY) continue;
            Level targetLevel = SubLevelBlockEntityCollector.resolveTargetLevel(controller.getLevel(), area.subLevelId());
            if(targetLevel == null) continue;
            List<LinkedPosition> positions = new ArrayList<>();
            for(BlockPos pos : areaBlocks(targetLevel, area)){
                positions.addAll(resolveStoragePositions(targetLevel, pos, null));
            }
            if(includeMachines) positions = expandProcessingPositions(positions);
            List<BlockPos> areaOutputs = includeMachines ? positions.stream()
                    .map(LinkedPosition::pos).distinct()
                    .filter(area.bounds()::contains)
                    .filter(pos -> WorkerMachineRegistry.resolve(targetLevel, pos, null) == null
                            && !WorkerContainerAccess.itemHandlers(targetLevel, pos, null).isEmpty())
                    .toList() : List.of();
            for(LinkedPosition linked : positions){
                if(!area.bounds().contains(linked.pos())) continue;
                String blockId = BuiltInRegistries.BLOCK.getKey(
                        linked.level().getBlockState(linked.pos()).getBlock()).toString();
                String label = (area.label().isBlank() ? "Worker Area" : area.label())
                        + " -> " + linked.level().getBlockState(linked.pos())
                        .getBlock().getName().getString();
                String reference = "worker:area:" + area.id() + ":" + linked.pos().asLong();
                WorkerStorageEndpoint endpoint = new WorkerStorageEndpoint(linked.level(), linked.pos(), linked.side(),
                        area.subLevelId(), label, blockId, reference, true, true, true, "");
                if(endpoint.isProcessingMachine()){
                    endpoint.areaBounds = area.bounds();
                    endpoint.machineInputPorts = area.ports().stream()
                            .filter(port -> port.role() == ContraptionNetworkLinkerData.MachinePortRole.INPUT)
                            .map(port -> new WorkerMachineSite.Port(port.pos(), port.face())).toList();
                    endpoint.machineOutputPorts = area.ports().stream()
                            .filter(port -> port.role() == ContraptionNetworkLinkerData.MachinePortRole.OUTPUT)
                            .map(port -> new WorkerMachineSite.Port(port.pos(), port.face())).toList();
                    endpoint.areaOutputPositions = endpoint.machineOutputPorts.isEmpty() ? areaOutputs
                            : endpoint.machineOutputPorts.stream().map(WorkerMachineSite.Port::pos).distinct().toList();
                    endpoint.assignedRecipeIds = area.recipeIds();
                }
                if(!endpoint.isAvailable() || !endpoint.hasAnyCapability()) continue;
                if(!includeMachines && !endpoint.isWorkerManagedStorage()) continue;
                if(endpoint.isProcessingMachine()) endpoints.put(endpoint.id(), endpoint);
                else endpoints.putIfAbsent(endpoint.id(), endpoint);
            }
        }
        return List.copyOf(endpoints.values());
    }

    // Refresh complete areas each second and retry partial loads after a few ticks
    private static List<BlockPos> areaBlocks(Level level, ContraptionNetworkLinkerData.LinkedArea area){
        AreaCacheKey key = new AreaCacheKey(area.id(), area.subLevelId(), area.bounds());
        synchronized(AREA_CACHE){
            Map<AreaCacheKey, AreaCacheEntry> entries = AREA_CACHE.get(level);
            AreaCacheEntry cached = entries == null ? null : entries.get(key);
            long time = level.getGameTime();
            if(cached != null && time >= cached.tick()
                    && time - cached.tick() < (cached.complete() ? 20L : 5L)) return cached.blocks();
            WorkerArea.Scan scan = area.bounds().scan(level, area.subLevelId());
            if(entries == null){
                entries = new LinkedHashMap<>();
                AREA_CACHE.put(level, entries);
            }
            if(entries.size() >= 64 && !entries.containsKey(key)) entries.remove(entries.keySet().iterator().next());
            entries.put(key, new AreaCacheEntry(time, scan.blocks(), scan.complete()));
            return scan.blocks();
        }
    }

    private record AreaCacheKey(UUID id, @Nullable UUID subLevelId, WorkerArea bounds){}
    private record AreaCacheEntry(long tick, List<BlockPos> blocks, boolean complete){}

    // Identify shared storage independently of which face was linked
    public UUID storageId(){
        BlockPos storagePos = com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageIdentity.position(level, pos);
        return com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointIdentity.of(
                level.dimension().location(), subLevelId, storagePos, null);
    }

    // Check whether this endpoint is a docking transfer buffer
    public boolean isDock() {
        return level.getBlockEntity(pos) instanceof ShipDockBlockEntity;
    }

    // Resolve reusable machine behaviour from the live linked block
    private @Nullable WorkerMachine machine(){
        long tick = level.getGameTime();
        if(machineCacheTick != tick){
            machineCache = WorkerMachineRegistry.resolve(level, pos, side);
            machineCacheTick = tick;
        }
        return machineCache;
    }

    private @Nullable WorkerMachineSite machineSite(){
        return areaBounds == null ? null : new WorkerMachineSite(areaBounds,
                machineInputPorts, machineOutputPorts);
    }

    private boolean hasMarkedMachinePorts(){
        return !machineInputPorts.isEmpty() || !machineOutputPorts.isEmpty();
    }

    public boolean isProcessingMachine(){
        return machine() != null;
    }

    // Prefer the bounded view when a machine also has a direct face link
    public boolean isAreaMachine(){
        return areaBounds != null && isProcessingMachine();
    }

    // Rank an available machine by the transport changes its route would need
    public int routingPenalty(WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        if(machine == null) return Integer.MAX_VALUE;
        int penalty = machine.routingPenalty(plan, areaBounds);
        if(!assignedRecipeIds.isEmpty() && plan != null
                && !assignedRecipeIds.contains(plan.recipeId().toString())) penalty += 16;
        return penalty;
    }

    // Reserve this machine's conflicting transport until its real output is collected
    public boolean isolateTransport(WorkerRecipePlan plan, UUID orderId){
        WorkerMachine machine = machine();
        if(machine == null) return false;
        if(areaBounds == null) return true;
        List<BlockPos> gates = machine.conflictingTransport(plan, areaBounds);
        return com.rieno.gadgetsandgizmos.lib.worker.WorkerTransportLocks.acquire(
                level, orderId, gates == null ? List.of() : gates);
    }

    public boolean isCraftingStation(){
        WorkerMachine machine = machine();
        return machine != null && machine.virtualCrafting();
    }

    // Bind machine input and output ports to this exact recipe without changing endpoint identity
    public WorkerStorageEndpoint forRecipe(WorkerRecipePlan plan){
        WorkerStorageEndpoint endpoint = withAccess(extractionAllowed, insertionAllowed);
        endpoint.activeRecipe = plan;
        return endpoint;
    }

    // Match intermediate progress when withdrawing a staged recipe ingredient
    public WorkerStorageEndpoint forSource(WorkerRecipePlan plan){
        WorkerStorageEndpoint endpoint = withAccess(extractionAllowed, insertionAllowed);
        endpoint.sourceRecipe = plan;
        return endpoint;
    }

    // Read a paused machine's ingredient ports so its workpiece can be moved aside safely.
    public WorkerStorageEndpoint forStagedInputs(WorkerRecipePlan plan){
        WorkerStorageEndpoint endpoint = withAccess(true, false);
        endpoint.activeRecipe = plan;
        endpoint.stagedInputView = true;
        return endpoint;
    }

    // Read the actual occupied input slots, including cells not credited by a recipe preload.
    public Map<WorkerResourceKey, Long> stagedInputItems(){
        if(!stagedInputView || !isAvailable()) return Map.of();
        Map<WorkerResourceKey, Long> stocked = new LinkedHashMap<>();
        Set<IItemHandler> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(IItemHandler handler : outputItemHandlers()){
            IItemHandler backing = handler instanceof WorkerItemPort port ? port.inventory() : handler;
            if(!seen.add(backing)) continue;
            for(int slot = 0; slot < handler.getSlots(); slot++){
                ItemStack stack = handler.getStackInSlot(slot);
                if(stack.isEmpty()) continue;
                int extractable = handler.extractItem(slot, stack.getCount(), true).getCount();
                if(extractable <= 0) continue;
                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()));
                stocked.merge(resource, (long)extractable,
                        (first, second) -> first >= Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second);
            }
        }
        return Map.copyOf(stocked);
    }

    public WorkerMachine.Supply machineSupply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available){
        return machineSupply(plan, available, 1L);
    }

    public WorkerMachine.Supply machineSupply(WorkerRecipePlan plan, Map<WorkerResourceKey, Long> available,
                                              long batches){
        WorkerMachine machine = machine();
        return machine == null ? WorkerMachine.Supply.READY : machine.supply(plan, available, batches);
    }

    // Collect only a completed assembly's alternate result, never its unfinished workpiece
    public @Nullable WorkerResourcePacket alternateRecipeResult(WorkerRecipePlan plan){
        return alternateRecipeResult(plan, null);
    }

    public @Nullable WorkerResourcePacket alternateRecipeResult(WorkerRecipePlan plan,
                                                                @Nullable WorkerOutputLedger ledger){
        for(IItemHandler handler : outputItemHandlers()){
            for(int idx = 0; idx < handler.getSlots(); idx++){
                ItemStack stack = handler.getStackInSlot(idx);
                if(!com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.alternateResult(level, plan, stack)) continue;
                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                long fresh = ledger == null ? stack.getCount() : ledger.produced(resource, available(resource));
                if(fresh <= 0L) continue;
                int before = stack.getCount();
                ItemStack removed = handler.extractItem(idx, (int)Math.min(fresh, before), false);
                if(removed.isEmpty() || !WorkerOutputLedger.confirmsExtraction(removed.getCount(), before,
                        handler.getStackInSlot(idx).getCount())) continue;
                CompoundTag payload = new CompoundTag();
                payload.put("Stack", removed.saveOptional(level.registryAccess()));
                return new WorkerResourcePacket(resource, removed.getCount(), payload);
            }
        }
        return null;
    }

    // Wake a parked worker when this assembly has produced a new final, failed or recirculating result
    public boolean hasRecipeOutcome(WorkerRecipePlan plan, @Nullable WorkerOutputLedger ledger){
        if(plan == null) return false;
        for(IItemHandler handler : outputItemHandlers()){
            for(int idx = 0; idx < handler.getSlots(); idx++){
                ItemStack stack = handler.getStackInSlot(idx);
                if(!com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.alternateResult(level, plan, stack)) continue;
                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if((ledger == null ? stack.getCount() : ledger.produced(resource, available(resource))) > 0L) return true;
            }
        }
        WorkerMachine machine = machine();
        if(machine == null) return false;
        for(IItemHandler handler : recirculationItemHandlers(plan)){
            for(int idx = 0; idx < handler.getSlots(); idx++){
                ItemStack stack = handler.getStackInSlot(idx);
                if(stack.isEmpty()) continue;
                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if((ledger == null ? stack.getCount() : ledger.produced(resource, available(resource))) > 0L) return true;
            }
        }
        return false;
    }

    public void startRecipe(WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        if(machine != null) machine.start(plan);
    }

    // Keep the real intermediate stack and its progress when returning a machine loop to its input
    public @Nullable WorkerResourcePacket recirculatingRecipeResult(WorkerRecipePlan plan){
        return recirculatingRecipeResult(plan, null);
    }

    public @Nullable WorkerResourcePacket recirculatingRecipeResult(WorkerRecipePlan plan,
                                                                    @Nullable WorkerOutputLedger ledger){
        WorkerMachine machine = machine();
        if(machine == null) return null;
        for(var handler : recirculationItemHandlers(plan)){
            for(int idx = 0; idx < handler.getSlots(); idx++){
                ItemStack present = handler.getStackInSlot(idx);
                if(present.isEmpty()) continue;
                WorkerResourceKey resource = new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(present.getItem()));
                if(ledger != null && ledger.produced(resource, available(resource)) <= 0L) continue;
                int beforeCount = present.getCount();
                ItemStack stack = handler.extractItem(idx, 1, false);
                if(stack.isEmpty() || !WorkerOutputLedger.confirmsExtraction(stack.getCount(), beforeCount,
                        handler.getStackInSlot(idx).getCount())) continue;
                CompoundTag payload = new CompoundTag();
                payload.put("Stack", stack.saveOptional(level.registryAccess()));
                return new WorkerResourcePacket(resource, stack.getCount(), payload);
            }
        }
        return null;
    }

    // Check whether this endpoint is a stonecutter station.
    public boolean isStonecutter() {
        String id = blockId.isBlank() ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()
                : blockId;
        return "minecraft:stonecutter".equals(id);
    }

    // Check whether workers may use this endpoint as an automatic source or destination.
    public boolean isWorkerManagedStorage() {
        return isManagedStorage() || explicitResourceAccess && isCreativeCrate();
    }

    // Linked ordinary containers may supply recipes, but only managed storage may receive idle cargo.
    public boolean isWorkerRecipeSource(){
        return isAvailable() && !isProcessingMachine() && !isCraftingStation();
    }

    // Check machine availability before expanding a recipe's dependencies
    public boolean supportsRecipeDefinition(WorkerRecipeDefinition recipe){
        if(!isAvailable() || recipe == null || !assignedRecipeId.isBlank()
                && !assignedRecipeId.equals(recipe.recipeId().toString())) return false;
        WorkerMachine machine = machine();
        return machine != null && machine.maySupportRecipe(recipe.recipeId(), recipe.processorType())
                && (hasMarkedMachinePorts() ? machine.supportsAt(recipe, machineSite())
                : areaBounds == null ? machine.supports(recipe) : machine.supports(recipe, areaBounds));
    }

    public boolean supportsRecipePlan(@Nullable WorkerRecipePlan plan){
        if(!isAvailable() || plan == null || !assignedRecipeId.isBlank()
                && !assignedRecipeId.equals(plan.recipeId().toString())) return false;
        WorkerMachine machine = machine();
        return machine != null && machine.maySupportRecipe(plan.recipeId(), plan.processorType())
                && (hasMarkedMachinePorts() ? machine.supportsAt(plan, machineSite())
                : areaBounds == null ? machine.supports(plan) : machine.supports(plan, areaBounds));
    }

    public boolean hasProcessorFor(WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        return plan != null && machine != null
                && machine.maySupportRecipe(plan.recipeId(), plan.processorType());
    }

    public List<String> recipeRouteDiagnostics(WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        return machine == null || areaBounds == null ? List.of() : machine.routeDiagnostics(plan, machineSite());
    }

    // Keep legacy adapter call order unless an adapter explicitly permits a cheap preload probe.
    public List<Long> creditedPreloadedInputs(WorkerRecipeDefinition recipe){
        if(!isAvailable() || recipe == null || !assignedRecipeId.isBlank()
                && !assignedRecipeId.equals(recipe.recipeId().toString())) return List.of();
        WorkerMachine machine = machine();
        if(machine == null || !machine.mayHavePreloadedInputs(recipe)) return List.of();
        boolean probeFirst = machine.mayProbePreloadedInputsBeforeSupport();
        if(!probeFirst && !supportsRecipeDefinition(recipe)) return List.of();
        List<Long> stocked = preloadedInputs(recipe);
        if(stocked.stream().noneMatch(count -> count > 0L)) return List.of();
        return !probeFirst || supportsRecipeDefinition(recipe) ? stocked : List.of();
    }

    // Count only ingredients already held by this recipe's physical machine inputs
    public List<Long> preloadedInputs(WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        return machine == null ? List.of() : hasMarkedMachinePorts()
                ? machine.preloadedInputsAt(plan, machineSite(), 1L)
                : machine.preloadedInputs(plan, areaBounds, 1L);
    }

    public List<Long> preloadedInputs(WorkerRecipePlan plan, long batches){
        WorkerMachine machine = machine();
        return machine == null ? List.of() : hasMarkedMachinePorts()
                ? machine.preloadedInputsAt(plan, machineSite(), batches)
                : machine.preloadedInputs(plan, areaBounds, batches);
    }

    public List<Long> preloadedInputs(WorkerRecipeDefinition recipe){
        WorkerMachine machine = machine();
        return machine == null ? List.of() : hasMarkedMachinePorts()
                ? machine.preloadedInputsAt(recipe, machineSite())
                : machine.preloadedInputs(recipe, areaBounds);
    }

    public boolean selectRecipeResult(@Nullable WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        return plan != null && machine != null && machine.prepare(plan);
    }

    // Create a direction-restricted view for dispatching one delivery
    public WorkerStorageEndpoint withAccess(boolean allowExtraction, boolean allowInsertion) {
        WorkerStorageEndpoint endpoint = new WorkerStorageEndpoint(level, pos, side, subLevelId, label, blockId,
                referenceId, allowExtraction, allowInsertion, explicitResourceAccess, assignedRecipeId);
        endpoint.activeRecipe = activeRecipe;
        endpoint.sourceRecipe = sourceRecipe;
        endpoint.stagedInputView = stagedInputView;
        endpoint.areaOutputPositions = areaOutputPositions;
        endpoint.areaBounds = areaBounds;
        endpoint.machineInputPorts = machineInputPorts;
        endpoint.machineOutputPorts = machineOutputPorts;
        endpoint.assignedRecipeIds = assignedRecipeIds;
        return endpoint;
    }

    // Recheck endpoint readiness after chunk or plot transitions
    @Override
    public boolean isAvailable(){
        return SubLevelBlockEntityCollector.isTargetLoaded(level, subLevelId, pos)
                && (!level.getBlockState(pos).isAir() || machine() != null)
                && (!level.getBlockState(pos).hasBlockEntity() || level.getBlockEntity(pos) != null);
    }

    // Keep virtual crafting stations out of ordinary storage destinations
    @Override
    public boolean acceptsDelivery(){
        return isWorkerManagedStorage() && !isCraftingStation() && !isCreativeCrate();
    }

    @Override
    public boolean acceptsProcessingInput(){
        return isProcessingMachine() && !isCraftingStation();
    }

    // Prefer a matching manifest filter even when its container is currently empty
    @Override
    public int insertionPriority(WorkerResourceKey resource){
        if(resource.type() != WorkerResourceType.ITEM) return available(resource) > 0L ? 1 : 2;
        Item item = BuiltInRegistries.ITEM.get(resource.id());
        ItemStack sample = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if(sample.isEmpty()) return 2;
        for(Direction dir : Direction.values()){
            BlockPos manifestPos = pos.relative(dir);
            if(level.getBlockEntity(manifestPos) instanceof ShippingManifestBlockEntity manifest
                    && manifest.isContainerLocked()
                    && ShippingManifestBlock.attachedTargetPos(manifestPos, manifest.getBlockState()).equals(pos)
                    && manifest.containerLockFilters().stream().anyMatch(filter ->
                    com.simibubi.create.content.logistics.filter.FilterItemStack.of(filter).test(level, sample)))
                return 0;
        }
        if(ManifestTabletStorage.attached(level, pos)){
            var root = com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi.serverLevel(level);
            var key = com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageIdentity.position(level, pos);
            var config = root == null ? null : com.rieno.gadgetsandgizmos.lib.inventory.ContainerAutomationStore
                    .get(root).find(subLevelId, key);
            if(config != null && !config.filter().isEmpty() && config.accepts(level, sample)) return 0;
        }
        return available(resource) > 0L ? 1 : 2;
    }

    // Get the stable linker discovery id backing this endpoint
    public String referenceId() {
        return referenceId;
    }

    // Create the graph target used for explicit worker routing
    public ControllerDiscoveryNode discoveryNode() {
        String group = subLevelId == null ? "worker:world" : "worker:" + subLevelId;
        return new ControllerDiscoveryNode(referenceId, ControllerDiscoveryKind.MACHINE,
                group, blockId, label, subLevelId, pos);
    }

    // Create the read-only snapshot shown by worker selectors
    public WorkerEndpointSnapshot snapshot() {
        Map<WorkerResourceKey, long[]> resources = new LinkedHashMap<>();
        if (allowsItems()) {
            for (IItemHandler items : outputItemHandlers()) {
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    WorkerResourceKey key = new WorkerResourceKey(WorkerResourceType.ITEM,
                            BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    long[] amounts = resources.computeIfAbsent(key, ignored -> new long[2]);
                    amounts[0] += stack.getCount();
                }
            }
        }
        resources.forEach((key, amounts) -> {
            if (key.type() == WorkerResourceType.ITEM) amounts[1] = amounts[0] + itemSpace(key.id());
        });
        if (allowsFluids()) {
            for (IFluidHandler fluids : outputFluidHandlers()) {
                for (int tank = 0; tank < fluids.getTanks(); tank++) {
                    FluidStack stack = fluids.getFluidInTank(tank);
                    if (stack.isEmpty()) continue;
                    if (allowsFluidResource(WorkerResourceType.FLUID)) {
                        WorkerResourceKey key = new WorkerResourceKey(WorkerResourceType.FLUID,
                                BuiltInRegistries.FLUID.getKey(stack.getFluid()));
                        resources.computeIfAbsent(key, ignored -> new long[2])[0] += stack.getAmount();
                    }
                    if (allowsFluidResource(WorkerResourceType.FUEL)) {
                        WorkerResourceKey key = new WorkerResourceKey(WorkerResourceType.FUEL,
                                BuiltInRegistries.FLUID.getKey(stack.getFluid()));
                        resources.computeIfAbsent(key, ignored -> new long[2])[0] += stack.getAmount();
                    }
                }
            }
        }
        resources.forEach((key, amounts) -> {
            if (key.type() == WorkerResourceType.FLUID || key.type() == WorkerResourceType.FUEL) {
                amounts[1] = amounts[0] + fluidSpace(key.id());
            }
        });
        long storedEnergy = allowsEnergy() ? exactStoredEnergy() : 0L;
        long energyCapacity = allowsEnergy() ? exactEnergyCapacity() : 0L;
        if (allowsEnergy() && energyCapacity > 0L) {
            resources.put(WorkerResourceKey.energy(), new long[]{storedEnergy, energyCapacity});
        }
        List<WorkerEndpointSnapshot.ResourceAmount> amounts = resources.entrySet().stream()
                .map(entry -> new WorkerEndpointSnapshot.ResourceAmount(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
        String resolvedBlockId = blockId.isBlank()
                ? BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString() : blockId;
        String resolvedLabel = label.isBlank() ? resolvedBlockId : label;
        return new WorkerEndpointSnapshot(id, subLevelId, pos, resolvedLabel, resolvedBlockId,
                extractionAllowed, insertionAllowed, amounts, isStorageSelectorEligible());
    }

    // Expose only smart containers and manifested storage to source and destination selectors
    private boolean isStorageSelectorEligible(){
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof SmartVaultBlockEntity
                || blockEntity instanceof SmartTankBlockEntity
                || blockEntity instanceof SmartBatteryBlockEntity
                || !isProcessingMachine() && (ShippingManifestBlockEntity.attachedResourceUses(level, pos) >= 0
                || ManifestTabletStorage.attached(level, pos))
                && hasAnyCapability();
    }

    // Get copies of the worker-managed items for exact schedule filter evaluation
    public List<ItemStack> stockedItems() {
        if (!allowsItems()) return List.of();
        List<ItemStack> stacks = new ArrayList<>();
        for (IItemHandler items : itemHandlers()) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                if (!stack.isEmpty()) stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    // Get copies of the worker-managed fluids for exact schedule filter evaluation
    public List<FluidStack> stockedFluids(WorkerResourceType type) {
        if (type != WorkerResourceType.FLUID && type != WorkerResourceType.FUEL) return List.of();
        if (!allowsFluidResource(type)) return List.of();
        List<FluidStack> stacks = new ArrayList<>();
        for (IFluidHandler fluids : fluidHandlers()) {
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                FluidStack stack = fluids.getFluidInTank(tank);
                if (!stack.isEmpty()) stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    // Check whether the worker-managed item storage can accept one exact stack
    public boolean canInsert(ItemStack stack) {
        if(isCreativeCrate()) return false;
        if (stack == null || stack.isEmpty() || !insertionAllowed || !allowsItems()) return false;
        if (!ShippingManifestBlockEntity.allowsAttachedContainerItem(level, pos, stack)) return false;
        for (IItemHandler items : itemHandlers()) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                if (items.insertItem(slot, stack, true).getCount() < stack.getCount()) return true;
            }
        }
        return false;
    }

    // Get the worker-managed FE stored in this endpoint
    public long storedEnergy() {
        return allowsEnergy() ? exactStoredEnergy() : 0L;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public @Nullable UUID subLevelId() {
        return subLevelId;
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    // Get the linker label used by worker activity text.
    @Override
    public String label() {
        return label.isBlank() ? position().toShortString() : label;
    }

    @Override
    public List<BlockPos> interactionBlocks() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ItemVaultBlockEntity vault) return connectedVaultBlocks(vault);
        if (blockEntity instanceof FluidTankBlockEntity tank) return connectedTankBlocks(tank);
        WorkerMachine machine = machine();
        if(machine != null && !machine.members().isEmpty()) return machine.members();
        return List.of(pos);
    }

    @Override
    public @Nullable Direction interactionFace() {
        return side;
    }

    @Override
    public boolean canExtract(WorkerResourceKey resource) {
        if (!isAvailable() || resource == null || !extractionAllowed || !allows(resource.type())) return false;
        return switch (resource.type()) {
            case ITEM -> !itemHandlers().isEmpty();
            case FLUID, FUEL -> !fluidHandlers().isEmpty();
            case ENERGY -> energyStorages().stream().anyMatch(IEnergyStorage::canExtract);
        };
    }

    @Override
    public boolean canInsert(WorkerResourceKey resource) {
        if(isCreativeCrate()) return false;
        if (!isAvailable() || resource == null || !insertionAllowed || !allows(resource.type())) return false;
        if (resource.type() == WorkerResourceType.ITEM && isCraftingStation()) return true;
        if (resource.type() == WorkerResourceType.ITEM) {
            Item item = BuiltInRegistries.ITEM.get(resource.id());
            if (item == null || !ShippingManifestBlockEntity.allowsAttachedContainerItem(level, pos,
                    new ItemStack(item))) return false;
        }
        return switch (resource.type()) {
            case ITEM -> !itemHandlers().isEmpty();
            case FLUID, FUEL -> !fluidHandlers().isEmpty();
            case ENERGY -> energyStorages().stream().anyMatch(IEnergyStorage::canReceive);
        };
    }

    @Override
    public long available(WorkerResourceKey resource) {
        if(!isAvailable() || resource == null || !allows(resource.type())) return 0L;
        return switch (resource.type()) {
            case ITEM -> {
                long amount = availableItems(resource.id());
                yield isCreativeCrate() && amount > 0L ? Long.MAX_VALUE : amount;
            }
            case FLUID, FUEL -> availableFluid(resource.id());
            case ENERGY -> exactStoredEnergy();
        };
    }

    @Override
    public long space(WorkerResourceKey resource) {
        if(isCreativeCrate()) return 0L;
        if(!isAvailable() || resource == null || !allows(resource.type())) return 0L;
        return switch (resource.type()) {
            case ITEM -> isCraftingStation() ? Long.MAX_VALUE : itemSpace(resource.id());
            case FLUID, FUEL -> fluidSpace(resource.id());
            case ENERGY -> Math.max(0L, exactEnergyCapacity() - exactStoredEnergy());
        };
    }

    @Override
    public WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        if(!isAvailable() || resource == null || maximumAmount <= 0L || !extractionAllowed || !allows(resource.type())) return WorkerResourcePacket.empty(resource);
        return switch (resource.type()) {
            case ITEM -> extractItems(resource, maximumAmount, simulate);
            case FLUID, FUEL -> extractFluid(resource, maximumAmount, simulate);
            case ENERGY -> extractEnergy(resource, maximumAmount, simulate);
        };
    }

    @Override
    public long insert(WorkerResourcePacket packet, boolean simulate) {
        if(isCreativeCrate()) return 0L;
        if(!isAvailable() || packet == null || packet.isEmpty() || !insertionAllowed || !allows(packet.resource().type())) return 0L;
        return switch (packet.resource().type()) {
            case ITEM -> insertItems(packet, simulate);
            case FLUID, FUEL -> insertFluid(packet, simulate);
            case ENERGY -> insertEnergy(packet, simulate);
        };
    }

    // Creative crates supply items but discard everything inserted into them
    private boolean isCreativeCrate(){
        return level.getBlockEntity(pos) instanceof CreativeCrateBlockEntity;
    }

    // Get every NeoForge item capability available to the worker
    private List<IItemHandler> itemHandlers() {
        WorkerMachine machine = machine();
        if(machine != null && stagedInputView) return hasMarkedMachinePorts()
                ? machine.stagedItemInputsAt(activeRecipe, machineSite())
                : machine.stagedItemInputs(activeRecipe, areaBounds);
        if(machine != null) return hasMarkedMachinePorts() ? machine.itemInputsAt(activeRecipe, machineSite())
                : areaBounds == null ? machine.itemInputs(activeRecipe) : machine.itemInputs(activeRecipe, areaBounds);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartVaultBlockEntity vault) return List.of(vault.getItemHandler());
        if(blockEntity instanceof ItemVaultBlockEntity vault){
            return connectedVaultBlocks(vault).stream().map(level::getBlockEntity)
                    .filter(ItemVaultBlockEntity.class::isInstance).map(ItemVaultBlockEntity.class::cast)
                    .map(member -> com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry.wrap(
                            level, member.getBlockPos(), member.getInventoryOfBlock())).toList();
        }
        if (blockEntity instanceof MechanicalCrafterBlockEntity crafter) {
            return List.of(crafter.getInput().getItemHandler(level, pos));
        }
        return WorkerContainerAccess.itemHandlers(level, pos, side);
    }

    // Get every NeoForge fluid capability available to the worker
    private List<IFluidHandler> fluidHandlers() {
        WorkerMachine machine = machine();
        if(machine != null) return machine.fluidInputs(activeRecipe);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartTankBlockEntity tank) return List.of(tank.getFluidHandler());
        if (blockEntity instanceof FluidTankBlockEntity tank) return List.of(tank.getTankInventory());
        return WorkerContainerAccess.fluidHandlers(level, pos, side);
    }

    // Keep finished products separate from the machine's ingredients and fuel
    private List<IItemHandler> outputItemHandlers(){
        if(stagedInputView) return itemHandlers();
        WorkerMachine machine = machine();
        java.util.Set<IItemHandler> unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<IItemHandler> handlers = new ArrayList<>();
        for(IItemHandler handler : machine == null ? itemHandlers() : !machineOutputPorts.isEmpty() ? List.<IItemHandler>of()
                : areaBounds == null ? machine.itemOutputs(activeRecipe) : machine.itemOutputs(activeRecipe, areaBounds)){
            if(unique.add(handler)) handlers.add(handler);
        }
        if(machine != null){
            List<BlockPos> outputs = machineOutputPorts.isEmpty()
                    ? machine.areaOutputs(activeRecipe, areaBounds, areaOutputPositions) : areaOutputPositions;
            for(BlockPos output : outputs){
                Direction outputFace = machineOutputPorts.stream().filter(port -> port.pos().equals(output))
                        .map(WorkerMachineSite.Port::face).findFirst().orElse(null);
                for(IItemHandler handler : WorkerContainerAccess.itemHandlers(level, output, outputFace)){
                    if(unique.add(handler)) handlers.add(handler);
                }
            }
        }
        if(activeRecipe == null && sourceRecipe == null) return handlers;
        return handlers.stream().map(handler -> (IItemHandler) new com.rieno.gadgetsandgizmos.lib.worker.WorkerItemOutput(handler,
                stack -> com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.matchesInput(level, sourceRecipe, stack)
                        && com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.matchesOutput(level, activeRecipe, stack))).toList();
    }

    private List<IItemHandler> recirculationItemHandlers(WorkerRecipePlan plan){
        WorkerMachine machine = machine();
        if(machine == null) return List.of();
        List<IItemHandler> handlers = new ArrayList<>(!machineOutputPorts.isEmpty() ? List.of() : areaBounds == null
                ? machine.recirculationOutputs(plan) : machine.recirculationOutputs(plan, areaBounds));
        java.util.Set<IItemHandler> unique = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(IItemHandler handler : handlers){
            unique.add(handler instanceof com.rieno.gadgetsandgizmos.lib.worker.WorkerItemOutput output
                    ? output.inventory() : handler);
        }
        List<BlockPos> outputs = machineOutputPorts.isEmpty()
                ? machine.areaOutputs(plan, areaBounds, areaOutputPositions) : areaOutputPositions;
        for(BlockPos output : outputs){
            Direction outputFace = machineOutputPorts.stream().filter(port -> port.pos().equals(output))
                    .map(WorkerMachineSite.Port::face).findFirst().orElse(null);
            for(IItemHandler handler : WorkerContainerAccess.itemHandlers(level, output, outputFace)){
                if(!unique.add(handler)) continue;
                handlers.add(new com.rieno.gadgetsandgizmos.lib.worker.WorkerItemOutput(handler,
                        stack -> com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog.recirculates(
                                level, plan, stack)));
            }
        }
        return handlers;
    }

    private List<IFluidHandler> outputFluidHandlers(){
        WorkerMachine machine = machine();
        if(machine == null) return fluidHandlers();
        if(machineOutputPorts.isEmpty()) return machine.fluidOutputs(activeRecipe);
        return machineOutputPorts.stream().flatMap(port -> WorkerContainerAccess.fluidHandlers(
                level, port.pos(), port.face()).stream()).toList();
    }

    // Get every NeoForge FE capability available to the worker
    private List<IEnergyStorage> energyStorages() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartBatteryBlockEntity battery) return List.of(battery.getEnergyHandler());
        return WorkerContainerAccess.energyStorages(level, pos, side);
    }

    // Get every loaded Item Vault member controlled by the linked vault block
    private List<BlockPos> connectedVaultBlocks(ItemVaultBlockEntity vault) {
        BlockPos controller = vault.getController();
        return connectedBlocks(vault.getClass(), controller,
                candidate -> candidate instanceof ItemVaultBlockEntity other
                        && controller.equals(other.getController()));
    }

    // Get every loaded Fluid Tank member controlled by the linked tank block
    private List<BlockPos> connectedTankBlocks(FluidTankBlockEntity tank) {
        BlockPos controller = tank.getController();
        return connectedBlocks(tank.getClass(), controller,
                candidate -> candidate instanceof FluidTankBlockEntity other
                        && controller.equals(other.getController()));
    }

    // Flood the connected multiblock without reaching separate adjacent storage blocks
    private List<BlockPos> connectedBlocks(Class<?> type, @Nullable BlockPos controller,
                                           java.util.function.Predicate<BlockEntity> member) {
        if (controller == null) return List.of(pos);
        List<BlockPos> blocks = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(pos);
        while (!pending.isEmpty() && blocks.size() < 81) {
            BlockPos candidate = pending.removeFirst();
            if (!visited.add(candidate) || !level.isLoaded(candidate)) continue;
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (blockEntity == null || blockEntity.getClass() != type || !member.test(blockEntity)) continue;
            blocks.add(candidate.immutable());
            for (Direction direction : Direction.values()) pending.addLast(candidate.relative(direction));
        }
        return blocks.isEmpty() ? List.of(pos) : List.copyOf(blocks);
    }

    // Get exact energy stored when the endpoint provides long-capacity storage
    private long exactStoredEnergy() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartBatteryBlockEntity battery) return battery.getLongEnergyStored();
        return energyStorages().stream().mapToLong(IEnergyStorage::getEnergyStored).sum();
    }

    // Get exact energy capacity when the endpoint provides long-capacity storage
    private long exactEnergyCapacity() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartBatteryBlockEntity battery) return battery.getLongCapacity();
        return energyStorages().stream().mapToLong(IEnergyStorage::getMaxEnergyStored).sum();
    }

    // Check whether this endpoint exposes any worker-compatible capability
    private boolean hasAnyCapability() {
        return machine() != null || !itemHandlers().isEmpty() || !fluidHandlers().isEmpty() || !energyStorages().isEmpty();
    }

    // Check whether automatic workers may use this storage without an explicit machine route
    private boolean isManagedStorage() {
        if(isProcessingMachine()) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if(ManifestTabletStorage.attached(level, pos)) return true;
        if (blockEntity instanceof SmartVaultBlockEntity
                || blockEntity instanceof SmartTankBlockEntity
                || blockEntity instanceof SmartBatteryBlockEntity) return true;
        return ShippingManifestBlockEntity.attachedResourceUses(level, pos) >= 0;
    }

    // Check whether the selected storage role permits a worker resource type
    private boolean allows(WorkerResourceType type) {
        return switch (type) {
            case ITEM -> allowsItems();
            case FLUID, FUEL -> allowsFluidResource(type);
            case ENERGY -> allowsEnergy();
        };
    }

    // Check whether items are selected for this storage
    private boolean allowsItems() {
        return (resourceUses() & ShippingManifestBlockEntity.USE_ITEMS) != 0;
    }

    // Check whether any fluid role is selected for this storage
    private boolean allowsFluids() {
        int uses = resourceUses();
        return (uses & (ShippingManifestBlockEntity.USE_FLUIDS
                | ShippingManifestBlockEntity.USE_FUEL)) != 0;
    }

    // Check whether one fluid resource type is selected for this storage
    private boolean allowsFluidResource(WorkerResourceType type) {
        int uses = resourceUses();
        return type == WorkerResourceType.FUEL
                ? (uses & ShippingManifestBlockEntity.USE_FUEL) != 0
                : (uses & ShippingManifestBlockEntity.USE_FLUIDS) != 0;
    }

    // Check whether FE is selected for this storage
    private boolean allowsEnergy() {
        return (resourceUses() & ShippingManifestBlockEntity.USE_ENERGY) != 0;
    }

    // Resolve manifest roles first, then smart storage's inherent role
    private int resourceUses() {
        int manifested = ShippingManifestBlockEntity.attachedResourceUses(level, pos);
        if (manifested >= 0) return manifested;
        if (isCraftingStation()) return ShippingManifestBlockEntity.USE_ITEMS;
        if(explicitResourceAccess || ManifestTabletStorage.attached(level, pos)) return detectedResourceUses();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartVaultBlockEntity) return ShippingManifestBlockEntity.USE_ITEMS;
        if (blockEntity instanceof SmartTankBlockEntity || blockEntity instanceof FluidTankBlockEntity) {
            return ShippingManifestBlockEntity.USE_FLUIDS;
        }
        if (blockEntity instanceof SmartBatteryBlockEntity) return ShippingManifestBlockEntity.USE_ENERGY;
        if (blockEntity instanceof ShipDockBlockEntity) {
            return ShippingManifestBlockEntity.USE_ITEMS
                    | ShippingManifestBlockEntity.USE_FLUIDS
                    | ShippingManifestBlockEntity.USE_FUEL
                    | ShippingManifestBlockEntity.USE_ENERGY;
        }
        return 0;
    }

    // Detect resources from a directly selected capability-bearing endpoint.
    private int detectedResourceUses() {
        int uses = 0;
        if (!itemHandlers().isEmpty()) uses |= ShippingManifestBlockEntity.USE_ITEMS;
        if (!fluidHandlers().isEmpty()) uses |= ShippingManifestBlockEntity.USE_FLUIDS;
        if (!energyStorages().isEmpty()) uses |= ShippingManifestBlockEntity.USE_ENERGY;
        return uses;
    }

    // Resolve block or face linker targets to their capability-bearing position
    private static List<LinkedPosition> linkedPositions(Level ownerLevel,
                                                        ContraptionNetworkLinkerData.LinkedTarget target) {
        BlockEntity exact = SimulatedHelper.findLoadedBlockEntityExact(
                ownerLevel, target.subLevelId(), target.blockPos());
        Level targetLevel = exact != null && exact.getLevel() != null ? exact.getLevel() : ownerLevel;
        BlockPos targetPos = exact == null ? target.blockPos() : exact.getBlockPos();
        if (!(targetLevel.getBlockState(targetPos).getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)
                || target.faces().isEmpty()) {
            return resolveStoragePositions(targetLevel, targetPos, null);
        }
        List<LinkedPosition> positions = new ArrayList<>();
        for (ContraptionNetworkLinkerData.LinkedFace face : target.faces()) {
            BlockPos attachedPos = targetPos.relative(face.face().getOpposite());
            if (WorkerContainerAccess.isLoaded(targetLevel, attachedPos) && !targetLevel.getBlockState(attachedPos).isAir()) {
                positions.addAll(resolveStoragePositions(targetLevel, attachedPos, face.face()));
            }
        }
        return positions;
    }

    // Resolve every machine access position through the reusable topology adapters
    private static List<LinkedPosition> expandProcessingPositions(List<LinkedPosition> positions){
        Map<String, LinkedPosition> resolved = new LinkedHashMap<>();
        for(LinkedPosition linked : positions){
            for(BlockPos pos : WorkerMachineRegistry.accessPositions(linked.level(), linked.pos())){
                if(!WorkerContainerAccess.isLoaded(linked.level(), pos)) continue;
                Direction side = pos.equals(linked.pos()) ? linked.side() : null;
                String key = linked.level().dimension().location() + ":" + pos.asLong() + ":" + side;
                resolved.putIfAbsent(key, new LinkedPosition(linked.level(), pos, side));
            }
        }
        return List.copyOf(resolved.values());
    }

    // Resolve a manifest target to its attached container so SCM links never target the paper interface itself
    private static List<LinkedPosition> resolveStoragePositions(Level level, BlockPos pos,
                                                                @Nullable Direction side) {
        if (level == null || pos == null || !WorkerContainerAccess.isLoaded(level, pos)) return List.of();
        BlockState state = level.getBlockState(pos);
        if(state.getBlock() instanceof DiagnosticTabletBlock){
            BlockPos attachedPos = pos.relative(state.getValue(DiagnosticTabletBlock.FACING).getOpposite());
            if(!WorkerContainerAccess.isLoaded(level, attachedPos)
                    || level.getBlockState(attachedPos).isAir()) return List.of();
            return List.of(new LinkedPosition(level, attachedPos, null));
        }
        if (!(state.getBlock() instanceof ShippingManifestBlock)) {
            return List.of(new LinkedPosition(level, pos, side));
        }
        BlockPos attachedPos = ShippingManifestBlock.attachedTargetPos(pos, state);
        if (!WorkerContainerAccess.isLoaded(level, attachedPos) || level.getBlockState(attachedPos).isAir()) return List.of();
        return List.of(new LinkedPosition(level, attachedPos,
                ShippingManifestBlock.attachedTargetSide(state)));
    }

    // Get available matching items
    private long availableItems(ResourceLocation itemId) {
        long amount = 0L;
        for (IItemHandler handler : outputItemHandlers()) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if(matches(stack, itemId)) amount += handler.extractItem(slot, stack.getCount(), true).getCount();
            }
        }
        return amount;
    }

    // Get matching item space
    private long itemSpace(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return 0L;
        if (!ShippingManifestBlockEntity.allowsAttachedContainerItem(level, pos, new ItemStack(item))) return 0L;
        long amount = 0L;
        for (IItemHandler handler : itemHandlers()) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack offered = new ItemStack(item, item.getDefaultMaxStackSize());
                amount += offered.getCount() - handler.insertItem(slot, offered, true).getCount();
            }
        }
        return amount;
    }

    // Extract matching items
    private WorkerResourcePacket extractItems(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        long remaining = Math.min(Integer.MAX_VALUE, maximumAmount);
        long extracted = 0L;
        ItemStack template = ItemStack.EMPTY;
        boolean machineOutput = machine() != null;
        boolean extractedThisPass;
        do {
            extractedThisPass = false;
            for (IItemHandler handler : outputItemHandlers()) {
                for (int slot = 0; slot < handler.getSlots() && remaining > 0L; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (!matches(stack, resource.id())) continue;
                    if (!template.isEmpty() && !ItemStack.isSameItemSameComponents(template, stack)) continue;
                    int beforeCount = stack.getCount();
                    ItemStack removed = handler.extractItem(slot, (int) Math.min(remaining, Integer.MAX_VALUE), simulate);
                    if (removed.isEmpty() || !matches(removed, resource.id())) continue;
                    if (!simulate && machineOutput && !WorkerOutputLedger.confirmsExtraction(removed.getCount(),
                            beforeCount, handler.getStackInSlot(slot).getCount())) continue;
                    if (template.isEmpty()) template = removed.copyWithCount(1);
                    extracted += removed.getCount();
                    remaining -= removed.getCount();
                    extractedThisPass = true;
                }
            }
        } while (!simulate && remaining > 0L && extractedThisPass);
        if (extracted <= 0L || template.isEmpty()) return WorkerResourcePacket.empty(resource);
        CompoundTag payload = new CompoundTag();
        payload.put("Stack", template.saveOptional(level.registryAccess()));
        return new WorkerResourcePacket(resource, extracted, payload);
    }

    @Override public WorkerResourcePacket extractMatchingItem(WorkerResourceKey resource, long maximumAmount,
                                                               java.util.function.Predicate<ItemStack> predicate,
                                                               boolean simulate){
        if(resource == null || resource.type() != WorkerResourceType.ITEM || maximumAmount <= 0L
                || predicate == null || !canExtract(resource)) return WorkerResourcePacket.empty(resource);
        for(IItemHandler handler : outputItemHandlers()){
            for(int slot = 0; slot < handler.getSlots(); slot++){
                ItemStack stored = handler.getStackInSlot(slot);
                if(!matches(stored, resource.id()) || !predicate.test(stored)) continue;
                int before = stored.getCount();
                ItemStack removed = handler.extractItem(slot, (int)Math.min(maximumAmount, before), simulate);
                if(removed.isEmpty()) continue;
                if(!simulate && machine() != null && !WorkerOutputLedger.confirmsExtraction(
                        removed.getCount(), before, handler.getStackInSlot(slot).getCount())) continue;
                CompoundTag payload = new CompoundTag();
                payload.put("Stack", removed.copyWithCount(1).saveOptional(level.registryAccess()));
                return new WorkerResourcePacket(resource, removed.getCount(), payload);
            }
        }
        return WorkerResourcePacket.empty(resource);
    }

    @Override public long availableMatchingItem(WorkerResourceKey resource,
                                                 java.util.function.Predicate<ItemStack> predicate){
        if(resource == null || resource.type() != WorkerResourceType.ITEM || predicate == null
                || !canExtract(resource)) return 0L;
        long total = 0L;
        for(IItemHandler handler : outputItemHandlers()){
            for(int slot = 0; slot < handler.getSlots(); slot++){
                ItemStack stack = handler.getStackInSlot(slot);
                if(matches(stack, resource.id()) && predicate.test(stack))
                    total += handler.extractItem(slot, stack.getCount(), true).getCount();
            }
        }
        return total;
    }

    // Insert matching items
    private long insertItems(WorkerResourcePacket packet, boolean simulate) {
        if(isCraftingStation()) return 0L;
        Item item = BuiltInRegistries.ITEM.get(packet.resource().id());
        if (item == null) return 0L;
        ItemStack template = packet.payload().contains("Stack")
                ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                : new ItemStack(item);
        if (template.isEmpty()) template = new ItemStack(item);
        if (!ShippingManifestBlockEntity.allowsAttachedContainerItem(level, pos, template)) return 0L;
        long remaining = packet.amount();
        for (IItemHandler handler : itemHandlers()) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0L; slot++) {
                int offeredAmount = (int) Math.min(template.getMaxStackSize(), remaining);
                ItemStack offered = template.copyWithCount(offeredAmount);
                ItemStack remainder = handler.insertItem(slot, offered, simulate);
                remaining -= offeredAmount - remainder.getCount();
            }
        }
        return packet.amount() - remaining;
    }

    // Get available matching fluid
    private long availableFluid(ResourceLocation fluidId) {
        long amount = 0L;
        for (IFluidHandler handler : outputFluidHandlers()) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty() && BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(fluidId)) {
                    amount += stack.getAmount();
                }
            }
        }
        return amount;
    }

    // Get matching fluid space
    private long fluidSpace(ResourceLocation fluidId) {
        var fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) return 0L;
        long amount = 0L;
        for (IFluidHandler handler : fluidHandlers()) {
            amount += handler.fill(new FluidStack(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
        }
        return amount;
    }

    // Extract matching fluid
    private WorkerResourcePacket extractFluid(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        var fluid = BuiltInRegistries.FLUID.get(resource.id());
        if (fluid == null) return WorkerResourcePacket.empty(resource);
        long remaining = Math.min(Integer.MAX_VALUE, maximumAmount);
        long extracted = 0L;
        boolean extractedThisPass;
        do {
            extractedThisPass = false;
            for (IFluidHandler handler : outputFluidHandlers()) {
                if (remaining <= 0L) break;
                FluidStack drained = handler.drain(new FluidStack(fluid, (int) remaining),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if (drained.isEmpty()) continue;
                extracted += drained.getAmount();
                remaining -= drained.getAmount();
                extractedThisPass = true;
            }
        } while (!simulate && remaining > 0L && extractedThisPass);
        return extracted <= 0L ? WorkerResourcePacket.empty(resource)
                : new WorkerResourcePacket(resource, extracted, new CompoundTag());
    }

    // Insert matching fluid
    private long insertFluid(WorkerResourcePacket packet, boolean simulate) {
        var fluid = BuiltInRegistries.FLUID.get(packet.resource().id());
        if (fluid == null) return 0L;
        long remaining = packet.amount();
        for (IFluidHandler handler : fluidHandlers()) {
            if (remaining <= 0L) break;
            int accepted = handler.fill(new FluidStack(fluid, (int) Math.min(Integer.MAX_VALUE, remaining)),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            remaining -= accepted;
        }
        return packet.amount() - remaining;
    }

    // Extract energy
    private WorkerResourcePacket extractEnergy(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        long remaining = Math.min(Integer.MAX_VALUE, maximumAmount);
        long extracted = 0L;
        boolean extractedThisPass;
        do {
            extractedThisPass = false;
            for (IEnergyStorage handler : energyStorages()) {
                if (remaining <= 0L) break;
                int moved = handler.extractEnergy((int) remaining, simulate);
                if (moved <= 0) continue;
                extracted += moved;
                remaining -= moved;
                extractedThisPass = true;
            }
        } while (!simulate && remaining > 0L && extractedThisPass);
        return new WorkerResourcePacket(resource, extracted, new CompoundTag());
    }

    // Insert energy
    private long insertEnergy(WorkerResourcePacket packet, boolean simulate) {
        long remaining = Math.min(Integer.MAX_VALUE, packet.amount());
        for (IEnergyStorage handler : energyStorages()) {
            if (remaining <= 0L) break;
            remaining -= handler.receiveEnergy((int) remaining, simulate);
        }
        return Math.min(Integer.MAX_VALUE, packet.amount()) - remaining;
    }

    // Check an item id
    private static boolean matches(ItemStack stack, ResourceLocation itemId) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId);
    }

    private record LinkedPosition(Level level, BlockPos pos, @Nullable Direction side) {
    }
}
