package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockConfigPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Edit Ship Dock settings
public class ShipDockScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "textures/gui/ship_port_gui.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int BACKGROUND_SOURCE_X = 33;
    private static final int BACKGROUND_SOURCE_Y = 37;
    private static final int GUI_WIDTH = 190;
    private static final int GUI_HEIGHT = 186;
    private static final int NAME_X = 8;
    private static final int NAME_Y = 18;
    private static final int NAME_W = 174;
    private static final int NAME_H = 14;
    private static final int CARD_X = 22;
    private static final int CARD_W = 146;
    private static final int CARD_H = 30;
    private static final int[] CARD_YS = {42, 80, 118};
    private static final int CARD_CHECK_X = 31;
    private static final int CARD_CHECK_Y_OFFSET = 16;
    private static final int CARD_LABEL_X = 55;
    private static final int CARD_LABEL_Y_OFFSET = 4;
    private static final int CARD_SELECTOR_Y_OFFSET = 16;
    private static final int CARD_SELECTOR_W = 105;
    private static final int CARD_SELECTOR_H = 11;
    private static final int DOOR_ENABLE_X = 14;
    private static final int DOOR_ENABLE_Y = 164;
    private static final int DOOR_ENABLE_SIZE = 16;
    private static final int DOOR_MODE_X = 31;
    private static final int DOOR_MODE_Y = 164;
    private static final int DOOR_MODE_W = 68;
    private static final int DOOR_MODE_H = 16;
    private static final int DOOR_LABEL_X = 103;
    private static final int DOOR_LABEL_Y = 168;
    private static final int CONFIRM_X = 165;
    private static final int CONFIRM_Y = 162;
    private static final int CONFIRM_W = 23;
    private static final int CONFIRM_H = 22;
    private static final int POPUP_ROW_H = 12;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Ship dock position
    private final BlockPos pos;
    // Sub-level id
    private final UUID subLevelId;
    // Available dock connectors
    private final List<ShipDockBlockEntity.ConnectorReference> availableConnectors;
    // Selected refueling connectors
    private final Set<ShipDockBlockEntity.ConnectorReference> refuelConnectors = new LinkedHashSet<>();
    // Selected restocking connectors
    private final Set<ShipDockBlockEntity.ConnectorReference> restockConnectors = new LinkedHashSet<>();
    // Selected package connectors
    private final Set<ShipDockBlockEntity.ConnectorReference> packageConnectors = new LinkedHashSet<>();
    // Current dock name
    private String dockName;
    // Tracks whether refuel is set
    private boolean refuel;
    // Tracks whether restock is set
    private boolean restock;
    // Tracks whether packages are set
    private boolean packages;
    // Tracks whether docked ship doors open
    private boolean doorControlEnabled;
    // Selected docked ship door directions
    private int doorControlMask;
    // Open selector
    private Dropdown openDropdown;
    // Current name box
    private EditBox nameBox;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock
    public ShipDockScreen(
            BlockPos pos,
            UUID subLevelId,
            String dockName,
            boolean refuel,
            boolean restock,
            boolean packages,
            boolean doorControlEnabled,
            int doorControlMask,
            List<ShipDockBlockEntity.ConnectorReference> availableConnectors,
            List<ShipDockBlockEntity.ConnectorReference> refuelConnectors,
            List<ShipDockBlockEntity.ConnectorReference> restockConnectors,
            List<ShipDockBlockEntity.ConnectorReference> packageConnectors
    ) {
        super(Component.translatable("createthrusters.ship_dock.title"));
        this.pos = pos;
        this.subLevelId = subLevelId;
        this.dockName = dockName;
        this.refuel = refuel;
        this.restock = restock;
        this.packages = packages;
        this.doorControlEnabled = doorControlEnabled;
        this.doorControlMask = ShipDockBlockEntity.sanitizeDoorControlMask(doorControlMask);
        this.availableConnectors = connectorReferences(availableConnectors);
        this.refuelConnectors.addAll(selectedConnectors(refuelConnectors));
        this.restockConnectors.addAll(selectedConnectors(restockConnectors));
        this.packageConnectors.addAll(selectedConnectors(packageConnectors));
    }

    // Initialize the ship dock
    @Override
    protected void init() {
        Minecraft.getInstance().getTextureManager().getTexture(BACKGROUND).setFilter(false, false);
        int left = left();
        int top = top();
        nameBox = new EditBox(font, left + NAME_X, top + NAME_Y, NAME_W, NAME_H,
                Component.translatable("createthrusters.ship_dock.name"));
        nameBox.setMaxLength(64);
        nameBox.setBordered(false);
        nameBox.setTextColor(0xFFE6E6E6);
        nameBox.setValue(dockName);
        addRenderableWidget(nameBox);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the left edge of the cropped screen
    private int left() {
        return (width - GUI_WIDTH) / 2;
    }

    // Get the top edge of the cropped screen
    private int top() {
        return (height - GUI_HEIGHT) / 2;
    }

    // Draw one dock capability card
    private void drawCapabilityCard(
            GuiGraphics graphics,
            int left,
            int top,
            int index,
            boolean enabled,
            String translationKey,
            Dropdown dropdown,
            Set<ShipDockBlockEntity.ConnectorReference> selected
    ) {
        int cardY = top + CARD_YS[index];
        drawCheckbox(graphics, left + CARD_CHECK_X, cardY + CARD_CHECK_Y_OFFSET, enabled);
        graphics.drawString(font, Component.translatable(translationKey), left + CARD_LABEL_X,
                cardY + CARD_LABEL_Y_OFFSET, enabled ? 0xFFE6E6E6 : 0xFF9A9A9A, false);
        drawDropdownField(graphics, left + CARD_LABEL_X, cardY + CARD_SELECTOR_Y_OFFSET,
                CARD_SELECTOR_W, CARD_SELECTOR_H, connectorSummary(selected), enabled,
                openDropdown == dropdown);
    }

    // Draw a checkbox over the sheet's neutral option field
    private void drawCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 12, y + 12, 0xFF1F1F1F);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, checked ? 0xFF555555 : 0xFF303030);
        if (checked) {
            graphics.drawString(font, "\u2713", x + 2, y + 2, 0xFFE6E6E6, false);
        }
    }

    // Draw a closed selector
    private void drawDropdownField(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            String label,
            boolean enabled,
            boolean open
    ) {
        int border = open ? 0xFFE6E6E6 : 0xFF6A6A6A;
        int background = enabled ? 0xFF1D1D1D : 0xFF272727;
        int textColor = enabled ? 0xFFE6E6E6 : 0xFF8A8A8A;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, background);
        graphics.drawString(font, label, x + 3, y + 2, textColor, false);
        int arrowX = x + width - 7;
        int arrowY = y + height / 2 - 1;
        graphics.fill(arrowX, arrowY, arrowX + 5, arrowY + 1, textColor);
        graphics.fill(arrowX + 1, arrowY + 1, arrowX + 4, arrowY + 2, textColor);
        graphics.fill(arrowX + 2, arrowY + 2, arrowX + 3, arrowY + 3, textColor);
    }

    // Draw the active selector list
    private void drawOpenDropdown(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        if (openDropdown == null) {
            return;
        }
        Popup popup = popup(left, top, openDropdown);
        if (popup.rows() <= 0) {
            return;
        }
        graphics.fill(popup.x() - 1, popup.y() - 1, popup.x() + popup.width() + 1,
                popup.y() + popup.rows() * POPUP_ROW_H + 1, 0xFF0A0A0A);
        for (int index = 0; index < popup.rows(); index++) {
            int rowY = popup.y() + index * POPUP_ROW_H;
            boolean hovered = inside(mouseX, mouseY, popup.x(), rowY, popup.width(), POPUP_ROW_H);
            graphics.fill(popup.x(), rowY, popup.x() + popup.width(), rowY + POPUP_ROW_H,
                    hovered ? 0xFF4A4A4A : 0xFF252525);
            drawSmallCheckbox(graphics, popup.x() + 3, rowY + 2,
                    dropdownSelection(openDropdown, index));
            graphics.drawString(font, dropdownLabel(openDropdown, index), popup.x() + 14, rowY + 2,
                    0xFFE6E6E6, false);
        }
    }

    // Draw a selector checkbox
    private void drawSmallCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 8, y + 8, 0xFF101010);
        graphics.fill(x + 1, y + 1, x + 7, y + 7, checked ? 0xFF6A6A6A : 0xFF353535);
        if (checked) {
            graphics.drawString(font, "\u2713", x, y - 1, 0xFFE6E6E6, false);
        }
    }

    // Get the popup bounds for a selector
    private Popup popup(int left, int top, Dropdown dropdown) {
        int fieldX = left + (dropdown == Dropdown.DOORS ? DOOR_MODE_X : CARD_LABEL_X);
        int fieldY = dropdown == Dropdown.DOORS ? top + DOOR_MODE_Y
                : top + CARD_YS[dropdown.cardIndex()] + CARD_SELECTOR_Y_OFFSET;
        int fieldWidth = dropdown == Dropdown.DOORS ? DOOR_MODE_W : CARD_SELECTOR_W;
        int fieldHeight = dropdown == Dropdown.DOORS ? DOOR_MODE_H : CARD_SELECTOR_H;
        int rows = dropdown == Dropdown.DOORS ? ShipDockBlockEntity.DoorDirection.values().length
                : availableConnectors.size();
        int popupY = fieldY + fieldHeight;
        if (popupY + rows * POPUP_ROW_H > top + GUI_HEIGHT) {
            popupY = fieldY - rows * POPUP_ROW_H;
        }
        return new Popup(fieldX, popupY, fieldWidth, rows);
    }

    // Get a selector label
    private String dropdownLabel(Dropdown dropdown, int index) {
        if (dropdown == Dropdown.DOORS) {
            return Component.translatable("createthrusters.ship_dock.door_control."
                    + ShipDockBlockEntity.DoorDirection.values()[index].name().toLowerCase()).getString();
        }
        return connectorLabel(availableConnectors.get(index), index);
    }

    // Check whether a selector row is selected
    private boolean dropdownSelection(Dropdown dropdown, int index) {
        if (dropdown == Dropdown.DOORS) {
            return (doorControlMask & ShipDockBlockEntity.DoorDirection.values()[index].mask()) != 0;
        }
        return connectorSet(dropdown).contains(availableConnectors.get(index));
    }

    // Toggle a selector row
    private void toggleDropdownSelection(Dropdown dropdown, int index) {
        if (dropdown == Dropdown.DOORS) {
            doorControlMask = ShipDockBlockEntity.sanitizeDoorControlMask(doorControlMask
                    ^ ShipDockBlockEntity.DoorDirection.values()[index].mask());
            return;
        }
        ShipDockBlockEntity.ConnectorReference reference = availableConnectors.get(index);
        Set<ShipDockBlockEntity.ConnectorReference> selected = connectorSet(dropdown);
        if (!selected.remove(reference)) {
            selected.add(reference);
        }
    }

    // Get one capability connector selection
    private Set<ShipDockBlockEntity.ConnectorReference> connectorSet(Dropdown dropdown) {
        return switch (dropdown) {
            case REFUEL -> refuelConnectors;
            case RESTOCK -> restockConnectors;
            case PACKAGES -> packageConnectors;
            case DOORS -> throw new IllegalArgumentException("Doors do not use connector selections");
        };
    }

    // Get the compact selected connector summary
    private String connectorSummary(Set<ShipDockBlockEntity.ConnectorReference> selected) {
        if (availableConnectors.isEmpty()) {
            return Component.translatable("createthrusters.ship_dock.connector_selection.none").getString();
        }
        if (selected.size() == availableConnectors.size()) {
            return Component.translatable("createthrusters.ship_dock.connector_selection.all").getString();
        }
        return Component.translatable("createthrusters.ship_dock.connector_selection.count",
                selected.size()).getString();
    }

    // Get the compact selected door summary
    private String doorSummary() {
        StringBuilder summary = new StringBuilder();
        for (ShipDockBlockEntity.DoorDirection direction : ShipDockBlockEntity.DoorDirection.values()) {
            if ((doorControlMask & direction.mask()) == 0) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append('+');
            }
            summary.append(direction.name().charAt(0));
        }
        return summary.isEmpty()
                ? Component.translatable("createthrusters.ship_dock.door_control.none").getString()
                : summary.toString();
    }

    // Get a linked connector label
    private String connectorLabel(ShipDockBlockEntity.ConnectorReference reference, int index) {
        return Component.translatable("createthrusters.ship_dock.connector_selection.connector",
                index + 1).getString();
    }

    // Toggle a selector
    private void toggleDropdown(Dropdown dropdown) {
        if (dropdown != Dropdown.DOORS && availableConnectors.isEmpty()) {
            return;
        }
        openDropdown = openDropdown == dropdown ? null : dropdown;
    }

    // Copy connector references
    private static List<ShipDockBlockEntity.ConnectorReference> connectorReferences(
            List<ShipDockBlockEntity.ConnectorReference> references
    ) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        List<ShipDockBlockEntity.ConnectorReference> result = new ArrayList<>();
        for (ShipDockBlockEntity.ConnectorReference reference : references) {
            if (reference != null && !result.contains(reference)) {
                result.add(reference);
            }
        }
        return List.copyOf(result);
    }

    // Copy the selected available connectors
    private Set<ShipDockBlockEntity.ConnectorReference> selectedConnectors(
            List<ShipDockBlockEntity.ConnectorReference> selected
    ) {
        Set<ShipDockBlockEntity.ConnectorReference> result = new LinkedHashSet<>();
        if (selected == null) {
            return result;
        }
        for (ShipDockBlockEntity.ConnectorReference reference : selected) {
            if (availableConnectors.contains(reference)) {
                result.add(reference);
            }
        }
        return result;
    }

    // Check whether a pointer is inside an area
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the game world and this pixel-art sheet free of the menu blur pass
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // Draw the ship dock
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.blit(BACKGROUND, left, top, GUI_WIDTH, GUI_HEIGHT,
                BACKGROUND_SOURCE_X, BACKGROUND_SOURCE_Y, GUI_WIDTH, GUI_HEIGHT,
                TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.drawCenteredString(font, title, left + GUI_WIDTH / 2, top + 4, 0xFF29231A);
        drawCapabilityCard(graphics, left, top, 0, refuel,
                "createthrusters.ship_dock.capability.refuel", Dropdown.REFUEL, refuelConnectors);
        drawCapabilityCard(graphics, left, top, 1, restock,
                "createthrusters.ship_dock.capability.restock", Dropdown.RESTOCK, restockConnectors);
        drawCapabilityCard(graphics, left, top, 2, packages,
                "createthrusters.ship_dock.capability.packages", Dropdown.PACKAGES, packageConnectors);
        graphics.blit(BACKGROUND, left + DOOR_ENABLE_X, top + DOOR_ENABLE_Y,
                DOOR_ENABLE_SIZE, DOOR_ENABLE_SIZE,
                doorControlEnabled ? 0 : 16, 240, DOOR_ENABLE_SIZE, DOOR_ENABLE_SIZE,
                TEXTURE_SIZE, TEXTURE_SIZE);
        drawDropdownField(graphics, left + DOOR_MODE_X, top + DOOR_MODE_Y,
                DOOR_MODE_W, DOOR_MODE_H, doorSummary(), doorControlEnabled,
                openDropdown == Dropdown.DOORS);
        graphics.drawString(font, Component.translatable("createthrusters.ship_dock.door_control"),
                left + DOOR_LABEL_X, top + DOOR_LABEL_Y,
                doorControlEnabled ? 0xFF323232 : 0xFF757575, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawOpenDropdown(graphics, left, top, mouseX, mouseY);

        if (openDropdown == null && (inside(mouseX, mouseY, left + DOOR_ENABLE_X,
                top + DOOR_ENABLE_Y, DOOR_ENABLE_SIZE, DOOR_ENABLE_SIZE)
                || inside(mouseX, mouseY, left + DOOR_MODE_X, top + DOOR_MODE_Y,
                DOOR_MODE_W, DOOR_MODE_H))) {
            graphics.renderTooltip(font, Component.translatable(
                    "createthrusters.ship_dock.door_control.tooltip"), mouseX, mouseY);
        }
    }

    // Handle a screen click
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int left = left();
        int top = top();
        if (openDropdown != null) {
            Popup popup = popup(left, top, openDropdown);
            if (inside(mouseX, mouseY, popup.x(), popup.y(), popup.width(),
                    popup.rows() * POPUP_ROW_H)) {
                int index = (int) ((mouseY - popup.y()) / POPUP_ROW_H);
                toggleDropdownSelection(openDropdown, index);
                return true;
            }
            openDropdown = null;
            return true;
        }
        for (int index = 0; index < CARD_YS.length; index++) {
            int cardY = top + CARD_YS[index];
            if (inside(mouseX, mouseY, left + CARD_LABEL_X, cardY + CARD_SELECTOR_Y_OFFSET,
                    CARD_SELECTOR_W, CARD_SELECTOR_H)) {
                toggleDropdown(Dropdown.forCard(index));
                return true;
            }
            if (!inside(mouseX, mouseY, left + CARD_X, cardY, CARD_W, CARD_H)) {
                continue;
            }
            switch (index) {
                case 0 -> refuel = !refuel;
                case 1 -> restock = !restock;
                case 2 -> packages = !packages;
                default -> {
                }
            }
            return true;
        }
        if (inside(mouseX, mouseY, left + DOOR_ENABLE_X, top + DOOR_ENABLE_Y,
                DOOR_ENABLE_SIZE, DOOR_ENABLE_SIZE)) {
            doorControlEnabled = !doorControlEnabled;
            return true;
        }
        if (inside(mouseX, mouseY, left + DOOR_MODE_X, top + DOOR_MODE_Y,
                DOOR_MODE_W, DOOR_MODE_H)) {
            toggleDropdown(Dropdown.DOORS);
            return true;
        }
        if (inside(mouseX, mouseY, left + CONFIRM_X, top + CONFIRM_Y, CONFIRM_W, CONFIRM_H)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Close an open selector before closing the screen
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && openDropdown != null) {
            openDropdown = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Handle the close event
    @Override
    public void onClose() {
        dockName = nameBox == null ? dockName : nameBox.getValue();
        PacketDistributor.sendToServer(new ShipDockConfigPayload(
                pos, subLevelId, dockName, refuel, restock, packages,
                doorControlEnabled, doorControlMask, List.copyOf(refuelConnectors),
                List.copyOf(restockConnectors), List.copyOf(packageConnectors)));
        super.onClose();
    }

    // Store the active selector
    private enum Dropdown {
        REFUEL(0),
        RESTOCK(1),
        PACKAGES(2),
        DOORS(-1);

        private final int cardIndex;

        // Initialize the selector
        Dropdown(int cardIndex) {
            this.cardIndex = cardIndex;
        }

        // Get the matching card index
        private int cardIndex() {
            return cardIndex;
        }

        // Get the selector for a card
        private static Dropdown forCard(int index) {
            return switch (index) {
                case 0 -> REFUEL;
                case 1 -> RESTOCK;
                case 2 -> PACKAGES;
                default -> throw new IllegalArgumentException("Invalid ship dock card index: " + index);
            };
        }
    }

    // Store selector popup bounds
    private record Popup(int x, int y, int width, int rows) {
    }
}
