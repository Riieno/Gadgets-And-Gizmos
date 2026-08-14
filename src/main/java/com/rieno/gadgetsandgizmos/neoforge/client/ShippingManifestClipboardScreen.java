package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.mixin.ClipboardScreenAccessor;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.core.component.DataComponentMap;

// Draw and handle the Shipping Manifest Clipboard screen
public class ShippingManifestClipboardScreen extends ClipboardScreen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final boolean REMOVE_CHECKED_ITEMS_ENABLED = false;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping manifest clipboard
    public ShippingManifestClipboardScreen(int targetSlot, DataComponentMap components) {
        super(targetSlot, components, null);
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
}
