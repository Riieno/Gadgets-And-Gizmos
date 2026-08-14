package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.List;

// Handle saved state for the Gyro Redstone Bridge
public class GyroRedstoneBridgeBlockEntity extends SmartBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked signals
    private final EnumMap<Direction, Integer> signals = new EnumMap<>(Direction.class);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the gyro redstone bridge
    public GyroRedstoneBridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.GYRO_REDSTONE_BRIDGE.get(), pos, blockState);
        for (Direction dir : Direction.values()) {
            signals.put(dir, 0);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, GyroRedstoneBridgeBlockEntity be) {
        be.tick();
        be.updateSignals(level, pos);
    }

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Get the signal
    public int getSignal(Direction dir) {
        return signals.getOrDefault(dir, 0);
    }

    // Update the signals
    private void updateSignals(Level level, BlockPos pos) {
        GyroscopeLinkBlockEntity link = resolveLink(level, pos);
        OrientationPayload payload = resolvePayload(level, pos);
        EnumMap<Direction, Integer> next = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            next.put(dir, 0);
        }

        if (link != null) {

            next.put(Direction.NORTH, link.getCardinalRedstoneSignal(Direction.NORTH));
            next.put(Direction.SOUTH, link.getCardinalRedstoneSignal(Direction.SOUTH));
            next.put(Direction.EAST, link.getCardinalRedstoneSignal(Direction.EAST));
            next.put(Direction.WEST, link.getCardinalRedstoneSignal(Direction.WEST));
        }

        if (payload != null) {
            double x = Mth.clamp(payload.getXAngleRadians() / (Math.PI / 2.0D), -1.0D, 1.0D);
            double z = Mth.clamp(payload.getZAngleRadians() / (Math.PI / 2.0D), -1.0D, 1.0D);
            double magnitude = Math.min(1.0D, Math.sqrt(x * x + z * z));
            next.put(Direction.UP, toSignal(magnitude));
            next.put(Direction.DOWN, payload.isLive() ? 15 : 0);
        }

        if (!next.equals(signals)) {
            signals.clear();
            signals.putAll(next);
            setChanged();
            sendData();
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), 3);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), getBlockState().getBlock());
            }
            level.updateNeighborsAt(pos, getBlockState().getBlock());
        }
    }

    // Resolve the payload
    private OrientationPayload resolvePayload(Level level, BlockPos pos) {
        GyroscopeLinkBlockEntity link = resolveLink(level, pos);
        if (link == null) {
            return null;
        }
        OrientationPayload payload = link.getLatestPayload();
        if (payload != null) {
            return payload;
        }
        double[] angles = link.getLinkedAnglesRadians();
        if (angles != null) {
            return new OrientationPayload(angles[0], angles[1], link.getLinkedDirection(),
                    link.getTrackingMode() == GyroscopeLinkBlockEntity.TrackingMode.LIVE,
                    level.getGameTime());
        }
        return null;
    }

    // Resolve the link
    private GyroscopeLinkBlockEntity resolveLink(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof GyroscopeLinkBlockEntity link) {
                return link;
            }
        }
        return null;
    }

    // Convert the gyro redstone bridge to signal
    private static int toSignal(double val) {
        return Mth.clamp((int) Math.round(Mth.clamp(val, 0.0D, 1.0D) * 15.0D), 0, 15);
    }

    // Write the gyro redstone bridge
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        for (Direction dir : Direction.values()) {
            tag.putInt("Signal_" + dir.getSerializedName(), signals.getOrDefault(dir, 0));
        }
    }

    // Read the gyro redstone bridge
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        for (Direction dir : Direction.values()) {
            String key = "Signal_" + dir.getSerializedName();
            signals.put(dir, tag.contains(key) ? tag.getInt(key) : 0);
        }
    }
}
