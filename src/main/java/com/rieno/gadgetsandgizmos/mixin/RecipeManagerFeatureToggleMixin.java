package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// Hide recipes for disabled addon features
@Mixin(RecipeManager.class)
public class RecipeManagerFeatureToggleMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Filter the recipe match
    @Inject(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void createthrusters$filterRecipeMatch(
            RecipeType<T> recipeType,
            I input,
            Level level,
            @Nullable RecipeHolder<T> lastRecipe,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> res = cir.getReturnValue();
        if (res.isPresent() && isDisabledOutput(res.get(), level)) {
            RecipeManager recipeManager = (RecipeManager) (Object) this;
            cir.setReturnValue(recipeManager.getRecipesFor(recipeType, input, level).stream().findFirst());
        }
    }

    // Filter the recipe matches
    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void createthrusters$filterRecipeMatches(
            RecipeType<T> recipeType,
            I input,
            Level level,
            CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        List<RecipeHolder<T>> filtered = createthrusters$mutableList(cir.getReturnValue().stream()
                .filter(recipe -> !isDisabledOutput(recipe, level)));
        cir.setReturnValue(filtered);
    }

    // Get the mutable list
    @Unique
    private static <T> List<T> createthrusters$mutableList(Stream<T> values) {
        return values.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    // Check if this is a disabled output
    private static boolean isDisabledOutput(RecipeHolder<?> recipe, Level level) {
        ItemStack res = recipe.value().getResultItem(level.registryAccess());
        if (res.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(res.getItem());
        return id != null
                && CreateThrusters.MOD_ID.equals(id.getNamespace())
                && !CTFeatureToggles.isItemEnabled(id.getPath());
    }
}
