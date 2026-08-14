package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rieno.gadgetsandgizmos.neoforge.GraphV2ThemeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

// Load, validate and resolve the colors used by the advanced controller editor
public final class AdvancedControllerV2Theme {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared canvas background
    static int CANVAS_BACKGROUND;
    // Shared canvas dot
    static int CANVAS_DOT;
    // Shared title background
    static int TITLE_BACKGROUND;
    // Shared panel background
    static int PANEL_BACKGROUND;
    // Shared panel raised
    static int PANEL_RAISED;
    // Shared panel hovered
    static int PANEL_HOVERED;
    // Shared panel selected
    static int PANEL_SELECTED;
    // Shared border
    static int BORDER;
    // Shared border strong
    static int BORDER_STRONG;
    // Shared border soft
    static int BORDER_SOFT;
    // Shared primary
    static int PRIMARY;
    // Shared secondary
    static int SECONDARY;
    // Shared muted
    static int MUTED;
    // Shared accent
    static int ACCENT;
    // Shared accent light
    static int ACCENT_LIGHT;
    // Shared accent dark
    static int ACCENT_DARK;
    // Shared accent overlay
    static int ACCENT_OVERLAY;
    // Shared primary action text
    static int PRIMARY_ACTION_TEXT;
    // Shared danger
    static int DANGER;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shared state
    static {
        applyPalette(GraphV2ThemeData.DEFAULT);
    }

    // Initialize the advanced controller V2 theme
    private AdvancedControllerV2Theme() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Apply the palette
    public static void applyPalette(GraphV2ThemeData.Palette palette) {
        GraphV2ThemeData.Palette resolved = palette == null ? GraphV2ThemeData.DEFAULT : palette;
        CANVAS_BACKGROUND = resolved.canvasBackground();
        CANVAS_DOT = resolved.canvasDot();
        TITLE_BACKGROUND = resolved.titleBackground();
        PANEL_BACKGROUND = resolved.panelBackground();
        PANEL_RAISED = resolved.panelRaised();
        PANEL_HOVERED = resolved.panelHovered();
        PANEL_SELECTED = resolved.panelSelected();
        BORDER = resolved.border();
        BORDER_STRONG = resolved.borderStrong();
        BORDER_SOFT = resolved.borderSoft();
        PRIMARY = resolved.primary();
        SECONDARY = resolved.secondary();
        MUTED = resolved.muted();
        ACCENT = resolved.accent();
        ACCENT_LIGHT = resolved.accentLight();
        ACCENT_DARK = resolved.accentDark();
        ACCENT_OVERLAY = resolved.accentOverlay();
        PRIMARY_ACTION_TEXT = resolved.primaryActionText();
        DANGER = resolved.danger();
    }

    // Draw the title bar
    static void drawTitleBar(GuiGraphics graphics, int x, int y, int width, int height) {
        fill(graphics, x, y, x + width, y + height, TITLE_BACKGROUND);
        fill(graphics, x, y + height - 1, x + width, y + height, BORDER);
    }

    // Draw the sidebar
    static void drawSidebar(GuiGraphics graphics, int x, int y, int width, int height) {
        fill(graphics, x, y, x + width, y + height, PANEL_BACKGROUND);
    }

