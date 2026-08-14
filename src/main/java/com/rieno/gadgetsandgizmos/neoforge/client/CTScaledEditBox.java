package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

// Scale a Create styled edit box
final class CTScaledEditBox extends EditBox {
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

    // Initialize the CT scaled edit box
    CTScaledEditBox(CTScalableGui scalableGui, Font font, int x, int y, int width, int height, Component msg) {
        super(font, x, y, width, height, msg);
        this.scalableGui = scalableGui;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the widget
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the click event
    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
    }
}
