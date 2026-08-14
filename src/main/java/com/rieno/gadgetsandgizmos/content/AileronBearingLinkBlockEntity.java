package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Keep an aileron bearing head loaded and forward its target across the moving level boundary
public class AileronBearingLinkBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {
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
    // Current head
    private BearingHead head = BearingHead.PRIMARY;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the aileron bearing link
    public AileronBearingLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.AILERON_BEARING_LINK.get(), pos, state);
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
    public void setParent(AileronBearingBlockEntity parent, BearingHead head) {
        BlockPos nextParentPos = parent.getBlockPos().immutable();
        UUID nextParentSubLevelId = SimulatedHelper.getContainingSubLevelId(parent);
        boolean topologyChanged = !nextParentPos.equals(parentPos)
                || !Objects.equals(nextParentSubLevelId, parentSubLevelId);
        this.parentPos = nextParentPos;
        this.parentSubLevelId = nextParentSubLevelId;
        this.head = head;
        if (topologyChanged) {
            SableAssemblyTopologyInvalidation.invalidate(level);
        }
        setChanged();
        sendData();
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        if (level == null || level.isClientSide || head == null) {
            return;
        }
        AileronBearingBlockEntity parent = resolveParent();
        if (parent == null) {
            return;
        }
        UUID childSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        parent.updateMountedAssemblyFromLink(head, childSubLevelId, getBlockPos());
        setParent(parent, head);
    }

    // Disassemble the parent
    public boolean disassembleParent() {
        AileronBearingBlockEntity parent = resolveParent();
        if (parent == null || head == null) {
            return false;
        }
        parent.disassembleMountedBlock(head);
        return true;
    }

    // Write the aileron bearing link
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
        if (head != null) {
            tag.putString("Head", head.serializedName());
        }
    }

    // Read the aileron bearing link
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        parentPos = tag.contains("ParentPos") ? NbtUtils.readBlockPos(tag, "ParentPos").orElse(null) : null;
        parentSubLevelId = tag.contains("ParentSubLevelId") ? tag.getUUID("ParentSubLevelId") : null;
        remapPlacedParent();
        head = BearingHead.byName(tag.getString("Head"), BearingHead.PRIMARY);
    }

    // Remap the placed parent
    private void remapPlacedParent() {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE
                || parentPos == null) {
            return;
        }
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
    @Nullable AileronBearingBlockEntity resolveParent() {
        if (level == null || parentPos == null) {
            return null;
        }
        BlockEntity parent = SimulatedHelper.findBlockEntityExact(level, parentSubLevelId, parentPos);
        return parent instanceof AileronBearingBlockEntity bearing ? bearing : null;
    }

    // Check if this belongs to the parent
    boolean isOwnedBy(AileronBearingBlockEntity parent, BearingHead expectedHead) {
        return parent != null
                && expectedHead == head
                && parentPos != null
                && parentPos.equals(parent.getBlockPos())
                && Objects.equals(parentSubLevelId, SimulatedHelper.getContainingSubLevelId(parent));
    }
}