    // Draw the panel
    static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawRoundedRect(graphics, x, y, width, height, 5, BORDER_STRONG);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 4, PANEL_RAISED);
    }

    // Draw the node frame
    static void drawNodeFrame(GuiGraphics graphics, int x, int y, int width, int height,
                              int titleHeight, int accentColor, boolean selected) {
        int border = selected ? ACCENT : BORDER;
        if (selected) {
            drawRoundedRect(graphics, x + 4, y + 4, width, height, 7, withAlpha(ACCENT_DARK, 0x33));
            drawRoundedRect(graphics, x - 1, y - 1, width + 2, height + 2, 7, withAlpha(ACCENT, 0x66));
        }
        drawRoundedRect(graphics, x, y, width, height, 6, border);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 5, PANEL_RAISED);

        int headerBottom = Math.min(y + height - 1, y + Math.max(1, titleHeight));
        int headerColor = mix(PANEL_RAISED, accentColor, 0.14D);
        drawTopRoundedRect(graphics, x + 1, y + 1, width - 2,
                Math.max(1, headerBottom - y - 1), 5, headerColor);
        if (headerBottom < y + height - 1) {
            fill(graphics, x + 1, headerBottom - 1, x + width - 1, headerBottom, BORDER_SOFT);
        }
        int indicatorSize = Math.min(6, Math.max(3, titleHeight / 4));
        drawCircle(graphics, x + 12, y + Math.max(7, titleHeight / 2), indicatorSize / 2,
                (accentColor & 0x00FFFFFF) | 0x55000000);
        drawCircle(graphics, x + 12, y + Math.max(7, titleHeight / 2),
                Math.max(1, indicatorSize / 2 - 1), accentColor);
    }

    // Draw the option
    static void drawOption(GuiGraphics graphics, int x, int y, int width, int height,
                           int accentColor, boolean selected, boolean accent) {
        int border = selected ? ACCENT : BORDER;
        drawRoundedRect(graphics, x, y, width, height, Math.min(4, Math.max(1, height / 3)), border);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2,
                Math.min(3, Math.max(1, height / 3 - 1)), selected ? PANEL_SELECTED : PANEL_RAISED);
        if (accent && height > 4 && width > 6) {
            fill(graphics, x + 1, y + 2, x + 4, y + height - 2, accentColor);
        }
    }

    // Draw the port
    static void drawPort(GuiGraphics graphics, int centerX, int centerY, int col) {
        drawCircle(graphics, centerX, centerY, 5, (col & 0x00FFFFFF) | 0x33000000);
        drawCircle(graphics, centerX, centerY, 4, col);
        drawCircle(graphics, centerX, centerY, 2, mix(col, 0xFFFFFFFF, 0.18D));
    }

    // Draw the exec port
    static void drawExecPort(GuiGraphics graphics, int centerX, int centerY, int size) {
        int radius = Math.max(3, size / 2);
        drawDiamond(graphics, centerX, centerY, radius, PRIMARY);
        drawDiamond(graphics, centerX, centerY, Math.max(1, radius - 2), CANVAS_BACKGROUND);
    }

    // Draw the slider
    static void drawSlider(GuiGraphics graphics, int trackLeft, int centerY,
                           int trackWidth, double amount, int fillColor, boolean active) {
        int fillWidth = (int) Math.round(Mth.clamp(amount, 0.0D, 1.0D) * trackWidth);
        fill(graphics, trackLeft, centerY - 1, trackLeft + trackWidth, centerY + 2, BORDER);
        if (fillWidth > 0) {
            fill(graphics, trackLeft, centerY - 1, trackLeft + fillWidth, centerY + 2, fillColor);
        }
        int thumbX = Mth.clamp(trackLeft + fillWidth, trackLeft, trackLeft + trackWidth);
        drawCircle(graphics, thumbX, centerY, active ? 4 : 3, active ? PRIMARY : fillColor);
    }

    // Draw the browser category
    static void drawBrowserCategory(GuiGraphics graphics, int x, int y, int width, int height,
                                    int col, boolean collapsed, boolean hovered) {
        if (hovered) {
            fill(graphics, x, y, x + width, y + height, withAlpha(PANEL_HOVERED, 0x55));
        }
        if (!collapsed) {
            fill(graphics, x, y, x + 3, y + height, col);
        }
    }

    // Draw the browser node
    static void drawBrowserNode(GuiGraphics graphics, int x, int y, int width, int height,
                                int col, boolean hovered) {
        if (hovered) {
            drawRoundedRect(graphics, x, y, width, height, 3, PANEL_HOVERED);
        }
        fill(graphics, x + 7, y + 5, x + 10, y + height - 5, col);
    }

    // Draw the document node
    static void drawDocumentNode(GuiGraphics graphics, int x, int y, int width, int height,
                                 boolean hovered) {
        int border = hovered ? ACCENT_LIGHT : ACCENT_DARK;
        drawRoundedRect(graphics, x, y, width, height, 4, border);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 3, PANEL_SELECTED);
        fill(graphics, x + 8, y + 6, x + 11, y + height - 6, ACCENT);
    }

    // Draw the handle
    static void drawHandle(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        int fill = hovered ? PANEL_HOVERED : PANEL_RAISED;
        drawRoundedRect(graphics, x, y, width, height, 4, BORDER);
        drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 3, fill);
    }

    // Draw the rounded rect
    static void drawRoundedRect(GuiGraphics graphics, int x, int y, int width, int height,
                                int radius, int col) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Mth.clamp(radius, 0, Math.min(width, height) / 2);
        if (r == 0) {
            fill(graphics, x, y, x + width, y + height, col);
            return;
        }
        fill(graphics, x + r, y, x + width - r, y + height, col);
        fill(graphics, x, y + r, x + width, y + height - r, col);
        for (int offset = 0; offset < r; offset++) {
            int inset = roundedInset(r, offset);
            fill(graphics, x + inset, y + offset, x + width - inset, y + offset + 1, col);
            fill(graphics, x + inset, y + height - offset - 1,
                    x + width - inset, y + height - offset, col);
        }
    }

    // Draw the top rounded rect
    private static void drawTopRoundedRect(GuiGraphics graphics, int x, int y, int width, int height,
                                           int radius, int col) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Mth.clamp(radius, 0, Math.min(width, height));
        fill(graphics, x, y + r, x + width, y + height, col);
        for (int offset = 0; offset < r; offset++) {
            int inset = roundedInset(r, offset);
            fill(graphics, x + inset, y + offset, x + width - inset, y + offset + 1, col);
        }
    }

    // Get the rounded inset
    private static int roundedInset(int radius, int offset) {
        double y = radius - offset - 0.5D;
        return Math.max(0, radius - (int) Math.floor(Math.sqrt(
                Math.max(0.0D, radius * radius - y * y))));
    }

    // Draw the circle
    private static void drawCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int col) {
        int r = Math.max(1, radius);
        for (int offsetY = -r; offsetY <= r; offsetY++) {
            int halfWidth = (int) Math.floor(Math.sqrt(Math.max(0, r * r - offsetY * offsetY)));
            fill(graphics, centerX - halfWidth, centerY + offsetY,
                    centerX + halfWidth + 1, centerY + offsetY + 1, col);
        }
    }

    // Draw the diamond
    private static void drawDiamond(GuiGraphics graphics, int centerX, int centerY, int radius, int col) {
        int r = Math.max(1, radius);
        for (int offsetY = -r; offsetY <= r; offsetY++) {
            int halfWidth = r - Math.abs(offsetY);
            fill(graphics, centerX - halfWidth, centerY + offsetY,
                    centerX + halfWidth + 1, centerY + offsetY + 1, col);
        }
    }

    // Fill the advanced controller V2 theme
    static void fill(GuiGraphics graphics, int left, int top, int right, int bottom, int col) {
        if (right <= left || bottom <= top) {
            return;
        }
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        buffer.addVertex(pose, left, top, 0.0F).setColor(col);
        buffer.addVertex(pose, left, bottom, 0.0F).setColor(col);
        buffer.addVertex(pose, right, bottom, 0.0F).setColor(col);
        buffer.addVertex(pose, right, top, 0.0F).setColor(col);
    }

    // Draw the outline
    static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int col) {
        if (width <= 0 || height <= 0) {
            return;
        }
        fill(graphics, x, y, x + width, y + 1, col);
        fill(graphics, x, y + height - 1, x + width, y + height, col);
        fill(graphics, x, y + 1, x + 1, y + height - 1, col);
        fill(graphics, x + width - 1, y + 1, x + width, y + height - 1, col);
    }

    // Get the mix
    private static int mix(int first, int second, double amount) {
        double clamped = Mth.clamp(amount, 0.0D, 1.0D);
        int a = (int) Math.round(Mth.lerp(clamped, (first >>> 24) & 0xFF, (second >>> 24) & 0xFF));
        int r = (int) Math.round(Mth.lerp(clamped, (first >>> 16) & 0xFF, (second >>> 16) & 0xFF));
        int g = (int) Math.round(Mth.lerp(clamped, (first >>> 8) & 0xFF, (second >>> 8) & 0xFF));
        int b = (int) Math.round(Mth.lerp(clamped, first & 0xFF, second & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }

    // Copy the advanced controller V2 theme with the alpha
    private static int withAlpha(int col, int alpha) {
        return (col & 0x00FFFFFF) | Mth.clamp(alpha, 0, 0xFF) << 24;
    }
}
