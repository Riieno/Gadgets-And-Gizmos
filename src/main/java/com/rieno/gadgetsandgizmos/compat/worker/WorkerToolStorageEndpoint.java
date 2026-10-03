package com.rieno.gadgetsandgizmos.compat.worker;

import com.rieno.gadgetsandgizmos.compat.ae2.Ae2WirelessWorkerEndpoint;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerInventoryEndpoint;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceKey;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourcePacket;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerResourceType;
import com.rieno.gadgetsandgizmos.lib.worker.WorkerRecipeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

// One worker source for network APIs and every Tool-slot item exposing standard storage capabilities.
// Merely invoking Item#use is intentionally excluded: it opens a GUI and cannot safely transfer stock.
public final class WorkerToolStorageEndpoint extends WorkerInventoryEndpoint {
    private final ServerLevel level;
    private final ItemStackHandler toolSlots;
    private final Ae2WirelessWorkerEndpoint ae2;

    public WorkerToolStorageEndpoint(ServerLevel level, PlayerMannequinEntity worker, boolean ae2Loaded){
        super(UUID.nameUUIDFromBytes((worker.getUUID() + ":tool-storage").getBytes(StandardCharsets.UTF_8)),
                worker::blockPosition, worker.workerTools() == null ? new ItemStackHandler(0) : worker.workerTools(),
                level.registryAccess(), stack -> false, true);
        this.level = level;
        this.toolSlots = worker.workerTools() == null ? new ItemStackHandler(0) : worker.workerTools();
        this.ae2 = ae2Loaded ? new Ae2WirelessWorkerEndpoint(level, worker) : null;
    }

