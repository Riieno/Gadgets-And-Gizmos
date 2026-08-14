package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import com.rieno.gadgetsandgizmos.compat.controller.ExternalBlockEntityDirectControlCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.ControllerDirectTargetReference;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSource;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Read and apply redstone through normal blocks, Sable ships and linked controller targets
public final class ControllerRedstoneCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String VIRTUAL_FACE_REDSTONE = "virtual_face_redstone";
    public static final String VIRTUAL_BLOCK_REDSTONE = "virtual_block_redstone";
    public static final String STATE_BACKED_DIRECTIONAL = "state_backed_directional";
    public static final String STATE_BACKED_BLOCK = "state_backed_block";
    public static final String DIRECT_ADAPTER = "direct_adapter";

    private static final String CREATE_ANALOG_LEVER_BLOCK_ID = "create:analog_lever";
    private static final String CREATE_ANALOGUE_LEVER_BLOCK_ID = "create:analogue_lever";
    private static final boolean DEBUG_LINKER_FACE_IO = Boolean.parseBoolean(
            System.getProperty("createthrusters.debug.linker_face_io", "false"));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller redstone compat
    private ControllerRedstoneCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Ensure the compat target
    public static @Nullable ControllerDirectTargetReference ensureCompatTarget(@Nullable Level ownerLevel,
                                                                               @Nullable ControllerDirectTargetReference target) {
        target = resolveMovingTarget(ownerLevel, target);
        if (target == null || !target.isBound() || !target.compatModeId().isBlank()) {
            return target;
        }
        if (ownerLevel == null || target.blockPos() == null
                || !SubLevelBlockEntityCollector.ensureTargetLoaded(
                ownerLevel, target.subLevelId(), target.blockPos())) {
            return target;
        }
        BlockEntity targetBe =
                SimulatedHelper.findLoadedBlockEntityExact(ownerLevel, target.subLevelId(), target.blockPos());
        Level targetLevel = resolveTargetLevel(ownerLevel, target, targetBe);
        BlockPos targetPos = resolveTargetPos(target, targetBe);
        if (targetLevel == null || targetPos == null) {
            return target;
        }
        BlockState state = targetLevel.getBlockState(targetPos);
        String compatMode = resolveCompatMode(target, targetBe, state);
        return compatMode.isBlank() ? target : target.withCompatMode(compatMode);
    }

    // Sample the target
    public static double sampleTarget(@Nullable Level ownerLevel, String channelId,
                                      @Nullable ControllerDirectTargetReference target) {
        // -----------------------------------------------------TARGET CHECKS-----------------------------------------------------
        if (ownerLevel == null || target == null || !target.isBound() || target.blockPos() == null) {
            return 0.0D;
        }
        // ------------------------------------TARGET RESOLUTION------------------------------------
        ControllerDirectTargetReference resolvedTarget = resolveMovingTarget(ownerLevel, target);
        if (resolvedTarget == null || resolvedTarget.blockPos() == null) {
            return 0.0D;
        }
        BlockEntity targetBe =
                SimulatedHelper.findLoadedBlockEntityExact(
                        ownerLevel, resolvedTarget.subLevelId(), resolvedTarget.blockPos());
        if (targetBe == null && !SubLevelBlockEntityCollector.isTargetLoaded(
                ownerLevel, resolvedTarget.subLevelId(), resolvedTarget.blockPos())) {
            return 0.0D;
        }
        Level targetLevel = resolveTargetLevel(ownerLevel, resolvedTarget, targetBe);
        BlockPos targetPos = resolveTargetPos(resolvedTarget, targetBe);
        if (targetLevel == null || targetPos == null) {
            return 0.0D;
        }
        if (resolvedTarget.compatModeId().isBlank()) {
            String compatMode = resolveCompatMode(
                    resolvedTarget, targetBe, targetLevel.getBlockState(targetPos));
            if (!compatMode.isBlank()) {
                resolvedTarget = resolvedTarget.withCompatMode(compatMode);
            }
        }
        // -----------------------------------------------------STATE SIGNALS-----------------------------------------------------
        String signalPropertyKey = resolveDirectSignalPropKey(resolvedTarget);
        if (signalPropertyKey != null && !signalPropertyKey.isBlank()) {
            Double propertyValue = readNamedSignalProperty(targetLevel.getBlockState(targetPos), signalPropertyKey);
            if (propertyValue != null) {
                return propertyValue;
            }
        }
        Direction planeFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(resolvedTarget);
        BlockState targetState = targetLevel.getBlockState(targetPos);
        // ------------------------------------LINKER SIGNALS------------------------------------
        if (ContraptionNetworkLinkerData.isLinkerFaceTarget(resolvedTarget) && planeFace != null
                && targetState.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock) {
            return Mth.clamp(samplePlaneInputSignal(
                    targetLevel, resolvedTarget.subLevelId(), targetPos, planeFace, targetBe) / 15.0D,
                    0.0D, 1.0D);
        }
        // ------------------------------------DIRECT ADAPTERS------------------------------------
        String compatMode = resolvedTarget.compatModeId();
        String optionChannelId = resolveDirectSignalChannelId(channelId, resolvedTarget);
        if (targetBe instanceof RcsThrusterBlockEntity rcsThruster) {
            Direction nozzle = RcsThrusterBlockEntity.nozzleFromChannel(optionChannelId);
            return nozzle == null ? 0.0D : rcsThruster.getThrottle(nozzle);
        }

        Double adapterSample = ExternalBlockEntityDirectControlCompat.sampleDirectSignal(targetBe);
        Double aeroworksSample = AeroworksControllerCompat.sampleDirectSignal(targetBe, optionChannelId);
        if (aeroworksSample != null) {
            return aeroworksSample;
        }
        if (DIRECT_ADAPTER.equals(compatMode) && adapterSample != null) {
            return adapterSample;
        }

        if (ContraptionNetworkLinkerData.isLinkerFaceTarget(resolvedTarget)) {
            Direction face = resolveOutputInjectionFace(resolvedTarget, targetLevel, targetPos);
            if (VIRTUAL_BLOCK_REDSTONE.equals(compatMode) || STATE_BACKED_BLOCK.equals(compatMode) || face == null) {
                Double createLeverValue = readCreateAnalogueLeverValue(targetLevel, targetPos, targetBe);
                return createLeverValue != null
                        ? createLeverValue
                        : blockTargetValue(targetLevel, targetPos);
            }
            return faceTargetValue(targetLevel, targetPos,
                    face, optionChannelId, signalPropertyKey);
        }

        if (targetBe instanceof DirectionalAnalogSource analogSource) {
            DirectionalAnalogSnapshot snapshot = analogSource.getDirectionalAnalogSnapshot();
            return snapshot == null ? 0.0D : directionalValueForChannel(optionChannelId, snapshot);
        }

        if (adapterSample != null) {
            return adapterSample;
        }
        if (targetBe == null) {
            return 0.0D;
        }
        return scalarTargetValue(targetBe);
    }

    // Sample the written target
    public static double sampleWrittenTarget(@Nullable Level ownerLevel,
                                             @Nullable ControllerDirectTargetReference target,
                                             String sourceId) {
        if (ownerLevel == null || target == null || !target.isBound()
                || target.blockPos() == null || sourceId == null || sourceId.isBlank()) {
            return 0.0D;
        }
        ControllerDirectTargetReference resolvedTarget = resolveMovingTarget(ownerLevel, target);
        if (resolvedTarget == null || resolvedTarget.blockPos() == null) {
            return 0.0D;
        }
        BlockEntity targetBe = SimulatedHelper.findLoadedBlockEntityExact(
                ownerLevel, resolvedTarget.subLevelId(), resolvedTarget.blockPos());
        Level targetLevel = resolveTargetLevel(ownerLevel, resolvedTarget, targetBe);
        BlockPos targetPos = resolveTargetPos(resolvedTarget, targetBe);
        if (targetLevel == null || targetPos == null) {
            return 0.0D;
        }
        if (resolvedTarget.compatModeId().isBlank()) {
            String compatMode = resolveCompatMode(
                    resolvedTarget, targetBe, targetLevel.getBlockState(targetPos));
            if (!compatMode.isBlank()) resolvedTarget = resolvedTarget.withCompatMode(compatMode);
        }
        Direction planeFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(resolvedTarget);
        if (ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(resolvedTarget) && planeFace != null) {
            return ContraptionNetworkLinkerSignalBus.sourcePlaneSignal(
                    targetLevel, resolvedTarget.subLevelId(), targetPos, planeFace, sourceId) / 15.0D;
        }
        String compatMode = resolvedTarget.compatModeId();
        if (DIRECT_ADAPTER.equals(compatMode) || targetBe instanceof RcsThrusterBlockEntity) {
            return sampleTarget(ownerLevel, sourceId, resolvedTarget);
        }
        Direction face;
        if (VIRTUAL_BLOCK_REDSTONE.equals(compatMode)) {
            face = null;
        } else if (STATE_BACKED_BLOCK.equals(compatMode)) {
            face = Direction.NORTH;
        } else {
            face = ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(resolvedTarget)
                    ? resolveOutputInjectionFace(resolvedTarget, targetLevel, targetPos)
                    : planeFace;
            if (face == null) face = Direction.NORTH;
        }
        return ContraptionNetworkLinkerSignalBus.sourceSignal(
                targetLevel, resolvedTarget.subLevelId(), targetPos, face, sourceId) / 15.0D;
    }

    // Write the target
    public static void writeTarget(@Nullable Level ownerLevel,
                                   @Nullable ControllerDirectTargetReference target,
                                   @Nullable BlockEntity targetBe,
                                   String sourceId,
                                   float value) {
        if (ownerLevel == null || target == null || !target.isBound() || target.blockPos() == null
                || sourceId == null || sourceId.isBlank()) {
            return;
        }
        // ------------------------------------TARGET RESOLUTION------------------------------------
        ControllerDirectTargetReference resolvedTarget = resolveMovingTarget(ownerLevel, target);
        if (resolvedTarget == null || resolvedTarget.blockPos() == null) {
            return;
        }
        BlockEntity resolvedTargetBe = resolvedTarget.equals(target) && targetBe != null ? targetBe
                : SimulatedHelper.findLoadedBlockEntityExact(
                        ownerLevel, resolvedTarget.subLevelId(), resolvedTarget.blockPos());
        if (resolvedTargetBe == null && !SubLevelBlockEntityCollector.isTargetLoaded(
                ownerLevel, resolvedTarget.subLevelId(), resolvedTarget.blockPos())) {
            return;
        }
        Level targetLevel = resolveTargetLevel(ownerLevel, resolvedTarget, resolvedTargetBe);
        BlockPos targetPos = resolveTargetPos(resolvedTarget, resolvedTargetBe);
        if (targetLevel == null || targetPos == null) {
            return;
        }
        if (resolvedTarget.compatModeId().isBlank()) {
            String compatMode = resolveCompatMode(
                    resolvedTarget, resolvedTargetBe, targetLevel.getBlockState(targetPos));
            if (!compatMode.isBlank()) {
                resolvedTarget = resolvedTarget.withCompatMode(compatMode);
            }
        }

        // ------------------------------------DIRECT ADAPTERS------------------------------------
        String optionChannelId = resolveDirectSignalChannelId(sourceId, resolvedTarget);
        // -----------------------------------------------------RCS CHANNELS-----------------------------------------------------
        if (resolvedTargetBe instanceof RcsThrusterBlockEntity rcsThruster) {
            Direction nozzle = RcsThrusterBlockEntity.nozzleFromChannel(optionChannelId);
            if (nozzle != null) {
                rcsThruster.setControllerThrottle(nozzle, sourceId, value);
            }
            return;
        }

        Direction planeFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(resolvedTarget);
        int strength = Mth.clamp(Mth.ceil(value * 15.0f), 0, 15);
        if (ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(resolvedTarget) && planeFace != null) {
            ContraptionNetworkLinkerSignalBus.setPlaneSignal(
                    targetLevel,
                    resolvedTarget.subLevelId(),
                    targetPos,
                    planeFace,
                    sourceId,
                    strength);
            return;
        }

        // -----------------------------------------------------COMPAT MODES-----------------------------------------------------
        String compatMode = resolvedTarget.compatModeId();
        if (DIRECT_ADAPTER.equals(compatMode)
                && AeroworksControllerCompat.applyDirectSignal(resolvedTargetBe, optionChannelId, sourceId, value)) {
            return;
        }
        if (DIRECT_ADAPTER.equals(compatMode)
                && ExternalBlockEntityDirectControlCompat.applyDirectSignal(resolvedTargetBe, optionChannelId, value)) {
            return;
        }

        // ------------------------------------VIRTUAL REDSTONE------------------------------------
        String propertyKey = resolveDirectSignalPropKey(resolvedTarget);
        boolean forceInjected = VIRTUAL_FACE_REDSTONE.equals(compatMode) || VIRTUAL_BLOCK_REDSTONE.equals(compatMode);

        if (VIRTUAL_BLOCK_REDSTONE.equals(compatMode)) {
            ContraptionNetworkLinkerSignalBus.setBlockSignal(
                    targetLevel,
                    resolvedTarget.subLevelId(),
                    targetPos,
                    sourceId,
                    strength);
            return;
        }

        if (STATE_BACKED_BLOCK.equals(compatMode)) {
            ContraptionNetworkLinkerSignalBus.setSignal(
                    targetLevel,
                    resolvedTarget.subLevelId(),
                    targetPos,
                    Direction.NORTH,
                    sourceId,
                    strength,
                    propertyKey,
                    false);
            return;
        }

        Direction face = ContraptionNetworkLinkerData.isLinkerFaceOutputTarget(resolvedTarget)
                ? resolveOutputInjectionFace(resolvedTarget, targetLevel, targetPos)
                : ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(resolvedTarget);
        if (face == null) {
            face = Direction.NORTH;
        }
        ContraptionNetworkLinkerSignalBus.setSignal(
                targetLevel,
                resolvedTarget.subLevelId(),
                targetPos,
                face,
                sourceId,
                strength,
                propertyKey,
                forceInjected);
    }

    // Clear the source
    public static void clearSource(@Nullable Level ownerLevel, String sourceId) {
        if (ownerLevel == null || sourceId == null || sourceId.isBlank()) {
            return;
        }
        ContraptionNetworkLinkerPlaneBlockEntity.clearSource(ownerLevel, sourceId);
        ContraptionNetworkLinkerSignalBus.clearSource(ownerLevel, sourceId);
        AeroworksControllerCompat.clearSource(sourceId);
    }

    // Resolve the compat mode
    private static String resolveCompatMode(@Nullable ControllerDirectTargetReference target,
                                            @Nullable BlockEntity targetBe,
                                            @Nullable BlockState state) {
        if (target == null || !target.isBound()) {
            return "";
        }
        if (targetBe instanceof RcsThrusterBlockEntity) {
            return DIRECT_ADAPTER;
        }
        String propertyKey = resolveDirectSignalPropKey(target);
        if (propertyKey != null && !propertyKey.isBlank()) {
            return STATE_BACKED_DIRECTIONAL;
        }

        if (ExternalBlockEntityDirectControlCompat.sampleDirectSignal(targetBe) != null
                && !ContraptionNetworkLinkerData.isLinkerFaceTarget(target)) {
            return DIRECT_ADAPTER;
        }
        if (AeroworksControllerCompat.isDirectAdapter(targetBe)) {
            return DIRECT_ADAPTER;
        }

        if (ContraptionNetworkLinkerData.isLinkerFaceTarget(target)) {
            return ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(target) == null
                    ? VIRTUAL_BLOCK_REDSTONE
                    : VIRTUAL_FACE_REDSTONE;
        }

        if (state != null && stateBackedWithoutDirectionalProp(state)) {
            return STATE_BACKED_BLOCK;
        }

        return VIRTUAL_BLOCK_REDSTONE;
    }

    // Check if the block state lacks a directional property
    private static boolean stateBackedWithoutDirectionalProp(BlockState state) {
        if (state == null) {
            return false;
        }
        return (state.hasProperty(BlockStateProperties.POWERED)
                || state.hasProperty(BlockStateProperties.LIT)
                || state.hasProperty(BlockStateProperties.POWER))
                && !state.isSignalSource()
                && !state.hasAnalogOutputSignal();
    }

    // Resolve the direct signal prop key
    private static @Nullable String resolveDirectSignalPropKey(@Nullable ControllerDirectTargetReference directTarget) {
        if (directTarget == null || directTarget.targetId() == null) {
            return null;
        }
        String targetId = directTarget.targetId();
        int propertyIndex = targetId.indexOf("::prop:");
        if (propertyIndex < 0) {
            return null;
        }
        String key = targetId.substring(propertyIndex + "::prop:".length()).trim().toLowerCase(Locale.ROOT);
        return key.isBlank() ? null : key;
    }

    // Resolve the direct signal channel id
    private static String resolveDirectSignalChannelId(String fallbackChannelId,
                                                       @Nullable ControllerDirectTargetReference directTarget) {
        if (directTarget == null || directTarget.targetId() == null) {
            return fallbackChannelId;
        }
        String targetId = directTarget.targetId();
        int optionIndex = targetId.indexOf("::opt:");
        if (optionIndex < 0) {
            return fallbackChannelId;
        }
        String optionChannelId = targetId.substring(optionIndex + "::opt:".length());
        int propertyIndex = optionChannelId.indexOf("::prop:");
        if (propertyIndex >= 0) {
            optionChannelId = optionChannelId.substring(0, propertyIndex);
        }
        optionChannelId = optionChannelId.trim().toLowerCase(Locale.ROOT);
        return optionChannelId.isEmpty() ? fallbackChannelId : optionChannelId;
    }

    // Resolve the output injection face
    private static @Nullable Direction resolveOutputInjectionFace(@Nullable ControllerDirectTargetReference directTarget,
                                                                  @Nullable Level targetLevel,
                                                                  @Nullable BlockPos targetPos) {
        Direction mappedFace = ContraptionNetworkLinkerData.resolveFaceFromDirectTarget(directTarget);
        if (directTarget == null || targetLevel == null || targetPos == null) {
            return mappedFace;
        }
        String signalPropertyKey = resolveDirectSignalPropKey(directTarget);
        if (signalPropertyKey == null || signalPropertyKey.isBlank()) {
            return mappedFace;
        }
        BlockState targetState = targetLevel.getBlockState(targetPos);
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(targetState.getBlock()));
        if ("aeroworks:stepper_servo".equals(blockId)) {
            Direction orientation = directionProperty(targetState, "orientation");
            if (orientation == null) {
                return mappedFace;
            }
            if ("left_signal".equals(signalPropertyKey)) {
                return orientation.getCounterClockWise();
            }
            if ("right_signal".equals(signalPropertyKey)) {
                return orientation.getClockWise();
            }
            return mappedFace;
        }
        if (!"simulated:directional_gearshift".equals(blockId) || !targetState.hasProperty(BlockStateProperties.FACING)) {
            return mappedFace;
        }
        Direction facing = targetState.getValue(BlockStateProperties.FACING);
        if ("left_powered".equals(signalPropertyKey)) {
            return facing;
        }
        if ("right_powered".equals(signalPropertyKey)) {
            return facing.getOpposite();
        }
        return mappedFace;
    }

    // Resolve the moving target
    public static @Nullable ControllerDirectTargetReference resolveMovingTarget(@Nullable Level ownerLevel,
                                                                                 @Nullable ControllerDirectTargetReference target) {
        if (ownerLevel == null || target == null || !target.isBound() || ownerLevel.getServer() == null) {
            return target;
        }
        return ContraptionNetworkLinkerTracker.get(ownerLevel.getServer()).resolveTargetReference(ownerLevel, target);
    }

    // Get the direction property
    private static @Nullable Direction directionProperty(BlockState state, String propertyName) {
        for (var property : state.getProperties()) {
            if (property.getName().equals(propertyName) && state.getValue(property) instanceof Direction dir) {
                return dir;
            }
        }
        return null;
    }

    // Get the directional value for channel
    private static double directionalValueForChannel(String channelId, DirectionalAnalogSnapshot snapshot) {
        AnalogueControlChannel channel = AnalogueControlChannel.byId(channelId);
        if (channel == null) {
            return 0.0D;
        }
        return switch (channel) {
            case PITCH_UP, THROTTLE_UP, LIFT_UP -> snapshot.forward();
            case PITCH_DOWN, THROTTLE_DOWN, LIFT_DOWN -> snapshot.backward();
            case ROLL_LEFT, YAW_LEFT, STRAFE_LEFT -> snapshot.left();
            case ROLL_RIGHT, YAW_RIGHT, STRAFE_RIGHT -> snapshot.right();
            case STABILIZE -> Mth.clamp(
                    Math.max(
                            Math.max(snapshot.forward(), snapshot.backward()),
                            Math.max(snapshot.left(), snapshot.right())),
                    0.0D,
                    1.0D);
        };
    }

    // Get the scalar target value
    private static double scalarTargetValue(BlockEntity target) {
        Object val = invokeNoArgNumber(target, "getState");
        if (!(val instanceof Number)) {
            val = invokeNoArgNumber(target, "getSignal");
        }
        if (!(val instanceof Number)) {
            val = readNumberField(target, "state");
        }
        return val instanceof Number num ? Mth.clamp(num.doubleValue() / 15.0D, 0.0D, 1.0D) : 0.0D;
    }

    // Get the face target value
    private static double faceTargetValue(@Nullable Level targetLevel,
                                          @Nullable BlockPos targetPos,
                                          Direction face,
                                          @Nullable String optionChannelId,
                                          @Nullable String signalPropertyKey) {
        if (targetLevel == null || targetPos == null || face == null) {
            return 0.0D;
        }
        BlockEntity targetBlockEntity = targetLevel.getBlockEntity(targetPos);
        if (SimulatedHelper.isGimbalSensor(targetBlockEntity)) {
            return SimulatedHelper.getGimbalPower(targetBlockEntity, face) / 15.0D;
        }
        Double directionalValue = readDirectionalPropValue(targetLevel.getBlockState(targetPos),
                optionChannelId, signalPropertyKey);
        if (directionalValue != null) {
            if (DEBUG_LINKER_FACE_IO) {
                Create.LOGGER.info("[CT-LinkerFaceIO] compat-read-property pos={} face={} option={} prop={} value={}",
                        targetPos, face, optionChannelId, signalPropertyKey, directionalValue);
            }
            return directionalValue;
        }
        BlockState targetState = targetLevel.getBlockState(targetPos);
        int signal = sampleFaceOutputSignal(targetLevel, targetPos, targetState, face);
        signal = Math.max(signal, targetLevel.getSignal(targetPos.relative(face), face));
        signal = Math.max(signal, ContraptionNetworkLinkerSignalBus.getInjectedSignal(targetLevel, targetPos, face));
        return Mth.clamp(signal / 15.0D, 0.0D, 1.0D);
    }

    // Get the block target value
    private static double blockTargetValue(@Nullable Level targetLevel, @Nullable BlockPos targetPos) {
        if (targetLevel == null || targetPos == null) {
            return 0.0D;
        }
        BlockState state = targetLevel.getBlockState(targetPos);
        int maxSignal = 0;
        for (Direction dir : Direction.values()) {
            maxSignal = Math.max(maxSignal, state.getSignal(targetLevel, targetPos, dir));
            maxSignal = Math.max(maxSignal, state.getDirectSignal(targetLevel, targetPos, dir));
        }
        maxSignal = Math.max(maxSignal, ContraptionNetworkLinkerSignalBus.getBestNeighborSignal(targetLevel, targetPos));
        return Mth.clamp(maxSignal / 15.0D, 0.0D, 1.0D);
    }

    // Sample the plane input signal
    private static int samplePlaneInputSignal(@Nullable Level targetLevel,
                                              @Nullable UUID subLevelId,
                                              @Nullable BlockPos planePos,
                                              @Nullable Direction planeFace,
                                              @Nullable BlockEntity planeEntity) {
        if (targetLevel == null || planePos == null || planeFace == null) {
            return 0;
        }
        if (planeEntity instanceof ContraptionNetworkLinkerPlaneBlockEntity plane) {
            return plane.sampleInputSignal(planeFace);
        }
        BlockPos attachedPos = planePos.relative(planeFace.getOpposite());
        if (!SubLevelBlockEntityCollector.isTargetLoaded(targetLevel, subLevelId, attachedPos)) {
            return 0;
        }
        BlockState attachedState = targetLevel.getBlockState(attachedPos);
        if (attachedState.isAir()) {
            return 0;
        }
        return sampleFaceOutputSignal(targetLevel, attachedPos, attachedState, planeFace);
    }

    // Sample the face output signal
    static int sampleFaceOutputSignal(@Nullable Level level,
                                      @Nullable BlockPos sourcePos,
                                      @Nullable BlockState sourceState,
                                      @Nullable Direction outputFace) {
        if (level == null || sourcePos == null || sourceState == null || outputFace == null) {
            return 0;
        }
        Direction queryDirection = redstoneQueryDirectionForOutputFace(outputFace);
        int signal = Math.max(sourceState.getSignal(level, sourcePos, queryDirection),
                sourceState.getDirectSignal(level, sourcePos, queryDirection));
        return Mth.clamp(signal, 0, 15);
    }

    // Get the redstone query direction for output face
    static Direction redstoneQueryDirectionForOutputFace(Direction outputFace) {
        return outputFace.getOpposite();
    }

    // Read the create analogue lever value
    private static @Nullable Double readCreateAnalogueLeverValue(@Nullable Level targetLevel,
                                                                 @Nullable BlockPos targetPos,
                                                                 @Nullable BlockEntity target) {
        if (targetLevel == null || targetPos == null) {
            return null;
        }
        String blockId = String.valueOf(BuiltInRegistries.BLOCK.getKey(targetLevel.getBlockState(targetPos).getBlock()));
        if (!CREATE_ANALOG_LEVER_BLOCK_ID.equals(blockId) && !CREATE_ANALOGUE_LEVER_BLOCK_ID.equals(blockId)) {
            return null;
        }
        if (target != null) {
            Object stateValue = invokeNoArgNumber(target, "getState");
            if (stateValue instanceof Number num) {
                return Mth.clamp(num.doubleValue() / 15.0D, 0.0D, 1.0D);
            }
            stateValue = invokeNoArgNumber(target, "getSignal");
            if (stateValue instanceof Number num) {
                return Mth.clamp(num.doubleValue() / 15.0D, 0.0D, 1.0D);
            }
        }
        BlockState state = targetLevel.getBlockState(targetPos);
        if (state.hasProperty(BlockStateProperties.POWER)) {
            return Mth.clamp(state.getValue(BlockStateProperties.POWER) / 15.0D, 0.0D, 1.0D);
        }
        return null;
    }

    // Read the directional prop value
    private static @Nullable Double readDirectionalPropValue(BlockState state,
                                                                 @Nullable String optionChannelId,
                                                                 @Nullable String signalPropertyKey) {
        if (state == null) {
            return null;
        }
        if (signalPropertyKey != null && !signalPropertyKey.isBlank()) {
            Double exactValue = readNamedSignalProperty(state, signalPropertyKey);
            if (exactValue != null) {
                return exactValue;
            }
        }
        if (optionChannelId == null || optionChannelId.isBlank()) {
            return null;
        }
        List<String> tokens = directionalTokensForOptionChannel(optionChannelId);
        if (tokens.isEmpty()) {
            return null;
        }
        Double best = null;
        for (var property : state.getProperties()) {
            String propertyName = property.getName();
            if (!matchesDirectionalToken(propertyName, tokens)) {
                continue;
            }
            String normalized = propertyName.toLowerCase(Locale.ROOT);
            if (property instanceof BooleanProperty booleanProperty
                    && (normalized.contains("power")
                    || normalized.contains("lit")
                    || normalized.contains("open")
                    || normalized.contains("active")
                    || normalized.contains("enable")
                    || normalized.endsWith("on"))) {
                double val = state.getValue(booleanProperty) ? 1.0D : 0.0D;
                best = best == null ? val : Math.max(best, val);
                continue;
            }
            if (property instanceof IntegerProperty integerProperty
                    && (normalized.contains("power")
                    || normalized.contains("signal")
                    || normalized.contains("strength"))) {
                int raw = state.getValue(integerProperty);
                int min = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
                int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
                double scaled = max <= min ? (raw > min ? 1.0D : 0.0D)
                        : Mth.clamp((raw - min) / (double) (max - min), 0.0D, 1.0D);
                best = best == null ? scaled : Math.max(best, scaled);
            }
        }
        return best;
    }

    // Read the named signal property
    private static @Nullable Double readNamedSignalProperty(BlockState state, String propertyName) {
        String normalizedName = propertyName == null ? "" : propertyName.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.isBlank()) {
            return null;
        }
        for (var property : state.getProperties()) {
            if (!normalizedName.equals(property.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (property instanceof BooleanProperty booleanProperty) {
                return state.getValue(booleanProperty) ? 1.0D : 0.0D;
            }
            if (property instanceof IntegerProperty integerProperty) {
                int raw = state.getValue(integerProperty);
                int min = integerProperty.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
                int max = integerProperty.getPossibleValues().stream().max(Integer::compareTo).orElse(15);
                if (max <= min) {
                    return raw > min ? 1.0D : 0.0D;
                }
                return Mth.clamp((raw - min) / (double) (max - min), 0.0D, 1.0D);
            }
            return null;
        }
        return null;
    }

    // Get the directional tokens for option channel
    private static List<String> directionalTokensForOptionChannel(String optionChannelId) {
        return switch (optionChannelId == null ? "" : optionChannelId.toLowerCase(Locale.ROOT)) {
            case "yaw_left", "roll_left", "strafe_left" -> List.of("left", "west");
            case "yaw_right", "roll_right", "strafe_right" -> List.of("right", "east");
            case "pitch_up", "throttle_up", "lift_up" -> List.of("up", "north", "forward", "front");
            case "pitch_down", "throttle_down", "lift_down" -> List.of("down", "south", "back", "backward", "rear");
            default -> List.of();
        };
    }

    // Check if this matches directional token
    private static boolean matchesDirectionalToken(String propertyName, List<String> tokens) {
        if (propertyName == null || tokens == null || tokens.isEmpty()) {
            return false;
        }
        String normalized = propertyName.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    // Invoke a numeric method without arguments
    private static @Nullable Object invokeNoArgNumber(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Read the number field
    private static @Nullable Number readNumberField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(target);
            return val instanceof Number num ? num : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Resolve the target level
    private static @Nullable Level resolveTargetLevel(@Nullable Level ownerLevel,
                                                      @Nullable ControllerDirectTargetReference target,
                                                      @Nullable BlockEntity targetBlockEntity) {
        if (targetBlockEntity != null && targetBlockEntity.getLevel() != null) {
            return targetBlockEntity.getLevel();
        }

        return ownerLevel;
    }

    // Resolve the target pos
    private static @Nullable BlockPos resolveTargetPos(@Nullable ControllerDirectTargetReference target,
                                                       @Nullable BlockEntity targetBlockEntity) {
        if (targetBlockEntity != null) {
            return targetBlockEntity.getBlockPos();
        }
        return target == null ? null : target.blockPos();
    }
}
