package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Sync Portable Advanced Contraption Controller settings
public class PortableAdvancedContraptionControllerMenu extends AdvancedContraptionControllerMenu {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Source hand
    private InteractionHand sourceHand = InteractionHand.MAIN_HAND;
    // Current portable initial data
    private CompoundTag portableInitialData;
    // Current save target
    private PortableControllerSaveTarget saveTarget;
    // Tracks whether portable advanced contraption controller menu is saving
    private boolean saving;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable advanced contraption controller menu
    public PortableAdvancedContraptionControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(CTMenuTypes.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get(), id, inv, extraData);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected AdvancedContraptionControllerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        super.createOnClient(extraData);
        BlockPos pos = getContentPos() == null ? BlockPos.ZERO : getContentPos();
        PortableAdvancedContraptionControllerBlockEntity controller = new PortableAdvancedContraptionControllerBlockEntity(
                pos,
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        CompoundTag data = portableInitialData == null ? new CompoundTag() : portableInitialData;
        controller.loadControllerData(data, extraData.registryAccess());
        loadPortableMenuContainers(data, extraData.registryAccess());
        return controller;
    }

    // Initialize and read the inventory
    @Override
    protected void initAndReadInventory(AnalogueContraptionControllerBlockEntity contentHolder) {
        if (contentHolder != null && contentHolder.getLevel() == null
                && playerInventory != null && playerInventory.player != null) {
            contentHolder.setLevel(playerInventory.player.level());
        }
        super.initAndReadInventory(contentHolder);
    }

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buffer) {
        super.readExtraOpenData(buffer);
        portableInitialData = buffer.readNbt();
        if (portableInitialData == null) {
            portableInitialData = new CompoundTag();
        }
    }

    // Initialize the portable advanced contraption controller menu
    public PortableAdvancedContraptionControllerMenu(int id, Inventory inv,
                                                     PortableAdvancedContraptionControllerBlockEntity controller,
                                                     InteractionHand sourceHand) {
        super(CTMenuTypes.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get(), id, inv, controller);
        this.sourceHand = sourceHand;
        loadPortableMenuContainers(
                PortableContraptionControllerItem.readControllerData(inv.player.getItemInHand(sourceHand)),
                inv.player.level().registryAccess());
        controller.setChangedCallback(this::saveToStack);
        saveToStack();
    }

    // Initialize the portable advanced contraption controller menu
    public PortableAdvancedContraptionControllerMenu(int id, Inventory inv,
                                                     PortableAdvancedContraptionControllerBlockEntity controller,
                                                     ItemStack sourceStack,
                                                     PortableControllerSaveTarget saveTarget) {
        super(CTMenuTypes.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get(), id, inv, controller);
        this.sourceHand = null;
        this.saveTarget = saveTarget;
        loadPortableMenuContainers(
                PortableContraptionControllerItem.readControllerData(sourceStack),
                inv.player.level().registryAccess());
        controller.setChangedCallback(this::saveToStack);
        saveToStack();
    }

    // Broadcast the changes
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        saveToStack();
    }

    // Handle screen removal
    @Override
    public void removed(Player player) {
        super.removed(player);
        saveToStack();
    }

    // Save the portable state
    public void savePortableState() {
        saveToStack();
    }

    // Check if the menu observes the portable stack
    public boolean observesPortableStack(ItemStack stack) {
        return sourceHand != null && playerInventory != null && playerInventory.player != null
                && playerInventory.player.getItemInHand(sourceHand) == stack;
    }

    // Check if the menu observes the lectern
    public boolean observesLectern(BlockPos pos) {
        return sourceHand == null && pos != null && pos.equals(getContentPos());
    }

    // Save the stack
    private void saveToStack() {
        if (saving || playerInventory == null || playerInventory.player == null
                || playerInventory.player.level().isClientSide || contentHolder == null) {
            return;
        }
        CompoundTag portableMenuContainers = savePortableMenuContainers(playerInventory.player.level().registryAccess());
        if (saveTarget != null) {
            saving = true;
            saveTarget.save(contentHolder, portableMenuContainers);
            saving = false;
            return;
        }
        if (sourceHand == null) {
            return;
        }
        ItemStack stack = playerInventory.player.getItemInHand(sourceHand);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable) || !portable.isAdvanced()) {
            return;
        }
        saving = true;
        boolean changed = PortableContraptionControllerItem.saveControllerToStackIfChanged(
                stack, contentHolder, true, portableMenuContainers);
        if (changed && playerInventory.player instanceof ServerPlayer serverPlayer) {
            PortableContraptionControllerRuntime.refreshActiveControllerFromStack(serverPlayer, sourceHand, true);
        }
        saving = false;
    }

    // Save the portable menu containers
    private CompoundTag savePortableMenuContainers(HolderLookup.Provider provider) {
        CompoundTag containers = new CompoundTag();
        containers.put(PortableContraptionControllerItem.PORTABLE_GOGGLES_TAG, saveGogglesContainer(provider));
        return containers;
    }

    // Load the portable menu containers
    private void loadPortableMenuContainers(CompoundTag controllerData, HolderLookup.Provider provider) {
        if (controllerData == null || !controllerData.contains(
                PortableContraptionControllerItem.PORTABLE_MENU_CONTAINERS_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag containers = controllerData.getCompound(PortableContraptionControllerItem.PORTABLE_MENU_CONTAINERS_TAG);
        if (containers.contains(PortableContraptionControllerItem.PORTABLE_GOGGLES_TAG, Tag.TAG_COMPOUND)) {
            loadGogglesContainer(containers.getCompound(PortableContraptionControllerItem.PORTABLE_GOGGLES_TAG), provider);
        }
    }
}
