package com.rieno.gadgetsandgizmos.compat.createdieselgenerators;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Combine every tank in one Create Diesel Generators distillation tower for manifest transfers
public final class CreateDieselGeneratorsManifestCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String MOD_ID = "createdieselgenerators";
    private static final String DISTILLATION_TANK_BE =
            "com.jesz.createdieselgenerators.content.distillation.DistillationTankBlockEntity";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create diesel generators manifest compat
    private CreateDieselGeneratorsManifestCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a distillation tank
    public static boolean isDistillationTank(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return ModList.get().isLoaded(MOD_ID)
                && blockEntity != null
                && DISTILLATION_TANK_BE.equals(blockEntity.getClass().getName());
    }

    // Find the combined handler
    @Nullable
    public static IFluidHandler findCombinedHandler(Level level, BlockPos attachedLevelPos) {
        TowerLevel attachedLevel = getTowerLevel(level.getBlockEntity(attachedLevelPos));
        if (attachedLevel == null) {
            return null;
        }

        BlockPos bottomPos = attachedLevelPos;
        while (isSameTowerLevel(level.getBlockEntity(bottomPos.below()), attachedLevel)) {
            bottomPos = bottomPos.below();
        }

        List<IFluidHandler> handlers = new ArrayList<>();
        Set<BlockPos> controllerPositions = new HashSet<>();
        for (BlockPos currentPos = bottomPos;
             isSameTowerLevel(level.getBlockEntity(currentPos), attachedLevel);
             currentPos = currentPos.above()) {
            TowerLevel towerLevel = getTowerLevel(level.getBlockEntity(currentPos));
            if (towerLevel == null || !controllerPositions.add(towerLevel.controllerPos)) {
                continue;
            }
            IFluidHandler handler = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    towerLevel.controllerPos, null);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers.isEmpty() ? null : new CombinedFluidHandler(handlers);
    }

    // Check if this is on the same tower level
    private static boolean isSameTowerLevel(@Nullable BlockEntity blockEntity, TowerLevel attachedLevel) {
        TowerLevel candidate = getTowerLevel(blockEntity);
        return candidate != null
                && candidate.width == attachedLevel.width
                && candidate.controllerPos.getX() == attachedLevel.controllerPos.getX()
                && candidate.controllerPos.getZ() == attachedLevel.controllerPos.getZ();
    }

    // Get the tower level
    @Nullable
    private static TowerLevel getTowerLevel(@Nullable BlockEntity blockEntity) {
        if (!ModList.get().isLoaded(MOD_ID)
                || blockEntity == null
                || !DISTILLATION_TANK_BE.equals(blockEntity.getClass().getName())) {
            return null;
        }
        try {
            Method getController = blockEntity.getClass().getMethod("getController");
            Method getControllerBE = blockEntity.getClass().getMethod("getControllerBE");
            BlockPos controllerPos = (BlockPos) getController.invoke(blockEntity);
            Object controllerBE = getControllerBE.invoke(blockEntity);
            if (controllerPos == null || controllerBE == null) {
                return null;
            }
            Method getWidth = controllerBE.getClass().getMethod("getWidth");
            return new TowerLevel(controllerPos, (int) getWidth.invoke(controllerBE));
        } catch (ReflectiveOperationException | ClassCastException err) {
            return null;
        }
    }

    // Store the tower level
    private record TowerLevel(BlockPos controllerPos, int width) {
    }

    // Handle the combined fluid handler
    private static final class CombinedFluidHandler implements IFluidHandler {
        // Tracked tanks
        private final List<TankReference> tanks;

        // Initialize the combined fluid handler
        private CombinedFluidHandler(List<IFluidHandler> handlers) {
            List<TankReference> references = new ArrayList<>();
            for (IFluidHandler handler : handlers) {
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    references.add(new TankReference(handler, tank));
                }
            }
            tanks = List.copyOf(references);
        }

        // Get the tanks
        @Override
        public int getTanks() {
            return tanks.size();
        }

        // Get the fluid in tank
        @Override
        public FluidStack getFluidInTank(int tank) {
            TankReference reference = tanks.get(tank);
            return reference.handler.getFluidInTank(reference.tank);
        }

        // Get the tank capacity
        @Override
        public int getTankCapacity(int tank) {
            TankReference reference = tanks.get(tank);
            return reference.handler.getTankCapacity(reference.tank);
        }

        // Check if the fluid is valid
        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            TankReference reference = tanks.get(tank);
            return reference.handler.isFluidValid(reference.tank, stack);
        }

        // Fill the combined fluid handler
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        // Drain the combined fluid handler
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        // Drain the combined fluid handler
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

        // Store the tank reference
        private record TankReference(IFluidHandler handler, int tank) {
        }
    }
}
