package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;

import java.util.function.Consumer;

// Keep the instanced Scissor Piston Shaft model aligned with its live block state
public class ScissorPistonShaftVisual extends KineticBlockEntityVisual<ScissorPistonBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shaft direction
    private final Direction shaftDirection;
    // Shaft
    private final RotatingInstance shaft;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the scissor piston shaft visual
    public ScissorPistonShaftVisual(VisualizationContext ctx, ScissorPistonBlockEntity blockEntity,
                                    float partialTick) {
        super(ctx, blockEntity, partialTick);
        shaftDirection = blockEntity.getPistonFacing().getOpposite();
        Instancer<RotatingInstance> instancer = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(CTPartialModels.SCISSOR_PISTON_SHAFT));
        shaft = instancer.createInstance();
        shaft.rotateToFace(Direction.SOUTH, shaftDirection)
                .setup(blockEntity, shaftDirection.getAxis())
                .setPosition((Vec3i) getVisualPosition());
        CTFlywheelVisuals.forceWhite(shaft).setChanged();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the scissor piston shaft visual
    @Override
    public void update(float partialTick) {
        CTFlywheelVisuals.forceWhite(shaft.setup(blockEntity, shaftDirection.getAxis()))
                .setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[]{shaft});
    }

    // Delete the scissor piston shaft visual
    @Override
    protected void _delete() {
        shaft.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
    }
}
