package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// Draw the live joystick axes and button state while it is being configured
public final class AnalogueJoystickHudOverlay {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PANEL_BG = 0xCC101820;
    private static final int PANEL_BORDER = 0xFF2F4A60;
    private static final int TEXT = 0xFFE9F0F5;
    private static final int SUBTLE = 0xFFA9BBC8;
    private static final int GRAPH_BG = 0xFF182531;
    private static final int GRAPH_GRID = 0xFF33485A;
    private static final int GRAPH_AXIS = 0xFF6B8CA6;
    private static final int GRAPH_POINT = 0xFFFFA94D;
    private static final int BAR_BG = 0xFF1A2632;
    private static final int BAR_FILL = 0xFF51C4D3;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue joystick HUD overlay
    private AnalogueJoystickHudOverlay() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render gui event
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        AnalogueJoystickClientHandler.DragHudState state = AnalogueJoystickClientHandler.getDragHudState();
        if (state == null) {
            return;
        }

        render(evt.getGuiGraphics(), minecraft.font, state);
    }

    // Draw the analogue joystick HUD overlay
    private static void render(GuiGraphics graphics, Font font, AnalogueJoystickClientHandler.DragHudState state) {
        int panelWidth = 188;
        int panelHeight = 136;
        int x = graphics.guiWidth() - panelWidth - 12;
        int y = graphics.guiHeight() - panelHeight - 12;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);
        graphics.fill(x, y, x + panelWidth, y + 1, PANEL_BORDER);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + panelHeight, PANEL_BORDER);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, PANEL_BORDER);

        graphics.drawString(font, Component.literal("Analogue Joystick"), x + 10, y + 8, TEXT, false);
        graphics.drawString(font, Component.literal("Live Input"), x + 118, y + 8, SUBTLE, false);

        int graphX = x + 10;
        int graphY = y + 24;
        int graphSize = 96;
        renderGraph(graphics, graphX, graphY, graphSize, state);

        int barsX = x + 116;
        int barWidth = 56;
        int barHeight = 10;
        int row = graphY;
        row = renderSignalBar(graphics, font, barsX, row, barWidth, barHeight, "F", state.forwardRedstone());
        row = renderSignalBar(graphics, font, barsX, row + 4, barWidth, barHeight, "B", state.backwardRedstone());
        row = renderSignalBar(graphics, font, barsX, row + 4, barWidth, barHeight, "L", state.leftRedstone());
        renderSignalBar(graphics, font, barsX, row + 4, barWidth, barHeight, "R", state.rightRedstone());

        String vectorText = String.format("x %.2f  z %.2f", state.localX(), state.localZ());
        graphics.drawString(font, Component.literal(vectorText), x + 10, y + panelHeight - 14, SUBTLE, false);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the graph
    private static void renderGraph(GuiGraphics graphics, int x, int y, int size,
                                    AnalogueJoystickClientHandler.DragHudState state) {
        graphics.fill(x, y, x + size, y + size, GRAPH_BG);

        int centerX = x + size / 2;
        int centerY = y + size / 2;

        int quarter = size / 4;
        graphics.fill(x + quarter, y, x + quarter + 1, y + size, GRAPH_GRID);
        graphics.fill(x + quarter * 3, y, x + quarter * 3 + 1, y + size, GRAPH_GRID);
        graphics.fill(x, y + quarter, x + size, y + quarter + 1, GRAPH_GRID);
        graphics.fill(x, y + quarter * 3, x + size, y + quarter * 3 + 1, GRAPH_GRID);

        graphics.fill(centerX, y, centerX + 1, y + size, GRAPH_AXIS);
        graphics.fill(x, centerY, x + size, centerY + 1, GRAPH_AXIS);

        int radius = size / 2 - 5;
        int pointX = centerX - Math.round(state.localX() * radius);
        int pointY = centerY + Math.round(-state.localZ() * radius);
        graphics.fill(pointX - 2, pointY - 2, pointX + 3, pointY + 3, GRAPH_POINT);

        graphics.fill(x, y, x + size, y + 1, PANEL_BORDER);
        graphics.fill(x, y + size - 1, x + size, y + size, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + size, PANEL_BORDER);
        graphics.fill(x + size - 1, y, x + size, y + size, PANEL_BORDER);
    }

    // Draw the signal bar
    private static int renderSignalBar(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                       String label, int val) {
        graphics.drawString(font, Component.literal(label), x, y + 1, SUBTLE, false);

        int barX = x + 12;
        int fillWidth = Math.round((val / 15.0f) * width);
        graphics.fill(barX, y, barX + width, y + height, BAR_BG);
        graphics.fill(barX, y, barX + fillWidth, y + height, BAR_FILL);

        graphics.drawString(font, Component.literal(Integer.toString(val)), barX + width + 4, y + 1, TEXT, false);
        return y + height;
    }
}
