package com.rieno.gadgetsandgizmos.compat.recipe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

// Draw the powered thruster model without creating a block entity or particles
public final class ThrusterProcessingPreview{
    public static final int WIDTH = 192;
    public static final int HEIGHT = 80;
    public static final int INPUT_X = 21;
    public static final int INPUT_Y = 48;

    private ThrusterProcessingPreview(){}

    // Keep the primary result aligned with Create's bulk processing layout
    public static int outputX(int count, int idx){
        return count == 1 ? 156 : 118 + idx % 4 * 18;
    }

    public static int outputY(int count, int idx){
        return count == 1 ? 48 : 12 + idx / 4 * 18;
    }

    // Render the same layout in every recipe viewer
    public static void render(GuiGraphics graphics, int left, int top){
        AllGuiTextures.JEI_SHADOW.render(graphics, left + 55, top + 34);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, left + 54, top + 52);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + 86.0D, top + 43.0D, 200.0D);
        pose.mulPose(Axis.XP.rotationDegrees(25.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-45.0F));
        pose.scale(29.0F, -29.0F, 29.0F);
        pose.translate(-0.5D, -0.5D, -0.5D);
        var state = CTBlocks.THRUSTER.get().defaultBlockState()
                .setValue(ThrusterBlock.POWERED, true)
                .setValue(ThrusterBlock.FACING, Direction.EAST);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, graphics.bufferSource(),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        graphics.flush();
        pose.popPose();
    }
}
