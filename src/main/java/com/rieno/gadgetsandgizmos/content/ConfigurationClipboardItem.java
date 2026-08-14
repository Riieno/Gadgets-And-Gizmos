package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.simibubi.create.api.schematic.nbt.PartialSafeNBT;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Copy safe block settings and thruster equipment between compatible targets
public class ConfigurationClipboardItem extends Item {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String ROOT_KEY = "ConfigurationClipboard";
    private static final String BLOCK_KEY = "Block";
    private static final String SETTINGS_KEY = "Settings";
    private static final String THRUSTER_EQUIPMENT_KEY = "ThrusterEquipment";
    private static final String FORMAT_KEY = "Format";
    private static final int SAFE_NBT_FORMAT = 1;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the configuration clipboard item
    public ConfigurationClipboardItem(Properties properties) {
        super(properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is foil
    @Override
    public boolean isFoil(ItemStack stack) {
        return readClipboard(stack).contains(BLOCK_KEY, Tag.TAG_STRING);
    }

    // Handle configuration clipboard item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockState state = level.getBlockState(ctx.getClickedPos());
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!CreateThrusters.MOD_ID.equals(blockId.getNamespace())) {
            message(player, "item.createthrusters.configuration_clipboard.addon_blocks_only", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(ctx.getClickedPos());
        if (blockEntity == null) {
            message(player, "item.createthrusters.configuration_clipboard.no_settings", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            copyFromBlock(ctx.getItemInHand(), blockId, state, blockEntity, level, player);
        } else {
            pasteToBlock(ctx.getItemInHand(), blockId, state, blockEntity, level, player);
        }
        return InteractionResult.SUCCESS;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle configuration clipboard item use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            stack.remove(DataComponents.CUSTOM_DATA);
            message(player, "item.createthrusters.configuration_clipboard.cleared", ChatFormatting.YELLOW);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag clipboard = readClipboard(stack);
        if (clipboard.contains(BLOCK_KEY, Tag.TAG_STRING)) {
            ResourceLocation blockId = ResourceLocation.tryParse(clipboard.getString(BLOCK_KEY));
            Component blockName = blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)
                    ? BuiltInRegistries.BLOCK.get(blockId).getName()
                    : Component.literal(clipboard.getString(BLOCK_KEY));
            tooltip.add(Component.translatable("item.createthrusters.configuration_clipboard.tooltip.stored", blockName)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.createthrusters.configuration_clipboard.tooltip.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        CTTooltipHelper.addCreateDescription(this, tooltip);
    }

    // Copy the block
    private static void copyFromBlock(ItemStack clipboardStack, ResourceLocation blockId, BlockState state,
                                      BlockEntity blockEntity, Level level, Player player) {
        HolderLookup.Provider registries = level.registryAccess();
        if (isController(blockEntity)) {
            message(player, "item.createthrusters.configuration_clipboard.no_settings", ChatFormatting.RED);
            return;
        }
        CompoundTag settings = safeSettings(blockEntity, registries);
        if (settings.isEmpty()) {
            message(player, "item.createthrusters.configuration_clipboard.no_settings", ChatFormatting.RED);
            return;
        }
        CompoundTag clipboard = new CompoundTag();
        clipboard.putString(BLOCK_KEY, blockId.toString());
        clipboard.putInt(FORMAT_KEY, SAFE_NBT_FORMAT);
        clipboard.put(SETTINGS_KEY, settings);
        if (blockEntity instanceof ThrusterBlockEntity thruster) {
            clipboard.put(THRUSTER_EQUIPMENT_KEY, saveThrusterEquipment(thruster, registries));
        }
        writeClipboard(clipboardStack, clipboard);
        player.displayClientMessage(Component.translatable(
                "item.createthrusters.configuration_clipboard.copied", state.getBlock().getName())
                .withStyle(ChatFormatting.GREEN), true);
    }

    // Paste the block
    private static void pasteToBlock(ItemStack clipboardStack, ResourceLocation blockId, BlockState state,
                                     BlockEntity blockEntity, Level level, Player player) {
        CompoundTag clipboard = readClipboard(clipboardStack);
        if (!clipboard.contains(BLOCK_KEY, Tag.TAG_STRING)) {
            message(player, "item.createthrusters.configuration_clipboard.empty", ChatFormatting.RED);
            return;
        }
        if (!blockId.toString().equals(clipboard.getString(BLOCK_KEY))) {
            message(player, "item.createthrusters.configuration_clipboard.wrong_block", ChatFormatting.RED);
            return;
        }
        if (isController(blockEntity) || clipboard.getInt(FORMAT_KEY) != SAFE_NBT_FORMAT) {
            message(player, "item.createthrusters.configuration_clipboard.no_settings", ChatFormatting.RED);
            return;
        }

        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag currentSettings = safeSettings(blockEntity, registries);
        if (currentSettings.isEmpty()) {
            message(player, "item.createthrusters.configuration_clipboard.no_settings", ChatFormatting.RED);
            return;
        }
        CompoundTag merged = blockEntity.saveCustomOnly(registries);
        replaceSafeSettings(merged, currentSettings, clipboard.getCompound(SETTINGS_KEY));
        blockEntity.loadCustomOnly(merged, registries);

        boolean equipmentMatched = true;
        if (blockEntity instanceof ThrusterBlockEntity thruster
                && clipboard.contains(THRUSTER_EQUIPMENT_KEY, Tag.TAG_COMPOUND)) {
            equipmentMatched = applyThrusterEquipment(
                    thruster, clipboard.getCompound(THRUSTER_EQUIPMENT_KEY), registries, player);
        }

        blockEntity.setChanged();
        if (blockEntity instanceof SyncedBlockEntity syncedBlockEntity) {
            syncedBlockEntity.sendData();
        } else {
            level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, 3);
        }
        String messageKey = equipmentMatched
                ? "item.createthrusters.configuration_clipboard.pasted"
                : "item.createthrusters.configuration_clipboard.pasted_missing_equipment";
        player.displayClientMessage(Component.translatable(messageKey, state.getBlock().getName())
                .withStyle(equipmentMatched ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
    }

    // Save the thruster equipment
    private static CompoundTag saveThrusterEquipment(ThrusterBlockEntity thruster, HolderLookup.Provider registries) {
        CompoundTag equipment = new CompoundTag();
        for (int slot = ThrusterBlockEntity.SLOT_UPGRADE; slot <= ThrusterBlockEntity.SLOT_LENS; slot++) {
            equipment.put(Integer.toString(slot), thruster.getItemInventory().getStackInSlot(slot).saveOptional(registries));
        }
        return equipment;
    }

    // Apply the thruster equipment
    private static boolean applyThrusterEquipment(ThrusterBlockEntity thruster, CompoundTag equipment,
                                                  HolderLookup.Provider registries, Player player) {
        boolean allMatched = true;
        for (int slot = ThrusterBlockEntity.SLOT_UPGRADE; slot <= ThrusterBlockEntity.SLOT_LENS; slot++) {
            ItemStack desired = ItemStack.parseOptional(registries, equipment.getCompound(Integer.toString(slot)));
            ItemStack current = thruster.getItemInventory().getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(current, desired)) {
                continue;
            }
            if (!desired.isEmpty() && !takeOne(player, desired)) {
                allMatched = false;
                continue;
            }
            thruster.extractInventorySlot(slot, player);
            if (!desired.isEmpty()) {
                thruster.insertInventorySlot(slot, desired);
            }
        }
        return allMatched;
    }

    // Take one matching item
    private static boolean takeOne(Player player, ItemStack required) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, required)) {
                candidate.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    // Check if this is a controller
    private static boolean isController(BlockEntity blockEntity) {
        return blockEntity instanceof AnalogueContraptionControllerBlockEntity
                || blockEntity instanceof AdvancedContraptionControllerBlockEntity
                || blockEntity instanceof PortableAnalogueContraptionControllerBlockEntity
                || blockEntity instanceof PortableAdvancedContraptionControllerBlockEntity;
    }

    // Get the safe settings
    private static CompoundTag safeSettings(BlockEntity blockEntity, HolderLookup.Provider registries) {
        CompoundTag settings = new CompoundTag();
        if (blockEntity instanceof PartialSafeNBT safeBlockEntity) {
            safeBlockEntity.writeSafe(settings, registries);
        }
        return settings;
    }

    // Replace the safe settings
    static void replaceSafeSettings(CompoundTag target, CompoundTag currentSettings, CompoundTag newSettings) {
        Set<String> keys = new HashSet<>(currentSettings.getAllKeys());
        keys.addAll(newSettings.getAllKeys());
        for (String key : keys) {
            Tag currentValue = currentSettings.get(key);
            Tag newValue = newSettings.get(key);
            Tag targetValue = target.get(key);
            if ((currentValue instanceof CompoundTag || newValue instanceof CompoundTag)
                    && targetValue instanceof CompoundTag targetCompound) {
                CompoundTag currentCompound = currentValue instanceof CompoundTag compound
                        ? compound : new CompoundTag();
                CompoundTag newCompound = newValue instanceof CompoundTag compound
                        ? compound : new CompoundTag();
                replaceSafeSettings(targetCompound, currentCompound, newCompound);
            } else if (newValue != null) {
                target.put(key, newValue.copy());
            } else {
                target.remove(key);
            }
        }
    }

    // Read the clipboard
    private static CompoundTag readClipboard(ItemStack stack) {
        CompoundTag customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customData.getCompound(ROOT_KEY);
    }

    // Write the clipboard
    private static void writeClipboard(ItemStack stack, CompoundTag clipboard) {
        CompoundTag customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        customData.put(ROOT_KEY, clipboard);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
    }

    // Build the clipboard status message
    private static void message(Player player, String key, ChatFormatting col) {
        player.displayClientMessage(Component.translatable(key).withStyle(col), true);
    }
}
