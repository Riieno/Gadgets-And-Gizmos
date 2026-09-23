package com.rieno.gadgetsandgizmos.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Owns the temporary GUI-scale override shared by every ACC editor screen.
 *
 * <p>NeoForge may remove the current screen before it installs the next GUI
 * layer. Restoring from an individual screen at that point races an ACC child
 * screen (or its parent) which is about to become active. Keeping one session
 * value and restoring on the next client task avoids that race and preserves
 * the player's original {@code Auto} (0) or numeric option exactly.</p>
 */
final class AccGuiScaleOverride {
    private static final int ACC_GUI_SCALE = 2;

    private static Integer playerGuiScale;
    private static boolean restoreQueued;

    private AccGuiScaleOverride() {
    }

    static boolean apply() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        restoreQueued = false;
        if (playerGuiScale == null) {
            playerGuiScale = minecraft.options.guiScale().get();
        }
        if (minecraft.options.guiScale().get() != ACC_GUI_SCALE) {
            minecraft.options.guiScale().set(ACC_GUI_SCALE);
            minecraft.resizeDisplay();
            return true;
        }
        return false;
    }

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

    private static boolean isAccEditor(Screen screen) {
        return screen instanceof AdvancedContraptionControllerScreen
                || screen instanceof FunctionPlotterScreen;
    }
}
