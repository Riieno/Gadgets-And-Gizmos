package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.control.LinkedOrientationSource;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Handle saved state for the Virtual Orientation Source
public class VirtualOrientationSourceBlockEntity extends SmartBlockEntity implements LinkedOrientationSource {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current x rad
    private double xRad;
    // Current z rad
    private double zRad;
    // Current dir
    private Vec3 dir;
    // Last tick
    private long lastTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the virtual orientation source
    public VirtualOrientationSourceBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.VIRTUAL_ORIENTATION_SOURCE.get(), pos, blockState);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the server
    public static void tickServer(Level level, BlockPos pos, BlockState state, VirtualOrientationSourceBlockEntity be) {
        be.tick();
    }

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Set the angles radians
    public void setAnglesRadians(double xAngleRadians, double zAngleRadians) {
        this.xRad = xAngleRadians;
        this.zRad = zAngleRadians;
        this.dir = null;
        stampUpdated();
    }

    // Set the angles degrees
    public void setAnglesDegrees(double xAngleDegrees, double zAngleDegrees) {
        setAnglesRadians(Math.toRadians(xAngleDegrees), Math.toRadians(zAngleDegrees));
    }

    // Set the direction
    public void setDirection(Vec3 direction) {
        if (direction == null || direction.lengthSqr() < 1.0E-6D) {
            clearVirtualOrientation();
            return;
        }
        Vec3 normalized = direction.normalize();
        this.dir = normalized;
        this.xRad = Math.atan2(normalized.z, normalized.y);
        this.zRad = Math.atan2(normalized.x, normalized.y);
        stampUpdated();
    }

    // Clear the virtual orientation
    public void clearVirtualOrientation() {
        this.dir = null;
        this.xRad = 0.0D;
        this.zRad = 0.0D;
        this.lastTick = Long.MIN_VALUE;
        setChanged();
        sendData();
    }

    // Get the linked direction
    @Override
    public Vec3 getLinkedDirection() {
        if (!isOrientationSourceActive()) {
            return null;
        }
        return dir != null
            ? dir
                : OrientationMath.directionFromAngles(xRad, zRad);
    }

    // Get the linked angles radians
    @Override
    public double[] getLinkedAnglesRadians() {
        if (!isOrientationSourceActive()) {
            return null;
        }
        return new double[]{xRad, zRad};
    }

    // Check if the orientation source is active
    @Override
    public boolean isOrientationSourceActive() {
        if (level == null || lastTick == Long.MIN_VALUE) {
            return false;
        }
        return level.getGameTime() - lastTick <= CTConfigs.SERVER.virtualOrientationSourceTimeoutTicks.get();
    }

    // Get the last update tick
    public long getLastUpdateTick() {
        return lastTick;
    }

    // Stamp the update time
    private void stampUpdated() {
        if (level != null) {
            this.lastTick = level.getGameTime();
        }
        setChanged();
        sendData();
    }

    // Write the virtual orientation source
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putDouble("XAngleRad", xRad);
        tag.putDouble("ZAngleRad", zRad);
        if (dir != null) {
            tag.putDouble("DirX", dir.x);
            tag.putDouble("DirY", dir.y);
            tag.putDouble("DirZ", dir.z);
        }
        tag.putLong("LastUpdateTick", lastTick);
    }

    // Read the virtual orientation source
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        xRad = tag.contains("XAngleRad") ? tag.getDouble("XAngleRad") : 0.0D;
        zRad = tag.contains("ZAngleRad") ? tag.getDouble("ZAngleRad") : 0.0D;
        if (tag.contains("DirX") && tag.contains("DirY") && tag.contains("DirZ")) {
            dir = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ")).normalize();
        } else {
            dir = null;
        }
        lastTick = tag.contains("LastUpdateTick") ? tag.getLong("LastUpdateTick") : Long.MIN_VALUE;
    }
}
