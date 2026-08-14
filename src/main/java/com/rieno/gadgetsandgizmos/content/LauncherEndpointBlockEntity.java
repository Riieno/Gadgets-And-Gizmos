package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Track the movable launcher rope endpoint and remove it once released
public class LauncherEndpointBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current rope holder
    private RopeStrandHolderBehavior ropeHolder;
    // Attachment offset
    private Vec3 attachmentOffset = new Vec3(0.5D, 0.5D, 0.5D);
    // Tracks whether remove when free is set
    private boolean removeWhenFree;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the launcher endpoint
    public LauncherEndpointBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.LAUNCHER_ENDPOINT.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ropeHolder = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeHolder);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the launcher endpoint
    @Override
    public void tick() {
        super.tick();
        if (ropeHolder != null) {
            ropeHolder.tick();
        }
        if (level == null || level.isClientSide() || !removeWhenFree || !isFree()) {
            return;
        }
        level.removeBlock(worldPosition, false);
    }

    // Get the behavior
    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeHolder;
    }

    // Get the rope holder
    public RopeStrandHolderBehavior getRopeHolder() {
        return ropeHolder;
    }

    // Check if this is free
    public boolean isFree() {
        if (ropeHolder == null) {
            return true;
        }
        return !ropeHolder.isAttached() || ropeHolder.getAttachedStrand() == null;
    }

    // Remove the launcher endpoint when free
    public void removeWhenFree() {
        removeWhenFree = true;
        setChanged();
        sendData();
    }

    // Detach the virtual rope
    public void detachVirtualRope() {
        if (ropeHolder != null) {
            ropeHolder.detachRope();
        }
    }

    // Set the changed
    @Override
    public void setChanged() {
        if (!isVirtual()) {
            super.setChanged();
        }
    }

    // Send the data
    @Override
    public void sendData() {
        if (!isVirtual()) {
            super.sendData();
        }
    }

    // Set the attachment point
    public void setAttachmentPoint(Vec3 attachmentPoint) {
        Vec3 newOffset = attachmentPoint.subtract(Vec3.atLowerCornerOf(worldPosition));
        if (newOffset.distanceToSqr(attachmentOffset) <= 1.0E-6D) {
            return;
        }
        attachmentOffset = newOffset;
        setChanged();
        sendData();
        refreshAttachedRopeAttachment();
    }

    // Refresh the attached rope attachment
    private void refreshAttachedRopeAttachment() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || ropeHolder == null || !ropeHolder.isAttached()) {
            return;
        }
        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand == null) {
            return;
        }
        RopeAttachmentPoint point = ropeHolder.ownsRope() ? RopeAttachmentPoint.START : RopeAttachmentPoint.END;
        RopeAttachment existing = strand.getAttachment(point);
        if (existing == null) {
            return;
        }
        ServerSubLevel subLevel = null;
        if (existing.subLevelID() != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) {
                return;
            }
            subLevel = (ServerSubLevel) container.getSubLevel(existing.subLevelID());
            if (subLevel == null) {
                return;
            }
        }
        RopeHandle.AttachmentPoint handlePoint = point == RopeAttachmentPoint.END
                ? RopeHandle.AttachmentPoint.END
                : RopeHandle.AttachmentPoint.START;
        strand.setAttachment(handlePoint, JOMLConversion.toJOML((Position) ropeHolder.getAttachmentPoint()), subLevel);
    }

    // Get the attachment point
    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        return Vec3.atLowerCornerOf(pos).add(attachmentOffset);
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {
        Vec3 point = getAttachmentPoint(worldPosition, getBlockState());
        return new AABB(point, point).inflate(8.0D);
    }

    // Destroy the endpoint rope
    public void destroyEndpointRope() {
        if (ropeHolder != null) {
            ropeHolder.destroy();
        }
    }

    // Write the launcher endpoint
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putDouble("AttachmentOffsetX", attachmentOffset.x);
        tag.putDouble("AttachmentOffsetY", attachmentOffset.y);
        tag.putDouble("AttachmentOffsetZ", attachmentOffset.z);
        tag.putBoolean("RemoveWhenFree", removeWhenFree);
    }

    // Read the launcher endpoint
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (tag.contains("AttachmentOffsetX")) {
            attachmentOffset = new Vec3(
                    tag.getDouble("AttachmentOffsetX"),
                    tag.getDouble("AttachmentOffsetY"),
                    tag.getDouble("AttachmentOffsetZ"));
        }
        removeWhenFree = tag.getBoolean("RemoveWhenFree");
    }
}
