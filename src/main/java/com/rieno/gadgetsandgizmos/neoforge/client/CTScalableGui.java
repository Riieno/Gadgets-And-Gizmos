package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collections;
import java.util.List;

// Scale Create-style screens to fit small windows without breaking mouse coordinates
public final class CTScalableGui {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MARGIN = 4;
    public static final int PLAYER_INVENTORY_WIDTH = 176;
    public static final int PLAYER_INVENTORY_HEIGHT = 90;
    private static final int RECIPE_SIDEBAR_RESERVE = 96;
    private static final int RECIPE_BOTTOM_RESERVE = 42;
    private static final int MIN_CENTER_WIDTH = 220;
    private static final float TARGET_MAX_GUI_SCALE = 3.0f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current content left
    private int contentLeft;
    // Current content top
    private int contentTop;
    // Current content width
    private int contentWidth;
    // Current content height
    private int contentHeight;
    // Current visual left
    private int visualLeft;
    // Current visual top
    private int visualTop;
    // Current visual width
    private int visualWidth;
    // Current visual height
    private int visualHeight;
    // Current scale
    private float scale = 1.0f;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the CT scalable gui
    public void update(int contentLeft, int contentTop, int contentWidth, int contentHeight,
                       int fallbackViewportWidth, int fallbackViewportHeight) {
        this.contentLeft = contentLeft;
        this.contentTop = contentTop;
        this.contentWidth = Math.max(1, contentWidth);
        this.contentHeight = Math.max(1, contentHeight);

        int viewportWidth = fallbackViewportWidth;
        int viewportHeight = fallbackViewportHeight;
        float guiScale = 1.0f;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null) {
            viewportWidth = minecraft.getWindow().getGuiScaledWidth();
            viewportHeight = minecraft.getWindow().getGuiScaledHeight();
            guiScale = (float) minecraft.getWindow().getGuiScale();
        }

        int sideReserve = recipeSidebarReserve(viewportWidth);
        int bottomReserve = recipeBottomReserve(viewportHeight);
        int availableLeft = MARGIN + sideReserve;
        int availableWidth = Math.max(1, viewportWidth - MARGIN * 2 - sideReserve * 2);
        int availableHeight = Math.max(1, viewportHeight - MARGIN * 2 - bottomReserve);

        float widthScale = (float) availableWidth / this.contentWidth;
        float heightScale = (float) availableHeight / this.contentHeight;
        float guiScaleLimit = guiScale > TARGET_MAX_GUI_SCALE
                ? TARGET_MAX_GUI_SCALE / guiScale
                : 1.0f;
        scale = Math.min(guiScaleLimit, Math.min(1.0f, Math.min(widthScale, heightScale)));

        visualWidth = Math.max(1, Math.round(this.contentWidth * scale));
        visualHeight = Math.max(1, Math.round(this.contentHeight * scale));
        int centeredLeft = clamp(Math.round(availableLeft + (availableWidth - visualWidth) * 0.5f),
                availableLeft, Math.max(availableLeft, viewportWidth - MARGIN - sideReserve - visualWidth));
        int centeredTop = clamp(Math.round((viewportHeight - visualHeight) * 0.5f),
                MARGIN, Math.max(MARGIN, viewportHeight - MARGIN - bottomReserve - visualHeight));
        if (scale < 1.0f || contentLeft < availableLeft
                || contentLeft + visualWidth > viewportWidth - MARGIN - sideReserve) {
            visualLeft = centeredLeft;
        } else {
            visualLeft = contentLeft;
        }
        visualTop = scale < 1.0f
                ? centeredTop
                : clamp(contentTop, MARGIN, Math.max(MARGIN, viewportHeight - MARGIN - bottomReserve - visualHeight));
    }

    // Update the unscaled
    public void updateUnscaled(int contentLeft, int contentTop, int contentWidth, int contentHeight) {
        this.contentLeft = contentLeft;
        this.contentTop = contentTop;
        this.contentWidth = Math.max(1, contentWidth);
        this.contentHeight = Math.max(1, contentHeight);
        this.visualLeft = contentLeft;
        this.visualTop = contentTop;
        this.visualWidth = this.contentWidth;
        this.visualHeight = this.contentHeight;
        this.scale = 1.0f;
    }

    // Push the CT scalable gui
    public void push(GuiGraphics graphics) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (isTransformed()) {
            pose.translate(visualLeft, visualTop, 0.0f);
            pose.scale(scale, scale, 1.0f);
            pose.translate(-contentLeft, -contentTop, 0.0f);
        }
    }

    // Restore the previous GUI scale
    public void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    // Push the content origin
    public void pushFromContentOrigin(GuiGraphics graphics) {
        pushFromOrigin(graphics, contentLeft, contentTop);
    }

    // Push the origin
    public void pushFromOrigin(GuiGraphics graphics, int originLeft, int originTop) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        if (isTransformed()) {
            float translatedLeft = visualLeft - originLeft + (originLeft - contentLeft) * scale;
            float translatedTop = visualTop - originTop + (originTop - contentTop) * scale;
            pose.translate(translatedLeft, translatedTop, 0.0f);
            pose.scale(scale, scale, 1.0f);
        }
    }

    // Handle mouse x
    public int mouseX(int mouseX) {
        return (int) mouseX((double) mouseX);
    }

    // Handle mouse y
    public int mouseY(int mouseY) {
        return (int) mouseY((double) mouseY);
    }

    // Handle mouse x
    public double mouseX(double mouseX) {
        return isTransformed() ? contentLeft + (mouseX - visualLeft) / scale : mouseX;
    }

    // Handle mouse y
    public double mouseY(double mouseY) {
        return isTransformed() ? contentTop + (mouseY - visualTop) / scale : mouseY;
    }

    // Convert the CT scalable gui to viewport x
    public int toViewportX(int contentX) {
        return isTransformed() ? Math.round(visualLeft + (contentX - contentLeft) * scale) : contentX;
    }

    // Convert the CT scalable gui to viewport y
    public int toViewportY(int contentY) {
        return isTransformed() ? Math.round(visualTop + (contentY - contentTop) * scale) : contentY;
    }

    // Get the extra areas
    public List<Rect2i> extraAreas() {
        return Collections.emptyList();
    }

    // Check if this is scaled
    public boolean isScaled() {
        return scale < 1.0f;
    }

    // Check if this is transformed
    public boolean isTransformed() {
        return scale < 1.0f || visualLeft != contentLeft || visualTop != contentTop;
    }

    // Clamp the CT scalable gui
    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    // Get the recipe sidebar reserve
    private static int recipeSidebarReserve(int viewportWidth) {
        int maxReserve = Math.max(0, (viewportWidth - MIN_CENTER_WIDTH - MARGIN * 2) / 2);
        return Math.min(RECIPE_SIDEBAR_RESERVE, maxReserve);
    }

    // Get the recipe bottom reserve
    private static int recipeBottomReserve(int viewportHeight) {
        int maxReserve = Math.max(0, viewportHeight - MIN_CENTER_WIDTH - MARGIN * 2);
        return Math.min(RECIPE_BOTTOM_RESERVE, maxReserve);
    }
}
