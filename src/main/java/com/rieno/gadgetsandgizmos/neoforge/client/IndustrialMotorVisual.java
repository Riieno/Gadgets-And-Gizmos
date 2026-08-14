package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

// Keep the instanced Industrial Motor model aligned with its live block state
public class IndustrialMotorVisual extends KineticBlockEntityVisual<IndustrialMotorBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shaft
    private final RotatingInstance shaft;
    // Rotor
    private final RotatingInstance rotor;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the industrial motor visual
    public IndustrialMotorVisual(VisualizationContext ctx, IndustrialMotorBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Direction facing = blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
        this.shaft = this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance();
        this.shaft
                .setRotationAxis(facing.getAxis())
                .setRotationalSpeed(blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
                .setRotationOffset(0f)
                .setPosition((Vec3i) this.getVisualPosition())
                .rotateToFace(Direction.SOUTH, facing);

        CTFlywheelVisuals.forceWhite(this.shaft)
                .setChanged();

        this.rotor = this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(CTPartialModels.ALTERNATOR_ROTOR))
                .createInstance();
        this.rotor
                .setRotationAxis(facing.getAxis())
                .setRotationalSpeed(blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
                .setRotationOffset(0f)
                .setPosition((Vec3i) this.getVisualPosition())
                .rotateToFace(Direction.SOUTH, facing);
        CTFlywheelVisuals.forceWhite(this.rotor)
                .setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the industrial motor visual
    @Override
    public void update(float partialTick) {
        Direction facing = this.blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
        this.shaft
                .setRotationAxis(facing.getAxis())
                .setRotationalSpeed(this.blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
                .setRotationOffset(0f);
        CTFlywheelVisuals.forceWhite(this.shaft)
                .setChanged();
        this.rotor
                .setRotationAxis(facing.getAxis())
                .setRotationalSpeed(this.blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
                .setRotationOffset(0f);
        CTFlywheelVisuals.forceWhite(this.rotor)
                .setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        this.relight(new FlatLit[] { this.shaft, this.rotor });
    }

    // Delete the industrial motor visual
    @Override
    protected void _delete() {
        this.shaft.delete();
        this.rotor.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.shaft);
        consumer.accept(this.rotor);
    }
}
