package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.content.RatchetCogwheelBlock;
import com.rieno.gadgetsandgizmos.content.RatchetCogwheelBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

// Draw the ratchet cogwheel gear and shaft
public class RatchetCogwheelRenderer extends KineticBlockEntityRenderer<RatchetCogwheelBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ratchet cogwheel renderer
    public RatchetCogwheelRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ratchet cogwheel
    @Override
    protected void renderSafe(RatchetCogwheelBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
        Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        boolean large = state.getBlock() instanceof RatchetCogwheelBlock cogwheel && cogwheel.isLargeCog();
        PartialModel gearModel = large
                ? CTPartialModels.LARGE_RATCHET_COGWHEEL_GEAR
                : CTPartialModels.RATCHET_COGWHEEL_GEAR;
        PartialModel shaftModel = large
                ? CTPartialModels.LARGE_RATCHET_COGWHEEL_SHAFT
                : CTPartialModels.RATCHET_COGWHEEL_SHAFT;
        VertexConsumer consumer = buffer.getBuffer(RenderType.cutoutMipped());
        KineticBlockEntity shaft = be.getShaftKineticBlockEntity();
        float gearAngle = getAngleForBe(be, be.getBlockPos(), axis);
        float shaftAngle = getAngleForBe(shaft, be.getBlockPos(), axis);

        renderPartial(be, state, gearModel, facing, axis, gearAngle, ms, consumer, light);
        renderPartial(shaft, state, shaftModel, facing, axis, shaftAngle, ms, consumer, light);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Helpers
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw one rotating ratchet partial
    private static void renderPartial(KineticBlockEntity be, BlockState state, PartialModel model,
                                      Direction facing, Direction.Axis axis, float angle, PoseStack ms,
                                      VertexConsumer consumer, int light) {
        SuperByteBuffer partial = CachedBuffers.partialFacingVertical(model, state, facing);
        kineticRotationTransform(partial, be, axis, angle, light).renderInto(ms, consumer);
    }
}
