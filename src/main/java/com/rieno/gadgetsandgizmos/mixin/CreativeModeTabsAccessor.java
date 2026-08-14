package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

// Expose Creative Mode Tabs
@Mixin(CreativeModeTabs.class)
public interface CreativeModeTabsAccessor {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the cached parameters
    @Accessor("CACHED_PARAMETERS")
    static CreativeModeTab.ItemDisplayParameters createthrusters$getCachedParameters() {
        throw new UnsupportedOperationException();
    }

    // Build all creative tab contents
    @Invoker("buildAllTabContents")
    static void createthrusters$buildAllTabContents(CreativeModeTab.ItemDisplayParameters parameters) {
        throw new UnsupportedOperationException();
    }
}
