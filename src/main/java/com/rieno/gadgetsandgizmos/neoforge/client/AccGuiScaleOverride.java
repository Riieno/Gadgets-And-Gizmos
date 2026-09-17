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
    static boolean apply() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        restoreQueued = false;
        if (playerGuiScale == null) {
            playerGuiScale = minecraft.options.guiScale().get();
        }
        if (minecraft.options.guiScale().get() == ACC_GUI_SCALE) return false;
        minecraft.options.guiScale().set(ACC_GUI_SCALE);
        minecraft.resizeDisplay();
        return true;
    }

    // Restore after Minecraft has installed the next screen
    static void restoreAfterExit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || playerGuiScale == null || restoreQueued) return;
        restoreQueued = true;
        minecraft.tell(() -> {
            restoreQueued = false;
            if (playerGuiScale == null || isAccEditor(minecraft.screen)) return;
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
