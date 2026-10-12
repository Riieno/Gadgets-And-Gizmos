package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.lib.physics.StrandAnchor;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

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
    // Saved strand position and owner, retained while the owner is unloaded
    @Nullable
    private StrandAnchor anchor;

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
        if (level == null || level.isClientSide()) {
            return;
        }
        if (anchor != null && !updateAnchoredAttachmentPoint()) {
            level.removeBlock(worldPosition, false);
            return;
        }
        if (anchor == null && isFree() && !isPendingRopeEndpoint()) {
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
        anchor = new StrandAnchor(ropeUUID, pathPosition, null, null);
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        ServerLevelRopeManager manager = serverLevel == null ? null : ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand strand = manager == null ? null : manager.getStrand(ropeUUID);
        if (strand != null) retainAnchorOwner(strand);
        setAttachmentPoint(worldPoint, false);
        setChanged();
        sendData();
    }

    // Set the attachment point
    private void setAttachmentPoint(Vec3 worldPoint, boolean notify) {
        Vec3 localPoint = SableTransformApi.toLocalPosition(this, worldPoint);
        if (localPoint == null) return;
        Vec3 newOffset = localPoint.subtract(Vec3.atLowerCornerOf(worldPosition));
        if (newOffset.distanceToSqr(attachmentOffset) <= 1.0E-6D) {
            return;
        }
        attachmentOffset = newOffset;
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

    // Follow a loaded parent strand and wait for saved owners to reload
    private boolean updateAnchoredAttachmentPoint() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || anchor == null) return true;
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand strand = manager == null ? null : manager.getStrand(anchor.strandId());
        if (strand == null && anchor.ownerPos() != null) {
            if (!SubLevelBlockEntityCollector.isTargetLoaded(serverLevel, anchor.ownerSubLevelId(), anchor.ownerPos())) {
                return true;
            }
            BlockEntity owner = BlockEntityLookupApi.findLoadedExact(serverLevel, anchor.ownerSubLevelId(), anchor.ownerPos());
            RopeStrandHolderBehavior holder = owner instanceof PoweredZiplineBlockEntity zipline
                    ? zipline.getOwnedHangingRopeHolder(anchor.strandId())
                    : owner instanceof SmartBlockEntity smart ? smart.getBehaviour(RopeStrandHolderBehavior.TYPE) : null;
            strand = holder == null ? null : holder.getOwnedStrand();
            if (anchor.isBroken(true, strand == null ? null : strand.getUUID())) return false;
        }
        if (strand == null) return true;
        retainAnchorOwner(strand);
        Vec3 sampled = anchor.sample(strand.getPoints());
        if (sampled != null) setAttachmentPoint(sampled, true);
        refreshAttachedRopeAttachment();
        return true;
    }

    // Retain the parent owner so unloading can be distinguished from breaking a rope
    private void retainAnchorOwner(ServerRopeStrand strand) {
        RopeAttachment owner = strand.getAttachment(RopeAttachmentPoint.START);
        if (anchor == null || owner == null) return;
        StrandAnchor updated = anchor.withOwner(owner.blockAttachment(), owner.subLevelID());
        if (updated.equals(anchor)) return;
        anchor = updated;
        setChanged();
    }

    // Update the physical endpoint without replacing its saved attachment or constraints
    private void refreshAttachedRopeAttachment() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || ropeHolder == null || !ropeHolder.isAttached()) return;
        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand == null || !strand.isActive()) return;
        RopeAttachmentPoint point = ropeHolder.ownsRope() ? RopeAttachmentPoint.START : RopeAttachmentPoint.END;
        RopeAttachment existing = strand.getAttachment(point);
        if (existing == null) return;
        ServerSubLevel subLevel = null;
        if (existing.subLevelID() != null) {
            if (!(SableLevelApi.subLevel(serverLevel, existing.subLevelID()) instanceof ServerSubLevel resolved)) return;
            subLevel = resolved;
        }
        Vec3 pos = getAttachmentPoint(worldPosition, getBlockState());
        strand.setAttachment(point == RopeAttachmentPoint.START ? RopeHandle.AttachmentPoint.START : RopeHandle.AttachmentPoint.END,
                new Vector3d(pos.x, pos.y, pos.z), subLevel);
    }

    // Write the rope knot
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putDouble("AttachmentOffsetX", attachmentOffset.x);
        tag.putDouble("AttachmentOffsetY", attachmentOffset.y);
        tag.putDouble("AttachmentOffsetZ", attachmentOffset.z);
        if (anchor != null) anchor.write(tag);
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
        anchor = StrandAnchor.read(tag);
    }
}
