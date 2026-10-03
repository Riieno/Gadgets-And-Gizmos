package com.rieno.gadgetsandgizmos.compat.rei;

import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingDisplays;
import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingPreview;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Give each thruster upgrade its own REI processing tab
public final class ThrusterReiCategory implements DisplayCategory<ThrusterReiDisplay>{
    private final ThrusterProcessingDisplays.Mode mode;
    private final CategoryIdentifier<ThrusterReiDisplay> id;

    public ThrusterReiCategory(ThrusterProcessingDisplays.Mode mode){
        this.mode = mode;
        id = CategoryIdentifier.of(mode.id());
    }

    @Override public CategoryIdentifier<ThrusterReiDisplay> getCategoryIdentifier(){ return id; }
    @Override public Component getTitle(){ return Component.translatable("createthrusters.recipe." + mode.id().getPath()); }
    @Override public Renderer getIcon(){ return EntryStacks.of(mode.icon()); }
    @Override public int getDisplayHeight(){ return ThrusterProcessingPreview.HEIGHT; }
    @Override public int getDisplayWidth(ThrusterReiDisplay display){ return ThrusterProcessingPreview.WIDTH; }

    @Override
    public List<Widget> setupDisplay(ThrusterReiDisplay display, Rectangle bounds){
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) ->
                ThrusterProcessingPreview.render(graphics, bounds.x, bounds.y)));
        widgets.add(Widgets.createSlot(new Point(bounds.x + ThrusterProcessingPreview.INPUT_X,
                        bounds.y + ThrusterProcessingPreview.INPUT_Y))
                .entries(display.getInputEntries().getFirst()).markInput());
        for(int idx = 0; idx < display.getOutputEntries().size(); idx++){
            widgets.add(Widgets.createSlot(new Point(bounds.x + ThrusterProcessingPreview.outputX(
                            display.getOutputEntries().size(), idx),
                            bounds.y + ThrusterProcessingPreview.outputY(display.getOutputEntries().size(), idx)))
                    .entries(display.getOutputEntries().get(idx)).markOutput());
        }
        return widgets;
    }
}
