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
    // Current manual forward signal
    private int manualForwardSignal;
    // Current manual backward signal
    private int manualBackwardSignal;
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
                protectRidingPlayerFromFall();
                return;
            }
        } else {
            clearPathConstraint();
            return;
        }

        tickAssembledSubLevelPose();
        float forward = Math.max(forwardSignalInertia.update(queryWirelessSignal(forwardBinding, forwardReceiver)), manualForwardSignal);
        float backward = Math.max(backwardSignalInertia.update(queryWirelessSignal(backwardBinding, backwardReceiver)), manualBackwardSignal);
        manualForwardSignal = 0;
        manualBackwardSignal = 0;
        float delta = applyHangingRopeInertiaDamping(((forward - backward) / 15.0f) * getConfiguredMaxSpeed());
        if (delta != 0.0f) {
            advancePath(delta);
        }

        protectRidingPlayerFromFall();
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
        return false;
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
        attachedRopeUUID = null;
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
        ropeLength = attachedChainConnection == null ? 360.0f : best.connectionStats.get(attachedChainConnection).chainLength();
        ropePosition = Mth.clamp(ropePosition, 0.0f, ropeLength);
        prevRopePosition = ropePosition;
        return true;
    }

    // Get the path carrier status
    private PathCarrierStatus getPathCarrierStatus() {
        if (attachedChainPos != null) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos);
            if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
                return PathCarrierStatus.BROKEN;
            }
            chain.prepareStats();
            if (attachedChainConnection != null) {
                if (!chain.connectionStats.containsKey(attachedChainConnection)) {
                    return PathCarrierStatus.BROKEN;
                }
                BlockEntity target = SimulatedHelper.findBlockEntityIncludingSubLevels(level, attachedChainPos.offset(attachedChainConnection));
                return target instanceof ChainConveyorBlockEntity
                        ? PathCarrierStatus.VALID
                        : PathCarrierStatus.BROKEN;
            }
            return PathCarrierStatus.VALID;
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

    // Apply the manual input
    public void applyManualInput(boolean forward) {
        if (forward) {
            manualForwardSignal = 15;
        } else {
            manualBackwardSignal = 15;
        }
    }

    // Set the riding player
    public void setRidingPlayer(@Nullable UUID playerUUID) {
        ridingPlayerUUID = playerUUID;
        setChanged();
        sendData();
    }

    // Advance the path
    private void advancePath(float delta) {
        if (attachedChainPos != null) {
            advanceChain(delta);
            return;
        }
        setPathPositionClamped(ropePosition + delta);
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
        if (!(blockEntity instanceof ChainConveyorBlockEntity chain)) {
            setPathPositionClamped(ropePosition + delta);
            return;
        }
        chain.prepareStats();
        if (attachedChainConnection != null) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(attachedChainConnection);
            if (stats == null) {
                attachedChainConnection = null;
                ropeLength = 360.0f;
                setPathPositionClamped(chain.wrapAngle(ropePosition));
                return;
            }
            float next = ropePosition + delta;
            if (next > stats.chainLength()) {
                BlockPos nextChainPos = attachedChainPos.offset(attachedChainConnection);
                BlockEntity nextBlockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, nextChainPos);
                if (nextBlockEntity instanceof ChainConveyorBlockEntity nextChain) {
                    nextChain.prepareStats();
                    attachedChainPos = nextChain.getBlockPos().immutable();
                    attachedChainConnection = null;
                    ropeLength = 360.0f;
                    ropePosition = nextChain.wrapAngle(stats.tangentAngle() + 180.0f + (float) (70 * (chain.reversed ? -1 : 1)));
                    prevRopePosition = ropePosition;
                    setChanged();
                    sendData();
                    return;
                }
            }
            if (next < 0.0f) {
                attachedChainConnection = null;
                ropeLength = 360.0f;
                ropePosition = chain.wrapAngle(stats.tangentAngle());
                prevRopePosition = ropePosition;
                setChanged();
                sendData();
                return;
            }
            setPathPositionClamped(next);
            return;
        }

        float prev = ropePosition;
        float next = chain.wrapAngle(prev + delta);
        if (followChain) {
            BlockPos connection = findCrossedConnection(chain, prev, next, delta > 0.0f);
            if (connection != null) {
                ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(connection);
                if (stats != null) {
                    attachedChainConnection = connection.immutable();
                    ropeLength = Math.max(0.01f, stats.chainLength());
                    ropePosition = delta > 0.0f ? 0.0f : ropeLength;
                    prevRopePosition = ropePosition;
                    setChanged();
                    sendData();
                    return;
                }
            }
        }
        ropeLength = 360.0f;
        ropePosition = next;
        setChanged();
        sendData();
    }

    // Find the crossed connection
    private @Nullable BlockPos findCrossedConnection(ChainConveyorBlockEntity chain, float prev, float next, boolean forward) {
        for (BlockPos connection : chain.connections) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(connection);
            if (stats == null) {
                continue;
            }
            if (crossedAngle(prev, next, stats.tangentAngle(), forward)) {
                return connection;
            }
        }
        return null;
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
        double t = Mth.lerp(partialTick, prevRopePosition, ropePosition);
        Vec3 sample = getSplineWorldPosition((float) t);
        return new Vector3d(sample.x, sample.y, sample.z);
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
        chain.prepareStats();
        if (attachedChainConnection != null) {
            ChainConveyorBlockEntity.ConnectionStats stats = chain.connectionStats.get(attachedChainConnection);
            if (stats != null) {
                Vec3 diff = stats.end().subtract(stats.start()).normalize();
                return stats.start().add(diff.scale(Math.min(stats.chainLength(), pos)));
            }
        }
        return Vec3.atBottomCenterOf(attachedChainPos).add(VecHelper.rotate(new Vec3(0.0, 0.25, 1.0), pos, Direction.Axis.Y));
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
        float clampedPosition = Mth.clamp(pos, 0.0f, Math.max(0.01f, ropeLength));
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
        return Mth.lerp(partialTick, prevRopePosition, ropePosition);
    }

    // Get the path length
    public float getPathLength() {
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
        writeRopeCarrierAttachment(tag, "AttachedRopeStart", attachedRopeStart);
        writeRopeCarrierAttachment(tag, "AttachedRopeEnd", attachedRopeEnd);
        if (attachedSubLevelId != null) {
            tag.putUUID("AttachedSubLevelId", attachedSubLevelId);
        }
        if (ridingPlayerUUID != null) {
            tag.putUUID("RidingPlayerUUID", ridingPlayerUUID);
        }
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
        attachedRopeStart = readRopeCarrierAttachment(tag, "AttachedRopeStart");
        attachedRopeEnd = readRopeCarrierAttachment(tag, "AttachedRopeEnd");
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
