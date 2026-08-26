package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.collider.SableCollisionContext;
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
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Build and update the moving scissor piston bodies without losing their physical joints
final class ScissorPistonMountedAssembly {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double COLLISION_EPSILON = 1.0E-5D;
    private static final double CONTACT_EPSILON = 1.0E-4D;
    private static final double MOVEMENT_EPSILON = 1.0E-6D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current head lock constraint
    private PhysicsConstraintHandle headLockConstraint;
    // Current head lock parent
    private ServerSubLevel headLockParent;
    // Current head lock sub-level id
    private UUID headLockSubLevelId;
    // Head lock position1
    private final Vector3d headLockPosition1 = new Vector3d(Double.NaN);
    // Head lock position2
    private final Vector3d headLockPosition2 = new Vector3d(Double.NaN);
    // Head lock orientation
    private final Quaterniond headLockOrientation = new Quaterniond();
    // Tracks whether pose is dirty
    private boolean poseDirty = true;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Mark the pose dirty
    void markPoseDirty() {
        poseDirty = true;
    }

    // Check if this needs pose update
    boolean needsPoseUpdate() {
        return shouldApplyPose(poseDirty, hasValidHeadLockConstraint());
    }

    // Check if this should apply pose
    static boolean shouldApplyPose(boolean poseDirty, boolean constraintValid) {
        return poseDirty || !constraintValid;
    }

    // Assemble the scissor piston mounted assembly
    boolean assemble(ScissorPistonBlockEntity piston, ServerLevel level) {
        BlockPos mountedPos = piston.getMountedBlockPos();
        ServerSubLevel child;
        BlockPos linkLocalPos;
        BlockState mountedState = level.getBlockState(mountedPos);
        if (mountedState.isAir()) {
            child = createLinkOnlySubLevel(piston, level);
            if (child == null) {
                return false;
            }
            linkLocalPos = child.getPlot().getCenterBlock();
        } else {
            if (isParentSubLevelLinkBlock(piston, level, mountedPos, mountedState)) {
                return false;
            }
            SimAssemblyHelper.AssemblyResult res;
            try {
                res = SimAssemblyHelper.assembleFromSingleBlock(level, piston.getBlockPos(), mountedPos, false, false);
            } catch (AssemblyException ignored) {
                return false;
            }
            if (res == null || !(res.subLevel() instanceof ServerSubLevel assembledChild)) {
                return false;
            }
            child = assembledChild;
            linkLocalPos = piston.getBlockPos().offset(res.offset());
            placeLinkBlock(piston, child, linkLocalPos);
        }

        if (resolveMountedState(child, linkLocalPos) == null) {
            clearChildSubLevel(level, child);
            return false;
        }
        if (!configLinkBlockEntity(piston, level, child, linkLocalPos)) {
            clearChildSubLevel(level, child);
            return false;
        }

        piston.setMountedAssembly(child.getUniqueId(), linkLocalPos);
        applyPose(piston, level);
        return true;
    }

    // Check if this has mounted block
    boolean hasMountedBlock(ScissorPistonBlockEntity piston, ServerLevel level) {
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos localPos = piston.getMountedLocalPos();
        if (child == null || localPos == null) {
            return false;
        }
        BlockState mountedState = resolveMountedState(child, localPos);
        return mountedState != null && mountedState.getBlock() instanceof ScissorPistonLinkBlock;
    }

