package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RcsThrusterMenu;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

// Assign Redstone Link frequencies to each RCS nozzle channel
public class RcsThrusterConfigScreen extends AbstractSimiContainerScreen<RcsThrusterMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 256;
    private static final int HEIGHT = 264;
    private static final int FIRST_FREQUENCY_TINT = 0xAA8A2A32;
    private static final int SECOND_FREQUENCY_TINT = 0xAA2D4A92;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the RCS thruster config
    public RcsThrusterConfigScreen(
            RcsThrusterMenu menu,
            Inventory playerInventory,
            Component title
    ) {
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
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        CTCreateScreenHelper.renderPanel(graphics, x, y, WIDTH, HEIGHT);
        renderPlayerInventory(graphics,
                x + RcsThrusterMenu.PLAYER_SLOTS_X - 8,
                y + RcsThrusterMenu.PLAYER_SLOTS_Y - 18);
        graphics.drawCenteredString(font, title, x + WIDTH / 2, y + 8,
                CTCreateScreenHelper.BANNER_TITLE_COLOR);
        graphics.drawString(font,
                Component.translatable("createthrusters.rcs_thruster.config.frequency_help"),
                x + 18, y + 27, 0xFFB8B8B8, false);

        List<Direction> nozzles = List.of(
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
        for (int row = 0; row < nozzles.size(); row++) {
            Direction nozzle = nozzles.get(row);
            int slotY = y + RcsThrusterMenu.GHOST_SLOTS_START_Y
                    + row * RcsThrusterMenu.GHOST_SLOT_ROW_SPACING;
            int firstX = x + RcsThrusterMenu.GHOST_SLOT_FIRST_X;
            int secondX = x + RcsThrusterMenu.GHOST_SLOT_SECOND_X;
            renderSlotTint(graphics, firstX, slotY, FIRST_FREQUENCY_TINT,
                    inside(mouseX, mouseY, firstX, slotY, 18, 18));
            renderSlotTint(graphics, secondX, slotY, SECOND_FREQUENCY_TINT,
                    inside(mouseX, mouseY, secondX, slotY, 18, 18));
            graphics.drawString(font,
                    Component.translatable("createthrusters.rcs_thruster.nozzle."
                            + nozzle.getSerializedName()),
                    x + 30, slotY + 5, 0xFFE5E5E5, false);
        }
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    // Draw the slot tint
    private static void renderSlotTint(
            GuiGraphics graphics,
            int x,
            int y,
            int col,
            boolean hovered
    ) {
        graphics.fill(x, y, x + 16, y + 16, col);
        if (hovered) {
            graphics.fill(x, y, x + 16, y + 16, 0x24FFFFFF);
        }
    }

    // Check if the point is inside the bounds
    private static boolean inside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }
}
