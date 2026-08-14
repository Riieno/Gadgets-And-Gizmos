package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.util.OxidizedFuel;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.OpenEndedPipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Use kinetic speed to oxidize fluid fuel between either pair of axial connections
public class FuelOxidizerBlockEntity extends KineticBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int TANK_CAPACITY = 4000;
    private static final int MAX_TRANSFER_PER_TICK = 1000;
    private static final String HAS_FLOW_DIRECTION_KEY = "HasFlowDirection";
    private static final String INPUT_FROM_FRONT_KEY = "InputFromFront";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Input tank
    private final FluidTank inputTank = createTank(OxidizedFuel::canOxidize);
    // Output tank
    private final FluidTank outputTank = createTank(OxidizedFuel::isOxidized);
    // Combined handler
    private final IFluidHandler combinedHandler = new CombinedHandler();
    // Fluid handlers for each queried side
    private final Map<Direction, IFluidHandler> sideHandlers = new EnumMap<>(Direction.class);
    // Tracked open pipe outputs
    private final Map<OpenEnd, OpenEndedPipe> openPipeOutputs = new HashMap<>();
    // Active end used for raw fuel input
    private boolean hasFlowDirection;
    // Whether raw fuel currently enters through the front
    private boolean isInputFromFront;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the fuel oxidizer
    public FuelOxidizerBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.FUEL_OXIDIZER.get(), pos, state);
        for (Direction side : Direction.values()) {
            sideHandlers.put(side, new SideHandler(side));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the fuel oxidizer
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        pullFuel();
        if (getSpeed() != 0.0F) {
            oxidizeFuel();
        }
        pushFuel();
        clearFlowDirectionIfEmpty();
    }

    // Get the fluid handler
    public IFluidHandler getFluidHandler(Direction side) {
        if (side == null) {
            return combinedHandler;
        }
        Direction front = getBlockState().getValue(FuelOxidizerBlock.FACING);
        if (side == front || side == front.getOpposite()) {
            return sideHandlers.get(side);
        }
        return null;
    }

    // Create the tank
    private FluidTank createTank(java.util.function.Predicate<FluidStack> validator) {
        return new FluidTank(TANK_CAPACITY, validator) {
            // Handle the contents changed event
            @Override
            protected void onContentsChanged() {
                setChanged();
                if (level != null) {
                    sendData();
                }
            }
        };
    }

    // Get the transfer rate
    private int transferRate() {
        return Math.max(1, Math.min(MAX_TRANSFER_PER_TICK, (int) Math.abs(getSpeed()) * 4));
    }

    // Oxidize the fuel
    private void oxidizeFuel() {
        FluidStack input = inputTank.getFluid();
        if (!OxidizedFuel.canOxidize(input)) {
            return;
        }

        FluidStack oxidized = OxidizedFuel.oxidize(input.copyWithAmount(Math.min(transferRate(), input.getAmount())));
        int accepted = OxidizedFuelStorageAccess.allow(() ->
                outputTank.fill(oxidized, IFluidHandler.FluidAction.SIMULATE));
        if (accepted <= 0) {
            return;
        }

        FluidStack drained = inputTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        OxidizedFuelStorageAccess.allow(() ->
                outputTank.fill(OxidizedFuel.oxidize(drained), IFluidHandler.FluidAction.EXECUTE));
    }

    // Pull fuel from either end
    private void pullFuel() {
        if (hasFlowDirection) {
            pullFuelFrom(getInputSide());
            return;
        }

        Direction front = getBlockState().getValue(FuelOxidizerBlock.FACING);
        if (!pullFuelFrom(front.getOpposite())) {
            pullFuelFrom(front);
        }
        if (!hasFlowDirection && (!inputTank.isEmpty() || !outputTank.isEmpty())) {
            setInputSide(front.getOpposite());
        }
    }

    // Pull raw fuel from one end
    private boolean pullFuelFrom(Direction side) {
        IFluidHandler src = level.getCapability(
                Capabilities.FluidHandler.BLOCK, worldPosition.relative(side), side.getOpposite());
        if (src == null) {
            return false;
        }

        FluidStack preview = src.drain(transferRate(), IFluidHandler.FluidAction.SIMULATE);
        if (!OxidizedFuel.canOxidize(preview)) {
            return false;
        }
        int accepted = inputTank.fill(preview, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return false;
        }

        FluidStack drained = src.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
        int filled = inputTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return false;
        }
        setInputSide(side);
        return true;
    }

    // Push oxidized fuel through the opposite end
    private void pushFuel() {
        if (outputTank.isEmpty()) {
            return;
        }
        if (!hasFlowDirection) {
            Direction front = getBlockState().getValue(FuelOxidizerBlock.FACING);
            setInputSide(front.getOpposite());
        }

        Direction outputSide = getInputSide().getOpposite();
        BlockPos outputPos = worldPosition.relative(outputSide);
        IFluidHandler target = level.getCapability(
                Capabilities.FluidHandler.BLOCK, outputPos, outputSide.getOpposite());
        if (target != null) {
            transferTo(target, transferRate());
            return;
        }

        FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, outputPos);
        if (pipe != null && pipe.canHaveFlowToward(level.getBlockState(outputPos), outputSide.getOpposite())) {
            pushThroughPipeNetwork(outputPos, transferRate());
            return;
        }

        if (FluidPropagator.isOpenEnd(level, worldPosition, outputSide)) {
            transferToOpenEnd(new OpenEnd(worldPosition, outputSide), transferRate());
        }
    }

    // Get the active raw fuel input side
    private Direction getInputSide() {
        Direction front = getBlockState().getValue(FuelOxidizerBlock.FACING);
        return isInputFromFront ? front : front.getOpposite();
    }

    // Select the active raw fuel input side
    private void setInputSide(Direction side) {
        Direction front = getBlockState().getValue(FuelOxidizerBlock.FACING);
        if (side != front && side != front.getOpposite()) {
            return;
        }
        boolean inputFromFront = side == front;
        if (hasFlowDirection && isInputFromFront == inputFromFront) {
            return;
        }
        hasFlowDirection = true;
        isInputFromFront = inputFromFront;
        setChanged();
    }

    // Clear the route once both buffers are empty
    private void clearFlowDirectionIfEmpty() {
        if (!hasFlowDirection || !inputTank.isEmpty() || !outputTank.isEmpty()) {
            return;
        }
        hasFlowDirection = false;
        setChanged();
    }

    // Fill the raw fuel buffer from one side
    private int fillInput(Direction side, FluidStack resource, IFluidHandler.FluidAction action) {
        if (side != null && hasFlowDirection && side != getInputSide()) {
            return 0;
        }
        int filled = inputTank.fill(resource, action);
        if (filled > 0 && action == IFluidHandler.FluidAction.EXECUTE) {
            Direction inputSide = side == null
                    ? getBlockState().getValue(FuelOxidizerBlock.FACING).getOpposite()
                    : side;
            setInputSide(inputSide);
        }
        return filled;
    }

    // Drain oxidized fuel through one side
    private FluidStack drainOutput(Direction side, FluidStack resource, IFluidHandler.FluidAction action) {
        if (side != null && hasFlowDirection && side == getInputSide()) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = outputTank.drain(resource, action);
        updateFlowAfterDrain(side, drained, action);
        return drained;
    }

    // Drain an amount of oxidized fuel through one side
    private FluidStack drainOutput(Direction side, int maxDrain, IFluidHandler.FluidAction action) {
        if (side != null && hasFlowDirection && side == getInputSide()) {
            return FluidStack.EMPTY;
        }
        FluidStack drained = outputTank.drain(maxDrain, action);
        updateFlowAfterDrain(side, drained, action);
        return drained;
    }

    // Track the inferred input after output extraction
    private void updateFlowAfterDrain(Direction side, FluidStack drained, IFluidHandler.FluidAction action) {
        if (drained.isEmpty() || action != IFluidHandler.FluidAction.EXECUTE) {
            return;
        }
        if (!hasFlowDirection) {
            Direction inputSide = side == null
                    ? getBlockState().getValue(FuelOxidizerBlock.FACING).getOpposite()
                    : side.getOpposite();
            setInputSide(inputSide);
        }
        clearFlowDirectionIfEmpty();
    }

    // Push fuel through the pipe network
    private void pushThroughPipeNetwork(BlockPos start, int maxAmount) {
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visitedPipes = new HashSet<>();
        Set<OpenEnd> visitedEnds = new HashSet<>();
        frontier.add(start);
        int remaining = maxAmount;

        while (!frontier.isEmpty() && remaining > 0) {
            BlockPos pipePos = frontier.removeFirst();
            if (!visitedPipes.add(pipePos)) {
                continue;
            }

            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pipePos);
            if (pipe == null) {
                continue;
            }

            BlockState pipeState = level.getBlockState(pipePos);
            for (Direction side : FluidPropagator.getPipeConnections(pipeState, pipe)) {
                if (remaining <= 0) {
                    break;
                }

                BlockPos connectedPos = pipePos.relative(side);
                if (connectedPos.equals(worldPosition)) {
                    continue;
                }

                FluidTransportBehaviour connectedPipe = FluidPropagator.getPipe(level, connectedPos);
                if (connectedPipe != null
                        && connectedPipe.canHaveFlowToward(level.getBlockState(connectedPos), side.getOpposite())) {
                    frontier.addLast(connectedPos);
                    continue;
                }

                IFluidHandler target = level.getCapability(Capabilities.FluidHandler.BLOCK, connectedPos, side.getOpposite());
                if (target != null) {
                    remaining -= transferTo(target, remaining);
                    continue;
                }

                if (FluidPropagator.isOpenEnd(level, pipePos, side)) {
                    OpenEnd end = new OpenEnd(pipePos, side);
                    if (visitedEnds.add(end)) {
                        remaining -= transferToOpenEnd(end, remaining);
                    }
                }
            }
        }
    }

    // Transfer fuel to the target tank
    private int transferTo(IFluidHandler target, int maxAmount) {
        if (maxAmount <= 0 || outputTank.isEmpty()) {
            return 0;
        }

        FluidStack offered = outputTank.getFluid().copyWithAmount(Math.min(maxAmount, outputTank.getFluidAmount()));
        int accepted = target.fill(offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }

        int filled = target.fill(offered.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            outputTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        }
        return filled;
    }

    // Transfer fuel to an open pipe end
    private int transferToOpenEnd(OpenEnd end, int maxAmount) {
        OpenEndedPipe openPipe = openPipeOutputs.computeIfAbsent(end,
                key -> new OpenEndedPipe(new BlockFace(key.pipePos(), key.side())));
        openPipe.manageSource(level, this);
        IFluidHandler handler = openPipe.provideHandler().getCapability();
        return handler == null ? 0 : transferTo(handler, maxAmount);
    }

    // Read the fuel oxidizer
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        inputTank.readFromNBT(provider, tag.getCompound("InputTank"));
        outputTank.readFromNBT(provider, tag.getCompound("OutputTank"));
        hasFlowDirection = tag.getBoolean(HAS_FLOW_DIRECTION_KEY);
        isInputFromFront = tag.getBoolean(INPUT_FROM_FRONT_KEY);
    }

    // Write the fuel oxidizer
    @Override
    public void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.put("InputTank", inputTank.writeToNBT(provider, new CompoundTag()));
        tag.put("OutputTank", outputTank.writeToNBT(provider, new CompoundTag()));
        tag.putBoolean(HAS_FLOW_DIRECTION_KEY, hasFlowDirection);
        tag.putBoolean(INPUT_FROM_FRONT_KEY, isInputFromFront);
    }

    // Route fluid through one axial side
    private final class SideHandler implements IFluidHandler {
        // Queried world side
        private final Direction side;

        // Initialize the side handler
        private SideHandler(Direction side) {
            this.side = side;
        }

        // Get the tanks
        @Override public int getTanks() { return 2; }
        // Get the fluid in tank
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? inputTank.getFluid() : outputTank.getFluid(); }
        // Get the tank capacity
        @Override public int getTankCapacity(int tank) { return TANK_CAPACITY; }
        // Check if the fluid is valid
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && inputTank.isFluidValid(stack); }
        // Fill raw fuel through this side
        @Override public int fill(FluidStack resource, FluidAction action) { return fillInput(side, resource, action); }
        // Drain matching oxidized fuel through this side
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return drainOutput(side, resource, action); }
        // Drain oxidized fuel through this side
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return drainOutput(side, maxDrain, action); }
    }

    // Handle the combined handler
    private final class CombinedHandler implements IFluidHandler {
        // Get the tanks
        @Override public int getTanks() { return 2; }
        // Get the fluid in tank
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? inputTank.getFluid() : outputTank.getFluid(); }
        // Get the tank capacity
        @Override public int getTankCapacity(int tank) { return TANK_CAPACITY; }
        // Check if the fluid is valid
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && inputTank.isFluidValid(stack); }
        // Fill the combined handler
        @Override public int fill(FluidStack resource, FluidAction action) { return fillInput(null, resource, action); }
        // Drain matching fluid from the combined output
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return drainOutput(null, resource, action); }
        // Drain an amount from the combined output
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return drainOutput(null, maxDrain, action); }
    }

    // Store the open end
    private record OpenEnd(BlockPos pipePos, Direction side) {
    }
}
