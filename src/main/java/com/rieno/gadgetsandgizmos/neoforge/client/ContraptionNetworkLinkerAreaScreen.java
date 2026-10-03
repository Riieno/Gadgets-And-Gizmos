package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerAreaConfigPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

// Name a completed SCM area and optionally bind it to one recipe sequence
public final class ContraptionNetworkLinkerAreaScreen extends Screen{
    private static final int PANEL_W = 292;
    private static final int PANEL_H = 112;
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
        name = new CTScaledEditBox(scalableGui, font, x + 12, y + 29, PANEL_W - 24, 16,
                Component.literal("Machine name"));
        name.setMaxLength(64);
        name.setValue(area.label());
        addRenderableWidget(name);
        recipe = new CTScaledEditBox(scalableGui, font, x + 12, y + 63, PANEL_W - 24, 16,
                Component.literal("Recipe sequence ID"));
        recipe.setMaxLength(128);
        recipe.setValue(area.recipeId());
        recipe.setResponder(value -> invalidRecipe = false);
        addRenderableWidget(recipe);
        addRenderableWidget(new CTScaledButton(scalableGui, x + 12, y + PANEL_H - 21, 62, 16,
                Component.literal("Later"), btn -> onClose()));
        addRenderableWidget(new CTScaledButton(scalableGui, x + PANEL_W - 74, y + PANEL_H - 21,
                62, 16, Component.literal("Save"), btn -> save()));
        setInitialFocus(name);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0x66000000);
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;
        scalableGui.push(graphics);
        try{
            CTCreateScreenHelper.renderPanel(graphics, x, y, PANEL_W, PANEL_H);
            graphics.drawCenteredString(font, title, x + PANEL_W / 2, y + 7,
                    CTCreateScreenHelper.BANNER_TITLE_COLOR);
            graphics.drawString(font, "Machine name", x + 12, y + 19,
                    CTCreateScreenHelper.LABEL_COLOR, false);
            graphics.drawString(font, "Recipe sequence ID (optional)", x + 12, y + 52,
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

    private void save(){
        String selected = recipe.getValue().trim();
        invalidRecipe = !selected.isBlank() && ResourceLocation.tryParse(selected) == null;
        if(invalidRecipe) return;
        PacketDistributor.sendToServer(new ContraptionNetworkLinkerAreaConfigPayload(area.hand(), area.areaId(),
                name.getValue().trim(), selected, false));
        onClose();
    }
}
