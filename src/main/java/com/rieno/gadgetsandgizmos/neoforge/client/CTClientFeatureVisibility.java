package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.mixin.CreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

// Hide disabled client features
public final class CTClientFeatureVisibility {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client feature visibility
    private CTClientFeatureVisibility() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Refresh the CT client feature visibility
    public static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isRunning()) {
            return;
        }

        minecraft.execute(() -> {
            CreativeModeTab.ItemDisplayParameters parameters =
                    CreativeModeTabsAccessor.createthrusters$getCachedParameters();
            if (parameters != null) {
                CreativeModeTabsAccessor.createthrusters$buildAllTabContents(parameters);
            }

            invokeOptionalRefresh("jei", "com.rieno.gadgetsandgizmos.compat.jei.CTJeiPlugin");
            invokeOptionalRefresh("emi", "com.rieno.gadgetsandgizmos.compat.emi.CTEmiPlugin");
        });
    }

    // Run the optional refresh
    private static void invokeOptionalRefresh(String modId, String className) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            Class<?> type = Class.forName(className, true, CTClientFeatureVisibility.class.getClassLoader());
            Method method = type.getMethod("refreshHiddenItemsFromCurrentToggles");
            method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
