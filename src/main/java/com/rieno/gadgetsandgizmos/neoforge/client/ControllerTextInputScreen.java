package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

// Collect one bounded text value before returning it to the controller screen
public class ControllerTextInputScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PANEL_W = 288;
    private static final int PANEL_H = 96;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Parent controller text input
    private final Screen parent;
    // Title
    private final Component title;
    // Prompt
    private final Component prompt;
    // Initial text
    private final String initialText;
    // On confirm
    private final Consumer<String> onConfirm;
    // Maximum length
    private final int maximumLength;
    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    // Current input
    private EditBox input;

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
        super(title);
        this.parent = parent;
        this.title = title;
        this.prompt = prompt;
        this.initialText = initialText == null ? "" : initialText;
        this.onConfirm = onConfirm;
        this.maximumLength = Math.max(1, maximumLength);
    }

    // Initialize the controller text input
    @Override
    protected void init() {
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.update(x, y, PANEL_W, PANEL_H, width, height);

        input = new CTScaledEditBox(scalableGui, font, x + 12, y + 34, PANEL_W - 24, 18, Component.literal("value"));
        input.setValue(initialText);
        input.setMaxLength(maximumLength);
        input.setResponder(val -> {
            if (val != null && val.length() > maximumLength) {
                input.setValue(val.substring(0, maximumLength));
            }
        });
        addRenderableWidget(input);
        setInitialFocus(input);

        addRenderableWidget(new CTScaledButton(scalableGui, x + 12, y + PANEL_H - 24, 62, 18,
                Component.literal("Cancel"), btn -> onClose()));

        addRenderableWidget(new CTScaledButton(scalableGui, x + PANEL_W - 74, y + PANEL_H - 24, 62, 18,
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
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.update(x, y, PANEL_W, PANEL_H, width, height);
        scalableGui.push(guiGraphics);
        try {
            CTCreateScreenHelper.renderPanel(guiGraphics, x, y, PANEL_W, PANEL_H);
            guiGraphics.drawCenteredString(font, title, x + PANEL_W / 2, y + 8, CTCreateScreenHelper.BANNER_TITLE_COLOR);
            guiGraphics.drawString(font, prompt, x + 12, y + 24, CTCreateScreenHelper.LABEL_COLOR, false);
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

    // Handle key pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Confirm the text input
    private void confirm() {
        if (onConfirm != null) {
            onConfirm.accept(input == null ? "" : input.getValue().trim());
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // Handle the close event
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
