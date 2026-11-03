package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.lib.inventory.FluidContainerTransfers;
import com.rieno.gadgetsandgizmos.lib.inventory.ItemStorageGrid;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Own all storage mutations on the server; virtual grid slots only synchronize copies for rendering
public final class SmartContainerMenu extends AbstractContainerMenu{
    public static final int CONTENT_X = 0;
    public static final int CONTENT_Y = 0;
    public static final int CONTENT_WIDTH = 320;
    public static final int GRID_COLUMNS = 17;
    public static final int GRID_ROWS = 6;
    public static final com.rieno.gadgetsandgizmos.lib.tablet.TabletInventoryLayout LAYOUT =
            com.rieno.gadgetsandgizmos.lib.tablet.TabletInventoryLayout.stacked(CONTENT_WIDTH, 40, 180, GRID_COLUMNS, GRID_ROWS);
    public static final int GRID_SIZE = GRID_COLUMNS * GRID_ROWS;
    public static final int PLAYER_START = GRID_SIZE + 18;
    public static final List<String> TABS = List.of("contents", "resources", "network", "storage", "transfers");
    public final BlockPos targetPos;
    public final UUID subLevelId;
    public final UUID tabletId;
    public final int kind;
    private final BlockEntity target;
    private final Player viewer;
    private final SimpleContainer display = new SimpleContainer(GRID_SIZE);
    private final ItemStorageGrid grid = new ItemStorageGrid();
    private final SimpleContainerData data = new SimpleContainerData(4);
    private String search = "";
    private int ticks;
    private String snapshotPage = "";

