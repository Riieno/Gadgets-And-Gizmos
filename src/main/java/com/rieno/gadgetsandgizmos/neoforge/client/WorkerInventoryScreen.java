package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.WorkerInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

// Display worker storage using the standard vanilla shulker container layout.
public class WorkerInventoryScreen extends AbstractContainerScreen<WorkerInventoryMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation SHULKER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");
    private static final int WINDOW_WIDTH = 284;
    private static final int WINDOW_HEIGHT = 166;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the worker inventory screen.
    public WorkerInventoryScreen(WorkerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WINDOW_WIDTH;
        imageHeight = WINDOW_HEIGHT;
        inventoryLabelY = 72;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(SHULKER_TEXTURE, x, y, 0, 0, 176, 166);
        graphics.fill(x + 181, y + 5, x + 281, y + 96, 0xFF8E729C);
        graphics.renderOutline(x + 181, y + 5, 100, 91, 0xFF4C3A55);
        for (int slot = 0; slot < WorkerInventoryMenu.ARMOR_SLOT_COUNT; slot++) {
            drawSideSlot(graphics, x + 186, y + 17 + slot * 18);
        }
        if (menu.curiosAvailable()) {
            for (int slot = 0; slot < WorkerInventoryMenu.CURIO_SLOT_COUNT; slot++) {
                drawSideSlot(graphics, x + 223 + slot % 3 * 18, y + 17 + slot / 3 * 18);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFF404040, false);
        graphics.drawString(font, Component.literal("Armor"), 184, 6, 0xFF404040, false);
        if (menu.curiosAvailable()) graphics.drawString(font, Component.literal("Curios"), 220, 6, 0xFF404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFF404040, false);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Helpers
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw one worker equipment or Curios slot beside the vanilla shulker body.
    private static void drawSideSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFFB69DC1);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF5B4567);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF8E729C);
    }
}
