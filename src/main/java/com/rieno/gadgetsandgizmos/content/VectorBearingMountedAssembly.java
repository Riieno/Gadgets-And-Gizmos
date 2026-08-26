package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.MountedAssemblyStatus;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
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
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Track the body mounted to a vector bearing and apply both bearing axes as one assembly pose
final class VectorBearingMountedAssembly {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ConstraintJointAxis> MOUNT_JOINT_AXES = EnumSet.of(
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Y,
            ConstraintJointAxis.LINEAR_Z,
            ConstraintJointAxis.ANGULAR_X,
            ConstraintJointAxis.ANGULAR_Y,
            ConstraintJointAxis.ANGULAR_Z);
    private static final Set<UUID> RECOVERING_CHILDREN = ConcurrentHashMap.newKeySet();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current joint
    private PhysicsConstraintHandle joint;
    // Current joint parent
    private ServerSubLevel jointParent;
    // Current joint child
    private ServerSubLevel jointChild;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Assemble the vector bearing mounted assembly
    boolean assemble(VectorBearingBlockEntity bearing, ServerLevel level) {
        BlockPos mountedPos = bearing.getMountedBlockPos();
        SubLevel containing = NestedAssemblyFrame.resolve(bearing).parent();
        if (containing != null && containing.isRemoved()) {
            return false;
        }

        BlockState mountedState = level.getBlockState(mountedPos);
        BlockPos linkLocalPos;
        ServerSubLevel child;
        if (mountedState.isAir()) {
            child = createLinkOnlySubLevel(bearing, level);
            if (child == null) {
                return false;
            }
            linkLocalPos = child.getPlot().getCenterBlock();
        } else {
            if (isUnsafeMountedState(bearing, mountedState)) {
                return false;
            }

            SimAssemblyHelper.AssemblyResult res;
            try {
                if (captureContainsVectorBearingHead(bearing, level, mountedPos)) {
                    return false;
                }
                res = SimAssemblyHelper.assembleFromSingleBlock(
                        level, bearing.getBlockPos(), mountedPos, false, false);
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

        bearing.setMountedAssembly(child.getUniqueId(), linkLocalPos);
        return true;
    }

    // Check if this is unsafe mounted state
    private boolean isUnsafeMountedState(VectorBearingBlockEntity bearing, BlockState mountedState) {
        if (mountedState.getBlock() instanceof VectorBearingLinkBlock) {
            return true;
        }
        if (!(mountedState.getBlock() instanceof VectorBearingBlock)) {
            return false;
        }
        return areDirectlyOpposed(bearing.getBearingFacing(), mountedState.getValue(VectorBearingBlock.FACING));
    }

    // Check if the bearings face directly opposite directions
    static boolean areDirectlyOpposed(Direction bearingFacing, Direction mountedFacing) {
        return bearingFacing != null && mountedFacing == bearingFacing.getOpposite();
    }

    // Check if this has conflicting head count
    static boolean hasConflictingHeadCount(int count) {
        return count > 1;
    }

    // Capture the contains vector bearing head
    private boolean captureContainsVectorBearingHead(VectorBearingBlockEntity bearing, ServerLevel level,
                                                     BlockPos mountedPos) throws AssemblyException {
        SimAssemblyContraption preview = new SimAssemblyContraption(bearing.getBlockPos(), true);
        preview.searchMovedStructure(level, mountedPos);
        for (BlockPos pos : preview.getBlocks()) {
            if (level.getBlockState(pos).getBlock() instanceof VectorBearingLinkBlock) {
                return true;
            }
        }
        return false;
    }

    // Create the link only sublevel
    private ServerSubLevel createLinkOnlySubLevel(VectorBearingBlockEntity bearing, ServerLevel level) {
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
            SubLevelAssemblyHelper.kickFromContainingSubLevel(
                    level, physicsSystem, pipeline, child, containingSubLevel);
        }
        pipeline.teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
        child.updateLastPose();
        return child;
    }

    // Get the link state
    private BlockState linkState(VectorBearingBlockEntity bearing) {
        return CTBlocks.VECTOR_BEARING_LINK.get()
                .defaultBlockState()
                .setValue(VectorBearingLinkBlock.FACING, bearing.getBearingFacing());
    }

    // Place the link block
    private void placeLinkBlock(VectorBearingBlockEntity bearing, ServerSubLevel child, BlockPos linkLocalPos) {
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, linkState(bearing), 3);
    }

