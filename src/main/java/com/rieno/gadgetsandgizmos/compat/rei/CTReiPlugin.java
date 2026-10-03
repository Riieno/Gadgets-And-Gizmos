package com.rieno.gadgetsandgizmos.compat.rei;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.recipe.ThrusterProcessingDisplays;
import com.rieno.gadgetsandgizmos.content.WorkerEnergyBatteryItem;
import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;

// Register the addon's REI displays and screen exclusions
@REIPluginClient
public class CTReiPlugin implements REIClientPlugin {
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

    // Remove internal worker presentation items from REI selection
    @Override
    public void registerEntries(EntryRegistry registry) {
        registry.removeEntryIf(entry -> entry.getValue() instanceof net.minecraft.world.item.ItemStack stack
                && WorkerEnergyBatteryItem.isInternal(stack));
    }

    @Override
    public void registerCategories(CategoryRegistry registry){
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
        if(!CTFeatureToggles.isItemEnabled("thruster") || Minecraft.getInstance().level == null) return;
        var level = Minecraft.getInstance().level;
        for(ThrusterProcessingDisplays.Mode mode : ThrusterProcessingDisplays.Mode.values()){
            for(var view : mode.recipes(level)){
                registry.add(new ThrusterReiDisplay(mode, view));
            }
        }
    }
}
