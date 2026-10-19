package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Store the SCM route binding and route-overlay preference on a Shipping Schedule
public final class ShippingScheduleRouteData {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String ROOT_TAG = "CreateThrustersScmRoute";
    private static final String OWNER_TAG = "Owner";
    private static final String VISIBLE_TAG = "Visible";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the route data helper
    private ShippingScheduleRouteData() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Bind a schedule item to the SCM which owns its prepared route
    public static void bind(ItemStack stack, UUID ownerId) {
        if (stack == null || stack.isEmpty() || ownerId == null) return;
        CompoundTag customData = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag route = customData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? customData.getCompound(ROOT_TAG) : new CompoundTag();
        UUID previous = route.hasUUID(OWNER_TAG) ? route.getUUID(OWNER_TAG) : null;
        route.putUUID(OWNER_TAG, ownerId);
        if (previous != null && !previous.equals(ownerId)) route.putBoolean(VISIBLE_TAG, false);
        customData.put(ROOT_TAG, route);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
    }

    // Get the bound SCM route owner
    public static @Nullable UUID owner(ItemStack stack) {
        CompoundTag route = routeData(stack);
        return route != null && route.hasUUID(OWNER_TAG) ? route.getUUID(OWNER_TAG) : null;
    }

    // Check whether the schedule route overlay is selected
    public static boolean visible(ItemStack stack) {
        CompoundTag route = routeData(stack);
        return route != null && route.getBoolean(VISIBLE_TAG);
    }

    // Set the schedule route overlay preference
    public static void setVisible(ItemStack stack, boolean visible) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag customData = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag route = customData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? customData.getCompound(ROOT_TAG) : new CompoundTag();
        route.putBoolean(VISIBLE_TAG, visible);
        customData.put(ROOT_TAG, route);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
    }

    // Read the route data root
    private static @Nullable CompoundTag routeData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag customData = data.copyTag();
        return customData.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? customData.getCompound(ROOT_TAG) : null;
    }
}
