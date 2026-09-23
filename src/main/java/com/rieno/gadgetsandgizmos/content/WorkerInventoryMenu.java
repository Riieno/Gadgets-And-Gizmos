package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

// Open and synchronize a persistent mannequin worker inventory
public class WorkerInventoryMenu extends AbstractContainerMenu {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int STORAGE_SLOT_COUNT = 27;
    public static final int ARMOR_SLOT_COUNT = 4;
    public static final int CURIO_SLOT_COUNT = 6;
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Worker entity backing the open menu
    private final @Nullable PlayerMannequinEntity worker;
    // Worker normal storage
    private final ItemStackHandler storage;
    // Worker armor equipment bridge
    private final Container armor;
    // Worker Curios storage
    private final ItemStackHandler curios;
    // Whether Curios slots are present in this menu
    private final boolean curiosAvailable;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the client-side worker inventory menu.
    public WorkerInventoryMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(CTMenuTypes.WORKER_INVENTORY.get(), id, playerInventory, workerFromData(playerInventory, data));
    }

    // Initialize the server-side worker inventory menu.
    public WorkerInventoryMenu(int id, Inventory playerInventory, PlayerMannequinEntity worker) {
        this(CTMenuTypes.WORKER_INVENTORY.get(), id, playerInventory, worker);
    }

    // Initialize the worker inventory menu.
    private WorkerInventoryMenu(MenuType<?> type, int id, Inventory playerInventory,
                                @Nullable PlayerMannequinEntity worker) {
        super(type, id);
        this.worker = worker;
        storage = worker == null ? new ItemStackHandler(STORAGE_SLOT_COUNT) : worker.workerInventory();
        armor = worker == null ? new SimpleContainer(ARMOR_SLOT_COUNT) : new WorkerArmorContainer(worker);
        curios = worker == null ? new ItemStackHandler(CURIO_SLOT_COUNT) : worker.workerCurios();
        curiosAvailable = ModList.get().isLoaded("curios");
        addWorkerSlots();
        addPlayerSlots(playerInventory);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open this persistent inventory for one assigned worker mannequin.
    public static void open(ServerPlayer player, PlayerMannequinEntity worker) {
        if (player == null || worker == null || worker.isRemoved()) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new WorkerInventoryMenu(id, inventory, worker), worker.getDisplayName()),
                buffer -> buffer.writeVarInt(worker.getId()));
    }

    // Get the worker backing this menu.
    public @Nullable PlayerMannequinEntity worker() {
        return worker;
    }

    // Check whether Curios slots are available.
    public boolean curiosAvailable() {
        return curiosAvailable;
    }

    // Get the first menu slot assigned to the worker's armor.
    public int armorSlotFirst() {
        return STORAGE_SLOT_COUNT;
    }

    // Get the first menu slot assigned to the worker's Curios tab.
    public int curioSlotFirst() {
        return STORAGE_SLOT_COUNT + ARMOR_SLOT_COUNT;
    }

    // Get the first menu slot assigned to the interacting player.
    public int playerSlotFirst() {
        return curioSlotFirst() + (curiosAvailable ? CURIO_SLOT_COUNT : 0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int workerSlots = playerSlotFirst();
        if (index < workerSlots) {
            if (!moveItemStackTo(stack, workerSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, workerSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return worker != null && worker.isAlive() && player.distanceToSqr(worker) <= 64.0D;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Helpers
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Resolve the target mannequin from menu open data on the client.
    private static @Nullable PlayerMannequinEntity workerFromData(Inventory inventory, RegistryFriendlyByteBuf data) {
        if (inventory == null || inventory.player == null || data == null) return null;
        Entity entity = inventory.player.level().getEntity(data.readVarInt());
        return entity instanceof PlayerMannequinEntity mannequin ? mannequin : null;
    }

    // Add storage, armor and optional Curios slots.
    private void addWorkerSlots() {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new SlotItemHandler(storage, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int slot = 0; slot < ARMOR_SLOT_COUNT; slot++) {
            addSlot(new Slot(armor, slot, 188, 18 + slot * 18));
        }
        if (!curiosAvailable) return;
        for (int slot = 0; slot < CURIO_SLOT_COUNT; slot++) {
            addSlot(new SlotItemHandler(curios, slot, 224 + slot % 3 * 18, 18 + slot / 3 * 18));
        }
    }

    // Add the interacting player's inventory and hotbar.
    private void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int slot = 0; slot < 9; slot++) {
            addSlot(new Slot(inventory, slot, 8 + slot * 18, 142));
        }
    }

    // Expose the mannequin armor equipment through normal menu slots.
    private static final class WorkerArmorContainer implements Container {
        private static final EquipmentSlot[] SLOTS = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        private final PlayerMannequinEntity worker;

        // Initialize the armor bridge.
        private WorkerArmorContainer(PlayerMannequinEntity worker) {
            this.worker = worker;
        }

        @Override
        public int getContainerSize() {
            return SLOTS.length;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot slot : SLOTS) if (!worker.getItemBySlot(slot).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot < 0 || slot >= SLOTS.length ? ItemStack.EMPTY : worker.getItemBySlot(SLOTS[slot]);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack removed = stack.split(amount);
            setItem(slot, stack);
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= SLOTS.length) return;
            worker.setItemSlot(SLOTS[slot], stack == null ? ItemStack.EMPTY : stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return worker.isAlive() && player.distanceToSqr(worker) <= 64.0D;
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < SLOTS.length; slot++) setItem(slot, ItemStack.EMPTY);
        }
    }
}
