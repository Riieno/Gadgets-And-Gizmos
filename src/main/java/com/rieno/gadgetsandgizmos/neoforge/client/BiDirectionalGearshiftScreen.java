package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftBlockEntity;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftMenu;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.BiDirectionalGearshiftConfigPayload;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

// Edit Bidirectional Gearshift settings
public class BiDirectionalGearshiftScreen extends AbstractSimiContainerScreen<BiDirectionalGearshiftMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int TITLE_CENTER_X = 128;
    private static final int TITLE_Y = 31;
    private static final int SECONDARY_MODE_X = 63;
    private static final int SECONDARY_LOCK_X = 94;
    private static final int PRIMARY_MODE_X = 148;
    private static final int PRIMARY_LOCK_X = 179;
    private static final int TOP_BUTTON_Y = 48;
    private static final int TOP_BUTTON_SIZE = 13;
    private static final int ICON_TEX_SIZE = 256;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Scalable GUI
    private final CTScalableGui scalableGui = new CTScalableGui();
    // Current primary mode
    private BiDirectionalGearshiftBlockEntity.AxisControlMode primaryMode;
    // Current secondary mode
    private BiDirectionalGearshiftBlockEntity.AxisControlMode secondaryMode;
    // Local mode
    private BiDirectionalGearshiftBlockEntity.LocalControlMode localMode;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the bi directional gearshift
    public BiDirectionalGearshiftScreen(BiDirectionalGearshiftMenu menu, Inventory playerInventory,
                                        Component title) {
        super(menu, playerInventory, title);
        primaryMode = menu.getAxisMode(BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY);
        secondaryMode = menu.getAxisMode(BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY);
        localMode = menu.getLocalMode();
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the bi directional gearshift
    @Override
    protected void init() {
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        mouseX = scalableGui.mouseX(mouseX);
        mouseY = scalableGui.mouseY(mouseY);
        scalableGui.push(guiGraphics);
        try {
            int x = leftPos;
            int y = topPos;
            CTCreateScreenHelper.renderBiDirectionalGearshiftGuiBackground(guiGraphics, x, y);
            guiGraphics.drawCenteredString(font, title, x + TITLE_CENTER_X, y + TITLE_Y,
                    CTCreateScreenHelper.BANNER_TITLE_COLOR);
            drawAxisControls(guiGraphics, x, y, mouseX, mouseY,
                    BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY, secondaryMode);
            drawAxisControls(guiGraphics, x, y, mouseX, mouseY,
                    BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY, primaryMode);
        } finally {
            scalableGui.pop(guiGraphics);
        }
    }

    // Draw the axis controls
    private void drawAxisControls(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                                  BiDirectionalGearshiftBlockEntity.AxisRole role,
                                  BiDirectionalGearshiftBlockEntity.AxisControlMode mode) {
        int modeX = x + modeButtonX(role);
        int lockX = x + lockButtonX(role);
        int buttonY = y + TOP_BUTTON_Y;
        drawIconButton(graphics, modeX, buttonY, iconForMode(mode),
                inside(mouseX, mouseY, modeX, buttonY, TOP_BUTTON_SIZE, TOP_BUTTON_SIZE));
        drawIconButton(graphics, lockX, buttonY, iconForLocalState(role),
                inside(mouseX, mouseY, lockX, buttonY, TOP_BUTTON_SIZE, TOP_BUTTON_SIZE));
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
        if (btn == 0) {
            if (clickMode(mouseX, mouseY, BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY)) {
                return true;
            }
            if (clickMode(mouseX, mouseY, BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY)) {
                return true;
            }
            if (clickLocalLock(mouseX, mouseY, BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY)) {
                return true;
            }
            if (clickLocalLock(mouseX, mouseY, BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY)) {
                return true;
            }
        }
        return super.mouseClicked(screenMouseX, screenMouseY, btn);
    }

    // Handle the mode click
    private boolean clickMode(double mouseX, double mouseY, BiDirectionalGearshiftBlockEntity.AxisRole role) {
        if (!inside(mouseX, mouseY, leftPos + modeButtonX(role), topPos + TOP_BUTTON_Y,
                TOP_BUTTON_SIZE, TOP_BUTTON_SIZE)) {
            return false;
        }
        if (role == BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY) {
            primaryMode = toggle(primaryMode);
        } else {
            secondaryMode = toggle(secondaryMode);
        }
        sendConfig();
        return true;
    }

    // Handle the local lock click
    private boolean clickLocalLock(double mouseX, double mouseY, BiDirectionalGearshiftBlockEntity.AxisRole role) {
        if (!inside(mouseX, mouseY, leftPos + lockButtonX(role), topPos + TOP_BUTTON_Y,
                TOP_BUTTON_SIZE, TOP_BUTTON_SIZE)) {
            return false;
        }
        BiDirectionalGearshiftBlockEntity.LocalControlMode nextMode = toggledLocalMode(role);
        if (nextMode != localMode) {
            localMode = nextMode;
            sendConfig();
        }
        return true;
    }

    // Toggle an axis control mode
    private BiDirectionalGearshiftBlockEntity.AxisControlMode toggle(
            BiDirectionalGearshiftBlockEntity.AxisControlMode mode) {
        return mode == BiDirectionalGearshiftBlockEntity.AxisControlMode.DIRECTIONAL
                ? BiDirectionalGearshiftBlockEntity.AxisControlMode.PASSTHROUGH
                : BiDirectionalGearshiftBlockEntity.AxisControlMode.DIRECTIONAL;
    }

    // Send the config
    private void sendConfig() {
        PacketDistributor.sendToServer(new BiDirectionalGearshiftConfigPayload(
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()),
                primaryMode,
                secondaryMode,
                localMode));
    }

    // Draw the icon button
    private void drawIconButton(GuiGraphics graphics, int x, int y, GearshiftIcon icon, boolean hovered) {
        int iconX = x + (TOP_BUTTON_SIZE - icon.width) / 2;
        int iconY = y + (TOP_BUTTON_SIZE - icon.height) / 2;
        graphics.blit(CTCreateScreenHelper.TNT_GUI_SPRITES, iconX, iconY, icon.width, icon.height,
                icon.sourceX, icon.sourceY, icon.width, icon.height, ICON_TEX_SIZE, ICON_TEX_SIZE);
        if (hovered) {
            graphics.fill(x, y, x + TOP_BUTTON_SIZE, y + TOP_BUTTON_SIZE, 0x22FFFFFF);
        }
    }

    // Get the icon for mode
    private GearshiftIcon iconForMode(BiDirectionalGearshiftBlockEntity.AxisControlMode mode) {
        return mode == BiDirectionalGearshiftBlockEntity.AxisControlMode.DIRECTIONAL
                ? GearshiftIcon.DIRECTIONAL
                : GearshiftIcon.PASSTHROUGH;
    }

    // Get the icon for local state
    private GearshiftIcon iconForLocalState(BiDirectionalGearshiftBlockEntity.AxisRole role) {
        return isLocalEnabled(role) ? GearshiftIcon.UNLOCKED : GearshiftIcon.LOCKED;
    }

    // Check if the local is enabled
    private boolean isLocalEnabled(BiDirectionalGearshiftBlockEntity.AxisRole role) {
        return localMode == BiDirectionalGearshiftBlockEntity.LocalControlMode.BOTH
                || localMode == localModeFor(role);
    }

    // Get the toggled local control mode
    private BiDirectionalGearshiftBlockEntity.LocalControlMode toggledLocalMode(
            BiDirectionalGearshiftBlockEntity.AxisRole role) {
        BiDirectionalGearshiftBlockEntity.LocalControlMode roleMode = localModeFor(role);
        BiDirectionalGearshiftBlockEntity.LocalControlMode otherMode = localModeFor(otherRole(role));
        if (localMode == BiDirectionalGearshiftBlockEntity.LocalControlMode.BOTH) {
            return otherMode;
        }
        if (localMode == roleMode) {
            return roleMode;
        }
        return BiDirectionalGearshiftBlockEntity.LocalControlMode.BOTH;
    }

    // Get the mode button x
    private int modeButtonX(BiDirectionalGearshiftBlockEntity.AxisRole role) {
        return role == BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY ? PRIMARY_MODE_X : SECONDARY_MODE_X;
    }

    // Get the lock button x
    private int lockButtonX(BiDirectionalGearshiftBlockEntity.AxisRole role) {
        return role == BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY ? PRIMARY_LOCK_X : SECONDARY_LOCK_X;
    }

    // Get the other role
    private BiDirectionalGearshiftBlockEntity.AxisRole otherRole(
            BiDirectionalGearshiftBlockEntity.AxisRole role) {
        return role == BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY
                ? BiDirectionalGearshiftBlockEntity.AxisRole.SECONDARY
                : BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY;
    }

    // Get the local mode
    private BiDirectionalGearshiftBlockEntity.LocalControlMode localModeFor(
            BiDirectionalGearshiftBlockEntity.AxisRole role) {
        return role == BiDirectionalGearshiftBlockEntity.AxisRole.PRIMARY
                ? BiDirectionalGearshiftBlockEntity.LocalControlMode.PRIMARY_AXIS
                : BiDirectionalGearshiftBlockEntity.LocalControlMode.SECONDARY_AXIS;
    }

    // Define the gearshift icon values
    private enum GearshiftIcon {
        UNLOCKED(156, 50, 11, 11),
        LOCKED(171, 51, 9, 10),
        DIRECTIONAL(185, 56, 9, 5),
        PASSTHROUGH(200, 54, 11, 5);

        // Source x
        private final int sourceX;
        // Source y
        private final int sourceY;
        // Gearshift icon width
        private final int width;
        // Gearshift icon height
        private final int height;

        // Initialize the gearshift icon
        GearshiftIcon(int sourceX, int sourceY, int width, int height) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.width = width;
            this.height = height;
        }
    }

    // Get the extra areas
    @Override
    public List<Rect2i> getExtraAreas() {
        return scalableGui.extraAreas();
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

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
