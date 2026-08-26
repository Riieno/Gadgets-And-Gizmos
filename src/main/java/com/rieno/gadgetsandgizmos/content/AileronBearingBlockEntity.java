package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.lib.control.OrientationTarget;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHeadAccess;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.lib.physics.MountedAssemblyStatus;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Build and aim one or two mounted aileron sub-levels from shaft, redstone, link or computer control
public class AileronBearingBlockEntity extends KineticBlockEntity implements MenuProvider, IDirectControlReceiver,
        OrientationTarget, AdvancedGraphDataProvider, IHaveGoggleInformation, BlockEntitySubLevelActor,
        SmartGearboxServoAngleAcceptor, BearingHeadAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COMPUTER_TIMEOUT_TICKS = 40L;
    private static final double DEFAULT_LIMIT_DEGREES = 45.0D;
    private static final double HEAD_HIT_DEPTH = 4.0D / 16.0D;
    private static final double HEAD_HIT_EPSILON = 1.0D / 32.0D;
    private static final long NESTED_ASSEMBLY_RETRY_DELAY_TICKS = 4L;
    private static final String[] HEATMAP_PENDING_FIELDS = {
            "subLevelSplits",
            "floodfill",
            "removed",
            "newStarts"
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked heads
    private final EnumMap<BearingHead, HeadState> heads = new EnumMap<>(BearingHead.class);
    // Pending assembly requests
    private final EnumSet<BearingHead> pendingAssemblyRequests = EnumSet.noneOf(BearingHead.class);
    // Current head mode
    private HeadModeBehaviour headMode;
    // Current control mode
    private ControlModeBehaviour controlMode;
    // Active control mode
    private ControlMode activeControlMode = ControlMode.AUTO;
    // Active gyroscope link channel
    private String activeGyroscopeLinkChannel;
    // Shaft angle in degrees
    private double shaftAngleDegrees;
    // Last shaft sample tick
    private long lastShaftSampleTick = Long.MIN_VALUE;
    // Tracks whether shaft angle is initialized
    private boolean shaftAngleInitialized;
    // Gearbox servo input angle in degrees
    private double gearboxServoInputAngleDeg;
    // Last gearbox servo input tick
    private long lastGearboxServoInputTick = Long.MIN_VALUE;
    // Next nested assembly tick
    private long nextNestedAssemblyTick = Long.MIN_VALUE;
    // Tracks whether assembly transfer is in progress
    private boolean assemblyTransferInProgress;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing
    public AileronBearingBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.AILERON_BEARING.get(), pos, state);
        for (BearingHead head : BearingHead.values()) {
            heads.put(head, new HeadState(head));
        }
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
        headMode = new HeadModeBehaviour(this, new AileronValueBoxTransform(4.0D));
        headMode.withCallback(val -> onScrollModeChanged());
        behaviours.add(headMode);
        controlMode = new ControlModeBehaviour(this, new AileronValueBoxTransform(12.0D));
        controlMode.setValue(ControlMode.AUTO.ordinal());
        controlMode.withCallback(val -> onScrollModeChanged());
        behaviours.add(controlMode);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the scroll mode changed event
    private void onScrollModeChanged() {
        if (level != null && !level.isClientSide) {
            setChanged();
            sendData();
        }
    }

    // Update the aileron bearing
    @Override
    public void tick() {
        super.tick();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            updateClientRenderedAngles();
            return;
        }

        processPendingPlacedBlocks(serverLevel);
        processPendingAssemblyReqs(serverLevel);
        maintainMountedAssemblies(serverLevel);
        updateSignals();
        updateTargetsAndAngles();
        aimMountedAssemblies(serverLevel);

        if (level.getGameTime() % 10L == 0L) {
            sendData();
        }
    }

    // Update the client rendered angles
    private void updateClientRenderedAngles() {
        double signedStep = getSignedAngularStep();
        double availableStep = Math.abs(signedStep);
        HeadMode mode = getHeadMode();
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            if (!state.renderedAngleInitialized) {
                state.previousRenderedAngleDeg = state.currentAngleDeg;
                state.renderedAngleDeg = state.currentAngleDeg;
                state.renderedAngleInitialized = true;
                continue;
            }

            state.previousRenderedAngleDeg = state.renderedAngleDeg;
            if (mode == HeadMode.FREE_SINGLE && head == BearingHead.PRIMARY) {
                state.renderedAngleDeg = wrapDeg0To360(state.renderedAngleDeg + signedStep);
            } else if (mode == HeadMode.FREE_MIRRORED) {
                state.renderedAngleDeg = wrapDeg0To360(state.renderedAngleDeg + signedStep);
            } else if (mode == HeadMode.FREE_OPPOSED) {
                double step = head == BearingHead.PRIMARY ? signedStep : -signedStep;
                state.renderedAngleDeg = wrapDeg0To360(state.renderedAngleDeg + step);
            } else {
                state.renderedAngleDeg = moveToward(state.renderedAngleDeg, state.targetAngleDeg, availableStep);
            }
        }
    }

    // Process the pending placed blocks
    private void processPendingPlacedBlocks(ServerLevel serverLevel) {
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            BlockPos pendingPos = state.pendingPlacedBlockPos;
            if (pendingPos == null) {
                continue;
            }
            state.pendingPlacedBlockPos = null;
            absorbPlacedBlockNow(head, state, serverLevel, pendingPos);
        }
    }

    // Process the pending assembly reqs
    private void processPendingAssemblyReqs(ServerLevel serverLevel) {
        if (pendingAssemblyRequests.isEmpty() || !isContainingSubLevelReadyForMountedAssembly(serverLevel)) {
            return;
        }
        for (BearingHead head : BearingHead.values()) {
            if (!pendingAssemblyRequests.remove(head)) {
                continue;
            }
            tryAssembleMountedBlockNow(head, serverLevel);
            return;
        }
    }

    // Maintain the mounted assemblies
    private void maintainMountedAssemblies(ServerLevel serverLevel) {
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            MountedAssemblyStatus status = state.assembly.mountedBlockStatus(this, serverLevel);
            if (state.mountedSubLevelId != null && status.shouldDisassemble()) {
                disassembleMountedBlock(head);
            } else if (state.mountedSubLevelId != null && status.shouldClear()) {
                state.assembly.clearInvalidAssembly(this, serverLevel);
            } else if (state.mountedSubLevelId != null && !state.mountedAssemblyPresent) {
                state.mountedAssemblyPresent = true;
                setChanged();
                sendData();
            }
        }
    }

    // Aim the mounted assemblies
    private void aimMountedAssemblies(ServerLevel serverLevel) {
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            if (state.mountedSubLevelId == null) {
                continue;
            }
            try {
                if (!state.assembly.aim(this, serverLevel, state.currentAngleDeg)) {
                    cleanupFailedAim(head, serverLevel);
                }
            } catch (RuntimeException error) {
                state.assembly.releaseJoint();
                LOGGER.debug("Aileron Bearing {} head at {} will retry after an aiming failure",
                        head.serializedName(), worldPosition, error);
            } catch (LinkageError error) {
                cleanupLinkageFailure(head, serverLevel, error);
            }
        }
    }

    // Clean up the failed aim
    private void cleanupFailedAim(BearingHead head, ServerLevel serverLevel) {
        HeadState state = state(head);
        if (state.assembly.hasMountedBlock(this, serverLevel)) {
            state.assembly.disassemble(this, serverLevel);
        } else {
            state.assembly.clearInvalidAssembly(this, serverLevel);
        }
    }

    // Clean up the linkage failure
    private void cleanupLinkageFailure(BearingHead head, ServerLevel serverLevel, LinkageError error) {
        LOGGER.warn("Aileron Bearing {} head at {} failed due to incompatible Sable linkage: {}",
                head.serializedName(), worldPosition, error.toString());
        try {
            state(head).assembly.disassemble(this, serverLevel);
        } catch (RuntimeException | LinkageError cleanupError) {
            state(head).assembly.releaseJoint();
            setMountedAssembly(head, null, null);
        }
        setChanged();
        sendData();
    }

    // Update the signals
    private void updateSignals() {
        Direction cwSide = getLocalControlSide(true);
        Direction ccwSide = getLocalControlSide(false);
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            int cw = Math.max(getLocalSignal(cwSide), queryWirelessSignal(state.frequencyBindings.get(ControlDirection.CW)));
            int ccw = Math.max(getLocalSignal(ccwSide), queryWirelessSignal(state.frequencyBindings.get(ControlDirection.CCW)));
            state.cwSignal = Mth.clamp(cw, 0, 15);
            state.ccwSignal = Mth.clamp(ccw, 0, 15);
        }
    }

    // Get the local signal
    private int getLocalSignal(Direction dir) {
        if (level == null || dir == null) {
            return 0;
        }
        BlockPos neighborPos = worldPosition.relative(dir);
        Direction side = redstoneSignalQuerySide(dir);
        return Math.max(level.getSignal(neighborPos, side), level.getDirectSignal(neighborPos, side));
    }

    // Get the redstone signal query side
    static Direction redstoneSignalQuerySide(Direction neighborDirection) {
        return neighborDirection;
    }

    // Query the wireless signal
    private int queryWirelessSignal(@Nullable FrequencyBinding binding) {
        if (level == null || binding == null || !binding.isBound()) {
            return 0;
        }
        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
        Map<Couple<RedstoneLinkNetworkHandler.Frequency>, Set<IRedstoneLinkable>> networks =
                Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level);
        Set<IRedstoneLinkable> network = networks.get(key);
        if (network == null || network.isEmpty()) {
            return 0;
        }

        Vec3 currentPosition = SimulatedHelper.toGlobalWorldPosition(this, Vec3.atCenterOf(worldPosition));
        int linkRange = AllConfigs.server().logistics.linkRange.get();
        double linkRangeSq = linkRange * (double) linkRange;
        int maxSignal = 0;
        for (IRedstoneLinkable candidate : network) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            int transmitted = Mth.clamp(candidate.getTransmittedStrength(), 0, 15);
            if (transmitted <= 0) {
                continue;
            }
            BlockPos candidateLocation = candidate.getLocation();
            if (candidateLocation == null) {
                continue;
            }
            Vec3 candidatePosition = SimulatedHelper.projectOutOfSubLevels(level, Vec3.atCenterOf(candidateLocation));
            if (SimulatedHelper.distanceSquaredWithSubLevels(level, currentPosition, candidatePosition) > linkRangeSq) {
                continue;
            }
            maxSignal = Math.max(maxSignal, transmitted);
            if (maxSignal >= 15) {
                break;
            }
        }
        return maxSignal;
    }

    // Get the local control side
    private Direction getLocalControlSide(boolean clockwise) {
        Direction.Axis facingAxis = getHeadDirection(BearingHead.PRIMARY).getAxis();
        Direction.Axis shaftAxis = getShaftAxis();
        Direction.Axis controlAxis = Direction.Axis.X;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != facingAxis && axis != shaftAxis) {
                controlAxis = axis;
                break;
            }
        }
        Direction positive = Direction.fromAxisAndDirection(controlAxis, Direction.AxisDirection.POSITIVE);
        return clockwise ? positive : positive.getOpposite();
    }

    // Update the targets and angles
    private void updateTargetsAndAngles() {
        double servoAngle = sampleShaftAngleDeg();
        double signedStep = getSignedAngularStep();
        double availableStep = Math.abs(signedStep);
        HeadMode mode = getHeadMode();
        activeControlMode = resolveActiveControlMode();
    // && !hasFreshComputerInput()
        if (mode != HeadMode.PRECISE && !hasAnyRedstoneSignal() && activeControlMode == ControlMode.REDSTONE) {
            for (BearingHead head : BearingHead.values()) {
                HeadState state = state(head);
                state.targetAngleDeg = clampToHeadRange(head, 0.0D);
                state.previousRenderedAngleDeg = state.currentAngleDeg;
                state.currentAngleDeg = moveToward(
                        state.currentAngleDeg, state.targetAngleDeg, availableStep);
                state.renderedAngleDeg = state.currentAngleDeg;
            }
            return;
        }

        if (mode == HeadMode.FREE_SINGLE) {
            applyFreeRotation(BearingHead.PRIMARY, signedStep);
            holdHead(BearingHead.SECONDARY);
            return;
        }
        if (mode == HeadMode.FREE_MIRRORED) {
            applyFreeRotation(BearingHead.PRIMARY, signedStep);
            applyFreeRotation(BearingHead.SECONDARY, signedStep);
            return;
        }
        if (mode == HeadMode.FREE_OPPOSED) {
            applyFreeRotation(BearingHead.PRIMARY, signedStep);
            applyFreeRotation(BearingHead.SECONDARY, -signedStep);
            return;
        }

        double primaryTarget = resolveRequestedAngle(BearingHead.PRIMARY, activeControlMode, servoAngle);
        double secondaryTarget = resolveRequestedAngle(BearingHead.SECONDARY, activeControlMode, servoAngle);
        switch (mode) {
            case SINGLE -> {
                setTarget(BearingHead.PRIMARY, primaryTarget);
                holdHead(BearingHead.SECONDARY);
            }
            case MIRRORED -> {
                setTarget(BearingHead.PRIMARY, primaryTarget);
                setTarget(BearingHead.SECONDARY, clampToHeadRange(BearingHead.SECONDARY, primaryTarget));
            }
            case OPPOSED -> {
                setTarget(BearingHead.PRIMARY, primaryTarget);
                setTarget(BearingHead.SECONDARY, clampToHeadRange(BearingHead.SECONDARY, -primaryTarget));
            }
            case PRECISE -> {
                setTarget(BearingHead.PRIMARY, primaryTarget);
                setTarget(BearingHead.SECONDARY, secondaryTarget);
            }
            case FREE_SINGLE, FREE_MIRRORED, FREE_OPPOSED -> {
            }
        }

        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            state.previousRenderedAngleDeg = state.currentAngleDeg;
            state.currentAngleDeg = moveToward(state.currentAngleDeg, state.targetAngleDeg, availableStep);
            state.renderedAngleDeg = state.currentAngleDeg;
        }
    }

    // Apply the free rotation
    private void applyFreeRotation(BearingHead head, double signedStep) {
        HeadState state = state(head);
        state.previousRenderedAngleDeg = state.currentAngleDeg;
        if (Math.abs(signedStep) > 1.0E-6D) {
            state.currentAngleDeg = wrapDeg0To360(state.currentAngleDeg + signedStep);
        }
        state.targetAngleDeg = state.currentAngleDeg;
        state.renderedAngleDeg = state.currentAngleDeg;
    }

    // Hold the head
    private void holdHead(BearingHead head) {
        HeadState state = state(head);
        state.targetAngleDeg = state.currentAngleDeg;
        state.previousRenderedAngleDeg = state.currentAngleDeg;
        state.renderedAngleDeg = state.currentAngleDeg;
    }

    // Set the target
    private void setTarget(BearingHead head, double targetAngleDeg) {
        state(head).targetAngleDeg = clampToHeadRange(head, targetAngleDeg);
    }

    // Resolve the requested angle
    private double resolveRequestedAngle(BearingHead head, ControlMode mode, double servoAngle) {
        HeadState state = state(head);
        return switch (mode) {
            case REDSTONE -> computeRedstoneRequestedAngle(head);
            case SERVO -> clampToHeadRange(head, signedDeg(servoAngle));
            case COMPUTER -> state.computerOverrideActive
                    ? clampToHeadRange(head, state.computerTargetAngleDeg)
                    : state.targetAngleDeg;
            case AUTO -> state.targetAngleDeg;
        };
    }

    // Resolve the active control mode
    private ControlMode resolveActiveControlMode() {
        ControlMode configured = getControlMode();
        if (configured != ControlMode.AUTO) {
            return configured;
        }
        if (hasFreshComputerInput()) {
            return ControlMode.COMPUTER;
        }
        if (hasAnyRedstoneSignal()) {
            return ControlMode.REDSTONE;
        }
        if (Math.abs(getSpeed()) > 1.0E-4F || shaftAngleInitialized) {
            return ControlMode.SERVO;
        }
        return ControlMode.AUTO;
    }

    // Check if this has fresh computer input
    private boolean hasFreshComputerInput() {
        if (level == null) {
            return false;
        }
        long now = level.getGameTime();
        for (HeadState state : heads.values()) {
            if (state.computerOverrideActive && now - state.lastComputerInputTick <= COMPUTER_TIMEOUT_TICKS) {
                return true;
            }
        }
        return false;
    }

    // Check if this has any redstone signal
    private boolean hasAnyRedstoneSignal() {
        for (HeadState state : heads.values()) {
            if (state.cwSignal > 0 || state.ccwSignal > 0) {
                return true;
            }
        }
        return false;
    }

    // Calculate the redstone requested angle
    private double computeRedstoneRequestedAngle(BearingHead head) {
        HeadState state = state(head);
        double blend = Mth.clamp((state.cwSignal - state.ccwSignal + 15.0D) / 30.0D, 0.0D, 1.0D);
        return Mth.lerp(blend, state.minAngleDeg, state.maxAngleDeg);
    }

    // Move toward the target
    private double moveToward(double currentAngle, double targetAngle, double availableStep) {
        if (availableStep <= 1.0E-6D) {
            return currentAngle;
        }
        double delta = targetAngle - currentAngle;
        if (Math.abs(delta) <= 0.05D) {
            return targetAngle;
        }
        return currentAngle + Mth.clamp(delta, -availableStep, availableStep);
    }

    // Sample the shaft angle deg
    private double sampleShaftAngleDeg() {
        if (hasRecentGearboxServoInput()) {
            shaftAngleDegrees = KineticAngleHelper.normalizeDegrees(gearboxServoInputAngleDeg);
            shaftAngleInitialized = true;
            return shaftAngleDegrees;
        }

        if (level == null) {
            return shaftAngleDegrees;
        }

        long now = level.getGameTime();
        if (!shaftAngleInitialized) {
            shaftAngleInitialized = true;
            shaftAngleDegrees = KineticAngleHelper.normalizeDegrees(getRotationAngleOffset(getShaftAxis()));
            lastShaftSampleTick = now;
            return shaftAngleDegrees;
        }

        long deltaTicks = Math.max(0L, now - lastShaftSampleTick);
        if (deltaTicks > 0L) {
            double delta = getSignedAngularStep() * deltaTicks;
            if (Math.abs(delta) > 1.0E-6D) {
                shaftAngleDegrees = KineticAngleHelper.normalizeDegrees(shaftAngleDegrees + delta);
            }
            lastShaftSampleTick = now;
        }
        return shaftAngleDegrees;
    }

    // Check if this has recent gearbox servo input
    private boolean hasRecentGearboxServoInput() {
        return level != null
                && !level.isClientSide
                && lastGearboxServoInputTick != Long.MIN_VALUE
                && level.getGameTime() - lastGearboxServoInputTick <= 2;
    }

    // Check if this can accept smart gearbox servo angle
    @Override
    public boolean canAcceptSmartGearboxServoAngle(Direction inputSide) {
        return inputSide == null || inputSide.getAxis() == getShaftAxis();
    }

    // Accept the smart gearbox servo angle degrees
    @Override
    public boolean acceptSmartGearboxServoAngleDegrees(double angleDegrees, Direction inputSide) {
        if (level == null || level.isClientSide || !canAcceptSmartGearboxServoAngle(inputSide)) {
            return false;
        }
        gearboxServoInputAngleDeg = KineticAngleHelper.normalizeDegrees(angleDegrees);
        lastGearboxServoInputTick = level.getGameTime();
        shaftAngleDegrees = gearboxServoInputAngleDeg;
        shaftAngleInitialized = true;
        lastShaftSampleTick = level.getGameTime();
        setChanged();
        return true;
    }

    // Get the signed angular step
    private double getSignedAngularStep() {
        return SwivelBearingBlockEntity.convertToAngular(getSpeed());
    }

    // Clamp the head range
    private double clampToHeadRange(BearingHead head, double angleDeg) {
        HeadState state = state(head);
        return Mth.clamp(angleDeg, state.minAngleDeg, state.maxAngleDeg);
    }

    // Get the signed deg
    private static double signedDeg(double angle) {
        double wrapped = wrapDeg0To360(angle);
        return wrapped > 180.0D ? wrapped - 360.0D : wrapped;
    }

    // Wrap the angle to 0–360 degrees
    private static double wrapDeg0To360(double angle) {
        angle %= 360.0D;
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        return angle;
    }

    // Get the shaft axis
    public Direction.Axis getShaftAxis() {
        return AileronBearingBlock.getShaftAxis(getBlockState());
    }

    // Get the head direction
    public Direction getHeadDirection(BearingHead head) {
        BlockState state = getBlockState();
        Direction primary = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.UP;
        return head == BearingHead.PRIMARY ? primary : primary.getOpposite();
    }

    // Get the head mode
    public HeadMode getHeadMode() {
        return headMode == null ? HeadMode.SINGLE : headMode.get();
    }

    // Set the head mode
    public void setHeadMode(HeadMode mode) {
        HeadMode next = mode == null ? HeadMode.SINGLE : mode;
        if (headMode != null) {
            headMode.setValue(next.ordinal());
        }
        setChanged();
        sendData();
    }

    // Get the control mode
    public ControlMode getControlMode() {
        return controlMode == null ? ControlMode.AUTO : controlMode.get();
    }

    // Set the control mode
    public void setControlMode(ControlMode mode) {
        ControlMode next = mode == null ? ControlMode.AUTO : mode;
        if (controlMode != null) {
            controlMode.setValue(next.ordinal());
        }
        setChanged();
        sendData();
    }

    // Get the active control mode
    public ControlMode getActiveControlMode() {
        return activeControlMode;
    }

    // Get the head angle
    public double getHeadAngle(BearingHead head) {
        return state(head).currentAngleDeg;
    }

    // Get the head target angle
    public double getHeadTargetAngle(BearingHead head) {
        return state(head).targetAngleDeg;
    }

    // Get the interpolated head angle
    public double getInterpolatedHeadAngle(BearingHead head, float partialTicks) {
        HeadState state = state(head);
        return AngleHelper.angleLerp(partialTicks, state.previousRenderedAngleDeg, state.renderedAngleDeg);
    }

    // Get the signal
    public int getSignal(BearingHead head, ControlDirection dir) {
        HeadState state = state(head);
        return dir == ControlDirection.CW ? state.cwSignal : state.ccwSignal;
    }

    // Get the min angle
    public double getMinAngle(BearingHead head) {
        return state(head).minAngleDeg;
    }

    // Get the max angle
    public double getMaxAngle(BearingHead head) {
        return state(head).maxAngleDeg;
    }

    // Set the angle range
    public void setAngleRange(BearingHead head, double minAngleDeg, double maxAngleDeg) {
        HeadState state = state(head);
        double limit = CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        double min = Mth.clamp(minAngleDeg, -limit, limit);
        double max = Mth.clamp(maxAngleDeg, -limit, limit);
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
        }
        state.minAngleDeg = min;
        state.maxAngleDeg = max;
        state.targetAngleDeg = clampToHeadRange(head, state.targetAngleDeg);
        state.currentAngleDeg = clampToHeadRange(head, state.currentAngleDeg);
        setChanged();
        sendData();
    }

    // Set the head target angle
    public void setHeadTargetAngle(BearingHead head, double angleDeg) {
        activeGyroscopeLinkChannel = null;
        setComputerTargetAngle(head, angleDeg, level == null ? Long.MIN_VALUE : level.getGameTime());
        setControlMode(ControlMode.COMPUTER);
        setChanged();
        sendData();
    }

    // Apply the gyroscope link control signal
    public void applyGyroscopeLinkControlSignal(String channelId, double primaryTargetAngleDeg,
                                                double secondaryTargetAngleDeg) {
        if (level == null || level.isClientSide) {
            return;
        }
        activeGyroscopeLinkChannel = gyroscopeLinkControlChannel(channelId);
        long now = level.getGameTime();
        setComputerTargetAngle(BearingHead.PRIMARY, primaryTargetAngleDeg, now);
        setComputerTargetAngle(BearingHead.SECONDARY, secondaryTargetAngleDeg, now);
        setControlMode(ControlMode.COMPUTER);
        setChanged();
        sendData();
    }

    // Clear the gyroscope link control signal
    public void clearGyroscopeLinkControlSignal(String channelId) {
        if (level == null || level.isClientSide) {
            return;
        }
        String signalChannel = gyroscopeLinkControlChannel(channelId);
        if (!signalChannel.equals(activeGyroscopeLinkChannel)) {
            return;
        }
        activeGyroscopeLinkChannel = null;
        for (BearingHead head : BearingHead.values()) {
            clearHeadTargetOverride(head);
        }
    }

    // Set the computer target angle
    private void setComputerTargetAngle(BearingHead head, double angleDeg, long gameTime) {
        HeadState state = state(head);
        state.computerTargetAngleDeg = clampToHeadRange(head, angleDeg);
        state.computerOverrideActive = true;
        state.lastComputerInputTick = gameTime;
    }

    // Get the gyroscope link control channel
    private static String gyroscopeLinkControlChannel(String channelId) {
        return channelId == null || channelId.isBlank() ? "gyroscope_link" : channelId;
    }

    // Clear the head target override
    public void clearHeadTargetOverride(BearingHead head) {
        activeGyroscopeLinkChannel = null;
        HeadState state = state(head);
        state.computerOverrideActive = false;
        state.lastComputerInputTick = Long.MIN_VALUE;
        setChanged();
        sendData();
    }

    // Check if this has head target override
    public boolean hasHeadTargetOverride(BearingHead head) {
        return state(head).computerOverrideActive;
    }

    // Get the frequency first
    public ItemStack getFrequencyFirst(BearingHead head, ControlDirection dir) {
        return state(head).frequencyBindings.get(dir).first();
    }

    // Get the frequency second
    public ItemStack getFrequencySecond(BearingHead head, ControlDirection dir) {
        return state(head).frequencyBindings.get(dir).second();
    }

    // Set the frequency
    public void setFrequency(BearingHead head, ControlDirection dir, ItemStack first, ItemStack second) {
        state(head).frequencyBindings.get(dir).set(copySingle(first), copySingle(second));
        setChanged();
        sendData();
    }

    // Try to assemble mounted block
    public boolean tryAssembleMountedBlock(BearingHead head) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        if (!isContainingSubLevelReadyForMountedAssembly(serverLevel)) {
            queueMountedAssembly(head, serverLevel);
            return true;
        }
        return tryAssembleMountedBlockNow(head, serverLevel);
    }

    // Try to assemble mounted block now
    private boolean tryAssembleMountedBlockNow(BearingHead head, ServerLevel serverLevel) {
        HeadState state = state(head);
        if (state.mountedSubLevelId != null) {
            if (state.assembly.hasMountedBlock(this, serverLevel)) {
                return false;
            }
            state.assembly.clearInvalidAssembly(this, serverLevel);
        }
        try {
            if (!state.assembly.assemble(this, serverLevel)) {
                return false;
            }
            if (!state.assembly.aim(this, serverLevel, state.currentAngleDeg)) {
                state.assembly.disassemble(this, serverLevel);
                markNestedAssemblyMutation(serverLevel);
                return false;
            }
            state.mountedAssemblyPresent = true;
            setChanged();
            sendData();
            markNestedAssemblyMutation(serverLevel);
            return true;
        } catch (RuntimeException error) {
            rollbackMountedAssembly(head, serverLevel);
            markNestedAssemblyMutation(serverLevel);
            LOGGER.debug("Aileron Bearing {} head at {} failed and was rolled back",
                    head.serializedName(), worldPosition, error);
            return false;
        } catch (LinkageError error) {
            cleanupLinkageFailure(head, serverLevel, error);
            return false;
        }
    }

    // Queue the mounted assembly
    private void queueMountedAssembly(BearingHead head, ServerLevel serverLevel) {
        pendingAssemblyRequests.add(head);
        nextNestedAssemblyTick = Math.max(nextNestedAssemblyTick,
                serverLevel.getGameTime() + NESTED_ASSEMBLY_RETRY_DELAY_TICKS);
        setChanged();
    }

    // Disassemble the mounted block
    public void disassembleMountedBlock(BearingHead head) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            state(head).assembly.disassemble(this, serverLevel);
            pendingAssemblyRequests.remove(head);
            markNestedAssemblyMutation(serverLevel);
        } else {
            state(head).assembly.releaseJoint();
            setMountedAssembly(head, null, null);
            pendingAssemblyRequests.remove(head);
        }
    }

    // Check if the mounted assembly is present
    public boolean isMountedAssemblyPresent(BearingHead head) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            return state(head).mountedSubLevelId != null && state(head).assembly.hasMountedBlock(this, serverLevel);
        }
        HeadState state = state(head);
        return state.mountedAssemblyPresent && state.mountedSubLevelId != null;
    }

    // Check if the set mounted assembly is present
    public boolean setMountedAssemblyPresent(BearingHead head, boolean assembled) {
        if (assembled) {
            return isMountedAssemblyPresent(head) || tryAssembleMountedBlock(head);
        }
        if (isMountedAssemblyPresent(head)) {
            disassembleMountedBlock(head);
        }
        return true;
    }

    // Absorb a block placed on the assembled head
    public boolean absorbPlacedBlockOnAssembledHead(BearingHead head, BlockPos placedPos) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || head == null || placedPos == null) {
            return false;
        }
        HeadState state = state(head);
        if (state.mountedSubLevelId == null || !placedPos.equals(getMountedBlockPos(head))) {
            return false;
        }
        BlockState placedState = serverLevel.getBlockState(placedPos);
        if (placedState.isAir() || placedState.getBlock() instanceof AileronBearingLinkBlock) {
            return false;
        }
        state.pendingPlacedBlockPos = placedPos.immutable();
        return true;
    }

    // Absorb the placed block immediately
    private boolean absorbPlacedBlockNow(BearingHead head, HeadState state, ServerLevel serverLevel, BlockPos placedPos) {
        try {
            boolean absorbed = state.assembly.absorbPlacedBlock(this, serverLevel, placedPos);
            if (absorbed) {
                state.mountedAssemblyPresent = true;
                setChanged();
                sendData();
            }
            return absorbed;
        } catch (RuntimeException error) {
            state.assembly.releaseJoint();
            LOGGER.debug("Aileron Bearing {} head at {} could not absorb a newly placed block",
                    head.serializedName(), worldPosition, error);
            return false;
        } catch (LinkageError error) {
            cleanupLinkageFailure(head, serverLevel, error);
            return false;
        }
    }

    // Roll back the mounted assembly
    private void rollbackMountedAssembly(BearingHead head, ServerLevel serverLevel) {
        try {
            state(head).assembly.disassemble(this, serverLevel);
        } catch (RuntimeException | LinkageError cleanupError) {
            try {
                state(head).assembly.clearInvalidAssembly(this, serverLevel);
            } catch (RuntimeException | LinkageError ignored) {
                state(head).assembly.releaseJoint();
                setMountedAssembly(head, null, null);
            }
        }
    }

    // Begin the assembly transfer
    void beginAssemblyTransfer() {
        assemblyTransferInProgress = true;
        pendingAssemblyRequests.clear();
        for (BearingHead head : BearingHead.values()) {
            state(head).assembly.releaseJoint();
        }
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        assemblyTransferInProgress = false;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            state.assembly.releaseJoint();
            if (state.mountedSubLevelId != null && state.mountedLocalPos != null) {
                state.assembly.refreshLinkParent(this, serverLevel);
            }
        }
        markNestedAssemblyMutation(serverLevel);
        setChanged();
        sendData();
    }

    // Update the mounted assembly from link
    void updateMountedAssemblyFromLink(BearingHead head, @Nullable UUID subLevelId, BlockPos localPos) {
        if (head == null || localPos == null) {
            return;
        }
        HeadState state = state(head);
        state.mountedSubLevelId = subLevelId;
        state.mountedLocalPos = localPos.immutable();
        state.mountedAssemblyPresent = subLevelId != null;
        state.assembly.releaseJoint();
        SableAssemblyTopologyInvalidation.invalidate(level);
        setChanged();
        sendData();
    }

    // Get the mounted block pos
    public BlockPos getMountedBlockPos(BearingHead head) {
        return worldPosition.relative(getHeadDirection(head));
    }

    // Get the mounted sublevel id
    public UUID getMountedSubLevelId(BearingHead head) {
        return state(head).mountedSubLevelId;
    }

    // Get the mounted local pos
    BlockPos getMountedLocalPos(BearingHead head) {
        return state(head).mountedLocalPos;
    }

    // Check if another head references the mounted sublevel
    boolean isMountedSubLevelReferencedByOtherHead(BearingHead owner, UUID subLevelId) {
        if (subLevelId == null) {
            return false;
        }
        for (BearingHead head : BearingHead.values()) {
            if (head != owner && subLevelId.equals(state(head).mountedSubLevelId)) {
                return true;
            }
        }
        return false;
    }

    // Set the mounted assembly
    void setMountedAssembly(BearingHead head, @Nullable UUID subLevelId, @Nullable BlockPos localPos) {
        HeadState state = state(head);
        state.mountedSubLevelId = subLevelId;
        state.mountedLocalPos = localPos == null ? null : localPos.immutable();
        state.mountedAssemblyPresent = subLevelId != null && localPos != null;
        SableAssemblyTopologyInvalidation.invalidate(level);
        setChanged();
        sendData();
    }

    // Check if the containing sublevel is ready for the mounted assembly
    private boolean isContainingSubLevelReadyForMountedAssembly(ServerLevel serverLevel) {
        SubLevel containing = Sable.HELPER.getContaining(this);
        if (containing == null) {
            return true;
        }
        if (containing.isRemoved() || serverLevel.getGameTime() < nextNestedAssemblyTick) {
            return false;
        }
        return isSubLevelSplitIdle(containing);
    }

    // Mark the nested assembly mutation
    private void markNestedAssemblyMutation(ServerLevel serverLevel) {
        if (Sable.HELPER.getContaining(this) != null) {
            nextNestedAssemblyTick = serverLevel.getGameTime() + NESTED_ASSEMBLY_RETRY_DELAY_TICKS;
        }
    }

    // Check if the sublevel split is idle
    private static boolean isSubLevelSplitIdle(SubLevel subLevel) {
        if (!SableConfig.SUB_LEVEL_SPLITTING.getAsBoolean() || !(subLevel instanceof ServerSubLevel serverSubLevel)) {
            return true;
        }
        Object heatMapManager = serverSubLevel.getHeatMapManager();
        if (!readBooleanField(heatMapManager, "splitComplete", true)) {
            return false;
        }
        for (String fieldName : HEATMAP_PENDING_FIELDS) {
            if (!readEmptyField(heatMapManager, fieldName)) {
                return false;
            }
        }
        return true;
    }

    // Read the boolean field
    private static boolean readBooleanField(Object owner, String fieldName, boolean fallback) {
        try {
            Field field = owner.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(owner);
            return val instanceof Boolean bool ? bool : fallback;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return fallback;
        }
    }

    // Read the empty field
    private static boolean readEmptyField(Object owner, String fieldName) {
        try {
            Field field = owner.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return isEmptyValue(field.get(owner));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return true;
        }
    }

    // Check if the value is empty
    private static boolean isEmptyValue(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (val instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        try {
            Object empty = val.getClass().getMethod("isEmpty").invoke(val);
            return empty instanceof Boolean bool ? bool : true;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return true;
        }
    }

    // Get the head for direction
    @Nullable
    public BearingHead getHeadForDirection(Direction hitFace) {
        for (BearingHead head : BearingHead.values()) {
            if (getHeadDirection(head) == hitFace) {
                return head;
            }
        }
        return null;
    }

    // Get the head for hit
    @Nullable
    public BearingHead getHeadForHit(BlockHitResult hitResult) {
        if (getHeadForDirection(hitResult.getDirection()) != null) {
            return null;
        }

        Vec3 localHit = hitResult.getLocation()
                .subtract(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        for (BearingHead head : BearingHead.values()) {
            if (isWithinHeadSlab(localHit, getHeadDirection(head))) {
                return head;
            }
        }
        return null;
    }

    // Check if the hit is within the head slab
    private static boolean isWithinHeadSlab(Vec3 localHit, Direction dir) {
        double coordinate = axisCoordinate(localHit, dir.getAxis());
        if (dir.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            return coordinate >= 1.0D - HEAD_HIT_DEPTH - HEAD_HIT_EPSILON
                    && coordinate <= 1.0D + HEAD_HIT_EPSILON;
        }
        return coordinate <= HEAD_HIT_DEPTH + HEAD_HIT_EPSILON
                && coordinate >= -HEAD_HIT_EPSILON;
    }

    // Get the axis coordinate
    private static double axisCoordinate(Vec3 vector, Direction.Axis axis) {
        return switch (axis) {
            case X -> vector.x;
            case Y -> vector.y;
            case Z -> vector.z;
        };
    }

    // Check if this can accept orientation payload
    @Override
    public boolean canAcceptOrientationPayload(OrientationPayload payload) {
        return payload != null;
    }

    // Apply the orientation payload
    @Override
    public void applyOrientationPayload(OrientationPayload payload) {
        if (payload == null || level == null || level.isClientSide) {
            return;
        }
        setHeadTargetAngle(BearingHead.PRIMARY, payload.getXAngleDegrees());
        setHeadTargetAngle(BearingHead.SECONDARY, payload.getZAngleDegrees());
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        if (level == null || level.isClientSide) {
            return;
        }
        String key = channelId == null ? "" : channelId.trim().toLowerCase(Locale.ROOT);
        BearingHead head = key.contains("secondary") || key.contains("bottom") || key.contains("orange")
                || key.startsWith("roll_") || key.equals("roll")
                ? BearingHead.SECONDARY
                : BearingHead.PRIMARY;
        double sign = key.contains("ccw") || key.contains("counter") || key.contains("down") || key.contains("left")
                ? -1.0D
                : 1.0D;
        float normalized = normalizeCtrlValue(val);
        if (Math.abs(normalized) <= 1.0E-4F) {
            if (getHeadMode() != HeadMode.PRECISE) {
                setComputerTargetAngle(head, 0.0D, level.getGameTime());
                setControlMode(ControlMode.COMPUTER);
                setChanged();
                sendData();
            }
            return;
        }
        HeadState state = state(head);
        double next = state.computerOverrideActive ? state.computerTargetAngleDeg : state.currentAngleDeg;
        next += sign * Math.abs(normalized) * Math.max(1.0D, Math.abs(getSignedAngularStep()));
        setHeadTargetAngle(head, next);
    }

    // Normalize the ctrl value
    private static float normalizeCtrlValue(float val) {
        if (Float.isNaN(val) || Float.isInfinite(val)) {
            return 0.0F;
        }
        float abs = Math.abs(val);
        if (abs <= 1.0F) {
            return Mth.clamp(val, -1.0F, 1.0F);
        }
        if (abs <= 15.0F) {
            return Mth.clamp(val / 15.0F, -1.0F, 1.0F);
        }
        return Mth.clamp(Math.signum(val), -1.0F, 1.0F);
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("primary_angle", "number");
        data.put("secondary_angle", "number");
        data.put("primary_target", "number");
        data.put("secondary_target", "number");
        data.put("primary_computer_target", "number");
        data.put("secondary_computer_target", "number");
        data.put("primary_computer_override", "boolean");
        data.put("secondary_computer_override", "boolean");
        data.put("primary_min", "number");
        data.put("primary_max", "number");
        data.put("secondary_min", "number");
        data.put("secondary_max", "number");
        data.put("primary_assembled", "boolean");
        data.put("secondary_assembled", "boolean");
        data.put("primary_cw_signal", "number");
        data.put("primary_ccw_signal", "number");
        data.put("secondary_cw_signal", "number");
        data.put("secondary_ccw_signal", "number");
        data.put("head_mode", "string");
        data.put("control_mode", "string");
        data.put("active_control_mode", "string");
        data.put("shaft_angle", "number");
        data.put("shaft_speed", "number");
        return data;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("primary_angle", "number");
        data.put("secondary_angle", "number");
        data.put("primary_target", "number");
        data.put("secondary_target", "number");
        data.put("primary_computer_target", "number");
        data.put("secondary_computer_target", "number");
        data.put("primary_computer_override", "boolean");
        data.put("secondary_computer_override", "boolean");
        data.put("primary_min", "number");
        data.put("primary_max", "number");
        data.put("secondary_min", "number");
        data.put("secondary_max", "number");
        data.put("head_mode", "string");
        data.put("control_mode", "string");
        return data;
    }

    // Get the graph writable options
    @Override
    public Map<String, List<String>> graphWritableOptions() {
        return Map.of(
                "head_mode", List.of(
                        "single", "mirrored", "opposed", "precise",
                        "free_single", "free_mirrored", "free_opposed"),
                "control_mode", List.of("redstone", "servo", "computer", "auto"));
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case "primary_angle" -> AdvancedGraphDocument.Value.number(getHeadAngle(BearingHead.PRIMARY));
            case "secondary_angle" -> AdvancedGraphDocument.Value.number(getHeadAngle(BearingHead.SECONDARY));
            case "primary_target" -> AdvancedGraphDocument.Value.number(getHeadTargetAngle(BearingHead.PRIMARY));
            case "secondary_target" -> AdvancedGraphDocument.Value.number(getHeadTargetAngle(BearingHead.SECONDARY));
            case "primary_computer_target" -> AdvancedGraphDocument.Value.number(state(BearingHead.PRIMARY).computerTargetAngleDeg);
            case "secondary_computer_target" -> AdvancedGraphDocument.Value.number(state(BearingHead.SECONDARY).computerTargetAngleDeg);
            case "primary_computer_override" -> AdvancedGraphDocument.Value.bool(hasHeadTargetOverride(BearingHead.PRIMARY));
            case "secondary_computer_override" -> AdvancedGraphDocument.Value.bool(hasHeadTargetOverride(BearingHead.SECONDARY));
            case "primary_min" -> AdvancedGraphDocument.Value.number(getMinAngle(BearingHead.PRIMARY));
            case "primary_max" -> AdvancedGraphDocument.Value.number(getMaxAngle(BearingHead.PRIMARY));
            case "secondary_min" -> AdvancedGraphDocument.Value.number(getMinAngle(BearingHead.SECONDARY));
            case "secondary_max" -> AdvancedGraphDocument.Value.number(getMaxAngle(BearingHead.SECONDARY));
            case "primary_assembled" -> AdvancedGraphDocument.Value.bool(isMountedAssemblyPresent(BearingHead.PRIMARY));
            case "secondary_assembled" -> AdvancedGraphDocument.Value.bool(isMountedAssemblyPresent(BearingHead.SECONDARY));
            case "primary_cw_signal" -> AdvancedGraphDocument.Value.number(getSignal(BearingHead.PRIMARY, ControlDirection.CW));
            case "primary_ccw_signal" -> AdvancedGraphDocument.Value.number(getSignal(BearingHead.PRIMARY, ControlDirection.CCW));
            case "secondary_cw_signal" -> AdvancedGraphDocument.Value.number(getSignal(BearingHead.SECONDARY, ControlDirection.CW));
            case "secondary_ccw_signal" -> AdvancedGraphDocument.Value.number(getSignal(BearingHead.SECONDARY, ControlDirection.CCW));
            case "head_mode" -> AdvancedGraphDocument.Value.string(getHeadMode().serializedName());
            case "control_mode" -> AdvancedGraphDocument.Value.string(getControlMode().serializedName());
            case "active_control_mode" -> AdvancedGraphDocument.Value.string(getActiveControlMode().serializedName());
            case "shaft_angle" -> AdvancedGraphDocument.Value.number(shaftAngleDegrees);
            case "shaft_speed" -> AdvancedGraphDocument.Value.number(getSpeed());
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value val) {
        try {
            switch (field) {
                case "primary_angle" -> setHeadTargetAngle(BearingHead.PRIMARY, val.asNumber());
                case "secondary_angle" -> setHeadTargetAngle(BearingHead.SECONDARY, val.asNumber());
                case "primary_target" -> setHeadTargetAngle(BearingHead.PRIMARY, val.asNumber());
                case "secondary_target" -> setHeadTargetAngle(BearingHead.SECONDARY, val.asNumber());
                case "primary_computer_target" -> setHeadTargetAngle(BearingHead.PRIMARY, val.asNumber());
                case "secondary_computer_target" -> setHeadTargetAngle(BearingHead.SECONDARY, val.asNumber());
                case "primary_computer_override" -> setHeadTargetOverrideActive(BearingHead.PRIMARY, val.asBoolean());
                case "secondary_computer_override" -> setHeadTargetOverrideActive(BearingHead.SECONDARY, val.asBoolean());
                case "primary_min" -> setAngleRange(BearingHead.PRIMARY, val.asNumber(), getMaxAngle(BearingHead.PRIMARY));
                case "primary_max" -> setAngleRange(BearingHead.PRIMARY, getMinAngle(BearingHead.PRIMARY), val.asNumber());
                case "secondary_min" -> setAngleRange(BearingHead.SECONDARY, val.asNumber(), getMaxAngle(BearingHead.SECONDARY));
                case "secondary_max" -> setAngleRange(BearingHead.SECONDARY, getMinAngle(BearingHead.SECONDARY), val.asNumber());
                case "head_mode" -> setHeadMode(HeadMode.byName(val.asString(), getHeadMode()));
                case "control_mode" -> setControlMode(ControlMode.byName(val.asString(), getControlMode()));
                default -> {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    // Set the head target override active
    private void setHeadTargetOverrideActive(BearingHead head, boolean active) {
        if (active) {
            HeadState state = state(head);
            double target = state.computerOverrideActive ? state.computerTargetAngleDeg : state.targetAngleDeg;
            setHeadTargetAngle(head, target);
        } else {
            clearHeadTargetOverride(head);
        }
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AileronBearingMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.aileron_bearing.config.title");
    }

    // Check if the player can use this
    public boolean canPlayerUse(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        MenuOpenHeader.encode(buffer, worldPosition, SimulatedHelper.getContainingSubLevelId(this));
        for (BearingHead head : BearingHead.values()) {
            buffer.writeDouble(getMinAngle(head));
            buffer.writeDouble(getMaxAngle(head));
        }
        for (BearingHead head : BearingHead.values()) {
            for (ControlDirection dir : ControlDirection.values()) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, getFrequencyFirst(head, dir));
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, getFrequencySecond(head, dir));
            }
        }
    }

    // Write the aileron bearing safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            CompoundTag headTag = new CompoundTag();
            headTag.putDouble("MinAngleDeg", state.minAngleDeg);
            headTag.putDouble("MaxAngleDeg", state.maxAngleDeg);
            for (ControlDirection dir : ControlDirection.values()) {
                headTag.put(frequencyKey(dir), state.frequencyBindings.get(dir).toTag(provider));
            }
            tag.put(head.serializedName(), headTag);
        }
    }

    // Write the aileron bearing
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putString("ActiveControlMode", activeControlMode.name());
        tag.putDouble("ShaftAngleDegrees", shaftAngleDegrees);
        tag.putBoolean("ShaftAngleInitialized", shaftAngleInitialized);
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        for (BearingHead head : BearingHead.values()) {
            HeadState state = state(head);
            CompoundTag headTag = new CompoundTag();
            headTag.putDouble("CurrentAngleDeg", state.currentAngleDeg);
            headTag.putDouble("TargetAngleDeg", state.targetAngleDeg);
            headTag.putDouble("ComputerTargetAngleDeg", state.computerTargetAngleDeg);
            headTag.putBoolean("ComputerOverrideActive", state.computerOverrideActive);
            headTag.putDouble("MinAngleDeg", state.minAngleDeg);
            headTag.putDouble("MaxAngleDeg", state.maxAngleDeg);
            headTag.putInt("CwSignal", state.cwSignal);
            headTag.putInt("CcwSignal", state.ccwSignal);
            UUID mountedId = state.mountedSubLevelId;
            BlockPos mountedPos = state.mountedLocalPos;
            if (mountedId != null && schematicContext != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping =
                        schematicContext.getMapping(mountedId);
                if (mapping != null) {
                    mountedId = mapping.newUUID();
                    mountedPos = mountedPos == null ? null : mapping.transform().apply(mountedPos);
                } else if (schematicContext.getType()
                        == SubLevelSchematicSerializationContext.Type.SAVE) {
                    mountedId = null;
                    mountedPos = null;
                }
            }
            if (mountedId != null) {
                headTag.putUUID("MountedSubLevel", mountedId);
            }
            if (mountedPos != null) {
                headTag.putLong("MountedLocalPos", mountedPos.asLong());
            }
            headTag.putBoolean("MountedAssemblyPresent",
                    state.mountedAssemblyPresent && mountedId != null && mountedPos != null);
            for (ControlDirection dir : ControlDirection.values()) {
                FrequencyBinding binding = state.frequencyBindings.get(dir);
                if (binding.isBound()) {
                    headTag.put(frequencyKey(dir), binding.toTag(provider));
                }
            }
            tag.put(head.serializedName(), headTag);
        }
    }

    // Read the aileron bearing
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        activeControlMode = readEnum(tag, "ActiveControlMode", ControlMode.AUTO);
        shaftAngleDegrees = tag.getDouble("ShaftAngleDegrees");
        shaftAngleInitialized = tag.getBoolean("ShaftAngleInitialized");
        lastShaftSampleTick = Long.MIN_VALUE;
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        for (BearingHead head : BearingHead.values()) {
            if (!tag.contains(head.serializedName(), Tag.TAG_COMPOUND)) {
                continue;
            }
            HeadState state = state(head);
            CompoundTag headTag = tag.getCompound(head.serializedName());
            state.currentAngleDeg = headTag.getDouble("CurrentAngleDeg");
            state.targetAngleDeg = headTag.getDouble("TargetAngleDeg");
            state.computerTargetAngleDeg = headTag.getDouble("ComputerTargetAngleDeg");
            state.computerOverrideActive = headTag.getBoolean("ComputerOverrideActive");
            state.minAngleDeg = headTag.contains("MinAngleDeg") ? headTag.getDouble("MinAngleDeg") : -DEFAULT_LIMIT_DEGREES;
            state.maxAngleDeg = headTag.contains("MaxAngleDeg") ? headTag.getDouble("MaxAngleDeg") : DEFAULT_LIMIT_DEGREES;
            state.cwSignal = headTag.getInt("CwSignal");
            state.ccwSignal = headTag.getInt("CcwSignal");
            state.mountedSubLevelId = headTag.hasUUID("MountedSubLevel") ? headTag.getUUID("MountedSubLevel") : null;
            state.mountedLocalPos = headTag.contains("MountedLocalPos") ? BlockPos.of(headTag.getLong("MountedLocalPos")) : null;
            if (state.mountedSubLevelId != null
                    && schematicContext != null
                    && schematicContext.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping =
                        schematicContext.getMapping(state.mountedSubLevelId);
                if (mapping != null) {
                    state.mountedSubLevelId = mapping.newUUID();
                    state.mountedLocalPos = state.mountedLocalPos == null
                            ? null
                            : mapping.transform().apply(state.mountedLocalPos);
                }
            }
            state.mountedAssemblyPresent = state.mountedSubLevelId != null && state.mountedLocalPos != null
                    && (!headTag.contains("MountedAssemblyPresent")
                    || headTag.getBoolean("MountedAssemblyPresent"));
            for (ControlDirection dir : ControlDirection.values()) {
                FrequencyBinding binding = state.frequencyBindings.get(dir);
                if (headTag.contains(frequencyKey(dir))) {
                    binding.read(headTag.getCompound(frequencyKey(dir)), provider);
                } else {
                    binding.clear();
                }
            }
            if (!clientPacket || !state.renderedAngleInitialized) {
                state.previousRenderedAngleDeg = state.currentAngleDeg;
                state.renderedAngleDeg = state.currentAngleDeg;
                state.renderedAngleInitialized = true;
            }
        }
    }

    // Read the enum
    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, E fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // Create the render bounding box
    @Override
    public AABB createRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0D);
    }

    // Destroy the aileron bearing
    @Override
    public void destroy() {
        if (assemblyTransferInProgress) {
            pendingAssemblyRequests.clear();
            for (BearingHead head : BearingHead.values()) {
                state(head).assembly.releaseJoint();
            }
            super.destroy();
            return;
        }
        for (BearingHead head : BearingHead.values()) {
            try {
                disassembleMountedBlock(head);
            } catch (RuntimeException | LinkageError ignored) {
                state(head).assembly.releaseJoint();
            }
        }
        super.destroy();
    }

    // Get the connection dependencies
    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        java.util.ArrayList<SubLevel> dependencies = new java.util.ArrayList<>(2);
        for (BearingHead head : BearingHead.values()) {
            UUID id = getMountedSubLevelId(head);
            SubLevel subLevel = id == null ? null : container.getSubLevel(id);
            if (subLevel != null && !subLevel.isRemoved()) {
                dependencies.add(subLevel);
            }
        }
        return dependencies.isEmpty() ? null : dependencies;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.aileron_bearing")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.aileron_bearing.head_mode"),
                CTTooltipHelper.value(getHeadMode().serializedName(), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.aileron_bearing.control_mode"),
                CTTooltipHelper.value(getControlMode().serializedName(), ChatFormatting.GOLD)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.aileron_bearing.active"),
                CTTooltipHelper.value(activeControlMode.serializedName(), ChatFormatting.GREEN)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.aileron_bearing.primary"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f deg", getHeadAngle(BearingHead.PRIMARY)),
                        ChatFormatting.DARK_AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.aileron_bearing.secondary"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f deg", getHeadAngle(BearingHead.SECONDARY)),
                        ChatFormatting.GOLD)));
        return true;
    }

    // Get the state
    private HeadState state(BearingHead head) {
        return heads.get(head);
    }

    // Get the frequency key
    private static String frequencyKey(ControlDirection dir) {
        return "Frequency" + dir.name();
    }

    // Copy one item
    private static ItemStack copySingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // Define the control direction values
    public enum ControlDirection {
        CW,
        CCW;

        // Get the serialized name
        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // Define the head mode values
    public enum HeadMode implements INamedIconOptions {
        SINGLE(AllIcons.I_TARGET, "single"),
        MIRRORED(AllIcons.I_REFRESH, "mirrored"),
        OPPOSED(AllIcons.I_ROTATE_CCW, "opposed"),
        PRECISE(AllIcons.I_MOVE_GAUGE, "precise"),
        FREE_SINGLE(AllIcons.I_PLAY, "free_single"),
        FREE_MIRRORED(AllIcons.I_SEND_AND_RECEIVE, "free_mirrored"),
        FREE_OPPOSED(AllIcons.I_ROTATE_CCW, "free_opposed");

        // Icon
        private final AllIcons icon;
        // Serialized name
        private final String serializedName;

        // Initialize the head mode
        HeadMode(AllIcons icon, String serializedName) {
            this.icon = icon;
            this.serializedName = serializedName;
        }

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return icon;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.aileron_bearing.head_mode." + serializedName;
        }

        // Get the serialized name
        public String serializedName() {
            return serializedName;
        }

        // Get the head mode by name
        public static HeadMode byName(String name, HeadMode fallback) {
            if (name == null || name.isBlank()) {
                return fallback;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (HeadMode mode : values()) {
                if (mode.serializedName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
            return fallback;
        }
    }

    // Define the control mode values
    public enum ControlMode implements INamedIconOptions {
        REDSTONE(AllIcons.I_ACTIVE, "redstone"),
        SERVO(AllIcons.I_ROTATE_CCW, "servo"),
        COMPUTER(AllIcons.I_MTD_USER_MODE, "computer"),
        AUTO(AllIcons.I_PLAY, "auto");

        // Icon
        private final AllIcons icon;
        // Serialized name
        private final String serializedName;

        // Initialize the control mode
        ControlMode(AllIcons icon, String serializedName) {
            this.icon = icon;
            this.serializedName = serializedName;
        }

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return icon;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.aileron_bearing.control_mode." + serializedName;
        }

        // Get the serialized name
        public String serializedName() {
            return serializedName;
        }

        // Get the control mode by name
        public static ControlMode byName(String name, ControlMode fallback) {
            if (name == null || name.isBlank()) {
                return fallback;
            }
            String normalized = name.trim().toLowerCase(Locale.ROOT);
            for (ControlMode mode : values()) {
                if (mode.serializedName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                    return mode;
                }
            }
            return fallback;
        }
    }

    // Store head state
    private static final class HeadState {
        // Assembly
        final AileronBearingMountedAssembly assembly;
        // Tracked frequency bindings
        final EnumMap<ControlDirection, FrequencyBinding> frequencyBindings = new EnumMap<>(ControlDirection.class);
        // Min angle in degrees
        double minAngleDeg = -DEFAULT_LIMIT_DEGREES;
        // Max angle in degrees
        double maxAngleDeg = DEFAULT_LIMIT_DEGREES;
        // Current angle in degrees
        double currentAngleDeg;
        // Target angle in degrees
        double targetAngleDeg;
        // Computer target angle in degrees
        double computerTargetAngleDeg;
        // Tracks whether computer override is active
        boolean computerOverrideActive;
        // Last computer input tick
        long lastComputerInputTick = Long.MIN_VALUE;
        // Previous rendered angle in degrees
        double previousRenderedAngleDeg;
        // Rendered angle in degrees
        double renderedAngleDeg;
        // Tracks whether rendered angle is initialized
        boolean renderedAngleInitialized;
        // Current cw signal
        int cwSignal;
        // Current ccw signal
        int ccwSignal;
        // Mounted sub-level id
        UUID mountedSubLevelId;
        // Mounted local pos
        BlockPos mountedLocalPos;
        // Tracks whether mounted assembly is present
        boolean mountedAssemblyPresent;
        // Pending placed block position
        BlockPos pendingPlacedBlockPos;

        // Initialize the head state
        HeadState(BearingHead head) {
            assembly = new AileronBearingMountedAssembly(head);
            for (ControlDirection dir : ControlDirection.values()) {
                frequencyBindings.put(dir,
                        new FrequencyBinding("aileron_bearing_" + head.serializedName() + "_" + dir.serializedName()));
            }
        }
    }

    // Handle the head mode behaviour
    private static class HeadModeBehaviour extends ScrollOptionBehaviour<HeadMode> {
        private static final BehaviourType<HeadModeBehaviour> TYPE = new BehaviourType<>();
        private static final String NBT_KEY = "AileronHeadMode";

        // Initialize the head mode behaviour
        HeadModeBehaviour(AileronBearingBlockEntity blockEntity, ValueBoxTransform transform) {
            super(HeadMode.class, Component.translatable("createthrusters.aileron_bearing.head_mode"),
                    blockEntity, transform);
        }

        // Get the type
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        // Get the net id
        @Override
        public int netId() {
            return 0;
        }

        // Write the head mode behaviour
        @Override
        public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            tag.putInt(NBT_KEY, value);
        }

        // Read the head mode behaviour
        @Override
        public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            value = tag.contains(NBT_KEY, Tag.TAG_INT)
                    ? Mth.clamp(tag.getInt(NBT_KEY), 0, HeadMode.values().length - 1)
                    : 0;
        }
    }

    // Handle the control mode behaviour
    private static class ControlModeBehaviour extends ScrollOptionBehaviour<ControlMode> {
        private static final BehaviourType<ControlModeBehaviour> TYPE = new BehaviourType<>();
        private static final String NBT_KEY = "AileronControlMode";

        // Initialize the control mode behaviour
        ControlModeBehaviour(AileronBearingBlockEntity blockEntity, ValueBoxTransform transform) {
            super(ControlMode.class, Component.translatable("createthrusters.aileron_bearing.control_mode"),
                    blockEntity, transform);
        }

        // Get the type
        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        // Get the net id
        @Override
        public int netId() {
            return 1;
        }

        // Write the control mode behaviour
        @Override
        public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            tag.putInt(NBT_KEY, value);
        }

        // Read the control mode behaviour
        @Override
        public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
            value = tag.contains(NBT_KEY, Tag.TAG_INT)
                    ? Mth.clamp(tag.getInt(NBT_KEY), 0, ControlMode.values().length - 1)
                    : ControlMode.AUTO.ordinal();
        }
    }

    // Handle the aileron value box transform
    private static class AileronValueBoxTransform extends CenteredSideValueBoxTransform {
        // Horizontal center
        private final double horizontalCenter;

        // Initialize the aileron value box transform
        AileronValueBoxTransform(double horizontalCenter) {
            super((state, side) -> {
                Direction.Axis shaftAxis = AileronBearingBlock.getShaftAxis(state);
                Direction.Axis facingAxis = state.hasProperty(BlockStateProperties.FACING)
                        ? state.getValue(BlockStateProperties.FACING).getAxis()
                        : Direction.Axis.Y;
                return side.getAxis() != shaftAxis && side.getAxis() != facingAxis;
            });
            this.horizontalCenter = horizontalCenter;
        }

        // Get the south location
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(horizontalCenter / 16.0D, 0.5D, 15.6D / 16.0D);
        }

        // Get the local offset
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Vec3 offset = super.getLocalOffset(level, pos, state);
            Direction facing = state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.UP;
            if (facing.getAxis() != Direction.Axis.X) {
                return offset;
            }
            return rotateOffsetAroundFace(offset, getSide(), facing == Direction.EAST ? 1.0D : -1.0D);
        }

        // Rotate the aileron value box transform
        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            super.rotate(level, pos, state, ms);
            Direction facing = state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.UP;
            float rotation = projectionRotation(facing, AileronBearingBlock.getShaftAxis(state));
            if (rotation != 0.0F) {
                ms.mulPose(Axis.ZP.rotationDegrees(rotation));
            }
        }

        // Rotate the offset around face
        private static Vec3 rotateOffsetAroundFace(Vec3 offset, Direction side, double rotationSign) {
            if (side.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                rotationSign = -rotationSign;
            }
            double x = offset.x - 0.5D;
            double y = offset.y - 0.5D;
            double z = offset.z - 0.5D;
            return switch (side.getAxis()) {
                case X -> new Vec3(offset.x, 0.5D - rotationSign * z, 0.5D + rotationSign * y);
                case Y -> new Vec3(0.5D + rotationSign * z, offset.y, 0.5D - rotationSign * x);
                case Z -> new Vec3(0.5D - rotationSign * y, 0.5D + rotationSign * x, offset.z);
            };
        }

        // Get the projection rotation
        private static float projectionRotation(Direction facing, Direction.Axis shaftAxis) {
            return switch (facing) {
                case EAST -> 90.0F;
                case SOUTH -> 90.0F;
                case WEST -> 270.0F;
                case NORTH -> 270.0F;
                case UP -> shaftAxis == Direction.Axis.X ? 90.0F : 0.0F;
                case DOWN -> shaftAxis == Direction.Axis.X ? 270.0F : 180.0F;
            };
        }

        // Get the scale
        @Override
        public float getScale() {
            return 0.4F;
        }
    }
}
