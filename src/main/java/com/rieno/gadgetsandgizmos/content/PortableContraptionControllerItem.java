package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// Open a short-lived remote controller session and release it when the item is no longer held
public class PortableContraptionControllerItem extends Item {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final BlockPos PORTABLE_POS = BlockPos.ZERO;
    public static final String PORTABLE_MENU_CONTAINERS_TAG = "PortableMenuContainers";
    public static final String PORTABLE_GOGGLES_TAG = "Goggles";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether advanced is set
    private final boolean advanced;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable contraption controller item
    public PortableContraptionControllerItem(Properties properties, boolean advanced) {
        super(properties.stacksTo(1));
        this.advanced = advanced;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is advanced
    public boolean isAdvanced() {
        return advanced;
    }

    // Check if this can use goggles pair candidate
    public static boolean canUseGogglesPairCandidate(
            UUID pairId,
            Set<UUID> expectedPairIds,
            boolean pairVerifiedFromStack,
            boolean cachedPairAuthorized,
            boolean sameCachedSource
    ) {
        return pairId != null && (pairVerifiedFromStack
                || expectedPairIds != null && expectedPairIds.contains(pairId)
                || cachedPairAuthorized && sameCachedSource);
    }

    // Update the inventory
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        migrateLegacyStorage(
                stack,
                level,
                entity == null ? BlockPos.ZERO : entity.blockPosition());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle portable contraption controller item use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (wantsMenu(player)) {
            if (!level.isClientSide) {
                openPortableMenu(level, player, hand);
            } else {
                reqClientPortableMenuOpen(hand, advanced);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            toggleClientPortableInteractMode(advanced, hand);
        }
        return InteractionResultHolder.pass(stack);
    }

    // Handle portable contraption controller item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AnalogueContraptionControllerBlockEntity controller) {
            if (player == null) {
                return InteractionResult.PASS;
            }
            useOnControllerBlock(stack, level.getBlockState(pos), level, pos, player, ctx.getHand(), controller);
            return InteractionResult.CONSUME;
        }

        if (player != null && wantsMenu(player)) {
            if (!level.isClientSide) {
                openPortableMenu(level, player, ctx.getHand());
            } else {
                reqClientPortableMenuOpen(ctx.getHand(), advanced);
            }
            return InteractionResult.CONSUME;
        }

        ControllerDiscoveryNode target = AnalogueContraptionControllerBlockItem.classifyTarget(level, pos);
        if (target != null) {
            return captureTarget(level, player, stack, target);
        }

