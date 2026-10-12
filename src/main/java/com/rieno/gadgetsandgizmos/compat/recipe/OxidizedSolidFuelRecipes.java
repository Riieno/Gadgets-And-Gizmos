package com.rieno.gadgetsandgizmos.compat.recipe;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.lib.create.ProcessingTransformationRecipes;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;

// Let finished-fuel searches show compacting and the required cooling step.
public final class OxidizedSolidFuelRecipes{
    public static final ResourceLocation CATEGORY = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "compacting_and_cooling");
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(
            CreateThrusters.MOD_ID, "scorching_oxidized_solid_fuel");

    private OxidizedSolidFuelRecipes(){}

    public static boolean enabled(){
        return CTFeatureToggles.isItemEnabled("oxidized_solid_fuel")
                && CTFeatureToggles.isItemEnabled("scorching_oxidized_solid_fuel");
    }

    public static Component title(){
        return Component.translatable("createthrusters.recipe.compacting_and_cooling");
    }

    public static ItemStack icon(){
        return new ItemStack(CTItems.OXIDIZED_SOLID_FUEL.get());
    }

    public static List<ItemStack> workstations(){
        return List.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:mechanical_press"))),
                new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:basin"))));
    }

    public static List<ProcessingTransformationRecipes.View> views(RecipeManager manager){
        if(manager == null || !enabled()) return List.of();
        return manager.byKey(SOURCE).flatMap(source -> ProcessingTransformationRecipes.create(
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "cooled_oxidized_solid_fuel"),
                source, Component.translatable("createthrusters.recipe.press_and_basin"),
                Component.translatable("createthrusters.recipe.fuel_cooling",
                        CTItems.SCORCHING_OXIDIZED_SOLID_FUEL.get().durationTicks() / 20)))
                .filter(view -> view.result().is(CTItems.OXIDIZED_SOLID_FUEL.get()))
                .map(List::of).orElseGet(List::of);
    }
}
