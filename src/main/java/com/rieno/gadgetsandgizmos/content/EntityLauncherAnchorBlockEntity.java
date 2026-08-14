package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedRopeCompat;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherAnchorControlPacket;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.config.server.blocks.SimBlockConfigs;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.service.SimConfigService;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// Own the launcher rope, captured entity and physical anchor used during a launch
public class EntityLauncherAnchorBlockEntity extends KineticBlockEntity
        implements RopeStrandHolderBlockEntity, BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final double MAX_SAFE_COORD = 29_999_984.0;
    private static final double MIN_SAFE_Y = -2048.0;
    private static final double MAX_SAFE_Y = 2047.0;
    private static final int MAX_CHARGE_TICKS = 60;
    private static final int TAP_TICKS = 6;
    private static final Map<UUID, EntityLauncherAnchorBlockEntity> MOUNTED_LAUNCHERS = new ConcurrentHashMap<>();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether client rope bounds method is resolved
    private static volatile boolean clientRopeBoundsMethodResolved;
    // Resolved client rope bounds method
    @Nullable
    private static Method clientRopeBoundsMethod;
    // Current rope holder
    private RopeStrandHolderBehavior ropeHolder;
    // Target entity id
    @Nullable
    private UUID targetEntityId;
    // Target sub-level id
    @Nullable
    private UUID targetSubLevelId;
    // Target local anchor pos
    @Nullable
    private Vec3 targetLocalAnchorPos;
    // Target knot pos
    @Nullable
    private BlockPos targetKnotPos;
    // Target knot sub-level id
    @Nullable
    private UUID targetKnotSubLevelId;
    // Tracks whether target temporary endpoint is set
    private boolean targetTemporaryEndpoint;
    // Target entity attach offset
    private Vec3 targetEntityAttachOffset = Vec3.ZERO;
    // Current rope length
    private double ropeLength = 3.0;
    // Current anchor rope retry cooldown
    private int anchorRopeRetryCooldown;
    // Tracks whether sub-level reconnect is pending
    private boolean pendingSubLevelReconnect;
    // Tracks whether loaded attachment resync is pending
    private boolean pendingLoadedAttachmentResync;
    // Current controlling player id
    @Nullable
    private UUID controllingPlayerId;
    // Current mount entity id
    @Nullable
    private UUID mountEntityId;
    // Mounted claw id
    @Nullable
    private UUID mountedClawId;
    // Current controlled aim direction
    @Nullable
    private Vec3 controlledAimDirection;
    // Mounted use press time
    private long mountedUsePressedAt = -1L;

    // Define the rope target safety values
    private enum RopeTargetSafety {
        SAFE,
        UNSAFE,
        UNRESOLVED
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the entity launcher anchor
    public EntityLauncherAnchorBlockEntity(BlockPos pos, BlockState blockState) {
        super(CTBlockEntities.ENTITY_LAUNCHER_ANCHOR.get(), pos, blockState);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the entity launcher anchor
    public static void tick(Level level, BlockPos pos, BlockState state, EntityLauncherAnchorBlockEntity anchor) {
        anchor.tick();
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
        ropeHolder = new RopeStrandHolderBehavior((SmartBlockEntity) this);
        behaviours.add(ropeHolder);
    }

    // Get the rope holder
    public RopeStrandHolderBehavior getRopeHolder() {
        return ropeHolder;
    }

    // Get the behavior
    @Override
    public RopeStrandHolderBehavior getBehavior() {
        return ropeHolder;
    }

    // Bind the target entity
    public void bindTargetEntity(@Nullable UUID targetEntityId, double ropeLength) {
        bindTargetEntity(targetEntityId, ropeLength, Vec3.ZERO);
    }

    // Bind the target entity
    public void bindTargetEntity(@Nullable UUID targetEntityId, double ropeLength, Vec3 entityAttachOffset) {
        destroyAnchorRope();
        this.targetEntityId = targetEntityId;
        this.targetSubLevelId = null;
        this.targetLocalAnchorPos = null;
        this.targetKnotPos = null;
        this.targetKnotSubLevelId = null;
        this.targetTemporaryEndpoint = true;
        this.targetEntityAttachOffset = entityAttachOffset == null ? Vec3.ZERO : entityAttachOffset;
        pendingSubLevelReconnect = false;
        anchorRopeRetryCooldown = 2;
        this.ropeLength = Math.max(1.0, ropeLength);

        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null && targetEntityId != null) {
            Entity target = getEntityByUuid(serverLevel, targetEntityId);
            if (target != null && !target.isRemoved()) {
                Vec3 targetCenter = getEntityAttachmentPos(target);
                targetLocalAnchorPos = sanitizeAnchorPosition(targetCenter, serverLevel);
                if (targetLocalAnchorPos != null) {
                    targetKnotPos = BlockPos.containing(targetCenter).immutable();
                    targetKnotSubLevelId = null;
                }
            }
            RopeTargetSafety safety = currentRopeTargetSafety(serverLevel);
            if (safety == RopeTargetSafety.UNSAFE) {
                releaseAllTargets();
                return;
            }
            if (safety == RopeTargetSafety.SAFE && targetLocalAnchorPos != null) {
                ensureTmpEndpoint(serverLevel);
            }
        }

        markChangedAndSync();
    }

    // Bind the deferred sublevel anchor
    public void bindDeferredSubLevelAnchor(@Nullable UUID subLevelId, @Nullable Vec3 localAnchorPos, double ropeLength) {
        if (subLevelId == null) {
            releaseAllTargets();
            return;
        }

        Vec3 sanitizedLocal = sanitizeAnchorPosition(localAnchorPos, level);
        if (sanitizedLocal == null) {
            releaseAllTargets();
            return;
        }
        bindDeferredBlockAnchor(subLevelId, sanitizedLocal, BlockPos.containing(sanitizedLocal), ropeLength);
    }

    // Bind the deferred block anchor
    public void bindDeferredBlockAnchor(@Nullable UUID subLevelId, @Nullable Vec3 localAnchorPos,
                                        @Nullable BlockPos endpointPos, double ropeLength) {

        Vec3 sanitizedLocal = sanitizeAnchorPosition(localAnchorPos, level);
        if (sanitizedLocal == null || endpointPos == null) {
            releaseAllTargets();
            return;
        }
        destroyAnchorRope();
        this.targetEntityId = null;
        this.targetSubLevelId = subLevelId;
        this.targetLocalAnchorPos = sanitizedLocal;
        this.targetKnotPos = endpointPos.immutable();
        this.targetKnotSubLevelId = subLevelId;
        this.targetTemporaryEndpoint = true;
        this.targetEntityAttachOffset = Vec3.ZERO;
        this.ropeLength = Math.max(1.0, ropeLength);
        this.pendingSubLevelReconnect = subLevelId != null;
        this.anchorRopeRetryCooldown = 2;
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            RopeTargetSafety safety = currentRopeTargetSafety(serverLevel);
            if (safety == RopeTargetSafety.UNSAFE) {
                releaseAllTargets();
                return;
            }
        }
        markChangedAndSync();
    }

    // Bind the target anchor
    public void bindTargetAnchor(Vec3 worldAnchorPos, @Nullable UUID subLevelId, double ropeLength) {

        worldAnchorPos = sanitizeAnchorPosition(worldAnchorPos, level);
        if (worldAnchorPos == null) {
            releaseAllTargets();
            return;
        }
        destroyAnchorRope();
        this.targetEntityId = null;
        this.targetSubLevelId = subLevelId;
        this.targetKnotPos = null;
        this.targetKnotSubLevelId = null;
        this.targetEntityAttachOffset = Vec3.ZERO;
        this.targetTemporaryEndpoint = false;

        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null && subLevelId != null) {
            Object subLevel = getSubLevelById(serverLevel, subLevelId);
            Vec3 local = SimulatedHelper.tryToContainingLocalPosition(subLevel, worldAnchorPos);
            this.targetLocalAnchorPos = sanitizeAnchorPosition(local, level);
        } else {
            this.targetLocalAnchorPos = worldAnchorPos;
        }

        if (this.targetLocalAnchorPos == null) {
            releaseAllTargets();
            return;
        }

        Vec3 muzzle = getBaseAttachmentWorldPos();
        this.ropeLength = Math.max(1.0, muzzle.distanceTo(worldAnchorPos));

        this.ropeLength = Math.max(1.0, ropeLength);
        this.pendingSubLevelReconnect = false;
        if (this.targetSubLevelId != null) {
            this.anchorRopeRetryCooldown = 2;
        }

        if (serverLevel != null) {
            RopeTargetSafety safety = currentRopeTargetSafety(serverLevel);
            if (safety == RopeTargetSafety.UNSAFE) {
                releaseAllTargets();
                return;
            }
        }

        markChangedAndSync();
    }

    // Bind the target connector
    public void bindTargetConnector(BlockPos connectorPos, @Nullable UUID connectorSubLevelId, double ropeLength) {
        if (connectorPos == null) {
            releaseAllTargets();
            return;
        }
        destroyAnchorRope();
        this.targetEntityId = null;
        this.targetSubLevelId = connectorSubLevelId;
        this.targetKnotPos = connectorPos.immutable();
        this.targetKnotSubLevelId = connectorSubLevelId;
        this.targetTemporaryEndpoint = false;
        this.targetEntityAttachOffset = Vec3.ZERO;
        pendingSubLevelReconnect = false;

        Vec3 worldAnchor = resolveConnectorWorldPos();
        if (worldAnchor == null) {
            releaseAllTargets();
            return;
        }
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (connectorSubLevelId != null && serverLevel != null) {
            Object subLevel = getSubLevelById(serverLevel, connectorSubLevelId);
            Vec3 local = SimulatedHelper.tryToContainingLocalPosition(subLevel, worldAnchor);
            this.targetLocalAnchorPos = sanitizeAnchorPosition(local, level);
        } else {
            this.targetLocalAnchorPos = sanitizeAnchorPosition(worldAnchor, level);
        }
        if (this.targetLocalAnchorPos == null) {
            releaseAllTargets();
            return;
        }

        this.ropeLength = Math.max(1.0, ropeLength);
        if (serverLevel != null) {
            RopeTargetSafety safety = currentRopeTargetSafety(serverLevel);
            if (safety == RopeTargetSafety.UNSAFE) {
                releaseAllTargets();
                return;
            }
            if (safety == RopeTargetSafety.SAFE) {
                createAnchorRope(serverLevel, worldAnchor);
            }
        }
        markChangedAndSync();
    }

    // Get the target sublevel id
    @Nullable
    public UUID getTargetSubLevelId() {
        return targetSubLevelId;
    }

    // Get the target local anchor pos
    @Nullable
    public Vec3 getTargetLocalAnchorPos() {
        return targetLocalAnchorPos;
    }

    // Check if this has entity target
    public boolean hasEntityTarget() {
        return targetEntityId != null;
    }

    // Check if this has anchor target
    public boolean hasAnchorTarget() {
        return targetLocalAnchorPos != null;
    }

    // Check if this has mounted claw
    public boolean hasMountedClaw() {
        return mountedClawId != null;
    }

    // Check if this can accept target sublevel
    boolean canAcceptTargetSubLevel(@Nullable UUID candidateSubLevelId) {
        return !EntityLauncherAnchorAim.sharesDynamicSubLevel(
                SimulatedHelper.getContainingSubLevelId(this), candidateSubLevelId);
    }

    // Bind the transferred claw target
    public boolean bindTransferredClawTarget(EntityLauncherClawEntity claw) {
        EntityLauncherTargetSnapshot snapshot = EntityLauncherTargetSnapshot.capture(claw);
        if (snapshot == null || !snapshot.bindTo(this)) {
            return false;
        }
        snapshot.prepareSourceRemoval(claw);
        return true;
    }

    // Accept the mounted claw attachment
    public boolean acceptMountedClawAttachment(EntityLauncherClawEntity claw) {
        if (claw == null || mountedClawId == null || !mountedClawId.equals(claw.getUUID())) {
            return false;
        }
        if (!bindTransferredClawTarget(claw)) {
            return false;
        }
        mountedClawId = null;
        markChangedAndSync();
        return true;
    }

    // Register the mounted claw
    public void registerMountedClaw(EntityLauncherClawEntity claw) {
        if (claw == null || claw.isRemoved()) {
            return;
        }
        if (mountedClawId == null) {
            mountedClawId = claw.getUUID();
            markChangedAndSync();
        }
    }

    // Try to mount
    public void tryMount(Player player) {
        if (level == null || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel serverLevel = serverPlayer.serverLevel();
        ServerLevel mountedLevel = mountedEntityLevel(null);
        validateMountedCtrl(mountedLevel == null ? serverLevel : mountedLevel);
        if (controllingPlayerId != null && !controllingPlayerId.equals(player.getUUID())) {
            return;
        }

        ArmorStand mount = getMountEntity(serverLevel);
        if (mount == null) {
            Vec3 pos = getMountWorldPosition();
            mount = new ArmorStand(serverLevel, pos.x, pos.y, pos.z);
            mount.setNoGravity(true);
            mount.setInvulnerable(true);
            mount.setNoBasePlate(true);
            mount.setSilent(true);
            mount.setInvisible(true);
            if (!serverLevel.addFreshEntity(mount)) {
                return;
            }
            mountEntityId = mount.getUUID();
        }

        controllingPlayerId = player.getUUID();
        controlledAimDirection = player.getLookAngle().normalize();
        MOUNTED_LAUNCHERS.put(player.getUUID(), this);
        serverPlayer.startRiding(mount, true);
        markChangedAndSync();
    }

    // Handle the mounted control
    public void handleMountedControl(ServerPlayer player, int action, float xRot, float yRot) {
        if (!player.getUUID().equals(controllingPlayerId)) {
            return;
        }
        ServerLevel serverLevel = player.serverLevel();
        ArmorStand mount = getMountEntity(serverLevel);
        if (mount == null || player.getVehicle() != mount) {
            clearMountedController(player);
            return;
        }

        updateControlledAim(player, xRot, yRot);
        if (action == ServerboundEntityLauncherAnchorControlPacket.DISMOUNT) {
            clearMountedController(player);
            return;
        }
        if (action == ServerboundEntityLauncherAnchorControlPacket.USE_PRESSED) {
            mountedUsePressedAt = serverLevel.getGameTime();
            return;
        }
        if (action != ServerboundEntityLauncherAnchorControlPacket.USE_RELEASED || mountedUsePressedAt < 0L) {
            return;
        }

        long heldTicks = Math.max(0L, serverLevel.getGameTime() - mountedUsePressedAt);
        mountedUsePressedAt = -1L;
        if (heldTicks < TAP_TICKS) {
            detachAndRetractMountedClaw(player);
        } else {
            fireMountedClaw(player, Mth.clamp(heldTicks / (double) MAX_CHARGE_TICKS, 0.0D, 1.0D));
        }
    }

    // Check if this is a mounted launcher rider
    public static boolean isMountedLauncherRider(ServerPlayer player) {
        return getMountedLauncher(player) != null;
    }

    // Get the mounted launcher muzzle
    public static @Nullable Vec3 getMountedLauncherMuzzle(Player player) {
        EntityLauncherAnchorBlockEntity anchor = player instanceof ServerPlayer serverPlayer
                ? getMountedLauncher(serverPlayer)
                : MOUNTED_LAUNCHERS.get(player.getUUID());
        return anchor != null && anchor.isCurrentMountedPlayer(player) ? anchor.getAttachmentWorldPosition() : null;
    }

    // Get the mounted launcher
    public static @Nullable EntityLauncherAnchorBlockEntity getMountedLauncher(ServerPlayer player) {
        EntityLauncherAnchorBlockEntity anchor = MOUNTED_LAUNCHERS.get(player.getUUID());
        if (anchor != null && anchor.isCurrentMountedPlayer(player)) {
            return anchor;
        }
        if (anchor != null) {
            MOUNTED_LAUNCHERS.remove(player.getUUID(), anchor);
        }
        return null;
    }

    // Check if this is current mounted player
    private boolean isCurrentMountedPlayer(Player player) {
        return player.getUUID().equals(controllingPlayerId)
                && player.isPassenger()
                && player.getVehicle() != null
                && player.getVehicle().getUUID().equals(mountEntityId);
    }

    // Handle the destroyed event
    public void onDestroyed() {
        ServerLevel serverLevel = mountedEntityLevel(null);
        if (serverLevel != null) {
            EntityLauncherClawEntity claw = getMountedClaw(serverLevel);
            if (claw != null) {
                claw.discard();
            }
            mountedClawId = null;
        }
        clearMountedController(null);
        releaseAllTargets();
    }

    // Handle the external rope break
    public void handleExternalRopeBreak() {
        removeAnchorKnot();
        targetEntityId = null;
        targetSubLevelId = null;
        targetLocalAnchorPos = null;
        targetKnotPos = null;
        targetKnotSubLevelId = null;
        targetTemporaryEndpoint = false;
        targetEntityAttachOffset = Vec3.ZERO;
        anchorRopeRetryCooldown = 0;
        pendingSubLevelReconnect = false;
        markChangedAndSync();
    }

    // Release all launcher targets
    public void releaseAllTargets() {
        destroyAnchorRope();
        targetEntityId = null;
        targetSubLevelId = null;
        targetLocalAnchorPos = null;
        targetKnotPos = null;
        targetKnotSubLevelId = null;
        targetTemporaryEndpoint = false;
        targetEntityAttachOffset = Vec3.ZERO;
        pendingSubLevelReconnect = false;
        markChangedAndSync();
    }

    // Get the attachment world position
    public Vec3 getAttachmentWorldPosition() {
        return toAnchorWorldPosition(getAttachmentLocalPosition());
    }

    // Get the attachment local position
    public Vec3 getAttachmentLocalPosition() {
        Vec3 pivot = barrelPivotLocalPos();
        Vec3 target = getBarrelAimTargetLocalPosition();
        if (target != null) {
            return pivot.add(constrainedAimDirection(getBlockState(), pivot, target)
                    .scale(EntityLauncherAnchorAim.MUZZLE_DISTANCE_FROM_PIVOT));
        }
        return getBaseAttachmentLocalPos(pivot);
    }

    // Get the base attachment world pos
    private Vec3 getBaseAttachmentWorldPos() {
        return toAnchorWorldPosition(getBaseAttachmentLocalPos());
    }

    // Get the base attachment local pos
    private Vec3 getBaseAttachmentLocalPos() {
        return getBaseAttachmentLocalPos(barrelPivotLocalPos());
    }

    // Get the base attachment local pos
    private Vec3 getBaseAttachmentLocalPos(Vec3 center) {
        if (getBlockState().hasProperty(EntityLauncherAnchorBlock.FACING)) {
            Vec3 offset = EntityLauncherAnchorAim.solve(mountNormal(getBlockState()), null).direction()
                    .scale(EntityLauncherAnchorAim.MUZZLE_DISTANCE_FROM_PIVOT);
            return center.add(offset);
        }
        return center;
    }

    // Get the attachment point
    @Override
    public Vec3 getAttachmentPoint(BlockPos pos, BlockState state) {
        return getAttachmentLocalPosition();
    }

    // Get the render target world position
    @Nullable
    public Vec3 getRenderTargetWorldPosition() {
        if (level == null) {
            return null;
        }
        if (targetEntityId != null) {
            Entity target = getEntityByUuid(level, targetEntityId);
            if (target != null && !target.isRemoved()) {
                return getEntityAttachmentPos(target);
            }
        }
        if (targetLocalAnchorPos != null) {
            if (targetSubLevelId != null) {
                Object subLevel = getSubLevelById(level, targetSubLevelId);
                Vec3 world = resolveStoredAnchorWorldPos(level, subLevel);
                if (world != null) {
                    return world;
                }
                return null;
            }
            return sanitizeAnchorPosition(targetLocalAnchorPos, level);
        }
        return null;
    }

    // Get the barrel aim target world position
    public @Nullable Vec3 getBarrelAimTargetWorldPosition() {
        if (level == null) {
            return null;
        }
        if (controlledAimDirection != null) {
            Vec3 pivot = getBarrelPivotWorldPos();
            return getAimTargetWorldPos(pivot);
        }
        return getRenderTargetWorldPosition();
    }

    // Get the barrel aim target local position
    public @Nullable Vec3 getBarrelAimTargetLocalPosition() {
        Vec3 target = getBarrelAimTargetWorldPosition();
        return target == null ? null : toAnchorLocalPosition(target);
    }

    // Update the entity launcher anchor
    @Override
    public void tick() {
        super.tick();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }

        validateMountedCtrl(serverLevel);
        if (targetEntityId != null || targetLocalAnchorPos != null) {
            RopeTargetSafety safety = currentRopeTargetSafety(serverLevel);
            if (safety == RopeTargetSafety.UNSAFE) {
                releaseAllTargets();
                return;
            }
            if (safety == RopeTargetSafety.UNRESOLVED) {
                return;
            }
        }
        if (pendingLoadedAttachmentResync && targetSubLevelId == null) {
            if (targetTemporaryEndpoint && targetKnotPos != null) {
                ensureTmpEndpoint(serverLevel);
            }
            resyncLoadedEndAttachment(serverLevel);
            pendingLoadedAttachmentResync = false;
        }
        ServerRopeStrand strand = ropeHolder == null ? null : ropeHolder.getAttachedStrand();
        if (strand != null) {
            updateRopeStrandExtension(strand);
            if (ropeHolder.ownsRope()) {
                syncOwnedRopeStartAttachment(strand);
            } else {
                setChanged();
            }
        } else {
            updateStoredRopeLengthFromKinetics();
        }

        if (targetEntityId != null) {
            Entity target = getEntityByUuid(serverLevel, targetEntityId);
            if (target != null && !target.isRemoved()) {
                ensureEntityTargetRope(serverLevel, target);
            }
            tickEntityTarget(serverLevel);
            return;
        }

        if (targetLocalAnchorPos != null) {
            if (pendingSubLevelReconnect && targetSubLevelId != null) {
                tryResolvePendingSubLevelReconnect(serverLevel);
            }
            ensureAnchorRope(serverLevel);
            return;
        }
    }

    // Update the stored rope length from kinetics
    private void updateStoredRopeLengthFromKinetics() {
        if ((targetEntityId == null && targetLocalAnchorPos == null) || Math.abs(getSpeed()) <= 1.0E-4F) {
            return;
        }
        double nextLength = Mth.clamp(ropeLength + getMovementSpeed(), 1.0D, 50.0D);
        if (Math.abs(nextLength - ropeLength) <= 1.0E-4D) {
            return;
        }
        ropeLength = nextLength;
        markChangedAndSync();
    }

    // Try to resolve pending sublevel reconnect
    private void tryResolvePendingSubLevelReconnect(ServerLevel serverLevel) {
        if (!pendingSubLevelReconnect || targetSubLevelId == null || targetLocalAnchorPos == null) {
            pendingSubLevelReconnect = false;
            return;
        }
        Object subLevel = getSubLevelById(serverLevel, targetSubLevelId);
        if (subLevel == null) {
            return;
        }

        if (targetTemporaryEndpoint && targetKnotPos != null) {
            ensureTmpEndpoint(serverLevel);
        }
        resyncLoadedEndAttachment(serverLevel);
        pendingSubLevelReconnect = false;
    }

    // Resync the loaded end attachment
    private void resyncLoadedEndAttachment(ServerLevel serverLevel) {
        if (ropeHolder == null) {
            return;
        }
        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand == null) {
            return;
        }
        RopeAttachment existing = strand.getAttachment(RopeAttachmentPoint.END);
        if (existing != null) {
            targetKnotPos = existing.blockAttachment();
            targetKnotSubLevelId = existing.subLevelID();
            if (targetTemporaryEndpoint && targetLocalAnchorPos != null) {
                ensureTmpEndpoint(serverLevel);
            }
            applyEndAttachmentPoint(serverLevel, strand, existing);
            setChanged();
            return;
        }
        if (targetKnotPos == null) {
            return;
        }
        UUID endSubLevelId = targetKnotSubLevelId != null ? targetKnotSubLevelId : targetSubLevelId;
        RopeAttachment restored = new RopeAttachment(RopeAttachmentPoint.END, endSubLevelId, targetKnotPos);
        strand.addAttachment(serverLevel, RopeAttachmentPoint.END,
                restored);
    }

    // Update the rope strand extension
    private void updateRopeStrandExtension(ServerRopeStrand strand) {
        updateRopeStrandExtension(strand, getMovementSpeed());
    }

    // Update the rope strand extension
    private void updateRopeStrandExtension(ServerRopeStrand strand, double movementSpeedIn) {
        SimBlockConfigs config = SimConfigService.INSTANCE.server().blocks;
        double movementSpeed = movementSpeedIn;
        double desiredExtension = strand.getExtension() + (double) (strand.getPoints().size() - 2);
        double currentExtension = strand.getCurrentExtension();
        if (currentExtension > desiredExtension * (1.0 + (Double) config.maxRopeStretchAllowed.get() / 100.0)) {
            movementSpeed = Math.max(0.0f, movementSpeed);
        }
        if (currentExtension > (Double) config.maxRopeRange.get()) {
            movementSpeed = Math.min(0.0f, movementSpeed);
        }

        double extension = strand.getExtension() + movementSpeed;
        if (extension < 1.0 && strand.getPoints().size() == 2) {
            extension = 1.0;
        } else {
            while (extension < 0.0) {
                strand.removeFirstPoint();
                extension += 1.0;
                if (extension >= 1.0 || strand.getPoints().size() != 2) {
                    continue;
                }
                extension = 1.0;
                break;
            }
            while (extension > 1.0) {
                Vector3d point = JOMLConversion.toJOML((Position)
                        Sable.HELPER.projectOutOfSubLevel(level, ropeHolder.getAttachmentPoint()));
                strand.addPoint((Vector3dc) point);
                extension -= 1.0;
            }
            if (extension < 1.0 && strand.getPoints().size() <= 2) {
                extension = 1.0;
            }
        }
        strand.updateFirstSegmentExtension(extension);
        ropeLength = Math.max(1.0D, strand.getCurrentExtension());
    }

    // Sync the owned rope start attachment
    private void syncOwnedRopeStartAttachment(ServerRopeStrand strand) {
        if (ropeHolder == null) {
            return;
        }
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }

        UUID startSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        RopeAttachment existing = strand.getAttachment(RopeAttachmentPoint.START);
        if (existing == null || !worldPosition.equals(existing.blockAttachment())
                || !Objects.equals(startSubLevelId, existing.subLevelID())) {
            strand.addAttachment(serverLevel, RopeAttachmentPoint.START,
                    new RopeAttachment(RopeAttachmentPoint.START, startSubLevelId, worldPosition));
        }
        ServerSubLevel subLevel = getServerSubLevel(serverLevel, startSubLevelId);
        if (startSubLevelId != null && subLevel == null) {
            return;
        }
        strand.setAttachment(RopeHandle.AttachmentPoint.START,
                JOMLConversion.toJOML((Position) ropeHolder.getAttachmentPoint()), subLevel);
    }

    // Update the controlled aim
    private void updateControlledAim(ServerPlayer player, float xRot, float yRot) {
        Vec3 eyePosition = SimulatedHelper.projectOutOfSubLevels(player.level(), player.getEyePosition());
        Vec3 lookDirection = Vec3.directionFromRotation(xRot, yRot);
        Object playerSubLevel = SimulatedHelper.getEntityTrackingSubLevel(player);
        if (playerSubLevel != null) {
            lookDirection = SimulatedHelper.toContainingWorldDirection(playerSubLevel, lookDirection);
        }
        if (lookDirection == null || lookDirection.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 cameraTarget = eyePosition.add(lookDirection.normalize().scale(64.0D));
        Vec3 pivot = getBarrelPivotWorldPos();
        Vec3 next = cameraTarget.subtract(pivot).normalize();
        if (controlledAimDirection == null || controlledAimDirection.distanceToSqr(next) > 1.0E-5D) {
            controlledAimDirection = next;
            markChangedAndSync();
        }
    }

    // Get the aim target world pos
    private @Nullable Vec3 getAimTargetWorldPos(Vec3 pivot) {
        if (controlledAimDirection == null) {
            return null;
        }
        return pivot.add(controlledAimDirection.scale(16.0D));
    }

    // Get the mounted fire direction
    private Vec3 getMountedFireDirection() {
        Vec3 localDirection = getAttachmentLocalPosition().subtract(barrelPivotLocalPos());
        if (localDirection.lengthSqr() < 1.0E-6D) {
            localDirection = EntityLauncherAnchorAim.solve(mountNormal(getBlockState()), null).direction();
        }
        Vec3 worldDirection = toAnchorWorldDirection(localDirection);
        return worldDirection.lengthSqr() < 1.0E-6D ? localDirection.normalize() : worldDirection.normalize();
    }

    // Fire the mounted claw
    private void fireMountedClaw(ServerPlayer player, double scalar) {
        detachAndRetractMountedClaw(player);
        Vec3 start = getAttachmentWorldPosition();
        Vec3 dir = getMountedFireDirection();
        EntityLauncherClawEntity claw = new EntityLauncherClawEntity(player.level(), player);
        claw.setPos(start);
        claw.setMountedLauncher(worldPosition, SimulatedHelper.getContainingSubLevelId(this), start);
        claw.setLaunchedFromMainHand(true);
        claw.setMaxTravelDistance(Mth.lerp(scalar, 6.0D, 50.0D));
        claw.setDeltaMovement(dir.scale(Mth.lerp(scalar, 0.75D, 2.25D)));
        claw.setOldPosAndRot();
        if (player.level().addFreshEntity(claw)) {
            mountedClawId = claw.getUUID();
            player.level().playSound(null, start.x, start.y, start.z, SoundEvents.CROSSBOW_SHOOT,
                    SoundSource.PLAYERS, 0.85F, 1.25F);
            markChangedAndSync();
        }
    }

    // Detach and retract the mounted claw
    private void detachAndRetractMountedClaw(ServerPlayer player) {
        EntityLauncherClawEntity claw = getMountedClaw(player.serverLevel());
        if (claw != null) {
            claw.beginRetracting(player);
        }
        mountedClawId = null;
        releaseAllTargets();
    }

    // Get the mounted claw
    private @Nullable EntityLauncherClawEntity getMountedClaw(ServerLevel serverLevel) {
        Entity entity = mountedClawId == null ? null : serverLevel.getEntity(mountedClawId);
        return entity instanceof EntityLauncherClawEntity claw && !claw.isRemoved() ? claw : null;
    }

    // Validate the mounted ctrl
    private void validateMountedCtrl(ServerLevel serverLevel) {
        if (controllingPlayerId == null) {
            discardUnusedMount(serverLevel);
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(controllingPlayerId);
        ArmorStand mount = getMountEntity(serverLevel);
        if (player == null || mount == null || player.getVehicle() != mount) {
            clearMountedController(player);
            return;
        }
        Vec3 pos = getMountWorldPosition();
        mount.teleportTo(pos.x, pos.y, pos.z);
        MOUNTED_LAUNCHERS.put(player.getUUID(), this);
        if (mountedUsePressedAt >= 0L) {
            long chargeTicks = Math.max(0L, serverLevel.getGameTime() - mountedUsePressedAt);
            if (chargeTicks % 5L == 0L) {
                int percent = Mth.floor(Mth.clamp(chargeTicks / (float) MAX_CHARGE_TICKS, 0.0F, 1.0F) * 100.0F);
                player.displayClientMessage(Component.literal("Entity Launcher Power: " + percent + "%"), true);
            }
        }
        if (mountedClawId != null && getMountedClaw(serverLevel) == null) {
            mountedClawId = null;
            markChangedAndSync();
        }
    }

    // Clear the mounted controller
    private void clearMountedController(@Nullable ServerPlayer player) {
        UUID oldController = controllingPlayerId;
        if (player != null && player.getVehicle() != null) {
            player.stopRiding();
        }
        ServerLevel serverLevel = mountedEntityLevel(player);
        if (serverLevel != null) {
            ArmorStand mount = getMountEntity(serverLevel);
            if (mount != null) {
                mount.ejectPassengers();
                mount.discard();
            }
        }
        if (oldController != null) {
            MOUNTED_LAUNCHERS.remove(oldController, this);
        }
        controllingPlayerId = null;
        mountEntityId = null;
        mountedUsePressedAt = -1L;
        markChangedAndSync();
    }

    // Get the mounted entity level
    @Nullable
    private ServerLevel mountedEntityLevel(@Nullable ServerPlayer player) {
        if (player != null) {
            return player.serverLevel();
        }
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return null;
        }
        if (controllingPlayerId != null) {
            ServerPlayer controller = serverLevel.getServer().getPlayerList().getPlayer(controllingPlayerId);
            if (controller != null) {
                return controller.serverLevel();
            }
        }
        return serverLevel;
    }

    // Discard the unused mount
    private void discardUnusedMount(ServerLevel serverLevel) {
        ArmorStand mount = getMountEntity(serverLevel);
        if (mount != null && mount.getPassengers().isEmpty()) {
            mount.discard();
            mountEntityId = null;
        }
    }

    // Get the mount entity
    private @Nullable ArmorStand getMountEntity(ServerLevel serverLevel) {
        Entity entity = mountEntityId == null ? null : serverLevel.getEntity(mountEntityId);
        return entity instanceof ArmorStand stand && !stand.isRemoved() ? stand : null;
    }

    // Get the mount world position
    private Vec3 getMountWorldPosition() {
        Vec3 pivot = getBarrelPivotWorldPos();
        Vec3 forward = getMountedFireDirection();
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            horizontal = toAnchorWorldDirection(
                    EntityLauncherAnchorAim.solve(mountNormal(getBlockState()), null).direction());
        }
        return pivot.subtract(horizontal.normalize().scale(1.15D)).add(0.0D, -1.75D, 0.0D);
    }

    // Get the movement speed
    public float getMovementSpeed() {
        return Mth.clamp((float) convertToLinear(getSpeed()), -0.49f, 0.49f);
    }

    // Get the render bounding box
    @Override
    public @NotNull net.minecraft.world.phys.AABB getRenderBoundingBox() {
        net.minecraft.world.phys.AABB ropeBounds = getClientRopeRenderBounds();
        if (ropeBounds != null) {
            return ropeBounds.inflate(3.0D);
        }
        return super.getRenderBoundingBox().inflate(8.0D);
    }

    // Get the client rope render bounds
    private @Nullable AABB getClientRopeRenderBounds() {
        if (ropeHolder == null || !ropeHolder.ownsRope() || level == null || !level.isClientSide()) {
            return null;
        }
        try {
            Method method = resolveClientRopeBoundsMethod();
            Object bounds = method == null ? null : method.invoke(null, ropeHolder);
            return bounds instanceof AABB aabb ? aabb : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    // Resolve the client rope bounds method
    private static @Nullable Method resolveClientRopeBoundsMethod() throws ReflectiveOperationException {
        if (!clientRopeBoundsMethodResolved) {
            synchronized (EntityLauncherAnchorBlockEntity.class) {
                if (!clientRopeBoundsMethodResolved) {
                    Class<?> helperClass = Class.forName(
                            "com.rieno.gadgetsandgizmos.neoforge.client.EntityLauncherAnchorClientBounds");
                    clientRopeBoundsMethod = helperClass.getMethod("getRopeBounds", RopeStrandHolderBehavior.class);
                    clientRopeBoundsMethodResolved = true;
                }
            }
        }
        return clientRopeBoundsMethod;
    }

    // Update the entity target
    private void tickEntityTarget(ServerLevel serverLevel) {
        Entity target = getEntityByUuid(serverLevel, targetEntityId);
        if (target == null) {

            destroyAnchorRope();
            anchorRopeRetryCooldown = Math.max(anchorRopeRetryCooldown, 10);
            return;
        }
        if (target.isRemoved()) {
            destroyAnchorRope();
            targetEntityId = null;
            targetSubLevelId = null;
            targetLocalAnchorPos = null;
            targetKnotPos = null;
            targetKnotSubLevelId = null;
            targetTemporaryEndpoint = false;
            markChangedAndSync();
            return;
        }

        Vec3 anchor = getAttachmentWorldPosition();
        Vec3 targetCenter = getEntityAttachmentPos(target);
        Vec3 delta = anchor.subtract(targetCenter);
        double distance = delta.length();
        double effectiveRopeLength = getEffectiveRopeLength();
        if (distance > effectiveRopeLength) {
            Vec3 pull = delta.normalize().scale(Math.min(0.35, (distance - effectiveRopeLength) * 0.08));
            target.setDeltaMovement(target.getDeltaMovement().scale(0.75).add(pull));
            target.hurtMarked = true;
        }
    }

    // Get the effective rope length
    private double getEffectiveRopeLength() {
        if (ropeHolder != null) {
            ServerRopeStrand strand = ropeHolder.getAttachedStrand();
            if (strand != null) {

                return Math.max(1.0, strand.getCurrentExtension());
            }
        }
        return Math.max(1.0, ropeLength);
    }

    // Ensure the entity target rope
    private void ensureEntityTargetRope(ServerLevel serverLevel, Entity target) {
        if (ropeHolder == null) {
            return;
        }

        Vec3 targetCenter = getEntityAttachmentPos(target);
        targetLocalAnchorPos = sanitizeAnchorPosition(targetCenter, serverLevel);
        if (targetLocalAnchorPos == null) {
            return;
        }

        if (targetKnotPos == null) {
            targetKnotPos = BlockPos.containing(targetCenter).immutable();
            targetKnotSubLevelId = null;
            targetTemporaryEndpoint = true;
            markChangedAndSync();
        }

        if (!ensureTmpEndpoint(serverLevel)) {
            return;
        }

        syncTmpEndpointAttachment(serverLevel, targetCenter);

        if (ropeHolder.getAttachedStrand() != null) {
            return;
        }

        if (anchorRopeRetryCooldown > 0) {
            anchorRopeRetryCooldown--;
            return;
        }

        createAnchorRope(serverLevel, targetCenter);
        anchorRopeRetryCooldown = ropeHolder.getAttachedStrand() == null ? 10 : 0;
    }

    // Ensure the anchor rope
    private void ensureAnchorRope(ServerLevel serverLevel) {
        if (targetLocalAnchorPos == null || ropeHolder == null || ropeHolder.getAttachedStrand() != null) {
            return;
        }

        if (targetTemporaryEndpoint && !ensureTmpEndpoint(serverLevel)) {
            return;
        }
        if (targetKnotPos == null) {
            return;
        }

        if (anchorRopeRetryCooldown > 0) {
            anchorRopeRetryCooldown--;
            return;
        }
        Vec3 targetWorld = getRenderTargetWorldPosition();
        if (targetSubLevelId != null) {
            targetWorld = getServerAnchorTargetWorldPos(serverLevel);
        }
        if (targetWorld != null) {
            createAnchorRope(serverLevel, targetWorld);
        }
        if (ropeHolder.getAttachedStrand() == null) {
            anchorRopeRetryCooldown = 20;
        }
    }

    // Create the anchor rope
    private void createAnchorRope(ServerLevel serverLevel, Vec3 worldAnchorPos) {
        if (ropeHolder == null || targetLocalAnchorPos == null || targetKnotPos == null) {
            return;
        }
        RopeTargetSafety safety = currentRopeTargetSafety(serverLevel);
        if (safety == RopeTargetSafety.UNSAFE) {
            releaseAllTargets();
            return;
        }
        if (safety == RopeTargetSafety.UNRESOLVED) {
            return;
        }
        RopeStrandHolderBehavior targetHolder = resolveTargetHolder(serverLevel);
        if (targetHolder == null || targetHolder.isAttached()) {
            return;
        }

        UUID startSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        targetKnotSubLevelId = targetKnotSubLevelId != null ? targetKnotSubLevelId : targetSubLevelId;
        if (!SimulatedRopeCompat.createRopeWithAttachments(ropeHolder, targetHolder, serverLevel,
                getAttachmentWorldPosition(), worldAnchorPos, startSubLevelId, worldPosition,
                targetKnotSubLevelId, targetKnotPos, false)) {
            return;
        }

        if (targetHolder.blockEntity instanceof LauncherEndpointBlockEntity endpoint) {
            endpoint.removeWhenFree();
        }
        ServerRopeStrand strand = ropeHolder.getAttachedStrand();
        if (strand != null) {
            syncOwnedRopeStartAttachment(strand);
            RopeAttachment endAttachment = new RopeAttachment(RopeAttachmentPoint.END, targetKnotSubLevelId, targetKnotPos);
            strand.addAttachment(serverLevel, RopeAttachmentPoint.END, endAttachment);
            applyEndAttachmentPoint(serverLevel, strand, endAttachment);
        }
    }

    // Get the current rope target safety
    private RopeTargetSafety currentRopeTargetSafety(ServerLevel serverLevel) {
        Object sourceSubLevel = SimulatedHelper.getContainingSubLevel(this);
        if (sourceSubLevel == null && SimulatedHelper.getSubLevelId(level) != null) {
            sourceSubLevel = level;
        }
        UUID sourceSubLevelId = SimulatedHelper.getSubLevelId(sourceSubLevel);
        if (sourceSubLevelId == null) {
            return RopeTargetSafety.SAFE;
        }

        UUID resolvedTargetSubLevelId = targetKnotSubLevelId != null ? targetKnotSubLevelId : targetSubLevelId;
        Vec3 targetWorldPosition;
        if (targetEntityId != null) {
            Entity target = getEntityByUuid(serverLevel, targetEntityId);
            if (target == null) {
                return RopeTargetSafety.UNRESOLVED;
            }
            if (target.isRemoved()) {
                return RopeTargetSafety.UNSAFE;
            }
            Object targetSubLevel = SimulatedHelper.getEntityTrackingSubLevel(target);
            if (targetSubLevel == null) {
                targetSubLevel = SimulatedHelper.getContainingSubLevel(target.level(), target.position());
            }
            resolvedTargetSubLevelId = SimulatedHelper.getSubLevelId(targetSubLevel);
            targetWorldPosition = getEntityAttachmentPos(target);
        } else {
            if (EntityLauncherAnchorAim.sharesDynamicSubLevel(sourceSubLevelId, resolvedTargetSubLevelId)) {
                return RopeTargetSafety.UNSAFE;
            }
            UUID positionSubLevelId = targetSubLevelId != null ? targetSubLevelId : targetKnotSubLevelId;
            if (positionSubLevelId != null) {
                Object targetSubLevel = getSubLevelById(serverLevel, positionSubLevelId);
                if (targetSubLevel == null || targetLocalAnchorPos == null) {
                    return RopeTargetSafety.UNRESOLVED;
                }
                targetWorldPosition = SimulatedHelper.tryToContainingWorldPosition(
                        targetSubLevel, targetLocalAnchorPos);
            } else {
                targetWorldPosition = getRenderTargetWorldPosition();
            }
        }

        if (EntityLauncherAnchorAim.sharesDynamicSubLevel(sourceSubLevelId, resolvedTargetSubLevelId)) {
            return RopeTargetSafety.UNSAFE;
        }
        if (!isFinite(targetWorldPosition)) {
            return RopeTargetSafety.UNRESOLVED;
        }
        Vec3 projectedTarget = SimulatedHelper.projectOutOfSubLevels(serverLevel, targetWorldPosition);
        Vec3 targetLocalPosition = SimulatedHelper.tryToContainingLocalPosition(sourceSubLevel, projectedTarget);
        if (!isFinite(targetLocalPosition)) {
            return RopeTargetSafety.UNRESOLVED;
        }
        return EntityLauncherAnchorAim.targetClearsMountingFace(
                mountNormal(getBlockState()), Vec3.atCenterOf(worldPosition), targetLocalPosition)
                ? RopeTargetSafety.SAFE
                : RopeTargetSafety.UNSAFE;
    }

    // Check if this is finite
    private static boolean isFinite(@Nullable Vec3 pos) {
        return pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y)
                && Double.isFinite(pos.z);
    }

    // Ensure the tmp endpoint
    private boolean ensureTmpEndpoint(ServerLevel serverLevel) {
        if (!targetTemporaryEndpoint || targetKnotPos == null || targetLocalAnchorPos == null) {
            return false;
        }
        UUID endpointSubLevelId = targetKnotSubLevelId != null ? targetKnotSubLevelId : targetSubLevelId;
        LauncherEndpointBlockEntity endpoint = endpointSubLevelId == null
                ? createTmpEndpointInLevel(serverLevel, targetKnotPos, targetLocalAnchorPos)
                : createTmpEndpointInSubLevel(serverLevel, endpointSubLevelId, targetKnotPos, targetLocalAnchorPos);
        if (endpoint == null) {
            return false;
        }
        targetKnotSubLevelId = endpointSubLevelId;
        return true;
    }

    // Create the tmp endpoint in level
    @Nullable
    private static LauncherEndpointBlockEntity createTmpEndpointInLevel(ServerLevel serverLevel, BlockPos endpointPos,
                                                                              Vec3 worldAnchorPos) {
        Vec3 target = sanitizeAnchorPosition(worldAnchorPos, serverLevel);
        if (target == null) {
            return null;
        }

        return EntityLauncherVirtualRopeEndpoints.getOrCreate(serverLevel, null, endpointPos, target);
    }

    // Create the tmp endpoint in sublevel
    @Nullable
    private static LauncherEndpointBlockEntity createTmpEndpointInSubLevel(ServerLevel serverLevel, UUID subLevelId,
                                                                                 BlockPos endpointPos, Vec3 localAnchorPos) {
        Vec3 target = sanitizeAnchorPosition(localAnchorPos, serverLevel);
        if (target == null) {
            return null;
        }
        if (SubLevelBlockEntityCollector.getSubLevel(serverLevel, subLevelId) == null) {
            return null;
        }

        return EntityLauncherVirtualRopeEndpoints.getOrCreate(serverLevel, subLevelId, endpointPos, target);
    }

    // Destroy the anchor rope
    private void destroyAnchorRope() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (ropeHolder != null) {
            if (ropeHolder.ownsRope() && ropeHolder.getOwnedStrand() != null) {
                SimulatedRopeCompat.destroyRope(ropeHolder, null, getAttachmentWorldPosition(), false);
            }

            if (ropeHolder.isAttached()) {
                ropeHolder.detachRope();
            }
        }
        removeAnchorKnot();
    }

    // Remove the anchor knot
    private void removeAnchorKnot() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || targetKnotPos == null) {
            return;
        }
        if (!targetTemporaryEndpoint) {
            return;
        }
        if (targetKnotSubLevelId != null) {
            removeFreeTmpEndpoint(serverLevel, targetKnotSubLevelId, targetKnotPos);
            return;
        }
        removeFreeTmpEndpoint(serverLevel, null, targetKnotPos);
    }

    // Resolve the connector world pos
    private @Nullable Vec3 resolveConnectorWorldPos() {
        if (targetKnotPos == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(level, targetKnotSubLevelId, targetKnotPos);
        if (blockEntity == null) {
            return null;
        }
        RopeStrandHolderBehavior targetHolder = resolveHolderBehavior(blockEntity);
        if (targetHolder == null) {
            return null;
        }
        Vec3 holderAnchor = targetHolder.getAttachmentPoint();
        if (holderAnchor == null) {
            return null;
        }
        if (targetKnotSubLevelId != null) {
            Object subLevel = SubLevelBlockEntityCollector.getSubLevel(level, targetKnotSubLevelId);
            Vec3 worldAnchor = SimulatedHelper.toContainingWorldPosition(subLevel, holderAnchor);
            return worldAnchor == null ? null : SimulatedHelper.projectOutOfSubLevels(level, worldAnchor);
        }
        return SimulatedHelper.projectOutOfSubLevels(level, holderAnchor);
    }

    // Resolve the target holder
    private @Nullable RopeStrandHolderBehavior resolveTargetHolder(ServerLevel serverLevel) {
        if (targetKnotPos == null) {
            return null;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(serverLevel, targetKnotSubLevelId, targetKnotPos);
        if (blockEntity == null) {
            blockEntity = EntityLauncherVirtualRopeEndpoints.find(serverLevel, targetKnotSubLevelId, targetKnotPos);
        }
        return resolveHolderBehavior(blockEntity);
    }

    // Resolve the holder behavior
    private static @Nullable RopeStrandHolderBehavior resolveHolderBehavior(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof LauncherEndpointBlockEntity endpoint) {
            return endpoint.getRopeHolder();
        }
        if (!(blockEntity instanceof SmartBlockEntity smartBlockEntity)) {
            return null;
        }
        return (RopeStrandHolderBehavior) smartBlockEntity.getBehaviour(RopeStrandHolderBehavior.TYPE);
    }

    // Get the tmp endpoint
    @Nullable
    private LauncherEndpointBlockEntity getTmpEndpoint(ServerLevel serverLevel) {
        if (!targetTemporaryEndpoint || targetKnotPos == null) {
            return null;
        }
        BlockEntity blockEntity = EntityLauncherVirtualRopeEndpoints.find(serverLevel, targetKnotSubLevelId, targetKnotPos);
        return blockEntity instanceof LauncherEndpointBlockEntity endpoint ? endpoint : null;
    }

    // Sync the tmp endpoint attachment
    private void syncTmpEndpointAttachment(ServerLevel serverLevel, Vec3 worldAnchorPos) {
        LauncherEndpointBlockEntity endpoint = getTmpEndpoint(serverLevel);
        if (endpoint == null) {
            return;
        }
        Vec3 sanitized = sanitizeAnchorPosition(worldAnchorPos, serverLevel);
        if (sanitized == null) {
            return;
        }
        endpoint.setAttachmentPoint(sanitized);
        ServerRopeStrand strand = ropeHolder == null ? null : ropeHolder.getAttachedStrand();
        RopeAttachment endAttachment = strand == null ? null : strand.getAttachment(RopeAttachmentPoint.END);
        if (endAttachment != null && targetKnotPos != null
                && targetKnotPos.equals(endAttachment.blockAttachment())
                && java.util.Objects.equals(targetKnotSubLevelId, endAttachment.subLevelID())) {
            applyEndAttachmentPoint(serverLevel, strand, endAttachment);
        }
    }

    // Apply the end attachment point
    private void applyEndAttachmentPoint(ServerLevel serverLevel, ServerRopeStrand strand, RopeAttachment attachment) {
        if (!strand.isActive()) {
            return;
        }
        BlockEntity blockEntity = EntityLauncherVirtualRopeEndpoints.find(serverLevel, attachment.subLevelID(),
                attachment.blockAttachment());
        if (blockEntity == null) {
            blockEntity = SimulatedHelper.findBlockEntity(serverLevel, attachment.subLevelID(), attachment.blockAttachment());
        }
        RopeStrandHolderBehavior holder = resolveHolderBehavior(blockEntity);
        if (holder == null) {
            return;
        }

        ServerSubLevel subLevel = null;
        if (attachment.subLevelID() != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) {
                return;
            }
            subLevel = (ServerSubLevel) container.getSubLevel(attachment.subLevelID());
            if (subLevel == null) {
                return;
            }
        }

        strand.setAttachment(RopeHandle.AttachmentPoint.END,
                JOMLConversion.toJOML((Position) holder.getAttachmentPoint()), subLevel);
    }

    // Get the server sublevel
    private static @Nullable ServerSubLevel getServerSubLevel(ServerLevel serverLevel, @Nullable UUID subLevelId) {
        if (subLevelId == null) {
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return null;
        }
        Object subLevel = container.getSubLevel(subLevelId);
        return subLevel instanceof ServerSubLevel serverSubLevel ? serverSubLevel : null;
    }

    // Remove the free tmp endpoint
    private static void removeFreeTmpEndpoint(ServerLevel serverLevel, @Nullable UUID subLevelId, BlockPos endpointPos) {
        if (EntityLauncherVirtualRopeEndpoints.find(serverLevel, subLevelId, endpointPos) instanceof LauncherEndpointBlockEntity) {
            EntityLauncherVirtualRopeEndpoints.remove(serverLevel, subLevelId, endpointPos);
            return;
        }
        if (subLevelId != null) {
            return;
        }
        BlockEntity blockEntity = SimulatedHelper.findBlockEntity(serverLevel, subLevelId, endpointPos);
        if (!(blockEntity instanceof LauncherEndpointBlockEntity endpoint) || !endpoint.isFree()) {
            return;
        }
        serverLevel.removeBlock(endpointPos, false);
    }

    // Get the server anchor target world pos
    private @Nullable Vec3 getServerAnchorTargetWorldPos(ServerLevel serverLevel) {
        if (targetLocalAnchorPos == null) {
            return null;
        }
        if (targetSubLevelId == null) {
            return sanitizeAnchorPosition(targetLocalAnchorPos, serverLevel);
        }
        Object subLevel = getSubLevelById(serverLevel, targetSubLevelId);
        return resolveStoredAnchorWorldPos(serverLevel, subLevel);
    }

    // Resolve the stored anchor world pos
    private @Nullable Vec3 resolveStoredAnchorWorldPos(Level queryLevel, @Nullable Object subLevel) {
        Vec3 stored = sanitizeAnchorPosition(targetLocalAnchorPos, queryLevel);
        if (stored == null) {
            return null;
        }

        Vec3 projectedWorld = sanitizeAnchorPosition(SimulatedHelper.projectOutOfSubLevels(queryLevel, stored), queryLevel);
        if (subLevel == null) {
            return projectedWorld;
        }

        Vec3 transformedWorld = sanitizeAnchorPosition(SimulatedHelper.toContainingWorldPosition(subLevel, stored), queryLevel);
        if (transformedWorld == null) {
            return projectedWorld;
        }
        if (projectedWorld == null) {
            return transformedWorld;
        }

        Vec3 muzzle = getBaseAttachmentWorldPos();
        double transformedDistanceSq = transformedWorld.distanceToSqr(muzzle);
        double projectedDistanceSq = projectedWorld.distanceToSqr(muzzle);

        if (projectedDistanceSq + 1.0E-6D < transformedDistanceSq) {
            Vec3 normalizedLocal = sanitizeAnchorPosition(SimulatedHelper.toContainingLocalPosition(subLevel, projectedWorld), queryLevel);
            if (normalizedLocal != null) {
                targetLocalAnchorPos = normalizedLocal;
                setChanged();
            }
            return projectedWorld;
        }

        return transformedWorld;
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

    // Get the barrel pivot world pos
    private Vec3 getBarrelPivotWorldPos() {
        return toAnchorWorldPosition(barrelPivotLocalPos());
    }

    // Get the barrel pivot local pos
    private Vec3 barrelPivotLocalPos() {
        return Vec3.atLowerCornerOf(worldPosition).add(barrelPivotLocal(getBlockState()));
    }

    // Convert the entity launcher anchor to anchor world position
    private Vec3 toAnchorWorldPosition(Vec3 localPosition) {
        if (localPosition == null) {
            return null;
        }
        Object subLevel = SimulatedHelper.getContainingSubLevel(this);
        Vec3 world = subLevel == null ? localPosition : SimulatedHelper.toContainingWorldPosition(subLevel, localPosition);
        return SimulatedHelper.projectOutOfSubLevels(level, world == null ? localPosition : world);
    }

    // Convert the entity launcher anchor to anchor local position
    private @Nullable Vec3 toAnchorLocalPosition(@Nullable Vec3 worldPosition) {
        if (worldPosition == null) {
            return null;
        }
        Vec3 projectedWorld = SimulatedHelper.projectOutOfSubLevels(level, worldPosition);
        Object subLevel = SimulatedHelper.getContainingSubLevel(this);
        if (subLevel == null) {
            return projectedWorld;
        }
        Vec3 local = SimulatedHelper.toContainingLocalPosition(subLevel, projectedWorld);
        return local == null ? projectedWorld : local;
    }

    // Convert the entity launcher anchor to anchor world direction
    private Vec3 toAnchorWorldDirection(Vec3 localDirection) {
        if (localDirection == null || localDirection.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        double length = localDirection.length();
        Object subLevel = SimulatedHelper.getContainingSubLevel(this);
        Vec3 worldDirection = subLevel == null
                ? localDirection
                : SimulatedHelper.toContainingWorldDirection(subLevel, localDirection);
        if (worldDirection == null || worldDirection.lengthSqr() < 1.0E-6D) {
            return localDirection.normalize().scale(length);
        }
        return worldDirection.normalize().scale(length);
    }

    // Get the barrel pivot local
    private static Vec3 barrelPivotLocal(BlockState state) {
        return EntityLauncherAnchorAim.barrelPivotLocal(mountNormal(state));
    }

    // Get the mount normal
    private static Direction mountNormal(BlockState state) {
        Direction facing = state.hasProperty(EntityLauncherAnchorBlock.FACING)
                ? state.getValue(EntityLauncherAnchorBlock.FACING)
                : Direction.NORTH;
        return facing;
    }

    // Get the constrained aim direction
    private static Vec3 constrainedAimDirection(BlockState state, Vec3 pivotWorld, Vec3 targetWorld) {
        return EntityLauncherAnchorAim.solve(mountNormal(state), targetWorld.subtract(pivotWorld)).direction();
    }

    // Get the entity attachment pos
    private Vec3 getEntityAttachmentPos(Entity entity) {
        Vec3 attachment;
        if (targetEntityAttachOffset.lengthSqr() < 1.0E-6D) {
            attachment = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        } else {
            attachment = entity.position().add(targetEntityAttachOffset);
        }

        return SimulatedHelper.projectOutOfSubLevels(level, attachment);
    }

    // Mark the changed and sync
    private void markChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    // Get the sublevel by id
    @Nullable
    private static Object getSubLevelById(Level level, @Nullable UUID subLevelId) {
        return SableLevelApi.subLevel(level, subLevelId);
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

        List<SubLevel> dependencies = new ArrayList<>(2);
        addConnectionDependency(dependencies, container, targetSubLevelId);
        addConnectionDependency(dependencies, container, targetKnotSubLevelId);
        return dependencies.isEmpty() ? null : dependencies;
    }

    // Add the connection dependency
    private static void addConnectionDependency(List<SubLevel> dependencies, SubLevelContainer container,
                                                @Nullable UUID subLevelId) {
        if (subLevelId == null) {
            return;
        }
        SubLevel subLevel = container.getSubLevel(subLevelId);
        if (subLevel != null && !subLevel.isRemoved() && !dependencies.contains(subLevel)) {
            dependencies.add(subLevel);
        }
    }

    // Handle the chunk unloaded event
    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
    }

    // Get the entity by UUID
    @Nullable
    private Entity getEntityByUuid(Level level, UUID entityId) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            return serverLevel.getEntity(entityId);
        }
        AABB searchBounds = new AABB(worldPosition).inflate(512.0);
        for (Entity entity : level.getEntitiesOfClass(
                Entity.class, searchBounds, entity -> entityId.equals(entity.getUUID()))) {
            return entity;
        }
        return null;
    }

    // Write the entity launcher anchor
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("RopeLength", ropeLength);
        if (targetEntityId != null) {
            tag.putUUID("TargetEntity", targetEntityId);
            tag.putDouble("TargetEntityAttachX", targetEntityAttachOffset.x);
            tag.putDouble("TargetEntityAttachY", targetEntityAttachOffset.y);
            tag.putDouble("TargetEntityAttachZ", targetEntityAttachOffset.z);
        }
        if (targetSubLevelId != null) {
            tag.putUUID("TargetSubLevel", targetSubLevelId);
        }
        if (targetLocalAnchorPos != null) {
            tag.putDouble("TargetLocalX", targetLocalAnchorPos.x);
            tag.putDouble("TargetLocalY", targetLocalAnchorPos.y);
            tag.putDouble("TargetLocalZ", targetLocalAnchorPos.z);
        }
        if (targetKnotPos != null) {
            tag.put("TargetKnotPos", NbtUtils.writeBlockPos(targetKnotPos));
        }
        if (targetKnotSubLevelId != null) {
            tag.putUUID("TargetKnotSubLevel", targetKnotSubLevelId);
        }
        tag.putBoolean("TargetTemporaryEndpoint", targetTemporaryEndpoint);
        if (clientPacket && controlledAimDirection != null) {
            tag.putDouble("ControlledAimX", controlledAimDirection.x);
            tag.putDouble("ControlledAimY", controlledAimDirection.y);
            tag.putDouble("ControlledAimZ", controlledAimDirection.z);
        }
        if (mountedClawId != null) {
            tag.putUUID("MountedClaw", mountedClawId);
        }
        remapSchematicReferences(tag, false);
    }

    // Read the entity launcher anchor
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        remapSchematicReferences(tag, true);
        super.read(tag, registries, clientPacket);
        ropeLength = tag.contains("RopeLength") ? tag.getDouble("RopeLength") : 3.0;
        targetEntityId = tag.hasUUID("TargetEntity") ? tag.getUUID("TargetEntity") : null;
        if (tag.contains("TargetEntityAttachX") && tag.contains("TargetEntityAttachY") && tag.contains("TargetEntityAttachZ")) {
            targetEntityAttachOffset = new Vec3(
                    tag.getDouble("TargetEntityAttachX"),
                    tag.getDouble("TargetEntityAttachY"),
                    tag.getDouble("TargetEntityAttachZ"));
        } else {
            targetEntityAttachOffset = Vec3.ZERO;
        }
        targetSubLevelId = tag.hasUUID("TargetSubLevel") ? tag.getUUID("TargetSubLevel") : null;
        if (tag.contains("TargetLocalX") && tag.contains("TargetLocalY") && tag.contains("TargetLocalZ")) {
            targetLocalAnchorPos = new Vec3(tag.getDouble("TargetLocalX"), tag.getDouble("TargetLocalY"), tag.getDouble("TargetLocalZ"));
        } else {
            targetLocalAnchorPos = null;
        }

        targetLocalAnchorPos = sanitizeAnchorPosition(targetLocalAnchorPos, level);
        targetKnotPos = tag.contains("TargetKnotPos") ? NbtUtils.readBlockPos(tag, "TargetKnotPos").orElse(null) : null;
        targetKnotSubLevelId = tag.hasUUID("TargetKnotSubLevel") ? tag.getUUID("TargetKnotSubLevel") : null;
        targetTemporaryEndpoint = tag.getBoolean("TargetTemporaryEndpoint");
        controlledAimDirection = tag.contains("ControlledAimX") && tag.contains("ControlledAimY") && tag.contains("ControlledAimZ")
                ? new Vec3(tag.getDouble("ControlledAimX"), tag.getDouble("ControlledAimY"), tag.getDouble("ControlledAimZ")).normalize()
                : null;
        mountedClawId = tag.hasUUID("MountedClaw") ? tag.getUUID("MountedClaw") : null;
        pendingSubLevelReconnect = targetSubLevelId != null && targetLocalAnchorPos != null;
        pendingLoadedAttachmentResync = !clientPacket && targetSubLevelId == null && targetKnotPos != null;
    }

    // Remap the schematic references
    private static void remapSchematicReferences(CompoundTag tag, boolean reading) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null) return;

        if (tag.hasUUID("TargetSubLevel")) {
            UUID oldId = tag.getUUID("TargetSubLevel");
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(oldId);
            if (mapping == null) {
                tag.remove("TargetSubLevel");
                tag.remove("TargetLocalX");
                tag.remove("TargetLocalY");
                tag.remove("TargetLocalZ");
            } else {
                tag.putUUID("TargetSubLevel", mapping.newUUID());
                if (tag.contains("TargetLocalX") && tag.contains("TargetLocalY") && tag.contains("TargetLocalZ")) {
                    Vec3 pos = new Vec3(tag.getDouble("TargetLocalX"), tag.getDouble("TargetLocalY"),
                            tag.getDouble("TargetLocalZ"));
                    Vec3 transformed = transformPosition(mapping, pos);
                    tag.putDouble("TargetLocalX", transformed.x);
                    tag.putDouble("TargetLocalY", transformed.y);
                    tag.putDouble("TargetLocalZ", transformed.z);
                }
            }
        }

        if (!tag.contains("TargetKnotPos")) return;
        BlockPos knotPos = NbtUtils.readBlockPos(tag, "TargetKnotPos").orElse(null);
        if (knotPos == null) return;
        if (tag.hasUUID("TargetKnotSubLevel")) {
            UUID oldId = tag.getUUID("TargetKnotSubLevel");
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(oldId);
            if (mapping == null) {
                tag.remove("TargetKnotSubLevel");
                tag.remove("TargetKnotPos");
            } else {
                tag.putUUID("TargetKnotSubLevel", mapping.newUUID());
                tag.put("TargetKnotPos", NbtUtils.writeBlockPos(mapping.transform().apply(knotPos)));
            }
        } else if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
            if (ctx.getBoundingBox().contains(knotPos.getX(), knotPos.getY(), knotPos.getZ())) {
                tag.put("TargetKnotPos", NbtUtils.writeBlockPos(ctx.getPlaceTransform().apply(knotPos)));
            } else {
                tag.remove("TargetKnotPos");
            }
        } else {
            BlockPos transformed = reading
                    ? ctx.getPlaceTransform().apply(knotPos)
                    : ctx.getSetupTransform().apply(knotPos);
            tag.put("TargetKnotPos", NbtUtils.writeBlockPos(transformed));
        }
    }

    // Transform the position
    private static Vec3 transformPosition(SubLevelSchematicSerializationContext.SchematicMapping mapping,
                                          Vec3 pos) {
        BlockPos base = BlockPos.containing(pos);
        Vec3 fractional = pos.subtract(base.getX(), base.getY(), base.getZ());
        BlockPos origin = mapping.transform().apply(base);
        BlockPos xAxis = mapping.transform().apply(base.east()).subtract(origin);
        BlockPos yAxis = mapping.transform().apply(base.above()).subtract(origin);
        BlockPos zAxis = mapping.transform().apply(base.south()).subtract(origin);
        return new Vec3(origin.getX(), origin.getY(), origin.getZ())
                .add(xAxis.getX() * fractional.x + yAxis.getX() * fractional.y + zAxis.getX() * fractional.z,
                        xAxis.getY() * fractional.x + yAxis.getY() * fractional.y + zAxis.getY() * fractional.z,
                        xAxis.getZ() * fractional.x + yAxis.getZ() * fractional.y + zAxis.getZ() * fractional.z);
    }

    // Create the launcher anchor update tag
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putDouble("RopeLength", ropeLength);
        if (targetEntityId != null) {
            tag.putUUID("TargetEntity", targetEntityId);
            tag.putDouble("TargetEntityAttachX", targetEntityAttachOffset.x);
            tag.putDouble("TargetEntityAttachY", targetEntityAttachOffset.y);
            tag.putDouble("TargetEntityAttachZ", targetEntityAttachOffset.z);
        }
        if (targetSubLevelId != null) {
            tag.putUUID("TargetSubLevel", targetSubLevelId);
        }
        if (targetLocalAnchorPos != null) {
            tag.putDouble("TargetLocalX", targetLocalAnchorPos.x);
            tag.putDouble("TargetLocalY", targetLocalAnchorPos.y);
            tag.putDouble("TargetLocalZ", targetLocalAnchorPos.z);
        }
        if (targetKnotPos != null) {
            tag.put("TargetKnotPos", NbtUtils.writeBlockPos(targetKnotPos));
        }
        if (targetKnotSubLevelId != null) {
            tag.putUUID("TargetKnotSubLevel", targetKnotSubLevelId);
        }
        tag.putBoolean("TargetTemporaryEndpoint", targetTemporaryEndpoint);
        if (controlledAimDirection != null) {
            tag.putDouble("ControlledAimX", controlledAimDirection.x);
            tag.putDouble("ControlledAimY", controlledAimDirection.y);
            tag.putDouble("ControlledAimZ", controlledAimDirection.z);
        }
        if (mountedClawId != null) {
            tag.putUUID("MountedClaw", mountedClawId);
        }
        return tag;
    }

    // Create the launcher anchor update packet
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
