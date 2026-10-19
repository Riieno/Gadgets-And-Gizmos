package com.rieno.gadgetsandgizmos.compat.rei;

import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;

import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingDisplays;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import com.rieno.gadgetsandgizmos.compat.recipe.OxidizedSolidFuelRecipes;
import com.rieno.gadgetsandgizmos.lib.compat.rei.ProcessingTransformationReiCategory;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.forge.REIPluginClient;

// Add the addon's REI screen exclusions
@REIPluginClient
public class CTReiPlugin implements REIClientPlugin {
    @Override
    public void registerCategories(CategoryRegistry registry){
        if(OxidizedSolidFuelRecipes.enabled()){
            var category = new ProcessingTransformationReiCategory(OxidizedSolidFuelRecipes.CATEGORY,
                    OxidizedSolidFuelRecipes.title(), OxidizedSolidFuelRecipes.icon());
            registry.add(category);
            for(var workstation : OxidizedSolidFuelRecipes.workstations()){
                registry.addWorkstations(category.getCategoryIdentifier(), EntryStacks.of(workstation));
            }
        }
        if(!CTFeatureToggles.isItemEnabled("thruster")) return;
        for(ThrusterProcessingDisplays.Mode mode : ThrusterProcessingDisplays.Mode.values()){
            ThrusterReiCategory category = new ThrusterReiCategory(mode);
            registry.add(category);
            for(var catalyst : mode.catalysts()){
                registry.addWorkstations(category.getCategoryIdentifier(), EntryStacks.of(catalyst));
            }
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry){
        if(Minecraft.getInstance().level != null){
            for(var view : OxidizedSolidFuelRecipes.views(Minecraft.getInstance().level.getRecipeManager())){
                registry.add(new ProcessingTransformationReiCategory.Display(OxidizedSolidFuelRecipes.CATEGORY, view));
            }
        }
        if(!CTFeatureToggles.isItemEnabled("thruster") || Minecraft.getInstance().level == null) return;
        var level = Minecraft.getInstance().level;
        for(ThrusterProcessingDisplays.Mode mode : ThrusterProcessingDisplays.Mode.values()){
            for(var view : mode.recipes(level)){
                registry.add(new ThrusterReiDisplay(mode, view));
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the exclusion zones
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(AdvancedContraptionControllerScreen.class, screen ->
                screen.getRecipeViewerExclusionAreas().stream()
                        .map(area -> new Rectangle(area.getX(), area.getY(),
                                area.getWidth(), area.getHeight()))
                        .toList());
    }
}
