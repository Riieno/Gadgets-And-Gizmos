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
import com.rieno.gadgetsandgizmos.lib.client.render.GuiPoseScissor;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiScrollbar;
import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.ListScrollState;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    private static final int WIDTH = 213;
    private static final int HEIGHT = 277;
    private static final int MAP_GRID_COLS = NavigationTableMenu.MAP_GRID_COLS;
    private static final int MAP_GRID_SLOT = 18;
    private static final int LIST_AREA_X = 86;
    private static final int LIST_AREA_Y = 26;
    private static final int LIST_AREA_W = 116;
    private static final int LIST_AREA_H = 112;
    private static final int MAP_ITEM_H = 14;
    private static final int MAP_ITEM_STEP = 15;
    private static final int SCROLLBAR_X = 206;
    private static final int SCROLLBAR_Y = 23;
    private static final int SCROLLBAR_W = 4;
    private static final int SCROLLBAR_H = 117;
    private static final int STATUS_X = 86;
    private static final int STATUS_Y = 151;
    private static final int STATUS_W = 116;
    private static final int BUTTON_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 11;
    private static final int START_X = 3;
    private static final int PAUSE_X = 45;
    private static final int STOP_X = 87;
    private static final int BUTTON_Y = 173;
    private static final int CONFIRM_X = 195;
    private static final int CONFIRM_Y = 173;
    private static final int CONFIRM_WIDTH = 15;
    private static final int TEXT_COLOR = 0xFFF3EEE4;
    private static final int SUBTLE_TEXT_COLOR = 0xFFB8B8B8;
    private static final int SELECTED_COLOR = 0xFF91BBE7;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters",
            "textures/gui/advanced_navigation_table.png");
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(TEXTURE, 280, 300, 0, 0, WIDTH, HEIGHT);
    private static final GuiTextureRegion LIST_BUTTON = new GuiTextureRegion(TEXTURE, 280, 300, 0, 286, LIST_AREA_W, MAP_ITEM_H);
    private static final GuiTextureRegion SCROLL_TRACK = new GuiTextureRegion(TEXTURE, 280, 300, 207, 23, 2, 117);
    private static final GuiTextureRegion SCROLL_THUMB = new GuiTextureRegion(TEXTURE, 280, 300, 119, 298, 4, 2);
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scroll by pixels so clipped entries remain selectable at the viewport edges
    private final ListScrollState mapListScroll = new ListScrollState();
    private final GuiScrollbar mapScrollbar = new GuiScrollbar(SCROLL_TRACK, SCROLL_THUMB);
    private List<Component> hoveredMapTooltip = List.of();

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

        updateScalableGuiBounds();
        mapScrollbar.mouseReleased();
        configureMapList();
        mapListScroll.ensureVisible(menu.getSelectedSlot());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the scalable gui bounds
    private void updateScalableGuiBounds() {
        scalableGui.update(leftPos, topPos, imageWidth, imageHeight, width, height);
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        updateScalableGuiBounds();
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try{
            int x = leftPos;
            int y = topPos;
            BACKGROUND.draw(guiGraphics, x, y);
            GuiControlPreviews.drawFittedText(guiGraphics, font, title, x + 8, y + 1,
                    WIDTH - 16, 11, 1, TEXT_COLOR, true);
            renderTableMapGrid(guiGraphics, x, y, mouseX, mouseY);
            hoveredMapTooltip = List.of();
            configureMapList();
            renderMapListArea(guiGraphics, x, y, mouseX, mouseY);
            mapScrollbar.render(guiGraphics);
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
            if(inside(mouseX, mouseY, CONFIRM_X, CONFIRM_Y, CONFIRM_WIDTH, BUTTON_HEIGHT)){
                guiGraphics.fill(x + CONFIRM_X, y + CONFIRM_Y, x + CONFIRM_X + CONFIRM_WIDTH,
                        y + CONFIRM_Y + BUTTON_HEIGHT, 0x22FFFFFF);
            }
            if(!hoveredMapTooltip.isEmpty()){
                guiGraphics.renderTooltip(font, hoveredMapTooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }finally{
            scalableGui.pop(guiGraphics);
        }
    }

    // Keep scrollbar input and pixel clipping on the same range
    private void configureMapList(){
        mapListScroll.configure(NavigationTableExtensionAccess.SLOT_COUNT, MAP_ITEM_STEP, LIST_AREA_H);
        mapScrollbar.configure(leftPos + SCROLLBAR_X, topPos + SCROLLBAR_Y, SCROLLBAR_W, SCROLLBAR_H,
                mapListScroll.maximumOffset(), mapListScroll.offset());
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

            if(selected){
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY, SELECTED_COLOR);
                guiGraphics.fill(slotX - 1, slotY + 16, slotX + 17, slotY + 17, SELECTED_COLOR);
                guiGraphics.fill(slotX - 1, slotY, slotX, slotY + 16, SELECTED_COLOR);
                guiGraphics.fill(slotX + 16, slotY, slotX + 17, slotY + 16, SELECTED_COLOR);
            }else if(hovered){
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x22FFFFFF);
            }
        }
    }

    // Clip painted rows, including the partially visible row at either edge
    private void renderMapListArea(GuiGraphics guiGraphics, int baseX, int baseY, int mouseX, int mouseY){
        int x = baseX + LIST_AREA_X;
        int y = baseY + LIST_AREA_Y;
        boolean overList = inside(mouseX, mouseY, LIST_AREA_X, LIST_AREA_Y, LIST_AREA_W, LIST_AREA_H);
        try(var clip = GuiPoseScissor.push(guiGraphics, x, y, x + LIST_AREA_W, y + LIST_AREA_H)){
            for(int idx = mapListScroll.firstVisibleIndex(); idx < NavigationTableExtensionAccess.SLOT_COUNT; idx++){
                int itemY = y + mapListScroll.rowOffset(idx);
                if(itemY >= y + LIST_AREA_H) break;
                if(itemY + MAP_ITEM_H <= y) continue;
                boolean hovered = overList && mouseY >= itemY && mouseY < itemY + MAP_ITEM_H;
                boolean selected = idx == menu.getSelectedSlot();
                LIST_BUTTON.draw(guiGraphics, x, itemY);
                if(selected) guiGraphics.fill(x + 1, itemY + 1, x + LIST_AREA_W - 1, itemY + MAP_ITEM_H - 1, 0x224F83B6);
                if(hovered) guiGraphics.fill(x + 1, itemY + 1, x + LIST_AREA_W - 1, itemY + MAP_ITEM_H - 1, 0x22FFFFFF);
                var holder = menu.getContentHolder();
                var map = holder == null ? net.minecraft.world.item.ItemStack.EMPTY : holder.ct$getMapInSlot(idx);
                if(map.isEmpty()){
                    GuiControlPreviews.drawFittedText(guiGraphics, font,
                            Component.translatable("createthrusters.navigation_table.label.slot", idx + 1),
                            x + 3, itemY + 2, LIST_AREA_W - 6, MAP_ITEM_H - 4, 0.8F,
                            selected ? SELECTED_COLOR : SUBTLE_TEXT_COLOR, false);
                    continue;
                }
                NavigationTableMapResolver.ResolvedTarget target = holder.ct$getResolvedTarget(idx);
                String label = targetLabel(target, idx, map);
                String coords = targetCoords(target);
                LayeredItemRenderer.renderVisible(guiGraphics, map, x + 3, itemY + 2, 10);
                GuiControlPreviews.drawFittedText(guiGraphics, font,
                        Component.literal(trimToWidth(label, (int)((LIST_AREA_W - 21) / 0.8F))),
                        x + 16, itemY + 2, LIST_AREA_W - 20, MAP_ITEM_H - 4, 0.8F,
                        selected ? SELECTED_COLOR : TEXT_COLOR, false);
                if(hovered){
                    Component slot = Component.translatable("createthrusters.navigation_table.label.slot", idx + 1);
                    hoveredMapTooltip = coords.isEmpty() ? List.of(slot, Component.literal(label))
                            : List.of(slot, Component.literal(label), Component.literal(coords));
                }
            }
        }
    }

    // Fit the selected slot and run state into the painted status strip
    private void renderStatusArea(GuiGraphics guiGraphics, int x, int y){
        Component selected = Component.translatable("createthrusters.navigation_table.label.selected_slot", menu.getSelectedSlot() + 1);
        Component state = Component.translatable("createthrusters.navigation_table.label.state",
                Component.translatable("createthrusters.navigation_table.state." + menu.getRunState().name().toLowerCase(Locale.ROOT)));
        GuiControlPreviews.drawFittedText(guiGraphics, font, selected, x + STATUS_X, y + STATUS_Y, 70, 10, 0.8F, SELECTED_COLOR, false);
        GuiControlPreviews.drawFittedText(guiGraphics, font, state, x + STATUS_X + 72, y + STATUS_Y,
                STATUS_W - 72, 10, 0.8F, SUBTLE_TEXT_COLOR, false);
    }

    // Keep action labels and hover states inside the baked footer buttons
    private void renderActionButton(GuiGraphics guiGraphics, int relativeX, int relativeY, Component text,
                                    int mouseX, int mouseY, boolean active){
        int x = leftPos + relativeX;
        int y = topPos + relativeY;
        if(active) guiGraphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, 0x224F83B6);
        if(inside(mouseX, mouseY, relativeX, relativeY, BUTTON_WIDTH, BUTTON_HEIGHT)){
            guiGraphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, 0x22FFFFFF);
        }
        GuiControlPreviews.drawFittedText(guiGraphics, font, text, x + 2, y + 1,
                BUTTON_WIDTH - 4, BUTTON_HEIGHT - 2, 0.8F, TEXT_COLOR, true);
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

    // Scroll the list by rows while retaining partial pixel offsets from the scrollbar
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        double localX = scalableGui.mouseX(mouseX);
        double localY = scalableGui.mouseY(mouseY);
        configureMapList();
        if(inside(localX, localY, LIST_AREA_X, LIST_AREA_Y, LIST_AREA_W, LIST_AREA_H) || mapScrollbar.contains(localX, localY)){
            mapListScroll.scrollBy((int)Math.round(-scrollY * MAP_ITEM_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Resolve painted list rows through the same pixel offset used for rendering
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn){
        double localX = scalableGui.mouseX(mouseX);
        double localY = scalableGui.mouseY(mouseY);
        if(btn != 0) return super.mouseClicked(mouseX, mouseY, btn);
        configureMapList();
        if(mapScrollbar.mouseClicked(localX, localY, btn)){
            mapListScroll.scrollBy(mapScrollbar.value() - mapListScroll.offset());
            return true;
        }
        if(inside(localX, localY, LIST_AREA_X, LIST_AREA_Y, LIST_AREA_W, LIST_AREA_H)){
            int pos = (int)(localY - topPos - LIST_AREA_Y) + mapListScroll.offset();
            int idx = pos / MAP_ITEM_STEP;
            if(pos % MAP_ITEM_STEP < MAP_ITEM_H && idx < NavigationTableExtensionAccess.SLOT_COUNT){
                sendAction(NavigationTableActionPayload.Action.SELECT_SLOT, idx);
                mapListScroll.ensureVisible(idx);
            }
            return true;
        }
        if(inside(localX, localY, START_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)){
            sendAction(NavigationTableActionPayload.Action.START, menu.getSelectedSlot());
            return true;
        }
        if(inside(localX, localY, PAUSE_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)){
            sendAction(NavigationTableActionPayload.Action.PAUSE, menu.getSelectedSlot());
            return true;
        }
        if(inside(localX, localY, STOP_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT)){
            sendAction(NavigationTableActionPayload.Action.STOP, menu.getSelectedSlot());
            return true;
        }
        if(inside(localX, localY, CONFIRM_X, CONFIRM_Y, CONFIRM_WIDTH, BUTTON_HEIGHT)){
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Drag the painted thumb without quantizing the list to whole rows
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY){
        if(mapScrollbar.mouseDragged(scalableGui.mouseY(mouseY), btn)){
            mapListScroll.scrollBy(mapScrollbar.value() - mapListScroll.offset());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn){
        if(btn == 0 && mapScrollbar.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, btn);
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
