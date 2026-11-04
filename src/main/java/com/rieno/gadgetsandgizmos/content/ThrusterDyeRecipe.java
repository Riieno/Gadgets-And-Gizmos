package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.DyeColorRecipe;
import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Optional;

// Turn crafting-grid dye slots into an already paid applicator preset
public final class ThrusterDyeRecipe extends CustomRecipe{
    public ThrusterDyeRecipe(CraftingBookCategory category){ super(category); }

    private static Optional<ColorEffectPreset> preset(CraftingInput input){
        if(!CTFeatureToggles.isItemEnabled("thruster_dye") || input.ingredientCount() < 2) return Optional.empty();
        boolean linker = false, chest = false, glow = false;
        var colors = new ArrayList<Integer>();
        for(int slot = 0; slot < input.size(); slot++){
            var stack = input.getItem(slot);
            if(stack.isEmpty()) continue;
            if(stack.is(CTItems.CONTRAPTION_NETWORK_LINKER.get())){
                if(linker) return Optional.empty();
                linker = true;
            }else if(stack.is(Items.CHEST)){
                if(chest) return Optional.empty();
                chest = true;
            }else if(stack.is(Items.GLOW_INK_SAC)){
                if(glow) return Optional.empty();
                glow = true;
            }else{
                var dye = DyeColorRecipe.colorOf(stack);
                if(dye == null || colors.size() >= ColorEffectPreset.MAX_STOPS) return Optional.empty();
                colors.add(dye.getTextColor());
            }
        }
        return linker && chest ? Optional.of(ColorEffectPreset.fromColors(colors, glow)) : Optional.empty();
    }

    @Override public boolean matches(CraftingInput input, Level level){ return preset(input).isPresent(); }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries){
        var preset = preset(input);
        if(preset.isEmpty()) return ItemStack.EMPTY;
        var output = new ItemStack(CTItems.THRUSTER_DYE.get());
        ThrusterDyeData.write(output, new PaletteDyeStorage(), preset.get());
        return output;
    }

    @Override public boolean canCraftInDimensions(int width, int height){ return width > 0 && height > 0 && width * height >= 2; }

    // Keep the base conversion visible while allowing optional dyes and glow ink in the grid
    @Override public boolean isSpecial(){ return false; }

    @Override public NonNullList<Ingredient> getIngredients(){
        return NonNullList.of(Ingredient.EMPTY, Ingredient.of(CTItems.CONTRAPTION_NETWORK_LINKER.get()), Ingredient.of(Items.CHEST));
    }

    @Override public ItemStack getResultItem(HolderLookup.Provider registries){ return new ItemStack(CTItems.THRUSTER_DYE.get()); }

    @Override public RecipeSerializer<?> getSerializer(){ return CTRecipeSerializers.THRUSTER_DYE.get(); }
}
