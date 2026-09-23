package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimMathUtils;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.createmod.catnip.math.AngleHelper;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

// Run the physical gantry carriage and keep its Sable attachment in step with Create kinetics
public class PhysicsGantryCarriageBlockEntity extends KineticBlockEntity
    implements IDisplayAssemblyExceptions, BlockEntitySubLevelActor {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();
    private static final boolean ENABLE_GANTRY_DEBUG_VISUALS = false;
    private static final int MAX_SHAFT_SCAN_BLOCKS = 2048;
    private static final int MAX_TRACKED_CARRIAGE_CHUNK_RADIUS = 8;
    private static final Set<ConstraintJointAxis> LOCKED_CONSTRAINT_AXES =
            EnumSet.allOf(ConstraintJointAxis.class);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether assembled to sub-level is set
    private boolean assembledToSubLevel;
    // Tracks whether assemble next tick is set
    private boolean assembleNextTick;
    // Attached shaft pos
    private BlockPos attachedShaftPos;
    // Attached shaft direction
    private Direction attachedShaftDirection;
    // Attached carriage facing
    private Direction attachedCarriageFacing;
    // Attached sub-level id
    private UUID attachedSubLevelId;
    // Attached shaft sub-level id
    private UUID attachedShaftSubLevelId;
    // Attached shaft progress
    private double attachedShaftProgress;
    // Furthest verified shaft block in the attached direction
    private int cachedForwardShaftSpan;
    // Furthest verified shaft block opposite the attached direction
    private int cachedBackwardShaftSpan;
    // Current sequenced movement limit
    private double sequencedMovementLimit = -1.0D;
    // Tracks whether restore sequenced movement limit is set
    private boolean restoreSequencedMovementLimit;
    // Current tracked sequence context
    private SequencedGearshiftBlockEntity.SequenceContext trackedSequenceContext;
    // Current shaft constraint handle
    private PhysicsConstraintHandle shaftConstraintHandle;
    // Current shaft constraint parent
    private ServerSubLevel shaftConstraintParent;
    // Current shaft constraint child
    private ServerSubLevel shaftConstraintChild;
    // Rail progress encoded in the current fixed joint's parent-local anchor
    private double shaftConstraintProgress = Double.NaN;
    // Last attachment tick game time
    private long lastAttachmentTickGameTime = Long.MIN_VALUE;
    // Last toggle game time
    private long lastToggleGameTime = Long.MIN_VALUE;
    // Current survival validation delay
    private int survivalValidationDelay;
    // Locked sub-level orientation
    private final Quaterniond lockedSubLevelOrientation = new Quaterniond();
    // Tracks whether locked sub-level orientation is available
    private boolean hasLockedSubLevelOrientation;
    // Locked shaft frame orientation
    private final Quaterniond lockedShaftFrameOrientation = new Quaterniond();
    // Tracks whether locked shaft frame orientation is available
    private boolean hasLockedShaftFrameOrientation;
    // Locked local attachment anchor
    private final Vector3d lockedLocalAttachmentAnchor = new Vector3d();
    // Tracks whether locked local attachment anchor is available
    private boolean hasLockedLocalAttachmentAnchor;
    // Locked rotation point
    private final Vector3d lockedRotationPoint = new Vector3d();
    // Tracks whether locked rotation point is available
    private boolean hasLockedRotationPoint;
    // Current debug particle tick gate
    private int debugParticleTickGate;
    // Current debug anchor entity id
    private UUID debugAnchorEntityId;
    // Pending manual relink shaft pos
    private BlockPos pendingManualRelinkShaftPos;
    // Pending manual relink shaft direction
    private Direction pendingManualRelinkShaftDirection;
    // Pending manual relink carriage facing
    private Direction pendingManualRelinkCarriageFacing;
    // Pending manual relink start pos
    private final Vector3d pendingManualRelinkStartPos = new Vector3d();
    // Pending manual relink target pos
    private final Vector3d pendingManualRelinkTargetPos = new Vector3d();
    // Pending manual relink orientation
    private final Quaterniond pendingManualRelinkOrientation = new Quaterniond();
    // Pending manual relink tick count
    private int pendingManualRelinkTicks;
    private static final int MANUAL_RELINK_LERP_TICKS = 14;
    private static final boolean ENABLE_MANUAL_RELINK = true;
    // Last exception
    protected AssemblyException lastException;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry carriage
    public PhysicsGantryCarriageBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.PHYSICS_GANTRY_CARRIAGE.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        registerAwardables(behaviours, AllAdvancements.CONTRAPTION_ACTORS);
    }

    // Validate the gantry shaft
    public void checkValidGantryShaft() {
        if (assembledToSubLevel && !hasValidAttachedShaft(resolveLookupLevel(level))) {
            detachFromShaftKeepSubLevel("check-valid-shaft");
        }
    }

    // Queue the assembly
    public void queueAssembly() {
        assembleNextTick = true;
    }

    // Initialize the physics gantry carriage
    @Override
    public void initialize() {
        super.initialize();
        if (!getBlockState().canSurvive(level, worldPosition)) {
            survivalValidationDelay = 1;
        }
    }

    // Force the toggle assembly
    public void forceToggleAssembly() {
        if (level == null || level.isClientSide) {
            return;
        }

        long now = level.getGameTime();

        if (lastToggleGameTime == now) {
            return;
        }
        lastToggleGameTime = now;

        Object currentSubLevel = resolveAttachedSubLevel();
        CT_LOGGER.info("[CT][PhysicsGantry] toggle requested pos={} assembled={} hasSubLevel={}",
                worldPosition,
                assembledToSubLevel,
                currentSubLevel != null);

        if (isAttachedPayloadSubLevel(currentSubLevel)) {
            PhysicsGantryCarriageBlockEntity active = resolveActiveCarriageInstance(null, null);
            if (active != this) {
                active.lastToggleGameTime = this.lastToggleGameTime;
                active.doSubLevelDisassemble();
            } else {
                doSubLevelDisassemble();
            }
        } else {

            clearAttachmentTrackingState();
            doSubLevelAssemble();
        }
    }

    // Check if this is sublevel assembled
    public boolean isSubLevelAssembled() {
        return assembledToSubLevel || attachedSubLevelId != null;
    }

    // Begin the manual shaft relink
    public boolean beginManualShaftRelink(BlockPos shaftPos, Direction clickedFace, Player requestedBy) {
        return beginManualShaftRelink(shaftPos, clickedFace, requestedBy, null);
    }

    // Begin the manual shaft relink
    public boolean beginManualShaftRelink(BlockPos shaftPos, Direction clickedFace, Player requestedBy,
                                          PhysicsGantryShaftBlockEntity clickedShaft) {
        if (!ENABLE_MANUAL_RELINK) {

            clearPendingManualRelink();
            return false;
        }

        if (level == null || level.isClientSide || shaftPos == null || clickedFace == null) {
            return false;
        }

        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return false;
        }

        Object subLevel = resolveAttachedSubLevel();
        if (subLevel == null) {

            subLevel = getSableContaining();
        }
        if (subLevel == null) {
            return false;
        }

        UUID payloadSubLevelId = extractSubLevelId(subLevel);
        UUID shaftSubLevelId = clickedShaft == null ? null : SimulatedHelper.getContainingSubLevelId(clickedShaft);
        if (payloadSubLevelId != null && payloadSubLevelId.equals(shaftSubLevelId)) {
            return false;
        }

        // -----------------------------------------------------SHAFT TARGET-----------------------------------------------------
        BlockState shaftState = clickedShaft == null ? serverLevel.getBlockState(shaftPos) : clickedShaft.getBlockState();
        if (shaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return false;
        }

        Direction shaftDirection = shaftState.getValue(PhysicsGantryShaftBlock.FACING);
        if (clickedFace.getAxis() == shaftDirection.getAxis()) {

            return false;
        }
        Direction carriageFacing = clickedFace;
        if (isShaftFaceOccupied(shaftPos, shaftDirection, carriageFacing, shaftSubLevelId)) {
            return false;
        }
        BlockState currentCarriageState = getBlockState();
        BlockState resolvedCarriageState = computeRelinkBlockState(clickedFace, shaftState);
        // ------------------------------------TARGET TRANSFORM------------------------------------
        Quaterniond currentOrientation = readSubLevelOrientation(subLevel);
        if (currentOrientation == null) {
            currentOrientation = new Quaterniond();
        }
        Quaterniond targetOrientation = computeManualRelinkOrientation(
                currentCarriageState,
                resolvedCarriageState,
                currentOrientation,
                shaftState,
                clickedFace,
                clickedShaft);

        Vector3d currentPos = readSubLevelPosition(subLevel);
        if (currentPos == null) {
            return false;
        }

        Vec3 localShaftSideAnchor = Vec3.atCenterOf(shaftPos)
                .add(carriageFacing.getStepX() * 0.5D,
                        carriageFacing.getStepY() * 0.5D,
                        carriageFacing.getStepZ() * 0.5D);
        Vec3 worldAnchor = clickedShaft == null
                ? localShaftSideAnchor
                : SimulatedHelper.toContainingWorldPosition(clickedShaft, localShaftSideAnchor);
        if (worldAnchor == null) {
            return false;
        }

        Vector3d targetPos = computeManualRelinkTargetPos(subLevel, worldAnchor, targetOrientation, currentCarriageState);
        if (!isFiniteAndSafe(targetPos.x) || !isFiniteAndSafe(targetPos.y) || !isFiniteAndSafe(targetPos.z)) {
            return false;
        }
        if (wouldSubLevelHitProtectedWorldBlock(subLevel, targetPos, targetOrientation)) {
            return false;
        }

        // ------------------------------------ATTACHMENT RESET------------------------------------
        clearShaftConstraint();
        clearDebugAnchorEntity();

        assembledToSubLevel = false;
        attachedShaftPos = null;
        attachedShaftDirection = null;
        attachedCarriageFacing = null;
        attachedShaftProgress = 0.0D;
        invalidateShaftSpanCache();

        // ------------------------------------PENDING RELINK------------------------------------
        pendingManualRelinkShaftPos = shaftPos.immutable();
        pendingManualRelinkShaftDirection = shaftDirection;
        pendingManualRelinkCarriageFacing = carriageFacing;
        attachedSubLevelId = payloadSubLevelId;
        attachedShaftSubLevelId = shaftSubLevelId;
        SableAssemblyTopologyInvalidation.invalidate(level);
        pendingManualRelinkStartPos.set(currentPos);
        pendingManualRelinkTargetPos.set(targetPos);
        pendingManualRelinkOrientation.set(targetOrientation);
        pendingManualRelinkTicks = 0;

        setChanged();
        sendData();

        CT_LOGGER.info("[CT][PhysicsGantry] manual relink requested pos={} shaft={} by={} facing={}",
                worldPosition,
                shaftPos,
                requestedBy == null ? "unknown" : requestedBy.getScoreboardName(),
                carriageFacing);

        return true;
    }

    // Check if this has tracked attachment state
    private boolean hasTrackedAttachmentState() {
        return assembledToSubLevel
                || attachedSubLevelId != null
                || (attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null)
                || hasPendingManualRelink();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the physics gantry carriage
    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            return;
        }

        if (survivalValidationDelay > 0 && --survivalValidationDelay == 0
                && !hasTrackedAttachmentState()
                && !getBlockState().canSurvive(level, worldPosition)) {
            level.destroyBlock(worldPosition, true);
            return;
        }

        if (assembleNextTick) {
            assembleNextTick = false;
            if (!hasTrackedAttachmentState() && shouldAssemble()) {
                clearAttachmentTrackingState();
                doSubLevelAssemble();
            }
        }

        if (tickPendingManualRelink()) {
            return;
        }

        Object attachedSubLevel = resolveAttachedSubLevel();
        if (!assembledToSubLevel && attachedSubLevel != null
                && attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null) {
            assembledToSubLevel = true;
        }

        if (attachedSubLevel != null
            && (assembledToSubLevel
            || (attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null))) {
            runAttachmentTick(attachedSubLevel);
        }
    }

    // Update the physics gantry carriage
    @Override
    public void sable$tick(ServerSubLevel subLevel) {
        if (level == null || level.isClientSide || subLevel == null) {
            return;
        }
        if (tickPendingManualRelink()) {
            return;
        }
        if (!hasTrackedAttachmentState()) {
            return;
        }
        runAttachmentTick(subLevel);
    }

    // Update the physics
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (level == null || level.isClientSide || subLevel == null) {
            return;
        }
        if (hasPendingManualRelink() || handle == null || !handle.isValid()
                || attachedShaftPos == null || attachedShaftDirection == null || attachedCarriageFacing == null) {
            return;
        }
        // The game-thread attachment update owns the constraint. Never correct the child
        // transform from the physics thread: doing so races the solver whenever either
        // sublevel changes and looks like a lateral teleport/jitter to the player.
        return;
    }

    // Update the shaft constraint for the game tick
    private void updateShaftConstraintForGameTick(Object subLevel) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || !(subLevel instanceof ServerSubLevel)) {
            return;
        }

        Object constrainedSubLevel = resolveConstrainedSubLevel(serverLevel, subLevel);
        if (!(constrainedSubLevel instanceof ServerSubLevel constrainedChild)) {
            return;
        }

        // -----------------------------------------------------SHAFT TARGET-----------------------------------------------------
        PhysicsGantryShaftBlockEntity shaft = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        if (shaft == null) {
            return;
        }
        Object containingShaft = SimulatedHelper.getContainingSubLevel(shaft);
        ServerSubLevel parent = containingShaft instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
        if (parent == constrainedChild) {
            clearShaftConstraint();
            return;
        }

        // The joint frame is expressed entirely in the shaft parent and carriage child
        // local frames. It must not be derived from their changing world transforms.
        Quaterniond relativeOrientation = resolveAttachmentRelativeOrientation();
        if (relativeOrientation == null) {
            return;
        }

        // A carriage is rigidly mounted at its current rail position. The parent
        // anchor changes only when Create advances the carriage along the shaft;
        // it is intentionally never recalculated from either body's world pose.
        Vector3d parentAnchor = new Vector3d(
                attachedShaftPos.getX() + 0.5D,
                attachedShaftPos.getY() + 0.5D,
                attachedShaftPos.getZ() + 0.5D)
                .add(attachedShaftDirection.getStepX() * attachedShaftProgress,
                        attachedShaftDirection.getStepY() * attachedShaftProgress,
                        attachedShaftDirection.getStepZ() * attachedShaftProgress)
                .add(attachedCarriageFacing.getStepX() * 0.5D,
                        attachedCarriageFacing.getStepY() * 0.5D,
                        attachedCarriageFacing.getStepZ() * 0.5D);
        Vector3d childAnchor = new Vector3d(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D)
                .sub(attachedCarriageFacing.getStepX() * 0.5D,
                        attachedCarriageFacing.getStepY() * 0.5D,
                        attachedCarriageFacing.getStepZ() * 0.5D);

        // ------------------------------------CONSTRAINT UPDATE------------------------------------
        try {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) {
                return;
            }
            Object pipeline = container.physicsSystem().getPipeline();

            boolean needsConstraint = shaftConstraintHandle == null
                    || !shaftConstraintHandle.isValid()
                    || shaftConstraintParent != parent
                    || shaftConstraintChild != constrainedChild;
            boolean railPositionChanged = !Double.isFinite(shaftConstraintProgress)
                    || Math.abs(shaftConstraintProgress - attachedShaftProgress) > 1.0E-9D;
            if (needsConstraint) {
                clearShaftConstraint();
                Object genericConstraint = SableConstraintApi.genericConfiguration(
                        parentAnchor, childAnchor, relativeOrientation, new Quaterniond(),
                        LOCKED_CONSTRAINT_AXES);
                shaftConstraintHandle = (PhysicsConstraintHandle) SableConstraintApi.addConstraint(
                        pipeline, parent, constrainedChild, genericConstraint);
                shaftConstraintParent = parent;
                shaftConstraintChild = constrainedChild;
                shaftConstraintProgress = attachedShaftProgress;
                if (shaftConstraintHandle == null || !shaftConstraintHandle.isValid()) {
                    clearShaftConstraint();
                    return;
                }
                shaftConstraintHandle.setContactsEnabled(false);
                SableConstraintApi.wakeUp(pipeline, parent);
                SableConstraintApi.wakeUp(pipeline, constrainedChild);
            } else if (railPositionChanged) {
                // Preserve this joint's solver state. Recreating a fixed joint for every
                // kinetic increment discards its warm start and causes lateral correction
                // when either nested sublevel changes. The movement target is only this
                // parent-local frame; all six relative degrees of freedom remain locked.
                SableConstraintApi.setFrame(shaftConstraintHandle, 1, parentAnchor, relativeOrientation);
                SableConstraintApi.setFrame(shaftConstraintHandle, 2, childAnchor, new Quaterniond());
                shaftConstraintProgress = attachedShaftProgress;
                shaftConstraintHandle.setContactsEnabled(false);
                SableConstraintApi.wakeUp(pipeline, parent);
                SableConstraintApi.wakeUp(pipeline, constrainedChild);
            }

        } catch (Exception | LinkageError e) {
            CT_LOGGER.warn("[CT][PhysicsGantry] constraint update failed at {}: {}", worldPosition, e.toString());
            clearShaftConstraint();
        }
    }

    // Refresh the rope attachments for moved sublevel
    private void refreshRopeAttachmentsForMovedSubLevel(ServerLevel serverLevel, Object movedSubLevel) {
        if (!(movedSubLevel instanceof ServerSubLevel subLevel)) {
            return;
        }

        ServerLevelRopeManager ropeManager = ServerLevelRopeManager.getOrCreate(serverLevel);
        if (ropeManager == null) {
            return;
        }

        UUID subLevelId = subLevel.getUniqueId();
        for (ServerRopeStrand strand : ropeManager.getAllStrands()) {
            if (!strand.isActive()) {
                continue;
            }
            for (RopeAttachment attachment : strand.getAttachments()) {
                if (!subLevelId.equals(attachment.subLevelID())) {
                    continue;
                }

                BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                        serverLevel, subLevelId, attachment.blockAttachment());
                if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
                    continue;
                }

                RopeStrandHolderBehavior holder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
                if (holder == null) {
                    continue;
                }

                RopeHandle.AttachmentPoint point = attachment.point() == RopeAttachmentPoint.END
                        ? RopeHandle.AttachmentPoint.END
                        : RopeHandle.AttachmentPoint.START;
                strand.setAttachment(point,
                        JOMLConversion.toJOML((Position) holder.getAttachmentPoint()),
                        subLevel);
            }
        }
    }

    // Resolve the constrained sublevel
    private Object resolveConstrainedSubLevel(ServerLevel serverLevel, Object fallbackSubLevel) {
        Object constrainedSubLevel = fallbackSubLevel;
        if (attachedSubLevelId != null) {
            Object preferredSubLevel = SubLevelBlockEntityCollector.getSubLevel(serverLevel, attachedSubLevelId);
            if (preferredSubLevel != null) {

                constrainedSubLevel = preferredSubLevel;
            }
        }
        return constrainedSubLevel;
    }

    // Get the last assembly exception
    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    // Assemble the gantry carriage sublevel
    private void doSubLevelAssemble() {
        // ------------------------------------ASSEMBLY CHECKS------------------------------------
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return;
        }

        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PhysicsGantryCarriageBlock)) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble aborted: not carriage block pos={}", worldPosition);
            return;
        }

        Direction carriageFacing = blockState.getValue(PhysicsGantryCarriageBlock.FACING);
        BlockPos shaftPos = worldPosition.relative(carriageFacing.getOpposite());
        BlockEntity blockEntity = level.getBlockEntity(shaftPos);
        if (!(blockEntity instanceof PhysicsGantryShaftBlockEntity shaftBE)) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble aborted: no shaft BE at {} from {}", shaftPos, worldPosition);
            return;
        }

        // -----------------------------------------------------SHAFT TARGET-----------------------------------------------------
        BlockState shaftState = shaftBE.getBlockState();
        if (shaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble aborted: wrong shaft block at {}", shaftPos);
            return;
        }

        UUID shaftSubLevelId = SimulatedHelper.getContainingSubLevelId(shaftBE);
        if (isShaftFaceOccupied(shaftPos, shaftState.getValue(PhysicsGantryShaftBlock.FACING),
                carriageFacing, shaftSubLevelId)) {
            CT_LOGGER.debug("[CT][PhysicsGantry] assemble blocked by occupied shaft face pos={} shaft={} face={}",
                    worldPosition,
                    shaftPos,
                    carriageFacing);
            return;
        }

        if (!shouldAssemble()) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble aborted: shouldAssemble=false pos={}", worldPosition);
            return;
        }

        // ------------------------------------SUB-LEVEL ASSEMBLY------------------------------------
        CombinedAssemblyResult res;
        try {

            res = assembleCombinedMountedStructure();
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble failed at {}: {}", worldPosition, e.getMessage());
            return;
        } catch (Exception e) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble invoke failed at {}: {}", worldPosition, e.toString());
            return;
        }

        if (res == null || !(res.subLevel() instanceof ServerSubLevel assembledSubLevel)) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble returned null/no server sublevel at {}", worldPosition);
            return;
        }

        // ------------------------------------ACTIVE CARRIAGE------------------------------------
        PhysicsGantryCarriageBlockEntity active = SimulatedHelper.findBlockEntityInSubLevel(
                assembledSubLevel,
                res.movedCarriagePos(),
                PhysicsGantryCarriageBlockEntity.class);
        if (active == null || active.isRemoved()
                || active.getBlockState().getBlock() != CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assembled payload is missing its carriage at {}; restoring blocks",
                    res.movedCarriagePos());
            try {
                SimAssemblyHelper.disassembleSubLevel(
                        serverLevel,
                        assembledSubLevel,
                        res.movedCarriagePos(),
                        worldPosition,
                        Rotation.NONE,
                        false);
            } catch (RuntimeException | LinkageError error) {
                CT_LOGGER.error("[CT][PhysicsGantry] failed to restore carriage assembly at {}: {}",
                        worldPosition,
                        error.toString());
            }
            return;
        }
        // ------------------------------------ATTACHMENT COMMIT------------------------------------
        Direction shaftDirection = shaftState.getValue(PhysicsGantryShaftBlock.FACING);
        UUID attachedId = extractSubLevelId(assembledSubLevel);
        if (attachedId == null) {
            CT_LOGGER.warn("[CT][PhysicsGantry] assemble result missing sublevel id at {}", worldPosition);
            return;
        }

        setAssembledAttachmentState(shaftPos, shaftDirection, carriageFacing, attachedId, shaftSubLevelId);
        setChanged();
        if (active != this) {
            active.setAssembledAttachmentState(shaftPos, shaftDirection, carriageFacing, attachedId, shaftSubLevelId);
            active.setChanged();
        }
        active.alignFreshAssemblyToShaft(assembledSubLevel);

        Vec3 initialAnchor = Vec3.atCenterOf(shaftPos)
                .add(carriageFacing.getStepX() * 0.5D,
                        carriageFacing.getStepY() * 0.5D,
                        carriageFacing.getStepZ() * 0.5D);
        active.emitDebugAttachmentParticles(initialAnchor, false);
        active.updateDebugAnchorEntity(initialAnchor, false);
        CT_LOGGER.info("[CT][PhysicsGantry] assembled pos={} active={} shaft={} dir={} face={}",
            worldPosition,
            active.worldPosition,
            shaftPos,
            shaftDirection,
            carriageFacing);

        sendData();
        if (active != this) {
            active.sendData();
        }
        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, (Vec3i) worldPosition);
    }

    // Assemble the combined mounted structure
    private CombinedAssemblyResult assembleCombinedMountedStructure() throws AssemblyException {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return null;
        }

        Set<BlockPos> assembledBlocks = new LinkedHashSet<>();
        Set<SuperGlueEntity> superGlues = new LinkedHashSet<>();
        Set<HoneyGlueEntity> honeyGlues = new LinkedHashSet<>();

        boolean carriageInsideGlueBox = isCarriageInsideGlueBox();
        if (!carriageInsideGlueBox) {
            Direction carriageFacing = getBlockState().getValue(PhysicsGantryCarriageBlock.FACING);
            BlockPos shaftPos = worldPosition.relative(carriageFacing.getOpposite());
            SimAssemblyContraption carriageContraption = new SimAssemblyContraption(shaftPos, false);
            carriageContraption.searchMovedStructure(level, worldPosition);
            assembledBlocks.addAll(carriageContraption.getBlocks());
            superGlues.addAll(carriageContraption.getGlues());
            honeyGlues.addAll(carriageContraption.getHoneyGlues());
        } else {

            assembledBlocks.add(worldPosition);
            CT_LOGGER.info("[CT][PhysicsGantry] ignoring enclosing glue box at carriage {}", worldPosition);
        }
        assembledBlocks.add(worldPosition);

        BlockState carriageState = getBlockState();
        Direction mountedDirection = getMountedPayloadDirection(carriageState);
        BlockPos mountedPos = worldPosition.relative(mountedDirection);

        if (!level.getBlockState(mountedPos).isAir() && !assembledBlocks.contains(mountedPos)) {
            SimAssemblyContraption mountedContraption = new SimAssemblyContraption(worldPosition, true);
            mountedContraption.searchMovedStructure(level, mountedPos);
            assembledBlocks.addAll(mountedContraption.getBlocks());
            superGlues.addAll(mountedContraption.getGlues());
            honeyGlues.addAll(mountedContraption.getHoneyGlues());
        }

        if (assembledBlocks.isEmpty()) {
            return null;
        }

        ArrayList<AABB> collectedContraptionGlues = new ArrayList<>();
        SubLevelAssemblyApi.prepareCreateContraptions(level, assembledBlocks, collectedContraptionGlues, true);

        BlockPos anchor = assembledBlocks.contains(worldPosition) ? worldPosition : assembledBlocks.iterator().next();
        ServerSubLevel subLevel = SubLevelAssemblyApi.assemble(serverLevel, anchor, assembledBlocks);
        if (subLevel == null) {
            return null;
        }

        BlockPos offsetBlocks = getPlotCenterBlock(subLevel).subtract((Vec3i) anchor);
        Vec3 offset = Vec3.atLowerCornerOf((Vec3i) offsetBlocks);
        for (AABB aabb : collectedContraptionGlues) {
            level.addFreshEntity(new SuperGlueEntity(level, aabb.move(offset)));
        }
        for (SuperGlueEntity superGlueEntity : superGlues) {
            superGlueEntity.remove(Entity.RemovalReason.KILLED);
            level.addFreshEntity(new SuperGlueEntity(level, superGlueEntity.getBoundingBox().move(offset)));
        }
        for (HoneyGlueEntity honeyGlueEntity : honeyGlues) {
            honeyGlueEntity.remove(Entity.RemovalReason.KILLED);
            AABB newBounds = honeyGlueEntity.getBoundingBox().move(offset);
            HoneyGlueEntity entity = new HoneyGlueEntity(level, newBounds);
            level.addFreshEntity(entity);
            entity.setBoundsAndSync(newBounds);
        }

        BlockPos movedCarriagePos = worldPosition.offset(offsetBlocks);
        return new CombinedAssemblyResult(new SimAssemblyHelper.AssemblyResult(subLevel, offsetBlocks), movedCarriagePos);
    }

    // Check if the carriage is inside the glue box
    private boolean isCarriageInsideGlueBox() {
        if (level == null) {
            return false;
        }

        AABB searchBounds = new AABB(worldPosition).inflate(16.0D);
        for (SuperGlueEntity glueEntity : level.getEntitiesOfClass(SuperGlueEntity.class, searchBounds)) {
            if (glueEntity.contains(worldPosition)) {
                return true;
            }
        }

        for (HoneyGlueEntity glueEntity : level.getEntitiesOfClass(HoneyGlueEntity.class, searchBounds)) {
            if (glueEntity.contains(worldPosition)) {
                return true;
            }
        }

        return false;
    }

    // Get the mounted payload direction
    public static Direction getMountedPayloadDirection(BlockState carriageState) {
        if (!carriageState.hasProperty(PhysicsGantryCarriageBlock.FACING)
                || !carriageState.hasProperty(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            return Direction.UP;
        }

        Direction facing = carriageState.getValue(PhysicsGantryCarriageBlock.FACING);
        boolean alongFirst = carriageState.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);

        Quaterniond orientation = new Quaterniond();
        orientation.rotateY(Math.toRadians(AngleHelper.horizontalAngle(facing)));
        orientation.rotateX(Math.toRadians(
                facing == Direction.UP ? 0.0D : (facing == Direction.DOWN ? 180.0D : 90.0D)));
        orientation.rotateY(Math.toRadians(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0D : 90.0D));

        Vector3d mountedNormal = orientation.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        return Direction.getNearest(mountedNormal.x, mountedNormal.y, mountedNormal.z);
    }

    // Store combined assembly results
    private record CombinedAssemblyResult(SimAssemblyHelper.AssemblyResult assemblyResult, BlockPos movedCarriagePos) {
        // Get the sublevel
        private Object subLevel() {
            return assemblyResult.subLevel();
        }

        // Get the offset
        private BlockPos offset() {
            return assemblyResult.offset();
        }
    }

    // Get the plot center block
    private BlockPos getPlotCenterBlock(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? sableSubLevel.getPlot().getCenterBlock()
                : worldPosition;
    }

    // Set the assembled attachment state
    private void setAssembledAttachmentState(BlockPos shaftPos, Direction shaftDirection,
                                             Direction carriageFacing, UUID subLevelId, UUID shaftSubLevelId) {
        clearShaftConstraint();
        assembledToSubLevel = true;
        attachedShaftPos = shaftPos == null ? null : shaftPos.immutable();
        attachedShaftDirection = shaftDirection;
        attachedCarriageFacing = carriageFacing;
        attachedSubLevelId = subLevelId;
        attachedShaftSubLevelId = shaftSubLevelId;
        attachedShaftProgress = 0.0d;
        invalidateShaftSpanCache();
        clearSequencedMovementLimit();
        hasLockedSubLevelOrientation = false;
        lockedSubLevelOrientation.identity();
        hasLockedShaftFrameOrientation = false;
        lockedShaftFrameOrientation.identity();
        hasLockedLocalAttachmentAnchor = false;
        lockedLocalAttachmentAnchor.set(0.0, 0.0, 0.0);
        hasLockedRotationPoint = false;
        lockedRotationPoint.set(0.0, 0.0, 0.0);
        lastException = null;
        SableAssemblyTopologyInvalidation.invalidate(level);
    }

    // Align a newly assembled payload with the shaft's containing frame before locking it
    private void alignFreshAssemblyToShaft(Object subLevel) {
        if (!(subLevel instanceof ServerSubLevel child)
                || attachedShaftPos == null
                || attachedShaftDirection == null
                || attachedCarriageFacing == null) {
            return;
        }

        PhysicsGantryShaftBlockEntity shaft = findAttachedShaftEntity(
                attachedShaftPos, attachedShaftSubLevelId);
        if (shaft == null) {
            return;
        }

        Quaterniond targetOrientation = resolveAttachmentWorldOrientation(shaft, child);

        Vec3 worldAnchor = computeAttachmentWorldAnchor(shaft, attachedShaftProgress);
        if (worldAnchor == null) {
            return;
        }

        hasLockedSubLevelOrientation = true;
        lockedSubLevelOrientation.set(targetOrientation);
        hasLockedShaftFrameOrientation = false;
        Quaterniond shaftFrame = resolveShaftFrameOrientation();
        if (shaftFrame != null) {
            lockedShaftFrameOrientation.set(shaftFrame);
            hasLockedShaftFrameOrientation = true;
        }
        hasLockedLocalAttachmentAnchor = false;
        hasLockedRotationPoint = false;
        Vector3d targetPosition = computeLockedAttachmentTargetPos(child, worldAnchor, targetOrientation);
        if (!isFiniteAndSafe(targetPosition.x)
                || !isFiniteAndSafe(targetPosition.y)
                || !isFiniteAndSafe(targetPosition.z)) {
            return;
        }

        teleportSubLevel(child, targetPosition, targetOrientation);
        resetSubLevelVelocity(child);
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel != null) {
            refreshRopeAttachmentsForMovedSubLevel(serverLevel, child);
        }
    }

    // Update the attached shaft position
    public void updateAttachedShaftPosition(BlockPos newShaftPos) {
        if (!assembledToSubLevel || newShaftPos == null) {
            return;
        }

        attachedShaftPos = newShaftPos.immutable();
        invalidateShaftSpanCache();
        setChanged();
    }

    // Retarget the attached shaft after assembly
    public boolean retargetAttachedShaftAfterAssembly(BlockPos oldShaftPos, BlockPos newShaftPos,
                                                      Direction shaftDirection, UUID shaftSubLevelId) {
        return retargetAttachedShaftAfterMove(oldShaftPos, null, newShaftPos, shaftDirection, shaftSubLevelId);
    }

    // Retarget the attached shaft after moving
    public boolean retargetAttachedShaftAfterMove(BlockPos oldShaftPos, UUID oldShaftSubLevelId,
                                                  BlockPos newShaftPos, Direction shaftDirection,
                                                  UUID newShaftSubLevelId) {
        if (!assembledToSubLevel || oldShaftPos == null || newShaftPos == null || attachedShaftPos == null) {
            return false;
        }
        if (!attachedShaftPos.equals(oldShaftPos)) {
            return false;
        }
        if (oldShaftSubLevelId != null && !oldShaftSubLevelId.equals(attachedShaftSubLevelId)) {
            return false;
        }
        if (shaftDirection != null && attachedShaftDirection != null
                && shaftDirection.getAxis() != attachedShaftDirection.getAxis()) {
            return false;
        }

        boolean topologyChanged = !Objects.equals(
                attachedShaftSubLevelId, newShaftSubLevelId);
        clearShaftConstraint();
        attachedShaftPos = newShaftPos.immutable();
        if (shaftDirection != null) {
            attachedShaftDirection = shaftDirection;
        }
        attachedShaftSubLevelId = newShaftSubLevelId;
        invalidateShaftSpanCache();
        hasLockedSubLevelOrientation = false;
        lockedSubLevelOrientation.identity();
        hasLockedShaftFrameOrientation = false;
        lockedShaftFrameOrientation.identity();
        hasLockedLocalAttachmentAnchor = false;
        lockedLocalAttachmentAnchor.set(0.0, 0.0, 0.0);
        hasLockedRotationPoint = false;
        lockedRotationPoint.set(0.0, 0.0, 0.0);
        lastAttachmentTickGameTime = Long.MIN_VALUE;
        if (topologyChanged) {
            SableAssemblyTopologyInvalidation.invalidate(level);
        }
        setChanged();
        sendData();
        return true;
    }

    // Check if this is attached to the shaft block
    public boolean isAttachedToShaftBlock(BlockPos shaftPos, Direction shaftDirection) {
        return isAttachedToShaftBlock(shaftPos, shaftDirection, null);
    }

    // Check if this is attached to the shaft block and sublevel
    public boolean isAttachedToShaftBlock(BlockPos shaftPos, Direction shaftDirection, UUID shaftSubLevelId) {
        if (!assembledToSubLevel || shaftPos == null || attachedShaftPos == null || attachedShaftDirection == null) {
            return false;
        }
        if (shaftSubLevelId != null && !shaftSubLevelId.equals(attachedShaftSubLevelId)) {
            return false;
        }
        if (shaftDirection != null && shaftDirection.getAxis() != attachedShaftDirection.getAxis()) {
            return false;
        }
        if (attachedShaftPos.equals(shaftPos)) {
            return true;
        }

        Vec3 shaftDirectionLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
        Vec3 currentAnchor = Vec3.atCenterOf(attachedShaftPos)
                .add(shaftDirectionLocal.x * attachedShaftProgress,
                        shaftDirectionLocal.y * attachedShaftProgress,
                        shaftDirectionLocal.z * attachedShaftProgress);
        return BlockPos.containing(currentAnchor).equals(shaftPos);
    }

    // Check if this is assembled to a sublevel
    public boolean isAssembledToSubLevel() {
        return assembledToSubLevel;
    }
    // Resolve the active carriage instance
    private PhysicsGantryCarriageBlockEntity resolveActiveCarriageInstance(BlockPos movedCarriagePos, BlockPos assemblyOffset) {
        Level lookup = resolveLookupLevel(level);
        if (lookup != null) {
            if (movedCarriagePos != null) {
                BlockEntity moved = SimulatedHelper.findBlockEntityIncludingSubLevels(lookup, movedCarriagePos);
                if (moved instanceof PhysicsGantryCarriageBlockEntity carriage) {
                    return carriage;
                }
            }

            if (assemblyOffset != null) {
                BlockPos expected = worldPosition.offset(assemblyOffset);
                BlockEntity moved = SimulatedHelper.findBlockEntityIncludingSubLevels(lookup, expected);
                if (moved instanceof PhysicsGantryCarriageBlockEntity carriage) {
                    return carriage;
                }
            }

            BlockEntity local = lookup.getBlockEntity(worldPosition);
            if (local instanceof PhysicsGantryCarriageBlockEntity carriage) {
                return carriage;
            }

            BlockEntity includingSubLevels = SimulatedHelper.findBlockEntityIncludingSubLevels(lookup, worldPosition);
            if (includingSubLevels instanceof PhysicsGantryCarriageBlockEntity carriage) {
                return carriage;
            }
        }

        return this;
    }

    // Disassemble the gantry carriage sublevel
    private void doSubLevelDisassemble() {
        if (level == null) {
            return;
        }

        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            CT_LOGGER.warn("[CT][PhysicsGantry] disassemble aborted: no server level for {}", worldPosition);
            return;
        }

        BlockPos previousShaftPos = attachedShaftPos;

        clearShaftConstraint();
        clearDebugAnchorEntity();
        clearPendingManualRelink();

        Object subLevel = resolveAttachedSubLevel();
        if (subLevel == null) {
            clearAttachmentTrackingStateAndRefreshShaft();
            sendData();
            return;
        }

        if (!isAttachedPayloadSubLevel(subLevel)) {

            CT_LOGGER.warn("[CT][PhysicsGantry] disassemble skipped unsafe parent host pos={} shaft={} subLevelId={}",
                    worldPosition,
                    attachedShaftPos,
                    extractSubLevelId(subLevel));
                clearAttachmentTrackingStateAndRefreshShaft();
            sendData();
            return;
        }

        BlockPos disassemblyAnchor = worldPosition;
        BlockPos disassemblyGoal = resolveDisassemblyGoalBlockPos(subLevel);
        Rotation disassemblyRotation = resolveDisassemblyRotation(subLevel);
        if (wouldDisassemblyOverwriteProtectedWorldBlock(subLevel, disassemblyAnchor, disassemblyGoal, disassemblyRotation)) {
            CT_LOGGER.warn("[CT][PhysicsGantry] disassemble blocked by protected block at {} goal={}",
                    worldPosition,
                    disassemblyGoal);
            return;
        }
        syncCarriageBlockStateToCurrentAttachment();
        try {
            SubLevelAssemblyApi.disassemble(serverLevel, (SubLevel) subLevel,
                    disassemblyAnchor, disassemblyGoal, disassemblyRotation, true);
        } catch (RuntimeException e) {
            CT_LOGGER.warn("[CT][PhysicsGantry] disassemble failed at {}: {}", worldPosition, e.toString());
            return;
        }

        clearAttachmentTrackingStateAndRefreshShaft();
        clearDisassembledCarriageAttachmentState(disassemblyGoal, previousShaftPos);
        sendData();
    }

    // Clear the disassembled carriage attachment state
    private void clearDisassembledCarriageAttachmentState(BlockPos disassemblyGoal, BlockPos previousShaftPos) {
        if (level == null || disassemblyGoal == null) {
            return;
        }

        Level lookup = resolveLookupLevel(level);
        if (lookup == null) {
            return;
        }

        PhysicsGantryCarriageBlockEntity disassembledCarriage = SimulatedHelper.findBlockEntityIncludingSubLevels(
                lookup,
                disassemblyGoal,
                PhysicsGantryCarriageBlockEntity.class);
        if (disassembledCarriage == null) {
            return;
        }

        disassembledCarriage.clearAttachmentTrackingState();
        disassembledCarriage.scrubKineticLinksAfterAttachmentTeardown(previousShaftPos);
        disassembledCarriage.refreshShaftAnchorLookup(previousShaftPos);
        disassembledCarriage.setChanged();
        disassembledCarriage.sendData();
    }

    // Sync the carriage block state to current attachment
    private void syncCarriageBlockStateToCurrentAttachment() {
        if (level == null || attachedCarriageFacing == null || attachedShaftPos == null) {
            return;
        }

        BlockState currentState = getBlockState();
        if (!(currentState.getBlock() instanceof PhysicsGantryCarriageBlock carriageBlock)) {
            return;
        }

        BlockState shaftState = findAttachedShaftState(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftState == null || shaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return;
        }

        BlockState updatedState = currentState.setValue(PhysicsGantryCarriageBlock.FACING, attachedCarriageFacing);
        updatedState = carriageBlock.cycleAxisIfNecessary(updatedState, attachedCarriageFacing.getOpposite(), shaftState);
        if (updatedState.equals(currentState)) {
            return;
        }

        level.setBlock(worldPosition, updatedState, 3);
    }

    // Check if this is an attached payload sublevel
    private boolean isAttachedPayloadSubLevel(Object subLevel) {
        if (subLevel == null) {
            return false;
        }

        if (!assembledToSubLevel && attachedSubLevelId == null && attachedShaftPos == null
                && attachedShaftDirection == null && attachedCarriageFacing == null) {
            return false;
        }

        if (subLevelContainsAttachedShaft(subLevel)) {
            return false;
        }

        return assembledToSubLevel || attachedSubLevelId != null
                || (attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null);
    }

    // Check if the sublevel contains the attached shaft
    private boolean subLevelContainsAttachedShaft(Object subLevel) {
        if (subLevel == null || attachedShaftPos == null) {
            return false;
        }

        UUID subLevelId = extractSubLevelId(subLevel);
        if (subLevelId == null) {
            return false;
        }
        if (attachedShaftSubLevelId == null || !attachedShaftSubLevelId.equals(subLevelId)) {
            return false;
        }

        PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityInSubLevel(
            subLevel,
            attachedShaftPos,
            PhysicsGantryShaftBlockEntity.class);
        if (shaft == null) {
            return false;
        }

        if (attachedShaftDirection == null) {
            return true;
        }

        BlockState shaftState = shaft.getBlockState();
        return shaftState.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                && shaftState.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
    }

    // Clear the attachment tracking state
    private void clearAttachmentTrackingState() {
        clearShaftConstraint();
        assembledToSubLevel = false;
        attachedShaftPos = null;
        attachedShaftDirection = null;
        attachedCarriageFacing = null;
        attachedSubLevelId = null;
        attachedShaftSubLevelId = null;
        attachedShaftProgress = 0.0d;
        invalidateShaftSpanCache();
        hasLockedSubLevelOrientation = false;
        lockedSubLevelOrientation.identity();
        hasLockedShaftFrameOrientation = false;
        lockedShaftFrameOrientation.identity();
        hasLockedLocalAttachmentAnchor = false;
        lockedLocalAttachmentAnchor.set(0.0, 0.0, 0.0);
        hasLockedRotationPoint = false;
        lockedRotationPoint.set(0.0, 0.0, 0.0);
        lastAttachmentTickGameTime = Long.MIN_VALUE;
        debugParticleTickGate = 0;
        SableAssemblyTopologyInvalidation.invalidate(level);
    }

    // Clear the attachment tracking state and refresh shaft
    private void clearAttachmentTrackingStateAndRefreshShaft() {
        BlockPos previousShaftPos = attachedShaftPos;
        clearAttachmentTrackingState();
        scrubKineticLinksAfterAttachmentTeardown(previousShaftPos);
        refreshShaftAnchorLookup(previousShaftPos);
    }

    // Scrub the kinetic links after attachment teardown
    private void scrubKineticLinksAfterAttachmentTeardown(BlockPos shaftPos) {
        if (level == null || level.isClientSide) {
            return;
        }

        detachKinetics();
        removeSource();
        attachKinetics();

        if (shaftPos != null) {
            Level lookup = resolveLookupLevel(level);
            if (lookup != null) {
                PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(
                        lookup,
                        shaftPos,
                        PhysicsGantryShaftBlockEntity.class);
                if (shaft != null) {
                    shaft.detachKinetics();
                    shaft.removeSource();
                    shaft.attachKinetics();
                }
            }
        }

        if (getBlockState().getBlock() != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        if (shaftPos != null) {
            BlockState shaftState = level.getBlockState(shaftPos);
            if (!shaftState.isAir()) {
                level.updateNeighborsAt(shaftPos, shaftState.getBlock());
            }
        }
    }

    // Refresh the shaft anchor lookup
    private void refreshShaftAnchorLookup(BlockPos shaftPos) {
        if (shaftPos == null || level == null) {
            return;
        }

        Level lookup = resolveLookupLevel(level);
        if (lookup == null) {
            return;
        }

        PhysicsGantryShaftBlockEntity shaft = SimulatedHelper.findBlockEntityIncludingSubLevels(
                lookup,
                shaftPos,
                PhysicsGantryShaftBlockEntity.class);
        if (shaft == null) {
            return;
        }

        shaft.refreshCarriageAnchorLookup();
    }

    // Check if this has active attachment anchor
    public boolean hasActiveAttachmentAnchor() {
        return assembledToSubLevel
                || shaftConstraintHandle != null
                || hasLockedLocalAttachmentAnchor;
    }

    // Release the payload when its carriage block is removed
    public void onCarriageRemoved() {
        if (level == null || level.isClientSide) {
            return;
        }

        Object payloadSubLevel = resolveAttachedSubLevel();
        BlockPos previousShaftPos = attachedShaftPos;
        clearShaftConstraint();
        clearDebugAnchorEntity();
        clearPendingManualRelink();
        if (payloadSubLevel != null) {
            resetSubLevelVelocity(payloadSubLevel);
        }
        clearAttachmentTrackingState();
        scrubKineticLinksAfterAttachmentTeardown(previousShaftPos);
        refreshShaftAnchorLookup(previousShaftPos);
    }

    // Release the payload when its backing shaft block is removed
    public void onAttachedShaftRemoved(BlockPos shaftPos, Direction shaftDirection, UUID shaftSubLevelId) {
        if (!isAttachedToShaftBlock(shaftPos, shaftDirection, shaftSubLevelId)) {
            return;
        }

        Object payloadSubLevel = resolveAttachedSubLevel();
        if (payloadSubLevel != null) {
            resetSubLevelVelocity(payloadSubLevel);
        }
        detachFromShaftKeepSubLevel("shaft-removed");
    }

    // Get the attached shaft pos
    public BlockPos getAttachedShaftPos() {
        return attachedShaftPos;
    }

    // Get the attached shaft direction
    public Direction getAttachedShaftDirection() {
        return attachedShaftDirection;
    }

    // Get the attached carriage facing
    public Direction getAttachedCarriageFacing() {
        return attachedCarriageFacing;
    }

    // Get the attached sublevel id
    public UUID getAttachedSubLevelId() {
        return attachedSubLevelId;
    }

    // Get the attached shaft progress
    public double getAttachedShaftProgress() {
        return attachedShaftProgress;
    }

    // Run the attachment tick
    private void runAttachmentTick(Object subLevel) {
        Level lookupLevel = resolveLookupLevel(level);
        if (lookupLevel == null) {
            return;
        }

        long gameTime = lookupLevel.getGameTime();
        if (lastAttachmentTickGameTime == gameTime) {
            return;
        }
        lastAttachmentTickGameTime = gameTime;
        tickAttachedSubLevelMovement(subLevel);
    }

    // Update the attached sublevel movement
    private void tickAttachedSubLevelMovement(Object subLevel) {
        ServerLevel lookupLevel = resolveServerLevel(level);
        if (lookupLevel == null) {
            return;
        }
        Object constrainedSubLevel = resolveConstrainedSubLevel(lookupLevel, subLevel);
        if (!hasTrackedAttachmentState()) {
            return;
        }
        if (!hasValidAttachedShaft(lookupLevel)) {
            detachFromShaftKeepSubLevel("tick-invalid-shaft");
            return;
        }

        PhysicsGantryShaftBlockEntity shaftBE = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftBE == null) {
            return;
        }

        double delta = shaftBE.getPinionMovementSpeed();
        if (delta == 0.0D && Math.abs(shaftBE.getSpeed()) > 1.0E-4f) {

            delta = Mth.clamp(convertToLinear(-shaftBE.getSpeed()), -0.49f, 0.49f);
        }
        delta = limitMovementForSequence(shaftBE, delta);

        if (delta != 0.0D) {
            double nextProgress = clampMovementToAvailableShaft(
                    lookupLevel, attachedShaftProgress + delta);

            if (!wouldAttachedSubLevelHitProtectedWorldBlock(constrainedSubLevel, nextProgress)) {
                double appliedMovement = nextProgress - attachedShaftProgress;
                if (Math.abs(appliedMovement) > 1.0E-6D) {
                    attachedShaftProgress = nextProgress;
                    consumeSequencedMovement(appliedMovement);
                    setChanged();
                }
            } else {
                resetSubLevelVelocity(constrainedSubLevel);
            }
        }

        applyAttachmentPose(constrainedSubLevel, true);
    }

    // Get the limit movement for sequence
    private double limitMovementForSequence(PhysicsGantryShaftBlockEntity shaft, double movement) {
        refreshSequencedMovementLimit(shaft);
        return PhysicsGantryShaftBlockEntity.clampSequenceMovement(movement, sequencedMovementLimit);
    }

    // Consume the sequenced movement
    private void consumeSequencedMovement(double appliedMovement) {
        if (sequencedMovementLimit < 0.0D) {
            return;
        }
        sequencedMovementLimit =
                PhysicsGantryShaftBlockEntity.consumeSequenceDistance(sequencedMovementLimit, appliedMovement);
        setChanged();
    }

    // Refresh the sequenced movement limit
    private void refreshSequencedMovementLimit(PhysicsGantryShaftBlockEntity shaft) {
        SequencedGearshiftBlockEntity.SequenceContext ctx = shaft.getSequenceContextForGantry();
        if (ctx == null) {
            if ((trackedSequenceContext != null || sequencedMovementLimit >= 0.0D)
                    && Math.abs(shaft.getSpeed()) <= 1.0E-6D) {
                clearSequencedMovementLimit();
            }
            return;
        }
        if (ctx.instruction() != SequencerInstructions.TURN_DISTANCE) {
            clearSequencedMovementLimit();
            return;
        }
        if (restoreSequencedMovementLimit) {
            trackedSequenceContext = ctx;
            restoreSequencedMovementLimit = false;
            return;
        }
        if (ctx == trackedSequenceContext) {
            return;
        }
        trackedSequenceContext = ctx;
        sequencedMovementLimit = Math.max(0.0D, ctx.getEffectiveValue(shaft.getTheoreticalSpeed()));
        setChanged();
    }

    // Clear the sequenced movement limit
    private void clearSequencedMovementLimit() {
        boolean changed = trackedSequenceContext != null || sequencedMovementLimit >= 0.0D
                || restoreSequencedMovementLimit;
        sequencedMovementLimit = -1.0D;
        restoreSequencedMovementLimit = false;
        trackedSequenceContext = null;
        if (changed) {
            setChanged();
        }
    }

    // Check if the shaft face is occupied
    private boolean isShaftFaceOccupied(BlockPos shaftPos, Direction shaftDirection, Direction carriageFacing,
                                        UUID shaftSubLevelId) {
        return hasPlacedCarriageOnShaftFace(level, shaftPos, shaftDirection, carriageFacing, shaftSubLevelId,
                worldPosition)
                || isShaftFaceOccupied(level, shaftPos, shaftDirection, carriageFacing, shaftSubLevelId, this);
    }

    // Check if the shaft face is occupied
    public static boolean isShaftFaceOccupied(Level level, BlockPos shaftPos, Direction shaftDirection,
                                              Direction carriageFacing, UUID shaftSubLevelId) {
        return isShaftFaceOccupiedForPlacement(level, shaftPos, shaftDirection, carriageFacing, shaftSubLevelId, null);
    }

    // Check if this is shaft face occupied for placement
    public static boolean isShaftFaceOccupiedForPlacement(Level level, BlockPos shaftPos, Direction shaftDirection,
                                                          Direction carriageFacing, UUID shaftSubLevelId,
                                                          BlockPos ignoredPlacedCarriagePos) {
        return hasPlacedCarriageOnShaftFace(level, shaftPos, shaftDirection, carriageFacing, shaftSubLevelId,
                ignoredPlacedCarriagePos)
                || isShaftFaceOccupied(level, shaftPos, shaftDirection, carriageFacing, shaftSubLevelId, null);
    }

    // Check if this would join duplicate shaft faces
    public static boolean wouldJoinDuplicateShaftFaces(Level level, BlockPos shaftPos, Direction shaftDirection,
                                                       UUID shaftSubLevelId) {
        if (level == null || shaftPos == null || shaftDirection == null) {
            return false;
        }

        BlockPos forwardShaftPos = shaftPos.relative(shaftDirection);
        BlockPos backwardShaftPos = shaftPos.relative(shaftDirection.getOpposite());
        if (!isMatchingShaft(level, forwardShaftPos, shaftDirection, shaftSubLevelId)
                || !isMatchingShaft(level, backwardShaftPos, shaftDirection, shaftSubLevelId)) {
            return false;
        }

        for (Direction carriageFacing : Direction.values()) {
            if (carriageFacing.getAxis() == shaftDirection.getAxis()) {
                continue;
            }
            if (isShaftFaceOccupiedForPlacement(level, forwardShaftPos, shaftDirection, carriageFacing,
                    shaftSubLevelId, null)
                    && isShaftFaceOccupiedForPlacement(level, backwardShaftPos, shaftDirection, carriageFacing,
                    shaftSubLevelId, null)) {
                return true;
            }
        }
        return false;
    }

    // Check if the shaft is matching
    private static boolean isMatchingShaft(Level level, BlockPos shaftPos, Direction shaftDirection,
                                           UUID shaftSubLevelId) {
        BlockState state = findShaftState(level, shaftPos, shaftSubLevelId);
        return state != null
                && state.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                && state.getValue(PhysicsGantryShaftBlock.FACING) == shaftDirection;
    }

    // Check if the shaft face is occupied
    private static boolean isShaftFaceOccupied(Level level, BlockPos shaftPos, Direction shaftDirection,
                                               Direction carriageFacing, UUID shaftSubLevelId,
                                               PhysicsGantryCarriageBlockEntity ignoredCarriage) {
        if (shaftPos == null || shaftDirection == null || carriageFacing == null) {
            return false;
        }

        for (PhysicsGantryCarriageBlockEntity other : collectTrackedCarriages(level, shaftPos, shaftDirection)) {
            if (other == ignoredCarriage || other.isRemoved()) {
                continue;
            }

            BlockPos otherShaftPos = other.hasPendingManualRelink()
                    ? other.pendingManualRelinkShaftPos
                    : other.attachedShaftPos;
            Direction otherShaftDirection = other.hasPendingManualRelink()
                    ? other.pendingManualRelinkShaftDirection
                    : other.attachedShaftDirection;
            Direction otherCarriageFacing = other.hasPendingManualRelink()
                    ? other.pendingManualRelinkCarriageFacing
                    : other.attachedCarriageFacing;

            if (otherShaftPos != null
                    && otherShaftDirection == shaftDirection
                    && otherCarriageFacing == carriageFacing
                    && Objects.equals(other.attachedShaftSubLevelId, shaftSubLevelId)
                    && isSameContiguousShaft(level, shaftPos, otherShaftPos, shaftDirection, shaftSubLevelId)) {
                return true;
            }
        }
        return false;
    }

    // Check if this has placed carriage on shaft face
    private static boolean hasPlacedCarriageOnShaftFace(Level level, BlockPos shaftPos, Direction shaftDirection,
                                                        Direction carriageFacing, UUID shaftSubLevelId,
                                                        BlockPos ignoredCarriagePos) {
        int forwardSpan = measureShaftSpan(level, shaftPos, shaftDirection, shaftDirection, shaftSubLevelId);
        int backwardSpan = measureShaftSpan(level, shaftPos, shaftDirection.getOpposite(), shaftDirection,
                shaftSubLevelId);
        for (int offset = -backwardSpan; offset <= forwardSpan; offset++) {
            BlockPos carriagePos = shaftPos.relative(shaftDirection, offset).relative(carriageFacing);
            if (carriagePos.equals(ignoredCarriagePos)) {
                continue;
            }
            BlockState carriageState = findBlockState(level, carriagePos, shaftSubLevelId);
            if (carriageState != null
                    && carriageState.getBlock() == CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                    && carriageState.getValue(PhysicsGantryCarriageBlock.FACING) == carriageFacing) {
                return true;
            }
        }
        return false;
    }

    // Check if this is same contiguous shaft
    private static boolean isSameContiguousShaft(Level level, BlockPos first, BlockPos second,
                                                 Direction shaftDirection, UUID shaftSubLevelId) {
        if (!isOnSameShaftLine(first, second, shaftDirection.getAxis())) {
            return false;
        }

        long signedDistance = switch (shaftDirection.getAxis()) {
            case X -> second.getX() - first.getX();
            case Y -> second.getY() - first.getY();
            case Z -> second.getZ() - first.getZ();
        };
        long absoluteDistance = Math.abs(signedDistance);
        if (absoluteDistance > MAX_SHAFT_SCAN_BLOCKS) {
            return false;
        }
        Direction step = signedDistance * shaftDirection.getAxisDirection().getStep() >= 0
                ? shaftDirection
                : shaftDirection.getOpposite();
        BlockPos cursor = first;
        for (int distance = 0; distance <= absoluteDistance; distance++) {
            BlockState state = findShaftState(level, cursor, shaftSubLevelId);
            if (state == null
                    || state.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                    || state.getValue(PhysicsGantryShaftBlock.FACING) != shaftDirection) {
                return false;
            }
            cursor = cursor.relative(step);
        }
        return true;
    }

    // Find the shaft state
    private static BlockState findShaftState(Level level, BlockPos shaftPos, UUID shaftSubLevelId) {
        if (level == null || shaftPos == null) {
            return null;
        }
        if (shaftSubLevelId != null) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                    level, shaftSubLevelId, shaftPos);
            return blockEntity instanceof PhysicsGantryShaftBlockEntity shaft
                    ? shaft.getBlockState() : null;
        }
        Level lookupLevel = resolveRootLevel(level);
        return lookupLevel == null || !lookupLevel.isLoaded(shaftPos)
                ? null : lookupLevel.getBlockState(shaftPos);
    }

    // Collect the tracked carriages
    private static Set<PhysicsGantryCarriageBlockEntity> collectTrackedCarriages(Level level, BlockPos shaftPos,
                                                                                 Direction shaftDirection) {
        Set<PhysicsGantryCarriageBlockEntity> carriages = new LinkedHashSet<>();
        Level lookupLevel = resolveRootLevel(level);
        if (lookupLevel == null) {
            return carriages;
        }

        int chunkRadius = 1;
        if (shaftPos != null && shaftDirection != null) {
            int forwardSpan = measureShaftSpan(lookupLevel, shaftPos, shaftDirection, shaftDirection, null);
            int backwardSpan = measureShaftSpan(lookupLevel, shaftPos, shaftDirection.getOpposite(), shaftDirection,
                    null);
            chunkRadius = Mth.clamp((forwardSpan + backwardSpan) / 16 + 1,
                    1, MAX_TRACKED_CARRIAGE_CHUNK_RADIUS);
        }

        BlockPos center = shaftPos == null ? BlockPos.ZERO : shaftPos;
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(lookupLevel, center, chunkRadius)) {
            if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage && !carriage.isRemoved()) {
                carriages.add(carriage);
            }
        }

        SubLevelContainer container = SubLevelContainer.getContainer(lookupLevel);
        if (container != null) {
            for (Object subLevel : container.getAllSubLevels()) {
                for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                    if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage && !carriage.isRemoved()) {
                        carriages.add(carriage);
                    }
                }
            }
        }
        return carriages;
    }

    // Get the measure shaft span
    private static int measureShaftSpan(Level lookupLevel, BlockPos origin, Direction stepDirection,
                                        Direction shaftDirection, UUID shaftSubLevelId) {
        int distance = 0;
        BlockPos cursor = origin;
        while (distance < MAX_SHAFT_SCAN_BLOCKS) {
            cursor = cursor.relative(stepDirection);
            BlockState state = findShaftState(lookupLevel, cursor, shaftSubLevelId);
            if (state == null
                    || state.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                    || state.getValue(PhysicsGantryShaftBlock.FACING) != shaftDirection) {
                break;
            }
            distance++;
        }
        return distance;
    }

    // Find the block state
    private static BlockState findBlockState(Level level, BlockPos pos, UUID subLevelId) {
        if (subLevelId != null) {
            BlockEntity blockEntity = SimulatedHelper.findLoadedBlockEntityExact(level, subLevelId, pos);
            return blockEntity == null ? null : blockEntity.getBlockState();
        }
        Level lookupLevel = resolveRootLevel(level);
        return lookupLevel == null || !lookupLevel.isLoaded(pos) ? null : lookupLevel.getBlockState(pos);
    }

    // Resolve the root level
    private static Level resolveRootLevel(Level level) {
        ServerLevel root = SableLevelApi.serverLevel(level);
        return root == null ? level : root;
    }

    // Check if this is on the same shaft line
    private static boolean isOnSameShaftLine(BlockPos first, BlockPos second, Direction.Axis axis) {
        return switch (axis) {
            case X -> first.getY() == second.getY() && first.getZ() == second.getZ();
            case Y -> first.getX() == second.getX() && first.getZ() == second.getZ();
            case Z -> first.getX() == second.getX() && first.getY() == second.getY();
        };
    }

    // Apply the attachment pose
    private void applyAttachmentPose(Object subLevel, boolean updateDebugAnchor) {
        if (subLevel == null || attachedShaftPos == null || attachedShaftDirection == null || attachedCarriageFacing == null) {
            return;
        }

        PhysicsGantryShaftBlockEntity shaftBE = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftBE == null) {
            return;
        }

        if (updateDebugAnchor) {
            Vec3 shaftDirectionLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
            Vec3 carriageFacingLocal = Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal());
            Vec3 localAnchor = Vec3.atCenterOf(attachedShaftPos)
                    .add(shaftDirectionLocal.x * attachedShaftProgress,
                            shaftDirectionLocal.y * attachedShaftProgress,
                            shaftDirectionLocal.z * attachedShaftProgress)
                    .add(carriageFacingLocal.x * 0.5D,
                            carriageFacingLocal.y * 0.5D,
                            carriageFacingLocal.z * 0.5D);
            updateDebugAnchorEntity(SimulatedHelper.toContainingWorldPosition(shaftBE, localAnchor), true);
        }

        updateShaftConstraintForGameTick(subLevel);
    }

    // Check if the attached sublevel would hit a protected world block
    private boolean wouldAttachedSubLevelHitProtectedWorldBlock(Object subLevel, double progress) {
        if (subLevel == null || attachedShaftPos == null || attachedShaftDirection == null || attachedCarriageFacing == null) {
            return false;
        }

        PhysicsGantryShaftBlockEntity shaftBE = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftBE == null) {
            return true;
        }

        Vec3 worldAnchor = computeAttachmentWorldAnchor(shaftBE, progress);
        if (worldAnchor == null) {
            return true;
        }
        Quaterniond orientation = resolveAttachmentWorldOrientation(shaftBE, subLevel);
        Vector3d targetPosition = computeLockedAttachmentTargetPos(subLevel, worldAnchor, orientation);
        return wouldSubLevelHitProtectedWorldBlock(subLevel, targetPosition, orientation);
    }

    // Calculate the attachment world anchor
    private Vec3 computeAttachmentWorldAnchor(PhysicsGantryShaftBlockEntity shaftBE, double progress) {
        Vec3 shaftDirectionLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
        Vec3 carriageFacingLocal = Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal());
        Vec3 localAnchor = Vec3.atCenterOf(attachedShaftPos)
            .add(shaftDirectionLocal.x * progress,
                shaftDirectionLocal.y * progress,
                shaftDirectionLocal.z * progress)
            .add(carriageFacingLocal.x * 0.5D,
                carriageFacingLocal.y * 0.5D,
                carriageFacingLocal.z * 0.5D);
        return SimulatedHelper.toContainingWorldPosition(shaftBE, localAnchor);
    }

    // Check if the sublevel would hit a protected world block
    private boolean wouldSubLevelHitProtectedWorldBlock(Object subLevel, Vector3dc targetPosition, Quaterniondc orientation) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || subLevel == null || targetPosition == null || orientation == null) {
            return false;
        }

        int[] bounds = getSubLevelBlockBounds(subLevel);
        if (bounds == null) {
            return true;
        }

        Vector3d rotationPoint = readSubLevelRotationPoint(subLevel);
        if (rotationPoint == null) {
            rotationPoint = new Vector3d();
        }

        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    BlockPos localPos = new BlockPos(x, y, z);
                    BlockState payloadState = serverLevel.getBlockState(localPos);
                    if (payloadState.isAir()) {
                        continue;
                    }
                    if (isProtectedWorldBlock(serverLevel, localPos, payloadState)) {
                        return true;
                    }

                    BlockPos targetPos = transformSubLevelBlockToWorld(localPos, rotationPoint, targetPosition, orientation);
                    BlockState targetState = serverLevel.getBlockState(targetPos);
                    if (isProtectedWorldBlock(serverLevel, targetPos, targetState)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Check if disassembly would overwrite a protected world block
    private boolean wouldDisassemblyOverwriteProtectedWorldBlock(Object subLevel, BlockPos anchor, BlockPos goal,
                                                                 Rotation rotation) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || subLevel == null || anchor == null || goal == null) {
            return false;
        }

        int[] bounds = getSubLevelBlockBounds(subLevel);
        if (bounds == null) {
            return true;
        }

        for (int x = bounds[0]; x <= bounds[3]; x++) {
            for (int y = bounds[1]; y <= bounds[4]; y++) {
                for (int z = bounds[2]; z <= bounds[5]; z++) {
                    BlockPos localPos = new BlockPos(x, y, z);
                    BlockState payloadState = serverLevel.getBlockState(localPos);
                    if (payloadState.isAir()) {
                        continue;
                    }
                    if (isProtectedWorldBlock(serverLevel, localPos, payloadState)) {
                        return true;
                    }

                    BlockPos targetPos = transformDisassemblyBlockPos(localPos, anchor, goal, rotation);
                    BlockState targetState = serverLevel.getBlockState(targetPos);
                    if (isProtectedWorldBlock(serverLevel, targetPos, targetState)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Transform the disassembly block pos
    private BlockPos transformDisassemblyBlockPos(BlockPos localPos, BlockPos anchor, BlockPos goal, Rotation rotation) {
        int angle = rotation == Rotation.NONE ? 0 : 4 - rotation.ordinal();
        Vec3 target = localPos.getCenter()
                .subtract(anchor.getCenter())
                .yRot((float) (angle * Math.PI / 2.0D))
                .add(goal.getCenter());
        return BlockPos.containing(target);
    }

    // Resolve the disassembly rotation
    private Rotation resolveDisassemblyRotation(Object subLevel) {
        Quaterniond orientation = readSubLevelOrientation(subLevel);
        if (orientation == null) {
            return Rotation.NONE;
        }

        PhysicsGantryShaftBlockEntity shaft = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        Object containingShaft = shaft == null ? null : SimulatedHelper.getContainingSubLevel(shaft);
        Quaterniond parentOrientation = readSubLevelOrientation(containingShaft);
        if (parentOrientation != null) {
            orientation = new Quaterniond(parentOrientation).conjugate().mul(orientation).normalize();
        }

        double closestYRotation = SimMathUtils.getClosestYaw(orientation);
        int turns = -Mth.floor(closestYRotation / (Math.PI / 2.0D) + 0.5D);
        return SimAssemblyHelper.rotationFrom90DegRots(turns);
    }

    // Transform the sublevel block to world
    private BlockPos transformSubLevelBlockToWorld(BlockPos localPos, Vector3dc rotationPoint,
                                                   Vector3dc targetPosition, Quaterniondc orientation) {
        Vector3d local = new Vector3d(
                localPos.getX() + 0.5D - rotationPoint.x(),
                localPos.getY() + 0.5D - rotationPoint.y(),
                localPos.getZ() + 0.5D - rotationPoint.z());
        orientation.transform(local);
        local.add(targetPosition);
        return BlockPos.containing(local.x, local.y, local.z);
    }

    // Get the sublevel block bounds
    private int[] getSubLevelBlockBounds(Object subLevel) {
        if (!(subLevel instanceof SubLevel sableSubLevel)) {
            return null;
        }
        var bounds = sableSubLevel.getPlot().getBoundingBox();
        if (bounds == null || bounds.maxX() < bounds.minX() || bounds.maxY() < bounds.minY()
                || bounds.maxZ() < bounds.minZ()) {
            return null;
        }
        return new int[]{
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ()
        };
    }

    // Check if this is a protected world block
    private boolean isProtectedWorldBlock(Level checkLevel, BlockPos pos, BlockState state) {
        return state.getBlock() == Blocks.BEDROCK || state.getDestroySpeed(checkLevel, pos) == -1.0f;
    }

    // Detach the shaft keep sublevel
    private void detachFromShaftKeepSubLevel(String reason) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (!hasTrackedAttachmentState() && shaftConstraintHandle == null) {
            return;
        }

        BlockPos previousShaftPos = attachedShaftPos;
        UUID previousSubLevelId = attachedSubLevelId;

        clearShaftConstraint();
        clearDebugAnchorEntity();
        clearPendingManualRelink();

        assembledToSubLevel = false;
        attachedShaftPos = null;
        attachedShaftDirection = null;
        attachedCarriageFacing = null;
        attachedShaftProgress = 0.0d;
        attachedShaftSubLevelId = null;
        invalidateShaftSpanCache();
        hasLockedSubLevelOrientation = false;
        lockedSubLevelOrientation.identity();
        hasLockedShaftFrameOrientation = false;
        lockedShaftFrameOrientation.identity();
        hasLockedLocalAttachmentAnchor = false;
        lockedLocalAttachmentAnchor.set(0.0, 0.0, 0.0);
        hasLockedRotationPoint = false;
        lockedRotationPoint.set(0.0, 0.0, 0.0);

        attachedSubLevelId = null;

        SableAssemblyTopologyInvalidation.invalidate(level);

        setChanged();
        sendData();

        refreshShaftAnchorLookup(previousShaftPos);

        CT_LOGGER.debug("[CT][PhysicsGantry] detached from shaft pos={} reason={} subLevelId={}",
                worldPosition,
                reason,
                previousSubLevelId);
    }

    // Check if this has pending manual relink
    private boolean hasPendingManualRelink() {
        return pendingManualRelinkShaftPos != null
                && pendingManualRelinkShaftDirection != null
                && pendingManualRelinkCarriageFacing != null;
    }

    // Clear the pending manual relink
    private void clearPendingManualRelink() {
        pendingManualRelinkShaftPos = null;
        pendingManualRelinkShaftDirection = null;
        pendingManualRelinkCarriageFacing = null;
        pendingManualRelinkTicks = 0;
        pendingManualRelinkStartPos.set(0.0, 0.0, 0.0);
        pendingManualRelinkTargetPos.set(0.0, 0.0, 0.0);
        pendingManualRelinkOrientation.identity();
    }

    // Update the pending manual relink
    private boolean tickPendingManualRelink() {
        if (!hasPendingManualRelink()) {
            return false;
        }

        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            clearPendingManualRelink();
            return false;
        }

        UUID pendingSubLevelId = attachedShaftSubLevelId;
        if (pendingSubLevelId == null) {
            pendingSubLevelId = extractSubLevelId(resolveAttachedSubLevel());
        }
        BlockState shaftState = findAttachedShaftState(pendingManualRelinkShaftPos, pendingSubLevelId);
        if (shaftState == null) {
            shaftState = serverLevel.getBlockState(pendingManualRelinkShaftPos);
        }
        if (shaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                || shaftState.getValue(PhysicsGantryShaftBlock.FACING) != pendingManualRelinkShaftDirection) {

            clearPendingManualRelink();
            return true;
        }

        Object subLevel = resolveAttachedSubLevel();
        if (subLevel == null) {
            clearPendingManualRelink();
            return false;
        }
        if (isShaftFaceOccupied(pendingManualRelinkShaftPos, pendingManualRelinkShaftDirection,
                pendingManualRelinkCarriageFacing, attachedShaftSubLevelId)) {
            clearPendingManualRelink();
            clearAttachmentTrackingState();
            resetSubLevelVelocity(subLevel);
            return true;
        }

        double alpha = (pendingManualRelinkTicks + 1) / (double) MANUAL_RELINK_LERP_TICKS;
        alpha = Mth.clamp(alpha, 0.0D, 1.0D);

        alpha = alpha * alpha * (3.0D - (2.0D * alpha));
        Vector3d lerpedPos = new Vector3d(pendingManualRelinkStartPos).lerp(pendingManualRelinkTargetPos, alpha);
        if (wouldSubLevelHitProtectedWorldBlock(subLevel, lerpedPos, pendingManualRelinkOrientation)) {
            clearPendingManualRelink();
            resetSubLevelVelocity(subLevel);
            return true;
        }
        teleportSubLevel(subLevel, lerpedPos, pendingManualRelinkOrientation);
        resetSubLevelVelocity(subLevel);
        pendingManualRelinkTicks++;

        if (pendingManualRelinkTicks < MANUAL_RELINK_LERP_TICKS) {
            return true;
        }

        UUID subLevelId = extractSubLevelId(subLevel);
        if (subLevelId != null) {

            setAssembledAttachmentState(
                    pendingManualRelinkShaftPos,
                    pendingManualRelinkShaftDirection,
                    pendingManualRelinkCarriageFacing,
                    subLevelId,
                    attachedShaftSubLevelId);
            setChanged();
            sendData();
        }

        clearPendingManualRelink();
        return true;
    }

    // Calculate the manual relink target pos
    private Vector3d computeManualRelinkTargetPos(Object subLevel,
                                                       Vec3 worldAnchor,
                                                       Quaterniond orientation,
                                                       BlockState carriageState) {
        Vector3d rotationPoint = readSubLevelRotationPoint(subLevel);
        if (rotationPoint == null) {
            rotationPoint = new Vector3d();
        }

        Direction currentFacing = carriageState.hasProperty(PhysicsGantryCarriageBlock.FACING)
                ? carriageState.getValue(PhysicsGantryCarriageBlock.FACING)
                : Direction.UP;

        Vec3 carriageAttachmentLocal = worldPosition.getCenter()
                .add(currentFacing.getStepX() * -0.5D,
                        currentFacing.getStepY() * -0.5D,
                        currentFacing.getStepZ() * -0.5D);

        hasLockedSubLevelOrientation = true;
        lockedSubLevelOrientation.set(orientation);
        hasLockedShaftFrameOrientation = false;
        lockedShaftFrameOrientation.identity();
        hasLockedRotationPoint = true;
        lockedRotationPoint.set(rotationPoint);
        hasLockedLocalAttachmentAnchor = true;
        lockedLocalAttachmentAnchor.set(
                carriageAttachmentLocal.x - rotationPoint.x,
                carriageAttachmentLocal.y - rotationPoint.y,
                carriageAttachmentLocal.z - rotationPoint.z);

        return computeLockedAttachmentTargetPos(subLevel, worldAnchor, orientation);
    }

    // Calculate the relink block state
    private BlockState computeRelinkBlockState(Direction clickedFace, BlockState shaftState) {

        BlockState carriageState = getBlockState();
        if (carriageState.hasProperty(PhysicsGantryCarriageBlock.FACING)
                && carriageState.getBlock() instanceof PhysicsGantryCarriageBlock block) {
            BlockState withNewFacing = carriageState.setValue(PhysicsGantryCarriageBlock.FACING, clickedFace);

            return block.cycleAxisIfNecessary(withNewFacing, clickedFace.getOpposite(), shaftState);
        }
        return carriageState;
    }

    // Calculate the manual relink orientation
    private Quaterniond computeManualRelinkOrientation(BlockState currentState,
                                                       BlockState resolvedState,
                                                       Quaterniond currentOrientation,
                                                       BlockState shaftState,
                                                       Direction clickedFace,
                                                       PhysicsGantryShaftBlockEntity clickedShaft) {

        Quaterniond aligned = computeCarriageToShaftAlignment(currentState, shaftState, clickedFace, clickedShaft);
        if (aligned != null) {
            return aligned;
        }

        Quaterniond currentLocalPose = computeCarriageLocalPose(currentState);
        Quaterniond targetLocalPose = computeCarriageLocalPose(resolvedState);
        Quaterniond localDelta = new Quaterniond(targetLocalPose)
                .mul(new Quaterniond(currentLocalPose).conjugate())
                .normalize();

        return localDelta.mul(new Quaterniond(currentOrientation)).normalize();
    }

    // Calculate the carriage to shaft alignment
    private Quaterniond computeCarriageToShaftAlignment(BlockState currentState,
                                                        BlockState shaftState,
                                                        Direction clickedFace,
                                                        PhysicsGantryShaftBlockEntity clickedShaft) {
        if (clickedFace == null || !currentState.hasProperty(PhysicsGantryCarriageBlock.FACING)) {
            return null;
        }

        Direction currentFacing = currentState.getValue(PhysicsGantryCarriageBlock.FACING);
        Direction.Axis localShaftAxis = PhysicsGantryCarriageBlock.getValidGantryShaftAxis(currentState);
        Direction shaftDirection = shaftState.hasProperty(PhysicsGantryShaftBlock.FACING)
                ? shaftState.getValue(PhysicsGantryShaftBlock.FACING)
                : Direction.fromAxisAndDirection(localShaftAxis, Direction.AxisDirection.POSITIVE);

        Vector3d localOutward = directionVector(currentFacing);
        Vector3d localShaft = axisVector(localShaftAxis);
        Vector3d targetOutward = directionVector(clickedFace);
        Vector3d targetShaft = directionVector(shaftDirection);

        if (clickedShaft != null) {
            Vec3 outward = SimulatedHelper.toContainingWorldDirection(clickedShaft, toVec3(targetOutward));
            Vec3 shaft = SimulatedHelper.toContainingWorldDirection(clickedShaft, toVec3(targetShaft));
            if (outward == null || shaft == null || outward.lengthSqr() < 1.0E-6D || shaft.lengthSqr() < 1.0E-6D) {
                return null;
            }
            targetOutward.set(outward.x, outward.y, outward.z);
            targetShaft.set(shaft.x, shaft.y, shaft.z);
        }

        return basisOrientation(targetOutward, targetShaft)
                .mul(basisOrientation(localOutward, localShaft).conjugate())
                .normalize();
    }

    // Get the direction vector
    private Vector3d directionVector(Direction dir) {
        return new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // Get the axis vector
    private Vector3d axisVector(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
    }

    // Convert the vector to a Minecraft position
    private Vec3 toVec3(Vector3dc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    // Get the basis orientation
    private Quaterniond basisOrientation(Vector3d outward, Vector3d shaft) {
        Vector3d z = new Vector3d(outward).normalize();
        Vector3d x = new Vector3d(shaft).normalize();
        x.sub(new Vector3d(z).mul(x.dot(z)));
        if (x.lengthSquared() < 1.0E-8D) {
            x = Math.abs(z.y) < 0.9D
                    ? new Vector3d(0.0D, 1.0D, 0.0D)
                    : new Vector3d(1.0D, 0.0D, 0.0D);
            x.sub(new Vector3d(z).mul(x.dot(z)));
        }
        x.normalize();
        Vector3d y = new Vector3d(z).cross(x).normalize();

        Matrix3d basis = new Matrix3d().identity();
        basis.setColumn(0, x);
        basis.setColumn(1, y);
        basis.setColumn(2, z);
        return new Quaterniond().setFromNormalized(basis).normalize();
    }

    // Calculate the carriage local pose
    private Quaterniond computeCarriageLocalPose(BlockState state) {

        Direction facing = state.hasProperty(PhysicsGantryCarriageBlock.FACING)
                ? state.getValue(PhysicsGantryCarriageBlock.FACING)
                : Direction.UP;
        boolean alongFirst = state.hasProperty(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE)
                && state.getValue(PhysicsGantryCarriageBlock.AXIS_ALONG_FIRST_COORDINATE);

        Quaterniond orientation = new Quaterniond();
        orientation.rotateY(Math.toRadians(AngleHelper.horizontalAngle(facing)));
        orientation.rotateX(Math.toRadians(
                facing == Direction.UP ? 0.0D : (facing == Direction.DOWN ? 180.0D : 90.0D)));
        orientation.rotateY(Math.toRadians(alongFirst ^ facing.getAxis() == Direction.Axis.X ? 0.0D : 90.0D));
        return orientation.normalize();
    }

    // Emit the debug attachment particles
    private void emitDebugAttachmentParticles(Vec3 worldAnchor, boolean constraintActive) {
        if (!ENABLE_GANTRY_DEBUG_VISUALS) {
            return;
        }

        return;
    }

    // Update the debug anchor entity
    private void updateDebugAnchorEntity(Vec3 worldAnchor, boolean constraintActive) {
        if (!ENABLE_GANTRY_DEBUG_VISUALS) {
            clearDebugAnchorEntity();
            return;
        }
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) {
            return;
        }

        ArmorStand anchor = null;
        if (debugAnchorEntityId != null) {
            Entity existing = serverLevel.getEntity(debugAnchorEntityId);
            if (existing instanceof ArmorStand stand && !stand.isRemoved()) {
                anchor = stand;
            }
        }

        if (anchor == null) {
            anchor = new ArmorStand(serverLevel, worldAnchor.x, worldAnchor.y, worldAnchor.z);
            anchor.setNoGravity(true);
            anchor.setInvulnerable(true);
            anchor.setNoBasePlate(true);
            anchor.setSilent(true);
            anchor.setGlowingTag(true);
            anchor.setCustomNameVisible(true);
            serverLevel.addFreshEntity(anchor);
            debugAnchorEntityId = anchor.getUUID();
        }

        debugParticleTickGate = (debugParticleTickGate + 1) % 5;
        if (debugParticleTickGate != 0) {
            return;
        }

        anchor.teleportTo(worldAnchor.x, worldAnchor.y, worldAnchor.z);
        anchor.setCustomName(Component.literal(
                constraintActive ? "CT Gantry Anchor: ACTIVE" : "CT Gantry Anchor: FALLBACK"));
    }

    // Clear the debug anchor entity
    private void clearDebugAnchorEntity() {
        if (!ENABLE_GANTRY_DEBUG_VISUALS) {
            if (debugAnchorEntityId != null) {
                ServerLevel serverLevel = resolveServerLevel(level);
                if (serverLevel != null) {
                    Entity existing = serverLevel.getEntity(debugAnchorEntityId);
                    if (existing != null && !existing.isRemoved()) {
                        existing.discard();
                    }
                }
            }
            debugAnchorEntityId = null;
            return;
        }
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel != null && debugAnchorEntityId != null) {
            Entity existing = serverLevel.getEntity(debugAnchorEntityId);
            if (existing != null && !existing.isRemoved()) {
                existing.discard();
            }
        }
        debugAnchorEntityId = null;
    }

    // Convert the physics gantry carriage to sublevel local anchor
    private Vec3 toSubLevelLocalAnchor(Object subLevel, Vec3 worldAnchor) {
        Vector3d pos = readSubLevelPosition(subLevel);
        Quaterniond orientation = readSubLevelOrientation(subLevel);
        if (pos == null || orientation == null) {
            return Vec3.ZERO;
        }

        Vector3d local = new Vector3d(worldAnchor.x, worldAnchor.y, worldAnchor.z)
                .sub(pos);
        new Quaterniond(orientation).conjugate().transform(local);
        return new Vec3(local.x, local.y, local.z);
    }

    // Clear the shaft constraint
    private void clearShaftConstraint() {
        if (shaftConstraintHandle != null) {
            try {
                shaftConstraintHandle.remove();
            } catch (Exception err) {
                CT_LOGGER.warn("[CT][PhysicsGantry] constraint remove exception at {}: {}",
                        worldPosition,
                        err.toString());
            }
        }

        shaftConstraintHandle = null;
        shaftConstraintParent = null;
        shaftConstraintChild = null;
        shaftConstraintProgress = Double.NaN;
    }

    // Clamp movement while discovering only the shaft blocks the carriage is about to use
    private double clampMovementToAvailableShaft(Level lookupLevel, double requestedProgress) {
        if (lookupLevel == null || attachedShaftPos == null || attachedShaftDirection == null
                || !Double.isFinite(requestedProgress)) {
            return attachedShaftProgress;
        }

        if (requestedProgress > cachedForwardShaftSpan) {
            int requiredSpan = Math.min(MAX_SHAFT_SCAN_BLOCKS,
                    Math.max(0, (int) Math.ceil(requestedProgress)));
            cachedForwardShaftSpan = extendVerifiedShaftSpan(
                    lookupLevel, attachedShaftDirection, cachedForwardShaftSpan, requiredSpan);
        } else if (requestedProgress < -cachedBackwardShaftSpan) {
            int requiredSpan = Math.min(MAX_SHAFT_SCAN_BLOCKS,
                    Math.max(0, (int) Math.ceil(-requestedProgress)));
            cachedBackwardShaftSpan = extendVerifiedShaftSpan(
                    lookupLevel, attachedShaftDirection.getOpposite(), cachedBackwardShaftSpan, requiredSpan);
        }

        int occupiedOffset = Mth.floor(requestedProgress + 0.5D);
        if (occupiedOffset != 0 && !isVerifiedShaftOffset(lookupLevel, occupiedOffset)) {
            if (occupiedOffset > 0) {
                cachedForwardShaftSpan = Math.min(cachedForwardShaftSpan, occupiedOffset - 1);
            } else {
                cachedBackwardShaftSpan = Math.min(cachedBackwardShaftSpan, -occupiedOffset - 1);
            }
        }

        return Mth.clamp(requestedProgress, -cachedBackwardShaftSpan, cachedForwardShaftSpan);
    }

    // Check the shaft block occupied by the next carriage position
    private boolean isVerifiedShaftOffset(Level lookupLevel, int signedOffset) {
        Direction stepDirection = signedOffset < 0
                ? attachedShaftDirection.getOpposite() : attachedShaftDirection;
        BlockPos shaftPos = attachedShaftPos.relative(stepDirection, Math.abs(signedOffset));
        BlockState state = findShaftState(lookupLevel, shaftPos, attachedShaftSubLevelId);
        return state != null
                && state.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                && state.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
    }

    // Extend one cached end of the shaft only as far as the current movement requires
    private int extendVerifiedShaftSpan(Level lookupLevel, Direction stepDirection,
                                        int verifiedSpan, int requiredSpan) {
        int span = Mth.clamp(verifiedSpan, 0, MAX_SHAFT_SCAN_BLOCKS);
        int target = Mth.clamp(requiredSpan, span, MAX_SHAFT_SCAN_BLOCKS);
        while (span < target) {
            BlockPos shaftPos = attachedShaftPos.relative(stepDirection, span + 1);
            BlockState state = findShaftState(lookupLevel, shaftPos, attachedShaftSubLevelId);
            if (state == null
                    || state.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                    || state.getValue(PhysicsGantryShaftBlock.FACING) != attachedShaftDirection) {
                break;
            }
            span++;
        }
        return span;
    }

    // Forget verified travel whenever the carriage is attached to a different shaft frame
    private void invalidateShaftSpanCache() {
        cachedForwardShaftSpan = 0;
        cachedBackwardShaftSpan = 0;
    }

    // Check if this has valid attached shaft
    private boolean hasValidAttachedShaft(Level lookupLevel) {
        if (!assembledToSubLevel || lookupLevel == null || attachedShaftPos == null
                || attachedShaftDirection == null || attachedCarriageFacing == null) {
            return false;
        }

        BlockState shaftState = findAttachedShaftState(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftState == null && attachedShaftSubLevelId == null && lookupLevel.isLoaded(attachedShaftPos)) {
            shaftState = lookupLevel.getBlockState(attachedShaftPos);
        }
        return shaftState != null
                && shaftState.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                && shaftState.getValue(PhysicsGantryShaftBlock.FACING) == attachedShaftDirection;
    }

    // Find the attached shaft entity
    private PhysicsGantryShaftBlockEntity findAttachedShaftEntity(BlockPos shaftPos, UUID preferredSubLevelId) {
        if (level == null || shaftPos == null) {
            return null;
        }

        BlockEntity scopedBlockEntity = SimulatedHelper.findLoadedBlockEntityExact(
                level, preferredSubLevelId, shaftPos);
        PhysicsGantryShaftBlockEntity scoped = scopedBlockEntity instanceof PhysicsGantryShaftBlockEntity found
                ? found : null;
        if (scoped != null) {
            return scoped;
        }

        PhysicsGantryShaftBlockEntity includingSubLevels =
                SubLevelBlockEntityCollector.findLoadedIncludingSubLevels(
                        level, shaftPos, PhysicsGantryShaftBlockEntity.class);
        if (includingSubLevels != null) {
            return includingSubLevels;
        }

        Level lookupLevel = resolveLookupLevel(level);
        if (lookupLevel == null) {
            return null;
        }

        if (!lookupLevel.isLoaded(shaftPos)) {
            return null;
        }
        BlockEntity fallback = lookupLevel.getBlockEntity(shaftPos);
        return fallback instanceof PhysicsGantryShaftBlockEntity shaft ? shaft : null;
    }

    // Find the attached shaft state
    private BlockState findAttachedShaftState(BlockPos shaftPos, UUID preferredSubLevelId) {
        PhysicsGantryShaftBlockEntity shaft = findAttachedShaftEntity(shaftPos, preferredSubLevelId);
        return shaft == null ? null : shaft.getBlockState();
    }

    // Resolve the lookup level
    private Level resolveLookupLevel(Level currentLevel) {
        Level src = currentLevel == null ? level : currentLevel;
        ServerLevel root = SableLevelApi.serverLevel(src);
        return root == null ? src : root;
    }

    // Resolve the server level
    private ServerLevel resolveServerLevel(Level currentLevel) {
        return SableLevelApi.serverLevel(resolveLookupLevel(currentLevel));
    }

    // Get the sable containing
    private Object getSableContaining() {
        return SableLevelApi.containing(this);
    }

    // Get the nearest grid from sublevel
    private BlockPos nearestGridFromSubLevel(Object subLevel) {
        Vector3d pos = readSubLevelPosition(subLevel);
        if (pos == null) {
            return worldPosition;
        }

        return BlockPos.containing(pos.x, pos.y, pos.z);
    }

    // Resolve the disassembly goal block pos
    private BlockPos resolveDisassemblyGoalBlockPos(Object subLevel) {
        if (attachedShaftPos != null && attachedShaftDirection != null && attachedCarriageFacing != null) {
            Vec3 baseCenter = Vec3.atCenterOf(attachedShaftPos);
            Vec3 targetCenter = baseCenter
                    .add(attachedShaftDirection.getStepX() * attachedShaftProgress,
                            attachedShaftDirection.getStepY() * attachedShaftProgress,
                            attachedShaftDirection.getStepZ() * attachedShaftProgress)
                    .add(attachedCarriageFacing.getStepX(),
                            attachedCarriageFacing.getStepY(),
                            attachedCarriageFacing.getStepZ());

            return BlockPos.containing(targetCenter.x, targetCenter.y, targetCenter.z);
        }
        return nearestGridFromSubLevel(subLevel);
    }

    // Read the sublevel orientation
    private Quaterniond readSubLevelOrientation(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? new Quaterniond(sableSubLevel.logicalPose().orientation())
                : null;
    }

    // Read the sublevel position
    private Vector3d readSubLevelPosition(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? new Vector3d(sableSubLevel.logicalPose().position())
                : null;
    }

    // Read the sublevel rotation point
    private Vector3d readSubLevelRotationPoint(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? new Vector3d(sableSubLevel.logicalPose().rotationPoint())
                : null;
    }

    // Teleport the sublevel
    private void teleportSubLevel(Object subLevel, Vector3dc pos, Quaterniondc orientation) {
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)
                || pos == null || orientation == null || level == null) {
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics != null) {
            physics.getPipeline().teleport(serverSubLevel, pos, orientation);
        }
    }

    // Handle the maybe correct far sublevel pose
    @SuppressWarnings("unused")
    private void maybeCorrectFarSubLevelPose(Object subLevel, Vec3 targetCenter) {
        if (subLevel == null || targetCenter == null) {
            return;
        }

        if (!isFiniteAndSafe(targetCenter.x) || !isFiniteAndSafe(targetCenter.y) || !isFiniteAndSafe(targetCenter.z)) {
            return;
        }

        Vector3d currentPos = readSubLevelPosition(subLevel);
        boolean needsCorrection = currentPos == null
                || !isFiniteAndSafe(currentPos.x)
                || !isFiniteAndSafe(currentPos.y)
                || !isFiniteAndSafe(currentPos.z)
                || currentPos.distanceSquared(targetCenter.x, targetCenter.y, targetCenter.z) > (64.0D * 64.0D);
        if (!needsCorrection) {
            return;
        }

        Quaterniond orientation = readSubLevelOrientation(subLevel);
        if (orientation == null) {
            orientation = new Quaterniond();
        }

        teleportSubLevel(subLevel,
                new Vector3d(targetCenter.x, targetCenter.y, targetCenter.z),
                orientation);
    }

    // Resolve the shaft frame orientation
    private Quaterniond resolveShaftFrameOrientation() {
        if (attachedShaftPos == null || attachedShaftDirection == null || attachedCarriageFacing == null) {
            return null;
        }

        PhysicsGantryShaftBlockEntity shaftBE = findAttachedShaftEntity(attachedShaftPos, attachedShaftSubLevelId);
        if (shaftBE == null) {
            return null;
        }

        Vec3 shaftLocal = Vec3.atLowerCornerOf(attachedShaftDirection.getNormal());
        Vec3 carriageLocal = Vec3.atLowerCornerOf(attachedCarriageFacing.getNormal());
        Vec3 shaftWorld = SimulatedHelper.toContainingWorldDirection(shaftBE, shaftLocal);
        Vec3 carriageWorld = SimulatedHelper.toContainingWorldDirection(shaftBE, carriageLocal);
        if (shaftWorld == null || carriageWorld == null
                || shaftWorld.lengthSqr() < 1.0E-6D || carriageWorld.lengthSqr() < 1.0E-6D) {
            return null;
        }

        Vector3d forward = new Vector3d(shaftWorld.x, shaftWorld.y, shaftWorld.z).normalize();
        Vector3d upHint = new Vector3d(carriageWorld.x, carriageWorld.y, carriageWorld.z).normalize();
        if (!Double.isFinite(forward.x) || !Double.isFinite(forward.y) || !Double.isFinite(forward.z)
                || !Double.isFinite(upHint.x) || !Double.isFinite(upHint.y) || !Double.isFinite(upHint.z)
                || Math.abs(forward.dot(upHint)) > 0.999D) {
            return null;
        }

        Vector3d right = new Vector3d(forward).cross(upHint).normalize();
        if (right.lengthSquared() < 1.0E-12D) {
            return null;
        }
        Vector3d up = new Vector3d(right).cross(forward).normalize();

        Matrix3d basis = new Matrix3d().identity();
        basis.setColumn(0, forward);
        basis.setColumn(1, up);
        basis.setColumn(2, right);

        Quaterniond orientation = new Quaterniond().setFromNormalized(basis).normalize();
        if (!Double.isFinite(orientation.x)
                || !Double.isFinite(orientation.y)
                || !Double.isFinite(orientation.z)
                || !Double.isFinite(orientation.w)) {
            return null;
        }
        return orientation;
    }

    // Resolve the fixed carriage orientation in the shaft parent's local frame.
    // Keeping this local is essential: a parent sublevel transform may change every
    // physics step, but that must not change an already configured joint frame.
    private Quaterniond resolveAttachmentRelativeOrientation() {
        if (attachedCarriageFacing == null || attachedShaftDirection == null) {
            return null;
        }

        BlockState carriageState = getBlockState();
        if (!carriageState.hasProperty(PhysicsGantryCarriageBlock.FACING)) {
            return null;
        }

        Direction carriageLocalFacing = carriageState.getValue(PhysicsGantryCarriageBlock.FACING);
        Direction.Axis carriageLocalShaftAxis = PhysicsGantryCarriageBlock.getValidGantryShaftAxis(carriageState);
        return basisOrientation(directionVector(attachedCarriageFacing), directionVector(attachedShaftDirection))
                .mul(basisOrientation(directionVector(carriageLocalFacing), axisVector(carriageLocalShaftAxis)).conjugate())
                .normalize();
    }

    // Resolve the only valid world orientation for initial placement and collision checks.
    private Quaterniond resolveAttachmentWorldOrientation(PhysicsGantryShaftBlockEntity shaft,
                                                          Object fallbackSubLevel) {
        Quaterniond relativeOrientation = resolveAttachmentRelativeOrientation();
        if (shaft == null || relativeOrientation == null) {
            return resolveLockedOrientation(fallbackSubLevel);
        }

        Object containingShaft = SimulatedHelper.getContainingSubLevel(shaft);
        Quaterniond parentOrientation = readSubLevelOrientation(containingShaft);
        if (parentOrientation == null) {
            parentOrientation = new Quaterniond();
        }
        return parentOrientation.mul(relativeOrientation).normalize();
    }

    // Resolve the locked orientation
    private Quaterniond resolveLockedOrientation(Object subLevel) {
        if (!hasLockedSubLevelOrientation) {
            Quaterniond current = readSubLevelOrientation(subLevel);
            if (current != null
                    && Double.isFinite(current.x)
                    && Double.isFinite(current.y)
                    && Double.isFinite(current.z)
                    && Double.isFinite(current.w)) {
                lockedSubLevelOrientation.set(current);
            } else {
                lockedSubLevelOrientation.identity();
            }
            hasLockedSubLevelOrientation = true;
        }

        if (!hasLockedShaftFrameOrientation) {
            Quaterniond initialFrame = resolveShaftFrameOrientation();
            if (initialFrame != null) {
                lockedShaftFrameOrientation.set(initialFrame);
                hasLockedShaftFrameOrientation = true;
            }
        }

        if (hasLockedShaftFrameOrientation) {
            Quaterniond currentFrame = resolveShaftFrameOrientation();
            if (currentFrame != null) {
                Quaterniond delta = new Quaterniond(currentFrame)
                        .mul(new Quaterniond(lockedShaftFrameOrientation).conjugate())
                        .normalize();
                return delta.mul(new Quaterniond(lockedSubLevelOrientation)).normalize();
            }
        }

        return new Quaterniond(lockedSubLevelOrientation);
    }

    // Calculate the locked attachment target pos
    private Vector3d computeLockedAttachmentTargetPos(Object subLevel, Vec3 worldAnchor, Quaterniond orientation) {
        if (!hasLockedLocalAttachmentAnchor) {
            Vec3 initialLocal = toSubLevelLocalAnchor(subLevel, worldAnchor);
            if (initialLocal != null
                    && Double.isFinite(initialLocal.x)
                    && Double.isFinite(initialLocal.y)
                    && Double.isFinite(initialLocal.z)) {
                lockedLocalAttachmentAnchor.set(initialLocal.x, initialLocal.y, initialLocal.z);
            } else {
                lockedLocalAttachmentAnchor.set(
                        attachedCarriageFacing == null ? 0.0 : attachedCarriageFacing.getStepX() * 0.5D,
                        attachedCarriageFacing == null ? 0.0 : attachedCarriageFacing.getStepY() * 0.5D,
                        attachedCarriageFacing == null ? 0.0 : attachedCarriageFacing.getStepZ() * 0.5D);
            }
            hasLockedLocalAttachmentAnchor = true;

            Vector3d initialRotPoint = readSubLevelRotationPoint(subLevel);
            if (initialRotPoint != null
                    && Double.isFinite(initialRotPoint.x)
                    && Double.isFinite(initialRotPoint.y)
                    && Double.isFinite(initialRotPoint.z)) {
                lockedRotationPoint.set(initialRotPoint);
                hasLockedRotationPoint = true;
            }
        }

        Vector3d effectiveLocalAnchor = new Vector3d(lockedLocalAttachmentAnchor);
        if (hasLockedRotationPoint) {
            Vector3d currentRotPoint = readSubLevelRotationPoint(subLevel);
            if (currentRotPoint != null
                    && Double.isFinite(currentRotPoint.x)
                    && Double.isFinite(currentRotPoint.y)
                    && Double.isFinite(currentRotPoint.z)) {
                effectiveLocalAnchor.add(
                        lockedRotationPoint.x - currentRotPoint.x,
                        lockedRotationPoint.y - currentRotPoint.y,
                        lockedRotationPoint.z - currentRotPoint.z);
            }
        }

        Vector3d rotatedLocal = new Vector3d(effectiveLocalAnchor);
        new Quaterniond(orientation).transform(rotatedLocal);

        return new Vector3d(
                worldAnchor.x - rotatedLocal.x,
                worldAnchor.y - rotatedLocal.y,
                worldAnchor.z - rotatedLocal.z);
    }

    // Reset the sublevel velocity
    private void resetSubLevelVelocity(Object subLevel) {
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || level == null) {
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics != null) {
            physics.getPipeline().resetVelocity(serverSubLevel);
        }
    }

    // Check if this is finite and safe
    private boolean isFiniteAndSafe(double val) {
        return Double.isFinite(val) && Math.abs(val) <= 30000000.0D;
    }

    // Extract the sublevel id
    private UUID extractSubLevelId(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel ? sableSubLevel.getUniqueId() : null;
    }

    // Resolve the attached sublevel
    private Object resolveAttachedSubLevel() {
        if (level == null) {
            return null;
        }

        if (!hasTrackedAttachmentState()) {
            return null;
        }

        Level lookup = resolveLookupLevel(level);
        if (lookup == null) {
            return null;
        }

        if (attachedSubLevelId != null) {
            Object byId = SubLevelBlockEntityCollector.getSubLevel(lookup, attachedSubLevelId);
            if (byId != null) {
                return byId;
            }
        }

        Object containing = getSableContaining();
        if (containing != null) {
            if (attachedSubLevelId == null) {
                attachedSubLevelId = extractSubLevelId(containing);
            }
            return containing;
        }

        if (attachedSubLevelId == null) {
            return null;
        }

        try {
            SubLevelContainer container = SubLevelContainer.getContainer(lookup);
            if (container == null) {
                return null;
            }
            return container.getSubLevel(attachedSubLevelId);
        } catch (Exception e) {
            CT_LOGGER.warn("[CT][PhysicsGantry] sublevel resolve failed at {}: {}", worldPosition, e.toString());
            return null;
        }
    }

    // Remove the physics gantry carriage
    @Override
    public void remove() {
        clearShaftConstraint();
        clearDebugAnchorEntity();
        clearPendingManualRelink();
        super.remove();
    }

    // Write the physics gantry carriage
    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        AssemblyException.write(compound, registries, lastException);
        BlockPos savedShaftPos = attachedShaftPos;
        UUID savedPayloadId = attachedSubLevelId;
        UUID savedShaftId = attachedShaftSubLevelId;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null) {
            if (savedPayloadId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(savedPayloadId);
                savedPayloadId = mapping == null ? null : mapping.newUUID();
            }
            if (savedShaftId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(savedShaftId);
                if (mapping == null) {
                    savedShaftId = null;
                    savedShaftPos = null;
                } else {
                    savedShaftId = mapping.newUUID();
                    savedShaftPos = savedShaftPos == null ? null : mapping.transform().apply(savedShaftPos);
                }
            } else if (savedShaftPos != null) {
                if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
                    savedShaftPos = ctx.getBoundingBox().contains(
                            savedShaftPos.getX(), savedShaftPos.getY(), savedShaftPos.getZ())
                            ? ctx.getPlaceTransform().apply(savedShaftPos)
                            : null;
                } else {
                    savedShaftPos = ctx.getSetupTransform().apply(savedShaftPos);
                }
            }
        }
        compound.putBoolean("AssembledToSubLevel", assembledToSubLevel && savedPayloadId != null);
        if (savedShaftPos != null) {
            compound.putLong("AttachedShaftPos", savedShaftPos.asLong());
        }
        if (attachedShaftDirection != null) {
            compound.putInt("AttachedShaftDirection", attachedShaftDirection.get3DDataValue());
        }
        if (attachedCarriageFacing != null) {
            compound.putInt("AttachedCarriageFacing", attachedCarriageFacing.get3DDataValue());
        }
        if (savedPayloadId != null) {
            compound.putUUID("AttachedSubLevelId", savedPayloadId);
        }
        if (savedShaftId != null) {
            compound.putUUID("AttachedShaftSubLevelId", savedShaftId);
        }
        compound.putDouble("AttachedShaftProgress", attachedShaftProgress);
        if (sequencedMovementLimit >= 0.0D) {
            compound.putDouble("SequencedMovementLimit", sequencedMovementLimit);
        }
        super.write(compound, registries, clientPacket);
    }

    // Read the physics gantry carriage
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        lastException = AssemblyException.read(compound, registries);
        assembledToSubLevel = compound.getBoolean("AssembledToSubLevel");
        attachedShaftPos = compound.contains("AttachedShaftPos")
            ? BlockPos.of(compound.getLong("AttachedShaftPos"))
            : null;
        attachedShaftDirection = compound.contains("AttachedShaftDirection")
            ? Direction.from3DDataValue(compound.getInt("AttachedShaftDirection"))
            : null;
        attachedCarriageFacing = compound.contains("AttachedCarriageFacing")
            ? Direction.from3DDataValue(compound.getInt("AttachedCarriageFacing"))
            : null;
        attachedSubLevelId = compound.contains("AttachedSubLevelId")
            ? compound.getUUID("AttachedSubLevelId")
            : null;
        attachedShaftSubLevelId = compound.contains("AttachedShaftSubLevelId")
            ? compound.getUUID("AttachedShaftSubLevelId")
            : null;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null) {
            if (attachedSubLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(attachedSubLevelId);
                attachedSubLevelId = mapping == null ? null : mapping.newUUID();
            }
            if (attachedShaftSubLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(attachedShaftSubLevelId);
                if (mapping == null) {
                    attachedShaftSubLevelId = null;
                    attachedShaftPos = null;
                } else {
                    attachedShaftSubLevelId = mapping.newUUID();
                    attachedShaftPos = attachedShaftPos == null ? null : mapping.transform().apply(attachedShaftPos);
                }
            } else if (attachedShaftPos != null
                    && ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
                attachedShaftPos = ctx.getPlaceTransform().apply(attachedShaftPos);
            }
        }
        assembledToSubLevel &= attachedSubLevelId != null;
        attachedShaftProgress = compound.getDouble("AttachedShaftProgress");
        invalidateShaftSpanCache();
        sequencedMovementLimit = compound.contains("SequencedMovementLimit")
                ? Math.max(0.0D, compound.getDouble("SequencedMovementLimit"))
                : -1.0D;
        restoreSequencedMovementLimit = sequencedMovementLimit >= 0.0D;
        trackedSequenceContext = null;
        super.read(compound, registries, clientPacket);
    }

    // Get the connection dependencies
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null) return List.of();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        Set<SubLevel> dependencies = new LinkedHashSet<>();
        if (attachedSubLevelId != null) {
            SubLevel subLevel = container.getSubLevel(attachedSubLevelId);
            if (subLevel != null && !subLevel.isRemoved()) dependencies.add(subLevel);
        }
        if (attachedShaftSubLevelId != null) {
            SubLevel subLevel = container.getSubLevel(attachedShaftSubLevelId);
            if (subLevel != null && !subLevel.isRemoved()) dependencies.add(subLevel);
        }
        return dependencies;
    }

    // Add the propagation locations
    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        neighbours.add(worldPosition.relative(getMountedPayloadDirection(state)));
        return super.addPropagationLocations(block, state, neighbours);
    }

    // Check if this is a custom connection
    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return other.getBlockPos().equals(worldPosition.relative(getMountedPayloadDirection(state)));
    }

    // Propagate the rotation
    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {

        BlockPos mountedPos = worldPosition.relative(getMountedPayloadDirection(stateFrom));
        if (target.getBlockPos().equals(mountedPos)) {
            return 1.0f;
        }

        if (!hasActiveAttachmentAnchor()) {
            return 0.0f;
        }

        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    // Get the gantry pinion modifier
    public static float getGantryPinionModifier(Direction shaft, Direction pinionDirection) {
        Direction.Axis shaftAxis = shaft.getAxis();
        float directionModifier = shaft.getAxisDirection().getStep();
        if (shaftAxis == Direction.Axis.Y && (pinionDirection == Direction.NORTH || pinionDirection == Direction.EAST)) {
            return -directionModifier;
        }
        if (shaftAxis == Direction.Axis.X && (pinionDirection == Direction.DOWN || pinionDirection == Direction.SOUTH)) {
            return -directionModifier;
        }
        if (shaftAxis == Direction.Axis.Z && (pinionDirection == Direction.UP || pinionDirection == Direction.WEST)) {
            return -directionModifier;
        }
        return directionModifier;
    }

    // Check if this should assemble
    private boolean shouldAssemble() {
        BlockState blockState = getBlockState();
        if (!(blockState.getBlock() instanceof PhysicsGantryCarriageBlock)) {
            return false;
        }

        Direction facing = blockState.getValue(PhysicsGantryCarriageBlock.FACING).getOpposite();
        BlockState shaftState = level.getBlockState(worldPosition.relative(facing));
        if (!(shaftState.getBlock() instanceof PhysicsGantryShaftBlock)) {
            return false;
        }

        return true;
    }
}
