package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// Route Forge Energy across every connected cable and balance it between attached machines
public class AndesiteCableBlockEntity extends BlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PASSIVE_TRANSFER_PER_TICK = 256;
    private static final int PASSIVE_TRANSFER_INTERVAL_TICKS = 2;
    private static final ThreadLocal<Set<AndesiteCableBlockEntity>> ROUTING_GUARD = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Map<Level, PassiveTransferFrame> PASSIVE_TRANSFER_FRAMES = new WeakHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked sided views
    private final EnumMap<Direction, IEnergyStorage> sidedViews = new EnumMap<>(Direction.class);
    // Internal view
    private final IEnergyStorage internalView = new CableEnergyView(null);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the andesite cable
    public AndesiteCableBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.ANDESITE_CABLE.get(), pos, blockState);
        for (Direction dir : Direction.values()) {
            sidedViews.put(dir, new CableEnergyView(dir));
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the energy view
    public IEnergyStorage getEnergyView(@Nullable Direction side) {
        if (side == null) {
            return internalView;
        }
        return sidedViews.get(side);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the andesite cable
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, AndesiteCableBlockEntity blockEntity) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (level.getGameTime() % PASSIVE_TRANSFER_INTERVAL_TICKS != 0L) {
            return;
        }
        PassiveTransferFrame frame = getPassiveTransferFrame(level);
        if (frame.processedCablePositions.contains(pos)) {
            return;
        }
        blockEntity.runPassiveTransferStep(frame.processedCablePositions);
    }

    // Get the passive transfer frame
    private static PassiveTransferFrame getPassiveTransferFrame(Level level) {
        PassiveTransferFrame frame = PASSIVE_TRANSFER_FRAMES.computeIfAbsent(level, ignored -> new PassiveTransferFrame());
        long gameTime = level.getGameTime();
        if (frame.gameTime != gameTime) {
            frame.gameTime = gameTime;
            frame.processedCablePositions.clear();
        }
        return frame;
    }

    // Run the passive transfer step
    private void runPassiveTransferStep(Set<BlockPos> processedCablePositions) {
        if (level == null || !enterRoutingGuard()) {
            return;
        }

        int budget = PASSIVE_TRANSFER_PER_TICK;
        boolean movedAny = false;
        try {
            Set<BlockPos> network = collectCableNetwork();
            processedCablePositions.addAll(network);
            List<EnergyEndpoint> sources = collectExternalEndpoints(network, null, EndpointMode.EXTRACT);
            List<EnergyEndpoint> targets = collectExternalEndpoints(network, null, EndpointMode.RECEIVE);
            for (EnergyEndpoint src : sources) {
                if (budget <= 0) {
                    break;
                }
                int offered = src.storage.extractEnergy(budget, true);
                if (offered <= 0) {
                    continue;
                }
                int accepted = offerToEndpoints(targets, src.key, offered, true);
                if (accepted <= 0) {
                    continue;
                }
                int extracted = src.storage.extractEnergy(accepted, false);
                if (extracted <= 0) {
                    continue;
                }
                int delivered = offerToEndpoints(targets, src.key, extracted, false);
                if (delivered > 0) {
                    movedAny = true;
                    budget -= delivered;
                }
            }
        } finally {
            exitRoutingGuard();
        }

        if (movedAny) {
            setChanged();
        }
    }

    // Route the receive
    private int routeReceive(@Nullable Direction sourceSide, int maxReceive, boolean simulate) {
        if (level == null || maxReceive <= 0 || !enterRoutingGuard()) {
            return 0;
        }

        int moved;
        try {
            Set<BlockPos> network = collectCableNetwork();
            List<EnergyEndpoint> targets = collectExternalEndpoints(network, sourceSide, EndpointMode.RECEIVE);
            moved = offerToEndpoints(targets, null, maxReceive, simulate);
        } finally {
            exitRoutingGuard();
        }

        if (moved > 0 && !simulate) {
            setChanged();
        }
        return moved;
    }

    // Route the extract
    private int routeExtract(@Nullable Direction sourceSide, int maxExtract, boolean simulate) {
        if (level == null || maxExtract <= 0 || !enterRoutingGuard()) {
            return 0;
        }

        int pulled = 0;
        try {
            Set<BlockPos> network = collectCableNetwork();
            List<EnergyEndpoint> sources = collectExternalEndpoints(network, sourceSide, EndpointMode.EXTRACT);
            int remaining = maxExtract;
            for (EnergyEndpoint src : sources) {
                if (remaining <= 0) {
                    break;
                }
                int extracted = src.storage.extractEnergy(remaining, simulate);
                if (extracted <= 0) {
                    continue;
                }
                pulled += extracted;
                remaining -= extracted;
            }
        } finally {
            exitRoutingGuard();
        }

        if (pulled > 0 && !simulate) {
            setChanged();
        }
        return pulled;
    }

    // Combine the energy stored
    private int aggregateEnergyStored() {
        if (level == null || !enterRoutingGuard()) {
            return 0;
        }

        int total = 0;
        try {
            Set<BlockPos> network = collectCableNetwork();
            List<EnergyEndpoint> endpoints = collectExternalEndpoints(network, null, EndpointMode.ANY);
            for (EnergyEndpoint endpoint : endpoints) {
                total += Math.max(0, endpoint.storage.getEnergyStored());
            }
        } finally {
            exitRoutingGuard();
        }
        return total;
    }

    // Combine the energy capacity
    private int aggregateEnergyCapacity() {
        if (level == null || !enterRoutingGuard()) {
            return 0;
        }

        int total = 0;
        try {
            Set<BlockPos> network = collectCableNetwork();
            List<EnergyEndpoint> endpoints = collectExternalEndpoints(network, null, EndpointMode.ANY);
            for (EnergyEndpoint endpoint : endpoints) {
                total += Math.max(0, endpoint.storage.getMaxEnergyStored());
            }
        } finally {
            exitRoutingGuard();
        }
        return total;
    }

    // Collect the cable network
    private Set<BlockPos> collectCableNetwork() {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(worldPosition);
        queue.add(worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (level == null || !level.isLoaded(current)) {
                continue;
            }
            BlockState state = level.getBlockState(current);
            if (!(state.getBlock() instanceof AndesiteCableBlock)) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                if (!AndesiteCableBlock.isConnected(state, dir)) {
                    continue;
                }
                BlockPos neighborPos = current.relative(dir);
                if (!level.isLoaded(neighborPos)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighborPos);
                if (!(neighborState.getBlock() instanceof AndesiteCableBlock)) {
                    continue;
                }
                if (visited.add(neighborPos)) {
                    queue.add(neighborPos);
                }
            }
        }
        return visited;
    }

    // Collect the external endpoints
    private List<EnergyEndpoint> collectExternalEndpoints(Set<BlockPos> network, @Nullable Direction excludedSideOnOrigin,
                                                          EndpointMode mode) {
        List<EnergyEndpoint> endpoints = new ArrayList<>();
        Set<EndpointKey> seen = new HashSet<>();

        for (BlockPos cablePos : network) {
            if (level == null || !level.isLoaded(cablePos)) {
                continue;
            }
            BlockState state = level.getBlockState(cablePos);
            if (!(state.getBlock() instanceof AndesiteCableBlock)) {
                continue;
            }

            for (Direction dir : Direction.values()) {
                if (cablePos.equals(worldPosition) && dir == excludedSideOnOrigin) {
                    continue;
                }
                if (!AndesiteCableBlock.isConnected(state, dir)) {
                    continue;
                }

                BlockPos endpointPos = cablePos.relative(dir);
                if (network.contains(endpointPos) || !level.isLoaded(endpointPos)) {
                    continue;
                }

                Direction endpointSide = dir.getOpposite();
                EndpointKey key = new EndpointKey(endpointPos, endpointSide);
                if (!seen.add(key)) {
                    continue;
                }

                IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, endpointPos, endpointSide);
                if (storage == null || !mode.accepts(storage)) {
                    continue;
                }
                endpoints.add(new EnergyEndpoint(key, storage));
            }
        }
        return endpoints;
    }

    // Get the offer to endpoints
    private static int offerToEndpoints(List<EnergyEndpoint> endpoints, @Nullable EndpointKey excludedKey,
                                        int amount, boolean simulate) {
        int remaining = amount;
        int moved = 0;
        for (EnergyEndpoint endpoint : endpoints) {
            if (remaining <= 0) {
                break;
            }
            if (endpoint.key.equals(excludedKey)) {
                continue;
            }
            int accepted = endpoint.storage.receiveEnergy(remaining, simulate);
            if (accepted <= 0) {
                continue;
            }
            moved += accepted;
            remaining -= accepted;
        }
        return moved;
    }

    // Enter the routing guard
    private boolean enterRoutingGuard() {
        return ROUTING_GUARD.get().add(this);
    }

    // Exit the routing guard
    private void exitRoutingGuard() {
        ROUTING_GUARD.get().remove(this);
    }

    // Define the endpoint mode values
    private enum EndpointMode {
        RECEIVE {
            // Check if this accepts the value
            @Override
            boolean accepts(IEnergyStorage storage) {
                return storage.canReceive();
            }
        },
        EXTRACT {
            // Check if this accepts the value
            @Override
            boolean accepts(IEnergyStorage storage) {
                return storage.canExtract();
            }
        },
        ANY {
            // Check if this accepts the value
            @Override
            boolean accepts(IEnergyStorage storage) {
                return true;
            }
        };

        // Check if this accepts the value
        abstract boolean accepts(IEnergyStorage storage);
    }

    // Handle the energy endpoint
    private static final class EnergyEndpoint {
        // Key
        private final EndpointKey key;
        // Storage
        private final IEnergyStorage storage;

        // Initialize the energy endpoint
        private EnergyEndpoint(EndpointKey key, IEnergyStorage storage) {
            this.key = key;
            this.storage = storage;
        }
    }

    // Handle the endpoint key
    private static final class EndpointKey {
        // Endpoint key position
        private final BlockPos pos;
        // Side
        private final Direction side;

        // Initialize the endpoint key
        private EndpointKey(BlockPos pos, Direction side) {
            this.pos = pos;
            this.side = side;
        }

        // Compare this endpoint key with another object
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof EndpointKey other)) {
                return false;
            }
            return pos.equals(other.pos) && side == other.side;
        }

        // Generate the endpoint key hash
        @Override
        public int hashCode() {
            return 31 * pos.hashCode() + side.hashCode();
        }
    }

    // Handle the passive transfer frame
    private static final class PassiveTransferFrame {
        // Current game time
        private long gameTime = Long.MIN_VALUE;
        // Tracked processed cable positions
        private final Set<BlockPos> processedCablePositions = new HashSet<>();
    }

    // Handle the cable energy view
    private class CableEnergyView implements IEnergyStorage {
        // Source side
        @Nullable
        private final Direction sourceSide;

        // Initialize the cable energy view
        private CableEnergyView(@Nullable Direction sourceSide) {
            this.sourceSide = sourceSide;
        }

        // Receive the energy
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return routeReceive(sourceSide, maxReceive, simulate);
        }

        // Extract the energy
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return routeExtract(sourceSide, maxExtract, simulate);
        }

        // Get the energy stored
        @Override
        public int getEnergyStored() {
            return aggregateEnergyStored();
        }

        // Get the max energy stored
        @Override
        public int getMaxEnergyStored() {
            return aggregateEnergyCapacity();
        }

        // Check if this can extract
        @Override
        public boolean canExtract() {
            return true;
        }

        // Check if this can receive
        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
