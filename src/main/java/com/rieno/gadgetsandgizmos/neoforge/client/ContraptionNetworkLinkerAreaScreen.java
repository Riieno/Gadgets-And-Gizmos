package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerAreaConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

// Name a completed SCM area and optionally bind it to one recipe sequence
public final class ContraptionNetworkLinkerAreaScreen extends Screen{
    private static final int PANEL_W = 285;
    private static final int PANEL_H = 185;
    private static final GuiTextureRegion PANEL = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/worker_area_naming.png"), 190, 123, 0, 0, 190, 123);
    private final ContraptionNetworkLinkerAreaConfigPayload area;
    private final CTScalableGui scalableGui = new CTScalableGui();
    private EditBox name;
    private EditBox recipe;
    private boolean invalidRecipe;

    public ContraptionNetworkLinkerAreaScreen(ContraptionNetworkLinkerAreaConfigPayload area){
        super(Component.literal("Configure Machine Area"));
        this.area = area;
    }

    @Override protected void init(){
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.update(x, y, PANEL_W, PANEL_H, width, height);
        name = new CTScaledEditBox(scalableGui, font, x + 39, y + 64, 194, 12,
                Component.literal("Machine name"));
        name.setBordered(false);
        name.setTextColor(0xE9F3EA);
        name.setMaxLength(64);
        name.setValue(area.label());
        addRenderableWidget(name);
        recipe = new CTScaledEditBox(scalableGui, font, x + 39, y + 120, 194, 12,
                Component.literal("Recipe sequence ID"));
        recipe.setBordered(false);
        recipe.setTextColor(0xE9F3EA);
        recipe.setMaxLength(128);
        recipe.setValue(area.recipeId());
        recipe.setResponder(value -> invalidRecipe = false);
        addRenderableWidget(recipe);
        addFooterButton(x + 36, y + 166, Component.literal("Later"), this::onClose);
        addFooterButton(x + PANEL_W - 98, y + 166, Component.literal("Save"), this::save);
        setInitialFocus(name);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0x66000000);
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.push(graphics);
        try{
            PANEL.draw(graphics, x, y, PANEL_W, PANEL_H);
            graphics.drawString(font, title, x + (PANEL_W - font.width(title)) / 2, y + 6,
                    0x3E2D15, false);
            graphics.drawString(font, "Machine name", x + 36, y + 42,
                    CTCreateScreenHelper.LABEL_COLOR, false);
            graphics.drawString(font, "Recipe sequence ID (optional)", x + 36, y + 97,
                    invalidRecipe ? 0xFF7777 : CTCreateScreenHelper.LABEL_COLOR, false);
        }finally{
            scalableGui.pop(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Keep the world and this custom panel sharp during the normal widget pass
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
    }

    @Override public boolean isPauseScreen(){ return false; }

    // Draw the actions inside the painted footer
    private void addFooterButton(int x, int y, Component text, Runnable onPress){
        addRenderableWidget(new CTScaledButton(scalableGui, x, y, 62, 14, text,
                btn -> onPress.run(), (graphics, btn, hovered, partialTick) -> {
            CTCreateScreenHelper.renderTextButton(graphics, font, btn.getX(), btn.getY(),
                    btn.getWidth(), btn.getHeight(), btn.getMessage(), hovered || btn.isFocused(), btn.active, false);
        }));
    }

    private void save(){
        String selected = recipe.getValue().trim();
        invalidRecipe = !selected.isBlank() && ResourceLocation.tryParse(selected) == null;
        if(invalidRecipe) return;
        PacketDistributor.sendToServer(new ContraptionNetworkLinkerAreaConfigPayload(area.hand(), area.areaId(),
                name.getValue().trim(), selected, false));
        onClose();
    }
}
