package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.function.Consumer;

// Double Create's configured capacity for every block in a Smart Tank multiblock
public class SmartTankBlockEntity extends FluidTankBlockEntity{
    // Initialize the smart tank block entity
    public SmartTankBlockEntity(BlockPos pos, BlockState state){
        super(CTBlockEntities.SMART_TANK.get(), pos, state);
    }

    // Create the double-capacity inventory
    @Override
    protected SmartFluidTank createInventory(){
        return new DoubleCapacityFluidTank(getCapacityMultiplier(), this::onFluidStackChanged);
    }

    // Get the per-block tank size
    @Override
    public int getTankSize(int tank){
        return getCapacityMultiplier() * 2;
    }

    // Get the complete multiblock fluid handler
    public IFluidHandler getFluidHandler(){
        FluidTankBlockEntity controller = getControllerBE();
        return controller == null ? tankInventory : controller.getTankInventory();
    }

    // Keep Create's assembly, load, and sync capacity updates at double capacity
    private static final class DoubleCapacityFluidTank extends SmartFluidTank{
        // Initialize the double-capacity tank
        private DoubleCapacityFluidTank(int capacity, Consumer<FluidStack> callback){
            super(capacity * 2, callback);
        }

        // Double every capacity supplied by Create's multiblock lifecycle
        @Override
        public FluidTank setCapacity(int capacity){
            return super.setCapacity(capacity * 2);
        }
    }
}
