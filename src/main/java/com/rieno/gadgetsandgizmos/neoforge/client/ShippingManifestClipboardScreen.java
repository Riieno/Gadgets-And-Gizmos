package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.mixin.ClipboardScreenAccessor;
import com.rieno.gadgetsandgizmos.content.ShippingManifestBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestLockPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestUsesPayload;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
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
    private final List<ItemStack> containerLockFilters = new ArrayList<>();
    private final List<ContainerLockSlot> containerLockSlots = new ArrayList<>();
    private int resourceUses;
    private boolean useSelectorOpen;
    private boolean containerLocked;
    private Button useSelectorButton;
    private Button containerLockButton;

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
            int availableResourceUses,
            boolean containerLocked,
            List<ItemStack> containerLockFilters
    ) {
        super(targetSlot, components, null);
        this.manifestPos = manifestPos == null ? BlockPos.ZERO : manifestPos.immutable();
        this.resourceUses = resourceUses;
        this.availableResourceUses = availableResourceUses;
        this.containerLocked = containerLocked;
        if (containerLockFilters != null) {
            for (ItemStack filter : containerLockFilters) {
                if (!filter.isEmpty()) this.containerLockFilters.add(filter.copyWithCount(1));
            }
        }
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
        containerLockButton = addRenderableWidget(Button.builder(containerLockLabel(), ignored -> {
            containerLocked = !containerLocked;
            syncContainerLock();
            refreshContainerLock();
        }).bounds(x, y + 112, 190, 20).build());
        for (int index = 0; index < 9; index++) {
            int slotX = x + (index % 5) * 38;
            int slotY = y + 134 + index / 5 * 22;
            int filterIndex = index;
            Button button = addRenderableWidget(Button.builder(containerLockSlotLabel(filterIndex),
                    ignored -> setContainerLockFilter(filterIndex)).bounds(slotX, slotY, 36, 20).build());
            containerLockSlots.add(new ContainerLockSlot(slotX, slotY, filterIndex, button));
        }
        refreshUseSelector();
        refreshContainerLock();
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

    // Remove a ghost item from the container lock with the secondary mouse button
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            for (ContainerLockSlot slot : containerLockSlots) {
                if (mouseX < slot.x || mouseX >= slot.x + 36 || mouseY < slot.y || mouseY >= slot.y + 20) continue;
                if (slot.index < containerLockFilters.size()) {
                    containerLockFilters.remove(slot.index);
                    syncContainerLock();
                    refreshContainerLock();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Draw configured lock item previews over their ghost-slot buttons
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (ContainerLockSlot slot : containerLockSlots) {
            if (slot.index >= containerLockFilters.size()) continue;
            graphics.renderItem(containerLockFilters.get(slot.index), slot.x + 10, slot.y + 2);
        }
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

    // Assign the held item to one container lock filter slot
    private void setContainerLockFilter(int index) {
        if (Minecraft.getInstance().player == null) return;
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        if (held.isEmpty()) return;
        while (containerLockFilters.size() <= index) containerLockFilters.add(ItemStack.EMPTY);
        containerLockFilters.set(index, held.copyWithCount(1));
        trimContainerLockFilters();
        syncContainerLock();
        refreshContainerLock();
    }

    // Persist the complete lock state so the server remains authoritative
    private void syncContainerLock() {
        PacketDistributor.sendToServer(new ShippingManifestLockPayload(manifestPos, containerLocked,
                containerLockFilters.stream().filter(filter -> !filter.isEmpty()).map(ItemStack::copy).toList()));
    }

    // Remove empty trailing ghost slots from the local lock state
    private void trimContainerLockFilters() {
        while (!containerLockFilters.isEmpty() && containerLockFilters.getLast().isEmpty()) {
            containerLockFilters.removeLast();
        }
    }

    // Refresh the lock control and its ghost-filter buttons
    private void refreshContainerLock() {
        if (containerLockButton != null) containerLockButton.setMessage(containerLockLabel());
        for (ContainerLockSlot slot : containerLockSlots) {
            slot.button.setMessage(containerLockSlotLabel(slot.index));
            slot.button.active = containerLocked;
        }
    }

    // Build the container lock toggle label
    private Component containerLockLabel() {
        return Component.literal((containerLocked ? "[x] " : "[ ] ") + "Lock container to filters");
    }

    // Build one container lock ghost-slot label
    private Component containerLockSlotLabel(int index) {
        return Component.literal(index < containerLockFilters.size() && !containerLockFilters.get(index).isEmpty()
                ? " " : "+");
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

    // Track one interactive container lock ghost-slot button
    private record ContainerLockSlot(int x, int y, int index, Button button) {
    }
}
