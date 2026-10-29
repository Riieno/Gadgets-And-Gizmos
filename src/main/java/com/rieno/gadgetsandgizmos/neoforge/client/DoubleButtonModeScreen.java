package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiControlPreviews;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiDropdownControl;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonAppearancePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonConfigPayload;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;

// Edit each button half's response mode and link-hardware appearance
public class DoubleButtonModeScreen extends AbstractSimiScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 182;
    private static final int HEIGHT = 169;
    private static final int COLUMN_WIDTH = 66;
    private static final int FIELD_Y = 49;
    private static final int FIELD_HEIGHT = 16;
    private static final int OPTION_Y = 67;
    private static final int OPTION_HEIGHT = 16;
    private static final int LINK_TOGGLE_X = 6;
    private static final int LINK_TOGGLE_Y = 150;
    private static final int LINK_TOGGLE_WIDTH = 113;
    private static final int CONFIRM_X = 160;
    private static final int CONFIRM_Y = 149;
    private static final int CONFIRM_SIZE = 18;
    private static final int TEXT_COLOR = 0xFFF3EEE4;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters",
            "textures/gui/double_button.png");
    private static final GuiTextureRegion BACKGROUND = new GuiTextureRegion(TEXTURE, 182, 212, 0, 0, WIDTH, HEIGHT);
    private static final GuiTextureRegion DROPDOWN_BUTTON = new GuiTextureRegion(TEXTURE, 182, 212, 0, 196, 66, 16);
    private static final GuiDropdownControl.Colors DROPDOWN_COLORS = new GuiDropdownControl.Colors(0, 0, TEXT_COLOR, TEXT_COLOR);

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
    // Keep painted controls and pointer input in the same scaled coordinates
    private final CTScalableGui scalableGui = new CTScalableGui();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the double button mode
    public DoubleButtonModeScreen(DoubleButtonBlockEntity blockEntity) {
        super(Component.translatable("block.createthrusters.double_button"));
        this.target = MenuConfigTarget.of(blockEntity.getBlockPos(), SimulatedHelper.getContainingSubLevelId(blockEntity));
        for (DoubleButtonBlockEntity.ButtonHalf btn : DoubleButtonBlockEntity.ButtonHalf.values()) {
            selectedModes.put(btn, blockEntity.getResponseMode(btn));
        }
        linkHardwareVisible = blockEntity.isLinkHardwareVisible();
        setWindowSize(WIDTH, HEIGHT);
    }

    // Fit the complete frame without including the spare dropdown partial
    @Override
    protected void init(){
        super.init();
        scalableGui.update(guiLeft, guiTop, windowWidth, windowHeight, width, height);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the window
    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks){
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(graphics);
        try{
            BACKGROUND.draw(graphics, guiLeft, guiTop);
            GuiControlPreviews.drawFittedText(graphics, font, title, guiLeft + 8, guiTop + 1,
                    WIDTH - 16, 11, 1, TEXT_COLOR, true);
            for(DoubleButtonBlockEntity.ButtonHalf btn : DoubleButtonBlockEntity.ButtonHalf.values()){
                int x = columnX(btn);
                GuiControlPreviews.drawFittedText(graphics, font, Component.translatable(btn.translationKey()),
                        x, guiTop + 36, COLUMN_WIDTH, 11, 1, TEXT_COLOR, true);
                renderDropdownField(graphics, btn, x, mouseX, mouseY);
            }
            int toggleX = guiLeft + LINK_TOGGLE_X;
            int toggleY = guiTop + LINK_TOGGLE_Y;
            if(inside(mouseX, mouseY, toggleX, toggleY, LINK_TOGGLE_WIDTH, FIELD_HEIGHT)){
                graphics.fill(toggleX + 1, toggleY + 1, toggleX + LINK_TOGGLE_WIDTH - 1, toggleY + FIELD_HEIGHT - 1, 0x22FFFFFF);
            }
            Component toggleLabel = Component.translatable(linkHardwareVisible
                    ? "createthrusters.double_button.links.visible" : "createthrusters.double_button.links.hidden");
            GuiControlPreviews.drawFittedText(graphics, font, toggleLabel, toggleX + 3, toggleY + 1,
                    LINK_TOGGLE_WIDTH - 6, FIELD_HEIGHT - 2, 1, TEXT_COLOR, true);
            if(inside(mouseX, mouseY, guiLeft + CONFIRM_X, guiTop + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
                graphics.fill(guiLeft + CONFIRM_X, guiTop + CONFIRM_Y, guiLeft + CONFIRM_X + CONFIRM_SIZE,
                        guiTop + CONFIRM_Y + CONFIRM_SIZE, 0x22FFFFFF);
            }
            if(openDropdown != null){
                graphics.pose().pushPose();
                try{
                    graphics.pose().translate(0, 0, 10);
                    renderDropdownOptions(graphics, openDropdown, columnX(openDropdown), mouseX, mouseY);
                }finally{
                    graphics.pose().popPose();
                }
            }
        }finally{
            scalableGui.pop(graphics);
        }
    }

    // Draw the dropdown field
    private void renderDropdownField(GuiGraphics graphics, DoubleButtonBlockEntity.ButtonHalf btn,
                                     int x, int mouseX, int mouseY) {
        int y = guiTop + FIELD_Y;
        boolean hovered = inside(mouseX, mouseY, x, y, COLUMN_WIDTH, FIELD_HEIGHT);
        DoubleButtonBlockEntity.ResponseMode mode = selectedModes.getOrDefault(
                btn, DoubleButtonBlockEntity.ResponseMode.OAK);
        DROPDOWN_BUTTON.draw(graphics, x, y);
        if(hovered || openDropdown == btn){
            graphics.fill(x + 1, y + 1, x + COLUMN_WIDTH - 1, y + FIELD_HEIGHT - 1, 0x22FFFFFF);
        }
        GuiDropdownControl.drawContents(graphics, font, Component.translatable(mode.translationKey()).getString(),
                x, y, COLUMN_WIDTH, FIELD_HEIGHT, 1, DROPDOWN_COLORS, true);
    }

    // Draw the dropdown options
    private void renderDropdownOptions(GuiGraphics graphics, DoubleButtonBlockEntity.ButtonHalf btn,
                                       int x, int mouseX, int mouseY) {
        int y = guiTop + OPTION_Y;
        DoubleButtonBlockEntity.ResponseMode selected = selectedModes.get(btn);
        for (DoubleButtonBlockEntity.ResponseMode mode : DoubleButtonBlockEntity.ResponseMode.values()) {
            boolean hovered = inside(mouseX, mouseY, x, y, COLUMN_WIDTH, OPTION_HEIGHT);
            DROPDOWN_BUTTON.draw(graphics, x, y);
            if(hovered) graphics.fill(x + 1, y + 1, x + COLUMN_WIDTH - 1, y + OPTION_HEIGHT - 1, 0x22FFFFFF);
            GuiControlPreviews.drawFittedText(graphics, font, Component.translatable(mode.translationKey()),
                    x + 3, y + 1, COLUMN_WIDTH - 6, OPTION_HEIGHT - 2, 1,
                    selected == mode ? 0xFFE5BA64 : TEXT_COLOR, true);
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
        double screenMouseX = mouseX;
        double screenMouseY = mouseY;
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        if (btn != 0) {
            return super.mouseClicked(screenMouseX, screenMouseY, btn);
        }

        if(inside(mouseX, mouseY, guiLeft + CONFIRM_X, guiTop + CONFIRM_Y, CONFIRM_SIZE, CONFIRM_SIZE)){
            onClose();
            return true;
        }
        for (DoubleButtonBlockEntity.ButtonHalf half : DoubleButtonBlockEntity.ButtonHalf.values()) {
            int x = columnX(half);
            if (inside(mouseX, mouseY, x, guiTop + FIELD_Y, COLUMN_WIDTH, FIELD_HEIGHT)) {
                openDropdown = openDropdown == half ? null : half;
                return true;
            }
        }

        if (openDropdown != null) {
            int x = columnX(openDropdown);
            int y = guiTop + OPTION_Y;
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

        int toggleX = guiLeft + LINK_TOGGLE_X;
        if (inside(mouseX, mouseY, toggleX, guiTop + LINK_TOGGLE_Y, LINK_TOGGLE_WIDTH, FIELD_HEIGHT)) {
            linkHardwareVisible = !linkHardwareVisible;
            PacketDistributor.sendToServer(new DoubleButtonAppearancePayload(target, linkHardwareVisible));
            openDropdown = null;
            return true;
        }

        openDropdown = null;
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Dismiss an expanded dropdown before closing the screen
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers){
        if(keyCode == GLFW.GLFW_KEY_ESCAPE && openDropdown != null){
            openDropdown = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Get the column x
    private int columnX(DoubleButtonBlockEntity.ButtonHalf btn) {
        return guiLeft + (btn == DoubleButtonBlockEntity.ButtonHalf.TOP ? 20 : 98);
    }

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
