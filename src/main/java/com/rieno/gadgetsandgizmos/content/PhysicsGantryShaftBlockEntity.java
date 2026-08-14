package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelConnectionApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.probe.ConnectedBlockEntityProvider;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

// Keep a physics gantry shaft linked to its carriage across root and Sable levels
public class PhysicsGantryShaftBlockEntity extends KineticBlockEntity
        implements BlockEntitySubLevelActor, ConnectedBlockEntityProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double SEQUENCE_EPSILON = 1.0E-6D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Previous level ref
    private Level previousLevelRef;
    // Previous shaft pos
    private BlockPos previousShaftPos;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry shaft
    public PhysicsGantryShaftBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.PHYSICS_GANTRY_SHAFT.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Sync the sequence context
    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the physics gantry shaft
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState shaftState = getBlockState();
        if (shaftState.getBlock() == CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                && shaftState.getValue(PhysicsGantryShaftBlock.POWERED)) {
            level.setBlock(worldPosition, shaftState.setValue(PhysicsGantryShaftBlock.POWERED, false), 2);
            return;
        }

        if (previousLevelRef != null && previousShaftPos != null
                && (previousLevelRef != level || !previousShaftPos.equals(worldPosition))) {
            moveAdjacentDisassembledCarriagesAcrossLevels(previousLevelRef, previousShaftPos, level, worldPosition);
        }

        previousLevelRef = level;
        previousShaftPos = worldPosition.immutable();

        pullAdjacentDisassembledCarriagesIntoCurrentLevel();
    }
    // Validate the attached carriage blocks
    public void checkAttachedCarriageBlocks() {
        if (!canAssembleOn()) {
            return;
        }

        refreshCarriageAnchorLookup();
    }

    // Refresh the carriage anchor lookup
    public void refreshCarriageAnchorLookup() {

        for (Direction d : Iterate.directions) {
            if (d.getAxis() == getBlockState().getValue(PhysicsGantryShaftBlock.FACING).getAxis()) {
                continue;
            }

            BlockPos offset = worldPosition.relative(d);
            BlockState pinionState = level.getBlockState(offset);
            if (pinionState.getBlock() != CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                    || pinionState.getValue(PhysicsGantryCarriageBlock.FACING) != d) {
                continue;
            }

            PhysicsGantryCarriageBlockEntity carriageBlockEntity = null;
            BlockEntity blockEntity = level.getBlockEntity(offset);
            if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage) {
                carriageBlockEntity = carriage;
            } else {
                carriageBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, offset, PhysicsGantryCarriageBlockEntity.class);
            }

            if (carriageBlockEntity != null && carriageBlockEntity.hasActiveAttachmentAnchor()) {
                carriageBlockEntity.checkValidGantryShaft();
            }
        }
    }

    // Check if this has active attached carriage
    public boolean hasActiveAttachedCarriage() {
        return !getConnectedCarriages().isEmpty();
    }

    // Get every carriage connected to this shaft
    public List<PhysicsGantryCarriageBlockEntity> getConnectedCarriages() {
        if (level == null || getBlockState().getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return List.of();
        }

        List<PhysicsGantryCarriageBlockEntity> carriages = new ArrayList<>();
        Set<PhysicsGantryCarriageBlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Direction.Axis shaftAxis = getBlockState().getValue(PhysicsGantryShaftBlock.FACING).getAxis();
        for (Direction dir : Iterate.directions) {
            if (dir.getAxis() == shaftAxis) {
                continue;
            }

            BlockPos carriagePos = worldPosition.relative(dir);
            BlockEntity local = level.getBlockEntity(carriagePos);
            PhysicsGantryCarriageBlockEntity carriage = local instanceof PhysicsGantryCarriageBlockEntity found
                    ? found
                    : SimulatedHelper.findBlockEntityIncludingSubLevels(
                            level, carriagePos, PhysicsGantryCarriageBlockEntity.class);
            if (carriage == null || carriage.isRemoved()) {
                continue;
            }

            BlockState carriageState = carriage.getBlockState();
            if (carriageState.getBlock() == CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                    && carriageState.getValue(PhysicsGantryCarriageBlock.FACING) == dir
                    && (carriage.hasActiveAttachmentAnchor()
                    || carriage.isSubLevelAssembled()
                    || carriage.getAttachedShaftPos() != null)) {
                addConnectedCarriage(carriages, seen, carriage);
            }
        }

        Direction shaftDirection = getBlockState().getValue(PhysicsGantryShaftBlock.FACING);
        for (SubLevel subLevel : SableLevelApi.subLevels(level)) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (blockEntity instanceof PhysicsGantryCarriageBlockEntity carriage
                        && carriage.isAttachedToShaftBlock(worldPosition, shaftDirection)) {
                    addConnectedCarriage(carriages, seen, carriage);
                }
            }
        }
        return List.copyOf(carriages);
    }

    // Get the connected block entities
    @Override
    public List<? extends BlockEntity> connectedBlockEntities() {
        return getConnectedCarriages();
    }

    // Return sublevels joined through connected carriages
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        return SubLevelConnectionApi.connectedTo(getConnectedCarriages());
    }

    // Add one connected carriage once
    private static void addConnectedCarriage(List<PhysicsGantryCarriageBlockEntity> carriages,
                                             Set<PhysicsGantryCarriageBlockEntity> seen,
                                             PhysicsGantryCarriageBlockEntity carriage) {
        if (!carriage.isRemoved() && seen.add(carriage)) {
            carriages.add(carriage);
        }
    }

    // Handle the speed changed event
    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        checkAttachedCarriageBlocks();
    }

    // Propagate the rotation
    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff,
                                     boolean connectedViaAxes, boolean connectedViaCogs) {
        return super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    // Check if this is a custom connection
    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        if (otherState.getBlock() != CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()) {
            return false;
        }

        PhysicsGantryCarriageBlockEntity carriageBlockEntity = null;
        if (other instanceof PhysicsGantryCarriageBlockEntity carriage) {
            carriageBlockEntity = carriage;
        } else {
            carriageBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, other.getBlockPos(), PhysicsGantryCarriageBlockEntity.class);
        }

        if (carriageBlockEntity == null || !carriageBlockEntity.hasActiveAttachmentAnchor()) {
            return false;
        }

        BlockPos diff = other.getBlockPos().subtract((Vec3i) worldPosition);
        Direction dir = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        return otherState.getValue(PhysicsGantryCarriageBlock.FACING) == dir;
    }

    // Check if this can assemble on the target
    public boolean canAssembleOn() {
        BlockState blockState = getBlockState();
        if (blockState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return false;
        }
        float speed = getPinionMovementSpeed();
        return switch (blockState.getValue(PhysicsGantryShaftBlock.PART)) {
            case END -> speed < 0.0f;
            case MIDDLE -> speed != 0.0f;
            case START -> speed > 0.0f;
            case SINGLE -> false;
        };
    }

    // Get the pinion movement speed
    public float getPinionMovementSpeed() {
        BlockState blockState = getBlockState();
        if (blockState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return 0.0f;
        }
        return Mth.clamp(convertToLinear(-getSpeed()), -0.49f, 0.49f);
    }

    // Get the sequence context for gantry
    SequencedGearshiftBlockEntity.SequenceContext getSequenceContextForGantry() {
        return sequenceContext;
    }

    // Clamp the sequence movement
    static double clampSequenceMovement(double movement, double remainingDistance) {
        if (remainingDistance < 0.0D) {
            return movement;
        }
        return Mth.clamp(movement, -remainingDistance, remainingDistance);
    }

    // Consume the sequence distance
    static double consumeSequenceDistance(double remainingDistance, double appliedMovement) {
        if (remainingDistance < 0.0D) {
            return remainingDistance;
        }
        double remaining = Math.max(0.0D, remainingDistance - Math.abs(appliedMovement));
        return remaining <= SEQUENCE_EPSILON ? 0.0D : remaining;
    }

    // Check if this is noisy
    @Override
    protected boolean isNoisy() {
        return false;
    }

    // Pull the adjacent disassembled carriages into current level
    private void pullAdjacentDisassembledCarriagesIntoCurrentLevel() {
        BlockState shaftState = getBlockState();
        if (shaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return;
        }

        Direction shaftFacing = shaftState.getValue(PhysicsGantryShaftBlock.FACING);
        for (Direction d : Iterate.directions) {
            if (d.getAxis() == shaftFacing.getAxis()) {
                continue;
            }

            BlockPos carriagePos = worldPosition.relative(d);

            BlockEntity localBe = level.getBlockEntity(carriagePos);
            if (localBe instanceof PhysicsGantryCarriageBlockEntity) {
                continue;
            }

            PhysicsGantryCarriageBlockEntity carriage =
                    SimulatedHelper.findBlockEntityIncludingSubLevels(level, carriagePos, PhysicsGantryCarriageBlockEntity.class);
            if (carriage == null || carriage.isRemoved() || carriage.isAssembledToSubLevel()) {
                continue;
            }

            Level sourceLevel = carriage.getLevel();
            if (sourceLevel == null || sourceLevel == level) {
                continue;
            }

            BlockState sourceState = sourceLevel.getBlockState(carriagePos);
            if (sourceState.getBlock() != CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                    || sourceState.getValue(PhysicsGantryCarriageBlock.FACING) != d) {
                continue;
            }

            BlockState targetState = level.getBlockState(carriagePos);
            if (!targetState.isAir()) {
                continue;
            }

            sourceLevel.removeBlock(carriagePos, false);
            level.setBlock(carriagePos, sourceState, 3);
        }
    }

    // Move the adjacent disassembled carriages across levels
    private void moveAdjacentDisassembledCarriagesAcrossLevels(Level sourceLevel, BlockPos sourceShaftPos,
                                                               Level targetLevel, BlockPos targetShaftPos) {
        if (sourceLevel == null || targetLevel == null || sourceShaftPos == null || targetShaftPos == null) {
            return;
        }

        BlockState sourceShaftState = sourceLevel.getBlockState(sourceShaftPos);
        BlockState targetShaftState = targetLevel.getBlockState(targetShaftPos);
        if (sourceShaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()
                || targetShaftState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            return;
        }

        Direction sourceFacing = sourceShaftState.getValue(PhysicsGantryShaftBlock.FACING);
        Direction targetFacing = targetShaftState.getValue(PhysicsGantryShaftBlock.FACING);
        if (sourceFacing.getAxis() != targetFacing.getAxis()) {
            return;
        }

        for (Direction d : Iterate.directions) {
            if (d.getAxis() == sourceFacing.getAxis()) {
                continue;
            }

            BlockPos sourceCarriagePos = sourceShaftPos.relative(d);
            BlockPos targetCarriagePos = targetShaftPos.relative(d);

            BlockState sourceCarriageState = sourceLevel.getBlockState(sourceCarriagePos);
            if (sourceCarriageState.getBlock() != CTBlocks.PHYSICS_GANTRY_CARRIAGE.get()
                    || sourceCarriageState.getValue(PhysicsGantryCarriageBlock.FACING) != d) {
                continue;
            }

            BlockEntity sourceCarriageBe = sourceLevel.getBlockEntity(sourceCarriagePos);
            if (!(sourceCarriageBe instanceof PhysicsGantryCarriageBlockEntity sourceCarriage)
                    || sourceCarriage.isAssembledToSubLevel()) {
                continue;
            }

            if (!targetLevel.getBlockState(targetCarriagePos).isAir()) {
                continue;
            }

            sourceLevel.removeBlock(sourceCarriagePos, false);
            targetLevel.setBlock(targetCarriagePos, sourceCarriageState, 3);
        }
    }
}
