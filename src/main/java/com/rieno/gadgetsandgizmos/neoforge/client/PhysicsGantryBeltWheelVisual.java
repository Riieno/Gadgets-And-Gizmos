package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

// Keep the instanced Physics Gantry Belt Wheel model aligned with its live block state
public class PhysicsGantryBeltWheelVisual extends KineticBlockEntityVisual<PhysicsGantryBeltWheelBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rotating model
    private final RotatingInstance rotatingModel;
    // Rotating top shaft
    private final RotatingInstance rotatingTopShaft;
    // Rotating bottom shaft
    private final RotatingInstance rotatingBottomShaft;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics gantry belt wheel visual
    public PhysicsGantryBeltWheelVisual(VisualizationContext ctx, PhysicsGantryBeltWheelBlockEntity blockEntity,
                                        float partialTick) {
        super(ctx, blockEntity, partialTick);

        BlockState state = blockEntity.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        this.rotatingModel = this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFTLESS_COGWHEEL))
                .createInstance();
        this.rotatingModel
                .setup(blockEntity)
            .setPosition(this.getVisualPosition())
                .rotateToFace(axis);

        CTFlywheelVisuals.forceWhite(this.rotatingModel)
                .setChanged();

        RotatingInstance topShaft = null;
        RotatingInstance bottomShaft = null;
        for (Direction dir : Iterate.directionsInAxis(axis)) {
            RotatingInstance shaft = this.instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance();
            shaft.setup(blockEntity)
                    .setPosition((Vec3i) this.getVisualPosition())
                    .rotateToFace(Direction.SOUTH, dir);
            CTFlywheelVisuals.forceWhite(shaft)
                    .setChanged();

            if (dir.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                topShaft = shaft;
            } else {
                bottomShaft = shaft;
            }
        }

        this.rotatingTopShaft = topShaft;
        this.rotatingBottomShaft = bottomShaft;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the physics gantry belt wheel visual
    @Override
    public void update(float partialTick) {

        this.rotatingModel
            .setup(this.blockEntity)
                .colorRgb(0xFFFFFF)
                .setChanged();
        if (this.rotatingTopShaft != null) {
            this.rotatingTopShaft.setup(this.blockEntity)
                    .colorRgb(0xFFFFFF)
                    .setChanged();
        }
        if (this.rotatingBottomShaft != null) {
            this.rotatingBottomShaft.setup(this.blockEntity)
                    .colorRgb(0xFFFFFF)
                    .setChanged();
        }
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {

        this.relight(this.rotatingModel, this.rotatingTopShaft, this.rotatingBottomShaft);
    }

    // Delete the physics gantry belt wheel visual
    @Override
    protected void _delete() {
        this.rotatingModel.delete();
        if (this.rotatingTopShaft != null) {
            this.rotatingTopShaft.delete();
        }
        if (this.rotatingBottomShaft != null) {
            this.rotatingBottomShaft.delete();
        }
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.rotatingModel);
        if (this.rotatingTopShaft != null) {
            consumer.accept(this.rotatingTopShaft);
        }
        if (this.rotatingBottomShaft != null) {
            consumer.accept(this.rotatingBottomShaft);
        }
    }

}
