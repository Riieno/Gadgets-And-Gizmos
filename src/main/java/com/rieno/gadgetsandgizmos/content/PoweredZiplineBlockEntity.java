package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedRopeCompat;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.compat.simulated.SchematicSubLevelReferenceRemapper;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.lib.zipline.ZiplineHandoffSpline;
import com.rieno.gadgetsandgizmos.lib.zipline.ZiplineRider;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Keep a powered zipline moving, linked and safely attached across normal and Sable levels
public class PoweredZiplineBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity,
        KinematicContraption, MenuProvider, BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final float DEFAULT_MAX_SPEED = 0.18f;
    public static final float MIN_CONFIGURED_MAX_SPEED = 0.01f;
    public static final float MAX_CONFIGURED_MAX_SPEED = 1.0f;
    public static final float DEFAULT_DAMPING = 0.45f;
    public static final float MIN_CONFIGURED_DAMPING = 0.0f;
    public static final float MAX_CONFIGURED_DAMPING = 1.0f;
    private static final int SIGNAL_HARD_CHANGE_THRESHOLD = 8;
    private static final float SIGNAL_RAMP_PER_TICK = 1.0f;
    private static final int PATH_SCAN_RADIUS = 4;
    private static final int HANGING_ROPE_SCAN_RADIUS = 8;
    private static final int HANGING_ROPE_ATTACHMENT_VALIDATION_INTERVAL = 20;
    private static final long MISSING_ROPE_CARRIER_REGISTRATION_GRACE_TICKS = 40L;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current rope holder
    private RopeStrandHolderBehavior ropeHolder;
    // Tracked extra hanging rope holders
    private final List<RopeStrandHolderBehavior> extraHangingRopeHolders = new ArrayList<>();
    // Tracked hanging rope attachment validations
    private final Map<UUID, HangingRopeAttachmentValidation> hangingRopeAttachmentValidations = new HashMap<>();
    // Forward binding
    private final FrequencyBinding forwardBinding = new FrequencyBinding("powered_zipline_forward");
    // Backward binding
    private final FrequencyBinding backwardBinding = new FrequencyBinding("powered_zipline_backward");
    // Forward receiver
    private final ZiplineLinkReceiver forwardReceiver = new ZiplineLinkReceiver(forwardBinding);
    // Backward receiver
    private final ZiplineLinkReceiver backwardReceiver = new ZiplineLinkReceiver(backwardBinding);
    // Forward signal inertia
    private final SignalInertia forwardSignalInertia = new SignalInertia();
    // Backward signal inertia
    private final SignalInertia backwardSignalInertia = new SignalInertia();

    // Current rope position
    private float ropePosition;
    // Current prev rope position
    private float prevRopePosition;
    // Current rope length
    private float ropeLength = 1.0f;
    // Keeps forward/backward controls consistent when the next rope is attached in reverse.
    private int ropeTravelDirection = 1;
    private transient long nextRopeHandoffScanTick = Long.MIN_VALUE;
    @Nullable
    private RopeHandoff ropeHandoff;
    @Nullable
    private ChainHandoff chainHandoff;
    private int chainTravelDirection = 1;
    private boolean chainSourceReversed;
    private boolean chainTravelFrameInitialized = true;
    // Attached chain pos
    @Nullable
    private BlockPos attachedChainPos;
    // Attached chain connection
    @Nullable
    private BlockPos attachedChainConnection;
    // Attached rope UUID
    @Nullable
    private UUID attachedRopeUUID;
    // Attached rope start
    @Nullable
    private RopeCarrierAttachment attachedRopeStart;
    // Attached rope end
    @Nullable
    private RopeCarrierAttachment attachedRopeEnd;
    // Attached sub-level id
    @Nullable
    private UUID attachedSubLevelId;
    // Current riding player UUID
    @Nullable
    private UUID ridingPlayerUUID;
    @Nullable
    private UUID ridingEntityUUID;
    private transient int missingRiderTicks;
    // Current manual forward signal
    private int manualForwardSignal;
    // Current manual backward signal
    private int manualBackwardSignal;
    private transient boolean riderInputActive;
    @Nullable
    private transient BlockPos riderSelectedChainConnection;
    // Current damped attachment delta
    private float dampedAttachmentDelta;
    // Configured max speed
    private float configuredMaxSpeed = DEFAULT_MAX_SPEED;
    // Current configured damping
    private float configuredDamping = Float.NaN;
    // Tracks whether follow chain is set
    private boolean followChain;
    // Tracks whether assembly transfer is in progress
    private boolean assemblyTransferInProgress;
    // Tracks whether kinematic is registered
    private boolean kinematicRegistered;
    // Tracks whether powered zipline is breaking from missing carrier
    private boolean breakingFromMissingCarrier;
    // Current missing rope carrier validation start
    private long missingRopeCarrierValidationStart = Long.MIN_VALUE;
    // Tracks whether powered zipline is destroying extra hanging rope
    private boolean destroyingExtraHangingRope;
    // Tracks whether powered zipline is creating zipline rope
    private boolean creatingZiplineRope;
    // Current path constraint handle
    private PhysicsConstraintHandle pathConstraintHandle;
    // Current path constraint world anchor
    @Nullable
    private Vec3 pathConstraintWorldAnchor;

    // Local bounds
    private BoundingBox3i localBounds;
    // Current mass tracker
    private MassTracker massTracker;
    // Floating cluster container
    private final FloatingClusterContainer floatingClusterContainer = new FloatingClusterContainer();
    // Tracked lift providers
    private final Map<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> liftProviders = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the powered zipline
    public PoweredZiplineBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.POWERED_ZIPLINE.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(java.util.List<BlockEntityBehaviour> behaviours) {
        ropeHolder = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeHolder);
    }

    // Initialize the powered zipline
    @Override
    public void initialize() {
        super.initialize();
        rebuildKinematicData();
    }

    // Destroy the powered zipline
    @Override
    public void destroy() {
        clearPathConstraint();
        unregisterKinematic();
        if (assemblyTransferInProgress) {
            assemblyTransferInProgress = false;
            return;
        }
        detachZiplineRider();
        destroyHangingRopes(null, getAttachmentPoint(worldPosition, getBlockState()), false);
        super.destroy();
    }

    // Invalidate the powered zipline
    @Override
    public void invalidate() {
        unloadExtraHangingRopeHolders();
        super.invalidate();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the chunk unloaded event
    @Override
    public void onChunkUnloaded() {
        unloadExtraHangingRopeHolders();
        super.onChunkUnloaded();
    }

    // Update the powered zipline
    @Override
    public void tick() {
        super.tick();
        prevRopePosition = ropePosition;
        if (ropeHandoff != null) ropeHandoff.previous = ropeHandoff.progress;
        if (chainHandoff != null) chainHandoff.previous = chainHandoff.progress;
        tickExtraHangingRopeHolders();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (hasPathAttachment()) {
            PathCarrierStatus carrierStatus = getPathCarrierStatus();
            if (carrierStatus == PathCarrierStatus.BROKEN) {
                breakAndDropFromMissingCarrier();
                return;
            }
            if (carrierStatus == PathCarrierStatus.DEFERRED) {
                holdAssembledSubLevelPose();
                manualForwardSignal = 0;
                manualBackwardSignal = 0;
                riderInputActive = false;
                riderSelectedChainConnection = null;
                protectRidingPlayerFromFall();
                return;
            }
        } else {
            clearPathConstraint();
            return;
        }

        refreshRopeHandoffSpline();
        refreshChainHandoffSpline();
        refreshChainTravelFrame();
        tickAssembledSubLevelPose();
        int forwardLink = queryWirelessSignal(forwardBinding, forwardReceiver);
        int backwardLink = queryWirelessSignal(backwardBinding, backwardReceiver);
        boolean controlled = forwardLink > 0 || backwardLink > 0 || riderInputActive;
        float forward = forwardSignalInertia.update(forwardLink);
        float backward = backwardSignalInertia.update(backwardLink);
        if (riderInputActive) {
            forward = manualForwardSignal;
            backward = manualBackwardSignal;
        }
        manualForwardSignal = 0;
        manualBackwardSignal = 0;
        riderInputActive = false;
        float delta = controlled
                ? ((forward - backward) / 15.0f) * getConfiguredMaxSpeed()
                : getPassiveChainDelta();
        if (attachedChainPos != null && attachedChainConnection == null && chainHandoff == null)
            delta *= 360.0f / (float) (Math.PI * 1.5D);
        delta = applyHangingRopeInertiaDamping(delta);
        if (delta != 0.0f) {
            advancePath(delta);
        }
        riderSelectedChainConnection = null;

        protectRidingPlayerFromFall();
        tickZiplineRider();
    }

    // Protect the riding player from fall
    private void protectRidingPlayerFromFall() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (ridingPlayerUUID == null || serverLevel == null) {
            return;
        }
        Player player = serverLevel.getPlayerByUUID(ridingPlayerUUID);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.fallDistance = 0.0f;
        }
    }

    // Try to auto attach to chain or rope
    public boolean tryAutoAttachToChainOrRope(Player player) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (tryAttachToNearestRope()) {
            doAutoAssemble();
            setChanged();
            sendData();
            return true;
        }
        if (!tryAttachToNearestChain()) return false;
        doAutoAssemble();
        setChanged();
        sendData();
        return true;
    }

    // Attach the chain
    public boolean attachToChain(BlockPos chainPos, @Nullable BlockPos connection, float pos) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, chainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
            return false;
        }
        chain.prepareStats();
        float length = 360.0f;
        BlockPos normalizedConnection = connection;
        if (normalizedConnection != null) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(normalizedConnection);
            if (stats == null) {
                normalizedConnection = null;
            } else {
                length = stats.chainLength();
            }
        }
        attachedChainPos = chainPos.immutable();
        attachedChainConnection = normalizedConnection == null ? null : normalizedConnection.immutable();
        chainHandoff = null;
        chainTravelDirection = chain.getSpeed() < 0.0f ? -1 : 1;
        chainSourceReversed = chain.reversed;
        chainTravelFrameInitialized = true;
        followChain = true;
        attachedRopeUUID = null;
        ropeHandoff = null;
        clearRopeCarrierAttachments();
        ropeLength = Math.max(0.01f, length);
        ropePosition = Mth.clamp(pos, 0.0f, ropeLength);
        prevRopePosition = ropePosition;
        setChanged();
        sendData();
        return true;
    }

    // Attach the rope
    public boolean attachToRope(UUID ropeUUID, float pos) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand strand = manager == null ? null : manager.getStrand(ropeUUID);
        if (strand == null) {
            return false;
        }
        float length = getRopeLength(strand);
        attachedRopeUUID = ropeUUID;
        ropeHandoff = null;
        chainHandoff = null;
        ropeTravelDirection = 1;
        nextRopeHandoffScanTick = Long.MIN_VALUE;
        attachedChainPos = null;
        attachedChainConnection = null;
        captureRopeCarrierAttachments(strand);
        ropeLength = Math.max(0.01f, length);
        ropePosition = Mth.clamp(pos, 0.0f, ropeLength);
        prevRopePosition = ropePosition;
        setChanged();
        sendData();
        return true;
    }

    // Ensure the assembled
    public boolean ensureAssembled() {
        return doAutoAssemble();
    }

    // Check if this has path attachment
    public boolean hasPathAttachment() {
        return attachedChainPos != null || attachedRopeUUID != null;
    }

    // Check if this has create movement sensitive state
    public boolean hasCreateMovementSensitiveState() {
        return hasPathAttachment()
                || attachedSubLevelId != null
                || ridingPlayerUUID != null
                || hasAttachedHangingRopes();
    }

    // Try to attach hanging rope
    public boolean tryAttachHangingRope(Player player, ItemStack ropeStack) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (ropeStack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) {
            BlockPos targetPos = ropeStack.get(SimDataComponents.ROPE_FIRST_CONNECTION);
            RopeStrandHolderBehavior targetHolder = targetPos == null ? null : findRopeHolder(targetPos);
            Vec3 targetPosition = targetHolder == null ? (targetPos == null ? worldPosition.getCenter() : targetPos.getCenter())
                    : holderWorldAttachmentPoint(targetHolder);
            if (targetHolder != null && !targetHolder.isAttached() && attachHangingRopeToHolder(player, ropeStack, targetHolder, targetPosition)) {
                ropeStack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
                return true;
            }
            ropeStack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
            return false;
        }

        RopeConnectorBlockEntity connector = findNearestFreeRopeConnector();
        if (connector == null || connector.getRopeHolder() == null) {
            return false;
        }
        return attachHangingRopeToHolder(player, ropeStack, connector.getRopeHolder(), connectorWorldPosition(connector));
    }

    // Try to attach hanging rope to target
    public boolean tryAttachHangingRopeToTarget(Player player, ItemStack ropeStack, BlockPos targetPos) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        RopeStrandHolderBehavior targetHolder = findRopeHolder(targetPos);
        if (targetHolder == null || targetHolder.blockEntity == this || targetHolder.isAttached()) {
            return false;
        }
        return attachHangingRopeToHolder(player, ropeStack, targetHolder, holderWorldAttachmentPoint(targetHolder));
    }

    // Find the rope holder
    private @Nullable RopeStrandHolderBehavior findRopeHolder(BlockPos pos) {
        if (level == null) {
            return null;
        }
        RopeStrandHolderBehavior holder = RopeItem.getRopeHolder(level, pos);
        if (holder != null) {
            return holder;
        }
        return resolveRopeHolderBehavior(SimulatedHelper.findBlockEntityIncludingSubLevels(level, pos));
    }

    // Resolve the rope holder behavior
    private @Nullable RopeStrandHolderBehavior resolveRopeHolderBehavior(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        RopeStrandHolderBehavior holder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        return holder;
    }

    // Get the holder world attachment point
    private Vec3 holderWorldAttachmentPoint(RopeStrandHolderBehavior holder) {
        Vec3 local = holder.getAttachmentPoint();
        Vec3 world = SimulatedHelper.toContainingWorldPosition(holder.blockEntity, local);
        return world == null ? local : world;
    }

    // Attach the hanging rope to holder
    private boolean attachHangingRopeToHolder(Player player, ItemStack ropeStack, RopeStrandHolderBehavior targetHolder, Vec3 notifyPosition) {
        if (!attachHangingRopeToHolder(targetHolder, notifyPosition)) {
            return false;
        }
        sendLatestOwnedRopeToPlayer(player);
        if (!player.isCreative()) {
            ropeStack.shrink(1);
        }
        return true;
    }

    // Try to create rope from simulated
    public boolean tryCreateRopeFromSimulated(RopeStrandHolderBehavior src, RopeStrandHolderBehavior target) {
        if (creatingZiplineRope || level == null || level.isClientSide()) {
            return false;
        }
        RopeStrandHolderBehavior targetHolder;
        if (src.blockEntity == this) {
            targetHolder = target;
        } else if (target.blockEntity == this) {
            targetHolder = src;
        } else {
            return false;
        }
        if (targetHolder == null || targetHolder.blockEntity == this || !isHangingRopeHolderFree(targetHolder)) {
            return false;
        }
        return attachHangingRopeToHolder(targetHolder, holderWorldAttachmentPoint(targetHolder));
    }

    // Check if this is creating zipline rope
    public boolean isCreatingZiplineRope() {
        return creatingZiplineRope;
    }

    // Attach the hanging rope to holder
    private boolean attachHangingRopeToHolder(RopeStrandHolderBehavior targetHolder, Vec3 notifyPosition) {
        RopeStrandHolderBehavior ziplineHolder = getOrCreateFreeHangingRopeHolder();
        if (ziplineHolder == null || !isHangingRopeHolderFree(ziplineHolder)) {
            return false;
        }
        if (!isHangingRopeHolderFree(targetHolder)) {
            return false;
        }
        creatingZiplineRope = true;
        try {
            if (!SimulatedRopeCompat.createRope(ziplineHolder, targetHolder, false)) {
                return false;
            }
        } finally {
            creatingZiplineRope = false;
        }
        ensureOneFreeHangingRopeHolder();
        Vec3 soundPosition = SimulatedHelper.toContainingWorldPosition(this, worldPosition.getCenter());
        level.playSound(null, soundPosition.x, soundPosition.y, soundPosition.z,
                SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        level.playSound(null, notifyPosition.x, notifyPosition.y, notifyPosition.z,
                SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5f, 1.0f);
        setChanged();
        sendData();
        targetHolder.blockEntity.notifyUpdate();
        return true;
    }

    // Send the latest owned rope to player
    private void sendLatestOwnedRopeToPlayer(Player player) {
        RopeStrandHolderBehavior latest = null;
        if (ropeHolder != null && ropeHolder.ownsRope() && ropeHolder.getOwnedStrand() != null) {
            latest = ropeHolder;
        }
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            if (holder.ownsRope() && holder.getOwnedStrand() != null) {
                latest = holder;
            }
        }
        if (latest != null) {
            sendOwnedRopeToPlayer(latest, player);
        }
    }

    // Send the owned rope to player
    private void sendOwnedRopeToPlayer(RopeStrandHolderBehavior holder, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !holder.ownsRope() || holder.getOwnedStrand() == null) {
            return;
        }
        try {
            Class<?> managerClass = Class.forName("foundry.veil.api.network.VeilPacketManager");
            Object sink = managerClass.getMethod("player", ServerPlayer.class).invoke(null, serverPlayer);
            sink.getClass().getMethod("sendPacket", CustomPacketPayload[].class)
                    .invoke(sink, (Object) new CustomPacketPayload[]{holder.makeUpdatePacket()});
        } catch (Exception ignored) {
        }
    }

    // Find the nearest free rope connector
    private @Nullable RopeConnectorBlockEntity findNearestFreeRopeConnector() {
        Vec3 ziplineWorld = SimulatedHelper.toContainingWorldPosition(this, worldPosition.getCenter());
        double maxDistanceSq = HANGING_ROPE_SCAN_RADIUS * HANGING_ROPE_SCAN_RADIUS;
        RopeConnectorBlockEntity best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(level, worldPosition, 1)) {
            if (blockEntity instanceof RopeConnectorBlockEntity connector) {
                double distanceSq = connectorWorldPosition(connector).distanceToSqr(ziplineWorld);
                if (distanceSq <= maxDistanceSq && distanceSq < bestDistanceSq && isFreeConnector(connector)) {
                    best = connector;
                    bestDistanceSq = distanceSq;
                }
            }
        }

        for (SubLevel subLevel : SableLevelApi.subLevels(level)) {
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (!(blockEntity instanceof RopeConnectorBlockEntity connector) || !isFreeConnector(connector)) {
                    continue;
                }
                double distanceSq = connectorWorldPosition(connector).distanceToSqr(ziplineWorld);
                if (distanceSq <= maxDistanceSq && distanceSq < bestDistanceSq) {
                    best = connector;
                    bestDistanceSq = distanceSq;
                }
            }
        }
        return best;
    }

    // Check if this is a free connector
    private boolean isFreeConnector(RopeConnectorBlockEntity connector) {
        RopeStrandHolderBehavior holder = connector.getRopeHolder();
        return holder != null && !holder.isAttached();
    }

    // Get the connector world position
    private Vec3 connectorWorldPosition(RopeConnectorBlockEntity connector) {
        Vec3 pos = SimulatedHelper.toContainingWorldPosition(connector, connector.getBlockPos().getCenter());
        return pos == null ? connector.getBlockPos().getCenter() : pos;
    }

    // Try to attach to nearest chain
    private boolean tryAttachToNearestChain() {
        ChainConveyorBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(worldPosition.offset(-PATH_SCAN_RADIUS, -PATH_SCAN_RADIUS, -PATH_SCAN_RADIUS),
                worldPosition.offset(PATH_SCAN_RADIUS, PATH_SCAN_RADIUS, PATH_SCAN_RADIUS))) {
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
                continue;
            }
            double dist = candidate.distSqr(worldPosition);
            if (dist < bestDist) {
                bestDist = dist;
                best = chain;
            }
        }
        if (best == null) {
            return false;
        }
        best.prepareStats();
        attachedChainPos = best.getBlockPos().immutable();
        attachedRopeUUID = null;
        clearRopeCarrierAttachments();
        attachedChainConnection = best.connectionStats.isEmpty() ? null : best.connectionStats.keySet().iterator().next();
        chainHandoff = null;
        ropeHandoff = null;
        chainTravelDirection = best.getSpeed() < 0.0f ? -1 : 1;
        chainSourceReversed = best.reversed;
        chainTravelFrameInitialized = true;
        followChain = true;
        ropeLength = attachedChainConnection == null ? 360.0f : best.connectionStats.get(attachedChainConnection).chainLength();
        ropePosition = Mth.clamp(ropePosition, 0.0f, ropeLength);
        prevRopePosition = ropePosition;
        return true;
    }

    // Get the path carrier status
    private PathCarrierStatus getPathCarrierStatus() {
        if (attachedChainPos != null) {
            if (chainHandoff != null) {
                PathCarrierStatus source = getChainCarrierStatus(chainHandoff.sourcePos, chainHandoff.sourceConnection);
                if (source != PathCarrierStatus.VALID) return source;
                return getChainCarrierStatus(chainHandoff.targetPos, chainHandoff.targetConnection);
            }
            return getChainCarrierStatus(attachedChainPos, attachedChainConnection);
        }
        if (attachedRopeUUID != null) {
            ServerLevel serverLevel = SableLevelApi.serverLevel(level);
            if (serverLevel == null) {
                return PathCarrierStatus.DEFERRED;
            }
            ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
            ServerRopeStrand strand = manager == null ? null : manager.getStrand(attachedRopeUUID);
            if (strand != null) {
                missingRopeCarrierValidationStart = Long.MIN_VALUE;
                captureRopeCarrierAttachments(strand);
                return areAllRopeAttachmentsLoaded(serverLevel, strand)
                        ? PathCarrierStatus.VALID
                        : PathCarrierStatus.DEFERRED;
            }

            boolean completeSnapshot = attachedRopeStart != null && attachedRopeEnd != null;
            boolean allAttachmentsLoaded = completeSnapshot
                    && isRopeCarrierAttachmentLoaded(serverLevel, attachedRopeStart)
                    && isRopeCarrierAttachmentLoaded(serverLevel, attachedRopeEnd);
            if (!completeSnapshot || !allAttachmentsLoaded) {
                missingRopeCarrierValidationStart = Long.MIN_VALUE;
                return PathCarrierStatus.DEFERRED;
            }
            long gameTime = serverLevel.getGameTime();
            if (missingRopeCarrierValidationStart == Long.MIN_VALUE) {
                missingRopeCarrierValidationStart = gameTime;
            }
            long loadedTicks = Math.max(0L, gameTime - missingRopeCarrierValidationStart);
            return shouldDeferMissingRopeCarrier(completeSnapshot, allAttachmentsLoaded, loadedTicks)
                    ? PathCarrierStatus.DEFERRED
                    : PathCarrierStatus.BROKEN;
        }
        return PathCarrierStatus.VALID;
    }

    private PathCarrierStatus getChainCarrierStatus(BlockPos position, @Nullable BlockPos connection) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, position);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain))
            return level.isLoaded(position) ? PathCarrierStatus.BROKEN : PathCarrierStatus.DEFERRED;
        chain.prepareStats();
        if (connection == null) return PathCarrierStatus.VALID;
        if (!chain.connectionStats.containsKey(connection)) return PathCarrierStatus.BROKEN;
        BlockEntity target = SimulatedHelper.findBlockEntityIncludingSubLevels(level, position.offset(connection));
        if (target == null && !level.isLoaded(position.offset(connection))) return PathCarrierStatus.DEFERRED;
        return target instanceof ChainConveyorBlockEntity ? PathCarrierStatus.VALID : PathCarrierStatus.BROKEN;
    }

    // Check if this should defer missing rope carrier
    static boolean shouldDeferMissingRopeCarrier(boolean completeSnapshot, boolean allAttachmentsLoaded,
                                                 long loadedTicks) {
        return !completeSnapshot || !allAttachmentsLoaded
                || loadedTicks < MISSING_ROPE_CARRIER_REGISTRATION_GRACE_TICKS;
    }

    // Check if the rope carrier attachment is loaded
    private boolean isRopeCarrierAttachmentLoaded(ServerLevel serverLevel,
                                                  RopeCarrierAttachment attachment) {
        if (attachment.subLevelId() != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(serverLevel, attachment.subLevelId());
            return isSubLevelAttachmentChunkLoaded(subLevel, attachment.blockPos());
        }
        return isRootAttachmentTicking(serverLevel, attachment.blockPos());
    }

    // Capture the rope carrier attachments
    private void captureRopeCarrierAttachments(ServerRopeStrand strand) {
        RopeCarrierAttachment start = RopeCarrierAttachment.from(strand.getAttachment(RopeAttachmentPoint.START));
        RopeCarrierAttachment end = RopeCarrierAttachment.from(strand.getAttachment(RopeAttachmentPoint.END));
        if (start == null || end == null) {
            return;
        }
        if (start.equals(attachedRopeStart) && end.equals(attachedRopeEnd)) {
            return;
        }
        attachedRopeStart = start;
        attachedRopeEnd = end;
        SableAssemblyTopologyInvalidation.invalidate(level);
        setChanged();
    }

    // Clear the rope carrier attachments
    private void clearRopeCarrierAttachments() {
        if (attachedRopeStart == null && attachedRopeEnd == null) {
            missingRopeCarrierValidationStart = Long.MIN_VALUE;
            return;
        }
        attachedRopeStart = null;
        attachedRopeEnd = null;
        missingRopeCarrierValidationStart = Long.MIN_VALUE;
        SableAssemblyTopologyInvalidation.invalidate(level);
    }

    // Handle the break and drop from missing carrier
    private void breakAndDropFromMissingCarrier() {
        if (breakingFromMissingCarrier || level == null || level.isClientSide()) {
            return;
        }
        breakingFromMissingCarrier = true;
        ServerLevel serverLevel = resolveServerLevel(level);
        Vec3 dropPosition = SimulatedHelper.toContainingWorldPosition(this, worldPosition.getCenter());
        if (dropPosition == null) {
            dropPosition = worldPosition.getCenter();
        }

        destroyHangingRopes(null, dropPosition, false);
        clearPathConstraint();

        attachedChainPos = null;
        attachedChainConnection = null;
        attachedRopeUUID = null;
        chainHandoff = null;
        ropeHandoff = null;
        clearRopeCarrierAttachments();
        ridingPlayerUUID = null;

        if (serverLevel != null) {
            serverLevel.addFreshEntity(new ItemEntity(serverLevel, dropPosition.x, dropPosition.y, dropPosition.z,
                    new ItemStack(CTItems.POWERED_ZIPLINE.get())));
            serverLevel.playSound(null, dropPosition.x, dropPosition.y, dropPosition.z,
                    SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.6f, 0.9f);
        }

        if (!level.destroyBlock(worldPosition, false)) {
            level.removeBlock(worldPosition, false);
        }
    }

    // Destroy the hanging ropes
    public boolean destroyHangingRopes(@Nullable ServerPlayer player, @Nullable Vec3 dropPosition, boolean keepOneEmptyPoint) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        boolean destroyedAny = false;
        if (ropeHolder != null && ropeHolder.isAttached()) {
            destroyAttachedHolderRope(ropeHolder, player, dropPosition);
            destroyedAny = true;
        }
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            if (holder.isAttached()) {
                destroyAttachedHolderRope(holder, player, dropPosition);
                destroyedAny = true;
            }
        }
        hangingRopeAttachmentValidations.clear();
        extraHangingRopeHolders.clear();
        if (keepOneEmptyPoint) {
            ensureOneFreeHangingRopeHolder();
        }
        setChanged();
        sendData();
        return destroyedAny;
    }

    // Destroy the extra hanging rope near
    public boolean destroyExtraHangingRopeNear(@Nullable ServerPlayer player, @Nullable Vec3 dropPosition) {
        if (destroyingExtraHangingRope || level == null || level.isClientSide() || dropPosition == null) {
            return false;
        }
        Iterator<RopeStrandHolderBehavior> iterator = extraHangingRopeHolders.iterator();
        while (iterator.hasNext()) {
            RopeStrandHolderBehavior holder = iterator.next();
            if (!holder.isAttached() || !holder.ownsRope()) {
                continue;
            }
            ServerRopeStrand strand = holder.getOwnedStrand();
            RopeAttachment endAttachment = strand == null ? null : strand.getAttachment(RopeAttachmentPoint.END);
            Vec3 endPoint = endAttachment == null ? null : resolveHolderAttachmentPoint(endAttachment.blockAttachment());
            if (endPoint == null || endPoint.distanceToSqr(dropPosition) > 1.0D) {
                continue;
            }
            destroyingExtraHangingRope = true;
            UUID ropeUUID = strand.getUUID();
            try {
                SimulatedRopeCompat.destroyRope(holder, player, dropPosition,
                        player == null || !player.hasInfiniteMaterials());
            } finally {
                destroyingExtraHangingRope = false;
            }
            hangingRopeAttachmentValidations.remove(ropeUUID);
            if (!holder.isAttached()) {
                holder.unload();
                iterator.remove();
            }
            ensureOneFreeHangingRopeHolder();
            setChanged();
            sendData();
            return true;
        }
        return false;
    }

    // Destroy the hanging rope
    public boolean destroyHangingRope(UUID ropeUUID, @Nullable ServerPlayer player, @Nullable Vec3 dropPosition) {
        if (destroyingExtraHangingRope || ropeUUID == null || level == null || level.isClientSide()) {
            return false;
        }
        hangingRopeAttachmentValidations.remove(ropeUUID);
        if (ropeHolder != null && ownsRope(ropeHolder, ropeUUID)) {
            destroyingExtraHangingRope = true;
            try {
                SimulatedRopeCompat.destroyRope(ropeHolder, player,
                        dropPosition == null ? getAttachmentPoint(worldPosition, getBlockState()) : dropPosition,
                        player == null || !player.hasInfiniteMaterials());
            } finally {
                destroyingExtraHangingRope = false;
            }
            ensureOneFreeHangingRopeHolder();
            setChanged();
            sendData();
            return true;
        }
        Iterator<RopeStrandHolderBehavior> iterator = extraHangingRopeHolders.iterator();
        while (iterator.hasNext()) {
            RopeStrandHolderBehavior holder = iterator.next();
            if (!ownsRope(holder, ropeUUID)) {
                continue;
            }
            destroyingExtraHangingRope = true;
            try {
                SimulatedRopeCompat.destroyRope(holder, player,
                        dropPosition == null ? getAttachmentPoint(worldPosition, getBlockState()) : dropPosition,
                        player == null || !player.hasInfiniteMaterials());
            } finally {
                destroyingExtraHangingRope = false;
            }
            if (isHangingRopeHolderFree(holder)) {
                holder.unload();
                iterator.remove();
            }
            ensureOneFreeHangingRopeHolder();
            setChanged();
            sendData();
            return true;
        }
        return false;
    }

    // Destroy the nearest hanging rope
    public boolean destroyNearestHangingRope(@Nullable ServerPlayer player, Vec3 hitPosition) {
        if (level == null || level.isClientSide() || hitPosition == null) {
            return false;
        }
        RopeStrandHolderBehavior best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        if (ropeHolder != null && ropeHolder.ownsRope() && ropeHolder.getOwnedStrand() != null) {
            best = ropeHolder;
            bestDistanceSq = distanceToStrandSq(ropeHolder.getOwnedStrand(), hitPosition);
        }
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            ServerRopeStrand strand = holder.getOwnedStrand();
            if (!holder.ownsRope() || strand == null) {
                continue;
            }
            double distanceSq = distanceToStrandSq(strand, hitPosition);
            if (distanceSq < bestDistanceSq) {
                best = holder;
                bestDistanceSq = distanceSq;
            }
        }
        if (best == null || best.getOwnedStrand() == null) {
            return false;
        }
        return destroyHangingRope(best.getOwnedStrand().getUUID(), player, hitPosition);
    }

    // Get the distance to strand sq
    private double distanceToStrandSq(ServerRopeStrand strand, Vec3 point) {
        var points = strand.getPoints();
        if (points.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            Vector3d current = new Vector3d((Vector3dc) points.get(i));
            Vec3 currentVec = new Vec3(current.x, current.y, current.z);
            best = Math.min(best, currentVec.distanceToSqr(point));
            if (i + 1 < points.size()) {
                Vector3d next = new Vector3d((Vector3dc) points.get(i + 1));
                best = Math.min(best, distanceToSegmentSq(point, currentVec, new Vec3(next.x, next.y, next.z)));
            }
        }
        return best;
    }

    // Get the distance to segment sq
    private double distanceToSegmentSq(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSq = segment.lengthSqr();
        if (lengthSq <= 1.0E-8D) {
            return point.distanceToSqr(start);
        }
        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSq, 0.0D, 1.0D);
        return point.distanceToSqr(start.add(segment.scale(t)));
    }

    // Check if this owns rope
    private boolean ownsRope(RopeStrandHolderBehavior holder, UUID ropeUUID) {
        ServerRopeStrand strand = holder.getOwnedStrand();
        return holder.ownsRope() && strand != null && ropeUUID.equals(strand.getUUID());
    }

    // Get the owned hanging rope holder
    public @Nullable RopeStrandHolderBehavior getOwnedHangingRopeHolder(UUID ropeUUID) {
        if (ropeUUID == null) {
            return null;
        }
        if (ropeHolder != null && ownsRope(ropeHolder, ropeUUID)) {
            return ropeHolder;
        }
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            if (ownsRope(holder, ropeUUID)) {
                return holder;
            }
        }
        return null;
    }

    // Handle the hanging rope attachment validation
    public boolean handleHangingRopeAttachmentValidation(RopeStrandHolderBehavior holder,
                                                        @Nullable ServerRopeStrand strand) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (holder == null || strand == null || serverLevel == null) {
            return false;
        }
        UUID ropeUUID = strand.getUUID();
        if (!ownsRope(holder, ropeUUID)) {
            return false;
        }

        RopeAttachment endAttachment = strand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment == null) {
            hangingRopeAttachmentValidations.remove(ropeUUID);
            destroyBrokenHangingRope(holder, holder.getAttachmentPoint());
            return true;
        }

        long gameTime = serverLevel.getGameTime();
        HangingRopeAttachmentValidation cached = hangingRopeAttachmentValidations.get(ropeUUID);
        if (cached != null && cached.matches(endAttachment) && cached.nextCheckTick() > gameTime) {
            return true;
        }
        if (cached == null && !isHangingRopeValidationTick(ropeUUID, gameTime)) {
            return true;
        }

        HangingRopeAttachmentStatus status = validateHangingRopeEndpoint(serverLevel, strand, endAttachment);
        if (status == HangingRopeAttachmentStatus.BROKEN) {
            hangingRopeAttachmentValidations.remove(ropeUUID);
            destroyBrokenHangingRope(holder, endAttachment.blockAttachment().getCenter());
            return true;
        }

        hangingRopeAttachmentValidations.put(ropeUUID, new HangingRopeAttachmentValidation(
                endAttachment.subLevelID(),
                endAttachment.blockAttachment(),
                nextHangingRopeValidationTick(ropeUUID, gameTime)));
        return true;
    }

    // Validate the hanging rope endpoint
    private HangingRopeAttachmentStatus validateHangingRopeEndpoint(ServerLevel serverLevel,
                                                                    ServerRopeStrand strand,
                                                                    RopeAttachment endAttachment) {
        if (!areAllRopeAttachmentsLoaded(serverLevel, strand)) {
            return HangingRopeAttachmentStatus.DEFERRED;
        }

        BlockEntity blockEntity = resolveAttachmentBlockEntity(serverLevel, endAttachment);
        RopeStrandHolderBehavior holder = resolveRopeHolderBehavior(blockEntity);
        return holder == null ? HangingRopeAttachmentStatus.BROKEN : HangingRopeAttachmentStatus.VALID;
    }

    // Check if all rope attachments are loaded
    private boolean areAllRopeAttachmentsLoaded(ServerLevel serverLevel, ServerRopeStrand strand) {
        for (RopeAttachment attachment : strand.getAttachments()) {
            if (attachment == null || attachment.blockAttachment() == null) {
                return false;
            }
            UUID subLevelId = attachment.subLevelID();
            BlockPos attachmentPos = attachment.blockAttachment();
            if (subLevelId != null) {
                Object subLevel = SubLevelBlockEntityCollector.getSubLevel(serverLevel, subLevelId);
                if (!isSubLevelAttachmentChunkLoaded(subLevel, attachmentPos)) {
                    return false;
                }
                continue;
            }
            if (!isRootAttachmentTicking(serverLevel, attachmentPos)) {
                return false;
            }
        }
        return true;
    }

    // Check if this is root attachment ticking
    private static boolean isRootAttachmentTicking(ServerLevel serverLevel, BlockPos pos) {
        return PhysicsChunkTicketManager.isChunkLoadedEnough(
                serverLevel, pos.getX() >> 4, pos.getZ() >> 4);
    }

    // Check if the sublevel attachment chunk is loaded
    private boolean isSubLevelAttachmentChunkLoaded(@Nullable Object subLevel, BlockPos pos) {
        if (!(subLevel instanceof SubLevel sableSubLevel) || sableSubLevel.isRemoved()) {
            return false;
        }
        LevelPlot plot = sableSubLevel.getPlot();
        return plot != null && plot.getChunkHolder(plot.toLocal(new ChunkPos(pos))) != null;
    }

    // Resolve the attachment block entity
    private @Nullable BlockEntity resolveAttachmentBlockEntity(ServerLevel serverLevel, RopeAttachment attachment) {
        UUID subLevelId = attachment.subLevelID();
        BlockPos attachmentPos = attachment.blockAttachment();
        if (subLevelId != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(serverLevel, subLevelId);
            return SubLevelBlockEntityCollector.getBlockEntity(subLevel, attachmentPos);
        }
        return serverLevel.isLoaded(attachmentPos) ? serverLevel.getBlockEntity(attachmentPos) : null;
    }

    // Check if this is hanging rope validation tick
    private boolean isHangingRopeValidationTick(UUID ropeUUID, long gameTime) {
        long validationSlot = Math.floorMod(ropeUUID.getLeastSignificantBits(),
                HANGING_ROPE_ATTACHMENT_VALIDATION_INTERVAL);
        return Math.floorMod(gameTime, HANGING_ROPE_ATTACHMENT_VALIDATION_INTERVAL) == validationSlot;
    }

    // Calculate the next hanging rope validation tick
    private long nextHangingRopeValidationTick(UUID ropeUUID, long gameTime) {
        long nextTick = gameTime + 1L;
        long validationSlot = Math.floorMod(ropeUUID.getLeastSignificantBits(),
                HANGING_ROPE_ATTACHMENT_VALIDATION_INTERVAL);
        long ticksUntilSlot = Math.floorMod(validationSlot
                - Math.floorMod(nextTick, HANGING_ROPE_ATTACHMENT_VALIDATION_INTERVAL),
                HANGING_ROPE_ATTACHMENT_VALIDATION_INTERVAL);
        return nextTick + ticksUntilSlot;
    }

    // Destroy the broken hanging rope
    private void destroyBrokenHangingRope(RopeStrandHolderBehavior holder, Vec3 dropPosition) {
        if (destroyingExtraHangingRope || level == null) {
            return;
        }
        boolean returnItem = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        destroyingExtraHangingRope = true;
        try {
            SimulatedRopeCompat.destroyRope(holder, null, dropPosition, returnItem);
        } finally {
            destroyingExtraHangingRope = false;
        }
        setChanged();
        sendData();
    }

    // Resolve the holder attachment point
    private @Nullable Vec3 resolveHolderAttachmentPoint(BlockPos pos) {
        if (level == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return pos.getCenter();
        }
        RopeStrandHolderBehavior holder = smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        return holder == null ? pos.getCenter() : holder.getAttachmentPoint();
    }

    // Destroy the attached holder rope
    private void destroyAttachedHolderRope(RopeStrandHolderBehavior holder, @Nullable ServerPlayer player, @Nullable Vec3 dropPosition) {
        Vec3 resolvedDropPosition = dropPosition == null ? getAttachmentPoint(worldPosition, getBlockState()) : dropPosition;
        forgetHangingRopeValidation(holder);
        if (holder.ownsRope()) {
            SimulatedRopeCompat.destroyRope(holder, player, resolvedDropPosition,
                    player == null || !player.hasInfiniteMaterials());
        } else {
            holder.destroy();
        }
    }

    // Forget the hanging rope validation
    private void forgetHangingRopeValidation(@Nullable RopeStrandHolderBehavior holder) {
        ServerRopeStrand strand = holder == null ? null : holder.getOwnedStrand();
        if (strand != null) {
            hangingRopeAttachmentValidations.remove(strand.getUUID());
        }
    }

    // Update the extra hanging rope holders
    private void tickExtraHangingRopeHolders() {
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            holder.tick();
        }
        if (level != null && !level.isClientSide()) {
            pruneUnusedHangingRopeHolders();
        }
    }

    // Get or create the free hanging rope holder
    private @Nullable RopeStrandHolderBehavior getOrCreateFreeHangingRopeHolder() {
        if (ropeHolder == null || isHangingRopeHolderFree(ropeHolder)) {
            return ropeHolder;
        }
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            if (isHangingRopeHolderFree(holder)) {
                return holder;
            }
        }
        RopeStrandHolderBehavior holder = new RopeStrandHolderBehavior(this);
        extraHangingRopeHolders.add(holder);
        return holder;
    }

    // Ensure the one free hanging rope holder
    private void ensureOneFreeHangingRopeHolder() {
        getOrCreateFreeHangingRopeHolder();
        pruneUnusedHangingRopeHolders();
    }

    // Prune the unused hanging rope holders
    private void pruneUnusedHangingRopeHolders() {
        boolean hasFreePoint = ropeHolder == null || isHangingRopeHolderFree(ropeHolder);
        Iterator<RopeStrandHolderBehavior> iterator = extraHangingRopeHolders.iterator();
        while (iterator.hasNext()) {
            RopeStrandHolderBehavior holder = iterator.next();
            if (!isHangingRopeHolderFree(holder)) {
                continue;
            }
            if (hasFreePoint) {
                holder.unload();
                iterator.remove();
                continue;
            }
            hasFreePoint = true;
        }
    }

    // Check if the hanging rope holder is free
    private boolean isHangingRopeHolderFree(RopeStrandHolderBehavior holder) {
        return !holder.isAttached() && holder.getOwnedStrand() == null && holder.getAttachedStrand() == null;
    }

    // Unload the extra hanging rope holders
    private void unloadExtraHangingRopeHolders() {
        hangingRopeAttachmentValidations.clear();
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            holder.unload();
        }
    }

    // Try to attach to nearest rope
    private boolean tryAttachToNearestRope() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
        if (manager == null) {
            return false;
        }
        Vec3 center = SimulatedHelper.toContainingWorldPosition(this, worldPosition.getCenter());
        double maxDistSq = PATH_SCAN_RADIUS * PATH_SCAN_RADIUS;
        ServerRopeStrand bestStrand = null;
        float bestAlong = 0.0f;
        double bestDistSq = Double.MAX_VALUE;
        float bestLength = 1.0f;
        for (ServerRopeStrand strand : manager.getAllStrands()) {
            PathSample sample = sampleRope(strand, center);
            if (sample != null && sample.distanceSq < bestDistSq && sample.distanceSq <= maxDistSq) {
                bestStrand = strand;
                bestAlong = sample.along;
                bestDistSq = sample.distanceSq;
                bestLength = sample.length;
            }
        }
        if (bestStrand == null) {
            return false;
        }
        attachedRopeUUID = bestStrand.getUUID();
        ropeTravelDirection = 1;
        nextRopeHandoffScanTick = Long.MIN_VALUE;
        attachedChainPos = null;
        attachedChainConnection = null;
        captureRopeCarrierAttachments(bestStrand);
        ropeLength = Math.max(0.01f, bestLength);
        ropePosition = Mth.clamp(bestAlong, 0.0f, ropeLength);
        prevRopePosition = ropePosition;
        return true;
    }

    // Sample the rope
    private @Nullable PathSample sampleRope(ServerRopeStrand strand, Vec3 worldPoint) {
        var points = strand.getPoints();
        if (points.size() < 2) {
            return null;
        }
        Vector3d query = new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z);
        float cumulative = 0.0f;
        float total = 0.0f;
        double bestDistance = Double.MAX_VALUE;
        float bestAlong = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d a = new Vector3d((Vector3dc) points.get(i));
            Vector3d b = new Vector3d((Vector3dc) points.get(i + 1));
            Vector3d ab = b.sub((Vector3dc) a, new Vector3d());
            float segmentLength = (float) ab.length();
            if (segmentLength < 1.0E-5f) {
                continue;
            }
            double along = Mth.clamp(query.sub((Vector3dc) a, new Vector3d()).dot(ab) / (segmentLength * segmentLength), 0.0, 1.0);
            Vector3d closest = a.fma(along, ab, new Vector3d());
            double distance = closest.distanceSquared(query);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestAlong = cumulative + (float) (along * segmentLength);
            }
            cumulative += segmentLength;
            total += segmentLength;
        }
        return new PathSample(bestAlong, total, bestDistance);
    }

    // Get the rope length
    private float getRopeLength(ServerRopeStrand strand) {
        var points = strand.getPoints();
        float total = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d a = new Vector3d((Vector3dc) points.get(i));
            Vector3d b = new Vector3d((Vector3dc) points.get(i + 1));
            total += (float) a.distance((Vector3dc) b);
        }
        return total;
    }

    // W follows the rider's look in world space; S follows the opposite direction.
    public void applyRiderInput(ServerPlayer player, boolean reverseLook) {
        if (!player.getUUID().equals(ridingPlayerUUID)) return;
        riderInputActive = true;
        manualForwardSignal = 0;
        manualBackwardSignal = 0;
        riderSelectedChainConnection = null;
        Vec3 look = player.getLookAngle();
        if (reverseLook) look = look.scale(-1.0D);
        int direction = chainHandoff != null
                ? chainHandoff.controlSign * (reverseLook ? -1 : 1)
                : riderTravelDirection(look);
        if (direction > 0) {
            manualForwardSignal = 15;
            manualBackwardSignal = 0;
        } else if (direction < 0) {
            manualBackwardSignal = 15;
            manualForwardSignal = 0;
        }
    }

    private int riderTravelDirection(Vec3 look) {
        if (ropeHandoff != null) {
            Vec3 tangent = getHandoffWorldTangent();
            return tangent != null && tangent.dot(look) < 0.0D
                    ? -ropeHandoff.controlSign : ropeHandoff.controlSign;
        }
        if (attachedChainPos != null) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos);
            if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) return 0;
            chain.prepareStats();
            if (attachedChainConnection == null) {
                riderSelectedChainConnection = riderFacingChainConnection(chain, look);
                if (riderSelectedChainConnection == null) return 0;
                ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(riderSelectedChainConnection);
                float clockwise = positiveAngleDistance(ropePosition, stats.tangentAngle());
                float counterclockwise = positiveAngleDistance(stats.tangentAngle(), ropePosition);
                return clockwise <= counterclockwise ? 1 : -1;
            }
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(attachedChainConnection);
            if (stats == null) return 0;
            float before = Mth.clamp(ropePosition - 0.1f, 0.0f, stats.chainLength());
            float after = Mth.clamp(ropePosition + 0.1f, 0.0f, stats.chainLength());
            Vec3 tangent = chainWorldPosition(chain, attachedChainConnection, after)
                    .subtract(chainWorldPosition(chain, attachedChainConnection, before));
            return (tangent.dot(look) >= 0.0D ? 1 : -1) * chainTravelDirection;
        }
        float before = Mth.clamp(ropePosition - 0.1f, 0.0f, ropeLength);
        float after = Mth.clamp(ropePosition + 0.1f, 0.0f, ropeLength);
        Vec3 tangent = getSplineWorldPosition(after).subtract(getSplineWorldPosition(before));
        return (tangent.dot(look) >= 0.0D ? 1 : -1) * ropeTravelDirection;
    }

    private @Nullable BlockPos riderFacingChainConnection(ChainConveyorBlockEntity chain, Vec3 look) {
        BlockPos best = null;
        double bestAlignment = 0.1D;
        for (BlockPos connection : chain.connections) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(connection);
            if (stats == null || stats.chainLength() <= 0.01f) continue;
            Vec3 outgoing = chainWorldPosition(chain, connection, Math.min(1.0f, stats.chainLength()))
                    .subtract(chainWorldPosition(chain, connection, 0.0f));
            if (outgoing.lengthSqr() < 1.0E-6D) continue;
            double alignment = outgoing.normalize().dot(look);
            if (alignment > bestAlignment || alignment == bestAlignment && best != null
                    && connection.asLong() < best.asLong()) {
                best = connection;
                bestAlignment = alignment;
            }
        }
        return best;
    }

    // Set the riding player
    public void setRidingPlayer(@Nullable UUID playerUUID) {
        ridingPlayerUUID = playerUUID;
        if (playerUUID == null) {
            riderInputActive = false;
            riderSelectedChainConnection = null;
        }
        setChanged();
        sendData();
    }

    public boolean hasZiplineRider() {
        return ridingEntityUUID != null;
    }

    public boolean attachZiplineRider(Entity entity) {
        if (level == null || level.isClientSide || !hasPathAttachment()
                || ridingEntityUUID != null || !(entity instanceof ZiplineRider rider)) return false;
        ridingEntityUUID = entity.getUUID();
        missingRiderTicks = 0;
        rider.ziplineAttached();
        tickZiplineRider();
        setChanged();
        sendData();
        return true;
    }

    private void detachZiplineRider() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (ridingEntityUUID != null && serverLevel != null
                && serverLevel.getEntity(ridingEntityUUID) instanceof ZiplineRider rider) rider.ziplineDetached();
        ridingEntityUUID = null;
        missingRiderTicks = 0;
        setChanged();
        sendData();
    }

    private void tickZiplineRider() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (ridingEntityUUID == null || serverLevel == null) return;
        Entity entity = serverLevel.getEntity(ridingEntityUUID);
        if (entity == null) {
            Vector3d position = getWorldPosition(1.0D);
            if (serverLevel.hasChunkAt(BlockPos.containing(position.x, position.y, position.z))
                    && ++missingRiderTicks > 40) {
                ridingEntityUUID = null;
                missingRiderTicks = 0;
                setChanged();
                sendData();
            }
            return;
        }
        missingRiderTicks = 0;
        if (entity.isRemoved() || !(entity instanceof ZiplineRider rider)) {
            ridingEntityUUID = null;
            setChanged();
            sendData();
            return;
        }
        Vec3 grip = getHandoffWorldPosition(1.0D);
        if (grip == null) {
            Vector3d position = getWorldPosition(1.0D);
            grip = new Vec3(position.x, position.y, position.z);
        }
        Vec3 tangent = getHandoffWorldTangent();
        if (tangent == null) {
            float next = Mth.clamp(ropePosition + 0.1f, 0.0f, ropeLength);
            float before = Mth.clamp(ropePosition - 0.1f, 0.0f, ropeLength);
            tangent = getSplineWorldPosition(next).subtract(getSplineWorldPosition(before));
        }
        rider.ziplineMoved(grip.add(0.0D, -0.35D, 0.0D), tangent);
    }

    // Advance the path
    private void advancePath(float delta) {
        if (chainHandoff != null) {
            advanceChainHandoff(delta);
            return;
        }
        if (ropeHandoff != null) {
            advanceRopeHandoff(delta);
            return;
        }
        if (attachedChainPos != null) {
            advanceChain(delta);
            return;
        }
        if (attachedRopeUUID != null) {
            float travel = delta * ropeTravelDirection;
            float next = ropePosition + travel;
            if ((next > ropeLength || next < 0.0f) && tryCrossAdjacentRope(travel, delta)) {
                return;
            }
            setPathPositionClamped(next);
            return;
        }
        setPathPositionClamped(ropePosition + delta);
    }

    // Change ropes only at two physically adjacent, loaded rope connectors.
    private boolean tryCrossAdjacentRope(float travel, float controlDelta) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || attachedRopeUUID == null) return false;
        long gameTime = serverLevel.getGameTime();
        if (gameTime < nextRopeHandoffScanTick) return false;
        nextRopeHandoffScanTick = gameTime + 10L;
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand current = manager == null ? null : manager.getStrand(attachedRopeUUID);
        if (current == null) return false;

        RopeAttachmentPoint exit = travel > 0.0f ? RopeAttachmentPoint.END : RopeAttachmentPoint.START;
        RopeAttachment source = current.getAttachment(exit);
        if (source == null || !(resolveAttachmentBlockEntity(serverLevel, source) instanceof RopeConnectorBlockEntity sourceConnector)) {
            return false;
        }
        Vec3 sourcePosition = connectorWorldPosition(sourceConnector);
        Vec3 exitDirection = ropeEndpointDirection(current, exit, true);

        ServerRopeStrand best = null;
        RopeAttachmentPoint entry = null;
        float bestLength = 0.0f;
        double bestScore = -Double.MAX_VALUE;
        for (ServerRopeStrand candidate : manager.getAllStrands()) {
            if (candidate.getUUID().equals(attachedRopeUUID)
                    || !areAllRopeAttachmentsLoaded(serverLevel, candidate)) continue;
            float candidateLength = getRopeLength(candidate);
            if (candidateLength <= 0.01f) continue;
            for (RopeAttachmentPoint point : RopeAttachmentPoint.values()) {
                RopeAttachment attachment = candidate.getAttachment(point);
                if (attachment == null || !(resolveAttachmentBlockEntity(serverLevel, attachment)
                        instanceof RopeConnectorBlockEntity nextConnector)) continue;
                Vec3 nextPosition = connectorWorldPosition(nextConnector);
                if (!adjacentRopeConnectors(source, attachment, sourcePosition, nextPosition)) continue;
                Vec3 entryDirection = ropeEndpointDirection(candidate, point, false);
                double alignment = exitDirection == null || entryDirection == null
                        ? 0.0D : exitDirection.dot(entryDirection);
                double score = alignment * 2.0D - sourcePosition.distanceToSqr(nextPosition);
                if (score > bestScore || score == bestScore && best != null
                        && candidate.getUUID().compareTo(best.getUUID()) < 0) {
                    best = candidate;
                    entry = point;
                    bestLength = candidateLength;
                    bestScore = score;
                }
            }
        }
        if (best == null) return false;

        float overshoot = travel > 0.0f ? ropePosition + travel - ropeLength : -ropePosition - travel;
        Vec3 start = getRopeWorldPosition(current, exit == RopeAttachmentPoint.END ? ropeLength : 0.0f);
        Vec3 end = getRopeWorldPosition(best, entry == RopeAttachmentPoint.START ? 0.0f : bestLength);
        Vec3 outgoing = exitDirection == null ? end.subtract(start) : exitDirection;
        Vec3 incoming = ropeEndpointDirection(best, entry, false);
        ZiplineHandoffSpline spline = new ZiplineHandoffSpline(start, outgoing, end,
                incoming == null ? end.subtract(start) : incoming);
        ropePosition = exit == RopeAttachmentPoint.END ? ropeLength : 0.0f;
        ropeHandoff = new RopeHandoff(best.getUUID(), entry == RopeAttachmentPoint.START,
                exit == RopeAttachmentPoint.END, controlDelta > 0.0f ? 1 : -1,
                spline, Math.min(Math.max(0.0f, overshoot), 0.2f));
        ropeHandoff.progress = Math.min(ropeHandoff.progress, ropeHandoff.length * 0.5f);
        nextRopeHandoffScanTick = gameTime + 10L;
        setChanged();
        sendData();
        return true;
    }

    // Travel the short connector spline before changing rope carriers. Never skip it in one tick.
    private void advanceRopeHandoff(float delta) {
        RopeHandoff handoff = ropeHandoff;
        if (handoff == null) return;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        ServerLevelRopeManager manager = serverLevel == null ? null : ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand destination = manager == null ? null : manager.getStrand(handoff.target);
        if (destination == null || !areAllRopeAttachmentsLoaded(serverLevel, destination)) return;
        float step = Math.min(Math.abs(delta), 0.2f) * (delta * handoff.controlSign >= 0 ? 1 : -1);
        handoff.progress += step;
        if (handoff.progress <= 0.0f) {
            ropeHandoff = null;
            ropePosition = handoff.exitAtEnd
                    ? Math.max(0.0f, ropeLength + handoff.progress)
                    : Math.min(ropeLength, -handoff.progress);
            prevRopePosition = ropePosition;
        } else if (handoff.progress >= handoff.length) {
            float overshoot = handoff.progress - handoff.length;
            attachedRopeUUID = handoff.target;
            captureRopeCarrierAttachments(destination);
            ropeLength = Math.max(0.01f, getRopeLength(destination));
            ropePosition = handoff.entryAtStart ? Math.min(overshoot, ropeLength)
                    : Math.max(0.0f, ropeLength - overshoot);
            prevRopePosition = ropePosition;
            ropeTravelDirection = handoff.controlSign * (handoff.entryAtStart ? 1 : -1);
            ropeHandoff = null;
        }
        setChanged();
        sendData();
    }

    // Follow moving connector endpoints during the handoff, including after a world reload.
    private void refreshRopeHandoffSpline() {
        RopeHandoff handoff = ropeHandoff;
        if (handoff == null || attachedRopeUUID == null) return;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        ServerLevelRopeManager manager = serverLevel == null ? null : ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand source = manager == null ? null : manager.getStrand(attachedRopeUUID);
        ServerRopeStrand destination = manager == null ? null : manager.getStrand(handoff.target);
        if (source == null || destination == null
                || !areAllRopeAttachmentsLoaded(serverLevel, destination)) return;
        RopeAttachmentPoint exit = handoff.exitAtEnd ? RopeAttachmentPoint.END : RopeAttachmentPoint.START;
        RopeAttachmentPoint entry = handoff.entryAtStart ? RopeAttachmentPoint.START : RopeAttachmentPoint.END;
        Vec3 start = getRopeWorldPosition(source, handoff.exitAtEnd ? getRopeLength(source) : 0.0f);
        Vec3 end = getRopeWorldPosition(destination, handoff.entryAtStart ? 0.0f : getRopeLength(destination));
        if (start.distanceToSqr(handoff.spline.start()) < 1.0E-4D
                && end.distanceToSqr(handoff.spline.end()) < 1.0E-4D) return;
        Vec3 out = ropeEndpointDirection(source, exit, true);
        Vec3 in = ropeEndpointDirection(destination, entry, false);
        ZiplineHandoffSpline updated = new ZiplineHandoffSpline(start,
                out == null ? end.subtract(start) : out,
                end, in == null ? end.subtract(start) : in);
        float oldLength = handoff.length;
        handoff.spline = updated;
        handoff.length = updated.length();
        handoff.progress = Mth.clamp(handoff.progress / oldLength * handoff.length, 0.0f, handoff.length);
        handoff.previous = Mth.clamp(handoff.previous / oldLength * handoff.length, 0.0f, handoff.length);
        setChanged();
        sendData();
    }

    private static final class RopeHandoff {
        private final UUID target;
        private final boolean entryAtStart;
        private final boolean exitAtEnd;
        private final int controlSign;
        private ZiplineHandoffSpline spline;
        private float length;
        private float progress;
        private float previous;

        private RopeHandoff(UUID target, boolean entryAtStart, boolean exitAtEnd, int controlSign,
                            ZiplineHandoffSpline spline, float progress) {
            this.target = target;
            this.entryAtStart = entryAtStart;
            this.exitAtEnd = exitAtEnd;
            this.controlSign = controlSign;
            this.spline = spline;
            this.length = spline.length();
            this.progress = Mth.clamp(progress, 0.0f, this.length);
            this.previous = 0.0f;
        }
    }

    private static final class ChainHandoff {
        private final BlockPos sourcePos;
        @Nullable
        private final BlockPos sourceConnection;
        private final float sourcePosition;
        private final BlockPos targetPos;
        @Nullable
        private final BlockPos targetConnection;
        private final float targetPosition;
        private final int sourceDirection;
        private final int targetDirection;
        private final int controlSign;
        private ZiplineHandoffSpline spline;
        private float length;
        private float progress;
        private float previous;

        private ChainHandoff(BlockPos sourcePos, @Nullable BlockPos sourceConnection, float sourcePosition,
                             BlockPos targetPos, @Nullable BlockPos targetConnection, float targetPosition,
                             int sourceDirection, int targetDirection, int controlSign,
                             ZiplineHandoffSpline spline, float progress) {
            this.sourcePos = sourcePos;
            this.sourceConnection = sourceConnection;
            this.sourcePosition = sourcePosition;
            this.targetPos = targetPos;
            this.targetConnection = targetConnection;
            this.targetPosition = targetPosition;
            this.sourceDirection = sourceDirection;
            this.targetDirection = targetDirection;
            this.controlSign = controlSign;
            this.spline = spline;
            this.length = spline.length();
            this.progress = Mth.clamp(progress, 0.0f, this.length);
            this.previous = 0.0f;
        }
    }

    private static void writeHandoffPoint(CompoundTag tag, String name, Vec3 point) {
        tag.putDouble(name + "X", point.x);
        tag.putDouble(name + "Y", point.y);
        tag.putDouble(name + "Z", point.z);
    }

    private static Vec3 readHandoffPoint(CompoundTag tag, String name) {
        return new Vec3(tag.getDouble(name + "X"), tag.getDouble(name + "Y"), tag.getDouble(name + "Z"));
    }

    private static boolean adjacentRopeConnectors(RopeAttachment source, RopeAttachment target,
                                                   Vec3 sourceWorld, Vec3 targetWorld) {
        if (source.blockAttachment().equals(target.blockAttachment())
                && java.util.Objects.equals(source.subLevelID(), target.subLevelID())) return false;
        if (java.util.Objects.equals(source.subLevelID(), target.subLevelID())) {
            BlockPos a = source.blockAttachment();
            BlockPos b = target.blockAttachment();
            return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())
                    + Math.abs(a.getZ() - b.getZ()) == 1;
        }
        double distanceSq = sourceWorld.distanceToSqr(targetWorld);
        return distanceSq > 0.01D && distanceSq <= 1.75D * 1.75D;
    }

    private static @Nullable Vec3 ropeEndpointDirection(ServerRopeStrand strand,
                                                         RopeAttachmentPoint point, boolean exiting) {
        var points = strand.getPoints();
        if (points.size() < 2) return null;
        Vector3dc endpoint = point == RopeAttachmentPoint.START ? points.getFirst() : points.getLast();
        Vector3dc neighbor = point == RopeAttachmentPoint.START ? points.get(1) : points.get(points.size() - 2);
        Vec3 direction = new Vec3(neighbor.x() - endpoint.x(), neighbor.y() - endpoint.y(), neighbor.z() - endpoint.z());
        if (direction.lengthSqr() < 1.0E-8D) return null;
        return exiting ? direction.normalize().scale(-1.0D) : direction.normalize();
    }

    // Apply the hanging rope inertia damping
    private float applyHangingRopeInertiaDamping(float targetDelta) {
        if (!hasAttachedHangingRopes()) {
            dampedAttachmentDelta = targetDelta;
            return targetDelta;
        }
        float damping = getConfiguredDamping();
        if (damping <= 0.0f) {
            dampedAttachmentDelta = targetDelta;
            return targetDelta;
        }
        if (damping >= 1.0f) {
            damping = 0.98f;
        }
        dampedAttachmentDelta = Mth.lerp(1.0f - damping, dampedAttachmentDelta, targetDelta);
        if (Math.abs(dampedAttachmentDelta) < 1.0E-5f && Math.abs(targetDelta) < 1.0E-5f) {
            dampedAttachmentDelta = 0.0f;
        }
        return dampedAttachmentDelta;
    }

    // Check if this has attached hanging ropes
    private boolean hasAttachedHangingRopes() {
        if (ropeHolder != null && ropeHolder.isAttached()) {
            return true;
        }
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            if (holder.isAttached()) {
                return true;
            }
        }
        return false;
    }

    // Set the path position clamped
    private void setPathPositionClamped(float pos) {
        ropePosition = Mth.clamp(pos, 0.0f, Math.max(0.01f, ropeLength));
        setChanged();
        sendData();
    }

    // Advance the chain
    private void advanceChain(float delta) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) return;
        chain.prepareStats();
        if (attachedChainConnection != null) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(attachedChainConnection);
            if (stats == null) {
                attachedChainConnection = null;
                ropeLength = 360.0f;
                setPathPositionClamped(chain.wrapAngle(ropePosition));
                return;
            }
            float next = ropePosition + delta * chainTravelDirection;
            if (next > stats.chainLength()) {
                BlockPos nextChainPos = attachedChainPos.offset(attachedChainConnection);
                BlockEntity nextBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, nextChainPos);
                if (nextBlockEntity instanceof ChainConveyorBlockEntity nextChain) {
                    nextChain.prepareStats();
                    float entryAngle = nextChain.wrapAngle(stats.tangentAngle() + 180.0f
                            + (float) (70 * (chain.reversed ? -1 : 1)));
                    beginChainHandoff(chain, attachedChainConnection, stats.chainLength(), 1,
                            nextChain, null, entryAngle, delta > 0.0f ? 1 : -1, delta > 0.0f ? 1 : -1,
                            Math.max(0.0f, next - stats.chainLength()));
                    return;
                }
            }
            if (next < 0.0f) {
                beginChainHandoff(chain, attachedChainConnection, 0.0f, -1,
                        chain, null, chain.wrapAngle(stats.tangentAngle()), delta > 0.0f ? 1 : -1,
                        delta > 0.0f ? 1 : -1,
                        Math.max(0.0f, -next));
                return;
            }
            setPathPositionClamped(next);
            return;
        }

        float prev = ropePosition;
        float next = chain.wrapAngle(prev + delta);
        if (followChain) {
            BlockPos connection = findCrossedConnection(chain, prev, next, delta > 0.0f,
                    riderSelectedChainConnection);
            if (connection != null) {
                ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(connection);
                if (stats != null) {
                    float overshootDegrees = delta > 0.0f
                            ? positiveAngleDistance(stats.tangentAngle(), next)
                            : positiveAngleDistance(next, stats.tangentAngle());
                    beginChainHandoff(chain, null, stats.tangentAngle(), delta > 0.0f ? 1 : -1,
                            chain, connection, 0.0f, 1, delta > 0.0f ? 1 : -1,
                            overshootDegrees * (float) (Math.PI * 1.5D / 360.0D));
                    return;
                }
            }
        }
        ropeLength = 360.0f;
        ropePosition = next;
        setChanged();
        sendData();
    }

    private void beginChainHandoff(ChainConveyorBlockEntity source, @Nullable BlockPos sourceConnection,
                                   float sourcePosition, int sourceDirection,
                                   ChainConveyorBlockEntity target, @Nullable BlockPos targetConnection,
                                   float targetPosition, int targetDirection, int controlSign, float overshoot) {
        Vec3 start = chainWorldPosition(source, sourceConnection, sourcePosition);
        Vec3 end = chainWorldPosition(target, targetConnection, targetPosition);
        Vec3 out = chainTangent(source, sourceConnection, sourcePosition, sourceDirection);
        Vec3 in = chainTangent(target, targetConnection, targetPosition, targetDirection);
        ZiplineHandoffSpline spline = new ZiplineHandoffSpline(start, out, end, in);
        chainHandoff = new ChainHandoff(source.getBlockPos().immutable(), sourceConnection, sourcePosition,
                target.getBlockPos().immutable(), targetConnection, targetPosition,
                sourceDirection, targetDirection, controlSign, spline,
                Math.min(overshoot, spline.length() * 0.5f));
        ropePosition = sourcePosition;
        prevRopePosition = sourcePosition;
        setChanged();
        sendData();
    }

    private Vec3 chainTangent(ChainConveyorBlockEntity chain, @Nullable BlockPos connection,
                              float position, int direction) {
        float step = connection == null ? 1.0f : 0.05f;
        float before = connection == null ? chain.wrapAngle(position - step * direction)
                : Mth.clamp(position - step * direction, 0.0f, chain.connectionStats.get(connection).chainLength());
        float after = connection == null ? chain.wrapAngle(position + step * direction)
                : Mth.clamp(position + step * direction, 0.0f, chain.connectionStats.get(connection).chainLength());
        return chainWorldPosition(chain, connection, after).subtract(chainWorldPosition(chain, connection, before));
    }

    private void advanceChainHandoff(float delta) {
        ChainHandoff handoff = chainHandoff;
        if (handoff == null) return;
        BlockEntity destination = SimulatedHelper.findBlockEntityIncludingSubLevels(level, handoff.targetPos);
        if (!(destination instanceof ChainConveyorBlockEntity chain)) return;
        chain.prepareStats();
        if (handoff.targetConnection != null && !chain.connectionStats.containsKey(handoff.targetConnection)) return;
        float step = Math.min(Math.abs(delta), 0.2f) * (delta * handoff.controlSign >= 0 ? 1 : -1);
        handoff.progress += step;
        if (handoff.progress <= 0.0f) {
            chainHandoff = null;
            ropePosition = handoff.sourcePosition;
            prevRopePosition = ropePosition;
        } else if (handoff.progress >= handoff.length) {
            float overshoot = handoff.progress - handoff.length;
            attachedChainPos = handoff.targetPos;
            attachedChainConnection = handoff.targetConnection;
            chainSourceReversed = chain.reversed;
            chainTravelDirection = handoff.controlSign * handoff.targetDirection;
            ropeLength = handoff.targetConnection == null ? 360.0f
                    : Math.max(0.01f, chain.connectionStats.get(handoff.targetConnection).chainLength());
            ropePosition = handoff.targetConnection == null
                    ? chain.wrapAngle(handoff.targetPosition + handoff.targetDirection * overshoot
                            * 360.0f / (float) (Math.PI * 1.5D))
                    : Mth.clamp(handoff.targetPosition + handoff.targetDirection * overshoot, 0.0f, ropeLength);
            prevRopePosition = ropePosition;
            chainHandoff = null;
        }
        setChanged();
        sendData();
    }

    private void refreshChainHandoffSpline() {
        ChainHandoff handoff = chainHandoff;
        if (handoff == null) return;
        BlockEntity source = SimulatedHelper.findBlockEntityIncludingSubLevels(level, handoff.sourcePos);
        BlockEntity destination = SimulatedHelper.findBlockEntityIncludingSubLevels(level, handoff.targetPos);
        if (!(source instanceof ChainConveyorBlockEntity from)
                || !(destination instanceof ChainConveyorBlockEntity to)) return;
        from.prepareStats();
        to.prepareStats();
        if (handoff.sourceConnection != null && !from.connectionStats.containsKey(handoff.sourceConnection)) return;
        if (handoff.targetConnection != null && !to.connectionStats.containsKey(handoff.targetConnection)) return;
        Vec3 start = chainWorldPosition(from, handoff.sourceConnection, handoff.sourcePosition);
        Vec3 end = chainWorldPosition(to, handoff.targetConnection, handoff.targetPosition);
        if (start.distanceToSqr(handoff.spline.start()) < 1.0E-4D
                && end.distanceToSqr(handoff.spline.end()) < 1.0E-4D) return;
        ZiplineHandoffSpline updated = new ZiplineHandoffSpline(start,
                chainTangent(from, handoff.sourceConnection, handoff.sourcePosition, handoff.sourceDirection),
                end, chainTangent(to, handoff.targetConnection, handoff.targetPosition, handoff.targetDirection));
        float oldLength = handoff.length;
        handoff.spline = updated;
        handoff.length = updated.length();
        handoff.progress = Mth.clamp(handoff.progress / oldLength * handoff.length, 0.0f, handoff.length);
        handoff.previous = Mth.clamp(handoff.previous / oldLength * handoff.length, 0.0f, handoff.length);
        setChanged();
        sendData();
    }

    // Find the crossed connection
    private @Nullable BlockPos findCrossedConnection(ChainConveyorBlockEntity chain, float prev, float next,
                                                     boolean forward, @Nullable BlockPos riderChoice) {
        BlockPos nearest = null;
        float nearestDistance = Float.POSITIVE_INFINITY;
        for (BlockPos connection : chain.connections) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(connection);
            if (stats == null || riderChoice != null && !riderChoice.equals(connection)
                    || !crossedAngle(prev, next, stats.tangentAngle(), forward)
                    && !(riderChoice != null && riderChoice.equals(connection)
                    && Math.min(positiveAngleDistance(prev, stats.tangentAngle()),
                            positiveAngleDistance(stats.tangentAngle(), prev)) < 0.001f)) {
                continue;
            }
            float distance = forward
                    ? positiveAngleDistance(prev, stats.tangentAngle())
                    : positiveAngleDistance(stats.tangentAngle(), prev);
            if (distance < nearestDistance || distance == nearestDistance
                    && nearest != null && connection.asLong() < nearest.asLong()) {
                nearest = connection;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    // Check if the movement crossed the target angle
    private boolean crossedAngle(float prev, float next, float target, boolean forward) {
        float travel = forward
                ? positiveAngleDistance(prev, next)
                : positiveAngleDistance(next, prev);
        float toTarget = forward
                ? positiveAngleDistance(prev, target)
                : positiveAngleDistance(target, prev);
        return travel > 0.0f && toTarget > 0.0f && toTarget <= travel + 0.001f;
    }

    // Get the positive angle distance
    private float positiveAngleDistance(float from, float to) {
        return (AngleHelper.getShortestAngleDiff(from, to) + 360.0f) % 360.0f;
    }

    // Get the world position
    public Vector3d getWorldPosition(double partialTick) {
        if (ropeHandoff != null || chainHandoff != null) {
            Vec3 point = getHandoffWorldPosition(partialTick);
            return new Vector3d(point.x, point.y, point.z);
        }
        double t = Mth.lerp(partialTick, prevRopePosition, ropePosition);
        Vec3 sample = getSplineWorldPosition((float) t);
        return new Vector3d(sample.x, sample.y, sample.z);
    }

    public @Nullable Vec3 getHandoffWorldPosition(double partialTick) {
        ChainHandoff chain = chainHandoff;
        if (chain != null)
            return chain.spline.sample(Mth.lerp(partialTick, chain.previous, chain.progress) / chain.length);
        RopeHandoff handoff = ropeHandoff;
        if (handoff == null) return null;
        return handoff.spline.sample(Mth.lerp(partialTick, handoff.previous, handoff.progress) / handoff.length);
    }

    public @Nullable Vec3 getHandoffWorldTangent() {
        ChainHandoff chain = chainHandoff;
        if (chain != null) return chain.spline.tangent(chain.progress / chain.length);
        RopeHandoff handoff = ropeHandoff;
        return handoff == null ? null : handoff.spline.tangent(handoff.progress / handoff.length);
    }

    public List<Vec3> getHandoffSplinePoints() {
        ChainHandoff chain = chainHandoff;
        RopeHandoff handoff = ropeHandoff;
        if (chain == null && handoff == null) return List.of();
        ZiplineHandoffSpline spline = chain != null ? chain.spline : handoff.spline;
        List<Vec3> points = new ArrayList<>(17);
        for (int index = 0; index <= 16; index++) points.add(spline.sample(index / 16.0D));
        return points;
    }

    // Get the desired zipline center
    private Vector3d getDesiredZiplineCenter(double partialTick) {
        Vector3d pathPosition = getWorldPosition(partialTick);
        return pathPosition.add(0.0D, -0.35D, 0.0D);
    }

    // Get the spline world position
    private Vec3 getSplineWorldPosition(float pos) {
        if (attachedChainPos != null) {
            return getChainWorldPosition(pos);
        }
        if (attachedRopeUUID != null) {
            return getRopeWorldPosition(pos);
        }
        return SimulatedHelper.toContainingWorldPosition(this, worldPosition.getCenter());
    }

    // Get the chain world position
    private Vec3 getChainWorldPosition(float pos) {
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
            return worldPosition.getCenter();
        }
        return chainWorldPosition(chain, attachedChainConnection, pos);
    }

    private Vec3 chainWorldPosition(ChainConveyorBlockEntity chain, @Nullable BlockPos connection, float pos) {
        chain.prepareStats();
        Vec3 local = chain.getPackagePosition(pos, connection);
        Vec3 world = SimulatedHelper.toContainingWorldPosition(chain, local);
        return world == null ? local : world;
    }

    private float getPassiveChainDelta() {
        if (attachedChainPos == null) return 0.0f;
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) return 0.0f;
        chain.prepareStats();
        float speed = chain.getSpeed() / 360.0f;
        return attachedChainConnection == null ? speed : Math.abs(speed) * chainTravelDirection;
    }

    private void refreshChainTravelFrame() {
        if (attachedChainPos == null || chainHandoff != null) return;
        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos);
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) return;
        chain.prepareStats();
        if (!chainTravelFrameInitialized) {
            chainTravelDirection = chain.getSpeed() < 0.0f ? -1 : 1;
            chainSourceReversed = chain.reversed;
            chainTravelFrameInitialized = true;
            return;
        }
        if (attachedChainConnection != null && chain.reversed != chainSourceReversed)
            rebaseReversedChainSpan(chain);
        else chainSourceReversed = chain.reversed;
    }

    private void rebaseReversedChainSpan(ChainConveyorBlockEntity source) {
        BlockPos connection = attachedChainConnection;
        if (connection == null) return;
        BlockEntity other = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos.offset(connection));
        if (!(other instanceof ChainConveyorBlockEntity destination)) return;
        BlockPos returnConnection = connection.multiply(-1);
        destination.prepareStats();
        ChainConveyorBlockEntity.ConnectionStats stats = destination.connectionStats.get(returnConnection);
        if (stats == null) return;
        attachedChainPos = destination.getBlockPos().immutable();
        attachedChainConnection = returnConnection;
        ropeLength = Math.max(0.01f, stats.chainLength());
        ropePosition = Mth.clamp(ropeLength - ropePosition, 0.0f, ropeLength);
        prevRopePosition = ropePosition;
        chainTravelDirection *= -1;
        chainSourceReversed = destination.reversed;
        setChanged();
        sendData();
    }

    // Get the rope world position
    private Vec3 getRopeWorldPosition(float pos) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return worldPosition.getCenter();
        }
        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(serverLevel);
        ServerRopeStrand strand = manager == null ? null : manager.getStrand(attachedRopeUUID);
        if (strand == null) {
            return worldPosition.getCenter();
        }
        return getRopeWorldPosition(strand, pos);
    }

    // Get the rope world position
    private Vec3 getRopeWorldPosition(ServerRopeStrand strand, float pos) {
        var points = strand.getPoints();
        if (points.isEmpty()) {
            return worldPosition.getCenter();
        }
        if (points.size() == 1) {
            Vector3d only = new Vector3d((Vector3dc) points.getFirst());
            return new Vec3(only.x, only.y, only.z);
        }
        float clampedPosition = Mth.clamp(pos, 0.0f, Math.max(0.01f, getRopeLength(strand)));
        float cumulative = 0.0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vector3d a = new Vector3d((Vector3dc) points.get(i));
            Vector3d b = new Vector3d((Vector3dc) points.get(i + 1));
            Vector3d ab = b.sub((Vector3dc) a, new Vector3d());
            float segmentLength = (float) ab.length();
            if (clampedPosition <= cumulative + segmentLength) {
                double local = segmentLength <= 1.0E-5f ? 0.0 : (clampedPosition - cumulative) / segmentLength;
                Vector3d res = a.fma(local, ab, new Vector3d());
                return new Vec3(res.x, res.y, res.z);
            }
            cumulative += segmentLength;
        }
        Vector3d last = new Vector3d((Vector3dc) points.get(points.size() - 1));
        return new Vec3(last.x, last.y, last.z);
    }

    // Query the wireless signal
    private int queryWirelessSignal(FrequencyBinding binding, ZiplineLinkReceiver receiver) {
        if (level == null || !binding.isBound()) {
            return 0;
        }
        Couple<RedstoneLinkNetworkHandler.Frequency> key = receiver.getNetworkKey();
        return Math.max(queryWirelessSignalForLevel(level, key, receiver),
                queryWirelessSignalForLevel(SimulatedHelper.projectOutOfSubLevel(level, worldPosition.getCenter()) == null ? level : level, key, receiver));
    }

    // Query the wireless signal for level
    private int queryWirelessSignalForLevel(Level queryLevel, Couple<RedstoneLinkNetworkHandler.Frequency> key, IRedstoneLinkable self) {
        Map<Couple<RedstoneLinkNetworkHandler.Frequency>, Set<IRedstoneLinkable>> networks =
                Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(queryLevel);
        Set<IRedstoneLinkable> network = networks.get(key);
        if (network == null || network.isEmpty()) {
            return 0;
        }
        int signal = 0;
        for (IRedstoneLinkable linkable : network) {
            if (linkable == self || !linkable.isAlive()) {
                continue;
            }
            signal = Math.max(signal, Mth.clamp(linkable.getTransmittedStrength(), 0, 15));
        }
        return signal;
    }

    // Check if automatic assembly should run
    private boolean doAutoAssemble() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        Object currentSubLevel = getSableContaining();
        if (currentSubLevel != null) {
            attachedSubLevelId = SimulatedHelper.getSubLevelId(currentSubLevel);
            setChanged();
            sendData();
            return true;
        }
        ServerSubLevel subLevel = SubLevelAssemblyApi.assembleSingle(resolveServerLevel(level), worldPosition);
        if (subLevel == null) {
            return false;
        }
        UUID subLevelId = subLevel.getUniqueId();
        PoweredZiplineBlockEntity moved = findMovedZipline(subLevel);
        if (moved != null) {
            moved.attachedSubLevelId = subLevelId;
            moved.endAssemblyTransfer();
            moved.tickAssembledSubLevelPose();
            moved.setChanged();
            moved.sendData();
        }
        attachedSubLevelId = subLevelId;
        setChanged();
        sendData();
        return true;
    }

    // Find the moved zipline
    private @Nullable PoweredZiplineBlockEntity findMovedZipline(@Nullable Object subLevel) {
        if (!(subLevel instanceof SubLevel sableSubLevel)) {
            return null;
        }
        return SimulatedHelper.findBlockEntityInSubLevel(sableSubLevel, sableSubLevel.getPlot().getCenterBlock(),
                PoweredZiplineBlockEntity.class);
    }

    // Resolve the server level
    private @Nullable ServerLevel resolveServerLevel(Level src) {
        return SableLevelApi.serverLevel(src);
    }

    // Get the sable containing
    private @Nullable Object getSableContaining() {
        return SableLevelApi.containing(this);
    }

    // Resolve the attached sublevel
    private @Nullable Object resolveAttachedSubLevel() {
        if (level == null) {
            return null;
        }
        Object containing = getSableContaining();
        if (containing != null) {
            if (attachedSubLevelId == null) {
                attachedSubLevelId = SimulatedHelper.getSubLevelId(containing);
            }
            return containing;
        }
        if (attachedSubLevelId == null) {
            return null;
        }
        return SableLevelApi.subLevel(level, attachedSubLevelId);
    }

    // Update the assembled sublevel pose
    private void tickAssembledSubLevelPose() {
        Object subLevel = resolveAttachedSubLevel();
        if (subLevel == null) {
            clearPathConstraint();
            return;
        }
        Quaterniond orientation = sable$getOrientation(1.0D);
        Vector3dc rotationPoint = getSubLevelRotationPoint(subLevel);
        if (rotationPoint == null) {
            rotationPoint = getKinematicRotationPoint();
        }
        Vector3d posePosition = getPosePosForZiplineCenter(getDesiredZiplineCenter(1.0D), rotationPoint, orientation);
        boolean constrained = ensurePathConstraint(subLevel, posePosition, rotationPoint, orientation);
        if (!constrained || needsPoseCorrection(subLevel, posePosition)) {
            teleportSubLevel(subLevel, posePosition, orientation);
        }
        resetSubLevelVelocity(subLevel);
    }

    // Hold the assembled sublevel pose
    private void holdAssembledSubLevelPose() {
        Object subLevel = resolveAttachedSubLevel();
        if (subLevel == null) {
            clearPathConstraint();
            return;
        }
        if (pathConstraintHandle != null && pathConstraintHandle.isValid()) {
            resetSubLevelVelocity(subLevel);
            return;
        }
        Vector3d posePosition = getSubLevelPosition(subLevel);
        Vector3dc rotationPoint = getSubLevelRotationPoint(subLevel);
        Quaterniond orientation = getSubLevelOrientation(subLevel);
        if (posePosition != null && rotationPoint != null && orientation != null) {
            ensurePathConstraint(subLevel, posePosition, rotationPoint, orientation);
        }
        resetSubLevelVelocity(subLevel);
    }

    // Ensure the path constraint
    private boolean ensurePathConstraint(Object subLevel, Vector3dc posePosition, Vector3dc rotationPoint, Quaterniond orientation) {
        ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || subLevel == null || posePosition == null || rotationPoint == null || orientation == null
                || !isFiniteAndSafe(posePosition.x()) || !isFiniteAndSafe(posePosition.y()) || !isFiniteAndSafe(posePosition.z())
                || !isFiniteQuaternion(orientation)) {
            clearPathConstraint();
            return false;
        }

        Vec3 worldAnchor = new Vec3(posePosition.x(), posePosition.y(), posePosition.z());
        boolean hasValidConstraint = pathConstraintHandle != null && pathConstraintHandle.isValid();
        boolean anchorMoved = pathConstraintWorldAnchor == null
                || pathConstraintWorldAnchor.distanceToSqr(worldAnchor) > 1.0E-6D;
        if (hasValidConstraint && !anchorMoved) {
            return true;
        }

        clearPathConstraint();
        try {
            Vector3d anchorPosition = new Vector3d(posePosition);
            Vector3d anchorRotation = new Vector3d(rotationPoint);
            Object fixedConstraint = SableConstraintApi.fixedConfiguration(
                    anchorPosition, anchorRotation, orientation);

            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) {
                pathConstraintWorldAnchor = null;
                return false;
            }
            pathConstraintHandle = (PhysicsConstraintHandle) SableConstraintApi.addConstraint(
                    container.physicsSystem().getPipeline(), null, subLevel, fixedConstraint);
            if (pathConstraintHandle == null) {
                pathConstraintWorldAnchor = null;
                return false;
            }
            pathConstraintWorldAnchor = worldAnchor;
            return true;
        } catch (Exception ignored) {
            clearPathConstraint();
            return false;
        }
    }

    // Check if this needs pose correction
    private boolean needsPoseCorrection(Object subLevel, Vector3dc targetPosition) {
        Vector3d currentPosition = getSubLevelPosition(subLevel);
        return currentPosition == null
                || !isFiniteAndSafe(currentPosition.x)
                || !isFiniteAndSafe(currentPosition.y)
                || !isFiniteAndSafe(currentPosition.z)
                || currentPosition.distanceSquared(targetPosition.x(), targetPosition.y(), targetPosition.z()) > 16.0D;
    }

    // Get the sublevel position
    private @Nullable Vector3d getSubLevelPosition(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? new Vector3d(sableSubLevel.logicalPose().position())
                : null;
    }

    // Clear the path constraint
    private void clearPathConstraint() {
        if (pathConstraintHandle == null) {
            pathConstraintWorldAnchor = null;
            return;
        }
        SableConstraintApi.remove(pathConstraintHandle);
        pathConstraintHandle = null;
        pathConstraintWorldAnchor = null;
    }

    // Get the pose pos for zipline center
    private Vector3d getPosePosForZiplineCenter(Vector3dc desiredZiplineCenter, Vector3dc rotationPoint, Quaterniond orientation) {
        Vector3d localBlockCenter = new Vector3d(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
        Vector3d localOffset = localBlockCenter.sub(rotationPoint, new Vector3d());
        orientation.transform(localOffset);
        return new Vector3d(desiredZiplineCenter).sub(localOffset);
    }

    // Get the sublevel rotation point
    private @Nullable Vector3dc getSubLevelRotationPoint(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? sableSubLevel.logicalPose().rotationPoint()
                : null;
    }

    // Get the kinematic rotation point
    private Vector3dc getKinematicRotationPoint() {
        if (massTracker == null) {
            rebuildKinematicData();
        }
        Vector3dc centerOfMass = massTracker == null ? null : massTracker.getCenterOfMass();
        return centerOfMass == null
                ? new Vector3d(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D)
                : centerOfMass;
    }

    // Check if this is finite and safe
    private boolean isFiniteAndSafe(double val) {
        return Double.isFinite(val) && Math.abs(val) <= 30000000.0D;
    }

    // Check if this is finite quaternion
    private boolean isFiniteQuaternion(Quaterniond orientation) {
        return Double.isFinite(orientation.x)
                && Double.isFinite(orientation.y)
                && Double.isFinite(orientation.z)
                && Double.isFinite(orientation.w);
    }

    // Teleport the sublevel
    private void teleportSubLevel(Object subLevel, Vector3dc pos, Quaterniond orientation) {
        if (level == null || !(subLevel instanceof ServerSubLevel serverSubLevel)
                || pos == null || orientation == null) {
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics != null) {
            physics.getPipeline().teleport(serverSubLevel, pos, orientation);
        }
    }

    // Reset the sublevel velocity
    private void resetSubLevelVelocity(Object subLevel) {
        if (level == null || !(subLevel instanceof ServerSubLevel serverSubLevel)) {
            return;
        }
        SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(level);
        if (physics != null) {
            physics.getPipeline().resetVelocity(serverSubLevel);
        }
    }

    // Register the kinematic if possible
    private void registerKinematicIfPossible() {
        if (kinematicRegistered || level == null || level.isClientSide()) {
            return;
        }
        Object subLevel = getSableContaining();
        ServerLevel serverLevel = resolveServerLevel(level);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverLevel == null) {
            return;
        }
        if (serverSubLevel.getPlot() instanceof ServerLevelPlot plot) {
            plot.addContraption(this);
            SubLevelPhysicsSystem.require(serverLevel).getPipeline().add(this);
            kinematicRegistered = true;
        }
    }

    // Remove the kinematic
    private void unregisterKinematic() {
        if (!kinematicRegistered || level == null || level.isClientSide()) {
            return;
        }
        Object subLevel = getSableContaining();
        ServerLevel serverLevel = resolveServerLevel(level);
        if (subLevel instanceof ServerSubLevel serverSubLevel
                && serverSubLevel.getPlot() instanceof ServerLevelPlot plot) {
            plot.removeContraption(this);
        }
        if (serverLevel != null) {
            SubLevelPhysicsSystem.require(serverLevel).getPipeline().remove(this);
        }
        kinematicRegistered = false;
    }

    // Rebuild the kinematic data
    private void rebuildKinematicData() {
        if (level == null) {
            return;
        }
        localBounds = new BoundingBox3i(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        massTracker = MassTracker.build(level, localBounds);
    }

    // Begin the assembly transfer
    public void beginAssemblyTransfer() {
        assemblyTransferInProgress = true;
        unregisterKinematic();
    }

    // End the assembly transfer
    public void endAssemblyTransfer() {
        assemblyTransferInProgress = false;
        rebuildKinematicData();
    }

    // Get the behavior
    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeHolder;
    }

    // Get the rope holder
    public RopeStrandHolderBehavior getRopeHolder() {
        return ropeHolder;
    }

    // Get the attachment point
    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        if (state.hasProperty(PoweredZiplineBlock.FACING) && state.getValue(PoweredZiplineBlock.FACING) == Direction.DOWN) {
            return pos.getCenter().add(0.0, -0.0625, 0.0);
        }
        return pos.getCenter().add(0.0, -0.5, 0.0);
    }

    // Handle the local bounds
    @Override
    public void sable$getLocalBounds(BoundingBox3i bounds) {
        if (localBounds == null) {
            rebuildKinematicData();
        }
        bounds.set((BoundingBox3ic) localBounds);
    }

    // Get the block getter
    @Override
    public BlockGetter sable$blockGetter() {
        return level;
    }

    // Get the mass tracker
    @Override
    public MassTracker sable$getMassTracker() {
        if (massTracker == null) {
            rebuildKinematicData();
        }
        return massTracker;
    }

    // Get the position
    @Override
    public Vector3dc sable$getPosition(double partialTick) {
        Quaterniond orientation = sable$getOrientation(partialTick);
        return getPosePosForZiplineCenter(getDesiredZiplineCenter(partialTick), getKinematicRotationPoint(), orientation);
    }

    // Get the orientation
    @Override
    public Quaterniond sable$getOrientation(double partialTick) {

        Direction facing = getBlockState().hasProperty(PoweredZiplineBlock.FACING)
                ? getBlockState().getValue(PoweredZiplineBlock.FACING)
                : Direction.DOWN;
        return switch (facing) {
            case DOWN -> new Quaterniond();
            case UP -> new Quaterniond().rotateX(Math.PI);
            case NORTH -> new Quaterniond().rotateX(-Math.PI / 2.0D);
            case SOUTH -> new Quaterniond().rotateX(Math.PI / 2.0D);
            case EAST -> new Quaterniond().rotateZ(-Math.PI / 2.0D);
            case WEST -> new Quaterniond().rotateZ(Math.PI / 2.0D);
        };
    }

    // Get the lift providers
    @Override
    public Map<BlockPos, BlockSubLevelLiftProvider.LiftProviderContext> sable$liftProviders() {
        return liftProviders;
    }

    // Get the floating cluster container
    @Override
    public FloatingClusterContainer sable$getFloatingClusterContainer() {
        return floatingClusterContainer;
    }

    // Check if this should collide
    @Override
    public boolean sable$shouldCollide() {
        return false;
    }

    // Check if this is valid
    @Override
    public boolean sable$isValid() {
        return !isRemoved();
    }

    // Get the forward frequency first
    public ItemStack getForwardFrequencyFirst() {
        return forwardBinding.first();
    }

    // Get the forward frequency second
    public ItemStack getForwardFrequencySecond() {
        return forwardBinding.second();
    }

    // Get the backward frequency first
    public ItemStack getBackwardFrequencyFirst() {
        return backwardBinding.first();
    }

    // Get the backward frequency second
    public ItemStack getBackwardFrequencySecond() {
        return backwardBinding.second();
    }

    // Set the forward frequency
    public void setForwardFrequency(ItemStack first, ItemStack second) {
        forwardBinding.set(first, second);
        setChanged();
        sendData();
    }

    // Set the backward frequency
    public void setBackwardFrequency(ItemStack first, ItemStack second) {
        backwardBinding.set(first, second);
        setChanged();
        sendData();
    }

    // Check if this is follow chain
    public boolean isFollowChain() {
        return followChain;
    }

    // Set the follow chain
    public void setFollowChain(boolean followChain) {
        this.followChain = followChain;
        setChanged();
        sendData();
    }

    // Get the configured max speed
    public float getConfiguredMaxSpeed() {
        return Mth.clamp(configuredMaxSpeed, MIN_CONFIGURED_MAX_SPEED, MAX_CONFIGURED_MAX_SPEED);
    }

    // Get the configured damping
    public float getConfiguredDamping() {
        float fallback = (float) Mth.clamp(CTConfigs.COMMON.poweredZiplineRopeAttachmentInertiaDamping.get(), 0.0D, 1.0D);
        return Mth.clamp(Float.isNaN(configuredDamping) ? fallback : configuredDamping,
                MIN_CONFIGURED_DAMPING, MAX_CONFIGURED_DAMPING);
    }

    // Set the motion configuration
    public void setMotionConfiguration(float maxSpeed, float damping) {
        configuredMaxSpeed = Mth.clamp(maxSpeed, MIN_CONFIGURED_MAX_SPEED, MAX_CONFIGURED_MAX_SPEED);
        configuredDamping = Mth.clamp(damping, MIN_CONFIGURED_DAMPING, MAX_CONFIGURED_DAMPING);
        setChanged();
        sendData();
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PoweredZiplineMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.powered_zipline.config.title");
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader.encode(
                buffer, worldPosition, SimulatedHelper.getContainingSubLevelId(this));
        buffer.writeBoolean(followChain);
        buffer.writeFloat(getConfiguredMaxSpeed());
        buffer.writeFloat(getConfiguredDamping());
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0);
    }

    // Get the path position
    public float getPathPosition(float partialTick) {
        if (chainHandoff != null) return Mth.lerp(partialTick, chainHandoff.previous, chainHandoff.progress);
        if (ropeHandoff != null) return Mth.lerp(partialTick, ropeHandoff.previous, ropeHandoff.progress);
        return Mth.lerp(partialTick, prevRopePosition, ropePosition);
    }

    // Get the path length
    public float getPathLength() {
        if (chainHandoff != null) return chainHandoff.length;
        if (ropeHandoff != null) return ropeHandoff.length;
        return ropeLength;
    }

    // Get the attached chain pos
    public @Nullable BlockPos getAttachedChainPos() {
        return attachedChainPos;
    }

    // Get the attached chain connection
    public @Nullable BlockPos getAttachedChainConnection() {
        return attachedChainConnection;
    }

    // Get the attached rope UUID
    public @Nullable UUID getAttachedRopeUUID() {
        return attachedRopeUUID;
    }

    // Write the powered zipline safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.putBoolean("FollowChain", followChain);
        tag.putFloat("ConfiguredMaxSpeed", getConfiguredMaxSpeed());
        tag.putFloat("ConfiguredDamping", getConfiguredDamping());
        tag.put("ForwardBinding", forwardBinding.toTag(provider));
        tag.put("BackwardBinding", backwardBinding.toTag(provider));
    }

    // Write the powered zipline
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putFloat("RopePosition", ropePosition);
        tag.putFloat("PrevRopePosition", prevRopePosition);
        tag.putFloat("RopeLength", ropeLength);
        tag.putInt("RopeTravelDirection", ropeTravelDirection);
        tag.putInt("ChainTravelDirection", chainTravelDirection);
        tag.putBoolean("ChainSourceReversed", chainSourceReversed);
        tag.putBoolean("FollowChain", followChain);
        tag.putFloat("ConfiguredMaxSpeed", getConfiguredMaxSpeed());
        tag.putFloat("ConfiguredDamping", getConfiguredDamping());
        tag.put("ForwardBinding", forwardBinding.toTag(provider));
        tag.put("BackwardBinding", backwardBinding.toTag(provider));
        if (attachedChainPos != null) {
            tag.put("AttachedChainPos", NbtUtils.writeBlockPos(attachedChainPos));
        }
        if (attachedChainConnection != null) {
            tag.put("AttachedChainConnection", NbtUtils.writeBlockPos(attachedChainConnection));
        }
        if (attachedRopeUUID != null) {
            tag.putUUID("AttachedRopeUUID", attachedRopeUUID);
        }
        if (ropeHandoff != null) {
            CompoundTag handoff = new CompoundTag();
            handoff.putUUID("Target", ropeHandoff.target);
            handoff.putBoolean("EntryAtStart", ropeHandoff.entryAtStart);
            handoff.putBoolean("ExitAtEnd", ropeHandoff.exitAtEnd);
            handoff.putInt("ControlSign", ropeHandoff.controlSign);
            handoff.putFloat("Progress", ropeHandoff.progress);
            handoff.putFloat("Previous", ropeHandoff.previous);
            writeHandoffPoint(handoff, "Start", ropeHandoff.spline.start());
            writeHandoffPoint(handoff, "StartTangent", ropeHandoff.spline.startTangent());
            writeHandoffPoint(handoff, "End", ropeHandoff.spline.end());
            writeHandoffPoint(handoff, "EndTangent", ropeHandoff.spline.endTangent());
            tag.put("RopeHandoff", handoff);
        }
        if (chainHandoff != null) {
            CompoundTag handoff = new CompoundTag();
            handoff.put("SourcePos", NbtUtils.writeBlockPos(chainHandoff.sourcePos));
            if (chainHandoff.sourceConnection != null)
                handoff.put("SourceConnection", NbtUtils.writeBlockPos(chainHandoff.sourceConnection));
            handoff.putFloat("SourcePosition", chainHandoff.sourcePosition);
            handoff.put("TargetPos", NbtUtils.writeBlockPos(chainHandoff.targetPos));
            if (chainHandoff.targetConnection != null)
                handoff.put("TargetConnection", NbtUtils.writeBlockPos(chainHandoff.targetConnection));
            handoff.putFloat("TargetPosition", chainHandoff.targetPosition);
            handoff.putInt("SourceDirection", chainHandoff.sourceDirection);
            handoff.putInt("TargetDirection", chainHandoff.targetDirection);
            handoff.putInt("ControlSign", chainHandoff.controlSign);
            handoff.putFloat("Progress", chainHandoff.progress);
            handoff.putFloat("Previous", chainHandoff.previous);
            writeHandoffPoint(handoff, "Start", chainHandoff.spline.start());
            writeHandoffPoint(handoff, "StartTangent", chainHandoff.spline.startTangent());
            writeHandoffPoint(handoff, "End", chainHandoff.spline.end());
            writeHandoffPoint(handoff, "EndTangent", chainHandoff.spline.endTangent());
            tag.put("ChainHandoff", handoff);
        }
        writeRopeCarrierAttachment(tag, "AttachedRopeStart", attachedRopeStart);
        writeRopeCarrierAttachment(tag, "AttachedRopeEnd", attachedRopeEnd);
        if (attachedSubLevelId != null) {
            tag.putUUID("AttachedSubLevelId", attachedSubLevelId);
        }
        if (ridingPlayerUUID != null) {
            tag.putUUID("RidingPlayerUUID", ridingPlayerUUID);
        }
        if (ridingEntityUUID != null) tag.putUUID("RidingEntityUUID", ridingEntityUUID);
        if (!extraHangingRopeHolders.isEmpty()) {
            CompoundTag holderTags = new CompoundTag();
            int idx = 0;
            for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
                CompoundTag holderTag = new CompoundTag();
                holder.write(holderTag, provider, clientPacket);
                holderTags.put("Holder" + idx, holderTag);
                idx++;
            }
            tag.putInt("ExtraHangingRopeHolderCount", idx);
            tag.put("ExtraHangingRopeHolders", holderTags);
        }
        if (clientPacket) {
            writeActiveHangingRopeIds(tag);
        }
        remapSchematicReferences(tag, false);
    }

    // Read the powered zipline
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        remapSchematicReferences(tag, true);
        super.read(tag, provider, clientPacket);
        ropePosition = tag.getFloat("RopePosition");
        prevRopePosition = tag.contains("PrevRopePosition") ? tag.getFloat("PrevRopePosition") : ropePosition;
        ropeLength = tag.contains("RopeLength") ? tag.getFloat("RopeLength") : 1.0f;
        ropeTravelDirection = tag.getInt("RopeTravelDirection") < 0 ? -1 : 1;
        chainTravelDirection = tag.getInt("ChainTravelDirection") < 0 ? -1 : 1;
        chainSourceReversed = tag.getBoolean("ChainSourceReversed");
        chainTravelFrameInitialized = tag.contains("ChainTravelDirection") && tag.contains("ChainSourceReversed");
        followChain = tag.getBoolean("FollowChain");
        configuredMaxSpeed = tag.contains("ConfiguredMaxSpeed") ? tag.getFloat("ConfiguredMaxSpeed") : DEFAULT_MAX_SPEED;
        configuredDamping = tag.contains("ConfiguredDamping") ? tag.getFloat("ConfiguredDamping") : Float.NaN;
        if (tag.contains("ForwardBinding")) {
            forwardBinding.read(tag.getCompound("ForwardBinding"), provider);
        }
        if (tag.contains("BackwardBinding")) {
            backwardBinding.read(tag.getCompound("BackwardBinding"), provider);
        }
        attachedChainPos = tag.contains("AttachedChainPos") ? NbtUtils.readBlockPos(tag, "AttachedChainPos").orElse(null) : null;
        attachedChainConnection = tag.contains("AttachedChainConnection") ? NbtUtils.readBlockPos(tag, "AttachedChainConnection").orElse(null) : null;
        attachedRopeUUID = tag.contains("AttachedRopeUUID") ? tag.getUUID("AttachedRopeUUID") : null;
        ropeHandoff = null;
        if (attachedRopeUUID != null && tag.contains("RopeHandoff", Tag.TAG_COMPOUND)) {
            CompoundTag handoff = tag.getCompound("RopeHandoff");
            if (handoff.hasUUID("Target")) {
                ZiplineHandoffSpline spline = new ZiplineHandoffSpline(
                        readHandoffPoint(handoff, "Start"), readHandoffPoint(handoff, "StartTangent"),
                        readHandoffPoint(handoff, "End"), readHandoffPoint(handoff, "EndTangent"));
                ropeHandoff = new RopeHandoff(handoff.getUUID("Target"),
                        handoff.getBoolean("EntryAtStart"), handoff.getBoolean("ExitAtEnd"),
                        handoff.getInt("ControlSign") < 0 ? -1 : 1, spline, handoff.getFloat("Progress"));
                ropeHandoff.previous = handoff.contains("Previous")
                        ? handoff.getFloat("Previous") : ropeHandoff.progress;
            }
        }
        chainHandoff = null;
        if (attachedChainPos != null && tag.contains("ChainHandoff", Tag.TAG_COMPOUND)) {
            CompoundTag handoff = tag.getCompound("ChainHandoff");
            BlockPos source = NbtUtils.readBlockPos(handoff, "SourcePos").orElse(null);
            BlockPos target = NbtUtils.readBlockPos(handoff, "TargetPos").orElse(null);
            if (source != null && target != null) {
                ZiplineHandoffSpline spline = new ZiplineHandoffSpline(
                        readHandoffPoint(handoff, "Start"), readHandoffPoint(handoff, "StartTangent"),
                        readHandoffPoint(handoff, "End"), readHandoffPoint(handoff, "EndTangent"));
                BlockPos sourceConnection = NbtUtils.readBlockPos(handoff, "SourceConnection").orElse(null);
                BlockPos targetConnection = NbtUtils.readBlockPos(handoff, "TargetConnection").orElse(null);
                chainHandoff = new ChainHandoff(source, sourceConnection, handoff.getFloat("SourcePosition"),
                        target, targetConnection, handoff.getFloat("TargetPosition"),
                        handoff.getInt("SourceDirection") < 0 ? -1 : 1,
                        handoff.getInt("TargetDirection") < 0 ? -1 : 1,
                        handoff.getInt("ControlSign") < 0 ? -1 : 1,
                        spline, handoff.getFloat("Progress"));
                chainHandoff.previous = handoff.contains("Previous")
                        ? handoff.getFloat("Previous") : chainHandoff.progress;
            }
        }
        attachedRopeStart = readRopeCarrierAttachment(tag, "AttachedRopeStart");
        attachedRopeEnd = readRopeCarrierAttachment(tag, "AttachedRopeEnd");
        ridingEntityUUID = tag.hasUUID("RidingEntityUUID") ? tag.getUUID("RidingEntityUUID") : null;
        if (attachedRopeUUID == null) {
            clearRopeCarrierAttachments();
        }
        attachedSubLevelId = tag.contains("AttachedSubLevelId") ? tag.getUUID("AttachedSubLevelId") : null;
        ridingPlayerUUID = tag.contains("RidingPlayerUUID") ? tag.getUUID("RidingPlayerUUID") : null;
        hangingRopeAttachmentValidations.clear();
        extraHangingRopeHolders.clear();
        if (tag.contains("ExtraHangingRopeHolders")) {
            CompoundTag holderTags = tag.getCompound("ExtraHangingRopeHolders");
            int count = tag.getInt("ExtraHangingRopeHolderCount");
            for (int i = 0; i < count; i++) {
                CompoundTag holderTag = holderTags.getCompound("Holder" + i);
                RopeStrandHolderBehavior holder = new RopeStrandHolderBehavior(this);
                holder.read(holderTag, provider, clientPacket);
                extraHangingRopeHolders.add(holder);
            }
        }
        if (!clientPacket) {
            pruneUnusedHangingRopeHolders();
        } else if (tag.contains("ActiveHangingRopeUUIDs")) {
            pruneClientHangingRopeCache(readActiveHangingRopeIds(tag));
        }
        rebuildKinematicData();
    }

    // Remap the schematic references
    private void remapSchematicReferences(CompoundTag tag, boolean reading) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null) return;
        SchematicSubLevelReferenceRemapper.remapInPlace(tag);

        if (!reading) {
            UUID ownerId = SimulatedHelper.getContainingSubLevelId(this);
            if (ownerId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping ownerMapping = ctx.getMapping(ownerId);
                if (ownerMapping != null) {
                    tag.putUUID("SchematicOwnerSubLevelId", ownerMapping.newUUID());
                    remapBlockPosTag(tag, "AttachedChainPos", ownerMapping);
                }
            }
        } else if (tag.hasUUID("SchematicOwnerSubLevelId")) {
            SubLevelSchematicSerializationContext.SchematicMapping ownerMapping =
                    ctx.getMapping(tag.getUUID("SchematicOwnerSubLevelId"));
            if (ownerMapping != null) remapBlockPosTag(tag, "AttachedChainPos", ownerMapping);
            tag.remove("SchematicOwnerSubLevelId");
        }

        if (tag.hasUUID("AttachedSubLevelId")) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping =
                    ctx.getMapping(tag.getUUID("AttachedSubLevelId"));
            if (mapping == null) tag.remove("AttachedSubLevelId");
            else tag.putUUID("AttachedSubLevelId", mapping.newUUID());
        }
    }

    // Remap the block pos tag
    private static void remapBlockPosTag(CompoundTag tag, String key,
                                         SubLevelSchematicSerializationContext.SchematicMapping mapping) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) return;
        BlockPos pos = NbtUtils.readBlockPos(tag, key).orElse(null);
        if (pos != null) tag.put(key, NbtUtils.writeBlockPos(mapping.transform().apply(pos)));
    }

    // Get the connection dependencies
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null) return List.of();
        Set<UUID> ids = new HashSet<>();
        if (attachedSubLevelId != null) ids.add(attachedSubLevelId);
        if (attachedRopeStart != null && attachedRopeStart.subLevelId() != null) {
            ids.add(attachedRopeStart.subLevelId());
        }
        if (attachedRopeEnd != null && attachedRopeEnd.subLevelId() != null) {
            ids.add(attachedRopeEnd.subLevelId());
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        List<SubLevel> dependencies = new ArrayList<>();
        for (UUID id : ids) {
            SubLevel subLevel = container.getSubLevel(id);
            if (subLevel != null && !subLevel.isRemoved()) dependencies.add(subLevel);
        }
        return dependencies;
    }

    // Write the rope carrier attachment
    private static void writeRopeCarrierAttachment(CompoundTag owner, String key,
                                                   @Nullable RopeCarrierAttachment attachment) {
        if (attachment == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("BlockPos", attachment.blockPos().asLong());
        if (attachment.subLevelId() != null) {
            tag.putUUID("SubLevelId", attachment.subLevelId());
        }
        owner.put(key, tag);
    }

    // Read the rope carrier attachment
    private static @Nullable RopeCarrierAttachment readRopeCarrierAttachment(CompoundTag owner, String key) {
        if (!owner.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = owner.getCompound(key);
        if (!tag.contains("BlockPos", Tag.TAG_LONG)) {
            return null;
        }
        UUID subLevelId = tag.hasUUID("SubLevelId") ? tag.getUUID("SubLevelId") : null;
        return new RopeCarrierAttachment(subLevelId, BlockPos.of(tag.getLong("BlockPos")));
    }

    // Write the active hanging rope ids
    private void writeActiveHangingRopeIds(CompoundTag tag) {
        ListTag ropes = new ListTag();
        addActiveHangingRopeId(ropes, ropeHolder);
        for (RopeStrandHolderBehavior holder : extraHangingRopeHolders) {
            addActiveHangingRopeId(ropes, holder);
        }
        tag.put("ActiveHangingRopeUUIDs", ropes);
    }

    // Add the active hanging rope id
    private void addActiveHangingRopeId(ListTag ropes, @Nullable RopeStrandHolderBehavior holder) {
        ServerRopeStrand strand = holder == null ? null : holder.getOwnedStrand();
        if (strand != null) {
            CompoundTag rope = new CompoundTag();
            rope.putUUID("UUID", strand.getUUID());
            ropes.add(rope);
        }
    }

    // Read the active hanging rope ids
    private Set<UUID> readActiveHangingRopeIds(CompoundTag tag) {
        Set<UUID> active = new HashSet<>();
        ListTag ropes = tag.getList("ActiveHangingRopeUUIDs", Tag.TAG_COMPOUND);
        for (int i = 0; i < ropes.size(); i++) {
            CompoundTag rope = ropes.getCompound(i);
            if (rope.hasUUID("UUID")) {
                active.add(rope.getUUID("UUID"));
            }
        }
        return active;
    }

    // Prune the client hanging rope cache
    private void pruneClientHangingRopeCache(Set<UUID> activeRopes) {
        if (level == null || ropeHolder == null || !(ropeHolder instanceof PoweredZiplineClientRopeCache cache)) {
            return;
        }
        cache.createthrusters$retainZiplineClientStrands(activeRopes, level);
    }

    // Store the path sample
    private record PathSample(float along, float length, double distanceSq) {
    }

    // Store the rope carrier attachment
    private record RopeCarrierAttachment(@Nullable UUID subLevelId, BlockPos blockPos) {
        // Initialize the rope carrier attachment
        private RopeCarrierAttachment {
            blockPos = blockPos.immutable();
        }

        // Create the rope carrier attachment
        private static @Nullable RopeCarrierAttachment from(@Nullable RopeAttachment attachment) {
            if (attachment == null || attachment.blockAttachment() == null) {
                return null;
            }
            return new RopeCarrierAttachment(attachment.subLevelID(), attachment.blockAttachment());
        }
    }

    // Get the sublevel orientation
    private @Nullable Quaterniond getSubLevelOrientation(Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel
                ? new Quaterniond(sableSubLevel.logicalPose().orientation())
                : null;
    }

    // Store the hanging rope attachment validation
    private record HangingRopeAttachmentValidation(@Nullable UUID endSubLevelId,
                                                   BlockPos endBlockAttachment,
                                                   long nextCheckTick) {
        // Initialize the hanging rope attachment validation
        private HangingRopeAttachmentValidation {
            endBlockAttachment = endBlockAttachment.immutable();
        }

        // Check if this matches the value
        private boolean matches(RopeAttachment attachment) {
            UUID attachmentSubLevelId = attachment.subLevelID();
            return (endSubLevelId == null ? attachmentSubLevelId == null : endSubLevelId.equals(attachmentSubLevelId))
                    && endBlockAttachment.equals(attachment.blockAttachment());
        }
    }

    // Define the hanging rope attachment status values
    private enum HangingRopeAttachmentStatus {
        VALID,
        DEFERRED,
        BROKEN
    }

    // Define the path carrier status values
    private enum PathCarrierStatus {
        VALID,
        DEFERRED,
        BROKEN
    }

    // Handle the signal inertia
    private class SignalInertia {
        // Tracks whether signal inertia is initialized
        private boolean initialized;
        // Tracks whether signal inertia is ramping
        private boolean ramping;
        // Last raw
        private int lastRaw;
        // Current signal inertia target
        private int target;
        // Current signal inertia value
        private float value;

        // Update the signal inertia
        private float update(int rawSignal) {
            int raw = Mth.clamp(rawSignal, 0, 15);
            if (!initialized) {
                initialized = true;
                lastRaw = raw;
                target = raw;
                value = raw;
                return value;
            }

            if (raw != target) {
                int change = Math.abs(raw - lastRaw);
                target = raw;
                if (change >= SIGNAL_HARD_CHANGE_THRESHOLD) {
                    ramping = true;
                } else {
                    value = raw;
                    ramping = false;
                }
            }
            lastRaw = raw;

            if (ramping) {
                float diff = target - value;
                if (Math.abs(diff) <= SIGNAL_RAMP_PER_TICK) {
                    value = target;
                    ramping = false;
                } else {
                    value += Math.signum(diff) * SIGNAL_RAMP_PER_TICK;
                }
            }
            return value;
        }
    }

    // Handle the zipline link receiver
    private class ZiplineLinkReceiver implements IRedstoneLinkable {
        // Binding
        private final FrequencyBinding binding;

        // Initialize the zipline link receiver
        private ZiplineLinkReceiver(FrequencyBinding binding) {
            this.binding = binding;
        }

        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int networkPower) {
        }

        // Check if this is listening
        @Override
        public boolean isListening() {
            return false;
        }

        // Check if this is alive
        @Override
        public boolean isAlive() {
            return level != null && !isRemoved();
        }

        // Get the network key
        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            if (!binding.isBound()) {
                return Couple.create(RedstoneLinkNetworkHandler.Frequency.EMPTY, RedstoneLinkNetworkHandler.Frequency.EMPTY);
            }
            return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                    RedstoneLinkNetworkHandler.Frequency.of(binding.second()));
        }

        // Get the location
        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }
}
