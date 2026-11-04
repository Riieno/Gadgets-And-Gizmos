package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

// Draw the Thruster Bearing
public class ThrusterBearingRenderer extends SwivelBearingRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster bearing
    public ThrusterBearingRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster bearing
    @Override
    protected void renderSafe(SwivelBearingBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization((LevelAccessor) be.getLevel())) {
            return;
        }

        renderCog(be, ms, buffer, light);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the SCM preview partial
    static boolean renderPreview(BlockEntity entity, BlockState state, PoseStack ms,
                                 MultiBufferSource buffer, int light) {
        if (!(entity instanceof SwivelBearingBlockEntity bearing)) {
            return false;
        }
        return renderCog(bearing, ms, buffer, light);
    }

    // Draw the bearing cog
    private static boolean renderCog(SwivelBearingBlockEntity be, PoseStack ms,
                                     MultiBufferSource buffer, int light) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof IRotate rotate)
                || !state.hasProperty(com.rieno.gadgetsandgizmos.content.CTDirectionalBlock.FACING)
                || !(be.getExtraKinetics() instanceof KineticBlockEntity extraKinetics)) {
            return false;
        }
        Direction.Axis axis = rotate.getRotationAxis(state);
        Direction connectorFacing = state.getValue(com.rieno.gadgetsandgizmos.content.CTDirectionalBlock.FACING).getOpposite();
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        SuperByteBuffer cogwheel = CTFlywheelVisuals.kineticRotationTransformWhite(
                CachedBuffers.partialFacingVertical(SimPartialModels.SWIVEL_BEARING_COG, state, connectorFacing),
                extraKinetics,
                axis,
                getAngleForBe(extraKinetics, be.getBlockPos(), axis),
                light);
        cogwheel.renderInto(ms, vb);
        return true;
    }
}
