package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity.ControlMode;
import com.rieno.gadgetsandgizmos.content.ThrusterMenu;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterFuelTankPayload;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// Edit thruster control, fuel, exhaust and focused-beam settings
public class ThrusterConfigScreen extends AbstractSimiContainerScreen<ThrusterMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;

    private static final int BAR_TOP_OFFSET = 77;
    private static final int BAR_HEIGHT = 58;
    private static final int TRACK_FILL_TOP_OFFSET = BAR_TOP_OFFSET + 1;
    private static final int TRACK_FILL_HEIGHT = 56;
    private static final int HANDLE_W = 17;
    private static final int HANDLE_H = 6;
    private static final int LEFT_HANDLE_X_OFFSET = 60;
    private static final int RIGHT_HANDLE_X_OFFSET = 82;
    private static final int HANDLE_TOOLTIP_PADDING = 2;

    private static final int BUTTON_WIDTH = 46;
    private static final int BUTTON_HEIGHT = 11;

    private static final int TANK_X_OFFSET = 215;
    private static final int TANK_Y_OFFSET = 54;
    private static final int TANK_WIDTH = 19;
    private static final int TANK_HEIGHT = 55;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Thruster being configured
    private final ThrusterBlockEntity be;

    // Current left handle x
    private int leftHandleX;
    // Current right handle x
    private int rightHandleX;
    // Current bar top
    private int barTop;
    // Current track fill top
    private int trackFillTop;
    // Current mode button x
    private int modeButtonX;
    // Current mode button y
    private int modeButtonY;
    // Current toggle x
    private int toggleX;
    // Current toggle y
    private int toggleY;
    // Current filter button x
    private int filterButtonX;
    // Current filter button y
    private int filterButtonY;
    // Current pressure button x
    private int pressureButtonX;
    // Current pressure button y
    private int pressureButtonY;

    // Tracks whether thruster is enabled
    private boolean enabled;
    // Minimum throttle
    private float minThrottle;
    // Maximum throttle
    private float maxThrottle;
    // Current beam max opacity
    private float beamMaxOpacity;
    // Current plume color ratio
    private float plumeColorRatio;
    // Controls whether sound is filtered
    private boolean filterSound;
    // Controls whether particles are filtered
    private boolean filterParticles;
    // Controls whether damage is filtered
    private boolean filterDamage;
    // Tracks whether reject air pressure is focused
    private boolean focusedRejectAirPressure;
    // Current control mode
    private ControlMode controlMode;

    // Tracks whether the minimum handle is being dragged
    private boolean dragMin;
    // Tracks whether drag max is set
    private boolean dragMax;
    // Tracks whether drag beam is set
    private boolean dragBeam;
    // Tracks whether thruster is dirty
    private boolean dirty;
    // Current drag start mouse y
    private double dragStartMouseY;
    // Current drag start min throttle
    private float dragStartMinThrottle;
    // Current drag start max throttle
    private float dragStartMaxThrottle;
    // Current drag start beam max opacity
    private float dragStartBeamMaxOpacity;
    // Current drag start plume color ratio
    private float dragStartPlumeColorRatio;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster config
    public ThrusterConfigScreen(ThrusterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.be = menu.contentHolder;
        if (be != null) {
            this.enabled = be.isEnabled();
            this.minThrottle = be.getMinThrottle();
            this.maxThrottle = be.getMaxThrottle();
            this.beamMaxOpacity = be.getBeamMaxOpacity();
            this.plumeColorRatio = be.getPlumeColorRatio();
            this.filterSound = be.isFilterSoundEnabled();
            this.filterParticles = be.isFilterParticlesEnabled();
            this.filterDamage = be.isFilterDamageEnabled();
            this.focusedRejectAirPressure = be.isFocusedAirPressureRejectionEnabled();
            this.controlMode = be.getControlMode();
        } else {
            this.controlMode = ControlMode.AUTO;
        }
        setWindowSize(WIDTH, HEIGHT);
    }

    // Initialize the thruster config
    @Override
    protected void init() {
        super.init();

        leftHandleX = leftPos + LEFT_HANDLE_X_OFFSET;
        rightHandleX = leftPos + RIGHT_HANDLE_X_OFFSET;
        barTop = topPos + BAR_TOP_OFFSET;
        trackFillTop = topPos + TRACK_FILL_TOP_OFFSET;

        modeButtonX = leftPos + 126;
        modeButtonY = topPos + 105;
        toggleX = leftPos + 126;
        toggleY = topPos + 121;
        filterButtonX = leftPos + 126;
        filterButtonY = topPos + 137;
        pressureButtonX = leftPos + 126;
        pressureButtonY = topPos + 153;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        if (be == null) {
            return;
        }

        int x = leftPos;
        int y = topPos;
        CTCreateScreenHelper.renderThrusterGuiBackground(g, x, y);
        drawFittedCenteredString(g, title, x + 126, y + 56, 150, 0.8f,
                CTCreateScreenHelper.BANNER_TITLE_COLOR);

        drawFittedString(g, Component.literal("MIN " + percentString(minThrottle)),
                x + 125, y + 73, 52, 0.84f, CTCreateScreenHelper.VALUE_COLOR);
        drawFittedString(g, Component.literal("MAX " + percentString(maxThrottle)),
                x + 125, y + 83, 52, 0.84f, CTCreateScreenHelper.VALUE_COLOR);

        boolean overRange = isOverRangeSlider(mouseX, mouseY);
        boolean overBlend = isOverBlendSlider(mouseX, mouseY);
        boolean beamMode = isBeamMode();
        float sliderValue = beamMode ? beamMaxOpacity : plumeColorRatio;
        renderSliderIllumination(g, sliderValue);
        renderRangeHandle(g, leftHandleX, minThrottle, false, overRange && isMinHandleClosest(mouseY));
        renderRangeHandle(g, leftHandleX, maxThrottle, true, overRange && !isMinHandleClosest(mouseY));

        renderBlendHandle(g, rightHandleX, sliderValue, overBlend);

        CTCreateScreenHelper.renderTntSmallButton(g, font, modeButtonX, modeButtonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                modeLabel(controlMode), inside(mouseX, mouseY, modeButtonX, modeButtonY, BUTTON_WIDTH, BUTTON_HEIGHT), true);
        CTCreateScreenHelper.renderTntSmallButton(g, font, toggleX, toggleY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal(enabled ? "ON" : "OFF"),
                inside(mouseX, mouseY, toggleX, toggleY, BUTTON_WIDTH, BUTTON_HEIGHT), enabled);

        if (hasEffectFilter()) {
            CTCreateScreenHelper.renderTntSmallButton(g, font, filterButtonX, filterButtonY, 58, BUTTON_HEIGHT,
                    Component.translatable("createthrusters.thruster.filter.button"),
                    inside(mouseX, mouseY, filterButtonX, filterButtonY, 58, BUTTON_HEIGHT), true);
        } else {
            drawFittedString(g, Component.literal((beamMode ? "BEAM " : "PLUME ") + percentString(sliderValue)),
                    x + 126, y + 137, 58, 0.84f, CTCreateScreenHelper.LABEL_COLOR);
        }

        if (beamMode) {
            CTCreateScreenHelper.renderTntSmallButton(g, font, pressureButtonX, pressureButtonY,
                    70, BUTTON_HEIGHT,
                    Component.translatable(focusedRejectAirPressure
                            ? "createthrusters.thruster.focused_air_pressure_rejection.on"
                            : "createthrusters.thruster.focused_air_pressure_rejection.off"),
                    inside(mouseX, mouseY, pressureButtonX, pressureButtonY, 70, BUTTON_HEIGHT),
                    focusedRejectAirPressure);
        }

        if (beamMode) {
            renderEnergyTank(g, x + TANK_X_OFFSET, y + TANK_Y_OFFSET);
        } else {
            renderFuelTank(g, x + TANK_X_OFFSET, y + TANK_Y_OFFSET);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the thruster config
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);
        renderEmptySlotTooltip(g, mouseX, mouseY, mouseX, mouseY);
        renderFuelTankTooltip(g, mouseX, mouseY, mouseX, mouseY);
        renderSliderHandleTooltip(g, mouseX, mouseY);
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (btn == 1 && isOverFuelTank(mouseX, mouseY) && be != null) {
            if (!isBeamMode()) {
                PacketDistributor.sendToServer(new ThrusterFuelTankPayload(be.getBlockPos()));
            }
            return true;
        }
        if (btn == 0) {
            if (isOverRangeSlider(mouseX, mouseY)) {
                if (isMinHandleClosest(mouseY)) {
                    dragMin = true;
                } else {
                    dragMax = true;
                }
                beginSliderDrag(mouseY);
                updateDraggedValue(mouseY);
                return true;
            }
            if (isOverBlendSlider(mouseX, mouseY)) {
                dragBeam = true;
                beginSliderDrag(mouseY);
                updateBeamOpacityValue(mouseY);
                return true;
            }
            if (inside(mouseX, mouseY, modeButtonX, modeButtonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                controlMode = nextMode(controlMode);
                dirty = true;
                return true;
            }
            if (inside(mouseX, mouseY, toggleX, toggleY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                enabled = !enabled;
                dirty = true;
                return true;
            }
            if (hasEffectFilter()
                    && inside(mouseX, mouseY, filterButtonX, filterButtonY, 58, BUTTON_HEIGHT)) {
                minecraft.setScreen(new ThrusterEffectFilterScreen(this, filterSound, filterParticles, filterDamage));
                return true;
            }
            if (isBeamMode()
                    && inside(mouseX, mouseY, pressureButtonX, pressureButtonY, 70, BUTTON_HEIGHT)) {
                focusedRejectAirPressure = !focusedRejectAirPressure;
                dirty = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Handle mouse dragged
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
        if (btn == 0 && (dragMin || dragMax)) {
            updateDraggedValue(mouseY);
            return true;
        }
        if (btn == 0 && dragBeam) {
            updateBeamOpacityValue(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        boolean wasDragging = dragMin || dragMax || dragBeam;
        dragMin = false;
        dragMax = false;
        dragBeam = false;
        if (wasDragging) {
            sendUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Handle the close event
    @Override
    public void onClose() {
        sendUpdate();
        super.onClose();
    }

    // Update the dragged value
    private void updateDraggedValue(double mouseY) {
        double directPercent = percentFromMouseY(mouseY);
        double startDirectPercent = percentFromMouseY(dragStartMouseY);
        if (dragMin) {
            float percent = CTSliderInputHelper.adjustPercent(directPercent, startDirectPercent, dragStartMinThrottle);
            minThrottle = Math.min(percent, maxThrottle);
        } else if (dragMax) {
            float percent = CTSliderInputHelper.adjustPercent(directPercent, startDirectPercent, dragStartMaxThrottle);
            maxThrottle = Math.max(percent, minThrottle);
        }
        dirty = true;
    }

    // Update the beam opacity value
    private void updateBeamOpacityValue(double mouseY) {
        double directPercent = percentFromMouseY(mouseY);
        double startDirectPercent = percentFromMouseY(dragStartMouseY);
        float startValue = isBeamMode() ? dragStartBeamMaxOpacity : dragStartPlumeColorRatio;
        float val = CTSliderInputHelper.adjustPercent(directPercent, startDirectPercent, startValue);
        if (isBeamMode()) {
            beamMaxOpacity = val;
        } else {
            plumeColorRatio = val;
        }
        dirty = true;
    }

    // Begin the slider drag
    private void beginSliderDrag(double mouseY) {
        dragStartMouseY = mouseY;
        dragStartMinThrottle = minThrottle;
        dragStartMaxThrottle = maxThrottle;
        dragStartBeamMaxOpacity = beamMaxOpacity;
        dragStartPlumeColorRatio = plumeColorRatio;
    }

    // Get the percent from mouse y
    private double percentFromMouseY(double mouseY) {
        return 1.0D - Mth.clamp((mouseY - barTop) / BAR_HEIGHT, 0.0D, 1.0D);
    }

    // Get the handle center y
    private int getHandleCenterY(float percent) {
        return barTop + Math.round((1.0f - Mth.clamp(percent, 0.0f, 1.0f)) * BAR_HEIGHT);
    }

    // Get the track fill y
    private int getTrackFillY(float percent) {
        return trackFillTop + Math.round((1.0f - Mth.clamp(percent, 0.0f, 1.0f)) * TRACK_FILL_HEIGHT);
    }

    // Check if this is over the range slider
    private boolean isOverRangeSlider(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, leftHandleX - 2, barTop - 4, HANDLE_W + 4, BAR_HEIGHT + 8);
    }

    // Check if this is over the blend slider
    private boolean isOverBlendSlider(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, rightHandleX - 2, barTop - 4, HANDLE_W + 4, BAR_HEIGHT + 8);
    }

    // Check if this is over the range handle
    private boolean isOverRangeHandle(double mouseX, double mouseY, float percent) {
        return isOverHandle(mouseX, mouseY, leftHandleX, percent);
    }

    // Check if this is over the blend handle
    private boolean isOverBlendHandle(double mouseX, double mouseY, float percent) {
        return isOverHandle(mouseX, mouseY, rightHandleX, percent);
    }

    // Check if this is over the handle
    private boolean isOverHandle(double mouseX, double mouseY, int handleX, float percent) {
        int handleY = getHandleCenterY(percent) - HANDLE_H / 2;
        return inside(mouseX, mouseY,
                handleX - HANDLE_TOOLTIP_PADDING,
                handleY - HANDLE_TOOLTIP_PADDING,
                HANDLE_W + HANDLE_TOOLTIP_PADDING * 2,
                HANDLE_H + HANDLE_TOOLTIP_PADDING * 2);
    }

    // Check if the min handle is closest
    private boolean isMinHandleClosest(double mouseY) {
        int minY = getHandleCenterY(minThrottle);
        int maxY = getHandleCenterY(maxThrottle);
        double minDistance = Math.abs(mouseY - minY);
        double maxDistance = Math.abs(mouseY - maxY);
        if (Math.abs(minDistance - maxDistance) < 0.001D) {
            return mouseY >= minY;
        }
        return minDistance < maxDistance;
    }

    // Check if the pointer is over the fuel tank
    private boolean isOverFuelTank(double mouseX, double mouseY) {
        return inside(mouseX, mouseY,
                leftPos + TANK_X_OFFSET,
                topPos + TANK_Y_OFFSET,
                TANK_WIDTH,
                TANK_HEIGHT);
    }

    // Draw the range handle
    private void renderRangeHandle(GuiGraphics g, int x, float percent, boolean maxHandle, boolean hovered) {
        int handleY = getHandleCenterY(percent) - HANDLE_H / 2;
        if (maxHandle) {
            CTCreateScreenHelper.renderTntRangeMaxHandle(g, x, handleY, hovered);
        } else {
            CTCreateScreenHelper.renderTntRangeMinHandle(g, x, handleY, hovered);
        }
    }

    // Draw the slider handle tooltip
    private void renderSliderHandleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Component tooltip = sliderHandleTooltip(mouseX, mouseY);
        if (tooltip != null) {
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    // Get the slider handle tooltip
    private Component sliderHandleTooltip(int mouseX, int mouseY) {
        if (be == null) {
            return null;
        }

        boolean overMin = isOverRangeHandle(mouseX, mouseY, minThrottle);
        boolean overMax = isOverRangeHandle(mouseX, mouseY, maxThrottle);
        if (overMin && overMax) {
            return isMinHandleClosest(mouseY) ? minThrottleTooltip() : maxThrottleTooltip();
        }
        if (overMin) {
            return minThrottleTooltip();
        }
        if (overMax) {
            return maxThrottleTooltip();
        }

        float blendValue = isBeamMode() ? beamMaxOpacity : plumeColorRatio;
        if (isOverBlendHandle(mouseX, mouseY, blendValue)) {
            return Component.literal((isBeamMode() ? "Beam Opacity" : "Plume Blend")
                    + ": " + percentString(blendValue));
        }

        return null;
    }

    // Get the minimum throttle tooltip
    private Component minThrottleTooltip() {
        return Component.literal("Thrust Minimum: " + percentString(minThrottle));
    }

    // Get the maximum throttle tooltip
    private Component maxThrottleTooltip() {
        return Component.literal("Thrust Maximum: " + percentString(maxThrottle));
    }

    // Draw the slider illumination
    private void renderSliderIllumination(GuiGraphics g, float blendValue) {
        CTCreateScreenHelper.renderTntVerticalTrackFill(g, leftHandleX + HANDLE_W / 2,
                trackFillTop, TRACK_FILL_HEIGHT, getTrackFillY(maxThrottle), getTrackFillY(minThrottle));
        CTCreateScreenHelper.renderTntVerticalTrackFill(g, rightHandleX + HANDLE_W / 2,
                trackFillTop, TRACK_FILL_HEIGHT, getTrackFillY(blendValue), trackFillTop + TRACK_FILL_HEIGHT);
    }

    // Draw the blend handle
    private void renderBlendHandle(GuiGraphics g, int x, float percent, boolean hovered) {
        int handleY = getHandleCenterY(percent) - HANDLE_H / 2;
        CTCreateScreenHelper.renderTntBlendHandle(g, x, handleY, hovered);
    }

    // Check if this is a beam mode
    private boolean isBeamMode() {
        if (be == null) {
            return false;
        }
        if (be.isFocusedMode()) {
            return true;
        }
        return CTItems.THRUSTER_LENSE != null
                && menu.getSlot(ThrusterBlockEntity.SLOT_LENS).getItem().is(CTItems.THRUSTER_LENSE.get());
    }

    // Check if this has effect filter
    private boolean hasEffectFilter() {
        return menu.getSlot(ThrusterBlockEntity.SLOT_LIST).hasItem();
    }

    // Apply the effect filters
    void applyEffectFilters(boolean sound, boolean particles, boolean damage) {
        filterSound = sound;
        filterParticles = particles;
        filterDamage = damage;
        dirty = true;
    }

    // Send the update
    private void sendUpdate() {
        if (!dirty || be == null) {
            return;
        }
        dirty = false;
        PacketDistributor.sendToServer(new ThrusterConfigPayload(
            be.getBlockPos(), enabled, minThrottle, maxThrottle, controlMode,
            beamMaxOpacity, plumeColorRatio, filterSound, filterParticles, filterDamage,
            focusedRejectAirPressure));
    }

    // Draw the empty slot tooltip
    private void renderEmptySlotTooltip(GuiGraphics g, int localMouseX, int localMouseY, int screenMouseX, int screenMouseY) {
        renderEmptySlotTooltip(g, localMouseX, localMouseY, screenMouseX, screenMouseY, ThrusterBlockEntity.SLOT_UPGRADE,
                leftPos + ThrusterMenu.SLOT_UPGRADE_X, topPos + ThrusterMenu.SLOT_UPGRADE_Y,
                "createthrusters.thruster.slot.upgrade_hint");
        renderEmptySlotTooltip(g, localMouseX, localMouseY, screenMouseX, screenMouseY, ThrusterBlockEntity.SLOT_LIST,
                leftPos + ThrusterMenu.SLOT_LIST_X, topPos + ThrusterMenu.SLOT_LIST_Y,
                "createthrusters.thruster.slot.list_hint");
        renderEmptySlotTooltip(g, localMouseX, localMouseY, screenMouseX, screenMouseY, ThrusterBlockEntity.SLOT_LENS,
                leftPos + ThrusterMenu.SLOT_LENS_X, topPos + ThrusterMenu.SLOT_LENS_Y,
                "createthrusters.thruster.slot.lens_hint");
        renderEmptySlotTooltip(g, localMouseX, localMouseY, screenMouseX, screenMouseY, ThrusterBlockEntity.SLOT_SOLID_FUEL,
                leftPos + ThrusterMenu.SLOT_SOLID_FUEL_X, topPos + ThrusterMenu.SLOT_SOLID_FUEL_Y,
                "createthrusters.thruster.slot.solid_fuel_hint");
    }

    // Draw the empty slot tooltip
    private void renderEmptySlotTooltip(GuiGraphics g, int localMouseX, int localMouseY, int screenMouseX, int screenMouseY,
                                        int slotIndex, int slotX, int slotY, String translationKey) {
        if (!menu.getSlot(slotIndex).getItem().isEmpty()) {
            return;
        }
        if (!inside(localMouseX, localMouseY, slotX, slotY, 18, 18)) {
            return;
        }
        g.renderTooltip(font, Component.translatable(translationKey), screenMouseX, screenMouseY);
    }

    // Draw the fuel tank
    private void renderFuelTank(GuiGraphics g, int tankX, int tankY) {
        if (be == null) {
            return;
        }

        int innerLeft = tankX + 3;
        int innerTop = tankY + 3;
        int innerWidth = TANK_WIDTH - 6;
        int innerHeight = TANK_HEIGHT - 6;

        float ratio = 0.0f;
        FluidStack fluid = be.getFuelTank().getFluid();
        boolean renderFluidTexture = false;
        int fillColor = 0x00000000;
        if (be.hasInfiniteSolidFuel()) {
            ratio = 1.0f;
            fillColor = 0xFFFFC84A;
        } else {
            int fluidAmount = be.getFuelTank().getFluidAmount();
            int capacity = Math.max(1, be.getFuelTank().getCapacity());
            if (!fluid.isEmpty() && fluidAmount > 0) {
                ratio = Mth.clamp((float) fluidAmount / (float) capacity, 0.0f, 1.0f);
                renderFluidTexture = true;
            } else if (be.getSolidFuelTicks() > 0) {
                ratio = Mth.clamp(be.getSolidFuelTicks() / (16.0f * 60.0f * 20.0f), 0.0f, 1.0f);
                fillColor = 0xFFFF8A1F;
            } else if (!menu.getSlot(ThrusterBlockEntity.SLOT_SOLID_FUEL).getItem().isEmpty()) {
                ratio = 0.08f;
                fillColor = 0xFF8A6A2F;
            }
        }

        int fillPixels = Math.round(ratio * innerHeight);
        if (fillPixels > 0) {
            int fillTop = innerTop + innerHeight - fillPixels;
            if (renderFluidTexture) {
                renderTiledFluid(g, fluid, innerLeft, fillTop, innerWidth, fillPixels);
            } else {
                g.fill(innerLeft, fillTop, innerLeft + innerWidth, innerTop + innerHeight, fillColor);
            }
        }
    }

    // Draw the energy tank
    private void renderEnergyTank(GuiGraphics g, int tankX, int tankY) {
        if (be == null) {
            return;
        }

        int innerLeft = tankX + 3;
        int innerTop = tankY + 3;
        int innerWidth = TANK_WIDTH - 6;
        int innerHeight = TANK_HEIGHT - 6;

        IEnergyStorage energy = be.getEnergyStorage();
        int capacity = Math.max(1, energy.getMaxEnergyStored());
        float ratio = Mth.clamp((float) energy.getEnergyStored() / (float) capacity, 0.0f, 1.0f);
        g.fill(innerLeft, innerTop, innerLeft + innerWidth, innerTop + innerHeight, 0x6608172A);

        int fillPixels = Math.round(ratio * innerHeight);
        if (fillPixels <= 0) {
            return;
        }

        int fillTop = innerTop + innerHeight - fillPixels;
        g.fill(innerLeft, fillTop, innerLeft + innerWidth, innerTop + innerHeight, 0xFF1F93FF);
        g.fill(innerLeft + 2, fillTop, innerLeft + innerWidth - 2,
                Math.min(fillTop + 2, innerTop + innerHeight), 0xFF7EE6FF);
    }

    // Draw the tiled fluid
    private void renderTiledFluid(GuiGraphics g, FluidStack fluid, int x, int y, int width, int height) {
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(fluidExtensions.getStillTexture(fluid));
        int tint = fluidExtensions.getTintColor(fluid);
        float red = ((tint >> 16) & 0xFF) / 255.0f;
        float green = ((tint >> 8) & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;
        float alpha = ((tint >> 24) & 0xFF) == 0 ? 1.0f : ((tint >> 24) & 0xFF) / 255.0f;

        for (int offsetY = 0; offsetY < height; offsetY += 16) {
            int tileHeight = Math.min(16, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += 16) {
                int tileWidth = Math.min(16, width - offsetX);
                g.blit(x + offsetX, y + offsetY, 0, tileWidth, tileHeight, sprite, red, green, blue, alpha);
            }
        }
    }

    // Draw the fuel tank tooltip
    private void renderFuelTankTooltip(GuiGraphics g, int localMouseX, int localMouseY, int screenMouseX, int screenMouseY) {
        if (!isOverFuelTank(localMouseX, localMouseY)) {
            return;
        }
        if (be == null) {
            return;
        }

        List<Component> tooltip = new ArrayList<>();
        if (isBeamMode()) {
            IEnergyStorage energy = be.getEnergyStorage();
            tooltip.add(Component.literal("FE Buffer"));
            tooltip.add(Component.literal(energy.getEnergyStored() + " / "
                    + energy.getMaxEnergyStored() + " FE"));
            g.renderTooltip(font, tooltip, java.util.Optional.empty(), screenMouseX, screenMouseY);
            return;
        }

        tooltip.add(Component.translatable("createthrusters.thruster.fuel_tank"));

        FluidStack fuel = be.getFuelTank().getFluid();
        if (!fuel.isEmpty()) {
            tooltip.add(Component.literal(be.getFuelTank().getFluidAmount() + " / " + be.getFuelTank().getCapacity() + " mB"));
            tooltip.add(fuel.getHoverName());
        } else {
            tooltip.add(Component.translatable("createthrusters.thruster.fuel_tank.empty"));
        }

        if (be.hasInfiniteSolidFuel()) {
            tooltip.add(Component.translatable("createthrusters.thruster.fuel_tank.solid_infinite"));
        } else if (be.getSolidFuelTicks() > 0) {
            tooltip.add(Component.translatable("createthrusters.thruster.fuel_tank.solid_ticks", be.getSolidFuelTicks()));
        }

        ItemStack queued = menu.getSlot(ThrusterBlockEntity.SLOT_SOLID_FUEL).getItem();
        if (!queued.isEmpty()) {
            tooltip.add(Component.translatable("createthrusters.thruster.fuel_tank.solid_queue", queued.getCount(), queued.getHoverName()));
        }

        g.renderTooltip(font, tooltip, java.util.Optional.empty(), screenMouseX, screenMouseY);
    }

    // Draw the fitted string
    private void drawFittedString(GuiGraphics g, Component text, int x, int y, int maxWidth, int col) {
        drawFittedString(g, text, x, y, maxWidth, 1.0f, col);
    }

    // Draw the fitted string
    private void drawFittedString(GuiGraphics g, Component text, int x, int y,
                                  int maxWidth, float maxScale, int col) {
        float scale = textScale(text, maxWidth, maxScale);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, col, false);
        g.pose().popPose();
    }

    // Draw the fitted centered string
    private void drawFittedCenteredString(GuiGraphics g, Component text, int centerX, int y,
                                          int maxWidth, float maxScale, int col) {
        float scale = textScale(text, maxWidth, maxScale);
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawCenteredString(font, text, 0, 0, col);
        g.pose().popPose();
    }

    // Get the text scale
    private float textScale(Component text, int maxWidth, float maxScale) {
        int width = font.width(text);
        if (width <= 0) {
            return maxScale;
        }
        return Mth.clamp(maxWidth / (float) width, 0.55f, maxScale);
    }

    // Get the mode label
    private static Component modeLabel(ControlMode mode) {
        return switch (mode) {
            case AUTO -> Component.literal("Auto");
            case REDSTONE -> Component.literal("Redstone");
            case COMPUTER -> Component.literal("Computer");
        };
    }

    // Get the percent string
    private static String percentString(float val) {
        return Math.round(val * 100.0f) + "%";
    }

    // Check if the point is inside the bounds
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    // Get the next mode
    private static ControlMode nextMode(ControlMode mode) {
        return switch (mode) {
            case AUTO -> ControlMode.REDSTONE;
            case REDSTONE -> ControlMode.COMPUTER;
            case COMPUTER -> ControlMode.AUTO;
        };
    }
}
