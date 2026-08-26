package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.lib.physics.MountedAssemblyStatus;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Keep an aileron's mounted body and physical hinge aligned with the bearing target
final class AileronBearingMountedAssembly {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ConstraintJointAxis> LOCKED_AXES = EnumSet.of(
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Y,
            ConstraintJointAxis.LINEAR_Z,
            ConstraintJointAxis.ANGULAR_X,
            ConstraintJointAxis.ANGULAR_Y,
            ConstraintJointAxis.ANGULAR_Z);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Head
    private final BearingHead head;
    // Current joint
    private PhysicsConstraintHandle joint;
    // Current joint parent
    private ServerSubLevel jointParent;
    // Current joint child
    private ServerSubLevel jointChild;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing mounted assembly
    AileronBearingMountedAssembly(BearingHead head) {
        this.head = head;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Assemble the aileron bearing mounted assembly
    boolean assemble(AileronBearingBlockEntity bearing, ServerLevel level) {
        BlockPos mountedPos = bearing.getMountedBlockPos(head);
        if (hasRemovedContainingSubLevel(level, mountedPos) || hasRemovedContainingSubLevel(bearing)) {
            return false;
        }
        BlockPos linkLocalPos;
        ServerSubLevel child;
        BlockState mountedState = level.getBlockState(mountedPos);
        if (mountedState.isAir()) {
            child = createLinkOnlySubLevel(bearing, level);
            if (child == null) {
                return false;
            }
            linkLocalPos = child.getPlot().getCenterBlock();
        } else {
            if (isParentSubLevelAileronLinkBlock(bearing, level, mountedPos, mountedState)) {
                return false;
            }
            SimAssemblyHelper.AssemblyResult res;
            try {
                res = SimAssemblyHelper.assembleFromSingleBlock(level, bearing.getBlockPos(), mountedPos, false, false);
            } catch (AssemblyException ignored) {
                return false;
            }

            if (res == null || !(res.subLevel() instanceof ServerSubLevel assembledChild)) {
                return false;
            }
            child = assembledChild;
            linkLocalPos = bearing.getBlockPos().offset(res.offset());
            placeLinkBlock(bearing, child, linkLocalPos);
        }

        if (resolveMountedState(child, linkLocalPos) == null) {
            clearChildSubLevel(level, child);
            return false;
        }
        if (!configLinkBlockEntity(bearing, level, child, linkLocalPos)) {
            clearChildSubLevel(level, child);
            return false;
        }

        bearing.setMountedAssembly(head, child.getUniqueId(), linkLocalPos);
        return true;
    }

    // Check if this is a parent sublevel aileron link block
    private boolean isParentSubLevelAileronLinkBlock(AileronBearingBlockEntity bearing, ServerLevel level,
                                                     BlockPos mountedPos, BlockState mountedState) {
        if (!(mountedState.getBlock() instanceof AileronBearingLinkBlock)) {
            return false;
        }
        SubLevel bearingSubLevel = NestedAssemblyFrame.resolve(bearing).parent();
        SubLevel mountedSubLevel = Sable.HELPER.getContaining(level, mountedPos);
        return bearingSubLevel == mountedSubLevel
                || bearingSubLevel != null && mountedSubLevel != null
                && bearingSubLevel.getUniqueId().equals(mountedSubLevel.getUniqueId());
    }

    // Create the link only sublevel
    private ServerSubLevel createLinkOnlySubLevel(AileronBearingBlockEntity bearing, ServerLevel level) {
        NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(bearing);
        SubLevel containingSubLevel = parentFrame.parent();
        if (parentFrame.isRemoved()) {
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        BlockPos anchorPos = bearing.getBlockPos();
        Pose3d pose = new Pose3d();
        pose.position().set(parentFrame.toWorldPosition(new Vector3d(
                anchorPos.getX() + 0.5D, anchorPos.getY() + 0.5D, anchorPos.getZ() + 0.5D)));
        pose.orientation().set(parentFrame.toWorldOrientation(new Quaterniond()));
        SubLevel allocated = container.allocateNewSubLevel(pose);
        if (!(allocated instanceof ServerSubLevel child)) {
            return null;
        }
        ServerLevelPlot plot = child.getPlot();
        ChunkPos centerChunk = plot.getCenterChunk();
        plot.newEmptyChunk(centerChunk);
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, linkState(bearing), 3);
        if (!LinkOnlySubLevelPhysics.refresh(child, container)) {
            return null;
        }

        BlockPos plotAnchor = plot.getCenterBlock();
        Vector3dc centerOfMass = child.getMassTracker().getCenterOfMass();
        Vector3d subLevelPosition = new Vector3d(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
        subLevelPosition.add(centerOfMass.x() - plotAnchor.getX(),
                centerOfMass.y() - plotAnchor.getY(),
                centerOfMass.z() - plotAnchor.getZ());
        child.logicalPose().position().set(subLevelPosition);

        SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        PhysicsPipeline pipeline = physicsSystem.getPipeline();
        if (containingSubLevel != null) {
            child.logicalPose().orientation().set(parentFrame.toWorldOrientation(new Quaterniond()));
            SubLevelAssemblyHelper.kickFromContainingSubLevel(level, physicsSystem, pipeline, child, containingSubLevel);
        }
        pipeline.teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
        child.updateLastPose();
        return child;
    }

    // Get the link state
    private BlockState linkState(AileronBearingBlockEntity bearing) {
        Direction headDirection = bearing.getHeadDirection(head);
        return CTBlocks.AILERON_BEARING_LINK.get()
                .defaultBlockState()
                .setValue(AileronBearingLinkBlock.FACING, headDirection)
                .setValue(AileronBearingLinkBlock.HEAD, AileronBearingLinkBlock.HeadVariant.fromHead(head));
    }

    // Place the link block
    private void placeLinkBlock(AileronBearingBlockEntity bearing, ServerSubLevel child, BlockPos linkLocalPos) {
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, linkState(bearing), 3);
    }

    // Configure the link block entity
    private boolean configLinkBlockEntity(AileronBearingBlockEntity bearing, ServerLevel level, ServerSubLevel child,
                                             BlockPos linkLocalPos) {
        BlockEntity blockEntity = level.getBlockEntity(linkLocalPos);
        AileronBearingLinkBlockEntity link = blockEntity instanceof AileronBearingLinkBlockEntity linkBlockEntity
                ? linkBlockEntity
                : SimulatedHelper.findBlockEntityInSubLevel(child, linkLocalPos, AileronBearingLinkBlockEntity.class);
        if (link == null) {
            return false;
        }
        link.setParent(bearing, head);
        return true;
    }

    // Refresh the link parent
    boolean refreshLinkParent(AileronBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos linkLocalPos = bearing.getMountedLocalPos(head);
        return child != null && linkLocalPos != null && configLinkBlockEntity(bearing, level, child, linkLocalPos);
    }

    // Check if this has mounted block
    boolean hasMountedBlock(AileronBearingBlockEntity bearing, ServerLevel level) {
        return mountedBlockStatus(bearing, level) == MountedAssemblyStatus.PRESENT;
    }

    // Get the mounted block status
    MountedAssemblyStatus mountedBlockStatus(AileronBearingBlockEntity bearing, ServerLevel level) {
        BlockPos localPos = bearing.getMountedLocalPos(head);
        if (localPos == null) {
            return MountedAssemblyStatus.INVALID;
        }
        ServerSubLevel child = findChild(bearing, level);
        if (child == null) {
            return MountedAssemblyStatus.UNAVAILABLE;
        }
        if (child.isRemoved()) {
            return MountedAssemblyStatus.BROKEN;
        }
        if (!isMountedPositionLoaded(child, localPos)) {
            return MountedAssemblyStatus.UNAVAILABLE;
        }
        BlockState mountedState = resolveMountedState(child, localPos);
        if (mountedState == null || !(mountedState.getBlock() instanceof AileronBearingLinkBlock)) {
            return MountedAssemblyStatus.BROKEN;
        }
        if (mountedState.getValue(AileronBearingLinkBlock.FACING) != bearing.getHeadDirection(head)
                || mountedState.getValue(AileronBearingLinkBlock.HEAD)
                != AileronBearingLinkBlock.HeadVariant.fromHead(head)) {
            return MountedAssemblyStatus.INVALID;
        }
        AileronBearingLinkBlockEntity link = SimulatedHelper.findBlockEntityInSubLevel(
                child, localPos, AileronBearingLinkBlockEntity.class);
        if (link == null) {
            return MountedAssemblyStatus.UNAVAILABLE;
        }
        return link.isOwnedBy(bearing, head) ? MountedAssemblyStatus.PRESENT : MountedAssemblyStatus.INVALID;
    }

    // Absorb the placed block
    boolean absorbPlacedBlock(AileronBearingBlockEntity bearing, ServerLevel level, BlockPos placedPos) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos linkLocalPos = bearing.getMountedLocalPos(head);
        if (child == null || linkLocalPos == null || placedPos == null
                || !placedPos.equals(bearing.getMountedBlockPos(head))) {
            return false;
        }

        BlockState placedState = level.getBlockState(placedPos);
        if (placedState.isAir() || placedState.getBlock() instanceof AileronBearingLinkBlock) {
            return false;
        }

        BlockPos targetLocalPos = linkLocalPos.relative(bearing.getHeadDirection(head));
        if (targetLocalPos.equals(placedPos) || resolveMountedState(child, targetLocalPos) != null) {
            return false;
        }

        SubLevelAssemblyHelper.moveBlocks(level,
                new SubLevelAssemblyHelper.AssemblyTransform(placedPos, targetLocalPos, 0, Rotation.NONE, level),
                List.of(placedPos));
        child.updateLastPose();
        return level.getBlockState(placedPos).isAir() && resolveMountedState(child, targetLocalPos) != null;
    }

