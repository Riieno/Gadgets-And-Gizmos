package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.ThrusterDyeMenu;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorPickerModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.CompactCheckbox;
import com.rieno.gadgetsandgizmos.lib.client.ui.GuiRenderLayer;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorStopEditor;
import com.rieno.gadgetsandgizmos.lib.color.DyeColorRecipe;
import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterDyePresetPayload;
import com.rieno.gadgetsandgizmos.lib.client.ui.ColorGradientEditor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// Use only the main panel and detached tick sprites from the supplied atlas
public final class ThrusterDyeScreen extends AbstractContainerScreen<ThrusterDyeMenu>{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/thruster_dye.png");
    private static final GuiTextureRegion PLAYER_INVENTORY = new GuiTextureRegion(CTCreateScreenHelper.SPRITE_MAP_TEX, 298, 328, 8, 217, 176, 108);
    private static final int BAR_X = 149, BAR_Y = 71, BAR_WIDTH = 138, BAR_HEIGHT = 52;
    private final CTScalableGui scale = new CTScalableGui();
    public static final GuiTextureRegion GRADIENT_HANDLE = new GuiTextureRegion(TEXTURE, 370, 220, 358, 206, 12, 14);
    public static final GuiTextureRegion GRADIENT_SELECTED_HANDLE = new GuiTextureRegion(TEXTURE, 370, 220, 344, 206, 12, 14);
    private final ColorStopEditor editor;
    private final ColorGradientEditor gradient;
    private ColorPickerModal picker;
    private CompactCheckbox animatedToggle;
    private CompactCheckbox additiveToggle;
    private int glowCost;
    private ColorEffectPreset costPreset, paidPreset;
    private int[] cost = new int[16];
    private int requiredScroll;

    public ThrusterDyeScreen(ThrusterDyeMenu menu, Inventory inventory, Component title){
        super(menu, inventory, title);
        editor = new ColorStopEditor(menu.preset());
        gradient = new ColorGradientEditor(editor);
        imageWidth = 340;
        imageHeight = ThrusterDyeMenu.PLAYER_INVENTORY_Y + PLAYER_INVENTORY.height();
    }

    @Override protected void init(){
        super.init();
        animatedToggle = addRenderableWidget(new CompactCheckbox(leftPos + 246, topPos + 31, 68, 12,
                text("animated"), font, () -> editor.preset().animated(), editor::animated));
        additiveToggle = addRenderableWidget(new CompactCheckbox(leftPos + 129, topPos + 31, 114, 12,
                text("additive"), font, () -> editor.preset().additive(), editor::additive));
        additiveToggle.setTooltip(Tooltip.create(text("additive.tooltip")));
        scale.update(leftPos, topPos, imageWidth, imageHeight, width, height);
    }

    private static Component text(String id){ return Component.translatable("createthrusters.thruster_dye." + id); }

    private void updateCost(){
        if(editor.preset().equals(costPreset) && menu.preset().equals(paidPreset)) return;
        costPreset = editor.preset(); paidPreset = menu.preset();
        cost = DyeColorRecipe.additional(paidPreset, costPreset);
        glowCost = DyeColorRecipe.additionalGlowInkSacs(paidPreset, costPreset);
    }

