package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Vector2f;

// Draw a bound docking connector with the Ship Dock texture set
public final class ShipDockingConnectorRenderer {
    private ShipDockingConnectorRenderer() {
    }

    // Draw the bound connector animation
    public static void render(
            DockingConnectorBlockEntity connector,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light
    ) {
        BlockState state = connector.getBlockState();
        Direction facing = state.getValue(BlockStateProperties.FACING);
        float extension = connector.getExtensionDistance(partialTicks);
        float footRotation = connector.getFeetRotation(partialTicks) * 90.0F;
        SuperByteBuffer mainPistonBottom = CachedBuffers.partial(
                CTPartialModels.SHIP_DOCKING_CONNECTOR_MAIN_PISTON_BOTTOM, state);
        SuperByteBuffer mainPistonTop = CachedBuffers.partial(
                CTPartialModels.SHIP_DOCKING_CONNECTOR_MAIN_PISTON_TOP, state);
        SuperByteBuffer sidePistonBottom = CachedBuffers.partial(
                CTPartialModels.SHIP_DOCKING_CONNECTOR_SIDE_PISTON_BOTTOM, state);
        SuperByteBuffer sidePistonTop = CachedBuffers.partial(
                CTPartialModels.SHIP_DOCKING_CONNECTOR_SIDE_PISTON_TOP, state);
        SuperByteBuffer foot = CachedBuffers.partial(CTPartialModels.SHIP_DOCKING_CONNECTOR_FOOT, state);

        poseStack.pushPose();
        DockingConnectorRenderer.rotateToFaceCentered(poseStack, facing);
        mainPistonBottom.translate(0.0D, extension * 0.5D, 0.0D);
        mainPistonTop.translate(0.0F, extension, 0.0F);
        mainPistonBottom.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
        mainPistonTop.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));

        Vector2f footPosition = new Vector2f().set(-7.5F, 15.5F).div(16.0F).add(0.0F, extension);
        Vector2f upperPistonPosition = rotate(new Vector2f().set(1.5F, -2.5F).div(16.0F), footRotation)
                .add(footPosition);
        Vector2f lowerPistonPosition = new Vector2f().set(-6.0F, 2.0F).div(16.0F)
                .add(0.0F, extension / 2.0F);
        Vector2f pistonAxis = new Vector2f(upperPistonPosition).sub(lowerPistonPosition).normalize();
        Matrix4f pistonRotation = new Matrix4f(
                1.0F, 0.0F, 0.0F, 0.0F,
                0.0F, pistonAxis.y, pistonAxis.x, 0.0F,
                0.0F, -pistonAxis.x, pistonAxis.y, 0.0F,
                0.0F, 0.0F, 0.0F, 1.0F);
        for (int index = 0; index < 4; index++) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.0D, 0.5D);
            TransformStack.of(poseStack).rotateYDegrees(index * 90.0F);
            sidePistonBottom.translate(0.0F, lowerPistonPosition.y, lowerPistonPosition.x);
            sidePistonTop.translate(0.0F, upperPistonPosition.y, upperPistonPosition.x);
            foot.translate(0.0F, footPosition.y, footPosition.x);
            foot.rotateXDegrees(footRotation);
            sidePistonBottom.mulPose(pistonRotation);
            sidePistonTop.mulPose(pistonRotation);
            sidePistonBottom.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
            sidePistonTop.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
            foot.light(light).renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    // Rotate a local piston coordinate around the connector axis
    private static Vector2f rotate(Vector2f value, float degrees) {
        float radians = (float) Math.toRadians(degrees);
        float sin = net.minecraft.util.Mth.sin(radians);
        float cos = net.minecraft.util.Mth.cos(radians);
        return value.set(value.x * cos + value.y * sin, value.y * cos - value.x * sin);
    }
}
