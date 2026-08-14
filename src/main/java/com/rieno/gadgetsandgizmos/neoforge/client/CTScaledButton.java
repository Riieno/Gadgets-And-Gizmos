package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

// Scale a Create styled button
final class CTScaledButton extends Button {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scalable GUI
    private final CTScalableGui scalableGui;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT scaled button
    CTScaledButton(CTScalableGui scalableGui, int x, int y, int width, int height,
                   Component msg, OnPress onPress) {
        super(x, y, width, height, msg, onPress, DEFAULT_NARRATION);
        this.scalableGui = scalableGui;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the widget
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        scalableGui.push(graphics);
        try {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        } finally {
            scalableGui.pop(graphics);
        }
    }

    // Check if this is mouse over
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
    }

    // Check if the pointer is inside the control
    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return super.clicked(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
    }
}
