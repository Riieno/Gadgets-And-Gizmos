package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.lib.control.OrientationTarget;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticGraphHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.HeldAngleKineticGraph;
import com.rieno.gadgetsandgizmos.lib.kinetics.ServoMotionController;
import com.rieno.gadgetsandgizmos.lib.kinetics.ServoMotionController.ServoMotionConfig;
import com.rieno.gadgetsandgizmos.lib.kinetics.SingleFaceRotationConfiguration;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticBlockEntity;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticPos;
import com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticProvider;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

// Run the gearbox's two kinetic lanes and its four independently controlled servo outputs
public class BiDirectionalGearboxBlockEntity extends SplitShaftBlockEntity implements ExtraKinetics, VirtualKineticProvider, OrientationTarget, com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float SERVO_ACTIVE_STRESS_CAPACITY = 1024.0f;
    private static final float SERVO_MAX_RPM = 32.0f;
    private static final float SERVO_RPM_PER_DEGREE_ERROR = 0.35f;
    private static final float SERVO_DEGREES_PER_TICK_PER_RPM = 0.3f;
    private static final float SERVO_ANGLE_TOLERANCE = 0.5f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // East west lane
    private final EastWestLaneBlockEntity eastWestLane;
    // Tracked servo controllers
    private final List<ServoFaceController> servoControllers;
    // Tracked virtual servo sources
    private final List<KineticBlockEntity> virtualServoSources;
    // Virtual source type
    private final BlockEntityType<?> virtualSourceType;

    // Current operation mode behaviour
    private ScrollOptionBehaviour<OperationMode> operationModeBehaviour;
    // Current operation mode
    private OperationMode operationMode = OperationMode.AUTO;
    // Current north south lane mode
    private LaneMode northSouthLaneMode = LaneMode.STRAIGHT;
    // Current east west lane mode
    private LaneMode eastWestLaneMode = LaneMode.STRAIGHT;
    // Tracks whether angle control is active
    private boolean angleControlActive;
    // Tracks whether gyro source is present
    private boolean gyroSourcePresent;
    // Tracks whether reverse mode is set
    private boolean reverseMode;
    // Queued lane kinetics refresh tick count
    private int queuedLaneKineticsRefreshTicks;

    // Output north
    private int outputNorth;
    // Output south
    private int outputSouth;
    // Output east
    private int outputEast;
    // Output west
    private int outputWest;
    // Controls whether to invert north
    private boolean invertNorth;
    // Controls whether to invert south
    private boolean invertSouth;
    // Controls whether to invert east
    private boolean invertEast;
    // Controls whether to invert west
    private boolean invertWest;
    // Current angle north
    private double angleNorth;
    // Current angle south
    private double angleSouth;
    // Current angle east
    private double angleEast;
    // Current angle west
    private double angleWest;
    // Current manual angle north
    private double manualAngleNorth = Double.NaN;
    // Current manual angle south
    private double manualAngleSouth = Double.NaN;
    // Current manual angle east
    private double manualAngleEast = Double.NaN;
    // Current manual angle west
    private double manualAngleWest = Double.NaN;
    // Maximum servo angle north
    private double maxServoAngleNorth = Double.NaN;
    // Maximum servo angle south
    private double maxServoAngleSouth = Double.NaN;
    // Maximum servo angle east
    private double maxServoAngleEast = Double.NaN;
    // Maximum servo angle west
    private double maxServoAngleWest = Double.NaN;
    // Current rotation sync token
    private int rotationSyncToken;
    // Last orientation payload
    private OrientationPayload lastOrientationPayload;
    // Last orientation payload tick
    private long lastOrientationPayloadTick = Long.MIN_VALUE;
    // Last gyroscope link source
    private GyroscopeLinkBlockEntity lastGyroscopeLinkSource;
    // Last gyroscope link source tick
    private long lastGyroscopeLinkSourceTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearbox
    public BiDirectionalGearboxBlockEntity(BlockPos pos, BlockState blockState) {
        this(CTBlockEntities.BIDIRECTIONAL_GEARBOX.get(), pos, blockState);
    }

    // Initialize the bi directional gearbox
    protected BiDirectionalGearboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.virtualSourceType = type;
        eastWestLane = new EastWestLaneBlockEntity(type,
                new ExtraBlockPos((Vec3i) pos), blockState, this);
        List<ServoFaceController> controllers = new ArrayList<>();
        List<KineticBlockEntity> sources = new ArrayList<>();
        for (Direction face : Direction.Plane.HORIZONTAL) {
            ServoFaceController controller = new ServoFaceController(face, controllers.size());
            controllers.add(controller);
            sources.add(controller.getGeneratedSource());
        }
        servoControllers = List.copyOf(controllers);
        virtualServoSources = List.copyOf(sources);
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
        if (!shouldAddOperationModeScrollBehaviour()) {
            return;
        }
        ValueBoxTransform topPanel = new CenteredSideValueBoxTransform((state, dir) -> {
            if (!(state.getBlock() instanceof BiDirectionalGearboxBlock)) return dir == Direction.UP;
            Direction.Axis primaryAxis = state.getValue(BiDirectionalGearboxBlock.AXIS) == Direction.Axis.Y
                    ? Direction.Axis.Z : Direction.Axis.Y;
            Direction.Axis secondaryAxis = switch (state.getValue(BiDirectionalGearboxBlock.AXIS)) {
                case Y -> Direction.Axis.X;
                case X -> Direction.Axis.Z;
                case Z -> Direction.Axis.X;
            };

            return dir.getAxis() != primaryAxis && dir.getAxis() != secondaryAxis
                    && dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        });
        operationModeBehaviour = new ScrollOptionBehaviour<>(OperationMode.class,
                Component.translatable("createthrusters.gearbox.mode"), (SmartBlockEntity) this, topPanel);
        operationModeBehaviour.withCallback(val -> setOperationMode(OperationMode.values()[val], false));
        behaviours.add(operationModeBehaviour);
    }

    // Check if this should add operation mode scroll behaviour
    protected boolean shouldAddOperationModeScrollBehaviour() {
        return true;
    }

    // Check if this should use redstone reverse
    protected boolean shouldUseRedstoneReverse() {
        return CTConfigs.COMMON.gearboxReverseWithRedstone.get();
    }

    // Check if this is a passthrough split mode
    public boolean isPassthroughSplitMode() {
        return operationMode == OperationMode.PASSTHROUGH_SPLIT;
    }

    // Check if this uses passthrough split kinetics
    private boolean usesPassthroughSplitKinetics() {
        return isPassthroughSplitMode() && !angleControlActive;
    }

    // Initialize the bi directional gearbox
    @Override
    public void initialize() {
        if (level != null && !level.isClientSide && angleControlActive) {
            scrubLoadedServoKinetics();
        }
        super.initialize();
        if (level != null && !level.isClientSide && !angleControlActive) {
            queueLaneKineticsRefresh();
            for (Direction dir : Direction.values()) {
                BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(dir));
                if (neighbour instanceof BiDirectionalGearboxBlockEntity gearbox) {
                    gearbox.queueLaneKineticsRefresh();
                }
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the bi directional gearbox
    @Override
    public void tick() {
        super.tick();
        eastWestLane.tick();
        for (ServoFaceController controller : servoControllers) {
            controller.tickGeneratedSource();
        }
        if (level == null || level.isClientSide) {
            return;
        }
        tickQueuedLaneKineticsRefresh();

        GyroscopeLinkBlockEntity sourceLink = resolveSourceLink(level, worldPosition);
        double[] sourceAngles = sourceLink != null
                ? sourceLink.getLinkedAnglesRadians()
                : resolveSourceAngles(level, worldPosition);
        boolean hadSource = gyroSourcePresent;
        gyroSourcePresent = sourceAngles != null;
        reverseMode = shouldUseRedstoneReverse() && level.hasNeighborSignal(worldPosition);

        boolean shouldAngleControl = operationMode.shouldUseServoOutputs(gyroSourcePresent, hasAnyManualAngle());
        setAngleControlActive(shouldAngleControl);

        if (angleControlActive) {
            updateServoOutputs(level, worldPosition, sourceAngles, sourceLink);
        } else {
            clearAngleOutputs();
        }

        if (hadSource != gyroSourcePresent || level.getGameTime() % 5 == 0) {
            sendData();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Scrub the loaded servo kinetics
    private void scrubLoadedServoKinetics() {

        detachKinetics();
        removeSource();
        eastWestLane.detachKinetics();
        eastWestLane.removeSource();
        for (ServoFaceController controller : servoControllers) {
            controller.resetLoadedKinetics();
        }
    }

    // Set the angle control active
    private void setAngleControlActive(boolean active) {
        if (active == angleControlActive) {
            return;
        }
        invalidateServoTargetCaches();
        angleControlActive = active;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BiDirectionalGearboxBlock)) {
            invalidateServoTargetCaches();
            return;
        }

        detachKinetics();
        eastWestLane.detachKinetics();
        removeSource();
        eastWestLane.removeSource();

        syncKineticModeBlockState();
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());

        if (!active) {
            for (ServoFaceController controller : servoControllers) {
                controller.stop(level);
            }
            clearHeldAngleTargets(level);
            attachKinetics();
            if (!usesPassthroughSplitKinetics()) {
                eastWestLane.attachKinetics();
            }
        }
        setChanged();
        invalidateServoTargetCaches();
    }

    // Sync the kinetic mode block state
    private void syncKineticModeBlockState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BiDirectionalGearboxBlock)) {
            return;
        }
        BlockState updated = state;
        if (updated.hasProperty(BiDirectionalGearboxBlock.GYRO_MODE)
                && updated.getValue(BiDirectionalGearboxBlock.GYRO_MODE) != angleControlActive) {
            updated = updated.setValue(BiDirectionalGearboxBlock.GYRO_MODE, angleControlActive);
        }
        boolean split = usesPassthroughSplitKinetics();
        if (updated.hasProperty(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT)
                && updated.getValue(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT) != split) {
            updated = updated.setValue(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT, split);
        }
        if (!updated.equals(state)) {
            level.setBlock(worldPosition, updated, 3);
        }
    }

    // Update the servo outputs
    private void updateServoOutputs(Level level, BlockPos pos, @Nullable double[] sourceAngles,
                                    @Nullable GyroscopeLinkBlockEntity sourceLink) {
        double xAngle = 0;
        double zAngle = 0;
        if (sourceAngles != null) {
            xAngle = sourceAngles[0];
            zAngle = sourceAngles[1];
        }

        double north = Math.toDegrees(-xAngle);
        double south = Math.toDegrees(xAngle);
        double west = Math.toDegrees(-zAngle);
        double east = Math.toDegrees(zAngle);

        if (sourceLink != null) {
            north = sourceLink.getConfiguredCardinalAngleDegrees(Direction.NORTH);
            south = sourceLink.getConfiguredCardinalAngleDegrees(Direction.SOUTH);
            west = sourceLink.getConfiguredCardinalAngleDegrees(Direction.WEST);
            east = sourceLink.getConfiguredCardinalAngleDegrees(Direction.EAST);
        }

        if (reverseMode) {
            double tmp = north;
            north = south;
            south = tmp;
            tmp = west;
            west = east;
            east = tmp;
        }

        setServoFaceAngle(mapServoFace(Direction.NORTH), north);
        setServoFaceAngle(mapServoFace(Direction.SOUTH), south);
        setServoFaceAngle(mapServoFace(Direction.WEST), west);
        setServoFaceAngle(mapServoFace(Direction.EAST), east);

        outputNorth = mapAngle(Math.toRadians(angleNorth));
        outputSouth = mapAngle(Math.toRadians(angleSouth));
        outputEast = mapAngle(Math.toRadians(angleEast));
        outputWest = mapAngle(Math.toRadians(angleWest));
        applyServoFaceControllers(level);
        level.updateNeighborsAt(pos, getBlockState().getBlock());
        sendData();
        setChanged();
    }

    // Clear the angle outputs
    private void clearAngleOutputs() {
        outputNorth = outputSouth = outputEast = outputWest = 0;
        angleNorth = angleSouth = angleEast = angleWest = 0;
        if (level != null && !level.isClientSide) {
            clearHeldAngleTargets(level);
        }
    }

    // Resolve the source angles
    @Nullable
    private double[] resolveSourceAngles(Level level, BlockPos pos) {
        if (lastOrientationPayload != null && level.getGameTime() - lastOrientationPayloadTick <= 2) {
            return new double[]{
                    lastOrientationPayload.getXAngleRadians(),
                    lastOrientationPayload.getZAngleRadians()
            };
        }

        BlockPos abovePos = pos.above();
        BlockEntity above = level.getBlockEntity(abovePos);

        if (above instanceof GyroscopeLinkBlockEntity link && link.isLinked()) {
            return link.getLinkedAnglesRadians();
        }
        if (SimulatedHelper.isGimbalSensor(above)) {
            return SimulatedHelper.getAngles(above);
        }

        BlockPos below = pos.below();
        BlockEntity belowBe = level.getBlockEntity(below);
        if (SimulatedHelper.isGimbalSensor(belowBe)) {
            return SimulatedHelper.getAngles(belowBe);
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = below.relative(dir);
            BlockEntity candidateBe = level.getBlockEntity(candidate);
            if (SimulatedHelper.isGimbalSensor(candidateBe)) {
                return SimulatedHelper.getAngles(candidateBe);
            }
        }
        return null;
    }

    // Resolve the source link
    @Nullable
    private GyroscopeLinkBlockEntity resolveSourceLink(Level level, BlockPos pos) {
        if (lastGyroscopeLinkSource != null
                && !lastGyroscopeLinkSource.isRemoved()
                && lastGyroscopeLinkSource.isLinked()
                && level.getGameTime() - lastGyroscopeLinkSourceTick <= 2
                && isAdjacent(pos, lastGyroscopeLinkSource.getBlockPos())) {
            return lastGyroscopeLinkSource;
        }

        BlockEntity above = level.getBlockEntity(pos.above());
        if (above instanceof GyroscopeLinkBlockEntity link && link.isLinked()) {
            return link;
        }
        return null;
    }

    // Check if this is adjacent
    private static boolean isAdjacent(BlockPos first, BlockPos second) {
        int distance = Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
        return distance == 1;
    }

    // Check if this can accept orientation payload
    @Override
    public boolean canAcceptOrientationPayload(OrientationPayload payload) {
        return payload != null;
    }

    // Apply the gyroscope link payload
    public void applyGyroscopeLinkPayload(GyroscopeLinkBlockEntity sourceLink, OrientationPayload payload) {
        if (sourceLink == null || payload == null || level == null || level.isClientSide) {
            return;
        }
        lastGyroscopeLinkSource = sourceLink;
        lastGyroscopeLinkSourceTick = level.getGameTime();
        applyOrientationPayload(payload);
    }

    // Apply the orientation payload
    @Override
    public void applyOrientationPayload(OrientationPayload payload) {
        if (payload == null || level == null || level.isClientSide) {
            return;
        }
        lastOrientationPayload = payload;
        lastOrientationPayloadTick = level.getGameTime();
    }

    // Map the angle
    private static int mapAngle(double angle) {
        double normalized = (angle / (Math.PI / 2.0) + 1.0) / 2.0;
        return (int) Math.round(Mth.clamp(normalized, 0.0, 1.0) * 15.0);
    }

    // Get the manual
    private double manualOr(Direction dir, double fallback) {
        double manual = getManualAngle(dir);
        return Double.isNaN(manual) ? fallback : manual;
    }

    // Set the servo face angle
    private void setServoFaceAngle(Direction face, double fallbackAngle) {
        double angle = manualOr(face, fallbackAngle);
        switch (face) {
            case NORTH -> angleNorth = angle;
            case SOUTH -> angleSouth = angle;
            case EAST -> angleEast = angle;
            case WEST -> angleWest = angle;
            default -> {
            }
        }
    }

    // Map the servo face
    private Direction mapServoFace(Direction localFace) {
        if (!localFace.getAxis().isHorizontal()) {
            return localFace;
        }

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof BiDirectionalGearboxBlock)
                || !state.hasProperty(BiDirectionalGearboxBlock.FACING)) {
            return localFace;
        }

        Direction facing = state.getValue(BiDirectionalGearboxBlock.FACING);
        if (!facing.getAxis().isHorizontal()) {
            return localFace;
        }

        int quarterTurns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        Direction mapped = localFace;
        for (int i = 0; i < quarterTurns; i++) {
            mapped = mapped.getClockWise(Direction.Axis.Y);
        }
        return mapped;
    }

    // Check if this has any manual angle
    private boolean hasAnyManualAngle() {
        return !Double.isNaN(manualAngleNorth)
                || !Double.isNaN(manualAngleSouth)
                || !Double.isNaN(manualAngleEast)
                || !Double.isNaN(manualAngleWest);
    }

    // Get the primary lane axis
    public Direction.Axis getPrimaryLaneAxis() {
        BlockState s = getBlockState();
        if (!(s.getBlock() instanceof BiDirectionalGearboxBlock)) return Direction.Axis.Z;
        return s.getValue(BiDirectionalGearboxBlock.AXIS) == Direction.Axis.Y
                ? Direction.Axis.Z : Direction.Axis.Y;
    }

    // Get the secondary lane axis
    public Direction.Axis getSecondaryLaneAxis() {
        BlockState s = getBlockState();
        if (!(s.getBlock() instanceof BiDirectionalGearboxBlock)) return Direction.Axis.X;
        return switch (s.getValue(BiDirectionalGearboxBlock.AXIS)) {
            case Y -> Direction.Axis.X;
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    // Get the secondary lane axis for state
    private static Direction.Axis secondaryLaneAxisForState(BlockState state) {
        if (!(state.getBlock() instanceof BiDirectionalGearboxBlock)) return Direction.Axis.X;
        return switch (state.getValue(BiDirectionalGearboxBlock.AXIS)) {
            case Y -> Direction.Axis.X;
            case X -> Direction.Axis.Z;
            case Z -> Direction.Axis.X;
        };
    }

    // Get the rotation speed modifier
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (usesPassthroughSplitKinetics()) {
            return getPassthroughSplitModifier(face);
        }
        return getLaneModifier(face, northSouthLaneMode, getPrimaryLaneAxis());
    }

    // Check if this is a passthrough split face
    private boolean isPassthroughSplitFace(Direction face) {
        Direction.Axis axis = face.getAxis();
        return axis == getPrimaryLaneAxis() || axis == getSecondaryLaneAxis();
    }

    // Get the passthrough split modifier
    private float getPassthroughSplitModifier(Direction face) {
        if (!isPassthroughSplitFace(face) || angleControlActive) {
            return 0.0f;
        }
        if (!hasSource()) {
            return 1.0f;
        }
        Direction sourceFacing = getSourceFacingFor(this);
        if (!isPassthroughSplitFace(sourceFacing)) {
            return 0.0f;
        }
        if (sourceFacing == face) {
            return 1.0f;
        }
        return getGearboxSplitModifier(face, sourceFacing) * getFaceOutputMultiplier(face);
    }

    // Get the gearbox split modifier
    private static float getGearboxSplitModifier(Direction face, Direction src) {
        if (face.getAxis() == src.getAxis()) {
            return face == src ? 1.0f : -1.0f;
        }
        return face.getAxisDirection() == src.getAxisDirection() ? -1.0f : 1.0f;
    }

    // Get the lane modifier
    private float getLaneModifier(Direction face, LaneMode laneMode, Direction.Axis laneAxis) {
        if (face.getAxis() != laneAxis || angleControlActive || laneMode == LaneMode.DISABLED) {
            return 0.0f;
        }
        if (!hasSource()) {
            return 1.0f;
        }
        Direction sourceFacing = getSourceFacingFor(this);
        if (sourceFacing.getAxis() != laneAxis) {
            return 0.0f;
        }
        if (sourceFacing == face) {
            return 1.0f;
        }
        return (laneMode == LaneMode.REVERSED ? -1.0f : 1.0f) * getFaceOutputMultiplier(face);
    }

    // Get the east west lane modifier
    private float getEastWestLaneModifier(Direction face) {
        return eastWestLane.getLaneModifier(face);
    }

    // Check if this has source for axis
    private static boolean hasSourceForAxis(KineticBlockEntity be, Direction.Axis axis) {
        return be.hasSource() && getSourceFacingFor(be).getAxis() == axis;
    }

    // Get the source facing
    private static Direction getSourceFacingFor(KineticBlockEntity be) {
        return ((com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity) be).getSourceFacing();
    }

    // Check if this should block internal gearshift feedback
    private boolean shouldBlockInternalGearshiftFeedback(KineticBlockEntity target) {
        return this instanceof BiDirectionalGearshiftBlockEntity
                && (target == this
                || target == eastWestLane
                || target instanceof ExtraKinetics.ExtraKineticsBlockEntity extraKinetics
                && extraKinetics.getParentBlockEntity() == this);
    }

    // Check if the target accepts the lane axis
    private static boolean targetAcceptsLaneAxis(KineticBlockEntity target, Direction.Axis laneAxis) {
        if (target instanceof ExtraKinetics.ExtraKineticsBlockEntity extraKinetics) {
            if (extraKinetics.getParentBlockEntity() instanceof BiDirectionalGearboxBlockEntity gearbox) {
                return !gearbox.usesPassthroughSplitKinetics() && gearbox.getSecondaryLaneAxis() == laneAxis;
            }
            return true;
        }
        if (target instanceof BiDirectionalGearboxBlockEntity gearbox) {
            if (gearbox.usesPassthroughSplitKinetics()) {
                return laneAxis == gearbox.getPrimaryLaneAxis() || laneAxis == gearbox.getSecondaryLaneAxis();
            }
            return gearbox.getPrimaryLaneAxis() == laneAxis;
        }
        return true;
    }

    // Propagate the rotation
    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        Direction dir = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        if (shouldBlockInternalGearshiftFeedback(target)) {
            return 0.0f;
        }
        if (usesPassthroughSplitKinetics()) {
            if (!targetAcceptsLaneAxis(target, dir.getAxis())) {
                return 0.0f;
            }
            return propagatePassthroughSplitTo(dir);
        }
        Direction.Axis primaryAxis = getPrimaryLaneAxis();
        if (dir.getAxis() != primaryAxis || angleControlActive || northSouthLaneMode == LaneMode.DISABLED) {
            return 0.0f;
        }
        if (!targetAcceptsLaneAxis(target, primaryAxis)) {
            return 0.0f;
        }
        if (hasSource() && getSourceFacing().getAxis() != primaryAxis) {
            return 0.0f;
        }
        if (hasSourceForAxis(this, primaryAxis) && getSourceFacingFor(this) == dir) {
            return 0.0f;
        }

        return getLaneModifier(dir, northSouthLaneMode, primaryAxis);
    }

    // Propagate the passthrough split
    private float propagatePassthroughSplitTo(Direction dir) {
        if (!isPassthroughSplitFace(dir)) {
            return 0.0f;
        }
        if (hasSource()) {
            Direction sourceFacing = getSourceFacingFor(this);
            if (!isPassthroughSplitFace(sourceFacing) || sourceFacing == dir) {
                return 0.0f;
            }
        }
        return getPassthroughSplitModifier(dir);
    }

    // Propagate the east west lane
    private float propagateEastWestLaneTo(KineticBlockEntity target, BlockPos diff) {
        Direction dir = Direction.getNearest(diff.getX(), diff.getY(), diff.getZ());
        Direction.Axis secondaryAxis = getSecondaryLaneAxis();
        if (shouldBlockInternalGearshiftFeedback(target)) {
            return 0.0f;
        }
        if (usesPassthroughSplitKinetics() || dir.getAxis() != secondaryAxis || angleControlActive || eastWestLaneMode == LaneMode.DISABLED) {
            return 0.0f;
        }
        if (!targetAcceptsLaneAxis(target, secondaryAxis)) {
            return 0.0f;
        }
        if (eastWestLane.hasSource() && eastWestLane.getSourceFacing().getAxis() != secondaryAxis) {
            return 0.0f;
        }
        if (eastWestLane.hasSource() && eastWestLane.getSourceFacing().getAxis() == secondaryAxis
                && eastWestLane.getSourceFacing() == dir) {
            return 0.0f;
        }

        return eastWestLane.getLaneModifier(dir);
    }

    // Check if this is a gyro mode
    public boolean isGyroMode() {
        return angleControlActive;
    }

    // Check if the angle control is active
    public boolean isAngleControlActive() {
        return angleControlActive;
    }

    // Check if the servo mode is active
    public boolean isServoModeActive() {
        return angleControlActive;
    }

    // Check if this has gyro source
    public boolean hasGyroSource() {
        return gyroSourcePresent;
    }

    // Check if this is a reverse mode
    public boolean isReverseMode() {
        return reverseMode;
    }

    // Get the operation mode
    public OperationMode getOperationMode() {
        return operationMode;
    }

    // Get the operation mode name
    public String getOperationModeName() {
        return operationMode.apiName();
    }

    // Set the operation mode
    public void setOperationMode(OperationMode mode) {
        setOperationMode(mode, true);
    }

    // Set the operation mode
    public void setOperationMode(String modeName) {
        setOperationMode(OperationMode.fromName(modeName));
    }

    // Set the operation mode
    private void setOperationMode(OperationMode mode, boolean updateBehaviour) {
        if (mode == null) {
            return;
        }
        boolean wasPassthroughSplit = usesPassthroughSplitKinetics();
        operationMode = mode;
        if (updateBehaviour && operationModeBehaviour != null && operationModeBehaviour.getValue() != mode.ordinal()) {
            operationModeBehaviour.setValue(mode.ordinal());
        }
        if (level != null && !level.isClientSide) {
            boolean shouldAngleControl = operationMode.shouldUseServoOutputs(gyroSourcePresent, hasAnyManualAngle());
            if (angleControlActive != shouldAngleControl) {
                setAngleControlActive(shouldAngleControl);
            } else if (wasPassthroughSplit != usesPassthroughSplitKinetics()) {
                rebuildLaneKinetics(this::syncKineticModeBlockState);
            } else {
                syncKineticModeBlockState();
            }
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        setChanged();
        sendData();
    }

    // Get the lane mode
    public LaneMode getLaneMode(Direction.Axis axis) {
        return axis == getSecondaryLaneAxis() ? eastWestLaneMode : northSouthLaneMode;
    }

    // Queue the lane kinetics refresh
    public void queueLaneKineticsRefresh() {
        if (level == null || level.isClientSide || preventSpeedUpdate > 0 || angleControlActive) {
            return;
        }
        if (usesPassthroughSplitKinetics()) {
            return;
        }
        warnOfMovement();
        detachKinetics();
        removeSource();
        clearKineticInformation();
        updateSpeed = true;
        eastWestLane.warnOfMovement();
        eastWestLane.detachKinetics();
        eastWestLane.removeSource();
        eastWestLane.clearKineticInformation();
        eastWestLane.updateSpeed = true;
        queuedLaneKineticsRefreshTicks = Math.max(queuedLaneKineticsRefreshTicks, 2);
    }

    // Update the queued lane kinetics refresh
    private void tickQueuedLaneKineticsRefresh() {
        if (queuedLaneKineticsRefreshTicks <= 0) {
            return;
        }
        queuedLaneKineticsRefreshTicks--;
        if (queuedLaneKineticsRefreshTicks > 0 || angleControlActive || usesPassthroughSplitKinetics()) {
            return;
        }
        rebuildLaneKinetics();
    }

    // Set the lane mode
    public void setLaneMode(Direction.Axis axis, LaneMode mode) {
        if (mode == null) {
            return;
        }
        rebuildLaneKinetics(() -> {
            if (axis == getSecondaryLaneAxis()) {
                eastWestLaneMode = mode;
            } else {
                northSouthLaneMode = mode;
            }
        });
        setChanged();
        sendData();
    }

    // Rebuild the lane kinetics
    private void rebuildLaneKinetics(Runnable stateUpdate) {
        detachKinetics();
        eastWestLane.detachKinetics();
        removeSource();
        eastWestLane.removeSource();
        stateUpdate.run();
        attachKinetics();
        if (!usesPassthroughSplitKinetics()) {
            eastWestLane.attachKinetics();
        }
    }

    // Rebuild the lane kinetics
    private void rebuildLaneKinetics() {
        rebuildLaneKinetics(() -> {
        });
    }

    // Get the output signal
    public int getOutputSignal(Direction dir) {
        return switch (dir) {
            case NORTH -> outputNorth;
            case SOUTH -> outputSouth;
            case EAST -> outputEast;
            case WEST -> outputWest;
            default -> 0;
        };
    }

    // Check if the face output is inverted
    public boolean isFaceOutputInverted(Direction dir) {
        return switch (dir) {
            case NORTH -> invertNorth;
            case SOUTH -> invertSouth;
            case EAST -> invertEast;
            case WEST -> invertWest;
            default -> false;
        };
    }

    // Toggle face output inversion
    public void toggleFaceOutputInverted(Direction dir) {
        setFaceOutputInverted(dir, !isFaceOutputInverted(dir));
    }

    // Set the face output inverted
    public void setFaceOutputInverted(Direction dir, boolean inverted) {
        boolean changed = switch (dir) {
            case NORTH -> {
                if (invertNorth == inverted) {
                    yield false;
                }
                invertNorth = inverted;
                yield true;
            }
            case SOUTH -> {
                if (invertSouth == inverted) {
                    yield false;
                }
                invertSouth = inverted;
                yield true;
            }
            case EAST -> {
                if (invertEast == inverted) {
                    yield false;
                }
                invertEast = inverted;
                yield true;
            }
            case WEST -> {
                if (invertWest == inverted) {
                    yield false;
                }
                invertWest = inverted;
                yield true;
            }
            default -> false;
        };
        if (!changed) {
            return;
        }

        if (level != null && !level.isClientSide) {
            if (angleControlActive) {
                applyServoFaceControllers(level);
            } else {
                queueFaceNeighbourKineticsRefresh(dir);
                queueLaneKineticsRefresh();
            }
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }

        setChanged();
        sendData();
    }

    // Queue the face neighbour kinetics refresh
    private void queueFaceNeighbourKineticsRefresh(Direction dir) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(dir));
        if (blockEntity instanceof BiDirectionalGearboxBlockEntity gearbox) {
            gearbox.queueLaneKineticsRefresh();
        }
    }

    // Get the face angle
    public double getFaceAngle(Direction dir) {
        return switch (dir) {
            case NORTH -> angleNorth;
            case SOUTH -> angleSouth;
            case EAST -> angleEast;
            case WEST -> angleWest;
            default -> 0.0D;
        };
    }

    // Get the face max angle
    public double getFaceMaxAngle(Direction dir) {
        double fallback = Math.max(0.0D, CTConfigs.COMMON.gearboxMaxStaticAngleDeg.get());
        double configured = switch (dir) {
            case NORTH -> maxServoAngleNorth;
            case SOUTH -> maxServoAngleSouth;
            case EAST -> maxServoAngleEast;
            case WEST -> maxServoAngleWest;
            default -> fallback;
        };
        if (Double.isNaN(configured)) {
            return fallback;
        }
        return Mth.clamp(Math.abs(configured), 0.0D, fallback);
    }

    // Set the face max angle
    public void setFaceMaxAngle(Direction dir, double maxAngle) {
        double clamped = Mth.clamp(Math.abs(maxAngle), 0.0D, Math.max(0.0D, CTConfigs.COMMON.gearboxMaxStaticAngleDeg.get()));
        switch (dir) {
            case NORTH -> maxServoAngleNorth = clamped;
            case SOUTH -> maxServoAngleSouth = clamped;
            case EAST -> maxServoAngleEast = clamped;
            case WEST -> maxServoAngleWest = clamped;
            default -> {
                return;
            }
        }
        setChanged();
        sendData();
    }

    // Clear the face max angle
    public void clearFaceMaxAngle(@Nullable Direction dir) {
        if (dir == null) {
            maxServoAngleNorth = maxServoAngleSouth = maxServoAngleEast = maxServoAngleWest = Double.NaN;
        } else {
            switch (dir) {
                case NORTH -> maxServoAngleNorth = Double.NaN;
                case SOUTH -> maxServoAngleSouth = Double.NaN;
                case EAST -> maxServoAngleEast = Double.NaN;
                case WEST -> maxServoAngleWest = Double.NaN;
                default -> {
                    return;
                }
            }
        }
        setChanged();
        sendData();
    }

    // Set the manual face angle
    public void setManualFaceAngle(Direction dir, double angle) {
        switch (dir) {
            case NORTH -> manualAngleNorth = angle;
            case SOUTH -> manualAngleSouth = angle;
            case EAST -> manualAngleEast = angle;
            case WEST -> manualAngleWest = angle;
            default -> {
                return;
            }
        }
        setOperationMode(OperationMode.SERVO);
        setChanged();
        sendData();
    }

    // Clear the manual face angle
    public void clearManualFaceAngle(@Nullable Direction dir) {
        if (dir == null) {
            manualAngleNorth = manualAngleSouth = manualAngleEast = manualAngleWest = Double.NaN;
        } else {
            switch (dir) {
                case NORTH -> manualAngleNorth = Double.NaN;
                case SOUTH -> manualAngleSouth = Double.NaN;
                case EAST -> manualAngleEast = Double.NaN;
                case WEST -> manualAngleWest = Double.NaN;
                default -> {
                    return;
                }
            }
        }
        setChanged();
        sendData();
    }

    // Get the graph readable data
    @Override
    public java.util.Map<String, String> graphReadableData() {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("operation_mode", "string");
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            String face = dir.getName();
            fields.put(face + "_angle", "number");
            fields.put(face + "_max_angle", "number");
            fields.put(face + "_output_signal", "number");
            fields.put(face + "_output_inverted", "boolean");
        }
        return fields;
    }

    // Get the graph writable data
    @Override
    public java.util.Map<String, String> graphWritableData() {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("operation_mode", "string");
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            String face = dir.getName();
            fields.put(face + "_angle", "number");
            fields.put(face + "_max_angle", "number");
            fields.put(face + "_output_inverted", "boolean");
        }
        return fields;
    }

    // Get the graph writable options
    @Override
    public java.util.Map<String, java.util.List<String>> graphWritableOptions() {
        return java.util.Map.of("operation_mode", java.util.List.of(
                "auto", "passthrough", "servo", "servo_locked", "passthrough_split"));
    }

    // Read the graph data
    @Override
    public com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value readGraphData(String field) {
        if ("operation_mode".equals(field)) {
            return com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.string(getOperationModeName());
        }
        Direction dir = graphFace(field);
        if (dir == null) return com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number(0);
        if (field.endsWith("_max_angle")) return com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number(getFaceMaxAngle(dir));
        if (field.endsWith("_output_signal")) return com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number(getOutputSignal(dir));
        if (field.endsWith("_output_inverted")) return com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.bool(isFaceOutputInverted(dir));
        return com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value.number(getFaceAngle(dir));
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Value val) {
        if ("operation_mode".equals(field)) {
            setOperationMode(val.asString());
            return true;
        }
        Direction dir = graphFace(field);
        if (dir == null) return false;
        if (field.endsWith("_max_angle")) setFaceMaxAngle(dir, val.asNumber());
        else if (field.endsWith("_output_inverted")) setFaceOutputInverted(dir, val.asBoolean());
        else if (field.endsWith("_angle")) setManualFaceAngle(dir, val.asNumber());
        else return false;
        return true;
    }

    // Get the graph face
    private static Direction graphFace(String field) {
        if (field == null) return null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (field.startsWith(dir.getName() + "_")) return dir;
        }
        return null;
    }

    // Get the manual angle
    public double getManualAngle(Direction dir) {
        return switch (dir) {
            case NORTH -> manualAngleNorth;
            case SOUTH -> manualAngleSouth;
            case EAST -> manualAngleEast;
            case WEST -> manualAngleWest;
            default -> Double.NaN;
        };
    }

    // Get the face output multiplier
    private float getFaceOutputMultiplier(Direction dir) {
        return isFaceOutputInverted(dir) ? -1.0f : 1.0f;
    }

    // Get the north south speed
    public float getNorthSouthSpeed() {
        return getSpeed();
    }

    // Get the east west speed
    public float getEastWestSpeed() {
        if (usesPassthroughSplitKinetics()) {
            return getSpeed();
        }
        return eastWestLane.getSpeed();
    }

    // Get the visual shaft speed
    public float getVisualShaftSpeed(Direction dir) {
        Direction.Axis primaryAxis = getPrimaryLaneAxis();
        Direction.Axis secondaryAxis = getSecondaryLaneAxis();
        if (dir.getAxis() != primaryAxis && dir.getAxis() != secondaryAxis) {
            return 0.0f;
        }
        if (angleControlActive) {
            return getServoGeneratedSpeed(dir);
        }
        if (usesPassthroughSplitKinetics()) {
            return getNorthSouthSpeed() * getPassthroughSplitModifier(dir);
        }
        if (dir.getAxis() == secondaryAxis) {
            return getEastWestSpeed() * eastWestLane.getLaneModifier(dir);
        }
        return getNorthSouthSpeed() * getRotationSpeedModifier(dir);
    }

    // Get the visual shaft offset
    public float getVisualShaftOffset(Direction dir) {
        Direction.Axis axis = dir.getAxis();
        Direction.Axis primaryAxis = getPrimaryLaneAxis();
        Direction.Axis secondaryAxis = getSecondaryLaneAxis();
        if (axis != primaryAxis && axis != secondaryAxis) {
            return 0.0f;
        }

        float baseOffset = KineticBlockEntityVisual.rotationOffset(getBlockState(), axis, getBlockPos());
        if (angleControlActive) {
            return baseOffset + getServoVisualPhaseOffset(dir);
        }

        KineticBlockEntity lane = usesPassthroughSplitKinetics() || axis != secondaryAxis ? this : eastWestLane;
        return baseOffset + lane.getRotationAngleOffset(axis);
    }

    // Get the visual kinetic block entity
    public KineticBlockEntity getVisualKineticBlockEntity(Direction dir) {
        return usesPassthroughSplitKinetics() || dir.getAxis() != getSecondaryLaneAxis() ? this : eastWestLane;
    }

    // Apply the servo face controllers
    private void applyServoFaceControllers(Level level) {
        Set<BlockPos> heldAngleTargets = new HashSet<>();
        Set<BlockPos> legacyAcceptorTargets = new HashSet<>();
        for (ServoFaceController controller : servoControllers) {
            controller.apply(level, heldAngleTargets, legacyAcceptorTargets);
        }
    }

    // Get the face seed target
    @Nullable
    private KineticBlockEntity getFaceSeedTarget(Direction face) {
        BlockPos targetPos = worldPosition.relative(face);
        BlockEntity blockEntity = level != null ? level.getBlockEntity(targetPos) : null;
        if (blockEntity instanceof ExtraKinetics extraKinetics
                && level.getBlockState(targetPos).getBlock() instanceof ExtraKinetics.ExtraKineticsBlock) {

            KineticBlockEntity exposedCog = extraKinetics.getExtraKinetics();
            if (exposedCog != null) {
                return exposedCog;
            }
        }
        return blockEntity instanceof KineticBlockEntity kineticBlockEntity ? kineticBlockEntity : null;
    }

    // Clear the held angle targets
    private void clearHeldAngleTargets(Level level) {
        for (ServoFaceController controller : servoControllers) {
            controller.stop(level);
        }
    }

    // Invalidate the servo target caches
    private void invalidateServoTargetCaches() {
        for (ServoFaceController controller : servoControllers) {
            controller.invalidateTargetCache();
        }
    }

    // Get the servo generated speed
    private float getServoGeneratedSpeed(Direction dir) {
        for (ServoFaceController controller : servoControllers) {
            if (controller.face == dir) {
                return controller.getOutputSpeed();
            }
        }
        return 0.0f;
    }

    // Get the servo visual phase offset
    private float getServoVisualPhaseOffset(Direction dir) {
        for (ServoFaceController controller : servoControllers) {
            if (controller.face == dir) {
                return controller.getVisualPhaseOffset();
            }
        }
        return 0.0f;
    }

    // Get the servo current angle
    private float getServoCurrentAngle(Direction dir) {
        for (ServoFaceController controller : servoControllers) {
            if (controller.face == dir) {
                return controller.getOutputAngle();
            }
        }
        return (float) (getFaceAngle(dir) * getFaceOutputMultiplier(dir));
    }

    // Sync the angle driven block entity
    private void syncAngleDrivenBlockEntity(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        blockEntity.setChanged();
        if (blockEntity instanceof KineticBlockEntity kineticBlockEntity) {
            kineticBlockEntity.sendData();
        }
        if (++rotationSyncToken % 4 == 0) {
            level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        }
    }

    // Get the extra kinetics save name
    @Override
    public String getExtraKineticsSaveName() {
        return "EastWestLane";
    }

    // Get the extra kinetics
    @Override
    public KineticBlockEntity getExtraKinetics() {
        return eastWestLane;
    }

    // Check if this should connect extra kinetics
    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    // Get the virtual kinetics
    @Override
    public List<KineticBlockEntity> ct$getVirtualKinetics() {
        return virtualServoSources;
    }

    // Create the gearbox update tag
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        writeGearbox(tag);
        return tag;
    }

    // Create the gearbox update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Add the goggle tooltip
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.bidirectional_gearbox")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.mode"),
                Component.translatable(operationMode.getTranslationKey()).copy().withStyle(ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.servo_active"),
                CTTooltipHelper.onOff(angleControlActive)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.lane_speed"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.2f / %.2f",
                        Mth.abs(getNorthSouthSpeed()), Mth.abs(getEastWestSpeed())), ChatFormatting.GOLD)));
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.lanes"),
                    CTTooltipHelper.value(northSouthLaneMode.name().toLowerCase(Locale.ROOT) + " / "
                            + eastWestLaneMode.name().toLowerCase(Locale.ROOT), ChatFormatting.GOLD)));
        }
        if (angleControlActive && showDetails) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.face_outputs"),
                    CTTooltipHelper.value(outputNorth + " / " + outputSouth + " / " + outputEast + " / " + outputWest,
                            ChatFormatting.AQUA)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.face_inversion"),
                    CTTooltipHelper.value((invertNorth ? "Inverted" : "Forward") + " / "
                            + (invertSouth ? "Inverted" : "Forward") + " / "
                            + (invertEast ? "Inverted" : "Forward") + " / "
                            + (invertWest ? "Inverted" : "Forward"), ChatFormatting.GOLD)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.target_angles"),
                    CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f / %.1f / %.1f / %.1f",
                            angleNorth, angleSouth, angleEast, angleWest), ChatFormatting.GREEN)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.servo_angles"),
                    CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f / %.1f / %.1f / %.1f",
                            getServoCurrentAngle(Direction.NORTH),
                            getServoCurrentAngle(Direction.SOUTH),
                            getServoCurrentAngle(Direction.EAST),
                            getServoCurrentAngle(Direction.WEST)), ChatFormatting.DARK_AQUA)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.gearbox.face_limits"),
                    CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f / %.1f / %.1f / %.1f",
                            getFaceMaxAngle(Direction.NORTH),
                            getFaceMaxAngle(Direction.SOUTH),
                            getFaceMaxAngle(Direction.EAST),
                            getFaceMaxAngle(Direction.WEST)), ChatFormatting.YELLOW)));
            for (ServoFaceController controller : servoControllers) {
                float capacity = controller.getNetworkCapacity();
                float stress = controller.getNetworkStress();
                float remaining = capacity - stress;
                tooltip.add(CTTooltipHelper.line(
                        controller.face.name().substring(0, 1) + controller.face.name().substring(1).toLowerCase(Locale.ROOT),
                        CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f RPM, %.1f SU free, %.1f SU total",
                                Math.abs(controller.getGeneratedSpeed()), remaining, capacity), ChatFormatting.LIGHT_PURPLE)));
            }
        }
        return added || true;
    }

    // Write the bi directional gearbox
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        writeGearbox(tag);
    }

    // Write the bi directional gearbox safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putString("OperationMode", operationMode.apiName());
        tag.putString("NorthSouthLaneMode", northSouthLaneMode.name());
        tag.putString("EastWestLaneMode", eastWestLaneMode.name());
        tag.putBoolean("InvertN", invertNorth);
        tag.putBoolean("InvertS", invertSouth);
        tag.putBoolean("InvertE", invertEast);
        tag.putBoolean("InvertW", invertWest);
        tag.putDouble("ManualAngleN", manualAngleNorth);
        tag.putDouble("ManualAngleS", manualAngleSouth);
        tag.putDouble("ManualAngleE", manualAngleEast);
        tag.putDouble("ManualAngleW", manualAngleWest);
        tag.putDouble("ServoMaxAngleN", maxServoAngleNorth);
        tag.putDouble("ServoMaxAngleS", maxServoAngleSouth);
        tag.putDouble("ServoMaxAngleE", maxServoAngleEast);
        tag.putDouble("ServoMaxAngleW", maxServoAngleWest);
    }

    // Write the gearbox
    private void writeGearbox(CompoundTag tag) {
        tag.putString("OperationMode", operationMode.apiName());
        tag.putString("NorthSouthLaneMode", northSouthLaneMode.name());
        tag.putString("EastWestLaneMode", eastWestLaneMode.name());
        tag.putBoolean("InvertN", invertNorth);
        tag.putBoolean("InvertS", invertSouth);
        tag.putBoolean("InvertE", invertEast);
        tag.putBoolean("InvertW", invertWest);
        tag.putBoolean("ServoModeActive", angleControlActive);
        tag.putBoolean("AngleControlActive", angleControlActive);
        tag.putBoolean("GyroSourcePresent", gyroSourcePresent);
        tag.putBoolean("ReverseMode", reverseMode);
        tag.putInt("OutN", outputNorth);
        tag.putInt("OutS", outputSouth);
        tag.putInt("OutE", outputEast);
        tag.putInt("OutW", outputWest);
        tag.putDouble("AngleN", angleNorth);
        tag.putDouble("AngleS", angleSouth);
        tag.putDouble("AngleE", angleEast);
        tag.putDouble("AngleW", angleWest);
        for (ServoFaceController controller : servoControllers) {
            String suffix = controller.face.name().substring(0, 1);
            tag.putFloat("ServoAngle" + suffix, controller.getCurrentAngle());
            tag.putFloat("ServoSpeed" + suffix, controller.getGeneratedSpeed());
        }
        tag.putDouble("ManualAngleN", manualAngleNorth);
        tag.putDouble("ManualAngleS", manualAngleSouth);
        tag.putDouble("ManualAngleE", manualAngleEast);
        tag.putDouble("ManualAngleW", manualAngleWest);
        if (!Double.isNaN(maxServoAngleNorth)) {
            tag.putDouble("ServoMaxAngleN", maxServoAngleNorth);
        }
        if (!Double.isNaN(maxServoAngleSouth)) {
            tag.putDouble("ServoMaxAngleS", maxServoAngleSouth);
        }
        if (!Double.isNaN(maxServoAngleEast)) {
            tag.putDouble("ServoMaxAngleE", maxServoAngleEast);
        }
        if (!Double.isNaN(maxServoAngleWest)) {
            tag.putDouble("ServoMaxAngleW", maxServoAngleWest);
        }
    }

    // Read the bi directional gearbox
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        operationMode = tag.contains("OperationMode")
            ? OperationMode.fromName(tag.getString("OperationMode"))
            : OperationMode.AUTO;
        northSouthLaneMode = readEnum(tag, "NorthSouthLaneMode", LaneMode.STRAIGHT);
        eastWestLaneMode = readEnum(tag, "EastWestLaneMode", LaneMode.STRAIGHT);
        invertNorth = tag.getBoolean("InvertN");
        invertSouth = tag.getBoolean("InvertS");
        invertEast = tag.getBoolean("InvertE");
        invertWest = tag.getBoolean("InvertW");
        angleControlActive = tag.contains("ServoModeActive") ? tag.getBoolean("ServoModeActive") : tag.getBoolean("AngleControlActive");
        gyroSourcePresent = tag.getBoolean("GyroSourcePresent");
        reverseMode = tag.getBoolean("ReverseMode");
        outputNorth = tag.getInt("OutN");
        outputSouth = tag.getInt("OutS");
        outputEast = tag.getInt("OutE");
        outputWest = tag.getInt("OutW");
        angleNorth = tag.getDouble("AngleN");
        angleSouth = tag.getDouble("AngleS");
        angleEast = tag.getDouble("AngleE");
        angleWest = tag.getDouble("AngleW");
        for (ServoFaceController controller : servoControllers) {
            String suffix = controller.face.name().substring(0, 1);
            if (tag.contains("ServoAngle" + suffix)) {
                controller.applySyncedMotion(
                        tag.getFloat("ServoAngle" + suffix),
                        tag.getFloat("ServoSpeed" + suffix));
            }
        }
        manualAngleNorth = tag.contains("ManualAngleN") ? tag.getDouble("ManualAngleN") : Double.NaN;
        manualAngleSouth = tag.contains("ManualAngleS") ? tag.getDouble("ManualAngleS") : Double.NaN;
        manualAngleEast = tag.contains("ManualAngleE") ? tag.getDouble("ManualAngleE") : Double.NaN;
        maxServoAngleNorth = tag.contains("ServoMaxAngleN") ? tag.getDouble("ServoMaxAngleN") : Double.NaN;
        maxServoAngleSouth = tag.contains("ServoMaxAngleS") ? tag.getDouble("ServoMaxAngleS") : Double.NaN;
        maxServoAngleEast = tag.contains("ServoMaxAngleE") ? tag.getDouble("ServoMaxAngleE") : Double.NaN;
        maxServoAngleWest = tag.contains("ServoMaxAngleW") ? tag.getDouble("ServoMaxAngleW") : Double.NaN;
        manualAngleWest = tag.contains("ManualAngleW") ? tag.getDouble("ManualAngleW") : Double.NaN;
        if (operationModeBehaviour != null) {
            operationModeBehaviour.value = operationMode.ordinal();
        }
    }

    // Read the enum
    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, E fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        try {
            E val = Enum.valueOf(fallback.getDeclaringClass(), tag.getString(key));
            return val;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // Define the operation mode values
    public enum OperationMode implements INamedIconOptions {
        AUTO(AllIcons.I_REFRESH, "auto"),
        PASSTHROUGH(AllIcons.I_SEND_AND_RECEIVE, "passthrough"),
        SERVO(AllIcons.I_TARGET, "servo"),
        SERVO_LOCKED(AllIcons.I_CONFIG_LOCKED, "servo_locked"),
        PASSTHROUGH_SPLIT(AllIcons.I_TUNNEL_SPLIT, "passthrough_split");

        // Icon
        private final AllIcons icon;
        // Key
        private final String key;

        // Initialize the operation mode
        OperationMode(AllIcons icon, String key) {
            this.icon = icon;
            this.key = key;
        }

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return icon;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.gearbox.mode." + key;
        }

        // Get the API name
        public String apiName() {
            return key;
        }

        // Check if this should use servo outputs
        public boolean shouldUseServoOutputs(boolean gyroSourcePresent, boolean hasManualAngles) {
            return switch (this) {
                case AUTO -> gyroSourcePresent || hasManualAngles;
                case PASSTHROUGH, PASSTHROUGH_SPLIT -> false;
                case SERVO -> gyroSourcePresent || hasManualAngles;
                case SERVO_LOCKED -> true;
            };
        }

        // Create the operation mode from name
        public static OperationMode fromName(String modeName) {
            String normalized = modeName.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return switch (normalized) {
                case "ANGLE_CONTROL", "FACE_OUTPUT", "SERVO_MODE", "SERVO" -> SERVO;
                case "LOCKED", "FACE_OUTPUT_LOCKED", "SERVO_LOCKED" -> SERVO_LOCKED;
                case "SPLIT", "SPLIT_PASSTHROUGH" -> PASSTHROUGH_SPLIT;
                default -> OperationMode.valueOf(normalized);
            };
        }
    }

    // Define the lane mode values
    public enum LaneMode {
        STRAIGHT,
        REVERSED,
        DISABLED
    }

    // Control servo face
    private final class ServoFaceController {
        private static final String MECHANICAL_CRAFTER_BE_CLASS =
                "com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity";

        // Face
        private final Direction face;
        // Generated source
        private final ServoGeneratedSource generatedSource;
        // Shared held-angle output graph
        private final HeldAngleKineticGraph heldAngleGraph = new HeldAngleKineticGraph();

        // Motion
        private final ServoMotionController motion = new ServoMotionController(new ServoMotionConfig(
            SERVO_MAX_RPM,
            SERVO_RPM_PER_DEGREE_ERROR,
            SERVO_DEGREES_PER_TICK_PER_RPM,
            SERVO_ANGLE_TOLERANCE));
        // Cached targets
        private List<ServoAcceptorTarget> cachedTargets = List.of();
        // Cached seed pos
        private BlockPos cachedSeedPos;
        // Cached network id
        private Long cachedNetworkId;
        // Cached network size
        private int cachedNetworkSize = Integer.MIN_VALUE;
        // Tracks whether target cache is valid
        private boolean targetCacheValid;
        // Current estimated stress base
        private float estimatedStressBase;
        // Visual phase offset
        private float visualPhaseOffset;

        // Initialize the servo face
        private ServoFaceController(Direction face, int virtualSlot) {
            this.face = face;
            this.generatedSource = new ServoGeneratedSource(worldPosition, getBlockState(), this, virtualSlot);
        }

        // Get the generated source
        private ServoGeneratedSource getGeneratedSource() {
            return generatedSource;
        }

        // Update the generated source
        private void tickGeneratedSource() {
            generatedSource.tick();
        }

        // Reset the loaded kinetics
        private void resetLoadedKinetics() {
            generatedSource.resetLoadedKinetics();
        }

        // Get the current angle
        @SuppressWarnings("unused")
        private float getCurrentAngle() {
            return motion.getCurrentAngle();
        }

        // Get the generated speed
        private float getGeneratedSpeed() {
            return motion.getGeneratedSpeed();
        }

        // Apply the synced motion
        private void applySyncedMotion(float angle, float speed) {
            motion.applySyncedState(angle, speed);
            float multiplier = getFaceOutputMultiplier(face);
            float gameTime = level == null ? 0.0f : level.getGameTime();
            visualPhaseOffset = angle * multiplier - gameTime * speed * multiplier * 3.0f / 10.0f;
        }

        // Get the visual phase offset
        private float getVisualPhaseOffset() {
            return visualPhaseOffset;
        }

        // Get the output angle
        private float getOutputAngle() {
            return motion.getCurrentAngle() * getFaceOutputMultiplier(face);
        }

        // Get the output speed
        private float getOutputSpeed() {
            return motion.getGeneratedSpeed() * getFaceOutputMultiplier(face);
        }

        // Get the estimated stress base
        @SuppressWarnings("unused")
        private float getEstimatedStressBase() {
            return estimatedStressBase;
        }

        // Get the network stress
        private float getNetworkStress() {
            return generatedSource.getNetworkStressValue();
        }

        // Get the network capacity
        private float getNetworkCapacity() {
            return generatedSource.getNetworkCapacityValue();
        }

        // Check if this is moving
        @SuppressWarnings("unused")
        private boolean isMoving() {
            return motion.isMoving();
        }

        // Stop the servo face
        private void stop(Level level) {
            stopMotion();
            heldAngleGraph.clear(level, BiDirectionalGearboxBlockEntity.this::syncAngleDrivenBlockEntity);
            clearCachedTargets();
        }

        // Stop the motion
        private void stopMotion() {
            motion.stop();
            estimatedStressBase = 0.0f;
            generatedSource.updateGeneratedRotation();
        }

        // Apply the servo face
        private void apply(Level level, Set<BlockPos> heldAngleTargets, Set<BlockPos> legacyAcceptorTargets) {
            KineticBlockEntity seedTarget = getFaceSeedTarget(face);
            float targetAngle = (float) getFaceAngle(face);

            if (seedTarget == null || heldAngleTargets.contains(seedTarget.getBlockPos())) {
                motion.snapTo(targetAngle);
                stopMotion();
                heldAngleGraph.apply(level, null, 0.0f, heldAngleTargets,
                        target -> false, BiDirectionalGearboxBlockEntity.this::syncAngleDrivenBlockEntity);
                clearCachedTargets();
                return;
            }

            updateMotion(targetAngle);
            heldAngleGraph.apply(
                    level,
                    seedTarget,
                    getOutputAngle(),
                    heldAngleTargets,
                    target -> target == generatedSource
                            || target == BiDirectionalGearboxBlockEntity.this
                            || target == eastWestLane
                            || isMechanicalCrafter(target),
                    BiDirectionalGearboxBlockEntity.this::syncAngleDrivenBlockEntity);

            refreshTargetCache(level, seedTarget, legacyAcceptorTargets);
            if (hasClaimedTargetConflict(legacyAcceptorTargets)) {
                motion.snapTo(targetAngle);
                stopMotion();
                clearCachedTargets();
                return;
            }
            claimCachedTargets(legacyAcceptorTargets);
            applyCachedTargets(level);
            generatedSource.updateGeneratedRotation();
        }

        // Refresh the target cache
        private void refreshTargetCache(Level level, KineticBlockEntity seedTarget, Set<BlockPos> claimedTargets) {
            if (targetCacheMatches(seedTarget)) {
                return;
            }

            clearCachedTargets();
            ServoTargetCache cache = buildTargetCache(level, seedTarget, claimedTargets);
            cachedTargets = cache.targets();
            estimatedStressBase = cache.stressBase();
            cachedSeedPos = seedTarget.getBlockPos().immutable();
            cachedNetworkId = seedTarget.network;
            cachedNetworkSize = getNetworkSize(seedTarget);
            targetCacheValid = true;
        }

        // Check if the cached target matches
        private boolean targetCacheMatches(KineticBlockEntity seedTarget) {
            return targetCacheValid
                    && Objects.equals(cachedSeedPos, seedTarget.getBlockPos())
                    && Objects.equals(cachedNetworkId, seedTarget.network)
                    && cachedNetworkSize == getNetworkSize(seedTarget);
        }

        // Build the target cache
        private ServoTargetCache buildTargetCache(Level level, KineticBlockEntity seedTarget,
                                                  Set<BlockPos> claimedTargets) {
            List<ServoAcceptorTarget> targets = new ArrayList<>();
            ArrayDeque<ServoWalkNode> frontier = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            frontier.addLast(new ServoWalkNode(seedTarget, 1.0f, face.getOpposite()));
            float stressBase = 0.0f;

            while (!frontier.isEmpty()) {
                ServoWalkNode node = frontier.removeFirst();
                KineticBlockEntity current = node.target();
                if (current == generatedSource || current == BiDirectionalGearboxBlockEntity.this || current == eastWestLane
                        || isMechanicalCrafter(current)) {
                    continue;
                }

                BlockPos currentPos = current.getBlockPos().immutable();
                if (!visited.add(currentPos)) {
                    continue;
                }

                ServoAcceptorTarget acceptorTarget = resolveServoAcceptorTarget(current, node.inputSide(), node.angleMultiplier());
                if (acceptorTarget != null) {
                    if (!claimedTargets.contains(acceptorTarget.blockPos())) {
                        targets.add(acceptorTarget);
                        stressBase += Math.max(current.calculateStressApplied(), 0.0f);
                    }
                    continue;
                }

                for (KineticBlockEntity neighbour : KineticGraphHelper.getConnectedNeighbours(current)) {
                    if (neighbour == generatedSource || neighbour == BiDirectionalGearboxBlockEntity.this || neighbour == eastWestLane
                            || isMechanicalCrafter(neighbour)) {
                        continue;
                    }
                    BlockPos neighbourPos = neighbour.getBlockPos();
                    if (visited.contains(neighbourPos)) {
                        continue;
                    }
                    Float modifier = KineticGraphHelper.getRotationSpeedModifier(current, neighbour);
                    if (modifier == null || Math.abs(modifier) < 1.0E-4f) {
                        continue;
                    }
                    frontier.addLast(new ServoWalkNode(
                            neighbour,
                            node.angleMultiplier() * modifier,
                            directionFromTo(neighbourPos, currentPos)));
                }
            }

            return new ServoTargetCache(List.copyOf(targets), stressBase);
        }

        // Apply the cached targets
        private void applyCachedTargets(Level level) {
            boolean foundInvalidTarget = false;
            float outputAngle = getOutputAngle();
            for (ServoAcceptorTarget target : cachedTargets) {
                if (!target.isValid(level)) {
                    foundInvalidTarget = true;
                    continue;
                }
                if (target.apply(outputAngle)) {
                    syncAngleDrivenBlockEntity(target.blockEntity());
                }
            }
            if (foundInvalidTarget) {
                invalidateTargetCache();
            }
        }

        // Claim the cached targets
        private void claimCachedTargets(Set<BlockPos> claimedTargets) {
            for (ServoAcceptorTarget target : cachedTargets) {
                claimedTargets.add(target.blockPos());
            }
        }

        // Check if this has claimed target conflict
        private boolean hasClaimedTargetConflict(Set<BlockPos> claimedTargets) {
            for (ServoAcceptorTarget target : cachedTargets) {
                if (claimedTargets.contains(target.blockPos())) {
                    return true;
                }
            }
            return false;
        }

        // Clear the cached targets
        private void clearCachedTargets() {
            for (ServoAcceptorTarget target : cachedTargets) {
                target.clear();
            }
            invalidateTargetCache();
        }

        // Invalidate the target cache
        private void invalidateTargetCache() {
            cachedTargets = List.of();
            cachedSeedPos = null;
            cachedNetworkId = null;
            cachedNetworkSize = Integer.MIN_VALUE;
            targetCacheValid = false;
            estimatedStressBase = 0.0f;
        }

        // Get the network size
        private int getNetworkSize(KineticBlockEntity seedTarget) {
            if (seedTarget == null || !seedTarget.hasNetwork()) {
                return 0;
            }
            try {
                var network = seedTarget.getOrCreateNetwork();
                return network == null ? 0 : network.getSize();
            } catch (RuntimeException ignored) {
                return 0;
            }
        }

        // Resolve the servo acceptor target
        @Nullable
        private ServoAcceptorTarget resolveServoAcceptorTarget(KineticBlockEntity current, @Nullable Direction inputSide,
                                                               float angleMultiplier) {
            if (current instanceof SmartGearboxServoAngleAcceptor acceptor
                    && current instanceof BlockEntity blockEntity
                    && acceptor.canAcceptSmartGearboxServoAngle(inputSide)) {
                return new ServoAcceptorTarget(blockEntity, acceptor, inputSide, angleMultiplier);
            }
            if (current instanceof ExtraKinetics.ExtraKineticsBlockEntity extraKinetics
                    && extraKinetics.getParentBlockEntity() instanceof SmartGearboxServoAngleAcceptor acceptor
                    && extraKinetics.getParentBlockEntity() instanceof BlockEntity blockEntity
                    && acceptor.canAcceptSmartGearboxServoAngle(inputSide)) {
                return new ServoAcceptorTarget(blockEntity, acceptor, inputSide, angleMultiplier);
            }

            Level currentLevel = current.getLevel();
            BlockEntity blockEntity = currentLevel == null ? null : currentLevel.getBlockEntity(current.getBlockPos());
            if (blockEntity instanceof SmartGearboxServoAngleAcceptor acceptor
                    && acceptor.canAcceptSmartGearboxServoAngle(inputSide)) {
                return new ServoAcceptorTarget(blockEntity, acceptor, inputSide, angleMultiplier);
            }
            return null;
        }

        // Update the motion
        private void updateMotion(float targetAngle) {
            motion.update(targetAngle);
        }

        // Check if this is mechanical crafter
        private boolean isMechanicalCrafter(KineticBlockEntity blockEntity) {
            return blockEntity.getClass().getName().equals(MECHANICAL_CRAFTER_BE_CLASS);
        }

        // Get the direction
        @Nullable
        private Direction directionFromTo(BlockPos from, BlockPos to) {
            int dx = to.getX() - from.getX();
            int dy = to.getY() - from.getY();
            int dz = to.getZ() - from.getZ();
            for (Direction dir : Direction.values()) {
                if (dir.getStepX() == dx && dir.getStepY() == dy && dir.getStepZ() == dz) {
                    return dir;
                }
            }
            return null;
        }
    }

    // Store the servo target cache
    private record ServoTargetCache(List<ServoAcceptorTarget> targets, float stressBase) {
    }

    // Store the servo walk node
    private record ServoWalkNode(KineticBlockEntity target, float angleMultiplier, @Nullable Direction inputSide) {
    }

    // Store the servo acceptor target
    private record ServoAcceptorTarget(BlockEntity blockEntity, SmartGearboxServoAngleAcceptor acceptor,
                                       @Nullable Direction inputSide, float angleMultiplier) {
        // Get the block pos
        private BlockPos blockPos() {
            return blockEntity.getBlockPos();
        }

        // Check if this is valid
        private boolean isValid(Level level) {
            return blockEntity.getLevel() == level
                    && !blockEntity.isRemoved()
                    && level.getBlockEntity(blockEntity.getBlockPos()) == blockEntity;
        }

        // Apply the servo acceptor target
        private boolean apply(float sourceAngle) {
            return acceptor.acceptSmartGearboxServoAngleDegrees(sourceAngle * angleMultiplier, inputSide);
        }

        // Clear the servo acceptor target
        private void clear() {
            acceptor.clearSmartGearboxServoAngle(inputSide);
        }
    }

    // Handle the servo generated source
    private final class ServoGeneratedSource extends GeneratingKineticBlockEntity
            implements VirtualKineticBlockEntity {
        // Parent servo generated source
        private final BiDirectionalGearboxBlockEntity parent;
        // Servo generated source controller
        private final ServoFaceController controller;
        // Virtual slot
        private final int virtualSlot;
        // Rotation configuration
        private final IRotate rotationConfiguration;

        // Initialize the servo generated source
        private ServoGeneratedSource(BlockPos parentPos, BlockState state, ServoFaceController controller, int virtualSlot) {
            super(virtualSourceType, new VirtualKineticPos((Vec3i) parentPos, virtualSlot), state);
            this.parent = BiDirectionalGearboxBlockEntity.this;
            this.controller = controller;
            this.virtualSlot = virtualSlot;
            this.rotationConfiguration = new SingleFaceRotationConfiguration(
                    controller.face,
                    blockState -> blockState.getBlock() instanceof BiDirectionalGearboxBlock
                            && blockState.getValue(BiDirectionalGearboxBlock.GYRO_MODE));
        }

        // Get the rotation speed modifier
        @SuppressWarnings("unused")
        public float getRotationSpeedModifier(Direction face) {
            return controller.face == face ? 1.0f : 0.0f;
        }

        // Get the generated speed
        @Override
        public float getGeneratedSpeed() {
            return controller.getOutputSpeed();
        }

        // Calculate the added stress capacity
        @Override
        public float calculateAddedStressCapacity() {

            return Math.abs(controller.getOutputSpeed()) > 1.0E-3f ? SERVO_ACTIVE_STRESS_CAPACITY : 0.0f;
        }

        // Create the network id
        @Override
        public Long createNetworkId() {
            return parent.getBlockPos().asLong() ^ ((long) (virtualSlot + 1) << 56);
        }

        // Get the virtual kinetic parent
        @Override
        public KineticBlockEntity ct$getVirtualKineticParent() {
            return parent;
        }

        // Get the network stress value
        private float getNetworkStressValue() {
            return this.stress;
        }

        // Get the network capacity value
        private float getNetworkCapacityValue() {
            return this.capacity;
        }

        // Get the virtual kinetic slot
        @Override
        public int ct$getVirtualKineticSlot() {
            return virtualSlot;
        }

        // Get the virtual kinetic rotation configuration
        @Override
        public IRotate ct$getVirtualKineticRotationConfiguration() {
            return rotationConfiguration;
        }

        // Reset the loaded kinetics
        private void resetLoadedKinetics() {
            if (hasNetwork()) {
                getOrCreateNetwork().remove(this);
            }
            detachKinetics();
            removeSource();
            reActivateSource = true;
        }
    }

    // Handle east west lane state and behaviour
    public static class EastWestLaneBlockEntity extends SplitShaftBlockEntity implements ExtraKinetics.ExtraKineticsBlockEntity {
        // Parent east west lane
        private final BiDirectionalGearboxBlockEntity parent;

        // Initialize the east west lane
        public EastWestLaneBlockEntity(BlockEntityType<?> type, ExtraBlockPos pos, BlockState state,
                                       BiDirectionalGearboxBlockEntity parent) {
            super(type, (BlockPos) pos, state);
            this.parent = parent;
        }

        // Get the rotation speed modifier
        @Override
        public float getRotationSpeedModifier(Direction face) {
            return parent.getEastWestLaneModifier(face);
        }

        // Get the lane modifier
        private float getLaneModifier(Direction face) {
            if (parent.usesPassthroughSplitKinetics() || face.getAxis() != parent.getSecondaryLaneAxis() || parent.angleControlActive || parent.eastWestLaneMode == LaneMode.DISABLED) {
                return 0.0f;
            }
            if (!hasSource()) {
                return 1.0f;
            }
            Direction sourceFacing = getSourceFacing();
            if (sourceFacing.getAxis() != parent.getSecondaryLaneAxis()) {
                return 0.0f;
            }
            if (sourceFacing == face) {
                return 1.0f;
            }
            return (parent.eastWestLaneMode == LaneMode.REVERSED ? -1.0f : 1.0f)
                    * parent.getFaceOutputMultiplier(face);
        }

        // Propagate the rotation
        @Override
        public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                         BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
            return parent.propagateEastWestLaneTo(target, diff);
        }

        // Get the parent block entity
        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return parent;
        }

        // Get the key
        @Override
        public @NotNull Component getKey() {
            return Component.translatable("createthrusters.gearbox.extra_kinetics.east_west");
        }
    }

    public static final IRotate EAST_WEST_ROTATION_CONFIGURATION = new IRotate() {
        // Check if this has a shaft on the side
        @Override
        public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
            return state.getBlock() instanceof BiDirectionalGearboxBlock
                    && !state.getValue(BiDirectionalGearboxBlock.GYRO_MODE)
                    && !(state.hasProperty(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT)
                    && state.getValue(BiDirectionalGearboxBlock.PASSTHROUGH_SPLIT))
                    && face.getAxis() == secondaryLaneAxisForState(state);
        }

        // Get the rotation axis
        @Override
        public Direction.Axis getRotationAxis(BlockState state) {
            return secondaryLaneAxisForState(state);
        }
    };
}
