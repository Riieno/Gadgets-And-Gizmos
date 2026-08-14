package com.rieno.gadgetsandgizmos.content.contraptions.gantry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// Keep a gantry contraption attached to its Sable parent while it moves
public class PhysicsGantryContraptionEntity extends GantryContraptionEntity {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolved movement axis field
    private static Field movementAxisField;
    // Resolved axis motion field
    private static Field axisMotionField;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        try {
            movementAxisField = GantryContraptionEntity.class.getDeclaredField("movementAxis");
            movementAxisField.setAccessible(true);
            axisMotionField = GantryContraptionEntity.class.getDeclaredField("axisMotion");
            axisMotionField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access GantryContraptionEntity fields", e);
        }
    }

    // Initialize the physics gantry contraption entity
    public PhysicsGantryContraptionEntity(EntityType<?> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the physics gantry contraption entity
    public static PhysicsGantryContraptionEntity create(Level world, GantryContraption contraption, Direction movementAxis) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("create:gantry_contraption"));
        if (entityType == null) {
            throw new IllegalStateException("Missing create:gantry_contraption entity type");
        }
        PhysicsGantryContraptionEntity entity = new PhysicsGantryContraptionEntity(entityType, world);
        entity.setContraption(contraption);
        try {
            movementAxisField.set(entity, movementAxis);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set movementAxis", e);
        }
        return entity;
    }

    // Validate the pinion shaft
    @Override
    protected void checkPinionShaft() {
        Direction facing = ((GantryContraption) this.contraption).getFacing();
        Vec3 currentPosition = this.getAnchorVec().add(0.5, 0.5, 0.5);
        BlockPos gantryShaftPos = BlockPos.containing((Position) currentPosition).relative(facing.getOpposite());
        BlockEntity be = this.level().getBlockEntity(gantryShaftPos);

        if (!(be instanceof PhysicsGantryShaftBlockEntity gantryShaftBlockEntity)) {
            if (!this.level().isClientSide) {
                this.setContraptionMotion(Vec3.ZERO);
                this.disassemble();
            }
            return;
        }

        BlockState blockState = be.getBlockState();
        if (blockState.getBlock() != CTBlocks.PHYSICS_GANTRY_SHAFT.get()) {
            if (!this.level().isClientSide) {
                this.setContraptionMotion(Vec3.ZERO);
                this.disassemble();
            }
            return;
        }

        Direction dir = blockState.getValue(PhysicsGantryShaftBlock.FACING);
        float pinionMovementSpeed = gantryShaftBlockEntity.getPinionMovementSpeed();
        if (pinionMovementSpeed == 0.0f) {
            this.setContraptionMotion(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.disassemble();
            }
            return;
        }

        if (this.sequencedOffsetLimit >= 0.0) {
            pinionMovementSpeed = (float) Mth.clamp(pinionMovementSpeed, -this.sequencedOffsetLimit, this.sequencedOffsetLimit);
        }

        Vec3 movementVec = Vec3.atLowerCornerOf(dir.getNormal()).scale(pinionMovementSpeed);
        Vec3 nextPosition = currentPosition.add(movementVec);
        double currentCoord = dir.getAxis().choose(currentPosition.x, currentPosition.y, currentPosition.z);
        double nextCoord = dir.getAxis().choose(nextPosition.x, nextPosition.y, nextPosition.z);
        boolean crossedBoundary = (Mth.floor(currentCoord) + 0.5 < nextCoord)
                != (pinionMovementSpeed * dir.getAxisDirection().getStep() < 0.0f);

        if (crossedBoundary && !gantryShaftBlockEntity.canAssembleOn()) {
            this.setContraptionMotion(Vec3.ZERO);
            if (!this.level().isClientSide) {
                this.disassemble();
            }
            return;
        }

        if (this.level().isClientSide) {
            return;
        }

        try {
            axisMotionField.set(this, pinionMovementSpeed);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set axisMotion", e);
        }
        this.setContraptionMotion(movementVec);
    }
}
