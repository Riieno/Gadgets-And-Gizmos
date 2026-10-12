package com.rieno.gadgetsandgizmos.compat.emi;

import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;

// Preserve the addon constructor for callers using the original recipe display
@Deprecated
public final class MechanicalCraftingEmiRecipe
        extends com.rieno.gadgetsandgizmos.lib.compat.emi.MechanicalCraftingEmiRecipe{
    public MechanicalCraftingEmiRecipe(EmiRecipeCategory category,
                                      RecipeHolder<MechanicalCraftingRecipe> source,
                                      HolderLookup.Provider registries){
        super(category, source, registries);
    }
}
