package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.kinetics.LinkedKineticBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Drive a gantry cable while keeping its remote wheel loaded without joining both ships structurally
public class PhysicsGantryBeltWheelBlockEntity extends LinkedKineticBlockEntity
        implements BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long LINK_RESOLVE_RETRY_TICKS = 20L;
    private static final long ASSEMBLY_TRANSFER_TIMEOUT_TICKS = 40L;
    private static final Map<BeltWheelMoveKey, BeltWheelMoveTarget> ASSEMBLY_TRANSFER_TARGETS =
            new ConcurrentHashMap<>();
    private static final Map<BeltWheelMoveTransition, BeltWheelMoveKey> ASSEMBLY_TRANSFER_KEYS =
            new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current linked pos
    @Nullable
    private BlockPos linkedPos;
    // Current linked sub-level id
    @Nullable
    private UUID linkedSubLevelId;
    // Tracks whether receives from linked wheel is set
    private boolean receivesFromLinkedWheel;
    // Cached linked wheel
    @Nullable
    private transient PhysicsGantryBeltWheelBlockEntity cachedLinkedWheel;
    // Next linked wheel resolve tick
    private transient long nextLinkedWheelResolveTick = Long.MIN_VALUE;
    // Endpoint location before the current Sable transfer rewrites normal link data
    @Nullable
    private transient BlockPos assemblyTransferLinkedPos;
    // Endpoint sub-level before the current Sable transfer rewrites normal link data
    @Nullable
    private transient UUID assemblyTransferLinkedSubLevelId;
    // Last tick at which the transfer endpoint may be used
    private transient long assemblyTransferExpiresAtTick;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry belt wheel
    public PhysicsGantryBeltWheelBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.PHYSICS_GANTRY_BELT_WHEEL.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the physics gantry belt wheel
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        retargetLinkedWheelAfterAssemblyTransfer();
        boolean nextReceiveFromLink = linkedPos != null && linkedPos.equals(source);
        if (receivesFromLinkedWheel != nextReceiveFromLink) {
            receivesFromLinkedWheel = nextReceiveFromLink;
            sendData();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the saved spline endpoint for the library's reciprocal kinetic connection
    @Override
    @Nullable
    protected LinkedKineticBlockEntity resolveKineticLink() {
        PhysicsGantryBeltWheelBlockEntity other = resolveLinkedWheel();
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(this);
        return other != null && other.references(worldPosition, subLevelId) ? other : null;
    }

    // Check if this has linked target
    public boolean hasLinkedTarget() {
        return linkedPos != null;
    }

    // Check if this should render link from this endpoint
    public boolean shouldRenderLinkFromThisEndpoint() {
        PhysicsGantryBeltWheelBlockEntity other = resolveLinkedWheel();
        return other != null && endpointKey(this).compareTo(endpointKey(other)) < 0;
    }

    // Set the linked target
    public void setLinkedTarget(BlockPos targetPos, @Nullable UUID targetSubLevelId) {
        linkedPos = targetPos.immutable();
        linkedSubLevelId = targetSubLevelId;
        invalidateLinkedWheelCache();
        receivesFromLinkedWheel = false;
        refreshKineticLink();
        setChanged();
        sendData();
    }

    // Check if the belt wheel references the target
    public boolean references(BlockPos pos, @Nullable UUID subLevelId) {
        return linkedPos != null && linkedPos.equals(pos) && Objects.equals(linkedSubLevelId, subLevelId);
    }

    // Handle the break link
    public void breakLink(boolean notifyOther) {
        if (!hasLinkedTarget()) {
            return;
        }

        BlockPos previousPos = linkedPos;
        UUID previousSubLevelId = linkedSubLevelId;
        linkedPos = null;
        linkedSubLevelId = null;
        invalidateLinkedWheelCache();
        receivesFromLinkedWheel = false;
        refreshKineticLink();
        setChanged();
        sendData();

        if (!notifyOther || level == null || previousPos == null) {
            return;
        }

        PhysicsGantryBeltWheelBlockEntity other = SimulatedHelper.findBlockEntity(level, previousSubLevelId, previousPos,
                PhysicsGantryBeltWheelBlockEntity.class);
        if (other != null && !other.isRemoved()
                && other.references(worldPosition, SimulatedHelper.getContainingSubLevelId(this))) {
            other.breakLink(false);
        }
    }

    // Record the source endpoint before Sable serializes it to its new location.
    public void beginAssemblyTransfer(ServerLevel originLevel, BlockPos oldPos, BlockPos newPos) {
        if (!hasLinkedTarget()) {
            return;
        }

        UUID sourceSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        assemblyTransferLinkedPos = linkedPos.immutable();
        assemblyTransferLinkedSubLevelId = linkedSubLevelId;
        BeltWheelMoveKey sourceKey = new BeltWheelMoveKey(
                originLevel.dimension().location().toString(), oldPos.immutable(), sourceSubLevelId);
        long expiresAtTick = originLevel.getGameTime() + ASSEMBLY_TRANSFER_TIMEOUT_TICKS;
        ASSEMBLY_TRANSFER_TARGETS.put(sourceKey,
                new BeltWheelMoveTarget(newPos.immutable(), null, false, expiresAtTick));
        ASSEMBLY_TRANSFER_KEYS.put(new BeltWheelMoveTransition(
                        originLevel.dimension().location().toString(), oldPos.immutable(), newPos.immutable()),
                sourceKey);
    }

    // Record the endpoint Sable created so its linked wheel can retarget on the next tick.
    public void finishAssemblyTransfer(ServerLevel originLevel, BlockPos oldPos, BlockPos newPos) {
        BeltWheelMoveTransition transition = new BeltWheelMoveTransition(
                originLevel.dimension().location().toString(), oldPos.immutable(), newPos.immutable());
        BeltWheelMoveKey sourceKey = ASSEMBLY_TRANSFER_KEYS.get(transition);
        if (sourceKey == null) {
            return;
        }

        long expiresAtTick = originLevel.getGameTime() + ASSEMBLY_TRANSFER_TIMEOUT_TICKS;
        assemblyTransferExpiresAtTick = expiresAtTick;
        ASSEMBLY_TRANSFER_TARGETS.put(sourceKey, new BeltWheelMoveTarget(
                newPos.immutable(), SimulatedHelper.getContainingSubLevelId(this), true, expiresAtTick));

        // Sable transfers the wheels one at a time. Once the second endpoint
        // arrives, repair both sides immediately instead of waiting for an
        // eventual block-entity tick in the newly created sub-level.
        retargetLinkedWheelAfterAssemblyTransfer();
        PhysicsGantryBeltWheelBlockEntity linkedWheel = resolveLinkedWheel();
        if (linkedWheel != null && wasMovedByAssemblyTransfer(linkedWheel)) {
            linkedWheel.retargetLinkedWheelAfterAssemblyTransfer();
        }
    }

    // Resolve the linked wheel
    @Nullable
    public PhysicsGantryBeltWheelBlockEntity resolveLinkedWheel() {
        if (level == null || linkedPos == null) {
            return null;
        }

        if (cachedLinkedWheel != null) {
            if (!cachedLinkedWheel.isRemoved() && linkedPos.equals(cachedLinkedWheel.getBlockPos())
                    && level.isLoaded(linkedPos)) {
                return cachedLinkedWheel;
            }
            invalidateLinkedWheelCache();
        }

        long gameTime = level.getGameTime();
        if (gameTime < nextLinkedWheelResolveTick) {
            return null;
        }
        nextLinkedWheelResolveTick = gameTime + LINK_RESOLVE_RETRY_TICKS;

        BlockEntity loadedTarget =
                SimulatedHelper.findLoadedBlockEntityExact(level, linkedSubLevelId, linkedPos);
        if (loadedTarget instanceof PhysicsGantryBeltWheelBlockEntity linkedWheel && linkedWheel != this) {
            cachedLinkedWheel = linkedWheel;
            return linkedWheel;
        }

        UUID currentSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        if (linkedSubLevelId == null && (currentSubLevelId != null
                || SubLevelBlockEntityCollector.isSubLevelPlotPosition(level, linkedPos))) {
            PhysicsGantryBeltWheelBlockEntity migratedTarget =
                    SimulatedHelper.findBlockEntityIncludingSubLevels(
                            level, linkedPos, PhysicsGantryBeltWheelBlockEntity.class);
            if (migratedTarget != null && migratedTarget != this && !migratedTarget.isRemoved()) {
                linkedSubLevelId = SimulatedHelper.getContainingSubLevelId(migratedTarget);
                if (!level.isClientSide) setChanged();
                cachedLinkedWheel = migratedTarget;
                return migratedTarget;
            }
        }
        return null;
    }

    // Get the world anchor position
    public Vec3 getWorldAnchorPosition() {
        Vec3 local = Vec3.atCenterOf(worldPosition);

        Vec3 transformed = SimulatedHelper.toGlobalWorldPosition(this, local);
        return transformed == null ? local : transformed;
    }

    // Get the anchor position in render frame
    public Vec3 getAnchorPositionInRenderFrameOf(PhysicsGantryBeltWheelBlockEntity renderOrigin) {
        Vec3 local = Vec3.atCenterOf(worldPosition);

        Vec3 transformed = SimulatedHelper.toRenderFramePosition(this, local, renderOrigin);
        if (transformed != null
                && Double.isFinite(transformed.x)
                && Double.isFinite(transformed.y)
                && Double.isFinite(transformed.z)) {
            return transformed;
        }
        Vec3 worldAnchor = getWorldAnchorPosition();
        Vec3 origin = Vec3.atLowerCornerOf(renderOrigin.getBlockPos());
        return worldAnchor.subtract(origin);
    }

    // Get the linked world anchor position
    @Nullable
    public Vec3 getLinkedWorldAnchorPosition() {
        PhysicsGantryBeltWheelBlockEntity linkedWheel = resolveLinkedWheel();
        if (linkedWheel == null) {
            return null;
        }
        return linkedWheel.getWorldAnchorPosition();
    }

    // Get the wrench status component
    public Component getWrenchStatusComponent() {
        if (!hasLinkedTarget()) {
            return Component.translatable("createthrusters.physics_gantry_belt_wheel.status_unlinked")
                    .withStyle(ChatFormatting.GRAY);
        }

        PhysicsGantryBeltWheelBlockEntity linkedWheel = resolveLinkedWheel();
        if (linkedWheel == null || linkedWheel.isRemoved()) {
            return Component.translatable("createthrusters.physics_gantry_belt_wheel.status_missing")
                    .withStyle(ChatFormatting.RED);
        }

        Vec3 start = getWorldAnchorPosition();
        Vec3 end = linkedWheel.getWorldAnchorPosition();
        int blocks = 0;
        if (start != null && end != null) {
            blocks = (int) Math.round(start.distanceTo(end));
        }

        String roleKey = receivesFromLinkedWheel
                ? "createthrusters.physics_gantry_belt_wheel.role_receiver"
                : "createthrusters.physics_gantry_belt_wheel.role_driver";
        String directionKey = receivesFromLinkedWheel
                ? "createthrusters.physics_gantry_belt_wheel.direction_in"
                : "createthrusters.physics_gantry_belt_wheel.direction_out";

        return Component.translatable("createthrusters.physics_gantry_belt_wheel.status_linked",
                        Component.translatable(roleKey),
                        Component.translatable(directionKey),
                        blocks)
                .withStyle(ChatFormatting.AQUA);
    }

    // Invalidate the linked wheel cache
    private void invalidateLinkedWheelCache() {
        cachedLinkedWheel = null;
        nextLinkedWheelResolveTick = Long.MIN_VALUE;
    }

    // Update an endpoint that was moved in the same Sable assembly operation.
    private void retargetLinkedWheelAfterAssemblyTransfer() {
        if (level == null || linkedPos == null) {
            return;
        }

        long gameTime = level.getGameTime();
        pruneExpiredAssemblyTransfers(gameTime);
        boolean hasTransferReference = assemblyTransferLinkedPos != null
                && assemblyTransferExpiresAtTick >= gameTime;
        BlockPos lookupPos = hasTransferReference ? assemblyTransferLinkedPos : linkedPos;
        UUID lookupSubLevelId = hasTransferReference ? assemblyTransferLinkedSubLevelId : linkedSubLevelId;
        if (assemblyTransferLinkedPos != null && !hasTransferReference) {
            clearAssemblyTransferReference();
        }
        BeltWheelMoveKey linkedKey = new BeltWheelMoveKey(
                level.dimension().location().toString(), lookupPos, lookupSubLevelId);
        BeltWheelMoveTarget movedTarget = ASSEMBLY_TRANSFER_TARGETS.get(linkedKey);
        if (movedTarget == null || !movedTarget.complete()) {
            return;
        }

        if (linkedPos.equals(movedTarget.position())
                && Objects.equals(linkedSubLevelId, movedTarget.subLevelId())) {
            return;
        }

        linkedPos = movedTarget.position();
        linkedSubLevelId = movedTarget.subLevelId();
        clearAssemblyTransferReference();
        invalidateLinkedWheelCache();
        refreshKineticLink();
        setChanged();
        sendData();
    }

    // Remove expired transfer data after every endpoint has had a chance to retarget.
    private static void pruneExpiredAssemblyTransfers(long gameTime) {
        ASSEMBLY_TRANSFER_TARGETS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() < gameTime);
        ASSEMBLY_TRANSFER_KEYS.entrySet().removeIf(entry ->
                !ASSEMBLY_TRANSFER_TARGETS.containsKey(entry.getValue()));
    }

    // Clear the temporary source endpoint once the pair has been remapped.
    private void clearAssemblyTransferReference() {
        assemblyTransferLinkedPos = null;
        assemblyTransferLinkedSubLevelId = null;
        assemblyTransferExpiresAtTick = 0L;
    }

    // Check whether the endpoint was already recreated by the current transfer.
    private static boolean wasMovedByAssemblyTransfer(PhysicsGantryBeltWheelBlockEntity wheel) {
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(wheel);
        return ASSEMBLY_TRANSFER_TARGETS.values().stream().anyMatch(target -> target.complete()
                && target.position().equals(wheel.getBlockPos())
                && Objects.equals(target.subLevelId(), subLevelId));
    }

    // Get the endpoint key
    private static String endpointKey(PhysicsGantryBeltWheelBlockEntity be) {
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(be);
        String subLevel = subLevelId == null ? "world" : subLevelId.toString();
        BlockPos pos = be.getBlockPos();
        return subLevel + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    // Identify an endpoint before Sable transfers it.
    private record BeltWheelMoveKey(String dimension, BlockPos position, UUID subLevelId) {
    }

    // Identify one Sable transfer operation.
    private record BeltWheelMoveTransition(String dimension, BlockPos oldPos, BlockPos newPos) {
    }

    // Store the endpoint Sable recreated for a pending transfer.
    private record BeltWheelMoveTarget(BlockPos position, UUID subLevelId, boolean complete, long expiresAtTick) {
    }

    // Write the physics gantry belt wheel
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        BlockPos savedLinkedPos = linkedPos;
        UUID savedLinkedSubLevelId = linkedSubLevelId;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null && savedLinkedPos != null) {
            if (savedLinkedSubLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping =
                        ctx.getMapping(savedLinkedSubLevelId);
                if (mapping == null) {
                    savedLinkedPos = null;
                    savedLinkedSubLevelId = null;
                } else {
                    savedLinkedPos = mapping.transform().apply(savedLinkedPos);
                    savedLinkedSubLevelId = mapping.newUUID();
                }
            } else if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
                savedLinkedPos = ctx.getBoundingBox().contains(
                        savedLinkedPos.getX(), savedLinkedPos.getY(), savedLinkedPos.getZ())
                        ? ctx.getPlaceTransform().apply(savedLinkedPos)
                        : null;
            } else {
                savedLinkedPos = ctx.getSetupTransform().apply(savedLinkedPos);
            }
        }
        if (savedLinkedPos != null) {
            tag.putLong("LinkedPos", savedLinkedPos.asLong());
        }
        if (savedLinkedSubLevelId != null) {
            tag.putUUID("LinkedSubLevelId", savedLinkedSubLevelId);
        }
        if (!clientPacket && assemblyTransferLinkedPos != null) {
            tag.putLong("AssemblyTransferLinkedPos", assemblyTransferLinkedPos.asLong());
            if (assemblyTransferLinkedSubLevelId != null) {
                tag.putUUID("AssemblyTransferLinkedSubLevelId", assemblyTransferLinkedSubLevelId);
            }
        }
        tag.putBoolean("ReceivesFromLinkedWheel", receivesFromLinkedWheel);
    }

    // Read the physics gantry belt wheel
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        BlockPos previousLinkedPos = linkedPos;
        UUID previousLinkedSubLevelId = linkedSubLevelId;
        linkedPos = tag.contains("LinkedPos") ? BlockPos.of(tag.getLong("LinkedPos")) : null;
        linkedSubLevelId = tag.contains("LinkedSubLevelId") ? tag.getUUID("LinkedSubLevelId") : null;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null && ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE
                && linkedPos != null) {
            if (linkedSubLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(linkedSubLevelId);
                if (mapping == null) {
                    linkedPos = null;
                    linkedSubLevelId = null;
                } else {
                    linkedPos = mapping.transform().apply(linkedPos);
                    linkedSubLevelId = mapping.newUUID();
                }
            } else {
                linkedPos = ctx.getPlaceTransform().apply(linkedPos);
            }
        }
        if (!Objects.equals(previousLinkedPos, linkedPos)
                || !Objects.equals(previousLinkedSubLevelId, linkedSubLevelId)) {
            invalidateLinkedWheelCache();
        }
        assemblyTransferLinkedPos = tag.contains("AssemblyTransferLinkedPos")
                ? BlockPos.of(tag.getLong("AssemblyTransferLinkedPos")) : null;
        assemblyTransferLinkedSubLevelId = tag.contains("AssemblyTransferLinkedSubLevelId")
                ? tag.getUUID("AssemblyTransferLinkedSubLevelId") : null;
        assemblyTransferExpiresAtTick = 0L;
        receivesFromLinkedWheel = tag.getBoolean("ReceivesFromLinkedWheel");
        if (!clientPacket && tag.contains("GeneratedLinkSpeed")) {
            rebuildKineticNetworkOnLoad();
        }
    }

    // Get the loading dependencies
    @Override
    public Iterable<SubLevel> sable$getLoadingDependencies() {
        if (level == null || linkedSubLevelId == null) return List.of();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        SubLevel subLevel = container.getSubLevel(linkedSubLevelId);
        return subLevel == null || subLevel.isRemoved() ? List.of() : List.of(subLevel);
    }

}
