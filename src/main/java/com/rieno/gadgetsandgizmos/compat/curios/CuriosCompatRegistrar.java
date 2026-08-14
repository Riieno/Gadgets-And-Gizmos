package com.rieno.gadgetsandgizmos.compat.curios;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.google.common.collect.LinkedHashMultimap;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

// Register the portable controller Curios slot when Curios is available
public class CuriosCompatRegistrar {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CURIOS_CAPABILITY_CLASS = "top.theillusivec4.curios.api.CuriosCapability";
    private static final String CURIOS_API_CLASS = "top.theillusivec4.curios.api.CuriosApi";
    private static final String CURIOS_ITEM_INTERFACE = "top.theillusivec4.curios.api.type.capability.ICurio";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the curios compat registrar
    public static void register(RegisterCapabilitiesEvent evt) {
        if (CTItems.PHYSICS_GOGGLES == null) {
            return;
        }

        try {
            Class<?> curiosCapabilityClass = Class.forName(CURIOS_CAPABILITY_CLASS);
            Class<?> iCurioClass = Class.forName(CURIOS_ITEM_INTERFACE);

            Field itemCapabilityField = curiosCapabilityClass.getField("ITEM");
            @SuppressWarnings("unchecked")
            ItemCapability<Object, Void> curiosItemCapability = (ItemCapability<Object, Void>) itemCapabilityField.get(null);

            ICapabilityProvider<ItemStack, Void, Object> provider = (stack, ctx) ->
                    Proxy.newProxyInstance(iCurioClass.getClassLoader(), new Class<?>[]{iCurioClass},
                            (proxy, method, args) -> {
                                if ("getStack".equals(method.getName())) {
                                    return stack;
                                }
                                if ("canEquip".equals(method.getName()) || "canUnequip".equals(method.getName())
                                        || "canSync".equals(method.getName()) || "canRender".equals(method.getName())) {
                                    return true;
                                }

                                if ("canEquipFromUse".equals(method.getName())) {
                                    return true;
                                }
                                if ("getSlotsTooltip".equals(method.getName()) && args != null && args.length > 0) {
                                    return args[0];
                                }

                                if ("getAttributeModifiers".equals(method.getName())) {
                                    return LinkedHashMultimap.create();
                                }

                                if ("getAttributesTooltip".equals(method.getName()) && args != null && args.length > 0) {
                                    return args[0];
                                }

                                if ("writeSyncData".equals(method.getName())) {
                                    return new CompoundTag();
                                }
                                if ("getDropRule".equals(method.getName()) && method.getReturnType().isEnum()) {
                                    for (Object constant : method.getReturnType().getEnumConstants()) {
                                        if (constant instanceof Enum<?> enumConstant && "DEFAULT".equals(enumConstant.name())) {
                                            return constant;
                                        }
                                    }
                                }
                                Class<?> returnType = method.getReturnType();
                                if (returnType == boolean.class) {
                                    return false;
                                }
                                if (returnType == int.class) {
                                    return 0;
                                }
                                if (returnType == float.class) {
                                    return 0f;
                                }
                                if (returnType == double.class) {
                                    return 0d;
                                }
                                if (returnType == long.class) {
                                    return 0L;
                                }
                                return null;
                            });

            evt.registerItem(curiosItemCapability, provider, CTItems.PHYSICS_GOGGLES.get());
        } catch (ReflectiveOperationException ignored) {
            return;
        }

        GogglesItem.addIsWearingPredicate(CuriosCompatRegistrar::isWearingCuriosGoggles);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the player is wearing Curios goggles
    public static boolean isWearingCuriosGoggles(Player player) {
        if (CTItems.PHYSICS_GOGGLES == null) {
            return false;
        }
        try {
            Class<?> curiosApiClass = Class.forName(CURIOS_API_CLASS);
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Optional<?> curiosInventory = (Optional<?>) getCuriosInventory.invoke(null, player);
            if (curiosInventory.isEmpty()) {
                return false;
            }

            Object inventory = curiosInventory.get();
            Method findFirstCurio = inventory.getClass().getMethod("findFirstCurio", Item.class);
            Optional<?> match = (Optional<?>) findFirstCurio.invoke(inventory, CTItems.PHYSICS_GOGGLES.get());
            return match.isPresent();
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
