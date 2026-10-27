package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.WorldHologramProjectorBlockEntity;
import com.rieno.gadgetsandgizmos.content.WorldHologramSettings;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.CompactCheckbox;
import com.rieno.gadgetsandgizmos.lib.client.ui.InlineNumberField;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiFocusGuard;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSliderStyle;
import com.rieno.gadgetsandgizmos.lib.client.ui.SteppedSlider;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.neoforge.network.WorldHologramConfigPayload;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

// Edit projection coverage, placement and entity visibility using persistent server controls
public final class WorldHologramProjectorScreen extends AbstractSimiScreen{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/template.png");
    private static final GuiTextureRegion PANEL = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/world_hologram_projector.png"), 280, 240, 0, 0, 280, 240);
    private static final GuiTextureRegion TICK = template(228, 163, 9, 9);
    private static final CompactCheckbox.Appearance CHECKBOX = new CompactCheckbox.Appearance(template(212, 163, 9, 9), TICK, 6);
    private static final PaintedSliderStyle SLIDER = new PaintedSliderStyle(new GuiTextureRegion(
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/hologram_projector_slider_fill.png"),
            82, 8, 0, 0, 82, 8), template(104, 160, 19, 16), 6);

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private final WorldHologramProjectorBlockEntity projector;
    private final MenuConfigTarget target;
    private WorldHologramSettings selected;
    private WorldHologramSettings lastServerSettings;
    private final List<InlineNumberField> numberFields = new ArrayList<>();
    private final Map<SteppedSlider, DoubleSupplier> sliders = new LinkedHashMap<>();
    private final GuiFocusGuard focus = new GuiFocusGuard();
    private boolean dirty;
    private int ticks;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public WorldHologramProjectorScreen(WorldHologramProjectorBlockEntity projector){
        super(Component.translatable("block.createthrusters.world_hologram_projector"));
        this.projector = projector;
        target = MenuConfigTarget.of(projector.getBlockPos(), SableLevelApi.containingId(projector));
        selected = projector.settings();
        lastServerSettings = selected;
        setWindowSize(PANEL.width(), PANEL.height());
    }

