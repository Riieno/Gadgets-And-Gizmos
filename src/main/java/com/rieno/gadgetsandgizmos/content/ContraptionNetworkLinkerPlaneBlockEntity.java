package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Expand one linker face into a configurable plane of stable ship-local targets
public class ContraptionNetworkLinkerPlaneBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String PLANES_KEY = "Planes";
    private static final String FACE_KEY = "Face";
    private static final String MODE_KEY = "Mode";
    private static final String ORIENTATION_KEY = "Orientation";
    private static final Set<ContraptionNetworkLinkerPlaneBlockEntity> LOADED_PLANES = ConcurrentHashMap.newKeySet();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked modes
    private final EnumMap<Direction, ContraptionNetworkLinkerData.LinkMode> modes = new EnumMap<>(Direction.class);
    // Output sources
    private final EnumMap<Direction, Map<String, Integer>> outputSources = new EnumMap<>(Direction.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker plane
    public ContraptionNetworkLinkerPlaneBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.CONTRAPTION_NETWORK_LINKER_PLANE.get(), pos, blockState);
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the load event
    @Override
    public void onLoad() {
        super.onLoad();
        LOADED_PLANES.add(this);
    }

    // Remove the contraption network linker plane
    @Override
    public void remove() {
        LOADED_PLANES.remove(this);
        super.remove();
    }

    // Set the plane
    public void setPlane(Direction face, ContraptionNetworkLinkerData.LinkMode mode) {
        if (face == null || mode == null || level == null) {
            return;
        }
        modes.put(face, mode);
        BlockState state = getBlockState();
        if (state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock) {
            BlockState next = ContraptionNetworkLinkerPlaneBlock.withPlane(state, face, true);
            if (next != state) {
                level.setBlock(worldPosition, next, 3);
            }
        }
        notifyFaceChanged(face);
        setChanged();
        sendData();
    }

    // Remove the plane
    public void removePlane(Direction face) {
        if (face == null || level == null) {
            return;
        }
        modes.remove(face);
        outputSources.remove(face);

        BlockState state = getBlockState();
        if (state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock) {
            BlockState next = ContraptionNetworkLinkerPlaneBlock.withPlane(state, face, false);
            if (ContraptionNetworkLinkerPlaneBlock.hasAnyPlane(next)) {
                level.setBlock(worldPosition, next, 3);
            } else {
                level.removeBlock(worldPosition, false);
            }
        }
        notifyFaceChanged(face);
        setChanged();
    }

    // Get the mode
    public @Nullable ContraptionNetworkLinkerData.LinkMode getMode(Direction face) {
        return modes.get(face);
    }

    // Check if this has plane
    public boolean hasPlane(Direction face) {
        return modes.containsKey(face);
    }

    // Set the output signal
    public void setOutputSignal(Direction face, String sourceId, int strength) {
        if (face == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        Map<String, Integer> sources = outputSources.computeIfAbsent(face, ignored -> new HashMap<>());
        int prev = maxSignal(sources);
        if (strength <= 0) {
            sources.remove(sourceId);
        } else {
            sources.put(sourceId, Math.min(15, strength));
        }
        if (sources.isEmpty()) {
            outputSources.remove(face);
        }
        int next = maxSignal(sources);
        if (prev != next) {
            notifyFaceChanged(face);
            setChanged();
            sendData();
        }
    }

    // Get the output signal
    public int getOutputSignal(Direction face) {
        if (face == null || modes.get(face) != ContraptionNetworkLinkerData.LinkMode.OUTPUT) {
            return 0;
        }
        return maxSignal(outputSources.get(face));
    }

    // Sample the input signal
    public int sampleInputSignal(Direction face) {
        if (face == null || modes.get(face) != ContraptionNetworkLinkerData.LinkMode.INPUT || level == null) {
            return 0;
        }
        BlockPos attachedPos = worldPosition.relative(face.getOpposite());
        if (!level.isLoaded(attachedPos)) {
            return 0;
        }
        BlockState attachedState = level.getBlockState(attachedPos);
        if (attachedState.isAir()) {
            return 0;
        }
        return ControllerRedstoneCompat.sampleFaceOutputSignal(level, attachedPos, attachedState, face);
    }

    // Clear the source
    public static boolean clearSource(@Nullable Level level, String sourceId) {
        if (level == null || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        boolean changed = false;
        for (ContraptionNetworkLinkerPlaneBlockEntity plane : List.copyOf(LOADED_PLANES)) {
            if (plane == null || plane.isRemoved() || !sameServer(plane.level, level)) {
                continue;
            }
            changed |= plane.clearSource(sourceId);
        }
        return changed;
    }

    // Clear the source
    private boolean clearSource(String sourceId) {
        boolean changed = false;
        for (Direction face : Direction.values()) {
            Map<String, Integer> sources = outputSources.get(face);
            if (sources == null || sources.isEmpty()) {
                continue;
            }
            int prev = maxSignal(sources);
            sources.keySet().removeIf(key -> key.equals(sourceId) || key.startsWith(sourceId + ":"));
            if (sources.isEmpty()) {
                outputSources.remove(face);
            }
            if (prev != maxSignal(sources)) {
                notifyFaceChanged(face);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            sendData();
        }
        return changed;
    }

    // Notify the face changed
    private void notifyFaceChanged(Direction face) {
        if (level == null || face == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        BlockPos attachedPos = worldPosition.relative(face.getOpposite());
        if (level.isLoaded(worldPosition)) {
            level.blockUpdated(worldPosition, state.getBlock());
            level.sendBlockUpdated(worldPosition, state, state, 2);
        }
        if (level.isLoaded(attachedPos)) {
            level.neighborChanged(attachedPos, state.getBlock(), worldPosition);
            level.blockUpdated(attachedPos, level.getBlockState(attachedPos).getBlock());
        }
    }

    // Get the maximum signal
    private static int maxSignal(@Nullable Map<String, Integer> sources) {
        if (sources == null || sources.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (Integer strength : sources.values()) {
            if (strength != null) {
                max = Math.max(max, strength);
            }
        }
        return max;
    }

    // Check if this uses the same server
    private static boolean sameServer(@Nullable Level first, Level second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.getServer() != null && first.getServer() == second.getServer();
    }

    // Create the linker plane update tag
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        writePlanes(tag);
        return tag;
    }

    // Create the linker plane update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Write the contraption network linker plane safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        writePlanes(tag);
    }

    // Write the contraption network linker plane
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        writePlanes(tag);
    }

    // Read the contraption network linker plane
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        modes.clear();
        outputSources.clear();
        Direction destinationOrientation = getBlockState().hasProperty(ContraptionNetworkLinkerPlaneBlock.ORIENTATION)
                ? getBlockState().getValue(ContraptionNetworkLinkerPlaneBlock.ORIENTATION)
                : Direction.NORTH;
        Direction sourceOrientation = Direction.byName(tag.getString(ORIENTATION_KEY));
        int quarterTurns = ContraptionNetworkLinkerPlaneBlock.horizontalQuarterTurns(
                sourceOrientation == null ? Direction.NORTH : sourceOrientation, destinationOrientation);
        ListTag planesTag = tag.getList(PLANES_KEY, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < planesTag.size(); idx++) {
            CompoundTag planeTag = planesTag.getCompound(idx);
            Direction face = Direction.byName(planeTag.getString(FACE_KEY));
            if (face == null) {
                continue;
            }
            modes.put(ContraptionNetworkLinkerPlaneBlock.rotateDirection(face, quarterTurns),
                    ContraptionNetworkLinkerData.LinkMode.byId(planeTag.getString(MODE_KEY)));
        }
        syncBlockStateFromModes();
    }

    // Write the planes
    private void writePlanes(CompoundTag tag) {
        ListTag planesTag = new ListTag();
        for (Map.Entry<Direction, ContraptionNetworkLinkerData.LinkMode> entry : modes.entrySet()) {
            CompoundTag planeTag = new CompoundTag();
            planeTag.putString(FACE_KEY, entry.getKey().getSerializedName());
            planeTag.putString(MODE_KEY, entry.getValue().id().toLowerCase(Locale.ROOT));
            planesTag.add(planeTag);
        }
        tag.put(PLANES_KEY, planesTag);
        BlockState state = getBlockState();
        Direction orientation = state.hasProperty(ContraptionNetworkLinkerPlaneBlock.ORIENTATION)
                ? state.getValue(ContraptionNetworkLinkerPlaneBlock.ORIENTATION)
                : Direction.NORTH;
        tag.putString(ORIENTATION_KEY, orientation.getSerializedName());
    }

    // Sync the block state from modes
    private void syncBlockStateFromModes() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)) {
            return;
        }
        BlockState next = state;
        for (Direction dir : Direction.values()) {
            next = ContraptionNetworkLinkerPlaneBlock.withPlane(next, dir, modes.containsKey(dir));
        }
        if (!Objects.equals(state, next)) {
            level.setBlock(worldPosition, next, 3);
        }
    }

    // Get the faces
    public List<Direction> faces() {
        return new ArrayList<>(modes.keySet());
    }
}
