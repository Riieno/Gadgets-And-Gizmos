package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

// Provide Create fluid-tank connectivity for the double-capacity Smart Tank
public class SmartTankBlock extends FluidTankBlock {
    // Initialize the smart tank block
    public SmartTankBlock(BlockBehaviour.Properties properties) {
        super(properties, false);
    }

    // Get the block entity type
    @Override
    public BlockEntityType<? extends FluidTankBlockEntity> getBlockEntityType() {
        return CTBlockEntities.SMART_TANK.get();
    }

    // Get the block entity class
    @Override
    public Class<FluidTankBlockEntity> getBlockEntityClass() {
        return FluidTankBlockEntity.class;
    }
}
