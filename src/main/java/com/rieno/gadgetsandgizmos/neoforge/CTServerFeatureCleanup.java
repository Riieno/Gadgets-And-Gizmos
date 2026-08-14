package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Release server-owned caches, handles and sessions when their level or server goes away
public final class CTServerFeatureCleanup {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final int MAX_CHUNK_RANGE = 64;

    private static final int MAX_CHUNK_STARTS_PER_TICK = 1;
    private static final int MAX_BLOCK_ENTITIES_PER_TICK = 16;
    private static final int MAX_INVENTORY_SLOTS_PER_TICK = 256;
    private static final Map<ServerLevel, Set<Long>> PENDING_CHUNKS = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, ChunkCleanupTask> ACTIVE_CHUNK_CLEANUPS = new ConcurrentHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracked disabled items
    private static volatile Set<Item> disabledItems = Set.of();
    // Next level index
    private static int nextLevelIndex;
    // Tracks whether optional inventories are initialized
    private static boolean optionalInventoriesInitialized;
    // Resolved curios inventory getter method
    private static Method curiosInventoryGetter;
    // Resolved curios equipped getter method
    private static Method curiosEquippedGetter;
    // Resolved accessories capability getter method
    private static Method accessoriesCapabilityGetter;
    // Resolved accessories containers getter method
    private static Method accessoriesContainersGetter;
    // Resolved accessories inventory getter method
    private static Method accessoriesInventoryGetter;
    // Resolved accessories cosmetic inventory getter method
    private static Method accessoriesCosmeticInventoryGetter;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT server feature cleanup
    private CTServerFeatureCleanup() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Queue the loaded chunk range
    public static RangeCleanupRequest queueLoadedChunkRange(ServerLevel level, ChunkPos center, int requestedRange) {
        refreshDisabledItems();
        int range = Math.max(0, Math.min(MAX_CHUNK_RANGE, requestedRange));
        if (disabledItems.isEmpty()) {
            return new RangeCleanupRequest(range, 0, 0, 0);
        }

        Set<Long> pending = PENDING_CHUNKS.computeIfAbsent(level,
                ignored -> ConcurrentHashMap.newKeySet());
        ChunkCleanupTask active = ACTIVE_CHUNK_CLEANUPS.get(level);
        int loadedChunks = 0;
        int queuedChunks = 0;
        for (int chunkX = center.x - range; chunkX <= center.x + range; chunkX++) {
            for (int chunkZ = center.z - range; chunkZ <= center.z + range; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                loadedChunks++;
                long chunkKey = chunk.getPos().toLong();
                if ((active == null || active.chunkKey() != chunkKey) && pending.add(chunkKey)) {
                    queuedChunks++;
                }
            }
        }
        return new RangeCleanupRequest(range, loadedChunks, queuedChunks, disabledItems.size());
    }

