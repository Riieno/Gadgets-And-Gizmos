package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AileronBearingMenu;
import com.rieno.gadgetsandgizmos.content.AnalogueJoystickMenu;
import com.rieno.gadgetsandgizmos.content.ClawMenu;
import com.rieno.gadgetsandgizmos.content.BiDirectionalGearshiftMenu;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkMenu;
import com.rieno.gadgetsandgizmos.content.NavigationTableMenu;
import com.rieno.gadgetsandgizmos.content.PortableAdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.PortableAnalogueContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.PoweredZiplineMenu;
import com.rieno.gadgetsandgizmos.content.RcsThrusterMenu;
import com.rieno.gadgetsandgizmos.content.ThrusterMenu;
import com.rieno.gadgetsandgizmos.content.VectorBearingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

// Register addon menu types
public final class CTMenuTypes {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister<MenuType<?>> REGISTRAR = DeferredRegister.create(Registries.MENU, CreateThrusters.MOD_ID);

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<AnalogueJoystickMenu>> ANALOGUE_JOYSTICK = register("analogue_joystick",
            "analogue_joystick", () -> IMenuTypeExtension.create(AnalogueJoystickMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<AnalogueContraptionControllerMenu>> ANALOGUE_CONTRAPTION_CONTROLLER = register("analogue_contraption_controller",
            "analogue_contraption_controller", () -> IMenuTypeExtension.create(AnalogueContraptionControllerMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedContraptionControllerMenu>> ADVANCED_CONTRAPTION_CONTROLLER = register("advanced_contraption_controller",
            "advanced_contraption_controller", () -> IMenuTypeExtension.create(AdvancedContraptionControllerMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<PortableAnalogueContraptionControllerMenu>> PORTABLE_CONTRAPTION_CONTROLLER = register("portable_contraption_controller",
            "portable_contraption_controller", () -> IMenuTypeExtension.create(PortableAnalogueContraptionControllerMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<PortableAdvancedContraptionControllerMenu>> ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER = register("advanced_portable_contraption_controller",
            "advanced_portable_contraption_controller", () -> IMenuTypeExtension.create(PortableAdvancedContraptionControllerMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<ThrusterMenu>> THRUSTER = register("thruster",
            "thruster", () -> IMenuTypeExtension.create(ThrusterMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<RcsThrusterMenu>> RCS_THRUSTER =
            register("rcs_thruster", "rcs_thruster",
                    () -> IMenuTypeExtension.create(RcsThrusterMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<ClawMenu>> CLAW = register("claw",
            "claw", () -> IMenuTypeExtension.create(ClawMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<GyroscopeLinkMenu>> GYROSCOPE_LINK = register("gyroscope_link",
            "gyroscope_link", () -> IMenuTypeExtension.create(GyroscopeLinkMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<BiDirectionalGearshiftMenu>> BI_DIRECTIONAL_GEARSHIFT = register("bi_directional_gearshift",
            "bi_directional_gearshift", () -> IMenuTypeExtension.create(BiDirectionalGearshiftMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<VectorBearingMenu>> VECTOR_BEARING = register("vector_bearing",
            "vector_bearing", () -> IMenuTypeExtension.create(VectorBearingMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<AileronBearingMenu>> AILERON_BEARING = register("aileron_bearing",
            "aileron_bearing", () -> IMenuTypeExtension.create(AileronBearingMenu::new));

    @Nullable
    public static final DeferredHolder<MenuType<?>, MenuType<PoweredZiplineMenu>> POWERED_ZIPLINE = register("powered_zipline",
            "powered_zipline", () -> IMenuTypeExtension.create(PoweredZiplineMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<NavigationTableMenu>> NAVIGATION_TABLE = REGISTRAR.register(
            "navigation_table", () -> IMenuTypeExtension.create(NavigationTableMenu::new));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the CT menu types
    @Nullable
    private static <T extends net.minecraft.world.inventory.AbstractContainerMenu> DeferredHolder<MenuType<?>, MenuType<T>> register(
            String featureId, String registryId, Supplier<MenuType<T>> supplier) {
                return REGISTRAR.register(registryId, supplier);
    }

    // Initialize the CT menu types
    private CTMenuTypes() {
    }
}
