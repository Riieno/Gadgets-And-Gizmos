package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.NavigationTableMenu;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableExtensionAccess;
import com.rieno.gadgetsandgizmos.content.navigation.NavigationTableMapResolver;
import com.rieno.gadgetsandgizmos.neoforge.network.NavigationTableActionPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.client.renderer.Rect2i;

import java.util.List;
import java.util.Locale;

// Edit Navigation Table settings
public class NavigationTableScreen extends AbstractSimiContainerScreen<NavigationTableMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 280;
    private static final int HEIGHT = 260;
    private static final int CHROME_SHIFT_UP = 26;

    private static final int MAP_GRID_COLS = 5;
    private static final int MAP_GRID_SLOT = 18;
    private static final int LIST_AREA_X = 106;
    private static final int LIST_AREA_Y = 32;
    private static final int LIST_AREA_W = 168;
    private static final int LIST_AREA_H = 180;
    private static final int MAP_ITEM_H = 18;

    private static final int STATUS_X = 106;
    private static final int STATUS_Y = 216;
    private static final int STATUS_W = 168;

    private static final int BUTTON_WIDTH = 52;
    private static final int BUTTON_HEIGHT = 16;
    private static final int START_X = 106;
    private static final int PAUSE_X = 162;
    private static final int STOP_X = 218;
    private static final int BUTTON_Y = 238;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current map list scroll
    private int mapListScroll = 0;

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the navigation table
    public NavigationTableScreen(NavigationTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the navigation table
    @Override
    protected void init() {
        super.init();

        topPos = Math.max(4, topPos - CHROME_SHIFT_UP);
        updateScalableGuiBounds();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the scalable gui bounds
    private void updateScalableGuiBounds() {
        int inventoryBottom = NavigationTableMenu.PLAYER_SLOTS_Y - 18 + CTScalableGui.PLAYER_INVENTORY_HEIGHT;
        scalableGui.update(leftPos, topPos, imageWidth, Math.max(imageHeight, inventoryBottom), width, height);
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        updateScalableGuiBounds();
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
        int x = leftPos;
        int y = topPos;

        CTCreateScreenHelper.renderPanel(guiGraphics, x, y, imageWidth, imageHeight);
        guiGraphics.drawCenteredString(font, title, x + imageWidth / 2, y + 6, CTCreateScreenHelper.BANNER_TITLE_COLOR);
        CTCreateScreenHelper.renderSeparator(guiGraphics, x + 6, y + 22, imageWidth - 12);

        renderTableMapGrid(guiGraphics, x, y, mouseX, mouseY);
        renderPlayerInventory(guiGraphics,
            x + NavigationTableMenu.PLAYER_SLOTS_X - 8,
            y + NavigationTableMenu.PLAYER_SLOTS_Y - 18);

        CTCreateScreenHelper.renderInset(guiGraphics, x + LIST_AREA_X, y + LIST_AREA_Y, LIST_AREA_W, LIST_AREA_H, false, false);
        renderMapListArea(guiGraphics, x, y, mouseX, mouseY);

        CTCreateScreenHelper.renderInset(guiGraphics, x + STATUS_X, y + STATUS_Y, STATUS_W, 18, false, false);
        renderStatusArea(guiGraphics, x, y);

        renderActionButton(guiGraphics, START_X, BUTTON_Y,
                Component.translatable("createthrusters.navigation_table.button.start"),
                mouseX, mouseY, menu.getRunState() == NavigationTableExtensionAccess.RunState.RUNNING);
        renderActionButton(guiGraphics, PAUSE_X, BUTTON_Y,
                Component.translatable("createthrusters.navigation_table.button.pause"),
                mouseX, mouseY, menu.getRunState() == NavigationTableExtensionAccess.RunState.PAUSED);
        renderActionButton(guiGraphics, STOP_X, BUTTON_Y,
                Component.translatable("createthrusters.navigation_table.button.stop"),
                mouseX, mouseY, menu.getRunState() == NavigationTableExtensionAccess.RunState.IDLE);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the table map grid
    private void renderTableMapGrid(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int gridX = baseX + NavigationTableMenu.MAP_GRID_X;
        int gridY = baseY + NavigationTableMenu.MAP_GRID_Y;
        int selectedSlot = menu.getSelectedSlot();

        if (menu.getContentHolder() == null) {
            return;
        }

        for (int slot = 0; slot < NavigationTableExtensionAccess.SLOT_COUNT; slot++) {
            int row = slot / MAP_GRID_COLS;
            int col = slot % MAP_GRID_COLS;
            int slotX = gridX + 2 + col * MAP_GRID_SLOT;
            int slotY = gridY + 2 + row * MAP_GRID_SLOT;
            boolean hovered = mouseX >= slotX && mouseX < slotX + MAP_GRID_SLOT
                    && mouseY >= slotY && mouseY < slotY + MAP_GRID_SLOT;
            boolean selected = slot == selectedSlot;

            CTCreateScreenHelper.renderItemSlot(guiGraphics, slotX, slotY);
            if (selected) {
                guiGraphics.fill(slotX, slotY, slotX + MAP_GRID_SLOT, slotY + 1, 0xFFD8B35E);
                guiGraphics.fill(slotX, slotY + MAP_GRID_SLOT - 1, slotX + MAP_GRID_SLOT, slotY + MAP_GRID_SLOT, 0xFFD8B35E);
                guiGraphics.fill(slotX, slotY, slotX + 1, slotY + MAP_GRID_SLOT, 0xFFD8B35E);
                guiGraphics.fill(slotX + MAP_GRID_SLOT - 1, slotY, slotX + MAP_GRID_SLOT, slotY + MAP_GRID_SLOT, 0xFFD8B35E);
            } else if (hovered) {
                guiGraphics.fill(slotX, slotY, slotX + MAP_GRID_SLOT, slotY + MAP_GRID_SLOT, 0x22FFFFFF);
            }

            var map = menu.getContentHolder().ct$getMapInSlot(slot);
            if (map.isEmpty()) {
                guiGraphics.drawString(font, Component.literal(String.valueOf(slot + 1)),
                        slotX + 6, slotY + 5, CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            }
        }
    }

    // Draw the map list area
    private void renderMapListArea(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY) {
        int x = baseX + LIST_AREA_X;
        int y = baseY + LIST_AREA_Y;
        int mapCount = NavigationTableExtensionAccess.SLOT_COUNT;
        int selectedSlot = menu.getSelectedSlot();
        int listHeight = LIST_AREA_H / MAP_ITEM_H;
        int maxScroll = Math.max(0, mapCount - listHeight);
        mapListScroll = Mth.clamp(mapListScroll, 0, maxScroll);

        int sx1 = scalableGui.toViewportX(x + 2);
        int sy1 = scalableGui.toViewportY(y + 2);
        int sx2 = scalableGui.toViewportX(x + LIST_AREA_W - 4);
        int sy2 = scalableGui.toViewportY(y + LIST_AREA_H - 2);
        guiGraphics.enableScissor(sx1, sy1, sx2, sy2);

        for (int i = 0; i < mapCount; i++) {
            int itemY = y + 2 + (i - mapListScroll) * MAP_ITEM_H;
            if (itemY + MAP_ITEM_H < y + 2 || itemY > y + LIST_AREA_H - 2) {
                continue;
            }

            boolean isSelected = i == selectedSlot;
            boolean isHovered = mouseX >= x + 2 && mouseX < x + LIST_AREA_W - 2 &&
                    mouseY >= itemY && mouseY < itemY + MAP_ITEM_H;

            CTCreateScreenHelper.renderTextButton(guiGraphics, font, x + 2, itemY, LIST_AREA_W - 4, MAP_ITEM_H,
                    Component.literal(""), isHovered, true, isSelected);

            if (menu.getContentHolder() != null) {
                var map = menu.getContentHolder().ct$getMapInSlot(i);
                if (!map.isEmpty()) {
                    NavigationTableMapResolver.ResolvedTarget target = menu.getContentHolder().ct$getResolvedTarget(i);
                    String label = targetLabel(target, i, map);
                    String coords = targetCoords(target);
                    guiGraphics.renderItem(map, x + 4, itemY + 2);
                    int rowRight = x + LIST_AREA_W - 8;
                    int coordsWidth = coords.isEmpty() ? 0 : font.width(coords);
                    int labelMaxWidth = Math.max(8, rowRight - (x + 22) - coordsWidth - (coords.isEmpty() ? 0 : 8));
                    guiGraphics.drawString(font, Component.literal(trimToWidth(label, labelMaxWidth)), x + 22, itemY + 5,
                        0xFFFFFF, false);
                    if (!coords.isEmpty()) {

                    guiGraphics.drawString(font, Component.literal(coords), rowRight - coordsWidth, itemY + 5,
                        CTCreateScreenHelper.VALUE_COLOR, false);
                    }
                } else {
                    guiGraphics.drawString(font, Component.literal("Slot " + (i + 1)), x + 6, itemY + 5,
                            CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
                }
            } else {
                guiGraphics.drawString(font, Component.literal("Slot " + (i + 1)), x + 6, itemY + 5,
                        CTCreateScreenHelper.SUBTLE_TEXT_COLOR, false);
            }
        }

        guiGraphics.disableScissor();
    }

    // Draw the status area
    private void renderStatusArea(GuiGraphics guiGraphics, int x, int y) {
        String selectedText = Component.translatable("createthrusters.navigation_table.label.selected_slot",
                menu.getSelectedSlot() + 1).getString();
        String stateText = Component.translatable("createthrusters.navigation_table.label.state",
                menu.getRunState().name().toLowerCase(Locale.ROOT)).getString();

        guiGraphics.drawString(font, selectedText, x + STATUS_X + 4, y + STATUS_Y + 3,
                CTCreateScreenHelper.VALUE_COLOR, false);
        guiGraphics.drawString(font, stateText, x + STATUS_X + 90, y + STATUS_Y + 3,
                CTCreateScreenHelper.LABEL_COLOR, false);
    }

    // Draw the action button
    private void renderActionButton(GuiGraphics guiGraphics, int relativeX, int relativeY, Component text,
                                    int mouseX, int mouseY, boolean active) {
        int buttonX = leftPos + relativeX;
        int buttonY = topPos + relativeY;
        CTCreateScreenHelper.renderTextButton(guiGraphics, font, buttonX, buttonY,
                BUTTON_WIDTH, BUTTON_HEIGHT, text,
                inside(mouseX, mouseY, relativeX, relativeY, BUTTON_WIDTH, BUTTON_HEIGHT), true, active);
    }

    // Check if this is inside
    private boolean inside(double mouseX, double mouseY, int relativeX, int relativeY, int width, int height) {
        int x = leftPos + relativeX;
        int y = topPos + relativeY;
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    // Draw the slot
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        scalableGui.pushFromOrigin(guiGraphics, leftPos, topPos);
        try {
            super.renderSlot(guiGraphics, slot);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the slot highlight
    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        scalableGui.pushFromOrigin(guiGraphics, leftPos, topPos);
        try {
            super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Check if this is hovering
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return super.isHovering(x, y, width, height, scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the navigation table
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // Get the extra areas
    @Override
    public List<Rect2i> getExtraAreas() {
        return scalableGui.extraAreas();
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        int x = leftPos + LIST_AREA_X;
        int y = topPos + LIST_AREA_Y;
        if (mouseX >= x && mouseX < x + LIST_AREA_W && mouseY >= y && mouseY < y + LIST_AREA_H) {
            mapListScroll -= (int) scrollY;
            return true;
        }
        return super.mouseScrolled(screenMouseX, screenMouseY, scrollX, scrollY);
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn == 0) {

            int x = leftPos + LIST_AREA_X;
            int y = topPos + LIST_AREA_Y;
            if (mouseX >= x + 2 && mouseX < x + LIST_AREA_W - 2 &&
                    mouseY >= y + 2 && mouseY < y + LIST_AREA_H - 2) {
                int listHeight = LIST_AREA_H / MAP_ITEM_H;
                int mapCount = NavigationTableExtensionAccess.SLOT_COUNT;
                int maxScroll = Math.max(0, mapCount - listHeight);
                mapListScroll = Mth.clamp(mapListScroll, 0, maxScroll);

                int relativeY = (int) (mouseY - y - 2);
                int slotIndex = mapListScroll + relativeY / MAP_ITEM_H;
                if (slotIndex >= 0 && slotIndex < NavigationTableExtensionAccess.SLOT_COUNT) {
                    sendAction(NavigationTableActionPayload.Action.SELECT_SLOT, slotIndex);
                    return true;
                }
            }

            if (inside(mouseX, mouseY, START_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                sendAction(NavigationTableActionPayload.Action.START, menu.getSelectedSlot());
                return true;
            }
            if (inside(mouseX, mouseY, PAUSE_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                sendAction(NavigationTableActionPayload.Action.PAUSE, menu.getSelectedSlot());
                return true;
            }
            if (inside(mouseX, mouseY, STOP_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                sendAction(NavigationTableActionPayload.Action.STOP, menu.getSelectedSlot());
                return true;
            }
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Get the target label
    private String targetLabel(NavigationTableMapResolver.ResolvedTarget target, int slotIndex, net.minecraft.world.item.ItemStack map) {
        if (target == null) {
            return map.getHoverName().getString();
        }
        if (target.label() != null && !target.label().isBlank()) {
            return target.label();
        }

        return map.getHoverName().getString();
    }

    // Get the target coords
    private String targetCoords(NavigationTableMapResolver.ResolvedTarget target) {
        if (target == null) {
            return "";
        }
        int x = Mth.floor(target.targetPos().x);
        int y = Mth.floor(target.targetPos().y);
        int z = Mth.floor(target.targetPos().z);
        return x + "," + y + "," + z;
    }

    // Trim the width
    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        int length = text.length();
        while (length > 0 && font.width(text.substring(0, length)) + ellipsisWidth > maxWidth) {
            length--;
        }
        return length <= 0 ? ellipsis : text.substring(0, length) + ellipsis;
    }

    // Send the action
    private void sendAction(NavigationTableActionPayload.Action action, int slot) {
        PacketDistributor.sendToServer(new NavigationTableActionPayload(
                menu.getContentPos(), menu.getContentSubLevelId(), action, slot));
    }
}