    public SmartContainerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer){
        this(id, inventory, buffer.readBlockPos(), buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt());
    }

    SmartContainerMenu(int id, Inventory inventory, BlockPos pos, UUID subLevel, UUID tablet,
                               int kind, int tab){
        super(CTMenuTypes.SMART_CONTAINER.get(), id);
        viewer = inventory.player;
        targetPos = pos;
        subLevelId = subLevel;
        tabletId = tablet;
        this.kind = kind;
        target = SimulatedHelper.findLoadedBlockEntityExact(viewer.level(), subLevel, pos);
        data.set(0, Math.max(0, Math.min(TABS.size() - 1, tab)));
        data.set(3, 1);
        addDataSlots(data);
        for(int idx = 0; idx < GRID_SIZE; idx++){
            addSlot(new Slot(display, idx, CONTENT_X + LAYOUT.storage().x(idx), CONTENT_Y + LAYOUT.storage().y(idx)){
                @Override public boolean isActive(){ return storageTab() && kind == 0; }
                @Override public boolean mayPlace(ItemStack stack){ return true; }
                @Override public boolean mayPickup(Player player){ return true; }
            });
        }
        var charging = !viewer.level().isClientSide && target instanceof SmartBatteryBlockEntity battery
                ? battery.chargingItems() : new ItemStackHandler(18);
        for(int idx = 0; idx < 18; idx++){
            addSlot(new SlotItemHandler(charging, idx, CONTENT_X + (idx < 9 ? LAYOUT.inputs() : LAYOUT.outputs()).x(idx % 9),
                    CONTENT_Y + (idx < 9 ? LAYOUT.inputs() : LAYOUT.outputs()).y(idx % 9)){
                @Override public boolean isActive(){
                    return storageTab() && kind == 2 && (!viewer.level().isClientSide || search.isBlank()
                            || getItem().getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(search.toLowerCase(java.util.Locale.ROOT)));
                }
            });
        }
        for(int row = 0; row < 3; row++){
            for(int col = 0; col < 9; col++) addPlayerSlot(inventory, 9 + row * 9 + col, CONTENT_X + LAYOUT.inventory().x(row * 9 + col), CONTENT_Y + LAYOUT.inventory().y(row * 9 + col));
        }
        for(int idx = 0; idx < 9; idx++) addPlayerSlot(inventory, idx, CONTENT_X + LAYOUT.hotbar().x(idx), CONTENT_Y + LAYOUT.hotbar().y(idx));
        refreshGrid();
    }

    public static void open(ServerPlayer player, BlockEntity selected){
        BlockEntity target = SmartContainerTablets.controller(selected);
        if(!SmartContainerTablets.supports(target) || !WorldAccessPolicy.canAccessLocal(player, player.serverLevel(),
                SimulatedHelper.getContainingSubLevelId(target), target.getBlockPos())) return;
        BlockPos pos = target.getBlockPos();
        UUID subLevel = SimulatedHelper.getContainingSubLevelId(target), tablet = SmartContainerTablets.tabletId(target);
        int kind = target instanceof SmartBatteryBlockEntity ? 2 : target instanceof SmartTankBlockEntity ? 1 : 0;
        int tab = Math.max(0, TABS.indexOf(SmartContainerTablets.state(target).tab()));
        player.openMenu(new SimpleMenuProvider((id, inv, ignored) ->
                new SmartContainerMenu(id, inv, pos, subLevel, tablet, kind, tab), SmartContainerManifest.APP.title()), buffer -> {
            buffer.writeBlockPos(pos);
            buffer.writeBoolean(subLevel != null);
            if(subLevel != null) buffer.writeUUID(subLevel);
            buffer.writeUUID(tablet);
            buffer.writeVarInt(kind);
            buffer.writeVarInt(tab);
        });
    }

    private void addPlayerSlot(Inventory inventory, int slot, int x, int y){
        addSlot(new Slot(inventory, slot, x, y){
            @Override public boolean isActive(){ return storageTab(); }
        });
    }

    public void selectClientTab(String tab){
        if(viewer.level().isClientSide && TABS.contains(tab)) data.set(0, TABS.indexOf(tab));
    }

    public String tab(){ return TABS.get(data.get(0)); }
    public boolean storageTab(){ return "storage".equals(tab()); }
    public int row(){ return data.get(1); }
    public int totalRows(){ return data.get(2); }
    public boolean network(){ return data.get(3) != 0; }

    @Override public boolean stillValid(Player player){
        if(player.level().isClientSide) return true;
        if(target == null || target.isRemoved() || !SmartContainerTablets.supports(target)
                || !SmartContainerTablets.controller(target).getBlockPos().equals(targetPos)
                || SimulatedHelper.findLoadedBlockEntityExact(player.level(), subLevelId, targetPos) != target) return false;
        var global = SimulatedHelper.toGlobalWorldPosition(target, targetPos.getCenter());
        return global != null && player.distanceToSqr(global) <= 256 && player instanceof ServerPlayer server
                && WorldAccessPolicy.canAccessLocal(server, server.serverLevel(), subLevelId, targetPos);
    }

    public void search(String text){
        if(!stillValid(viewer) || !storageTab() || kind == 1) return;
        search = text.substring(0, Math.min(80, text.length()));
        if(viewer.level().isClientSide) return;
        data.set(1, 0);
        refreshGrid();
        broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player player, int button){
        if(player.level().isClientSide || !stillValid(player)) return false;
        if(button >= 0 && button < TABS.size()){
            data.set(0, button);
            snapshotPage = "";
            SmartContainerTablets.selectTab(target, tab());
        }else if(storageTab()){
            if(button == 100 && kind == 0){ data.set(3, network() ? 0 : 1); data.set(1, 0); }
            else if(button == 101 && kind == 0) data.set(1, Math.max(0, row() - 1));
            else if(button == 102 && kind == 0) data.set(1, Math.min(Math.max(0, totalRows() - GRID_ROWS), row() + 1));
            else if((button == 110 || button == 111 || button == 112) && target instanceof SmartTankBlockEntity tank){
                setCarried(button == 112 ? FluidContainerTransfers.transferBucket(player, getCarried(), tank.bucketHandler())
                        : FluidContainerTransfers.transfer(player, getCarried(), tank.bucketHandler(), button == 110));
            }else return false;
        }else return false;
        refreshGrid();
        sendSnapshot();
        broadcastChanges();
        return true;
    }

    private void refreshGrid(){
        if(viewer.level().isClientSide || kind != 0 || !(viewer instanceof ServerPlayer player) || !stillValid(player)) return;
        var sources = network() ? ContainerStorageNetworks.connected(target, player) : List.of(target);
        var handlers = new ArrayList<net.neoforged.neoforge.items.IItemHandler>();
        for(BlockEntity source : sources){
            if(source instanceof SmartBatteryBlockEntity) continue;
            if(source instanceof SmartVaultBlockEntity vault) handlers.add(vault.getItemHandler());
            else handlers.addAll(WorkerContainerAccess.itemHandlers(source.getLevel(), source.getBlockPos(), null));
        }
        grid.refresh(handlers, search, row(), GRID_ROWS, GRID_COLUMNS);
        data.set(1, grid.row());
        data.set(2, Math.min(32767, grid.rows()));
        for(int idx = 0; idx < GRID_SIZE; idx++) display.setItem(idx, grid.stack(idx));
    }

    @Override public void broadcastChanges(){
        if(!viewer.level().isClientSide){
            if(!stillValid(viewer)) return;
            data.set(0, Math.max(0, TABS.indexOf(SmartContainerTablets.state(target).tab())));
            if(storageTab()) refreshGrid();
            if(ticks++ % 10 == 0) sendSnapshot();
        }
        super.broadcastChanges();
    }

    public void refreshSnapshot(String value){
        if(!stillValid(viewer)) return;
        if("network".equals(tab())){
            try{
                String[] pages = value.split("\\|", 2);
                int items = Math.max(0, Integer.parseInt(pages[0]));
                int resources = pages.length == 2 ? Math.max(0, Integer.parseInt(pages[1])) : 0;
                snapshotPage = items + "|" + resources;
            }
            catch(NumberFormatException ignored){ snapshotPage = ""; }
        }
        sendSnapshot();
    }

    private void sendSnapshot(){
        if(!(viewer instanceof ServerPlayer player) || !stillValid(player)) return;
        var snapshot = SmartContainerManifest.snapshot(player, target, tab(), "refresh", snapshotPage);
        PacketDistributor.sendToPlayer(player, new DiagnosticTabletAppSnapshotPayload(SmartContainerManifest.ID,
                true, tabletId, subLevelId, targetPos, snapshot));
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player){
        if(!stillValid(player)) return;
        if(slot >= 0 && slot < GRID_SIZE){
            if(player.level().isClientSide || !storageTab() || kind != 0) return;
            refreshGrid();
            ItemStack shown = grid.stack(slot), carried = getCarried();
            if(type == ClickType.QUICK_MOVE) quickMoveStack(player, slot);
            else if(type == ClickType.PICKUP && (button == 0 || button == 1)){
                if(carried.isEmpty()) setCarried(grid.extract(slot, button == 1 ? (shown.getCount() + 1) / 2 : shown.getMaxStackSize()));
                else if(shown.isEmpty() || ItemStack.isSameItemSameComponents(shown, carried)){
                    int count = button == 1 ? 1 : carried.getCount();
                    ItemStack rest = grid.insert(carried.copyWithCount(count), slot, false);
                    carried.shrink(count - rest.getCount());
                    setCarried(carried);
                }else if(button == 0) setCarried(grid.exchange(slot, carried, stack -> recover(player, stack)));
            }else if(type == ClickType.THROW && !shown.isEmpty()){
                player.drop(grid.extract(slot, button == 0 ? 1 : shown.getMaxStackSize()), true);
            }else if(type == ClickType.CLONE && player.getAbilities().instabuild && carried.isEmpty()){
                setCarried(shown.copyWithCount(shown.getMaxStackSize()));
            }else if(type == ClickType.SWAP && button >= 0 && button <= 8){
                ItemStack hotbar = player.getInventory().getItem(button);
                if(hotbar.isEmpty()) player.getInventory().setItem(button, grid.extract(slot, shown.getMaxStackSize()));
                else player.getInventory().setItem(button, grid.insert(hotbar, slot, false));
            }
            refreshGrid();
            broadcastChanges();
            return;
        }
        if(slot >= 0 && slot < slots.size() && !slots.get(slot).isActive()) return;
        super.clicked(slot, button, type, player);
    }

    @Override public boolean canDragTo(Slot slot){ return slot.index >= GRID_SIZE && slot.isActive(); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot){ return slot.index >= GRID_SIZE && slot.isActive(); }

    @Override public ItemStack quickMoveStack(Player player, int index){
        if(player.level().isClientSide || !stillValid(player) || !storageTab() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        refreshGrid();
        if(index < GRID_SIZE){
            if(kind != 0) return ItemStack.EMPTY;
            ItemStack available = grid.simulateExtract(index, grid.stack(index).getMaxStackSize());
            ItemStack rest = ItemHandlerHelper.insertItemStacked(new InvWrapper(player.getInventory()), available, true);
            ItemStack extracted = grid.extract(index, available.getCount() - rest.getCount());
            ItemStack result = extracted.copy();
            ItemStack rejected = ItemHandlerHelper.insertItemStacked(new InvWrapper(player.getInventory()), extracted, false);
            recover(player, grid.insert(rejected, index, false));
            refreshGrid();
            return result;
        }
        Slot slot = slots.get(index);
        if(!slot.isActive() || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy(), moving = slot.getItem();
        if(index < PLAYER_START){
            if(!moveItemStackTo(moving, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        }else if(kind == 0){
            moving = grid.insert(moving, -1, false);
            slot.set(moving);
        }else if(kind == 2){
            if(!moveItemStackTo(moving, GRID_SIZE, PLAYER_START, false)) return ItemStack.EMPTY;
        }else return ItemStack.EMPTY;
        if(moving.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        refreshGrid();
        return moving.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    private void recover(Player player, ItemStack stack){
        if(!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack, false);
    }
}
