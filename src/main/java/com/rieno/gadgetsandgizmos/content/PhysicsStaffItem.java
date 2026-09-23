package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerHooks;
import com.rieno.gadgetsandgizmos.lib.compat.PhysicsStaffPowerTracker;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

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

    // Place the staff beside a Backtank to keep retained locks powered in the world
    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos base = context.getClickedPos().relative(context.getClickedFace());
        BlockPos target = base.above();
        if (!canPlaceAnchor(context.getPlayer(), level, base)) {
            return super.useOn(context);
        }
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!level.setBlock(target, CTBlocks.PHYSICS_STAFF_ANCHOR.get().defaultBlockState(), 3)) {
            return net.minecraft.world.InteractionResult.FAIL;
        }

        BlockEntity blockEntity = level.getBlockEntity(target);
        if (!(blockEntity instanceof PhysicsStaffAnchorBlockEntity anchor)) {
            level.removeBlock(target, false);
            return net.minecraft.world.InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        java.util.UUID staffId = PhysicsStaffPowerHooks.getOrCreateStaffId(stack);
        anchor.setStaff(stack);
        if (staffId != null && level.getServer() != null) {
            PhysicsStaffPowerTracker.get(level.getServer()).moveToWorldPower(level.getServer(), staffId);
        }
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    // Check whether a sneaking player can place the staff beside a Backtank
    public static boolean canPlaceAnchor(Player player, Level level, BlockPos base) {
        BlockPos anchor = base.above();
        return player != null && player.isShiftKeyDown()
                && level.getBlockState(base).canBeReplaced()
                && level.getBlockState(anchor).canBeReplaced()
                && isAdjacentToBacktank(level, base);
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

    // Check for a Backtank on any side of the placed staff
    private static boolean isAdjacentToBacktank(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof BacktankBlockEntity) return true;
        }
        return false;
    }
}