    // Clean up the player
    public static boolean cleanupPlayer(ServerPlayer player) {
        refreshDisabledItems();
        if (disabledItems.isEmpty()) {
            return false;
        }

        boolean changed = cleanupContainer(player.getInventory());
        changed |= cleanupContainer(player.getEnderChestInventory());

        for (Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (isDisabledModItem(stack)) {
                slot.set(ItemStack.EMPTY);
                changed = true;
            } else if (sanitizeNestedContents(stack)) {
                slot.set(stack);
                changed = true;
            }
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (isDisabledModItem(carried)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            changed = true;
        } else if (sanitizeNestedContents(carried)) {
            player.containerMenu.setCarried(carried);
            changed = true;
        }

        changed |= cleanupOptionalLivingInventories(player);
        if (changed) {
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
        return changed;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the server tick event
    public static void onServerTick(ServerTickEvent.Post evt) {
        if (!hasPendingCleanup()) {
            return;
        }
        processPendingCleanup(evt.getServer().getAllLevels());
    }

    // Handle the server stopped event
    public static void onServerStopped(ServerStoppedEvent evt) {
        PENDING_CHUNKS.clear();
        ACTIVE_CHUNK_CLEANUPS.clear();
        disabledItems = Set.of();
        nextLevelIndex = 0;
    }

    // Check if this has pending cleanup
    private static boolean hasPendingCleanup() {
        return !ACTIVE_CHUNK_CLEANUPS.isEmpty()
                || PENDING_CHUNKS.values().stream().anyMatch(chunks -> !chunks.isEmpty());
    }

    // Process the pending cleanup
    private static void processPendingCleanup(Iterable<ServerLevel> serverLevels) {
        List<ServerLevel> levels = new ArrayList<>();
        serverLevels.forEach(levels::add);
        if (levels.isEmpty()) {
            return;
        }

        CleanupBudget budget = new CleanupBudget();
        int startIndex = Math.floorMod(nextLevelIndex, levels.size());
        for (int offset = 0; offset < levels.size(); offset++) {
            ServerLevel level = levels.get((startIndex + offset) % levels.size());
            processChunkCleanup(level, budget);
        }
        nextLevelIndex = (startIndex + 1) % levels.size();
    }

    // Process the chunk cleanup
    private static void processChunkCleanup(ServerLevel level, CleanupBudget budget) {
        if (!budget.canProcessChunk()) {
            return;
        }

        ChunkCleanupTask task = ACTIVE_CHUNK_CLEANUPS.get(level);
        if (task == null) {
            if (budget.remainingChunkStarts <= 0) {
                return;
            }
            Long chunkKey = pollPendingChunk(level);
            if (chunkKey == null) {
                return;
            }

            budget.remainingChunkStarts--;
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (chunk == null) {
                return;
            }
            task = new ChunkCleanupTask(chunkKey, chunk);
            ACTIVE_CHUNK_CLEANUPS.put(level, task);
        }

        if (task.process(level, budget)) {
            ACTIVE_CHUNK_CLEANUPS.remove(level, task);
        }
    }

    // Poll the next pending chunk
    private static Long pollPendingChunk(ServerLevel level) {
        Set<Long> pending = PENDING_CHUNKS.get(level);
        if (pending == null) {
            return null;
        }
        for (Long chunkKey : pending) {
            if (pending.remove(chunkKey)) {
                return chunkKey;
            }
        }
        return null;
    }

    // Collect the block capabilities
    private static List<IItemHandler> collectBlockCapabilities(ServerLevel level, BlockEntity blockEntity,
                                                                Set<IItemHandler> cleanedHandlers) {
        Set<IItemHandler> handlers = Collections.newSetFromMap(new IdentityHashMap<>());
        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        addBlockItemHandler(handlers, level, pos, state, blockEntity, null);
        for (Direction dir : Direction.values()) {
            addBlockItemHandler(handlers, level, pos, state, blockEntity, dir);
        }

        List<IItemHandler> unvisitedHandlers = new ArrayList<>();
        for (IItemHandler handler : handlers) {
            if (cleanedHandlers.add(handler)) {
                unvisitedHandlers.add(handler);
            }
        }
        return unvisitedHandlers;
    }

    // Clean up the container
    private static boolean cleanupContainer(Container container) {
        return cleanupContainer(container, 0, Integer.MAX_VALUE).changed();
    }

    // Clean up the container
    private static InventoryScanResult cleanupContainer(Container container, int firstSlot, int maxSlots) {
        int slots;
        try {
            slots = container.getContainerSize();
        } catch (RuntimeException ignored) {
            return new InventoryScanResult(false, firstSlot, 0, true);
        }

        int startSlot = Math.min(Math.max(0, firstSlot), Math.max(0, slots));
        int slotsToScan = Math.min(Math.max(0, maxSlots), Math.max(0, slots - startSlot));
        int endSlot = startSlot + slotsToScan;
        boolean changed = false;
        for (int slot = startSlot; slot < endSlot; slot++) {
            try {
                ItemStack stack = container.getItem(slot);
                if (isDisabledModItem(stack)) {
                    container.setItem(slot, ItemStack.EMPTY);
                    changed = true;
                } else {
                    ItemStack sanitized = stack.copy();
                    if (sanitizeNestedContents(sanitized)) {
                        container.setItem(slot, sanitized);
                        changed = true;
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (changed) {
            try {
                container.setChanged();
            } catch (RuntimeException ignored) {
            }
        }
        return new InventoryScanResult(changed, endSlot, slotsToScan, endSlot >= slots);
    }

    // Clean up the item handler
    static boolean cleanupItemHandler(IItemHandler handler) {
        return cleanupItemHandler(handler, 0, Integer.MAX_VALUE).changed();
    }

    // Clean up the item handler
    static InventoryScanResult cleanupItemHandler(IItemHandler handler, int firstSlot, int maxSlots) {
        int slots;
        try {
            slots = handler.getSlots();
        } catch (RuntimeException ignored) {
            return new InventoryScanResult(false, firstSlot, 0, true);
        }

        int startSlot = Math.min(Math.max(0, firstSlot), Math.max(0, slots));
        int slotsToScan = Math.min(Math.max(0, maxSlots), Math.max(0, slots - startSlot));
        int endSlot = startSlot + slotsToScan;
        boolean changed = false;
        for (int slot = startSlot; slot < endSlot; slot++) {
            try {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (isDisabledModItem(stack)) {
                    changed |= clearItemHandlerSlot(handler, slot, stack.getCount());
                } else if (handler instanceof IItemHandlerModifiable modifiable) {
                    ItemStack sanitized = stack.copy();
                    if (sanitizeNestedContents(sanitized)) {
                        modifiable.setStackInSlot(slot, sanitized);
                        changed = true;
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return new InventoryScanResult(changed, endSlot, slotsToScan, endSlot >= slots);
    }

    // Clear the item handler slot
    private static boolean clearItemHandlerSlot(IItemHandler handler, int slot, int count) {
        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            return true;
        }

        int remaining = count;
        while (remaining > 0) {
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) {
                break;
            }
            int extractedCount = extracted.getCount();
            if (extractedCount <= 0) {
                break;
            }
            remaining -= Math.min(remaining, extractedCount);
        }
        return remaining < count;
    }

    // Sanitize nested item contents
    private static boolean sanitizeNestedContents(ItemStack owner) {
        if (owner.isEmpty()) {
            return false;
        }

        boolean changed = false;
        ItemContainerContents container = owner.get(DataComponents.CONTAINER);
        if (container != null) {
            List<ItemStack> contents = new ArrayList<>(container.getSlots());
            boolean containerChanged = false;
            for (int slot = 0; slot < container.getSlots(); slot++) {
                ItemStack nested = container.getStackInSlot(slot).copy();
                if (isDisabledModItem(nested)) {
                    nested = ItemStack.EMPTY;
                    containerChanged = true;
                } else if (sanitizeNestedContents(nested)) {
                    containerChanged = true;
                }
                contents.add(nested);
            }
            if (containerChanged) {
                owner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
                changed = true;
            }
        }

        BundleContents bundle = owner.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            List<ItemStack> contents = new ArrayList<>();
            boolean bundleChanged = false;
            for (ItemStack original : bundle.itemsCopy()) {
                ItemStack nested = original.copy();
                if (isDisabledModItem(nested)) {
                    bundleChanged = true;
                    continue;
                }
                bundleChanged |= sanitizeNestedContents(nested);
                contents.add(nested);
            }
            if (bundleChanged) {
                owner.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(contents));
                changed = true;
            }
        }

        ChargedProjectiles projectiles = owner.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles != null) {
            List<ItemStack> contents = new ArrayList<>();
            boolean projectilesChanged = false;
            for (ItemStack original : projectiles.getItems()) {
                ItemStack nested = original.copy();
                if (isDisabledModItem(nested)) {
                    projectilesChanged = true;
                    continue;
                }
                projectilesChanged |= sanitizeNestedContents(nested);
                contents.add(nested);
            }
            if (projectilesChanged) {
                owner.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(contents));
                changed = true;
            }
        }

        return changed;
    }

    // Clean up the optional living inventories
    private static boolean cleanupOptionalLivingInventories(LivingEntity entity) {
        initOptionalInventoryAccess();
        boolean changed = false;

        if (curiosInventoryGetter != null && curiosEquippedGetter != null) {
            try {
                Optional<?> inventory = (Optional<?>) curiosInventoryGetter.invoke(null, entity);
                if (inventory.isPresent()) {
                    Object handler = curiosEquippedGetter.invoke(inventory.get());
                    if (handler instanceof IItemHandler itemHandler) {
                        changed |= cleanupItemHandler(itemHandler);
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        if (accessoriesCapabilityGetter != null && accessoriesContainersGetter != null) {
            try {
                Optional<?> capability = (Optional<?>) accessoriesCapabilityGetter.invoke(null, entity);
                if (capability.isPresent()) {
                    Object containersValue = accessoriesContainersGetter.invoke(capability.get());
                    if (containersValue instanceof Map<?, ?> containers) {
                        for (Object accessoryContainer : containers.values()) {
                            changed |= cleanupAccessoryContainer(accessoryContainer);
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return changed;
    }

    // Clean up the accessory container
    private static boolean cleanupAccessoryContainer(Object accessoryContainer) throws ReflectiveOperationException {
        if (accessoryContainer == null) {
            return false;
        }
        boolean changed = false;
        Object inventory = accessoriesInventoryGetter.invoke(accessoryContainer);
        if (inventory instanceof Container container) {
            changed |= cleanupContainer(container);
        }
        Object cosmeticInventory = accessoriesCosmeticInventoryGetter.invoke(accessoryContainer);
        if (cosmeticInventory instanceof Container container) {
            changed |= cleanupContainer(container);
        }
        return changed;
    }

    // Initialize the optional inventory access
    private static synchronized void initOptionalInventoryAccess() {
        if (optionalInventoriesInitialized) {
            return;
        }
        optionalInventoriesInitialized = true;

        if (ModList.get().isLoaded("curios")) {
            try {
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                Class<?> inventory = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                curiosInventoryGetter = api.getMethod("getCuriosInventory", LivingEntity.class);
                curiosEquippedGetter = inventory.getMethod("getEquippedCurios");
            } catch (ReflectiveOperationException ignored) {
                curiosInventoryGetter = null;
                curiosEquippedGetter = null;
            }
        }

        if (ModList.get().isLoaded("accessories")) {
            try {
                Class<?> capability = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                Class<?> container = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
                accessoriesCapabilityGetter = capability.getMethod("getOptionally", LivingEntity.class);
                accessoriesContainersGetter = capability.getMethod("getContainers");
                accessoriesInventoryGetter = container.getMethod("getAccessories");
                accessoriesCosmeticInventoryGetter = container.getMethod("getCosmeticAccessories");
            } catch (ReflectiveOperationException ignored) {
                accessoriesCapabilityGetter = null;
                accessoriesContainersGetter = null;
                accessoriesInventoryGetter = null;
                accessoriesCosmeticInventoryGetter = null;
            }
        }
    }

    // Refresh the disabled items
    private static void refreshDisabledItems() {
        Set<Item> nextDisabledItems = new HashSet<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null
                    && CreateThrusters.MOD_ID.equals(id.getNamespace())
                    && !CTFeatureToggles.isItemEnabled(id.getPath())) {
                nextDisabledItems.add(item);
            }
        });
        disabledItems = Set.copyOf(nextDisabledItems);
    }

    // Add the handler
    private static void addHandler(Set<IItemHandler> handlers, IItemHandler handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    // Add the block item handler
    private static void addBlockItemHandler(Set<IItemHandler> handlers, ServerLevel level, BlockPos pos,
                                             BlockState state, BlockEntity blockEntity, Direction dir) {
        try {
            addHandler(handlers, level.getCapability(
                    Capabilities.ItemHandler.BLOCK, pos, state, blockEntity, dir));
        } catch (RuntimeException ignored) {
        }
    }

    // Check if this is a disabled mod item
    private static boolean isDisabledModItem(ItemStack stack) {
        return !stack.isEmpty() && disabledItems.contains(stack.getItem());
    }

    // Store the range cleanup request
    public record RangeCleanupRequest(int range, int loadedChunks, int queuedChunks, int disabledItemTypes) {
    }

    // Store inventory scan results
    static record InventoryScanResult(boolean changed, int nextSlot, int slotsScanned, boolean complete) {
    }

    // Handle the cleanup budget
    private static final class CleanupBudget {
        // Current remaining chunk starts
        private int remainingChunkStarts = MAX_CHUNK_STARTS_PER_TICK;
        // Current remaining block entities
        private int remainingBlockEntities = MAX_BLOCK_ENTITIES_PER_TICK;
        // Current remaining inventory slots
        private int remainingInventorySlots = MAX_INVENTORY_SLOTS_PER_TICK;

        // Check if this can process chunk
        private boolean canProcessChunk() {
            return remainingBlockEntities > 0 && remainingInventorySlots > 0;
        }
    }

    // Handle the chunk cleanup task
    private static final class ChunkCleanupTask {
        // Chunk key
        private final long chunkKey;
        // Chunk
        private final LevelChunk chunk;
        // Tracked block entities
        private final List<BlockEntity> blockEntities;
        // Tracked cleaned handlers
        private final Set<IItemHandler> cleanedHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        // Next block entity
        private int nextBlockEntity;
        // Active inventory
        private BlockEntityInventoryCursor activeInventory;

        // Initialize the chunk cleanup task
        private ChunkCleanupTask(long chunkKey, LevelChunk chunk) {
            this.chunkKey = chunkKey;
            this.chunk = chunk;
            this.blockEntities = new ArrayList<>(chunk.getBlockEntities().values());
        }

        // Get the chunk key
        private long chunkKey() {
            return chunkKey;
        }

        // Process the chunk cleanup task
        private boolean process(ServerLevel level, CleanupBudget budget) {
            LevelChunk currentChunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (currentChunk != chunk) {
                return true;
            }

            while (budget.remainingBlockEntities > 0) {
                if (activeInventory == null) {
                    if (nextBlockEntity >= blockEntities.size()) {
                        return true;
                    }

                    BlockEntity blockEntity = blockEntities.get(nextBlockEntity++);
                    budget.remainingBlockEntities--;
                    if (blockEntity == null || blockEntity.isRemoved()) {
                        continue;
                    }
                    activeInventory = new BlockEntityInventoryCursor(level, blockEntity, cleanedHandlers);
                }

                if (!activeInventory.process(budget)) {
                    return false;
                }
                activeInventory = null;
            }
            return nextBlockEntity >= blockEntities.size() && activeInventory == null;
        }
    }

    // Handle the block entity inventory cursor
    private static final class BlockEntityInventoryCursor {
        // Bound block entity
        private final BlockEntity blockEntity;
        // Container
        private final Container container;
        // Tracked handlers
        private final List<IItemHandler> handlers;
        // Next container slot
        private int nextContainerSlot;
        // Next handler
        private int nextHandler;
        // Next handler slot
        private int nextHandlerSlot;
        // Tracks whether container is complete
        private boolean containerComplete;
        // Tracks whether changed is set
        private boolean changed;

        // Initialize the block entity inventory cursor
        private BlockEntityInventoryCursor(ServerLevel level, BlockEntity blockEntity,
                                           Set<IItemHandler> cleanedHandlers) {
            this.blockEntity = blockEntity;
            this.container = blockEntity instanceof Container blockContainer ? blockContainer : null;
            this.containerComplete = container == null;
            this.handlers = collectBlockCapabilities(level, blockEntity, cleanedHandlers);
        }

        // Process the block entity inventory cursor
        private boolean process(CleanupBudget budget) {
            if (!containerComplete) {
                if (budget.remainingInventorySlots <= 0) {
                    return false;
                }
                InventoryScanResult res = cleanupContainer(
                        container, nextContainerSlot, budget.remainingInventorySlots);
                changed |= res.changed();
                nextContainerSlot = res.nextSlot();
                budget.remainingInventorySlots -= res.slotsScanned();
                containerComplete = res.complete();
                if (!containerComplete) {
                    return false;
                }
            }

            while (nextHandler < handlers.size()) {
                if (budget.remainingInventorySlots <= 0) {
                    return false;
                }
                InventoryScanResult res = cleanupItemHandler(
                        handlers.get(nextHandler), nextHandlerSlot, budget.remainingInventorySlots);
                changed |= res.changed();
                nextHandlerSlot = res.nextSlot();
                budget.remainingInventorySlots -= res.slotsScanned();
                if (!res.complete()) {
                    return false;
                }
                nextHandler++;
                nextHandlerSlot = 0;
            }

            if (changed) {
                blockEntity.setChanged();
            }
            return true;
        }
    }
}
