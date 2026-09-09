package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.BiDirectionalGearboxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

// Draw the Bidirectional Gearbox
public class BiDirectionalGearboxRenderer extends KineticBlockEntityRenderer<BiDirectionalGearboxBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearbox
    public BiDirectionalGearboxRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the bi directional gearbox
    @Override
    protected void renderSafe(BiDirectionalGearboxBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        Direction.Axis primaryAxis = be.getPrimaryLaneAxis();
        Direction.Axis secondaryAxis = be.getSecondaryLaneAxis();

        for (Direction dir : Direction.values()) {
            Direction.Axis axis = dir.getAxis();
            if (axis != primaryAxis && axis != secondaryAxis) {
                continue;
            }

            KineticBlockEntity lane = be.getVisualKineticBlockEntity(dir);
            float speed = be.getVisualShaftSpeed(dir);

            SuperByteBuffer shaft = CachedBuffers.partialFacing((PartialModel) AllPartialModels.SHAFT_HALF,
                    be.getBlockState(), dir);

            float angle = (time * speed * 3.0f / 10.0f + be.getVisualShaftOffset(dir)) % 360.0f;
            angle = angle / 180.0f * (float) Math.PI;
            CTFlywheelVisuals.kineticRotationTransformWhite(shaft, lane, axis, angle, light);
            shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }
}