    // Configure the link block entity
    private boolean configLinkBlockEntity(VectorBearingBlockEntity bearing, ServerLevel level, ServerSubLevel child,
                                             BlockPos linkLocalPos) {
        BlockEntity blockEntity = level.getBlockEntity(linkLocalPos);
        VectorBearingLinkBlockEntity link = blockEntity instanceof VectorBearingLinkBlockEntity linkBlockEntity
                ? linkBlockEntity
                : SimulatedHelper.findBlockEntityInSubLevel(child, linkLocalPos, VectorBearingLinkBlockEntity.class);
        if (link == null) {
            return false;
        }
        link.setParent(bearing);
        return true;
    }

    // Refresh the link parent
    boolean refreshLinkParent(VectorBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos linkLocalPos = bearing.getMountedLocalPos();
        return child != null && linkLocalPos != null
                && configLinkBlockEntity(bearing, level, child, linkLocalPos);
    }

    // Check if this has mounted block
    boolean hasMountedBlock(VectorBearingBlockEntity bearing, ServerLevel level) {
        return mountedBlockStatus(bearing, level) == MountedAssemblyStatus.PRESENT;
    }

    // Get the mounted block status
    MountedAssemblyStatus mountedBlockStatus(VectorBearingBlockEntity bearing, ServerLevel level) {
        BlockPos localPos = bearing.getMountedLocalPos();
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
        if (mountedState == null || !(mountedState.getBlock() instanceof VectorBearingLinkBlock)) {
            return MountedAssemblyStatus.BROKEN;
        }
        if (mountedState.getValue(VectorBearingLinkBlock.FACING) != bearing.getBearingFacing()) {
            return MountedAssemblyStatus.INVALID;
        }
        VectorBearingLinkBlockEntity link = SimulatedHelper.findBlockEntityInSubLevel(
                child, localPos, VectorBearingLinkBlockEntity.class);
        if (link == null) {
            return MountedAssemblyStatus.UNAVAILABLE;
        }
        return link.isOwnedBy(bearing) ? MountedAssemblyStatus.PRESENT : MountedAssemblyStatus.INVALID;
    }

    // Absorb the placed block
    boolean absorbPlacedBlock(VectorBearingBlockEntity bearing, ServerLevel level, BlockPos placedPos) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos linkLocalPos = bearing.getMountedLocalPos();
        if (child == null || linkLocalPos == null || placedPos == null
                || !placedPos.equals(bearing.getMountedBlockPos())) {
            return false;
        }
        BlockState placedState = level.getBlockState(placedPos);
        if (placedState.isAir() || placedState.getBlock() instanceof VectorBearingLinkBlock) {
            return false;
        }