    // Build controls against the current local selection so resizing preserves pending edits
    @Override
    protected void init(){
        numberFields.forEach(InlineNumberField::commit);
        numberFields.clear();
        sliders.clear();
        super.init();
        var radius = addSetting(49, 1, 9, 1, true, () -> selected.radius(),
                val -> Component.translatable("createthrusters.world_hologram.radius", (int)val),
                val -> update(new WorldHologramSettings((int)val, selected.height(), selected.size(), selected.players(), selected.hostile(), selected.passive(), selected.yOffset(), selected.ships())));
        radius.setTooltip(Tooltip.create(Component.translatable("createthrusters.world_hologram.radius.tooltip")));
        addSetting(70, 0, 32, 0.25, false, () -> selected.height(),
                val -> Component.translatable("createthrusters.world_hologram.height", decimal(val)),
                val -> update(new WorldHologramSettings(selected.radius(), (float)val, selected.size(), selected.players(), selected.hostile(), selected.passive(), selected.yOffset(), selected.ships())));
        addSetting(92, 0.5, 16, 0.25, false, () -> selected.size(),
                val -> Component.translatable("createthrusters.world_hologram.size", decimal(val)),
                val -> update(new WorldHologramSettings(selected.radius(), selected.height(), (float)val, selected.players(), selected.hostile(), selected.passive(), selected.yOffset(), selected.ships())));
        var offset = addSetting(113, -512, 512, 1, true, () -> selected.yOffset(),
                val -> offsetLabel((int)val),
                val -> update(new WorldHologramSettings(selected.radius(), selected.height(), selected.size(), selected.players(), selected.hostile(), selected.passive(), (int)val, selected.ships())));
        offset.setTooltip(Tooltip.create(Component.translatable("createthrusters.world_hologram.y_offset.tooltip")));
        int checkX = guiLeft + 18, checkWidth = 230;
        addRenderableWidget(new CompactCheckbox(checkX, guiTop + 141, checkWidth, 9, Component.translatable("createthrusters.world_hologram.players"), font,
                () -> selected.players(), val -> update(new WorldHologramSettings(selected.radius(), selected.height(), selected.size(), val, selected.hostile(), selected.passive(), selected.yOffset(), selected.ships())), CHECKBOX));
        addRenderableWidget(new CompactCheckbox(checkX, guiTop + 160, checkWidth, 9, Component.translatable("createthrusters.world_hologram.hostile"), font,
                () -> selected.hostile(), val -> update(new WorldHologramSettings(selected.radius(), selected.height(), selected.size(), selected.players(), val, selected.passive(), selected.yOffset(), selected.ships())), CHECKBOX));
        addRenderableWidget(new CompactCheckbox(checkX, guiTop + 178, checkWidth, 9, Component.translatable("createthrusters.world_hologram.passive"), font,
                () -> selected.passive(), val -> update(new WorldHologramSettings(selected.radius(), selected.height(), selected.size(), selected.players(), selected.hostile(), val, selected.yOffset(), selected.ships())), CHECKBOX));
        var ships = addRenderableWidget(new CompactCheckbox(checkX, guiTop + 195, checkWidth, 9, Component.translatable("createthrusters.world_hologram.ships"), font,
                () -> selected.ships(), val -> update(new WorldHologramSettings(selected.radius(), selected.height(), selected.size(), selected.players(), selected.hostile(), selected.passive(), selected.yOffset(), val)), CHECKBOX));
        ships.setTooltip(Tooltip.create(Component.translatable("createthrusters.world_hologram.ships.tooltip")));
        var done = addRenderableWidget(new Button(guiLeft + 263, guiTop + 228, 9, 9,
                Component.translatable("gui.done"), btn -> onClose(), supplier -> supplier.get()){
            @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
                TICK.draw(graphics, getX(), getY());
                if(isHoveredOrFocused()) graphics.fill(getX() + 1, getY() + 1, getX() + 8, getY() + 8, 0x33FFFFFF);
            }
        });
        done.setTooltip(Tooltip.create(done.getMessage()));
    }

    private static GuiTextureRegion template(int u, int v, int width, int height){
        return new GuiTextureRegion(TEMPLATE, 508, 268, u, v, width, height);
    }

    // Reuse the same setting callback for dragging and precise inline entry
    private SteppedSlider addSetting(int y, double min, double max, double step, boolean wholeNumbers,
                                     DoubleSupplier val, DoubleFunction<Component> label, DoubleConsumer changed){
        var slider = addRenderableWidget(new SteppedSlider(guiLeft + 175, guiTop + y + 1, 82, 16,
                min, max, step, val.getAsDouble(), label, changed, SLIDER));
        sliders.put(slider, val);
        slider.synchronize(val.getAsDouble());
        var field = addRenderableWidget(new InlineNumberField(guiLeft + 14, guiTop + y, 143, 17, font, 19,
                min, max, wholeNumbers, val, label, WorldHologramProjectorScreen::decimal, changed));
        field.setTooltip(Tooltip.create(Component.translatable("createthrusters.world_hologram.inline_edit")));
        numberFields.add(field);
        return slider;
    }

    private static String decimal(double val){ return new BigDecimal(Float.toString((float)val)).stripTrailingZeros().toPlainString(); }
    private static Component offsetLabel(int val){
        return val == 0 ? Component.translatable("createthrusters.world_hologram.y_offset.surface")
                : Component.translatable("createthrusters.world_hologram.y_offset", val);
    }
    private void update(WorldHologramSettings val){
        if(selected.equals(val)) return;
        selected = val;
        dirty = true;
        synchronizeSliders();
    }

    private void synchronizeSliders(){ sliders.forEach((slider, val) -> slider.synchronize(val.getAsDouble())); }

    @Override public void setFocused(GuiEventListener next){
        if(focus.update(next)) super.setFocused(next);
    }

    // Commit a draft before another control acts and let Escape cancel typing before the screen closes
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button){
        if(getFocused() instanceof InlineNumberField field && !field.isMouseOver(mouseX, mouseY)) setFocused(null);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers){
        if(getFocused() instanceof InlineNumberField field && field.editing() && field.keyPressed(key, scanCode, modifiers)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
    private void send(){
        if(!dirty) return;
        PacketDistributor.sendToServer(new WorldHologramConfigPayload(target, selected));
        dirty = false;
    }

    // Throttle dragged settings while still applying the final value when the screen closes
    @Override
    public void tick(){
        super.tick();
        if(projector.isRemoved() || minecraft.player == null || !projector.canView(minecraft.player, 8)){ onClose(); return; }
        WorldHologramSettings current = projector.settings();
        if(!current.equals(lastServerSettings)){
            if(dirty){
                selected = new WorldHologramSettings(
                        selected.radius() == lastServerSettings.radius() ? current.radius() : selected.radius(),
                        selected.height() == lastServerSettings.height() ? current.height() : selected.height(),
                        selected.size() == lastServerSettings.size() ? current.size() : selected.size(),
                        selected.players() == lastServerSettings.players() ? current.players() : selected.players(),
                        selected.hostile() == lastServerSettings.hostile() ? current.hostile() : selected.hostile(),
                        selected.passive() == lastServerSettings.passive() ? current.passive() : selected.passive(),
                        selected.yOffset() == lastServerSettings.yOffset() ? current.yOffset() : selected.yOffset(),
                        selected.ships() == lastServerSettings.ships() ? current.ships() : selected.ships());
            }else selected = current;
            synchronizeSliders();
            lastServerSettings = current;
        }
        if(++ticks % 4 == 0) send();
    }
    @Override public void removed(){ numberFields.forEach(InlineNumberField::commit); send(); super.removed(); }
    @Override public boolean isPauseScreen(){ return false; }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks){
        PANEL.draw(graphics, guiLeft, guiTop);
        graphics.drawCenteredString(font, title, guiLeft + windowWidth / 2, guiTop + 3, 0xFFFFFF);
    }
}
