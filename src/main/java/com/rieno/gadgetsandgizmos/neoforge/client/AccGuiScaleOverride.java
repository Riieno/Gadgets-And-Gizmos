package com.rieno.gadgetsandgizmos.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

// Own the temporary GUI-scale override shared by ACC editor screens
final class AccGuiScaleOverride {
    private static final int ACC_GUI_SCALE = 2;

    private static Integer playerGuiScale;
    private static boolean restoreQueued;

    private AccGuiScaleOverride() {
    }

    // Apply GUI scale two and rebuild the active screen at the new dimensions
    static boolean apply(){ return apply(0); }

    static boolean apply(int minimumWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        int scale = com.rieno.gadgetsandgizmos.lib.client.GuiViewportScale.forWidth(
                ACC_GUI_SCALE, minecraft.getWindow().getWidth(), minimumWidth);
        restoreQueued = false;
        if (playerGuiScale == null) {
            playerGuiScale = minecraft.options.guiScale().get();
        }
        if (minecraft.options.guiScale().get() != scale) {
            minecraft.options.guiScale().set(scale);
            minecraft.resizeDisplay();
            return true;
        }
        return false;
    }

    // Restore after Minecraft has installed the next screen
    static void restoreAfterExit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || playerGuiScale == null || restoreQueued) {
            return;
        }
        restoreQueued = true;
        // execute(...) runs inline when this is already the client thread,
        // which is exactly where Screen#removed is called. Queue the work
        // instead so Minecraft/NeoForge first installs the replacement screen
        // (or finishes popping the final GUI layer).
        minecraft.tell(() -> {
            restoreQueued = false;
            if (playerGuiScale == null || isAccEditor(minecraft.screen)) {
                return;
            }
            int scale = playerGuiScale;
            playerGuiScale = null;
            if (minecraft.options.guiScale().get() != scale) {
                minecraft.options.guiScale().set(scale);
                minecraft.resizeDisplay();
            }
        });
    }

    // Check whether an ACC editor layer is still active
    private static boolean isAccEditor(Screen screen) {
        return screen instanceof AdvancedContraptionControllerScreen
                || screen instanceof FunctionPlotterScreen;
    }


}
