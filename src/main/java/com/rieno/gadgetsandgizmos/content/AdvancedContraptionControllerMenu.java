package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

// Sync Advanced Contraption Controller settings
public class AdvancedContraptionControllerMenu extends AnalogueContraptionControllerMenu {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int LINKER_SLOT_X = 122;
    public static final int LINKER_SLOT_Y = 44;
    public static final int GOGGLES_INPUT_SLOT_X = 78;
    public static final int GOGGLES_INPUT_SLOT_Y = 112;
    public static final int GOGGLES_OUTPUT_SLOT_X = 160;
    public static final int GOGGLES_OUTPUT_SLOT_Y = 112;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initial draft
    private AdvancedGraphDocument initialDraft;
    // Initial active
    private AdvancedGraphDocument initialActive;
    // Tracks whether tablet remote access is set
    private boolean tabletRemoteAccess;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        super(CTMenuTypes.ADVANCED_CONTRAPTION_CONTROLLER.get(), id, inventory, data);
    }

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(MenuType<?> type, int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        super(type, id, inventory, data);
    }

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(int id, Inventory inventory, AdvancedContraptionControllerBlockEntity blockEntity) {
        super(CTMenuTypes.ADVANCED_CONTRAPTION_CONTROLLER.get(), id, inventory, blockEntity);
        tabletRemoteAccess = DiagnosticTabletRemoteSessions.consumeMenuAuthorization(
                inventory.player, blockEntity.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(blockEntity));
        initialDraft = blockEntity.getDraftGraph();
        initialActive = blockEntity.getActiveGraph();
    }

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(MenuType<?> type, int id, Inventory inventory,
                                             AdvancedContraptionControllerBlockEntity blockEntity) {
        super(type, id, inventory, blockEntity);
        tabletRemoteAccess = DiagnosticTabletRemoteSessions.consumeMenuAuthorization(
                inventory.player, blockEntity.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(blockEntity));
        initialDraft = blockEntity.getDraftGraph();
        initialActive = blockEntity.getActiveGraph();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buffer) {
        super.readExtraOpenData(buffer);
        int draftRevision = buffer.readVarInt();
        int activeRevision = buffer.readVarInt();
        AdvancedControllerGraphSnapshotPayload.GraphSnapshot snapshot =
                AdvancedControllerGraphSnapshotPayload.clientSnapshot(
                        getContentPos(), getContentSubLevelId(), draftRevision, activeRevision);
        initialDraft = AdvancedGraphDocument.fromTag(snapshot.draft());
        initialActive = AdvancedGraphDocument.fromTag(snapshot.active());
    }

    // Get the initial draft
    public AdvancedGraphDocument getInitialDraft() {
        return initialDraft == null ? new AdvancedGraphDocument() : initialDraft.copy();
    }

    // Get the initial active
    public AdvancedGraphDocument getInitialActive() {
        return initialActive == null ? new AdvancedGraphDocument() : initialActive.copy();
    }

    // Check if the still is valid
    @Override
    public boolean stillValid(Player player) {
        if (!tabletRemoteAccess) return super.stillValid(player);
        return contentHolder != null && contentHolder.getLevel() != null
                && !contentHolder.isRemoved()
                && contentHolder.getLevel().getBlockEntity(contentHolder.getBlockPos()) == contentHolder;
    }

    // Get the linker slot x
    @Override
    protected int linkerSlotX() {
        return LINKER_SLOT_X;
    }

    // Get the linker slot y
    @Override
    protected int linkerSlotY() {
        return LINKER_SLOT_Y;
    }

    // Get the goggles input slot x
    @Override
    protected int gogglesInputSlotX() {
        return GOGGLES_INPUT_SLOT_X;
    }

    // Get the goggles input slot y
    @Override
    protected int gogglesInputSlotY() {
        return GOGGLES_INPUT_SLOT_Y;
    }

    // Get the goggles output slot x
    @Override
    protected int gogglesOutputSlotX() {
        return GOGGLES_OUTPUT_SLOT_X;
    }

    // Get the goggles output slot y
    @Override
    protected int gogglesOutputSlotY() {
        return GOGGLES_OUTPUT_SLOT_Y;
    }

    // Get the menu config target block entity
    @Override
    public AdvancedContraptionControllerBlockEntity getMenuConfigTargetBlockEntity() {
        if (contentHolder == null && getContentPos() != null && playerInventory != null && playerInventory.player != null) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                    playerInventory.player.level(), getContentSubLevelId(), getContentPos());
            if (blockEntity instanceof AdvancedContraptionControllerBlockEntity controller) {
                contentHolder = controller;
            }
        }
        return contentHolder instanceof AdvancedContraptionControllerBlockEntity controller ? controller : null;
    }

    // Save the data
    @Override
    protected void saveData(AnalogueContraptionControllerBlockEntity contentHolder) {
        if (contentHolder == null || contentHolder.getLevel() == null || contentHolder.getLevel().isClientSide || slots.isEmpty()) {
            return;
        }
        ItemStack linker = getCurrentLinkerStack();
        linker.setCount(linker.isEmpty() ? 0 : 1);
        if (!sameSingleItem(contentHolder.getStoredLinker(), linker)) {
            contentHolder.getLinkerSlotHandler().setStackInSlot(0, linker);
        }
    }

}