    @Override public String label(){ return "Worker tool storage"; }
    @Override public boolean isAvailable(){
        if(ae2 != null && ae2.isAvailable()) return true;
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            ItemStack stack = toolSlots.getStackInSlot(slot);
            if(WorkerContainerAccess.itemHandler(stack) != null
                    || WorkerContainerAccess.fluidHandler(stack) != null) return true;
        }
        return false;
    }

    @Override public boolean canExtract(WorkerResourceKey resource){
        return resource != null && (resource.type() == WorkerResourceType.ITEM
                || resource.type() == WorkerResourceType.FLUID) && isAvailable();
    }
    @Override public boolean canInsert(WorkerResourceKey resource){ return canExtract(resource); }
    @Override public int insertionPriority(WorkerResourceKey resource){ return available(resource) > 0L ? 0 : 2; }

    @Override public Map<WorkerResourceKey, Long> contents(){
        Map<WorkerResourceKey, Long> result = new LinkedHashMap<>();
        if(ae2 != null && ae2.isAvailable()) ae2.contents().forEach((key, count) ->
                result.merge(key, count, WorkerToolStorageEndpoint::add));
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            ItemStack tool = toolSlots.getStackInSlot(slot);
            IItemHandler items = WorkerContainerAccess.itemHandler(tool);
            if(items != null) for(int idx = 0; idx < items.getSlots(); idx++){
                ItemStack stack = items.getStackInSlot(idx);
                if(stack.isEmpty()) continue;
                long count = items.extractItem(idx, stack.getCount(), true).getCount();
                if(count > 0L) result.merge(new WorkerResourceKey(WorkerResourceType.ITEM,
                        BuiltInRegistries.ITEM.getKey(stack.getItem())), count, WorkerToolStorageEndpoint::add);
            }
            IFluidHandler fluids = WorkerContainerAccess.fluidHandler(tool);
            if(fluids != null) for(int idx = 0; idx < fluids.getTanks(); idx++){
                FluidStack stack = fluids.getFluidInTank(idx);
                if(stack.isEmpty()) continue;
                FluidStack accessible = fluids.drain(stack.copy(), IFluidHandler.FluidAction.SIMULATE);
                if(!accessible.isEmpty()) result.merge(new WorkerResourceKey(WorkerResourceType.FLUID,
                        BuiltInRegistries.FLUID.getKey(accessible.getFluid())), (long)accessible.getAmount(),
                        WorkerToolStorageEndpoint::add);
            }
        }
        return Map.copyOf(result);
    }

    @Override public long available(WorkerResourceKey resource){
        if(resource == null) return 0L;
        long amount = ae2 != null && ae2.isAvailable() ? ae2.available(resource) : 0L;
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            ItemStack tool = toolSlots.getStackInSlot(slot);
            if(resource.type() == WorkerResourceType.ITEM){
                IItemHandler items = WorkerContainerAccess.itemHandler(tool);
                if(items == null) continue;
                for(int idx = 0; idx < items.getSlots(); idx++){
                    ItemStack stack = items.getStackInSlot(idx);
                    if(stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(resource.id()))
                        continue;
                    amount = add(amount, items.extractItem(idx, stack.getCount(), true).getCount());
                }
            }else if(resource.type() == WorkerResourceType.FLUID){
                IFluidHandler fluids = WorkerContainerAccess.fluidHandler(tool);
                if(fluids == null) continue;
                for(int idx = 0; idx < fluids.getTanks(); idx++){
                    FluidStack stack = fluids.getFluidInTank(idx);
                    if(stack.isEmpty() || !BuiltInRegistries.FLUID.getKey(stack.getFluid())
                            .equals(resource.id())) continue;
                    FluidStack accessible = fluids.drain(stack.copy(), IFluidHandler.FluidAction.SIMULATE);
                    if(!accessible.isEmpty()) amount = add(amount, accessible.getAmount());
                }
            }
        }
        return amount;
    }

    @Override public long space(WorkerResourceKey resource){
        if(resource == null) return 0L;
        long space = ae2 != null && ae2.isAvailable() ? ae2.space(resource) : 0L;
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            ItemStack tool = toolSlots.getStackInSlot(slot);
            if(resource.type() == WorkerResourceType.ITEM){
                if(WorkerRecipeCatalog.isPortableCraftingTool(BuiltInRegistries.ITEM.getKey(tool.getItem()))) continue;
                IItemHandler items = WorkerContainerAccess.itemHandler(tool);
                if(items == null) continue;
                ItemStack sample = new ItemStack(BuiltInRegistries.ITEM.get(resource.id()));
                if(sample.isEmpty()) continue;
                sample.setCount(sample.getMaxStackSize());
                for(int idx = 0; idx < items.getSlots(); idx++)
                    space = add(space, sample.getCount() - items.insertItem(idx, sample, true).getCount());
            }else if(resource.type() == WorkerResourceType.FLUID){
                IFluidHandler fluids = WorkerContainerAccess.fluidHandler(tool);
                var fluid = BuiltInRegistries.FLUID.getOptional(resource.id()).orElse(null);
                if(fluids != null && fluid != null) space = add(space, fluids.fill(
                        new FluidStack(fluid, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE));
            }
        }
        return space;
    }

    @Override public WorkerResourcePacket extract(WorkerResourceKey resource, long amount, boolean simulate){
        if(resource == null || amount <= 0L) return WorkerResourcePacket.empty(resource);
        if(resource.type() == WorkerResourceType.ITEM)
            return extractMatchingItem(resource, amount, stack -> true, simulate);
        if(ae2 != null && ae2.isAvailable()){
            WorkerResourcePacket packet = ae2.extract(resource, amount, simulate);
            if(!packet.isEmpty()) return packet;
        }
        if(resource.type() != WorkerResourceType.FLUID) return WorkerResourcePacket.empty(resource);
        var fluid = BuiltInRegistries.FLUID.getOptional(resource.id()).orElse(null);
        if(fluid == null) return WorkerResourcePacket.empty(resource);
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            IFluidHandler handler = WorkerContainerAccess.fluidHandler(toolSlots.getStackInSlot(slot));
            if(handler == null) continue;
            FluidStack drained = handler.drain(new FluidStack(fluid, (int)Math.min(amount, Integer.MAX_VALUE)),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if(drained.isEmpty()) continue;
            persistFluidTool(slot, handler, simulate);
            return new WorkerResourcePacket(resource, drained.getAmount(), new CompoundTag());
        }
        return WorkerResourcePacket.empty(resource);
    }

    @Override public WorkerResourcePacket extractMatchingItem(WorkerResourceKey resource, long amount,
                                                                Predicate<ItemStack> matches, boolean simulate){
        if(resource == null || resource.type() != WorkerResourceType.ITEM || amount <= 0L)
            return WorkerResourcePacket.empty(resource);
        WorkerResourcePacket staged = extractFromToolSlots(resource, amount, matches, simulate, true);
        if(!staged.isEmpty()) return staged;
        if(ae2 != null && ae2.isAvailable()){
            WorkerResourcePacket packet = ae2.extractMatchingItem(resource, amount, matches, simulate);
            if(!packet.isEmpty()) return packet;
        }
        return extractFromToolSlots(resource, amount, matches, simulate, false);
    }

    private WorkerResourcePacket extractFromToolSlots(WorkerResourceKey resource, long amount,
                                                      Predicate<ItemStack> matches, boolean simulate,
                                                      boolean portableCraftingTool){
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            ItemStack tool = toolSlots.getStackInSlot(slot);
            if(WorkerRecipeCatalog.isPortableCraftingTool(BuiltInRegistries.ITEM.getKey(tool.getItem()))
                    != portableCraftingTool) continue;
            IItemHandler handler = WorkerContainerAccess.itemHandler(tool);
            if(handler == null) continue;
            for(int idx = 0; idx < handler.getSlots(); idx++){
                ItemStack stored = handler.getStackInSlot(idx);
                if(stored.isEmpty() || !BuiltInRegistries.ITEM.getKey(stored.getItem()).equals(resource.id())
                        || !matches.test(stored)) continue;
                ItemStack extracted = handler.extractItem(idx, (int)Math.min(amount, stored.getCount()), simulate);
                if(extracted.isEmpty()) continue;
                CompoundTag payload = new CompoundTag();
                payload.put("Stack", extracted.copyWithCount(1).saveOptional(level.registryAccess()));
                return new WorkerResourcePacket(resource, extracted.getCount(), payload);
            }
        }
        return WorkerResourcePacket.empty(resource);
    }

    @Override public long availableMatchingItem(WorkerResourceKey resource, Predicate<ItemStack> matches){
        if(resource == null || resource.type() != WorkerResourceType.ITEM) return 0L;
        long amount = ae2 != null && ae2.isAvailable() ? ae2.availableMatchingItem(resource, matches) : 0L;
        for(int slot = 0; slot < toolSlots.getSlots(); slot++){
            IItemHandler handler = WorkerContainerAccess.itemHandler(toolSlots.getStackInSlot(slot));
            if(handler == null) continue;
            for(int idx = 0; idx < handler.getSlots(); idx++){
                ItemStack stack = handler.getStackInSlot(idx);
                if(!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(resource.id())
                        && matches.test(stack)) amount = add(amount,
                        handler.extractItem(idx, stack.getCount(), true).getCount());
            }
        }
        return amount;
    }

    @Override public long insert(WorkerResourcePacket packet, boolean simulate){
        if(packet == null || packet.isEmpty()) return 0L;
        if(ae2 != null && ae2.isAvailable()){
            long accepted = ae2.insert(packet, simulate);
            if(accepted > 0L) return accepted;
        }
        WorkerResourceKey resource = packet.resource();
        if(resource.type() == WorkerResourceType.ITEM){
            ItemStack sample = packet.payload().contains("Stack")
                    ? ItemStack.parseOptional(level.registryAccess(), packet.payload().getCompound("Stack"))
                    : new ItemStack(BuiltInRegistries.ITEM.get(resource.id()));
            if(sample.isEmpty()) return 0L;
            for(int slot = 0; slot < toolSlots.getSlots(); slot++){
                ItemStack tool = toolSlots.getStackInSlot(slot);
                if(WorkerRecipeCatalog.isPortableCraftingTool(BuiltInRegistries.ITEM.getKey(tool.getItem()))) continue;
                IItemHandler handler = WorkerContainerAccess.itemHandler(tool);
                if(handler == null) continue;
                for(int idx = 0; idx < handler.getSlots(); idx++){
                    int offered = (int)Math.min(packet.amount(), sample.getMaxStackSize());
                    int accepted = offered - handler.insertItem(idx, sample.copyWithCount(offered), simulate).getCount();
                    if(accepted > 0) return accepted;
                }
            }
        }else if(resource.type() == WorkerResourceType.FLUID){
            var fluid = BuiltInRegistries.FLUID.getOptional(resource.id()).orElse(null);
            if(fluid == null) return 0L;
            for(int slot = 0; slot < toolSlots.getSlots(); slot++){
                IFluidHandler handler = WorkerContainerAccess.fluidHandler(toolSlots.getStackInSlot(slot));
                if(handler == null) continue;
                int accepted = handler.fill(new FluidStack(fluid, (int)Math.min(packet.amount(), Integer.MAX_VALUE)),
                        simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                if(accepted <= 0) continue;
                persistFluidTool(slot, handler, simulate);
                return accepted;
            }
        }
        return 0L;
    }

    private void persistFluidTool(int slot, IFluidHandler handler, boolean simulate){
        if(!simulate && handler instanceof IFluidHandlerItem item)
            toolSlots.setStackInSlot(slot, item.getContainer());
    }

    private static long add(long first, long second){
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
