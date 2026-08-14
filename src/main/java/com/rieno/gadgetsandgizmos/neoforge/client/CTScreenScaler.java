package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;

// Calculate a safe GUI scale and translate mouse coordinates back to screen space
public final class CTScreenScaler {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MARGIN = 4;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT screen scaler
    private CTScreenScaler() {}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Calculate the CT screen scaler
    public static float compute(int panelW, int panelH, int availW, int availH) {
        if (panelW <= 0 || panelH <= 0 || availW <= 0 || availH <= 0) return 1.0f;
        float sx = (float)(availW - MARGIN * 2) / panelW;
        float sy = (float)(availH - MARGIN * 2) / panelH;
        return Math.min(1.0f, Math.min(sx, sy));
    }

    // Get the imx d
    public static double imxD(double mx, int cx, float s) {
        return cx + (mx - cx) / s;
    }

    // Get the imy d
    public static double imyD(double my, int cy, float s) {
        return cy + (my - cy) / s;
    }

    // Get the imx
    public static int imx(int mx, int cx, float s) {
        return (int) imxD(mx, cx, s);
    }

    // Get the imy
    public static int imy(int my, int cy, float s) {
        return (int) imyD(my, cy, s);
    }

    // Push the CT screen scaler
    public static void push(GuiGraphics graphics, int cx, int cy, float scale) {
        PoseStack ps = graphics.pose();
        ps.pushPose();
        if (scale < 1.0f) {
            ps.translate(cx, cy, 0.0f);
            ps.scale(scale, scale, 1.0f);
            ps.translate(-cx, -cy, 0.0f);
        }
    }

    // Restore the previous GUI scale
    public static void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    // Scale the scissor coord
    public static int scaleScissorCoord(int coord, int center, float scale) {
        return Math.round(center + (coord - center) * scale);
    }
}