    private boolean canConfirm(){
        if(menu.ignoresDyeRequirements() || editor.preset().equals(menu.preset())) return true;
        return PaletteDyeStorage.canPay(cost, glowCost, menu::count);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY){
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, 332, 220, 370, 220);
        graphics.blit(TEXTURE, leftPos + 332, topPos + 196, 332, 196, 8, 19, 370, 220);
        graphics.drawCenteredString(font, title.getString().toUpperCase(java.util.Locale.ROOT), leftPos + 166, topPos + 3, 0xFFFFFF);
        PLAYER_INVENTORY.draw(graphics, leftPos + ThrusterDyeMenu.PLAYER_INVENTORY_X, topPos + ThrusterDyeMenu.PLAYER_INVENTORY_Y);
        graphics.drawString(font, playerInventoryTitle, leftPos + ThrusterDyeMenu.PLAYER_INVENTORY_X + 8,
                topPos + ThrusterDyeMenu.PLAYER_INVENTORY_Y + 6, 0x404040, false);
        button(graphics, mouseX, mouseY, 28, 104, 58, 18, "reset", false);
        for(int idx = 0; idx < 3; idx++) button(graphics, mouseX, mouseY, 131 + idx * 62, 162, 58, 18,
                "mode." + ColorEffectPreset.Mode.values()[idx + 1].name().toLowerCase(java.util.Locale.ROOT), idx == 0 ? !editor.preset().usesStops() : editor.preset().mode().ordinal() == idx + 1);
        gradient.bounds(leftPos + BAR_X, topPos + BAR_Y, BAR_WIDTH, BAR_HEIGHT);
        gradient.render(graphics, GRADIENT_HANDLE, GRADIENT_SELECTED_HANDLE);
        if(editor.preset().usesStops()){
            button(graphics, mouseX, mouseY, 146, 49, 69, 16, "add_step", false);
            button(graphics, mouseX, mouseY, 219, 49, 69, 16, "remove_step", false);
        }
        graphics.drawCenteredString(font, "\u2713", leftPos + 315, topPos + 202, canConfirm() ? 0xFFFFFF : 0xAA6666);
        requirements(graphics);
    }

    private void requirements(GuiGraphics graphics){
        if(menu.ignoresDyeRequirements()){
            graphics.drawWordWrap(font, text("creative"), leftPos + 9, topPos + 31, 96, 0xAADD99);
            graphics.drawString(font, text("creative"), leftPos + 7, topPos + 198, 0x404040, false);
            return;
        }
        graphics.enableScissor(leftPos + 7, topPos + 29, leftPos + 108, topPos + 101);
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 9, topPos + 31, 0);
        graphics.pose().scale(.65F, .65F, 1);
        var rows = new java.util.ArrayList<Component>();
        var missing = new java.util.ArrayList<Boolean>();
        for(int idx = 0; idx < 16; idx++){
            int needed = cost[idx];
            if(needed == 0) continue;
            String name = new ItemStack(DyeItem.byColor(DyeColor.byId(idx))).getHoverName().getString();
            rows.add(Component.literal(name + " x" + needed));
            missing.add(menu.count(idx) < needed);
        }
        if(glowCost > 0){
            rows.add(Component.literal(new ItemStack(PaletteDyeStorage.slotItem(PaletteDyeStorage.GLOW_SLOT)).getHoverName().getString() + " x" + glowCost));
            missing.add(menu.count(PaletteDyeStorage.GLOW_SLOT) < glowCost);
        }
        requiredScroll = Math.clamp(requiredScroll, 0, Math.max(0, rows.size() - 8));
        graphics.drawString(font, text("required"), 0, 0, 0xEACB76, false);
        if(rows.isEmpty()) graphics.drawString(font, text("no_cost"), 0, 10, 0xAADD99, false);
        for(int idx = 0; idx < Math.min(8, rows.size() - requiredScroll); idx++){
            graphics.drawString(font, rows.get(requiredScroll + idx), 0, 12 + idx * 11,
                    missing.get(requiredScroll + idx) ? 0xFF6666 : 0xDCDCDC, false);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        graphics.drawString(font, font.plainSubstrByWidth(text("reserve").getString(), 294), leftPos + 7, topPos + 198, 0x404040, false);
    }

    private void button(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, String label, boolean selected){
        if(selected || inside(mouseX, mouseY, x, y, width, height)) graphics.fill(leftPos + x + 1, topPos + y + 1,
                leftPos + x + width - 1, topPos + y + height - 1, selected ? 0x886D5418 : 0x33555555);
        String value = text(label).getString();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + x + width / 2F, topPos + y + (height - 8) / 2F, 0);
        float factor = Math.min(1, (width - 4) / (float)Math.max(1, font.width(value)));
        graphics.pose().scale(factor, factor, 1);
        graphics.drawCenteredString(font, value, 0, 0, selected ? 0xFFE09A : 0xFFFFFF);
        graphics.pose().popPose();
    }

    @Override protected void renderLabels(GuiGraphics graphics, int x, int y){}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial){
        updateCost();
        animatedToggle.visible = editor.preset().mode() == ColorEffectPreset.Mode.GRADIENT;
        animatedToggle.active = animatedToggle.visible;
        int x = scale.mouseX(mouseX), y = scale.mouseY(mouseY);
        scale.push(graphics);
        super.render(graphics, x, y, partial);
        for(int idx = 0; idx < PaletteDyeStorage.SLOTS; idx++){
            int count = menu.count(idx);
            if(count == 0){
                graphics.renderItem(new ItemStack(PaletteDyeStorage.slotItem(idx)), leftPos + ThrusterDyeMenu.dyeX(idx), topPos + ThrusterDyeMenu.dyeY(idx));
                graphics.fill(leftPos + ThrusterDyeMenu.dyeX(idx), topPos + ThrusterDyeMenu.dyeY(idx), leftPos + ThrusterDyeMenu.dyeX(idx) + 16, topPos + ThrusterDyeMenu.dyeY(idx) + 16, 0x99808080);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + ThrusterDyeMenu.dyeX(idx) + 16, topPos + ThrusterDyeMenu.dyeY(idx) + 10, 300);
            graphics.pose().scale(.75F, .75F, 1);
            String amount = Integer.toString(count);
            int needed = idx < PaletteDyeStorage.GLOW_SLOT ? cost[idx] : glowCost;
            graphics.drawString(font, amount, -font.width(amount), 0,
                    count < needed && !menu.ignoresDyeRequirements() ? 0xFF7777 : 0xFFFFFF, true);
            graphics.pose().popPose();
        }
        if(picker == null || !picker.isOpen()){
            renderTooltip(graphics, x, y);
            if(editor.preset().usesStops() && gradient.contains(x, y)) graphics.renderTooltip(font,
                    Component.literal("Ctrl+click: add step | Shift+click step: remove | Double-click step: color"), x, y);
        }
        scale.pop(graphics);
        if(picker != null && picker.isOpen()){
            try(var layer = GuiRenderLayer.push(graphics, 1000)){
                picker.renderTextured(graphics, font, width, height, CTColorPickerAppearance.DEFAULT, mouseX, mouseY);
            }
        }
    }

    private boolean inside(double x, double y, int left, int top, int width, int height){
        return x >= leftPos + left && x < leftPos + left + width && y >= topPos + top && y < topPos + top + height;
    }

    private void pickColor(){
        var originalMode = editor.preset().mode();
        int initial = !editor.preset().usesStops() ? editor.preset().color() : editor.preset().stops().get(editor.selected()).color();
        picker = new ColorPickerModal(0xFF000000 | initial, editor::color, () -> picker = null, () -> {
            editor.mode(originalMode);
            picker = null;
        });
    }

    @Override public boolean mouseClicked(double x, double y, int button){
        if(picker != null && picker.isOpen()) return picker.mouseClicked(x, y, button);
        x = scale.mouseX(x); y = scale.mouseY(y);
        if(button == 0){
            if(inside(x, y, 28, 104, 58, 18)){
                editor.reset();
                PacketDistributor.sendToServer(new ThrusterDyePresetPayload(menu.containerId, editor.preset()));
                return true;
            }
            if(inside(x, y, 303, 195, 37, 25)){
                updateCost();
                if(canConfirm()){ PacketDistributor.sendToServer(new ThrusterDyePresetPayload(menu.containerId, editor.preset())); onClose(); }
                return true;
            }
            for(int idx = 0; idx < 3; idx++) if(inside(x, y, 131 + idx * 62, 162, 58, 18)){
                editor.mode(ColorEffectPreset.Mode.values()[idx + 1]); return true;
            }
            if(gradient.mouseClicked(x, y, button, hasControlDown(), hasShiftDown(), this::pickColor)) return true;
            if(editor.preset().usesStops()){
                if(inside(x, y, 146, 49, 69, 16)){ editor.add(); return true; }
                if(inside(x, y, 219, 49, 69, 16)){ editor.remove(); return true; }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy){
        if(picker != null && picker.isOpen()) return picker.mouseDragged(x, y, button);
        if(gradient.dragging()){ gradient.mouseDragged(scale.mouseX(x)); return true; }
        return super.mouseDragged(scale.mouseX(x), scale.mouseY(y), button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button){
        if(picker != null && picker.isOpen()) return picker.mouseReleased(x, y, button);
        gradient.mouseReleased();
        return super.mouseReleased(scale.mouseX(x), scale.mouseY(y), button);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy){
        if(picker != null && picker.isOpen()) return true;
        if(inside(scale.mouseX(x), scale.mouseY(y), 7, 29, 101, 72)){
            requiredScroll = Math.max(0, requiredScroll - (int)Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers){
        return picker != null && picker.isOpen() ? picker.keyPressed(key, scan, modifiers) : super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char value, int modifiers){
        return picker != null && picker.isOpen() ? picker.charTyped(value, modifiers) : super.charTyped(value, modifiers);
    }
}
