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
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpointSnapshot;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
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

// Adapt smart storage, Ship Docks and manifested standard storage for workers
public final class WorkerStorageEndpoint implements WorkerEndpoint {
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
    private final boolean extractionAllowed;
    private final boolean insertionAllowed;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the worker storage endpoint
    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side) {
        this(level, pos, side, null, "", "", "", true, true);
    }

    // Initialize an endpoint view with explicit worker access directions
    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side,
                                  boolean extractionAllowed, boolean insertionAllowed) {
        this(level, pos, side, null, "", "", "", extractionAllowed, insertionAllowed);
    }

    // Initialize a linked endpoint view
    private WorkerStorageEndpoint(Level level, BlockPos pos, @Nullable Direction side,
                                  @Nullable UUID subLevelId, String label, String blockId, String referenceId,
                                  boolean extractionAllowed, boolean insertionAllowed) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.subLevelId = subLevelId;
        this.label = label == null ? "" : label;
        this.blockId = blockId == null ? "" : blockId;
        this.referenceId = referenceId == null ? "" : referenceId;
        this.extractionAllowed = extractionAllowed;
        this.insertionAllowed = insertionAllowed;
        String key = level.dimension().location() + ":" + subLevelId + ":" + pos.asLong() + ":" + side;
        id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
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
                    || blockEntity instanceof ShipDockBlockEntity) {
                BlockPos pos = blockEntity.getBlockPos();
                endpoints.putIfAbsent(pos, new WorkerStorageEndpoint(level, pos, null,
                        SimulatedHelper.getContainingSubLevelId(blockEntity), "", "", "", true, true));
                continue;
            }
            if (!(blockEntity instanceof ShippingManifestBlockEntity)) continue;
            BlockState state = blockEntity.getBlockState();
            if (!(state.getBlock() instanceof ShippingManifestBlock)) continue;
            BlockPos targetPos = ShippingManifestBlock.attachedTargetPos(blockEntity.getBlockPos(), state);
            Direction targetSide = ShippingManifestBlock.attachedTargetSide(state);
            endpoints.putIfAbsent(targetPos, new WorkerStorageEndpoint(level, targetPos, targetSide,
                    SimulatedHelper.getContainingSubLevelId(blockEntity), "", "", "", true, true));
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
                subLevelId, label, blockId, reference, true, true);
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
                        true, true);
                if (!endpoint.hasAnyCapability()) continue;
                if (!includeMachines && !endpoint.isManagedStorage()) continue;
                endpoints.putIfAbsent(endpoint.id(), endpoint);
            }
        }
        for (BlockEntity blockEntity : ShipCargoAutomation.shipBlockEntities(controller)) {
            WorkerStorageEndpoint endpoint = managed(blockEntity);
            if (endpoint != null) endpoints.putIfAbsent(endpoint.id(), endpoint);
        }
        return List.copyOf(endpoints.values());
    }

    // Check whether this endpoint is a docking transfer buffer
    public boolean isDock() {
        return level.getBlockEntity(pos) instanceof ShipDockBlockEntity;
    }

    // Create a direction-restricted view for dispatching one delivery
    public WorkerStorageEndpoint withAccess(boolean allowExtraction, boolean allowInsertion) {
        return new WorkerStorageEndpoint(level, pos, side, subLevelId, label, blockId,
                referenceId, allowExtraction, allowInsertion);
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
            for (IItemHandler items : itemHandlers()) {
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
            for (IFluidHandler fluids : fluidHandlers()) {
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
                extractionAllowed, insertionAllowed, amounts);
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
        if (stack == null || stack.isEmpty() || !insertionAllowed || !allowsItems()) return false;
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
        return List.of(pos);
    }

    @Override
    public @Nullable Direction interactionFace() {
        return side;
    }

    @Override
    public boolean canExtract(WorkerResourceKey resource) {
        if (resource == null || !extractionAllowed || !allows(resource.type())) return false;
        return switch (resource.type()) {
            case ITEM -> !itemHandlers().isEmpty();
            case FLUID, FUEL -> !fluidHandlers().isEmpty();
            case ENERGY -> energyStorages().stream().anyMatch(IEnergyStorage::canExtract);
        };
    }

    @Override
    public boolean canInsert(WorkerResourceKey resource) {
        if (resource == null || !insertionAllowed || !allows(resource.type())) return false;
        return switch (resource.type()) {
            case ITEM -> !itemHandlers().isEmpty();
            case FLUID, FUEL -> !fluidHandlers().isEmpty();
            case ENERGY -> energyStorages().stream().anyMatch(IEnergyStorage::canReceive);
        };
    }

    @Override
    public long available(WorkerResourceKey resource) {
        if (resource == null || !allows(resource.type())) return 0L;
        return switch (resource.type()) {
            case ITEM -> availableItems(resource.id());
            case FLUID, FUEL -> availableFluid(resource.id());
            case ENERGY -> exactStoredEnergy();
        };
    }

    @Override
    public long space(WorkerResourceKey resource) {
        if (resource == null || !allows(resource.type())) return 0L;
        return switch (resource.type()) {
            case ITEM -> itemSpace(resource.id());
            case FLUID, FUEL -> fluidSpace(resource.id());
            case ENERGY -> Math.max(0L, exactEnergyCapacity() - exactStoredEnergy());
        };
    }

    @Override
    public WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        if (resource == null || maximumAmount <= 0L || !allows(resource.type())) return WorkerResourcePacket.empty(resource);
        return switch (resource.type()) {
            case ITEM -> extractItems(resource, maximumAmount, simulate);
            case FLUID, FUEL -> extractFluid(resource, maximumAmount, simulate);
            case ENERGY -> extractEnergy(resource, maximumAmount, simulate);
        };
    }

    @Override
    public long insert(WorkerResourcePacket packet, boolean simulate) {
        if (packet == null || packet.isEmpty() || !allows(packet.resource().type())) return 0L;
        return switch (packet.resource().type()) {
            case ITEM -> insertItems(packet, simulate);
            case FLUID, FUEL -> insertFluid(packet, simulate);
            case ENERGY -> insertEnergy(packet, simulate);
        };
    }

    // Get every NeoForge item capability available to the worker
    private List<IItemHandler> itemHandlers() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartVaultBlockEntity vault) return List.of(vault.getItemHandler());
        return WorkerContainerAccess.itemHandlers(level, pos, side);
    }

    // Get every NeoForge fluid capability available to the worker
    private List<IFluidHandler> fluidHandlers() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartTankBlockEntity tank) return List.of(tank.getFluidHandler());
        if (blockEntity instanceof FluidTankBlockEntity tank) return List.of(tank.getTankInventory());
        return WorkerContainerAccess.fluidHandlers(level, pos, side);
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
        return !itemHandlers().isEmpty() || !fluidHandlers().isEmpty() || !energyStorages().isEmpty();
    }

    // Check whether automatic workers may use this storage without an explicit machine route
    private boolean isManagedStorage() {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SmartVaultBlockEntity
                || blockEntity instanceof SmartTankBlockEntity
                || blockEntity instanceof SmartBatteryBlockEntity
                || blockEntity instanceof ShipDockBlockEntity) return true;
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
            if (targetLevel.isLoaded(attachedPos) && !targetLevel.getBlockState(attachedPos).isAir()) {
                positions.addAll(resolveStoragePositions(targetLevel, attachedPos, face.face()));
            }
        }
        return positions;
    }

    // Resolve a manifest target to its attached container so SCM links never target the paper interface itself
    private static List<LinkedPosition> resolveStoragePositions(Level level, BlockPos pos,
                                                                @Nullable Direction side) {
        if (level == null || pos == null || !level.isLoaded(pos)) return List.of();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ShippingManifestBlock)) {
            return List.of(new LinkedPosition(level, pos, side));
        }
        BlockPos attachedPos = ShippingManifestBlock.attachedTargetPos(pos, state);
        if (!level.isLoaded(attachedPos) || level.getBlockState(attachedPos).isAir()) return List.of();
        return List.of(new LinkedPosition(level, attachedPos,
                ShippingManifestBlock.attachedTargetSide(state)));
    }

    // Get available matching items
    private long availableItems(ResourceLocation itemId) {
        long amount = 0L;
        for (IItemHandler handler : itemHandlers()) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (matches(stack, itemId)) amount += stack.getCount();
            }
        }
        return amount;
    }

    // Get matching item space
    private long itemSpace(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return 0L;
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
        int maximum = (int) Math.min(Integer.MAX_VALUE, maximumAmount);
        for (IItemHandler handler : itemHandlers()) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!matches(stack, resource.id())) continue;
                ItemStack removed = handler.extractItem(slot, maximum, simulate);
                CompoundTag payload = new CompoundTag();
                if (!removed.isEmpty()) payload.put("Stack", removed.saveOptional(level.registryAccess()));
                return new WorkerResourcePacket(resource, removed.getCount(), payload);
            }
        }
        return WorkerResourcePacket.empty(resource);
    }

    // Insert matching items
    private long insertItems(WorkerResourcePacket packet, boolean simulate) {
        Item item = BuiltInRegistries.ITEM.get(packet.resource().id());
        if (item == null) return 0L;
        ItemStack template = packet.payload().contains("Stack")
                ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                : new ItemStack(item);
        if (template.isEmpty()) template = new ItemStack(item);
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
        for (IFluidHandler handler : fluidHandlers()) {
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
        for (IFluidHandler handler : fluidHandlers()) {
            FluidStack drained = handler.drain(new FluidStack(fluid,
                            (int) Math.min(Integer.MAX_VALUE, maximumAmount)),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) return new WorkerResourcePacket(resource, drained.getAmount(), new CompoundTag());
        }
        return WorkerResourcePacket.empty(resource);
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
        for (IEnergyStorage handler : energyStorages()) {
            if (remaining <= 0L) break;
            int moved = handler.extractEnergy((int) remaining, simulate);
            extracted += moved;
            remaining -= moved;
        }
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
