package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// Sync Portable Analogue Contraption Controller settings
public class PortableAnalogueContraptionControllerMenu extends AnalogueContraptionControllerMenu {
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
    // Tracks whether portable analogue contraption controller menu is saving
    private boolean saving;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the portable analogue contraption controller menu
    public PortableAnalogueContraptionControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(CTMenuTypes.PORTABLE_CONTRAPTION_CONTROLLER.get(), id, inv, extraData);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Create the client
    @Override
    protected AnalogueContraptionControllerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        super.createOnClient(extraData);
        BlockPos pos = getContentPos() == null ? BlockPos.ZERO : getContentPos();
        PortableAnalogueContraptionControllerBlockEntity controller = new PortableAnalogueContraptionControllerBlockEntity(
                pos,
                CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get().defaultBlockState());
        controller.loadControllerData(portableInitialData == null ? new CompoundTag() : portableInitialData,
                extraData.registryAccess());
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

    // Initialize the portable analogue contraption controller menu
    public PortableAnalogueContraptionControllerMenu(int id, Inventory inv,
                                                     PortableAnalogueContraptionControllerBlockEntity controller,
                                                     InteractionHand sourceHand) {
        super(CTMenuTypes.PORTABLE_CONTRAPTION_CONTROLLER.get(), id, inv, controller);
        this.sourceHand = sourceHand;
        controller.setChangedCallback(this::saveToStack);
        saveToStack();
    }

    // Initialize the portable analogue contraption controller menu
    public PortableAnalogueContraptionControllerMenu(int id, Inventory inv,
                                                     PortableAnalogueContraptionControllerBlockEntity controller,
                                                     PortableControllerSaveTarget saveTarget) {
        super(CTMenuTypes.PORTABLE_CONTRAPTION_CONTROLLER.get(), id, inv, controller);
        this.sourceHand = null;
        this.saveTarget = saveTarget;
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

    // Save the stack
    protected void saveToStack() {
        if (saving || playerInventory == null || playerInventory.player == null
                || playerInventory.player.level().isClientSide || contentHolder == null) {
            return;
        }
        if (saveTarget != null) {
            saving = true;
            saveTarget.save(contentHolder, null);
            saving = false;
            return;
        }
        if (sourceHand == null) {
            return;
        }
        ItemStack stack = playerInventory.player.getItemInHand(sourceHand);
        if (!(stack.getItem() instanceof PortableContraptionControllerItem portable) || portable.isAdvanced()) {
            return;
        }
        saving = true;
        PortableContraptionControllerItem.saveControllerToStack(stack, contentHolder, false);
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            PortableContraptionControllerRuntime.refreshActiveControllerFromStack(serverPlayer, sourceHand, false);
        }
        saving = false;
    }
}
