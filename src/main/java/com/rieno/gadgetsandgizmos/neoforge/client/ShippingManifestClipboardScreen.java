package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.mixin.ClipboardScreenAccessor;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestUsesPayload;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// Draw and handle the Shipping Manifest Clipboard screen
public class ShippingManifestClipboardScreen extends ClipboardScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final boolean REMOVE_CHECKED_ITEMS_ENABLED = false;

    private final BlockPos manifestPos;
    private final int availableResourceUses;
    private final List<Button> useButtons = new ArrayList<>();
    private int resourceUses;
    private boolean useSelectorOpen;
    private Button useSelectorButton;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping manifest clipboard
    public ShippingManifestClipboardScreen(
            int targetSlot,
            DataComponentMap components,
            BlockPos manifestPos,
            int resourceUses,
            int availableResourceUses
    ) {
        super(targetSlot, components, null);
        this.manifestPos = manifestPos == null ? BlockPos.ZERO : manifestPos.immutable();
        this.resourceUses = resourceUses;
        this.availableResourceUses = availableResourceUses;
    }

    // Initialize the shipping manifest clipboard
    @Override
    protected void init() {
        super.init();

        IconButton removeCheckedItemsButton = ((ClipboardScreenAccessor) this).createthrusters$getClearButton();
        if (removeCheckedItemsButton != null) {
            removeCheckedItemsButton.visible = REMOVE_CHECKED_ITEMS_ENABLED;
            removeCheckedItemsButton.active = REMOVE_CHECKED_ITEMS_ENABLED;
        }

        int x = width / 2 - 95;
        int y = height / 2 - 112;
        useSelectorButton = addRenderableWidget(Button.builder(usesLabel(), button -> {
            useSelectorOpen = !useSelectorOpen;
            refreshUseSelector();
        }).bounds(x, y, 190, 20).build());
        addUseButton(x, y + 22, ShippingManifestBlockEntity.USE_ITEMS, "Items");
        addUseButton(x, y + 44, ShippingManifestBlockEntity.USE_FLUIDS, "Fluids");
        addUseButton(x, y + 66, ShippingManifestBlockEntity.USE_FUEL, "Fuel");
        addUseButton(x, y + 88, ShippingManifestBlockEntity.USE_ENERGY, "FE");
        refreshUseSelector();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle screen removal
    @Override
    public void removed() {
    }

    // Add one selectable resource use to the drop-down list
    private void addUseButton(int x, int y, int use, String label) {
        Button button = Button.builder(useLabel(use, label), ignored -> toggleUse(use))
                .bounds(x, y, 190, 20).build();
        useButtons.add(button);
        addRenderableWidget(button);
    }

    // Toggle one resource use and update the persistent server setting
    private void toggleUse(int use) {
        if ((availableResourceUses & use) == 0) return;
        resourceUses ^= use;
        resourceUses &= availableResourceUses;
        PacketDistributor.sendToServer(new ShippingManifestUsesPayload(manifestPos, resourceUses));
        refreshUseSelector();
    }

    // Refresh the multi-select drop-down labels and visibility
    private void refreshUseSelector() {
        for (Button button : useButtons) {
            button.visible = useSelectorOpen;
        }
        if (useButtons.size() == 4) {
            useButtons.get(0).setMessage(useLabel(ShippingManifestBlockEntity.USE_ITEMS, "Items"));
            useButtons.get(1).setMessage(useLabel(ShippingManifestBlockEntity.USE_FLUIDS, "Fluids"));
            useButtons.get(2).setMessage(useLabel(ShippingManifestBlockEntity.USE_FUEL, "Fuel"));
            useButtons.get(3).setMessage(useLabel(ShippingManifestBlockEntity.USE_ENERGY, "FE"));
            for (int index = 0; index < useButtons.size(); index++) {
                int use = switch (index) {
                    case 0 -> ShippingManifestBlockEntity.USE_ITEMS;
                    case 1 -> ShippingManifestBlockEntity.USE_FLUIDS;
                    case 2 -> ShippingManifestBlockEntity.USE_FUEL;
                    default -> ShippingManifestBlockEntity.USE_ENERGY;
                };
                useButtons.get(index).active = (availableResourceUses & use) != 0;
            }
        }
        if (useSelectorButton != null) useSelectorButton.setMessage(usesLabel());
    }

    // Build the selected uses summary
    private Component usesLabel() {
        List<String> selected = new ArrayList<>();
        if ((resourceUses & ShippingManifestBlockEntity.USE_ITEMS) != 0) selected.add("Items");
        if ((resourceUses & ShippingManifestBlockEntity.USE_FLUIDS) != 0) selected.add("Fluids");
        if ((resourceUses & ShippingManifestBlockEntity.USE_FUEL) != 0) selected.add("Fuel");
        if ((resourceUses & ShippingManifestBlockEntity.USE_ENERGY) != 0) selected.add("FE");
        return Component.literal("Uses: " + (selected.isEmpty() ? "None" : String.join(", ", selected))
                + (useSelectorOpen ? " ^" : " v"));
    }

    // Build one checkable resource use label
    private Component useLabel(int use, String label) {
        boolean selected = (resourceUses & use) != 0;
        boolean available = (availableResourceUses & use) != 0;
        return Component.literal((selected ? "[x] " : "[ ] ") + label
                + (available ? "" : " (unavailable)"));
    }
}
