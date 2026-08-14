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
import dev.eriksonn.aeronautics.content.blocks.propeller.behaviour.PropellerActorBehaviour;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Link a vector bearing to SCM or direct controls without becoming another moving joint
public class VectorBearingLinkBlockEntity extends SmartBlockEntity
        implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final long VISUAL_SYNC_INTERVAL_TICKS = 5L;
    private static final double MIN_PARTICLE_RADIUS = 0.75D;
    private static final double MAX_PARTICLE_RADIUS = 32.0D;
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
    // Particle direction
    private final Vector3d particleDirection = new Vector3d(0.0D, 1.0D, 0.0D);
    // Current particle behaviour
    private PropellerActorBehaviour particleBehaviour;
    // Tracks whether visual is active
    private boolean visualActive;
    // Current visual airflow
    private double visualAirflow;
    // Current visual thrust
    private double visualThrust;
    // Current visual sail power
    private double visualSailPower;
    // Visual rotation rate
    private double visualRotationRate;
    // Current visual sail radius
    private double visualSailRadius;
    // Last visual sync tick
    private long lastVisualSyncTick = Long.MIN_VALUE;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the vector bearing link
    public VectorBearingLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CTBlockEntities.VECTOR_BEARING_LINK.get(), pos, state);
        updateParticleDirection();
        particleBehaviour.setThrustDirection(particleDirection);
        rebuildParticleLayer();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the behaviours
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        particleBehaviour = new PropellerActorBehaviour(this, this);
        particleBehaviour.setParticleAmountUpdater(
                () -> particleAmount(visualRotationRate, visualSailPower));
        particleBehaviour.setParticleCountProperties(50, 10.0D);
        particleBehaviour.setParticlePositionUpdater(this::setRandomParticlePos);
        particleBehaviour.addSimpleLayer(1.0D, MIN_PARTICLE_RADIUS);
        behaviours.add(particleBehaviour);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the vector bearing link
    @Override
    public void tick() {
        updateParticleDirection();
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            if (visualActive) {
                particleBehaviour.spawnParticles();
            }
            return;
        }
        long gameTime = level.getGameTime();
        if (lastVisualSyncTick == Long.MIN_VALUE
                || gameTime - lastVisualSyncTick >= VISUAL_SYNC_INTERVAL_TICKS) {
            lastVisualSyncTick = gameTime;
            refreshVisualState();
        }
    }

    // Update the particle direction
    private void updateParticleDirection() {
        Direction dir = getBlockDirection();
        particleDirection.set(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    // Set the random particle pos
    private void setRandomParticlePos(Vector3d target, RandomSource random) {
        Direction dir = getBlockDirection();
        double radius = particleRadius(visualSailRadius);
        double radialDistance = Math.sqrt(random.nextDouble()) * radius;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double first = Math.cos(angle) * radialDistance;
        double second = Math.sin(angle) * radialDistance;
        double x;
        double y;
        double z;
        switch (dir.getAxis()) {
            case X -> {
                x = dir.getStepX();
                y = first;
                z = second;
            }
            case Y -> {
                x = first;
                y = dir.getStepY();
                z = second;
            }
            case Z -> {
                x = first;
                y = second;
                z = dir.getStepZ();
            }
            default -> throw new IllegalStateException("Unsupported bearing axis");
        }
        target.set(x, y, z);
    }

    // Refresh the visual state
    private void refreshVisualState() {
        VectorBearingBlockEntity parent = resolvePropellerParent();
        boolean nextActive = parent != null && parent.isActive();
        double nextAirflow = parent == null ? 0.0D : parent.getAirflow();
        double nextThrust = parent == null ? 0.0D : parent.getThrust();
        double nextSailPower = parent == null ? 0.0D : parent.getMountedSailPower();
        double nextRotationRate = parent == null ? 0.0D : parent.getPropellerRotationRate();
        double nextSailRadius = parent == null ? 0.0D : parent.getMountedSailRadius();
        if (visualActive == nextActive
                && nearlyEqual(visualAirflow, nextAirflow)
                && nearlyEqual(visualThrust, nextThrust)
                && nearlyEqual(visualSailPower, nextSailPower)
                && nearlyEqual(visualRotationRate, nextRotationRate)
                && nearlyEqual(visualSailRadius, nextSailRadius)) {
            return;
        }
        visualActive = nextActive;
        visualAirflow = nextAirflow;
        visualThrust = nextThrust;
        visualSailPower = nextSailPower;
        visualRotationRate = nextRotationRate;
        visualSailRadius = nextSailRadius;
        rebuildParticleLayer();
        setChanged();
        sendData();
    }

    // Rebuild the particle layer
    private void rebuildParticleLayer() {
        if (particleBehaviour == null) {
            return;
        }
        particleBehaviour.getLayers().clear();
        particleBehaviour.addSimpleLayer(1.0D, particleRadius(visualSailRadius));
    }

    // Get the particle amount
    static double particleAmount(double rotationRate, double sailPower) {
        return 0.02D * Math.abs(rotationRate) * Math.max(0.0D, sailPower);
    }

    // Get the particle radius
    static double particleRadius(double sailRadius) {
        return Math.max(MIN_PARTICLE_RADIUS, Math.min(MAX_PARTICLE_RADIUS, sailRadius));
    }

    // Check if the values are nearly equal
    private static boolean nearlyEqual(double first, double second) {
        return Math.abs(first - second) <= 1.0E-4D;
    }

    // Set the parent
    public void setParent(VectorBearingBlockEntity parent) {
        BlockPos nextParentPos = parent.getBlockPos().immutable();
        UUID nextParentSubLevelId = SimulatedHelper.getContainingSubLevelId(parent);
        boolean topologyChanged = !nextParentPos.equals(parentPos)
                || !Objects.equals(nextParentSubLevelId, parentSubLevelId);
        this.parentPos = nextParentPos;
        this.parentSubLevelId = nextParentSubLevelId;
        if (topologyChanged) {
            SableAssemblyTopologyInvalidation.invalidate(level);
        }
        setChanged();
        sendData();
    }

    // Finish the assembly transfer
    void finishAssemblyTransfer() {
        if (level == null || level.isClientSide) {
            return;
        }
        VectorBearingBlockEntity parent = resolveParent();
        if (parent == null) {
            return;
        }
        UUID childSubLevelId = SimulatedHelper.getContainingSubLevelId(this);
        parent.updateMountedAssemblyFromLink(childSubLevelId, getBlockPos());
        setParent(parent);
    }

    // Disassemble the parent
    public boolean disassembleParent() {
        VectorBearingBlockEntity parent = resolveParent();
        if (parent == null) {
            return false;
        }
        parent.disassembleMountedBlock();
        return true;
    }

    // Write the vector bearing link
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
        tag.putBoolean("PropellerVisualActive", visualActive);
        tag.putDouble("PropellerVisualAirflow", visualAirflow);
        tag.putDouble("PropellerVisualThrust", visualThrust);
        tag.putDouble("PropellerVisualSailPower", visualSailPower);
        tag.putDouble("PropellerVisualRotationRate", visualRotationRate);
        tag.putDouble("PropellerVisualSailRadius", visualSailRadius);
    }

    // Read the vector bearing link
    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        parentPos = tag.contains("ParentPos") ? NbtUtils.readBlockPos(tag, "ParentPos").orElse(null) : null;
        parentSubLevelId = tag.contains("ParentSubLevelId") ? tag.getUUID("ParentSubLevelId") : null;
        visualActive = tag.getBoolean("PropellerVisualActive");
        visualAirflow = tag.getDouble("PropellerVisualAirflow");
        visualThrust = tag.getDouble("PropellerVisualThrust");
        visualSailPower = tag.getDouble("PropellerVisualSailPower");
        visualRotationRate = tag.getDouble("PropellerVisualRotationRate");
        visualSailRadius = tag.getDouble("PropellerVisualSailRadius");
        rebuildParticleLayer();
        remapPlacedParent();
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

    // Get the propeller
    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    // Get the block direction
    @Override
    public Direction getBlockDirection() {
        return getBlockState().getValue(VectorBearingLinkBlock.FACING);
    }

    // Get the airflow
    @Override
    public double getAirflow() {
        if (level != null && level.isClientSide) {
            return visualAirflow;
        }
        VectorBearingBlockEntity parent = resolvePropellerParent();
        return parent == null ? 0.0D : parent.getAirflow();
    }

    // Get the thrust
    @Override
    public double getThrust() {
        if (level != null && level.isClientSide) {
            return visualThrust;
        }
        VectorBearingBlockEntity parent = resolvePropellerParent();
        return parent == null ? 0.0D : parent.getThrust();
    }

    // Check if this is active
    @Override
    public boolean isActive() {
        if (level != null && level.isClientSide) {
            return visualActive;
        }
        VectorBearingBlockEntity parent = resolvePropellerParent();
        return parent != null && parent.isActive();
    }

    // Resolve the propeller parent
    private @Nullable VectorBearingBlockEntity resolvePropellerParent() {
        VectorBearingBlockEntity parent = resolveLoadedParent();
        if (parent == null
                || !Objects.equals(parent.getMountedSubLevelId(), SimulatedHelper.getContainingSubLevelId(this))
                || !Objects.equals(parent.getMountedLocalPos(), getBlockPos())) {
            return null;
        }
        return parent;
    }

    // Resolve the parent
    @Nullable VectorBearingBlockEntity resolveParent() {
        if (level == null || parentPos == null) {
            return null;
        }
        BlockEntity parent = SimulatedHelper.findBlockEntityExact(level, parentSubLevelId, parentPos);
        return parent instanceof VectorBearingBlockEntity bearing ? bearing : null;
    }

    // Resolve the loaded parent
    private @Nullable VectorBearingBlockEntity resolveLoadedParent() {
        if (level == null || parentPos == null) {
            return null;
        }
        BlockEntity parent = SimulatedHelper.findLoadedBlockEntityExact(level, parentSubLevelId, parentPos);
        return parent instanceof VectorBearingBlockEntity bearing ? bearing : null;
    }

    // Check if this belongs to the parent
    boolean isOwnedBy(VectorBearingBlockEntity parent) {
        return parent != null
                && parentPos != null
                && parentPos.equals(parent.getBlockPos())
                && Objects.equals(parentSubLevelId, SimulatedHelper.getContainingSubLevelId(parent));
    }
}
