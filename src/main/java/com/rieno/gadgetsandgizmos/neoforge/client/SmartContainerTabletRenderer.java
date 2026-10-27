package com.rieno.gadgetsandgizmos.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rieno.gadgetsandgizmos.content.*;
import com.rieno.gadgetsandgizmos.lib.multiblock.MultiblockPanelLayout;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.MultiFluidTankRenderer;
import com.rieno.gadgetsandgizmos.lib.client.render.HologramRenderScope;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderVisibilityGate;
import com.rieno.gadgetsandgizmos.lib.client.view.ViewSceneRenderer;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

// Render the tablet as a decorative partial while retaining the original storage renderers
public final class SmartContainerTabletRenderer{
    private static final float SCALE = .75F;
    private static final AABB TABLET_BOUNDS = new AABB(0, 0, 1.5D / 16, 1, 2D / 16, 14.5D / 16).inflate(.002D);
    private static final RenderVisibilityGate<BlockEntity> VISIBILITY = new RenderVisibilityGate<>();
    private SmartContainerTabletRenderer(){}

    private static void render(BlockEntity target, float partial, PoseStack poses, MultiBufferSource buffers, int light){
        if(!SmartContainerTablets.supports(target)) return;
        boolean hologram = HologramRenderScope.isActive();
        for(var panel : SmartContainerTablets.panels(target)){
            if(!panel.owner().equals(target.getBlockPos())) continue;
            poses.pushPose();
            try{
                Vec3 center = panel.center().subtract(Vec3.atLowerCornerOf(target.getBlockPos()));
                var face = panel.facing();
                poses.translate(center.x + face.getStepX() * .002D, center.y, center.z + face.getStepZ() * .002D);
                Matrix4f rotation = new Matrix4f().set(
                        face.getStepZ(), 0, -face.getStepX(), 0,
                        face.getStepX(), 0, face.getStepZ(), 0,
                        0, -1, 0, 0,
                        0, 0, 0, 1);
                poses.mulPose(rotation);
                poses.scale(SCALE, SCALE, SCALE);
                poses.translate(-.5D, 0, -.5D);
                if(!hologram && !ViewSceneRenderer.isCapturing()
                        && !VISIBILITY.shouldRender(target, face, poses, buffers, TABLET_BOUNDS)) continue;
                CachedBuffers.partial(CTPartialModels.SMART_CONTAINER_TABLET, Blocks.AIR.defaultBlockState())
                        .light(light).renderInto(poses, buffers.getBuffer(RenderType.cutoutMipped()));
                if(!hologram) DiagnosticTabletGuiProjection.renderContainer(SmartContainerTablets.controller(target),
                        DiagnosticTabletSurface.screen(net.minecraft.core.Direction.UP), poses, buffers);
            }finally{ poses.popPose(); }
        }
    }

    public static final class Tank extends MultiFluidTankRenderer{
        public Tank(BlockEntityRendererProvider.Context ctx){ super(ctx); }
        @Override protected void renderSafe(FluidTankBlockEntity tank, float partial, PoseStack poses,
                                             MultiBufferSource buffers, int light, int overlay){
            super.renderSafe(tank, partial, poses, buffers, light, overlay);
            SmartContainerTabletRenderer.render(tank, partial, poses, buffers, light);
        }
    }

    public static final class Vault extends SmartBlockEntityRenderer<ItemVaultBlockEntity>{
        public Vault(BlockEntityRendererProvider.Context ctx){ super(ctx); }
        @Override protected void renderSafe(ItemVaultBlockEntity vault, float partial, PoseStack poses,
                                             MultiBufferSource buffers, int light, int overlay){
            SmartContainerTabletRenderer.render(vault, partial, poses, buffers, light);
        }
    }
}