    // Recover the mounted piston block
    boolean recoverMountedBlock(ScissorPistonBlockEntity piston, ServerLevel level) {
        ScissorPistonLinkBlockEntity link = findLinkedBlockEntity(piston, level);
        if (link == null) {
            return false;
        }
        UUID childId = SimulatedHelper.getContainingSubLevelId(link);
        if (childId == null) {
            return false;
        }
        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, childId);
        if (!(subLevel instanceof ServerSubLevel child) || child.isRemoved()) {
            return false;
        }
        BlockState mountedState = resolveMountedState(child, link.getBlockPos());
        if (mountedState == null || !(mountedState.getBlock() instanceof ScissorPistonLinkBlock)) {
            return false;
        }
        link.setParent(piston);
        piston.updateMountedAssemblyFromLink(child.getUniqueId(), link.getBlockPos());
        return true;
    }

    // Find the linked block entity
    private ScissorPistonLinkBlockEntity findLinkedBlockEntity(ScissorPistonBlockEntity piston, ServerLevel level) {
        Object storedSubLevel = SubLevelBlockEntityCollector.getSubLevel(level, piston.getMountedSubLevelId());
        BlockPos storedLocalPos = piston.getMountedLocalPos();
        BlockEntity storedBlockEntity = SubLevelBlockEntityCollector.getBlockEntity(storedSubLevel, storedLocalPos);
        if (storedBlockEntity instanceof ScissorPistonLinkBlockEntity link && link.isLinkedTo(piston)) {
            return link;
        }

        for (Object subLevel : SubLevelBlockEntityCollector.getSubLevels(level)) {
            if (!(subLevel instanceof ServerSubLevel child) || child.isRemoved()) {
                continue;
            }
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(child)) {
                if (blockEntity instanceof ScissorPistonLinkBlockEntity link && link.isLinkedTo(piston)) {
                    return link;
                }
            }
        }
        return null;
    }

    // Apply the pose
    void applyPose(ScissorPistonBlockEntity piston, ServerLevel level) {
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos linkLocalPos = piston.getMountedLocalPos();
        if (child == null || linkLocalPos == null) {
            clearHeadLockConstraint();
            return;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        piston.beginInternalArmBlockUpdate();
        try {
            syncArmBlocks(piston, level, child, linkLocalPos);
            configLinkBlockEntity(piston, level, child, linkLocalPos);
        } finally {
            piston.endInternalArmBlockUpdate();
        }

        boolean poseLocked = false;
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        try {
            TargetPose targetPose = targetPoseForExtension(
                    piston, child, linkLocalPos, piston.getExtension());
            if (targetPose != null) {
                ServerSubLevel parent = targetPose.parent();
                boolean lockChanged = refreshHeadLockConstraint(piston, child, pipeline, targetPose);
                boolean lockValid = hasValidHeadLockConstraint();
                if (shouldRealignHead(lockChanged, lockValid)) {
                    teleportConnectedChain(piston, child, pipeline, targetPose, false, parent == null);
                    if (parent != null) {
                        pipeline.wakeUp(parent);
                    }
                }
                poseLocked = lockValid;
            }
        } catch (RuntimeException err) {
            poseLocked = false;
            LOGGER.warn("Scissor Piston pose update failed at {}: {}", piston.getBlockPos(), err.toString());
        } finally {
            poseDirty = !poseLocked;
        }
    }

    // Carry the contacting sub levels
    void carryContactingSubLevels(ScissorPistonBlockEntity piston, ServerLevel level,
                                  double fromExtension, double toExtension) {
        if (Math.abs(toExtension - fromExtension) <= MOVEMENT_EPSILON) {
            return;
        }
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos linkLocalPos = piston.getMountedLocalPos();
        if (child == null || linkLocalPos == null) {
            return;
        }
        TargetPose previousPose = targetPoseForExtension(piston, child, linkLocalPos, fromExtension);
        TargetPose targetPose = targetPoseForExtension(piston, child, linkLocalPos, toExtension);
        if (previousPose == null || targetPose == null) {
            return;
        }
        Vector3d movement = new Vector3d(targetPose.position()).sub(previousPose.position());
        if (movement.lengthSquared() <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return;
        }

        AABB previousBounds = transformedSubLevelBounds(child, previousPose);
        AABB targetBounds = transformedSubLevelBounds(child, targetPose);
        if (previousBounds == null || targetBounds == null) {
            return;
        }
        AABB searchBounds = union(previousBounds, targetBounds).inflate(CONTACT_EPSILON);
        Direction.Axis movementAxis = dominantAxis(movement);
        double movementOnAxis = movementComponent(movement, movementAxis);
        ServerSubLevel parentSubLevel = targetPose.parent();
        UUID childId = child.getUniqueId();
        UUID parentId = parentSubLevel == null ? null : parentSubLevel.getUniqueId();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Set<UUID> movedSubLevels = new HashSet<>();

        for (Object candidateObject : SimulatedHelper.getIntersectingSubLevels(level, searchBounds)) {
            if (!(candidateObject instanceof ServerSubLevel candidate) || candidate.isRemoved()) {
                continue;
            }
            UUID candidateId = candidate.getUniqueId();
            if (childId.equals(candidateId)
                    || parentId != null && parentId.equals(candidateId)
                    || !movedSubLevels.add(candidateId)) {
                continue;
            }
            candidate.updateBoundingBox();
            if (!subLevelShapesTouchAlongMovementAxis(child, previousPose, candidate, movementAxis, movementOnAxis)) {
                continue;
            }
            try {
                Vector3d movedPosition = new Vector3d(candidate.logicalPose().position()).add(movement);
                pipeline.teleport(candidate, movedPosition, candidate.logicalPose().orientation());
                candidate.updateBoundingBox();
                pipeline.wakeUp(candidate);
            } catch (RuntimeException err) {
                LOGGER.warn("Scissor Piston contact carry failed at {}: {}", piston.getBlockPos(), err.toString());
            }
        }
    }

    // Get the transformed sublevel bounds
    private AABB transformedSubLevelBounds(ServerSubLevel subLevel, TargetPose pose) {
        var bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) {
            return null;
        }
        return transformBox(pose,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1.0D, bounds.maxY() + 1.0D, bounds.maxZ() + 1.0D);
    }

    // Get the union
    private AABB union(AABB first, AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ));
    }

    // Get the dominant axis
    private Direction.Axis dominantAxis(Vector3d movement) {
        double x = Math.abs(movement.x);
        double y = Math.abs(movement.y);
        double z = Math.abs(movement.z);
        if (y >= x && y >= z) {
            return Direction.Axis.Y;
        }
        return z >= x ? Direction.Axis.Z : Direction.Axis.X;
    }

    // Get the movement component
    private double movementComponent(Vector3d movement, Direction.Axis axis) {
        return switch (axis) {
            case X -> movement.x;
            case Y -> movement.y;
            case Z -> movement.z;
        };
    }

    // Check if the bounds touch along the movement axis
    private boolean touchesAlongMovementAxis(AABB movingBounds, AABB candidateBounds,
                                             Direction.Axis axis, double movementOnAxis) {
        return touchesAlongMovementAxis(movingBounds, candidateBounds, axis, movementOnAxis, CONTACT_EPSILON);
    }

    // Check if the block bounds touch along the movement axis
    private boolean touchesAlongMovementAxis(AABB movingBounds, AABB candidateBounds,
                                             Direction.Axis axis, double movementOnAxis, double tolerance) {
        if (!overlapsPerpendicularAxes(movingBounds, candidateBounds, axis, tolerance)) {
            return false;
        }
        if (Math.abs(movementOnAxis) <= MOVEMENT_EPSILON) {
            return false;
        }
        double movingMin = minOnAxis(movingBounds, axis);
        double movingMax = maxOnAxis(movingBounds, axis);
        double candidateMin = minOnAxis(candidateBounds, axis);
        double candidateMax = maxOnAxis(candidateBounds, axis);
        return movementOnAxis > 0.0D
                ? Math.abs(candidateMin - movingMax) <= tolerance
                : Math.abs(candidateMax - movingMin) <= tolerance;
    }

    // Check if the sublevel shapes touch along the movement axis
    private boolean subLevelShapesTouchAlongMovementAxis(ServerSubLevel movingSubLevel, TargetPose movingPose,
                                                         ServerSubLevel candidate, Direction.Axis axis,
                                                         double movementOnAxis) {
        return subLevelShapesTouchAlongMovementAxis(movingSubLevel, movingPose, candidate, axis, movementOnAxis,
                CONTACT_EPSILON);
    }

    // Check if the sublevel shapes touch along the movement axis
    private boolean subLevelShapesTouchAlongMovementAxis(ServerSubLevel movingSubLevel, TargetPose movingPose,
                                                         ServerSubLevel candidate, Direction.Axis axis,
                                                         double movementOnAxis, double tolerance) {
        if (Math.abs(movementOnAxis) <= MOVEMENT_EPSILON) {
            return false;
        }
        List<AABB> candidateBoxes = collectSubLevelCollisionBoxes(candidate, currentPose(candidate));
        if (candidateBoxes.isEmpty()) {
            return false;
        }

        ServerLevelPlot movingPlot = movingSubLevel.getPlot();
        BlockGetter movingLevel = movingPlot.getEmbeddedLevelAccessor();
        BlockPos plotCenter = movingPlot.getCenterBlock();
        for (var chunkHolder : movingPlot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            ChunkPos chunkPos = chunk.getPos();
            int chunkMinX = chunkPos.getMinBlockX();
            int chunkMinZ = chunkPos.getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (shouldSkipMovingBlock(state)) {
                                continue;
                            }
                            BlockPos localPos = new BlockPos(chunkMinX + x, sectionMinY + y, chunkMinZ + z);
                            BlockPos relativePos = localPos.subtract(plotCenter);
                            VoxelShape shape = subLevelCollisionShape(movingLevel, relativePos, state);
                            if (!shape.isEmpty() && shapeTouchesCandidateBoxes(shape, localPos, movingPose,
                                    candidateBoxes, axis, movementOnAxis, tolerance)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    // Collect the sublevel collision boxes
    private List<AABB> collectSubLevelCollisionBoxes(ServerSubLevel subLevel, TargetPose pose) {
        List<AABB> boxes = new ArrayList<>();
        ServerLevelPlot plot = subLevel.getPlot();
        BlockGetter embeddedLevel = plot.getEmbeddedLevelAccessor();
        BlockPos plotCenter = plot.getCenterBlock();
        for (var chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            ChunkPos chunkPos = chunk.getPos();
            int chunkMinX = chunkPos.getMinBlockX();
            int chunkMinZ = chunkPos.getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (shouldSkipMovingBlock(state)) {
                                continue;
                            }
                            BlockPos localPos = new BlockPos(chunkMinX + x, sectionMinY + y, chunkMinZ + z);
                            BlockPos relativePos = localPos.subtract(plotCenter);
                            VoxelShape shape = subLevelCollisionShape(embeddedLevel, relativePos, state);
                            if (!shape.isEmpty()) {
                                addTransformedShapeBoxes(boxes, shape, localPos, pose);
                            }
                        }
                    }
                }
            }
        }
        return boxes;
    }

    // Add the transformed shape boxes
    private void addTransformedShapeBoxes(List<AABB> boxes, VoxelShape shape, BlockPos localPos, TargetPose pose) {
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(transformBox(pose,
                localPos.getX() + minX, localPos.getY() + minY, localPos.getZ() + minZ,
                localPos.getX() + maxX, localPos.getY() + maxY, localPos.getZ() + maxZ)));
    }

    // Check if the shape touches any candidate box
    private boolean shapeTouchesCandidateBoxes(VoxelShape shape, BlockPos localPos, TargetPose pose,
                                               List<AABB> candidateBoxes, Direction.Axis axis,
                                               double movementOnAxis) {
        return shapeTouchesCandidateBoxes(shape, localPos, pose, candidateBoxes, axis, movementOnAxis,
                CONTACT_EPSILON);
    }

    // Check if the shape touches any candidate box
    private boolean shapeTouchesCandidateBoxes(VoxelShape shape, BlockPos localPos, TargetPose pose,
                                               List<AABB> candidateBoxes, Direction.Axis axis,
                                               double movementOnAxis, double tolerance) {
        boolean[] touches = new boolean[1];
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (touches[0]) {
                return;
            }
            AABB movingBox = transformBox(pose,
                    localPos.getX() + minX, localPos.getY() + minY, localPos.getZ() + minZ,
                    localPos.getX() + maxX, localPos.getY() + maxY, localPos.getZ() + maxZ);
            for (AABB candidateBox : candidateBoxes) {
                if (touchesAlongMovementAxis(movingBox, candidateBox, axis, movementOnAxis, tolerance)) {
                    touches[0] = true;
                    return;
                }
            }
        });
        return touches[0];
    }

    // Check if this should realign head
    static boolean shouldRealignHead(boolean lockChanged, boolean lockValid) {
        return lockChanged || !lockValid;
    }

    // Check if the boxes overlap on the perpendicular axes
    private boolean overlapsPerpendicularAxes(AABB first, AABB second, Direction.Axis movementAxis) {
        return overlapsPerpendicularAxes(first, second, movementAxis, CONTACT_EPSILON);
    }

    // Check if the boxes overlap on the perpendicular axes
    private boolean overlapsPerpendicularAxes(AABB first, AABB second, Direction.Axis movementAxis,
                                              double tolerance) {
        return switch (movementAxis) {
            case X -> rangesOverlap(first.minY, first.maxY, second.minY, second.maxY, tolerance)
                    && rangesOverlap(first.minZ, first.maxZ, second.minZ, second.maxZ, tolerance);
            case Y -> rangesOverlap(first.minX, first.maxX, second.minX, second.maxX, tolerance)
                    && rangesOverlap(first.minZ, first.maxZ, second.minZ, second.maxZ, tolerance);
            case Z -> rangesOverlap(first.minX, first.maxX, second.minX, second.maxX, tolerance)
                    && rangesOverlap(first.minY, first.maxY, second.minY, second.maxY, tolerance);
        };
    }

    // Check if the ranges overlap
    private boolean rangesOverlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return rangesOverlap(firstMin, firstMax, secondMin, secondMax, CONTACT_EPSILON);
    }

    // Check if the ranges overlap
    private boolean rangesOverlap(double firstMin, double firstMax, double secondMin, double secondMax,
                                  double tolerance) {
        return firstMax + tolerance > secondMin && secondMax + tolerance > firstMin;
    }

    // Get the minimum axis
    private double minOnAxis(AABB bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.minX;
            case Y -> bounds.minY;
            case Z -> bounds.minZ;
        };
    }

    // Get the maximum axis
    private double maxOnAxis(AABB bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.maxX;
            case Y -> bounds.maxY;
            case Z -> bounds.maxZ;
        };
    }

    // Clamp the extension for collision
    double clampExtensionForCollision(ScissorPistonBlockEntity piston, ServerLevel level,
                                      double currentExtension, double desiredExtension) {
        if (Math.abs(desiredExtension - currentExtension) <= 1.0E-6D) {
            return desiredExtension;
        }
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos linkLocalPos = piston.getMountedLocalPos();
        if (child == null || linkLocalPos == null) {
            return desiredExtension;
        }
        TargetPose desiredPose = targetPoseForExtension(piston, child, linkLocalPos, desiredExtension);
        if (desiredPose == null) {
            return desiredExtension;
        }
        if (!childCollidesWithWorldAtPose(piston, level, child, desiredPose)) {
            return desiredExtension;
        }
        if (isExtensionBlockedByWorld(piston, level, child, linkLocalPos, currentExtension)) {
            return currentExtension;
        }

        double free = currentExtension;
        double blocked = desiredExtension;
        for (int i = 0; i < 8; i++) {
            double mid = (free + blocked) * 0.5D;
            if (isExtensionBlockedByWorld(piston, level, child, linkLocalPos, mid)) {
                blocked = mid;
            } else {
                free = mid;
            }
        }
        return Math.abs(free - currentExtension) <= 1.0E-5D ? currentExtension : free;
    }

    // Check if this is extension blocked by world
    private boolean isExtensionBlockedByWorld(ScissorPistonBlockEntity piston, ServerLevel level,
                                              ServerSubLevel child, BlockPos linkLocalPos, double extension) {
        TargetPose targetPose = targetPoseForExtension(piston, child, linkLocalPos, extension);
        return targetPose != null && childCollidesWithWorldAtPose(piston, level, child, targetPose);
    }

    // Check if the child collides with the world at this pose
    private boolean childCollidesWithWorldAtPose(ScissorPistonBlockEntity piston, ServerLevel level,
                                                 ServerSubLevel child, TargetPose targetPose) {
        ServerLevelPlot plot = child.getPlot();
        BlockGetter embeddedLevel = plot.getEmbeddedLevelAccessor();
        BlockPos plotCenter = plot.getCenterBlock();
        SubLevel parentSubLevel = targetPose.parent();
        for (var chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            ChunkPos chunkPos = chunk.getPos();
            int chunkMinX = chunkPos.getMinBlockX();
            int chunkMinZ = chunkPos.getMinBlockZ();
            LevelChunkSection[] sections = chunk.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()) {
                    continue;
                }
                int sectionMinY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, y, z);
                            if (shouldSkipMovingBlock(state)) {
                                continue;
                            }
                            BlockPos localPos = new BlockPos(chunkMinX + x, sectionMinY + y, chunkMinZ + z);
                            BlockPos relativePos = localPos.subtract(plotCenter);
                            VoxelShape shape = subLevelCollisionShape(embeddedLevel, relativePos, state);
                            if (!shape.isEmpty() && movingShapeIntersectsWorld(piston, level, parentSubLevel,
                                    localPos, shape, targetPose)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    // Detach the scissor piston mounted assembly
    void detach(ScissorPistonBlockEntity piston, ServerLevel level, boolean removeLinkBlock) {
        clearHeadLockConstraint();
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos localPos = piston.getMountedLocalPos();
        try {
            if (child != null && localPos != null && !child.isRemoved()) {
                piston.beginInternalArmBlockUpdate();
                try {
                    destroyArmBlocks(level, piston, child, localPos);
                } finally {
                    piston.endInternalArmBlockUpdate();
                }
                if (removeLinkBlock) {
                    destroyLinkBlock(level, child, localPos);
                }
            }
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Scissor Piston head detach failed at {}: {}", piston.getBlockPos(), error.toString());
        } finally {
            piston.setMountedAssembly(null, null);
        }
    }

    // Disassemble the scissor piston mounted assembly
    void disassemble(ScissorPistonBlockEntity piston, ServerLevel level) {
        clearHeadLockConstraint();
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos localPos = piston.getMountedLocalPos();
        if (child != null && localPos != null && !child.isRemoved()) {
            SubLevel containing = NestedAssemblyFrame.resolve(piston).parent();
            if (containing != null && containing.isRemoved()) {
                piston.beginInternalArmBlockUpdate();
                try {
                    destroyArmBlocks(level, piston, child, localPos);
                } finally {
                    piston.endInternalArmBlockUpdate();
                }
                destroyLinkBlock(level, child, localPos);
                piston.setMountedAssembly(null, null);
                return;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                teleportChildToExtension(piston, child, container.physicsSystem().getPipeline(), 0.0D, true);
            }
            piston.beginInternalArmBlockUpdate();
            try {
                destroyArmBlocks(level, piston, child, localPos);
            } finally {
                piston.endInternalArmBlockUpdate();
            }
            destroyLinkBlock(level, child, localPos);
            SimAssemblyHelper.disassembleSubLevel(level, child, localPos, piston.getBlockPos(), Rotation.NONE, true);
        }
        piston.setMountedAssembly(null, null);
    }

    // Remove the head after failed disassembly
    void removeHeadAfterFailedDisassembly(ScissorPistonBlockEntity piston, ServerLevel level) {
        clearHeadLockConstraint();
        ServerSubLevel child = resolveChild(piston, level);
        BlockPos localPos = piston.getMountedLocalPos();
        if (child != null && localPos != null && !child.isRemoved()) {
            piston.beginInternalArmBlockUpdate();
            try {
                try {
                    destroyArmBlocks(level, piston, child, localPos);
                } catch (RuntimeException | LinkageError ignored) {
                }
                try {
                    destroyLinkBlock(level, child, localPos);
                } catch (RuntimeException | LinkageError ignored) {
                }
            } finally {
                piston.endInternalArmBlockUpdate();
            }
        }
        piston.setMountedAssembly(null, null);
    }

    // Check if this is a parent sublevel link block
    private boolean isParentSubLevelLinkBlock(ScissorPistonBlockEntity piston, ServerLevel level,
                                              BlockPos mountedPos, BlockState mountedState) {
        if (!(mountedState.getBlock() instanceof ScissorPistonLinkBlock)) {
            return false;
        }
        SubLevel pistonSubLevel = NestedAssemblyFrame.resolve(piston).parent();
        SubLevel mountedSubLevel = Sable.HELPER.getContaining(level, mountedPos);
        return pistonSubLevel == mountedSubLevel
                || pistonSubLevel != null && mountedSubLevel != null
                && pistonSubLevel.getUniqueId().equals(mountedSubLevel.getUniqueId());
    }

    // Create the link only sublevel
    private ServerSubLevel createLinkOnlySubLevel(ScissorPistonBlockEntity piston, ServerLevel level) {
        NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(piston);
        SubLevel containingSubLevel = parentFrame.parent();
        if (parentFrame.isRemoved()) {
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        BlockPos anchorPos = piston.getBlockPos();
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
        plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, linkState(piston), 3);
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
    private BlockState linkState(ScissorPistonBlockEntity piston) {
        return CTBlocks.SCISSOR_PISTON_LINK.get()
                .defaultBlockState()
                .setValue(ScissorPistonLinkBlock.FACING, piston.getPistonFacing());
    }

    // Get the arm state
    private BlockState armState(ScissorPistonBlockEntity piston, int distanceFromHead) {
        return CTBlocks.SCISSOR_PISTON_ARM.get()
                .defaultBlockState()
                .setValue(ScissorPistonArmBlock.FACING, piston.getPistonFacing())
                .setValue(ScissorPistonArmBlock.EXPOSED,
                        isArmCellExposed(distanceFromHead, piston.getExtension()));
    }

    // Place the link block
    private void placeLinkBlock(ScissorPistonBlockEntity piston, ServerSubLevel child, BlockPos linkLocalPos) {
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, linkState(piston), 3);
    }

    // Configure the link block entity
    private boolean configLinkBlockEntity(ScissorPistonBlockEntity piston, ServerLevel level, ServerSubLevel child,
                                             BlockPos linkLocalPos) {
        BlockEntity blockEntity = level.getBlockEntity(linkLocalPos);
        ScissorPistonLinkBlockEntity link = blockEntity instanceof ScissorPistonLinkBlockEntity linkBlockEntity
                ? linkBlockEntity
                : SimulatedHelper.findBlockEntityInSubLevel(child, linkLocalPos, ScissorPistonLinkBlockEntity.class);
        if (link == null) {
            return false;
        }
        link.setParent(piston);
        return true;
    }

    // Sync the arm blocks
    private void syncArmBlocks(ScissorPistonBlockEntity piston, ServerLevel level, ServerSubLevel child,
                               BlockPos linkLocalPos) {
        Direction facing = piston.getPistonFacing();
        int activeRange = piston.getEffectiveMaxRange();
        int rangeLimit = piston.getConfiguredRangeLimit();
        ServerLevelPlot plot = child.getPlot();
        for (int distance = 1; distance <= rangeLimit; distance++) {
            BlockPos armLocalPos = linkLocalPos.relative(facing.getOpposite(), distance);
            BlockPos relativePos = armLocalPos.subtract(plot.getCenterBlock());
            if (distance <= activeRange) {
                ensurePlotChunk(plot, relativePos);
            }
            BlockState current = plot.getEmbeddedLevelAccessor().getBlockState(relativePos);
            if (distance <= activeRange) {
                if (current.getBlock() instanceof ScissorPistonArmBlock) {
                    BlockState desiredState = armState(piston, distance);
                    if (!current.equals(desiredState)) {
                        markArmRemoving(level, child, armLocalPos);
                        plot.getEmbeddedLevelAccessor().setBlock(relativePos, desiredState, 3);
                    }
                } else {
                    if (!current.isAir()) {
                        continue;
                    }
                    plot.getEmbeddedLevelAccessor().setBlock(relativePos, armState(piston, distance), 3);
                }
                configArmBlockEntity(piston, level, child, armLocalPos, distance);
            } else if (current.getBlock() instanceof ScissorPistonArmBlock) {
                markArmRemoving(level, child, armLocalPos);
                plot.getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // Check if this is arm cell exposed
    private boolean isArmCellExposed(int distanceFromHead, double extension) {
        return extension > distanceFromHead + 1.0E-5D;
    }

    // Configure the arm block entity
    private void configArmBlockEntity(ScissorPistonBlockEntity piston, ServerLevel level, ServerSubLevel child,
                                         BlockPos armLocalPos, int distanceFromHead) {
        BlockEntity blockEntity = level.getBlockEntity(armLocalPos);
        ScissorPistonArmBlockEntity arm = blockEntity instanceof ScissorPistonArmBlockEntity armBlockEntity
                ? armBlockEntity
                : SimulatedHelper.findBlockEntityInSubLevel(child, armLocalPos, ScissorPistonArmBlockEntity.class);
        if (arm != null) {
            arm.configureFromParent(piston, distanceFromHead);
        }
    }

    // Destroy the arm blocks
    private void destroyArmBlocks(ServerLevel level, ScissorPistonBlockEntity piston, ServerSubLevel child,
                                  BlockPos linkLocalPos) {
        Direction facing = piston.getPistonFacing();
        ServerLevelPlot plot = child.getPlot();
        for (int distance = 1; distance <= piston.getConfiguredRangeLimit(); distance++) {
            BlockPos armLocalPos = linkLocalPos.relative(facing.getOpposite(), distance);
            markArmRemoving(level, child, armLocalPos);
            if (level.getBlockState(armLocalPos).getBlock() instanceof ScissorPistonArmBlock) {
                level.setBlock(armLocalPos, Blocks.AIR.defaultBlockState(), 2);
                continue;
            }
            BlockPos relativePos = armLocalPos.subtract(plot.getCenterBlock());
            if (plot.getEmbeddedLevelAccessor().getBlockState(relativePos).getBlock() instanceof ScissorPistonArmBlock) {
                plot.getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // Mark the arm removing
    private void markArmRemoving(ServerLevel level, ServerSubLevel child, BlockPos armLocalPos) {
        BlockEntity blockEntity = level.getBlockEntity(armLocalPos);
        ScissorPistonArmBlockEntity arm = blockEntity instanceof ScissorPistonArmBlockEntity armBlockEntity
                ? armBlockEntity
                : SimulatedHelper.findBlockEntityInSubLevel(child, armLocalPos, ScissorPistonArmBlockEntity.class);
        if (arm != null) {
            arm.markRemovingByPiston();
        }
    }

    // Ensure the plot chunk
    private void ensurePlotChunk(ServerLevelPlot plot, BlockPos relativePos) {
        BlockPos globalPos = relativePos.offset(plot.getCenterBlock());
        ChunkPos globalChunk = new ChunkPos(globalPos);
        if (plot.getChunk(plot.toLocal(globalChunk)) == null) {
            plot.newEmptyChunk(globalChunk);
        }
    }

    // Check if the moving shape intersects the world
    private boolean movingShapeIntersectsWorld(ScissorPistonBlockEntity piston, ServerLevel level,
                                               SubLevel parentSubLevel, BlockPos localPos, VoxelShape shape,
                                               TargetPose targetPose) {
        boolean[] blocked = new boolean[1];
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (blocked[0]) {
                return;
            }
            AABB movingBox = transformBox(targetPose,
                    localPos.getX() + minX, localPos.getY() + minY, localPos.getZ() + minZ,
                    localPos.getX() + maxX, localPos.getY() + maxY, localPos.getZ() + maxZ);
            blocked[0] = worldBlocksIntersect(piston, level, parentSubLevel, movingBox);
        });
        return blocked[0];
    }

    // Check if any world block intersects the moving box
    private boolean worldBlocksIntersect(ScissorPistonBlockEntity piston, ServerLevel level,
                                         SubLevel parentSubLevel, AABB movingBox) {
        BlockPos skippedPos = parentSubLevel == null ? piston.getBlockPos() : null;
        return worldBlocksIntersect(level, movingBox, skippedPos);
    }

    // Check if any world block intersects the moving box
    private boolean worldBlocksIntersect(ServerLevel level, AABB movingBox, BlockPos skippedPos) {
        for (BlockPos pos : BlockPos.betweenClosed(scanMin(movingBox.minX), scanMin(movingBox.minY),
                scanMin(movingBox.minZ), scanMax(movingBox.maxX), scanMax(movingBox.maxY),
                scanMax(movingBox.maxZ))) {
            if ((skippedPos != null && pos.equals(skippedPos)) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (shouldSkipBlocker(state)) {
                continue;
            }
            VoxelShape shape = subLevelCollisionShape(level, pos, state);
            if (!shape.isEmpty() && shapeIntersectsWorldBox(shape, pos, movingBox)) {
                return true;
            }
        }
        return false;
    }

    // Get the sublevel collision shape
    private VoxelShape subLevelCollisionShape(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BlockSubLevelCollisionShape customShape) {
            return customShape.getSubLevelCollisionShape(level, state);
        }
        return state.getCollisionShape(level, pos, SableCollisionContext.get());
    }

    // Check if this should skip moving block
    private boolean shouldSkipMovingBlock(BlockState state) {
        return state.isAir()
                || state.getBlock() instanceof ScissorPistonArmBlock;
    }

    // Check if this should skip blocker
    private boolean shouldSkipBlocker(BlockState state) {
        return state.isAir()
                || state.getBlock() instanceof ScissorPistonArmBlock
                || state.getBlock() instanceof ScissorPistonLinkBlock;
    }

    // Check if the shape intersects the world box
    private boolean shapeIntersectsWorldBox(VoxelShape shape, BlockPos blockPos, AABB movingBox) {
        boolean[] intersects = new boolean[1];
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            if (intersects[0]) {
                return;
            }
            AABB blockerBox = new AABB(
                    blockPos.getX() + minX, blockPos.getY() + minY, blockPos.getZ() + minZ,
                    blockPos.getX() + maxX, blockPos.getY() + maxY, blockPos.getZ() + maxZ);
            intersects[0] = intersectsStrictly(movingBox, blockerBox);
        });
        return intersects[0];
    }

    // Check if the boxes strictly intersect
    private boolean intersectsStrictly(AABB first, AABB second) {
        return first.maxX - COLLISION_EPSILON > second.minX
                && second.maxX - COLLISION_EPSILON > first.minX
                && first.maxY - COLLISION_EPSILON > second.minY
                && second.maxY - COLLISION_EPSILON > first.minY
                && first.maxZ - COLLISION_EPSILON > second.minZ
                && second.maxZ - COLLISION_EPSILON > first.minZ;
    }

    // Transform the box
    private AABB transformBox(TargetPose pose, double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ) {
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        includeTransformedCorner(pose, minX, minY, minZ, min, max);
        includeTransformedCorner(pose, maxX, minY, minZ, min, max);
        includeTransformedCorner(pose, minX, maxY, minZ, min, max);
        includeTransformedCorner(pose, maxX, maxY, minZ, min, max);
        includeTransformedCorner(pose, minX, minY, maxZ, min, max);
        includeTransformedCorner(pose, maxX, minY, maxZ, min, max);
        includeTransformedCorner(pose, minX, maxY, maxZ, min, max);
        includeTransformedCorner(pose, maxX, maxY, maxZ, min, max);
        return new AABB(min.x, min.y, min.z, max.x, max.y, max.z);
    }

    // Transform the box
    private AABB transformBox(TargetPose pose, AABB box) {
        return transformBox(pose, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    // Include the transformed corner
    private void includeTransformedCorner(TargetPose pose, double x, double y, double z,
                                          Vector3d min, Vector3d max) {
        Vector3d transformed = new Vector3d(x, y, z)
                .sub(pose.rotationPoint())
                .rotate(pose.orientation())
                .add(pose.position());
        includePoint(transformed, min, max);
    }

    // Include the point
    private void includePoint(Vector3d point, Vector3d min, Vector3d max) {
        min.min(point);
        max.max(point);
    }

    // Scan the min
    private int scanMin(double coordinate) {
        return Mth.floor(coordinate - COLLISION_EPSILON);
    }

    // Scan the max
    private int scanMax(double coordinate) {
        return Mth.floor(coordinate + COLLISION_EPSILON);
    }

    // Teleport the child to the extension
    private TargetPose teleportChildToExtension(ScissorPistonBlockEntity piston, ServerSubLevel child,
                                                PhysicsPipeline pipeline, double extension,
                                                boolean includeLoadingDependents) {
        BlockPos linkLocalPos = piston.getMountedLocalPos();
        if (linkLocalPos == null) {
            return null;
        }
        TargetPose targetPose = targetPoseForExtension(piston, child, linkLocalPos, extension);
        if (targetPose != null) {
            teleportConnectedChain(piston, child, pipeline, targetPose, includeLoadingDependents, true);
        }
        return targetPose;
    }

    // Teleport the connected chain
    private void teleportConnectedChain(ScissorPistonBlockEntity piston, ServerSubLevel child,
                                        PhysicsPipeline pipeline, TargetPose targetPose,
                                        boolean includeLoadingDependents, boolean resetVelocity) {
        moveConnectedDependents(piston, child, pipeline, targetPose, includeLoadingDependents, resetVelocity);
        pipeline.teleport(child, targetPose.position(), targetPose.orientation());
        settleAfterTeleport(pipeline, child, resetVelocity);
    }

    // Move the connected dependents
    private void moveConnectedDependents(ScissorPistonBlockEntity piston, ServerSubLevel child,
                                         PhysicsPipeline pipeline, TargetPose targetPose,
                                         boolean includeLoadingDependents, boolean resetVelocity) {
        TargetPose previousPose = currentPose(child);
        for (ServerSubLevel dependent : connectedDependents(piston, child, includeLoadingDependents)) {
            TargetPose dependentPose = transformRelativePose(previousPose, targetPose, dependent);
            pipeline.teleport(dependent, dependentPose.position(), dependentPose.orientation());
            settleAfterTeleport(pipeline, dependent, resetVelocity);
        }
    }

    // Get the current pose
    private TargetPose currentPose(ServerSubLevel subLevel) {
        return new TargetPose(new Vector3d(subLevel.logicalPose().position()),
                new Quaterniond(subLevel.logicalPose().orientation()),
                new Vector3d(subLevel.logicalPose().rotationPoint()));
    }

    // Get the connected dependents
    private List<ServerSubLevel> connectedDependents(ScissorPistonBlockEntity piston, ServerSubLevel child,
                                                     boolean includeLoadingDependents) {
        List<ServerSubLevel> dependents = new ArrayList<>();
        Set<UUID> added = new HashSet<>();
        try {
            collectDependents(piston, child, SubLevelHelper.getConnectedChain(child), dependents, added);
            if (includeLoadingDependents) {
                collectDependents(piston, child, SubLevelHelper.getLoadingDependencyChain(child), dependents, added);
            }
        } catch (RuntimeException err) {
            LOGGER.warn("Scissor Piston dependency scan failed at {}: {}", piston.getBlockPos(), err.toString());
        }
        return dependents;
    }

    // Collect the dependents
    private void collectDependents(ScissorPistonBlockEntity piston, ServerSubLevel child,
                                   Collection<? extends SubLevel> connected,
                                   List<ServerSubLevel> dependents, Set<UUID> added) {
        SubLevel parent = NestedAssemblyFrame.resolve(piston).parent();
        UUID childId = child.getUniqueId();
        UUID parentId = parent == null ? null : parent.getUniqueId();
        for (SubLevel subLevel : connected) {
            if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
                continue;
            }
            UUID subLevelId = serverSubLevel.getUniqueId();
            if (childId.equals(subLevelId) || parentId != null && parentId.equals(subLevelId)) {
                continue;
            }
            if (added.add(subLevelId)) {
                dependents.add(serverSubLevel);
            }
        }
    }

    // Transform the relative pose
    private TargetPose transformRelativePose(TargetPose fromPose, TargetPose toPose, ServerSubLevel subLevel) {
        Quaterniond inverseFromOrientation = new Quaterniond(fromPose.orientation()).invert();
        Vector3d relativePosition = new Vector3d(subLevel.logicalPose().position()).sub(fromPose.position());
        inverseFromOrientation.transform(relativePosition);
        Vector3d pos = new Vector3d(relativePosition);
        toPose.orientation().transform(pos);
        pos.add(toPose.position());

        Quaterniond relativeOrientation = inverseFromOrientation
                .mul(subLevel.logicalPose().orientation(), new Quaterniond());
        Quaterniond orientation = new Quaterniond(toPose.orientation()).mul(relativeOrientation).normalize();
        return new TargetPose(pos, orientation, new Vector3d(subLevel.logicalPose().rotationPoint()));
    }

    // Settle the assembly after teleport
    private void settleAfterTeleport(PhysicsPipeline pipeline, ServerSubLevel subLevel, boolean resetVelocity) {
        if (resetVelocity) {
            pipeline.resetVelocity(subLevel);
        }
        subLevel.updateBoundingBox();
        subLevel.updateLastPose();
        pipeline.wakeUp(subLevel);
    }

    // Refresh the head lock constraint
    private boolean refreshHeadLockConstraint(ScissorPistonBlockEntity piston,
                                              ServerSubLevel child,
                                              PhysicsPipeline pipeline,
                                              TargetPose targetPose) {
        ServerSubLevel parent = targetPose.parent();
        UUID childId = child.getUniqueId();
        ConstraintFrame frame = targetPose.constraintFrame();
        if (frame == null) {
            clearHeadLockConstraint();
            return false;
        }
        if (headLockConstraint != null && headLockConstraint.isValid()
                && headLockParent == parent
                && childId.equals(headLockSubLevelId)
                && headLockPosition1.distanceSquared(frame.position1()) <= 1.0E-8D
                && headLockPosition2.distanceSquared(frame.position2()) <= 1.0E-8D
                && headLockOrientationDifference(frame.orientation()) <= 1.0E-5D) {
            return false;
        }
        clearHeadLockConstraint();
        try {
            Object fixedConstraint = SableConstraintApi.fixedConfiguration(
                    new Vector3d(frame.position1()),
                    new Vector3d(frame.position2()),
                    new Quaterniond(frame.orientation()));
            Object constraint = SableConstraintApi.addConstraint(pipeline, parent, child, fixedConstraint);
            headLockConstraint = constraint instanceof PhysicsConstraintHandle handle ? handle : null;
            if (headLockConstraint != null) {
                headLockConstraint.setContactsEnabled(false);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError err) {
            LOGGER.warn("Scissor Piston head lock constraint failed at {} for {}: {}",
                    piston.getBlockPos(), child, err.toString());
            headLockConstraint = null;
        }
        headLockParent = parent;
        headLockSubLevelId = childId;
        headLockPosition1.set(frame.position1());
        headLockPosition2.set(frame.position2());
        headLockOrientation.set(frame.orientation());
        return true;
    }

    // Get the head lock orientation difference
    private double headLockOrientationDifference(Quaterniond orientation) {
        return new Quaterniond(headLockOrientation).div(orientation, new Quaterniond()).angle();
    }

    // Check if this has valid head lock constraint
    private boolean hasValidHeadLockConstraint() {
        try {
            return headLockConstraint != null && headLockConstraint.isValid();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    // Clear the head lock constraint
    private void clearHeadLockConstraint() {
        try {
            if (headLockConstraint != null && headLockConstraint.isValid()) {
                headLockConstraint.remove();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        headLockConstraint = null;
        headLockParent = null;
        headLockSubLevelId = null;
        headLockPosition1.set(Double.NaN);
        headLockPosition2.set(Double.NaN);
        poseDirty = true;
    }

    // Get the target pose for extension
    private TargetPose targetPoseForExtension(ScissorPistonBlockEntity piston, ServerSubLevel child,
                                              BlockPos linkLocalPos, double extension) {
        NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(piston);
        if (parentFrame.isRemoved()) {
            return null;
        }
        Quaterniond parentOrientation = new Quaterniond();
        Vector3d desiredLinkCenter = desiredLinkCenterInParent(piston, extension);

        Vector3d localLinkCenter = new Vector3d(
                linkLocalPos.getX() + 0.5D,
                linkLocalPos.getY() + 0.5D,
                linkLocalPos.getZ() + 0.5D);
        Vector3d rotationPoint = new Vector3d(child.logicalPose().rotationPoint());
        Vector3d localOffset = localLinkCenter.sub(rotationPoint, new Vector3d());
        parentOrientation.transform(localOffset);
        Vector3d parentPosition = new Vector3d(desiredLinkCenter)
                .sub(localOffset);
        Vector3d worldPosition = parentFrame.toWorldPosition(parentPosition);
        Quaterniond worldOrientation = parentFrame.toWorldOrientation(parentOrientation);
        ConstraintFrame constraintFrame = new ConstraintFrame(
                new Vector3d(parentPosition), new Vector3d(rotationPoint), new Quaterniond(parentOrientation));
        return new TargetPose(worldPosition, worldOrientation, rotationPoint,
                parentFrame.parentBody(), constraintFrame);
    }

    // Get the desired link center in parent
    private Vector3d desiredLinkCenterInParent(ScissorPistonBlockEntity piston, double extension) {
        Direction facing = piston.getPistonFacing();
        return new Vector3d(
                piston.getBlockPos().getX() + 0.5D + facing.getStepX() * extension,
                piston.getBlockPos().getY() + 0.5D + facing.getStepY() * extension,
                piston.getBlockPos().getZ() + 0.5D + facing.getStepZ() * extension);
    }

    // Destroy the link block
    private void destroyLinkBlock(ServerLevel level, ServerSubLevel child, BlockPos linkLocalPos) {
        ScissorPistonLinkBlockEntity link = level.getBlockEntity(linkLocalPos) instanceof ScissorPistonLinkBlockEntity linkBlockEntity
                ? linkBlockEntity
                : SimulatedHelper.findBlockEntityInSubLevel(child, linkLocalPos, ScissorPistonLinkBlockEntity.class);
        if (link != null) {
            link.markRemovingByPiston();
        }
        if (level.getBlockState(linkLocalPos).getBlock() instanceof ScissorPistonLinkBlock) {
            level.setBlock(linkLocalPos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        BlockPos relativePos = linkLocalPos.subtract(child.getPlot().getCenterBlock());
        if (child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos).getBlock() instanceof ScissorPistonLinkBlock) {
            child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // Clear the child sublevel
    private void clearChildSubLevel(ServerLevel level, ServerSubLevel child) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && child != null && !child.isRemoved()) {
            container.removeSubLevel(child, SubLevelRemovalReason.REMOVED);
        }
    }

    // Resolve the mounted state
    private BlockState resolveMountedState(ServerSubLevel child, BlockPos localPos) {
        BlockPos relativePos = localPos.subtract(child.getPlot().getCenterBlock());
        BlockState state = child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos);
        return state.isAir() ? null : state;
    }

    // Resolve the child
    private ServerSubLevel resolveChild(ScissorPistonBlockEntity piston, ServerLevel level) {
        ServerSubLevel child = findChild(piston, level);
        return child != null && !child.isRemoved() ? child : null;
    }

    // Find the child
    private ServerSubLevel findChild(ScissorPistonBlockEntity piston, ServerLevel level) {
        UUID id = piston.getMountedSubLevelId();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = id == null || container == null ? null : container.getSubLevel(id);
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Store the target pose
    private record TargetPose(Vector3d position, Quaterniond orientation, Vector3d rotationPoint,
                              ServerSubLevel parent, ConstraintFrame constraintFrame) {
        // Initialize the target pose
        private TargetPose(Vector3d pos, Quaterniond orientation, Vector3d rotationPoint) {
            this(pos, orientation, rotationPoint, null, null);
        }
    }

    // Store the constraint frame
    private record ConstraintFrame(Vector3d position1, Vector3d position2, Quaterniond orientation) {
    }
}