        BlockPos targetLocalPos = linkLocalPos.relative(bearing.getBearingFacing());
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
    void clearInvalidAssembly(VectorBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = findChild(bearing, level);
        releaseJoint();
        if (child != null && !child.isRemoved()) {
            SubLevel containing = NestedAssemblyFrame.resolve(bearing).parent();
            BlockPos localPos = bearing.getMountedLocalPos();
            VectorBearingLinkBlockEntity link = localPos == null ? null
                    : SimulatedHelper.findBlockEntityInSubLevel(
                    child, localPos, VectorBearingLinkBlockEntity.class);
            if (child != containing && link != null && link.isOwnedBy(bearing)) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
                }
            }
        }
        bearing.setMountedAssembly(null, null);
    }

    // Recover conflicting bearing heads
    boolean recoverConflictingHeads(VectorBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = resolveChild(bearing, level);
        return child != null
                && !RECOVERING_CHILDREN.contains(child.getUniqueId())
                && recoverConflictingHeads(level, child, null);
    }

    // Recover conflicting bearing heads
    static boolean recoverConflictingHeads(ServerLevel level, VectorBearingLinkBlockEntity trigger) {
        SubLevel containing = trigger == null ? null : Sable.HELPER.getContaining(trigger);
        return containing instanceof ServerSubLevel child
                && !child.isRemoved()
                && recoverConflictingHeads(level, child, trigger);
    }

    // Recover conflicting bearing heads
    private static boolean recoverConflictingHeads(ServerLevel level, ServerSubLevel child,
                                                   VectorBearingLinkBlockEntity trigger) {
        List<VectorBearingLinkBlockEntity> links = new ArrayList<>();
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(child)) {
            if (blockEntity instanceof VectorBearingLinkBlockEntity link) {
                links.add(link);
            }
        }
        if (trigger != null && links.stream().noneMatch(link -> link == trigger)) {
            links.add(trigger);
        }
        if (!hasConflictingHeadCount(links.size())) {
            return false;
        }

        UUID childId = child.getUniqueId();
        if (!RECOVERING_CHILDREN.add(childId)) {
            return true;
        }
        try {
            Set<VectorBearingBlockEntity> parents = new LinkedHashSet<>();
            for (VectorBearingLinkBlockEntity link : links) {
                VectorBearingBlockEntity parent = link.resolveParent();
                if (parent != null) {
                    parents.add(parent);
                }
            }

            VectorBearingBlockEntity anchor = null;
            for (VectorBearingBlockEntity parent : parents) {
                if (Sable.HELPER.getContaining(parent) != child
                        && childId.equals(parent.getMountedSubLevelId())) {
                    anchor = parent;
                    break;
                }
            }
            if (anchor == null) {
                for (VectorBearingBlockEntity parent : parents) {
                    if (Sable.HELPER.getContaining(parent) != child) {
                        anchor = parent;
                        break;
                    }
                }
            }

            for (VectorBearingLinkBlockEntity link : links) {
                removeLinkBlock(child, link.getBlockPos());
            }
            for (VectorBearingBlockEntity parent : parents) {
                if (parent != anchor) {
                    parent.clearMountedAssemblyForHeadConflict();
                }
            }
            if (anchor != null) {
                try {
                    anchor.disassembleMountedBlock();
                } catch (RuntimeException | LinkageError error) {
                    anchor.clearMountedAssemblyForHeadConflict();
                    LOGGER.warn("Vector Bearing head conflict recovery failed for sub-level {}", childId, error);
                }
            } else {
                for (VectorBearingBlockEntity parent : parents) {
                    parent.clearMountedAssemblyForHeadConflict();
                }
                LOGGER.warn("Cleared a Vector Bearing head conflict without a safe external disassembly anchor in sub-level {}",
                        childId);
            }
            return true;
        } finally {
            RECOVERING_CHILDREN.remove(childId);
        }
    }

    // Remove the link block
    private static void removeLinkBlock(ServerSubLevel child, BlockPos linkLocalPos) {
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        if (child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos).getBlock()
                instanceof VectorBearingLinkBlock) {
            child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // Aim the mounted assembly
    boolean aim(VectorBearingBlockEntity bearing, ServerLevel level, Vec3 physicalDirection,
                double shaftAngleDegrees) {
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
        BlockPos mountedPos = bearing.getMountedLocalPos();
        if (child == null || mountedPos == null || physicalDirection.lengthSqr() < 1.0E-6D) {
            releaseJoint();
            return child == null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        Direction facing = bearing.getBearingFacing();
        Vector3d baseNormal = directionVector(facing);
        NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(bearing);
        if (parentFrame.isRemoved()) {
            releaseJoint();
            return true;
        }
        ServerSubLevel parent = parentFrame.parentBody();
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();

        Vector3d requestedLocal = new Vector3d(physicalDirection.x, physicalDirection.y, physicalDirection.z).normalize();
        Vector3d desiredParent = new Quaterniond().rotationTo(new Vector3d(0.0D, 1.0D, 0.0D), baseNormal)
                .transform(requestedLocal).normalize();
        Quaterniond tilt = new Quaterniond().rotationTo(baseNormal, desiredParent);
        Quaterniond shaftSpin = shaftRotation(facing, shaftAngleDegrees);
        Quaterniond relativeOrientation = new Quaterniond(tilt).mul(shaftSpin).normalize();
        TiltCandidate accepted = createTiltCandidate(bearing, facing, relativeOrientation);

        if (!ensureJoint(mountedPos, facing, accepted.baseAnchor(), accepted.baseFrameOrientation(),
                pipeline, parent, child)) {
            return false;
        }
        retargetJoint(mountedPos, facing, accepted.baseAnchor(), accepted.baseFrameOrientation());
        if (parent != null) {
            pipeline.wakeUp(parent);
        }
        pipeline.wakeUp(child);
        return joint != null && joint.isValid();
    }

    // Create the tilt candidate
    private TiltCandidate createTiltCandidate(VectorBearingBlockEntity bearing, Direction facing,
                                              Quaterniondc relativeOrientation) {
        Quaterniond baseFrameOrientation = new Quaterniond(relativeOrientation).normalize();
        Vector3d baseAnchor = getBaseAnchor(bearing, facing);
        return new TiltCandidate(baseAnchor, baseFrameOrientation);
    }

    // Ensure the joint
    private boolean ensureJoint(BlockPos mountedPos, Direction facing, Vector3d baseAnchor, Quaterniond baseFrameOrientation,
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
            Object genericConstraint = createGenericConstraint(baseAnchor, getChildAnchor(mountedPos, facing), baseFrameOrientation);
            joint = (PhysicsConstraintHandle) SableConstraintApi.addConstraint(
                    pipeline, parent, child, genericConstraint);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException error) {
            LOGGER.warn("Vector Bearing constraint creation failed at {}: {}", mountedPos, error.toString());
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
    private void retargetJoint(BlockPos mountedPos, Direction facing, Vector3d baseAnchor, Quaterniond baseFrameOrientation) {
        if (joint == null || !joint.isValid()) {
            return;
        }
        try {
            SableConstraintApi.setFrame(joint, 1, baseAnchor, baseFrameOrientation);
            SableConstraintApi.setFrame(joint, 2, getChildAnchor(mountedPos, facing), new Quaterniond());
            joint.setContactsEnabled(false);
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("Vector Bearing constraint retarget failed at {}: {}", mountedPos, error.toString());
            releaseJoint();
        }
    }

    // Create the generic constraint
    private Object createGenericConstraint(Vector3dc baseAnchor, Vector3dc childAnchor,
                                           Quaterniondc baseFrameOrientation)
            throws ReflectiveOperationException {
        return SableConstraintApi.genericConfiguration(
                baseAnchor, childAnchor, baseFrameOrientation, new Quaterniond(), MOUNT_JOINT_AXES);
    }

    // Get the base anchor
    private Vector3d getBaseAnchor(VectorBearingBlockEntity bearing, Direction facing) {
        Vector3d baseNormal = directionVector(facing);
        return new Vector3d(bearing.getBlockPos().getX() + 0.5D,
                bearing.getBlockPos().getY() + 0.5D,
                bearing.getBlockPos().getZ() + 0.5D)
                .add(baseNormal.mul(0.5D));
    }

    // Get the child anchor
    private Vector3d getChildAnchor(BlockPos mountedPos, Direction facing) {
        return new Vector3d(mountedPos.getX() + 0.5D,
                mountedPos.getY() + 0.5D,
                mountedPos.getZ() + 0.5D)
                .add(new Vector3d(directionVector(facing)).mul(0.5D));
    }

    // Get the shaft rotation
    private Quaterniond shaftRotation(Direction facing, double shaftAngleDegrees) {
        Vector3d shaftAxis = positiveAxisVector(facing.getAxis());
        return new Quaterniond().rotateAxis(Math.toRadians(shaftAngleDegrees), shaftAxis);
    }

    // Get the positive axis vector
    private Vector3d positiveAxisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }

    // Disassemble the vector bearing mounted assembly
    void disassemble(VectorBearingBlockEntity bearing, ServerLevel level) {
        if (recoverConflictingHeads(bearing, level)) {
            return;
        }
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos localPos = bearing.getMountedLocalPos();
        releaseJoint();
        if (child != null && localPos != null && !child.isRemoved()) {
            NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(bearing);
            SubLevel containing = parentFrame.parent();
            if (containing != null && containing.isRemoved()) {
                destroyLinkBlock(level, child, localPos);
                bearing.setMountedAssembly(null, null);
                return;
            }
            if (child == containing) {
                bearing.setMountedAssembly(null, null);
                return;
            }
            Quaterniond orientation = parentFrame.toWorldOrientation(new Quaterniond());
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                container.physicsSystem().getPipeline().teleport(child, child.logicalPose().position(), orientation);
                child.updateLastPose();
            }
            destroyLinkBlock(level, child, localPos);
            SimAssemblyHelper.disassembleSubLevel(level, child, localPos, bearing.getBlockPos(), Rotation.NONE, true);
        }
        bearing.setMountedAssembly(null, null);
    }

    // Remove the head after failed disassembly
    void removeHeadAfterFailedDisassembly(VectorBearingBlockEntity bearing, ServerLevel level) {
        releaseJoint();
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos localPos = bearing.getMountedLocalPos();
        if (child != null && localPos != null) {
            try {
                destroyLinkBlock(level, child, localPos);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        bearing.setMountedAssembly(null, null);
    }

    // Destroy the link block
    private void destroyLinkBlock(ServerLevel level, ServerSubLevel child, BlockPos linkLocalPos) {
        if (level.getBlockState(linkLocalPos).getBlock() instanceof VectorBearingLinkBlock) {
            level.setBlock(linkLocalPos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        if (child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos).getBlock() instanceof VectorBearingLinkBlock) {
            child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // Get the direction vector
    private Vector3d directionVector(Direction dir) {
        return new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
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
    private BlockState resolveMountedState(VectorBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = resolveChild(bearing, level);
        BlockPos localPos = bearing.getMountedLocalPos();
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
    private ServerSubLevel resolveChild(VectorBearingBlockEntity bearing, ServerLevel level) {
        ServerSubLevel child = findChild(bearing, level);
        return child != null && !child.isRemoved() ? child : null;
    }

    // Find the child
    private ServerSubLevel findChild(VectorBearingBlockEntity bearing, ServerLevel level) {
        UUID id = bearing.getMountedSubLevelId();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = id == null || container == null ? null : container.getSubLevel(id);
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Store the tilt candidate
    private record TiltCandidate(Vector3d baseAnchor, Quaterniond baseFrameOrientation) {
    }
}
