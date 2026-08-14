package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.physics.SableAssemblyTopologyInvalidation;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Give a moving thruster bearing one stable plate for linked and SCM control
public class ThrusterBearingLinkBlockEntity extends SwivelBearingPlateBlockEntity {
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
    // Tracks whether thruster bearing link is assembling by bearing
    private boolean assemblingByBearing;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing link
    public ThrusterBearingLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.THRUSTER_BEARING_LINK.get(), pos, state);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the parent
    public void setParent(ThrusterBearingBlockEntity parent) {
        super.setParent(parent);
        BlockPos nextParentPos = parent.getBlockPos().immutable();
        UUID nextParentSubLevelId = SimulatedHelper.getContainingSubLevelId(parent);
        boolean topologyChanged = !nextParentPos.equals(parentPos)
                || !Objects.equals(nextParentSubLevelId, parentSubLevelId);
        parentPos = nextParentPos;
        parentSubLevelId = nextParentSubLevelId;
        if (topologyChanged) {
            SableAssemblyTopologyInvalidation.invalidate(level);
        }
        setChanged();
        sendData();
    }

    // Handle the state before assembly
    @Override
    public void beforeAssembly() {
        assemblingByBearing = true;
        super.beforeAssembly();
    }

    // Destroy the thruster bearing link
    @Override
    public void destroy() {
        if (level != null && !level.isClientSide && !assemblingByBearing) {
            destroyParentBearing();
        }
        super.destroy();
    }

    // Remove the thruster bearing link
    @Override
    public void remove() {
        beforeAssembly();
        super.remove();
    }

    // Destroy the parent bearing
    private void destroyParentBearing() {
        ThrusterBearingBlockEntity parent = resolveParent();
        if (parent != null && parent.getLevel() != null && parent.ownsLink(this)) {
            parent.getLevel().destroyBlock(parent.getBlockPos(), false);
        }
    }

    // Assemble the parent on the next tick
    @Override
    public void setParentAssembleNextTick() {
        ThrusterBearingBlockEntity parent = resolveParent();
        if (parent != null) {
            parent.assembleNextTick = true;
        }
    }

    // Fix the parent linking when moved
    @Override
    public void fixParentLinkingWhenMoved() {
        if (level == null || level.isClientSide || parentPos == null) {
            return;
        }
        ThrusterBearingBlockEntity parent = resolveParent();
        if (parent == null) {
            return;
        }
        parent.setPlatePos(getBlockPos());
        ServerSubLevel newSubLevel = Sable.HELPER.getContaining((BlockEntity) this) instanceof ServerSubLevel serverSubLevel
                ? serverSubLevel
                : null;
        if (newSubLevel != null) {
            UUID newId = newSubLevel.getUniqueId();
            if (!newId.equals(parent.getSubLevelID())) {
                parent.setSubLevelID(newId);
            }
            parent.reattachConstraint(newSubLevel, true);
        } else {
            parent.setSubLevelID(null);
            parent.reattachConstraint(null, true);
        }
    }

    // Update the physics
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        ThrusterBearingBlockEntity parent = resolveLoadedParent();
        if (parent != null) {
            parent.updateServoCoefficients();
        }
    }

    // Propagate the rotation
    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        ThrusterBearingBlockEntity parent = resolveParent();
        return parent != null && parent.equals(target)
                ? 1.0f
                : super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    // Check if this is a custom connection
    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        ThrusterBearingBlockEntity parent = resolveParent();
        return (parent != null && parent.equals(other)) || super.isCustomConnection(other, state, otherState);
    }

    // Add the propagation locations
    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (parentPos != null) {
            neighbours.add(parentPos);
        }
        return super.addPropagationLocations(block, state, neighbours);
    }

    // Write the thruster bearing link
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
        tag.remove("ParentPos");
        tag.remove("ParentSubLevelId");
        if (savedParentPos != null) {
            tag.put("ParentPos", NbtUtils.writeBlockPos(savedParentPos));
            tag.put("ThrusterParentPos", NbtUtils.writeBlockPos(savedParentPos));
        }
        if (savedParentId != null) {
            tag.putUUID("ParentSubLevelId", savedParentId);
            tag.putUUID("ThrusterParentSubLevelId", savedParentId);
        }
    }

    // Read the thruster bearing link
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        CompoundTag placedTag = remapPlacedParentTag(tag);
        super.read(placedTag, provider, clientPacket);
        String positionKey = placedTag.contains("ThrusterParentPos") ? "ThrusterParentPos" : "ParentPos";
        String idKey = placedTag.contains("ThrusterParentSubLevelId")
                ? "ThrusterParentSubLevelId"
                : "ParentSubLevelId";
        parentPos = placedTag.contains(positionKey)
                ? NbtUtils.readBlockPos(placedTag, positionKey).orElse(null)
                : null;
        parentSubLevelId = placedTag.contains(idKey) ? placedTag.getUUID(idKey) : null;
        assemblingByBearing = false;
    }

    // Remap the placed parent tag
    private static CompoundTag remapPlacedParentTag(CompoundTag tag) {
        SubLevelSchematicSerializationContext ctx =
                SubLevelSchematicSerializationContext.getCurrentContext();
        if (ctx == null || ctx.getType() != SubLevelSchematicSerializationContext.Type.PLACE) {
            return tag;
        }
        String positionKey = tag.contains("ThrusterParentPos") ? "ThrusterParentPos" : "ParentPos";
        String idKey = tag.contains("ThrusterParentSubLevelId")
                ? "ThrusterParentSubLevelId"
                : "ParentSubLevelId";
        BlockPos placedParentPos = tag.contains(positionKey)
                ? NbtUtils.readBlockPos(tag, positionKey).orElse(null)
                : null;
        UUID placedParentId = tag.contains(idKey) ? tag.getUUID(idKey) : null;
        if (placedParentPos == null) {
            return tag;
        }
        if (placedParentId != null) {
            SubLevelSchematicSerializationContext.SchematicMapping mapping = ctx.getMapping(placedParentId);
            if (mapping == null) {
                return tag;
            }
            placedParentId = mapping.newUUID();
            placedParentPos = mapping.transform().apply(placedParentPos);
        } else {
            placedParentPos = ctx.getPlaceTransform().apply(placedParentPos);
        }

        CompoundTag placedTag = tag.copy();
        placedTag.put("ParentPos", NbtUtils.writeBlockPos(placedParentPos));
        placedTag.put("ThrusterParentPos", NbtUtils.writeBlockPos(placedParentPos));
        if (placedParentId != null) {
            placedTag.putUUID("ParentSubLevelId", placedParentId);
            placedTag.putUUID("ThrusterParentSubLevelId", placedParentId);
        }
        return placedTag;
    }

    // Get the connection dependencies
    @Override
    public @Nullable Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        if (level == null || parentSubLevelId == null) {
            return null;
        }
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel subLevel = container == null ? null : container.getSubLevel(parentSubLevelId);
        return subLevel == null || subLevel.isRemoved() ? null : List.of(subLevel);
    }

    // Resolve the parent
    private @Nullable ThrusterBearingBlockEntity resolveParent() {
        if (level == null || parentPos == null) {
            return null;
        }
        BlockEntity parent = SimulatedHelper.findBlockEntityExact(level, parentSubLevelId, parentPos);
        return parent instanceof ThrusterBearingBlockEntity bearing ? bearing : null;
    }

    // Resolve the loaded parent
    private @Nullable ThrusterBearingBlockEntity resolveLoadedParent() {
        if (level == null || parentPos == null) {
            return null;
        }
        BlockEntity parent = SimulatedHelper.findLoadedBlockEntityExact(level, parentSubLevelId, parentPos);
        return parent instanceof ThrusterBearingBlockEntity bearing ? bearing : null;
    }
}
