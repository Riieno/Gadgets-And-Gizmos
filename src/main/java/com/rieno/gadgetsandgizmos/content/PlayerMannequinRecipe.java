package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTRecipeSerializers;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

// Build a mannequin result while preserving player data from its ingredients
public class PlayerMannequinRecipe extends CustomRecipe {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the player mannequin recipe
    public PlayerMannequinRecipe(CraftingBookCategory category) {
        super(category);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this matches the value
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return CTFeatureToggles.isItemEnabled("player_mannequin") && findVariant(input) != null;
    }

    // Assemble the player mannequin recipe
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        PlayerMannequinVariant variant = findVariant(input);
        return variant == null ? ItemStack.EMPTY : SupporterHeads.createStack(variant);
    }

    // Check if this can craft in dimensions
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    // Get the result item
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return SupporterHeads.createStack(PlayerMannequinVariants.byIdOrDefault(PlayerMannequinVariants.DEFAULT_ID));
    }

    // Get the serializer
    @Override
    public RecipeSerializer<?> getSerializer() {
        return CTRecipeSerializers.PLAYER_MANNEQUIN.get();
    }

    // Find the variant
    private static PlayerMannequinVariant findVariant(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }

        boolean foundHead = false;
        ItemStack nameTag = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (PlayerMannequinCrafting.isMobHead(stack) && !foundHead) {
                foundHead = true;
                continue;
            }
            if (stack.is(Items.NAME_TAG) && nameTag.isEmpty()) {
                nameTag = stack;
                continue;
            }
            return null;
        }

        if (!foundHead || nameTag.isEmpty()) {
            return null;
        }
        return PlayerMannequinCrafting.variantFromNameTag(nameTag);
    }
}
