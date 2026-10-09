package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.ui.GuiButtonPainter;
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
    private final GuiButtonPainter painter;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT scaled button
    CTScaledButton(CTScalableGui scalableGui, int x, int y, int width, int height,
                   Component msg, OnPress onPress) {
        this(scalableGui, x, y, width, height, msg, onPress, null);
    }

    // Initialize a scaled button with a custom painter
    CTScaledButton(CTScalableGui scalableGui, int x, int y, int width, int height,
                   Component msg, OnPress onPress, GuiButtonPainter painter){
        super(x, y, width, height, msg, onPress, DEFAULT_NARRATION);
        this.scalableGui = scalableGui;
        this.painter = painter;
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
            isHovered = isMouseOver(mouseX, mouseY);
            if(painter == null){
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }else{
                painter.draw(graphics, this, isHovered, partialTick);
            }
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
