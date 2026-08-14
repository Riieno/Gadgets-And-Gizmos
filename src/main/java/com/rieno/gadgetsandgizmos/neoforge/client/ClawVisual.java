package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ClawBlock;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

// Keep the instanced Claw model aligned with its live block state
public class ClawVisual extends AbstractBlockEntityVisual<ClawBlockEntity> implements SimpleDynamicVisual {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float MAX_JAW_ANGLE = 27.5f;

    private static final double PIVOT_X = 8.0 / 16.0;
    private static final double PIVOT_Y = 5.0 / 16.0;
    private static final double PIVOT_Z = 8.0 / 16.0;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Jaw left
    private final TransformedInstance jawLeft;
    // Jaw right
    private final TransformedInstance jawRight;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw visual
    public ClawVisual(VisualizationContext ctx, ClawBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        jawLeft = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(CTPartialModels.CLAW_JAW_LEFT))
                .createInstance();

        jawRight = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(CTPartialModels.CLAW_JAW_RIGHT))
                .createInstance();

        updateJawTransforms(partialTick);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Begin the frame
    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        updateJawTransforms(ctx.partialTick());
    }

    // Update the jaw transforms
    private void updateJawTransforms(float partialTick) {

        float openAngle = (float) blockEntity.clawAngle.getValue(partialTick) * MAX_JAW_ANGLE;

        Direction facing = blockState.getValue(ClawBlock.FACING);
        float xRot = ClawBlock.getXRotationDegrees(facing);
        float yRot = ClawBlock.getYRotationDegrees(facing);

        jawLeft.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5, 0.5, 0.5)
                .rotateYDegrees(yRot)
                .rotateXDegrees(xRot)
                .translate(-0.5, -0.5, -0.5)
                .translate(PIVOT_X, PIVOT_Y, PIVOT_Z)
                .rotateXDegrees(-openAngle)
                .translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z)
                .setChanged();

        jawRight.setIdentityTransform()
                .translate(getVisualPosition())
                .translate(0.5, 0.5, 0.5)
                .rotateYDegrees(yRot)
                .rotateXDegrees(xRot)
                .translate(-0.5, -0.5, -0.5)
                .translate(PIVOT_X, PIVOT_Y, PIVOT_Z)
                .rotateXDegrees(openAngle)
                .translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z)
                .setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[]{jawLeft, jawRight});
    }

    // Delete the claw visual
    @Override
    protected void _delete() {
        jawLeft.delete();
        jawRight.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(jawLeft);
        consumer.accept(jawRight);
    }
}
