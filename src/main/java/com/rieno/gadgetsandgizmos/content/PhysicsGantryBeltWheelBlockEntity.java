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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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
    public static final int MAX_BELTS = 8;
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
    // Whether breaking this link should return the belt connector consumed to create it
    private boolean returnsBeltOnBreak;
    private final List<BeltLink> additionalLinks = new ArrayList<>();
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
    private transient List<BeltLink> assemblyTransferAdditionalLinks = List.of();
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
        if (!nextReceiveFromLink) {
            for (BeltLink link : additionalLinks) {
                if (link.pos().equals(source)) {
                    nextReceiveFromLink = true;
                    break;
                }
            }
        }
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

    @Override
    protected Collection<? extends LinkedKineticBlockEntity> resolveKineticLinks() {
        UUID ownSubLevel = SimulatedHelper.getContainingSubLevelId(this);
        List<LinkedKineticBlockEntity> wheels = new ArrayList<>();
        for (BeltLink link : allLinks()) {
            PhysicsGantryBeltWheelBlockEntity other = resolveLinkedWheel(link);
            if (other != null && other.references(worldPosition, ownSubLevel)) wheels.add(other);
        }
        return wheels;
    }

    // Check if this has linked target
    public boolean hasLinkedTarget() {
        return linkCount() > 0;
    }

    public int linkCount() {
        return (linkedPos == null ? 0 : 1) + additionalLinks.size();
    }

    public boolean canAddLink(BlockPos pos, @Nullable UUID subLevelId) {
        return linkCount() < MAX_BELTS && !references(pos, subLevelId);
    }

    public boolean addLinkedTarget(BlockPos pos, @Nullable UUID subLevelId, boolean refundable) {
        if (!canAddLink(pos, subLevelId)) return false;
        if (linkedPos == null) {
            linkedPos = pos.immutable();
            linkedSubLevelId = subLevelId;
            returnsBeltOnBreak = refundable;
        } else {
            additionalLinks.add(new BeltLink(pos.immutable(), subLevelId, refundable));
        }
        invalidateLinkedWheelCache();
        refreshKineticLink();
        setChanged();
        sendData();
        return true;
    }

    public List<PhysicsGantryBeltWheelBlockEntity> getRenderableLinkedWheels() {
        List<PhysicsGantryBeltWheelBlockEntity> wheels = new ArrayList<>();
        UUID ownSubLevel = SimulatedHelper.getContainingSubLevelId(this);
        String ownKey = endpointKey(this);
        for (BeltLink link : allLinks()) {
            PhysicsGantryBeltWheelBlockEntity other = resolveLinkedWheel(link);
            if (other != null && other.references(worldPosition, ownSubLevel)
                    && ownKey.compareTo(endpointKey(other)) < 0) wheels.add(other);
        }
        return wheels;
    }

    // Check if this should render link from this endpoint
    public boolean shouldRenderLinkFromThisEndpoint() {
        return !getRenderableLinkedWheels().isEmpty();
    }

    // Set the linked target
    public void setLinkedTarget(BlockPos targetPos, @Nullable UUID targetSubLevelId) {
        setLinkedTarget(targetPos, targetSubLevelId, true);
    }

    // Set the linked target and whether its consumed connector can be returned when broken
    public void setLinkedTarget(BlockPos targetPos, @Nullable UUID targetSubLevelId, boolean returnsBeltOnBreak) {
        linkedPos = targetPos.immutable();
        linkedSubLevelId = targetSubLevelId;
        this.returnsBeltOnBreak = returnsBeltOnBreak;
        additionalLinks.clear();
        invalidateLinkedWheelCache();
        receivesFromLinkedWheel = false;
        refreshKineticLink();
        setChanged();
        sendData();
    }

    // Check if the belt wheel references the target
    public boolean references(BlockPos pos, @Nullable UUID subLevelId) {
        if (linkedPos != null && linkedPos.equals(pos) && Objects.equals(linkedSubLevelId, subLevelId)) return true;
        for (BeltLink link : additionalLinks) {
            if (link.pos().equals(pos) && Objects.equals(link.subLevelId(), subLevelId)) return true;
        }
        return false;
    }

    private List<BeltLink> allLinks() {
        List<BeltLink> links = new ArrayList<>(MAX_BELTS);
        if (linkedPos != null) links.add(new BeltLink(linkedPos, linkedSubLevelId, returnsBeltOnBreak));
        links.addAll(additionalLinks);
        return links;
    }

    private record BeltLink(BlockPos pos, @Nullable UUID subLevelId, boolean refundable) {}

    // Handle the break link
    public void breakLink(boolean notifyOther) {
        breakLink(notifyOther, false);
    }

    // Handle the break link and optionally return its connector item
    public void breakLink(boolean notifyOther, boolean returnBelt) {
        for (BeltLink link : allLinks()) breakLinkTo(link.pos(), link.subLevelId(), notifyOther, returnBelt);
    }

    // Shears remove exactly the selected reciprocal connection and return its connector once.
    public boolean shearLink(BlockPos targetPos, @Nullable UUID targetSubLevelId) {
        if (!references(targetPos, targetSubLevelId)) return false;
        breakLinkTo(targetPos, targetSubLevelId, true, true);
        return true;
    }

    private void breakLinkTo(BlockPos targetPos, @Nullable UUID targetSubLevelId,
                             boolean notifyOther, boolean returnBelt) {
        BeltLink removed = null;
        if (linkedPos != null && linkedPos.equals(targetPos) && Objects.equals(linkedSubLevelId, targetSubLevelId)) {
            removed = new BeltLink(linkedPos, linkedSubLevelId, returnsBeltOnBreak);
            linkedPos = null;
            linkedSubLevelId = null;
            returnsBeltOnBreak = false;
            if (!additionalLinks.isEmpty()) {
                BeltLink promoted = additionalLinks.removeFirst();
                linkedPos = promoted.pos();
                linkedSubLevelId = promoted.subLevelId();
                returnsBeltOnBreak = promoted.refundable();
            }
        } else {
            for (BeltLink link : additionalLinks) {
                if (link.pos().equals(targetPos) && Objects.equals(link.subLevelId(), targetSubLevelId)) {
                    removed = link;
                    break;
                }
            }
            additionalLinks.remove(removed);
        }
        if (removed == null) return;

        invalidateLinkedWheelCache();
        receivesFromLinkedWheel = false;
        refreshKineticLink();
        setChanged();
        sendData();
        if (returnBelt && removed.refundable()) dropBelt();

        if (!notifyOther || level == null) return;
        PhysicsGantryBeltWheelBlockEntity other = SimulatedHelper.findBlockEntity(level,
                removed.subLevelId(), removed.pos(), PhysicsGantryBeltWheelBlockEntity.class);
        if (other != null && !other.isRemoved()) {
            other.breakLinkTo(worldPosition, SimulatedHelper.getContainingSubLevelId(this), false, false);
        }
    }

    // Return the belt connector at the endpoint that broke the link
    void dropBelt() {
        if (level == null || level.isClientSide) {
            return;
        }
        Item beltConnector = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "belt_connector"));
        if (beltConnector != Items.AIR) {
            Block.popResource(level, worldPosition, new ItemStack(beltConnector));
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
        assemblyTransferAdditionalLinks = List.copyOf(additionalLinks);
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
        for (BeltLink link : allLinks()) {
            PhysicsGantryBeltWheelBlockEntity linkedWheel = resolveLinkedWheel(link);
            if (linkedWheel != null && wasMovedByAssemblyTransfer(linkedWheel)) {
                linkedWheel.retargetLinkedWheelAfterAssemblyTransfer();
            }
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

    @Nullable
    private PhysicsGantryBeltWheelBlockEntity resolveLinkedWheel(BeltLink link) {
        if (level == null) return null;
        if (linkedPos != null && linkedPos.equals(link.pos())
                && Objects.equals(linkedSubLevelId, link.subLevelId())) return resolveLinkedWheel();
        BlockEntity target = SimulatedHelper.findLoadedBlockEntityExact(level, link.subLevelId(), link.pos());
        return target instanceof PhysicsGantryBeltWheelBlockEntity wheel && wheel != this && !wheel.isRemoved()
                ? wheel : null;
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

        if (linkCount() > 1) {
            return Component.translatable("createthrusters.physics_gantry_belt_wheel.status_multi", linkCount(), MAX_BELTS)
                    .withStyle(ChatFormatting.AQUA);
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
        if (level == null || !hasLinkedTarget()) return;
        long gameTime = level.getGameTime();
        pruneExpiredAssemblyTransfers(gameTime);
        boolean transferReferenceValid = assemblyTransferLinkedPos != null
                && assemblyTransferExpiresAtTick >= gameTime;
        boolean changed = false;
        List<BeltLink> currentLinks = allLinks();
        for (int index = 0; index < currentLinks.size(); index++) {
            BeltLink current = currentLinks.get(index);
            BeltLink lookup = current;
            if (transferReferenceValid) {
                if (index == 0) lookup = new BeltLink(assemblyTransferLinkedPos,
                        assemblyTransferLinkedSubLevelId, current.refundable());
                else if (index - 1 < assemblyTransferAdditionalLinks.size()) {
                    lookup = assemblyTransferAdditionalLinks.get(index - 1);
                }
            }
            BeltWheelMoveKey key = new BeltWheelMoveKey(level.dimension().location().toString(),
                    lookup.pos(), lookup.subLevelId());
            BeltWheelMoveTarget moved = ASSEMBLY_TRANSFER_TARGETS.get(key);
            if (moved == null || !moved.complete() || current.pos().equals(moved.position())
                    && Objects.equals(current.subLevelId(), moved.subLevelId())) continue;
            if (index == 0) {
                linkedPos = moved.position();
                linkedSubLevelId = moved.subLevelId();
            } else {
                additionalLinks.set(index - 1, new BeltLink(moved.position(), moved.subLevelId(), current.refundable()));
            }
            changed = true;
        }
        if (!transferReferenceValid && assemblyTransferLinkedPos != null) clearAssemblyTransferReference();
        if (!changed) return;
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
        assemblyTransferAdditionalLinks = List.of();
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
            tag.putBoolean("ReturnsBeltOnBreak", returnsBeltOnBreak);
        }
        if (savedLinkedSubLevelId != null) {
            tag.putUUID("LinkedSubLevelId", savedLinkedSubLevelId);
        }
        ListTag savedLinks = new ListTag();
        for (BeltLink link : additionalLinks) {
            CompoundTag linkTag = serializeBeltLink(link, ctx);
            if (linkTag != null) savedLinks.add(linkTag);
        }
        tag.put("AdditionalLinks", savedLinks);
        if (!clientPacket && assemblyTransferLinkedPos != null) {
            tag.putLong("AssemblyTransferLinkedPos", assemblyTransferLinkedPos.asLong());
            if (assemblyTransferLinkedSubLevelId != null) {
                tag.putUUID("AssemblyTransferLinkedSubLevelId", assemblyTransferLinkedSubLevelId);
            }
            ListTag transferLinks = new ListTag();
            for (BeltLink link : assemblyTransferAdditionalLinks) {
                CompoundTag linkTag = serializeBeltLink(link, null);
                if (linkTag != null) transferLinks.add(linkTag);
            }
            tag.put("AssemblyTransferAdditionalLinks", transferLinks);
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
        // Saved links from before this field existed consumed a connector in survival, so keep them refundable.
        returnsBeltOnBreak = linkedPos != null && (!tag.contains("ReturnsBeltOnBreak")
                || tag.getBoolean("ReturnsBeltOnBreak"));
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
        additionalLinks.clear();
        ListTag savedLinks = tag.getList("AdditionalLinks", Tag.TAG_COMPOUND);
        for (int index = 0; index < savedLinks.size() && linkCount() < MAX_BELTS; index++) {
            BeltLink link = deserializeBeltLink(savedLinks.getCompound(index), ctx);
            if (link != null && !references(link.pos(), link.subLevelId())) additionalLinks.add(link);
        }
        if (linkedPos == null && !additionalLinks.isEmpty()) {
            BeltLink promoted = additionalLinks.removeFirst();
            linkedPos = promoted.pos();
            linkedSubLevelId = promoted.subLevelId();
            returnsBeltOnBreak = promoted.refundable();
        }
        if (!Objects.equals(previousLinkedPos, linkedPos)
                || !Objects.equals(previousLinkedSubLevelId, linkedSubLevelId)) {
            invalidateLinkedWheelCache();
        }
        assemblyTransferLinkedPos = tag.contains("AssemblyTransferLinkedPos")
                ? BlockPos.of(tag.getLong("AssemblyTransferLinkedPos")) : null;
        assemblyTransferLinkedSubLevelId = tag.contains("AssemblyTransferLinkedSubLevelId")
                ? tag.getUUID("AssemblyTransferLinkedSubLevelId") : null;
        ListTag transferLinks = tag.getList("AssemblyTransferAdditionalLinks", Tag.TAG_COMPOUND);
        List<BeltLink> originalLinks = new ArrayList<>();
        for (int index = 0; index < transferLinks.size() && index < MAX_BELTS - 1; index++) {
            BeltLink link = deserializeBeltLink(transferLinks.getCompound(index), null);
            if (link != null) originalLinks.add(link);
        }
        assemblyTransferAdditionalLinks = List.copyOf(originalLinks);
        assemblyTransferExpiresAtTick = 0L;
        receivesFromLinkedWheel = tag.getBoolean("ReceivesFromLinkedWheel");
        if (!clientPacket && tag.contains("GeneratedLinkSpeed")) {
            rebuildKineticNetworkOnLoad();
        }
    }

    // Get the loading dependencies
    @Override
    public Iterable<SubLevel> sable$getLoadingDependencies() {
        if (level == null || !hasLinkedTarget()) return List.of();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        LinkedHashSet<SubLevel> dependencies = new LinkedHashSet<>();
        for (BeltLink link : allLinks()) {
            if (link.subLevelId() == null) continue;
            SubLevel subLevel = container.getSubLevel(link.subLevelId());
            if (subLevel != null && !subLevel.isRemoved()) dependencies.add(subLevel);
        }
        return dependencies;
    }

    @Nullable
    private static CompoundTag serializeBeltLink(BeltLink link,
                                                   @Nullable SubLevelSchematicSerializationContext ctx) {
        BlockPos pos = link.pos();
        UUID subLevelId = link.subLevelId();
        if (ctx != null) {
            if (subLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(subLevelId);
                if (mapping == null) return null;
                pos = mapping.transform().apply(pos);
                subLevelId = mapping.newUUID();
            } else if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
                if (!ctx.getBoundingBox().contains(pos.getX(), pos.getY(), pos.getZ())) return null;
                pos = ctx.getPlaceTransform().apply(pos);
            } else {
                pos = ctx.getSetupTransform().apply(pos);
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pos", pos.asLong());
        if (subLevelId != null) tag.putUUID("SubLevelId", subLevelId);
        tag.putBoolean("Refundable", link.refundable());
        return tag;
    }

    @Nullable
    private static BeltLink deserializeBeltLink(CompoundTag tag,
                                                  @Nullable SubLevelSchematicSerializationContext ctx) {
        if (!tag.contains("Pos")) return null;
        BlockPos pos = BlockPos.of(tag.getLong("Pos"));
        UUID subLevelId = tag.contains("SubLevelId") ? tag.getUUID("SubLevelId") : null;
        if (ctx != null && ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE) {
            if (subLevelId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(subLevelId);
                if (mapping == null) return null;
                pos = mapping.transform().apply(pos);
                subLevelId = mapping.newUUID();
            } else {
                pos = ctx.getPlaceTransform().apply(pos);
            }
        }
        return new BeltLink(pos, subLevelId, !tag.contains("Refundable") || tag.getBoolean("Refundable"));
    }

}
