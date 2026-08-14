package com.rieno.gadgetsandgizmos.compat.accessories;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.lang.reflect.Method;

// Register the portable controller Accessories slot
public final class AccessoriesCompatRegistrar {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String ACCESSORIES_CAPABILITY_CLASS = "io.wispforest.accessories.api.AccessoriesCapability";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the accessories compat registrar
    private AccessoriesCompatRegistrar() {
    }

    // Register the accessories compat registrar
    public static void register() {
        if (CTItems.PHYSICS_GOGGLES == null) {
            return;
        }

        GogglesItem.addIsWearingPredicate(AccessoriesCompatRegistrar::isWearingAccessoriesGoggles);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the player is wearing Accessories goggles
    public static boolean isWearingAccessoriesGoggles(Player player) {
        if (CTItems.PHYSICS_GOGGLES == null) {
            return false;
        }
        try {
            Class<?> capabilityClass = Class.forName(ACCESSORIES_CAPABILITY_CLASS);
            Method getMethod = capabilityClass.getMethod("get", LivingEntity.class);
            Object capability = getMethod.invoke(null, player);
            if (capability == null) {
                return false;
            }

            Method isEquippedMethod = capabilityClass.getMethod("isEquipped", Item.class);
            Object res = isEquippedMethod.invoke(capability, CTItems.PHYSICS_GOGGLES.get());
            return res instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
