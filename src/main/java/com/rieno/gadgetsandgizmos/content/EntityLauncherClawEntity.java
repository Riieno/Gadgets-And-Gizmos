package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedRopeCompat;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.index.SimEntityDataSerializers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

// Carry the launcher claw through the world and report its final attachment back to the anchor
public class EntityLauncherClawEntity extends ThrowableProjectile {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double MAX_SAFE_COORD = 29_999_984.0;
    private static final double MAX_SAFE_LOCAL_COORD = 1_000_000.0;
    private static final double MIN_SAFE_Y = -2048.0;
    private static final double MAX_SAFE_Y = 2047.0;
    private static final double RETRACT_SPEED = 0.8;
    private static final double MIN_ROPE_LENGTH = 1.0;
    private static final double MAX_ROPE_LENGTH = 50.0;
    private static final EntityDataAccessor<Boolean> LAUNCHED_FROM_MAIN_HAND =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SYNC_ROPE_LENGTH =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SYNC_BLOCK_ANCHOR =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vec3> SYNC_BLOCK_ANCHOR_POS =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, SimEntityDataSerializers.VEC3);
    private static final EntityDataAccessor<Optional<UUID>> SYNC_BLOCK_ANCHOR_SUBLEVEL =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> SYNC_GRABBED_ENTITY =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Vec3> SYNC_GRABBED_ENTITY_OFFSET =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, SimEntityDataSerializers.VEC3);
    private static final EntityDataAccessor<Boolean> SYNC_REAL_ROPE_STRAND =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SYNC_MOUNTED_LAUNCHER =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vec3> SYNC_MOUNTED_LAUNCHER_ORIGIN =
            SynchedEntityData.defineId(EntityLauncherClawEntity.class, SimEntityDataSerializers.VEC3);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current grabbed entity id
    @Nullable
    private UUID grabbedEntityId;
    // Grabbed entity attach offset
    private Vec3 grabbedEntityAttachOffset = Vec3.ZERO;
    // Tracks whether entity launcher claw is retracting
    private boolean retracting;
    // Current traveled distance
    private double traveledDistance;
    // Maximum travel distance
    private double maxTravelDistance = 50.0;
    // Tracks whether launched from main hand is set
    private boolean launchedFromMainHand = true;
    // Current rope length
    private double ropeLength = -1.0;
    // Tracks whether entity launcher claw is reeling entity to owner
    private boolean reelingEntityToOwner;
    // Attached sub-level id
    @Nullable
    private UUID attachedSubLevelId;
    // Attached local anchor pos
    @Nullable
    private Vec3 attachedLocalAnchorPos;
    // Attached block position
    @Nullable
    private BlockPos attachedBlockPos;
    // Attached block face
    @Nullable
    private Direction attachedBlockFace;
    // Attached connector pos
    @Nullable
    private BlockPos attachedConnectorPos;
    // Attached connector sub-level id
    @Nullable
    private UUID attachedConnectorSubLevelId;
    // Tracks whether preserve attached connector on remove is set
    private boolean preserveAttachedConnectorOnRemove;
    // Tracks whether attached block requires block entity
    private boolean attachedBlockRequiresBlockEntity;
    // Attached anchor validation cooldown
    private int attachedAnchorValidationCooldown;
    // Tracks whether entity launcher claw is reeling owner to anchor
    private boolean reelingOwnerToAnchor;
    // Mounted launcher pos
    @Nullable
    private BlockPos mountedLauncherPos;
    // Mounted launcher sub-level id
    @Nullable
    private UUID mountedLauncherSubLevelId;
    // Current held rope start endpoint
    @Nullable
    private LauncherEndpointBlockEntity heldRopeStartEndpoint;
    // Current held rope end endpoint
    @Nullable
    private LauncherEndpointBlockEntity heldRopeEndEndpoint;
    // Current held rope start pos
    @Nullable
    private BlockPos heldRopeStartPos;
    // Current held rope end pos
    @Nullable
    private BlockPos heldRopeEndPos;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher claw entity
    public EntityLauncherClawEntity(EntityType<? extends EntityLauncherClawEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    // Initialize the entity launcher claw entity
    public EntityLauncherClawEntity(Level level, LivingEntity owner) {
        this(CTEntityTypes.LAUNCHED_CLAW.get(), level);
        setOwner(owner);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Define the synched data
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LAUNCHED_FROM_MAIN_HAND, true);
        builder.define(RETRACTING, false);
        builder.define(SYNC_ROPE_LENGTH, -1.0f);
        builder.define(SYNC_BLOCK_ANCHOR, false);
        builder.define(SYNC_BLOCK_ANCHOR_POS, Vec3.ZERO);
        builder.define(SYNC_BLOCK_ANCHOR_SUBLEVEL, Optional.empty());
        builder.define(SYNC_GRABBED_ENTITY, Optional.empty());
        builder.define(SYNC_GRABBED_ENTITY_OFFSET, Vec3.ZERO);
        builder.define(SYNC_REAL_ROPE_STRAND, false);
        builder.define(SYNC_MOUNTED_LAUNCHER, false);
        builder.define(SYNC_MOUNTED_LAUNCHER_ORIGIN, Vec3.ZERO);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the entity launcher claw entity
    @Override
    public void tick() {
        noPhysics = true;
        if (level().isClientSide() && isSyncedBlockAnchor()) {
            noPhysics = true;
            setDeltaMovement(Vec3.ZERO);
            setPos(getSyncedBlockAnchorPos());
        }

        if (grabbedEntityId != null || attachedLocalAnchorPos != null) {
            noPhysics = true;
        }

        // ------------------------------------FLIGHT COLLISION------------------------------------
        Vec3 old = position();
        FlightHit flightHit = findFlightHit(old);
        if (flightHit != null) {
            setPos(flightHit.worldHit() == null ? flightHit.hitLocation() : flightHit.worldHit());
            if (flightHit.entityHit() != null) {
                onHitEntity(flightHit.entityHit());
            } else if (flightHit.blockHit() != null) {
                Entity owner = getOwner();
                if (owner != null) {
                    attachToBlock(flightHit.blockHit(), owner, flightHit.subLevel(), flightHit.worldHit());
                }
            }
        } else {
            super.tick();
        }
        if (handleMountedLauncherClaw()) {
            return;
        }

        Entity owner = getOwner();
        if (owner == null || owner.isRemoved()) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }
        if (!level().isClientSide() && !isMountedLauncherClaw()) {
            EntityLauncherItem.registerActiveClaw(this);
        }
        // ------------------------------------CLIENT MOVEMENT------------------------------------
        if (level().isClientSide()) {
            if (isSyncedBlockAnchor()) {
                noPhysics = true;
                setDeltaMovement(Vec3.ZERO);
                setPos(getSyncedBlockAnchorPos());
            } else if (isSyncedGrabbedEntity()) {
                Entity grabbed = findGrabbedEntity();
                if (grabbed != null && !grabbed.isRemoved()) {
                    setDeltaMovement(Vec3.ZERO);
                    setPos(getGrabbedAttachmentPos(grabbed));
                }
            }
            return;
        }

        if (grabbedEntityId != null) {
            Entity grabbed = ((ServerLevel) level()).getEntity(grabbedEntityId);
            if (grabbed == null || grabbed.isRemoved()) {
                discard();
                return;
            }
            Vec3 ownerHold = getOwnerHoldPosition(owner);
            EntityLauncherItem.protectHeldEntityFall(grabbed);
            Vec3 grabbedAnchor = getGrabbedAttachmentPos(grabbed);
            if (ropeLength < 0.0) {
                setRopeLength(Math.max(1.0, Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(level(), grabbedAnchor, ownerHold))));
            }
            ensureRootEntityRope(owner, grabbed, ownerHold, grabbedAnchor);

            if (heldRopeStartEndpoint != null && !level().isClientSide()) {
                heldRopeStartEndpoint.setAttachmentPoint(ownerHold);
            }
            if (reelingEntityToOwner) {
                if (reelGrabbedEntityTowardOwner(owner, 50.0) && owner instanceof ServerPlayer player) {
                    EntityLauncherItem.onClawReturnedWithEntity(player, grabbedEntityId);
                    discard();
                }
                setPos(getGrabbedAttachmentPos(grabbed));
                return;
            }
            maintainGrabbedDistance(grabbed, ownerHold);
            setPos(getGrabbedAttachmentPos(grabbed));
            return;
        }

        if (attachedLocalAnchorPos != null) {
            tickBlockAnchor(owner);
            return;
        }

        if (retracting) {
            tickRetracting(owner);
            return;
        }

        traveledDistance += old.distanceTo(position());
    }

    // Find the flight hit
    @Nullable
    private FlightHit findFlightHit(Vec3 start) {
        if (retracting || grabbedEntityId != null || attachedLocalAnchorPos != null) {
            return null;
        }
        Vec3 delta = getDeltaMovement();
        if (delta.lengthSqr() < 1.0E-7) {
            return null;
        }
        Vec3 end = start.add(delta);
        BlockFlightHit blockHit = findNearestBlockFlightHit(start, end);
        double blockDistance = blockHit == null ? Double.MAX_VALUE : blockHit.distanceSqr();
        AABB entitySearch = getBoundingBox().expandTowards(delta).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this, start, end, entitySearch, this::canHitEntity, blockDistance);
        if (entityHit != null) {
            return new FlightHit(entityHit, null, null, null);
        }
        return blockHit == null ? null : new FlightHit(null, blockHit.hit(), blockHit.subLevel(), blockHit.worldHit());
    }

    // Find the nearest block flight hit
    @Nullable
    private BlockFlightHit findNearestBlockFlightHit(Vec3 start, Vec3 end) {
        BlockFlightHit nearest = traceRootBlock(start, end);
        AABB bounds = new AABB(start, end).inflate(1.0);
        for (Object subLevel : SimulatedHelper.getIntersectingSubLevels(level(), bounds)) {
            BlockFlightHit hit = traceSubLevelBlock(subLevel, start, end);
            if (hit != null && (nearest == null || hit.distanceSqr() < nearest.distanceSqr())) {
                nearest = hit;
            }
        }
        return nearest;
    }

    // Get the trace root block
    @Nullable
    private BlockFlightHit traceRootBlock(Vec3 start, Vec3 end) {
        if (!isSafeTrace(start, end, level())) {
            return null;
        }
        return traceBlocks(start, end, null, pos -> {
            if (!level().hasChunkAt(pos)) {
                return null;
            }
            return level().getBlockState(pos);
        });
    }

    // Get the trace sublevel block
    @Nullable
    private BlockFlightHit traceSubLevelBlock(@Nullable Object subLevel, Vec3 startWorld, Vec3 endWorld) {
        if (subLevel == null) {
            return null;
        }
        Level subLevelWorld = getSubLevelLevel(subLevel);
        if (subLevelWorld == null) {
            return null;
        }
        Vec3 startLocal = SimulatedHelper.toContainingLocalPosition(subLevel, startWorld);
        Vec3 endLocal = SimulatedHelper.toContainingLocalPosition(subLevel, endWorld);
        if (!isSafeSubLevelTrace(startLocal, endLocal, subLevelWorld)) {
            return null;
        }
        Object accessor = getEmbeddedLevelAccessor(subLevel);
        if (accessor == null) {
            return null;
        }
        BlockFlightHit localTrace = traceBlocks(startLocal, endLocal, subLevel,
                pos -> hasAccessorChunk(accessor, pos) ? getAccessorBlockState(accessor, pos) : null);
        if (localTrace == null) {
            return null;
        }
        BlockHitResult localHit = localTrace.hit();
        Vec3 worldHit = SimulatedHelper.toContainingWorldPosition(subLevel, localHit.getLocation());
        if (worldHit == null || !isSafePosition(worldHit)) {
            return null;
        }
        double distance = SimulatedHelper.distanceSquaredWithSubLevels(level(), startWorld, worldHit);
        return new BlockFlightHit(localHit, subLevel, worldHit, distance);
    }

    // Get the trace blocks
    @Nullable
    private BlockFlightHit traceBlocks(Vec3 start, Vec3 end, @Nullable Object subLevel, BlockStateLookup lookup) {
        int minX = Mth.floor(Math.min(start.x, end.x)) - 1;
        int minY = Mth.floor(Math.min(start.y, end.y)) - 1;
        int minZ = Mth.floor(Math.min(start.z, end.z)) - 1;
        int maxX = Mth.floor(Math.max(start.x, end.x)) + 1;
        int maxY = Mth.floor(Math.max(start.y, end.y)) + 1;
        int maxZ = Mth.floor(Math.max(start.z, end.z)) + 1;

        BlockHitResult nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos pos = candidate.immutable();
            BlockState state = lookup.get(pos);
            if (state == null || state.isAir() || state.canBeReplaced()) {
                continue;
            }
            BlockHitResult hit = Shapes.block().clip(start, end, pos);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            double distance = start.distanceToSqr(hit.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = hit;
            }
        }
        return nearest == null ? null : new BlockFlightHit(nearest, subLevel, nearest.getLocation(), nearestDistance);
    }

    // Check if this is safe trace
    private static boolean isSafeTrace(@Nullable Vec3 start, @Nullable Vec3 end, Level level) {
        if (!isSafePosition(start) || !isSafePosition(end)) {
            return false;
        }
        if (start.distanceToSqr(end) > 256.0) {
            return false;
        }
        double minY = Math.max(MIN_SAFE_Y, level.getMinBuildHeight() - 2.0);
        double maxY = Math.min(MAX_SAFE_Y, level.getMaxBuildHeight() + 2.0);
        return start.y >= minY && start.y <= maxY && end.y >= minY && end.y <= maxY;
    }

    // Check if this is safe sublevel trace
    private static boolean isSafeSubLevelTrace(@Nullable Vec3 start, @Nullable Vec3 end, Level level) {
        return isSafeTrace(start, end, level)
                && Math.abs(start.x) <= MAX_SAFE_LOCAL_COORD
                && Math.abs(start.z) <= MAX_SAFE_LOCAL_COORD
                && Math.abs(end.x) <= MAX_SAFE_LOCAL_COORD
                && Math.abs(end.z) <= MAX_SAFE_LOCAL_COORD;
    }

    // Check if this is a safe position
    private static boolean isSafePosition(@Nullable Vec3 pos) {
        return pos != null
                && Double.isFinite(pos.x)
                && Double.isFinite(pos.y)
                && Double.isFinite(pos.z)
                && Math.abs(pos.x) <= MAX_SAFE_COORD
                && Math.abs(pos.z) <= MAX_SAFE_COORD
                && pos.y >= MIN_SAFE_Y
                && pos.y <= MAX_SAFE_Y;
    }

    // Resolve the containing level
    @Nullable
    private static Level getSubLevelLevel(@Nullable Object subLevel) {
        return subLevel instanceof SubLevel sableSubLevel ? sableSubLevel.getLevel() : null;
    }

    // Expose the block state lookup
    @FunctionalInterface
    private interface BlockStateLookup {
        // Get the block state lookup value
        @Nullable
        BlockState get(BlockPos pos);
    }

    // Set the max travel distance
    public void setMaxTravelDistance(double maxTravelDistance) {
        this.maxTravelDistance = Math.max(1.0, maxTravelDistance);
        if (ropeLength < 0.0) {
            setRopeLength(this.maxTravelDistance);
        }
    }

    // Begin the retracting
    public void beginRetracting(Entity owner) {
        Vec3 retractStart = attachedLocalAnchorPos == null ? position() : getAttachedAnchorWorldPos();
        if (retractStart != null) {
            setPos(retractStart);
        }
        grabbedEntityId = null;
        grabbedEntityAttachOffset = Vec3.ZERO;
        syncGrabbedEntity(null, Vec3.ZERO);
        destroyHeldRope();
        attachedSubLevelId = null;
        attachedLocalAnchorPos = null;
        attachedBlockPos = null;
        attachedBlockFace = null;
        removeAttachedConnector();
        attachedConnectorPos = null;
        attachedConnectorSubLevelId = null;
        preserveAttachedConnectorOnRemove = false;
        attachedBlockRequiresBlockEntity = false;
        attachedAnchorValidationCooldown = 0;
        syncBlockAnchor(null, null);
        setRetracting(true);
        setRopeLength(-1.0);
        reelingEntityToOwner = false;
        reelingOwnerToAnchor = false;
        noPhysics = true;

        Vec3 toOwner = getProjectedOwnerHoldPos(owner).subtract(position());
        if (toOwner.lengthSqr() < 1.0) {
            discard();
            return;
        }
        setDeltaMovement(toOwner.normalize().scale(RETRACT_SPEED));
    }

    // Shorten the held rope
    public void shortenHeldRope(Entity owner, double amount) {
        if (attachedLocalAnchorPos != null) {
            Vec3 anchorWorld = getAttachedAnchorWorldPos();
            if (anchorWorld == null) {
                return;
            }
            ensureBlockRopeLength(owner, anchorWorld);
            reelingOwnerToAnchor = false;
            setRopeLength(Math.max(MIN_ROPE_LENGTH, Math.min(MAX_ROPE_LENGTH, ropeLength - amount)));
            maintainOwnerDistance(owner, anchorWorld);
            return;
        }
        if (grabbedEntityId == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity grabbed = serverLevel.getEntity(grabbedEntityId);
        if (grabbed == null || grabbed.isRemoved()) {
            return;
        }
        Vec3 ownerHold = getOwnerHoldPosition(owner);
        EntityLauncherItem.protectHeldEntityFall(grabbed);
        Vec3 grabbedAnchor = getGrabbedAttachmentPos(grabbed);
        double current = Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(level(), grabbedAnchor, ownerHold));
        if (ropeLength < 0.0) {
            setRopeLength(current);
        }
        reelingEntityToOwner = false;
        setRopeLength(Math.max(MIN_ROPE_LENGTH, Math.min(MAX_ROPE_LENGTH, ropeLength - amount)));
        maintainGrabbedDistance(grabbed, ownerHold);
    }

    // Extend the held rope
    public void extendHeldRope(Entity owner, double amount) {
        if (attachedLocalAnchorPos != null) {
            Vec3 anchorWorld = getAttachedAnchorWorldPos();
            if (anchorWorld == null) {
                return;
            }
            ensureBlockRopeLength(owner, anchorWorld);
            reelingOwnerToAnchor = false;
            setRopeLength(Math.max(MIN_ROPE_LENGTH, Math.min(MAX_ROPE_LENGTH, ropeLength + amount)));
            return;
        }
        if (grabbedEntityId == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity grabbed = serverLevel.getEntity(grabbedEntityId);
        if (grabbed == null || grabbed.isRemoved()) {
            return;
        }
        Vec3 ownerHold = getOwnerHoldPosition(owner);
        EntityLauncherItem.protectHeldEntityFall(grabbed);
        Vec3 grabbedAnchor = getGrabbedAttachmentPos(grabbed);
        double current = Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(level(), grabbedAnchor, ownerHold));
        if (ropeLength < 0.0) {
            setRopeLength(current);
        }
        reelingEntityToOwner = false;
        setRopeLength(Math.max(MIN_ROPE_LENGTH, Math.min(MAX_ROPE_LENGTH, ropeLength + amount)));
    }

    // Start the entity retract to owner
    public void startEntityRetractToOwner(Entity owner) {
        if (grabbedEntityId == null) {
            beginRetracting(owner);
            return;
        }
        reelingEntityToOwner = true;
        setRopeLength(0.35);
        setRetracting(false);
        noPhysics = true;
    }

    // Reel the grabbed entity toward its owner
    public boolean reelGrabbedEntityTowardOwner(Entity owner) {
        return reelGrabbedEntityTowardOwner(owner, 50.0);
    }

    // Reel the grabbed entity toward its owner
    public boolean reelGrabbedEntityTowardOwner(Entity owner, double amount) {
        if (grabbedEntityId == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Entity grabbed = serverLevel.getEntity(grabbedEntityId);
        if (grabbed == null || grabbed.isRemoved()) {
            return false;
        }
        Vec3 ownerHold = getOwnerHoldPosition(owner);
        EntityLauncherItem.protectHeldEntityFall(grabbed);
        Vec3 grabbedAnchor = getGrabbedAttachmentPos(grabbed);
        double current = Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(level(), grabbedAnchor, ownerHold));
        if (ropeLength < 0.0) {
            setRopeLength(current);
        }
        reelingEntityToOwner = true;
        setRopeLength(Math.max(0.35, Math.min(50.0, ropeLength - amount)));

        Vec3 toOwner = ownerHold.subtract(grabbedAnchor);
        double distance = toOwner.length();
        if (distance > 0.25) {
            double speed = Math.min(RETRACT_SPEED * 2.25, 0.35 + distance * 0.18);
            grabbed.setDeltaMovement(grabbed.getDeltaMovement().scale(0.1).add(toOwner.normalize().scale(speed)));
            grabbed.hurtMarked = true;
        }
        return distance <= 1.0;
    }

    // Reel the owner toward the anchor
    public boolean reelOwnerTowardAnchor(Entity owner, double amount) {
        if (attachedLocalAnchorPos == null) {
            return false;
        }
        Vec3 anchorWorld = getAttachedAnchorWorldPos();
        if (anchorWorld == null) {
            return false;
        }
        ensureBlockRopeLength(owner, anchorWorld);
        reelingOwnerToAnchor = true;

        double reelVelocity = getGrappleReelVelocity();
        setRopeLength(Math.max(0.75, Math.min(MAX_ROPE_LENGTH, ropeLength - reelVelocity)));
        return pullOwnerTowardAnchor(owner, anchorWorld, true);
    }

    // Stop the owner grapple
    public void stopOwnerGrapple() {
        reelingOwnerToAnchor = false;
    }

    // Check if this has grabbed entity
    public boolean hasGrabbedEntity() {
        return grabbedEntityId != null;
    }

    // Check if this has block anchor
    public boolean hasBlockAnchor() {
        return level().isClientSide() ? isSyncedBlockAnchor() : attachedLocalAnchorPos != null;
    }

    // Get the attached sublevel id
    @Nullable
    public UUID getAttachedSubLevelId() {
        return attachedSubLevelId;
    }

    // Get the attached anchor world position for transfer
    @Nullable
    public Vec3 getAttachedAnchorWorldPositionForTransfer() {
        return getAttachedAnchorWorldPos();
    }

    // Get the attached anchor local position for transfer
    @Nullable
    public Vec3 getAttachedAnchorLocalPositionForTransfer() {
        return attachedLocalAnchorPos;
    }

    // Get the attached block pos for transfer
    @Nullable
    public BlockPos getAttachedBlockPosForTransfer() {
        return attachedBlockPos;
    }

    // Get the attached block face for transfer
    @Nullable
    public Direction getAttachedBlockFaceForTransfer() {
        return attachedBlockFace;
    }

    // Get the attached connector pos for transfer
    @Nullable
    public BlockPos getAttachedConnectorPosForTransfer() {
        return attachedConnectorPos;
    }

    // Get the attached connector sublevel id for transfer
    @Nullable
    public UUID getAttachedConnectorSubLevelIdForTransfer() {
        return attachedConnectorSubLevelId;
    }

    // Preserve the attached connector for transfer
    public void preserveAttachedConnectorForTransfer() {
        preserveAttachedConnectorOnRemove = true;
    }

    // Get the grabbed entity id
    @Nullable
    public UUID getGrabbedEntityId() {
        return grabbedEntityId;
    }

    // Get the grabbed entity attach offset
    public Vec3 getGrabbedEntityAttachOffset() {
        return grabbedEntityAttachOffset;
    }

    // Get the render rope length
    public double getRenderRopeLength() {
        return level().isClientSide() ? entityData.get(SYNC_ROPE_LENGTH).doubleValue() : ropeLength;
    }

    // Check if this has real rope strand
    public boolean hasRealRopeStrand() {
        return entityData.get(SYNC_REAL_ROPE_STRAND);
    }

    // Handle the hit event
    @Override
    protected void onHit(HitResult res) {
        if (res.getType() == HitResult.Type.ENTITY) {
            onHitEntity((EntityHitResult) res);
        } else if (res.getType() == HitResult.Type.BLOCK) {
            onHitBlock((BlockHitResult) res);
        }
    }

    // Handle the hit block event
    @Override
    protected void onHitBlock(BlockHitResult res) {
        if (level().isClientSide() || retracting || grabbedEntityId != null || attachedLocalAnchorPos != null) {
            return;
        }
        Entity owner = getOwner();
        if (owner != null) {
            attachToBlock(res, owner);
        }
    }

    // Handle the hit entity event
    @Override
    protected void onHitEntity(EntityHitResult res) {
        if (retracting) {
            return;
        }
        Entity entity = res.getEntity();
        if (attachedLocalAnchorPos == null && entity != getOwner() && entity.isPickable()) {
            Vec3 attachOffset = getEntityAttachOffset(entity, res.getLocation());
            grabbedEntityId = entity.getUUID();
            grabbedEntityAttachOffset = attachOffset;
            syncGrabbedEntity(grabbedEntityId, grabbedEntityAttachOffset);
            setDeltaMovement(Vec3.ZERO);
            noPhysics = true;
            setRetracting(false);
            reelingEntityToOwner = false;
            Entity owner = getOwner();
            Vec3 ownerHold = owner == null ? position() : getOwnerHoldPosition(owner);
            EntityLauncherItem.protectHeldEntityFall(entity);
            Vec3 grabbedAnchor = getGrabbedAttachmentPos(entity);
            setRopeLength(Math.max(1.0, Math.sqrt(SimulatedHelper.distanceSquaredWithSubLevels(level(), grabbedAnchor, ownerHold))));
            ensureRootEntityRope(owner, entity, ownerHold, grabbedAnchor);
            setPos(grabbedAnchor);
            if (!level().isClientSide()) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
    }

    // Check if this can hit entity
    @Override
    protected boolean canHitEntity(Entity target) {
        return !retracting && target != getOwner() && super.canHitEntity(target);
    }

    // Check if this is pickable
    @Override
    public boolean isPickable() {
        return false;
    }

    // Check if this is pushable
    @Override
    public boolean isPushable() {
        return false;
    }

    // Get the default gravity
    @Override
    protected double getDefaultGravity() {
        return retracting || grabbedEntityId != null || attachedLocalAnchorPos != null ? 0.0 : 0.045;
    }

    // Get the attachment pos
    public Vec3 getAttachmentPos(float partialTick) {
        if (level().isClientSide() && isSyncedBlockAnchor()) {
            return getSyncedBlockAnchorPos();
        }
        if (attachedLocalAnchorPos != null) {
            return attachedLocalAnchorPos;
        }
        return getPosition(partialTick);
    }

    // Check if this is retracting
    public boolean isRetracting() {
        return entityData.get(RETRACTING);
    }

    // Set the launched from main hand
    public void setLaunchedFromMainHand(boolean launchedFromMainHand) {
        this.launchedFromMainHand = launchedFromMainHand;
        entityData.set(LAUNCHED_FROM_MAIN_HAND, launchedFromMainHand);
    }

    // Check if this is launched from main hand
    public boolean isLaunchedFromMainHand() {
        return entityData.get(LAUNCHED_FROM_MAIN_HAND);
    }

    // Set the mounted launcher
    public void setMountedLauncher(BlockPos pos, @Nullable UUID subLevelId, Vec3 origin) {
        mountedLauncherPos = pos == null ? null : pos.immutable();
        mountedLauncherSubLevelId = subLevelId;
        entityData.set(SYNC_MOUNTED_LAUNCHER, mountedLauncherPos != null);
        entityData.set(SYNC_MOUNTED_LAUNCHER_ORIGIN, origin == null ? Vec3.ZERO : origin);
    }

    // Check if this is a mounted launcher claw
    public boolean isMountedLauncherClaw() {
        return mountedLauncherPos != null || entityData.get(SYNC_MOUNTED_LAUNCHER);
    }

    // Get the mounted launcher origin
    public @Nullable Vec3 getMountedLauncherOrigin() {
        if (!isMountedLauncherClaw()) {
            return null;
        }
        if (!level().isClientSide() && mountedLauncherPos != null) {
            EntityLauncherAnchorBlockEntity anchor = getMountedLauncher();
            if (anchor != null) {
                return anchor.getAttachmentWorldPosition();
            }
        }
        return entityData.get(SYNC_MOUNTED_LAUNCHER_ORIGIN);
    }

    // Handle the mounted launcher claw
    private boolean handleMountedLauncherClaw() {
        if (level().isClientSide() || mountedLauncherPos == null) {
            return false;
        }
        EntityLauncherAnchorBlockEntity anchor = getMountedLauncher();
        if (anchor == null) {
            if (grabbedEntityId != null || attachedLocalAnchorPos != null || retracting) {
                discard();
                return true;
            }
            if (traveledDistance >= maxTravelDistance) {
                discard();
                return true;
            }
            return false;
        }

        anchor.registerMountedClaw(this);
        entityData.set(SYNC_MOUNTED_LAUNCHER_ORIGIN, anchor.getAttachmentWorldPosition());
        if (grabbedEntityId == null && attachedLocalAnchorPos == null) {
            if (traveledDistance >= maxTravelDistance) {
                discard();
                return true;
            }
            return false;
        }

        if (anchor.acceptMountedClawAttachment(this)) {
            discard();
        } else {
            Entity owner = getOwner();
            if (owner == null || owner.isRemoved()) {
                discard();
            } else {
                beginRetracting(owner);
            }
        }
        return true;
    }

    // Get the mounted launcher
    @Nullable
    private EntityLauncherAnchorBlockEntity getMountedLauncher() {
        if (mountedLauncherPos == null) {
            return null;
        }
        EntityLauncherAnchorBlockEntity exact = SimulatedHelper.findBlockEntity(level(),
                mountedLauncherSubLevelId, mountedLauncherPos, EntityLauncherAnchorBlockEntity.class);
        if (exact != null || mountedLauncherSubLevelId != null) {
            return exact;
        }
        return SimulatedHelper.findBlockEntityIncludingSubLevels(level(), mountedLauncherPos,
                EntityLauncherAnchorBlockEntity.class);
    }

    // Add the additional save data
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Retracting", retracting);
        tag.putDouble("TraveledDistance", traveledDistance);
        tag.putDouble("MaxTravelDistance", maxTravelDistance);
        tag.putBoolean("LaunchedFromMainHand", launchedFromMainHand);
        tag.putDouble("RopeLength", ropeLength);
        tag.putBoolean("ReelingEntityToOwner", reelingEntityToOwner);
        tag.putBoolean("ReelingOwnerToAnchor", reelingOwnerToAnchor);
        if (grabbedEntityId != null) {
            tag.putUUID("GrabbedEntity", grabbedEntityId);
            tag.putDouble("GrabbedAttachX", grabbedEntityAttachOffset.x);
            tag.putDouble("GrabbedAttachY", grabbedEntityAttachOffset.y);
            tag.putDouble("GrabbedAttachZ", grabbedEntityAttachOffset.z);
        }
        if (attachedSubLevelId != null) {
            tag.putUUID("AttachedSubLevel", attachedSubLevelId);
        }
        if (attachedLocalAnchorPos != null) {
            tag.putDouble("AttachedLocalX", attachedLocalAnchorPos.x);
            tag.putDouble("AttachedLocalY", attachedLocalAnchorPos.y);
            tag.putDouble("AttachedLocalZ", attachedLocalAnchorPos.z);
        }
        if (attachedBlockPos != null) {
            tag.putLong("AttachedBlockPos", attachedBlockPos.asLong());
        }
        if (attachedBlockFace != null) {
            tag.putString("AttachedBlockFace", attachedBlockFace.getName());
        }
        if (attachedConnectorPos != null) {
            tag.putLong("AttachedConnectorPos", attachedConnectorPos.asLong());
        }
        if (attachedConnectorSubLevelId != null) {
            tag.putUUID("AttachedConnectorSubLevel", attachedConnectorSubLevelId);
        }
        tag.putBoolean("AttachedBlockRequiresBlockEntity", attachedBlockRequiresBlockEntity);
        if (mountedLauncherPos != null) {
            tag.putLong("MountedLauncherPos", mountedLauncherPos.asLong());
        }
        if (mountedLauncherSubLevelId != null) {
            tag.putUUID("MountedLauncherSubLevel", mountedLauncherSubLevelId);
        }
    }

    // Read the additional save data
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        retracting = tag.getBoolean("Retracting");
        traveledDistance = tag.getDouble("TraveledDistance");
        maxTravelDistance = tag.contains("MaxTravelDistance") ? tag.getDouble("MaxTravelDistance") : 50.0;
        launchedFromMainHand = !tag.contains("LaunchedFromMainHand") || tag.getBoolean("LaunchedFromMainHand");
        entityData.set(LAUNCHED_FROM_MAIN_HAND, launchedFromMainHand);
        setRopeLength(tag.contains("RopeLength") ? tag.getDouble("RopeLength") : -1.0);
        reelingEntityToOwner = tag.getBoolean("ReelingEntityToOwner");
        reelingOwnerToAnchor = tag.getBoolean("ReelingOwnerToAnchor");
        grabbedEntityId = tag.hasUUID("GrabbedEntity") ? tag.getUUID("GrabbedEntity") : null;
        if (tag.contains("GrabbedAttachX") && tag.contains("GrabbedAttachY") && tag.contains("GrabbedAttachZ")) {
            grabbedEntityAttachOffset = new Vec3(
                    tag.getDouble("GrabbedAttachX"),
                    tag.getDouble("GrabbedAttachY"),
                    tag.getDouble("GrabbedAttachZ"));
        } else {
            grabbedEntityAttachOffset = Vec3.ZERO;
        }
        attachedSubLevelId = tag.hasUUID("AttachedSubLevel") ? tag.getUUID("AttachedSubLevel") : null;
        if (tag.contains("AttachedLocalX") && tag.contains("AttachedLocalY") && tag.contains("AttachedLocalZ")) {
            attachedLocalAnchorPos = new Vec3(
                    tag.getDouble("AttachedLocalX"),
                    tag.getDouble("AttachedLocalY"),
                    tag.getDouble("AttachedLocalZ"));
        } else {
            attachedLocalAnchorPos = null;
        }

        attachedLocalAnchorPos = sanitizeAnchorPosition(attachedLocalAnchorPos, level());
        attachedBlockPos = tag.contains("AttachedBlockPos") ? BlockPos.of(tag.getLong("AttachedBlockPos")) : null;
        attachedBlockFace = tag.contains("AttachedBlockFace")
                ? Direction.byName(tag.getString("AttachedBlockFace"))
                : null;
        attachedConnectorPos = tag.contains("AttachedConnectorPos") ? BlockPos.of(tag.getLong("AttachedConnectorPos")) : null;
        attachedConnectorSubLevelId = tag.hasUUID("AttachedConnectorSubLevel") ? tag.getUUID("AttachedConnectorSubLevel") : null;
        preserveAttachedConnectorOnRemove = false;
        attachedBlockRequiresBlockEntity = tag.getBoolean("AttachedBlockRequiresBlockEntity");
        mountedLauncherPos = tag.contains("MountedLauncherPos") ? BlockPos.of(tag.getLong("MountedLauncherPos")) : null;
        mountedLauncherSubLevelId = tag.hasUUID("MountedLauncherSubLevel") ? tag.getUUID("MountedLauncherSubLevel") : null;
        entityData.set(SYNC_MOUNTED_LAUNCHER, mountedLauncherPos != null);
        attachedAnchorValidationCooldown = attachedLocalAnchorPos == null ? 0 : 10;
        entityData.set(RETRACTING, retracting);
        if (attachedLocalAnchorPos != null) {
            noPhysics = true;
        }
        syncBlockAnchor(getAttachedAnchorWorldPos(), attachedSubLevelId);
        syncGrabbedEntity(grabbedEntityId, grabbedEntityAttachOffset);
        entityData.set(SYNC_REAL_ROPE_STRAND, false);
    }

    // Set the retracting
    private void setRetracting(boolean retracting) {
        this.retracting = retracting;
        entityData.set(RETRACTING, retracting);
    }

    // Set the rope length
    private void setRopeLength(double ropeLength) {
        this.ropeLength = ropeLength;
        entityData.set(SYNC_ROPE_LENGTH, (float) ropeLength);
    }

    // Check if this is synced block anchor
    private boolean isSyncedBlockAnchor() {
        return entityData.get(SYNC_BLOCK_ANCHOR);
    }

    // Get the synced block anchor pos
    private Vec3 getSyncedBlockAnchorPos() {
        return entityData.get(SYNC_BLOCK_ANCHOR_POS);
    }

    // Check if this is synced grabbed entity
    private boolean isSyncedGrabbedEntity() {
        return entityData.get(SYNC_GRABBED_ENTITY).isPresent();
    }

    // Get the synced grabbed entity id
    @Nullable
    private UUID getSyncedGrabbedEntityId() {
        return entityData.get(SYNC_GRABBED_ENTITY).orElse(null);
    }

    // Get the synced grabbed entity offset
    private Vec3 getSyncedGrabbedEntityOffset() {
        return entityData.get(SYNC_GRABBED_ENTITY_OFFSET);
    }

    // Sync the block anchor
    private void syncBlockAnchor(@Nullable Vec3 worldAnchorPos, @Nullable UUID subLevelId) {
        entityData.set(SYNC_BLOCK_ANCHOR, worldAnchorPos != null);
        entityData.set(SYNC_BLOCK_ANCHOR_POS, worldAnchorPos == null ? Vec3.ZERO : worldAnchorPos);
        entityData.set(SYNC_BLOCK_ANCHOR_SUBLEVEL, Optional.ofNullable(subLevelId));
    }

    // Sync the grabbed entity
    private void syncGrabbedEntity(@Nullable UUID entityId, Vec3 attachOffset) {
        entityData.set(SYNC_GRABBED_ENTITY, Optional.ofNullable(entityId));
        entityData.set(SYNC_GRABBED_ENTITY_OFFSET, attachOffset == null ? Vec3.ZERO : attachOffset);
    }

    // Set the real rope strand active
    private void setRealRopeStrandActive(boolean active) {
        entityData.set(SYNC_REAL_ROPE_STRAND, active);
    }

    // Get the owner hold position
    private Vec3 getOwnerHoldPosition(Entity owner) {
        Vec3 mountedOrigin = getMountedLauncherOrigin();
        if (mountedOrigin != null) {
            return mountedOrigin;
        }
        if (owner instanceof net.minecraft.world.entity.player.Player player) {
            Vec3 mountedMuzzle = EntityLauncherAnchorBlockEntity.getMountedLauncherMuzzle(player);
            if (mountedMuzzle == null && player.isPassenger() && player.getVehicle() != null
                    && player.level().getBlockEntity(player.getVehicle().blockPosition()) instanceof EntityLauncherAnchorBlockEntity anchor) {
                mountedMuzzle = anchor.getAttachmentWorldPosition();
            }
            if (mountedMuzzle != null) {
                return mountedMuzzle;
            }
            return EntityLauncherItem.handRopeWorldPos(player,
                    launchedFromMainHand ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND,
                    1.0f);
        }
        return owner.position().add(0.0, owner.getBbHeight() * 0.55, 0.0);
    }

    // Get the projected owner hold pos
    private Vec3 getProjectedOwnerHoldPos(Entity owner) {
        return SimulatedHelper.projectOutOfSubLevels(level(), getOwnerHoldPosition(owner));
    }

    // Attach the block
    private void attachToBlock(BlockHitResult res, Entity owner) {
        Vec3 rawHit = res.getLocation();
        Object subLevel = SimulatedHelper.getContainingSubLevel(level(), rawHit);
        Vec3 worldHit = subLevel == null
                ? SimulatedHelper.projectOutOfSubLevels(level(), rawHit)
                : SimulatedHelper.toContainingWorldPosition(subLevel, rawHit);
        attachToBlock(res, owner, subLevel, worldHit == null ? rawHit : worldHit);
    }

    // Attach the block
    private void attachToBlock(BlockHitResult res, Entity owner, @Nullable Object subLevel, Vec3 worldHit) {
        Vec3 rawHit = res.getLocation();
        if (worldHit == null) {
            worldHit = subLevel == null ? rawHit : SimulatedHelper.toContainingWorldPosition(subLevel, rawHit);
        }
        worldHit = SimulatedHelper.projectOutOfSubLevels(level(), worldHit == null ? rawHit : worldHit);
        Direction hitFace = res.getDirection();
        Vec3 faceNormal = Vec3.atLowerCornerOf(hitFace.getNormal());
        Vec3 localHit = subLevel == null ? worldHit : rawHit;

        Vec3 localAnchor = localHit.add(faceNormal.scale(0.12D));
        BlockPos localBlockPos = BlockPos.containing(localHit.subtract(faceNormal.scale(1.0E-4D)));
        Vec3 worldAnchor = subLevel == null
                ? localAnchor
                : SimulatedHelper.toContainingWorldPosition(subLevel, localAnchor);
        if (worldAnchor != null) {
            worldHit = SimulatedHelper.projectOutOfSubLevels(level(), worldAnchor);
        }
        localAnchor = sanitizeAnchorPosition(localAnchor, level());
        worldHit = sanitizeAnchorPosition(worldHit, level());
        if (localAnchor == null || worldHit == null) {
            beginRetracting(owner);
            return;
        }

        attachedSubLevelId = SimulatedHelper.getSubLevelId(subLevel);
        attachedLocalAnchorPos = localAnchor;
        attachedBlockPos = subLevel == null ? res.getBlockPos().immutable() : localBlockPos.immutable();
        attachedBlockFace = hitFace;

        BlockEntity attachedBlockEntity = resolveAttachedBlockEntity(subLevel, attachedBlockPos);
        if (attachedBlockEntity instanceof RopeConnectorBlockEntity
                && resolveHolderBehavior(attachedBlockEntity) != null) {
            attachedConnectorPos = attachedBlockPos;
            attachedConnectorSubLevelId = attachedSubLevelId;
        } else {

            attachedConnectorPos = null;
            attachedConnectorSubLevelId = null;
        }
        preserveAttachedConnectorOnRemove = false;
        attachedBlockRequiresBlockEntity = false;
        attachedAnchorValidationCooldown = 10;
        syncBlockAnchor(worldHit, attachedSubLevelId);
        grabbedEntityId = null;
        grabbedEntityAttachOffset = Vec3.ZERO;
        syncGrabbedEntity(null, Vec3.ZERO);
        reelingEntityToOwner = false;
        reelingOwnerToAnchor = false;
        setRetracting(false);
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
        Vec3 anchorWorld = getAttachedAnchorWorldPos();
        syncBlockAnchor(anchorWorld, attachedSubLevelId);
        setPos(anchorWorld == null ? worldHit : anchorWorld);
        Vec3 ownerHold = getProjectedOwnerHoldPos(owner);
        setRopeLength(Math.max(MIN_ROPE_LENGTH, anchorWorld == null ? ownerHold.distanceTo(worldHit) : ownerHold.distanceTo(anchorWorld)));
        Vec3 soundPos = anchorWorld == null ? worldHit : anchorWorld;
        level().playSound(null, soundPos.x, soundPos.y, soundPos.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.8f, 0.95f);
    }

    // Store the flight hit
    private record FlightHit(@Nullable EntityHitResult entityHit, @Nullable BlockHitResult blockHit,
                             @Nullable Object subLevel, @Nullable Vec3 worldHit) {
        // Get the hit location
        private Vec3 hitLocation() {
            if (entityHit != null) {
                return entityHit.getLocation();
            }
            return blockHit == null ? Vec3.ZERO : blockHit.getLocation();
        }
    }

    // Store the block flight hit
    private record BlockFlightHit(BlockHitResult hit, @Nullable Object subLevel, Vec3 worldHit, double distanceSqr) {
    }

    // Sanitize the anchor position
    private static @Nullable Vec3 sanitizeAnchorPosition(@Nullable Vec3 pos, @Nullable Level level) {
        if (pos == null) {
            return null;
        }
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) {
            return null;
        }
        double minY = MIN_SAFE_Y;
        double maxY = MAX_SAFE_Y;
        if (level != null) {
            minY = Math.max(minY, level.getMinBuildHeight());
            maxY = Math.min(maxY, level.getMaxBuildHeight() - 1.0);
        }
        double x = Mth.clamp(pos.x, -MAX_SAFE_COORD, MAX_SAFE_COORD);
        double y = Mth.clamp(pos.y, minY, maxY);
        double z = Mth.clamp(pos.z, -MAX_SAFE_COORD, MAX_SAFE_COORD);
        return new Vec3(x, y, z);
    }

    // Update the block anchor
    private void tickBlockAnchor(Entity owner) {
        noPhysics = true;
        EntityLauncherItem.protectGrappleFall(owner);
        setRetracting(false);
        setDeltaMovement(Vec3.ZERO);
        if (attachedAnchorValidationCooldown > 0) {
            attachedAnchorValidationCooldown--;
        } else if (!isAttachedAnchorStillPresent()) {
            beginRetracting(owner);
            return;
        }
        Vec3 anchorWorld = getAttachedAnchorWorldPos();
        if (anchorWorld == null) {
            beginRetracting(owner);
            return;
        }
        syncBlockAnchor(anchorWorld, attachedSubLevelId);
        setPos(anchorWorld);
        ensureBlockRopeLength(owner, anchorWorld);
        if (attachedSubLevelId == null) {
            ensureRootBlockRope(owner, anchorWorld);
        }
        if (reelingOwnerToAnchor) {
            pullOwnerTowardAnchor(owner, anchorWorld, true);
        } else {
            maintainOwnerDistance(owner, anchorWorld);
        }
    }

    // Ensure the root block rope
    private void ensureRootBlockRope(Entity owner, Vec3 anchorWorld) {
        if (!(level() instanceof ServerLevel serverLevel) || attachedSubLevelId != null) {
            return;
        }
        Vec3 ownerHold = getProjectedOwnerHoldPos(owner);
        BlockPos targetPos = attachedBlockPos == null
                ? virtualEndpointPos(anchorWorld, 1)
                : attachedBlockPos.relative(attachedBlockFace == null ? Direction.UP : attachedBlockFace);
        ensureRootHeldRope(serverLevel, ownerHold, anchorWorld, targetPos);
    }

    // Ensure the root entity rope
    private void ensureRootEntityRope(Entity owner, Entity grabbed, Vec3 ownerHold, Vec3 grabbedAnchor) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (SimulatedHelper.getContainingSubLevel(level(), ownerHold) != null
                || SimulatedHelper.getContainingSubLevel(level(), grabbedAnchor) != null) {
            destroyHeldRope();
            return;
        }
        ensureRootHeldRope(serverLevel, ownerHold, grabbedAnchor, virtualEndpointPos(grabbed.position(), 1));
    }

    // Ensure the root held rope
    private void ensureRootHeldRope(ServerLevel serverLevel, Vec3 ownerHold, Vec3 targetAnchor, BlockPos targetPos) {
        if (heldRopeStartEndpoint == null || heldRopeEndEndpoint == null) {
            destroyHeldRope();
            heldRopeStartPos = virtualEndpointPos(ownerHold, 0);
            heldRopeEndPos = targetPos.immutable();
            heldRopeStartEndpoint = EntityLauncherVirtualRopeEndpoints.getOrCreate(serverLevel, null, heldRopeStartPos, ownerHold);
            heldRopeEndEndpoint = EntityLauncherVirtualRopeEndpoints.getOrCreate(serverLevel, null, heldRopeEndPos, targetAnchor);
        }

        heldRopeStartEndpoint.setAttachmentPoint(ownerHold);
        heldRopeEndEndpoint.setAttachmentPoint(targetAnchor);

        RopeStrandHolderBehavior startHolder = heldRopeStartEndpoint.getRopeHolder();
        RopeStrandHolderBehavior endHolder = heldRopeEndEndpoint.getRopeHolder();
        if (startHolder == null || endHolder == null) {
            destroyHeldRope();
            return;
        }

        ServerRopeStrand strand = startHolder.getOwnedStrand();
        if (strand == null && !endHolder.isAttached()) {
            SimulatedRopeCompat.createRope(startHolder, endHolder, false);
            strand = startHolder.getOwnedStrand();
        }
        if (strand != null) {
            updateVirtualRopeAttachment(strand, RopeHandle.AttachmentPoint.START, heldRopeStartEndpoint);
            updateVirtualRopeAttachment(strand, RopeHandle.AttachmentPoint.END, heldRopeEndEndpoint);
            startHolder.tick();
            setRealRopeStrandActive(true);
        } else {
            setRealRopeStrandActive(false);
        }
    }

    // Update the virtual rope attachment
    private static void updateVirtualRopeAttachment(ServerRopeStrand strand, RopeHandle.AttachmentPoint point,
                                                    LauncherEndpointBlockEntity endpoint) {
        RopeStrandHolderBehavior holder = endpoint.getRopeHolder();
        if (holder == null) {
            return;
        }
        strand.setAttachment(point, JOMLConversion.toJOML((Position) holder.getAttachmentPoint()), null);
    }

    // Get the virtual endpoint pos
    private BlockPos virtualEndpointPos(Vec3 worldPos, int salt) {
        int y = level().getMinBuildHeight() - 16 - Math.floorMod(getId() * 2 + salt, 48);
        return new BlockPos(Mth.floor(worldPos.x), y, Mth.floor(worldPos.z));
    }

    // Destroy the held rope
    private void destroyHeldRope() {
        if (level().isClientSide()) {
            return;
        }
        if (heldRopeStartEndpoint != null) {
            RopeStrandHolderBehavior holder = heldRopeStartEndpoint.getRopeHolder();
            if (holder != null) {
                SimulatedRopeCompat.destroyRope(holder, null, position(), false);
                holder.detachRope();
            }
        }
        if (level() instanceof ServerLevel serverLevel) {
            if (heldRopeStartPos != null) {
                EntityLauncherVirtualRopeEndpoints.remove(serverLevel, null, heldRopeStartPos);
            }
            if (heldRopeEndPos != null) {
                EntityLauncherVirtualRopeEndpoints.remove(serverLevel, null, heldRopeEndPos);
            }
        }
        heldRopeStartEndpoint = null;
        heldRopeEndEndpoint = null;
        heldRopeStartPos = null;
        heldRopeEndPos = null;
        setRealRopeStrandActive(false);
    }

    // Check if the attached anchor still is present
    private boolean isAttachedAnchorStillPresent() {
        if (attachedConnectorPos != null) {
            return getAttachedConnectorBlockEntity() != null;
        }
        if (attachedLocalAnchorPos == null) {
            return false;
        }
        if (attachedBlockPos == null) {
            return true;
        }
        if (attachedSubLevelId == null) {
            if (!level().hasChunkAt(attachedBlockPos)) {
                return true;
            }
            return !level().getBlockState(attachedBlockPos).isAir();
        }
        Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level(), attachedSubLevelId);
        if (subLevel == null) {
            return false;
        }

        return true;
    }

    // Update the retracting
    private void tickRetracting(Entity owner) {
        noPhysics = true;
        Vec3 ownerHold = getProjectedOwnerHoldPos(owner);
        Vec3 clawWorld = SimulatedHelper.projectOutOfSubLevels(level(), position());
        Vec3 toOwner = ownerHold.subtract(clawWorld);
        if (toOwner.lengthSqr() < 1.0) {
            discard();
            return;
        }

        Vec3 globalStep = toOwner.normalize().scale(RETRACT_SPEED);
        Vec3 newPos = clawWorld.add(globalStep);
        setPos(newPos);
        setDeltaMovement(Vec3.ZERO);
    }

    // Get the attached anchor world pos
    @Nullable
    private Vec3 getAttachedAnchorWorldPos() {
        BlockEntity connector = getAttachedConnectorBlockEntity();
        RopeStrandHolderBehavior connectorHolder = resolveHolderBehavior(connector);
        if (connectorHolder != null) {
            Vec3 connectorAnchor = connectorHolder.getAttachmentPoint();
            if (attachedConnectorSubLevelId != null) {

                Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level(), attachedConnectorSubLevelId);
                Vec3 world = SimulatedHelper.toContainingWorldPosition(subLevel, connectorAnchor);
                return world == null ? null : SimulatedHelper.projectOutOfSubLevels(level(), world);
            }
            return SimulatedHelper.projectOutOfSubLevels(level(), connectorAnchor);
        }
        if (attachedLocalAnchorPos == null) {
            return null;
        }
        if (attachedSubLevelId != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level(), attachedSubLevelId);
            if (subLevel == null) {
                return null;
            }
            Vec3 world = SimulatedHelper.toContainingWorldPosition(subLevel, attachedLocalAnchorPos);
            return world == null ? null : SimulatedHelper.projectOutOfSubLevels(level(), world);
        }
        return SimulatedHelper.projectOutOfSubLevels(level(), attachedLocalAnchorPos);
    }

    // Get the attached connector block entity
    @Nullable
    private BlockEntity getAttachedConnectorBlockEntity() {
        if (attachedConnectorPos == null) {
            return null;
        }
        return SimulatedHelper.findBlockEntity(level(), attachedConnectorSubLevelId, attachedConnectorPos);
    }

    // Remove the attached connector
    private void removeAttachedConnector() {
        if (preserveAttachedConnectorOnRemove || level().isClientSide() || attachedConnectorPos == null) {
            return;
        }
        if (attachedConnectorSubLevelId != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level(), attachedConnectorSubLevelId);
            Object accessor = getEmbeddedLevelAccessor(subLevel);
            BlockEntity blockEntity = getAccessorBlockEntity(accessor, attachedConnectorPos);
            if (blockEntity instanceof LauncherEndpointBlockEntity endpoint && endpoint.isFree()) {
                setAccessorBlock(accessor, attachedConnectorPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
            return;
        }
        if (!level().hasChunkAt(attachedConnectorPos)) {
            return;
        }
        BlockEntity blockEntity = level().getBlockEntity(attachedConnectorPos);
        if (blockEntity instanceof LauncherEndpointBlockEntity endpoint && endpoint.isFree()) {
            level().removeBlock(attachedConnectorPos, false);
        }
    }

    // Resolve the attached block entity
    private @Nullable BlockEntity resolveAttachedBlockEntity(@Nullable Object subLevel, @Nullable BlockPos localBlockPos) {
        if (localBlockPos == null) {
            return null;
        }
        if (subLevel != null) {
            return SimulatedHelper.findBlockEntityInSubLevel(subLevel, localBlockPos, BlockEntity.class);
        }
        if (!level().hasChunkAt(localBlockPos)) {
            return null;
        }
        return level().getBlockEntity(localBlockPos);
    }

    // Resolve the holder behavior
    private static @Nullable RopeStrandHolderBehavior resolveHolderBehavior(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        return (RopeStrandHolderBehavior) smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
    }

    // Get the embedded level accessor
    private static @Nullable Object getEmbeddedLevelAccessor(@Nullable Object subLevel) {
        if (subLevel == null) {
            return null;
        }
        try {
            Object plot = subLevel.getClass().getMethod("getPlot").invoke(subLevel);
            return plot == null ? null : plot.getClass().getMethod("getEmbeddedLevelAccessor").invoke(plot);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the accessor block entity
    private static @Nullable BlockEntity getAccessorBlockEntity(@Nullable Object accessor, BlockPos pos) {
        if (accessor == null) {
            return null;
        }
        try {
            Object res = accessor.getClass().getMethod("getBlockEntity", BlockPos.class).invoke(accessor, pos);
            return res instanceof BlockEntity blockEntity ? blockEntity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Get the accessor block state
    private static @Nullable BlockState getAccessorBlockState(@Nullable Object accessor, BlockPos pos) {
        if (accessor == null) {
            return null;
        }
        try {
            Object res = accessor.getClass().getMethod("getBlockState", BlockPos.class).invoke(accessor, pos);
            return res instanceof BlockState state ? state : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Check if this has accessor chunk
    private static boolean hasAccessorChunk(@Nullable Object accessor, BlockPos pos) {
        if (accessor == null || pos == null) {
            return false;
        }
        try {
            Object res = accessor.getClass().getMethod("hasChunk", int.class, int.class)
                    .invoke(accessor, pos.getX() >> 4, pos.getZ() >> 4);
            return res instanceof Boolean loaded && loaded;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Set the accessor block
    private static boolean setAccessorBlock(@Nullable Object accessor, BlockPos pos, BlockState state) {
        if (accessor == null) {
            return false;
        }
        try {
            Object res = accessor.getClass().getMethod("setBlock", BlockPos.class, BlockState.class, int.class)
                    .invoke(accessor, pos, state, 3);
            return !(res instanceof Boolean bool) || bool;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Remove the entity launcher claw entity
    @Override
    public void remove(RemovalReason reason) {
        EntityLauncherItem.unregisterActiveClaw(this);
        if (!level().isClientSide()) {
            destroyHeldRope();
            removeAttachedConnector();
        }
        super.remove(reason);
    }

    // Ensure the block rope length
    private void ensureBlockRopeLength(Entity owner, Vec3 anchorWorld) {
        if (ropeLength >= 0.0) {
            return;
        }
        setRopeLength(Math.max(MIN_ROPE_LENGTH, getProjectedOwnerHoldPos(owner).distanceTo(anchorWorld)));
    }

    // Maintain the owner distance
    private void maintainOwnerDistance(Entity owner, Vec3 anchorWorld) {
        Vec3 ownerHold = getProjectedOwnerHoldPos(owner);
        double distance = ownerHold.distanceTo(anchorWorld);
        double targetLength = ropeLength >= 0.0 ? ropeLength : distance;
        if (distance <= targetLength + 0.2) {
            return;
        }
        pullOwnerTowardAnchor(owner, anchorWorld, false);
    }

    // Pull the owner toward the anchor
    private boolean pullOwnerTowardAnchor(Entity owner, Vec3 anchorWorld, boolean activeReel) {
        Vec3 ownerHold = getProjectedOwnerHoldPos(owner);
        Vec3 toAnchor = anchorWorld.subtract(ownerHold);
        double distance = toAnchor.length();
        if (distance <= 0.25) {
            EntityLauncherItem.protectGrappleFall(owner);
            owner.fallDistance = 0.0f;
            return true;
        }

        double targetLength = ropeLength >= 0.0 ? ropeLength : distance;
        double excess = Math.max(0.0, distance - targetLength);
        if (!activeReel && excess <= 0.2) {
            return false;
        }

        if (excess > 0.05) {

            double correctionAmount = Math.min(excess, activeReel ? 0.38 : 0.28);
            Vec3 localCorrection = SimulatedHelper.toEntityLocalMovement(owner, toAnchor.normalize().scale(correctionAmount));
            owner.setPos(owner.position().add(localCorrection));
            owner.hurtMarked = true;
            ownerHold = getProjectedOwnerHoldPos(owner);
            toAnchor = anchorWorld.subtract(ownerHold);
            distance = toAnchor.length();
            excess = Math.max(0.0, distance - targetLength);
        }

        double speed = activeReel
            ? Math.min(getGrappleReelVelocity(), 0.09 + excess * 0.055)
            : Math.min(getGrappleSlackPullVelocity(), 0.02 + excess * 0.02);
        Vec3 globalPull = toAnchor.normalize().scale(speed);
        Vec3 globalVelocity = toEntityWorldMovement(owner, owner.getDeltaMovement());
        if (distance >= targetLength - 0.05) {
            double awaySpeed = -globalVelocity.dot(toAnchor.normalize());
            if (awaySpeed > 0.0) {
                globalVelocity = globalVelocity.add(toAnchor.normalize().scale(awaySpeed * 0.9));
            }
        }

        Vec3 swingImpulse = getGrappleSwingImpulse(owner, toAnchor);
        Vec3 nextGlobalVelocity = globalVelocity
                .scale(activeReel ? 0.94 : 0.985)
                .add(globalPull)
                .add(swingImpulse);
        double grappleMaxVelocity = getGrappleMaxVelocity();
        if (nextGlobalVelocity.lengthSqr() > grappleMaxVelocity * grappleMaxVelocity) {
            nextGlobalVelocity = nextGlobalVelocity.normalize().scale(grappleMaxVelocity);
        }
        owner.setDeltaMovement(SimulatedHelper.toEntityLocalMovement(owner, nextGlobalVelocity));
        EntityLauncherItem.protectGrappleFall(owner);
        owner.hurtMarked = true;
        owner.fallDistance = 0.0f;
        return distance <= 1.0;
    }

    // Find the grabbed entity
    @Nullable
    private Entity findGrabbedEntity() {
        UUID entityId = level().isClientSide() ? getSyncedGrabbedEntityId() : grabbedEntityId;
        if (entityId == null) {
            return null;
        }
        if (level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(entityId);
        }
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(128.0), entity -> entityId.equals(entity.getUUID()))) {
            return entity;
        }
        return null;
    }

    // Get the grabbed attachment pos
    private Vec3 getGrabbedAttachmentPos(Entity grabbed) {
        Vec3 offset = level().isClientSide() ? getSyncedGrabbedEntityOffset() : grabbedEntityAttachOffset;
        if (offset.lengthSqr() < 1.0E-6D) {
            offset = new Vec3(0.0, grabbed.getBbHeight() * 0.5, 0.0);
        }
        Vec3 attachment = grabbed.position().add(offset);

        return SimulatedHelper.projectOutOfSubLevels(level(), attachment);
    }

    // Get the entity attach offset
    private Vec3 getEntityAttachOffset(Entity entity, Vec3 hitLocation) {
        Vec3 offset = hitLocation == null
                ? new Vec3(0.0, entity.getBbHeight() * 0.5, 0.0)
                : hitLocation.subtract(entity.position());
        double halfWidth = Math.max(0.1, entity.getBbWidth() * 0.65);
        double x = Math.max(-halfWidth, Math.min(halfWidth, offset.x));
        double y = Math.max(0.0, Math.min(entity.getBbHeight(), offset.y));
        double z = Math.max(-halfWidth, Math.min(halfWidth, offset.z));
        return new Vec3(x, y, z);
    }

    // Get the grapple swing impulse
    private Vec3 getGrappleSwingImpulse(Entity owner, Vec3 toAnchor) {
        if (!(owner instanceof net.minecraft.world.entity.player.Player player)) {
            return Vec3.ZERO;
        }
        Vec3 localInput = getPlayerLocalInput(player);
        if (localInput.lengthSqr() < 1.0E-5 || toAnchor.lengthSqr() < 1.0E-5) {
            return Vec3.ZERO;
        }

        Vec3 worldInput = toEntityWorldMovement(player, localInput).normalize();
        Vec3 ropeDirection = toAnchor.normalize();
        Vec3 tangent = worldInput.subtract(ropeDirection.scale(worldInput.dot(ropeDirection)));
        if (tangent.lengthSqr() < 1.0E-5) {
            return Vec3.ZERO;
        }
        return tangent.normalize().scale(getGrappleSwingAcceleration());
    }

    // Get the grapple reel velocity
    private static double getGrappleReelVelocity() {
        return Math.max(0.01D, CTConfigs.COMMON.entityLauncherGrappleReelVelocity.get());
    }

    // Get the grapple slack pull velocity
    private static double getGrappleSlackPullVelocity() {
        return Math.max(0.0D, CTConfigs.COMMON.entityLauncherGrappleSlackPullVelocity.get());
    }

    // Get the grapple max velocity
    private static double getGrappleMaxVelocity() {
        return Math.max(0.1D, CTConfigs.COMMON.entityLauncherGrappleMaxVelocity.get());
    }

    // Get the grapple swing acceleration
    private static double getGrappleSwingAcceleration() {
        return Math.max(0.0D, CTConfigs.COMMON.entityLauncherGrappleSwingAcceleration.get());
    }

    // Get the player local input
    private Vec3 getPlayerLocalInput(net.minecraft.world.entity.player.Player player) {
        float forwardInput = player.zza;
        float strafeInput = player.xxa;
        if (Math.abs(forwardInput) < 1.0E-4F && Math.abs(strafeInput) < 1.0E-4F) {
            return Vec3.ZERO;
        }

        float yawRad = player.getYRot() * ((float) Math.PI / 180.0f);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3 right = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
        Vec3 input = forward.scale(forwardInput).add(right.scale(strafeInput));
        return input.lengthSqr() > 1.0 ? input.normalize() : input;
    }

    // Convert the entity launcher claw entity to entity world movement
    private Vec3 toEntityWorldMovement(Entity entity, Vec3 localMovement) {
        if (entity == null || localMovement == null || localMovement.lengthSqr() < 1.0E-6D) {
            return localMovement;
        }
        Object subLevel = SimulatedHelper.getEntityTrackingSubLevel(entity);
        if (subLevel == null) {
            subLevel = SimulatedHelper.getContainingSubLevel(entity.level(), entity.position());
        }
        if (subLevel == null) {
            return localMovement;
        }
        double length = localMovement.length();
        Vec3 worldDirection = SimulatedHelper.toContainingWorldDirection(subLevel, localMovement);
        return worldDirection.lengthSqr() < 1.0E-6D ? localMovement : worldDirection.normalize().scale(length);
    }

    // Maintain the grabbed distance
    private void maintainGrabbedDistance(Entity grabbed, Vec3 ownerHold) {
        EntityLauncherItem.protectHeldEntityFall(grabbed);
        Vec3 grabbedAnchor = getGrabbedAttachmentPos(grabbed);

        if (heldRopeEndEndpoint != null && !level().isClientSide()) {
            heldRopeEndEndpoint.setAttachmentPoint(grabbedAnchor);
        }
        Vec3 toOwner = ownerHold.subtract(grabbedAnchor);
        double distance = toOwner.length();
        double targetLength = ropeLength >= 0.0 ? ropeLength : distance;
        if (distance > targetLength + 0.15) {
            boolean fullRetract = targetLength <= 0.5;
            double maxPull = fullRetract ? RETRACT_SPEED * 1.75 : 0.22;
            double basePull = fullRetract ? 0.18 + (distance - targetLength) * 0.16 : 0.03 + (distance - targetLength) * 0.025;
            Vec3 pull = toOwner.normalize().scale(Math.min(maxPull, basePull));
            grabbed.setDeltaMovement(grabbed.getDeltaMovement().add(pull));
            grabbed.hurtMarked = true;
        }
    }
}
