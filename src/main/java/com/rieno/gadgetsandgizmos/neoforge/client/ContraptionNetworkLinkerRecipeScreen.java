package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.LayeredItemRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.SelectionFilter;
import com.rieno.gadgetsandgizmos.lib.client.ui.WorkerRecipeDisplay;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphTextLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// Select the recipe routes assigned to one machine area
public final class ContraptionNetworkLinkerRecipeScreen extends Screen{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PANEL_W = 285;
    private static final int PANEL_H = 266;
    private static final int LIST_X = 22;
    private static final int LIST_Y = 80;
    private static final int LIST_W = 228;
    private static final int ROW_H = 24;
    private static final int VISIBLE_ROWS = 6;
    private static final int TEXT_X = 45;
    private static final int TEXT_W = 185;
    private static final float ID_SCALE = 0.75F;
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/assign_machine_recipes.png");
    private static final GuiTextureRegion PANEL = new GuiTextureRegion(GUI_TEXTURE, 190, 192, 0, 0, 190, 177);
    private static final GuiTextureRegion SAVE = new GuiTextureRegion(GUI_TEXTURE, 190, 192, 176, 180, 14, 12);
    private static final GuiTextureRegion ROW_DIVIDER = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath(
            "createthrusters", "textures/gui/linker_gui.png"), 256, 256, 85, 242, 91, 2);
    private static final ResourceLocation CHECKED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected");
    private static final ResourceLocation CHECKED_HOVER = ResourceLocation.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private static final ResourceLocation UNCHECKED = ResourceLocation.withDefaultNamespace("widget/checkbox");
    private static final ResourceLocation UNCHECKED_HOVER = ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");

    private final Screen parent;
    private final Consumer<List<String>> save;
    private final Set<String> selected;
    private final CTScalableGui scalableGui = new CTScalableGui();
    private List<WorkerRecipeDisplay.Entry> recipes = List.of();
    private List<WorkerRecipeDisplay.Entry> filtered = List.of();
    private SelectionFilter selectionFilter = SelectionFilter.ALL;
    private EditBox search;
    private int left;
    private int top;
    private int scroll;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public ContraptionNetworkLinkerRecipeScreen(Screen parent, List<String> selected,
                                                 Consumer<List<String>> save){
        super(Component.literal("Assign Machine Recipes"));
        this.parent = parent;
        this.save = save;
        this.selected = new LinkedHashSet<>(selected);
    }

    @Override protected void init(){
        String query = search == null ? "" : search.getValue();
        int prevScroll = scroll;
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        scalableGui.update(left, top, PANEL_W, PANEL_H, width, height);
        recipes = WorkerRecipeDisplay.entries(minecraft.level);
        search = new CTScaledEditBox(scalableGui, font, left + 42, top + 49, 192, 12,
                Component.literal("Search recipes"));
        search.setBordered(false);
        search.setTextColor(0xE9F3EA);
        search.setHint(Component.literal("Search recipes"));
        search.setMaxLength(128);
        search.setResponder(this::filterRecipes);
        search.setValue(query);
        filterRecipes(query);
        scroll = Math.min(prevScroll, Math.max(0, filtered.size() - VISIBLE_ROWS));
        addRenderableWidget(search);
        addRenderableWidget(new CTScaledButton(scalableGui, left + 18, top + 246, 58, 16,
                Component.literal("Cancel"), btn -> minecraft.setScreen(parent)));
        addRenderableWidget(new CTScaledButton(scalableGui, left + 86, top + 246, 148, 16,
                filterLabel(), btn -> {
            selectionFilter = selectionFilter.next();
            btn.setMessage(filterLabel());
            filterRecipes(search.getValue());
        }));
        var saveButton = new CTScaledButton(scalableGui, left + 254, top + 245, 21, 18,
                Component.literal("Save"), btn -> onClose(), (graphics, btn, hovered, partialTick) -> {
            SAVE.draw(graphics, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
            if(hovered || btn.isFocused()){
                graphics.fill(btn.getX() + 1, btn.getY() + 1, btn.getX() + btn.getWidth() - 1,
                        btn.getY() + btn.getHeight() - 1, 0x22FFFFFF);
            }
        });
        saveButton.setTooltip(Tooltip.create(saveButton.getMessage()));
        addRenderableWidget(saveButton);
        setInitialFocus(search);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
        graphics.fill(0, 0, width, height, 0x66000000);
        double x = scalableGui.mouseX(mouseX);
        double y = scalableGui.mouseY(mouseY);
        scalableGui.push(graphics);
        try{
            PANEL.draw(graphics, left, top, PANEL_W, PANEL_H);
            graphics.drawString(font, title, left + (PANEL_W - font.width(title)) / 2, top + 6, 0x3E2D15, false);
            graphics.drawString(font, selected.size() + " recipes selected", left + LIST_X, top + 230,
                    CTCreateScreenHelper.LABEL_COLOR, false);
            int end = Math.min(filtered.size(), scroll + VISIBLE_ROWS);
            for(int idx = scroll; idx < end; idx++){
                renderRecipe(graphics, filtered.get(idx), top + LIST_Y + (idx - scroll) * ROW_H, x, y);
            }
            if(filtered.isEmpty()){
                graphics.drawString(font, "No matching recipes", left + TEXT_X, top + LIST_Y + 4, 0x909090, false);
            }
            renderScrollBar(graphics);
        }finally{
            scalableGui.pop(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Keep the world and this custom panel sharp during the normal widget pass
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick){
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int btn){
        double x = scalableGui.mouseX(mouseX);
        double y = scalableGui.mouseY(mouseY);
        if(btn == 0 && insideList(x, y)){
            int idx = scroll + (int)((y - top - LIST_Y) / ROW_H);
            if(idx < filtered.size()){
                String id = filtered.get(idx).recipeId().toString();
                if(!selected.add(id)) selected.remove(id);
                int prevScroll = scroll;
                filterRecipes(search.getValue());
                scroll = Math.min(prevScroll, Math.max(0, filtered.size() - VISIBLE_ROWS));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY){
        if(!insideList(scalableGui.mouseX(mouseX), scalableGui.mouseY(mouseY))){
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll = Math.max(0, Math.min(Math.max(0, filtered.size() - VISIBLE_ROWS),
                scroll - (int)Math.signum(scrollY)));
        return true;
    }

    @Override public void onClose(){
        save.accept(new ArrayList<>(selected));
        minecraft.setScreen(parent);
    }

    @Override public boolean isPauseScreen(){ return false; }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep the preview, display name and selection box in separate columns
    private void renderRecipe(GuiGraphics graphics, WorkerRecipeDisplay.Entry entry, int y,
                              double mouseX, double mouseY){
        boolean checked = selected.contains(entry.recipeId().toString());
        boolean hovered = mouseX >= left + LIST_X && mouseX < left + LIST_X + LIST_W
                && mouseY >= y && mouseY < y + ROW_H;
        if(checked || hovered){
            graphics.fill(left + LIST_X, y, left + LIST_X + LIST_W, y + ROW_H - 2,
                    hovered ? 0x22FFFFFF : 0x334A7A5A);
        }
        LayeredItemRenderer.renderVisible(graphics, entry.icon(), left + 24, y + 3);
        String name = GraphTextLayout.ellipsize(entry.name().getString(), font::width, TEXT_W);
        graphics.drawString(font, name, left + TEXT_X, y + 2, 0xE9F3EA, false);
        String id = GraphTextLayout.ellipsize(entry.recipeId().toString(), font::width, (int)(TEXT_W / ID_SCALE));
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(left + TEXT_X, y + 13, 0);
            graphics.pose().scale(ID_SCALE, ID_SCALE, 1);
            graphics.drawString(font, id, 0, 0, 0x909090, false);
        }finally{
            graphics.pose().popPose();
        }
        ResourceLocation checkbox = checked ? (hovered ? CHECKED_HOVER : CHECKED)
                : (hovered ? UNCHECKED_HOVER : UNCHECKED);
        graphics.blitSprite(checkbox, left + LIST_X + LIST_W - 14, y + 4, 12, 12);
        ROW_DIVIDER.draw(graphics, left + LIST_X, y + ROW_H - 2, LIST_W, 2);
    }

    private void renderScrollBar(GuiGraphics graphics){
        int maxScroll = filtered.size() - VISIBLE_ROWS;
        if(maxScroll <= 0) return;
        int height = VISIBLE_ROWS * ROW_H;
        int thumb = Math.max(10, height * VISIBLE_ROWS / filtered.size());
        int y = top + LIST_Y + (height - thumb) * scroll / maxScroll;
        graphics.fill(left + 254, y, left + 259, y + thumb, 0xFF898989);
        graphics.fill(left + 254, y, left + 255, y + thumb, 0xFFB0B0B0);
    }

    private boolean insideList(double x, double y){
        return x >= left + LIST_X && x < left + LIST_X + LIST_W
                && y >= top + LIST_Y && y < top + LIST_Y + VISIBLE_ROWS * ROW_H;
    }

    private Component filterLabel(){
        String label = switch(selectionFilter){
            case ALL -> "All";
            case SELECTED -> "Selected";
            case UNSELECTED -> "Unselected";
        };
        return Component.literal("Filter: " + label);
    }

    // Apply the selection filter alongside the name and id search
    private void filterRecipes(String query){
        filtered = recipes.stream().filter(entry -> entry.matches(query)
                && selectionFilter.matches(selected.contains(entry.recipeId().toString()))).toList();
        scroll = 0;
    }
}
