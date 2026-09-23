package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// Upgrade one fresh Thruster to an ion thruster by installing a Lens
public class IonThrusterRecipe extends CustomRecipe {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ion thruster recipe
    public IonThrusterRecipe(CraftingBookCategory category) {
        super(category);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check whether this has exactly one fresh Thruster and one Lens
    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!CTFeatureToggles.isItemEnabled("thruster") || CTItems.THRUSTER_LENSE == null || input.ingredientCount() != 2) {
            return false;
        }

        boolean foundThruster = false;
        boolean foundLens = false;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (IonThrusterStacks.isUnmodifiedThruster(stack) && !foundThruster) {
                foundThruster = true;
                continue;
            }
            if (stack.is(CTItems.THRUSTER_LENSE.get()) && !foundLens) {
                foundLens = true;
                continue;
            }
            return false;
        }
        return foundThruster && foundLens;
    }

    // Assemble the pre-equipped ion thruster
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return IonThrusterStacks.create();
    }

    // Check whether this fits in the crafting grid
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    // Describe the ingredients for the recipe book and JEI
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        if (CTItems.THRUSTER != null) {
            ingredients.add(Ingredient.of(CTItems.THRUSTER.get()));
        }
        if (CTItems.THRUSTER_LENSE != null) {
            ingredients.add(Ingredient.of(CTItems.THRUSTER_LENSE.get()));
        }
        return ingredients;
    }

    // Get the JEI and recipe-book result
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return IonThrusterStacks.create();
    }

    // Get the serializer
    @Override
    public RecipeSerializer<?> getSerializer() {
        return CTRecipeSerializers.ION_THRUSTER.get();
    }
}
