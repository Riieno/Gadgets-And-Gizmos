package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnection;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyConnectionProvider;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyDynamicsApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableMagneticCaptureApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableYawJointApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// Pair two carriage endpoints and keep their saved yaw joint safe across movement and reloads
public class ShipCouplerBlockEntity extends SmartBlockEntity
        implements BlockEntitySubLevelActor, SableAssemblyConnectionProvider {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double ATTACH_RADIUS_SQUARED =
            ShipCouplerBlock.ATTACH_RADIUS * ShipCouplerBlock.ATTACH_RADIUS;
    private static final double MAGNETIC_CAPTURE_RADIUS = 4.0D;
    private static final double MAGNETIC_CAPTURE_RADIUS_SQUARED =
            MAGNETIC_CAPTURE_RADIUS * MAGNETIC_CAPTURE_RADIUS;
    private static final double MAGNETIC_CAPTURE_MAX_ACCELERATION = 1.5D;
    private static final double FACING_DOT_LIMIT = -0.8660254D;
    private static final double RECOVERY_FACING_DOT_LIMIT = -0.707106781D;
    private static final double UP_DOT_LIMIT = 0.996194698D;
    private static final int SCAN_INTERVAL = 5;
    private static final int LOAD_RETRY_INTERVAL = 20;
    private static final int LOAD_REDSTONE_RECHECK_TICKS = 40;
    private static final int STALE_LINK_GRACE_TICKS = 600;
    private static final int JOINT_TOPOLOGY_GRACE_TICKS = 40;
    private static final double FREE_YAW_RADIANS = Math.toRadians(10.0D);
    private static final double MAXIMUM_YAW_RADIANS = Math.toRadians(30.0D);
    private static final double MAXIMUM_STIFFNESS = 36.0D;
    private static final double MAXIMUM_DAMPING = 8.0D;
    private static final double MAXIMUM_FORCE_PER_INERTIA = 12.0D;
    private static final double MINIMUM_EFFECTIVE_SERVO_INERTIA = 0.05D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current partner sub-level id
    private @Nullable UUID partnerSubLevelId;
    // Current partner position
    private @Nullable BlockPos partnerPosition;
    // Current pairing token
    private @Nullable UUID pairingToken;
    // Current yaw joint
    private @Nullable SableYawJointApi.Joint yawJoint;
    // Joint target yaw in radians
    private double jointTargetYawRadians;
    // Tracks whether yaw servo is active
    private boolean yawServoActive;
    // Joint unavailable tick count
    private int jointUnavailableTicks;
    // Yaw servo inertia tick
    private long yawServoInertiaTick = Long.MIN_VALUE;
    // Cached yaw servo inertia
    private double cachedYawServoInertia;

    // Current scan cooldown
    private int scanCooldown;
    // Current load retry cooldown
    private int loadRetryCooldown;
    // Load redstone recheck tick count
    private int loadRedstoneRecheckTicks;
    // Stale link tick count
    private int staleLinkTicks;
    // Current magnetic capture candidate
    private @Nullable ShipCouplerBlockEntity magneticCaptureCandidate;
    // Tracks whether redstone powered is set
    private boolean redstonePowered;
    // Tracks whether redstone inverted is set
    private boolean redstoneInverted;
    // Tracks whether automation request is set
    private @Nullable Boolean automationRequest;
    // Tracks whether assembly transfer is in progress
    private boolean assemblyTransferInProgress;
    // Tracks whether removal handled is set
    private boolean removalHandled;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship coupler
    public ShipCouplerBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SHIP_COUPLER.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // Initialize the ship coupler
    @Override
    public void initialize() {
        super.initialize();
        if (level == null || level.isClientSide) {
            return;
        }
        ShipCouplerService.registerLoaded(this);
        BlockState state = getBlockState();
        if (automationRequest == null && state.hasProperty(ShipCouplerBlock.POWERED)) {
            redstonePowered = redstoneInverted
                    ? !state.getValue(ShipCouplerBlock.POWERED)
                    : state.getValue(ShipCouplerBlock.POWERED);
        }
        loadRedstoneRecheckTicks = LOAD_REDSTONE_RECHECK_TICKS;
        applyRequestedPowerState();
        syncAttachedState(isCouplingRequested() && hasPersistedLink());
        if (!isCouplingRequested()) {
            detachPair();
        } else {
            scanCooldown = 0;
            loadRetryCooldown = 0;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the ship coupler
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        ShipCouplerService.registerLoaded(this);
        if (assemblyTransferInProgress) {
            return;
        }
        recheckRedstoneAfterLoad();

        if (yawJoint != null && !hasPersistedLink()) {
            releaseJoint();
            if (yawJoint != null) {
                return;
            }
        }

        if (!isCouplingRequested()) {
            if (hasPersistedLink()) {
                detachPair();
            }
            return;
        }

        if (hasPersistedLink()) {
            maintainPair();
            return;
        }

        syncAttachedState(false);
        if (scanCooldown-- > 0) {
            return;
        }
        scanCooldown = SCAN_INTERVAL;
        ShipCouplerBlockEntity candidate = ShipCouplerService.findMutualCandidate(this);
        if (candidate != null) {
            magneticCaptureCandidate = null;
            establishPair(candidate);
            return;
        }
        magneticCaptureCandidate = ShipCouplerService.findMutualMagneticCandidate(this);
    }

    // Request the coupled
    public boolean requestCoupled(boolean coupled) {
        if (level == null || level.isClientSide || isRemoved()) {
            return false;
        }
        automationRequest = coupled;
        applyRequestedPowerState();
        setChanged();
        return true;
    }

    // Request the attach
    public boolean requestAttach() {
        return requestCoupled(true);
    }

    // Request the detach
    public boolean requestDetach() {
        return requestCoupled(false);
    }

    // Check if this is coupling requested
    public boolean isCouplingRequested() {
        BlockState state = getBlockState();
        return state.hasProperty(ShipCouplerBlock.POWERED)
                && state.getValue(ShipCouplerBlock.POWERED);
    }

    // Check if this is coupled
    public boolean isCoupled() {
        if (!hasPersistedLink()) {
            return false;
        }
        ShipCouplerBlockEntity partner = ShipCouplerService.resolvePartner(this, false);
        if (partner == null || !isReciprocalWith(partner)) {
            return false;
        }
        ShipCouplerBlockEntity owner = isCanonicalOwner(partner) ? this : partner;
        return SableYawJointApi.isValid(owner.yawJoint);
    }

    // Check if this has reciprocal requested link
    private boolean hasReciprocalRequestedLink() {
        if (!hasPersistedLink() || !isCouplingRequested()) {
            return false;
        }
        ShipCouplerBlockEntity partner = ShipCouplerService.resolvePartner(this, false);
        if (partner == null || !partner.isCouplingRequested() || !isReciprocalWith(partner)) {
            return false;
        }
        ShipCouplerBlockEntity owner = isCanonicalOwner(partner) ? this : partner;
        return SableYawJointApi.isValid(owner.yawJoint)
                || owner.jointUnavailableTicks <= JOINT_TOPOLOGY_GRACE_TICKS;
    }

    // Get the partner sublevel id
    public @Nullable UUID partnerSubLevelId() {
        return partnerSubLevelId;
    }

    // Get the partner position
    public @Nullable BlockPos partnerPosition() {
        return partnerPosition;
    }

    // Get the pairing token
    public @Nullable UUID pairingToken() {
        return pairingToken;
    }

    // Handle the redstone control
    public boolean useRedstoneControl() {
        if (level == null || level.isClientSide || isRemoved()) {
            return false;
        }
        automationRequest = null;
        loadRedstoneRecheckTicks = 0;
        redstonePowered = level.hasNeighborSignal(worldPosition);
        applyRequestedPowerState();
        setChanged();
        return true;
    }

    // Toggle the coupler redstone behaviour
    public boolean toggleRedstoneBehavior() {
        if (level == null || level.isClientSide || isRemoved()) {
            return redstoneInverted;
        }
        redstoneInverted = !redstoneInverted;
        applyRequestedPowerState();
        setChanged();
        notifyUpdate();
        return redstoneInverted;
    }

    // Check if the redstone is inverted
    public boolean isRedstoneInverted() {
        return redstoneInverted;
    }

    // Handle the redstone state changed event
    void onRedstoneStateChanged(boolean powered) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (shouldDeferRedstonePowerOff(powered)) {
            loadRedstoneRecheckTicks = LOAD_RETRY_INTERVAL;
            return;
        }
        loadRedstoneRecheckTicks = 0;
        if (powered != redstonePowered) {
            redstonePowered = powered;
            automationRequest = null;
            applyRequestedPowerState();
            setChanged();
        }
    }

    // Handle the facing changed event
    void onFacingChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        detachPair();
        scanCooldown = 0;
    }

    // Apply the requested power state
    private void applyRequestedPowerState() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        boolean requested = automationRequest == null
                ? redstonePowered != redstoneInverted : automationRequest;
        BlockState state = getBlockState();
        if (state.hasProperty(ShipCouplerBlock.POWERED)
                && state.getValue(ShipCouplerBlock.POWERED) != requested) {
            level.setBlock(worldPosition, state.setValue(ShipCouplerBlock.POWERED, requested),
                    Block.UPDATE_ALL);
        }
        if (!requested) {
            detachPair();
        } else {
            scanCooldown = 0;
            loadRetryCooldown = 0;
        }
    }

    // Recheck the redstone after load
    private void recheckRedstoneAfterLoad() {
        if (level == null || loadRedstoneRecheckTicks <= 0) {
            return;
        }
        loadRedstoneRecheckTicks--;
        if (loadRedstoneRecheckTicks > 0 || automationRequest != null) {
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (shouldDeferRedstonePowerOff(powered)) {
            loadRedstoneRecheckTicks = LOAD_RETRY_INTERVAL;
            return;
        }
        if (powered != redstonePowered) {
            redstonePowered = powered;
            applyRequestedPowerState();
            setChanged();
        }
    }

    // Check if this should defer redstone power off
    private boolean shouldDeferRedstonePowerOff(boolean powered) {
        if (!hasPersistedLink() || level == null) {
            return false;
        }
        if (automationRequest != null) {
            return loadRedstoneRecheckTicks > 0;
        }
        boolean wasRequested = redstonePowered != redstoneInverted;
        boolean willRequest = powered != redstoneInverted;
        if (willRequest || !wasRequested) {
            return false;
        }
        if (!level.hasChunkAt(worldPosition)) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (!level.hasChunkAt(worldPosition.relative(dir))) {
                return true;
            }
        }
        return false;
    }

    // Begin the assembly transfer
    void beginAssemblyTransfer() {
        assemblyTransferInProgress = true;
        detachPair();
    }

    // End the assembly transfer
    void endAssemblyTransfer() {
        assemblyTransferInProgress = false;
        removalHandled = false;
        scanCooldown = 0;
        loadRetryCooldown = 0;
    }

    // Handle the block removed event
    void onBlockRemoved() {
        if (removalHandled || level == null || level.isClientSide) {
            return;
        }
        removalHandled = true;
        detachPair();
    }

    // Destroy the ship coupler
    @Override
    public void destroy() {
        if (!assemblyTransferInProgress) {
            onBlockRemoved();
        }
        releaseJoint();
        super.destroy();
    }

    // Remove the ship coupler
    @Override
    public void remove() {
        ShipCouplerService.unregisterLoaded(this);
        releaseJoint();
        super.remove();
    }

    // Handle the chunk unloaded event
    @Override
    public void onChunkUnloaded() {
        ShipCouplerService.unregisterLoaded(this);
        super.onChunkUnloaded();
    }

    // Get the endpoint key
    @Nullable EndpointKey endpointKey() {
        ServerSubLevel owner = ownerSubLevel();
        return owner == null ? null : new EndpointKey(owner.getUniqueId(), worldPosition);
    }

    // Check if this can pair with the target
    boolean canPairWith(ShipCouplerBlockEntity candidate) {
        if (candidate == null || candidate == this || isRemoved() || candidate.isRemoved()
                || hasPersistedLink() || candidate.hasPersistedLink()
                || SableYawJointApi.isValid(yawJoint)
                || SableYawJointApi.isValid(candidate.yawJoint)
                || !isCouplingRequested() || !candidate.isCouplingRequested()) {
            return false;
        }
        ServerSubLevel firstBody = ownerSubLevel();
        ServerSubLevel secondBody = candidate.ownerSubLevel();
        if (firstBody == null || secondBody == null || firstBody == secondBody
                || firstBody.isRemoved() || secondBody.isRemoved()
                || firstBody.getLevel() != secondBody.getLevel()
                || hasOtherLiveCoupling(firstBody, secondBody, candidate)) {
            return false;
        }
        return geometryAllowsPair(candidate, false);
    }

    // Check if this can magnetically capture with the target
    boolean canMagneticallyCaptureWith(ShipCouplerBlockEntity candidate) {
        if (candidate == null || candidate == this || isRemoved() || candidate.isRemoved()
                || hasPersistedLink() || candidate.hasPersistedLink()
                || SableYawJointApi.isValid(yawJoint)
                || SableYawJointApi.isValid(candidate.yawJoint)
                || !isCouplingRequested() || !candidate.isCouplingRequested()) {
            return false;
        }
        ServerSubLevel firstBody = ownerSubLevel();
        ServerSubLevel secondBody = candidate.ownerSubLevel();
        if (firstBody == null || secondBody == null || firstBody == secondBody
                || firstBody.isRemoved() || secondBody.isRemoved()
                || firstBody.getLevel() != secondBody.getLevel()
                || hasOtherLiveCoupling(firstBody, secondBody, candidate)) {
            return false;
        }
        return magneticCaptureGeometryAllowsPair(candidate);
    }

    // Check if the coupler geometry allows the pair
    private boolean geometryAllowsPair(
            ShipCouplerBlockEntity candidate,
            boolean recoveringLink) {
        if (candidate == null || candidate == this || isRemoved() || candidate.isRemoved()) {
            return false;
        }
        Vec3 firstTip = worldTip();
        Vec3 secondTip = candidate.worldTip();
        Vec3 firstFacing = worldFacing();
        Vec3 secondFacing = candidate.worldFacing();
        Vec3 firstUp = worldUp();
        Vec3 secondUp = candidate.worldUp();
        if (firstTip == null || secondTip == null
                || firstFacing == null || secondFacing == null
                || firstUp == null || secondUp == null) {
            return false;
        }
        Vec3 separation = secondTip.subtract(firstTip);
        return separation.lengthSqr() <= ATTACH_RADIUS_SQUARED + 1.0E-8D
                && firstFacing.dot(secondFacing) <= (recoveringLink
                        ? RECOVERY_FACING_DOT_LIMIT : FACING_DOT_LIMIT)
                && firstUp.dot(secondUp) >= UP_DOT_LIMIT;
    }

    // Check if the magnetic capture geometry allows the pair
    private boolean magneticCaptureGeometryAllowsPair(ShipCouplerBlockEntity candidate) {
        Vec3 firstTip = worldTip();
        Vec3 secondTip = candidate.worldTip();
        Vec3 firstFacing = worldFacing();
        Vec3 secondFacing = candidate.worldFacing();
        Vec3 firstUp = worldUp();
        Vec3 secondUp = candidate.worldUp();
        if (firstTip == null || secondTip == null
                || firstFacing == null || secondFacing == null
                || firstUp == null || secondUp == null) {
            return false;
        }
        Vec3 separation = secondTip.subtract(firstTip);
        return separation.lengthSqr() <= MAGNETIC_CAPTURE_RADIUS_SQUARED + 1.0E-8D
                && firstFacing.dot(secondFacing) <= FACING_DOT_LIMIT
                && firstUp.dot(secondUp) >= UP_DOT_LIMIT;
    }

    // Check if this has other live coupling
    private boolean hasOtherLiveCoupling(ServerSubLevel firstBody,
                                         ServerSubLevel secondBody,
                                         ShipCouplerBlockEntity candidate) {
        UUID firstId = firstBody.getUniqueId();
        UUID secondId = secondBody.getUniqueId();
        return hasLiveCouplingTo(firstBody, secondId, this, candidate)
                || hasLiveCouplingTo(secondBody, firstId, this, candidate);
    }

    // Check if this has live coupling
    private static boolean hasLiveCouplingTo(ServerSubLevel owner,
                                             UUID targetId,
                                             ShipCouplerBlockEntity firstExcluded,
                                             ShipCouplerBlockEntity secondExcluded) {
        UUID ownerId = owner.getUniqueId();
        for (BlockEntity blockEntity : SubLevelBlockEntityCollector.getBlockEntities(owner)) {
            if (blockEntity instanceof ShipCouplerBlockEntity coupler
                    && coupler != firstExcluded && coupler != secondExcluded
                    && isLiveJointBetween(coupler.yawJoint, ownerId, targetId)) {
                return true;
            }
        }
        return false;
    }

    // Check for a live joint between the assemblies
    private static boolean isLiveJointBetween(@Nullable SableYawJointApi.Joint joint,
                                              UUID firstId,
                                              UUID secondId) {
        if (!SableYawJointApi.isValid(joint)) {
            return false;
        }
        return (Objects.equals(firstId, joint.firstSubLevelId())
                && Objects.equals(secondId, joint.secondSubLevelId()))
                || (Objects.equals(firstId, joint.secondSubLevelId())
                && Objects.equals(secondId, joint.firstSubLevelId()));
    }

    // Get the tip distance squared
    double tipDistanceSquared(ShipCouplerBlockEntity candidate) {
        Vec3 first = worldTip();
        Vec3 second = candidate == null ? null : candidate.worldTip();
        return first == null || second == null ? Double.MAX_VALUE : first.distanceToSqr(second);
    }

    // Update the physics
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        ShipCouplerBlockEntity candidate = magneticCaptureCandidate;
        if (candidate == null || !canMagneticallyCaptureWith(candidate)
                || ownerSubLevel() != subLevel || !isCanonicalOwner(candidate)) {
            return;
        }
        ServerSubLevel candidateBody = candidate.ownerSubLevel();
        if (candidateBody == null || handle == null || !handle.isValid()) {
            return;
        }
        Vec3 firstAnchor = localTip();
        Vec3 secondAnchor = candidate.localTip();
        SableMagneticCaptureApi.pullTogether(subLevel,
                new Vector3d(firstAnchor.x, firstAnchor.y, firstAnchor.z),
                candidateBody,
                new Vector3d(secondAnchor.x, secondAnchor.y, secondAnchor.z),
                MAGNETIC_CAPTURE_RADIUS, MAGNETIC_CAPTURE_MAX_ACCELERATION, timeStep);
    }

    // Maintain the pair
    private void maintainPair() {
        boolean requestLoad = loadRetryCooldown-- <= 0;
        if (requestLoad) {
            loadRetryCooldown = LOAD_RETRY_INTERVAL;
        }
        ShipCouplerBlockEntity partner = ShipCouplerService.resolvePartner(this, requestLoad);
        if (partner == null) {
            releaseJoint();
            syncAttachedState(true);
            if (!ShipCouplerService.partnerTargetIsLoaded(this)) {
                staleLinkTicks = 0;
                return;
            }
            staleLinkTicks = Math.min(STALE_LINK_GRACE_TICKS + 1, staleLinkTicks + 1);
            if (staleLinkTicks > STALE_LINK_GRACE_TICKS) {
                clearLocalLink();
            }
            return;
        }

        staleLinkTicks = 0;
        if (!isReciprocalWith(partner)) {
            clearLocalLink();
            return;
        }
        if (!partner.isCouplingRequested()) {
            detachPair();
            return;
        }

        boolean live;
        boolean retainAttachedCollider;
        if (isCanonicalOwner(partner)) {
            if (!SableYawJointApi.isValid(yawJoint)) {
                releaseJoint();
                if (yawJoint == null) {
                    ServerSubLevel firstBody = ownerSubLevel();
                    ServerSubLevel secondBody = partner.ownerSubLevel();
                    if (firstBody != null && secondBody != null
                            && hasOtherLiveCoupling(firstBody, secondBody, partner)) {
                        detachPair();
                        return;
                    }
                    yawJoint = createJoint(partner);
                }
            }
            if (SableYawJointApi.isValid(yawJoint)) {
                if (!yawJoint.contactsEnabled() && !yawJoint.setContactsEnabled(true)) {
                    releaseJoint();
                } else if (!updateYawServo(partner)) {
                    releaseJoint();
                }
            }
            live = SableYawJointApi.isValid(yawJoint);
            setJointUnavailableTicks(live ? 0 : Math.min(
                    JOINT_TOPOLOGY_GRACE_TICKS + 1, jointUnavailableTicks + 1));
            retainAttachedCollider = live
                    || jointUnavailableTicks <= JOINT_TOPOLOGY_GRACE_TICKS;
        } else {
            releaseJoint();
            live = SableYawJointApi.isValid(partner.yawJoint);
            retainAttachedCollider = live
                    || partner.jointUnavailableTicks <= JOINT_TOPOLOGY_GRACE_TICKS;
        }
        syncAttachedState(retainAttachedCollider);
        partner.syncAttachedState(retainAttachedCollider);
    }

    // Establish the coupler pair
    private boolean establishPair(ShipCouplerBlockEntity partner) {
        if (!canPairWith(partner) || !isCanonicalOwner(partner)) {
            return false;
        }
        EndpointKey ownKey = endpointKey();
        EndpointKey partnerKey = partner.endpointKey();
        if (ownKey == null || partnerKey == null) {
            return false;
        }
        SableYawJointApi.Joint created = createJoint(partner);
        if (!SableYawJointApi.isValid(created)) {
            if (created != null) {
                created.remove();
            }
            return false;
        }
        UUID token = UUID.randomUUID();
        yawJoint = created;
        partnerSubLevelId = partnerKey.subLevelId();
        partnerPosition = partnerKey.position();
        pairingToken = token;
        partner.partnerSubLevelId = ownKey.subLevelId();
        partner.partnerPosition = ownKey.position();
        partner.pairingToken = token;
        syncAttachedState(true);
        partner.syncAttachedState(true);
        markLinkChanged();
        partner.markLinkChanged();
        invalidateAssemblyTopology();
        created.wake();
        return true;
    }

    // Create the joint
    private @Nullable SableYawJointApi.Joint createJoint(ShipCouplerBlockEntity partner) {
        ServerSubLevel firstBody = ownerSubLevel();
        ServerSubLevel secondBody = partner == null ? null : partner.ownerSubLevel();
        if (firstBody == null || secondBody == null || firstBody == secondBody) {
            return null;
        }
        JointAnchors anchors = alignedJointAnchors(firstBody, secondBody, partner);
        if (anchors == null) {
            return null;
        }
        SableYawJointApi.Joint created;
        try {
            created = SableYawJointApi.create(
                    firstBody,
                    secondBody,
                    anchors.first(),
                    anchors.second(),
                    new Vector3d(0.0D, 1.0D, 0.0D),
                    new Vector3d(0.0D, 1.0D, 0.0D),
                    true);
        } catch (RuntimeException | LinkageError ignored) {
            anchors.captureMove().rollback();
            return null;
        }
        if (!SableYawJointApi.isValid(created)) {
            if (created != null) {
                created.remove();
            }
            anchors.captureMove().rollback();
            return null;
        }
        try {
            jointTargetYawRadians = desiredJointYawRad(partner);
            yawServoActive = false;
            setJointUnavailableTicks(0);
            yawServoInertiaTick = Long.MIN_VALUE;
            cachedYawServoInertia = 0.0D;
        } catch (RuntimeException | LinkageError ignored) {
            if (created.remove() || !created.isValid()) {
                anchors.captureMove().rollback();
                return null;
            }
        }
        return created;
    }

    // Update the yaw servo
    private boolean updateYawServo(ShipCouplerBlockEntity partner) {
        if (yawJoint == null) {
            return false;
        }
        double yaw = relativeYawRad(partner);
        if (!Double.isFinite(yaw)) {
            if (yawServoActive) {
                yawServoActive = !yawJoint.disableYawServo();
            }
            return true;
        }
        SableYawJointApi.ProgressiveYawResponse resp =
                SableYawJointApi.progressiveResponse(
                        yaw, FREE_YAW_RADIANS, MAXIMUM_YAW_RADIANS,
                        MAXIMUM_STIFFNESS,
                        MAXIMUM_DAMPING,
                        MAXIMUM_FORCE_PER_INERTIA);
        if (!resp.active()) {
            if (!yawServoActive) {
                return true;
            }
            if (!yawJoint.disableYawServo()) {
                return false;
            }
            yawServoActive = false;
            yawJoint.wake();
            return true;
        }
        ServerSubLevel firstBody = ownerSubLevel();
        ServerSubLevel secondBody = partner == null ? null : partner.ownerSubLevel();
        double inertia = yawServoInertia(firstBody, secondBody, partner);
        if (!yawJoint.setYawServo(
                jointTargetYawRadians,
                resp.stiffness(), resp.damping(),
                resp.maximumForce() * inertia)) {
            return false;
        }
        yawServoActive = true;
        yawJoint.wake();
        return true;
    }

    // Get the relative yaw rad
    private double relativeYawRad(ShipCouplerBlockEntity partner) {
        Vec3 firstFacing = worldFacing();
        Vec3 secondFacing = partner == null ? null : partner.worldFacing();
        Vec3 firstUp = worldUp();
        Vec3 secondUp = partner == null ? null : partner.worldUp();
        if (firstFacing == null || secondFacing == null
                || firstUp == null || secondUp == null) {
            return Double.NaN;
        }
        Vec3 up = firstUp.add(secondUp);
        if (up.lengthSqr() <= 1.0E-10D) {
            return Double.NaN;
        }
        up = up.normalize();
        Vec3 firstPlanar = firstFacing.subtract(up.scale(firstFacing.dot(up)));
        Vec3 secondPlanar = secondFacing.scale(-1.0D)
                .subtract(up.scale(-secondFacing.dot(up)));
        if (firstPlanar.lengthSqr() <= 1.0E-10D
                || secondPlanar.lengthSqr() <= 1.0E-10D) {
            return Double.NaN;
        }
        firstPlanar = firstPlanar.normalize();
        secondPlanar = secondPlanar.normalize();
        double sine = up.dot(firstPlanar.cross(secondPlanar));
        double cosine = Math.max(-1.0D, Math.min(1.0D,
                firstPlanar.dot(secondPlanar)));
        return Math.atan2(sine, cosine);
    }

    // Get the desired joint yaw rad
    private double desiredJointYawRad(ShipCouplerBlockEntity partner) {
        if (partner == null) {
            return 0.0D;
        }
        Vec3 firstFacing = Vec3.atLowerCornerOf(
                getBlockState().getValue(ShipCouplerBlock.FACING).getNormal());
        Vec3 opposedSecondFacing = Vec3.atLowerCornerOf(
                partner.getBlockState().getValue(ShipCouplerBlock.FACING).getNormal())
                .scale(-1.0D);
        // Sable measures body two from body one, with opposed coupler faces treated as straight ahead
        double sine = new Vec3(0.0D, 1.0D, 0.0D).dot(
                opposedSecondFacing.cross(firstFacing));
        double cosine = Math.max(-1.0D, Math.min(1.0D,
                opposedSecondFacing.dot(firstFacing)));
        double target = Math.atan2(sine, cosine);
        return Double.isFinite(target) ? target : 0.0D;
    }

    // Get the yaw servo inertia
    private double yawServoInertia(
            @Nullable ServerSubLevel firstBody,
            @Nullable ServerSubLevel secondBody,
            ShipCouplerBlockEntity partner) {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (cachedYawServoInertia > 0.0D && gameTime != Long.MIN_VALUE
                && gameTime >= yawServoInertiaTick
                && gameTime - yawServoInertiaTick < LOAD_RETRY_INTERVAL) {
            return cachedYawServoInertia;
        }
        double effective = assemblyYawServoInertia(firstBody, secondBody);
        if (!Double.isFinite(effective) || effective <= 0.0D) {
            double first = localYawInertia(firstBody, localTip());
            double second = localYawInertia(secondBody, partner.localTip());
            effective = reducedInertia(first, second);
        }
        cachedYawServoInertia = Math.max(
                MINIMUM_EFFECTIVE_SERVO_INERTIA, effective);
        yawServoInertiaTick = gameTime;
        return cachedYawServoInertia;
    }

    // Get the assembly yaw servo inertia
    private double assemblyYawServoInertia(
            @Nullable ServerSubLevel firstBody,
            @Nullable ServerSubLevel secondBody) {
        if (firstBody == null || secondBody == null) {
            return 0.0D;
        }
        SableAssemblyTopologyApi.Topology topology =
                ShipCouplerService.topology(firstBody);
        if (!topology.available()) {
            return 0.0D;
        }
        UUID firstId = firstBody.getUniqueId();
        UUID secondId = secondBody.getUniqueId();
        Set<UUID> firstSide = componentWithoutEdge(
                topology, firstId, firstId, secondId);
        Set<UUID> secondSide = componentWithoutEdge(
                topology, secondId, firstId, secondId);
        if (firstSide.isEmpty() || secondSide.isEmpty()
                || firstSide.stream().anyMatch(secondSide::contains)) {
            return 0.0D;
        }
        SableAssemblyDynamicsApi.Snapshot dynamics =
                SableAssemblyDynamicsApi.sample(topology);
        SableAssemblyDynamicsApi.Snapshot firstDynamics =
                dynamics.aggregate(firstSide);
        SableAssemblyDynamicsApi.Snapshot secondDynamics =
                dynamics.aggregate(secondSide);
        Vec3 yawAxis = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 anchor = localTip();
        return reducedInertia(
                yawInertiaAtAnchor(firstDynamics, anchor, yawAxis),
                yawInertiaAtAnchor(secondDynamics, anchor, yawAxis));
    }

    // Get the component without edge
    private static Set<UUID> componentWithoutEdge(
            SableAssemblyTopologyApi.Topology topology,
            UUID start,
            UUID excludedFirst,
            UUID excludedSecond) {
        Set<UUID> discovered = new HashSet<>();
        ArrayDeque<UUID> frontier = new ArrayDeque<>();
        frontier.add(start);
        while (!frontier.isEmpty()) {
            UUID current = frontier.removeFirst();
            if (!discovered.add(current)) {
                continue;
            }
            for (SableAssemblyTopologyApi.Edge edge : topology.edges()) {
                UUID other = edge.other(current);
                if (other == null || sameEdge(
                        edge, excludedFirst, excludedSecond)) {
                    continue;
                }
                if (!discovered.contains(other)) {
                    frontier.addLast(other);
                }
            }
        }
        return Set.copyOf(discovered);
    }

    // Check if this uses the same edge
    private static boolean sameEdge(
            SableAssemblyTopologyApi.Edge edge,
            UUID first,
            UUID second) {
        return Objects.equals(edge.firstSubLevelId(), first)
                && Objects.equals(edge.secondSubLevelId(), second)
                || Objects.equals(edge.firstSubLevelId(), second)
                && Objects.equals(edge.secondSubLevelId(), first);
    }

    // Get the yaw inertia at anchor
    private static double yawInertiaAtAnchor(
            SableAssemblyDynamicsApi.Snapshot dynamics,
            Vec3 anchor,
            Vec3 yawAxis) {
        if (dynamics == null || !dynamics.massAvailable()) {
            return 0.0D;
        }
        Vec3 transformed = dynamics.inertia().transform(yawAxis);
        double inertia = yawAxis.dot(transformed);
        Vec3 offset = dynamics.centerOfMass().subtract(anchor);
        double alongAxis = offset.dot(yawAxis);
        inertia += dynamics.mass() * Math.max(
                0.0D, offset.lengthSqr() - alongAxis * alongAxis);
        return Double.isFinite(inertia) && inertia > 0.0D ? inertia : 0.0D;
    }

    // Get the reduced inertia
    private static double reducedInertia(double first, double second) {
        if (first <= 1.0E-9D || second <= 1.0E-9D) {
            return 0.0D;
        }
        double smaller = Math.min(first, second);
        double larger = Math.max(first, second);
        double reduced = smaller / (1.0D + smaller / larger);
        return Double.isFinite(reduced) && reduced > 0.0D
                ? reduced : 0.0D;
    }

    // Get the local yaw inertia
    private static double localYawInertia(
            @Nullable ServerSubLevel body,
            Vec3 localAnchor) {
        if (body == null || body.getMassTracker() == null
                || body.getMassTracker().getInertiaTensor() == null) {
            return 0.0D;
        }
        Vector3d axis = new Vector3d(0.0D, 1.0D, 0.0D);
        Vector3d transformed = body.getMassTracker().getInertiaTensor()
                .transform(axis, new Vector3d());
        double inertia = transformed.dot(axis);
        double mass = body.getMassTracker().getMass();
        var center = body.getMassTracker().getCenterOfMass();
        if (Double.isFinite(mass) && mass > 0.0D && center != null
                && Double.isFinite(center.x()) && Double.isFinite(center.z())) {
            double offsetX = localAnchor.x - center.x();
            double offsetZ = localAnchor.z - center.z();
            inertia += mass * (offsetX * offsetX + offsetZ * offsetZ);
        }
        return Double.isFinite(inertia) && inertia > 0.0D ? inertia : 0.0D;
    }

    // Get the aligned joint anchors
    private @Nullable JointAnchors alignedJointAnchors(
            ServerSubLevel firstBody,
            ServerSubLevel secondBody,
            ShipCouplerBlockEntity partner) {
        CaptureMove captureMove = null;
        try {
            boolean recoveringLink = hasPersistedLink()
                    && isReciprocalWith(partner);
            if (!geometryAllowsPair(partner, recoveringLink)) {
                return null;
            }
            Vec3 firstTip = localTip();
            Vec3 secondTip = partner.localTip();
            Vector3d firstWorld = new Vector3d(firstTip.x, firstTip.y, firstTip.z);
            Vector3d secondWorld = new Vector3d(secondTip.x, secondTip.y, secondTip.z);
            firstBody.logicalPose().transformPosition(firstWorld);
            secondBody.logicalPose().transformPosition(secondWorld);
            Vector3d separation = new Vector3d(firstWorld).sub(secondWorld);
            if (!isFinite(separation)
                    || separation.lengthSquared() > ATTACH_RADIUS_SQUARED + 1.0E-8D) {
                return null;
            }
            captureMove = closeCouplerGap(firstBody, secondBody, separation);
            if (captureMove == null) {
                return null;
            }
            Vector3d firstLocalAnchor = new Vector3d(firstTip.x, firstTip.y, firstTip.z);
            Vector3d secondLocalAnchor = new Vector3d(secondTip.x, secondTip.y, secondTip.z);
            return new JointAnchors(firstLocalAnchor, secondLocalAnchor, captureMove);
        } catch (RuntimeException | LinkageError ignored) {
            if (captureMove != null) {
                captureMove.rollback();
            }
            return null;
        }
    }

    // Get the close coupler gap
    private static @Nullable CaptureMove closeCouplerGap(
            ServerSubLevel firstBody,
            ServerSubLevel secondBody,
            Vector3d firstMinusSecond) {
        if (!isFinite(firstMinusSecond)) {
            return null;
        }
        if (firstMinusSecond.lengthSquared() <= 1.0E-8D) {
            return CaptureMove.stationary();
        }
        List<ServerSubLevel> firstComponent = connectedComponent(firstBody);
        List<ServerSubLevel> secondComponent = connectedComponent(secondBody);
        if (firstComponent.isEmpty() || secondComponent.isEmpty()) {
            return null;
        }
        Set<UUID> firstIds = new HashSet<>();
        firstComponent.forEach(body -> firstIds.add(body.getUniqueId()));
        if (secondComponent.stream().anyMatch(body -> firstIds.contains(body.getUniqueId()))) {
            return null;
        }

        double firstMass = componentMass(firstComponent);
        double secondMass = componentMass(secondComponent);
        boolean movingFirstComponent = firstMass < secondMass;
        List<ServerSubLevel> moving = movingFirstComponent
                ? firstComponent : secondComponent;
        Vector3d movement = movingFirstComponent
                ? new Vector3d(firstMinusSecond).negate()
                : new Vector3d(firstMinusSecond);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(firstBody.getLevel());
        if (container == null || container.physicsSystem() == null) {
            return null;
        }
        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        if (pipeline == null) {
            return null;
        }
        List<CapturePose> originalPoses = moving.stream()
                .map(body -> new CapturePose(
                        body, new Vector3d(body.logicalPose().position()),
                        new Quaterniond(body.logicalPose().orientation())))
                .toList();
        CaptureMove captureMove = new CaptureMove(pipeline, originalPoses);
        try {
            for (CapturePose pose : originalPoses) {
                pipeline.teleport(pose.body(),
                        new Vector3d(pose.position()).add(movement), pose.orientation());
                pipeline.wakeUp(pose.body());
            }
        } catch (RuntimeException | LinkageError err) {
            captureMove.rollback();
            return null;
        }
        return captureMove;
    }

    // Get the connected component
    private static List<ServerSubLevel> connectedComponent(ServerSubLevel root) {
        Collection<SubLevel> connected = SubLevelHelper.getConnectedChain(root);
        List<ServerSubLevel> res = new ArrayList<>();
        for (SubLevel candidate : connected) {
            if (candidate instanceof ServerSubLevel body && !body.isRemoved()
                    && body.getLevel() == root.getLevel()) {
                res.add(body);
            }
        }
        if (res.stream().noneMatch(body -> body == root)) {
            res.add(root);
        }
        return res;
    }

    // Get the component mass
    private static double componentMass(List<ServerSubLevel> component) {
        double mass = 0.0D;
        for (ServerSubLevel body : component) {
            double bodyMass = body.getMassTracker() == null
                    ? 0.0D : body.getMassTracker().getMass();
            if (Double.isFinite(bodyMass) && bodyMass > 0.0D) {
                mass += bodyMass;
            }
        }
        return mass;
    }

    // Check if this is finite
    private static boolean isFinite(Vector3d val) {
        return Double.isFinite(val.x) && Double.isFinite(val.y)
                && Double.isFinite(val.z);
    }

    // Detach the pair
    private void detachPair() {
        if (!hasPersistedLink()) {
            releaseJoint();
            syncAttachedState(false);
            return;
        }
        UUID oldToken = pairingToken;
        EndpointKey ownKey = endpointKey();
        ShipCouplerBlockEntity partner = ShipCouplerService.resolvePartner(this, false);
        clearLocalLink();
        if (partner != null && oldToken != null && ownKey != null
                && oldToken.equals(partner.pairingToken)
                && ownKey.subLevelId().equals(partner.partnerSubLevelId)
                && ownKey.position().equals(partner.partnerPosition)) {
            partner.clearLocalLink();
        }
    }

    // Clear the local link
    private void clearLocalLink() {
        releaseJoint();
        partnerSubLevelId = null;
        partnerPosition = null;
        pairingToken = null;
        staleLinkTicks = 0;
        setJointUnavailableTicks(0);
        loadRetryCooldown = 0;
        syncAttachedState(false);
        markLinkChanged();
        invalidateAssemblyTopology();
    }

    // Invalidate the assembly topology
    private void invalidateAssemblyTopology() {
        ServerSubLevel owner = ownerSubLevel();
        if (owner != null) {
            SableAssemblyTopologyInvalidation.invalidate(owner.getLevel());
        }
    }

    // Set the joint unavailable ticks
    private void setJointUnavailableTicks(int ticks) {
        int normalized = Math.max(0, Math.min(JOINT_TOPOLOGY_GRACE_TICKS + 1, ticks));
        boolean wasTopologyProvider = jointUnavailableTicks <= JOINT_TOPOLOGY_GRACE_TICKS;
        boolean isTopologyProvider = normalized <= JOINT_TOPOLOGY_GRACE_TICKS;
        jointUnavailableTicks = normalized;
        if (wasTopologyProvider != isTopologyProvider) {
            invalidateAssemblyTopology();
        }
    }

    // Release the joint
    private void releaseJoint() {
        if (yawJoint != null) {
            SableYawJointApi.Joint current = yawJoint;
            if (current.remove() || !current.isValid()) {
                yawJoint = null;
                jointTargetYawRadians = 0.0D;
                yawServoActive = false;
                yawServoInertiaTick = Long.MIN_VALUE;
                cachedYawServoInertia = 0.0D;
            }
        }
    }

    // Check if this has a reciprocal link
    private boolean isReciprocalWith(ShipCouplerBlockEntity partner) {
        EndpointKey ownKey = endpointKey();
        EndpointKey partnerKey = partner == null ? null : partner.endpointKey();
        return ownKey != null && partnerKey != null && pairingToken != null
                && pairingToken.equals(partner.pairingToken)
                && partnerKey.subLevelId().equals(partnerSubLevelId)
                && partnerKey.position().equals(partnerPosition)
                && ownKey.subLevelId().equals(partner.partnerSubLevelId)
                && ownKey.position().equals(partner.partnerPosition);
    }

    // Check if this is canonical owner
    private boolean isCanonicalOwner(ShipCouplerBlockEntity partner) {
        EndpointKey ownKey = endpointKey();
        EndpointKey otherKey = partner == null ? null : partner.endpointKey();
        return ownKey != null && otherKey != null && ownKey.compareTo(otherKey) < 0;
    }

    // Check if this has persisted link
    private boolean hasPersistedLink() {
        return partnerSubLevelId != null && partnerPosition != null && pairingToken != null;
    }

    // Sync the attached state
    private void syncAttachedState(boolean attached) {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(ShipCouplerBlock.ATTACHED)
                && state.getValue(ShipCouplerBlock.ATTACHED) != attached) {
            level.setBlock(worldPosition, state.setValue(ShipCouplerBlock.ATTACHED, attached),
                    Block.UPDATE_CLIENTS);
        }
    }

    // Mark the link changed
    private void markLinkChanged() {
        setChanged();
        notifyUpdate();
    }

    // Get the local tip
    private Vec3 localTip() {
        Direction facing = getBlockState().getValue(ShipCouplerBlock.FACING);
        return worldPosition.getCenter().add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5D));
    }

    // Get the world tip
    private @Nullable Vec3 worldTip() {
        ServerSubLevel owner = ownerSubLevel();
        if (owner == null || level == null) {
            return null;
        }
        Vec3 resolved = SimulatedHelper.toContainingWorldPosition(owner, localTip());
        return resolved == null ? null : SimulatedHelper.projectOutOfSubLevels(level, resolved);
    }

    // Get the world facing
    private @Nullable Vec3 worldFacing() {
        ServerSubLevel owner = ownerSubLevel();
        if (owner == null) {
            return null;
        }
        Direction facing = getBlockState().getValue(ShipCouplerBlock.FACING);
        Vec3 resolved = SimulatedHelper.toContainingWorldDirection(
                owner, Vec3.atLowerCornerOf(facing.getNormal()));
        return resolved == null || resolved.lengthSqr() < 1.0E-10D ? null : resolved.normalize();
    }

    // Get the world up
    private @Nullable Vec3 worldUp() {
        ServerSubLevel owner = ownerSubLevel();
        if (owner == null) {
            return null;
        }
        Vec3 resolved = SimulatedHelper.toContainingWorldDirection(owner, new Vec3(0.0D, 1.0D, 0.0D));
        return resolved == null || resolved.lengthSqr() < 1.0E-10D ? null : resolved.normalize();
    }

    // Get the owner sublevel
    private @Nullable ServerSubLevel ownerSubLevel() {
        if (level == null) {
            return null;
        }
        try {
            SubLevel containing = Sable.HELPER.getContaining((BlockEntity) this);
            return containing instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()
                    ? serverSubLevel : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    // Write the ship coupler
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        SavedReference saved = remapForSchematic(partnerSubLevelId, partnerPosition);
        if (saved != null && pairingToken != null) {
            tag.putUUID("PartnerSubLevelId", saved.subLevelId());
            tag.put("PartnerPosition", NbtUtils.writeBlockPos(saved.position()));
            tag.putUUID("PairingToken", pairingToken);
        }
        tag.putBoolean("RedstonePowered", redstonePowered);
        tag.putBoolean("RedstoneInverted", redstoneInverted);
        tag.putBoolean("HasAutomationRequest", automationRequest != null);
        if (automationRequest != null) {
            tag.putBoolean("AutomationRequest", automationRequest);
        }
    }

    // Read the ship coupler
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (!clientPacket) {
            releaseJoint();
        }
        UUID savedSubLevelId = tag.hasUUID("PartnerSubLevelId")
                ? tag.getUUID("PartnerSubLevelId") : null;
        BlockPos savedPosition = tag.contains("PartnerPosition", 10)
                ? NbtUtils.readBlockPos(tag, "PartnerPosition").orElse(null) : null;
        SavedReference restored = remapPlacedReference(savedSubLevelId, savedPosition);
        partnerSubLevelId = restored == null ? null : restored.subLevelId();
        partnerPosition = restored == null ? null : restored.position();
        pairingToken = restored != null && tag.hasUUID("PairingToken")
                ? tag.getUUID("PairingToken") : null;
        redstoneInverted = tag.getBoolean("RedstoneInverted");
        redstonePowered = tag.contains("RedstonePowered")
                ? tag.getBoolean("RedstonePowered")
                : getBlockState().hasProperty(ShipCouplerBlock.POWERED)
                && getBlockState().getValue(ShipCouplerBlock.POWERED) != redstoneInverted;
        automationRequest = tag.getBoolean("HasAutomationRequest")
                ? tag.getBoolean("AutomationRequest") : null;
        scanCooldown = 0;
        loadRetryCooldown = 0;
        staleLinkTicks = 0;
        setJointUnavailableTicks(0);
    }

    // Remap the schematic
    private static @Nullable SavedReference remapForSchematic(
            @Nullable UUID subLevelId, @Nullable BlockPos pos) {
        if (subLevelId == null || pos == null) {
            return null;
        }
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null) {
            return new SavedReference(subLevelId, pos);
        }
        SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(subLevelId);
        if (mapping == null) {
            return null;
        }
        return new SavedReference(mapping.newUUID(), mapping.transform().apply(pos));
    }

    // Remap the placed reference
    private static @Nullable SavedReference remapPlacedReference(
            @Nullable UUID subLevelId, @Nullable BlockPos pos) {
        if (subLevelId == null || pos == null) {
            return null;
        }
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE) {
            return new SavedReference(subLevelId, pos);
        }
        SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(subLevelId);
        if (mapping == null) {
            return null;
        }
        return new SavedReference(mapping.newUUID(), mapping.transform().apply(pos));
    }

    // Get the connection dependencies
    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        return isCoupled() ? referencedSubLevels() : List.of();
    }

    // Get the loading dependencies
    @Override
    public Iterable<SubLevel> sable$getLoadingDependencies() {
        return referencedSubLevels();
    }

    // Get the sable assembly connections
    @Override
    public Iterable<SableAssemblyConnection> sableAssemblyConnections(ServerSubLevel owner) {
        EndpointKey key = endpointKey();
        if (!hasReciprocalRequestedLink() || key == null || owner == null
                || !Objects.equals(key.subLevelId(), owner.getUniqueId())) {
            return List.of();
        }
        return List.of(SableAssemblyConnection.carriageCoupler(partnerSubLevelId));
    }

    // Get the referenced sub levels
    private Iterable<SubLevel> referencedSubLevels() {
        if (level == null || partnerSubLevelId == null) {
            return List.of();
        }
        try {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            SubLevel partner = container == null ? null : container.getSubLevel(partnerSubLevelId);
            return partner == null || partner.isRemoved() ? List.of() : List.of(partner);
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    // Store the endpoint key
    public record EndpointKey(UUID subLevelId, BlockPos position) implements Comparable<EndpointKey> {
        // Initialize the endpoint key
        public EndpointKey {
            position = position.immutable();
        }

        // Compare coupler endpoint keys
        @Override
        public int compareTo(EndpointKey other) {
            int bySubLevel = subLevelId.toString().compareTo(other.subLevelId.toString());
            if (bySubLevel != 0) {
                return bySubLevel;
            }
            int byX = Integer.compare(position.getX(), other.position.getX());
            if (byX != 0) {
                return byX;
            }
            int byY = Integer.compare(position.getY(), other.position.getY());
            return byY != 0 ? byY : Integer.compare(position.getZ(), other.position.getZ());
        }
    }

    // Store the saved reference
    private record SavedReference(UUID subLevelId, BlockPos position) {
        // Initialize the saved reference
        private SavedReference {
            position = position.immutable();
        }
    }

    // Store the joint anchors
    private record JointAnchors(
            Vector3d first,
            Vector3d second,
            CaptureMove captureMove) {
    }

    // Store the capture pose
    private record CapturePose(
            ServerSubLevel body,
            Vector3d position,
            Quaterniond orientation) {
    }

    // Handle the capture move
    private static final class CaptureMove {
        // Pipeline
        private final @Nullable PhysicsPipeline pipeline;
        // Original poses
        private final List<CapturePose> originalPoses;
        // Tracks whether capture move is active
        private boolean active;

        // Initialize the capture move
        private CaptureMove(
                @Nullable PhysicsPipeline pipeline,
                List<CapturePose> originalPoses) {
            this.pipeline = pipeline;
            this.originalPoses = List.copyOf(originalPoses);
            active = pipeline != null && !originalPoses.isEmpty();
        }

        // Get the stationary
        private static CaptureMove stationary() {
            return new CaptureMove(null, List.of());
        }

        // Roll back the coupler link
        private boolean rollback() {
            if (!active) {
                return true;
            }
            boolean restored = true;
            for (CapturePose pose : originalPoses) {
                try {
                    pipeline.teleport(
                            pose.body(), new Vector3d(pose.position()),
                            new Quaterniond(pose.orientation()));
                    pipeline.wakeUp(pose.body());
                } catch (RuntimeException | LinkageError ignored) {
                    restored = false;
                }
            }
            if (restored) {
                active = false;
            }
            return restored;
        }
    }
}
