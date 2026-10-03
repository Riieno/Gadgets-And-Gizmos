package com.rieno.gadgetsandgizmos.compat.emi;

import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingDisplays;
import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingPreview;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerProcessingRecipeViews;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

// Show an existing processing recipe through the corresponding thruster upgrade
public final class ThrusterEmiRecipe extends BasicEmiRecipe{
    private final WorkerProcessingRecipeViews.View view;

    public ThrusterEmiRecipe(EmiRecipeCategory category, ThrusterProcessingDisplays.Mode mode,
                            WorkerProcessingRecipeViews.View view){
        super(category, recipeId(mode, view), ThrusterProcessingPreview.WIDTH, ThrusterProcessingPreview.HEIGHT);
        this.view = view;
        inputs.add(EmiIngredient.of(view.input()));
        catalysts.add(EmiStack.of(CTItems.THRUSTER.get()));
        catalysts.add(EmiStack.of(mode.upgrade(1)));
        for(var output : view.outputs()){
            outputs.add(EmiStack.of(output.stack()).setChance(output.chance()));
        }
    }

    // Give each mirrored recipe a stable id under the owning mod
    private static ResourceLocation recipeId(ThrusterProcessingDisplays.Mode mode,
                                             WorkerProcessingRecipeViews.View view){
        ResourceLocation source = view.source().id();
        return ResourceLocation.fromNamespaceAndPath(mode.id().getNamespace(),
                mode.id().getPath() + "/" + source.getNamespace() + "/" + source.getPath());
    }

    @Override public RecipeHolder<?> getBackingRecipe(){ return view.source(); }

    @Override
    public void addWidgets(WidgetHolder widgets){
        widgets.addDrawable(0, 0, ThrusterProcessingPreview.WIDTH, ThrusterProcessingPreview.HEIGHT,
                (graphics, mouseX, mouseY, delta) -> ThrusterProcessingPreview.render(graphics, 0, 0));
        widgets.addSlot(inputs.getFirst(), ThrusterProcessingPreview.INPUT_X, ThrusterProcessingPreview.INPUT_Y);
        for(int idx = 0; idx < outputs.size(); idx++){
            widgets.addSlot(outputs.get(idx), ThrusterProcessingPreview.outputX(outputs.size(), idx),
                    ThrusterProcessingPreview.outputY(outputs.size(), idx)).recipeContext(this);
        }
    }
}
