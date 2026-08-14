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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Keep a scissor piston head linked to its base without publishing another structural branch
public class ScissorPistonLinkBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
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
    // Tracks whether scissor piston link is removing by piston
    private boolean removingByPiston;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston link
    public ScissorPistonLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.SCISSOR_PISTON_LINK.get(), pos, state);
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

    // Set the parent
    public void setParent(ScissorPistonBlockEntity parent) {
        BlockPos nextParentPos = parent.getBlockPos().immutable();
        UUID nextParentSubLevelId = SimulatedHelper.getContainingSubLevelId(parent);
        if (nextParentPos.equals(parentPos) && Objects.equals(nextParentSubLevelId, parentSubLevelId)) {
            return;
        }
        parentPos = nextParentPos;
        parentSubLevelId = nextParentSubLevelId;
        SableAssemblyTopologyInvalidation.invalidate(level);
        setChanged();
        sendData();
    }

    // Check if this is linked to the target
    boolean isLinkedTo(ScissorPistonBlockEntity parent) {
        if (parent == null || parentPos == null || !parentPos.equals(parent.getBlockPos())) {
            return false;
        }
        UUID currentParentSubLevelId = SimulatedHelper.getContainingSubLevelId(parent);
        if (Objects.equals(parentSubLevelId, currentParentSubLevelId)) {
            return true;
        }
        return resolveParent() == parent;
    }

    // Mark the removing by piston
    public void markRemovingByPiston() {
        removingByPiston = true;
    }

    // Begin the assembly transfer
    void beginAssemblyTransfer() {
        removingByPiston = true;
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        removingByPiston = false;
        if (level == null || level.isClientSide) {
            return;
        }
        ScissorPistonBlockEntity parent = resolveParent();
        if (parent == null) {
            return;
        }
        UUID childSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        parent.updateMountedAssemblyFromLink(childSubLevelId, getBlockPos());
        setParent(parent);
    }

    // Remove the extension from parent
    public boolean removeExtensionFromParent(Player player) {
        ScissorPistonBlockEntity parent = resolveParent();
        return parent != null && parent.removeExtension(player);
    }

    // Disassemble the parent
    public boolean disassembleParent() {
        ScissorPistonBlockEntity parent = resolveParent();
        if (parent == null) {
            return false;
        }
        parent.disassembleMountedBlock();
        return true;
    }

    // Destroy the scissor piston link
    @Override
    public void destroy() {
        if (level != null && !level.isClientSide && !removingByPiston) {
            removingByPiston = true;
            ScissorPistonBlockEntity parent = resolveParent();
            if (parent != null) {
                parent.queueMountedSubLevelDetach(
                        parent.getMountedSubLevelId(), getBlockPos());
            }
        }
        super.destroy();
    }

    // Write the scissor piston link
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
    }

    // Read the scissor piston link
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        parentPos = tag.contains("ParentPos") ? NbtUtils.readBlockPos(tag, "ParentPos").orElse(null) : null;
        parentSubLevelId = tag.contains("ParentSubLevelId") ? tag.getUUID("ParentSubLevelId") : null;
        remapPlacedParent();
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
}
