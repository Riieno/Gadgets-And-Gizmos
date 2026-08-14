package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import com.simibubi.create.content.trains.schedule.Schedule;

// Store Shipping Auto Refuel Settings
public record ShippingAutoRefuelSettings(boolean enabled, int thresholdPercent, String dockFilter) {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final ShippingAutoRefuelSettings DEFAULT = new ShippingAutoRefuelSettings(false, 25, "*");
    private static final String ROOT = "ShippingAutoRefuel";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping auto refuel settings
    public ShippingAutoRefuelSettings {
        thresholdPercent = Math.max(0, Math.min(100, thresholdPercent));
        dockFilter = dockFilter == null || dockFilter.isBlank() ? "*" : dockFilter.trim();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the shipping auto refuel settings
    public static ShippingAutoRefuelSettings read(ItemStack stack) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return fromTag(custom.getCompound(ROOT));
    }

    // Write the shipping auto refuel settings
    public static void write(ItemStack stack, ShippingAutoRefuelSettings settings) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.put(ROOT, settings.toTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    // Create the shipping auto refuel settings from schedule
    public static ShippingAutoRefuelSettings fromSchedule(Schedule schedule) {
        if (schedule == null || schedule.entries.isEmpty()) {
            return DEFAULT;
        }
        for (var entry : schedule.entries) {
            if (entry.instruction instanceof RefuelIfInstruction refuel) {
                return new ShippingAutoRefuelSettings(true,
                        refuel.thresholdPercent(), refuel.dockFilter());
            }
        }
        return DEFAULT;
    }

    // Write the shipping auto refuel settings data
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Threshold", thresholdPercent);
        tag.putString("Dock", dockFilter);
        return tag;
    }

    // Read the shipping auto refuel settings data
    public static ShippingAutoRefuelSettings fromTag(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return DEFAULT;
        return new ShippingAutoRefuelSettings(tag.getBoolean("Enabled"),
                tag.contains("Threshold") ? tag.getInt("Threshold") : 25,
                tag.getString("Dock"));
    }

    // Check if this should refuel
    public boolean shouldRefuel(double fuelRatio) {
        return enabled && fuelRatio * 100.0D <= thresholdPercent;
    }
}
