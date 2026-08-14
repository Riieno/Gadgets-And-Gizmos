package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingAutoRefuelSettings;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingAutoRefuelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Edit Shipping Auto Refuel settings
public class ShippingAutoRefuelScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Hand
    private final InteractionHand hand;
    // Tracks whether shipping auto refuel is enabled
    private boolean enabled;
    // Current threshold
    private EditBox threshold;
    // Current dock
    private EditBox dock;
    // Current toggle
    private Button toggle;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping auto refuel
    private ShippingAutoRefuelScreen(InteractionHand hand, ShippingAutoRefuelSettings settings) {
        super(Component.literal("Shipping Schedule - Auto Refuel"));
        this.hand = hand;
        enabled = settings.enabled();
        initialThreshold = Integer.toString(settings.thresholdPercent());
        initialDock = settings.dockFilter();
    }

    // Initial threshold
    private final String initialThreshold;
    // Initial dock
    private final String initialDock;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the shipping auto refuel
    public static void open(InteractionHand hand, ItemStack stack) {
        Minecraft.getInstance().setScreen(new ShippingAutoRefuelScreen(
                hand, ShippingAutoRefuelSettings.read(stack)));
    }

    // Initialize the shipping auto refuel
    @Override
    protected void init() {
        int x = width / 2 - 110;
        int y = height / 2 - 64;
        toggle = addRenderableWidget(Button.builder(toggleLabel(), btn -> {
            enabled = !enabled;
            toggle.setMessage(toggleLabel());
        }).bounds(x, y, 220, 20).build());
        threshold = new EditBox(font, x, y + 38, 70, 20, Component.literal("Fuel amount"));
        threshold.setValue(initialThreshold);
        threshold.setFilter(val -> val.isEmpty() || val.chars().allMatch(Character::isDigit));
        addRenderableWidget(threshold);
        dock = new EditBox(font, x + 78, y + 38, 142, 20, Component.literal("Dock"));
        dock.setValue(initialDock);
        dock.setMaxLength(32);
        addRenderableWidget(dock);
        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(x, y + 76, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(x + 114, y + 76, 106, 20).build());
    }

    // Build the auto-refuel toggle label
    private Component toggleLabel() {
        return Component.literal("Auto Refuel: " + (enabled ? "ON" : "OFF"));
    }

    // Save the shipping auto refuel
    private void save() {
        int amount;
        try {
            amount = Integer.parseInt(threshold.getValue());
        } catch (NumberFormatException ignored) {
            amount = 25;
        }
        PacketDistributor.sendToServer(new ShippingAutoRefuelPayload(
                hand, enabled, amount, dock.getValue()));
        onClose();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the shipping auto refuel
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 91, 0xFFFFFFFF);
        graphics.drawString(font, "Fuel %", width / 2 - 110, height / 2 - 42, 0xFFB7C7D9, false);
        graphics.drawString(font, "Dock filter (* supported)", width / 2 - 32, height / 2 - 42, 0xFFB7C7D9, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
