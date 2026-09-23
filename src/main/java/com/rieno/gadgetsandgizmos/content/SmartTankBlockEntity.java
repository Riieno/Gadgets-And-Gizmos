package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

// Double Create's configured capacity for every block in a Smart Tank multiblock
public class SmartTankBlockEntity extends FluidTankBlockEntity {
    // Initialize the smart tank block entity
    public SmartTankBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SMART_TANK.get(), pos, state);
    }

    // Create the double-capacity inventory
    @Override
    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(getCapacityMultiplier() * 2, this::onFluidStackChanged);
    }

    // Apply the double-capacity multiblock size
    @Override
    public void applyFluidTankSize(int blocks) {
        super.applyFluidTankSize(Math.max(1, blocks) * 2);
    }

    // Get the per-block tank size
    @Override
    public int getTankSize(int tank) {
        return getCapacityMultiplier() * 2;
    }

    // Get the complete multiblock fluid handler
    public IFluidHandler getFluidHandler() {
        FluidTankBlockEntity controller = getControllerBE();
        return controller == null ? tankInventory : controller.getTankInventory();
    }

    // Restore the doubled capacity after Create deserializes its standard capacity
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (isController()) {
            tankInventory.setCapacity(getCapacityMultiplier() * getTotalTankSize() * 2);
        }
    }
}
