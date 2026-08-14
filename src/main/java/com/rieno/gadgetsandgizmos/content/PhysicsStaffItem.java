package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

// Show and synchronize the stored charge used by Physics Staff actions
public class PhysicsStaffItem extends dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the physics staff item
    public PhysicsStaffItem(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if the bar is visible
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return PhysicsStaffPowerHooks.isBarVisible(stack);
    }

    // Get the bar width
    @Override
    public int getBarWidth(ItemStack stack) {
        return PhysicsStaffPowerHooks.getBarWidth(stack);
    }

    // Get the bar color
    @Override
    public int getBarColor(ItemStack stack) {
        return PhysicsStaffPowerHooks.getBarColor(stack);
    }

    // Update the inventory
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide) {
            java.util.UUID staffId = PhysicsStaffPowerHooks.getOrCreateStaffId(stack);
            if (entity instanceof Player player && staffId != null && level.getServer() != null) {
                PhysicsStaffPowerHooks.updateTooltipSnapshot(
                        stack,
                        PhysicsStaffPowerTracker.get(level.getServer()).getLockedCount(staffId),
                        PhysicsStaffPowerHooks.getTooltipPressureCurrent(player),
                        PhysicsStaffPowerHooks.getTooltipPressureCapacity(player));
            }
        }
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(createStatusLine(
                Component.translatable("item.createthrusters.physics_staff.status.locked"),
                Component.literal(Integer.toString(PhysicsStaffPowerHooks.getStoredLockedCount(stack))).withStyle(ChatFormatting.AQUA)));

        int pressureCurrent = PhysicsStaffPowerHooks.getStoredPressureCurrent(stack);
        int pressureMax = PhysicsStaffPowerHooks.getStoredPressureMax(stack);
        Component pressureValue = pressureCurrent < 0 || pressureMax < 0
                ? Component.translatable("item.createthrusters.physics_staff.status.pressure.creative").withStyle(ChatFormatting.AQUA)
                : Component.literal(pressureCurrent + " / " + pressureMax).withStyle(ChatFormatting.AQUA);
        tooltip.add(createStatusLine(
                Component.translatable("item.createthrusters.physics_staff.status.pressure"),
                pressureValue));

        CTTooltipHelper.addCreateDescription(this, tooltip);
    }

    // Create the status line
    private static Component createStatusLine(Component label, Component val) {
        return CTTooltipHelper.line(label.getString(), val);
    }
}
