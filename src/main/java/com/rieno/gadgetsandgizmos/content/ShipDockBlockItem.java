package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

// Handle Ship Dock Block
public class ShipDockBlockItem extends CTTooltipBlockItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String LINKED_CONNECTORS_TAG = "ShipDockLinkedConnectors";
    public static final String LINKED_CONNECTOR_POS_TAG = "Pos";
    public static final String LINKED_CONNECTOR_SUBLEVEL_TAG = "SubLevel";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship dock block item
    public ShipDockBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle ship dock block item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockEntity target = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if (!DockingConnectorAutomation.isDockingConnector(target)) {
            return super.useOn(ctx);
        }
        if (!ctx.getLevel().isClientSide) {
            ItemStack stack = ctx.getItemInHand();
            CompoundTag data = stack.getOrDefault(
                    DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            ListTag connectors = data.getList(LINKED_CONNECTORS_TAG, Tag.TAG_COMPOUND);
            UUID subLevelId = SimulatedHelper.getContainingSubLevelId(target);
            BlockPos pos = target.getBlockPos();
            boolean alreadyLinked = false;
            for (int idx = 0; idx < connectors.size(); idx++) {
                CompoundTag entry = connectors.getCompound(idx);
                UUID entrySubLevel = entry.hasUUID(LINKED_CONNECTOR_SUBLEVEL_TAG)
                        ? entry.getUUID(LINKED_CONNECTOR_SUBLEVEL_TAG) : null;
                if (entry.getLong(LINKED_CONNECTOR_POS_TAG) == pos.asLong()
                        && java.util.Objects.equals(entrySubLevel, subLevelId)) {
                    alreadyLinked = true;
                    break;
                }
            }
            if (!alreadyLinked) {
                CompoundTag entry = new CompoundTag();
                entry.putLong(LINKED_CONNECTOR_POS_TAG, pos.asLong());
                if (subLevelId != null) {
                    entry.putUUID(LINKED_CONNECTOR_SUBLEVEL_TAG, subLevelId);
                }
                connectors.add(entry);
                data.put(LINKED_CONNECTORS_TAG, connectors);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            }
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(Component.translatable(
                                alreadyLinked
                                        ? "createthrusters.ship_dock.connector_already_linked"
                                        : "createthrusters.ship_dock.connector_linked",
                                connectors.size())
                        .withStyle(alreadyLinked ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
            }
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    // Check if this is foil
    @Override
    public boolean isFoil(ItemStack stack) {
        return linkedConnectorCount(stack) > 0 || super.isFoil(stack);
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        int count = linkedConnectorCount(stack);
        if (count > 0) {
            tooltip.add(Component.translatable(
                    "createthrusters.ship_dock.linked_connector_count", count)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    // Get the linked connector count
    private static int linkedConnectorCount(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
        return data.getList(LINKED_CONNECTORS_TAG, Tag.TAG_COMPOUND).size();
    }
}
