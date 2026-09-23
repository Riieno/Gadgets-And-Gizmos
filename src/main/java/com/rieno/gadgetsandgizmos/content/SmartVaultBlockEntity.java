package com.rieno.gadgetsandgizmos.content;

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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.List;

// Store twenty-five percent more item slots while retaining Create vault multiblocks
public class SmartVaultBlockEntity extends ItemVaultBlockEntity {
    // Initialize the smart vault block entity
    public SmartVaultBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SMART_VAULT.get(), pos, state);
        inventory = new ItemStackHandler(smartCapacity()) {
            @Override
            protected void onContentsChanged(int slot) {
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
        return new CombinedInvWrapper(handlers.toArray(IItemHandlerModifiable[]::new));
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
                    BlockEntity blockEntity = level.getBlockEntity(pos);
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
}
