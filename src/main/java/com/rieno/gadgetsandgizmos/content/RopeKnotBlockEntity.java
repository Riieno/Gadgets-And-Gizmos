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
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.UUID;

// Track the rope strand and exact path position attached to one knot
public class RopeKnotBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity {
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
    // Current anchor rope UUID
    @Nullable
    private UUID anchorRopeUUID;
    // Current anchor path position
    private float anchorPathPosition;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the rope knot
    public RopeKnotBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.ROPE_KNOT.get(), pos, state);
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

    // Update the rope knot
    @Override
    public void tick() {
        super.tick();
        if (ropeHolder != null) {
            ropeHolder.tick();
        }
        if (level == null || level.isClientSide()) {
            return;
        }
        if (anchorRopeUUID != null && !updateAnchoredAttachmentPoint()) {
            level.removeBlock(worldPosition, false);
            return;
        }
        if (!level.isClientSide() && isFree() && !isPendingRopeEndpoint()) {
            level.removeBlock(worldPosition, false);
        }
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
        return ropeHolder != null && !ropeHolder.isAttached();
    }

    // Check if this is pending rope endpoint
    private boolean isPendingRopeEndpoint() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        for (ServerPlayer player : serverLevel.players()) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof RopeItem
                        && stack.has(SimDataComponents.ROPE_FIRST_CONNECTION)
                        && worldPosition.equals(stack.get(SimDataComponents.ROPE_FIRST_CONNECTION))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Set the attachment point
    public void setAttachmentPoint(Vec3 worldPoint) {
        setAttachmentPoint(worldPoint, true);
    }

    // Set the rope anchor
    public void setRopeAnchor(UUID ropeUUID, float pathPosition, Vec3 worldPoint) {
        anchorRopeUUID = ropeUUID;
        anchorPathPosition = pathPosition;
        setAttachmentPoint(worldPoint, true);
    }

    // Set the attachment point
    private void setAttachmentPoint(Vec3 worldPoint, boolean notify) {
        Vec3 newOffset = worldPoint.subtract(Vec3.atLowerCornerOf(worldPosition));
        if (newOffset.distanceToSqr(attachmentOffset) <= 1.0E-6D) {
            return;
        }
        attachmentOffset = worldPoint.subtract(Vec3.atLowerCornerOf(worldPosition));
        setChanged();
        if (notify) {
            sendData();
        }
        refreshAttachedRopeAttachment();
    }

    // Get the attachment offset
    public Vec3 getAttachmentOffset() {
        return attachmentOffset;
    }

    // Get the attachment point
    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        return Vec3.atLowerCornerOf(pos).add(attachmentOffset);
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {
        Vec3 attachmentPoint = getAttachmentPoint(worldPosition, getBlockState());
        return new AABB(attachmentPoint, attachmentPoint).inflate(8.0D);
    }

    // Update the anchored attachment point
    private boolean updateAnchoredAttachmentPoint() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || anchorRopeUUID == null) {
            return true;
        }
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand strand = manager == null ? null : manager.getStrand(anchorRopeUUID);
        Vec3 sampled = strand == null ? null : sampleRope(strand, anchorPathPosition);
        if (sampled == null) {
            return false;
        }
        setAttachmentPoint(sampled, true);
        return true;
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
        UUID subLevelId = existing == null ? null : existing.subLevelID();
        strand.addAttachment(serverLevel, point, new RopeAttachment(point, subLevelId, worldPosition));
    }

    // Sample the rope
    private static @Nullable Vec3 sampleRope(ServerRopeStrand strand, float pos) {
        var points = strand.getPoints();
        float cumulative = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d a = new Vector3d((Vector3dc) points.get(i));
            Vector3d b = new Vector3d((Vector3dc) points.get(i + 1));
            Vector3d ab = b.sub((Vector3dc) a, new Vector3d());
            float segmentLength = (float) ab.length();
            if (pos <= cumulative + segmentLength) {
                double local = segmentLength <= 1.0E-5f ? 0.0D : (pos - cumulative) / segmentLength;
                Vector3d res = a.fma(Mth.clamp(local, 0.0D, 1.0D), ab, new Vector3d());
                return new Vec3(res.x, res.y, res.z);
            }
            cumulative += segmentLength;
        }
        if (points.isEmpty()) {
            return null;
        }
        Vector3d last = new Vector3d((Vector3dc) points.get(points.size() - 1));
        return new Vec3(last.x, last.y, last.z);
    }

    // Write the rope knot
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putDouble("AttachmentOffsetX", attachmentOffset.x);
        tag.putDouble("AttachmentOffsetY", attachmentOffset.y);
        tag.putDouble("AttachmentOffsetZ", attachmentOffset.z);
        if (anchorRopeUUID != null) {
            tag.putUUID("AnchorRopeUUID", anchorRopeUUID);
            tag.putFloat("AnchorPathPosition", anchorPathPosition);
        }
    }

    // Read the rope knot
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (tag.contains("AttachmentOffsetX")) {
            attachmentOffset = new Vec3(
                tag.getDouble("AttachmentOffsetX"),
                tag.getDouble("AttachmentOffsetY"),
                tag.getDouble("AttachmentOffsetZ"));
        }
        anchorRopeUUID = tag.hasUUID("AnchorRopeUUID") ? tag.getUUID("AnchorRopeUUID") : null;
        anchorPathPosition = tag.contains("AnchorPathPosition") ? tag.getFloat("AnchorPathPosition") : 0.0f;
    }
}
