package com.rieno.gadgetsandgizmos.content;

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
    public static final String LINKED_CONNECTORS_TAG = "ShipDockLinkedConnectors";
    public static final String LINKED_CONNECTOR_POS_TAG = "Pos";
    public static final String LINKED_CONNECTOR_SUBLEVEL_TAG = "SubLevel";
    public static final String PENDING_DOCK_TAG = "ShipDockPendingBinding";
    public static final String PENDING_DOCK_ID_TAG = "DockId";
    public static final String PENDING_DOCK_POS_TAG = "Pos";
    public static final String PENDING_DOCK_SUBLEVEL_TAG = "SubLevel";

    // Initialize the ship dock block item
    public ShipDockBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    // Handle ship dock block item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        BlockEntity target = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if (!DockingConnectorAutomation.isDockingConnector(target)) return super.useOn(ctx);
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
                if (subLevelId != null) entry.putUUID(LINKED_CONNECTOR_SUBLEVEL_TAG, subLevelId);
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

    // Store a Ship Dock binding on a docking connector item
    public static void bindDockingConnectorToShipDock(ItemStack stack, ShipDockBlockEntity dock) {
        if (stack == null || dock == null) return;
        CompoundTag data = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID(PENDING_DOCK_ID_TAG, dock.getDockId());
        binding.putLong(PENDING_DOCK_POS_TAG, dock.getBlockPos().asLong());
        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(dock);
        if (subLevelId != null) binding.putUUID(PENDING_DOCK_SUBLEVEL_TAG, subLevelId);
        data.put(PENDING_DOCK_TAG, binding);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    // Apply a pending Ship Dock binding to a newly placed docking connector
    public static boolean applyPendingDockingConnectorBinding(ItemStack stack, BlockEntity connector) {
        if (stack == null || !DockingConnectorAutomation.isDockingConnector(connector)
                || connector.getLevel() == null) return false;
        CompoundTag data = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(PENDING_DOCK_TAG, Tag.TAG_COMPOUND)) return false;
        CompoundTag binding = data.getCompound(PENDING_DOCK_TAG);
        if (!binding.hasUUID(PENDING_DOCK_ID_TAG)
                || !binding.contains(PENDING_DOCK_POS_TAG, Tag.TAG_LONG)) return false;
        UUID dockSubLevelId = binding.hasUUID(PENDING_DOCK_SUBLEVEL_TAG)
                ? binding.getUUID(PENDING_DOCK_SUBLEVEL_TAG) : null;
        BlockEntity target = SimulatedHelper.findLoadedBlockEntityExact(
                connector.getLevel(), dockSubLevelId, BlockPos.of(binding.getLong(PENDING_DOCK_POS_TAG)));
        if (!(target instanceof ShipDockBlockEntity dock)
                || !binding.getUUID(PENDING_DOCK_ID_TAG).equals(dock.getDockId())) return false;
        dock.addLinkedDockingConnector(new ShipDockBlockEntity.ConnectorReference(
                SimulatedHelper.getContainingSubLevelId(connector), connector.getBlockPos()));
        data.remove(PENDING_DOCK_TAG);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return true;
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