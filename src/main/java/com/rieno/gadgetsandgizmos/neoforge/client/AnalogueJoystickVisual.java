package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlock;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickBlockEntity;
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
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.function.Consumer;

// Keep the instanced Analogue Joystick model aligned with its live block state
public class AnalogueJoystickVisual extends AbstractBlockEntityVisual<AnalogueJoystickBlockEntity> implements SimpleDynamicVisual {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle instance
    private final TransformedInstance handleInstance;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick visual
    public AnalogueJoystickVisual(VisualizationContext ctx, AnalogueJoystickBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.handleInstance = this.instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(CTPartialModels.ANALOGUE_JOYSTICK_HANDLE))
                .createInstance();
        updateTransform(partialTick);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Begin the frame
    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        updateTransform(ctx.partialTick());
    }

    // Update the transform
    private void updateTransform(float partialTick) {
        Direction facing = this.blockState.getValue(AnalogueJoystickBlock.FACING);
        float yRotation = AnalogueJoystickBlock.getYRotationDegrees(this.blockState);
        if (facing.getAxis() == Direction.Axis.X) {

            yRotation += 180.0f;
        }
        double renderLocalX = this.blockEntity.getVisualLocalX(partialTick);
        double renderLocalZ = this.blockEntity.getVisualLocalZ(partialTick);
        AttachFace face = this.blockState.getValue(AnalogueJoystickBlock.FACE);
        if (face == AttachFace.CEILING) {
            renderLocalX = -renderLocalX;
            renderLocalZ = -renderLocalZ;
        }

        this.handleInstance.setIdentityTransform()
                .translate(this.getVisualPosition())
                .translate(0.5D, 0.3125D, 0.5D)
                .rotateYDegrees(yRotation)
                .rotateXDegrees(AnalogueJoystickBlock.getXRotationDegrees(this.blockState))
                .rotateZDegrees((float) (renderLocalX * this.blockEntity.getMaxTiltDegrees()))
                .rotateXDegrees((float) (-renderLocalZ * this.blockEntity.getMaxTiltDegrees()))
                .translate(-0.5D, -0.3125D, -0.5D)
                .setChanged();
    }

    // Update the light
    @Override
    public void updateLight(float partialTick) {
        relight(new FlatLit[]{this.handleInstance});
    }

    // Delete the analogue joystick visual
    @Override
    protected void _delete() {
        this.handleInstance.delete();
    }

    // Collect the crumbling instances
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.handleInstance);
    }
}
