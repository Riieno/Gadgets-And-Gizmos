package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonAppearancePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonConfigPayload;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;

// Edit each button half's response mode and link-hardware appearance
public class DoubleButtonModeScreen extends AbstractSimiScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 224;
    private static final int HEIGHT = 180;
    private static final int COLUMN_WIDTH = 88;
    private static final int FIELD_HEIGHT = 18;
    private static final int OPTION_HEIGHT = 18;
    private static final int COLUMN_GAP = 12;
    private static final int LINK_TOGGLE_Y = 153;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Double button mode target
    private final MenuConfigTarget target;
    // Selected modes
    private final EnumMap<DoubleButtonBlockEntity.ButtonHalf, DoubleButtonBlockEntity.ResponseMode> selectedModes =
            new EnumMap<>(DoubleButtonBlockEntity.ButtonHalf.class);
    // Current open dropdown
    private DoubleButtonBlockEntity.ButtonHalf openDropdown;
    // Tracks whether link hardware is visible
    private boolean linkHardwareVisible;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button mode
    public DoubleButtonModeScreen(DoubleButtonBlockEntity blockEntity) {
        super(Component.translatable("createthrusters.double_button.screen.title"));
        this.target = MenuConfigTarget.of(blockEntity.getBlockPos(), SimulatedHelper.getContainingSubLevelId(blockEntity));
        for (DoubleButtonBlockEntity.ButtonHalf btn : DoubleButtonBlockEntity.ButtonHalf.values()) {
            selectedModes.put(btn, blockEntity.getResponseMode(btn));
        }
        linkHardwareVisible = blockEntity.isLinkHardwareVisible();
        setWindowSize(WIDTH, HEIGHT);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the window
    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        CTCreateScreenHelper.renderPanel(graphics, guiLeft, guiTop, windowWidth, windowHeight);
        graphics.drawCenteredString(font, title, guiLeft + windowWidth / 2, guiTop + 7,
                CTCreateScreenHelper.BANNER_TITLE_COLOR);
        CTCreateScreenHelper.renderSeparator(graphics, guiLeft + 8, guiTop + 21, windowWidth - 16);

        for (DoubleButtonBlockEntity.ButtonHalf btn : DoubleButtonBlockEntity.ButtonHalf.values()) {
            int x = columnX(btn);
            graphics.drawCenteredString(font, Component.translatable(btn.translationKey()),
                    x + COLUMN_WIDTH / 2, guiTop + 32, CTCreateScreenHelper.LABEL_COLOR);
            renderDropdownField(graphics, btn, x, mouseX, mouseY);
        }

        if (openDropdown != null) {
            renderDropdownOptions(graphics, openDropdown, columnX(openDropdown), mouseX, mouseY);
        }

        int toggleX = contentLeft();
        int toggleY = guiTop + LINK_TOGGLE_Y;
        int toggleWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
        boolean hovered = inside(mouseX, mouseY, toggleX, toggleY, toggleWidth, FIELD_HEIGHT);
        Component toggleLabel = Component.translatable(linkHardwareVisible
                ? "createthrusters.double_button.links.visible"
                : "createthrusters.double_button.links.hidden");
        CTCreateScreenHelper.renderTextButton(graphics, font, toggleX, toggleY, toggleWidth, FIELD_HEIGHT,
                toggleLabel, hovered, linkHardwareVisible, linkHardwareVisible);
    }

    // Draw the dropdown field
    private void renderDropdownField(GuiGraphics graphics, DoubleButtonBlockEntity.ButtonHalf btn,
                                     int x, int mouseX, int mouseY) {
        int y = guiTop + 47;
        boolean hovered = inside(mouseX, mouseY, x, y, COLUMN_WIDTH, FIELD_HEIGHT);
        DoubleButtonBlockEntity.ResponseMode mode = selectedModes.getOrDefault(
                btn, DoubleButtonBlockEntity.ResponseMode.OAK);
        Component label = Component.translatable(mode.translationKey()).copy().append("  ▾");
        CTCreateScreenHelper.renderTextButton(graphics, font, x, y, COLUMN_WIDTH, FIELD_HEIGHT,
                label, hovered, openDropdown == btn, openDropdown == btn);
    }

    // Draw the dropdown options
    private void renderDropdownOptions(GuiGraphics graphics, DoubleButtonBlockEntity.ButtonHalf btn,
                                       int x, int mouseX, int mouseY) {
        int y = guiTop + 67;
        DoubleButtonBlockEntity.ResponseMode selected = selectedModes.get(btn);
        for (DoubleButtonBlockEntity.ResponseMode mode : DoubleButtonBlockEntity.ResponseMode.values()) {
            boolean hovered = inside(mouseX, mouseY, x, y, COLUMN_WIDTH, OPTION_HEIGHT);
            CTCreateScreenHelper.renderTextButton(graphics, font, x, y, COLUMN_WIDTH, OPTION_HEIGHT,
                    Component.translatable(mode.translationKey()), hovered, selected == mode, selected == mode);
            y += OPTION_HEIGHT + 2;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (btn != 0) {
            return super.mouseClicked(mouseX, mouseY, btn);
        }

        for (DoubleButtonBlockEntity.ButtonHalf half : DoubleButtonBlockEntity.ButtonHalf.values()) {
            int x = columnX(half);
            if (inside(mouseX, mouseY, x, guiTop + 47, COLUMN_WIDTH, FIELD_HEIGHT)) {
                openDropdown = openDropdown == half ? null : half;
                return true;
            }
        }

        if (openDropdown != null) {
            int x = columnX(openDropdown);
            int y = guiTop + 67;
            for (DoubleButtonBlockEntity.ResponseMode mode : DoubleButtonBlockEntity.ResponseMode.values()) {
                if (inside(mouseX, mouseY, x, y, COLUMN_WIDTH, OPTION_HEIGHT)) {
                    selectedModes.put(openDropdown, mode);
                    PacketDistributor.sendToServer(new DoubleButtonConfigPayload(target, openDropdown, mode));
                    openDropdown = null;
                    return true;
                }
                y += OPTION_HEIGHT + 2;
            }
        }

        int toggleX = contentLeft();
        int toggleWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
        if (inside(mouseX, mouseY, toggleX, guiTop + LINK_TOGGLE_Y, toggleWidth, FIELD_HEIGHT)) {
            linkHardwareVisible = !linkHardwareVisible;
            PacketDistributor.sendToServer(new DoubleButtonAppearancePayload(target, linkHardwareVisible));
            openDropdown = null;
            return true;
        }

        openDropdown = null;
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Get the column x
    private int columnX(DoubleButtonBlockEntity.ButtonHalf btn) {
        int left = contentLeft();
        return btn == DoubleButtonBlockEntity.ButtonHalf.TOP ? left : left + COLUMN_WIDTH + COLUMN_GAP;
    }

    // Get the content left
    private int contentLeft() {
        int contentWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
        return guiLeft + (windowWidth - contentWidth) / 2;
    }

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
