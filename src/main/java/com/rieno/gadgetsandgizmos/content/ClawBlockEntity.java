package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.physics.SableConstraintApi;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.control.FrequencyBinding;
import com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelConnectionApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Handle the rope claw, its target and the temporary physical connection it creates
public class ClawBlockEntity extends SmartBlockEntity implements RopeStrandHolderBlockEntity,
        IHaveGoggleInformation, IDirectControlReceiver, MenuProvider {

    // Store the connector reference
    public record ConnectorReference(BlockPos localPos, @Nullable UUID subLevelId, @Nullable Vec3 worldPos) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int HOLD_CHECK_INTERVAL = 20;
    private static final int GRAB_SCAN_RADIUS = 3;
    private static final double GRAB_SCAN_RADIUS_SQ = GRAB_SCAN_RADIUS * GRAB_SCAN_RADIUS;

    private static final int AUTO_ASSEMBLE_STABLE_TICKS = 8;

    private static final int AUTO_ASSEMBLE_RETRY_INTERVAL = 5;

    private static final double CONTACT_RAY_LENGTH = 0.0625;
    private static final double CONTACT_SAMPLE_OFFSET = 0.375;
    private static final double CONTACT_ANGLE_TOLERANCE_DEGREES = 30.0;

    private static final double APPROACH_MAX_FORCE = 140.0;
    private static final double APPROACH_LINEAR_STIFFNESS = 42.0;
    private static final double APPROACH_LINEAR_DAMPING = 24.0;
    private static final double APPROACH_ANGULAR_DAMPING = 5.0;

    private static final double MASS_REFERENCE = 10.0;
    private static final double MAX_MASS_FACTOR = 4.0;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current rope holder
    private RopeStrandHolderBehavior ropeHolder;

    // Current grab constraint handle
    @Nullable
    private PhysicsConstraintHandle grabConstraintHandle;

    // Current approach constraint handle
    @Nullable
    private PhysicsConstraintHandle approachConstraintHandle;

    // Pending connector target
    @Nullable
    private BlockEntity pendingConnectorTarget;

    // Current grab constraint pos1
    @Nullable
    private Vector3d grabConstraintPos1;

    // Current grab constraint pos2
    @Nullable
    private Vector3d grabConstraintPos2;

    // Current grab constraint orientation
    @Nullable
    private Quaterniond grabConstraintOrientation;

    // Current debug stage
    private String debugStage = "idle";
    // Current debug last error
    private String debugLastError = "none";
    // Current debug scan candidates
    private int debugScanCandidates = 0;
    // Current debug scan eligible
    private int debugScanEligible = 0;
    // Current debug best distance sq
    private double debugBestDistanceSq = -1.0;

    // Signal strength
    private int signalStrength = 0;

    // Current redstone signal
    private int redstoneSignal = 0;

    // Current wireless signal
    private int wirelessSignal = 0;

    // Current marker signal
    private int markerSignal = 0;

    // Tracks whether wireless latched is closed
    private boolean wirelessLatchedClosed = false;
    // Wireless latched strength
    private int wirelessLatchedStrength = 0;
    // Last wireless network power
    private int lastWirelessNetworkPower = 0;

    // Tracks whether wireless toggle is armed
    private boolean wirelessToggleArmed = true;

    // Current computer signal override
    private int computerSignalOverride = -1;

    // Receiver binding
    private final FrequencyBinding receiverBinding = new FrequencyBinding("claw_receiver");
    // Marker binding
    private final FrequencyBinding markerBinding = new FrequencyBinding("claw_marker");
    // Link receiver
    private final ClawLinkReceiver linkReceiver = new ClawLinkReceiver();
    // Tracks whether this is registered with link network
    private boolean registeredWithLinkNetwork;
    // Registered link network level
    @Nullable
    private net.minecraft.world.level.Level registeredLinkNetworkLevel;
    // Last link network location
    @Nullable
    private BlockPos lastLinkNetworkLocation;

    // Current grabbed connector pos
    @Nullable
    private BlockPos grabbedConnectorPos = null;

    // Current grabbed connector sub-level id
    @Nullable
    private UUID grabbedConnectorSubLevelId = null;

    // Current grabbed entity
    @Nullable
    private Entity grabbedEntity = null;

    // Current grabbed entity UUID
    @Nullable
    private UUID grabbedEntityUUID = null;

    // Current hold check timer
    private int holdCheckTimer = 0;

    // Claw angle
    public final LerpedFloat clawAngle = LerpedFloat.linear().chase(0.0f, 0.15f, LerpedFloat.Chaser.LINEAR);

    // Tracks whether this was rope attached last tick
    private boolean wasRopeAttachedLastTick = false;

    // Tracks whether assembly is pending
    private boolean pendingAssembly = false;

    // Rope attached stable tick count
    private int ropeAttachedStableTicks = 0;

    // Current auto assemble retry cooldown
    private int autoAssembleRetryCooldown = 0;

    // Tracks whether disassembly is pending
    private boolean pendingDisassembly = false;

    // Tracks whether assembly transfer is in progress
    private boolean assemblyTransferInProgress = false;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw
    public ClawBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.CLAW.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        ropeHolder = new RopeStrandHolderBehavior(this);
        behaviours.add(ropeHolder);
    }

    // Initialize the claw
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide()) {
            registerLinkReceiver();

            Direction baseFace = ClawBlock.getBaseDirection(getBlockState());
            int signal = 0;
            for (Direction d : Direction.values()) {
                if (d != baseFace) {

                    signal = Math.max(signal, level.getSignal(worldPosition.relative(d), d));
                }
            }
            updateSignal(signal);
        }
    }

    // Destroy the claw
    @Override
    public void destroy() {
        unregisterLinkReceiver();
        clearGrabConstraintHandle();
        clearApproachConstraint();
        if (assemblyTransferInProgress) {
            assemblyTransferInProgress = false;
            return;
        }
        super.destroy();
    }

    // Remove the claw
    @Override
    public void remove() {
        clearGrabConstraintHandle();
        clearApproachConstraint();
        super.remove();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the claw
    @Override
    public void tick() {
        super.tick();

        if (level != null && level.isClientSide()) {
            clawAngle.tickChaser();
            invalidateRenderBoundingBox();
            return;
        }

        clawAngle.tickChaser();

        // -----------------------------------------------------SIGNAL INPUTS-----------------------------------------------------
        syncLinkReceiverRegistration();

        syncWirelessSignalFromNetwork();

        markerSignal = 0;

        float angleTarget = signalStrength == 0 ? 0f : Math.min(signalStrength / 14.0f, 1.0f);
        clawAngle.updateChaseTarget(angleTarget);

        // -----------------------------------------------------ROPE STATE-----------------------------------------------------
        boolean ropeNowAttached = ropeHolder != null && ropeHolder.isAttached();
        if (ropeNowAttached) {
            ropeAttachedStableTicks++;
        } else {
            ropeAttachedStableTicks = 0;
            autoAssembleRetryCooldown = 0;
        }

        if (ropeNowAttached && !wasRopeAttachedLastTick) {

            pendingAssembly = true;
            pendingDisassembly = false;
        } else if (!ropeNowAttached && wasRopeAttachedLastTick) {

            pendingDisassembly = false;
        }
        wasRopeAttachedLastTick = ropeNowAttached;

        // ------------------------------------ASSEMBLY STATE------------------------------------
        if (pendingAssembly) {
            if (autoAssembleRetryCooldown > 0) {
                autoAssembleRetryCooldown--;
            }

            if (ropeAttachedStableTicks >= AUTO_ASSEMBLE_STABLE_TICKS && autoAssembleRetryCooldown <= 0) {

                boolean assembled = doAutoAssemble();
                pendingAssembly = !assembled && ropeNowAttached && getSableContaining() == null;
                if (pendingAssembly) {
                    autoAssembleRetryCooldown = AUTO_ASSEMBLE_RETRY_INTERVAL;
                }
            }
        }

        if (pendingDisassembly) {
            pendingDisassembly = false;
        }

        // -----------------------------------------------------GRAB TARGET-----------------------------------------------------
        if (signalStrength > 0 && grabbedConnectorPos == null && grabbedEntity == null) {
            if (pendingConnectorTarget != null) {
                processSelectedConnector();
            } else {
                scanForConnector();
            }
        }

        if (grabbedEntity != null && grabbedConnectorPos == null && pendingConnectorTarget == null) {
            processEntityApproachAndMaybeAttach();
        }

        // -----------------------------------------------------HELD TARGET-----------------------------------------------------
        if (grabbedConnectorPos != null
                && !isConstraintHandleValid(grabConstraintHandle)) {
            if (!tryRestoreGrabConstraint()) {
                if (isGrabTargetDefinitelyGone()) {
                    clearGrabbedConnectorState();
                    notifyUpdate();
                }
            }
        }

        if (grabbedEntityUUID != null && grabbedEntity == null) {
            if (!tryRestoreGrabbedEntity()) {
                grabbedEntityUUID = null;
                holdCheckTimer = 0;
                notifyUpdate();
            }
        }

        if (grabbedEntity != null && !grabbedEntity.isRemoved()) {
            tickHeldEntity();
        }

        if (grabbedConnectorPos != null && signalStrength >= 1 && signalStrength <= 14) {
            holdCheckTimer++;
            if (holdCheckTimer >= HOLD_CHECK_INTERVAL) {
                holdCheckTimer = 0;
                evaluateHold();
            }
        } else if (grabbedEntity != null && signalStrength >= 1 && signalStrength <= 14) {

            holdCheckTimer++;
            if (holdCheckTimer >= HOLD_CHECK_INTERVAL) {
                holdCheckTimer = 0;
                evaluateEntityHold();
            }
        } else {
            holdCheckTimer = 0;
        }
    }

    // Update the held entity
    private void tickHeldEntity() {
        if (grabbedEntity == null || grabbedEntity.isRemoved()) {
            releaseEntity();
            return;
        }

        if (grabbedEntity instanceof Player grabbedPlayer && grabbedPlayer.isShiftKeyDown()) {
            releaseEntity();
            return;
        }

        Vec3 vel = grabbedEntity.getDeltaMovement();
        grabbedEntity.setDeltaMovement(vel.scale(0.92));

        Object clawSubLevel = getSableContaining();
        Vec3 clawCenter = toWorldAnchor(clawSubLevel, worldPosition.getCenter());
        Vec3 entityWorldPos = getEntityWorldPosition(grabbedEntity);
        double distanceSq = clawCenter.distanceToSqr(entityWorldPos);
        if (distanceSq > 1.0) {
            Vec3 directionToClaw = clawCenter.subtract(entityWorldPos).normalize();
            Vec3 localDirectionToClaw = toEntityMotionFrameDir(grabbedEntity, directionToClaw);
            grabbedEntity.setDeltaMovement(grabbedEntity.getDeltaMovement()
                    .add(localDirectionToClaw.scale(0.1)));
        }
    }

    // Check if automatic assembly should run
    private boolean doAutoAssemble() {
        if (level == null) return false;
        Object currentSubLevel = getSableContaining();
        if (currentSubLevel != null && isSingleBlockContainingSubLevel(currentSubLevel)) {

            return true;
        }
        net.minecraft.server.level.ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null) return false;

        ServerSubLevel assembledSubLevel = SubLevelAssemblyApi.assembleSingle(serverLevel, worldPosition);
        if (assembledSubLevel == null) {
            return false;
        }

        ClawBlockEntity movedClaw = findMovedClaw(assembledSubLevel);
        if (movedClaw != null) {
            movedClaw.endAssemblyTransfer();
            movedClaw.pendingAssembly = false;
            movedClaw.autoAssembleRetryCooldown = 0;
            movedClaw.wasRopeAttachedLastTick = movedClaw.ropeHolder != null
                    && movedClaw.ropeHolder.isAttached();
            movedClaw.ropeAttachedStableTicks = movedClaw.wasRopeAttachedLastTick
                    ? AUTO_ASSEMBLE_STABLE_TICKS
                    : 0;
            movedClaw.setChanged();
            movedClaw.sendData();
        }
        return true;
    }

    // Find the moved claw
    private @Nullable ClawBlockEntity findMovedClaw(Object subLevel) {
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
            if (blockEntity instanceof ClawBlockEntity claw && !claw.isRemoved()) {
                return claw;
            }
        }
        return null;
    }

    // Check if this is a single block containing sublevel
    private boolean isSingleBlockContainingSubLevel(Object subLevel) {
        if (!(subLevel instanceof SubLevel sableSubLevel) || level == null) return false;
        int solidBlocks = 0;
        for (PlotChunkHolder chunk : sableSubLevel.getPlot().getLoadedChunks()) {
            BoundingBox3ic bounds = chunk.getBoundingBox();
            if (bounds == null) continue;
            int minBlockX = chunk.getPos().getMinBlockX();
            int minBlockZ = chunk.getPos().getMinBlockZ();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockPos pos = new BlockPos(minBlockX + x, y, minBlockZ + z);
                        if (level.getBlockState(pos).isAir()) continue;
                        solidBlocks++;
                        if (solidBlocks > 1) {
                            return false;
                        }
                    }
                }
            }
        }
        return solidBlocks == 1;
    }

    // Ensure the assembled for rope attach
    public boolean ensureAssembledForRopeAttach() {
        if (level == null || level.isClientSide()) {
            return false;
        }

        return doAutoAssemble();
    }

    // Automatically disassemble the claw
    private void doAutoDisassemble() {
        if (level == null) return;
        Object subLevel = getSableContaining();
        if (subLevel instanceof SubLevel sableSubLevel) {
            SubLevelAssemblyApi.disassemble(level, sableSubLevel, worldPosition, worldPosition, Rotation.NONE, true);
        }
    }

    // Force the toggle assembly
    public void forceToggleAssembly() {
        if (level == null || level.isClientSide()) return;
        if (getSableContaining() != null) {
            doAutoDisassemble();
        } else {
            doAutoAssemble();
        }
    }

    // Begin the assembly transfer
    public void beginAssemblyTransfer() {
        assemblyTransferInProgress = true;
        clearGrabConstraintHandle();
        clearApproachConstraint();
    }

    // End the assembly transfer
    public void endAssemblyTransfer() {
        assemblyTransferInProgress = false;
    }

    // Get the sable containing
    private Object getSableContaining() {

        return getSableContainingBE(this);
    }

    // Get the Sable sublevel containing a block entity
    private Object getSableContainingBE(net.minecraft.world.level.block.entity.BlockEntity be) {
        return SableLevelApi.containing(be);
    }

    // Get the sable containing entity
    private @Nullable Object getSableContainingEntity(Entity entity) {
        return SableLevelApi.containing(entity);
    }

    // Get the entity world position
    private Vec3 getEntityWorldPosition(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        Object entitySubLevel = getSableContainingEntity(entity);

        return toWorldAnchor(entitySubLevel, entity.position());
    }

    // Update the signal
    public void updateSignal(int newSignal) {
        redstoneSignal = Math.max(0, Math.min(15, newSignal));
        applySignalState();
    }

    // Set the computer signal override
    public void setComputerSignalOverride(int newSignal) {
        computerSignalOverride = Math.max(0, Math.min(15, newSignal));
        applySignalState();
    }

    // Clear the computer signal override
    public void clearComputerSignalOverride() {
        computerSignalOverride = -1;
        applySignalState();
    }

    // Toggle the manual open state
    public void toggleManualOpenClosed() {
        if (level != null && level.isClientSide()) {
            return;
        }
        computerSignalOverride = signalStrength > 0 ? 0 : 15;
        applySignalState();
    }

    // Get the receiver frequency first
    public ItemStack getReceiverFrequencyFirst() {
        return receiverBinding.first();
    }

    // Get the receiver frequency second
    public ItemStack getReceiverFrequencySecond() {
        return receiverBinding.second();
    }

    // Get the marker frequency
    public ItemStack getMarkerFrequency() {
        return markerBinding.first();
    }

    // Get the marker frequency first
    public ItemStack getMarkerFrequencyFirst() {
        return markerBinding.first();
    }

    // Get the marker frequency second
    public ItemStack getMarkerFrequencySecond() {
        return markerBinding.second();
    }

    // Set the receiver frequency
    public void setReceiverFrequency(ItemStack first, ItemStack second) {
        ItemStack previousFirst = receiverBinding.first();
        ItemStack previousSecond = receiverBinding.second();
        receiverBinding.set(first, second);
        if (ItemStack.isSameItemSameComponents(previousFirst, receiverBinding.first())
                && ItemStack.isSameItemSameComponents(previousSecond, receiverBinding.second())) {
            return;
        }

        wirelessSignal = 0;
        wirelessLatchedClosed = false;
        wirelessLatchedStrength = 0;
        lastWirelessNetworkPower = 0;
        wirelessToggleArmed = true;

        if (level != null && !level.isClientSide && registeredWithLinkNetwork) {
            net.minecraft.world.level.Level linkLevel = registeredLinkNetworkLevel != null
                    ? registeredLinkNetworkLevel
                    : getLinkNetworkLevel();

            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(linkLevel, linkReceiver);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(linkLevel, linkReceiver);
            registeredLinkNetworkLevel = linkLevel;
            lastLinkNetworkLocation = getLinkNetworkLocation();
        }
        applySignalState();
        setChanged();
        sendData();
    }

    // Set the marker frequency
    public void setMarkerFrequency(ItemStack first, ItemStack second) {

        markerBinding.set(ItemStack.EMPTY, ItemStack.EMPTY);
        markerSignal = 0;

        setChanged();
        sendData();
    }

    // Set the marker frequency
    public void setMarkerFrequency(ItemStack markerItem) {
        setMarkerFrequency(markerItem, ItemStack.EMPTY);
    }

    // Get the computer signal override
    public int getComputerSignalOverride() {
        return computerSignalOverride;
    }

    // Check if the claw is holding a connector
    public boolean isHoldingConnector() {
        return grabbedConnectorPos != null;
    }

    // Check if this is holding entity
    public boolean isHoldingEntity() {
        return grabbedEntityUUID != null || (grabbedEntity != null && !grabbedEntity.isRemoved());
    }

    // Check if this is holding
    public boolean isHolding() {
        return isHoldingConnector() || isHoldingEntity();
    }

    // Force the release
    public void forceRelease() {
        release();
        releaseEntity();
    }

    // Get the pending connector pos
    public @Nullable BlockPos getPendingConnectorPos() {
        ConnectorReference reference = getPendingConnectorReference();
        return reference == null ? null : reference.localPos();
    }

    // Get the pending connector reference
    public @Nullable ConnectorReference getPendingConnectorReference() {
        return pendingConnectorTarget == null ? null : connectorReference(pendingConnectorTarget);
    }

    // Find the nearest free connector in range
    public @Nullable BlockPos findNearestFreeConnectorInRange(int range) {
        ConnectorReference reference = findNearestFreeConnectorReferenceInRange(range);
        return reference == null ? null : reference.localPos();
    }

    // Find the nearest free connector reference in range
    public @Nullable ConnectorReference findNearestFreeConnectorReferenceInRange(int range) {
        if (level == null || level.isClientSide()) return null;
        int clampedRange = Math.max(1, range);
        double maxDistanceSq = clampedRange * (double) clampedRange;
        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        Vec3 clawAnchor = worldPosition.getCenter();
        Vec3 worldClawAnchor = SimulatedHelper.toGlobalWorldPosition(this, clawAnchor);
        BlockPos anchorPos = BlockPos.containing(worldClawAnchor);

        BlockEntity bestTarget = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (BlockEntity candidateBE : collectPotentialConnectorBlockEntities(lookupLevel, anchorPos)) {
            if (!isEligibleFreeConnector(candidateBE)) {
                continue;
            }
            Vec3 targetAnchor = candidateBE.getBlockPos().getCenter();
            if (candidateBE instanceof RopeStrandHolderBlockEntity holderBE) {
                targetAnchor = holderBE.getAttachmentPoint(candidateBE.getBlockPos(), candidateBE.getBlockState());
            }
            Vec3 worldTargetAnchor = SimulatedHelper.toGlobalWorldPosition(candidateBE, targetAnchor);
            double distanceSq = worldClawAnchor.distanceToSqr(worldTargetAnchor);
            if (distanceSq > maxDistanceSq) {
                continue;
            }
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestTarget = candidateBE;
            }
        }
        return bestTarget == null ? null : connectorReference(bestTarget);
    }

    // Get the free connectors in range
    public List<BlockPos> getFreeConnectorsInRange(int range, int limit) {
        return getFreeConnectorReferencesInRange(range, limit).stream()
                .map(ConnectorReference::localPos)
                .toList();
    }

    // Get the free connector references in range
    public List<ConnectorReference> getFreeConnectorReferencesInRange(int range, int limit) {
        if (level == null || level.isClientSide()) return List.of();
        int clampedRange = Math.max(1, range);
        int clampedLimit = Math.max(1, limit);
        double maxDistanceSq = clampedRange * (double) clampedRange;
        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        Vec3 clawAnchor = worldPosition.getCenter();
        Vec3 worldClawAnchor = SimulatedHelper.toGlobalWorldPosition(this, clawAnchor);
        BlockPos anchorPos = BlockPos.containing(worldClawAnchor);

        List<ConnectorReference> found = new ArrayList<>();
        for (BlockEntity candidateBE : collectPotentialConnectorBlockEntities(lookupLevel, anchorPos)) {
            if (!isEligibleFreeConnector(candidateBE)) {
                continue;
            }
            Vec3 targetAnchor = candidateBE.getBlockPos().getCenter();
            if (candidateBE instanceof RopeStrandHolderBlockEntity holderBE) {
                targetAnchor = holderBE.getAttachmentPoint(candidateBE.getBlockPos(), candidateBE.getBlockState());
            }
            Vec3 worldTargetAnchor = SimulatedHelper.toGlobalWorldPosition(candidateBE, targetAnchor);
            double distanceSq = worldClawAnchor.distanceToSqr(worldTargetAnchor);
            if (distanceSq > maxDistanceSq) {
                continue;
            }
            found.add(connectorReference(candidateBE));
            if (found.size() >= clampedLimit) {
                break;
            }
        }
        return found;
    }

    // Check if the connector is in range
    public boolean isConnectorInRange(BlockPos connectorPos, int range) {
        return isConnectorInRange(connectorPos, null, range, false);
    }

    // Check if the connector is in range
    public boolean isConnectorInRange(BlockPos connectorPos, @Nullable UUID subLevelId, int range) {
        return isConnectorInRange(connectorPos, subLevelId, range, true);
    }

    // Check if the connector is in range
    private boolean isConnectorInRange(BlockPos connectorPos, @Nullable UUID subLevelId, int range,
                                       boolean exactReference) {
        if (level == null || level.isClientSide() || connectorPos == null) return false;
        int clampedRange = Math.max(1, range);
        double maxDistanceSq = clampedRange * (double) clampedRange;
        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        BlockEntity candidateBE = exactReference
                ? SimulatedHelper.findBlockEntityExact(lookupLevel, subLevelId, connectorPos)
                : SimulatedHelper.findBlockEntityIncludingSubLevels(lookupLevel, connectorPos);
        if (!isEligibleFreeConnector(candidateBE)) {
            return false;
        }
        Vec3 clawAnchor = worldPosition.getCenter();
        Vec3 targetAnchor = candidateBE.getBlockPos().getCenter();
        if (candidateBE instanceof RopeStrandHolderBlockEntity holderBE) {
            targetAnchor = holderBE.getAttachmentPoint(candidateBE.getBlockPos(), candidateBE.getBlockState());
        }
        Vec3 worldClawAnchor = SimulatedHelper.toGlobalWorldPosition(this, clawAnchor);
        Vec3 worldTargetAnchor = SimulatedHelper.toGlobalWorldPosition(candidateBE, targetAnchor);
        return worldClawAnchor.distanceToSqr(worldTargetAnchor) <= maxDistanceSq;
    }

    // Select the connector
    public boolean selectConnector(BlockPos connectorPos) {
        return selectConnector(connectorPos, null, false);
    }

    // Select the connector
    public boolean selectConnector(BlockPos connectorPos, @Nullable UUID subLevelId) {
        return selectConnector(connectorPos, subLevelId, true);
    }

    // Select the connector
    private boolean selectConnector(BlockPos connectorPos, @Nullable UUID subLevelId, boolean exactReference) {
        if (level == null || level.isClientSide() || connectorPos == null) return false;
        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        BlockEntity candidateBE = exactReference
                ? SimulatedHelper.findBlockEntityExact(lookupLevel, subLevelId, connectorPos)
                : SimulatedHelper.findBlockEntityIncludingSubLevels(lookupLevel, connectorPos);
        if (!isEligibleFreeConnector(candidateBE)) {
            return false;
        }
        clearApproachConstraint();
        pendingConnectorTarget = candidateBE;
        debugStage = "approach_start";
        holdCheckTimer = 0;
        notifyUpdate();
        return true;
    }

    // Clear the selected connector
    public void clearSelectedConnector() {
        clearApproachConstraint();
        pendingConnectorTarget = null;
        debugStage = "idle";
        notifyUpdate();
    }

    // Check if this is an eligible free connector
    private boolean isEligibleFreeConnector(@Nullable BlockEntity candidateBE) {
        if (candidateBE == null || candidateBE == this || candidateBE.isRemoved()) {
            return false;
        }
        if (!isRopeConnector(candidateBE)) {
            return false;
        }
        Object clawSubLevel = getSableContaining();
        Object candidateSubLevel = getSableContainingBE(candidateBE);
        if (clawSubLevel != null && clawSubLevel == candidateSubLevel) {
            return false;
        }
        RopeStrandHolderBehavior targetHolder = resolveRopeHolderBehavior(candidateBE);
        return targetHolder != null && !targetHolder.isAttached();
    }

    // Check if the connector is within the distance
    private boolean isConnectorWithinDistance(BlockEntity connector, double maxDistanceSq) {
        if (connector == null || level == null) {
            return false;
        }
        Vec3 clawAnchor = SimulatedHelper.toGlobalWorldPosition(this, worldPosition.getCenter());
        Vec3 connectorAnchor = connector.getBlockPos().getCenter();
        if (connector instanceof RopeStrandHolderBlockEntity holder) {
            connectorAnchor = holder.getAttachmentPoint(connector.getBlockPos(), connector.getBlockState());
        }
        Vec3 worldConnectorAnchor = SimulatedHelper.toGlobalWorldPosition(connector, connectorAnchor);
        return clawAnchor.distanceToSqr(worldConnectorAnchor) <= maxDistanceSq;
    }

    // Apply the direct controller signal
    @Override
    public void applyDirectControllerSignal(String channelId, float val) {
        if (level != null && level.isClientSide()) {
            return;
        }
        int directSignal = net.minecraft.util.Mth.clamp((int) Math.ceil(net.minecraft.util.Mth.clamp(val, 0.0f, 1.0f) * 15.0f), 0, 15);

        setComputerSignalOverride(directSignal);
    }

    // Apply the signal state
    private void applySignalState() {
        int effectiveSignal = redstoneSignal;
        effectiveSignal = Math.max(effectiveSignal, wirelessSignal);
        if (computerSignalOverride >= 0) {
            effectiveSignal = Math.max(effectiveSignal, computerSignalOverride);
        }

        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            boolean powered = effectiveSignal > 0;
            if (state.hasProperty(ClawBlock.POWERED) && state.getValue(ClawBlock.POWERED) != powered) {
                level.setBlock(worldPosition, state.setValue(ClawBlock.POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }

        if (effectiveSignal == signalStrength) return;
        signalStrength = effectiveSignal;

        if (effectiveSignal == 0) {
            release();
            releaseEntity();
        }

        float target = effectiveSignal == 0 ? 0f : Math.min(effectiveSignal / 14.0f, 1.0f);
        clawAngle.updateChaseTarget(target);
        notifyUpdate();
    }

    // Sync the wireless signal from network
    private void syncWirelessSignalFromNetwork() {
        if (level == null || level.isClientSide || !receiverBinding.isBound()) {
            if (lastWirelessNetworkPower != 0) {
                linkReceiver.setReceivedStrength(0);
            }
            return;
        }

        int networkSignal = Math.max(
                queryWirelessSignalForLevel(receiverBinding, level),
                queryWirelessSignalForLevel(receiverBinding, resolveLookupLevel(level)));
        linkReceiver.setReceivedStrength(networkSignal);
    }

    // Sync the marker signal from network
    private void syncMarkerSignalFromNetwork() {
        if (markerSignal == 0 && !markerBinding.isBound()) {
            return;
        }
        markerBinding.set(ItemStack.EMPTY, ItemStack.EMPTY);
        markerSignal = 0;
        setChanged();
        sendData();
    }

    // Query the wireless signal for level
    private int queryWirelessSignalForLevel(FrequencyBinding binding, @Nullable net.minecraft.world.level.Level queryLevel) {
        if (queryLevel == null || binding == null || !binding.isBound()) {
            return 0;
        }

        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(binding.first()),
                RedstoneLinkNetworkHandler.Frequency.of(binding.second()));

        Map<Couple<RedstoneLinkNetworkHandler.Frequency>, Set<IRedstoneLinkable>> networks =
                Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(queryLevel);
        Set<IRedstoneLinkable> network = networks.get(key);
        if (network == null || network.isEmpty()) {
            return 0;
        }

        Vec3 currentCenter = Vec3.atCenterOf(worldPosition);
        Object currentSubLevel = getSableContaining();
        Vec3 projectedCurrent = currentSubLevel == null
                ? currentCenter
                : toWorldAnchor(currentSubLevel, currentCenter);

        int maxSignal = 0;
        int linkRange = AllConfigs.server().logistics.linkRange.get();
        double linkRangeSq = linkRange * (double) linkRange;
        for (IRedstoneLinkable candidate : network) {
            if (candidate == null || candidate == linkReceiver || !candidate.isAlive()) {
                continue;
            }

            int transmittedStrength = net.minecraft.util.Mth.clamp(candidate.getTransmittedStrength(), 0, 15);
            if (transmittedStrength <= 0) {
                continue;
            }

            BlockPos candidateLocation = candidate.getLocation();
            if (candidateLocation == null) {
                continue;
            }

            Vec3 projectedTarget = projectLinkLocationToWorld(queryLevel, candidateLocation);
            if (projectedTarget.distanceToSqr(projectedCurrent) > linkRangeSq) {
                continue;
            }

            if (transmittedStrength > maxSignal) {
                maxSignal = transmittedStrength;
                if (maxSignal >= 15) {
                    break;
                }
            }
        }

        return maxSignal;
    }

    // Get the project link location to world
    private Vec3 projectLinkLocationToWorld(net.minecraft.world.level.Level queryLevel, BlockPos candidateLocation) {
        Vec3 targetCenter = Vec3.atCenterOf(candidateLocation);
        SubLevel candidateSubLevel = SableLevelApi.containing(queryLevel, candidateLocation);
        Vec3 worldPos = SableTransformApi.toWorldPosition(candidateSubLevel, targetCenter);
        return worldPos == null ? targetCenter : worldPos;
    }

    // Scan the connector
    private void scanForConnector() {
        // -----------------------------------------------------SCAN CHECKS-----------------------------------------------------
        if (level == null) return;
        String previousStage = debugStage;
        debugStage = "scan";
        debugLastError = "none";
        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        BlockEntity bestTarget = null;
        Entity bestEntityTarget = null;
        double bestDistanceSq = Double.MAX_VALUE;
        double bestEntityDistanceSq = Double.MAX_VALUE;
        double closestEligibleDistanceSq = Double.MAX_VALUE;
        int totalCandidates = 0;
        int eligibleCandidates = 0;

        // -----------------------------------------------------SEARCH ORIGIN-----------------------------------------------------
        Vec3 clawAnchor = worldPosition.getCenter();
        Object clawSubLevel = getSableContaining();
        Vec3 worldClawAnchor = toWorldAnchor(clawSubLevel, clawAnchor);

        // -----------------------------------------------------BLOCK TARGETS-----------------------------------------------------
        for (BlockEntity candidateBE : collectPotentialConnectorBlockEntities(lookupLevel, BlockPos.containing(worldClawAnchor))) {
            if (candidateBE == null) continue;
            totalCandidates++;
            if (candidateBE == this || candidateBE.isRemoved()) continue;

            if (!isRopeConnector(candidateBE)) continue;
            RopeStrandHolderBehavior targetHolder = resolveRopeHolderBehavior(candidateBE);
            if (targetHolder == null || targetHolder.isAttached()) continue;
            eligibleCandidates++;

            Vec3 targetAnchor = candidateBE.getBlockPos().getCenter();
            if (candidateBE instanceof RopeStrandHolderBlockEntity holderBE) {
                targetAnchor = holderBE.getAttachmentPoint(candidateBE.getBlockPos(), candidateBE.getBlockState());
            }

            Object targetSubLevel = getSableContainingBE(candidateBE);
            Vec3 worldTargetAnchor = toWorldAnchor(targetSubLevel, targetAnchor);
            double distanceSq = worldClawAnchor.distanceToSqr(worldTargetAnchor);
            if (distanceSq < closestEligibleDistanceSq) {
                closestEligibleDistanceSq = distanceSq;
            }
            if (distanceSq > GRAB_SCAN_RADIUS_SQ) continue;
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestTarget = candidateBE;
            }
        }

        // ------------------------------------ENTITY TARGETS------------------------------------
        if (shouldScanLivingEntityTargets()) {

            for (Entity entity : collectPotentialLivingEntities(lookupLevel, worldClawAnchor)) {
                totalCandidates++;
                if (entity.isRemoved() || entity.isPassenger()) continue;
                eligibleCandidates++;

                Vec3 entityWorldPos = getEntityWorldPosition(entity);
                double distanceSq = worldClawAnchor.distanceToSqr(entityWorldPos);
                if (distanceSq < closestEligibleDistanceSq) {
                    closestEligibleDistanceSq = distanceSq;
                }
                if (distanceSq > GRAB_SCAN_RADIUS_SQ) continue;
                if (distanceSq < bestEntityDistanceSq) {
                    bestEntityDistanceSq = distanceSq;
                    bestEntityTarget = entity;
                }
            }
        }

        // -----------------------------------------------------SCAN RESULT-----------------------------------------------------
        debugScanCandidates = totalCandidates;
        debugScanEligible = eligibleCandidates;
        debugBestDistanceSq = closestEligibleDistanceSq == Double.MAX_VALUE
            ? -1.0
            : closestEligibleDistanceSq;

        if (bestTarget != null) {
            debugStage = "scan_target_found";
            pendingConnectorTarget = bestTarget;
            processSelectedConnector();
        } else if (bestEntityTarget != null) {
            debugStage = "scan_entity_target_found";
            beginEntityApproach(bestEntityTarget);
        } else {
            debugStage = eligibleCandidates > 0 ? "scan_no_target_range" : "scan_no_target";
            if (!Objects.equals(previousStage, debugStage)) {
                notifyUpdate();
            }
        }
    }

    // Check if the grab target is valid
    private boolean isValidGrabTarget(Entity entity) {
        if (entity == null || entity.isRemoved()) return false;
        if (!CTConfigs.COMMON.clawCanGrabEntities.get()) return false;
        if (entity instanceof Player player) {
            if (player.isCreative() && player.getAbilities().flying) return false;
        }

        if (entity == grabbedEntity) return false;
        return true;
    }

    // Get the connector reference
    private ConnectorReference connectorReference(BlockEntity connector) {
        BlockPos localPos = connector.getBlockPos().immutable();
        Vec3 localAnchor = localPos.getCenter();
        if (connector instanceof RopeStrandHolderBlockEntity holder) {
            localAnchor = holder.getAttachmentPoint(localPos, connector.getBlockState());
        }
        Vec3 worldAnchor = SimulatedHelper.toGlobalWorldPosition(connector, localAnchor);
        return new ConnectorReference(localPos, SimulatedHelper.getContainingSubLevelId(connector), worldAnchor);
    }

    // Check if this should scan living entity targets
    private boolean shouldScanLivingEntityTargets() {
        if (!CTConfigs.COMMON.clawCanGrabEntities.get()) {
            return false;
        }

        return net.neoforged.fml.loading.FMLEnvironment.dist.isClient();
    }

    // Collect the potential living entities
    private List<Entity> collectPotentialLivingEntities(net.minecraft.world.level.Level lookupLevel, Vec3 worldCenter) {
        List<Entity> candidates = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        AABB worldBounds = new AABB(
            worldCenter.x, worldCenter.y, worldCenter.z,
            worldCenter.x, worldCenter.y, worldCenter.z)
            .inflate(GRAB_SCAN_RADIUS);
        for (Entity entity : lookupLevel.getEntitiesOfClass(LivingEntity.class, worldBounds, this::isValidGrabTarget)) {
            if (entity != null && !entity.isRemoved() && seen.add(entity)) {
                candidates.add(entity);
            }
        }

        if (level != null && level != lookupLevel) {
            for (Entity entity : level.getEntitiesOfClass(LivingEntity.class, worldBounds, this::isValidGrabTarget)) {
                if (entity != null && !entity.isRemoved() && seen.add(entity)) {
                    candidates.add(entity);
                }
            }
        }

        try {
            for (Object subLevel : SubLevelBlockEntityCollector.getSubLevels(lookupLevel)) {
                if (!(subLevel instanceof net.minecraft.world.level.Level subLevelLevel)) {
                    continue;
                }
                Vec3 localCenter = SimulatedHelper.toContainingLocalPosition(subLevel, worldCenter);
                AABB localBounds = new AABB(
                        localCenter.x, localCenter.y, localCenter.z,
                        localCenter.x, localCenter.y, localCenter.z)
                        .inflate(GRAB_SCAN_RADIUS);
                for (Entity entity : subLevelLevel.getEntitiesOfClass(LivingEntity.class, localBounds, this::isValidGrabTarget)) {
                    if (entity != null && !entity.isRemoved() && seen.add(entity)) {
                        candidates.add(entity);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return candidates;
    }

    // Collect the potential connector block entities
    private List<BlockEntity> collectPotentialConnectorBlockEntities(net.minecraft.world.level.Level lookupLevel, BlockPos worldCenter) {
        List<BlockEntity> candidates = new ArrayList<>();
        Set<BlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        int chunkRadius = Math.max(1, (GRAB_SCAN_RADIUS + 15) >> 4);
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getLoadedWorldBlockEntities(lookupLevel, worldCenter, chunkRadius)) {
            if (blockEntity != null && !blockEntity.isRemoved() && seen.add(blockEntity)) {
                candidates.add(blockEntity);
            }
        }

        try {
            for (Object subLevel : SubLevelBlockEntityCollector.getSubLevels(lookupLevel)) {
                for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                    if (blockEntity != null && !blockEntity.isRemoved() && seen.add(blockEntity)) {
                        candidates.add(blockEntity);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        BlockPos[] fallbackCenters = new BlockPos[]{
                worldCenter,
                worldPosition
        };
        for (BlockPos center : fallbackCenters) {
            for (int dx = -GRAB_SCAN_RADIUS; dx <= GRAB_SCAN_RADIUS; dx++) {
                for (int dy = -GRAB_SCAN_RADIUS; dy <= GRAB_SCAN_RADIUS; dy++) {
                    for (int dz = -GRAB_SCAN_RADIUS; dz <= GRAB_SCAN_RADIUS; dz++) {
                        BlockPos candidatePos = center.offset(dx, dy, dz);
                        BlockEntity blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(lookupLevel, candidatePos);
                        if (blockEntity == null && level != null && level != lookupLevel) {
                            blockEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, candidatePos);
                        }
                        if (blockEntity != null && !blockEntity.isRemoved() && seen.add(blockEntity)) {
                            candidates.add(blockEntity);
                        }
                    }
                }
            }
        }

        return candidates;
    }

    // Resolve the lookup level
    private net.minecraft.world.level.Level resolveLookupLevel(net.minecraft.world.level.Level currentLevel) {
        net.minecraft.world.level.Level src = currentLevel == null ? level : currentLevel;
        net.minecraft.server.level.ServerLevel root = SableLevelApi.serverLevel(src);
        return root == null ? src : root;
    }

    // Resolve the server level
    private net.minecraft.server.level.ServerLevel resolveServerLevel(net.minecraft.world.level.Level currentLevel) {
        return SableLevelApi.serverLevel(resolveLookupLevel(currentLevel));
    }

    // Check if this is a rope connector
    private boolean isRopeConnector(BlockEntity blockEntity) {
        if (blockEntity instanceof RopeConnectorBlockEntity) {
            return true;
        }
        if (blockEntity.getType() == null) {
            return false;
        }
        ResourceLocation id = BlockEntityType.getKey(blockEntity.getType());
        return id != null && "simulated".equals(id.getNamespace()) && "rope_connector".equals(id.getPath());
    }

    // Begin the entity approach
    private void beginEntityApproach(Entity targetEntity) {
        if (level == null) return;
        if (targetEntity == null || targetEntity.isRemoved()) return;

        clearApproachConstraint();
        pendingConnectorTarget = null;
        grabbedEntity = targetEntity;
        grabbedEntityUUID = targetEntity.getUUID();
        debugStage = "entity_approach_start";
        holdCheckTimer = 0;
        notifyUpdate();
    }

    // Process the selected connector
    private void processSelectedConnector() {
        BlockEntity target = pendingConnectorTarget;
        if (target == null) {
            return;
        }
        if (!isEligibleFreeConnector(target) || !isConnectorWithinDistance(target, GRAB_SCAN_RADIUS_SQ)) {
            clearApproachConstraint();
            pendingConnectorTarget = null;
            debugStage = "selected_target_lost";
            notifyUpdate();
            return;
        }

        if (tryAttachConnector(target)) {
            clearApproachConstraint();
            pendingConnectorTarget = null;
            holdCheckTimer = 0;
            debugStage = "attached";
            notifyUpdate();
        } else {
            if (pendingConnectorTarget != target) {
                clearApproachConstraint();
                return;
            }
            processConnectorApproach(target);
        }
    }

    // Process the connector approach
    private void processConnectorApproach(BlockEntity target) {
        net.minecraft.server.level.ServerLevel serverLevel = resolveServerLevel(level);
        if (serverLevel == null || target == null || target.isRemoved()) {
            clearApproachConstraint();
            return;
        }

        Object clawSubLevel = getSableContaining();
        if (clawSubLevel == null) {
            doAutoAssemble();
            clearApproachConstraint();
            debugStage = "approach_waiting_for_claw_assembly";
            return;
        }

        BlockPos targetPos = target.getBlockPos();
        Vec3 targetAnchor = targetPos.getCenter();
        if (target instanceof RopeStrandHolderBlockEntity holder) {
            targetAnchor = holder.getAttachmentPoint(targetPos, target.getBlockState());
        }

        Direction jawDirection = ClawBlock.getJawDirection(getBlockState());
        Vec3 clawAnchor = worldPosition.getCenter().add(
                jawDirection.getStepX() * 0.49,
                jawDirection.getStepY() * 0.49,
                jawDirection.getStepZ() * 0.49);
        Vec3 worldClawAnchor = toWorldAnchor(clawSubLevel, clawAnchor);
        Vec3 worldTargetAnchor = toWorldAnchor(getSableContainingBE(target), targetAnchor);
        if (worldClawAnchor.distanceToSqr(worldTargetAnchor) > GRAB_SCAN_RADIUS_SQ) {
            clearApproachConstraint();
            pendingConnectorTarget = null;
            debugStage = "approach_out_of_range";
            notifyUpdate();
            return;
        }

        createOrRefreshApproachConstraint(serverLevel, clawSubLevel, worldTargetAnchor, clawAnchor);
    }

    // Process the entity approach and maybe attach
    private void processEntityApproachAndMaybeAttach() {
        if (level == null || grabbedEntity == null || grabbedEntity.isRemoved()) {
            grabbedEntity = null;
            grabbedEntityUUID = null;
            debugStage = "entity_approach_target_lost";
            return;
        }

        Object clawSubLevel = getSableContaining();
        Vec3 clawCenter = toWorldAnchor(clawSubLevel, worldPosition.getCenter());
        Vec3 entityWorldPos = getEntityWorldPosition(grabbedEntity);
        double distanceSq = clawCenter.distanceToSqr(entityWorldPos);

        if (distanceSq > GRAB_SCAN_RADIUS_SQ) {
            releaseEntity();
            debugStage = "entity_approach_out_of_range";
            return;
        }

        if (distanceSq <= 0.2 * 0.2) {

            if (createEntityGrabConstraint(grabbedEntity)) {
                debugStage = "entity_attached";
                notifyUpdate();
            } else {
                debugStage = "entity_attach_failed";
                releaseEntity();
            }
        } else {

            Vec3 directionToClawEntity = clawCenter.subtract(entityWorldPos).normalize();
            Vec3 localDirectionToClawEntity = toEntityMotionFrameDir(grabbedEntity, directionToClawEntity);
            double pullStrength = 0.3;
            grabbedEntity.setDeltaMovement(grabbedEntity.getDeltaMovement()
                    .add(localDirectionToClawEntity.scale(pullStrength)));
            debugStage = "entity_approaching";
        }
    }

    // Convert the claw to entity motion frame dir
    private Vec3 toEntityMotionFrameDir(Entity entity, Vec3 worldDirection) {
        if (worldDirection.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }

        Object entitySubLevel = getSableContainingEntity(entity);
        if (entitySubLevel == null) {
            return worldDirection.normalize();
        }

        Vec3 worldOrigin = getEntityWorldPosition(entity);
        Vec3 worldTarget = worldOrigin.add(worldDirection);
        Vec3 localOrigin = SimulatedHelper.toContainingLocalPosition(entitySubLevel, worldOrigin);
        Vec3 localTarget = SimulatedHelper.toContainingLocalPosition(entitySubLevel, worldTarget);
        if (localOrigin == null || localTarget == null) {
            return worldDirection.normalize();
        }

        Vec3 localDirection = localTarget.subtract(localOrigin);
        if (localDirection.lengthSqr() < 1.0E-6D) {
            return worldDirection.normalize();
        }
        return localDirection.normalize();
    }

    // Create the entity grab constraint
    private boolean createEntityGrabConstraint(Entity targetEntity) {
        if (level == null || level.isClientSide() || targetEntity == null || targetEntity.isRemoved()) {
            return false;
        }

        grabbedEntity = targetEntity;
        grabbedEntityUUID = targetEntity.getUUID();
        return true;
    }

    // Create the refresh approach constraint
    private void createOrRefreshApproachConstraint(
            net.minecraft.server.level.ServerLevel serverLevel,
            Object clawSubLevel,
            Vec3 goalAnchor,
            Vec3 clawAnchor) {
        clearApproachConstraint();
        try {
            Object config = SableConstraintApi.freeConfiguration(
                    new Vector3d(goalAnchor.x, goalAnchor.y, goalAnchor.z),
                    new Vector3d(clawAnchor.x, clawAnchor.y, clawAnchor.z),
                    new Quaterniond());

            Object pipeline = getPhysicsPipeline(serverLevel);
            if (pipeline == null) {
                debugLastError = "approach_pipeline_null";
                return;
            }

            Object created = SableConstraintApi.addConstraint(pipeline, null, clawSubLevel, config);
            approachConstraintHandle = created instanceof PhysicsConstraintHandle handle ? handle : null;
            if (approachConstraintHandle == null) {
                debugLastError = "approach_handle_null";
                return;
            }

            approachConstraintHandle.setMotor(ConstraintJointAxis.LINEAR_X, 0.0,
                    APPROACH_LINEAR_STIFFNESS, APPROACH_LINEAR_DAMPING, true, APPROACH_MAX_FORCE);
            approachConstraintHandle.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0,
                    APPROACH_LINEAR_STIFFNESS, APPROACH_LINEAR_DAMPING, true, APPROACH_MAX_FORCE);
            approachConstraintHandle.setMotor(ConstraintJointAxis.LINEAR_Z, 0.0,
                    APPROACH_LINEAR_STIFFNESS, APPROACH_LINEAR_DAMPING, true, APPROACH_MAX_FORCE);
            approachConstraintHandle.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0,
                    0.0, APPROACH_ANGULAR_DAMPING, true, APPROACH_MAX_FORCE);
            approachConstraintHandle.setMotor(ConstraintJointAxis.ANGULAR_Y, 0.0,
                    0.0, APPROACH_ANGULAR_DAMPING, true, APPROACH_MAX_FORCE);
            approachConstraintHandle.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0,
                    0.0, APPROACH_ANGULAR_DAMPING, true, APPROACH_MAX_FORCE);
            SableConstraintApi.wakeUp(pipeline, clawSubLevel);
            debugStage = "approach_motor_active";
            debugLastError = "none";
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            clearApproachConstraint();
            debugLastError = "approach_constraint_exception";
        }
    }

    // Get the physics pipeline
    private @Nullable Object getPhysicsPipeline(
            net.minecraft.server.level.ServerLevel serverLevel) {
        ServerSubLevelContainer container = getServerSubLevelContainer(serverLevel);
        return container == null ? null : container.physicsSystem().getPipeline();
    }

    // Get the server sublevel container
    @Nullable
    private ServerSubLevelContainer getServerSubLevelContainer(net.minecraft.server.level.ServerLevel serverLevel) {
        return serverLevel == null ? null : SubLevelContainer.getContainer(serverLevel);
    }

    // Convert the claw to world anchor
    private Vec3 toWorldAnchor(@Nullable Object containingSubLevel, Vec3 anchor) {
        if (anchor == null) return Vec3.ZERO;
        if (containingSubLevel instanceof SubLevel subLevel) {
            Vec3 world = SableTransformApi.toWorldPosition(subLevel, anchor);
            return world == null ? anchor : world;
        }
        return anchor;
    }

    // Resolve the rope holder behavior
    @Nullable
    private RopeStrandHolderBehavior resolveRopeHolderBehavior(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }

        RopeStrandHolderBehavior holder = (RopeStrandHolderBehavior) smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
        return holder;
    }

    // Try to attach connector
    private boolean tryAttachConnector(BlockEntity target) {
        if (level == null || level.isClientSide() || !isEligibleFreeConnector(target)) {
            return false;
        }

        ServerSubLevel clawSubLevel = getServerSubLevel(this);
        ServerSubLevel targetSubLevel = getServerSubLevel(target);
        if (clawSubLevel != null && clawSubLevel == targetSubLevel) {
            debugLastError = "attach_same_sublevel";
            return false;
        }

        Direction dir = ClawBlock.getJawDirection(getBlockState());
        Vector3d rayStart = JOMLConversion.atCenterOf(worldPosition)
                .add(dir.getStepX() * 0.5,
                        dir.getStepY() * 0.5,
                        dir.getStepZ() * 0.5);
        if (tryAttachAtContact(target, clawSubLevel, targetSubLevel, rayStart, dir)) {
            return true;
        }

        Direction secondaryDirection = dir.getAxis().isVertical()
                ? Direction.NORTH
                : dir.getClockWise();
        Direction tertiaryDirection = dir.getAxis().isVertical()
                ? Direction.EAST
                : Direction.UP;
        for (int secondaryOffset = -1; secondaryOffset <= 1; secondaryOffset += 2) {
            for (int tertiaryOffset = -1; tertiaryOffset <= 1; tertiaryOffset += 2) {
                Vector3d sample = new Vector3d(rayStart)
                        .add(secondaryDirection.getStepX() * secondaryOffset * CONTACT_SAMPLE_OFFSET,
                                secondaryDirection.getStepY() * secondaryOffset * CONTACT_SAMPLE_OFFSET,
                                secondaryDirection.getStepZ() * secondaryOffset * CONTACT_SAMPLE_OFFSET)
                        .add(tertiaryDirection.getStepX() * tertiaryOffset * CONTACT_SAMPLE_OFFSET,
                                tertiaryDirection.getStepY() * tertiaryOffset * CONTACT_SAMPLE_OFFSET,
                                tertiaryDirection.getStepZ() * tertiaryOffset * CONTACT_SAMPLE_OFFSET);
                if (tryAttachAtContact(target, clawSubLevel, targetSubLevel, sample, dir)) {
                    return true;
                }
            }
        }

        debugLastError = "no_connector_face_contact";
        return false;
    }

    // Try to attach at contact
    private boolean tryAttachAtContact(BlockEntity target, @Nullable ServerSubLevel clawSubLevel,
                                       @Nullable ServerSubLevel targetSubLevel, Vector3dc rayStart,
                                       Direction dir) {
        Vec3 start = JOMLConversion.toMojang(rayStart);
        Vec3 end = start.add(
                dir.getStepX() * CONTACT_RAY_LENGTH,
                dir.getStepY() * CONTACT_RAY_LENGTH,
                dir.getStepZ() * CONTACT_RAY_LENGTH);
        BlockHitResult hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return false;
        }

        BlockEntity hitBlockEntity = resolveContactBlockEntity(hit.getBlockPos());
        if (!sameConnector(target, hitBlockEntity)) {
            return false;
        }
        if (clawSubLevel == null) {
            doAutoAssemble();
            debugLastError = "waiting_for_claw_assembly";
            return false;
        }
        if (targetSubLevel == null) {
            clearApproachConstraint();
            BlockEntity movedTarget = assembleConnectorTarget(target);
            if (pendingConnectorTarget == target && movedTarget != null) {
                pendingConnectorTarget = movedTarget;
            }
            debugLastError = "waiting_for_connector_assembly";
            return false;
        }

        Quaterniondc firstOrientation = clawSubLevel.logicalPose().orientation();
        Quaterniondc secondOrientation = targetSubLevel.logicalPose().orientation();
        Direction targetDirection = hit.getDirection().getOpposite();
        Vector3d globalDirectionA = firstOrientation.transform(new Vector3d(
                dir.getStepX(), dir.getStepY(), dir.getStepZ()));
        Vector3d globalDirectionB = secondOrientation.transform(new Vector3d(
                targetDirection.getStepX(), targetDirection.getStepY(), targetDirection.getStepZ()));
        double dot = Math.max(-1.0, Math.min(1.0, globalDirectionA.dot(globalDirectionB)));
        if (dot <= 0.0) {
            return false;
        }
        double angle = Math.acos(dot);
        if (angle > Math.toRadians(CONTACT_ANGLE_TOLERANCE_DEGREES)) {
            return false;
        }

        Quaterniond relativeOrientation;
        if (angle < 1.0E-7) {
            relativeOrientation = firstOrientation.conjugate(new Quaterniond())
                    .mul(secondOrientation);
        } else {
            Vector3d axis = globalDirectionA.cross(globalDirectionB, new Vector3d()).normalize();
            relativeOrientation = new Quaterniond()
                    .rotateAxis(-angle, axis)
                    .mul(secondOrientation)
                    .premul(firstOrientation.conjugate(new Quaterniond()));
        }

        Vector3d positionA = JOMLConversion.toJOML(start);
        Vector3d positionB = JOMLConversion.toJOML(hit.getLocation());
        clearApproachConstraint();
        if (!applyGrabConstraint(clawSubLevel, targetSubLevel, positionA, positionB, relativeOrientation)) {
            return false;
        }

        grabbedConnectorPos = target.getBlockPos().immutable();
        grabbedConnectorSubLevelId = targetSubLevel.getUniqueId();
        grabConstraintPos1 = new Vector3d(positionA);
        grabConstraintPos2 = new Vector3d(positionB);
        grabConstraintOrientation = new Quaterniond(relativeOrientation);
        debugLastError = "none";
        return true;
    }

    // Apply the grab constraint
    private boolean applyGrabConstraint(ServerSubLevel clawSubLevel, ServerSubLevel targetSubLevel,
                                        Vector3dc positionA, Vector3dc positionB,
                                        Quaterniondc relativeOrientation) {
        net.minecraft.server.level.ServerLevel serverLevel = resolveServerLevel(level);
        ServerSubLevelContainer container = getServerSubLevelContainer(serverLevel);
        if (container == null) {
            debugLastError = "attach_pipeline_unavailable";
            return false;
        }

        clearGrabConstraintHandle();
        try {
            Object config = SableConstraintApi.fixedConfiguration(positionA, positionB, relativeOrientation);
            Object created = SableConstraintApi.addConstraint(
                    container.physicsSystem().getPipeline(),
                    clawSubLevel,
                    targetSubLevel,
                    config);
            grabConstraintHandle = created instanceof PhysicsConstraintHandle handle ? handle : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            grabConstraintHandle = null;
        }

        if (grabConstraintHandle == null) {
            debugLastError = "attach_handle_null";
            return false;
        }
        return true;
    }

    // Assemble the connector target
    private @Nullable BlockEntity assembleConnectorTarget(BlockEntity target) {
        net.minecraft.world.level.Level targetLevel = target.getLevel();
        if (targetLevel == null) {
            return null;
        }
        BlockPos targetPos = target.getBlockPos();
        try {
            SubLevelAssemblyApi.AssemblyResult res = SubLevelAssemblyApi.assembleBlock(
                    targetLevel, targetPos, targetPos, true, false);
            if (res != null) {
                BlockPos movedTargetPos = targetPos.offset(res.offset());
                BlockEntity movedTarget = SimulatedHelper.findBlockEntityInSubLevel(
                        res.subLevel(), movedTargetPos, BlockEntity.class);
                return movedTarget != null && isRopeConnector(movedTarget) ? movedTarget : null;
            }
        } catch (com.simibubi.create.content.contraptions.AssemblyException ignored) {
        }
        return null;
    }

    // Get the server sublevel
    private @Nullable ServerSubLevel getServerSubLevel(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        SubLevel containing = Sable.HELPER.getContaining(blockEntity);
        return containing instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Resolve the contact block entity
    private @Nullable BlockEntity resolveContactBlockEntity(BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        SubLevel containing = Sable.HELPER.getContaining(level, pos);
        if (containing != null) {
            return SubLevelBlockEntityCollector.getBlockEntity(containing, pos);
        }
        net.minecraft.server.level.ServerLevel serverLevel = resolveServerLevel(level);
        return serverLevel == null ? null : serverLevel.getBlockEntity(pos);
    }

    // Check if this uses the same connector
    private boolean sameConnector(BlockEntity expected, @Nullable BlockEntity actual) {
        if (expected == null || actual == null || !isRopeConnector(actual)) {
            return false;
        }
        return expected.getBlockPos().equals(actual.getBlockPos())
                && Objects.equals(
                        SimulatedHelper.getContainingSubLevelId(expected),
                        SimulatedHelper.getContainingSubLevelId(actual));
    }

    // Clear the grab constraint handle
    private void clearGrabConstraintHandle() {
        removeConstraintHandle(grabConstraintHandle);
        grabConstraintHandle = null;
    }

    // Check if the constraint handle is valid
    private boolean isConstraintHandleValid(@Nullable PhysicsConstraintHandle handle) {
        return handle != null && handle.isValid();
    }

    // Remove the constraint handle
    private void removeConstraintHandle(@Nullable PhysicsConstraintHandle handle) {
        SableConstraintApi.remove(handle);
    }

    // Clear the grabbed connector state
    private void clearGrabbedConnectorState() {
        clearGrabConstraintHandle();
        grabbedConnectorPos = null;
        grabbedConnectorSubLevelId = null;
        grabConstraintPos1 = null;
        grabConstraintPos2 = null;
        grabConstraintOrientation = null;
        holdCheckTimer = 0;
    }

    // Release the claw
    private void release() {
        if (grabbedConnectorPos == null && grabbedEntity == null && pendingConnectorTarget == null) {
            return;
        }

        clearGrabbedConnectorState();
        clearApproachConstraint();
        pendingConnectorTarget = null;
        grabbedEntity = null;
        grabbedEntityUUID = null;
        notifyUpdate();
    }

    // Try to restore grab constraint
    private boolean tryRestoreGrabConstraint() {
        if (level == null || level.isClientSide() || grabbedConnectorPos == null) {
            return false;
        }
        if (grabConstraintPos1 == null || grabConstraintPos2 == null || grabConstraintOrientation == null) {
            clearGrabbedConnectorState();
            debugLastError = "saved_constraint_incomplete";
            return false;
        }

        BlockEntity target = SimulatedHelper.findBlockEntity(
                resolveLookupLevel(level), grabbedConnectorSubLevelId, grabbedConnectorPos);
        if (target == null || target.isRemoved() || !isRopeConnector(target)) {
            return false;
        }

        ServerSubLevel clawSubLevel = getServerSubLevel(this);
        ServerSubLevel targetSubLevel = getServerSubLevel(target);
        if (clawSubLevel == null || targetSubLevel == null || clawSubLevel == targetSubLevel) {
            return false;
        }
        if (!applyGrabConstraint(clawSubLevel, targetSubLevel,
                grabConstraintPos1, grabConstraintPos2, grabConstraintOrientation)) {
            return false;
        }

        holdCheckTimer = 0;
        debugStage = "attached_restored";
        debugLastError = "none";
        notifyUpdate();
        return true;
    }

    // Try to restore grabbed entity
    private boolean tryRestoreGrabbedEntity() {
        if (level == null || level.isClientSide() || grabbedEntityUUID == null) {
            return false;
        }

        net.minecraft.server.level.ServerLevel serverLevel = resolveServerLevel(level);
        Entity entity = serverLevel == null ? null : serverLevel.getEntity(grabbedEntityUUID);
        if (entity == null || entity.isRemoved() || !(entity instanceof LivingEntity)) {
            grabbedEntityUUID = null;
            return false;
        }

        grabbedEntity = (LivingEntity) entity;
        debugStage = "entity_attached_restored";
        debugLastError = "none";
        notifyUpdate();
        return true;
    }

    // Check if this is grab target definitely gone
    private boolean isGrabTargetDefinitelyGone() {
        if (level == null || grabbedConnectorPos == null) {
            return true;
        }

        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        if (grabbedConnectorSubLevelId != null) {

            Object targetSubLevel = SubLevelBlockEntityCollector.getSubLevel(lookupLevel, grabbedConnectorSubLevelId);
            if (targetSubLevel == null) {
                return false;
            }
            for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(targetSubLevel)) {
                if (blockEntity != null && !blockEntity.isRemoved() && grabbedConnectorPos.equals(blockEntity.getBlockPos()) && isRopeConnector(blockEntity)) {
                    return false;
                }
            }
            return true;
        }

        int chunkX = grabbedConnectorPos.getX() >> 4;
        int chunkZ = grabbedConnectorPos.getZ() >> 4;

        if (!lookupLevel.hasChunk(chunkX, chunkZ)) {
            return false;
        }

        BlockEntity worldBlockEntity = lookupLevel.getBlockEntity(grabbedConnectorPos);
        return worldBlockEntity == null || worldBlockEntity.isRemoved() || !isRopeConnector(worldBlockEntity);
    }

    // Evaluate the hold
    private void evaluateHold() {
        if (grabbedConnectorPos == null || level == null) return;

        double mass = getGrabbedSubLevelMass();
        double pDrop = calculateGripDropChance(signalStrength, mass);

        RandomSource rng = level.getRandom();
        if (rng.nextDouble() < pDrop) {
            release();
        }
    }

    // Get the grabbed sublevel mass
    private double getGrabbedSubLevelMass() {
        if (grabbedConnectorPos == null || level == null) return 0.0;
        try {
            net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
            Object subLevel = grabbedConnectorSubLevelId == null
                    ? Sable.HELPER.getContaining(lookupLevel, grabbedConnectorPos)
                    : SubLevelBlockEntityCollector.getSubLevel(lookupLevel, grabbedConnectorSubLevelId);
            if (subLevel instanceof ServerSubLevel serverSubLevel) {
                return serverSubLevel.getMassTracker().getMass();
            }
        } catch (RuntimeException ignored) {
        }
        return 0.0;
    }

    // Evaluate the entity hold
    private void evaluateEntityHold() {
        if (grabbedEntity == null || grabbedEntity.isRemoved() || level == null) {
            releaseEntity();
            return;
        }

        double entityMass = getEntityMass(grabbedEntity);
        double pDrop = calculateGripDropChance(signalStrength, entityMass);

        RandomSource rng = level.getRandom();
        if (rng.nextDouble() < pDrop) {
            releaseEntity();
        }
    }

    // Clear the approach constraint
    private void clearApproachConstraint() {
        removeConstraintHandle(approachConstraintHandle);
        approachConstraintHandle = null;
    }

    // Calculate the grip drop chance
    static double calculateGripDropChance(int signal, double mass) {
        int clampedSignal = Math.max(0, Math.min(signal, 14));
        double baseChance = 1.0 - clampedSignal / 14.0;
        double massFactor = Math.max(1.0, Math.min(mass / MASS_REFERENCE, MAX_MASS_FACTOR));
        return Math.max(0.0, Math.min(baseChance * massFactor, 1.0));
    }

    // Get the entity mass
    private double getEntityMass(Entity entity) {
        if (entity instanceof LivingEntity living) {

            float maxHealth = living.getMaxHealth();

            return Math.max(0.5, maxHealth / 10.0);
        }

        return 1.0;
    }

    // Release the entity
    private void releaseEntity() {
        if (grabbedEntity == null && grabbedEntityUUID == null) return;
        grabbedEntity = null;
        grabbedEntityUUID = null;
        holdCheckTimer = 0;
        notifyUpdate();
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

        return pos.getCenter().add(0.0, 0.6, 0.0);
    }

    // Get the visual attachment point
    @Override
    public Vec3 getVisualAttachmentPoint(BlockPos pos, BlockState state) {
        return pos.getCenter().add(0.0, 0.6, 0.0);
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {

        return new AABB(worldPosition).inflate(1.0);
    }

    // Create the menu
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ClawMenu(containerId, playerInventory, this);
    }

    // Get the display name
    @Override
    public Component getDisplayName() {
        return Component.translatable("createthrusters.claw.config.title");
    }

    // Check if the player can use this
    public boolean canPlayerUse(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64.0D;
    }

    // Send the menu data
    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        com.rieno.gadgetsandgizmos.lib.menuconfig.MenuOpenHeader.encode(
                buffer,
                worldPosition,
                com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper.getContainingSubLevelId(this));
    }

    // Register the link receiver
    private void registerLinkReceiver() {
        if (registeredWithLinkNetwork || level == null || level.isClientSide) {
            return;
        }

        registeredWithLinkNetwork = false;
        registeredLinkNetworkLevel = null;
        lastLinkNetworkLocation = null;
        return;

    }

    // Remove the link receiver
    private void unregisterLinkReceiver() {
        registeredWithLinkNetwork = false;
        registeredLinkNetworkLevel = null;
        lastLinkNetworkLocation = null;
    }

    // Sync the link receiver registration
    private void syncLinkReceiverRegistration() {
        if (level == null || level.isClientSide) {
            return;
        }

        registeredWithLinkNetwork = false;
        registeredLinkNetworkLevel = null;
        lastLinkNetworkLocation = null;
    }

    // Get the link network level
    private net.minecraft.world.level.Level getLinkNetworkLevel() {
        net.minecraft.world.level.Level lookup = resolveLookupLevel(level);
        if (lookup != null) {
            return lookup;
        }
        if (registeredLinkNetworkLevel != null) {
            return registeredLinkNetworkLevel;
        }
        return level;
    }

    // Get the link network location
    private BlockPos getLinkNetworkLocation() {
        Vec3 worldCenter = toWorldAnchor(getSableContaining(), worldPosition.getCenter());
        return BlockPos.containing(worldCenter);
    }

    // Handle the claw link receiver
    private class ClawLinkReceiver implements IRedstoneLinkable {
        // Get the transmitted strength
        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        // Set the received strength
        @Override
        public void setReceivedStrength(int networkPower) {
            int clamped = net.minecraft.util.Mth.clamp(networkPower, 0, 15);

            if (clamped == 0) {
                wirelessToggleArmed = true;
                lastWirelessNetworkPower = 0;
                applySignalState();
                return;
            }

            boolean risingEdge = wirelessToggleArmed;
            wirelessToggleArmed = false;
            lastWirelessNetworkPower = clamped;

            if (risingEdge) {
                if (wirelessLatchedClosed) {
                    wirelessLatchedClosed = false;
                    wirelessLatchedStrength = 0;
                    wirelessSignal = 0;
                } else {
                    wirelessLatchedClosed = true;

                    wirelessLatchedStrength = Math.max(1, clamped);
                    wirelessSignal = wirelessLatchedStrength;
                }
                applySignalState();
                return;
            }

            if (wirelessLatchedClosed && clamped > 0 && clamped != wirelessLatchedStrength) {
                wirelessLatchedStrength = clamped;
                wirelessSignal = wirelessLatchedStrength;
                applySignalState();
                return;
            }

            applySignalState();
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
            if (!receiverBinding.isBound()) {
                return Couple.create(RedstoneLinkNetworkHandler.Frequency.EMPTY, RedstoneLinkNetworkHandler.Frequency.EMPTY);
            }
            return Couple.create(
                    RedstoneLinkNetworkHandler.Frequency.of(receiverBinding.first()),
                    RedstoneLinkNetworkHandler.Frequency.of(receiverBinding.second()));
        }

        // Get the location
        @Override
        public BlockPos getLocation() {

            return worldPosition;
        }
    }

    // Get the signal strength
    public int getSignalStrength() {
        return signalStrength;
    }

    // Get the wireless signal
    public int getWirelessSignal() {
        return wirelessSignal;
    }

    // Get the marker signal
    public int getMarkerSignal() {
        return 0;
    }

    // Get the grabbed connector pos
    @Nullable
    public BlockPos getGrabbedConnectorPos() {
        return grabbedConnectorPos;
    }

    // Get the grabbed connector reference
    public @Nullable ConnectorReference getGrabbedConnectorReference() {
        if (grabbedConnectorPos == null || level == null) {
            return null;
        }

        net.minecraft.world.level.Level lookupLevel = resolveLookupLevel(level);
        BlockEntity connector = SimulatedHelper.findLoadedBlockEntityExact(
                lookupLevel, grabbedConnectorSubLevelId, grabbedConnectorPos);
        if (connector != null && !connector.isRemoved()) {
            return connectorReference(connector);
        }

        Vec3 localCenter = grabbedConnectorPos.getCenter();
        Vec3 worldCenter = grabbedConnectorSubLevelId == null ? localCenter : null;
        if (grabbedConnectorSubLevelId != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(lookupLevel, grabbedConnectorSubLevelId);
            if (subLevel != null) {
                worldCenter = SimulatedHelper.toContainingWorldPosition(subLevel, localCenter);
                worldCenter = SimulatedHelper.projectOutOfSubLevels(lookupLevel, worldCenter);
            }
        }
        return new ConnectorReference(grabbedConnectorPos.immutable(), grabbedConnectorSubLevelId, worldCenter);
    }

    // Get the grabbed connector sublevel id
    @Nullable
    public UUID getGrabbedConnectorSubLevelId() {
        return grabbedConnectorSubLevelId;
    }

    // Add the goggle tooltip
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean showDetails = CTTooltipHelper.showGoggleDetails(isPlayerSneaking);
        tooltip.add(CTTooltipHelper.title(Component.translatable("block.createthrusters.claw")));
        tooltip.add(CTTooltipHelper.line("Holding",
            CTTooltipHelper.onOff(grabbedConnectorPos != null)));
        if (showDetails) {
            tooltip.add(CTTooltipHelper.line("Signal",
                    CTTooltipHelper.value(Integer.toString(signalStrength), ChatFormatting.RED)));
            tooltip.add(CTTooltipHelper.line("Marker Signal",
                    CTTooltipHelper.value(Integer.toString(markerSignal), ChatFormatting.GOLD)));
            tooltip.add(CTTooltipHelper.line("Marker Binding",
                    CTTooltipHelper.value(markerBinding.isBound() ? "bound" : "empty", ChatFormatting.AQUA)));
            String stage = debugStage == null || debugStage.isBlank() ? "idle" : debugStage;
            tooltip.add(CTTooltipHelper.line("Stage",
                CTTooltipHelper.value(stage, ChatFormatting.AQUA)));
        }
        return true;
    }

    // Write the claw safely
    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        tag.put("ReceiverBinding", receiverBinding.toTag(provider));
        tag.put("MarkerBinding", markerBinding.toTag(provider));
    }

    // Write the claw
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("Signal", signalStrength);
        tag.putInt("RedstoneSignal", redstoneSignal);
        tag.putInt("WirelessSignal", wirelessSignal);
        tag.putInt("MarkerSignal", markerSignal);
        tag.putInt("ComputerSignal", computerSignalOverride);
        tag.put("ReceiverBinding", receiverBinding.toTag(provider));
        tag.put("MarkerBinding", markerBinding.toTag(provider));
        BlockPos savedGrabbedPos = grabbedConnectorPos;
        UUID savedGrabbedSubLevelId = grabbedConnectorSubLevelId;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null && savedGrabbedPos != null) {
            if (savedGrabbedSubLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping =
                        ctx.getMapping(savedGrabbedSubLevelId);
                if (mapping == null) {
                    savedGrabbedPos = null;
                    savedGrabbedSubLevelId = null;
                } else {
                    savedGrabbedPos = mapping.transform().apply(savedGrabbedPos);
                    savedGrabbedSubLevelId = mapping.newUUID();
                }
            } else if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
                savedGrabbedPos = ctx.getBoundingBox().contains(
                        savedGrabbedPos.getX(), savedGrabbedPos.getY(), savedGrabbedPos.getZ())
                        ? ctx.getPlaceTransform().apply(savedGrabbedPos)
                        : null;
            } else {
                savedGrabbedPos = ctx.getSetupTransform().apply(savedGrabbedPos);
            }
        }
        if (savedGrabbedPos != null) {
            tag.put("GrabbedPos", NbtUtils.writeBlockPos(savedGrabbedPos));
        }
        if (savedGrabbedSubLevelId != null) {

            tag.putUUID("GrabbedSubLevelId", savedGrabbedSubLevelId);
        }
        if (ctx == null
                && grabbedConnectorPos != null
                && grabConstraintPos1 != null
                && grabConstraintPos2 != null
                && grabConstraintOrientation != null) {
            CompoundTag constraint = new CompoundTag();
            constraint.put("ClawPos", NbtUtils.writeBlockPos(worldPosition));
            constraint.putDouble("FromX", grabConstraintPos1.x);
            constraint.putDouble("FromY", grabConstraintPos1.y);
            constraint.putDouble("FromZ", grabConstraintPos1.z);
            constraint.putDouble("ToX", grabConstraintPos2.x);
            constraint.putDouble("ToY", grabConstraintPos2.y);
            constraint.putDouble("ToZ", grabConstraintPos2.z);
            constraint.putDouble("QuatX", grabConstraintOrientation.x);
            constraint.putDouble("QuatY", grabConstraintOrientation.y);
            constraint.putDouble("QuatZ", grabConstraintOrientation.z);
            constraint.putDouble("QuatW", grabConstraintOrientation.w);
            tag.put("GrabConstraint", constraint);
        }
        if (grabbedEntityUUID != null) {

            tag.putUUID("GrabbedEntityUUID", grabbedEntityUUID);
        }
        tag.putFloat("ClawAngle", clawAngle.getChaseTarget());
        tag.putString("DebugStage", debugStage);
        tag.putString("DebugLastError", debugLastError);
        tag.putInt("DebugScanCandidates", debugScanCandidates);
        tag.putInt("DebugScanEligible", debugScanEligible);
        tag.putDouble("DebugBestDistanceSq", debugBestDistanceSq);
    }

    // Read the claw
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        signalStrength = tag.getInt("Signal");
        redstoneSignal = tag.contains("RedstoneSignal") ? tag.getInt("RedstoneSignal") : signalStrength;
        wirelessSignal = tag.contains("WirelessSignal") ? tag.getInt("WirelessSignal") : 0;
        markerSignal = tag.contains("MarkerSignal") ? tag.getInt("MarkerSignal") : 0;
        wirelessLatchedClosed = wirelessSignal > 0;
        wirelessLatchedStrength = wirelessSignal;
        lastWirelessNetworkPower = 0;
        wirelessToggleArmed = true;
        computerSignalOverride = tag.contains("ComputerSignal") ? tag.getInt("ComputerSignal") : -1;
        // ------------------------------------WIRELESS BINDINGS------------------------------------
        if (tag.contains("ReceiverBinding")) {
            receiverBinding.read(tag.getCompound("ReceiverBinding"), provider);
        }
        if (tag.contains("MarkerBinding")) {
            markerBinding.read(tag.getCompound("MarkerBinding"), provider);
        }
        // -----------------------------------------------------GRAB TARGET-----------------------------------------------------
        grabbedConnectorPos = tag.contains("GrabbedPos")
                ? NbtUtils.readBlockPos(tag, "GrabbedPos").orElse(null)
                : null;

        grabbedConnectorSubLevelId = tag.contains("GrabbedSubLevelId")
            ? tag.getUUID("GrabbedSubLevelId")
            : null;
        // ------------------------------------SCHEMATIC REMAP------------------------------------
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null && ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE
                && grabbedConnectorPos != null) {
            if (grabbedConnectorSubLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping =
                        ctx.getMapping(grabbedConnectorSubLevelId);
                if (mapping == null) {
                    grabbedConnectorPos = null;
                    grabbedConnectorSubLevelId = null;
                } else {
                    grabbedConnectorPos = mapping.transform().apply(grabbedConnectorPos);
                    grabbedConnectorSubLevelId = mapping.newUUID();
                }
            } else {
                grabbedConnectorPos = ctx.getPlaceTransform().apply(grabbedConnectorPos);
            }
        }
        grabConstraintPos1 = null;
        grabConstraintPos2 = null;
        grabConstraintOrientation = null;
        // ------------------------------------CONSTRAINT STATE------------------------------------
        if (tag.contains("GrabConstraint", 10)) {
            CompoundTag constraint = tag.getCompound("GrabConstraint");
            BlockPos savedClawPos = NbtUtils.readBlockPos(constraint, "ClawPos").orElse(null);
            if (worldPosition.equals(savedClawPos)) {
                grabConstraintPos1 = new Vector3d(
                        constraint.getDouble("FromX"),
                        constraint.getDouble("FromY"),
                        constraint.getDouble("FromZ"));
                grabConstraintPos2 = new Vector3d(
                        constraint.getDouble("ToX"),
                        constraint.getDouble("ToY"),
                        constraint.getDouble("ToZ"));
                grabConstraintOrientation = new Quaterniond(
                        constraint.getDouble("QuatX"),
                        constraint.getDouble("QuatY"),
                        constraint.getDouble("QuatZ"),
                        constraint.getDouble("QuatW"));
            }
        }
        // ------------------------------------ENTITY / DEBUG STATE------------------------------------
        grabbedEntityUUID = tag.contains("GrabbedEntityUUID")
            ? tag.getUUID("GrabbedEntityUUID")
            : null;
        if (tag.contains("ClawAngle")) {
            clawAngle.updateChaseTarget(tag.getFloat("ClawAngle"));
            clawAngle.chase(tag.getFloat("ClawAngle"), 0.15f, LerpedFloat.Chaser.LINEAR);
        }
        if (tag.contains("DebugStage")) {
            debugStage = tag.getString("DebugStage");
        }
        if (tag.contains("DebugLastError")) {
            debugLastError = tag.getString("DebugLastError");
        }
        debugScanCandidates = tag.contains("DebugScanCandidates") ? tag.getInt("DebugScanCandidates") : 0;
        debugScanEligible = tag.contains("DebugScanEligible") ? tag.getInt("DebugScanEligible") : 0;
        debugBestDistanceSq = tag.contains("DebugBestDistanceSq") ? tag.getDouble("DebugBestDistanceSq") : -1.0;
    }

    // Return the rope and grabbed connector sublevels
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        SubLevel grabbed = SubLevelConnectionApi.resolve(level, grabbedConnectorSubLevelId);
        return SubLevelConnectionApi.merge(
                RopeStrandHolderBlockEntity.super.sable$getConnectionDependencies(),
                grabbed == null ? List.of() : List.of(grabbed));
    }
}
