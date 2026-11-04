package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.compat.create.AccDisplayTarget;
import com.rieno.gadgetsandgizmos.compat.create.AdvancedControllerDisplayTarget;
import com.rieno.gadgetsandgizmos.compat.create.UniversalDisplayAdapterTarget;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

// Register Create display targets
public final class CTDisplayTargets {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<DisplayTarget> REGISTRAR =
            DeferredRegister.create(CreateRegistries.DISPLAY_TARGET, CreateThrusters.MOD_ID);

    public static final DeferredHolder<DisplayTarget, AdvancedControllerDisplayTarget> ADVANCED_CONTROLLER =
            REGISTRAR.register("advanced_contraption_controller", AdvancedControllerDisplayTarget::new);

    @Nullable
    public static final DeferredHolder<DisplayTarget, AccDisplayTarget> ACC_DISPLAY =
            CTBlockEntities.ACC_DISPLAY == null ? null
                    : REGISTRAR.register("acc_display", AccDisplayTarget::new);

    @Nullable
    public static final DeferredHolder<DisplayTarget, UniversalDisplayAdapterTarget> UNIVERSAL_DISPLAY_ADAPTER =
            CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER == null ? null
                    : REGISTRAR.register("universal_display_adapter", UniversalDisplayAdapterTarget::new);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT display targets
    private CTDisplayTargets() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the common setup event
    public static void onCommonSetup(FMLCommonSetupEvent evt) {
        evt.enqueueWork(() -> {
            DisplayTarget.BY_BLOCK_ENTITY.register(CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get(), ADVANCED_CONTROLLER.get());
            if (CTBlockEntities.ACC_DISPLAY != null && ACC_DISPLAY != null) {
                DisplayTarget.BY_BLOCK_ENTITY.register(
                        CTBlockEntities.ACC_DISPLAY.get(), ACC_DISPLAY.get());
            }
            if (CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER != null && UNIVERSAL_DISPLAY_ADAPTER != null) {
                DisplayTarget.BY_BLOCK_ENTITY.register(
                        CTBlockEntities.UNIVERSAL_DISPLAY_ADAPTER.get(), UNIVERSAL_DISPLAY_ADAPTER.get());
            }
        });
    }
}
