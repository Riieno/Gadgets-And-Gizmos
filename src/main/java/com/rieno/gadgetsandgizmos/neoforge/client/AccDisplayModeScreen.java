package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayModePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;

// Select an ACC display mode and preview its current frame before saving
public final class AccDisplayModeScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/aac_display.png");
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;
    private static final int LIST_LEFT = 5;
    private static final int LIST_TOP = 28;
    private static final int LIST_WIDTH = 108;
    private static final int LIST_HEIGHT = 156;
    private static final int ROW_HEIGHT = 14;
    private static final List<String> MODES = ShipInformationDisplayModes.ids();
    private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry("passenger_information/running_text", "Single-line passenger information banner."),
            Map.entry("passenger_information/detailed_with_schedule", "Ship, route, next stop, progress and ETA."),
            Map.entry("train_destination/simple", "Large destination with minimal surrounding detail."),
            Map.entry("train_destination/extended", "Destination with current and next stop context."),
            Map.entry("train_destination/detailed", "Detailed destination, route status and ETA."),
            Map.entry("platform/running_text", "Scrolling platform-style arrival message."),
            Map.entry("platform/table", "Compact platform timetable rows."),
            Map.entry("platform/focus", "Large focused platform and destination card."),
            Map.entry("departure_board/table", "Multi-row departures board layout."),
            Map.entry("static_text/simple_text", "Centered plain text presentation."),
            Map.entry("static_text/rich_text", "Header and body rich-text presentation."));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // ACC display mode target
    private final MenuConfigTarget target;
    // Current frame
    private final CompoundTag currentFrame;
    // Selected mode
    private String selectedMode;
    // Current scroll row
    private int scrollRow;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display mode
    public AccDisplayModeScreen(MenuConfigTarget target, String selectedMode, CompoundTag currentFrame) {
        super(Component.literal("ACC Display Mode"));
        this.target = target;
        this.selectedMode = AccDisplayBlockEntity.normalizeDisplayMode(selectedMode);
        this.currentFrame = currentFrame == null ? new CompoundTag() : currentFrame.copy();
    }

    // Initialize the ACC display mode
    @Override
    protected void init() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the background
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ACC display mode
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xB0080C12, 0xD0101820);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.blit(BACKGROUND, left, top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_WIDTH, PANEL_HEIGHT);
        graphics.drawString(font, title, left + 7, top + 7, 0xFF17212A, false);

        int listLeft = left + LIST_LEFT;
        int listTop = top + LIST_TOP;
        graphics.enableScissor(listLeft, listTop, listLeft + LIST_WIDTH, listTop + LIST_HEIGHT);
        for (int idx = 0; idx < MODES.size(); idx++) {
            int rowY = listTop + (idx - scrollRow) * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT <= listTop || rowY >= listTop + LIST_HEIGHT) {
                continue;
            }
            String mode = MODES.get(idx);
            boolean selected = mode.equals(selectedMode);
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + 59
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            graphics.fill(listLeft + 1, rowY, listLeft + LIST_WIDTH - 1, rowY + ROW_HEIGHT - 1,
                    selected ? 0xFF6D777D : hovered ? 0xFF4B5358 : 0x00000000);
            String label = font.plainSubstrByWidth(ShipInformationDisplayModes.label(mode),
                    LIST_WIDTH - 7);
            graphics.drawString(font, label, listLeft + 4, rowY + 3,
                    selected ? 0xFFFFFFFF : 0xFFD7DBDD, false);
        }
        graphics.disableScissor();

        int previewLeft = left + 124;
        int previewTop = top + 62;
        int previewWidth = 188;
        graphics.drawCenteredString(font, ShipInformationDisplayModes.label(selectedMode),
                previewLeft + previewWidth / 2, top + 38, 0xFFDCE1E4);
        drawPreview(graphics, previewLeft + 3, previewTop + 3, previewWidth - 6);
        graphics.drawWordWrap(font, Component.literal(DESCRIPTIONS.get(selectedMode)),
                previewLeft + 3, top + 143, previewWidth - 6, 0xFFCDD2D5);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Draw the preview
    private void drawPreview(GuiGraphics graphics, int x, int y, int width) {
        int height = 65;
        graphics.fill(x, y, x + width, y + height, 0xFF151D25);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF071017);
        CompoundTag data = currentFrame.getCompound("CrnData");
        String ship = previewValue(data.getString("ShipName"), "Unnamed Ship");
        String target = previewValue(data.getString("TargetStop"), "Central Harbour");
        String next = previewValue(data.getString("NextStop"), "North Platform");
        switch (selectedMode) {
            case "passenger_information/running_text", "platform/running_text" -> {
                previewHeader(graphics, x, y, width, ship);
                graphics.drawCenteredString(font, "NEXT  " + target, x + width / 2,
                        y + 35, 0xFFFFFFFF);
            }
            case "passenger_information/detailed_with_schedule" -> {
                previewHeader(graphics, x, y, width, ship);
                previewRow(graphics, x, y + 17, width, "DESTINATION", target);
                previewRow(graphics, x, y + 30, width, "NEXT", next);
                graphics.fill(x + 7, y + 49, x + width - 7, y + 53, 0xFF19323D);
                graphics.fill(x + 7, y + 49, x + width / 2, y + 53, 0xFF49B8D7);
            }
            case "train_destination/simple" -> {
                graphics.drawCenteredString(font, target.toUpperCase(java.util.Locale.ROOT),
                        x + width / 2, y + 18, 0xFFFFFFFF);
                graphics.drawCenteredString(font, "DESTINATION", x + width / 2,
                        y + 36, 0xFF70D4EA);
            }
            case "train_destination/extended", "train_destination/detailed" -> {
                previewHeader(graphics, x, y, width, "DESTINATION");
                graphics.drawCenteredString(font, target, x + width / 2, y + 25, 0xFFFFFFFF);
                previewRow(graphics, x, y + 40, width, "NEXT", next);
            }
            case "platform/table", "departure_board/table" -> {
                previewHeader(graphics, x, y, width,
                        selectedMode.startsWith("departure") ? "DEPARTURES" : "PLATFORM");
                previewRow(graphics, x, y + 17, width, "10:20", target);
                previewRow(graphics, x, y + 30, width, "10:45", next);
                previewRow(graphics, x, y + 43, width, "11:10", "Shipyard");
            }
            case "platform/focus" -> {
                previewHeader(graphics, x, y, width, "PLATFORM  2");
                graphics.drawCenteredString(font, target, x + width / 2, y + 27, 0xFFFFFFFF);
                graphics.drawCenteredString(font, "BOARDING", x + width / 2, y + 43, 0xFF62D58B);
            }
            case "static_text/rich_text" -> {
                previewHeader(graphics, x, y, width, ship);
                graphics.drawString(font, "Route information", x + 8, y + 24, 0xFFFFFFFF, false);
                graphics.drawString(font, "Services operating normally", x + 8, y + 39,
                        0xFF9FD7E8, false);
            }
            default -> {
                graphics.drawCenteredString(font, "Route information", x + width / 2,
                        y + 24, 0xFFFFFFFF);
                graphics.drawCenteredString(font, target, x + width / 2,
                        y + 39, 0xFF70D4EA);
            }
        }
    }

    // Handle the preview header
    private void previewHeader(GuiGraphics graphics, int x, int y, int width, String text) {
        graphics.fill(x + 3, y + 3, x + width - 3, y + 14, 0xFF123141);
        graphics.drawCenteredString(font, text, x + width / 2, y + 5, 0xFFBCEFFF);
    }

    // Handle the preview row
    private void previewRow(GuiGraphics graphics, int x, int y, int width,
                            String label, String val) {
        graphics.drawString(font, label, x + 7, y, 0xFF66BBD2, false);
        graphics.drawString(font, font.plainSubstrByWidth(val, Math.max(10, width - 62)),
                x + 58, y, 0xFFFFFFFF, false);
    }

    // Get the preview value
    private static String previewValue(String val, String fallback) {
        return val == null || val.isBlank() ? fallback : val;
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (btn == 0) {
            int panelLeft = (width - PANEL_WIDTH) / 2;
            int panelTop = (height - PANEL_HEIGHT) / 2;
            int left = panelLeft + LIST_LEFT;
            int top = panelTop + LIST_TOP;
            if (mouseX >= left && mouseX < left + LIST_WIDTH
                    && mouseY >= top && mouseY < top + LIST_HEIGHT) {
                int idx = scrollRow + (int) ((mouseY - top) / ROW_HEIGHT);
                if (idx >= 0 && idx < MODES.size()) {
                    selectedMode = MODES.get(idx);
                    PacketDistributor.sendToServer(new AccDisplayModePayload(target, selectedMode));
                    return true;
                }
            }
            if (mouseX >= panelLeft + 302 && mouseX < panelLeft + 329
                    && mouseY >= panelTop + 191 && mouseY < panelTop + 219) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visibleRows = Math.max(1, LIST_HEIGHT / ROW_HEIGHT);
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY),
                0, Math.max(0, MODES.size() - visibleRows));
        return true;
    }
}