        return InteractionResult.PASS;
    }

    // Handle the controller block
    public net.minecraft.world.ItemInteractionResult useOnControllerBlock(ItemStack stack, BlockState state, Level level,
                                                                          BlockPos pos, Player player,
                                                                          InteractionHand hand,
                                                                          AnalogueContraptionControllerBlockEntity controller) {
        if (!matchesController(controller)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "item.createthrusters.portable_contraption_controller.wrong_variant"), true);
            }
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }

        if (wantsMenu(player)) {
            if (!level.isClientSide) {
                copyStackToPlaced(stack, controller, level, player);
            }
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }

        if (!level.isClientSide) {
            copyPlacedToStack(stack, controller, level, player);
        }
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        int storedCount = AnalogueContraptionControllerBlockItem.getStoredTargets(stack).size();
        if (storedCount > 0) {
            tooltip.add(Component.translatable("item.createthrusters.analogue_contraption_controller.stored_targets", storedCount)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // Create the controller from stack
    public static AnalogueContraptionControllerBlockEntity createControllerFromStack(ItemStack stack, @Nullable Level level,
                                                                                    boolean advanced) {
        return createControllerFromStack(stack, level, advanced, PORTABLE_POS);
    }

    // Create the controller from stack
    public static AnalogueContraptionControllerBlockEntity createControllerFromStack(ItemStack stack, @Nullable Level level,
                                                                                    boolean advanced, BlockPos pos) {
        BlockState state = advanced
                ? CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState()
                : CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get().defaultBlockState();
        AnalogueContraptionControllerBlockEntity controller = advanced
                ? new PortableAdvancedContraptionControllerBlockEntity(pos == null ? PORTABLE_POS : pos.immutable(), state)
                : new PortableAnalogueContraptionControllerBlockEntity(pos == null ? PORTABLE_POS : pos.immutable(), state);
        if (level != null) {
            controller.setLevel(level);
            controller.loadControllerData(readControllerData(stack, level), level.registryAccess());
        }
        return controller;
    }

    // Save the controller to stack
    public static void saveControllerToStack(ItemStack stack, AnalogueContraptionControllerBlockEntity controller,
                                             boolean advanced) {
        saveControllerToStack(stack, controller, advanced, null);
    }

    // Save the controller to stack
    public static void saveControllerToStack(ItemStack stack, AnalogueContraptionControllerBlockEntity controller,
                                             boolean advanced, @Nullable CompoundTag portableMenuContainers) {
        saveControllerToStackIfChanged(stack, controller, advanced, portableMenuContainers);
    }

    // Save the controller to stack if changed
    public static boolean saveControllerToStackIfChanged(ItemStack stack,
                                                         AnalogueContraptionControllerBlockEntity controller,
                                                         boolean advanced,
                                                         @Nullable CompoundTag portableMenuContainers) {
        if (stack.isEmpty() || controller == null || controller.getLevel() == null) {
            return false;
        }
        CompoundTag tag = controller.saveControllerData(controller.getLevel().registryAccess());
        if (portableMenuContainers != null && !portableMenuContainers.isEmpty()) {
            tag.put(PORTABLE_MENU_CONTAINERS_TAG, portableMenuContainers.copy());
        }
        return saveControllerDataToStackIfChanged(
                stack,
                tag,
                advanced,
                controller.getLevel(),
                controller.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(controller));
    }

    // Read the controller data
    public static CompoundTag readControllerData(ItemStack stack) {
        return readControllerData(stack, null);
    }

    // Read the controller data
    public static CompoundTag readControllerData(ItemStack stack, @Nullable Level level) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag stored = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        if (level != null && level.isClientSide) {
            return stored;
        }
        ControllerManifestStore.ManifestSnapshot snapshot =
                ControllerManifestStore.loadControllerFromMetadata(stored, level);
        if (snapshot != null) {
            return controllerData(snapshot);
        }
        return stored;
    }

    // Save the controller data to stack if changed
    static boolean saveControllerDataToStackIfChanged(ItemStack stack,
                                                      CompoundTag controllerData,
                                                      boolean advanced,
                                                      @Nullable Level level,
                                                      BlockPos ownerPos,
                                                      @Nullable java.util.UUID subLevelId) {
        if (stack == null || stack.isEmpty() || controllerData == null
                || level == null || level.isClientSide) {
            return false;
        }

        CompoundTag data = controllerData.copy();
        clearControllerMetadata(data);
        data.remove("id");
        CompoundTag existingTag =
                stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        String manifestId = ControllerManifestStore.controllerManifestId(existingTag);
        ControllerManifestStore.ManifestSnapshot existing =
                manifestId.isBlank() ? null : ControllerManifestStore.loadController(manifestId, level);

        if (existing != null && controllerData(existing).equals(data)) {
            CompoundTag metadata = controllerMetadata(existing, advanced);
            if (!metadata.equals(existingTag)) {
                stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(metadata));
                return true;
            }
            return false;
        }

        int revision = existing == null ? 0 : existing.revision();
        ControllerManifestStore.ManifestSnapshot saved = ControllerManifestStore.saveController(
                manifestId,
                advanced ? "advanced_controller" : "base_controller",
                level,
                ownerPos == null ? BlockPos.ZERO : ownerPos,
                subLevelId,
                data,
                data.getCompound("AdvancedDraftGraph"),
                data.getCompound("AdvancedActiveGraph"),
                revision);
        if (saved == null) {
            return false;
        }
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(controllerMetadata(saved, advanced)));
        return true;
    }

    // Get the controller data
    private static CompoundTag controllerData(ControllerManifestStore.ManifestSnapshot snapshot) {
        CompoundTag data = snapshot.controllerData();
        if (!snapshot.draftGraph().isEmpty()) {
            data.put("AdvancedDraftGraph", snapshot.draftGraph());
        }
        if (!snapshot.activeGraph().isEmpty()) {
            data.put("AdvancedActiveGraph", snapshot.activeGraph());
        }
        return data;
    }

    // Get the controller metadata
    private static CompoundTag controllerMetadata(ControllerManifestStore.ManifestSnapshot snapshot,
                                                  boolean advanced) {
        CompoundTag metadata = new CompoundTag();
        ControllerManifestStore.writeControllerMetadata(metadata, snapshot);
        BlockEntity.addEntityType(metadata, advanced
                ? CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get()
                : CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get());
        return metadata;
    }

    // Clear the controller metadata
    private static void clearControllerMetadata(CompoundTag tag) {
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
    }

    // Check if this has legacy ctrl payload
    private static boolean hasLegacyCtrlPayload(ItemStack stack) {
        CompoundTag tag =
                stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        for (String key : tag.getAllKeys()) {
            if (!"id".equals(key)
                    && !ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID.equals(key)
                    && !ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION.equals(key)
                    && !ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH.equals(key)
                    && !ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION.equals(key)) {
                return true;
            }
        }
        return tag.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION)
                || tag.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH)
                || tag.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
    }

    // Migrate the legacy storage
    public static boolean migrateLegacyStorage(ItemStack stack, @Nullable Level level, BlockPos ownerPos) {
        if (stack == null || stack.isEmpty() || level == null || level.isClientSide
                || !hasLegacyCtrlPayload(stack)
                || (!(stack.getItem() instanceof PortableContraptionControllerItem)
                && !(stack.getItem() instanceof AnalogueContraptionControllerBlockItem))) {
            return false;
        }
        CompoundTag legacyData = readControllerData(stack, level);
        return saveControllerDataToStackIfChanged(
                stack,
                legacyData,
                AnalogueContraptionControllerBlockItem.isAdvancedStack(stack),
                level,
                ownerPos == null ? BlockPos.ZERO : ownerPos,
                SimulatedHelper.getSubLevelId(level));
    }

    // Check if this matches controller
    protected boolean matchesController(AnalogueContraptionControllerBlockEntity controller) {
        return advanced == controller instanceof AdvancedContraptionControllerBlockEntity;
    }

    // Check if the player wants the controller menu
    public static boolean wantsMenu(Player player) {
        return player != null && (player.isShiftKeyDown() || player.isCrouching());
    }

    // Open the portable menu
    public void openPortableMenu(Level level, Player player, InteractionHand hand) {
        if (isPortableMenuOpen(player.containerMenu, advanced)) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PortableContraptionControllerRuntime.saveActiveControllerToStack(serverPlayer, hand, advanced);
        }
        openMenu(level, player, hand, player.getItemInHand(hand));
    }

    // Check if the portable menu is open
    private static boolean isPortableMenuOpen(AbstractContainerMenu menu, boolean advanced) {
        return advanced
                ? menu instanceof PortableAdvancedContraptionControllerMenu
                : menu instanceof PortableAnalogueContraptionControllerMenu;
    }

    // Capture the target
    private InteractionResult captureTarget(Level level, @Nullable Player player, ItemStack stack, ControllerDiscoveryNode target) {
        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }
        boolean added = AnalogueContraptionControllerBlockItem.storeCapturedTarget(stack, target, level);
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    added ? "createthrusters.analogue_controller.capture.added" : "createthrusters.analogue_controller.capture.already_added",
                    target.label()).withStyle(added ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    // Copy the placed to stack
    private void copyPlacedToStack(ItemStack stack, AnalogueContraptionControllerBlockEntity controller,
                                   Level level, Player player) {
        saveControllerToStack(stack, controller, advanced);
        player.displayClientMessage(Component.translatable(
                "item.createthrusters.portable_contraption_controller.copied_from_placed"), true);
    }

    // Copy the stack to placed
    private void copyStackToPlaced(ItemStack stack, AnalogueContraptionControllerBlockEntity controller,
                                   Level level, Player player) {
        controller.loadControllerData(readControllerData(stack, level), level.registryAccess());
        controller.setChanged();
        controller.sendData();
        player.displayClientMessage(Component.translatable(
                "item.createthrusters.portable_contraption_controller.copied_to_placed"), true);
    }

    // Open the menu
    private void openMenu(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (level.isClientSide || !(player instanceof ServerPlayer)) {
            return;
        }
        AnalogueContraptionControllerBlockEntity controller = createControllerFromStack(stack, level, advanced);
        MenuProvider provider = new MenuProvider() {
            // Get the display name
            @Override
            public Component getDisplayName() {
                return controller.getDisplayName();
            }

            // Create the menu
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player menuPlayer) {
                if (advanced && controller instanceof PortableAdvancedContraptionControllerBlockEntity advancedController) {
                    AdvancedControllerGraphSnapshotPayload.send(
                            (ServerPlayer) menuPlayer,
                            advancedController.getBlockPos(),
                            SimulatedHelper.getContainingSubLevelId(advancedController),
                            advancedController.getDraftGraph(),
                            advancedController.getActiveGraph());
                    return new PortableAdvancedContraptionControllerMenu(containerId, inventory, advancedController, hand);
                }
                if (controller instanceof PortableAnalogueContraptionControllerBlockEntity portableController) {
                    return new PortableAnalogueContraptionControllerMenu(containerId, inventory, portableController, hand);
                }
                return null;
            }
        };
        CompoundTag portableData = readControllerData(stack, level);
        portableData.remove("AdvancedDraftGraph");
        portableData.remove("AdvancedActiveGraph");
        portableData.remove("AdvancedGraphVersions");
        player.openMenu(provider, buffer -> {
            controller.sendToMenu(buffer);
            buffer.writeNbt(portableData.copy());
        });
    }

    // Toggle portable interaction mode
    private static void toggleClientPortableInteractMode(boolean advanced, InteractionHand hand) {
        try {
            Class<?> handlerClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
            handlerClass.getMethod("togglePortableInteractMode", boolean.class, InteractionHand.class)
                    .invoke(null, advanced, hand);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    // Request the client portable menu open
    private static void reqClientPortableMenuOpen(InteractionHand hand, boolean advanced) {
        try {
            Class<?> handlerClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AnalogueContraptionControllerClientHandler");
            handlerClass.getMethod("requestPortableMenuOpen", InteractionHand.class, boolean.class)
                    .invoke(null, hand, advanced);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
