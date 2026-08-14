package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RcsThrusterBlockEntity;
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

// Keep the instanced RCS Thruster model aligned with its live block state
public class RcsThrusterVisual extends KineticBlockEntityVisual<RcsThrusterBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float AXIS_OUTWARD_OFFSET = 0.5F / 16.0F;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shaft
    private final RotatingInstance shaft;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS thruster visual
    public RcsThrusterVisual(
            VisualizationContext ctx,
            RcsThrusterBlockEntity blockEntity,
            float partialTick
    ) {
        super(ctx, blockEntity, partialTick);
        shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(CTPartialModels.RCS_AXIS))
                .createInstance();
        configShaftGeometry();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Configure the shaft geometry
    private void configShaftGeometry() {
        Direction facing = blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
        Direction shaftFacing = facing.getOpposite();
        shaft.setup(blockEntity, shaftFacing.getAxis())
                .setPosition((Vec3i) getVisualPosition())
                .nudge(
                        shaftFacing.getStepX() * AXIS_OUTWARD_OFFSET,
                        shaftFacing.getStepY() * AXIS_OUTWARD_OFFSET,
                        shaftFacing.getStepZ() * AXIS_OUTWARD_OFFSET)
                .rotateToFace(Direction.NORTH, shaftFacing);
        CTFlywheelVisuals.forceWhite(shaft).setChanged();
    }

    // Update the RCS thruster visual
    @Override
    public void update(float partialTick) {
        Direction.Axis axis = blockEntity.getBlockState()
                .getValue(BlockStateProperties.FACING)
                .getOpposite()
                .getAxis();
        shaft.setup(blockEntity, axis);
        CTFlywheelVisuals.forceWhite(shaft).setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[]{shaft});
    }

    // Delete the RCS thruster visual
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
