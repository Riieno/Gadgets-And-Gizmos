package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ClawMenu;
import com.rieno.gadgetsandgizmos.neoforge.network.ClawGhostSlotsPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Edit both Claw filter slots and clear them as one server update
public class ClawConfigScreen extends AbstractSimiContainerScreen<ClawMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int RESET_BUTTON_X = 138;
    private static final int RESET_BUTTON_Y = 117;
    private static final int RESET_BUTTON_SIZE = 18;
    private static final int FIRST_GHOST_SLOT_TINT = 0xAA8A2A32;
    private static final int SECOND_GHOST_SLOT_TINT = 0xAA2D4A92;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the claw config
    public ClawConfigScreen(ClawMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        setWindowSize(WIDTH, HEIGHT);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        CTCreateScreenHelper.renderClawGuiBackground(guiGraphics, x, y);
        drawFittedCenteredString(guiGraphics, title, x + 126, y + 96, 150, 0.8f,
                CTCreateScreenHelper.BANNER_TITLE_COLOR);
        renderClawGhostSlotTint(guiGraphics, x + ClawMenu.GHOST_SLOT_FIRST_X, y + ClawMenu.GHOST_SLOTS_Y,
                FIRST_GHOST_SLOT_TINT,
                inside(mouseX, mouseY, x + ClawMenu.GHOST_SLOT_FIRST_X, y + ClawMenu.GHOST_SLOTS_Y, 18, 18));
        renderClawGhostSlotTint(guiGraphics, x + ClawMenu.GHOST_SLOT_SECOND_X, y + ClawMenu.GHOST_SLOTS_Y,
                SECOND_GHOST_SLOT_TINT,
                inside(mouseX, mouseY, x + ClawMenu.GHOST_SLOT_SECOND_X, y + ClawMenu.GHOST_SLOTS_Y, 18, 18));
        if (inside(mouseX, mouseY, x + RESET_BUTTON_X, y + RESET_BUTTON_Y,
                RESET_BUTTON_SIZE, RESET_BUTTON_SIZE)) {
            guiGraphics.fill(x + RESET_BUTTON_X + 1, y + RESET_BUTTON_Y + 1,
                    x + RESET_BUTTON_X + RESET_BUTTON_SIZE - 1,
                    y + RESET_BUTTON_Y + RESET_BUTTON_SIZE - 1, 0x18FFFFFF);
        }
    }

    // Draw the foreground
    @Override
    protected void renderForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(guiGraphics, mouseX, mouseY, partialTicks);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the claw config
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (btn == 0 && inside(mouseX, mouseY,
                leftPos + RESET_BUTTON_X, topPos + RESET_BUTTON_Y,
                RESET_BUTTON_SIZE, RESET_BUTTON_SIZE)) {
            clearGhostSlots();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    // Clear the ghost slots
    private void clearGhostSlots() {
        menu.ghostInventory.setStackInSlot(0, ItemStack.EMPTY);
        menu.ghostInventory.setStackInSlot(1, ItemStack.EMPTY);
        menu.getSlot(36).setChanged();
        menu.getSlot(37).setChanged();
        PacketDistributor.sendToServer(new ClawGhostSlotsPayload(ItemStack.EMPTY, ItemStack.EMPTY));
    }

    // Draw the claw ghost slot tint
    private static void renderClawGhostSlotTint(GuiGraphics graphics, int x, int y, int tint, boolean hovered) {
        graphics.fill(x, y, x + 16, y + 16, tint);
        if (hovered) {
            graphics.fill(x, y, x + 16, y + 16, 0x24FFFFFF);
        }
    }

    // Draw the fitted centered string
    private void drawFittedCenteredString(GuiGraphics graphics, Component text, int centerX, int y,
                                          int maxWidth, float maxScale, int col) {
        int textWidth = font.width(text);
        float scale = textWidth <= 0 ? maxScale : Math.max(0.55f, Math.min(maxScale, maxWidth / (float) textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawCenteredString(font, text, 0, 0, col);
        graphics.pose().popPose();
    }

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
