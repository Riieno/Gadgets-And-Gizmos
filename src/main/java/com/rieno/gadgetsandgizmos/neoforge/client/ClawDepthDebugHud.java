package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.ClawMarkerRenderMode;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Collection;

// Show the claw's grab depth and collision probes while debug mode is enabled
public final class ClawDepthDebugHud {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether claw depth debug HUD is enabled
    private static boolean enabled = false;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw depth debug HUD
    private ClawDepthDebugHud() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Toggle the claw depth debug HUD
    public static void toggleDebug() {
        enabled = !enabled;
    }

    // Check if this is enabled
    public static boolean isEnabled() {
        return enabled;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the render screen event
    public static void onRenderScreen(ScreenEvent.Init evt) {
        if (!enabled) {
            return;
        }
    }

    // Handle the render gui layer event
    public static void onRenderGuiLayer(ScreenEvent.Render.Post evt) {
        if (!enabled) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        GuiGraphics guiGraphics = evt.getGuiGraphics();
        int y = 10;
        int x = 10;

        ClawMarkerRenderMode mode = CTConfigs.CLIENT.clawMarkerRenderMode.get();
        drawString(guiGraphics, "Marker Mode: " + mode.name(), x, y);
        y += 12;

        drawString(guiGraphics, "Render Hits: " + ClawDepthHighlightRenderer.getCallbackHits(), x, y);
        y += 12;
        drawString(guiGraphics, "Draw Attempts: " + ClawDepthHighlightRenderer.getDrawAttempts() + " Probe: " + ClawDepthHighlightRenderer.getProbeDraws(), x, y);
        y += 12;
        drawString(guiGraphics, "Last Status: " + ClawDepthHighlightRenderer.getLastStatus(), x, y);
        y += 12;
        drawString(guiGraphics, "Renderer Markers: " + ClawDepthHighlightRenderer.getLastMarkerCount(), x, y);
        y += 12;

        Collection<ClawMarkerRenderState.MarkerEntry> markers = ClawMarkerRenderState.getActiveMarkers();
        drawString(guiGraphics, "Active Markers: " + markers.size(), x, y);
        y += 12;

        for (ClawMarkerRenderState.MarkerEntry marker : markers) {
            drawString(guiGraphics, String.format("  Pos: %.1f, %.1f, %.1f",
                    marker.worldCenter.x, marker.worldCenter.y, marker.worldCenter.z), x, y);
            y += 10;
            drawString(guiGraphics, String.format("  Radius: %.2f Signal: %.2f",
                    marker.radius, marker.signalFactor), x, y);
            y += 10;
        }
    }

    // Draw the string
    private static void drawString(GuiGraphics guiGraphics, String text, int x, int y) {
        guiGraphics.drawString(Minecraft.getInstance().font, text, x, y, 0x00FF00);
    }
}
