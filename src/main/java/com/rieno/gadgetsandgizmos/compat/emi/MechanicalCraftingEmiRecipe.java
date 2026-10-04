package com.rieno.gadgetsandgizmos.compat.emi;

import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;

// Display an addon mechanical crafting recipe at its actual crafter size
public final class MechanicalCraftingEmiRecipe extends BasicEmiRecipe {
    private final RecipeHolder<MechanicalCraftingRecipe> source;
    private final int columns;

    public MechanicalCraftingEmiRecipe(EmiRecipeCategory category,
                                       RecipeHolder<MechanicalCraftingRecipe> source,
                                       HolderLookup.Provider registries) {
        super(category, source.id(), source.value().getWidth() * 18 + 50,
                Math.max(54, source.value().getHeight() * 18));
        this.source = source;
        columns = source.value().getWidth();
        source.value().getIngredients().forEach(ingredient -> inputs.add(EmiIngredient.of(ingredient)));
        outputs.add(EmiStack.of(source.value().getResultItem(registries)));
    }

    @Override
    public RecipeHolder<?> getBackingRecipe() {
        return source;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int idx = 0; idx < inputs.size(); idx++) {
            widgets.addSlot(inputs.get(idx), idx % columns * 18, idx / columns * 18);
        }
        widgets.addSlot(outputs.getFirst(), columns * 18 + 24, (height - 18) / 2)
                .large(true).recipeContext(this);
    }
}
