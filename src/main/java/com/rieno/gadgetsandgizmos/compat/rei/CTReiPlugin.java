package com.rieno.gadgetsandgizmos.compat.rei;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.client.AdvancedContraptionControllerScreen;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.forge.REIPluginClient;

// Add the addon's REI screen exclusions
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
}
