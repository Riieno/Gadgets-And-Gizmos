package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// Build the scissor arm bodies and keep their extension, joints and kinetic load together
public class ScissorPistonBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int DEFAULT_RANGE = 1;
    private static final double SPEED_EPSILON = 1.0E-4D;
    private static final double MOVEMENT_EPSILON = 1.0E-6D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Mounted assembly
    private final ScissorPistonMountedAssembly mountedAssembly = new ScissorPistonMountedAssembly();
    // Mounted sub-level id
    private UUID mountedSubLevelId;
    // Mounted local pos
    private BlockPos mountedLocalPos;
    // Tracks whether mounted assembly is present
    private boolean mountedAssemblyPresent;
    // Maximum range
    private int maxRange = DEFAULT_RANGE;
    // Current extension
    private double extension;
    // Previous extension
    private double previousExtension;
    // Tracks whether scissor piston is transferring
    private boolean transferring;
    // Tracks whether internal arm block update is set
    private boolean internalArmBlockUpdate;
    // Tracks whether head detach is pending
    private boolean pendingHeadDetach;
    // Last external arm break game time
    private long lastExternalArmBreakGameTime = Long.MIN_VALUE;
    // Current sequenced extension limit
    private double sequencedExtensionLimit = -1.0D;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston
    public ScissorPistonBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SCISSOR_PISTON.get(), pos, state);
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

    // Sync the sequence context
    @Override
    protected boolean syncSequenceContext() {
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the speed changed event
    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        sequencedExtensionLimit = -1.0D;
        if (sequenceContext != null && sequenceContext.instruction() == SequencerInstructions.TURN_DISTANCE) {
            sequencedExtensionLimit = Math.max(0.0D,
                    sequenceContext.getEffectiveValue(getTheoreticalSpeed()));
        }
    }

    // Update the scissor piston
    @Override
    public void tick() {
        super.tick();
        previousExtension = extension;
        if (level == null || level.isClientSide) {
            return;
        }
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }
        if (pendingHeadDetach) {
            pendingHeadDetach = false;
            detachMountedSubLevel(false);
            return;
        }

        clampConfiguredRange();
        refreshAssemblyState(serverLevel);

        double movement = limitMovementForSequence(getMovementStep(), sequencedExtensionLimit);
        boolean movedMountedAssembly = false;
        double movementThreshold = sequencedExtensionLimit >= 0.0D ? MOVEMENT_EPSILON : SPEED_EPSILON;
        if (Math.abs(movement) > movementThreshold) {
            if (mountedSubLevelId != null) {
                double targetExtension = Math.max(0.0D, Math.min(getEffectiveMaxRange(), extension + movement));
                double nextExtension = mountedAssembly.clampExtensionForCollision(this, serverLevel,
                        extension, targetExtension);
                double appliedMovement = nextExtension - extension;
                if (Math.abs(appliedMovement) > MOVEMENT_EPSILON) {
                    extension = nextExtension;
                    mountedAssembly.markPoseDirty();
                    sequencedExtensionLimit = consumeSequenceDistance(
                            sequencedExtensionLimit, appliedMovement);
                    movedMountedAssembly = true;
                    setChanged();
                    sendData();
                }
            }
        }

        if (mountedSubLevelId != null) {
            if (movedMountedAssembly) {
                mountedAssembly.carryContactingSubLevels(this, serverLevel, previousExtension, extension);
            }
            if (mountedAssembly.needsPoseUpdate()) {
                mountedAssembly.applyPose(this, serverLevel);
            }
        }
    }

    // Refresh the assembly state
    private void refreshAssemblyState(ServerLevel serverLevel) {
        if (mountedSubLevelId == null) {
            mountedAssemblyPresent = false;
            syncAssembledBlockState();
            return;
        }
        if (mountedAssembly.hasMountedBlock(this, serverLevel)) {
            mountedAssemblyPresent = true;
            syncAssembledBlockState();
            return;
        }
        if (mountedAssembly.recoverMountedBlock(this, serverLevel)) {
            mountedAssemblyPresent = true;
            syncAssembledBlockState();
            return;
        }
        mountedAssemblyPresent = true;
        syncAssembledBlockState();
    }

    // Get the movement step
    private double getMovementStep() {
        double linear = Math.max(-0.49D, Math.min(0.49D, KineticBlockEntity.convertToLinear(getSpeed())));
        Direction facing = getPistonFacing();
        return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? linear : -linear;
    }

    // Get the limit movement for sequence
    static double limitMovementForSequence(double movement, double remainingDistance) {
        if (remainingDistance < 0.0D) {
            return movement;
        }
        double limit = Math.max(0.0D, remainingDistance);
        return Math.max(-limit, Math.min(limit, movement));
    }

    // Consume the sequence distance
    static double consumeSequenceDistance(double remainingDistance, double appliedMovement) {
        if (remainingDistance < 0.0D) {
            return remainingDistance;
        }
        double remaining = Math.max(0.0D, remainingDistance - Math.abs(appliedMovement));
        return remaining <= MOVEMENT_EPSILON ? 0.0D : remaining;
    }

    // Add the extension
    public boolean addExtension(Player player, ItemStack stack) {
        int limit = getConfiguredRangeLimit();
        if (maxRange >= limit) {
            return false;
        }
        maxRange++;
        mountedAssembly.markPoseDirty();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null && mountedSubLevelId != null) {
            mountedAssembly.applyPose(this, serverLevel);
        }
        setChanged();
        sendData();
        return true;
    }

    // Try to assemble mounted block
    public boolean tryAssembleMountedBlock() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null || mountedSubLevelId != null) {
            return false;
        }
        extension = 0.0D;
        previousExtension = 0.0D;
        return mountedAssembly.assemble(this, serverLevel);
    }

    // Disassemble the mounted block
    public void disassembleMountedBlock() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            try {
                mountedAssembly.disassemble(this, serverLevel);
            } catch (RuntimeException | LinkageError error) {
                mountedAssembly.removeHeadAfterFailedDisassembly(this, serverLevel);
            }
        } else {
            setMountedAssembly(null, null);
        }
    }

    // Remove the extension
    public boolean removeExtension(Player player) {
        if (!removeExtensionInternal(true)) {
            return false;
        }
        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(CTItems.SCISSOR_ARMS.get()));
        }
        return true;
    }

    // Remove the extension dropping item
    public boolean removeExtensionDroppingItem(BlockPos dropPos) {
        if (!removeExtensionInternal(false)) {
            return false;
        }
        if (level != null && !level.isClientSide && CTItems.SCISSOR_ARMS != null) {
            Block.popResource(level, dropPos, new ItemStack(CTItems.SCISSOR_ARMS.get()));
        }
        return true;
    }

    // Try to start external arm break
    boolean tryStartExternalArmBreak() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return false;
        }
        long gameTime = serverLevel.getGameTime();
        if (lastExternalArmBreakGameTime == gameTime) {
            return false;
        }
        lastExternalArmBreakGameTime = gameTime;
        return true;
    }

    // Check if this is internal arm block update
    boolean isInternalArmBlockUpdate() {
        return internalArmBlockUpdate;
    }

    // Begin the internal arm block update
    void beginInternalArmBlockUpdate() {
        internalArmBlockUpdate = true;
    }

    // End the internal arm block update
    void endInternalArmBlockUpdate() {
        internalArmBlockUpdate = false;
    }

    // Remove the extension internal
    private boolean removeExtensionInternal(boolean syncAssembly) {
        if (maxRange <= DEFAULT_RANGE || CTItems.SCISSOR_ARMS == null) {
            return false;
        }
        maxRange--;
        if (extension > maxRange) {
            extension = maxRange;
        }
        mountedAssembly.markPoseDirty();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (syncAssembly && serverLevel != null && mountedSubLevelId != null) {
            mountedAssembly.applyPose(this, serverLevel);
        }
        setChanged();
        sendData();
        return true;
    }

    // Handle the break with wrench
    public void breakWithWrench(@Nullable Player player) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel == null) {
            return;
        }
        int returnedArms = Math.max(0, maxRange - DEFAULT_RANGE);
        disassembleMountedBlock();
        if (player != null && !player.isCreative()) {
            if (CTItems.SCISSOR_PISTON != null) {
                player.getInventory().placeItemBackInInventory(new ItemStack(CTItems.SCISSOR_PISTON.get()));
            }
            if (CTItems.SCISSOR_ARMS != null && returnedArms > 0) {
                player.getInventory().placeItemBackInInventory(new ItemStack(CTItems.SCISSOR_ARMS.get(), returnedArms));
            }
        }
        serverLevel.removeBlock(worldPosition, false);
    }

    // Detach the mounted sublevel
    public void detachMountedSubLevel(boolean removeLinkBlock) {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null) {
            mountedAssembly.detach(this, serverLevel, removeLinkBlock);
        } else {
            setMountedAssembly(null, null);
        }
    }

    // Queue the mounted sublevel detach
    void queueMountedSubLevelDetach(@Nullable UUID childSubLevelId, BlockPos childLocalPos) {
        if (mountedSubLevelId == null || mountedLocalPos == null
                || !mountedSubLevelId.equals(childSubLevelId)
                || childLocalPos == null || !mountedLocalPos.equals(childLocalPos)) {
            return;
        }
        pendingHeadDetach = true;
        setChanged();
    }

    // Handle the base removed event
    void onBaseRemoved() {
        if (transferring) {
            return;
        }
        disassembleMountedBlock();
    }

    // Begin the assembly transfer
    void beginAssemblyTransfer() {
        transferring = true;
        mountedAssembly.markPoseDirty();
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        transferring = false;
        mountedAssembly.markPoseDirty();
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null && mountedSubLevelId != null) {
            mountedAssembly.applyPose(this, serverLevel);
        }
    }

    // Check if the mounted assembly is present
    public boolean isMountedAssemblyPresent() {
        ServerLevel serverLevel = SableLevelApi.serverLevel(level);
        if (serverLevel != null && mountedSubLevelId != null) {
            return mountedAssembly.hasMountedBlock(this, serverLevel);
        }
        return mountedAssemblyPresent && mountedSubLevelId != null;
    }

    // Get the piston facing
    public Direction getPistonFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(ScissorPistonBlock.FACING)
                ? state.getValue(ScissorPistonBlock.FACING)
                : Direction.UP;
    }

    // Get the mounted block pos
    public BlockPos getMountedBlockPos() {
        return worldPosition.relative(getPistonFacing());
    }

    // Get the mounted sublevel id
    public @Nullable UUID getMountedSubLevelId() {
        return mountedSubLevelId;
    }

    // Get the mounted local pos
    public @Nullable BlockPos getMountedLocalPos() {
        return mountedLocalPos;
    }

    // Set the mounted assembly
    void setMountedAssembly(@Nullable UUID subLevelId, @Nullable BlockPos localPos) {
        mountedSubLevelId = subLevelId;
        mountedLocalPos = localPos == null ? null : localPos.immutable();
        mountedAssemblyPresent = subLevelId != null && localPos != null;
        mountedAssembly.markPoseDirty();
        SableAssemblyTopologyInvalidation.invalidate(level);
        if (subLevelId == null) {
            pendingHeadDetach = false;
            extension = 0.0D;
            previousExtension = 0.0D;
        }
        syncAssembledBlockState();
        setChanged();
        sendData();
    }

    // Update the mounted assembly from link
    void updateMountedAssemblyFromLink(@Nullable UUID subLevelId, BlockPos localPos) {
        mountedSubLevelId = subLevelId;
        mountedLocalPos = localPos.immutable();
        mountedAssemblyPresent = subLevelId != null;
        mountedAssembly.markPoseDirty();
        SableAssemblyTopologyInvalidation.invalidate(level);
        syncAssembledBlockState();
        setChanged();
        sendData();
    }

    // Sync the assembled block state
    private void syncAssembledBlockState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(ScissorPistonBlock.ASSEMBLED)) {
            return;
        }
        boolean assembled = mountedSubLevelId != null && mountedLocalPos != null;
        if (state.getValue(ScissorPistonBlock.ASSEMBLED) == assembled) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(ScissorPistonBlock.ASSEMBLED, assembled),
                Block.UPDATE_CLIENTS);
    }

    // Get the max range
    public int getMaxRange() {
        return maxRange;
    }

    // Get the effective max range
    public int getEffectiveMaxRange() {
        return Math.max(DEFAULT_RANGE, Math.min(maxRange, getConfiguredRangeLimit()));
    }

    // Get the extension
    public double getExtension() {
        return extension;
    }

    // Get the previous extension
    double getPreviousExtension() {
        return previousExtension;
    }

    // Get the interpolated extension
    public double getInterpolatedExtension(float partialTicks) {
        return previousExtension + (extension - previousExtension) * partialTicks;
    }

    // Get the mounted sublevel handle
    public Object getMountedSubLevelHandle() {
        Level lookupLevel = level;
        if (lookupLevel == null || mountedSubLevelId == null) {
            return null;
        }
        return SubLevelBlockEntityCollector.getSubLevel(lookupLevel, mountedSubLevelId);
    }

    // Clamp the configured range
    private void clampConfiguredRange() {
        int previousMaxRange = maxRange;
        double previousClampedExtension = extension;
        int limit = getConfiguredRangeLimit();
        if (maxRange > limit) {
            maxRange = limit;
        }
        if (maxRange < DEFAULT_RANGE) {
            maxRange = DEFAULT_RANGE;
        }
        if (extension > maxRange) {
            extension = maxRange;
        }
        if (extension < 0.0D) {
            extension = 0.0D;
        }
        if (previousMaxRange != maxRange || Double.compare(previousClampedExtension, extension) != 0) {
            mountedAssembly.markPoseDirty();
        }
    }

    // Get the configured range limit
    public int getConfiguredRangeLimit() {
        return Math.max(DEFAULT_RANGE, CTConfigs.COMMON.scissorPistonMaxRange.get());
    }

    // Write the scissor piston
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        tag.putInt("MaxRange", maxRange);
        tag.putDouble("Extension", extension);
        tag.putDouble("PreviousExtension", previousExtension);
        UUID savedMountedId = mountedSubLevelId;
        BlockPos savedMountedPos = mountedLocalPos;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (savedMountedId != null && ctx != null) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(savedMountedId);
            if (mapping != null) {
                savedMountedId = mapping.newUUID();
                savedMountedPos = savedMountedPos == null ? null : mapping.transform().apply(savedMountedPos);
            } else {
                savedMountedId = null;
                savedMountedPos = null;
            }
        }
        tag.putBoolean("MountedAssemblyPresent",
                mountedAssemblyPresent && savedMountedId != null && savedMountedPos != null);
        if (savedMountedId != null) {
            tag.putUUID("MountedSubLevel", savedMountedId);
        }
        if (savedMountedPos != null) {
            tag.putLong("MountedLocalPos", savedMountedPos.asLong());
        }
        tag.putBoolean("PendingHeadDetach", pendingHeadDetach);
        if (sequencedExtensionLimit >= 0.0D) {
            tag.putDouble("SequencedExtensionLimit", sequencedExtensionLimit);
        }
    }

    // Read the scissor piston
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        maxRange = tag.contains("MaxRange") ? tag.getInt("MaxRange") : DEFAULT_RANGE;
        extension = tag.contains("Extension") ? tag.getDouble("Extension") : 0.0D;
        previousExtension = tag.contains("PreviousExtension") ? tag.getDouble("PreviousExtension") : extension;
        mountedAssemblyPresent = tag.getBoolean("MountedAssemblyPresent");
        mountedSubLevelId = tag.hasUUID("MountedSubLevel") ? tag.getUUID("MountedSubLevel") : null;
        mountedLocalPos = tag.contains("MountedLocalPos") ? BlockPos.of(tag.getLong("MountedLocalPos")) : null;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (mountedSubLevelId != null && ctx != null) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(mountedSubLevelId);
            if (mapping != null) {
                mountedSubLevelId = mapping.newUUID();
                mountedLocalPos = mountedLocalPos == null ? null : mapping.transform().apply(mountedLocalPos);
            } else {
                mountedSubLevelId = null;
                mountedLocalPos = null;
            }
        }
        mountedAssemblyPresent &= mountedSubLevelId != null && mountedLocalPos != null;
        pendingHeadDetach = tag.getBoolean("PendingHeadDetach");
        sequencedExtensionLimit = tag.contains("SequencedExtensionLimit")
                ? Math.max(0.0D, tag.getDouble("SequencedExtensionLimit"))
                : -1.0D;
        clampConfiguredRange();
    }

    // Get the connection dependencies
    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null || mountedSubLevelId == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(mountedSubLevelId);
        return subLevel == null || subLevel.isRemoved() ? null : List.of(subLevel);
    }
}
