package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Hold the docking target, schedule identity and transfer state used by nearby ship pilots
public class ShipDockBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String BUFFER_ITEMS_TAG = "BufferItems";
    private static final String BUFFER_FLUID_TAG = "BufferFluid";
    private static final String BUFFER_ENERGY_TAG = "BufferEnergy";
    private static final int ITEM_SLOTS = 27;
    private static final int FLUID_CAPACITY = 64_000;
    private static final int ENERGY_CAPACITY = 1_000_000;
    private static final int ENERGY_TRANSFER = 16_384;
    private static final int MAX_CONNECTOR_SELECTIONS = 64;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current dock id
    private UUID dockId = UUID.randomUUID();
    // Current dock name
    private String dockName = "Ship Dock";
    // Tracks whether refuel is set
    private boolean refuel = true;
    // Tracks whether restock is set
    private boolean restock = true;
    // Tracks whether packages are set
    private boolean packages = true;
    // Tracks whether docked ship doors open
    private boolean doorControlEnabled;
    // Selected docked ship door directions
    private int doorControlMask = DoorDirection.allMask();
    // Tracks whether buffers empty is set
    private boolean buffersEmpty = true;
    // Tracked linked connectors
    private final List<ConnectorReference> linkedConnectors = new ArrayList<>();
    // Selected refueling connectors
    private final List<ConnectorReference> refuelConnectorSelection = new ArrayList<>();
    // Selected restocking connectors
    private final List<ConnectorReference> restockConnectorSelection = new ArrayList<>();
    // Selected package connectors
    private final List<ConnectorReference> packageConnectorSelection = new ArrayList<>();
    // Tracks whether refueling connector selection was explicitly configured
    private boolean refuelConnectorSelectionConfigured;
    // Tracks whether restocking connector selection was explicitly configured
    private boolean restockConnectorSelectionConfigured;
    // Tracks whether package connector selection was explicitly configured
    private boolean packageConnectorSelectionConfigured;
    // Tracked landing zones
    private final List<LandingZone> landingZones = new ArrayList<>();
    // Item buffer
    private final ItemStackHandler itemBuffer = new ItemStackHandler(ITEM_SLOTS) {
        // Handle the contents changed event
        @Override
        protected void onContentsChanged(int slot) {
            buffersEmpty = false;
            storageChanged();
        }
    };
    // Fluid buffer
    private final FluidTank fluidBuffer = new FluidTank(FLUID_CAPACITY) {
        // Handle the contents changed event
        @Override
        protected void onContentsChanged() {
            buffersEmpty = false;
            storageChanged();
        }
    };
    // Energy buffer
    private final DockEnergyStorage energyBuffer = new DockEnergyStorage();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock
    public ShipDockBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SHIP_DOCK.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Initialize the ship dock
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide && level.getServer() != null) {
            ShipDockRegistry.get(level.getServer()).update(this);
            refreshLinkedConnectorBindings();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the ship dock
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (buffersEmpty) {
            return;
        }
        pushBuffersToAdjacentConnectors();
        buffersEmpty = !hasBufferedResources();
    }

    // Check if this has buffered resources
    private boolean hasBufferedResources() {
        if (!fluidBuffer.isEmpty() || energyBuffer.getEnergyStored() > 0) {
            return true;
        }
        for (int slot = 0; slot < itemBuffer.getSlots(); slot++) {
            if (!itemBuffer.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Create the route record
    public @Nullable ShipDockRegistry.Dock createRouteRecord() {
        if (level == null || level.getServer() == null) {
            return null;
        }
        Object subLevel = SimulatedHelper.getContainingSubLevel(this);
        UUID subLevelId = SimulatedHelper.getSubLevelId(subLevel);
        Direction localFacing = getDockFacing();
        Vec3 localFacingVector = Vec3.atLowerCornerOf(localFacing.getNormal());
        Vec3 resolvedPosition = subLevel == null
                ? worldPosition.getCenter()
                : SimulatedHelper.toContainingWorldPosition(subLevel, worldPosition.getCenter());
        List<DockConnector> connectors = findDockingConnectors(subLevel, subLevelId);
        DockConnector connector = connectors.isEmpty() ? null : connectors.getFirst();
        List<ShipDockRegistry.ConnectorTarget> connectorTargets = connectors.stream()
                .map(val -> new ShipDockRegistry.ConnectorTarget(
                        val.subLevelId(), val.blockPosition(),
                        val.worldTipPosition(), val.worldFacing(), val.worldUp()))
                .toList();
        List<ShipDockRegistry.LandingZoneTarget> landingZoneTargets = landingZones.stream()
                .map(zone -> ShipDockRegistry.LandingZoneTarget.transformed(
                        zone.id(), zone.name(), zone.min(), zone.max(), zone.queueOrder(),
                        isLandingZoneAirborne(zone),
                        pos -> SimulatedHelper.toGlobalWorldPosition(this, pos)))
                .toList();
        return new ShipDockRegistry.Dock(
                dockId,
                level.dimension().location(),
                subLevelId,
                worldPosition,
                resolvedPosition == null ? worldPosition.getCenter() : resolvedPosition,
                localFacingVector,
                dockName,
                refuel,
                restock,
                packages,
                connector == null ? null : connector.subLevelId(),
                connector == null ? null : connector.blockPosition(),
                connector == null ? null : connector.worldTipPosition(),
                connector == null ? null : connector.worldFacing(),
                connector == null ? null : connector.worldUp(),
                System.currentTimeMillis(), connectorTargets, landingZoneTargets);
    }

    // Check if the landing zone is airborne
    private boolean isLandingZoneAirborne(LandingZone zone) {
        if (level == null || zone == null) {
            return false;
        }
        int supportY = zone.max().getY();
        for (int x = zone.min().getX(); x <= zone.max().getX(); x++) {
            for (int z = zone.min().getZ(); z <= zone.max().getZ(); z++) {
                BlockPos supportPos = new BlockPos(x, supportY, z);
                if (!level.isLoaded(supportPos)) {
                    return false;
                }
                BlockState support = level.getBlockState(supportPos);
                if (!support.getCollisionShape(level, supportPos).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    // Find the docking connectors
    private List<DockConnector> findDockingConnectors(
            @Nullable Object subLevel,
            @Nullable UUID subLevelId
    ) {
        if (level == null) {
            return List.of();
        }
        List<DockConnector> resolved = new ArrayList<>();
        for (int index = 0; index < linkedConnectors.size(); index++) {
            ConnectorReference linked = linkedConnectors.get(index);
            BlockEntity candidate = SimulatedHelper.findLoadedBlockEntityExact(
                    level, linked.subLevelId(), linked.blockPosition());
            if (candidate == null) {
                if (linked.subLevelId() != null) {
                    SubLevelBlockEntityCollector.ensureTargetLoaded(
                            level, linked.subLevelId(), linked.blockPosition());
                } else {
                    level.getChunkAt(linked.blockPosition());
                }
                candidate = SimulatedHelper.findLoadedBlockEntityExact(
                        level, linked.subLevelId(), linked.blockPosition());
            }
            if (isDockingConnector(candidate)) {
                DockingConnectorAutomation.bindToShipDock(candidate, dockId, dockName, index);
                Object connectorSubLevel = SimulatedHelper.getContainingSubLevel(candidate);
                resolved.add(connectorRecord(candidate, connectorSubLevel, linked.subLevelId()));
            }
        }
        if (!resolved.isEmpty()) {
            return List.copyOf(resolved);
        }
        List<BlockEntity> candidates = new ArrayList<>();
        if (subLevel != null) {
            candidates.addAll(SubLevelBlockEntityCollector.getBlockEntities(subLevel));
        } else {
            for (BlockEntity candidate : SubLevelBlockEntityCollector
                    .getLoadedWorldBlockEntities(level, worldPosition, 1)) {
                BlockPos candidatePos = candidate.getBlockPos();
                if (Math.abs(candidatePos.getX() - worldPosition.getX()) <= 16
                        && Math.abs(candidatePos.getY() - worldPosition.getY()) <= 16
                        && Math.abs(candidatePos.getZ() - worldPosition.getZ()) <= 16) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates.stream()
                .filter(this::isDockingConnector)
                .filter(candidate -> java.util.Objects.equals(
                        subLevelId, SimulatedHelper.getContainingSubLevelId(candidate)))
                .sorted(Comparator.comparingDouble(candidate ->
                        candidate.getBlockPos().distSqr(worldPosition)))
                .map(candidate -> connectorRecord(candidate, subLevel, subLevelId))
                .limit(1)
                .toList();
    }

    // Check if this is a docking connector
    private boolean isDockingConnector(BlockEntity candidate) {
        if (candidate == null || candidate.getBlockState() == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(
                candidate.getBlockState().getBlock());
        return id != null && "simulated".equals(id.getNamespace())
                && "docking_connector".equals(id.getPath());
    }

    // Get the connector record
    private DockConnector connectorRecord(
            BlockEntity connector,
            @Nullable Object subLevel,
            @Nullable UUID subLevelId
    ) {
        Direction dir = connector.getBlockState().hasProperty(BlockStateProperties.FACING)
                ? connector.getBlockState().getValue(BlockStateProperties.FACING)
                : Direction.NORTH;
        Vec3 localFacing = Vec3.atLowerCornerOf(dir.getNormal());
        Vec3 localUp = ShipDockRegistry.Dock.connectorUpForFacing(localFacing);
        Vec3 localTip = connector.getBlockPos().getCenter().add(localFacing.scale(1.5D));
        Vec3 worldTip = subLevel == null ? localTip
                : SimulatedHelper.toContainingWorldPosition(subLevel, localTip);
        Vec3 worldFacing = subLevel == null ? localFacing
                : SimulatedHelper.toContainingWorldDirection(subLevel, localFacing);
        Vec3 worldUp = subLevel == null ? localUp
                : SimulatedHelper.toContainingWorldDirection(subLevel, localUp);
        return new DockConnector(subLevelId, connector.getBlockPos(),
                worldTip == null ? localTip : worldTip,
                worldFacing == null ? localFacing : worldFacing,
                worldUp == null ? localUp : worldUp);
    }

    // Store the dock connector
    private record DockConnector(
            @Nullable UUID subLevelId,
            BlockPos blockPosition,
            Vec3 worldTipPosition,
            Vec3 worldFacing,
            Vec3 worldUp
    ) {
    }

    // Limit a door setting to the supported directions
    public static int sanitizeDoorControlMask(int mask) {
        return mask & DoorDirection.allMask();
    }

    // Get the selected linked connectors from a client configuration
    private List<ConnectorReference> selectedLinkedConnectors(List<ConnectorReference> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<ConnectorReference> selected = new ArrayList<>();
        for (ConnectorReference reference : requested) {
            if (reference == null || !linkedConnectors.contains(reference)
                    || selected.contains(reference)) {
                continue;
            }
            selected.add(reference);
            if (selected.size() >= MAX_CONNECTOR_SELECTIONS) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    // Replace one connector selection
    private static void replaceSelection(
            List<ConnectorReference> target,
            List<ConnectorReference> source
    ) {
        target.clear();
        target.addAll(source);
    }

    // Get an effective connector selection
    private List<ConnectorReference> effectiveConnectorSelection(
            List<ConnectorReference> selected,
            boolean configured
    ) {
        return List.copyOf(configured ? selected : linkedConnectors);
    }

    // Reconcile connector selections with the current linked connector list
    private void normalizeConnectorSelections() {
        normalizeConnectorSelection(refuelConnectorSelection, refuelConnectorSelectionConfigured);
        normalizeConnectorSelection(restockConnectorSelection, restockConnectorSelectionConfigured);
        normalizeConnectorSelection(packageConnectorSelection, packageConnectorSelectionConfigured);
    }

    // Reconcile one connector selection
    private void normalizeConnectorSelection(
            List<ConnectorReference> selected,
            boolean configured
    ) {
        selected.removeIf(reference -> !linkedConnectors.contains(reference));
        if (!configured) {
            replaceSelection(selected, linkedConnectors);
        }
    }

    // Write connector references to an NBT list
    private static ListTag connectorReferenceTag(List<ConnectorReference> references) {
        ListTag tag = new ListTag();
        for (int index = 0; index < references.size() && index < MAX_CONNECTOR_SELECTIONS; index++) {
            ConnectorReference reference = references.get(index);
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", reference.blockPosition().asLong());
            if (reference.subLevelId() != null) {
                entry.putUUID("SubLevel", reference.subLevelId());
            }
            tag.add(entry);
        }
        return tag;
    }

    // Read connector references from an NBT list
    private static void readConnectorReferences(
            CompoundTag tag,
            String key,
            List<ConnectorReference> target
    ) {
        target.clear();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size() && index < MAX_CONNECTOR_SELECTIONS; index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.contains("Pos", Tag.TAG_LONG)) {
                continue;
            }
            ConnectorReference reference = new ConnectorReference(
                    entry.hasUUID("SubLevel") ? entry.getUUID("SubLevel") : null,
                    BlockPos.of(entry.getLong("Pos")));
            if (!target.contains(reference)) {
                target.add(reference);
            }
        }
    }

    // Configure the ship dock while retaining connector and door settings
    public void configure(String name, boolean refuel, boolean restock, boolean packages) {
        configureInternal(name, refuel, restock, packages, doorControlEnabled, doorControlMask,
                false, List.of(), List.of(), List.of());
    }

    // Configure the ship dock
    public void configure(String name, boolean refuel, boolean restock, boolean packages,
                          boolean doorControlEnabled, int doorControlMask,
                          List<ConnectorReference> refuelConnectors,
                          List<ConnectorReference> restockConnectors,
                          List<ConnectorReference> packageConnectors) {
        configureInternal(name, refuel, restock, packages, doorControlEnabled, doorControlMask,
                true, refuelConnectors, restockConnectors, packageConnectors);
    }

    // Apply the ship dock configuration
    private void configureInternal(String name, boolean refuel, boolean restock, boolean packages,
                                   boolean doorControlEnabled, int doorControlMask,
                                   boolean updateConnectorSelections,
                                   List<ConnectorReference> refuelConnectors,
                                   List<ConnectorReference> restockConnectors,
                                   List<ConnectorReference> packageConnectors) {
        String normalized = name == null ? "" : name.trim();
        String nextName = normalized.isEmpty() ? "Ship Dock"
                : normalized.substring(0, Math.min(64, normalized.length()));
        int nextDoorControlMask = sanitizeDoorControlMask(doorControlMask);
        List<ConnectorReference> nextRefuelConnectors = updateConnectorSelections
                ? selectedLinkedConnectors(refuelConnectors) : List.copyOf(refuelConnectorSelection);
        List<ConnectorReference> nextRestockConnectors = updateConnectorSelections
                ? selectedLinkedConnectors(restockConnectors) : List.copyOf(restockConnectorSelection);
        List<ConnectorReference> nextPackageConnectors = updateConnectorSelections
                ? selectedLinkedConnectors(packageConnectors) : List.copyOf(packageConnectorSelection);
        if (dockName.equals(nextName) && this.refuel == refuel
                && this.restock == restock && this.packages == packages
                && this.doorControlEnabled == doorControlEnabled
                && this.doorControlMask == nextDoorControlMask
                && (!updateConnectorSelections || (refuelConnectorSelectionConfigured
                && restockConnectorSelectionConfigured && packageConnectorSelectionConfigured
                && refuelConnectorSelection.equals(nextRefuelConnectors)
                && restockConnectorSelection.equals(nextRestockConnectors)
                && packageConnectorSelection.equals(nextPackageConnectors)))) {
            return;
        }
        this.dockName = nextName;
        this.refuel = refuel;
        this.restock = restock;
        this.packages = packages;
        this.doorControlEnabled = doorControlEnabled;
        this.doorControlMask = nextDoorControlMask;
        if (updateConnectorSelections) {
            refuelConnectorSelectionConfigured = true;
            restockConnectorSelectionConfigured = true;
            packageConnectorSelectionConfigured = true;
            replaceSelection(refuelConnectorSelection, nextRefuelConnectors);
            replaceSelection(restockConnectorSelection, nextRestockConnectors);
            replaceSelection(packageConnectorSelection, nextPackageConnectors);
        }
        setChanged();
        sendData();
        refreshLinkedConnectorBindings();
        if (level != null && level.getServer() != null) {
            ShipDockRegistry.get(level.getServer()).update(this);
        }
    }

    // Get the dock id
    public UUID getDockId() {
        return dockId;
    }

    // Get the dock name
    public String getDockName() {
        return dockName;
    }

    // Get the shipping telemetry
    public List<ShipDockRegistry.ShipTelemetry> getShippingTelemetry() {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return List.of();
        }
        return ShipDockRegistry.get(level.getServer()).telemetry(dockId);
    }

    // Check if this can refuel
    public boolean canRefuel() {
        return refuel;
    }

    // Check if this can restock
    public boolean canRestock() {
        return restock;
    }

    // Check if this can handle packages
    public boolean canHandlePackages() {
        return packages;
    }

    // Check whether docked ship doors should open
    public boolean isDoorControlEnabled() {
        return doorControlEnabled;
    }

    // Get the selected docked ship door directions
    public int getDoorControlMask() {
        return doorControlMask;
    }

    // Check whether the dock opens the selected door direction
    public boolean opensDoor(DoorDirection direction) {
        return direction != null && (doorControlMask & direction.mask()) != 0;
    }

    // Get the linked docking connectors
    public List<ConnectorReference> linkedConnectorReferences() {
        return List.copyOf(linkedConnectors);
    }

    // Add a docking connector to this Ship Dock
    public boolean addLinkedDockingConnector(ConnectorReference connector) {
        if (connector == null || linkedConnectors.contains(connector)) return false;
        linkedConnectors.add(connector);
        normalizeConnectorSelections();
        storageChanged();
        if (level != null && level.getServer() != null) {
            ShipDockRegistry.get(level.getServer()).update(this);
        }
        refreshLinkedConnectorBindings();
        return true;
    }

    // Remove a docking connector from this Ship Dock
    public boolean removeLinkedDockingConnector(ConnectorReference connector) {
        return removeLinkedDockingConnector(connector, true);
    }

    // Remove a docking connector without rediscovering it before the block is removed
    boolean removeLinkedDockingConnector(ConnectorReference connector, boolean updateRegistry) {
        if (connector == null || !linkedConnectors.remove(connector)) return false;
        normalizeConnectorSelections();
        storageChanged();
        refreshLinkedConnectorBindings();
        if (updateRegistry && level != null && level.getServer() != null) {
            ShipDockRegistry.get(level.getServer()).update(this);
        }
        return true;
    }

    // Clear this Ship Dock's connector bindings
    public void clearLinkedConnectorBindings() {
        clearLinkedConnectorBindings(List.copyOf(linkedConnectors));
    }

    // Get the selected refueling connectors
    public List<ConnectorReference> refuelConnectorReferences() {
        return effectiveConnectorSelection(refuelConnectorSelection,
                refuelConnectorSelectionConfigured);
    }

    // Get the selected restocking connectors
    public List<ConnectorReference> restockConnectorReferences() {
        return effectiveConnectorSelection(restockConnectorSelection,
                restockConnectorSelectionConfigured);
    }

    // Get the selected package connectors
    public List<ConnectorReference> packageConnectorReferences() {
        return effectiveConnectorSelection(packageConnectorSelection,
                packageConnectorSelectionConfigured);
    }

    // Get the landing zones
    public List<LandingZone> landingZones() {
        return List.copyOf(landingZones);
    }

    // Add the landing zone
    public LandingZone addLandingZone(BlockPos first, BlockPos second) {
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        LandingZone zone = new LandingZone(UUID.randomUUID(),
                "Landing Zone " + (landingZones.size() + 1), min, max,
                landingZones.size());
        landingZones.add(zone);
        landingZones.sort(Comparator.comparingInt(LandingZone::queueOrder));
        landingZonesChanged();
        return zone;
    }

    // Remove the landing zone
    public boolean removeLandingZone(UUID id) {
        boolean removed = landingZones.removeIf(zone -> zone.id().equals(id));
        if (removed) landingZonesChanged();
        return removed;
    }

    // Adjust the landing zone
    public boolean adjustLandingZone(UUID id, Direction face, int amount) {
        for (int idx = 0; idx < landingZones.size(); idx++) {
            LandingZone zone = landingZones.get(idx);
            if (!zone.id().equals(id)) continue;
            BlockPos min = zone.min();
            BlockPos max = zone.max();
            BlockPos nextMin = min;
            BlockPos nextMax = max;
            switch (face) {
                case WEST -> nextMin = new BlockPos(Math.min(max.getX(), min.getX() + amount), min.getY(), min.getZ());
                case EAST -> nextMax = new BlockPos(Math.max(min.getX(), max.getX() + amount), max.getY(), max.getZ());
                case DOWN -> nextMin = new BlockPos(min.getX(), Math.min(max.getY(), min.getY() + amount), min.getZ());
                case UP -> nextMax = new BlockPos(max.getX(), Math.max(min.getY(), max.getY() + amount), max.getZ());
                case NORTH -> nextMin = new BlockPos(min.getX(), min.getY(), Math.min(max.getZ(), min.getZ() + amount));
                case SOUTH -> nextMax = new BlockPos(max.getX(), max.getY(), Math.max(min.getZ(), max.getZ() + amount));
            }
            landingZones.set(idx, new LandingZone(zone.id(), zone.name(), nextMin, nextMax,
                    zone.queueOrder()));
            landingZonesChanged();
            return true;
        }
        return false;
    }

    // Handle the landing zones changed
    private void landingZonesChanged() {
        storageChanged();
        if (level != null && level.getServer() != null) {
            ShipDockRegistry.get(level.getServer()).update(this);
        }
    }

    // Get the dock facing
    public Direction getDockFacing() {
        return getBlockState().hasProperty(ShipDockBlock.FACING)
                ? getBlockState().getValue(ShipDockBlock.FACING) : Direction.NORTH;
    }

    // Get the item buffer
    public IItemHandler getItemBuffer() {
        return itemBuffer;
    }

    // Get the fluid buffer
    public IFluidHandler getFluidBuffer() {
        return fluidBuffer;
    }

    // Get the energy buffer
    public IEnergyStorage getEnergyBuffer() {
        return energyBuffer;
    }

    // Check if this has package in buffer
    public boolean hasPackageInBuffer() {
        for (int slot = 0; slot < itemBuffer.getSlots(); slot++) {
            if (PackageItem.isPackage(itemBuffer.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    // Get the buffered items
    public List<ItemStack> bufferedItems() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < itemBuffer.getSlots(); slot++) {
            ItemStack stack = itemBuffer.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return List.copyOf(stacks);
    }

    // Apply the linked connectors
    public void applyLinkedConnectors(ItemStack stack) {
        List<ConnectorReference> previous = List.copyOf(linkedConnectors);
        linkedConnectors.clear();
        CompoundTag customData = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
        ListTag entries = customData.getList(
                ShipDockBlockItem.LINKED_CONNECTORS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < entries.size(); idx++) {
            CompoundTag entry = entries.getCompound(idx);
            if (!entry.contains(ShipDockBlockItem.LINKED_CONNECTOR_POS_TAG, Tag.TAG_LONG)) {
                continue;
            }
            UUID subLevelId = entry.hasUUID(ShipDockBlockItem.LINKED_CONNECTOR_SUBLEVEL_TAG)
                    ? entry.getUUID(ShipDockBlockItem.LINKED_CONNECTOR_SUBLEVEL_TAG) : null;
            ConnectorReference linked = new ConnectorReference(
                    subLevelId,
                    BlockPos.of(entry.getLong(ShipDockBlockItem.LINKED_CONNECTOR_POS_TAG)));
            if (!linkedConnectors.contains(linked)) {
                linkedConnectors.add(linked);
            }
        }
        normalizeConnectorSelections();
        clearLinkedConnectorBindings(previous);
        storageChanged();
        refreshLinkedConnectorBindings();
        if (level != null && level.getServer() != null) {
            // An item-applied connector list is an explicit edit, including an intentional
            // empty list. Normal load-time registry refreshes retain persisted links until their
            // linked sublevels finish restoring.
            ShipDockRegistry.get(level.getServer()).update(this);
        }
    }

    // Refresh the linked connector bindings
    private void refreshLinkedConnectorBindings() {
        if (level == null || level.isClientSide) return;
        for (int index = 0; index < linkedConnectors.size(); index++) {
            ConnectorReference connector = linkedConnectors.get(index);
            DockingConnectorAutomation.bindToShipDock(
                    SimulatedHelper.findLoadedBlockEntityExact(
                            level, connector.subLevelId(), connector.blockPosition()),
                    dockId, dockName, index);
        }
    }

    // Clear bindings from the supplied connector references
    private void clearLinkedConnectorBindings(List<ConnectorReference> connectors) {
        if (level == null || level.isClientSide) return;
        for (ConnectorReference connector : connectors) {
            DockingConnectorAutomation.clearShipDockBinding(
                    SimulatedHelper.findLoadedBlockEntityExact(
                            level, connector.subLevelId(), connector.blockPosition()), dockId);
        }
    }

    // Handle the storage changed
    private void storageChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    // Push the buffers to adjacent connectors
    private void pushBuffersToAdjacentConnectors() {
        Set<BlockEntity> targets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ConnectorReference linked : linkedConnectors) {
            BlockEntity target = SimulatedHelper.findLoadedBlockEntityExact(
                    level, linked.subLevelId(), linked.blockPosition());
            if (DockingConnectorAutomation.isDockingConnector(target)
                    && DockingConnectorAutomation.isLocked(target)) {
                targets.add(target);
            }
        }
        for (Direction side : Direction.values()) {
            BlockPos targetPos = worldPosition.relative(side);
            BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);
            if (DockingConnectorAutomation.isDockingConnector(targetBlockEntity)
                    && DockingConnectorAutomation.isLocked(targetBlockEntity)) {
                targets.add(targetBlockEntity);
            }
        }
        for (BlockEntity targetBlockEntity : targets) {
            if (targetBlockEntity.getLevel() == null) {
                continue;
            }
            BlockPos targetPos = targetBlockEntity.getBlockPos();
            IItemHandler targetItems = targetBlockEntity.getLevel().getCapability(
                    Capabilities.ItemHandler.BLOCK, targetPos,
                    targetBlockEntity.getBlockState(), targetBlockEntity, null);
            IFluidHandler targetFluids = targetBlockEntity.getLevel().getCapability(
                    Capabilities.FluidHandler.BLOCK, targetPos,
                    targetBlockEntity.getBlockState(), targetBlockEntity, null);
            IEnergyStorage targetEnergy = targetBlockEntity.getLevel().getCapability(
                    Capabilities.EnergyStorage.BLOCK, targetPos,
                    targetBlockEntity.getBlockState(), targetBlockEntity, null);
            pushItems(targetItems);
            pushFluid(targetFluids);
            pushEnergy(targetEnergy);
        }
    }

    // Push the items
    private void pushItems(@Nullable IItemHandler target) {
        if (target == null) {
            return;
        }
        for (int sourceSlot = 0; sourceSlot < itemBuffer.getSlots(); sourceSlot++) {
            ItemStack available = itemBuffer.extractItem(sourceSlot, 64, true);
            if (available.isEmpty()) {
                continue;
            }
            ItemStack remainder = insertItem(target, available, true);
            int accepted = available.getCount() - remainder.getCount();
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = itemBuffer.extractItem(sourceSlot, accepted, false);
            ItemStack unaccepted = insertItem(target, extracted, false);
            if (!unaccepted.isEmpty()) {
                itemBuffer.insertItem(sourceSlot, unaccepted, false);
            }
        }
    }

    // Insert the item
    private static ItemStack insertItem(IItemHandler target, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < target.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = target.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    // Push the fluid
    private void pushFluid(@Nullable IFluidHandler target) {
        if (target == null || fluidBuffer.isEmpty()) {
            return;
        }
        FluidStack available = fluidBuffer.getFluid().copy();
        int accepted = target.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return;
        }
        FluidStack drained = fluidBuffer.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        int inserted = target.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (inserted < drained.getAmount()) {
            fluidBuffer.fill(drained.copyWithAmount(drained.getAmount() - inserted),
                    IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // Push the energy
    private void pushEnergy(@Nullable IEnergyStorage target) {
        if (target == null || !target.canReceive() || energyBuffer.getEnergyStored() <= 0) {
            return;
        }
        int offered = Math.min(ENERGY_TRANSFER, energyBuffer.getEnergyStored());
        int accepted = target.receiveEnergy(offered, true);
        if (accepted <= 0) {
            return;
        }
        int extracted = energyBuffer.extractEnergy(accepted, false);
        int inserted = target.receiveEnergy(extracted, false);
        if (inserted < extracted) {
            energyBuffer.restore(extracted - inserted);
        }
    }

    // Write the ship dock
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putUUID("DockId", dockId);
        tag.putString("DockName", dockName);
        tag.putBoolean("Refuel", refuel);
        tag.putBoolean("Restock", restock);
        tag.putBoolean("Packages", packages);
        tag.putBoolean("DoorControlEnabled", doorControlEnabled);
        tag.putInt("DoorControlMask", doorControlMask);
        tag.put(BUFFER_ITEMS_TAG, itemBuffer.serializeNBT(provider));
        tag.put(BUFFER_FLUID_TAG, fluidBuffer.writeToNBT(provider, new CompoundTag()));
        tag.putInt(BUFFER_ENERGY_TAG, energyBuffer.getEnergyStored());
        ListTag linkedTag = new ListTag();
        for (ConnectorReference linked : linkedConnectors) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", linked.blockPosition().asLong());
            if (linked.subLevelId() != null) {
                entry.putUUID("SubLevel", linked.subLevelId());
            }
            linkedTag.add(entry);
        }
        tag.put("LinkedConnectors", linkedTag);
        tag.putBoolean("RefuelConnectorSelectionConfigured", refuelConnectorSelectionConfigured);
        tag.putBoolean("RestockConnectorSelectionConfigured", restockConnectorSelectionConfigured);
        tag.putBoolean("PackageConnectorSelectionConfigured", packageConnectorSelectionConfigured);
        tag.put("RefuelConnectorSelection", connectorReferenceTag(refuelConnectorSelection));
        tag.put("RestockConnectorSelection", connectorReferenceTag(restockConnectorSelection));
        tag.put("PackageConnectorSelection", connectorReferenceTag(packageConnectorSelection));
        ListTag zoneTags = new ListTag();
        for (LandingZone zone : landingZones) zoneTags.add(zone.toTag());
        tag.put("LandingZones", zoneTags);
    }

    // Read the ship dock
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (tag.hasUUID("DockId")) {
            dockId = tag.getUUID("DockId");
        }
        if (tag.contains("DockName")) {
            dockName = tag.getString("DockName");
        }
        refuel = !tag.contains("Refuel") || tag.getBoolean("Refuel");
        restock = !tag.contains("Restock") || tag.getBoolean("Restock");
        packages = !tag.contains("Packages") || tag.getBoolean("Packages");
        doorControlEnabled = tag.getBoolean("DoorControlEnabled");
        doorControlMask = tag.contains("DoorControlMask", Tag.TAG_INT)
                ? sanitizeDoorControlMask(tag.getInt("DoorControlMask"))
                : DoorDirection.fromLegacyName(tag.getString("DoorControl"));
        if (tag.contains(BUFFER_ITEMS_TAG, Tag.TAG_COMPOUND)) {
            itemBuffer.deserializeNBT(provider, tag.getCompound(BUFFER_ITEMS_TAG));
        }
        if (tag.contains(BUFFER_FLUID_TAG, Tag.TAG_COMPOUND)) {
            fluidBuffer.readFromNBT(provider, tag.getCompound(BUFFER_FLUID_TAG));
        }
        energyBuffer.setStored(tag.getInt(BUFFER_ENERGY_TAG));
        buffersEmpty = !hasBufferedResources();
        linkedConnectors.clear();
        ListTag linkedTag = tag.getList("LinkedConnectors", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < linkedTag.size(); idx++) {
            CompoundTag entry = linkedTag.getCompound(idx);
            if (!entry.contains("Pos", Tag.TAG_LONG)) {
                continue;
            }
            linkedConnectors.add(new ConnectorReference(
                    entry.hasUUID("SubLevel") ? entry.getUUID("SubLevel") : null,
                    BlockPos.of(entry.getLong("Pos"))));
        }
        refuelConnectorSelectionConfigured = tag.getBoolean("RefuelConnectorSelectionConfigured");
        restockConnectorSelectionConfigured = tag.getBoolean("RestockConnectorSelectionConfigured");
        packageConnectorSelectionConfigured = tag.getBoolean("PackageConnectorSelectionConfigured");
        readConnectorReferences(tag, "RefuelConnectorSelection", refuelConnectorSelection);
        readConnectorReferences(tag, "RestockConnectorSelection", restockConnectorSelection);
        readConnectorReferences(tag, "PackageConnectorSelection", packageConnectorSelection);
        normalizeConnectorSelections();
        landingZones.clear();
        ListTag zoneTags = tag.getList("LandingZones", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < zoneTags.size(); idx++) {
            LandingZone zone = LandingZone.fromTag(zoneTags.getCompound(idx));
            if (zone != null) landingZones.add(zone);
        }
        landingZones.sort(Comparator.comparingInt(LandingZone::queueOrder));
    }

    // Store the docked ship door directions
    public enum DoorDirection {
        NORTH(1),
        SOUTH(2),
        EAST(4),
        WEST(8);

        private final int mask;

        // Initialize the direction
        DoorDirection(int mask) {
            this.mask = mask;
        }

        // Get the direction mask
        public int mask() {
            return mask;
        }

        // Get the mask containing every door direction
        public static int allMask() {
            return NORTH.mask | SOUTH.mask | EAST.mask | WEST.mask;
        }

        // Migrate the previous left/right door setting
        private static int fromLegacyName(String value) {
            if (value == null || value.isBlank()) {
                return allMask();
            }
            return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "LEFT" -> WEST.mask;
                case "RIGHT" -> EAST.mask;
                case "BOTH" -> EAST.mask | WEST.mask;
                default -> allMask();
            };
        }
    }

    // Store the landing zone
    public record LandingZone(UUID id, String name, BlockPos min, BlockPos max, int queueOrder) {
        // Initialize the landing zone
        public LandingZone {
            id = id == null ? UUID.randomUUID() : id;
            name = name == null || name.isBlank() ? "Landing Zone" : name;
            min = min == null ? BlockPos.ZERO : min.immutable();
            max = max == null ? min : max.immutable();
            queueOrder = Math.max(0, queueOrder);
        }

        // Get the center
        public Vec3 center() {
            return new Vec3((min.getX() + max.getX() + 1.0D) * 0.5D,
                    max.getY() + 1.0D, (min.getZ() + max.getZ() + 1.0D) * 0.5D);
        }

        // Write the landing zone data
        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Name", name);
            tag.putLong("Min", min.asLong());
            tag.putLong("Max", max.asLong());
            tag.putInt("QueueOrder", queueOrder);
            return tag;
        }

        // Read the landing zone data
        static LandingZone fromTag(CompoundTag tag) {
            if (!tag.hasUUID("Id") || !tag.contains("Min", Tag.TAG_LONG)
                    || !tag.contains("Max", Tag.TAG_LONG)) return null;
            return new LandingZone(tag.getUUID("Id"), tag.getString("Name"),
                    BlockPos.of(tag.getLong("Min")), BlockPos.of(tag.getLong("Max")),
                    tag.getInt("QueueOrder"));
        }
    }

    // Store a dock connector reference
    public record ConnectorReference(@Nullable UUID subLevelId, BlockPos blockPosition) {
        // Initialize the connector reference
        public ConnectorReference {
            blockPosition = (blockPosition == null ? BlockPos.ZERO : blockPosition).immutable();
        }
    }

    // Handle the dock energy storage
    private final class DockEnergyStorage extends EnergyStorage {
        // Initialize the dock energy storage
        private DockEnergyStorage() {
            super(ENERGY_CAPACITY, ENERGY_TRANSFER, ENERGY_TRANSFER);
        }

        // Receive the energy
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                buffersEmpty = false;
                storageChanged();
            }
            return received;
        }

        // Extract the energy
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) {
                storageChanged();
            }
            return extracted;
        }

        // Restore the dock energy storage
        private void restore(int amount) {
            energy = Math.min(capacity, energy + Math.max(0, amount));
            storageChanged();
        }

        // Set the stored
        private void setStored(int amount) {
            energy = Math.max(0, Math.min(capacity, amount));
        }
    }
}
