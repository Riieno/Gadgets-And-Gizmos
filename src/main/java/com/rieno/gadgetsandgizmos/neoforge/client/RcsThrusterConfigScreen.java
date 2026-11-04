package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.RcsThrusterMenu;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

// Assign Redstone Link frequencies to each RCS nozzle channel
public class RcsThrusterConfigScreen extends AbstractSimiContainerScreen<RcsThrusterMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 182;
    private static final int HEIGHT = 269;
    private static final int CONFIRM_X = 160;
    private static final int CONFIRM_Y = 149;
    private static final int CONFIRM_SIZE = 18;
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/rcs_thruster.png"), WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT);

    // Keep the painted inventory and its interactions together on smaller windows
    private final CTScalableGui scalableGui = new CTScalableGui();

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

    // Fit the complete painted screen and inventory inside the viewport
    @Override
    protected void init(){
        super.init();
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY){
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(graphics);
        try{
            int x = leftPos;
            int y = topPos;
            BACKGROUND.draw(graphics, x, y);
            GuiControlPreviews.drawFittedText(graphics, font, title, x + 8, y + 1,
                    WIDTH - 16, 11, 1, 0xFFF3EEE4, true);
            if(inside(mouseX, mouseY, x + CONFIRM_X, y + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                graphics.fill(x + CONFIRM_X, y + CONFIRM_Y, x + CONFIRM_X + CONFIRM_SIZE,
                        y + CONFIRM_Y + CONFIRM_SIZE, 0x22FFFFFF);
            }
            for(Direction nozzle : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)){
                for(int idx = 0; idx < 2; idx++){
                    int slotX = x + RcsThrusterMenu.frequencySlotX(nozzle, idx == 1);
                    int slotY = y + RcsThrusterMenu.frequencySlotY(nozzle, idx == 1);
                    if(!inside(mouseX, mouseY, slotX - 4, slotY - 4, 24, 24)) continue;
                    GuiControlPreviews.drawFittedText(graphics, font,
                            Component.translatable("createthrusters.rcs_thruster.nozzle." + nozzle.getSerializedName()),
                            x + 8, y + 152, 144, 10, 1, 0xFF303030, false);
                }
            }
            if(inside(mouseX, mouseY, x + 64, y + 54, 50, 54)){
                graphics.renderTooltip(font, Component.translatable("createthrusters.rcs_thruster.config.frequency_help"), mouseX, mouseY);
            }
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Close through the painted confirmation button while slot clicks use the same scale
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn){
        if(btn == 0 && inside(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY),
                leftPos + CONFIRM_X, topPos + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Reserve the transformed screen area beside recipe viewers
    @Override
    public List<Rect2i> getExtraAreas(){ return scalableGui.extraAreas(); }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    // Draw inventory items over their painted slots after scaling
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot){
        scalableGui.pushFromOrigin(graphics, leftPos, topPos);
        try{
            super.renderSlot(graphics, slot);
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Keep slot highlights aligned with their items
    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick){
        scalableGui.pushFromOrigin(graphics, leftPos, topPos);
        try{
            super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Resolve slot hit areas in the same coordinates as the background
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY){
        return super.isHovering(x, y, width, height, scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
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
