package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// Choose which sound, particle and damage effects a thruster suppresses
public final class ThrusterEffectFilterScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 142;
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Parent thruster effect filter
    private final ThrusterConfigScreen parent;
    // Tracks whether sound is set
    private boolean sound;
    // Tracks whether particles are set
    private boolean particles;
    // Tracks whether damage is set
    private boolean damage;
    // Current sound button
    private Button soundButton;
    // Current particles button
    private Button particlesButton;
    // Current damage button
    private Button damageButton;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster effect filter
    public ThrusterEffectFilterScreen(ThrusterConfigScreen parent, boolean sound,
                                      boolean particles, boolean damage) {
        super(Component.translatable("createthrusters.thruster.filter.title"));
        this.parent = parent;
        this.sound = sound;
        this.particles = particles;
        this.damage = damage;
    }

    // Initialize the thruster effect filter
    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int buttonX = left + (PANEL_WIDTH - BUTTON_WIDTH) / 2;
        soundButton = addRenderableWidget(Button.builder(filterLabel("sound", sound), btn -> {
            sound = !sound;
            btn.setMessage(filterLabel("sound", sound));
        }).bounds(buttonX, top + 38, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        particlesButton = addRenderableWidget(Button.builder(filterLabel("particles", particles), btn -> {
            particles = !particles;
            btn.setMessage(filterLabel("particles", particles));
        }).bounds(buttonX, top + 61, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        damageButton = addRenderableWidget(Button.builder(filterLabel("damage", damage), btn -> {
            damage = !damage;
            btn.setMessage(filterLabel("damage", damage));
        }).bounds(buttonX, top + 84, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> applyAndReturn())
                .bounds(left + 70, top + 115, 70, BUTTON_HEIGHT).build());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster effect filter
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        CTCreateScreenHelper.renderPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 9,
                CTCreateScreenHelper.BANNER_TITLE_COLOR);
        graphics.drawCenteredString(font,
                Component.translatable("createthrusters.thruster.filter.checked_hint"),
                left + PANEL_WIDTH / 2, top + 24, CTCreateScreenHelper.LABEL_COLOR);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Handle the close event
    @Override
    public void onClose() {
        applyAndReturn();
    }

    // Apply and return the thruster effect filter
    private void applyAndReturn() {
        parent.applyEffectFilters(sound, particles, damage);
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // Filter the label
    private static Component filterLabel(String effect, boolean checked) {
        return Component.literal(checked ? "\u2713 " : "  ")
                .append(Component.translatable("createthrusters.thruster.filter." + effect));
    }
}
