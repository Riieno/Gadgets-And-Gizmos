package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.DyeColorRecipe;
import com.rieno.gadgetsandgizmos.lib.inventory.PaletteDyeStorage;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

// The server owns counts above vanilla stack size and never creates oversized cursor stacks
public final class ThrusterDyeMenu extends AbstractContainerMenu{
    public static final int PLAYER_START = PaletteDyeStorage.SLOTS;
    public static final int PLAYER_INVENTORY_X = 82, PLAYER_INVENTORY_Y = 224;
    private final Inventory inventory;
    private final int itemSlot;
    private final ItemStack item;
    private final PaletteDyeStorage storage;
    private final SimpleContainer display = new SimpleContainer(PLAYER_START);
    private final SimpleContainerData counts = new SimpleContainerData(PLAYER_START);
    private ColorEffectPreset preset;

    public ThrusterDyeMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data){ this(id, inventory, readOpeningItem(inventory, data)); }
    public ThrusterDyeMenu(int id, Inventory inventory, int itemSlot){
        super(CTMenuTypes.THRUSTER_DYE.get(), id);
        this.inventory = inventory;
        this.itemSlot = itemSlot;
        item = inventory.getItem(itemSlot);
        storage = ThrusterDyeData.storage(item);
        preset = ThrusterDyeData.preset(item);
        addDataSlots(counts);
        for(int idx = 0; idx < PLAYER_START; idx++){
            final int color = idx;
            addSlot(new Slot(display, idx, dyeX(idx), dyeY(idx)){
                @Override public boolean mayPlace(ItemStack stack){ return stack.is(PaletteDyeStorage.slotItem(color)); }
                @Override public int getMaxStackSize(){ return 128; }
                @Override public int getMaxStackSize(ItemStack stack){ return 128; }
            });
        }
        for(int row = 0; row < 3; row++) for(int col = 0; col < 9; col++) playerSlot(9 + row * 9 + col, PLAYER_INVENTORY_X + 8 + col * 18, PLAYER_INVENTORY_Y + 18 + row * 18);
        for(int col = 0; col < 9; col++) playerSlot(col, PLAYER_INVENTORY_X + 8 + col * 18, PLAYER_INVENTORY_Y + 76);
        refresh();
    }

    public static int dyeX(int idx){ return idx < 2 ? 9 + idx * 80 : 9 + (idx - 2) % 5 * 20; }
    public static int dyeY(int idx){ return idx < 2 ? 105 : 125 + (idx - 2) / 5 * 19; }
    public int count(int color){ return counts.get(color); }
    public boolean ignoresDyeRequirements(){ return inventory.player.getAbilities().instabuild; }
    public ColorEffectPreset preset(){ return inventory.player.level().isClientSide ? ThrusterDyeData.preset(inventory.getItem(itemSlot)) : preset; }

    public static void open(ServerPlayer player, InteractionHand hand){
        int slot = hand == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
        player.openMenu(new MenuProvider(){
            @Override public Component getDisplayName(){ return Component.translatable("item.createthrusters.thruster_dye"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored){
                return new ThrusterDyeMenu(id, inventory, slot);
            }
            @Override public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf data){
                ((ThrusterDyeMenu)menu).writeOpeningData(data);
            }
        });
    }

    // The screen is constructed before ordinary slot updates reach the client
    private static int readOpeningItem(Inventory inventory, RegistryFriendlyByteBuf data){
        int slot = data.readVarInt();
        inventory.setItem(slot, ItemStack.STREAM_CODEC.decode(data));
        return slot;
    }

    void writeOpeningData(RegistryFriendlyByteBuf data){
        data.writeVarInt(itemSlot);
        ItemStack.STREAM_CODEC.encode(data, item);
    }

    private void playerSlot(int index, int x, int y){
        addSlot(new Slot(inventory, index, x, y){
            @Override public boolean mayPickup(Player player){ return index != itemSlot; }
            @Override public boolean mayPlace(ItemStack stack){ return index != itemSlot; }
        });
    }

    @Override public boolean stillValid(Player player){ return inventory.getItem(itemSlot).getItem() instanceof ThrusterDyeItem && (player.level().isClientSide || inventory.getItem(itemSlot) == item); }

    public boolean confirm(ColorEffectPreset next){
        if(inventory.player.level().isClientSide || !stillValid(inventory.player)) return false;
        if(next.equals(preset)) return true;
        int glowCost = DyeColorRecipe.additionalGlowInkSacs(preset, next);
        int[] dyeCost = DyeColorRecipe.additional(preset, next);
        if(!ignoresDyeRequirements() && (next.active() || next.additive()) && !storage.pay(dyeCost, glowCost)){
            inventory.player.displayClientMessage(Component.translatable(storage.count(PaletteDyeStorage.GLOW_SLOT) < glowCost
                    ? "createthrusters.thruster_dye.missing_glow" : "createthrusters.thruster_dye.missing"), true);
            return false;
        }
        preset = next;
        save();
        return true;
    }

    private void save(){
        ThrusterDyeData.write(item, storage, preset);
        inventory.setChanged();
        if(inventory.player instanceof ServerPlayer player){
            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, itemSlot, item.copy()));
        }
        refresh();
        broadcastChanges();
    }

    private void refresh(){
        for(int idx = 0; idx < PLAYER_START; idx++){
            counts.set(idx, storage.count(idx));
            display.setItem(idx, storage.count(idx) == 0 ? ItemStack.EMPTY : new ItemStack(PaletteDyeStorage.slotItem(idx), 1));
        }
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player){
        if(!stillValid(player)) return;
        if(type == ClickType.SWAP && (button == itemSlot || button == 40 && itemSlot == 40)) return;
        if(slot >= 0 && slot < PLAYER_START){
            if(player.level().isClientSide) return;
            if(type == ClickType.QUICK_MOVE) quickMoveStack(player, slot);
            else if(type == ClickType.PICKUP && (button == 0 || button == 1)){
                var carried = getCarried();
                if(carried.isEmpty()) setCarried(storage.extract(slot, button == 0 ? 64 : (storage.count(slot) + 1) / 2));
                else{
                    int offered = button == 1 ? 1 : carried.getCount();
                    var rest = storage.insert(slot, carried.copyWithCount(offered));
                    setCarried(carried.copyWithCount(carried.getCount() - offered + rest.getCount()));
                }
            }else if(type == ClickType.THROW) player.drop(storage.extract(slot, button == 0 ? 1 : 64), false);
            else if(type == ClickType.SWAP && button >= 0 && button < 9){
                var stack = inventory.getItem(button);
                inventory.setItem(button, stack.isEmpty() ? storage.extract(slot, 64) : storage.insert(slot, stack));
            }
            save();
            return;
        }
        if(slot >= 0 && slot < slots.size() && slots.get(slot).container == inventory && slots.get(slot).getContainerSlot() == itemSlot) return;
        super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index){
        if(player.level().isClientSide || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack result;
        if(index < PLAYER_START){
            ItemStack candidate = new ItemStack(PaletteDyeStorage.slotItem(index), Math.min(64, storage.count(index)));
            var wrapper = new InvWrapper(inventory);
            int accepted = candidate.getCount() - ItemHandlerHelper.insertItemStacked(wrapper, candidate, true).getCount();
            result = storage.extract(index, accepted);
            var rejected = ItemHandlerHelper.insertItemStacked(wrapper, result, false);
            if(!rejected.isEmpty()) storage.insert(index, rejected);
        }else{
            Slot slot = slots.get(index);
            if(slot.getContainerSlot() == itemSlot) return ItemStack.EMPTY;
            int ingredient = slot.getItem().is(PaletteDyeStorage.slotItem(PaletteDyeStorage.GLOW_SLOT))
                    ? PaletteDyeStorage.GLOW_SLOT : slot.getItem().getItem() instanceof DyeItem dye ? dye.getDyeColor().getId() : -1;
            if(ingredient < 0) return ItemStack.EMPTY;
            result = slot.getItem().copy();
            var remaining = storage.insert(ingredient, slot.getItem());
            slot.set(remaining);
            if(remaining.getCount() == result.getCount()) return ItemStack.EMPTY;
        }
        save();
        return result;
    }

    @Override public boolean canDragTo(Slot slot){ return slot.index >= PLAYER_START && slot.getContainerSlot() != itemSlot; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot){ return slot.index >= PLAYER_START && slot.getContainerSlot() != itemSlot; }
}
