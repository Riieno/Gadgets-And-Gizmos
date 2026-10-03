package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

// Select the recipe routes assigned to one machine area
public final class ContraptionNetworkLinkerRecipeScreen extends Screen{
    private static final int PANEL_W = 330;
    private static final int PANEL_H = 224;
    private final Screen parent;
    private final Consumer<List<String>> save;
    private final Set<String> selected;
    private final CTScalableGui scalableGui = new CTScalableGui();
    private List<String> recipes = List.of();
    private EditBox search;
    private int left;
    private int top;
    private int scroll;

    public ContraptionNetworkLinkerRecipeScreen(Screen parent, List<String> selected,
                                                 Consumer<List<String>> save){
        super(Component.literal("Assign machine recipes"));
        this.parent = parent;
        this.save = save;
        this.selected = new LinkedHashSet<>(selected);
    }

    @Override protected void init(){
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        scalableGui.update(left, top, PANEL_W, PANEL_H, width, height);
        if(minecraft.level != null){
            recipes = WorkerRecipeCatalog.index(minecraft.level).definitions().stream()
                    .map(def -> def.recipeId().toString()).distinct().sorted().toList();
        }
        search = new CTScaledEditBox(scalableGui, font, left + 12, top + 32, PANEL_W - 24, 16,
                Component.literal("Search recipes"));
        search.setMaxLength(128);
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);
        addRenderableWidget(new CTScaledButton(scalableGui, left + 12, top + PANEL_H - 23, 58, 16,
                Component.literal("Cancel"), button -> minecraft.setScreen(parent)));
        addRenderableWidget(new CTScaledButton(scalableGui, left + PANEL_W - 70, top + PANEL_H - 23,
                58, 16, Component.literal("Save"), button -> onClose()));
        setInitialFocus(search);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0x66000000);
        scalableGui.push(graphics);
        try{
            CTCreateScreenHelper.renderPanel(graphics, left, top, PANEL_W, PANEL_H);
            graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 8,
                    CTCreateScreenHelper.BANNER_TITLE_COLOR);
            CTCreateScreenHelper.renderInset(graphics, left + 10, top + 54, PANEL_W - 20, 138, false, false);
            graphics.drawString(font, selected.size() + " recipes selected", left + 13, top + 193,
                    CTCreateScreenHelper.LABEL_COLOR, false);
            List<String> filtered = filteredRecipes();
            int end = Math.min(filtered.size(), scroll + 11);
            for(int idx = scroll; idx < end; idx++){
                String id = filtered.get(idx);
                int y = top + 58 + (idx - scroll) * 12;
                if(selected.contains(id)) graphics.fill(left + 13, y - 1, left + PANEL_W - 14, y + 10, 0x664A7A5A);
                graphics.drawString(font, (selected.contains(id) ? "[x] " : "[ ] ") + id,
                        left + 16, y, CTCreateScreenHelper.BANNER_TITLE_COLOR, false);
            }
        }finally{
            scalableGui.pop(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Keep the world and this custom panel sharp during the normal widget pass
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button){
        double x = scalableGui.mouseX(mouseX);
        double y = scalableGui.mouseY(mouseY);
        if(button == 0 && x >= left + 10 && x < left + PANEL_W - 10 && y >= top + 54 && y < top + 186){
            int idx = scroll + (int)((y - top - 54) / 12);
            List<String> filtered = filteredRecipes();
            if(idx >= 0 && idx < filtered.size()){
                String id = filtered.get(idx);
                if(!selected.add(id)) selected.remove(id);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        scroll = Math.max(0, Math.min(Math.max(0, filteredRecipes().size() - 11),
                scroll - (int)Math.signum(scrollY)));
        return true;
    }

    @Override public void onClose(){
        save.accept(new ArrayList<>(selected));
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen(){ return false; }

    private List<String> filteredRecipes(){
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? recipes : recipes.stream().filter(id -> id.contains(query)).toList();
    }
}
