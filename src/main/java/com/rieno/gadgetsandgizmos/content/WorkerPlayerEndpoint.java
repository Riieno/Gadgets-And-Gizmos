package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

// Adapt an online player inventory to the shared worker destination API
public final class WorkerPlayerEndpoint implements WorkerEndpoint {
    private static final String WORKER_FLUID_CARGO_TAG = "WorkerFluidCargo";

    private final ServerPlayer player;

    // Initialize one live player delivery endpoint
    public WorkerPlayerEndpoint(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public UUID id() {
        return player.getUUID();
    }

    @Override
    public @Nullable UUID subLevelId() {
        return null;
    }

    @Override
    public BlockPos position() {
        return player.blockPosition();
    }

    @Override
    public String label() {
        return player.getGameProfile().getName();
    }

    @Override
    public List<BlockPos> interactionBlocks() {
        return List.of(player.blockPosition().below());
    }

    @Override
    public @Nullable Direction interactionFace() {
        return null;
    }

    @Override
    public boolean canExtract(WorkerResourceKey resource) {
        return false;
    }

    @Override
    public boolean canInsert(WorkerResourceKey resource) {
        return resource != null && space(resource) > 0L;
    }

    @Override
    public long available(WorkerResourceKey resource) {
        return 0L;
    }

    @Override
    public long space(WorkerResourceKey resource) {
        ItemStack carried = resource == null ? ItemStack.EMPTY : representativeStack(resource);
        if (carried.isEmpty()) return 0L;
        long itemSpace = simulatedInsert(carried, carried.getCount());
        if (itemSpace < carried.getCount()) return 0L;
        return switch (resource.type()) {
            case ITEM -> simulatedInsert(carried, carried.getMaxStackSize());
            case FLUID, FUEL -> 1000L;
            case ENERGY -> WorkerEnergyBatteryItem.MAX_ENERGY;
        };
    }

    @Override
    public WorkerResourcePacket extract(WorkerResourceKey resource, long maximumAmount, boolean simulate) {
        return WorkerResourcePacket.empty(resource);
    }

    @Override
    public long insert(WorkerResourcePacket packet, boolean simulate) {
        if (packet == null || packet.isEmpty()) return 0L;
        ItemStack template = packetStack(packet);
        if (template.isEmpty()) return 0L;
        long itemAmount = packet.resource().type() == WorkerResourceType.ITEM ? packet.amount() : template.getCount();
        if (simulate) {
            long accepted = simulatedInsert(template, itemAmount);
            return accepted >= itemAmount ? packet.amount() : 0L;
        }
        long remaining = itemAmount;
        while (remaining > 0L) {
            ItemStack offered = template.copyWithCount((int) Math.min(template.getMaxStackSize(), remaining));
            int offeredCount = offered.getCount();
            player.getInventory().add(offered);
            int inserted = offeredCount - offered.getCount();
            if (inserted <= 0) break;
            remaining -= inserted;
        }
        player.getInventory().setChanged();
        return remaining == 0L ? packet.amount() : packet.amount() - remaining;
    }

    // Get the currently usable space for a representative item stack.
    // Simulate normal player-inventory insertion without mutating the player.
    private long simulatedInsert(ItemStack template, long amount) {
        if (template.isEmpty() || amount <= 0L) return 0L;
        ItemStackHandler inventory = new ItemStackHandler(player.getInventory().items.size());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, player.getInventory().items.get(slot).copy());
        }
        long remaining = amount;
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0L; slot++) {
            ItemStack offered = template.copyWithCount((int) Math.min(template.getMaxStackSize(), remaining));
            ItemStack remainder = inventory.insertItem(slot, offered, false);
            remaining -= offered.getCount() - remainder.getCount();
        }
        return amount - remaining;
    }

    // Convert one carried packet into the real item representation delivered to a player.
    private ItemStack packetStack(WorkerResourcePacket packet) {
        if (packet.resource().type() == WorkerResourceType.ITEM) {
            ItemStack stack = packet.payload().contains("Stack")
                    ? ItemStack.parseOptional(player.registryAccess(), packet.payload().getCompound("Stack"))
                    : new ItemStack(BuiltInRegistries.ITEM.get(packet.resource().id()));
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(
                    (int) Math.min(stack.getMaxStackSize(), packet.amount()));
        }
        return representativeStack(packet.resource(), packet.amount());
    }

    // Create one physical representation for fluid or FE custody in a player inventory.
    private ItemStack representativeStack(WorkerResourceKey resource) {
        return representativeStack(resource, resource.type() == WorkerResourceType.ENERGY
                ? WorkerEnergyBatteryItem.MAX_ENERGY : 1000L);
    }

    // Create one physical representation with the packet's actual fluid or FE amount.
    private ItemStack representativeStack(WorkerResourceKey resource, long amount) {
        if (resource.type() == WorkerResourceType.ENERGY) {
            return CTItems.WORKER_ENERGY_BATTERY == null ? ItemStack.EMPTY
                    : WorkerEnergyBatteryItem.charged(CTItems.WORKER_ENERGY_BATTERY.get(),
                    amount);
        }
        if (resource.type() == WorkerResourceType.ITEM) {
            return new ItemStack(BuiltInRegistries.ITEM.get(resource.id()));
        }
        var fluid = BuiltInRegistries.FLUID.get(resource.id());
        if (fluid == null) return ItemStack.EMPTY;
        ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, (int) Math.min(1000L, amount)));
        if (!bucket.isEmpty()) return bucket;
        ItemStack carrier = new ItemStack(Items.BUCKET);
        CompoundTag data = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putString(WORKER_FLUID_CARGO_TAG, resource.id().toString());
        carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        carrier.set(DataComponents.CUSTOM_NAME, Component.literal("Bucket of "
                + fluid.getFluidType().getDescription().getString()));
        return carrier;
    }
}