    // Clear the invalid assembly
    void clearInvalidAssembly(AileronBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = findChild(bearing, level);
        releaseJoint();
        if (child != null && !child.isRemoved()) {
            BlockPos localPos = bearing.getMountedLocalPos(head);
            AileronBearingLinkBlockEntity link = localPos == null ? null
                    : SimulatedHelper.findBlockEntityInSubLevel(
                    child, localPos, AileronBearingLinkBlockEntity.class);
            boolean ownsChild = link != null && link.isOwnedBy(bearing, head);
            SubLevel containing = NestedAssemblyFrame.resolve(bearing).parent();
            boolean sharedByOtherHead = bearing.isMountedSubLevelReferencedByOtherHead(
                    head, child.getUniqueId());
            if (child != containing && ownsChild && !sharedByOtherHead) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
                }
            }
        }
        bearing.setMountedAssembly(head, null, null);
    }

    // Aim the mounted assembly
    boolean aim(AileronBearingBlockEntity bearing, ServerLevel level, double angleDegrees) {
        MountedAssemblyStatus status = mountedBlockStatus(bearing, level);
        if (status.shouldRetry()) {
            releaseJoint();
            return true;
        }
        if (status.shouldClear() || status.shouldDisassemble()) {
            releaseJoint();
            return false;
        }
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos mountedPos = bearing.getMountedLocalPos(head);
        if (child == null || mountedPos == null) {
            releaseJoint();
            return true;
        }

        NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(bearing);
        if (parentFrame.isRemoved()) {
            releaseJoint();
            return true;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }

        Vector3d axis = axisVector(bearing.getHeadDirection(head).getAxis());
        Quaterniond relativeOrientation = new Quaterniond().rotateAxis(Math.toRadians(angleDegrees), axis);
        Vector3d baseAnchor = getBaseAnchor(bearing);

        ServerSubLevel parent = parentFrame.parentBody();
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

        if (!ensureJoint(bearing, mountedPos, baseAnchor, relativeOrientation, pipeline, parent, child)) {
            return false;
        }
        retargetJoint(bearing, mountedPos, baseAnchor, relativeOrientation);
        if (parent != null) {
            pipeline.wakeUp(parent);
        }
        pipeline.wakeUp(child);
        return joint != null && joint.isValid();
    }

    // Ensure the joint
    private boolean ensureJoint(AileronBearingBlockEntity bearing, BlockPos mountedPos, Vector3d baseAnchor,
                                Quaterniond baseFrameOrientation,
                                PhysicsPipeline pipeline, ServerSubLevel parent, ServerSubLevel child) {
        if (parent == child) {
            releaseJoint();
            return false;
        }
        if (joint != null && joint.isValid() && jointParent == parent && jointChild == child) {
            return true;
        }
        releaseJoint();
        try {
            Object genericConstraint = createGenericConstraint(baseAnchor, getChildAnchor(bearing, mountedPos), baseFrameOrientation);
            joint = (PhysicsConstraintHandle) SableConstraintApi.addConstraint(
                    pipeline, parent, child, genericConstraint);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            LOGGER.warn("Aileron Bearing constraint creation failed at {}: {}", mountedPos, error.toString());
            joint = null;
        }
        if (joint != null) {
            joint.setContactsEnabled(false);
            jointParent = parent;
            jointChild = child;
        }
        return joint != null && joint.isValid();
    }

    // Retarget the joint
    private void retargetJoint(AileronBearingBlockEntity bearing, BlockPos mountedPos, Vector3d baseAnchor,
                               Quaterniond baseFrameOrientation) {
        if (joint == null || !joint.isValid()) {
            return;
        }
        try {
            SableConstraintApi.setFrame(joint, 1, baseAnchor, baseFrameOrientation);
            SableConstraintApi.setFrame(joint, 2, getChildAnchor(bearing, mountedPos), new Quaterniond());
            joint.setContactsEnabled(false);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("Aileron Bearing constraint retarget failed at {}: {}", mountedPos, error.toString());
            releaseJoint();
        }
    }

    // Create the generic constraint
    private Object createGenericConstraint(Vector3dc baseAnchor, Vector3dc childAnchor,
                                           Quaterniondc baseFrameOrientation)
            throws ReflectiveOperationException {
        return SableConstraintApi.genericConfiguration(
                baseAnchor, childAnchor, baseFrameOrientation, new Quaterniond(), LOCKED_AXES);
    }

    // Get the base anchor
    private Vector3d getBaseAnchor(AileronBearingBlockEntity bearing) {
        Direction normal = bearing.getHeadDirection(head);
        return new Vector3d(bearing.getBlockPos().getX() + 0.5D,
                bearing.getBlockPos().getY() + 0.5D,
                bearing.getBlockPos().getZ() + 0.5D)
                .add(new Vector3d(normal.getStepX(), normal.getStepY(), normal.getStepZ()).mul(0.5D));
    }

    // Get the child anchor
    private Vector3d getChildAnchor(AileronBearingBlockEntity bearing, BlockPos mountedPos) {
        Direction normal = bearing.getHeadDirection(head);
        return new Vector3d(mountedPos.getX() + 0.5D,
                mountedPos.getY() + 0.5D,
                mountedPos.getZ() + 0.5D)
                .add(new Vector3d(normal.getStepX(), normal.getStepY(), normal.getStepZ()).mul(0.5D));
    }

    // Disassemble the aileron bearing mounted assembly
    void disassemble(AileronBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos localPos = bearing.getMountedLocalPos(head);
        releaseJoint();
        if (child != null && localPos != null && !child.isRemoved()) {
            NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(bearing);
            SubLevel containing = parentFrame.parent();
            if (containing != null && containing.isRemoved()) {
                bearing.setMountedAssembly(head, null, null);
                return;
            }
            if (child == containing) {
                bearing.setMountedAssembly(head, null, null);
                return;
            }
            Quaterniond orientation = parentFrame.toWorldOrientation(new Quaterniond());
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                container.physicsSystem().getPipeline().teleport(child, child.logicalPose().position(), orientation);
                child.updateLastPose();
            }
            destroyLinkBlock(level, child, localPos);
            SimAssemblyHelper.disassembleSubLevel(level, child, localPos, bearing.getBlockPos(),
                    Rotation.NONE, true);
        }
        bearing.setMountedAssembly(head, null, null);
    }

    // Check if this has removed containing sublevel
    private boolean hasRemovedContainingSubLevel(ServerLevel level, BlockPos pos) {
        SubLevel containing = Sable.HELPER.getContaining(level, pos);
        return containing != null && containing.isRemoved();
    }

    // Check if this has removed containing sublevel
    private boolean hasRemovedContainingSubLevel(AileronBearingBlockEntity bearing) {
        return NestedAssemblyFrame.resolve(bearing).isRemoved();
    }

    // Destroy the link block
    private void destroyLinkBlock(ServerLevel level, ServerSubLevel child, BlockPos linkLocalPos) {
        if (level.getBlockState(linkLocalPos).getBlock() instanceof AileronBearingLinkBlock) {
            level.setBlock(linkLocalPos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        if (child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos).getBlock() instanceof AileronBearingLinkBlock) {
            child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // Release the joint
    void releaseJoint() {
        if (joint != null && joint.isValid()) {
            joint.remove();
        }
        joint = null;
        jointParent = null;
        jointChild = null;
    }

    // Clear the child sublevel
    private void clearChildSubLevel(ServerLevel level, ServerSubLevel child) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && child != null && !child.isRemoved()) {
            container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
        }
    }

    // Resolve the mounted state
    private BlockState resolveMountedState(AileronBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos localPos = bearing.getMountedLocalPos(head);
        if (child == null || localPos == null) {
            return null;
        }
        return resolveMountedState(child, localPos);
    }

    // Resolve the mounted state
    private BlockState resolveMountedState(ServerSubLevel child, BlockPos localPos) {
        BlockPos relativePos = localPos.subtract(child.getPlot().getCenterBlock());
        BlockState state = child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos);
        return state.isAir() ? null : state;
    }

    // Check if the mounted position is loaded
    private boolean isMountedPositionLoaded(ServerSubLevel child, BlockPos localPos) {
        ServerLevelPlot plot = child.getPlot();
        ChunkPos globalChunk = new ChunkPos(localPos);
        return plot.getChunk(plot.toLocal(globalChunk)) != null;
    }

    // Resolve the child
    private ServerSubLevel resolveChild(AileronBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = findChild(bearing, level);
        return child != null && !child.isRemoved() ? child : null;
    }

    // Find the child
    private ServerSubLevel findChild(AileronBearingBlockEntity bearing, ServerLevel level) {
        UUID id = bearing.getMountedSubLevelId(head);
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = id == null || container == null ? null : container.getSubLevel(id);
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Get the axis vector
    private static Vector3d axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }
}
