package com.rieno.gadgetsandgizmos.content.navigation;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.DirectionalAnalogSnapshot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// Expose optional map-provider data attached to a navigation table
public interface NavigationTableExtensionAccess {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    int SLOT_COUNT = 15;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the selected slot
    int ct$getSelectedSlot();

    // Set the selected slot
    void ct$setSelectedSlot(int slot);

    // Get the run state
    RunState ct$getRunState();

    // Start the navigation
    void ct$startNavigation();

    // Pause the navigation
    void ct$pauseNavigation();

    // Stop the navigation
    void ct$stopNavigation();

    // Get the map in slot
    ItemStack ct$getMapInSlot(int slot);

    // Set the map in slot
    void ct$setMapInSlot(int slot, ItemStack stack);

    // Set the map in slot
    default void ct$setMapInSlot(int slot, ItemStack stack, @Nullable Player player) {
        ct$setMapInSlot(slot, stack);
    }

    // Remove the map in slot
    default ItemStack ct$removeMapInSlot(int slot) {
        return ct$removeMapInSlot(slot, null);
    }

    // Remove the map in slot
    default ItemStack ct$removeMapInSlot(int slot, @Nullable Player player) {
        ItemStack stack = ct$getMapInSlot(slot);
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = stack.copy();
        ct$setMapInSlot(slot, ItemStack.EMPTY, player);
        return removed;
    }

    // Get the resolved target
    @Nullable NavigationTableMapResolver.ResolvedTarget ct$getResolvedTarget(int slot);

    // Get the target label
    @Nullable String ct$getTargetLabel();

    // Get the relative angle deg
    float ct$getRelativeAngleDeg();

    // Get the directional snapshot
    DirectionalAnalogSnapshot ct$getDirectionalSnapshot();

    // Store run state
    enum RunState {
        IDLE,
        RUNNING,
        PAUSED
    }
}
