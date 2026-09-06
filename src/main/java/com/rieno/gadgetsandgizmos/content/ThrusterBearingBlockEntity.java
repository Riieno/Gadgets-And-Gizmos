package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedPreciseAngleCompat;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDataProvider;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.control.LinkedOrientationSource;
import com.rieno.gadgetsandgizmos.lib.control.OrientationMath;
import com.rieno.gadgetsandgizmos.lib.control.OrientationPayload;
import com.rieno.gadgetsandgizmos.lib.control.OrientationTarget;
import com.rieno.gadgetsandgizmos.lib.kinetics.KineticAngleHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.util.CTPropulsionTelemetry;
import com.rieno.gadgetsandgizmos.util.OxidizedFuelStorageAccess;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlock;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import dev.simulated_team.simulated.data.advancements.SimAdvancements;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyException;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import java.lang.reflect.Method;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Aim attached thrusters from SCM and direct controls
public class ThrusterBearingBlockEntity extends SwivelBearingBlockEntity implements INamedBlockEntity,
        IDirectControlReceiver, LinkedOrientationSource, OrientationTarget, AdvancedGraphDataProvider,
        SmartGearboxServoAngleAcceptor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float THRUSTER_TOO_FAST_TOOLTIP_RPM = 256.0f;
    private static final long ASSEMBLED_PLATE_LOAD_RETRY_TICKS = 100L;
    private static final Component RANGE_LABEL = Component.translatable("createthrusters.thruster_bearing.angle_range");
    private static final double SWIVEL_MIN_ANGLE_DEG = 0.0D;
    private static final double SWIVEL_MAX_ANGLE_DEG = 360.0D;
    private static final Field TARGET_ANGLE_FIELD = getDeclaredField("targetAngleDegrees");
    private static final Field LAST_TARGET_ANGLE_FIELD = getDeclaredField("lastTargetAngleDegrees");
    private static final Field LOCK_OPTION_FIELD = getDeclaredField("lockedDefaultOption");
    private static final Field HANDLE_FIELD = getDeclaredField("handle");
    private static final Field ASSEMBLING_FIELD = getDeclaredField("assembling");
    private static final String[] ROTARY_CONSTRAINT_CONFIGURATION_CLASSES = {
            "dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration",
            "dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration"
    };

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Assembly fluid handler
    private final IFluidHandler assemblyFluidHandler = new AssemblyFluidDistributor(this);

    // Assembly energy handler
    private final IEnergyStorage assemblyEnergyHandler = new AssemblyEnergyDistributor(this);

    // Current forward signal
    private int forwardSignal;
    // Current backward signal
    private int backwardSignal;
    // Tracked direct controller signals
    protected final Map<String, Float> directControllerSignals = new LinkedHashMap<>();
    // Current control mode
    private ControlMode controlMode = ControlMode.AUTO;
    // Current angle mode
    private AngleMode angleMode = AngleMode.RANGE;
    // Tracks whether inverted is set
    private boolean inverted;
    // Tracks whether computer override is active
    private boolean computerOverrideActive;
    // Computer target angle in degrees
    private double computerTargetAngleDeg;
    // Min angle in degrees
    private double minAngleDeg;
    // Max angle in degrees
    private double maxAngleDeg;
    // Last requested target angle in degrees
    private double lastRequestedTargetAngleDeg;
    // Last aim angle initialized state
    private boolean lastAimAngleInitialized;
    // Last aim angle in degrees
    private double lastAimAngleDeg;
    // Last target change tick
    private long lastTargetChangeTick;
    // Tracks whether tracked servo input is initialized
    private boolean trackedServoInputInitialized;
    // Tracked servo input angle in degrees
    private double trackedServoInputAngleDeg;
    // Last servo input sample tick
    private long lastServoInputSampleTick = Long.MIN_VALUE;
    // Last servo input source pos
    private BlockPos lastServoInputSourcePos;
    // Last servo input network id
    private Long lastServoInputNetworkId;
    // Gearbox servo input angle in degrees
    private double gearboxServoInputAngleDeg;
    // Last gearbox servo input tick
    private long lastGearboxServoInputTick = Long.MIN_VALUE;
    // Last orientation payload tick
    private long lastOrientationPayloadTick = Long.MIN_VALUE;
    // Next assembled plate load retry tick
    private long nextAssembledPlateLoadRetryTick = Long.MIN_VALUE;
    // Previous rendered angle in degrees
    private double previousRenderedAngleDeg;
    // Rendered angle in degrees
    private double renderedAngleDeg;
    // Last synced forward signal
    private int lastSyncedForwardSignal = Integer.MIN_VALUE;
    // Last synced backward signal
    private int lastSyncedBackwardSignal = Integer.MIN_VALUE;
    // Last synced target angle in degrees
    private double lastSyncedTargetAngleDeg = Double.NaN;
    // Last synced min angle in degrees
    private double lastSyncedMinAngleDeg = Double.NaN;
    // Last synced max angle in degrees
    private double lastSyncedMaxAngleDeg = Double.NaN;
    // Last synced computer override state
    private boolean lastSyncedComputerOverride;
    // Last synced inverted state
    private boolean lastSyncedInverted;
    // Last synced control mode
    private ControlMode lastSyncedControlMode;
    // Last synced angle mode
    private AngleMode lastSyncedAngleMode;
    // Tracked thruster aliases
    private final Map<String, String> thrusterAliases = new LinkedHashMap<>();
    // Current custom name
    @org.jetbrains.annotations.Nullable
    private String customName;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing
    public ThrusterBearingBlockEntity(BlockPos pos, BlockState state) {
        this(CTBlockEntities.THRUSTER_BEARING.get(), pos, state);
    }

    // Initialize the thruster bearing
    protected ThrusterBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        double configLimit = CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        minAngleDeg = -configLimit;
        maxAngleDeg = configLimit;
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

        removeLockingOptionBehaviour(behaviours);

        setLockingOptionAlwaysLocked();

        behaviours.add(new AngleRangeScreenBehaviour(this, new AngleRangeValueBox()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the thruster bearing
    @Override
    public void tick() {

        double previousTarget = getTargetAngleDegrees();
        super.tick();

        if (level == null || level.isClientSide) {
            previousRenderedAngleDeg = renderedAngleDeg;
            renderedAngleDeg = getTargetAngleDegrees();
            return;
        }

        if (skipAssembledPlateTick()) {
            return;
        }

        forceServoLock();
        checkThrusterLinks();
        updateSignals(level, worldPosition);
        updateTargetAngle(previousTarget);
        if (shouldUpdateBearingConstraintInParentTick()) {
            updateServoCoefficients();
        }
        boolean thrusterIdsChanged = refreshThrusterBindings();
        redistributeAssemblyFuel();

        ThrusterBlockEntity attachedThruster = getAttachedThruster();
        if (attachedThruster != null) {
            attachedThruster.setOvrDir(null);
        }
        if (thrusterIdsChanged) {
            setChanged();
            sendData();
        }
        syncBearingClientData();
    }

    // Check whether to skip the assembled plate tick
    private boolean skipAssembledPlateTick() {
        if (level == null || level.isClientSide || !isAssembled()) {
            nextAssembledPlateLoadRetryTick = Long.MIN_VALUE;
            return false;
        }
        BlockPos platePos = getPlatePos();
        if (platePos == null) {
            nextAssembledPlateLoadRetryTick = Long.MIN_VALUE;
            return false;
        }
        UUID subLevelId = getSubLevelID();
        if (!SubLevelBlockEntityCollector.isTargetLoaded(level, subLevelId, platePos)) {
            if (subLevelId == null) {
                return true;
            }
            long gameTime = level.getGameTime();
            if (gameTime < nextAssembledPlateLoadRetryTick) {
                return true;
            }
            nextAssembledPlateLoadRetryTick = gameTime + ASSEMBLED_PLATE_LOAD_RETRY_TICKS;
            if (!SubLevelBlockEntityCollector.ensureTargetLoaded(level, subLevelId, platePos)) {
                return true;
            }
        }
        nextAssembledPlateLoadRetryTick = Long.MIN_VALUE;
        BlockState plateState = level.getBlockState(platePos);
        if (plateState.getBlock() instanceof SwivelBearingPlateBlock
                || plateState.getBlock() instanceof ThrusterBearingLinkBlock) {
            return false;
        }
        removeThrusterLinkHandle();
        setSubLevelID(null);
        setPlatePos(null);
        nextAssembledPlateLoadRetryTick = Long.MIN_VALUE;
        setBaseTargetAngles(0.0D, 0.0D);
        BlockState state = getBlockState();
        if (state.hasProperty(SwivelBearingBlock.ASSEMBLED)
                && state.getValue(SwivelBearingBlock.ASSEMBLED)) {
            level.setBlockAndUpdate(worldPosition, state.setValue(SwivelBearingBlock.ASSEMBLED, false));
        }
        setChanged();
        sendData();
        return true;
    }

    // Check if this should update bearing constraint in parent tick
    protected boolean shouldUpdateBearingConstraintInParentTick() {
        return true;
    }

    // Destroy the thruster bearing
    @Override
    public void destroy() {
        if (level != null && !level.isClientSide && !isBearingAssemblyTransfer()) {
            BlockPos platePos = getPlatePos();
            if (platePos != null) {
                removeThrusterLinkHandle();
                destroyThrusterLink(platePos);
            }
        }
        super.destroy();
    }

    // Get the connection dependencies
    @Override
    public @org.jetbrains.annotations.Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null || getSubLevelID() == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        SubLevel mountedSubLevel = container.getSubLevel(getSubLevelID());
        return mountedSubLevel == null || mountedSubLevel.isRemoved() ? null : List.of(mountedSubLevel);
    }

    // Handle the state before assembly
    @Override
    public void beforeAssembly() {
        removeThrusterLinkHandle();
        super.beforeAssembly();
    }

    // Remove the thruster bearing
    @Override
    public void remove() {
        beforeAssembly();
        super.remove();
    }

    // Assemble the thruster bearing
    @Override
    public void assemble() {
        // ------------------------------------ASSEMBLY CHECK------------------------------------
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }

        BlockPos pos = getBlockPos();
        Direction facing = getFacing();
        BlockPos toAssemble = pos.relative(facing);
        SimAssemblyHelper.AssemblyResult res;
        try {
            res = SimAssemblyHelper.assembleFromSingleBlock(serverLevel, pos, toAssemble, false, false);
            lastException = null;
        } catch (AssemblyException err) {
            lastException = err;
            sendData();
            return;
        }
        sendData();

        BlockState link = customLinkState();
        ServerSubLevel assembledSubLevel;
        BlockPos assembleOffset;
        if (res != null) {
            assembledSubLevel = (ServerSubLevel) res.subLevel();
            assembleOffset = res.offset();
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) {
                return;
            }

            NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(this);
            SubLevel containingSubLevel = parentFrame.parent();
            if (parentFrame.isRemoved()) {
                return;
            }
            Pose3d pose = new Pose3d();
            pose.position().set(parentFrame.toWorldPosition(new Vector3d(
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)));
            pose.orientation().set(parentFrame.toWorldOrientation(new Quaterniond()));
            SubLevel allocated = container.allocateNewSubLevel(pose);
            if (!(allocated instanceof ServerSubLevel childSubLevel)) {
                return;
            }
            assembledSubLevel = childSubLevel;

            ServerLevelPlot plot = assembledSubLevel.getPlot();
            ChunkPos center = plot.getCenterChunk();
            plot.newEmptyChunk(center);
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, link, 3);
            if (!LinkOnlySubLevelPhysics.refresh(assembledSubLevel, container)) {
                assembledSubLevel = null;
                return;
            }

            BlockPos plotAnchor = plot.getCenterBlock();
            Vector3dc centerOfMass = assembledSubLevel.getMassTracker().getCenterOfMass();
            Vector3d subLevelCenter = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
            subLevelCenter.add(centerOfMass.x() - plotAnchor.getX(),
                    centerOfMass.y() - plotAnchor.getY(),
                    centerOfMass.z() - plotAnchor.getZ());
            assembledSubLevel.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);
            assembleOffset = plotAnchor.subtract(pos);

            SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
            PhysicsPipeline pipeline = physicsSystem.getPipeline();
            if (containingSubLevel != null) {
                assembledSubLevel.logicalPose().orientation().set(
                        parentFrame.toWorldOrientation(new Quaterniond()));
                SubLevelAssemblyHelper.kickFromContainingSubLevel(serverLevel, physicsSystem, pipeline,
                        assembledSubLevel, containingSubLevel);
            }
            pipeline.teleport((PhysicsPipelineBody) assembledSubLevel, assembledSubLevel.logicalPose().position(),
                    assembledSubLevel.logicalPose().orientation());
            assembledSubLevel.updateLastPose();
            level.playSound(null, pos, SimSoundEvents.SIMULATED_CONTRAPTION_MOVES.event(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        getLevel().setBlockAndUpdate(pos, getBlockState().setValue(SwivelBearingBlock.ASSEMBLED, true));
        setSubLevelID(assembledSubLevel.getUniqueId());

        BlockPos plotPos = pos.offset(assembleOffset);
        setPlatePos(plotPos);
        if (res != null) {
            getLevel().setBlockAndUpdate(plotPos, link);
        }
        if (getLevel().getBlockEntity(plotPos) instanceof ThrusterBearingLinkBlockEntity linkBlockEntity) {
            linkBlockEntity.setParent(this);
        }

        // ------------------------------------LINK ATTACHMENT------------------------------------
        if (!attachThrusterLinks(assembledSubLevel, centerOf(toAssemble.offset(assembleOffset)))) {
            disassemble();
            return;
        }
        SimAdvancements.YOU_SPIN_ME_RIGHT_ROUND.awardToNearby(pos, getLevel());
    }

    // Disassemble the thruster bearing
    @Override
    public void disassemble() {
        if (isRemoved() || level == null) {
            return;
        }

        removeThrusterLinkHandle();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container != null && getSubLevelID() != null
                ? container.getSubLevel(getSubLevelID())
                : null;
        BlockPos platePos = getPlatePos();
        if (platePos != null) {
            destroyThrusterLink(platePos);
            if (Objects.equals(subLevel, Sable.HELPER.getContaining(level, getBlockPos()))) {
                lastException = SimAssemblyException.sameSubLevel();
                level.playSound(null, platePos, SimSoundEvents.ASSEMBLER_FAIL.event(), SoundSource.BLOCKS, 1.0f, 1.0f);
            } else if (subLevel != null) {
                ServerLevel serverLevel = SableLevelApi.serverLevel(level);
                if (!subLevel.isRemoved() && serverLevel != null) {
                    SimAssemblyHelper.disassembleSubLevel(serverLevel, subLevel, platePos, getBlockPos(), Rotation.NONE, true);
                } else {
                    level.playSound(null, platePos, SimSoundEvents.SIMULATED_CONTRAPTION_STOPS.event(),
                            SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }
        }

        getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(SwivelBearingBlock.ASSEMBLED, false));
        setSubLevelID(null);
        setPlatePos(null);
        setBaseTargetAngles(0.0D, 0.0D);
        sendData();
    }

    // Reattach the constraint
    @Override
    public void reattachConstraint(@org.jetbrains.annotations.Nullable ServerSubLevel plateSubLevel, boolean updatePlate) {
        BlockPos platePos = getPlatePos();
        if (platePos == null || level == null) {
            return;
        }

        removeThrusterLinkHandle();
        if (updatePlate) {
            associatePlateWithParent();
        }

        BlockState plateState = level.getBlockState(platePos);
        if (isThrusterBearingLink(plateState)) {
            Direction plateFacing = plateState.getValue(SwivelBearingPlateBlock.FACING);
            if (!attachThrusterLinks(plateSubLevel, centerOf(platePos.relative(plateFacing)))) {
                disassemble();
            }
            return;
        }

        super.reattachConstraint(plateSubLevel, updatePlate);
    }

    // Associate the plate with parent
    @Override
    public void associatePlateWithParent() {
        BlockPos platePos = getPlatePos();
        if (platePos != null && level != null && isThrusterBearingLink(level.getBlockState(platePos))
                && level.getBlockEntity(platePos) instanceof ThrusterBearingLinkBlockEntity linkBlockEntity) {
            linkBlockEntity.setParent(this);
            return;
        }
        super.associatePlateWithParent();
    }

    // Get the custom link state
    private BlockState customLinkState() {
        return CTBlocks.THRUSTER_BEARING_LINK.get().defaultBlockState()
                .setValue(SwivelBearingPlateBlock.FACING, getFacing());
    }

    // Validate the thruster links
    private void checkThrusterLinks() {
        if (level == null || !isAssembled() || getSubLevelID() == null) {
            return;
        }
        BlockPos platePos = getPlatePos();
        if (platePos == null) {
            return;
        }
        BlockState plateState = level.getBlockState(platePos);
        if (!isThrusterBearingLink(plateState)) {
            return;
        }

        validateThrusterLinkHandle();
        if (getThrusterLinkHandle() != null) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(getSubLevelID());
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            reattachConstraint(serverSubLevel, true);
        }
    }

    // Check if this is a thruster bearing link
    private boolean isThrusterBearingLink(BlockState state) {
        return state.getBlock() instanceof ThrusterBearingLinkBlock;
    }

    // Check if this owns link
    boolean ownsLink(ThrusterBearingLinkBlockEntity link) {
        return link != null
                && isAssembled()
                && Objects.equals(getPlatePos(), link.getBlockPos())
                && Objects.equals(getSubLevelID(),
                        SimulatedHelper.getContainingSubLevelId(link));
    }

    // Get the center
    private static Vector3d centerOf(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    // Get the vector
    private static Vector3d vectorOf(Direction dir) {
        return new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // Attach the thruster links
    private boolean attachThrusterLinks(@org.jetbrains.annotations.Nullable ServerSubLevel plateSubLevel,
                                                  Vector3d attachPos) {
        BlockPos platePos = getPlatePos();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || platePos == null) {
            return false;
        }
        BlockState plateState = level.getBlockState(platePos);
        if (!isThrusterBearingLink(plateState)) {
            return false;
        }

        Direction facing = getFacing();
        Direction plateFacing = plateState.getValue(SwivelBearingPlateBlock.FACING);
        Vector3d anchorPos = centerOf(getBlockPos().relative(facing));
        Vector3d facingVec = vectorOf(facing);
        Vector3d plateFacingVec = vectorOf(plateFacing);
        Vector3d plateAttachPos = new Vector3d(attachPos).sub(new Vector3d(plateFacingVec).mul(0.001D));
        PhysicsConstraintConfiguration<PhysicsConstraintHandle> constraint =
                createRotaryConstraintConfiguration(anchorPos, plateAttachPos, facingVec, plateFacingVec);
        if (constraint == null) {
            return false;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return false;
        }
        NestedAssemblyFrame parentFrame = NestedAssemblyFrame.resolve(this);
        if (parentFrame.isRemoved()) {
            return false;
        }
        ServerSubLevel containingSubLevel = parentFrame.parentBody();
        if (containingSubLevel == plateSubLevel) {
            return false;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        PhysicsConstraintHandle handle = addThrusterLinkConstraint(pipeline, containingSubLevel, plateSubLevel, constraint);
        if (handle == null) {
            return false;
        }
        setThrusterLinkHandle(handle);
        return true;
    }

    // Create the rotary constraint configuration
    @SuppressWarnings("unchecked")
    private static @org.jetbrains.annotations.Nullable PhysicsConstraintConfiguration<PhysicsConstraintHandle>
    createRotaryConstraintConfiguration(Vector3dc anchorPos, Vector3dc plateAttachPos,
                                        Vector3dc facingVec, Vector3dc plateFacingVec) {
        for (String className : ROTARY_CONSTRAINT_CONFIGURATION_CLASSES) {
            try {
                Object constraint = Class.forName(className)
                        .getConstructor(Vector3dc.class, Vector3dc.class, Vector3dc.class, Vector3dc.class)
                        .newInstance(anchorPos, plateAttachPos, facingVec, plateFacingVec);
                if (constraint instanceof PhysicsConstraintConfiguration<?> physicsConstraintConfiguration) {
                    return (PhysicsConstraintConfiguration<PhysicsConstraintHandle>) physicsConstraintConfiguration;
                }
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            }
        }
        return null;
    }

    // Add the thruster link constraint
    private static @org.jetbrains.annotations.Nullable PhysicsConstraintHandle addThrusterLinkConstraint(
            PhysicsPipeline pipeline,
            @org.jetbrains.annotations.Nullable ServerSubLevel containingSubLevel,
            @org.jetbrains.annotations.Nullable ServerSubLevel plateSubLevel,
            PhysicsConstraintConfiguration<PhysicsConstraintHandle> constraint) {
        PhysicsConstraintHandle handle = addThrusterLinkConstraint(pipeline,
                PhysicsPipelineBody.class, (PhysicsPipelineBody) containingSubLevel, (PhysicsPipelineBody) plateSubLevel,
                constraint);
        if (handle != null) {
            return handle;
        }
        return addThrusterLinkConstraint(pipeline, ServerSubLevel.class, containingSubLevel, plateSubLevel, constraint);
    }

    // Add the thruster link constraint
    private static @org.jetbrains.annotations.Nullable PhysicsConstraintHandle addThrusterLinkConstraint(
            PhysicsPipeline pipeline,
            Class<?> bodyClass,
            @org.jetbrains.annotations.Nullable Object containingSubLevel,
            @org.jetbrains.annotations.Nullable Object plateSubLevel,
            PhysicsConstraintConfiguration<PhysicsConstraintHandle> constraint) {
        try {
            Object handle = pipeline.getClass()
                    .getMethod("addConstraint", bodyClass, bodyClass, PhysicsConstraintConfiguration.class)
                    .invoke(pipeline, containingSubLevel, plateSubLevel, constraint);
            return handle instanceof PhysicsConstraintHandle physicsConstraintHandle ? physicsConstraintHandle : null;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
            return null;
        }
    }

    // Get the thruster link handle
    private PhysicsConstraintHandle getThrusterLinkHandle() {
        try {
            return (PhysicsConstraintHandle) HANDLE_FIELD.get(this);
        } catch (IllegalAccessException err) {
            throw new IllegalStateException("Failed to read swivel-bearing constraint handle", err);
        }
    }

    // Set the thruster link handle
    private void setThrusterLinkHandle(@org.jetbrains.annotations.Nullable PhysicsConstraintHandle handle) {
        try {
            HANDLE_FIELD.set(this, handle);
        } catch (IllegalAccessException err) {
            throw new IllegalStateException("Failed to update swivel-bearing constraint handle", err);
        }
    }

    // Remove the thruster link handle
    private void removeThrusterLinkHandle() {
        PhysicsConstraintHandle handle = getThrusterLinkHandle();
        if (handle != null) {
            try {
                if (handle.isValid()) {
                    handle.remove();
                }
            } catch (RuntimeException | LinkageError ignored) {
            } finally {
                setThrusterLinkHandle(null);
            }
        }
    }

    // Validate the thruster link handle
    private void validateThrusterLinkHandle() {
        PhysicsConstraintHandle handle = getThrusterLinkHandle();
        if (handle != null && !handle.isValid()) {
            setThrusterLinkHandle(null);
        }
    }

    // Check if this is bearing assembly transfer
    private boolean isBearingAssemblyTransfer() {
        try {
            return ASSEMBLING_FIELD.getBoolean(this);
        } catch (IllegalAccessException err) {
            throw new IllegalStateException("Failed to read swivel-bearing assembly state", err);
        }
    }

    // Destroy the thruster link
    private void destroyThrusterLink(BlockPos platePos) {
        if (level == null) {
            return;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container != null && getSubLevelID() != null
                ? container.getSubLevel(getSubLevelID())
                : null;

        BlockState plateState = level.getBlockState(platePos);
        if (isThrusterBearingLink(plateState)) {
            if (level.getBlockEntity(platePos) instanceof ThrusterBearingLinkBlockEntity linkBlockEntity) {
                linkBlockEntity.beforeAssembly();
            }
            level.setBlock(platePos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        if (plateState.getBlock() instanceof SwivelBearingPlateBlock) {
            if (level.getBlockEntity(platePos) instanceof SwivelBearingPlateBlockEntity plateBlockEntity) {
                plateBlockEntity.beforeAssembly();
            }
            level.setBlock(platePos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        if (!(subLevel instanceof ServerSubLevel child) || child.isRemoved()) {
            return;
        }

        BlockPos relativePos = platePos.subtract(child.getPlot().getCenterBlock());
        BlockState embeddedState = child.getPlot().getEmbeddedLevelAccessor().getBlockState(relativePos);
        if (isThrusterBearingLink(embeddedState)) {
            ThrusterBearingLinkBlockEntity linkBlockEntity = SimulatedHelper.findBlockEntityInSubLevel(
                    child, platePos, ThrusterBearingLinkBlockEntity.class);
            if (linkBlockEntity != null) {
                linkBlockEntity.beforeAssembly();
            }
            child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
            return;
        }
        if (embeddedState.getBlock() instanceof SwivelBearingPlateBlock) {
            SwivelBearingPlateBlockEntity plateBlockEntity = SimulatedHelper.findBlockEntityInSubLevel(
                    child, platePos, SwivelBearingPlateBlockEntity.class);
            if (plateBlockEntity != null) {
                plateBlockEntity.beforeAssembly();
            }
            child.getPlot().getEmbeddedLevelAccessor().setBlock(relativePos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // Update the signals
    private void updateSignals(Level level, BlockPos pos) {

        Direction forwardSide = getMarkedRedstoneSide(getBlockState(), true);
        Direction backwardSide = getMarkedRedstoneSide(getBlockState(), false);
        BlockPos forwardPos = pos.relative(forwardSide);
        BlockPos backwardPos = pos.relative(backwardSide);
        forwardSignal = Math.max(level.getSignal(forwardPos, forwardSide),
                level.getDirectSignal(forwardPos, forwardSide));
        backwardSignal = Math.max(level.getSignal(backwardPos, backwardSide),
                level.getDirectSignal(backwardPos, backwardSide));
        applyDirectSignalOverlay();
    }

    // Get the marked redstone side
    private static Direction getMarkedRedstoneSide(BlockState state, boolean forward) {
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.UP;
        boolean rolled = state.hasProperty(ThrusterBearingBlock.AXIS_ALONG_FIRST_COORDINATE)
                && state.getValue(ThrusterBearingBlock.AXIS_ALONG_FIRST_COORDINATE);
        Direction positiveSide = rolled ? getUnrolledModelEastSide(facing) : getUnrolledModelSouthSide(facing);
        return forward ? positiveSide : positiveSide.getOpposite();
    }

    // Get the unrolled model south side
    private static Direction getUnrolledModelSouthSide(Direction facing) {
        return switch (facing) {
            case DOWN -> Direction.NORTH;
            case UP -> Direction.SOUTH;
            case NORTH, SOUTH, EAST, WEST -> Direction.UP;
        };
    }

    // Get the unrolled model east side
    private static Direction getUnrolledModelEastSide(Direction facing) {
        return switch (facing) {
            case DOWN, UP, NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
        };
    }

    // Apply the direct signal overlay
    private void applyDirectSignalOverlay() {
        int overlayForward = getDirectSignalStrength(true);
        int overlayBackward = getDirectSignalStrength(false);
        forwardSignal = Math.max(forwardSignal, overlayForward);
        backwardSignal = Math.max(backwardSignal, overlayBackward);
    }

    // Get the direct signal strength
    protected int getDirectSignalStrength(boolean positive) {
        int strongest = 0;
        for (Map.Entry<String, Float> entry : directControllerSignals.entrySet()) {
            float normalizedValue = normalizeDirectInput(entry.getValue());
            float magnitude = Mth.clamp(Math.abs(normalizedValue), 0.0f, 1.0f);

            int strength = Mth.clamp((int) Math.ceil(magnitude * 15.0f), 0, 15);
            if (strength <= 0) {
                continue;
            }
            if (isPositiveCtrlInput(entry.getKey(), normalizedValue) == positive) {
                strongest = Math.max(strongest, strength);
            }
        }
        return strongest;
    }

    // Check if this is positive ctrl input
    private static boolean isPositiveCtrlInput(String channelId, float val) {
        boolean positive = isPositiveControllerChannel(channelId);
        return val < 0.0f ? !positive : positive;
    }

    // Normalize the direct input
    private static float normalizeDirectInput(float val) {
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

    // Check if this is a positive controller channel
    protected static boolean isPositiveControllerChannel(String channelId) {
        if (channelId == null) {
            return true;
        }
        String normalized = channelId.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith("_down") || normalized.endsWith("_left") || normalized.contains("_down") || normalized.contains("_left")) {
            return false;
        }
        return true;
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float value) {
        float clamped = normalizeDirectInput(value);
        boolean hadDirectSignals = !directControllerSignals.isEmpty();
        if (Math.abs(clamped) <= 0.0001f) {
            directControllerSignals.remove(channelId);
        } else {
            directControllerSignals.put(channelId, clamped);
        }
        if (level != null && !level.isClientSide) {

            if (angleMode == AngleMode.SWIVEL && !directControllerSignals.isEmpty()) {
                if (controlMode != ControlMode.COMPUTER) {
                    controlMode = ControlMode.COMPUTER;
                }
                computerOverrideActive = true;
                if (!hadDirectSignals) {
                    computerTargetAngleDeg = clampTargetAngle(getTargetAngleDegrees());
                    lastAimAngleDeg = computerTargetAngleDeg;
                    lastAimAngleInitialized = true;
                }
            }

            if (!directControllerSignals.isEmpty() && controlMode != ControlMode.COMPUTER) {
                lastRequestedTargetAngleDeg = clampTargetAngle(getTargetAngleDegrees());
            } else if (angleMode == AngleMode.SWIVEL) {

                lastRequestedTargetAngleDeg = clampTargetAngle(getTargetAngleDegrees());

                if (hadDirectSignals) {
                    lastAimAngleDeg = lastRequestedTargetAngleDeg;
                    lastAimAngleInitialized = true;
                    if (controlMode == ControlMode.COMPUTER && computerOverrideActive) {
                        computerTargetAngleDeg = lastRequestedTargetAngleDeg;
                    }
                }
            } else {
                lastRequestedTargetAngleDeg = computeRequestedTargetAngle(getServoInputAngleDegrees());
            }
            lastTargetChangeTick = level.getGameTime();
            setChanged();
            sendData();
        }
    }

    // Apply the gyroscope link control signal
    public void applyGyroscopeLinkControlSignal(String channelId, double rangeTargetAngleDeg, float swivelSignal) {
        if (level == null || level.isClientSide) {
            return;
        }
        String signalChannel = gyroscopeLinkControlChannel(channelId);
        if (angleMode == AngleMode.SWIVEL) {
            applyGyroscopeLinkSwivelSignal(signalChannel, swivelSignal);
            return;
        }
        directControllerSignals.remove(signalChannel);
        applyGearboxServoAngleDegrees(rangeTargetAngleDeg);
    }

    // Clear the gyroscope link control signal
    public void clearGyroscopeLinkControlSignal(String channelId) {
        if (level == null || level.isClientSide) {
            return;
        }
        String signalChannel = gyroscopeLinkControlChannel(channelId);
        if (directControllerSignals.remove(signalChannel) == null) {
            return;
        }
        double holdAngle = clampTargetAngle(getTargetAngleDegrees());
        lastRequestedTargetAngleDeg = holdAngle;
        lastAimAngleDeg = holdAngle;
        lastAimAngleInitialized = true;
        lastTargetChangeTick = level.getGameTime();
        setChanged();
        sendData();
    }

    // Apply the gyroscope link swivel signal
    private void applyGyroscopeLinkSwivelSignal(String channelId, float val) {
        float clamped = normalizeDirectInput(val);
        boolean hadDirectSignals = !directControllerSignals.isEmpty();
        if (Math.abs(clamped) <= 0.0001f) {
            directControllerSignals.remove(channelId);
        } else {
            directControllerSignals.put(channelId, clamped);
        }

        if (!directControllerSignals.isEmpty() && controlMode != ControlMode.COMPUTER) {
            lastRequestedTargetAngleDeg = clampTargetAngle(getTargetAngleDegrees());
        } else if (directControllerSignals.isEmpty() && hadDirectSignals) {
            double holdAngle = clampTargetAngle(getTargetAngleDegrees());
            lastRequestedTargetAngleDeg = holdAngle;
            lastAimAngleDeg = holdAngle;
            lastAimAngleInitialized = true;
        }

        lastTargetChangeTick = level.getGameTime();
        setChanged();
        sendData();
    }

    // Get the gyroscope link control channel
    private static String gyroscopeLinkControlChannel(String channelId) {
        return channelId == null || channelId.isBlank() ? "gyroscope_link" : channelId;
    }

    // Update the target angle
    private void updateTargetAngle(double previousTarget) {
        // ------------------------------------COMPUTER CONTROL------------------------------------
        boolean computerDirectDriveActive = false;

        if (angleMode == AngleMode.SWIVEL && controlMode == ControlMode.COMPUTER && computerOverrideActive) {
            double computerDirectIncrement = getDirectIncrementDeg();
            if (Math.abs(computerDirectIncrement) > 1.0E-4D) {

                double effectiveComputerTarget = clampTargetAngle(previousTarget + computerDirectIncrement);
                computerTargetAngleDeg = applyTargetInversion(effectiveComputerTarget);
                lastRequestedTargetAngleDeg = effectiveComputerTarget;
                lastAimAngleDeg = effectiveComputerTarget;
                lastAimAngleInitialized = true;
                computerDirectDriveActive = true;
            }
        }

        // -----------------------------------------------------NEUTRAL HOLD-----------------------------------------------------
        if (isHardSwivelNeutralHoldState()) {
            double holdAngle = clampTargetAngle(previousTarget);
            lastAimAngleDeg = holdAngle;
            lastAimAngleInitialized = true;
            lastRequestedTargetAngleDeg = holdAngle;
            previousRenderedAngleDeg = previousTarget;
            renderedAngleDeg = holdAngle;
            setBaseTargetAngles(previousTarget, holdAngle);
            return;
        }

        // -----------------------------------------------------INPUT TARGET-----------------------------------------------------
        double servoInputAngle = getServoInputAngleDegrees();
        double requestedTarget = computeRequestedTargetAngle(servoInputAngle);
        boolean directIncrementActive = false;
        double directIncrement = 0.0D;
        boolean redstoneSwivelIncrementActive = false;
        double redstoneSwivelIncrement = 0.0D;
        boolean orientationPayloadDriveActive = isRecentOrientationPayloadDrive();
        if (controlMode == ControlMode.REDSTONE && angleMode == AngleMode.SWIVEL) {
            redstoneSwivelIncrement = getRedstoneSwivelDeltaDeg();
            if (Math.abs(redstoneSwivelIncrement) > 1.0E-4D) {
                requestedTarget = clampTargetAngle(lastRequestedTargetAngleDeg + redstoneSwivelIncrement);
                redstoneSwivelIncrementActive = true;
            }
        }

        if (controlMode != ControlMode.COMPUTER && controlMode != ControlMode.REDSTONE) {
            directIncrement = getDirectIncrementDeg();
            if (Math.abs(directIncrement) > 1.0E-4D) {
                requestedTarget = clampTargetAngle(lastRequestedTargetAngleDeg + directIncrement);
                directIncrementActive = true;
            }
        }

        boolean holdSwivelNeutral = shouldHoldSwivelAtNeutralInput(servoInputAngle, directIncrementActive);
        if (holdSwivelNeutral) {

            lastAimAngleDeg = clampTargetAngle(previousTarget);
            lastAimAngleInitialized = true;
            requestedTarget = lastAimAngleDeg;
        }
        double requestedDelta = angleMode == AngleMode.SWIVEL
                ? shortestDeltaDeg(lastRequestedTargetAngleDeg, requestedTarget)
                : (requestedTarget - lastRequestedTargetAngleDeg);
        if (Math.abs(requestedDelta) > 0.05D) {
            lastRequestedTargetAngleDeg = requestedTarget;

            if (!directIncrementActive && !redstoneSwivelIncrementActive && !computerDirectDriveActive && !orientationPayloadDriveActive) {
                lastTargetChangeTick = level.getGameTime();
            }
        }

        if (shouldDirectFollowServoInput(servoInputAngle)) {
            if (angleMode == AngleMode.SWIVEL && !holdSwivelNeutral) {

                lastAimAngleDeg = clampTargetAngle(requestedTarget);
                lastAimAngleInitialized = true;
            }
            previousRenderedAngleDeg = previousTarget;
            renderedAngleDeg = requestedTarget;
            setBaseTargetAngles(previousTarget, requestedTarget);
            return;
        }

        // -----------------------------------------------------TARGET STEP-----------------------------------------------------
        double nextTarget = previousTarget;
        if (directIncrementActive) {

            nextTarget = clampTargetAngle(previousTarget + directIncrement);
        } else if (redstoneSwivelIncrementActive) {
            nextTarget = clampTargetAngle(previousTarget + redstoneSwivelIncrement);
        } else if (computerDirectDriveActive) {

            nextTarget = clampTargetAngle(requestedTarget);
        } else if (orientationPayloadDriveActive) {

            nextTarget = moveTowardTarget(previousTarget, requestedTarget);
        } else {
            int debounceTicks = Math.max(0, CTConfigs.COMMON.bearingDebounceTicks.get());
            if (level.getGameTime() - lastTargetChangeTick >= debounceTicks) {
            nextTarget = moveTowardTarget(previousTarget, requestedTarget);
            }
        }

        if (angleMode == AngleMode.SWIVEL && !holdSwivelNeutral) {

            lastAimAngleDeg = clampTargetAngle(directIncrementActive ? (previousTarget + directIncrement) : requestedTarget);
            lastAimAngleInitialized = true;
        }

        // -----------------------------------------------------ANGLE COMMIT-----------------------------------------------------
        previousRenderedAngleDeg = previousTarget;
        renderedAngleDeg = nextTarget;
        setBaseTargetAngles(previousTarget, nextTarget);
    }

    // Check if this should hold swivel at neutral input
    private boolean shouldHoldSwivelAtNeutralInput(double servoInputAngle, boolean directIncrementActive) {
        if (angleMode != AngleMode.SWIVEL || directIncrementActive) {
            return false;
        }
        if (controlMode == ControlMode.COMPUTER || controlMode == ControlMode.SERVO) {
            return false;
        }
        if (computerOverrideActive) {
            return false;
        }

        return isNetManualInputNeutral();
    }

    // Check if this is hard swivel neutral hold state
    private boolean isHardSwivelNeutralHoldState() {
        if (angleMode != AngleMode.SWIVEL) {
            return false;
        }
        if (controlMode == ControlMode.COMPUTER || controlMode == ControlMode.SERVO) {
            return false;
        }
        if (computerOverrideActive) {
            return false;
        }
        return isNetManualInputNeutral();
    }

    // Check if this is net manual input neutral
    private boolean isNetManualInputNeutral() {
        if (forwardSignal != backwardSignal) {
            return false;
        }
        return Math.abs(getDirectIncrementDeg()) <= 1.0E-4D;
    }

    // Get the live physical angle degrees
    private double getLivePhysicalAngleDegrees(double fallbackAngleDeg) {
        if (level == null || !isAssembled()) {
            return clampTargetAngle(fallbackAngleDeg);
        }

        BlockPos platePos = getPlatePos();
        if (platePos == null) {
            return clampTargetAngle(fallbackAngleDeg);
        }

        Object attached = getAttachedSubLevelHandle();
        if (attached == null) {
            return clampTargetAngle(fallbackAngleDeg);
        }

        BlockState attachedState = level.getBlockState(platePos);
        if (!attachedState.hasProperty(BlockStateProperties.FACING)) {
            return clampTargetAngle(fallbackAngleDeg);
        }

        Quaterniond orientationB = extractSubLevelOrientation(attached);
        if (orientationB == null) {
            return clampTargetAngle(fallbackAngleDeg);
        }

        Quaterniond orientationA = new Quaterniond();
        Quaterniond blockOrientationA = new Quaterniond((Quaternionfc) getFacing().getRotation());
        Quaterniond blockOrientationB = new Quaterniond((Quaternionfc) ((Direction) attachedState.getValue(BlockStateProperties.FACING)).getRotation());
        SubLevel containing = SableLevelApi.containing(this);
        Quaterniond containingOrientation = extractSubLevelOrientation(containing);
        if (containingOrientation != null) {
            orientationA.set((Quaterniondc) containingOrientation);
        }

        Quaterniond localB = new Quaterniond((Quaterniondc) orientationA)
                .mul((Quaterniondc) blockOrientationA)
                .conjugate()
                .mul((Quaterniondc) new Quaterniond((Quaterniondc) orientationB).mul((Quaterniondc) blockOrientationB));
        double dot = new Vec3(0.0D, 1.0D, 0.0D).dot(new Vec3(localB.x(), localB.y(), localB.z()));
        double currentAngle = -2.0D * Math.toDegrees(Math.atan2(-dot, localB.w()));
        return clampTargetAngle(currentAngle);
    }

    // Extract the sublevel orientation
    private @org.jetbrains.annotations.Nullable Quaterniond extractSubLevelOrientation(@org.jetbrains.annotations.Nullable Object subLevelHandle) {
        if (!(subLevelHandle instanceof SubLevel subLevel)) {
            return null;
        }
        return new Quaterniond(subLevel.logicalPose().orientation());
    }

    // Move toward the target angle
    private double moveTowardTarget(double currentTarget, double requestedTarget) {
        float availableStep = getAvailableAngularStep();
        if (availableStep <= 0.0f) {
            return currentTarget;
        }

        double angleDiff = angleMode == AngleMode.SWIVEL
                ? shortestDeltaDeg(currentTarget, requestedTarget)
                : (requestedTarget - currentTarget);
        if (Math.abs(angleDiff) <= 0.05D) {
            return clampTargetAngle(requestedTarget);
        }

        double smoothing = Mth.clamp(CTConfigs.COMMON.bearingSmoothing.get().doubleValue(), 0.0D, 1.0D);
        double cappedStep = Math.max(0.05D, smoothing) * availableStep;
        double commandedStep = Mth.clamp(angleDiff, -cappedStep, cappedStep);
        return clampTargetAngle(currentTarget + commandedStep);
    }

    // Get the direct increment deg
    private double getDirectIncrementDeg() {
        double positive = 0.0D;
        double negative = 0.0D;
        for (Map.Entry<String, Float> entry : directControllerSignals.entrySet()) {
            float normalizedValue = normalizeDirectInput(entry.getValue());
            float magnitude = Mth.clamp(Math.abs(normalizedValue), 0.0f, 1.0f);
            if (magnitude <= 0.0f) {
                continue;
            }
            if (isPositiveCtrlInput(entry.getKey(), normalizedValue)) {
                positive = Math.max(positive, magnitude);
            } else {
                negative = Math.max(negative, magnitude);
            }
        }

        double net = positive - negative;
        if (Math.abs(net) <= 1.0E-4D) {
            return 0.0D;
        }

        float availableStep = getAvailableAngularStep();
        if (availableStep <= 0.0f) {
            return 0.0D;
        }

        return net * availableStep * getTargetDirectionMultiplier();
    }

    // Get the available angular step
    private float getAvailableAngularStep() {
        float maxSwivelRpm = SimConfigService.INSTANCE.server().blocks.maxSwivelBearingSpeed.getF();
        float shaftSpeed = Mth.clamp(getExtraKinetics().getSpeed(), -maxSwivelRpm, maxSwivelRpm);
        return Math.abs(SwivelBearingBlockEntity.convertToAngular(shaftSpeed));
    }

    // Get the redstone swivel delta deg
    private double getRedstoneSwivelDeltaDeg() {
        if (angleMode != AngleMode.SWIVEL || controlMode != ControlMode.REDSTONE) {
            return 0.0D;
        }

        int netSignal = forwardSignal - backwardSignal;
        if (netSignal == 0) {
            return 0.0D;
        }

        float availableStep = getAvailableAngularStep();
        if (availableStep <= 0.0f) {
            return 0.0D;
        }

        double magnitude = Mth.clamp(Math.abs(netSignal) / 15.0D, 0.0D, 1.0D);
        return Math.signum(netSignal) * availableStep * magnitude * getTargetDirectionMultiplier();
    }

    // Calculate the requested target angle
    private double computeRequestedTargetAngle(double servoInputAngle) {
        return switch (controlMode) {
            case AUTO -> {
                double publishedServoInputAngle = getPublishedServoAngleDeg();
                if (!Double.isNaN(publishedServoInputAngle)) {
                    yield applyTargetInversion(publishedServoInputAngle);
                }
                if (computerOverrideActive) {
                    yield applyTargetInversion(computerTargetAngleDeg);
                }
                yield computeRedstoneRequestedAngle();
            }
            case REDSTONE -> computeRedstoneRequestedAngle();
            case COMPUTER -> computerOverrideActive
                    ? applyTargetInversion(computerTargetAngleDeg)
                    : getTargetAngleDegrees();
            case SERVO -> !Double.isNaN(servoInputAngle)
                    ? applyTargetInversion(servoInputAngle)
                    : getTargetAngleDegrees();
        };
    }

    // Apply the target inversion
    private double applyTargetInversion(double targetAngleDeg) {
        return clampTargetAngle(inverted ? -targetAngleDeg : targetAngleDeg);
    }

    // Get the target direction multiplier
    private double getTargetDirectionMultiplier() {
        return inverted ? -1.0D : 1.0D;
    }

    // Check if this should follow servo input directly
    private boolean shouldDirectFollowServoInput(double servoInputAngle) {
        return switch (controlMode) {
            case AUTO -> !Double.isNaN(getPublishedServoAngleDeg());
            case SERVO -> !Double.isNaN(servoInputAngle);
            default -> false;
        };
    }

    // Calculate the redstone requested angle
    private double computeRedstoneRequestedAngle() {
        return ThrusterBearingRedstoneRange.computeTarget(getMinAngleDeg(), getMaxAngleDeg(),
                forwardSignal, backwardSignal, inverted);
    }

    // Get the min angle deg
    private double getMinAngleDeg() {
        return angleMode == AngleMode.SWIVEL ? SWIVEL_MIN_ANGLE_DEG : minAngleDeg;
    }

    // Get the max angle deg
    private double getMaxAngleDeg() {
        return angleMode == AngleMode.SWIVEL ? SWIVEL_MAX_ANGLE_DEG : maxAngleDeg;
    }

    // Clamp the target angle
    private double clampTargetAngle(double angleDeg) {
        if (angleMode == AngleMode.SWIVEL) {
            return wrapDegrees0To360(angleDeg);
        }
        return Mth.clamp(angleDeg, minAngleDeg, maxAngleDeg);
    }

    // Wrap the angle to 0–360 degrees
    private static double wrapDegrees0To360(double angle) {
        angle %= 360.0D;
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        return angle;
    }

    // Get the shortest delta deg
    private static double shortestDeltaDeg(double fromAngle, double toAngle) {
        double fromWrapped = wrapDegrees0To360(fromAngle);
        double toWrapped = wrapDegrees0To360(toAngle);
        double delta = toWrapped - fromWrapped;
        if (delta > 180.0D) {
            delta -= 360.0D;
        } else if (delta < -180.0D) {
            delta += 360.0D;
        }
        return delta;
    }

    // Get the lerp wrapped degrees
    private static double lerpWrappedDegrees(double partialTicks, double fromAngle, double toAngle) {
        return wrapDegrees0To360(fromAngle + shortestDeltaDeg(fromAngle, toAngle) * partialTicks);
    }

    // Get the servo input angle degrees
    public double getServoInputAngleDegrees() {
        KineticBlockEntity inputCog = getExtraKinetics();
        if (inputCog == null) {
            return trackedServoInputInitialized ? trackedServoInputAngleDeg : Double.NaN;
        }

        double heldAngle = getHeldServoAngleDeg();
        if (!Double.isNaN(heldAngle)) {
            return acceptServoInputAngle(inputCog, heldAngle);
        }

        double torsionSpringAngle = SimulatedPreciseAngleCompat.getTorsionSpringOutputAngleDegrees(inputCog);
        if (!Double.isNaN(torsionSpringAngle)) {
            return acceptServoInputAngle(inputCog, torsionSpringAngle);
        }

        if (hasRecentGearboxServoInput()) {
            return acceptServoInputAngle(inputCog, gearboxServoInputAngleDeg);
        }

        if (hasLiveServoCogInput(inputCog)) {
            double liveCogAngle = getLiveServoAngleDeg(inputCog);
            if (!Double.isNaN(liveCogAngle)) {
                return acceptServoInputAngle(inputCog, liveCogAngle);
            }
        }

        return updateTrackedServoInputAngle(inputCog);
    }

    // Get the payload angle deg
    private double getPayloadAngleDeg(OrientationPayload payload) {
        return getPayloadAngleDeg(payload, null);
    }

    // Get the payload angle deg
    private double getPayloadAngleDeg(OrientationPayload payload, @org.jetbrains.annotations.Nullable Direction sourceSide) {
        if (payload == null) {
            return Double.NaN;
        }
        double angleRadians = getPayloadAngleRad(payload, sourceSide);
        if (Math.abs(angleRadians) < 1.0E-7D) {
            angleRadians = 0.0D;
        }
        return clampTargetAngle(Math.toDegrees(angleRadians));
    }

    // Get the payload angle rad
    private double getPayloadAngleRad(OrientationPayload payload, @org.jetbrains.annotations.Nullable Direction sourceSide) {
        if (sourceSide != null && sourceSide.getAxis().isHorizontal()) {

            return switch (sourceSide) {
                case NORTH -> -payload.getXAngleRadians();
                case SOUTH -> payload.getXAngleRadians();
                case WEST -> -payload.getZAngleRadians();
                case EAST -> payload.getZAngleRadians();
                default -> getDominantPayloadAngleRad(payload);
            };
        }
        if (sourceSide != null) {
            double dominant = getDominantPayloadAngleRad(payload);
            if (Math.abs(dominant) > 1.0E-7D) {
                return dominant;
            }
        }

        Direction facing = getFacing();
        if (facing.getAxis() == Direction.Axis.X) {
            return facing == Direction.EAST
                    ? payload.getZAngleRadians()
                    : -payload.getZAngleRadians();
        } else if (facing.getAxis() == Direction.Axis.Z) {
            return facing == Direction.SOUTH
                    ? payload.getXAngleRadians()
                    : -payload.getXAngleRadians();
        }
        return payload.getXAngleRadians();
    }

    // Get the dominant payload angle rad
    private static double getDominantPayloadAngleRad(OrientationPayload payload) {
        return Math.abs(payload.getZAngleRadians()) > Math.abs(payload.getXAngleRadians())
                ? payload.getZAngleRadians()
                : payload.getXAngleRadians();
    }

    // Accept the servo input angle
    private double acceptServoInputAngle(KineticBlockEntity inputCog, double angleDegrees) {

        trackedServoInputInitialized = true;
        trackedServoInputAngleDeg = clampTargetAngle(angleDegrees);
        lastServoInputSampleTick = level != null ? level.getGameTime() : lastServoInputSampleTick;
        lastServoInputSourcePos = inputCog.hasSource() && inputCog.source != null
                ? inputCog.source.immutable()
                : inputCog.getBlockPos().immutable();
        lastServoInputNetworkId = inputCog.network;
        return trackedServoInputAngleDeg;
    }

    // Get the published servo angle deg
    private double getPublishedServoAngleDeg() {
        double heldAngle = getHeldServoAngleDeg();
        if (!Double.isNaN(heldAngle)) {
            return heldAngle;
        }
        return hasRecentGearboxServoInput() ? gearboxServoInputAngleDeg : Double.NaN;
    }

    // Check if this has recent gearbox servo input
    private boolean hasRecentGearboxServoInput() {
        return level != null
                && !level.isClientSide
                && lastGearboxServoInputTick != Long.MIN_VALUE
                && level.getGameTime() - lastGearboxServoInputTick <= 2;
    }

    // Get the held servo angle deg
    private double getHeldServoAngleDeg() {
        KineticBlockEntity inputCog = getExtraKinetics();
        double heldAngle = getHeldServoAngleDeg(inputCog);
        if (!Double.isNaN(heldAngle)) {
            return heldAngle;
        }
        return getHeldServoAngleDeg(this);
    }

    // Get the held servo angle deg
    private double getHeldServoAngleDeg(KineticBlockEntity inputCog) {
        if (inputCog == null) {
            return Double.NaN;
        }
        Direction.Axis axis = getServoInputAxis(inputCog);
        double heldAngle = KineticAngleHelper.getHeldRotationAngleDegrees(inputCog, axis);
        if (!Double.isNaN(heldAngle)) {
            return clampTargetAngle(heldAngle);
        }
        for (Direction.Axis fallbackAxis : Direction.Axis.values()) {
            if (fallbackAxis == axis) {
                continue;
            }
            heldAngle = KineticAngleHelper.getHeldRotationAngleDegrees(inputCog, fallbackAxis);
            if (!Double.isNaN(heldAngle)) {
                return clampTargetAngle(heldAngle);
            }
        }
        return Double.NaN;
    }

    // Get the live servo angle deg
    private double getLiveServoAngleDeg(KineticBlockEntity inputCog) {
        Direction.Axis axis = getServoInputAxis(inputCog);
        return clampTargetAngle(KineticAngleHelper.getAbsoluteRotationAngleDegrees(inputCog, axis));
    }

    // Check if this has live servo cog input
    private boolean hasLiveServoCogInput(KineticBlockEntity inputCog) {
        return inputCog.hasSource() || inputCog.network != null || Math.abs(inputCog.getSpeed()) > 1.0E-4f;
    }

    // Get the servo input axis
    private Direction.Axis getServoInputAxis(KineticBlockEntity inputCog) {
        Direction.Axis axis = getFacing().getAxis();
        BlockState inputState = inputCog.getBlockState();
        if (inputState.getBlock() instanceof IRotate rotate) {
            axis = rotate.getRotationAxis(inputState);
        }
        return axis;
    }

    // Check if this is recent orientation payload drive
    private boolean isRecentOrientationPayloadDrive() {
        return level != null
                && !level.isClientSide
                && controlMode == ControlMode.COMPUTER
                && computerOverrideActive
                && level.getGameTime() - lastOrientationPayloadTick <= 2;
    }

    // Update the tracked servo input angle
    private double updateTrackedServoInputAngle(KineticBlockEntity inputCog) {
        if (level == null) {
            return trackedServoInputInitialized ? trackedServoInputAngleDeg : Double.NaN;
        }

        BlockPos sourcePos = inputCog.hasSource() && inputCog.source != null
                ? inputCog.source.immutable()
                : inputCog.getBlockPos().immutable();
        Long networkId = inputCog.network;
        long gameTime = level.getGameTime();

        if (!trackedServoInputInitialized
                || !sourcePos.equals(lastServoInputSourcePos)
                || !java.util.Objects.equals(networkId, lastServoInputNetworkId)) {
            trackedServoInputInitialized = true;
            trackedServoInputAngleDeg = KineticAngleHelper.normalizeDegrees(
                    applyTargetInversion(getTargetAngleDegrees()));
            lastServoInputSampleTick = gameTime;
            lastServoInputSourcePos = sourcePos;
            lastServoInputNetworkId = networkId;
            return trackedServoInputAngleDeg;
        }

        long deltaTicks = Math.max(0L, gameTime - lastServoInputSampleTick);
        if (deltaTicks > 0L) {
            double deltaAngle = SwivelBearingBlockEntity.convertToAngular(inputCog.getSpeed()) * deltaTicks;
            if (Math.abs(deltaAngle) > 1.0E-6D) {
                trackedServoInputAngleDeg = KineticAngleHelper.normalizeDegrees(trackedServoInputAngleDeg + deltaAngle);
            }
            lastServoInputSampleTick = gameTime;
        }

        return trackedServoInputAngleDeg;
    }

    // Seed the tracked servo input angle
    private void seedTrackedServoInputAngle(KineticBlockEntity inputCog, double angleDegrees) {
        trackedServoInputInitialized = true;
        trackedServoInputAngleDeg = KineticAngleHelper.normalizeDegrees(angleDegrees);
        lastServoInputSampleTick = level != null ? level.getGameTime() : lastServoInputSampleTick;
        lastServoInputSourcePos = inputCog.hasSource() && inputCog.source != null
                ? inputCog.source.immutable()
                : inputCog.getBlockPos().immutable();
        lastServoInputNetworkId = inputCog.network;
    }

    // Force the servo lock
    private void forceServoLock() {
        if (!isAssembled()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.getValue(BlockStateProperties.POWERED)) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BlockStateProperties.POWERED, true));
        }
    }

    // Set the locking option always locked
    private void setLockingOptionAlwaysLocked() {
        try {
            ScrollOptionBehaviour<LockingSetting> option = getLockingOptionBehaviour();
            if (option != null) {
                option.setValue(LockingSetting.LOCKED_ALWAYS.ordinal());
            }
        } catch (IllegalAccessException err) {
            throw new IllegalStateException("Failed to lock thruster bearing servo mode", err);
        }
    }

    // Remove the locking option behaviour
    private void removeLockingOptionBehaviour(List<BlockEntityBehaviour> behaviours) {
        try {
            ScrollOptionBehaviour<LockingSetting> option = getLockingOptionBehaviour();
            if (option != null) {
                behaviours.remove(option);
            }
        } catch (IllegalAccessException err) {
            throw new IllegalStateException("Failed to remove swivel-bearing lock option behaviour", err);
        }
    }

    // Get the locking option behaviour
    @SuppressWarnings("unchecked")
    private ScrollOptionBehaviour<LockingSetting> getLockingOptionBehaviour() throws IllegalAccessException {
        return (ScrollOptionBehaviour<LockingSetting>) LOCK_OPTION_FIELD.get(this);
    }

    // Set the base target angles
    private void setBaseTargetAngles(double previousTarget, double nextTarget) {
        try {
            LAST_TARGET_ANGLE_FIELD.setDouble(this, previousTarget);
            TARGET_ANGLE_FIELD.setDouble(this, nextTarget);
        } catch (IllegalAccessException err) {
            throw new IllegalStateException("Failed to update swivel-bearing target angle", err);
        }
    }

    // Get the declared field
    private static Field getDeclaredField(String name) {
        try {
            Field field = SwivelBearingBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException err) {
            throw new IllegalStateException("Missing SwivelBearingBlockEntity field: " + name, err);
        }
    }

    // Handle the angle range changed event
    private void onAngleRangeChanged() {
        computerTargetAngleDeg = clampTargetAngle(computerTargetAngleDeg);
        double adjustedRequested = shouldDirectFollowServoInput(getServoInputAngleDegrees())
            ? computeRequestedTargetAngle(getServoInputAngleDegrees())
            : clampTargetAngle(lastRequestedTargetAngleDeg);
        double adjustedCurrent = shouldDirectFollowServoInput(getServoInputAngleDegrees())
            ? computeRequestedTargetAngle(getServoInputAngleDegrees())
            : clampTargetAngle(getTargetAngleDegrees());
        lastRequestedTargetAngleDeg = adjustedRequested;
        previousRenderedAngleDeg = adjustedCurrent;
        renderedAngleDeg = adjustedCurrent;
        setBaseTargetAngles(adjustedCurrent, adjustedCurrent);
        if (level != null && !level.isClientSide) {
            lastTargetChangeTick = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    // Clamp the configured angle
    private double clampConfiguredAngle(double angleDeg) {
        double configLimit = CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        return Mth.clamp(angleDeg, -configLimit, configLimit);
    }

    // Sync the bearing client data
    private void syncBearingClientData() {
        double targetAngle = getTargetAngleDegrees();
        if (forwardSignal == lastSyncedForwardSignal
                && backwardSignal == lastSyncedBackwardSignal
                && Math.abs(targetAngle - lastSyncedTargetAngleDeg) < 0.05D
                && Math.abs(minAngleDeg - lastSyncedMinAngleDeg) < 0.05D
                && Math.abs(maxAngleDeg - lastSyncedMaxAngleDeg) < 0.05D
                && computerOverrideActive == lastSyncedComputerOverride
                && inverted == lastSyncedInverted
                && controlMode == lastSyncedControlMode
                && angleMode == lastSyncedAngleMode) {
            return;
        }
        lastSyncedForwardSignal = forwardSignal;
        lastSyncedBackwardSignal = backwardSignal;
        lastSyncedTargetAngleDeg = targetAngle;
        lastSyncedMinAngleDeg = minAngleDeg;
        lastSyncedMaxAngleDeg = maxAngleDeg;
        lastSyncedComputerOverride = computerOverrideActive;
        lastSyncedInverted = inverted;
        lastSyncedControlMode = controlMode;
        lastSyncedAngleMode = angleMode;
        sendData();
    }

    // Get the forward signal
    public int getForwardSignal() {
        return forwardSignal;
    }

    // Get the backward signal
    public int getBackwardSignal() {
        return backwardSignal;
    }

    // Update the signal
    public void updateSignal() {

    }

    // Get the facing
    public Direction getFacing() {
        return getBlockState().getValue(BlockStateProperties.FACING);
    }

    // Set the pivot angle degrees
    public void setPivotAngleDegrees(double angleDeg) {
        computerTargetAngleDeg = clampTargetAngle(angleDeg);
        double effectiveTargetAngleDeg = applyTargetInversion(computerTargetAngleDeg);
        computerOverrideActive = true;
        controlMode = ControlMode.COMPUTER;
        lastAimAngleDeg = effectiveTargetAngleDeg;
        lastAimAngleInitialized = true;
        if (level != null && !level.isClientSide) {
            lastRequestedTargetAngleDeg = effectiveTargetAngleDeg;
            lastTargetChangeTick = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
        sendData();
    }

    // Clear the pivot override
    public void clearPivotOverride() {
        computerOverrideActive = false;
        computerTargetAngleDeg = 0.0D;
        if (controlMode == ControlMode.COMPUTER) {
            controlMode = ControlMode.AUTO;
        }
        lastAimAngleDeg = clampTargetAngle(getTargetAngleDegrees());
        lastAimAngleInitialized = true;
        if (level != null && !level.isClientSide) {
            lastRequestedTargetAngleDeg = computeRequestedTargetAngle(getServoInputAngleDegrees());
            lastTargetChangeTick = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
        sendData();
    }

    // Check if this has pivot override
    public boolean hasPivotOverride() {
        return computerOverrideActive;
    }

    // Get the smoothed pivot angle deg
    public double getSmoothedPivotAngleDeg() {
        return getTargetAngleDegrees();
    }

    // Get the current pivot angle degrees
    public double getCurrentPivotAngleDegrees() {
        return getLivePhysicalAngleDegrees(getTargetAngleDegrees());
    }

    // Get the min angle degrees
    public double getMinAngleDegrees() {
        return minAngleDeg;
    }

    // Get the max angle degrees
    public double getMaxAngleDegrees() {
        return maxAngleDeg;
    }

    // Get the control mode
    public ControlMode getControlMode() {
        return controlMode;
    }

    // Check if this is inverted
    public boolean isInverted() {
        return inverted;
    }

    // Set the inverted
    public void setInverted(boolean inverted) {
        if (this.inverted == inverted) {
            return;
        }
        double previousTarget = getTargetAngleDegrees();
        this.inverted = inverted;
        if (level != null && !level.isClientSide) {
            double servoInputAngle = getServoInputAngleDegrees();
            double nextTarget = usesIncrementalSwivelControl(servoInputAngle)
                    ? clampTargetAngle(-previousTarget)
                    : computeRequestedTargetAngle(servoInputAngle);
            lastRequestedTargetAngleDeg = nextTarget;
            lastAimAngleDeg = nextTarget;
            lastAimAngleInitialized = true;
            previousRenderedAngleDeg = previousTarget;
            renderedAngleDeg = nextTarget;
            setBaseTargetAngles(previousTarget, nextTarget);
            if (shouldUpdateBearingConstraintInParentTick()) {
                updateServoCoefficients();
            }
            lastTargetChangeTick = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
        sendData();
    }

    // Check if this uses incremental swivel control
    private boolean usesIncrementalSwivelControl(double servoInputAngle) {
        if (angleMode != AngleMode.SWIVEL) {
            return false;
        }
        if (controlMode == ControlMode.REDSTONE) {
            return true;
        }
        if (controlMode == ControlMode.COMPUTER
                || shouldDirectFollowServoInput(servoInputAngle)
                || computerOverrideActive) {
            return false;
        }
        return !directControllerSignals.isEmpty();
    }

    // Get the linked direction
    @Override
    public Vec3 getLinkedDirection() {
        double[] angles = getLinkedAnglesRadians();
        if (angles == null) {
            return null;
        }
        return OrientationMath.directionFromAngles(angles[0], angles[1]);
    }

    // Get the linked angles radians
    @Override
    public double[] getLinkedAnglesRadians() {
        double pivotRadians = Math.toRadians(getSmoothedPivotAngleDeg());
        Direction facing = getFacing();

        double xAngle = 0.0D;
        double zAngle = 0.0D;
        if (facing.getAxis() == Direction.Axis.X) {
            zAngle = facing == Direction.EAST ? pivotRadians : -pivotRadians;
        } else if (facing.getAxis() == Direction.Axis.Z) {
            xAngle = facing == Direction.SOUTH ? pivotRadians : -pivotRadians;
        } else {
            xAngle = pivotRadians;
        }

        return new double[]{xAngle, zAngle};
    }

    // Check if the orientation source is active
    @Override
    public boolean isOrientationSourceActive() {
        return !isRemoved();
    }

    // Check if this can accept orientation payload
    @Override
    public boolean canAcceptOrientationPayload(OrientationPayload payload) {
        return payload != null;
    }

    // Apply the orientation payload
    @Override
    public void applyOrientationPayload(OrientationPayload payload) {
        applyOrientationPayload(payload, null);
    }

    // Apply the orientation payload
    public void applyOrientationPayload(OrientationPayload payload, @org.jetbrains.annotations.Nullable Direction sourceSide) {
        if (payload == null || level == null || level.isClientSide) {
            return;
        }
        double payloadAngle = getPayloadAngleDeg(payload, sourceSide);
        if (Double.isNaN(payloadAngle)) {
            return;
        }
        controlMode = ControlMode.COMPUTER;
        computerOverrideActive = true;
        computerTargetAngleDeg = payloadAngle;
        double effectiveTargetAngle = applyTargetInversion(payloadAngle);
        lastRequestedTargetAngleDeg = effectiveTargetAngle;
        lastOrientationPayloadTick = level.getGameTime();
        lastAimAngleDeg = effectiveTargetAngle;
        lastAimAngleInitialized = true;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        setChanged();
        sendData();
    }

    // Apply the gearbox servo angle degrees
    public boolean applyGearboxServoAngleDegrees(double angleDegrees) {
        if (level == null || level.isClientSide) {
            return false;
        }

        double inputAngle = clampTargetAngle(angleDegrees);
        double targetAngle = applyTargetInversion(inputAngle);
        gearboxServoInputAngleDeg = inputAngle;
        lastGearboxServoInputTick = level.getGameTime();
        trackedServoInputInitialized = true;
        trackedServoInputAngleDeg = inputAngle;
        lastServoInputSampleTick = level.getGameTime();
        lastRequestedTargetAngleDeg = targetAngle;

        double previousTarget = getTargetAngleDegrees();
        previousRenderedAngleDeg = previousTarget;
        renderedAngleDeg = targetAngle;
        setBaseTargetAngles(previousTarget, targetAngle);
        if (shouldUpdateBearingConstraintInParentTick()) {
            updateServoCoefficients();
        }
        return true;
    }

    // Accept the smart gearbox servo angle degrees
    @Override
    public boolean acceptSmartGearboxServoAngleDegrees(double angleDegrees, Direction inputSide) {
        return applyGearboxServoAngleDegrees(angleDegrees);
    }

    // Set the control mode
    public void setControlMode(ControlMode controlMode) {
        if (controlMode == null || this.controlMode == controlMode) {
            return;
        }
        this.controlMode = controlMode;
        if (controlMode == ControlMode.COMPUTER && !computerOverrideActive) {
            computerTargetAngleDeg = applyTargetInversion(getTargetAngleDegrees());
            computerOverrideActive = true;
        }
        if (level != null && !level.isClientSide) {
            lastRequestedTargetAngleDeg = computeRequestedTargetAngle(getServoInputAngleDegrees());
            lastTargetChangeTick = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
        sendData();
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

        Direction inputFace = getBlockState().getValue(BlockStateProperties.FACING);
        return side == inputFace || side == inputFace.getOpposite();
    }

    // Set the min angle degrees
    public void setMinAngleDegrees(double angleDeg) {
        minAngleDeg = clampConfiguredAngle(angleDeg);
        if (minAngleDeg > maxAngleDeg) {
            maxAngleDeg = minAngleDeg;
        }
        onAngleRangeChanged();
    }

    // Set the max angle degrees
    public void setMaxAngleDegrees(double angleDeg) {
        maxAngleDeg = clampConfiguredAngle(angleDeg);
        if (maxAngleDeg < minAngleDeg) {
            minAngleDeg = maxAngleDeg;
        }
        onAngleRangeChanged();
    }

    // Set the angle range degrees
    public void setAngleRangeDegrees(double minAngleDeg, double maxAngleDeg) {
        double clampedMin = clampConfiguredAngle(minAngleDeg);
        double clampedMax = clampConfiguredAngle(maxAngleDeg);
        if (clampedMin > clampedMax) {
            double swap = clampedMin;
            clampedMin = clampedMax;
            clampedMax = swap;
        }
        this.minAngleDeg = clampedMin;
        this.maxAngleDeg = clampedMax;
        onAngleRangeChanged();
    }

    // Get the angle mode
    public AngleMode getAngleMode() {
        return angleMode;
    }

    // Set the angle mode
    public void setAngleMode(AngleMode angleMode) {
        AngleMode next = angleMode == null ? AngleMode.RANGE : angleMode;
        if (this.angleMode == next) {
            return;
        }
        this.angleMode = next;
        onAngleRangeChanged();
        setChanged();
        sendData();
    }

    // Get the interpolated angle
    public float getInterpolatedAngle(float partialTicks) {
        if (angleMode == AngleMode.SWIVEL) {
            return (float) lerpWrappedDegrees(partialTicks, previousRenderedAngleDeg, renderedAngleDeg);
        }
        return (float) AngleHelper.angleLerp(partialTicks, previousRenderedAngleDeg, renderedAngleDeg);
    }

    // Write the thruster bearing safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putString("ControlMode", controlMode.name());
        tag.putString("AngleMode", angleMode.name());
        tag.putBoolean("Inverted", inverted);
        tag.putDouble("MinAngleDeg", minAngleDeg);
        tag.putDouble("MaxAngleDeg", maxAngleDeg);
        CompoundTag thrusterAliasesTag = new CompoundTag();
        for (Map.Entry<String, String> entry : thrusterAliases.entrySet()) {
            thrusterAliasesTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("ThrusterAliases", thrusterAliasesTag);
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
    }

    // Write the thruster bearing
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("ForwardSignal", forwardSignal);
        tag.putInt("BackwardSignal", backwardSignal);
        tag.putString("ControlMode", controlMode.name());
        tag.putString("AngleMode", angleMode.name());
        tag.putBoolean("Inverted", inverted);
        tag.putBoolean("ComputerOverrideActive", computerOverrideActive);
        tag.putDouble("ComputerTargetAngleDeg", computerTargetAngleDeg);
        tag.putBoolean("TrackedServoInputInitialized", trackedServoInputInitialized);
        tag.putDouble("TrackedServoInputAngleDeg", trackedServoInputAngleDeg);
        tag.putDouble("MinAngleDeg", minAngleDeg);
        tag.putDouble("MaxAngleDeg", maxAngleDeg);
        tag.putDouble("LastRequestedTargetAngleDeg", lastRequestedTargetAngleDeg);
        tag.putBoolean("LastAimAngleInitialized", lastAimAngleInitialized);
        tag.putDouble("LastAimAngleDeg", lastAimAngleDeg);
        CompoundTag thrusterAliasesTag = new CompoundTag();
        for (Map.Entry<String, String> entry : thrusterAliases.entrySet()) {
            thrusterAliasesTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("ThrusterAliases", thrusterAliasesTag);
        if (customName != null) {
            tag.putString("CustomName", customName);
        }
    }

    // Read the thruster bearing
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(remapPlacedPlateTag(tag), provider, clientPacket);

        setLockingOptionAlwaysLocked();
        forwardSignal = tag.getInt("ForwardSignal");
        backwardSignal = tag.getInt("BackwardSignal");
        computerOverrideActive = tag.getBoolean("ComputerOverrideActive");
        controlMode = tag.contains("ControlMode")
            ? readEnum(tag, "ControlMode", ControlMode.AUTO)
            : (computerOverrideActive ? ControlMode.COMPUTER : ControlMode.AUTO);
        angleMode = tag.contains("AngleMode")
            ? readEnum(tag, "AngleMode", AngleMode.RANGE)
            : AngleMode.RANGE;
        inverted = tag.getBoolean("Inverted");
        computerTargetAngleDeg = tag.getDouble("ComputerTargetAngleDeg");
        trackedServoInputInitialized = tag.getBoolean("TrackedServoInputInitialized");
        trackedServoInputAngleDeg = tag.getDouble("TrackedServoInputAngleDeg");
        lastServoInputSampleTick = Long.MIN_VALUE;
        lastServoInputSourcePos = null;
        lastServoInputNetworkId = null;
        minAngleDeg = tag.contains("MinAngleDeg") ? tag.getDouble("MinAngleDeg") : -CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        maxAngleDeg = tag.contains("MaxAngleDeg") ? tag.getDouble("MaxAngleDeg") : CTConfigs.COMMON.bearingMaxPivotAngleDeg.get();
        lastRequestedTargetAngleDeg = tag.getDouble("LastRequestedTargetAngleDeg");
        lastAimAngleInitialized = tag.getBoolean("LastAimAngleInitialized");
        lastAimAngleDeg = tag.getDouble("LastAimAngleDeg");
        thrusterAliases.clear();
        if (tag.contains("ThrusterAliases")) {
            CompoundTag thrusterAliasesTag = tag.getCompound("ThrusterAliases");
            for (String key : thrusterAliasesTag.getAllKeys()) {
                thrusterAliases.put(key, thrusterAliasesTag.getString(key));
            }
        }
        customName = tag.contains("CustomName") ? tag.getString("CustomName") : null;
        minAngleDeg = clampConfiguredAngle(minAngleDeg);
        maxAngleDeg = clampConfiguredAngle(maxAngleDeg);
        if (minAngleDeg > maxAngleDeg) {
            double midpoint = (minAngleDeg + maxAngleDeg) * 0.5D;
            minAngleDeg = midpoint;
            maxAngleDeg = midpoint;
        }
        computerTargetAngleDeg = clampTargetAngle(computerTargetAngleDeg);
        if (angleMode == AngleMode.SWIVEL) {
            lastRequestedTargetAngleDeg = wrapDegrees0To360(lastRequestedTargetAngleDeg);
            lastAimAngleDeg = wrapDegrees0To360(lastAimAngleDeg);
        }
        previousRenderedAngleDeg = renderedAngleDeg;
        renderedAngleDeg = clampTargetAngle(getTargetAngleDegrees());
    }

    // Remap the placed plate tag
    private static CompoundTag remapPlacedPlateTag(CompoundTag tag) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null
                || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE
                || !tag.hasUUID("SubLevelID")
                || !tag.contains("SwivelPlate")) {
            return tag;
        }
        SubLevelSchematicSerializationContext.SchematicMapping mapping =
                ctx.getMapping(tag.getUUID("SubLevelID"));
        if (mapping == null) {
            return tag;
        }
        BlockPos platePos = NbtUtils.readBlockPos(tag, "SwivelPlate").orElse(null);
        if (platePos == null) {
            return tag;
        }
        CompoundTag placedTag = tag.copy();
        placedTag.putUUID("SubLevelID", mapping.newUUID());
        placedTag.put("SwivelPlate", NbtUtils.writeBlockPos(mapping.transform().apply(platePos)));
        return placedTag;
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

    // Get the custom name
    @Override
    public @org.jetbrains.annotations.Nullable String getCustomName() {
        return customName;
    }

    // Set the custom name
    @Override
    public void setCustomName(@org.jetbrains.annotations.Nullable String name) {
        this.customName = (name != null && !name.isBlank()) ? name.strip() : null;
        setChanged();
        sendData();
    }

    // Get the attached thruster
    public ThrusterBlockEntity getAttachedThruster() {
        if (level == null) {
            return null;
        }

        BlockEntity side = level.getBlockEntity(worldPosition.relative(getFacing()));
        return side instanceof ThrusterBlockEntity thruster ? thruster : null;
    }

    // Get the attached thruster direction
    public Direction getAttachedThrusterDirection() {
        return getAttachedThruster() != null ? getFacing() : null;
    }

    // Get the attached thrusters by id
    public Map<String, ThrusterBlockEntity> getAttachedThrustersById() {
        if (level != null && !level.isClientSide) {
            refreshThrusterBindings();
        }

        Map<BlockPos, ThrusterBlockEntity> currentThrusters = attachedThrustersByPos();
        Map<String, ThrusterBlockEntity> thrusters = new LinkedHashMap<>();

        for (ThrusterBlockEntity thruster : currentThrusters.values()) {
            String thrusterId = getThrusterId(thruster);
            if (!thrusterId.isEmpty()) {
                thrusters.put(thrusterId, thruster);
            }
        }

        if (thrusters.isEmpty()) {
            ThrusterBlockEntity attachedThruster = getAttachedThruster();
            if (attachedThruster != null) {
                String thrusterId = getThrusterId(attachedThruster);
                if (!thrusterId.isEmpty()) {
                    thrusters.put(thrusterId, attachedThruster);
                }
            }
        }

        return thrusters;
    }

    // Get the thruster id
    public String getThrusterId(ThrusterBlockEntity thruster) {
        return UUID.nameUUIDFromBytes(("thruster:" + thruster.getBlockPos().asLong())
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Get the thruster alias
    public String getThrusterAlias(ThrusterBlockEntity thruster) {
        return thrusterAliases.getOrDefault(getThrusterId(thruster), "");
    }

    // Set the thruster alias
    public void setThrusterAlias(String thrusterId, String alias) {
        String normalizedId = thrusterId == null ? "" : thrusterId.trim();
        String normalizedAlias = alias == null ? "" : alias.trim();
        if (normalizedAlias.isEmpty()) {
            if (thrusterAliases.remove(normalizedId) != null) {
                setChanged();
                if (level != null && !level.isClientSide) {
                    refreshThrusterBindings();
                    sendData();
                }
            }
            return;
        }

        thrusterAliases.put(normalizedId, normalizedAlias);
        setChanged();
        if (level != null && !level.isClientSide) {
            refreshThrusterBindings();
            sendData();
        }
    }

    // Get the assembly real thrust
    public double getAssemblyRealThrust() {
        return CTPropulsionTelemetry.getAssemblyRealThrust(this);
    }

    // Get the assembly lift capacity
    public double getAssemblyLiftCapacity() {
        return CTPropulsionTelemetry.getAssemblyLiftCapacity(this);
    }

    // Get the attached thruster real thrusts
    public Map<String, Double> getAttachedThrusterRealThrusts() {
        return CTPropulsionTelemetry.getAttachedThrusterRealThrusts(this);
    }

    // Get the attached thruster lift capacities
    public Map<String, Double> getAttachedThrusterLiftCapacities() {
        return CTPropulsionTelemetry.getAttachedThrusterLiftCapacities(this);
    }

    // Get the attached thrusters by pos
    private Map<BlockPos, ThrusterBlockEntity> attachedThrustersByPos() {
        Map<BlockPos, ThrusterBlockEntity> currentThrusters = new LinkedHashMap<>();
        Set<ThrusterBlockEntity> seenThrusters = Collections.newSetFromMap(new IdentityHashMap<>());
        Object attachedSubLevel = getAttachedSubLevelHandle();

        if (attachedSubLevel != null) {
            for (BlockEntity blockEntity : getAttachedBlockEntities(attachedSubLevel)) {
                if (blockEntity instanceof ThrusterBlockEntity thruster && seenThrusters.add(thruster)) {
                    currentThrusters.put(thruster.getBlockPos().immutable(), thruster);
                }
            }
        }

        if (currentThrusters.isEmpty()) {
            ThrusterBlockEntity attachedThruster = getAttachedThruster();
            if (attachedThruster != null) {
                currentThrusters.put(attachedThruster.getBlockPos().immutable(), attachedThruster);
            }
        }

        return currentThrusters;
    }

    // Get the next thruster alias
    private String nextThrusterAlias() {
        Set<String> usedAliases = new java.util.HashSet<>(thrusterAliases.values());
        int idx = 0;
        while (usedAliases.contains("thruster_" + idx)) {
            idx++;
        }
        return "thruster_" + idx;
    }

    // Refresh the thruster bindings
    private boolean refreshThrusterBindings() {
        boolean changed = false;
        Set<String> currentThrusterIds = new java.util.HashSet<>();
        for (Map.Entry<BlockPos, ThrusterBlockEntity> entry : attachedThrustersByPos().entrySet()) {
            ThrusterBlockEntity thruster = entry.getValue();
            String thrusterId = getThrusterId(thruster);
            currentThrusterIds.add(thrusterId);
            String alias = thrusterAliases.get(thrusterId);
            if (alias == null || alias.isBlank()) {
                alias = nextThrusterAlias();
                thrusterAliases.put(thrusterId, alias);
                changed = true;
            }
            String previousId = thruster.getCcId();
            String previousAlias = thruster.getAssemblyComputerCraftAlias();
            thruster.setCcId(thrusterId);
            thruster.setAssemblyComputerCraftAlias(alias);
            if (!thrusterId.equals(previousId)) {
                changed = true;
            }
            if (!alias.equals(previousAlias)) {
                changed = true;
            }
        }

        if (thrusterAliases.keySet().removeIf(id -> !currentThrusterIds.contains(id))) {
            changed = true;
        }
        return changed;
    }

    // Get the attached sublevel handle
    private Object getAttachedSubLevelHandle() {
        if (level == null || !isAssembled()) {
            return null;
        }

        return SubLevelBlockEntityCollector.getSubLevel(level, getSubLevelID());
    }

    // Collect the attached fuel handlers
    private List<IFluidHandler> collectAttachedFuelHandlers(FluidStack requestedFluid) {
        if (level == null) {
            return List.of();
        }

        List<IFluidHandler> targets = new ArrayList<>();
        Set<IFluidHandler> seenHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Object attachedSubLevel = getAttachedSubLevelHandle();

        if (attachedSubLevel != null) {
            for (BlockEntity blockEntity : getAttachedBlockEntities(attachedSubLevel)) {
                addAttachedFuelHandler(targets, seenHandlers, blockEntity, requestedFluid);
            }
        }

        if (targets.isEmpty()) {
            addAttachedFuelHandler(targets, seenHandlers, getAttachedThruster(), requestedFluid);
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
        Object attachedSubLevel = getAttachedSubLevelHandle();

        if (attachedSubLevel != null) {
            for (BlockEntity blockEntity : getAttachedBlockEntities(attachedSubLevel)) {
                addAttachedEnergyHandler(targets, seenHandlers, blockEntity);
            }
        }

        if (targets.isEmpty()) {
            addAttachedEnergyHandler(targets, seenHandlers, getAttachedThruster());
        }

        return targets;
    }

    // Get the attached block entities
    private List<BlockEntity> getAttachedBlockEntities(Object attachedSubLevel) {
        return SubLevelBlockEntityCollector.getBlockEntities(attachedSubLevel);
    }

    // Add the attached fuel handler
    private void addAttachedFuelHandler(List<IFluidHandler> targets, Set<IFluidHandler> seenHandlers,
            BlockEntity blockEntity, FluidStack requestedFluid) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity == this
            || blockEntity instanceof ThrusterBearingBlockEntity) {
            return;
        }

        IFluidHandler handler;
        if (blockEntity instanceof ThrusterBlockEntity thruster) {
            handler = thruster.getFuelTank();
        } else {
            handler = findFluidHandler(blockEntity);
        }

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
            || blockEntity instanceof ThrusterBearingBlockEntity) {
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

    // Collect the assembly fuel storers
    private List<BlockEntity> collectAssemblyFuelStorers() {
        List<BlockEntity> storers = new ArrayList<>();
        Object attachedSubLevel = getAttachedSubLevelHandle();
        if (attachedSubLevel == null) {
            return storers;
        }

        for (BlockEntity blockEntity : getAttachedBlockEntities(attachedSubLevel)) {
                if (blockEntity == null || blockEntity.isRemoved() || blockEntity == this
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

    // Redistribute the assembly fuel
    private void redistributeAssemblyFuel() {
        if (level == null || level.isClientSide || level.getGameTime() % 5L != 0L) {
            return;
        }

        List<BlockEntity> storers = collectAssemblyFuelStorers();
        if (storers.isEmpty()) {
            return;
        }

        for (ThrusterBlockEntity thruster : getAttachedThrustersById().values()) {
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

                FluidStack drainedPreview = storage.drain(storedFuel.copyWithAmount(Math.min(remaining, storedFuel.getAmount())),
                        IFluidHandler.FluidAction.SIMULATE);
                if (drainedPreview.isEmpty()) {
                    continue;
                }

                int fillable = consumer.fill(drainedPreview.copy(), IFluidHandler.FluidAction.SIMULATE);
                if (fillable <= 0) {
                    continue;
                }

                FluidStack transferred = storage.drain(drainedPreview.copyWithAmount(fillable), IFluidHandler.FluidAction.EXECUTE);
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

    // Add the tooltip
    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        addBaseKineticTooltip(tooltip, isPlayerSneaking);

        if (!isPlayerSneaking && getExtraKinetics().getSpeed() != 0.0f) {
            if (isAssembled()) {
                if (isThrusterTooFastForTooltip()) {
                    CreateLang.translate("generic.too_fast").style(ChatFormatting.GOLD).forGoggles(tooltip);
                    MutableComponent component = Component.translatable("createthrusters.thruster_bearing.too_fast_error");
                    tooltip.addAll(TooltipHelper.cutTextComponent(component, FontHelper.Palette.GRAY_AND_WHITE));
                }
            } else if (level != null) {
                BlockState attachedState = level.getBlockState(worldPosition.relative(getFacing()));
                if (!attachedState.canBeReplaced()) {
                    TooltipHelper.addHint(tooltip, "hint.empty_bearing");
                }
            }
        }

        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.thruster_bearing")));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.aim_angle"),
                CTTooltipHelper.value(CTTooltipHelper.degrees(getTargetAngleDegrees()), ChatFormatting.GREEN)));
        tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.control_mode"),
                CTTooltipHelper.value(switch (controlMode) {
                    case AUTO -> "Auto";
                    case REDSTONE -> "Redstone";
                    case COMPUTER -> "Computer";
                    case SERVO -> "Servo";
                }, ChatFormatting.GOLD)));
        if (!getAttachedThrustersById().isEmpty()) {
            MutableComponent thrustComponent = CTPropulsionTelemetry.thrustComponent(getAssemblyRealThrust()).withStyle(ChatFormatting.AQUA);
            tooltip.add(dev.eriksonn.aeronautics.data.AeroLang.translate("propeller.thrust", thrustComponent)
                .style(ChatFormatting.GRAY)
                .component());
            MutableComponent liftComponent = CTPropulsionTelemetry.liftComponent(getAssemblyLiftCapacity()).withStyle(ChatFormatting.AQUA);
            tooltip.add(dev.eriksonn.aeronautics.data.AeroLang.translate("propeller.can_lift", liftComponent)
                .style(ChatFormatting.GRAY)
                .component());
        }
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.steering_arc"),
                    CTTooltipHelper.value(CTTooltipHelper.degrees(minAngleDeg) + " to "
                                    + CTTooltipHelper.degrees(maxAngleDeg),
                            ChatFormatting.GRAY)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.angle_mode"),
                    CTTooltipHelper.value(switch (angleMode) {
                        case RANGE -> "Range";
                        case SWIVEL -> "Swivel";
                    }, ChatFormatting.DARK_AQUA)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.inverted"),
                    CTTooltipHelper.value(inverted ? "Yes" : "No", inverted ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
            tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.fwd_rev_signal"),
                    CTTooltipHelper.value(forwardSignal + " / " + backwardSignal, ChatFormatting.AQUA)));
            double servoInputAngle = getServoInputAngleDegrees();
            if (!Double.isNaN(servoInputAngle)) {
                tooltip.add(CTTooltipHelper.line(Component.translatable("createthrusters.goggle.bearing.servo_request"),
                        CTTooltipHelper.value(CTTooltipHelper.degrees(servoInputAngle), ChatFormatting.DARK_AQUA)));
            }
        }
        return true;
    }

    // Add the base kinetic tooltip
    private boolean addBaseKineticTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean notFastEnough = !isSpeedRequirementFulfilled() && getSpeed() != 0.0f;
        if (overStressed && AllConfigs.client().enableOverstressedTooltip.get()) {
            CreateLang.translate("gui.stressometer.overstressed").style(ChatFormatting.GOLD).forGoggles(tooltip);
            MutableComponent hint = CreateLang.translateDirect("gui.contraptions.network_overstressed");
            for (Component component : TooltipHelper.cutTextComponent(hint, FontHelper.Palette.GRAY_AND_WHITE)) {
                CreateLang.builder().add(component.copy()).forGoggles(tooltip);
            }
            return true;
        }
        if (notFastEnough) {
            CreateLang.translate("tooltip.speedRequirement").style(ChatFormatting.GOLD).forGoggles(tooltip);
            MutableComponent hint = CreateLang.translateDirect("gui.contraptions.not_fast_enough",
                    getBlockState().getBlock().getName());
            for (Component component : TooltipHelper.cutTextComponent(hint, FontHelper.Palette.GRAY_AND_WHITE)) {
                CreateLang.builder().add(component.copy()).forGoggles(tooltip);
            }
            return true;
        }
        return false;
    }

    // Check if this is thruster too fast for tooltip
    private boolean isThrusterTooFastForTooltip() {
        return Math.abs(getExtraKinetics().getSpeed()) > THRUSTER_TOO_FAST_TOOLTIP_RPM;
    }

    // Handle the assembly fluid distributor
    private static class AssemblyFluidDistributor implements IFluidHandler {
        // Bearing
        private final ThrusterBearingBlockEntity bearing;

        // Initialize the assembly fluid distributor
        public AssemblyFluidDistributor(ThrusterBearingBlockEntity bearing) {
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
            for (IFluidHandler handler : bearing.collectAttachedFuelHandlers(FluidStack.EMPTY)) {
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
            for (IFluidHandler handler : bearing.collectAttachedFuelHandlers(FluidStack.EMPTY)) {
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

            for (IFluidHandler handler : bearing.collectAttachedFuelHandlers(stack)) {
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

            List<IFluidHandler> activeTargets = new ArrayList<>(bearing.collectAttachedFuelHandlers(resource));
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
        private final ThrusterBearingBlockEntity bearing;

        // Initialize the assembly energy distributor
        public AssemblyEnergyDistributor(ThrusterBearingBlockEntity bearing) {
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

    // Handle the angle range screen behaviour
    private static class AngleRangeScreenBehaviour extends ScrollOptionBehaviour<AngleRangeControlOption> {
        // Initialize the angle range screen behaviour
        public AngleRangeScreenBehaviour(ThrusterBearingBlockEntity be, ValueBoxTransform slot) {
            super(AngleRangeControlOption.class, RANGE_LABEL, be, slot);
            setValue(0);
        }

        // Check if this accepts value settings
        @Override
        public boolean acceptsValueSettings() {
            return false;
        }

        // Handle the short interact event
        @Override
        public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
            if (!getWorld().isClientSide || !FMLEnvironment.dist.isClient()) {
                return;
            }

            openRangeScreenClient((ThrusterBearingBlockEntity) blockEntity);
        }

        // Open the range screen client
        private static void openRangeScreenClient(ThrusterBearingBlockEntity blockEntity) {
            try {

                Class<?> clientScreensClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.CTClientScreens");
                Method openMethod = clientScreensClass.getMethod("openThrusterBearingRangeScreen", ThrusterBearingBlockEntity.class);
                openMethod.invoke(null, blockEntity);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    // Define the angle range control option values
    private enum AngleRangeControlOption implements INamedIconOptions {
        CONFIGURE;

        // Get the icon
        @Override
        public AllIcons getIcon() {
            return AllIcons.I_MOVE_GAUGE;
        }

        // Get the translation key
        @Override
        public String getTranslationKey() {
            return "createthrusters.thruster_bearing.angle_range";
        }
    }

    // Handle the angle range value box
    private static class AngleRangeValueBox extends CenteredSideValueBoxTransform {
        // Initialize the angle range value box
        public AngleRangeValueBox() {
            super((state, side) -> side.getAxis() != state.getValue(BlockStateProperties.FACING).getAxis());
        }

        // Get the south location
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8.0D, 8.0D, 15.75D);
        }

        // Get the local offset
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction facing = state.getValue(BlockStateProperties.FACING);
            return super.getLocalOffset(level, pos, state)
                    .subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.3125D));
        }

        // Get the scale
        @Override
        public float getScale() {
            return 0.35f;
        }
    }

    // Get the graph readable data
    @Override
    public Map<String, String> graphReadableData() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pivot_angle", "number");
        fields.put("servo_input_angle", "number");
        fields.put("min_angle", "number");
        fields.put("max_angle", "number");
        fields.put("control_mode", "string");
        fields.put("angle_mode", "string");
        return fields;
    }

    // Get the graph writable data
    @Override
    public Map<String, String> graphWritableData() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("pivot_angle", "number");
        fields.put("min_angle", "number");
        fields.put("max_angle", "number");
        fields.put("control_mode", "string");
        fields.put("angle_mode", "string");
        return fields;
    }

    // Get the graph writable options
    @Override
    public Map<String, List<String>> graphWritableOptions() {
        return Map.of(
                "control_mode", List.of("auto", "redstone", "computer", "servo"),
                "angle_mode", List.of("range", "swivel"));
    }

    // Read the graph data
    @Override
    public AdvancedGraphDocument.Value readGraphData(String field) {
        return switch (field) {
            case "pivot_angle" -> AdvancedGraphDocument.Value.number(getSmoothedPivotAngleDeg());
            case "servo_input_angle" -> AdvancedGraphDocument.Value.number(getServoInputAngleDegrees());
            case "min_angle" -> AdvancedGraphDocument.Value.number(getMinAngleDegrees());
            case "max_angle" -> AdvancedGraphDocument.Value.number(getMaxAngleDegrees());
            case "control_mode" -> AdvancedGraphDocument.Value.string(getControlMode().name().toLowerCase());
            case "angle_mode" -> AdvancedGraphDocument.Value.string(getAngleMode().name().toLowerCase());
            default -> AdvancedGraphDocument.Value.number(0);
        };
    }

    // Write the graph data
    @Override
    public boolean writeGraphData(String field, AdvancedGraphDocument.Value value) {
        try {
            switch (field) {
                case "pivot_angle" -> setPivotAngleDegrees(value.asNumber());
                case "min_angle" -> setMinAngleDegrees(value.asNumber());
                case "max_angle" -> setMaxAngleDegrees(value.asNumber());
                case "control_mode" -> setControlMode(ControlMode.valueOf(value.asString().trim().toUpperCase()));
                case "angle_mode" -> setAngleMode(AngleMode.valueOf(value.asString().trim().toUpperCase()));
                default -> {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    // Define the control mode values
    public enum ControlMode {
        AUTO,
        REDSTONE,
        COMPUTER,
        SERVO
    }

    // Define the angle mode values
    public enum AngleMode {
        RANGE,
        SWIVEL
    }
}

// Handle the thruster bearing redstone range
final class ThrusterBearingRedstoneRange {
    // Initialize the thruster bearing redstone range
    private ThrusterBearingRedstoneRange() {
    }

    // Calculate the target
    static double computeTarget(double minAngleDeg, double maxAngleDeg,
                                int forwardSignal, int backwardSignal, boolean inverted) {
        int forward = Mth.clamp(forwardSignal, 0, 15);
        int backward = Mth.clamp(backwardSignal, 0, 15);
        int netSignal = forward - backward;
        if (inverted) {
            netSignal = -netSignal;
        }

        double neutralAngle = Mth.clamp(0.0D, minAngleDeg, maxAngleDeg);
        double strength = Mth.clamp(Math.abs(netSignal) / 15.0D, 0.0D, 1.0D);
        return netSignal >= 0
                ? Mth.lerp(strength, neutralAngle, maxAngleDeg)
                : Mth.lerp(strength, neutralAngle, minAngleDeg);
    }
}
