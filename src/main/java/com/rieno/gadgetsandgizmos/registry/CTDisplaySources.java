package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.create.AdvancedContraptionControllerDisplaySource;
import com.rieno.gadgetsandgizmos.compat.create.AdvancedNavigationTableDisplaySource;
import com.rieno.gadgetsandgizmos.compat.create.BiDirectionalGearboxDisplaySource;
import com.rieno.gadgetsandgizmos.compat.create.GyroscopeLinkDisplaySource;
import com.rieno.gadgetsandgizmos.compat.create.ShipDockDisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

// Register Create display sources
public final class CTDisplaySources {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<DisplaySource> REGISTRAR = DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE,
            CreateThrusters.MOD_ID);

    @Nullable
    public static final DeferredHolder<DisplaySource, GyroscopeLinkDisplaySource> GYROSCOPE_LINK =
            REGISTRAR.register("gyroscope_link", GyroscopeLinkDisplaySource::new);

    @Nullable
    public static final DeferredHolder<DisplaySource, BiDirectionalGearboxDisplaySource> BIDIRECTIONAL_GEARBOX =
            REGISTRAR.register("bidirectional_gearbox", BiDirectionalGearboxDisplaySource::new);

    @Nullable
    public static final DeferredHolder<DisplaySource, AdvancedContraptionControllerDisplaySource> ADVANCED_CONTRAPTION_CONTROLLER_OUTPUT =
            REGISTRAR.register("advanced_contraption_controller_output", AdvancedContraptionControllerDisplaySource::new);

    @Nullable
    public static final DeferredHolder<DisplaySource, AdvancedNavigationTableDisplaySource> ADVANCED_NAVIGATION_TABLE_DATA =
            REGISTRAR.register("advanced_navigation_table_data", AdvancedNavigationTableDisplaySource::new);

    @Nullable
    public static final DeferredHolder<DisplaySource, ShipDockDisplaySource> SHIP_DOCK_SHIPPING =
            REGISTRAR.register("ship_dock_shipping", ShipDockDisplaySource::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT display sources
    private CTDisplaySources() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the common setup event
    public static void onCommonSetup(FMLCommonSetupEvent evt) {
        evt.enqueueWork(() -> {
            if (CTBlockEntities.GYROSCOPE_LINK != null && GYROSCOPE_LINK != null) {
                DisplaySource.BY_BLOCK_ENTITY.add(CTBlockEntities.GYROSCOPE_LINK.get(), GYROSCOPE_LINK.get());
            }
            if (CTBlockEntities.BIDIRECTIONAL_GEARBOX != null && BIDIRECTIONAL_GEARBOX != null) {
                DisplaySource.BY_BLOCK_ENTITY.add(CTBlockEntities.BIDIRECTIONAL_GEARBOX.get(), BIDIRECTIONAL_GEARBOX.get());
            }
            if (CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER != null && ADVANCED_CONTRAPTION_CONTROLLER_OUTPUT != null) {
                DisplaySource.BY_BLOCK_ENTITY.add(CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get(),
                        ADVANCED_CONTRAPTION_CONTROLLER_OUTPUT.get());
            }
            if (CTBlockEntities.ADVANCED_NAVIGATION_TABLE != null && ADVANCED_NAVIGATION_TABLE_DATA != null) {
                DisplaySource.BY_BLOCK_ENTITY.add(CTBlockEntities.ADVANCED_NAVIGATION_TABLE.get(),
                        ADVANCED_NAVIGATION_TABLE_DATA.get());
            }
            if (CTBlockEntities.SHIP_DOCK != null && SHIP_DOCK_SHIPPING != null) {
                DisplaySource.BY_BLOCK_ENTITY.add(CTBlockEntities.SHIP_DOCK.get(),
                        SHIP_DOCK_SHIPPING.get());
            }
        });
    }
}
