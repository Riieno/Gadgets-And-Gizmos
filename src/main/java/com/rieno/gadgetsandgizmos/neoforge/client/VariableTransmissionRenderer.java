package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.VariableTransmissionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Draw the Variable Transmission
public class VariableTransmissionRenderer extends WhiteKineticBlockEntityRenderer<VariableTransmissionBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the variable transmission
    public VariableTransmissionRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the variable transmission
    @Override
    protected void renderSafe(VariableTransmissionBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        BlockState state = getRenderedBlockState(be);
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        float angle = getAngleForBe(be, be.getBlockPos(), axis);

        for (Direction.AxisDirection axisDirection : Direction.AxisDirection.values()) {
            Direction facing = Direction.get(axisDirection, axis);
            SuperByteBuffer shaft = CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF, state, facing);
            CTFlywheelVisuals.kineticRotationTransformWhite(shaft, be, axis, angle, light)
                    .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

}
