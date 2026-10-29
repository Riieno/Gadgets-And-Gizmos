package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerDataSource;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.lib.inventory.LocalResourceStorage;
import com.rieno.gadgetsandgizmos.lib.inventory.IndexedItemHandler;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferItemStackHandler;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferMeter;
import com.rieno.gadgetsandgizmos.lib.inventory.TransferTrackedStorage;
import com.rieno.gadgetsandgizmos.lib.create.ConnectedVaultBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;
import com.rieno.gadgetsandgizmos.lib.tablet.MountedTabletState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

// Store twenty-five percent more item slots while retaining Create vault multiblocks
public class SmartVaultBlockEntity extends ConnectedVaultBlockEntity implements MountedTabletState.Host, LocalResourceStorage, TransferTrackedStorage, ContainerDataSource {
    @Override public List<BlockEntity> dataContainers(){
        return level != null && level.isClientSide ? List.of(this) : ContainerStorageNetworks.connected(this);
    }

    private final TransferMeter transferMeter = new TransferMeter(() -> level == null ? 0 : level.getGameTime());

    @Override public TransferMeter.Rates transferRates(){
        var root = getControllerBE() instanceof SmartVaultBlockEntity vault ? vault : this;
        var res = TransferMeter.Rates.ZERO;
        for(var member : root.memberInventories()){
            if(member instanceof TransferItemStackHandler tracked) res = res.plus(tracked.transferRates());
        }
        return res;
    }

    private final ContainerNetworkAccess networkResources = new ContainerNetworkAccess(this);
    private final IndexedItemHandler.Cache itemHandlers = new IndexedItemHandler.Cache();

    // Expose only the resource capability registered for this block's automation port
    public ContainerNetworkAccess networkResources(){ return networkResources; }

    private final com.rieno.gadgetsandgizmos.lib.inventory.LocalResourcePool localPool =
            new com.rieno.gadgetsandgizmos.lib.inventory.LocalResourcePool();
    @Override public ResourceRouting.Pool localResources(){
        return localPool.get(getItemHandler(), null, null);
    }

    private final MountedTabletState tabletState = new MountedTabletState();

    @Override public MountedTabletState mountedTabletState(){ return tabletState; }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.read(tag, provider, clientPacket);
        tabletState.read(tag);
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.write(tag, provider, clientPacket);
        tabletState.write(tag);
    }

    // Initialize the smart vault block entity
    public SmartVaultBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SMART_VAULT.get(), pos, state);
        inventory = new TransferItemStackHandler(smartCapacity(), transferMeter) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                setChanged();
                updateComparators();
            }
        };
    }

    // Get the complete multiblock item handler
    public IItemHandler getItemHandler() {
        SmartVaultBlockEntity controller = getControllerBE() instanceof SmartVaultBlockEntity smart
                ? smart : this;
        List<IItemHandlerModifiable> handlers = controller.memberInventories();
        if (handlers.isEmpty()) return inventory;
        return controller.itemHandlers.get(handlers);
    }

    // Get the multiblock inventory identity without Create's hard-coded vault type lookup
    @Override
    public InventoryIdentifier getInvId() {
        SmartVaultBlockEntity controller = getControllerBE() instanceof SmartVaultBlockEntity smart
                ? smart : this;
        Direction.Axis axis = controller.getMainConnectionAxis();
        BlockPos opposite = axis == Direction.Axis.Z
                ? controller.worldPosition.offset(controller.getWidth(),
                controller.getWidth(), controller.getHeight())
                : controller.worldPosition.offset(controller.getHeight(),
                controller.getWidth(), controller.getWidth());
        return new InventoryIdentifier.Bounds(BoundingBox.fromCorners(controller.worldPosition, opposite));
    }

    // Update the custom vault state after Create changes the multiblock geometry
    @Override
    public void notifyMultiUpdated() {
        updateLargeState(getWidth() > 2, 6);
        super.notifyMultiUpdated();
        SmartContainerTablets.inheritPresentation(this);
    }

    // Reset the custom vault state when Create splits the multiblock
    @Override
    public void removeController(boolean keepContents) {
        super.removeController(keepContents);
        updateLargeState(false, 22);
    }

    // Get all loaded multiblock inventories
    private List<IItemHandlerModifiable> memberInventories() {
        List<IItemHandlerModifiable> handlers = new ArrayList<>();
        if (level == null) return handlers;
        Direction.Axis axis = getMainConnectionAxis();
        for (int lengthIdx = 0; lengthIdx < getHeight(); lengthIdx++) {
            for (int firstWidthIdx = 0; firstWidthIdx < getWidth(); firstWidthIdx++) {
                for (int secondWidthIdx = 0; secondWidthIdx < getWidth(); secondWidthIdx++) {
                    BlockPos pos = axis == Direction.Axis.Z
                            ? worldPosition.offset(firstWidthIdx, secondWidthIdx, lengthIdx)
                            : worldPosition.offset(lengthIdx, secondWidthIdx, firstWidthIdx);
                    BlockEntity blockEntity = getLoadedMember(pos);
                    if (blockEntity instanceof SmartVaultBlockEntity smart) {
                        handlers.add(smart.inventory);
                    }
                }
            }
        }
        return handlers;
    }

    // Set the custom vault large-model property
    private void updateLargeState(boolean large, int flags) {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(ItemVaultBlock.LARGE) || state.getValue(ItemVaultBlock.LARGE) == large) return;
        level.setBlock(worldPosition, state.setValue(ItemVaultBlock.LARGE, large), flags);
    }

    // Get the per-block slot count
    private static int smartCapacity() {
        int standard = AllConfigs.server().logistics.vaultCapacity.get();
        return Math.max(1, (standard * 5 + 3) / 4);
    }
    // Include decorative panels spanning the edge of their owning member
    @Override
    public AABB getRenderBoundingBox(){
        return super.getRenderBoundingBox().inflate(1.0D);
    }
}
