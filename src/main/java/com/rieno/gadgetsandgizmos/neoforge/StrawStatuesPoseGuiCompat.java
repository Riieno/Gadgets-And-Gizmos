package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;

// Connect Straw Statues Pose GUI to its optional mod without making it a hard dependency
final class StrawStatuesPoseGuiCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final String MOD_ID = "strawstatues";
    private static final ResourceLocation STRAW_STATUE_MENU =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "straw_statue");
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolved open armor statue menu method
    private static Method openArmorStatueMenu;
    // Tracks whether tried resolve open method is set
    private static boolean triedResolveOpenMethod;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the straw statues pose gui compat
    private StrawStatuesPoseGuiCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Try to open
    static boolean tryOpen(ServerPlayer player, ArmorStand armorStand) {
        if (player == null || armorStand == null || !ModList.get().isLoaded(MOD_ID)) {
            return false;
        }

        Optional<MenuType<?>> menuType = BuiltInRegistries.MENU.getOptional(STRAW_STATUE_MENU);
        if (menuType.isEmpty()) {
            return false;
        }

        Method method = openMethod();
        if (method == null) {
            return false;
        }

        try {
            method.invoke(null, player, armorStand, menuType.get(), null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    // Open the method
    private static Method openMethod() {
        if (triedResolveOpenMethod) {
            return openArmorStatueMenu;
        }
        triedResolveOpenMethod = true;
        try {
            Class<?> helperClass = Class.forName("fuzs.statuemenus.api.v1.helper.ArmorStandInteractHelper");
            Class<?> dataProviderClass = Class.forName("fuzs.statuemenus.api.v1.world.entity.decoration.ArmorStandDataProvider");
            openArmorStatueMenu = helperClass.getMethod("openArmorStatueMenu",
                    Player.class, ArmorStand.class, MenuType.class, dataProviderClass);
        } catch (ReflectiveOperationException ignored) {
            openArmorStatueMenu = null;
        }
        return openArmorStatueMenu;
    }
}
