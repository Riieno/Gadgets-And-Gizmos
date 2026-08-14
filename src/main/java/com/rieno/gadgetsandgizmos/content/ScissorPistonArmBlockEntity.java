package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Track one scissor arm segment and expose its physical dependency to Sable
public class ScissorPistonArmBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current parent position
    private BlockPos parentPos;
    // Current parent sub-level id
    private UUID parentSubLevelId;
    // Current piston facing
    private Direction pistonFacing = Direction.UP;
    // Current cell distance from head
    private int cellDistanceFromHead = 1;
    // Maximum range
    private int maxRange = 1;
    // Current extension
    private double extension;
    // Previous extension
    private double previousExtension;
    // Tracks whether scissor piston arm is transferring
    private boolean transferring;
    // Tracks whether scissor piston arm is removing by piston
    private boolean removingByPiston;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston arm
    public ScissorPistonArmBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SCISSOR_PISTON_ARM.get(), pos, state);
        if (state.hasProperty(ScissorPistonArmBlock.FACING)) {
            pistonFacing = state.getValue(ScissorPistonArmBlock.FACING);
        }
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

    // Configure the parent
    public void configureFromParent(ScissorPistonBlockEntity parent, int cellDistanceFromHead) {
        BlockPos nextParentPos = parent.getBlockPos().immutable();
        UUID nextParentSubLevelId = SimulatedHelper.getContainingSubLevelId(parent);
        int nextCellDistance = Math.max(1, cellDistanceFromHead);
        boolean topologyChanged = !nextParentPos.equals(parentPos)
                || !Objects.equals(nextParentSubLevelId, parentSubLevelId);
        boolean connectionChanged = topologyChanged
                || nextCellDistance != this.cellDistanceFromHead;
        parentPos = nextParentPos;
        parentSubLevelId = nextParentSubLevelId;
        this.cellDistanceFromHead = nextCellDistance;
        updateFromParent(parent, connectionChanged);
        if (topologyChanged) {
            SableAssemblyTopologyInvalidation.invalidate(level);
        }
    }

    // Update the parent
    public void updateFromParent(ScissorPistonBlockEntity parent) {
        updateFromParent(parent, false);
    }

    // Update the parent
    private void updateFromParent(ScissorPistonBlockEntity parent, boolean connectionChanged) {
        Direction nextFacing = parent.getPistonFacing();
        int nextMaxRange = Math.max(1, parent.getEffectiveMaxRange());
        double nextExtension = parent.getExtension();
        boolean extensionChanged = Double.compare(extension, nextExtension) != 0;
        boolean interpolationNeedsSettling = !extensionChanged
                && Double.compare(previousExtension, extension) != 0;
        if (!shouldSyncState(connectionChanged, removingByPiston, pistonFacing, nextFacing,
                maxRange, nextMaxRange, previousExtension, extension, nextExtension)) {
            return;
        }
        removingByPiston = false;
        pistonFacing = nextFacing;
        maxRange = nextMaxRange;
        if (extensionChanged) {
            previousExtension = extension;
            extension = nextExtension;
        } else if (interpolationNeedsSettling) {
            previousExtension = extension;
        }
        setChanged();
        sendData();
    }

    // Check if this should sync state
    static boolean shouldSyncState(boolean connectionChanged, boolean removingByPiston,
                                   Direction currentFacing, Direction nextFacing,
                                   int currentMaxRange, int nextMaxRange,
                                   double previousExtension, double currentExtension, double nextExtension) {
        return connectionChanged || removingByPiston || currentFacing != nextFacing
                || currentMaxRange != nextMaxRange
                || Double.compare(currentExtension, nextExtension) != 0
                || Double.compare(previousExtension, currentExtension) != 0;
    }

    // Remove the extension from parent
    public boolean removeExtensionFromParent(Player player) {
        ScissorPistonBlockEntity parent = resolveParent();
        return parent != null && parent.removeExtension(player);
    }

    // Remove the extension from external break
    public void removeExtensionFromExternalBreak() {
        if (level == null || level.isClientSide || transferring || removingByPiston) {
            return;
        }
        ScissorPistonBlockEntity parent = resolveParent();
        if (parent != null && !parent.isInternalArmBlockUpdate()) {
            removingByPiston = true;
            if (!parent.tryStartExternalArmBreak()) {
                return;
            }
            parent.removeExtensionDroppingItem(globalDropPos());
        }
    }

    // Mark the removing by piston
    public void markRemovingByPiston() {
        removingByPiston = true;
    }

    // Begin the assembly transfer
    void beginAssemblyTransfer() {
        transferring = true;
        removingByPiston = true;
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        transferring = false;
        removingByPiston = false;
        ScissorPistonBlockEntity parent = resolveParent();
        if (parent != null) {
            configureFromParent(parent, cellDistanceFromHead);
        }
    }

    // Get the piston facing
    public Direction getPistonFacing() {
        return pistonFacing;
    }

    // Get the cell distance from head
    public int getCellDistanceFromHead() {
        return Math.max(1, cellDistanceFromHead);
    }

    // Get the max range
    public int getMaxRange() {
        return Math.max(1, maxRange);
    }

    // Get the arm link count
    public int getArmLinkCount() {
        return Math.max(2, getMaxRange() + 1);
    }

    // Get the extension
    public double getExtension() {
        return extension;
    }

    // Get the interpolated extension
    public double getInterpolatedExtension(float partialTicks) {
        return previousExtension + (extension - previousExtension) * partialTicks;
    }

    // Get the render bounding box
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5D);
    }

    // Remove the scissor piston arm
    @Override
    public void remove() {
        transferring = true;
        super.remove();
    }

    // Write the scissor piston arm
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        BlockPos savedParentPos = parentPos;
        UUID savedParentId = parentSubLevelId;
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx != null && savedParentPos != null) {
            if (savedParentId != null) {
                SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(savedParentId);
                if (mapping != null) {
                    savedParentId = mapping.newUUID();
                    savedParentPos = mapping.transform().apply(savedParentPos);
                } else if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
                    savedParentId = null;
                    savedParentPos = null;
                }
            } else if (ctx.getType() == SubLevelSchematicSerializationContext.Type.SAVE) {
                savedParentPos = ctx.getBoundingBox().contains(
                        savedParentPos.getX(), savedParentPos.getY(), savedParentPos.getZ())
                        ? ctx.getPlaceTransform().apply(savedParentPos)
                        : null;
            } else {
                savedParentPos = ctx.getSetupTransform().apply(savedParentPos);
            }
        }
        if (savedParentPos != null) {
            tag.put("ParentPos", NbtUtils.writeBlockPos(savedParentPos));
        }
        if (savedParentId != null) {
            tag.putUUID("ParentSubLevelId", savedParentId);
        }
        tag.putString("PistonFacing", pistonFacing.getName());
        tag.putInt("CellDistanceFromHead", cellDistanceFromHead);
        tag.putInt("MaxRange", maxRange);
        tag.putDouble("Extension", extension);
        tag.putDouble("PreviousExtension", previousExtension);
    }

    // Read the scissor piston arm
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        parentPos = tag.contains("ParentPos") ? NbtUtils.readBlockPos(tag, "ParentPos").orElse(null) : null;
        parentSubLevelId = tag.contains("ParentSubLevelId") ? tag.getUUID("ParentSubLevelId") : null;
        remapPlacedParent();
        pistonFacing = Direction.byName(tag.getString("PistonFacing"));
        if (pistonFacing == null) {
            pistonFacing = Direction.UP;
        }
        cellDistanceFromHead = tag.contains("CellDistanceFromHead") ? tag.getInt("CellDistanceFromHead") : 1;
        maxRange = tag.contains("MaxRange") ? tag.getInt("MaxRange") : 1;
        extension = tag.contains("Extension") ? tag.getDouble("Extension") : 0.0D;
        previousExtension = tag.contains("PreviousExtension") ? tag.getDouble("PreviousExtension") : extension;
        transferring = false;
        removingByPiston = false;
    }

    // Remap the placed parent
    private void remapPlacedParent() {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE
                || parentPos == null) return;
        if (parentSubLevelId != null) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(parentSubLevelId);
            if (mapping != null) {
                parentSubLevelId = mapping.newUUID();
                parentPos = mapping.transform().apply(parentPos);
            }
        } else {
            parentPos = ctx.getPlaceTransform().apply(parentPos);
        }
    }

    // Get the connection dependencies
    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null || parentSubLevelId == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(parentSubLevelId);
        return subLevel == null || subLevel.isRemoved() ? null : List.of(subLevel);
    }

    // Resolve the parent
    private @Nullable ScissorPistonBlockEntity resolveParent() {
        if (level == null || parentPos == null) {
            return null;
        }
        BlockEntity parent = SimulatedHelper.findBlockEntity(level, parentSubLevelId, parentPos);
        if (parent instanceof ScissorPistonBlockEntity piston) {
            return piston;
        }
        return SimulatedHelper.findBlockEntityIncludingSubLevels(level, parentPos, ScissorPistonBlockEntity.class);
    }

    // Get the global drop pos
    private BlockPos globalDropPos() {
        Vector3d global = NestedAssemblyFrame.resolve(this).toWorldPosition(new Vector3d(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D));
        return BlockPos.containing(global.x, global.y, global.z);
    }
}
