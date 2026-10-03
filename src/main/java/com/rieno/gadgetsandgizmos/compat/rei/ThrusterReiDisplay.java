package com.rieno.gadgetsandgizmos.compat.rei;

import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingDisplays;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProcessingRecipeViews;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

// Present one live thruster processing recipe in REI
public final class ThrusterReiDisplay extends BasicDisplay{
    private final CategoryIdentifier<ThrusterReiDisplay> category;

    public ThrusterReiDisplay(ThrusterProcessingDisplays.Mode mode, WorkerProcessingRecipeViews.View view){
        super(List.of(EntryIngredients.ofIngredient(view.input())), outputs(view),
                Optional.of(displayId(mode, view)));
        category = CategoryIdentifier.of(mode.id());
    }

    // Preserve every result and its processing chance
    private static List<EntryIngredient> outputs(WorkerProcessingRecipeViews.View view){
        return view.outputs().stream().map(output -> {
            var stack = EntryStacks.of(output.stack());
            if(output.chance() < 1.0F){
                stack = stack.tooltip(Component.translatable("createthrusters.recipe.output_chance",
                        Math.round(output.chance() * 100.0F)));
            }
            return EntryIngredient.of(stack);
        }).toList();
    }

    private static ResourceLocation displayId(ThrusterProcessingDisplays.Mode mode,
                                              WorkerProcessingRecipeViews.View view){
        ResourceLocation source = view.source().id();
        return ResourceLocation.fromNamespaceAndPath(mode.id().getNamespace(),
                mode.id().getPath() + "/" + source.getNamespace() + "/" + source.getPath());
    }

    @Override public CategoryIdentifier<ThrusterReiDisplay> getCategoryIdentifier(){ return category; }
}
