package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.IonThrusterRecipe;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// Register addon recipe serializers
public final class CTRecipeSerializers {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<RecipeSerializer<?>> REGISTRAR =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateThrusters.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PlayerMannequinRecipe>> PLAYER_MANNEQUIN =
            REGISTRAR.register("player_mannequin", () -> new SimpleCraftingRecipeSerializer<>(PlayerMannequinRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<IonThrusterRecipe>> ION_THRUSTER =
            REGISTRAR.register("ion_thruster", () -> new SimpleCraftingRecipeSerializer<>(IonThrusterRecipe::new));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT recipe serializers
    private CTRecipeSerializers() {
    }
}
