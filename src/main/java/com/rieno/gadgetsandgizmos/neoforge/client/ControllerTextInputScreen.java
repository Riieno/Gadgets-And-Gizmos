package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import com.rieno.gadgetsandgizmos.lib.client.ui.TextInputScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.rieno.gadgetsandgizmos.CreateThrusters;

import java.util.function.Consumer;

// Collect one bounded text value before returning it to the controller screen
public class ControllerTextInputScreen extends TextInputScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PANEL_W = 190;
    private static final int PANEL_H = 87;
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/text_input.png");

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Title
    private final Component title;
    // Prompt
    private final Component prompt;
    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the controller text input
    public ControllerTextInputScreen(Screen parent,
                                     Component title,
                                     Component prompt,
                                     String initialText,
                                     Consumer<String> onConfirm) {
        this(parent, title, prompt, initialText, onConfirm, 64);
    }

    // Initialize the controller text input
    public ControllerTextInputScreen(Screen parent,
                                     Component title,
                                     Component prompt,
                                     String initialText,
                                     Consumer<String> onConfirm,
                                     int maximumLength) {
        super(parent, title, initialText, onConfirm, maximumLength);
        this.title = title;
        this.prompt = prompt;
    }

    // Initialize the controller text input
    @Override
    protected void init() {
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.update(x, y, PANEL_W, PANEL_H, width, height);

        CTScaledEditBox input = new CTScaledEditBox(scalableGui, font,
                x + 28, y + 42, 134, 10, prompt);
        input.setBordered(false);
        initializeInput(input);

        addRenderableWidget(new CTScaledButton(scalableGui, x + 6, y + 72, 50, 14,
                Component.literal("Cancel"), btn -> onClose()));

        addRenderableWidget(new CTScaledButton(scalableGui, x + PANEL_W - 56, y + 72, 50, 14,
                Component.literal("Save"), btn -> confirm()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the controller text input
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.update(x, y, PANEL_W, PANEL_H, width, height);
        scalableGui.push(guiGraphics);
        try {
            guiGraphics.blit(BACKGROUND, x, y, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
            guiGraphics.drawCenteredString(font, font.plainSubstrByWidth(title.getString(), PANEL_W - 16),
                    x + PANEL_W / 2, y + 3, 0xFF29231A);
            guiGraphics.drawString(font, font.plainSubstrByWidth(prompt.getString(), PANEL_W - 16),
                    x + 8, y + 25, CTCreateScreenHelper.LABEL_COLOR, false);
        } finally {
            scalableGui.pop(guiGraphics);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        return super.mouseClicked(mouseX, mouseY, btn);
    }

}
