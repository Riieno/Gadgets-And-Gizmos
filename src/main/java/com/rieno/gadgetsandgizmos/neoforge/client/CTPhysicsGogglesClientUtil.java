package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

// Collect and format the Sable physics details shown by the goggles
public final class CTPhysicsGogglesClientUtil {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final CuriosReflection CURIOS = new CuriosReflection();
    private static final AccessoriesReflection ACCESSORIES = new AccessoriesReflection();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT physics goggles client util
    private CTPhysicsGogglesClientUtil() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the player is wearing physics goggles
    public static boolean isWearingPhysicsGoggles(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem.isFunctionalGoggles(
                player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD))) {
            return true;
        }
        return CURIOS.isWearing(player) || ACCESSORIES.isWearing(player);
    }

    // Get the worn physics goggles stack
    public static ItemStack getWornPhysicsGogglesStack(LocalPlayer player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem.isFunctionalGoggles(head)) {
            return head;
        }
        ItemStack curios = CURIOS.wornStack(player);
        if (!curios.isEmpty()) {
            return curios;
        }
        return ACCESSORIES.wornStack(player);
    }

    // Check if this should show contextual diagram
    public static boolean shouldShowContextualDiagram(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        if (isOnSubLevel(player)) {
            return true;
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return false;
        }

        Level level = minecraft.level;
        return level != null && SableLevelApi.containing(level, hitResult.getLocation()) != null;
    }

    // Check if the player is on a sublevel
    private static boolean isOnSubLevel(LocalPlayer player) {
        return SableLevelApi.tracking(player) != null;
    }

    // Handle the curios reflection
    private static final class CuriosReflection {
        // Wearing predicate
        private final Predicate<LocalPlayer> wearingPredicate;

        // Initialize the curios reflection
        private CuriosReflection() {
            Predicate<LocalPlayer> predicate = player -> false;
            try {
                Class<?> registrarClass = Class.forName("com.rieno.gadgetsandgizmos.compat.curios.CuriosCompatRegistrar");
                Method wearingMethod = registrarClass.getMethod("isWearingCuriosGoggles", net.minecraft.world.entity.player.Player.class);

                predicate = player -> {
                    try {
                        Object res = wearingMethod.invoke(null, player);
                        return res instanceof Boolean bool && bool;
                    } catch (ReflectiveOperationException ignored) {
                        return false;
                    }
                };
            } catch (ReflectiveOperationException ignored) {
                predicate = player -> false;
            }
            this.wearingPredicate = predicate;
        }

        // Check if this is wearing
        private boolean isWearing(LocalPlayer player) {
            return wearingPredicate.test(player);
        }

        // Get the worn stack
        private ItemStack wornStack(LocalPlayer player) {
            try {
                Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                Optional<?> curiosInventory = (Optional<?>) getCuriosInventory.invoke(null, player);
                if (curiosInventory.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                Object inventory = curiosInventory.get();
                Method findFirstCurio = inventory.getClass().getMethod("findFirstCurio", Item.class);
                Optional<?> match = CTItems.PHYSICS_GOGGLES == null ? Optional.empty()
                        : (Optional<?>) findFirstCurio.invoke(inventory, CTItems.PHYSICS_GOGGLES.get());
                if (match.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                return stackFromSlotResult(match.get());
            } catch (ReflectiveOperationException ignored) {
                return ItemStack.EMPTY;
            }
        }
    }

    // Handle the accessories reflection
    private static final class AccessoriesReflection {
        // Wearing predicate
        private final Predicate<LocalPlayer> wearingPredicate;

        // Initialize the accessories reflection
        private AccessoriesReflection() {
            Predicate<LocalPlayer> predicate = player -> false;
            try {
                Class<?> registrarClass = Class.forName("com.rieno.gadgetsandgizmos.compat.accessories.AccessoriesCompatRegistrar");
                Method wearingMethod = registrarClass.getMethod("isWearingAccessoriesGoggles", net.minecraft.world.entity.player.Player.class);

                predicate = player -> {
                    try {
                        Object res = wearingMethod.invoke(null, player);
                        return res instanceof Boolean bool && bool;
                    } catch (ReflectiveOperationException ignored) {
                        return false;
                    }
                };
            } catch (ReflectiveOperationException ignored) {
                predicate = player -> false;
            }
            this.wearingPredicate = predicate;
        }

        // Check if this is wearing
        private boolean isWearing(LocalPlayer player) {
            return wearingPredicate.test(player);
        }

        // Get the worn stack
        private ItemStack wornStack(LocalPlayer player) {
            try {
                Class<?> capabilityClass = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                Method getMethod = capabilityClass.getMethod("get", net.minecraft.world.entity.LivingEntity.class);
                Object capability = getMethod.invoke(null, player);
                if (capability == null) {
                    return ItemStack.EMPTY;
                }
                Method firstEquippedMethod = capabilityClass.getMethod("getFirstEquipped", Item.class);
                Object slotEntryReference = CTItems.PHYSICS_GOGGLES == null ? null
                        : firstEquippedMethod.invoke(capability, CTItems.PHYSICS_GOGGLES.get());
                if (slotEntryReference == null) {
                    return ItemStack.EMPTY;
                }
                return stackFromSlotResult(slotEntryReference);
            } catch (ReflectiveOperationException ignored) {
                return ItemStack.EMPTY;
            }
        }
    }

    // Get the stack from slot result
    private static ItemStack stackFromSlotResult(Object slotResult) throws ReflectiveOperationException {
        Method stackMethod;
        try {
            stackMethod = slotResult.getClass().getMethod("stack");
        } catch (NoSuchMethodException err) {
            stackMethod = slotResult.getClass().getMethod("getStack");
        }
        Object stack = stackMethod.invoke(slotResult);
        return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
    }
}
