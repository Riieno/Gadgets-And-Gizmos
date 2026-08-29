package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedPreciseAngleCompat;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.control.AnalogueControlChannel;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.lib.control.OrientationTarget;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader;
import com.rieno.gadgetsandgizmos.lib.physics.MountedAssemblyStatus;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import com.mojang.logging.LogUtils;
import com.simibubi.create.AllTags;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.eriksonn.aeronautics.config.AeroConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Control two-axis bearing movement and expose the same target to linked and graph controls
public class VectorBearingBlockEntity extends KineticBlockEntity implements MenuProvider, OrientationTarget,
        IDirectControlReceiver, AdvancedGraphDataProvider, IHaveGoggleInformation,
        BlockEntitySubLevelActor, BlockEntityPropeller {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double DEFAULT_MAX_TILT_DEGREES = 30.0D;
    private static final double MIN_MAX_TILT_DEGREES = 0.0D;
    private static final double MAX_MAX_TILT_DEGREES = 89.0D;
    private static final long LINK_PAYLOAD_TIMEOUT_TICKS = 20L;
    private static final long DIRECT_INPUT_TIMEOUT_TICKS = 20L;
    private static final long WIRELESS_SIGNAL_SAMPLE_INTERVAL_TICKS = 5L;
    private static final long SAIL_POWER_SAMPLE_INTERVAL_TICKS = 5L;
    private static final long ASSEMBLY_FUEL_REDISTRIBUTION_INTERVAL_TICKS = 10L;
    private static final long NESTED_ASSEMBLY_RETRY_DELAY_TICKS = 4L;
    private static final double DEGREES_PER_TICK_PER_RPM = 3.0D / 10.0D;
    private static final double MIN_TILT_BLEND = 0.18D;
    private static final double MAX_TILT_BLEND = 0.62D;
    private static final double FAST_TILT_ERROR_FRACTION = 0.4D;
    /**
     * Redstone and Redstone Link signals are quantised and linked signals refresh in
     * batches. Use a deliberately slower response for that control path so a one-tick
     * signal fluctuation cannot jerk a mounted assembly towards a new tilt.
     */
    private static final double MIN_REDSTONE_TILT_BLEND = 0.045D;
    private static final double MAX_REDSTONE_TILT_BLEND = 0.16D;
    private static final double REDSTONE_FAST_TILT_ERROR_FRACTION = 0.65D;
    private static final double TILT_SNAP_EPSILON_DEGREES = 1.0E-3D;
    private static final ResourceLocation COPYCAT_PANEL_ID =
            ResourceLocation.fromNamespaceAndPath("create", "copycat_panel");
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

    // Mounted assembly
    private final VectorBearingMountedAssembly mountedAssembly = new VectorBearingMountedAssembly();
    // Assembly fluid handler
    private final IFluidHandler assemblyFluidHandler = new AssemblyFluidDistributor(this);
    // Assembly energy handler
    private final IEnergyStorage assemblyEnergyHandler = new AssemblyEnergyDistributor(this);
    // Tracked frequency bindings
    private final EnumMap<Direction, FrequencyBinding> frequencyBindings = createFrequencyBindings();
    // Last signals
    private final EnumMap<Direction, Integer> lastSignals = new EnumMap<>(Direction.class);
    // Cached wireless signals
    private final EnumMap<Direction, Integer> cachedWirelessSignals = new EnumMap<>(Direction.class);
    // Tracked direct tilt inputs
    private final Map<String, DirectTiltInput> directTiltInputs = new LinkedHashMap<>();

    // Current control mode
    private ControlMode controlMode = ControlMode.AUTO;
    // Max tilt in degrees
    private double maxTiltDegrees = DEFAULT_MAX_TILT_DEGREES;
    // Computer x in degrees
    private double computerXDegrees;
    // Computer z in degrees
    private double computerZDegrees;
    // Tracks whether computer override is active
    private boolean computerOverrideActive;
    // Linked x in degrees
    private double linkedXDegrees;
    // Linked z in degrees
    private double linkedZDegrees;
    // Last linked payload tick
    private long lastLinkedPayloadTick = Long.MIN_VALUE;
    // Last direct tilt tick
    private long lastDirectTiltTick = Long.MIN_VALUE;
    // Target x in degrees
    private double targetXDegrees;
    // Target z in degrees
    private double targetZDegrees;
    // Previous applied x in degrees
    private double previousAppliedXDegrees;
    // Previous applied z in degrees
    private double previousAppliedZDegrees;
    // Applied x in degrees
    private double appliedXDegrees;
    // Applied z in degrees
    private double appliedZDegrees;
    // Active control mode
    private ControlMode activeControlMode = ControlMode.AUTO;
    // Retains redstone damping while a released redstone target returns to neutral
    private boolean redstoneTiltDamping;
    // Mounted sub-level id
    private UUID mountedSubLevelId;
    // Mounted local pos
    private BlockPos mountedLocalPos;
    // Tracks whether mounted assembly is present
    private boolean mountedAssemblyPresent;
    // Shaft angle in degrees
    private double shaftAngleDegrees;
    // Last shaft sample tick
    private long lastShaftSampleTick = Long.MIN_VALUE;
    // Tracks whether shaft angle is initialized
    private boolean shaftAngleInitialized;
    // Last wireless signal sample tick
    private long lastWirelessSignalSampleTick = Long.MIN_VALUE;
    // Last sail power sample tick
    private long lastSailPowerSampleTick = Long.MIN_VALUE;
    // Mounted sail power
    private volatile double mountedSailPower;
    // Mounted sail radius
    private volatile double mountedSailRadius;
    // Next nested assembly tick
    private long nextNestedAssemblyTick = Long.MIN_VALUE;
    // Tracks whether assembly transfer is in progress
    private boolean assemblyTransferInProgress;
    // Pending placed block position
    private BlockPos pendingPlacedBlockPos;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing
    public VectorBearingBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.VECTOR_BEARING.get(), pos, state);
        for (Direction dir : Direction.values()) {
            lastSignals.put(dir, 0);
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            cachedWirelessSignals.put(dir, 0);
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
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the vector bearing
    @Override
    public void tick() {
        super.tick();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            updateAppliedTilt();
            return;
        }

        processPendingPlacedBlock(serverLevel);
        maintainMountedAssembly(serverLevel);
        refreshMountedSailPower();
        double shaftAngle = sampleShaftAngleDeg();
        redistributeAssemblyFuelFromStorage();
        TiltCommand command = resolveTiltCommand();
        boolean targetChanged = setTargetTilt(command);
        ControlMode previousActiveMode = activeControlMode;
        activeControlMode = command.sourceMode();
        boolean tiltChanged = updateAppliedTilt();
        if (targetChanged || tiltChanged || previousActiveMode != activeControlMode) {
            setChanged();
        }
        TiltCommand appliedCommand = TiltCommand.fromDegrees(appliedXDegrees, appliedZDegrees,
                maxTiltDegrees, activeControlMode, command.active());

        if (mountedSubLevelId != null) {
            try {
                if (!mountedAssembly.aim(this, serverLevel, appliedCommand.direction(), shaftAngle)) {
                    cleanupFailedAim(serverLevel);
                }
            } catch (RuntimeException error) {
                mountedAssembly.releaseJoint();
                LOGGER.debug("Vector Bearing assembly at {} will retry after an aiming failure", worldPosition, error);
            } catch (LinkageError error) {
                cleanupLinkageFailure(serverLevel, error);
            }
        }

        if (targetChanged || previousActiveMode != activeControlMode || level.getGameTime() % 10L == 0L) {
            sendData();
        }
    }

    // Maintain the mounted assembly
    private void maintainMountedAssembly(ServerLevel serverLevel) {
        if (mountedSubLevelId != null && mountedAssembly.recoverConflictingHeads(this, serverLevel)) {
            return;
        }
        MountedAssemblyStatus status = mountedAssembly.mountedBlockStatus(this, serverLevel);
        if (mountedSubLevelId != null && status.shouldDisassemble()) {
            disassembleMountedBlock();
        } else if (mountedSubLevelId != null && status.shouldClear()) {
            mountedAssembly.clearInvalidAssembly(this, serverLevel);
        } else if (mountedSubLevelId != null && !mountedAssemblyPresent) {
            mountedAssemblyPresent = true;
            setChanged();
            sendData();
        }
    }

    // Clean up the linkage failure
    private void cleanupLinkageFailure(ServerLevel serverLevel, LinkageError error) {
        LOGGER.warn("Vector Bearing mounted assembly at {} failed due to incompatible Sable linkage: {}",
                worldPosition, error.toString());
        rollbackMountedAssembly(serverLevel);
        markNestedAssemblyMutation(serverLevel);
        setChanged();
        sendData();
    }

    // Resolve the tilt command
    private TiltCommand resolveTiltCommand() {
        return switch (controlMode) {
            case REDSTONE -> resolveRedstoneCommand().withSource(ControlMode.REDSTONE);
            case COMPUTER -> resolveComputerCommand().withSource(ControlMode.COMPUTER);
            case AUTO -> {
                TiltCommand computer = resolveComputerCommand();
                if (computer.active()) {
                    yield computer.withSource(ControlMode.COMPUTER);
                }
                TiltCommand redstone = resolveRedstoneCommand();
                if (redstone.active()) {
                    yield redstone.withSource(ControlMode.REDSTONE);
                }
                yield TiltCommand.neutral(ControlMode.AUTO);
            }
        };
    }

    // Resolve the computer command
    private TiltCommand resolveComputerCommand() {
        if (computerOverrideActive) {
            return TiltCommand.fromDegrees(computerXDegrees, computerZDegrees, maxTiltDegrees, ControlMode.COMPUTER, true);
        }
        TiltCommand directController = resolveDirectCtrlCommand();
        if (directController.active()) {
            return directController;
        }
        if (hasFreshLinkedPayload()) {
            return TiltCommand.fromDegrees(linkedXDegrees, linkedZDegrees, maxTiltDegrees, ControlMode.COMPUTER, true);
        }
        return TiltCommand.neutral(ControlMode.COMPUTER);
    }

    // Resolve the direct ctrl command
    private TiltCommand resolveDirectCtrlCommand() {
        if (!hasFreshDirectTiltInput()) {
            if (!directTiltInputs.isEmpty()) {
                directTiltInputs.clear();
                setChanged();
                sendData();
            }
            return TiltCommand.neutral(ControlMode.COMPUTER);
        }

        double localX = 0.0D;
        double localZ = 0.0D;
        for (DirectTiltInput input : directTiltInputs.values()) {
            localX += input.localX() * input.value();
            localZ += input.localZ() * input.value();
        }

        double length = Math.hypot(localX, localZ);
        if (length <= 1.0E-6D) {
            return TiltCommand.neutral(ControlMode.COMPUTER);
        }
        if (length > 1.0D) {
            localX /= length;
            localZ /= length;
        }
        return TiltCommand.fromLocalVector(localX, localZ, maxTiltDegrees, ControlMode.COMPUTER, true);
    }

    // Resolve the redstone command
    private TiltCommand resolveRedstoneCommand() {
        refreshWirelessSignals();
        EnumMap<Direction, Integer> signals = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            signals.put(dir, 0);
        }
        boolean active = false;
        double localX = 0.0D;
        double localZ = 0.0D;
        for (Direction localSide : Direction.Plane.HORIZONTAL) {
            int signal = getRedstoneSignalForLocalSide(localSide);
            signals.put(localSide, signal);
            if (signal <= 0) {
                continue;
            }
            double strength = signal / 15.0D;
            localX += localSide.getStepX() * strength;
            localZ += localSide.getStepZ() * strength;
            active = true;
        }
        if (!signals.equals(lastSignals)) {
            lastSignals.clear();
            lastSignals.putAll(signals);
            setChanged();
            sendData();
        }

        return active
                ? TiltCommand.fromLocalVector(localX, localZ, maxTiltDegrees, ControlMode.REDSTONE, true)
                : TiltCommand.neutral(ControlMode.REDSTONE);
    }

    // Get the redstone signal for local side
    private int getRedstoneSignalForLocalSide(Direction localSide) {
        if (localSide == null || !localSide.getAxis().isHorizontal()) {
            return 0;
        }
        Direction worldSide = worldSideForLocal(getBearingFacing(), localSide);
        int signal = getLocalSignal(worldSide);
        signal = Math.max(signal, cachedWirelessSignals.getOrDefault(localSide, 0));
        return Mth.clamp(signal, 0, 15);
    }

    // Get the world side for local
    static Direction worldSideForLocal(Direction bearingFacing, Direction localSide) {
        if (bearingFacing == null || localSide == null || !localSide.getAxis().isHorizontal()) {
            return localSide;
        }
        Quaterniond localToWorld = new Quaterniond()
                .rotationTo(new Vector3d(0.0D, 1.0D, 0.0D), directionVector(bearingFacing));
        Vector3d world = localToWorld.transform(directionVector(localSide));
        return Direction.getNearest(world.x, world.y, world.z);
    }

    // Set the target tilt
    private boolean setTargetTilt(TiltCommand command) {
        double previousX = targetXDegrees;
        double previousZ = targetZDegrees;
        targetXDegrees = command.xDegrees();
        targetZDegrees = command.zDegrees();
        if (command.sourceMode() == ControlMode.REDSTONE) {
            redstoneTiltDamping = true;
        } else if (command.sourceMode() == ControlMode.COMPUTER) {
            redstoneTiltDamping = false;
        } else if (Math.hypot(targetXDegrees - appliedXDegrees, targetZDegrees - appliedZDegrees)
                <= TILT_SNAP_EPSILON_DEGREES) {
            redstoneTiltDamping = false;
        }
        return Math.abs(previousX - targetXDegrees) > 1.0E-4D
                || Math.abs(previousZ - targetZDegrees) > 1.0E-4D;
    }

    // Update the applied tilt
    private boolean updateAppliedTilt() {
        previousAppliedXDegrees = appliedXDegrees;
        previousAppliedZDegrees = appliedZDegrees;
        double deltaX = targetXDegrees - appliedXDegrees;
        double deltaZ = targetZDegrees - appliedZDegrees;
        double errorDegrees = Math.hypot(deltaX, deltaZ);
        if (errorDegrees <= TILT_SNAP_EPSILON_DEGREES) {
            appliedXDegrees = targetXDegrees;
            appliedZDegrees = targetZDegrees;
        } else {
            double blend = redstoneTiltDamping
                    ? dampedRedstoneTiltBlend(errorDegrees, maxTiltDegrees)
                    : adaptiveTiltBlend(errorDegrees, maxTiltDegrees);
            appliedXDegrees += deltaX * blend;
            appliedZDegrees += deltaZ * blend;
        }
        return Math.abs(previousAppliedXDegrees - appliedXDegrees) > 1.0E-6D
                || Math.abs(previousAppliedZDegrees - appliedZDegrees) > 1.0E-6D;
    }

    // Get the adaptive tilt blend
    static double adaptiveTiltBlend(double errorDegrees, double maxTiltDegrees) {
        double fastResponseError = Math.max(1.0D, Math.abs(maxTiltDegrees) * FAST_TILT_ERROR_FRACTION);
        double normalizedError = Mth.clamp(Math.abs(errorDegrees) / fastResponseError, 0.0D, 1.0D);
        double easedError = normalizedError * normalizedError * (3.0D - 2.0D * normalizedError);
        return MIN_TILT_BLEND + (MAX_TILT_BLEND - MIN_TILT_BLEND) * easedError;
    }

    // Get the damped redstone tilt blend
    static double dampedRedstoneTiltBlend(double errorDegrees, double maxTiltDegrees) {
        double fastResponseError = Math.max(1.0D,
                Math.abs(maxTiltDegrees) * REDSTONE_FAST_TILT_ERROR_FRACTION);
        double normalizedError = Mth.clamp(Math.abs(errorDegrees) / fastResponseError, 0.0D, 1.0D);
        double easedError = normalizedError * normalizedError * (3.0D - 2.0D * normalizedError);
        return MIN_REDSTONE_TILT_BLEND
                + (MAX_REDSTONE_TILT_BLEND - MIN_REDSTONE_TILT_BLEND) * easedError;
    }

    // Clamp the tilt state
    private void clampTiltState() {
        TiltCommand target = TiltCommand.fromDegrees(targetXDegrees, targetZDegrees,
                maxTiltDegrees, activeControlMode, true);
        targetXDegrees = target.xDegrees();
        targetZDegrees = target.zDegrees();
        TiltCommand applied = TiltCommand.fromDegrees(appliedXDegrees, appliedZDegrees,
                maxTiltDegrees, activeControlMode, true);
        appliedXDegrees = applied.xDegrees();
        appliedZDegrees = applied.zDegrees();
        previousAppliedXDegrees = appliedXDegrees;
        previousAppliedZDegrees = appliedZDegrees;
    }

    // Refresh the wireless signals
    private void refreshWirelessSignals() {
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (lastWirelessSignalSampleTick != Long.MIN_VALUE
                && gameTime - lastWirelessSignalSampleTick < WIRELESS_SIGNAL_SAMPLE_INTERVAL_TICKS) {
            return;
        }
        lastWirelessSignalSampleTick = gameTime;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            cachedWirelessSignals.put(dir, queryWirelessSignal(frequencyBindings.get(dir)));
        }
    }

    // Get the local signal
    private int getLocalSignal(Direction dir) {
        if (level == null) {
            return 0;
        }
        BlockPos neighborPos = worldPosition.relative(dir);
        Direction neighborSide = redstoneSignalQuerySide(dir);
        return Math.max(level.getSignal(neighborPos, neighborSide), level.getDirectSignal(neighborPos, neighborSide));
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

    // Check if this can accept orientation payload
    @Override
    public boolean canAcceptOrientationPayload(OrientationPayload payload) {
        return payload != null && payload.getDirection() != null;
    }

    // Apply the orientation payload
    @Override
    public void applyOrientationPayload(OrientationPayload payload) {
        linkedXDegrees = payload.getXAngleDegrees();
        linkedZDegrees = payload.getZAngleDegrees();
        lastLinkedPayloadTick = payload.getGameTime();
        setChanged();
        sendData();
    }

    // Check if this has fresh linked payload
    private boolean hasFreshLinkedPayload() {
        return level != null
                && lastLinkedPayloadTick != Long.MIN_VALUE
                && level.getGameTime() - lastLinkedPayloadTick <= LINK_PAYLOAD_TIMEOUT_TICKS;
    }

    // Check if this has fresh direct tilt input
    private boolean hasFreshDirectTiltInput() {
        return level != null
                && lastDirectTiltTick != Long.MIN_VALUE
                && level.getGameTime() - lastDirectTiltTick <= DIRECT_INPUT_TIMEOUT_TICKS
                && !directTiltInputs.isEmpty();
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        Vec3 localDirection = localTiltDirForChannel(channelId);
        if (localDirection == null) {
            return;
        }
        applyDirectTiltInput(channelKey(channelId), localDirection.x, localDirection.z, val);
    }

    // Apply the source directional controller signal
    public boolean applySourceDirectionalControllerSignal(String channelId, float val, BlockEntity sourceBlockEntity) {
        if (!(sourceBlockEntity instanceof AnalogueJoystickBlockEntity joystick)) {
            return false;
        }

        Direction sourceDirection = joystickDirForChannel(channelId, joystick);
        if (sourceDirection == null) {
            return false;
        }

        Vec3 localDirection = localTiltDirFromSource(sourceBlockEntity,
                Vec3.atLowerCornerOf(sourceDirection.getNormal()));
        if (localDirection == null) {
            return false;
        }

        applyDirectTiltInput(channelKey(channelId), localDirection.x, localDirection.z, val);
        return true;
    }

    // Apply the direct tilt input
    private void applyDirectTiltInput(String key, double localX, double localZ, float val) {
        float normalizedValue = normalizeDirectCtrlValue(val);
        double length = Math.hypot(localX, localZ);
        if (length <= 1.0E-6D || Math.abs(normalizedValue) <= 1.0E-4f) {
            directTiltInputs.remove(key);
        } else {
            double sign = Math.signum(normalizedValue);
            directTiltInputs.put(key, new DirectTiltInput((localX / length) * sign,
                    (localZ / length) * sign,
                    Math.abs(normalizedValue)));
        }
        lastDirectTiltTick = level == null ? Long.MIN_VALUE : level.getGameTime();
        setChanged();
        sendData();
    }

    // Normalize the direct ctrl value
    private static float normalizeDirectCtrlValue(float val) {
        if (Float.isNaN(val) || Float.isInfinite(val)) {
            return 0.0f;
        }
        float abs = Math.abs(val);
        if (abs <= 1.0f) {
            return Mth.clamp(val, -1.0f, 1.0f);
        }
        if (abs <= 15.0f) {
            return Mth.clamp(val / 15.0f, -1.0f, 1.0f);
        }
        return Mth.clamp(Math.signum(val), -1.0f, 1.0f);
    }

    // Get the channel key
    private static String channelKey(String channelId) {
        return channelId == null ? "" : channelId.trim().toLowerCase(Locale.ROOT);
    }

    // Get the local tilt dir for channel
    private Vec3 localTiltDirForChannel(String channelId) {
        String normalized = channelKey(channelId);
        AnalogueControlChannel channel = AnalogueControlChannel.byId(normalized);
        if (channel != null) {
            return switch (channel) {
                case PITCH_UP, THROTTLE_UP, LIFT_UP -> new Vec3(0.0D, 0.0D, -1.0D);
                case PITCH_DOWN, THROTTLE_DOWN, LIFT_DOWN -> new Vec3(0.0D, 0.0D, 1.0D);
                case ROLL_LEFT, YAW_LEFT, STRAFE_LEFT -> new Vec3(-1.0D, 0.0D, 0.0D);
                case ROLL_RIGHT, YAW_RIGHT, STRAFE_RIGHT -> new Vec3(1.0D, 0.0D, 0.0D);
                case STABILIZE -> null;
            };
        }
        return switch (normalized) {
            case "forward", "north", "advanced_graph" -> new Vec3(0.0D, 0.0D, -1.0D);
            case "backward", "back", "south" -> new Vec3(0.0D, 0.0D, 1.0D);
            case "left", "west" -> new Vec3(-1.0D, 0.0D, 0.0D);
            case "right", "east" -> new Vec3(1.0D, 0.0D, 0.0D);
            default -> null;
        };
    }

    // Get the joystick dir for channel
    private Direction joystickDirForChannel(String channelId, AnalogueJoystickBlockEntity joystick) {
        Direction facing = AnalogueJoystickBlock.getLogicalFacing(joystick.getBlockState());
        String normalized = channelKey(channelId);
        AnalogueControlChannel channel = AnalogueControlChannel.byId(normalized);
        if (channel != null) {
            return switch (channel) {
                case PITCH_UP, THROTTLE_UP, LIFT_UP -> facing;
                case PITCH_DOWN, THROTTLE_DOWN, LIFT_DOWN -> facing.getOpposite();
                case ROLL_LEFT, YAW_LEFT, STRAFE_LEFT -> facing.getCounterClockWise();
                case ROLL_RIGHT, YAW_RIGHT, STRAFE_RIGHT -> facing.getClockWise();
                case STABILIZE -> null;
            };
        }
        return switch (normalized) {
            case "forward", "advanced_graph" -> facing;
            case "backward", "back" -> facing.getOpposite();
            case "left" -> facing.getCounterClockWise();
            case "right" -> facing.getClockWise();
            default -> null;
        };
    }

    // Get the local tilt dir from source
    private Vec3 localTiltDirFromSource(BlockEntity sourceBlockEntity, Vec3 sourceLocalDirection) {
        Vec3 worldDirection = toGlobalDirection(sourceBlockEntity, sourceLocalDirection);
        if (worldDirection == null || worldDirection.lengthSqr() <= 1.0E-6D) {
            return null;
        }

        Vec3 localX = toGlobalDirection(this, new Vec3(1.0D, 0.0D, 0.0D));
        Vec3 localZ = toGlobalDirection(this, new Vec3(0.0D, 0.0D, 1.0D));
        if (localX == null || localZ == null) {
            Vec3 localDirection = SimulatedHelper.toContainingLocalDirection(this, worldDirection);
            return localDirection == null || localDirection.lengthSqr() <= 1.0E-6D
                    ? null
                    : new Vec3(localDirection.x, 0.0D, localDirection.z);
        }

        Vec3 normalizedWorld = worldDirection.normalize();
        return new Vec3(normalizedWorld.dot(localX.normalize()), 0.0D, normalizedWorld.dot(localZ.normalize()));
    }

    // Convert the vector bearing to global direction
    private static Vec3 toGlobalDirection(BlockEntity blockEntity, Vec3 localDirection) {
        if (blockEntity == null || localDirection == null || localDirection.lengthSqr() <= 1.0E-6D) {
            return null;
        }
        Vector3d worldDirection = NestedAssemblyFrame.resolve(blockEntity).toWorldDirection(new Vector3d(
                localDirection.x, localDirection.y, localDirection.z));
        return worldDirection.lengthSquared() <= 1.0E-6D
                ? null
                : new Vec3(worldDirection.x, worldDirection.y, worldDirection.z).normalize();
    }

    // Get the direction vector
    private static Vector3d directionVector(Direction dir) {
        return new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // Get the control mode
    public ControlMode getControlMode() {
        return controlMode;
    }

    // Set the control mode
    public void setControlMode(ControlMode mode) {
        if (mode == null || controlMode == mode) {
            return;
        }
        controlMode = mode;
        setChanged();
        sendData();
    }

    // Get the active control mode
    public ControlMode getActiveControlMode() {
        return activeControlMode;
    }

    // Get the max tilt degrees
    public double getMaxTiltDegrees() {
        return maxTiltDegrees;
    }

    // Set the max tilt degrees
    public void setMaxTiltDegrees(double maxTiltDegrees) {
        double next = Mth.clamp(maxTiltDegrees, MIN_MAX_TILT_DEGREES, MAX_MAX_TILT_DEGREES);
        if (Math.abs(this.maxTiltDegrees - next) < 1.0E-4D) {
            return;
        }
        this.maxTiltDegrees = next;
        setComputerAnglesDegrees(computerXDegrees, computerZDegrees, computerOverrideActive);
        clampTiltState();
        setChanged();
        sendData();
    }

    // Get the applied x degrees
    public double getAppliedXDegrees() {
        return appliedXDegrees;
    }

    // Get the applied z degrees
    public double getAppliedZDegrees() {
        return appliedZDegrees;
    }

    // Get the interpolated applied x degrees
    public double getInterpolatedAppliedXDegrees(float partialTicks) {
        return previousAppliedXDegrees + (appliedXDegrees - previousAppliedXDegrees) * partialTicks;
    }

    // Get the interpolated applied z degrees
    public double getInterpolatedAppliedZDegrees(float partialTicks) {
        return previousAppliedZDegrees + (appliedZDegrees - previousAppliedZDegrees) * partialTicks;
    }

    // Check if this has computer override
    public boolean hasComputerOverride() {
        return computerOverrideActive;
    }

    // Set the computer angles degrees
    public void setComputerAnglesDegrees(double xDegrees, double zDegrees) {
        setComputerAnglesDegrees(xDegrees, zDegrees, true);
    }

    // Set the computer angles degrees
    private void setComputerAnglesDegrees(double xDegrees, double zDegrees, boolean active) {
        TiltCommand clamped = TiltCommand.fromDegrees(xDegrees, zDegrees, maxTiltDegrees, ControlMode.COMPUTER, active);
        computerXDegrees = clamped.xDegrees();
        computerZDegrees = clamped.zDegrees();
        computerOverrideActive = active;
        setChanged();
        sendData();
    }

    // Clear the computer angles
    public void clearComputerAngles() {
        if (!computerOverrideActive && Math.abs(computerXDegrees) < 1.0E-4D && Math.abs(computerZDegrees) < 1.0E-4D) {
            return;
        }
        computerXDegrees = 0.0D;
        computerZDegrees = 0.0D;
        computerOverrideActive = false;
        setChanged();
        sendData();
    }

    // Get the computer x degrees
    public double getComputerXDegrees() {
        return computerXDegrees;
    }

    // Get the computer z degrees
    public double getComputerZDegrees() {
        return computerZDegrees;
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("tilt_x", "number");
        data.put("tilt_z", "number");
        data.put("computer_tilt_x", "number");
        data.put("computer_tilt_z", "number");
        data.put("max_tilt", "number");
        data.put("control_mode", "string");
        data.put("active_control_mode", "string");
        data.put("mounted", "boolean");
        data.put("mounted_sublevel", "string");
        return data;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("tilt_x", "number");
        data.put("tilt_z", "number");
        data.put("max_tilt", "number");
        data.put("control_mode", "string");
        data.put("forward", "number");
        data.put("backward", "number");
        data.put("left", "number");
        data.put("right", "number");
        return data;
    }

    // Get the graph writable options
    @Override
    public Map<String, List<String>> graphWritableOptions() {
        return Map.of("control_mode", List.of("auto", "computer", "redstone"));
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case "tilt_x" -> AdvancedGraphDocument.Value.number(appliedXDegrees);
            case "tilt_z" -> AdvancedGraphDocument.Value.number(appliedZDegrees);
            case "computer_tilt_x" -> AdvancedGraphDocument.Value.number(computerXDegrees);
            case "computer_tilt_z" -> AdvancedGraphDocument.Value.number(computerZDegrees);
            case "max_tilt" -> AdvancedGraphDocument.Value.number(maxTiltDegrees);
            case "control_mode" -> AdvancedGraphDocument.Value.string(controlMode.name().toLowerCase(Locale.ROOT));
            case "active_control_mode" -> AdvancedGraphDocument.Value.string(activeControlMode.name().toLowerCase(Locale.ROOT));
            case "mounted" -> AdvancedGraphDocument.Value.bool(isMountedAssemblyPresent());
            case "mounted_sublevel" -> AdvancedGraphDocument.Value.string(mountedSubLevelId == null ? "" : mountedSubLevelId.toString());
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value val) {
        switch (field) {
            case "tilt_x" -> {
                setComputerAnglesDegrees(val.asNumber(), computerZDegrees);
                return true;
            }
            case "tilt_z" -> {
                setComputerAnglesDegrees(computerXDegrees, val.asNumber());
                return true;
            }
            case "max_tilt" -> {
                setMaxTiltDegrees(val.asNumber());
                return true;
            }
            case "control_mode" -> {
                setControlMode(readControlMode(val.asString(), controlMode));
                return true;
            }
            case "forward", "backward", "left", "right" -> {
                applyDirectControllerSignal(field, (float) val.asNumber());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // Read the control mode
    private static ControlMode readControlMode(String val, ControlMode fallback) {
        if (val == null || val.isBlank()) {
            return fallback;
        }
        try {
            return ControlMode.valueOf(val.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    // Get the signal
    public int getSignal(Direction dir) {
        return lastSignals.getOrDefault(dir, 0);
    }

    // Get the frequency first
    public ItemStack getFrequencyFirst(Direction dir) {
        return frequencyBindings.get(dir).first();
    }

    // Get the frequency second
    public ItemStack getFrequencySecond(Direction dir) {
        return frequencyBindings.get(dir).second();
    }

    // Set the frequency
    public void setFrequency(Direction dir, ItemStack first, ItemStack second) {
        if (dir == null || !dir.getAxis().isHorizontal()) {
            return;
        }
        FrequencyBinding binding = frequencyBindings.get(dir);
        if (binding == null || sameFrequencyItem(binding.first(), first)
                && sameFrequencyItem(binding.second(), second)) {
            return;
        }
        binding.set(first, second);
        cachedWirelessSignals.put(dir, 0);
        lastWirelessSignalSampleTick = Long.MIN_VALUE;
        setChanged();
        sendData();
    }

    // Check if this uses the same frequency item
    private static boolean sameFrequencyItem(ItemStack current, @Nullable ItemStack next) {
        boolean currentEmpty = current == null || current.isEmpty();
        boolean nextEmpty = next == null || next.isEmpty();
        return currentEmpty || nextEmpty
                ? currentEmpty == nextEmpty
                : ItemStack.isSameItemSameComponents(current, next);
    }

    // Check if the mounted assembly is present
    public boolean isMountedAssemblyPresent() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            return mountedSubLevelId != null && mountedAssembly.hasMountedBlock(this, serverLevel);
        }
        return mountedAssemblyPresent && mountedSubLevelId != null;
    }

    // Get the mounted sublevel id
    public UUID getMountedSubLevelId() {
        return mountedSubLevelId;
    }

    // Get the mounted local pos
    BlockPos getMountedLocalPos() {
        return mountedLocalPos;
    }

    // Set the mounted assembly
    void setMountedAssembly(@Nullable UUID subLevelId, @Nullable BlockPos localPos) {
        mountedSubLevelId = subLevelId;
        mountedLocalPos = localPos == null ? null : localPos.immutable();
        mountedAssemblyPresent = subLevelId != null && localPos != null;
        invalidateMountedSailPower();
        SableAssemblyTopologyInvalidation.invalidate(level);
        setChanged();
        sendData();
    }

    // Begin the assembly transfer
    void beginAssemblyTransfer() {
        assemblyTransferInProgress = true;
        mountedAssembly.releaseJoint();
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        assemblyTransferInProgress = false;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }
        mountedAssembly.releaseJoint();
        if (mountedSubLevelId != null && mountedLocalPos != null) {
            mountedAssembly.refreshLinkParent(this, serverLevel);
        }
        markNestedAssemblyMutation(serverLevel);
        setChanged();
        sendData();
    }

    // Update the mounted assembly from link
    void updateMountedAssemblyFromLink(@Nullable UUID subLevelId, BlockPos localPos) {
        if (localPos == null) {
            return;
        }
        mountedSubLevelId = subLevelId;
        mountedLocalPos = localPos.immutable();
        mountedAssemblyPresent = subLevelId != null;
        invalidateMountedSailPower();
        mountedAssembly.releaseJoint();
        SableAssemblyTopologyInvalidation.invalidate(level);
        setChanged();
        sendData();
    }

    // Absorb a block placed on the assembled head
    public boolean absorbPlacedBlockOnAssembledHead(BlockPos placedPos) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || placedPos == null
                || mountedSubLevelId == null || !placedPos.equals(getMountedBlockPos())) {
            return false;
        }
        BlockState placedState = serverLevel.getBlockState(placedPos);
        if (placedState.isAir() || placedState.getBlock() instanceof VectorBearingLinkBlock) {
            return false;
        }
        pendingPlacedBlockPos = placedPos.immutable();
        return true;
    }

    // Process the pending placed block
    private void processPendingPlacedBlock(ServerLevel serverLevel) {
        BlockPos placedPos = pendingPlacedBlockPos;
        if (placedPos == null) {
            return;
        }
        pendingPlacedBlockPos = null;
        try {
            if (mountedAssembly.absorbPlacedBlock(this, serverLevel, placedPos)) {
                mountedAssemblyPresent = true;
                setChanged();
                sendData();
            }
        } catch (RuntimeException | LinkageError error) {
            mountedAssembly.releaseJoint();
            LOGGER.debug("Vector Bearing at {} could not absorb a newly placed block", worldPosition, error);
        }
    }

    // Clear the mounted assembly for head conflict
    void clearMountedAssemblyForHeadConflict() {
        pendingPlacedBlockPos = null;
        assemblyTransferInProgress = false;
        mountedAssembly.releaseJoint();
        setMountedAssembly(null, null);
    }

    // Get the assembly fluid handler
    public IFluidHandler getAssemblyFluidHandler() {
        return assemblyFluidHandler;
    }

    // Get the assembly energy handler
    public IEnergyStorage getAssemblyEnergyHandler() {
        return assemblyEnergyHandler;
    }

    // Check if this can accept assembly fluid from the source
    public boolean canAcceptAssemblyFluidFrom(Direction side) {
        return canAcceptAssemblyInputFrom(side);
    }

    // Check if this can accept assembly energy from the source
    public boolean canAcceptAssemblyEnergyFrom(Direction side) {
        return canAcceptAssemblyInputFrom(side);
    }

    // Check if this can accept assembly input from the source
    private boolean canAcceptAssemblyInputFrom(Direction side) {
        if (side == null) {
            return true;
        }

        Direction inputFace = getBearingFacing();
        return side == inputFace || side == inputFace.getOpposite();
    }

    // Get the bearing facing
    public Direction getBearingFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.UP;
    }

    // Get the mounted block pos
    BlockPos getMountedBlockPos() {
        return worldPosition.relative(getBearingFacing());
    }

    // Get the shaft angle degrees
    public double getShaftAngleDegrees() {
        return shaftAngleDegrees;
    }

    // Sample the shaft angle deg
    private double sampleShaftAngleDeg() {
        if (level == null) {
            return shaftAngleDegrees;
        }

        long now = level.getGameTime();
        if (!shaftAngleInitialized) {
            shaftAngleDegrees = 0.0D;
            shaftAngleInitialized = true;
            lastShaftSampleTick = now;
        }

        double exactAngle = KineticAngleHelper.getHeldRotationAngleDegrees(this, getBearingFacing().getAxis());
        if (Double.isNaN(exactAngle)) {
            exactAngle = SimulatedPreciseAngleCompat.getTorsionSpringOutputAngleDegrees(this);
        }
        if (!Double.isNaN(exactAngle)) {
            shaftAngleDegrees = KineticAngleHelper.normalizeDegrees(exactAngle);
            lastShaftSampleTick = now;
            return shaftAngleDegrees;
        }

        if (lastShaftSampleTick != Long.MIN_VALUE) {
            long deltaTicks = Math.max(0L, now - lastShaftSampleTick);
            if (deltaTicks > 0L) {
                shaftAngleDegrees = KineticAngleHelper.normalizeDegrees(
                        shaftAngleDegrees + getSignedAngularStep() * deltaTicks);
            }
        }
        lastShaftSampleTick = now;
        return shaftAngleDegrees;
    }

    // Reset the mounted head's rotation frame. A new assembly always begins at zero.
    private void resetShaftAngleForAssembly() {
        shaftAngleDegrees = 0.0D;
        shaftAngleInitialized = true;
        lastShaftSampleTick = level == null ? Long.MIN_VALUE : level.getGameTime();
    }

    // Get the signed angular step
    private double getSignedAngularStep() {
        return getSpeed() * DEGREES_PER_TICK_PER_RPM;
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
        if (owner == null) {
            return fallback;
        }
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
        if (owner == null) {
            return true;
        }
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

    // Try to assemble mounted block
    public boolean tryAssembleMountedBlock() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        if (!isContainingSubLevelReadyForMountedAssembly(serverLevel)) {
            return false;
        }
        if (mountedSubLevelId != null) {
            if (mountedAssembly.hasMountedBlock(this, serverLevel)) {
                return false;
            }
            mountedAssembly.clearInvalidAssembly(this, serverLevel);
        }
        try {
            resetShaftAngleForAssembly();
            if (!mountedAssembly.assemble(this, serverLevel)) {
                return false;
            }
            TiltCommand command = resolveTiltCommand();
            setTargetTilt(command);
            updateAppliedTilt();
            TiltCommand appliedCommand = TiltCommand.fromDegrees(appliedXDegrees, appliedZDegrees,
                    maxTiltDegrees, command.sourceMode(), command.active());
            if (!mountedAssembly.aim(this, serverLevel, appliedCommand.direction(), sampleShaftAngleDeg())) {
                mountedAssembly.disassemble(this, serverLevel);
                markNestedAssemblyMutation(serverLevel);
                return false;
            }
            mountedAssemblyPresent = true;
            setChanged();
            sendData();
            markNestedAssemblyMutation(serverLevel);
            return true;
        } catch (RuntimeException error) {
            rollbackMountedAssembly(serverLevel);
            markNestedAssemblyMutation(serverLevel);
            LOGGER.debug("Vector Bearing assembly at {} failed and was rolled back", worldPosition, error);
            return false;
        } catch (LinkageError error) {
            cleanupLinkageFailure(serverLevel, error);
            return false;
        }
    }

    // Disassemble the mounted block
    public void disassembleMountedBlock() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            mountedAssembly.disassemble(this, serverLevel);
            markNestedAssemblyMutation(serverLevel);
        } else {
            mountedAssembly.releaseJoint();
            setMountedAssembly(null, null);
        }
    }

    // Toggle the mounted bearing block
    public boolean toggleMountedBlock() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        if (mountedSubLevelId != null && mountedAssembly.hasMountedBlock(this, serverLevel)) {
            disassembleMountedBlock();
            return true;
        }
        if (mountedSubLevelId != null) {
            mountedAssembly.clearInvalidAssembly(this, serverLevel);
        }
        return tryAssembleMountedBlock();
    }

    // Get the mounted sublevel handle
    private Object getMountedSubLevelHandle() {
        if (level == null || mountedSubLevelId == null) {
            return null;
        }
        return SubLevelBlockEntityCollector.getSubLevel(level, mountedSubLevelId);
    }

    // Refresh the mounted sail power
    private void refreshMountedSailPower() {
        if (level == null || level.isClientSide || mountedSubLevelId == null) {
            mountedSailPower = 0.0D;
            lastSailPowerSampleTick = Long.MIN_VALUE;
            return;
        }
        long gameTime = level.getGameTime();
        if (lastSailPowerSampleTick != Long.MIN_VALUE
                && gameTime - lastSailPowerSampleTick < SAIL_POWER_SAMPLE_INTERVAL_TICKS) {
            return;
        }
        lastSailPowerSampleTick = gameTime;
        MountedSailSample sample = scanMountedSails();
        mountedSailPower = sample.power();
        mountedSailRadius = sample.radius();
    }

    // Scan the mounted sails
    private MountedSailSample scanMountedSails() {
        if (!(getMountedSubLevelHandle() instanceof ServerSubLevel mountedSubLevel)) {
            return MountedSailSample.EMPTY;
        }
        var plot = mountedSubLevel.getPlot();
        var embeddedLevel = plot.getEmbeddedLevelAccessor();
        BlockPos plotCenter = plot.getCenterBlock();
        BlockPos forceOrigin = mountedLocalPos == null ? plotCenter : mountedLocalPos;
        Direction facing = getBearingFacing();
        BoundingBox bounds = plot.getBoundingBox().toMojang();
        double sailPower = 0.0D;
        double sailRadius = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            BlockState state = embeddedLevel.getBlockState(pos.subtract(plotCenter));
            if (COPYCAT_PANEL_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                    && SubLevelBlockEntityCollector.getBlockEntity(
                    mountedSubLevel, pos) instanceof CopycatBlockEntity copycat
                    && !copycat.getMaterial().isAir()) {
                state = copycat.getMaterial();
            }
            if (state.is(AllTags.AllBlockTags.WINDMILL_SAILS.tag)) {
                sailPower += 1.0D;
                double x = pos.getX() - forceOrigin.getX();
                double y = pos.getY() - forceOrigin.getY();
                double z = pos.getZ() - forceOrigin.getZ();
                double axial = x * facing.getStepX() + y * facing.getStepY() + z * facing.getStepZ();
                double radialSquared = Math.max(0.0D, x * x + y * y + z * z - axial * axial);
                sailRadius = Math.max(sailRadius, Math.sqrt(radialSquared) + 0.5D);
            }
        }
        return new MountedSailSample(sailPower, sailRadius);
    }

    // Invalidate the mounted sail power
    private void invalidateMountedSailPower() {
        lastSailPowerSampleTick = Long.MIN_VALUE;
        if (mountedSubLevelId == null) {
            mountedSailPower = 0.0D;
            mountedSailRadius = 0.0D;
        }
    }

    // Get the mounted sail power
    double getMountedSailPower() {
        return mountedSailPower;
    }

    // Get the mounted sail radius
    double getMountedSailRadius() {
        return mountedSailRadius;
    }

    // Get the propeller rotation rate
    double getPropellerRotationRate() {
        return convertToAngular(getSpeed());
    }

    // Collect the attached fluid handlers
    private List<IFluidHandler> collectAttachedFluidHandlers(FluidStack requestedFluid) {
        if (level == null) {
            return List.of();
        }

        List<IFluidHandler> targets = new ArrayList<>();
        Set<IFluidHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Object mountedSubLevel = getMountedSubLevelHandle();

        if (mountedSubLevel != null) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(mountedSubLevel)) {
                addAttachedFluidHandler(targets, seenHandlers, blockEntity, requestedFluid);
            }
        }

        if (targets.isEmpty()) {
            addAttachedFluidHandler(targets, seenHandlers, level.getBlockEntity(getMountedBlockPos()), requestedFluid);
        }

        return targets;
    }

    // Collect the attached energy handlers
    private List<IEnergyStorage> collectAttachedEnergyHandlers() {
        if (level == null) {
            return List.of();
        }

        List<IEnergyStorage> targets = new ArrayList<>();
        Set<IEnergyStorage> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Object mountedSubLevel = getMountedSubLevelHandle();

        if (mountedSubLevel != null) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(mountedSubLevel)) {
                addAttachedEnergyHandler(targets, seenHandlers, blockEntity);
            }
        }

        if (targets.isEmpty()) {
            addAttachedEnergyHandler(targets, seenHandlers, level.getBlockEntity(getMountedBlockPos()));
        }

        return targets;
    }

    // Get the mounted sublevel block entities
    private List<BlockEntity> getMountedSubLevelBlockEntities() {
        Object mountedSubLevel = getMountedSubLevelHandle();
        if (mountedSubLevel == null) {
            return List.of();
        }
        return SubLevelBlockEntityCollector.getBlockEntities(mountedSubLevel);
    }

    // Add the attached fluid handler
    private void addAttachedFluidHandler(List<IFluidHandler> targets, Set<IFluidHandler> seenHandlers,
            BlockEntity blockEntity, FluidStack requestedFluid) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity == this
                || blockEntity instanceof VectorBearingBlockEntity) {
            return;
        }

        IFluidHandler handler = findFluidHandler(blockEntity);
        if (handler == null) {
            return;
        }

        if (!requestedFluid.isEmpty()
                && OxidizedFuelStorageAccess.allow(() ->
                        handler.fill(requestedFluid.copyWithAmount(1), IFluidHandler.FluidAction.SIMULATE)) <= 0) {
            return;
        }

        if (seenHandlers.add(handler)) {
            targets.add(handler);
        }
    }

    // Add the attached energy handler
    private void addAttachedEnergyHandler(List<IEnergyStorage> targets, Set<IEnergyStorage> seenHandlers,
            BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity == this
                || blockEntity instanceof VectorBearingBlockEntity) {
            return;
        }

        IEnergyStorage handler;
        if (blockEntity instanceof ThrusterBlockEntity thruster) {
            handler = thruster.isFocusedMode() ? thruster.getFocusInput() : null;
        } else {
            handler = findEnergyHandler(blockEntity);
        }

        if (handler == null || !handler.canReceive() || handler.receiveEnergy(1, true) <= 0) {
            return;
        }

        if (seenHandlers.add(handler)) {
            targets.add(handler);
        }
    }

    // Collect the mounted thrusters
    private List<ThrusterBlockEntity> collectMountedThrusters() {
        return collectMountedThrusters(getMountedSubLevelBlockEntities());
    }

    // Collect the mounted thrusters
    private List<ThrusterBlockEntity> collectMountedThrusters(List<BlockEntity> blockEntities) {
        List<ThrusterBlockEntity> thrusters = new ArrayList<>();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof ThrusterBlockEntity thruster && !thruster.isRemoved()) {
                thrusters.add(thruster);
            }
        }
        return thrusters;
    }

    // Collect the assembly fuel storers
    private List<BlockEntity> collectAssemblyFuelStorers() {
        return collectAssemblyFuelStorers(getMountedSubLevelBlockEntities());
    }

    // Collect the assembly fuel storers
    private List<BlockEntity> collectAssemblyFuelStorers(List<BlockEntity> blockEntities) {
        List<BlockEntity> storers = new ArrayList<>();
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity == null || blockEntity.isRemoved() || blockEntity == this
                    || blockEntity instanceof VectorBearingBlockEntity
                    || blockEntity instanceof ThrusterBearingBlockEntity
                    || blockEntity instanceof ThrusterBlockEntity) {
                continue;
            }
            if (findFluidHandler(blockEntity) != null) {
                storers.add(blockEntity);
            }
        }
        return storers;
    }

    // Redistribute the assembly fuel from storage
    private void redistributeAssemblyFuelFromStorage() {
        if (level == null || level.isClientSide
                || level.getGameTime() % ASSEMBLY_FUEL_REDISTRIBUTION_INTERVAL_TICKS != 0L) {
            return;
        }

        List<BlockEntity> mountedBlockEntities = getMountedSubLevelBlockEntities();
        List<BlockEntity> storers = collectAssemblyFuelStorers(mountedBlockEntities);
        if (storers.isEmpty()) {
            return;
        }

        for (ThrusterBlockEntity thruster : collectMountedThrusters(mountedBlockEntities)) {
            refillThrusterFromStorers(thruster, storers);
        }
    }

    // Refill the thruster from storers
    private void refillThrusterFromStorers(ThrusterBlockEntity thruster, List<BlockEntity> storers) {
        IFluidHandler consumer = thruster.getFuelTank();
        int remaining = consumer.getTankCapacity(0) - consumer.getFluidInTank(0).getAmount();
        if (remaining <= 0) {
            return;
        }

        FluidStack desiredFuel = consumer.getFluidInTank(0);
        for (BlockEntity storageBlockEntity : storers) {
            IFluidHandler storage = findFluidHandler(storageBlockEntity);
            if (storage == null) {
                continue;
            }

            for (int tankIndex = 0; tankIndex < storage.getTanks() && remaining > 0; tankIndex++) {
                FluidStack storedFuel = storage.getFluidInTank(tankIndex);
                if (storedFuel.isEmpty()) {
                    continue;
                }
                if (!desiredFuel.isEmpty() && !FluidStack.isSameFluidSameComponents(desiredFuel, storedFuel)) {
                    continue;
                }

                FluidStack drainedPreview = storage.drain(
                        storedFuel.copyWithAmount(Math.min(remaining, storedFuel.getAmount())),
                        IFluidHandler.FluidAction.SIMULATE);
                if (drainedPreview.isEmpty()) {
                    continue;
                }

                int fillable = consumer.fill(drainedPreview.copy(), IFluidHandler.FluidAction.SIMULATE);
                if (fillable <= 0) {
                    continue;
                }

                FluidStack transferred = storage.drain(drainedPreview.copyWithAmount(fillable),
                        IFluidHandler.FluidAction.EXECUTE);
                if (transferred.isEmpty()) {
                    continue;
                }

                int filled = consumer.fill(transferred, IFluidHandler.FluidAction.EXECUTE);
                if (filled <= 0) {
                    continue;
                }

                if (desiredFuel.isEmpty()) {
                    desiredFuel = transferred.copyWithAmount(1);
                }

                remaining -= filled;
                thruster.setChanged();
                storageBlockEntity.setChanged();
            }
        }
    }

    // Find the fluid handler
    private IFluidHandler findFluidHandler(BlockEntity blockEntity) {
        Level blockEntityLevel = blockEntity.getLevel();
        if (blockEntityLevel == null) {
            return null;
        }

        BlockPos blockEntityPos = blockEntity.getBlockPos();
        BlockState blockEntityState = blockEntity.getBlockState();
        IFluidHandler handler = Capabilities.FluidHandler.BLOCK.getCapability(blockEntityLevel, blockEntityPos,
                blockEntityState, blockEntity, null);
        if (handler != null) {
            return handler;
        }

        for (Direction dir : Direction.values()) {
            handler = Capabilities.FluidHandler.BLOCK.getCapability(blockEntityLevel, blockEntityPos,
                    blockEntityState, blockEntity, dir);
            if (handler != null) {
                return handler;
            }
        }

        return null;
    }

    // Find the energy handler
    private IEnergyStorage findEnergyHandler(BlockEntity blockEntity) {
        Level blockEntityLevel = blockEntity.getLevel();
        if (blockEntityLevel == null) {
            return null;
        }

        BlockPos blockEntityPos = blockEntity.getBlockPos();
        BlockState blockEntityState = blockEntity.getBlockState();
        IEnergyStorage handler = Capabilities.EnergyStorage.BLOCK.getCapability(blockEntityLevel, blockEntityPos,
                blockEntityState, blockEntity, null);
        if (handler != null) {
            return handler;
        }

        for (Direction dir : Direction.values()) {
            handler = Capabilities.EnergyStorage.BLOCK.getCapability(blockEntityLevel, blockEntityPos,
                    blockEntityState, blockEntity, dir);
            if (handler != null) {
                return handler;
            }
        }

        return null;
    }

    // Roll back the mounted assembly
    private void rollbackMountedAssembly(ServerLevel serverLevel) {
        try {
            mountedAssembly.disassemble(this, serverLevel);
        } catch (RuntimeException | LinkageError cleanupError) {
            try {
                mountedAssembly.clearInvalidAssembly(this, serverLevel);
            } catch (RuntimeException | LinkageError ignored) {
                mountedAssembly.releaseJoint();
                setMountedAssembly(null, null);
            }
        }
    }

    // Clean up the failed aim
    private void cleanupFailedAim(ServerLevel serverLevel) {
        if (mountedAssembly.hasMountedBlock(this, serverLevel)) {
            mountedAssembly.disassemble(this, serverLevel);
        } else {
            mountedAssembly.clearInvalidAssembly(this, serverLevel);
        }
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VectorBearingMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.vector_bearing.config.title");
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
        buffer.writeEnum(controlMode);
        buffer.writeDouble(maxTiltDegrees);
        writeMenuFrequency(buffer, Direction.NORTH);
        writeMenuFrequency(buffer, Direction.SOUTH);
        writeMenuFrequency(buffer, Direction.EAST);
        writeMenuFrequency(buffer, Direction.WEST);
    }

    // Write the menu frequency
    private void writeMenuFrequency(RegistryFriendlyByteBuf buffer, Direction dir) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, getFrequencyFirst(dir));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, getFrequencySecond(dir));
    }

    // Write the vector bearing safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putString("ControlMode", controlMode.name());
        tag.putDouble("MaxTiltDegrees", maxTiltDegrees);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            tag.put(frequencyKey(dir), frequencyBindings.get(dir).toTag(provider));
        }
    }

    // Write the vector bearing
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putString("ControlMode", controlMode.name());
        tag.putDouble("MaxTiltDegrees", maxTiltDegrees);
        tag.putDouble("ComputerXDegrees", computerXDegrees);
        tag.putDouble("ComputerZDegrees", computerZDegrees);
        tag.putBoolean("ComputerOverrideActive", computerOverrideActive);
        tag.putDouble("LinkedXDegrees", linkedXDegrees);
        tag.putDouble("LinkedZDegrees", linkedZDegrees);
        tag.putLong("LastLinkedPayloadTick", lastLinkedPayloadTick);
        tag.putDouble("TargetXDegrees", targetXDegrees);
        tag.putDouble("TargetZDegrees", targetZDegrees);
        tag.putDouble("AppliedXDegrees", appliedXDegrees);
        tag.putDouble("AppliedZDegrees", appliedZDegrees);
        tag.putString("ActiveControlMode", activeControlMode.name());
        tag.putDouble("ShaftAngleDegrees", shaftAngleDegrees);
        tag.putBoolean("ShaftAngleInitialized", shaftAngleInitialized);
        UUID mountedId = mountedSubLevelId;
        BlockPos mountedPos = mountedLocalPos;
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
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
            tag.putUUID("MountedSubLevel", mountedId);
        }
        if (mountedPos != null) {
            tag.putLong("MountedLocalPos", mountedPos.asLong());
        }
        tag.putBoolean("MountedAssemblyPresent",
                mountedAssemblyPresent && mountedId != null && mountedPos != null);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            FrequencyBinding binding = frequencyBindings.get(dir);
            if (binding.isBound()) {
                tag.put(frequencyKey(dir), binding.toTag(provider));
            }
        }
        for (Direction dir : Direction.values()) {
            tag.putInt(signalKey(dir), lastSignals.getOrDefault(dir, 0));
        }
    }

    // Read the vector bearing
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        controlMode = readEnum(tag, "ControlMode", ControlMode.AUTO);
        maxTiltDegrees = Mth.clamp(tag.contains("MaxTiltDegrees") ? tag.getDouble("MaxTiltDegrees") : DEFAULT_MAX_TILT_DEGREES,
                MIN_MAX_TILT_DEGREES, MAX_MAX_TILT_DEGREES);
        computerXDegrees = tag.getDouble("ComputerXDegrees");
        computerZDegrees = tag.getDouble("ComputerZDegrees");
        computerOverrideActive = tag.getBoolean("ComputerOverrideActive");
        linkedXDegrees = tag.getDouble("LinkedXDegrees");
        linkedZDegrees = tag.getDouble("LinkedZDegrees");
        lastLinkedPayloadTick = tag.contains("LastLinkedPayloadTick") ? tag.getLong("LastLinkedPayloadTick") : Long.MIN_VALUE;
        appliedXDegrees = tag.getDouble("AppliedXDegrees");
        appliedZDegrees = tag.getDouble("AppliedZDegrees");
        targetXDegrees = tag.contains("TargetXDegrees") ? tag.getDouble("TargetXDegrees") : appliedXDegrees;
        targetZDegrees = tag.contains("TargetZDegrees") ? tag.getDouble("TargetZDegrees") : appliedZDegrees;
        activeControlMode = readEnum(tag, "ActiveControlMode", ControlMode.AUTO);
        shaftAngleDegrees = tag.getDouble("ShaftAngleDegrees");
        shaftAngleInitialized = tag.getBoolean("ShaftAngleInitialized");
        lastShaftSampleTick = Long.MIN_VALUE;
        clampTiltState();
        mountedSubLevelId = tag.hasUUID("MountedSubLevel") ? tag.getUUID("MountedSubLevel") : null;
        mountedLocalPos = tag.contains("MountedLocalPos") ? BlockPos.of(tag.getLong("MountedLocalPos")) : null;
        SubLevelSchematicSerializationContext schematicContext =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (mountedSubLevelId != null
                && schematicContext != null
                && schematicContext.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping =
                    schematicContext.getMapping(mountedSubLevelId);
            if (mapping != null) {
                mountedSubLevelId = mapping.newUUID();
                mountedLocalPos = mountedLocalPos == null
                        ? null
                        : mapping.transform().apply(mountedLocalPos);
            }
        }
        mountedAssemblyPresent = mountedSubLevelId != null && mountedLocalPos != null
                && (!tag.contains("MountedAssemblyPresent") || tag.getBoolean("MountedAssemblyPresent"));
        invalidateMountedSailPower();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            FrequencyBinding binding = frequencyBindings.get(dir);
            if (tag.contains(frequencyKey(dir))) {
                binding.read(tag.getCompound(frequencyKey(dir)), provider);
            } else {
                binding.clear();
            }
            cachedWirelessSignals.put(dir, 0);
        }
        for (Direction dir : Direction.values()) {
            lastSignals.put(dir, tag.getInt(signalKey(dir)));
        }
        lastWirelessSignalSampleTick = Long.MIN_VALUE;
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
        return new AABB(worldPosition).minmax(new AABB(getMountedBlockPos())).inflate(2.0D);
    }

    // Destroy the vector bearing
    @Override
    public void destroy() {
        if (assemblyTransferInProgress) {
            mountedAssembly.releaseJoint();
            super.destroy();
            return;
        }
        try {
            disassembleMountedBlock();
        } catch (RuntimeException | LinkageError ignored) {
            ServerLevel serverLevel = SableLevelApi.serverLevel(level);
            if (serverLevel != null) {
                mountedAssembly.removeHeadAfterFailedDisassembly(this, serverLevel);
            } else {
                mountedAssembly.releaseJoint();
                setMountedAssembly(null, null);
            }
        }
        super.destroy();
    }

    // Get the connection dependencies
    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null || mountedSubLevelId == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevel mountedSubLevel = container.getSubLevel(mountedSubLevelId);
        return mountedSubLevel == null || mountedSubLevel.isRemoved() ? null : List.of(mountedSubLevel);
    }

    // Get the block direction
    @Override
    public Direction getBlockDirection() {
        return getBearingFacing();
    }

    // Get the airflow
    @Override
    public double getAirflow() {
        return propellerAirflow(mountedSailPower, getDirIndependentSpeed(), getPropellerAirflowScale());
    }

    // Get the thrust
    @Override
    public double getThrust() {
        return propellerThrust(mountedSailPower, getDirIndependentSpeed(), getPropellerThrustScale());
    }

    // Check if this is active
    @Override
    public boolean isActive() {
        return mountedSubLevelId != null && mountedSailPower > 0.0D && Math.abs(getSpeed()) > 0.01D;
    }

    // Get the dir independent speed
    private double getDirIndependentSpeed() {
        return getBearingFacing().getAxisDirection().getStep() * getSpeed();
    }

    // Get the propeller airflow scale
    private static double getPropellerAirflowScale() {
        Number scale = AeroConfig.server().physics.propellerBearingAirflowMult.get();
        return scale.doubleValue();
    }

    // Get the propeller thrust scale
    private static double getPropellerThrustScale() {
        Number scale = AeroConfig.server().physics.propellerBearingThrust.get();
        return scale.doubleValue();
    }

    // Get the propeller airflow
    static double propellerAirflow(double sailPower, double speed, double scale) {
        return Math.sqrt(Math.max(0.0D, sailPower)) * speed * scale;
    }

    // Get the propeller thrust
    static double propellerThrust(double sailPower, double speed, double scale) {
        return Math.pow(Math.max(0.0D, sailPower), 1.5D) * speed * scale;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.vector_bearing")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.vector_bearing.mode"),
                CTTooltipHelper.value(controlMode.name().toLowerCase(Locale.ROOT), ChatFormatting.AQUA)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.vector_bearing.active"),
                CTTooltipHelper.value(activeControlMode.name().toLowerCase(Locale.ROOT), ChatFormatting.GOLD)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.vector_bearing.tilt"),
                CTTooltipHelper.value(String.format(Locale.ROOT, "%.1f / %.1f", appliedXDegrees, appliedZDegrees),
                        ChatFormatting.GREEN)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.vector_bearing.assembly"),
                CTTooltipHelper.linked(isMountedAssemblyPresent())));
        return true;
    }

    // Handle the assembly fluid distributor
    private static class AssemblyFluidDistributor implements IFluidHandler {
        // Bearing
        private final VectorBearingBlockEntity bearing;

        // Initialize the assembly fluid distributor
        public AssemblyFluidDistributor(VectorBearingBlockEntity bearing) {
            this.bearing = bearing;
        }

        // Get the tanks
        @Override
        public int getTanks() {
            return 1;
        }

        // Get the fluid in tank
        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0) {
                return FluidStack.EMPTY;
            }

            FluidStack aggregate = FluidStack.EMPTY;
            for (IFluidHandler handler : bearing.collectAttachedFluidHandlers(FluidStack.EMPTY)) {
                for (int idx = 0; idx < handler.getTanks(); idx++) {
                    FluidStack fluidInTank = handler.getFluidInTank(idx);
                    if (fluidInTank.isEmpty()) {
                        continue;
                    }

                    if (aggregate.isEmpty()) {
                        aggregate = fluidInTank.copy();
                        continue;
                    }

                    if (!FluidStack.isSameFluidSameComponents(aggregate, fluidInTank)) {
                        return FluidStack.EMPTY;
                    }

                    aggregate.setAmount(aggregate.getAmount() + fluidInTank.getAmount());
                }
            }

            return aggregate;
        }

        // Get the tank capacity
        @Override
        public int getTankCapacity(int tank) {
            if (tank != 0) {
                return 0;
            }

            int totalCapacity = 0;
            for (IFluidHandler handler : bearing.collectAttachedFluidHandlers(FluidStack.EMPTY)) {
                for (int idx = 0; idx < handler.getTanks(); idx++) {
                    totalCapacity += handler.getTankCapacity(idx);
                }
            }
            return totalCapacity;
        }

        // Check if the fluid is valid
        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank != 0 || stack.isEmpty()) {
                return false;
            }

            for (IFluidHandler handler : bearing.collectAttachedFluidHandlers(stack)) {
                if (OxidizedFuelStorageAccess.allow(() ->
                        handler.fill(stack.copyWithAmount(1), FluidAction.SIMULATE)) > 0) {
                    return true;
                }
            }
            return false;
        }

        // Fill the assembly fluid distributor
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }

            List<IFluidHandler> activeTargets = new ArrayList<>(bearing.collectAttachedFluidHandlers(resource));
            if (activeTargets.isEmpty()) {
                return 0;
            }

            int remaining = resource.getAmount();
            while (remaining > 0 && !activeTargets.isEmpty()) {
                int share = Math.max(1, Mth.ceil((double) remaining / (double) activeTargets.size()));
                List<IFluidHandler> nextTargets = new ArrayList<>(activeTargets.size());
                boolean movedFluid = false;

                for (IFluidHandler handler : activeTargets) {
                    if (remaining <= 0) {
                        break;
                    }

                    FluidStack req = resource.copyWithAmount(Math.min(share, remaining));
                    int filled = OxidizedFuelStorageAccess.allow(() -> handler.fill(req, action));
                    if (filled > 0) {
                        remaining -= filled;
                        movedFluid = true;
                    }

                    if (OxidizedFuelStorageAccess.allow(() ->
                            handler.fill(resource.copyWithAmount(1), FluidAction.SIMULATE)) > 0) {
                        nextTargets.add(handler);
                    }
                }

                if (!movedFluid) {
                    break;
                }

                activeTargets = nextTargets;
            }

            return resource.getAmount() - remaining;
        }

        // Drain the assembly fluid distributor
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        // Drain the assembly fluid distributor
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // Handle the assembly energy distributor
    private static class AssemblyEnergyDistributor implements IEnergyStorage {
        // Bearing
        private final VectorBearingBlockEntity bearing;

        // Initialize the assembly energy distributor
        public AssemblyEnergyDistributor(VectorBearingBlockEntity bearing) {
            this.bearing = bearing;
        }

        // Receive the energy
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }

            List<IEnergyStorage> activeTargets = new ArrayList<>(bearing.collectAttachedEnergyHandlers());
            if (activeTargets.isEmpty()) {
                return 0;
            }

            int remaining = maxReceive;
            while (remaining > 0 && !activeTargets.isEmpty()) {
                int share = Math.max(1, Mth.ceil((double) remaining / (double) activeTargets.size()));
                List<IEnergyStorage> nextTargets = new ArrayList<>(activeTargets.size());
                boolean movedEnergy = false;

                for (IEnergyStorage handler : activeTargets) {
                    if (remaining <= 0) {
                        break;
                    }

                    int accepted = handler.receiveEnergy(Math.min(share, remaining), simulate);
                    if (accepted > 0) {
                        remaining -= accepted;
                        movedEnergy = true;
                    }

                    if (handler.canReceive() && handler.receiveEnergy(1, true) > 0) {
                        nextTargets.add(handler);
                    }
                }

                if (!movedEnergy) {
                    break;
                }

                activeTargets = nextTargets;
            }

            return maxReceive - remaining;
        }

        // Extract the energy
        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        // Get the energy stored
        @Override
        public int getEnergyStored() {
            int total = 0;
            for (IEnergyStorage handler : bearing.collectAttachedEnergyHandlers()) {
                total += handler.getEnergyStored();
            }
            return total;
        }

        // Get the max energy stored
        @Override
        public int getMaxEnergyStored() {
            int total = 0;
            for (IEnergyStorage handler : bearing.collectAttachedEnergyHandlers()) {
                total += handler.getMaxEnergyStored();
            }
            return total;
        }

        // Check if this can extract
        @Override
        public boolean canExtract() {
            return false;
        }

        // Check if this can receive
        @Override
        public boolean canReceive() {
            return true;
        }
    }

    // Get the frequency key
    private static String frequencyKey(Direction dir) {
        return "Frequency" + dir.getName().toUpperCase(Locale.ROOT);
    }

    // Get the signal key
    private static String signalKey(Direction dir) {
        return "Signal" + dir.getName().toUpperCase(Locale.ROOT);
    }

    // Create the frequency bindings
    private static EnumMap<Direction, FrequencyBinding> createFrequencyBindings() {
        EnumMap<Direction, FrequencyBinding> bindings = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            bindings.put(dir, new FrequencyBinding("vector_bearing_" + dir.getName()));
        }
        return bindings;
    }

    // Define the control mode values
    public enum ControlMode {
        AUTO,
        COMPUTER,
        REDSTONE
    }

    // Store the tilt command
    private record TiltCommand(double xDegrees, double zDegrees, Vec3 direction, ControlMode sourceMode,
                               boolean active) {
        // Get the neutral
        static TiltCommand neutral(ControlMode sourceMode) {
            return new TiltCommand(0.0D, 0.0D, new Vec3(0.0D, 1.0D, 0.0D), sourceMode, false);
        }

        // Create the tilt command from local vector
        static TiltCommand fromLocalVector(double localX, double localZ, double maxTiltDegrees,
                                           ControlMode sourceMode, boolean active) {
            return fromDegrees(localZ * maxTiltDegrees, localX * maxTiltDegrees,
                    maxTiltDegrees, sourceMode, active);
        }

        // Create the tilt command from degrees
        static TiltCommand fromDegrees(double xDegrees, double zDegrees, double maxTiltDegrees,
                                       ControlMode sourceMode, boolean active) {
            double max = Math.max(0.0D, maxTiltDegrees);
            double length = Math.hypot(xDegrees, zDegrees);
            if (length > max && length > 1.0E-6D) {
                double scale = max / length;
                xDegrees *= scale;
                zDegrees *= scale;
            }
            Vec3 dir = OrientationMath.directionFromAngles(Math.toRadians(xDegrees), Math.toRadians(zDegrees));
            return new TiltCommand(xDegrees, zDegrees, dir, sourceMode, active);
        }

        // Copy the tilt command with the source
        TiltCommand withSource(ControlMode sourceMode) {
            return new TiltCommand(xDegrees, zDegrees, direction, sourceMode, active);
        }
    }

    // Store the direct tilt input
    private record DirectTiltInput(double localX, double localZ, double value) {
    }

    // Store the mounted sail sample
    private record MountedSailSample(double power, double radius) {
        private static final MountedSailSample EMPTY = new MountedSailSample(0.0D, 0.0D);
    }
}
